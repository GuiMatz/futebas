package br.futebas.ui.telas;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.modelo.AcompanhamentoPartida;
import javafx.application.Platform;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Telas do modo multi-treinador: "Vez de <Nome>", a barra de cima com a vez, e a rodada ao vivo na
 * tela dividida (os dois jogos juntos, um botão de cada técnico, intervalo na ordem) até o fim.
 */
class MultiTreinadorTelasTest {
    private static boolean fx;

    @BeforeAll
    static void iniciarFx() throws Exception {
        CountDownLatch l = new CountDownLatch(1);
        try {
            Platform.startup(l::countDown);
            fx = l.await(10, TimeUnit.SECONDS);
        } catch (IllegalStateException jaIniciado) {
            fx = true;
        } catch (Throwable semTela) {
            fx = false;
        }
    }

    private static void naTela(Runnable r) throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch l = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                erro.set(t);
            } finally {
                l.countDown();
            }
        });
        assertTrue(l.await(120, TimeUnit.SECONDS), "a tela respondeu");
        if (erro.get() != null) throw new AssertionError("erro na tela", erro.get());
    }

    private static Jogo dois() {
        List<String> ids = new ArrayList<>();
        String pais = null;
        for (Jogo.OpcaoClube o : Jogo.clubesDisponiveis(Fixture.banco())) {
            if (!Integer.valueOf(1).equals(o.divisao())) continue;
            if (pais == null) pais = o.pais();
            if (pais.equals(o.pais()) && ids.size() < 2) ids.add(o.id());
        }
        return Jogo.novoJogoMulti(Fixture.banco(), 21,
                List.of(new Jogo.EscolhaTecnico("Ana", ids.get(0), null), new Jogo.EscolhaTecnico("Beto", ids.get(1), null)), null);
    }

    /** Kings no multi: os jogos da Kings dos dois técnicos na tela dividida, até o fim, sem erro. */
    @Test
    void kingsNaTelaDividida() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        Jogo j = Jogo.novoJogoMulti(Fixture.banco(), 22, List.of(new Jogo.EscolhaTecnico("Ana", null, "kb-1"),
                new Jogo.EscolhaTecnico("Beto", null, "kb-2")), null);
        Sessao s = new Sessao(j, null);
        String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
        boolean auto = App.automatico;
        App.automatico = true;
        AcompanhamentoPartida.escala = 0.02;
        int vistos = 0;
        try {
            for (int k = 0; k < 4 && vistos == 0; k++) {
                s.fluxo.avancarAteRodadaMulti();
                j.passarVez(0);
                TelaPartidaMulti[] t = new TelaPartidaMulti[1];
                naTela(() -> {
                    t[0] = new TelaPartidaMulti(s);
                    new Scene(t[0].raiz(), 1366, 768).getStylesheets().add(css);
                    t[0].rodarSemApp();
                });
                AtomicBoolean fim = new AtomicBoolean();
                AtomicReference<Integer> nk = new AtomicReference<>(0);
                for (int i = 0; i < 900 && !fim.get(); i++) {
                    Thread.sleep(200);
                    naTela(() -> {
                        fim.set(t[0].terminou());
                        nk.set(Math.max(nk.get(), t[0].jogosKingsNaTela()));
                        t[0].raiz().applyCss();
                        t[0].raiz().layout();
                    });
                }
                assertTrue(fim.get(), "a rodada da Kings termina");
                vistos += nk.get();
            }
            assertTrue(vistos > 0, "os jogos da Kings aparecem na tela dividida");
        } finally {
            App.automatico = auto;
            AcompanhamentoPartida.escala = 1.0;
        }
    }

    @Test
    void vezNaBarraEPartidaDividida() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        Jogo j = dois();
        Sessao s = new Sessao(j, null);
        s.fluxo.avancarAteRodadaMulti();
        String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
        // vez do Beto: a tela "Vez de" e a barra de cima dizem de quem é a vez
        j.passarVez(1);
        naTela(() -> {
            AtomicBoolean foi = new AtomicBoolean();
            TelaVez v = new TelaVez(s, () -> foi.set(true));
            new Scene(v.raiz(), 1280, 800).getStylesheets().add(css);
            assertEquals("Beto", v.nomeMostrado());
            v.tecla(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ENTER, false, false, false, false));
            assertTrue(foi.get(), "Enter começa a vez");
            Hub h = new Hub(s);
            new Scene(h.raiz(), 1280, 800).getStylesheets().add(css);
            h.aoMostrar();
            assertEquals("VEZ DE BETO  ·  2/2", h.textoVez());
            TelaDesempregado d = new TelaDesempregado(s); // a tela "sem clube" também passa a vez (monta sem erro)
            new Scene(d.raiz(), 1280, 800);
        });
        j.passarVez(0);
        naTela(() -> {
            Hub h = new Hub(s);
            new Scene(h.raiz(), 1280, 800).getStylesheets().add(css);
            h.aoMostrar();
            assertEquals("VEZ DE ANA  ·  1/2", h.textoVez());
        });

        // a rodada ao vivo na tela dividida (rápida: capturas)
        boolean auto = App.automatico;
        App.automatico = true;
        AcompanhamentoPartida.escala = 0.02;
        try {
            TelaPartidaMulti[] t = new TelaPartidaMulti[1];
            naTela(() -> {
                t[0] = new TelaPartidaMulti(s);
                new Scene(t[0].raiz(), 1366, 768).getStylesheets().add(css);
                t[0].rodarSemApp();
            });
            boolean fim = false;
            for (int k = 0; k < 600 && !fim; k++) {
                Thread.sleep(200);
                AtomicBoolean x = new AtomicBoolean();
                naTela(() -> x.set(t[0].terminou()));
                fim = x.get();
            }
            assertTrue(fim, "a rodada ao vivo termina");
            AtomicReference<Integer> n = new AtomicReference<>();
            naTela(() -> {
                n.set(t[0].jogosNaTela());
                t[0].raiz().applyCss();
                t[0].raiz().layout();
            });
            assertTrue(n.get() >= 1, "os jogos dos técnicos aparecem na tela dividida");
            assertTrue(j.tecnicos().get(0).jogos + j.tecnicos().get(1).jogos >= 2, "os dois técnicos jogaram");
        } finally {
            App.automatico = auto;
            AcompanhamentoPartida.escala = 1.0;
        }
    }
}
