package br.futebas.mundo;

/** Uma temporada da carreira simulada de um jogador (histórico permanente). */
public class Passagem {
    public int ano;
    public String clubeId;
    public String clubeNome;
    public int jogos, gols, assistencias;
    public double notaMedia;
    public int forca;
    public boolean emprestimo;
    /** Passagem resumida (aposentado arquivado): várias temporadas seguidas no mesmo clube, até este ano (0 = só {@link #ano}). */
    public int anoFim;

    public Passagem() {}

    public Passagem(int ano, String clubeId, String clubeNome, int jogos, int gols, int assistencias, double nota, int forca) {
        this.ano = ano;
        this.clubeId = clubeId;
        this.clubeNome = clubeNome;
        this.jogos = jogos;
        this.gols = gols;
        this.assistencias = assistencias;
        this.notaMedia = nota;
        this.forca = forca;
    }
}
