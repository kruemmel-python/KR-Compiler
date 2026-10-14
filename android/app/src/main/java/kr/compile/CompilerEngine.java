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
            if (cmakeProject.isFile()) {
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
        Set<String> packages = findCMakePackages(project);
        Set<String> manifestPackages = VcpkgManager.readDependencies(project);
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

        String systemVersion = target == Target.ANDROID ? "26" : (target == Target.WINDOWS ? "10.0" : "1");
        List<String> configure = new ArrayList<>();
        Collections.addAll(configure, cmake.getPath(), "-S", project.getPath(), "-B", binary.getPath(), "-G", "Ninja",
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_SYSTEM_VERSION=" + systemVersion,
                "-DCMAKE_MAKE_PROGRAM=" + ninja.getPath(),
                "-DCMAKE_TOOLCHAIN_FILE=" + toolchainFile.getPath(),
                "-DCMAKE_PROJECT_TOP_LEVEL_INCLUDES=" + projectInclude.getPath(),
                "-DFETCHCONTENT_BASE_DIR=" + dependencyCache.getPath(),
                "-DFETCHCONTENT_QUIET=OFF");
        if (!onlineDependencies) configure.add("-DFETCHCONTENT_FULLY_DISCONNECTED=ON");
        else log.line("Online-Abhängigkeiten sind erlaubt; CMake FetchContent und Git dürfen benötigte Quellen laden.");
        run(configure, "CMake konfiguriert das Projekt", project);

        List<String> build = new ArrayList<>();
        Collections.addAll(build, cmake.getPath(), "--build", binary.getPath(), "--config", "Release", "--parallel",
                Integer.toString(Math.max(1, Runtime.getRuntime().availableProcessors())));
        run(build, "CMake baut das Projekt", project);

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
            RunnerPackager.create(context, library, cppRuntime, project, apk, log);
            if (!library.delete()) log.line("Hinweis: temporäre Android-Bibliothek bleibt in der Ausgabe.");
            return apk;
        }

        File artifact = new File(output, target == Target.WINDOWS ? "programm.exe" : "programm");
        copy(built, artifact);
        artifact.setExecutable(true, false);
        return artifact;
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
            if (key.equals("sdl3")) {
                name = "SDL3"; url = "https://github.com/libsdl-org/SDL/archive/refs/tags/release-3.4.18.tar.gz";
                hash = "12fcb5592016b27922b08d1580d75f5f15c72e67cf04415e2bd824dc7563ac940c25bbe368b03d0a755f5832803049467e3d0ee6784ed3684792bc4d7741af50";
                options = "set(SDL_SHARED OFF CACHE BOOL \"\" FORCE)\nset(SDL_STATIC ON CACHE BOOL \"\" FORCE)\n"
                        + "set(SDL_TEST_LIBRARY OFF CACHE BOOL \"\" FORCE)\nset(SDL_TESTS OFF CACHE BOOL \"\" FORCE)\n"
                        + "set(SDL_EXAMPLES OFF CACHE BOOL \"\" FORCE)\nset(SDL_INSTALL OFF CACHE BOOL \"\" FORCE)\n"
                        + (target == Target.LINUX
                        ? "set(HAVE_WL_VERSION_SCRIPT TRUE CACHE INTERNAL \"\" FORCE)\nset(SDL_X11 ON CACHE BOOL \"\" FORCE)\nset(SDL_X11_SHARED ON CACHE BOOL \"\" FORCE)\nset(X11_INCLUDEDIR \"${CMAKE_SYSROOT}/usr/include\" CACHE PATH \"\" FORCE)\nset(X11_LIB \"${CMAKE_SYSROOT}/usr/lib/x86_64-linux-gnu/libX11.so\" CACHE FILEPATH \"\" FORCE)\nset(XEXT_LIB \"${CMAKE_SYSROOT}/usr/lib/x86_64-linux-gnu/libXext.so\" CACHE FILEPATH \"\" FORCE)\nset(HAVE_XEXT_H \"${CMAKE_SYSROOT}/usr/include/X11/extensions/Xext.h\" CACHE FILEPATH \"\" FORCE)\nset(SDL_X11_XCURSOR OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XDBE OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XINPUT OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XFIXES OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XRANDR OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XSCRNSAVER OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XSHAPE OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XSYNC OFF CACHE BOOL \"\" FORCE)\nset(SDL_X11_XTEST OFF CACHE BOOL \"\" FORCE)\nset(SDL_WAYLAND OFF CACHE BOOL \"\" FORCE)\nset(SDL_KMSDRM OFF CACHE BOOL \"\" FORCE)\nset(SDL_OPENGL OFF CACHE BOOL \"\" FORCE)\nset(SDL_OPENGLES OFF CACHE BOOL \"\" FORCE)\n"
                        : "");
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
                    .append(" DOWNLOAD_EXTRACT_TIMESTAMP TRUE OVERRIDE_FIND_PACKAGE)\n");
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
        RunnerPackager.create(context, library, cppRuntime, project, apk, log);
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
