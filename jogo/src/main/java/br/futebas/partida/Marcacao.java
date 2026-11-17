package br.futebas.partida;

/**
 * Intensidade da marcação. Pesada: o adversário cria menos, mas o time comete mais faltas,
 * leva mais cartões e cansa mais. Leve: o contrário. Os números ficam em {@code Balanceamento}.
 */
public enum Marcacao {
    LEVE("Leve"),
    NORMAL("Normal"),
    PESADA("Pesada");

    public final String nome;

    Marcacao(String nome) { this.nome = nome; }
}
