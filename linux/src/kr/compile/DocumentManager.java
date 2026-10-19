package kr.compile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DocumentManager {
    public interface Listener {
        void onDocumentOpened(DocumentModel doc);
        void onDocumentClosed(DocumentModel doc);
        void onActiveDocumentChanged(DocumentModel doc);
        void onDocumentModified(DocumentModel doc);
        void onDocumentSaved(DocumentModel doc);
    }

    private static DocumentManager instance;
    private final List<DocumentModel> openDocs = new ArrayList<>();
    private DocumentModel activeDoc;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public static synchronized DocumentManager getInstance() {
        if (instance == null) instance = new DocumentManager();
        return instance;
    }

    private DocumentManager() {}

    public void addListener(Listener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public List<DocumentModel> getOpenDocuments() { return Collections.unmodifiableList(openDocs); }
    public DocumentModel getActiveDocument() { return activeDoc; }

    public DocumentModel openDocument(File root, File file) throws IOException {
        for (DocumentModel doc : openDocs) {
            if (doc.getFile().equals(file)) {
                setActiveDocument(doc);
                return doc;
            }
        }
        DocumentModel doc = DocumentModel.load(root, file);
        openDocs.add(doc);
        for (Listener l : listeners) l.onDocumentOpened(doc);
        setActiveDocument(doc);
        return doc;
    }

    public void closeDocument(DocumentModel doc) {
        if (doc == null || !openDocs.contains(doc)) return;
        int idx = openDocs.indexOf(doc);
        openDocs.remove(doc);
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

    public void setActiveDocument(DocumentModel doc) {
        if (this.activeDoc != doc) {
            this.activeDoc = doc;
            for (Listener l : listeners) l.onActiveDocumentChanged(doc);
        }
    }

    public void notifyModified(DocumentModel doc) {
        for (Listener l : listeners) l.onDocumentModified(doc);
    }

    public void saveActiveDocument() throws IOException {
        if (activeDoc != null && activeDoc.isModified()) {
            activeDoc.save();
            for (Listener l : listeners) l.onDocumentSaved(activeDoc);
        }
    }

    public void saveAll() throws IOException {
        for (DocumentModel doc : openDocs) {
            if (doc.isModified()) {
                doc.save();
                for (Listener l : listeners) l.onDocumentSaved(doc);
            }
        }
    }

    public void reloadIfOpen(String relativePath, String newContent) {
        if (relativePath == null) return;
        String normTarget = relativePath.replace('\\', '/').replaceAll("^/+", "");
        for (DocumentModel doc : openDocs) {
            String normDoc = doc.getRelativePath().replace('\\', '/').replaceAll("^/+", "");
            if (normDoc.equals(normTarget)) {
                doc.setContent(newContent);
                try {
                    doc.save();
                } catch (IOException ignored) {}
                for (Listener l : listeners) l.onDocumentModified(doc);
                break;
            }
        }
    }

    public void closeAll() {
        List<DocumentModel> copy = new ArrayList<>(openDocs);
        for (DocumentModel doc : copy) closeDocument(doc);
    }
}
