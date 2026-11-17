package br.futebas.partida;

import java.util.List;

/**
 * Acompanhamento ao vivo de uma partida narrada (o jogo do técnico na interface). O motor
 * chama {@link #minuto} antes de cada minuto jogado; quem acompanha pode, nesse momento,
 * fazer substituições e mudar o estilo do seu lado. Sem acompanhamento (null), o motor roda
 * exatamente como antes: a mesma semente dá o mesmo resultado.
 */
public interface AoVivo {

    /** Chamado no motor antes de cada minuto (e no intervalo). Roda na thread do motor. */
    void minuto(Controle c);

    /**
     * Antes de montar os times de um jogo do técnico (no motor): no 2º jogo da mesma semana a tela
     * volta à central para o pré-jogo e a escalação, e pode bloquear até o técnico mandar jogar.
     */
    default void antesDoProximoJogo(String mandanteId, String mandante, String visitanteId, String visitante) {}

    /** Chamado quando o jogo termina (antes de uma eventual disputa de pênaltis). */
    default void fim(ResultadoPartida r) {}

    /** Por que o jogo parou para o técnico. */
    enum Pausa { PENALTI_A_FAVOR, LESAO, EXPULSAO, INTERVALO }

    /**
     * O jogo parou por um evento do lado do técnico ({@link OpcoesPartida#ladoHumano}): lesão ou
     * expulsão no time dele, ou o intervalo (sempre). Roda na thread do motor e pode bloquear até o
     * técnico decidir; o que ele fizer pelo {@link Controle} (substituir, mudar estilo, marcação,
     * concentração) vale na hora. {@code jogadorId} = quem se machucou / foi expulso (null no intervalo).
     */
    default void pausa(Controle c, Pausa motivo, int lado, Integer jogadorId) {}

    /**
     * Pênalti a favor do time do técnico (pausa {@link Pausa#PENALTI_A_FAVOR}): devolva o id do
     * batedor (um dos {@code candidatos}, que estão em campo) ou null para o cobrador automático.
     * Pode bloquear até o técnico escolher.
     */
    default Integer escolherBatedor(Controle c, int lado, List<Escalado> candidatos) { return null; }

    /**
     * Disputa de pênaltis: a ordem dos batedores do lado {@code lado} (do técnico que acompanha).
     * {@code sugestao}: quem terminou em campo, na ordem automática. Devolva os ids na ordem escolhida
     * (os 5 primeiros batem a série; os demais, nas alternadas) ou null para a automática. Roda na
     * thread do motor e pode bloquear até o técnico confirmar.
     */
    default List<Integer> ordemPenaltis(int lado, List<Escalado> sugestao) { return null; }

    /**
     * A disputa de pênaltis decidida ({@code cobrancas}: os lances "cobranca", na ordem), para a tela
     * que acompanha mostrar cobrança a cobrança. Não bloqueia.
     */
    default void disputaPenaltis(ResultadoPartida r, List<Lance> cobrancas) {}

    /**
     * Antes do apito inicial: os outros jogos da mesma rodada (mesma competição e data), já
     * jogados, para a tela mostrar todos "rolando" junto, minuto a minuto.
     */
    default void outrosJogos(List<JogoParalelo> jogos) {}

    /** O que quem acompanha pode ler e fazer durante a partida. Lado 0 = mandante, 1 = visitante. */
    interface Controle {
        int minuto();

        int acrescimo();

        boolean intervalo();

        boolean prorrogacao();

        int gols(int lado);

        /** Lances narrados até agora (lista viva do motor: copie, não guarde). */
        List<Lance> lances();

        TimePartida time(int lado);

        List<Escalado> emCampo(int lado);

        List<Escalado> banco(int lado);

        /**
         * As vagas da formação, na ordem dos titulares (quem foi expulso continua na vaga dele; o
         * substituto ocupa a vaga de quem saiu). Para desenhar o campo.
         */
        default List<Escalado> vagas(int lado) { return emCampo(lado); }

        int substituicoesFeitas(int lado);

        int substituicoesPermitidas();

        Estilo estilo(int lado);

        /** Ids que já saíram do jogo (substituídos ou expulsos) e não podem voltar. */
        java.util.Set<Integer> sairam(int lado);

        /** Faz uma substituição. Retorna false se não for permitida. */
        boolean substituir(int lado, int saiId, int entraId);

        void mudarEstilo(int lado, Estilo estilo);

        /** Dois jogadores em campo trocam de posição (não gasta substituição). */
        default boolean trocarPosicoes(int lado, int idA, int idB) { return false; }

        /**
         * Muda a formação no meio do jogo (não gasta substituição): cada um vai para a vaga nova em que
         * rende mais ({@link Escalador#remapear}); o perfil do time é refeito. false = formação inválida.
         */
        default boolean mudarFormacao(int lado, String formacao) { return false; }

        /**
         * Os números do jogo até agora (posse, finalizações, no alvo, escanteios, faltas, cartões e
         * impedimentos), para a tela de trocas/intervalo. Só leitura: não mexe no sorteio. null = sem números.
         */
        default Estatisticas estatisticas() { return null; }

        /** Eventos resumidos (gol, cartão, pênalti, lesão) até agora, dos dois lados. */
        List<EventoJogo> eventos();

        Marcacao marcacao(int lado);

        void mudarMarcacao(int lado, Marcacao marcacao);

        Concentracao concentracao(int lado);

        void mudarConcentracao(int lado, Concentracao concentracao);

        /**
         * O técnico assume o lado: o motor deixa de fazer as substituições e a troca tática
         * automáticas desse lado.
         */
        void assumirControle(int lado);
    }
}
