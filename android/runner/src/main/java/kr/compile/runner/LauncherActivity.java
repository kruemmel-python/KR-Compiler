package kr.compile.runner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import java.io.InputStream;

/** Chooses the console or SDL surface runner from a marker injected by KR IDE. */
public final class LauncherActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Class<?> activity = hasGuiMarker() ? GuiRunnerActivity.class : RunnerActivity.class;
        startActivity(new Intent(this, activity));
        finish();
    }

    private boolean hasGuiMarker() {
        try (InputStream ignored = getAssets().open("kr-gui-runner")) {
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
