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

        // Rust location pointer: --> src/lib.rs:5:10
        if (trimmed.startsWith("-->")) {
            String rest = trimmed.substring(3).trim();
            int colon1 = rest.indexOf(':');
            if (colon1 > 0) {
                String file = rest.substring(0, colon1);
                int colon2 = rest.indexOf(':', colon1 + 1);
                int lineNum = 1;
                int colNum = 1;
                if (colon2 > colon1) {
                    try {
                        lineNum = Integer.parseInt(rest.substring(colon1 + 1, colon2));
                        colNum = Integer.parseInt(rest.substring(colon2 + 1).replaceAll("[^0-9].*$", ""));
                    } catch (Exception ignored) {}
                }
                return new Item(file, lineNum, colNum, Severity.ERROR, "Rust-Fehlerposition", trimmed);
            }
        }

        // Go compiler message: ./main.go:5:2: undefined: foo
        int firstColon = trimmed.indexOf(':');
        if (firstColon > 0 && (trimmed.endsWith(".go") || trimmed.contains(".go:") || trimmed.contains(".kr:"))) {
            int secondColon = trimmed.indexOf(':', firstColon + 1);
            if (secondColon > firstColon) {
                try {
                    String file = trimmed.substring(0, firstColon);
                    int lineNum = Integer.parseInt(trimmed.substring(firstColon + 1, secondColon));
                    int thirdColon = trimmed.indexOf(':', secondColon + 1);
                    int colNum = 1;
                    String msg = trimmed.substring(secondColon + 1).trim();
                    if (thirdColon > secondColon) {
                        try {
                            colNum = Integer.parseInt(trimmed.substring(secondColon + 1, thirdColon));
                            msg = trimmed.substring(thirdColon + 1).trim();
                        } catch (NumberFormatException ignored) {}
                    }
                    return new Item(file, lineNum, colNum, Severity.ERROR, msg, trimmed);
                } catch (Exception ignored) {}
            }
        }

        return null;
    }
}
