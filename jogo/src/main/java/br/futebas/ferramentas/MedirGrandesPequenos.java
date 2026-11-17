package br.futebas.ferramentas;

import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Simulador;
import br.futebas.partida.Escalador;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Uma temporada simulada: jogos de clubes da 1ª/2ª divisão contra clubes da 4ª divisão ou sem
 * divisão, por competição: vitória/empate/derrota do grande, e a força do dia. uso: MedirGrandesPequenos [pais]
 */
public final class MedirGrandesPequenos {
    public static void main(String[] args) throws Exception {
        String pais = args.length > 0 ? args[0] : "BRA";
        BancoMundo db = BancoMundo.carregar(LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite"));
        Simulador s = Simulador.novo(db, 5, null, null);
        Map<Integer, Clube> div0 = new TreeMap<>();
        // guarda a divisão de cada clube no começo (a temporada muda acesso/rebaixamento no fim)
        Map<String, Integer> divisao = new java.util.HashMap<>();
        Map<String, Double> forca = new java.util.HashMap<>();
        for (Clube c : s.m.clubes.values()) {
            divisao.put(c.id, c.divisao);
            try {
                forca.put(c.id, Escalador.forcaTime(s.m, c));
            } catch (RuntimeException e) {
                forca.put(c.id, 0.0);
            }
        }
        s.avancarTemporada();
        Map<String, int[]> porComp = new TreeMap<>();
        int[] tot = new int[3];
        double somaDif = 0;
        int n = 0;
        for (Torneio t : s.m.torneiosAnteriores.isEmpty() ? s.m.torneios : s.m.torneiosAnteriores) {
            for (Partida p : t.partidas) {
                if (!p.jogada || p.golsMandante == null) continue;
                Clube a = s.m.clube(p.mandante), b = s.m.clube(p.visitante);
                if (a == null || b == null || !pais.equals(a.pais) || !pais.equals(b.pais)) continue;
                int da = divisao.getOrDefault(a.id, 0), dbv = divisao.getOrDefault(b.id, 0);
                boolean aGrande = da == 1 || da == 2, bGrande = dbv == 1 || dbv == 2;
                boolean aPeq = da == 0 || da >= 4, bPeq = dbv == 0 || dbv >= 4;
                int lado;
                if (aGrande && bPeq) lado = 0;
                else if (bGrande && aPeq) lado = 1;
                else continue;
                int pro = lado == 0 ? p.golsMandante : p.golsVisitante, contra = lado == 0 ? p.golsVisitante : p.golsMandante;
                int r = pro > contra ? 0 : pro == contra ? 1 : 2;
                tot[r]++;
                porComp.computeIfAbsent(t.id + (lado == 0 ? " (grande em casa)" : " (grande fora)"), k -> new int[3])[r]++;
                somaDif += forca.getOrDefault(lado == 0 ? a.id : b.id, 0.0) - forca.getOrDefault(lado == 0 ? b.id : a.id, 0.0);
                n++;
            }
        }
        System.out.printf(Locale.ROOT, "TOTAL %d jogos: grande V %.1f%% E %.1f%% D %.1f%% | diferença média de força %.1f%n", n,
                100.0 * tot[0] / n, 100.0 * tot[1] / n, 100.0 * tot[2] / n, somaDif / Math.max(1, n));
        porComp.forEach((k, v) -> {
            int m = v[0] + v[1] + v[2];
            if (m >= 6) System.out.printf(Locale.ROOT, "  %-44s %4d  V %5.1f%%  E %5.1f%%  D %5.1f%%%n", k, m, 100.0 * v[0] / m, 100.0 * v[1] / m, 100.0 * v[2] / m);
        });
    }
}
