package kr.compile.windows;

import kr.compile.DocumentManager;
import kr.compile.DocumentModel;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;

public final class CodeEditorPanel extends JPanel implements DocumentManager.Listener {

    private final JTabbedPane tabbedPane;
    private final JLabel statusLabel;
    private final Map<DocumentModel, JTextPane> editorMap = new HashMap<>();
    private final Map<DocumentModel, JPanel> tabComponentMap = new HashMap<>();
    private boolean updating = false;

    public CodeEditorPanel() {
        super(new BorderLayout());
        setBackground(new Color(30, 30, 30));

        statusLabel = new JLabel("Bereit | UTF-8");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setForeground(new Color(150, 150, 150));
        statusLabel.setBackground(new Color(25, 25, 25));
        statusLabel.setOpaque(true);

        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(new Color(37, 37, 38));
        tabbedPane.setForeground(new Color(200, 200, 200));

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

        add(tabbedPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        DocumentManager.getInstance().addListener(this);
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
        editor.setFont(new Font("Consolas", Font.PLAIN, 14));
        editor.setBackground(new Color(30, 30, 30));
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

        editorMap.put(doc, editor);

        // Custom Tab Header mit Schließen-Button & Modifiziert-Stern
        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabHeader.setOpaque(false);
        JLabel titleLabel = new JLabel(doc.getName());
        titleLabel.setForeground(Color.LIGHT_GRAY);
        JButton btnClose = new JButton("×");
        btnClose.setBorder(null);
        btnClose.setContentAreaFilled(false);
        btnClose.setForeground(Color.GRAY);
        btnClose.setPreferredSize(new Dimension(16, 16));
        btnClose.addActionListener(e -> DocumentManager.getInstance().closeDocument(doc));

        tabHeader.add(titleLabel);
        tabHeader.add(btnClose);
        tabComponentMap.put(doc, tabHeader);

        tabbedPane.addTab(null, scroll);
        int idx = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(idx, tabHeader);
        tabbedPane.setSelectedIndex(idx);
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
    }

    @Override
    public void onActiveDocumentChanged(DocumentModel doc) {
        if (doc == null) {
            tabbedPane.setSelectedIndex(-1);
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
        updateStatusForActive();
    }

    @Override
    public void onDocumentModified(DocumentModel doc) {
        JPanel header = tabComponentMap.get(doc);
        if (header != null && header.getComponent(0) instanceof JLabel label) {
            label.setText(doc.getName() + (doc.isModified() ? " *" : ""));
            label.setForeground(doc.isModified() ? new Color(255, 180, 80) : Color.LIGHT_GRAY);
        }
    }

    @Override
    public void onDocumentSaved(DocumentModel doc) {
        onDocumentModified(doc);
    }
}
