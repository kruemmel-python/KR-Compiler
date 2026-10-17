package kr.compile.windows;

import javax.swing.text.*;
import java.awt.Color;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SyntaxHighlighting {
    private static final SimpleAttributeSet ATTR_DEFAULT = new SimpleAttributeSet();
    private static final SimpleAttributeSet ATTR_KEYWORD = new SimpleAttributeSet();
    private static final SimpleAttributeSet ATTR_TYPE = new SimpleAttributeSet();
    private static final SimpleAttributeSet ATTR_STRING = new SimpleAttributeSet();
    private static final SimpleAttributeSet ATTR_COMMENT = new SimpleAttributeSet();
    private static final SimpleAttributeSet ATTR_NUMBER = new SimpleAttributeSet();
    private static final SimpleAttributeSet ATTR_PREPROCESSOR = new SimpleAttributeSet();

    static {
        StyleConstants.setForeground(ATTR_DEFAULT, new Color(220, 220, 220));
        StyleConstants.setFontFamily(ATTR_DEFAULT, "Consolas");
        StyleConstants.setFontSize(ATTR_DEFAULT, 14);

        StyleConstants.setForeground(ATTR_KEYWORD, new Color(86, 156, 214)); // VS Code Blue
        StyleConstants.setBold(ATTR_KEYWORD, true);

        StyleConstants.setForeground(ATTR_TYPE, new Color(78, 201, 176)); // Teal
        StyleConstants.setBold(ATTR_TYPE, true);

        StyleConstants.setForeground(ATTR_STRING, new Color(206, 145, 120)); // Orange-Brown
        StyleConstants.setForeground(ATTR_COMMENT, new Color(106, 153, 85)); // Green
        StyleConstants.setItalic(ATTR_COMMENT, true);

        StyleConstants.setForeground(ATTR_NUMBER, new Color(181, 206, 168)); // Light Green
        StyleConstants.setForeground(ATTR_PREPROCESSOR, new Color(155, 155, 155));
    }

    private static final Pattern PATTERN_KR = Pattern.compile(
            "(/\\*.*?\\*/|//.*?$)" +
            "|(\"(?:\\\\.|[^\"\\\\])*\")" +
            "|\\b(fn|let|mut|if|else|while|for|in|return|struct|enum|mod|use|pub|trait|impl|match|break|continue|true|false)\\b" +
            "|\\b(i8|i16|i32|i64|u8|u16|u32|u64|f32|f64|bool|char|string|void)\\b" +
            "|\\b(\\d+\\.?\\d*)\\b",
            Pattern.MULTILINE);

    private static final Pattern PATTERN_CPP = Pattern.compile(
            "(/\\*.*?\\*/|//.*?$)" +
            "|(\"(?:\\\\.|[^\"\\\\])*\")" +
            "|\\b(auto|break|case|catch|class|const|continue|default|delete|do|else|enum|explicit|export|extern|false|for|friend|goto|if|inline|mutable|namespace|new|noexcept|nullptr|operator|private|protected|public|register|return|sizeof|static|struct|switch|template|this|throw|true|try|typedef|typeid|typename|union|using|virtual|volatile|while)\\b" +
            "|\\b(bool|char|char16_t|char32_t|double|float|int|long|short|signed|unsigned|void|wchar_t|int8_t|int16_t|int32_t|int64_t|uint8_t|uint16_t|uint32_t|uint64_t|size_t)\\b" +
            "|(#\\s*\\w+)" +
            "|\\b(\\d+\\.?\\d*)\\b",
            Pattern.MULTILINE);

    public static void highlight(StyledDocument doc, String filename) {
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return;
        }

        // Reset default
        doc.setCharacterAttributes(0, text.length(), ATTR_DEFAULT, true);

        String fn = filename.toLowerCase(Locale.ROOT);
        Pattern p = fn.endsWith(".kr") || fn.endsWith(".rs") ? PATTERN_KR : PATTERN_CPP;

        Matcher m = p.matcher(text);
        while (m.find()) {
            if (m.group(1) != null) {
                doc.setCharacterAttributes(m.start(1), m.end(1) - m.start(1), ATTR_COMMENT, false);
            } else if (m.group(2) != null) {
                doc.setCharacterAttributes(m.start(2), m.end(2) - m.start(2), ATTR_STRING, false);
            } else if (m.group(3) != null) {
                doc.setCharacterAttributes(m.start(3), m.end(3) - m.start(3), ATTR_KEYWORD, false);
            } else if (m.group(4) != null) {
                doc.setCharacterAttributes(m.start(4), m.end(4) - m.start(4), ATTR_TYPE, false);
            } else if (m.groupCount() >= 5 && m.group(5) != null) {
                doc.setCharacterAttributes(m.start(5), m.end(5) - m.start(5), ATTR_NUMBER, false);
            }
        }
    }
}
