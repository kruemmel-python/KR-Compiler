package kr.compile;

import java.io.File;
import java.net.URI;
import java.util.Map;

/** Locates only the portable Linux bundle; system SDKs are never required. */
public final class ToolchainManager {
    private static final ToolchainManager INSTANCE = new ToolchainManager();
    private final File root;

    public static ToolchainManager getInstance() { return INSTANCE; }

    private ToolchainManager() {
        File app = applicationDirectory();
        File beside = new File(app, "toolchain");
        File parent = app.getParentFile() != null ? new File(app.getParentFile(), "toolchain") : beside;
        root = valid(beside) ? beside : valid(parent) ? parent : beside;
    }

    private static boolean valid(File directory) {
        return directory.isDirectory() && (executable(directory, "bin/clang++") != null
                || executable(directory, "bin/g++") != null);
    }

    public boolean isValidToolchain(File directory) { return valid(directory); }
    public boolean isEmbeddedToolchainPresent() { return valid(root); }
    public File getToolchainDir() { return root; }
    public String getCompilerExecutable() { return first("bin/clang++", "bin/g++"); }
    public String getGccExecutable() { return first("bin/clang", "bin/gcc"); }
    public String getWindowsCompilerExecutable() {
        return first("targets/windows/bin/x86_64-w64-mingw32-clang++",
                "targets/windows/bin/x86_64-w64-mingw32-g++", "bin/x86_64-w64-mingw32-g++");
    }
    public String getWindowsCCompilerExecutable() {
        return first("targets/windows/bin/x86_64-w64-mingw32-clang",
                "targets/windows/bin/x86_64-w64-mingw32-gcc", "bin/x86_64-w64-mingw32-gcc");
    }
    public String getCargoExecutable() { return first("bin/cargo", "rust/bin/cargo"); }
    public String getRustcExecutable() { return first("bin/rustc", "rust/bin/rustc"); }
    public String getGoExecutable() { return first("go/bin/go", "bin/go"); }
    public String getNinjaExecutable() { return first("bin/ninja"); }
    public String getCMakeExecutable() { return first("bin/cmake"); }
    public String getMakeExecutable() { return first("bin/make"); }
    public File getIncludeDir() { File value = new File(root, "include"); return value.isDirectory() ? value : null; }
    public File getLibDir() { File value = new File(root, "lib"); return value.isDirectory() ? value : null; }

    public File getNdkDir() {
        File value = new File(root, "android/ndk");
        return new File(value, "build/cmake/android.toolchain.cmake").isFile() ? value : null;
    }
    public File getAndroidCMakeToolchainFile() {
        File ndk = getNdkDir();
        return ndk == null ? null : new File(ndk, "build/cmake/android.toolchain.cmake");
    }
    public String getAndroidCompilerExecutable() {
        File ndk = getNdkDir();
        if (ndk == null) return null;
        return executable(ndk, "toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang++");
    }
    public String getAndroidCCompilerExecutable() {
        File ndk = getNdkDir();
        if (ndk == null) return null;
        return executable(ndk, "toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang");
    }

    public void injectEnvironment(Map<String, String> environment) {
        String path = new File(root, "bin").getAbsolutePath() + File.pathSeparator
                + new File(root, "go/bin").getAbsolutePath()
                + File.pathSeparator + new File(root, "jdk/bin").getAbsolutePath();
        String existing = environment.get("PATH");
        environment.put("PATH", path + (existing == null || existing.isEmpty() ? "" : File.pathSeparator + existing));
        environment.put("GOROOT", new File(root, "go").getAbsolutePath());
        environment.put("CARGO_HOME", new File(root, "cargo-home").getAbsolutePath());
        environment.put("RUSTUP_HOME", new File(root, "rustup-home").getAbsolutePath());
    }

    private String first(String... paths) {
        for (String path : paths) { String value = executable(root, path); if (value != null) return value; }
        return null;
    }
    private static String executable(File base, String relative) {
        File value = new File(base, relative);
        return value.isFile() && value.canExecute() ? value.getAbsolutePath() : null;
    }
    private static File applicationDirectory() {
        try {
            URI location = ToolchainManager.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File value = new File(location);
            return value.isFile() ? value.getParentFile() : value;
        } catch (Exception ignored) { return new File(".").getAbsoluteFile(); }
    }
}
