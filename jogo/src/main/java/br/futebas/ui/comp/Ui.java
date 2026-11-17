package br.futebas.ui.comp;

import br.futebas.mundo.Mundo;
import br.futebas.mundo.Posicao;
import br.futebas.ui.modelo.Textos;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Peças da interface (tipografia, painéis, insígnias, tabelas, diálogos) usadas por todas as telas. */
public final class Ui {
    private Ui() {}

    /** Folha de estilos e janela dona, para os diálogos (definidas pelo App). */
    public static String folhaDeEstilo;
    public static Window janela;
    /** Sem diálogos (modo automático de capturas): informar só registra, confirmar responde sim. */
    public static volatile boolean semDialogos;

    // ------------------------------------------------------------------ texto
    public static Label rotulo(String texto, String... classes) {
        Label l = new Label(texto == null ? "" : texto);
        l.getStyleClass().addAll(classes);
        return l;
    }

    public static Label titulo(String texto) { return rotulo(texto, "titulo-tela"); }

    public static Label tituloPainel(String texto) { return rotulo(texto, "titulo-painel"); }

    public static Label paragrafo(String texto, String... classes) {
        Label l = rotulo(texto, classes);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    public static VBox dado(String rotulo, String valor) {
        VBox v = new VBox(1, rotulo(rotulo, "dado-rotulo"), rotulo(valor, "dado-valor"));
        v.setMinWidth(Region.USE_PREF_SIZE);
        return v;
    }

    public static VBox dado(String rotulo, Node valor) {
        VBox v = new VBox(2, rotulo(rotulo, "dado-rotulo"), valor);
        v.setMinWidth(Region.USE_PREF_SIZE);
        return v;
    }

    // ------------------------------------------------------------------ layout
    public static Button botao(String texto, Runnable acao, String... classes) {
        Button b = new Button(texto);
        b.getStyleClass().addAll(classes);
        b.setOnAction(e -> acao.run());
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    public static Region espaco() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static HBox linha(double espaco, Node... filhos) {
        HBox h = new HBox(espaco, filhos);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    public static VBox coluna(double espaco, Node... filhos) {
        return new VBox(espaco, filhos);
    }

    public static VBox conteudo(Node... filhos) {
        VBox v = new VBox(16, filhos);
        v.getStyleClass().add("conteudo");
        return v;
    }

    /** Painel branco com título opcional e uma área de ações à direita do título. */
    public static VBox painelAcoes(String titulo, Node acoes, Node... filhos) {
        VBox v = new VBox(10);
        v.getStyleClass().add("painel");
        if (titulo != null || acoes != null) {
            HBox cab = linha(8, tituloPainel(titulo == null ? "" : titulo), espaco());
            if (acoes != null) cab.getChildren().add(acoes);
            v.getChildren().add(cab);
        }
        v.getChildren().addAll(filhos);
        return v;
    }

    public static VBox painel(String titulo, Node... filhos) {
        return painelAcoes(titulo, null, filhos);
    }

    /** Painel cujo corpo é uma tabela encostada nas bordas (cabeçalho com título e ações). */
    public static VBox painelTabela(String titulo, Node acoes, TableView<?> t) {
        VBox v = new VBox();
        v.getStyleClass().add("painel-tabela");
        if (titulo != null || acoes != null) {
            HBox cab = linha(8, tituloPainel(titulo == null ? "" : titulo), espaco());
            if (acoes != null) cab.getChildren().add(acoes);
            cab.getStyleClass().add("painel-cabeca");
            v.getChildren().add(cab);
        }
        t.getStyleClass().add("sem-borda");
        v.getChildren().add(t);
        VBox.setVgrow(t, Priority.ALWAYS);
        return v;
    }

    /**
     * Grade que preenche a largura: os cartões (Region) ganham a mesma largura, a maior possível
     * a partir de {@code minimo}, sem sobrar faixa vazia à direita.
     */
    public static javafx.scene.layout.FlowPane gradeFluida(double minimo, double espaco) {
        javafx.scene.layout.FlowPane fp = new javafx.scene.layout.FlowPane(espaco, espaco);
        Runnable ajustar = () -> {
            javafx.geometry.Insets in = fp.getInsets();
            double w = fp.getWidth() - in.getLeft() - in.getRight() - 1;
            if (w <= 0) return;
            int n = Math.max(1, (int) Math.floor((w + espaco) / (minimo + espaco)));
            double cw = Math.floor((w - (n - 1) * espaco) / n);
            for (Node c : fp.getChildren()) {
                if (c instanceof Region r) {
                    r.setMinWidth(cw);
                    r.setPrefWidth(cw);
                    r.setMaxWidth(cw);
                }
            }
        };
        fp.widthProperty().addListener((o, a, b) -> ajustar.run());
        fp.getChildren().addListener((javafx.collections.ListChangeListener<Node>) c -> ajustar.run());
        return fp;
    }

    public static ScrollPane rolar(Node n) {
        ScrollPane sp = new ScrollPane(n);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    public static Region separadorH() {
        Region r = new Region();
        r.getStyleClass().add("separador-h");
        return r;
    }

    public static Region separadorV() {
        Region r = new Region();
        r.getStyleClass().add("separador-v");
        return r;
    }

    /** Estado vazio: o que falta e (opcional) a ação que resolve. */
    public static VBox vazio(String texto, String acao, Runnable r) {
        Label l = paragrafo(texto);
        l.setMaxWidth(420);
        VBox v = new VBox(8, l);
        v.getStyleClass().add("vazio");
        if (acao != null && r != null) v.getChildren().add(botao(acao, r));
        return v;
    }

    public static HBox aviso(String texto, String tipo, Node... acoes) {
        Label l = paragrafo(texto);
        HBox.setHgrow(l, Priority.ALWAYS);
        l.setMaxWidth(Double.MAX_VALUE);
        HBox h = linha(10, l);
        h.getChildren().addAll(acoes);
        h.getStyleClass().add(tipo == null ? "aviso" : tipo);
        return h;
    }

    /** Grupo de botões exclusivos (segmentado). */
    public static <T> HBox segmentos(List<T> valores, Function<T, String> nome, T atual, Consumer<T> aoEscolher) {
        HBox h = new HBox();
        ToggleGroup g = new ToggleGroup();
        for (int i = 0; i < valores.size(); i++) {
            T v = valores.get(i);
            ToggleButton b = new ToggleButton(nome.apply(v));
            b.setToggleGroup(g);
            b.setFocusTraversable(false);
            if (i == 0) b.getStyleClass().add("primeiro");
            if (i == valores.size() - 1) b.getStyleClass().add("ultimo");
            b.setSelected(v.equals(atual));
            b.setOnAction(e -> {
                if (!b.isSelected()) b.setSelected(true);
                aoEscolher.accept(v);
            });
            h.getChildren().add(b);
        }
        return h;
    }

    /**
     * Controle segmentado que ocupa a largura toda: os botões dividem o espaço por igual e o texto
     * nunca vaza do cartão (encolhe com reticências e mostra o nome inteiro na dica).
     */
    public static <T> HBox segmentosCheios(List<T> valores, Function<T, String> nome, T atual, Consumer<T> aoEscolher) {
        HBox h = segmentos(valores, nome, atual, aoEscolher);
        h.getStyleClass().add("segmentos-cheios");
        h.setMaxWidth(Double.MAX_VALUE);
        h.setMinWidth(0);
        for (Node n : h.getChildren()) {
            ToggleButton b = (ToggleButton) n;
            b.setMaxWidth(Double.MAX_VALUE);
            b.setMinWidth(0);
            b.setPrefWidth(10);
            b.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
            b.setTooltip(new javafx.scene.control.Tooltip(b.getText()));
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        return h;
    }

    /**
     * Aba montada só quando for aberta pela primeira vez (as abas escondidas também pagam CSS e
     * layout; montar tudo de uma vez travava a abertura de alguns menus com o banco completo).
     * Depois de pôr as abas no TabPane e escolher a selecionada, chame {@link #ativarSobDemanda}.
     */
    public static javafx.scene.control.Tab abaSobDemanda(String nome, java.util.function.Supplier<Node> fabrica) {
        javafx.scene.control.Tab t = new javafx.scene.control.Tab(nome);
        t.getProperties().put("futebas.fabrica", fabrica);
        return t;
    }

    // ------------------------------------------------------------------ montagem adiada
    private static final java.util.ArrayDeque<Runnable> ADIADOS = new java.util.ArrayDeque<>();
    private static javafx.animation.AnimationTimer montador;

    /**
     * Lugar reservado (largura × altura) que recebe o conteúdo um quadro depois, um por quadro, na
     * ordem pedida: um painel com 12 tabelas de grupo aparece com as primeiras prontas e as outras
     * chegam em seguida, sem travar a abertura. Se o painel sair da tela antes, nada é montado.
     * Só na thread da tela.
     */
    public static Node adiado(java.util.function.Supplier<Node> fabrica, double largura, double altura) {
        StackPane p = new StackPane();
        p.setPrefSize(largura, altura);
        p.setMinHeight(altura);
        p.getStyleClass().add("adiado");
        ADIADOS.add(() -> {
            if (p.getScene() == null) return; // o painel já foi trocado
            Node n = fabrica.get();
            p.setMinHeight(Region.USE_COMPUTED_SIZE);
            p.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
            p.getChildren().setAll(n);
        });
        if (montador == null) {
            montador = new javafx.animation.AnimationTimer() {
                @Override
                public void handle(long agora) {
                    long fim = System.nanoTime() + 12_000_000L; // até ~12 ms por quadro
                    while (!ADIADOS.isEmpty() && System.nanoTime() < fim) {
                        try {
                            ADIADOS.poll().run();
                        } catch (RuntimeException e) {
                            // um bloco com erro não impede os outros
                        }
                    }
                    if (ADIADOS.isEmpty()) stop();
                }
            };
        }
        montador.start();
        return p;
    }

    /** Monta já o que estiver adiado (testes e medições que precisam da tela completa). */
    public static void montarAdiados() {
        while (!ADIADOS.isEmpty()) ADIADOS.poll().run();
        if (montador != null) montador.stop();
    }

    /** Monta a aba selecionada agora e as outras quando forem escolhidas. */
    @SuppressWarnings("unchecked")
    public static void ativarSobDemanda(javafx.scene.control.TabPane tp) {
        for (javafx.scene.control.Tab t : tp.getTabs()) {
            if (!(t.getProperties().get("futebas.fabrica") instanceof java.util.function.Supplier<?>)) continue;
            Runnable montar = () -> {
                Object f = t.getProperties().remove("futebas.fabrica");
                if (f != null) t.setContent(((java.util.function.Supplier<Node>) f).get());
            };
            if (t.isSelected()) montar.run();
            else t.selectedProperty().addListener((o, a, b) -> {
                if (b) montar.run();
            });
        }
    }

    // ------------------------------------------------------------------ insígnias
    public static String classeSetor(Posicao p) {
        return switch (p.setor) {
            case 'G' -> "pos-g";
            case 'D' -> "pos-d";
            case 'M' -> "pos-m";
            default -> "pos-a";
        };
    }

    public static Label pos(Posicao p) {
        Label l = rotulo(p == null ? "—" : p.name(), "pos");
        if (p != null) l.getStyleClass().add(classeSetor(p));
        return l;
    }

    public static String classeForca(int f) {
        if (f >= 82) return "f6";
        if (f >= 74) return "f5";
        if (f >= 66) return "f4";
        if (f >= 58) return "f3";
        if (f >= 50) return "f2";
        return "f1";
    }

    /**
     * Faixa de cor da nota média da temporada (mesma escala de cor da força). A média de quem joga
     * fica perto de 6,0; 7,3+ é temporada de Bola de Ouro.
     */
    public static String classeNota(double n) {
        if (n >= 7.3) return "f6";
        if (n >= 6.9) return "f5";
        if (n >= 6.5) return "f4";
        if (n >= 6.2) return "f3";
        if (n >= 5.8) return "f2";
        return "f1";
    }

    /** Nota como bloco colorido ("7,4"); sem jogos, "—". */
    public static Label nota(double n, boolean temJogos) {
        Label l = temJogos ? rotulo(br.futebas.ui.modelo.Textos.umaCasa(n), "forca", "nota-bloco", classeNota(n)) : rotulo("—", "texto-suave");
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    public static String corForca(int f) {
        return switch (classeForca(f)) {
            case "f6" -> "#4fdc98";
            case "f5" -> "#39c47f";
            case "f4" -> "#9ccc58";
            case "f3" -> "#e3bd2e";
            case "f2" -> "#f08a3a";
            default -> "#ff6a5c";
        };
    }

    /** Força como bloco colorido (tabelas). */
    public static Label forca(int f) {
        Label l = rotulo(String.valueOf(f), "forca", classeForca(f));
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    /** Força como número grande colorido (fichas). */
    public static Label forcaGrande(int f, String classeTamanho) {
        return rotulo(String.valueOf(f), classeTamanho, "forca-texto", classeForca(f));
    }

    /** Últimos resultados (3 = vitória, 1 = empate, 0 = derrota), do mais antigo ao mais recente. */
    public static HBox forma(List<Integer> pontos, int n) {
        HBox h = new HBox(3);
        h.setAlignment(Pos.CENTER_LEFT);
        int ini = Math.max(0, pontos.size() - n);
        for (int i = ini; i < pontos.size(); i++) {
            int p = pontos.get(i);
            Label l = rotulo(p == 3 ? "V" : p == 1 ? "E" : "D", "forma", p == 3 ? "forma-v" : p == 1 ? "forma-e" : "forma-d");
            h.getChildren().add(l);
        }
        if (h.getChildren().isEmpty()) h.getChildren().add(rotulo("sem jogos", "texto-pequeno"));
        return h;
    }

    public static Label forma(String r) {
        return rotulo(r, "forma", "V".equals(r) ? "forma-v" : "E".equals(r) ? "forma-e" : "forma-d");
    }

    public static Label etiqueta(String texto, String cor) {
        Label l = rotulo(texto, "etiqueta");
        if (cor != null) l.getStyleClass().add(cor);
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    /** Ícone "i" com a explicação só no tooltip (sem parágrafos de instrução nas telas). */
    public static Label info(String texto) {
        Label l = rotulo("i", "info");
        javafx.scene.control.Tooltip tt = new javafx.scene.control.Tooltip(texto);
        tt.setWrapText(true);
        tt.setMaxWidth(360);
        tt.setShowDelay(javafx.util.Duration.millis(150));
        javafx.scene.control.Tooltip.install(l, tt);
        return l;
    }

    public static Label tecla(String t) {
        Label l = rotulo(t, "tecla");
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    /**
     * Quem abre a ficha de um clube ao clicar no escudo (a central define; fora dela é null e o
     * escudo é só imagem).
     */
    public static volatile Consumer<String> aoAbrirClube;
    /** Quem abre a ficha de um jogador e a de um técnico da IA (definidos pelo App). */
    public static volatile Consumer<Integer> aoAbrirJogador, aoAbrirTecnico;

    /** Texto que navega: cursor de mão e sublinhado ao passar o mouse. */
    public static Label link(String texto, Runnable acao, String... classes) {
        Label l = rotulo(texto, classes);
        l.getStyleClass().add("link");
        l.setCursor(javafx.scene.Cursor.HAND);
        l.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                acao.run();
                e.consume();
            }
        });
        return l;
    }

    /** Quem abre a página de uma competição (logo, troféu ou nome). */
    public static volatile Consumer<String> aoAbrirCompeticao;

    /** Clube, seleção ("sel-XXX") ou time da Kings League: tudo que tem página. */
    private static boolean ehClube(String id) {
        if (id == null) return false;
        if (id.startsWith("sel-") || id.startsWith("kings-")) return true;
        var db = Imagens.banco();
        return db != null && (db.clubes.containsKey(id) || db.kingsTimes.containsKey(id));
    }

    /** Troféu/logo da competição que abre a página dela. */
    public static Node trofeu(String competicaoId, String ambito, String pais, double altura) {
        Node n = Imagens.trofeu(competicaoId, ambito, pais, altura);
        if (aoAbrirCompeticao == null || competicaoId == null) return n;
        n.setCursor(javafx.scene.Cursor.HAND);
        n.setOnMouseClicked(e -> {
            Consumer<String> c = aoAbrirCompeticao;
            if (c != null && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                c.accept(competicaoId);
                e.consume();
            }
        });
        return n;
    }

    /** Nome de competição que abre a página dela. */
    public static Label nomeCompeticao(String competicaoId, String nome, String... classes) {
        if (aoAbrirCompeticao == null || competicaoId == null) return rotulo(nome, classes);
        return link(nome, () -> {
            Consumer<String> k = aoAbrirCompeticao;
            if (k != null) k.accept(competicaoId);
        }, classes);
    }

    /** Nome de clube que abre a ficha (ou texto simples se não houver navegação). */
    public static Label nomeClube(String id, String nome, String... classes) {
        Consumer<String> c = aoAbrirClube;
        if (c == null || !ehClube(id)) return rotulo(nome, classes);
        return link(nome, () -> {
            Consumer<String> k = aoAbrirClube;
            if (k != null) k.accept(id);
        }, classes);
    }

    /** Nome de jogador que abre a ficha. */
    public static Label nomeJogador(Integer id, String nome, String... classes) {
        if (aoAbrirJogador == null || id == null) return rotulo(nome, classes);
        return link(nome, () -> {
            Consumer<Integer> k = aoAbrirJogador;
            if (k != null) k.accept(id);
        }, classes);
    }

    /** Abre o perfil de um atleta da Kings que não está no banco do mundo: (id do time, nome). */
    public static volatile java.util.function.BiConsumer<String, String> aoAbrirAtletaKings;

    /**
     * Nome de atleta da Kings que abre o perfil: o do jogador do mundo quando ele existe; senão o perfil
     * do atleta da Kings (presidente, elenco real), achado pelo time e pelo nome.
     */
    public static Label nomeAtletaKings(Integer jogadorId, String timeId, String nome, String... classes) {
        if (jogadorId != null && aoAbrirJogador != null) return nomeJogador(jogadorId, nome, classes);
        if (aoAbrirAtletaKings == null || timeId == null || nome == null) return rotulo(nome, classes);
        return link(nome, () -> {
            var k = aoAbrirAtletaKings;
            if (k != null) k.accept(timeId, nome);
        }, classes);
    }

    /** Nome de técnico da IA que abre a ficha. */
    public static Label nomeTecnico(Integer id, String nome, String... classes) {
        if (aoAbrirTecnico == null || id == null) return rotulo(nome, classes);
        return link(nome, () -> {
            Consumer<Integer> k = aoAbrirTecnico;
            if (k != null) k.accept(id);
        }, classes);
    }

    public static Node escudo(String id, double altura) {
        Node n = Imagens.escudo(id, altura);
        clicavel(n, id);
        return n;
    }

    /** Escudo + nome numa linha (clubes e seleções). */
    public static HBox clube(Mundo m, String id, double altura, String... classesNome) {
        Label n = nomeClube(id, m == null ? id : m.nomeParticipante(id), classesNome);
        n.setMinWidth(0);
        Node e = Imagens.escudo(id, altura);
        clicavel(e, id);
        return linha(8, e, n);
    }

    private static void clicavel(Node n, String id) {
        if (aoAbrirClube == null || !ehClube(id)) return;
        n.setCursor(javafx.scene.Cursor.HAND);
        n.setOnMouseClicked(e -> {
            Consumer<String> c = aoAbrirClube;
            if (c != null && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                c.accept(id);
                e.consume();
            }
        });
    }

    /** Reputação (0–100) em cinco estrelas, como no Brasfoot. */
    public static HBox estrelas(double reputacao, double tamanho) {
        HBox h = new HBox(1);
        h.setAlignment(Pos.CENTER_LEFT);
        double n = Math.max(0, Math.min(5, reputacao / 20.0));
        for (int i = 0; i < 5; i++) {
            double cheio = n - i;
            Label l = rotulo("★", "estrela");
            if (cheio < 0.5) l.getStyleClass().add("estrela-vazia");
            l.setStyle("-fx-font-size: " + tamanho + "px;");
            h.getChildren().add(l);
        }
        h.setMinWidth(Region.USE_PREF_SIZE);
        return h;
    }

    /**
     * Cabeçalho de tela inteira (fora da central): fundo escuro (ou nas cores do clube), um
     * ícone opcional à esquerda, título, linha de apoio e ações à direita, com a faixa embaixo.
     */
    public static VBox cabecalhoTela(Node icone, String titulo, String sub, String cor1, String cor2, Node... acoes) {
        HBox topo = new HBox(14);
        topo.getStyleClass().add("cabecalho");
        topo.setAlignment(Pos.CENTER_LEFT);
        if (icone != null) topo.getChildren().add(icone);
        VBox id = new VBox(1, rotulo(titulo, "cab-nome"));
        if (sub != null && !sub.isBlank()) id.getChildren().add(rotulo(sub, "cab-meta"));
        id.setMinWidth(0);
        HBox.setHgrow(id, Priority.ALWAYS);
        topo.getChildren().add(id);
        topo.getChildren().addAll(acoes);
        Region faixa = new Region();
        faixa.getStyleClass().add("faixa-clube");
        if (cor1 != null || cor2 != null) {
            topo.setStyle(Tema.estiloCabecalho(cor1, cor2));
            faixa.setStyle("-fx-background-color: " + Tema.hex(Tema.faixaClube(cor1, cor2)) + ";");
        } else {
            topo.getStyleClass().add("neutro");
            faixa.getStyleClass().add("faixa-gramado");
        }
        return new VBox(topo, faixa);
    }

    /** Bloco "valor grande + rótulo" para cabeçalhos escuros. */
    public static VBox blocoCabecalho(String valor, String rotulo) {
        VBox v = new VBox(0, rotulo(valor, "cab-valor"), rotulo(rotulo, "cab-rotulo"));
        v.setAlignment(Pos.CENTER_LEFT);
        v.setMinWidth(Region.USE_PREF_SIZE);
        return v;
    }

    public static Region separadorCabecalho() {
        Region r = new Region();
        r.getStyleClass().add("cab-sep");
        r.setMinHeight(32);
        return r;
    }

    /** Barra inferior de ações (partida, fim de temporada, sem clube). */
    public static HBox rodape(Node... filhos) {
        HBox h = new HBox(10, filhos);
        h.getStyleClass().add("rodape-partida");
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    /** Botão com a tecla de atalho ao lado do texto. */
    public static Button botaoTecla(String texto, String tecla, Runnable acao, String... classes) {
        Button b = botao(texto, acao, classes);
        // a tecla continua funcionando (tratada pela tela); o selo visual foi tirado a requisito
        b.setFocusTraversable(false);
        return b;
    }

    /** Comparação de um número entre dois times (barra dividida, mandante à esquerda). */
    public static VBox comparacao(String rotulo, double a, double b, String textoA, String textoB) {
        Label la = rotulo(textoA, "comp-valor"), lb = rotulo(textoB, "comp-valor");
        Label lr = rotulo(rotulo, "comp-rotulo");
        HBox topo = linha(6, la, espaco(), lr, espaco(), lb);
        double total = a + b;
        double fa = total <= 0 ? 0.5 : a / total;
        Region ra = new Region(), rb = new Region();
        ra.getStyleClass().add("estat-barra-a");
        rb.getStyleClass().add("estat-barra-b");
        ra.setMinHeight(6);
        rb.setMinHeight(6);
        HBox barra = new HBox(2, ra, rb);
        barra.widthProperty().addListener((o, x, w) -> {
            double util = Math.max(0, w.doubleValue() - 2);
            ra.setPrefWidth(util * fa);
            rb.setPrefWidth(util * (1 - fa));
        });
        ra.setMinWidth(0);
        rb.setMinWidth(0);
        return new VBox(4, topo, barra);
    }

    // ------------------------------------------------------------------ tabelas
    // ------------------------------------------------------------------ tabelas
    public static <T> TableView<T> tabela() {
        TableView<T> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(rotulo("Nada para mostrar.", "texto-suave"));
        t.setFixedCellSize(30);
        return t;
    }

    public static <T, V> TableColumn<T, V> col(TableView<T> t, String titulo, double largura, Function<T, V> valor) {
        TableColumn<T, V> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(valor.apply(d.getValue())));
        c.setPrefWidth(largura);
        if (largura > 0 && largura < 70) {
            c.setMinWidth(largura);
            c.setMaxWidth(largura + 20);
        }
        t.getColumns().add(c);
        return c;
    }

    /** Coluna numérica centralizada no tipo do placar. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T, V extends Comparable> TableColumn<T, V> colNum(TableView<T> t, String titulo, double largura, Function<T, V> valor) {
        TableColumn<T, V> c = col(t, titulo, largura, valor);
        c.getStyleClass().add("centro");
        c.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        c.setCellFactory(x -> new TableCell<>() {
            {
                getStyleClass().add("num");
            }

            @Override
            protected void updateItem(V v, boolean vazio) {
                super.updateItem(v, vazio);
                setText(vazio || v == null ? null : String.valueOf(v));
            }
        });
        return c;
    }

    /** Coluna com nós gráficos montados por linha. */
    public static <T> TableColumn<T, T> colNo(TableView<T> t, String titulo, double largura, Function<T, Node> no) {
        TableColumn<T, T> c = col(t, titulo, largura, x -> x);
        c.setSortable(false);
        c.setCellFactory(x -> new TableCell<>() {
            @Override
            protected void updateItem(T v, boolean vazio) {
                super.updateItem(v, vazio);
                setText(null);
                setGraphic(vazio || v == null ? null : no.apply(v));
            }
        });
        return c;
    }

    public static <T> TableColumn<T, Integer> colForca(TableView<T> t, String titulo, Function<T, Integer> valor) {
        TableColumn<T, Integer> c = col(t, titulo, 54, valor);
        c.getStyleClass().add("centro");
        c.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        c.setCellFactory(x -> new TableCell<>() {
            {
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Integer v, boolean vazio) {
                super.updateItem(v, vazio);
                setText(null);
                setGraphic(vazio || v == null ? null : forca(v));
            }
        });
        return c;
    }

    public static <T> TableColumn<T, Posicao> colPos(TableView<T> t, Function<T, Posicao> valor) {
        TableColumn<T, Posicao> c = col(t, "Pos", 50, valor);
        c.getStyleClass().add("centro");
        c.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        c.setCellFactory(x -> new TableCell<>() {
            {
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Posicao p, boolean vazio) {
                super.updateItem(p, vazio);
                setText(null);
                setGraphic(vazio || p == null ? null : pos(p));
            }
        });
        return c;
    }

    /** Coluna com o escudo e o nome do clube (ou seleção). */
    public static <T> TableColumn<T, String> colClube(TableView<T> t, String titulo, double largura, Mundo m, Function<T, String> clubeId) {
        TableColumn<T, String> c = col(t, titulo, largura, clubeId);
        c.setCellFactory(x -> new TableCell<>() {
            @Override
            protected void updateItem(String id, boolean vazio) {
                super.updateItem(id, vazio);
                if (vazio || id == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label n = nomeClube(id, m == null ? id : m.nomeParticipante(id));
                n.setMinWidth(0);
                n.textFillProperty().bind(textFillProperty());
                setGraphic(linha(8, escudo(id, 20), n));
                setText(null);
            }
        });
        c.setComparator(Comparator.comparing(id -> m == null || id == null ? String.valueOf(id) : m.nomeParticipante(id)));
        return c;
    }

    public static <T> TableColumn<T, Long> colDinheiro(TableView<T> t, String titulo, double largura, Function<T, Long> valor) {
        TableColumn<T, Long> c = col(t, titulo, largura, valor);
        c.getStyleClass().add("direita");
        c.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        c.setCellFactory(x -> new TableCell<>() {
            {
                getStyleClass().add("direita");
            }

            @Override
            protected void updateItem(Long v, boolean vazio) {
                super.updateItem(v, vazio);
                setText(vazio || v == null ? null : v == 0 ? "—" : Textos.dinheiro(v));
            }
        });
        return c;
    }

    /** Aplica classes de estilo às linhas conforme a regra. */
    public static <T> void estiloLinhas(TableView<T> t, BiConsumer<T, TableRow<T>> regra) {
        t.setRowFactory(x -> new TableRow<>() {
            @Override
            protected void updateItem(T item, boolean vazio) {
                super.updateItem(item, vazio);
                getStyleClass().removeAll("meu-clube", "indisponivel");
                if (!vazio && item != null) regra.accept(item, this);
            }
        });
    }

    /** Altura exata para {@code linhas} linhas (tabelas pequenas, sem rolagem). */
    public static <T> void alturaPorLinhas(TableView<T> t, int linhas) {
        double h = 32 + 30 * Math.max(1, linhas) + 4;
        t.setPrefHeight(h);
        t.setMinHeight(h);
        t.setMaxHeight(h);
    }

    // ------------------------------------------------------------------ diálogos (dentro da janela)
    /** Camada da janela onde os diálogos aparecem (fundo escurecido); definida pelo App. */
    public static StackPane camadaModal;

    /**
     * Mostra um diálogo DENTRO da janela principal (nada de janela nova do sistema) e espera a
     * resposta num laço de eventos aninhado. {@code botoes}: textos; o primeiro é o principal.
     * Retorna o índice do botão (Esc = último, que deve ser o de cancelar).
     */
    public static int modal(String titulo, Node conteudo, String... botoes) {
        return modalCaixa(titulo, conteudo, null, null, botoes);
    }

    /**
     * Variante de {@link #modal}: {@code classeCaixa} entra na caixa (por exemplo, sem margem para um
     * cabeçalho de ponta a ponta) e {@code rodape} fica à esquerda dos botões (resumo do negócio).
     */
    public static int modalCaixa(String titulo, Node conteudo, String classeCaixa, Node rodape, String... botoes) {
        if (camadaModal == null) return botoes.length - 1;
        int[] escolha = {botoes.length - 1};
        Object chave = new Object();
        StackPane fundo = new StackPane();
        Runnable[] fechar = new Runnable[1];
        VBox caixa = caixaModal(titulo, conteudo, classeCaixa, rodape, k -> {
            escolha[0] = k;
            fechar[0].run();
        }, botoes);
        fundo.getChildren().add(caixa);
        fundo.getStyleClass().add("sobreposicao");
        fechar[0] = () -> {
            camadaModal.getChildren().remove(fundo);
            camadaModal.setVisible(!camadaModal.getChildren().isEmpty());
            javafx.application.Platform.exitNestedEventLoop(chave, null);
        };
        fundo.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                escolha[0] = botoes.length - 1;
                fechar[0].run();
                e.consume();
            }
        });
        camadaModal.getChildren().add(fundo);
        camadaModal.setVisible(true);
        javafx.application.Platform.runLater(caixa::requestFocus);
        javafx.application.Platform.enterNestedEventLoop(chave);
        return escolha[0];
    }

    /** Monta a caixa do diálogo (título, conteúdo, rodapé e botões) sem mostrar; usada também pelas ferramentas de PNG. */
    public static VBox caixaModal(String titulo, Node conteudo, String classeCaixa, Node rodape, java.util.function.IntConsumer aoEscolher, String... botoes) {
        VBox caixa = new VBox(14);
        caixa.getStyleClass().add("caixa-sobreposta");
        if (classeCaixa != null) caixa.getStyleClass().add(classeCaixa);
        caixa.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        caixa.setMinWidth(420);
        if (titulo != null) caixa.getChildren().add(rotulo(titulo, "titulo-tela"));
        if (conteudo != null) caixa.getChildren().add(conteudo);
        HBox acoes = linha(8);
        if (rodape != null) {
            HBox.setHgrow(rodape, Priority.ALWAYS);
            acoes.getChildren().add(rodape);
        } else acoes.getChildren().add(espaco());
        acoes.getStyleClass().add("acoes-modal");
        for (int i = botoes.length - 1; i >= 0; i--) {
            int k = i;
            Button bt = botao(botoes[i], () -> aoEscolher.accept(k), i == 0 ? "primario" : "");
            bt.setMinWidth(Region.USE_PREF_SIZE);
            acoes.getChildren().add(bt);
            if (i == 0) bt.setDefaultButton(true);
            if (i == botoes.length - 1 && botoes.length > 1) bt.setCancelButton(true);
        }
        caixa.getChildren().add(acoes);
        return caixa;
    }

    public static boolean confirmar(String titulo, String texto) {
        if (semDialogos) return true;
        Label l = paragrafo(texto, "texto-medio");
        l.setMaxWidth(460);
        return modal(titulo, l, "Confirmar", "Cancelar") == 0;
    }

    public static void informar(String titulo, String texto) {
        if (semDialogos) {
            System.out.println("[aviso] " + titulo + ": " + texto);
            return;
        }
        Label l = paragrafo(texto, "texto-medio");
        l.setMaxWidth(460);
        modal(titulo, l, "OK");
    }

    public static Optional<String> perguntar(String titulo, String texto, String padrao) {
        if (semDialogos) return Optional.ofNullable(padrao);
        javafx.scene.control.TextField tf = new javafx.scene.control.TextField(padrao);
        tf.setPrefWidth(320);
        VBox v = new VBox(8, rotulo(texto, "texto-medio"), tf);
        javafx.application.Platform.runLater(tf::requestFocus);
        int r = modal(titulo, v, "OK", "Cancelar");
        return r == 0 ? Optional.ofNullable(tf.getText()) : Optional.empty();
    }

    /**
     * Mostra o conteúdo de um {@code Dialog} montado pela tela como diálogo dentro da janela
     * (mesmos botões e o mesmo conversor de resultado), em vez de abrir uma janela do sistema.
     */
    public static <R> Optional<R> dialogo(Dialog<R> d) {
        if (semDialogos) return Optional.empty();
        var tipos = new java.util.ArrayList<>(d.getDialogPane().getButtonTypes());
        // principal primeiro, cancelar por último
        tipos.sort(java.util.Comparator.comparingInt(t -> t.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.OK_DONE ? 0
                : t.getButtonData().isCancelButton() ? 2 : 1));
        String[] nomes = new String[tipos.size()];
        for (int i = 0; i < tipos.size(); i++) {
            Node bn = d.getDialogPane().lookupButton(tipos.get(i));
            String txt = bn instanceof Button b && b.getText() != null ? b.getText() : tipos.get(i).getText();
            nomes[i] = "OK".equals(txt) ? "Confirmar" : "Cancel".equals(txt) ? "Cancelar" : txt;
        }
        Node conteudo = d.getDialogPane().getContent();
        d.getDialogPane().setContent(null);
        String titulo = d.getHeaderText() != null ? d.getHeaderText() : d.getTitle();
        int r = modal(titulo, conteudo, nomes.length == 0 ? new String[] {"OK"} : nomes);
        if (tipos.isEmpty()) return Optional.empty();
        ButtonType bt = tipos.get(r);
        // sem conversor, o Dialog<ButtonType> do JavaFX devolve o próprio botão: antes devolvia vazio e
        // "Confirmar" não fazia nada (proposta, renovação, amistoso, delegar...)
        if (d.getResultConverter() == null) {
            @SuppressWarnings("unchecked") R res = (R) bt;
            return Optional.of(res);
        }
        return Optional.ofNullable(d.getResultConverter().call(bt));
    }

    public static Insets margem(double v) {
        return new Insets(v);
    }
}
