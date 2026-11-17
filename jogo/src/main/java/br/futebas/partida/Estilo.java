package br.futebas.partida;

/**
 * Estilos de jogo (portados do FUT.AE): fator de chances criadas, de chances cedidas e de
 * conversão, mais a inclinação da posse. Cada estilo vence dois e perde para dois.
 */
public enum Estilo {
    EQUILIBRADO("Equilibrado", 1.00, 1.00, 1.00, 0),
    ATAQUE_TOTAL("Ataque total", 1.14, 1.13, 0.97, 4),
    CONTRA_ATAQUE("Contra-ataque", 0.86, 0.89, 1.10, -5),
    PRESSAO_ALTA("Pressão alta", 1.06, 1.06, 1.00, 3),
    RETRANCA("Retranca", 0.75, 0.75, 1.02, -8);

    public final String nome;
    public final double cria, cede, converte;
    public final int inclinacaoPosse;

    Estilo(String nome, double cria, double cede, double converte, int posse) {
        this.nome = nome;
        this.cria = cria;
        this.cede = cede;
        this.converte = converte;
        this.inclinacaoPosse = posse;
    }

    public static final double VANTAGEM = 0.04;

    public boolean vence(Estilo outro) {
        return switch (this) {
            case ATAQUE_TOTAL -> outro == EQUILIBRADO || outro == RETRANCA;
            case CONTRA_ATAQUE -> outro == ATAQUE_TOTAL || outro == PRESSAO_ALTA;
            case EQUILIBRADO -> outro == CONTRA_ATAQUE || outro == RETRANCA;
            case PRESSAO_ALTA -> outro == EQUILIBRADO || outro == ATAQUE_TOTAL;
            case RETRANCA -> outro == CONTRA_ATAQUE || outro == PRESSAO_ALTA;
        };
    }

    /** Multiplica as chances criadas: estilo que vence o outro cria um pouco mais. */
    public double confronto(Estilo outro) {
        if (vence(outro)) return 1 + VANTAGEM;
        if (outro.vence(this)) return 1 - VANTAGEM;
        return 1.0;
    }
}
