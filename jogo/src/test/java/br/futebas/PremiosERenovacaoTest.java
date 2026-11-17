package br.futebas;

import br.futebas.historia.Historia;
import br.futebas.mundo.Jogador;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Bola de Ouro (Top 50 e jovens), seleção da liga, renovação automática e passe livre. */
class PremiosERenovacaoTest {

    @Test
    void premiosDoAnoERenovacao() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 77, "bra-1-01", "Técnico Teste");
        // renovação automática: um titular com contrato vencendo fica no clube
        j.definirRenovacaoAutomatica(true, 0);
        assertTrue(j.renovacaoAutomatica());
        Jogador alvo = null;
        for (Jogador x : j.elenco(j.meuClube().id)) if (x.contrato != null && !x.contrato.teste() && (alvo == null || x.forca > alvo.forca)) alvo = x;
        assertNotNull(alvo);
        alvo.contrato.fim = j.mundo().semanaAbsoluta() - 1;
        j.avancarSemana();
        assertEquals(j.meuClube().id, alvo.clubeId, "renovado sozinho");
        assertTrue(alvo.contrato.fim > j.mundo().semanaAbsoluta());
        // sem renovação: sai livre
        j.definirRenovacaoAutomatica(false, 0);
        Jogador outro = null;
        for (Jogador x : j.elenco(j.meuClube().id)) if (x != alvo && x.contrato != null && !x.contrato.teste()) { outro = x; break; }
        assertNotNull(outro);
        outro.contrato.fim = j.mundo().semanaAbsoluta() - 1;
        j.avancarSemana();
        assertNotEquals(j.meuClube().id, outro.clubeId, "sem renovação, sai ao fim do contrato");
        assertFalse(j.jogadoresLivres(500).isEmpty());

        j.avancarAteFimDaTemporada();
        List<Historia.ItemRanking> bola = j.bolaDeOuro(2026);
        assertTrue(bola.size() >= 20 && bola.size() <= 50, "Top 50: " + bola.size());
        assertEquals(1, bola.get(0).posicao);
        for (int i = 1; i < bola.size(); i++) assertTrue(bola.get(i - 1).valor >= bola.get(i).valor);
        for (Historia.ItemRanking it : j.bolaDeOuroJovens(2026)) assertTrue(it.idade <= 21);
        assertFalse(j.bolaDeOuroJovens(2026).isEmpty());
        Historia.SelecaoDaLiga s = j.selecaoDaLiga("bra-1", 2026);
        assertNotNull(s, "seleção da Série A");
        assertEquals(11, s.time.size());
        Set<Integer> ids = new HashSet<>();
        for (Historia.ItemSelecao it : s.time) assertTrue(ids.add(it.jogadorId));
        assertEquals("GOL", s.time.get(0).posicao);
        assertFalse(j.selecoesDaLiga(2026).isEmpty());
    }
}
