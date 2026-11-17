package br.futebas.selecao;

import br.futebas.mundo.Jogador;
import br.futebas.mundo.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seleções: força top-heavy e jogos que respeitam a diferença de força. */
class ForcaSelecaoTest {
    static List<Jogador> elenco(double craques, double resto) {
        List<Jogador> l = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            Jogador j = new Jogador();
            j.id = i;
            j.forca = i < 11 ? craques : resto;
            l.add(j);
        }
        return l;
    }

    @Test
    void forcaPesaOsMelhores() {
        double topo = Selecoes.forcaDosMelhores(elenco(86, 60));
        assertTrue(topo > 80, "os 11 melhores dominam: " + topo);
    }

    @Test
    void favoritoVenceNaMaioria() {
        Rng rng = new Rng(7);
        int v = 0, d = 0, n = 20_000;
        for (int i = 0; i < n; i++) {
            int[] g = Selecoes.placarSelecoes(86, 79, false, rng);
            if (g[0] > g[1]) v++;
            else if (g[0] < g[1]) d++;
        }
        assertTrue(v > 0.55 * n, "favorito vence " + v);
        assertTrue(d < 0.20 * n, "zebra " + d);
    }
}
