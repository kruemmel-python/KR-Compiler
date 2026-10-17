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
        setBackground(new Color(30, 30, 30));

        // Toolbar oben
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.setBackground(new Color(45, 45, 48));

        btnRun = new JButton("▶️ Ausführen");
        btnRun.setBackground(new Color(46, 125, 50));
        btnRun.setForeground(Color.WHITE);
        btnRun.setEnabled(false);
        btnRun.addActionListener(e -> runSelectedArtifact());
        toolbar.add(btnRun);

        btnOpenExplorer = new JButton("📂 Im Explorer anzeigen");
        btnOpenExplorer.setEnabled(false);
        btnOpenExplorer.addActionListener(e -> openSelectedInExplorer());
        toolbar.add(btnOpenExplorer);

        btnViewLog = new JButton("📄 Log im Editor öffnen");
        btnViewLog.addActionListener(e -> openBuildLog());
        toolbar.add(btnViewLog);

        JButton btnRefresh = new JButton("↻ Aktualisieren");
        btnRefresh.addActionListener(e -> refreshArtifacts());
        toolbar.add(btnRefresh);

        add(toolbar, BorderLayout.NORTH);

        String[] cols = {"Name", "Größe", "Typ", "Zuletzt geändert", "Pfad"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(tableModel);
        table.setBackground(new Color(25, 25, 25));
        table.setForeground(new Color(220, 220, 220));
        table.setGridColor(new Color(50, 50, 50));
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

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
