package br.futebas.ui.telas;

import br.futebas.partida.Lance;
import br.futebas.ui.modelo.AcompanhamentoPartida;
import br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade;
import br.futebas.ui.telas.SuspensePenalti.Desfecho;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Suspense do pênalti: frases pelo desfecho, esperas pela velocidade e, sem espera, tudo na hora (testes determinísticos). */
class SuspensePenaltiTest {
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

    /** Requisito: o suspense do pênalti tem ritmo fixo (~1,2 s por linha), o mesmo em qualquer velocidade. */
    @Test
    void ritmoFixoEmQualquerVelocidade() {
        for (Velocidade v : Velocidade.visiveis()) {
            assertArrayEquals(new long[] {1200, 1200, 1200}, v.suspensePenalti(), v.nome);
            assertEquals(3600, v.suspenseTotal());
        }
        assertEquals(0, Velocidade.INSTANTANEA.suspenseTotal(), "testes e capturas: sem espera");
    }

    /** As linhas do pênalti na narração: sem espera, todas na hora; com espera, pular mostra o resto. */
    @Test
    void linhasNaNarracao() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                List<Integer> vistas = new java.util.ArrayList<>();
                boolean[] acabou = {false};
                SuspensePenalti.emLinhas(4, 0, vistas::add, () -> acabou[0] = true);
                assertEquals(List.of(0, 1, 2, 3), vistas);
                assertTrue(acabou[0]);
                List<Integer> v2 = new java.util.ArrayList<>();
                boolean[] a2 = {false};
                SuspensePenalti.Toque t = SuspensePenalti.emLinhas(4, 5000, v2::add, () -> a2[0] = true);
                assertEquals(List.of(0), v2, "a primeira linha já aparece; as outras esperam");
                t.pular();
                assertEquals(List.of(0, 1, 2, 3), v2, "SIMULAR ATÉ O FIM: o resto na hora");
                assertTrue(a2[0]);
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
    void frasesPeloQueAconteceu() {
        for (int i = 0; i < 200; i++) {
            assertTrue(List.of(br.futebas.partida.Frases.PENALTI_GOL).contains(SuspensePenalti.fraseResultado(Desfecho.GOL, "Weverton")));
            String d = SuspensePenalti.fraseResultado(Desfecho.DEFESA, "Weverton");
            assertTrue(d.equals("DEFENDEU WEVERTON!") || d.equals("Espalmou!") || d.equals("Pegou firme!"), d);
            assertNotEquals(Desfecho.GOL, SuspensePenalti.desfecho(false, "isolou"));
            assertEquals(Desfecho.DEFESA, SuspensePenalti.desfecho(false, "milagre"));
            Desfecho t = SuspensePenalti.desfecho(false, "trave");
            assertTrue(t == Desfecho.TRAVE || t == Desfecho.TRAVESSAO);
            assertTrue(SuspensePenalti.fraseIda("Pedro").contains("Pedro"));
        }
        assertEquals("ISOLOU!", SuspensePenalti.fraseResultado(Desfecho.ISOLOU, null));
        assertEquals(Desfecho.GOL, SuspensePenalti.desfechoDoLance("gol", "GOL DE PÊNALTI!"));
        assertEquals(Desfecho.DEFESA, SuspensePenalti.desfechoDoLance("defesa", ""));
        Lance pen = new Lance(30, 0, "defesa", "Pênalti perdido!", 0, 0, 0, 9, "Pedro", "Weverton", "penalti");
        Lance chute = new Lance(30, 0, "defesa", "Defesa!", 0, 0, 0, 9, "Pedro", "Weverton", null);
        assertTrue(AcompanhamentoPartida.resultadoDePenalti(pen));
        assertFalse(AcompanhamentoPartida.resultadoDePenalti(chute));
    }

    @Test
    void semEsperaTudoNaHora() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Label l = new Label();
                boolean[] acabou = {false};
                SuspensePenalti.tocar(l, "Pedro vai para a bola…", "Bateu…", "GOL!", true, new long[3], null, () -> acabou[0] = true);
                assertTrue(acabou[0]);
                assertEquals("GOL!", l.getText());
                // disputa: 5 x 4 cobranças, na hora
                SuspensePenalti.Disputa d = new SuspensePenalti.Disputa("A", "B");
                List<SuspensePenalti.Cobranca> cs = List.of(
                        new SuspensePenalti.Cobranca(0, "a1", Desfecho.GOL, "G"), new SuspensePenalti.Cobranca(1, "b1", Desfecho.DEFESA, "G"),
                        new SuspensePenalti.Cobranca(0, "a2", Desfecho.GOL, "G"), new SuspensePenalti.Cobranca(1, "b2", Desfecho.GOL, "G"),
                        new SuspensePenalti.Cobranca(0, "a3", Desfecho.ISOLOU, "G"), new SuspensePenalti.Cobranca(1, "b3", Desfecho.GOL, "G"),
                        new SuspensePenalti.Cobranca(0, "a4", Desfecho.GOL, "G"), new SuspensePenalti.Cobranca(1, "b4", Desfecho.TRAVE, "G"));
                boolean[] fimDisputa = {false};
                d.revelar(cs, new long[3], () -> fimDisputa[0] = true);
                assertTrue(fimDisputa[0]);
                assertArrayEquals(new int[] {3, 2}, d.placar());
                // pular no meio de uma disputa com espera: termina na hora
                SuspensePenalti.Disputa d2 = new SuspensePenalti.Disputa("A", "B");
                boolean[] fim2 = {false};
                d2.revelar(cs, new long[] {5000, 5000, 5000}, () -> fim2[0] = true);
                assertFalse(fim2[0], "com espera, não termina na hora");
                d2.pular();
                assertTrue(fim2[0], "SIMULAR ATÉ O FIM: o resto na hora");
                assertArrayEquals(new int[] {3, 2}, d2.placar());
            } catch (Throwable x) {
                erro.set(x);
            } finally {
                fim.countDown();
            }
        });
        assertTrue(fim.await(60, TimeUnit.SECONDS));
        if (erro.get() != null) throw new AssertionError(erro.get());
    }
}
