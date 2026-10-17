package kr.compile;

public enum KrLanguage {
    CPP("C++20", ".cpp"),
    RUST("Rust", ".rs"),
    GO("Go", ".go");

    private final String displayName;
    private final String fileExtension;

    KrLanguage(String displayName, String fileExtension) {
        this.displayName = displayName;
        this.fileExtension = fileExtension;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFileExtension() {
        return fileExtension;
    }
}
