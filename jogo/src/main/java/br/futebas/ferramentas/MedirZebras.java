package br.futebas.ferramentas;

import br.futebas.partida.Estilo;
import br.futebas.partida.Marcacao;
import br.futebas.partida.MotorPartida;
import br.futebas.partida.OpcoesPartida;
import br.futebas.partida.ResultadoPartida;
import br.futebas.partida.TimePartida;
import br.futebas.mundo.Rng;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Favorito FORA de casa contra um time mais fraco: vitória/empate/derrota do favorito por diferença
 * de força, e o efeito de cada estilo/marcação do azarão (para achar tática que dá vantagem demais).
 * <pre>uso: MedirZebras [jogos por caso, padrão 20000]</pre>
 */
public final class MedirZebras {
    private MedirZebras() {}

    static String vde(int jogos, long semente, double fav, double azarao, Consumer<TimePartida> ajusteAzarao) {
        Rng rng = new Rng(semente);
        OpcoesPartida op = new OpcoesPartida();
        int v = 0, e = 0, d = 0;
        double gf = 0, ga = 0;
        for (int i = 0; i < jogos; i++) {
            TimePartida a = CalibrarMotor.time("F", fav, rng, 0), b = CalibrarMotor.time("Z", azarao, rng, 0);
            if (ajusteAzarao != null) ajusteAzarao.accept(b);
            ResultadoPartida r = MotorPartida.simular(b, a, op, rng); // azarão manda
            gf += r.golsVisitante;
            ga += r.golsMandante;
            if (r.golsVisitante > r.golsMandante) v++;
            else if (r.golsVisitante == r.golsMandante) e++;
            else d++;
        }
        return String.format(Locale.ROOT, "favorito V %4.1f%%  E %4.1f%%  D %4.1f%%  | gols %.2f x %.2f",
                100.0 * v / jogos, 100.0 * e / jogos, 100.0 * d / jogos, gf / jogos, ga / jogos);
    }

    /** Forte EM CASA contra o azarão. */
    static String vdeCasa(int jogos, long semente, double fav, double azarao) {
        Rng rng = new Rng(semente);
        OpcoesPartida op = new OpcoesPartida();
        int v = 0, e = 0, d = 0;
        double gf = 0, ga = 0;
        for (int i = 0; i < jogos; i++) {
            TimePartida a = CalibrarMotor.time("F", fav, rng, 0), b = CalibrarMotor.time("Z", azarao, rng, 0);
            ResultadoPartida r = MotorPartida.simular(a, b, op, rng);
            gf += r.golsMandante;
            ga += r.golsVisitante;
            if (r.golsMandante > r.golsVisitante) v++;
            else if (r.golsMandante == r.golsVisitante) e++;
            else d++;
        }
        return String.format(Locale.ROOT, "favorito V %4.1f%%  E %4.1f%%  D %4.1f%%  | gols %.2f x %.2f",
                100.0 * v / jogos, 100.0 * e / jogos, 100.0 * d / jogos, gf / jogos, ga / jogos);
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 20000;
        System.out.println("== Favorito fora de casa, táticas padrão (ambos equilibrado/normal)");
        for (int gap : new int[]{0, 4, 8, 12, 16})
            System.out.println(String.format("  77 x %d (dif %2d): ", 77 - gap, gap) + vde(n, 11 + gap, 77, 77 - gap, null));
        System.out.println("== Diferenças grandes (Série A x Série D / sem divisão): forte FORA e forte EM CASA");
        for (int gap : new int[]{15, 20, 25, 30}) {
            System.out.println(String.format("  fora  75 x %d (dif %2d): ", 75 - gap, gap) + vde(n, 51 + gap, 75, 75 - gap, null));
            System.out.println(String.format("  casa  75 x %d (dif %2d): ", 75 - gap, gap) + vdeCasa(n, 61 + gap, 75, 75 - gap));
        }
        if (args.length > 1) return;
        System.out.println("== 77 fora x 69 em casa: estilo do azarão");
        for (Estilo s : Estilo.values()) System.out.println(String.format("  %-16s", s.nome) + vde(n, 21, 77, 69, t -> t.estilo = s));
        System.out.println("== 77 fora x 69 em casa: marcação do azarão");
        for (Marcacao mc : Marcacao.values()) System.out.println(String.format("  %-16s", mc.nome) + vde(n, 31, 77, 69, t -> t.marcacao = mc));
        System.out.println("== 77 fora x 69 em casa: combinação de cada estilo com cada marcação do azarão");
        for (Estilo s : Estilo.values())
            for (Marcacao mc : Marcacao.values())
                System.out.println(String.format("  %-14s %-8s", s.nome, mc.nome) + vde(n / 2, 41, 77, 69, t -> {
                    t.estilo = s;
                    t.marcacao = mc;
                }));
    }
}
