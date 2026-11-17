package br.futebas.ui;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.telas.Hub;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
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
 * Desempenho com o banco de teste (pequeno): limites MUITO folgados, só para pegar regressões
 * grosseiras (um painel que volte a montar 12 tabelas, uma semana que volte a varrer o mundo à toa)
 * sem falhar num computador ocupado. Cada medida vale a melhor de 3. O teste de verdade, com o banco
 * completo e limites de 300 ms, é {@code ui.ferramentas.TestesDesempenho}.
 * {@code -Dfutebas.desempenho.fator=2} dobra os limites; {@code -Dfutebas.desempenho=pular} pula.
 */
class DesempenhoTest {
    private static boolean fx;
    private static final double FATOR = Double.parseDouble(System.getProperty("futebas.desempenho.fator", "1"));
    private static final boolean PULAR = "pular".equals(System.getProperty("futebas.desempenho"));

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
        if (erro.get() != null) throw new AssertionError(erro.get());
    }

    private static double medir(Hub h, Runnable r) {
        long t = System.nanoTime();
        r.run();
        h.raiz().applyCss();
        h.raiz().layout();
        return (System.nanoTime() - t) / 1e6;
    }

    @Test
    void semanasEPaineisDentroDosLimites() throws Exception {
        assumeTrue(fx && !PULAR, "JavaFX indisponível ou desempenho pulado");
        Jogo j = Jogo.novoJogo(Fixture.banco(), 41, FluxoTest.clubeSerieA(), "Técnico");
        j.mundo().tecnico.auxiliarTudo = true;
        Sessao s = new Sessao(j, "desempenho");
        List<Double> semanas = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            long t = System.nanoTime();
            s.fluxo.avancarSemana();
            semanas.add((System.nanoTime() - t) / 1e6);
        }
        double media = semanas.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        assertTrue(media < 1500 * FATOR, "semana média " + Math.round(media) + " ms (banco de teste)");

        Hub.prepararCentral(s);
        List<String> lentos = new ArrayList<>();
        naTela(() -> {
            String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
            Hub h = new Hub(s);
            new Scene(h.raiz(), 1366, 768).getStylesheets().add(css);
            double primeira = medir(h, () -> h.selecionar("jogo"));
            if (primeira > 8000 * FATOR) lentos.add("1ª central: " + Math.round(primeira) + " ms");
            for (String p : h.paineis()) {
                if (!h.painelVisivel(p)) continue;
                medir(h, () -> h.selecionar(p));
                Ui.montarAdiados();
                double melhor = Double.MAX_VALUE;
                for (int i = 0; i < 3; i++) melhor = Math.min(melhor, medir(h, () -> h.selecionar(p)));
                if (melhor > 1500 * FATOR) lentos.add(p + ": " + Math.round(melhor) + " ms");
            }
        });
        assertTrue(lentos.isEmpty(), "painéis lentos (2ª abertura, banco de teste): " + lentos);
    }

    @Test
    void imagemForaDaTelaVemNaHoraENaTelaChegaDepois() throws Exception {
        assumeTrue(fx, "JavaFX indisponível");
        String raiz = "assets/escudos/flamengo.png";
        assumeTrue(Imagens.arquivo(raiz) != null, "sem o escudo para testar");
        // fora da thread da tela: na hora
        AtomicReference<Image> fora = new AtomicReference<>();
        assertTrue(Imagens.pedir(raiz, 30, 30, fora::set));
        assertNotNull(fora.get());
        // na tela, num tamanho novo: em segundo plano, entregue depois na thread da tela
        AtomicBoolean naHora = new AtomicBoolean();
        AtomicReference<Image> depois = new AtomicReference<>();
        CountDownLatch chegou = new CountDownLatch(1);
        AtomicBoolean naThreadDaTela = new AtomicBoolean();
        naTela(() -> naHora.set(Imagens.pedir(raiz, 17, 17, img -> {
            naThreadDaTela.set(Platform.isFxApplicationThread());
            depois.set(img);
            chegou.countDown();
        })));
        assertTrue(chegou.await(20, TimeUnit.SECONDS), "a imagem chegou");
        assertNotNull(depois.get());
        assertTrue(naThreadDaTela.get(), "entregue na thread da tela");
        // regra do jogo (13/11): a foto aparece junto com a tela; só o que passa do orçamento vai para o segundo plano
        assertTrue(naHora.get(), "na tela, dentro do orçamento, a imagem vem na hora");
    }

    @Test
    void blocoAdiadoMontaDepoisESoSeAindaEstiverNaTela() throws Exception {
        assumeTrue(fx, "JavaFX indisponível");
        naTela(() -> {
            StackPane raiz = new StackPane();
            new Scene(raiz, 200, 200);
            int[] montados = {0};
            Node a = Ui.adiado(() -> {
                montados[0]++;
                return new StackPane();
            }, 100, 50);
            Node fora = Ui.adiado(() -> {
                montados[0] += 10;
                return new StackPane();
            }, 100, 50);
            raiz.getChildren().add(a);
            assertEquals(0, montados[0], "não monta na abertura");
            Ui.montarAdiados();
            assertEquals(1, montados[0], "monta o que está na tela e pula o que saiu");
            assertEquals(1, ((StackPane) a).getChildren().size());
            assertNotNull(fora);
        });
    }
}
