package br.futebas.ui.comp;

import br.futebas.kings.MotorKings;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.Locale;

/**
 * Carta secreta da Kings no estilo das cartas oficiais (requisito, 12/11/2026, com a foto das
 * cartas reais), desenho ORIGINAL com formas do JavaFX (nada copiado da arte oficial):
 * <ul>
 *   <li>um escudo verde com a borda de cima recortada/ondulada, contorno escuro fino e uma borda interna;</li>
 *   <li>o título em cima, em maiúsculas pretas condensadas ("GOL 2X", "SHOOT OUT", "JOGADOR ESTRELA",
 *   "PÊNALTI", "CORINGA", "SUSPENSÃO", "PÊNALTI INVERTIDO");</li>
 *   <li>embaixo do título, um ícone preto de traço sobre o verde (bola; jogador correndo com a bola;
 *   estrela de 4 pontas num círculo; gol com rede e bola; chapéu de bobo da corte; círculo cortado
 *   sobre um jogador; gol com a seta de volta);</li>
 *   <li>os ~25% de baixo, uma faixa preta sólida (o efeito, na carta grande).</li>
 * </ul>
 * Estados: virada para baixo (escudo escuro com "K" ou "?"), virar (escala X 1→0→1), brilho quando dá
 * para usar e cinza com o selo "USADA · resultado". Tamanhos: grande (ao vivo), {@link #mini} (listas)
 * e {@link #pequena} (botões, ~30 px).
 */
public final class CartaKings extends StackPane {
    private final double w, h;
    private final boolean mini;
    private MotorKings.Carta carta;
    private boolean aberta;
    private Timeline brilho;
    private String monograma = "K";

    static final Color VERDE_CLARO = Color.web("#46d873"), VERDE = Color.web("#22b257"), VERDE_ESCURO = Color.web("#13843c"),
            CONTORNO = Color.web("#0b1f12"), PRETO = Color.web("#0d0f0d");

    /** Carta grande (ao vivo). {@code escudo}: ignorado no desenho novo (mantido pela assinatura). */
    public CartaKings(Node escudo) {
        this(escudo, false, 120);
    }

    /** Carta grande com largura própria (altura ≈ 1,4 × largura). */
    public CartaKings(Node escudo, double largura) {
        this(escudo, false, largura);
    }

    private CartaKings(Node escudo, boolean mini, double largura) {
        this.mini = mini || largura < 100;
        this.w = largura;
        this.h = Math.round(largura * 1.4);
        setMinSize(w, h);
        setPrefSize(w, h);
        setMaxSize(w, h);
        getStyleClass().add("kings-carta");
        if (this.mini) getStyleClass().add("kings-carta-mini");
        verso();
    }

    /** Carta pequena já aberta (listas: baralho da liga, cartas usadas). */
    public static CartaKings mini(MotorKings.Carta c) {
        CartaKings k = new CartaKings(null, true, 76);
        k.mostrar(c);
        return k;
    }

    /** Carta de qualquer largura já aberta (botões: ~30 px, só o escudo, o ícone e a faixa). */
    public static CartaKings pequena(MotorKings.Carta c, double largura) {
        CartaKings k = new CartaKings(null, true, largura);
        if (c != null) k.mostrar(c);
        return k;
    }

    /** Carta pequena virada com "?" (a carta do adversário antes de ele usar). */
    public static CartaKings miniVirada(Node escudo) {
        CartaKings k = new CartaKings(escudo, true, 76);
        k.monograma = "?";
        k.verso();
        return k;
    }

    /** Carta pequena virada de qualquer largura ("?"). */
    public static CartaKings pequenaVirada(double largura) {
        CartaKings k = new CartaKings(null, true, largura);
        k.monograma = "?";
        k.verso();
        return k;
    }

    public MotorKings.Carta carta() { return carta; }

    public boolean aberta() { return aberta; }

    /** Largura e altura da carta. */
    public double largura() { return w; }

    public double altura() { return h; }

    // ------------------------------------------------------------------ forma
    /**
     * O escudo da carta no retângulo (x, y, cw, ch): borda de cima com as abas dos cantos e duas
     * ondas, laterais retas até 78% e a ponta embaixo, no meio.
     */
    static String forma(double x, double y, double cw, double ch) {
        double a = ch * 0.028;
        StringBuilder sb = new StringBuilder();
        sb.append("M ").append(f(x)).append(' ').append(f(y + 2 * a));
        sb.append(" L ").append(f(x + cw * 0.12)).append(' ').append(f(y + 2 * a));
        sb.append(" L ").append(f(x + cw * 0.12)).append(' ').append(f(y));
        sb.append(" Q ").append(f(x + cw * 0.31)).append(' ').append(f(y + 2.2 * a)).append(' ').append(f(x + cw * 0.5)).append(' ').append(f(y));
        sb.append(" Q ").append(f(x + cw * 0.69)).append(' ').append(f(y + 2.2 * a)).append(' ').append(f(x + cw * 0.88)).append(' ').append(f(y));
        sb.append(" L ").append(f(x + cw * 0.88)).append(' ').append(f(y + 2 * a));
        sb.append(" L ").append(f(x + cw)).append(' ').append(f(y + 2 * a));
        sb.append(" L ").append(f(x + cw)).append(' ').append(f(y + ch * 0.78));
        sb.append(" Q ").append(f(x + cw)).append(' ').append(f(y + ch * 0.92)).append(' ').append(f(x + cw * 0.5)).append(' ').append(f(y + ch));
        sb.append(" Q ").append(f(x)).append(' ').append(f(y + ch * 0.92)).append(' ').append(f(x)).append(' ').append(f(y + ch * 0.78));
        sb.append(" Z");
        return sb.toString();
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private SVGPath escudo(Color topo, Color base) {
        SVGPath p = new SVGPath();
        p.setContent(forma(1, 1, w - 2, h - 2));
        p.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, new Stop(0, topo), new Stop(1, base)));
        p.setStroke(CONTORNO);
        p.setStrokeWidth(w < 50 ? 1.2 : 2);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return p;
    }

    /** Faixa preta dos ~25% de baixo, recortada pelo escudo. */
    private Node faixaPreta() {
        Rectangle r = new Rectangle(0, h * 0.75, w, h * 0.25 + 1);
        r.setFill(PRETO);
        SVGPath clip = new SVGPath();
        clip.setContent(forma(1, 1, w - 2, h - 2));
        r.setClip(clip);
        return r;
    }

    /** Borda interna (o mesmo escudo, menor). */
    private Node bordaInterna(Color cor) {
        double i = Math.max(2.5, w * 0.055);
        SVGPath p = new SVGPath();
        p.setContent(forma(i, i, w - 2 * i, h - 2 * i));
        p.setFill(null);
        p.setStroke(cor);
        p.setStrokeWidth(w < 50 ? 0.7 : 1.1);
        p.setMouseTransparent(true);
        return p;
    }

    // ------------------------------------------------------------------ estados
    /** Virada para baixo: escudo escuro, borda verde, "K" (ou "?") num disco e a faixa preta. */
    public void verso() {
        aberta = false;
        carta = null;
        pararBrilho();
        setOpacity(1);
        getStyleClass().removeAll("kings-carta-usada", "kings-carta-frente");
        if (!getStyleClass().contains("kings-carta-verso")) getStyleClass().add("kings-carta-verso");
        Pane p = new Pane();
        p.setMinSize(w, h);
        p.setPrefSize(w, h);
        p.setMaxSize(w, h);
        SVGPath fundo = escudo(Color.web("#173826"), Color.web("#0a1a10"));
        // losangos discretos (o "verso" de baralho)
        Group padrao = new Group();
        double passo = Math.max(7, w / 8);
        for (double y = passo; y < h * 0.74; y += passo)
            for (double x = ((int) (y / passo) % 2) * passo / 2; x < w; x += passo) {
                double s = passo * 0.18;
                Polygon d = new Polygon(x, y - s, x + s, y, x, y + s, x - s, y);
                d.setFill(Color.web("#46d873", 0.16));
                padrao.getChildren().add(d);
            }
        SVGPath clip = new SVGPath();
        clip.setContent(forma(1, 1, w - 2, h - 2));
        padrao.setClip(clip);
        double r = Math.max(6, w * 0.22);
        Circle disco = new Circle(w / 2, h * 0.42, r, Color.web("#0d1f14"));
        disco.setStroke(VERDE_CLARO);
        disco.setStrokeWidth(Math.max(1, w * 0.02));
        Label k = new Label(monograma);
        k.getStyleClass().add("kings-carta-monograma");
        k.setStyle("-fx-font-size: " + f(Math.max(8, r * 1.15)) + "px;");
        k.setAlignment(Pos.CENTER);
        k.setMinSize(2 * r, 2 * r);
        k.setPrefSize(2 * r, 2 * r);
        k.setLayoutX(w / 2 - r);
        k.setLayoutY(h * 0.42 - r);
        p.getChildren().addAll(fundo, padrao, faixaPreta(), bordaInterna(Color.web("#46d873", 0.55)), disco, k);
        if (w >= 60) p.getChildren().add(textoFaixa(w >= 100 ? "CARTA SECRETA" : "KINGS", VERDE_CLARO));
        getChildren().setAll(p);
    }

    /** Mostra a frente (sem animação). */
    public void mostrar(MotorKings.Carta c) {
        if (c == null) {
            verso();
            return;
        }
        carta = c;
        aberta = true;
        getStyleClass().removeAll("kings-carta-verso", "kings-carta-usada");
        if (!getStyleClass().contains("kings-carta-frente")) getStyleClass().add("kings-carta-frente");
        Pane p = new Pane();
        p.setMinSize(w, h);
        p.setPrefSize(w, h);
        p.setMaxSize(w, h);
        p.getChildren().addAll(escudo(VERDE_CLARO, VERDE_ESCURO), faixaPreta(), bordaInterna(CONTORNO));
        boolean comTitulo = w >= 48;
        double topoIcone = h * 0.08;
        if (comTitulo) {
            Label t = new Label(titulo(c));
            t.getStyleClass().add("kings-carta-titulo");
            double fs = Math.max(7, Math.min(16, w * 0.115));
            t.setStyle("-fx-font-size: " + f(fs) + "px;");
            t.setWrapText(true);
            t.setTextAlignment(TextAlignment.CENTER);
            t.setAlignment(Pos.CENTER);
            double pad = w * 0.1;
            t.setLayoutX(pad);
            t.setLayoutY(h * 0.075);
            t.setPrefWidth(w - 2 * pad);
            t.setMaxWidth(w - 2 * pad);
            p.getChildren().add(t);
            topoIcone = h * 0.075 + fs * (titulo(c).length() > 11 ? 2.3 : 1.25);
        }
        // ícone: centrado entre o título e a faixa preta
        double area = h * 0.73 - topoIcone;
        double lado = Math.min(w * (comTitulo ? 0.62 : 0.78), area * 0.92);
        Node ic = icone(c, lado / 80.0);
        Group g = new Group(ic);
        g.setLayoutX(w / 2);
        g.setLayoutY(topoIcone + area / 2);
        p.getChildren().add(g);
        if (w >= 100) p.getChildren().add(textoFaixa(efeito(c), Color.WHITE));
        else if (w >= 60) p.getChildren().add(textoFaixa("KINGS", VERDE_CLARO));
        getChildren().setAll(p);
    }

    /** Texto dentro da faixa preta (efeito na carta grande; "KINGS" na mini). */
    private Label textoFaixa(String s, Color cor) {
        Label l = new Label(s);
        l.getStyleClass().add("kings-carta-faixa-texto");
        double fs = w >= 100 ? Math.max(8.5, w * 0.068) : Math.max(6, w * 0.085);
        l.setStyle("-fx-font-size: " + f(fs) + "px; -fx-text-fill: " + Tema.hex(cor) + ";");
        l.setWrapText(true);
        l.setTextAlignment(TextAlignment.CENTER);
        l.setAlignment(Pos.CENTER);
        double pad = w * 0.13;
        l.setLayoutX(pad);
        l.setLayoutY(h * 0.765);
        l.setPrefSize(w - 2 * pad, h * 0.15);
        l.setMaxSize(w - 2 * pad, h * 0.15);
        l.setMouseTransparent(true);
        return l;
    }

    /** Vira para mostrar a frente: escala X 1 → 0, troca, 0 → 1. */
    public void virar(MotorKings.Carta c) {
        if (aberta && c == carta) return;
        ScaleTransition fecha = new ScaleTransition(Duration.millis(170), this);
        fecha.setFromX(1);
        fecha.setToX(0);
        fecha.setOnFinished(e -> mostrar(c));
        ScaleTransition abre = new ScaleTransition(Duration.millis(170), this);
        abre.setFromX(0);
        abre.setToX(1);
        new SequentialTransition(fecha, abre).play();
    }

    /** Brilho verde pulsando (a carta pode ser usada agora / está sendo usada). */
    public void brilhar(boolean sim) {
        if (!sim) {
            pararBrilho();
            return;
        }
        if (brilho != null) return;
        DropShadow ds = new DropShadow(mini ? 8 : 14, Color.web("#6dff9c"));
        ds.setSpread(0.3);
        setEffect(ds);
        brilho = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(ds.radiusProperty(), mini ? 4 : 8)),
                new KeyFrame(Duration.millis(700), new KeyValue(ds.radiusProperty(), mini ? 12 : 22)));
        brilho.setAutoReverse(true);
        brilho.setCycleCount(Animation.INDEFINITE);
        brilho.play();
    }

    /** O brilho está ligado? */
    public boolean brilhando() { return brilho != null; }

    private void pararBrilho() {
        if (brilho != null) brilho.stop();
        brilho = null;
        setEffect(sombra());
    }

    /** Sombra de sempre (sem brilho). */
    private DropShadow sombra() {
        DropShadow s = new DropShadow(w < 50 ? 3 : mini ? 5 : 10, 0, w < 50 ? 1 : 3, Color.web("#000000", 0.45));
        return s;
    }

    /** Cinza com o selo "USADA" (e o resultado: "USADA · GOL", "USADA · ERROU"...). */
    public void usada(String resultado) {
        pararBrilho();
        if (!getStyleClass().contains("kings-carta-usada")) getStyleClass().add("kings-carta-usada");
        getChildren().removeIf(n -> "selo".equals(n.getId()));
        if (!getChildren().isEmpty()) {
            ColorAdjust cinza = new ColorAdjust();
            cinza.setSaturation(-1);
            cinza.setBrightness(-0.25);
            getChildren().get(0).setEffect(cinza);
            getChildren().get(0).setOpacity(0.75);
        }
        if (w < 48) return; // botão pequeno: só cinza (o resultado vai na dica)
        Label selo = new Label(resultado == null || resultado.isBlank() ? "USADA" : "USADA · " + resultado);
        selo.setId("selo");
        selo.getStyleClass().add("kings-carta-selo");
        if (mini) selo.setStyle("-fx-font-size: 8.5px; -fx-padding: 2 4 2 4;");
        selo.setRotate(-14);
        selo.setWrapText(true);
        selo.setMaxWidth(w - 6);
        selo.setTextAlignment(TextAlignment.CENTER);
        getChildren().add(selo);
    }

    /** Está cinza ("USADA")? */
    public boolean foiUsada() { return getStyleClass().contains("kings-carta-usada"); }

    // ------------------------------------------------------------------ tipos
    /** Título impresso na carta (maiúsculas): "GOL 2X", "SHOOT OUT", "SUSPENSÃO"... */
    public static String titulo(MotorKings.Carta c) {
        return switch (c) {
            case GOL_DUPLO -> "GOL 2X";
            case SANCAO -> "SUSPENSÃO";
            case PENALTI_SHOOTOUT -> "SHOOT OUT";
            case JOGADOR_ESTRELA -> "JOGADOR ESTRELA";
            case PENALTI -> "PÊNALTI";
            case PENALTI_INVERTIDO -> "PÊNALTI INVERTIDO";
            case CORINGA -> "CORINGA";
        };
    }

    /** Cor de destaque do tipo (dicas e listas; a carta em si é sempre verde). */
    public static Color cor(MotorKings.Carta c) {
        return switch (c) {
            case GOL_DUPLO -> Color.web("#f5a524");
            case SANCAO -> Color.web("#e5484d");
            case PENALTI_SHOOTOUT -> Color.web("#3fb6ff");
            case JOGADOR_ESTRELA -> Color.web("#ffd84d");
            case PENALTI -> Color.web("#46c97a");
            case PENALTI_INVERTIDO -> Color.web("#b07cff");
            case CORINGA -> Color.web("#ff6fb5");
        };
    }

    /** Uma linha do efeito (a carta grande mostra na faixa preta). */
    public static String efeito(MotorKings.Carta c) {
        return switch (c) {
            case GOL_DUPLO -> "Seus gols valem 2 por 4 minutos.";
            case SANCAO -> "Um rival de linha fica fora 4 minutos.";
            case PENALTI_SHOOTOUT -> "Um shoot out a seu favor.";
            case JOGADOR_ESTRELA -> "Os gols do seu escolhido valem 2.";
            case PENALTI -> "Um pênalti a seu favor.";
            case PENALTI_INVERTIDO -> "Um rival bate: se errar, gol seu.";
            case CORINGA -> "Vira qualquer carta ou rouba a do rival.";
        };
    }

    /** O ícone de traço preto de cada tipo, centrado em (0, 0) numa caixa de ~80 px na escala 1. */
    public static Node icone(MotorKings.Carta c, double esc) {
        Group g = switch (c) {
            case GOL_DUPLO -> bola(0, 0, 27);
            case PENALTI_SHOOTOUT -> correndo();
            case JOGADOR_ESTRELA -> estrela();
            case PENALTI -> gol(false);
            case PENALTI_INVERTIDO -> gol(true);
            case CORINGA -> chapeu();
            case SANCAO -> suspensao();
        };
        g.setScaleX(esc);
        g.setScaleY(esc);
        return new Group(g);
    }

    private static <T extends Shape> T traco(T s, double largura) {
        s.setStroke(Color.BLACK);
        s.setStrokeWidth(largura);
        s.setStrokeLineCap(StrokeLineCap.ROUND);
        s.setStrokeLineJoin(StrokeLineJoin.ROUND);
        if (!(s instanceof Line)) s.setFill(null);
        return s;
    }

    private static SVGPath caminho(String d, double largura) {
        SVGPath p = new SVGPath();
        p.setContent(d);
        return traco(p, largura);
    }

    /** Bola de futebol de traço: círculo, pentágono preto no meio e as costuras. */
    static Group bola(double cx, double cy, double r) {
        Group g = new Group();
        g.getChildren().add(traco(new Circle(cx, cy, r), Math.max(1.6, r * 0.12)));
        double[] p = new double[10];
        for (int i = 0; i < 5; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
            p[2 * i] = cx + r * 0.36 * Math.cos(a);
            p[2 * i + 1] = cy + r * 0.36 * Math.sin(a);
            Line l = traco(new Line(cx + r * 0.36 * Math.cos(a), cy + r * 0.36 * Math.sin(a), cx + r * 0.92 * Math.cos(a), cy + r * 0.92 * Math.sin(a)),
                    Math.max(1.2, r * 0.09));
            g.getChildren().add(l);
        }
        Polygon pent = new Polygon(p);
        pent.setFill(Color.BLACK);
        g.getChildren().add(pent);
        return g;
    }

    /** Jogador correndo com a bola (SHOOT OUT). */
    private static Group correndo() {
        Group g = new Group();
        g.getChildren().add(traco(new Circle(2, -27, 6.5), 3));
        g.getChildren().add(caminho("M 0 -19 L -7 4", 3.6)); // tronco
        g.getChildren().add(caminho("M -2 -14 L 9 -9 L 14 -17", 3)); // braço da frente
        g.getChildren().add(caminho("M -2 -14 L -14 -9 L -21 -15", 3)); // braço de trás
        g.getChildren().add(caminho("M -7 4 L 6 11 L 4 25", 3.4)); // perna da frente
        g.getChildren().add(caminho("M -7 4 L -16 15 L -29 13", 3.4)); // perna de trás
        g.getChildren().add(bola(17, 25, 8));
        for (int i = 0; i < 3; i++) g.getChildren().add(caminho("M " + (-36 + i * 3) + " " + (-10 + i * 8) + " L " + (-26 + i * 3) + " " + (-10 + i * 8), 2));
        return g;
    }

    /** Estrela de 4 pontas dentro de um círculo (JOGADOR ESTRELA). */
    private static Group estrela() {
        Group g = new Group();
        g.getChildren().add(traco(new Circle(0, 0, 31), 3.2));
        double[] p = new double[16];
        for (int i = 0; i < 8; i++) {
            double r = i % 2 == 0 ? 23 : 6.5, a = -Math.PI / 2 + i * Math.PI / 4;
            p[2 * i] = r * Math.cos(a);
            p[2 * i + 1] = r * Math.sin(a);
        }
        Polygon s = new Polygon(p);
        s.setFill(Color.BLACK);
        s.setStroke(Color.BLACK);
        s.setStrokeWidth(1.5);
        s.setStrokeLineJoin(StrokeLineJoin.ROUND);
        g.getChildren().add(s);
        return g;
    }

    /** Gol com rede e bola (PÊNALTI); {@code invertido}: com a seta de volta (PÊNALTI INVERTIDO). */
    private static Group gol(boolean invertido) {
        Group g = new Group();
        double esq = invertido ? -34 : -34, dir = invertido ? 22 : 34;
        for (double x = esq + 7; x < dir; x += 7) g.getChildren().add(traco(new Line(x, -20, x, 10), 1));
        for (double y = -13; y < 10; y += 6.5) g.getChildren().add(traco(new Line(esq, y, dir, y), 1));
        g.getChildren().add(caminho("M " + esq + " 12 L " + esq + " -21 L " + dir + " -21 L " + dir + " 12", 3.6));
        g.getChildren().add(traco(new Line(esq - 6, 12, dir + 6, 12), 2.2));
        g.getChildren().add(bola(invertido ? -6 : 0, 25, 7.5));
        if (invertido) {
            // seta curva saindo da bola e voltando para o lado (o rival bate contra o próprio gol de quem usou)
            g.getChildren().add(caminho("M 4 26 Q 34 26 34 -2", 3));
            g.getChildren().add(caminho("M 27 4 L 34 -4 L 40 5", 3));
        }
        return g;
    }

    /** Chapéu de bobo da corte com três pontas e guizos (CORINGA). */
    private static Group chapeu() {
        Group g = new Group();
        g.getChildren().add(caminho("M -26 12 Q -28 -6 -36 -17 Q -19 -12 -10 2 Q -8 -18 0 -30 Q 8 -18 10 2 Q 19 -12 36 -17 Q 28 -6 26 12 Z", 3));
        Rectangle faixa = traco(new Rectangle(-28, 12, 56, 11), 3);
        faixa.setArcWidth(6);
        faixa.setArcHeight(6);
        g.getChildren().add(faixa);
        for (int x = -18; x <= 18; x += 12) {
            Polygon d = new Polygon(x, 14.5, x + 3, 17.5, x, 20.5, x - 3, 17.5);
            d.setFill(Color.BLACK);
            g.getChildren().add(d);
        }
        for (double[] b : new double[][]{{-36, -17}, {0, -30}, {36, -17}}) {
            Circle c = new Circle(b[0], b[1], 4.6);
            c.setFill(Color.BLACK);
            g.getChildren().add(c);
        }
        return g;
    }

    /** Círculo cortado sobre um jogador (SUSPENSÃO). */
    private static Group suspensao() {
        Group g = new Group();
        g.getChildren().add(traco(new Circle(0, -12, 8), 3));
        g.getChildren().add(caminho("M -17 22 Q -17 0 0 0 Q 17 0 17 22", 3));
        g.getChildren().add(traco(new Circle(0, 2, 31), 3.6));
        g.getChildren().add(traco(new Line(-22, -20, 22, 24), 3.6));
        return g;
    }
}
