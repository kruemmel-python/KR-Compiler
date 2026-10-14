package kr.compile;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CodeEditorView extends LinearLayout {

    public interface OnModifiedListener {
        void onModified(DocumentModel doc);
    }

    private DocumentModel currentDoc = null;
    private OnModifiedListener modifiedListener = null;

    private ScrollView verticalScroll;
    private HorizontalScrollView horizontalScroll;
    private TextView lineNumberGutter;
    private EditText editorText;
    private LinearLayout searchBar;
    private EditText searchInput;
    private EditText replaceInput;
    private TextView searchMatchLabel;

    private float fontSizeSp = 17f;
    private boolean isDarkTheme = true;
    private boolean autoBrackets = true;
    private boolean autoIndent = true;
    private boolean wrapLines = false;
    private int tabWidth = 4;

    private boolean isUpdatingText = false;
    private final Handler syntaxHandler = new Handler(Looper.getMainLooper());
    private Runnable syntaxRunnable = null;

    private final List<Integer> searchMatches = new ArrayList<>();
    private int currentSearchIndex = -1;

    public CodeEditorView(Context context) {
        super(context);
        init();
    }

    public CodeEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 1. Search Bar (initially hidden)
        searchBar = createSearchBar();
        searchBar.setVisibility(GONE);
        addView(searchBar);

        // 2. Editor Container
        verticalScroll = new ScrollView(getContext()) {
            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                if (currentDoc != null) {
                    currentDoc.setScroll(horizontalScroll != null ? horizontalScroll.getScrollX() : 0, t);
                }
            }
        };
        verticalScroll.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        verticalScroll.setFillViewport(true);

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Line number gutter
        lineNumberGutter = new TextView(getContext());
        lineNumberGutter.setTypeface(Typeface.MONOSPACE);
        lineNumberGutter.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
        lineNumberGutter.setLineSpacing(0f, 1.4f);
        lineNumberGutter.setGravity(Gravity.END);
        lineNumberGutter.setPadding(dp(10), dp(12), dp(10), dp(12));
        lineNumberGutter.setText("1");
        row.addView(lineNumberGutter, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // Horizontal scroll container for code
        horizontalScroll = new HorizontalScrollView(getContext()) {
            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                if (currentDoc != null) {
                    currentDoc.setScroll(l, verticalScroll.getScrollY());
                }
            }
        };
        horizontalScroll.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        horizontalScroll.setFillViewport(true);

        editorText = new EditText(getContext());
        editorText.setTypeface(Typeface.MONOSPACE);
        editorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
        editorText.setLineSpacing(0f, 1.4f);
        editorText.setPadding(dp(8), dp(12), dp(16), dp(12));
        editorText.setGravity(Gravity.TOP | Gravity.START);
        editorText.setBackground(null);
        editorText.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editorText.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN);
        editorText.setHorizontallyScrolling(!wrapLines);

        horizontalScroll.addView(editorText, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(horizontalScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        verticalScroll.addView(row);
        addView(verticalScroll);

        setupTextWatcher();
        applyTheme();
    }

    private LinearLayout createSearchBar() {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setBackgroundColor(Color.parseColor("#21252B"));

        // Row 1: Search input + nav buttons + close
        LinearLayout row1 = new LinearLayout(getContext());
        row1.setOrientation(HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        searchInput = new EditText(getContext());
        searchInput.setHint("Suchen…");
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        searchInput.setSingleLine(true);
        searchInput.setBackgroundColor(Color.parseColor("#282C34"));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setHintTextColor(Color.GRAY);
        searchInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        row1.addView(searchInput, new LayoutParams(0, dp(40), 1f));

        searchMatchLabel = new TextView(getContext());
        searchMatchLabel.setText("0/0");
        searchMatchLabel.setTextColor(Color.LTGRAY);
        searchMatchLabel.setPadding(dp(6), 0, dp(6), 0);
        row1.addView(searchMatchLabel);

        Button btnPrev = createSmallButton("▲", v -> findPrevious());
        row1.addView(btnPrev);

        Button btnNext = createSmallButton("▼", v -> findNext());
        row1.addView(btnNext);

        Button btnClose = createSmallButton("✕", v -> hideSearchBar());
        row1.addView(btnClose);
        bar.addView(row1);

        // Row 2: Replace input + buttons
        LinearLayout row2 = new LinearLayout(getContext());
        row2.setOrientation(HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        row2.setPadding(0, dp(4), 0, 0);

        replaceInput = new EditText(getContext());
        replaceInput.setHint("Ersetzen durch…");
        replaceInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        replaceInput.setSingleLine(true);
        replaceInput.setBackgroundColor(Color.parseColor("#282C34"));
        replaceInput.setTextColor(Color.WHITE);
        replaceInput.setHintTextColor(Color.GRAY);
        replaceInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        row2.addView(replaceInput, new LayoutParams(0, dp(40), 1f));

        Button btnRep = createSmallButton("Ersetzen", v -> replaceCurrent());
        row2.addView(btnRep);

        Button btnRepAll = createSmallButton("Alle", v -> replaceAll());
        row2.addView(btnRepAll);

        bar.addView(row2);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                performSearch(s.toString());
            }
        });

        return bar;
    }

    private Button createSmallButton(String text, OnClickListener listener) {
        Button b = new Button(getContext());
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        b.setOnClickListener(listener);
        b.setMinimumWidth(dp(44));
        b.setMinimumHeight(dp(40));
        b.setPadding(dp(6), 0, dp(6), 0);
        return b;
    }

    private void setupTextWatcher() {
        editorText.addTextChangedListener(new TextWatcher() {
            private int changeStart;
            private int changeBefore;
            private int changeAfter;
            private String oldText;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (isUpdatingText) return;
                changeStart = start;
                changeBefore = count;
                changeAfter = after;
                oldText = s.subSequence(start, start + count).toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdatingText) return;
                handleAutoPair(s, start, before, count);
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingText) return;
                updateLineNumbers();

                if (currentDoc == null) {
                    currentDoc = DocumentManager.getInstance().openDocument("main.cpp", s.toString(), null, null);
                }
                String newText = s.subSequence(changeStart, changeStart + changeAfter).toString();
                currentDoc.onTextChanged(s.toString(), changeStart, oldText, newText);
                currentDoc.setCursor(editorText.getSelectionStart(), editorText.getSelectionEnd());
                if (modifiedListener != null) {
                    modifiedListener.onModified(currentDoc);
                }

                scheduleSyntaxHighlight();
            }
        });
    }

    public static char getClosingPair(char openChar) {
        if (openChar == '(') return ')';
        if (openChar == '{') return '}';
        if (openChar == '[') return ']';
        if (openChar == '"') return '"';
        if (openChar == '\'') return '\'';
        return 0;
    }

    public static String calculateAutoIndent(String full, int newlinePos, int tabWidth) {
        if (full == null || newlinePos <= 0 || newlinePos > full.length()) return "";
        int lineStart = full.lastIndexOf('\n', newlinePos - 1);
        lineStart = (lineStart == -1) ? 0 : lineStart + 1;
        String prevLine = full.substring(lineStart, newlinePos);

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < prevLine.length(); i++) {
            char c = prevLine.charAt(i);
            if (c == ' ' || c == '\t') indent.append(c);
            else break;
        }

        String trimmed = prevLine.trim();
        if (trimmed.endsWith("{")) {
            for (int i = 0; i < tabWidth; i++) indent.append(' ');
        }
        return indent.toString();
    }

    private void handleAutoPair(CharSequence s, int start, int before, int count) {
        if (!autoBrackets || count != 1 || before != 0) return;
        char inserted = s.charAt(start);
        char closePair = getClosingPair(inserted);

        if (closePair != 0) {
            isUpdatingText = true;
            Editable e = editorText.getText();
            e.insert(start + 1, String.valueOf(closePair));
            editorText.setSelection(start + 1);
            isUpdatingText = false;
        } else if (autoIndent && inserted == '\n') {
            handleAutoIndent(start);
        }
    }

    private void handleAutoIndent(int newlinePos) {
        Editable e = editorText.getText();
        String full = e.toString();
        String indent = calculateAutoIndent(full, newlinePos, tabWidth);
        if (indent != null && !indent.isEmpty()) {
            isUpdatingText = true;
            e.insert(newlinePos + 1, indent);
            editorText.setSelection(newlinePos + 1 + indent.length());
            isUpdatingText = false;
        }
    }

    private void updateLineNumbers() {
        int lineCount = editorText.getLineCount();
        if (lineCount <= 0) lineCount = 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lineCount; i++) {
            sb.append(i).append('\n');
        }
        lineNumberGutter.setText(sb.toString());
    }

    public void scheduleSyntaxHighlight() {
        if (syntaxRunnable != null) {
            syntaxHandler.removeCallbacks(syntaxRunnable);
        }
        syntaxRunnable = () -> {
            if (currentDoc != null && editorText != null) {
                int len = editorText.length();
                int sel = editorText.getSelectionStart();
                int start = Math.max(0, sel - 2500);
                int end = Math.min(len, sel + 2500);
                CppSyntaxHighlighter.highlight(editorText.getText(), currentDoc.getDisplayName(), isDarkTheme, start, end);
            }
        };
        syntaxHandler.postDelayed(syntaxRunnable, 100);
    }

    public void setDocument(DocumentModel doc) {
        this.currentDoc = doc;
        isUpdatingText = true;
        if (doc != null) {
            editorText.setText(doc.getText());
            int sel = Math.min(doc.getCursorPosition(), editorText.length());
            editorText.setSelection(sel);
            updateLineNumbers();
            post(() -> {
                verticalScroll.scrollTo(0, doc.getScrollY());
                horizontalScroll.scrollTo(doc.getScrollX(), 0);
            });
            CppSyntaxHighlighter.highlight(editorText.getText(), doc.getDisplayName(), isDarkTheme, 0, editorText.length());
        } else {
            editorText.setText("");
            lineNumberGutter.setText("1");
        }
        isUpdatingText = false;
    }

    public void openDocument(DocumentModel doc) {
        setDocument(doc);
    }

    public void setOnModifiedListener(OnModifiedListener l) {
        this.modifiedListener = l;
    }

    public void setFontSize(float sp) {
        this.fontSizeSp = Math.max(12f, Math.min(32f, sp));
        lineNumberGutter.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
        editorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
    }

    public float getFontSize() { return fontSizeSp; }

    public void setDarkTheme(boolean dark) {
        this.isDarkTheme = dark;
        applyTheme();
        if (currentDoc != null) {
            CppSyntaxHighlighter.highlight(editorText.getText(), currentDoc.getDisplayName(), isDarkTheme, 0, editorText.length());
        }
    }

    public void setAutoBrackets(boolean enable) { this.autoBrackets = enable; }
    public void setAutoIndent(boolean enable) { this.autoIndent = enable; }
    public void setTabWidth(int width) { this.tabWidth = Math.max(1, width); }
    public void setWrapLines(boolean wrap) {
        this.wrapLines = wrap;
        editorText.setHorizontallyScrolling(!wrap);
    }

    private void applyTheme() {
        if (isDarkTheme) {
            setBackgroundColor(Color.parseColor("#181A1F"));
            lineNumberGutter.setBackgroundColor(Color.parseColor("#1E2227"));
            lineNumberGutter.setTextColor(Color.parseColor("#5C6370"));
            editorText.setTextColor(Color.parseColor("#ABB2BF"));
        } else {
            setBackgroundColor(Color.parseColor("#FAFAFA"));
            lineNumberGutter.setBackgroundColor(Color.parseColor("#F0F0F0"));
            lineNumberGutter.setTextColor(Color.parseColor("#A0A1A7"));
            editorText.setTextColor(Color.parseColor("#383A42"));
        }
    }

    public void insertTextAtCursor(String text) {
        int start = Math.max(editorText.getSelectionStart(), 0);
        int end = Math.max(editorText.getSelectionEnd(), 0);
        editorText.getText().replace(Math.min(start, end), Math.max(start, end), text);
    }

    public void moveCursor(int offset) {
        int pos = editorText.getSelectionStart() + offset;
        pos = Math.max(0, Math.min(editorText.length(), pos));
        editorText.setSelection(pos);
    }

    public void indentSelection() {
        StringBuilder spaces = new StringBuilder();
        for (int i = 0; i < tabWidth; i++) spaces.append(' ');
        insertTextAtCursor(spaces.toString());
    }

    public void unindentSelection() {
        int start = editorText.getSelectionStart();
        if (start <= 0) return;
        Editable e = editorText.getText();
        int lineStart = e.toString().lastIndexOf('\n', start - 1);
        lineStart = (lineStart == -1) ? 0 : lineStart + 1;
        int deleteCount = 0;
        for (int i = 0; i < tabWidth && (lineStart + i) < e.length(); i++) {
            if (e.charAt(lineStart + i) == ' ') deleteCount++;
            else break;
        }
        if (deleteCount > 0) {
            e.delete(lineStart, lineStart + deleteCount);
        }
    }

    public void undo() {
        if (currentDoc == null || !currentDoc.canUndo()) return;
        DocumentModel.EditAction action = currentDoc.popUndo();
        if (action == null) return;
        isUpdatingText = true;
        Editable e = editorText.getText();
        int end = Math.min(action.start + (action.newText != null ? action.newText.length() : 0), e.length());
        e.replace(action.start, end, action.oldText != null ? action.oldText : "");
        int newPos = Math.min(action.start + (action.oldText != null ? action.oldText.length() : 0), e.length());
        editorText.setSelection(newPos);
        currentDoc.onTextChanged(e.toString(), action.start, null, null);
        currentDoc.setCursor(newPos, newPos);
        updateLineNumbers();
        isUpdatingText = false;
        scheduleSyntaxHighlight();
        if (modifiedListener != null) modifiedListener.onModified(currentDoc);
    }

    public void redo() {
        if (currentDoc == null || !currentDoc.canRedo()) return;
        DocumentModel.EditAction action = currentDoc.popRedo();
        if (action == null) return;
        isUpdatingText = true;
        Editable e = editorText.getText();
        int end = Math.min(action.start + (action.oldText != null ? action.oldText.length() : 0), e.length());
        e.replace(action.start, end, action.newText != null ? action.newText : "");
        int newPos = Math.min(action.start + (action.newText != null ? action.newText.length() : 0), e.length());
        editorText.setSelection(newPos);
        currentDoc.onTextChanged(e.toString(), action.start, null, null);
        currentDoc.setCursor(newPos, newPos);
        updateLineNumbers();
        isUpdatingText = false;
        scheduleSyntaxHighlight();
        if (modifiedListener != null) modifiedListener.onModified(currentDoc);
    }

    public void jumpToLine(int line, int col) {
        if (line <= 0) line = 1;
        String text = editorText.getText().toString();
        int currentLine = 1;
        int pos = 0;
        for (int i = 0; i < text.length(); i++) {
            if (currentLine == line) {
                pos = i;
                break;
            }
            if (text.charAt(i) == '\n') currentLine++;
        }
        pos = Math.min(text.length(), pos + Math.max(0, col - 1));
        editorText.setSelection(pos);
        editorText.requestFocus();

        // Scroll to cursor
        int lineIndex = Math.max(0, line - 1);
        int layoutTop = editorText.getLayout() != null ? editorText.getLayout().getLineTop(lineIndex) : 0;
        verticalScroll.smoothScrollTo(0, Math.max(0, layoutTop - dp(80)));
    }

    public void showGoToLineDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Gehe zu Zeile");
        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Zeilennummer");
        builder.setView(input);
        builder.setPositiveButton("Springen", (d, w) -> {
            try {
                int line = Integer.parseInt(input.getText().toString().trim());
                jumpToLine(line, 1);
            } catch (Exception ignored) {}
        });
        builder.setNegativeButton("Abbrechen", null);
        builder.show();
    }

    public void toggleSearchBar() {
        if (searchBar.getVisibility() == VISIBLE) {
            hideSearchBar();
        } else {
            searchBar.setVisibility(VISIBLE);
            searchInput.requestFocus();
        }
    }

    public void hideSearchBar() {
        searchBar.setVisibility(GONE);
        clearSearchHighlight();
        editorText.requestFocus();
    }

    private void performSearch(String query) {
        searchMatches.clear();
        currentSearchIndex = -1;
        clearSearchHighlight();

        if (query == null || query.isEmpty()) {
            searchMatchLabel.setText("0/0");
            return;
        }

        String content = editorText.getText().toString().toLowerCase(Locale.ROOT);
        String q = query.toLowerCase(Locale.ROOT);
        int idx = 0;
        while ((idx = content.indexOf(q, idx)) >= 0) {
            searchMatches.add(idx);
            idx += q.length();
        }

        if (!searchMatches.isEmpty()) {
            currentSearchIndex = 0;
            highlightSearchMatch(searchMatches.get(0), query.length());
            searchMatchLabel.setText("1/" + searchMatches.size());
        } else {
            searchMatchLabel.setText("0/0");
        }
    }

    private void findNext() {
        if (searchMatches.isEmpty()) return;
        currentSearchIndex = (currentSearchIndex + 1) % searchMatches.size();
        String q = searchInput.getText().toString();
        highlightSearchMatch(searchMatches.get(currentSearchIndex), q.length());
        searchMatchLabel.setText((currentSearchIndex + 1) + "/" + searchMatches.size());
    }

    private void findPrevious() {
        if (searchMatches.isEmpty()) return;
        currentSearchIndex = (currentSearchIndex - 1 + searchMatches.size()) % searchMatches.size();
        String q = searchInput.getText().toString();
        highlightSearchMatch(searchMatches.get(currentSearchIndex), q.length());
        searchMatchLabel.setText((currentSearchIndex + 1) + "/" + searchMatches.size());
    }

    private void highlightSearchMatch(int start, int length) {
        editorText.setSelection(start, start + length);
        int line = editorText.getLayout() != null ? editorText.getLayout().getLineForOffset(start) : 0;
        int top = editorText.getLayout() != null ? editorText.getLayout().getLineTop(line) : 0;
        verticalScroll.smoothScrollTo(0, Math.max(0, top - dp(60)));
    }

    private void clearSearchHighlight() {
        // Selection is maintained naturally
    }

    private void replaceCurrent() {
        if (currentSearchIndex >= 0 && currentSearchIndex < searchMatches.size()) {
            int pos = searchMatches.get(currentSearchIndex);
            String q = searchInput.getText().toString();
            String rep = replaceInput.getText().toString();
            editorText.getText().replace(pos, pos + q.length(), rep);
            performSearch(q);
        }
    }

    private void replaceAll() {
        String q = searchInput.getText().toString();
        String rep = replaceInput.getText().toString();
        if (q.isEmpty()) return;
        String full = editorText.getText().toString();
        String replaced = full.replace(q, rep);
        editorText.setText(replaced);
        performSearch(q);
        Toast.makeText(getContext(), "Ersetzungen durchgeführt", Toast.LENGTH_SHORT).show();
    }

    public EditText getEditText() { return editorText; }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
