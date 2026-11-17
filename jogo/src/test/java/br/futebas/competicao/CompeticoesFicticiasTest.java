package br.futebas.competicao;

import br.futebas.Fixture;
import br.futebas.mundo.Simulador;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Copa Cóndor (fictícia), vaga do campeão na Sul-Americana e o Desafio de agosto. */
class CompeticoesFicticiasTest {

    static Torneio achar(Simulador s, String id, int ano) {
        for (List<Torneio> l : List.of(s.m.torneios, s.m.torneiosAnteriores)) for (Torneio t : l) if (t.id.equals(id) && t.ano == ano) return t;
        return null;
    }

    @Test
    void condorEDesafio() {
        Simulador s = Simulador.novo(Fixture.banco(), 29, null, null);
        assertEquals("assets/competicoes/copa-condor.png", Fixture.banco().competicoes.get(CompeticoesFicticias.CONDOR).logoPng());
        Torneio c = achar(s, CompeticoesFicticias.CONDOR, 2026);
        assertNotNull(c, "Copa Cóndor 2026");
        Torneio lib = achar(s, "conmebol-libertadores", 2026), sula = achar(s, "conmebol-sul-americana", 2026);
        for (String id : c.participantes) {
            assertFalse(lib.participantes.contains(id) || sula.participantes.contains(id), "fora da Libertadores e da Sul-Americana: " + id);
        }
        while (s.m.ano < 2027 || s.m.semana <= CompeticoesFicticias.SEMANA_DESAFIO + 1) s.avancarSemana();
        Torneio cv = achar(s, CompeticoesFicticias.CONDOR, 2026);
        assertTrue(cv.encerrado && cv.campeao != null);
        Torneio sula27 = achar(s, "conmebol-sul-americana", 2027), lib27 = achar(s, "conmebol-libertadores", 2027);
        assertTrue(sula27.participantes.contains(cv.campeao) || lib27.participantes.contains(cv.campeao), "campeão da Cóndor vai à Sul-Americana");
        assertNotNull(achar(s, CompeticoesFicticias.DESAFIO, 2027), "Desafio Sul-Americana × Europa League em agosto");
        conferirCondor(s, 2026);
        conferirCondor(s, 2027);
        for (String id : List.of(CompeticoesFicticias.TACA_ATLANTICA, CompeticoesFicticias.DESAFIO)) {
            Torneio x = achar(s, id, 2027);
            if (x == null) continue;
            assertEquals(2, x.participantes.size(), id);
            assertNotEquals(x.participantes.get(0), x.participantes.get(1), id);
        }
        while (s.m.ano < 2028) s.avancarSemana();
        conferirCondor(s, 2028);
    }

    /** A Copa Cóndor tem sempre 32 clubes distintos, fora da Libertadores e da Sul-Americana. */
    static void conferirCondor(Simulador s, int ano) {
        {
            Torneio x = achar(s, CompeticoesFicticias.CONDOR, ano);
            assertNotNull(x, "Copa Cóndor " + ano);
            assertEquals(32, x.participantes.size(), "Copa Cóndor " + ano + " com 32 clubes");
            assertEquals(32, new java.util.HashSet<>(x.participantes).size(), "sem clube repetido");
            Torneio l = achar(s, "conmebol-libertadores", ano), su = achar(s, "conmebol-sul-americana", ano);
            for (String id : x.participantes) assertFalse(l.participantes.contains(id) || su.participantes.contains(id), ano + ": " + id);
        }
    }
}
