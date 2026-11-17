package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.ui.App;
import br.futebas.ui.Caminhos;
import br.futebas.ui.Sessao;
import br.futebas.ui.Tela;
import br.futebas.ui.comp.Escudo;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.ResumoSave;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Abertura: fundo abstrato desenhado (campo em perspectiva só com linhas luminosas que somem na névoa,
 * feixe de luz, poeira de luz e um brilho que respira), o escudo e a marca FUTÉBAS, o cartão "Continuar" do último jogo salvo e o
 * menu (↑/↓ e Enter; N, C, O e Esc continuam como atalhos).
 */
public final class TelaInicio implements Tela {
    private static final SimpleDateFormat SALVO = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"));

    private final StackPane raiz = new StackPane();
    private final List<Sessao.Save> saves = Sessao.saves();
    private final List<Button> itens = new ArrayList<>();
    private final VBox coluna = new VBox();
    private final ImageView logo = new ImageView();
    private final Fundo fundo = new Fundo();
    private VBox cartao;
    private final StackPane escudoCartao = new StackPane();
    private int sel;

    public TelaInicio() {
        raiz.getStyleClass().add("ini");

        // fundo desenhado + véu escuro à esquerda que se dissolve no fundo (legibilidade do menu)
        Region veu = new Region();
        veu.getStyleClass().add("ini-veu");
        veu.setMouseTransparent(true);

        // marca: escudo + FUTÉBAS
        var url = TelaInicio.class.getResource("/br/futebas/ui/icone/logo-512.png");
        if (url == null) url = TelaInicio.class.getResource("/br/futebas/ui/icone/icone-256.png");
        if (url != null) logo.setImage(new Image(url.toExternalForm(), 512, 512, true, true));
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        Label marca = Ui.rotulo("FUTÉBAS", "ini-marca");
        Label edicao = Ui.rotulo("Temporada 2026", "ini-edicao");
        VBox nome = new VBox(marca, edicao);
        nome.setAlignment(Pos.CENTER_LEFT);
        HBox cabeca = new HBox(logo, nome);
        cabeca.getStyleClass().add("ini-cabeca");
        cabeca.setAlignment(Pos.CENTER_LEFT);

        VBox menu = new VBox();
        menu.getStyleClass().add("ini-menu");
        if (!saves.isEmpty()) {
            cartao = cartaoContinuar(saves.get(0));
            menu.getChildren().add(cartao);
        }
        menu.getChildren().addAll(
                item("Novo jogo", null, () -> App.atual().novoJogo()),
                item("Carregar jogo", saves.isEmpty() ? "Nenhum jogo salvo" : saves.size() + (saves.size() == 1 ? " jogo salvo" : " jogos salvos"), this::carregar),
                item("Configurações", null, Hub::abrirOpcoes),
                item("Sair", null, () -> App.atual().palco().close()));

        coluna.getStyleClass().add("ini-coluna");
        Region topo = new Region(), meio = new Region(), fim = new Region();
        VBox.setVgrow(topo, Priority.ALWAYS);
        VBox.setVgrow(fim, Priority.ALWAYS);
        meio.getStyleClass().add("ini-intervalo");
        coluna.getChildren().addAll(topo, cabeca, meio, menu, fim);
        coluna.setMaxWidth(Region.USE_PREF_SIZE);
        coluna.setFillWidth(true);
        StackPane.setAlignment(coluna, Pos.TOP_LEFT);

        raiz.getChildren().addAll(fundo, veu, coluna, rodape());
        Rectangle recorte = new Rectangle();
        recorte.widthProperty().bind(raiz.widthProperty());
        recorte.heightProperty().bind(raiz.heightProperty());
        raiz.setClip(recorte);

        // tamanhos acompanham a altura da janela (a escala da interface já entra na altura lógica)
        raiz.heightProperty().addListener((o, a, b) -> medidas());
        raiz.widthProperty().addListener((o, a, b) -> medidas());

    }

    private Node rodapeNo;

    private Node rodape() {
        if (rodapeNo != null) return rodapeNo;
        String v = TelaInicio.class.getPackage() != null ? TelaInicio.class.getPackage().getImplementationVersion() : null;
        Label versao = Ui.rotulo("v" + (v != null ? v : "0.1.0"), "ini-versao");
        HBox h = new HBox(versao);
        if (!Files.isRegularFile(Caminhos.banco())) {
            Label erro = Ui.rotulo("Banco de dados não encontrado", "ini-erro");
            erro.setTooltip(new javafx.scene.control.Tooltip(Caminhos.banco().toString()));
            h.getChildren().add(0, erro);
        }
        h.setSpacing(16);
        h.setAlignment(Pos.BOTTOM_RIGHT);
        h.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        h.getStyleClass().add("ini-rodape");
        h.setMouseTransparent(true);
        StackPane.setAlignment(h, Pos.BOTTOM_RIGHT);
        rodapeNo = h;
        return h;
    }

    /** Ajusta escala dos tipos (em), logo, margens e a coluna ao tamanho da janela. */
    private void medidas() {
        double w = raiz.getWidth(), h = raiz.getHeight();
        if (w <= 0 || h <= 0) return;
        // altura física (a escala da interface amplia o que já foi diagramado em pixels lógicos)
        double f = br.futebas.ui.Opcoes.escala().fator;
        double k = Math.max(0.74, Math.min(1.45, Math.min(h * f / 800.0, w * f / 1280.0 * 1.08)));
        k = Math.min(k, h / 610.0); // nunca maior do que cabe na altura lógica
        coluna.setStyle("-fx-font-size: " + String.format(Locale.ROOT, "%.2f", 13 * k) + "px;");
        logo.setFitHeight(118 * k);
        escudoCartao.setPrefSize(58 * k, 58 * k);
        escudoCartao.setMinSize(58 * k, 58 * k);
        double mx = Math.max(40, w * 0.055), my = Math.max(28, h * 0.075);
        coluna.setPadding(new Insets(my, 0, my * 0.9, mx));
        coluna.setPrefWidth(mx + 470 * k);
        coluna.setMaxWidth(mx + 470 * k);
    }

    // ------------------------------------------------------------------ menu
    private Button item(String texto, String detalhe, Runnable acao) {
        Label nome = Ui.rotulo(texto, "ini-item-texto");
        HBox linha = new HBox(nome);
        linha.setAlignment(Pos.BASELINE_LEFT);
        if (detalhe != null) {
            Label d = Ui.rotulo(detalhe, "ini-item-sub");
            linha.getChildren().add(d);
            linha.getStyleClass().add("ini-item-linha");
        }
        Region barra = new Region();
        barra.getStyleClass().add("ini-item-barra");
        HBox g = new HBox(barra, linha);
        g.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(linha, Priority.ALWAYS);
        Button b = new Button();
        b.getStyleClass().setAll("ini-item");
        b.setGraphic(g);
        b.setMaxWidth(Double.MAX_VALUE);
        g.prefWidthProperty().bind(b.widthProperty().subtract(4));
        b.setOnAction(e -> acao.run());
        registrar(b, linha);
        return b;
    }

    /** Liga o botão ao laço de foco (setas) e à animação de deslocamento ao focar. */
    private void registrar(Button b, Node desliza) {
        int i = itens.size();
        itens.add(b);
        b.setOnMouseEntered(e -> focar(i));
        Timeline t = new Timeline();
        b.focusedProperty().addListener((o, a, f) -> {
            t.stop();
            t.getKeyFrames().setAll(new KeyFrame(Duration.millis(140), new KeyValue(desliza.translateXProperty(), f ? 10 : 0, Interpolator.EASE_OUT)));
            t.playFromStart();
        });
    }

    private void focar(int i) {
        if (itens.isEmpty()) return;
        sel = Math.floorMod(i, itens.size());
        itens.get(sel).requestFocus();
    }

    private VBox cartaoContinuar(Sessao.Save s) {
        StackPane escudo = escudoCartao;
        escudo.getStyleClass().add("ini-cartao-escudo");
        Label clube = Ui.rotulo(s.nome(), "ini-cartao-clube");
        Label tecnico = Ui.rotulo("", "ini-cartao-meta");
        Label data = Ui.rotulo("", "ini-cartao-meta");
        Label salvo = Ui.rotulo("Salvo em " + SALVO.format(new Date(s.modificado())), "ini-cartao-salvo");
        VBox textos = new VBox(clube, tecnico, data);
        textos.getStyleClass().add("ini-cartao-textos");
        textos.setAlignment(Pos.CENTER_LEFT);
        HBox corpo = new HBox(escudo, textos);
        corpo.getStyleClass().add("ini-cartao-corpo");
        corpo.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textos, Priority.ALWAYS);

        Label acao = Ui.rotulo("Continuar", "ini-cartao-acao");
        HBox topo = new HBox(acao, Ui.espaco(), salvo);
        topo.setAlignment(Pos.BASELINE_LEFT);
        VBox conteudo = new VBox(topo, corpo);
        conteudo.getStyleClass().add("ini-cartao-conteudo");

        Button b = new Button();
        b.getStyleClass().setAll("ini-cartao");
        b.setGraphic(conteudo);
        b.setMaxWidth(Double.MAX_VALUE);
        conteudo.prefWidthProperty().bind(b.widthProperty().subtract(2));
        b.setOnAction(e -> abrir(s));
        registrar(b, corpo);
        VBox caixa = new VBox(b);

        ResumoSave r = ResumoSave.ler(s.arquivo());
        preencher(r, s, escudo, clube, tecnico, data, b);
        if (r == null) {
            Thread t = new Thread(() -> {
                ResumoSave x = ResumoSave.extrair(s.arquivo());
                if (x != null) Platform.runLater(() -> preencher(x, s, escudo, clube, tecnico, data, b));
            }, "resumo-save");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
        return caixa;
    }

    private void preencher(ResumoSave r, Sessao.Save s, StackPane escudo, Label clube, Label tecnico, Label data, Button b) {
        String nomeSave = br.futebas.ui.modelo.AutoSalvamento.semSufixo(s.nome());
        if (r == null) {
            clube.setText(nomeSave);
            tecnico.setText("");
            data.setText("");
            escudo.getChildren().setAll(ajustar(Escudo.gerado(nomeSave, nomeSave, null, null, 64), escudo));
            return;
        }
        clube.setText(r.clube() != null ? r.clube() : nomeSave);
        tecnico.setText(r.multi() ? "Multi: " + r.tecnicos() : r.tecnico() != null ? "Técnico " + r.tecnico() : "");
        data.setText(r.data() != null ? DIA.format(r.data()) : "Temporada " + r.temporada());
        Node e = null;
        if (r.clubeId() != null) {
            String rel = r.escudo() != null ? r.escudo() : "assets/escudos/" + r.clubeId() + ".png";
            Image img = Imagens.imagemCaixa(rel, 64, 64);
            if (img == null && r.clubeId().startsWith("kings-ios")) img = Imagens.imagemCaixa("assets/custom/ios/escudos/" + r.clubeId() + ".png", 64, 64);
            if (img != null) e = ajustar(new ImageView(img), escudo);
        }
        if (e == null) e = ajustar(Escudo.gerado(r.clubeId(), r.clube() != null ? r.clube() : nomeSave, r.cor1(), r.cor2(), 64), escudo);
        escudo.getChildren().setAll(e);
        if (r.cor1() != null) {
            try {
                Color c = Color.web(r.cor1());
                // clube escuro (preto, azul-marinho) usa a segunda cor na faixa do cartão
                if (c.getBrightness() < 0.25 && r.cor2() != null) c = Color.web(r.cor2());
                if (c.getBrightness() > 0.92 && c.getSaturation() < 0.1) c = Color.web("#d9dde3");
                b.setStyle("-ini-clube: " + web(c) + ";");
            } catch (IllegalArgumentException ex) {
                // cor inválida: fica a padrão
            }
        }
    }

    private static ImageView ajustar(ImageView v, StackPane caixa) {
        v.setPreserveRatio(true);
        v.setSmooth(true);
        v.fitHeightProperty().bind(caixa.prefHeightProperty());
        v.fitWidthProperty().bind(caixa.prefWidthProperty());
        return v;
    }

    private static String web(Color c) {
        return String.format("#%02x%02x%02x", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    // ------------------------------------------------------------------ fundo
    /**
     * Fundo abstrato: escuro profundo com o verde/dourado do FUTÉBAS, um campo visto do alto em perspectiva
     * só com linhas finas luminosas que somem na névoa, feixe de luz diagonal, grão e vinheta. Tudo é
     * desenhado uma vez em canvas (de novo só quando o tamanho muda); a animação é um brilho que respira
     * e uma poeira de luz com poucas partículas.
     */
    private static final class Fundo extends Pane {
        private static final int PARTICULAS = 26;
        private final Canvas base = new Canvas();
        private final Canvas brilho = new Canvas();
        private final Canvas linhas = new Canvas();
        private final Region respiro = new Region();
        private final Pane poeira = new Pane();
        private final Canvas acabamento = new Canvas();
        private final javafx.scene.effect.GaussianBlur desfoque = new javafx.scene.effect.GaussianBlur(10);
        private final javafx.scene.shape.Circle[] grao = new javafx.scene.shape.Circle[PARTICULAS];
        private final double[][] part = new double[PARTICULAS][];
        private final Timeline anim = new Timeline();
        private final javafx.animation.AnimationTimer timer;
        private long inicio;

        Fundo() {
            brilho.setEffect(desfoque);
            for (Canvas cv : new Canvas[] {base, brilho, linhas, acabamento}) {
                cv.setCache(true);
                cv.setManaged(false);
            }
            respiro.setManaged(false);
            respiro.setMouseTransparent(true);
            poeira.setManaged(false);
            Random rnd = new Random(7);
            for (int i = 0; i < PARTICULAS; i++) {
                double r = 0.6 + rnd.nextDouble() * 1.5;
                // x, y (0..1), velocidade, fase, alfa
                part[i] = new double[] {0.36 + rnd.nextDouble() * 0.64, rnd.nextDouble(), 0.004 + rnd.nextDouble() * 0.010,
                        rnd.nextDouble() * Math.PI * 2, 0.18 + rnd.nextDouble() * 0.42};
                var c = new javafx.scene.shape.Circle(r);
                c.setFill(rnd.nextDouble() < 0.3 ? Color.rgb(255, 222, 140) : Color.rgb(200, 255, 225));
                c.setManaged(false);
                c.setOpacity(0);
                grao[i] = c;
                poeira.getChildren().add(c);
            }
            getChildren().addAll(base, respiro, brilho, linhas, poeira, acabamento);
            setMinSize(0, 0);
            setMouseTransparent(true);

            // o brilho do meio-campo respira devagar (8 s ida e volta)
            anim.getKeyFrames().setAll(
                    new KeyFrame(Duration.ZERO, new KeyValue(respiro.opacityProperty(), 0.55, Interpolator.EASE_BOTH)),
                    new KeyFrame(Duration.seconds(4), new KeyValue(respiro.opacityProperty(), 1.0, Interpolator.EASE_BOTH)));
            anim.setAutoReverse(true);
            anim.setCycleCount(Animation.INDEFINITE);
            // poeira: poucos pontos sobem devagar e piscam; só mexe na posição/opacidade dos nós, ~30 quadros/s
            timer = new javafx.animation.AnimationTimer() {
                private long ultimo;

                @Override
                public void handle(long agora) {
                    if (agora - ultimo < 33_000_000L) return;
                    ultimo = agora;
                    mexer((agora - inicio) / 1e9);
                }
            };
            sceneProperty().addListener((o, a, cena) -> {
                if (cena == null) parar();
                else tocar();
            });
        }

        private void mexer(double t) {
            double w = getWidth(), h = getHeight();
            for (int i = 0; i < PARTICULAS; i++) {
                double[] p = part[i];
                double y = 1.08 - ((p[1] + t * p[2]) % 1.16);
                double x = p[0] + 0.012 * Math.sin(t * 0.21 + p[3]);
                double pisca = 0.55 + 0.45 * Math.sin(t * 0.7 + p[3] * 3);
                // somem perto das bordas de cima e de baixo e à esquerda (coluna do menu)
                double borda = Math.max(0, Math.min(1, Math.min(y / 0.18, (1.05 - y) / 0.2)));
                double esq = Math.min(1, Math.max(0, (x - 0.36) / 0.16));
                grao[i].setCenterX(x * w);
                grao[i].setCenterY(y * h);
                grao[i].setOpacity(p[4] * pisca * borda * esq);
            }
        }

        private void tocar() {
            inicio = System.nanoTime() - 40_000_000_000L; // começa no meio do movimento (sem "nascer" tudo junto)
            anim.play();
            timer.start();
        }

        private void parar() {
            anim.stop();
            timer.stop();
        }

        @Override
        protected void layoutChildren() {
            double w = Math.ceil(getWidth()), h = Math.ceil(getHeight());
            if (w <= 0 || h <= 0 || base.getWidth() == w && base.getHeight() == h) return;
            for (Canvas cv : new Canvas[] {base, brilho, linhas, acabamento}) {
                cv.setWidth(w);
                cv.setHeight(h);
            }
            desfoque.setRadius(Math.max(6, h * 0.011));
            Campo campo = new Campo(w, h);
            pintarBase(base.getGraphicsContext2D(), w, h, campo);
            GraphicsContext gb = brilho.getGraphicsContext2D();
            gb.clearRect(0, 0, w, h);
            campo.linhas(gb, Color.rgb(90, 235, 160), 0.7, Math.max(2.5, h * 0.0042));
            GraphicsContext gl = linhas.getGraphicsContext2D();
            gl.clearRect(0, 0, w, h);
            campo.linhas(gl, Color.rgb(214, 255, 232), 0.72, Math.max(1, h * 0.0012));
            // um ponto de luz dourado na marca do centro
            double[] c = campo.p(0, 0);
            double rg = h * 0.045;
            gl.setFill(new RadialGradient(0, 0, c[0], c[1], rg, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(255, 236, 170, 0.55)), new Stop(0.18, Color.rgb(246, 196, 48, 0.18)), new Stop(1, Color.TRANSPARENT)));
            gl.fillOval(c[0] - rg, c[1] - rg, 2 * rg, 2 * rg);
            pintarAcabamento(acabamento.getGraphicsContext2D(), w, h);
            // brilho que respira: em volta do círculo central
            double r = h * 0.62;
            respiro.resizeRelocate(c[0] - r, c[1] - r, 2 * r, 2 * r);
            respiro.setBackground(new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundFill(
                    new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                            new Stop(0, Color.rgb(40, 190, 120, 0.16)), new Stop(0.45, Color.rgb(30, 150, 100, 0.06)), new Stop(1, Color.TRANSPARENT)),
                    null, null)));
            mexer(0);
        }

        /** Fundo, feixe de luz e gramado tênue. */
        private static void pintarBase(GraphicsContext g, double w, double h, Campo campo) {
            // fundo profundo: quase preto esverdeado
            g.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#03080a")), new Stop(0.55, Color.web("#051310")), new Stop(1, Color.web("#020605"))));
            g.fillRect(0, 0, w, h);
            double m = Math.max(w, h);
            // luz verde ampla, de baixo para o centro do campo
            g.setFill(new RadialGradient(0, 0, w * 0.66, h * 0.78, m * 0.62, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(18, 110, 70, 0.34)), new Stop(0.5, Color.rgb(10, 70, 46, 0.12)), new Stop(1, Color.TRANSPARENT)));
            g.fillRect(0, 0, w, h);
            // calor dourado no alto à direita (de onde vem o feixe)
            g.setFill(new RadialGradient(0, 0, w * 0.93, -h * 0.08, h * 0.85, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(246, 196, 48, 0.16)), new Stop(0.4, Color.rgb(200, 150, 40, 0.05)), new Stop(1, Color.TRANSPARENT)));
            g.fillRect(0, 0, w, h);

            // gramado: faixas de corte quase invisíveis dentro do campo
            for (int i = 0; i < 12; i += 2) {
                double x0 = -52.5 + i * 105 / 12.0;
                campo.faixa(g, x0, x0 + 105 / 12.0, Color.rgb(120, 230, 170), 0.035);
            }

            // feixe de luz diagonal: cunhas sobrepostas de abertura crescente (borda macia)
            double sx = w * 0.98, sy = -h * 0.12;
            double[] alvo = campo.p(-8, 6);
            double dx = alvo[0] - sx, dy = alvo[1] - sy, comp = Math.hypot(dx, dy);
            double nx = -dy / comp, ny = dx / comp;
            double fx = sx + dx * 1.25, fy = sy + dy * 1.25;
            for (int k = 1; k <= 7; k++) {
                double a0 = w * 0.006 * k, a1 = w * 0.034 * k;
                g.setFill(new RadialGradient(0, 0, sx, sy, comp * 1.25, false, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.rgb(255, 236, 186, 0.036)), new Stop(0.55, Color.rgb(255, 236, 186, 0.019)), new Stop(1, Color.TRANSPARENT)));
                g.fillPolygon(new double[] {sx - nx * a0, sx + nx * a0, fx + nx * a1, fx - nx * a1},
                        new double[] {sy - ny * a0, sy + ny * a0, fy + ny * a1, fy - ny * a1}, 4);
            }
        }

        /** Névoa no alto (as linhas somem nela), vinheta e grão fino. */
        private static void pintarAcabamento(GraphicsContext g, double w, double h) {
            g.clearRect(0, 0, w, h);
            g.setFill(new LinearGradient(0, 0, 0, h, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(3, 8, 10, 0.92)), new Stop(0.16, Color.rgb(4, 10, 11, 0.55)),
                    new Stop(0.34, Color.rgb(5, 12, 12, 0.0)), new Stop(1, Color.TRANSPARENT)));
            g.fillRect(0, 0, w, h);
            g.setFill(new LinearGradient(0, 0, 0, h, false, CycleMethod.NO_CYCLE,
                    new Stop(0.72, Color.TRANSPARENT), new Stop(1, Color.rgb(1, 4, 4, 0.6))));
            g.fillRect(0, 0, w, h);
            g.setFill(new RadialGradient(0, 0, w * 0.62, h * 0.5, Math.hypot(w, h) * 0.62, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.TRANSPARENT), new Stop(0.62, Color.rgb(0, 0, 0, 0.12)), new Stop(1, Color.rgb(0, 0, 0, 0.62))));
            g.fillRect(0, 0, w, h);
            g.setFill(new javafx.scene.paint.ImagePattern(ruido(), 0, 0, 160, 160, false));
            g.fillRect(0, 0, w, h);
        }

        private static Image ruido() {
            int n = 160;
            javafx.scene.image.WritableImage img = new javafx.scene.image.WritableImage(n, n);
            var px = img.getPixelWriter();
            Random rnd = new Random(11);
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int v = rnd.nextInt(256), a = rnd.nextInt(14);
                    px.setArgb(x, y, (a << 24) | (v << 16) | (v << 8) | v);
                }
            }
            return img;
        }
    }

    /** Campo de 105×68 m num plano visto do alto e de lado, projetado em perspectiva para a tela. */
    private static final class Campo {
        private static final double GIRO = Math.toRadians(-24), INCLINA = Math.toRadians(36), ALTURA = 46, PERTO = 4;
        private final double cx, cy, f, recuo, h;

        Campo(double w, double h) {
            this.h = h;
            double esc = Math.min(h, w * 0.62);
            f = esc * 0.84;
            cx = w * 0.69;
            cy = h * 0.58;
            recuo = ALTURA / Math.tan(INCLINA);
        }

        /** {x da tela, y da tela, profundidade}; profundidade &lt; PERTO = colado ou atrás da câmera. */
        double[] p(double x, double z) {
            double xr = x * Math.cos(GIRO) - z * Math.sin(GIRO), zr = x * Math.sin(GIRO) + z * Math.cos(GIRO);
            double py = -ALTURA, pz = zr + recuo;
            double vy = py * Math.cos(INCLINA) + pz * Math.sin(INCLINA);
            double vz = Math.max(-py * Math.sin(INCLINA) + pz * Math.cos(INCLINA), 0.01);
            return new double[] {cx + f * xr / vz, cy - f * vy / vz, vz};
        }

        /** Força da linha: some ao longe (névoa) e no alto da tela. */
        private double alcance(double prof, double y) {
            double longe = 1 - suave((prof - 50) / 55);
            double cima = suave((y - h * 0.08) / (h * 0.30));
            return longe * cima;
        }

        private static double suave(double t) {
            t = Math.max(0, Math.min(1, t));
            return t * t * (3 - 2 * t);
        }

        void faixa(GraphicsContext g, double x0, double x1, Color cor, double alfa) {
            int n = 10;
            for (int i = 0; i < n; i++) {
                double z0 = -34 + 68.0 * i / n, z1 = -34 + 68.0 * (i + 1) / n;
                double[] a = p(x0, z0), b = p(x1, z0), c = p(x1, z1), d = p(x0, z1);
                if (Math.min(Math.min(a[2], b[2]), Math.min(c[2], d[2])) < PERTO) continue;
                double k = alcance((a[2] + c[2]) / 2, (a[1] + c[1]) / 2);
                if (k <= 0.01) continue;
                g.setFill(Color.color(cor.getRed(), cor.getGreen(), cor.getBlue(), alfa * k));
                g.fillPolygon(new double[] {a[0], b[0], c[0], d[0]}, new double[] {a[1], b[1], c[1], d[1]}, 4);
            }
        }

        /** Todas as marcações do campo, em segmentos curtos com alfa conforme a distância. */
        void linhas(GraphicsContext g, Color cor, double alfa, double espessura) {
            g.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            List<double[][]> tracos = new ArrayList<>();
            double cl = 52.5, cm = 34;
            tracos.add(reta(-cl, -cm, cl, -cm));
            tracos.add(reta(cl, -cm, cl, cm));
            tracos.add(reta(cl, cm, -cl, cm));
            tracos.add(reta(-cl, cm, -cl, -cm));
            tracos.add(reta(0, -cm, 0, cm));
            tracos.add(arco(0, 0, 9.15, 0, 360));
            for (int s = -1; s <= 1; s += 2) {
                double gx = s * cl, d = -s;
                // grande área, pequena área, gol, meia-lua, escanteios
                tracos.add(reta(gx, -20.16, gx + d * 16.5, -20.16));
                tracos.add(reta(gx + d * 16.5, -20.16, gx + d * 16.5, 20.16));
                tracos.add(reta(gx + d * 16.5, 20.16, gx, 20.16));
                tracos.add(reta(gx, -9.16, gx + d * 5.5, -9.16));
                tracos.add(reta(gx + d * 5.5, -9.16, gx + d * 5.5, 9.16));
                tracos.add(reta(gx + d * 5.5, 9.16, gx, 9.16));
                tracos.add(reta(gx, -3.66, gx - d * 2.0, -3.66));
                tracos.add(reta(gx - d * 2.0, -3.66, gx - d * 2.0, 3.66));
                tracos.add(reta(gx - d * 2.0, 3.66, gx, 3.66));
                double px = gx + d * 11, ang = Math.toDegrees(Math.acos(5.5 / 9.15));
                tracos.add(s < 0 ? arco(px, 0, 9.15, -ang, ang) : arco(px, 0, 9.15, 180 - ang, 180 + ang));
                tracos.add(s < 0 ? arco(gx, -cm, 1, 0, 90) : arco(gx, -cm, 1, 90, 180));
                tracos.add(s < 0 ? arco(gx, cm, 1, 270, 360) : arco(gx, cm, 1, 180, 270));
            }
            g.setLineWidth(espessura);
            for (double[][] t : tracos) {
                double[] ant = null;
                for (double[] q : t) {
                    double[] s = p(q[0], q[1]);
                    if (ant != null && ant[2] > PERTO && s[2] > PERTO) {
                        double k = alcance((ant[2] + s[2]) / 2, (ant[1] + s[1]) / 2);
                        if (k > 0.01) {
                            g.setStroke(Color.color(cor.getRed(), cor.getGreen(), cor.getBlue(), alfa * k));
                            g.strokeLine(ant[0], ant[1], s[0], s[1]);
                        }
                    }
                    ant = s;
                }
            }
            // marcas do centro e dos pênaltis
            for (double[] q : new double[][] {{0, 0}, {-41.5, 0}, {41.5, 0}}) {
                double[] s = p(q[0], q[1]);
                if (s[2] < PERTO) continue;
                double k = alcance(s[2], s[1]), r = espessura * 1.6;
                g.setFill(Color.color(cor.getRed(), cor.getGreen(), cor.getBlue(), Math.min(1, alfa * k * 1.3)));
                g.fillOval(s[0] - r, s[1] - r * 0.7, 2 * r, 1.4 * r);
            }
        }

        private static double[][] reta(double x0, double z0, double x1, double z1) {
            int n = (int) Math.max(2, Math.ceil(Math.hypot(x1 - x0, z1 - z0) / 1.5));
            double[][] r = new double[n + 1][];
            for (int i = 0; i <= n; i++) r[i] = new double[] {x0 + (x1 - x0) * i / n, z0 + (z1 - z0) * i / n};
            return r;
        }

        private static double[][] arco(double x, double z, double raio, double de, double ate) {
            int n = (int) Math.max(6, Math.ceil(Math.abs(ate - de) / 4));
            double[][] r = new double[n + 1][];
            for (int i = 0; i <= n; i++) {
                double a = Math.toRadians(de + (ate - de) * i / n);
                r[i] = new double[] {x + raio * Math.cos(a), z + raio * Math.sin(a)};
            }
            return r;
        }
    }
    // ------------------------------------------------------------------ ações
    private void abrir(Sessao.Save s) {
        App.atual().trabalhar("Carregando " + s.nome() + "...", () -> {
            Jogo j = Jogo.carregar(s.arquivo(), Sessao.banco());
            return new Sessao(j, br.futebas.ui.modelo.AutoSalvamento.semSufixo(s.nome()));
        }, sessao -> App.atual().abrir(sessao));
    }

    private void carregar() {
        if (saves.isEmpty()) {
            Ui.informar("Carregar jogo", "Nenhum jogo salvo ainda.");
            return;
        }
        ListView<Sessao.Save> lista = new ListView<>();
        lista.getItems().setAll(saves);
        lista.setCellFactory(x -> new ListCell<>() {
            @Override
            protected void updateItem(Sessao.Save s, boolean vazio) {
                super.updateItem(s, vazio);
                setText(null);
                setGraphic(vazio || s == null ? null
                        : new VBox(1, Ui.rotulo(s.nome(), "texto-forte"), Ui.rotulo(SALVO.format(new Date(s.modificado())), "texto-pequeno")));
            }
        });
        lista.getSelectionModel().select(0);
        lista.setPrefSize(460, 320);
        // Ui.modal monta a própria janela (os botões de um Dialog não chegam a existir): o Excluir é uma
        // das respostas; depois de excluir, a janela volta com a lista atualizada
        boolean mudou = false;
        while (true) {
            int r = Ui.modal("Escolha o jogo salvo", lista, "Carregar", "Excluir", "Cancelar");
            Sessao.Save s = lista.getSelectionModel().getSelectedItem();
            if (r == 0 && s != null) {
                abrir(s);
                return;
            }
            if (r != 1 || s == null) break;
            if (!Ui.confirmar("Excluir jogo salvo", "Excluir \"" + s.nome() + "\"? Isso não pode ser desfeito.")) continue;
            if (!Sessao.excluir(s)) {
                Ui.informar("Excluir jogo salvo", "Não foi possível excluir o arquivo: " + s.arquivo());
                continue;
            }
            mudou = true;
            int i = lista.getSelectionModel().getSelectedIndex();
            lista.getItems().remove(s);
            saves.remove(s);
            if (lista.getItems().isEmpty()) break;
            lista.getSelectionModel().select(Math.min(i, lista.getItems().size() - 1));
        }
        if (mudou) App.atual().irParaInicio(); // atualiza o "Continuar" e a contagem
    }

    @Override
    public Parent raiz() { return raiz; }

    @Override
    public void aoMostrar() {
        // entrada: a coluna aparece suave, uma vez
        coluna.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(320), coluna);
        fade.setToValue(1);
        fade.play();
        Platform.runLater(() -> {
            medidas();
            focar(0);
        });
    }

    @Override
    public boolean tecla(KeyEvent e) {
        KeyCode k = e.getCode();
        if (k == KeyCode.DOWN || k == KeyCode.TAB && !e.isShiftDown()) focar(atual() + 1);
        else if (k == KeyCode.UP || k == KeyCode.TAB) focar(atual() - 1);
        else if (k == KeyCode.ENTER || k == KeyCode.SPACE) {
            int i = atual();
            if (i >= 0) itens.get(i).fire();
        } else if (k == KeyCode.N) App.atual().novoJogo();
        else if (k == KeyCode.C) carregar();
        else if (k == KeyCode.O) Hub.abrirOpcoes();
        else if (k == KeyCode.ESCAPE) App.atual().palco().close();
        else return false;
        return true;
    }

    private int atual() {
        for (int i = 0; i < itens.size(); i++) if (itens.get(i).isFocused()) return i;
        return itens.isEmpty() ? -1 : sel;
    }
}
