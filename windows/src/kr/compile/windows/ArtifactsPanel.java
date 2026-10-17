package kr.compile.windows;

import kr.compile.ArtifactsManager;
import kr.compile.DocumentManager;
import kr.compile.ProjectManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public final class ArtifactsPanel extends JPanel {

    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JButton btnRun;
    private final JButton btnOpenExplorer;
    private final JButton btnViewLog;
    private List<ArtifactsManager.ArtifactItem> currentItems;

    public ArtifactsPanel() {
        super(new BorderLayout());
        setBackground(DarkTheme.BG_PANEL);

        // Toolbar oben
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBackground(DarkTheme.BG_TOOLBAR);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER_DARK));

        btnRun = DarkTheme.createStyledButton("▶ Ausführen", DarkTheme.ACCENT_GREEN, Color.WHITE);
        btnRun.setEnabled(false);
        btnRun.addActionListener(e -> runSelectedArtifact());
        toolbar.add(btnRun);

        btnOpenExplorer = DarkTheme.createStyledButton("Im Explorer", DarkTheme.BG_BUTTON, DarkTheme.FG_PRIMARY);
        btnOpenExplorer.setEnabled(false);
        btnOpenExplorer.addActionListener(e -> openSelectedInExplorer());
        toolbar.add(btnOpenExplorer);

        btnViewLog = DarkTheme.createStyledButton("Log öffnen", DarkTheme.BG_BUTTON, DarkTheme.FG_PRIMARY);
        btnViewLog.addActionListener(e -> openBuildLog());
        toolbar.add(btnViewLog);

        JButton btnRefresh = DarkTheme.createStyledButton("Aktualisieren", DarkTheme.BG_BUTTON, DarkTheme.FG_PRIMARY);
        btnRefresh.addActionListener(e -> refreshArtifacts());
        toolbar.add(btnRefresh);

        add(toolbar, BorderLayout.NORTH);

        String[] cols = {"Name", "Größe", "Typ", "Zuletzt geändert", "Pfad"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(tableModel);
        table.setBackground(DarkTheme.BG_INPUT);
        table.setForeground(DarkTheme.FG_PRIMARY);
        table.setSelectionBackground(DarkTheme.BG_SELECT);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(DarkTheme.BORDER_DARK);
        table.setFont(DarkTheme.FONT_REGULAR);
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        table.getTableHeader().setBackground(DarkTheme.BG_TOOLBAR);
        table.getTableHeader().setForeground(DarkTheme.FG_PRIMARY);
        table.getTableHeader().setFont(DarkTheme.FONT_BOLD);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER_DARK));

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    runSelectedArtifact();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(DarkTheme.BG_INPUT);
        add(scroll, BorderLayout.CENTER);

        ProjectManager.getInstance().addListener(new ProjectManager.Listener() {
            @Override public void onProjectOpened(File p) { refreshArtifacts(); }
            @Override public void onProjectClosed() { clear(); }
            @Override public void onTreeRefreshed(File p) { refreshArtifacts(); }
        });
    }

    public void refreshArtifacts() {
        File root = ProjectManager.getInstance().getActiveProjectDir();
        if (root == null) {
            clear();
            return;
        }

        currentItems = ArtifactsManager.getInstance().getArtifacts(root);
        tableModel.setRowCount(0);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (ArtifactsManager.ArtifactItem item : currentItems) {
            String type = item.isExecutable() ? "Ausführbare Anwendung (.exe)" : (item.getName().endsWith(".log") ? "Protokolldatei" : "Build-Artefakt");
            tableModel.addRow(new Object[]{
                    item.getName(),
                    item.getFormattedSize(),
                    type,
                    sdf.format(new Date(item.getLastModified())),
                    item.getFile().getAbsolutePath()
            });
        }
        updateButtonStates();
    }

    private void clear() {
        tableModel.setRowCount(0);
        currentItems = null;
        updateButtonStates();
    }

    private void updateButtonStates() {
        int row = table.getSelectedRow();
        if (row >= 0 && currentItems != null && row < currentItems.size()) {
            ArtifactsManager.ArtifactItem item = currentItems.get(row);
            btnRun.setEnabled(item.isExecutable());
            btnOpenExplorer.setEnabled(true);
        } else {
            btnRun.setEnabled(false);
            btnOpenExplorer.setEnabled(false);
        }
    }

    private void runSelectedArtifact() {
        int row = table.getSelectedRow();
        if (row >= 0 && currentItems != null && row < currentItems.size()) {
            ArtifactsManager.ArtifactItem item = currentItems.get(row);
            if (item.isExecutable()) {
                try {
                    ArtifactsManager.getInstance().runArtifact(item.getFile());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Fehler beim Starten: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void openSelectedInExplorer() {
        int row = table.getSelectedRow();
        if (row >= 0 && currentItems != null && row < currentItems.size()) {
            ArtifactsManager.ArtifactItem item = currentItems.get(row);
            try {
                ArtifactsManager.getInstance().openInExplorer(item.getFile());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Fehler: " + ex.getMessage());
            }
        }
    }

    private void openBuildLog() {
        File root = ProjectManager.getInstance().getActiveProjectDir();
        if (root == null) return;
        File buildLog = new File(new File(root, "KR_BUILD"), "build.log");
        if (buildLog.isFile()) {
            try {
                DocumentManager.getInstance().openDocument(root, buildLog);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Fehler: " + ex.getMessage());
            }
        } else {
            JOptionPane.showMessageDialog(this, "Keine build.log gefunden. Bitte zuerst kompilieren.");
        }
    }
}
