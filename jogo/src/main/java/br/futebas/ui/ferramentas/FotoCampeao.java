package br.futebas.ui.ferramentas;

import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Fluxo;
import br.futebas.ui.telas.TelaCampeao;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * PNGs da tela de campeão ({@link TelaCampeao}) sem abrir o jogo: Flamengo campeão do Brasileirão
 * (1366×768 e 1920×1080), Corinthians na Libertadores, um time da Kings League Brazil, o Brasil na
 * Copa do Mundo e o multi-treinador (com o nome do técnico). Também um quadro no meio da entrada.
 * Uso: {@code java -cp ... br.futebas.ui.ferramentas.FotoCampeao [pasta=captura-campeao]} na raiz do FUTÉBAS.
 */
public final class FotoCampeao {
    private FotoCampeao() {}

    record Cena(String arquivo, Fluxo.Conquista c, boolean multi, double w, double h, long esperaMs) {}

    public static void main(String[] args) throws Exception {
        File pasta = new File(args.length > 0 ? args[0] : "captura-campeao");
        pasta.mkdirs();
        Sessao.banco();
        List<Cena> cenas = List.of(
                new Cena("campeao-flamengo-1366.png", new Fluxo.Conquista("Técnico", "flamengo", "Flamengo", "bra-1", "Campeonato Brasileiro Série A", "nacional", "BRA", 2026), false, 1366, 768, 1800),
                new Cena("campeao-flamengo-1920.png", new Fluxo.Conquista("Técnico", "flamengo", "Flamengo", "bra-1", "Campeonato Brasileiro Série A", "nacional", "BRA", 2026), false, 1920, 1080, 1800),
                new Cena("campeao-flamengo-entrada.png", new Fluxo.Conquista("Técnico", "flamengo", "Flamengo", "bra-1", "Campeonato Brasileiro Série A", "nacional", "BRA", 2026), false, 1366, 768, 420),
                new Cena("campeao-kings-1366.png", new Fluxo.Conquista("Técnico", "kings-brasil-g3x", "G3X FC", "kings-brasil-split1", "Kings League Brazil — Split 1", "kings", "BRA", 2026), false, 1366, 768, 1800),
                new Cena("campeao-corinthians-libertadores.png", new Fluxo.Conquista("Técnico", "corinthians", "Corinthians", "conmebol-libertadores", "Copa Libertadores da América", "continental", null, 2027), false, 1366, 768, 1800),
                new Cena("campeao-selecao-copa.png", new Fluxo.Conquista("Técnico", "sel-BRA", "Brasil", "fifa-copa-do-mundo", "Copa do Mundo FIFA 2026", "selecoes", null, 2026), false, 1920, 1080, 1800),
                new Cena("campeao-multi-palmeiras.png", new Fluxo.Conquista("Guilherme", "palmeiras", "Palmeiras", "bra-copa", "Copa do Brasil", "copa_nacional", "BRA", 2026), true, 1366, 768, 1800));

        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false); // fechar a janela de uma foto não encerra o JavaFX
        for (Cena c : cenas) foto(c, new File(pasta, c.arquivo()));
        Platform.exit();
        System.exit(0);
    }

    private static void foto(Cena c, File saida) throws Exception {
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            App.carregarFontes();
            String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
            Ui.folhaDeEstilo = css;
            TelaCampeao t = new TelaCampeao(null, c.c(), c.multi(), () -> {});
            Scene cena = new Scene(t.raiz(), c.w(), c.h());
            cena.getStylesheets().add(css);
            Stage palco = new Stage();
            palco.setScene(cena);
            palco.setX(-c.w() - 200); // fora da tela
            palco.setY(0);
            palco.show();
            t.aoMostrar();
            javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(javafx.util.Duration.millis(c.esperaMs()));
            p.setOnFinished(e -> {
                try {
                    WritableImage img = cena.snapshot(null);
                    ImageIO.write(converter(img), "png", saida);
                    System.out.println("[campeao] " + saida.getAbsolutePath());
                } catch (Exception x) {
                    x.printStackTrace();
                } finally {
                    palco.close();
                    fim.countDown();
                }
            });
            p.play();
        });
        if (!fim.await(20, TimeUnit.SECONDS)) System.out.println("[campeao] sem foto: " + saida);
    }

    private static BufferedImage converter(WritableImage img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader r = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b.setRGB(x, y, r.getArgb(x, y));
        return b;
    }
}
