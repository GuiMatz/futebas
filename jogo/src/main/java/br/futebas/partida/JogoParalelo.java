package br.futebas.partida;

import java.util.List;

/**
 * Um dos outros jogos da rodada do técnico, já jogado pelo motor antes do jogo dele, para a tela
 * mostrar "rolando" ao mesmo tempo: revele os {@link #eventos} com minuto &lt;= o minuto atual.
 */
public record JogoParalelo(String competicaoId, String competicao, String mandanteId, String mandante,
                           String visitanteId, String visitante, List<EventoJogo> eventos,
                           int golsMandante, int golsVisitante) {

    /** Placar até o minuto dado (inclusive), contando só os eventos de gol. */
    public int[] placarAte(int minuto) {
        int[] p = new int[2];
        for (EventoJogo e : eventos) if (e.minuto() <= minuto && e.gol()) p[e.lado()] += e.valor();
        return p;
    }
}
