import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

/** Roda os testes do motor sem o Maven (uso: RodarTestes [classe ...]; sem argumentos, tudo em br.futebas menos ui). */
public class RodarTestes {
    public static void main(String[] args) throws Exception {
        LauncherDiscoveryRequestBuilder b = LauncherDiscoveryRequestBuilder.request();
        if (args.length == 0) {
            b.selectors(selectPackage("br.futebas"));
            b.filters(org.junit.platform.engine.discovery.PackageNameFilter.excludePackageNames("br.futebas.ui"));
        } else for (String a : args) b.selectors(selectClass(Class.forName(a)));
        LauncherDiscoveryRequest req = b.build();
        Launcher l = LauncherFactory.create();
        SummaryGeneratingListener s = new SummaryGeneratingListener();
        l.execute(req, s);
        PrintWriter w = new PrintWriter(System.out);
        s.getSummary().printTo(w);
        s.getSummary().printFailuresTo(w, 15);
        w.flush();
        System.exit(s.getSummary().getTotalFailureCount() == 0 ? 0 : 1);
    }
}
