package br.futebas.ferramentas;

import br.futebas.mundo.Rng;
import br.futebas.partida.MotorPartida;
import br.futebas.partida.OpcoesPartida;
import br.futebas.partida.ResultadoPartida;
import br.futebas.partida.TimePartida;

import java.util.Locale;

/** Chutes, chutes no gol e gols por time e por jogo, em cada nível de força (times iguais). */
public final class MedirChutes {
    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 4000;
        for (int f : new int[]{40, 50, 60, 70, 80, 88}) {
            Rng rng = new Rng(f);
            OpcoesPartida op = new OpcoesPartida();
            double ch = 0, ng = 0, gols = 0;
            for (int i = 0; i < n; i++) {
                TimePartida a = CalibrarMotor.time("A", f, rng, 0), b = CalibrarMotor.time("B", f, rng, 0);
                ResultadoPartida r = MotorPartida.simular(a, b, op, rng);
                ch += r.chutes[0] + r.chutes[1];
                ng += r.noGol[0] + r.noGol[1];
                gols += r.golsMandante + r.golsVisitante;
            }
            System.out.printf(Locale.ROOT, "%d x %d: chutes por time %.1f, no gol %.1f, gols por jogo %.2f%n", f, f, ch / n / 2, ng / n / 2, gols / n);
        }
    }
}
