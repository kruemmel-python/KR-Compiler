package kr.compile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Verwaltet das Quellcode-Mapping zwischen KR Unified Quelldateien (.kr)
 * und den daraus für C++, Rust und Go generierten Build-Artefakten
 * (z. B. KR_BUILD/rust_module/src/lib.rs, KR_BUILD/go_module/main.go, KR_BUILD/modules/*.cpp).
 */
public final class KrSourceMap {

    public static final class SegmentMapping {
        public final String generatedFile;      // z. B. "rust_module/src/lib.rs" oder "modules/gui_window.cpp"
        public final String originalFile;       // z. B. "security/policy.kr" oder "gui/window.kr"
        public final int generatedStartLine;    // 1-basiert
        public final int generatedEndLine;      // 1-basiert
        public final int originalStartLine;     // 1-basiert
        public final int originalEndLine;       // 1-basiert
        public final String language;           // "RUST", "GO", "CPP"
        public final List<String> functions;    // Deklarierte Funktionsnamen in diesem Segment

        public SegmentMapping(String generatedFile, String originalFile,
                              int generatedStartLine, int generatedEndLine,
                              int originalStartLine, int originalEndLine,
                              String language, List<String> functions) {
            this.generatedFile = normalize(generatedFile);
            this.originalFile = normalize(originalFile);
            this.generatedStartLine = generatedStartLine;
            this.generatedEndLine = generatedEndLine;
            this.originalStartLine = originalStartLine;
            this.originalEndLine = originalEndLine;
            this.language = language != null ? language.toUpperCase(Locale.ROOT) : "CPP";
            this.functions = functions != null ? new ArrayList<>(functions) : new ArrayList<>();
        }

        public boolean matchesGenerated(String file) {
            if (file == null || file.isBlank()) return false;
            String n = normalize(file);
            if (n.equals(generatedFile)) return true;
            if (generatedFile.endsWith("/" + n) || generatedFile.endsWith(n)) return true;
            if (n.endsWith("/" + generatedFile) || n.endsWith(generatedFile)) return true;
            // Spezialfall für lib.rs und main.go
            String name = new File(n).getName();
            String genName = new File(generatedFile).getName();
            return name.equalsIgnoreCase(genName);
        }
    }

    public static final class ResolvedLocation {
        public final String originalFile;
        public final int originalLine;
        public final String functionName;
        public final String language;

        public ResolvedLocation(String originalFile, int originalLine, String functionName, String language) {
            this.originalFile = originalFile;
            this.originalLine = originalLine;
            this.functionName = functionName;
            this.language = language;
        }

        @Override
        public String toString() {
            return originalFile + ":" + originalLine + (functionName != null ? " (" + functionName + ")" : "");
        }
    }

    private final List<SegmentMapping> mappings = new ArrayList<>();

    public void addMapping(SegmentMapping mapping) {
        if (mapping != null) {
            mappings.add(mapping);
        }
    }

    public List<SegmentMapping> getMappings() {
        return Collections.unmodifiableList(mappings);
    }

    public static String normalize(String path) {
        if (path == null) return "";
        String s = path.trim().replace('\\', '/');
        while (s.startsWith("./")) s = s.substring(2);
        while (s.startsWith("/")) s = s.substring(1);
        if (s.startsWith("KR_BUILD/")) s = s.substring("KR_BUILD/".length());
        return s;
    }

    /**
     * Löst eine generierte Datei auf die ursprüngliche .kr-Quelldatei auf.
     */
    public String resolveOriginalFile(String queryPath, String functionHint) {
        if (queryPath == null || queryPath.isBlank()) return null;
        String clean = normalize(queryPath);

        // 1. Priorität: Funktionsname bekannt
        if (functionHint != null && !functionHint.isBlank()) {
            for (SegmentMapping m : mappings) {
                if (m.functions.contains(functionHint)) {
                    return m.originalFile;
                }
            }
        }

        // 2. Suche passende Generierungsdatei
        for (SegmentMapping m : mappings) {
            if (m.matchesGenerated(clean)) {
                return m.originalFile;
            }
        }

        // 3. Modulname-Auflösung (z. B. modules/gui_window.cpp -> gui/window.kr)
        String baseName = new File(clean).getName();
        if (baseName.endsWith(".cpp")) baseName = baseName.substring(0, baseName.length() - 4);
        if (baseName.endsWith(".c")) baseName = baseName.substring(0, baseName.length() - 2);

        for (SegmentMapping m : mappings) {
            String origClean = m.originalFile.replace('/', '_').replace(".kr", "");
            if (origClean.equalsIgnoreCase(baseName)) {
                return m.originalFile;
            }
        }

        return null;
    }

    /**
     * Löst eine Position in einer generierten Datei präzise auf die .kr-Quelldatei und Zeile auf.
     */
    public ResolvedLocation resolveLocation(String queryPath, int generatedLine, String functionHint) {
        // 1. Suche nach Funktionsname
        if (functionHint != null && !functionHint.isBlank()) {
            for (SegmentMapping m : mappings) {
                if (m.functions.contains(functionHint)) {
                    int origLine = m.originalStartLine;
                    if (generatedLine >= m.generatedStartLine && generatedLine <= m.generatedEndLine) {
                        origLine = m.originalStartLine + (generatedLine - m.generatedStartLine);
                    }
                    return new ResolvedLocation(m.originalFile, origLine, functionHint, m.language);
                }
            }
        }

        // 2. Suche nach Zeilenbereich in der passenden generierten Datei
        String clean = normalize(queryPath);
        for (SegmentMapping m : mappings) {
            if (m.matchesGenerated(clean)) {
                if (generatedLine >= m.generatedStartLine && generatedLine <= m.generatedEndLine) {
                    int origLine = m.originalStartLine + (generatedLine - m.generatedStartLine);
                    String fn = m.functions.isEmpty() ? null : m.functions.get(0);
                    return new ResolvedLocation(m.originalFile, origLine, fn, m.language);
                }
            }
        }

        // 3. Fallback: Erste Mapping-Datei
        for (SegmentMapping m : mappings) {
            if (m.matchesGenerated(clean)) {
                String fn = m.functions.isEmpty() ? null : m.functions.get(0);
                return new ResolvedLocation(m.originalFile, m.originalStartLine, fn, m.language);
            }
        }

        return null;
    }

    /**
     * Schreibt die SourceMap als JSON-Datei nach KR_BUILD/source_map.json
     */
    public void save(File jsonFile) throws IOException {
        JSONObject root = new JSONObject();
        root.put("version", "1.0");
        JSONArray arr = new JSONArray();
        for (SegmentMapping m : mappings) {
            JSONObject obj = new JSONObject();
            obj.put("generatedFile", m.generatedFile);
            obj.put("originalFile", m.originalFile);
            obj.put("generatedStartLine", m.generatedStartLine);
            obj.put("generatedEndLine", m.generatedEndLine);
            obj.put("originalStartLine", m.originalStartLine);
            obj.put("originalEndLine", m.originalEndLine);
            obj.put("language", m.language);
            JSONArray fns = new JSONArray();
            for (String f : m.functions) fns.put(f);
            obj.put("functions", fns);
            arr.put(obj);
        }
        root.put("mappings", arr);

        if (jsonFile.getParentFile() != null && !jsonFile.getParentFile().exists()) {
            jsonFile.getParentFile().mkdirs();
        }
        try (FileOutputStream out = new FileOutputStream(jsonFile)) {
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Lädt eine SourceMap aus einer Datei.
     */
    public static KrSourceMap load(File jsonFile) {
        if (jsonFile == null || !jsonFile.isFile()) return null;
        try {
            String content = Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(content);
            JSONArray arr = root.optJSONArray("mappings");
            if (arr == null) return null;

            KrSourceMap map = new KrSourceMap();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                List<String> functions = new ArrayList<>();
                JSONArray fns = obj.optJSONArray("functions");
                if (fns != null) {
                    for (int j = 0; j < fns.length(); j++) {
                        functions.add(fns.getString(j));
                    }
                }
                map.addMapping(new SegmentMapping(
                        obj.optString("generatedFile", ""),
                        obj.optString("originalFile", ""),
                        obj.optInt("generatedStartLine", 1),
                        obj.optInt("generatedEndLine", 1),
                        obj.optInt("originalStartLine", 1),
                        obj.optInt("originalEndLine", 1),
                        obj.optString("language", "CPP"),
                        functions
                ));
            }
            return map;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Sucht nach KR_BUILD/source_map.json im Projektverzeichnis.
     */
    public static KrSourceMap loadFromProject(File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) return null;
        File mapFile = new File(new File(projectRoot, "KR_BUILD"), "source_map.json");
        return load(mapFile);
    }
}
