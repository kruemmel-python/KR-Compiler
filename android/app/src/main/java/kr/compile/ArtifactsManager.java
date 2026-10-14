package kr.compile;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ArtifactsManager {

    public static final class ArtifactItem {
        public final String name;
        public final File file;
        public final long sizeBytes;
        public final long lastModified;
        public final boolean isApk;
        public final boolean isWindowsExe;
        public final boolean isLinuxElf;

        public ArtifactItem(File file) {
            this.file = file;
            this.name = file.getName();
            this.sizeBytes = file.length();
            this.lastModified = file.lastModified();
            String lower = name.toLowerCase(Locale.ROOT);
            this.isApk = lower.endsWith(".apk");
            this.isWindowsExe = lower.endsWith(".exe");
            this.isLinuxElf = name.equals("programm") || (!name.contains(".") && !isApk && !isWindowsExe);
        }

        public String getReadableSize() {
            if (sizeBytes < 1024) return sizeBytes + " B";
            if (sizeBytes < 1024 * 1024) return String.format(Locale.GERMANY, "%.1f KB", sizeBytes / 1024.0);
            return String.format(Locale.GERMANY, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
        }

        public String getPlatformDescription() {
            if (isApk) return "Android ARM64 Runner APK (Direkt installierbar)";
            if (isWindowsExe) return "Windows x64 Executable (Desktop)";
            if (isLinuxElf) return "Linux x64 ELF Binary (Desktop)";
            return "Build-Artefakt";
        }
    }

    public static List<ArtifactItem> getArtifacts(Context context) {
        List<ArtifactItem> list = new ArrayList<>();
        // 1. Check active workspace build dir
        File wsBuild = CompilerEngine.getActiveWorkspaceOutput(context);
        collectFromDir(wsBuild, list);

        // 2. Check local project KR_BUILD dir if available
        File localProject = ProjectManager.getInstance().getActiveLocalDir();
        if (localProject != null) {
            File localBuild = new File(localProject, "KR_BUILD");
            if (localBuild.isDirectory()) {
                collectFromDir(localBuild, list);
            }
        }

        // Sort: APK first, then EXE, then Linux, then others
        Collections.sort(list, (a, b) -> {
            if (a.isApk && !b.isApk) return -1;
            if (!a.isApk && b.isApk) return 1;
            return a.name.compareToIgnoreCase(b.name);
        });
        return list;
    }

    private static void collectFromDir(File dir, List<ArtifactItem> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && f.length() > 0) {
                // Deduplicate by name
                boolean exists = false;
                for (ArtifactItem item : out) {
                    if (item.name.equals(f.getName())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    out.add(new ArtifactItem(f));
                }
            }
        }
    }

    public static void installApk(Context context, ArtifactItem item) {
        if (!item.isApk) return;
        try {
            Uri contentUri = KrFileProvider.getUriForFile(context, item.file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Installation konnte nicht gestartet werden: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void shareFile(Context context, ArtifactItem item) {
        try {
            Uri contentUri = KrFileProvider.getUriForFile(context, item.file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(item.isApk ? "application/vnd.android.package-archive" : "application/octet-stream");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Artefakt teilen: " + item.name));
        } catch (Exception e) {
            Toast.makeText(context, "Teilen fehlgeschlagen: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
