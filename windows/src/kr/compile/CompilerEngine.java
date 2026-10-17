package kr.compile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class CompilerEngine {
    public enum Target {
        WINDOWS("Windows x64 (.exe)"),
        LINUX("Linux x64 (ELF)"),
        ANDROID("Android ARM64 (.so)");

        private final String displayName;
        Target(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        @Override public String toString() { return displayName; }
    }

    public interface Logger {
        void line(String value);
    }

    private final File projectDir;
    private final Target target;
    private final boolean onlineDependencies;
    private final Logger log;
    private volatile Process activeProcess;
    private volatile boolean cancelled = false;

    public CompilerEngine(File projectDir, Target target, boolean onlineDependencies, Logger log) {
        this.projectDir = projectDir;
        this.target = target != null ? target : Target.WINDOWS;
        this.onlineDependencies = onlineDependencies;
        this.log = log != null ? log : msg -> {};
    }

    public void cancel() {
        cancelled = true;
        Process p = activeProcess;
        if (p != null) {
            try { p.destroyForcibly(); } catch (Exception ignored) {}
        }
    }

    public File build() throws Exception {
        if (projectDir == null || !projectDir.isDirectory()) {
            throw new IOException("Ungültiges Projektverzeichnis.");
        }

        File outputDir = new File(projectDir, "KR_BUILD");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Konnte Ausgabeordner KR_BUILD nicht anlegen: " + outputDir);
        }

        File buildLog = new File(outputDir, "build.log");
        Files.write(buildLog.toPath(), ("=== KR Compiler Build Log: " + new Date() + " ===\n").getBytes(StandardCharsets.UTF_8));

        log.line("[INFO] Starte Build für Zielplattform: " + target.getDisplayName());
        log.line("[PROJEKT] Projektordner: " + projectDir.getAbsolutePath());

        // 1. KR Unified Language Prüfung
        List<File> krFiles = KrPipeline.findKrFiles(projectDir);
        if (!krFiles.isEmpty()) {
            log.line("[INFO] KR Unified Language Projekt erkannt (" + krFiles.size() + " .kr-Datei(en)).");
            return buildKrUnified(krFiles, outputDir, buildLog);
        }

        // 2. Rust Projekt
        if (new File(projectDir, "Cargo.toml").isFile()) {
            log.line("[INFO] Rust-Projekt erkannt (Cargo.toml).");
            return buildRust(outputDir, buildLog);
        }

        // 3. Go Projekt
        if (new File(projectDir, "go.mod").isFile() || hasGoFiles(projectDir)) {
            log.line("[INFO] Go-Projekt erkannt.");
            return buildGo(outputDir, buildLog);
        }

        // 4. CMake Projekt
        if (new File(projectDir, "CMakeLists.txt").isFile()) {
            log.line("[INFO] CMake-Projekt erkannt (CMakeLists.txt).");
            return buildCMake(outputDir, buildLog);
        }

        // 5. Makefile Projekt
        if (new File(projectDir, "Makefile").isFile() || new File(projectDir, "makefile").isFile()) {
            log.line("[INFO] Makefile-Projekt erkannt.");
            return buildMakefile(outputDir, buildLog);
        }

        // 6. Direkte C/C++ Dateien
        List<File> cppFiles = findCppFiles(projectDir);
        if (!cppFiles.isEmpty()) {
            log.line("[INFO] " + cppFiles.size() + " C/C++-Quelldatei(en) gefunden. Direkter Compilerlauf.");
            return buildDirectCpp(cppFiles, outputDir, buildLog);
        }

        throw new IOException("Keine unterstützten Quell- oder Projektdateien (.kr, Cargo.toml, go.mod, CMakeLists.txt, Makefile, .cpp) gefunden.");
    }

    private File buildKrUnified(List<File> krFiles, File outputDir, File buildLog) throws Exception {
        log.line("[INFO] Führe KR Multi-File Pipeline & Code-Generator aus …");
        KrPipeline.PipelineResult res = KrPipeline.process(projectDir, outputDir, false);

        for (KrDiagnostic diag : res.diagnostics) {
            log.line(diag.toString());
        }

        if (!res.success) {
            throw new IOException("KR Pipeline fehlgeschlagen mit " + res.diagnostics.size() + " Diagnosemeldungen.");
        }

        int count = res.codeResult != null ? res.codeResult.cppSources.size() : 0;
        log.line("[INFO] Code-Generierung abgeschlossen: " + count + " Translation Unit(s) erstellt.");

        // Wenn modularer C++ Output erzeugt wurde:
        File cmakeLists = new File(projectDir, "CMakeLists.txt");
        if (cmakeLists.isFile()) {
            return buildCMake(outputDir, buildLog);
        }

        List<File> sourcesToCompile = new ArrayList<>();
        File generatedMain = new File(projectDir, "main.cpp");
        if (generatedMain.isFile()) sourcesToCompile.add(generatedMain);

        File modulesDir = new File(projectDir, "modules");
        if (modulesDir.isDirectory()) {
            File[] mFiles = modulesDir.listFiles((d, n) -> n.endsWith(".cpp"));
            if (mFiles != null) Collections.addAll(sourcesToCompile, mFiles);
        }

        if (sourcesToCompile.isEmpty()) {
            sourcesToCompile.addAll(findCppFiles(projectDir));
        }

        return buildDirectCpp(sourcesToCompile, outputDir, buildLog);
    }

    private File buildDirectCpp(List<File> sources, File outputDir, File buildLog) throws Exception {
        String clang = findClangExecutable();
        if (clang == null) {
            throw new IOException("Kein C++-Compiler gefunden (clang++ oder g++ auf PATH oder in Program Files/LLVM).");
        }

        String exeName = target == Target.WINDOWS ? "programm.exe" : (target == Target.LINUX ? "programm" : "libuser.so");
        File artifact = new File(outputDir, exeName);

        List<String> cmd = new ArrayList<>();
        cmd.add(clang);
        cmd.add("-std=c++20");
        cmd.add("-O3");
        cmd.add("-static-libgcc");
        cmd.add("-static-libstdc++");
        cmd.add("-I" + projectDir.getAbsolutePath());
        cmd.add("-L" + projectDir.getAbsolutePath());

        // Gängige Include-Verzeichnisse automatisch einbinden
        for (String sub : new String[]{"include", "inc", "src", "locale", "samples"}) {
            File subDir = new File(projectDir, sub);
            if (subDir.isDirectory()) {
                cmd.add("-I" + subDir.getAbsolutePath());
            }
        }

        // Erkennung von DLL-Export-Projekten (wie Mycelia i18n_api)
        File i18nHeader = new File(projectDir, "i18n_api.h");
        if (i18nHeader.isFile()) {
            cmd.add("-DI18N_EXPORTS");
        }

        File bridgeHeader = new File(projectDir, "kr_bridge.h");
        if (bridgeHeader.isFile()) {
            cmd.add("-include");
            cmd.add(bridgeHeader.getAbsolutePath());
        }

        for (File s : sources) {
            cmd.add(s.getAbsolutePath());
        }

        cmd.add("-o");
        cmd.add(artifact.getAbsolutePath());

        log.line("[EXEC] Führe Compiler aus: " + clang + " -> " + artifact.getName());
        runProcess(cmd, projectDir, buildLog);

        if (!artifact.exists()) {
            throw new IOException("Kompilierung beendet, aber Artefakt wurde nicht erzeugt: " + artifact);
        }

        log.line("[ERFOLG] Build erfolgreich: " + artifact.getAbsolutePath() + " (" + artifact.length() + " Bytes)");
        return artifact;
    }

    private File buildCMake(File outputDir, File buildLog) throws Exception {
        String cmake = findExecutable("cmake");
        if (cmake == null) throw new IOException("cmake.exe nicht gefunden.");

        File cmakeBuildDir = new File(outputDir, "cmake_build");
        cmakeBuildDir.mkdirs();

        List<String> configCmd = Arrays.asList(cmake, "-S", projectDir.getAbsolutePath(), "-B", cmakeBuildDir.getAbsolutePath());
        log.line("[INFO] Konfiguriere CMake-Projekt …");
        runProcess(configCmd, projectDir, buildLog);

        List<String> buildCmd = Arrays.asList(cmake, "--build", cmakeBuildDir.getAbsolutePath(), "--config", "Release");
        log.line("[INFO] Kompiliere CMake-Projekt …");
        runProcess(buildCmd, projectDir, buildLog);

        File targetExe = new File(outputDir, "programm.exe");
        File found = findFirstExecutable(cmakeBuildDir);
        if (found != null) {
            Files.copy(found.toPath(), targetExe.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return targetExe;
        }
        return targetExe;
    }

    private File buildMakefile(File outputDir, File buildLog) throws Exception {
        String make = findMakeExecutable();
        if (make == null) {
            log.line("[INFO] Kein make/mingw32-make gefunden. Starte direkten C/C++ Compilerlauf...");
            return buildDirectCpp(findCppFiles(projectDir), outputDir, buildLog);
        }

        log.line("[INFO] Makefile-Build mit: " + make);
        List<String> cmd = Arrays.asList(make);
        try {
            runProcess(cmd, projectDir, buildLog);
        } catch (Exception ex) {
            log.line("[WARNUNG] Makefile-Lauf mit Fehler (" + ex.getMessage() + "). Starte automatischen Fallback auf direkten C/C++ Build...");
            return buildDirectCpp(findCppFiles(projectDir), outputDir, buildLog);
        }

        File targetExe = new File(outputDir, "programm.exe");
        File found = findFirstExecutable(projectDir);
        if (found != null && !found.equals(targetExe)) {
            Files.copy(found.toPath(), targetExe.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return targetExe;
        }
        return targetExe;
    }

    private File buildRust(File outputDir, File buildLog) throws Exception {
        String cargo = findExecutable("cargo");
        if (cargo == null) cargo = findExecutable("rustc");
        if (cargo == null) throw new IOException("Rust (cargo/rustc) wurde auf dem System nicht gefunden.");

        File artifact = new File(outputDir, "programm.exe");
        List<String> cmd = Arrays.asList(cargo, "build", "--release");
        log.line("[INFO] Starte Cargo Release-Build …");
        runProcess(cmd, projectDir, buildLog);

        File targetRelease = new File(new File(projectDir, "target"), "release");
        File found = findFirstExecutable(targetRelease);
        if (found != null) {
            Files.copy(found.toPath(), artifact.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return artifact;
    }

    private File buildGo(File outputDir, File buildLog) throws Exception {
        String go = findExecutable("go");
        if (go == null) throw new IOException("Go Compiler (go.exe) wurde auf dem System nicht gefunden.");

        File artifact = new File(outputDir, "programm.exe");
        List<String> cmd = Arrays.asList(go, "build", "-o", artifact.getAbsolutePath(), ".");
        log.line("[INFO] Starte Go Build …");
        runProcess(cmd, projectDir, buildLog);
        return artifact;
    }

    private void runProcess(List<String> cmd, File cwd, File buildLog) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd);
        pb.redirectErrorStream(true);

        // Toolchain-Pfade (z.B. MSYS2/MinGW, LLVM) zu PATH hinzufügen
        Map<String, String> env = pb.environment();
        String currentPath = env.getOrDefault("PATH", "");
        StringBuilder pathBuilder = new StringBuilder();
        if (!cmd.isEmpty()) {
            File exeFile = new File(cmd.get(0));
            File parentDir = exeFile.getParentFile();
            if (parentDir != null && parentDir.isDirectory()) {
                pathBuilder.append(parentDir.getAbsolutePath()).append(File.pathSeparator);
            }
        }
        String[] toolDirs = {
                "C:\\msys64\\mingw64\\bin",
                "C:\\msys64\\ucrt64\\bin",
                "C:\\msys64\\usr\\bin",
                "C:\\Program Files\\LLVM\\bin"
        };
        for (String td : toolDirs) {
            File f = new File(td);
            if (f.isDirectory()) {
                pathBuilder.append(f.getAbsolutePath()).append(File.pathSeparator);
            }
        }
        pathBuilder.append(currentPath);
        env.put("PATH", pathBuilder.toString());

        activeProcess = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream(), StandardCharsets.UTF_8));
             FileOutputStream logOut = new FileOutputStream(buildLog, true)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled) {
                    activeProcess.destroyForcibly();
                    throw new InterruptedException("Build vom Benutzer abgebrochen.");
                }
                log.line(line);
                logOut.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }

        int exitCode = activeProcess.waitFor();
        activeProcess = null;

        if (exitCode != 0) {
            throw new IOException("Prozess beendet mit Fehlercode " + exitCode + ". Siehe KR_BUILD/build.log für Details.");
        }
    }

    private static String findMakeExecutable() {
        String exe = findExecutable("mingw32-make");
        if (exe != null) return exe;
        exe = findExecutable("make");
        if (exe != null) return exe;

        String[] wellKnown = {
                "C:\\msys64\\mingw64\\bin\\mingw32-make.exe",
                "C:\\msys64\\ucrt64\\bin\\mingw32-make.exe",
                "C:\\msys64\\usr\\bin\\make.exe"
        };
        for (String p : wellKnown) {
            File f = new File(p);
            if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }

    private static String findClangExecutable() {
        String exe = findExecutable("clang++");
        if (exe != null) return exe;
        exe = findExecutable("g++");
        if (exe != null) return exe;

        String[] wellKnown = {
                "C:\\Program Files\\LLVM\\bin\\clang++.exe",
                "C:\\msys64\\mingw64\bin\\clang++.exe",
                "C:\\msys64\\mingw64\\bin\\g++.exe",
                "C:\\msys64\\ucrt64\\bin\\g++.exe"
        };
        for (String p : wellKnown) {
            File f = new File(p);
            if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }

    private static String findExecutable(String name) {
        String withExe = name.toLowerCase(Locale.ROOT).endsWith(".exe") ? name : name + ".exe";
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                File candidate = new File(dir, withExe);
                if (candidate.isFile() && candidate.canExecute()) return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    private static boolean hasGoFiles(File dir) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".go"));
        return files != null && files.length > 0;
    }

    private static List<File> findCppFiles(File dir) {
        List<File> list = new ArrayList<>();
        scanCpp(dir, list);
        return list;
    }

    private static void scanCpp(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith(".") || f.getName().equals("KR_BUILD")) continue;
            if (f.isDirectory()) {
                scanCpp(f, out);
            } else if (f.isFile()) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".cpp") || n.endsWith(".cc") || n.endsWith(".cxx") || n.endsWith(".c")) {
                    out.add(f);
                }
            }
        }
    }

    private static File findFirstExecutable(File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".exe")) {
                return f;
            }
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                File sub = findFirstExecutable(f);
                if (sub != null) return sub;
            }
        }
        return null;
    }
}
