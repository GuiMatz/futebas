package br.futebas.competicao;

/** Uma partida marcada numa competição. */
public class Partida {
    public int id;
    public String torneioId;
    /** Data (índice da rodada no torneio, 0..totalDatas-1). */
    public int data;
    /** Semana do ano (1..52) e ano em que foi (ou será) jogada. */
    public int semana, ano;
    /** Semana absoluta (ano * 52 + semana). */
    public int sa;
    /** Dia do jogo (LocalDate.toEpochDay). */
    public long dia;
    public int fase;
    public int grupo = -1;
    /** Mata-mata: rodada da fase, índice do confronto e perna (0 ida, 1 volta). */
    public int rodadaMata = -1, confronto = -1, perna;
    public String mandante, visitante;
    public Integer golsMandante, golsVisitante;
    public Integer penaltisMandante, penaltisVisitante;
    public boolean prorrogacao;
    public boolean jogada;
    public int cartoesMandante, cartoesVisitante;
    /** Melhor em campo (nome) e público, para a tela de resultados. */
    public String melhor;
    public int publico;

    public Partida() {}

    public String placar() {
        if (!jogada) return "x";
        String s = golsMandante + " x " + golsVisitante;
        if (penaltisMandante != null) s += " (" + penaltisMandante + " x " + penaltisVisitante + " pên.)";
        return s;
    }

    public java.time.LocalDate data() {
        return java.time.LocalDate.ofEpochDay(dia);
    }

    public boolean envolve(String clubeId) {
        return clubeId.equals(mandante) || clubeId.equals(visitante);
    }
}
