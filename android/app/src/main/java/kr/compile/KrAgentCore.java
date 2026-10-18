package kr.compile;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Autonomer Programmieragent für die KR Compiler Android IDE.
 * Unterstützt sowohl lokale Verzeichnisse als auch über SAF geladene Speicher-Projekte.
 * Bietet 1:1 Parität mit der Windows Desktop Edition inklusive KrSourceMap,
 * resilientem JSON-Parsing, zielgenauem 'replace', Zeilennummern-Tracking und 10-Minuten-Timeout.
 */
public final class KrAgentCore {

    public interface Events {
        void onEvent(String text);
        void onFinished(String status);
    }

    public interface StatusListener {
        void onStatus(String statusText, boolean isRunning);
    }

    public interface Approval {
        void request(String title, String detail, java.util.function.Consumer<Boolean> callback);
    }

    public static final class Settings {
        private final String endpoint, model, token;
        private final int maxSteps;

        public Settings(String endpoint, String model, String token, int maxSteps) {
            this.endpoint = endpoint;
            this.model = model;
            this.token = token;
            this.maxSteps = maxSteps;
        }

        public String endpoint() { return endpoint; }
        public String model() { return model; }
        public String token() { return token; }
        public int maxSteps() { return maxSteps; }
    }

    private static final int CONNECT_TIMEOUT_MS = 60_000;
    private static final int READ_TIMEOUT_MS = 600_000; // 10 Minuten für lokale LLMs (LM Studio / Ollama)

    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile boolean running;
    private final File journal;

    private final StringBuilder logHistory = new StringBuilder();
    private final List<Events> listeners = new CopyOnWriteArrayList<>();
    private volatile StatusListener statusListener;
    private volatile Approval approvalHandler;
    private volatile String currentStatus = "Bereit";
    private volatile String lastTask = "";

    public KrAgentCore(Context context) {
        this.context = context.getApplicationContext();
        this.journal = new File(this.context.getFilesDir(), "kr_agent_audit.jsonl");
    }

    public boolean isRunning() { return running; }
    public String getCurrentStatus() { return currentStatus; }
    public String getLastTask() { return lastTask; }

    public synchronized String getLogHistory() {
        return logHistory.toString();
    }

    public synchronized void clearLogHistory() {
        logHistory.setLength(0);
    }

    public synchronized String exportLogToProject() throws IOException {
        String logContent = getLogHistory();
        if (logContent.isEmpty()) {
            throw new IOException("Das Protokoll ist leer.");
        }

        ProjectManager projects = ProjectManager.getInstance();
        File localRoot = projects.getActiveLocalDir();
        Uri safUri = projects.getActiveSafTreeUri();
        if ((localRoot == null || !localRoot.isDirectory()) && safUri == null) {
            throw new IOException("Kein Projekt geöffnet. Bitte zuerst ein Projekt öffnen.");
        }

        String fileName = "kr_agent_log.txt";
        String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        String fullExport = "=== KR Autonomous Agent Protokoll ===\n"
                + "Projekt: " + projects.getActiveProjectName() + "\n"
                + "Exportiert am: " + dateStr + "\n"
                + "======================================\n\n"
                + logContent;

        if (localRoot != null && localRoot.isDirectory()) {
            File target = new File(localRoot, fileName);
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(fullExport.getBytes(StandardCharsets.UTF_8));
            }
            DocumentManager.getInstance().reloadIfOpen(fileName, fullExport);
            projects.refreshTree(context);
            return target.getAbsolutePath();
        } else {
            DocumentTree.writeSafFile(context, safUri, fileName, fullExport);
            File ws = new File(context.getFilesDir(), "saf_agent_workspace");
            if (ws.exists()) {
                File wsFile = new File(ws, fileName);
                try (FileOutputStream fos = new FileOutputStream(wsFile)) {
                    fos.write(fullExport.getBytes(StandardCharsets.UTF_8));
                }
            }
            DocumentManager.getInstance().reloadIfOpen(fileName, fullExport);
            projects.refreshTree(context);
            return fileName + " (im Projektordner)";
        }
    }

    public void addListener(Events l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Events l) {
        listeners.remove(l);
    }

    public void setStatusListener(StatusListener listener) {
        this.statusListener = listener;
    }

    public void setApproval(Approval approval) {
        this.approvalHandler = approval;
    }

    public void cancel() {
        cancelled.set(true);
        BuildManager.getInstance().cancelBuild();
        updateStatus("Abbruch angefordert …");
    }

    private void updateStatus(String status) {
        this.currentStatus = status;
        main.post(() -> {
            if (statusListener != null) {
                statusListener.onStatus(status, running);
            }
        });
    }

    private void emit(String message) {
        synchronized (logHistory) {
            if (logHistory.length() > 250_000) {
                logHistory.delete(0, 60_000);
            }
            logHistory.append(message).append("\n");
        }
        main.post(() -> {
            for (Events l : listeners) {
                l.onEvent(message);
            }
        });

        // Audit-Log
        try (FileOutputStream out = new FileOutputStream(journal, true)) {
            JSONObject line = new JSONObject();
            line.put("time", System.currentTimeMillis());
            line.put("message", message);
            out.write((line.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private void finish(String message) {
        running = false;
        updateStatus(message);
        synchronized (logHistory) {
            logHistory.append("\n=== ").append(message).append(" ===\n");
        }
        main.post(() -> {
            for (Events l : listeners) {
                l.onFinished(message);
            }
        });
    }

    public synchronized void start(String task, Settings settings, Approval approval) {
        if (running) {
            emit("⚠️ Ein Agentenauftrag läuft bereits.");
            return;
        }
        if (task == null || task.isBlank()) {
            emit("⚠️ Auftrag fehlt.");
            return;
        }
        if (approval != null) {
            this.approvalHandler = approval;
        }
        this.lastTask = task;
        running = true;
        cancelled.set(false);
        updateStatus("Starte Agentenauftrag …");

        worker.execute(() -> {
            try {
                execute(task, settings);
            } catch (java.net.SocketTimeoutException te) {
                String teMsg = te.getMessage() != null ? te.getMessage().toLowerCase(Locale.ROOT) : "";
                if (teMsg.contains("connect")) {
                    emit("❌ Timeout beim Verbindungsaufbau zu " + settings.endpoint() + " (nach " + (CONNECT_TIMEOUT_MS / 1000) + "s).\n"
                            + "👉 Mögliche Ursachen:\n"
                            + "  1. Ist der Server online und die URL/Port korrekt?\n"
                            + "  2. Bei lokalem PC-Server (Ollama/LM Studio): Nutzen Sie die WLAN-IP Ihres PCs (z. B. http://192.168.x.x:11434) oder 'adb reverse tcp:11434 tcp:11434'.\n"
                            + "  3. Prüfen Sie die Firewall auf dem Rechner.");
                } else {
                    emit("❌ Timeout beim Warten auf die Modellantwort von " + settings.endpoint() + " (nach " + (READ_TIMEOUT_MS / 60000) + " Minuten / " + (READ_TIMEOUT_MS / 1000) + "s).\n"
                            + "👉 Das Modell (" + settings.model() + ") hat die maximale Antwortzeit überschritten.\n"
                            + "👉 Tipp: Bei lokalen Modellen (LM Studio / Ollama) kann die Inferenz komplexer Prompts mehrere Minuten dauern.");
                }
            } catch (java.net.ConnectException ce) {
                emit("❌ Verbindungsaufbau verweigert (" + settings.endpoint() + "): " + ce.getMessage() + "\n"
                        + "👉 Server läuft nicht oder Port ist blockiert.");
            } catch (Exception e) {
                emit("❌ Agentenfehler: " + e.getMessage());
            } finally {
                running = false;
                finish(cancelled.get() ? "Vom Benutzer abgebrochen" : "Auftrag beendet");
            }
        });
    }

    private void execute(String task, Settings settings) throws Exception {
        String ep = settings.endpoint().trim();
        boolean isLocalOrLan = ep.startsWith("http://127.0.0.1:")
                || ep.startsWith("http://localhost:")
                || ep.startsWith("http://10.")
                || ep.startsWith("http://192.168.")
                || ep.startsWith("http://172.");
        if (!ep.startsWith("https://") && !isLocalOrLan) {
            throw new IOException("Für entfernte Modellserver im Internet ist HTTPS erforderlich (z. B. https://api.openai.com/...). Für lokale Modelle ist HTTP im Heimnetzwerk (192.168.x.x / 10.x.x.x) oder localhost erlaubt.");
        }

        ProjectManager projects = ProjectManager.getInstance();
        File localRoot = projects.getActiveLocalDir();
        Uri safUri = projects.getActiveSafTreeUri();
        String projectName = projects.getActiveProjectName();

        if ((localRoot == null || !localRoot.isDirectory()) && safUri == null) {
            throw new IOException("Kein Projekt geöffnet. Bitte zuerst einen Projektordner im Menü öffnen oder erstellen.");
        }

        final File workspace;
        final boolean isSaf = (localRoot == null || !localRoot.isDirectory()) && safUri != null;

        if (isSaf) {
            workspace = new File(context.getFilesDir(), "saf_agent_workspace");
            if (!workspace.exists() && !workspace.mkdirs()) {
                throw new IOException("Konnte lokalen SAF-Arbeitsbereich nicht anlegen: " + workspace);
            }
            updateStatus("Lade Projekt aus Speicher …");
            emit("📂 Geladenes Speicher-Projekt erkannt: " + projectName);
            emit("⏳ Synchronisiere Dateien aus Gerätespeicher in lokalen Arbeitsbereich …");
            DocumentTree.importProject(context, safUri, workspace);
            emit("✅ Projektdateien erfolgreich synchronisiert.");
        } else {
            workspace = localRoot.getCanonicalFile();
            emit("📂 Lokales Projekt geöffnet: " + projectName + " (" + workspace.getAbsolutePath() + ")");
        }

        // 1. Vorhandene Dateien scannen
        String fileListing = listFiles(workspace, "");
        emit("📋 Projektstruktur:\n" + fileListing);

        // 2. Chat-Verlauf aufbauen
        boolean isReadOnly = isReadOnlyTask(task);

        JSONArray history = new JSONArray();
        JSONObject initial = new JSONObject();
        initial.put("role", "system");

        StringBuilder sys = new StringBuilder();
        sys.append("Du bist der autonome Programmieragent der KR Compiler Android IDE. Du entwickelst und reparierst Projekte in der KR Unified Language (.kr), C++20, Rust und Go.\n\n")
           .append("KOMMUNIKATIONSREGELN:\n")
           .append("1. Antworte AUSSCHLIESSLICH als gültiges JSON-Objekt mit genau diesen Schlüsseln: {\"action\": \"...\", \"path\": \"...\", \"search\": \"...\", \"replace\": \"...\", \"content\": \"...\", \"message\": \"...\"}\n")
           .append("   action MUSS eines der folgenden Schlüsselwörter sein: list, read, replace, write, build, done.\n")
           .append("2. WERKZEUG-REGELN:\n")
           .append("   - 'list': Listet Verzeichnisse auf (z. B. path=\"\" für das Hauptverzeichnis, path=\"gui\" für Unterordner).\n")
           .append("   - 'read': Liest den Inhalt einer Textdatei mit Zeilennummern (z. B. path=\"nexus.krproject\", path=\"main.kr\").\n")
           .append("             TIPP: Um gezielt Zeilen zu lesen, gib den Bereich an: path=\"gui/window.kr:190-230\".\n")
           .append("   - 'replace': Ersetzt einen fehlerhaften Codeabschnitt zielgenau in einer Datei (benötigt 'path', 'search' und 'replace').\n")
           .append("   - 'write': Schreibt eine Datei komplett neu oder legt eine neue Datei an (benötigt 'path' und 'content').\n")
           .append("   - 'build': Kompiliert das Projekt. Baue erst nach einer gezielten Reparatur oder um einen gemeldeten Compilerfehler zu reproduzieren.\n")
           .append("   - 'done': Beendet den Auftrag erfolgreich (benötigt 'message' mit deiner Zusammenfassung auf Deutsch).\n\n");

        if (isReadOnly) {
            sys.append("⚠️ WICHTIGER HINWEIS ZUM AKTUELLEN AUFTRAG:\n")
               .append("Der aktuelle Auftrag ist eine reine Analyse-, Lese- oder Informationsabfrage!\n")
               .append("Führe KEINE Änderungen aus (weder 'write' noch 'replace') und führe KEINEN 'build' aus!\n")
               .append("Lies die relevanten Dateien mit 'read' und beende dann direkt mit 'done'. Erkläre deine Analyse im Feld 'message'.\n\n");
        }

        sys.append("Begründe jede Aktion im Feld 'message' kurz und verständlich auf Deutsch.");
        initial.put("content", sys.toString());
        history.put(initial);

        String initialPrompt = "AUFTRAG:\n" + task + "\n\n" +
                "PROJEKT: " + projectName + "\n" +
                "DATEIEN IM PROJEKT:\n" + fileListing + "\n\n" +
                (isReadOnly
                        ? "Bitte analysiere die relevanten Dateien mit 'read' und beantworte den Auftrag im Feld 'message' von 'done'. Bitte KEINE Änderungen und KEINEN Build durchführen!"
                        : "Bitte starte mit der Analyse der relevanten Dateien.");
        history.put(new JSONObject().put("role", "user").put("content", initialPrompt));

        int budget = Math.min(40, Math.max(1, settings.maxSteps()));

        for (int step = 0; step < budget && !cancelled.get(); ++step) {
            compactHistory(history);

            updateStatus("Schritt " + (step + 1) + ": Modell denkt nach (" + settings.model() + ") …");
            emit("🤖 [Schritt " + (step + 1) + "/" + budget + "] Sende Anfrage an KI-Modell (" + settings.model() + ") …");

            JSONObject action = askModel(settings, history);
            String kind = action.optString("action", "done").trim().toLowerCase(Locale.ROOT);
            String path = action.optString("path", "").trim();
            String reason = action.optString("message", "").trim();

            if (!reason.isEmpty()) {
                emit("💬 Begründung: " + reason);
            }
            emit("⚡ Aktion: " + kind + (path.isEmpty() ? "" : " -> " + path));
            updateStatus("Schritt " + (step + 1) + ": " + kind + (path.isEmpty() ? "" : " " + path));

            String output;
            try {
                switch (kind) {
                    case "list" -> output = listFiles(workspace, path);
                    case "read" -> output = read(workspace, path, action);
                    case "replace" -> {
                        if (isReadOnly) {
                            output = "Hinweis: Der Benutzer hat keine Codeänderung angefordert (Auftrag: '" + task + "'). Bitte ändere keine Dateien, sondern analysiere die vorhandenen Dateien und beende mit 'done'.";
                            emit("ℹ️ Codeänderung übersprungen (reiner Analyseauftrag).");
                            break;
                        }
                        String search = action.optString("search", "");
                        String replacement = action.optString("replace", action.optString("content", ""));

                        File target = null;
                        String effectivePath = path;
                        try {
                            target = safeFile(workspace, path, false);
                        } catch (Exception ignored) {}

                        if (target == null || !target.isFile() || effectivePath.contains("KR_BUILD") || effectivePath.endsWith(".rs") || effectivePath.endsWith(".go")) {
                            String resolved = findSourceForGeneratedFile(workspace, path, null);
                            if (resolved != null && !resolved.equalsIgnoreCase(path)) {
                                File candidate = new File(workspace, resolved);
                                if (candidate.isFile()) {
                                    target = candidate;
                                    effectivePath = resolved;
                                }
                            }
                        }

                        if (target == null || !target.isFile()) {
                            throw new IOException("Datei existiert nicht: " + path);
                        }
                        String current = new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
                        String updated = performReplace(current, search, replacement);
                        String diffPreview = "Datei: " + effectivePath + "\n\n--- ZU ERSETZEN ---\n" + preview(search) + "\n\n+++ NEUER CODE +++\n" + preview(replacement);
                        boolean ok = requireApproval("Code ersetzen in: " + effectivePath, diffPreview);
                        if (!ok) {
                            output = "Ersetzung wurde vom Benutzer abgelehnt.";
                            emit("❌ Ersetzung abgelehnt: " + effectivePath);
                        } else {
                            writeAtomically(target, updated);
                            if (isSaf && safUri != null) {
                                try {
                                    DocumentTree.writeSafFile(context, safUri, effectivePath, updated);
                                    emit("💾 Datei im Gerätespeicher gesichert: " + effectivePath);
                                } catch (Exception ex) {
                                    emit("⚠️ Warnung beim Schreiben in Gerätespeicher: " + ex.getMessage());
                                }
                            }
                            final String finalPath = effectivePath;
                            final String finalContent = updated;
                            main.post(() -> {
                                projects.refreshTree(context);
                                DocumentManager.getInstance().reloadIfOpen(finalPath, finalContent);
                            });
                            if (!effectivePath.equalsIgnoreCase(path)) {
                                output = "Code in '" + effectivePath + "' erfolgreich ersetzt! (Hinweis: '" + path + "' war ein generiertes Build-Artefakt; die Änderung wurde automatisch in der Original-Quelldatei gespeichert).";
                                emit("✅ Code ersetzt in: " + effectivePath + " (aus '" + path + "')");
                            } else {
                                output = "Code in '" + path + "' erfolgreich ersetzt!";
                                emit("✅ Code ersetzt in: " + path);
                            }
                        }
                    }
                    case "write" -> {
                        if (isReadOnly) {
                            output = "Hinweis: Der Benutzer hat keine Dateiänderung angefordert (Auftrag: '" + task + "'). Bitte ändere keine Dateien, sondern analysiere die vorhandenen Dateien und beende mit 'done'.";
                            emit("ℹ️ Dateiänderung übersprungen (reiner Analyseauftrag).");
                            break;
                        }
                        String content = action.optString("content", "");
                        File target = safeFile(workspace, path, true);
                        if (target.isDirectory()) {
                            throw new IOException("Zielpfad ist ein Verzeichnis: " + path + ". Bitte konkrete Datei angeben.");
                        }
                        if (target.exists() && target.length() > 1024 * 1024) {
                            throw new IOException("Datei zu groß (> 1 MiB): " + path);
                        }
                        String diffPreview = "Datei: " + path + "\n\n" + preview(content);
                        boolean ok = requireApproval("Datei ändern: " + path, diffPreview);
                        if (!ok) {
                            output = "Änderung wurde vom Benutzer abgelehnt.";
                            emit("❌ Änderung abgelehnt: " + path);
                        } else {
                            writeAtomically(target, content);
                            if (isSaf && safUri != null) {
                                try {
                                    DocumentTree.writeSafFile(context, safUri, path, content);
                                    emit("💾 Datei im Gerätespeicher gesichert: " + path);
                                } catch (Exception ex) {
                                    emit("⚠️ Warnung beim Schreiben in Gerätespeicher: " + ex.getMessage());
                                }
                            }
                            final String finalPath = path;
                            final String finalContent = content;
                            main.post(() -> {
                                projects.refreshTree(context);
                                DocumentManager.getInstance().reloadIfOpen(finalPath, finalContent);
                            });
                            output = "Datei erfolgreich geschrieben: " + path;
                            emit("✅ Datei geschrieben: " + path);
                        }
                    }
                    case "build" -> {
                        if (isReadOnly) {
                            output = "Hinweis: Der Benutzer hat keinen Build angefordert (Auftrag: '" + task + "'). Bitte führe keinen Build aus! Wenn du genügend Dateien gelesen hast, beende mit 'done' und gib deine Analyse im Feld 'message' aus.";
                            emit("ℹ️ Build übersprungen (reiner Analyseauftrag).");
                            break;
                        }
                        CompilerEngine.Target target = BuildManager.getInstance().getCurrentTarget();
                        boolean ok = requireApproval("Projekt kompilieren",
                                "Zielplattform: " + target + "\nProjekt: " + projectName + "\n\nSoll der Compilerlauf jetzt gestartet werden?");
                        if (!ok) {
                            output = "Kompilierung wurde vom Benutzer abgelehnt. Bitte fahre ohne Build mit 'done' fort und erkläre deine Analyse.";
                            emit("❌ Build abgelehnt.");
                        } else {
                            output = build(workspace, isSaf ? safUri : null, target);
                            if (isSaf && safUri != null) {
                                try {
                                    DocumentTree.exportBuild(context, safUri, new File(workspace, "KR_BUILD"));
                                    emit("📦 Build-Artefakte in Gerätespeicher exportiert.");
                                } catch (Exception ex) {
                                    emit("⚠️ Export-Warnung: " + ex.getMessage());
                                }
                            }
                        }
                    }
                    case "done" -> {
                        String finalMessage = action.optString("message", "Auftrag erfolgreich abgeschlossen.");
                        emit("🏁 ERGEBNIS:\n" + finalMessage);
                        return;
                    }
                    default -> output = "Unbekannte Aktion '" + kind + "'. Erlaubt sind: list, read, replace, write, build, done";
                }
            } catch (Exception ex) {
                output = "Fehler bei Aktion '" + kind + "': " + ex.getMessage() + ". Bitte Parameter prüfen und nächsten Schritt ausführen.";
                emit("⚠️ " + output);
            }

            emit("📄 Ergebnis: " + (output.length() > 1500 ? output.substring(0, 1500) + " … [gekürzt]" : output));
            history.put(new JSONObject().put("role", "assistant").put("content", action.toString()));
            history.put(new JSONObject().put("role", "user").put("content",
                    "Werkzeugergebnis:\n" + truncate(output, 5000) + "\n\nFahre mit der nächsten logischen Aktion fort."));
        }

        emit("⚠️ Maximale Schrittzahl (" + budget + ") erreicht oder abgebrochen.");
    }

    private static void compactHistory(JSONArray history) {
        int len = history.length();
        if (len <= 6) return;
        for (int i = 2; i < len - 4; i++) {
            JSONObject msg = history.optJSONObject(i);
            if (msg != null && "user".equals(msg.optString("role"))) {
                String c = msg.optString("content", "");
                if (c.startsWith("Werkzeugergebnis:\n") && c.length() > 1000) {
                    msg.put("content", c.substring(0, 1000) + "\n[…älteres Ergebnis für Kontext gekürzt…]");
                }
            }
        }
    }

    private JSONObject askModel(Settings s, JSONArray messages) throws Exception {
        String ep = s.endpoint().trim();
        if (ep.endsWith("/api/v1/chat")) {
            ep = ep.substring(0, ep.length() - "/api/v1/chat".length()) + "/v1/chat/completions";
        } else if (ep.endsWith("/v1/chat")) {
            ep = ep + "/completions";
        } else if (ep.endsWith("/v1")) {
            ep = ep + "/chat/completions";
        } else if (!ep.endsWith("/chat/completions") && !ep.endsWith("/completions")) {
            if (ep.endsWith("/")) ep = ep.substring(0, ep.length() - 1);
            ep = ep + "/v1/chat/completions";
        }

        URL url = new URL(ep);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (!s.token().isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + s.token());
        }

        JSONObject body = new JSONObject()
                .put("model", s.model())
                .put("messages", messages)
                .put("temperature", 0.1);

        try {
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            if (stream == null) throw new IOException("Modellserver HTTP " + code);

            String payload;
            try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (output.size() + count > 1024 * 1024) throw new IOException("Modellantwort über 1 MiB");
                    output.write(buffer, 0, count);
                }
                payload = output.toString(StandardCharsets.UTF_8.name());
            }

            if (code < 200 || code >= 300) {
                throw new IOException("Modellserver HTTP " + code + ": " + truncate(payload, 300));
            }

            JSONObject response = new JSONObject(payload);
            String rawText = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
            return parseActionJson(rawText);
        } finally {
            c.disconnect();
        }
    }

    private static String sanitizeJsonStrings(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 32);
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                sb.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                sb.append(c);
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }
            if (inString) {
                if (c == '\n') {
                    sb.append("\\n");
                } else if (c == '\r') {
                    // skip CR
                } else if (c == '\t') {
                    sb.append("\\t");
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String extractRegex(String src, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(src);
        return m.find() ? m.group(1) : null;
    }

    private static String extractContentField(String text) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"content\"\\s*:\\s*\"");
        java.util.regex.Matcher m = p.matcher(text);
        if (!m.find()) return "";
        int start = m.end();
        int end = -1;
        int msgIdx = text.indexOf("\"message\"", start);
        if (msgIdx > 0) {
            int lastQuote = text.lastIndexOf('"', msgIdx - 1);
            if (lastQuote > start) end = lastQuote;
        }
        if (end <= start) {
            int lastBrace = text.lastIndexOf('}');
            if (lastBrace > start) {
                int lastQuote = text.lastIndexOf('"', lastBrace);
                if (lastQuote > start) end = lastQuote;
            }
        }
        if (end > start) {
            return text.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return "";
    }

    private static String extractNamedField(String text, String fieldName) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return null;
    }

    private static String performReplace(String fileContent, String search, String replacement) throws IOException {
        if (search == null || search.isEmpty()) {
            throw new IOException("'replace' benötigt ein 'search'-Feld mit dem zu ersetzenden Codeabschnitt.");
        }
        if (replacement == null) replacement = "";

        // 1. Bereinige eventuell vom Modell mitkopierte Zeilennummern (z. B. " 207 | ")
        String cleanSearch = search.replaceAll("(?m)^\\s*\\d+\\s*[|:]\\s*", "");
        String cleanReplace = replacement.replaceAll("(?m)^\\s*\\d+\\s*[|:]\\s*", "");

        // 2. Direkter Treffer
        if (fileContent.contains(cleanSearch)) {
            return fileContent.replace(cleanSearch, cleanReplace);
        }

        // 3. CRLF / LF Normalisierung
        String normContent = fileContent.replace("\r\n", "\n");
        String normSearch = cleanSearch.replace("\r\n", "\n");
        String normReplace = cleanReplace.replace("\r\n", "\n");
        if (normContent.contains(normSearch)) {
            String updated = normContent.replace(normSearch, normReplace);
            return fileContent.contains("\r\n") ? updated.replace("\n", "\r\n") : updated;
        }

        // 4. Zeilenweiser getrimmter Abgleich für Einzeiler
        String trimmedSearch = normSearch.trim();
        if (!trimmedSearch.isEmpty() && !trimmedSearch.contains("\n")) {
            String[] lines = normContent.split("\n", -1);
            boolean replaced = false;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].trim().equals(trimmedSearch) || lines[i].contains(trimmedSearch)) {
                    String leadingIndent = lines[i].replaceAll("^(\\s*).*", "$1");
                    lines[i] = leadingIndent + normReplace.trim();
                    replaced = true;
                    break;
                }
            }
            if (replaced) {
                String updated = String.join("\n", lines);
                return fileContent.contains("\r\n") ? updated.replace("\n", "\r\n") : updated;
            }
        }

        throw new IOException("Suchmuster 'search' wurde in der Zieldatei nicht gefunden!\nGesucht wurde:\n" + cleanSearch);
    }

    private static JSONObject parseActionJson(String raw) throws JSONException {
        if (raw == null || raw.isBlank()) {
            throw new JSONException("Leere Antwort vom Modell erhalten.");
        }
        String text = raw.trim();

        // 1. Entferne <think>...</think> Tags
        text = text.replaceAll("(?si)<think>.*?</think>", "").trim();

        // 2. Extrahiere Markdown Code-Block falls vorhanden
        if (text.contains("```")) {
            int firstFence = text.indexOf("```");
            int secondFence = text.indexOf("```", firstFence + 3);
            if (secondFence > firstFence) {
                String inside = text.substring(firstFence + 3, secondFence).trim();
                if (inside.toLowerCase(Locale.ROOT).startsWith("json")) {
                    inside = inside.substring(4).trim();
                }
                text = inside;
            }
        }

        // 3. Bereinige unescapte Zeilenumbrüche in String-Literalen
        String sanitized = sanitizeJsonStrings(text);

        // 4. Finde das äußerste JSON-Objekt { ... }
        int start = sanitized.indexOf('{');
        int end = sanitized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String jsonPart = sanitized.substring(start, end + 1).trim();
            try {
                return new JSONObject(jsonPart);
            } catch (JSONException ignored) {}
        }

        // 5. Direkter Versuch
        try {
            return new JSONObject(sanitized);
        } catch (JSONException ignored) {}

        // 6. Regex-Extraktion falls JSON syntaktisch unvollständig ist
        String actionVal = extractRegex(text, "\"action\"\\s*:\\s*\"([a-zA-Z]+)\"");
        if (actionVal != null && !actionVal.isBlank()) {
            JSONObject recovered = new JSONObject();
            recovered.put("action", actionVal.trim().toLowerCase(Locale.ROOT));
            String pathVal = extractRegex(text, "\"path\"\\s*:\\s*\"([^\"]+)\"");
            recovered.put("path", pathVal != null ? pathVal.trim() : "");
            String searchVal = extractNamedField(text, "search");
            recovered.put("search", searchVal != null ? searchVal : "");
            String replaceVal = extractNamedField(text, "replace");
            recovered.put("replace", replaceVal != null ? replaceVal : "");
            String msgVal = extractRegex(text, "\"message\"\\s*:\\s*\"([^\"]+)\"");
            recovered.put("message", msgVal != null ? msgVal.trim() : "");
            String contentVal = extractContentField(text);
            recovered.put("content", contentVal != null ? contentVal : "");
            return recovered;
        }

        // 7. Fallback bei freier Textantwort: Behandle als 'done' mit Antworttext
        JSONObject fallback = new JSONObject();
        fallback.put("action", "done");
        fallback.put("path", "");
        fallback.put("message", text);
        return fallback;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + " [abgeschnitten]" : s;
    }

    private static String preview(String text) {
        return truncate(text, 1500);
    }

    private boolean requireApproval(String title, String detail) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean approved = new AtomicBoolean();
        updateStatus("Warte auf Freigabe: " + title);
        emit("❓ Freigabe erbeten: " + title);

        main.post(() -> {
            Approval handler = approvalHandler;
            if (handler != null) {
                handler.request(title, detail, accepted -> {
                    approved.set(accepted);
                    latch.countDown();
                });
            } else {
                approved.set(false);
                latch.countDown();
            }
        });

        while (!cancelled.get()) {
            if (latch.await(200, TimeUnit.MILLISECONDS)) {
                return approved.get();
            }
        }
        return false;
    }

    private static String findSourceForGeneratedFile(File root, String path, String functionHint) {
        if (root == null || path == null) return "main.kr";

        // 1. Prüfe KrSourceMap (KR_BUILD/source_map.json)
        KrSourceMap sourceMap = KrSourceMap.loadFromProject(root);
        if (sourceMap != null) {
            String resolved = sourceMap.resolveOriginalFile(path, functionHint);
            if (resolved != null && new File(root, resolved).isFile()) {
                return resolved;
            }
        }

        // 2. Heuristische Erkennung für Rust (src/lib.rs, lib.rs, rust_module)
        String norm = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (norm.endsWith(".rs") || norm.contains("rust_module")) {
            List<File> krFiles = KrPipeline.findKrFiles(root);
            if (functionHint != null && !functionHint.isBlank()) {
                for (File k : krFiles) {
                    try {
                        String content = Files.readString(k.toPath(), StandardCharsets.UTF_8);
                        if (content.contains("fn " + functionHint)) {
                            return root.toPath().relativize(k.toPath()).toString().replace('\\', '/');
                        }
                    } catch (Exception ignored) {}
                }
            }
            for (File k : krFiles) {
                try {
                    String content = Files.readString(k.toPath(), StandardCharsets.UTF_8);
                    if (content.contains("pub fn ") || content.contains("extern \"C\" fn")) {
                        return root.toPath().relativize(k.toPath()).toString().replace('\\', '/');
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3. Heuristische Erkennung für Go (main.go, go_module)
        if (norm.endsWith(".go") || norm.contains("go_module")) {
            List<File> krFiles = KrPipeline.findKrFiles(root);
            if (functionHint != null && !functionHint.isBlank()) {
                for (File k : krFiles) {
                    try {
                        String content = Files.readString(k.toPath(), StandardCharsets.UTF_8);
                        if (content.contains("func " + functionHint)) {
                            return root.toPath().relativize(k.toPath()).toString().replace('\\', '/');
                        }
                    } catch (Exception ignored) {}
                }
            }
            for (File k : krFiles) {
                try {
                    String content = Files.readString(k.toPath(), StandardCharsets.UTF_8);
                    if (content.contains("func ") && !content.contains("#include")) {
                        return root.toPath().relativize(k.toPath()).toString().replace('\\', '/');
                    }
                } catch (Exception ignored) {}
            }
        }

        // 4. Modul- und Namensbasierte Auflösung (z. B. modules/gui_window.cpp -> gui/window.kr)
        String filename = new File(path).getName();
        String base = filename.replace(".cpp", "").replace(".c", "").replace("_kr", "");
        File direct = new File(root, base + ".kr");
        if (direct.isFile()) return base + ".kr";
        if (base.contains("_")) {
            String[] parts = base.split("_", 2);
            File sub = new File(new File(root, parts[0]), parts[1] + ".kr");
            if (sub.isFile()) return parts[0] + "/" + parts[1] + ".kr";
        }
        List<File> krFiles = KrPipeline.findKrFiles(root);
        for (File k : krFiles) {
            String kn = k.getName().replace(".kr", "");
            if (kn.equalsIgnoreCase(base) || base.endsWith(kn)) {
                return root.toPath().relativize(k.toPath()).toString().replace('\\', '/');
            }
        }
        return "main.kr";
    }

    private static String findSourceForGeneratedFile(File root, String path) {
        return findSourceForGeneratedFile(root, path, null);
    }

    private static File safeFile(File root, String relative, boolean isWrite) throws IOException {
        if (relative == null || relative.isBlank()) {
            return root.getCanonicalFile();
        }
        String clean = relative.trim().replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        while (clean.startsWith("./")) clean = clean.substring(2);
        if (clean.isEmpty() || clean.equals(".")) {
            return root.getCanonicalFile();
        }

        if (clean.startsWith("KR_BUILD/") || clean.equals("KR_BUILD")) {
            if (isWrite) {
                String src = findSourceForGeneratedFile(root, clean);
                throw new IOException("'" + clean + "' ist ein generiertes Build-Artefakt und wird beim Build überschrieben. Bitte bearbeite stattdessen die Quelldatei im Projektordner: '" + src + "'!");
            }
            File bTarget = new File(root, clean).getCanonicalFile();
            if (bTarget.toPath().startsWith(root.toPath())) {
                return bTarget;
            }
        }

        File target = new File(root, clean).getCanonicalFile();
        if (!target.toPath().startsWith(root.toPath())) {
            throw new IOException("Pfad außerhalb des Projekts: " + relative);
        }
        for (String segment : clean.split("/")) {
            if (Set.of(".git", ".kragent", "..").contains(segment)) {
                throw new IOException("Geschützter Pfad: " + segment);
            }
        }
        return target;
    }

    private String listFiles(File root, String relative) throws IOException {
        File dir = (relative == null || relative.isBlank() || relative.equals(".") || relative.equals("./")) ? root : safeFile(root, relative, false);
        if (!dir.isDirectory()) return "Kein Ordner: " + relative;
        File[] files = dir.listFiles();
        if (files == null) return "Ordner nicht lesbar";
        Arrays.sort(files, Comparator.comparing(File::getName));
        StringBuilder b = new StringBuilder();
        int count = 0;
        for (File f : files) {
            if (f.getName().startsWith(".") || f.getName().equals("KR_BUILD")) continue;
            b.append(f.isDirectory() ? "[D] " : "[F] ").append(f.getName()).append('\n');
            if (++count >= 250) {
                b.append("... [weitere Dateien gekürzt]\n");
                break;
            }
        }
        return b.length() == 0 ? "(Ordner ist leer)" : b.toString();
    }

    private String read(File root, String relative, JSONObject action) throws IOException {
        if (relative == null || relative.isBlank() || relative.equals(".") || relative.equals("./")) {
            return "Hinweis: '" + relative + "' ist das Projektverzeichnis. Bitte gib einen Dateinamen an (z. B. 'main.kr', 'nexus.krproject' oder 'README.md').\nDateien im Projekt:\n" + listFiles(root, "");
        }
        String cleanPath = relative.trim();
        int reqStartLine = -1;
        int reqEndLine = -1;

        // Prüfe ob path das Format datei:start-end oder datei:zeile hat (z. B. gui/window.kr:190-230)
        int colonIdx = cleanPath.lastIndexOf(':');
        if (colonIdx > 0 && colonIdx < cleanPath.length() - 1) {
            String suffix = cleanPath.substring(colonIdx + 1).trim();
            if (suffix.matches("\\d+(?:-\\d+)?")) {
                cleanPath = cleanPath.substring(0, colonIdx).trim();
                if (suffix.contains("-")) {
                    String[] parts = suffix.split("-", 2);
                    try {
                        reqStartLine = Integer.parseInt(parts[0]);
                        reqEndLine = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {}
                } else {
                    try {
                        int targetLine = Integer.parseInt(suffix);
                        reqStartLine = Math.max(1, targetLine - 25);
                        reqEndLine = targetLine + 25;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (reqStartLine < 0 && action != null) {
            String linesStr = action.optString("lines", "").trim();
            if (linesStr.matches("\\d+-\\d+")) {
                String[] parts = linesStr.split("-", 2);
                try {
                    reqStartLine = Integer.parseInt(parts[0]);
                    reqEndLine = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
            }
        }

        File file = null;
        String effectivePath = cleanPath;
        String redirectionNotice = null;
        try {
            file = safeFile(root, cleanPath, false);
        } catch (Exception ignored) {}

        if (file == null || !file.isFile()) {
            String resolved = findSourceForGeneratedFile(root, cleanPath, null);
            if (resolved != null && !resolved.equalsIgnoreCase(cleanPath)) {
                File candidate = new File(root, resolved);
                if (candidate.isFile()) {
                    file = candidate;
                    effectivePath = resolved;
                    redirectionNotice = "ℹ️ HINWEIS: '" + cleanPath + "' ist ein vom KR-Compiler generiertes Build-Artefakt.\n"
                            + "👉 Die Original-Quelldatei im Projekt ist: '" + resolved + "'.\n"
                            + "Hier ist der Inhalt von '" + resolved + "' (Änderungen bitte per 'replace' direkt auf '" + resolved + "' anwenden!):\n\n";
                }
            }
        }

        if (file == null || !file.exists()) {
            throw new IOException("Datei nicht gefunden: " + cleanPath);
        }
        if (file.isDirectory()) {
            return "Hinweis: '" + cleanPath + "' ist ein Ordner, keine Datei. Verwende 'list', um Ordnerinhalte aufzulisten, oder lies eine konkrete Datei im Ordner.\nInhalt von " + cleanPath + ":\n" + listFiles(root, cleanPath);
        }
        if (!file.isFile() || file.length() > 512 * 1024) {
            throw new IOException("Nur Textdateien bis 512 KiB lesbar: " + cleanPath);
        }
        if (ProjectManager.isBinaryFile(file.getName())) {
            throw new IOException("Binärdatei nicht erlaubt: " + cleanPath);
        }

        List<String> allLines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        if (allLines.isEmpty()) return (redirectionNotice != null ? redirectionNotice : "") + "(Datei ist leer)";

        if (reqStartLine > 0) {
            int start = Math.max(1, Math.min(reqStartLine, allLines.size()));
            int end = Math.max(start, Math.min(reqEndLine > 0 ? reqEndLine : start + 50, allLines.size()));
            StringBuilder sb = new StringBuilder();
            if (redirectionNotice != null) sb.append(redirectionNotice);
            sb.append("Ausschnitt aus '").append(effectivePath).append("' (Zeilen ").append(start).append(" bis ").append(end).append(" von ").append(allLines.size()).append("):\n");
            for (int i = start; i <= end; i++) {
                sb.append(String.format(Locale.ROOT, "%4d | %s\n", i, allLines.get(i - 1)));
            }
            return sb.toString();
        }

        if (allLines.size() > 180) {
            StringBuilder sb = new StringBuilder();
            if (redirectionNotice != null) sb.append(redirectionNotice);
            sb.append("Ausschnitt aus '").append(effectivePath).append("' (Zeilen 1 bis 120 von ").append(allLines.size()).append("):\n");
            for (int i = 1; i <= Math.min(120, allLines.size()); i++) {
                sb.append(String.format(Locale.ROOT, "%4d | %s\n", i, allLines.get(i - 1)));
            }
            sb.append("... [Zeilen 121 bis ").append(allLines.size()).append(" gekürzt. Nutze: {\"action\": \"read\", \"path\": \"").append(effectivePath).append(":121-240\"} für weitere Zeilen]\n");
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder();
        if (redirectionNotice != null) sb.append(redirectionNotice);
        for (int i = 1; i <= allLines.size(); i++) {
            sb.append(String.format(Locale.ROOT, "%4d | %s\n", i, allLines.get(i - 1)));
        }
        return sb.toString();
    }

    private void writeAtomically(File file, String content) throws IOException {
        if (ProjectManager.isBinaryFile(file.getName())) {
            throw new IOException("Binärdatei nicht erlaubt: " + file.getName());
        }
        if (content.length() > 1024 * 1024) {
            throw new IOException("Inhalt größer als 1 MiB: " + file.getName());
        }
        Files.createDirectories(file.getParentFile().toPath());
        Path temp = Files.createTempFile(file.getParentFile().toPath(), ".kr_agent_", ".tmp");
        try {
            Files.write(temp, content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private String build(File root, Uri safUri, CompilerEngine.Target target) throws InterruptedException {
        BuildManager manager = BuildManager.getInstance();
        if (manager.isBuilding()) return "Bereits laufender Build. Bitte warten.";

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean();
        String[] summary = new String[1];

        BuildManager.Listener listener = new BuildManager.Listener() {
            public void onLogLine(String line) {}
            public void onStatusChanged(String status, boolean building) {}
            public void onBuildFinished(boolean ok, String result) {
                success.set(ok);
                summary[0] = result;
                latch.countDown();
            }
        };
        manager.addListener(listener);

        try {
            updateStatus("Kompiliere für " + target + " …");
            emit("🔨 Starte Kompilierung für " + target + " …");
            main.post(() -> manager.startBuild(context, safUri, root, target, manager.isOnlineDependencies()));

            while (!cancelled.get()) {
                if (latch.await(250, TimeUnit.MILLISECONDS)) break;
            }
            if (cancelled.get()) return "Build abgebrochen";

            boolean ok = success.get();
            emit(ok ? "✅ Build erfolgreich abgeschlossen." : "❌ Build mit Fehlern beendet.");
            String buildResult = (ok ? "BUILD OK\n" : "BUILD FEHLER\n") +
                    truncate(summary[0] == null ? "" : summary[0], 3000) + "\n" +
                    truncate(manager.getFullLog(), 9000);
            if (!ok) {
                String hints = extractDiagnosticHints(root, manager.getFullLog());
                if (!hints.isEmpty()) {
                    buildResult += "\n" + hints;
                } else {
                    buildResult += "\n\nHINWEIS ZUR FEHLERBEHEBUNG:\n" +
                            "Dateien unter 'KR_BUILD/modules/...' werden vom KR-Compiler aus den '.kr'-Quelldateien im Projekt generiert.\n" +
                            "Um Compilerfehler zu beheben, nutze die Aktion 'replace' direkt auf der Quelldatei im Projekt (z. B. 'gui/window.kr')!";
                }
            }
            return buildResult;
        } finally {
            manager.removeListener(listener);
        }
    }

    private static String extractDiagnosticHints(File root, String fullLog) {
        if (fullLog == null || fullLog.isBlank()) return "";
        StringBuilder hints = new StringBuilder();
        String[] lines = fullLog.split("\r?\n");
        int found = 0;
        Set<String> seen = new HashSet<>();
        KrSourceMap sourceMap = KrSourceMap.loadFromProject(root);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            DiagnosticParser.Item item = DiagnosticParser.parseLine(line);
            if (item != null && item.severity == DiagnosticParser.Severity.ERROR) {
                // Bei Rust-Fehler: Echte Fehlermeldung aus vorherigen Zeilen suchen
                String message = item.message;
                if ("Rust-Fehlerposition".equals(message)) {
                    for (int k = i - 1; k >= Math.max(0, i - 6); k--) {
                        String prev = lines[k].trim();
                        if (prev.startsWith("error:") || prev.startsWith("error[")) {
                            message = prev.replaceFirst("^error(?:\\[[^\\]]+\\])?:\\s*", "");
                            break;
                        }
                    }
                }

                // Suche nach Funktionsname und Code-Snippets in den nachfolgenden Zeilen
                String functionHint = null;
                String snippet = null;
                for (int j = i + 1; j < Math.min(lines.length, i + 25); j++) {
                    String sub = lines[j].trim();
                    if (functionHint == null) {
                        java.util.regex.Matcher mFn = java.util.regex.Pattern.compile("(?:fn|func)\\s+([a-zA-Z0-9_]+)").matcher(sub);
                        if (mFn.find()) {
                            functionHint = mFn.group(1);
                        }
                    }
                    if (sub.matches("^\\d+\\s*\\|\\s*.*$")) {
                        String snipCandidate = sub.substring(sub.indexOf('|') + 1).trim();
                        if (snipCandidate.length() > 3 && !snipCandidate.equals("}") && !snipCandidate.equals("{")) {
                            boolean isMarked = false;
                            for (int m = j + 1; m < Math.min(lines.length, j + 3); m++) {
                                String mark = lines[m].trim();
                                if (mark.contains("delimiter") || mark.contains("closed") || mark.contains("^") || mark.contains("-")) {
                                    isMarked = true;
                                    break;
                                }
                            }
                            if (isMarked || snippet == null) {
                                snippet = snipCandidate;
                            }
                        }
                    }
                }

                // Löse Datei und Position über SourceMap auf
                KrSourceMap.ResolvedLocation loc = sourceMap != null ? sourceMap.resolveLocation(item.file, item.line, functionHint) : null;
                String srcFile = loc != null ? loc.originalFile : findSourceForGeneratedFile(root, item.file, functionHint);
                int targetLine = loc != null ? loc.originalLine : item.line;

                String key = srcFile + ":" + targetLine;
                if (!seen.add(key)) continue;

                File realFile = new File(root, srcFile);
                hints.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                hints.append("🔴 COMPILERFEHLER #").append(++found).append(" in Quelldatei: '").append(srcFile).append("'\n");
                hints.append("Fehlermeldung: ").append(message);
                if (functionHint != null) hints.append(" (Funktion: ").append(functionHint).append(")");
                hints.append("\n");

                if (realFile.isFile()) {
                    try {
                        List<String> srcLines = Files.readAllLines(realFile.toPath(), StandardCharsets.UTF_8);
                        int matchedLine = -1;
                        if (snippet != null && !snippet.isEmpty()) {
                            for (int l = 0; l < srcLines.size(); l++) {
                                String srcTrimmed = srcLines.get(l).trim();
                                if (!srcTrimmed.isEmpty() && srcTrimmed.length() > 5 && (srcLines.get(l).contains(snippet) || snippet.contains(srcTrimmed))) {
                                    matchedLine = l + 1;
                                    break;
                                }
                            }
                        }
                        if (matchedLine < 0) {
                            matchedLine = Math.max(1, Math.min(targetLine, srcLines.size()));
                        }

                        int start = Math.max(1, matchedLine - 3);
                        int end = Math.min(srcLines.size(), matchedLine + 3);
                        hints.append("Betroffener Code in '").append(srcFile).append("' (Zeilen ").append(start).append(" bis ").append(end).append("):\n");
                        for (int l = start; l <= end; l++) {
                            String prefix = (l == matchedLine) ? "👉 " : "   ";
                            hints.append(String.format(Locale.ROOT, "%s%4d | %s\n", prefix, l, srcLines.get(l - 1)));
                        }

                        String badLine = srcLines.get(matchedLine - 1).trim();
                        String suggestedReplace = "<korrigierter Code>";
                        if (message.toLowerCase(Locale.ROOT).contains("delimiter") || (badLine.contains("{") && !badLine.contains("}"))) {
                            suggestedReplace = badLine + " }";
                        }

                        hints.append("\n👉 ANWEISUNG ZUR BEHEBUNG:\n");
                        hints.append("Führe die Aktion 'replace' aus, um diese Zeile zu korrigieren:\n");
                        hints.append("{\n");
                        hints.append("  \"action\": \"replace\",\n");
                        hints.append("  \"path\": \"").append(srcFile).append("\",\n");
                        hints.append("  \"search\": \"").append(badLine.replace("\"", "\\\"")).append("\",\n");
                        hints.append("  \"replace\": \"").append(suggestedReplace.replace("\"", "\\\"")).append("\",\n");
                        hints.append("  \"message\": \"Syntaxfehler in ").append(srcFile).append(" behoben\"\n");
                        hints.append("}\n");
                    } catch (Exception ignored) {}
                }
                if (found >= 2) break;
            }
        }
        return hints.toString();
    }

    private static boolean isReadOnlyTask(String task) {
        if (task == null || task.isBlank()) return false;
        String lower = task.toLowerCase(Locale.ROOT);
        if (lower.contains("kompilier") || lower.contains("compile") || lower.contains("build") ||
                lower.contains("beheb") || lower.contains("fix") || lower.contains("reparier") ||
                lower.contains("änder") || lower.contains("modifizier") || lower.contains("schreib") ||
                lower.contains("erstell") || lower.contains("neu") || lower.contains("implementier") ||
                lower.contains("hinzufüg")) {
            return false;
        }
        return lower.contains("analys") || lower.contains("erklär") || lower.contains("untersuch") ||
                lower.contains("prüf") || lower.contains("zeig") || lower.contains("was macht") ||
                lower.contains("wie funktioniert") || lower.contains("beschreib") || lower.contains("review") ||
                lower.contains("überblick") || lower.contains("struktur") || lower.contains("info") ||
                lower.contains("zusammenfass") || lower.contains("welche") || lower.contains("lies");
    }
}
