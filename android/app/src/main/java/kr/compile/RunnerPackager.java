package kr.compile;

import android.content.Context;
import com.android.apksig.ApkSigner;

import java.io.*;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.zip.*;

final class RunnerPackager {
    private RunnerPackager() {}

    static void verifySigning(Context context) throws Exception {
        SignerMaterial material = loadSigner(context);
        byte[] probe = "KR-Compiler-Selbsttest".getBytes("UTF-8");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(material.key); signature.update(probe);
        byte[] signed = signature.sign();
        signature.initVerify(material.certificate); signature.update(probe);
        if (!signature.verify(signed)) throw new IOException("Eingebetteter APK-Signierschlüssel ist ungültig.");
    }

    static void create(Context context, File userLibrary, File cppRuntime, File project,
                       boolean guiProgram, File destination, CompilerEngine.Logger log) throws Exception {
        File unsigned = new File(context.getCacheDir(), "kr-runner-unsigned.apk");
        File projectData = new File(context.getCacheDir(), "kr-runner-project-data.zip");
        ResourceStats resources = createProjectData(project, projectData);
        log.line(resources.count + " Laufzeitdatei(en) werden in die Android-APK übernommen (" +
                readableSize(resources.bytes) + ").");
        try (InputStream template = context.getAssets().open("runner-template.apk")) {
            inject(template, userLibrary, cppRuntime, projectData, guiProgram, unsigned);
        }
        // Do not use the desktop-only JKS provider here. Android universally
        // supports RSA PKCS#8 keys and X.509 certificates through its platform JCA.
        SignerMaterial material = loadSigner(context);
        PrivateKey key = material.key;
        X509Certificate certificate = material.certificate;
        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder("KR Runner", key,
                Collections.singletonList(certificate)).build();
        new ApkSigner.Builder(Collections.singletonList(signer)).setInputApk(unsigned).setOutputApk(destination)
                .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).build().sign();
        if (!unsigned.delete()) log.line("Hinweis: temporäre APK konnte nicht gelöscht werden.");
        if (!projectData.delete()) log.line("Hinweis: temporäres Laufzeitarchiv konnte nicht gelöscht werden.");
        log.line("Android-APK wurde erstellt und signiert.");
    }

    private static SignerMaterial loadSigner(Context context) throws Exception {
        PrivateKey key;
        X509Certificate certificate;
        try (InputStream in = context.getAssets().open("kr-runner-key.pk8")) {
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(readAll(in)));
        }
        try (InputStream in = context.getAssets().open("kr-runner-cert.der")) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        return new SignerMaterial(key, certificate);
    }

    private static final class SignerMaterial {
        final PrivateKey key; final X509Certificate certificate;
        SignerMaterial(PrivateKey key, X509Certificate certificate) { this.key = key; this.certificate = certificate; }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static void inject(InputStream raw, File library, File cppRuntime, File projectData,
                               boolean guiProgram, File outFile) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(raw));
             ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)))) {
            ZipEntry entry; byte[] buffer = new byte[128 * 1024];
            Set<String> written = new HashSet<>();
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                // Directory records are not required in an APK. Some ZIP producers emit
                // them more than once; Android's ZipOutputStream rejects such duplicates.
                if (name.length() == 0 || entry.isDirectory() || name.startsWith("META-INF/") ||
                        name.equals("lib/arm64-v8a/libuser.so") ||
                        name.equals("lib/arm64-v8a/libc++_shared.so") ||
                        name.equals("assets/kr-project-data.zip") ||
                        name.equals("assets/kr-gui-runner")) continue;
                if (!written.add(name)) continue;
                ZipEntry copy = new ZipEntry(name); copy.setTime(0); out.putNextEntry(copy);
                int n; while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n); out.closeEntry();
            }
            addFile(out, written, "lib/arm64-v8a/libuser.so", library, buffer);
            addFile(out, written, "lib/arm64-v8a/libc++_shared.so", cppRuntime, buffer);
            addFile(out, written, "assets/kr-project-data.zip", projectData, buffer);
            if (guiProgram) addBytes(out, written, "assets/kr-gui-runner", "SDL3\n".getBytes("UTF-8"));
        }
    }

    private static ResourceStats createProjectData(File project, File archive) throws IOException {
        ResourceStats stats = new ResourceStats();
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(archive)))) {
            addProjectFiles(project, project, out, stats);
        }
        return stats;
    }

    private static void addProjectFiles(File root, File current, ZipOutputStream out,
                                        ResourceStats stats) throws IOException {
        File[] children = current.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName));
        byte[] buffer = new byte[128 * 1024];
        for (File child : children) {
            String relative = root.toPath().relativize(child.toPath()).toString().replace('\\', '/');
            if (child.isDirectory()) {
                if (!ignoredDirectory(child.getName())) addProjectFiles(root, child, out, stats);
            } else if (child.isFile() && !ignoredFile(child.getName())) {
                ZipEntry entry = new ZipEntry(relative); entry.setTime(0); out.putNextEntry(entry);
                try (InputStream in = new BufferedInputStream(new FileInputStream(child))) {
                    int n; while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
                }
                out.closeEntry(); stats.count++; stats.bytes += child.length();
            }
        }
    }

    private static boolean ignoredDirectory(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals(".git") || n.equals(".svn") || n.equals(".gradle") || n.equals(".idea") ||
                n.equals("kr_build") || n.equals("build") || n.startsWith("cmake-build-");
    }

    private static boolean ignoredFile(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("cmakelists.txt") || n.equals("makefile") || n.equals("kr-android-target.txt")) return true;
        int dot = n.lastIndexOf('.');
        String extension = dot >= 0 ? n.substring(dot) : "";
        return Arrays.asList(".c", ".cc", ".cpp", ".cxx", ".h", ".hh", ".hpp", ".hxx", ".cmake",
                ".o", ".obj", ".a", ".lib", ".so", ".dll", ".exe", ".apk", ".class", ".jar",
                ".zip", ".7z", ".tar", ".gz", ".bz2", ".xz").contains(extension);
    }

    private static String readableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.GERMANY, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static final class ResourceStats { int count; long bytes; }

    private static void addFile(ZipOutputStream out, Set<String> written, String name,
                                File file, byte[] buffer) throws IOException {
        if (!file.isFile() || file.length() == 0) {
            throw new IOException("Benötigte APK-Datei fehlt oder ist leer: " + file.getName());
        }
        if (!written.add(name)) return;
        ZipEntry entry = new ZipEntry(name); entry.setTime(0); out.putNextEntry(entry);
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            int n; while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        }
        out.closeEntry();
    }

    private static void addBytes(ZipOutputStream out, Set<String> written, String name,
                                 byte[] data) throws IOException {
        if (!written.add(name)) return;
        ZipEntry entry = new ZipEntry(name); entry.setTime(0); out.putNextEntry(entry);
        out.write(data); out.closeEntry();
    }
}
