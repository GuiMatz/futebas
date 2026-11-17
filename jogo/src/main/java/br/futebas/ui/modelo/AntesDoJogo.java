package br.futebas.ui.modelo;

import br.futebas.competicao.Partida;
import br.futebas.ui.Sessao;

/**
 * Os passos antes de cada jogo do técnico (regra do jogo): pré-jogo → escalação → partida, para
 * TODO jogo não delegado do clube ou do time da Kings (inclusive o 2º jogo da mesma semana), cada
 * passo uma vez por partida. Sem tela: o {@code App} mostra os passos e os testes conferem a
 * sequência numa temporada inteira.
 *
 * <p>Bug corrigido "alguns jogos aparece para escalar antes, outros já vai direto": o 2º jogo da
 * semana voltava direto para a partida, e o jogo anunciado (de onde vinha a chave) nem sempre era o
 * jogado (dois jogos do mesmo time no mesmo dia, ver {@code competicao.Agenda}).
 */
public final class AntesDoJogo {
    public enum Passo { PRE_JOGO, ESCALACAO, PARTIDA }

    private String preJogoVisto, escalacaoVista;

    /** O próximo jogo do técnico a jogar agora (clube: o desta semana; Kings: o próximo marcado); null = nenhum. */
    public static Partida partida(Sessao s) {
        if (s == null) return null;
        if (s.jogo.meuClube() != null) return s.fluxo.temJogoNaSemana() ? s.fluxo.proximoJogo() : null;
        if (s.fluxo.soKings() && s.jogo.meuTimeKings() != null) return s.fluxo.proximoJogoKings();
        return null;
    }

    /** Chave da partida: competição, id, semana e dia (o id se repete a cada ano; o dia pode mudar de semana). */
    public static String chave(Partida p) {
        return p.torneioId + "#" + p.id + "#" + p.sa + "#" + p.dia;
    }

    /**
     * O passo a mostrar agora: o pré-jogo (uma vez por partida), depois a escalação (se ligada nas
     * Opções), depois a partida. Jogo delegado ao auxiliar (ou sem jogo): direto para a partida.
     */
    public Passo proximo(Sessao s, boolean escalacaoLigada) {
        Partida p = partida(s);
        if (p == null || s.jogo.delegadoAoAuxiliar(p.torneioId, p.id)) return Passo.PARTIDA;
        String k = chave(p);
        if (!k.equals(preJogoVisto)) {
            preJogoVisto = k;
            return Passo.PRE_JOGO;
        }
        if (escalacaoLigada && !k.equals(escalacaoVista)) {
            escalacaoVista = k;
            return Passo.ESCALACAO;
        }
        return Passo.PARTIDA;
    }
}
