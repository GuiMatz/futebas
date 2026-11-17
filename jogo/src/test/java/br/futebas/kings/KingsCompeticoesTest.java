package br.futebas.kings;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Torneio;
import br.futebas.ia.PropostaTecnico;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Kings: Split 1, Kings Cup e Split 2 por liga, Copas do Mundo (Clubs e Nations) e emprego só por convite. */
class KingsCompeticoesTest {

    @Test
    void splitsCopaMundiaisEConvites() {
        Jogo jogo = Jogo.novoJogo(Fixture.banco(), 23, "bra-1-03", "Técnico Teste");
        LigaKings liga = jogo.ligasKings().stream().filter(l -> l.id.equals("kings-brasil")).findFirst().orElseThrow();
        jogo.avancarSemana();
        assertNotNull(jogo.competicaoKings("kings-brasil-split1"));
        assertNotNull(jogo.competicaoKings("kings-brasil-cup"));
        assertNotNull(jogo.competicaoKings("kings-brasil-split2"));
        assertEquals("kings", jogo.competicaoKings("kings-brasil-cup").ambito);
        assertFalse(jogo.assumirTimeKings(liga.times.get(0).id), "carreira: não assume time da Kings sem convite");
        while (jogo.ano() == 2026) {
            jogo.meuClube().confiancaDiretoria = 90;
            jogo.avancarSemana();
        }
        for (String s : List.of("split1", "cup", "split2")) {
            Torneio t = null;
            for (var x : jogo.historia().titulos) if (x.competicaoId.equals("kings-brasil-" + s) && x.ano == 2026) t = jogo.competicaoKings("kings-brasil-" + s);
            assertNotNull(t, "campeão de " + s);
        }
        assertTrue(jogo.historia().titulos.stream().anyMatch(x -> x.competicaoId.equals("kings-mundial-clubes") && x.ano == 2026), "Kings World Cup Clubs");
        Torneio mc = jogo.simulador().kings.mundial("kings-mundial-clubes", 2026);
        assertTrue(mc.participantes.stream().anyMatch(id -> id.startsWith("kb-")) && mc.participantes.stream().anyMatch(id -> id.startsWith("ke-")), "times de duas ligas");
        // convite de um time da Kings: aceitar pelo fluxo normal de emprego (carreira exclusiva: deixa o clube)
        PropostaTecnico p = jogo.simulador().kings.proporKings(liga, liga.times.get(2));
        assertTrue(jogo.propostasDeEmprego().contains(p));
        String clube = jogo.meuClube().id;
        assertTrue(jogo.aceitarEmprego(p.id));
        assertEquals(liga.times.get(2).id, jogo.mundo().tecnico.kingsTimeId);
        assertNull(jogo.meuClube(), "deixou o clube");
        assertFalse(jogo.clube(clube).humano);
        assertTrue(liga.times.get(2).humano);
    }

    @Test
    void candidaturaSoNoJogoNovoOuVagaAberta() {
        Jogo jogo = Jogo.novoJogo(Fixture.banco(), 24, "bra-1-03", "Técnico Teste");
        LigaKings liga = jogo.ligasKings().stream().filter(l -> l.id.equals("kings-brasil")).findFirst().orElseThrow();
        LigaKings.TimeKings a = liga.times.get(0), b = liga.times.get(1);
        assertTrue(jogo.vagasKings().contains(a), "jogo novo: todos os times aceitam candidatura");
        jogo.avancarSemana();
        assertFalse(jogo.vagasKings().contains(a), "carreira: time com técnico não tem vaga");
        assertNull(jogo.candidatarKings(a.id), "carreira: candidatura sem vaga é proibida");
        b.vagaAte = jogo.mundo().semanaAbsoluta() + 6;
        assertTrue(jogo.vagasKings().contains(b));
        jogo.mundo().tecnico.reputacao = 100;
        PropostaTecnico p = null;
        for (int i = 0; i < 20 && p == null; i++) { b.vagaAte = jogo.mundo().semanaAbsoluta() + 6; p = jogo.candidatarKings(b.id); }
        assertNotNull(p, "vaga aberta: o presidente pode aceitar");
        assertEquals(b.id, p.kingsTimeId);
    }
}
