package br.futebas.ui.comp;

import br.futebas.kings.LigaKings;
import br.futebas.kings.MotorKings;
import br.futebas.mundo.Posicao;
import br.futebas.ui.App;
import br.futebas.ui.modelo.AcompanhamentoKings;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cartas da Kings no estilo das oficiais (requisito, 12/11/2026): todo tipo de carta desenha
 * (grande, mini e pequena), com o título certo, a face virada, o estado "USADA · resultado"; e os
 * botões ao lado de "Tática e trocas" (carta e pênalti do presidente).
 */
class CartaKingsTest {
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
        assertTrue(l.await(60, TimeUnit.SECONDS), "a tela respondeu");
        if (erro.get() != null) throw new AssertionError("erro ao montar a tela", erro.get());
    }

    private static String titulo(Node n) {
        for (Node x : n.lookupAll(".kings-carta-titulo")) if (x instanceof Label l) return l.getText();
        return null;
    }

    @Test
    void todoTipoDeCartaDesenha() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        naTela(() -> {
            FlowPane p = new FlowPane(8, 8);
            Scene sc = new Scene(p, 1400, 900);
            sc.getStylesheets().add(App.class.getResource("/br/futebas/ui/tema.css").toExternalForm());
            for (MotorKings.Carta c : MotorKings.Carta.values()) {
                CartaKings grande = new CartaKings(null, 150), mini = CartaKings.mini(c), pequena = CartaKings.pequena(c, 21);
                grande.mostrar(c);
                CartaKings usada = CartaKings.mini(c);
                usada.usada("GOL");
                p.getChildren().addAll(grande, mini, pequena, usada);
                p.applyCss();
                p.layout();
                assertTrue(grande.aberta() && grande.carta() == c);
                assertEquals(CartaKings.titulo(c), titulo(grande), "título na carta grande");
                assertEquals(CartaKings.titulo(c), titulo(mini), "título na mini");
                assertNull(titulo(pequena), "a pequena (botão) é só o escudo e o ícone");
                assertNotNull(CartaKings.icone(c, 1));
                assertTrue(grande.getWidth() >= 149 && grande.getHeight() >= 200, "carta grande ~150 × 210: " + grande.getWidth() + " × " + grande.getHeight());
                assertTrue(usada.foiUsada());
                Label selo = (Label) usada.lookup("#selo");
                assertNotNull(selo);
                assertEquals("USADA · GOL", selo.getText());
                assertFalse(CartaKings.efeito(c).isBlank());
            }
            // títulos definidos para as cartas
            assertEquals("GOL 2X", CartaKings.titulo(MotorKings.Carta.GOL_DUPLO));
            assertEquals("SHOOT OUT", CartaKings.titulo(MotorKings.Carta.PENALTI_SHOOTOUT));
            assertEquals("JOGADOR ESTRELA", CartaKings.titulo(MotorKings.Carta.JOGADOR_ESTRELA));
            assertEquals("PÊNALTI", CartaKings.titulo(MotorKings.Carta.PENALTI));
            assertEquals("CORINGA", CartaKings.titulo(MotorKings.Carta.CORINGA));
            assertEquals("SUSPENSÃO", CartaKings.titulo(MotorKings.Carta.SANCAO));
            // face virada (a sua antes do sorteio, a do rival com "?")
            CartaKings virada = CartaKings.miniVirada(null);
            p.getChildren().add(virada);
            p.applyCss();
            p.layout();
            assertFalse(virada.aberta());
            assertNotNull(virada.lookup(".kings-carta-monograma"));
            assertEquals("?", ((Label) virada.lookup(".kings-carta-monograma")).getText());
            // brilho de "pode usar"
            CartaKings b = CartaKings.mini(MotorKings.Carta.PENALTI);
            b.brilhar(true);
            assertTrue(b.brilhando());
            b.brilhar(false);
            assertFalse(b.brilhando());
            assertTrue(CartaKings.forma(0, 0, 100, 140).startsWith("M ") && CartaKings.forma(0, 0, 100, 140).endsWith("Z"));
        });
    }

    @Test
    void botoesDaKingsCartaEPresidente() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        naTela(() -> {
            AcompanhamentoKings acomp = new AcompanhamentoKings(0, e -> { });
            BotoesKings bk = new BotoesKings(acomp);
            Scene sc = new Scene(new FlowPane(bk), 600, 200);
            sc.getStylesheets().add(App.class.getResource("/br/futebas/ui/tema.css").toExternalForm());
            bk.novoJogo("Gaules");
            assertFalse(bk.carta().aberta(), "antes do sorteio: virada");
            assertTrue(bk.botaoPresidente().isDisabled(), "presidente só na fase normal");
            LigaKings.AtletaKings meu = new LigaKings.AtletaKings(), rival = new LigaKings.AtletaKings(), gk = new LigaKings.AtletaKings();
            meu.nome = "Meu";
            meu.posicao = Posicao.CA;
            rival.nome = "Rival";
            rival.posicao = Posicao.MC;
            gk.nome = "Goleiro";
            gk.posicao = Posicao.GOL;
            for (MotorKings.Carta c : MotorKings.Carta.values()) {
                AcompanhamentoKings.Estado e = new AcompanhamentoKings.Estado(8, 0, 0, 7, 7, "normal", null, List.of(), c, true, true, null, false,
                        List.of(gk, meu), List.of(gk, rival), null);
                bk.novoJogo("Gaules");
                bk.atualizar(e);
                bk.applyCss();
                bk.layout();
                assertTrue(bk.botaoCarta().getStyleClass().contains("pronto"), "pode usar: o botão acende");
                assertFalse(bk.botaoPresidente().isDisabled());
                // quem a carta deixa escolher: estrela = um seu de linha; suspensão e invertido = um rival de linha (nunca o goleiro)
                List<LigaKings.AtletaKings> alvos = BotoesKings.alvos(c, e);
                switch (c) {
                    case JOGADOR_ESTRELA -> assertEquals(List.of(meu), alvos);
                    case SANCAO, PENALTI_INVERTIDO -> assertEquals(List.of(rival), alvos);
                    default -> assertTrue(alvos.isEmpty());
                }
                bk.cartaUsada("GOL");
                assertTrue(bk.botaoCarta().isDisabled());
                assertTrue(bk.carta().foiUsada());
            }
            bk.presidenteCobrado("GOL");
            assertTrue(bk.botaoPresidente().isDisabled());
            assertNotNull(BotoesKings.iconePresidente());
        });
    }
}
