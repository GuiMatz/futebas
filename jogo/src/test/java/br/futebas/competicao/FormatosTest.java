package br.futebas.competicao;

import br.futebas.dados.Formato;
import br.futebas.mundo.Rng;
import br.futebas.partida.ResultadoPartida;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class FormatosTest {

    /** Executor de teste: placar aleatório (ou roteirizado) sem motor de partida. */
    static final class Falso implements Executor {
        final Rng rng = new Rng(5);
        BiFunction<Partida, Boolean, int[]> roteiro;
        int penaltis;

        @Override
        public ResultadoPartida jogar(Torneio t, Partida p, boolean prorrogacao) {
            ResultadoPartida r = new ResultadoPartida();
            int[] g = roteiro != null ? roteiro.apply(p, prorrogacao) : new int[]{rng.poisson(1.4), rng.poisson(1.1)};
            r.golsMandante = g[0];
            r.golsVisitante = g[1];
            return r;
        }

        @Override
        public int[] penaltis(Torneio t, Partida p) {
            penaltis++;
            return rng.chance(0.5) ? new int[]{5, 4} : new int[]{3, 4};
        }
    }

    static Torneio torneio(String json, int n) {
        Torneio t = new Torneio();
        t.id = "teste";
        t.nome = "Teste";
        t.ambito = "nacional";
        t.ano = 2000;
        t.semente = 99;
        t.formato = new Gson().fromJson(json, Formato.class);
        for (int i = 0; i < n; i++) t.participantes.add("c" + (i < 10 ? "0" : "") + i);
        t.semanaInicio = 10;
        t.semanaFim = 40;
        t.planejar();
        return t;
    }

    @Test
    void pontosCorridosTurnoEReturno() {
        Torneio t = torneio("{\"fases\":[{\"tipo\":\"pontos_corridos\",\"turnos\":2}]}", 20);
        assertEquals(38, t.totalDatas);
        t.concluir(new Falso());
        assertEquals(380, t.partidas.size());
        Map<String, Integer> jogos = new HashMap<>(), casa = new HashMap<>();
        Set<String> pares = new HashSet<>();
        for (Partida p : t.partidas) {
            assertTrue(p.jogada);
            jogos.merge(p.mandante, 1, Integer::sum);
            jogos.merge(p.visitante, 1, Integer::sum);
            casa.merge(p.mandante, 1, Integer::sum);
            assertTrue(pares.add(p.mandante + ">" + p.visitante), "confronto repetido com o mesmo mando");
        }
        for (int v : jogos.values()) assertEquals(38, v);
        for (int v : casa.values()) assertEquals(19, v);
        // ninguém joga duas vezes na mesma data
        Map<Integer, Set<String>> porData = new HashMap<>();
        for (Partida p : t.partidas) {
            Set<String> s = porData.computeIfAbsent(p.data, k -> new HashSet<>());
            assertTrue(s.add(p.mandante) && s.add(p.visitante));
        }
        assertTrue(t.encerrado);
        assertEquals(t.tabela(0, 0).get(0).clubeId, t.campeao);
        assertEquals(20, t.classificacao.size());
        assertTrue(t.semanaDaData(0) >= 10 && t.semanaDaData(t.totalDatas - 1) <= 40);
    }

    @Test
    void gruposEMataMataIdaEVolta() {
        Torneio t = torneio("{\"fases\":[{\"tipo\":\"grupos\",\"grupos\":4,\"turnos\":2,\"classificam\":2},"
                + "{\"tipo\":\"mata_mata\",\"ida_volta\":true,\"gol_fora\":true,\"desempate\":\"penaltis\"}]}", 16);
        t.concluir(new Falso());
        FaseExec g = t.fases.get(0), mm = t.fases.get(1);
        assertEquals(4, g.grupos.size());
        for (List<String> gr : g.grupos) assertEquals(4, gr.size());
        assertEquals(4 * 12, g.partidas.size());
        assertEquals(8, g.classificados.size());
        assertEquals(3, mm.rodadasMata);
        assertEquals(14, mm.partidas.size()); // 4+2+1 confrontos, ida e volta
        assertEquals(7, mm.confrontos.size());
        assertNotNull(t.campeao);
        assertTrue(mm.clubes.contains(t.campeao));
        assertNotEquals(t.campeao, t.vice);
        // o vice perdeu a final
        Confronto fin = mm.confrontos.get(6);
        assertEquals(t.vice, fin.perdedor);
    }

    @Test
    void mataMataComNumeroImparUsaByes() {
        Torneio t = torneio("{\"fases\":[{\"tipo\":\"mata_mata\",\"ida_volta\":false}]}", 20);
        Falso f = new Falso();
        t.concluir(f);
        FaseExec mm = t.fases.get(0);
        assertEquals(19, mm.confrontos.size()); // 20 entram, 1 sai campeão: 19 eliminações
        assertEquals(1, mm.vivos.size());
        assertEquals(mm.vivos.get(0), t.campeao);
        assertEquals(t.totalDatas, mm.numDatas);
    }

    @Test
    void golForaDecideAntesDosPenaltis() {
        Torneio t = torneio("{\"fases\":[{\"tipo\":\"mata_mata\",\"ida_volta\":true,\"gol_fora\":true}]}", 2);
        Falso f = new Falso();
        // ida: c01 (pior) em casa 2x1; volta: c00 em casa 1x0 -> agregado 2x2, c01 tem 1 gol fora... c00 marcou 1 fora
        f.roteiro = (p, pr) -> p.perna == 0 ? new int[]{2, 1} : new int[]{1, 0};
        t.concluir(f);
        // c00 fez 1 gol fora (na ida) e c01 fez 0 gols fora: passa c00
        assertEquals("c00", t.campeao);
        assertEquals(0, f.penaltis);

        Torneio t2 = torneio("{\"fases\":[{\"tipo\":\"mata_mata\",\"ida_volta\":true,\"gol_fora\":false,\"desempate\":\"penaltis\"}]}", 2);
        Falso f2 = new Falso();
        f2.roteiro = (p, pr) -> p.perna == 0 ? new int[]{2, 1} : new int[]{1, 0};
        t2.concluir(f2);
        assertEquals(1, f2.penaltis);
        Partida volta = t2.partidas.get(1);
        assertNotNull(volta.penaltisMandante);
    }

    @Test
    void melhorCampanhaEPrimeiroColocadoNoMataMata() {
        Torneio t = torneio("{\"fases\":[{\"tipo\":\"mata_mata\",\"ida_volta\":false,\"desempate\":\"melhor_campanha\"}]}", 2);
        Falso f = new Falso();
        f.roteiro = (p, pr) -> new int[]{0, 0};
        t.concluir(f);
        assertEquals("c00", t.campeao);
        assertEquals(0, f.penaltis);
    }

    @Test
    void quadrangularEFinalUnica() {
        Torneio t = torneio("{\"fases\":[{\"tipo\":\"quadrangular\",\"grupos\":2,\"classificam\":1},"
                + "{\"tipo\":\"mata_mata\",\"ida_volta\":true,\"final_jogo_unico\":true}]}", 8);
        t.concluir(new Falso());
        assertEquals(2, t.fases.get(0).grupos.size());
        assertEquals(2 * 12, t.fases.get(0).partidas.size()); // 4 times, turno e returno
        assertEquals(1, t.fases.get(1).partidas.size()); // final em jogo único
        assertNotNull(t.campeao);
    }

    @Test
    void especialCaiNoFormatoMaisProximoEAvisa() {
        Torneio t = torneio("{\"fases\":[{\"tipo\":\"especial\",\"observacoes\":\"hexagonal\"}]}", 10);
        assertFalse(t.avisos.isEmpty());
        assertTrue(t.avisos.get(0).contains("especial"));
        t.concluir(new Falso());
        assertNotNull(t.campeao);
    }

    @Test
    void variasFasesSemanaASemana() {
        Torneio t = torneio("{\"fases\":[{\"tipo\":\"pontos_corridos\",\"turnos\":1,\"classificam\":4},"
                + "{\"nome\":\"Semifinal\",\"tipo\":\"mata_mata\",\"ida_volta\":true},{\"nome\":\"Final\",\"tipo\":\"mata_mata\",\"ida_volta\":true}]}", 10);
        Falso f = new Falso();
        List<Integer> semanas = new ArrayList<>();
        for (int w = 1; w <= 52; w++) {
            int antes = t.proximaData;
            t.jogarAte(w, f);
            if (t.proximaData > antes) semanas.add(w);
        }
        assertTrue(t.encerrado);
        assertTrue(semanas.get(0) >= 10 && semanas.get(semanas.size() - 1) <= 40);
        assertEquals(9 + 2 + 2, t.totalDatas);
        assertEquals(4, t.fases.get(0).classificados.size());
        assertEquals(2, t.fases.get(1).classificados.size());
    }

    @Test
    void pontosPorVitoriaDoFormatoOuDaEpoca() {
        Torneio t = new Torneio();
        t.id = "x";
        t.formato = new Gson().fromJson("{\"fases\":[{\"tipo\":\"pontos_corridos\",\"turnos\":1}]}", Formato.class);
        t.regras.pontosVitoria = 2; // regra da época (antes de 1995)
        t.participantes.addAll(List.of("a", "b"));
        t.planejar();
        Falso f = new Falso();
        f.roteiro = (p, pr) -> new int[]{1, 0};
        t.concluir(f);
        assertEquals(2, t.tabela(0, 0).get(0).pontos);
    }
}
