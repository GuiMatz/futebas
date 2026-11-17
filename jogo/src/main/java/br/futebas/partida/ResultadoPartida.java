package br.futebas.partida;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resultado de uma partida, com gols, lances (se narrada), notas e estatísticas. */
public final class ResultadoPartida {
    public String mandante, visitante;
    public String mandanteId, visitanteId;
    public int golsMandante, golsVisitante;
    public double xgMandante, xgVisitante;
    public boolean prorrogacao;
    /** Pênaltis da disputa (null se não houve). */
    public Integer penaltisMandante, penaltisVisitante;
    public List<Lance> lances = new ArrayList<>();
    /** Gols: {lado, jogadorId (autor; no gol contra, o defensor), minuto, assistenteId ou 0, contra 0/1}. */
    public List<int[]> gols = new ArrayList<>();
    public int[] posse = {50, 50};
    public int[] chutes = new int[2];
    public int[] noGol = new int[2];
    /** Eventos resumidos (gol, cartão, pênalti, lesão) em ordem de minuto, de todo jogo. */
    public List<EventoJogo> eventos = new ArrayList<>();
    /** Lesões na partida: {lado, jogadorId, minuto}. */
    public List<int[]> lesoes = new ArrayList<>();
    /** Faltas cometidas por lado (a marcação pesada faz mais). */
    public int[] faltas = new int[2];
    /** Escanteios e impedimentos por lado (só estatística: calculados no fim, fora do sorteio do jogo). */
    public int[] escanteios = new int[2];
    public int[] impedimentos = new int[2];
    /** Amarelos: {lado, jogadorId}. */
    public List<int[]> amarelos = new ArrayList<>();
    /** Expulsões: {lado, jogadorId}. */
    public List<int[]> vermelhos = new ArrayList<>();
    /** Nota de 0 a 10 por jogador (os dois lados). */
    public Map<Integer, Double> notas = new LinkedHashMap<>();
    /** Minutos jogados por jogador. */
    public Map<Integer, Integer> minutos = new LinkedHashMap<>();
    /** Energia (0–100) de cada um que jogou, no apito final: é a que ele leva para a semana. */
    public Map<Integer, Integer> energia = new LinkedHashMap<>();
    public Integer melhorId;
    public String melhorNome;
    public List<String> destaques = new ArrayList<>();
    public String comentario;

    /** Ids dos que se machucaram na partida. */
    public java.util.Set<Integer> lesoesIds() {
        java.util.Set<Integer> s = new java.util.HashSet<>();
        for (int[] x : lesoes) s.add(x[1]);
        return s;
    }

    /** 0 mandante, 1 visitante, -1 empate (considera pênaltis). */
    public int vencedorLado() {
        if (golsMandante != golsVisitante) return golsMandante > golsVisitante ? 0 : 1;
        if (penaltisMandante != null) return penaltisMandante > penaltisVisitante ? 0 : 1;
        return -1;
    }

    public String placar() {
        String s = mandante + " " + golsMandante + " x " + golsVisitante + " " + visitante;
        if (penaltisMandante != null) s += " (pênaltis: " + penaltisMandante + " x " + penaltisVisitante + ")";
        return s;
    }
}
