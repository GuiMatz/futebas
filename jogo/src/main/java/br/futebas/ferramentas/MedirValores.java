package br.futebas.ferramentas;

import br.futebas.Balanceamento;
import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.financas.Financas;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Simulador;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compara o valor de mercado do jogo com o do Transfermarkt (banco, em euros) no início de 2026. */
public final class MedirValores {
    public static void main(String[] args) throws Exception {
        BancoMundo db = BancoMundo.carregar(LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite"));
        Simulador s = Simulador.novo(db, 1, null, null);
        List<double[]> r = new ArrayList<>();
        List<String> exemplos = new ArrayList<>();
        for (Jogador j : s.m.jogadores) {
            if (j.dbId == null || j.clubeId == null) continue;
            Db.Jogador d = db.jogadores.get(j.dbId);
            if (d == null || d.valorMercado() == null || d.valorMercado() <= 0) continue;
            double real = d.valorMercado() * Balanceamento.EURO_EM_REAIS;
            double jogo = Financas.valorMercado(j, s.m.clube(j.clubeId), s.m.ano);
            r.add(new double[]{jogo / real, j.forca, real, j.idade(s.m.ano)});
            if (exemplos.size() < 40 && (j.clubeId.equals("internacional") || j.clubeId.equals("flamengo") || j.clubeId.equals("real-madrid") || j.clubeId.equals("criciuma")))
                exemplos.add(String.format(Locale.ROOT, "%-22s %-14s f%2.0f  jogo %6.1fM  real %6.1fM  x%.2f", j.nomeExibicao(), j.clubeId, (double) j.forca, jogo / 1e6, real / 1e6, jogo / real));
        }
        exemplos.forEach(System.out::println);
        r.sort((a, b) -> Double.compare(a[0], b[0]));
        System.out.printf(Locale.ROOT, "n=%d  razão jogo/real: p10 %.2f  p25 %.2f  mediana %.2f  p75 %.2f  p90 %.2f%n", r.size(),
                r.get(r.size() / 10)[0], r.get(r.size() / 4)[0], r.get(r.size() / 2)[0], r.get(r.size() * 3 / 4)[0], r.get(r.size() * 9 / 10)[0]);
        for (int f0 = 50; f0 < 95; f0 += 5) {
            List<Double> x = new ArrayList<>();
            for (double[] a : r) if (a[1] >= f0 && a[1] < f0 + 5) x.add(a[0]);
            if (x.isEmpty()) continue;
            x.sort(Double::compare);
            System.out.printf(Locale.ROOT, "força %d-%d: n=%5d mediana x%.2f%n", f0, f0 + 4, x.size(), x.get(x.size() / 2));
        }
        for (int i0 = 16; i0 < 40; i0 += 2) {
            List<Double> x = new ArrayList<>();
            for (double[] a : r) if (a[3] >= i0 && a[3] < i0 + 2 && a[1] >= 65) x.add(a[0]);
            if (x.isEmpty()) continue;
            x.sort(Double::compare);
            System.out.printf(Locale.ROOT, "idade %d-%d (f65+): n=%5d mediana x%.2f%n", i0, i0 + 1, x.size(), x.get(x.size() / 2));
        }
    }
}
