package kr.compile;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ArtifactsManager {
    public static final class ArtifactItem {
        private final File file;
        private final String name;
        private final long size;
        private final long lastModified;
        private final boolean isExecutable;

        public ArtifactItem(File file) {
            this.file = file;
            this.name = file.getName();
            this.size = file.length();
            this.lastModified = file.lastModified();
            this.isExecutable = name.toLowerCase(Locale.ROOT).endsWith(".exe")
                    || name.toLowerCase(Locale.ROOT).endsWith(".bat")
                    || name.toLowerCase(Locale.ROOT).endsWith(".cmd");
        }

        public File getFile() { return file; }
        public String getName() { return name; }
        public long getSize() { return size; }
        public long getLastModified() { return lastModified; }
        public boolean isExecutable() { return isExecutable; }

        public String getFormattedSize() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
            return String.format(Locale.ROOT, "%.2f MB", size / (1024.0 * 1024.0));
        }
    }

    private static ArtifactsManager instance;

    public static synchronized ArtifactsManager getInstance() {
        if (instance == null) instance = new ArtifactsManager();
        return instance;
    }

    private ArtifactsManager() {}

    public List<ArtifactItem> getArtifacts(File projectDir) {
        List<ArtifactItem> list = new ArrayList<>();
        if (projectDir == null || !projectDir.isDirectory()) return list;

        File buildDir = new File(projectDir, "KR_BUILD");
        if (!buildDir.isDirectory()) return list;

        scan(buildDir, list);
        list.sort(Comparator.comparing(ArtifactItem::getName));
        return list;
    }

    private void scan(File dir, List<ArtifactItem> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scan(f, list);
            } else if (f.isFile()) {
                list.add(new ArtifactItem(f));
            }
        }
    }

    public void runArtifact(File exe) throws IOException {
        if (exe == null || !exe.isFile()) {
            throw new IOException("Ausführbare Datei existiert nicht: " + exe);
        }
        File runBat = new File(exe.getParentFile(), "start_" + exe.getName().replace(".exe", "") + ".bat");
        if (runBat.isFile()) {
            new ProcessBuilder("cmd.exe", "/c", "start", "KR-Programm", runBat.getAbsolutePath())
                    .directory(exe.getParentFile())
                    .start();
        } else {
            new ProcessBuilder("cmd.exe", "/c", "start", "KR-Programm", "cmd.exe", "/k", exe.getAbsolutePath())
                    .directory(exe.getParentFile())
                    .start();
        }
    }

    public void openInExplorer(File file) throws IOException {
        if (file == null) return;
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            File toOpen = file.isDirectory() ? file : file.getParentFile();
            if (toOpen != null && toOpen.exists()) {
                Desktop.getDesktop().open(toOpen);
                return;
            }
        }
        new ProcessBuilder("explorer.exe", "/select,", file.getAbsolutePath()).start();
    }
}
