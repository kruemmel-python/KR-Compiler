package kr.compile.windows;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class DarkTheme {

    public static final Color BG_DARKEST    = new Color(18, 18, 18);    // #121212
    public static final Color BG_EDITOR     = new Color(30, 30, 30);    // #1E1E1E
    public static final Color BG_PANEL      = new Color(37, 37, 38);    // #252526
    public static final Color BG_TOOLBAR    = new Color(45, 45, 48);    // #2D2D30
    public static final Color BG_BUTTON     = new Color(55, 55, 60);    // #37373C
    public static final Color BG_BUTTON_HOV = new Color(75, 75, 82);
    public static final Color BG_INPUT      = new Color(24, 24, 24);    // #181818
    public static final Color BG_SELECT     = new Color(9, 71, 113);    // #094771 (VS Code Selection)

    public static final Color ACCENT_BLUE   = new Color(0, 122, 204);   // #007ACC
    public static final Color ACCENT_GREEN  = new Color(46, 125, 50);   // #2E7D32
    public static final Color ACCENT_RED    = new Color(198, 40, 40);   // #C62828

    public static final Color FG_PRIMARY    = new Color(240, 240, 240); // Fast Weiß
    public static final Color FG_SECONDARY  = new Color(180, 180, 180);
    public static final Color FG_MUTED      = new Color(120, 120, 120);

    public static final Color BORDER_DARK   = new Color(55, 55, 58);

    public static final Font FONT_REGULAR   = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_BOLD      = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_TITLE     = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_CODE      = new Font("Consolas", Font.PLAIN, 14);

    private DarkTheme() {}

    public static void installDefaults() {
        UIManager.put("Panel.background", BG_PANEL);
        UIManager.put("Panel.foreground", FG_PRIMARY);

        UIManager.put("OptionPane.background", BG_PANEL);
        UIManager.put("OptionPane.foreground", FG_PRIMARY);
        UIManager.put("OptionPane.messageForeground", FG_PRIMARY);
        UIManager.put("OptionPane.messageArea.background", BG_PANEL);
        UIManager.put("OptionPane.buttonArea.background", BG_PANEL);

        UIManager.put("Label.foreground", FG_PRIMARY);
        UIManager.put("Label.font", FONT_REGULAR);

        UIManager.put("MenuBar.background", BG_TOOLBAR);
        UIManager.put("MenuBar.foreground", FG_PRIMARY);
        UIManager.put("MenuBar.border", BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_DARK));

        UIManager.put("Menu.background", BG_TOOLBAR);
        UIManager.put("Menu.foreground", FG_PRIMARY);
        UIManager.put("Menu.selectionBackground", BG_SELECT);
        UIManager.put("Menu.selectionForeground", Color.WHITE);
        UIManager.put("Menu.font", FONT_REGULAR);

        UIManager.put("MenuItem.background", BG_TOOLBAR);
        UIManager.put("MenuItem.foreground", FG_PRIMARY);
        UIManager.put("MenuItem.selectionBackground", BG_SELECT);
        UIManager.put("MenuItem.selectionForeground", Color.WHITE);
        UIManager.put("MenuItem.font", FONT_REGULAR);

        UIManager.put("PopupMenu.background", BG_TOOLBAR);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER_DARK));

        UIManager.put("Button.background", BG_BUTTON);
        UIManager.put("Button.foreground", FG_PRIMARY);
        UIManager.put("Button.font", FONT_BOLD);

        UIManager.put("TabbedPane.background", BG_TOOLBAR);
        UIManager.put("TabbedPane.foreground", FG_PRIMARY);
        UIManager.put("TabbedPane.selected", BG_EDITOR);
        UIManager.put("TabbedPane.selectHighlight", ACCENT_BLUE);
        UIManager.put("TabbedPane.contentAreaColor", BG_EDITOR);
        UIManager.put("TabbedPane.darkShadow", BG_DARKEST);
        UIManager.put("TabbedPane.shadow", BORDER_DARK);
        UIManager.put("TabbedPane.light", BG_PANEL);
        UIManager.put("TabbedPane.highlight", BG_PANEL);
        UIManager.put("TabbedPane.focus", BG_SELECT);

        UIManager.put("ScrollPane.background", BG_EDITOR);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());

        UIManager.put("Viewport.background", BG_EDITOR);

        UIManager.put("TextArea.background", BG_INPUT);
        UIManager.put("TextArea.foreground", FG_PRIMARY);
        UIManager.put("TextArea.caretForeground", Color.WHITE);
        UIManager.put("TextArea.font", FONT_CODE);

        UIManager.put("TextField.background", BG_INPUT);
        UIManager.put("TextField.foreground", FG_PRIMARY);
        UIManager.put("TextField.caretForeground", Color.WHITE);
        UIManager.put("TextField.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_DARK),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));

        UIManager.put("PasswordField.background", BG_INPUT);
        UIManager.put("PasswordField.foreground", FG_PRIMARY);
        UIManager.put("PasswordField.caretForeground", Color.WHITE);
        UIManager.put("PasswordField.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_DARK),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));

        UIManager.put("ComboBox.background", BG_BUTTON);
        UIManager.put("ComboBox.foreground", FG_PRIMARY);
        UIManager.put("ComboBox.selectionBackground", BG_SELECT);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);

        UIManager.put("CheckBox.background", BG_TOOLBAR);
        UIManager.put("CheckBox.foreground", FG_PRIMARY);

        UIManager.put("Table.background", BG_INPUT);
        UIManager.put("Table.foreground", FG_PRIMARY);
        UIManager.put("Table.selectionBackground", BG_SELECT);
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("Table.gridColor", BORDER_DARK);

        UIManager.put("TableHeader.background", BG_TOOLBAR);
        UIManager.put("TableHeader.foreground", FG_PRIMARY);
        UIManager.put("TableHeader.font", FONT_BOLD);

        UIManager.put("Tree.background", BG_PANEL);
        UIManager.put("Tree.textBackground", BG_PANEL);
        UIManager.put("Tree.textForeground", FG_PRIMARY);
        UIManager.put("Tree.selectionBackground", BG_SELECT);
        UIManager.put("Tree.selectionForeground", Color.WHITE);
        UIManager.put("Tree.selectionBorderColor", ACCENT_BLUE);

        UIManager.put("SplitPane.background", BG_TOOLBAR);
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
    }

    public static JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setUI(new BasicButtonUI());
        btn.setBackground(bg != null ? bg : BG_BUTTON);
        btn.setForeground(fg != null ? fg : FG_PRIMARY);
        btn.setFont(FONT_BOLD);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_DARK, 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Color normalBg = btn.getBackground();
        Color hoverBg = bg != null ? bg.brighter() : BG_BUTTON_HOV;

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (btn.isEnabled()) btn.setBackground(hoverBg); }
            @Override public void mouseExited(MouseEvent e) { btn.setBackground(normalBg); }
        });
        return btn;
    }

    public static JButton createIconButton(String iconText, String tooltip) {
        JButton btn = new JButton(iconText);
        btn.setUI(new BasicButtonUI());
        btn.setBackground(BG_BUTTON);
        btn.setForeground(FG_PRIMARY);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(28, 26));
        btn.setBorder(BorderFactory.createLineBorder(BORDER_DARK, 1));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (btn.isEnabled()) btn.setBackground(BG_BUTTON_HOV); }
            @Override public void mouseExited(MouseEvent e) { btn.setBackground(BG_BUTTON); }
        });
        return btn;
    }
}
