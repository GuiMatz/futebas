package br.futebas.kings;

import br.futebas.Fixture;
import br.futebas.Jogo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regras do jogo (12/11/2026): todo time da Kings tem no máximo 12 atletas (presidentes que jogam
 * contam; os outros presidentes não são atletas), a regra do jogador 13 acabou e a força dos
 * atletas segue uma escala só ({@link Kings#escalaKings}).
 */
class KingsElencoMaximoTest {

    private static void conferir(Jogo jogo, String quando) {
        for (LigaKings l : jogo.ligasKings()) for (LigaKings.TimeKings t : l.times) {
            assertTrue(t.elenco.size() <= Kings.MAX_ELENCO, quando + ": " + l.id + " / " + t.nome + " tem " + t.elenco.size());
            for (LigaKings.AtletaKings a : t.elenco) {
                assertNotEquals("13", a.tipo, "sem jogador 13");
                assertTrue(a.forca <= Kings.FORCA_MAX + 1e-9, "teto 80: " + a.nome);
                if (a.presidente && !l.custom) assertEquals("Kelvin Oliveira", a.nome, "fora da IOS só o Kelvin joga");
            }
        }
    }

    @Test
    void escalaUnica() {
        assertEquals(62, Kings.escalaKings(50), 1e-9);
        assertEquals(48, Kings.escalaKings(36), 1e-9);
        assertEquals(69.6, Kings.escalaKings(66), 1e-9);
        assertEquals(74, Kings.escalaKings(74), 1e-9);
        assertEquals(80, Kings.escalaKings(80), 1e-9);
        assertEquals(80, Kings.escalaKings(85), 1e-9);
        for (double f = 20; f < 90; f += 0.5) assertTrue(Kings.escalaKings(f + 0.5) >= Kings.escalaKings(f), "monótona em " + f);
    }

    @Test
    void noMaximo12NoMundoNovoEDepoisDeVariasTemporadas() {
        Jogo jogo = Jogo.novoJogo(Fixture.banco(), 57, "bra-1-02", "T");
        conferir(jogo, "mundo novo");
        for (int ano = 2026; ano <= 2029; ano++) {
            while (jogo.ano() == ano) {
                if (jogo.meuClube() != null) jogo.meuClube().confiancaDiretoria = 90;
                jogo.avancarSemana();
                conferir(jogo, ano + "/" + jogo.semana());
            }
        }
        LigaKings br = jogo.ligasKings().stream().filter(l -> l.id.equals("kings-brasil")).findFirst().orElseThrow();
        for (LigaKings.TimeKings t : br.times) {
            for (LigaKings.AtletaKings a : t.elenco) {
                if (a.jogadorId == null) continue;
                assertEquals(t.id, jogo.jogador(a.jogadorId).kingsTimeId, "atleta do elenco aponta para o time");
            }
        }
    }

    /** Regra do jogo: na Kings os times não pegam jogadores da 1ª divisão (draft, wildcard, reposição). */
    @Test
    void ninguemDaPrimeiraDivisaoEntraNaKings() {
        Jogo jogo = Jogo.novoJogo(Fixture.banco(), 63, "bra-1-02", "T");
        while (jogo.ano() == 2026) {
            if (jogo.meuClube() != null) jogo.meuClube().confiancaDiretoria = 90;
            jogo.avancarSemana();
        }
        int entraram = 0;
        for (int w = 0; w < 8; w++) {
            java.util.Set<String> naKings = new java.util.HashSet<>();
            for (LigaKings l : jogo.ligasKings()) for (LigaKings.TimeKings t : l.times) for (LigaKings.AtletaKings a : t.elenco) naKings.add(t.id + a.chave());
            if (jogo.meuClube() != null) jogo.meuClube().confiancaDiretoria = 90;
            jogo.avancarSemana();
            for (LigaKings l : jogo.ligasKings()) for (LigaKings.TimeKings t : l.times) for (LigaKings.AtletaKings a : t.elenco) {
                if (naKings.contains(t.id + a.chave()) || a.jogadorId == null) continue;
                entraram++;
                // a divisão do clube NA HORA em que entrou (quem foi dispensado por um clube da 1ª e ficou livre pode entrar)
                assertNotEquals(Integer.valueOf(1), a.divisaoOrigem, a.nome + " veio da 1ª divisão (" + a.clubeOrigem + ", " + a.tipo + "@" + l.id + ")");
            }
        }
        assertTrue(entraram > 20, "houve draft/wildcards: " + entraram);
        for (LigaKings l : jogo.ligasKings())
            for (br.futebas.mundo.Jogador j : jogo.simulador().kings.elegiveis(l, true)) {
                if (j.clubeId == null) continue;
                assertNotEquals(1, jogo.clube(j.clubeId).divisao, "elegível da 1ª divisão: " + j.nomeExibicao());
            }
    }

    /** Wildcard com contrato: a proposta vai primeiro ao clube, depois ao jogador; aceitando, ele deixa o clube. */
    @Test
    void wildcardComContratoPassaPeloClube() {
        Jogo jogo = Jogo.novoJogoKings(Fixture.banco(), 64, null, "kb-3", "T");
        while (jogo.ano() == 2026) jogo.avancarSemana();
        while (jogo.estadoDraft() != null || jogo.vagasWildcardKings()[0] == 0) {
            if (jogo.estadoDraft() != null) jogo.escolherNoDraft(jogo.estadoDraft().disponiveis.get(0));
            else jogo.avancarSemana();
        }
        LigaKings liga = jogo.ligasKings().stream().filter(l -> l.time("kb-3") != null).findFirst().orElseThrow();
        br.futebas.mundo.Jogador alvo = null;
        for (br.futebas.mundo.Jogador x : jogo.mercadoKings(5000)) if (x.contrato != null && x.clubeId != null) { alvo = x; break; }
        assertNotNull(alvo, "há jogador com contrato (clube sem divisão ou divisão de baixo) no mercado da Kings");
        assertNotEquals(1, jogo.clube(alvo.clubeId).divisao);
        String clube = alvo.clubeId;
        assertTrue(jogo.contratarNaKings(alvo.id));
        LigaKings.PropostaKings p = liga.propostas.stream().filter(x -> x.timeId.equals("kb-3")).findFirst().orElseThrow();
        assertEquals("clube", p.etapa, "com contrato: o clube responde primeiro");
        assertTrue(p.taxa > 0);
        for (int i = 0; i < 3 && liga.propostas.contains(p); i++) jogo.avancarSemana();
        assertFalse(liga.propostas.contains(p), "clube e jogador responderam");
        if ("kb-3".equals(alvo.kingsTimeId)) {
            assertNull(alvo.clubeId, "aceitou: deixou o clube");
            assertFalse(jogo.clube(clube).elenco.contains(alvo.id));
        }
    }

    /** Regra do jogo: escalação automática da Kings sempre com cada um na posição correta (se o elenco permite). */
    @Test
    void escalacaoAutomaticaSemNinguemForaDePosicao() {
        Jogo jogo = Jogo.novoJogo(Fixture.banco(), 65, "bra-1-02", "T");
        for (int ano = 2026; ano <= 2027; ano++) while (jogo.ano() == ano) {
            if (jogo.meuClube() != null) jogo.meuClube().confiancaDiretoria = 90;
            jogo.avancarSemana();
        }
        int conferidos = 0;
        for (String formacao : MotorKings.FORMACOES)
            for (LigaKings l : jogo.ligasKings()) for (LigaKings.TimeKings t : l.times) {
                if (!MotorKings.temGoleiro(t)) continue;
                java.util.List<br.futebas.mundo.Posicao> vagas = MotorKings.vagasDaFormacao(formacao, 7);
                // dá para todos na posição? cada setor das vagas tem gente suficiente no elenco
                java.util.Map<br.futebas.mundo.Posicao, Integer> precisa = new java.util.HashMap<>();
                for (br.futebas.mundo.Posicao v : vagas) precisa.merge(v, 1, Integer::sum);
                boolean da = true;
                for (var e : precisa.entrySet()) {
                    long tem = t.elenco.stream().filter(a -> MotorKings.encaixe(a.posicao, e.getKey(), true) == 1).count();
                    if (tem < e.getValue()) da = false;
                }
                if (!da) continue;
                java.util.List<LigaKings.AtletaKings> esc = MotorKings.escalacaoAutomatica(t, formacao);
                assertEquals(7, esc.size());
                assertEquals(br.futebas.mundo.Posicao.GOL, esc.get(0).posicao, "goleiro de ofício no gol: " + t.nome);
                for (int i = 0; i < 7; i++)
                    assertEquals(1.0, MotorKings.encaixe(esc.get(i).posicao, vagas.get(i), true), 1e-9,
                            t.nome + " " + formacao + ": " + esc.get(i).nome + " (" + esc.get(i).posicao + ") na vaga " + vagas.get(i));
                conferidos++;
            }
        assertTrue(conferidos > 20, "times conferidos: " + conferidos);
    }

    @Test
    void elencoRealGrandeFicaCom12EGoleiro() {
        LigaKings.TimeKings t = new LigaKings.TimeKings();
        t.id = "x";
        for (int i = 0; i < 18; i++) {
            LigaKings.AtletaKings a = new LigaKings.AtletaKings();
            a.nome = "A" + i;
            a.forca = 40 + i;
            a.posicao = i == 0 ? br.futebas.mundo.Posicao.GOL : br.futebas.mundo.Posicao.MC;
            t.elenco.add(a);
        }
        LigaKings.AtletaKings pres = new LigaKings.AtletaKings();
        pres.nome = "Pres";
        pres.presidente = true;
        pres.forca = 30;
        t.elenco.add(pres);
        assertEquals(7, Kings.cortarElenco(t).size());
        assertEquals(12, t.elenco.size());
        assertTrue(t.elenco.contains(pres), "presidente que joga fica");
        assertTrue(t.elenco.stream().anyMatch(a -> a.posicao == br.futebas.mundo.Posicao.GOL), "o goleiro (mesmo fraco) fica");
    }

    @Test
    void saveAntigoCarrega() throws Exception {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 58, "bra-1-02", "T");
        for (int i = 0; i < 4; i++) {
            if (j.meuClube() != null) j.meuClube().confiancaDiretoria = 90;
            j.avancarSemana();
        }
        Path dir = Files.createTempDirectory("futebas-kings-12");
        Path arq = dir.resolve("a.futebas.json.gz");
        j.salvar(arq);
        JsonObject o;
        try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(arq)), StandardCharsets.UTF_8)) {
            o = JsonParser.parseReader(r).getAsJsonObject();
        }
        // simula um save de antes: campos do jogador 13, sem versaoForca, força antiga e elenco de 15
        String timeInchado = null;
        for (JsonElement le : o.getAsJsonArray("kings")) {
            JsonObject l = le.getAsJsonObject();
            l.remove("versaoForca");
            JsonObject conv = new JsonObject();
            conv.addProperty("123", "kb-1");
            l.add("convidados13", conv);
            JsonArray cand = new JsonArray();
            cand.add(1);
            l.add("candidatos13", cand);
            l.getAsJsonObject("regras").addProperty("jogador13Liga", 3);
            l.getAsJsonObject("regras").addProperty("jogador13Playoff", 1);
            for (JsonElement te : l.getAsJsonArray("times")) {
                JsonObject t = te.getAsJsonObject();
                t.addProperty("usos13Liga", 2);
                t.addProperty("usos13Playoff", 0);
                t.addProperty("proximo13", 5);
                JsonArray el = t.getAsJsonArray("elenco");
                for (JsonElement ae : el) {
                    JsonObject a = ae.getAsJsonObject();
                    if (!a.has("presidente") || !a.get("presidente").getAsBoolean()) a.addProperty("forca", 50.0);
                }
                if (timeInchado == null && "kings-brasil".equals(l.get("id").getAsString()) && el.size() >= 4) {
                    timeInchado = t.get("id").getAsString();
                    int n = el.size();
                    for (int k = 0; el.size() < 15; k++) {
                        JsonObject c = el.get(k % n).getAsJsonObject().deepCopy();
                        c.remove("jogadorId");
                        c.addProperty("nome", "Extra " + k);
                        el.add(c);
                    }
                    JsonObject treze = el.get(0).getAsJsonObject().deepCopy();
                    treze.remove("jogadorId");
                    treze.addProperty("nome", "Convidado");
                    treze.addProperty("tipo", "13");
                    el.add(treze);
                }
            }
        }
        assertNotNull(timeInchado, "a Kings League Brasil já tem elencos na semana 4");
        Path velho = dir.resolve("velho.futebas.json.gz");
        try (Writer w = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(velho)), StandardCharsets.UTF_8)) {
            w.write(o.toString());
        }
        Jogo c = Jogo.carregar(velho, Fixture.banco());
        conferir(c, "save antigo");
        LigaKings br = c.ligasKings().stream().filter(l -> l.id.equals("kings-brasil")).findFirst().orElseThrow();
        assertEquals(Kings.VERSAO_FORCA, br.versaoForca);
        LigaKings.TimeKings t = br.time(timeInchado);
        assertEquals(Kings.MAX_ELENCO, t.elenco.size(), "save antigo com 16 fica com 12");
        for (LigaKings.AtletaKings a : t.elenco) if (!a.presidente) assertEquals(62, a.forca, 1e-9, "50 da escala antiga vira 62");
        // carregar de novo não reescala (versão gravada)
        Path de_novo = dir.resolve("b.futebas.json.gz");
        c.salvar(de_novo);
        Jogo d = Jogo.carregar(de_novo, Fixture.banco());
        LigaKings br2 = d.ligasKings().stream().filter(l -> l.id.equals("kings-brasil")).findFirst().orElseThrow();
        for (LigaKings.AtletaKings a : br2.time(timeInchado).elenco) if (!a.presidente) assertEquals(62, a.forca, 1e-9);
        for (int i = 0; i < 3; i++) {
            if (d.meuClube() != null) d.meuClube().confiancaDiretoria = 90;
            d.avancarSemana();
        }
        conferir(d, "depois de carregar");
    }
}
