package kr.compile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Conservative token/structure based parallel-loop analysis. It only accepts a
 * deliberately small, provable subset; everything else receives a CPU reason.
 */
public final class KrAdaptiveComputeAnalyzer {
    private static final Set<String> PURE_CALLS = Set.of("abs", "fabs", "sqrt", "sqrtf", "min", "max");
    private static final Set<String> EXPLICIT_API = Set.of(
            "execute_add_on_gpu", "execute_mul_on_gpu", "execute_matmul_on_gpu",
            "execute_reduce_sum_gpu", "execute_conv2d_forward_on_gpu");

    private KrAdaptiveComputeAnalyzer() {}

    public static KrComputePlan analyzeProject(File projectDir, KrComputePlan.Mode mode) throws IOException {
        List<KrSegment> segments = new ArrayList<>();
        for (File source : KrPipeline.findKrFiles(projectDir)) {
            StringBuilder text = new StringBuilder();
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8)) {
                char[] buffer = new char[8192]; int count;
                while ((count = reader.read(buffer)) >= 0) text.append(buffer, 0, count);
            }
            String relative = projectDir.toPath().relativize(source.toPath()).toString().replace('\\', '/');
            segments.addAll(new KrParser(relative, text.toString()).parse());
        }
        return analyze(segments, mode);
    }

    public static String formatReport(KrComputePlan plan) {
        StringBuilder out = new StringBuilder("Modus: ").append(plan.getMode()).append('\n');
        if (plan.getEntries().isEmpty()) return out.append("Keine unterstützten Compute-Kandidaten erkannt.\n").toString();
        int index = 1;
        for (KrComputePlan.Entry entry : plan.getEntries()) {
            out.append('\n').append(index++).append(". ").append(entry.file).append(':').append(entry.line)
                    .append(" — ").append(entry.operation).append(" — ").append(entry.disposition).append('\n');
            if (!entry.reads.isEmpty()) out.append("   Lesen: ").append(entry.reads).append('\n');
            if (!entry.writes.isEmpty()) out.append("   Schreiben: ").append(entry.writes).append('\n');
            for (String reason : entry.reasons) out.append("   • ").append(reason).append('\n');
        }
        return out.toString();
    }

    public static KrComputePlan analyze(List<KrSegment> segments, KrComputePlan.Mode mode) {
        List<KrComputePlan.Entry> entries = new ArrayList<>();
        if (segments != null) for (KrSegment segment : segments) analyzeSegment(segment, entries);
        return new KrComputePlan(mode, entries);
    }

    private static void analyzeSegment(KrSegment segment, List<KrComputePlan.Entry> out) {
        List<KrToken> raw = new KrLexer(segment.source).tokenize();
        List<KrToken> tokens = new ArrayList<>();
        for (KrToken token : raw) if (token.type != KrTokenType.COMMENT && token.type != KrTokenType.EOF) tokens.add(token);

        for (int i = 0; i < tokens.size(); i++) {
            KrToken token = tokens.get(i);
            if (EXPLICIT_API.contains(token.text) && i + 1 < tokens.size() && tokens.get(i + 1).type == KrTokenType.LPAREN) {
                out.add(new KrComputePlan.Entry(segment.file, absoluteLine(segment, token.line), token.column,
                        KrComputePlan.Operation.EXPLICIT_COMPUTE_API, KrComputePlan.Disposition.GPU_ELIGIBLE,
                        "", "explicit", List.of(), List.of(), List.of("Explizite, geprüfte CC_OpenCL-API."), false, false));
            }
            if ("for".equals(token.text)) {
                Loop loop = parseLoop(tokens, i);
                if (loop != null) {
                    out.add(classify(segment, loop));
                    i = Math.max(i, loop.end);
                }
            }
        }
    }

    private static KrComputePlan.Entry classify(KrSegment segment, Loop loop) {
        List<String> reasons = new ArrayList<>();
        LinkedHashSet<String> reads = new LinkedHashSet<>();
        LinkedHashSet<String> writes = new LinkedHashSet<>();
        KrComputePlan.Operation operation = KrComputePlan.Operation.ELEMENTWISE_ADD;
        boolean eligible = true;
        boolean fp64 = false;

        if (loop.induction.isEmpty() || loop.bound.isEmpty()) {
            eligible = false; reasons.add("Schleifenraum oder Induktionsvariable ist nicht statisch bestimmbar.");
        }
        if (!loop.unitStride) { eligible = false; reasons.add("Nur vorwärts laufende Schleifen mit Schrittweite 1 sind verifiziert."); }
        if (loop.hasControlFlow) { eligible = false; reasons.add("Datenabhängige Kontrollflüsse in der Schleife sind nicht freigegeben."); }
        if (loop.hasUnknownCall) { eligible = false; reasons.add("Die Schleife enthält einen Aufruf mit unbekannten Seiteneffekten."); }
        if (loop.hasPointerWrite) { eligible = false; reasons.add("Pointer-Schreibzugriffe können Aliasing verursachen."); }
        if (loop.assignments != 1) { eligible = false; reasons.add("Automatische Auslagerung verlangt genau eine beweisbare Array-Zuweisung."); }

        Assignment assignment = loop.assignment;
        if (assignment != null) {
            writes.add(assignment.target);
            reads.addAll(assignment.sources);
            if (!assignment.indexMatches) { eligible = false; reasons.add("Der Schreibindex entspricht nicht eindeutig der Induktionsvariable."); }
            if (assignment.readsOutput) { eligible = false; reasons.add("Schleifengetragene Abhängigkeit: Ausgabepuffer wird zugleich gelesen."); }
            if (assignment.sources.contains(assignment.target)) { eligible = false; reasons.add("Mögliches In-place-Aliasing ist ohne restrict/Ownership-Beweis nicht zulässig."); }
            if (assignment.operator == KrTokenType.PLUS) operation = KrComputePlan.Operation.ELEMENTWISE_ADD;
            else if (assignment.operator == KrTokenType.MINUS) operation = KrComputePlan.Operation.ELEMENTWISE_SUBTRACT;
            else if (assignment.operator == KrTokenType.STAR) operation = KrComputePlan.Operation.ELEMENTWISE_MULTIPLY;
            else if (assignment.operator == KrTokenType.SLASH) operation = KrComputePlan.Operation.ELEMENTWISE_DIVIDE;
            else { eligible = false; reasons.add("Ausdruck ist keinem verifizierten elementweisen Kernel zuordenbar."); }
            fp64 = assignment.fp64;
        }
        if (reads.isEmpty()) { eligible = false; reasons.add("Keine eindeutig indizierten Eingabepuffer erkannt."); }
        if (eligible) reasons.add("Unabhängiger Einzelausgabe-Loop ohne unbekannte Seiteneffekte; CPU-Referenz bleibt erhalten.");

        KrToken at = loop.at;
        return new KrComputePlan.Entry(segment.file, absoluteLine(segment, at.line), at.column, operation,
                eligible ? KrComputePlan.Disposition.GPU_ELIGIBLE : KrComputePlan.Disposition.CPU_REQUIRED,
                loop.induction, loop.bound, new ArrayList<>(reads), new ArrayList<>(writes), reasons, eligible, fp64);
    }

    private static int absoluteLine(KrSegment segment, int localLine) {
        return Math.max(1, segment.startLine + Math.max(0, localLine - 1));
    }

    private static Loop parseLoop(List<KrToken> tokens, int forIndex) {
        Loop loop = new Loop(tokens.get(forIndex));
        int open = forIndex + 1;
        if (open >= tokens.size() || tokens.get(open).type != KrTokenType.LPAREN) return loop;
        int close = matching(tokens, open, KrTokenType.LPAREN, KrTokenType.RPAREN);
        if (close < 0) return loop;
        List<List<KrToken>> clauses = split(tokens, open + 1, close, KrTokenType.SEMICOLON);
        if (clauses.size() == 3) {
            loop.induction = induction(clauses.get(0));
            loop.bound = bound(clauses.get(1), loop.induction);
            loop.unitStride = unitStride(clauses.get(2), loop.induction);
        }
        int bodyOpen = close + 1;
        if (bodyOpen >= tokens.size() || tokens.get(bodyOpen).type != KrTokenType.LBRACE) return loop;
        int bodyClose = matching(tokens, bodyOpen, KrTokenType.LBRACE, KrTokenType.RBRACE);
        if (bodyClose < 0) return loop;
        loop.end = bodyClose;
        List<KrToken> body = tokens.subList(bodyOpen + 1, bodyClose);
        for (int i = 0; i < body.size(); i++) {
            KrToken t = body.get(i);
            if ("if".equals(t.text) || "while".equals(t.text) || "break".equals(t.text) || "continue".equals(t.text)) loop.hasControlFlow = true;
            if (t.type == KrTokenType.STAR && i + 2 < body.size()
                    && body.get(i + 1).type == KrTokenType.IDENTIFIER
                    && body.get(i + 2).type == KrTokenType.ASSIGN) loop.hasPointerWrite = true;
            if (t.type == KrTokenType.IDENTIFIER && i + 1 < body.size() && body.get(i + 1).type == KrTokenType.LPAREN
                    && !PURE_CALLS.contains(t.text)) loop.hasUnknownCall = true;
            if (t.type == KrTokenType.ASSIGN) {
                loop.assignments++;
                if (loop.assignments == 1) loop.assignment = parseAssignment(body, i, loop.induction);
            }
        }
        return loop;
    }

    private static Assignment parseAssignment(List<KrToken> body, int eq, String induction) {
        Assignment a = new Assignment();
        int leftBracket = eq - 1;
        while (leftBracket >= 0 && body.get(leftBracket).type != KrTokenType.LBRACKET && body.get(leftBracket).type != KrTokenType.SEMICOLON) leftBracket--;
        if (leftBracket > 0 && body.get(leftBracket - 1).type == KrTokenType.IDENTIFIER) {
            a.target = body.get(leftBracket - 1).text;
            int rightBracket = matching(body, leftBracket, KrTokenType.LBRACKET, KrTokenType.RBRACKET);
            a.indexMatches = rightBracket > leftBracket && rightBracket < eq && rightBracket == leftBracket + 2
                    && body.get(leftBracket + 1).text.equals(induction);
        }
        int end = eq + 1;
        while (end < body.size() && body.get(end).type != KrTokenType.SEMICOLON) end++;
        for (int i = eq + 1; i < end; i++) {
            KrToken t = body.get(i);
            if (t.type == KrTokenType.IDENTIFIER && i + 3 < end && body.get(i + 1).type == KrTokenType.LBRACKET) {
                int rb = matching(body, i + 1, KrTokenType.LBRACKET, KrTokenType.RBRACKET);
                if (rb == i + 3 && body.get(i + 2).text.equals(induction)) a.sources.add(t.text);
                if (t.text.equals(a.target)) a.readsOutput = true;
            }
            if (t.type == KrTokenType.PLUS || t.type == KrTokenType.MINUS || t.type == KrTokenType.STAR || t.type == KrTokenType.SLASH) {
                if (a.operator == null) a.operator = t.type;
            }
            String lower = t.text.toLowerCase(Locale.ROOT);
            if (lower.equals("double") || lower.endsWith("f64")) a.fp64 = true;
        }
        return a;
    }

    private static String induction(List<KrToken> clause) {
        for (int i = 0; i + 1 < clause.size(); i++)
            if (clause.get(i).type == KrTokenType.IDENTIFIER && clause.get(i + 1).type == KrTokenType.ASSIGN) return clause.get(i).text;
        return "";
    }

    private static String bound(List<KrToken> clause, String induction) {
        for (int i = 0; i + 1 < clause.size(); i++) {
            KrToken t = clause.get(i);
            if (t.text.equals(induction) && (clause.get(i + 1).type == KrTokenType.LT || clause.get(i + 1).type == KrTokenType.LE)) {
                StringBuilder b = new StringBuilder();
                for (int j = i + 2; j < clause.size(); j++) b.append(clause.get(j).text);
                return b.toString();
            }
        }
        return "";
    }

    private static boolean unitStride(List<KrToken> clause, String induction) {
        StringBuilder compact = new StringBuilder();
        for (KrToken t : clause) compact.append(t.text);
        String s = compact.toString();
        return s.equals(induction + "++") || s.equals("++" + induction)
                || s.equals(induction + "+=1") || s.equals(induction + "=" + induction + "+1");
    }

    private static int matching(List<KrToken> tokens, int open, KrTokenType openType, KrTokenType closeType) {
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
            if (tokens.get(i).type == openType) depth++;
            else if (tokens.get(i).type == closeType && --depth == 0) return i;
        }
        return -1;
    }

    private static List<List<KrToken>> split(List<KrToken> tokens, int from, int to, KrTokenType delimiter) {
        List<List<KrToken>> result = new ArrayList<>();
        int start = from, nested = 0;
        for (int i = from; i < to; i++) {
            KrTokenType type = tokens.get(i).type;
            if (type == KrTokenType.LPAREN || type == KrTokenType.LBRACKET) nested++;
            else if (type == KrTokenType.RPAREN || type == KrTokenType.RBRACKET) nested--;
            else if (type == delimiter && nested == 0) { result.add(tokens.subList(start, i)); start = i + 1; }
        }
        result.add(tokens.subList(start, to));
        return result;
    }

    private static final class Loop {
        final KrToken at; int end; String induction = ""; String bound = ""; boolean unitStride;
        boolean hasControlFlow, hasUnknownCall, hasPointerWrite; int assignments; Assignment assignment;
        Loop(KrToken at) { this.at = at; }
    }
    private static final class Assignment {
        String target = ""; final LinkedHashSet<String> sources = new LinkedHashSet<>();
        boolean indexMatches, readsOutput, fp64; KrTokenType operator;
    }
}
