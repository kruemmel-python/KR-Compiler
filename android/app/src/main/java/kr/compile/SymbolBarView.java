package kr.compile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

public final class SymbolBarView extends HorizontalScrollView {

    public interface OnSymbolActionListener {
        void onInsertText(String symbol);
        void onTab();
        void onIndent();
        void onUnindent();
        void onCursorMove(int offset);
        void onUndo();
        void onRedo();
    }

    private OnSymbolActionListener listener = null;
    private LinearLayout container;
    private boolean isDarkTheme = true;

    private static final String[] SYMBOLS = {
            "Tab", "⇥", "⇤", "◀", "▶", "↶", "↷",
            "{", "}", "(", ")", "[", "]", ";", "<", ">",
            "=", "_", "\"", "'", "#", "&", "|", "*", "/", "\\"
    };

    public SymbolBarView(Context context) {
        super(context);
        init();
    }

    public SymbolBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setHorizontalScrollBarEnabled(false);
        setFillViewport(true);

        container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(dp(4), dp(4), dp(4), dp(4));

        for (String sym : SYMBOLS) {
            Button btn = createButton(sym);
            container.addView(btn);
        }

        addView(container);
        applyTheme();
    }

    public void setOnSymbolActionListener(OnSymbolActionListener listener) {
        this.listener = listener;
    }

    public void setDarkTheme(boolean dark) {
        this.isDarkTheme = dark;
        applyTheme();
    }

    private void applyTheme() {
        int barBg = isDarkTheme ? Color.parseColor("#1E2227") : Color.parseColor("#EAEAEA");
        setBackgroundColor(barBg);

        int btnBg = isDarkTheme ? Color.parseColor("#282C34") : Color.parseColor("#FFFFFF");
        int btnFg = isDarkTheme ? Color.parseColor("#ABB2BF") : Color.parseColor("#383A42");

        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof Button) {
                Button b = (Button) child;
                b.setBackgroundColor(btnBg);
                b.setTextColor(btnFg);
            }
        }
    }

    private Button createButton(final String text) {
        Button b = new Button(getContext());
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);

        // Touch target at least 48dp x 48dp
        int minSize = dp(48);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                text.length() > 2 ? dp(60) : minSize,
                minSize
        );
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        b.setPadding(dp(4), dp(4), dp(4), dp(4));

        // Prevent button from stealing focus from editor
        b.setFocusable(false);
        b.setFocusableInTouchMode(false);

        b.setOnClickListener(v -> {
            if (listener == null) return;
            switch (text) {
                case "Tab":
                    listener.onTab();
                    break;
                case "⇥":
                    listener.onIndent();
                    break;
                case "⇤":
                    listener.onUnindent();
                    break;
                case "◀":
                    listener.onCursorMove(-1);
                    break;
                case "▶":
                    listener.onCursorMove(1);
                    break;
                case "↶":
                    listener.onUndo();
                    break;
                case "↷":
                    listener.onRedo();
                    break;
                default:
                    listener.onInsertText(text);
                    break;
            }
        });

        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
