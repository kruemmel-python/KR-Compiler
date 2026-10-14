package kr.compile.runner;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RunnerActivity extends Activity {
    static {
        System.loadLibrary("kr_runner");
    }
    private static native int execute(String outputFile, String libraryFile, String workingDirectory);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this); text.setTypeface(Typeface.MONOSPACE); text.setTextSize(14);
        int p = Math.round(16 * getResources().getDisplayMetrics().density); text.setPadding(p, p, p, p);
        ScrollView scroll = new ScrollView(this); scroll.addView(text); setContentView(scroll);
        text.setText("C++-Programm läuft …\n");
        File output = new File(getCacheDir(), "program-output.txt");
        if (output.isFile()) output.delete();
        AtomicBoolean running = new AtomicBoolean(true);
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable refresh = new Runnable() {
            @Override public void run() {
                if (!running.get()) return;
                try {
                    if (output.isFile()) {
                        String content = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
                        if (!content.isEmpty()) text.setText(content + "\n[Programm läuft …]\n");
                    }
                } catch (Throwable ignored) { }
                handler.postDelayed(this, 500);
            }
        };
        handler.post(refresh);
        new Thread(() -> {
            String shown;
            try {
                File library = new File(getApplicationInfo().nativeLibraryDir, "libuser.so");
                File project = prepareProjectFiles();
                int code = execute(output.getPath(), library.getPath(), project.getPath());
                String content = output.isFile() ? new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8) : "";
                shown = content + "\n[Programm beendet: " + code + "]\n";
            } catch (Throwable error) { shown = "Programmfehler: " + error + "\n"; }
            running.set(false);
            String result = shown; runOnUiThread(() -> text.setText(result));
        }, "kr-cpp-main").start();
    }

    private File prepareProjectFiles() throws Exception {
        File project = new File(getFilesDir(), "project");
        deleteTree(project);
        if (!project.mkdirs()) throw new IllegalStateException("Laufzeitordner kann nicht erstellt werden.");
        String root = project.getCanonicalPath() + File.separator;
        try (InputStream raw = getAssets().open("kr-project-data.zip");
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry; byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                File target = new File(project, entry.getName());
                String canonical = target.getCanonicalPath();
                if (!canonical.startsWith(root)) throw new SecurityException("Ungültiger Projektpfad in der APK.");
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                    throw new IllegalStateException("Laufzeit-Unterordner kann nicht erstellt werden.");
                try (OutputStream out = new FileOutputStream(target)) {
                    int n; while ((n = zip.read(buffer)) >= 0) out.write(buffer, 0, n);
                }
                zip.closeEntry();
            }
        }
        return project;
    }

    private static void deleteTree(File file) throws Exception {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) throw new IllegalStateException("Alter Laufzeitordner kann nicht erneuert werden.");
    }
}
