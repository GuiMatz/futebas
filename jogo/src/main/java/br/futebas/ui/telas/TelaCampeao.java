package br.futebas.ui.telas;

import br.futebas.mundo.Clube;
import br.futebas.ui.Sessao;
import br.futebas.ui.Tela;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Tema;
import br.futebas.ui.modelo.Fluxo;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.Locale;
import java.util.Random;

/**
 * Tela de campeão (requisito, 13/11/2026): só o escudo do time com o troféu da competição à
 * frente, "CAMPEÃO <ano>" e o nome do campeonato embaixo. Fundo nas cores do clube, raios de luz atrás
 * do troféu e confete dourado caindo. A entrada dura ~1 s; depois só o confete se mexe. Enter, Esc,
 * clique ou "Continuar" seguem. Sem som. No multi-treinador mostra de qual técnico é o título.
 */
public final class TelaCampeao implements Tela {

    /** Tamanho de desenho: o miolo é montado em 1366×768 e escalado por igual para a janela. */
    private static final double W = 1366, H = 768;
    /** Centro do escudo no desenho. */
    private static final double CX = W / 2, CY = 268;
    private static final double ESCUDO = 300, TROFEU = 240;
    private static final int CONFETES = 150;

    private final Fluxo.Conquista c;
    private final Runnable continuar;
    private final StackPane raiz = new StackPane();
    private final Region fundo = new Region();
    private final Canvas confete = new Canvas();
    private final Pane palco = new Pane();
    private final Color corA, corB;
    private boolean saiu;
    private long abertaEm;
    private AnimationTimer timer;

    public TelaCampeao(Sessao s, Fluxo.Conquista c, boolean mostrarTecnico, Runnable continuar) {
        this.c = c;
        this.continuar = continuar;
        Color[] cores = cores(s, c.timeId());
        corA = cores[0];
        corB = cores[1];
        montar(mostrarTecnico ? c.tecnico() : null);
    }

    // ------------------------------------------------------------------ montagem
    private void montar(String tecnico) {
        raiz.getStyleClass().add("tela-campeao");
        raiz.setStyle("-fx-background-color: #06080b;");
        fundo.setBackground(fundo());
        fundo.setMouseTransparent(true);

        palco.setPrefSize(W, H);
        palco.setMinSize(W, H);
        palco.setMaxSize(W, H);

        // raios de luz e o brilho atrás do escudo (desenhados uma vez)
        Canvas raios = new Canvas(2600, 1500);
        desenharRaios(raios.getGraphicsContext2D(), 2600, 1500);
        raios.setLayoutX(CX - 1300);
        raios.setLayoutY(CY + 40 - 750);
        raios.setEffect(new GaussianBlur(22));
        raios.setBlendMode(BlendMode.ADD);
        raios.setMouseTransparent(true);

        Node escudo = escudo(c.timeId(), ESCUDO);
        StackPane caixaEscudo = new StackPane(escudo);
        caixaEscudo.setPrefSize(ESCUDO, ESCUDO);
        caixaEscudo.setLayoutX(CX - ESCUDO / 2);
        caixaEscudo.setLayoutY(CY - ESCUDO / 2 - 30);
        DropShadow sombraEscudo = new DropShadow(46, Color.rgb(0, 0, 0, 0.75));
        sombraEscudo.setOffsetY(10);
        DropShadow auraEscudo = new DropShadow(70, Color.rgb(255, 214, 110, 0.38));
        auraEscudo.setSpread(0.08);
        auraEscudo.setInput(sombraEscudo);
        caixaEscudo.setEffect(auraEscudo);

        Node trofeu = trofeu(TROFEU);
        StackPane caixaTrofeu = new StackPane(trofeu);
        caixaTrofeu.setAlignment(Pos.BOTTOM_CENTER);
        caixaTrofeu.setPrefSize(TROFEU * 1.3, TROFEU);
        caixaTrofeu.setLayoutX(CX - TROFEU * 0.65);
        caixaTrofeu.setLayoutY(CY + ESCUDO / 2 - TROFEU + 92);
        DropShadow sombraTrofeu = new DropShadow(28, Color.rgb(0, 0, 0, 0.85));
        sombraTrofeu.setOffsetY(8);
        DropShadow brilhoTrofeu = new DropShadow(36, Color.rgb(255, 222, 120, 0.55));
        brilhoTrofeu.setSpread(0.12);
        brilhoTrofeu.setInput(sombraTrofeu);
        caixaTrofeu.setEffect(brilhoTrofeu);

        // "CAMPEÃO 2026"
        Text campeao = new Text("CAMPEÃO " + c.ano());
        campeao.setFont(fonte("FUTEBAS Placar", 112));
        campeao.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fff6cf")), new Stop(0.45, Color.web("#f7cd4a")),
                new Stop(0.55, Color.web("#e8b02c")), new Stop(1, Color.web("#b9821c"))));
        DropShadow sombraTexto = new DropShadow(14, Color.rgb(0, 0, 0, 0.7));
        sombraTexto.setOffsetY(5);
        DropShadow brilhoTexto = new DropShadow(32, Color.rgb(255, 200, 70, 0.45));
        brilhoTexto.setInput(sombraTexto);
        campeao.setEffect(brilhoTexto);
        StackPane caixaTexto = centralizado(campeao, 586);

        // nome do campeonato, entre dois filetes dourados
        Label nome = new Label(nomeCompeticao().toUpperCase(new Locale("pt", "BR")));
        nome.setFont(fonte("FUTEBAS Titulo", 27));
        nome.setTextFill(Color.rgb(246, 240, 226, 0.92));
        nome.setStyle("-fx-letter-spacing: 2;");
        nome.setMaxWidth(980);
        nome.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.8)));
        HBox linhaNome = new HBox(22, filete(false), nome, filete(true));
        linhaNome.setAlignment(Pos.CENTER);
        StackPane caixaNome = centralizado(linhaNome, 662);

        palco.getChildren().addAll(raios, caixaEscudo, caixaTrofeu, caixaTexto, caixaNome);

        if (tecnico != null) {
            // multi-treinador: de quem é o título
            Label t = new Label("TÉCNICO  ·  " + tecnico.toUpperCase(new Locale("pt", "BR")));
            t.setFont(fonte("FUTEBAS Titulo", 17));
            t.setTextFill(Color.web("#fff3c4"));
            t.setPadding(new Insets(7, 18, 7, 18));
            t.setStyle("-fx-background-color: rgba(0,0,0,0.38); -fx-background-radius: 999; "
                    + "-fx-border-color: rgba(246,196,48,0.65); -fx-border-radius: 999; -fx-border-width: 1;");
            palco.getChildren().add(centralizado(t, 52));
        }

        Button b = new Button("Continuar");
        b.setFocusTraversable(false);
        b.setStyle("-fx-background-color: rgba(0,0,0,0.28); -fx-text-fill: rgba(255,255,255,0.78); -fx-font-size: 14px; "
                + "-fx-background-radius: 6; -fx-border-color: rgba(255,255,255,0.28); -fx-border-radius: 6; -fx-padding: 8 22 8 22; -fx-cursor: hand;");
        b.setOnMouseEntered(e -> b.setOpacity(1));
        b.setOnAction(e -> sair());
        StackPane.setAlignment(b, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(b, new Insets(0, 28, 24, 0));

        confete.setMouseTransparent(true);
        Pane camadaConfete = new Pane(confete);
        camadaConfete.setMouseTransparent(true);
        confete.widthProperty().bind(raiz.widthProperty());
        confete.heightProperty().bind(raiz.heightProperty());

        Pane centro = new Pane(palco);
        centro.setMouseTransparent(true);
        centro.setMinSize(0, 0);
        raiz.getChildren().addAll(fundo, centro, camadaConfete, b);
        raiz.setMinSize(0, 0);
        Rectangle corte = new Rectangle();
        corte.widthProperty().bind(raiz.widthProperty());
        corte.heightProperty().bind(raiz.heightProperty());
        raiz.setClip(corte);
        raiz.widthProperty().addListener((o, a, v) -> escalar());
        raiz.heightProperty().addListener((o, a, v) -> escalar());
        raiz.setOnMouseClicked(e -> sair());
        raiz.setFocusTraversable(true);

        // os efeitos (desfoque, brilhos, sombras) viram bitmap uma vez: o confete redesenha a tela
        // inteira a cada quadro e não deve refazer o desfoque dos raios
        for (Node n : new Node[]{raios, caixaEscudo, caixaTrofeu, caixaTexto, caixaNome}) {
            n.setCache(true);
            n.setCacheHint(javafx.scene.CacheHint.QUALITY);
        }
        entrada(raios, caixaEscudo, caixaTrofeu, caixaTexto, caixaNome, b);
    }

    private static StackPane centralizado(Node n, double centroY) {
        StackPane p = new StackPane(n);
        p.setPrefWidth(W);
        p.setLayoutX(0);
        p.layoutYProperty().bind(p.heightProperty().divide(-2).add(centroY));
        return p;
    }

    private static Region filete(boolean direita) {
        Region r = new Region();
        r.setPrefSize(120, 2);
        r.setMinSize(40, 2);
        r.setMaxHeight(2);
        r.setBackground(new Background(new BackgroundFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(246, 196, 48, direita ? 0.9 : 0)), new Stop(1, Color.rgb(246, 196, 48, direita ? 0 : 0.9))), null, null)));
        return r;
    }

    private void escalar() {
        double w = raiz.getWidth(), h = raiz.getHeight();
        if (w <= 0 || h <= 0) return;
        double s = Math.min(w / W, h / H);
        palco.setScaleX(s);
        palco.setScaleY(s);
        palco.setLayoutX((w - W) / 2);
        palco.setLayoutY((h - H) / 2);
    }

    private String nomeCompeticao() {
        String n = c.competicao() == null ? "" : c.competicao().trim();
        String ano = String.valueOf(c.ano());
        if (n.endsWith(" " + ano)) n = n.substring(0, n.length() - ano.length()).trim(); // o ano já está no CAMPEÃO
        return n;
    }

    // ------------------------------------------------------------------ cores e fundo
    /** Cor principal (a mais viva do clube) e a segunda; clube sem cor viva fica no grafite. */
    static Color[] cores(Sessao s, String id) {
        String c1 = null, c2 = null;
        Clube cl = s == null ? null : s.jogo.mundo().clube(id);
        if (cl != null) {
            c1 = cl.cor1;
            c2 = cl.cor2;
        } else if (s != null && id != null) {
            try {
                var tk = s.jogo.timeKings(id);
                if (tk != null) {
                    c1 = tk.cor1;
                    c2 = tk.cor2;
                }
            } catch (RuntimeException e) {
                // sem Kings neste save
            }
        }
        if (c1 == null && id != null && Sessao.bancoCarregado()) {
            // sem save (ferramenta de captura): as cores do banco
            var db = Sessao.banco();
            var dc = db.clubes.get(id);
            var dk = dc == null ? db.kingsTimes.get(id) : null;
            if (dc != null) {
                c1 = dc.cor1();
                c2 = dc.cor2();
            } else if (dk != null) {
                c1 = dk.cor1();
                c2 = dk.cor2();
            }
        }
        Color a = Tema.cor(c1, Color.web("#1d3a5f")), b = Tema.cor(c2, Color.web("#2a2f38"));
        if (vivacidade(b) > vivacidade(a)) {
            Color x = a;
            a = b;
            b = x;
        }
        if (vivacidade(a) < 0.12) a = Color.web("#3a3f4a"); // preto e branco: grafite (o dourado faz o resto)
        if (vivacidade(b) < 0.12) b = a.deriveColor(0, 0.8, 0.6, 1);
        return new Color[]{a, b};
    }

    private static double vivacidade(Color c) {
        return c.getSaturation() * Math.min(1, c.getBrightness() * 1.4);
    }

    private static Color escuro(Color c, double brilho) {
        return Color.hsb(c.getHue(), Math.min(1, c.getSaturation() * 0.95), Math.min(c.getBrightness(), brilho));
    }

    private Background fundo() {
        Color centro = escuro(corA, 0.62), meio = escuro(corA, 0.30), borda = Color.web("#050608");
        RadialGradient luz = new RadialGradient(0, 0, 0.5, 0.40, 0.75, true, CycleMethod.NO_CYCLE,
                new Stop(0, centro), new Stop(0.45, meio), new Stop(1, borda));
        LinearGradient diagonal = new LinearGradient(0, 1, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(corB.getRed(), corB.getGreen(), corB.getBlue(), 0.30)),
                new Stop(0.5, Color.TRANSPARENT),
                new Stop(1, Color.color(corA.getRed(), corA.getGreen(), corA.getBlue(), 0.18)));
        RadialGradient vinheta = new RadialGradient(0, 0, 0.5, 0.45, 0.85, true, CycleMethod.NO_CYCLE,
                new Stop(0.55, Color.TRANSPARENT), new Stop(1, Color.rgb(0, 0, 0, 0.78)));
        return new Background(new BackgroundFill(luz, null, null), new BackgroundFill(diagonal, null, null),
                new BackgroundFill(vinheta, null, null));
    }

    private static void desenharRaios(GraphicsContext g, double w, double h) {
        double cx = w / 2, cy = h / 2, r = Math.hypot(w, h) / 2;
        g.clearRect(0, 0, w, h);
        Random rnd = new Random(7);
        int n = 22;
        for (int i = 0; i < n; i++) {
            double ang = -Math.PI / 2 + (i + rnd.nextDouble() * 0.4) * 2 * Math.PI / n;
            double larg = Math.toRadians(2.2 + rnd.nextDouble() * 3.4);
            double alfa = 0.05 + rnd.nextDouble() * 0.08;
            g.setFill(new RadialGradient(0, 0, cx, cy, r * 0.62, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(255, 226, 140, alfa)), new Stop(0.35, Color.rgb(255, 210, 110, alfa * 0.55)), new Stop(1, Color.TRANSPARENT)));
            g.fillPolygon(new double[]{cx, cx + Math.cos(ang - larg) * r, cx + Math.cos(ang + larg) * r},
                    new double[]{cy, cy + Math.sin(ang - larg) * r, cy + Math.sin(ang + larg) * r}, 3);
        }
        // brilho atrás do escudo e do troféu
        g.setFill(new RadialGradient(0, 0, cx, cy - 20, 330, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 228, 150, 0.42)), new Stop(0.4, Color.rgb(255, 205, 100, 0.16)), new Stop(1, Color.TRANSPARENT)));
        g.fillOval(cx - 340, cy - 360, 680, 680);
    }

    // ------------------------------------------------------------------ escudo e troféu
    private static Node escudo(String id, double tam) {
        boolean bandeira = id == null || id.startsWith("sel-") || Imagens.paisKingsNations(id) != null;
        if (!bandeira) {
            String rel = Imagens.caminhoEscudo(id);
            Image img = ampliada(rel, tam, tam);
            if (img != null && !img.isError()) {
                ImageView v = new ImageView(img);
                v.setFitWidth(tam);
                v.setFitHeight(tam);
                v.setPreserveRatio(true);
                v.setSmooth(true);
                return v;
            }
        }
        return Imagens.escudo(id, tam);
    }

    private Node trofeu(double alt) {
        String id = c.competicaoId();
        // torneio curto (Apertura/Clausura): o troféu é o da liga
        if (id != null && !"kings".equals(c.ambito()) && c.competicao() != null && c.competicao().contains(" — ") && id.lastIndexOf('-') > 0)
            id = id.substring(0, id.lastIndexOf('-'));
        String rel = Imagens.caminhoTrofeu(id, c.ambito(), c.pais());
        Image img = ampliada(rel, alt * 1.3, alt);
        if (img != null && img.getWidth() > img.getHeight() * 1.6) {
            // logo que é só letreiro ("FIFA WORLD CUP"): a arte do troféu no lugar
            Image t = ampliada(Imagens.caminhoTrofeu(id, c.ambito(), c.pais(), false), alt * 1.3, alt);
            if (t != null) img = t;
        }
        if (img != null && !img.isError()) {
            ImageView v = new ImageView(img);
            v.setFitWidth(alt * 1.3);
            v.setFitHeight(alt);
            v.setPreserveRatio(true);
            v.setSmooth(true);
            return v;
        }
        return Imagens.trofeu(id, c.ambito(), c.pais(), alt);
    }

    /** Ampliações já feitas (os PNGs do jogo são pequenos: escudo 60 px, troféu ~120 px). */
    private static final java.util.Map<String, Image> AMPLIADAS = new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Image> e) { return size() > 12; }
    };

    /**
     * A imagem do asset ampliada para caber em {@code w}×{@code h} (vezes 2,4, para a escala da janela
     * e telas de alta densidade) em passos bicúbicos de 2×: sem os "quadradinhos" de ampliar 60 px para
     * 300 de uma vez. null se não houver arquivo.
     */
    static Image ampliada(String rel, double w, double h) {
        java.nio.file.Path f = rel == null ? null : Imagens.arquivo(rel);
        if (f == null) return null;
        String k = rel + "|" + (int) w + "x" + (int) h;
        synchronized (AMPLIADAS) {
            Image ja = AMPLIADAS.get(k);
            if (ja != null) return ja;
        }
        try {
            java.awt.image.BufferedImage b = javax.imageio.ImageIO.read(f.toFile());
            if (b == null) return null;
            double esc = Math.min(w * 2.4 / b.getWidth(), h * 2.4 / b.getHeight());
            int alvoW = Math.max(1, (int) Math.round(b.getWidth() * esc)), alvoH = Math.max(1, (int) Math.round(b.getHeight() * esc));
            java.awt.image.BufferedImage atual = argb(b, b.getWidth(), b.getHeight());
            while (atual.getWidth() != alvoW || atual.getHeight() != alvoH) {
                int nw = atual.getWidth() * 2 < alvoW ? atual.getWidth() * 2 : alvoW;
                int nh = atual.getHeight() * 2 < alvoH ? atual.getHeight() * 2 : alvoH;
                if (nw < alvoW && nh >= alvoH || nh < alvoH && nw >= alvoW) {
                    nw = alvoW;
                    nh = alvoH;
                }
                atual = argb(atual, nw, nh);
            }
            javafx.scene.image.WritableImage img = new javafx.scene.image.WritableImage(alvoW, alvoH);
            int[] px = atual.getRGB(0, 0, alvoW, alvoH, null, 0, alvoW);
            img.getPixelWriter().setPixels(0, 0, alvoW, alvoH, javafx.scene.image.PixelFormat.getIntArgbInstance(), px, 0, alvoW);
            synchronized (AMPLIADAS) {
                AMPLIADAS.put(k, img);
            }
            return img;
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    private static java.awt.image.BufferedImage argb(java.awt.image.BufferedImage b, int w, int h) {
        java.awt.image.BufferedImage r = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = r.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(b, 0, 0, w, h, null);
        g.dispose();
        return r;
    }

    private static Font fonte(String familia, double tamanho) {
        Font f = Font.font(familia, tamanho);
        if (!familia.equals(f.getFamily())) f = Font.font("Segoe UI", FontWeight.BOLD, tamanho);
        return f;
    }

    // ------------------------------------------------------------------ animação de entrada
    private void entrada(Node raios, Node escudo, Node trofeu, Node texto, Node nome, Node botao) {
        fundo.setOpacity(0);
        raios.setOpacity(0);
        raios.setScaleX(0.55);
        raios.setScaleY(0.55);
        escudo.setOpacity(0);
        escudo.setScaleX(0.55);
        escudo.setScaleY(0.55);
        trofeu.setOpacity(0);
        trofeu.setScaleX(0.4);
        trofeu.setScaleY(0.4);
        trofeu.setTranslateY(70);
        texto.setOpacity(0);
        texto.setScaleX(1.35);
        texto.setScaleY(1.35);
        nome.setOpacity(0);
        nome.setTranslateY(14);
        botao.setOpacity(0);
        Interpolator saida = Interpolator.SPLINE(0.16, 1, 0.3, 1); // desacelera no fim
        Interpolator mola = new Interpolator() { // passa um pouco do tamanho e volta (ease-out-back)
            @Override
            protected double curve(double t) {
                double k = 1.4, u = t - 1;
                return 1 + (k + 1) * u * u * u + k * u * u;
            }
        };
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(fundo.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(320), new KeyValue(fundo.opacityProperty(), 1, saida)),
                new KeyFrame(Duration.millis(120), new KeyValue(escudo.opacityProperty(), 0), new KeyValue(escudo.scaleXProperty(), 0.55),
                        new KeyValue(escudo.scaleYProperty(), 0.55), new KeyValue(raios.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(560), new KeyValue(escudo.opacityProperty(), 1, saida)),
                new KeyFrame(Duration.millis(720), new KeyValue(escudo.scaleXProperty(), 1, mola), new KeyValue(escudo.scaleYProperty(), 1, mola)),
                new KeyFrame(Duration.millis(1000), new KeyValue(raios.opacityProperty(), 1, saida),
                        new KeyValue(raios.scaleXProperty(), 1, saida), new KeyValue(raios.scaleYProperty(), 1, saida)),
                new KeyFrame(Duration.millis(340), new KeyValue(trofeu.opacityProperty(), 0), new KeyValue(trofeu.scaleXProperty(), 0.4),
                        new KeyValue(trofeu.scaleYProperty(), 0.4), new KeyValue(trofeu.translateYProperty(), 70)),
                new KeyFrame(Duration.millis(640), new KeyValue(trofeu.opacityProperty(), 1, saida)),
                new KeyFrame(Duration.millis(900), new KeyValue(trofeu.scaleXProperty(), 1, mola), new KeyValue(trofeu.scaleYProperty(), 1, mola),
                        new KeyValue(trofeu.translateYProperty(), 0, mola)),
                new KeyFrame(Duration.millis(520), new KeyValue(texto.opacityProperty(), 0), new KeyValue(texto.scaleXProperty(), 1.35),
                        new KeyValue(texto.scaleYProperty(), 1.35)),
                new KeyFrame(Duration.millis(960), new KeyValue(texto.opacityProperty(), 1, saida),
                        new KeyValue(texto.scaleXProperty(), 1, saida), new KeyValue(texto.scaleYProperty(), 1, saida)),
                new KeyFrame(Duration.millis(720), new KeyValue(nome.opacityProperty(), 0), new KeyValue(nome.translateYProperty(), 14)),
                new KeyFrame(Duration.millis(1100), new KeyValue(nome.opacityProperty(), 1, saida), new KeyValue(nome.translateYProperty(), 0, saida)),
                new KeyFrame(Duration.millis(1000), new KeyValue(botao.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(1500), new KeyValue(botao.opacityProperty(), 1)));
        raiz.sceneProperty().addListener((o, a, cena) -> {
            if (cena != null && abertaEm == 0) {
                abertaEm = System.nanoTime();
                t.play();
                comecarConfete();
            }
        });
    }

    // ------------------------------------------------------------------ confete
    private final double[] px = new double[CONFETES], py = new double[CONFETES], vy = new double[CONFETES], fase = new double[CONFETES],
            giro = new double[CONFETES], vgiro = new double[CONFETES], larg = new double[CONFETES], alt = new double[CONFETES],
            balanco = new double[CONFETES];
    private final Color[] cor = new Color[CONFETES];
    private static final Color[] OURO = {Color.web("#f6c430"), Color.web("#ffd95a"), Color.web("#e5a823"), Color.web("#fff0b0"),
            Color.web("#c99018"), Color.web("#ffe48a"), Color.web("#ffffff")};

    private void comecarConfete() {
        Random r = new Random(13);
        double w = Math.max(1, raiz.getWidth() > 0 ? raiz.getWidth() : W), h = Math.max(1, raiz.getHeight() > 0 ? raiz.getHeight() : H);
        double esc = Math.max(0.8, h / H);
        for (int i = 0; i < CONFETES; i++) {
            px[i] = r.nextDouble() * w;
            py[i] = h * 0.35 - r.nextDouble() * h * 1.35; // a primeira leva já descendo, o resto entra por cima
            vy[i] = (85 + r.nextDouble() * 95) * esc;
            fase[i] = r.nextDouble() * Math.PI * 2;
            giro[i] = r.nextDouble() * 360;
            vgiro[i] = (r.nextDouble() - 0.5) * 360;
            larg[i] = (6 + r.nextDouble() * 6) * esc;
            alt[i] = (3 + r.nextDouble() * 4) * esc;
            balanco[i] = (14 + r.nextDouble() * 26) * esc;
            cor[i] = OURO[i % OURO.length];
        }
        long[] antes = {0};
        timer = new AnimationTimer() {
            @Override
            public void handle(long agora) {
                if (raiz.getScene() == null || saiu) {
                    stop();
                    return;
                }
                double dt = antes[0] == 0 ? 0 : Math.min(0.05, (agora - antes[0]) / 1e9);
                antes[0] = agora;
                desenharConfete(dt, agora / 1e9);
            }
        };
        timer.start();
    }

    private void desenharConfete(double dt, double tempo) {
        double w = confete.getWidth(), h = confete.getHeight();
        GraphicsContext g = confete.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        for (int i = 0; i < CONFETES; i++) {
            py[i] += vy[i] * dt;
            giro[i] += vgiro[i] * dt;
            if (py[i] > h + 20) {
                py[i] = -20 - Math.random() * 60;
                px[i] = Math.random() * w;
            }
            double x = px[i] + Math.sin(tempo * 1.3 + fase[i]) * balanco[i];
            // o papel girando no ar: a largura "vira" (cosseno) e o brilho acompanha
            double vira = Math.cos(tempo * 3.1 + fase[i] * 2);
            double lw = larg[i] * Math.max(0.15, Math.abs(vira));
            g.save();
            g.translate(x, py[i]);
            g.rotate(giro[i]);
            g.setGlobalAlpha(0.75 + 0.25 * Math.abs(vira));
            g.setFill(vira < 0 ? cor[i].deriveColor(0, 1, 0.78, 1) : cor[i]);
            g.fillRect(-lw / 2, -alt[i] / 2, lw, alt[i]);
            g.restore();
        }
    }

    // ------------------------------------------------------------------ saída
    private void sair() {
        if (saiu) return;
        if (abertaEm != 0 && System.nanoTime() - abertaEm < 500_000_000L) return; // Enter "repetido" da tela anterior
        saiu = true;
        if (timer != null) timer.stop();
        continuar.run();
    }

    @Override
    public Parent raiz() { return raiz; }

    @Override
    public boolean tecla(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.ESCAPE || e.getCode() == KeyCode.SPACE) {
            sair();
            return true;
        }
        return false;
    }

    @Override
    public void aoMostrar() {
        escalar();
        raiz.requestFocus();
    }
}
