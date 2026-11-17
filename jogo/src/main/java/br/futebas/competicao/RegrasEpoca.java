package br.futebas.competicao;

import br.futebas.dados.Db;

import java.util.List;

/**
 * As regras do jogo valendo num ano (tabela {@code regras}): cada regra vale a partir da data
 * da mudança. Âmbito: 'mundo', o país (ISO-3) ou o id de uma competição (o mais específico vence).
 * Sem linha no banco, usa o padrão da época (com aviso, para o relatório de cobertura).
 */
public class RegrasEpoca {
    public int ano;
    public int pontosVitoria = 3;
    public int substituicoes = 3;
    /** Reservas relacionados no banco (regra "reservas" do banco; 0 = padrão do país/competição, ver {@link #reservas(Torneio)}). */
    public int reservas;
    public boolean golDeOuro;
    public boolean var;
    public boolean cartoes = true;
    public boolean recuoGoleiroMao;
    public boolean profissionalismo = true;
    public String janelaTransferencias;

    public RegrasEpoca() {}

    /**
     * Reservas no banco: a regra do banco ou, sem ela, o regulamento de 2026 por país/confederação
     * (Brasil, Itália, Espanha, Portugal, Argentina e continentais: 12; Inglaterra, Alemanha, França,
     * EUA e o resto: 9). Nunca menos que as substituições.
     */
    public int reservas(Torneio t) {
        if (reservas > 0) return Math.max(reservas, substituicoes);
        String pais = t == null ? null : t.pais;
        int r;
        if (t != null && ("continental".equals(t.ambito) || "selecoes".equals(t.ambito))) r = 12;
        else r = pais == null ? 9 : switch (pais) {
            case "BRA", "ITA", "ESP", "PRT", "ARG", "URY", "CHL", "COL", "MEX", "NLD", "BEL", "TUR" -> 12;
            default -> 9;
        };
        return Math.max(r, substituicoes);
    }

    public static RegrasEpoca resolver(List<Db.Regra> regras, int ano, String pais, String competicaoId) {
        RegrasEpoca r = new RegrasEpoca();
        r.ano = ano;
        // padrões da época (usados só se o banco não tiver a regra)
        r.pontosVitoria = ano >= 1995 ? 3 : 2;
        r.substituicoes = ano >= 2020 ? 5 : ano >= 1995 ? 3 : 2;
        r.var = false;
        r.cartoes = ano >= 1970;
        r.recuoGoleiroMao = ano < 1992;
        String limite = ano + "-07-01"; // a regra vale na temporada se mudou até o meio do ano
        for (String nome : new String[]{"reservas", "pontos_vitoria", "substituicoes", "gol_de_ouro", "var", "cartoes",
                "recuo_goleiro_mao", "profissionalismo", "janela_transferencias"}) {
            String valor = valor(regras, nome, limite, pais, competicaoId);
            if (valor == null) continue;
            switch (nome) {
                case "pontos_vitoria" -> r.pontosVitoria = inteiro(valor, r.pontosVitoria);
                case "substituicoes" -> r.substituicoes = inteiro(valor, r.substituicoes);
                case "reservas" -> r.reservas = inteiro(valor, r.reservas);
                case "gol_de_ouro" -> r.golDeOuro = verdade(valor);
                case "var" -> r.var = verdade(valor);
                case "cartoes" -> r.cartoes = verdade(valor);
                case "recuo_goleiro_mao" -> r.recuoGoleiroMao = verdade(valor);
                case "profissionalismo" -> r.profissionalismo = verdade(valor);
                case "janela_transferencias" -> r.janelaTransferencias = valor;
                default -> { }
            }
        }
        return r;
    }

    private static String valor(List<Db.Regra> regras, String nome, String limite, String pais, String comp) {
        String melhor = null;
        int melhorNivel = -1;
        String melhorData = "";
        // Se o país (ou a competição) tem a sua própria data para esta regra, a data mundial não
        // vale para ele: ex.: VAR no mundo em 2018, mas no Brasileirão só em 2019.
        int nivelMinimo = 0;
        for (Db.Regra g : regras) {
            if (g.regra() == null || !g.regra().equals(nome)) continue;
            String amb = g.ambito() == null ? "mundo" : g.ambito();
            if (comp != null && amb.equals(comp)) nivelMinimo = Math.max(nivelMinimo, 2);
            else if (pais != null && amb.equalsIgnoreCase(pais)) nivelMinimo = Math.max(nivelMinimo, 1);
        }
        for (Db.Regra g : regras) {
            if (g.regra() == null || !g.regra().equals(nome)) continue;
            String de = g.validoDe() == null ? "0000" : g.validoDe();
            if (de.length() == 4) de = de + "-01-01";
            if (de.compareTo(limite) > 0) continue;
            String amb = g.ambito() == null ? "mundo" : g.ambito();
            int nivel;
            if (comp != null && amb.equals(comp)) nivel = 2;
            else if (pais != null && amb.equalsIgnoreCase(pais)) nivel = 1;
            else if (amb.equals("mundo")) nivel = 0;
            else continue;
            if (nivel < nivelMinimo) continue;
            if (nivel > melhorNivel || (nivel == melhorNivel && de.compareTo(melhorData) >= 0)) {
                melhorNivel = nivel;
                melhorData = de;
                melhor = g.valor();
            }
        }
        return melhor;
    }

    private static int inteiro(String v, int padrao) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private static boolean verdade(String v) {
        String s = v.trim().toLowerCase();
        return s.equals("1") || s.equals("true") || s.equals("sim");
    }
}
