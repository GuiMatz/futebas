package br.futebas.mundo;

import br.futebas.Fixture;
import br.futebas.historia.Historia;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DeterminismoTest {

    static String impressao(Mundo m) {
        StringBuilder sb = new StringBuilder();
        for (Historia.Titulo t : m.historia.titulos) sb.append(t.ano).append(t.competicaoId).append(t.campeaoId).append(t.golsArtilheiro).append(';');
        double soma = 0;
        long dinheiro = 0;
        for (Jogador j : m.jogadores) {
            soma += j.forca * (j.id + 1) + j.atributos[3] + (j.contrato == null ? 0 : j.contrato.salario) + j.status.ordinal() + (j.clubeId == null ? 0 : j.clubeId.hashCode());
            for (Passagem p : j.carreira) soma += p.ano * 7 + p.gols + p.jogos + p.notaMedia + (p.clubeId == null ? 0 : p.clubeId.length());
        }
        for (Arquivado x : m.arquivados) soma += x.id + x.golsCarreira + x.carreira().size();
        for (Clube c : m.clubes.values()) dinheiro += c.dinheiro;
        return sb + "|" + soma + "|" + dinheiro + "|" + m.jogadores.size() + "|" + m.rng.estado;
    }

    @Test
    void mesmaSementeMesmoMundo() {
        Simulador a = Simulador.novo(Fixture.banco(), 123, "bra-1-01", "Teste");
        Simulador b = Simulador.novo(Fixture.banco(), 123, "bra-1-01", "Teste");
        for (int i = 0; i < 3; i++) {
            a.avancarTemporada();
            b.avancarTemporada();
        }
        assertEquals(impressao(a.m), impressao(b.m));
        Simulador c = Simulador.novo(Fixture.banco(), 124, "bra-1-01", "Teste");
        for (int i = 0; i < 3; i++) c.avancarTemporada();
        assertNotEquals(impressao(a.m), impressao(c.m), "outra semente, outro mundo");
    }

    @Test
    void salvarECarregarNoMeioDaTemporadaNaoMudaOFuturo() throws Exception {
        Simulador a = Simulador.novo(Fixture.banco(), 55, "bra-1-03", "Teste");
        for (int i = 0; i < 20; i++) a.avancarSemana();
        Path arq = Files.createTempDirectory("futebas-save").resolve("t" + Salvamento.EXTENSAO);
        Salvamento.salvar(a.m, arq);
        Simulador b = Simulador.retomar(Salvamento.carregar(arq), Fixture.banco());
        assertEquals(impressao(a.m), impressao(b.m));
        for (int i = 0; i < 60; i++) {
            a.avancarSemana();
            b.avancarSemana();
        }
        assertEquals(impressao(a.m), impressao(b.m));
    }

    @Test
    void saveAntigoSemColunasCarrega() throws Exception {
        Simulador a = Simulador.novo(Fixture.banco(), 56, "bra-1-03", "Teste");
        for (int i = 0; i < 6; i++) a.avancarSemana();
        a.m.arredondarParaSalvar();
        // formato antigo: lista de objetos (sem Colunas)
        com.google.gson.Gson antigo = new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues()
                .registerTypeAdapterFactory(new SemPadroes(java.util.Set.of(Jogador.class, Passagem.class, Contrato.class, Arquivado.class))).create();
        Path arq = Files.createTempDirectory("futebas-save").resolve("antigo" + Salvamento.EXTENSAO);
        try (var w = new java.io.OutputStreamWriter(new java.util.zip.GZIPOutputStream(Files.newOutputStream(arq)), java.nio.charset.StandardCharsets.UTF_8)) {
            antigo.toJson(a.m, w);
        }
        Simulador b = Simulador.retomar(Salvamento.carregar(arq), Fixture.banco());
        assertEquals(impressao(a.m), impressao(b.m));
    }

    @Test
    void savePorColunasDevolveTudoIgual() throws Exception {
        Simulador a = Simulador.novo(Fixture.banco(), 57, "bra-1-03", "Teste");
        for (int i = 0; i < 3; i++) a.avancarSemana();
        Arquivado.Interner in = new Arquivado.Interner();
        int n = 0;
        for (Jogador j : a.m.jogadores) {
            if (j.carreira.isEmpty()) {
                j.carreira.add(new Passagem(2024, j.clubeId, "Nome Antigo", 30, 3, 2, 6.5, 60));
                j.carreira.add(new Passagem(2025, j.clubeId, a.m.clubes.containsKey(String.valueOf(j.clubeId)) ? a.m.clubes.get(j.clubeId).nome : "X", 31, 4, 1, 6.1, 61));
                j.carreira.add(new Passagem(2026, "outro", "Outro", 5, 0, 0, 5.9, 59));
            }
            if (n++ < 500) a.m.arquivados.add(Arquivado.de(j, in));
        }
        a.m.arredondarParaSalvar();
        com.google.gson.Gson simples = new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create();
        String jog = simples.toJson(a.m.jogadores), arq = simples.toJson(a.m.arquivados), tor = simples.toJson(a.m.torneios);
        Path f = Files.createTempDirectory("futebas-save").resolve("col" + Salvamento.EXTENSAO);
        Salvamento.salvar(a.m, f);
        assertEquals(jog, simples.toJson(a.m.jogadores), "salvar não muda o mundo em memória");
        Mundo b = Salvamento.carregar(f);
        assertEquals(jog, simples.toJson(b.jogadores));
        assertEquals(arq, simples.toJson(b.arquivados));
        assertEquals(tor, simples.toJson(b.torneios));
    }

    @Test
    void carreiraArquivadaResumidaPorPassagem() {
        Jogador j = new Jogador();
        j.forca = 60;
        j.carreira.add(new Passagem(2030, "a", "A", 10, 1, 0, 6.0, 60));
        j.carreira.add(new Passagem(2031, "a", "A", 20, 2, 1, 7.0, 62));
        j.carreira.add(new Passagem(2032, "b", "B", 5, 0, 0, 6.0, 61));
        Arquivado x = Arquivado.de(j, new Arquivado.Interner());
        var c = x.carreira();
        assertEquals(2, c.size());
        assertEquals(2030, c.get(0).ano);
        assertEquals(2031, c.get(0).anoFim);
        assertEquals(30, c.get(0).jogos);
        assertEquals(62, c.get(0).forca);
        assertEquals(62, x.forcaMaxima);
    }
}
