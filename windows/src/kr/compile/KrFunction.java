package kr.compile;

import java.util.ArrayList;
import java.util.List;

public final class KrFunction {

    public static final class Parameter {
        public final String name;
        public final KrType type;

        public Parameter(String name, KrType type) {
            this.name = name;
            this.type = type != null ? type : KrType.UNKNOWN;
        }

        @Override
        public String toString() {
            return name + ": " + type;
        }
    }

    public final String name;
    public KrType returnType;
    public final List<Parameter> parameters = new ArrayList<>();
    public final KrLanguage definingLanguage;
    public final String file;
    public final int line;
    public final int column;
    public final String rawSignature;

    public KrFunction(String name, KrType returnType, KrLanguage definingLanguage, String file, int line, int column, String rawSignature) {
        this.name = name;
        this.returnType = returnType != null ? returnType : KrType.VOID;
        this.definingLanguage = definingLanguage;
        this.file = file;
        this.line = line;
        this.column = column;
        this.rawSignature = rawSignature;
    }

    public void setReturnType(KrType returnType) {
        this.returnType = returnType != null ? returnType : KrType.VOID;
    }

    public void addParameter(String name, KrType type) {
        parameters.add(new Parameter(name, type));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(definingLanguage).append(" ").append(name).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(parameters.get(i));
        }
        sb.append(") -> ").append(returnType);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KrFunction that = (KrFunction) o;
        return name != null && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
