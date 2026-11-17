package br.futebas.competicao;

import java.util.ArrayList;
import java.util.List;

/** Um confronto de mata-mata (jogo único ou ida e volta). {@code a} é o mais bem colocado. */
public class Confronto {
    public int rodada;
    public String a, b;
    public List<Integer> partidas = new ArrayList<>();
    public String vencedor;
    public String perdedor;

    public Confronto() {}

    public Confronto(int rodada, String a, String b) {
        this.rodada = rodada;
        this.a = a;
        this.b = b;
    }
}
