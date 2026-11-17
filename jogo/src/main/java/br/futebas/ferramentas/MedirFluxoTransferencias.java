package br.futebas.ferramentas;

import br.futebas.Balanceamento;
import br.futebas.dados.BancoMundo;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Simulador;
import br.futebas.mundo.Sonda;
import br.futebas.mundo.StatusJogador;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Fluxo das transferências da IA (sem técnico humano): por confederação de origem → destino, por
 * divisão (mesmo país), quedas de 3+ divisões, Europa (5 grandes ligas) → Brasil e UEFA → CONMEBOL,
 * empréstimos, e se os clubes conseguem montar elenco. Livre: a origem é o último clube.
 * <pre>uso: MedirFluxoTransferencias [temporadas=2] [semente=1]</pre>
 */
public final class MedirFluxoTransferencias implements Sonda {
    static final Set<String> TOP5 = Set.of("ENG", "ESP", "ITA", "DEU", "GER", "FRA");

    final Mundo m;
    long total, pagas, livres, semOrigem, internacionais, emprestimos;
    final Map<String, Long> conf = new TreeMap<>();
    final Map<String, Long> div = new TreeMap<>();
    final Map<String, Long> divEmp = new TreeMap<>();
    long queda3, queda3Livre, queda3Emp, mesmoPais;
    final List<String> exQueda = new ArrayList<>();
    final List<String> top5Bra = new ArrayList<>();
    long uefaConmebol, uefaConmebolVolta, uefaConmebolVet, uefaConmebolOutro;
    long saBra, saEuropa;

    final Map<String, Integer> ultDiv = new java.util.HashMap<>();
    long queda3Vet;

    MedirFluxoTransferencias(Mundo m) {
        this.m = m;
        for (Clube c : m.clubes.values()) if (c.divisao > 0) ultDiv.merge(c.pais, c.divisao, Math::max);
    }

    int de(Clube c) {
        return c.divisao > 0 ? c.divisao : ultDiv.getOrDefault(c.pais, 0) + 1;
    }

    static String d(Clube c) {
        return c == null ? "-" : c.divisao <= 0 ? "s/d" : String.valueOf(Math.min(c.divisao, 5));
    }

    static String cf(Clube c) {
        return c == null ? "?" : c.confederacao == null ? "?" : c.confederacao;
    }

    @Override
    public void transferencia(Jogador j, Clube origem, Clube destino, long preco) {
        total++;
        if (preco > 0) pagas++;
        boolean livre = origem == null;
        if (livre) {
            livres++;
            origem = j.ultimoClubeId == null ? null : m.clube(j.ultimoClubeId);
            if (origem == null) {
                semOrigem++;
                return;
            }
        }
        int idade = j.idade(m.ano);
        if (!origem.pais.equals(destino.pais)) internacionais++;
        conf.merge(cf(origem) + " -> " + cf(destino), 1L, Long::sum);
        if (origem.pais.equals(destino.pais)) {
            mesmoPais++;
            div.merge(d(origem) + " -> " + d(destino), 1L, Long::sum);
            int o = de(origem), de = de(destino);
            if (de - o >= 3) {
                queda3++;
                if (idade >= 35) queda3Vet++;
                if (livre) queda3Livre++;
                if (exQueda.size() < 15) exQueda.add(String.format(Locale.ROOT, "  %s %d anos f%.0f %s (%s, div %s) -> %s (div %s)%s", j.nomeExibicao(), idade, j.forca,
                        j.pais, origem.nome, d(origem), destino.nome, d(destino), livre ? " [livre]" : ""));
            }
        }
        if (TOP5.contains(origem.pais) && origem.divisao == 1 && "BRA".equals(destino.pais)) {
            top5Bra.add(String.format(Locale.ROOT, "  %s %d anos f%.0f nac %s: %s -> %s (div %s)%s", j.nomeExibicao(), idade, j.forca, j.pais, origem.nome, destino.nome, d(destino),
                    livre ? " [livre]" : ""));
        }
        if ("UEFA".equals(origem.confederacao) && "CONMEBOL".equals(destino.confederacao)) {
            uefaConmebol++;
            if (j.pais.equals(destino.pais)) uefaConmebolVolta++;
            else if (idade >= 31) uefaConmebolVet++;
            else uefaConmebolOutro++;
        }
        if ("CONMEBOL".equals(origem.confederacao) && "BRA".equals(destino.pais) && !origem.pais.equals("BRA")) saBra++;
        if ("CONMEBOL".equals(origem.confederacao) && "UEFA".equals(destino.confederacao)) saEuropa++;
    }

    @Override
    public void emprestimo(Jogador j, Clube origem, Clube destino) {
        emprestimos++;
        divEmp.merge(d(origem) + " -> " + d(destino), 1L, Long::sum);
        int o = de(origem), de = de(destino);
        if (origem.pais.equals(destino.pais) && de - o >= 3) queda3Emp++;
    }

    public static void main(String[] args) throws Exception {
        List<String> arg = new ArrayList<>(List.of(args));
        if (arg.remove("--antes")) br.futebas.ia.Mercado.fluxoLivre = true;
        int temporadas = arg.size() > 0 ? Integer.parseInt(arg.get(0)) : 2;
        long semente = arg.size() > 1 ? Long.parseLong(arg.get(1)) : 1;
        BancoMundo db = BancoMundo.carregar(LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite"));
        Simulador s = Simulador.novo(db, semente, null, null);
        MedirFluxoTransferencias f = new MedirFluxoTransferencias(s.m);
        Sonda.Atual.s = f;
        Mundo m = s.m;
        for (int t = 0; t < temporadas; t++) {
            long t0 = System.nanoTime();
            s.avancarTemporada();
            System.out.printf(Locale.ROOT, "temporada %d: %.1f s%n", m.ano - 1, (System.nanoTime() - t0) / 1e9);
        }
        Sonda.Atual.s = null;
        System.out.printf(Locale.ROOT, "TOTAL %d (pagas %d, livres %d, livre sem clube anterior %d) | internacionais %d | empréstimos IA %d%n",
                f.total, f.pagas, f.livres, f.semOrigem, f.internacionais, f.emprestimos);
        System.out.println("== confederação origem -> destino");
        f.conf.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(25)
                .forEach(e -> System.out.printf("  %-24s %6d%n", e.getKey(), e.getValue()));
        System.out.println("== divisão (mesmo país) origem -> destino: " + f.mesmoPais);
        f.div.forEach((k, v) -> System.out.printf("  %-12s %6d%n", k, v));
        System.out.printf("QUEDA 3+ divisões (mesmo país; s/d = última + 1): %d (livres %d, 35+ anos %d) = %.1f%% | empréstimos com queda 3+: %d%n", f.queda3, f.queda3Livre, f.queda3Vet,
                100.0 * f.queda3 / Math.max(1, f.mesmoPais), f.queda3Emp);
        f.exQueda.forEach(System.out::println);
        System.out.println("== empréstimos por divisão");
        f.divEmp.forEach((k, v) -> System.out.printf("  %-12s %6d%n", k, v));
        System.out.println("TOP5 (1ª div) -> BRASIL: " + f.top5Bra.size());
        f.top5Bra.stream().limit(40).forEach(System.out::println);
        System.out.printf("UEFA -> CONMEBOL: %d (volta para casa %d, veterano 31+ %d, outros %d) | CONMEBOL -> UEFA: %d | outro país CONMEBOL -> BRA: %d%n",
                f.uefaConmebol, f.uefaConmebolVolta, f.uefaConmebolVet, f.uefaConmebolOutro, f.saEuropa, f.saBra);
        // elencos
        int curtos = 0, n = 0, abaixo18 = 0;
        double soma = 0;
        Map<String, double[]> grupos = new TreeMap<>();
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe || c.divisao <= 0) continue;
            n++;
            soma += c.elenco.size();
            if (c.elenco.size() < Balanceamento.ELENCO_MINIMO_IA) curtos++;
            if (c.elenco.size() < 18) abaixo18++;
            var p = m.pais(c.pais);
            String g = (p != null && p.detalhado ? "detalhado" : "resumido") + " " + c.confederacao + " div " + Math.min(c.divisao, 3);
            double[] x = grupos.computeIfAbsent(g, k -> new double[3]);
            x[0]++;
            x[1] += c.elenco.size();
            if (c.elenco.size() < 18) x[2]++;
        }
        grupos.forEach((k, x) -> System.out.printf(Locale.ROOT, "  %-32s clubes %5.0f  elenco médio %5.1f  abaixo de 18: %4.0f%n", k, x[0], x[1] / x[0], x[2]));
        long livresAgora = m.jogadores.stream().filter(j -> j.status == StatusJogador.LIVRE && !j.amador()).count();
        System.out.printf(Locale.ROOT, "ELENCOS (clubes com divisão): %d, média %.1f, abaixo de %d: %d, abaixo de 18: %d | livres agora %d%n", n, soma / n,
                Balanceamento.ELENCO_MINIMO_IA, curtos, abaixo18, livresAgora);
    }
}
