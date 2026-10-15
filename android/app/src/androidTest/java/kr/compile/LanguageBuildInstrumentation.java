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
    private String onlyTarget;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        onlyLanguage = arguments == null ? null : arguments.getString("language");
        onlyTarget = arguments == null ? null : arguments.getString("target");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            int buildCount = buildAll();
            result.putString("result", buildCount + " Sprach-Builds erfolgreich");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("result", "FEHLER: " + error);
            result.putString("stack", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static final class TestJob {
        final String name;
        final boolean isGui;
        final LanguageProjectManager.KrTemplate krTemplate;
        final String customKrCode;
        final LanguageProjectManager.Language legacyLanguage;

        TestJob(String name, LanguageProjectManager.KrTemplate template) {
            this.name = name;
            this.isGui = template.isGui;
            this.krTemplate = template;
            this.customKrCode = null;
            this.legacyLanguage = null;
        }

        TestJob(String name, boolean isGui, String customKrCode) {
            this.name = name;
            this.isGui = isGui;
            this.krTemplate = null;
            this.customKrCode = customKrCode;
            this.legacyLanguage = null;
        }

        TestJob(String name, boolean isGui, LanguageProjectManager.Language legacyLanguage) {
            this.name = name;
            this.isGui = isGui;
            this.krTemplate = null;
            this.customKrCode = null;
            this.legacyLanguage = legacyLanguage;
        }
        TestJob(String name) {
            this.name = name;
            this.isGui = false;
            this.krTemplate = null;
            this.customKrCode = null;
            this.legacyLanguage = null;
        }
    }

    private int buildAll() throws Exception {
        Context context = getTargetContext();
        File resultRoot = new File(context.getExternalFilesDir(null), "language-build-tests");
        if (onlyLanguage == null && onlyTarget == null) {
            delete(resultRoot);
        }
        if (!resultRoot.isDirectory() && !resultRoot.mkdirs()) throw new IOException("Ergebnisordner kann nicht erstellt werden.");

        java.util.List<TestJob> jobs = new java.util.ArrayList<>();
        String mode = onlyLanguage == null ? "KR" : onlyLanguage.toUpperCase(java.util.Locale.ROOT);

        if (mode.equals("RUST") || mode.equals("ALL_LEGACY")) {
            jobs.add(new TestJob("rust-console", false, LanguageProjectManager.Language.RUST));
            jobs.add(new TestJob("rust-gui", true, LanguageProjectManager.Language.RUST));
        }
        if (mode.equals("GO") || mode.equals("ALL_LEGACY")) {
            jobs.add(new TestJob("go-console", false, LanguageProjectManager.Language.GO));
            jobs.add(new TestJob("go-gui", true, LanguageProjectManager.Language.GO));
        }

        if (mode.equals("KR_FUSION_GUI")) {
            jobs.add(new TestJob("kr-fusion-gui", LanguageProjectManager.KrTemplate.KR_FUSION_GUI));
        } else if (mode.equals("KR_FUSION_CONSOLE")) {
            jobs.add(new TestJob("kr-fusion-console", LanguageProjectManager.KrTemplate.KR_FUSION_CONSOLE));
        } else if (mode.equals("KR_CONSOLE")) {
            jobs.add(new TestJob("kr-console", LanguageProjectManager.KrTemplate.KR_CONSOLE));
        } else if (mode.equals("KR_GUI")) {
            jobs.add(new TestJob("kr-gui", LanguageProjectManager.KrTemplate.KR_GUI));
        } else if (mode.equals("KR_RUST")) {
            jobs.add(new TestJob("kr-rust", false, "fn main() {\n    println!(\"Hallo von reinem Rust in .kr!\");\n}\n"));
        } else if (mode.equals("KR_GO")) {
            jobs.add(new TestJob("kr-go", false, "package main\n\nimport \"fmt\"\n\nfunc main() {\n    fmt.Println(\"Hallo von reinem Go in .kr!\")\n}\n"));
        } else if (mode.equals("CC_OPENCL")) {
            jobs.add(new TestJob("cc_opencl"));
        } else if (mode.equals("KR") || mode.equals("ALL") || mode.equals("KR_FUSION")) {
            if (!mode.equals("KR_FUSION")) {
                jobs.add(new TestJob("kr-console", LanguageProjectManager.KrTemplate.KR_CONSOLE));
                jobs.add(new TestJob("kr-gui", LanguageProjectManager.KrTemplate.KR_GUI));
                jobs.add(new TestJob("kr-rust", false, "fn main() {\n    println!(\"Hallo von reinem Rust in .kr!\");\n}\n"));
                jobs.add(new TestJob("kr-go", false, "package main\n\nimport \"fmt\"\n\nfunc main() {\n    fmt.Println(\"Hallo von reinem Go in .kr!\")\n}\n"));
            }
            jobs.add(new TestJob("kr-fusion-console", LanguageProjectManager.KrTemplate.KR_FUSION_CONSOLE));
            jobs.add(new TestJob("kr-fusion-gui", LanguageProjectManager.KrTemplate.KR_FUSION_GUI));
        }

        int successCount = 0;
        for (TestJob job : jobs) {
            String projectName = "E2E-" + job.name.toUpperCase(java.util.Locale.ROOT);
            File existing = new File(new File(context.getFilesDir(), "projects"), projectName);
            delete(existing);

            File project;
            if (job.krTemplate != null) {
                project = LanguageProjectManager.createKr(context, job.krTemplate, projectName);
            } else if (job.customKrCode != null) {
                File projects = new File(context.getFilesDir(), "projects");
                project = new File(projects, projectName);
                if (!project.mkdirs()) throw new IOException("Projektordner kann nicht erstellt werden: " + projectName);
                write(new File(project, "main.kr"), job.customKrCode);
                if (job.isGui) write(new File(project, "kr-gui-target.txt"), "sdl3\n");
            } else if (job.name.equals("cc_opencl")) {
                File projects = new File(context.getFilesDir(), "projects");
                project = new File(projects, "CC_OpenCl_Enterprise");
                delete(project);
                File externalProject = new File(context.getExternalFilesDir(null), "CC_OpenCl_Enterprise");
                copyDir(externalProject, project);
            } else {
                project = LanguageProjectManager.create(context, job.legacyLanguage, job.isGui, projectName);
            }

            for (CompilerEngine.Target target : CompilerEngine.Target.values()) {
                if (onlyTarget != null && !target.name().equalsIgnoreCase(onlyTarget)) {
                    continue;
                }
                StringBuilder log = new StringBuilder();
                CompilerEngine engine = new CompilerEngine(context, null, project, target, true,
                        line -> log.append(line).append('\n'));
                String prefix = job.name.toLowerCase(java.util.Locale.ROOT) + "-" + target.name().toLowerCase(java.util.Locale.ROOT);
                String summary;
                try {
                    summary = engine.build();
                } catch (Exception error) {
                    write(new File(resultRoot, prefix + ".failure.log"), log.toString() + "\n" + error + "\n");
                    File goArchive = new File(new File(project.getParentFile(), "go-target"), "libkr_go_" + target.name().toLowerCase(java.util.Locale.ROOT) + (target == CompilerEngine.Target.ANDROID ? ".so" : ".a"));
                    if (goArchive.isFile()) {
                        try { copy(goArchive, new File(resultRoot, prefix + "-" + goArchive.getName())); } catch (Exception ignored) {}
                    }
                    File krGen = new File(project.getParentFile(), "kr-generated");
                    if (krGen.isDirectory()) {
                        copyDir(krGen, new File(resultRoot, prefix + "-kr-gen"));
                    }
                    throw error;
                }
                if (!summary.startsWith("Fertig:")) throw new IOException(summary);
                File build = new File(project, "KR_BUILD");
                File artifact = new File(build, target == CompilerEngine.Target.ANDROID
                        ? "programm-android-arm64.apk" : target == CompilerEngine.Target.WINDOWS ? "programm.exe" : "programm");
                if (job.name.equals("cc_opencl")) {
                    File dll = new File(build, "CC_OpenCl.dll");
                    if (!dll.isFile()) dll = new File(new File(context.getCacheDir(), "active-project/cmake/build/bin"), "CC_OpenCl.dll");
                    if (!dll.isFile()) dll = new File(new File(context.getCacheDir(), "active-project/cmake/artifacts"), "CC_OpenCl.dll");
                    if (dll.isFile()) {
                        try { copy(dll, new File(resultRoot, "CC_OpenCl.dll")); } catch (Exception ignored) {}
                        try { copy(dll, new File("/sdcard/Download/CC_OpenCl_Enterprise/CC_OpenCl.dll")); } catch (Exception ignored) {}
                    }
                    File implib = new File(build, "libCC_OpenCl.dll.a");
                    if (!implib.isFile()) implib = new File(new File(context.getCacheDir(), "active-project/cmake/build/lib"), "libCC_OpenCl.dll.a");
                    if (!implib.isFile()) implib = new File(new File(context.getCacheDir(), "active-project/cmake/artifacts"), "libCC_OpenCl.dll.a");
                    if (implib.isFile()) {
                        try { copy(implib, new File(resultRoot, "libCC_OpenCl.dll.a")); } catch (Exception ignored) {}
                        try { copy(implib, new File("/sdcard/Download/CC_OpenCl_Enterprise/libCC_OpenCl.dll.a")); } catch (Exception ignored) {}
                    }
                    File testExe = new File(build, "programm.exe");
                    if (!testExe.isFile()) testExe = new File(new File(context.getCacheDir(), "active-project/cmake/build/bin"), "test_cc_opencl.exe");
                    if (testExe.isFile()) {
                        try { copy(testExe, new File(resultRoot, "test_cc_opencl.exe")); } catch (Exception ignored) {}
                        try { copy(testExe, new File("/sdcard/Download/CC_OpenCl_Enterprise/test_cc_opencl.exe")); } catch (Exception ignored) {}
                    }
                }
                if (!artifact.isFile() || artifact.length() == 0)
                    throw new IOException("Artefakt fehlt: " + artifact + "\n" + log);
                File destination = new File(resultRoot, prefix
                        + (target == CompilerEngine.Target.ANDROID ? ".apk" : target == CompilerEngine.Target.WINDOWS ? ".exe" : ".elf"));
                copy(artifact, destination);
                copy(new File(build, "build.log"), new File(resultRoot, destination.getName() + ".log"));
                successCount++;
            }
        }
        return successCount;
    }

    private static void copyDir(File src, File dst) {
        if (src == null || !src.exists()) return;
        if (src.isDirectory()) {
            dst.mkdirs();
            File[] files = src.listFiles();
            if (files != null) for (File f : files) copyDir(f, new File(dst, f.getName()));
        } else {
            try { copy(src, dst); } catch (Exception ignored) {}
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
