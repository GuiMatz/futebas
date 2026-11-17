package br.futebas;

import br.futebas.competicao.Torneio;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.StatusJogador;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Torneio amistoso (quadrangular) e jogo de despedida. */
class AmistososTest {

    @Test
    void quadrangularEDespedida() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 55, "bra-1-01", "Técnico Teste");
        Torneio q = j.marcarTorneioAmistoso("Copa de Verão", List.of("bra-1-02", "bra-1-03", "bra-2-01"));
        assertNotNull(q);
        assertEquals(4, q.participantes.size());
        assertEquals(6, q.partidas.size(), "turno único entre 4");
        assertNull(j.marcarTorneioAmistoso("x", List.of("bra-1-02")), "precisa de 3 ou 4 clubes");
        Jogador veterano = null;
        for (Jogador x : j.elenco("bra-1-01")) if (x.idade(j.ano()) >= 30 && (veterano == null || x.forca > veterano.forca)) veterano = x;
        assertNotNull(veterano, "o fictício tem veteranos");
        Torneio d = j.marcarJogoDeDespedida(veterano.id, "bra-1-04");
        assertNotNull(d);
        for (int k = 0; k < 40 && !(q.encerrado && d.encerrado); k++) j.avancarSemana();
        assertTrue(q.encerrado && d.encerrado, "q " + q.encerrado + " " + q.semanasJogo + " d " + d.encerrado + " " + d.semanasJogo + " agora " + j.mundo().semanaAbsoluta() + " datas " + q.totalDatas + " prox " + q.proximaData);
        assertTrue(j.historia().titulos.stream().anyMatch(t -> t.competicaoId.equals(q.id)), "campeão do amistoso na história");
        assertEquals(StatusJogador.APOSENTADO, j.jogador(veterano.id).status, "se aposenta depois da despedida");
        assertFalse(j.despedidas().isEmpty());
        assertEquals(veterano.id, j.despedidas().get(0).jogadorId.intValue());
        int vid = veterano.id;
        assertTrue(d.partidas.get(0).jogada);
        assertTrue(j.historia().linhaDoTempo.stream().anyMatch(e -> "despedida".equals(e.tipo)));
        assertNotNull(vid);
    }
}
