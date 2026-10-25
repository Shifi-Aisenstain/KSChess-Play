import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

/** Minimal JUnit Platform runner: discovers and runs everything under target/test-classes. */
public class RunTests {
    public static void main(String[] args) {
        Set<Path> roots = Set.of(Paths.get("target/test-classes"));
        LauncherDiscoveryRequest req = request().selectors(selectClasspathRoots(roots)).build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(req);

        TestExecutionSummary summary = listener.getSummary();
        PrintWriter out = new PrintWriter(System.out, true);
        summary.printTo(out);
        summary.printFailuresTo(out, 1000);
        System.exit(summary.getTotalFailureCount() > 0 ? 1 : 0);
    }
}
