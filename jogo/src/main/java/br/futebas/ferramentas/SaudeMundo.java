package br.futebas.ferramentas;

import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.financas.Financas;
import br.futebas.historia.Historia;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Origem;
import br.futebas.mundo.Pais;
import br.futebas.mundo.Salvamento;
import br.futebas.mundo.Simulador;
import br.futebas.mundo.Sonda;
import br.futebas.mundo.StatusJogador;
import br.futebas.mundo.TipoContrato;

import java.io.PrintStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Relatório de saúde do mundo em muitas temporadas, sem técnico humano (todos os clubes na IA).
 * Só observa: a {@link Sonda} conta transferências e o resto é lido do mundo no 1º de janeiro de cada
 * ano (antes da 1ª temporada e depois de cada uma). O save é gravado de verdade a cada 5 anos.
 *
 * <pre>uso: SaudeMundo [--temporadas 20] [--semente 1000] [--saida tools/saude]</pre>
 * Também aceita posicionais: {@code SaudeMundo 30 1000 tools/saude}. Grava {@code saude-<semente>.csv}
 * (uma linha por 1º/jan) e {@code saude-<semente>.txt} (resumo legível), atualizados a cada temporada.
 * Rode com {@code JOPTS="-Xmx3g -Xss8m"} para 30 temporadas.
 */
public final class SaudeMundo implements Sonda {
    /** Ligas de referência: id da competição → rótulo curto. */
    static final Map<String, String> LIGAS = new LinkedHashMap<>();

    static {
        LIGAS.put("bra-1", "BraA");
        LIGAS.put("bra-2", "BraB");
        LIGAS.put("eng-1", "PL");
        LIGAS.put("esp-1", "Liga");
        LIGAS.put("ita-1", "SerieA");
        LIGAS.put("deu-1", "Bundes");
        LIGAS.put("fra-1", "Ligue1");
        LIGAS.put("arg-1", "Arg");
        LIGAS.put("usa-1", "MLS");
        LIGAS.put("sau-1", "Saudi");
    }

    static final List<String> FORTES = List.of("eng-1", "esp-1", "ita-1", "deu-1", "fra-1");
    /** Competições com o posto de força do campeão medido. */
    static final List<String> POSTO = List.of("bra-1", "eng-1", "esp-1", "ita-1", "deu-1", "fra-1", "arg-1", "conmebol-libertadores", "uefa-champions");

    final Mundo m;
    // transferências da temporada corrente (zeradas a cada ano)
    long transf, transfPagas, transfInter, transfPagasInter, saEuropa, europaBrasil, europaBrasilJovem;
    long volume;

    SaudeMundo(Mundo m) {
        this.m = m;
    }

    @Override
    public void transferencia(Jogador j, Clube origem, Clube destino, long preco) {
        transf++;
        if (preco > 0) {
            transfPagas++;
            volume += preco;
        }
        Clube o = origem != null ? origem : j.ultimoClubeId == null ? null : m.clube(j.ultimoClubeId);
        if (o == null || destino == null) return;
        if (!o.pais.equals(destino.pais)) {
            transfInter++;
            if (preco > 0) transfPagasInter++;
        }
        if ("CONMEBOL".equals(o.confederacao) && "UEFA".equals(destino.confederacao)) saEuropa++;
        if ("UEFA".equals(o.confederacao) && "BRA".equals(destino.pais)) {
            europaBrasil++;
            if (j.idade(m.ano) <= 29) europaBrasilJovem++;
        }
    }

    // ================================================================== medidas
    static double media(List<Double> l) {
        double s = 0;
        for (double x : l) s += x;
        return l.isEmpty() ? Double.NaN : s / l.size();
    }

    static double q(List<Double> l, double p) {
        if (l.isEmpty()) return Double.NaN;
        List<Double> o = new ArrayList<>(l);
        o.sort(null);
        return o.get(Math.min(o.size() - 1, (int) Math.floor(p * (o.size() - 1) + 1e-9)));
    }

    /** Torneio mais recente (maior ano) de uma liga, entre os em curso e os anteriores. */
    Torneio liga(String id, boolean concluida) {
        Torneio melhor = null;
        for (List<Torneio> l : List.of(m.torneiosAnteriores, m.torneios))
            for (Torneio t : l) {
                if (!Balanco.base(t.id).equals(id)) continue;
                if (concluida && t.campeao == null) continue;
                if (melhor == null || t.ano > melhor.ano) melhor = t;
            }
        return melhor;
    }

    List<Clube> clubesDaLiga(String id) {
        List<Clube> l = new ArrayList<>();
        Torneio t = liga(id, false);
        if (t == null) return l;
        for (String cid : t.participantes) {
            Clube c = m.clube(cid);
            if (c != null && !c.virtual && c.existe) l.add(c);
        }
        return l;
    }

    double top11(Clube c) {
        List<Double> f = new ArrayList<>();
        for (int id : c.elenco) {
            Jogador j = m.jogador(id);
            if (j != null && j.status == StatusJogador.PROFISSIONAL) f.add(j.forca);
        }
        f.sort(Comparator.reverseOrder());
        double s = 0;
        int n = Math.min(11, f.size());
        for (int i = 0; i < n; i++) s += f.get(i);
        return n == 0 ? 0 : s / n;
    }

    /** Uma linha de medidas (colunas em ordem fixa). */
    final Map<String, Double> linha = new LinkedHashMap<>();

    void put(String k, double v) {
        linha.put(k, v);
    }

    /** Grandes de cada liga: os 4 de maior reputação no início. */
    final Map<String, List<String>> grandes = new LinkedHashMap<>();
    /** Força/potencial dos reais em 2026 por idade (21 e 25): {força, potencial, p90 força, n}. */
    final Map<Integer, double[]> reais2026 = new TreeMap<>();
    double salarioRef0, valorRef0, salarioMedio0, valorMedio0;

    void medir(int estadoEm) {
        linha.clear();
        put("ano", estadoEm);
        // ------------------------------------------------------------ nível
        for (Map.Entry<String, String> e : LIGAS.entrySet()) {
            List<Double> t = new ArrayList<>();
            for (Clube c : clubesDaLiga(e.getKey())) t.add(top11(c));
            put("top11_" + e.getValue(), media(t));
        }
        List<Double> forcas = new ArrayList<>(), idades = new ArrayList<>();
        int n80 = 0, n85 = 0, n88 = 0, n90 = 0, pro = 0, base = 0, livre = 0;
        Map<Integer, List<double[]>> gerIdade = new TreeMap<>(), realIdade = new TreeMap<>();
        List<Double> salarios = new ArrayList<>(), valores = new ArrayList<>(), salRef = new ArrayList<>(), valRef = new ArrayList<>();
        int ano = m.ano;
        for (Jogador j : m.jogadores) {
            switch (j.status) {
                case BASE -> base++;
                case LIVRE -> livre++;
                default -> { }
            }
            if (j.status != StatusJogador.PROFISSIONAL || j.amador()) continue;
            pro++;
            forcas.add(j.forca);
            int id = j.idade(ano);
            idades.add((double) id);
            if (j.forca >= 80) n80++;
            if (j.forca >= 85) n85++;
            if (j.forca >= 88) n88++;
            if (j.forca >= 90) n90++;
            if (id == 21 || id == 25) {
                // gerado no jogo = sem id do banco; real = jogador do banco com fonte
                if (j.dbId == null) gerIdade.computeIfAbsent(id, k -> new ArrayList<>()).add(new double[]{j.forca, j.potencial});
                else if (j.origem == Origem.REAL) realIdade.computeIfAbsent(id, k -> new ArrayList<>()).add(new double[]{j.forca, j.potencial});
            }
            Clube c = m.clube(j.clubeId);
            if (j.contrato != null && j.contrato.tipo == TipoContrato.PROFISSIONAL && c != null && !c.virtual) {
                double sal = j.contrato.salario / 1e3;
                double val = Financas.valorMercado(j, c, ano) / 1e6;
                salarios.add(sal);
                valores.add(val);
                // cesta fixa: força 70–75, 24–28 anos (tira o efeito de composição)
                if (j.forca >= 70 && j.forca < 75 && id >= 24 && id <= 28) {
                    salRef.add(sal);
                    valRef.add(val);
                }
            }
        }
        put("forca_media", media(forcas));
        put("forca_p90", q(forcas, .9));
        put("forca_max", q(forcas, 1));
        put("n80", n80);
        put("n85", n85);
        put("n88", n88);
        put("n90", n90);
        put("idade_media", media(idades));
        for (int idd : new int[]{21, 25}) {
            List<double[]> g = gerIdade.getOrDefault(idd, List.of()), r = realIdade.getOrDefault(idd, List.of());
            List<Double> gf = new ArrayList<>(), gp = new ArrayList<>(), rf = new ArrayList<>();
            for (double[] x : g) {
                gf.add(x[0]);
                gp.add(x[1]);
            }
            for (double[] x : r) rf.add(x[0]);
            put("ger" + idd + "_n", g.size());
            put("ger" + idd + "_forca", media(gf));
            put("ger" + idd + "_pot", media(gp));
            put("ger" + idd + "_p90", q(gf, .9));
            put("real" + idd + "_n", r.size());
            put("real" + idd + "_forca", media(rf));
            if (estadoEm == m.anoInicial) {
                List<Double> rp = new ArrayList<>();
                for (double[] x : r) rp.add(x[1]);
                reais2026.put(idd, new double[]{media(rf), media(rp), q(rf, .9), r.size()});
            }
        }
        // ------------------------------------------------------------ economia
        int clubes = 0, detalhados = 0, caixaNeg = 0, dividaAlta = 0, falidosHist = 0;
        // perfil fixo no mesmo clube (16/11/2026): um jogador de referência (força 72, 26 anos, MC) avaliado em
        // cada clube detalhado: separa o preço do perfil da mudança de onde os jogadores 70–75 estão
        Jogador ref = new Jogador();
        ref.forca = 72;
        ref.potencial = 72;
        ref.anoNasc = ano - 26;
        ref.posicao = br.futebas.mundo.Posicao.MC;
        ref.origem = Origem.GERADO;
        List<Double> refSal = new ArrayList<>(), refVal = new ArrayList<>(), caixaReceita = new ArrayList<>();
        List<Double> caixa = new ArrayList<>(), divida = new ArrayList<>(), folha = new ArrayList<>();
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe) continue;
            clubes++;
            Pais p = m.pais(c.pais);
            if (p == null || !p.detalhado || c.divisao <= 0) continue;
            detalhados++;
            caixa.add(c.dinheiro / 1e6);
            refSal.add(Financas.salarioNaEscala(ref, c, ano) / 1e3);
            refVal.add(Financas.valorMercado(ref, c, ano) / 1e6);
            caixaReceita.add(c.dinheiro / Math.max(1.0, Financas.receitaReferencia(c, ano)));
            divida.add(c.divida / 1e6);
            long f = 0;
            for (int id : c.elenco) {
                Jogador j = m.jogador(id);
                if (j != null && j.contrato != null && j.contrato.tipo == TipoContrato.PROFISSIONAL) f += j.contrato.salario;
            }
            folha.add(f * 12 / 1e6);
            if (c.dinheiro < 0) caixaNeg++;
            long receita = Math.max(1, (long) Financas.receitaReferencia(c, ano));
            if (c.divida - Math.min(0, c.dinheiro) > receita) dividaAlta++;
            if (c.falencias > 0) falidosHist++;
        }
        put("clubes", clubes);
        put("clubes_det", detalhados);
        put("caixa_medio_mi", media(caixa));
        put("caixa_receita_media", media(caixaReceita));
        put("ref72_salario_mil", media(refSal));
        put("ref72_valor_mi", media(refVal));
        put("caixa_p50_mi", q(caixa, .5));
        put("divida_media_mi", media(divida));
        put("folha_media_mi", media(folha));
        put("caixa_neg_pct", 100.0 * caixaNeg / Math.max(1, detalhados));
        put("divida_gt_receita_pct", 100.0 * dividaAlta / Math.max(1, detalhados));
        put("ja_faliram_pct", 100.0 * falidosHist / Math.max(1, detalhados));
        double sm = media(salarios), vm = media(valores), sr = media(salRef), vr = media(valRef);
        if (estadoEm == m.anoInicial) {
            salarioMedio0 = sm;
            valorMedio0 = vm;
            salarioRef0 = sr;
            valorRef0 = vr;
        }
        put("salario_medio_mil", sm);
        put("valor_medio_mi", vm);
        put("valor_max_mi", q(valores, 1));
        put("salario_cesta_mil", sr);
        put("valor_cesta_mi", vr);
        put("fator_epoca", Financas.fatorEpoca(ano));
        // ------------------------------------------------------------ volume
        put("profissionais", pro);
        put("base", base);
        put("livres", livre);
        put("vivos", m.jogadores.size());
        put("arquivados", m.arquivados.size());
        put("total_save", m.jogadores.size() + m.arquivados.size());
        put("titulos_hist", m.historia.titulos.size());
    }

    // ================================================================== por temporada
    static final class Temporada {
        int ano;
        Map<String, Double> estado;
        int aposent, newgens, falencias, transfEstat, interEstat;
        long transf, pagas, inter, pagasInter, saEur, eurBra, eurBraJovem, volume;
        double cpuThread, cpuProcesso, gc, parede, saveMb = Double.NaN, saveSeg = Double.NaN;
        Map<String, String> campeoes = new LinkedHashMap<>();
        Map<String, Double> posGrandes = new LinkedHashMap<>();
        /** Posto de força (nível do elenco no fim da temporada) do campeão entre os participantes. */
        Map<String, Integer> postoCampeao = new LinkedHashMap<>();
        String libertadores, champions;
    }

    void competicao(Temporada t) {
        for (Historia.Titulo ti : m.historia.titulos) {
            if (ti.ano != t.ano) continue;
            String b = Balanco.base(ti.competicaoId);
            if (LIGAS.containsKey(b)) t.campeoes.put(b, ti.campeao);
            if (b.equals("conmebol-libertadores")) t.libertadores = ti.campeao + " (" + paisDe(ti.campeaoId) + ")";
            if (b.equals("uefa-champions")) t.champions = ti.campeao + " (" + paisDe(ti.campeaoId) + ")";
        }
        for (String id : POSTO) {
            Torneio tr = liga(id, true);
            if (tr == null || tr.campeao == null || tr.ano != t.ano) continue;
            List<String> ps = new ArrayList<>(tr.participantes);
            ps.removeIf(x -> m.clube(x) == null);
            ps.sort(Comparator.comparingDouble((String x) -> -m.clube(x).nivelAtual).thenComparing(x -> x));
            int k = ps.indexOf(tr.campeao) + 1;
            if (k > 0) t.postoCampeao.put(id, k);
        }
        for (String id : LIGAS.keySet()) {
            Torneio tr = liga(id, true);
            List<String> gs = grandes.get(id);
            if (tr == null || gs == null || gs.isEmpty()) continue;
            double s = 0;
            for (String g : gs) {
                int p = tr.posicaoFinal(g);
                // fora da 1ª divisão conta como último + 1
                s += p >= 999 ? tr.participantes.size() + 1 : p;
            }
            t.posGrandes.put(id, s / gs.size());
        }
    }

    String paisDe(String id) {
        Clube c = m.clube(id);
        return c == null ? "?" : c.pais;
    }

    void escolherGrandes() {
        for (String id : LIGAS.keySet()) {
            List<Clube> l = clubesDaLiga(id);
            l.sort(Comparator.comparingDouble((Clube c) -> -c.reputacao).thenComparing(c -> c.id));
            List<String> g = new ArrayList<>();
            for (int i = 0; i < Math.min(4, l.size()); i++) g.add(l.get(i).id);
            grandes.put(id, g);
        }
    }

    // ================================================================== principal
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        System.setOut(new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        Map<String, String> a = new HashMap<>();
        List<String> pos = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) a.put(args[i].substring(2), args[++i]);
            else if (args[i].contains("=")) a.put(args[i].substring(0, args[i].indexOf('=')), args[i].substring(args[i].indexOf('=') + 1));
            else pos.add(args[i]);
        }
        int temporadas = Integer.parseInt(a.getOrDefault("temporadas", pos.size() > 0 ? pos.get(0) : "20"));
        long semente = Long.parseLong(a.getOrDefault("semente", pos.size() > 1 ? pos.get(1) : "1000"));
        Path raiz = LongRun.acharRaiz();
        Path dir = Paths.get(a.getOrDefault("saida", pos.size() > 2 ? pos.get(2) : raiz.resolve("tools").resolve("saude").toString()));
        Files.createDirectories(dir);
        Path csv = dir.resolve("saude-" + semente + ".csv"), txt = dir.resolve("saude-" + semente + ".txt");
        Path save = Files.createTempDirectory("futebas-saude").resolve("saude-" + semente + Salvamento.EXTENSAO);

        long t0 = System.nanoTime();
        BancoMundo db = BancoMundo.carregar(raiz.resolve("dados").resolve("mundo.sqlite"));
        Simulador s = Simulador.novo(db, semente, null, null);
        Mundo m = s.m;
        SaudeMundo sm = new SaudeMundo(m);
        System.out.printf("SaudeMundo | semente %d | %d temporadas | criado em %.1f s | %d clubes, %d jogadores%n", semente, temporadas,
                (System.nanoTime() - t0) / 1e9, m.clubes.size(), m.jogadores.size());
        sm.escolherGrandes();
        sm.medir(m.ano);
        List<Temporada> hist = new ArrayList<>();
        Temporada ini = new Temporada();
        ini.ano = m.ano - 1;
        ini.estado = new LinkedHashMap<>(sm.linha);
        medirSave(m, save, ini);
        hist.add(ini);
        Sonda.Atual.s = sm;

        ThreadMXBean tm = ManagementFactory.getThreadMXBean();
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        for (int k = 0; k < temporadas; k++) {
            int ano = m.ano;
            sm.transf = sm.transfPagas = sm.transfInter = sm.transfPagasInter = sm.saEuropa = sm.europaBrasil = sm.europaBrasilJovem = sm.volume = 0;
            long c0 = tm.getCurrentThreadCpuTime(), p0 = os.getProcessCpuTime(), g0 = gcMs(), w0 = System.nanoTime();
            s.avancarTemporada();
            Temporada t = new Temporada();
            t.ano = ano;
            t.cpuThread = (tm.getCurrentThreadCpuTime() - c0) / 1e9;
            t.cpuProcesso = (os.getProcessCpuTime() - p0) / 1e9;
            t.gc = (gcMs() - g0) / 1e3;
            t.parede = (System.nanoTime() - w0) / 1e9;
            Historia.EstatAno e = m.historia.estat(ano);
            t.aposent = e.aposentadorias;
            t.newgens = e.newgens;
            t.falencias = e.falencias;
            t.transfEstat = e.transferencias;
            t.interEstat = e.transferenciasInternacionais;
            t.transf = sm.transf;
            t.pagas = sm.transfPagas;
            t.inter = sm.transfInter;
            t.pagasInter = sm.transfPagasInter;
            t.saEur = sm.saEuropa;
            t.eurBra = sm.europaBrasil;
            t.eurBraJovem = sm.europaBrasilJovem;
            t.volume = sm.volume;
            sm.competicao(t);
            Sonda.Atual.s = null; // a medição não deve contar como transferência
            sm.medir(m.ano);
            t.estado = new LinkedHashMap<>(sm.linha);
            if ((m.ano - m.anoInicial) % 5 == 0 || k == temporadas - 1) medirSave(m, save, t);
            Sonda.Atual.s = sm;
            hist.add(t);
            System.out.printf(Locale.ROOT, "%d: CPU thread %.1f s | processo %.1f s | parede %.1f s | prof %.0f | top11 PL %.1f BraA %.1f | n88 %.0f | transf %d | save %s%n",
                    ano, t.cpuThread, t.cpuProcesso, t.parede, t.estado.get("profissionais"), t.estado.get("top11_PL"), t.estado.get("top11_BraA"),
                    t.estado.get("n88"), t.transf, Double.isNaN(t.saveMb) ? "-" : String.format(Locale.ROOT, "%.1f MB", t.saveMb));
            gravar(sm, hist, csv, txt, semente);
        }
        Sonda.Atual.s = null;
        Files.deleteIfExists(save);
        System.out.printf(Locale.ROOT, "Total: %.1f min | %s | %s%n", (System.nanoTime() - t0) / 60e9, csv, txt);
    }

    static long gcMs() {
        long s = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) s += Math.max(0, b.getCollectionTime());
        return s;
    }

    static void medirSave(Mundo m, Path arq, Temporada t) {
        long a = System.nanoTime();
        Salvamento.salvar(m, arq);
        t.saveSeg = (System.nanoTime() - a) / 1e9;
        try {
            t.saveMb = Files.size(arq) / 1e6;
        } catch (java.io.IOException ex) {
            t.saveMb = Double.NaN;
        }
    }

    // ================================================================== saída
    static String f(double v) {
        if (Double.isNaN(v)) return "";
        if (v == Math.rint(v) && Math.abs(v) < 1e12) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.3f", v);
    }

    static void gravar(SaudeMundo sm, List<Temporada> hist, Path csv, Path txt, long semente) throws java.io.IOException {
        StringBuilder c = new StringBuilder();
        List<String> cols = new ArrayList<>(hist.get(0).estado.keySet());
        c.append("estado_em_1jan;temporada;").append(String.join(";", cols.subList(1, cols.size())));
        c.append(";aposentados;newgens;falencias;transf_mundo;transf_mundo_inter;transf;transf_pagas;transf_inter;sa_europa;europa_brasil;europa_brasil_ate29;volume_mi;cpu_thread_s;cpu_processo_s;gc_s;parede_s;save_mb;save_s");
        for (String id : LIGAS.keySet()) c.append(";campeao_").append(LIGAS.get(id));
        for (String id : LIGAS.keySet()) c.append(";pos_grandes_").append(LIGAS.get(id));
        c.append(";libertadores;champions;transf_pagas_inter");
        for (String id : POSTO) c.append(";posto_campeao_").append(id);
        c.append('\n');
        for (Temporada t : hist) {
            boolean jogada = t != hist.get(0);
            c.append(f(t.estado.get("ano"))).append(';').append(jogada ? String.valueOf(t.ano) : "");
            for (String k : cols.subList(1, cols.size())) c.append(';').append(f(t.estado.getOrDefault(k, Double.NaN)));
            if (jogada) {
                c.append(String.format(Locale.ROOT, ";%d;%d;%d;%d;%d;%d;%d;%d;%d;%d;%d;%.1f;%.2f;%.2f;%.2f;%.2f", t.aposent, t.newgens, t.falencias, t.transfEstat, t.interEstat, t.transf, t.pagas, t.inter, t.saEur, t.eurBra,
                        t.eurBraJovem, t.volume / 1e6, t.cpuThread, t.cpuProcesso, t.gc, t.parede));
            } else {
                c.append(";;;;;;;;;;;;;;;;");
            }
            c.append(';').append(f(t.saveMb)).append(';').append(f(t.saveSeg));
            for (String id : LIGAS.keySet()) c.append(';').append(t.campeoes.getOrDefault(id, ""));
            for (String id : LIGAS.keySet()) c.append(';').append(t.posGrandes.containsKey(id) ? f(t.posGrandes.get(id)) : "");
            c.append(';').append(t.libertadores == null ? "" : t.libertadores).append(';').append(t.champions == null ? "" : t.champions);
            c.append(';').append(jogada ? String.valueOf(t.pagasInter) : "");
            for (String id : POSTO) c.append(';').append(t.postoCampeao.containsKey(id) ? String.valueOf(t.postoCampeao.get(id)) : "");
            c.append('\n');
        }
        Files.writeString(csv, c.toString(), StandardCharsets.UTF_8);
        Files.writeString(txt, resumo(sm, hist, semente), StandardCharsets.UTF_8);
    }

    static double g(Temporada t, String k) {
        return t.estado.getOrDefault(k, Double.NaN);
    }

    static String resumo(SaudeMundo sm, List<Temporada> hist, long semente) {
        StringBuilder sb = new StringBuilder();
        Temporada a = hist.get(0), z = hist.get(hist.size() - 1);
        int anos = hist.size() - 1;
        sb.append(String.format(Locale.ROOT, "FUTÉBAS — Saúde do mundo | semente %d | %d temporadas jogadas (%d → %d) | estado em 1º/jan%n%n", semente, anos, a.ano + 1, z.ano));
        sb.append("== Nível: top-11 médio por liga (1º/jan)\n");
        sb.append(String.format("  %-8s", "ano"));
        for (String r : LIGAS.values()) sb.append(String.format("%8s", r));
        sb.append(String.format("%8s%8s%7s%7s%7s%7s%7s%n", "média", "p90", "≥80", "≥85", "≥88", "≥90", "idade"));
        for (Temporada t : hist) {
            sb.append(String.format(Locale.ROOT, "  %-8.0f", g(t, "ano")));
            for (String r : LIGAS.values()) sb.append(String.format(Locale.ROOT, "%8.1f", g(t, "top11_" + r)));
            sb.append(String.format(Locale.ROOT, "%8.1f%8.1f%7.0f%7.0f%7.0f%7.0f%7.1f%n", g(t, "forca_media"), g(t, "forca_p90"), g(t, "n80"), g(t, "n85"), g(t, "n88"), g(t, "n90"),
                    g(t, "idade_media")));
        }
        double fortes0 = 0, fortesZ = 0;
        for (String id : FORTES) {
            fortes0 += g(a, "top11_" + LIGAS.get(id));
            fortesZ += g(z, "top11_" + LIGAS.get(id));
        }
        sb.append(String.format(Locale.ROOT, "  top-11 das 5 ligas fortes: %.1f → %.1f (%+.1f)%n", fortes0 / FORTES.size(), fortesZ / FORTES.size(), (fortesZ - fortes0) / FORTES.size()));
        sb.append(grafico("top-11 médio das 5 ligas fortes", hist, t -> {
            double s = 0;
            for (String id : FORTES) s += g(t, "top11_" + LIGAS.get(id));
            return s / FORTES.size();
        }));
        sb.append(grafico("jogadores com força ≥ 85", hist, t -> g(t, "n85")));

        sb.append("\n== Renovação: gerados no jogo (sem id do banco) × reais do banco em 2026, profissionais\n");
        for (Map.Entry<Integer, double[]> e : sm.reais2026.entrySet())
            sb.append(String.format(Locale.ROOT, "  reais com %d anos em 2026: n %.0f | força %.1f (p90 %.1f) | potencial %.1f%n", e.getKey(), e.getValue()[3], e.getValue()[0], e.getValue()[2],
                    e.getValue()[1]));
        sb.append("  ano     aposent newgens | ger21 n  força  pot   p90 | ger25 n  força  pot   p90 | reais21 n força | reais25 n força\n");
        for (Temporada t : hist) {
            sb.append(String.format(Locale.ROOT, "  %-7.0f %7s %7s | %7.0f %6.1f %5.1f %5.1f | %7.0f %6.1f %5.1f %5.1f | %9.0f %5.1f | %9.0f %5.1f%n", g(t, "ano"),
                    t == a ? "-" : String.valueOf(t.aposent), t == a ? "-" : String.valueOf(t.newgens), g(t, "ger21_n"), g(t, "ger21_forca"), g(t, "ger21_pot"), g(t, "ger21_p90"),
                    g(t, "ger25_n"), g(t, "ger25_forca"), g(t, "ger25_pot"), g(t, "ger25_p90"), g(t, "real21_n"), g(t, "real21_forca"), g(t, "real25_n"), g(t, "real25_forca")));
        }

        sb.append("\n== Competição: campeões por temporada\n");
        sb.append(String.format("  %-6s", "temp"));
        for (String r : LIGAS.values()) sb.append(String.format(" %-14s", r));
        sb.append(" | Libertadores | Champions\n");
        Map<String, Map<String, Integer>> porLiga = new LinkedHashMap<>();
        Map<String, Integer> lib = new TreeMap<>(), cha = new TreeMap<>();
        for (Temporada t : hist) {
            if (t == a) continue;
            sb.append(String.format("  %-6d", t.ano));
            for (String id : LIGAS.keySet()) {
                String c = t.campeoes.getOrDefault(id, "-");
                sb.append(String.format(" %-14s", c.length() > 14 ? c.substring(0, 14) : c));
                if (!"-".equals(c)) porLiga.computeIfAbsent(id, x -> new TreeMap<>()).merge(c, 1, Integer::sum);
            }
            sb.append(" | ").append(t.libertadores == null ? "-" : t.libertadores).append(" | ").append(t.champions == null ? "-" : t.champions).append('\n');
            if (t.libertadores != null) lib.merge(t.libertadores, 1, Integer::sum);
            if (t.champions != null) cha.merge(t.champions, 1, Integer::sum);
        }
        sb.append("  variedade (campeões distintos / títulos | maior campeão) e posição média dos 4 grandes de 2026 (fora da 1ª = último+1):\n");
        for (String id : LIGAS.keySet()) {
            Map<String, Integer> mp = porLiga.getOrDefault(id, Map.of());
            int tot = mp.values().stream().mapToInt(Integer::intValue).sum();
            List<Double> pg = new ArrayList<>();
            for (Temporada t : hist) if (t.posGrandes.containsKey(id)) pg.add(t.posGrandes.get(id));
            sb.append(String.format(Locale.ROOT, "    %-7s %2d / %2d | %-40s | grandes %.1f%n", LIGAS.get(id), mp.size(), tot, topo(mp, 3), media(pg)));
        }
        sb.append(String.format("    Libertadores %d distintos: %s%n", lib.size(), topo(lib, 5)));
        sb.append(String.format("    Champions    %d distintos: %s%n", cha.size(), topo(cha, 5)));
        sb.append("  posto de força do campeão (nível do elenco no fim da temporada; média | vezes o mais forte | fora do top 4):\n");
        for (String id : POSTO) {
            List<Double> ps = new ArrayList<>();
            int um = 0, fora4 = 0;
            for (Temporada t : hist) {
                Integer k = t.postoCampeao.get(id);
                if (k == null) continue;
                ps.add((double) k);
                if (k == 1) um++;
                if (k > 4) fora4++;
            }
            if (!ps.isEmpty()) sb.append(String.format(Locale.ROOT, "    %-22s %.1f | %d de %d | %d%n", id, media(ps), um, ps.size(), fora4));
        }

        sb.append("\n== Economia (clubes com divisão nos países detalhados; R$)\n");
        sb.append("  ano     clubes  caixa méd  caixa p50  dívida méd  folha/ano | caixa<0%  dív>receita%  já faliram% | sal. médio  valor méd  valor máx | cesta sal  cesta valor | época\n");
        Temporada ant = null;
        for (Temporada t : hist) {
            sb.append(String.format(Locale.ROOT, "  %-7.0f %6.0f %10.1f %10.1f %11.1f %10.1f | %7.1f %13.1f %12.1f | %8.0f mil %7.2f mi %7.0f mi | %6.0f mil %8.2f mi | %.3f%n", g(t, "ano"),
                    g(t, "clubes_det"), g(t, "caixa_medio_mi"), g(t, "caixa_p50_mi"), g(t, "divida_media_mi"), g(t, "folha_media_mi"), g(t, "caixa_neg_pct"), g(t, "divida_gt_receita_pct"),
                    g(t, "ja_faliram_pct"), g(t, "salario_medio_mil"), g(t, "valor_medio_mi"), g(t, "valor_max_mi"), g(t, "salario_cesta_mil"), g(t, "valor_cesta_mi"), g(t, "fator_epoca")));
            ant = t;
        }
        sb.append("  perfil fixo no mesmo clube (força 72, 26 anos, média dos clubes detalhados) e caixa ÷ receita:\n");
        for (Temporada t : hist)
            sb.append(String.format(Locale.ROOT, "    %-7.0f salário %6.0f mil | valor %6.2f mi | caixa/receita %.2f%n", g(t, "ano"), g(t, "ref72_salario_mil"), g(t, "ref72_valor_mi"),
                    g(t, "caixa_receita_media")));
        sb.append(String.format(Locale.ROOT, "  inflação anual composta (%d anos): salário médio %+.2f%% | valor médio %+.2f%% | cesta salário %+.2f%% | cesta valor %+.2f%% | fator de época %+.2f%%%n", anos,
                cagr(g(a, "salario_medio_mil"), g(z, "salario_medio_mil"), anos), cagr(g(a, "valor_medio_mi"), g(z, "valor_medio_mi"), anos),
                cagr(g(a, "salario_cesta_mil"), g(z, "salario_cesta_mil"), anos), cagr(g(a, "valor_cesta_mi"), g(z, "valor_cesta_mi"), anos),
                cagr(g(a, "fator_epoca"), g(z, "fator_epoca"), anos)));
        int fal = 0;
        for (Temporada t : hist) fal += t.falencias;
        sb.append(String.format("  falências no mundo inteiro: %d em %d temporadas%n", fal, anos));

        sb.append("\n== Volume e desempenho\n");
        sb.append("  ano     clubes  profiss.    base  livres   vivos  arquiv.  no save | CPU thread  CPU proc    GC  parede | save MB  save s\n");
        for (Temporada t : hist) {
            sb.append(String.format(Locale.ROOT, "  %-7.0f %6.0f %9.0f %7.0f %7.0f %7.0f %8.0f %8.0f | %10s %9s %5s %7s | %7s %7s%n", g(t, "ano"), g(t, "clubes"), g(t, "profissionais"), g(t, "base"),
                    g(t, "livres"), g(t, "vivos"), g(t, "arquivados"), g(t, "total_save"), t == a ? "-" : String.format(Locale.ROOT, "%.1f", t.cpuThread),
                    t == a ? "-" : String.format(Locale.ROOT, "%.1f", t.cpuProcesso), t == a ? "-" : String.format(Locale.ROOT, "%.1f", t.gc),
                    t == a ? "-" : String.format(Locale.ROOT, "%.1f", t.parede), Double.isNaN(t.saveMb) ? "-" : String.format(Locale.ROOT, "%.1f", t.saveMb),
                    Double.isNaN(t.saveSeg) ? "-" : String.format(Locale.ROOT, "%.1f", t.saveSeg)));
        }
        sb.append(grafico("profissionais ativos", hist, t -> g(t, "profissionais")));
        sb.append(grafico("CPU da thread por temporada (s)", hist.subList(1, hist.size()), t -> t.cpuThread, true));

        sb.append("\n== Mercado (pela sonda; livres contam pelo último clube)\n");
        sb.append("  temp    transf   pagas  internac  AmSul→Europa  Europa→Brasil (≤29)  volume (R$ bi) | domésticas: todas  pagas\n");
        for (Temporada t : hist) {
            if (t == a) continue;
            sb.append(String.format(Locale.ROOT, "  %-7d %6d (%6d) | %12d %7d %9d %13d %14d (%d) %15.2f | %5.1f%% %5.1f%%%n", t.ano, t.transfEstat, t.interEstat, t.transf, t.pagas, t.inter, t.saEur, t.eurBra, t.eurBraJovem, t.volume / 1e9,
                    100.0 * (t.transf - t.inter) / Math.max(1, t.transf), 100.0 * (t.pagas - t.pagasInter) / Math.max(1, t.pagas)));
        }
        return sb.toString();
    }

    static double cagr(double a, double b, int anos) {
        if (!(a > 0) || !(b > 0) || anos <= 0) return Double.NaN;
        return 100 * (Math.pow(b / a, 1.0 / anos) - 1);
    }

    static String topo(Map<String, Integer> m, int n) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((x, y) -> !y.getValue().equals(x.getValue()) ? y.getValue() - x.getValue() : x.getKey().compareTo(y.getKey()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, l.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(l.get(i).getKey()).append(' ').append(l.get(i).getValue());
        }
        return sb.toString();
    }

    /** Gráfico ASCII de barras horizontais (uma por ano). */
    static String grafico(String titulo, List<Temporada> hist, java.util.function.ToDoubleFunction<Temporada> fn) {
        return grafico(titulo, hist, fn, false);
    }

    static String grafico(String titulo, List<Temporada> hist, java.util.function.ToDoubleFunction<Temporada> fn, boolean porTemporada) {
        double mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
        for (Temporada t : hist) {
            double v = fn.applyAsDouble(t);
            if (Double.isNaN(v)) continue;
            mn = Math.min(mn, v);
            mx = Math.max(mx, v);
        }
        if (mn == Double.MAX_VALUE) return "";
        double lo = mn - (mx - mn) * 0.1 - 1e-9;
        StringBuilder sb = new StringBuilder("  ").append(titulo).append(String.format(Locale.ROOT, " (barra de %.1f a %.1f)%n", lo, mx));
        for (Temporada t : hist) {
            double v = fn.applyAsDouble(t);
            int n = Double.isNaN(v) ? 0 : (int) Math.round(40 * (v - lo) / Math.max(1e-9, mx - lo));
            sb.append(String.format(Locale.ROOT, "    %4.0f %s %.1f%n", porTemporada ? (double) t.ano : g(t, "ano"), "#".repeat(Math.max(0, n)), v));
        }
        return sb.toString();
    }
}
