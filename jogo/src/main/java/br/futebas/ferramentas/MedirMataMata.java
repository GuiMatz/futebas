package br.futebas.ferramentas;

import br.futebas.competicao.Confronto;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Selecao;
import br.futebas.mundo.Simulador;
import br.futebas.mundo.Sonda;
import br.futebas.partida.ResultadoPartida;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * Mede quão "justos" são os mata-matas (Balanço 2026-11, rodada 2): mundo sem técnico humano,
 * N temporadas; para Champions, Libertadores, Europa League e Copa do Mundo guarda o posto de força
 * do campeão (no começo e na final), a chance do mais forte passar num confronto por faixa de
 * diferença de força, e a mesma conta jogo a jogo nas 1ªs divisões (comparação).
 * <pre>uso: MedirMataMata [--semente 1000] [--anos 6]</pre>
 */
public final class MedirMataMata implements Sonda {
    final Mundo m;
    MedirMataMata(Mundo m) { this.m = m; }

    static final Set<String> COMPS = Set.of("uefa-champions", "uefa-europa", "conmebol-libertadores", "conmebol-sul-americana", "fifa-copa-do-mundo",
            "uefa-conference");
    /** Ligas só para o posto do campeão (dinastias). */
    static final Set<String> LIGAS = Set.of("eng-1", "esp-1", "deu-1", "ita-1", "fra-1", "bra-1", "prt-1", "arg-1");
    static final double[] FAIXAS = {1, 2, 3, 4, 6, 8, 12, 99};

    static String base(String id) { return id.replaceAll("-\\d{4}$", ""); }

    double forca(Torneio t, String id) {
        if ("selecoes".equals(t.ambito)) {
            Selecao s = m.selecao(id);
            return s == null ? 0 : s.forca;
        }
        Clube c = m.clube(id);
        return c == null ? 0 : c.nivelAtual;
    }

    static int faixa(double d) {
        for (int i = 0; i < FAIXAS.length; i++) if (d < FAIXAS[i]) return i;
        return FAIXAS.length - 1;
    }

    /** [faixa] {jogos, mais forte vence, empates} (liga) e {confrontos, mais forte passa} (mata-mata). */
    final Map<String, long[][]> jogos = new TreeMap<>();
    final Map<String, long[][]> confrontos = new TreeMap<>();
    final Map<String, double[]> forcaConfronto = new HashMap<>();
    final Map<String, List<String>> rankInicial = new HashMap<>();
    /** Força de cada participante quando entrou (os 3ºs da Libertadores chegam à Sul-Americana em julho). */
    final Map<String, Map<String, Double>> forcaEntrada = new HashMap<>();

    /** Participantes que entraram no meio da competição: entram no ranking "do começo" com a força de quando chegaram. */
    void entraramDepois(Torneio t) {
        String k = t.id + "#" + t.ano;
        List<String> rk = rankInicial.get(k);
        Map<String, Double> fz = forcaEntrada.get(k);
        if (rk == null || fz == null || rk.size() >= t.participantes.size()) return;
        for (String id : t.participantes) if (!fz.containsKey(id)) fz.put(id, forca(t, id));
        List<String> novo = new ArrayList<>(fz.keySet());
        novo.sort(Comparator.comparingDouble((String id) -> -fz.get(id)).thenComparing(id -> id));
        rankInicial.put(k, novo);
    }
    final Map<String, List<int[]>> postos = new TreeMap<>(); // {posto início, posto final}
    final Map<String, List<double[]>> espalhamento = new TreeMap<>(); // forças #1, #4, #8, #16, #24
    final Set<Torneio> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
    final Set<Torneio> fechados = Collections.newSetFromMap(new IdentityHashMap<>());
    final List<String> campeoes = new ArrayList<>();

    @Override
    public void partida(Torneio t, Partida p, ResultadoPartida r) {
        String b = base(t.id);
        double fm = forca(t, p.mandante), fv = forca(t, p.visitante);
        String chave = null;
        if ("nacional".equals(t.ambito) && Integer.valueOf(1).equals(t.divisao) && p.rodadaMata < 0) chave = "liga-1div";
        else if (COMPS.contains(b)) chave = b + (p.rodadaMata >= 0 ? " mm" : " fase");
        if (chave != null && fm != fv) {
            long[] x = jogos.computeIfAbsent(chave, k -> new long[FAIXAS.length][3])[faixa(Math.abs(fm - fv))];
            x[0]++;
            int gf = fm > fv ? r.golsMandante : r.golsVisitante, gc = fm > fv ? r.golsVisitante : r.golsMandante;
            if (gf > gc) x[1]++;
            else if (gf == gc) x[2]++;
        }
        if (COMPS.contains(b) && p.rodadaMata >= 0) {
            forcaConfronto.putIfAbsent(t.id + "#" + t.ano + "#" + p.mandante, new double[]{fm});
            forcaConfronto.putIfAbsent(t.id + "#" + t.ano + "#" + p.visitante, new double[]{fv});
        }
    }

    void semana() {
        List<Torneio> todos = new ArrayList<>(m.torneios);
        todos.addAll(m.torneiosAnteriores);
        for (Torneio t : todos) {
            String b = base(t.id);
            if (!COMPS.contains(b) && !LIGAS.contains(b)) continue;
            if (vistos.add(t)) {
                List<String> p = new ArrayList<>(t.participantes);
                p.sort(Comparator.comparingDouble((String id) -> -forca(t, id)));
                rankInicial.put(t.id + "#" + t.ano, p);
                Map<String, Double> fz = new HashMap<>();
                for (String id : p) fz.put(id, forca(t, id));
                forcaEntrada.put(t.id + "#" + t.ano, fz);
                int[] q = {0, 3, 7, 15, 23};
                double[] e = new double[q.length];
                for (int i = 0; i < q.length; i++) e[i] = q[i] < p.size() ? forca(t, p.get(q[i])) : Double.NaN;
                espalhamento.computeIfAbsent(b, k -> new ArrayList<>()).add(e);
            }
            entraramDepois(t);
            if (t.encerrado && fechados.add(t)) fim(t, b);
        }
        if (fechados.size() > 5000) fechados.retainAll(new HashSet<>(todos));
        if (vistos.size() > 5000) vistos.retainAll(new HashSet<>(todos));
    }

    void fim(Torneio t, String b) {
        for (FaseExec f : t.fases) {
            if (!f.mataMata()) continue;
            for (Confronto c : f.confrontos) {
                if (c.vencedor == null) continue;
                double[] fa = forcaConfronto.get(t.id + "#" + t.ano + "#" + c.a), fb = forcaConfronto.get(t.id + "#" + t.ano + "#" + c.b);
                if (fa == null || fb == null || fa[0] == fb[0]) continue;
                String forte = fa[0] > fb[0] ? c.a : c.b;
                long[] x = confrontos.computeIfAbsent(b + (c.partidas.size() > 1 ? " ida-volta" : " único"), k -> new long[FAIXAS.length][3])[faixa(Math.abs(fa[0] - fb[0]))];
                x[0]++;
                if (forte.equals(c.vencedor)) x[1]++;
            }
        }
        List<String> ini = rankInicial.get(t.id + "#" + t.ano);
        if (t.campeao == null || ini == null) return;
        String camp = null;
        for (String id : t.participantes) {
            Clube c = m.clube(id);
            Selecao s = m.selecao(id);
            String nome = c != null ? c.nome : s != null ? s.nome : id;
            if (nome.equals(t.campeao) || id.equals(t.campeao)) camp = id;
        }
        if (camp == null) return;
        List<String> agora = new ArrayList<>(t.participantes);
        agora.sort(Comparator.comparingDouble((String id) -> -forca(t, id)));
        int k0 = ini.indexOf(camp) + 1, k1 = agora.indexOf(camp) + 1;
        postos.computeIfAbsent(b, k -> new ArrayList<>()).add(new int[]{k0, k1});
        campeoes.add(t.ano + " " + b + ": " + t.campeao + " #" + k0 + "→#" + k1 + String.format(Locale.ROOT, " (%.1f; #1 %.1f, #8 %.1f)", forca(t, camp),
                forca(t, agora.get(0)), agora.size() > 7 ? forca(t, agora.get(7)) : 0));
    }

    final List<String> economia = new ArrayList<>();

    /** Semana 40: empréstimos ativos; caixa ÷ receita e folha ÷ receita (p50, p90) nas 1ªs divisões de ENG, ESP e BRA. */
    void economia() {
        int emp = 0, prof = 0;
        for (br.futebas.mundo.Jogador j : m.jogadores) {
            if (j.status != br.futebas.mundo.StatusJogador.PROFISSIONAL || j.amador()) continue;
            prof++;
            if (j.contrato != null && j.contrato.tipo == br.futebas.mundo.TipoContrato.EMPRESTIMO) emp++;
        }
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%d: emprestados %d de %d (%.1f%%)", m.ano, emp, prof, 100.0 * emp / Math.max(1, prof)));
        for (String pais : List.of("ENG", "ESP", "BRA")) {
            List<Double> caixa = new ArrayList<>(), folha = new ArrayList<>();
            for (Clube c : m.clubes.values()) {
                if (c.virtual || !c.existe || c.divisao != 1 || !pais.equals(c.pais)) continue;
                double e = br.futebas.financas.Financas.receitaReferencia(c, m.ano);
                long f = 0;
                for (int id : c.elenco) {
                    br.futebas.mundo.Jogador j = m.jogador(id);
                    if (j != null && j.contrato != null) f += j.contrato.salario;
                }
                caixa.add(c.dinheiro / e);
                folha.add(f * 12 / e);
            }
            Collections.sort(caixa);
            Collections.sort(folha);
            if (caixa.isEmpty()) continue;
            sb.append(String.format(Locale.ROOT, " | %s caixa/E p50 %.2f p90 %.2f, folha/E p50 %.2f", pais, caixa.get(caixa.size() / 2),
                    caixa.get((int) (caixa.size() * 0.9)), folha.get(folha.size() / 2)));
        }
        economia.add(sb.toString());
    }

    String relatorio() {
        StringBuilder sb = new StringBuilder();
        sb.append("== Mais forte vence (jogo a jogo, nivelAtual): faixa de diferença → jogos | V% E%\n");
        for (Map.Entry<String, long[][]> e : jogos.entrySet()) {
            sb.append(String.format(Locale.ROOT, "  %-28s", e.getKey()));
            double lo = 0;
            for (int i = 0; i < FAIXAS.length; i++) {
                long[] x = e.getValue()[i];
                if (x[0] > 0) sb.append(String.format(Locale.ROOT, " | %s-%s: %d %.0f/%.0f", fmt(lo), FAIXAS[i] > 50 ? "+" : fmt(FAIXAS[i]), x[0], 100.0 * x[1] / x[0], 100.0 * x[2] / x[0]));
                lo = FAIXAS[i];
            }
            sb.append('\n');
        }
        sb.append("== Mais forte passa (confrontos): faixa → n | %\n");
        for (Map.Entry<String, long[][]> e : confrontos.entrySet()) {
            sb.append(String.format(Locale.ROOT, "  %-34s", e.getKey()));
            double lo = 0;
            long n = 0, s = 0;
            for (int i = 0; i < FAIXAS.length; i++) {
                long[] x = e.getValue()[i];
                n += x[0];
                s += x[1];
                if (x[0] > 0) sb.append(String.format(Locale.ROOT, " | %s-%s: %d %.0f", fmt(lo), FAIXAS[i] > 50 ? "+" : fmt(FAIXAS[i]), x[0], 100.0 * x[1] / x[0]));
                lo = FAIXAS[i];
            }
            sb.append(String.format(Locale.ROOT, " | total %d %.0f%%%n", n, 100.0 * s / Math.max(1, n)));
        }
        sb.append("== Posto do campeão: n | médio início/final | top 8 início/final | top 4 início\n");
        for (Map.Entry<String, List<int[]>> e : postos.entrySet()) {
            List<int[]> l = e.getValue();
            double s0 = 0, s1 = 0;
            int t8a = 0, t8b = 0, t4 = 0;
            for (int[] x : l) {
                s0 += x[0];
                s1 += x[1];
                if (x[0] <= 8) t8a++;
                if (x[1] <= 8) t8b++;
                if (x[0] <= 4) t4++;
            }
            sb.append(String.format(Locale.ROOT, "  %-26s %d | %.1f / %.1f | %d / %d | %d%n", e.getKey(), l.size(), s0 / l.size(), s1 / l.size(), t8a, t8b, t4));
        }
        sb.append("== Força no começo (#1 #4 #8 #16 #24), média\n");
        for (Map.Entry<String, List<double[]>> e : espalhamento.entrySet()) {
            double[] s = new double[5];
            int[] n = new int[5];
            for (double[] x : e.getValue()) for (int i = 0; i < 5; i++) if (!Double.isNaN(x[i])) { s[i] += x[i]; n[i]++; }
            sb.append(String.format(Locale.ROOT, "  %-26s", e.getKey()));
            for (int i = 0; i < 5; i++) sb.append(String.format(Locale.ROOT, " %.1f", n[i] == 0 ? 0 : s[i] / n[i]));
            sb.append('\n');
        }
        sb.append("== Economia (semana 40)\n");
        for (String e : economia) sb.append("  ").append(e).append('\n');
        sb.append("== Campeões\n");
        for (String c : campeoes) sb.append("  ").append(c).append('\n');
        return sb.toString();
    }

    static String fmt(double d) { return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d); }


    /** Confrontos sintéticos (ida e volta) entre times de força fixa: % do mais forte passar. */
    static void sintetico() {
        br.futebas.mundo.Rng rng = new br.futebas.mundo.Rng(7);
        br.futebas.partida.OpcoesPartida op = new br.futebas.partida.OpcoesPartida();
        for (double base : new double[]{70, 80, 84}) {
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "base %.0f (elite %.2f):", base, br.futebas.partida.MotorPartida.fatorElite(base)));
            for (double d : new double[]{2, 4, 6, 8}) {
                int passa = 0, n = 2000;
                double fa = base + d / 2, fb = base - d / 2;
                for (int i = 0; i < n; i++) {
                    br.futebas.partida.TimePartida a1 = CalibrarMotor.time("A", fa, rng, 2), b1 = CalibrarMotor.time("B", fb, rng, 2);
                    a1.estilo = Simulador.estiloIA(null, fa + 3, fb, true);
                    b1.estilo = Simulador.estiloIA(null, fb - 3, fa, false);
                    ResultadoPartida r1 = br.futebas.partida.MotorPartida.simular(a1, b1, op, rng);
                    br.futebas.partida.TimePartida a2 = CalibrarMotor.time("A", fa, rng, 2), b2 = CalibrarMotor.time("B", fb, rng, 2);
                    b2.estilo = Simulador.estiloIA(null, fb + 3, fa, true);
                    a2.estilo = Simulador.estiloIA(null, fa - 3, fb, false);
                    ResultadoPartida r2 = br.futebas.partida.MotorPartida.simular(b2, a2, op, rng);
                    int ga = r1.golsMandante + r2.golsVisitante, gb = r1.golsVisitante + r2.golsMandante;
                    if (ga > gb || (ga == gb && rng.chance(0.5))) passa++;
                }
                sb.append(String.format(Locale.ROOT, " d%.0f %.0f%%", d, 100.0 * passa / n));
            }
            System.out.println(sb);
        }
    }

    /** Liga Kings sintética parecida com a IOS (20 times: presidente 75 CA + 11 draftados ~68): fatia de gols e artilheiros presidentes. */
    static void kingsSintetico() {
        br.futebas.mundo.Rng rng = new br.futebas.mundo.Rng(11);
        br.futebas.mundo.Posicao[] pos = {br.futebas.mundo.Posicao.GOL, br.futebas.mundo.Posicao.ZAG, br.futebas.mundo.Posicao.ZAG, br.futebas.mundo.Posicao.LD,
                br.futebas.mundo.Posicao.MC, br.futebas.mundo.Posicao.MC, br.futebas.mundo.Posicao.VOL, br.futebas.mundo.Posicao.MEI, br.futebas.mundo.Posicao.CA,
                br.futebas.mundo.Posicao.CA, br.futebas.mundo.Posicao.PE};
        int temporadas = 20, presArt = 0, presTop3 = 0;
        long golsPres = 0, golsAutor = 0;
        for (int t = 0; t < temporadas; t++) {
            List<br.futebas.kings.LigaKings.TimeKings> times = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                br.futebas.kings.LigaKings.TimeKings tk = new br.futebas.kings.LigaKings.TimeKings();
                tk.id = "t" + i;
                tk.nome = "T" + i;
                tk.presidente = "P" + i;
                br.futebas.kings.LigaKings.AtletaKings p = new br.futebas.kings.LigaKings.AtletaKings();
                p.nome = "P" + i;
                p.forca = 75;
                p.posicao = br.futebas.mundo.Posicao.CA;
                p.presidente = true;
                tk.elenco.add(p);
                for (int k = 0; k < 11; k++) {
                    br.futebas.kings.LigaKings.AtletaKings a = new br.futebas.kings.LigaKings.AtletaKings();
                    a.nome = "T" + i + "-" + k;
                    a.forca = 68 + rng.gauss(0, 2.5);
                    a.posicao = pos[k];
                    tk.elenco.add(a);
                }
                times.add(tk);
            }
            for (int a = 0; a < 20; a++) for (int b = 0; b < 20; b++) if (a != b && (a + b) % 2 == 0)
                br.futebas.kings.MotorKings.simular(times.get(a), times.get(b), null, rng.derivar(t * 1000L + a * 20 + b), false);
            List<br.futebas.kings.LigaKings.AtletaKings> todos = new ArrayList<>();
            for (br.futebas.kings.LigaKings.TimeKings tk : times) todos.addAll(tk.elenco);
            todos.sort(Comparator.comparingInt((br.futebas.kings.LigaKings.AtletaKings x) -> -x.gols));
            if (todos.get(0).presidente) presArt++;
            for (int i = 0; i < 3; i++) if (todos.get(i).presidente) presTop3++;
            for (br.futebas.kings.LigaKings.AtletaKings x : todos) {
                golsAutor += x.gols;
                if (x.presidente) golsPres += x.gols;
            }
        }
        System.out.printf(Locale.ROOT, "Kings sintética (AUTOR_K da propriedade): presidentes %.1f%% dos gols com autor | artilheiro presidente em %d de %d | top 3: %d de %d%n",
                100.0 * golsPres / Math.max(1, golsAutor), presArt, temporadas, presTop3, 3 * temporadas);
    }

    /** Copa do Mundo sintética (48 seleções mais fortes do mundo inicial, formato de 2026, modelo rápido): % do campeão no top 8. */
    static void copaSintetica(long semente) {
        Simulador s = Simulador.novo(BancoMundo.carregar(LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite")), semente, null, null);
        List<Double> f = new ArrayList<>();
        for (Selecao x : s.m.selecoes.values()) f.add(x.forca);
        f.sort(Comparator.reverseOrder());
        f = new ArrayList<>(f.subList(0, 48));
        br.futebas.mundo.Rng rng = new br.futebas.mundo.Rng(semente);
        int n = 4000, top1 = 0, top4 = 0, top8 = 0;
        for (int it = 0; it < n; it++) {
            List<List<Integer>> grupos = new ArrayList<>();
            for (int g = 0; g < 12; g++) grupos.add(new ArrayList<>());
            for (int pote = 0; pote < 4; pote++) {
                List<Integer> p = new ArrayList<>();
                for (int i = 0; i < 12; i++) p.add(pote * 12 + i);
                rng.embaralhar(p);
                for (int g = 0; g < 12; g++) grupos.get(g).add(p.get(g));
            }
            List<double[]> terceiros = new ArrayList<>();
            List<Integer> vivos = new ArrayList<>();
            for (List<Integer> g : grupos) {
                double[] pts = new double[4];
                for (int a = 0; a < 4; a++) for (int b = a + 1; b < 4; b++) {
                    int[] r = br.futebas.selecao.Selecoes.placarSelecoes(f.get(g.get(a)), f.get(g.get(b)), false, rng);
                    pts[a] += (r[0] > r[1] ? 3 : r[0] == r[1] ? 1 : 0) + 0.01 * (r[0] - r[1]) + 0.0001 * rng.real();
                    pts[b] += (r[1] > r[0] ? 3 : r[0] == r[1] ? 1 : 0) + 0.01 * (r[1] - r[0]) + 0.0001 * rng.real();
                }
                Integer[] ord = {0, 1, 2, 3};
                java.util.Arrays.sort(ord, (x, y) -> Double.compare(pts[y], pts[x]));
                vivos.add(g.get(ord[0]));
                vivos.add(g.get(ord[1]));
                terceiros.add(new double[]{pts[ord[2]], g.get(ord[2])});
            }
            terceiros.sort((x, y) -> Double.compare(y[0], x[0]));
            for (int i = 0; i < 8; i++) vivos.add((int) terceiros.get(i)[1]);
            rng.embaralhar(vivos);
            while (vivos.size() > 1) {
                List<Integer> prox = new ArrayList<>();
                for (int i = 0; i + 1 < vivos.size(); i += 2) {
                    int a = vivos.get(i), b = vivos.get(i + 1);
                    double fa = f.get(a), fb = f.get(b);
                    int[] r = br.futebas.selecao.Selecoes.placarSelecoes(fa, fb, false, rng);
                    boolean va;
                    if (r[0] != r[1]) va = r[0] > r[1];
                    else if (rng.chance(0.35)) va = rng.chance(1 / (1 + Math.exp(-0.15 * (fa - fb))));
                    else va = rng.chance(0.5);
                    prox.add(va ? a : b);
                }
                vivos = prox;
            }
            int c = vivos.get(0);
            if (c == 0) top1++;
            if (c < 4) top4++;
            if (c < 8) top8++;
        }
        System.out.printf(Locale.ROOT, "Copa sintética (forças #1 %.1f #8 %.1f #16 %.1f #32 %.1f): campeão #1 %.0f%% | top 4 %.0f%% | top 8 %.0f%%%n",
                f.get(0), f.get(7), f.get(15), f.get(31), 100.0 * top1 / n, 100.0 * top4 / n, 100.0 * top8 / n);
    }
    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) a.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        if (a.containsKey("copa")) {
            copaSintetica(Long.parseLong(a.getOrDefault("semente", "1000")));
            return;
        }
        if (a.containsKey("kings")) {
            kingsSintetico();
            return;
        }
        if (a.containsKey("sintetico")) {
            sintetico();
            return;
        }
        int anos = Integer.parseInt(a.getOrDefault("anos", "6"));
        long semente = Long.parseLong(a.getOrDefault("semente", "1000"));
        Path banco = LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite");
        Simulador s = Simulador.novo(BancoMundo.carregar(banco), semente, null, null);
        MedirMataMata med = new MedirMataMata(s.m);
        Sonda.Atual.s = med;
        int fim = s.m.ano + anos;
        long t0 = System.nanoTime();
        while (s.m.ano < fim) {
            s.avancarSemana();
            med.semana();
            if (s.m.semana == 40 && (med.economia.isEmpty() || !med.economia.get(med.economia.size() - 1).startsWith(s.m.ano + ":"))) med.economia();
        }
        med.semana();
        System.out.println(med.relatorio());
        System.out.printf("tempo %.1f min%n", (System.nanoTime() - t0) / 6e10);
    }
}
