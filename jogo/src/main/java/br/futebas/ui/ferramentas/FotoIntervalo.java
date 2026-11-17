package br.futebas.ui.ferramentas;

import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoPartida;
import br.futebas.ui.modelo.MataMataAoVivo;
import br.futebas.ui.modelo.TabelaAoVivo;
import br.futebas.ui.telas.TelaPartida;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * PNG da camada do intervalo de verdade (sem abrir o jogo inteiro): carreira nova com o banco completo,
 * avança até a semana em que o seu time joga uma fase de pontos ("tabela") ou de mata-mata ("mata"),
 * roda a partida na {@link TelaPartida} acelerada e fotografa quando a camada de trocas abre no intervalo.
 * Uso: {@code java -cp ... br.futebas.ui.ferramentas.FotoIntervalo [saida.png] [clube=flamengo] [tabela|mata|lesao] [largura=1366] [altura=768]}
 * ({@code lesao}: abre a camada aos 16' com o título de Lesão).
 * rodando na raiz do FUTÉBAS.
 */
public final class FotoIntervalo {
    private FotoIntervalo() {}

    public static void main(String[] args) throws Exception {
        String saida = args.length > 0 ? args[0] : "intervalo.png";
        String clube = args.length > 1 ? args[1] : "flamengo";
        boolean mata = args.length > 2 && args[2].equals("mata");
        boolean lesao = args.length > 2 && args[2].equals("lesao");
        double largura = args.length > 3 ? Double.parseDouble(args[3]) : 1366;
        double altura = args.length > 4 ? Double.parseDouble(args[4]) : 768;
        BancoMundo db = Sessao.banco();
        Jogo j = Jogo.novoJogo(db, 7, clube, "Técnico");
        j.mundo().tecnico.auxiliarTudo = true;
        Sessao s = new Sessao(j, "foto-intervalo");
        for (int i = 0; i < 60 && !serve(s, mata); i++) j.avancarSemana();
        if (!serve(s, mata)) throw new IllegalStateException("nenhum jogo " + (mata ? "de mata-mata" : "de tabela") + " em 60 semanas");
        j.mundo().tecnico.auxiliarTudo = false;
        Partida p = s.fluxo.proximoJogo();
        System.out.println("[intervalo] semana " + j.semana() + ": " + p.mandante + " x " + p.visitante + " (" + p.torneioId + ")");
        AcompanhamentoPartida.escala = 0.03;

        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            App.carregarFontes();
            String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
            Ui.folhaDeEstilo = css;
            TelaPartida t = new TelaPartida(s);
            Scene cena = new Scene(t.raiz(), largura, altura);
            cena.getStylesheets().add(css);
            Stage palco = new Stage();
            palco.setScene(cena);
            palco.setX(-largura - 200); // fora da tela: o mouse não passa por cima (hover)
            palco.setY(0);
            palco.show();
            t.aoMostrar();
            long[] aberto = {0};
            boolean[] pediu = {false};
            new AnimationTimer() {
                @Override
                public void handle(long agora) {
                    // sem a pausa do intervalo nas Opções: abre pela tecla T no começo do 2º tempo
                    if (lesao && !t.painelAberto() && !pediu[0] && t.minuto() >= 16) {
                        pediu[0] = true;
                        t.abrirComoLesao();
                    }
                    if (!t.painelAberto() && !pediu[0] && t.minuto() >= 46) {
                        pediu[0] = true;
                        t.tecla(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.T, false, false, false, false));
                    }
                    // pênalti a favor no caminho: batedor oficial (Esc) e segue
                    if (t.batedorAberto()) {
                        t.tecla(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
                        return;
                    }
                    if (!t.painelAberto()) return;
                    if (aberto[0] == 0) aberto[0] = agora;
                    if (agora - aberto[0] < 2_500_000_000L) return; // fotos e escudos chegando
                    stop();
                    try {
                        WritableImage img = cena.snapshot(null);
                        ImageIO.write(converter(img), "png", new File(saida));
                        System.out.println("[intervalo] " + new File(saida).getAbsolutePath());
                    } catch (Exception x) {
                        x.printStackTrace();
                    } finally {
                        fim.countDown();
                    }
                }
            }.start();
        });
        boolean ok = fim.await(240, TimeUnit.SECONDS);
        if (!ok) System.out.println("[intervalo] a camada não abriu a tempo");
        Platform.exit();
        System.exit(ok ? 0 : 1);
    }

    /** A semana tem jogo do seu time numa fase de pontos (ou de mata-mata, com {@code mata})? */
    private static boolean serve(Sessao s, boolean mata) {
        if (!s.fluxo.temJogoNaSemana()) return false;
        Partida p = s.fluxo.proximoJogo();
        if (p == null || p.semana != s.jogo.semana()) return false;
        Torneio t = s.fluxo.torneioDe(p);
        return mata ? MataMataAoVivo.de(t, p) != null : TabelaAoVivo.de(t, p) != null;
    }

    private static BufferedImage converter(WritableImage img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader r = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b.setRGB(x, y, r.getArgb(x, y));
        return b;
    }
}
