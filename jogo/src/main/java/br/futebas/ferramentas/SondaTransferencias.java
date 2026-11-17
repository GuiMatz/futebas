package br.futebas.ferramentas;

import br.futebas.Jogo;
import br.futebas.dados.BancoMundo;
import br.futebas.ia.PropostaJogador;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;

/** Reproduz compra e venda do técnico humano sem interface, semana a semana. uso: SondaTransferencias [clube] */
public final class SondaTransferencias {
    public static void main(String[] args) throws Exception {
        String clube = args.length > 0 ? args[0] : "internacional";
        BancoMundo db = BancoMundo.carregar(LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite"));
        Jogo j = Jogo.novoJogo(db, 7, clube, "Teste");
        Clube meu = j.meuClube();
        System.out.println("data " + j.mundo().semanaAbsoluta() + " janela aberta: " + j.janelaAberta() + " caixa " + meu.dinheiro);
        // compra: um jogador de outro clube brasileiro, força 70-74
        Jogador alvo = null;
        for (Jogador x : j.mundo().jogadores) {
            Clube c = j.mundo().clube(x.clubeId);
            if (c != null && !c.humano && "BRA".equals(c.pais) && c.divisao == 1 && x.forca >= 70 && x.forca <= 74 && x.kingsTimeId == null) { alvo = x; break; }
        }
        long valor = Math.round(j.valorDeMercado(alvo.id) * 1.3);
        System.out.println("COMPRA " + alvo.nomeExibicao() + " (" + alvo.clubeId + ") valor " + j.valorDeMercado(alvo.id) + " oferta " + valor + " -> " + j.proporCompra(alvo.id, valor, 24));
        // venda: um reserva na lista de transferências
        Jogador venda = j.elenco(meu.id).stream().filter(x -> !x.amador()).sorted((a, b) -> Double.compare(a.forca, b.forca)).skip(5).findFirst().orElseThrow();
        j.colocarAVenda(venda.id, j.valorDeMercado(venda.id));
        System.out.println("VENDA " + venda.nomeExibicao() + " à venda por " + j.valorDeMercado(venda.id));
        for (int w = 0; w < 10; w++) {
            j.avancarSemana();
            for (PropostaJogador p : j.propostasEnviadas())
                if (p.jogadorId == alvo.id) {
                    System.out.println("  sem " + w + " enviada: " + p.estado + " | " + p.mensagem);
                    if ("aceita".equals(p.estado)) System.out.println("    contrato -> " + j.proporContrato(p.id, j.salarioPedido(alvo.id) * 11 / 10, 24));
                    if ("aguardando_confirmacao".equals(p.estado)) {
                        System.out.println("    confirmar -> " + j.confirmarNegocio(p.id) + " | estado " + p.estado + " | clube do jogador " + alvo.clubeId + " | caixa " + meu.dinheiro);
                    }
                }
            for (PropostaJogador p : j.propostasRecebidas()) {
                System.out.println("  sem " + w + " recebida: " + j.mundo().jogador(p.jogadorId).nomeExibicao() + " " + p.estado + " valor " + p.valor + " | " + p.mensagem);
                if ("recebida".equals(p.estado) && p.ativa()) {
                    long antes = meu.dinheiro;
                    System.out.println("    aceitar -> " + j.responderProposta(p.id, true) + " | estado " + p.estado + " | " + p.mensagem);
                    if ("aguardando_confirmacao".equals(p.estado)) System.out.println("    confirmar -> " + j.confirmarNegocio(p.id) + " | estado " + p.estado);
                    System.out.println("    clube do jogador " + j.mundo().jogador(p.jogadorId).clubeId + " | caixa " + antes + " -> " + meu.dinheiro + " | janela " + j.janelaAberta());
                }
            }
        }
        System.out.println("fim: " + alvo.nomeExibicao() + " está no " + alvo.clubeId + "; " + venda.nomeExibicao() + " está no " + venda.clubeId + "; caixa " + meu.dinheiro);
    }
}
