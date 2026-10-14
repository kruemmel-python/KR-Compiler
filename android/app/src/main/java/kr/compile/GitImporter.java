package kr.compile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GitImporter {

    public interface ProgressCallback {
        void onProgress(String message);
        void onSuccess(File projectDir);
        void onError(String error);
    }

    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
            "^https://github\\.com/([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+?)(?:\\.git)?/?$"
    );

    public static boolean isValidGitHubUrl(String url) {
        if (url == null) return false;
        return GITHUB_URL_PATTERN.matcher(url.trim()).matches();
    }

    public static void cloneOrDownload(Context context, String rawUrl, ProgressCallback callback) {
        final Handler handler = new Handler(Looper.getMainLooper());
        if (rawUrl == null || !isValidGitHubUrl(rawUrl)) {
            callback.onError("Ungültige GitHub-URL. Erwartet: https://github.com/Benutzer/Repository.git");
            return;
        }

        final String url = rawUrl.trim();
        Matcher m = GITHUB_URL_PATTERN.matcher(url);
        if (!m.matches()) {
            callback.onError("URL-Format wird nicht unterstützt.");
            return;
        }

        final String owner = m.group(1);
        final String repoName = m.group(2);

        new Thread(() -> {
            try {
                handler.post(() -> callback.onProgress("Prüfe Zielverzeichnis …"));

                File projectsDir = new File(context.getFilesDir(), "projects");
                if (!projectsDir.exists()) projectsDir.mkdirs();

                File destDir = new File(projectsDir, repoName);
                if (destDir.exists()) {
                    // Unique name if already exists
                    destDir = new File(projectsDir, repoName + "-" + System.currentTimeMillis() % 10000);
                }

                // Check if git tool is available
                File toolchainRoot = context.getFilesDir();
                File gitBin = new File(toolchainRoot, "usr/bin/git");

                boolean gitSuccess = false;
                if (gitBin.canExecute()) {
                    handler.post(() -> callback.onProgress("Klone Repository mit Git …"));
                    try {
                        runGitClone(context, gitBin, toolchainRoot, url, destDir, line -> {
                            handler.post(() -> callback.onProgress(line));
                        });
                        gitSuccess = true;
                    } catch (Exception e) {
                        handler.post(() -> callback.onProgress("Git-Klonen fehlgeschlagen, versuche HTTPS-ZIP-Download …"));
                    }
                }

                if (!gitSuccess) {
                    handler.post(() -> callback.onProgress("Lade Quellcode-Archiv über HTTPS herunter …"));
                    downloadAndExtractZip(owner, repoName, destDir, msg -> {
                        handler.post(() -> callback.onProgress(msg));
                    });
                }

                final File finalDir = destDir;
                handler.post(() -> {
                    callback.onProgress("Projekt erfolgreich geladen: " + finalDir.getName());
                    callback.onSuccess(finalDir);
                });

            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.toString();
                handler.post(() -> callback.onError("Fehler beim Herunterladen: " + err));
            }
        }).start();
    }

    private interface LineCallback { void onLine(String line); }

    private static void runGitClone(Context context, File gitBin, File toolchain, String url, File destDir, LineCallback callback) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(gitBin.getAbsolutePath());
        cmd.add("clone");
        cmd.add("--depth");
        cmd.add("1");
        cmd.add(url);
        cmd.add(destDir.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        env.put("HOME", context.getFilesDir().getPath());
        env.put("TMPDIR", context.getCacheDir().getPath());
        env.put("PREFIX", new File(toolchain, "usr").getPath());
        env.put("LD_LIBRARY_PATH", new File(toolchain, "usr/lib").getPath());
        env.put("PATH", new File(toolchain, "usr/bin").getPath() + ":/system/bin:/system/xbin");
        File certificates = new File(toolchain, "usr/etc/tls/cert.pem");
        env.put("SSL_CERT_FILE", certificates.getPath());
        env.put("CURL_CA_BUNDLE", certificates.getPath());
        env.put("GIT_SSL_CAINFO", certificates.getPath());

        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) {
                callback.onLine(l);
            }
        }
        int code = p.waitFor();
        if (code != 0) throw new IOException("Git clone fehlgeschlagen (Code " + code + ")");
    }

    private static void downloadAndExtractZip(String owner, String repo, File destDir, LineCallback progress) throws IOException {
        String[] branches = {"main", "master"};
        InputStream stream = null;
        for (String b : branches) {
            String zipUrl = "https://github.com/" + owner + "/" + repo + "/archive/refs/heads/" + b + ".zip";
            try {
                progress.onLine("Verbinde mit " + b + " …");
                HttpURLConnection conn = (HttpURLConnection) new URL(zipUrl).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                if (code == 200) {
                    stream = new BufferedInputStream(conn.getInputStream());
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (stream == null) {
            throw new IOException("Repository konnte weder über Branch 'main' noch 'master' geladen werden.");
        }

        progress.onLine("Entpacke Quellcode …");
        if (!destDir.mkdirs() && !destDir.isDirectory()) {
            throw new IOException("Zielordner konnte nicht erstellt werden: " + destDir);
        }

        try (ZipInputStream zip = new ZipInputStream(stream)) {
            ZipEntry entry;
            byte[] buf = new byte[32 * 1024];
            String rootPrefix = null;

            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                if (rootPrefix == null) {
                    int slash = entryName.indexOf('/');
                    if (slash > 0) rootPrefix = entryName.substring(0, slash + 1);
                }

                String relName = entryName;
                if (rootPrefix != null && relName.startsWith(rootPrefix)) {
                    relName = relName.substring(rootPrefix.length());
                }

                if (relName.isEmpty()) continue;
                File out = new File(destDir, relName);

                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int n;
                        while ((n = zip.read(buf)) >= 0) fos.write(buf, 0, n);
                    }
                }
            }
        }
    }
}
