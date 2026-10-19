package kr.compile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class CompilerEngine {
    public enum Target {
        WINDOWS("Windows x64 (.exe)"),
        LINUX("Linux x64 (ELF)"),
        ANDROID("Android ARM64 (.apk)");

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

        if (ToolchainManager.getInstance().isEmbeddedToolchainPresent()) {
            log.line("[TOOLCHAIN] Integrierte Windows-Toolchain geladen: " + ToolchainManager.getInstance().getToolchainDir().getAbsolutePath());
        }

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

        // Rust-Kompilierung wenn im KR-Projekt verwendet
        File rustArchive = null;
        if (res.codeResult != null && res.codeResult.hasRust && res.codeResult.rustDir != null) {
            String cargo = ToolchainManager.getInstance().getCargoExecutable();
            if (cargo == null) cargo = findExecutable("cargo");
            if (cargo != null) {
                log.line("[INFO] Kompiliere Rust-Modul mit Cargo …");
                List<String> cargoCmd;
                if (target == Target.ANDROID) {
                    cargoCmd = Arrays.asList(cargo, "build", "--target", "aarch64-linux-android", "--release");
                } else {
                    cargoCmd = Arrays.asList(cargo, "build", "--release");
                }
                try {
                    runProcess(cargoCmd, res.codeResult.rustDir, buildLog);
                    File a = target == Target.ANDROID
                            ? new File(new File(res.codeResult.rustDir, "target/aarch64-linux-android/release"), "libkr_rust.a")
                            : new File(new File(res.codeResult.rustDir, "target/release"), "libkr_rust.a");
                    if (!a.isFile()) {
                        a = new File(new File(res.codeResult.rustDir, "target/release"), "libkr_rust.a");
                    }
                    if (a.isFile()) {
                        rustArchive = a;
                        log.line("[INFO] Rust-Statische Bibliothek erfolgreich erzeugt: " + a.getName() + " (" + a.length() + " Bytes)");
                    }
                } catch (Exception ex) {
                    log.line("[FEHLER] Cargo-Build fehlgeschlagen: " + ex.getMessage());
                    throw new IOException("Cargo-Build fehlgeschlagen: " + ex.getMessage(), ex);
                }
            } else {
                throw new IOException("Portable Rust-Toolchain fehlt; KR ersetzt Rust-Symbole nicht durch Attrappen.");
            }
        }

        // Go-Kompilierung wenn im KR-Projekt verwendet
        File goArchive = null;
        if (res.codeResult != null && res.codeResult.hasGo && res.codeResult.goDir != null) {
            String go = ToolchainManager.getInstance().getGoExecutable();
            if (go == null) go = findExecutable("go");
            if (go != null) {
                log.line("[INFO] Kompiliere Go-Modul mit Go …");
                List<String> goCmd;
                if (target == Target.ANDROID) {
                    goCmd = Arrays.asList(go, "build", "-buildmode=c-shared", "-o", "libkr_go.so", ".");
                } else {
                    goCmd = Arrays.asList(go, "build", "-buildmode=c-archive", "-o", "libkr_go.a", ".");
                }
                try {
                    runProcess(goCmd, res.codeResult.goDir, buildLog);
                    File a = target == Target.ANDROID
                            ? new File(res.codeResult.goDir, "libkr_go.so")
                            : new File(res.codeResult.goDir, "libkr_go.a");
                    if (a.isFile()) {
                        goArchive = a;
                        log.line("[INFO] Go-Bibliothek erfolgreich erzeugt: " + a.getName() + " (" + a.length() + " Bytes)");
                    }
                } catch (Exception ex) {
                    log.line("[FEHLER] Go-Build fehlgeschlagen: " + ex.getMessage());
                    throw new IOException("Go-Build fehlgeschlagen: " + ex.getMessage(), ex);
                }
            } else {
                throw new IOException("Portable Go-Toolchain fehlt; KR ersetzt Go-Symbole nicht durch Attrappen.");
            }
        }

        // Automatische Fallback-Bridge generieren für alle Symbole, die nicht über die Archive bereitgestellt werden
        File fallbackBridge = null;

        // Wenn modularer C++ Output mit CMakeLists.txt im Projekt vorliegt:
        File cmakeLists = new File(projectDir, "CMakeLists.txt");
        if (cmakeLists.isFile()) {
            return buildCMake(outputDir, buildLog);
        }

        List<File> sourcesToCompile = new ArrayList<>();
        if (res.codeResult != null && res.codeResult.cppSources != null && !res.codeResult.cppSources.isEmpty()) {
            sourcesToCompile.addAll(res.codeResult.cppSources);
        } else if (res.codeResult != null && res.codeResult.cppSource != null && res.codeResult.cppSource.isFile()) {
            sourcesToCompile.add(res.codeResult.cppSource);
        }

        if (sourcesToCompile.isEmpty()) {
            sourcesToCompile.addAll(findCppFiles(projectDir));
        }
        File ccRoot = new File(projectDir, "components/cc-opencl");
        File ccDriver = new File(ccRoot, "src/CC_OpenCL.c");
        File ccNoise = new File(ccRoot, "src/CipherCore_NoiseCtrl.c");
        if (ccDriver.isFile() && ccNoise.isFile()) {
            sourcesToCompile.add(ccDriver);
            sourcesToCompile.add(ccNoise);
            log.line("[COMPUTE] CC_OpenCL Enterprise wird aus dem portablen Quellpaket eingebunden.");
        }

        if (rustArchive != null) {
            sourcesToCompile.add(rustArchive);
        }
        if (goArchive != null) {
            sourcesToCompile.add(goArchive);
        }
        if (fallbackBridge != null && fallbackBridge.isFile()) {
            sourcesToCompile.add(fallbackBridge);
        }

        return buildDirectCpp(sourcesToCompile, outputDir, buildLog);
    }

    private File generateFallbackBridge(File outputDir, KrPipeline.PipelineResult res, boolean hasRustLib, boolean hasGoLib) throws IOException {
        List<KrFunction> stubs = new ArrayList<>();
        if (!hasRustLib && res.symbolTable != null) {
            stubs.addAll(res.symbolTable.getCppCallsRust().values());
        }
        if (!hasGoLib && res.symbolTable != null) {
            stubs.addAll(res.symbolTable.getCppCallsGo().values());
        }
        if (stubs.isEmpty()) return null;

        File stubFile = new File(outputDir, "kr_abi_fallback.cpp");
        StringBuilder sb = new StringBuilder();
        sb.append("// Automatisch generierte C++ ABI Fallback-Bridge fuer KR Unified Cross-Language Calls\n");
        sb.append("#include \"kr_bridge.h\"\n");
        sb.append("#include <iostream>\n#include <cstdint>\n#include <stdbool.h>\n\n");
        sb.append("#ifdef __cplusplus\nextern \"C\" {\n#endif\n\n");

        Set<String> generatedNames = new HashSet<>();
        for (KrFunction fn : stubs) {
            if (generatedNames.contains(fn.name)) continue;
            generatedNames.add(fn.name);

            sb.append(fn.returnType.toCType()).append(" ").append(fn.name).append("(");
            if (fn.parameters.isEmpty()) {
                sb.append("void");
            } else {
                for (int i = 0; i < fn.parameters.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(fn.parameters.get(i).type.toCType()).append(" ").append(fn.parameters.get(i).name);
                }
            }
            sb.append(") {\n");
            sb.append("    std::cout << \"[KR_ABI_FALLBACK] Aufruf von ").append(fn.name).append(" (Sprache: ").append(fn.definingLanguage.getDisplayName()).append(")\" << std::endl;\n");
            if (fn.returnType.kind == KrType.Kind.VOID) {
                // void
            } else if (fn.returnType.kind == KrType.Kind.BOOL) {
                sb.append("    return true;\n");
            } else if (fn.returnType.kind == KrType.Kind.STRING) {
                sb.append("    return \"ok\";\n");
            } else {
                sb.append("    return 1;\n");
            }
            sb.append("}\n\n");
        }

        sb.append("#ifdef __cplusplus\n}\n#endif\n");
        Files.writeString(stubFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
        return stubFile;
    }

    private File buildDirectCpp(List<File> sources, File outputDir, File buildLog) throws Exception {
        String clang = null;
        if (target == Target.ANDROID) {
            clang = ToolchainManager.getInstance().getAndroidCompilerExecutable();
        }
        if (clang == null) {
            clang = ToolchainManager.getInstance().getCompilerExecutable();
        }
        if (clang == null) {
            clang = findClangExecutable();
        }
        if (clang == null) {
            throw new IOException("Keine C/C++ Compiler-Toolchain gefunden. Bitte stellen Sie sicher, dass der Ordner 'toolchain' im IDE-Verzeichnis vorhanden ist.");
        }

        String exeName = target == Target.WINDOWS ? "programm.exe" : (target == Target.LINUX ? "programm" : "libuser.so");
        File artifact = new File(outputDir, exeName);

        List<String> cmd = new ArrayList<>();
        cmd.add(clang);
        cmd.add("-std=c++20");
        cmd.add("-O3");
        cmd.add("-Wl,--allow-multiple-definition");
        cmd.add("-Wno-deprecated");

        if (target == Target.ANDROID) {
            cmd.add("-shared");
            cmd.add("-fPIC");
            cmd.add("-Wl,--export-dynamic");
        } else {
            cmd.add("-static-libgcc");
            cmd.add("-static-libstdc++");
            cmd.add("-mconsole");
        }

        cmd.add("-I" + projectDir.getAbsolutePath());
        cmd.add("-I" + outputDir.getAbsolutePath());
        cmd.add("-L" + projectDir.getAbsolutePath());
        cmd.add("-L" + outputDir.getAbsolutePath());

        File tc = ToolchainManager.getInstance().getToolchainDir();
        if (tc != null) {
            File tpInc = new File(new File(tc, "include"), "third_party");
            if (tpInc.isDirectory()) {
                cmd.add("-I" + tpInc.getAbsolutePath());
            }
            if (target != Target.ANDROID) {
                File tcInc = ToolchainManager.getInstance().getIncludeDir();
                if (tcInc != null) {
                    cmd.add("-I" + tcInc.getAbsolutePath());
                }
                File tcLib = ToolchainManager.getInstance().getLibDir();
                if (tcLib != null) {
                    cmd.add("-L" + tcLib.getAbsolutePath());
                }
            } else {
                File androidDir = new File(tc, "android");
                if (androidDir.isDirectory()) {
                    cmd.add("-L" + androidDir.getAbsolutePath());
                }
            }
        }

        // Gängige Include-Verzeichnisse automatisch einbinden
        for (String sub : new String[]{"include", "inc", "src", "locale", "samples", "CL"}) {
            File subDir = new File(projectDir, sub);
            if (subDir.isDirectory()) {
                cmd.add("-I" + subDir.getAbsolutePath());
            }
        }
        File bundledCcRoot = new File(projectDir, "components/cc-opencl");
        if (bundledCcRoot.isDirectory()) {
            cmd.add("-I" + bundledCcRoot.getAbsolutePath());
            cmd.add("-I" + new File(bundledCcRoot, "include").getAbsolutePath());
            cmd.add("-I" + new File(bundledCcRoot, "CL").getAbsolutePath());
        }

        // Erkennung von DLL-Export-Projekten (wie Mycelia i18n_api)
        File i18nHeader = new File(projectDir, "i18n_api.h");
        if (i18nHeader.isFile()) {
            cmd.add("-DI18N_EXPORTS");
        }

        File bridgeHeader = new File(projectDir, "kr_bridge.h");
        if (!bridgeHeader.isFile()) {
            bridgeHeader = new File(outputDir, "kr_bridge.h");
        }
        if (bridgeHeader.isFile()) {
            cmd.add("-include");
            cmd.add(bridgeHeader.getAbsolutePath());
        }

        for (File s : sources) {
            cmd.add(s.getAbsolutePath());
        }

        // Automatische Erkennung und Verlinkung von OpenCL, SDL3 und SQLite3
        boolean needsOpenCl = false;
        boolean needsSdl3 = false;
        boolean needsSqlite3 = false;
        for (File s : sources) {
            String name = s.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".cpp") || name.endsWith(".c")) {
                try {
                    String content = Files.readString(s.toPath(), StandardCharsets.UTF_8);
                    if (content.contains("CL/cl.h") || content.contains("CL/opencl.h") || content.contains("clGetPlatformIDs") || content.contains("clCreateContext")) {
                        needsOpenCl = true;
                    }
                    if (content.contains("SDL3/SDL.h") || content.contains("SDL_Init")) needsSdl3 = true;
                    if (content.contains("sqlite3.h") || content.contains("sqlite3_open")) needsSqlite3 = true;
                } catch (Exception ignored) {}
            }
        }
        File pf = KrProjectConfig.findProjectConfigFile(projectDir);
        if (pf != null) {
            KrProjectConfig pcfg = KrProjectConfig.parse(pf, projectDir.getName(), false);
            for (String dep : pcfg.vcpkgDependencies) {
                String d = dep.toLowerCase(Locale.ROOT);
                if (d.contains("opencl")) needsOpenCl = true;
                if (d.contains("sdl3") || d.contains("sdl")) needsSdl3 = true;
                if (d.contains("sqlite")) needsSqlite3 = true;
            }
        }

        if (needsOpenCl) {
            cmd.add("-DCL_TARGET_OPENCL_VERSION=300");
            cmd.add("-DCL_USE_DEPRECATED_OPENCL_1_2_APIS");
            if (target == Target.ANDROID) {
                cmd.add("-lOpenCL");
            } else if (target == Target.WINDOWS) {
                File clDir = new File(projectDir, "CL");
                File libDllA = new File(clDir, "libOpenCL.dll.a");
                if (libDllA.isFile()) {
                    cmd.add("-L" + clDir.getAbsolutePath());
                    cmd.add("-lOpenCL");
                } else if (ToolchainManager.getInstance().getLibDir() != null
                        && new File(ToolchainManager.getInstance().getLibDir(), "libOpenCL.dll.a").isFile()) {
                    cmd.add("-lOpenCL");
                }
            } else if (target == Target.LINUX) {
                cmd.add("-lOpenCL");
            }
        }

        if (needsSqlite3) {
            cmd.add("-lsqlite3");
        }
        if (needsSdl3) {
            cmd.add("-lSDL3");
            if (target != Target.ANDROID) {
                cmd.add("-lgdi32");
                cmd.add("-luser32");
                cmd.add("-limm32");
                cmd.add("-lole32");
                cmd.add("-loleaut32");
                cmd.add("-lshell32");
                cmd.add("-lsetupapi");
                cmd.add("-lversion");
                cmd.add("-lwinmm");
            }
        }

        if (target == Target.ANDROID) {
            cmd.add("-llog");
            cmd.add("-landroid");
        } else {
            cmd.add("-lws2_32");
            cmd.add("-luserenv");
            cmd.add("-lbcrypt");
            cmd.add("-lntdll");
            cmd.add("-lwinmm");
        }

        cmd.add("-o");
        cmd.add(artifact.getAbsolutePath());

        log.line("[EXEC] Führe Compiler aus: " + clang + " -> " + artifact.getName());
        runProcess(cmd, projectDir, buildLog);

        if (target == Target.LINUX) {
            File linuxBin = new File(outputDir, "programm");
            File exeFile = new File(outputDir, "programm.exe");
            if (exeFile.exists()) {
                Files.copy(exeFile.toPath(), linuxBin.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (linuxBin.exists()) {
                artifact = linuxBin;
            } else {
                throw new IOException("Kompilierung beendet, aber Artefakt wurde nicht erzeugt: " + artifact);
            }
        } else if (!artifact.exists()) {
            File withExe = new File(outputDir, artifact.getName() + ".exe");
            if (withExe.exists()) {
                artifact = withExe;
            } else {
                throw new IOException("Kompilierung beendet, aber Artefakt wurde nicht erzeugt: " + artifact);
            }
        }

        if (needsSdl3 && ToolchainManager.getInstance().getToolchainDir() != null) {
            File sdlDll = new File(ToolchainManager.getInstance().getToolchainDir(), "bin/SDL3.dll");
            if (sdlDll.isFile()) {
                try {
                    Files.copy(sdlDll.toPath(), new File(outputDir, "SDL3.dll").toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }

        if (target == Target.WINDOWS && artifact.getName().toLowerCase(Locale.ROOT).endsWith(".exe")) {
            String base = artifact.getName().substring(0, artifact.getName().length() - 4);
            File runBat = new File(outputDir, "start_" + base + ".bat");
            String batContent = "@echo off\r\n"
                    + "chcp 65001 >nul\r\n"
                    + "title KR Programm - " + artifact.getName() + "\r\n"
                    + "cd /d \"%~dp0\"\r\n"
                    + "echo ========================================================\r\n"
                    + "echo   KR Programm Ausfuehrung: " + artifact.getName() + "\r\n"
                    + "echo ========================================================\r\n"
                    + "echo.\r\n"
                    + "\"%~dp0" + artifact.getName() + "\"\r\n"
                    + "echo.\r\n"
                    + "echo --------------------------------------------------------\r\n"
                    + "echo [BEENDET] Rueckgabewert / Exit Code: %ERRORLEVEL%\r\n"
                    + "echo.\r\n"
                    + "echo Druecken Sie eine beliebige Taste, um dieses Fenster zu schliessen...\r\n"
                    + "pause >nul\r\n";
            try {
                Files.writeString(runBat.toPath(), batContent, StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }

        if (target == Target.ANDROID) {
            return packageAndroidApk(outputDir, artifact, Collections.emptyList(), projectDir, needsSdl3, buildLog);
        }

        log.line("[INFO] Erzeugt: " + artifact.getName() + " (" + artifact.length() + " Bytes)");
        return artifact;
    }

    private File packageAndroidApk(File outputDir, File userSo, File projectDir, boolean isGui, File buildLog) throws Exception {
        return packageAndroidApk(outputDir, userSo, Collections.emptyList(), projectDir, isGui, buildLog);
    }

    private File packageAndroidApk(File outputDir, File userSo, List<File> extraLibraries, File projectDir, boolean isGui, File buildLog) throws Exception {
        log.line("[INFO] Erstelle Android ARM64 Runner APK …");
        File toolchainDir = ToolchainManager.getInstance().getToolchainDir();
        File androidDir = new File(toolchainDir, "android");
        File templateApk = new File(androidDir, "runner-template.apk");
        if (!templateApk.isFile()) {
            throw new IOException("Android Runner-Template nicht gefunden: " + templateApk);
        }
        File cppRuntime = new File(androidDir, "libc++_shared.so");
        File certFile = new File(androidDir, "kr-runner-cert.der");
        File keyFile = new File(androidDir, "kr-runner-key.pk8");

        File apkArtifact = new File(outputDir, "programm-android-arm64.apk");
        File unsignedApk = new File(outputDir, "kr-runner-unsigned.apk");
        File projectZip = new File(outputDir, "kr-runner-project-data.zip");

        // 1. Projektdaten packen
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(projectZip)))) {
            addProjectFiles(projectDir, projectDir, zos, new byte[64 * 1024]);
        }

        // 2. Unsigned APK aus Template zusammenbauen
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(templateApk)));
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(unsignedApk)))) {
            byte[] buf = new byte[64 * 1024];
            Set<String> written = new HashSet<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.isEmpty() || entry.isDirectory() || name.startsWith("META-INF/")
                        || name.equals("lib/arm64-v8a/libuser.so")
                        || name.equals("lib/arm64-v8a/libc++_shared.so")
                        || name.equals("lib/arm64-v8a/libsqlite3.so")
                        || name.equals("lib/arm64-v8a/libSDL3.so")
                        || name.equals("lib/arm64-v8a/libkr_go.so")
                        || name.equals("assets/kr-project-data.zip")
                        || name.equals("assets/kr-gui-runner")) {
                    continue;
                }
                if (written.add(name)) {
                    ZipEntry copy = new ZipEntry(name);
                    copy.setTime(0);
                    zos.putNextEntry(copy);
                    int n;
                    while ((n = zis.read(buf)) >= 0) zos.write(buf, 0, n);
                    zos.closeEntry();
                }
            }
            if (userSo != null && userSo.isFile()) {
                addZipFile(zos, written, "lib/arm64-v8a/libuser.so", userSo, buf);
            }
            if (extraLibraries != null) {
                for (File extra : extraLibraries) {
                    if (extra != null && extra.isFile() && !extra.getName().equalsIgnoreCase("libOpenCL.so")) {
                        addZipFile(zos, written, "lib/arm64-v8a/" + extra.getName(), extra, buf);
                    }
                }
            }
            if (cppRuntime.isFile()) {
                addZipFile(zos, written, "lib/arm64-v8a/libc++_shared.so", cppRuntime, buf);
            }
            File sqliteSo = new File(androidDir, "libsqlite3.so");
            if (sqliteSo.isFile()) {
                addZipFile(zos, written, "lib/arm64-v8a/libsqlite3.so", sqliteSo, buf);
            }
            File sdl3So = new File(androidDir, "libSDL3.so");
            if (sdl3So.isFile()) {
                addZipFile(zos, written, "lib/arm64-v8a/libSDL3.so", sdl3So, buf);
            }
            File goSo = new File(outputDir, "libkr_go.so");
            if (!goSo.isFile()) {
                goSo = new File(new File(projectDir, "KR_BUILD/go_module"), "libkr_go.so");
            }
            if (goSo.isFile()) {
                addZipFile(zos, written, "lib/arm64-v8a/libkr_go.so", goSo, buf);
            }
            if (projectZip.isFile()) {
                addZipFile(zos, written, "assets/kr-project-data.zip", projectZip, buf);
            }
            if (isGui) {
                ZipEntry guiEntry = new ZipEntry("assets/kr-gui-runner");
                guiEntry.setTime(0);
                zos.putNextEntry(guiEntry);
                zos.write("SDL3\n".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }

        // 3. APK signieren
        String apksigner = findApksigner();
        if (apksigner != null && keyFile.isFile() && certFile.isFile()) {
            log.line("[INFO] Signiere Android APK mit apksigner …");
            List<String> signCmd = Arrays.asList(
                    apksigner, "sign",
                    "--key", keyFile.getAbsolutePath(),
                    "--cert", certFile.getAbsolutePath(),
                    "--out", apkArtifact.getAbsolutePath(),
                    unsignedApk.getAbsolutePath()
            );
            runProcess(signCmd, outputDir, buildLog);
        } else {
            Files.copy(unsignedApk.toPath(), apkArtifact.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        try { unsignedApk.delete(); } catch (Exception ignored) {}
        try { projectZip.delete(); } catch (Exception ignored) {}

        log.line("[ERFOLG] Android ARM64 Runner APK erfolgreich erstellt: " + apkArtifact.getAbsolutePath() + " (" + apkArtifact.length() + " Bytes)");
        return apkArtifact;
    }

    private static void addProjectFiles(File root, File current, ZipOutputStream out, byte[] buf) throws IOException {
        File[] files = current.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            if (f.getName().equals("KR_BUILD") || f.getName().startsWith(".") || f.getName().equals("target")) continue;
            String relative = root.toPath().relativize(f.toPath()).toString().replace('\\', '/');
            if (f.isDirectory()) {
                addProjectFiles(root, f, out, buf);
            } else if (f.isFile()) {
                ZipEntry entry = new ZipEntry(relative);
                entry.setTime(0);
                out.putNextEntry(entry);
                try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
                    int n;
                    while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                }
                out.closeEntry();
            }
        }
    }

    private static void addZipFile(ZipOutputStream out, Set<String> written, String path, File file, byte[] buf) throws IOException {
        if (!written.add(path)) return;
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(0);
        out.putNextEntry(entry);
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        }
        out.closeEntry();
    }

    private static String findApksigner() {
        String[] wellKnown = {
                "C:\\Users\\ralfk\\AppData\\Local\\Android\\Sdk\\build-tools\\35.0.0\\apksigner.bat",
                "C:\\Users\\ralfk\\AppData\\Local\\Android\\Sdk\\build-tools\\36.0.0\\apksigner.bat",
                "C:\\Users\\ralfk\\AppData\\Local\\Android\\Sdk\\build-tools\\34.0.0\\apksigner.bat",
                "C:\\Users\\ralfk\\AppData\\Local\\Android\\Sdk\\build-tools\\30.0.3\\apksigner.bat"
        };
        for (String p : wellKnown) {
            File f = new File(p);
            if (f.isFile()) return f.getAbsolutePath();
        }
        return null;
    }

    private boolean isGuiProject() {
        if (new File(projectDir, "kr-gui-target.txt").isFile()) return true;
        File vcpkg = new File(projectDir, "vcpkg.json");
        if (vcpkg.isFile()) {
            try {
                String c = Files.readString(vcpkg.toPath(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (c.contains("sdl3") || c.contains("sdl")) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static File findFirstSharedLibrary(File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".so")) {
                return f;
            }
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                File sub = findFirstSharedLibrary(f);
                if (sub != null) return sub;
            }
        }
        return null;
    }

    private static void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    private static File findTargetArtifact(File dir, String targetName, Target target) {
        if (dir == null || !dir.isDirectory() || targetName == null) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        String tLow = targetName.toLowerCase(Locale.ROOT);
        if (target == Target.ANDROID) {
            for (File f : files) {
                if (f.isFile()) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (n.equals("lib" + tLow + ".so") || n.equals(tLow + ".so")) {
                        return f;
                    }
                } else if (f.isDirectory() && !f.getName().startsWith(".")) {
                    File sub = findTargetArtifact(f, targetName, target);
                    if (sub != null && sub.getName().toLowerCase(Locale.ROOT).endsWith(".so")) return sub;
                }
            }
        }
        for (File f : files) {
            if (f.isFile()) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.equals("lib" + tLow + ".so") || n.equals(tLow + ".so")
                        || n.equals(tLow + ".exe") || n.equals(tLow + ".dll")
                        || n.equals("lib" + tLow + ".dll") || n.equals(tLow)) {
                    return f;
                }
            } else if (f.isDirectory() && !f.getName().startsWith(".")) {
                File sub = findTargetArtifact(f, targetName, target);
                if (sub != null) return sub;
            }
        }
        return null;
    }

    private static void collectSharedLibraries(File dir, List<File> list, File exclude) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile()) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".so") && !n.equalsIgnoreCase("libuser.so") && !n.equalsIgnoreCase("libOpenCL.so")) {
                    if (exclude == null || !f.equals(exclude)) {
                        boolean exists = false;
                        for (File already : list) {
                            if (already.getName().equalsIgnoreCase(f.getName())) { exists = true; break; }
                        }
                        if (!exists) list.add(f);
                    }
                }
            } else if (f.isDirectory() && !f.getName().startsWith(".")) {
                collectSharedLibraries(f, list, exclude);
            }
        }
    }

    private static void copyAllCMakeArtifacts(File dir, File destinationDir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile()) {
                String lower = f.getName().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".exe") || lower.endsWith(".dll") || lower.endsWith(".dll.a") || lower.endsWith(".so")) {
                    try {
                        File dest = new File(destinationDir, f.getName());
                        if (!f.getCanonicalPath().equalsIgnoreCase(dest.getCanonicalPath())) {
                            Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            dest.setExecutable(true, false);
                        }
                    } catch (Exception ignored) {}
                }
            } else if (f.isDirectory() && !f.getName().startsWith(".")) {
                copyAllCMakeArtifacts(f, destinationDir);
            }
        }
    }

    private File buildCMake(File outputDir, File buildLog) throws Exception {
        if (target == Target.ANDROID) {
            log.line("[INFO] Starte Android ARM64 CMake-Build …");
            File ndkTc = ToolchainManager.getInstance().getAndroidCMakeToolchainFile();
            List<File> cppFiles = findCppFiles(projectDir);

            File cmakeBuildDir = new File(outputDir, "cmake_build_android");
            deleteDirectory(cmakeBuildDir);
            cmakeBuildDir.mkdirs();

            String cmake = findCMakeExecutable();
            if (cmake != null && ndkTc != null) {
                try {
                    List<String> configCmd = new ArrayList<>();
                    configCmd.add(cmake);
                    configCmd.add("-S");
                    configCmd.add(projectDir.getAbsolutePath());
                    configCmd.add("-B");
                    configCmd.add(cmakeBuildDir.getAbsolutePath());

                    String ninja = ToolchainManager.getInstance().getNinjaExecutable();
                    if (ninja == null) ninja = findExecutable("ninja");
                    String make = ToolchainManager.getInstance().getMakeExecutable();
                    if (make == null) make = findMakeExecutable();

                    if (ninja != null) {
                        configCmd.add("-G");
                        configCmd.add("Ninja");
                        configCmd.add("-DCMAKE_MAKE_PROGRAM=" + ninja.replace('\\', '/'));
                    } else if (make != null) {
                        configCmd.add("-G");
                        configCmd.add("MinGW Makefiles");
                        configCmd.add("-DCMAKE_MAKE_PROGRAM=" + make.replace('\\', '/'));
                    }

                    File projectInclude = new File(cmakeBuildDir, "kr-android-project.cmake");
                    File execTargetList = new File(cmakeBuildDir, "kr-android-exec-targets.txt");
                    String hookContent = "set(CMAKE_HAVE_LIBC_PTHREAD 1 CACHE INTERNAL \"\" FORCE)\n"
                            + "set(CMAKE_THREAD_LIBS_INIT \"\" CACHE STRING \"\" FORCE)\n"
                            + "set(CMAKE_USE_PTHREADS_INIT 1 CACHE INTERNAL \"\" FORCE)\n"
                            + "set(Threads_FOUND TRUE CACHE BOOL \"\" FORCE)\n"
                            + "set_property(GLOBAL PROPERTY KR_EXEC_TARGETS \"\")\n"
                            + "function(add_executable target)\n"
                            + "  set(_kr_args ${ARGN})\n"
                            + "  list(FIND _kr_args \"IMPORTED\" _kr_imported)\n"
                            + "  list(FIND _kr_args \"ALIAS\" _kr_alias)\n"
                            + "  if(NOT _kr_imported EQUAL -1 OR NOT _kr_alias EQUAL -1)\n"
                            + "    _add_executable(${target} ${ARGN})\n"
                            + "    return()\n"
                            + "  endif()\n"
                            + "  list(REMOVE_ITEM _kr_args WIN32 MACOSX_BUNDLE EXCLUDE_FROM_ALL)\n"
                            + "  add_library(${target} SHARED ${_kr_args})\n"
                            + "  set_target_properties(${target} PROPERTIES PREFIX \"lib\")\n"
                            + "  target_link_options(${target} PRIVATE -Wl,--export-dynamic)\n"
                            + "  set_property(GLOBAL APPEND PROPERTY KR_EXEC_TARGETS \"${target}\")\n"
                            + "endfunction()\n"
                            + "cmake_language(DEFER CALL kr_write_android_targets)\n"
                            + "function(kr_write_android_targets)\n"
                            + "  get_property(_targets GLOBAL PROPERTY KR_EXEC_TARGETS)\n"
                            + "  if(_targets)\n"
                            + "    string(REPLACE \";\" \"\\n\" _tlines \"${_targets}\")\n"
                            + "    file(WRITE \"" + execTargetList.getAbsolutePath().replace('\\', '/') + "\" \"${_tlines}\\n\")\n"
                            + "  endif()\n"
                            + "endfunction()\n";
                    Files.writeString(projectInclude.toPath(), hookContent, StandardCharsets.UTF_8);

                    configCmd.add("--no-warn-unused-cli");
                    configCmd.add("-DCMAKE_TOOLCHAIN_FILE=" + ndkTc.getAbsolutePath().replace('\\', '/'));
                    configCmd.add("-DANDROID_ABI=arm64-v8a");
                    configCmd.add("-DANDROID_PLATFORM=android-26");
                    configCmd.add("-DCMAKE_BUILD_TYPE=Release");
                    configCmd.add("-DCMAKE_COMPILE_WARNING_AS_ERROR=OFF");
                    configCmd.add("-DCMAKE_PROJECT_TOP_LEVEL_INCLUDES=" + projectInclude.getAbsolutePath().replace('\\', '/'));

                    String ldFlags = "-Wl,--export-dynamic";
                    File tcDir = ToolchainManager.getInstance().getToolchainDir();
                    File tcAndroid = tcDir != null ? new File(tcDir, "android") : null;
                    if (tcAndroid != null && tcAndroid.isDirectory()) {
                        String tcAndroidPath = tcAndroid.getAbsolutePath().replace('\\', '/');
                        ldFlags = "-L" + tcAndroidPath + " -Wl,--export-dynamic";
                        File openclSo = new File(tcAndroid, "libOpenCL.so");
                        if (openclSo.isFile()) {
                            configCmd.add("-DOpenCL_LIBRARY=" + openclSo.getAbsolutePath().replace('\\', '/'));
                        }
                        File projectCl = new File(projectDir, "CL");
                        if (projectCl.isDirectory()) {
                            configCmd.add("-DOpenCL_INCLUDE_DIR=" + projectCl.getAbsolutePath().replace('\\', '/'));
                        } else {
                            File incCl = new File(projectDir, "include/CL");
                            if (incCl.isDirectory()) {
                                configCmd.add("-DOpenCL_INCLUDE_DIR=" + incCl.getParentFile().getAbsolutePath().replace('\\', '/'));
                            }
                        }
                    }
                    configCmd.add("-DCMAKE_SHARED_LINKER_FLAGS=" + ldFlags);
                    configCmd.add("-DCMAKE_EXE_LINKER_FLAGS=" + ldFlags);

                    runProcess(configCmd, projectDir, buildLog);

                    File targetTxt = new File(projectDir, "kr-android-target.txt");
                    String wantedTarget = null;
                    if (targetTxt.isFile()) {
                        try {
                            List<String> lines = Files.readAllLines(targetTxt.toPath(), StandardCharsets.UTF_8);
                            if (!lines.isEmpty() && !lines.get(0).trim().isEmpty()) {
                                wantedTarget = lines.get(0).trim();
                            }
                        } catch (Exception ignored) {}
                    }
                    if (wantedTarget == null && execTargetList.isFile()) {
                        try {
                            List<String> lines = Files.readAllLines(execTargetList.toPath(), StandardCharsets.UTF_8);
                            for (String l : lines) {
                                if (!l.trim().isEmpty()) {
                                    wantedTarget = l.trim();
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    List<String> buildCmd = new ArrayList<>();
                    buildCmd.add(cmake);
                    buildCmd.add("--build");
                    buildCmd.add(cmakeBuildDir.getAbsolutePath());
                    buildCmd.add("--config");
                    buildCmd.add("Release");
                    if (wantedTarget != null) {
                        log.line("[INFO] Ziel für Android ARM64 ausgewählt: " + wantedTarget);
                    }
                    runProcess(buildCmd, projectDir, buildLog);
                } catch (Exception ex) {
                    log.line("[WARNUNG] CMake NDK-Build mit Fehler (" + ex.getMessage() + "). Starte automatischen Fallback auf direkten NDK Clang-Build …");
                }
            }

            File targetTxt = new File(projectDir, "kr-android-target.txt");
            String wantedTarget = null;
            if (targetTxt.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(targetTxt.toPath(), StandardCharsets.UTF_8);
                    if (!lines.isEmpty() && !lines.get(0).trim().isEmpty()) {
                        wantedTarget = lines.get(0).trim();
                    }
                } catch (Exception ignored) {}
            }
            File execTargetList = new File(cmakeBuildDir, "kr-android-exec-targets.txt");
            if (wantedTarget == null && execTargetList.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(execTargetList.toPath(), StandardCharsets.UTF_8);
                    for (String l : lines) {
                        if (!l.trim().isEmpty()) {
                            wantedTarget = l.trim();
                            log.line("[INFO] Primäres Android-Ziel aus CMake add_executable ausgewählt: " + wantedTarget);
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }

            File primaryArtifact = null;
            if (wantedTarget != null) {
                primaryArtifact = findTargetArtifact(cmakeBuildDir, wantedTarget, target);
                if (primaryArtifact == null) primaryArtifact = findTargetArtifact(outputDir, wantedTarget, target);
                if (primaryArtifact == null) primaryArtifact = findTargetArtifact(new File(projectDir, "bin"), wantedTarget, target);
            }
            if (primaryArtifact == null) {
                primaryArtifact = findFirstSharedLibrary(cmakeBuildDir);
            }
            if (primaryArtifact == null) {
                primaryArtifact = findFirstExecutable(cmakeBuildDir);
            }
            if (primaryArtifact == null) {
                primaryArtifact = findFirstSharedLibrary(new File(projectDir, "bin"));
            }
            if (primaryArtifact == null) {
                primaryArtifact = findFirstSharedLibrary(outputDir);
            }

            File userSo = new File(outputDir, "libuser.so");
            if (primaryArtifact != null && !primaryArtifact.equals(userSo)) {
                Files.copy(primaryArtifact.toPath(), userSo.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            List<File> extraLibs = new ArrayList<>();
            collectSharedLibraries(cmakeBuildDir, extraLibs, primaryArtifact);
            File projectBin = new File(projectDir, "bin");
            if (projectBin.isDirectory()) {
                collectSharedLibraries(projectBin, extraLibs, primaryArtifact);
            }

            if (!userSo.isFile()) {
                if (!cppFiles.isEmpty()) {
                    log.line("[INFO] Kompiliere " + cppFiles.size() + " C/C++-Quelldatei(en) direkt mit Android NDK Clang für ARM64 …");
                    return buildDirectCpp(cppFiles, outputDir, buildLog);
                }
                throw new IOException("Weder CMake noch der direkte Compilerlauf konnten eine Android libuser.so erzeugen.");
            }

            boolean isGui = isGuiProject();
            return packageAndroidApk(outputDir, userSo, extraLibs, projectDir, isGui, buildLog);
        } else if (target == Target.LINUX) {
            String cmake = findCMakeExecutable();
            if (cmake == null) throw new IOException("cmake.exe nicht gefunden.");

            File cmakeBuildDir = new File(outputDir, "cmake_build");
            cmakeBuildDir.mkdirs();

            List<String> configCmd = new ArrayList<>();
            configCmd.add(cmake);
            configCmd.add("-S");
            configCmd.add(projectDir.getAbsolutePath());
            configCmd.add("-B");
            configCmd.add(cmakeBuildDir.getAbsolutePath());

            String ninja = ToolchainManager.getInstance().getNinjaExecutable();
            if (ninja == null) ninja = findExecutable("ninja");
            String make = ToolchainManager.getInstance().getMakeExecutable();
            if (make == null) make = findMakeExecutable();

            if (ninja != null) {
                configCmd.add("-G");
                configCmd.add("Ninja");
                configCmd.add("-DCMAKE_MAKE_PROGRAM=" + ninja.replace('\\', '/'));
            } else if (make != null) {
                configCmd.add("-G");
                configCmd.add("MinGW Makefiles");
                configCmd.add("-DCMAKE_MAKE_PROGRAM=" + make.replace('\\', '/'));
            }

            configCmd.add("--no-warn-unused-cli");
            configCmd.add("-DCMAKE_SYSTEM_NAME=Linux");
            configCmd.add("-DCMAKE_BUILD_TYPE=Release");
            configCmd.add("-DCMAKE_COMPILE_WARNING_AS_ERROR=OFF");

            log.line("[INFO] Konfiguriere CMake-Projekt für Linux x64 …");
            runProcess(configCmd, projectDir, buildLog);

            List<String> buildCmd = Arrays.asList(cmake, "--build", cmakeBuildDir.getAbsolutePath(), "--config", "Release");
            log.line("[INFO] Kompiliere CMake-Projekt …");
            runProcess(buildCmd, projectDir, buildLog);

            File targetElf = new File(outputDir, "programm");
            File found = findFirstExecutable(cmakeBuildDir);
            if (found == null) found = findFirstExecutable(new File(projectDir, "bin"));
            if (found == null) found = findFirstExecutable(outputDir);
            if (found != null && !found.equals(targetElf)) {
                Files.copy(found.toPath(), targetElf.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return targetElf;
        } else {
            // Windows x64
            String cmake = findCMakeExecutable();
            if (cmake == null) throw new IOException("cmake.exe nicht gefunden. Bitte CMake für Windows installieren oder in die Toolchain einbetten.");

            File cmakeBuildDir = new File(outputDir, "cmake_build");
            cmakeBuildDir.mkdirs();

            List<String> configCmd = new ArrayList<>();
            configCmd.add(cmake);
            configCmd.add("-S");
            configCmd.add(projectDir.getAbsolutePath());
            configCmd.add("-B");
            configCmd.add(cmakeBuildDir.getAbsolutePath());

            String ninja = ToolchainManager.getInstance().getNinjaExecutable();
            if (ninja == null) ninja = findExecutable("ninja");
            String make = ToolchainManager.getInstance().getMakeExecutable();
            if (make == null) make = findMakeExecutable();

            if (ninja != null) {
                configCmd.add("-G");
                configCmd.add("Ninja");
                configCmd.add("-DCMAKE_MAKE_PROGRAM=" + ninja.replace('\\', '/'));
            } else if (make != null) {
                configCmd.add("-G");
                configCmd.add("MinGW Makefiles");
                configCmd.add("-DCMAKE_MAKE_PROGRAM=" + make.replace('\\', '/'));
            }

            String gcc = ToolchainManager.getInstance().getGccExecutable();
            if (gcc != null) configCmd.add("-DCMAKE_C_COMPILER=" + gcc.replace('\\', '/'));
            String gpp = ToolchainManager.getInstance().getCompilerExecutable();
            if (gpp != null) configCmd.add("-DCMAKE_CXX_COMPILER=" + gpp.replace('\\', '/'));

            File tcDir = ToolchainManager.getInstance().getToolchainDir();
            File openclLib = null;
            if (tcDir != null) {
                File tcLib = new File(tcDir, "lib/libOpenCL.dll.a");
                if (tcLib.isFile()) openclLib = tcLib;
            }
            if (openclLib == null) {
                File projCl = new File(projectDir, "CL/libOpenCL.dll.a");
                if (projCl.isFile()) openclLib = projCl;
            }
            if (openclLib != null) {
                configCmd.add("-DOpenCL_LIBRARY=" + openclLib.getAbsolutePath().replace('\\', '/'));
                File projClDir = new File(projectDir, "CL");
                if (projClDir.isDirectory()) {
                    configCmd.add("-DOpenCL_INCLUDE_DIR=" + projClDir.getAbsolutePath().replace('\\', '/'));
                }
            }

            configCmd.add("--no-warn-unused-cli");
            configCmd.add("-DCMAKE_BUILD_TYPE=Release");
            configCmd.add("-DCMAKE_EXPORT_COMPILE_COMMANDS=ON");
            configCmd.add("-DCMAKE_COMPILE_WARNING_AS_ERROR=OFF");

            log.line("[INFO] Konfiguriere CMake-Projekt …");
            runProcess(configCmd, projectDir, buildLog);

            List<String> buildCmd = Arrays.asList(cmake, "--build", cmakeBuildDir.getAbsolutePath(), "--config", "Release");
            log.line("[INFO] Kompiliere CMake-Projekt …");
            runProcess(buildCmd, projectDir, buildLog);

            copyAllCMakeArtifacts(cmakeBuildDir, outputDir);
            File projectBin = new File(projectDir, "bin");
            if (projectBin.isDirectory()) {
                copyAllCMakeArtifacts(projectBin, outputDir);
            }

            File targetExe = new File(outputDir, "programm.exe");
            File found = findFirstExecutable(cmakeBuildDir);
            if (found == null) {
                found = findFirstExecutable(projectBin);
            }
            if (found == null) {
                found = findFirstExecutable(outputDir);
            }
            if (found != null && !found.equals(targetExe)) {
                Files.copy(found.toPath(), targetExe.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (targetExe.isFile()) {
                File runBat = new File(outputDir, "start_programm.bat");
                String batContent = "@echo off\r\n"
                        + "chcp 65001 >nul\r\n"
                        + "title KR Programm - programm.exe\r\n"
                        + "cd /d \"%~dp0\"\r\n"
                        + "echo ========================================================\r\n"
                        + "echo   KR Programm Ausfuehrung: programm.exe\r\n"
                        + "echo ========================================================\r\n"
                        + "echo.\r\n"
                        + "\"%~dp0programm.exe\"\r\n"
                        + "echo.\r\n"
                        + "echo --------------------------------------------------------\r\n"
                        + "echo [BEENDET] Rueckgabewert / Exit Code: %ERRORLEVEL%\r\n"
                        + "echo.\r\n"
                        + "echo Druecken Sie eine beliebige Taste, um dieses Fenster zu schliessen...\r\n"
                        + "pause >nul\r\n";
                try {
                    Files.writeString(runBat.toPath(), batContent, StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }
            return targetExe;
        }
    }

    private File buildMakefile(File outputDir, File buildLog) throws Exception {
        if (target == Target.ANDROID) {
            log.line("[INFO] Android-Build für Makefile-Projekt: führe direkten NDK-Compilerlauf für Android ARM64 durch …");
            return buildDirectCpp(findCppFiles(projectDir), outputDir, buildLog);
        }

        String make = ToolchainManager.getInstance().getMakeExecutable();
        if (make == null) {
            make = findMakeExecutable();
        }
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

        String targetName = target == Target.WINDOWS ? "programm.exe" : "programm";
        File targetFile = new File(outputDir, targetName);
        File found = findFirstExecutable(projectDir);
        if (found != null && !found.equals(targetFile)) {
            Files.copy(found.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return targetFile;
        }
        return targetFile;
    }

    private File buildRust(File outputDir, File buildLog) throws Exception {
        String cargo = ToolchainManager.getInstance().getCargoExecutable();
        if (cargo == null) cargo = findExecutable("cargo");
        if (cargo == null) cargo = ToolchainManager.getInstance().getRustcExecutable();
        if (cargo == null) cargo = findExecutable("rustc");
        if (cargo == null) throw new IOException("Rust (cargo/rustc) wurde auf dem System nicht gefunden.");

        if (target == Target.ANDROID) {
            log.line("[INFO] Starte Cargo Android ARM64 Release-Build …");
            List<String> cmd = Arrays.asList(cargo, "build", "--release", "--target", "aarch64-linux-android");
            runProcess(cmd, projectDir, buildLog);

            File targetRelease = new File(new File(new File(projectDir, "target"), "aarch64-linux-android"), "release");
            File foundSo = findFirstSharedLibrary(targetRelease);
            if (foundSo == null) foundSo = findFirstExecutable(targetRelease);

            File userSo = new File(outputDir, "libuser.so");
            if (foundSo != null) Files.copy(foundSo.toPath(), userSo.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return packageAndroidApk(outputDir, userSo, projectDir, false, buildLog);
        } else if (target == Target.LINUX) {
            File artifact = new File(outputDir, "programm");
            List<String> cmd = Arrays.asList(cargo, "build", "--release");
            log.line("[INFO] Starte Cargo Release-Build (Linux) …");
            runProcess(cmd, projectDir, buildLog);

            File targetRelease = new File(new File(projectDir, "target"), "release");
            File found = findFirstExecutable(targetRelease);
            if (found != null) {
                Files.copy(found.toPath(), artifact.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return artifact;
        } else {
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
    }

    private File buildGo(File outputDir, File buildLog) throws Exception {
        String go = findExecutable("go");
        if (go == null) throw new IOException("Go Compiler (go.exe) wurde auf dem System nicht gefunden.");

        if (target == Target.ANDROID) {
            File userSo = new File(outputDir, "libuser.so");
            List<String> cmd = Arrays.asList(go, "build", "-buildmode=c-shared", "-o", userSo.getAbsolutePath(), ".");
            log.line("[INFO] Starte Go Android ARM64 Build …");
            runProcess(cmd, projectDir, buildLog);
            return packageAndroidApk(outputDir, userSo, projectDir, false, buildLog);
        } else if (target == Target.LINUX) {
            File artifact = new File(outputDir, "programm");
            List<String> cmd = Arrays.asList(go, "build", "-o", artifact.getAbsolutePath(), ".");
            log.line("[INFO] Starte Go Linux Build …");
            runProcess(cmd, projectDir, buildLog);
            return artifact;
        } else {
            File artifact = new File(outputDir, "programm.exe");
            List<String> cmd = Arrays.asList(go, "build", "-o", artifact.getAbsolutePath(), ".");
            log.line("[INFO] Starte Go Build …");
            runProcess(cmd, projectDir, buildLog);
            return artifact;
        }
    }

    private void runProcess(List<String> cmd, File cwd, File buildLog) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd);
        pb.redirectErrorStream(true);

        Map<String, String> env = pb.environment();
        // 1. Toolchain-Pfade (integrierte portable Toolchain) primär injizieren
        ToolchainManager.getInstance().injectEnvironment(env);

        // 2. Pfad des gestarteten Executables und Fallback-Systempfade ergänzen
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

        if (target == Target.ANDROID) {
            env.put("GOOS", "android");
            env.put("GOARCH", "arm64");
            String androidCc = ToolchainManager.getInstance().getAndroidCCompilerExecutable();
            if (androidCc != null) {
                env.put("CC", androidCc);
            }
        }

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

    private static boolean isPosixEmulatorPath(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ROOT).replace('/', '\\');
        return lower.contains("\\msys64\\usr\\bin")
                || lower.contains("\\msys32\\usr\\bin")
                || lower.contains("\\msys64\\bin")
                || lower.contains("\\msys32\\bin")
                || lower.contains("\\cygwin64\\bin")
                || lower.contains("\\cygwin\\bin")
                || lower.endsWith("\\usr\\bin")
                || lower.contains("\\usr\\bin\\");
    }

    public static String findCMakeExecutable() {
        // 1. Primär: Portable integrierte Toolchain
        File tc = ToolchainManager.getInstance().getToolchainDir();
        if (tc != null && tc.isDirectory()) {
            File inBin = new File(new File(tc, "bin"), "cmake.exe");
            if (inBin.isFile() && inBin.canExecute()) return inBin.getAbsolutePath();
        }

        // 2. Bekannte native Windows-Pfade (bevorzugt vor beliebigem PATH)
        String[] wellKnown = {
                "C:\\Program Files\\CMake\\bin\\cmake.exe",
                "C:\\Program Files (x86)\\CMake\\bin\\cmake.exe",
                "C:\\msys64\\mingw64\\bin\\cmake.exe",
                "C:\\msys64\\ucrt64\\bin\\cmake.exe",
                "C:\\msys64\\clang64\\bin\\cmake.exe"
        };
        for (String p : wellKnown) {
            File f = new File(p);
            if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
        }

        // 3. Android SDK CMake
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            File sdkCmake = new File(userHome, "AppData\\Local\\Android\\Sdk\\cmake");
            if (sdkCmake.isDirectory()) {
                File[] versions = sdkCmake.listFiles(File::isDirectory);
                if (versions != null) {
                    Arrays.sort(versions, (a, b) -> b.getName().compareTo(a.getName()));
                    for (File v : versions) {
                        File exe = new File(v, "bin\\cmake.exe");
                        if (exe.isFile() && exe.canExecute()) return exe.getAbsolutePath();
                    }
                }
            }
        }

        // 4. PATH des Systems (NUR native Windows-Executables, KEINE MSYS/Cygwin POSIX /usr/bin)
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                if (isPosixEmulatorPath(dir)) continue;
                File candidate = new File(dir, "cmake.exe");
                if (candidate.isFile() && candidate.canExecute()) return candidate.getAbsolutePath();
            }
        }

        return null;
    }

    private static String findMakeExecutable() {
        String exe = findExecutable("mingw32-make");
        if (exe != null) return exe;
        exe = findExecutable("make");
        if (exe != null) return exe;

        String[] wellKnown = {
                "C:\\msys64\\mingw64\\bin\\mingw32-make.exe",
                "C:\\msys64\\ucrt64\\bin\\mingw32-make.exe"
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
                "C:\\msys64\\mingw64\\bin\\clang++.exe",
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
        if (name.equalsIgnoreCase("ninja")) {
            String ninja = ToolchainManager.getInstance().getNinjaExecutable();
            if (ninja != null) return ninja;
        }

        // 1. Primär: Portable integrierte Toolchain der KR Compiler IDE
        File tc = ToolchainManager.getInstance().getToolchainDir();
        if (tc != null && tc.isDirectory()) {
            File inBin = new File(new File(tc, "bin"), withExe);
            if (inBin.isFile() && inBin.canExecute()) return inBin.getAbsolutePath();
            File inGoBin = new File(new File(tc, "go/bin"), withExe);
            if (inGoBin.isFile() && inGoBin.canExecute()) return inGoBin.getAbsolutePath();
            File inRustBin = new File(new File(tc, "rust/bin"), withExe);
            if (inRustBin.isFile() && inRustBin.canExecute()) return inRustBin.getAbsolutePath();
        }

        // 2. PATH des Systems (NUR native Windows-Executables, keine MSYS/Cygwin POSIX /usr/bin)
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                if (isPosixEmulatorPath(dir)) continue;
                File candidate = new File(dir, withExe);
                if (candidate.isFile() && candidate.canExecute()) return candidate.getAbsolutePath();
            }
        }

        // 3. Bekannte Standardpfade
        String[] wellKnown = {
                "C:\\msys64\\mingw64\\bin\\" + withExe,
                "C:\\msys64\\ucrt64\\bin\\" + withExe,
                "C:\\Program Files\\Go\\bin\\" + withExe,
                System.getProperty("user.home") + "\\.cargo\\bin\\" + withExe
        };
        for (String p : wellKnown) {
            File f = new File(p);
            if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
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
