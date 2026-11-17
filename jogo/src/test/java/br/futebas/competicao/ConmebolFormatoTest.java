package br.futebas.competicao;

import br.futebas.dados.BancoMundo;
import br.futebas.ferramentas.LongRun;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Rng;
import br.futebas.mundo.Simulador;
import br.futebas.partida.ResultadoPartida;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Libertadores e Sul-Americana no formato real de 2026 ({@link Conmebol}): estrutura das fases, os
 * eliminados da 3ª fase e os 3ºs dos grupos da Libertadores entrando na Sul-Americana, e uma temporada
 * inteira no banco real (pulada sem o banco).
 */
class ConmebolFormatoTest {

    static final String[] PAISES = Conmebol.PAISES;

    /** Executor de teste: placar aleatório; a outra competição e o país vêm de mapas. */
    static final class Exec implements Executor {
        final Rng rng = new Rng(11);
        final Map<String, Torneio> outros = new HashMap<>();
        final Map<String, String> pais = new HashMap<>();

        @Override
        public ResultadoPartida jogar(Torneio t, Partida p, boolean prorrogacao) {
            ResultadoPartida r = new ResultadoPartida();
            r.golsMandante = rng.poisson(1.4);
            r.golsVisitante = rng.poisson(1.1);
            return r;
        }

        @Override
        public int[] penaltis(Torneio t, Partida p) {
            return rng.chance(0.5) ? new int[]{5, 4} : new int[]{3, 4};
        }

        @Override
        public Torneio outroTorneio(Torneio atual, String competicaoId) {
            return outros.get(competicaoId);
        }

        @Override
        public String paisDe(String clubeId) {
            return pais.get(clubeId);
        }
    }

    static Torneio torneio(String id, List<String> part) {
        Torneio t = new Torneio();
        t.id = id;
        t.nome = id;
        t.ambito = "continental";
        t.confederacao = "CONMEBOL";
        t.ano = 2026;
        t.semente = id.hashCode();
        t.formato = Conmebol.ajustar(id, null);
        t.participantes = new ArrayList<>(part);
        t.semanaInicio = Calendario.absoluta(2026, 5);
        t.semanaFim = Calendario.absoluta(2026, 48);
        t.planejar();
        Conmebol.calendario(t, 2026);
        return t;
    }

    @Test
    void estruturaETransferenciasDaLibertadores() {
        Exec ex = new Exec();
        List<String> lib = new ArrayList<>();
        for (int i = 0; i < 47; i++) {
            lib.add("L" + i);
            ex.pais.put("L" + i, PAISES[i % PAISES.length]);
        }
        List<String> sula = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            sula.add("G" + i);
            ex.pais.put("G" + i, i % 2 == 0 ? "ARG" : "BRA");
        }
        for (int p = 2; p < PAISES.length; p++)
            for (int k = 0; k < 4; k++) {
                String id = "P" + PAISES[p] + k;
                sula.add(id);
                ex.pais.put(id, PAISES[p]);
            }
        Torneio l = torneio(Conmebol.LIBERTADORES, lib), s = torneio(Conmebol.SUL_AMERICANA, sula);
        ex.outros.put(Conmebol.LIBERTADORES, l);

        // Libertadores: 3 fases prévias, 8 grupos de 4, oitavas, quartas, semifinal e final única
        assertEquals(List.of("Primeira fase", "Segunda fase", "Terceira fase", "Fase de grupos", "Oitavas de final", "Quartas de final", "Semifinal", "Final"),
                l.fases.stream().map(f -> f.nome).toList());
        l.concluir(ex);
        int[] lClubes = {6, 16, 8, 32, 16, 8, 4, 2};
        for (int i = 0; i < lClubes.length; i++) assertEquals(lClubes[i], l.fases.get(i).clubes.size(), "Libertadores, " + l.fases.get(i).nome);
        FaseExec lg = l.fases.get(3);
        assertEquals(8, lg.grupos.size());
        for (List<String> g : lg.grupos) {
            assertEquals(4, g.size());
            assertEquals(4, g.stream().map(ex.pais::get).distinct().count(), "sem dois do mesmo país no grupo: " + g);
        }
        // oitavas: 1º de grupo × 2º, o 1º decide em casa
        Set<String> primeiros = new HashSet<>();
        for (int g = 0; g < 8; g++) primeiros.add(l.tabela(3, g).get(0).clubeId);
        for (Confronto c : l.fases.get(4).confrontos) {
            assertTrue(primeiros.contains(c.a) && !primeiros.contains(c.b), "oitavas 1º × 2º: " + c.a + " × " + c.b);
            Partida volta = l.partidas.get(c.partidas.get(1));
            assertEquals(c.a, volta.mandante, "1º de grupo decide em casa");
        }
        // final em jogo único
        assertEquals(1, l.fases.get(7).confrontos.get(0).partidas.size());
        assertNotNull(l.campeao);
        // datas reais: a 3ª fase termina antes dos grupos da Sul-Americana; os grupos, antes do playoff
        int fimTerceira = 0, fimGrupos = 0;
        for (Partida p : l.partidas) {
            if (p.fase == 2) fimTerceira = Math.max(fimTerceira, p.sa);
            if (p.fase == 3) fimGrupos = Math.max(fimGrupos, p.sa);
        }

        Set<String> eliminados3 = new HashSet<>();
        for (Confronto c : l.fases.get(2).confrontos) eliminados3.add(c.perdedor);
        List<String> terceiros = new ArrayList<>();
        for (int g = 0; g < 8; g++) terceiros.add(l.tabela(3, g).get(2).clubeId);

        // Sul-Americana
        assertEquals(List.of("Primeira fase", "Fase de grupos", "Playoffs das oitavas", "Oitavas de final", "Quartas de final", "Semifinal", "Final"),
                s.fases.stream().map(f -> f.nome).toList());
        assertEquals(44, s.participantes.size());
        s.concluir(ex);
        int[] sClubes = {32, 32, 16, 16, 8, 4, 2};
        for (int i = 0; i < sClubes.length; i++) assertEquals(sClubes[i], s.fases.get(i).clubes.size(), "Sul-Americana, " + s.fases.get(i).nome);
        assertEquals(56, s.participantes.size(), "44 + 4 eliminados da 3ª fase + 8 terceiros da Libertadores");
        // 1ª fase: jogo único entre clubes do mesmo país
        FaseExec p1 = s.fases.get(0);
        assertEquals(16, p1.confrontos.size());
        for (Confronto c : p1.confrontos) {
            assertEquals(1, c.partidas.size());
            assertEquals(ex.pais.get(c.a), ex.pais.get(c.b), "1ª fase entre clubes do mesmo país");
            assertFalse(Set.of("ARG", "BRA").contains(ex.pais.get(c.a)));
        }
        // grupos: 16 da 1ª fase + 12 de ARG/BRA + os 4 eliminados da 3ª fase da Libertadores
        FaseExec g = s.fases.get(1);
        assertTrue(g.clubes.containsAll(eliminados3), "eliminados da 3ª fase da Libertadores nos grupos");
        for (int i = 0; i < 12; i++) assertTrue(g.clubes.contains("G" + i));
        for (List<String> gr : g.grupos) assertEquals(4, gr.size());
        // playoffs: os 8 segundos × os 8 terceiros da Libertadores, o 2º decide em casa
        FaseExec po = s.fases.get(2);
        Set<String> segundos = new HashSet<>(), lideres = new HashSet<>();
        for (int k = 0; k < 8; k++) {
            lideres.add(s.tabela(1, k).get(0).clubeId);
            segundos.add(s.tabela(1, k).get(1).clubeId);
        }
        assertTrue(po.clubes.containsAll(terceiros), "3ºs da Libertadores nos playoffs");
        assertTrue(po.clubes.containsAll(segundos), "2ºs de grupo nos playoffs");
        for (String id : lideres) assertFalse(po.clubes.contains(id), "1º de grupo não joga o playoff");
        assertEquals(8, po.confrontos.size());
        for (Confronto c : po.confrontos) {
            assertTrue(segundos.contains(c.a) && terceiros.contains(c.b), "playoff 2º × 3º da Libertadores: " + c.a + " × " + c.b);
            assertEquals(c.a, s.partidas.get(c.partidas.get(1)).mandante, "2º de grupo decide em casa");
        }
        // oitavas: 1ºs de grupo × vencedores do playoff
        FaseExec oit = s.fases.get(3);
        assertTrue(oit.clubes.containsAll(lideres));
        for (Confronto c : oit.confrontos) assertTrue(lideres.contains(c.a) && !lideres.contains(c.b), "oitavas 1º × vencedor do playoff");
        // quartas em chave fixa: vencedores de confrontos vizinhos das oitavas
        List<String> vencOit = new ArrayList<>();
        for (Confronto c : oit.confrontos) vencOit.add(c.vencedor);
        for (Confronto c : s.fases.get(4).confrontos) {
            int ia = vencOit.indexOf(c.a), ib = vencOit.indexOf(c.b);
            assertEquals(Math.min(ia, ib) / 2, Math.max(ia, ib) / 2, "chave fixa nas quartas");
        }
        assertEquals(1, s.fases.get(6).confrontos.get(0).partidas.size(), "final única");
        assertNotNull(s.campeao);
        // calendário: o playoff depois dos grupos da Libertadores; os grupos depois da 3ª fase
        int iniGrupos = Integer.MAX_VALUE, iniPlayoff = Integer.MAX_VALUE;
        for (Partida p : s.partidas) {
            if (p.fase == 1) iniGrupos = Math.min(iniGrupos, p.sa);
            if (p.fase == 2) iniPlayoff = Math.min(iniPlayoff, p.sa);
        }
        assertTrue(fimTerceira < iniGrupos, "3ª fase da Libertadores antes dos grupos da Sul-Americana");
        assertTrue(fimGrupos < iniPlayoff, "grupos da Libertadores antes do playoff da Sul-Americana");
        assertEquals(Calendario.absoluta(java.time.LocalDate.of(2026, 11, 21)), s.partidas.get(s.partidas.size() - 1).sa, "final em 21/11");
    }

    @Test
    void temporadaInteiraNoBancoReal() {
        Path banco = LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite");
        Assumptions.assumeTrue(Files.exists(banco), "sem o banco real");
        Simulador s = Simulador.novo(BancoMundo.carregar(banco), 4242, null, null);
        while (s.m.ano == 2026) s.avancarSemana();
        for (int k = 0; k < 14; k++) s.avancarSemana(); // 2027 até depois da 1ª fase da Sul-Americana
        for (int ano : new int[]{2026, 2027}) {
            Torneio lib = null, sula = null;
            for (List<Torneio> l : List.of(s.m.torneios, s.m.torneiosAnteriores))
                for (Torneio t : l) {
                    if (t.ano != ano) continue;
                    if (t.id.equals(Conmebol.LIBERTADORES)) lib = t;
                    if (t.id.equals(Conmebol.SUL_AMERICANA)) sula = t;
                }
            assertNotNull(lib, "Libertadores " + ano);
            assertNotNull(sula, "Sul-Americana " + ano);
            assertEquals(47, lib.fases.get(0).clubes.size() + lib.fases.get(1).estreantes.size() + lib.fases.get(3).estreantes.size(), "Libertadores com 47");
            FaseExec p1 = sula.fases.get(0);
            assertEquals(32, p1.clubes.size(), "1ª fase da Sul-Americana com 32 (" + ano + ")");
            for (Confronto c : p1.confrontos) assertEquals(s.m.clube(c.a).pais, s.m.clube(c.b).pais, "1ª fase entre clubes do mesmo país");
            Map<String, Integer> porPais = new HashMap<>();
            for (int i = 0; i < 44; i++) porPais.merge(s.m.clube(sula.participantes.get(i)).pais, 1, Integer::sum);
            for (String p : PAISES) assertEquals(Conmebol.VAGAS_SUL_AMERICANA.get(p), porPais.getOrDefault(p, 0), "vagas da Sul-Americana " + ano + ", " + p);
            for (String id : sula.participantes.subList(0, 12)) assertTrue(Set.of("ARG", "BRA").contains(s.m.clube(id).pais), "ARG/BRA direto nos grupos");
            Set<String> naLib = new HashSet<>(lib.participantes);
            for (String id : sula.participantes.subList(0, 44)) assertFalse(naLib.contains(id), "clube nas duas: " + id);
            if (ano == 2026) {
                assertTrue(lib.encerrado && sula.encerrado, "edições de 2026 terminadas");
                assertNotNull(sula.campeao);
                assertEquals(56, sula.participantes.size(), "Sul-Americana com 56: " + sula.avisos);
                Set<String> terceiros = new HashSet<>();
                for (int g = 0; g < 8; g++) terceiros.add(lib.tabela(3, g).get(2).clubeId);
                assertTrue(sula.fases.get(2).clubes.containsAll(terceiros), "3ºs da Libertadores nos playoffs da Sul-Americana");
                for (Confronto c : lib.fases.get(2).confrontos) assertTrue(sula.fases.get(1).clubes.contains(c.perdedor), "eliminado da 3ª fase nos grupos: " + c.perdedor);
            } else {
                // 2027: o campeão brasileiro de 2026 está na Libertadores, nos grupos
                Torneio bra = s.m.torneioAnterior("bra-1");
                assertTrue(lib.participantes.subList(0, 28).contains(bra.classificacao.get(0)), "campeão brasileiro nos grupos da Libertadores");
            }
        }
    }
}
