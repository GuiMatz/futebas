package br.futebas.ui;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.ui.modelo.Fluxo;
import br.futebas.ui.telas.Hub;
import br.futebas.ui.telas.PainelMundo;
import br.futebas.ui.telas.PainelTabelas;
import br.futebas.ui.telas.TelaDesempregado;
import br.futebas.ui.telas.TelaFimTemporada;
import br.futebas.ui.telas.TelaInicio;
import br.futebas.ui.telas.TelaNovoJogo;
import javafx.application.Platform;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Monta todas as telas com um mundo de teste (sem abrir janela): cada painel da central do
 * técnico, o fim de temporada, o desempregado e o "O mundo" depois de uma temporada.
 */
class TelasTest {
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
        if (erro.get() != null) throw new AssertionError("erro ao montar a tela", erro.get());
    }

    @Test
    void todasAsTelasMontam() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        String clube = FluxoTest.clubeSerieA();
        Jogo j = Jogo.novoJogo(Fixture.banco(), 31, clube, "Técnico");
        Sessao s = new Sessao(j, "teste");
        // algumas rodadas para ter dados em todas as telas
        for (int i = 0; i < 4; i++) s.fluxo.jogarProximaRodada(null);
        naTela(() -> {
            String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
            Hub h = new Hub(s);
            Scene cena = new Scene(h.raiz(), 1280, 800);
            cena.getStylesheets().add(css);
            for (String p : h.paineis()) {
                h.selecionar(p);
                assertEquals(p, h.painelAtual());
                h.raiz().applyCss();
                h.raiz().layout();
            }
            new Scene(new TelaInicio().raiz(), 1280, 800);
            new Scene(new TelaNovoJogo().raiz(), 1280, 800);
        });
        // fim de temporada
        Fluxo.ResumoTemporada fim = null;
        for (int i = 0; i < 200 && fim == null; i++) {
            Fluxo.Rodada r = s.fluxo.jogarProximaRodada(null);
            fim = r.fimDeTemporada();
            if (r.demitido()) fim = s.fluxo.avancarTemporada();
        }
        assertNotNull(fim);
        Fluxo.ResumoTemporada resumo = fim;
        naTela(() -> {
            new Scene(new TelaFimTemporada(s, resumo).raiz(), 1280, 800);
            new Scene(new TelaDesempregado(s).raiz(), 1280, 800);
            new Scene(new javafx.scene.layout.StackPane(new PainelMundo(s).construir()), 1280, 800);
            new Scene(new javafx.scene.layout.StackPane(new PainelTabelas(s, false).construir()), 1280, 800);
            Hub h = new Hub(s);
            if (j.meuClube() != null) for (String p : h.paineis()) h.selecionar(p);
        });
    }

    @Test
    void kingsLeagueComoTecnico() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        var db = br.futebas.Fixture.banco();
        assumeTrue(!db.kingsTimes.isEmpty(), "o banco de teste não tem Kings League");
        String time = db.kingsTimes.values().iterator().next().id();
        // time da Kings escolhido no jogo novo (carreira exclusiva: sem clube)
        Jogo j = Jogo.novoJogoKings(db, 9, null, time, "Técnico");
        Sessao s = new Sessao(j, "kings");
        assertEquals(time, j.mundo().tecnico.kingsTimeId);
        assertNull(j.meuClube());
        for (int i = 0; i < 4; i++) s.fluxo.jogarProximaRodada(null);
        // na carreira, assumir direto não vale mais (só por convite/candidatura)
        assertFalse(j.assumirTimeKings(time));
        naTela(() -> {
            Hub h = new Hub(s);
            new Scene(h.raiz(), 1280, 800);
            h.selecionar("kings");
            h.selecionar("mundo");
        });
    }

    @Test
    void carreiraSoDaKings() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        var db = br.futebas.Fixture.banco();
        assumeTrue(!db.kingsTimes.isEmpty(), "o banco de teste não tem Kings League");
        String time = db.kingsTimes.values().iterator().next().id();
        Jogo j = Jogo.novoJogoKings(db, 11, null, time, "Técnico");
        Sessao s = new Sessao(j, "so-kings");
        assertNull(j.meuClube());
        assertTrue(s.fluxo.soKings());
        for (int i = 0; i < 3; i++) s.fluxo.jogarProximaRodada(null);
        assertTrue(s.fluxo.soKings(), "continua técnico da Kings");
        naTela(() -> {
            String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
            Hub h = new Hub(s);
            Scene cena = new Scene(h.raiz(), 1280, 800);
            cena.getStylesheets().add(css);
            h.aoMostrar();
            assertEquals("jogo", h.painelAtual(), "a central da Kings abre no Próximo jogo (versão da Kings)");
            for (String p : h.paineis()) {
                h.selecionar(p);
                h.raiz().applyCss();
                h.raiz().layout();
            }
        });
    }
}
