package br.futebas.partida;

import br.futebas.mundo.Posicao;

/** Um jogador numa partida: posição natural, vaga em que joga, força do dia e energia ao vivo. */
public final class Escalado {
    public final int id;
    public final String nome;
    public final Posicao natural;
    public Posicao vaga;
    /** Força do dia (já com moral e com a energia com que chegou ao jogo). */
    public final double forca;
    /** Rendimento na vaga calculado pelo escalador (posições secundárias); < 0 = usar a tabela. */
    public double encaixe = -1;
    /**
     * Energia (0–100). Antes do jogo, a do jogador; durante o jogo o motor a gasta minuto a minuto
     * (como no Brasfoot: mais com a idade, o estilo intenso e a marcação pesada).
     */
    public double energia = 100;
    /** Energia ao entrar em campo (o cansaço do jogo é a queda desde então); < 0 = ainda não entrou. */
    public double energiaInicio = -1;
    /** Ano de nascimento (0 = desconhecido: o motor usa a idade média). */
    public int anoNasc;
    /** Fôlego: fator do cansaço no rendimento (1 = inteiro). O motor recalcula a cada minuto. */
    public double folego = 1.0;

    public Escalado(int id, String nome, Posicao natural, Posicao vaga, double forca) {
        this.id = id;
        this.nome = nome;
        this.natural = natural;
        this.vaga = vaga;
        this.forca = forca;
    }

    /** Cópia com outra vaga e força (substituição, lesão): leva a energia, o ano de nascimento e o fôlego. */
    public Escalado copia(Posicao novaVaga, double novaForca, boolean manterEncaixe) {
        Escalado e = new Escalado(id, nome, natural, novaVaga, novaForca);
        if (manterEncaixe) e.encaixe = encaixe;
        e.energia = energia;
        e.energiaInicio = energiaInicio;
        e.anoNasc = anoNasc;
        e.folego = folego;
        return e;
    }

    /** O que o jogador rende na vaga em que foi escalado, sem o cansaço do jogo. */
    public double rendeInteiro() {
        double f = encaixe >= 0 ? encaixe : natural.encaixe(vaga);
        if (f <= 0) f = vaga == Posicao.GOL || natural == Posicao.GOL ? 0.35 : 0.7;
        return forca * f;
    }

    /** O que o jogador rende agora (com o cansaço do jogo). */
    public double rende() {
        return rendeInteiro() * folego;
    }
}
