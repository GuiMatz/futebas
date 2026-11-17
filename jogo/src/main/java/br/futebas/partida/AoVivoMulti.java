package br.futebas.partida;

/**
 * Acompanhamento ao vivo do modo multi-treinador: os jogos dos técnicos humanos da mesma rodada
 * rodam JUNTOS numa tela só (dividida). O motor monta o grupo de jogos, pede um {@link AoVivo} para
 * cada um e simula todos ao mesmo tempo, cada um na sua thread; quem acompanha sincroniza os minutos
 * e as pausas (na ordem dos técnicos).
 */
public interface AoVivoMulti {

    /**
     * Acompanhamento de um jogo do grupo que está sendo montado ({@code ladoHumano}/{@code ambosHumanos}
     * já definidos nas opções). null = esse jogo roda sem tela.
     */
    AoVivo partida(String mandanteId, String visitanteId, OpcoesPartida op);

    /** O grupo montado vai começar (todos os jogos pedidos em {@link #partida} desde o último grupo). */
    default void iniciarGrupo() {}

    /** Todos os jogos do grupo terminaram. */
    default void fimDoGrupo() {}

    /**
     * Antes de um 2º grupo de jogos na mesma semana (algum técnico joga duas vezes), ANTES de montar os
     * times: a tela volta às vezes dos técnicos desses clubes ({@code clubes}: os clubes humanos que
     * jogam no grupo) e bloqueia até o último mandar jogar (regra do jogo: um jogo por "Jogar").
     */
    default void antesDoGrupo(java.util.List<String> clubes) {}
}
