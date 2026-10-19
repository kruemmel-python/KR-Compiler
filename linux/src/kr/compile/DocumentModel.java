package kr.compile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

public final class DocumentModel {
    private final File file;
    private final String relativePath;
    private String content;
    private String savedContent;
    private boolean modified;

    public DocumentModel(File file, String relativePath, String content) {
        this.file = file;
        this.relativePath = relativePath;
        this.content = content != null ? content : "";
        this.savedContent = this.content;
        this.modified = false;
    }

    public static DocumentModel load(File root, File file) throws IOException {
        String relative = root.toURI().relativize(file.toURI()).getPath();
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return new DocumentModel(file, relative, text);
    }

    public File getFile() { return file; }
    public String getRelativePath() { return relativePath; }
    public String getName() { return file.getName(); }

    public String getContent() { return content; }
    public void setContent(String newContent) {
        if (!Objects.equals(this.content, newContent)) {
            this.content = newContent != null ? newContent : "";
            this.modified = !Objects.equals(this.content, this.savedContent);
        }
    }

    public boolean isModified() { return modified; }

    public void save() throws IOException {
        if (file != null) {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            this.savedContent = this.content;
            this.modified = false;
        }
    }

    public void reload() throws IOException {
        if (file != null && file.isFile()) {
            this.content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            this.savedContent = this.content;
            this.modified = false;
        }
    }
}
