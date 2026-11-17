package br.futebas.ui.modelo;

import br.futebas.competicao.Confronto;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.dados.Formato;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Mata-mata ao vivo: agregado com o placar de agora e do que o time precisa. */
class MataMataAoVivoTest {

    /** Ida e volta: "a" (melhor campanha) x "b"; a ida (b em casa) terminou {@code idaB} x {@code idaA}. */
    private static Object[] montar(int idaB, int idaA, boolean golFora) {
        Torneio t = new Torneio();
        FaseExec f = new FaseExec();
        f.tipo = "mata_mata";
        f.nome = "Oitavas de final";
        f.def = new Formato.Fase();
        f.def.gol_fora = golFora;
        f.def.desempate = "penaltis";
        t.fases.add(f);
        Confronto c = new Confronto(0, "a", "b");
        f.confrontos.add(c);
        Partida ida = partida(0, "b", "a", 0);
        ida.jogada = true;
        ida.golsMandante = idaB;
        ida.golsVisitante = idaA;
        Partida volta = partida(1, "a", "b", 1);
        for (Partida p : List.of(ida, volta)) {
            t.partidas.add(p);
            f.partidas.add(p);
            c.partidas.add(p.id);
        }
        return new Object[] {t, volta};
    }

    private static Partida partida(int id, String m, String v, int perna) {
        Partida p = new Partida();
        p.id = id;
        p.fase = 0;
        p.rodadaMata = 0;
        p.confronto = 0;
        p.perna = perna;
        p.mandante = m;
        p.visitante = v;
        return p;
    }

    private static MataMataAoVivo.Situacao agora(Object[] tv, int gm, int gv) {
        MataMataAoVivo mm = MataMataAoVivo.de((Torneio) tv[0], (Partida) tv[1]);
        assertNotNull(mm);
        List<MataMataAoVivo.Situacao> ss = mm.agora(List.<Object[]>of(new Object[] {"a", "b", gm, gv}));
        assertEquals(1, ss.size());
        return ss.get(0);
    }

    @Test
    void precisaDeGolNaVolta() {
        // ida: b 1 x 0 a; volta 0 x 0 → a perde no agregado, precisa de 1 para os pênaltis e 2 para passar
        Object[] tv = montar(1, 0, false);
        MataMataAoVivo mm = MataMataAoVivo.de((Torneio) tv[0], (Partida) tv[1]);
        MataMataAoVivo.Situacao s = agora(tv, 0, 0);
        assertTrue(s.decisivo());
        assertEquals(0, s.golsA());
        assertEquals(1, s.golsB());
        assertArrayEquals(new int[] {0, 1}, s.ida());
        assertEquals("b", s.passa());
        assertEquals("Precisa de 1 gol para levar aos pênaltis, 2 para passar", mm.precisa(s, "a"));
        assertEquals("Classificado pelo agregado", mm.precisa(s, "b"));
        // 1 x 0 na volta: agregado empatado
        MataMataAoVivo.Situacao e = agora(tv, 1, 0);
        assertNull(e.passa());
        assertEquals("Agregado empatado: pênaltis", mm.precisa(e, "a"));
    }

    @Test
    void golForaDecide() {
        // ida: b 2 x 1 a (a marcou fora); volta 1 x 0 → 2 x 2, a passa pelo gol fora
        Object[] tv = montar(2, 1, true);
        MataMataAoVivo mm = MataMataAoVivo.de((Torneio) tv[0], (Partida) tv[1]);
        MataMataAoVivo.Situacao s = agora(tv, 1, 0);
        assertEquals("a", s.passa());
        // 0 x 0: b classificado; a precisa de 1 gol (o empate no agregado com mais gols fora)
        MataMataAoVivo.Situacao z = agora(tv, 0, 0);
        assertEquals("b", z.passa());
        assertEquals("Precisa de 1 gol", mm.precisa(z, "a"));
    }
}
