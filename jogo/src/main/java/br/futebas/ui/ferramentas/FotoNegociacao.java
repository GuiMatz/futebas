package br.futebas.ui.ferramentas;

import br.futebas.Jogo;
import br.futebas.dados.BancoMundo;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.StatusJogador;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Tema;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.telas.DialogoNegocio;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Gera um PNG dos diálogos de negociação do Mercado (proposta, passe livre e empréstimo) com o banco
 * completo, sem abrir o jogo. Uso, na raiz do FUTÉBAS:
 * {@code java -cp ... br.futebas.ui.ferramentas.FotoNegociacao [saida.png] [meu-clube]}.
 */
public final class FotoNegociacao {
    private FotoNegociacao() {}

    public static void main(String[] args) throws Exception {
        String saida = args.length > 0 ? args[0] : "negociacao.png";
        String clube = args.length > 1 ? args[1] : "internacional";
        BancoMundo db = Sessao.banco();
        Jogo j = Jogo.novoJogo(db, 7, clube, "Técnico");
        Clube meu = j.mundo().clube(clube);
        Jogador alvo = null, livre = null, emp = null;
        for (Jogador x : j.mundo().jogadores) {
            boolean temFoto = x.dbId != null && new File("assets/fotos/" + x.dbId + ".png").exists();
            if (x.clubeId != null && !x.clubeId.equals(clube) && x.status != StatusJogador.LIVRE && temFoto) {
                if (alvo == null && x.forcaExibida() >= 74 && x.forcaExibida() <= 80 && "BRA".equals(j.mundo().clube(x.clubeId).pais)) alvo = x;
                else if (emp == null && j.taxaEmprestimo(x.id) > 0) emp = x;
            }
            if (livre == null && (x.status == StatusJogador.LIVRE || x.clubeId == null) && x.forcaExibida() >= 60) livre = x;
        }
        Jogador fa = alvo, fl = livre, fe = emp;
        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            App.carregarFontes();
            String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
            Ui.folhaDeEstilo = css;
            HBox tudo = new HBox(28);
            tudo.setAlignment(Pos.TOP_LEFT);
            if (fa != null) tudo.getChildren().add(DialogoNegocio.caixa(DialogoNegocio.montar(j, meu, fa, DialogoNegocio.Tipo.COMPRA), "Enviar proposta"));
            VBox dir = new VBox(28);
            if (fl != null) dir.getChildren().add(DialogoNegocio.caixa(DialogoNegocio.montar(j, meu, fl, DialogoNegocio.Tipo.LIVRE), "Contratar"));
            if (fe != null) dir.getChildren().add(DialogoNegocio.caixa(DialogoNegocio.montar(j, meu, fe, DialogoNegocio.Tipo.EMPRESTIMO), "Pedir emprestado"));
            tudo.getChildren().add(dir);
            tudo.setPadding(new Insets(36));
            tudo.setStyle("-fx-background-color: #0e1720;");
            Scene cena = new Scene(tudo);
            cena.getStylesheets().add(css);
            tudo.setStyle(tudo.getStyle() + Tema.estiloAcento(meu.cor1, meu.cor2));
            javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2.5));
            p.setOnFinished(e -> {
                try {
                    WritableImage img = tudo.snapshot(null, null);
                    ImageIO.write(converter(img), "png", new File(saida));
                    System.out.println("[negociacao] " + new File(saida).getAbsolutePath());
                } catch (Exception x) {
                    x.printStackTrace();
                } finally {
                    fim.countDown();
                }
            });
            new javafx.stage.Stage().setScene(cena);
            cena.getRoot().applyCss();
            cena.getRoot().layout();
            p.play();
        });
        fim.await(60, TimeUnit.SECONDS);
        Platform.exit();
        System.exit(0);
    }

    private static BufferedImage converter(WritableImage img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader r = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b.setRGB(x, y, r.getArgb(x, y));
        return b;
    }
}
