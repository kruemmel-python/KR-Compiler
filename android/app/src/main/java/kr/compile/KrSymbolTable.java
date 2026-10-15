package kr.compile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KrSymbolTable {

    private final Map<String, KrFunction> symbols = new LinkedHashMap<>();
    private final List<KrDiagnostic> diagnostics = new ArrayList<>();

    // Cross-call mappings
    private final Map<String, KrFunction> cppCallsRust = new LinkedHashMap<>();
    private final Map<String, KrFunction> cppCallsGo = new LinkedHashMap<>();
    private final Map<String, KrFunction> rustCallsCpp = new LinkedHashMap<>();
    private final Map<String, KrFunction> rustCallsGo = new LinkedHashMap<>();
    private final Map<String, KrFunction> goCallsCpp = new LinkedHashMap<>();
    private final Map<String, KrFunction> goCallsRust = new LinkedHashMap<>();

    private KrFunction mainFunction = null;

    public void registerFunction(KrFunction fn) {
        if (fn == null) return;
        String name = fn.name;

        // Skip standard main signatures from collision check if handled
        if (symbols.containsKey(name)) {
            KrFunction existing = symbols.get(name);
            if (existing.definingLanguage != fn.definingLanguage) {
                KrDiagnostic diag = new KrDiagnostic(KrDiagnostic.Severity.ERROR,
                        "Symbolkollision: Funktion '" + name + "' ist sowohl in "
                                + existing.definingLanguage.getDisplayName() + " (Zeile " + existing.line + ") als auch in "
                                + fn.definingLanguage.getDisplayName() + " (Zeile " + fn.line + ") definiert.",
                        fn.file, fn.line, fn.column);
                diagnostics.add(diag);
                return;
            }
        }

        symbols.put(name, fn);

        if (name.equals("main") || name.equals("KrLanguageMain")) {
            if (mainFunction == null || fn.definingLanguage == KrLanguage.CPP) {
                mainFunction = fn;
            }
        }
    }

    public void resolveCalls(List<KrSegment> segments) {
        for (KrSegment seg : segments) {
            for (String calledName : seg.calledFunctionNames) {
                KrFunction target = symbols.get(calledName);
                if (target == null) continue; // Might be a standard library function or external symbol

                if (target.definingLanguage != seg.language) {
                    // This is a cross-language call!
                    validateCrossCall(seg, target);

                    if (seg.language == KrLanguage.CPP) {
                        if (target.definingLanguage == KrLanguage.RUST) cppCallsRust.put(calledName, target);
                        else if (target.definingLanguage == KrLanguage.GO) cppCallsGo.put(calledName, target);
                    } else if (seg.language == KrLanguage.RUST) {
                        if (target.definingLanguage == KrLanguage.CPP) rustCallsCpp.put(calledName, target);
                        else if (target.definingLanguage == KrLanguage.GO) rustCallsGo.put(calledName, target);
                    } else if (seg.language == KrLanguage.GO) {
                        if (target.definingLanguage == KrLanguage.CPP) goCallsCpp.put(calledName, target);
                        else if (target.definingLanguage == KrLanguage.RUST) goCallsRust.put(calledName, target);
                    }
                }
            }
        }
    }

    private void validateCrossCall(KrSegment callerSegment, KrFunction target) {
        // Validate return type
        if (target.returnType.kind == KrType.Kind.UNKNOWN) {
            diagnostics.add(new KrDiagnostic(KrDiagnostic.Severity.WARNING,
                    "Sprachübergreifender Aufruf von '" + target.name + "': Rückgabetyp '"
                            + target.returnType + "' konnte keinem standardisierten KR-Typ zugeordnet werden.",
                    callerSegment.file, callerSegment.startLine, callerSegment.startCol));
        }

        // Validate parameter types
        for (KrFunction.Parameter p : target.parameters) {
            if (p.type.kind == KrType.Kind.UNKNOWN) {
                diagnostics.add(new KrDiagnostic(KrDiagnostic.Severity.WARNING,
                        "Sprachübergreifender Aufruf von '" + target.name + "': Parametertyp '"
                                + p.type + "' für Parameter '" + p.name + "' ist nicht standardisiert.",
                        callerSegment.file, callerSegment.startLine, callerSegment.startCol));
            }
        }
    }

    public Map<String, KrFunction> getSymbols() { return symbols; }
    public List<KrDiagnostic> getDiagnostics() { return diagnostics; }
    public KrFunction getMainFunction() { return mainFunction; }

    public Map<String, KrFunction> getCppCallsRust() { return cppCallsRust; }
    public Map<String, KrFunction> getCppCallsGo() { return cppCallsGo; }
    public Map<String, KrFunction> getRustCallsCpp() { return rustCallsCpp; }
    public Map<String, KrFunction> getRustCallsGo() { return rustCallsGo; }
    public Map<String, KrFunction> getGoCallsCpp() { return goCallsCpp; }
    public Map<String, KrFunction> getGoCallsRust() { return goCallsRust; }

    public boolean isExported(String name) {
        if (name == null) return false;
        return cppCallsRust.containsKey(name)
                || cppCallsGo.containsKey(name)
                || rustCallsCpp.containsKey(name)
                || rustCallsGo.containsKey(name)
                || goCallsCpp.containsKey(name)
                || goCallsRust.containsKey(name);
    }

    public Set<KrFunction> getAllExportedFunctions() {
        Set<KrFunction> set = new HashSet<>();
        set.addAll(cppCallsRust.values());
        set.addAll(cppCallsGo.values());
        set.addAll(rustCallsCpp.values());
        set.addAll(rustCallsGo.values());
        set.addAll(goCallsCpp.values());
        set.addAll(goCallsRust.values());
        return set;
    }
}
