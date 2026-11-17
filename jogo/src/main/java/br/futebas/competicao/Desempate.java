package br.futebas.competicao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ordena uma tabela pela lista de critérios do formato ("pontos", "vitorias", "saldo",
 * "gols_pro", "gols_contra", "gols_fora", "derrotas", "confronto_direto", "cartoes",
 * "sorteio"). Os critérios são aplicados em cascata: cada critério só desempata quem ficou
 * igual em todos os anteriores. O confronto direto monta uma minitabela só com os jogos entre
 * os empatados. O sorteio é determinístico (semente do torneio).
 */
public final class Desempate {
    private Desempate() {}

    public static List<LinhaTabela> ordenar(Collection<LinhaTabela> linhas, List<String> criterios,
                                            List<Partida> jogos, long semente) {
        List<LinhaTabela> l = new ArrayList<>(linhas);
        l.sort(Comparator.comparing(x -> x.clubeId)); // base estável e determinística
        List<String> crit = new ArrayList<>(criterios);
        if (!crit.contains("sorteio")) crit.add("sorteio");
        return cascata(l, crit, 0, jogos, semente);
    }

    private static List<LinhaTabela> cascata(List<LinhaTabela> grupo, List<String> crit, int i, List<Partida> jogos, long semente) {
        if (grupo.size() <= 1 || i >= crit.size()) return grupo;
        String c = crit.get(i);
        double[] chave = new double[grupo.size()];
        for (int k = 0; k < grupo.size(); k++) chave[k] = valor(c, grupo.get(k), grupo, jogos, semente);
        Integer[] idx = new Integer[grupo.size()];
        for (int k = 0; k < idx.length; k++) idx[k] = k;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(chave[b], chave[a])); // maior primeiro (estável)
        List<LinhaTabela> saida = new ArrayList<>();
        int k = 0;
        while (k < idx.length) {
            int j = k;
            List<LinhaTabela> empatados = new ArrayList<>();
            while (j < idx.length && chave[idx[j]] == chave[idx[k]]) {
                empatados.add(grupo.get(idx[j]));
                j++;
            }
            saida.addAll(cascata(empatados, crit, i + 1, jogos, semente));
            k = j;
        }
        return saida;
    }

    /** Valor do critério (maior é melhor). Critérios desconhecidos não desempatam. */
    static double valor(String c, LinhaTabela t, List<LinhaTabela> grupo, List<Partida> jogos, long semente) {
        return switch (c) {
            case "pontos" -> t.pontos;
            case "vitorias" -> t.vitorias;
            case "saldo" -> t.saldo();
            case "gols_pro" -> t.golsPro;
            case "gols_contra" -> -t.golsContra;
            case "gols_fora" -> t.golsFora;
            case "derrotas" -> -t.derrotas;
            case "empates" -> t.empates;
            case "cartoes" -> -t.cartoes;
            case "aproveitamento" -> t.aproveitamento();
            case "confronto_direto" -> confrontoDireto(t, grupo, jogos);
            case "sorteio" -> sorteio(t.clubeId, semente);
            default -> 0;
        };
    }

    private static double confrontoDireto(LinhaTabela t, List<LinhaTabela> grupo, List<Partida> jogos) {
        if (jogos == null) return 0;
        Set<String> ids = new HashSet<>();
        for (LinhaTabela g : grupo) ids.add(g.clubeId);
        int pts = 0, saldo = 0, gols = 0;
        for (Partida p : jogos) {
            if (!p.jogada || p.golsMandante == null) continue;
            if (!ids.contains(p.mandante) || !ids.contains(p.visitante)) continue;
            if (p.mandante.equals(t.clubeId)) {
                pts += p.golsMandante > p.golsVisitante ? 3 : p.golsMandante.equals(p.golsVisitante) ? 1 : 0;
                saldo += p.golsMandante - p.golsVisitante;
                gols += p.golsMandante;
            } else if (p.visitante.equals(t.clubeId)) {
                pts += p.golsVisitante > p.golsMandante ? 3 : p.golsMandante.equals(p.golsVisitante) ? 1 : 0;
                saldo += p.golsVisitante - p.golsMandante;
                gols += p.golsVisitante;
            }
        }
        return pts * 1_000_000.0 + (saldo + 500) * 1000.0 + gols;
    }

    static double sorteio(String clubeId, long semente) {
        long h = semente * 0x9E3779B97F4A7C15L;
        for (int i = 0; i < clubeId.length(); i++) h = (h ^ clubeId.charAt(i)) * 0x100000001B3L;
        h ^= (h >>> 29);
        return (h >>> 11) * 0x1.0p-53;
    }
}
