package br.futebas.ferramentas;

import br.futebas.dados.BancoMundo;
import br.futebas.dados.Esquema;
import br.futebas.historia.Historia;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Origem;
import br.futebas.mundo.Simulador;
import br.futebas.mundo.StatusJogador;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Simulação longa sem interface: roda N mundos (sementes) de 1º/jan/2026 até o ano final e mede
 * se o mundo evolui de forma plausível e rápida.
 *
 * <pre>
 * uso: LongRun [--banco caminho] [--mundos 2] [--fim 2060] [--extras 34] [--saida tools/longrun-resultado.txt]
 * </pre>
 * Sem {@code --banco}, usa {@code dados/mundo.sqlite} se estiver no esquema v2; senão cria um
 * banco FICTÍCIO v2 com {@code --extras} países sintéticos (34 ≈ 1.500 clubes, 35 mil jogadores).
 * Rode com {@code -Xmx2g}.
 */
public final class LongRun {
    private LongRun() {}

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) a.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        Path raiz = acharRaiz();
        Path banco = a.containsKey("banco") ? Paths.get(a.get("banco")) : raiz.resolve("dados").resolve("mundo.sqlite");
        boolean ficticio = false;
        int extras = Integer.parseInt(a.getOrDefault("extras", "34"));
        if (BancoMundo.versaoDoArquivo(banco) < Esquema.VERSAO) {
            banco = Files.createTempDirectory("futebas-longrun").resolve("ficticio.sqlite");
            BancoFicticio.criar(banco, 42, extras);
            ficticio = true;
        }
        int mundos = Integer.parseInt(a.getOrDefault("mundos", "2"));
        int fim = Integer.parseInt(a.getOrDefault("fim", "2060"));
        Path saida = Paths.get(a.getOrDefault("saida", raiz.resolve("tools").resolve("longrun-resultado.txt").toString()));
        String texto = rodar(banco, ficticio, mundos, fim);
        System.out.println(texto);
        Files.createDirectories(saida.getParent());
        Files.writeString(saida, texto, StandardCharsets.UTF_8);
        System.out.println("Resultado gravado em " + saida.toAbsolutePath());
    }

    public static Path acharRaiz() {
        Path p = Paths.get("").toAbsolutePath();
        for (Path x = p; x != null; x = x.getParent()) if (Files.exists(x.resolve("jogo").resolve("pom.xml"))) return x;
        return p;
    }

    static long heapUsado() {
        long s = 0;
        for (MemoryPoolMXBean b : ManagementFactory.getMemoryPoolMXBeans()) {
            if (b.getType() == MemoryType.HEAP && b.getUsage() != null) s += b.getUsage().getUsed();
        }
        return s;
    }

    public static String rodar(Path banco, boolean ficticio, int mundos, int fim) {
        long t0 = System.nanoTime();
        BancoMundo db = BancoMundo.carregar(banco);
        long tCarga = System.nanoTime() - t0;
        StringBuilder out = new StringBuilder();
        out.append("FUTÉBAS - LongRun\n");
        out.append("Banco: ").append(banco).append(ficticio ? "  (FICTÍCIO v2: dados/mundo.sqlite v2 ainda não existe)" : "").append('\n');
        out.append(String.format("Países: %d | clubes: %d | jogadores: %d (descartados sem fonte: %d) | competições: %d | carga: %.1f s%n",
                db.paises.size(), db.clubes.size(), db.jogadores.size(), db.jogadoresSemFonte, db.competicoes.size(), tCarga / 1e9));
        out.append(String.format("Mundos: %d | 2026 -> %d | heap máximo da JVM: %d MB%n%n", mundos, fim, Runtime.getRuntime().maxMemory() >> 20));

        List<Double> tempos = new ArrayList<>();
        long picoHeap = 0, picoRetido = 0;
        long tamanhoSave = 0;
        double tSalvar = 0, tCarregar = 0;
        Map<String, Map<String, Integer>> campeoesPorLiga = new TreeMap<>();
        Map<String, Integer> champions = new TreeMap<>(), libertadores = new TreeMap<>(), copas = new TreeMap<>();
        List<String> copasLinha = new ArrayList<>();
        long golsLiga = 0, jogosLiga = 0, vm = 0, emp = 0, vv = 0, golsSel = 0, jogosSel = 0;
        int falMax = 0;
        Map<Integer, int[]> falPorDecada = new TreeMap<>();
        long newgens = 0, falencias = 0, transf = 0, internac = 0, leiloes = 0, aposent = 0;
        double somaElenco = 0;
        int anos = 0;
        long detalhadas = 0, rapidas = 0;
        double tCriacaoTotal = 0;
        Map<Integer, int[]> ativosPorAno = new TreeMap<>();
        List<String> ligasGrandes = ligasGrandes(db);

        for (int w = 0; w < mundos; w++) {
            long semente = 1000 + w * 7919L;
            long tc = System.nanoTime();
            Simulador s = Simulador.novo(db, semente, null, null);
            double tCriacao = (System.nanoTime() - tc) / 1e9;
            tCriacaoTotal += tCriacao;
            Mundo m = s.m;
            out.append(String.format("== Mundo %d (semente %d): criado em %.1f s | %d clubes, %d jogadores, %d países detalhados%n",
                    w + 1, semente, tCriacao, m.clubes.size(), m.jogadores.size(), m.paises.values().stream().filter(p -> p.detalhado).count()));
            while (m.ano <= fim) {
                int ano = m.ano;
                long ti = System.nanoTime();
                s.avancarTemporada();
                tempos.add((System.nanoTime() - ti) / 1e9);
                long pico = heapUsado();
                System.gc();
                long retido = heapUsado();
                picoHeap = Math.max(picoHeap, pico);
                picoRetido = Math.max(picoRetido, retido);
                Historia.EstatAno e = m.historia.estat(ano);
                System.out.printf(Locale.ROOT, "  [%d] %d: %.1f s | heap %d MB (retido %d MB) | ativos+recentes %d, arquivados %d | gols liga %.2f, seleções %.2f%n",
                        w + 1, ano, tempos.get(tempos.size() - 1), pico >> 20, retido >> 20, m.jogadores.size(), m.arquivados.size(),
                        e.golsLiga / (double) Math.max(1, e.jogosLiga), e.golsSelecoes / (double) Math.max(1, e.jogosSelecoes));
                golsLiga += e.golsLiga;
                jogosLiga += e.jogosLiga;
                vm += e.vitMandante;
                emp += e.empates;
                vv += e.vitVisitante;
                golsSel += e.golsSelecoes;
                jogosSel += e.jogosSelecoes;
                newgens += e.newgens;
                falencias += e.falencias;
                falMax = Math.max(falMax, e.falencias);
                int[] fd = falPorDecada.computeIfAbsent(ano / 10 * 10, k -> new int[2]);
                fd[0] += e.falencias;
                fd[1]++;
                transf += e.transferencias;
                internac += e.transferenciasInternacionais;
                leiloes += e.leiloes;
                aposent += e.aposentadorias;
                somaElenco += e.elencoMedio;
                anos++;
                int[] at = ativosPorAno.computeIfAbsent(ano, k -> new int[2]);
                at[0] += e.jogadoresAtivos;
                at[1]++;
            }
            detalhadas += s.partidasDetalhadas;
            rapidas += s.partidasRapidas;
            for (Historia.Titulo t : m.historia.titulos) {
                if (ligasGrandes.contains(t.competicaoId)) campeoesPorLiga.computeIfAbsent(t.competicaoId, k -> new TreeMap<>()).merge(t.campeao, 1, Integer::sum);
                if (t.competicaoId.equals("uefa-champions")) champions.merge(t.campeao + " (" + paisDe(m, t.campeaoId) + ")", 1, Integer::sum);
                if (t.competicaoId.equals("conmebol-libertadores")) libertadores.merge(t.campeao + " (" + paisDe(m, t.campeaoId) + ")", 1, Integer::sum);
                if (t.competicaoId.equals("fifa-copa-do-mundo")) {
                    copas.merge(t.campeao, 1, Integer::sum);
                    if (w == 0) copasLinha.add(t.ano + ": " + t.campeao + " (vice " + t.vice + ")");
                }
            }
            if (w == 0) {
                out.append("  Copas do Mundo do mundo 1:\n");
                for (String c : copasLinha) out.append("    ").append(c).append('\n');
                int reais = 0, gerados = 0, ativos = 0;
                for (Jogador j : m.jogadores) {
                    if (j.status != StatusJogador.PROFISSIONAL) continue;
                    ativos++;
                    if (j.origem == Origem.REAL) reais++;
                    else if (j.origem == Origem.GERADO) gerados++;
                }
                out.append(String.format("  Em %d: %d profissionais (%d reais do banco, %d gerados). Jogadores no mundo: %d vivos + %d aposentados arquivados%n",
                        fim, ativos, reais, gerados, m.jogadores.size(), m.arquivados.size()));
                try {
                    java.nio.file.Path tmp = java.nio.file.Files.createTempFile("futebas-longrun", br.futebas.mundo.Salvamento.EXTENSAO);
                    long ts = System.nanoTime();
                    br.futebas.mundo.Salvamento.salvar(m, tmp);
                    tSalvar = (System.nanoTime() - ts) / 1e9;
                    tamanhoSave = java.nio.file.Files.size(tmp);
                    ts = System.nanoTime();
                    br.futebas.mundo.Salvamento.carregar(tmp);
                    tCarregar = (System.nanoTime() - ts) / 1e9;
                    java.nio.file.Files.deleteIfExists(tmp);
                } catch (java.io.IOException ex) {
                    out.append("  (falha ao medir o save: ").append(ex.getMessage()).append(")\n");
                }
                Clube maior = null;
                for (Clube c : m.clubes.values()) if (maior == null || c.dinheiro > maior.dinheiro) maior = c;
                if (maior != null) out.append("  Clube mais rico em ").append(fim).append(": ").append(maior.nome).append(" (")
                        .append(br.futebas.financas.Financas.formatar(maior.dinheiro)).append(")\n");
            }
        }
        out.append('\n');
        out.append("== Partidas\n");
        out.append(String.format(Locale.ROOT, "  Ligas: %.2f gols/jogo em %d jogos | mandante %.1f%% | empate %.1f%% | visitante %.1f%%%n",
                golsLiga / (double) Math.max(1, jogosLiga), jogosLiga, 100.0 * vm / Math.max(1, jogosLiga), 100.0 * emp / Math.max(1, jogosLiga),
                100.0 * vv / Math.max(1, jogosLiga)));
        out.append(String.format(Locale.ROOT, "  Seleções: %.2f gols/jogo em %d jogos (sem as datas FIFA rápidas)%n", golsSel / (double) Math.max(1, jogosSel), jogosSel));
        out.append(String.format(Locale.ROOT, "  Lance a lance: %d | rápidas: %d (%.0f%% rápidas)%n", detalhadas, rapidas,
                100.0 * rapidas / Math.max(1, detalhadas + rapidas)));
        out.append("\n== Campeões distintos nas principais ligas (todas as sementes)\n");
        for (Map.Entry<String, Map<String, Integer>> e : campeoesPorLiga.entrySet()) {
            int total = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
            out.append(String.format("  %-10s %2d campeões distintos em %d temporadas | mais títulos: %s%n", e.getKey(), e.getValue().size(), total, topo(e.getValue(), 3)));
        }
        out.append("\n== Champions (fase de liga de 36): ").append(champions.size()).append(" campeões distintos | ").append(topo(champions, 6)).append('\n');
        out.append("== Libertadores: ").append(libertadores.size()).append(" campeões distintos | ").append(topo(libertadores, 6)).append('\n');
        out.append("== Copa do Mundo: ").append(copas.size()).append(" campeões distintos | ").append(topo(copas, 8)).append('\n');
        out.append("\n== Mundo (médias por ano)\n");
        out.append(String.format(Locale.ROOT, "  newgens (peneira): %.0f | aposentadorias: %.0f | elenco médio: %.1f | falências: %.2f%n",
                newgens / (double) anos, aposent / (double) anos, somaElenco / anos, falencias / (double) anos));
        StringBuilder fdx = new StringBuilder("  falências por ano, média por década: ");
        for (Map.Entry<Integer, int[]> x : falPorDecada.entrySet()) fdx.append(String.format(Locale.ROOT, "%ds %.2f  ", x.getKey(), x.getValue()[0] / (double) x.getValue()[1]));
        out.append(fdx).append(String.format(Locale.ROOT, "| pior ano: %d%n", falMax));
        out.append(String.format(Locale.ROOT, "  transferências: %.0f (internacionais %.0f) | leilões: %.1f%n", transf / (double) anos, internac / (double) anos,
                leiloes / (double) anos));
        StringBuilder at = new StringBuilder();
        for (Map.Entry<Integer, int[]> e : ativosPorAno.entrySet()) {
            if ((e.getKey() - 2026) % 6 == 0) at.append(e.getKey()).append(": ").append(e.getValue()[0] / e.getValue()[1]).append("  ");
        }
        out.append("  profissionais ativos: ").append(at).append('\n');
        List<Double> ord = new ArrayList<>(tempos);
        ord.sort(null);
        double total = tempos.stream().mapToDouble(Double::doubleValue).sum();
        out.append("\n== Desempenho\n");
        out.append(String.format(Locale.ROOT, "  criação do mundo: %.1f s em média | %d temporadas em %.1f s | mediana %.2f s por temporada (máx %.2f s)%n",
                tCriacaoTotal / mundos, tempos.size(), total, ord.isEmpty() ? 0 : ord.get(ord.size() / 2), ord.isEmpty() ? 0 : ord.get(ord.size() - 1)));
        out.append(String.format(Locale.ROOT, "  heap usado no pico: %d MB; retido depois do GC (pico): %d MB (máximo da JVM %d MB)%n", picoHeap >> 20, picoRetido >> 20,
                Runtime.getRuntime().maxMemory() >> 20));
        out.append(String.format(Locale.ROOT, "  save do mundo 1 em %d: %.1f MB (gzip), salvar %.1f s, carregar %.1f s%n", fim, tamanhoSave / 1e6, tSalvar, tCarregar));
        return out.toString();
    }

    private static String paisDe(Mundo m, String clubeId) {
        Clube c = m.clube(clubeId);
        return c == null ? "?" : c.pais;
    }

    /** Primeiras divisões dos maiores países (até 6) para medir a variedade de campeões. */
    static List<String> ligasGrandes(BancoMundo db) {
        Map<String, Double> peso = new LinkedHashMap<>();
        for (var c : db.competicoes.values()) {
            if (!"nacional".equals(c.ambito()) || !Integer.valueOf(1).equals(c.divisao())) continue;
            double s = 0;
            for (String id : db.participantesIniciais(c.id())) {
                var cl = db.clubes.get(id);
                if (cl != null && cl.reputacao() != null) s += cl.reputacao();
            }
            peso.put(c.id(), s);
        }
        List<String> l = new ArrayList<>(peso.keySet());
        l.sort((x, y) -> Double.compare(peso.get(y), peso.get(x)));
        return new ArrayList<>(l.subList(0, Math.min(6, l.size())));
    }

    private static String topo(Map<String, Integer> m, int n) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> b.getValue() != a.getValue().intValue() ? b.getValue() - a.getValue() : a.getKey().compareTo(b.getKey()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, l.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(l.get(i).getKey()).append(' ').append(l.get(i).getValue());
        }
        return sb.toString();
    }
}
