package kr.compile.runner;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.app.Activity;

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


    // MDraft-specific Android Storage Access Framework tree picker.
    private static final int MD_TREE_REQUEST = 0x4D41;
    private volatile String mdTreeResult = null; // null=waiting, ""=cancelled

    public boolean mdOpenFolderPicker() {
        mdTreeResult = null;
        runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, MD_TREE_REQUEST);
            } catch (Exception failure) {
                android.util.Log.e("MDraftKR", "Ordnerauswahl fehlgeschlagen", failure);
                mdTreeResult = "";
            }
        });
        return true;
    }

    public String mdPollFolderPicker() {
        return mdTreeResult;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MD_TREE_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri tree = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(
                        tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {}
                mdTreeResult = tree.toString();
            } else {
                mdTreeResult = "";
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
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
