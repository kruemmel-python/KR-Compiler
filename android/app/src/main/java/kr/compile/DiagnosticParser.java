package kr.compile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiagnosticParser {
    public enum Severity { ERROR, WARNING, NOTE, INFO }

    public static final class Item {
        public final String file;
        public final int line;
        public final int column;
        public final Severity severity;
        public final String message;
        public final String rawLine;

        public Item(String file, int line, int column, Severity severity, String message, String rawLine) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.severity = severity;
            this.message = message;
            this.rawLine = rawLine;
        }

        @Override
        public String toString() {
            return (file != null ? file + ":" + line + (column > 0 ? ":" + column : "") + " " : "")
                    + "[" + severity + "] " + message;
        }
    }

    // Matches: path/to/file.cpp:42:15: error: expected ';'
    // or: path/to/file.cpp:42: error: ...
    private static final Pattern CLANG_DIAG_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9_./\\\\-]+):(\\d+)(?::(\\d+))?:\\s*(fatal error|error|warning|note):\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    // Matches: CMake Error at CMakeLists.txt:42 (target_link_libraries):
    // or: CMake Warning at CMakeLists.txt:12:
    private static final Pattern CMAKE_DIAG_PATTERN = Pattern.compile(
            "^CMake (Error|Warning)(?: at ([a-zA-Z0-9_./\\\\-]+):(\\d+)(?:\\s*\\([^)]*\\))?)?:\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    public static Item parseLine(String line) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return null;

        Matcher mClang = CLANG_DIAG_PATTERN.matcher(trimmed);
        if (mClang.matches()) {
            String file = mClang.group(1);
            int lineNum = Integer.parseInt(mClang.group(2));
            int colNum = 1;
            if (mClang.group(3) != null) {
                try { colNum = Integer.parseInt(mClang.group(3)); } catch (NumberFormatException ignored) {}
            }
            String sevStr = mClang.group(4).toLowerCase();
            Severity sev = Severity.INFO;
            if (sevStr.contains("error")) sev = Severity.ERROR;
            else if (sevStr.contains("warning")) sev = Severity.WARNING;
            else if (sevStr.contains("note")) sev = Severity.NOTE;

            String msg = mClang.group(5);
            return new Item(file, lineNum, colNum, sev, msg, trimmed);
        }

        Matcher mCmake = CMAKE_DIAG_PATTERN.matcher(trimmed);
        if (mCmake.matches()) {
            String type = mCmake.group(1).toLowerCase();
            Severity sev = type.contains("error") ? Severity.ERROR : Severity.WARNING;
            String file = mCmake.group(2);
            int lineNum = 1;
            if (mCmake.group(3) != null) {
                try { lineNum = Integer.parseInt(mCmake.group(3)); } catch (NumberFormatException ignored) {}
            }
            String msg = mCmake.group(4);
            return new Item(file, lineNum, 1, sev, msg, trimmed);
        }

        return null;
    }
}
