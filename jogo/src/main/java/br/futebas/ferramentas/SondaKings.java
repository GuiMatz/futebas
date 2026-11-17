package br.futebas.ferramentas;

import br.futebas.dados.BancoMundo;
import br.futebas.kings.LigaKings;
import br.futebas.mundo.Simulador;

import java.util.Locale;

/** Força dos atletas da Kings por liga e time (mundo novo e depois de N temporadas). uso: SondaKings [anos] */
public final class SondaKings {
    public static void main(String[] args) throws Exception {
        int anos = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        BancoMundo db = BancoMundo.carregar(LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite"));
        Simulador s = Simulador.novo(db, 3, null, null);
        for (int i = 0; i < anos; i++) s.avancarTemporada();
        for (LigaKings l : s.m.kings) {
            double soma = 0, max = 0, min = 999;
            int n = 0, maxElenco = 0;
            for (LigaKings.TimeKings t : l.times) {
                maxElenco = Math.max(maxElenco, t.elenco.size());
                for (LigaKings.AtletaKings a : t.elenco) {
                    soma += a.forca;
                    n++;
                    max = Math.max(max, a.forca);
                    min = Math.min(min, a.forca);
                }
            }
            System.out.printf(Locale.ROOT, "%-28s times %2d  atletas %3d  força média %.1f  min %.0f  max %.0f  maior elenco %d%n",
                    l.id, l.times.size(), n, n == 0 ? 0 : soma / n, min, max, maxElenco);
        }
        LigaKings l = s.m.kings.get(0);
        for (LigaKings.TimeKings t : l.times.subList(0, Math.min(2, l.times.size()))) {
            System.out.println(t.nome + ":");
            for (LigaKings.AtletaKings a : t.elenco)
                System.out.printf(Locale.ROOT, "   %-24s %-4s %5.1f %s%s%n", a.nome, a.posicao, a.forca, a.tipo, a.presidente ? " (presidente)" : "");
        }
    }
}
