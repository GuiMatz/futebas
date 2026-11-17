package br.futebas.ui.ferramentas;

import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.dados.BancoMundo;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.modelo.Fluxo;
import br.futebas.ui.telas.Hub;
import br.futebas.ui.telas.TelaFimTemporada;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Testes de desempenho com o banco completo ({@code dados/mundo.sqlite}): o tempo de cada semana
 * ({@code Fluxo.avancarSemana}: média, mediana, pior e a virada de temporada), a primeira abertura da
 * central e de cada painel (1ª e 2ª vez, abas, páginas de competição e de clube, pré-jogo, escalação
 * antes do jogo, fim de temporada), uma carreira só da Kings e a memória depois de várias temporadas.
 * Imprime um relatório e sai com código 1 se algum limite estourar (requisito, 12/11/2026:
 * "telas lentas... faça testes de desempenho").
 *
 * <pre>
 * uso: TestesDesempenho [--semanas 20] [--clube flamengo] [--temporadas 0] [--kings sim] [--fim sim] [--paineis sim]
 *                       [--limite-painel 300] [--limite-semana 4000] [--limite-virada 20000]
 *                       [--saida arquivo.txt]
 * </pre>
 * Os limites valem para a 2ª abertura de cada painel (a 1ª paga imagens e classes) e para as semanas
 * comuns; a semana que vira a temporada tem limite próprio. Rode com o computador folgado: os
 * limites são generosos, mas tempos de parede dependem da máquina. {@code --temporadas 5} mede a
 * memória (heap depois de um GC) ao fim de cada temporada.
 */
public final class TestesDesempenho {
    private TestesDesempenho() {}

    /** frio: 1ª abertura até o 1º quadro; adiados: o que veio nos quadros seguintes (Ui.adiado); quente: melhor das reaberturas. */
    record Medida(String nome, double frioMs, double quenteMs, double adiadosMs) {
        Medida(String nome, double frioMs, double quenteMs) {
            this(nome, frioMs, quenteMs, 0);
        }
    }

    private static final List<String> falhas = new ArrayList<>();
    private static final Sessao[] SESSAO_KINGS = {null};
    private static final long[] CLASSES_CENTRAL = {0};
    /**
     * Como o App (padrão): classes da interface carregadas em segundo plano desde o início, cena de
     * aquecimento e escudos/troféus pré-aquecidos durante a "tela de carregamento" (fora da medida da
     * 1ª central). {@code -Dmedir.aquecer=false}: como era antes (tudo na 1ª central).
     */
    private static final boolean AQUECER = !"false".equals(System.getProperty("medir.aquecer"));
    private static final StringBuilder rel = new StringBuilder();

    public static void main(String[] args) {
        try {
            rodar(args);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(2); // a thread da tela não pode segurar a JVM viva
        }
    }

    private static void rodar(String[] args) throws Exception {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) a.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        int semanas = Integer.parseInt(a.getOrDefault("semanas", "20"));
        String clube = a.getOrDefault("clube", "flamengo");
        int temporadas = Integer.parseInt(a.getOrDefault("temporadas", "0"));
        boolean kings = !"nao".equals(a.getOrDefault("kings", "sim"));
        boolean fimTemporada = !"nao".equals(a.getOrDefault("fim", "sim"));
        boolean comPaineis = !"nao".equals(a.getOrDefault("paineis", "sim"));
        double limPainel = Double.parseDouble(a.getOrDefault("limite-painel", "300"));
        double limSemana = Double.parseDouble(a.getOrDefault("limite-semana", "4000"));
        double limVirada = Double.parseDouble(a.getOrDefault("limite-virada", "20000"));

        linha("FUTÉBAS — testes de desempenho (%s, %d núcleos, heap máx. %d MB, Java %s)", java.time.LocalDateTime.now().withNano(0),
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() >> 20, System.getProperty("java.version"));
        if (AQUECER) {
            Thread aq = new Thread(br.futebas.ui.comp.Aquecimento::classes, "aquecer");
            aq.setDaemon(true);
            aq.start();
        }
        long t0 = System.nanoTime();
        BancoMundo db = Sessao.banco();
        linha("banco: %.1f s (%d clubes, %d jogadores)", seg(t0), db.clubes.size(), db.jogadores.size());

        // ---------------------------------------------------------------- carreira de clube
        t0 = System.nanoTime();
        Jogo j = Jogo.novoJogo(db, 7, clube, "Técnico");
        j.mundo().tecnico.auxiliarTudo = true;
        Sessao s = new Sessao(j, "desempenho");
        linha("jogo novo (%s): %.1f s, %d jogadores no mundo", clube, seg(t0), j.mundo().jogadores.size());
        List<Double> tempos = new ArrayList<>();
        for (int i = 0; i < semanas; i++) {
            long t = System.nanoTime();
            s.fluxo.avancarSemana();
            tempos.add(ms(t));
        }
        resumoSemanas("semanas 1–" + semanas + " (clube)", tempos, limSemana);

        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();

        List<Medida> ms = new ArrayList<>();
        if (AQUECER && comPaineis) {
            long ta = System.nanoTime();
            Hub.prepararCentral(s);
            linha("escudos e troféus pré-aquecidos (na espera): %.0f ms — %s", ms(ta), Imagens.estatisticas());
        }
        if (comPaineis) naTela(() -> {
            App.carregarFontes();
            // a central inteira (topo, navegação e o 1º painel), como ao sair da tela de carregamento
            if (AQUECER) {
                long ta = System.nanoTime();
                br.futebas.ui.comp.Aquecimento.tela(css);
                linha("aquecimento da tela (na espera, antes da central): %.0f ms", ms(ta));
            }
            AtomicReference<Hub> ref = new AtomicReference<>();
            long classes = ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount();
            double primeira = medir(null, () -> {
                Hub h = new Hub(s);
                Scene cena = new Scene(h.raiz(), 1366, 768);
                cena.getStylesheets().add(css);
                h.selecionar("jogo");
                ref.set(h);
            }, () -> ref.get().raiz());
            Hub h = ref.get();
            ms.add(new Medida("central (1ª abertura, com o painel Próximo jogo)", primeira, Double.NaN));
            CLASSES_CENTRAL[0] = ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount() - classes;
            paineis(h, ms);
            for (String c : List.of("bra-1", "est-rj", "conmebol-libertadores", "uefa-champions", "eng-1"))
                pagina(h, "página " + c, () -> h.abrirCompeticao(c), ms);
            for (String c : List.of(clube, "palmeiras", "real-madrid"))
                pagina(h, "clube " + c, () -> h.abrirFichaClube(c), ms);
            List<Partida> prox = j.proximasPartidas(j.meuClube().id, 1);
            if (!prox.isEmpty()) pagina(h, "pré-jogo", () -> h.abrirPreJogo(prox.get(0)), ms);
            pagina(h, "escalação antes do jogo", h::abrirEscalacaoAntesDoJogo, ms);
        });
        if (comPaineis) {
            resumoPaineis("Painéis — carreira de clube (" + clube + ", semana " + j.semana() + ")", ms, limPainel);
            linha("  (a 1ª central carregou %d classes)", CLASSES_CENTRAL[0]);
        }

        // ---------------------------------------------------------------- fim de temporada
        t0 = System.nanoTime();
        Fluxo.ResumoTemporada fim = fimTemporada || temporadas > 0 ? s.fluxo.avancarTemporada() : null;
        if (fim != null) linha("resto da temporada %d (até a virada): %.1f s", j.ano() - 1, seg(t0));
        if (fim != null && comPaineis) {
            List<Medida> mf = new ArrayList<>();
            naTela(() -> {
                AtomicReference<Parent> r = new AtomicReference<>();
                double frio = medir(null, () -> {
                    TelaFimTemporada tf = new TelaFimTemporada(s, fim);
                    new Scene(tf.raiz(), 1366, 768).getStylesheets().add(css);
                    r.set(tf.raiz());
                }, r::get);
                double quente = medir(null, () -> {
                    TelaFimTemporada tf = new TelaFimTemporada(s, fim);
                    new Scene(tf.raiz(), 1366, 768).getStylesheets().add(css);
                    r.set(tf.raiz());
                }, r::get);
                mf.add(new Medida("tela de fim de temporada", frio, quente));
            });
            resumoPaineis("Fim de temporada", mf, limPainel);
        }

        // ---------------------------------------------------------------- várias temporadas: virada e memória
        if (temporadas > 0) {
            linha("");
            linha("%d temporadas (clube, banco completo):", temporadas);
            linha("  %-6s %10s %10s %10s %12s %10s", "ano", "semanas", "média ms", "pior ms", "virada ms", "heap MB");
            int ano = j.ano();
            for (int k = 0; k < temporadas; k++) {
                List<Double> ts = new ArrayList<>();
                double virada = 0;
                int anoIni = j.ano();
                while (j.ano() == anoIni) {
                    long t = System.nanoTime();
                    s.fluxo.avancarSemana();
                    double d = ms(t);
                    if (j.ano() != anoIni) virada = d;
                    else ts.add(d);
                }
                // a semana da virada mede só a montagem da temporada nova quando a temporada acabou na anterior
                double media = ts.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double pior = ts.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                long heap = heapDepoisDoGc();
                linha("  %-6d %10d %10.0f %10.0f %12.0f %10d", anoIni, ts.size(), media, pior, virada, heap >> 20);
                if (pior > limSemana) falhas.add(String.format(Locale.ROOT, "semana mais lenta de %d: %.0f ms > %.0f", anoIni, pior, limSemana));
                if (virada > limVirada) falhas.add(String.format(Locale.ROOT, "virada de %d: %.0f ms > %.0f", anoIni, virada, limVirada));
            }
            linha("  mundo em %d: %d jogadores ativos, %d arquivados", j.ano(), j.mundo().jogadores.size(), j.aposentadosArquivados().size());
            if (ano == j.ano()) linha("  (nenhuma temporada completa)");
        }

        // ---------------------------------------------------------------- carreira só da Kings
        if (kings) {
            String time = null;
            for (var kt : db.kingsTimes.values()) if ("kings-brasil".equals(kt.ligaId())) { time = kt.id(); break; }
            if (time == null && !db.kingsTimes.isEmpty()) time = db.kingsTimes.keySet().iterator().next();
            if (time != null) {
                t0 = System.nanoTime();
                Jogo jk = Jogo.novoJogoKings(db, 11, null, time, "Técnico");
                jk.mundo().tecnico.auxiliarTudo = true;
                Sessao sk = new Sessao(jk, "desempenho-kings");
                SESSAO_KINGS[0] = sk;
                linha("");
                linha("jogo novo só da Kings (%s): %.1f s", time, seg(t0));
                List<Double> tk = new ArrayList<>();
                for (int i = 0; i < semanas; i++) {
                    long t = System.nanoTime();
                    sk.fluxo.avancarSemana();
                    tk.add(ms(t));
                }
                resumoSemanas("semanas 1–" + semanas + " (Kings)", tk, limSemana);
                if (!sk.fluxo.soKings()) {
                    linha("  (o técnico ficou sem time da Kings na semana %d: painéis da Kings não medidos)", jk.semana());
                    time = null;
                }
            }
            if (time != null && comPaineis) {
                Sessao sk = SESSAO_KINGS[0];
                if (AQUECER) Hub.prepararCentral(sk);
                List<Medida> mk = new ArrayList<>();
                naTela(() -> {
                    AtomicReference<Hub> ref = new AtomicReference<>();
                    double primeira = medir(null, () -> {
                        Hub h = new Hub(sk);
                        new Scene(h.raiz(), 1366, 768).getStylesheets().add(css);
                        h.selecionar("kings");
                        ref.set(h);
                    }, () -> ref.get().raiz());
                    mk.add(new Medida("central (1ª abertura, painel Kings League)", primeira, Double.NaN));
                    paineis(ref.get(), mk);
                });
                resumoPaineis("Painéis — carreira só da Kings (" + time + ")", mk, limPainel);
            }
        }

        linha("");
        linha("cache de imagens: %s", Imagens.estatisticas());
        if (falhas.isEmpty()) linha("RESULTADO: OK (nenhum limite estourado)");
        else {
            linha("RESULTADO: FALHOU");
            for (String f : falhas) linha("  - %s", f);
        }
        String saida = a.get("saida");
        if (saida != null) Files.writeString(Path.of(saida), rel.toString(), StandardCharsets.UTF_8);
        Platform.exit();
        System.exit(falhas.isEmpty() ? 0 : 1);
    }

    // ------------------------------------------------------------------ painéis
    /** Repetições da 2ª abertura (vale a melhor): com o computador ocupado, uma só medida engana. */
    private static final int REPETICOES = Integer.getInteger("medir.repeticoes", 3);

    private static double quente(Hub h, Runnable r) {
        double melhor = Double.MAX_VALUE;
        for (int i = 0; i < REPETICOES; i++) melhor = Math.min(melhor, medir(h, r, null));
        return melhor;
    }

    private static void paineis(Hub h, List<Medida> ms) {
        for (String p : h.paineis()) {
            if (!h.painelVisivel(p)) continue;
            double frio = medir(h, () -> h.selecionar(p), null);
            double adiados = medir(h, br.futebas.ui.comp.Ui::montarAdiados, null);
            double quente = quente(h, () -> h.selecionar(p));
            ms.add(new Medida(p, frio, quente, adiados));
            abas(h, p, ms);
        }
    }

    private static void pagina(Hub h, String nome, Runnable abrir, List<Medida> ms) {
        double frio = medir(h, abrir, null);
        double adiados = medir(h, br.futebas.ui.comp.Ui::montarAdiados, null);
        double quente = quente(h, abrir);
        ms.add(new Medida(nome, frio, quente, adiados));
        abas(h, nome, ms);
    }

    /** Troca cada aba de cada TabPane visível e mede (a 2ª vez volta à aba e entra de novo). */
    private static void abas(Hub h, String prefixo, List<Medida> ms) {
        List<TabPane> tps = new ArrayList<>();
        juntar(h.raiz(), tps);
        Set<TabPane> feitos = Collections.newSetFromMap(new IdentityHashMap<>());
        for (TabPane tp : tps) {
            if (!feitos.add(tp) || !tp.isVisible()) continue;
            int sel = tp.getSelectionModel().getSelectedIndex();
            for (int i = 0; i < tp.getTabs().size(); i++) {
                if (i == sel) continue;
                int k = i;
                double t = medir(h, () -> tp.getSelectionModel().select(k), null);
                double adiados = medir(h, br.futebas.ui.comp.Ui::montarAdiados, null);
                ms.add(new Medida(prefixo + " › " + tp.getTabs().get(i).getText(), t, Double.NaN, adiados));
            }
            tp.getSelectionModel().select(sel);
        }
    }

    private static void juntar(Node n, List<TabPane> l) {
        if (n instanceof TabPane tp) l.add(tp);
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) juntar(c, l);
    }

    /** Tempo de {@code r} + CSS + layout da raiz (a da central, ou a que {@code raiz} der). */
    private static double medir(Hub h, Runnable r, java.util.function.Supplier<Parent> raiz) {
        long t = System.nanoTime();
        r.run();
        Parent p = h != null ? h.raiz() : raiz.get();
        p.applyCss();
        p.layout();
        return ms(t);
    }

    // ------------------------------------------------------------------ relatório
    private static void resumoSemanas(String titulo, List<Double> ts, double limite) {
        if (ts.isEmpty()) return;
        List<Double> o = new ArrayList<>(ts);
        Collections.sort(o);
        double media = o.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double pior = o.get(o.size() - 1);
        int qual = ts.indexOf(pior) + 1;
        linha("%s: média %.0f ms, mediana %.0f ms, p90 %.0f ms, pior %.0f ms (semana %d)", titulo, media, o.get(o.size() / 2),
                o.get(Math.min(o.size() - 1, (int) Math.ceil(o.size() * 0.9) - 1)), pior, qual);
        if (pior > limite) falhas.add(String.format(Locale.ROOT, "%s: pior semana %.0f ms > %.0f", titulo, pior, limite));
    }

    private static void resumoPaineis(String titulo, List<Medida> ms, double limite) {
        linha("");
        linha("%s", titulo);
        linha("  %-60s %9s %9s %9s", "painel", "1ª (ms)", "2ª (ms)", "+adiados");
        for (Medida m : ms) {
            boolean estourou = !Double.isNaN(m.quenteMs()) && m.quenteMs() > limite;
            linha("  %-60s %9.0f %9s %9s%s", m.nome(), m.frioMs(), Double.isNaN(m.quenteMs()) ? "" : String.format(Locale.ROOT, "%.0f", m.quenteMs()),
                    m.adiadosMs() >= 1 ? String.format(Locale.ROOT, "%.0f", m.adiadosMs()) : "",
                    estourou ? "   <-- acima do limite" : Math.max(m.frioMs(), Double.isNaN(m.quenteMs()) ? 0 : m.quenteMs()) > 150 ? "   (lento)" : "");
            if (estourou) falhas.add(String.format(Locale.ROOT, "%s: 2ª abertura %.0f ms > %.0f", m.nome(), m.quenteMs(), limite));
        }
    }

    private static void linha(String f, Object... a) {
        String s = String.format(Locale.ROOT, f, a);
        System.out.println(s);
        rel.append(s).append('\n');
    }

    private static double ms(long t) { return (System.nanoTime() - t) / 1e6; }

    private static double seg(long t) { return (System.nanoTime() - t) / 1e9; }

    static long heapDepoisDoGc() {
        for (int i = 0; i < 3; i++) System.gc();
        long s = 0;
        for (MemoryPoolMXBean b : ManagementFactory.getMemoryPoolMXBeans())
            if (b.getType() == MemoryType.HEAP && b.getUsage() != null) s += b.getUsage().getUsed();
        return s;
    }

    private static void naTela(Runnable r) throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch l = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                erro.set(t);
            } finally {
                l.countDown();
            }
        });
        if (!l.await(30, TimeUnit.MINUTES)) throw new IllegalStateException("a tela não respondeu");
        if (erro.get() != null) throw new IllegalStateException(erro.get());
    }
}
