package br.futebas.ui.telas;

import br.futebas.mundo.Posicao;
import br.futebas.partida.Formacoes;
import br.futebas.ui.App;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
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

/**
 * Bug corrigido (Kings 3-2-1): o goleiro desenhado em cima dos zagueiros e a zaga desalinhada. Em
 * toda formação (futebol e Kings), nenhuma vaga (foto, nome, força, energia e cartão) encosta em
 * outra, e as vagas ficam dentro do campo.
 */
class CampoSemSobreposicaoTest {
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

    static final String[] KINGS = {"2-3-1", "3-2-1", "2-2-2", "3-1-2", "1-3-2", "4-2"};

    /** Sobreposições "formação: vaga i × vaga j" depois do layout. */
    static List<String> sobreposicoes(String nome, Posicao[] vagas, double[][] xy) {
        CampoEscalacao campo = new CampoEscalacao(new CampoEscalacao.Acoes() {
            @Override public void clicar(int vaga) {}
            @Override public void soltar(int vaga, String conteudo) {}
            @Override public String arraste(int vaga) { return null; }
            @Override public double encaixeDoArraste(int vaga) { return Double.NaN; }
        });
        List<CampoEscalacao.Peca> pecas = new ArrayList<>();
        for (int i = 0; i < vagas.length; i++) {
            // o pior caso comum: nome comprido, força, capitão, energia abaixo de 100 e amarelo
            pecas.add(new CampoEscalacao.Peca(true, "Nome Comprido " + i, vagas[i].name(), null, 88, i == 1, false, false, 64.0,
                    null, false, true, false));
        }
        campo.desenhar(pecas, xy, -1, -1);
        Scene cena = new Scene(new StackPane(campo), 600, 700);
        cena.getStylesheets().add(App.class.getResource("/br/futebas/ui/tema.css").toExternalForm());
        cena.getRoot().applyCss();
        cena.getRoot().layout();
        List<Bounds> caixas = new ArrayList<>();
        for (Node n : campo.getChildren()) if (n.getStyleClass().contains("vaga")) caixas.add(n.getBoundsInParent());
        List<String> l = new ArrayList<>();
        assertEquals(vagas.length, caixas.size(), nome);
        for (int i = 0; i < caixas.size(); i++) {
            Bounds a = caixas.get(i);
            if (a.getMinX() < -0.5 || a.getMinY() < -0.5 || a.getMaxX() > CampoEscalacao.W + 0.5 || a.getMaxY() > CampoEscalacao.H + 0.5)
                l.add(nome + ": vaga " + i + " (" + vagas[i] + ") fora do campo " + a);
            for (int k = i + 1; k < caixas.size(); k++) {
                Bounds b = caixas.get(k);
                boolean cruza = a.getMinX() < b.getMaxX() - 1 && b.getMinX() < a.getMaxX() - 1 && a.getMinY() < b.getMaxY() - 1 && b.getMinY() < a.getMaxY() - 1;
                if (cruza) l.add(nome + ": " + i + " (" + vagas[i] + ") × " + k + " (" + vagas[k] + ")");
            }
        }
        return l;
    }

    @Test
    void nenhumaVagaSobreOutra() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                List<String> problemas = new ArrayList<>();
                for (String f : Formacoes.nomes()) {
                    Posicao[] v = Formacoes.vagas(f);
                    problemas.addAll(sobreposicoes(f, v, CampoEscalacao.coordenadasOnze(f, v)));
                }
                for (String f : KINGS) {
                    Posicao[] v = br.futebas.kings.MotorKings.vagasDaFormacao(f, 7).subList(0, 7).toArray(Posicao[]::new);
                    problemas.addAll(sobreposicoes("Kings " + f, v, CampoEscalacao.coordenadasSete(f)));
                }
                assertTrue(problemas.isEmpty(), problemas.size() + " sobreposições:\n" + String.join("\n", problemas));
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
