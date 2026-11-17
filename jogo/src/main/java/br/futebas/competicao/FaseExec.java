package br.futebas.competicao;

import br.futebas.dados.Formato;

import java.util.ArrayList;
import java.util.List;

/** Uma fase de um torneio em andamento. */
public class FaseExec {
    public int indice;
    public String nome;
    /** pontos_corridos | grupos | mata_mata (quadrangular vira grupos de 4; especial cai no mais próximo). */
    public String tipo;
    public Formato.Fase def;
    public int entrantes;
    /** Liga suíça com play-off: quantos vão direto à fase seguinte ao play-off (top N). */
    public int diretos;
    /** Clubes que estreiam nesta fase (entradas escalonadas); os demais vêm da fase anterior. */
    public List<String> estreantes = new ArrayList<>();
    public int saida;
    public int turnos = 1;
    public int numGrupos = 1;
    /** Grupos: classificados por grupo e melhores terceiros (Copa de 48). */
    public int porGrupo = 1, terceiros;
    /** liga_suica: jogos de cada clube. */
    public int jogosLiga;
    public int dataInicial, numDatas;
    public boolean iniciada, concluida;
    public List<String> clubes = new ArrayList<>();
    public List<List<String>> grupos = new ArrayList<>();
    /** Partidas da fase (as mesmas de Torneio.partidas; não vão para o arquivo salvo). */
    public transient List<Partida> partidas = new ArrayList<>();
    // mata-mata
    public List<String> vivos = new ArrayList<>();
    public List<Confronto> confrontos = new ArrayList<>();
    public int rodadasMata;
    public int pernas = 1;
    /** Classificados para a fase seguinte, do melhor para o pior. */
    public List<String> classificados = new ArrayList<>();

    public FaseExec() {}

    public boolean mataMata() { return "mata_mata".equals(tipo); }
}
