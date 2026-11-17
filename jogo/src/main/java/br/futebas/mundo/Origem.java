package br.futebas.mundo;

/** De onde o jogador veio. */
public enum Origem {
    /** Jogador real do banco, com pelo menos uma fonte. */
    REAL,
    /** Amador em teste de saves antigos (a mecânica saiu em 15/11/2026; ao carregar, eles se aposentam). */
    COMPLEMENTAR_TESTE,
    /** Jogador gerado (completar elenco no banco, ou jovem da peneira), com nome real do país. */
    GERADO
}
