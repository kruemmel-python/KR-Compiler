package kr.compile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, auditable result of the Adaptive Compute compiler pass. */
public final class KrComputePlan {
    public enum Mode { AUTO, CPU_ONLY, GPU_ONLY, VERIFY }
    public enum Operation {
        ELEMENTWISE_ADD, ELEMENTWISE_SUBTRACT, ELEMENTWISE_MULTIPLY,
        ELEMENTWISE_DIVIDE, REDUCE_SUM, MATRIX_MULTIPLY, BATCHED_MATRIX_MULTIPLY,
        TENSOR_ADD, TENSOR_MULTIPLY, IMAGE_RGBA_SCALE, CONVOLUTION_2D,
        EXPLICIT_COMPUTE_API
    }
    public enum Disposition { GPU_ELIGIBLE, CPU_REQUIRED }

    public static final class Entry {
        public final String file;
        public final int line;
        public final int column;
        public final Operation operation;
        public final Disposition disposition;
        public final String inductionVariable;
        public final String tripCountExpression;
        public final List<String> reads;
        public final List<String> writes;
        public final List<String> reasons;
        public final boolean replaySafe;
        public final boolean requiresFp64;

        Entry(String file, int line, int column, Operation operation, Disposition disposition,
              String inductionVariable, String tripCountExpression, List<String> reads,
              List<String> writes, List<String> reasons, boolean replaySafe, boolean requiresFp64) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.operation = operation;
            this.disposition = disposition;
            this.inductionVariable = inductionVariable;
            this.tripCountExpression = tripCountExpression;
            this.reads = immutable(reads);
            this.writes = immutable(writes);
            this.reasons = immutable(reasons);
            this.replaySafe = replaySafe;
            this.requiresFp64 = requiresFp64;
        }

        private static List<String> immutable(List<String> source) {
            return Collections.unmodifiableList(new ArrayList<>(source));
        }
    }

    private final Mode mode;
    private final List<Entry> entries;

    public KrComputePlan(Mode mode, List<Entry> entries) {
        this.mode = mode != null ? mode : Mode.AUTO;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public Mode getMode() { return mode; }
    public List<Entry> getEntries() { return entries; }

    public boolean hasGpuEligibleOperations() {
        for (Entry entry : entries) if (entry.disposition == Disposition.GPU_ELIGIBLE) return true;
        return false;
    }

    public String toJson() {
        StringBuilder out = new StringBuilder(512).append("{\n  \"schema\": 2,\n  \"mode\": \"")
                .append(mode).append("\",\n  \"operations\": [");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (i > 0) out.append(',');
            out.append("\n    {\"file\":\"").append(escape(e.file)).append("\",\"line\":")
                    .append(e.line).append(",\"column\":").append(e.column)
                    .append(",\"operation\":\"").append(e.operation)
                    .append("\",\"disposition\":\"").append(e.disposition)
                    .append("\",\"inductionVariable\":\"").append(escape(e.inductionVariable))
                    .append("\",\"tripCountExpression\":\"").append(escape(e.tripCountExpression))
                    .append("\",\"replaySafe\":").append(e.replaySafe)
                    .append(",\"requiresFp64\":").append(e.requiresFp64)
                    .append(",\"reads\":");
            appendArray(out, e.reads);
            out.append(",\"writes\":");
            appendArray(out, e.writes);
            out.append(",\"reasons\":");
            appendArray(out, e.reasons);
            out.append('}');
        }
        return out.append("\n  ]\n}\n").toString();
    }

    private static void appendArray(StringBuilder out, List<String> values) {
        out.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append('\"').append(escape(values.get(i))).append('\"');
        }
        out.append(']');
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
