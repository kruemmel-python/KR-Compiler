package kr.compile;

public final class KrToken {
    public final KrTokenType type;
    public final String text;
    public final int line;
    public final int column;
    public final int startOffset;
    public final int endOffset;

    public KrToken(KrTokenType type, String text, int line, int column, int startOffset, int endOffset) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.column = column;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    @Override
    public String toString() {
        return "[" + type + " '" + text + "' at " + line + ":" + column + "]";
    }
}
