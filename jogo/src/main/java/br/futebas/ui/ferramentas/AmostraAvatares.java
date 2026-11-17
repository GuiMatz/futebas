package br.futebas.ui.ferramentas;

import br.futebas.mundo.PerfilFoto;
import br.futebas.ui.comp.Foto;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Gera um PNG com avatares variados (países, idades e camisas diferentes) para conferir o visual
 * do {@link br.futebas.ui.comp.Avatar}. Uso: {@code java -cp ... br.futebas.ui.ferramentas.AmostraAvatares [saida.png]}.
 */
public final class AmostraAvatares {
    private AmostraAvatares() {}

    private static final String[][] AMOSTRA = {
            {"BRA", "SP", "2004", "#000000", "#c8102e"}, {"BRA", "BA", "1999", "#ffffff", "#000000"}, {"BRA", "RS", "1992", "#0033a0", "#ffffff"},
            {"ARG", null, "2001", "#75aadb", "#ffffff"}, {"NGA", null, "2003", "#008751", "#ffffff"}, {"ESP", null, "1996", "#c8102e", "#f1bf00"},
            {"DEU", null, "1990", "#ffffff", "#000000"}, {"JPN", null, "2002", "#1b2a6b", "#ffffff"}, {"MAR", null, "1998", "#c1272d", "#006233"},
            {"SWE", null, "2000", "#fecc00", "#006aa7"}, {"MEX", null, "1995", "#006847", "#ce1126"}, {"FRA", null, "2005", "#002654", "#ffffff"},
            {"SEN", null, "1994", "#ffffff", "#00853f"}, {"ITA", null, "1988", "#0066b3", "#ffffff"}, {"KOR", null, "1997", "#c8102e", "#000000"},
            {"COL", null, "2003", "#fcd116", "#003893"}, {"IND", null, "2001", "#ff9933", "#138808"}, {"TUR", null, "1993", "#e30a17", "#ffffff"},
            {"ENG", null, "1999", "#ffffff", "#1d428a"}, {"URY", null, "1987", "#5cbfeb", "#000000"}, {"JAM", null, "2002", "#fed100", "#009b3a"},
            {"POR", null, "2006", "#006600", "#ff0000"}, {"CHN", null, "1998", "#de2910", "#ffde00"}, {"BRA", "RJ", "1985", null, null},
    };

    public static void main(String[] args) throws Exception {
        String saida = args.length > 0 ? args[0] : "amostra-avatares.png";
        int semente = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                FlowPane grade = new FlowPane(10, 12);
                grade.setPrefWrapLength(8 * 106);
                HBox pequenos = new HBox(6), medios = new HBox(8);
                for (int i = 0; i < AMOSTRA.length; i++) {
                    String[] a = AMOSTRA[i];
                    PerfilFoto p = new PerfilFoto(PerfilFoto.hash("amostra" + semente + ":" + i), a[0].equals("POR") ? "PRT" : a[0], a[1],
                            Integer.parseInt(a[2]), a[3], a[4], null);
                    Label l = new Label(a[0] + (a[1] == null ? "" : "-" + a[1]) + " · " + (2026 - Integer.parseInt(a[2])));
                    l.setStyle("-fx-text-fill: #c9ced6; -fx-font-size: 11px;");
                    grade.getChildren().add(new VBox(3, Foto.de(p.chave(), null, 96, 116), l));
                    medios.getChildren().add(Foto.de(p.chave(), null, 50, 50));
                    pequenos.getChildren().add(Foto.de(p.chave(), null, 22, 26));
                }
                VBox tudo = new VBox(14, grade, medios, pequenos);
                tudo.setPadding(new Insets(16));
                tudo.setStyle("-fx-background-color: #1c1f24;");
                Scene cena = new Scene(tudo);
                cena.getRoot().applyCss();
                cena.getRoot().layout();
                WritableImage img = tudo.snapshot(null, null);
                ImageIO.write(converter(img), "png", new File(saida));
                System.out.println("[avatares] " + new File(saida).getAbsolutePath());
            } catch (Exception x) {
                x.printStackTrace();
            } finally {
                fim.countDown();
            }
        });
        fim.await(60, TimeUnit.SECONDS);
        Platform.exit();
    }

    private static java.awt.image.BufferedImage converter(WritableImage img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        java.awt.image.BufferedImage b = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javafx.scene.image.PixelReader r = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b.setRGB(x, y, r.getArgb(x, y));
        return b;
    }
}
