package kr.compile;

import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class IdeCoreTests {
    @Test
    public void testAdaptiveComputeRuntimeGenerationCoversPortableOperations() throws Exception {
        File out = new File("build/test-adaptive-runtime");
        assertTrue(out.isDirectory() || out.mkdirs());
        File source = KrAdaptiveComputeCodeGenerator.generate(out,
                new KrComputePlan(KrComputePlan.Mode.VERIFY, java.util.Collections.emptyList()));
        String cpp = new String(java.nio.file.Files.readAllBytes(source.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        String header = new String(java.nio.file.Files.readAllBytes(new File(out, "kr_adaptive_compute.h").toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(cpp.contains("KR_COMPUTE_VERIFY"));
        assertTrue(cpp.contains("execute_conv2d_forward_on_gpu"));
        assertTrue(cpp.contains("CPU_VALIDATION_FALLBACK"));
        assertTrue(header.contains("kr_compute_tensor_add_f32"));
        assertTrue(header.contains("kr_compute_matmul_f32"));
        assertTrue(header.contains("kr_compute_image_rgba_scale_f32"));
        assertTrue(header.contains("kr_compute_require"));
        assertTrue(cpp.contains("GPU_REQUIRED_FAILED"));
    }

    private static KrSegment cppSegment(String source) {
        return new KrSegment(KrLanguage.CPP, source, "main.kr", 1, 1, 1, 1, 0, source.length());
    }

    private static File autoComputeExample() {
        File directory = new File(System.getProperty("user.dir")).getAbsoluteFile();
        for (int depth = 0; directory != null && depth < 6; depth++, directory = directory.getParentFile()) {
            File candidate = new File(directory, "KR_AUTO_COMPUTE_TEST");
            if (candidate.isDirectory()) return candidate;
        }
        return new File(System.getProperty("user.dir"), "KR_AUTO_COMPUTE_TEST");
    }

    @Test
    public void testAdaptiveComputeAcceptsIndependentElementwiseLoop() {
        String source = "void add(float* out, const float* a, const float* b, int n) {\n"
                + "  for (int i = 0; i < n; i++) { out[i] = a[i] + b[i]; }\n}";
        KrComputePlan plan = KrAdaptiveComputeAnalyzer.analyze(
                java.util.List.of(cppSegment(source)), KrComputePlan.Mode.AUTO);
        assertEquals(1, plan.getEntries().size());
        KrComputePlan.Entry entry = plan.getEntries().get(0);
        assertEquals(KrComputePlan.Disposition.GPU_ELIGIBLE, entry.disposition);
        assertEquals(KrComputePlan.Operation.ELEMENTWISE_ADD, entry.operation);
        assertTrue(entry.replaySafe);
        assertEquals(java.util.List.of("a", "b"), entry.reads);
        assertEquals(java.util.List.of("out"), entry.writes);
    }

    @Test
    public void testAdaptiveComputeRecognizesReductionAndMatrixMultiply() {
        KrComputePlan reduction = KrAdaptiveComputeAnalyzer.analyze(java.util.List.of(cppSegment(
                "float sum(float* a,int n){float total=0;for(int i=0;i<n;i++){total += a[i];}return total;}")),
                KrComputePlan.Mode.VERIFY);
        assertEquals(KrComputePlan.Operation.REDUCE_SUM, reduction.getEntries().get(0).operation);
        assertEquals(KrComputePlan.Disposition.GPU_ELIGIBLE, reduction.getEntries().get(0).disposition);

        String mm = "void mm(float* o,float* a,float* b,int M,int N,int K){"
                + "for(int i=0;i<M;i++){for(int j=0;j<N;j++){o[i*N+j]=0;"
                + "for(int k=0;k<K;k++){o[i*N+j] += a[i*K+k]*b[k*N+j];}}}}";
        KrComputePlan matrix = KrAdaptiveComputeAnalyzer.analyze(java.util.List.of(cppSegment(mm)), KrComputePlan.Mode.AUTO);
        assertEquals(KrComputePlan.Operation.MATRIX_MULTIPLY, matrix.getEntries().get(0).operation);
        assertEquals(KrComputePlan.Disposition.GPU_ELIGIBLE, matrix.getEntries().get(0).disposition);
        assertEquals(java.util.List.of("a", "b"), matrix.getEntries().get(0).reads);
        assertEquals(java.util.List.of("o"), matrix.getEntries().get(0).writes);
    }

    @Test
    public void testAdaptiveComputeExampleProject() throws Exception {
        File project = autoComputeExample();
        assertTrue("The shipped example project must exist", project.isDirectory());

        KrProjectConfig config = KrProjectConfig.parse(
                KrProjectConfig.findProjectConfigFile(project), project.getName(), false);
        KrComputePlan plan = KrAdaptiveComputeAnalyzer.analyzeProject(project, config.computeMode);

        assertEquals(KrComputePlan.Mode.AUTO, plan.getMode());
        assertFalse("The example must contain an analyzable loop", plan.getEntries().isEmpty());
        KrComputePlan.Entry entry = plan.getEntries().get(0);
        assertEquals(KrComputePlan.Operation.ELEMENTWISE_ADD, entry.operation);
        assertEquals(KrComputePlan.Disposition.GPU_ELIGIBLE, entry.disposition);
        assertEquals(java.util.List.of("left", "right"), entry.reads);
        assertEquals(java.util.List.of("output"), entry.writes);
    }

    @Test
    public void testAdaptiveComputeExampleGeneratesNativeRuntime() throws Exception {
        File project = autoComputeExample();
        File output = new File("build/test-adaptive-example-pipeline");
        assertTrue(output.isDirectory() || output.mkdirs());
        KrPipeline.PipelineResult result = KrPipeline.process(project, output, false);
        assertTrue(result.success);
        assertTrue(new File(output, "kr_adaptive_compute.h").isFile());
        assertTrue(new File(output, "kr_adaptive_compute.cpp").isFile());
        assertTrue(result.codeResult.cppSources.contains(new File(output, "kr_adaptive_compute.cpp")));
    }

    @Test
    public void testAutomaticFp32LoopLoweringUsesGeneratedRuntime() throws Exception {
        File project = autoComputeExample();
        File output = new File("build/test-auto-compute-lowering");
        assertTrue(output.isDirectory() || output.mkdirs());

        KrPipeline.PipelineResult result = KrPipeline.process(project, output, false);
        assertTrue(result.success);
        String generated = new String(java.nio.file.Files.readAllBytes(
                new File(output, "kr_cpp.cpp").toPath()), java.nio.charset.StandardCharsets.UTF_8);
        String original = new String(java.nio.file.Files.readAllBytes(
                new File(project, "main.kr").toPath()), java.nio.charset.StandardCharsets.UTF_8);

        assertFalse(original.contains("kr_compute_"));
        assertTrue(generated.contains("kr_compute_add_f32(left, right, output"));
        assertTrue(generated.contains("kr_compute_mul_f32(left, right, output"));
        assertTrue(generated.contains("kr_compute_require("));
        assertFalse(generated.contains("output[index] = left[index] + right[index]"));
        assertFalse(generated.contains("output[index] = left[index] * right[index]"));
    }

    @Test
    public void testAutomaticLoweringKeepsUnprovenTypesOnCpuPath() {
        String source = "void add(double* out,const double* a,const double* b,int n){"
                + "for(int i=0;i<n;i++){out[i]=a[i]+b[i];}}";
        KrSegment original = cppSegment(source);
        KrComputePlan plan = KrAdaptiveComputeAnalyzer.analyze(
                java.util.List.of(original), KrComputePlan.Mode.AUTO);
        java.util.List<KrSegment> lowered = KrAdaptiveComputeLowerer.lower(java.util.List.of(original), plan);
        assertEquals(source, lowered.get(0).source);
        assertFalse(lowered.get(0).source.contains("kr_compute_add_f32"));
    }

    @Test
    public void testAdaptiveComputeRejectsDependencyAndSideEffect() {
        String source = "void unsafe(float* a, int n) {\n"
                + "  for (int i = 0; i < n; i++) { a[i] = a[i] + external(i); }\n}";
        KrComputePlan plan = KrAdaptiveComputeAnalyzer.analyze(
                java.util.List.of(cppSegment(source)), KrComputePlan.Mode.AUTO);
        assertEquals(1, plan.getEntries().size());
        KrComputePlan.Entry entry = plan.getEntries().get(0);
        assertEquals(KrComputePlan.Disposition.CPU_REQUIRED, entry.disposition);
        assertFalse(entry.replaySafe);
        assertTrue(entry.reasons.toString().contains("Seiteneffekten"));
        assertTrue(entry.reasons.toString().contains("Abhängigkeit") || entry.reasons.toString().contains("Aliasing"));
    }

    @Test
    public void testComputeDispatcherModesAndMemorySafety() {
        KrComputePlan.Entry entry = KrAdaptiveComputeAnalyzer.analyze(
                java.util.List.of(cppSegment("void f(float* o,float* a,float* b,int n){for(int i=0;i<n;i++){o[i]=a[i]*b[i];}}")),
                KrComputePlan.Mode.AUTO).getEntries().get(0);
        KrComputeDispatcher.Capabilities caps = new KrComputeDispatcher.Capabilities(
                true, true, true, false, 1L << 30, 2L << 30, 100_000, 8_000_000_000L);
        assertEquals(KrComputeDispatcher.Target.CPU,
                KrComputeDispatcher.decide(KrComputePlan.Mode.CPU_ONLY, entry, caps, 1_000_000, 4).target);
        assertEquals(KrComputeDispatcher.Target.GPU,
                KrComputeDispatcher.decide(KrComputePlan.Mode.AUTO, entry, caps, 1_000_000, 4).target);
        KrComputeDispatcher.Capabilities noGpu = new KrComputeDispatcher.Capabilities(
                false, false, true, false, 0, 0, 0, 0);
        assertEquals(KrComputeDispatcher.Target.ERROR,
                KrComputeDispatcher.decide(KrComputePlan.Mode.GPU_ONLY, entry, noGpu, 1_000_000, 4).target);
    }

    @Test
    public void testClangDiagnosticParsing() {
        String clangLine = "src/main.cpp:42:15: error: expected ';'";
        DiagnosticParser.Item item = DiagnosticParser.parseLine(clangLine);
        assertNotNull("Diagnostic line should be recognized", item);
        assertEquals("src/main.cpp", item.file);
        assertEquals(42, item.line);
        assertEquals(15, item.column);
        assertEquals(DiagnosticParser.Severity.ERROR, item.severity);
        assertEquals("expected ';'", item.message);

        String fatalLine = "include/header.hpp:12: fatal error: 'unknown.h' file not found";
        DiagnosticParser.Item fatalItem = DiagnosticParser.parseLine(fatalLine);
        assertNotNull(fatalItem);
        assertEquals("include/header.hpp", fatalItem.file);
        assertEquals(12, fatalItem.line);
        assertEquals(DiagnosticParser.Severity.ERROR, fatalItem.severity);

        String warnLine = "main.cpp:7:1: warning: unused variable 'count' [-Wunused-variable]";
        DiagnosticParser.Item warnItem = DiagnosticParser.parseLine(warnLine);
        assertNotNull(warnItem);
        assertEquals("main.cpp", warnItem.file);
        assertEquals(7, warnItem.line);
        assertEquals(1, warnItem.column);
        assertEquals(DiagnosticParser.Severity.WARNING, warnItem.severity);
    }

    @Test
    public void testCMakeDiagnosticParsing() {
        String cmakeLine = "CMake Error at CMakeLists.txt:25 (target_link_libraries): Cannot find library";
        DiagnosticParser.Item item = DiagnosticParser.parseLine(cmakeLine);
        assertNotNull("CMake error should be recognized", item);
        assertEquals("CMakeLists.txt", item.file);
        assertEquals(25, item.line);
        assertEquals(DiagnosticParser.Severity.ERROR, item.severity);

        String cmakeWarn = "CMake Warning at cmake/modules.cmake:10: Deprecated module";
        DiagnosticParser.Item warnItem = DiagnosticParser.parseLine(cmakeWarn);
        assertNotNull(warnItem);
        assertEquals("cmake/modules.cmake", warnItem.file);
        assertEquals(10, warnItem.line);
        assertEquals(DiagnosticParser.Severity.WARNING, warnItem.severity);
    }

    @Test
    public void testDocumentModelUndoRedo() {
        String initial = "#include <iostream>\n\nint main() {\n    return 0;\n}\n";
        DocumentModel doc = new DocumentModel("src/main.cpp", initial, null, null);

        assertEquals("src/main.cpp", doc.getRelativePath());
        assertEquals("main.cpp", doc.getDisplayName());
        assertEquals(initial, doc.getText());
        assertFalse("Document initially not modified", doc.isModified());

        // Perform edit
        String edited = "#include <iostream>\n\nint main() {\n    std::cout << \"KR IDE\";\n    return 0;\n}\n";
        doc.onTextChanged(edited, 33, "", "    std::cout << \"KR IDE\";\n");
        assertTrue("Document should be marked modified", doc.isModified());
        assertTrue("Can undo", doc.canUndo());

        // Test Undo
        DocumentModel.EditAction undoAction = doc.popUndo();
        assertNotNull(undoAction);
        assertEquals("", undoAction.oldText);
        assertEquals("    std::cout << \"KR IDE\";\n", undoAction.newText);
        assertTrue("Can redo after undo", doc.canRedo());

        // Test Redo
        DocumentModel.EditAction redoAction = doc.popRedo();
        assertNotNull(redoAction);
        assertEquals("    std::cout << \"KR IDE\";\n", redoAction.newText);

        // Mark saved
        doc.markSaved(edited);
        assertFalse("Should be clean after save", doc.isModified());
        assertEquals(edited, doc.getText());
    }

    @Test
    public void testDocumentCursorAndScrollRetention() {
        DocumentModel doc = new DocumentModel("test.cpp", "line 1\nline 2\nline 3\n", null, null);
        doc.setCursor(14, 14);
        doc.setScroll(50, 120);

        assertEquals(14, doc.getCursorPosition());
        assertEquals(14, doc.getSelectionEnd());
        assertEquals(50, doc.getScrollX());
        assertEquals(120, doc.getScrollY());
    }

    @Test
    public void testGitUrlValidation() {
        assertTrue(GitImporter.isValidGitHubUrl("https://github.com/kruemmel-python/Mycelia-CSS.git"));
        assertTrue(GitImporter.isValidGitHubUrl("https://github.com/kruemmel-python/Mycelia-CSS"));
        assertTrue(GitImporter.isValidGitHubUrl("https://github.com/torvalds/linux.git/"));

        assertFalse(GitImporter.isValidGitHubUrl("http://insecure.com/repo"));
        assertFalse(GitImporter.isValidGitHubUrl("https://github.com/evil; rm -rf /"));
        assertFalse(GitImporter.isValidGitHubUrl("not-a-url"));
        assertFalse(GitImporter.isValidGitHubUrl(""));
        assertFalse(GitImporter.isValidGitHubUrl(null));
    }

    @Test
    public void testBinaryFileDetection() {
        assertTrue(ProjectManager.isBinaryFile("app.apk"));
        assertTrue(ProjectManager.isBinaryFile("program.exe"));
        assertTrue(ProjectManager.isBinaryFile("libuser.so"));
        assertTrue(ProjectManager.isBinaryFile("archive.zip"));
        assertTrue(ProjectManager.isBinaryFile("picture.png"));
        assertTrue(ProjectManager.isBinaryFile("library.o"));

        assertFalse(ProjectManager.isBinaryFile("main.cpp"));
        assertFalse(ProjectManager.isBinaryFile("header.hpp"));
        assertFalse(ProjectManager.isBinaryFile("CMakeLists.txt"));
        assertFalse(ProjectManager.isBinaryFile("script.cmake"));
        assertFalse(ProjectManager.isBinaryFile("config.json"));
        assertFalse(ProjectManager.isBinaryFile("README.md"));
    }

    @Test
    public void testAutoBracketPairing() {
        assertEquals(')', CodeEditorView.getClosingPair('('));
        assertEquals('}', CodeEditorView.getClosingPair('{'));
        assertEquals(']', CodeEditorView.getClosingPair('['));
        assertEquals('"', CodeEditorView.getClosingPair('"'));
        assertEquals('\'', CodeEditorView.getClosingPair('\''));
        assertEquals(0, CodeEditorView.getClosingPair('a'));
        assertEquals(0, CodeEditorView.getClosingPair(';'));
        assertEquals(0, CodeEditorView.getClosingPair('>'));
    }

    @Test
    public void testAutoIndentationCalculation() {
        // Line ending with '{' should increase indentation by tabWidth (4 spaces)
        String code1 = "int main() {\n";
        String indent1 = CodeEditorView.calculateAutoIndent(code1, code1.length() - 1, 4);
        assertEquals("    ", indent1);

        // Nested block ending with '{' should preserve current 4 spaces + add 4 = 8 spaces
        String code2 = "int main() {\n    if (x > 0) {\n";
        String indent2 = CodeEditorView.calculateAutoIndent(code2, code2.length() - 1, 4);
        assertEquals("        ", indent2);

        // Normal line with 4 spaces indent should preserve same 4 spaces on enter
        String code3 = "int main() {\n    std::cout << \"Hello\";\n";
        String indent3 = CodeEditorView.calculateAutoIndent(code3, code3.length() - 1, 4);
        assertEquals("    ", indent3);

        // Root level statement without braces has 0 indent
        String code4 = "#include <iostream>\n";
        String indent4 = CodeEditorView.calculateAutoIndent(code4, code4.length() - 1, 4);
        assertEquals("", indent4);
    }

    @Test
    public void testDocumentModelMultiStepUndoRedo() {
        DocumentModel doc = new DocumentModel("main.cpp", "a", null, null);
        assertFalse(doc.canUndo());
        assertFalse(doc.canRedo());

        // Step 1: "a" -> "ab"
        doc.onTextChanged("ab", 1, "", "b");
        assertTrue(doc.isModified());
        assertTrue(doc.canUndo());

        // Step 2: "ab" -> "abc"
        doc.onTextChanged("abc", 2, "", "c");
        assertTrue(doc.canUndo());

        // Step 3: Undo step 2 ("abc" -> "ab")
        DocumentModel.EditAction undo2 = doc.popUndo();
        assertNotNull(undo2);
        assertEquals("c", undo2.newText);
        assertEquals(2, undo2.start);
        assertTrue(doc.canRedo());

        // Step 4: Undo step 1 ("ab" -> "a")
        DocumentModel.EditAction undo1 = doc.popUndo();
        assertNotNull(undo1);
        assertEquals("b", undo1.newText);
        assertEquals(1, undo1.start);
        assertFalse(doc.canUndo());

        // Step 5: Redo step 1 ("a" -> "ab")
        DocumentModel.EditAction redo1 = doc.popRedo();
        assertNotNull(redo1);
        assertEquals("b", redo1.newText);
        assertTrue(doc.canUndo());

        // Step 6: Redo step 2 ("ab" -> "abc")
        DocumentModel.EditAction redo2 = doc.popRedo();
        assertNotNull(redo2);
        assertEquals("c", redo2.newText);
        assertFalse(doc.canRedo());
    }

    @Test
    public void testDiagnosticParserSeveritiesAndEdgeCases() {
        // Diagnostic with Note
        String noteLine = "util.h:88:5: note: candidate function not viable";
        DiagnosticParser.Item noteItem = DiagnosticParser.parseLine(noteLine);
        assertNotNull(noteItem);
        assertEquals(DiagnosticParser.Severity.NOTE, noteItem.severity);
        assertEquals(88, noteItem.line);
        assertEquals(5, noteItem.column);

        // Windows path with backslashes
        String winPathLine = "src\\core\\engine.cpp:102:4: error: use of undeclared identifier";
        DiagnosticParser.Item winItem = DiagnosticParser.parseLine(winPathLine);
        assertNotNull(winItem);
        assertEquals("src\\core\\engine.cpp", winItem.file);
        assertEquals(102, winItem.line);
        assertEquals(DiagnosticParser.Severity.ERROR, winItem.severity);

        // Line without column number
        String noColLine = "test.cpp:15: error: missing return statement";
        DiagnosticParser.Item noColItem = DiagnosticParser.parseLine(noColLine);
        assertNotNull(noColItem);
        assertEquals("test.cpp", noColItem.file);
        assertEquals(15, noColItem.line);
        assertEquals(1, noColItem.column);

        // Non-diagnostic compiler output (info/banner lines) should return null
        assertNull(DiagnosticParser.parseLine("[1/3] Building CXX object CMakeFiles/test.dir/main.cpp.o"));
        assertNull(DiagnosticParser.parseLine("Ninja build started..."));
        assertNull(DiagnosticParser.parseLine(""));
        assertNull(DiagnosticParser.parseLine(null));
    }
}
