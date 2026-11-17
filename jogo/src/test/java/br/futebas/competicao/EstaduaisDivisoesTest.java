package br.futebas.competicao;

import br.futebas.Fixture;
import br.futebas.mundo.Simulador;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Estadual com divisões de acesso: sobe e desce entre est-sp e est-sp-2 a cada ano. */
class EstaduaisDivisoesTest {

    static Torneio achar(Simulador s, String id, int ano) {
        for (List<Torneio> l : List.of(s.m.torneios, s.m.torneiosAnteriores)) for (Torneio t : l) if (t.id.equals(id) && t.ano == ano) return t;
        return null;
    }

    @Test
    void piramideDoEstadual() {
        Simulador s = Simulador.novo(Fixture.banco(), 12, null, null);
        Torneio a2 = achar(s, "est-sp-2", 2026);
        assertNotNull(a2, "A2 de 2026 existe");
        assertTrue(a2.participantes.size() >= 4, "A2 com clubes: " + a2.participantes.size());
        s.avancarTemporada();
        Torneio a1v = achar(s, "est-sp", 2026), a2v = achar(s, "est-sp-2", 2026);
        Torneio a1n = achar(s, "est-sp", 2027), a2n = achar(s, "est-sp-2", 2027);
        assertNotNull(a1n);
        assertNotNull(a2n);
        List<String> c1 = a1v.classificacao, c2 = a2v.classificacao;
        for (int i = 0; i < 2; i++) {
            assertTrue(a1n.participantes.contains(c2.get(i)), "promovido da A2 sobe: " + c2.get(i));
            assertTrue(a2n.participantes.contains(c1.get(c1.size() - 1 - i)), "rebaixado da A1 desce: " + c1.get(c1.size() - 1 - i));
        }
        for (String id : a1n.participantes) assertFalse(a2n.participantes.contains(id), "clube em duas divisões: " + id);
    }
}
