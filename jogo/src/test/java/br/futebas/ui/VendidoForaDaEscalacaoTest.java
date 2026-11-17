package br.futebas.ui;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.ia.PropostaJogador;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.partida.*;
import br.futebas.ui.modelo.ConferirEscalacao;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug corrigido: "vendi jogadores mas eles ainda aparecem na escalação no jogo; substituí o Ronaldo
 * que acabei de vender". Vendido/emprestado fora da janela (aguardando_janela) não joga mais pelo
 * clube do técnico; vendido na janela (já saiu) também não pode ficar na escalação salva.
 */
class VendidoForaDaEscalacaoTest {

    private static List<Integer> ids(List<Escalado> l) {
        List<Integer> r = new ArrayList<>();
        for (Escalado e : l) r.add(e.id);
        return r;
    }

    @Test
    void negociadoEsperandoAJanelaSaiDaEscalacaoDoBancoEDoJogo() {
        Jogo jogo = Jogo.novoJogo(Fixture.banco(), 41, "bra-1-01", "T");
        Mundo m = jogo.mundo();
        Clube c = jogo.meuClube();
        TimePartida antes = Escalador.montar(m, c, 5, 12);
        // escalação manual salva com os 11 de hoje e o banco
        jogo.definirEscalacao(ids(antes.titulares), ids(antes.reservas));
        int craque = antes.titulares.get(10).id, reserva = antes.reservas.get(0).id;

        for (int id : new int[] {craque, reserva}) {
            PropostaJogador p = new PropostaJogador();
            p.jogadorId = id;
            p.vendedorId = c.id;
            p.compradorId = "eng-1-01";
            p.estado = "aguardando_janela";
            m.propostas.add(p);
        }
        assertEquals(java.util.Set.of(craque, reserva), Escalador.deSaida(m, c));
        assertTrue(Escalador.disponiveis(m, c).stream().noneMatch(j -> j.id == craque || j.id == reserva), "fora dos disponíveis");

        // o time do jogo (titulares e banco: é o que aparece na troca ao vivo) não tem os negociados,
        // e os outros 10 da escalação manual continuam (o auxiliar completa só a vaga)
        TimePartida tp = Escalador.montar(m, c, 5, 12);
        assertFalse(ids(tp.titulares).contains(craque));
        assertFalse(ids(tp.reservas).contains(craque) || ids(tp.reservas).contains(reserva));
        for (int i = 0; i < 10; i++) assertEquals(antes.titulares.get(i).id, tp.titulares.get(i).id, "vaga " + i + " mantida");

        // o aviso de desfalques limpa a escalação salva e avisa
        List<String> problemas = ConferirEscalacao.problemas(m, c);
        assertTrue(problemas.stream().anyMatch(x -> x.contains("aguarda a janela")), "avisa o negociado: " + problemas);
        assertEquals(-1, (int) c.tatica.titulares.get(10), "a vaga fica vazia");
        assertFalse(c.tatica.reservas.contains(reserva));

        // e o motor nunca o põe em campo
        OpcoesPartida op = new OpcoesPartida();
        TimePartida adv = Escalador.montarAutomatico(m, m.clube("bra-1-02"), 5);
        ResultadoPartida r = MotorPartida.simular(tp, adv, op, new br.futebas.mundo.Rng(3));
        assertFalse(r.minutos.containsKey(craque) || r.minutos.containsKey(reserva));
    }

    @Test
    void vendidoNaJanelaNaoFicaNaEscalacaoSalva() {
        Jogo jogo = Jogo.novoJogo(Fixture.banco(), 42, "bra-1-01", "T");
        Mundo m = jogo.mundo();
        Clube c = jogo.meuClube();
        TimePartida antes = Escalador.montar(m, c, 5, 12);
        jogo.definirEscalacao(ids(antes.titulares), ids(antes.reservas));
        Jogador vendido = m.jogador(antes.titulares.get(5).id);
        // negócio concluído na janela: o jogador já é do outro clube (a escalação salva ficou com o id velho)
        c.elenco.remove((Integer) vendido.id);
        vendido.clubeId = "eng-1-01";
        TimePartida tp = Escalador.montar(m, c, 5, 12);
        assertFalse(ids(tp.titulares).contains(vendido.id));
        assertFalse(ids(tp.reservas).contains(vendido.id));
        List<String> tirados = Escalador.limparEscalacao(m, c);
        assertEquals(1, tirados.size(), tirados.toString());
        assertEquals(-1, (int) c.tatica.titulares.get(5));
    }
}
