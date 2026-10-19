package kr.compile.runner;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.net.Uri;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
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
                File nativeDir = new File(getApplicationInfo().nativeLibraryDir);
                File[] libs = nativeDir.listFiles();
                if (libs != null) {
                    for (File f : libs) {
                        String name = f.getName();
                        if (name.endsWith(".so") && !name.equals("libkr_runner.so") && !name.equals("libuser.so")) {
                            try { System.load(f.getPath()); } catch (Throwable ignored) {}
                        }
                    }
                }
                File library = new File(getApplicationInfo().nativeLibraryDir, "libuser.so");
                File project = RunnerFiles.prepareProjectFiles(this);
                int code = execute(output.getPath(), library.getPath(), project.getPath());
                String content = output.isFile() ? new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8) : "";
                String exportResult = exportExecutionLog(new File(project, "cc_opencl_execution.log"));
                String telemetryResult = exportTelemetry(new File(project, "adaptive_compute_telemetry.jsonl"));
                shown = content + "\n[Programm beendet: " + code + "]\n" + exportResult + "\n" + telemetryResult + "\n";
            } catch (Throwable error) { shown = "Programmfehler: " + error + "\n"; }
            running.set(false);
            String result = shown; runOnUiThread(() -> text.setText(result));
        }, "kr-cpp-main").start();
    }

    /** Publishes a native execution log in public Downloads without broad storage permissions. */
    private String exportExecutionLog(File source) {
        return exportDownload(source, "cc_opencl_", ".log", "[LOG]");
    }

    private String exportTelemetry(File source) {
        return exportDownload(source, "adaptive_compute_", ".jsonl", "[TELEMETRIE]");
    }

    private String exportDownload(File source, String prefix, String suffix, String label) {
        if (!source.isFile() || source.length() == 0) return label + " Keine Daten erzeugt.";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return label + " Automatischer MediaStore-Export benötigt Android 10 oder neuer.";
        }
        String name = prefix + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT)
                .format(new Date()) + suffix;
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("MediaStore insert lieferte keine URI");
            try (InputStream in = new java.io.FileInputStream(source);
                 OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IOException("MediaStore-Ausgabestream nicht verfügbar");
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                out.flush();
            }
            ContentValues complete = new ContentValues();
            complete.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (getContentResolver().update(uri, complete, null, null) < 1)
                throw new IOException("MediaStore-Datei konnte nicht freigegeben werden");
            return label + " EXPORT OK: Download/" + name + " (" + source.length() + " Bytes)";
        } catch (Exception error) {
            if (uri != null) try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) { }
            return label + " EXPORT ERROR: " + error + " (interne Datei bleibt erhalten: " + source.getPath() + ")";
        }
    }

}
