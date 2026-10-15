package kr.compile;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Device-side end-to-end runner for all Rust/Go target combinations. */
public final class LanguageBuildInstrumentation extends Instrumentation {
    private String onlyLanguage;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        onlyLanguage = arguments == null ? null : arguments.getString("language");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            buildAll();
            result.putString("result", "12 Sprach-Builds erfolgreich");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("result", "FEHLER: " + error);
            result.putString("stack", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void buildAll() throws Exception {
        Context context = getTargetContext();
        File resultRoot = new File(context.getExternalFilesDir(null), "language-build-tests");
        delete(resultRoot);
        if (!resultRoot.mkdirs()) throw new IOException("Ergebnisordner kann nicht erstellt werden.");
        for (LanguageProjectManager.Language language : LanguageProjectManager.Language.values()) {
            if (onlyLanguage != null && !language.name().equalsIgnoreCase(onlyLanguage)) continue;
            for (boolean gui : new boolean[]{false, true}) {
                String projectName = "E2E-" + language.name() + (gui ? "-GUI" : "-CONSOLE");
                File existing = new File(new File(context.getFilesDir(), "projects"), projectName);
                delete(existing);
                File project = LanguageProjectManager.create(context, language, gui, projectName);
                for (CompilerEngine.Target target : CompilerEngine.Target.values()) {
                    StringBuilder log = new StringBuilder();
                    CompilerEngine engine = new CompilerEngine(context, null, project, target, true,
                            line -> log.append(line).append('\n'));
                    String prefix = language.name().toLowerCase() + "-" + (gui ? "gui" : "console") + "-" + target.name().toLowerCase();
                    String summary;
                    try {
                        summary = engine.build();
                    } catch (Exception error) {
                        write(new File(resultRoot, prefix + ".failure.log"), log.toString() + "\n" + error + "\n");
                        throw error;
                    }
                    if (!summary.startsWith("Fertig:")) throw new IOException(summary);
                    File build = new File(project, "KR_BUILD");
                    File artifact = new File(build, target == CompilerEngine.Target.ANDROID
                            ? "programm-android-arm64.apk" : target == CompilerEngine.Target.WINDOWS ? "programm.exe" : "programm");
                    if (!artifact.isFile() || artifact.length() == 0)
                        throw new IOException("Artefakt fehlt: " + artifact + "\n" + log);
                    File destination = new File(resultRoot, prefix
                            + (target == CompilerEngine.Target.ANDROID ? ".apk" : target == CompilerEngine.Target.WINDOWS ? ".exe" : ".elf"));
                    copy(artifact, destination);
                    copy(new File(build, "build.log"), new File(resultRoot, destination.getName() + ".log"));
                }
            }
        }
    }

    private static void copy(File source, File destination) throws Exception {
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[128 * 1024]; int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        }
    }

    private static void write(File destination, String text) throws Exception {
        try (OutputStream out = new FileOutputStream(destination)) {
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles(); if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
