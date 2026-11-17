package br.futebas.kings;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Kings 2026: vitória = 1 ponto (também no match ball e nos shootouts). A regra do jogador 13 saiu (dono, 12/11/2026). */
class Kings2026PontosE13Test {

    static final String REGRAS = "{\"pontuacao\": \"2025/26 e 2026/27: vitória = 1 ponto, derrota = 0, independentemente de ser no tempo, "
            + "no match ball ou nos shootouts\", \"formato_split_2026\": \"12 times, 11 rodadas (turno único); play-in (7º x 10º, 8º x 9º) e playoffs com 7 times\","
            + "\"cartas_especiais_2025_26\": {\"carta_jogador_13\": \"até 3 rodadas na fase regular (ligas de 10/12 times) ou 2 (ligas de 8) e 1 vez nos playoffs\"}}";

    @Test
    void leRegrasDe2026() {
        LigaKings l = new LigaKings();
        Kings.lerRegras(l, REGRAS);
        assertTrue(l.regras.pontuacao2026);
        assertEquals(1, l.regras.pontos_vitoria);
        assertEquals(1, l.regras.pontos_vitoria_penaltis);
        assertEquals(0, l.regras.pontos_derrota_penaltis);
        assertEquals(8, l.regras.classificados_playoff, "7 + o vencedor do play-in");
        LigaKings mena = new LigaKings();
        Kings.lerRegras(mena, "{\"formato_split_2026_27\": \"10 times, 9 rodadas, 1 ponto por vitória; playoffs com 7\", \"carta_13\": \"até 2 rodadas na fase regular e 1 vez nos playoffs\"}");
        assertTrue(mena.regras.pontuacao2026);
        assertEquals(7, mena.regras.classificados_playoff);
    }

    @Test
    void pontosPorVitoriaSemJogador13() {
        Jogo jogo = Jogo.novoJogo(Fixture.banco(), 41, "bra-1-02", "Técnico Teste");
        LigaKings liga = jogo.ligasKings().stream().filter(l -> l.id.equals("kings-brasil")).findFirst().orElseThrow();
        Kings.usarPontuacao2026(liga.regras);
        assertTrue(jogo.assumirTimeKings(liga.times.get(0).id));
        assertNull(jogo.meuClube(), "carreira exclusiva: só o time da Kings");
        jogo.avancarSemana();
        assertTrue(liga.ativa);
        while (liga.torneio.proximaData < liga.torneio.fases.get(0).numDatas && jogo.ano() == 2026) {
            jogo.avancarSemana();
            for (LigaKings.TimeKings t : liga.times) {
                assertTrue(t.elenco.size() <= Kings.MAX_ELENCO, "no máximo 12: " + t.nome);
                assertTrue(t.elenco.stream().noneMatch(a -> "13".equals(a.tipo)), "sem jogador 13");
            }
        }
        for (LinhaTabela x : liga.torneio.tabela(0, 0)) {
            int vit = 0;
            for (Partida p : liga.torneio.partidas) {
                if (p.fase != 0 || !p.jogada || !p.envolve(x.clubeId)) continue;
                boolean mand = p.mandante.equals(x.clubeId);
                int pro = mand ? p.golsMandante : p.golsVisitante, contra = mand ? p.golsVisitante : p.golsMandante;
                if (pro > contra) vit++;
                else if (pro == contra && p.penaltisMandante != null && (mand ? p.penaltisMandante > p.penaltisVisitante : p.penaltisVisitante > p.penaltisMandante)) vit++;
            }
            assertEquals(vit, x.pontos, "1 ponto por vitória: " + x.clubeId);
        }
    }
}
