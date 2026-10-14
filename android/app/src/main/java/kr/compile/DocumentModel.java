package kr.compile;

import android.net.Uri;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

public final class DocumentModel {
    public static final class EditAction {
        public final int start;
        public final String oldText;
        public final String newText;

        public EditAction(int start, String oldText, String newText) {
            this.start = start;
            this.oldText = oldText;
            this.newText = newText;
        }
    }

    private final String relativePath;
    private final String displayName;
    private final Uri safUri;
    private final File localFile;

    private String text;
    private String savedText;
    private boolean isModified = false;
    private int cursorPosition = 0;
    private int selectionEnd = 0;
    private int scrollY = 0;
    private int scrollX = 0;

    private final Deque<EditAction> undoStack = new ArrayDeque<>();
    private final Deque<EditAction> redoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 100;

    public DocumentModel(String relativePath, String text, Uri safUri, File localFile) {
        this.relativePath = relativePath;
        String name = relativePath;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        this.displayName = name;
        this.text = text != null ? text : "";
        this.savedText = this.text;
        this.safUri = safUri;
        this.localFile = localFile;
        this.isModified = false;
    }

    public String getRelativePath() { return relativePath; }
    public String getDisplayName() { return displayName; }
    public Uri getSafUri() { return safUri; }
    public File getLocalFile() { return localFile; }
    public String getText() { return text; }
    public boolean isModified() { return isModified; }
    public int getCursorPosition() { return cursorPosition; }
    public int getSelectionEnd() { return selectionEnd; }
    public int getScrollY() { return scrollY; }
    public int getScrollX() { return scrollX; }

    public void setCursor(int start, int end) {
        this.cursorPosition = Math.max(0, start);
        this.selectionEnd = Math.max(0, end);
    }

    public void setScroll(int x, int y) {
        this.scrollX = Math.max(0, x);
        this.scrollY = Math.max(0, y);
    }

    public void onTextChanged(String newText, int start, String oldSub, String newSub) {
        this.text = newText != null ? newText : "";
        this.isModified = !this.text.equals(this.savedText);

        if (oldSub != null || newSub != null) {
            undoStack.push(new EditAction(start, oldSub != null ? oldSub : "", newSub != null ? newSub : ""));
            if (undoStack.size() > MAX_UNDO) {
                undoStack.removeLast();
            }
            redoStack.clear();
        }
    }

    public void markSaved(String savedText) {
        this.savedText = savedText;
        this.text = savedText;
        this.isModified = false;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public EditAction popUndo() {
        if (undoStack.isEmpty()) return null;
        EditAction action = undoStack.pop();
        redoStack.push(action);
        return action;
    }

    public EditAction popRedo() {
        if (redoStack.isEmpty()) return null;
        EditAction action = redoStack.pop();
        undoStack.push(action);
        return action;
    }
}
