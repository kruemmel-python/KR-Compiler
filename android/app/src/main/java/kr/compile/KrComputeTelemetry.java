package kr.compile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the last native Adaptive Compute JSONL event without extra dependencies. */
public final class KrComputeTelemetry {
    private final String json;

    private KrComputeTelemetry(String json) { this.json = json; }

    public static KrComputeTelemetry readLast(File file) throws IOException {
        if (file == null || !file.isFile()) return null;
        if (file.length() > Integer.MAX_VALUE) throw new IOException("Telemetriedatei ist zu groß.");
        byte[] all = new byte[(int) file.length()];
        int used = 0;
        try (FileInputStream in = new FileInputStream(file)) {
            while (used < all.length) {
                int count = in.read(all, used, all.length - used);
                if (count < 0) break;
                used += count;
            }
        }
        int from = Math.max(0, used - 1024 * 1024);
        String text = new String(all, from, used - from, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) return null;
        int line = text.lastIndexOf('\n');
        String last = (line >= 0 ? text.substring(line + 1) : text).trim();
        return last.startsWith("{") ? new KrComputeTelemetry(last) : null;
    }

    public String formatReport() {
        String backend = string("backend", "unbekannt");
        String decision = string("decision", "unbekannt");
        String operation = string("operation", "unbekannt");
        String fallback = string("fallbackReason", "");
        String device = string("deviceName", backend.equals("cpu") ? "CPU" : "unbekannt");
        String runtime = string("runtimeVersion", "unbekannt");
        String capabilities = string("capabilities", "unbekannt");
        return "\nLaufzeit-Telemetrie (letzte Operation)\n"
                + "Operation: " + operation + "\n"
                + "Gerätestatus/Backend: " + backend + " – " + device + "\n"
                + "Laufzeit/Version: " + runtime + "\n"
                + "Fähigkeiten: " + capabilities + "\n"
                + "Entscheidung: " + decision + "\n"
                + "Zeit: Initialisierung " + number("initMs") + " ms, Host→Gerät " + number("hostToDeviceMs")
                + " ms, Kernel " + number("kernelMs") + " ms, Gerät→Host " + number("deviceToHostMs")
                + " ms, Gesamt " + number("totalMs") + " ms\n"
                + "Datentransfer: " + integer("bytesToDevice") + " B hin / " + integer("bytesFromDevice") + " B zurück\n"
                + "Spitzenspeicher Gerät: " + integer("peakDeviceBytes") + " B\n"
                + "Validierung: " + (bool("validated") ? (bool("validationOk") ? "bestanden" : "fehlgeschlagen") : "nicht angefordert") + "\n"
                + "Fehlercode: " + integer("errorCode") + (fallback.isEmpty() ? "" : "\nFallback/Fehler: " + fallback) + "\n";
    }

    private String string(String key, String fallback) {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : fallback;
    }

    private String raw(String key, String fallback) {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*([-+0-9.eE]+|true|false)").matcher(json);
        return m.find() ? m.group(1) : fallback;
    }

    private String number(String key) {
        try { return String.format(Locale.ROOT, "%.3f", Double.parseDouble(raw(key, "0"))); }
        catch (NumberFormatException ignored) { return "0.000"; }
    }

    private long integer(String key) {
        try { return Long.parseLong(raw(key, "0")); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private boolean bool(String key) { return "true".equals(raw(key, "false")) || "1".equals(raw(key, "0")); }
}
