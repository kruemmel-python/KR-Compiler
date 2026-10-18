package kr.compile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.SwingUtilities;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
            this.token = token != null ? token : "";
            this.maxSteps = maxSteps > 0 ? maxSteps : 30;
        }

        public String endpoint() { return endpoint; }
        public String model() { return model; }
        public String token() { return token; }
        public int maxSteps() { return maxSteps; }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile boolean running;

    private final StringBuilder logHistory = new StringBuilder();
    private final List<Events> listeners = new CopyOnWriteArrayList<>();
    private volatile StatusListener statusListener;
    private volatile Approval approvalHandler;
    private volatile String currentStatus = "Bereit";
    private volatile String lastTask = "";

    public KrAgentCore() {}

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
        File root = projects.getActiveProjectDir();
        if (root == null || !root.isDirectory()) {
            throw new IOException("Kein Projekt geöffnet. Bitte zuerst ein Projekt öffnen.");
        }

        String fileName = "kr_agent_log.txt";
        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String fullExport = "=== KR Autonomous Agent Protokoll (Windows Desktop) ===\n"
                + "Projekt: " + projects.getActiveProjectName() + "\n"
                + "Exportiert am: " + dateStr + "\n"
                + "=====================================================\n\n"
                + logContent;

        File target = new File(root, fileName);
        try (FileOutputStream fos = new FileOutputStream(target)) {
            fos.write(fullExport.getBytes(StandardCharsets.UTF_8));
        }

        DocumentManager.getInstance().reloadIfOpen(fileName, fullExport);
        projects.refreshTree();
        return target.getAbsolutePath();
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
        updateStatus("Abbruch angefordert …");
        emit("⏹️ Abbruch durch Benutzer angefordert.");
    }

    private void updateStatus(String statusText) {
        this.currentStatus = statusText;
        StatusListener sl = this.statusListener;
        if (sl != null) {
            SwingUtilities.invokeLater(() -> sl.onStatus(statusText, running));
        }
    }

    private void emit(String message) {
        synchronized (logHistory) {
            logHistory.append(message).append('\n');
        }
        SwingUtilities.invokeLater(() -> {
            for (Events l : listeners) {
                l.onEvent(message);
            }
        });

        // Revisionssicheres Audit-Log im Projektordner
        File root = ProjectManager.getInstance().getActiveProjectDir();
        if (root != null && root.isDirectory()) {
            File journal = new File(root, "kr_agent_audit.jsonl");
            try (FileOutputStream out = new FileOutputStream(journal, true)) {
                JSONObject line = new JSONObject();
                line.put("time", System.currentTimeMillis());
                line.put("message", message);
                out.write((line.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }
    }

    private void finish(String message) {
        running = false;
        updateStatus(message);
        synchronized (logHistory) {
            logHistory.append("\n=== ").append(message).append(" ===\n");
        }
        SwingUtilities.invokeLater(() -> {
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
                    emit("❌ Timeout beim Verbindungsaufbau zu " + settings.endpoint() + " (nach 60s).\n"
                            + "👉 Mögliche Ursachen:\n"
                            + "  1. Ist der Server online und die URL/Port korrekt?\n"
                            + "  2. Bei lokalem Server (Ollama/LM Studio): Prüfen Sie, ob der Dienst gestartet ist (z. B. http://127.0.0.1:11434).\n"
                            + "  3. Prüfen Sie die Firewall auf dem Rechner.");
                } else {
                    emit("❌ Timeout beim Warten auf die Modellantwort von " + settings.endpoint() + " (nach 300s).\n"
                            + "👉 Das Modell (" + settings.model() + ") hat die maximale Antwortzeit überschritten.");
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
        ProjectManager projects = ProjectManager.getInstance();
        File workspace = projects.getActiveProjectDir();
        String projectName = projects.getActiveProjectName();

        if (workspace == null || !workspace.isDirectory()) {
            throw new IOException("Kein Projekt geöffnet. Bitte zuerst einen Projektordner öffnen oder erstellen.");
        }

        emit("📂 Projekt geöffnet: " + projectName + " (" + workspace.getAbsolutePath() + ")");

        // 1. Vorhandene Dateien scannen
        String fileListing = listFiles(workspace, "");
        emit("📋 Projektstruktur:\n" + fileListing);

        // 2. Chat-Verlauf aufbauen
        boolean isReadOnly = isReadOnlyTask(task);

        JSONArray history = new JSONArray();
        JSONObject initial = new JSONObject();
        initial.put("role", "system");

        StringBuilder sys = new StringBuilder();
        sys.append("Du bist der autonome Programmieragent der KR Compiler Windows IDE. Du entwickelst und analysierst Projekte in der KR Unified Language (.kr), C++20, Rust und Go.\n\n")
           .append("KOMMUNIKATIONSREGELN:\n")
           .append("1. Antworte AUSSCHLIESSLICH als gültiges JSON-Objekt mit genau diesen Schlüsseln: {\"action\": \"...\", \"path\": \"...\", \"content\": \"...\", \"message\": \"...\"}\n")
           .append("   action MUSS eines der folgenden Schlüsselwörter sein: list, read, write, build, done.\n")
           .append("2. WERKZEUG-REGELN:\n")
           .append("   - 'list': Listet Verzeichnisse auf (z. B. path=\"\" für das Hauptverzeichnis, path=\"app\" für Unterordner).\n")
           .append("   - 'read': Liest den Inhalt einer Textdatei (z. B. path=\"nexus.krproject\", path=\"main.kr\", path=\"README.md\"). Verwende 'read' NUR für Dateien, NIEMALS für Ordner oder '.'!\n")
           .append("   - 'write': Ersetzt den Inhalt einer Zieldatei (benötigt 'path' und 'content').\n")
           .append("   - 'build': Führt den Compilerlauf für die aktive Zielplattform aus.\n")
           .append("   - 'done': Beendet den Auftrag erfolgreich (Abschlussbericht im Feld 'message').\n\n");

        if (isReadOnly) {
            sys.append("WICHTIGE REGEL FÜR DIESEN AUFTRAG (REINE ANALYSE):\n")
               .append("• Der Benutzer möchte NUR eine Analyse oder Erklärung des Projekts ('").append(task).append("').\n")
               .append("• Führe KEINEN Build ('build') und KEINE Dateiänderungen ('write') aus!\n")
               .append("• Lies die relevanten Dateien mit 'read' und 'list'.\n")
               .append("• Sobald du die Struktur und den Code verstanden hast, beende direkt mit 'done' und präsentiere deine vollständige, strukturierte Analyse im Feld 'message'!\n\n");
        } else {
            sys.append("ARBEITSABLAUF:\n")
               .append("• Lies IMMER zuerst die relevanten Projektdateien (z. B. 'nexus.krproject', 'main.kr') mit 'read'.\n")
               .append("• Nimm die gewünschten Codeanpassungen mit 'write' vor.\n")
               .append("• Baue mit 'build', wenn du den Code angepasst hast oder Fehler testen willst.\n")
               .append("• Analysiere Compilerfehler nach dem Build und behebe sie mit 'write'.\n")
               .append("• Wenn alle Ziele erreicht sind, beende mit 'done'.\n\n");
        }
        sys.append("Begründe jede Aktion im Feld 'message' kurz und verständlich auf Deutsch.");
        initial.put("content", sys.toString());
        history.put(initial);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("AUFTRAG:\n").append(task).append("\n\n");
        promptBuilder.append("PROJEKT: ").append(projectName).append("\n");
        promptBuilder.append("DATEIEN IM PROJEKT:\n").append(fileListing).append("\n\n");
        if (isReadOnly) {
            promptBuilder.append("HINWEIS: Dies ist ein reiner Analyseauftrag. Lies die wichtigsten Projektdateien (z. B. 'nexus.krproject', 'main.kr', 'README.md') mit 'read'. Starte KEINEN Build ('build') und ändere keine Dateien! Fasse nach der Analyse alles mit 'done' im Feld 'message' zusammen.\n\n");
        }
        promptBuilder.append("Bitte starte deine Analyse mit 'read' (z. B. path=\"nexus.krproject\" oder path=\"main.kr\").");
        history.put(new JSONObject().put("role", "user").put("content", promptBuilder.toString()));

        int budget = Math.min(40, Math.max(1, settings.maxSteps()));

        for (int step = 0; step < budget && !cancelled.get(); ++step) {
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
                    case "read" -> output = read(workspace, path);
                    case "write" -> {
                        if (isReadOnly) {
                            output = "Hinweis: Der Benutzer hat keine Dateiänderung angefordert (Auftrag: '" + task + "'). Bitte ändere keine Dateien, sondern analysiere die vorhandenen Dateien und beende mit 'done'.";
                            emit("ℹ️ Dateiänderung übersprungen (reiner Analyseauftrag).");
                            break;
                        }
                        String content = action.optString("content", "");
                        File target = safeFile(workspace, path);
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
                            final String finalPath = path;
                            final String finalContent = content;
                            SwingUtilities.invokeLater(() -> {
                                projects.refreshTree();
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
                            output = build(workspace, target);
                        }
                    }
                    case "done" -> {
                        String finalMessage = action.optString("message", "Auftrag erfolgreich abgeschlossen.");
                        emit("🏁 ERGEBNIS:\n" + finalMessage);
                        return;
                    }
                    default -> output = "Unbekannte Aktion '" + kind + "'. Erlaubt sind: list, read, write, build, done";
                }
            } catch (Exception ex) {
                output = "Fehler bei Aktion '" + kind + "': " + ex.getMessage() + ". Bitte Parameter prüfen und nächsten Schritt ausführen.";
                emit("⚠️ " + output);
            }

            emit("📄 Ergebnis: " + (output.length() > 1500 ? output.substring(0, 1500) + " … [gekürzt]" : output));
            history.put(new JSONObject().put("role", "assistant").put("content", action.toString()));
            history.put(new JSONObject().put("role", "user").put("content",
                    "Werkzeugergebnis:\n" + truncate(output, 8000) + "\n\nFahre mit der nächsten logischen Aktion fort."));
        }

        emit("⚠️ Maximale Schrittzahl (" + budget + ") erreicht oder abgebrochen.");
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
        c.setConnectTimeout(60000);
        c.setReadTimeout(300000);
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
            String text = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
            return parseActionJson(text);
        } finally {
            c.disconnect();
        }
    }

    private static JSONObject parseActionJson(String raw) throws JSONException {
        if (raw == null || raw.isBlank()) {
            throw new JSONException("Leere Antwort vom Modell erhalten.");
        }
        String text = raw.trim();

        // 1. Entferne <think>...</think> Tags (z. B. DeepSeek / Qwen Modellen)
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

        // 3. Finde das äußerste JSON-Objekt { ... }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String jsonPart = text.substring(start, end + 1).trim();
            try {
                return new JSONObject(jsonPart);
            } catch (JSONException ignored) {}
        }

        // 4. Direkter Versuch
        try {
            return new JSONObject(text);
        } catch (JSONException e) {
            // 5. Fallback bei freier Textantwort: Behandle als 'done' mit Antworttext
            JSONObject fallback = new JSONObject();
            fallback.put("action", "done");
            fallback.put("path", "");
            fallback.put("message", text);
            return fallback;
        }
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

        SwingUtilities.invokeLater(() -> {
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

    private static File safeFile(File root, String relative) throws IOException {
        if (relative == null || relative.isBlank()) {
            return root.getCanonicalFile();
        }
        String clean = relative.trim().replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        while (clean.startsWith("./")) clean = clean.substring(2);
        if (clean.isEmpty() || clean.equals(".")) {
            return root.getCanonicalFile();
        }
        File target = new File(root, clean).getCanonicalFile();
        if (!target.toPath().startsWith(root.toPath())) {
            throw new IOException("Pfad außerhalb des Projekts: " + relative);
        }
        for (String segment : clean.split("/")) {
            if (Set.of(".git", "KR_BUILD", ".kragent", "..").contains(segment)) {
                throw new IOException("Geschützter Pfad: " + segment);
            }
        }
        return target;
    }

    private String listFiles(File root, String relative) throws IOException {
        File dir = (relative == null || relative.isBlank() || relative.equals(".") || relative.equals("./")) ? root : safeFile(root, relative);
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

    private String read(File root, String relative) throws IOException {
        if (relative == null || relative.isBlank() || relative.equals(".") || relative.equals("./")) {
            return "Hinweis: '" + relative + "' ist das Projektverzeichnis. Bitte gib einen Dateinamen an (z. B. 'main.kr', 'nexus.krproject' oder 'README.md').\nDateien im Projekt:\n" + listFiles(root, "");
        }
        File file = safeFile(root, relative);
        if (file.isDirectory()) {
            return "Hinweis: '" + relative + "' ist ein Ordner, keine Datei. Verwende 'list', um Ordnerinhalte aufzulisten, oder lies eine konkrete Datei im Ordner.\nInhalt von " + relative + ":\n" + listFiles(root, relative);
        }
        if (!file.isFile() || file.length() > 256 * 1024) {
            throw new IOException("Nur Textdateien bis 256 KiB lesbar: " + relative);
        }
        if (ProjectManager.isBinaryFile(file.getName())) {
            throw new IOException("Binärdatei nicht erlaubt: " + relative);
        }
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
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

    private String build(File root, CompilerEngine.Target target) throws InterruptedException {
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
            manager.startBuild(root, target, manager.isOnlineDependencies());

            while (!cancelled.get()) {
                if (latch.await(250, TimeUnit.MILLISECONDS)) break;
            }
            if (cancelled.get()) return "Build abgebrochen";

            boolean ok = success.get();
            emit(ok ? "✅ Build erfolgreich abgeschlossen." : "❌ Build mit Fehlern beendet.");
            return (ok ? "BUILD OK\n" : "BUILD FEHLER\n") +
                    truncate(summary[0] == null ? "" : summary[0], 3000) + "\n" +
                    truncate(manager.getFullLog(), 9000);
        } finally {
            manager.removeListener(listener);
        }
    }

    private static boolean isReadOnlyTask(String task) {
        if (task == null || task.isBlank()) return false;
        String lower = task.toLowerCase(Locale.ROOT);
        // Wenn der Nutzer explizit bauen, reparieren oder modifizieren will:
        if (lower.contains("kompilier") || lower.contains("compile") || lower.contains("build") ||
                lower.contains("beheb") || lower.contains("fix") || lower.contains("reparier") ||
                lower.contains("änder") || lower.contains("modifizier") || lower.contains("schreib") ||
                lower.contains("erstell") || lower.contains("neu") || lower.contains("implementier") ||
                lower.contains("hinzufüg")) {
            return false;
        }
        // Schlüsselwörter für reine Analyse / Fragen / Lese-Aufträge:
        return lower.contains("analys") || lower.contains("erklär") || lower.contains("untersuch") ||
                lower.contains("prüf") || lower.contains("zeig") || lower.contains("was macht") ||
                lower.contains("wie funktioniert") || lower.contains("beschreib") || lower.contains("review") ||
                lower.contains("überblick") || lower.contains("struktur") || lower.contains("info") ||
                lower.contains("zusammenfass") || lower.contains("welche") || lower.contains("lies");
    }
}
