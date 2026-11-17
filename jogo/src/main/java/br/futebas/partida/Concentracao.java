package br.futebas.partida;

/**
 * Por onde o time concentra os ataques. Pelo meio: rende mais contra um meio-campo fraco;
 * pelas laterais: rende mais contra laterais fracos. Os números ficam em {@code Balanceamento}.
 */
public enum Concentracao {
    EQUILIBRADO("Equilibrado"),
    MEIO("Pelo meio"),
    LATERAIS("Pelas laterais");

    public final String nome;

    Concentracao(String nome) { this.nome = nome; }
}
