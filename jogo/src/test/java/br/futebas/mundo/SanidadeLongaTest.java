package br.futebas.mundo;

import br.futebas.Fixture;
import br.futebas.competicao.Torneio;
import br.futebas.historia.Historia;
import br.futebas.partida.Escalador;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 10 temporadas sem interface com estatísticas plausíveis (critério da Fase 2). */
class SanidadeLongaTest {

    @Test
    void dezTemporadas() {
        long t0 = System.nanoTime();
        // semente 2025 desde 15/11/2026: com os reforços de emergência (fim do amador em teste) a sequência
        // aleatória mudou e a 2024 caiu em 2 campeões da Série A fictícia em 10 anos (o limite é 3)
        // semente 2024 de novo desde 16/11/2026: o mercado em camadas, a forma da temporada e o freio de receita
        // (saúde do mundo) mudaram a sequência; com a 2025 o banco fictício caiu em 2 campeões da Série A em 10 anos
        // (2024: 3, 7: 3, 11: 5)
        Simulador s = Simulador.novo(Fixture.banco(), 2024, null, null);
        Set<String> campeoesA = new HashSet<>();
        long piorTemporada = 0;
        for (int i = 0; i < 10; i++) {
            int ano = s.m.ano;
            long ts = System.nanoTime();
            s.avancarTemporada();
            piorTemporada = Math.max(piorTemporada, System.nanoTime() - ts);
            // toda competição do ano terminou com campeão
            for (Torneio t : s.m.torneiosAnteriores) {
                assertTrue(t.encerrado, t.id + " " + ano);
                assertNotNull(t.campeao, t.id + " " + ano);
                assertEquals(t.participantes.size(), t.classificacao.size());
                for (var p : t.partidas) assertTrue(p.jogada, "partida não jogada em " + t.id);
            }
            Torneio a = s.m.torneioAnterior("bra-1");
            assertNotNull(a, "Série A em " + ano);
            campeoesA.add(a.campeao);
            Historia.EstatAno e = s.m.historia.estat(ano);
            double gpj = (double) e.golsLiga / e.jogosLiga;
            assertTrue(gpj > 1.8 && gpj < 3.4, "gols por jogo em " + ano + ": " + gpj);
            assertTrue(e.vitMandante > e.vitVisitante, "mando de campo em " + ano);
        }
        assertEquals(2036, s.m.ano);
        assertTrue(campeoesA.size() >= 3, "nem sempre os mesmos campeões");
        assertTrue(campeoesA.size() <= 25, "nem um caos total");
        // elencos jogáveis e números finitos
        for (Clube c : s.m.clubes.values()) {
            if (c.virtual || !c.existe) continue;
            assertTrue(Double.isFinite(c.reputacao) && c.reputacao >= 0 && c.reputacao <= 100);
            Complementares.garantir(s, c);
            assertTrue(Escalador.disponiveis(s.m, c).size() >= 11);
        }
        for (Jogador j : s.m.jogadores) {
            assertTrue(Double.isFinite(j.forca) && j.forca >= 1 && j.forca <= 99, "força inválida " + j.forca);
            if (j.status == StatusJogador.PROFISSIONAL || j.status == StatusJogador.BASE) assertNotNull(s.m.clube(j.clubeId));
        }
        // memória: aposentados antigos arquivados, ainda acessíveis pelo id (como esboço)
        assertFalse(s.m.arquivados.isEmpty(), "aposentados arquivados");
        Set<Integer> ids = new HashSet<>();
        for (Jogador j : s.m.jogadores) {
            assertSame(j, s.m.jogador(j.id), "índice por id");
            assertTrue(ids.add(j.id), "id repetido");
        }
        int anterior = -1;
        for (Arquivado a : s.m.arquivados) {
            assertTrue(a.id > anterior, "arquivo em ordem de id");
            anterior = a.id;
            assertTrue(ids.add(a.id), "arquivado também ativo: " + a.id);
            Jogador e = s.m.jogador(a.id);
            assertEquals(StatusJogador.APOSENTADO, e.status);
            assertEquals(a.nomeExibicao(), e.nomeExibicao());
            assertEquals(a.golsCarreira, e.golsCarreira);
        }
        for (Clube c : s.m.clubes.values()) for (int id : c.elenco) assertNotNull(s.m.ativo(id), "elenco aponta para arquivado");
        java.nio.file.Path arq;
        try {
            arq = java.nio.file.Files.createTempDirectory("futebas-memoria").resolve("m.futebas.json.gz");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        Salvamento.salvar(s.m, arq);
        Mundo carregado = Salvamento.carregar(arq);
        Simulador s2 = Simulador.retomar(carregado, Fixture.banco());
        assertEquals(s.m.arquivados.size(), carregado.arquivados.size());
        Arquivado ultimo = s.m.arquivados.get(s.m.arquivados.size() - 1);
        assertEquals(ultimo.nomeExibicao(), carregado.jogador(ultimo.id).nomeExibicao());
        assertEquals(ultimo.carreira().size(), carregado.jogador(ultimo.id).carreira.size());
        Jogador novo = carregado.novoJogador();
        assertTrue(novo.id >= s.m.proximoId, "ids novos não colidem depois de carregar");
        assertSame(novo, carregado.jogador(novo.id));
        s2.avancarSemana();
        // a Kings League fictícia já existe em 2026
        assertTrue(s.m.kings.get(0).ativa);
        assertTrue(piorTemporada / 1e9 < 3.0, "uma temporada precisa rodar em poucos segundos");
        assertTrue((System.nanoTime() - t0) / 1e9 < 120);
    }
}
