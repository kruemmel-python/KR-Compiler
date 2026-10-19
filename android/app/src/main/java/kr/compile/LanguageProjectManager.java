package kr.compile;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

/** Creates dependency-free Rust, Go, and KR Unified Language starter projects for all KR targets. */
final class LanguageProjectManager {
    enum Language { RUST, GO, KR }

    enum KrTemplate {
        KR_CONSOLE("KR Konsole", false),
        KR_GUI("KR GUI mit SDL3", true),
        KR_FUSION_CONSOLE("KR Sprachfusion – C++ / Rust / Go", false),
        KR_FUSION_GUI("KR Sprachfusion – GUI", true),
        KR_MODULAR("KR Modulares Projekt (.krproject)", true);

        final String displayName;
        final boolean isGui;

        KrTemplate(String displayName, boolean isGui) {
            this.displayName = displayName;
            this.isGui = isGui;
        }
    }

    private LanguageProjectManager() {}

    static File create(Context context, Language language, boolean gui, String requestedName) throws IOException {
        String fallback = "KR-" + (language == Language.RUST ? "Rust" : "Go") + (gui ? "-GUI" : "-Konsole");
        String name = requestedName == null ? fallback : requestedName.trim();
        if (name.isEmpty()) name = fallback;
        if (!name.matches("[A-Za-z0-9ÄÖÜäöüß._ -]{1,64}"))
            throw new IOException("Der Projektname enthält nicht unterstützte Zeichen.");
        File projects = new File(context.getFilesDir(), "projects");
        if (!projects.isDirectory() && !projects.mkdirs()) throw new IOException("Projektordner kann nicht erstellt werden.");
        File project = new File(projects, name);
        if (project.exists()) throw new IOException("Projekt existiert bereits: " + name);
        if (!project.mkdirs()) throw new IOException("Projektstruktur kann nicht erstellt werden.");
        if (language == Language.RUST) createRust(project, name, gui);
        else if (language == Language.GO) createGo(project, name, gui);
        else createKr(context, project, name, gui ? KrTemplate.KR_FUSION_GUI : KrTemplate.KR_FUSION_CONSOLE);
        if (gui) write(new File(project, "kr-gui-target.txt"), "sdl3\n");
        write(new File(project, "README.md"), readme(language, gui));
        return project;
    }

    static File createKr(Context context, KrTemplate template, String requestedName) throws IOException {
        String fallback = "KR-" + template.name().replace('_', '-');
        String name = requestedName == null ? fallback : requestedName.trim();
        if (name.isEmpty()) name = fallback;
        if (!name.matches("[A-Za-z0-9ÄÖÜäöüß._ -]{1,64}"))
            throw new IOException("Der Projektname enthält nicht unterstützte Zeichen.");
        File projects = new File(context.getFilesDir(), "projects");
        if (!projects.isDirectory() && !projects.mkdirs()) throw new IOException("Projektordner kann nicht erstellt werden.");
        File project = new File(projects, name);
        if (project.exists()) throw new IOException("Projekt existiert bereits: " + name);
        if (!project.mkdirs()) throw new IOException("Projektstruktur kann nicht erstellt werden.");

        createKr(context, project, name, template);
        if (template.isGui) {
            write(new File(project, "kr-gui-target.txt"), "sdl3\n");
        }
        write(new File(project, "README.md"), readmeKr(template));
        return project;
    }

    private static void createKr(Context context, File project, String name, KrTemplate template) throws IOException {
        if (template == KrTemplate.KR_MODULAR) {
            createModularKrProject(context, project, name);
            return;
        }
        String code;
        switch (template) {
            case KR_CONSOLE:
                code = krConsole();
                break;
            case KR_GUI:
                code = krGui();
                break;
            case KR_FUSION_GUI:
                code = krFusionGui();
                break;
            case KR_FUSION_CONSOLE:
            default:
                code = krFusionConsole();
                break;
        }
        write(new File(project, "main.kr"), code);
    }

    public static void createModularKrProject(File project, String name) throws IOException {
        createModularKrProject(null, project, name);
    }

    private static void createModularKrProject(Context context, File project, String name) throws IOException {
        String safeName = sanitize(name);

        // 1. Root configuration: forge.krproject
        StringBuilder proj = new StringBuilder();
        proj.append("[project]\n");
        proj.append("name = \"").append(name).append("\"\n");
        proj.append("version = \"0.1.0\"\n");
        proj.append("entry = \"main.kr\"\n");
        proj.append("target_type = \"gui\"\n\n");
        proj.append("[modules]\n");
        proj.append("paths = [\"app\", \"gui\", \"research\", \"compute\", \"orchestration\", \"storage\"]\n\n");
        proj.append("[dependencies]\n");
        proj.append("vcpkg = [\"sdl3\"]\n\n");
        proj.append("[build]\n");
        proj.append("cpp_standard = \"c++20\"\n");
        proj.append("rust_edition = \"2024\"\n");
        proj.append("go_version = \"1.24\"\n");
        proj.append("\n[compute]\n");
        proj.append("mode = \"AUTO\"\n");
        write(new File(project, safeName + ".krproject"), proj.toString());

        // 2. Root main.kr
        StringBuilder mainKr = new StringBuilder();
        mainKr.append("// Haupteinstiegspunkt für modulares KR-Projekt: ").append(name).append("\n");
        mainKr.append("#include <iostream>\n\n");
        mainKr.append("extern \"C\" {\n");
        mainKr.append("    int app_application_run();\n");
        mainKr.append("}\n\n");
        mainKr.append("int main(int argc, char** argv) {\n");
        mainKr.append("    (void)argc; (void)argv;\n");
        mainKr.append("    std::cout << \"[KR_FORGE] Initialisiere universelle Systemarchitektur...\" << std::endl;\n");
        mainKr.append("    return app_application_run();\n");
        mainKr.append("}\n");
        write(new File(project, "main.kr"), mainKr.toString());

        // 3. app/
        File appDir = new File(project, "app");
        write(new File(appDir, "application.kr"),
                "// app/application.kr - Zentrale Orchestrierung\n#include <iostream>\n\n"
                + "extern \"C\" {\n"
                + "    int compute_cpu_executor_init();\n"
                + "    int orchestration_job_scheduler_start();\n"
                + "    int storage_database_init();\n"
                + "    int gui_window_system_launch();\n"
                + "}\n\n"
                + "extern \"C\" int app_application_run() {\n"
                + "    std::cout << \"[App] Starte KR_FORGE Engine...\" << std::endl;\n"
                + "    storage_database_init();\n"
                + "    compute_cpu_executor_init();\n"
                + "    orchestration_job_scheduler_start();\n"
                + "    return gui_window_system_launch();\n"
                + "}\n");
        write(new File(appDir, "lifecycle.kr"),
                "// app/lifecycle.kr\n#include <iostream>\n\nextern \"C\" void app_lifecycle_shutdown() {\n    std::cout << \"[Lifecycle] Graceful shutdown.\" << std::endl;\n}\n");
        write(new File(appDir, "configuration.kr"),
                "// app/configuration.kr\n#include <iostream>\n\nextern \"C\" int app_config_load() { return 1; }\n");

        // 4. gui/
        File guiDir = new File(project, "gui");
        write(new File(guiDir, "window_system.kr"),
                "// gui/window_system.kr - SDL3 Windowing & Rendering\n#include <SDL3/SDL.h>\n#include <iostream>\n\n"
                + "extern \"C\" int gui_window_system_launch() {\n"
                + "    if (!SDL_Init(SDL_INIT_VIDEO)) {\n"
                + "        std::cout << \"[GUI] SDL_Init fehlgeschlagen: \" << SDL_GetError() << std::endl;\n"
                + "        return 1;\n"
                + "    }\n"
                + "    SDL_Window* window = SDL_CreateWindow(\"KR FORGE – Modulares System\", 900, 560, 0);\n"
                + "    SDL_Renderer* renderer = SDL_CreateRenderer(window, nullptr);\n"
                + "    if (!window || !renderer) { SDL_Quit(); return 2; }\n"
                + "    std::cout << \"[GUI] Fenster und SDL3 Renderer erfolgreich initialisiert.\" << std::endl;\n"
                + "    for (int i = 0; i < 3; ++i) {\n"
                + "        SDL_SetRenderDrawColor(renderer, 24, 48, 80, 255);\n"
                + "        SDL_RenderClear(renderer);\n"
                + "        SDL_RenderPresent(renderer);\n"
                + "        SDL_Delay(10);\n"
                + "    }\n"
                + "    SDL_DestroyRenderer(renderer);\n"
                + "    SDL_DestroyWindow(window);\n"
                + "    SDL_Quit();\n"
                + "    std::cout << \"[GUI] Modulares GUI-System erfolgreich beendet.\" << std::endl;\n"
                + "    return 0;\n"
                + "}\n");
        write(new File(guiDir, "widget_engine.kr"), "// gui/widget_engine.kr\nextern \"C\" int gui_widget_count() { return 42; }\n");
        write(new File(guiDir, "dock_manager.kr"), "// gui/dock_manager.kr\nextern \"C\" void gui_dock_init() {}\n");
        write(new File(guiDir, "graph_renderer.kr"), "// gui/graph_renderer.kr\nextern \"C\" void gui_render_node() {}\n");
        write(new File(guiDir, "formula_editor.kr"), "// gui/formula_editor.kr\nextern \"C\" void gui_editor_open() {}\n");
        write(new File(guiDir, "discovery_view.kr"), "// gui/discovery_view.kr\nextern \"C\" void gui_discovery_update() {}\n");
        write(new File(guiDir, "proof_view.kr"), "// gui/proof_view.kr\nextern \"C\" void gui_proof_render() {}\n");
        write(new File(guiDir, "compute_view.kr"), "// gui/compute_view.kr\nextern \"C\" void gui_compute_status() {}\n");

        // 5. research/
        File resDir = new File(project, "research");
        write(new File(resDir, "mathematical_ir.kr"), "// research/mathematical_ir.kr\nextern \"C\" int research_ir_version() { return 1; }\n");
        write(new File(resDir, "algebra_engine.kr"), "// research/algebra_engine.kr\nextern \"C\" int research_algebra_simplify() { return 0; }\n");
        write(new File(resDir, "structural_search.kr"), "// research/structural_search.kr\nextern \"C\" int research_search_patterns() { return 0; }\n");
        write(new File(resDir, "equivalence_engine.kr"), "// research/equivalence_engine.kr\nextern \"C\" bool research_is_equivalent() { return true; }\n");
        write(new File(resDir, "synthesis_engine.kr"), "// research/synthesis_engine.kr\nextern \"C\" void research_synthesize() {}\n");
        write(new File(resDir, "verification_engine.kr"), "// research/verification_engine.kr\nextern \"C\" bool research_verify_proof() { return true; }\n");
        write(new File(resDir, "strategy_evolution.kr"), "// research/strategy_evolution.kr\nextern \"C\" void research_evolve_step() {}\n");

        // 6. compute/ (Integrierte Rust-Funktionen)
        File compDir = new File(project, "compute");
        write(new File(compDir, "cpu_executor.kr"),
                "// compute/cpu_executor.kr - Geschrieben in Rust\n"
                + "pub fn compute_cpu_executor_init() -> i32 {\n"
                + "    println!(\"[Compute::CPU] Rust Tensor- & IR-Executor initialisiert.\");\n"
                + "    0\n"
                + "}\n\n"
                + "pub fn compute_dot_product(a: f32, b: f32) -> f32 {\n"
                + "    a * b\n"
                + "}\n");
        write(new File(compDir, "opencl_executor.kr"),
                "// compute/opencl_executor.kr - echte CC_OpenCL-Laufzeitprüfung\n#include <iostream>\n\n"
                + "extern \"C\" int initialize_gpu(int);\n"
                + "extern \"C\" void shutdown_gpu(int);\n"
                + "extern \"C\" const char* cc_get_last_error();\n\n"
                + "extern \"C\" bool compute_opencl_available() {\n"
                + "    const int ok = initialize_gpu(0);\n"
                + "    if (!ok) std::cout << \"[Compute::OpenCL] \" << cc_get_last_error() << std::endl;\n"
                + "    return ok == 1;\n}\n"
                + "extern \"C\" void compute_opencl_shutdown() { shutdown_gpu(0); }\n");
        write(new File(compDir, "code_generator.kr"), "// compute/code_generator.kr\nextern \"C\" void compute_emit_bytecode() {}\n");
        write(new File(compDir, "benchmark_engine.kr"), "// compute/benchmark_engine.kr\nextern \"C\" double compute_bench_flops() { return 4.2e9; }\n");
        write(new File(compDir, "memory_manager.kr"), "// compute/memory_manager.kr\nextern \"C\" size_t compute_vram_free() { return 1024 * 1024 * 512; }\n");

        // 7. orchestration/ (Integrierte Go-Funktionen)
        File orchDir = new File(project, "orchestration");
        write(new File(orchDir, "job_scheduler.kr"),
                "// orchestration/job_scheduler.kr - Geschrieben in Go\n"
                + "package main\n\n"
                + "import \"fmt\"\n\n"
                + "func orchestration_job_scheduler_start() int32 {\n"
                + "    fmt.Println(\"[Orchestration] Go Goroutine-Job-Scheduler aktiv.\")\n"
                + "    return 0\n"
                + "}\n");
        write(new File(orchDir, "experiment_manager.kr"), "// orchestration/experiment_manager.kr\nfunc orchestration_exp_count() int32 { return 10 }\n");
        write(new File(orchDir, "worker_runtime.kr"), "// orchestration/worker_runtime.kr\nfunc orchestration_worker_active() bool { return true }\n");
        write(new File(orchDir, "event_dispatch.kr"), "// orchestration/event_dispatch.kr\nfunc orchestration_emit_event() int32 { return 1 }\n");

        // 8. storage/
        File storDir = new File(project, "storage");
        write(new File(storDir, "database.kr"),
                "// storage/database.kr\n#include <iostream>\n\nextern \"C\" int storage_database_init() {\n    std::cout << \"[Storage] Initialisiere lokale Daten- & Snapshot-Engine.\" << std::endl;\n    return 0;\n}\n");
        write(new File(storDir, "artifact_store.kr"), "// storage/artifact_store.kr\nextern \"C\" void storage_put_artifact() {}\n");
        write(new File(storDir, "provenance.kr"), "// storage/provenance.kr\nextern \"C\" void storage_record_provenance() {}\n");
        write(new File(storDir, "snapshots.kr"), "// storage/snapshots.kr\nextern \"C\" void storage_create_snapshot() {}\n");

        // 9. contracts/
        File contrDir = new File(project, "contracts");
        write(new File(contrDir, "abi_contract.md"), "# ABI Contract\nStandardisiertes C-ABI fuer KR Sprachfusion.\n");
        write(new File(contrDir, "data_schema.md"), "# Data Schema\nSpeicher- und Nachrichtenschemata.\n");
        write(new File(contrDir, "status_codes.md"), "# Status Codes\n0: OK, 1: Error.\n");

        // 10. tests/
        File testsDir = new File(project, "tests");
        new File(testsDir, "cross_language").mkdirs();
        new File(testsDir, "mathematics").mkdirs();
        new File(testsDir, "verification").mkdirs();
        new File(testsDir, "benchmarks").mkdirs();
        new File(testsDir, "integration").mkdirs();

        // 11. docs/
        File docsDir = new File(project, "docs");
        write(new File(docsDir, "ARCHITECTURE.md"), "# Architecture\nUmfassende modulare Architektur von KR_FORGE.\n");

        if (context != null) {
            copyAssetTree(context, "cc-opencl", new File(project, "components/cc-opencl"));
        }
        write(new File(docsDir, "RESEARCH_METHOD.md"), "# Research Method\nForschungsmethoden und mathematische Verifikation.\n");
        write(new File(docsDir, "GUI_SPECIFICATION.md"), "# GUI Specification\nSpezifikation des SDL3 / Dear ImGui Windowing-Systems.\n");
        write(new File(docsDir, "MATHEMATICAL_PROVENANCE.md"), "# Mathematical Provenance\nMathematische Herleitung und Nachweise.\n");
    }

    private static void copyAssetTree(Context context, String assetPath, File destination) throws IOException {
        String[] children = context.getAssets().list(assetPath);
        if (children == null) throw new IOException("APK-Asset fehlt: " + assetPath);
        if (children.length == 0) {
            File parent = destination.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Ordnerfehler: " + parent);
            try (InputStream in = new BufferedInputStream(context.getAssets().open(assetPath));
                 FileOutputStream file = new FileOutputStream(destination);
                 BufferedOutputStream out = new BufferedOutputStream(file)) {
                byte[] buffer = new byte[64 * 1024]; int count;
                while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
            }
            return;
        }
        if (!destination.isDirectory() && !destination.mkdirs()) throw new IOException("Ordnerfehler: " + destination);
        for (String child : children) copyAssetTree(context, assetPath + "/" + child, new File(destination, child));
    }

    private static void createRust(File project, String name, boolean gui) throws IOException {
        File src = new File(project, "src");
        if (!src.mkdirs()) throw new IOException("Rust-src-Ordner kann nicht erstellt werden.");
        String crate = sanitize(name).replace('-', '_');
        write(new File(project, "Cargo.toml"), "[package]\nname = \"" + crate + "\"\nversion = \"0.1.0\"\nedition = \"2024\"\n\n"
                + "[lib]\nname = \"kr_language\"\npath = \"src/lib.rs\"\ncrate-type = [\"staticlib\"]\n\n[profile.release]\nopt-level = 2\nlto = true\n");
        write(new File(src, "lib.rs"), gui ? rustGui() : rustConsole());
    }

    private static void createGo(File project, String name, boolean gui) throws IOException {
        write(new File(project, "go.mod"), "module kr.local/" + sanitize(name) + "\n\ngo 1.24\n");
        write(new File(project, "main.go"), gui ? goGui() : goConsole());
    }

    private static String krConsole() {
        return "#include <iostream>\n\n"
                + "int main() {\n"
                + "    std::cout << \"Hallo von KR Unified Language (.kr)!\" << std::endl;\n"
                + "    std::cout << \"Plattformunabhaengig fuer Android, Windows und Linux kompiliert.\" << std::endl;\n"
                + "    return 0;\n"
                + "}\n";
    }

    private static String krGui() {
        return "#include <SDL3/SDL.h>\n"
                + "#include <SDL3/SDL_main.h>\n\n"
                + "int main(int argc, char* argv[]) {\n"
                + "    (void)argc; (void)argv;\n"
                + "    if (!SDL_Init(SDL_INIT_VIDEO)) return 1;\n"
                + "    SDL_Window* window = SDL_CreateWindow(\"KR Unified GUI - SDL3\", 900, 560, 0);\n"
                + "    SDL_Renderer* renderer = SDL_CreateRenderer(window, nullptr);\n"
                + "    if (!window || !renderer) { SDL_Quit(); return 2; }\n"
                + "    bool running = true;\n"
                + "    SDL_Event event;\n"
                + "    while (running) {\n"
                + "        while (SDL_PollEvent(&event)) {\n"
                + "            if (event.type == SDL_EVENT_QUIT) running = false;\n"
                + "        }\n"
                + "        SDL_SetRenderDrawColor(renderer, 24, 76, 135, 255);\n"
                + "        SDL_RenderClear(renderer);\n"
                + "        SDL_RenderPresent(renderer);\n"
                + "        SDL_Delay(16);\n"
                + "    }\n"
                + "    SDL_DestroyRenderer(renderer);\n"
                + "    SDL_DestroyWindow(window);\n"
                + "    SDL_Quit();\n"
                + "    return 0;\n"
                + "}\n";
    }

    private static String krFusionConsole() {
        return "#include <iostream>\n"
                + "#include <cstdint>\n\n"
                + "// --- C++20 Bereich ---\n"
                + "int cpp_calculate(int a, int b) {\n"
                + "    return a * b + 42;\n"
                + "}\n\n"
                + "int cpp_square(int x) {\n"
                + "    return x * x;\n"
                + "}\n\n"
                + "// --- Rust Bereich ---\n"
                + "fn rust_verify(val: i32) -> bool {\n"
                + "    let check = val > 50;\n"
                + "    if check {\n"
                + "        let sq = cpp_square(4);\n"
                + "        go_notify(sq);\n"
                + "    }\n"
                + "    check\n"
                + "}\n\n"
                + "// --- Go Bereich ---\n"
                + "func go_notify(val int32) {\n"
                + "    println(\"Go empfaengt Benachrichtigung von Rust: Wert =\", val)\n"
                + "}\n\n"
                + "func go_process(val int32, ok bool) {\n"
                + "    if ok {\n"
                + "        println(\"Go verarbeitet Ergebnis: gueltig! Wert =\", val)\n"
                + "        extra := cpp_calculate(2, 5)\n"
                + "        println(\"Go rief C++ auf: Zusatzwert =\", extra)\n"
                + "        valid := rust_verify(extra)\n"
                + "        println(\"Go prueft Zusatzwert mit Rust:\", valid)\n"
                + "    } else {\n"
                + "        println(\"Go: Ungueltiger Wert\")\n"
                + "    }\n"
                + "}\n\n"
                + "// --- Gemeinsamer Einstiegspunkt ---\n"
                + "int main() {\n"
                + "    std::cout << \"=== KR Unified Language: Sprachfusion C++ / Rust / Go ===\" << std::endl;\n"
                + "    int berechnet = cpp_calculate(3, 4);\n"
                + "    std::cout << \"[C++] Ergebnis berechnet: \" << berechnet << std::endl;\n"
                + "    bool geprueft = rust_verify(berechnet);\n"
                + "    std::cout << \"[Rust] Verifizierung erfolgt: \" << (geprueft ? \"ERFOLGREICH\" : \"FEHLGESCHLAGEN\") << std::endl;\n"
                + "    go_process(berechnet, geprueft);\n"
                + "    std::cout << \"[KR] Alle Sprachgrenzen erfolgreich ueberschritten!\" << std::endl;\n"
                + "    return 0;\n"
                + "}\n";
    }

    private static String krFusionGui() {
        return "#include <SDL3/SDL.h>\n"
                + "#include <SDL3/SDL_main.h>\n"
                + "#include <iostream>\n"
                + "#include <cstdint>\n\n"
                + "// --- Rust Bereich ---\n"
                + "fn rust_calc_color(frame: i32) -> i32 {\n"
                + "    ((frame * 2) % 255)\n"
                + "}\n\n"
                + "fn rust_validate_tick(tick: i32) -> bool {\n"
                + "    tick >= 0\n"
                + "}\n\n"
                + "// --- Go Bereich ---\n"
                + "func go_log_status(frame int32, ok bool) {\n"
                + "    if ok && frame % 60 == 0 {\n"
                + "        println(\"Go: GUI Frame =\", frame, \"aktiv\")\n"
                + "    }\n"
                + "}\n\n"
                + "// --- C++ Bereich mit SDL3 GUI ---\n"
                + "int main(int argc, char* argv[]) {\n"
                + "    (void)argc; (void)argv;\n"
                + "    if (!SDL_Init(SDL_INIT_VIDEO)) return 1;\n"
                + "    SDL_Window* window = SDL_CreateWindow(\"KR Unified GUI - C++ / Rust / Go\", 900, 560, 0);\n"
                + "    SDL_Renderer* renderer = SDL_CreateRenderer(window, nullptr);\n"
                + "    if (!window || !renderer) { SDL_Quit(); return 2; }\n"
                + "    bool running = true;\n"
                + "    SDL_Event event;\n"
                + "    int frame = 0;\n"
                + "    while (running) {\n"
                + "        while (SDL_PollEvent(&event)) {\n"
                + "            if (event.type == SDL_EVENT_QUIT) running = false;\n"
                + "        }\n"
                + "        int blue = rust_calc_color(frame);\n"
                + "        bool valid = rust_validate_tick(frame);\n"
                + "        go_log_status(frame, valid);\n"
                + "        SDL_SetRenderDrawColor(renderer, 24, 76, (uint8_t)blue, 255);\n"
                + "        SDL_RenderClear(renderer);\n"
                + "        SDL_RenderPresent(renderer);\n"
                + "        frame++;\n"
                + "        SDL_Delay(16);\n"
                + "    }\n"
                + "    SDL_DestroyRenderer(renderer);\n"
                + "    SDL_DestroyWindow(window);\n"
                + "    SDL_Quit();\n"
                + "    return 0;\n"
                + "}\n";
    }

    private static String rustConsole() {
        return "#[unsafe(no_mangle)]\npub extern \"C\" fn KrLanguageMain() -> i32 {\n"
                + "    println!(\"Hallo von Rust im KR Compiler!\");\n"
                + "    println!(\"APK, Windows EXE und Linux ELF verwenden denselben Quelltext.\");\n    0\n}\n";
    }

    private static String rustGui() {
        return "use std::ffi::{c_char, c_int, c_void};\n\n"
                + "#[repr(C, align(8))]\nstruct SdlEvent { data: [u8; 128] }\n\n"
                + "unsafe extern \"C\" {\n"
                + "    fn SDL_Init(flags: u32) -> bool;\n    fn SDL_Quit();\n"
                + "    fn SDL_CreateWindow(title: *const c_char, w: c_int, h: c_int, flags: u64) -> *mut c_void;\n"
                + "    fn SDL_DestroyWindow(window: *mut c_void);\n    fn SDL_CreateRenderer(window: *mut c_void, name: *const c_char) -> *mut c_void;\n"
                + "    fn SDL_DestroyRenderer(renderer: *mut c_void);\n    fn SDL_SetRenderDrawColor(renderer: *mut c_void, r: u8, g: u8, b: u8, a: u8) -> bool;\n"
                + "    fn SDL_RenderClear(renderer: *mut c_void) -> bool;\n    fn SDL_RenderPresent(renderer: *mut c_void) -> bool;\n"
                + "    fn SDL_PollEvent(event: *mut SdlEvent) -> bool;\n    fn SDL_Delay(ms: u32);\n}\n\n"
                + "#[unsafe(no_mangle)]\npub extern \"C\" fn KrLanguageMain() -> i32 { unsafe {\n"
                + "    if !SDL_Init(0x20) { return 1; }\n"
                + "    let window = SDL_CreateWindow(c\"KR Rust GUI\".as_ptr(), 900, 560, 0x20);\n"
                + "    let renderer = SDL_CreateRenderer(window, std::ptr::null());\n"
                + "    if window.is_null() || renderer.is_null() { SDL_Quit(); return 2; }\n"
                + "    let mut running = true; let mut event = SdlEvent { data: [0; 128] };\n"
                + "    while running {\n        while SDL_PollEvent(&mut event) { let kind = *(event.data.as_ptr() as *const u32); if kind == 0x100 { running = false; } }\n"
                + "        SDL_SetRenderDrawColor(renderer, 18, 92, 82, 255); SDL_RenderClear(renderer); SDL_RenderPresent(renderer); SDL_Delay(16);\n    }\n"
                + "    SDL_DestroyRenderer(renderer); SDL_DestroyWindow(window); SDL_Quit(); 0\n} }\n";
    }

    private static String goConsole() {
        return "package main\n\n/*\n#include <stdint.h>\n*/\nimport \"C\"\nimport \"fmt\"\n\n"
                + "//export KrLanguageMain\nfunc KrLanguageMain() C.int {\n"
                + "\tfmt.Println(\"Hallo von Go im KR Compiler!\")\n"
                + "\tfmt.Println(\"APK, Windows EXE und Linux ELF verwenden denselben Quelltext.\")\n\treturn 0\n}\n\nfunc main() {}\n";
    }

    private static String goGui() {
        return "package main\n\n/*\n#include <stdint.h>\n#include <stdbool.h>\n#include <stddef.h>\n#include <stdlib.h>\n"
                + "typedef struct SDL_Window SDL_Window;\ntypedef struct SDL_Renderer SDL_Renderer;\n"
                + "typedef union { uint32_t type; unsigned char padding[128]; } KR_SDL_Event;\n"
                + "bool SDL_Init(uint32_t); void SDL_Quit(void);\n"
                + "SDL_Window* SDL_CreateWindow(const char*, int, int, uint64_t); void SDL_DestroyWindow(SDL_Window*);\n"
                + "SDL_Renderer* SDL_CreateRenderer(SDL_Window*, const char*); void SDL_DestroyRenderer(SDL_Renderer*);\n"
                + "bool SDL_SetRenderDrawColor(SDL_Renderer*, uint8_t, uint8_t, uint8_t, uint8_t); bool SDL_RenderClear(SDL_Renderer*); bool SDL_RenderPresent(SDL_Renderer*);\n"
                + "bool SDL_PollEvent(KR_SDL_Event*); void SDL_Delay(uint32_t);\n*/\nimport \"C\"\nimport \"unsafe\"\n\n"
                + "//export KrLanguageMain\nfunc KrLanguageMain() C.int {\n"
                + "\tif !C.SDL_Init(0x20) { return 1 }\n\ttitle := C.CString(\"KR Go GUI\"); defer C.free(unsafe.Pointer(title))\n"
                + "\twindow := C.SDL_CreateWindow(title, 900, 560, 0x20); renderer := C.SDL_CreateRenderer(window, nil)\n"
                + "\tif window == nil || renderer == nil { C.SDL_Quit(); return 2 }\n\trunning := true; var event C.KR_SDL_Event\n"
                + "\tfor running {\n\t\tfor C.SDL_PollEvent(&event) { if *(*C.uint32_t)(unsafe.Pointer(&event)) == 0x100 { running = false } }\n"
                + "\t\tC.SDL_SetRenderDrawColor(renderer, 24, 76, 135, 255); C.SDL_RenderClear(renderer); C.SDL_RenderPresent(renderer); C.SDL_Delay(16)\n\t}\n"
                + "\tC.SDL_DestroyRenderer(renderer); C.SDL_DestroyWindow(window); C.SDL_Quit(); return 0\n}\n\n"
                + "//export SDL_main\nfunc SDL_main(argc C.int, argv **C.char) C.int { return KrLanguageMain() }\n\nfunc main() {}\n";
    }

    private static String readme(Language language, boolean gui) {
        return "# KR " + (language == Language.RUST ? "Rust" : "Go") + " " + (gui ? "GUI" : "Konsole") + "\n\n"
                + "Dieses Projekt wird unverändert als Android-APK (ARM64), Windows-EXE (x64) und Linux-ELF (x64) gebaut.\n"
                + (gui ? "Die Oberfläche verwendet SDL3 aus dem integrierten KR-Bibliothekskatalog.\n" : "Es werden keine externen Bibliotheken benötigt.\n");
    }

    private static String readmeKr(KrTemplate template) {
        return "# " + template.displayName + " (.kr)\n\n"
                + "Dieses Projekt verwendet die KR Unified Language (.kr) und wird als Android-APK (ARM64), Windows-EXE (x64) und Linux-ELF (x64) kompiliert.\n\n"
                + "Quellcode in C++20, Rust und Go kann innerhalb derselben .kr-Datei frei kombiniert und ohne manuelle FFI-Definitionen sprachübergreifend aufgerufen werden.\n\n"
                + (template.isGui ? "Die Grafikausgabe erfolgt portabel über das integrierte SDL3.\n" : "Ausgaben erfolgen über die Standard-Konsole der Zielplattform.\n");
    }

    private static String sanitize(String value) {
        String result = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        result = result.replaceAll("^-|-$", "");
        return result.isEmpty() ? "kr-project" : result;
    }

    private static void write(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Ordner kann nicht erstellt werden: " + parent);
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
    }
}
