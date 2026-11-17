package br.futebas.kings;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.ResultadoPartida;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Times customizados (Kings League IOS) e presidentes-jogadores: presidentes 75/ATA/18 anos (o mais
 * novo com a idade mínima), fixos no time e ocupando vaga de wildcard; jogam o 7x7 e os
 * shootouts; o pênalti do presidente é sempre de um presidente; Kelvin Oliveira no G3X; vaga da
 * liga custom na Kings World Cup Clubs; com a opção desligada nada muda.
 */
class KingsCustomEPresidentesTest {

    /** A definição real (dados/custom) se existir; senão, 20 times de teste. */
    static KingsCustom.Definicao definicao() {
        Path f = KingsCustom.localizar(null);
        if (f != null) return KingsCustom.carregar(f);
        StringBuilder sb = new StringBuilder("{\"liga\":{\"id\":\"kings-ios\",\"nome\":\"Kings League IOS\",\"nome_copa\":\"Kings Cup IOS\"},"
                + "\"presidente_jogador\":{\"forca\":75,\"posicao\":\"CA\",\"idade\":18},\"times\":[");
        for (int i = 1; i <= 20; i++) {
            if (i > 1) sb.append(',');
            sb.append("{\"id\":\"kings-ios-t").append(i).append("\",\"nome\":\"Time ").append(i).append("\",\"presidente\":\"Pres ").append(i)
                    .append("\",\"presidente_id\":\"ios-p").append(i).append("\",\"idade\":").append(i == 8 ? 16 : 18).append('}');
        }
        return KingsCustom.ler(sb.append("]}").toString());
    }

    private static LigaKings ios(Jogo j) {
        return j.ligasKings().stream().filter(l -> l.id.equals(KingsCustom.LIGA_ID)).findFirst().orElse(null);
    }

    private static List<LigaKings.AtletaKings> presidentes(LigaKings.TimeKings t) {
        List<LigaKings.AtletaKings> l = new ArrayList<>();
        for (LigaKings.AtletaKings a : t.elenco) if (a.presidente) l.add(a);
        return l;
    }

    @Test
    void desligadoNadaMuda() {
        Jogo a = Jogo.novoJogo(Fixture.banco(), 77, "bra-1-02", "T");
        Jogo b = Jogo.novoJogo(Fixture.banco(), 77, "bra-1-02", "T", null);
        assertNull(ios(a));
        assertFalse(a.mundo().timesCustom);
        for (int i = 0; i < 6; i++) {
            if (a.meuClube() != null) a.meuClube().confiancaDiretoria = 90;
            if (b.meuClube() != null) b.meuClube().confiancaDiretoria = 90;
            a.avancarSemana();
            b.avancarSemana();
        }
        for (String id : List.of("kings-brasil-split1", "kings-espanha-split1")) {
            Torneio x = a.competicaoKings(id), y = b.competicaoKings(id);
            assertEquals(x.partidas.size(), y.partidas.size());
            for (int i = 0; i < x.partidas.size(); i++)
                assertEquals(x.partidas.get(i).golsMandante + "x" + x.partidas.get(i).golsVisitante,
                        y.partidas.get(i).golsMandante + "x" + y.partidas.get(i).golsVisitante, "determinismo sem a liga custom");
        }
    }

    @Test
    void ligaIosPresidentesEMundial() {
        KingsCustom.Definicao def = definicao();
        LigaKings.TimeKings[] meu = new LigaKings.TimeKings[1];
        Jogo jogo = Jogo.novoJogoKings(Fixture.banco(), 41, null, def.times.get(0).id, "T", def);
        assertTrue(jogo.mundo().timesCustom);
        LigaKings liga = ios(jogo);
        assertNotNull(liga);
        assertEquals(20, liga.times.size());
        assertNull(liga.pais, "liga sem país");
        meu[0] = liga.time(def.times.get(0).id);
        assertTrue(meu[0].humano, "o humano pode começar como técnico de um time da IOS");
        Map<String, String> presidentesIniciais = new LinkedHashMap<>();
        boolean minima = false;
        for (LigaKings.TimeKings t : liga.times) {
            presidentesIniciais.put(t.id, t.presidente);
            List<LigaKings.AtletaKings> ps = presidentes(t);
            assertEquals(1, ps.size(), "um presidente-jogador: " + t.nome);
            LigaKings.AtletaKings p = ps.get(0);
            assertEquals(t.presidente, p.nome);
            assertEquals(75, p.forca, 0.001);
            assertEquals(Posicao.CA, p.posicao);
            int idade = 2026 - p.anoNasc;
            assertTrue(idade == 18 || idade == Kings.IDADE_MINIMA_JOGAR, "idade " + idade);
            if (idade == Kings.IDADE_MINIMA_JOGAR) minima = true;
        }
        assertTrue(minima, "o presidente mais novo tem a idade mínima da Kings");

        // Kings League IOS (requisito): presidente + 11 atletas genéricos gerados, sem draft do mundo
        while (jogo.semana() < 5) jogo.avancarSemana();
        assertTrue(liga.ativa);
        java.util.Set<String> nomes = new java.util.HashSet<>();
        for (LigaKings.TimeKings t : liga.times) {
            assertEquals(12, t.elenco.size(), "presidente + 11 genéricos: " + t.nome);
            assertEquals(Kings.WILDCARDS, t.elenco.stream().filter(a -> a.presidente || "wildcard".equals(a.tipo)).count(),
                    "o presidente ocupa uma das 6 vagas de wildcard");
            assertTrue(t.elenco.stream().anyMatch(a -> a.posicao == Posicao.GOL), "tem goleiro: " + t.nome);
            for (LigaKings.AtletaKings a : t.elenco) {
                if (a.presidente) continue;
                assertNull(a.jogadorId, "genérico, não é jogador do mundo");
                assertTrue(a.forca >= 60 && a.forca <= 76, "força na escala da IOS: " + a.forca);
                assertTrue(nomes.add(a.nome), "nome repetido: " + a.nome);
            }
            long[] setor = new long[4];
            for (LigaKings.AtletaKings a : t.elenco) setor[PosicoesKings.setor(a.posicao)]++;
            assertTrue(setor[1] >= 3 && setor[2] >= 3 && setor[3] >= 3, "elenco equilibrado: " + java.util.Arrays.toString(setor));
        }
        // o presidente nunca sai (nem pelo técnico)
        LigaKings.AtletaKings pres = presidentes(meu[0]).get(0);
        for (LigaKings.AtletaKings a : new ArrayList<>(meu[0].elenco)) if (a.jogadorId != null) jogo.dispensarDaKings(a.jogadorId);
        assertTrue(meu[0].elenco.contains(pres), "presidente nunca sai");
        // alguém saiu: a vaga volta a ser preenchida por um genérico
        LigaKings.AtletaKings sai = meu[0].elenco.stream().filter(a -> !a.presidente).reduce((x, y) -> y).orElseThrow();
        meu[0].elenco.remove(sai);
        assertEquals(11, meu[0].elenco.size());
        jogo.avancarSemana();
        assertEquals(12, meu[0].elenco.size(), "completado de novo");

        assertNotNull(jogo.competicaoKings("kings-ios-split1"));
        assertTrue(jogo.competicaoKings("kings-ios-cup").nome.startsWith("Kings Cup IOS"));
        while (jogo.ano() == 2026 && jogo.semana() < 32) jogo.avancarSemana();
        Torneio mc = jogo.simulador().kings.mundial(Kings.MUNDIAL_CLUBES, 2026);
        assertNotNull(mc, "Kings World Cup Clubs");
        long iosNoMundial = mc.participantes.stream().filter(id -> liga.time(id) != null).count();
        assertEquals(1, iosNoMundial, "uma vaga para a Kings League IOS");
        assertEquals(0, mc.participantes.size() % 4);
        assertTrue(mc.participantes.size() <= 16);
        // o presidente joga o 7x7: marca gols na temporada
        assertTrue(liga.times.stream().anyMatch(t -> presidentes(t).get(0).gols > 0), "presidentes marcam no jogo normal");
        // presidentes nunca mudam
        while (jogo.ano() < 2028) jogo.avancarSemana();
        for (LigaKings.TimeKings t : liga.times) {
            assertEquals(presidentesIniciais.get(t.id), t.presidente);
            assertEquals(1, presidentes(t).size(), "presidente segue no elenco depois do draft: " + t.nome);
            assertEquals(75, presidentes(t).get(0).forca, 0.001);
        }
    }

    @Test
    void penaltiDoPresidenteEShootoutsComOPresidente() {
        LigaKings.TimeKings a = MotorKings2026Test.time("a", 60, "Gaules / Kelvin Oliveira");
        LigaKings.TimeKings b = MotorKings2026Test.time("b", 60, "Pres B");
        LigaKings.AtletaKings kelvin = new LigaKings.AtletaKings();
        kelvin.nome = "Kelvin Oliveira";
        kelvin.forca = 66;
        kelvin.posicao = Posicao.PD;
        kelvin.presidente = true;
        kelvin.tipo = "presidente";
        a.elenco.add(kelvin);
        Rng rng = new Rng(5);
        int presA = 0, gaules = 0, kelvinPen = 0, kelvinGols = 0, kelvinShoot = 0;
        for (int i = 0; i < 1500; i++) {
            ResultadoPartida r = MotorKings.simular(a, b, new LigaKings.RegrasKings(), rng.derivar(i), false);
            for (EventoJogo e : r.eventos) {
                if (e.tipo().startsWith("penalti_presidente") && e.lado() == 0) {
                    presA++;
                    assertTrue("Gaules".equals(e.jogador()) || "Kelvin Oliveira".equals(e.jogador()), "cobrador: " + e.jogador());
                    if ("Gaules".equals(e.jogador())) gaules++;
                    else kelvinPen++;
                }
                if (e.tipo().startsWith("penalti_presidente") && e.lado() == 1) assertEquals("Pres B", e.jogador());
                if (e.lado() == 0 && "Kelvin Oliveira".equals(e.jogador()) && (e.tipo().equals("gol") || e.tipo().equals("gol_x2"))) kelvinGols++;
                if (e.lado() == 0 && "Kelvin Oliveira".equals(e.jogador()) && e.tipo().startsWith("disputa")) kelvinShoot++;
            }
        }
        assertTrue(presA > 500 && gaules > 0 && kelvinPen > 0, "os dois presidentes se revezam no pênalti");
        assertTrue(kelvinGols > 0, "presidente-jogador joga o 7x7");
        assertTrue(kelvinShoot > 0, "e bate shootouts");
    }

    /** Banco fictício com o G3X (Kelvin Oliveira presidente e jogador; Gaules só presidente) e um presidente que aparece como jogador (não joga). */
    static BancoMundo bancoComG3x() {
        BancoMundo db = BancoMundo.carregar(Fixture.caminho());
        String f = "https://exemplo.test/kings-ficticia";
        db.kingsTimes.put("kings-brasil-g3x", new Db.KingsTime("kings-brasil-g3x", "kings-brasil", "G3X FC", "Gaules / Kelvin Oliveira",
                "assets/kings/escudos/kings-brasil-g3x.png", "#FFFFFF", "#01426A", f));
        for (int i = 1; i <= 9; i++) db.kingsElencos.add(new Db.KingsElenco("kings-brasil-g3x", "2026", null, "Atleta G3X " + i, "draft", f));
        db.kingsElencos.add(new Db.KingsElenco("kings-brasil-g3x", "2026", null, "Gaules", "presidente", f));
        db.kingsElencos.add(new Db.KingsElenco("kings-brasil-g3x", "2026", null, "Kelvin Oliveira", "presidente", f));
        // genérico: presidente que também aparece como jogador do time
        db.kingsElencos.add(new Db.KingsElenco("kb-1", "2026", null, "Presidente 1", "presidente", f));
        db.kingsElencos.add(new Db.KingsElenco("kb-1", "2025", null, "Presidente 1", "coringa", f));
        return db;
    }

    @Test
    void kelvinFicaNoG3xParaSempre() {
        Jogo jogo = Jogo.novoJogo(bancoComG3x(), 13, "bra-1-02", "T");
        LigaKings liga = jogo.ligasKings().stream().filter(l -> l.id.equals("kings-brasil")).findFirst().orElseThrow();
        LigaKings.TimeKings g3x = liga.time("kings-brasil-g3x");
        assertEquals(List.of("Gaules", "Kelvin Oliveira"), g3x.nomesPresidentes());
        assertEquals(1, presidentes(g3x).size());
        assertEquals("Kelvin Oliveira", presidentes(g3x).get(0).nome, "Gaules não vira jogador");
        // regra do jogo (12/11/2026): fora a IOS, só o Kelvin joga; o presidente que aparece nas súmulas como jogador não vira atleta
        assertEquals(0, presidentes(liga.time("kb-1")).size(), "presidente comum não joga");
        assertTrue(liga.time("kb-1").presidentesJogadores.isEmpty());
        int seasons = 0;
        for (int ano = 2026; ano <= 2036; ano++) {
            while (jogo.ano() == ano) {
                if (jogo.meuClube() != null) jogo.meuClube().confiancaDiretoria = 90;
                jogo.avancarSemana();
                assertEquals(1, g3x.elenco.stream().filter(a -> a.presidente && a.nome.equals("Kelvin Oliveira")).count(), "Kelvin sempre no G3X");
            }
            seasons++;
            assertEquals("Gaules / Kelvin Oliveira", g3x.presidente, "presidentes nunca mudam");
            assertEquals("Presidente 1", liga.time("kb-1").presidente);
            if (ano > 2026) assertTrue(g3x.elenco.size() <= 12 && g3x.elenco.stream().filter(a -> a.presidente || "wildcard".equals(a.tipo) || "mantido".equals(a.tipo)).count() <= Kings.WILDCARDS,
                    "Kelvin ocupa vaga de wildcard");
        }
        assertTrue(seasons >= 10);
        assertTrue(liga.temporadas >= 10);
    }
}
