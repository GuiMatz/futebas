package br.futebas.ia;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.StatusJogador;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Regras do jogo: negociação assíncrona, contrato com o jogador e confirmação final; listas. */
class NegociacoesTest {

    @Test
    void compraAssincronaComConfirmacao() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 61, "bra-1-01", "T");
        Clube meu = j.meuClube();
        meu.dinheiro = 5_000_000_000L;
        Jogador alvo = null;
        for (Jogador x : j.elenco("bra-2-03")) if (x.status == StatusJogador.PROFISSIONAL && (alvo == null || x.forca > alvo.forca)) alvo = x;
        long pedido = j.precoPedido(alvo.id);
        assertTrue(j.proporCompra(alvo.id, pedido * 2, 24).aceito());
        PropostaJogador p = j.negociacaoPor(alvo.id);
        assertEquals("aguardando", p.estado, "nada acontece na hora");
        assertEquals("bra-2-03", alvo.clubeId);
        assertTrue(j.propostasEnviadas().contains(p));
        assertNotNull(j.prazoProposta(p.id));
        for (int k = 0; k < 2 && "aguardando".equals(p.estado); k++) j.avancarSemana();
        if ("expirada".equals(p.estado)) return; // o clube não respondeu (10%)
        assertEquals("aceita", p.estado, p.mensagem);
        String resp = j.proporContrato(p.id, 1, 24);
        assertNotEquals("aguardando_confirmacao", p.estado, resp);
        j.proporContrato(p.id, br.futebas.financas.Financas.salarioPedido(alvo, meu, j.ano()) * 2, 24);
        if (!"aguardando_confirmacao".equals(p.estado)) return; // o jogador desistiu depois da proposta baixa
        assertEquals("bra-2-03", alvo.clubeId, "só depois da confirmação");
        j.confirmarNegocio(p.id);
        while ("aguardando_janela".equals(p.estado) && j.ano() == 2026) j.avancarSemana();
        assertEquals("concluida", p.estado);
        assertEquals(meu.id, alvo.clubeId);
    }

    /** Regra do jogo: acerto fora da janela só se muda na próxima janela; até lá, ele joga pelo clube atual. */
    @Test
    void acertoForaDaJanelaEsperaAJanela() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 63, "bra-1-01", "T");
        Clube meu = j.meuClube();
        while (j.semana() <= 12) {
            meu.confiancaDiretoria = 90;
            j.avancarSemana();
        }
        assertFalse(j.janelaAberta(), "semana " + j.semana() + " fora da janela");
        meu.dinheiro = 5_000_000_000L;
        Jogador alvo = null;
        for (Jogador x : j.elenco("bra-2-03")) if (x.status == StatusJogador.PROFISSIONAL && (alvo == null || x.forca > alvo.forca)) alvo = x;
        assertTrue(j.proporCompra(alvo.id, j.precoPedido(alvo.id) * 3, 24).aceito());
        PropostaJogador p = j.negociacaoPor(alvo.id);
        for (int k = 0; k < 3 && "aguardando".equals(p.estado); k++) j.avancarSemana();
        if (!"aceita".equals(p.estado)) return; // não respondeu (10%)
        j.proporContrato(p.id, br.futebas.financas.Financas.salarioPedido(alvo, meu, j.ano()) * 3, 24);
        if (!"aguardando_confirmacao".equals(p.estado)) return;
        String r = j.confirmarNegocio(p.id);
        assertEquals("aguardando_janela", p.estado);
        assertTrue(r.startsWith("Acerto concluído: " + alvo.nomeExibicao() + " chega na abertura da janela ("), r);
        assertNotNull(p.chegada());
        assertEquals(java.time.LocalDate.of(2026, 7, 2), p.chegada(), "1º dia da janela de julho (semana 27)");
        assertEquals(p.chegada(), j.chegadaPrevista(alvo.id));
        assertTrue(j.transferenciasPendentes().contains(p));
        assertTrue(p.situacao().contains("02/07/2026"), p.situacao());
        assertTrue(j.caixaDeEntrada().stream().anyMatch(x -> "Acerto concluído".equals(x.titulo)));
        int jogosAntes = alvo.jogosTemp;
        while (j.semana() < 27 && j.ano() == 2026) {
            meu.confiancaDiretoria = 90;
            j.avancarSemana();
            if (j.semana() < 27) assertEquals("bra-2-03", alvo.clubeId, "continua no clube até a janela");
        }
        assertTrue(alvo.jogosTemp > jogosAntes, "jogou pelo clube atual enquanto esperava");
        j.avancarSemana();
        assertEquals("concluida", p.estado);
        assertEquals(meu.id, alvo.clubeId);
        assertNull(j.chegadaPrevista(alvo.id));
    }

    /** A IA segue a mesma regra (leilão fora da janela do comprador). */
    @Test
    void iaForaDaJanelaTambemEspera() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 64, "bra-1-01", "T");
        while (j.semana() <= 12) j.avancarSemana();
        var sim = j.simulador();
        Clube vendedor = j.mundo().clube("bra-2-03"), comprador = j.mundo().clube("bra-1-02");
        Jogador x = j.elenco("bra-2-03").get(0);
        PropostaJogador p = sim.negociacoes.agendarIA(x, vendedor, comprador, 1_000_000, "compra");
        assertEquals("aguardando_janela", p.estado);
        assertTrue(sim.negociacoes.comprometido(x.id));
        assertTrue(j.transferenciasPendentesDoMundo().contains(p));
        assertFalse(j.proporCompra(x.id, 50_000_000, 24).aceito(), "já acertado com outro clube");
        while (j.semana() < 27) {
            j.avancarSemana();
            if (j.semana() < 27) assertEquals("bra-2-03", x.clubeId);
        }
        j.avancarSemana();
        assertEquals("concluida", p.estado);
        assertNotEquals("bra-2-03", x.clubeId, "mudou-se na abertura da janela (o comprador pode já ter repassado)");
    }

    @Test
    void listasNaoExecutamNada() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 62, "bra-1-02", "T");
        Jogador x = j.elenco("bra-1-02").get(5);
        assertTrue(j.emprestar(x.id).aceito());
        assertEquals("bra-1-02", x.clubeId, "emprestar só põe na lista");
        assertTrue(j.listaEmprestimos().contains(x));
        j.colocarAVenda(x.id, 1_000_000);
        assertTrue(j.listaTransferencias().contains(x));
        j.tirarDasListas(x.id);
        assertTrue(j.listaEmprestimos().isEmpty() && j.listaTransferencias().isEmpty());
    }
}
