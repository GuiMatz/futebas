package br.futebas.competicao;

import br.futebas.Fixture;
import br.futebas.dados.Formato;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Simulador;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regressões dos bugs achados nas simulações longas de balanço (docs/BALANCO-2026-11.md). */
class BalancoRegressaoTest {

    /** Kings: empate no tempo vai aos shootouts; a tabela não pode ter empates (antes: "V6 E1 D1, 7 pts"). */
    @Test
    void shootoutNaoContaComoEmpateNaTabela() {
        for (int[] regra : new int[][]{{1, 1, 0}, {3, 2, 1}}) {
            Torneio t = new Torneio();
            t.id = "kings-teste-split1";
            t.ambito = "kings";
            t.ano = 2026;
            t.semente = 7;
            t.penaltisNoEmpate = true;
            t.pontosVitoriaPenaltis = regra[1];
            t.pontosDerrotaPenaltis = regra[2];
            t.formato = new Gson().fromJson("{\"pontos_vitoria\":" + regra[0] + ",\"fases\":[{\"tipo\":\"pontos_corridos\",\"turnos\":1}]}", Formato.class);
            for (int i = 0; i < 8; i++) t.participantes.add("k" + i);
            t.semanaInicio = 2;
            t.semanaFim = 20;
            t.planejar();
            FormatosTest.Falso ex = new FormatosTest.Falso();
            // metade dos jogos empata no tempo (vai aos shootouts)
            ex.roteiro = (p, prorrog) -> p.id % 2 == 0 ? new int[]{3, 3} : new int[]{4, 2};
            t.concluir(ex);
            int jogos = 0, so = 0;
            for (LinhaTabela l : t.tabela(0, 0)) {
                assertEquals(0, l.empates, "Kings não tem empate: " + l.clubeId);
                assertEquals(l.jogos, l.vitorias + l.derrotas);
                assertEquals(regra[0] * (l.vitorias - l.vitoriasPenaltis) + regra[1] * l.vitoriasPenaltis + regra[2] * l.derrotasPenaltis, l.pontos);
                jogos += l.jogos;
                so += l.vitoriasPenaltis;
            }
            assertEquals(56, jogos);
            assertEquals(14, so, "metade dos 28 jogos foi aos shootouts");
        }
    }

    /** Copa com grupos gigantes no banco (Copa da Rússia: 104 clubes em 4 grupos de 26) vira mata-mata. */
    @Test
    void copaComGruposGigantesViraMataMata() {
        Torneio t = new Torneio();
        t.id = "rus-copa";
        t.ambito = "copa_nacional";
        t.ano = 2026;
        t.semente = 3;
        t.formato = new Gson().fromJson("{\"fases\":[{\"tipo\":\"grupos\",\"grupos\":4,\"turnos\":2,\"classificam\":2},"
                + "{\"tipo\":\"mata_mata\",\"ida_volta\":false,\"desempate\":\"penaltis\"}]}", Formato.class);
        for (int i = 0; i < 104; i++) t.participantes.add("c" + i);
        t.semanaInicio = 30;
        t.semanaFim = 50;
        t.planejar();
        assertEquals("mata_mata", t.fases.get(0).tipo);
        t.concluir(new FormatosTest.Falso());
        assertNotNull(t.campeao);
        assertTrue(t.partidas.size() < 120, "jogos da copa: " + t.partidas.size());
    }

    /**
     * Desafio Sul-Americana × Europa League: sempre os dois campeões. Antes, do 2º ano em diante, a
     * montagem da temporada criava a competição com clubes pelo ranking (Juventus e Liverpool
     * "campeões" sem terem vencido a Europa League) e o jogo dos campeões não acontecia.
     */
    @Test
    void desafioDeAgostoSempreComOsDoisCampeoes() {
        Simulador s = Simulador.novo(Fixture.banco(), 777, null, null);
        java.util.Set<Torneio> vistos = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        int conferidos = 0;
        while (s.m.ano <= 2029) {
            s.avancarSemana();
            java.util.List<Torneio> todos = new java.util.ArrayList<>(s.m.torneios);
            todos.addAll(s.m.torneiosAnteriores);
            int porAno = 0;
            for (Torneio t : todos) {
                if (!CompeticoesFicticias.DESAFIO.equals(t.id)) continue;
                if (t.ano == s.m.ano) porAno++;
                if (!vistos.add(t)) continue;
                Torneio sul = s.m.ultimoEncerrado(CompeticoesFicticias.SUL_AMERICANA), eur = s.m.ultimoEncerrado(CompeticoesFicticias.EUROPA);
                assertNotNull(sul);
                assertNotNull(eur);
                assertEquals(java.util.Set.of(sul.campeao, eur.campeao), new java.util.HashSet<>(t.participantes), "Desafio " + t.ano);
                conferidos++;
            }
            assertTrue(porAno <= 1, "um Desafio por ano");
        }
        assertTrue(conferidos >= 2, "Desafios conferidos: " + conferidos);
    }

    /**
     * Formato sem {@code promovidos} (271 dos 514 do banco): sobem tantos quantos caem da divisão
     * de cima (antes a liga ficava fechada: os rebaixados voltavam para completar a 1ª divisão).
     */
    @Test
    void semPromovidosNoFormatoSobemOsRebaixadosDeCima() {
        Simulador s = Simulador.novo(Fixture.banco(), 4242, null, null);
        Torneio b = null, a = null;
        while (s.m.ano == 2026) {
            s.avancarSemana();
            Torneio tb = s.m.torneio("bra-2"), ta = s.m.torneio("bra-1");
            if (tb != null && tb.encerrado && tb.ano == 2026 && b == null) {
                Formato f = new Gson().fromJson(new Gson().toJson(tb.formato), Formato.class);
                f.promovidos = null; // como o banco real
                tb.formato = f;
                b = tb;
            }
            if (ta != null && ta.encerrado && ta.ano == 2026) a = ta;
        }
        assertNotNull(b, "Série B de 2026 terminou");
        assertNotNull(a);
        int reb = a.formato.rebaixados;
        List<String> cb = b.classificacao, ca = a.classificacao;
        for (int i = 0; i < reb; i++) {
            Clube sobe = s.m.clube(cb.get(i));
            assertEquals(1, sobe.divisao, "sobe da B: " + sobe.nome + " (" + (i + 1) + "º)");
            Clube cai = s.m.clube(ca.get(ca.size() - 1 - i));
            assertEquals(2, cai.divisao, "cai da A: " + cai.nome);
        }
    }
}
