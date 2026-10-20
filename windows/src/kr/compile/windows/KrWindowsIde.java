package kr.compile.windows;

import kr.compile.BuildManager;
import kr.compile.CompilerEngine;
import kr.compile.DocumentManager;
import kr.compile.KrAgentCore;
import kr.compile.KrAdaptiveComputeAnalyzer;
import kr.compile.KrComputePlan;
import kr.compile.KrProjectConfig;
import kr.compile.ProjectManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;

public final class KrWindowsIde extends JFrame {

    private final ProjectExplorerPanel explorerPanel;
    private final CodeEditorPanel editorPanel;
    private final BuildConsolePanel buildPanel;
    private final ArtifactsPanel artifactsPanel;
    private final KrAgentCore agentCore;
    private final AgentDialog agentDialog;

    private final JPanel mainCardPanel;
    private final CardLayout cardLayout;

    private final JButton btnNavCode;
    private final JButton btnNavFiles;
    private final JButton btnNavBuild;
    private final JButton btnNavArtifacts;
    private final JButton btnNavAgent;

    private final JLabel statusProject;
    private final JLabel statusTarget;
    private final JLabel statusAgent;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DarkTheme.installDefaults();
            new KrWindowsIde().setVisible(true);
        });
    }

    public KrWindowsIde() {
        super("KR Compiler IDE v4.3.0 – Windows Desktop Edition");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(DarkTheme.BG_DARKEST);

        agentCore = new KrAgentCore();
        agentDialog = AgentDialog.getInstance(this, agentCore);

        // Menüleiste einrichten
        setupMenuBar();

        // Oberer Navigationsbereich (1 zu 1 wie Mobile IDE: CODE | DATEIEN | BUILD | ARTEFAKTE | AGENT)
        JPanel navBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        navBar.setBackground(DarkTheme.BG_TOOLBAR);
        navBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER_DARK));

        btnNavCode = createNavButton("CODE", () -> showView("CODE"));
        btnNavFiles = createNavButton("DATEIEN", () -> showView("FILES"));
        btnNavBuild = createNavButton("BUILD", () -> showView("BUILD"));
        btnNavArtifacts = createNavButton("ARTEFAKTE", () -> showView("ARTIFACTS"));
        btnNavAgent = createNavButton("AGENT", () -> agentDialog.setVisible(true));

        navBar.add(btnNavCode);
        navBar.add(btnNavFiles);
        navBar.add(btnNavBuild);
        navBar.add(btnNavArtifacts);
        navBar.add(btnNavAgent);

        getContentPane().add(navBar, BorderLayout.NORTH);

        // Hauptbereich mit SplitPane: Links Projekt-Explorer, Rechts Arbeitsansichten
        explorerPanel = new ProjectExplorerPanel();
        editorPanel = new CodeEditorPanel(this::chooseProjectFolder, this::showNewProjectDialog, () -> agentDialog.setVisible(true));
        buildPanel = new BuildConsolePanel();
        artifactsPanel = new ArtifactsPanel();

        cardLayout = new CardLayout();
        mainCardPanel = new JPanel(cardLayout);
        mainCardPanel.setBackground(DarkTheme.BG_EDITOR);
        mainCardPanel.add(editorPanel, "CODE");
        mainCardPanel.add(buildPanel, "BUILD");
        mainCardPanel.add(artifactsPanel, "ARTIFACTS");

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorerPanel, mainCardPanel);
        splitPane.setDividerLocation(260);
        splitPane.setDividerSize(4);
        splitPane.setBorder(null);

        getContentPane().add(splitPane, BorderLayout.CENTER);

        // Statusleiste unten
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        statusBar.setBackground(DarkTheme.BG_DARKEST);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DarkTheme.BORDER_DARK));

        statusProject = new JLabel("Projekt: Kein Projekt");
        statusProject.setForeground(DarkTheme.FG_PRIMARY);

        boolean hasTc = kr.compile.ToolchainManager.getInstance().isEmbeddedToolchainPresent();
        statusTarget = new JLabel("Ziel: Windows x64 (" + (hasTc ? "Integrierte Toolchain" : "System") + ")");
        statusTarget.setForeground(DarkTheme.FG_SECONDARY);

        statusAgent = new JLabel("Agent: Bereit");
        statusAgent.setForeground(DarkTheme.ACCENT_GREEN);

        JLabel infoVersion = new JLabel("v4.3.0 Windows Edition | Entwickler: Ralf Krümmel");
        infoVersion.setForeground(DarkTheme.FG_MUTED);

        statusBar.add(statusProject);
        statusBar.add(statusTarget);
        statusBar.add(statusAgent);
        statusBar.add(Box.createHorizontalGlue());
        statusBar.add(infoVersion);

        getContentPane().add(statusBar, BorderLayout.SOUTH);

        // Listener registrieren
        ProjectManager.getInstance().addListener(new ProjectManager.Listener() {
            @Override public void onProjectOpened(File p) {
                statusProject.setText("Projekt: " + p.getName());
                setTitle("KR Compiler IDE v4.3.0 – " + p.getName());
                openDefaultProjectFile(p);
            }
            @Override public void onProjectClosed() {
                statusProject.setText("Projekt: Kein Projekt");
                setTitle("KR Compiler IDE v4.3.0 – Windows Desktop Edition");
            }
            @Override public void onTreeRefreshed(File p) {}
        });

        BuildManager.getInstance().addListener(new BuildManager.Listener() {
            @Override public void onLogLine(String l) {}
            @Override public void onStatusChanged(String s, boolean b) {
                statusTarget.setText("Ziel: " + BuildManager.getInstance().getCurrentTarget().name() + " (" + s + ")");
            }
            @Override public void onBuildFinished(boolean ok, String r) {
                statusTarget.setText("Ziel: " + BuildManager.getInstance().getCurrentTarget().name() + (ok ? " (OK)" : " (Fehler)"));
            }
        });

        agentCore.setStatusListener((statusText, isRunning) -> {
            if (isRunning) {
                btnNavAgent.setText("AGENT (aktiv)");
                btnNavAgent.setBackground(DarkTheme.ACCENT_GREEN);
                btnNavAgent.setForeground(Color.WHITE);
                statusAgent.setText("Agent: " + statusText);
                statusAgent.setForeground(DarkTheme.ACCENT_GREEN);
            } else {
                btnNavAgent.setText("AGENT");
                btnNavAgent.setBackground(DarkTheme.BG_BUTTON);
                btnNavAgent.setForeground(DarkTheme.FG_PRIMARY);
                statusAgent.setText("Agent: " + statusText);
                statusAgent.setForeground(DarkTheme.FG_SECONDARY);
            }
        });

        showView("CODE");
    }

    private JButton createNavButton(String text, Runnable action) {
        JButton b = DarkTheme.createStyledButton(text, DarkTheme.BG_BUTTON, DarkTheme.FG_PRIMARY);
        b.setFont(DarkTheme.FONT_BOLD);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DarkTheme.BORDER_DARK, 1),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));
        b.addActionListener(e -> action.run());
        return b;
    }

    private void showView(String view) {
        if ("FILES".equals(view)) {
            explorerPanel.setVisible(true);
        } else {
            cardLayout.show(mainCardPanel, view);
            if ("ARTIFACTS".equals(view)) {
                artifactsPanel.refreshArtifacts();
            }
        }
        updateNavSelection(view);
    }

    private void updateNavSelection(String active) {
        btnNavCode.setBackground("CODE".equals(active) ? DarkTheme.ACCENT_BLUE : DarkTheme.BG_BUTTON);
        btnNavCode.setForeground("CODE".equals(active) ? Color.WHITE : DarkTheme.FG_PRIMARY);
        btnNavBuild.setBackground("BUILD".equals(active) ? DarkTheme.ACCENT_BLUE : DarkTheme.BG_BUTTON);
        btnNavBuild.setForeground("BUILD".equals(active) ? Color.WHITE : DarkTheme.FG_PRIMARY);
        btnNavArtifacts.setBackground("ARTIFACTS".equals(active) ? DarkTheme.ACCENT_BLUE : DarkTheme.BG_BUTTON);
        btnNavArtifacts.setForeground("ARTIFACTS".equals(active) ? Color.WHITE : DarkTheme.FG_PRIMARY);
    }

    private void setupMenuBar() {
        JMenuBar mb = new JMenuBar();
        mb.setBackground(DarkTheme.BG_TOOLBAR);
        mb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER_DARK));

        // Menü: Projekt
        JMenu mProject = new JMenu("Projekt");

        JMenuItem mNew = new JMenuItem("Neues Projekt erstellen …");
        mNew.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        mNew.addActionListener(e -> showNewProjectDialog());
        mProject.add(mNew);

        JMenuItem mOpen = new JMenuItem("Projektordner öffnen …");
        mOpen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        mOpen.addActionListener(e -> chooseProjectFolder());
        mProject.add(mOpen);

        mProject.addSeparator();

        JMenuItem mSave = new JMenuItem("Aktive Datei speichern");
        mSave.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        mSave.addActionListener(e -> {
            try {
                DocumentManager.getInstance().saveActiveDocument();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Fehler beim Speichern: " + ex.getMessage());
            }
        });
        mProject.add(mSave);

        JMenuItem mSaveAll = new JMenuItem("Alle Dateien speichern");
        mSaveAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        mSaveAll.addActionListener(e -> {
            try {
                DocumentManager.getInstance().saveAll();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Fehler: " + ex.getMessage());
            }
        });
        mProject.add(mSaveAll);

        mProject.addSeparator();

        JMenuItem mClose = new JMenuItem("Projekt schließen");
        mClose.addActionListener(e -> ProjectManager.getInstance().closeProject());
        mProject.add(mClose);

        JMenuItem mExit = new JMenuItem("Beenden");
        mExit.addActionListener(e -> System.exit(0));
        mProject.add(mExit);

        styleMenu(mProject);
        mb.add(mProject);

        // Menü: Build
        JMenu mBuild = new JMenu("Build");

        JMenuItem mRunBuild = new JMenuItem("Projekt kompilieren");
        mRunBuild.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        mRunBuild.addActionListener(e -> {
            File p = ProjectManager.getInstance().getActiveProjectDir();
            if (p != null) {
                showView("BUILD");
                BuildManager.getInstance().startBuild(p, BuildManager.getInstance().getCurrentTarget(), BuildManager.getInstance().isOnlineDependencies());
            } else {
                JOptionPane.showMessageDialog(this, "Kein Projekt geöffnet.");
            }
        });
        mBuild.add(mRunBuild);

        JMenuItem mStopBuild = new JMenuItem("Build abbrechen");
        mStopBuild.addActionListener(e -> BuildManager.getInstance().cancelBuild());
        mBuild.add(mStopBuild);

        JMenuItem mCompute = new JMenuItem("Adaptive Compute analysieren …");
        mCompute.addActionListener(e -> showAdaptiveComputeDialog());
        mBuild.addSeparator();
        mBuild.add(mCompute);

        styleMenu(mBuild);
        mb.add(mBuild);

        // Menü: Agent
        JMenu mAgent = new JMenu("Agent");

        JMenuItem mOpenAgent = new JMenuItem("Autonomen KI-Agenten öffnen");
        mOpenAgent.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK));
        mOpenAgent.addActionListener(e -> agentDialog.setVisible(true));
        mAgent.add(mOpenAgent);

        styleMenu(mAgent);
        mb.add(mAgent);

        // Menü: Hilfe
        JMenu mHelp = new JMenu("Hilfe");

        JMenuItem mAbout = new JMenuItem("Über KR IDE");
        mAbout.addActionListener(e -> showAboutDialog());
        mHelp.add(mAbout);

        styleMenu(mHelp);
        mb.add(mHelp);

        setJMenuBar(mb);
    }

    private void showAdaptiveComputeDialog() {
        File project = ProjectManager.getInstance().getActiveProjectDir();
        if (project == null || !project.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Kein lokales KR-Projekt geöffnet.",
                    "Adaptive Compute", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            KrProjectConfig config = KrProjectConfig.parse(
                    KrProjectConfig.findProjectConfigFile(project), project.getName(), false);
            KrComputePlan plan = KrAdaptiveComputeAnalyzer.analyzeProject(project, config.computeMode);
            JTextArea report = new JTextArea(KrAdaptiveComputeAnalyzer.formatReport(plan), 28, 88);
            report.setEditable(false);
            report.setLineWrap(true);
            report.setWrapStyleWord(true);
            report.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            JOptionPane.showMessageDialog(this, new JScrollPane(report),
                    "Adaptive Compute", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Adaptive Compute – Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void styleMenu(JMenu menu) {
        menu.setForeground(DarkTheme.FG_PRIMARY);
        menu.setBackground(DarkTheme.BG_TOOLBAR);
        menu.setFont(DarkTheme.FONT_REGULAR);
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null) {
                item.setBackground(DarkTheme.BG_TOOLBAR);
                item.setForeground(DarkTheme.FG_PRIMARY);
                item.setFont(DarkTheme.FONT_REGULAR);
            }
        }
    }

    private void chooseProjectFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Projektordner auswählen");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int res = fc.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File selected = fc.getSelectedFile();
            ProjectManager.getInstance().openProject(selected);
        }
    }

    private void showNewProjectDialog() {
        JPanel p = new JPanel(new GridLayout(3, 2, 8, 8));
        JTextField txtName = new JTextField("MeinKrProjekt");
        JComboBox<ProjectManager.Template> cbTemplate = new JComboBox<>(new ProjectManager.Template[]{
                ProjectManager.Template.MODULAR_KR_FORGE,
                ProjectManager.Template.SINGLE_KR,
                ProjectManager.Template.CPP_PROJECT,
                ProjectManager.Template.RUST_PROJECT,
                ProjectManager.Template.GO_PROJECT
        });

        p.add(new JLabel("Projektname:"));
        p.add(txtName);
        p.add(new JLabel("Vorlage:"));
        p.add(cbTemplate);

        int res = JOptionPane.showConfirmDialog(this, p, "Neues Projekt anlegen", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String name = txtName.getText().trim();
            if (name.isEmpty()) return;

            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Zielordner für neues Projekt wählen");
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File parent = fc.getSelectedFile();
                try {
                    ProjectManager.Template t = (ProjectManager.Template) cbTemplate.getSelectedItem();
                    ProjectManager.getInstance().createProject(parent, name, t);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Fehler beim Erstellen: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void openDefaultProjectFile(File projectDir) {
        File forge = new File(projectDir, "forge.krproject");
        if (forge.isFile()) { openDoc(projectDir, forge); return; }
        File mainKr = new File(projectDir, "main.kr");
        if (mainKr.isFile()) { openDoc(projectDir, mainKr); return; }
        File mainCpp = new File(projectDir, "main.cpp");
        if (mainCpp.isFile()) { openDoc(projectDir, mainCpp); return; }
        File mainRs = new File(new File(projectDir, "src"), "main.rs");
        if (mainRs.isFile()) { openDoc(projectDir, mainRs); return; }
        File mainGo = new File(projectDir, "main.go");
        if (mainGo.isFile()) { openDoc(projectDir, mainGo); return; }
    }

    private void openDoc(File root, File file) {
        try {
            DocumentManager.getInstance().openDocument(root, file);
            showView("CODE");
        } catch (IOException ignored) {}
    }

    private void showAboutDialog() {
        boolean hasTc = kr.compile.ToolchainManager.getInstance().isEmbeddedToolchainPresent();
        String tcStatus = hasTc ? "Aktiv (" + kr.compile.ToolchainManager.getInstance().getToolchainDir().getName() + ")" : "Host-System";

        JDialog dialog = new JDialog(this, "Über KR IDE", true);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(DarkTheme.BG_PANEL);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(DarkTheme.BG_PANEL);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel titleLabel = new JLabel("KR Compiler IDE – Windows Desktop Edition");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel versionLabel = new JLabel("Version 4.3.0");
        versionLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        versionLabel.setForeground(DarkTheme.ACCENT_BLUE);
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel authorLabel = new JLabel("Entwickler: Ralf Krümmel");
        authorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        authorLabel.setForeground(new Color(78, 201, 176));
        authorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tcLabel = new JLabel("Compiler-Status: " + tcStatus);
        tcLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tcLabel.setForeground(DarkTheme.FG_SECONDARY);
        tcLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel featuresTitle = new JLabel("Funktionsumfang:");
        featuresTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        featuresTitle.setForeground(Color.WHITE);
        featuresTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] features = {
                "• Vollständig integrierte, portable C/C++20 Toolchain (ohne externe SDK-Installation)",
                "• KR Unified Language (.kr) mit vollem Multi-File Support (*.krproject)",
                "• C++20, Rust und Go Übersetzung direkt auf Windows",
                "• Multi-Target Builds: Windows x64 (.exe), Linux x64 (ELF), Android ARM64 (.apk)",
                "• Autonomer KI-Programmierassistent („AGENT“) mit Tool-Loop, Audit-Log und Freigabesystem",
                "• Integrierter vcpkg Paket-Manager"
        };

        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(versionLabel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(authorLabel);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(tcLabel);
        mainPanel.add(Box.createVerticalStrut(16));
        mainPanel.add(featuresTitle);
        mainPanel.add(Box.createVerticalStrut(8));

        for (String feat : features) {
            JLabel featLabel = new JLabel(feat);
            featLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            featLabel.setForeground(new Color(225, 225, 225));
            featLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            mainPanel.add(featLabel);
            mainPanel.add(Box.createVerticalStrut(3));
        }

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setBackground(DarkTheme.BG_TOOLBAR);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        JButton okBtn = DarkTheme.createStyledButton("Schließen", DarkTheme.ACCENT_BLUE, Color.WHITE);
        okBtn.setPreferredSize(new Dimension(100, 32));
        okBtn.addActionListener(e -> dialog.dispose());
        btnPanel.add(okBtn);

        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
}
