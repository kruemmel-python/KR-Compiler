package kr.compile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Manages the portable subset of vcpkg manifest mode supported by KR IDE. */
public final class VcpkgManager {
    public static final class Library {
        public final String port;
        public final String title;
        public final String description;

        Library(String port, String title, String description) {
            this.port = port;
            this.title = title;
            this.description = description;
        }
    }

    private static final List<Library> CATALOG = Arrays.asList(
            new Library("sdl3", "SDL3", "Fenster, Touch, Eingabe und 2D-Rendering für Android/Windows/Linux"),
            new Library("imgui", "Dear ImGui", "Unmittelbare C++-Oberflächen für Werkzeuge und Editoren"),
            new Library("fmt", "fmt", "Formatierte Textausgabe"),
            new Library("nlohmann-json", "nlohmann-json", "JSON für modernes C++"),
            new Library("spdlog", "spdlog", "Schnelles strukturiertes Logging"),
            new Library("eigen3", "Eigen3", "Lineare Algebra")
    );

    private VcpkgManager() {}

    public static List<Library> catalog() { return CATALOG; }

    public static Set<String> readDependencies(File project) throws IOException {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        File manifest = new File(project, "vcpkg.json");
        if (!manifest.isFile()) return result;
        try {
            JSONObject json = new JSONObject(read(manifest));
            JSONArray dependencies = json.optJSONArray("dependencies");
            if (dependencies == null) return result;
            for (int i = 0; i < dependencies.length(); i++) {
                Object value = dependencies.get(i);
                String port = value instanceof JSONObject
                        ? ((JSONObject) value).optString("name", "") : String.valueOf(value);
                port = normalize(port);
                if (!port.isEmpty()) result.add(port);
            }
        } catch (JSONException e) {
            throw new IOException("vcpkg.json ist ungültig: " + e.getMessage(), e);
        }
        return result;
    }

    public static void writeDependencies(File project, Set<String> ports) throws IOException {
        if (project == null || !project.isDirectory()) throw new IOException("Kein lokales Projekt geöffnet.");
        try {
            JSONObject manifest = new JSONObject();
            File file = new File(project, "vcpkg.json");
            if (file.isFile()) manifest = new JSONObject(read(file));
            if (!manifest.has("name")) manifest.put("name", sanitizeName(project.getName()));
            if (!manifest.has("version-string")) manifest.put("version-string", "0.1.0");
            JSONArray dependencies = new JSONArray();
            for (String port : ports) dependencies.put(normalize(port));
            manifest.put("dependencies", dependencies);
            write(file, manifest.toString(2) + "\n");
        } catch (JSONException e) {
            throw new IOException("vcpkg.json ist ungültig: " + e.getMessage(), e);
        }
    }

    public static String normalize(String port) {
        if (port == null) return "";
        return port.trim().toLowerCase(Locale.ROOT);
    }

    public static String sanitizeName(String raw) {
        String s = raw == null ? "projekt" : raw.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9_-]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "projekt" : s;
    }

    private static String read(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = in.readAllBytes();
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    private static void write(File f, String s) throws IOException {
        if (f.getParentFile() != null && !f.getParentFile().exists()) f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
