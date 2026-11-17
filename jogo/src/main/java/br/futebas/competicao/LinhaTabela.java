package br.futebas.competicao;

/** Uma linha de tabela de classificação. */
public class LinhaTabela {
    public String clubeId;
    public int jogos, vitorias, empates, derrotas, golsPro, golsContra, pontos, cartoes, golsFora;
    /** Das vitórias/derrotas, quantas foram nos shootouts/pênaltis (competições sem empate, como a Kings). */
    public int vitoriasPenaltis, derrotasPenaltis;

    public LinhaTabela() {}

    public LinhaTabela(String clubeId) {
        this.clubeId = clubeId;
    }

    public int saldo() { return golsPro - golsContra; }

    public double aproveitamento() { return jogos == 0 ? 0 : 100.0 * pontos / (3.0 * jogos); }

    public void registrar(int pro, int contra, boolean fora, int pontosVitoria, int cartoesNoJogo) {
        jogos++;
        golsPro += pro;
        golsContra += contra;
        if (fora) golsFora += pro;
        cartoes += cartoesNoJogo;
        if (pro > contra) {
            vitorias++;
            pontos += pontosVitoria;
        } else if (pro == contra) {
            empates++;
            pontos += 1;
        } else {
            derrotas++;
        }
    }
}
