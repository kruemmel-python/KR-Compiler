package kr.compile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional agent layer: it never modifies CompilerEngine, KrLexer, or KrPipeline. */
public final class KrAgentCore {
    public interface Events { void onEvent(String text); void onFinished(String status); }
    public interface Approval { void request(String title, String detail, java.util.function.Consumer<Boolean> callback); }
    public static final class Settings {
        private final String endpoint, model, token;
        private final int maxSteps;
        public Settings(String endpoint, String model, String token, int maxSteps) {
            this.endpoint = endpoint; this.model = model; this.token = token; this.maxSteps = maxSteps;
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

    public KrAgentCore(Context context) {
        this.context = context.getApplicationContext();
        journal = new File(context.getFilesDir(), "kr_agent_audit.jsonl");
    }
    public boolean isRunning() { return running; }
    public void cancel() { cancelled.set(true); BuildManager.getInstance().cancelBuild(); }
    private void emit(Events events, String message) {
        main.post(() -> events.onEvent(message));
        try (FileOutputStream out = new FileOutputStream(journal, true)) {
            JSONObject line = new JSONObject();
            line.put("time", System.currentTimeMillis()); line.put("message", message);
            out.write((line.toString()+"\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }
    private void finish(Events events, String message) { main.post(() -> events.onFinished(message)); }
    public synchronized void start(String task, Settings settings, Approval approval, Events events) {
        if (running) { finish(events, "Ein Agentenauftrag läuft bereits."); return; }
        if (task == null || task.isBlank()) { finish(events, "Auftrag fehlt."); return; }
        running = true; cancelled.set(false);
        worker.execute(() -> {
            try { execute(task, settings, approval, events); }
            catch (Exception e) { emit(events, "Agentenfehler: " + e.getMessage()); }
            finally { running = false; finish(events, cancelled.get() ? "Abgebrochen" : "Auftrag beendet"); }
        });
    }
    private void execute(String task, Settings settings, Approval approval, Events events) throws Exception {
        if (!settings.endpoint().startsWith("https://") && !settings.endpoint().startsWith("http://127.0.0.1:") && !settings.endpoint().startsWith("http://localhost:"))
            throw new IOException("Für entfernte Modellserver ist HTTPS erforderlich.");
        ProjectManager projects = ProjectManager.getInstance();
        File root = projects.getActiveLocalDir();
        if (root == null || !root.isDirectory()) throw new IOException("Agentenmodus benötigt ein geöffnetes lokales Projekt. SAF-Projekte noch nicht unterstützt.");
        root = root.getCanonicalFile();
        final File workspace = root;
        emit(events, "Projekt: " + workspace.getName());
        JSONArray history = new JSONArray();
        JSONObject initial = new JSONObject(); initial.put("role", "system");
        initial.put("content", "Du bist ein Android KR-IDE Programmieragent. KR kann C++20/Rust/Go in .kr Dateien kombinieren. Arbeite mit kurzen, präzisen Aktionen. Antworte nur als JSON-Objekt mit {action,path,content,message}, action ist eines von list,read,write,build,done. write ersetzt den vollständigen Inhalt einer Textdatei. Verwende nur relative Pfade. Pro Schritt genau eine Aktion. Prüfe Build-Ergebnisse und korrigiere Fehler. Du darfst keinerlei System-, Geheimnis- oder fremde Projektdateien anfordern. Erkläre abschließend das Ergebnis."); history.put(initial);
        history.put(new JSONObject().put("role","user").put("content",task));
        int budget = Math.min(40, Math.max(1, settings.maxSteps()));
        for (int step = 0; step < budget && !cancelled.get(); ++step) {
            JSONObject action = askModel(settings, history);
            String kind = action.optString("action", "done");
            String path = action.optString("path", "");
            String output;
            emit(events, "Schritt " + (step+1) + ": " + kind + " " + path);
            switch (kind) {
                case "list" -> output = listFiles(workspace, path);
                case "read" -> output = read(workspace, path);
                case "write" -> {
                    String content = action.optString("content", "");
                    File target = safeFile(workspace,path);
                    if (target.exists() && target.length() > 1024*1024) throw new IOException("Datei zu groß: " + path);
                    boolean ok = requireApproval(approval, "Datei ändern", target.getAbsolutePath()+"\n"+preview(content));
                    if (!ok) output = "Änderung wurde nicht genehmigt.";
                    else { writeAtomically(target,content); output = "Datei geschrieben: "+path; }
                }
                case "build" -> {
                    boolean ok = requireApproval(approval,"Projekt kompilieren", "Ziel: "+BuildManager.getInstance().getCurrentTarget());
                    output = ok ? build(workspace,events) : "Build nicht genehmigt.";
                }
                case "done" -> { emit(events, action.optString("message","Abgeschlossen")); return; }
                default -> output = "Unbekannte Aktion. Erlaubt: list,read,write,build,done";
            }
            emit(events, output.length() > 3000 ? output.substring(0,3000)+" ..." : output);
            history.put(new JSONObject().put("role","assistant").put("content",action.toString()));
            history.put(new JSONObject().put("role","user").put("content", "Werkzeugergebnis:\n"+truncate(output,10000)+"\nFahre mit der nächsten Aktion fort."));
        }
        emit(events,"Maximale Schrittzahl erreicht oder abgebrochen.");
    }
    private JSONObject askModel(Settings s, JSONArray messages) throws Exception {
        URL url = new URL(s.endpoint());
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setConnectTimeout(30000); c.setReadTimeout(180000);
        c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        if (!s.token().isEmpty()) c.setRequestProperty("Authorization","Bearer "+s.token());
        JSONObject body = new JSONObject().put("model",s.model()).put("messages",messages).put("temperature",0.1);
        try {
            try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            if (stream == null) throw new IOException("Modellserver: HTTP "+code);
            String payload;
            try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (output.size() + count > 1024 * 1024) throw new IOException("Modellantwort über 1 MiB");
                    output.write(buffer, 0, count);
                }
                payload = output.toString(StandardCharsets.UTF_8.name());
            }
            if (code < 200 || code >= 300) throw new IOException("Modellserver HTTP "+code+": "+truncate(payload,300));
            JSONObject response = new JSONObject(payload);
            String text = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
            if (text.startsWith("```")) { text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""); }
            return new JSONObject(text);
        } finally { c.disconnect(); }
    }
    private static String truncate(String s,int max){return s.length()>max ? s.substring(0,max)+" [abgeschnitten]" : s;}
    private static String preview(String text){return truncate(text,1200);}
    private boolean requireApproval(Approval approval,String title,String detail) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean approved = new AtomicBoolean();
        main.post(() -> approval.request(title, detail, accepted -> { approved.set(accepted); latch.countDown(); }));
        while (!cancelled.get()) {
            if (latch.await(250,TimeUnit.MILLISECONDS)) return approved.get();
        }
        return false;
    }
    private static File safeFile(File root,String relative) throws IOException {
        if (relative == null || relative.isBlank() || relative.startsWith("/") || relative.contains("\\") || relative.contains(":")) throw new IOException("Ungültiger relativer Pfad");
        File target = new File(root,relative).getCanonicalFile();
        if (!target.toPath().startsWith(root.toPath()) || target.equals(root)) throw new IOException("Pfad außerhalb des Projekts");
        String normalized = relative.replace('\\','/');
        for (String segment:normalized.split("/")) if (Set.of(".git","KR_BUILD",".gradle",".kragent","..",".").contains(segment)) throw new IOException("Geschützter Pfad");
        return target;
    }
    private String listFiles(File root,String relative) throws IOException {
        File dir = relative.isBlank() ? root : safeFile(root,relative);
        if (!dir.isDirectory()) return "Kein Ordner: "+relative;
        File[] files = dir.listFiles(); if (files == null) return "Ordner nicht lesbar";
        Arrays.sort(files,Comparator.comparing(File::getName));
        StringBuilder b=new StringBuilder(); int count=0;
        for(File f:files){ if(f.getName().startsWith(".") || f.getName().equals("KR_BUILD"))continue;
            b.append(f.isDirectory()?"[D] ":"[F] ").append(f.getName()).append('\n');if(++count>=250)break;}
        return b.toString();
    }
    private String read(File root,String relative) throws IOException {
        File file=safeFile(root,relative);
        if(!file.isFile() || file.length()>256*1024)throw new IOException("Nur Textdateien bis 256 KiB lesbar");
        if(ProjectManager.isBinaryFile(file.getName()))throw new IOException("Binärdatei nicht erlaubt");
        return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
    }
    private void writeAtomically(File file,String content) throws IOException {
        if(ProjectManager.isBinaryFile(file.getName()))throw new IOException("Binärdatei nicht erlaubt");
        if(content.length()>1024*1024)throw new IOException("Inhalt größer als 1 MiB");
        Files.createDirectories(file.getParentFile().toPath());
        Path temp=Files.createTempFile(file.getParentFile().toPath(),".kr_agent_",".tmp");
        try {
            Files.write(temp,content.getBytes(StandardCharsets.UTF_8));
            try { Files.move(temp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        } finally { Files.deleteIfExists(temp); }
    }
    private String build(File root,Events events) throws InterruptedException {
        BuildManager manager=BuildManager.getInstance();
        if(manager.isBuilding())return "Bereits laufender Build. Bitte warten.";
        CountDownLatch latch=new CountDownLatch(1);
        AtomicBoolean success=new AtomicBoolean();
        String[] summary=new String[1];
        BuildManager.Listener listener=new BuildManager.Listener(){
            public void onLogLine(String line){}
            public void onStatusChanged(String status,boolean building){}
            public void onBuildFinished(boolean ok,String result){success.set(ok);summary[0]=result;latch.countDown();}
        };
        manager.addListener(listener);
        try {
            main.post(() -> manager.startBuild(context,null,root,manager.getCurrentTarget(),manager.isOnlineDependencies()));
            while(!cancelled.get()){if(latch.await(250,TimeUnit.MILLISECONDS))break;}
            if(cancelled.get())return "Build abgebrochen";
            return (success.get()?"BUILD OK\n":"BUILD FEHLER\n")+truncate(summary[0]==null?"":summary[0],3000)+"\n"+truncate(manager.getFullLog(),9000);
        } finally {manager.removeListener(listener);}
    }
}
