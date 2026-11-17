package br.futebas.mundo;

/** Leilão de jogador (Brasfoot): lance inicial, lances da IA e do humano, fim numa semana. */
public class Leilao {
    public int id;
    public int jogadorId;
    public String vendedorId;
    public long lanceMinimo;
    public long maiorLance;
    public String compradorId;
    /** Semana absoluta em que o leilão termina. */
    public int fim;
    /** aberto | vendido | sem_lances */
    public String estado = "aberto";

    public Leilao() {}
}
