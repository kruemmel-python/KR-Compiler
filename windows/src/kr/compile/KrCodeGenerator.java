package kr.compile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KrCodeGenerator {

    private final KrSymbolTable symbolTable;
    private final List<KrSegment> segments;
    private final File buildDir;
    private final boolean isGui;
    private final KrSourceMap sourceMap = new KrSourceMap();

    public KrCodeGenerator(KrSymbolTable symbolTable, List<KrSegment> segments, File buildDir, boolean isGui) {
        this.symbolTable = symbolTable;
        this.segments = segments;
        this.buildDir = buildDir;
        this.isGui = isGui;
    }

    public static final class Result {
        public final boolean hasCpp;
        public final boolean hasRust;
        public final boolean hasGo;
        public final File cppSource;
        public final List<File> cppSources = new ArrayList<>();
        public final File rustDir;
        public final File goDir;
        public final File bridgeHeader;
        public final Set<String> vcpkgDependencies = new LinkedHashSet<>();
        public final KrSourceMap sourceMap;

        Result(boolean hasCpp, boolean hasRust, boolean hasGo, File cppSource, List<File> cppSources,
               File rustDir, File goDir, File bridgeHeader, Set<String> vcpkgDependencies) {
            this(hasCpp, hasRust, hasGo, cppSource, cppSources, rustDir, goDir, bridgeHeader, vcpkgDependencies, new KrSourceMap());
        }

        Result(boolean hasCpp, boolean hasRust, boolean hasGo, File cppSource, List<File> cppSources,
               File rustDir, File goDir, File bridgeHeader, Set<String> vcpkgDependencies, KrSourceMap sourceMap) {
            this.hasCpp = hasCpp;
            this.hasRust = hasRust;
            this.hasGo = hasGo;
            this.cppSource = cppSource;
            if (cppSources != null) this.cppSources.addAll(cppSources);
            else if (cppSource != null) this.cppSources.add(cppSource);
            this.rustDir = rustDir;
            this.goDir = goDir;
            this.bridgeHeader = bridgeHeader;
            if (vcpkgDependencies != null) this.vcpkgDependencies.addAll(vcpkgDependencies);
            this.sourceMap = sourceMap != null ? sourceMap : new KrSourceMap();
        }
    }

    public Result generate() throws IOException {
        return generate(Collections.emptySet());
    }

    public Result generate(Set<String> projectDependencies) throws IOException {
        boolean hasCpp = false;
        boolean hasRust = false;
        boolean hasGo = false;

        for (KrSegment seg : segments) {
            if (seg.language == KrLanguage.CPP) hasCpp = true;
            else if (seg.language == KrLanguage.RUST) hasRust = true;
            else if (seg.language == KrLanguage.GO) hasGo = true;
        }

        if (!buildDir.isDirectory() && !buildDir.mkdirs()) {
            throw new IOException("Build-Verzeichnis kann nicht erstellt werden: " + buildDir);
        }

        // 1. Generate kr_bridge.h
        File bridgeHeader = new File(buildDir, "kr_bridge.h");
        generateBridgeHeader(bridgeHeader);

        // 2. Generate C++ sources (modular + fallback main)
        File cppSource = null;
        List<File> cppSources = new ArrayList<>();
        if (hasCpp || hasRust || hasGo) {
            cppSource = new File(buildDir, "kr_cpp.cpp");
            cppSources = generateCppSources(cppSource, hasCpp);
        }

        // 3. Generate Rust crate if Rust is used
        File rustDir = null;
        if (hasRust) {
            rustDir = new File(buildDir, "rust_module");
            generateRustCrate(rustDir);
        }

        // 4. Generate Go module if Go is used
        File goDir = null;
        if (hasGo) {
            goDir = new File(buildDir, "go_module");
            generateGoModule(goDir);
        }

        // 5. Save source map
        File sourceMapFile = new File(buildDir, "source_map.json");
        try {
            sourceMap.save(sourceMapFile);
        } catch (Exception ignored) {}

        return new Result(hasCpp, hasRust, hasGo, cppSource, cppSources, rustDir, goDir, bridgeHeader, projectDependencies, sourceMap);
    }

    private void generateBridgeHeader(File destination) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("// Automatisch generierter KR Unified Language ABI Header\n");
        sb.append("#pragma once\n");
        sb.append("#include <stdint.h>\n");
        sb.append("#include <stdbool.h>\n");
        sb.append("#include <stddef.h>\n\n");
        sb.append("#ifdef __cplusplus\nextern \"C\" {\n#endif\n\n");

        for (KrFunction fn : symbolTable.getAllExportedFunctions()) {
            sb.append(fn.returnType.toCType()).append(" ").append(fn.name).append("(");
            if (fn.parameters.isEmpty()) {
                sb.append("void");
            } else {
                for (int i = 0; i < fn.parameters.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(fn.parameters.get(i).type.toCType()).append(" ").append(fn.parameters.get(i).name);
                }
            }
            sb.append(");\n");
        }

        sb.append("\n#ifdef __cplusplus\n}\n#endif\n");
        writeText(destination, sb.toString());
    }

    private List<File> generateCppSources(File destination, boolean hasCppSegments) throws IOException {
        List<File> generated = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("// Automatisch generierte C++20-Quelle für KR Unified Language\n");
        sb.append("#include \"kr_bridge.h\"\n");
        sb.append("#include \"kr_adaptive_compute.h\"\n");
        sb.append("#include <iostream>\n");
        sb.append("#include <string>\n\n");

        // Declarations for Rust and Go functions callable from C++
        sb.append("#ifdef __cplusplus\nextern \"C\" {\n#endif\n");
        for (KrFunction fn : symbolTable.getCppCallsRust().values()) {
            appendFunctionSignature(sb, fn);
        }
        for (KrFunction fn : symbolTable.getCppCallsGo().values()) {
            appendFunctionSignature(sb, fn);
        }
        sb.append("#ifdef __cplusplus\n}\n#endif\n\n");

        if (hasCppSegments) {
            for (KrSegment seg : segments) {
                if (seg.language == KrLanguage.CPP) {
                    sb.append("\n// --- Segment aus ").append(seg.file).append(":").append(seg.startLine).append(" ---\n");
                    int curLine = countLines(sb.toString()) + 1;
                    sb.append(seg.source).append("\n");
                    int endLine = countLines(sb.toString());
                    sourceMap.addMapping(new KrSourceMap.SegmentMapping(
                            "kr_cpp.cpp", seg.file, curLine, endLine, seg.startLine, seg.endLine, "CPP", extractFunctionNames(seg.declaredFunctions)
                    ));
                }
            }
        }

        // If main is in Rust or Go, create standard C/C++ main entry point calling KrLanguageMain
        KrFunction main = symbolTable.getMainFunction();
        if (main != null && (main.definingLanguage == KrLanguage.RUST || main.definingLanguage == KrLanguage.GO)) {
            sb.append("\n#ifdef __cplusplus\nextern \"C\" {\n#endif\n");
            sb.append("int KrLanguageMain(void);\n");
            sb.append("#ifdef __cplusplus\n}\n#endif\n\n");
            sb.append("int main(int argc, char** argv) {\n");
            sb.append("    (void)argc; (void)argv;\n");
            sb.append("    return KrLanguageMain();\n");
            sb.append("}\n");
        } else if (main == null) {
            sb.append("\nint main(int argc, char** argv) {\n");
            sb.append("    (void)argc; (void)argv;\n");
            sb.append("    std::cout << \"KR Unified Language Programm gestartet.\" << std::endl;\n");
            sb.append("    return 0;\n");
            sb.append("}\n");
        }

        writeText(destination, sb.toString());
        generated.add(destination);

        // Also generate individual modular C++ files in a "modules" folder if multiple .kr files exist
        Map<String, List<KrSegment>> fileMap = new java.util.LinkedHashMap<>();
        for (KrSegment seg : segments) {
            if (seg.language == KrLanguage.CPP) {
                fileMap.computeIfAbsent(seg.file, k -> new ArrayList<>()).add(seg);
            }
        }

        if (fileMap.size() > 1) {
            generated.clear(); // Verwende modulare TUs statt monolithischem kr_cpp.cpp
            File modulesDir = new File(buildDir, "modules");
            if (!modulesDir.isDirectory()) modulesDir.mkdirs();
            for (Map.Entry<String, List<KrSegment>> entry : fileMap.entrySet()) {
                String safeName = entry.getKey().replace('\\', '_').replace('/', '_');
                if (safeName.endsWith(".kr")) safeName = safeName.substring(0, safeName.length() - 3);
                File modFile = new File(modulesDir, safeName + ".cpp");
                StringBuilder mb = new StringBuilder();
                mb.append("// Automatisch generiertes C++20-Modul aus ").append(entry.getKey()).append("\n");
                mb.append("#include \"../kr_bridge.h\"\n");
                mb.append("#include \"../kr_adaptive_compute.h\"\n");
                mb.append("#include <iostream>\n#include <string>\n\n");
                for (KrSegment s : entry.getValue()) {
                    int curLine = countLines(mb.toString()) + 1;
                    mb.append(s.source).append("\n");
                    int endLine = countLines(mb.toString());
                    sourceMap.addMapping(new KrSourceMap.SegmentMapping(
                            "modules/" + modFile.getName(), s.file, curLine, endLine, s.startLine, s.endLine, "CPP", extractFunctionNames(s.declaredFunctions)
                    ));
                }
                writeText(modFile, mb.toString());
                generated.add(modFile);
            }

            if (main == null || main.definingLanguage == KrLanguage.RUST || main.definingLanguage == KrLanguage.GO) {
                File runnerFile = new File(modulesDir, "kr_runner.cpp");
                StringBuilder rb = new StringBuilder();
                rb.append("// KR Unified Entry Runner\n");
                rb.append("#include \"../kr_bridge.h\"\n");
                rb.append("#include <iostream>\n\n");
                if (main != null && (main.definingLanguage == KrLanguage.RUST || main.definingLanguage == KrLanguage.GO)) {
                    rb.append("#ifdef __cplusplus\nextern \"C\" {\n#endif\n");
                    rb.append("int KrLanguageMain(void);\n");
                    rb.append("#ifdef __cplusplus\n}\n#endif\n\n");
                    rb.append("int main(int argc, char** argv) {\n");
                    rb.append("    (void)argc; (void)argv;\n");
                    rb.append("    return KrLanguageMain();\n");
                    rb.append("}\n");
                } else {
                    rb.append("int main(int argc, char** argv) {\n");
                    rb.append("    (void)argc; (void)argv;\n");
                    rb.append("    std::cout << \"KR Unified Language Programm gestartet.\" << std::endl;\n");
                    rb.append("    return 0;\n");
                    rb.append("}\n");
                }
                writeText(runnerFile, rb.toString());
                generated.add(runnerFile);
            }
        }

        return generated;
    }

    private void generateRustCrate(File crateDir) throws IOException {
        File srcDir = new File(crateDir, "src");
        if (!srcDir.isDirectory() && !srcDir.mkdirs()) {
            throw new IOException("Rust-Verzeichnis konnte nicht erstellt werden: " + srcDir);
        }

        // Cargo.toml
        String cargoToml = "[package]\nname = \"kr_rust_module\"\nversion = \"0.1.0\"\nedition = \"2021\"\n\n"
                + "[lib]\nname = \"kr_rust\"\npath = \"src/lib.rs\"\ncrate-type = [\"staticlib\"]\n\n"
                + "[profile.release]\nopt-level = 2\nlto = true\npanic = \"abort\"\n";
        writeText(new File(crateDir, "Cargo.toml"), cargoToml);

        // src/lib.rs
        StringBuilder sb = new StringBuilder();
        sb.append("// Automatisch generiertes Rust-Modul für KR Unified Language\n");
        sb.append("#![allow(non_snake_case, unused_unsafe, dead_code, unused_variables, unused_imports)]\n\n");
        sb.append("use std::ffi::{c_char, c_int, c_void};\n\n");

        // External declarations for C++ and Go functions called from Rust
        Set<KrFunction> externCalls = new HashSet<>();
        externCalls.addAll(symbolTable.getRustCallsCpp().values());
        externCalls.addAll(symbolTable.getRustCallsGo().values());

        if (!externCalls.isEmpty()) {
            sb.append("unsafe extern \"C\" {\n");
            for (KrFunction fn : externCalls) {
                sb.append("    #[link_name = \"").append(fn.name).append("\"]\n");
                sb.append("    fn __kr_raw_").append(fn.name).append("(");
                for (int i = 0; i < fn.parameters.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(fn.parameters.get(i).name).append(": ").append(fn.parameters.get(i).type.toRustType());
                }
                sb.append(") -> ").append(fn.returnType.toRustType()).append(";\n");
            }
            sb.append("}\n\n");

            // Safe inline wrappers
            for (KrFunction fn : externCalls) {
                sb.append("#[inline(always)]\n");
                sb.append("pub fn ").append(fn.name).append("(");
                for (int i = 0; i < fn.parameters.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(fn.parameters.get(i).name).append(": ").append(fn.parameters.get(i).type.toRustType());
                }
                sb.append(")");
                if (fn.returnType.kind != KrType.Kind.VOID) {
                    sb.append(" -> ").append(fn.returnType.toRustType());
                }
                sb.append(" {\n    unsafe { __kr_raw_").append(fn.name).append("(");
                for (int i = 0; i < fn.parameters.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(fn.parameters.get(i).name);
                }
                sb.append(") }\n}\n\n");
            }
        }

        // Rust segments
        for (KrSegment seg : segments) {
            if (seg.language == KrLanguage.RUST) {
                sb.append("\n// --- Segment aus ").append(seg.file).append(":").append(seg.startLine).append(" ---\n");
                String segCode = seg.source;
                for (KrFunction fn : seg.declaredFunctions) {
                    if (symbolTable.isExported(fn.name)) {
                        if (!segCode.contains("extern \"C\" fn " + fn.name) && !segCode.contains("extern \"C\"\nfn " + fn.name)) {
                            segCode = segCode.replaceAll("(?m)^([ \t]*)(?:pub[ \t]+)?fn[ \t]+" + fn.name + "\\b",
                                    "$1#[no_mangle]\n$1pub extern \"C\" fn " + fn.name);
                        } else if (!segCode.contains("no_mangle")) {
                            segCode = segCode.replaceAll("(?m)^([ \t]*)(?:pub[ \t]+)?extern[ \t]+\"C\"[ \t]+fn[ \t]+" + fn.name + "\\b",
                                    "$1#[no_mangle]\n$1pub extern \"C\" fn " + fn.name);
                        }
                    }
                }
                int curLine = countLines(sb.toString()) + 1;
                sb.append(segCode).append("\n");
                int endLine = countLines(sb.toString());
                sourceMap.addMapping(new KrSourceMap.SegmentMapping(
                        "rust_module/src/lib.rs", seg.file, curLine, endLine, seg.startLine, seg.endLine, "RUST", extractFunctionNames(seg.declaredFunctions)
                ));
            }
        }

        KrFunction main = symbolTable.getMainFunction();
        if (main != null && main.definingLanguage == KrLanguage.RUST) {
            sb.append("\n#[no_mangle]\npub extern \"C\" fn KrLanguageMain() -> i32 {\n    main();\n    0\n}\n");
        }

        writeText(new File(srcDir, "lib.rs"), sb.toString());
    }

    private void generateGoModule(File goDir) throws IOException {
        if (!goDir.isDirectory() && !goDir.mkdirs()) {
            throw new IOException("Go-Verzeichnis konnte nicht erstellt werden: " + goDir);
        }

        // go.mod
        writeText(new File(goDir, "go.mod"), "module kr.local/unified\n\ngo 1.24\n");

        // 1. Collect user imports and clean segments
        Set<String> userImports = new LinkedHashSet<>();
        List<CleanedGoSegment> cleanedSegments = new ArrayList<>();
        for (KrSegment seg : segments) {
            if (seg.language == KrLanguage.GO) {
                String code = seg.source;
                // Strip package statement
                code = code.replaceAll("(?m)^[ \t]*package[ \t]+[a-zA-Z0-9_]+[ \t]*\r?\n?", "");
                // Extract multi-line import (...)
                Matcher multiImport = Pattern.compile("(?s)[ \t]*import[ \t]*\\((.*?)\\)").matcher(code);
                while (multiImport.find()) {
                    String inside = multiImport.group(1);
                    for (String line : inside.split("\n")) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) userImports.add(trimmed);
                    }
                }
                code = code.replaceAll("(?s)[ \t]*import[ \t]*\\(.*?\\)\r?\n?", "");
                // Extract single-line import "..."
                Matcher singleImport = Pattern.compile("(?m)^[ \t]*import[ \t]+(\"[^\"]+\")").matcher(code);
                while (singleImport.find()) {
                    userImports.add(singleImport.group(1));
                }
                code = code.replaceAll("(?m)^[ \t]*import[ \t]+(\"[^\"]+\")[ \t]*\r?\n?", "");

                // Export Go functions called by C++ or Rust
                for (KrFunction fn : seg.declaredFunctions) {
                    if (symbolTable.isExported(fn.name) && !code.contains("//export " + fn.name)) {
                        code = code.replaceAll("(?m)^([ \t]*)func[ \t]+" + fn.name + "\\b",
                                "$1//export " + fn.name + "\n$1func " + fn.name);
                    }
                }
                cleanedSegments.add(new CleanedGoSegment(seg, code));
            }
        }

        // 2. Build main.go
        StringBuilder sb = new StringBuilder();
        sb.append("// Automatisch generiertes Go-Paket für KR Unified Language\n");
        sb.append("package main\n\n");

        // Cgo preamble
        sb.append("/*\n#include <stdint.h>\n#include <stdbool.h>\n#include <stdlib.h>\n");
        Set<KrFunction> externCalls = new HashSet<>();
        externCalls.addAll(symbolTable.getGoCallsCpp().values());
        externCalls.addAll(symbolTable.getGoCallsRust().values());

        for (KrFunction fn : externCalls) {
            sb.append(toCgoHeaderType(fn.returnType)).append(" ").append(fn.name).append("(");
            if (fn.parameters.isEmpty()) {
                sb.append("void");
            } else {
                for (int i = 0; i < fn.parameters.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(toCgoHeaderType(fn.parameters.get(i).type)).append(" ").append(fn.parameters.get(i).name);
                }
            }
            sb.append(");\n");
        }
        sb.append("*/\nimport \"C\"\nimport \"unsafe\"\nimport \"fmt\"\n");
        for (String imp : userImports) {
            if (!imp.equals("\"fmt\"") && !imp.equals("\"unsafe\"") && !imp.equals("\"C\"")) {
                sb.append("import ").append(imp).append("\n");
            }
        }
        sb.append("\nvar _ = unsafe.Pointer(nil)\nvar _ = fmt.Sprintf\n\n");

        // Go wrappers for calling C++ / Rust functions with native Go types
        for (KrFunction fn : externCalls) {
            sb.append("func ").append(fn.name).append("(");
            for (int i = 0; i < fn.parameters.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(fn.parameters.get(i).name).append(" ").append(fn.parameters.get(i).type.toGoFriendlyType());
            }
            sb.append(")");
            if (fn.returnType.kind != KrType.Kind.VOID) {
                sb.append(" ").append(fn.returnType.toGoFriendlyType());
            }
            sb.append(" {\n");

            sb.append("    ");
            if (fn.returnType.kind == KrType.Kind.BOOL) {
                sb.append("return C.").append(fn.name).append("(");
                for (int i = 0; i < fn.parameters.size(); i++) {
                    if (i > 0) sb.append(", ");
                    KrFunction.Parameter p = fn.parameters.get(i);
                    if (p.type.kind == KrType.Kind.BOOL) {
                        sb.append("(func(b bool) C.uint8_t { if b { return 1 }; return 0 })(").append(p.name).append(")");
                    } else {
                        sb.append("C.").append(p.type.toCType()).append("(").append(p.name).append(")");
                    }
                }
                sb.append(") != 0\n}\n\n");
            } else if (fn.returnType.kind != KrType.Kind.VOID) {
                sb.append("return ").append(fn.returnType.toGoFriendlyType()).append("(C.").append(fn.name).append("(");
                for (int i = 0; i < fn.parameters.size(); i++) {
                    if (i > 0) sb.append(", ");
                    KrFunction.Parameter p = fn.parameters.get(i);
                    if (p.type.kind == KrType.Kind.BOOL) {
                        sb.append("(func(b bool) C.uint8_t { if b { return 1 }; return 0 })(").append(p.name).append(")");
                    } else {
                        sb.append("C.").append(p.type.toCType()).append("(").append(p.name).append(")");
                    }
                }
                sb.append("))\n}\n\n");
            } else {
                sb.append("C.").append(fn.name).append("(");
                for (int i = 0; i < fn.parameters.size(); i++) {
                    if (i > 0) sb.append(", ");
                    KrFunction.Parameter p = fn.parameters.get(i);
                    if (p.type.kind == KrType.Kind.BOOL) {
                        sb.append("(func(b bool) C.uint8_t { if b { return 1 }; return 0 })(").append(p.name).append(")");
                    } else {
                        sb.append("C.").append(p.type.toCType()).append("(").append(p.name).append(")");
                    }
                }
                sb.append(")\n}\n\n");
            }
        }

        // Cleaned segments
        for (CleanedGoSegment cs : cleanedSegments) {
            sb.append("\n// --- Segment aus ").append(cs.segment.file).append(":").append(cs.segment.startLine).append(" ---\n");
            int curLine = countLines(sb.toString()) + 1;
            sb.append(cs.code).append("\n");
            int endLine = countLines(sb.toString());
            sourceMap.addMapping(new KrSourceMap.SegmentMapping(
                    "go_module/main.go", cs.segment.file, curLine, endLine, cs.segment.startLine, cs.segment.endLine, "GO", extractFunctionNames(cs.segment.declaredFunctions)
            ));
        }

        KrFunction mainFn = symbolTable.getMainFunction();
        if (mainFn != null && mainFn.definingLanguage == KrLanguage.GO) {
            sb.append("\n//export KrLanguageMain\nfunc KrLanguageMain() int32 {\n    main()\n    return 0\n}\n");
        } else if (mainFn == null || mainFn.definingLanguage != KrLanguage.GO) {
            sb.append("\nfunc main() {}\n");
        }

        writeText(new File(goDir, "main.go"), sb.toString());
    }

    private static final class CleanedGoSegment {
        final KrSegment segment;
        final String code;
        CleanedGoSegment(KrSegment segment, String code) {
            this.segment = segment;
            this.code = code;
        }
    }

    private static int countLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    private static List<String> extractFunctionNames(List<KrFunction> fns) {
        List<String> names = new ArrayList<>();
        if (fns != null) {
            for (KrFunction fn : fns) {
                if (fn != null && fn.name != null) {
                    names.add(fn.name);
                }
            }
        }
        return names;
    }

    private static String toCgoHeaderType(KrType type) {
        if (type.kind == KrType.Kind.BOOL) return "uint8_t";
        return type.toCType();
    }

    private void appendFunctionSignature(StringBuilder sb, KrFunction fn) {
        sb.append(fn.returnType.toCType()).append(" ").append(fn.name).append("(");
        if (fn.parameters.isEmpty()) {
            sb.append("void");
        } else {
            for (int i = 0; i < fn.parameters.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(fn.parameters.get(i).type.toCType()).append(" ").append(fn.parameters.get(i).name);
            }
        }
        sb.append(");\n");
    }

    private static void writeText(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
