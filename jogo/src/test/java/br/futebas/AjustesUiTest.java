package br.futebas;

import br.futebas.historia.Historia;
import br.futebas.kings.LigaKings;
import br.futebas.mundo.Artigos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pedidos da UI: jogo novo só com a Kings, artigos, ME/MD no 4-4-2, carreira do técnico e caixa inicial. */
class AjustesUiTest {

    @Test
    void artigos() {
        assertEquals("do", Artigos.de("Campeonato Brasileiro Série A"));
        assertEquals("da", Artigos.de("Copa do Brasil"));
        assertEquals("da", Artigos.de("Libertadores"));
        assertEquals("da", Artigos.de("Série A"));
        assertEquals("no", Artigos.em("Brasileirão"));
        assertEquals("na", Artigos.em("Premier League"));
        assertEquals("no", Artigos.em("Campeonato Paulista"));
    }

    @Test
    void jogoNovoSoComKingsERotulos() {
        Jogo semClube = Jogo.novoJogo(Fixture.banco(), 3, null, null);
        LigaKings liga = semClube.ligasKings().stream().filter(l -> l.id.equals("kings-brasil")).findFirst().orElseThrow();
        assertTrue(semClube.assumirTimeKings(liga.times.get(0).id), "jogo novo só com a Kings");
        assertNull(semClube.meuClube());
        for (int i = 0; i < 8; i++) semClube.avancarSemana();
        assertEquals(liga.times.get(0).id, semClube.mundo().tecnico.kingsTimeId);
        Jogo k = Jogo.novoJogoKings(Fixture.banco(), 4, "bra-1-05", liga.times.get(1).id, "T");
        assertNull(k.meuClube(), "carreira exclusiva: o clube passado é ignorado");
        assertEquals(liga.times.get(1).id, k.mundo().tecnico.kingsTimeId);
        List<String> r = k.rotulosFormacao("4-4-2");
        assertTrue(r.contains("ME") && r.contains("MD") && !r.contains("PE"));
        assertTrue(k.rotulosFormacao("4-3-3").contains("PE"));
        assertEquals(12, k.reservasPermitidas("bra-1"));
        assertEquals(9, k.reservasPermitidas("eng-1"));
        assertEquals(5, k.substituicoesPermitidas("bra-1"));
    }

    @Test
    void carreiraDoTecnicoECaixa() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 5, "bra-1-01", "T");
        var c = j.meuClube();
        double e = br.futebas.financas.Financas.receitaReferencia(c, 2026);
        assertTrue(c.dinheiro <= 0.15 * e, "caixa inicial é uma fração pequena da receita: " + c.dinheiro / e);
        while (j.ano() == 2026) {
            j.meuClube().confiancaDiretoria = 90;
            j.avancarSemana();
        }
        Historia.TemporadaTecnico tt = j.historia().carreiraTecnico.get(0);
        assertTrue(tt.jogos > 0 && tt.jogos == tt.vitorias + tt.empates + tt.derrotas, "J/V/E/D gravados: " + tt.jogos);
    }
}
