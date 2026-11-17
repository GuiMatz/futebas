package br.futebas.partida;

import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Disputa de pênaltis (port de {@code penalty_shootout_kicks}), com a regra real: 5 cobranças
 * alternadas (acaba antes quando um time não alcança mais o outro), depois alternadas até um errar e o
 * outro acertar. Batem só os que terminaram o jogo em campo, na ordem escolhida (pelo técnico, ou a
 * automática); nas alternadas, ninguém repete antes de todos do time terem batido. O time com mais
 * jogadores em campo usa só tantos quanto o adversário tem (como na regra).
 */
public final class Penaltis {
    private Penaltis() {}

    static final double CONVERSAO = 0.72;
    static final double MORTE_SUBITA = 0.10;

    /**
     * Uma cobrança da disputa. {@code jeito}: como foi ("angulo", "canto", "deslocou", "meio", "cavadinha";
     * "defendeu", "milagre", "isolou", "trave", "fora"); {@code goleiro}: quem estava no gol (para o suspense na tela).
     */
    public record Cobranca(int lado, int jogadorId, String nome, boolean gol, int placarM, int placarV, String texto,
                           String jeito, String goleiro) {
        /** Texto do lance na narração: "jeito:goleiro" (a tela monta o suspense com ele). */
        public String detalheLance() { return jeito + ":" + (goleiro == null ? "" : goleiro); }
    }

    public record Disputa(int golsMandante, int golsVisitante, List<Cobranca> cobrancas) {}

    /** Quem pode bater: quem terminou o jogo em campo (sem o jogo jogado, os titulares). */
    public static List<Escalado> candidatos(TimePartida t) {
        return new ArrayList<>(t.emCampoFim != null && !t.emCampoFim.isEmpty() ? t.emCampoFim : t.titulares);
    }

    /**
     * A ordem automática (sugestão para o técnico e a ordem da IA): o cobrador de pênalti do time
     * primeiro, depois do mais forte ao mais fraco.
     */
    public static List<Escalado> ordemAutomatica(TimePartida t) {
        List<Escalado> l = candidatos(t);
        l.sort(Comparator.comparingDouble((Escalado e) -> -e.forca).thenComparingInt(e -> e.id));
        Integer escolhido = t.cobradorPenalti;
        if (escolhido != null) {
            for (int i = 0; i < l.size(); i++) {
                if (l.get(i).id == escolhido) {
                    l.add(0, l.remove(i));
                    break;
                }
            }
        }
        return l;
    }

    /**
     * A ordem de um lado: os ids escolhidos ({@code ids}, na ordem; quem não está em campo é ignorado)
     * e depois os que faltaram, na ordem automática. {@code ids} null = automática.
     */
    public static List<Escalado> ordem(TimePartida t, List<Integer> ids) {
        List<Escalado> auto = ordemAutomatica(t);
        if (ids == null || ids.isEmpty()) return auto;
        List<Escalado> l = new ArrayList<>();
        Set<Integer> usados = new LinkedHashSet<>();
        for (Integer id : ids) {
            if (id == null || !usados.add(id)) continue;
            for (Escalado e : auto) if (e.id == id) l.add(e);
        }
        for (Escalado e : auto) if (!usados.contains(e.id)) l.add(e);
        return l;
    }

    /** Ordem automática nos dois lados (a mesma semente dá o mesmo resultado). */
    public static Disputa disputar(TimePartida m, TimePartida v, Rng rng, boolean narrar) {
        return disputar(m, v, rng, narrar, null, null);
    }

    /** {@code ordemM}/{@code ordemV}: ids dos batedores na ordem escolhida (null = automática). */
    public static Disputa disputar(TimePartida m, TimePartida v, Rng rng, boolean narrar, List<Integer> ordemM, List<Integer> ordemV) {
        double am = PerfilTime.de(m.titulares, null).ataque;
        double av = PerfilTime.de(v.titulares, null).ataque;
        double[] prob = {
            Math.min(CONVERSAO + 0.10, Math.max(CONVERSAO - 0.10, CONVERSAO + (am - av) * 0.002)),
            Math.min(CONVERSAO + 0.10, Math.max(CONVERSAO - 0.10, CONVERSAO + (av - am) * 0.002))
        };
        TimePartida[] t = {m, v};
        List<List<Escalado>> batedores = new ArrayList<>(List.of(ordem(m, ordemM), ordem(v, ordemV)));
        // regra: o time com mais jogadores em campo reduz ao número do adversário (sai quem bateria por último)
        int n = Math.max(1, Math.min(batedores.get(0).size(), batedores.get(1).size()));
        for (int s = 0; s < 2; s++) if (batedores.get(s).size() > n) batedores.set(s, new ArrayList<>(batedores.get(s).subList(0, n)));
        String[] goleiros = new String[2];
        for (int s = 0; s < 2; s++) {
            for (Escalado e : candidatos(t[s])) if (e.vaga == Posicao.GOL && goleiros[s] == null) goleiros[s] = e.nome;
            if (goleiros[s] == null) for (Escalado e : t[s].titulares) if (e.vaga == Posicao.GOL) goleiros[s] = e.nome;
        }
        int[] placar = new int[2];
        List<Cobranca> cob = new ArrayList<>();
        for (int rodada = 0; rodada < 5; rodada++) {
            for (int s = 0; s < 2; s++) {
                cobrar(s, batedor(batedores.get(s), rodada), prob[s], placar, cob, rng, narrar, t, goleiros);
                int restamM = 5 - rodada - 1;
                int restamV = 5 - rodada - (s == 1 ? 1 : 0);
                if (placar[0] > placar[1] + restamV || placar[1] > placar[0] + restamM) {
                    return new Disputa(placar[0], placar[1], cob);
                }
            }
        }
        int rodada = 5;
        while (placar[0] == placar[1] && rodada < 60) {
            for (int s = 0; s < 2; s++) {
                cobrar(s, batedor(batedores.get(s), rodada), prob[s], placar, cob, rng, narrar, t, goleiros);
            }
            rodada++;
        }
        if (placar[0] == placar[1]) placar[rng.inteiro(2)]++; // segurança: nunca termina empatado
        return new Disputa(placar[0], placar[1], cob);
    }

    /** O batedor da rodada: a lista em ciclo (ninguém repete antes de todos baterem). */
    private static Escalado batedor(List<Escalado> l, int rodada) {
        return l.get(rodada % l.size());
    }

    private static void cobrar(int s, Escalado b, double base, int[] placar, List<Cobranca> cob, Rng rng, boolean narrar,
                               TimePartida[] t, String[] goleiros) {
        double p = base;
        if (cob.size() >= 10) p -= MORTE_SUBITA;
        p = Math.min(0.90, Math.max(0.50, p + (b.forca - 75) * 0.001));
        boolean gol = rng.real() < p;
        if (gol) placar[s]++;
        String texto = null;
        String jeito = Narracao.jeitoPenalti(gol, rng);
        if (narrar) {
            texto = (gol ? "Converteu: " : "Perdeu: ") + b.nome + " (" + t[s].nome + ") "
                    + Narracao.frasePenalti(jeito, goleiros[1 - s]) + ". Pênaltis: " + placar[0] + " x " + placar[1] + ".";
        }
        cob.add(new Cobranca(s, b.id, b.nome, gol, placar[0], placar[1], texto, jeito, goleiros[1 - s]));
    }
}
