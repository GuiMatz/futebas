package br.futebas.ui.ferramentas;

import br.futebas.ui.App;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.telas.TelaInicio;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * PNGs da tela inicial em vários tamanhos (para conferir o fundo do menu).
 * Uso: {@code java -cp ... br.futebas.ui.ferramentas.FotoInicio pasta [LxA ...]} (padrão 1366x768 e 1920x1080),
 * rodando na raiz do FUTÉBAS.
 */
public final class FotoInicio {
    private FotoInicio() {}

    public static void main(String[] args) throws Exception {
        File pasta = new File(args.length > 0 ? args[0] : ".");
        pasta.mkdirs();
        String[] tamanhos = args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[] {"1366x768", "1920x1080"};
        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        Platform.runLater(() -> {
            App.carregarFontes();
            Ui.folhaDeEstilo = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
        });
        for (String t : tamanhos) {
            String[] p = t.split("x");
            double w = Double.parseDouble(p[0]), h = Double.parseDouble(p[1]);
            CountDownLatch fim = new CountDownLatch(1);
            Platform.runLater(() -> {
                long t0 = System.nanoTime();
                TelaInicio tela = new TelaInicio();
                Scene cena = new Scene(tela.raiz(), w, h);
                cena.getStylesheets().add(Ui.folhaDeEstilo);
                Stage palco = new Stage();
                palco.setScene(cena);
                palco.setX(-w - 200); // fora da tela: o mouse não passa por cima
                palco.setY(0);
                palco.show();
                tela.aoMostrar();
                System.out.printf("[inicio] %s montada em %.0f ms%n", t, (System.nanoTime() - t0) / 1e6);
                PauseTransition espera = new PauseTransition(Duration.seconds(2.5));
                espera.setOnFinished(e -> {
                    WritableImage img = cena.snapshot(null);
                    try {
                        File f = new File(pasta, "inicio-" + t + ".png");
                        ImageIO.write(paraBuffered(img), "png", f);
                        System.out.println("[inicio] " + f.getAbsolutePath());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    palco.close();
                    fim.countDown();
                });
                espera.play();
            });
            fim.await(30, TimeUnit.SECONDS);
        }
        Platform.exit();
    }

    private static java.awt.image.BufferedImage paraBuffered(WritableImage img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        java.awt.image.BufferedImage b = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var r = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b.setRGB(x, y, r.getArgb(x, y));
        return b;
    }
}
