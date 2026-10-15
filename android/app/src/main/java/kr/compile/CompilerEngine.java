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
            File cmakeProject = new File(project, "CMakeLists.txt");
            File artifact;
            List<File> krFiles = KrPipeline.findKrFiles(project);
            if (!krFiles.isEmpty()) {
                log.line("KR Unified Language Projekt erkannt (" + krFiles.size() + " .kr-Datei(en)).");
                artifact = buildKrUnified(krFiles);
            } else if (new File(project, "Cargo.toml").isFile()) {
                log.line("Rust-Projekt erkannt; Cargo und rustc werden verwendet.");
                artifact = buildRust();
            } else if (new File(project, "go.mod").isFile() || !goSources(project).isEmpty()) {
                log.line("Go-Projekt erkannt; go build wird verwendet.");
                artifact = buildGo();
            } else if (cmakeProject.isFile()) {
                log.line("CMakeLists.txt erkannt; das Projekt wird mit CMake ausgewertet.");
                artifact = buildCMake();
            } else {
                List<File> sources = sources(project);
                if (sources.isEmpty()) throw new IOException("Keine CMakeLists.txt und keine .c-, .cc-, .cpp- oder .cxx-Datei gefunden.");
                log.line(sources.size() + " Quelldatei(en) gefunden; direkter Einzelprogramm-Build wird verwendet.");
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
        File cmake = new File(toolchain, "usr/bin/cmake");
        File ninja = new File(toolchain, "usr/bin/ninja");
        if (!cmake.canExecute() || !ninja.canExecute())
            throw new IOException("CMake oder Ninja fehlt in der Compilerlaufzeit. Bitte die vollständige Compiler-APK installieren.");

        File cmakeRoot = new File(project.getParentFile(), "cmake");
        File binary = new File(cmakeRoot, "build");
        File artifacts = new File(cmakeRoot, "artifacts");
        File config = new File(cmakeRoot, "config");
        if (!binary.mkdirs() || !artifacts.mkdirs() || !config.mkdirs())
            throw new IOException("CMake-Arbeitsordner kann nicht angelegt werden.");

        File toolchainFile = new File(config, "kr-toolchain.cmake");
        File projectInclude = new File(config, "kr-project.cmake");
        File targetList = new File(binary, "kr-executable-targets.txt");
        writeText(toolchainFile, cmakeToolchain());
        Set<String> packages = findCMakePackages(sourceProject);
        Set<String> manifestPackages = VcpkgManager.readDependencies(sourceProject);
        boolean guiProgram = VcpkgManager.isGuiProject(sourceProject, manifestPackages);
        if (guiProgram) {
            log.line("GUI-Projekt erkannt: SDL3-/Dear-ImGui-Fenster wird für "
                    + (target == Target.ANDROID ? "Android ARM64" : target == Target.WINDOWS ? "Windows x64" : "Linux x64")
                    + " gebaut.");
            if (target == Target.ANDROID) {
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
        writeText(projectInclude, cmakeProjectInclude(artifacts, targetList, packages));

        File dependencyCache = new File(context.getFilesDir(), "components/cmake/" + target.name().toLowerCase(Locale.ROOT));
        if (!dependencyCache.isDirectory() && !dependencyCache.mkdirs())
            throw new IOException("Abhängigkeitscache kann nicht angelegt werden.");
        if (target == Target.ANDROID && containsPackage(packages, "sdl3")) {
            patchCachedAndroidSdl(dependencyCache);
        }

        String systemVersion = target == Target.ANDROID ? "26" : (target == Target.WINDOWS ? "10.0" : "1");
        List<String> configure = new ArrayList<>();
        Collections.addAll(configure, cmake.getPath(), "-S", sourceProject.getPath(), "-B", binary.getPath(), "-G", "Ninja",
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_SYSTEM_VERSION=" + systemVersion,
                "-DCMAKE_MAKE_PROGRAM=" + ninja.getPath(),
                "-DCMAKE_TOOLCHAIN_FILE=" + toolchainFile.getPath(),
                "-DCMAKE_PROJECT_TOP_LEVEL_INCLUDES=" + projectInclude.getPath(),
                "-DFETCHCONTENT_BASE_DIR=" + dependencyCache.getPath(),
                "-DFETCHCONTENT_QUIET=OFF");
        if (!onlineDependencies) configure.add("-DFETCHCONTENT_FULLY_DISCONNECTED=ON");
        else log.line("Online-Abhängigkeiten sind erlaubt; CMake FetchContent und Git dürfen benötigte Quellen laden.");
        run(configure, "CMake konfiguriert das Projekt", sourceProject);

        List<String> build = new ArrayList<>();
        int buildJobs = target == Target.ANDROID
                ? Math.min(2, Math.max(1, Runtime.getRuntime().availableProcessors()))
                : Math.max(1, Runtime.getRuntime().availableProcessors());
        if (target == Target.ANDROID) log.line("Android-GUI-Build: " + buildJobs + " parallele Compilerprozesse für stabilen Speicherverbrauch.");
        Collections.addAll(build, cmake.getPath(), "--build", binary.getPath(), "--config", "Release", "--parallel",
                Integer.toString(buildJobs));
        run(build, "CMake baut das Projekt", sourceProject);

        List<String> targets = readLines(targetList);
        if (targets.isEmpty()) throw new IOException("CMake hat kein ausführbares Ziel aus add_executable erzeugt.");
        String selected = selectTarget(targets);
        File built = findCMakeArtifact(artifacts, selected);
        if (built == null) throw new IOException("Das CMake-Ziel " + selected + " wurde gebaut, aber seine Ausgabedatei wurde nicht gefunden.");

        if (target == Target.ANDROID) {
            File library = new File(output, "libuser.so");
            copy(built, library);
            File apk = new File(output, "programm-android-arm64.apk");
            File cppRuntime = new File(toolchain, "usr/lib/libc++_shared.so");
            RunnerPackager.create(context, library, cppRuntime, runtimeProject, guiProgram, apk, log);
            if (!library.delete()) log.line("Hinweis: temporäre Android-Bibliothek bleibt in der Ausgabe.");
            return apk;
        }

        File artifact = new File(output, target == Target.WINDOWS ? "programm.exe" : "programm");
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
        File clang = new File(toolchain, "usr/bin/clang");
        File clangxx = new File(toolchain, "usr/bin/clang++");
        File ar = new File(toolchain, "usr/bin/llvm-ar");
        File ranlib = new File(toolchain, "usr/bin/llvm-ranlib");
        File strip = new File(toolchain, "usr/bin/llvm-strip");
        StringBuilder s = new StringBuilder();
        s.append("set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)\n");
        s.append("set(CMAKE_C_COMPILER ").append(cmakeQuote(clang)).append(")\n");
        s.append("set(CMAKE_CXX_COMPILER ").append(cmakeQuote(clangxx)).append(")\n");
        s.append("set(CMAKE_AR ").append(cmakeQuote(ar)).append(")\n");
        s.append("set(CMAKE_RANLIB ").append(cmakeQuote(ranlib)).append(")\n");
        s.append("set(CMAKE_STRIP ").append(cmakeQuote(strip)).append(")\n");
        if (target == Target.ANDROID) {
            s.append("set(CMAKE_SYSTEM_NAME Linux)\nset(CMAKE_SYSTEM_PROCESSOR aarch64)\n");
            // KR uses the standalone Android Clang/sysroot shipped in the app.
            // Expose Android explicitly so dependencies select their Android
            // backend without invoking CMake's desktop-NDK discovery module.
            s.append("set(ANDROID TRUE CACHE BOOL \"KR Android cross target\" FORCE)\n");
            s.append("set(ANDROID_ABI arm64-v8a CACHE STRING \"\" FORCE)\n");
            s.append("set(CMAKE_ANDROID_ARCH_ABI arm64-v8a CACHE STRING \"\" FORCE)\n");
            s.append("set(CMAKE_SYSTEM_VERSION 26)\n");
            s.append("set(CMAKE_C_COMPILER_TARGET aarch64-linux-android26)\n");
            s.append("set(CMAKE_CXX_COMPILER_TARGET aarch64-linux-android26)\n");
            s.append("set(CMAKE_POSITION_INDEPENDENT_CODE ON)\n");
            s.append("set(CMAKE_SHARED_LINKER_FLAGS_INIT \"-fuse-ld=lld -L")
                    .append(cmakeEscape(new File(toolchain, "usr/lib").getPath())).append(" -Wl,--export-dynamic -Wl,-z,defs\")\n");
        } else if (target == Target.WINDOWS) {
            File win = new File(toolchain, "targets/windows");
            String include1 = new File(win, "x86_64-w64-mingw32/include/c++/v1").getPath();
            String include2 = new File(win, "include/c++/v1").getPath();
            s.append("set(CMAKE_SYSTEM_NAME Windows)\nset(CMAKE_SYSTEM_PROCESSOR x86_64)\n");
            s.append("set(CMAKE_SYSTEM_VERSION 10.0)\n");
            s.append("set(CMAKE_C_COMPILER_TARGET x86_64-w64-windows-gnu)\n");
            s.append("set(CMAKE_CXX_COMPILER_TARGET x86_64-w64-windows-gnu)\n");
            s.append("set(CMAKE_SYSROOT ").append(cmakeQuote(win)).append(")\n");
            s.append("set(CMAKE_FIND_ROOT_PATH ").append(cmakeQuote(win)).append(")\n");
            // The libc++ headers are selected explicitly. Passing -stdlib to
            // a compile-only MinGW invocation is reported as unused and breaks
            // projects that promote warnings to errors.
            s.append("set(CMAKE_CXX_FLAGS_INIT \"-nostdinc++ -isystem ")
                    .append(cmakeEscape(include1)).append(" -isystem ").append(cmakeEscape(include2)).append("\")\n");
            s.append("set(CMAKE_EXE_LINKER_FLAGS_INIT \"-fuse-ld=lld -static -stdlib=libc++ -rtlib=compiler-rt -unwindlib=libunwind\")\n");
        } else {
            File linux = new File(toolchain, "targets/linux");
            File sysroot = new File(linux, "sysroot");
            File gcc = new File(linux, "gcc");
            s.append("set(CMAKE_SYSTEM_NAME Linux)\nset(CMAKE_SYSTEM_PROCESSOR x86_64)\n");
            s.append("set(CMAKE_SYSTEM_VERSION 1)\n");
            s.append("set(CMAKE_C_COMPILER_TARGET x86_64-linux-gnu)\n");
            s.append("set(CMAKE_CXX_COMPILER_TARGET x86_64-linux-gnu)\n");
            s.append("set(CMAKE_SYSROOT ").append(cmakeQuote(sysroot)).append(")\n");
            s.append("set(CMAKE_FIND_ROOT_PATH ").append(cmakeQuote(sysroot)).append(' ').append(cmakeQuote(gcc)).append(")\n");
            s.append("set(CMAKE_C_FLAGS_INIT \"--gcc-toolchain=").append(cmakeEscape(gcc.getPath())).append("\")\n");
            s.append("set(CMAKE_CXX_FLAGS_INIT \"--gcc-toolchain=").append(cmakeEscape(gcc.getPath())).append(" -stdlib=libstdc++\")\n");
            s.append("set(CMAKE_EXE_LINKER_FLAGS_INIT \"-fuse-ld=lld --gcc-toolchain=").append(cmakeEscape(gcc.getPath()))
                    .append(" -rtlib=libgcc -static-libstdc++ -static-libgcc\")\n");
        }
        s.append("set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)\n");
        s.append("set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)\n");
        s.append("set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)\n");
        s.append("set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)\n");
        return s.toString();
    }

    private String cmakeProjectInclude(File artifacts, File targetList, Set<String> packages) {
        String out = cmakeEscape(artifacts.getPath());
        String list = cmakeEscape(targetList.getPath());
        StringBuilder s = new StringBuilder();
        if (onlineDependencies) appendDownloadableDependencies(s, packages);
        s.append("set(KR_EXECUTABLE_OUTPUT \"").append(out).append("\")\n");
        s.append("function(add_executable target)\n");
        s.append("  set(_kr_args ${ARGN})\n");
        s.append("  list(FIND _kr_args \"IMPORTED\" _kr_imported)\n  list(FIND _kr_args \"ALIAS\" _kr_alias)\n");
        s.append("  if(NOT _kr_imported EQUAL -1 OR NOT _kr_alias EQUAL -1)\n    _add_executable(${target} ${ARGN})\n    return()\n  endif()\n");
        s.append("  file(RELATIVE_PATH _kr_relative \"${CMAKE_SOURCE_DIR}\" \"${CMAKE_CURRENT_SOURCE_DIR}\")\n");
        s.append("  if(_kr_relative MATCHES \"^\\\\.\\\\.\")\n    _add_executable(${target} ${ARGN})\n    return()\n  endif()\n");
        if (target == Target.ANDROID) {
            s.append("  list(REMOVE_ITEM _kr_args WIN32 MACOSX_BUNDLE EXCLUDE_FROM_ALL)\n");
            s.append("  add_library(${target} SHARED ${_kr_args})\n");
            s.append("  set_target_properties(${target} PROPERTIES PREFIX \"lib\" LIBRARY_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\")\n");
            s.append("  target_link_options(${target} PRIVATE -Wl,--export-dynamic -Wl,-z,defs)\n");
        } else {
            s.append("  _add_executable(${target} ${ARGN})\n");
            s.append("  set_target_properties(${target} PROPERTIES RUNTIME_OUTPUT_DIRECTORY \"${KR_EXECUTABLE_OUTPUT}\")\n");
        }
        s.append("  set_property(GLOBAL APPEND PROPERTY KR_EXECUTABLE_TARGETS \"${target}\")\nendfunction()\n");
        s.append("function(kr_write_executable_targets)\n  get_property(_targets GLOBAL PROPERTY KR_EXECUTABLE_TARGETS)\n");
        s.append("  if(_targets)\n    list(REMOVE_DUPLICATES _targets)\n    string(REPLACE \";\" \"\\n\" _text \"${_targets}\")\n");
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

    private String selectTarget(List<String> targets) throws IOException {
        File selection = new File(project, "kr-android-target.txt");
        if (selection.isFile()) {
            List<String> lines = readLines(selection);
            if (!lines.isEmpty() && targets.contains(lines.get(0))) return lines.get(0);
            throw new IOException("kr-android-target.txt nennt kein vorhandenes CMake-Ziel. Vorhanden: " + String.join(", ", targets));
        }
        if (targets.size() == 1) return targets.get(0);
        throw new IOException("Das CMake-Projekt erzeugt mehrere Programme: " + String.join(", ", targets) +
                ". Gewünschten Zielnamen in kr-android-target.txt eintragen.");
    }

    private File findCMakeArtifact(File artifacts, String selected) {
        String expected = target == Target.ANDROID ? "lib" + selected + ".so" : selected + (target == Target.WINDOWS ? ".exe" : "");
        File exact = new File(artifacts, expected);
        if (exact.isFile()) return exact;
        List<File> files = new ArrayList<>();
        collectFiles(artifacts, files);
        for (File file : files) if (file.getName().equals(expected)) return file;
        if (files.size() == 1) return files.get(0);
        return null;
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
                return target == Target.ANDROID ? buildAndroid(Collections.singletonList(pipeline.codeResult.cppSource))
                        : buildDesktop(Collections.singletonList(pipeline.codeResult.cppSource));
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

        return linkKrUnified(pipeline.codeResult.cppSource, rustArchive, goArchive, gui);
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

    private File linkKrUnified(File cppSource, File rustArchive, File goArchive, boolean gui) throws Exception {
        File finalizer = new File(project.getParentFile(), "kr-unified-finalizer");
        if (!finalizer.isDirectory() && !finalizer.mkdirs()) throw new IOException("KR-Linkordner kann nicht erstellt werden.");

        if (target == Target.ANDROID) {
            File library = new File(output, "libuser.so");
            List<String> command = compiler();
            command.add("--target=aarch64-linux-android26"); command.add("-std=c++20");
            command.add("-O2"); command.add("-fPIC"); command.add("-shared");
            command.add("-L" + new File(toolchain, "usr/lib").getPath());
            command.add("-Wl,--export-dynamic");
            command.add("-Wl,--allow-multiple-definition");
            File sdl = null;
            if (gui) {
                sdl = ensureAndroidSdlArchive();
                File dependencyCache = new File(context.getFilesDir(), "components/cmake/android");
                for (File inc : getAndroidSdlIncludeDirs(dependencyCache)) {
                    command.add("-I" + inc.getPath());
                }
            }
            if (cppSource != null && cppSource.isFile()) {
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
            command.add("-lc++_shared"); command.add("-llog"); command.add("-landroid"); command.add("-ldl"); command.add("-lm");
            command.add("-o"); command.add(library.getPath());
            run(command, "KR Unified Android-Bibliothek wird gelinkt", finalizer);

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

        File entrySource;
        if (cppSource != null && cppSource.isFile()) {
            entrySource = cppSource;
        } else {
            entrySource = new File(finalizer, "kr_entry.cpp");
            writeText(entrySource, "extern \"C\" int KrLanguageMain(void);\nint main(int argc, char** argv) { (void)argc; (void)argv; return KrLanguageMain(); }\n");
        }

        cmake.append("add_executable(kr_program \"").append(cmakeEscape(entrySource.getPath())).append("\")\n");
        cmake.append("target_include_directories(kr_program PRIVATE \"").append(cmakeEscape(entrySource.getParentFile().getPath())).append("\")\n");
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
        if (target == Target.WINDOWS) cmake.append(" ws2_32 winmm advapi32 userenv shell32 ole32 bcrypt ntdll");
        else cmake.append(" pthread dl rt m");
        cmake.append(")\n");

        writeText(new File(finalizer, "CMakeLists.txt"), cmake.toString());
        if (gui) {
            writeText(new File(finalizer, "kr-gui-target.txt"), "sdl3\n");
            writeText(new File(finalizer, "vcpkg.json"), "{\n  \"name\": \"kr-unified-gui\",\n  \"version-string\": \"1.0.0\",\n  \"dependencies\": [\"sdl3\"]\n}\n");
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
        File artifact = new File(output, target == Target.WINDOWS ? "programm.exe" : "programm");
        List<String> command = compiler();
        if (target == Target.WINDOWS) {
            File win = new File(toolchain, "targets/windows");
            command.add("--target=x86_64-w64-windows-gnu");
            command.add("--sysroot=" + win);
            command.add("-fuse-ld=lld"); command.add("-static");
            // This MinGW sysroot is LLVM based. Without these explicit choices,
            // clang assumes a GCC installation and asks lld for unavailable
            // libgcc/libgcc_eh even though compiler-rt and libunwind are bundled.
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
            // Termux clang defaults to Android libc++_shared. The Linux target
            // ships GCC 14's headers and static libstdc++, so select it explicitly.
            command.add("-stdlib=libstdc++");
            command.add("-rtlib=libgcc"); command.add("-static-libstdc++"); command.add("-static-libgcc");
        }
        command.add("-std=c++20"); command.add("-O2");
        addSources(command, sources); command.add("-o"); command.add(artifact.getPath());
        run(command, "Desktop-Programm wird kompiliert", project);
        artifact.setExecutable(true, false);
        return artifact;
    }

    private File buildAndroid(List<File> sources) throws Exception {
        File library = new File(output, "libuser.so");
        List<String> command = compiler();
        command.add("--target=aarch64-linux-android26"); command.add("-std=c++20");
        command.add("-O2"); command.add("-fPIC"); command.add("-shared");
        // The embedded Termux NDK provides libc++_shared in usr/lib instead of
        // the usual NDK triple directory. Add it explicitly and package the same
        // runtime into the generated APK below.
        command.add("-L" + new File(toolchain, "usr/lib").getPath());
        command.add("-Wl,--export-dynamic"); command.add("-Wl,-z,defs");
        addSources(command, sources); command.add("-o"); command.add(library.getPath());
        run(command, "Android-Bibliothek wird kompiliert", project);
        File apk = new File(output, "programm-android-arm64.apk");
        File cppRuntime = new File(toolchain, "usr/lib/libc++_shared.so");
        if (!cppRuntime.isFile()) throw new IOException("Eingebettete Android-C++-Laufzeit fehlt.");
        RunnerPackager.create(context, library, cppRuntime, project, false, apk, log);
        if (!library.delete()) log.line("Hinweis: temporäre Android-Bibliothek bleibt in der Ausgabe.");
        return apk;
    }

    private List<String> compiler() {
        return new ArrayList<>(Collections.singletonList(new File(toolchain, "usr/bin/clang++").getPath()));
    }
    private void addSources(List<String> command, List<File> sources) {
        for (File file : sources) command.add(file.getPath());
        command.add("-I"); command.add(project.getPath());
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
        if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) collect(child, result); return; }
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
