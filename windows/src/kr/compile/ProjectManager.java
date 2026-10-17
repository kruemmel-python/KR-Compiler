package kr.compile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ProjectManager {
    public interface Listener {
        void onProjectOpened(File projectDir);
        void onProjectClosed();
        void onTreeRefreshed(File projectDir);
    }

    public enum Template {
        MODULAR_KR_FORGE,
        SINGLE_KR,
        CPP_PROJECT,
        RUST_PROJECT,
        GO_PROJECT
    }

    private static ProjectManager instance;
    private File activeProjectDir;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public static synchronized ProjectManager getInstance() {
        if (instance == null) instance = new ProjectManager();
        return instance;
    }

    private ProjectManager() {}

    public void addListener(Listener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public File getActiveProjectDir() { return activeProjectDir; }
    public File getActiveLocalDir() { return activeProjectDir; }
    public String getActiveProjectName() { return activeProjectDir != null ? activeProjectDir.getName() : "Kein Projekt"; }

    public void openProject(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        this.activeProjectDir = dir;
        DocumentManager.getInstance().closeAll();
        for (Listener l : listeners) l.onProjectOpened(dir);
        refreshTree();
    }

    public void closeProject() {
        this.activeProjectDir = null;
        DocumentManager.getInstance().closeAll();
        for (Listener l : listeners) l.onProjectClosed();
    }

    public void refreshTree() {
        if (activeProjectDir != null) {
            for (Listener l : listeners) l.onTreeRefreshed(activeProjectDir);
        }
    }

    public static boolean isBinaryFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".apk") || lower.endsWith(".so") || lower.endsWith(".exe")
                || lower.endsWith(".dll") || lower.endsWith(".a") || lower.endsWith(".lib")
                || lower.endsWith(".o") || lower.endsWith(".obj") || lower.endsWith(".zip")
                || lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".png")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".bin");
    }

    public File createProject(File parentDir, String projectName, Template template) throws IOException {
        File dir = new File(parentDir, projectName);
        if (dir.exists()) {
            throw new IOException("Ordner existiert bereits: " + dir.getAbsolutePath());
        }
        if (!dir.mkdirs()) {
            throw new IOException("Konnte Projektordner nicht anlegen: " + dir.getAbsolutePath());
        }

        switch (template) {
            case MODULAR_KR_FORGE -> createModularKrForgeTemplate(dir, projectName);
            case SINGLE_KR -> createSingleKrTemplate(dir, projectName);
            case CPP_PROJECT -> createCppTemplate(dir, projectName);
            case RUST_PROJECT -> createRustTemplate(dir, projectName);
            case GO_PROJECT -> createGoTemplate(dir, projectName);
        }

        openProject(dir);
        return dir;
    }

    private void createModularKrForgeTemplate(File root, String name) throws IOException {
        String projectConfig = "[project]\n"
                + "name = \"" + name + "\"\n"
                + "version = \"1.0.0\"\n"
                + "edition = \"2026\"\n\n"
                + "[modules]\n"
                + "sources = [\n"
                + "    \"app/\",\n"
                + "    \"gui/\",\n"
                + "    \"research/\",\n"
                + "    \"compute/\",\n"
                + "    \"orchestration/\",\n"
                + "    \"storage/\"\n"
                + "]\n\n"
                + "[dependencies]\n"
                + "packages = [\"opencl\", \"sqlite3\", \"nlohmann-json\"]\n\n"
                + "[build]\n"
                + "optimization = \"O3\"\n"
                + "warnings = \"all\"\n";
        writeFile(new File(root, "forge.krproject"), projectConfig);

        String mainKr = "#include <iostream>\n\n"
                + "void forge_app_init();\n\n"
                + "int main(int argc, char** argv) {\n"
                + "    (void)argc; (void)argv;\n"
                + "    std::cout << \"=== KR FORGE Enterprise Simulation Engine ===\" << std::endl;\n"
                + "    forge_app_init();\n"
                + "    std::cout << \"=== KR_FORGE Ausfuehrung erfolgreich abgeschlossen ===\" << std::endl;\n"
                + "    std::cout << \"\\nDruecken Sie die Eingabetaste (Enter), um das Fenster zu beenden...\" << std::endl;\n"
                + "    std::cin.get();\n"
                + "    return 0;\n"
                + "}\n";
        writeFile(new File(root, "main.kr"), mainKr);

        String appKr = "#include <iostream>\n#include <cstdint>\n\n"
                + "void forge_app_init() {\n"
                + "    std::cout << \"[KR_FORGE] Initialisiere Core-Subsysteme...\" << std::endl;\n"
                + "    int64_t gcd = forge_algebra_reduce(48, 18);\n"
                + "    int32_t workerRes = start_worker(gcd);\n"
                + "    bool taskOk = forge_orchestrate_task(101, workerRes);\n"
                + "    std::cout << \"[KR_FORGE] Status: Task=\" << (taskOk ? \"OK\" : \"ERR\") << \", GCD(48, 18)=\" << gcd << std::endl;\n"
                + "}\n";
        writeFile(new File(root, "app/application.kr"), appKr);

        writeFile(new File(root, "research/algebra_engine.kr"), "pub fn forge_algebra_reduce(mut a: i64, mut b: i64) -> i64 {\n    while b != 0 {\n        let t = b;\n        b = a % b;\n        a = t;\n    }\n    a\n}\n");
        writeFile(new File(root, "compute/cpu_executor.kr"), "fn start_worker(id: i64) -> i32 {\n    println!(\"Rust Worker gestartet mit ID: {}\", id);\n    (id * 10) as i32\n}\n");
        writeFile(new File(root, "orchestration/job_scheduler.kr"), "func forge_orchestrate_task(taskId int32, workload int32) bool {\n    println(\"Go Scheduler: Starte Task #\", taskId, \"mit Workload:\", workload)\n    return workload > 0\n}\n");
        writeFile(new File(root, "gui/window_system.kr"), "fn render_frame() -> bool {\n    true\n}\n");
        writeFile(new File(root, "storage/database.kr"), "fn query_record(id: i64) -> bool {\n    id > 0\n}\n");
    }

    private void createSingleKrTemplate(File root, String name) throws IOException {
        String code = "#include <iostream>\n\n"
                + "int main() {\n"
                + "    std::cout << \"=== Hallo aus der KR Unified Language ===\" << std::endl;\n"
                + "    int x = 42;\n"
                + "    int y = 58;\n"
                + "    std::cout << \"Berechnung in C++: \" << x << \" + \" << y << \" = \" << (x + y) << std::endl;\n"
                + "    std::cout << \"\\nDruecken Sie Enter zum Beenden...\" << std::endl;\n"
                + "    std::cin.get();\n"
                + "    return 0;\n"
                + "}\n";
        writeFile(new File(root, "main.kr"), code);
    }

    private void createCppTemplate(File root, String name) throws IOException {
        String cmake = "cmake_minimum_required(VERSION 3.20)\n"
                + "project(" + name + " CXX)\n"
                + "set(CMAKE_CXX_STANDARD 20)\n"
                + "add_executable(" + name + " main.cpp)\n";
        writeFile(new File(root, "CMakeLists.txt"), cmake);

        String cpp = "#include <iostream>\n\n"
                + "int main() {\n"
                + "    std::cout << \"Hallo aus C++20 auf Windows!\" << std::endl;\n"
                + "    return 0;\n"
                + "}\n";
        writeFile(new File(root, "main.cpp"), cpp);
    }

    private void createRustTemplate(File root, String name) throws IOException {
        String cargo = "[package]\n"
                + "name = \"" + name.toLowerCase(Locale.ROOT) + "\"\n"
                + "version = \"0.1.0\"\n"
                + "edition = \"2021\"\n\n"
                + "[dependencies]\n";
        writeFile(new File(root, "Cargo.toml"), cargo);
        File src = new File(root, "src");
        src.mkdirs();
        writeFile(new File(src, "main.rs"), "fn main() {\n    println!(\"Hallo aus Rust auf Windows!\");\n}\n");
    }

    private void createGoTemplate(File root, String name) throws IOException {
        writeFile(new File(root, "go.mod"), "module " + name.toLowerCase(Locale.ROOT) + "\n\ngo 1.22\n");
        writeFile(new File(root, "main.go"), "package main\n\nimport \"fmt\"\n\nfunc main() {\n\tfmt.Println(\"Hallo aus Go auf Windows!\")\n}\n");
    }

    private static void writeFile(File f, String content) throws IOException {
        if (f.getParentFile() != null && !f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
