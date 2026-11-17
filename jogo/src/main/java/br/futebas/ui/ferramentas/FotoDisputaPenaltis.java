package br.futebas.ui.ferramentas;

import br.futebas.mundo.Posicao;
import br.futebas.ui.App;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoPartida.Atleta;
import br.futebas.ui.telas.OrdemPenaltis;
import br.futebas.ui.telas.SuspensePenalti;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Gera um PNG da tela de ordem dos batedores da disputa de pênaltis e do placar com as bolinhas
 * (verde gol, vermelha erro, cinza a bater; 6ª cobrança nas alternadas), para conferir o visual.
 * Uso: {@code java -cp ... br.futebas.ui.ferramentas.FotoDisputaPenaltis [saida.png]}.
 */
public final class FotoDisputaPenaltis {
    private FotoDisputaPenaltis() {}

    private static final String[] NOMES = {"Pedro", "Arrascaeta", "Bruno Henrique", "De la Cruz", "Gerson", "Plata", "Pulgar",
            "Léo Ortiz", "Varela", "Ayrton Lucas", "Rossi"};
    private static final Posicao[] POS = {Posicao.CA, Posicao.MEI, Posicao.PE, Posicao.MEI, Posicao.VOL, Posicao.PD, Posicao.VOL,
            Posicao.ZAG, Posicao.LD, Posicao.LE, Posicao.GOL};

    public static void main(String[] args) throws Exception {
        String saida = args.length > 0 ? args[0] : "disputa-penaltis.png";
        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                App.carregarFontes();
                String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
                Ui.folhaDeEstilo = css;
                List<Atleta> sug = new ArrayList<>();
                for (int i = 0; i < NOMES.length; i++) sug.add(new Atleta(i + 1, NOMES[i], POS[i], POS[i], 84 - i * 2, false, 70));
                VBox cab = new VBox(2, Ui.rotulo("Disputa de pênaltis", "titulo-tela"),
                        Ui.rotulo("Flamengo x Palmeiras  ·  escolha a ordem dos batedores", "texto-medio"));
                OrdemPenaltis ordem = new OrdemPenaltis(cab, sug, ids -> { });
                ordem.mover(3, 0);
                // placar com as bolinhas: 4 x 4 nas 5, alternadas na 6ª (Flamengo fez, Palmeiras perdeu)
                SuspensePenalti.Bolinhas a = new SuspensePenalti.Bolinhas(7), b = new SuspensePenalti.Bolinhas(7);
                boolean[] ga = {true, true, false, true, true, true}, gb = {true, false, true, true, true, false};
                for (int i = 0; i < 6; i++) {
                    a.marcar(ga[i]);
                    b.marcar(gb[i]);
                }
                VBox ladoA = new VBox(6, Ui.rotulo("Flamengo", "placar-time"), a), ladoB = new VBox(6, Ui.rotulo("Palmeiras", "placar-time"), b);
                ladoA.setAlignment(Pos.CENTER);
                ladoB.setAlignment(Pos.CENTER);
                VBox meio = new VBox(4, new HBox(14, Ui.rotulo("1", "placar-gols"), Ui.rotulo("–", "placar-gols"), Ui.rotulo("1", "placar-gols")),
                        Ui.rotulo("Pên. 5 x 4", "placar-relogio"));
                meio.setAlignment(Pos.CENTER);
                HBox placar = new HBox(30, ladoA, meio, ladoB);
                placar.setAlignment(Pos.CENTER);
                placar.getStyleClass().addAll("placar", "placar-cartao");
                placar.setPadding(new Insets(16));
                HBox tudo = new HBox(24, ordem, placar);
                tudo.setAlignment(Pos.TOP_LEFT);
                tudo.setPadding(new Insets(20));
                tudo.setStyle("-fx-background-color: #1c1f24;");
                Scene cena = new Scene(tudo);
                cena.getStylesheets().add(css);
                new javafx.stage.Stage().setScene(cena);
                cena.getRoot().applyCss();
                cena.getRoot().layout();
                WritableImage img = tudo.snapshot(null, null);
                ImageIO.write(converter(img), "png", new File(saida));
                System.out.println("[disputa] " + new File(saida).getAbsolutePath());
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
