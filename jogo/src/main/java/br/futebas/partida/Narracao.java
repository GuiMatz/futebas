package br.futebas.partida;

import br.futebas.mundo.Rng;

import java.util.List;
import java.util.Map;

/**
 * Textos de narração em português do Brasil, curtos, no espírito do Brasfoot. Adaptados das
 * frases do FUT.AE, sem os emojis e a formatação do Discord.
 */
public final class Narracao {
    private Narracao() {}

    static final String[] GOL = {
        "GOL do {time}! {autor} bate firme e estufa a rede.",
        "GOL! {autor} aparece livre na área e marca para o {time}.",
        "É GOL! Bela jogada de {autor}. O {time} comemora.",
        "GOL do {time}! {autor} tira do goleiro e manda para o fundo da rede.",
    };
    static final Map<String, String> GOL_JOGADA = Map.of(
        "falta", "GOL DE FALTA! {autor} coloca a bola no ângulo para o {time}.",
        "cabeca", "GOL DE CABEÇA! {autor} sobe mais que todos e marca para o {time}.",
        "fora", "QUE GOLAÇO! {autor} solta a bomba de fora da área para o {time}.",
        "contra_ataque", "GOL DE CONTRA-ATAQUE! {autor} puxa a corrida e finaliza para o {time}.",
        "rebote", "GOL! O goleiro solta e {autor} aproveita o rebote para o {time}.",
        "escanteio", "GOL DE ESCANTEIO! {autor} desvia na pequena área para o {time}.",
        "contra", "GOL CONTRA! {autor} tenta cortar e manda para as próprias redes. Gol do {time}."
    );
    static final String GOL_NO_FIM = " No apagar das luzes!";
    static final String[] DEFESA = {
        "{autor} arrisca e {goleiro} faz grande defesa.",
        "Que defesa de {goleiro}! Impediu o gol de {autor}.",
        "{autor} sai cara a cara, mas {goleiro} fecha o ângulo e salva.",
    };
    static final String[] MILAGRE = {
        "MILAGRE! {goleiro} voa e tira a bola de {autor} quando todos gritavam gol.",
        "INACREDITÁVEL! {autor} chuta no ângulo e {goleiro} busca com a ponta dos dedos.",
    };
    static final String[] FORA = {
        "{autor} finaliza e a bola passa raspando a trave.",
        "{autor} isolou! Era uma chance clara para o {time}.",
        "{autor} chuta cruzado, mas a bola sai pela linha de fundo.",
    };
    static final String[] TRAVE = {
        "NA TRAVE! {autor} quase marca para o {time}.",
        "Bola no travessão! {autor} acertou a madeira.",
    };
    static final String[] PRESSAO = {
        "O {time} se lança ao ataque e encurrala o adversário.",
        "Pressão total do {time}! A torcida empurra o time.",
        "O {time} sente o momento e parte para cima.",
    };
    static final Map<Integer, String> FEITO = Map.of(
        3, "HAT-TRICK! {autor} marca o terceiro e leva a bola para casa.",
        4, "POKER! {autor} faz o quarto gol. Que noite!",
        5, "{autor} chega a {gols} gols! Atuação histórica."
    );

    static final Map<String, String> PENALTI = Map.ofEntries(
        Map.entry("angulo", "acerta o ângulo"),
        Map.entry("deslocou", "desloca o goleiro e converte"),
        Map.entry("canto", "bate rasteiro no canto"),
        Map.entry("meio", "enche o pé no meio do gol"),
        Map.entry("cavadinha", "converte de cavadinha"),
        Map.entry("defendeu", "para em {goleiro}"),
        Map.entry("milagre", "para em defesa inacreditável de {goleiro}"),
        Map.entry("isolou", "isola por cima do gol"),
        Map.entry("trave", "carimba a trave"),
        Map.entry("fora", "bate para fora")
    );
    static final String[] PEN_GOL = {"angulo", "canto", "deslocou", "meio", "cavadinha"};
    static final double[] PEN_GOL_PESO = {22, 38, 24, 14, 2};
    static final String[] PEN_ERRO = {"defendeu", "milagre", "isolou", "trave", "fora"};
    static final double[] PEN_ERRO_PESO = {60, 14, 12, 6, 8};

    static String jeitoPenalti(boolean gol, Rng rng) {
        return gol ? PEN_GOL[rng.peso(PEN_GOL_PESO)] : PEN_ERRO[rng.peso(PEN_ERRO_PESO)];
    }

    static String frasePenalti(String jeito, String goleiro) {
        String f = PENALTI.getOrDefault(jeito, "cobra");
        return f.replace("{goleiro}", goleiro == null ? "o goleiro" : goleiro);
    }

    static String preencher(String modelo, String time, String autor, String goleiro) {
        String s = modelo;
        if (time != null) s = s.replace("{time}", time);
        if (autor != null) s = s.replace("{autor}", autor);
        if (goleiro != null) s = s.replace("{goleiro}", goleiro);
        return s;
    }

    static String sortear(String[] opcoes, Rng rng) {
        return opcoes[rng.inteiro(opcoes.length)];
    }

    // ---------------------------------------------------------------- comentário final
    private static final Map<String, List<String>> COMENTARIOS = Map.of(
        "virada", List.of(
            "Começou mal e terminou sorrindo: o {vencedor} buscou a virada com personalidade.",
            "O {perdedor} saiu na frente, mas não segurou. Virada merecida do {vencedor}.",
            "Tomou o gol, não se abateu e virou. Grande reação do {vencedor}."),
        "goleada", List.of(
            "Atropelo! O {vencedor} não tomou conhecimento do {perdedor}: {placar}.",
            "Noite para esquecer do {perdedor}. O {vencedor} sobrou em campo.",
            "Goleada com autoridade. O {placar} retrata bem o que foi o jogo."),
        "vitoria_facil", List.of(
            "Vitória tranquila do {vencedor}, sem sustos.",
            "O {vencedor} controlou o jogo e venceu sem precisar acelerar muito.",
            "Superioridade clara do {vencedor}. O {perdedor} não encontrou o caminho."),
        "jogo_dificil", List.of(
            "Vitória suada do {vencedor}! Jogo pegado, decidido nos detalhes.",
            "Não foi bonito, mas foi eficiente: o {vencedor} sofreu e segurou o resultado.",
            "O {perdedor} vendeu caro a derrota. Mérito do {vencedor}."),
        "zero", List.of(
            "Zero a zero. As defesas levaram a melhor do começo ao fim.",
            "Jogo truncado, poucas chances e placar em branco.",
            "Ninguém conseguiu furar o bloqueio. Um ponto para cada lado."),
        "empate", List.of(
            "Empate justo pelo que os dois times apresentaram.",
            "Jogo equilibrado, e o placar ficou de bom tamanho.",
            "Cada um ficou com um ponto. O equilíbrio deu o tom da partida."),
        "penaltis", List.of(
            "Noventa minutos não bastaram. Nos pênaltis, o {vencedor} teve mais frieza.",
            "Coração na boca até a última cobrança. O {vencedor} sobreviveu.",
            "A disputa foi cruel com o {perdedor}. O {vencedor} comemora como título.")
    );

    static String comentario(ResultadoPartida r, boolean houveVirada, Rng rng) {
        int dif = Math.abs(r.golsMandante - r.golsVisitante);
        int lado = r.vencedorLado();
        String chave;
        if (r.penaltisMandante != null) chave = "penaltis";
        else if (lado < 0) chave = r.golsMandante == 0 ? "zero" : "empate";
        else if (houveVirada) chave = "virada";
        else if (dif >= 4) chave = "goleada";
        else if (dif >= 2) chave = "vitoria_facil";
        else chave = "jogo_dificil";
        List<String> l = COMENTARIOS.get(chave);
        String s = l.get(rng.inteiro(l.size()));
        String venc = lado == 0 ? r.mandante : lado == 1 ? r.visitante : r.mandante;
        String perd = lado == 0 ? r.visitante : lado == 1 ? r.mandante : r.visitante;
        String placar = Math.max(r.golsMandante, r.golsVisitante) + " x " + Math.min(r.golsMandante, r.golsVisitante);
        return s.replace("{vencedor}", venc).replace("{perdedor}", perd).replace("{placar}", placar);
    }
}
