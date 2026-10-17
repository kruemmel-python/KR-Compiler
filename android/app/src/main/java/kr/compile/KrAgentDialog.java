package kr.compile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/**
 * Autarke Benutzeroberfläche für den KR Autonomous Agent.
 * Behält den Chat- und Protokollverlauf dauerhaft bei, auch wenn der Dialog geschlossen wird.
 */
public final class KrAgentDialog {

    private static KrAgentCore agent;
    private static String sessionToken = "";

    private KrAgentDialog() {}

    public static synchronized KrAgentCore getAgent(Context context) {
        if (agent == null) {
            agent = new KrAgentCore(context.getApplicationContext());
        }
        return agent;
    }

    private static EditText field(Activity a, String hint, String value, int lines) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setText(value != null ? value : "");
        e.setTextSize(14);
        e.setSingleLine(lines == 1);
        if (lines > 1) {
            e.setMinLines(lines);
            e.setGravity(Gravity.TOP | Gravity.START);
        }
        return e;
    }

    public static void show(Activity a) {
        final KrAgentCore core = getAgent(a);
        SharedPreferences prefs = a.getSharedPreferences("kr_agent_settings", Activity.MODE_PRIVATE);

        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(28, 16, 28, 16);

        // 1. Status- und Info-Leiste
        LinearLayout statusRow = new LinearLayout(a);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, 0, 0, 12);

        ProgressBar spinner = new ProgressBar(a, null, android.R.attr.progressBarStyleSmall);
        spinner.setVisibility(core.isRunning() ? View.VISIBLE : View.GONE);
        statusRow.addView(spinner);

        TextView statusText = new TextView(a);
        statusText.setTextSize(13);
        statusText.setPadding(12, 0, 0, 0);
        statusText.setTextColor(Color.parseColor("#4CAF50"));
        statusText.setText(core.isRunning() ? "Aktiv: " + core.getCurrentStatus() : "Status: " + core.getCurrentStatus());
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btnClearLog = new Button(a);
        btnClearLog.setText("Log leeren");
        btnClearLog.setTextSize(11);
        statusRow.addView(btnClearLog);
        body.addView(statusRow);

        // 2. Eingabefelder
        EditText endpoint = field(a, "OpenAI-kompatibler HTTPS-Endpunkt",
                prefs.getString("endpoint", "https://api.openai.com/v1/chat/completions"), 1);

        EditText model = field(a, "Modellname (z. B. gpt-4o, claude-3-5-sonnet, llama3.1)",
                prefs.getString("model", "gpt-4o"), 1);

        EditText token = field(a, "API-Key (nur für diese Sitzung im RAM)",
                sessionToken, 1);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        String savedTask = !core.getLastTask().isEmpty() ? core.getLastTask() : prefs.getString("last_task", "");
        EditText task = field(a, "Auftrag an Agenten: z. B. Analysiere das geladene Projekt, behebe Compilerfehler und teste den Build.",
                savedTask, 3);

        body.addView(endpoint);
        body.addView(model);
        body.addView(token);
        body.addView(task);

        // 3. Protokollfenster mit Auto-Scroll
        TextView log = new TextView(a);
        log.setTextSize(12);
        log.setTextIsSelectable(true);
        String history = core.getLogHistory();
        log.setText(history.isEmpty() ? "Bereit. Ein lokales oder aus dem Speicher geladenes Projekt muss geöffnet sein.\n" : history);

        ScrollView logScroll = new ScrollView(a);
        logScroll.addView(log);
        body.addView(logScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 4. Steuerungs-Buttons
        LinearLayout buttons = new LinearLayout(a);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, 8, 0, 0);

        Button start = new Button(a);
        start.setText("START");
        start.setEnabled(!core.isRunning());

        Button cancel = new Button(a);
        cancel.setText("STOPP");
        cancel.setEnabled(core.isRunning());

        buttons.addView(start);
        buttons.addView(cancel);
        body.addView(buttons);

        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("KR Autonomous Agent")
                .setView(body)
                .setPositiveButton("Schließen", (d, w) -> {})
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
        });

        // Listener für Live-Updates während Dialog geöffnet ist
        KrAgentCore.Events dialogListener = new KrAgentCore.Events() {
            @Override
            public void onEvent(String message) {
                log.append("\n" + message + "\n");
                logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
                statusText.setText("Aktiv: " + core.getCurrentStatus());
                spinner.setVisibility(core.isRunning() ? View.VISIBLE : View.GONE);
                start.setEnabled(!core.isRunning());
                cancel.setEnabled(core.isRunning());
            }

            @Override
            public void onFinished(String status) {
                spinner.setVisibility(View.GONE);
                statusText.setText("Status: " + status);
                start.setEnabled(true);
                cancel.setEnabled(false);
            }
        };

        core.addListener(dialogListener);
        dialog.setOnDismissListener(d -> core.removeListener(dialogListener));

        btnClearLog.setOnClickListener(v -> {
            core.clearLogHistory();
            log.setText("Protokoll geleert.\n");
        });

        cancel.setOnClickListener(v -> {
            core.cancel();
            Toast.makeText(a, "Abbruch angefordert …", Toast.LENGTH_SHORT).show();
            cancel.setEnabled(false);
        });

        start.setOnClickListener(v -> {
            if (core.isRunning()) {
                Toast.makeText(a, "Agent läuft bereits.", Toast.LENGTH_SHORT).show();
                return;
            }
            String url = endpoint.getText().toString().trim();
            String modelId = model.getText().toString().trim();
            String taskText = task.getText().toString().trim();

            if (url.isEmpty() || modelId.isEmpty()) {
                Toast.makeText(a, "Endpunkt und Modellname dürfen nicht leer sein.", Toast.LENGTH_LONG).show();
                return;
            }
            if (taskText.isEmpty()) {
                Toast.makeText(a, "Bitte einen Entwicklungsauftrag eingeben.", Toast.LENGTH_LONG).show();
                return;
            }

            sessionToken = token.getText().toString();
            prefs.edit()
                    .putString("endpoint", url)
                    .putString("model", modelId)
                    .putString("last_task", taskText)
                    .apply();

            start.setEnabled(false);
            cancel.setEnabled(true);
            spinner.setVisibility(View.VISIBLE);
            statusText.setText("Agent gestartet …");

            KrAgentCore.Settings settings = new KrAgentCore.Settings(url, modelId, sessionToken, 30);
            core.start(taskText, settings, (title, detail, callback) -> {
                if (a.isFinishing() || a.isDestroyed()) {
                    callback.accept(false);
                    return;
                }
                new AlertDialog.Builder(a)
                        .setTitle(title)
                        .setMessage(detail)
                        .setCancelable(false)
                        .setNegativeButton("Ablehnen", (d, w) -> callback.accept(false))
                        .setPositiveButton("Genehmigen", (d, w) -> callback.accept(true))
                        .show();
            });
        });

        dialog.show();
    }
}
