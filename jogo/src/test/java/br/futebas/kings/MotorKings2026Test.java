package br.futebas.kings;

import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.ResultadoPartida;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Regras 2026/27 da Kings: rampa, gol duplo, dado, cartas secretas, presidente, match ball e shootouts. */
class MotorKings2026Test {

    static LigaKings.TimeKings time(String id, double forca, String presidente) {
        LigaKings.TimeKings t = new LigaKings.TimeKings();
        t.id = id;
        t.nome = "Time " + id;
        t.presidente = presidente;
        Posicao[] p = {Posicao.GOL, Posicao.ZAG, Posicao.ZAG, Posicao.MC, Posicao.MC, Posicao.PE, Posicao.CA, Posicao.CA, Posicao.LD, Posicao.MEI, Posicao.VOL, Posicao.GOL};
        for (int i = 0; i < p.length; i++) {
            LigaKings.AtletaKings a = new LigaKings.AtletaKings();
            a.jogadorId = id.hashCode() % 1000 * 100 + i;
            a.nome = id + " " + i;
            a.forca = forca;
            a.posicao = p[i];
            t.elenco.add(a);
        }
        return t;
    }

    @Test
    void regrasDe2026() {
        Rng rng = new Rng(3);
        int n = 3000, empates = 0, shootouts = 0, dados = 0, cartasUsadas = 0, presidentes = 0, duplos = 0;
        double gols = 0;
        for (int i = 0; i < n; i++) {
            ResultadoPartida r = MotorKings.simular(time("a", 60, "Pres A"), time("b", 60, "Pres B"), new LigaKings.RegrasKings(), rng, i < 20);
            gols += r.golsMandante + r.golsVisitante;
            if (r.golsMandante == r.golsVisitante) {
                empates++;
                assertNotNull(r.penaltisMandante, "empate no tempo sempre vai aos shootouts");
                assertNotEquals(r.penaltisMandante, r.penaltisVisitante);
                shootouts++;
            }
            int[] placar = new int[2];
            int sorteadas = 0;
            for (EventoJogo e : r.eventos) {
                if (e.gol()) placar[e.lado()] += e.valor();
                if (e.tipo().equals("carta_sorteada")) sorteadas++;
                if (e.tipo().equals("dado")) dados++;
                if (e.tipo().equals("carta_usada")) {
                    cartasUsadas++;
                    assertTrue((e.minuto() >= 5 && e.minuto() < 17) || (e.minuto() >= 23 && e.minuto() < 36), "janela da carta: " + e.minuto());
                }
                if (e.tipo().startsWith("penalti_presidente")) presidentes++;
                if (e.tipo().endsWith("_x2")) duplos++;
            }
            assertEquals(2, sorteadas);
            assertEquals(r.golsMandante, placar[0], "eventos somam o placar");
            assertEquals(r.golsVisitante, placar[1]);
        }
        double media = gols / n;
        assertTrue(media > 5 && media < 12, "gols por jogo da Kings: " + media);
        assertEquals(n, dados, "o dado sai em todo jogo");
        assertTrue(cartasUsadas > n, "a IA usa as cartas");
        assertTrue(presidentes > n / 2, "presidentes cobram");
        assertTrue(duplos > 0);
        assertTrue(shootouts == empates && empates > 0);
    }

    @Test
    void tecnicoEscolheQuandoUsarACartaEOPresidente() {
        final int[] usouEm = {-1, -1};
        AoVivoKings ao = new AoVivoKings() {
            @Override
            public void minuto(ControleKings c) {
                if (c.minuto() == 30 && c.podeUsarCarta(0)) {
                    assertNotNull(c.carta(0));
                    assertTrue(c.usarCarta(0, MotorKings.Carta.PENALTI, null));
                    usouEm[0] = c.minuto();
                }
                if (c.minuto() == 10 && c.podePenaltiPresidente(0)) {
                    assertTrue(c.cobrarPenaltiPresidente(0));
                    usouEm[1] = c.minuto();
                }
                assertFalse(c.minuto() == 18 && c.podePenaltiPresidente(0), "sem presidente no gol duplo");
            }
        };
        ResultadoPartida r = MotorKings.simular(time("h", 62, "Eu"), time("b", 60, "Pres B"), new LigaKings.RegrasKings(), new Rng(9), true, ao, 0);
        assertEquals(30, usouEm[0], "a carta só sai quando o técnico quer");
        assertEquals(10, usouEm[1]);
        assertTrue(r.eventos.stream().anyMatch(e -> e.tipo().equals("carta_usada") && e.lado() == 0 && e.minuto() == 30));
        assertTrue(r.eventos.stream().anyMatch(e -> e.tipo().startsWith("penalti_presidente") && e.lado() == 0 && e.minuto() == 10));
        assertFalse(r.lances.isEmpty());
    }
}
