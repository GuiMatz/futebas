package br.futebas.mundo;

import br.futebas.Balanceamento;
import br.futebas.Fixture;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.historia.Historia;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Contrato v2, calendários por país, Champions de 36, Copa de 48 e mercado internacional. */
class MundoV2Test {

    @Test
    void leitorV2() {
        BancoMundo db = Fixture.banco();
        assertEquals(2, db.versaoEsquema);
        assertTrue(db.paises.get("ENG").europeu());
        assertFalse(db.paises.get("BRA").europeu());
        assertEquals(20, db.participantesIniciais("bra-1").size());
        assertEquals(36, db.participantesIniciais("uefa-champions").size());
        assertEquals(20, db.tabelaInicial("esp-1").size());
        assertEquals(48, db.selecoes.values().stream().filter(Db.Selecao::classificadaCopa2026).count());
        assertFalse(db.nomes.isEmpty());
        assertTrue(db.jogadores.values().stream().anyMatch(j -> j.atributos() != null));
        assertTrue(db.jogadores.values().stream().anyMatch(j -> "gerado".equals(j.origem())));
        Simulador s = Simulador.novo(db, 1, null, null);
        Jogador emp = null;
        for (Jogador j : s.m.jogadores) if (j.contrato != null && j.contrato.tipo == TipoContrato.EMPRESTIMO) emp = j;
        assertNotNull(emp, "empréstimo do banco");
        assertEquals("bra-2-01", emp.clubeId);
        assertEquals("bra-1-01", emp.contrato.clubeOrigem);
    }

    /**
     * Calendário único (decisão de design, "igual Brasfoot"): todos os países jogam jan–dez e tudo
     * começa do zero em 2026 (nada de tabela real nem de jogos já jogados).
     */
    @Test
    void calendarioUnicoJanDez() {
        Simulador s = Simulador.novo(Fixture.banco(), 2, null, null);
        assertTrue(s.m.calendarioUnico);
        for (var p : s.m.paises.values()) assertEquals("ano", p.calendario, p.id);
        Torneio esp = s.m.torneio("esp-1"), eng = s.m.torneio("eng-1"), bra = s.m.torneio("bra-1");
        assertNull(esp.tabelaBase, "sem tabela real de 1/jan/2026");
        for (Torneio t : java.util.List.of(esp, eng, bra)) {
            assertEquals("2026", t.temporada);
            assertEquals(2026, t.ano);
            assertTrue(t.partidas.stream().noneMatch(p -> p.jogada), "nada jogado em " + t.id);
            for (Partida p : t.partidas) {
                assertEquals(2026, p.ano, t.id + " cabe em 2026");
                for (int f : Balanceamento.JANELAS_FIFA) assertNotEquals(f, p.semana, "liga parada na data FIFA");
                assertFalse(p.semana >= 24 && p.semana <= 29, t.id + " para na Copa");
            }
        }
        Torneio ucl = s.m.torneio("uefa-champions");
        assertEquals("2026", ucl.temporada);
        for (Partida p : ucl.partidas) assertEquals(2026, p.ano, "Champions cabe em 2026");
        // fim do ano: tudo termina; a Premier de 2027 tem os 3 primeiros da 2ª divisão de 2026
        s.avancarTemporada();
        assertTrue(s.m.historia.titulos.stream().anyMatch(t -> t.competicaoId.equals("eng-1") && "2026".equals(t.temporada)));
        assertTrue(s.m.historia.titulos.stream().anyMatch(t -> t.competicaoId.equals("uefa-champions") && t.ano == 2026));
        Torneio eng2 = s.m.torneio("eng-1");
        assertEquals("2027", eng2.temporada);
        Torneio ant2 = s.m.torneioAnterior("eng-2");
        Set<String> sobem = new HashSet<>(ant2.classificacao.subList(0, 3));
        assertTrue(eng2.participantes.containsAll(sobem));
        assertEquals(20, eng2.participantes.size());
    }

    @Test
    void championsFaseDeLigaECopaDoMundo() {
        Simulador s = Simulador.novo(Fixture.banco(), 3, null, null);
        Torneio ucl = s.m.torneio("uefa-champions");
        FaseExec liga = ucl.fases.get(0);
        assertEquals("liga_suica", liga.tipo);
        assertEquals(36, liga.clubes.size());
        Map<String, Set<String>> adv = new HashMap<>();
        Map<String, Integer> jogos = new HashMap<>();
        for (Partida p : liga.partidas) {
            adv.computeIfAbsent(p.mandante, k -> new HashSet<>()).add(p.visitante);
            adv.computeIfAbsent(p.visitante, k -> new HashSet<>()).add(p.mandante);
            jogos.merge(p.mandante, 1, Integer::sum);
            jogos.merge(p.visitante, 1, Integer::sum);
        }
        for (String c : liga.clubes) {
            assertEquals(8, jogos.get(c), "8 jogos na fase de liga");
            assertEquals(8, adv.get(c).size(), "8 adversários diferentes");
        }
        // Copa de 2026: 48 seleções, grupos reais, 8 melhores terceiros, 16 avos
        Torneio copa = s.m.torneio("fifa-copa-do-mundo");
        assertNotNull(copa);
        assertEquals(48, copa.participantes.size());
        FaseExec grupos = copa.fases.get(0);
        assertEquals(12, grupos.numGrupos);
        assertEquals(32, grupos.saida);
        assertTrue(grupos.grupos.get(2).containsAll(List.of("sel-BRA", "sel-MAR", "sel-HAI", "sel-SCO")), "grupo C real");
        while (s.m.semana < 26) s.avancarSemana();
        Selecao bra = s.m.selecao("BRA");
        assertTrue(bra.convocados.size() >= 20);
        assertTrue(s.m.jogador(bra.convocados.get(0)).convocado, "convocado sai do clube durante a Copa");
        while (s.m.semana < 32) s.avancarSemana();
        assertTrue(copa.encerrado);
        assertEquals(32, copa.fases.get(1).clubes.size());
        assertEquals(7, copa.partidas.get(copa.partidas.size() - 1).data().getMonthValue(), "final em julho");
        assertFalse(s.m.jogador(bra.convocados.get(0)).convocado, "volta ao clube");
        assertTrue(s.m.historia.titulos.stream().anyMatch(t -> t.competicaoId.equals("fifa-copa-do-mundo") && t.ano == 2026));
        s.avancarTemporada(); // calendário único: a Champions termina em novembro
        assertTrue(s.m.historia.titulos.stream().anyMatch(t -> t.competicaoId.equals("uefa-champions") && t.ano == 2026));
    }

    @Test
    void copaDe2030ComVagasPorConfederacao() {
        Simulador s = Simulador.novo(Fixture.banco(), 4, null, null);
        List<Torneio> l = s.selecoes.torneiosDoAno(2030);
        Torneio copa = l.stream().filter(t -> t.id.equals("fifa-copa-do-mundo")).findFirst().orElseThrow();
        assertEquals(48, copa.participantes.size());
        long conmebol = copa.participantes.stream().filter(id -> "CONMEBOL".equals(s.m.selecao(id).confederacao)).count();
        assertTrue(conmebol >= 6);
        assertTrue(s.selecoes.torneiosDoAno(2028).stream().anyMatch(t -> t.id.equals("uefa-eurocopa")));
        assertTrue(s.selecoes.torneiosDoAno(2027).isEmpty());
    }

    @Test
    void mercadoInternacional() {
        Simulador s = Simulador.novo(Fixture.banco(), 5, null, null);
        s.avancarTemporada();
        for (int i = 0; i < 3; i++) s.avancarSemana(); // janela de janeiro repõe quem saiu na virada
        Historia.EstatAno e = s.m.historia.estat(2026);
        assertTrue(e.transferencias > 0);
        assertTrue(e.transferenciasInternacionais > 0, "clubes compram em outros países");
        Set<Integer> vistos = new HashSet<>();
        for (Clube c : s.m.clubes.values()) {
            for (int id : c.elenco) {
                assertTrue(vistos.add(id), "jogador em dois clubes");
                assertEquals(c.id, s.m.jogador(id).clubeId);
            }
            if (c.divisao > 0) assertTrue(c.elenco.size() >= 16, c.id + " elenco " + c.elenco.size());
        }
    }
}
