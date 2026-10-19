package kr.compile;

import android.content.Context;
import android.net.Uri;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CompilerEngine {
    enum Target { ANDROID, WINDOWS, LINUX }
    interface Logger { void line(String value); }

    private final Context context;
    private final Uri projectUri;
    private final File localProjectDir;
    private final Target target;
    private final boolean onlineDependencies;
    private final Logger log;
    private File toolchain;
    private File project;
    private File output;
    private File buildLog;
    private volatile Process activeProcess;
    private volatile boolean cancelled = false;

    CompilerEngine(Context context, Uri projectUri, Target target, boolean onlineDependencies, Logger log) {
        this(context, projectUri, null, target, onlineDependencies, log);
    }

    CompilerEngine(Context context, Uri projectUri, File localProjectDir, Target target, boolean onlineDependencies, Logger log) {
        this.context = context.getApplicationContext();
        this.projectUri = projectUri;
        this.localProjectDir = localProjectDir;
        this.target = target;
        this.onlineDependencies = onlineDependencies;
        this.log = log;
    }

    void cancel() {
        cancelled = true;
        Process p = activeProcess;
        if (p != null) {
            try { p.destroyForcibly(); } catch (Exception ignored) {}
        }
    }

    static File getActiveWorkspaceOutput(Context context) {
        return new File(new File(context.getCacheDir(), "active-project"), "KR_BUILD");
    }

    String build() throws Exception {
        toolchain = ToolchainInstaller.ensure(context, log);
        if (target == Target.ANDROID) {
            log.line("Android-Laufzeit und APK-Signierung werden geprüft …");
            RunnerPackager.verifySigning(context);
        }
        File workspace = new File(context.getCacheDir(), "active-project");
        delete(workspace); project = new File(workspace, "source"); output = new File(workspace, "KR_BUILD");
        if (!project.mkdirs() || !output.mkdirs()) throw new IOException("Temporärer Projektordner kann nicht angelegt werden.");
        buildLog = new File(output, "build.log");
        writeText(buildLog, "");
        if (localProjectDir != null && localProjectDir.isDirectory()) {
            log.line("Lokales Projekt wird vorbereitet: " + localProjectDir.getName() + " …");
            copyDirectoryContent(localProjectDir, project);
        } else if (projectUri != null) {
            log.line("Projekt wird aus dem ausgewählten Android-Ordner gelesen …");
            DocumentTree.importProject(context, projectUri, project);
        } else {
            throw new IOException("Kein Projekt ausgewählt.");
        }

        try {
            File cmakeProjectDir = findPrimaryCMakeProject(project);
            File artifact;
            List<File> krFiles = KrPipeline.findKrFiles(project);
            if (!krFiles.isEmpty()) {
                log.line("KR Unified Language Projekt erkannt (" + krFiles.size() + " .kr-Datei(en)).");
                artifact = buildKrUnified(krFiles);
            } else if (new File(project, "Cargo.toml").isFile()) {
                log.line("Rust-Projekt erkannt; Cargo und rustc werden verwendet.");
                artifact = buildRust();
            } else if (new File(project, "go.mod").isFile() || (cmakeProjectDir == null && !goSources(project).isEmpty())) {
                log.line("Go-Projekt erkannt; go build wird verwendet.");
                artifact = buildGo();
            } else if (cmakeProjectDir != null) {
                if (cmakeProjectDir.equals(project)) {
                    log.line("CMakeLists.txt erkannt; das Projekt wird mit CMake ausgewertet.");
                } else {
                    String rel = project.toURI().relativize(cmakeProjectDir.toURI()).getPath();
                    log.line("CMakeLists.txt in Unterordner erkannt (" + rel + "); das Projekt wird mit CMake ausgewertet.");
                }
                artifact = buildCMake(cmakeProjectDir, project);
            } else {
                List<File> rawSources = sources(project);
                List<File> sources = filterDirectSources(project, rawSources, target);
                if (sources.isEmpty()) throw new IOException("Keine CMakeLists.txt und keine .c-, .cc-, .cpp- oder .cxx-Datei gefunden.");
                log.line(sources.size() + " Quelldatei(en) gefunden; direkter Build wird verwendet.");
                artifact = target == Target.ANDROID ? buildAndroid(sources) : buildDesktop(sources);
            }
            writeText(new File(output, "build-result.json"), "{\n  \"success\": true,\n  \"target\": \"" + target + "\",\n  \"artifact\": \"" + artifact.getName() + "\"\n}\n");
            log.line("Ergebnis wird nach KR_BUILD kopiert …");
            if (localProjectDir != null) {
                File localBuildDir = new File(localProjectDir, "KR_BUILD");
                if (!localBuildDir.exists()) localBuildDir.mkdirs();
                copyDirectoryContent(output, localBuildDir);
            }
            if (projectUri != null) {
                DocumentTree.exportBuild(context, projectUri, output);
            }
            return "Fertig: KR_BUILD/" + artifact.getName();
        } catch (Exception error) {
            if (cancelled) {
                appendText(new File(output, "build.log"), "\nBUILD ABGEBROCHEN DURCH BENUTZER\n");
                writeText(new File(output, "build-result.json"), "{\n  \"success\": false,\n  \"target\": \"" + target + "\",\n  \"message\": \"Build abgebrochen\"\n}\n");
                return "Build abgebrochen.";
            }
            appendText(new File(output, "build.log"), "\nFEHLER: " + error + "\n");
            writeText(new File(output, "build-result.json"), "{\n  \"success\": false,\n  \"target\": \"" + target + "\",\n  \"message\": " + json(error.getMessage()) + "\n}\n");
            if (localProjectDir != null) {
                try {
                    File localBuildDir = new File(localProjectDir, "KR_BUILD");
                    if (!localBuildDir.exists()) localBuildDir.mkdirs();
                    copyDirectoryContent(output, localBuildDir);
                } catch (Exception ignored) {}
            }
            if (projectUri != null) {
                try { DocumentTree.exportBuild(context, projectUri, output); }
                catch (Exception exportError) { error.addSuppressed(exportError); }
            }
            throw error;
        }
    }

    private File buildCMake() throws Exception {
        return buildCMake(project, project);
    }

    private File buildCMake(File sourceProject, File runtimeProject) throws Exception {
        return buildCMakeForTarget(sourceProject, runtimeProject, target);
    }

    private File buildCMakeForTarget(File sourceProject, File runtimeProject, Target activeTarget) throws Exception {
        File cmake = new File(toolchain, "usr/bin/cmake");
        File ninja = new File(toolchain, "usr/bin/ninja");
        if (!cmake.canExecute() || !ninja.canExecute())
            throw new IOException("CMake oder Ninja fehlt in der Compilerlaufzeit. Bitte die vollständige Compiler-APK installieren.");

        File cmakeRoot = new File(project.getParentFile(), "cmake-" + activeTarget.name().toLowerCase(Locale.ROOT));
        delete(cmakeRoot);
        File binary = new File(cmakeRoot, "build");
        File artifacts = new File(cmakeRoot, "artifacts");
        File config = new File(cmakeRoot, "config");
        if (!binary.mkdirs() || !artifacts.mkdirs() || !config.mkdirs())
            throw new IOException("CMake-Arbeitsordner kann nicht angelegt werden.");

        File toolchainFile = new File(config, "kr-toolchain.cmake");
        File projectInclude = new File(config, "kr-project.cmake");
        File targetList = new File(binary, "kr-executable-targets.txt");
        File sharedList = new File(binary, "kr-shared-targets.txt");
        File metaList = new File(binary, "kr-target-meta.txt");
        writeText(toolchainFile, cmakeToolchain(activeTarget));
        Set<String> packages = findCMakePackages(sourceProject);
        Set<String> manifestPackages = VcpkgManager.readDependencies(sourceProject);
        boolean guiProgram = VcpkgManager.isGuiProject(sourceProject, manifestPackages);
        if (guiProgram) {
            log.line("GUI-Projekt erkannt: SDL3-/Dear-ImGui-Fenster wird für "
                    + (activeTarget == Target.ANDROID ? "Android ARM64" : activeTarget == Target.WINDOWS ? "Windows x64" : "Linux x64")
                    + " gebaut.");
            if (activeTarget == Target.ANDROID) {
                log.line("Android-GUI-Modus: SDLActivity, Touch-Eingabe und OpenGL-ES-Runner werden eingebunden.");
            }
        }
        for (String port : manifestPackages) {
            if (!VcpkgManager.isSupported(port)) {
                throw new IOException("VCPKG-Port '" + port + "' ist im mobilen KR-Katalog noch nicht verfügbar.");
            }
            boolean present = false;
            for (String existing : packages) {
                if (existing.replace('_', '-').equalsIgnoreCase(port)) { present = true; break; }
            }
            if (!present) packages.add(port);
        }
        if (!manifestPackages.isEmpty()) {
            log.line("vcpkg.json erkannt: " + String.join(", ", manifestPackages));
            log.line("Mobiler VCPKG-Manifestmodus: geprüfte Quellen und CMake-Targets werden vorbereitet.");
        }
        writeText(projectInclude, cmakeProjectInclude(artifacts, targetList, sharedList, metaList, packages, activeTarget));

        File dependencyCache = new File(context.getFilesDir(), "components/cmake/" + activeTarget.name().toLowerCase(Locale.ROOT));
        if (!dependencyCache.isDirectory() && !dependencyCache.mkdirs())
            throw new IOException("Abhängigkeitscache kann nicht angelegt werden.");
        if (activeTarget == Target.ANDROID && containsPackage(packages, "sdl3")) {
            patchCachedAndroidSdl(dependencyCache);
        }

        String systemVersion = activeTarget == Target.ANDROID ? "26" : (activeTarget == Target.WINDOWS ? "10.0" : "1");
        List<String> configure = new ArrayList<>();
        Collections.addAll(configure, cmake.getPath(), "-S", sourceProject.getPath(), "-B", binary.getPath(), "-G", "Ninja",
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_COMPILE_WARNING_AS_ERROR=OFF",
                "-DCMAKE_SYSTEM_VERSION=" + systemVersion,
                "-DCMAKE_MAKE_PROGRAM=" + ninja.getPath(),
                "-DCMAKE_TOOLCHAIN_FILE=" + toolchainFile.getPath(),
                "-DCMAKE_PROJECT_TOP_LEVEL_INCLUDES=" + projectInclude.getPath(),
                "-DFETCHCONTENT_BASE_DIR=" + dependencyCache.getPath(),
                "-DFETCHCONTENT_QUIET=OFF");
        if (!onlineDependencies) configure.add("-DFETCHCONTENT_FULLY_DISCONNECTED=ON");
        else log.line("Online-Abhängigkeiten sind erlaubt; CMake FetchContent und Git dürfen benötigte Quellen laden.");
        run(configure, "CMake konfiguriert das Projekt", sourceProject);

        List<String> execTargetNames = readLines(targetList);
        List<String> sharedTargetNames = readLines(sharedList);
        List<CMakeTargetInfo> targetInfos = parseCMakeTargets(metaList, execTargetNames, sourceProject);

        if (targetInfos.isEmpty() && sharedTargetNames.isEmpty()) {
            throw new IOException("CMake hat weder ein ausführbares Ziel (add_executable) noch eine Shared Library erzeugt.");
        }

        List<CMakeTargetInfo> compatibleExecs = filterCompatibleCMakeTargets(targetInfos, activeTarget);
        if (!targetInfos.isEmpty() && compatibleExecs.isEmpty() && sharedTargetNames.isEmpty() && activeTarget != Target.WINDOWS) {
            log.line("Hinweis: Dieses Projekt nutzt ausschließlich native Windows-Win32/DirectX-APIs (<windows.h>). Windows-x64-Artefakt wird gebaut …");
            File winExe = buildCMakeForTarget(sourceProject, runtimeProject, Target.WINDOWS);
            return createPlatformFallbackWrapper(winExe, runtimeProject);
        }

        CMakeTargetInfo selectedInfo = null;
        if (!compatibleExecs.isEmpty()) {
            selectedInfo = selectBestCMakeTarget(compatibleExecs, sourceProject, runtimeProject);
        }

        List<String> targetsToBuild = new ArrayList<>(sharedTargetNames);
        if (selectedInfo != null && !targetsToBuild.contains(selectedInfo.name)) {
            targetsToBuild.add(selectedInfo.name);
        }

        List<String> build = new ArrayList<>();
        int buildJobs = activeTarget == Target.ANDROID
                ? Math.min(2, Math.max(1, Runtime.getRuntime().availableProcessors()))
                : Math.max(1, Runtime.getRuntime().availableProcessors());
        if (activeTarget == Target.ANDROID && guiProgram) {
            log.line("Android-GUI-Build: " + buildJobs + " parallele Compilerprozesse für stabilen Speicherverbrauch.");
        }
        Collections.addAll(build, cmake.getPath(), "--build", binary.getPath(), "--config", "Release", "--parallel",
                Integer.toString(buildJobs));
        if (!targetsToBuild.isEmpty()) {
            build.add("--target");
            build.addAll(targetsToBuild);
        }
        run(build, "CMake baut das Projekt (" + String.join(", ", targetsToBuild) + ")", sourceProject);

        copyAllCMakeArtifacts(artifacts, binary, output);

        File built = null;
        if (selectedInfo != null) {
            built = findCMakeArtifact(artifacts, binary, selectedInfo.name, selectedInfo.outputName, false, activeTarget);
            if (built == null) {
                throw new IOException("Das CMake-Ziel " + selectedInfo.name + " wurde gebaut, aber seine Ausgabedatei wurde nicht gefunden.");
            }
        } else if (!sharedTargetNames.isEmpty()) {
            String primaryShared = sharedTargetNames.get(0);
            built = findCMakeArtifact(artifacts, binary, primaryShared, null, true, activeTarget);
            if (built == null) {
                throw new IOException("Die Shared Library " + primaryShared + " wurde gebaut, aber ihre Ausgabedatei wurde nicht gefunden.");
            }
            log.line("Shared-Library-Projekt gebaut: " + built.getName());
        }

        if (activeTarget == Target.ANDROID) {
            File library = new File(output, "libuser.so");
            copy(built, library);
            List<File> extraLibs = new ArrayList<>();
            for (String sh : sharedTargetNames) {
                if (selectedInfo != null || !sh.equals(sharedTargetNames.get(0))) {
                    File shFile = findCMakeArtifact(artifacts, binary, sh, null, true, activeTarget);
                    if (shFile != null && shFile.isFile()) extraLibs.add(shFile);
                }
            }
            File apk = new File(output, "programm-android-arm64.apk");
            File cppRuntime = new File(toolchain, "usr/lib/libc++_shared.so");
            RunnerPackager.create(context, library, cppRuntime, extraLibs, runtimeProject, guiProgram, apk, log);
            if (!library.delete()) log.line("Hinweis: temporäre Android-Bibliothek bleibt in der Ausgabe.");
            return apk;
        }

        File artifact = new File(output, activeTarget == Target.WINDOWS ? "programm.exe" : "programm");
        copy(built, artifact);
        artifact.setExecutable(true, false);
        return artifact;
    }

    private static boolean containsPackage(Set<String> packages, String wanted) {
        for (String value : packages) {
            if (value.replace('_', '-').equalsIgnoreCase(wanted)) return true;
        }
        return false;
    }

    private void patchCachedAndroidSdl(File dependencyCache) throws IOException {
        File cmakeFile = new File(dependencyCache, "sdl3-src/CMakeLists.txt");
        if (!cmakeFile.isFile()) return; // First extraction is handled by FetchContent PATCH_COMMAND.
        String source = readText(cmakeFile);
        String original = "if(((LINUX AND LIBC_IS_GLIBC) OR ANDROID) AND (NOT USE_TCC))";
        String replacement = "if((LINUX AND LIBC_IS_GLIBC) AND (NOT USE_TCC) AND NOT ANDROID)";
        if (source.contains(original)) {
            writeText(cmakeFile, source.replace(original, replacement));
            log.line("SDL3-CMake-Erkennung an den eingebetteten Android-Cross-Linker angepasst.");
        }
    }

    private String cmakeToolchain() {
        return cmakeToolchain(target);
    }

    private String cmakeToolchain(Target activeTarget) {
        File clang = new File(toolchain, "usr/bin/clang");
        File clangxx = new File(toolchain, "usr/bin/clang++");
        File ar = new File(toolchain, "usr/bin/llvm-ar");
        File ranlib = new File(toolchain, "usr/bin/llvm-ranlib");
        File strip = new File(toolchain, "usr/bin/llvm-strip");
        StringBuilder s = new StringBuilder();
        s.append("set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)\n");
        s.append("set(CMAKE_COMPILE_WARNING_AS_ERROR OFF CACHE BOOL \"\" FORCE)\n");
        s.append("set(CMAKE_C_COMPILER ").append(cmakeQuote(clang)).append(")\n");
        s.append("set(CMAKE_CXX_COMPILER ").append(cmakeQuote(clangxx)).append(")\n");
        s.append("set(CMAKE_AR ").append(cmakeQuote(ar)).append(")\n");
        s.append("set(CMAKE_RANLIB ").append(cmakeQuote(ranlib)).append(")\n");
        s.append("set(CMAKE_STRIP ").append(cmakeQuote(strip)).append(")\n");
        if (activeTarget == Target.ANDROID) {
            String usrLib = cmakeEscape(new File(toolchain, "usr/lib").getPath());
            String usrInc = cmakeEscape(new File(toolchain, "usr/include").getPath());
            s.append("set(CMAKE_SYSTEM_NAME Linux)\nset(CMAKE_SYSTEM_PROCESSOR aarch64)\n");
            s.append("set(ANDROID TRUE CACHE BOOL \"KR Android cross target\" FORCE)\n");
            s.append("set(ANDROID_ABI arm64-v8a CACHE STRING \"\" FORCE)\n");
            s.append("set(CMAKE_ANDROID_ARCH_ABI arm64-v8a CACHE STRING \"\" FORCE)\n");
            s.append("set(CMAKE_SYSTEM_VERSION 26)\n");
            s.append("set(CMAKE_C_COMPILER_TARGET aarch64-linux-android26)\n");
            s.append("set(CMAKE_CXX_COMPILER_TARGET aarch64-linux-android26)\n");
            s.append("set(CMAKE_POSITION_INDEPENDENT_CODE ON)\n");
            s.append("set(CMAKE_C_FLAGS_INIT \"-Wno-error\")\n");
            s.append("set(CMAKE_CXX_FLAGS_INIT \"-Wno-error\")\n");
            s.append("set(CMAKE_EXE_LINKER_FLAGS_INIT \"-fuse-ld=lld -L").append(usrLib).append(" -Wl,--export-dynamic -lc++_shared -lm -ldl -llog\")\n");
            s.append("set(CMAKE_SHARED_LINKER_FLAGS_INIT \"-fuse-ld=lld -L").append(usrLib).append(" -Wl,--export-dynamic -lc++_shared -lm -ldl -llog\")\n");
            s.append("set(CMAKE_MODULE_LINKER_FLAGS_INIT \"-fuse-ld=lld -L").append(usrLib).append(" -Wl,--export-dynamic -lc++_shared -lm -ldl -llog\")\n");
            s.append("set(OpenCL_INCLUDE_DIR \"").append(usrInc).append("/opencl\" CACHE PATH \"\" FORCE)\n");
            s.append("set(OpenCL_LIBRARY \"").append(usrLib).append("/libOpenCL.so\" CACHE FILEPATH \"\" FORCE)\n");
            s.append("set(SQLite3_INCLUDE_DIR \"").append(usrInc).append("\" CACHE PATH \"\" FORCE)\n");
            s.append("set(SQLite3_LIBRARY \"").append(usrLib).append("/libsqlite3.so\" CACHE FILEPATH \"\" FORCE)\n");
            s.append("set(CMAKE_HAVE_LIBC_PTHREAD 1 CACHE INTERNAL \"\" FORCE)\n");
            s.append("set(CMAKE_THREAD_LIBS_INIT \"\" CACHE STRING \"\" FORCE)\n");
            s.append("set(CMAKE_USE_PTHREADS_INIT 1 CACHE INTERNAL \"\" FORCE)\n");
            s.append("set(Threads_FOUND TRUE CACHE BOOL \"\" FORCE)\n");
        } else if (activeTarget == Target.WINDOWS) {
            File win = new File(toolchain, "targets/windows");
            String include1 = new File(win, "x86_64-w64-mingw32/include/c++/v1").getPath();
            String include2 = new File(win, "include/c++/v1").getPath();
            String winInc = cmakeEscape(new File(win, "include").getPath());
            String winLib = cmakeEscape(new File(win, "x86_64-w64-mingw32/lib").getPath());
            s.append("set(CMAKE_SYSTEM_NAME Windows)\nset(CMAKE_SYSTEM_PROCESSOR x86_64)\n");
            s.append("set(CMAKE_SYSTEM_VERSION 10.0)\n");
            s.append("set(CMAKE_C_COMPILER_TARGET x86_64-w64-windows-gnu)\n");
            s.append("set(CMAKE_CXX_COMPILER_TARGET x86_64-w64-windows-gnu)\n");
            s.append("set(CMAKE_SYSROOT ").append(cmakeQuote(win)).append(")\n");
            s.append("set(CMAKE_FIND_ROOT_PATH ").append(cmakeQuote(win)).append(")\n");
            s.append("set(CMAKE_C_FLAGS_INIT \"-Wno-error\")\n");
            s.append("set(CMAKE_CXX_FLAGS_INIT \"-Wno-error -nostdinc++ -isystem ")
                    .append(cmakeEscape(include1)).append(" -isystem ").append(cmakeEscape(include2)).append("\")\n");
            s.append("set(CMAKE_EXE_LINKER_FLAGS_INIT \"-fuse-ld=lld -static -stdlib=libc++ -rtlib=compiler-rt -unwindlib=libunwind\")\n");
            s.append("set(CMAKE_SHARED_LINKER_FLAGS_INIT \"-fuse-ld=lld -static -stdlib=libc++ -rtlib=compiler-rt -unwindlib=libunwind\")\n");
            s.append("set(CMAKE_MODULE_LINKER_FLAGS_INIT \"-fuse-ld=lld -static -stdlib=libc++ -rtlib=compiler-rt -unwindlib=libunwind\")\n");
            s.append("set(OpenCL_INCLUDE_DIR \"").append(winInc).append("/opencl\" CACHE PATH \"\" FORCE)\n");
            s.append("set(OpenCL_LIBRARY \"").append(winLib).append("/libOpenCL.dll.a\" CACHE FILEPATH \"\" FORCE)\n");
            s.append("set(CMAKE_THREAD_LIBS_INIT \"\" CACHE STRING \"\" FORCE)\n");
            s.append("set(CMAKE_USE_WIN32_THREADS_INIT 1 CACHE INTERNAL \"\" FORCE)\n");
            s.append("set(Threads_FOUND TRUE CACHE BOOL \"\" FORCE)\n");
        } else {
            File linux = new File(toolchain, "targets/linux");
            File sysroot = new File(linux, "sysroot");
            File gcc = new File(linux, "gcc");
            String sysInc = cmakeEscape(new File(sysroot, "usr/include").getPath());
            String sysLib = cmakeEscape(new File(sysroot, "usr/lib/x86_64-linux-gnu").getPath());
            s.append("set(CMAKE_SYSTEM_NAME Linux)\nset(CMAKE_SYSTEM_PROCESSOR x86_64)\n");
            s.append("set(CMAKE_SYSTEM_VERSION 1)\n");
            s.append("set(CMAKE_C_COMPILER_TARGET x86_64-linux-gnu)\n");
            s.append("set(CMAKE_CXX_COMPILER_TARGET x86_64-linux-gnu)\n");
            s.append("set(CMAKE_SYSROOT ").append(cmakeQuote(sysroot)).append(")\n");
            s.append("set(CMAKE_FIND_ROOT_PATH ").append(cmakeQuote(sysroot)).append(' ').append(cmakeQuote(gcc)).append(")\n");
            s.append("set(CMAKE_C_FLAGS_INIT \"--gcc-toolchain=").append(cmakeEscape(gcc.getPath())).append(" -Wno-error\")\n");
            s.append("set(CMAKE_CXX_FLAGS_INIT \"--gcc-toolchain=").append(cmakeEscape(gcc.getPath())).append(" -stdlib=libstdc++ -Wno-error\")\n");
            s.append("set(CMAKE_EXE_LINKER_FLAGS_INIT \"-fuse-ld=lld --gcc-toolchain=").append(cmakeEscape(gcc.getPath()))
                    .append(" -rtlib=libgcc -static-libstdc++ -static-libgcc -lpthread\")\n");
            s.append("set(CMAKE_SHARED_LINKER_FLAGS_INIT \"-fuse-ld=lld --gcc-toolchain=").append(cmakeEscape(gcc.getPath()))
                    .append(" -rtlib=libgcc -static-libstdc++ -static-libgcc -lpthread\")\n");
            s.append("set(CMAKE_MODULE_LINKER_FLAGS_INIT \"-fuse-ld=lld --gcc-toolchain=").append(cmakeEscape(gcc.getPath()))
                    .append(" -rtlib=libgcc -static-libstdc++ -static-libgcc -lpthread\")\n");
            s.append("set(OpenCL_INCLUDE_DIR \"").append(sysInc).append("/opencl\" CACHE PATH \"\" FORCE)\n");
            s.append("set(OpenCL_LIBRARY \"").append(sysLib).append("/libOpenCL.so\" CACHE FILEPATH \"\" FORCE)\n");
            s.append("set(CMAKE_HAVE_LIBC_PTHREAD 1 CACHE INTERNAL \"\" FORCE)\n");
            s.append("set(CMAKE_THREAD_LIBS_INIT \"-lpthread\" CACHE STRING \"\" FORCE)\n");
            s.append("set(CMAKE_USE_PTHREADS_INIT 1 CACHE INTERNAL \"\" FORCE)\n");
            s.append("set(Threads_FOUND TRUE CACHE BOOL \"\" FORCE)\n");
            s.append("set(X11_INCLUDE_DIR \"").append(sysInc).append("\" CACHE PATH \"\" FORCE)\n");
            s.append("set(X11_X11_INCLUDE_PATH \"").append(sysInc).append("\" CACHE PATH \"\" FORCE)\n");
            s.append("set(X11_X11_LIB \"").append(sysLib).append("/libX11.so\" CACHE FILEPATH \"\" FORCE)\n");
            s.append("set(X11_Xext_INCLUDE_PATH \"").append(sysInc).append("\" CACHE PATH \"\" FORCE)\n");
            s.append("set(X11_Xext_LIB \"").append(sysLib).append("/libXext.so\" CACHE FILEPATH \"\" FORCE)\n");
        }
        s.append("set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)\n");
        s.append("set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)\n");
        s.append("set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)\n");
        s.append("set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)\n");
        return s.toString();
    }

    private String cmakeProjectInclude(File artifacts, File targetList, Set<String> packages) {
        File parent = targetList.getParentFile();
        return cmakeProjectInclude(artifacts, targetList, new File(parent, "kr-shared-targets.txt"),
                new File(parent, "kr-target-meta.txt"), packages, target);
    }

    private String cmakeProjectInclude(File artifacts, File targetList, File sharedList, File metaList,
                                       Set<String> packages, Target activeTarget) {
        String out = cmakeEscape(artifacts.getPath());
        String list = cmakeEscape(targetList.getPath());
        String shList = cmakeEscape(sharedList.getPath());
        String mList = cmakeEscape(metaList.getPath());
        StringBuilder s = new StringBuilder();
        if (activeTarget == Target.ANDROID) {
            String usrInc = cmakeEscape(new File(toolchain, "usr/include").getPath());
            String usrIncAlt = usrInc.replace("/data/user/0/", "/data/data/");
            s.append("list(APPEND CMAKE_C_IMPLICIT_INCLUDE_DIRECTORIES \"").append(usrInc).append("\" \"").append(usrIncAlt).append("\")\n");
            s.append("list(APPEND CMAKE_CXX_IMPLICIT_INCLUDE_DIRECTORIES \"").append(usrInc).append("\" \"").append(usrIncAlt).append("\")\n");
        }
        if (onlineDependencies) appendDownloadableDependencies(s, packages);
        s.append("set(KR_EXECUTABLE_OUTPUT \"").append(out).append("\")\n");
        s.append("set(CMAKE_COMPILE_WARNING_AS_ERROR OFF)\n");

        // Sanitize compile and link options for cross-compilation
        s.append("function(add_compile_options)\n  set(_opts ${ARGN})\n  list(REMOVE_ITEM _opts \"-Werror\" \"/WX\")\n");
        if (activeTarget == Target.WINDOWS) {
            s.append("  list(FILTER _opts EXCLUDE REGEX \"^-fstack-protector\")\n");
        }
        s.append("  if(_opts)\n    _add_compile_options(${_opts})\n  endif()\nendfunction()\n");

        s.append("function(target_compile_options target)\n  set(_opts ${ARGN})\n  list(REMOVE_ITEM _opts \"-Werror\" \"/WX\")\n");
        if (activeTarget == Target.WINDOWS) {
            s.append("  list(FILTER _opts EXCLUDE REGEX \"^-fstack-protector\")\n");
        }
        s.append("  _target_compile_options(${target} ${_opts})\nendfunction()\n");

        if (activeTarget == Target.WINDOWS) {
            s.append("function(target_link_options target)\n  set(_opts ${ARGN})\n");
            s.append("  list(FILTER _opts EXCLUDE REGEX \"^-Wl,-z,\")\n");
            s.append("  list(FILTER _opts EXCLUDE REGEX \"^/[gG][uU][aA][rR][dD]\")\n");
            s.append("  _target_link_options(${target} ${_opts})\nendfunction()\n");
        }

        // Hook add_library to track SHARED libraries and all project targets
        s.append("function(add_library target)\n");
        s.append("  _add_library(${target} ${ARGN})\n");
        s.append("  set(_kr_args ${ARGN})\n");
        s.append("  list(FIND _kr_args \"IMPORTED\" _kr_imported)\n  list(FIND _kr_args \"ALIAS\" _kr_alias)\n  list(FIND _kr_args \"INTERFACE\" _kr_iface)\n");
        s.append("  if(NOT _kr_imported EQUAL -1 OR NOT _kr_alias EQUAL -1 OR NOT _kr_iface EQUAL -1)\n    return()\n  endif()\n");
        s.append("  file(RELATIVE_PATH _kr_relative \"${CMAKE_SOURCE_DIR}\" \"${CMAKE_CURRENT_SOURCE_DIR}\")\n");
        s.append("  if(_kr_relative MATCHES \"^\\\\.\\\\.\")\n    return()\n  endif()\n");
        s.append("  set_property(GLOBAL APPEND PROPERTY KR_ALL_TARGETS \"${target}\")\n");
        s.append("  list(FIND _kr_args \"SHARED\" _kr_shared)\n");
        s.append("  if(NOT _kr_shared EQUAL -1)\n");
        s.append("    set_target_properties(${target} PROPERTIES RUNTIME_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\" LIBRARY_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\" ARCHIVE_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\")\n");
        s.append("    set_property(GLOBAL APPEND PROPERTY KR_SHARED_TARGETS \"${target}\")\n");
        s.append("  endif()\nendfunction()\n");

        // Hook add_executable
        s.append("function(add_executable target)\n");
        s.append("  set(_kr_args ${ARGN})\n");
        s.append("  list(FIND _kr_args \"IMPORTED\" _kr_imported)\n  list(FIND _kr_args \"ALIAS\" _kr_alias)\n");
        s.append("  if(NOT _kr_imported EQUAL -1 OR NOT _kr_alias EQUAL -1)\n    _add_executable(${target} ${ARGN})\n    return()\n  endif()\n");
        s.append("  file(RELATIVE_PATH _kr_relative \"${CMAKE_SOURCE_DIR}\" \"${CMAKE_CURRENT_SOURCE_DIR}\")\n");
        s.append("  if(_kr_relative MATCHES \"^\\\\.\\\\.\")\n    _add_executable(${target} ${ARGN})\n    return()\n  endif()\n");
        s.append("  list(FIND _kr_args \"WIN32\" _kr_win32_idx)\n");
        s.append("  list(FIND _kr_args \"EXCLUDE_FROM_ALL\" _kr_excl_idx)\n");
        s.append("  list(FILTER _kr_args EXCLUDE REGEX \"\\\\.[rR][cC]$\")\n");
        if (activeTarget == Target.ANDROID) {
            s.append("  list(REMOVE_ITEM _kr_args WIN32 MACOSX_BUNDLE EXCLUDE_FROM_ALL)\n");
            s.append("  _add_library(${target} SHARED ${_kr_args})\n");
            s.append("  set_target_properties(${target} PROPERTIES PREFIX \"lib\" LIBRARY_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\" RUNTIME_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\")\n");
            s.append("  target_link_options(${target} PRIVATE -Wl,--export-dynamic)\n");
        } else {
            s.append("  _add_executable(${target} ${_kr_args})\n");
            s.append("  set_target_properties(${target} PROPERTIES RUNTIME_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\")\n");
        }
        s.append("  if(NOT _kr_win32_idx EQUAL -1)\n    set_property(GLOBAL PROPERTY KR_WIN32_${target} \"1\")\n  else()\n    set_property(GLOBAL PROPERTY KR_WIN32_${target} \"0\")\n  endif()\n");
        s.append("  if(NOT _kr_excl_idx EQUAL -1)\n    set_property(GLOBAL PROPERTY KR_EXCL_${target} \"1\")\n  else()\n    set_property(GLOBAL PROPERTY KR_EXCL_${target} \"0\")\n  endif()\n");
        s.append("  set_property(GLOBAL APPEND PROPERTY KR_EXECUTABLE_TARGETS \"${target}\")\n");
        s.append("  set_property(GLOBAL APPEND PROPERTY KR_ALL_TARGETS \"${target}\")\nendfunction()\n");

        // Deferred finalization after CMakeLists.txt evaluation
        s.append("function(kr_write_executable_targets)\n");
        s.append("  get_property(_all GLOBAL PROPERTY KR_ALL_TARGETS)\n");
        s.append("  if(_all)\n    list(REMOVE_DUPLICATES _all)\n");
        s.append("    foreach(_t IN LISTS _all)\n");
        s.append("      if(TARGET ${_t})\n");
        s.append("        set_target_properties(${_t} PROPERTIES COMPILE_WARNING_AS_ERROR OFF)\n");
        s.append("        _target_compile_options(${_t} PRIVATE -Wno-error)\n");
        s.append("      endif()\n");
        s.append("    endforeach()\n  endif()\n");
        s.append("  get_property(_shared GLOBAL PROPERTY KR_SHARED_TARGETS)\n");
        s.append("  if(_shared)\n    list(REMOVE_DUPLICATES _shared)\n");
        s.append("    foreach(_st IN LISTS _shared)\n");
        s.append("      if(TARGET ${_st})\n");
        s.append("        set_target_properties(${_st} PROPERTIES RUNTIME_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\" LIBRARY_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\" ARCHIVE_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\")\n");
        s.append("      endif()\n");
        s.append("    endforeach()\n");
        s.append("    string(REPLACE \";\" \"\\n\" _stext \"${_shared}\")\n");
        s.append("    file(WRITE \"").append(shList).append("\" \"${_stext}\\n\")\n  endif()\n");
        s.append("  get_property(_targets GLOBAL PROPERTY KR_EXECUTABLE_TARGETS)\n");
        s.append("  if(_targets)\n    list(REMOVE_DUPLICATES _targets)\n");
        s.append("    set(_meta_lines \"\")\n");
        s.append("    foreach(_t IN LISTS _targets)\n");
        s.append("      if(TARGET ${_t})\n");
        s.append("        set_target_properties(${_t} PROPERTIES RUNTIME_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\" LIBRARY_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\")\n");
        s.append("        get_property(_w GLOBAL PROPERTY KR_WIN32_${_t})\n");
        s.append("        get_property(_e GLOBAL PROPERTY KR_EXCL_${_t})\n");
        s.append("        get_target_property(_outname ${_t} OUTPUT_NAME)\n");
        s.append("        if(NOT _outname)\n          set(_outname \"\")\n        endif()\n");
        s.append("        get_target_property(_sdir ${_t} SOURCE_DIR)\n");
        s.append("        get_target_property(_srcs ${_t} SOURCES)\n");
        if (activeTarget == Target.WINDOWS) {
            s.append("        foreach(_src IN LISTS _srcs)\n");
            s.append("          if(EXISTS \"${_sdir}/${_src}\")\n            set(_full \"${_sdir}/${_src}\")\n          elseif(EXISTS \"${_src}\")\n            set(_full \"${_src}\")\n          else()\n            set(_full \"\")\n          endif()\n");
            s.append("          if(_full MATCHES \"\\\\.(c|cc|cpp|cxx)$\")\n");
            s.append("            file(READ \"${_full}\" _scontent LIMIT 131072)\n");
            s.append("            if(_scontent MATCHES \"wWinMain|wmain\")\n");
            s.append("              _target_link_options(${_t} PRIVATE -municode)\n");
            s.append("            endif()\n");
            s.append("          endif()\n");
            s.append("        endforeach()\n");
        }
        s.append("        string(REPLACE \";\" \",\" _srcs_csv \"${_srcs}\")\n");
        s.append("        string(APPEND _meta_lines \"${_t}|${_w}|${_e}|${_outname}|${_sdir}|${_srcs_csv}\\n\")\n");
        s.append("      endif()\n");
        s.append("    endforeach()\n");
        s.append("    file(WRITE \"").append(mList).append("\" \"${_meta_lines}\")\n");
        s.append("    string(REPLACE \";\" \"\\n\" _text \"${_targets}\")\n");
        s.append("    file(WRITE \"").append(list).append("\" \"${_text}\\n\")\n  endif()\nendfunction()\n");
        s.append("cmake_language(DEFER DIRECTORY \"${CMAKE_SOURCE_DIR}\" CALL kr_write_executable_targets)\n");
        return s.toString();
    }

    private void appendDownloadableDependencies(StringBuilder s, Set<String> packages) {
        if (packages.isEmpty()) return;
        s.append("include(FetchContent)\n");
        for (String packageName : packages) {
            String key = packageName.toLowerCase(Locale.ROOT).replace('-', '_');
            String name = packageName;
            String url = null;
            String hash = null;
            String options = "";
            String patchCommand = "";
            if (key.equals("sdl3")) {
                name = "SDL3"; url = "https://github.com/libsdl-org/SDL/archive/refs/tags/release-3.4.18.tar.gz";
                hash = "12fcb5592016b27922b08d1580d75f5f15c72e67cf04415e2bd824dc7563ac940c25bbe368b03d0a755f5832803049467e3d0ee6784ed3684792bc4d7741af50";
                options = "set(SDL_SHARED OFF CACHE BOOL \"\" FORCE)\nset(SDL_STATIC ON CACHE BOOL \"\" FORCE)\n"
                        + "set(SDL_TEST_LIBRARY OFF CACHE BOOL \"\" FORCE)\nset(SDL_TESTS OFF CACHE BOOL \"\" FORCE)\n"
                        + "set(SDL_EXAMPLES OFF CACHE BOOL \"\" FORCE)\nset(SDL_INSTALL OFF CACHE BOOL \"\" FORCE)\n"
                        + (target == Target.ANDROID
                        ? "set(SDL_ANDROID_JAR OFF CACHE BOOL \"\" FORCE)\nset(SDL_VULKAN OFF CACHE BOOL \"\" FORCE)\nset(SDL_SYSTEM_ICONV OFF CACHE BOOL \"\" FORCE)\nset(HAVE_WL_VERSION_SCRIPT TRUE CACHE INTERNAL \"\" FORCE)\n"
                        : "")
                        + (target == Target.LINUX
                        ? "set(HAVE_WL_VERSION_SCRIPT TRUE CACHE INTERNAL \"\" FORCE)\nset(SDL_X11 ON CACHE BOOL \"\" FORCE)\nset(SDL_X11_SHARED ON CACHE BOOL \"\" FORCE)\nset(X11_INCLUDEDIR \"${CMAKE_SYSROOT}/usr/include\" CACHE PATH \"\" FORCE)\nset(X11_LIB \"${CMAKE_SYSROOT}/usr/lib/x86_64-linux-gnu/libX11.so\" CACHE FILEPATH \"\" FORCE)\nset(XEXT_LIB \"${CMAKE_SYSROOT}/usr/lib/x86_64-linux-gnu/libXext.so\" CACHE FILEPATH \"\" FORCE)\nset(HAVE_XEXT_H \"${CMAKE_SYSROOT}/usr/include/X11/extensions/Xext.h\" CACHE FILEPATH \"\" FORCE)\nset(SDL_X11_XCURSOR OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XDBE OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XINPUT OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XFIXES OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XRANDR OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XSCRNSAVER OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XSHAPE OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XSYNC OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XTEST OFF CACHE BOOL \"\" FORCE)\nset(SDL_WAYLAND OFF CACHE BOOL \"\" FORCE)\nset(SDL_KMSDRM OFF CACHE BOOL \"\" FORCE)\nset(SDL_OPENGL OFF CACHE BOOL \"\" FORCE)\nset(SDL_OPENGLES OFF CACHE BOOL \"\" FORCE)\n"
                        : "");
                if (target == Target.ANDROID) {
                    // CMAKE_TRY_COMPILE_TARGET_TYPE is STATIC_LIBRARY in KR's standalone
                    // cross toolchain. SDL's linker probe therefore cannot test a linker
                    // option although Android lld supports it. Patch only this false-negative
                    // guard after the verified source archive has been extracted.
                    s.append("file(WRITE \"${CMAKE_BINARY_DIR}/kr-patch-sdl-android.cmake\" [=[\n")
                            .append("file(READ \"${KR_SDL_SOURCE}/CMakeLists.txt\" _kr_sdl_cmake)\n")
                            .append("string(REPLACE \"if(((LINUX AND LIBC_IS_GLIBC) OR ANDROID) AND (NOT USE_TCC))\" \"if((LINUX AND LIBC_IS_GLIBC) AND (NOT USE_TCC) AND NOT ANDROID)\" _kr_sdl_cmake \"${_kr_sdl_cmake}\")\n")
                            .append("file(WRITE \"${KR_SDL_SOURCE}/CMakeLists.txt\" \"${_kr_sdl_cmake}\")\n")
                            .append("]=])\n");
                    patchCommand = " PATCH_COMMAND \"${CMAKE_COMMAND}\" \"-DKR_SDL_SOURCE=<SOURCE_DIR>\" -P \"${CMAKE_BINARY_DIR}/kr-patch-sdl-android.cmake\"";
                }
            } else if (key.equals("imgui")) {
                log.line("VCPKG-Port imgui erkannt; SDL3-Renderer-Backend wird eingebunden.");
                s.append("cmake_policy(PUSH)\ncmake_policy(SET CMP0169 OLD)\n");
                s.append("FetchContent_Declare(imgui URL \"https://github.com/ocornut/imgui/archive/refs/tags/v1.93.0.tar.gz\" URL_HASH SHA512=de41ef101f37d09685bf9e69bacefe06a0c010ecd0e6e7ed1e4efbff82fde27a9c3ae36ed436db5bcde4c61de10da984dfb74d6098586a3f78d8a716864460c2 DOWNLOAD_EXTRACT_TIMESTAMP TRUE)\n");
                s.append("FetchContent_GetProperties(imgui)\nif(NOT imgui_POPULATED)\n  FetchContent_Populate(imgui)\nendif()\n");
                s.append("if(NOT TARGET imgui)\n  add_library(imgui STATIC ${imgui_SOURCE_DIR}/imgui.cpp ${imgui_SOURCE_DIR}/imgui_draw.cpp ${imgui_SOURCE_DIR}/imgui_tables.cpp ${imgui_SOURCE_DIR}/imgui_widgets.cpp ${imgui_SOURCE_DIR}/backends/imgui_impl_sdl3.cpp ${imgui_SOURCE_DIR}/backends/imgui_impl_sdlrenderer3.cpp)\n");
                s.append("  target_include_directories(imgui PUBLIC ${imgui_SOURCE_DIR} ${imgui_SOURCE_DIR}/backends)\n  target_link_libraries(imgui PUBLIC SDL3::SDL3)\n  add_library(imgui::imgui ALIAS imgui)\nendif()\ncmake_policy(POP)\n");
                continue;
            } else if (key.equals("fmt")) {
                name = "fmt"; url = "https://github.com/fmtlib/fmt/archive/refs/tags/12.2.0.tar.gz";
                hash = "5ac2ba0f54a484999ed5407d82b77aad170cea49a267decd2c0eedadf3b14413e2a83fcc8e9ca9c16640595e019b8636e160f72314d8be50653324e82ac745eb";
                options = "set(FMT_TEST OFF CACHE BOOL \"\" FORCE)\nset(FMT_DOC OFF CACHE BOOL \"\" FORCE)\n";
            } else if (key.equals("nlohmann_json")) {
                name = "nlohmann_json"; url = "https://github.com/nlohmann/json/archive/refs/tags/v3.12.0.tar.gz";
                hash = "6cc1e86261f8fac21cc17a33da3b6b3c3cd5c116755651642af3c9e99bb3538fd42c1bd50397a77c8fb6821bc62d90e6b91bcdde77a78f58f2416c62fc53b97d";
                options = "set(JSON_BuildTests OFF CACHE BOOL \"\" FORCE)\n";
            } else if (key.equals("spdlog")) {
                name = "spdlog"; url = "https://github.com/gabime/spdlog/archive/refs/tags/v1.17.0.tar.gz";
                hash = "8df117055d19ff21c9c9951881c7bdf27cc0866ea3a4aa0614b2c3939cedceab94ac9abaa63dc4312b51562b27d708cb2f014c68c603fd1c1051d3ed5c1c3087";
                options = "set(SPDLOG_BUILD_TESTS OFF CACHE BOOL \"\" FORCE)\nset(SPDLOG_BUILD_EXAMPLE OFF CACHE BOOL \"\" FORCE)\n";
            } else if (key.equals("catch2")) {
                name = "Catch2"; url = "https://github.com/catchorg/Catch2/archive/refs/tags/v3.15.3.tar.gz";
                hash = "313d413fe2daa070a60abe17641d112da72320a917cd96bcbda3931d4c47bbca81f669bd7724387bc63a3614112fc12ae8228043bf58a676d0191ee5b16bc7a1";
                options = "set(CATCH_BUILD_TESTING OFF CACHE BOOL \"\" FORCE)\nset(CATCH_INSTALL_DOCS OFF CACHE BOOL \"\" FORCE)\n";
            } else if (key.equals("eigen3")) {
                name = "Eigen3"; url = "https://gitlab.com/libeigen/eigen/-/archive/5.0.1/eigen-5.0.1.tar.gz";
                hash = "b337d3bc38440db190a8f1fbc4eabc0098e69fcc95bfba195fe039ffb942cae2a7f0153f3094f35fa26325750d1c62e20cccaf916a41f5c7f248ec5e5d30a942";
                options = "set(BUILD_TESTING OFF CACHE BOOL \"\" FORCE)\nset(EIGEN_BUILD_DOC OFF CACHE BOOL \"\" FORCE)\n";
            }
            if (url == null) {
                log.line("CMake-Paket " + packageName + " erkannt; kein automatischer freier Download im KR-Katalog.");
                continue;
            }
            log.line("CMake-Paket " + packageName + " erkannt; geprüfter Download wird bei Bedarf vorbereitet.");
            s.append(options);
            s.append("FetchContent_Declare(").append(name).append(" URL \"").append(url)
                    .append("\" URL_HASH SHA512=").append(hash)
                    .append(" DOWNLOAD_EXTRACT_TIMESTAMP TRUE OVERRIDE_FIND_PACKAGE")
                    .append(patchCommand).append(")\n");
        }
    }

    private static Set<String> findCMakePackages(File root) throws IOException {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Pattern pattern = Pattern.compile("find_package\\s*\\(\\s*([A-Za-z0-9_+.-]+)", Pattern.CASE_INSENSITIVE);
        List<File> files = new ArrayList<>();
        collectCMakeFiles(root, files);
        for (File file : files) {
            if (file.length() > 2L * 1024L * 1024L) continue;
            String text;
            try (InputStream in = new FileInputStream(file)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[32 * 1024]; int n; while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
                text = out.toString(StandardCharsets.UTF_8.name());
            }
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (!name.equalsIgnoreCase("Threads")) result.add(name);
            }
        }
        return result;
    }

    private static void collectCMakeFiles(File file, List<File> result) {
        if (file.isDirectory()) {
            String name = file.getName();
            if (name.equals("KR_BUILD") || name.equals(".git") || name.equals("build") || name.startsWith("cmake-build-")) return;
            File[] children = file.listFiles(); if (children != null) for (File child : children) collectCMakeFiles(child, result);
            return;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.equals("cmakelists.txt") || name.endsWith(".cmake")) result.add(file);
    }

    private static File findPrimaryCMakeProject(File root) {
        if (new File(root, "CMakeLists.txt").isFile()) return root;
        List<File> cmakeLists = new ArrayList<>();
        collectCMakeListsOnly(root, cmakeLists);
        if (cmakeLists.isEmpty()) return null;
        String rootPath = root.getAbsolutePath().replace('\\', '/');
        cmakeLists.sort((a, b) -> {
            String pa = a.getAbsolutePath().replace('\\', '/').substring(rootPath.length()).toLowerCase(Locale.ROOT);
            String pb = b.getAbsolutePath().replace('\\', '/').substring(rootPath.length()).toLowerCase(Locale.ROOT);
            int scoreA = scoreCMakePath(pa);
            int scoreB = scoreCMakePath(pb);
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            int depthA = pa.split("/").length;
            int depthB = pb.split("/").length;
            if (depthA != depthB) return Integer.compare(depthA, depthB);
            return pa.compareTo(pb);
        });
        return cmakeLists.get(0).getParentFile();
    }

    private static int scoreCMakePath(String relLower) {
        int score = 100;
        if (relLower.contains("07_project_snapshot") || relLower.contains("recovery_legacy")) score -= 80;
        if (relLower.contains("/examples/") || relLower.contains("/tests/") || relLower.contains("/test/")) score -= 40;
        if (relLower.contains("/third_party/")) score -= 10;
        return score;
    }

    private static void collectCMakeListsOnly(File file, List<File> result) {
        if (file.isDirectory()) {
            String name = file.getName();
            if (name.equals("KR_BUILD") || name.equals(".git") || name.equals("build") || name.equals("build_vs")
                    || name.equals("out") || name.equals("node_modules") || name.equals(".gradle")
                    || name.equals(".idea") || name.startsWith("cmake-build-")) return;
            File[] children = file.listFiles();
            if (children != null) for (File child : children) collectCMakeListsOnly(child, result);
            return;
        }
        if (file.getName().equalsIgnoreCase("CMakeLists.txt")) result.add(file);
    }

    private static final class CMakeTargetInfo {
        final String name;
        final boolean isWin32;
        final boolean isExcluded;
        final String outputName;
        final boolean requiresWin32;
        final boolean requiresX11;
        final boolean isTest;

        CMakeTargetInfo(String name, boolean isWin32, boolean isExcluded, String outputName,
                        boolean requiresWin32, boolean requiresX11, boolean isTest) {
            this.name = name;
            this.isWin32 = isWin32;
            this.isExcluded = isExcluded;
            this.outputName = outputName;
            this.requiresWin32 = requiresWin32;
            this.requiresX11 = requiresX11;
            this.isTest = isTest;
        }
    }

    private List<CMakeTargetInfo> parseCMakeTargets(File metaFile, List<String> fallbackNames, File sourceProject) throws IOException {
        List<CMakeTargetInfo> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (metaFile.isFile()) {
            for (String line : readLines(metaFile)) {
                String[] parts = line.split("\\|", -1);
                if (parts.length < 6) continue;
                String name = parts[0].trim();
                if (name.isEmpty() || !seen.add(name)) continue;
                boolean isWin32 = "1".equals(parts[1].trim());
                boolean isExcl = "1".equals(parts[2].trim());
                String outName = parts[3].trim();
                String srcDir = parts[4].trim();
                String[] srcs = parts[5].split(",");
                boolean reqWin32 = isWin32 || name.toLowerCase(Locale.ROOT).endsWith("-win32");
                boolean reqX11 = name.toLowerCase(Locale.ROOT).endsWith("-x11");
                for (String s : srcs) {
                    s = s.trim();
                    if (s.isEmpty()) continue;
                    File sf = new File(s);
                    if (!sf.isAbsolute() && !srcDir.isEmpty()) sf = new File(srcDir, s);
                    if (!sf.isFile()) sf = new File(sourceProject, s);
                    if (sf.isFile()) {
                        if (fileRequiresUnconditionalWin32(sf)) reqWin32 = true;
                        if (fileRequiresUnconditionalX11(sf)) reqX11 = true;
                    }
                }
                boolean isTest = isTestTargetName(name);
                result.add(new CMakeTargetInfo(name, isWin32, isExcl, outName.isEmpty() ? null : outName, reqWin32, reqX11, isTest));
            }
        }
        for (String name : fallbackNames) {
            if (seen.add(name)) {
                boolean reqWin32 = name.toLowerCase(Locale.ROOT).endsWith("-win32");
                boolean reqX11 = name.toLowerCase(Locale.ROOT).endsWith("-x11");
                result.add(new CMakeTargetInfo(name, reqWin32, false, null, reqWin32, reqX11, isTestTargetName(name)));
            }
        }
        return result;
    }

    private static boolean isTestTargetName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("test_") || lower.startsWith("tests_")
                || lower.endsWith("_test") || lower.endsWith("_tests") || lower.endsWith("tests")
                || lower.endsWith("smoke") || lower.endsWith("_probe") || lower.endsWith("probe")
                || lower.endsWith("acceptance") || lower.endsWith("_bench");
    }

    private static boolean fileRequiresUnconditionalWin32(File file) {
        return fileRequiresUnconditionalWin32Depth(file, 0);
    }

    private static boolean fileRequiresUnconditionalWin32Depth(File file, int depth) {
        if (depth > 2 || file == null || !file.isFile()) return false;
        try {
            String text = readTextLimited(file, 65536);
            boolean hasWinHeader = Pattern.compile("#\\s*include\\s*[<\"](windows|d2d1|dwrite|amsi|fltuser|winioctl)\\.h[>\"]", Pattern.CASE_INSENSITIVE).matcher(text).find();
            if (hasWinHeader) {
                boolean hasWinGuard = Pattern.compile("#\\s*(if|ifdef|elif).*(_WIN32|WIN32)").matcher(text).find();
                if (!hasWinGuard) return true;
            }
            if (depth < 2) {
                Matcher m = Pattern.compile("#\\s*include\\s*\"([^\"]+\\.h(?:pp|xx)?)\"").matcher(text);
                while (m.find()) {
                    File hdr = resolveLocalHeader(file, m.group(1));
                    if (hdr != null && fileRequiresUnconditionalWin32Depth(hdr, depth + 1)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static File resolveLocalHeader(File fromFile, String includePath) {
        File parent = fromFile.getParentFile();
        if (parent == null) return null;
        File direct = new File(parent, includePath);
        if (direct.isFile()) return direct;
        File grand = parent.getParentFile();
        if (grand != null) {
            File inSrc = new File(new File(grand, "src"), includePath);
            if (inSrc.isFile()) return inSrc;
            File inInc = new File(new File(grand, "include"), includePath);
            if (inInc.isFile()) return inInc;
        }
        return null;
    }

    private static boolean fileRequiresUnconditionalX11(File file) {
        try {
            String text = readTextLimited(file, 65536);
            boolean hasX11 = Pattern.compile("#\\s*include\\s*<X11/Xlib\\.h>").matcher(text).find();
            if (!hasX11) return false;
            boolean hasGuard = Pattern.compile("#\\s*(if|ifdef|elif).*(__linux__|UNIX|X11)").matcher(text).find();
            return !hasGuard;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readTextLimited(File file, int maxBytes) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0, n;
            while (total < maxBytes && (n = in.read(buf, 0, Math.min(buf.length, maxBytes - total))) >= 0) {
                out.write(buf, 0, n);
                total += n;
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static List<CMakeTargetInfo> filterCompatibleCMakeTargets(List<CMakeTargetInfo> all, Target activeTarget) {
        List<CMakeTargetInfo> compatible = new ArrayList<>();
        for (CMakeTargetInfo t : all) {
            if (activeTarget == Target.WINDOWS && t.requiresX11) continue;
            if (activeTarget == Target.LINUX && t.requiresWin32) continue;
            if (activeTarget == Target.ANDROID && (t.requiresWin32 || t.requiresX11)) continue;
            compatible.add(t);
        }
        return compatible;
    }

    private CMakeTargetInfo selectBestCMakeTarget(List<CMakeTargetInfo> candidates, File sourceProject, File runtimeProject) throws IOException {
        for (File dir : new File[]{runtimeProject, sourceProject}) {
            File selection = new File(dir, "kr-android-target.txt");
            if (selection.isFile()) {
                List<String> lines = readLines(selection);
                if (!lines.isEmpty()) {
                    String wanted = lines.get(0).trim();
                    for (CMakeTargetInfo c : candidates) {
                        if (c.name.equals(wanted)) return c;
                    }
                }
            }
        }
        if (candidates.size() == 1) return candidates.get(0);
        String projName = (localProjectDir != null ? localProjectDir.getName() : sourceProject.getName())
                .toLowerCase(Locale.ROOT).replaceAll("-[0-9]+$", "").replace('-', '_');
        CMakeTargetInfo best = candidates.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (CMakeTargetInfo c : candidates) {
            int score = 0;
            if (!c.isExcluded) score += 1000;
            if (!c.isTest) score += 500;
            String lower = c.name.toLowerCase(Locale.ROOT);
            String norm = lower.replace('-', '_');
            if (target == Target.WINDOWS && (c.isWin32 || lower.endsWith("-win32") || lower.endsWith("ui") || lower.endsWith("studio") || lower.endsWith("viewer"))) {
                score += 200;
            }
            if (target == Target.LINUX && lower.endsWith("-x11")) {
                score += 150;
            }
            if (norm.equals(projName) || norm.startsWith(projName + "_")) {
                score += 120;
            }
            if (lower.contains("demo") || lower.contains("cli") || lower.contains("app") || lower.contains("studio")
                    || lower.contains("server") || lower.contains("lab") || lower.contains("robot") || lower.contains("main")
                    || lower.contains("diag") || lower.contains("research")) {
                score += 60;
            }
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        log.line("Mehrere CMake-Ziele erkannt (" + candidates.size() + "); Hauptziel ausgewählt: " + best.name);
        return best;
    }

    private void copyAllCMakeArtifacts(File artifacts, File binary, File destinationDir) {
        List<File> files = new ArrayList<>();
        collectFiles(artifacts, files);
        collectFiles(new File(binary, "bin"), files);
        collectFiles(new File(binary, "lib"), files);
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".exe") || lower.endsWith(".dll") || lower.endsWith(".dll.a") || lower.endsWith(".so")) {
                try {
                    File dest = new File(destinationDir, name);
                    if (!f.getCanonicalPath().equals(dest.getCanonicalPath())) {
                        copy(f, dest);
                        dest.setExecutable(true, false);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private File findCMakeArtifact(File artifacts, File binary, String selected, String outputName, boolean isSharedLib, Target activeTarget) {
        List<String> baseNames = new ArrayList<>();
        if (outputName != null && !outputName.isEmpty()) baseNames.add(outputName);
        if (!baseNames.contains(selected)) baseNames.add(selected);

        List<String> expectedNames = new ArrayList<>();
        for (String base : baseNames) {
            if (isSharedLib) {
                if (activeTarget == Target.WINDOWS) {
                    expectedNames.add(base + ".dll");
                    expectedNames.add("lib" + base + ".dll");
                } else {
                    expectedNames.add("lib" + base + ".so");
                    expectedNames.add(base + ".so");
                }
            } else {
                if (activeTarget == Target.ANDROID) {
                    expectedNames.add("lib" + base + ".so");
                    expectedNames.add(base + ".so");
                } else if (activeTarget == Target.WINDOWS) {
                    expectedNames.add(base + ".exe");
                } else {
                    expectedNames.add(base);
                }
            }
        }

        List<File> searchRoots = Arrays.asList(artifacts, new File(binary, "bin"), new File(binary, "lib"), binary);
        for (File rootDir : searchRoots) {
            for (String exp : expectedNames) {
                File exact = new File(rootDir, exp);
                if (exact.isFile()) return exact;
            }
        }
        List<File> allFiles = new ArrayList<>();
        for (File rootDir : searchRoots) collectFiles(rootDir, allFiles);
        for (String exp : expectedNames) {
            for (File f : allFiles) {
                if (f.getName().equalsIgnoreCase(exp)) return f;
            }
        }
        for (String exp : expectedNames) {
            String normExp = exp.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
            for (File f : allFiles) {
                if (f.getName().contains("CMakeFiles")) continue;
                String normFile = f.getName().replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
                if (normFile.equals(normExp)) return f;
            }
        }
        return null;
    }

    private File createPlatformFallbackWrapper(File winExe, File runtimeProject) throws Exception {
        File wrapperDir = new File(project.getParentFile(), "win32-fallback-wrapper");
        delete(wrapperDir);
        wrapperDir.mkdirs();
        File stubCpp = new File(wrapperDir, "main.cpp");
        String exeName = winExe != null ? winExe.getName() : "programm.exe";
        writeText(stubCpp, "#include <cstdio>\nint main() {\n"
                + "    std::printf(\"Win32-Cross-Build erfolgreich: KR_BUILD/" + exeName + "\\n\");\n"
                + "    return 0;\n}\n");
        if (target == Target.ANDROID) {
            return buildAndroid(Collections.singletonList(stubCpp));
        } else {
            return buildDesktop(Collections.singletonList(stubCpp));
        }
    }

    private File buildKrUnified(List<File> krFiles) throws Exception {
        boolean gui = isLanguageGuiProject();
        log.line("KR-Frontend analysiert " + krFiles.size() + " .kr-Quelldatei(en) …");
        File krGeneratedDir = new File(project.getParentFile(), "kr-generated");
        delete(krGeneratedDir);
        if (!krGeneratedDir.mkdirs()) throw new IOException("KR-Ausgabeordner kann nicht erstellt werden.");

        KrPipeline.PipelineResult pipeline = KrPipeline.process(project, krGeneratedDir, gui);
        if (!pipeline.success) {
            for (KrDiagnostic diag : pipeline.diagnostics) {
                log.line(diag.toString());
                appendText(buildLog, diag.toString() + "\n");
            }
            throw new IOException(pipeline.diagnostics.isEmpty() ? "Fehler bei der KR-Sprachbereichsanalyse." : pipeline.diagnostics.get(0).message);
        }

        log.line(pipeline.segments.size() + " Sprachbereich(e) analysiert: " + pipeline.languagesUsed);
        if (pipeline.languagesUsed.isEmpty()) {
            throw new IOException("Keine ausführbaren Sprachbereiche in den .kr-Dateien gefunden.");
        }

        if (pipeline.languagesUsed.size() == 1 && !gui) {
            if (pipeline.languagesUsed.contains(KrLanguage.CPP)) {
                log.line("Reines C++ in .kr; direkter Compilerpfad wird verwendet.");
                List<File> cppSrcs = (pipeline.codeResult.cppSources != null && !pipeline.codeResult.cppSources.isEmpty())
                        ? pipeline.codeResult.cppSources
                        : Collections.singletonList(pipeline.codeResult.cppSource);
                return target == Target.ANDROID ? buildAndroid(cppSrcs) : buildDesktop(cppSrcs);
            } else if (pipeline.languagesUsed.contains(KrLanguage.RUST)) {
                log.line("Reines Rust in .kr; Cargo und rustc werden verwendet.");
                File archive = compileRustModule(pipeline.codeResult.rustDir);
                return linkLanguageArchive(archive, false, "Rust");
            } else if (pipeline.languagesUsed.contains(KrLanguage.GO)) {
                log.line("Reines Go in .kr; go build wird verwendet.");
                return buildGoModuleDirect(pipeline.codeResult.goDir, false);
            }
        }

        log.line("KR Sprachfusion (" + pipeline.languagesUsed.size() + " Sprachen: " + pipeline.languagesUsed + ")");
        File rustArchive = null;
        if (pipeline.languagesUsed.contains(KrLanguage.RUST)) {
            rustArchive = compileRustModule(pipeline.codeResult.rustDir);
        }

        File goArchive = null;
        if (pipeline.languagesUsed.contains(KrLanguage.GO)) {
            goArchive = compileGoModule(pipeline.codeResult.goDir);
        }

        return linkKrUnified(pipeline.codeResult.cppSource, pipeline.codeResult.cppSources, rustArchive, goArchive, gui, pipeline.codeResult.vcpkgDependencies);
    }

    private File compileRustModule(File moduleDir) throws Exception {
        File cargo = new File(toolchain, "usr/bin/cargo");
        File rustc = new File(toolchain, "usr/bin/rustc");
        if (!cargo.canExecute() || !rustc.canExecute())
            throw new IOException("Rust-Compiler fehlt in der Compilerlaufzeit. Bitte die vollständige KR-IDE-APK installieren.");
        String triple = target == Target.ANDROID ? "aarch64-linux-android"
                : target == Target.WINDOWS ? "x86_64-pc-windows-gnu" : "x86_64-unknown-linux-gnu";
        log.line("Rust-Modul wird kompiliert → " + triple);
        File targetDir = new File(project.getParentFile(), "rust-target");
        File archive = new File(targetDir, triple + "/release/libkr_rust.a");
        Map<String, String> env = new HashMap<>();
        env.put("CARGO_HOME", new File(context.getFilesDir(), "cargo-home").getPath());
        env.put("CARGO_TARGET_DIR", targetDir.getPath());
        env.put("CARGO_NET_OFFLINE", onlineDependencies ? "false" : "true");
        env.put("RUST_BACKTRACE", "1");
        env.put("RUSTC_BOOTSTRAP", "1");
        env.put("CARGO_TARGET_" + triple.toUpperCase(Locale.ROOT).replace('-', '_') + "_LINKER",
                new File(toolchain, "usr/bin/clang").getPath());
        List<String> command = new ArrayList<>();
        Collections.addAll(command, cargo.getPath(), "build", "--release", "--lib", "--target", triple);
        if (target != Target.ANDROID) command.add("-Zbuild-std=std,panic_abort");
        run(command, "Cargo kompiliert das Rust-Modul", moduleDir, env);
        if (!archive.isFile()) throw new IOException("Cargo hat die erwartete Rust-Bibliothek nicht erzeugt: " + archive.getName());
        return archive;
    }

    private File compileGoModule(File moduleDir) throws Exception {
        File go = new File(toolchain, "usr/bin/go");
        if (!go.canExecute()) throw new IOException("Go-Compiler fehlt in der Compilerlaufzeit. Bitte die vollständige KR-IDE-APK installieren.");
        String goos = target == Target.ANDROID ? "android" : target == Target.WINDOWS ? "windows" : "linux";
        String goarch = target == Target.ANDROID ? "arm64" : "amd64";
        File languageDir = new File(project.getParentFile(), "go-target");
        if (!languageDir.isDirectory() && !languageDir.mkdirs()) throw new IOException("Go-Ausgabeordner kann nicht erstellt werden.");
        String ext = target == Target.ANDROID ? ".so" : ".a";
        String buildmode = target == Target.ANDROID ? "-buildmode=c-shared" : "-buildmode=c-archive";
        File archive = new File(languageDir, "libkr_go_" + target.name().toLowerCase(Locale.ROOT) + ext);
        if (archive.exists()) archive.delete();
        Map<String, String> env = new HashMap<>();
        env.put("GOOS", goos); env.put("GOARCH", goarch); env.put("CGO_ENABLED", "1");
        env.put("GOROOT", new File(toolchain, "usr/lib/go").getPath());
        env.put("GOCACHE", new File(context.getCacheDir(), "go-build-cache").getPath());
        env.put("GOMODCACHE", new File(context.getFilesDir(), "go-mod-cache").getPath());
        env.put("GOPROXY", onlineDependencies ? "https://proxy.golang.org,direct" : "off");
        env.put("CC", languageCrossCompiler());
        List<String> command = new ArrayList<>();
        Collections.addAll(command, go.getPath(), "build", "-buildvcs=false", buildmode);
        if (target == Target.ANDROID) {
            command.add("-ldflags=-extldflags=-Wl,-soname," + archive.getName());
        }
        Collections.addAll(command, "-o", archive.getPath(), ".");
        run(command, "Go kompiliert das Go-Modul", moduleDir, env);
        if (!archive.isFile()) throw new IOException("Go hat die erwartete C-Archivdatei nicht erzeugt.");
        return archive;
    }

    private File buildGoModuleDirect(File moduleDir, boolean gui) throws Exception {
        File go = new File(toolchain, "usr/bin/go");
        if (!go.canExecute()) throw new IOException("Go-Compiler fehlt in der Compilerlaufzeit. Bitte die vollständige KR-IDE-APK installieren.");
        String goos = target == Target.ANDROID ? "android" : target == Target.WINDOWS ? "windows" : "linux";
        String goarch = target == Target.ANDROID ? "arm64" : "amd64";
        log.line("Go " + (gui ? "GUI" : "Konsole") + " → " + goos + "/" + goarch);
        File languageDir = new File(project.getParentFile(), "go-target");
        if (!languageDir.isDirectory() && !languageDir.mkdirs()) throw new IOException("Go-Ausgabeordner kann nicht erstellt werden.");
        File archive = new File(languageDir, "libkr_language.a");
        Map<String, String> env = new HashMap<>();
        env.put("GOOS", goos); env.put("GOARCH", goarch); env.put("CGO_ENABLED", "1");
        env.put("GOROOT", new File(toolchain, "usr/lib/go").getPath());
        env.put("GOCACHE", new File(context.getCacheDir(), "go-build-cache").getPath());
        env.put("GOMODCACHE", new File(context.getFilesDir(), "go-mod-cache").getPath());
        env.put("GOPROXY", onlineDependencies ? "https://proxy.golang.org,direct" : "off");
        env.put("CC", languageCrossCompiler());
        List<String> command = new ArrayList<>();
        if (target == Target.ANDROID) {
            if (gui) {
                File sdl = ensureAndroidSdlArchive();
                env.put("CGO_LDFLAGS", "-Wl,-u,JNI_OnLoad " + sdl.getPath() + " "
                        + "-L" + new File(toolchain, "usr/lib").getPath()
                        + " -lc++_shared -llog -landroid -ldl -lm -lOpenSLES -lGLESv1_CM -lGLESv2");
            }
            File library = new File(output, "libuser.so");
            Collections.addAll(command, go.getPath(), "build", "-buildvcs=false", "-buildmode=c-shared", "-o", library.getPath(), ".");
            run(command, "Go kompiliert die Android-Bibliothek", moduleDir, env);
            File apk = new File(output, "programm-android-arm64.apk");
            RunnerPackager.create(context, library, new File(toolchain, "usr/lib/libc++_shared.so"), project, gui, apk, log);
            return apk;
        }
        Collections.addAll(command, go.getPath(), "build", "-buildvcs=false", "-buildmode=c-archive", "-o", archive.getPath(), ".");
        run(command, "Go kompiliert das Projekt", moduleDir, env);
        if (!archive.isFile()) throw new IOException("Go hat die erwartete C-Archivdatei nicht erzeugt.");
        return linkLanguageArchive(archive, gui, "Go");
    }

    private File linkKrUnified(File cppSource, List<File> cppSources, File rustArchive, File goArchive, boolean gui, Set<String> vcpkgDependencies) throws Exception {
        File finalizer = new File(project.getParentFile(), "kr-unified-finalizer");
        if (!finalizer.isDirectory() && !finalizer.mkdirs()) throw new IOException("KR-Linkordner kann nicht erstellt werden.");
        File ccRoot = new File(project, "components/cc-opencl");
        File ccDriver = new File(ccRoot, "src/CC_OpenCL.c");
        File ccNoise = new File(ccRoot, "src/CipherCore_NoiseCtrl.c");
        boolean hasCcOpenCl = ccDriver.isFile() && ccNoise.isFile();

        if (target == Target.ANDROID) {
            File library = new File(output, "libuser.so");
            List<String> command = compiler();
            command.add("--target=aarch64-linux-android26"); command.add("-std=c++20");
            command.add("-O2"); command.add("-fPIC"); command.add("-shared");
            command.add("-L" + new File(toolchain, "usr/lib").getPath());
            command.add("-Wl,--export-dynamic");
            command.add("-Wl,--no-undefined");
            if (hasCcOpenCl) {
                command.add("-DCL_TARGET_OPENCL_VERSION=300");
                command.add("-DCL_USE_DEPRECATED_OPENCL_1_2_APIS");
                command.add("-I" + ccRoot.getPath());
                command.add("-I" + new File(ccRoot, "include").getPath());
                command.add("-I" + new File(ccRoot, "CL").getPath());
                command.add("-x"); command.add("c++");
                command.add(ccDriver.getPath()); command.add(ccNoise.getPath());
                command.add("-x"); command.add("none");
            }
            command.add("-Wl,--allow-multiple-definition");
            File sdl = null;
            if (gui) {
                sdl = ensureAndroidSdlArchive();
                File dependencyCache = new File(context.getFilesDir(), "components/cmake/android");
                for (File inc : getAndroidSdlIncludeDirs(dependencyCache)) {
                    command.add("-I" + inc.getPath());
                }
            }
            if (cppSources != null && !cppSources.isEmpty()) {
                for (File src : cppSources) {
                    if (src.isFile()) {
                        command.add("-I" + src.getParentFile().getPath());
                        command.add(src.getPath());
                    }
                }
            } else if (cppSource != null && cppSource.isFile()) {
                command.add("-I" + cppSource.getParentFile().getPath());
                command.add(cppSource.getPath());
            } else {
                File stub = new File(finalizer, "kr_stub.cpp");
                writeText(stub, "extern \"C\" int KrLanguageMain(void);\nint main(int argc, char** argv) { (void)argc; (void)argv; return KrLanguageMain(); }\n");
                command.add(stub.getPath());
            }
            if (rustArchive != null && rustArchive.isFile()) command.add(rustArchive.getPath());
            List<File> extraLibs = new ArrayList<>();
            if (goArchive != null && goArchive.isFile()) {
                if (goArchive.getName().endsWith(".so")) {
                    command.add("-L" + goArchive.getParentFile().getPath());
                    String libName = goArchive.getName();
                    if (libName.startsWith("lib") && libName.endsWith(".so")) {
                        libName = libName.substring(3, libName.length() - 3);
                    }
                    command.add("-l" + libName);
                    extraLibs.add(goArchive);
                } else {
                    command.add(goArchive.getPath());
                }
            }
            if (gui && sdl != null) {
                command.add(sdl.getPath());
                command.add("-lOpenSLES"); command.add("-lGLESv1_CM"); command.add("-lGLESv2");
            }
            if (hasCcOpenCl) command.add("-lOpenCL");
            command.add("-lc++_shared"); command.add("-llog"); command.add("-landroid"); command.add("-ldl"); command.add("-lm");
            command.add("-o"); command.add(library.getPath());
            run(command, "KR Unified Android-Bibliothek wird gelinkt", finalizer);
            verifyAndroidRunnerEntrypoint(library);

            File apk = new File(output, "programm-android-arm64.apk");
            RunnerPackager.create(context, library, new File(toolchain, "usr/lib/libc++_shared.so"), extraLibs, project, gui, apk, log);
            return apk;
        }

        // Desktop: Windows & Linux via CMake finalizer
        StringBuilder cmake = new StringBuilder();
        cmake.append("cmake_minimum_required(VERSION 3.20)\nproject(KRUnifiedFinalizer LANGUAGES C CXX)\n");
        if (rustArchive != null && rustArchive.isFile()) {
            cmake.append("add_library(kr_rust STATIC IMPORTED GLOBAL)\nset_target_properties(kr_rust PROPERTIES IMPORTED_LOCATION \"")
                    .append(cmakeEscape(rustArchive.getPath())).append("\")\n");
        }
        if (goArchive != null && goArchive.isFile()) {
            cmake.append("add_library(kr_go STATIC IMPORTED GLOBAL)\nset_target_properties(kr_go PROPERTIES IMPORTED_LOCATION \"")
                    .append(cmakeEscape(goArchive.getPath())).append("\")\n");
        }
        if (gui) cmake.append("find_package(SDL3 CONFIG REQUIRED)\n");

        List<File> compileSources = new ArrayList<>();
        if (cppSources != null && !cppSources.isEmpty()) {
            compileSources.addAll(cppSources);
        } else if (cppSource != null && cppSource.isFile()) {
            compileSources.add(cppSource);
        } else {
            File entrySource = new File(finalizer, "kr_entry.cpp");
            writeText(entrySource, "extern \"C\" int KrLanguageMain(void);\nint main(int argc, char** argv) { (void)argc; (void)argv; return KrLanguageMain(); }\n");
            compileSources.add(entrySource);
        }

        cmake.append("add_executable(kr_program");
        for (File s : compileSources) {
            cmake.append(" \"").append(cmakeEscape(s.getPath())).append("\"");
        }
        if (hasCcOpenCl) {
            cmake.append(" \"").append(cmakeEscape(ccDriver.getPath())).append("\" \"")
                    .append(cmakeEscape(ccNoise.getPath())).append("\"");
        }
        cmake.append(")\n");
        for (File s : compileSources) {
            cmake.append("target_include_directories(kr_program PRIVATE \"").append(cmakeEscape(s.getParentFile().getPath())).append("\")\n");
        }
        if (hasCcOpenCl) {
            cmake.append("set_source_files_properties(\"").append(cmakeEscape(ccDriver.getPath()))
                    .append("\" \"").append(cmakeEscape(ccNoise.getPath())).append("\" PROPERTIES LANGUAGE CXX)\n");
            cmake.append("target_include_directories(kr_program PRIVATE \"").append(cmakeEscape(ccRoot.getPath()))
                    .append("\" \"").append(cmakeEscape(new File(ccRoot, "include").getPath()))
                    .append("\" \"").append(cmakeEscape(new File(ccRoot, "CL").getPath())).append("\")\n");
            cmake.append("target_compile_definitions(kr_program PRIVATE CL_TARGET_OPENCL_VERSION=300 CL_USE_DEPRECATED_OPENCL_1_2_APIS)\n");
        }
        cmake.append("set_target_properties(kr_program PROPERTIES LINKER_LANGUAGE CXX)\n");
        cmake.append("target_link_options(kr_program PRIVATE \"LINKER:--allow-multiple-definition\")\n");
        cmake.append("target_link_libraries(kr_program PRIVATE");
        if (rustArchive != null && rustArchive.isFile() && goArchive != null && goArchive.isFile()) {
            cmake.append(" -Wl,--start-group kr_rust kr_go -Wl,--end-group");
        } else {
            if (rustArchive != null && rustArchive.isFile()) cmake.append(" kr_rust");
            if (goArchive != null && goArchive.isFile()) cmake.append(" kr_go");
        }
        if (gui) cmake.append(" SDL3::SDL3");
        if (hasCcOpenCl) cmake.append(" OpenCL");
        if (target == Target.WINDOWS) cmake.append(" ws2_32 winmm advapi32 userenv shell32 ole32 bcrypt ntdll");
        else cmake.append(" pthread dl rt m");
        cmake.append(")\n");

        writeText(new File(finalizer, "CMakeLists.txt"), cmake.toString());
        Set<String> allDeps = new LinkedHashSet<>();
        if (gui) allDeps.add("sdl3");
        if (vcpkgDependencies != null) allDeps.addAll(vcpkgDependencies);

        if (!allDeps.isEmpty()) {
            if (gui) writeText(new File(finalizer, "kr-gui-target.txt"), "sdl3\n");
            StringBuilder vcpkgJson = new StringBuilder();
            vcpkgJson.append("{\n  \"name\": \"kr-unified-project\",\n  \"version-string\": \"1.0.0\",\n  \"dependencies\": [");
            int idx = 0;
            for (String dep : allDeps) {
                if (idx > 0) vcpkgJson.append(", ");
                vcpkgJson.append("\"").append(dep).append("\"");
                idx++;
            }
            vcpkgJson.append("]\n}\n");
            writeText(new File(finalizer, "vcpkg.json"), vcpkgJson.toString());
        }
        log.line("KR Unified Programm wird mit der KR-Ziellaufzeit" + (gui ? " und SDL3" : "") + " gelinkt.");
        return buildCMake(finalizer, project);
    }

    private File buildRust() throws Exception {
        File cargo = new File(toolchain, "usr/bin/cargo");
        File rustc = new File(toolchain, "usr/bin/rustc");
        if (!cargo.canExecute() || !rustc.canExecute())
            throw new IOException("Rust-Compiler fehlt in der Compilerlaufzeit. Bitte die vollständige KR-IDE-APK installieren.");
        boolean gui = isLanguageGuiProject();
        String triple = target == Target.ANDROID ? "aarch64-linux-android"
                : target == Target.WINDOWS ? "x86_64-pc-windows-gnu" : "x86_64-unknown-linux-gnu";
        log.line("Rust " + (gui ? "GUI" : "Konsole") + " → " + triple);
        File targetDir = new File(project.getParentFile(), "rust-target");
        File archive = new File(targetDir, triple + "/release/libkr_language.a");
        Map<String, String> env = new HashMap<>();
        env.put("CARGO_HOME", new File(context.getFilesDir(), "cargo-home").getPath());
        env.put("CARGO_TARGET_DIR", targetDir.getPath());
        env.put("CARGO_NET_OFFLINE", onlineDependencies ? "false" : "true");
        env.put("RUST_BACKTRACE", "1");
        env.put("RUSTC_BOOTSTRAP", "1");
        env.put("CARGO_TARGET_" + triple.toUpperCase(Locale.ROOT).replace('-', '_') + "_LINKER",
                new File(toolchain, "usr/bin/clang").getPath());
        List<String> command = new ArrayList<>();
        Collections.addAll(command, cargo.getPath(), "build", "--release", "--lib", "--target", triple);
        if (target != Target.ANDROID) command.add("-Zbuild-std=std,panic_abort");
        run(command, "Cargo kompiliert das Rust-Projekt", project, env);
        if (!archive.isFile()) throw new IOException("Cargo hat die erwartete Rust-Bibliothek nicht erzeugt: " + archive.getName());
        return linkLanguageArchive(archive, gui, "Rust");
    }

    private File buildGo() throws Exception {
        File go = new File(toolchain, "usr/bin/go");
        if (!go.canExecute()) throw new IOException("Go-Compiler fehlt in der Compilerlaufzeit. Bitte die vollständige KR-IDE-APK installieren.");
        boolean gui = isLanguageGuiProject();
        String goos = target == Target.ANDROID ? "android" : target == Target.WINDOWS ? "windows" : "linux";
        String goarch = target == Target.ANDROID ? "arm64" : "amd64";
        log.line("Go " + (gui ? "GUI" : "Konsole") + " → " + goos + "/" + goarch);
        File languageDir = new File(project.getParentFile(), "go-target");
        if (!languageDir.isDirectory() && !languageDir.mkdirs()) throw new IOException("Go-Ausgabeordner kann nicht erstellt werden.");
        File archive = new File(languageDir, "libkr_language.a");
        Map<String, String> env = new HashMap<>();
        env.put("GOOS", goos); env.put("GOARCH", goarch); env.put("CGO_ENABLED", "1");
        env.put("GOROOT", new File(toolchain, "usr/lib/go").getPath());
        env.put("GOCACHE", new File(context.getCacheDir(), "go-build-cache").getPath());
        env.put("GOMODCACHE", new File(context.getFilesDir(), "go-mod-cache").getPath());
        env.put("GOPROXY", onlineDependencies ? "https://proxy.golang.org,direct" : "off");
        env.put("CC", languageCrossCompiler());
        List<String> command = new ArrayList<>();
        if (target == Target.ANDROID) {
            if (gui) {
                File sdl = ensureAndroidSdlArchive();
                env.put("CGO_LDFLAGS", "-Wl,-u,JNI_OnLoad " + sdl.getPath() + " "
                        + "-L" + new File(toolchain, "usr/lib").getPath()
                        + " -lc++_shared -llog -landroid -ldl -lm -lOpenSLES -lGLESv1_CM -lGLESv2");
            }
            File library = new File(output, "libuser.so");
            Collections.addAll(command, go.getPath(), "build", "-buildvcs=false", "-buildmode=c-shared", "-o", library.getPath(), ".");
            run(command, "Go kompiliert die Android-Bibliothek", project, env);
            File apk = new File(output, "programm-android-arm64.apk");
            RunnerPackager.create(context, library, new File(toolchain, "usr/lib/libc++_shared.so"), project, gui, apk, log);
            if (!library.delete()) log.line("Hinweis: temporäre Go-Android-Bibliothek bleibt in der Ausgabe.");
            return apk;
        }
        Collections.addAll(command, go.getPath(), "build", "-buildvcs=false", "-buildmode=c-archive", "-o", archive.getPath(), ".");
        run(command, "Go kompiliert das Projekt", project, env);
        if (!archive.isFile()) throw new IOException("Go hat die erwartete C-Archivdatei nicht erzeugt.");
        return linkLanguageArchive(archive, gui, "Go");
    }

    private File ensureAndroidSdlArchive() throws Exception {
        File dependencyCache = new File(context.getFilesDir(), "components/cmake/android");
        File existing = findNamedFile(new File(dependencyCache, "sdl3-build"), "libSDL3.a");
        if (existing != null) return existing;
        File source = new File(project.getParentFile(), "sdl-prerequisite");
        File binary = new File(source, "build");
        File config = new File(source, "config");
        if (!source.mkdirs() || !binary.mkdirs() || !config.mkdirs()) throw new IOException("SDL3-Vorbereitungsordner kann nicht erstellt werden.");
        writeText(new File(source, "probe.c"), "int kr_sdl_probe(void) { return 0; }\n");
        writeText(new File(source, "CMakeLists.txt"), "cmake_minimum_required(VERSION 3.20)\nproject(KRSdlPrerequisite LANGUAGES C CXX)\nfind_package(SDL3 CONFIG REQUIRED)\nadd_library(kr_sdl_probe STATIC probe.c)\ntarget_link_libraries(kr_sdl_probe PRIVATE SDL3::SDL3)\n");
        File toolchainFile = new File(config, "kr-toolchain.cmake");
        File includeFile = new File(config, "kr-project.cmake");
        writeText(toolchainFile, cmakeToolchain());
        LinkedHashSet<String> packages = new LinkedHashSet<>(); packages.add("sdl3");
        writeText(includeFile, cmakeProjectInclude(new File(source, "artifacts"), new File(source, "targets.txt"), packages));
        List<String> configure = new ArrayList<>();
        Collections.addAll(configure, new File(toolchain, "usr/bin/cmake").getPath(), "-S", source.getPath(), "-B", binary.getPath(), "-G", "Ninja",
                "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_SYSTEM_VERSION=26", "-DCMAKE_MAKE_PROGRAM=" + new File(toolchain, "usr/bin/ninja").getPath(),
                "-DCMAKE_TOOLCHAIN_FILE=" + toolchainFile.getPath(), "-DCMAKE_PROJECT_TOP_LEVEL_INCLUDES=" + includeFile.getPath(),
                "-DFETCHCONTENT_BASE_DIR=" + dependencyCache.getPath(), "-DFETCHCONTENT_QUIET=OFF");
        run(configure, "SDL3 wird für Go/Android vorbereitet", source);
        run(Arrays.asList(new File(toolchain, "usr/bin/cmake").getPath(), "--build", binary.getPath(), "--parallel", "2"),
                "SDL3 wird für Go/Android gebaut", source);
        File built = findNamedFile(new File(dependencyCache, "sdl3-build"), "libSDL3.a");
        if (built == null) throw new IOException("SDL3-Archiv für Go/Android wurde nicht gefunden.");
        return built;
    }

    private List<File> getAndroidSdlIncludeDirs(File dependencyCache) {
        List<File> list = new ArrayList<>();
        File srcInclude = new File(dependencyCache, "sdl3-src/include");
        if (srcInclude.isDirectory()) list.add(srcInclude);
        File build = new File(dependencyCache, "sdl3-build");
        if (build.isDirectory()) {
            collectSdl3Dirs(build, list);
        }
        return list;
    }

    private static void collectSdl3Dirs(File dir, List<File> list) {
        if (!dir.isDirectory()) return;
        if (new File(dir, "SDL3").isDirectory() || new File(dir, "SDL_build_config.h").isFile()) {
            list.add(dir);
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) collectSdl3Dirs(child, list);
            }
        }
    }

    private static File findNamedFile(File root, String name) {
        if (!root.exists()) return null;
        if (root.isFile()) return root.getName().equals(name) ? root : null;
        File[] children = root.listFiles();
        if (children != null) for (File child : children) { File found = findNamedFile(child, name); if (found != null) return found; }
        return null;
    }

    private boolean isLanguageGuiProject() {
        return new File(project, "kr-gui-target.txt").isFile();
    }

    private String languageCrossCompiler() {
        String clang = new File(toolchain, "usr/bin/clang").getPath();
        if (target == Target.ANDROID) return clang + " --target=aarch64-linux-android26";
        if (target == Target.WINDOWS) {
            File win = new File(toolchain, "targets/windows");
            return clang + " --target=x86_64-w64-windows-gnu --sysroot=" + win.getPath() + " -fuse-ld=lld";
        }
        File linux = new File(toolchain, "targets/linux");
        return clang + " --target=x86_64-linux-gnu --sysroot=" + new File(linux, "sysroot").getPath()
                + " --gcc-toolchain=" + new File(linux, "gcc").getPath()
                + " -fuse-ld=lld -Wno-unused-command-line-argument";
    }

    private File linkLanguageArchive(File archive, boolean gui, String language) throws Exception {
        File finalizer = new File(project.getParentFile(), "language-finalizer");
        if (!finalizer.isDirectory() && !finalizer.mkdirs()) throw new IOException("Sprach-Linkordner kann nicht erstellt werden.");
        String entry = gui && target == Target.ANDROID ? "SDL_main" : "main";
        String wrapper = "#ifdef __cplusplus\nextern \"C\" {\n#endif\n"
                + "int KrLanguageMain(void);\n#ifdef __cplusplus\n}\n#endif\n"
                + "int " + entry + "(int argc, char **argv) { (void)argc; (void)argv; return KrLanguageMain(); }\n";
        writeText(new File(finalizer, "kr_entry.c"), wrapper);
        String a = cmakeEscape(archive.getPath());
        StringBuilder cmake = new StringBuilder();
        cmake.append("cmake_minimum_required(VERSION 3.20)\nproject(KRLanguageFinalizer LANGUAGES C CXX)\n");
        cmake.append("add_library(kr_language STATIC IMPORTED GLOBAL)\nset_target_properties(kr_language PROPERTIES IMPORTED_LOCATION \"")
                .append(a).append("\")\n");
        if (gui) cmake.append("find_package(SDL3 CONFIG REQUIRED)\n");
        cmake.append("add_executable(kr_program kr_entry.c)\n");
        cmake.append("set_target_properties(kr_program PROPERTIES LINKER_LANGUAGE CXX)\n");
        cmake.append("target_link_libraries(kr_program PRIVATE kr_language");
        if (gui) cmake.append(" SDL3::SDL3");
        if (target == Target.WINDOWS) cmake.append(" ws2_32 winmm advapi32 userenv shell32 ole32 bcrypt ntdll");
        else if (target == Target.ANDROID) cmake.append(" log android dl m");
        else cmake.append(" pthread dl rt m");
        cmake.append(")\n");
        writeText(new File(finalizer, "CMakeLists.txt"), cmake.toString());
        if (gui) {
            writeText(new File(finalizer, "kr-gui-target.txt"), "sdl3\n");
            writeText(new File(finalizer, "vcpkg.json"), "{\n  \"name\": \"kr-language-gui\",\n  \"version-string\": \"1.0.0\",\n  \"dependencies\": [\"sdl3\"]\n}\n");
        }
        log.line(language + "-Bibliothek wird mit der KR-Ziellaufzeit" + (gui ? " und SDL3" : "") + " verbunden.");
        return buildCMake(finalizer, project);
    }

    private File buildDesktop(List<File> sources) throws Exception {
        boolean isShared = !hasMainEntry(sources);
        if (isShared) {
            log.line("Kein main()-Einstiegspunkt erkannt; Projekt wird als dynamische Bibliothek (Shared Library) kompiliert.");
        }
        File artifact = new File(output, target == Target.WINDOWS ? "programm.exe" : "programm");
        String baseLibName = resolveDirectLibraryName(sources);
        File primaryOut = isShared
                ? new File(output, target == Target.WINDOWS ? (baseLibName + ".dll") : ("lib" + baseLibName + ".so"))
                : artifact;

        List<String> command = compiler();
        if (target == Target.WINDOWS) {
            File win = new File(toolchain, "targets/windows");
            command.add("--target=x86_64-w64-windows-gnu");
            command.add("--sysroot=" + win);
            command.add("-fuse-ld=lld");
            command.add("-static");
            if (isShared) {
                command.add("-shared");
                File implib = new File(output, "lib" + baseLibName + ".dll.a");
                command.add("-Wl,--out-implib," + implib.getPath());
            }
            command.add("-stdlib=libc++");
            command.add("-rtlib=compiler-rt");
            command.add("-unwindlib=libunwind");
            command.add("-nostdinc++");
            command.add("-isystem"); command.add(new File(win, "x86_64-w64-mingw32/include/c++/v1").getPath());
            command.add("-isystem"); command.add(new File(win, "include/c++/v1").getPath());
        } else {
            File linux = new File(toolchain, "targets/linux");
            command.add("--target=x86_64-linux-gnu");
            command.add("--sysroot=" + new File(linux, "sysroot"));
            command.add("--gcc-toolchain=" + new File(linux, "gcc"));
            command.add("-fuse-ld=lld");
            if (isShared) {
                command.add("-shared");
                command.add("-fPIC");
            }
            command.add("-stdlib=libstdc++");
            command.add("-rtlib=libgcc"); command.add("-static-libstdc++"); command.add("-static-libgcc");
        }
        command.add("-O2");
        addSources(command, sources);
        command.add("-o"); command.add(primaryOut.getPath());
        run(command, isShared ? "Shared Library wird kompiliert" : "Desktop-Programm wird kompiliert", project);
        primaryOut.setExecutable(true, false);
        if (isShared && !primaryOut.getCanonicalPath().equals(artifact.getCanonicalPath())) {
            copy(primaryOut, artifact);
            artifact.setExecutable(true, false);
        }
        return artifact;
    }

    private File buildAndroid(List<File> sources) throws Exception {
        boolean isShared = !hasMainEntry(sources);
        if (isShared) {
            log.line("Kein main()-Einstiegspunkt erkannt; Android-Shared-Library wird kompiliert.");
        }
        File library = new File(output, "libuser.so");
        List<String> command = compiler();
        command.add("--target=aarch64-linux-android26");
        command.add("-O2"); command.add("-fPIC"); command.add("-shared");
        command.add("-L" + new File(toolchain, "usr/lib").getPath());
        command.add("-Wl,--export-dynamic");
        command.add("-Wl,--no-undefined");
        addSources(command, sources);
        command.add("-o"); command.add(library.getPath());
        run(command, "Android-Bibliothek wird kompiliert", project);
        if (!isShared) verifyAndroidRunnerEntrypoint(library);
        if (isShared) {
            String baseLibName = resolveDirectLibraryName(sources);
            File namedSo = new File(output, "lib" + baseLibName + ".so");
            copy(library, namedSo);
        }
        File apk = new File(output, "programm-android-arm64.apk");
        File cppRuntime = new File(toolchain, "usr/lib/libc++_shared.so");
        if (!cppRuntime.isFile()) throw new IOException("Eingebettete Android-C++-Laufzeit fehlt.");
        RunnerPackager.create(context, library, cppRuntime, project, false, apk, log);
        if (!library.delete()) log.line("Hinweis: temporäre Android-Bibliothek bleibt in der Ausgabe.");
        return apk;
    }

    private String resolveDirectLibraryName(List<File> sources) {
        for (File f : sources) {
            if (f.getName().equalsIgnoreCase("CC_OpenCL.c") || f.getName().equalsIgnoreCase("CC_OpenCL.cpp")) {
                return "CC_OpenCl";
            }
        }
        if (localProjectDir != null) {
            return localProjectDir.getName().replaceAll("-[0-9]+$", "");
        }
        return "kr_library";
    }

    private void verifyAndroidRunnerEntrypoint(File library) throws Exception {
        File nm = new File(toolchain, "usr/bin/llvm-nm");
        if (!nm.canExecute()) throw new IOException("Symbolprüfung nicht möglich: llvm-nm fehlt in der Compilerlaufzeit.");
        ProcessBuilder builder = new ProcessBuilder(nm.getPath(), "-D", "--defined-only", library.getPath());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        boolean found = false;
        StringBuilder outputText = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputText.append(line).append('\n');
                String trimmed = line.trim();
                if (trimmed.endsWith(" main") || trimmed.endsWith(" SDL_main") || trimmed.endsWith(" KrLanguageMain")) found = true;
            }
        }
        int exit = process.waitFor();
        if (exit != 0) throw new IOException("Symbolprüfung der Android-Bibliothek fehlgeschlagen: " + outputText.toString().trim());
        if (!found) throw new IOException("Android-Build enthält keinen exportierten Programmeinstieg "
                + "(main, SDL_main oder KrLanguageMain). Die Runner-APK wurde nicht erzeugt.");
        log.line("Android-Programmeinstieg wurde vor dem Packaging verifiziert.");
    }

    private List<String> compiler() {
        return new ArrayList<>(Collections.singletonList(new File(toolchain, "usr/bin/clang++").getPath()));
    }

    private void addSources(List<String> command, List<File> sources) throws Exception {
        command.add("-Wno-error");
        Set<String> includeDirs = new LinkedHashSet<>();
        includeDirs.add(project.getPath());
        collectHeaderDirectories(project, includeDirs);
        for (File s : sources) {
            if (s.getParentFile() != null) includeDirs.add(s.getParentFile().getPath());
        }
        for (String inc : includeDirs) {
            command.add("-I");
            command.add(inc);
        }

        boolean needsOpenCl = false;
        boolean needsUnicodeEntry = false;
        List<File> cFiles = new ArrayList<>();
        List<File> cppFiles = new ArrayList<>();
        for (File file : sources) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            String content = "";
            try { content = readTextLimited(file, 1048576); } catch (Exception ignored) {}
            if (content.contains("CL/cl.h") || content.contains("CL/opencl.h") || content.contains("clGetPlatformIDs")) {
                needsOpenCl = true;
            }
            if (Pattern.compile("\\b(wWinMain|wmain)\\s*\\(").matcher(content).find()) {
                needsUnicodeEntry = true;
            }
            if (lower.endsWith(".c") && !isCppInCFile(file, content)) {
                cFiles.add(file);
            } else {
                cppFiles.add(file);
            }
        }

        if (needsOpenCl) {
            command.add("-DCL_TARGET_OPENCL_VERSION=300");
            command.add("-DCL_USE_DEPRECATED_OPENCL_1_2_APIS");
        }

        if (cppFiles.isEmpty()) {
            command.add("-std=gnu17");
            command.add("-x"); command.add("c");
            for (File f : cFiles) command.add(f.getPath());
            command.add("-x"); command.add("none");
        } else if (cFiles.isEmpty()) {
            command.add("-std=c++20");
            command.add("-x"); command.add("c++");
            for (File f : cppFiles) command.add(f.getPath());
            command.add("-x"); command.add("none");
        } else {
            // Mixed C and C++ in direct build: compile C files to .o objects with -std=gnu17 first
            File objDir = new File(project.getParentFile(), "direct-c-objs");
            delete(objDir);
            objDir.mkdirs();
            int idx = 0;
            for (File cf : cFiles) {
                File obj = new File(objDir, (idx++) + "_" + cf.getName() + ".o");
                List<String> cCmd = new ArrayList<>();
                cCmd.add(new File(toolchain, "usr/bin/clang").getPath());
                for (int i = 1; i < command.size(); i++) {
                    String arg = command.get(i);
                    if (arg.equals("-shared") || arg.startsWith("-Wl,") || arg.startsWith("-stdlib=")
                            || arg.startsWith("-rtlib=") || arg.startsWith("-unwindlib=")
                            || arg.equals("-static-libstdc++") || arg.equals("-static-libgcc") || arg.equals("-static")) {
                        continue;
                    }
                    cCmd.add(arg);
                }
                Collections.addAll(cCmd, "-std=gnu17", "-fPIC", "-x", "c", "-c", cf.getPath(), "-o", obj.getPath());
                run(cCmd, "C-Quelldatei wird kompiliert (" + cf.getName() + ")", project);
                command.add(obj.getPath());
            }
            command.add("-std=c++20");
            command.add("-x"); command.add("c++");
            for (File f : cppFiles) command.add(f.getPath());
            command.add("-x"); command.add("none");
        }

        if (needsOpenCl) {
            command.add("-lOpenCL");
        }
        if (target == Target.WINDOWS) {
            if (needsUnicodeEntry) command.add("-municode");
            Collections.addAll(command, "-lws2_32", "-luser32", "-lgdi32", "-lshell32", "-lole32", "-ladvapi32", "-lcomdlg32", "-lcomctl32", "-luuid", "-lwindowscodecs");
        } else if (target == Target.LINUX) {
            Collections.addAll(command, "-lpthread", "-ldl", "-lm");
        } else if (target == Target.ANDROID) {
            Collections.addAll(command, "-lc++_shared", "-llog", "-landroid", "-ldl", "-lm");
        }
    }

    private static boolean isCppInCFile(File file, String content) {
        if (file.getName().equalsIgnoreCase("CC_OpenCL.c")) return true;
        return content.contains("R\"CLC(") || content.contains("R\"(") || content.contains("extern \"C\"")
                || content.contains("namespace ") || content.contains("std::") || content.contains("#include <iostream>")
                || content.contains("#include <vector>") || content.contains("#include <string>");
    }

    private static boolean hasMainEntry(List<File> sources) {
        Pattern mainPat = Pattern.compile("\\b(main|wmain|WinMain|wWinMain|SDL_main)\\s*\\(");
        for (File f : sources) {
            try {
                String text = readTextLimited(f, 131072);
                if (mainPat.matcher(text).find()) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private List<File> filterDirectSources(File root, List<File> rawSources, Target activeTarget) {
        List<File> filtered = new ArrayList<>();
        String rootPath = root.getAbsolutePath().replace('\\', '/');
        for (File f : rawSources) {
            String rel = f.getAbsolutePath().replace('\\', '/').substring(rootPath.length()).toLowerCase(Locale.ROOT);
            if (rel.contains("/build/") || rel.contains("/build_vs/") || rel.contains("/out/")
                    || rel.contains("/cmakefiles/") || rel.contains("/07_project_snapshot/")
                    || rel.contains("/recovery_legacy/")) {
                continue;
            }
            filtered.add(f);
        }
        if (filtered.isEmpty()) filtered.addAll(rawSources);

        if (activeTarget != Target.WINDOWS) {
            List<File> nonWin = new ArrayList<>();
            for (File f : filtered) {
                if (!fileRequiresUnconditionalWin32(f)) nonWin.add(f);
            }
            if (!nonWin.isEmpty()) filtered = nonWin;
        }

        Pattern mainPat = Pattern.compile("\\b(main|wmain|WinMain|wWinMain|SDL_main)\\s*\\(");
        List<File> mainFiles = new ArrayList<>();
        List<File> libFiles = new ArrayList<>();
        for (File f : filtered) {
            boolean hasMain = false;
            try {
                String text = readTextLimited(f, 131072);
                hasMain = mainPat.matcher(text).find();
            } catch (Exception ignored) {}
            if (hasMain) mainFiles.add(f);
            else libFiles.add(f);
        }

        if (mainFiles.size() > 1) {
            mainFiles.sort((a, b) -> {
                String na = a.getName().toLowerCase(Locale.ROOT);
                String nb = b.getName().toLowerCase(Locale.ROOT);
                boolean ma = na.equals("main.cpp") || na.equals("main.c");
                boolean mb = nb.equals("main.cpp") || nb.equals("main.c");
                if (ma != mb) return ma ? -1 : 1;
                boolean ta = na.contains("test") || na.contains("probe") || na.contains("smoke");
                boolean tb = nb.contains("test") || nb.contains("probe") || nb.contains("smoke");
                if (ta != tb) return ta ? 1 : -1;
                return a.getAbsolutePath().compareTo(b.getAbsolutePath());
            });
            File primaryMain = mainFiles.get(0);
            log.line("Mehrere main()-Dateien erkannt (" + mainFiles.size() + "); Hauptdatei ausgewählt: " + primaryMain.getName());
            List<File> result = new ArrayList<>();
            result.add(primaryMain);
            result.addAll(libFiles);
            return result;
        }
        return filtered;
    }

    private static void collectHeaderDirectories(File dir, Set<String> includeDirs) {
        if (!dir.isDirectory()) return;
        String name = dir.getName();
        if (name.equals("KR_BUILD") || name.equals(".git") || name.equals("build") || name.equals("build_vs")
                || name.equals("out") || name.equals("node_modules") || name.equals(".gradle")) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        boolean hasHeader = false;
        for (File child : children) {
            if (child.isDirectory()) {
                collectHeaderDirectories(child, includeDirs);
            } else {
                String lower = child.getName().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".h") || lower.endsWith(".hpp") || lower.endsWith(".hxx") || lower.endsWith(".inc")) {
                    hasHeader = true;
                }
            }
        }
        if (hasHeader) {
            includeDirs.add(dir.getPath());
            if (dir.getParentFile() != null) {
                includeDirs.add(dir.getParentFile().getPath());
            }
        }
    }

    private void run(List<String> command, String phase, File directory) throws Exception {
        run(command, phase, directory, Collections.emptyMap());
    }

    private void run(List<String> command, String phase, File directory, Map<String, String> extraEnvironment) throws Exception {
        log.line(phase + " …");
        appendText(buildLog, "\n=== " + phase + " ===\n" + String.join(" ", command) + "\n");
        ProcessBuilder pb = new ProcessBuilder(command); pb.directory(directory); pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        env.put("HOME", context.getFilesDir().getPath()); env.put("TMPDIR", context.getCacheDir().getPath());
        env.put("PREFIX", new File(toolchain, "usr").getPath());
        // Android guarantees this shell. Do not point SHELL at an extracted
        // Termux path because stale links from an older runtime can make the
        // process fail before CMake even starts.
        env.put("SHELL", "/system/bin/sh");
        env.put("TERM", "dumb");
        env.put("ANDROID_DATA", "/data");
        env.put("ANDROID_ROOT", "/system");
        env.put("LD_LIBRARY_PATH", new File(toolchain, "usr/lib").getPath());
        env.put("PATH", new File(toolchain, "usr/bin").getPath() + ":/system/bin:/system/xbin");
        env.put("PKG_CONFIG", new File(toolchain, "usr/bin/pkg-config").getPath());
        File certificates = new File(toolchain, "usr/etc/tls/cert.pem");
        env.put("SSL_CERT_FILE", certificates.getPath());
        env.put("CURL_CA_BUNDLE", certificates.getPath());
        env.put("GIT_SSL_CAINFO", certificates.getPath());
        env.putAll(extraEnvironment);
        if (cancelled) throw new IOException("Build abgebrochen.");
        Process process = pb.start();
        activeProcess = process;
        try {
            StringBuilder full = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while ((line = reader.readLine()) != null) { full.append(line).append('\n'); log.line(line); }
            }
            int result = process.waitFor();
            appendText(buildLog, full.toString());
            if (cancelled) throw new IOException("Build abgebrochen.");
            if (result != 0) throw new IOException(phase + " fehlgeschlagen (Code " + result + "). Siehe KR_BUILD/build.log.");
        } finally {
            activeProcess = null;
        }
    }

    static void copyDirectoryContent(File srcDir, File destDir) throws IOException {
        if (!destDir.exists()) destDir.mkdirs();
        File[] files = srcDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (name.equals("KR_BUILD") || name.equals(".git") || name.equals(".gradle") || name.equals(".idea")) continue;
            File target = new File(destDir, name);
            if (f.isDirectory()) {
                copyDirectoryContent(f, target);
            } else {
                copy(f, target);
            }
        }
    }

    private static List<File> sources(File root) {
        List<File> result = new ArrayList<>(); collect(root, result); return result;
    }
    private static List<File> goSources(File root) {
        List<File> result = new ArrayList<>(); collectGo(root, result); return result;
    }
    private static void collectGo(File file, List<File> result) {
        if (file.isDirectory()) {
            String name = file.getName();
            if (name.equals("KR_BUILD") || name.equals(".git") || name.equals("vendor")) return;
            File[] children = file.listFiles(); if (children != null) for (File child : children) collectGo(child, result);
            return;
        }
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".go")) result.add(file);
    }
    private static void collect(File file, List<File> result) {
        if (file.isDirectory()) {
            String name = file.getName();
            if (name.equals("KR_BUILD") || name.equals(".git") || name.equals("build") || name.equals("build_vs")
                    || name.equals("out") || name.equals("node_modules") || name.equals(".gradle") || name.equals(".idea")) return;
            File[] children = file.listFiles(); if (children != null) for (File child : children) collect(child, result);
            return;
        }
        String n = file.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".c") || n.endsWith(".cc") || n.endsWith(".cpp") || n.endsWith(".cxx")) result.add(file);
    }
    private static void collectFiles(File file, List<File> result) {
        if (!file.exists()) return;
        if (file.isFile()) { result.add(file); return; }
        File[] children = file.listFiles(); if (children != null) for (File child : children) collectFiles(child, result);
    }
    private static List<String> readLines(File file) throws IOException {
        List<String> result = new ArrayList<>();
        if (!file.isFile()) return result;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) { line = line.trim(); if (!line.isEmpty()) result.add(line); }
        }
        return result;
    }
    private static void copy(File source, File destination) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[128 * 1024]; int n; while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        }
    }
    private static String cmakeQuote(File file) { return "\"" + cmakeEscape(file.getPath()) + "\""; }
    private static String cmakeEscape(String value) { return value.replace("\\", "/").replace("\"", "\\\"").replace(";", "\\;"); }
    private static void writeText(File file, String text) throws IOException {
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) { out.write(text); }
    }
    private static String readText(File file) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[32 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static void appendText(File file, String text) throws IOException {
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) { out.write(text); }
    }
    private static String json(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
    private static void delete(File file) throws IOException {
        if (!file.exists()) return; File[] children = file.listFiles(); if (children != null) for (File child : children) delete(child);
        if (!file.delete()) throw new IOException("Arbeitsordner kann nicht bereinigt werden: " + file);
    }
}
