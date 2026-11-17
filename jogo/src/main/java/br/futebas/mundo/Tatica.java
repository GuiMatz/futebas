package br.futebas.mundo;

import br.futebas.partida.Concentracao;
import br.futebas.partida.Estilo;
import br.futebas.partida.Marcacao;

import java.util.ArrayList;
import java.util.List;

/** Tática do clube: formação, estilo, escalação manual e cobradores (poucas alavancas). */
public class Tatica {
    public String formacao = "4-4-2";
    public Estilo estilo = Estilo.EQUILIBRADO;
    /** Saves antigos não têm estes campos: ficam null e valem NORMAL / EQUILIBRADO. */
    public Marcacao marcacao = Marcacao.NORMAL;
    public Concentracao concentracao = Concentracao.EQUILIBRADO;
    /** Escalação manual (11 ids na ordem das vagas da formação); vazia = automática. */
    public List<Integer> titulares = new ArrayList<>();
    public List<Integer> reservas = new ArrayList<>();
    public Integer capitao, cobradorPenalti, cobradorFalta;
    /**
     * Caixa "Sub-20" da escalação (regra do jogo, 13/11/2026): marcada, o time Sub-20 aparece nos não
     * relacionados e entra na escalação automática; desmarcada (padrão), o Sub-20 fica de fora.
     */
    public boolean usarSub20;

    public Tatica() {}

    public Marcacao marcacao() { return marcacao == null ? Marcacao.NORMAL : marcacao; }

    public Concentracao concentracao() { return concentracao == null ? Concentracao.EQUILIBRADO : concentracao; }
}
