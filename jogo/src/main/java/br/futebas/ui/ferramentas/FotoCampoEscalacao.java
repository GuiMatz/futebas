package br.futebas.ui.ferramentas;

import br.futebas.mundo.Posicao;
import br.futebas.partida.Formacoes;
import br.futebas.ui.App;
import br.futebas.ui.comp.Foto;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.telas.CampoEscalacao;
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
 * Gera um PNG do campo da escalação (sem abrir o jogo) para conferir o visual: antes do jogo (4-3-3),
 * ao vivo (4-4-2, com energia, cartões, expulso, lesionado e troca pendente) e a Kings (7, 2-3-1).
 * Uso: {@code java -cp ... br.futebas.ui.ferramentas.FotoCampoEscalacao [saida.png]} rodando na raiz
 * do FUTÉBAS (para achar assets/fotos).
 */
public final class FotoCampoEscalacao {
    private FotoCampoEscalacao() {}

    private static final String[] NOMES = {"Agustín Rossi", "Guillermo Varela", "Léo Ortiz", "Danilo Barbosa", "Ayrton Lucas",
            "Erick Pulgar", "Jorge Carrascal", "Giorgian de Arrascaeta", "Gonzalo Plata", "Luiz Araújo", "Antonio Sanabria",
            "Johan Carbonero", "Kevin De Bruyne"};
    private static final int[] FOTOS = {1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012, 1013};

    public static void main(String[] args) throws Exception {
        String saida = args.length > 0 ? args[0] : "campo-escalacao.png";
        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            App.carregarFontes();
            String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
            Ui.folhaDeEstilo = css;
            HBox tudo = new HBox(24);
            if (args.length > 1) for (int k = 1; k < args.length; k++) tudo.getChildren().add(bloco(args[k], campoFormacao(args[k])));
            else tudo.getChildren().addAll(
                    bloco("Antes do jogo · 4-3-3", campoAntes()),
                    bloco("Ao vivo · 4-4-2", campoAoVivo()),
                    bloco("Kings · 2-3-1", campoKings()));
            tudo.setPadding(new Insets(20));
            tudo.setStyle("-fx-background-color: #1c1f24;");
            Scene cena = new Scene(tudo);
            cena.getStylesheets().add(css);
            // as fotos chegam em segundo plano: espera um pouco antes da foto
            javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2.5));
            p.setOnFinished(e -> {
                try {
                    WritableImage img = tudo.snapshot(null, null);
                    ImageIO.write(converter(img), "png", new File(saida));
                    System.out.println("[campo] " + new File(saida).getAbsolutePath());
                } catch (Exception x) {
                    x.printStackTrace();
                } finally {
                    fim.countDown();
                }
            });
            new javafx.stage.Stage().setScene(cena); // a cena precisa de uma janela para o CSS e as animações
            cena.getRoot().applyCss();
            cena.getRoot().layout();
            p.play();
        });
        fim.await(30, TimeUnit.SECONDS);
        Platform.exit();
    }

    private static VBox bloco(String titulo, CampoEscalacao campo) {
        VBox v = new VBox(10, Ui.rotulo(titulo, "titulo-painel"), campo);
        v.setAlignment(Pos.TOP_LEFT);
        return v;
    }

    private static CampoEscalacao novo(String cor) {
        CampoEscalacao c = new CampoEscalacao(new CampoEscalacao.Acoes() {
            @Override public void clicar(int vaga) {}
            @Override public void soltar(int vaga, String conteudo) {}
            @Override public String arraste(int vaga) { return "x"; }
            @Override public double encaixeDoArraste(int vaga) { return Double.NaN; }
        });
        c.cor(cor);
        return c;
    }

    private static javafx.scene.Node foto(int i) {
        return Foto.de(null, FOTOS[i % FOTOS.length], 50, 50);
    }

    /** Uma formação qualquer, todos preenchidos (uso: FotoCampoEscalacao saida.png 5-3-2 3-5-2 ...). */
    private static CampoEscalacao campoFormacao(String f) {
        boolean sem = f.startsWith("sem:");
        if (sem) f = f.substring(4);
        CampoEscalacao c = novo(sem ? "#f2c500" : "#c8372d");
        Posicao[] v = Formacoes.vagas(f);
        List<CampoEscalacao.Peca> l = new ArrayList<>();
        for (int i = 0; i < v.length; i++)
            l.add(new CampoEscalacao.Peca(true, NOMES[i % NOMES.length], v[i].name(), sem && i % 2 == 0 ? Foto.de(null, null, 50, 50) : foto(i), 70 + i, false, false, false, null, null, false, false, false));
        c.desenhar(l, CampoEscalacao.coordenadasOnze(f, v), -1, -1);
        return c;
    }

    private static CampoEscalacao campoAntes() {
        CampoEscalacao c = novo("#c8372d");
        Posicao[] v = Formacoes.vagas("4-3-3");
        int[] forca = {78, 74, 76, 71, 73, 75, 77, 84, 72, 70, 76};
        List<CampoEscalacao.Peca> l = new ArrayList<>();
        for (int i = 0; i < v.length; i++) {
            if (i == 9) {
                l.add(CampoEscalacao.Peca.vazia(v[i].name()));
                continue;
            }
            l.add(new CampoEscalacao.Peca(true, NOMES[i], v[i].name(), foto(i), forca[i], i == 7, i == 4, i == 3, null, null, false,
                    false, i == 6));
        }
        c.desenhar(l, CampoEscalacao.coordenadasOnze("4-3-3", v), 8, -1);
        return c;
    }

    private static CampoEscalacao campoAoVivo() {
        CampoEscalacao c = novo("#1b4fa0");
        Posicao[] v = Formacoes.vagas("4-4-2");
        int[] forca = {80, 68, 74, 63, 55, 79, 72, 81, 66, 77, 48};
        double[] en = {100, 82, 71, 64, 47, 90, 58, 76, 88, 69, 95};
        List<CampoEscalacao.Peca> l = new ArrayList<>();
        for (int i = 0; i < v.length; i++) {
            boolean expulso = i == 3, entra = i == 10, lesao = i == 6;
            l.add(new CampoEscalacao.Peca(true, NOMES[(i + 2) % NOMES.length], v[i].name(), foto(i + 2), forca[i], i == 1, false, false,
                    expulso ? null : en[i], expulso ? "Expulso" : entra ? "Entra" : null, expulso, i == 5, lesao, entra));
        }
        c.desenhar(l, CampoEscalacao.coordenadasOnze("4-4-2", v), -1, -1);
        return c;
    }

    private static CampoEscalacao campoKings() {
        CampoEscalacao c = novo("#f2f2f2");
        Posicao[] v = br.futebas.kings.MotorKings.vagasDaFormacao("2-3-1", 7).subList(0, 7).toArray(Posicao[]::new);
        int[] forca = {72, 70, 69, 74, 75, 71, 78};
        List<CampoEscalacao.Peca> l = new ArrayList<>();
        for (int i = 0; i < v.length; i++) {
            l.add(new CampoEscalacao.Peca(true, NOMES[(i + 5) % NOMES.length], v[i].name(), foto(i + 5), forca[i], i == 4, false, false,
                    null, null, false));
        }
        c.desenhar(l, CampoEscalacao.coordenadasSete("2-3-1"), -1, -1);
        return c;
    }

    private static BufferedImage converter(WritableImage img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader r = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b.setRGB(x, y, r.getArgb(x, y));
        return b;
    }
}
