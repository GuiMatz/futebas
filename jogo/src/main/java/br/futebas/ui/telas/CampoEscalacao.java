package br.futebas.ui.telas;

import br.futebas.mundo.Posicao;
import br.futebas.ui.comp.Foto;
import br.futebas.ui.comp.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O campo da escalação: gramado com as linhas, a foto redonda de cada jogador na vaga da formação
 * (aro na cor do time), nome, posição, força, capitão e — ao vivo — a barra de energia. Clique e
 * arraste ficam com quem usa ({@link Acoes}); o campo só colore a vaga-alvo pelo encaixe de quem
 * está sendo arrastado. Usado pela tela de Escalação (clube e Kings), pelo passo de escalação antes
 * do jogo e pela troca ao vivo (intervalo, lesão, expulsão).
 */
public final class CampoEscalacao extends Pane {
    static final double W = 430, H = 540;
    /** Caixa de uma vaga (foto, nome, força, energia e cartão) e a altura do centro da foto dentro dela. */
    static final double VAGA_L = 96, VAGA_A = 100, FOTO_Y = 30;

    /**
     * Uma vaga do campo.
     *
     * @param ocupada  false = vaga vazia (silhueta apagada)
     * @param foto     a foto do jogador (null = silhueta)
     * @param forca    força exibida (null = não mostra)
     * @param energia  energia 0–100 (null = sem barra)
     * @param marca    etiqueta vermelha sobre o nome (ex.: "Expulso"); null = nenhuma
     * @param apagada  desenha esmaecida (expulso)
     * @param alerta   nome em vermelho (lesionado/suspenso escalado)
     * @param amarelo  cartão amarelo: um cartãozinho no canto da foto (não mais a etiqueta "Amarelo")
     * @param lesionado destaque avermelhado e o "+" de médico ao lado do nome (apagada + lesionado = saiu
     *                  lesionado sem troca, um a menos: sem cartão vermelho)
     */
    public record Peca(boolean ocupada, String nome, String rotulo, Node foto, Integer forca, boolean capitao, boolean improvisada,
                boolean alerta, Double energia, String marca, boolean apagada, boolean amarelo, boolean lesionado, boolean pendente,
                Double nota, Node escudo) {
        /** Sem nota e sem escudo (escalação, troca ao vivo). */
        public Peca(boolean ocupada, String nome, String rotulo, Node foto, Integer forca, boolean capitao, boolean improvisada,
             boolean alerta, Double energia, String marca, boolean apagada, boolean amarelo, boolean lesionado, boolean pendente) {
            this(ocupada, nome, rotulo, foto, forca, capitao, improvisada, alerta, energia, marca, apagada, amarelo, lesionado, pendente, null, null);
        }

        /**
         * Vaga de prêmio (seleção da liga): a nota média da temporada no lugar da força (cor por faixa)
         * e o escudo do clube no canto da foto.
         */
        public static Peca premio(String nome, String rotulo, Node foto, double nota, Node escudo) {
            return new Peca(true, nome, rotulo, foto, null, false, false, false, null, null, false, false, false, false, nota, escudo);
        }

        /** Sem "pendente". */
        public Peca(boolean ocupada, String nome, String rotulo, Node foto, Integer forca, boolean capitao, boolean improvisada,
             boolean alerta, Double energia, String marca, boolean apagada, boolean amarelo, boolean lesionado) {
            this(ocupada, nome, rotulo, foto, forca, capitao, improvisada, alerta, energia, marca, apagada, amarelo, lesionado, false);
        }

        public Peca(boolean ocupada, String nome, String rotulo, Node foto, Integer forca, boolean capitao, boolean improvisada,
             boolean alerta, Double energia, String marca, boolean apagada) {
            this(ocupada, nome, rotulo, foto, forca, capitao, improvisada, alerta, energia, marca, apagada, false, false, false);
        }

        public Peca(boolean ocupada, String nome, String rotulo, Node foto, Integer forca, boolean capitao, boolean improvisada,
             boolean alerta, Double energia, String marca, boolean apagada, boolean amarelo) {
            this(ocupada, nome, rotulo, foto, forca, capitao, improvisada, alerta, energia, marca, apagada, amarelo, false, false);
        }

        public static Peca vazia(String rotulo) {
            return new Peca(false, "vazio", rotulo, null, null, false, false, false, null, null, false);
        }
    }

    /** O que o campo pede a quem o usa. */
    public interface Acoes {
        void clicar(int vaga);

        /** Conteúdo soltado na vaga (o texto do arraste). */
        void soltar(int vaga, String conteudo);

        /** Texto do arraste que começa na vaga (null = a vaga não arrasta). */
        String arraste(int vaga);

        /** Imagem do arraste que começa na vaga (o "fantasma"). */
        default void fantasma(int vaga, Dragboard db) {}

        /** Encaixe (0 a 1) de quem está sendo arrastado na vaga, para colorir; NaN = sem cor. */
        double encaixeDoArraste(int vaga);

        /** Botão direito na vaga. */
        default void menu(int vaga, Node dono, ContextMenuEvent e) {}

        /** O arraste terminou (soltou em qualquer lugar ou cancelou). */
        default void fimArraste() {}
    }

    private final Acoes acoes;
    private String cor = "#c8372d";
    /** O gramado com as linhas (desenhado uma vez; cada redesenho só troca as vagas). */
    private final Canvas gramado = linhasDoCampo();

    public CampoEscalacao(Acoes acoes) {
        this.acoes = acoes;
        setPrefSize(W, H);
        setMinSize(W, H);
        setMaxSize(W, H);
        getStyleClass().add("campo");
        // nada desenha fora do gramado (nem conta para o tamanho de quem o contém); cantos arredondados
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(W, H);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        setClip(clip);
    }

    public void cor(String c) {
        this.cor = c != null && c.startsWith("#") ? c : "#c8372d";
    }

    /** Foto da vaga: diâmetro (sem o aro) e o lado da caixa com o aro. */
    static final double FOTO_D = 50, FOTO_T = FOTO_D + 4;
    /** Placa do jogador (nome, posição, força e energia) embaixo da foto, que entra um pouco nela. */
    static final double PLACA_L = 86, PLACA_A = 47, PLACA_Y = FOTO_Y + FOTO_T / 2 - 8;
    /** Largura útil do nome na placa (o que passar disso é abreviado: "A. Sanabria"). */
    static final double NOME_MAX = PLACA_L - 16;

    /** Desenha as vagas nos pontos {@code xy}; {@code selecionada} realça uma vaga, {@code animar} "pula" a vaga que acabou de receber alguém. */
    public void desenhar(List<Peca> pecas, double[][] xy, int selecionada, int animar) {
        getChildren().setAll(gramado);
        for (int i = 0; i < pecas.size() && i < xy.length; i++) {
            Peca p = pecas.get(i);
            Pane v = vaga(p);
            if (i == selecionada) v.getStyleClass().add("selecionada");
            // a vaga inteira (foto, placa, cartão) dentro do campo; xy = centro da foto
            v.setLayoutX(Math.max(1, Math.min(W - VAGA_L - 1, xy[i][0] - VAGA_L / 2)));
            v.setLayoutY(Math.max(1, Math.min(H - VAGA_A - 1, xy[i][1] - FOTO_Y)));
            int vaga = i;
            v.setOnMouseClicked(e -> {
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) acoes.clicar(vaga);
            });
            v.setOnContextMenuRequested(e -> acoes.menu(vaga, v, e));
            String arr = acoes.arraste(vaga);
            v.setCursor(arr != null ? javafx.scene.Cursor.OPEN_HAND : javafx.scene.Cursor.DEFAULT);
            v.setOnDragDetected(e -> {
                String conteudo = acoes.arraste(vaga);
                if (conteudo == null) return;
                Dragboard db = v.startDragAndDrop(TransferMode.MOVE);
                javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                cc.putString(conteudo);
                db.setContent(cc);
                acoes.fantasma(vaga, db);
                e.consume();
            });
            v.setOnDragDone(e -> acoes.fimArraste());
            v.setOnDragOver(e -> {
                if (e.getDragboard().hasString()) e.acceptTransferModes(TransferMode.MOVE);
                e.consume();
            });
            v.setOnDragEntered(e -> {
                double enc = acoes.encaixeDoArraste(vaga);
                if (Double.isNaN(enc)) return;
                v.getStyleClass().add(enc >= 0.95 ? "alvo-bom" : enc >= 0.8 ? "alvo-medio" : "alvo-ruim");
            });
            v.setOnDragExited(e -> v.getStyleClass().removeAll("alvo-bom", "alvo-medio", "alvo-ruim"));
            v.setOnDragDropped(e -> {
                String s = e.getDragboard().getString();
                if (s != null) acoes.soltar(vaga, s);
                e.setDropCompleted(true);
                e.consume();
            });
            getChildren().add(v);
            if (i == animar) {
                javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(220), v);
                st.setFromX(1.18);
                st.setFromY(1.18);
                st.setToX(1);
                st.setToY(1);
                st.play();
            }
        }
    }

    /**
     * Uma vaga (VAGA_L × VAGA_A, posições fixas, nada encosta): a foto redonda com o aro na cor do
     * time, o capitão (braçadeira) e o cartão nos cantos de cima da foto; embaixo, a placa escura com
     * o nome (abreviado quando não cabe), a posição e a força num bloco só (força na escala de cor)
     * e, ao vivo, a energia numa barra fina no pé da placa. A marca ("Expulso", "Entra"...) fica na
     * emenda da foto com a placa.
     */
    private Pane vaga(Peca p) {
        Pane v = new Pane();
        v.getStyleClass().add("vaga");
        v.setMinSize(VAGA_L, VAGA_A);
        v.setPrefSize(VAGA_L, VAGA_A);
        v.setMaxSize(VAGA_L, VAGA_A);
        double fx = (VAGA_L - FOTO_T) / 2, fy = FOTO_Y - FOTO_T / 2;

        // placa (atrás da foto, que entra um pouco nela)
        VBox placa = new VBox(1);
        placa.getStyleClass().add("vaga-placa");
        placa.setAlignment(Pos.TOP_CENTER);
        placa.setMinSize(PLACA_L, PLACA_A);
        placa.setPrefSize(PLACA_L, PLACA_A);
        placa.setMaxSize(PLACA_L, PLACA_A);
        placa.relocate((VAGA_L - PLACA_L) / 2, PLACA_Y);

        String texto = p.ocupada() ? nomeCurto(p.nome(), NOME_MAX) : "Vazio";
        Label nome = Ui.rotulo(texto, "vaga-nome");
        nome.setMaxWidth(NOME_MAX + 2);
        if (!p.ocupada()) nome.getStyleClass().add("vaga-nome-vazio");
        if (p.ocupada() && p.alerta()) placa.getStyleClass().add("alerta");
        Node linhaNome = nome;
        if (p.ocupada() && p.lesionado()) {
            // lesionado: o "+" de médico ao lado do nome (requisito)
            HBox nomeCruz = new HBox(3, cruzMedica(), nome);
            nomeCruz.setAlignment(Pos.CENTER);
            nome.setMaxWidth(NOME_MAX - 12);
            nome.setText(nomeCurto(p.nome(), NOME_MAX - 14));
            linhaNome = nomeCruz;
        }

        // posição e força num bloco só: [ZAG | 76], com a energia (abaixo de 100) entre os dois
        Label pos = Ui.rotulo(p.rotulo(), "vaga-pos");
        pos.setMinWidth(Label.USE_PREF_SIZE);
        Posicao posicao = posicao(p.rotulo());
        if (p.ocupada() && p.improvisada()) {
            pos.getStyleClass().add("vaga-pos-fora");
            javafx.scene.control.Tooltip.install(pos, new javafx.scene.control.Tooltip("Fora de posição"));
        } else if (posicao != null) {
            pos.getStyleClass().add(Ui.classeSetor(posicao));
        }
        HBox info = new HBox(0, pos);
        info.getStyleClass().add("vaga-info");
        info.setAlignment(Pos.CENTER);
        info.setMaxWidth(Region.USE_PREF_SIZE);
        if (p.ocupada() && p.nota() != null) {
            Label f = Ui.rotulo(br.futebas.ui.modelo.Textos.umaCasa(p.nota()), "vaga-forca", "vaga-nota", Ui.classeNota(p.nota()));
            f.setMinWidth(Label.USE_PREF_SIZE);
            info.getChildren().add(f);
        } else if (p.ocupada() && p.forca() != null) {
            Label f = Ui.rotulo(String.valueOf(p.forca()), "vaga-forca", Ui.classeForca(p.forca()));
            f.setMinWidth(Label.USE_PREF_SIZE);
            info.getChildren().add(f);
        } else {
            pos.getStyleClass().add("vaga-pos-so");
        }
        HBox linhaInfo = new HBox(5, info);
        linhaInfo.setAlignment(Pos.CENTER);
        if (p.ocupada() && p.energia() != null && Math.round(p.energia()) < 100) {
            Label en = Ui.rotulo(Math.round(p.energia()) + "%", "energia-num", "vaga-energia-num", classeEnergia(p.energia()));
            en.setMinWidth(Label.USE_PREF_SIZE);
            linhaInfo.getChildren().add(en);
        }
        placa.getChildren().addAll(linhaNome, linhaInfo);
        if (p.ocupada() && p.energia() != null) {
            Region espaco = new Region();
            VBox.setVgrow(espaco, Priority.ALWAYS);
            placa.getChildren().addAll(espaco, barraEnergia(p.energia(), PLACA_L - 14, 3, false));
        }
        v.getChildren().add(placa);

        // foto com o aro na cor do time
        Node foto = fotoRedonda(p.ocupada() ? p.foto() : null, FOTO_D, cor, !p.ocupada());
        foto.getStyleClass().add("vaga-foto");
        foto.relocate(fx, fy);
        v.getChildren().add(foto);

        // escudo do clube (prêmios): pequeno, no canto de cima à direita da foto
        if (p.ocupada() && p.escudo() != null) {
            StackPane esc = new StackPane(p.escudo());
            esc.getStyleClass().add("vaga-escudo");
            esc.setMinSize(22, 22);
            esc.setPrefSize(22, 22);
            esc.setMaxSize(22, 22);
            esc.relocate(fx + FOTO_T - 13, fy - 3);
            v.getChildren().add(esc);
        }
        // capitão: braçadeira no canto de cima à esquerda da foto
        if (p.ocupada() && p.capitao()) {
            Label cap = Ui.rotulo("C", "vaga-capitao");
            cap.setMinSize(15, 13);
            cap.setPrefSize(15, 13);
            cap.setMaxSize(15, 13);
            cap.relocate(fx - 3, fy + 1);
            cap.setMouseTransparent(true);
            v.getChildren().add(cap);
        }
        // cartões: amarelo (e vermelho do expulso) pequenos no canto de cima à direita da foto
        boolean vermelho = p.apagada() && !p.lesionado();
        if (p.ocupada() && (p.amarelo() || vermelho)) {
            Region c = cartao(vermelho);
            c.relocate(fx + FOTO_T - 9, fy + 1);
            v.getChildren().add(c);
        }
        // marca ("Expulso", "Um a menos", "Entra", "Troca") na emenda da foto com a placa
        if (p.marca() != null) {
            Label m = Ui.etiqueta(p.marca(), p.apagada() ? "vermelha" : p.pendente() ? "azul" : "ambar");
            m.getStyleClass().add("vaga-marca");
            HBox h = new HBox(m);
            h.setAlignment(Pos.CENTER);
            h.setMinWidth(VAGA_L);
            h.setPrefWidth(VAGA_L);
            h.setMaxWidth(VAGA_L);
            h.relocate(0, PLACA_Y - 13);
            h.setMouseTransparent(true);
            v.getChildren().add(h);
        }

        if (p.ocupada()) {
            if (p.improvisada()) v.getStyleClass().add("improvisada");
            if (p.lesionado()) v.getStyleClass().add("lesionado");
            if (!texto.equals(p.nome())) javafx.scene.control.Tooltip.install(placa, new javafx.scene.control.Tooltip(p.nome()));
        } else {
            v.getStyleClass().add("vazia");
        }
        if (p.apagada()) v.setOpacity(0.45);
        // troca ainda não confirmada (troca ao vivo): placa e brilho azuis e a etiqueta "Entra"/"Troca"
        if (p.pendente()) v.getStyleClass().add("pendente");
        return v;
    }

    private static Posicao posicao(String rotulo) {
        if (rotulo == null) return null;
        try {
            return Posicao.valueOf(rotulo);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ================================================================== nome na placa
    private static final java.util.Set<String> PARTICULAS = java.util.Set.of("de", "da", "do", "das", "dos", "di", "du", "del", "della",
            "van", "von", "der", "den", "ter", "la", "le", "el", "al", "bin", "ben", "mac", "st.", "e");
    private static final java.util.Set<String> SUFIXOS = java.util.Set.of("júnior", "junior", "jr", "jr.", "filho", "neto", "sobrinho", "segundo");

    private static double largura(String s) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(s);
        // a fonte do nome (vaga-nome); buscada na hora porque as fontes do jogo são carregadas na abertura
        t.setFont(javafx.scene.text.Font.font("FUTEBAS Titulo", 12));
        return t.getLayoutBounds().getWidth();
    }

    /**
     * Nome que cabe na placa sem "…": o nome inteiro se couber; senão a inicial e o sobrenome
     * ("Antonio Sanabria" → "A. Sanabria", "Kevin De Bruyne" → "K. De Bruyne", "Vinícius Júnior"
     * → "V. Júnior" só se precisar); senão só o sobrenome. Partículas (de, da, van...) e sufixos
     * (Júnior, Filho, Neto) ficam com o sobrenome.
     */
    static String nomeCurto(String nome, double max) {
        if (nome == null) return "";
        String n = nome.trim().replaceAll("\\s+", " ");
        if (n.isEmpty() || largura(n) <= max) return n;
        String[] p = n.split(" ");
        if (p.length < 2) return n;
        int ini = p.length - 1;
        if (SUFIXOS.contains(p[ini].toLowerCase(java.util.Locale.ROOT)) && ini > 1) ini--;
        while (ini > 1 && PARTICULAS.contains(p[ini - 1].toLowerCase(java.util.Locale.ROOT))) ini--;
        String sobrenome = String.join(" ", java.util.Arrays.copyOfRange(p, ini, p.length));
        String abreviado = p[0].codePoints().limit(1).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append) + ". " + sobrenome;
        if (largura(abreviado) <= max) return abreviado;
        if (largura(sobrenome) <= max) return sobrenome;
        // sobrenome composto comprido: fica a última palavra
        return p[p.length - 1];
    }


    /** O campo dentro de um suporte que cresce com a janela mantendo a proporção (tamanho preferido W×H). */
    public Suporte escalavel() {
        return escalavel(W, H);
    }

    /** Idem, com o tamanho preferido do suporte (a escala vem só do espaço que ele recebe). */
    Suporte escalavel(double largura, double altura) {
        Suporte s = new Suporte(this, largura, altura);
        HBox.setHgrow(s, Priority.ALWAYS);
        return s;
    }

    /**
     * Suporte do campo. Bug corrigido ("cada troca no intervalo a escalação fica menor"): o antigo
     * StackPane + Group media o campo JÁ escalado (e o que passava da borda: brilho da vaga, a animação
     * de 1,18, nomes compridos), então o tamanho preferido seguia a escala e a escala seguia o tamanho —
     * a cada redesenho encolhia. Agora o suporte tem tamanho preferido fixo, a escala vem só da
     * largura/altura que ele recebe, o campo não entra na conta do layout (unmanaged) e é recortado em W×H.
     */
    public static final class Suporte extends Region {
        private final CampoEscalacao campo;
        private final double largura, altura;

        Suporte(CampoEscalacao campo, double largura, double altura) {
            this.campo = campo;
            this.largura = largura;
            this.altura = altura;
            campo.setManaged(false);
            getChildren().add(campo);
            setMinSize(200, 200);
        }

        @Override protected double computePrefWidth(double h) { return largura; }

        @Override protected double computePrefHeight(double w) { return altura; }

        @Override protected double computeMinWidth(double h) { return 200; }

        @Override protected double computeMinHeight(double w) { return 200; }

        @Override
        protected void layoutChildren() {
            double w = getWidth(), h = getHeight();
            double k = Math.max(0.4, Math.min((w - 8) / W, (h - 8) / H));
            campo.resize(W, H);
            campo.relocate((w - W) / 2, (h - H) / 2); // a escala é em volta do centro do campo
            campo.setScaleX(k);
            campo.setScaleY(k);
        }

        double escala() { return campo.getScaleX(); }
    }

    // ================================================================== peças comuns
    /**
     * Foto redonda com aro fino na cor do time (e um contorno escuro para destacar do gramado), sobre
     * um fundo escuro tingido com a cor do time. Vazia: círculo tracejado com a silhueta apagada.
     * Ocupa {@code d + 4} de lado.
     */
    public static Node fotoRedonda(Node foto, double d, String cor, boolean vazia) {
        Node f = foto == null ? new StackPane(Foto.silhueta(d, d)) : foto;
        String c = cor != null && cor.startsWith("#") ? cor : "#c8372d";
        Color base;
        try {
            base = Color.web(c);
        } catch (IllegalArgumentException e) {
            base = Color.web("#c8372d");
        }
        boolean semFoto = foto == null || foto.getStyleClass().contains("sem-foto");
        if (semFoto && f instanceof Region r) {
            r.setStyle("-fx-background-color: #08090b;"); // sem foto real: fundo preto (regra do jogo 13/11)
        } else if (f instanceof Region r) {
            // fundo da foto: a cor do time bem escurecida (rosto destacado em qualquer cor de clube)
            Color centro = base.interpolate(Color.web("#20252c"), 0.62), borda = base.interpolate(Color.web("#0f1216"), 0.84);
            r.setStyle("-fx-background-color: radial-gradient(center 50% 38%, radius 70%, " + web(centro) + ", " + web(borda) + ");");
        }
        f.setClip(new javafx.scene.shape.Circle(d / 2, d / 2, d / 2));
        StackPane aro = new StackPane(f);
        double t = d + 4;
        aro.setMinSize(t, t);
        aro.setPrefSize(t, t);
        aro.setMaxSize(t, t);
        aro.getStyleClass().add("foto-aro");
        if (vazia) {
            f.setOpacity(0.35);
            aro.getStyleClass().add("foto-aro-vazia");
            aro.setStyle("-fx-background-radius: " + t + "; -fx-border-radius: " + t + ";");
        } else {
            aro.setStyle("-fx-background-color: rgba(0,0,0,0.55), " + c + ", #14181d; -fx-background-insets: 0, 0.8, 2.4; -fx-background-radius: " + t + ";");
        }
        return aro;
    }

    private static String web(Color c) {
        return String.format(java.util.Locale.ROOT, "#%02x%02x%02x", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    /** "+" de médico, discreto (11×11: cruz vermelha num quadradinho branco), para lesionado. */
    static Node cruzMedica() {
        javafx.scene.shape.Rectangle h = new javafx.scene.shape.Rectangle(7, 2.2), v = new javafx.scene.shape.Rectangle(2.2, 7);
        h.getStyleClass().add("cruz-medica-traco");
        v.getStyleClass().add("cruz-medica-traco");
        StackPane s = new StackPane(h, v);
        s.getStyleClass().add("cruz-medica");
        s.setMinSize(11, 11);
        s.setPrefSize(11, 11);
        s.setMaxSize(11, 11);
        javafx.scene.control.Tooltip.install(s, new javafx.scene.control.Tooltip("Lesionado"));
        return s;
    }

    /** Cartãozinho (9×12, cantos arredondados, um pouco inclinado): amarelo ou vermelho. */
    static Region cartao(boolean vermelho) {
        Region r = new Region();
        r.getStyleClass().addAll("cartao-mini", vermelho ? "cartao-mini-vermelho" : "cartao-mini-amarelo");
        r.setMinSize(9, 12);
        r.setPrefSize(9, 12);
        r.setMaxSize(9, 12);
        r.setRotate(12);
        r.setMouseTransparent(true);
        javafx.scene.control.Tooltip.install(r, new javafx.scene.control.Tooltip(vermelho ? "Expulso" : "Cartão amarelo"));
        return r;
    }

    /** Ordem do banco de reservas, de cima para baixo (requisito): GOL, LD, ZAG, LE, VOL, MC, MEI, PE, PD, CA. */
    private static final List<Posicao> ORDEM_BANCO = List.of(Posicao.GOL, Posicao.LD, Posicao.ZAG, Posicao.LE, Posicao.VOL,
            Posicao.MC, Posicao.MEI, Posicao.PE, Posicao.PD, Posicao.CA);

    /** Posição de {@code p} na ordem do banco (sem posição: no fim). */
    static int ordemBanco(Posicao p) {
        int i = p == null ? -1 : ORDEM_BANCO.indexOf(p);
        return i < 0 ? ORDEM_BANCO.size() : i;
    }

    /** Classe de cor da energia: verde (≥ 80), amarela (≥ 65), laranja (≥ 50), vermelha. */
    static String classeEnergia(double energia) {
        return energia >= 80 ? "energia-alta" : energia >= 65 ? "energia-media" : energia >= 50 ? "energia-baixa" : "energia-critica";
    }

    /** Barra de energia (com o número ao lado, se {@code numero}). */
    static Node barraEnergia(double energia, double largura, boolean numero) {
        return barraEnergia(energia, largura, 6, numero);
    }

    /** Barra de energia com a altura dada (a da placa do campo é fina). */
    static Node barraEnergia(double energia, double largura, double altura, boolean numero) {
        double e = Math.max(0, Math.min(100, energia));
        Region trilha = new Region();
        trilha.getStyleClass().add("energia-trilha");
        trilha.setPrefSize(largura, altura);
        trilha.setMaxSize(largura, altura);
        Region cheia = new Region();
        cheia.getStyleClass().addAll("energia-cheia", classeEnergia(e));
        cheia.setPrefSize(largura * e / 100, altura);
        cheia.setMaxSize(largura * e / 100, altura);
        StackPane barra = new StackPane(trilha, cheia);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setMaxSize(largura, altura);
        javafx.scene.control.Tooltip.install(barra, new javafx.scene.control.Tooltip("Energia " + Math.round(e) + "%"));
        if (!numero) return barra;
        Label n = Ui.rotulo(Math.round(e) + "%", "energia-num", classeEnergia(e));
        n.setMinWidth(Label.USE_PREF_SIZE);
        HBox h = new HBox(4, barra, n);
        h.setAlignment(Pos.CENTER);
        h.setMaxWidth(Region.USE_PREF_SIZE);
        return h;
    }

    /** Imagem que acompanha o mouse no arraste: cartão com foto, nome, posição e força. */
    static void fantasma(Dragboard db, Node referencia, Node foto, String nome, Posicao pos, Integer forca) {
        HBox card = Ui.linha(8, foto, Ui.rotulo(nome, "texto-forte"), Ui.pos(pos));
        if (forca != null) card.getChildren().add(Ui.forca(forca));
        card.getStyleClass().add("fantasma");
        javafx.scene.Scene sc = new javafx.scene.Scene(card);
        sc.setFill(Color.TRANSPARENT);
        if (Ui.folhaDeEstilo != null) sc.getStylesheets().add(Ui.folhaDeEstilo);
        for (javafx.scene.Parent q = referencia == null ? null : referencia.getParent(); q != null; q = q.getParent()) {
            if (q.getStyle() != null && q.getStyle().contains("-acento")) {
                card.setStyle(q.getStyle());
                break;
            }
        }
        javafx.scene.SnapshotParameters sp = new javafx.scene.SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        javafx.scene.image.WritableImage img = card.snapshot(sp, null);
        db.setDragView(img, img.getWidth() / 2, img.getHeight() / 2);
    }

    /**
     * O gramado: faixas de corte alternadas, um grão bem leve, luz de estádio de cima para baixo, as
     * linhas (área, pequena área, marca do pênalti, meia-lua, círculo central, cantos e as traves)
     * com opacidade suave e uma vinheta escura nas bordas.
     */
    private static Canvas linhasDoCampo() {
        Canvas cv = new Canvas(W, H);
        GraphicsContext g = cv.getGraphicsContext2D();
        // faixas de corte
        int faixas = 14;
        double fh = H / (double) faixas;
        for (int i = 0; i < faixas; i++) {
            g.setFill(i % 2 == 0 ? Color.web("#2e8a4d") : Color.web("#287e46"));
            g.fillRect(0, i * fh, W, fh + 0.5);
        }
        // grão (fixo: o mesmo desenho sempre)
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < 2600; i++) {
            double x = rnd.nextDouble() * W, y = rnd.nextDouble() * H;
            g.setFill(rnd.nextBoolean() ? Color.rgb(255, 255, 255, 0.035) : Color.rgb(0, 0, 0, 0.05));
            g.fillRect(x, y, 1.2, 1.2);
        }
        // luz: um pouco mais clara no alto, mais escura embaixo
        g.setFill(new javafx.scene.paint.LinearGradient(0, 0, 0, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, Color.rgb(255, 255, 255, 0.06)), new javafx.scene.paint.Stop(0.5, Color.TRANSPARENT),
                new javafx.scene.paint.Stop(1, Color.rgb(0, 0, 0, 0.10))));
        g.fillRect(0, 0, W, H);

        // linhas
        Color linha = Color.rgb(255, 255, 255, 0.5);
        g.setStroke(linha);
        g.setLineWidth(1.6);
        double m = 16, cx = W / 2;
        g.strokeRect(m, m, W - 2 * m, H - 2 * m);
        g.strokeLine(m, H / 2, W - m, H / 2);
        g.strokeOval(cx - 48, H / 2 - 48, 96, 96);
        double areaL = 214, areaA = 80, peqL = 100, peqA = 30, penalti = 56, raio = 46;
        g.strokeRect(cx - areaL / 2, m, areaL, areaA);
        g.strokeRect(cx - peqL / 2, m, peqL, peqA);
        g.strokeRect(cx - areaL / 2, H - m - areaA, areaL, areaA);
        g.strokeRect(cx - peqL / 2, H - m - peqA, peqL, peqA);
        // meia-lua: só a parte fora da área, centrada na marca do pênalti
        double meio = Math.toDegrees(Math.acos((areaA - penalti) / raio));
        g.strokeArc(cx - raio, m + penalti - raio, 2 * raio, 2 * raio, 270 - meio, 2 * meio, javafx.scene.shape.ArcType.OPEN);
        g.strokeArc(cx - raio, H - m - penalti - raio, 2 * raio, 2 * raio, 90 - meio, 2 * meio, javafx.scene.shape.ArcType.OPEN);
        // cantos
        double rc = 8;
        g.strokeArc(m - rc, m - rc, 2 * rc, 2 * rc, 270, 90, javafx.scene.shape.ArcType.OPEN);
        g.strokeArc(W - m - rc, m - rc, 2 * rc, 2 * rc, 180, 90, javafx.scene.shape.ArcType.OPEN);
        g.strokeArc(m - rc, H - m - rc, 2 * rc, 2 * rc, 0, 90, javafx.scene.shape.ArcType.OPEN);
        g.strokeArc(W - m - rc, H - m - rc, 2 * rc, 2 * rc, 90, 90, javafx.scene.shape.ArcType.OPEN);
        // marcas: centro e pênaltis
        g.setFill(linha);
        for (double[] ponto : new double[][] {{cx, H / 2}, {cx, m + penalti}, {cx, H - m - penalti}}) g.fillOval(ponto[0] - 2.2, ponto[1] - 2.2, 4.4, 4.4);
        // traves (fora da linha de fundo)
        double gol = 56, fundo = 7;
        g.setFill(Color.rgb(255, 255, 255, 0.10));
        g.fillRect(cx - gol / 2, m - fundo, gol, fundo);
        g.fillRect(cx - gol / 2, H - m, gol, fundo);
        g.strokeRect(cx - gol / 2, m - fundo, gol, fundo);
        g.strokeRect(cx - gol / 2, H - m, gol, fundo);

        // vinheta: bordas mais escuras, o centro limpo
        g.setFill(new javafx.scene.paint.RadialGradient(0, 0, 0.5, 0.5, 0.78, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0.55, Color.TRANSPARENT), new javafx.scene.paint.Stop(1, Color.rgb(0, 0, 0, 0.34))));
        g.fillRect(0, 0, W, H);
        return cv;
    }

    // ================================================================== coordenadas
    /**
     * Grade fixa de posições (como a do Brasfoot, com desenho próprio): cada célula tem x (da
     * esquerda para a direita) e y (do ataque, em cima, ao gol, embaixo). Cada formação só escolhe
     * as células, na ordem das vagas de {@code Formacoes}. Linhas: goleiro; zaga (laterais nas
     * pontas); alas e volantes na mesma faixa; meias ofensivos; ataque com pontas.
     */
    private static final Map<String, double[]> GRADE = Map.ofEntries(
            Map.entry("GOL", new double[] {.5, .92}),
            Map.entry("LE", new double[] {.1, .72}), Map.entry("LD", new double[] {.9, .72}),
            Map.entry("ZAG_E", new double[] {.33, .76}), Map.entry("ZAG_C", new double[] {.5, .78}), Map.entry("ZAG_D", new double[] {.67, .76}),
            Map.entry("ZAG2_E", new double[] {.37, .76}), Map.entry("ZAG2_D", new double[] {.63, .76}),
            Map.entry("ALA_E", new double[] {.1, .49}), Map.entry("ALA_D", new double[] {.9, .49}),
            Map.entry("VOL_E", new double[] {.3, .5}), Map.entry("VOL_C", new double[] {.5, .53}), Map.entry("VOL_D", new double[] {.7, .5}),
            Map.entry("MC_E", new double[] {.25, .44}), Map.entry("MC_D", new double[] {.75, .44}),
            Map.entry("MEI_E", new double[] {.2, .33}), Map.entry("MEI_C", new double[] {.5, .33}), Map.entry("MEI_D", new double[] {.8, .33}),
            Map.entry("PE", new double[] {.13, .2}), Map.entry("PD", new double[] {.87, .2}),
            Map.entry("CA_E", new double[] {.38, .18}), Map.entry("CA_C", new double[] {.5, .16}), Map.entry("CA_D", new double[] {.62, .18}),
            // 7x7 da Kings: zaga de 1 a 4, meio de 1 a 3, ataque de 1 a 2 (mesma grade, células próprias)
            Map.entry("K_ZAG1", new double[] {.5, .74}),
            Map.entry("K_ZAG2_E", new double[] {.3, .72}), Map.entry("K_ZAG2_D", new double[] {.7, .72}),
            Map.entry("K_ZAG3_E", new double[] {.18, .69}), Map.entry("K_ZAG3_D", new double[] {.82, .69}),
            Map.entry("K_ZAG4_E", new double[] {.12, .66}), Map.entry("K_ZAG4_CE", new double[] {.37, .73}),
            Map.entry("K_ZAG4_CD", new double[] {.63, .73}), Map.entry("K_ZAG4_D", new double[] {.88, .66}),
            Map.entry("K_MC1", new double[] {.5, .46}),
            Map.entry("K_MC2_E", new double[] {.3, .45}), Map.entry("K_MC2_D", new double[] {.7, .45}),
            Map.entry("K_MC3_E", new double[] {.16, .42}), Map.entry("K_MC3_D", new double[] {.84, .42}),
            Map.entry("K_FR2_E", new double[] {.33, .27}), Map.entry("K_FR2_D", new double[] {.67, .27}),
            Map.entry("K_CA1", new double[] {.5, .18}),
            Map.entry("K_CA2_E", new double[] {.34, .2}), Map.entry("K_CA2_D", new double[] {.66, .2}));

    /** Células de cada formação, na ordem das vagas de {@code Formacoes} (clube) ou de {@code MotorKings.vagasDaFormacao} (Kings). */
    private static final Map<String, String[]> FORMACOES = Map.ofEntries(
            Map.entry("4-4-2", new String[] {"GOL", "LD", "ZAG2_D", "ZAG2_E", "LE", "VOL_E", "VOL_D", "ALA_E", "ALA_D", "CA_E", "CA_D"}),
            Map.entry("4-3-3", new String[] {"GOL", "LD", "ZAG2_D", "ZAG2_E", "LE", "VOL_C", "VOL_E", "VOL_D", "PE", "PD", "CA_C"}),
            Map.entry("4-2-3-1", new String[] {"GOL", "LD", "ZAG2_D", "ZAG2_E", "LE", "VOL_E", "VOL_D", "MEI_E", "MEI_C", "MEI_D", "CA_C"}),
            Map.entry("4-5-1", new String[] {"GOL", "LD", "ZAG2_D", "ZAG2_E", "LE", "VOL_C", "VOL_E", "VOL_D", "ALA_E", "ALA_D", "CA_C"}),
            Map.entry("3-5-2", new String[] {"GOL", "ZAG_E", "ZAG_C", "ZAG_D", "ALA_D", "ALA_E", "VOL_C", "VOL_E", "VOL_D", "CA_E", "CA_D"}),
            Map.entry("3-4-3", new String[] {"GOL", "ZAG_E", "ZAG_C", "ZAG_D", "ALA_D", "ALA_E", "VOL_E", "VOL_D", "PE", "PD", "CA_C"}),
            Map.entry("5-3-2", new String[] {"GOL", "LD", "ZAG_D", "ZAG_C", "ZAG_E", "LE", "VOL_C", "MC_E", "MC_D", "CA_E", "CA_D"}), // alas com o volante entre eles, 2 meias à frente (requisito 13/11)
            Map.entry("4-4-2 losango", new String[] {"GOL", "LD", "ZAG2_D", "ZAG2_E", "LE", "VOL_C", "MC_E", "MC_D", "MEI_C", "CA_E", "CA_D"}),
            Map.entry("k2-3-1", new String[] {"GOL", "K_ZAG2_E", "K_ZAG2_D", "K_MC3_E", "K_MC1", "K_MC3_D", "K_CA1"}),
            Map.entry("k3-2-1", new String[] {"GOL", "K_ZAG3_E", "K_ZAG1", "K_ZAG3_D", "K_MC2_E", "K_MC2_D", "K_CA1"}),
            Map.entry("k2-2-2", new String[] {"GOL", "K_ZAG2_E", "K_ZAG2_D", "K_MC2_E", "K_MC2_D", "K_CA2_E", "K_CA2_D"}),
            Map.entry("k3-1-2", new String[] {"GOL", "K_ZAG3_E", "K_ZAG1", "K_ZAG3_D", "K_MC1", "K_CA2_E", "K_CA2_D"}),
            Map.entry("k1-3-2", new String[] {"GOL", "K_ZAG1", "K_MC3_E", "K_MC1", "K_MC3_D", "K_CA2_E", "K_CA2_D"}),
            Map.entry("k4-2", new String[] {"GOL", "K_ZAG4_E", "K_ZAG4_CE", "K_ZAG4_CD", "K_ZAG4_D", "K_FR2_E", "K_FR2_D"}));

    /**
     * Arruma as vagas em linhas sem que uma caixa encoste na outra (bug corrigido: na Kings o goleiro
     * ficava em cima da zaga, e a zaga desencontrada). As linhas saem da grade (vagas com alturas
     * parecidas formam uma linha); linha com mais de 4 manda as duas pontas para uma linha acima (alas);
     * mais de 5 linhas, junta as vizinhas pequenas; na linha, se não couber, espalha por igual. As
     * linhas usam a altura toda do campo, do goleiro (embaixo) ao ataque, com o mesmo espaço entre elas.
     */
    static double[][] arrumar(double[][] xy) {
        int n = xy.length;
        if (n == 0) return xy;
        Integer[] ordem = new Integer[n];
        for (int i = 0; i < n; i++) ordem[i] = i;
        java.util.Arrays.sort(ordem, (a, b) -> Double.compare(xy[b][1], xy[a][1])); // de baixo para cima
        List<List<Integer>> linhas = new ArrayList<>();
        double limiar = 0.07 * (H - 44);
        for (int i : ordem) {
            List<Integer> ult = linhas.isEmpty() ? null : linhas.get(linhas.size() - 1);
            if (ult != null && Math.abs(xy[ult.get(0)][1] - xy[i][1]) <= limiar) ult.add(i);
            else linhas.add(new ArrayList<>(List.of(i)));
        }
        Comparator<Integer> porX = Comparator.comparingDouble(i -> xy[i][0]);
        // mais de 4 numa linha: as pontas sobem para uma linha própria (alas, laterais avançados)
        for (int r = 0; r < linhas.size(); r++) {
            List<Integer> l = linhas.get(r);
            if (l.size() <= 4) continue;
            l.sort(porX);
            List<Integer> pontas = new ArrayList<>(List.of(l.remove(0), l.remove(l.size() - 1)));
            linhas.add(r + 1, pontas);
        }
        // mais de 5 linhas: junta as vizinhas que somem até 4 (as menores primeiro)
        while (linhas.size() > 5) {
            int melhor = -1, menor = Integer.MAX_VALUE;
            for (int r = 1; r + 1 < linhas.size(); r++) {
                int soma = linhas.get(r).size() + linhas.get(r + 1).size();
                if (soma <= 4 && soma < menor) {
                    menor = soma;
                    melhor = r;
                }
            }
            if (melhor < 0) break;
            linhas.get(melhor).addAll(linhas.remove(melhor + 1));
        }
        double[][] r = new double[n][2];
        int nl = linhas.size();
        double topoBaixo = H - VAGA_A - 2, topoAlto = 4; // 4: o cartãozinho inclinado passa um pouco da caixa
        double passo = nl <= 1 ? 0 : (topoBaixo - topoAlto) / (nl - 1);
        double m = VAGA_L / 2 + 3;
        for (int k = 0; k < nl; k++) {
            List<Integer> l = linhas.get(k);
            l.sort(porX);
            double y = topoBaixo - k * passo + FOTO_Y;
            boolean apertado = false;
            for (int i = 0; i < l.size(); i++) {
                double x = xy[l.get(i)][0];
                if (x < m - 0.5 || x > W - m + 0.5) apertado = true;
                if (i > 0 && x - xy[l.get(i - 1)][0] < VAGA_L + 4) apertado = true;
            }
            for (int i = 0; i < l.size(); i++) {
                double x = xy[l.get(i)][0];
                if (apertado) x = l.size() == 1 ? Math.max(m, Math.min(W - m, x)) : m + i * (W - 2 * m) / (l.size() - 1);
                r[l.get(i)] = new double[] {x, y};
            }
        }
        return r;
    }

    private static double[][] celulas(String[] cel) {
        double[][] xy = new double[cel.length][2];
        for (int i = 0; i < cel.length; i++) {
            double[] g = GRADE.get(cel[i]);
            xy[i] = new double[] {W * g[0], 22 + (H - 44) * g[1]};
        }
        return xy;
    }

    /**
     * Pontos das vagas de uma formação de 11. Se as posições não forem as da formação (time montado
     * de outro jeito, seleção), distribui pelas posições.
     */
    public static double[][] coordenadasOnze(String formacao, Posicao[] vagas) {
        String[] cel = formacao == null ? null : FORMACOES.get(formacao);
        if (cel == null || cel.length != vagas.length) return arrumar(coordenadas(vagas));
        if (!java.util.Arrays.equals(br.futebas.partida.Formacoes.vagas(formacao), vagas)) return arrumar(coordenadas(vagas));
        return arrumar(celulas(cel));
    }

    /** Pontos das 7 vagas da Kings (formação desconhecida: 2-3-1). */
    public static double[][] coordenadasSete(String formacao) {
        String[] cel = FORMACOES.get("k" + formacao);
        return arrumar(celulas(cel != null ? cel : FORMACOES.get("k2-3-1")));
    }

    private static double[][] coordenadas(Posicao[] vagas) {
        Map<Posicao, int[]> chave = new HashMap<>();
        chave.put(Posicao.GOL, new int[]{0, 0});
        chave.put(Posicao.LE, new int[]{1, -2});
        chave.put(Posicao.ZAG, new int[]{1, 0});
        chave.put(Posicao.LD, new int[]{1, 2});
        chave.put(Posicao.VOL, new int[]{2, 0});
        chave.put(Posicao.MC, new int[]{3, 0});
        chave.put(Posicao.MEI, new int[]{4, 0});
        chave.put(Posicao.PE, new int[]{5, -2});
        chave.put(Posicao.CA, new int[]{5, 0});
        chave.put(Posicao.PD, new int[]{5, 2});
        Map<Integer, List<Integer>> linhas = new HashMap<>();
        for (int i = 0; i < vagas.length; i++) linhas.computeIfAbsent(chave.get(vagas[i])[0], k -> new ArrayList<>()).add(i);
        List<Integer> usadas = new ArrayList<>(linhas.keySet());
        usadas.sort(Comparator.naturalOrder());
        double[][] xy = new double[vagas.length][2];
        for (int li = 0; li < usadas.size(); li++) {
            List<Integer> idx = linhas.get(usadas.get(li));
            idx.sort(Comparator.comparingInt((Integer i) -> chave.get(vagas[i])[1]).thenComparingInt(i -> i));
            double y = H - 62 - li * ((H - 124) / Math.max(1, usadas.size() - 1));
            for (int k = 0; k < idx.size(); k++) {
                double x = W * (k + 1) / (idx.size() + 1.0);
                xy[idx.get(k)] = new double[]{x, y};
            }
        }
        return xy;
    }
}
