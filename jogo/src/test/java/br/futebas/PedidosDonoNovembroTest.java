package br.futebas;

import br.futebas.financas.Financas;
import br.futebas.ia.PropostaJogador;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.StatusJogador;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requisitos de 12/11/2026: ordem da aba Vagas, dívidas claras e pagamento escolhido, e uma
 * confirmação só para comprar e vender.
 */
class PedidosDonoNovembroTest {

    /** "Organize as vagas": propostas recebidas, depois as que dá para se candidatar e por último as acima do calibre. */
    @Test
    void vagasEmTresGrupos() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 71, "bra-1-01", "T");
        Clube meu = j.meuClube();
        // proposta recebida de um clube pequeno, para o grupo 0 existir
        Clube pequeno = null;
        for (Clube c : j.mundo().clubes.values()) if (!c.humano && c.existe && !c.virtual && c.divisao > 0 && (pequeno == null || c.reputacao < pequeno.reputacao)) pequeno = c;
        assertNotNull(pequeno);
        j.simulador().diretoria.proporAoHumano(pequeno);
        for (int k = 0; k < 30; k++) {
            meu.confiancaDiretoria = 90;
            j.avancarSemana();
        }
        j.simulador().diretoria.proporAoHumano(pequeno);
        List<Jogo.VagaEmprego> vs = j.vagasDeEmprego();
        assertFalse(vs.isEmpty(), "há vagas");
        int ultimo = -1;
        for (Jogo.VagaEmprego v : vs) {
            assertTrue(v.grupo() >= ultimo, "ordem dos grupos: " + vs);
            ultimo = v.grupo();
            if (v.proposta() != null) assertEquals(0, v.grupo());
            else if ("Fora do seu alcance".equals(v.interesse())) assertEquals(2, v.grupo());
            else assertEquals(1, v.grupo());
        }
        assertEquals(3, Jogo.GRUPOS_VAGAS.length);
    }

    /** "Deixe claro quanto deve, quando pagar; não gaste muito dinheiro; escolha quanto pagar." */
    @Test
    void dividaComParcelasEPagamentoEscolhido() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 72, "bra-1-01", "T");
        Clube c = j.meuClube();
        assertEquals(0, j.situacaoDivida().total());
        long pedido = Math.min(Financas.limiteEmprestimo(c, j.ano()), 12_000_000L);
        long caixaAntes = c.dinheiro;
        assertEquals(pedido, j.pedirEmprestimo(pedido));
        assertEquals(caixaAntes + pedido, c.dinheiro);
        Jogo.SituacaoDivida d = j.situacaoDivida();
        assertEquals(pedido, d.total());
        assertEquals(Financas.PRAZO_EMPRESTIMO_MESES, d.parcelasRestantes());
        assertEquals(Financas.PRAZO_EMPRESTIMO_MESES, d.cronograma().size());
        long soma = 0;
        int sem = j.mundo().semanaAbsoluta();
        for (Financas.Parcela p : d.cronograma()) {
            soma += p.amortizacao();
            assertTrue(p.semana() >= sem, "parcelas em datas futuras e crescentes");
            assertEquals(0, Calendario.semana(p.semana()) % 4, "vence no fechamento do mês");
            sem = p.semana() + 1;
            assertTrue(p.juros() >= 0);
        }
        assertEquals(pedido, soma, "as parcelas somam a dívida");
        assertEquals(0, d.cronograma().get(d.cronograma().size() - 1).saldoDepois());
        assertTrue(d.proxima().total() < pedido / 4, "uma parcela não leva o caixa de uma vez");
        // pagamento escolhido: nunca passa do caixa nem do total devido
        c.dinheiro = 1_000_000;
        assertEquals(1_000_000, j.maximoAmortizar());
        assertEquals(1_000_000, j.pagarEmprestimo(50_000_000));
        assertEquals(0, c.dinheiro);
        assertEquals(pedido - 1_000_000, c.divida);
        assertEquals(Financas.PRAZO_EMPRESTIMO_MESES, j.situacaoDivida().parcelasRestantes(), "pagar antes diminui as parcelas, não o prazo");
        c.dinheiro = 500_000_000;
        long devendo = c.divida;
        assertEquals(devendo, j.pagarEmprestimo(Long.MAX_VALUE), "no máximo o total devido");
        assertEquals(0, c.divida);
        assertEquals(0, j.situacaoDivida().parcelasRestantes());
        assertNull(j.situacaoDivida().proxima());
        // a parcela sai sozinha no fechamento do mês
        j.pedirEmprestimo(pedido);
        c.dinheiro = 500_000_000;
        int vence = j.situacaoDivida().proxima().semana();
        long amort = j.situacaoDivida().proxima().amortizacao();
        while (j.mundo().semanaAbsoluta() <= vence) j.avancarSemana();
        assertEquals(pedido - amort, c.divida, "a parcela do mês foi descontada");
        assertEquals(Financas.PRAZO_EMPRESTIMO_MESES - 1, j.situacaoDivida().parcelasRestantes());
    }

    /** "Ainda tem que confirmar 3 vezes para vender e comprar": a proposta com o salário já fecha o negócio. */
    @Test
    void compraComUmaConfirmacao() {
        int fechados = 0;
        for (int sem = 81; sem <= 86; sem++) {
            Jogo j = Jogo.novoJogo(Fixture.banco(), sem, "bra-1-01", "T");
            Clube meu = j.meuClube();
            meu.dinheiro = 5_000_000_000L;
            Jogador alvo = null;
            for (Jogador x : j.elenco("bra-2-03")) if (x.status == StatusJogador.PROFISSIONAL && (alvo == null || x.forca > alvo.forca)) alvo = x;
            long salario = Financas.salarioPedido(alvo, meu, j.ano()) * 2;
            assertTrue(j.proporCompra(alvo.id, j.precoPedido(alvo.id) * 2, 24, salario).aceito());
            PropostaJogador p = j.negociacaoPor(alvo.id);
            for (int k = 0; k < 3 && "aguardando".equals(p.estado); k++) j.avancarSemana();
            if ("expirada".equals(p.estado) || "recusada".equals(p.estado)) continue; // não respondeu / recusou
            assertTrue("concluida".equals(p.estado) || "aguardando_janela".equals(p.estado),
                    "clube e jogador aceitaram: fecha sem outra confirmação (" + p.estado + ": " + p.mensagem + ")");
            if ("concluida".equals(p.estado)) {
                assertEquals(meu.id, alvo.clubeId);
                assertEquals(salario, alvo.contrato.salario);
            }
            fechados++;
        }
        assertTrue(fechados > 0, "ao menos um negócio respondido");
    }
}
