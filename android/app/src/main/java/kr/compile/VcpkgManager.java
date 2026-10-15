package kr.compile;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    public static File createGuiProject(Context context, String requestedName) throws IOException {
        String name = requestedName == null ? "KR-GUI-Beispiel" : requestedName.trim();
        if (name.isEmpty()) name = "KR-GUI-Beispiel";
        if (!name.matches("[A-Za-z0-9ÄÖÜäöüß._ -]{1,64}"))
            throw new IOException("Der Projektname enthält nicht unterstützte Zeichen.");
        File projects = new File(context.getFilesDir(), "projects");
        if (!projects.isDirectory() && !projects.mkdirs()) throw new IOException("Projektordner kann nicht erstellt werden.");
        File project = new File(projects, name);
        if (project.exists()) throw new IOException("Projekt existiert bereits: " + name);
        File src = new File(project, "src");
        if (!src.mkdirs()) throw new IOException("Projektstruktur kann nicht erstellt werden.");
        write(new File(project, "CMakeLists.txt"), cmakeTemplate());
        write(new File(src, "main.cpp"), sourceTemplate());
        write(new File(project, "kr-gui-target.txt"), "sdl3-imgui\n");
        LinkedHashSet<String> ports = new LinkedHashSet<>();
        ports.add("sdl3");
        ports.add("imgui");
        writeDependencies(project, ports);
        write(new File(project, "README.md"), readmeTemplate());
        return project;
    }

    public static boolean isSupported(String port) {
        for (Library library : CATALOG) if (library.port.equals(normalize(port))) return true;
        return false;
    }

    public static boolean isGuiProject(File project, Set<String> dependencies) {
        if (new File(project, "kr-gui-target.txt").isFile()) return true;
        return dependencies.contains("sdl3") && dependencies.contains("imgui");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String sanitizeName(String value) {
        String result = normalize(value).replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        result = result.replaceAll("^-|-$", "");
        return result.isEmpty() ? "kr-project" : result;
    }

    private static String cmakeTemplate() {
        return "cmake_minimum_required(VERSION 3.20)\n"
                + "project(KRGuiBeispiel LANGUAGES CXX)\n\n"
                + "set(CMAKE_CXX_STANDARD 20)\n"
                + "set(CMAKE_CXX_STANDARD_REQUIRED ON)\n\n"
                + "find_package(SDL3 CONFIG REQUIRED)\n\n"
                + "add_executable(KRGuiBeispiel WIN32 src/main.cpp)\n"
                + "target_link_libraries(KRGuiBeispiel PRIVATE SDL3::SDL3 imgui::imgui)\n";
    }

    private static String sourceTemplate() {
        return "#ifndef __ANDROID__\n#define SDL_MAIN_HANDLED\n#endif\n"
                + "#include <SDL3/SDL.h>\n"
                + "#include <SDL3/SDL_main.h>\n"
                + "#include <imgui.h>\n"
                + "#include <backends/imgui_impl_sdl3.h>\n"
                + "#include <backends/imgui_impl_sdlrenderer3.h>\n"
                + "#ifdef _WIN32\n#include <windows.h>\n#endif\n\n"
                + "static int run_app() {\n"
                + "    SDL_SetMainReady();\n"
                + "    if (!SDL_Init(SDL_INIT_VIDEO)) return 1;\n"
                + "    SDL_Window* window = SDL_CreateWindow(\"KR Compiler GUI\", 900, 560, SDL_WINDOW_RESIZABLE);\n"
                + "    SDL_Renderer* renderer = window ? SDL_CreateRenderer(window, nullptr) : nullptr;\n"
                + "    if (!window || !renderer) { SDL_Quit(); return 2; }\n"
                + "    IMGUI_CHECKVERSION();\n"
                + "    ImGui::CreateContext();\n"
                + "    ImGui::StyleColorsDark();\n"
                + "#if defined(__ANDROID__)\n"
                + "    ImGui::GetIO().FontGlobalScale = 2.0f;\n"
                + "    ImGui::GetStyle().ScaleAllSizes(2.0f);\n"
                + "#endif\n"
                + "    ImGui_ImplSDL3_InitForSDLRenderer(window, renderer);\n"
                + "    ImGui_ImplSDLRenderer3_Init(renderer);\n"
                + "    bool running = true;\n"
                + "    while (running) {\n"
                + "        SDL_Event event;\n"
                + "        while (SDL_PollEvent(&event)) {\n"
                + "            ImGui_ImplSDL3_ProcessEvent(&event);\n"
                + "            if (event.type == SDL_EVENT_QUIT) running = false;\n"
                + "        }\n"
                + "        ImGui_ImplSDLRenderer3_NewFrame();\n"
                + "        ImGui_ImplSDL3_NewFrame();\n"
                + "        ImGui::NewFrame();\n"
                + "#if defined(__ANDROID__)\n"
                + "        ImGui::SetNextWindowPos(ImVec2(32, 32), ImGuiCond_Once);\n"
                + "        ImGui::SetNextWindowSize(ImVec2(760, 320), ImGuiCond_Once);\n"
                + "#endif\n"
                + "        ImGui::Begin(\"KR Compiler\");\n"
                + "        ImGui::Text(\"GUI erfolgreich mit SDL3 + Dear ImGui gebaut.\");\n"
                + "        ImGui::Text(\"Ziel: %s\",\n"
                + "#if defined(__ANDROID__)\n                \"Android ARM64\"\n#elif defined(_WIN32)\n                \"Windows x64\"\n#else\n                \"Linux x64\"\n#endif\n"
                + "        );\n"
                + "        if (ImGui::Button(\"Beenden\")) running = false;\n"
                + "        ImGui::End();\n"
                + "        ImGui::Render();\n"
                + "        SDL_SetRenderDrawColor(renderer, 20, 24, 32, 255);\n"
                + "        SDL_RenderClear(renderer);\n"
                + "        ImGui_ImplSDLRenderer3_RenderDrawData(ImGui::GetDrawData(), renderer);\n"
                + "        SDL_RenderPresent(renderer);\n"
                + "    }\n"
                + "    ImGui_ImplSDLRenderer3_Shutdown();\n"
                + "    ImGui_ImplSDL3_Shutdown();\n"
                + "    ImGui::DestroyContext();\n"
                + "    SDL_DestroyRenderer(renderer);\n"
                + "    SDL_DestroyWindow(window);\n"
                + "    SDL_Quit();\n"
                + "    return 0;\n"
                + "}\n\n"
                + "#ifdef _WIN32\n"
                + "int WINAPI WinMain(HINSTANCE, HINSTANCE, LPSTR, int) { return run_app(); }\n"
                + "#else\n"
                + "int main(int, char**) { return run_app(); }\n"
                + "#endif\n";
    }

    private static String readmeTemplate() {
        return "# KR GUI-Beispiel\n\n"
                + "Dieses Projekt verwendet ein vcpkg-Manifest sowie SDL3 und Dear ImGui.\n"
                + "In KR IDE als Ziel Android APK, Windows x64 oder Linux x64 wählen und kompilieren.\n";
    }

    private static String read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int n = in.read(data, offset, data.length - offset);
                if (n < 0) break;
                offset += n;
            }
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private static void write(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Ordner kann nicht erstellt werden: " + parent);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
