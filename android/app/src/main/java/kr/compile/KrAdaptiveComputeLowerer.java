package kr.compile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lowers a deliberately small, proven subset of C++ loops to the portable
 * Adaptive Compute ABI.  This pass only consumes lexer tokens and analyzer
 * decisions; it never performs textual/regular-expression loop matching.
 */
public final class KrAdaptiveComputeLowerer {
    private KrAdaptiveComputeLowerer() {}

    public static List<KrSegment> lower(List<KrSegment> segments, KrComputePlan plan) {
        List<KrSegment> result = new ArrayList<>();
        if (segments == null) return result;
        for (KrSegment segment : segments) {
            if (segment.language != KrLanguage.CPP) {
                result.add(segment);
                continue;
            }
            KrSegment lowered = copyWithSource(segment, lowerSegment(segment, plan));
            result.add(lowered);
        }
        return result;
    }

    private static String lowerSegment(KrSegment segment, KrComputePlan plan) {
        List<KrToken> tokens = significant(new KrLexer(segment.source).tokenize());
        List<Replacement> replacements = new ArrayList<>();
        Set<Integer> consumedPlanEntries = new HashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (!"for".equals(tokens.get(i).text)) continue;
            Candidate candidate = parseCandidate(tokens, i);
            if (candidate == null) continue;
            int absoluteLine = segment.startLine + Math.max(0, tokens.get(i).line - 1);
            int planIndex = matchingPlanEntry(plan, segment.file, absoluteLine, candidate.operation, consumedPlanEntries);
            if (planIndex < 0 || !hasFp32Declaration(tokens, i, candidate.target)
                    || !hasFp32Declaration(tokens, i, candidate.left)
                    || !hasFp32Declaration(tokens, i, candidate.right)) continue;

            String function = candidate.operation == KrComputePlan.Operation.ELEMENTWISE_ADD
                    ? "kr_compute_add_f32" : "kr_compute_mul_f32";
            String location = cString(segment.file);
            String call = "if ((" + candidate.bound + ") > 0) { kr_compute_require("
                    + function + "(" + candidate.left + ", " + candidate.right + ", "
                    + candidate.target + ", static_cast<size_t>(" + candidate.bound + ")), \""
                    + location + "\", " + absoluteLine + "); }";
            String original = segment.source.substring(candidate.startOffset, candidate.endOffset);
            StringBuilder replacement = new StringBuilder(call);
            for (int n = 0; n < original.length(); n++) if (original.charAt(n) == '\n') replacement.append('\n');
            replacements.add(new Replacement(candidate.startOffset, candidate.endOffset, replacement.toString()));
            consumedPlanEntries.add(planIndex);
            i = candidate.endToken;
        }
        if (replacements.isEmpty()) return segment.source;
        StringBuilder out = new StringBuilder(segment.source);
        for (int i = replacements.size() - 1; i >= 0; i--) {
            Replacement replacement = replacements.get(i);
            out.replace(replacement.start, replacement.end, replacement.text);
        }
        return out.toString();
    }

    private static int matchingPlanEntry(KrComputePlan plan, String file, int line,
                                         KrComputePlan.Operation operation, Set<Integer> consumed) {
        if (plan == null) return -1;
        List<KrComputePlan.Entry> entries = plan.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            KrComputePlan.Entry entry = entries.get(i);
            if (!consumed.contains(i) && entry.disposition == KrComputePlan.Disposition.GPU_ELIGIBLE
                    && entry.operation == operation && entry.line == line && entry.file.equals(file)) return i;
        }
        return -1;
    }

    private static Candidate parseCandidate(List<KrToken> tokens, int forIndex) {
        int open = forIndex + 1;
        if (open >= tokens.size() || tokens.get(open).type != KrTokenType.LPAREN) return null;
        int close = matching(tokens, open, KrTokenType.LPAREN, KrTokenType.RPAREN);
        if (close < 0) return null;
        List<Integer> semicolons = topLevel(tokens, open + 1, close, KrTokenType.SEMICOLON);
        if (semicolons.size() != 2) return null;

        List<KrToken> init = tokens.subList(open + 1, semicolons.get(0));
        List<KrToken> condition = tokens.subList(semicolons.get(0) + 1, semicolons.get(1));
        List<KrToken> stride = tokens.subList(semicolons.get(1) + 1, close);
        String induction = inductionAtZero(init);
        if (induction.isEmpty() || condition.size() != 3 || !condition.get(0).text.equals(induction)
                || condition.get(1).type != KrTokenType.LT || !safeBound(condition.get(2))) return null;
        String bound = condition.get(2).text;
        if (!unitStride(stride, induction)) return null;

        int bodyOpen = close + 1;
        if (bodyOpen >= tokens.size() || tokens.get(bodyOpen).type != KrTokenType.LBRACE) return null;
        int bodyClose = matching(tokens, bodyOpen, KrTokenType.LBRACE, KrTokenType.RBRACE);
        if (bodyClose < 0) return null;
        List<KrToken> body = tokens.subList(bodyOpen + 1, bodyClose);
        // target[i] = left[i] (+|*) right[i];
        if (body.size() != 15 || body.get(0).type != KrTokenType.IDENTIFIER
                || body.get(1).type != KrTokenType.LBRACKET || !body.get(2).text.equals(induction)
                || body.get(3).type != KrTokenType.RBRACKET || body.get(4).type != KrTokenType.ASSIGN
                || body.get(5).type != KrTokenType.IDENTIFIER || body.get(6).type != KrTokenType.LBRACKET
                || !body.get(7).text.equals(induction) || body.get(8).type != KrTokenType.RBRACKET
                || (body.get(9).type != KrTokenType.PLUS && body.get(9).type != KrTokenType.STAR)
                || body.get(10).type != KrTokenType.IDENTIFIER || body.get(11).type != KrTokenType.LBRACKET
                || !body.get(12).text.equals(induction) || body.get(13).type != KrTokenType.RBRACKET
                || body.get(14).type != KrTokenType.SEMICOLON) return null;
        String target = body.get(0).text, left = body.get(5).text, right = body.get(10).text;
        if (target.equals(left) || target.equals(right)) return null;
        KrComputePlan.Operation operation = body.get(9).type == KrTokenType.PLUS
                ? KrComputePlan.Operation.ELEMENTWISE_ADD : KrComputePlan.Operation.ELEMENTWISE_MULTIPLY;
        return new Candidate(tokens.get(forIndex).startOffset, tokens.get(bodyClose).endOffset,
                bodyClose, bound, target, left, right, operation);
    }

    private static boolean hasFp32Declaration(List<KrToken> tokens, int before, String name) {
        for (int i = 0; i < before; i++) {
            if (!tokens.get(i).text.equals(name)) continue;
            int floatIndex = -1;
            for (int j = i - 1, inspected = 0; j >= 0 && inspected++ < 24; j--) {
                KrToken token = tokens.get(j);
                if ("double".equals(token.text)) break;
                if ("float".equals(token.text)) {
                    floatIndex = j;
                    break;
                }
                if (token.type == KrTokenType.SEMICOLON || token.type == KrTokenType.LBRACE
                        || token.type == KrTokenType.RBRACE || token.type == KrTokenType.LPAREN
                        || token.type == KrTokenType.COMMA) break;
            }
            if (floatIndex < 0) continue;
            boolean declarator = true;
            for (int j = floatIndex + 1; j < i; j++) {
                KrToken token = tokens.get(j);
                String text = token.text;
                if (token.type == KrTokenType.STAR || token.type == KrTokenType.AMPERSAND
                        || "const".equals(text) || "volatile".equals(text)
                        || "restrict".equals(text) || "__restrict".equals(text)) continue;
                declarator = false;
                break;
            }
            if (declarator) return true;
        }
        return false;
    }

    private static String inductionAtZero(List<KrToken> init) {
        for (int i = 0; i + 2 < init.size(); i++) {
            if (init.get(i).type == KrTokenType.IDENTIFIER && init.get(i + 1).type == KrTokenType.ASSIGN
                    && init.get(i + 2).type == KrTokenType.NUMBER_LITERAL
                    && ("0".equals(init.get(i + 2).text) || "0u".equalsIgnoreCase(init.get(i + 2).text))
                    && i + 3 == init.size()) return init.get(i).text;
        }
        return "";
    }

    private static boolean unitStride(List<KrToken> tokens, String induction) {
        StringBuilder value = new StringBuilder();
        for (KrToken token : tokens) value.append(token.text);
        String s = value.toString();
        return s.equals(induction + "++") || s.equals("++" + induction)
                || s.equals(induction + "+=1") || s.equals(induction + "=" + induction + "+1");
    }

    private static boolean safeBound(KrToken token) {
        return token.type == KrTokenType.IDENTIFIER || token.type == KrTokenType.NUMBER_LITERAL;
    }

    private static List<Integer> topLevel(List<KrToken> tokens, int from, int to, KrTokenType wanted) {
        List<Integer> result = new ArrayList<>(); int depth = 0;
        for (int i = from; i < to; i++) {
            KrTokenType type = tokens.get(i).type;
            if (type == KrTokenType.LPAREN || type == KrTokenType.LBRACKET) depth++;
            else if (type == KrTokenType.RPAREN || type == KrTokenType.RBRACKET) depth--;
            else if (depth == 0 && type == wanted) result.add(i);
        }
        return result;
    }

    private static int matching(List<KrToken> tokens, int open, KrTokenType openType, KrTokenType closeType) {
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
            if (tokens.get(i).type == openType) depth++;
            else if (tokens.get(i).type == closeType && --depth == 0) return i;
        }
        return -1;
    }

    private static List<KrToken> significant(List<KrToken> raw) {
        List<KrToken> result = new ArrayList<>();
        for (KrToken token : raw) if (token.type != KrTokenType.COMMENT && token.type != KrTokenType.EOF) result.add(token);
        return result;
    }

    private static KrSegment copyWithSource(KrSegment original, String source) {
        KrSegment copy = new KrSegment(original.language, source, original.file, original.startLine,
                original.startCol, original.endLine, original.endCol, original.startOffset, original.endOffset);
        copy.declaredFunctions.addAll(original.declaredFunctions);
        copy.calledFunctionNames.addAll(original.calledFunctionNames);
        return copy;
    }

    private static String cString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class Candidate {
        final int startOffset, endOffset, endToken; final String bound, target, left, right;
        final KrComputePlan.Operation operation;
        Candidate(int startOffset, int endOffset, int endToken, String bound, String target,
                  String left, String right, KrComputePlan.Operation operation) {
            this.startOffset = startOffset; this.endOffset = endOffset; this.endToken = endToken;
            this.bound = bound; this.target = target; this.left = left; this.right = right; this.operation = operation;
        }
    }

    private static final class Replacement {
        final int start, end; final String text;
        Replacement(int start, int end, String text) { this.start = start; this.end = end; this.text = text; }
    }
}
