package br.futebas.mundo;

import java.util.ArrayList;
import java.util.List;

/**
 * Técnico da IA (clubes e seleções): os reais do banco ({@code tecnicos}), ex-jogadores que
 * viraram técnicos e gerados quando faltar gente no mercado. O técnico humano é o {@link Tecnico}.
 */
public class Treinador {
    public int id;
    /** Id na tabela {@code tecnicos} do banco (reais). */
    public Integer dbId;
    public String nome;
    public String pais;
    public int anoNasc;
    /** "real", "ex_jogador" ou "gerado". */
    public String origem = "gerado";
    /** Ex-jogador: o id dele como jogador. */
    public Integer jogadorId;
    /** Emprego atual (no máximo um dos dois). */
    public String clubeId;
    public String selecaoPais;
    /** Qualidade (escala da força dos jogadores): define que clube o contrata. */
    public double avaliacao;
    public double reputacao;
    public int jogos, vitorias, empates, derrotas, demissoes;
    public List<String> titulos = new ArrayList<>();
    /** Passagens: "2026–2028 Flamengo". */
    public List<String> historico = new ArrayList<>();
    /** Ano em que assumiu o emprego atual; e o último ano em que trabalhou. */
    public int desde, ultimoAnoEmpregado;
    public boolean aposentado;

    public Treinador() {}

    public int idade(int ano) {
        return ano - anoNasc;
    }

    public boolean livre() {
        return !aposentado && clubeId == null && selecaoPais == null;
    }

    public double aproveitamento() {
        return jogos == 0 ? 0 : (3.0 * vitorias + empates) / (3.0 * jogos);
    }
}
