package br.futebas.ui.ferramentas;

import br.futebas.Jogo;
import br.futebas.dados.BancoMundo;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.telas.Hub;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mede quanto cada painel da central leva para abrir na thread da tela (montar + CSS + layout),
 * com o banco completo ({@code dados/mundo.sqlite}) numa carreira de clube no meio da temporada.
 * Requisito (12/11/2026): "alguns menus estão travando para abrir". Uso:
 * {@code java -cp ... br.futebas.ferramentas.MedirPaineis [semanas=20] [clube=flamengo]}.
 * Cada painel é aberto duas vezes (a 1ª paga o carregamento de imagens) e cada aba é trocada.
 */
public final class MedirPaineis {
    private MedirPaineis() {}

    record Medida(String nome, double frioMs, double quenteMs) {}

    public static void main(String[] args) throws Exception {
        int semanas = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        String clube = args.length > 1 ? args[1] : "flamengo";
        long t0 = System.nanoTime();
        BancoMundo db = Sessao.banco();
        System.out.printf(Locale.ROOT, "banco: %.1f s, %d jogadores no banco%n", (System.nanoTime() - t0) / 1e9, db.jogadores.size());
        t0 = System.nanoTime();
        Jogo j = Jogo.novoJogo(db, 7, clube, "Técnico");
        j.mundo().tecnico.auxiliarTudo = true;
        for (int i = 0; i < semanas; i++) j.avancarSemana();
        System.out.printf(Locale.ROOT, "carreira: %d semanas em %.1f s (%s, semana %d, %d jogadores no mundo)%n", semanas,
                (System.nanoTime() - t0) / 1e9, j.mundo().ano, j.mundo().semana, j.mundo().jogadores.size());
        Sessao s = new Sessao(j, "medir");
        CountDownLatch ini = new CountDownLatch(1);
        Platform.startup(ini::countDown);
        ini.await(10, TimeUnit.SECONDS);
        List<Medida> ms = new ArrayList<>();
        naTela(() -> {
            String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
            Hub h = new Hub(s);
            Scene cena = new Scene(h.raiz(), 1366, 768);
            cena.getStylesheets().add(css);
            for (String p : h.paineis()) {
                rotulo = p;
                double frio = medir(h, () -> h.selecionar(p));
                double quente = medir(h, () -> h.selecionar(p));
                ms.add(new Medida(p, frio, quente));
                abas(h, p, ms);
                if (p.equals("mercado")) {
                    Button b = achar(h.raiz(), "Buscar");
                    if (b != null) ms.add(new Medida("mercado › Buscar (sem filtro)", medir(h, b::fire), medir(h, b::fire)));
                }
            }
            for (String c : List.of("bra-1", "est-rj", "conmebol-libertadores", "uefa-champions", "eng-1", "taca-atlantica")) {
                rotulo = "página " + c;
                double frio = medir(h, () -> h.abrirCompeticao(c));
                double quente = medir(h, () -> h.abrirCompeticao(c));
                ms.add(new Medida("página " + c, frio, quente));
                abas(h, "página " + c, ms);
            }
            for (String c : List.of(clube, "palmeiras", "real-madrid")) {
                rotulo = "clube " + c;
                double frio = medir(h, () -> h.abrirFichaClube(c));
                double quente = medir(h, () -> h.abrirFichaClube(c));
                ms.add(new Medida("clube " + c, frio, quente));
                abas(h, "clube " + c, ms);
            }
        });
        System.out.println();
        System.out.printf(Locale.ROOT, "%-58s %9s %9s%n", "painel", "1ª (ms)", "2ª (ms)");
        for (Medida m : ms)
            System.out.printf(Locale.ROOT, "%-58s %9.0f %9.0f%s%n", m.nome(), m.frioMs(), m.quenteMs(), Math.max(m.frioMs(), m.quenteMs()) > 150 ? "   <-- lento" : "");
        Platform.exit();
        System.exit(0);
    }

    /** Troca cada aba de cada TabPane visível e mede (abas aninhadas também). */
    private static void abas(Hub h, String prefixo, List<Medida> ms) {
        List<TabPane> tps = new ArrayList<>();
        juntar(h.raiz(), tps);
        java.util.Set<TabPane> feitos = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (TabPane tp : tps) {
            if (!feitos.add(tp) || !tp.isVisible()) continue;
            int sel = tp.getSelectionModel().getSelectedIndex();
            for (int i = 0; i < tp.getTabs().size(); i++) {
                if (i == sel) continue;
                int k = i;
                String nome = tp.getTabs().get(i).getText();
                rotulo = prefixo + " > " + nome;
                double t = medir(h, () -> tp.getSelectionModel().select(k));
                ms.add(new Medida(prefixo + " › " + nome, t, Double.NaN));
            }
            tp.getSelectionModel().select(sel);
        }
    }

    private static void juntar(Node n, List<TabPane> l) {
        if (n instanceof TabPane tp) l.add(tp);
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) juntar(c, l);
    }

    private static Button achar(Node n, String texto) {
        if (n instanceof Button b && texto.equals(b.getText()) && b.isVisible()) return b;
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) {
            Button r = achar(c, texto);
            if (r != null) return r;
        }
        return null;
    }

    /** Janelas de tempo (relógio de parede) de cada medida, para cruzar com um perfil do JFR. */
    static final boolean JANELAS = Boolean.getBoolean("medir.janelas");
    static String rotulo = "";

    private static double medir(Hub h, Runnable r) {
        long w = System.currentTimeMillis();
        long t = System.nanoTime();
        r.run();
        h.raiz().applyCss();
        h.raiz().layout();
        double ms = (System.nanoTime() - t) / 1e6;
        if (JANELAS) System.err.println("JANELA " + w + " " + System.currentTimeMillis() + " " + rotulo);
        return ms;
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
