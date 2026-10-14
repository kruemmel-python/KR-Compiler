package kr.compile;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProjectManager {

    public static final class FileNode {
        public final String name;
        public final String relativePath;
        public final boolean isDirectory;
        public final Uri uri;
        public final File localFile;
        public final List<FileNode> children = new ArrayList<>();
        public boolean isExpanded = false;
        public int depth = 0;

        public FileNode(String name, String relativePath, boolean isDirectory, Uri uri, File localFile) {
            this.name = name;
            this.relativePath = relativePath;
            this.isDirectory = isDirectory;
            this.uri = uri;
            this.localFile = localFile;
        }
    }

    private static final ProjectManager INSTANCE = new ProjectManager();
    public static ProjectManager getInstance() { return INSTANCE; }

    private Uri activeSafTreeUri = null;
    private File activeLocalDir = null;
    private String activeProjectName = "Kein Projekt";
    private FileNode rootNode = null;

    private static final Set<String> BINARY_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".apk", ".exe", ".so", ".dll", ".o", ".obj", ".a", ".lib", ".zip", ".tar", ".gz",
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".pdf", ".docx", ".pptx", ".class", ".jar"
    ));

    private ProjectManager() {}

    public Uri getActiveSafTreeUri() { return activeSafTreeUri; }
    public File getActiveLocalDir() { return activeLocalDir; }
    public String getActiveProjectName() { return activeProjectName; }
    public FileNode getRootNode() { return rootNode; }

    public void setProject(Context context, Uri safUri, File localDir, String name) {
        this.activeSafTreeUri = safUri;
        this.activeLocalDir = localDir;
        this.activeProjectName = name != null ? name : (safUri != null ? DocumentTree.displayName(context, safUri) : (localDir != null ? localDir.getName() : "Projekt"));

        // Save in recent projects
        saveRecentProject(context, safUri != null ? safUri.toString() : (localDir != null ? "file:" + localDir.getAbsolutePath() : null), activeProjectName);
        refreshTree(context);
    }

    public static boolean isBinaryFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot >= 0) {
            String ext = lower.substring(dot);
            if (BINARY_EXTENSIONS.contains(ext)) return true;
        }
        return false;
    }

    public void refreshTree(Context context) {
        if (activeLocalDir != null && activeLocalDir.isDirectory()) {
            rootNode = buildLocalTree(activeLocalDir, activeLocalDir, 0);
        } else if (activeSafTreeUri != null) {
            rootNode = buildSafTree(context, activeSafTreeUri);
        } else {
            rootNode = null;
        }
    }

    private FileNode buildLocalTree(File root, File current, int depth) {
        String relPath = root.toPath().relativize(current.toPath()).toString().replace('\\', '/');
        if (relPath.isEmpty()) relPath = current.getName();
        FileNode node = new FileNode(current.getName(), relPath, current.isDirectory(), null, current);
        node.depth = depth;
        node.isExpanded = depth == 0; // expand root by default

        if (current.isDirectory()) {
            File[] files = current.listFiles();
            if (files != null) {
                Arrays.sort(files, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for (File f : files) {
                    if (f.getName().equals("KR_BUILD") || f.getName().equals(".git") || f.getName().equals(".gradle")) continue;
                    node.children.add(buildLocalTree(root, f, depth + 1));
                }
            }
        }
        return node;
    }

    private FileNode buildSafTree(Context context, Uri treeUri) {
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri rootDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
        String name = DocumentTree.displayName(context, treeUri);
        FileNode root = new FileNode(name, name, true, rootDoc, null);
        root.isExpanded = true;
        root.depth = 0;
        populateSafChildren(context, treeUri, rootDoc, root, "", 1);
        return root;
    }

    private void populateSafChildren(Context context, Uri tree, Uri parentDoc, FileNode parentNode, String parentPath, int depth) {
        String id = DocumentsContract.getDocumentId(parentDoc);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        String[] cols = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        try (Cursor c = context.getContentResolver().query(children, cols, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext()) {
                String childId = c.getString(0);
                String childName = c.getString(1);
                String mime = c.getString(2);
                if ("KR_BUILD".equals(childName) || ".git".equals(childName)) continue;

                boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                String relPath = parentPath.isEmpty() ? childName : parentPath + "/" + childName;
                Uri childUri = DocumentsContract.buildDocumentUriUsingTree(tree, childId);

                FileNode childNode = new FileNode(childName, relPath, isDir, childUri, null);
                childNode.depth = depth;
                parentNode.children.add(childNode);

                if (isDir) {
                    populateSafChildren(context, tree, childUri, childNode, relPath, depth + 1);
                }
            }
        } catch (Exception ignored) {}

        Collections.sort(parentNode.children, (a, b) -> {
            if (a.isDirectory && !b.isDirectory) return -1;
            if (!a.isDirectory && b.isDirectory) return 1;
            return a.name.compareToIgnoreCase(b.name);
        });
    }

    public String readFile(Context context, FileNode node) throws IOException {
        if (node.localFile != null) {
            byte[] bytes = new byte[(int) node.localFile.length()];
            try (FileInputStream in = new FileInputStream(node.localFile)) {
                int read = in.read(bytes);
                return new String(bytes, 0, Math.max(0, read), StandardCharsets.UTF_8);
            }
        } else if (node.uri != null) {
            try (InputStream in = context.getContentResolver().openInputStream(node.uri);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (in == null) throw new IOException("Datei nicht lesbar");
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }
        throw new IOException("Datei-Referenz fehlt");
    }

    public boolean createNewFile(Context context, FileNode parentDir, String fileName, String initialContent) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) return false;
        fileName = fileName.trim();

        if (activeLocalDir != null && activeLocalDir.isDirectory()) {
            File targetDir = parentDir != null && parentDir.localFile != null && parentDir.isDirectory ? parentDir.localFile : activeLocalDir;
            File newFile = new File(targetDir, fileName);
            if (newFile.exists()) throw new IOException("Datei existiert bereits: " + fileName);
            try (FileOutputStream out = new FileOutputStream(newFile)) {
                if (initialContent != null) {
                    out.write(initialContent.getBytes(StandardCharsets.UTF_8));
                }
            }
            refreshTree(context);
            return true;
        } else if (activeSafTreeUri != null) {
            Uri parentUri = parentDir != null && parentDir.uri != null && parentDir.isDirectory ? parentDir.uri
                    : DocumentsContract.buildDocumentUriUsingTree(activeSafTreeUri, DocumentsContract.getTreeDocumentId(activeSafTreeUri));
            Uri doc = DocumentsContract.createDocument(context.getContentResolver(), parentUri, "text/plain", fileName);
            if (doc == null) throw new IOException("Datei konnte nicht erstellt werden");
            if (initialContent != null) {
                try (OutputStream out = context.getContentResolver().openOutputStream(doc, "wt")) {
                    if (out != null) out.write(initialContent.getBytes(StandardCharsets.UTF_8));
                }
            }
            refreshTree(context);
            return true;
        }
        return false;
    }

    public boolean createNewFolder(Context context, FileNode parentDir, String folderName) throws IOException {
        if (folderName == null || folderName.trim().isEmpty()) return false;
        folderName = folderName.trim();

        if (activeLocalDir != null && activeLocalDir.isDirectory()) {
            File targetDir = parentDir != null && parentDir.localFile != null && parentDir.isDirectory ? parentDir.localFile : activeLocalDir;
            File newDir = new File(targetDir, folderName);
            if (newDir.exists()) throw new IOException("Ordner existiert bereits: " + folderName);
            if (!newDir.mkdirs()) throw new IOException("Ordner konnte nicht angelegt werden");
            refreshTree(context);
            return true;
        } else if (activeSafTreeUri != null) {
            Uri parentUri = parentDir != null && parentDir.uri != null && parentDir.isDirectory ? parentDir.uri
                    : DocumentsContract.buildDocumentUriUsingTree(activeSafTreeUri, DocumentsContract.getTreeDocumentId(activeSafTreeUri));
            Uri dir = DocumentsContract.createDocument(context.getContentResolver(), parentUri, DocumentsContract.Document.MIME_TYPE_DIR, folderName);
            if (dir == null) throw new IOException("Ordner konnte nicht angelegt werden");
            refreshTree(context);
            return true;
        }
        return false;
    }

    public boolean deleteNode(Context context, FileNode node) throws IOException {
        if (node == null) return false;
        if (node.localFile != null) {
            deleteLocalRecursive(node.localFile);
            refreshTree(context);
            return true;
        } else if (node.uri != null) {
            boolean ok = DocumentsContract.deleteDocument(context.getContentResolver(), node.uri);
            refreshTree(context);
            return ok;
        }
        return false;
    }

    private void deleteLocalRecursive(File f) {
        if (f.isDirectory()) {
            File[] c = f.listFiles();
            if (c != null) for (File sub : c) deleteLocalRecursive(sub);
        }
        f.delete();
    }

    public boolean renameNode(Context context, FileNode node, String newName) throws IOException {
        if (node == null || newName == null || newName.trim().isEmpty()) return false;
        newName = newName.trim();

        if (node.localFile != null) {
            File parent = node.localFile.getParentFile();
            File dest = new File(parent, newName);
            boolean ok = node.localFile.renameTo(dest);
            refreshTree(context);
            return ok;
        } else if (node.uri != null) {
            Uri renamed = DocumentsContract.renameDocument(context.getContentResolver(), node.uri, newName);
            refreshTree(context);
            return renamed != null;
        }
        return false;
    }

    // Recent Projects Management
    public static final class RecentProject {
        public final String uriOrPath;
        public final String name;
        public RecentProject(String uriOrPath, String name) {
            this.uriOrPath = uriOrPath;
            this.name = name;
        }
    }

    public static List<RecentProject> getRecentProjects(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("kr_ide_recent", Context.MODE_PRIVATE);
        String data = prefs.getString("list", "");
        List<RecentProject> result = new ArrayList<>();
        if (data.isEmpty()) return result;
        String[] entries = data.split(";");
        for (String entry : entries) {
            int pipe = entry.indexOf('|');
            if (pipe > 0) {
                String path = entry.substring(0, pipe);
                String name = entry.substring(pipe + 1);
                result.add(new RecentProject(path, name));
            }
        }
        return result;
    }

    private void saveRecentProject(Context context, String uriOrPath, String name) {
        if (uriOrPath == null) return;
        List<RecentProject> recents = getRecentProjects(context);
        List<RecentProject> updated = new ArrayList<>();
        updated.add(new RecentProject(uriOrPath, name));
        for (RecentProject rp : recents) {
            if (!rp.uriOrPath.equals(uriOrPath) && updated.size() < 10) {
                updated.add(rp);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < updated.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(updated.get(i).uriOrPath).append('|').append(updated.get(i).name);
        }
        context.getSharedPreferences("kr_ide_recent", Context.MODE_PRIVATE)
                .edit().putString("list", sb.toString()).apply();
    }
}
