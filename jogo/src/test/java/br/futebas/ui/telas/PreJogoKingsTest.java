package br.futebas.ui.telas;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Requisito: a tela antes do jogo da Kings igual à do futebol (os mesmos quadros, com os 7 de cada lado). */
class PreJogoKingsTest {
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

    private static void textos(Node n, List<String> l) {
        if (n instanceof javafx.scene.control.Labeled x && x.getText() != null) l.add(x.getText());
        if (n instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) textos(sp.getContent(), l);
        if (n instanceof javafx.scene.Parent p) for (Node c : p.getChildrenUnmodifiable()) textos(c, l);
    }

    @Test
    void preJogoDaKingsComOsSeteDeCadaLado() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 17, null, "kb-2", "Técnico");
        Sessao s = new Sessao(j, "prejogo-kings");
        s.fluxo.avancarSemana(); // a temporada da Kings começa na 2ª semana
        Partida p = s.fluxo.proximoJogoKings();
        assertNotNull(p);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Hub h = new Hub(s);
                Node tela = PainelPreJogo.construir(h, p, () -> { });
                Scene cena = new Scene(new StackPane(tela), 1366, 768);
                cena.getStylesheets().add(App.class.getResource("/br/futebas/ui/tema.css").toExternalForm());
                cena.getRoot().applyCss();
                cena.getRoot().layout();
                List<String> l = new ArrayList<>();
                textos(tela, l);
                assertTrue(l.contains("Seu time"), "o seu time: " + l);
                assertTrue(l.contains("Provável escalação"), "o adversário");
                assertTrue(l.contains("Força por setor") && l.contains("Confrontos recentes"), "os mesmos quadros do futebol");
                assertTrue(l.stream().anyMatch(x -> x.startsWith("JOGAR")), "JOGAR");
                assertTrue(l.contains("GOL"), "goleiro na escalação dos 7");
            } catch (Throwable x) {
                erro.set(x);
            } finally {
                fim.countDown();
            }
        });
        assertTrue(fim.await(120, TimeUnit.SECONDS));
        if (erro.get() != null) throw new AssertionError(erro.get());
    }
}
