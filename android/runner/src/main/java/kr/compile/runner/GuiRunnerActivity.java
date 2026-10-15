package kr.compile.runner;

import android.os.Bundle;

import org.libsdl.app.SDLActivity;

import java.io.File;

/** SDL3 host for GUI programs compiled by KR IDE. */
public final class GuiRunnerActivity extends SDLActivity {
    static {
        System.loadLibrary("kr_runner");
    }

    private static native boolean setWorkingDirectory(String path);

    @Override public void onCreate(Bundle state) {
        try {
            File project = RunnerFiles.prepareProjectFiles(this);
            if (!setWorkingDirectory(project.getPath())) {
                throw new IllegalStateException("Arbeitsverzeichnis kann nicht gesetzt werden.");
            }
        } catch (Exception error) {
            throw new IllegalStateException("Projektressourcen können nicht vorbereitet werden.", error);
        }
        super.onCreate(state);
    }

    @Override protected String[] getLibraries() {
        File nativeDir = new File(getApplicationInfo().nativeLibraryDir);
        File[] libs = nativeDir.listFiles();
        java.util.List<String> list = new java.util.ArrayList<>();
        if (libs != null) {
            for (File f : libs) {
                String name = f.getName();
                if (name.startsWith("lib") && name.endsWith(".so")) {
                    String base = name.substring(3, name.length() - 3);
                    if (!base.equals("kr_runner") && !base.equals("user") && !base.equals("c++_shared")) {
                        list.add(base);
                    }
                }
            }
        }
        list.add("user");
        return list.toArray(new String[0]);
    }

    @Override protected String getMainFunction() {
        return "SDL_main";
    }
}
