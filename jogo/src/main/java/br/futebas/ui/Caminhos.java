package br.futebas.ui;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Onde estão o banco mestre e os assets. Procura em: -Dfutebas.raiz, pasta atual e acima, pasta
 * do .jar e acima (no executável do jpackage, tudo fica em {@code app/}).
 */
public final class Caminhos {
    private Caminhos() {}

    private static Path raiz;

    public static synchronized Path raiz() {
        if (raiz != null) return raiz;
        List<Path> cand = new ArrayList<>();
        String prop = System.getProperty("futebas.raiz");
        if (prop != null) cand.add(Paths.get(prop));
        Path cwd = Paths.get("").toAbsolutePath();
        for (Path p = cwd; p != null && cand.size() < 8; p = p.getParent()) cand.add(p);
        try {
            Path jar = Paths.get(Caminhos.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (Path p = jar; p != null; p = p.getParent()) cand.add(p);
        } catch (URISyntaxException | RuntimeException e) {
            // segue com os outros candidatos
        }
        String app = System.getProperty("jpackage.app-path");
        if (app != null) {
            Path exe = Paths.get(app).getParent();
            if (exe != null) cand.add(0, exe.resolve("app"));
        }
        for (Path p : cand) {
            if (Files.isRegularFile(p.resolve("dados").resolve("mundo.sqlite"))) {
                raiz = p;
                return raiz;
            }
        }
        raiz = cwd;
        return raiz;
    }

    public static Path banco() {
        return raiz().resolve("dados").resolve("mundo.sqlite");
    }

    public static Path fotos() {
        return raiz().resolve("assets").resolve("fotos");
    }
}
