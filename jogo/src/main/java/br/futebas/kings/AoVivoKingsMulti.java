package br.futebas.kings;

/**
 * Multi-treinador: os jogos da Kings dos técnicos humanos da mesma semana rodam JUNTOS na tela
 * dividida (como os do futebol, {@code partida.AoVivoMulti}). O motor monta o grupo, pede um
 * {@link AoVivoKings} para cada jogo e simula todos ao mesmo tempo, cada um na sua thread.
 */
public interface AoVivoKingsMulti {

    /** Acompanhamento de um jogo do grupo; {@code humanoA}/{@code humanoB}: o lado é de um técnico humano. null = sem tela. */
    AoVivoKings partida(String timeA, String timeB, boolean humanoA, boolean humanoB);

    /**
     * Antes de um grupo com um time de técnico que já jogou nesta semana (rodada dupla): a tela volta às
     * vezes desses técnicos e bloqueia até o último mandar jogar (regra do jogo: um jogo por "Jogar").
     */
    default void antesDoGrupo(java.util.List<String> times) {}

    /** O grupo montado vai começar. */
    default void iniciarGrupo() {}

    /** O jogo deste acompanhamento acabou (também se o motor caiu). */
    default void terminou(AoVivoKings jogo) {}

    /** Todos os jogos do grupo terminaram. */
    default void fimDoGrupo() {}
}
