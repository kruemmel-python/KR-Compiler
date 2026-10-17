package kr.compile.windows;

import kr.compile.DocumentManager;
import kr.compile.DocumentModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.Element;
import java.awt.*;
import java.util.*;

public final class CodeEditorPanel extends JPanel implements DocumentManager.Listener {

    private final JPanel container;
    private final CardLayout cardLayout;
    private final JTabbedPane tabbedPane;
    private final JPanel emptyPanel;
    private final JLabel statusLabel;
    private final Map<DocumentModel, JTextPane> editorMap = new HashMap<>();
    private final Map<DocumentModel, JPanel> tabComponentMap = new HashMap<>();
    private boolean updating = false;

    public CodeEditorPanel(Runnable onOpenProject, Runnable onNewProject, Runnable onOpenAgent) {
        super(new BorderLayout());
        setBackground(DarkTheme.BG_EDITOR);

        statusLabel = new JLabel("Bereit | UTF-8");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        statusLabel.setForeground(DarkTheme.FG_MUTED);
        statusLabel.setBackground(DarkTheme.BG_DARKEST);
        statusLabel.setOpaque(true);

        cardLayout = new CardLayout();
        container = new JPanel(cardLayout);
        container.setBackground(DarkTheme.BG_EDITOR);

        // 1. Schöner leerer Zustand (kein grelles Weiß mehr!)
        emptyPanel = new JPanel(new GridBagLayout());
        emptyPanel.setBackground(DarkTheme.BG_EDITOR);

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);

        JLabel lblTitle = new JLabel("KR Compiler IDE");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 26));
        lblTitle.setForeground(DarkTheme.ACCENT_BLUE);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSub = new JLabel("KR Unified Language (.kr) • C++20 • Rust • Go");
        lblSub.setFont(DarkTheme.FONT_TITLE);
        lblSub.setForeground(DarkTheme.FG_SECONDARY);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblHint = new JLabel("Wählen Sie eine Datei im Explorer links oder nutzen Sie die Schnellaktionen:");
        lblHint.setFont(DarkTheme.FONT_REGULAR);
        lblHint.setForeground(DarkTheme.FG_MUTED);
        lblHint.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnOpen = DarkTheme.createStyledButton("📂 Projektordner öffnen …", DarkTheme.BG_BUTTON, DarkTheme.FG_PRIMARY);
        btnOpen.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnOpen.setMaximumSize(new Dimension(280, 36));
        btnOpen.addActionListener(e -> { if (onOpenProject != null) onOpenProject.run(); });

        JButton btnNew = DarkTheme.createStyledButton("✨ Neues Projekt erstellen …", DarkTheme.BG_BUTTON, DarkTheme.FG_PRIMARY);
        btnNew.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnNew.setMaximumSize(new Dimension(280, 36));
        btnNew.addActionListener(e -> { if (onNewProject != null) onNewProject.run(); });

        JButton btnAgent = DarkTheme.createStyledButton("🤖 Autonomen KI-Agenten öffnen", DarkTheme.BG_BUTTON, DarkTheme.FG_PRIMARY);
        btnAgent.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnAgent.setMaximumSize(new Dimension(280, 36));
        btnAgent.addActionListener(e -> { if (onOpenAgent != null) onOpenAgent.run(); });

        box.add(lblTitle);
        box.add(Box.createVerticalStrut(8));
        box.add(lblSub);
        box.add(Box.createVerticalStrut(18));
        box.add(lblHint);
        box.add(Box.createVerticalStrut(20));
        box.add(btnOpen);
        box.add(Box.createVerticalStrut(10));
        box.add(btnNew);
        box.add(Box.createVerticalStrut(10));
        box.add(btnAgent);

        emptyPanel.add(box);

        // 2. TabbedPane Ansicht für geöffnete Dokumente
        tabbedPane = new JTabbedPane();
        tabbedPane.setUI(new BasicTabbedPaneUI());
        tabbedPane.setBackground(DarkTheme.BG_TOOLBAR);
        tabbedPane.setForeground(DarkTheme.FG_PRIMARY);
        tabbedPane.setOpaque(true);

        tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            if (idx >= 0) {
                java.util.List<DocumentModel> open = DocumentManager.getInstance().getOpenDocuments();
                if (idx < open.size()) {
                    DocumentManager.getInstance().setActiveDocument(open.get(idx));
                    updateStatusForActive();
                }
            } else {
                statusLabel.setText("Keine Datei geöffnet");
            }
        });

        container.add(emptyPanel, "EMPTY");
        container.add(tabbedPane, "TABS");

        add(container, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        updateViewMode();
        DocumentManager.getInstance().addListener(this);
    }

    private void updateViewMode() {
        if (tabbedPane.getTabCount() == 0) {
            cardLayout.show(container, "EMPTY");
            statusLabel.setText("Keine Datei geöffnet | Bereit");
        } else {
            cardLayout.show(container, "TABS");
            updateStatusForActive();
        }
    }

    private void updateStatusForActive() {
        DocumentModel doc = DocumentManager.getInstance().getActiveDocument();
        if (doc == null) {
            statusLabel.setText("Keine Datei geöffnet");
            return;
        }
        JTextPane pane = editorMap.get(doc);
        if (pane != null) {
            int caret = pane.getCaretPosition();
            Element root = pane.getDocument().getDefaultRootElement();
            int line = root.getElementIndex(caret) + 1;
            int col = caret - root.getElement(line - 1).getStartOffset() + 1;
            statusLabel.setText("Zeile " + line + ", Spalte " + col + " | " + doc.getContent().length() + " Zeichen | " + doc.getName() + " | UTF-8");
        } else {
            statusLabel.setText(doc.getName() + " | UTF-8");
        }
    }

    @Override
    public void onDocumentOpened(DocumentModel doc) {
        if (editorMap.containsKey(doc)) return;

        JTextPane editor = new JTextPane();
        editor.setFont(DarkTheme.FONT_CODE);
        editor.setBackground(DarkTheme.BG_EDITOR);
        editor.setForeground(DarkTheme.FG_PRIMARY);
        editor.setCaretColor(Color.WHITE);
        editor.setText(doc.getContent());
        SyntaxHighlighting.highlight(editor.getStyledDocument(), doc.getName());

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { handleEdit(); }
            @Override public void removeUpdate(DocumentEvent e) { handleEdit(); }
            @Override public void changedUpdate(DocumentEvent e) {}

            private void handleEdit() {
                if (updating) return;
                doc.setContent(editor.getText());
                DocumentManager.getInstance().notifyModified(doc);
                SwingUtilities.invokeLater(() -> SyntaxHighlighting.highlight(editor.getStyledDocument(), doc.getName()));
            }
        });

        editor.addCaretListener(e -> updateStatusForActive());

        JScrollPane scroll = new JScrollPane(editor);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(DarkTheme.BG_EDITOR);
        scroll.setBackground(DarkTheme.BG_EDITOR);

        editorMap.put(doc, editor);

        // Tab Header mit Schließen-Button & Modifiziert-Stern
        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        tabHeader.setOpaque(false);
        JLabel titleLabel = new JLabel(doc.getName());
        titleLabel.setForeground(DarkTheme.FG_PRIMARY);
        titleLabel.setFont(DarkTheme.FONT_REGULAR);

        JButton btnClose = new JButton("×");
        btnClose.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        btnClose.setBorder(null);
        btnClose.setContentAreaFilled(false);
        btnClose.setForeground(DarkTheme.FG_MUTED);
        btnClose.setFont(new Font("Arial", Font.BOLD, 14));
        btnClose.setPreferredSize(new Dimension(16, 16));
        btnClose.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClose.addActionListener(e -> DocumentManager.getInstance().closeDocument(doc));

        tabHeader.add(titleLabel);
        tabHeader.add(btnClose);
        tabComponentMap.put(doc, tabHeader);

        tabbedPane.addTab(null, scroll);
        int idx = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(idx, tabHeader);
        tabbedPane.setSelectedIndex(idx);

        updateViewMode();
    }

    @Override
    public void onDocumentClosed(DocumentModel doc) {
        JTextPane pane = editorMap.remove(doc);
        JPanel header = tabComponentMap.remove(doc);
        if (pane != null) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                if (tabbedPane.getComponentAt(i) instanceof JScrollPane scroll) {
                    if (scroll.getViewport().getView() == pane) {
                        tabbedPane.removeTabAt(i);
                        break;
                    }
                }
            }
        }
        updateViewMode();
    }

    @Override
    public void onActiveDocumentChanged(DocumentModel doc) {
        if (doc == null) {
            tabbedPane.setSelectedIndex(-1);
            updateViewMode();
            return;
        }
        JTextPane pane = editorMap.get(doc);
        if (pane != null) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                if (tabbedPane.getComponentAt(i) instanceof JScrollPane scroll) {
                    if (scroll.getViewport().getView() == pane) {
                        tabbedPane.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }
        updateViewMode();
        updateStatusForActive();
    }

    @Override
    public void onDocumentModified(DocumentModel doc) {
        JPanel header = tabComponentMap.get(doc);
        if (header != null && header.getComponent(0) instanceof JLabel label) {
            label.setText(doc.getName() + (doc.isModified() ? " *" : ""));
            label.setForeground(doc.isModified() ? new Color(255, 180, 80) : DarkTheme.FG_PRIMARY);
        }
    }

    @Override
    public void onDocumentSaved(DocumentModel doc) {
        onDocumentModified(doc);
    }
}
