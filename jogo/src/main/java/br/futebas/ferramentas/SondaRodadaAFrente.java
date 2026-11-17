package br.futebas.ferramentas;

import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;

import java.util.HashMap;
import java.util.Map;

/** Procura semanas em que o time do técnico fica com mais jogos que os outros na mesma fase de pontos. uso: SondaRodadaAFrente [clube] [semanas] */
public final class SondaRodadaAFrente {
    public static void main(String[] args) throws Exception {
        String clube = args.length > 0 ? args[0] : "internacional";
        int semanas = args.length > 1 ? Integer.parseInt(args[1]) : 52;
        BancoMundo db = BancoMundo.carregar(LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite"));
        Jogo j = Jogo.novoJogo(db, 7, clube, "Teste");
        String eu = j.meuClube().id;
        for (int w = 0; w < semanas; w++) {
            j.avancarSemana();
            for (Torneio t : j.mundo().torneios) {
                boolean participa = false;
                for (Partida p : t.partidas) if (p.envolve(eu) && p.rodadaMata < 0) { participa = true; break; }
                if (!participa) continue;
                // jogos por time na fase de pontos, por fase+grupo do técnico
                Map<String, Integer> jogos = new HashMap<>();
                int fase = -1, grupo = -1;
                for (Partida p : t.partidas) if (p.envolve(eu) && p.rodadaMata < 0) { fase = p.fase; grupo = p.grupo; }
                for (Partida p : t.partidas) {
                    if (p.rodadaMata >= 0 || p.fase != fase || p.grupo != grupo) continue;
                    jogos.putIfAbsent(p.mandante, 0);
                    jogos.putIfAbsent(p.visitante, 0);
                    if (p.jogada) { jogos.merge(p.mandante, 1, Integer::sum); jogos.merge(p.visitante, 1, Integer::sum); }
                }
                int meu = jogos.getOrDefault(eu, 0), maxOutros = 0, minOutros = 999;
                for (var e : jogos.entrySet()) if (!e.getKey().equals(eu)) { maxOutros = Math.max(maxOutros, e.getValue()); minOutros = Math.min(minOutros, e.getValue()); }
                if (meu > maxOutros) {
                    StringBuilder sb = new StringBuilder();
                    for (Partida p : t.partidas) if (p.envolve(eu) && p.jogada && p.fase == fase && p.rodadaMata < 0)
                        sb.append(" [data ").append(p.data).append(" sa ").append(p.sa).append(" dia ").append(p.dia).append("]");
                    // a última jogada do técnico e as outras da mesma data
                    System.out.println("sem " + j.mundo().semanaAbsoluta() + " " + t.id + ": eu " + meu + " outros " + minOutros + ".." + maxOutros);
                    for (Partida p : t.partidas) if (p.envolve(eu) && p.fase == fase && p.rodadaMata < 0)
                        System.out.println("   meu: data " + p.data + " id " + p.id + " " + p.mandante + "x" + p.visitante + " sa " + p.sa + " dia " + p.dia + " jogada " + p.jogada + " placar " + p.placar());
                    System.out.println("   times no grupo: " + jogos.size() + " -> " + jogos);
                    Partida ult = null;
                    for (Partida p : t.partidas) if (p.envolve(eu) && p.jogada && p.fase == fase && p.rodadaMata < 0 && (ult == null || p.data > ult.data)) ult = p;
                    if (ult != null) for (Partida p : t.partidas) if (p.data == ult.data && p.fase == fase)
                        System.out.println("   data " + p.data + " " + p.mandante + "x" + p.visitante + " sa " + p.sa + " dia " + p.dia + " jogada " + p.jogada + " proxData " + t.proximaData + " semDaData " + t.semanaDaData(p.data));
                }
            }
        }
    }
}
