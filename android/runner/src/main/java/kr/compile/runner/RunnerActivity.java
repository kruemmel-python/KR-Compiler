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
                shown = content + "\n[Programm beendet: " + code + "]\n";
            } catch (Throwable error) { shown = "Programmfehler: " + error + "\n"; }
            running.set(false);
            String result = shown; runOnUiThread(() -> text.setText(result));
        }, "kr-cpp-main").start();
    }

}
