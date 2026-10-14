package kr.compile;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BuildManager {
    public interface Listener {
        void onLogLine(String line);
        void onStatusChanged(String status, boolean isBuilding);
        void onBuildFinished(boolean success, String summary);
    }

    private static final BuildManager INSTANCE = new BuildManager();
    public static BuildManager getInstance() { return INSTANCE; }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<String> logBuffer = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean isBuilding = false;
    private volatile String currentStatus = "Bereit";
    private volatile CompilerEngine.Target currentTarget = CompilerEngine.Target.ANDROID;
    private volatile boolean onlineDependencies = true;
    private volatile CompilerEngine activeEngine = null;
    private volatile String lastResult = null;
    private volatile Boolean lastSuccess = null;

    private BuildManager() {}

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isBuilding() { return isBuilding; }
    public String getCurrentStatus() { return currentStatus; }
    public CompilerEngine.Target getCurrentTarget() { return currentTarget; }
    public void setCurrentTarget(CompilerEngine.Target target) { this.currentTarget = target; }
    public boolean isOnlineDependencies() { return onlineDependencies; }
    public void setOnlineDependencies(boolean online) { this.onlineDependencies = online; }
    public List<String> getLogBuffer() { return new ArrayList<>(logBuffer); }
    public String getLastResult() { return lastResult; }
    public Boolean getLastSuccess() { return lastSuccess; }

    public String getFullLog() {
        synchronized (logBuffer) {
            StringBuilder sb = new StringBuilder();
            for (String line : logBuffer) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    public void clearLog() {
        logBuffer.clear();
    }

    public synchronized void startBuild(Context context, Uri projectUri, File localProjectDir,
                                        CompilerEngine.Target target, boolean allowOnline) {
        if (isBuilding) return;
        this.currentTarget = target;
        this.onlineDependencies = allowOnline;
        this.isBuilding = true;
        this.lastResult = null;
        this.lastSuccess = null;
        this.currentStatus = "Compiler wird vorbereitet …";
        logBuffer.clear();

        notifyStatus(currentStatus, true);

        executor.execute(() -> {
            activeEngine = new CompilerEngine(context, projectUri, localProjectDir, target, allowOnline, line -> {
                logBuffer.add(line);
                mainHandler.post(() -> {
                    for (Listener l : listeners) {
                        l.onLogLine(line);
                    }
                });
            });

            String result;
            boolean success = false;
            try {
                result = activeEngine.build();
                success = true;
            } catch (Exception e) {
                result = "FEHLER: " + (e.getMessage() != null ? e.getMessage() : e.toString());
                success = false;
            } finally {
                activeEngine = null;
            }

            final boolean finalSuccess = success;
            final String finalResult = result;
            mainHandler.post(() -> {
                isBuilding = false;
                currentStatus = finalResult;
                lastResult = finalResult;
                lastSuccess = finalSuccess;
                for (Listener l : listeners) {
                    l.onStatusChanged(finalResult, false);
                    l.onBuildFinished(finalSuccess, finalResult);
                }
            });
        });
    }

    public void cancelBuild() {
        if (!isBuilding) return;
        CompilerEngine engine = activeEngine;
        if (engine != null) {
            engine.cancel();
        }
    }

    private void notifyStatus(String status, boolean building) {
        mainHandler.post(() -> {
            for (Listener l : listeners) {
                l.onStatusChanged(status, building);
            }
        });
    }
}
