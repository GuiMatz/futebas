package br.futebas.kings;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.dados.BancoMundo;
import br.futebas.ferramentas.LongRun;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;
import br.futebas.mundo.Simulador;
import br.futebas.partida.EventoJogo;
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
 * Balanço 2026-11, rodada 2 (Kings): posições oficiais (goleiro em todo time), pênalti do presidente
 * fora da artilharia, rodízio pelo banco do técnico, carreira da Kings separada e confiança do
 * presidente guardada no save.
 */
class KingsRodada2Test {

    static LigaKings.AtletaKings atleta(String nome, double forca) {
        LigaKings.AtletaKings a = new LigaKings.AtletaKings();
        a.nome = nome;
        a.forca = forca;
        a.fonte = "teste";
        return a;
    }

    static LigaKings.TimeKings time(String id, int n, double forca) {
        LigaKings.TimeKings t = new LigaKings.TimeKings();
        t.id = id;
        t.nome = id;
        for (int i = 0; i < n; i++) t.elenco.add(atleta(id + " A" + i, forca - i * 0.5));
        return t;
    }

    @Test
    void posicoesOficiaisEGoleiroEmTodoTime() {
        LigaKings.TimeKings t = time("kt", 12, 70);
        Map<String, String> mapa = new HashMap<>();
        mapa.put(PosicoesKings.chave("kt", "kt A3"), "GOL");
        mapa.put(PosicoesKings.chave("kt", "kt A0"), "ATA");
        mapa.put(PosicoesKings.chave("kt", "kt A1"), "DEF");
        mapa.put(PosicoesKings.chave("kt", "kt A2"), "MEI");
        assertEquals(4, PosicoesKings.aplicar(t, mapa));
        assertEquals(Posicao.GOL, t.elenco.get(3).posicao);
        assertTrue(Set.of(Posicao.CA, Posicao.PE, Posicao.PD).contains(t.elenco.get(0).posicao));
        assertTrue(Set.of(Posicao.ZAG, Posicao.LD, Posicao.LE).contains(t.elenco.get(1).posicao));
        Map<Integer, Integer> setores = new HashMap<>();
        for (LigaKings.AtletaKings a : t.elenco) setores.merge(PosicoesKings.setor(a.posicao), 1, Integer::sum);
        assertEquals(2, setores.get(0), "12 atletas: 2 goleiros (1 do arquivo + 1 pela necessidade)");
        for (int s = 1; s <= 3; s++) assertTrue(setores.getOrDefault(s, 0) >= 2, "setor " + s + " equilibrado: " + setores);
        // sem arquivo nenhum: ainda assim um goleiro
        LigaKings.TimeKings u = time("ku", 7, 60);
        PosicoesKings.aplicar(u, Map.of());
        assertTrue(u.elenco.stream().anyMatch(a -> a.posicao == Posicao.GOL));
    }

    @Test
    void bancoRealTodoTimeDaKingsTemGoleiroInclusiveSaveAntigo() {
        Path banco = LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite");
        Assumptions.assumeTrue(Files.exists(banco), "sem o banco real");
        BancoMundo db = BancoMundo.carregar(banco);
        Assumptions.assumeTrue(!PosicoesKings.mapa(db).isEmpty(), "sem assets/kings/posicoes.json");
        Simulador s = Simulador.novo(db, 31, null, null);
        for (int i = 0; i < 3; i++) s.avancarSemana();
        int times = 0;
        for (LigaKings l : s.m.kings) {
            if (!l.ativa) continue;
            for (LigaKings.TimeKings t : l.times) {
                if (t.elenco.isEmpty()) continue;
                times++;
                assertTrue(t.elenco.stream().anyMatch(a -> a.posicao == Posicao.GOL), "sem goleiro: " + t.nome + " " + PosicoesKings.contagem(t));
            }
        }
        Assumptions.assumeTrue(times > 0, "nenhuma liga da Kings ativa nas primeiras semanas");
        // save antigo: todos os atletas sem jogador do mundo voltam a MC; carregar (novo Kings) recoloca as posições
        for (LigaKings l : s.m.kings) for (LigaKings.TimeKings t : l.times) for (LigaKings.AtletaKings a : t.elenco)
            if (a.jogadorId == null && !a.presidente) a.posicao = Posicao.MC;
        new Kings(s.m, db);
        for (LigaKings l : s.m.kings) {
            if (!l.ativa) continue;
            for (LigaKings.TimeKings t : l.times) if (!t.elenco.isEmpty())
                assertTrue(t.elenco.stream().anyMatch(a -> a.posicao == Posicao.GOL), "save antigo sem goleiro: " + t.nome);
        }
    }

    @Test
    void penaltiDoPresidenteNaoEntraNaArtilharia() {
        LigaKings.TimeKings a = time("pa", 12, 68), b = time("pb", 12, 68);
        for (LigaKings.TimeKings t : List.of(a, b)) {
            PosicoesKings.aplicar(t, Map.of());
            LigaKings.AtletaKings p = atleta("Pres " + t.id, 75);
            p.presidente = true;
            p.posicao = Posicao.CA;
            t.presidente = p.nome;
            t.elenco.add(0, p);
        }
        int presGols = 0;
        for (int s = 0; s < 150; s++) {
            Map<LigaKings.AtletaKings, Integer> antes = new HashMap<>();
            for (LigaKings.TimeKings t : List.of(a, b)) for (LigaKings.AtletaKings x : t.elenco) antes.put(x, x.gols);
            ResultadoPartida r = MotorKings.simular(a, b, null, new Rng(500 + s), false);
            Map<String, Integer> esperado = new HashMap<>();
            for (EventoJogo e : r.eventos) {
                if (!e.gol() || e.jogador() == null) continue;
                if (e.tipo().startsWith("penalti_presidente")) {
                    presGols++;
                    continue;
                }
                esperado.merge(e.jogador(), 1, Integer::sum);
            }
            for (LigaKings.TimeKings t : List.of(a, b)) for (LigaKings.AtletaKings x : t.elenco)
                assertEquals((int) esperado.getOrDefault(x.nome, 0), x.gols - antes.get(x), "gols de " + x.nome + " (o pênalti do presidente não conta)");
        }
        assertTrue(presGols > 0, "houve pênaltis do presidente convertidos");
    }

    @Test
    void rodizioUsaOBancoNaOrdemDoTecnico() {
        LigaKings.TimeKings h = time("hh", 12, 70), ia = time("ii", 12, 70);
        PosicoesKings.aplicar(h, Map.of());
        PosicoesKings.aplicar(ia, Map.of());
        h.humano = true;
        // 7 titulares + 2 no banco (na ordem); os outros 3 não entram
        List<LigaKings.AtletaKings> linha = new ArrayList<>();
        LigaKings.AtletaKings gk = null;
        for (LigaKings.AtletaKings x : h.elenco) if (x.posicao == Posicao.GOL && gk == null) gk = x; else linha.add(x);
        List<String> lista = new ArrayList<>(List.of(gk.chave()));
        for (int i = 0; i < 8; i++) lista.add(linha.get(i).chave());
        h.titulares = lista;
        Set<String> listados = new HashSet<>(lista), bancoListado = Set.of(lista.get(7), lista.get(8));
        Set<String> marcaram = new HashSet<>();
        for (int s = 0; s < 120; s++) {
            ResultadoPartida r = MotorKings.simular(h, ia, null, new Rng(900 + s), true);
            for (EventoJogo e : r.eventos) {
                if (e.lado() != 0 || !e.gol() || e.jogador() == null || e.tipo().startsWith("penalti_presidente") || e.tipo().startsWith("disputa")) continue;
                LigaKings.AtletaKings x = h.elenco.stream().filter(y -> y.nome.equals(e.jogador())).findFirst().orElse(null);
                if (x == null) continue;
                assertTrue(listados.contains(x.chave()), "marcou quem o técnico não relacionou: " + x.nome);
                marcaram.add(x.chave());
            }
            if (s == 0) assertTrue(r.lances.stream().anyMatch(l -> "substituicao".equals(l.tipo)), "houve rodízio no jogo");
        }
        assertTrue(marcaram.stream().anyMatch(bancoListado::contains), "quem entra do banco também marca");
        // só 7 relacionados: sem rodízio (o comportamento antigo)
        h.titulares = new ArrayList<>(lista.subList(0, 7));
        ResultadoPartida r = MotorKings.simular(h, ia, null, new Rng(1), true);
        assertTrue(r.lances.stream().noneMatch(l -> "substituicao".equals(l.tipo) && l.lado == 0), "sem banco relacionado, ninguém entra");
    }

    @Test
    void carreiraDaKingsEConfiancaDoPresidenteNoSave() throws Exception {
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 73, null, "kb-3", "T");
        assertEquals(Kings.CONFIANCA_INICIAL_KINGS, j.confiancaPresidenteKings(), 1e-9);
        for (int i = 0; i < 30 && j.recordeKings()[0] < 3 && j.meuTimeKings() != null; i++) j.avancarSemana();
        int[] rec = j.recordeKings();
        assertTrue(rec[0] >= 1, "jogos da Kings contam na carreira");
        assertEquals(rec[0], rec[1] + rec[2], "Kings não tem empate: V + D = J");
        assertTrue(rec[3] <= rec[1] && rec[4] <= rec[2]);
        assertEquals(0, j.mundo().tecnico.jogos, "a carreira do futebol não muda");
        if (j.meuTimeKings() != null) {
            double c = j.confiancaPresidenteKings();
            Path arq = Files.createTempDirectory("futebas-kings-r2").resolve("k.futebas.json.gz");
            j.salvar(arq);
            Jogo c2 = Jogo.carregar(arq, Fixture.banco());
            assertEquals(c, c2.confiancaPresidenteKings(), 1e-9, "a confiança fica no save");
            assertArrayEquals(rec, c2.recordeKings());
        }
        while (j.ano() == 2026) j.avancarSemana();
        assertFalse(j.carreiraKings().isEmpty(), "linha da Kings na carreira no fim do ano (ou na saída)");
        assertTrue(j.carreiraKings().get(0).kings);
    }

    @Test
    void presidenteDemiteDepoisDeSequenciaRuimMasProtegeOsPrimeirosJogos() {
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 74, null, "kb-3", "T");
        Kings k = j.simulador().kings;
        LigaKings.TimeKings meu = j.meuTimeKings();
        LigaKings liga = j.ligaKingsDoTime(meu.id);
        LigaKings.TimeKings adv = liga.times.stream().filter(x -> x != meu).findFirst().orElseThrow();
        ResultadoPartida derrota = new ResultadoPartida();
        derrota.golsMandante = 1;
        derrota.golsVisitante = 5;
        j.mundo().tecnico.confiancaKings = 5.0;
        k.resultadoDoHumano(meu, adv, derrota, 0, liga.regras);
        assertNotNull(j.meuTimeKings(), "nos primeiros jogos no time o presidente espera");
        j.mundo().tecnico.kingsJogosNoTime = Kings.KINGS_JOGOS_PROTEGIDOS;
        int dem = j.mundo().tecnico.demissoes;
        k.resultadoDoHumano(meu, adv, derrota, 0, liga.regras);
        assertNull(j.meuTimeKings(), "sequência ruim: demitido");
        assertEquals(dem + 1, j.mundo().tecnico.demissoes);
        assertTrue(meu.vagaAte > 0, "a vaga abre");
        assertEquals("Demitido", j.carreiraKings().get(j.carreiraKings().size() - 1).saida);
    }
}
