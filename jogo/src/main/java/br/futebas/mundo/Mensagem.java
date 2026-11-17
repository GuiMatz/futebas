package br.futebas.mundo;

/** Mensagem curta da caixa de entrada do técnico (estilo Brasfoot). */
public class Mensagem {
    public int ano, semana;
    /** jogador | diretoria | torcida | mercado | proposta | resultado | mundo | kings | financas */
    public String tipo;
    public String titulo;
    public String texto;
    public boolean lida;
    /** Id de uma proposta ligada à mensagem (aceitar/recusar), se houver. */
    public Integer propostaId;

    public Mensagem() {}

    public Mensagem(int ano, int semana, String tipo, String titulo, String texto) {
        this.ano = ano;
        this.semana = semana;
        this.tipo = tipo;
        this.titulo = titulo;
        this.texto = texto;
    }
}
