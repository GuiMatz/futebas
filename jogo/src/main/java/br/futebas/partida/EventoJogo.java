package br.futebas.partida;

/**
 * Evento resumido de uma partida (para mostrar os outros jogos da rodada "rolando"): só gol,
 * cartão, pênalti e lesão, sem lances de chute.
 *
 * @param tipo "gol", "gol_contra", "penalti_gol", "penalti_perdido", "amarelo", "vermelho" ou "lesao"; na Kings também
 *             "gol_x2", "shootout_gol", "shootout_perdido", "penalti_presidente_gol", "penalti_presidente_perdido",
 *             "carta_sorteada", "carta_usada", "dado", "fase", "disputa_gol", "disputa_perdido" ({@code jogador} = detalhe),
 *             "penalti_carta_gol|_defendeu|_fora", "penalti_invertido_gol|_defendeu|_fora", "jogador_estrela", "suspensao",
 *             "suspensao_fim" e, no jogo narrado, "chance_defesa" ({@code jogador} = "chutador|goleiro"), "chance_fora",
 *             "chance_trave", "chance_bloqueio"
 * @param lado 0 = mandante, 1 = visitante (no gol contra, o lado que ganhou o gol)
 */
public record EventoJogo(int minuto, int acrescimo, String tipo, int lado, int jogadorId, String jogador) {
    /** Conta no placar? (Kings: também {@code shootout_gol}, {@code penalti_presidente_gol}; sufixo {@code _x2} = vale 2.) */
    public boolean gol() {
        String t = tipo.endsWith("_x2") ? tipo.substring(0, tipo.length() - 3) : tipo;
        return t.equals("gol") || t.equals("gol_contra") || t.equals("penalti_gol") || t.equals("shootout_gol") || t.equals("penalti_presidente_gol");
    }

    /** Quanto o evento soma no placar (0, 1 ou 2). */
    public int valor() {
        return !gol() ? 0 : tipo.endsWith("_x2") ? 2 : 1;
    }
}
