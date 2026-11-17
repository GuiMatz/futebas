package br.futebas.ferramentas;

import br.futebas.dados.BancoMundo;
import br.futebas.financas.Financas;
import br.futebas.historia.Historia;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Simulador;
import br.futebas.mundo.StatusJogador;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Diagnóstico rápido do motor (finanças, memória, seleções) num mundo só, ano a ano.
 * <pre>uso: Diagnostico [--anos 6] [--semente 1000]</pre>
 */
public final class Diagnostico {
    private Diagnostico() {}

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) a.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        int anos = Integer.parseInt(a.getOrDefault("anos", "6"));
        long semente = Long.parseLong(a.getOrDefault("semente", "1000"));
        boolean resumo = a.containsKey("resumo");
        Path banco = LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite");
        BancoMundo db = BancoMundo.carregar(banco);
        Simulador s = Simulador.novo(db, semente, null, null);
        Mundo m = s.m;
        financas(m, "inicio");
        forcas(m, "inicio");
        Map<String, Integer> falAntes = new HashMap<>();
        for (int k = 0; k < anos; k++) {
            int ano = m.ano;
            long t0 = System.nanoTime();
            s.avancarTemporada();
            double t = (System.nanoTime() - t0) / 1e9;
            Historia.EstatAno e = m.historia.estat(ano);
            System.gc();
            System.out.printf("== %d: seleções %.2f gols/jogo em %d jogos%n", ano, e.golsSelecoes / (double) Math.max(1, e.jogosSelecoes), e.jogosSelecoes);
            System.out.printf("== %d: %.1f s | falências %d | gols/jogo %.2f | M/E/V %.1f/%.1f/%.1f | heap %d MB | jogadores %d%n", ano, t, e.falencias,
                    e.golsLiga / (double) Math.max(1, e.jogosLiga), 100.0 * e.vitMandante / Math.max(1, e.jogosLiga),
                    100.0 * e.empates / Math.max(1, e.jogosLiga), 100.0 * e.vitVisitante / Math.max(1, e.jogosLiga), LongRun.heapUsado() >> 20,
                    m.totalJogadores());
            if (resumo) {
                Map<StatusJogador, Integer> st = new EnumMap<>(StatusJogador.class);
                for (Jogador j : m.jogadores) st.merge(j.status, 1, Integer::sum);
                System.out.println("   status: " + st + " arquivados " + m.arquivados.size());
                for (Historia.Titulo ti : m.historia.titulos) if (ti.ano == ano && "selecoes".equals(ti.ambito)) System.out.println("   " + ti.competicao + ": " + ti.campeao + " (vice " + ti.vice + ")");
                if (ano % 4 == 2) {
                    List<br.futebas.mundo.Selecao> sel = new ArrayList<>(m.selecoes.values());
                    sel.sort(java.util.Comparator.comparingDouble(x -> -x.forca));
                    StringBuilder sb = new StringBuilder("   força/ref: ");
                    for (int i = 0; i < 16; i++) sb.append(String.format("%s %.1f/%.1f, ", sel.get(i).pais, sel.get(i).forca, sel.get(i).forcaReferencia));
                    System.out.println(sb);
                }
                continue;
            }
            int mostradas = 0;
            for (Clube c : m.clubes.values()) {
                if (c.falencias > falAntes.getOrDefault(c.id, 0) && mostradas++ < 12) {
                    double rr = Financas.receitaReferencia(c, m.ano);
                    long folha = 0;
                    for (int id : c.elenco) { Jogador j = m.jogador(id); if (j != null && j.contrato != null) folha += j.contrato.salario; }
                    System.out.printf("   falência: %s (%s div %d->%s rep %.0f/%.0f) E %.1fM folha/E %.2f elenco %d q %.2e%n", c.nome, c.pais, c.divisao, c.divisaoInicial,
                            c.reputacao, c.reputacaoInicial, rr / 1e6, folha * 12 / rr, c.elenco.size(), c.fatorSalarial);
                    var f = c.financasAnterior;
                    if (f != null) System.out.printf("      ingr %.0f tv %.0f vend %.0f prem %.0f | sal %.0f compras %.0f juros %.0f obras %.0f | saldoIni %.0f%n",
                            f.ingressos / 1e3, f.tvPatrocinio / 1e3, f.vendas / 1e3, f.premios / 1e3, f.salarios / 1e3, f.compras / 1e3, f.juros / 1e3, f.obras / 1e3, f.saldoInicio / 1e3);
                }
                falAntes.put(c.id, c.falencias);
            }
            financas(m, String.valueOf(ano));
            forcas(m, String.valueOf(ano));
            List<Clube> piores = new ArrayList<>();
            for (Clube c : m.clubes.values()) if (!c.virtual) piores.add(c);
            piores.sort(java.util.Comparator.comparingDouble(c -> c.dinheiro / Financas.receitaReferencia(c, m.ano)));
            for (Clube c : piores.subList(0, 6)) {
                var f = c.financas;
                System.out.printf("   pior: %s (%s div %d) E %.1fM caixa %.0f dív %.0f | ingr %.0f tv %.0f vend %.0f prem %.0f | sal %.0f compras %.0f juros %.0f obras %.0f | saldoIni %.0f%n",
                        c.nome, c.pais, c.divisao, Financas.receitaReferencia(c, m.ano) / 1e6, c.dinheiro / 1e3, c.divida / 1e3,
                        f.ingressos / 1e3, f.tvPatrocinio / 1e3, f.vendas / 1e3, f.premios / 1e3, f.salarios / 1e3, f.compras / 1e3, f.juros / 1e3, f.obras / 1e3, f.saldoInicio / 1e3);
                List<Integer> ids = new ArrayList<>(c.elenco);
                ids.addAll(c.base);
                ids.addAll(c.amadores);
                ids.sort(java.util.Comparator.comparingLong(id -> m.jogador(id).contrato == null ? 0 : -m.jogador(id).contrato.salario));
                for (int id : ids.subList(0, Math.min(3, ids.size()))) {
                    Jogador j = m.jogador(id);
                    System.out.printf("      %s F%.0f %s sal %d tipo %s origem %s%n", j.nome, j.forca, j.status, j.contrato == null ? -1 : j.contrato.salario,
                            j.contrato == null ? "-" : j.contrato.tipo, j.contrato == null ? "-" : j.contrato.clubeOrigem);
                }
            }
            Map<StatusJogador, Integer> st = new EnumMap<>(StatusJogador.class);
            for (Jogador j : m.jogadores) st.merge(j.status, 1, Integer::sum);
            System.out.println("   status: " + st);
            for (Historia.Titulo ti : m.historia.titulos) if (ti.ano == ano && "selecoes".equals(ti.ambito)) System.out.println("   " + ti.competicao + ": " + ti.campeao + " (vice " + ti.vice + ")");
        }
        List<br.futebas.mundo.Selecao> rk = s.selecoes.ranking();
        StringBuilder sb = new StringBuilder("   ranking: ");
        for (int i = 0; i < 20; i++) sb.append(String.format("%s %.0f/%.1f, ", rk.get(i).pais, rk.get(i).elo, rk.get(i).forca));
        System.out.println(sb);
    }

    /** Caixa ÷ receita de referência, folha ÷ receita de TV, por divisão. */
    static void financas(Mundo m, String rotulo) {
        Map<Integer, List<double[]>> porDiv = new TreeMap<>();
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe) continue;
            long folha = 0;
            for (int id : c.elenco) {
                Jogador j = m.jogador(id);
                if (j != null && j.contrato != null) folha += j.contrato.salario;
            }
            double tv = Math.max(1, Financas.tvPatrocinio(c, m.ano));
            porDiv.computeIfAbsent(Math.min(c.divisao, 5), k -> new ArrayList<>()).add(new double[]{c.dinheiro / tv, folha * 12.0 / tv, c.divida / tv,
                    c.dinheiro < 0 ? 1 : 0});
        }
        StringBuilder sb = new StringBuilder("   [" + rotulo + "] caixa/TV p10/p50/p90 | folha/TV p50/p90 | dívida/TV p90 | %neg\n");
        for (Map.Entry<Integer, List<double[]>> e : porDiv.entrySet()) {
            List<double[]> l = e.getValue();
            sb.append(String.format("     div %d (%d): %.2f/%.2f/%.2f | %.2f/%.2f | %.2f | %.0f%%%n", e.getKey(), l.size(), q(l, 0, 0.1), q(l, 0, 0.5), q(l, 0, 0.9),
                    q(l, 1, 0.5), q(l, 1, 0.9), q(l, 2, 0.9), 100 * l.stream().mapToDouble(x -> x[3]).average().orElse(0)));
        }
        System.out.print(sb);
    }

    /** Força dos profissionais por divisão × faixa de reputação (p50/p90/p99) e por idade. */
    static void forcas(Mundo m, String rotulo) {
        Map<String, List<double[]>> g = new TreeMap<>();
        Map<Integer, List<double[]>> idade = new TreeMap<>();
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe) continue;
            int div = c.divisao <= 0 ? 5 : Math.min(c.divisao, 5);
            int faixa = (int) Math.min(5, Math.max(0, c.reputacao / 17));
            String k = "div " + div + " rep " + faixa;
            for (int id : c.elenco) {
                Jogador j = m.jogador(id);
                if (j == null) continue;
                g.computeIfAbsent(k, x -> new ArrayList<>()).add(new double[]{j.forca});
                int i = j.idade(m.ano);
                if (i >= 20 && i <= 38 && div == 1) idade.computeIfAbsent(i, x -> new ArrayList<>()).add(new double[]{j.forca});
            }
        }
        StringBuilder sb = new StringBuilder("   [" + rotulo + "] força dos elencos p50/p90/p99 (n)%n".formatted());
        for (Map.Entry<String, List<double[]>> e : g.entrySet()) {
            List<double[]> l = e.getValue();
            sb.append(String.format("     %s: %.0f/%.0f/%.0f (%d)%n", e.getKey(), q(l, 0, 0.5), q(l, 0, 0.9), q(l, 0, 0.99), l.size()));
        }
        sb.append("     1ª divisão por idade p50/p90: ");
        for (Map.Entry<Integer, List<double[]>> e : idade.entrySet()) {
            if (e.getKey() % 2 == 0) sb.append(String.format("%d: %.0f/%.0f  ", e.getKey(), q(e.getValue(), 0, 0.5), q(e.getValue(), 0, 0.9)));
        }
        System.out.println(sb);
    }

    static double q(List<double[]> l, int k, double p) {
        double[] v = l.stream().mapToDouble(x -> x[k]).sorted().toArray();
        return v.length == 0 ? 0 : v[(int) Math.min(v.length - 1, Math.floor(p * v.length))];
    }
}
