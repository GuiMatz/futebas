package br.futebas.ui.ferramentas;

import br.futebas.ui.App;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.telas.TelaPartida;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Gera um PNG com uma amostra das linhas da narração da partida (gol, cartões, troca, lesão, chances,
 * pênalti, fases) para conferir o visual. Uso: {@code java -cp ... br.futebas.ui.ferramentas.FotoNarracao [saida.png]}.
 */
public final class FotoNarracao {
    private FotoNarracao() {}

    public static void main(String[] args) throws Exception {
        String saida = args.length > 0 ? args[0] : "narracao.png";
        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                App.carregarFontes();
                String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
                Ui.folhaDeEstilo = css;
                VBox linhas = TelaPartida.amostraNarracao();
                linhas.setPrefWidth(820);
                VBox tudo = new VBox(linhas);
                tudo.setPadding(new Insets(16));
                tudo.setStyle("-fx-background-color: #1c1f24;");
                Scene cena = new Scene(tudo);
                cena.getStylesheets().add(css);
                new javafx.stage.Stage().setScene(cena);
                cena.getRoot().applyCss();
                cena.getRoot().layout();
                WritableImage img = tudo.snapshot(null, null);
                ImageIO.write(converter(img), "png", new File(saida));
                System.out.println("[narracao] " + new File(saida).getAbsolutePath());
            } catch (Exception x) {
                x.printStackTrace();
            } finally {
                fim.countDown();
            }
        });
        fim.await(30, TimeUnit.SECONDS);
        Platform.exit();
    }

    private static BufferedImage converter(WritableImage img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader r = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b.setRGB(x, y, r.getArgb(x, y));
        return b;
    }
}
