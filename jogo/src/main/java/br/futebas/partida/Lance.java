package br.futebas.partida;

/** Um lance da partida. {@code minuto} fica em 1-120; acréscimos vão em {@code acrescimo}. */
public final class Lance {
    public final int minuto, acrescimo;
    /**
     * inicio, gol, defesa, milagre, trave, fora, impedimento, cartao, vermelho, pressao, tatica,
     * intervalo, segundo_tempo, substituicao, feito, prorrogacao, penalidades, cobranca, fim.
     */
    public final String tipo;
    public final String texto;
    /** 0 mandante, 1 visitante, -1 nenhum. */
    public final int lado;
    public final int golsMandante, golsVisitante;
    public final Integer jogadorId;
    public final String jogador;
    public final String detalhe;
    /** penalti, falta, cabeca, fora, escanteio, rebote, contra_ataque, contra ou null. */
    public final String jogada;

    public Lance(int minuto, int acrescimo, String tipo, String texto, int lado, int gm, int gv,
                 Integer jogadorId, String jogador, String detalhe, String jogada) {
        this.minuto = minuto;
        this.acrescimo = acrescimo;
        this.tipo = tipo;
        this.texto = texto;
        this.lado = lado;
        this.golsMandante = gm;
        this.golsVisitante = gv;
        this.jogadorId = jogadorId;
        this.jogador = jogador;
        this.detalhe = detalhe;
        this.jogada = jogada;
    }

    public String relogio() {
        return acrescimo > 0 ? minuto + "+" + acrescimo + "'" : minuto + "'";
    }

    @Override
    public String toString() {
        return relogio() + " " + texto;
    }
}
