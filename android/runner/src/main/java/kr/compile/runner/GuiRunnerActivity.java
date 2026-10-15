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
        // SDL3 is linked statically into the generated user library. Loading
        // libuser.so also registers SDL's complete Android JNI bridge.
        return new String[]{"user"};
    }

    @Override protected String getMainFunction() {
        return "SDL_main";
    }
}
