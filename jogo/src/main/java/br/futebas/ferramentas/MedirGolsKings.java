package br.futebas.ferramentas;

import br.futebas.kings.LigaKings;
import br.futebas.kings.MotorKings;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.ResultadoPartida;

import java.util.Locale;

/**
 * Gols por jogo da Kings e gols por minuto pelo número de jogadores em campo (por lado): rampa
 * (1v1 → 5v5), 7v7, dado (3v3/2v2/1v1) e match ball (5v5 → 1v1). Cartões e sanções não entram na
 * conta do "n" (o minuto conta pela fase). uso: MedirGolsKings [jogos] [forçaA] [forçaB]
 */
public final class MedirGolsKings {
    static LigaKings.TimeKings time(String id, double forca) {
        LigaKings.TimeKings t = new LigaKings.TimeKings();
        t.id = id;
        t.nome = "Time " + id;
        t.presidente = "Pres " + id;
        Posicao[] p = {Posicao.GOL, Posicao.ZAG, Posicao.ZAG, Posicao.MC, Posicao.MC, Posicao.PE, Posicao.CA, Posicao.CA, Posicao.LD, Posicao.MEI, Posicao.VOL, Posicao.GOL};
        for (int i = 0; i < p.length; i++) {
            LigaKings.AtletaKings a = new LigaKings.AtletaKings();
            a.jogadorId = Math.abs(id.hashCode() % 1000) * 100 + i;
            a.nome = id + " " + i;
            a.forca = forca;
            a.posicao = p[i];
            t.elenco.add(a);
        }
        return t;
    }

    public static void main(String[] args) {
        int jogos = args.length > 0 ? Integer.parseInt(args[0]) : 20000;
        double fa = args.length > 1 ? Double.parseDouble(args[1]) : 65, fb = args.length > 2 ? Double.parseDouble(args[2]) : 65;
        Rng rng = new Rng(11);
        double[] golsN = new double[8], minN = new double[8];
        double gols = 0, golsMb = 0, minMb = 0, golsRegulares = 0;
        int shootouts = 0, matchBall = 0;
        for (int i = 0; i < jogos; i++) {
            ResultadoPartida r = MotorKings.simular(time("a", fa), time("b", fb), new LigaKings.RegrasKings(), rng.derivar(i), false);
            gols += r.golsMandante + r.golsVisitante;
            int dado = 7;
            int ultimo = 35;
            for (EventoJogo e : r.eventos) {
                if (e.tipo().equals("dado")) dado = e.jogador().charAt(0) - '0';
                ultimo = Math.max(ultimo, e.minuto());
            }
            if (r.penaltisMandante != null) shootouts++;
            else matchBall++;
            // minutos jogados por n (por lado)
            for (int m = 0; m < 36; m++) minN[nDoMinuto(m, dado)] += 1;
            if (r.penaltisMandante == null) for (int m = 36; m <= ultimo; m++) {
                minN[nMatchBall(m)] += 1;
                minMb++;
            }
            for (EventoJogo e : r.eventos) {
                if (!e.gol() || !(e.tipo().equals("gol") || e.tipo().equals("gol_x2"))) continue; // só gol de jogo (sem pênaltis/cartas)
                int m = e.minuto();
                int n = m < 36 ? nDoMinuto(m, dado) : nMatchBall(m);
                golsN[n] += 1;
                if (m >= 36) golsMb++;
                else golsRegulares++;
            }
        }
        System.out.printf(Locale.ROOT, "jogos %d  forças %.0f x %.0f%n", jogos, fa, fb);
        System.out.printf(Locale.ROOT, "gols por jogo %.2f  (de jogo no tempo normal %.2f, no match ball %.2f)  shootouts %.1f%%  match ball %.1f%%  minutos de match ball por jogo com MB %.2f%n",
                gols / jogos, golsRegulares / jogos, golsMb / jogos, 100.0 * shootouts / jogos, 100.0 * matchBall / jogos, matchBall == 0 ? 0 : minMb / matchBall);
        System.out.println("n por lado | minutos | gols de jogo (2 times) por minuto");
        for (int n = 1; n <= 7; n++)
            if (minN[n] > 0) System.out.printf(Locale.ROOT, "  %dv%d     | %7.0f | %.3f%n", n, n, minN[n], golsN[n] / minN[n]);
    }

    static int nDoMinuto(int m, int dado) {
        if (m < 5) return m + 1;
        if (m >= 20 && m < 23) return dado;
        return 7;
    }

    static int nMatchBall(int m) {
        int[] ns = {5, 4, 3, 2, 1};
        return ns[Math.min(m - 36, ns.length - 1)];
    }
}
