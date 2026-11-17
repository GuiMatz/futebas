package br.futebas.mundo;

/**
 * Contrato de um jogador com um clube. As datas são semanas absolutas
 * ({@code ano * 52 + semana}).
 */
public class Contrato {
    public TipoContrato tipo = TipoContrato.PROFISSIONAL;
    /** Salário mensal em reais. */
    public long salario;
    /** Semana absoluta em que o contrato termina (inclusive). */
    public int fim;
    /** Multa rescisória (cláusula de liberação). */
    public long multa;
    /** Só nos contratos de teste de saves antigos (não são mais criados). */
    public int jogosRestantes;
    /** Só no empréstimo: clube dono do jogador. */
    public String clubeOrigem;

    public Contrato() {}

    public static Contrato profissional(long salario, int fim, long multa) {
        Contrato c = new Contrato();
        c.tipo = TipoContrato.PROFISSIONAL;
        c.salario = salario;
        c.fim = fim;
        c.multa = multa;
        return c;
    }

    public boolean teste() { return tipo == TipoContrato.TESTE; }

    public static int semanaAbsoluta(int ano, int semana) { return ano * 52 + semana; }
}
