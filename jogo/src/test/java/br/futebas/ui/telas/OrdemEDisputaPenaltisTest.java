package br.futebas.ui.telas;

import br.futebas.mundo.Posicao;
import br.futebas.ui.modelo.AcompanhamentoPartida.Atleta;
import br.futebas.ui.telas.SuspensePenalti.Desfecho;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Disputa de pênaltis na tela: a escolha da ordem, as bolinhas do placar e a narração cobrança a cobrança. */
class OrdemEDisputaPenaltisTest {
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
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable x) {
                erro.set(x);
            } finally {
                fim.countDown();
            }
        });
        assertTrue(fim.await(60, TimeUnit.SECONDS));
        if (erro.get() != null) throw new AssertionError(erro.get());
    }

    @Test
    void escolherAOrdem() throws Exception {
        naTela(() -> {
            List<Atleta> sug = new ArrayList<>();
            for (int i = 0; i < 11; i++) sug.add(new Atleta(i + 1, "J" + (i + 1), Posicao.MEI, i == 10 ? Posicao.GOL : Posicao.MEI, 80 - i, false, 90));
            List<List<Integer>> resposta = new ArrayList<>();
            OrdemPenaltis o = new OrdemPenaltis(new javafx.scene.control.Label("x"), sug, resposta::add);
            assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), o.ids(), "já vem com a sugestão");
            o.mover(4, 0); // o 5º passa a bater primeiro
            assertEquals(List.of(5, 1, 2, 3, 4), o.ids().subList(0, 5));
            o.mover(0, 1);
            assertEquals(List.of(1, 5, 2), o.ids().subList(0, 3));
            o.automatica();
            assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), o.ids());
            o.mover(10, 9);
            o.confirmar();
            o.confirmar();
            assertEquals(1, resposta.size(), "confirma uma vez só");
            assertEquals(11, resposta.get(0).get(9));
        });
    }

    @Test
    void bolinhasCrescemNasAlternadas() throws Exception {
        naTela(() -> {
            SuspensePenalti.Bolinhas b = new SuspensePenalti.Bolinhas(7);
            assertEquals("-----", b.resumo());
            b.vez();
            b.marcar(true);
            b.marcar(false);
            assertEquals("GE---", b.resumo());
            for (int i = 0; i < 4; i++) b.marcar(true);
            assertEquals("GEGGGG", b.resumo(), "passou de 5: as bolinhas continuam crescendo");
            b.reiniciar();
            assertEquals("-----", b.resumo());
        });
    }

    @Test
    void narracaoCobrancaACobranca() throws Exception {
        naTela(() -> {
            List<SuspensePenalti.Cobranca> cs = new ArrayList<>();
            for (int i = 0; i < 12; i++) cs.add(new SuspensePenalti.Cobranca(i % 2, "B" + i, i == 11 ? Desfecho.DEFESA : Desfecho.GOL, "Goleiro"));
            List<String> linhas = new ArrayList<>();
            boolean[] fim = {false};
            SuspensePenalti.disputaEmLinhas(cs, 0, (i, c, etapa, txt) -> linhas.add(i + ":" + etapa), () -> fim[0] = true);
            assertTrue(fim[0]);
            assertEquals(12 * 3 + 1, linhas.size(), "ida, batida e resultado de cada uma, e o aviso das alternadas");
            assertEquals(List.of("0:0", "0:1", "0:2", "1:0"), linhas.subList(0, 4), "alternando os times, um passo por vez");
            assertEquals("10:" + SuspensePenalti.ALTERNADAS, linhas.get(30), "depois de 5 de cada: alternadas");
            // com espera: só a primeira linha; pular mostra o resto
            List<String> l2 = new ArrayList<>();
            boolean[] f2 = {false};
            SuspensePenalti.Toque t = SuspensePenalti.disputaEmLinhas(cs, 5000, (i, c, etapa, txt) -> l2.add(txt), () -> f2[0] = true);
            assertEquals(1, l2.size());
            assertTrue(l2.get(0).contains("B0"));
            t.pular();
            assertTrue(f2[0]);
            assertEquals(37, l2.size());
        });
    }
}
