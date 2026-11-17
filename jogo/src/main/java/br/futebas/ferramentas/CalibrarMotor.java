package br.futebas.ferramentas;

import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;
import br.futebas.partida.Escalado;
import br.futebas.partida.Formacoes;
import br.futebas.partida.MotorPartida;
import br.futebas.partida.OpcoesPartida;
import br.futebas.partida.ResultadoPartida;
import br.futebas.partida.TimePartida;

import java.util.ArrayList;
import java.util.List;

/**
 * Mede o motor de partida sem o mundo: confrontos de forças fixas (como a tabela 3.8 do
 * futae.md) e uma "liga" de 20 times com forças de Série A. Uso: {@code CalibrarMotor [jogos]}.
 */
public final class CalibrarMotor {
    private CalibrarMotor() {}

    public static TimePartida time(String nome, double forca, Rng rng, double espalhamento) {
        Posicao[] vagas = Formacoes.vagas("4-4-2");
        List<Escalado> t = new ArrayList<>();
        List<Escalado> r = new ArrayList<>();
        int base = Math.abs(nome.hashCode() % 1000) * 100;
        for (int i = 0; i < 11; i++) t.add(new Escalado(base + i, nome + " " + i, vagas[i], vagas[i], forca + rng.gauss(0, espalhamento)));
        Posicao[] banco = {Posicao.GOL, Posicao.ZAG, Posicao.LD, Posicao.VOL, Posicao.MEI, Posicao.PE, Posicao.CA};
        for (int i = 0; i < banco.length; i++) r.add(new Escalado(base + 50 + i, nome + " r" + i, banco[i], banco[i], forca - 4 + rng.gauss(0, espalhamento)));
        return new TimePartida(nome, nome, t, r);
    }

    /** {gols mandante, gols visitante, vitórias, empates, derrotas} médios/percentuais. */
    public static double[] medir(double fm, double fv, int jogos, long semente) {
        Rng rng = new Rng(semente);
        double gm = 0, gv = 0, v = 0, e = 0, d = 0;
        OpcoesPartida op = new OpcoesPartida();
        for (int i = 0; i < jogos; i++) {
            TimePartida tm = time("M", fm, rng, 0), tv = time("V", fv, rng, 0);
            tm.estilo = br.futebas.mundo.Simulador.estiloIA(null, fm + 3, fv, true);
            tv.estilo = br.futebas.mundo.Simulador.estiloIA(null, fv - 3, fm, false);
            ResultadoPartida r = MotorPartida.simular(tm, tv, op, rng);
            gm += r.golsMandante;
            gv += r.golsVisitante;
            if (r.golsMandante > r.golsVisitante) v++;
            else if (r.golsMandante == r.golsVisitante) e++;
            else d++;
        }
        return new double[]{gm / jogos, gv / jogos, 100 * v / jogos, 100 * e / jogos, 100 * d / jogos};
    }

    /** Liga de 20 times com forças entre ~62 e ~78, turno e returno, repetida {@code temporadas} vezes. */
    public static double[] liga(int temporadas, long semente) {
        Rng rng = new Rng(semente);
        double gols = 0, v = 0, e = 0, d = 0, n = 0;
        OpcoesPartida op = new OpcoesPartida();
        for (int t = 0; t < temporadas; t++) {
            double[] f = new double[20];
            for (int i = 0; i < 20; i++) f[i] = 70 + rng.gauss(0, 4.5);
            for (int a = 0; a < 20; a++) {
                for (int b = 0; b < 20; b++) {
                    if (a == b) continue;
                    TimePartida ta = time("A" + a, f[a], rng, 4), tb = time("B" + b, f[b], rng, 4);
                    // a IA escolhe o estilo pela diferença de força, como no mundo
                    ta.estilo = br.futebas.mundo.Simulador.estiloIA(null, f[a] + 3, f[b], true);
                    tb.estilo = br.futebas.mundo.Simulador.estiloIA(null, f[b] - 3, f[a], false);
                    ResultadoPartida r = MotorPartida.simular(ta, tb, op, rng);
                    gols += r.golsMandante + r.golsVisitante;
                    if (r.golsMandante > r.golsVisitante) v++;
                    else if (r.golsMandante == r.golsVisitante) e++;
                    else d++;
                    n++;
                }
            }
        }
        return new double[]{gols / n, 100 * v / n, 100 * e / n, 100 * d / n};
    }

    public static void main(String[] args) {
        int jogos = args.length > 0 ? Integer.parseInt(args[0]) : 4000;
        double[][] casos = {{70, 70}, {75, 70}, {80, 70}, {85, 65}, {60, 80}};
        long t0 = System.nanoTime();
        for (double[] c : casos) {
            double[] m = medir(c[0], c[1], jogos, 7);
            System.out.printf("%.0f x %.0f  gols %.2f x %.2f  total %.2f  V/E/D %.0f%% / %.0f%% / %.0f%%%n",
                    c[0], c[1], m[0], m[1], m[0] + m[1], m[2], m[3], m[4]);
        }
        double[] l = liga(Math.max(1, jogos / 380), 11);
        System.out.printf("Liga: gols/jogo %.2f  mandante %.1f%%  empate %.1f%%  visitante %.1f%%%n", l[0], l[1], l[2], l[3]);
        System.out.printf("Tempo: %.1f ms%n", (System.nanoTime() - t0) / 1e6);
    }
}
