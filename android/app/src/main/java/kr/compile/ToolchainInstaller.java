package kr.compile;

import android.content.Context;
import android.os.Build;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ToolchainInstaller {
    private static final String STAMP = "kr-toolchain-21.1.8-cmake-v9";
    private static final String GRAPHICS_STAMP = "kr-android-graphics-headers-v1";
    private ToolchainInstaller() {}

    static File ensure(Context context, CompilerEngine.Logger log) throws IOException {
        boolean arm64 = false;
        for (String abi : Build.SUPPORTED_ABIS) if ("arm64-v8a".equals(abi)) arm64 = true;
        if (!arm64) throw new IOException("Dieses Gerät unterstützt kein ARM64 (arm64-v8a). Die Compiler-APK benötigt ein ARM64-Android-Gerät.");
        // Termux/Clang is compiled for /data/data/kr.compile/files/usr. Keep usr
        // directly below filesDir; an extra directory would break its resource lookup.
        File root = context.getFilesDir();
        File stamp = new File(root, STAMP);
        File clang = new File(root, "usr/bin/clang++");
        if (stamp.isFile() && clang.isFile()) {
            installGraphicsHeaders(context, root, log);
            verify(root);
            return root;
        }
        log.line("Compilerlaufzeit wird einmalig entpackt …");
        delete(new File(root, "usr"));
        delete(new File(root, "targets"));
        deleteLegacyBackslashEntries(root);
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Compilerordner kann nicht erstellt werden.");
        try (InputStream raw = context.getAssets().open("toolchain.zip");
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buffer = new byte[128 * 1024];
            String canonicalRoot = root.getCanonicalPath() + File.separator;
            while ((entry = zip.getNextEntry()) != null) {
                // Archives produced on Windows can contain backslashes. A
                // backslash is a legal filename character on Android and must
                // therefore be normalized explicitly before extraction.
                String entryName = entry.getName().replace('\\', '/');
                while (entryName.startsWith("/")) entryName = entryName.substring(1);
                File out = new File(root, entryName);
                if (!out.getCanonicalPath().startsWith(canonicalRoot)) throw new IOException("Ungültiger Eintrag in Compilerpaket.");
                if (entry.isDirectory()) { if (!out.isDirectory() && !out.mkdirs()) throw new IOException("Ordnerfehler: " + out); continue; }
                File parent = out.getParentFile(); if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Ordnerfehler: " + parent);
                try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(out))) {
                    int n; while ((n = zip.read(buffer)) >= 0) stream.write(buffer, 0, n);
                }
            }
        } catch (FileNotFoundException e) {
            throw new IOException("Die APK enthält keine Compilerlaufzeit. Bitte die vollständige APK installieren.", e);
        }
        File bin = new File(root, "usr/bin");
        File[] executables = bin.listFiles();
        if (executables != null) for (File file : executables) file.setExecutable(true, false);
        installGraphicsHeaders(context, root, log);
        verify(root);
        if (!stamp.createNewFile()) throw new IOException("Compilerstatus kann nicht gespeichert werden.");
        return root;
    }

    private static void verify(File root) throws IOException {
        String[] required = {
                "usr/bin/clang++", "usr/bin/clang-21", "usr/bin/ld.lld",
                "usr/bin/cmake", "usr/bin/ninja", "usr/bin/pkg-config", "usr/bin/git",
                "usr/etc/tls/cert.pem",
                "usr/lib/libLLVM.so", "usr/lib/libclang-cpp.so", "usr/lib/libc++_shared.so",
                "usr/include/c++/v1/iostream", "usr/lib/clang/21/include/stddef.h",
                "usr/include/EGL/egl.h", "usr/include/GLES/gl.h", "usr/include/GLES2/gl2.h",
                "targets/windows/include/windows.h", "targets/windows/include/c++/v1/iostream",
                "targets/windows/x86_64-w64-mingw32/lib/crt2.o",
                "usr/lib/clang/21/lib/x86_64-w64-windows-gnu/libclang_rt.builtins.a",
                "targets/linux/sysroot/usr/include/stdio.h",
                "targets/linux/sysroot/usr/include/X11/Xlib.h",
                "targets/linux/sysroot/usr/include/X11/extensions/Xext.h",
                "targets/linux/sysroot/usr/lib/x86_64-linux-gnu/libX11.so",
                "targets/linux/sysroot/lib64/ld-linux-x86-64.so.2"
        };
        for (String path : required) if (!new File(root, path).isFile())
            throw new IOException("Compilerpaket ist unvollständig; erforderlich fehlt: " + path);
        for (String executable : new String[]{"usr/bin/clang++", "usr/bin/clang-21", "usr/bin/ld.lld", "usr/bin/cmake", "usr/bin/ninja", "usr/bin/git"})
            if (!new File(root, executable).canExecute()) throw new IOException("Compilerwerkzeug ist nicht ausführbar: " + executable);
    }

    private static void installGraphicsHeaders(Context context, File root, CompilerEngine.Logger log) throws IOException {
        File header = new File(root, "usr/include/GLES2/gl2.h");
        File stamp = new File(root, GRAPHICS_STAMP);
        if (stamp.isFile() && header.isFile()) return;
        log.line("Android EGL-/OpenGL-ES-Header für GUI-Projekte werden eingerichtet …");
        try (InputStream raw = context.getAssets().open("android-graphics-headers.zip");
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buffer = new byte[32 * 1024];
            String canonicalRoot = root.getCanonicalPath() + File.separator;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                while (entryName.startsWith("/")) entryName = entryName.substring(1);
                File out = new File(root, entryName);
                if (!out.getCanonicalPath().startsWith(canonicalRoot)) throw new IOException("Ungültiger Grafik-Header-Eintrag.");
                if (entry.isDirectory()) {
                    if (!out.isDirectory() && !out.mkdirs()) throw new IOException("Grafik-Header-Ordner kann nicht erstellt werden.");
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Grafik-Header-Ordner kann nicht erstellt werden.");
                try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(out))) {
                    int n;
                    while ((n = zip.read(buffer)) >= 0) stream.write(buffer, 0, n);
                }
            }
        }
        if (!header.isFile()) throw new IOException("Android-GUI-Header konnten nicht installiert werden.");
        if (!stamp.isFile() && !stamp.createNewFile()) throw new IOException("Grafik-Header-Status kann nicht gespeichert werden.");
    }

    private static void copy(File source, File destination) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        }
        destination.setExecutable(true, false);
    }

    private static void delete(File file) throws IOException {
        // File.exists() follows symbolic links and returns false for a dangling
        // link. Such a leftover link previously survived an upgrade and made
        // extraction fail with "open failed: ENOENT". Inspect and delete the
        // directory entry itself instead of following it.
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        Files.delete(file.toPath());
    }

    private static void deleteLegacyBackslashEntries(File root) throws IOException {
        File[] children = root.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName();
            if (name.startsWith("usr\\") || name.startsWith("targets\\")) delete(child);
        }
    }
}
