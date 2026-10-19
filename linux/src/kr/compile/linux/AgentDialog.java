package kr.compile.linux;

import kr.compile.KrAgentCore;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;

public final class AgentDialog extends JDialog {

    private static AgentDialog instance;
    private final KrAgentCore core;

    private final JTextField txtEndpoint;
    private final JTextField txtModel;
    private final JPasswordField txtToken;
    private final JTextArea txtTask;
    private final JTextArea txtLog;
    private final JLabel lblStatus;
    private final JProgressBar progressBar;
    private final JButton btnStart;
    private final JButton btnStop;
    private static String sessionToken = "";

    public static synchronized AgentDialog getInstance(JFrame parent, KrAgentCore core) {
        if (instance == null) {
            instance = new AgentDialog(parent, core);
        }
        return instance;
    }

    private AgentDialog(JFrame parent, KrAgentCore core) {
        super(parent, "KR Autonomous Agent (Linux Desktop Edition)", false);
        this.core = core;

        setSize(850, 750);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(DarkTheme.BG_PANEL);
        setLayout(new BorderLayout(0, 0));

        // 1. Oben: Konfiguration & Auftrag
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBackground(DarkTheme.BG_PANEL);
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER_DARK),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Status Row
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        JLabel lblStatusTag = new JLabel("Status:");
        lblStatusTag.setFont(DarkTheme.FONT_BOLD);
        lblStatusTag.setForeground(DarkTheme.FG_PRIMARY);
        topPanel.add(lblStatusTag, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 2;
        lblStatus = new JLabel(core.isRunning() ? "Aktiv: " + core.getCurrentStatus() : "Bereit");
        lblStatus.setForeground(DarkTheme.ACCENT_GREEN);
        lblStatus.setFont(DarkTheme.FONT_BOLD);
        topPanel.add(lblStatus, gbc);

        gbc.gridx = 3; gbc.gridy = 0; gbc.gridwidth = 1;
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(core.isRunning());
        progressBar.setVisible(core.isRunning());
        topPanel.add(progressBar, gbc);

        // Endpoint
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        JLabel lblEp = new JLabel("HTTPS-Endpunkt:");
        lblEp.setForeground(DarkTheme.FG_PRIMARY);
        topPanel.add(lblEp, gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 3;
        txtEndpoint = new JTextField("https://api.openai.com/v1/chat/completions");
        txtEndpoint.setBackground(DarkTheme.BG_INPUT);
        txtEndpoint.setForeground(DarkTheme.FG_PRIMARY);
        txtEndpoint.setCaretColor(Color.WHITE);
        topPanel.add(txtEndpoint, gbc);

        // Model
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        JLabel lblMod = new JLabel("Modell:");
        lblMod.setForeground(DarkTheme.FG_PRIMARY);
        topPanel.add(lblMod, gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 3;
        txtModel = new JTextField("gpt-4o");
        txtModel.setBackground(DarkTheme.BG_INPUT);
        txtModel.setForeground(DarkTheme.FG_PRIMARY);
        txtModel.setCaretColor(Color.WHITE);
        topPanel.add(txtModel, gbc);

        // Token
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        JLabel lblTok = new JLabel("API-Key (nur RAM):");
        lblTok.setForeground(DarkTheme.FG_PRIMARY);
        topPanel.add(lblTok, gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 3;
        txtToken = new JPasswordField(sessionToken);
        txtToken.setBackground(DarkTheme.BG_INPUT);
        txtToken.setForeground(DarkTheme.FG_PRIMARY);
        txtToken.setCaretColor(Color.WHITE);
        topPanel.add(txtToken, gbc);

        // Task
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1;
        JLabel lblTsk = new JLabel("Auftrag:");
        lblTsk.setForeground(DarkTheme.FG_PRIMARY);
        topPanel.add(lblTsk, gbc);
        gbc.gridx = 1; gbc.gridy = 4; gbc.gridwidth = 3;
        txtTask = new JTextArea(3, 40);
        txtTask.setLineWrap(true);
        txtTask.setWrapStyleWord(true);
        txtTask.setBackground(DarkTheme.BG_INPUT);
        txtTask.setForeground(DarkTheme.FG_PRIMARY);
        txtTask.setCaretColor(Color.WHITE);
        txtTask.setFont(DarkTheme.FONT_REGULAR);
        txtTask.setText(core.getLastTask().isEmpty() ? "Analysiere das geladene Projekt, behebe eventuelle Fehler und führe den Build aus." : core.getLastTask());
        JScrollPane taskScroll = new JScrollPane(txtTask);
        taskScroll.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER_DARK));
        topPanel.add(taskScroll, gbc);

        add(topPanel, BorderLayout.NORTH);

        // 2. Mitte: Protokoll-Werkzeugleiste & Textfenster
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(DarkTheme.BG_PANEL);

        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setBackground(DarkTheme.BG_INPUT);
        txtLog.setForeground(DarkTheme.FG_PRIMARY);
        txtLog.setCaretColor(Color.WHITE);
        txtLog.setFont(DarkTheme.FONT_CODE);
        txtLog.setMargin(new Insets(8, 10, 8, 10));

        String history = core.getLogHistory();
        txtLog.setText(history.isEmpty() ? "Bereit. Bitte ein Projekt öffnen und Auftrag starten.\n" : history);

        JPanel logToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        logToolbar.setBackground(DarkTheme.BG_TOOLBAR);
        logToolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER_DARK));

        JLabel logTitle = new JLabel("Agenten-Protokoll:");
        logTitle.setFont(DarkTheme.FONT_BOLD);
        logTitle.setForeground(DarkTheme.FG_PRIMARY);
        logToolbar.add(logTitle);

        JButton btnCopy = DarkTheme.createStyledButton("Kopieren", DarkTheme.BG_BUTTON, DarkTheme.FG_PRIMARY);
        btnCopy.addActionListener(e -> {
            String text = core.getLogHistory();
            if (text.isEmpty()) text = txtLog.getText();
            StringSelection sel = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            JOptionPane.showMessageDialog(this, "Protokoll in die Zwischenablage kopiert (" + text.length() + " Zeichen).");
        });
        logToolbar.add(btnCopy);

        JButton btnExport = DarkTheme.createStyledButton("Export", DarkTheme.BG_BUTTON, DarkTheme.FG_PRIMARY);
        btnExport.addActionListener(e -> {
            try {
                String path = core.exportLogToProject();
                JOptionPane.showMessageDialog(this, "Protokoll erfolgreich exportiert:\n" + path, "Export Erfolgreich", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Export fehlgeschlagen: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });
        logToolbar.add(btnExport);

        JButton btnClear = DarkTheme.createStyledButton("Leeren", DarkTheme.BG_BUTTON, DarkTheme.FG_PRIMARY);
        btnClear.addActionListener(e -> {
            core.clearLogHistory();
            txtLog.setText("Protokoll geleert.\n");
        });
        logToolbar.add(btnClear);

        centerPanel.add(logToolbar, BorderLayout.NORTH);

        JScrollPane logScroll = new JScrollPane(txtLog);
        logScroll.setBorder(null);
        logScroll.getViewport().setBackground(DarkTheme.BG_INPUT);
        centerPanel.add(logScroll, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // 3. Unten: Start / Stopp / Schließen Buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bottomPanel.setBackground(DarkTheme.BG_TOOLBAR);
        bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DarkTheme.BORDER_DARK));

        btnStart = DarkTheme.createStyledButton("START", DarkTheme.ACCENT_GREEN, Color.WHITE);
        btnStart.setEnabled(!core.isRunning());

        btnStop = DarkTheme.createStyledButton("STOPP", DarkTheme.ACCENT_RED, Color.WHITE);
        btnStop.setEnabled(core.isRunning());

        JButton btnClose = DarkTheme.createStyledButton("Schließen", DarkTheme.BG_BUTTON, DarkTheme.FG_PRIMARY);
        btnClose.addActionListener(e -> setVisible(false));

        bottomPanel.add(btnStart);
        bottomPanel.add(btnStop);
        bottomPanel.add(btnClose);
        add(bottomPanel, BorderLayout.SOUTH);

        // Listener registrieren
        core.addListener(new KrAgentCore.Events() {
            @Override
            public void onEvent(String text) {
                SwingUtilities.invokeLater(() -> {
                    txtLog.append(text + "\n");
                    txtLog.setCaretPosition(txtLog.getDocument().getLength());
                    lblStatus.setText("Aktiv: " + core.getCurrentStatus());
                    progressBar.setVisible(core.isRunning());
                    btnStart.setEnabled(!core.isRunning());
                    btnStop.setEnabled(core.isRunning());
                });
            }

            @Override
            public void onFinished(String status) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Status: " + status);
                    progressBar.setVisible(false);
                    btnStart.setEnabled(true);
                    btnStop.setEnabled(false);
                });
            }
        });

        btnStop.addActionListener(e -> {
            core.cancel();
            btnStop.setEnabled(false);
        });

        btnStart.addActionListener(e -> {
            if (core.isRunning()) return;

            String ep = txtEndpoint.getText().trim();
            String mod = txtModel.getText().trim();
            String tsk = txtTask.getText().trim();
            sessionToken = new String(txtToken.getPassword()).trim();

            if (ep.isEmpty() || mod.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Endpunkt und Modellname dürfen nicht leer sein.", "Eingabefehler", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (tsk.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Bitte einen Auftrag eingeben.", "Eingabefehler", JOptionPane.WARNING_MESSAGE);
                return;
            }

            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            progressBar.setVisible(true);
            lblStatus.setText("Agent gestartet …");

            KrAgentCore.Settings s = new KrAgentCore.Settings(ep, mod, sessionToken, 30);
            core.start(tsk, s, (title, detail, callback) -> {
                int res = JOptionPane.showConfirmDialog(this, detail, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                callback.accept(res == JOptionPane.YES_OPTION);
            });
        });
    }
}
