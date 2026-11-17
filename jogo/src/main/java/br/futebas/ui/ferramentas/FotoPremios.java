package br.futebas.ui.ferramentas;

import br.futebas.Jogo;
import br.futebas.dados.BancoMundo;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.telas.Hub;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * PNGs da tela de Prêmios (Bola de Ouro, Bola de Ouro jovem e Seleção da liga) com o banco
 * completo depois de uma temporada inteira simulada. A temporada é simulada uma vez e guardada em
 * {@code save} (as próximas execuções só carregam). Uso, na raiz do FUTÉBAS:
 * {@code java -cp ... br.futebas.ui.ferramentas.FotoPremios pasta save [clube=flamengo] [LxA ...]}.
 */
public final class FotoPremios {
    private FotoPremios() {}

    public static void main(String[] args) throws Exception {
        File pasta = new File(args.length > 0 ? args[0] : ".");
        pasta.mkdirs();
        Path save = Paths.get(args.length > 1 ? args[1] : "premios.futebas");
        String clube = args.length > 2 ? args[2] : "flamengo";
        String[] tamanhos = args.length > 3 ? java.util.Arrays.copyOfRange(args, 3, args.length) : new String[] {"1366x768", "1920x1080"};
        BancoMundo db = Sessao.banco();
        Jogo j;
        if (Files.exists(save)) {
            j = Jogo.carregar(save, db);
            System.out.println("[premios] save carregado: " + save);
        } else {
            long t0 = System.nanoTime();
            j = Jogo.novoJogo(db, 7, clube, "Técnico");
            j.mundo().tecnico.auxiliarTudo = true;
            int ano = j.mundo().ano;
            for (int i = 0; i < 70 && j.bolaDeOuro(ano).isEmpty(); i++) j.avancarSemana();
            j.salvar(save);
            System.out.printf("[premios] temporada %d simulada em %.0f s; salva em %s%n", ano, (System.nanoTime() - t0) / 1e9, save);
        }
        for (var sl : j.historia().selecoesDaLiga)
            System.out.println("[premios] liga " + sl.competicaoId + " -> " + br.futebas.ui.telas.PainelPremios.nomeLiga(sl) + " (" + sl.pais + ")");
        Sessao s = new Sessao(j, "premios");
        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        Platform.runLater(() -> {
            App.carregarFontes();
            Ui.folhaDeEstilo = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
        });
        String[] abas = {"bola-de-ouro", "bola-de-ouro-jovem", "selecao-da-liga"};
        for (String t : tamanhos) {
            String[] p = t.split("x");
            double w = Double.parseDouble(p[0]), h = Double.parseDouble(p[1]);
            for (int a = 0; a < abas.length; a++) {
                int aba = a;
                CountDownLatch fim = new CountDownLatch(1);
                Platform.runLater(() -> {
                    Hub hub = new Hub(s);
                    Scene cena = new Scene(hub.raiz(), w, h);
                    cena.getStylesheets().add(Ui.folhaDeEstilo);
                    Stage palco = new Stage();
                    palco.setScene(cena);
                    palco.setX(-w - 200);
                    palco.setY(0);
                    palco.show();
                    hub.selecionar("premios");
                    TabPane tp = achar(hub.raiz());
                    if (tp != null) tp.getSelectionModel().select(aba);
                    PauseTransition espera = new PauseTransition(Duration.seconds(2.5));
                    espera.setOnFinished(e -> {
                        if (Boolean.getBoolean("premios.debug")) for (Node q = tp; q != null; q = q.getParent())
                            System.out.println("  " + q.getClass().getSimpleName() + " " + q.localToScene(q.getLayoutBounds()) + " min=" + (q instanceof javafx.scene.layout.Region rr ? rr.minHeight(-1) : -1));
                        WritableImage img = cena.snapshot(null);
                        try {
                            File f = new File(pasta, "premios-" + abas[aba] + "-" + t + ".png");
                            ImageIO.write(paraBuffered(img), "png", f);
                            System.out.println("[premios] " + f.getAbsolutePath());
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        palco.close();
                        fim.countDown();
                    });
                    espera.play();
                });
                fim.await(60, TimeUnit.SECONDS);
            }
        }
        Platform.exit();
        System.exit(0);
    }

    private static TabPane achar(Node n) {
        if (n instanceof TabPane tp && tp.getTabs().size() == 3 && "Bola de Ouro".equals(tp.getTabs().get(0).getText())) return tp;
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) {
            TabPane r = achar(c);
            if (r != null) return r;
        }
        return null;
    }

    private static java.awt.image.BufferedImage paraBuffered(WritableImage img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        java.awt.image.BufferedImage b = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var r = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b.setRGB(x, y, r.getArgb(x, y));
        return b;
    }
}
