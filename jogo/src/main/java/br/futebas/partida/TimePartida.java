package br.futebas.partida;

import java.util.ArrayList;
import java.util.List;

/** Um lado da partida: 11 titulares, reservas, estilo e cobradores. */
public final class TimePartida {
    public final String clubeId;
    public final String nome;
    public final List<Escalado> titulares;
    public final List<Escalado> reservas;
    public Estilo estilo = Estilo.EQUILIBRADO;
    public Marcacao marcacao = Marcacao.NORMAL;
    public Concentracao concentracao = Concentracao.EQUILIBRADO;
    public Integer capitao, cobradorPenalti, cobradorFalta;
    /** Quem terminou o jogo em campo (o motor preenche no apito final; null antes). Os batedores da disputa de pênaltis. */
    public List<Escalado> emCampoFim;

    public TimePartida(String clubeId, String nome, List<Escalado> titulares, List<Escalado> reservas) {
        this.clubeId = clubeId;
        this.nome = nome;
        this.titulares = new ArrayList<>(titulares);
        this.reservas = new ArrayList<>(reservas == null ? List.of() : reservas);
    }
}
