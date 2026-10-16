package kr.compile;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

final class DocumentTree {
    private DocumentTree() {}

    static String displayName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }

    static void importProject(Context context, Uri tree, File destination) throws IOException {
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);
        copyDirectoryFromProvider(context, tree, root, destination);
    }

    private static void copyDirectoryFromProvider(Context context, Uri tree, Uri directory, File out) throws IOException {
        if (!out.isDirectory() && !out.mkdirs()) throw new IOException("Arbeitsordner kann nicht erstellt werden: " + out);
        String id = DocumentsContract.getDocumentId(directory);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor c = context.getContentResolver().query(children, columns, null, null, null)) {
            if (c == null) throw new IOException("Projektordner ist nicht lesbar.");
            while (c.moveToNext()) {
                String childId = c.getString(0), name = safeName(c.getString(1)), mime = c.getString(2);
                if (name.equals("KR_BUILD") || name.equals(".git") || name.equals(".gradle") || name.equals(".idea")
                        || name.equals("build") || name.equals("build_vs") || name.equals("node_modules")
                        || name.startsWith("cmake-build-")) continue;
                Uri child = DocumentsContract.buildDocumentUriUsingTree(tree, childId);
                File target = new File(out, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) copyDirectoryFromProvider(context, tree, child, target);
                else try (InputStream in = context.getContentResolver().openInputStream(child);
                          OutputStream stream = new FileOutputStream(target)) {
                    if (in == null) throw new IOException("Datei nicht lesbar: " + name);
                    transfer(in, stream);
                }
            }
        }
    }

    static void exportBuild(Context context, Uri tree, File source) throws IOException {
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);
        Uri build = findChild(context, tree, root, "KR_BUILD");
        if (build == null) build = DocumentsContract.createDocument(context.getContentResolver(), root,
                DocumentsContract.Document.MIME_TYPE_DIR, "KR_BUILD");
        if (build == null) throw new IOException("KR_BUILD kann nicht angelegt werden.");
        exportDirectory(context, tree, build, source);
    }

    private static void exportDirectory(Context context, Uri tree, Uri parent, File source) throws IOException {
        File[] files = source.listFiles(); if (files == null) return;
        for (File file : files) {
            Uri old = findChild(context, tree, parent, file.getName());
            if (old != null) DocumentsContract.deleteDocument(context.getContentResolver(), old);
            if (file.isDirectory()) {
                Uri dir = DocumentsContract.createDocument(context.getContentResolver(), parent,
                        DocumentsContract.Document.MIME_TYPE_DIR, file.getName());
                if (dir == null) throw new IOException("Ausgabeordner kann nicht erstellt werden: " + file.getName());
                exportDirectory(context, tree, dir, file);
            } else {
                Uri doc = DocumentsContract.createDocument(context.getContentResolver(), parent,
                        mime(file.getName()), file.getName());
                if (doc == null) throw new IOException("Ausgabedatei kann nicht erstellt werden: " + file.getName());
                try (InputStream in = new FileInputStream(file); OutputStream out = context.getContentResolver().openOutputStream(doc, "w")) {
                    if (out == null) throw new IOException("Ausgabedatei nicht schreibbar: " + file.getName());
                    transfer(in, out);
                }
            }
        }
    }

    private static Uri findChild(Context context, Uri tree, Uri parent, String wanted) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent));
        try (Cursor c = context.getContentResolver().query(children,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            while (c != null && c.moveToNext()) if (wanted.equals(c.getString(1)))
                return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
        } catch (Exception ignored) {}
        return null;
    }

    private static String safeName(String name) throws IOException {
        if (name == null || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\"))
            throw new IOException("Ungültiger Dateiname im Projekt.");
        return name;
    }
    private static String mime(String name) {
        if (name.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".txt") || name.endsWith(".log")) return "text/plain";
        return "application/octet-stream";
    }
    private static void transfer(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[64 * 1024]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n);
    }
}
