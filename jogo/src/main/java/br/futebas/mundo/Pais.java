package br.futebas.mundo;

/** Um país do mundo: calendário próprio (jan–dez ou ago–mai) e nível de detalhe da simulação. */
public class Pais {
    public String id;
    public String nome;
    public String confederacao;
    /** 'ano' (jan–dez) | 'europeu' (ago–mai). */
    public String calendario = "ano";
    public Integer rankingFifa;
    public String cor1, cor2;
    /** Simulação lance a lance (true) ou rápida por força do time (false). */
    public boolean detalhado = true;

    public Pais() {}

    public boolean europeu() { return "europeu".equals(calendario); }
}
