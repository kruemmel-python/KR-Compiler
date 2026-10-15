package kr.compile.runner;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class RunnerFiles {
    private RunnerFiles() {}

    static File prepareProjectFiles(Context context) throws Exception {
        File project = new File(context.getFilesDir(), "project");
        deleteTree(project);
        if (!project.mkdirs()) throw new IllegalStateException("Laufzeitordner kann nicht erstellt werden.");
        String root = project.getCanonicalPath() + File.separator;
        try (InputStream raw = context.getAssets().open("kr-project-data.zip");
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
