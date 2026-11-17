package br.futebas.partida;

import br.futebas.ferramentas.CalibrarMotor;
import br.futebas.mundo.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Disputa de pênaltis com a regra real (requisito): 5 alternadas, acaba cedo quando um não alcança
 * mais o outro, depois alternadas até um errar e o outro acertar; ordem escolhida pelo técnico (ou a
 * automática); só quem terminou em campo bate e ninguém repete antes de todos baterem.
 */
class DisputaPenaltisTest {

    private static TimePartida time(String n, Rng rng) {
        return CalibrarMotor.time(n, 70, rng, 4);
    }

    @Test
    void ordemEscolhidaEhRespeitadaEEmCiclo() {
        Rng gera = new Rng(5);
        for (int k = 0; k < 400; k++) {
            TimePartida a = time("A", gera), b = time("B", gera);
            List<Integer> escolha = new ArrayList<>();
            for (int i = 10; i >= 0; i--) escolha.add(a.titulares.get(i).id); // de trás para frente
            Penaltis.Disputa d = Penaltis.disputar(a, b, new Rng(100 + k), false, escolha, null);
            List<Integer> autoB = Penaltis.ordemAutomatica(b).stream().map(e -> e.id).toList();
            int[] vez = new int[2];
            for (Penaltis.Cobranca c : d.cobrancas()) {
                List<Integer> ordem = c.lado() == 0 ? escolha : autoB;
                assertEquals(ordem.get(vez[c.lado()] % 11), c.jogadorId(), "ordem e ciclo (ninguém repete antes de todos)");
                vez[c.lado()]++;
            }
        }
    }

    @Test
    void regraReal() {
        Rng gera = new Rng(9);
        boolean viuCedo = false, viuAlternadas = false;
        for (int k = 0; k < 2000; k++) {
            Penaltis.Disputa d = Penaltis.disputar(time("A", gera), time("B", gera), new Rng(k), false);
            List<Penaltis.Cobranca> cs = d.cobrancas();
            assertNotEquals(d.golsMandante(), d.golsVisitante());
            int[] g = new int[2], b = new int[2];
            for (int i = 0; i < cs.size(); i++) {
                Penaltis.Cobranca c = cs.get(i);
                assertEquals(i % 2, c.lado(), "alternando, o mandante primeiro");
                b[c.lado()]++;
                if (c.gol()) g[c.lado()]++;
                boolean decidido = i < 10
                        ? g[0] > g[1] + (5 - b[1]) || g[1] > g[0] + (5 - b[0])
                        : b[0] == b[1] && g[0] != g[1];
                assertEquals(i == cs.size() - 1, decidido, "acaba exatamente quando decide (cobrança " + (i + 1) + ")");
            }
            if (cs.size() < 10) viuCedo = true;
            if (cs.size() > 10) viuAlternadas = true;
        }
        assertTrue(viuCedo && viuAlternadas);
    }

    @Test
    void soQuemTerminouEmCampoEIgualaNumeros() {
        Rng gera = new Rng(3);
        for (int k = 0; k < 300; k++) {
            TimePartida a = time("A", gera), b = time("B", gera);
            // A terminou com 9 (dois expulsos): B só usa os 9 primeiros da ordem dele
            a.emCampoFim = new ArrayList<>(a.titulares.subList(0, 9));
            Penaltis.Disputa d = Penaltis.disputar(a, b, new Rng(k), false);
            List<Integer> idsA = a.emCampoFim.stream().map(e -> e.id).toList();
            List<Integer> noveB = Penaltis.ordemAutomatica(b).subList(0, 9).stream().map(e -> e.id).toList();
            for (Penaltis.Cobranca c : d.cobrancas()) {
                if (c.lado() == 0) assertTrue(idsA.contains(c.jogadorId()));
                else assertTrue(noveB.contains(c.jogadorId()), "o time com mais em campo reduz ao número do outro");
            }
        }
    }

    @Test
    void ordemAutomaticaPelaForcaECobradorPrimeiro() {
        TimePartida a = time("A", new Rng(1));
        List<Escalado> o = Penaltis.ordemAutomatica(a);
        for (int i = 1; i < o.size(); i++) assertTrue(o.get(i - 1).forca >= o.get(i).forca, "do mais forte ao mais fraco");
        a.cobradorPenalti = a.titulares.get(5).id;
        assertEquals(a.titulares.get(5).id, Penaltis.ordemAutomatica(a).get(0).id);
        // ids repetidos ou de fora são ignorados; quem faltou vem depois, na automática
        List<Escalado> m = Penaltis.ordem(a, List.of(a.titulares.get(3).id, 999999, a.titulares.get(3).id));
        assertEquals(11, m.size());
        assertEquals(a.titulares.get(3).id, m.get(0).id);
    }

    @Test
    void mesmaSementeMesmoResultado() {
        for (int k = 0; k < 50; k++) {
            Penaltis.Disputa x = Penaltis.disputar(time("A", new Rng(k)), time("B", new Rng(k + 1)), new Rng(77 + k), true);
            Penaltis.Disputa y = Penaltis.disputar(time("A", new Rng(k)), time("B", new Rng(k + 1)), new Rng(77 + k), true, null, null);
            assertEquals(x.cobrancas().stream().map(c -> c.jogadorId() + ":" + c.gol()).toList(),
                    y.cobrancas().stream().map(c -> c.jogadorId() + ":" + c.gol()).toList());
        }
    }

    @Test
    void motorGuardaQuemTerminouEmCampo() {
        TimePartida a = time("A", new Rng(1)), b = time("B", new Rng(2));
        MotorPartida.simular(a, b, new OpcoesPartida(), new Rng(7));
        assertNotNull(a.emCampoFim);
        assertTrue(a.emCampoFim.size() <= 11 && a.emCampoFim.size() >= 7);
    }
}
