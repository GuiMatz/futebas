package br.futebas.ia;

/**
 * Vaga de técnico aberta num clube (requisito: "as vagas ficam em aberto em alguma aba e,
 * caso a pessoa queira, ela aceita"). Abre quando a diretoria demite o técnico da IA e fecha
 * quando o clube fecha com um técnico (1 a 4 semanas). O humano pode se candidatar: a diretoria
 * responde em alguns dias (uma nota curta na caixa só com a resposta final).
 */
public class Vaga {
    public int id;
    public String clubeId;
    /** "Técnico demitido", "Técnico saiu"... */
    public String motivo;
    /** Semana absoluta em que abriu e em que fecha (o clube contrata um técnico da IA). */
    public int abertaEm, fechaEm;
    /** aberta | fechada */
    public String estado = "aberta";
    /** Candidatura do humano: semana absoluta da resposta (0 = não se candidatou). */
    public int respostaEm;
    /** null (sem resposta ainda) | aceita | recusada */
    public String resposta;
    /** Multi-treinador: nome do técnico humano que se candidatou (null = o único técnico). */
    public String candidato;

    public Vaga() {}

    public boolean aberta() { return "aberta".equals(estado); }
}
