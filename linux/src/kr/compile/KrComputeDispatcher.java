package kr.compile;

/** Deterministic, side-effect-free CPU/GPU policy used by the IDE and tests. */
public final class KrComputeDispatcher {
    public static final class Capabilities {
        public final boolean gpuAvailable;
        public final boolean kernelAvailable;
        public final boolean fp32;
        public final boolean fp64;
        public final long maxAllocationBytes;
        public final long globalMemoryBytes;
        public final long launchOverheadNanos;
        public final long transferBytesPerSecond;

        public Capabilities(boolean gpuAvailable, boolean kernelAvailable, boolean fp32, boolean fp64,
                            long maxAllocationBytes, long globalMemoryBytes, long launchOverheadNanos,
                            long transferBytesPerSecond) {
            this.gpuAvailable = gpuAvailable;
            this.kernelAvailable = kernelAvailable;
            this.fp32 = fp32;
            this.fp64 = fp64;
            this.maxAllocationBytes = Math.max(0, maxAllocationBytes);
            this.globalMemoryBytes = Math.max(0, globalMemoryBytes);
            this.launchOverheadNanos = Math.max(0, launchOverheadNanos);
            this.transferBytesPerSecond = Math.max(0, transferBytesPerSecond);
        }
    }

    public enum Target { CPU, GPU, ERROR }

    public static final class Decision {
        public final Target target;
        public final String reason;
        public Decision(Target target, String reason) { this.target = target; this.reason = reason; }
    }

    private KrComputeDispatcher() {}

    public static Decision decide(KrComputePlan.Mode mode, KrComputePlan.Entry operation,
                                  Capabilities caps, long elementCount, int bytesPerElement) {
        if (mode == null || operation == null || caps == null || elementCount < 0 || bytesPerElement <= 0)
            return new Decision(Target.ERROR, "Ungültiger Compute-Auftrag.");
        if (mode == KrComputePlan.Mode.CPU_ONLY)
            return new Decision(Target.CPU, "CPU_ONLY ist ausgewählt.");
        if (operation.disposition != KrComputePlan.Disposition.GPU_ELIGIBLE)
            return unavailable(mode, "Die Compileranalyse hat die Operation nicht als sicher parallelisierbar bewiesen.");
        if (!caps.gpuAvailable || !caps.kernelAvailable)
            return unavailable(mode, !caps.gpuAvailable ? "Kein OpenCL-Gerät verfügbar." : "Kein validierter GPU-Kernel verfügbar.");
        if (!caps.fp32 || (operation.requiresFp64 && !caps.fp64))
            return unavailable(mode, operation.requiresFp64 ? "FP64 wird vom Gerät nicht unterstützt." : "FP32 wird vom Gerät nicht unterstützt.");
        long bytes;
        try { bytes = Math.multiplyExact(elementCount, (long) bytesPerElement); }
        catch (ArithmeticException ex) { return unavailable(mode, "Puffergröße läuft über."); }
        long workingSet;
        try { workingSet = Math.multiplyExact(bytes, Math.max(2L, operation.reads.size() + operation.writes.size())); }
        catch (ArithmeticException ex) { return unavailable(mode, "Arbeitsspeicherbedarf läuft über."); }
        if (bytes > caps.maxAllocationBytes || workingSet > caps.globalMemoryBytes)
            return unavailable(mode, "GPU-Speichergrenze würde überschritten.");
        if (mode == KrComputePlan.Mode.GPU_ONLY || mode == KrComputePlan.Mode.VERIFY)
            return new Decision(Target.GPU, mode == KrComputePlan.Mode.VERIFY
                    ? "VERIFY führt GPU und anschließend die CPU-Referenz aus." : "GPU_ONLY ist zulässig.");

        // Conservative cold-start threshold. Profiling may raise/lower this externally, never correctness gates.
        long minimum = operation.operation == KrComputePlan.Operation.MATRIX_MULTIPLY ? 4096L : 65536L;
        if (elementCount < minimum)
            return new Decision(Target.CPU, "Datenmenge liegt unter dem konservativen GPU-Schwellenwert.");
        if (caps.transferBytesPerSecond == 0)
            return new Decision(Target.CPU, "Keine belastbare Transfermessung; konservativer CPU-Fallback.");
        return new Decision(Target.GPU, "Sicherheits-, Fähigkeits-, Speicher- und Kostenschwellen sind erfüllt.");
    }

    private static Decision unavailable(KrComputePlan.Mode mode, String reason) {
        return mode == KrComputePlan.Mode.GPU_ONLY
                ? new Decision(Target.ERROR, "GPU_ONLY: " + reason)
                : new Decision(Target.CPU, reason);
    }
}
