package kr.compile;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Creates dependency-free Rust and Go starter projects for all KR targets. */
final class LanguageProjectManager {
    enum Language { RUST, GO }

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
        if (language == Language.RUST) createRust(project, name, gui); else createGo(project, name, gui);
        if (gui) write(new File(project, "kr-gui-target.txt"), "sdl3\n");
        write(new File(project, "README.md"), readme(language, gui));
        return project;
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
