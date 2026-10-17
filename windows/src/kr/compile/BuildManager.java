package kr.compile;

import java.io.File;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BuildManager {
    public interface Listener {
        void onLogLine(String line);
        void onStatusChanged(String status, boolean building);
        void onBuildFinished(boolean ok, String result);
    }

    private static BuildManager instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final StringBuilder fullLog = new StringBuilder();

    private volatile boolean building = false;
    private volatile CompilerEngine activeEngine;
    private volatile CompilerEngine.Target currentTarget = CompilerEngine.Target.WINDOWS;
    private volatile boolean onlineDependencies = false;

    public static synchronized BuildManager getInstance() {
        if (instance == null) instance = new BuildManager();
        return instance;
    }

    private BuildManager() {}

    public void addListener(Listener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public boolean isBuilding() { return building; }
    public CompilerEngine.Target getCurrentTarget() { return currentTarget; }
    public void setCurrentTarget(CompilerEngine.Target target) { if (target != null) this.currentTarget = target; }

    public boolean isOnlineDependencies() { return onlineDependencies; }
    public void setOnlineDependencies(boolean online) { this.onlineDependencies = online; }

    public synchronized String getFullLog() { return fullLog.toString(); }
    public synchronized void clearLog() { fullLog.setLength(0); }

    public synchronized void startBuild(File projectDir, CompilerEngine.Target target, boolean onlineDeps) {
        if (building) return;
        if (projectDir == null || !projectDir.isDirectory()) {
            notifyFinished(false, "Kein Projektordner geöffnet.");
            return;
        }

        building = true;
        currentTarget = target != null ? target : CompilerEngine.Target.WINDOWS;
        onlineDependencies = onlineDeps;
        clearLog();

        notifyStatus("Kompiliere für " + currentTarget.getDisplayName() + " …", true);

        executor.execute(() -> {
            boolean success = false;
            String resultMessage = "";
            try {
                CompilerEngine.Logger logger = line -> {
                    synchronized (fullLog) {
                        fullLog.append(line).append('\n');
                    }
                    for (Listener l : listeners) l.onLogLine(line);
                };

                activeEngine = new CompilerEngine(projectDir, currentTarget, onlineDependencies, logger);
                File artifact = activeEngine.build();
                success = true;
                resultMessage = "Build erfolgreich: " + artifact.getName();
                logger.line("✅ " + resultMessage);
            } catch (InterruptedException e) {
                resultMessage = "Build vom Benutzer abgebrochen.";
                logDirect("⚠️ " + resultMessage);
            } catch (Exception e) {
                resultMessage = "Fehler: " + e.getMessage();
                logDirect("❌ " + resultMessage);
            } finally {
                building = false;
                activeEngine = null;
                notifyStatus("Bereit", false);
                notifyFinished(success, resultMessage);
                ProjectManager.getInstance().refreshTree();
            }
        });
    }

    public void cancelBuild() {
        if (activeEngine != null) {
            activeEngine.cancel();
        }
    }

    private void logDirect(String line) {
        synchronized (fullLog) {
            fullLog.append(line).append('\n');
        }
        for (Listener l : listeners) l.onLogLine(line);
    }

    private void notifyStatus(String status, boolean isBuilding) {
        for (Listener l : listeners) l.onStatusChanged(status, isBuilding);
    }

    private void notifyFinished(boolean ok, String result) {
        for (Listener l : listeners) l.onBuildFinished(ok, result);
    }
}
