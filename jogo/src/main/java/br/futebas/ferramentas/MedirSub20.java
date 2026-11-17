package br.futebas.ferramentas;

import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Simulador;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Mede o custo do Sub-20 com o banco completo: criação do mundo, semanas e temporadas, clubes com
 * Sub-20 montado, jogadores do Sub-20 e competições Sub-20.
 * uso: MedirSub20 [temporadas=2] [clube=flamengo]
 */
public final class MedirSub20 {
    private MedirSub20() {}

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        int temporadas = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        String clube = args.length > 1 ? args[1] : null;
        Path banco = LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite");
        BancoMundo db = BancoMundo.carregar(banco);
        long t0 = System.nanoTime();
        Simulador s = Simulador.novo(db, 1234, clube, "T");
        Mundo m = s.m;
        System.out.printf("criação: %.2f s | jogadores %d%n", (System.nanoTime() - t0) / 1e9, m.jogadores.size());
        relatorio(m);
        System.out.println("gerados para o Sub-20 na criação: " + s.sub20.gerados);
        // 20 semanas medidas uma a uma
        long soma = 0, pior = 0;
        java.lang.management.ThreadMXBean tm = java.lang.management.ManagementFactory.getThreadMXBean();
        long cpu0 = tm.getCurrentThreadCpuTime();
        for (int i = 0; i < 20; i++) {
            long a = System.nanoTime();
            s.avancarSemana();
            long d = System.nanoTime() - a;
            soma += d;
            pior = Math.max(pior, d);
        }
        System.out.printf("semanas 1-20: média %.0f ms, pior %.0f ms | CPU da thread por semana %.0f ms%n", soma / 20 / 1e6, pior / 1e6,
                (tm.getCurrentThreadCpuTime() - cpu0) / 20 / 1e6);
        for (int k = 0; k < temporadas; k++) {
            int ano = m.ano;
            long a = System.nanoTime(), ca = tm.getCurrentThreadCpuTime();
            s.avancarTemporada();
            System.out.printf("temporada %d: %.2f s (CPU da thread %.2f s) | jogadores %d%n", ano, (System.nanoTime() - a) / 1e9,
                    (tm.getCurrentThreadCpuTime() - ca) / 1e9, m.jogadores.size());
        }
        relatorio(m);
        System.out.println("gerados para o Sub-20 no total: " + s.sub20.gerados);
    }

    static void relatorio(Mundo m) {
        int clubes = 0, com23 = 0, sub20 = 0;
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe) continue;
            clubes++;
            if (!c.sub20Montado) continue;
            int n = 0;
            for (int id : c.base) {
                Jogador j = m.ativo(id);
                if (j != null && j.noSub20) n++;
            }
            sub20 += n;
            if (n >= 23) com23++;
        }
        int torneios = 0, jogos = 0;
        for (Torneio t : m.torneiosSub20) {
            torneios++;
            jogos += t.partidas.size();
        }
        int pro = 0, base = 0, livre = 0;
        for (Jogador j : m.jogadores) switch (j.status) {
            case PROFISSIONAL -> pro++;
            case BASE -> base++;
            case LIVRE -> livre++;
            default -> { }
        }
        System.out.printf("profissionais %d | base %d | livres %d%n", pro, base, livre);
        System.out.printf("clubes %d | Sub-20 montado com 23+: %d | jogadores no Sub-20: %d | torneios Sub-20: %d (%d jogos) | newgens no ano: %d%n",
                clubes, com23, sub20, torneios, jogos, m.historia.estat(m.ano).newgens);
    }
}
