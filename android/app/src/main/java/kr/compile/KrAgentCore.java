package kr.compile;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
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
 * Der Compilerkern (CompilerEngine, KrPipeline, Sysroots) bleibt unantastbar.
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
            } catch (Exception e) {
                emit("❌ Agentenfehler: " + e.getMessage());
            } finally {
                running = false;
                finish(cancelled.get() ? "Vom Benutzer abgebrochen" : "Auftrag beendet");
            }
        });
    }

    private void execute(String task, Settings settings) throws Exception {
        if (!settings.endpoint().startsWith("https://")
                && !settings.endpoint().startsWith("http://127.0.0.1:")
                && !settings.endpoint().startsWith("http://localhost:")) {
            throw new IOException("Für entfernte Modellserver ist HTTPS erforderlich.");
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
        JSONArray history = new JSONArray();
        JSONObject initial = new JSONObject();
        initial.put("role", "system");
        initial.put("content",
                "Du bist der autonome Programmieragent der KR Compiler Android IDE. Du entwickelst und debuggst Projekte in der KR Unified Language (.kr), C++20, Rust und Go.\n\n" +
                "KOMMUNIKATIONSREGELN:\n" +
                "1. Antworte AUSSCHLIESSLICH als gültiges JSON-Objekt mit genau diesen Schlüsseln: {action, path, content, message}\n" +
                "   action MUSS eines der folgenden Schlüsselwörter sein: list, read, write, build, done.\n" +
                "2. Verwende immer relative Pfade ohne führenden Schrägstrich.\n" +
                "3. ARBEITSABLAUF:\n" +
                "   - Lies IMMER zuerst die relevanten Projektdateien mit 'read', bevor du Änderungen vornimmst.\n" +
                "   - Nimm die gewünschten Codeanpassungen mit 'write' vor. 'write' ersetzt den kompletten Textinhalt der Zieldatei.\n" +
                "   - Baue erst mit 'build', wenn du den Code tatsächlich angepasst hast oder einen gemeldeten Compilerfehler reproduzieren willst. Ein unverändertes Projekt ohne Grund zu bauen, löst die Aufgabe nicht!\n" +
                "   - Analysiere Compilerfehler nach dem Build und behebe sie mit 'write'.\n" +
                "   - Wenn alle Ziele erreicht sind, beende mit 'done' und erkläre im Feld 'message' zusammenfassend, was geändert und verifiziert wurde.\n" +
                "4. Begründe jede Aktion im Feld 'message' kurz und verständlich auf Deutsch.");
        history.put(initial);

        String initialPrompt = "AUFTRAG:\n" + task + "\n\n" +
                "PROJEKT: " + projectName + "\n" +
                "DATEIEN IM PROJEKT:\n" + fileListing + "\n\n" +
                "Bitte starte mit der Analyse der relevanten Dateien.";
        history.put(new JSONObject().put("role", "user").put("content", initialPrompt));

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
            switch (kind) {
                case "list" -> output = listFiles(workspace, path);
                case "read" -> output = read(workspace, path);
                case "write" -> {
                    String content = action.optString("content", "");
                    File target = safeFile(workspace, path);
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
                        // IDE über Änderungen informieren
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
                    CompilerEngine.Target target = BuildManager.getInstance().getCurrentTarget();
                    boolean ok = requireApproval("Projekt kompilieren",
                            "Zielplattform: " + target + "\nProjekt: " + projectName + "\n\nSoll der Compilerlauf jetzt gestartet werden?");
                    if (!ok) {
                        output = "Kompilierung wurde vom Benutzer abgelehnt.";
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
                default -> output = "Unbekannte Aktion '" + kind + "'. Erlaubt sind: list, read, write, build, done";
            }

            emit("📄 Ergebnis: " + (output.length() > 1500 ? output.substring(0, 1500) + " … [gekürzt]" : output));
            history.put(new JSONObject().put("role", "assistant").put("content", action.toString()));
            history.put(new JSONObject().put("role", "user").put("content",
                    "Werkzeugergebnis:\n" + truncate(output, 8000) + "\n\nFahre mit der nächsten logischen Aktion fort."));
        }

        emit("⚠️ Maximale Schrittzahl (" + budget + ") erreicht oder abgebrochen.");
    }

    private JSONObject askModel(Settings s, JSONArray messages) throws Exception {
        URL url = new URL(s.endpoint());
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(30000);
        c.setReadTimeout(180000);
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
            if (text.startsWith("```")) {
                text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            }
            return new JSONObject(text);
        } finally {
            c.disconnect();
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
            if (latch.await(250, TimeUnit.MILLISECONDS)) {
                return approved.get();
            }
        }
        return false;
    }

    private static File safeFile(File root, String relative) throws IOException {
        if (relative == null || relative.isBlank() || relative.startsWith("/") || relative.contains("\\") || relative.contains(":")) {
            throw new IOException("Ungültiger relativer Pfad: " + relative);
        }
        File target = new File(root, relative).getCanonicalFile();
        if (!target.toPath().startsWith(root.toPath()) || target.equals(root)) {
            throw new IOException("Pfad außerhalb des Projekts: " + relative);
        }
        String normalized = relative.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if (Set.of(".git", "KR_BUILD", ".gradle", ".kragent", "..", ".").contains(segment)) {
                throw new IOException("Geschützter Pfad: " + segment);
            }
        }
        return target;
    }

    private String listFiles(File root, String relative) throws IOException {
        File dir = relative.isBlank() ? root : safeFile(root, relative);
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
        File file = safeFile(root, relative);
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
            return (ok ? "BUILD OK\n" : "BUILD FEHLER\n") +
                    truncate(summary[0] == null ? "" : summary[0], 3000) + "\n" +
                    truncate(manager.getFullLog(), 9000);
        } finally {
            manager.removeListener(listener);
        }
    }
}
