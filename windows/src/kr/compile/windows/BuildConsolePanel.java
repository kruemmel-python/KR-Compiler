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
        setBackground(new Color(30, 30, 30));

        console = new JTextArea();
        console.setEditable(false);
        console.setBackground(new Color(20, 20, 20));
        console.setForeground(new Color(220, 220, 220));
        console.setFont(new Font("Consolas", Font.PLAIN, 13));
        console.setMargin(new Insets(8, 8, 8, 8));

        // Toolbar oben
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.setBackground(new Color(45, 45, 48));

        toolbar.add(new JLabel("Zielplattform:"));
        targetCombo = new JComboBox<>(CompilerEngine.Target.values());
        targetCombo.setSelectedItem(CompilerEngine.Target.WINDOWS);
        targetCombo.addActionListener(e -> {
            CompilerEngine.Target t = (CompilerEngine.Target) targetCombo.getSelectedItem();
            BuildManager.getInstance().setCurrentTarget(t);
        });
        toolbar.add(targetCombo);

        chkOnline = new JCheckBox("Online-Pakete (vcpkg)");
        chkOnline.setBackground(new Color(45, 45, 48));
        chkOnline.setForeground(Color.LIGHT_GRAY);
        chkOnline.addActionListener(e -> BuildManager.getInstance().setOnlineDependencies(chkOnline.isSelected()));
        toolbar.add(chkOnline);

        btnBuild = new JButton("🔨 Kompilieren");
        btnBuild.setBackground(new Color(46, 125, 50));
        btnBuild.setForeground(Color.WHITE);
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

        btnStop = new JButton("⏹️ Stopp");
        btnStop.setEnabled(false);
        btnStop.addActionListener(e -> BuildManager.getInstance().cancelBuild());
        toolbar.add(btnStop);

        btnClear = new JButton("🗑️ Leeren");
        btnClear.addActionListener(e -> {
            console.setText("");
            BuildManager.getInstance().clearLog();
        });
        toolbar.add(btnClear);

        add(toolbar, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(console);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        statusLabel = new JLabel("Bereit");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setForeground(new Color(76, 175, 80));
        statusLabel.setBackground(new Color(25, 25, 25));
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
            statusLabel.setForeground(ok ? new Color(76, 175, 80) : new Color(244, 67, 54));
            btnBuild.setEnabled(true);
            btnStop.setEnabled(false);
            targetCombo.setEnabled(true);
        });
    }
}
