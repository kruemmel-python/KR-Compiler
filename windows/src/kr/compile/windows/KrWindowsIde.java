package kr.compile.windows;

import kr.compile.BuildManager;
import kr.compile.CompilerEngine;
import kr.compile.DocumentManager;
import kr.compile.KrAgentCore;
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
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new KrWindowsIde().setVisible(true);
        });
    }

    public KrWindowsIde() {
        super("KR Compiler IDE v4.0.3 – Windows Desktop Edition");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        agentCore = new KrAgentCore();
        agentDialog = AgentDialog.getInstance(this, agentCore);

        // Menüleiste einrichten
        setupMenuBar();

        // Oberer Navigationsbereich (1 zu 1 wie Mobile IDE: CODE | DATEIEN | BUILD | ARTEFAKTE | AGENT)
        JPanel navBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        navBar.setBackground(new Color(30, 30, 30));

        btnNavCode = createNavButton("CODE", () -> showView("CODE"));
        btnNavFiles = createNavButton("DATEIEN", () -> showView("FILES"));
        btnNavBuild = createNavButton("BUILD", () -> showView("BUILD"));
        btnNavArtifacts = createNavButton("ARTEFAKTE", () -> showView("ARTIFACTS"));
        btnNavAgent = createNavButton("🤖 AGENT", () -> agentDialog.setVisible(true));

        navBar.add(btnNavCode);
        navBar.add(btnNavFiles);
        navBar.add(btnNavBuild);
        navBar.add(btnNavArtifacts);
        navBar.add(btnNavAgent);

        getContentPane().add(navBar, BorderLayout.NORTH);

        // Hauptbereich mit SplitPane: Links Projekt-Explorer, Rechts Arbeitsansichten
        explorerPanel = new ProjectExplorerPanel();
        editorPanel = new CodeEditorPanel();
        buildPanel = new BuildConsolePanel();
        artifactsPanel = new ArtifactsPanel();

        cardLayout = new CardLayout();
        mainCardPanel = new JPanel(cardLayout);
        mainCardPanel.add(editorPanel, "CODE");
        mainCardPanel.add(buildPanel, "BUILD");
        mainCardPanel.add(artifactsPanel, "ARTIFACTS");

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorerPanel, mainCardPanel);
        splitPane.setDividerLocation(260);
        splitPane.setDividerSize(5);
        splitPane.setBorder(null);

        getContentPane().add(splitPane, BorderLayout.CENTER);

        // Statusleiste unten
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        statusBar.setBackground(new Color(25, 25, 25));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(45, 45, 48)));

        statusProject = new JLabel("Projekt: Kein Projekt");
        statusProject.setForeground(new Color(200, 200, 200));

        statusTarget = new JLabel("Ziel: Windows x64");
        statusTarget.setForeground(new Color(200, 200, 200));

        statusAgent = new JLabel("Agent: Bereit");
        statusAgent.setForeground(new Color(76, 175, 80));

        JLabel infoVersion = new JLabel("v4.0.3 Windows Edition | Entwickler: Ralf Krümmel");
        infoVersion.setForeground(new Color(130, 130, 130));

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
                setTitle("KR Compiler IDE v4.0.3 – " + p.getName());
                openDefaultProjectFile(p);
            }
            @Override public void onProjectClosed() {
                statusProject.setText("Projekt: Kein Projekt");
                setTitle("KR Compiler IDE v4.0.3 – Windows Desktop Edition");
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
                btnNavAgent.setText("🤖 AGENT (aktiv)");
                btnNavAgent.setBackground(new Color(46, 125, 50));
                statusAgent.setText("🤖 Agent: " + statusText);
                statusAgent.setForeground(new Color(76, 175, 80));
            } else {
                btnNavAgent.setText("🤖 AGENT");
                btnNavAgent.setBackground(new Color(45, 45, 48));
                statusAgent.setText("Agent: " + statusText);
                statusAgent.setForeground(Color.LIGHT_GRAY);
            }
        });

        showView("CODE");
    }

    private JButton createNavButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setBackground(new Color(45, 45, 48));
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
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
        Color sel = new Color(9, 71, 113);
        Color unsel = new Color(45, 45, 48);

        btnNavCode.setBackground("CODE".equals(active) ? sel : unsel);
        btnNavBuild.setBackground("BUILD".equals(active) ? sel : unsel);
        btnNavArtifacts.setBackground("ARTIFACTS".equals(active) ? sel : unsel);
    }

    private void setupMenuBar() {
        JMenuBar mb = new JMenuBar();
        mb.setBackground(new Color(30, 30, 30));

        // Menü: Projekt
        JMenu mProject = new JMenu("Projekt");
        mProject.setForeground(Color.LIGHT_GRAY);

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

        mb.add(mProject);

        // Menü: Build
        JMenu mBuild = new JMenu("Build");
        mBuild.setForeground(Color.LIGHT_GRAY);

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

        mb.add(mBuild);

        // Menü: Agent
        JMenu mAgent = new JMenu("Agent");
        mAgent.setForeground(Color.LIGHT_GRAY);

        JMenuItem mOpenAgent = new JMenuItem("Autonomen KI-Agenten öffnen");
        mOpenAgent.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK));
        mOpenAgent.addActionListener(e -> agentDialog.setVisible(true));
        mAgent.add(mOpenAgent);

        mb.add(mAgent);

        // Menü: Hilfe
        JMenu mHelp = new JMenu("Hilfe");
        mHelp.setForeground(Color.LIGHT_GRAY);

        JMenuItem mAbout = new JMenuItem("Über KR IDE");
        mAbout.addActionListener(e -> showAboutDialog());
        mHelp.add(mAbout);

        mb.add(mHelp);

        setJMenuBar(mb);
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
        String info = "KR Compiler IDE – Windows Desktop Edition\n"
                + "Version: 4.0.3\n\n"
                + "Entwickler: Ralf Krümmel\n\n"
                + "Funktionsumfang:\n"
                + "• KR Unified Language (.kr) mit vollem Multi-File Support (*.krproject)\n"
                + "• C++20, Rust und Go Übersetzung direkt auf Windows\n"
                + "• Multi-Target Builds: Windows x64 (.exe), Linux x64, Android ARM64\n"
                + "• Autonomer KI-Programmierassistent („AGENT“) mit Tool-Loop, Audit-Log, Export und Freigabesystem\n"
                + "• Integrierter vcpkg Paket-Manager";
        JOptionPane.showMessageDialog(this, info, "Über KR IDE", JOptionPane.INFORMATION_MESSAGE);
    }
}
