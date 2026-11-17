package br.futebas.mundo;

import java.util.ArrayList;
import java.util.List;

/** Seleção nacional: convocação automática dos melhores do país e força (Elo) dinâmica. */
public class Selecao {
    /** Id usado nas competições: {@code "sel-" + pais}. */
    public String id;
    public String pais;
    public String nome;
    public String confederacao;
    public String tecnico;
    /** Técnico da IA da seleção (ver {@link Treinador}). */
    public Integer treinadorId;
    public Integer rankingFifaInicial;
    /** Força Elo (sobe e desce com os resultados). */
    public double elo = 1500;
    /** Convocados atuais (ids do mundo). */
    public List<Integer> convocados = new ArrayList<>();
    /** Força média dos convocados (sem jogadores no mundo: estimada pelo Elo). */
    public double forca;
    /**
     * Força de referência (2026: a dos melhores reais do país, ou pelo ranking FIFA): a peneira
     * puxa as gerações novas do país para perto dela, para a força das seleções acompanhar o
     * mundo real em vez de derivar (0 = ainda não calculada).
     */
    public double forcaReferencia;
    public boolean humano;
    public int jogos, vitorias, empates, derrotas;
    public List<String> titulos = new ArrayList<>();

    public Selecao() {}

    public static String id(String pais) { return "sel-" + pais; }
}
