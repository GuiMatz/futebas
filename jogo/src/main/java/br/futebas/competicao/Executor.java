package br.futebas.competicao;

import br.futebas.partida.ResultadoPartida;

/** Quem joga as partidas de um torneio (o simulador do mundo, ou um teste). */
public interface Executor {
    /** Joga a partida (90 minutos, ou 120 se {@code prorrogacao} e empatar). */
    ResultadoPartida jogar(Torneio torneio, Partida partida, boolean prorrogacao);

    /** Disputa de pênaltis depois da partida: {pênaltis mandante, pênaltis visitante}. */
    int[] penaltis(Torneio torneio, Partida partida);

    /**
     * Partidas a jogar por último na data (o jogo ao vivo do técnico): assim os outros jogos da
     * rodada já têm resultado e a tela pode mostrá-los "rolando" junto.
     */
    default boolean adiar(Torneio torneio, Partida partida) { return false; }

    /**
     * O torneio acabou de criar partidas (fase nova ou rodada de mata-mata), antes de jogá-las: o
     * simulador acerta a agenda (nenhum time com dois jogos no mesmo dia, {@link Agenda}).
     */
    default void novasPartidas(Torneio torneio) {}

    /**
     * Outra competição do mesmo ano (a Sul-Americana busca na Libertadores os eliminados da 3ª fase e
     * os terceiros dos grupos); null = não há (testes, sub-20 sem a competição).
     */
    default Torneio outroTorneio(Torneio atual, String competicaoId) { return null; }

    /** País do clube (sorteio de grupos sem dois do mesmo país); null = desconhecido (sem restrição). */
    default String paisDe(String clubeId) { return null; }
}
