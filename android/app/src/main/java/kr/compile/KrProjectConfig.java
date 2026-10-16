package kr.compile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parser and model for *.krproject configuration files.
 */
public final class KrProjectConfig {

    public final String name;
    public final String version;
    public final String entry;
    public final boolean isGui;
    public final List<String> modulePaths = new ArrayList<>();
    public final Set<String> vcpkgDependencies = new LinkedHashSet<>();
    public final String cppStandard;
    public final String rustEdition;
    public final String goVersion;

    public KrProjectConfig(String name, String version, String entry, boolean isGui,
                           List<String> modulePaths, Set<String> vcpkgDependencies,
                           String cppStandard, String rustEdition, String goVersion) {
        this.name = name != null && !name.trim().isEmpty() ? name.trim() : "KR_PROJECT";
        this.version = version != null && !version.trim().isEmpty() ? version.trim() : "0.1.0";
        this.entry = entry != null && !entry.trim().isEmpty() ? entry.trim() : "main.kr";
        this.isGui = isGui;
        if (modulePaths != null) {
            for (String p : modulePaths) {
                String trimmed = p.trim().replace('\\', '/');
                if (!trimmed.isEmpty() && !this.modulePaths.contains(trimmed)) {
                    this.modulePaths.add(trimmed);
                }
            }
        }
        if (vcpkgDependencies != null) {
            for (String d : vcpkgDependencies) {
                String trimmed = d.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty()) this.vcpkgDependencies.add(trimmed);
            }
        }
        this.cppStandard = cppStandard != null ? cppStandard.trim() : "c++20";
        this.rustEdition = rustEdition != null ? rustEdition.trim() : "2024";
        this.goVersion = goVersion != null ? goVersion.trim() : "1.24";
    }

    public static File findProjectConfigFile(File projectDir) {
        if (projectDir == null || !projectDir.isDirectory()) return null;
        File[] files = projectDir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".krproject")) {
                return f;
            }
        }
        return null;
    }

    public static KrProjectConfig parse(File configFile, String defaultName, boolean defaultGui) {
        if (configFile == null || !configFile.isFile()) {
            return new KrProjectConfig(defaultName, "0.1.0", "main.kr", defaultGui,
                    Collections.emptyList(), Collections.emptySet(), "c++20", "2024", "1.24");
        }

        String name = defaultName;
        String version = "0.1.0";
        String entry = "main.kr";
        boolean isGui = defaultGui;
        List<String> modulePaths = new ArrayList<>();
        Set<String> vcpkgDeps = new LinkedHashSet<>();
        String cppStandard = "c++20";
        String rustEdition = "2024";
        String goVersion = "1.24";

        String currentSection = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                    continue;
                }

                int eq = line.indexOf('=');
                if (eq <= 0) continue;

                String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String val = line.substring(eq + 1).trim();

                // Strip quotes if present
                if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                    val = val.substring(1, val.length() - 1);
                } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                    val = val.substring(1, val.length() - 1);
                }

                if (currentSection.equals("project")) {
                    if (key.equals("name")) name = val;
                    else if (key.equals("version")) version = val;
                    else if (key.equals("entry")) entry = val;
                    else if (key.equals("target_type") || key.equals("target") || key.equals("type")) {
                        isGui = val.equalsIgnoreCase("gui") || val.equalsIgnoreCase("sdl3") || val.equalsIgnoreCase("window");
                    }
                } else if (currentSection.equals("modules") || currentSection.equals("module")) {
                    if (key.equals("paths") || key.equals("path") || key.equals("modules")) {
                        parseArrayValues(val, modulePaths);
                    }
                } else if (currentSection.equals("dependencies") || currentSection.equals("vcpkg")) {
                    if (key.equals("vcpkg") || key.equals("packages") || key.equals("ports")) {
                        List<String> list = new ArrayList<>();
                        parseArrayValues(val, list);
                        vcpkgDeps.addAll(list);
                    }
                } else if (currentSection.equals("build")) {
                    if (key.equals("cpp_standard") || key.equals("cpp")) cppStandard = val;
                    else if (key.equals("rust_edition") || key.equals("rust")) rustEdition = val;
                    else if (key.equals("go_version") || key.equals("go")) goVersion = val;
                    else if (key.equals("target_type") || key.equals("gui")) {
                        isGui = val.equalsIgnoreCase("gui") || val.equalsIgnoreCase("true") || val.equalsIgnoreCase("1");
                    }
                }
            }
        } catch (Exception ignored) {}

        return new KrProjectConfig(name, version, entry, isGui, modulePaths, vcpkgDeps, cppStandard, rustEdition, goVersion);
    }

    private static void parseArrayValues(String raw, List<String> out) {
        String s = raw.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        for (String part : s.split(",")) {
            String item = part.trim();
            if (item.startsWith("\"") && item.endsWith("\"") && item.length() >= 2) {
                item = item.substring(1, item.length() - 1).trim();
            } else if (item.startsWith("'") && item.endsWith("'") && item.length() >= 2) {
                item = item.substring(1, item.length() - 1).trim();
            }
            if (!item.isEmpty() && !out.contains(item)) {
                out.add(item);
            }
        }
    }
}
