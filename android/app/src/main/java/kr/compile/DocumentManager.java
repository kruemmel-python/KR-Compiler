package kr.compile;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DocumentManager {
    public interface Listener {
        void onDocumentOpened(DocumentModel doc);
        void onDocumentClosed(DocumentModel doc);
        void onActiveDocumentChanged(DocumentModel doc);
        void onDocumentModified(DocumentModel doc);
    }

    private static final DocumentManager INSTANCE = new DocumentManager();
    public static DocumentManager getInstance() { return INSTANCE; }

    private final List<DocumentModel> openDocs = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private DocumentModel activeDoc = null;

    private DocumentManager() {}

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public List<DocumentModel> getOpenDocuments() {
        return new ArrayList<>(openDocs);
    }

    public DocumentModel getActiveDocument() {
        return activeDoc;
    }

    public DocumentModel findByRelativePath(String path) {
        if (path == null) return null;
        for (DocumentModel doc : openDocs) {
            if (doc.getRelativePath().equals(path) || doc.getDisplayName().equals(path)) {
                return doc;
            }
        }
        return null;
    }

    public void reloadIfOpen(String relativePath, String newContent) {
        if (relativePath == null || newContent == null) return;
        DocumentModel doc = findByRelativePath(relativePath);
        if (doc != null) {
            doc.markSaved(newContent);
            notifyModified(doc);
        }
    }

    public DocumentModel openDocument(String relativePath, String content, Uri safUri, File localFile) {
        DocumentModel existing = findByRelativePath(relativePath);
        if (existing != null) {
            setActiveDocument(existing);
            return existing;
        }

        DocumentModel doc = new DocumentModel(relativePath, content, safUri, localFile);
        openDocs.add(doc);
        for (Listener l : listeners) l.onDocumentOpened(doc);
        setActiveDocument(doc);
        return doc;
    }

    public void setActiveDocument(DocumentModel doc) {
        if (this.activeDoc != doc) {
            this.activeDoc = doc;
            for (Listener l : listeners) l.onActiveDocumentChanged(doc);
        }
    }

    public void closeDocument(DocumentModel doc) {
        if (doc == null) return;
        int idx = openDocs.indexOf(doc);
        if (idx >= 0) {
            openDocs.remove(idx);
            for (Listener l : listeners) l.onDocumentClosed(doc);
            if (activeDoc == doc) {
                if (!openDocs.isEmpty()) {
                    int nextIdx = Math.min(idx, openDocs.size() - 1);
                    setActiveDocument(openDocs.get(nextIdx));
                } else {
                    setActiveDocument(null);
                }
            }
        }
    }

    public void notifyModified(DocumentModel doc) {
        for (Listener l : listeners) l.onDocumentModified(doc);
    }

    public boolean hasUnsavedChanges() {
        for (DocumentModel doc : openDocs) {
            if (doc.isModified()) return true;
        }
        return false;
    }

    public boolean saveDocument(Context context, DocumentModel doc) {
        if (doc == null) return false;
        try {
            byte[] bytes = doc.getText().getBytes(StandardCharsets.UTF_8);
            if (doc.getSafUri() != null) {
                try (OutputStream out = context.getContentResolver().openOutputStream(doc.getSafUri(), "wt")) {
                    if (out == null) return false;
                    out.write(bytes);
                }
            } else if (doc.getLocalFile() != null) {
                File f = doc.getLocalFile();
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    out.write(bytes);
                }
            }
            doc.markSaved(doc.getText());
            notifyModified(doc);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void saveAll(Context context) {
        for (DocumentModel doc : openDocs) {
            if (doc.isModified()) {
                saveDocument(context, doc);
            }
        }
    }
}
