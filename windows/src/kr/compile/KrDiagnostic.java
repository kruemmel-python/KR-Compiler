package kr.compile;

import java.util.ArrayList;
import java.util.List;

public final class KrDiagnostic {
    public enum Severity { ERROR, WARNING, INFO }

    public final Severity severity;
    public final String message;
    public final String file;
    public final int line;
    public final int column;
    public final List<String> possibleInterpretations = new ArrayList<>();

    public KrDiagnostic(Severity severity, String message, String file, int line, int column) {
        this.severity = severity;
        this.message = message;
        this.file = file;
        this.line = line;
        this.column = column;
    }

    public void addInterpretation(String interpretation) {
        if (interpretation != null && !interpretation.isEmpty()) {
            possibleInterpretations.add(interpretation);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (file != null && !file.isEmpty()) {
            sb.append(file).append(":");
            if (line > 0) sb.append(line).append(":");
            if (column > 0) sb.append(column).append(": ");
        }
        sb.append("[").append(severity).append("] ").append(message);
        if (!possibleInterpretations.isEmpty()) {
            sb.append(" (Mögliche Interpretationen: ");
            for (int i = 0; i < possibleInterpretations.size(); i++) {
                if (i > 0) sb.append("; ");
                sb.append(possibleInterpretations.get(i));
            }
            sb.append(")");
        }
        return sb.toString();
    }
}
