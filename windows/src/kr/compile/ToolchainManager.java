package kr.compile;

import java.io.File;
import java.util.Map;

/**
 * Verwaltet die integrierte, portable Compiler-Toolchain für Windows.
 * Genau wie in der Android-Version (ToolchainInstaller) macht dies die Windows IDE
 * vollkommen autark: Der Benutzer muss KEINE Compiler, SDKs oder MSYS2 vorinstallieren.
 */
public final class ToolchainManager {

    private static ToolchainManager instance;
    private File toolchainDir;

    public static synchronized ToolchainManager getInstance() {
        if (instance == null) {
            instance = new ToolchainManager();
        }
        return instance;
    }

    private ToolchainManager() {
        locateToolchain();
    }

    private void locateToolchain() {
        File baseDir = getApplicationDirectory();

        // 1. Direkt im Verzeichnis "toolchain" neben dem Programm (z.B. windows/toolchain)
        File candidate = new File(baseDir, "toolchain");
        if (isValidToolchain(candidate)) {
            toolchainDir = candidate;
            return;
        }

        // 2. Im übergeordneten Ordner (falls die JAR in dist/ gestartet wurde)
        if (baseDir.getParentFile() != null) {
            candidate = new File(baseDir.getParentFile(), "toolchain");
            if (isValidToolchain(candidate)) {
                toolchainDir = candidate;
                return;
            }
        }

        // 3. Im Entwicklungs- bzw. Projektpfad
        File devTc = new File("D:\\android_project\\windows\\toolchain");
        if (isValidToolchain(devTc)) {
            toolchainDir = devTc;
            return;
        }

        // 4. Im lokalen Anwendungsdaten-Verzeichnis (%LOCALAPPDATA%\KR-Compiler-IDE\toolchain)
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.trim().isEmpty()) {
            candidate = new File(new File(localAppData, "KR-Compiler-IDE"), "toolchain");
            if (isValidToolchain(candidate)) {
                toolchainDir = candidate;
                return;
            }
        }

        // Standard-Fallback
        toolchainDir = new File(baseDir, "toolchain");
    }

    public boolean isValidToolchain(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        File bin = new File(dir, "bin");
        if (!bin.isDirectory()) return false;
        File gpp = new File(bin, "g++.exe");
        File clang = new File(bin, "clang++.exe");
        return gpp.isFile() || clang.isFile();
    }

    public File getToolchainDir() {
        return toolchainDir;
    }

    public boolean isEmbeddedToolchainPresent() {
        return isValidToolchain(toolchainDir);
    }

    public String getCompilerExecutable() {
        if (isValidToolchain(toolchainDir)) {
            File bin = new File(toolchainDir, "bin");
            File gpp = new File(bin, "g++.exe");
            if (gpp.isFile()) return gpp.getAbsolutePath();
            File clang = new File(bin, "clang++.exe");
            if (clang.isFile()) return clang.getAbsolutePath();
        }
        return null;
    }

    public String getMakeExecutable() {
        if (isValidToolchain(toolchainDir)) {
            File bin = new File(toolchainDir, "bin");
            File make = new File(bin, "mingw32-make.exe");
            if (make.isFile()) return make.getAbsolutePath();
            make = new File(bin, "make.exe");
            if (make.isFile()) return make.getAbsolutePath();
        }
        return null;
    }

    public File getIncludeDir() {
        if (toolchainDir != null) {
            File inc = new File(toolchainDir, "include");
            if (inc.isDirectory()) return inc;
        }
        return null;
    }

    public File getLibDir() {
        if (toolchainDir != null) {
            File lib = new File(toolchainDir, "lib");
            if (lib.isDirectory()) return lib;
        }
        return null;
    }

    public String getGccExecutable() {
        if (isValidToolchain(toolchainDir)) {
            File bin = new File(toolchainDir, "bin");
            File gcc = new File(bin, "gcc.exe");
            if (gcc.isFile()) return gcc.getAbsolutePath();
        }
        return null;
    }

    public String getGoExecutable() {
        if (toolchainDir != null && toolchainDir.isDirectory()) {
            File goBin = new File(toolchainDir, "go/bin/go.exe");
            if (goBin.isFile()) return goBin.getAbsolutePath();
            File binGo = new File(toolchainDir, "bin/go.exe");
            if (binGo.isFile()) return binGo.getAbsolutePath();
        }
        File standardGo = new File("C:\\Program Files\\Go\\bin\\go.exe");
        if (standardGo.isFile()) return standardGo.getAbsolutePath();
        return null;
    }

    public File getGoRoot() {
        if (toolchainDir != null && toolchainDir.isDirectory()) {
            File goDir = new File(toolchainDir, "go");
            if (new File(goDir, "bin/go.exe").isFile()) return goDir;
        }
        File standardGo = new File("C:\\Program Files\\Go");
        if (new File(standardGo, "bin/go.exe").isFile()) return standardGo;
        return null;
    }

    public void injectEnvironment(Map<String, String> env) {
        if (toolchainDir != null && toolchainDir.isDirectory()) {
            File bin = new File(toolchainDir, "bin");
            File goBin = new File(toolchainDir, "go/bin");
            StringBuilder extra = new StringBuilder();
            if (bin.isDirectory()) extra.append(bin.getAbsolutePath()).append(File.pathSeparator);
            if (goBin.isDirectory()) extra.append(goBin.getAbsolutePath()).append(File.pathSeparator);

            String existing = env.getOrDefault("PATH", "");
            env.put("PATH", extra.toString() + existing);

            File goRoot = getGoRoot();
            if (goRoot != null) {
                env.put("GOROOT", goRoot.getAbsolutePath());
            }
            env.put("CGO_ENABLED", "1");
            String gcc = getGccExecutable();
            if (gcc != null) {
                env.put("CC", gcc);
            }
        }
    }

    public static File getApplicationDirectory() {
        try {
            File jar = new File(ToolchainManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jar.isFile()) {
                return jar.getParentFile();
            }
        } catch (Exception ignored) {}
        return new File(System.getProperty("user.dir", "."));
    }
}
