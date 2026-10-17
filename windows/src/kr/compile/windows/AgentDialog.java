package kr.compile.windows;

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
        super(parent, "KR Autonomous Agent (Windows Desktop Edition)", false);
        this.core = core;

        setSize(850, 750);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(new Color(30, 30, 30));
        setLayout(new BorderLayout(8, 8));

        // 1. Oben: Konfiguration & Auftrag
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBackground(new Color(37, 37, 38));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Status Row
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        topPanel.add(new JLabel("Status:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 2;
        lblStatus = new JLabel(core.isRunning() ? "Aktiv: " + core.getCurrentStatus() : "Bereit");
        lblStatus.setForeground(new Color(76, 175, 80));
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        topPanel.add(lblStatus, gbc);

        gbc.gridx = 3; gbc.gridy = 0; gbc.gridwidth = 1;
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(core.isRunning());
        progressBar.setVisible(core.isRunning());
        topPanel.add(progressBar, gbc);

        // Endpoint
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        topPanel.add(new JLabel("HTTPS-Endpunkt:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 3;
        txtEndpoint = new JTextField("https://api.openai.com/v1/chat/completions");
        topPanel.add(txtEndpoint, gbc);

        // Model
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        topPanel.add(new JLabel("Modell:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 3;
        txtModel = new JTextField("gpt-4o");
        topPanel.add(txtModel, gbc);

        // Token
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        topPanel.add(new JLabel("API-Key (nur RAM):"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 3;
        txtToken = new JPasswordField(sessionToken);
        topPanel.add(txtToken, gbc);

        // Task
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1;
        topPanel.add(new JLabel("Auftrag:"), gbc);
        gbc.gridx = 1; gbc.gridy = 4; gbc.gridwidth = 3;
        txtTask = new JTextArea(3, 40);
        txtTask.setLineWrap(true);
        txtTask.setWrapStyleWord(true);
        txtTask.setText(core.getLastTask().isEmpty() ? "Analysiere das geladene Projekt, behebe eventuelle Fehler und führe den Build aus." : core.getLastTask());
        topPanel.add(new JScrollPane(txtTask), gbc);

        add(topPanel, BorderLayout.NORTH);

        // 2. Mitte: Protokoll-Werkzeugleiste & Textfenster
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(new Color(30, 30, 30));

        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setBackground(new Color(20, 20, 20));
        txtLog.setForeground(new Color(220, 220, 220));
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLog.setMargin(new Insets(8, 8, 8, 8));

        String history = core.getLogHistory();
        txtLog.setText(history.isEmpty() ? "Bereit. Bitte ein Projekt öffnen und Auftrag starten.\n" : history);

        JPanel logToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        logToolbar.setBackground(new Color(45, 45, 48));

        JLabel logTitle = new JLabel("Agenten-Protokoll:");
        logTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        logToolbar.add(logTitle);

        JButton btnCopy = new JButton("📋 Kopieren");
        btnCopy.addActionListener(e -> {
            String text = core.getLogHistory();
            if (text.isEmpty()) text = txtLog.getText();
            StringSelection sel = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            JOptionPane.showMessageDialog(this, "Protokoll in die Zwischenablage kopiert (" + text.length() + " Zeichen).");
        });
        logToolbar.add(btnCopy);

        JButton btnExport = new JButton("💾 Export");
        btnExport.addActionListener(e -> {
            try {
                String path = core.exportLogToProject();
                JOptionPane.showMessageDialog(this, "Protokoll erfolgreich exportiert:\n" + path, "Export Erfolgreich", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Export fehlgeschlagen: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });
        logToolbar.add(btnExport);

        JButton btnClear = new JButton("🗑️ Leeren");
        btnClear.addActionListener(e -> {
            core.clearLogHistory();
            txtLog.setText("Protokoll geleert.\n");
        });
        logToolbar.add(btnClear);

        centerPanel.add(logToolbar, BorderLayout.NORTH);

        JScrollPane logScroll = new JScrollPane(txtLog);
        logScroll.setBorder(null);
        centerPanel.add(logScroll, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // 3. Unten: Start / Stopp / Schließen Buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottomPanel.setBackground(new Color(37, 37, 38));

        btnStart = new JButton("START");
        btnStart.setBackground(new Color(46, 125, 50));
        btnStart.setForeground(Color.WHITE);
        btnStart.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnStart.setEnabled(!core.isRunning());

        btnStop = new JButton("STOPP");
        btnStop.setBackground(new Color(198, 40, 40));
        btnStop.setForeground(Color.WHITE);
        btnStop.setEnabled(core.isRunning());

        JButton btnClose = new JButton("Schließen");
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
