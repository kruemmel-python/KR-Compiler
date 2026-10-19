package kr.compile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class KrPipeline {

    public static final class PipelineResult {
        public final boolean success;
        public final List<KrDiagnostic> diagnostics = new ArrayList<>();
        public final List<KrSegment> segments = new ArrayList<>();
        public final Set<KrLanguage> languagesUsed = new HashSet<>();
        public final KrSymbolTable symbolTable;
        public final KrCodeGenerator.Result codeResult;
        public final KrComputePlan computePlan;

        PipelineResult(boolean success, KrSymbolTable symbolTable, KrCodeGenerator.Result codeResult,
                       KrComputePlan computePlan) {
            this.success = success;
            this.symbolTable = symbolTable;
            this.codeResult = codeResult;
            this.computePlan = computePlan;
        }
    }

    private KrPipeline() {}

    public static List<File> findKrFiles(File projectDir) {
        List<File> result = new ArrayList<>();
        collectKrFiles(projectDir, result);
        Collections.sort(result, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return result;
    }

    private static void collectKrFiles(File dir, List<File> result) {
        if (dir == null || !dir.exists()) return;
        if (dir.isFile()) {
            if (dir.getName().toLowerCase().endsWith(".kr")) result.add(dir);
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.getName().equals("KR_BUILD") || child.getName().equals(".git") || child.getName().equals(".gradle")) continue;
                collectKrFiles(child, result);
            }
        }
    }

    public static PipelineResult process(File projectDir, File buildDir, boolean isGui) throws IOException {
        File configFile = KrProjectConfig.findProjectConfigFile(projectDir);
        KrProjectConfig config = KrProjectConfig.parse(configFile, projectDir.getName(), isGui);
        boolean actualGui = config.isGui || isGui;

        List<File> krFiles = findKrFiles(projectDir);
        if (krFiles.isEmpty()) {
            throw new IOException("Keine .kr-Quelldateien im Projektordner gefunden.");
        }

        List<KrSegment> allSegments = new ArrayList<>();
        List<KrDiagnostic> allDiagnostics = new ArrayList<>();
        KrSymbolTable symbolTable = new KrSymbolTable();

        for (File krFile : krFiles) {
            String relPath = projectDir.toPath().relativize(krFile.toPath()).toString().replace('\\', '/');
            String content = readFile(krFile);
            KrParser parser = new KrParser(relPath, content);
            List<KrSegment> segments = parser.parse();
            allSegments.addAll(segments);
            allDiagnostics.addAll(parser.getDiagnostics());

            for (KrSegment seg : segments) {
                for (KrFunction fn : seg.declaredFunctions) {
                    symbolTable.registerFunction(fn);
                }
            }
        }

        // Check for symbol table errors
        symbolTable.resolveCalls(allSegments);
        allDiagnostics.addAll(symbolTable.getDiagnostics());

        KrComputePlan computePlan = KrAdaptiveComputeAnalyzer.analyze(allSegments, config.computeMode);

        // Check if there are any fatal diagnostics
        boolean hasErrors = false;
        for (KrDiagnostic d : allDiagnostics) {
            if (d.severity == KrDiagnostic.Severity.ERROR) {
                hasErrors = true;
                break;
            }
        }

        if (hasErrors) {
            PipelineResult res = new PipelineResult(false, symbolTable, null, computePlan);
            res.diagnostics.addAll(allDiagnostics);
            return res;
        }

        // Generate target code
        KrCodeGenerator generator = new KrCodeGenerator(symbolTable, allSegments, buildDir, actualGui);
        KrCodeGenerator.Result codeResult = generator.generate(config.vcpkgDependencies);
        File computeRuntime = KrAdaptiveComputeCodeGenerator.generate(buildDir, computePlan);
        codeResult.cppSources.add(computeRuntime);

        File computePlanFile = new File(buildDir, "adaptive_compute_plan.json");
        try (FileOutputStream stream = new FileOutputStream(computePlanFile)) {
            stream.write(computePlan.toJson().getBytes(StandardCharsets.UTF_8));
        }

        PipelineResult result = new PipelineResult(true, symbolTable, codeResult, computePlan);
        result.diagnostics.addAll(allDiagnostics);
        result.segments.addAll(allSegments);

        if (codeResult.hasCpp) result.languagesUsed.add(KrLanguage.CPP);
        if (codeResult.hasRust) result.languagesUsed.add(KrLanguage.RUST);
        if (codeResult.hasGo) result.languagesUsed.add(KrLanguage.GO);

        return result;
    }

    public static DiagnosticParser.Item mapDiagnostic(DiagnosticParser.Item original, List<KrSegment> segments) {
        if (original == null || original.file == null) return original;
        String f = original.file.replace('\\', '/');

        // Check if error comes from a generated file
        if (f.endsWith("kr_cpp.cpp") || f.contains("rust_module") || f.contains("go_module")) {
            for (KrSegment seg : segments) {
                if ((f.endsWith("kr_cpp.cpp") && seg.language == KrLanguage.CPP) ||
                    (f.contains("rust_module") && seg.language == KrLanguage.RUST) ||
                    (f.contains("go_module") && seg.language == KrLanguage.GO)) {
                    // Map line number relative to segment
                    int mappedLine = Math.max(1, seg.startLine + Math.max(0, original.line - 1));
                    return new DiagnosticParser.Item(seg.file, mappedLine, original.column,
                            original.severity, original.message, original.rawLine);
                }
            }
        }

        return original;
    }

    private static String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) >= 0) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }
}
