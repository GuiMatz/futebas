package br.futebas.ui.ferramentas;

import br.futebas.Jogo;
import br.futebas.dados.BancoMundo;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Posicao;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Foto;
import br.futebas.ui.comp.Tema;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoPartida.Atleta;
import br.futebas.ui.telas.EscolhaBatedor;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Gera um PNG da escolha do batedor do pênalti a favor (jogo de um técnico e multi), com o banco completo.
 * Uso, na raiz do FUTÉBAS: {@code java -cp ... br.futebas.ui.ferramentas.FotoBatedor [saida.png] [clube]}.
 */
public final class FotoBatedor {
    private FotoBatedor() {}

    public static void main(String[] args) throws Exception {
        String saida = args.length > 0 ? args[0] : "batedor.png";
        String clube = args.length > 1 ? args[1] : "palmeiras";
        BancoMundo db = Sessao.banco();
        Jogo j = Jogo.novoJogo(db, 7, clube, "Técnico");
        Clube meu = j.mundo().clube(clube);
        List<Jogador> el = new ArrayList<>(j.elenco(clube));
        el.removeIf(x -> x.posicao == Posicao.GOL);
        el.sort((a, b) -> b.forcaExibida() - a.forcaExibida());
        List<Atleta> cands = new ArrayList<>();
        java.util.Map<Integer, Jogador> por = new java.util.HashMap<>();
        int n = 0;
        for (Jogador x : el) {
            if (n++ >= 10) break;
            por.put(x.id, x);
            cands.add(new Atleta(x.id, x.nomeExibicao(), x.posicao, x.posicao, x.forcaExibida(), n == 6, 95 - n * 5));
        }
        java.util.Collections.shuffle(cands, new java.util.Random(3));
        Integer oficial = cands.get(4).id();
        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            App.carregarFontes();
            String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
            Ui.folhaDeEstilo = css;
            EscolhaBatedor.Fotos fotos = (id, w, h) -> {
                Jogador x = por.get(id);
                return Foto.de(j.foto(id), x == null ? null : x.dbId, w, h);
            };
            EscolhaBatedor um = new EscolhaBatedor(meu, null, cands, oficial, fotos, id -> { });
            EscolhaBatedor multi = new EscolhaBatedor(meu, "Técnico 2", cands, null, fotos, id -> { });
            HBox tudo = new HBox(28, um, multi);
            tudo.setAlignment(Pos.TOP_LEFT);
            tudo.setPadding(new Insets(36));
            tudo.setStyle("-fx-background-color: #0e1720;" + Tema.estiloAcento(meu.cor1, meu.cor2));
            Scene cena = new Scene(tudo);
            cena.getStylesheets().add(css);
            new javafx.stage.Stage().setScene(cena);
            javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2.5));
            p.setOnFinished(e -> {
                try {
                    cena.getRoot().applyCss();
                    cena.getRoot().layout();
                    WritableImage img = tudo.snapshot(null, null);
                    ImageIO.write(converter(img), "png", new File(saida));
                    System.out.println("[batedor] " + new File(saida).getAbsolutePath());
                } catch (Exception x) {
                    x.printStackTrace();
                } finally {
                    fim.countDown();
                }
            });
            p.play();
        });
        fim.await(60, TimeUnit.SECONDS);
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
