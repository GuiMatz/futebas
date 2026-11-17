package br.futebas.ferramentas;

import br.futebas.Jogo;
import br.futebas.dados.BancoMundo;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.StatusJogador;

/** Proposta do humano a jogadores sem contrato (livres). */
public final class SondaLivre {
    public static void main(String[] args) throws Exception {
        BancoMundo db = BancoMundo.carregar(LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite"));
        Jogo j = Jogo.novoJogo(db, 7, "internacional", "Teste");
        int n = 0;
        for (Jogador x : j.mundo().jogadores) {
            if (x.status != StatusJogador.LIVRE || x.amador()) continue;
            long preco = j.precoPedido(x.id);
            var r = j.proporCompra(x.id, preco, 12);
            var p = j.negociacaoPor(x.id);
            String c = p == null ? "-" : j.proporContrato(p.id, j.salarioPedido(x.id), 12);
            String f = p != null && "aguardando_confirmacao".equals(p.estado) ? j.confirmarNegocio(p.id) : "(" + (p == null ? "" : p.estado) + ")";
            System.out.println(x.nomeExibicao() + " -> " + r.mensagem() + " | contrato: " + c + " | " + f + " | clube agora " + x.clubeId);
            if (++n >= 8) break;
        }
        j.avancarSemana();
        for (var p : j.propostasEnviadas()) System.out.println("  " + j.mundo().jogador(p.jogadorId).nomeExibicao() + " " + p.estado + " | " + p.mensagem);
    }
}
