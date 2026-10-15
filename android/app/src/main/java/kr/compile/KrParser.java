package kr.compile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class KrParser {

    private final String filename;
    private final String source;
    private final List<KrToken> tokens;
    private final int tokenCount;
    private int cursor = 0;

    private final List<KrDiagnostic> diagnostics = new ArrayList<>();
    private final List<KrSegment> segments = new ArrayList<>();

    public KrParser(String filename, String source) {
        this.filename = filename;
        this.source = source != null ? source : "";
        KrLexer lexer = new KrLexer(this.source);
        this.tokens = lexer.tokenize();
        this.tokenCount = this.tokens.size();
    }

    public List<KrDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    public List<KrSegment> parse() {
        segments.clear();
        diagnostics.clear();

        List<RawBlock> blocks = new ArrayList<>();

        while (cursor < tokenCount) {
            KrToken tok = current();
            if (tok.type == KrTokenType.EOF) break;

            if (tok.type == KrTokenType.COMMENT) {
                cursor++;
                continue;
            }

            RawBlock block = parseNextTopLevelBlock();
            if (block != null) {
                blocks.add(block);
            }
        }

        // Group consecutive blocks of the same language into segments
        KrLanguage currentLang = null;
        List<RawBlock> currentGroup = new ArrayList<>();

        for (RawBlock b : blocks) {
            if (b.language == null) continue;

            if (currentLang == null || currentLang == b.language) {
                currentLang = b.language;
                currentGroup.add(b);
            } else {
                // Emit segment for previous group
                emitSegment(currentLang, currentGroup);
                currentLang = b.language;
                currentGroup.clear();
                currentGroup.add(b);
            }
        }

        if (!currentGroup.isEmpty()) {
            emitSegment(currentLang, currentGroup);
        }

        return segments;
    }

    private void emitSegment(KrLanguage lang, List<RawBlock> group) {
        if (group.isEmpty()) return;

        RawBlock first = group.get(0);
        RawBlock last = group.get(group.size() - 1);

        int startOffset = first.startOffset;
        int endOffset = last.endOffset;

        if (startOffset < 0) startOffset = 0;
        if (endOffset > source.length()) endOffset = source.length();
        if (startOffset > endOffset) endOffset = startOffset;

        String segSource = source.substring(startOffset, endOffset);

        KrSegment seg = new KrSegment(lang, segSource, filename,
                first.startLine, first.startCol, last.endLine, last.endCol,
                startOffset, endOffset);

        for (RawBlock b : group) {
            if (b.definedFunction != null) {
                seg.declaredFunctions.add(b.definedFunction);
            }
            seg.calledFunctionNames.addAll(b.calledFunctions);
        }

        segments.add(seg);
    }

    private static final class RawBlock {
        final KrLanguage language;
        final int startLine;
        final int startCol;
        final int endLine;
        final int endCol;
        final int startOffset;
        final int endOffset;
        final KrFunction definedFunction;
        final Set<String> calledFunctions = new HashSet<>();

        RawBlock(KrLanguage language, int startLine, int startCol, int endLine, int endCol,
                 int startOffset, int endOffset, KrFunction definedFunction) {
            this.language = language;
            this.startLine = startLine;
            this.startCol = startCol;
            this.endLine = endLine;
            this.endCol = endCol;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.definedFunction = definedFunction;
        }
    }

    private KrToken current() {
        return cursor < tokenCount ? tokens.get(cursor) : tokens.get(tokenCount - 1);
    }

    private KrToken peek(int offset) {
        int idx = cursor + offset;
        return idx < tokenCount ? tokens.get(idx) : tokens.get(tokenCount - 1);
    }

    private RawBlock parseNextTopLevelBlock() {
        KrToken first = current();
        int startLine = first.line;
        int startCol = first.column;
        int startOffset = first.startOffset;

        // 1. Preprocessor directive: always C++
        if (first.type == KrTokenType.PREPROCESSOR) {
            cursor++;
            return new RawBlock(KrLanguage.CPP, startLine, startCol, first.line, first.column, startOffset, first.endOffset, null);
        }

        // 2. Go-specific top-level keywords
        if (first.type == KrTokenType.KEYWORD_GO) {
            String kw = first.text;
            if (kw.equals("package")) {
                cursor++; // skip 'package'
                if (cursor < tokenCount && current().type == KrTokenType.IDENTIFIER) {
                    cursor++; // skip package name (e.g. 'main')
                }
                if (cursor < tokenCount && current().type == KrTokenType.SEMICOLON) {
                    cursor++;
                }
                KrToken lastTok = peek(-1);
                return new RawBlock(KrLanguage.GO, startLine, startCol, lastTok.line, lastTok.column, startOffset, lastTok.endOffset, null);
            }

            if (kw.equals("import")) {
                cursor++; // skip 'import'
                if (cursor < tokenCount && current().type == KrTokenType.LPAREN) {
                    cursor++; // skip '('
                    while (cursor < tokenCount && current().type != KrTokenType.RPAREN && current().type != KrTokenType.EOF) {
                        cursor++;
                    }
                    if (cursor < tokenCount && current().type == KrTokenType.RPAREN) {
                        cursor++;
                    }
                } else {
                    while (cursor < tokenCount && current().type != KrTokenType.EOF) {
                        KrToken t = current();
                        cursor++;
                        if (t.type == KrTokenType.STRING_LITERAL || t.type == KrTokenType.RAW_STRING_LITERAL) {
                            break;
                        }
                    }
                }
                if (cursor < tokenCount && current().type == KrTokenType.SEMICOLON) {
                    cursor++;
                }
                KrToken lastTok = peek(-1);
                return new RawBlock(KrLanguage.GO, startLine, startCol, lastTok.line, lastTok.column, startOffset, lastTok.endOffset, null);
            }

            if (kw.equals("var") || kw.equals("type") || kw.equals("chan") || kw.equals("defer")) {
                cursor++;
                int endOffset = skipToGoStatementEnd();
                KrToken lastTok = peek(-1);
                return new RawBlock(KrLanguage.GO, startLine, startCol, lastTok.line, lastTok.column, startOffset, endOffset, null);
            }

            if (kw.equals("func")) {
                cursor++; // skip 'func'
                KrFunction fn = parseGoFunctionSignature(startLine, startCol);
                Set<String> called = new HashSet<>();
                int endOffset = skipBodyBlock(called);
                RawBlock rb = new RawBlock(KrLanguage.GO, startLine, startCol, peek(-1).line, peek(-1).column, startOffset, endOffset, fn);
                rb.calledFunctions.addAll(called);
                return rb;
            }
        }

        // 3. Rust-specific top-level keywords
        if (first.type == KrTokenType.KEYWORD_RUST) {
            String kw = first.text;
            if (kw.equals("use") || kw.equals("mod") || kw.equals("trait") || kw.equals("impl")) {
                int endOffset = skipToStatementOrBlockEnd();
                KrToken lastTok = peek(-1);
                return new RawBlock(KrLanguage.RUST, startLine, startCol, lastTok.line, lastTok.column, startOffset, endOffset, null);
            }

            if (kw.equals("pub")) {
                cursor++; // skip 'pub'
                if (current().text.equals("fn")) {
                    cursor++; // skip 'fn'
                    KrFunction fn = parseRustFunctionSignature(startLine, startCol);
                    Set<String> called = new HashSet<>();
                    int endOffset = skipBodyBlock(called);
                    RawBlock rb = new RawBlock(KrLanguage.RUST, startLine, startCol, peek(-1).line, peek(-1).column, startOffset, endOffset, fn);
                    rb.calledFunctions.addAll(called);
                    return rb;
                } else if (current().text.equals("use") || current().text.equals("mod") || current().text.equals("trait") || current().text.equals("struct") || current().text.equals("enum")) {
                    int endOffset = skipToStatementOrBlockEnd();
                    KrToken lastTok = peek(-1);
                    return new RawBlock(KrLanguage.RUST, startLine, startCol, lastTok.line, lastTok.column, startOffset, endOffset, null);
                }
            }

            if (kw.equals("fn")) {
                cursor++; // skip 'fn'
                KrFunction fn = parseRustFunctionSignature(startLine, startCol);
                Set<String> called = new HashSet<>();
                int endOffset = skipBodyBlock(called);
                RawBlock rb = new RawBlock(KrLanguage.RUST, startLine, startCol, peek(-1).line, peek(-1).column, startOffset, endOffset, fn);
                rb.calledFunctions.addAll(called);
                return rb;
            }
        }

        // 4. C++ specific top-level keywords
        if (first.type == KrTokenType.KEYWORD_CPP) {
            String kw = first.text;
            int endOffset = skipToStatementOrBlockEnd();
            KrToken lastTok = peek(-1);
            return new RawBlock(KrLanguage.CPP, startLine, startCol, lastTok.line, lastTok.column, startOffset, endOffset, null);
        }

        // 5. Rust attributes like #[no_mangle]
        if (first.type == KrTokenType.UNKNOWN && first.text.equals("#") && peek(1).type == KrTokenType.LBRACKET) {
            int endOffset = skipToStatementOrBlockEnd();
            KrToken lastTok = peek(-1);
            return new RawBlock(KrLanguage.RUST, startLine, startCol, lastTok.line, lastTok.column, startOffset, endOffset, null);
        }

        // 6. Common keywords: struct, enum
        if (first.text.equals("struct") || first.text.equals("enum")) {
            return parseStructOrEnumBlock(first, startLine, startCol, startOffset);
        }

        // 7. General function or variable definition (e.g. C++: int foo(...) { ... })
        return parsePotentialCppOrAmbiguous(first, startLine, startCol, startOffset);
    }

    private RawBlock parseStructOrEnumBlock(KrToken first, int startLine, int startCol, int startOffset) {
        cursor++; // skip struct/enum
        // Read until closing brace
        boolean isCpp = false;
        boolean isRust = false;

        int depth = 0;
        while (cursor < tokenCount) {
            KrToken tok = current();
            cursor++;
            if (tok.type == KrTokenType.LBRACE) {
                depth++;
            } else if (tok.type == KrTokenType.RBRACE) {
                depth--;
                if (depth == 0) {
                    if (current().type == KrTokenType.SEMICOLON) {
                        cursor++; // C++ struct ends with };
                        isCpp = true;
                    } else {
                        isRust = true; // Rust struct has no trailing semicolon
                    }
                    break;
                }
            } else if (depth > 0 && tok.type == KrTokenType.COLON) {
                // Rust field syntax: field: type
                isRust = true;
            } else if (depth > 0 && tok.type == KrTokenType.SEMICOLON) {
                // C++ field syntax: type field;
                isCpp = true;
            }
        }

        KrLanguage lang = isCpp ? KrLanguage.CPP : (isRust ? KrLanguage.RUST : KrLanguage.CPP);
        int endOffset = peek(-1).endOffset;
        return new RawBlock(lang, startLine, startCol, peek(-1).line, peek(-1).column, startOffset, endOffset, null);
    }

    private RawBlock parsePotentialCppOrAmbiguous(KrToken first, int startLine, int startCol, int startOffset) {
        // Look ahead to see if this is a function definition: <type> <name>(...) { ... }
        int scan = cursor;
        boolean hasParen = false;
        boolean hasBrace = false;
        String fnName = null;
        String returnTypeName = first.text;

        while (scan < tokenCount && scan < cursor + 20) {
            KrToken t = tokens.get(scan);
            if (t.type == KrTokenType.SEMICOLON) {
                // Simple statement or variable declaration e.g. int x = 0;
                break;
            }
            if (t.type == KrTokenType.LPAREN) {
                hasParen = true;
                if (scan > cursor) {
                    fnName = tokens.get(scan - 1).text;
                }
            }
            if (t.type == KrTokenType.LBRACE && hasParen) {
                hasBrace = true;
                break;
            }
            scan++;
        }

        if (hasParen && hasBrace && fnName != null) {
            // This is a C++ function definition!
            KrFunction fn = parseCppFunctionSignature(startLine, startCol, returnTypeName, fnName);
            Set<String> called = new HashSet<>();
            int endOffset = skipBodyBlock(called);
            RawBlock rb = new RawBlock(KrLanguage.CPP, startLine, startCol, peek(-1).line, peek(-1).column, startOffset, endOffset, fn);
            rb.calledFunctions.addAll(called);
            return rb;
        }

        // Otherwise statement
        int endOffset = skipToStatementOrBlockEnd();
        KrToken lastTok = peek(-1);
        return new RawBlock(KrLanguage.CPP, startLine, startCol, lastTok.line, lastTok.column, startOffset, endOffset, null);
    }

    private KrFunction parseGoFunctionSignature(int line, int col) {
        // Go function: func Name(param type, ...) (returnType)
        String fnName = "";
        if (current().type == KrTokenType.IDENTIFIER) {
            fnName = current().text;
            cursor++;
        }

        KrFunction fn = new KrFunction(fnName, KrType.VOID, KrLanguage.GO, filename, line, col, fnName);

        if (current().type == KrTokenType.LPAREN) {
            cursor++;
            parseGoParameters(fn);
        }

        // Return type
        if (current().type == KrTokenType.IDENTIFIER || current().type == KrTokenType.KEYWORD_GO || current().type == KrTokenType.KEYWORD_COMMON) {
            KrType retType = KrType.fromString(current().text);
            cursor++;
            fn.setReturnType(retType);
        }

        return fn;
    }

    private void parseGoParameters(KrFunction fn) {
        List<String> paramNames = new ArrayList<>();
        while (cursor < tokenCount && current().type != KrTokenType.RPAREN && current().type != KrTokenType.EOF) {
            if (current().type == KrTokenType.COMMA) {
                cursor++;
                continue;
            }
            if (current().type == KrTokenType.IDENTIFIER) {
                String tokenText = current().text;
                cursor++;
                // In Go: `param1, param2 type` or `param type`
                if (current().type == KrTokenType.COMMA || current().type == KrTokenType.RPAREN) {
                    paramNames.add(tokenText);
                } else {
                    // This tokenText was param name, and next token is type
                    paramNames.add(tokenText);
                    String typeName = current().text;
                    cursor++;
                    KrType pt = KrType.fromString(typeName);
                    for (String name : paramNames) {
                        fn.addParameter(name, pt);
                    }
                    paramNames.clear();
                }
            } else {
                cursor++;
            }
        }
        if (current().type == KrTokenType.RPAREN) cursor++;
    }

    private KrFunction parseRustFunctionSignature(int line, int col) {
        // Rust function: fn name(param: type, ...) -> retType
        String fnName = "";
        if (current().type == KrTokenType.IDENTIFIER || current().type == KrTokenType.KEYWORD_COMMON) {
            fnName = current().text;
            cursor++;
        }

        KrFunction fn = new KrFunction(fnName, KrType.VOID, KrLanguage.RUST, filename, line, col, fnName);

        if (current().type == KrTokenType.LPAREN) {
            cursor++;
            parseRustParameters(fn);
        }

        // Return type: -> type
        if (current().type == KrTokenType.ARROW) {
            cursor++; // skip ->
            StringBuilder sb = new StringBuilder();
            while (cursor < tokenCount && current().type != KrTokenType.LBRACE && current().type != KrTokenType.SEMICOLON) {
                sb.append(current().text);
                cursor++;
            }
            KrType retType = KrType.fromString(sb.toString().trim());
            fn.setReturnType(retType);
        }

        return fn;
    }

    private void parseRustParameters(KrFunction fn) {
        while (cursor < tokenCount && current().type != KrTokenType.RPAREN && current().type != KrTokenType.EOF) {
            if (current().type == KrTokenType.COMMA) {
                cursor++;
                continue;
            }
            if (current().type == KrTokenType.IDENTIFIER || current().text.equals("mut")) {
                if (current().text.equals("mut")) cursor++;
                String paramName = current().text;
                cursor++;
                if (current().type == KrTokenType.COLON) {
                    cursor++; // skip :
                    StringBuilder typeSb = new StringBuilder();
                    while (cursor < tokenCount && current().type != KrTokenType.COMMA && current().type != KrTokenType.RPAREN) {
                        typeSb.append(current().text);
                        cursor++;
                    }
                    KrType pt = KrType.fromString(typeSb.toString().trim());
                    fn.addParameter(paramName, pt);
                }
            } else {
                cursor++;
            }
        }
        if (current().type == KrTokenType.RPAREN) cursor++;
    }

    private KrFunction parseCppFunctionSignature(int line, int col, String returnTypeName, String fnName) {
        KrType retType = KrType.fromString(returnTypeName);
        KrFunction fn = new KrFunction(fnName, retType, KrLanguage.CPP, filename, line, col, fnName);

        // Advance cursor until '('
        while (cursor < tokenCount && current().type != KrTokenType.LPAREN) {
            cursor++;
        }
        if (current().type == KrTokenType.LPAREN) {
            cursor++;
            parseCppParameters(fn);
        }

        return fn;
    }

    private void parseCppParameters(KrFunction fn) {
        while (cursor < tokenCount && current().type != KrTokenType.RPAREN && current().type != KrTokenType.EOF) {
            if (current().type == KrTokenType.COMMA) {
                cursor++;
                continue;
            }
            // type followed by name
            StringBuilder typeSb = new StringBuilder();
            String paramName = "";
            while (cursor < tokenCount && current().type != KrTokenType.COMMA && current().type != KrTokenType.RPAREN) {
                KrToken tok = current();
                if (peek(1).type == KrTokenType.COMMA || peek(1).type == KrTokenType.RPAREN) {
                    paramName = tok.text;
                    cursor++;
                    break;
                } else {
                    typeSb.append(tok.text).append(" ");
                    cursor++;
                }
            }
            KrType pt = KrType.fromString(typeSb.toString().trim());
            fn.addParameter(paramName.isEmpty() ? "arg" : paramName, pt);
        }
        if (current().type == KrTokenType.RPAREN) cursor++;
    }

    private int skipBodyBlock(Set<String> calledFunctions) {
        while (cursor < tokenCount && current().type != KrTokenType.LBRACE && current().type != KrTokenType.SEMICOLON) {
            cursor++;
        }

        if (current().type == KrTokenType.SEMICOLON) {
            int end = current().endOffset;
            cursor++;
            return end;
        }

        if (current().type == KrTokenType.LBRACE) {
            int depth = 0;
            while (cursor < tokenCount) {
                KrToken tok = current();
                cursor++;

                if (tok.type == KrTokenType.LBRACE) {
                    depth++;
                } else if (tok.type == KrTokenType.RBRACE) {
                    depth--;
                    if (depth == 0) {
                        return tok.endOffset;
                    }
                } else if (tok.type == KrTokenType.IDENTIFIER) {
                    // Check if this identifier is a function call: identifier '('
                    if (current().type == KrTokenType.LPAREN) {
                        calledFunctions.add(tok.text);
                    }
                }
            }
        }
        return peek(-1).endOffset;
    }

    private int skipToStatementOrBlockEnd() {
        int depth = 0;
        while (cursor < tokenCount) {
            KrToken tok = current();
            cursor++;

            if (tok.type == KrTokenType.LBRACE) {
                depth++;
            } else if (tok.type == KrTokenType.RBRACE) {
                depth--;
                if (depth == 0) {
                    // Check if trailing semicolon e.g. class Foo {};
                    if (current().type == KrTokenType.SEMICOLON) {
                        int end = current().endOffset;
                        cursor++;
                        return end;
                    }
                    return tok.endOffset;
                }
            } else if (tok.type == KrTokenType.SEMICOLON && depth == 0) {
                return tok.endOffset;
            }
        }
        return peek(-1).endOffset;
    }

    private int skipToGoStatementEnd() {
        int depth = 0;
        int parenDepth = 0;
        while (cursor < tokenCount) {
            KrToken tok = current();
            if (tok.type == KrTokenType.EOF) break;

            if (tok.type == KrTokenType.LBRACE) {
                depth++;
                cursor++;
            } else if (tok.type == KrTokenType.RBRACE) {
                depth--;
                cursor++;
                if (depth <= 0) {
                    if (cursor < tokenCount && current().type == KrTokenType.SEMICOLON) {
                        cursor++;
                    }
                    return peek(-1).endOffset;
                }
            } else if (tok.type == KrTokenType.LPAREN) {
                parenDepth++;
                cursor++;
            } else if (tok.type == KrTokenType.RPAREN) {
                parenDepth--;
                cursor++;
                if (depth == 0 && parenDepth <= 0) {
                    if (cursor < tokenCount && current().type == KrTokenType.SEMICOLON) {
                        cursor++;
                    }
                    return peek(-1).endOffset;
                }
            } else if (tok.type == KrTokenType.SEMICOLON) {
                cursor++;
                if (depth == 0 && parenDepth == 0) {
                    return peek(-1).endOffset;
                }
            } else {
                cursor++;
                if (depth == 0 && parenDepth == 0) {
                    if (cursor >= tokenCount || current().line > tok.line) {
                        return tok.endOffset;
                    }
                }
            }
        }
        return peek(-1).endOffset;
    }
}
