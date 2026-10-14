package kr.compile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_PROJECT = 42;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView projectLabel;
    private TextView status;
    private TextView log;
    private Spinner target;
    private CheckBox onlineDependencies;
    private Button build;
    private Uri projectUri;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(createUi());
        String saved = getPreferences(MODE_PRIVATE).getString("project", null);
        if (saved != null) {
            projectUri = Uri.parse(saved);
            projectLabel.setText(DocumentTree.displayName(this, projectUri));
        }
    }

    private View createUi() {
        int pad = dp(18);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("KR Compiler · Android");
        title.setTextSize(25); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        page.addView(title);

        TextView intro = new TextView(this);
        intro.setText("C/C++-Projektordner auswählen, CMake auswerten und direkt auf dem Android-Gerät bauen.");
        intro.setTextSize(15); intro.setPadding(0, dp(6), 0, dp(14));
        page.addView(intro);

        Button choose = new Button(this);
        choose.setText("Projektordner auswählen");
        choose.setOnClickListener(v -> chooseProject());
        page.addView(choose);

        projectLabel = new TextView(this);
        projectLabel.setText("Kein Projekt ausgewählt");
        projectLabel.setPadding(0, dp(8), 0, dp(12));
        page.addView(projectLabel);

        TextView targetTitle = new TextView(this); targetTitle.setText("Zielsystem");
        targetTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD); page.addView(targetTitle);
        target = new Spinner(this);
        target.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Android APK (ARM64)", "Windows EXE (x64)", "Linux Programm (x64)"}));
        page.addView(target);

        onlineDependencies = new CheckBox(this);
        onlineDependencies.setText("Fehlende CMake-Abhängigkeiten online laden");
        onlineDependencies.setChecked(true);
        onlineDependencies.setPadding(0, dp(8), 0, 0);
        page.addView(onlineDependencies);

        build = new Button(this); build.setText("Kompilieren");
        build.setOnClickListener(v -> compile());
        LinearLayout.LayoutParams buildParams = new LinearLayout.LayoutParams(-1, dp(52));
        buildParams.setMargins(0, dp(12), 0, dp(8)); page.addView(build, buildParams);

        status = new TextView(this); status.setText("Bereit"); status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        page.addView(status);

        log = new TextView(this); log.setText("Ausgaben erscheinen hier.\n");
        log.setTypeface(Typeface.MONOSPACE); log.setTextSize(12); log.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this); scroll.addView(log);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return page;
    }

    private void chooseProject() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, PICK_PROJECT);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_PROJECT || result != RESULT_OK || data == null || data.getData() == null) return;
        projectUri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { getContentResolver().takePersistableUriPermission(projectUri, flags); } catch (SecurityException ignored) {}
        getPreferences(MODE_PRIVATE).edit().putString("project", projectUri.toString()).apply();
        projectLabel.setText(DocumentTree.displayName(this, projectUri));
    }

    private void compile() {
        if (projectUri == null) { Toast.makeText(this, "Bitte zuerst Test_projekt auswählen.", Toast.LENGTH_LONG).show(); return; }
        build.setEnabled(false); log.setText(""); status.setText("Compiler wird vorbereitet …");
        CompilerEngine.Target selected = CompilerEngine.Target.values()[target.getSelectedItemPosition()];
        boolean allowOnline = onlineDependencies.isChecked();
        worker.execute(() -> {
            CompilerEngine engine = new CompilerEngine(this, projectUri, selected, allowOnline, line -> runOnUiThread(() -> {
                log.append(line + "\n");
            }));
            String result;
            try { result = engine.build(); }
            catch (Exception e) { result = "FEHLER: " + e.getMessage(); }
            String finalResult = result;
            runOnUiThread(() -> { status.setText(finalResult); build.setEnabled(true); });
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { super.onDestroy(); worker.shutdownNow(); }
}
