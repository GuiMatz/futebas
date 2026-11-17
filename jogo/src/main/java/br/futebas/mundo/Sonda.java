package br.futebas.mundo;

import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.partida.ResultadoPartida;

/**
 * Ganchos de medição para as ferramentas de balanceamento ({@code ferramentas.Balanco}). Fica
 * {@code null} no jogo: nada aqui consome o RNG nem muda o mundo (só observa).
 */
public interface Sonda {
    /** A sonda ligada (null = nenhuma; o jogo nunca liga). */
    final class Atual {
        public static Sonda s;

        private Atual() {}
    }

    /** Partida de clubes ou seleções jogada (antes do torneio aplicar o placar). */
    default void partida(Torneio t, Partida p, ResultadoPartida r) {}

    /** Partida da Kings League (ligas e mundiais). */
    default void partidaKings(Torneio t, Partida p, ResultadoPartida r) {}

    /** Transferência concluída (preço 0 = jogador livre). */
    default void transferencia(Jogador j, Clube origem, Clube destino, long preco) {}

    /** Empréstimo da IA concluído. */
    default void emprestimo(Jogador j, Clube origem, Clube destino) {}

    /** Lesão (semanas fora). */
    default void lesao(Jogador j, int semanas) {}

    /** Técnico da IA demitido. */
    default void demissao(Clube c) {}
}
