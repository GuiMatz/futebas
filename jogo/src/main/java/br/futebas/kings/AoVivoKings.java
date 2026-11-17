package br.futebas.kings;

import br.futebas.partida.EventoJogo;

import java.util.List;

/**
 * Acompanhamento ao vivo de um jogo da Kings League do técnico. O motor chama {@link #minuto}
 * antes de cada minuto (na thread do motor; pode bloquear). Nesse momento o técnico decide se usa
 * a carta secreta e o pênalti do presidente, pelo {@link ControleKings}.
 */
public interface AoVivoKings {

    void minuto(ControleKings c);

    /**
     * Pausas: "carta_disponivel" (a janela da carta abriu: min 5 e min 23), "intervalo",
     * "dado" (resultado no {@link ControleKings#dado()}), "match_ball", "shootouts", "fim".
     */
    default void pausa(ControleKings c, String motivo) {}

    /**
     * Uma cobrança (pênalti da carta, pênalti invertido, pênalti do presidente, shootout da falta no
     * 1v1 ou da carta, disputa do desempate), ANTES de o resultado entrar no placar: a tela mostra
     * em etapas ("vai para a bola…", "bateu…", o resultado). Pode bloquear o motor por um instante
     * (escala com a velocidade; "simular até o fim" pula). Nunca espera o técnico.
     */
    default void cobranca(ControleKings c, Cobranca k) {}

    /**
     * @param tipo "penalti_carta", "penalti_invertido", "penalti_presidente", "shootout", "disputa"
     * @param lado lado de quem bate
     * @param resultado "GOL", "DEFENDEU" ou "PRA FORA"
     * @param disputa na disputa do desempate: as cobranças já batidas de cada lado ("G" gol, "X" erro), sem esta
     */
    /**
     * Shoot out do desempate: a ordem dos batedores do lado {@code lado} (do técnico que acompanha).
     * {@code sugestao}: a ordem sugerida. Devolva as chaves ({@link LigaKings.AtletaKings#chave()}) na ordem
     * escolhida ou null para a automática. Roda na thread do motor e pode bloquear até o técnico confirmar.
     */
    default List<String> ordemShootout(int lado, List<LigaKings.AtletaKings> sugestao) { return null; }

    record Cobranca(String tipo, int lado, String cobrador, String time, String goleiro, String resultado, String[] disputa) {}

    interface ControleKings {
        int minuto();

        int gols(int lado);

        /** Jogadores em campo agora (rampa, dado, cartões e sanção mudam isso). */
        int emCampo(int lado);

        /** "rampa", "normal", "gol_duplo", "dado", "match_ball". */
        String fase();

        /** Resultado do dado ("3v3", "2v2", "1v1") ou null antes do 2º tempo. */
        String dado();

        List<EventoJogo> eventos();

        /**
         * Os números do jogo até agora (posse pela força, finalizações, cartões...), para a tela de
         * trocas. Só leitura: não mexe no sorteio. null = sem números.
         */
        default br.futebas.partida.Estatisticas numerosAoVivo() { return null; }

        /** A carta do lado (o técnico vê a sua); null se já usou. */
        MotorKings.Carta carta(int lado);

        /** Dá para usar a carta agora (janela 5–16:59 e 23–35:59, fora de gol duplo e dado)? */
        boolean podeUsarCarta(int lado);

        /**
         * Usa a carta. {@code coringaVira}: a carta que o Coringa vira (null = rouba a do adversário
         * se ainda não foi usada; senão Gol Duplo). {@code alvoInvertido}: chave ({@link LigaKings.AtletaKings#chave()})
         * do adversário que cobra o Pênalti Invertido (null = o pior finalizador dele em campo). Retorna false se não pode.
         */
        boolean usarCarta(int lado, MotorKings.Carta coringaVira, String alvoInvertido);

        /** Quem está em campo agora no lado (para escolher o cobrador do Pênalti Invertido). */
        default List<LigaKings.AtletaKings> emCampoAgora(int lado) { return List.of(); }

        /** O banco do lado agora (substituições ilimitadas da Kings: quem saiu pode voltar). */
        default List<LigaKings.AtletaKings> bancoAgora(int lado) { return List.of(); }

        /** Energia (0–100) do atleta no jogo (rodízio da Kings). */
        default double energiaDe(LigaKings.AtletaKings a) { return 100; }

        /**
         * Substituição do técnico (Kings: ilimitadas e de volta; o goleiro também pode ser trocado):
         * {@code sai} (em campo) por {@code entra} (no banco), pelas chaves. Falso se não deu.
         */
        default boolean substituir(int lado, String sai, String entra) { return false; }

        /** O pênalti do presidente ainda está disponível e a fase permite? */
        boolean podePenaltiPresidente(int lado);

        boolean cobrarPenaltiPresidente(int lado);
    }
}
