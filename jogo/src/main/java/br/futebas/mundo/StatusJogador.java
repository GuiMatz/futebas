package br.futebas.mundo;

/** Situação do jogador no mundo. */
public enum StatusJogador {
    /** Categorias de base de um clube. */
    BASE,
    /** Elenco profissional de um clube (inclui emprestados). */
    PROFISSIONAL,
    /** Sem clube. */
    LIVRE,
    APOSENTADO,
    /** Fora do futebol sem ser aposentado (saves antigos: amadores dispensados). */
    DISPENSADO
}
