package kr.compile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class KrSegment {
    public final KrLanguage language;
    public final String source;
    public final String file;
    public final int startLine;
    public final int startCol;
    public final int endLine;
    public final int endCol;
    public final int startOffset;
    public final int endOffset;

    public final List<KrFunction> declaredFunctions = new ArrayList<>();
    public final Set<String> calledFunctionNames = new HashSet<>();

    public KrSegment(KrLanguage language, String source, String file,
                     int startLine, int startCol, int endLine, int endCol,
                     int startOffset, int endOffset) {
        this.language = language;
        this.source = source != null ? source : "";
        this.file = file;
        this.startLine = startLine;
        this.startCol = startCol;
        this.endLine = endLine;
        this.endCol = endCol;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    @Override
    public String toString() {
        return language + " [" + startLine + ":" + startCol + " -> " + endLine + ":" + endCol + "] (" + declaredFunctions.size() + " fn)";
    }
}
