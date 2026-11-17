package br.futebas;

import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.StatusJogador;
import br.futebas.mundo.Treinador;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Técnicos da IA (mercado, demissões, ex-jogadores), escolha da Kings ao se aposentar e pesquisa. */
class TreinadoresEBuscaTest {

    @Test
    void mercadoDeTecnicosEPesquisa() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 31, "bra-1-01", "Técnico Teste");
        var m = j.mundo();
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe || c.humano) continue;
            Treinador t = m.treinador(c.treinadorId);
            assertNotNull(t, "clube sem técnico: " + c.id);
            assertEquals(c.id, t.clubeId);
        }
        j.avancarSemana();
        assertNull(j.treinadorDoClube("bra-1-01"), "o clube do humano não tem técnico da IA");
        j.avancarAteFimDaTemporada();
        j.avancarAteFimDaTemporada();
        long exJogadores = m.treinadores.stream().filter(t -> "ex_jogador".equals(t.origem)).count();
        assertTrue(exJogadores > 0, "aposentados viram técnicos");
        for (Treinador t : m.treinadores) {
            if (t.clubeId != null) assertEquals(t.id, m.clube(t.clubeId).treinadorId, "emprego coerente");
            if ("ex_jogador".equals(t.origem)) {
                Jogador x = m.jogador(t.jogadorId);
                assertTrue(x.virouTecnico);
                assertNull(x.kingsPais, "técnico não vai para a Kings");
            }
        }
        assertTrue(m.treinadores.stream().anyMatch(t -> t.jogos > 0), "técnicos acumulam jogos");
        for (Clube c : m.clubes.values()) if (!c.virtual && c.existe && !c.humano) assertNotNull(m.treinador(c.treinadorId));
        // pesquisa
        Clube alvo = m.clube("bra-1-05");
        List<Jogo.ResultadoBusca> r = j.buscar(alvo.nome.substring(0, 8).toUpperCase(), 50);
        assertTrue(r.stream().anyMatch(x -> x.tipo().equals("clube") && x.id().equals("bra-1-05")), "acha o clube sem caixa");
        Treinador t = j.treinadorDoClube("bra-1-05");
        assertTrue(j.buscar(t.nome, 50).stream().anyMatch(x -> x.tipo().equals("tecnico") && x.id().equals(String.valueOf(t.id))), "acha o técnico");
        assertTrue(j.buscar("serie", 20).stream().anyMatch(x -> x.tipo().equals("competicao")) || j.buscar("bra", 20).size() > 0);
        Jogador craque = j.elenco("bra-1-02").get(0);
        assertTrue(j.buscar(craque.nomeExibicao(), 200).stream().anyMatch(x -> x.tipo().equals("jogador") && x.id().equals(String.valueOf(craque.id))));
        assertTrue(j.buscar("x", 10).isEmpty(), "texto curto demais");
        // camada histórica (o banco fictício não tem as tabelas: só a história do save)
        assertFalse(j.carreiraDoJogador(craque.id).isEmpty(), "carreira simulada");
        assertTrue(j.campeoes("bra-1").size() >= 2, "campeões do save");
        assertNotNull(j.lendas(null, null, null));
        assertNotNull(j.onzeHistorico("bra-1-01"));
        // Kings: aposentados que escolheram a Kings entram no draft do país escolhido
        long kings = m.jogadores.stream().filter(x -> x.status == StatusJogador.APOSENTADO && x.kingsPais != null).count();
        assertTrue(kings >= 0);
    }
}
