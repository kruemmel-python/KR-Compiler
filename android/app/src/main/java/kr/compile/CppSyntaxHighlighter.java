package kr.compile;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CppSyntaxHighlighter {

    // Theme color palettes
    public static final class Colors {
        final int keyword;
        final int type;
        final int preprocessor;
        final int string;
        final int number;
        final int comment;
        final int function;
        final int variable;

        Colors(int kw, int ty, int pp, int str, int num, int com, int fn, int var) {
            this.keyword = kw;
            this.type = ty;
            this.preprocessor = pp;
            this.string = str;
            this.number = num;
            this.comment = com;
            this.function = fn;
            this.variable = var;
        }
    }

    private static final Colors DARK = new Colors(
            Color.parseColor("#C678DD"), // keyword: purple
            Color.parseColor("#E5C07B"), // type: gold/yellow
            Color.parseColor("#E06C75"), // preprocessor: coral red
            Color.parseColor("#98C379"), // string: green
            Color.parseColor("#D19A66"), // number: orange
            Color.parseColor("#7F848E"), // comment: muted grey
            Color.parseColor("#61AFEF"), // function: bright blue
            Color.parseColor("#56B6C2")  // variable/macro: cyan
    );

    private static final Colors LIGHT = new Colors(
            Color.parseColor("#A626A4"), // keyword: deep magenta
            Color.parseColor("#B76B01"), // type: dark amber
            Color.parseColor("#E45649"), // preprocessor: red
            Color.parseColor("#50A14F"), // string: green
            Color.parseColor("#986801"), // number: orange
            Color.parseColor("#A0A1A7"), // comment: grey
            Color.parseColor("#4078F2"), // function: blue
            Color.parseColor("#0184BC")  // variable/macro: cyan
    );

    // C++20 Patterns
    private static final Pattern CPP_PREPROCESSOR = Pattern.compile(
            "^\\s*#\\s*(?:include|define|undef|if|ifdef|ifndef|elif|else|endif|pragma|error|warning)\\b.*$",
            Pattern.MULTILINE);

    private static final Pattern CPP_COMMENTS = Pattern.compile(
            "//.*|/\\*[\\s\\S]*?\\*/");

    private static final Pattern CPP_STRINGS = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])'");

    private static final Pattern CPP_NUMBERS = Pattern.compile(
            "\\b(?:0x[0-9a-fA-F]+(?:'[0-9a-fA-F]+)*|0b[01]+(?:'[01]+)*|\\d+(?:'\\d+)*(?:\\.\\d+(?:'\\d+)*)?(?:[eE][+-]?\\d+)?)[fFlLuUzZ]*\\b");

    private static final Pattern CPP_KEYWORDS = Pattern.compile(
            "\\b(?:alignas|alignof|asm|auto|bool|break|case|catch|char|char8_t|char16_t|char32_t|class|concept|const|consteval|constexpr|constinit|continue|co_await|co_return|co_yield|decltype|default|delete|do|double|dynamic_cast|else|enum|explicit|export|extern|false|float|for|friend|goto|if|inline|int|long|mutable|namespace|new|noexcept|nullptr|operator|private|protected|public|register|reinterpret_cast|requires|return|short|signed|sizeof|static|static_assert|static_cast|struct|switch|template|this|thread_local|throw|true|try|typedef|typeid|typename|union|unsigned|using|virtual|void|volatile|wchar_t|while)\\b");

    private static final Pattern CPP_TYPES = Pattern.compile(
            "\\b(?:std|string|string_view|vector|map|unordered_map|set|unordered_set|pair|tuple|unique_ptr|shared_ptr|weak_ptr|optional|variant|any|span|cout|cin|cerr|endl|size_t|int8_t|uint8_t|int16_t|uint16_t|int32_t|uint32_t|int64_t|uint64_t|intptr_t|uintptr_t|ptrdiff_t|nullptr_t|FILE)\\b");

    private static final Pattern CPP_FUNCTIONS = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");

    // Rust Patterns
    private static final Pattern RUST_KEYWORDS = Pattern.compile(
            "\\b(?:fn|pub|impl|trait|match|let|mut|use|mod|crate|super|where|unsafe|async|await|loop|move|ref|struct|enum|if|else|return|const|type|as|in)\\b");
    private static final Pattern RUST_TYPES = Pattern.compile(
            "\\b(?:i8|i16|i32|i64|i128|isize|u8|u16|u32|u64|u128|usize|f32|f64|bool|char|str|String|Vec|Option|Result|Some|None|Ok|Err|Box)\\b");

    // Go Patterns
    private static final Pattern GO_KEYWORDS = Pattern.compile(
            "\\b(?:func|package|import|var|type|chan|defer|select|go|range|fallthrough|interface|struct|if|else|return|for|switch|case|default|const)\\b");
    private static final Pattern GO_TYPES = Pattern.compile(
            "\\b(?:int|int8|int16|int32|int64|uint|uint8|uint16|uint32|uint64|uintptr|float32|float64|complex64|complex128|string|bool|byte|rune|error|nil|true|false|make|append|len|cap)\\b");

    // CMake Patterns
    private static final Pattern CMAKE_COMMANDS = Pattern.compile(
            "\\b(?:cmake_minimum_required|project|add_executable|add_library|target_link_libraries|target_include_directories|target_compile_definitions|target_compile_options|target_sources|find_package|include|FetchContent_Declare|FetchContent_MakeAvailable|set|get_property|set_property|if|elseif|else|endif|foreach|endforeach|while|endwhile|function|endfunction|macro|endmacro|return|message|option)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CMAKE_COMMENTS = Pattern.compile("#.*");
    private static final Pattern CMAKE_VARS = Pattern.compile("\\$\\{[a-zA-Z0-9_]+\\}");

    // JSON Patterns
    private static final Pattern JSON_KEYS = Pattern.compile("\"([^\"]+)\"\\s*:");

    // XML Patterns
    private static final Pattern XML_TAGS = Pattern.compile("</?[a-zA-Z0-9_:-]+");
    private static final Pattern XML_ATTRS = Pattern.compile("\\b[a-zA-Z0-9_:-]+=");

    public static void highlight(Spannable spannable, String filename, boolean isDarkTheme, int rangeStart, int rangeEnd) {
        if (spannable == null || spannable.length() == 0) return;

        Colors c = isDarkTheme ? DARK : LIGHT;
        int textLength = spannable.length();

        int start = Math.max(0, rangeStart);
        int end = Math.min(textLength, rangeEnd);
        if (start >= end) {
            start = 0;
            end = textLength;
        }

        // Clear existing syntax spans in target range
        CharacterStyle[] existing = spannable.getSpans(start, end, CharacterStyle.class);
        for (CharacterStyle s : existing) {
            if (s instanceof ForegroundColorSpan || s instanceof StyleSpan) {
                spannable.removeSpan(s);
            }
        }

        CharSequence subSeq = spannable.subSequence(start, end);
        String name = filename != null ? filename.toLowerCase(Locale.ROOT) : "";

        if (name.endsWith(".cmake") || name.equals("cmakelists.txt")) {
            highlightCMake(spannable, subSeq, start, c);
        } else if (name.endsWith(".json")) {
            highlightJson(spannable, subSeq, start, c);
        } else if (name.endsWith(".xml") || name.endsWith(".html")) {
            highlightXml(spannable, subSeq, start, c);
        } else if (name.endsWith(".md") || name.endsWith(".txt")) {
            // Minimal text highlighting
        } else if (name.endsWith(".kr")) {
            highlightKr(spannable, subSeq, start, c);
        } else {
            // Default to C/C++
            highlightCpp(spannable, subSeq, start, c);
        }
    }

    private static void highlightKr(Spannable sp, CharSequence text, int offset, Colors c) {
        String code = text.toString();
        try {
            KrParser parser = new KrParser("editor.kr", code);
            List<KrSegment> segments = parser.parse();

            if (!segments.isEmpty()) {
                for (KrSegment seg : segments) {
                    int segStart = Math.max(0, seg.startOffset);
                    int segEnd = Math.min(text.length(), seg.endOffset);
                    if (segStart >= segEnd) continue;

                    CharSequence segText = text.subSequence(segStart, segEnd);
                    if (seg.language == KrLanguage.RUST) {
                        highlightRust(sp, segText, offset + segStart, c);
                    } else if (seg.language == KrLanguage.GO) {
                        highlightGo(sp, segText, offset + segStart, c);
                    } else {
                        highlightCpp(sp, segText, offset + segStart, c);
                    }
                }
            } else {
                highlightCpp(sp, text, offset, c);
            }
        } catch (Exception ignored) {
            highlightCpp(sp, text, offset, c);
        }

        // Comments and strings
        Matcher mCom = CPP_COMMENTS.matcher(text);
        while (mCom.find()) {
            sp.setSpan(new ForegroundColorSpan(c.comment), offset + mCom.start(), offset + mCom.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(Typeface.ITALIC), offset + mCom.start(), offset + mCom.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mStr = CPP_STRINGS.matcher(text);
        while (mStr.find()) {
            sp.setSpan(new ForegroundColorSpan(c.string), offset + mStr.start(), offset + mStr.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void highlightRust(Spannable sp, CharSequence text, int offset, Colors c) {
        Matcher mFunc = CPP_FUNCTIONS.matcher(text);
        while (mFunc.find()) {
            sp.setSpan(new ForegroundColorSpan(c.function), offset + mFunc.start(1), offset + mFunc.end(1), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mType = RUST_TYPES.matcher(text);
        while (mType.find()) {
            sp.setSpan(new ForegroundColorSpan(c.type), offset + mType.start(), offset + mType.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mKw = RUST_KEYWORDS.matcher(text);
        while (mKw.find()) {
            sp.setSpan(new ForegroundColorSpan(c.keyword), offset + mKw.start(), offset + mKw.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(Typeface.BOLD), offset + mKw.start(), offset + mKw.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mNum = CPP_NUMBERS.matcher(text);
        while (mNum.find()) {
            sp.setSpan(new ForegroundColorSpan(c.number), offset + mNum.start(), offset + mNum.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void highlightGo(Spannable sp, CharSequence text, int offset, Colors c) {
        Matcher mFunc = CPP_FUNCTIONS.matcher(text);
        while (mFunc.find()) {
            sp.setSpan(new ForegroundColorSpan(c.function), offset + mFunc.start(1), offset + mFunc.end(1), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mType = GO_TYPES.matcher(text);
        while (mType.find()) {
            sp.setSpan(new ForegroundColorSpan(c.type), offset + mType.start(), offset + mType.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mKw = GO_KEYWORDS.matcher(text);
        while (mKw.find()) {
            sp.setSpan(new ForegroundColorSpan(c.keyword), offset + mKw.start(), offset + mKw.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(Typeface.BOLD), offset + mKw.start(), offset + mKw.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mNum = CPP_NUMBERS.matcher(text);
        while (mNum.find()) {
            sp.setSpan(new ForegroundColorSpan(c.number), offset + mNum.start(), offset + mNum.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void highlightCpp(Spannable sp, CharSequence text, int offset, Colors c) {
        // 1. Functions
        Matcher mFunc = CPP_FUNCTIONS.matcher(text);
        while (mFunc.find()) {
            sp.setSpan(new ForegroundColorSpan(c.function), offset + mFunc.start(1), offset + mFunc.end(1), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 2. Types
        Matcher mType = CPP_TYPES.matcher(text);
        while (mType.find()) {
            sp.setSpan(new ForegroundColorSpan(c.type), offset + mType.start(), offset + mType.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 3. Keywords
        Matcher mKw = CPP_KEYWORDS.matcher(text);
        while (mKw.find()) {
            sp.setSpan(new ForegroundColorSpan(c.keyword), offset + mKw.start(), offset + mKw.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(Typeface.BOLD), offset + mKw.start(), offset + mKw.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 4. Numbers
        Matcher mNum = CPP_NUMBERS.matcher(text);
        while (mNum.find()) {
            sp.setSpan(new ForegroundColorSpan(c.number), offset + mNum.start(), offset + mNum.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 5. Preprocessor directives
        Matcher mPp = CPP_PREPROCESSOR.matcher(text);
        while (mPp.find()) {
            sp.setSpan(new ForegroundColorSpan(c.preprocessor), offset + mPp.start(), offset + mPp.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 6. Strings
        Matcher mStr = CPP_STRINGS.matcher(text);
        while (mStr.find()) {
            sp.setSpan(new ForegroundColorSpan(c.string), offset + mStr.start(), offset + mStr.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 7. Comments (highest priority over previous spans)
        Matcher mCom = CPP_COMMENTS.matcher(text);
        while (mCom.find()) {
            sp.setSpan(new ForegroundColorSpan(c.comment), offset + mCom.start(), offset + mCom.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(Typeface.ITALIC), offset + mCom.start(), offset + mCom.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void highlightCMake(Spannable sp, CharSequence text, int offset, Colors c) {
        Matcher mCmd = CMAKE_COMMANDS.matcher(text);
        while (mCmd.find()) {
            sp.setSpan(new ForegroundColorSpan(c.keyword), offset + mCmd.start(), offset + mCmd.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(Typeface.BOLD), offset + mCmd.start(), offset + mCmd.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mVar = CMAKE_VARS.matcher(text);
        while (mVar.find()) {
            sp.setSpan(new ForegroundColorSpan(c.variable), offset + mVar.start(), offset + mVar.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mStr = CPP_STRINGS.matcher(text);
        while (mStr.find()) {
            sp.setSpan(new ForegroundColorSpan(c.string), offset + mStr.start(), offset + mStr.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mCom = CMAKE_COMMENTS.matcher(text);
        while (mCom.find()) {
            sp.setSpan(new ForegroundColorSpan(c.comment), offset + mCom.start(), offset + mCom.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(Typeface.ITALIC), offset + mCom.start(), offset + mCom.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void highlightJson(Spannable sp, CharSequence text, int offset, Colors c) {
        Matcher mStr = CPP_STRINGS.matcher(text);
        while (mStr.find()) {
            sp.setSpan(new ForegroundColorSpan(c.string), offset + mStr.start(), offset + mStr.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mKey = JSON_KEYS.matcher(text);
        while (mKey.find()) {
            sp.setSpan(new ForegroundColorSpan(c.keyword), offset + mKey.start(1), offset + mKey.end(1), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mNum = CPP_NUMBERS.matcher(text);
        while (mNum.find()) {
            sp.setSpan(new ForegroundColorSpan(c.number), offset + mNum.start(), offset + mNum.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void highlightXml(Spannable sp, CharSequence text, int offset, Colors c) {
        Matcher mTag = XML_TAGS.matcher(text);
        while (mTag.find()) {
            sp.setSpan(new ForegroundColorSpan(c.keyword), offset + mTag.start(), offset + mTag.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mAttr = XML_ATTRS.matcher(text);
        while (mAttr.find()) {
            sp.setSpan(new ForegroundColorSpan(c.type), offset + mAttr.start(), offset + mAttr.end() - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher mStr = CPP_STRINGS.matcher(text);
        while (mStr.find()) {
            sp.setSpan(new ForegroundColorSpan(c.string), offset + mStr.start(), offset + mStr.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
