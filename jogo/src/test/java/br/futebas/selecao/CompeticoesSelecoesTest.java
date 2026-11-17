package br.futebas.selecao;

import br.futebas.Fixture;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Simulador;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Eliminatórias, Liga das Nações (com acesso), Finalíssima e Copa pelas eliminatórias. */
class CompeticoesSelecoesTest {

    static Torneio achar(Simulador s, String id, int ano) {
        for (List<Torneio> l : List.of(s.m.torneios, s.m.torneiosAnteriores)) for (Torneio t : l) if (t.id.equals(id) && t.ano == ano) return t;
        return null;
    }

    @Test
    void cicloDeSelecoes() {
        Simulador s = Simulador.novo(Fixture.banco(), 17, null, null);
        Torneio nlA = achar(s, Selecoes.NATIONS_UEFA + "-a", 2026);
        assertNotNull(nlA, "Liga das Nações A em 2026");
        assertTrue(nlA.datasFifa);
        while (s.m.ano < 2028) s.avancarTemporada();
        assertNotNull(achar(s, Selecoes.NATIONS_UEFA, 2027), "finais da Liga das Nações em 2027");
        while (s.m.ano < 2029) s.avancarTemporada();
        assertFalse(s.m.divisaoNations.isEmpty());
        Torneio elim = achar(s, Selecoes.idEliminatorias("CONMEBOL"), 2029);
        assertNotNull(elim, "eliminatórias no ano anterior à Copa");
        while (s.m.ano < 2030 || s.m.semana < 3) s.avancarSemana();
        Torneio copa = achar(s, Selecoes.COPA, 2030);
        assertNotNull(copa);
        Torneio e = achar(s, Selecoes.idEliminatorias("CONMEBOL"), 2029);
        assertTrue(e.encerrado);
        assertTrue(copa.participantes.contains(e.classificacao.get(0)), "o 1º das eliminatórias vai à Copa");
    }
}
