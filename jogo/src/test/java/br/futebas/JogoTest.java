package br.futebas;

import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.StatusJogador;
import br.futebas.partida.Estilo;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A fachada usada pela interface: uma temporada de técnico do começo ao fim. */
class JogoTest {

    @Test
    void temporadaDoTecnico() throws Exception {
        assertFalse(Jogo.clubesDisponiveis(Fixture.banco()).isEmpty());
        assertFalse(Jogo.paises(Fixture.banco()).isEmpty());
        assertEquals("BRA", Jogo.paises(Fixture.banco()).get(0).id());
        assertEquals("bra-1", Jogo.ligas(Fixture.banco(), "BRA").get(0).id());
        assertEquals(20, Jogo.clubes(Fixture.banco(), "bra-1").size());
        Jogo j = Jogo.novoJogo(Fixture.banco(), 99, "bra-1-01", "Técnico Teste");
        assertEquals("bra-1-01", j.meuClube().id);
        assertFalse(j.caixaDeEntrada().isEmpty());
        j.definirTatica("4-3-3", Estilo.PRESSAO_ALTA);
        List<Partida> proximas = j.proximasPartidas(j.meuClube().id, 3);
        assertFalse(proximas.isEmpty());
        // escalação manual com os 11 melhores disponíveis
        j.avancarSemana();
        List<Integer> onze = new ArrayList<>();
        for (Jogador x : j.elenco(j.meuClube().id)) if (x.disponivel() && onze.size() < 11) onze.add(x.id);
        if (onze.size() == 11) assertTrue(j.definirEscalacao(onze, List.of()));
        Jogo.ResumoSemana r = j.avancarAteProximoJogo();
        assertFalse(r.jogosDoUsuario().isEmpty());
        assertFalse(r.narradas().isEmpty(), "jogo do usuário é narrado");
        assertNotNull(j.ultimaPartidaNarrada());
        // mercado: contratar um jogador livre
        List<Jogador> livres = new ArrayList<>();
        for (Jogador x : j.mundo().jogadores) if (x.status == StatusJogador.LIVRE && !x.amador() && x.kingsTimeId == null && x.idade(j.ano()) < 33) livres.add(x);
        if (!livres.isEmpty()) {
            Jogo.ResultadoNegociacao n = j.proporCompra(livres.get(0).id, 0, 12);
            assertTrue(n.aceito(), n.mensagem());
            // regras do jogo: contrato com o jogador e confirmação final, nada automático
            var p = j.negociacaoPor(livres.get(0).id);
            assertEquals("aceita", p.estado, "livre vai direto ao contrato");
            assertNotEquals(j.meuClube().id, livres.get(0).clubeId);
            j.proporContrato(p.id, br.futebas.financas.Financas.salarioPedido(livres.get(0), j.meuClube(), j.ano()) * 2, 12);
            assertEquals("aguardando_confirmacao", p.estado);
            j.confirmarNegocio(p.id);
            assertEquals("concluida", p.estado);
            assertEquals(j.meuClube().id, livres.get(0).clubeId);
        }
        j.avancarAteFimDaTemporada();
        assertEquals(2027, j.ano());
        List<LinhaTabela> tab = j.tabela("bra-1");
        assertFalse(tab.isEmpty());
        assertNotNull(j.financas(j.meuClube() == null ? "bra-1-01" : j.meuClube().id));
        assertFalse(j.historia().titulos.isEmpty());

        // salvar e carregar
        Path arq = Files.createTempDirectory("futebas-jogo").resolve("x.futebas.json.gz");
        j.salvar(arq);
        Jogo c = Jogo.carregar(arq, Fixture.banco());
        assertEquals(j.ano(), c.ano());
        assertEquals(j.mundo().jogadores.size(), c.mundo().jogadores.size());
        c.avancarSemana();
    }

    @Test
    void soAssistir() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 5, null, null);
        assertTrue(j.soAssistindo());
        j.avancarAteFimDaTemporada();
        assertEquals(2027, j.ano());
        assertTrue(j.historia().titulos.stream().anyMatch(t -> t.ano == 2026 && t.competicaoId.equals("bra-3")));
        assertTrue(j.mundo().tecnico == null);
        assertEquals(Mundo.ANO_PRESENTE, 2026);
    }
}
