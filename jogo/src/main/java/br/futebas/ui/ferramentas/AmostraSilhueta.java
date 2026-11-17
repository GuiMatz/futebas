package br.futebas.ui.ferramentas;

import br.futebas.ui.comp.Foto;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** PNG da silhueta (jogador sem foto real) em vários tamanhos. uso: AmostraSilhueta saida.png */
public final class AmostraSilhueta {
    public static void main(String[] args) throws Exception {
        String saida = args.length > 0 ? args[0] : "silhueta.png";
        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                HBox h = new HBox(14, Foto.de(null, null, 96, 116), Foto.de(null, null, 64, 78), Foto.de(null, null, 54, 64),
                        Foto.de(null, null, 40, 48), Foto.de(null, null, 22, 26));
                h.setPadding(new Insets(16));
                h.setStyle("-fx-background-color: #1c1f24;");
                Scene cena = new Scene(h);
                cena.getRoot().applyCss();
                cena.getRoot().layout();
                WritableImage img = h.snapshot(null, null);
                int w = (int) img.getWidth(), hh = (int) img.getHeight();
                java.awt.image.BufferedImage b = new java.awt.image.BufferedImage(w, hh, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < hh; y++) for (int x = 0; x < w; x++) b.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                ImageIO.write(b, "png", new File(saida));
            } catch (Exception x) {
                x.printStackTrace();
            } finally {
                fim.countDown();
            }
        });
        fim.await(30, TimeUnit.SECONDS);
        Platform.exit();
    }
}
