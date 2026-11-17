package br.futebas.partida;

/** Regras e contexto de uma partida (as regras da época entram aqui). */
public final class OpcoesPartida {
    /** O mandante tem o apoio da torcida (falso em campo neutro). */
    public boolean mando = true;
    /** Forma da temporada de cada lado (pontos de força; {@code Clube.formaTemporada}). */
    public double formaMandante, formaVisitante;
    /** Gerar os textos de narração (só nos jogos do usuário). */
    public boolean narrar;
    /** Substituições permitidas (regra da época). */
    public int substituicoes = 3;
    public boolean var;
    /** Se empatar, joga prorrogação de 30 minutos. */
    public boolean prorrogacao;
    /** Na prorrogação, o primeiro gol encerra o jogo (regra da época). */
    public boolean golDeOuro;
    /** Multiplicador de chances da época (calibração de gols por época). */
    public double fatorGols = 1.0;
    /** Acompanhamento ao vivo (só o jogo do técnico na interface); null = simula direto. */
    public transient AoVivo aoVivo;
    /** Lado do técnico humano (0 mandante, 1 visitante, -1 nenhum): recebe as pausas e escolhe o batedor. */
    public int ladoHumano = -1;
    /**
     * Duelo entre dois técnicos humanos (multi-treinador): os dois lados são humanos (pausas, batedor,
     * intervalo primeiro o mandante, depois o visitante). {@link #ladoHumano} fica 0.
     */
    public boolean ambosHumanos;
    /** Fator de elite desta partida ({@code MotorPartida.fatorElite}); NaN = o padrão das ligas. Continentais usam mais. */
    public double eliteK = Double.NaN;
    /** Ano da partida (idade dos jogadores no cansaço); 0 = desconhecido (idade média). */
    public int ano;

    public OpcoesPartida() {}
}
