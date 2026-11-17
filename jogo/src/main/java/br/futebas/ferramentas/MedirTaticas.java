package br.futebas.ferramentas;

import br.futebas.Balanceamento;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;
import br.futebas.partida.Concentracao;
import br.futebas.partida.Escalado;
import br.futebas.partida.Estilo;
import br.futebas.partida.Marcacao;
import br.futebas.partida.MotorPartida;
import br.futebas.partida.OpcoesPartida;
import br.futebas.partida.ResultadoPartida;
import br.futebas.partida.TimePartida;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Mede cada alavanca tática em muitos jogos (time A, força 70, contra B, força 70, campo neutro
 * e mando alternado): gols pró/contra, pontos por jogo, cartões e faltas.
 * <pre>uso: MedirTaticas [jogos por caso, padrão 20000]</pre>
 */
public final class MedirTaticas {
    private MedirTaticas() {}

    /** Resultado médio do lado A. */
    public record Medida(double gp, double gc, double pontos, double amarelos, double vermelhos, double faltas, double cabeca) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "gols %.2f x %.2f | pts/jogo %.3f | amarelos %.2f | vermelhos %.3f | faltas %.1f | gols pelos lados %.0f%%",
                    gp, gc, pontos, amarelos, vermelhos, faltas, 100 * cabeca);
        }
    }

    /**
     * Joga {@code jogos} partidas de A contra B (A em casa na metade delas).
     *
     * @param ajusteA ajusta o time A (tática, forças) antes de cada jogo
     * @param ajusteB idem para B
     */
    public static Medida medir(int jogos, long semente, double forcaA, double forcaB, Consumer<TimePartida> ajusteA, Consumer<TimePartida> ajusteB) {
        Rng rng = new Rng(semente);
        OpcoesPartida op = new OpcoesPartida();
        double gp = 0, gc = 0, pts = 0, am = 0, vm = 0, fa = 0, cab = 0, golsTotal = 0;
        for (int i = 0; i < jogos; i++) {
            TimePartida a = CalibrarMotor.time("A", forcaA, rng, 0), b = CalibrarMotor.time("B", forcaB, rng, 0);
            if (ajusteA != null) ajusteA.accept(a);
            if (ajusteB != null) ajusteB.accept(b);
            boolean casa = i % 2 == 0;
            ResultadoPartida r = casa ? MotorPartida.simular(a, b, op, rng) : MotorPartida.simular(b, a, op, rng);
            int la = casa ? 0 : 1;
            int g1 = casa ? r.golsMandante : r.golsVisitante, g2 = casa ? r.golsVisitante : r.golsMandante;
            gp += g1;
            gc += g2;
            pts += g1 > g2 ? 3 : g1 == g2 ? 1 : 0;
            for (int[] x : r.amarelos) if (x[0] == la) am++;
            for (int[] x : r.vermelhos) if (x[0] == la) vm++;
            fa += r.faltas[la];
            java.util.Set<Integer> lados = new java.util.HashSet<>();
            for (Escalado e : a.titulares) if (e.vaga == Posicao.LE || e.vaga == Posicao.LD || e.vaga == Posicao.PE || e.vaga == Posicao.PD) lados.add(e.id);
            for (int[] g : r.gols) {
                if (g[0] != la || g[4] == 1) continue;
                golsTotal++;
                if (lados.contains(g[1])) cab++;
            }
        }
        return new Medida(gp / jogos, gc / jogos, pts / jogos, am / jogos, vm / jogos, fa / jogos, cab / Math.max(1, golsTotal));
    }

    /** Enfraquece (delta < 0) os jogadores de A nas vagas dadas. */
    public static Consumer<TimePartida> setor(double delta, Posicao... vagas) {
        return t -> {
            for (int i = 0; i < t.titulares.size(); i++) {
                Escalado e = t.titulares.get(i);
                for (Posicao v : vagas) {
                    if (e.vaga == v) {
                        Escalado n = new Escalado(e.id, e.nome, e.natural, e.vaga, e.forca + delta);
                        t.titulares.set(i, n);
                    }
                }
            }
        };
    }

    /** Multiplica a força de todos (moral/energia) e define a energia inicial. */
    public static Consumer<TimePartida> condicao(double fator, double energia) {
        return t -> {
            for (int i = 0; i < t.titulares.size(); i++) {
                Escalado e = t.titulares.get(i);
                Escalado n = new Escalado(e.id, e.nome, e.natural, e.vaga, e.forca * fator);
                n.energia = energia;
                t.titulares.set(i, n);
            }
        };
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 20000;
        long t0 = System.nanoTime();
        System.out.println("== Estilo de A (B equilibrado), forças iguais 70");
        for (Estilo e : Estilo.values()) System.out.println(pad(e.nome) + medir(n, 1, 70, 70, t -> t.estilo = e, null));
        System.out.println("== Estilo de A sendo mais fraco (64 x 72, B equilibrado)");
        for (Estilo e : Estilo.values()) System.out.println(pad(e.nome) + medir(n, 2, 64, 72, t -> t.estilo = e, null));
        System.out.println("== Marcação de A (B normal), forças iguais 70");
        for (Marcacao mc : Marcacao.values()) System.out.println(pad(mc.nome) + medir(n, 3, 70, 70, t -> t.marcacao = mc, null));
        System.out.println("== Marcação de A sendo mais fraco (64 x 72)");
        for (Marcacao mc : Marcacao.values()) System.out.println(pad(mc.nome) + medir(n, 4, 64, 72, t -> t.marcacao = mc, null));
        System.out.println("== Concentrar (B com laterais fracos: LE/LD −10)");
        for (Concentracao c : Concentracao.values())
            System.out.println(pad(c.nome) + medir(n, 5, 70, 70, t -> t.concentracao = c, setor(-10, Posicao.LE, Posicao.LD)));
        System.out.println("== Concentrar (B com zaga fraca: ZAG/VOL −10)");
        for (Concentracao c : Concentracao.values())
            System.out.println(pad(c.nome) + medir(n, 6, 70, 70, t -> t.concentracao = c, setor(-10, Posicao.ZAG, Posicao.VOL)));
        System.out.println("== Concentrar (B normal)");
        for (Concentracao c : Concentracao.values()) System.out.println(pad(c.nome) + medir(n, 7, 70, 70, t -> t.concentracao = c, null));
        System.out.println("== Moral de A (força do dia), B moral neutra");
        for (int moral : new int[]{20, 45, 65, 85, 100}) {
            double f = 1 + Balanceamento.MORAL_PESO * (moral - Balanceamento.MORAL_NEUTRA) / 100.0;
            System.out.println(pad("moral " + moral) + medir(n, 8, 70, 70, condicao(f, 100), null));
        }
        System.out.println("== Energia de A ao começar, B 100");
        for (int en : new int[]{100, 80, 60, 40}) {
            double f = 1 - Balanceamento.ENERGIA_PESO * (100 - en) / 100.0;
            System.out.println(pad("energia " + en) + medir(n, 9, 70, 70, condicao(f, en), null));
        }
        System.out.printf("Tempo: %.1f s%n", (System.nanoTime() - t0) / 1e9);
    }

    private static String pad(String s) {
        return String.format("  %-16s", s);
    }
}
