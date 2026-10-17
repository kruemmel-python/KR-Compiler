package kr.compile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class KrLexer {

    private static final Set<String> CPP_KEYWORDS = new HashSet<>();
    private static final Set<String> RUST_KEYWORDS = new HashSet<>();
    private static final Set<String> GO_KEYWORDS = new HashSet<>();
    private static final Set<String> COMMON_KEYWORDS = new HashSet<>();

    static {
        // C++ specific keywords
        Collections.addAll(CPP_KEYWORDS,
                "class", "namespace", "template", "typename", "constexpr", "consteval", "constinit",
                "concept", "requires", "decltype", "using", "virtual", "override", "final",
                "explicit", "friend", "operator", "noexcept", "static_assert",
                "static_cast", "dynamic_cast", "const_cast", "reinterpret_cast",
                "new", "delete", "this", "throw", "try", "catch", "std", "cout", "cin", "endl");

        // Rust specific keywords
        Collections.addAll(RUST_KEYWORDS,
                "fn", "pub", "impl", "trait", "match", "let", "mut", "use", "mod", "crate",
                "super", "where", "unsafe", "async", "await", "loop", "move", "ref",
                "i8", "i16", "i32", "i64", "i128", "isize",
                "u8", "u16", "u32", "u64", "u128", "usize",
                "f32", "f64");

        // Go specific keywords
        Collections.addAll(GO_KEYWORDS,
                "func", "package", "import", "var", "type", "chan", "defer", "select",
                "fallthrough", "range", "nil", "make", "append",
                "int8", "int16", "int32", "int64",
                "uint8", "uint16", "uint32", "uint64", "uintptr",
                "float32", "float64", "complex64", "complex128");

        // Common keywords
        Collections.addAll(COMMON_KEYWORDS,
                "struct", "enum", "if", "else", "for", "while", "return", "const",
                "break", "continue", "switch", "case", "default", "bool", "true", "false",
                "int", "void", "char", "short", "long", "float", "double", "unsigned", "signed");
    }

    private final String source;
    private final int length;
    private int index = 0;
    private int line = 1;
    private int col = 1;

    public KrLexer(String source) {
        this.source = source != null ? source : "";
        this.length = this.source.length();
    }

    public List<KrToken> tokenize() {
        List<KrToken> tokens = new ArrayList<>();
        while (index < length) {
            char c = source.charAt(index);

            // Whitespace
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
                continue;
            }
            if (c == '\n') {
                line++;
                col = 1;
                index++;
                continue;
            }

            // Preprocessor: starts with # at the beginning of a line (or with leading whitespace)
            if (c == '#') {
                tokens.add(scanPreprocessor());
                continue;
            }

            // Single or multi-line comment or division operator
            if (c == '/') {
                if (peek(1) == '/') {
                    tokens.add(scanLineComment());
                    continue;
                } else if (peek(1) == '*') {
                    tokens.add(scanBlockComment());
                    continue;
                }
            }

            // Go raw string literal with backtick `...`
            if (c == '`') {
                tokens.add(scanGoRawString());
                continue;
            }

            // Rust raw string r"..." or r#"..."# or C++ raw string R"(...)"
            if ((c == 'r' || c == 'R') && (peek(1) == '"' || peek(1) == '#')) {
                KrToken rawStr = scanRawString();
                if (rawStr != null) {
                    tokens.add(rawStr);
                    continue;
                }
            }

            // Double quoted string
            if (c == '"') {
                tokens.add(scanRegularString());
                continue;
            }

            // Character literal
            if (c == '\'') {
                tokens.add(scanCharLiteral());
                continue;
            }

            // Number literal
            if (Character.isDigit(c) || (c == '.' && Character.isDigit(peek(1)))) {
                tokens.add(scanNumber());
                continue;
            }

            // Identifiers and keywords
            if (isIdentStart(c)) {
                tokens.add(scanIdentifierOrKeyword());
                continue;
            }

            // Operators and punctuation
            tokens.add(scanPunctuationOrOperator());
        }

        tokens.add(new KrToken(KrTokenType.EOF, "", line, col, index, index));
        return tokens;
    }

    private char peek(int offset) {
        int target = index + offset;
        return target < length ? source.charAt(target) : '\0';
    }

    private char advance() {
        char c = source.charAt(index++);
        col++;
        return c;
    }

    private KrToken scanPreprocessor() {
        int startLine = line;
        int startCol = col;
        int startOffset = index;

        StringBuilder sb = new StringBuilder();
        while (index < length) {
            char c = advance();
            sb.append(c);
            if (c == '\\' && peek(0) == '\n') {
                sb.append(advance());
                line++;
                col = 1;
            } else if (c == '\n') {
                line++;
                col = 1;
                break;
            }
        }
        return new KrToken(KrTokenType.PREPROCESSOR, sb.toString().trim(), startLine, startCol, startOffset, index);
    }

    private KrToken scanLineComment() {
        int startLine = line;
        int startCol = col;
        int startOffset = index;

        StringBuilder sb = new StringBuilder();
        while (index < length && source.charAt(index) != '\n') {
            sb.append(advance());
        }
        return new KrToken(KrTokenType.COMMENT, sb.toString(), startLine, startCol, startOffset, index);
    }

    private KrToken scanBlockComment() {
        int startLine = line;
        int startCol = col;
        int startOffset = index;

        StringBuilder sb = new StringBuilder();
        sb.append(advance()); // '/'
        sb.append(advance()); // '*'

        while (index < length) {
            char c = advance();
            sb.append(c);
            if (c == '\n') {
                line++;
                col = 1;
            } else if (c == '*' && peek(0) == '/') {
                sb.append(advance()); // '/'
                break;
            }
        }
        return new KrToken(KrTokenType.COMMENT, sb.toString(), startLine, startCol, startOffset, index);
    }

    private KrToken scanGoRawString() {
        int startLine = line;
        int startCol = col;
        int startOffset = index;

        StringBuilder sb = new StringBuilder();
        sb.append(advance()); // '`'

        while (index < length) {
            char c = advance();
            sb.append(c);
            if (c == '\n') {
                line++;
                col = 1;
            } else if (c == '`') {
                break;
            }
        }
        return new KrToken(KrTokenType.RAW_STRING_LITERAL, sb.toString(), startLine, startCol, startOffset, index);
    }

    private KrToken scanRawString() {
        int startLine = line;
        int startCol = col;
        int startOffset = index;

        char lead = advance(); // 'r' or 'R'
        int hashCount = 0;
        while (index < length && peek(0) == '#') {
            advance();
            hashCount++;
        }

        if (index < length && peek(0) == '"') {
            advance(); // '"'
            StringBuilder sb = new StringBuilder();
            sb.append(lead);
            for (int i = 0; i < hashCount; i++) sb.append('#');
            sb.append('"');

            while (index < length) {
                char c = advance();
                sb.append(c);
                if (c == '\n') {
                    line++;
                    col = 1;
                } else if (c == '"') {
                    boolean match = true;
                    for (int i = 0; i < hashCount; i++) {
                        if (peek(i) != '#') {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        for (int i = 0; i < hashCount; i++) {
                            sb.append(advance());
                        }
                        return new KrToken(KrTokenType.RAW_STRING_LITERAL, sb.toString(), startLine, startCol, startOffset, index);
                    }
                }
            }
            return new KrToken(KrTokenType.RAW_STRING_LITERAL, sb.toString(), startLine, startCol, startOffset, index);
        }

        // Backtrack if not a valid raw string
        index = startOffset;
        line = startLine;
        col = startCol;
        return null;
    }

    private KrToken scanRegularString() {
        int startLine = line;
        int startCol = col;
        int startOffset = index;

        StringBuilder sb = new StringBuilder();
        sb.append(advance()); // '"'

        while (index < length) {
            char c = advance();
            sb.append(c);
            if (c == '\\') {
                if (index < length) {
                    char next = advance();
                    sb.append(next);
                    if (next == '\n') {
                        line++;
                        col = 1;
                    }
                }
            } else if (c == '"') {
                break;
            } else if (c == '\n') {
                line++;
                col = 1;
            }
        }
        return new KrToken(KrTokenType.STRING_LITERAL, sb.toString(), startLine, startCol, startOffset, index);
    }

    private KrToken scanCharLiteral() {
        int startLine = line;
        int startCol = col;
        int startOffset = index;

        StringBuilder sb = new StringBuilder();
        sb.append(advance()); // '\''

        while (index < length) {
            char c = advance();
            sb.append(c);
            if (c == '\\') {
                if (index < length) {
                    sb.append(advance());
                }
            } else if (c == '\'') {
                break;
            } else if (c == '\n') {
                line++;
                col = 1;
                break;
            }
        }
        return new KrToken(KrTokenType.CHAR_LITERAL, sb.toString(), startLine, startCol, startOffset, index);
    }

    private KrToken scanNumber() {
        int startLine = line;
        int startCol = col;
        int startOffset = index;

        StringBuilder sb = new StringBuilder();
        while (index < length) {
            char c = peek(0);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '\'' || c == '_') {
                sb.append(advance());
            } else {
                break;
            }
        }
        return new KrToken(KrTokenType.NUMBER_LITERAL, sb.toString(), startLine, startCol, startOffset, index);
    }

    private KrToken scanIdentifierOrKeyword() {
        int startLine = line;
        int startCol = col;
        int startOffset = index;

        StringBuilder sb = new StringBuilder();
        while (index < length && isIdentPart(peek(0))) {
            sb.append(advance());
        }

        String text = sb.toString();
        KrTokenType type = KrTokenType.IDENTIFIER;

        if (CPP_KEYWORDS.contains(text)) {
            type = KrTokenType.KEYWORD_CPP;
        } else if (RUST_KEYWORDS.contains(text)) {
            type = KrTokenType.KEYWORD_RUST;
        } else if (GO_KEYWORDS.contains(text)) {
            type = KrTokenType.KEYWORD_GO;
        } else if (COMMON_KEYWORDS.contains(text)) {
            type = KrTokenType.KEYWORD_COMMON;
        }

        return new KrToken(type, text, startLine, startCol, startOffset, index);
    }

    private KrToken scanPunctuationOrOperator() {
        int startLine = line;
        int startCol = col;
        int startOffset = index;

        char c = advance();
        char next = peek(0);

        // Two-character punctuation
        if (c == '-' && next == '>') {
            advance();
            return new KrToken(KrTokenType.ARROW, "->", startLine, startCol, startOffset, index);
        }
        if (c == '=' && next == '>') {
            advance();
            return new KrToken(KrTokenType.FAT_ARROW, "=>", startLine, startCol, startOffset, index);
        }
        if (c == '=' && next == '=') {
            advance();
            return new KrToken(KrTokenType.EQ, "==", startLine, startCol, startOffset, index);
        }
        if (c == '!' && next == '=') {
            advance();
            return new KrToken(KrTokenType.NE, "!=", startLine, startCol, startOffset, index);
        }
        if (c == '<' && next == '=') {
            advance();
            return new KrToken(KrTokenType.LE, "<=", startLine, startCol, startOffset, index);
        }
        if (c == '>' && next == '=') {
            advance();
            return new KrToken(KrTokenType.GE, ">=", startLine, startCol, startOffset, index);
        }
        if (c == '&' && next == '&') {
            advance();
            return new KrToken(KrTokenType.AND, "&&", startLine, startCol, startOffset, index);
        }
        if (c == '|' && next == '|') {
            advance();
            return new KrToken(KrTokenType.OR, "||", startLine, startCol, startOffset, index);
        }

        // Single-character punctuation
        switch (c) {
            case '{': return new KrToken(KrTokenType.LBRACE, "{", startLine, startCol, startOffset, index);
            case '}': return new KrToken(KrTokenType.RBRACE, "}", startLine, startCol, startOffset, index);
            case '(': return new KrToken(KrTokenType.LPAREN, "(", startLine, startCol, startOffset, index);
            case ')': return new KrToken(KrTokenType.RPAREN, ")", startLine, startCol, startOffset, index);
            case '[': return new KrToken(KrTokenType.LBRACKET, "[", startLine, startCol, startOffset, index);
            case ']': return new KrToken(KrTokenType.RBRACKET, "]", startLine, startCol, startOffset, index);
            case ';': return new KrToken(KrTokenType.SEMICOLON, ";", startLine, startCol, startOffset, index);
            case ':': return new KrToken(KrTokenType.COLON, ":", startLine, startCol, startOffset, index);
            case ',': return new KrToken(KrTokenType.COMMA, ",", startLine, startCol, startOffset, index);
            case '.': return new KrToken(KrTokenType.DOT, ".", startLine, startCol, startOffset, index);
            case '=': return new KrToken(KrTokenType.ASSIGN, "=", startLine, startCol, startOffset, index);
            case '+': return new KrToken(KrTokenType.PLUS, "+", startLine, startCol, startOffset, index);
            case '-': return new KrToken(KrTokenType.MINUS, "-", startLine, startCol, startOffset, index);
            case '*': return new KrToken(KrTokenType.STAR, "*", startLine, startCol, startOffset, index);
            case '/': return new KrToken(KrTokenType.SLASH, "/", startLine, startCol, startOffset, index);
            case '%': return new KrToken(KrTokenType.PERCENT, "%", startLine, startCol, startOffset, index);
            case '<': return new KrToken(KrTokenType.LT, "<", startLine, startCol, startOffset, index);
            case '>': return new KrToken(KrTokenType.GT, ">", startLine, startCol, startOffset, index);
            case '!': return new KrToken(KrTokenType.NOT, "!", startLine, startCol, startOffset, index);
            case '&': return new KrToken(KrTokenType.AMPERSAND, "&", startLine, startCol, startOffset, index);
            case '|': return new KrToken(KrTokenType.PIPE, "|", startLine, startCol, startOffset, index);
            case '^': return new KrToken(KrTokenType.CARET, "^", startLine, startCol, startOffset, index);
            case '~': return new KrToken(KrTokenType.TILDE, "~", startLine, startCol, startOffset, index);
            default:  return new KrToken(KrTokenType.UNKNOWN, String.valueOf(c), startLine, startCol, startOffset, index);
        }
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
