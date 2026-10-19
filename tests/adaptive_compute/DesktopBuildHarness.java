import java.io.File;
import kr.compile.CompilerEngine;

/** Small command-line harness used by release validation of the desktop IDE. */
public final class DesktopBuildHarness {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: DesktopBuildHarness <project> <WINDOWS|LINUX>");
        CompilerEngine.Target target = CompilerEngine.Target.valueOf(args[1]);
        File artifact = new CompilerEngine(new File(args[0]), target, false, System.out::println).build();
        if (!artifact.isFile()) throw new IllegalStateException("missing build artifact: " + artifact);
        System.out.println("ARTIFACT=" + artifact.getAbsolutePath());
    }
}
