package kr.compile.windows;

import kr.compile.BuildManager;
import kr.compile.CompilerEngine;
import kr.compile.ProjectManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public final class BuildConsolePanel extends JPanel implements BuildManager.Listener {

    private final JComboBox<CompilerEngine.Target> targetCombo;
    private final JButton btnBuild;
    private final JButton btnStop;
    private final JButton btnClear;
    private final JCheckBox chkOnline;
    private final JTextArea console;
    private final JLabel statusLabel;

    public BuildConsolePanel() {
        super(new BorderLayout());
        setBackground(DarkTheme.BG_PANEL);

        console = new JTextArea();
        console.setEditable(false);
        console.setBackground(DarkTheme.BG_INPUT);
        console.setForeground(DarkTheme.FG_PRIMARY);
        console.setCaretColor(Color.WHITE);
        console.setFont(DarkTheme.FONT_CODE);
        console.setMargin(new Insets(8, 10, 8, 10));

        // Toolbar oben
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBackground(DarkTheme.BG_TOOLBAR);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER_DARK));

        JLabel lblTarget = new JLabel("Zielplattform:");
        lblTarget.setForeground(DarkTheme.FG_PRIMARY);
        lblTarget.setFont(DarkTheme.FONT_BOLD);
        toolbar.add(lblTarget);

        targetCombo = new JComboBox<>(CompilerEngine.Target.values());
        targetCombo.setSelectedItem(CompilerEngine.Target.WINDOWS);
        targetCombo.setBackground(DarkTheme.BG_BUTTON);
        targetCombo.setForeground(DarkTheme.FG_PRIMARY);
        targetCombo.setFont(DarkTheme.FONT_REGULAR);
        targetCombo.addActionListener(e -> {
            CompilerEngine.Target t = (CompilerEngine.Target) targetCombo.getSelectedItem();
            BuildManager.getInstance().setCurrentTarget(t);
        });
        toolbar.add(targetCombo);

        chkOnline = new JCheckBox("Online-Pakete (vcpkg)");
        chkOnline.setBackground(DarkTheme.BG_TOOLBAR);
        chkOnline.setForeground(DarkTheme.FG_PRIMARY);
        chkOnline.setFont(DarkTheme.FONT_REGULAR);
        chkOnline.setFocusPainted(false);
        chkOnline.addActionListener(e -> BuildManager.getInstance().setOnlineDependencies(chkOnline.isSelected()));
        toolbar.add(chkOnline);

        btnBuild = DarkTheme.createStyledButton("🔨 Kompilieren", DarkTheme.ACCENT_GREEN, Color.WHITE);
        btnBuild.addActionListener(e -> {
            File p = ProjectManager.getInstance().getActiveProjectDir();
            if (p == null) {
                JOptionPane.showMessageDialog(this, "Bitte zuerst ein Projekt öffnen oder erstellen.", "Kein Projekt", JOptionPane.WARNING_MESSAGE);
                return;
            }
            CompilerEngine.Target t = (CompilerEngine.Target) targetCombo.getSelectedItem();
            BuildManager.getInstance().startBuild(p, t, chkOnline.isSelected());
        });
        toolbar.add(btnBuild);

        btnStop = DarkTheme.createStyledButton("⏹️ Stopp", DarkTheme.ACCENT_RED, Color.WHITE);
        btnStop.setEnabled(false);
        btnStop.addActionListener(e -> BuildManager.getInstance().cancelBuild());
        toolbar.add(btnStop);

        btnClear = DarkTheme.createStyledButton("🗑️ Leeren", DarkTheme.BG_BUTTON, DarkTheme.FG_PRIMARY);
        btnClear.addActionListener(e -> {
            console.setText("");
            BuildManager.getInstance().clearLog();
        });
        toolbar.add(btnClear);

        add(toolbar, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(console);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(DarkTheme.BG_INPUT);
        add(scroll, BorderLayout.CENTER);

        statusLabel = new JLabel("Bereit");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        statusLabel.setFont(DarkTheme.FONT_BOLD);
        statusLabel.setForeground(DarkTheme.ACCENT_GREEN);
        statusLabel.setBackground(DarkTheme.BG_DARKEST);
        statusLabel.setOpaque(true);
        add(statusLabel, BorderLayout.SOUTH);

        BuildManager.getInstance().addListener(this);
    }

    @Override
    public void onLogLine(String line) {
        SwingUtilities.invokeLater(() -> {
            console.append(line + "\n");
            console.setCaretPosition(console.getDocument().getLength());
        });
    }

    @Override
    public void onStatusChanged(String status, boolean building) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
            btnBuild.setEnabled(!building);
            btnStop.setEnabled(building);
            targetCombo.setEnabled(!building);
        });
    }

    @Override
    public void onBuildFinished(boolean ok, String result) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(ok ? "✅ " + result : "❌ " + result);
            statusLabel.setForeground(ok ? DarkTheme.ACCENT_GREEN : DarkTheme.ACCENT_RED);
            btnBuild.setEnabled(true);
            btnStop.setEnabled(false);
            targetCombo.setEnabled(true);
        });
    }
}
