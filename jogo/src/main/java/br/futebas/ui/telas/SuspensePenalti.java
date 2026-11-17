package br.futebas.ui.telas;

import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Suspense do pênalti (requisito: "foi para a bola, bateu, espera um pouco"): cada cobrança
 * em três passos — "X vai para a bola…", "Bateu…" e o desfecho ("GOL!", "DEFENDEU FULANO!",
 * "ISOLOU!"...), com frases variadas pelo que aconteceu. Peças reaproveitáveis (a Kings usa as mesmas):
 * <ul>
 *   <li>{@link #desfecho}, {@link #fraseIda}, {@link #fraseBatida}, {@link #fraseResultado}: as frases;</li>
 *   <li>{@link #tocar}: os três passos num {@code Label}, com as esperas da velocidade (zero = na hora);</li>
 *   <li>{@link Faixa}: o letreiro no meio da tela para o pênalti durante o jogo;</li>
 *   <li>{@link Disputa}: o painel da disputa de pênaltis (bolinhas verde/vermelha/cinza, placar, morte súbita).</li>
 * </ul>
 * As esperas vêm de {@link Velocidade#suspensePenalti()}; "SIMULAR ATÉ O FIM" chama {@code pular()}.
 */
public final class SuspensePenalti {
    private SuspensePenalti() {}

    /** Como terminou a cobrança. */
    public enum Desfecho {
        GOL, DEFESA, TRAVE, TRAVESSAO, FORA, ISOLOU, ESCORREGOU;

        public boolean gol() { return this == GOL; }
    }

    private static final Random SORTE = new Random();

    // ------------------------------------------------------------------ frases (banco comum: partida.Frases)
    /** As frases do pênalti vêm do banco comum (o mesmo da Kings), sem repetir a mesma em seguida. */
    static final br.futebas.partida.Frases FRASES = new br.futebas.partida.Frases(System.nanoTime());

    /**
     * Desfecho pelo "jeito" do motor ("angulo", "canto", "deslocou", "meio", "cavadinha", "defendeu",
     * "milagre", "isolou", "trave", "fora"; null = sorteia dentro de gol/erro), com um pouco de sorte:
     * trave às vezes é travessão; fora, às vezes "escorregou".
     */
    public static Desfecho desfecho(boolean gol, String jeito) {
        if (gol) return Desfecho.GOL;
        String j = jeito == null ? "" : jeito;
        return switch (j) {
            case "defendeu", "milagre" -> Desfecho.DEFESA;
            case "trave" -> SORTE.nextDouble() < 0.4 ? Desfecho.TRAVESSAO : Desfecho.TRAVE;
            case "isolou" -> SORTE.nextDouble() < 0.12 ? Desfecho.ESCORREGOU : Desfecho.ISOLOU;
            case "fora" -> SORTE.nextDouble() < 0.10 ? Desfecho.ESCORREGOU : Desfecho.FORA;
            default -> SORTE.nextDouble() < 0.7 ? Desfecho.DEFESA : Desfecho.FORA;
        };
    }

    /** Desfecho de um lance de pênalti no jogo (tipo do lance + texto da narração). */
    static Desfecho desfechoDoLance(String tipo, String texto) {
        return switch (tipo) {
            case "gol" -> Desfecho.GOL;
            case "defesa", "milagre" -> Desfecho.DEFESA;
            case "trave" -> desfecho(false, "trave");
            default -> desfecho(false, texto != null && texto.contains("isola") ? "isolou" : "fora");
        };
    }

    public static String fraseIda(String batedor) {
        return FRASES.penaltiIda(batedor);
    }

    private static final String[] SHOOTOUT_IDA = {"{j} sai do meio-campo e parte para cima do goleiro…",
            "{j} arranca do meio-campo, cara a cara com o goleiro…", "{j} conduz desde o meio-campo…",
            "{j} sai em velocidade do meio-campo…"};
    private static int ultimaIdaShootout = -1;

    /** Shoot out da Kings: o batedor sai do meio-campo contra o goleiro (sem repetir a frase em seguida). */
    public static String fraseIdaShootout(String batedor) {
        int i;
        do i = SORTE.nextInt(SHOOTOUT_IDA.length); while (i == ultimaIdaShootout);
        ultimaIdaShootout = i;
        return SHOOTOUT_IDA[i].replace("{j}", batedor == null || batedor.isBlank() ? "O batedor" : batedor);
    }

    public static String fraseBatida() {
        return FRASES.penaltiBatida();
    }

    public static String fraseResultado(Desfecho d, String goleiro) {
        return FRASES.penaltiResultado(switch (d) {
            case GOL -> "gol";
            case DEFESA -> "defesa";
            case TRAVE -> "trave";
            case TRAVESSAO -> "travessao";
            case ISOLOU -> "isolou";
            case ESCORREGOU -> "escorregou";
            case FORA -> "fora";
        }, goleiro);
    }

    // ------------------------------------------------------------------ os três passos
    /** Uma sequência em andamento (dá para pular: termina na hora). */
    public static final class Toque {
        private final List<PauseTransition> pausas = new ArrayList<>();
        private Runnable fim;
        private boolean acabou;

        /** Pula as esperas: mostra o desfecho e chama o fim já. */
        public void pular() {
            for (PauseTransition p : pausas) p.stop();
            terminar();
        }

        private void terminar() {
            if (acabou) return;
            acabou = true;
            if (fim != null) fim.run();
        }

        public boolean acabou() { return acabou; }
    }

    /**
     * Mostra em {@code alvo} os três passos (ida, batida, resultado) com as esperas {@code ms}
     * ({vai para a bola, bateu, resultado na tela}); ao terminar, chama {@code aoMostrarResultado} (no
     * passo 3, para o placar mudar junto) e depois {@code fim}. Esperas zeradas = tudo na hora.
     */
    public static Toque tocar(Label alvo, String ida, String batida, String resultado, boolean gol, long[] ms,
                              Runnable aoMostrarResultado, Runnable fim) {
        return tocar(alvo, ida, batida, resultado, gol, ms, aoMostrarResultado, null, fim);
    }

    /** Idem, avisando cada passo mostrado ({@code aoPasso}: ida, batida e resultado), para a narração. */
    public static Toque tocar(Label alvo, String ida, String batida, String resultado, boolean gol, long[] ms,
                              Runnable aoMostrarResultado, java.util.function.Consumer<String> aoPasso, Runnable fim) {
        java.util.function.Consumer<String> passo = aoPasso == null ? x -> { } : aoPasso;
        boolean[] avisados = {false, false};
        Toque t = new Toque();
        boolean[] mostrado = {false};
        Runnable mostrarResultado = () -> {
            if (mostrado[0]) return;
            mostrado[0] = true;
            if (!avisados[0]) passo.accept(ida);
            if (!avisados[1]) passo.accept(batida);
            avisados[0] = avisados[1] = true;
            passo.accept(resultado);
            alvo.setText(resultado);
            alvo.getStyleClass().removeAll("suspense-passo", "suspense-gol", "suspense-erro");
            alvo.getStyleClass().add(gol ? "suspense-gol" : "suspense-erro");
            if (aoMostrarResultado != null) aoMostrarResultado.run();
        };
        t.fim = () -> {
            mostrarResultado.run();
            if (fim != null) fim.run();
        };
        long total = ms[0] + ms[1] + ms[2];
        if (total <= 0) {
            t.terminar();
            return t;
        }
        alvo.getStyleClass().removeAll("suspense-gol", "suspense-erro");
        if (!alvo.getStyleClass().contains("suspense-passo")) alvo.getStyleClass().add("suspense-passo");
        alvo.setText(ida);
        passo.accept(ida);
        avisados[0] = true;
        PauseTransition p1 = new PauseTransition(Duration.millis(Math.max(1, ms[0])));
        PauseTransition p2 = new PauseTransition(Duration.millis(Math.max(1, ms[1])));
        PauseTransition p3 = new PauseTransition(Duration.millis(Math.max(1, ms[2])));
        p1.setOnFinished(e -> {
            if (t.acabou) return;
            alvo.setText(batida);
            passo.accept(batida);
            avisados[1] = true;
            p2.play();
        });
        p2.setOnFinished(e -> {
            if (t.acabou) return;
            mostrarResultado.run();
            p3.play();
        });
        p3.setOnFinished(e -> t.terminar());
        t.pausas.addAll(List.of(p1, p2, p3));
        p1.play();
        return t;
    }

    // ------------------------------------------------------------------ na narração (pênalti no jogo)
    /**
     * Ritmo fixo do suspense na narração (requisito: devagar e igual em qualquer velocidade):
     * uma linha a cada ~1,2 s. Instantânea (testes/capturas) e "SIMULAR ATÉ O FIM": sem espera.
     */
    public static final long RITMO_MS = 1200;

    /**
     * Mostra {@code n} linhas na narração, uma a cada {@code ritmoMs} ({@code linha.accept(i)}); depois
     * da última chama {@code fim}. {@link Toque#pular()} mostra o resto na hora. Para o pênalti no jogo
     * (clube e Kings): "Pênalti marcado…", "X vai para a bola…", "Bateu…" e o resultado.
     */
    public static Toque emLinhas(int n, long ritmoMs, java.util.function.IntConsumer linha, Runnable fim) {
        Toque t = new Toque();
        int[] proxima = {0};
        Runnable resto = () -> {
            while (proxima[0] < n) linha.accept(proxima[0]++);
        };
        t.fim = () -> {
            resto.run();
            if (fim != null) fim.run();
        };
        if (ritmoMs <= 0 || n <= 1) {
            t.terminar();
            return t;
        }
        linha.accept(proxima[0]++);
        for (int i = 1; i < n; i++) {
            PauseTransition p = new PauseTransition(Duration.millis(ritmoMs * i));
            p.setOnFinished(e -> {
                if (t.acabou) return;
                if (proxima[0] < n) linha.accept(proxima[0]++);
                if (proxima[0] >= n) t.terminar();
            });
            t.pausas.add(p);
            p.play();
        }
        return t;
    }

    // ------------------------------------------------------------------ disputa de pênaltis na narração
    /** Passos de uma cobrança da disputa na narração. */
    public static final int ALTERNADAS = -1, IDA = 0, BATIDA = 1, RESULTADO = 2;

    /** Quem escreve cada linha da disputa ({@code etapa}: {@link #IDA}, {@link #BATIDA}, {@link #RESULTADO} ou {@link #ALTERNADAS}). */
    public interface NarradorDisputa {
        void linha(int i, Cobranca c, int etapa, String texto);
    }

    /** Linha da narração quando a disputa passa das 5 cobranças de cada lado. */
    public static final String TEXTO_ALTERNADAS = "Empate nas 5 cobranças! Agora são as alternadas: quem errar primeiro pode perder.";

    /**
     * A disputa de pênaltis na narração (requisito: "igual no jogo"): cobrança a cobrança, alternando
     * os times, "X vai para a bola…", "Bateu…" e o resultado, uma linha a cada {@code ritmoMs} (o ritmo fixo
     * do suspense); antes da 11ª cobrança, o aviso das alternadas. Ritmo zero ou {@link Toque#pular()}: o
     * resto na hora. Depois da última linha, {@code fim}.
     */
    public static Toque disputaEmLinhas(List<Cobranca> cs, long ritmoMs, NarradorDisputa n, Runnable fim) {
        List<Runnable> passos = new ArrayList<>();
        for (int i = 0; i < cs.size(); i++) {
            Cobranca c = cs.get(i);
            int k = i;
            if (i == 10) passos.add(() -> n.linha(k, c, ALTERNADAS, TEXTO_ALTERNADAS));
            String ida = fraseIda(c.batedor()), batida = fraseBatida(), res = fraseResultado(c.desfecho(), c.goleiro());
            passos.add(() -> n.linha(k, c, IDA, ida));
            passos.add(() -> n.linha(k, c, BATIDA, batida));
            passos.add(() -> n.linha(k, c, RESULTADO, res));
        }
        return emLinhas(passos.size(), ritmoMs, k -> passos.get(k).run(), fim);
    }

    /**
     * As bolinhas da disputa de pênaltis de um time, para o placar: uma por cobrança (verde gol,
     * vermelha erro, cinza ainda não bateu; a da vez com contorno). Começa com 5 e cresce nas alternadas.
     */
    public static final class Bolinhas extends HBox {
        private final double raio;
        private int batidas;

        public Bolinhas(double raio) {
            super(Math.max(3, raio * 0.6));
            this.raio = raio;
            setAlignment(Pos.CENTER);
            getStyleClass().add("pen-bolinhas");
            reiniciar();
        }

        /** Volta às 5 cinzas. */
        public void reiniciar() {
            batidas = 0;
            getChildren().clear();
            for (int i = 0; i < 5; i++) getChildren().add(nova());
        }

        private Circle nova() {
            Circle c = new Circle(raio);
            c.getStyleClass().addAll("pen-bola", "pen-pendente");
            return c;
        }

        private Circle bola(int k) {
            while (getChildren().size() <= k) getChildren().add(nova());
            return (Circle) getChildren().get(k);
        }

        /** A próxima cobrança é deste time (contorno na bolinha). */
        public void vez() {
            bola(batidas).getStyleClass().add("pen-vez");
        }

        /** A cobrança da vez terminou: verde (gol) ou vermelha. */
        public void marcar(boolean gol) {
            Circle c = bola(batidas++);
            c.getStyleClass().removeAll("pen-pendente", "pen-vez");
            c.getStyleClass().add(gol ? "pen-gol" : "pen-erro");
        }

        public int batidas() { return batidas; }

        /** Testes e capturas: "GGE--" (G gol, E erro, - a bater). */
        public String resumo() {
            StringBuilder b = new StringBuilder();
            for (javafx.scene.Node n : getChildren()) {
                var cl = n.getStyleClass();
                b.append(cl.contains("pen-gol") ? 'G' : cl.contains("pen-erro") ? 'E' : '-');
            }
            return b.toString();
        }
    }

    // ------------------------------------------------------------------ letreiro (pênalti no jogo)
    /** Letreiro no meio da tela para o pênalti durante o jogo: time, e os três passos grandes. */
    public static final class Faixa extends StackPane {
        private final Label titulo = Ui.rotulo("", "suspense-titulo");
        private final Label passo = Ui.rotulo("", "suspense-passo");
        private Toque atual;

        public Faixa() {
            VBox caixa = new VBox(8, titulo, passo);
            caixa.setAlignment(Pos.CENTER);
            caixa.getStyleClass().add("suspense-faixa");
            caixa.setMaxSize(560, Region.USE_PREF_SIZE);
            getChildren().add(caixa);
            setPickOnBounds(false);
            caixa.setMouseTransparent(true);
            setVisible(false);
        }

        /** Mostra a cobrança; ao fim esconde e chama {@code fim}. */
        public void mostrar(String time, String batedor, Desfecho d, String goleiro, long[] ms, Runnable fim) {
            if (atual != null && !atual.acabou()) atual.pular();
            titulo.setText("PÊNALTI" + (time == null || time.isBlank() ? "" : " PARA O " + time.toUpperCase(java.util.Locale.ROOT)));
            boolean espera = ms[0] + ms[1] + ms[2] > 0;
            setVisible(espera);
            atual = tocar(passo, fraseIda(batedor), fraseBatida(), fraseResultado(d, goleiro), d.gol(), ms, null, () -> {
                setVisible(false);
                if (fim != null) fim.run();
            });
        }

        /** "SIMULAR ATÉ O FIM": termina na hora. */
        public void pular() {
            if (atual != null && !atual.acabou()) atual.pular();
            setVisible(false);
        }
    }

    // ------------------------------------------------------------------ disputa de pênaltis
    /** Uma cobrança da disputa para o painel. */
    public record Cobranca(int lado, String batedor, Desfecho desfecho, String goleiro) {}

    /**
     * Painel da disputa de pênaltis: os dois times com uma bolinha por cobrança (verde gol, vermelha
     * erro, cinza a bater; 5 de cada e mais na morte súbita), o placar e os três passos de cada
     * cobrança. {@link #revelar} toca as cobranças em ordem.
     */
    public static final class Disputa extends StackPane {
        private final Label placar = Ui.rotulo("0 x 0", "suspense-placar");
        private final Label passo = Ui.rotulo("", "suspense-passo");
        private final Label morteSubita = Ui.rotulo("MORTE SÚBITA", "suspense-morte");
        private final HBox[] bolas = {new HBox(6), new HBox(6)};
        private final int[] gols = new int[2], batidas = new int[2];
        private Toque atual;
        private boolean pulando;
        /** Cada passo de cada cobrança (lado, texto), para a narração também mostrar. */
        private java.util.function.BiConsumer<Integer, String> aoPasso;

        public void aoPasso(java.util.function.BiConsumer<Integer, String> p) { this.aoPasso = p; }

        public Disputa(String mandante, String visitante) {
            Label titulo = Ui.rotulo("DISPUTA DE PÊNALTIS", "suspense-titulo");
            morteSubita.setVisible(false);
            morteSubita.setManaged(false);
            VBox linhas = new VBox(10, linha(mandante, bolas[0]), linha(visitante, bolas[1]));
            for (HBox b : bolas) for (int i = 0; i < 5; i++) b.getChildren().add(bola("pen-pendente"));
            VBox caixa = new VBox(12, titulo, placar, linhas, morteSubita, passo);
            caixa.setAlignment(Pos.CENTER);
            caixa.getStyleClass().add("suspense-faixa");
            caixa.setMaxSize(620, Region.USE_PREF_SIZE);
            getChildren().add(caixa);
            setPickOnBounds(false);
            caixa.setMouseTransparent(true);
        }

        private static HBox linha(String time, HBox bolas) {
            Label n = Ui.rotulo(time, "suspense-time");
            n.setMinWidth(200);
            n.setPrefWidth(200);
            HBox h = new HBox(14, n, bolas);
            h.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(bolas, Priority.ALWAYS);
            return h;
        }

        private static Circle bola(String classe) {
            Circle c = new Circle(9);
            c.getStyleClass().addAll("pen-bola", classe);
            return c;
        }

        /**
         * Toca as cobranças uma a uma (cada uma nos três passos, com as esperas {@code ms}); ao fim
         * chama {@code fim}. Esperas zeradas ou {@link #pular()}: tudo na hora.
         */
        public void revelar(List<Cobranca> cobrancas, long[] ms, Runnable fim) {
            proxima(cobrancas, 0, ms, fim);
        }

        private void proxima(List<Cobranca> cs, int i, long[] ms, Runnable fim) {
            if (i >= cs.size()) {
                passo.setText("Fim da disputa: " + gols[0] + " x " + gols[1]);
                passo.getStyleClass().removeAll("suspense-gol", "suspense-erro");
                if (fim != null) fim.run();
                return;
            }
            Cobranca c = cs.get(i);
            boolean subita = i >= 10;
            morteSubita.setVisible(subita);
            morteSubita.setManaged(subita);
            int lado = c.lado() == 1 ? 1 : 0;
            int k = batidas[lado]++;
            HBox linha = bolas[lado];
            while (linha.getChildren().size() <= k) linha.getChildren().add(bola("pen-pendente"));
            Circle alvo = (Circle) linha.getChildren().get(k);
            alvo.getStyleClass().add("pen-vez");
            long[] espera = pulando ? new long[] {0, 0, 0} : ms;
            atual = tocar(passo, fraseIda(c.batedor()), fraseBatida(), fraseResultado(c.desfecho(), c.goleiro()), c.desfecho().gol(), espera,
                    () -> {
                        alvo.getStyleClass().removeAll("pen-pendente", "pen-vez");
                        alvo.getStyleClass().add(c.desfecho().gol() ? "pen-gol" : "pen-erro");
                        if (c.desfecho().gol()) gols[lado]++;
                        placar.setText(gols[0] + " x " + gols[1]);
                    },
                    x -> {
                        if (aoPasso != null) aoPasso.accept(lado, x);
                    },
                    () -> proxima(cs, i + 1, ms, fim));
        }

        /** "SIMULAR ATÉ O FIM" / Esc: o resto na hora. */
        public void pular() {
            pulando = true;
            if (atual != null && !atual.acabou()) atual.pular();
        }

        int[] placar() { return gols.clone(); }
    }
}
