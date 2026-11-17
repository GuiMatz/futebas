package br.futebas.ui.modelo;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.historia.Historia;
import br.futebas.ui.telas.TelaCampeao;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Tela de campeão: quais títulos entram na fila (do técnico humano, sem sub-20) e a tela monta e continua. */
class ConquistasTest {
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

    private static Historia.Titulo titulo(String comp, String ambito, String campeao, int ano) {
        Historia.Titulo t = new Historia.Titulo();
        t.ano = ano;
        t.temporada = String.valueOf(ano);
        t.competicaoId = comp;
        t.competicao = comp.toUpperCase();
        t.ambito = ambito;
        t.campeaoId = campeao;
        t.campeao = campeao;
        return t;
    }

    private static String clubeSerieA() {
        for (Jogo.OpcaoClube o : Jogo.clubesDisponiveis(Fixture.banco())) if (Integer.valueOf(1).equals(o.divisao())) return o.id();
        throw new IllegalStateException("sem Série A");
    }

    @Test
    void soOsTitulosDoTimeDoTecnicoEntramNaFila() {
        String clube = clubeSerieA();
        Jogo j = Jogo.novoJogo(Fixture.banco(), 77, clube, "Técnico");
        Fluxo f = new Fluxo(j);
        var ts = j.mundo().historia.titulos;
        int antes = ts.size();
        var times = f.timesDosTecnicos();
        String outro = null;
        for (var c : j.mundo().clubes.values()) if (!c.id.equals(clube) && !c.virtual) { outro = c.id; break; }
        ts.add(titulo("liga-x", "nacional", clube, 2026));
        ts.add(titulo("copa-y", "copa_nacional", outro, 2026));
        ts.add(titulo("liga-x-sub20", "sub20", clube, 2026));
        ts.add(titulo("copa-z", "copa_nacional", clube, 2026));
        f.coletarConquistas(antes, times);
        assertTrue(f.temConquistas());
        List<Fluxo.Conquista> l = f.tirarConquistas();
        assertEquals(2, l.size(), "dois títulos do clube (o sub-20 e o do outro clube não contam): " + l);
        assertEquals("liga-x", l.get(0).competicaoId());
        assertEquals("copa-z", l.get(1).competicaoId());
        assertEquals(2026, l.get(0).ano());
        assertFalse(f.temConquistas(), "tirar esvazia a fila");
    }

    @Test
    void tituloDaKingsEntraNaFila() {
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 5, null, "kb-1", "T");
        Fluxo f = new Fluxo(j);
        var ts = j.mundo().historia.titulos;
        int antes = ts.size();
        var times = f.timesDosTecnicos();
        ts.add(titulo("kings-x-split1", "kings", "kb-1", 2026));
        f.coletarConquistas(antes, times);
        List<Fluxo.Conquista> l = f.tirarConquistas();
        assertEquals(1, l.size());
        assertEquals("kb-1", l.get(0).timeId());
    }

    @Test
    void telaMontaEContinuaNoEnter() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        Fluxo.Conquista c = new Fluxo.Conquista("Técnico", "x", "Time X", "bra-1", "Campeonato X 2026", "nacional", "BRA", 2026);
        AtomicInteger seguiu = new AtomicInteger();
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch l = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TelaCampeao t = new TelaCampeao(null, c, true, seguiu::incrementAndGet);
                assertNotNull(t.raiz());
                assertTrue(t.tecla(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false)));
                t.tecla(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
            } catch (Throwable x) {
                erro.set(x);
            } finally {
                l.countDown();
            }
        });
        assertTrue(l.await(30, TimeUnit.SECONDS));
        if (erro.get() != null) throw new AssertionError(erro.get());
        assertEquals(1, seguiu.get(), "continua uma vez só");
    }
}
