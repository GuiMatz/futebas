package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Clube;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.Tela;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Tema;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.GuardaJogar;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A central do técnico: cabeçalho com as cores do clube, navegação à esquerda com o JOGAR
 * sempre à vista e o painel escolhido no centro. Tudo a uma tecla de distância.
 */
public final class Hub implements Tela {

    /** Um painel da central. */
    public interface Painel {
        Node construir();
    }

    private record Entrada(String id, String grupo, String nome, String atalho, KeyCode tecla, Function<Hub, Painel> fabrica) {}

    private final Sessao sessao;
    private final BorderPane raiz = new BorderPane();
    private final StackPane centro = new StackPane();
    private final HBox topo = new HBox();
    private final Region faixa = new Region();
    private final VBox nav = new VBox();
    private final Map<String, Entrada> entradas = new LinkedHashMap<>();
    private final Map<String, Button> botoes = new LinkedHashMap<>();
    private final Map<String, Label> grupos = new LinkedHashMap<>();
    /**
     * Painéis que dependem de um clube: somem na carreira da Kings (o elenco 7x7,
     * o draft e a carta secreta ficam no painel Kings League; o Calendário mostra os jogos da Kings).
     */
    private static final java.util.Set<String> DO_CLUBE = java.util.Set.of("base", "transferencias"); // o treino também vale na Kings (12/11/2026)
    private final Button jogar = new Button();
    private String atual = "jogo";
    private final TextField busca = new TextField();
    /** Resultados da pesquisa: painel flutuante dentro da central (não é janela separada). */
    private final StackPane popupBusca = new StackPane();
    private final ListView<Resultado> resultados = new ListView<>();
    private boolean fichaAberta;
    private long mostradoEm;
    /** O que estava na tela antes de cada ficha aberta (Voltar/Esc desfaz um passo). */
    private final java.util.Deque<Node> historico = new java.util.ArrayDeque<>();

    /** Um resultado da pesquisa. */
    private record Resultado(String tipo, String nome, String detalhe, Node icone, Runnable abrir) {}

    public Hub(Sessao sessao) {
        this.sessao = sessao;
        preaquecer(sessao);
        add("jogo", "Hoje", "Próximo jogo", "F1", KeyCode.F1, PainelProximoJogo::new);
        add("caixa", "Hoje", "Caixa de entrada", "F9", KeyCode.F9, PainelCaixa::new);
        add("elenco", "Time", "Elenco", "F3", KeyCode.F3, PainelElenco::new);
        add("escalacao", "Time", "Escalação e tática", "F4", KeyCode.F4, PainelEscalacao::new);
        add("treino", "Time", "Treino", "", null, PainelTreino::new);
        add("base", "Time", "Base e Sub-20", "", null, PainelBase::new);
        // duas entradas no menu (regra do jogo): as competições do seu time e as do resto do mundo
        add("tabelas", "Competições", "Campeonatos", "F5", KeyCode.F5, h -> new PainelTabelas(h.sessao(), true).lista(PainelTabelas.ABA_MEUS));
        add("outros", "Competições", "Outros Campeonatos", "", null, h -> new PainelTabelas(h.sessao(), true).lista(PainelTabelas.ABA_OUTROS));
        add("calendario", "Competições", "Calendário", "F6", KeyCode.F6, PainelCalendario::new);
        add("selecoes", "Competições", "Seleções", "F10", KeyCode.F10, h -> new PainelSelecoes(h.sessao()));
        add("mundo", "Competições", "Mundo", "", null, h -> new PainelMundo(h.sessao()));
        add("premios", "Competições", "Prêmios", "", null, h -> new PainelPremios(h.sessao()));
        // aba Lendas removida a requisito (a classe PainelLendas fica, sem entrada no menu)
        //         add("lendas", "Competições", "Lendas", "", null, h -> new PainelLendas(h.sessao()));
        add("kings", "Competições", "Kings League", "F12", KeyCode.F12, PainelKings::new);
        add("mercado", "Gestão", "Mercado", "F7", KeyCode.F7, PainelMercado::new);
        add("transferencias", "Gestão", "Transferências", "", null, PainelTransferencias::new);
        add("financas", "Gestão", "Finanças", "F8", KeyCode.F8, PainelFinancas::new);
        add("clube", "Gestão", "Clube e troféus", "", null, PainelClube::new);
        add("vagas", "Carreira", "Vagas", "", null, PainelVagas::new);

        nav.getStyleClass().add("nav");
        String grupo = null;
        for (Entrada e : entradas.values()) {
            if (!e.grupo().equals(grupo)) {
                grupo = e.grupo();
                Label gl = Ui.rotulo(grupo, "nav-grupo");
                grupos.put(grupo, gl);
                nav.getChildren().add(gl);
            }
            Button b = new Button();
            b.getStyleClass().setAll("nav-item");
            b.setFocusTraversable(false);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(x -> selecionar(e.id()));
            botoes.put(e.id(), b);
            preencherBotao(e, 0);
            nav.getChildren().add(b);
        }
        jogar.getStyleClass().setAll("jogar");
        jogar.setFocusTraversable(false);
        jogar.setOnAction(e -> App.atual().jogar());
        jogar.setMaxWidth(Double.MAX_VALUE);
        nav.getChildren().add(Ui.espaco());

        topo.getStyleClass().add("cabecalho");
        topo.setAlignment(Pos.CENTER_LEFT);
        faixa.getStyleClass().add("faixa-clube");
        raiz.setTop(new VBox(topo, faixa));
        montarBusca();
        // menu lateral rolável: em 1366×768 os itens passam da altura e, sem rolagem, o menu empurrava o
        // painel do centro para fora da janela (todo painel perdia ~80 px embaixo)
        javafx.scene.control.ScrollPane navRolo = new javafx.scene.control.ScrollPane(nav);
        navRolo.setFitToWidth(true);
        navRolo.setFitToHeight(true);
        navRolo.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        navRolo.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        navRolo.getStyleClass().add("nav-rolo");
        navRolo.setMinHeight(0);
        raiz.setLeft(navRolo);
        raiz.setCenter(centro);
    }

    private void add(String id, String grupo, String nome, String atalho, KeyCode k, Function<Hub, Painel> f) {
        entradas.put(id, new Entrada(id, grupo, nome, atalho, k, f));
    }

    private void preencherBotao(Entrada e, int contador) {
        Button b = botoes.get(e.id());
        // carreira da Kings: os mesmos nomes e os mesmos painéis do futebol (requisito: "é só um futebol com 7 pessoas")
        String nome = e.nome();
        Label texto = Ui.rotulo(nome);
        HBox g = Ui.linha(10, icone(e.id()), texto, Ui.espaco());
        g.setMaxWidth(Double.MAX_VALUE);
        texto.getStyleClass().add("nav-texto");
        if (contador > 0) {
            Label ct = Ui.rotulo(String.valueOf(contador), "contador");
            ct.setMinWidth(Region.USE_PREF_SIZE);
            g.getChildren().add(ct);
        }
        texto.setMinWidth(0);
        b.setGraphic(g);
        b.setText(null);
        g.prefWidthProperty().bind(b.widthProperty().subtract(20));
        // o texto herda a cor do botão (ativo/hover)
        texto.textFillProperty().bind(b.textFillProperty());
        texto.fontProperty().bind(b.fontProperty());
    }

    /** Ícones de traço próprios (24×24, desenhados para o FUTÉBAS). */
    private static final java.util.Map<String, String> ICONES = java.util.Map.ofEntries(
            java.util.Map.entry("jogo", "M3 11 L12 3 L21 11 M5 9.5 V21 H10 V15 H14 V21 H19 V9.5"),
            java.util.Map.entry("caixa", "M3 6 H21 V18 H3 Z M3 6 L12 13 L21 6"),
            java.util.Map.entry("elenco", "M8 3 L3 6.5 L5.5 10.5 L8 9.2 V21 H16 V9.2 L18.5 10.5 L21 6.5 L16 3 C15 5 9 5 8 3 Z"),
            java.util.Map.entry("escalacao", "M4 3 H20 V21 H4 Z M4 12 H20 M9 3 V6 H15 V3 M9 21 V18 H15 V21"),
            java.util.Map.entry("treino", "M12 3 L18.5 20 H5.5 Z M8 14 H16 M9.5 9 H14.5"),
            java.util.Map.entry("base", "M12 21 V11 M12 11 C12 6.5 8 5 4 5 C4 9 7 11 12 11 M12 14 C12 10 15.5 8.5 20 8.5 C20 12.5 16.5 14 12 14"),
            java.util.Map.entry("tabelas", "M4 4 H20 V20 H4 Z M4 9 H20 M4 14.5 H20 M9 4 V20"),
            java.util.Map.entry("outros", "M12 3 A9 9 0 1 0 12.01 3 Z M3 12 H21 M12 3 C8 7 8 17 12 21 M12 3 C16 7 16 17 12 21"),
            java.util.Map.entry("calendario", "M4 6 H20 V20 H4 Z M4 10.5 H20 M8 3 V8 M16 3 V8"),
            java.util.Map.entry("selecoes", "M5 21 V3.5 M5 4 H18 L15.5 8.5 L18 13 H5"),
            java.util.Map.entry("mundo", "M12 3 A9 9 0 1 0 12.01 3 Z M3 12 H21 M12 3 C7.5 7.5 7.5 16.5 12 21 C16.5 16.5 16.5 7.5 12 3"),
            java.util.Map.entry("premios", "M7.5 4 H16.5 V9 C16.5 14 7.5 14 7.5 9 Z M7.5 6 H4.5 C4.5 9 6 10.5 8 10.5 M16.5 6 H19.5 C19.5 9 18 10.5 16 10.5 M12 13 V17 M8 20 H16 V17 H8 Z"),
            java.util.Map.entry("lendas", "M12 3 L14.6 8.6 L20.6 9.2 L16 13.2 L17.4 19.2 L12 16 L6.6 19.2 L8 13.2 L3.4 9.2 L9.4 8.6 Z"),
            java.util.Map.entry("kings", "M4 18 L3 7 L8 11 L12 4.5 L16 11 L21 7 L20 18 Z M4 21 H20"),
            java.util.Map.entry("transferencias", "M7 4 H17 V20 H7 Z M10 8 H14 M10 12 H14 M10 16 H12"),
            java.util.Map.entry("mercado", "M4 8 H19 M15 4 L19 8 L15 12 M20 16 H5 M9 12 L5 16 L9 20"),
            java.util.Map.entry("financas", "M12 3 A9 9 0 1 0 12.01 3 Z M15 8.8 C14 7 9 7 9 9.6 C9 12.3 15 11.6 15 14.5 C15 17 10 17 9 15.2 M12 5.8 V18.2"),
            java.util.Map.entry("clube", "M12 3 L19 6 V12 C19 16 16 19 12 21 C8 19 5 16 5 12 V6 Z"),
            java.util.Map.entry("vagas", "M4 8 H20 V20 H4 Z M9 8 V5 H15 V8 M4 13 H20"));

    private static Node icone(String id) {
        javafx.scene.shape.SVGPath sp = new javafx.scene.shape.SVGPath();
        sp.setContent(ICONES.getOrDefault(id, "M5 5 H19 V19 H5 Z"));
        sp.getStyleClass().add("nav-icone");
        sp.setScaleX(0.72);
        sp.setScaleY(0.72);
        javafx.scene.layout.StackPane c = new javafx.scene.layout.StackPane(new javafx.scene.Group(sp));
        c.setMinSize(18, 18);
        c.setPrefSize(18, 18);
        c.setMaxSize(18, 18);
        return c;
    }

    // ------------------------------------------------------------------ pesquisa e fichas
    private void montarBusca() {
        busca.setPromptText("Pesquisar clubes, competições, técnicos, jogadores");
        busca.getStyleClass().add("busca");
        busca.setPrefWidth(300);
        busca.setMinWidth(160);
        busca.setFocusTraversable(false);
        resultados.setPrefSize(440, 380);
        resultados.getStyleClass().add("resultados-busca");
        resultados.setCellFactory(x -> new ListCell<>() {
            @Override
            protected void updateItem(Resultado r, boolean vazio) {
                super.updateItem(r, vazio);
                setText(null);
                if (vazio || r == null) {
                    setGraphic(null);
                    return;
                }
                Label n = Ui.rotulo(r.nome(), "texto-forte");
                n.setMinWidth(0);
                VBox t = new VBox(0, n, Ui.rotulo(r.detalhe(), "texto-pequeno"));
                t.setMinWidth(0);
                HBox.setHgrow(t, Priority.ALWAYS);
                HBox h = Ui.linha(10, r.icone(), t, Ui.etiqueta(r.tipo(), null));
                h.setMaxWidth(405);
                h.setPrefWidth(405);
                setGraphic(h);
            }
        });
        resultados.setOnMouseClicked(e -> abrirResultado());
        resultados.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) abrirResultado();
            else if (e.getCode() == KeyCode.ESCAPE) popupBusca.setVisible(false);
        });
        popupBusca.getChildren().add(resultados);
        popupBusca.setVisible(false);
        popupBusca.setManaged(false);
        popupBusca.getStyleClass().add("busca-flutuante");
        busca.focusedProperty().addListener((o, a, b) -> {
            if (!b && !resultados.isFocused()) javafx.application.Platform.runLater(() -> {
                if (!resultados.isFocused() && !busca.isFocused()) popupBusca.setVisible(false);
            });
        });
        busca.textProperty().addListener((o, a, b) -> pesquisar(b));
        busca.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN && popupBusca.isVisible()) {
                resultados.requestFocus();
                resultados.getSelectionModel().select(0);
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER && !resultados.getItems().isEmpty()) {
                resultados.getSelectionModel().select(0);
                abrirResultado();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                busca.clear();
                popupBusca.setVisible(false);
                raiz.requestFocus();
                e.consume();
            }
        });
    }

    private static String norm(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.trim().toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Pesquisa (também usada pelas capturas). */
    public void pesquisar(String q) {
        String n = norm(q);
        if (n.length() < 2) {
            popupBusca.setVisible(false);
            return;
        }
        Jogo j = sessao.jogo;
        var m = j.mundo();
        java.util.List<Resultado> l = new java.util.ArrayList<>();
        for (Jogo.ResultadoBusca rb : j.buscar(q, 40)) {
            switch (rb.tipo()) {
                case "clube" -> l.add(new Resultado("Clube", rb.nome(), rb.detalhe(), Imagens.escudo(rb.id(), 22), () -> abrirFichaClube(rb.id())));
                case "competicao" -> {
                    Torneio t = j.competicaoQualquer(rb.id());
                    if (t != null) l.add(new Resultado("Competição", Textos.curto(rb.nome()), rb.detalhe(), Imagens.trofeu(t.id, t.ambito, t.pais, 22),
                            () -> abrirCompeticao(t.id)));
                    else if (j.competicaoDoBanco(rb.id()) != null) {
                        var dc = j.competicaoDoBanco(rb.id());
                        l.add(new Resultado("Competição", Textos.curto(rb.nome()), rb.detalhe(), Imagens.trofeu(dc.id(), dc.ambito(), dc.pais(), 22),
                                () -> abrirCompeticao(dc.id())));
                    }
                }
                case "kings" -> l.add(new Resultado("Kings League", rb.nome(), rb.detalhe(), Imagens.escudo(rb.id(), 22), () -> abrirParticipante(rb.id())));
                case "selecao" -> l.add(new Resultado("Seleção", rb.nome(), rb.detalhe(), Imagens.bandeira(rb.id(), 14),
                        () -> abrirFicha(PaginasEquipe.selecao(this, rb.id(), this::fecharFicha))));
                case "tecnico" -> {
                    if ("humano".equals(rb.id())) {
                        l.add(new Resultado("Técnico", rb.nome(), rb.detalhe(), new Region(), () -> abrirFicha(Fichas.tecnico(this, this::fecharFicha))));
                    } else {
                        try {
                            var t = j.treinador(Integer.parseInt(rb.id()));
                            if (t != null) l.add(new Resultado("Técnico", rb.nome(), rb.detalhe(), Imagens.bandeira(t.pais, 14),
                                    () -> abrirFicha(Fichas.treinador(this, t, this::fecharFicha))));
                        } catch (NumberFormatException e) {
                            // id inesperado: ignora
                        }
                    }
                }
                case "jogador" -> {
                    try {
                        var x = j.jogador(Integer.parseInt(rb.id()));
                        if (x != null) l.add(new Resultado("Jogador", rb.nome(), rb.detalhe(), Imagens.bandeira(x.pais, 14),
                                () -> abrirFicha(fichaJogador(x))));
                    } catch (NumberFormatException e) {
                        // id inesperado: ignora
                    }
                }
                default -> { }
            }
        }
        resultados.getItems().setAll(l);
        resultados.setPlaceholder(Ui.rotulo("Nada encontrado.", "texto-suave"));
        if (busca.getScene() != null) {
            if (!centro.getChildren().contains(popupBusca)) centro.getChildren().add(popupBusca);
            var b = busca.localToScene(busca.getBoundsInLocal());
            var c = centro.localToScene(centro.getBoundsInLocal());
            popupBusca.resizeRelocate(Math.max(8, b.getMinX() - c.getMinX()), 6, 440, 380);
            popupBusca.setVisible(true);
            popupBusca.toFront();
        }
    }

    public void abrirFichaJogador(br.futebas.mundo.Jogador x) {
        abrirFicha(fichaJogador(x));
    }

    private Node fichaJogador(br.futebas.mundo.Jogador x) {
        Node pg = FichaJogador.pagina(sessao.jogo, x, this::fecharFicha);
        // requisito: da página do jogador dá para mandar proposta (o mesmo diálogo do Mercado)
        br.futebas.mundo.Clube meu = clube();
        if (meu != null && !meu.id.equals(x.clubeId) && x.status != br.futebas.mundo.StatusJogador.APOSENTADO
                && !x.amador() && pg instanceof javafx.scene.layout.Pane raiz) {
            javafx.scene.layout.HBox acoes = Ui.linha(8, Ui.botao("Fazer proposta", () -> PainelMercado.propor(this, x), "primario"));
            if (sessao.jogo.taxaEmprestimo(x.id) > 0) acoes.getChildren().add(Ui.botao("Pedir emprestado", () -> PainelMercado.pedirEmprestado(this, x)));
            acoes.setPadding(new javafx.geometry.Insets(8, 16, 0, 16));
            raiz.getChildren().add(1, acoes);
        }
        return pg;
    }

    private void abrirResultado() {
        Resultado r = resultados.getSelectionModel().getSelectedItem();
        if (r == null) return;
        popupBusca.setVisible(false);
        busca.clear();
        r.abrir().run();
    }

    /** Mostra uma ficha no lugar do painel atual (Esc ou Voltar retornam). */
    public void abrirFicha(Node n) {
        popupBusca.setVisible(false);
        if (!centro.getChildren().isEmpty()) historico.push(centro.getChildren().get(0));
        if (historico.size() > 30) historico.removeLast();
        fichaAberta = true;
        centro.getChildren().setAll(n);
        raiz.requestFocus();
    }

    /** A escalação antes do jogo: campo, banco e formação, com Voltar (Esc) e JOGAR. */
    public void abrirEscalacaoAntesDoJogo() {
        abrirFicha(new PainelEscalacao(this, this::fecharFicha, () -> App.atual().jogar()).construir());
    }

    /** Pré-jogo do próximo jogo (Esc volta). */
    public void abrirPreJogo(Partida p) {
        abrirFicha(PainelPreJogo.construir(this, p, this::fecharFicha));
    }

    /** Perfil de um atleta da Kings (requisito: clicar no nome abre o perfil, como no futebol). */
    public void abrirAtletaKings(String timeId, String nome) {
        for (br.futebas.kings.LigaKings lg : sessao.jogo.mundo().kings) {
            for (br.futebas.kings.LigaKings.TimeKings t : lg.times) {
                if (!t.id.equals(timeId)) continue;
                for (br.futebas.kings.LigaKings.AtletaKings a : t.elenco) {
                    if (!nome.equals(a.nome)) continue;
                    if (a.jogadorId != null && sessao.jogo.jogador(a.jogadorId) != null) {
                        abrirFichaJogadorPorId(a.jogadorId);
                        return;
                    }
                    javafx.scene.layout.VBox v = new javafx.scene.layout.VBox(12,
                            Ui.botao("← Voltar", this::fecharFicha, "fantasma"),
                            FichaJogador.kings(sessao.jogo, t, a, PainelElenco.vagaKings(a), null));
                    v.setPadding(new javafx.geometry.Insets(16));
                    v.setMaxWidth(760);
                    javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(v);
                    sp.setFitToWidth(true);
                    abrirFicha(sp);
                    return;
                }
            }
        }
    }

    public void abrirFichaJogadorPorId(int id) {
        var x = sessao.jogo.jogador(id);
        if (x != null) abrirFichaJogador(x);
    }

    public void abrirFichaTecnico(int id) {
        var t = sessao.jogo.treinador(id);
        if (t != null) abrirFicha(Fichas.treinador(this, t, this::fecharFicha));
    }

    public void abrirFichaClube(String id) {
        abrirFicha(Fichas.clube(this, id, this::fecharFicha));
    }

    /** Página de qualquer participante: clube, seleção ("sel-XXX") ou time da Kings League. */
    public void abrirParticipante(String id) {
        if (id == null) return;
        Jogo j = sessao.jogo;
        if (id.startsWith("sel-")) {
            if (j.selecao(id) != null) abrirFicha(PaginasEquipe.selecao(this, id.substring(4), this::fecharFicha));
            return;
        }
        if (j.mundo().clube(id) != null) {
            abrirFichaClube(id);
            return;
        }
        if (j.timeKings(id) != null) abrirFicha(PaginasEquipe.timeKings(this, id, this::fecharFicha));
        else if (!id.startsWith("kings-")) abrirFichaClube(id);
    }

    /** Página da competição (visão geral, tabela, jogos, estatísticas, campeões, formato). */
    public void abrirCompeticao(String id) {
        if (id == null) return;
        abrirFicha(PaginaCompeticao.construir(this, id, null, this::fecharFicha));
    }

    void fecharFicha() {
        if (!historico.isEmpty()) {
            Node anterior = historico.pop();
            centro.getChildren().setAll(anterior);
            fichaAberta = !historico.isEmpty();
            if (!fichaAberta) atualizarTopo();
            return;
        }
        fichaAberta = false;
        selecionar(atual);
    }

    /** Carreira só com um time da Kings League (sem clube). */
    public boolean soKings() {
        return sessao.fluxo.soKings();
    }

    /** Na carreira só da Kings, esconde o que depende de clube. */
    private void ajustarNavegacao() {
        boolean k = soKings();
        java.util.Map<String, Boolean> grupoVisivel = new java.util.HashMap<>();
        for (Entrada e : entradas.values()) {
            boolean vis = !(k && DO_CLUBE.contains(e.id()));
            Button b = botoes.get(e.id());
            b.setVisible(vis);
            b.setManaged(vis);
            if (vis) grupoVisivel.put(e.grupo(), true);
        }
        grupos.forEach((g, l) -> {
            boolean vis = grupoVisivel.getOrDefault(g, false);
            l.setVisible(vis);
            l.setManaged(vis);
        });
    }

    /**
     * Carrega fora da thread da tela o que a primeira ficha de jogador e as páginas de clube usam
     * (carreiras e campeões reais do banco: ~0,5 s com o banco completo). Sem isso o primeiro clique
     * no Elenco travava.
     */
    private static void preaquecer(Sessao sessao) {
        br.futebas.dados.BancoMundo db;
        try {
            db = sessao.jogo.banco();
        } catch (RuntimeException e) {
            return;
        }
        if (db == null) return;
        Thread t = new Thread(() -> {
            try {
                db.historico();
            } catch (RuntimeException e) {
                // sem camada histórica: a tela trata
            }
        }, "futebas-preaquecer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /**
     * Na tela de carregamento (fora da thread da tela, com o jogo recém-aberto): pede ao cache de
     * imagens os escudos e troféus que a 1ª central mostra (o seu clube, os próximos adversários, os
     * times e troféus das suas competições) e espera até 0,3 s por eles. Ao abrir a central, as
     * primeiras já estão no cache e as outras chegam sozinhas.
     */
    public static void prepararCentral(Sessao s) {
        try {
            Imagens.preaquecer(imagensIniciais(s));
            long fim = System.nanoTime() + 300_000_000L; // o resto segue em segundo plano (a tela não espera imagem)
            while (Imagens.ocupado() && System.nanoTime() < fim) Thread.sleep(15);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // pré-aquecimento é opcional
        }
    }

    /** Escudos e troféus da 1ª central, nos tamanhos que o Próximo jogo e a barra do topo usam. */
    static java.util.List<Imagens.Pedido> imagensIniciais(Sessao s) {
        java.util.List<Imagens.Pedido> l = new java.util.ArrayList<>();
        Jogo j = s.jogo;
        Clube c = j.meuClube();
        String meu = c != null ? c.id : j.mundo().tecnico != null ? j.mundo().tecnico.kingsTimeId : null;
        if (meu == null) return l;
        for (double h : new double[] {18, 20, 22, 26, 44, 64}) l.add(Imagens.pedidoEscudo(meu, h));
        java.util.List<Partida> prox = c != null ? j.proximasPartidas(meu, 3) : java.util.List.of();
        for (Partida p : prox) {
            String outro = meu.equals(p.mandante) ? p.visitante : p.mandante;
            for (double h : new double[] {18, 22, 26, 44, 64}) l.add(Imagens.pedidoEscudo(outro, h));
        }
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (Torneio t : j.competicoesDoParticipante(meu)) {
            for (double h : new double[] {18, 22, 30, 64}) l.add(Imagens.pedidoTrofeu(t.id, t.ambito, t.pais, h));
            for (String id : t.participantes) {
                if (vistos.size() >= 240 || !vistos.add(id)) continue;
                l.add(Imagens.pedidoEscudo(id, 18));
                l.add(Imagens.pedidoEscudo(id, 20));
            }
        }
        return l;
    }

    public Sessao sessao() { return sessao; }

    public Jogo jogo() { return sessao.jogo; }

    public Clube clube() { return sessao.jogo.meuClube(); }

    public String painelAtual() { return atual; }

    public java.util.Set<String> paineis() { return entradas.keySet(); }

    /** O painel aparece na navegação? (Na carreira da Kings, os do clube somem.) */
    public boolean painelVisivel(String id) {
        return entradas.containsKey(id) && !(soKings() && DO_CLUBE.contains(id));
    }

    /** Mostra um painel (reconstruído na hora, sempre com os dados atuais). */
    public void selecionar(String pedido) {
        String id = soKings() && DO_CLUBE.contains(pedido) ? ("transferencias".equals(pedido) ? "mercado" : "kings") : pedido;
        Entrada e = entradas.get(id);
        if (e == null) return;
        fichaAberta = false;
        historico.clear();
        atual = id;
        ajustarNavegacao();
        botoes.forEach((k, b) -> {
            b.getStyleClass().remove("ativo");
            if (k.equals(id)) b.getStyleClass().add("ativo");
        });
        Painel painel = e.fabrica().apply(this);
        Node n = painel.construir();
        centro.getChildren().setAll(n);
        atualizarTopo();
    }

    /** Recria o painel atual (depois de uma ação que muda os dados). */
    public void atualizar() {
        selecionar(atual);
    }

    public void atualizarTopo() {
        Jogo j = sessao.jogo;
        Clube c = j.meuClube();
        topo.getChildren().clear();
        if (c == null) {
            if (soKings()) topoKings(j);
            return;
        }
        topo.setStyle(Tema.estiloCabecalho(c.cor1, c.cor2));
        faixa.setStyle("-fx-background-color: " + Tema.hex(Tema.faixaClube(c.cor1, c.cor2)) + ";");

        Label nome = Ui.rotulo(c.nome, "cab-nome");
        String pos = posicaoNaLiga(c);
        Label meta = Ui.rotulo(j.mundo().tecnico.nome + "  ·  " + Textos.divisaoDe(j.mundo(), c) + (pos == null ? "" : "  ·  " + pos), "cab-meta");
        VBox id = new VBox(1, nome, meta);
        Node vez = vezDe(j);
        if (vez != null) id.getChildren().add(0, vez);
        id.setMinWidth(0);
        HBox.setHgrow(id, Priority.ALWAYS);

        VBox quando = bloco(Textos.dataLonga(j.dataAtual()), "Temporada " + j.ano() + " · semana " + j.semana());
        ((Label) quando.getChildren().get(0)).getStyleClass().setAll("label", "cab-valor");
        ((Label) quando.getChildren().get(0)).setStyle("-fx-font-size: 15px;");
        VBox dinheiro = bloco(Textos.dinheiro(c.dinheiro), "em caixa");
        if (c.dinheiro < 0) dinheiro.getChildren().get(0).setStyle("-fx-text-fill: #ffb4a8;");
        VBox conf = new VBox(3, confianca("Diretoria", c.confiancaDiretoria), confianca("Torcida", c.confiancaTorcida));
        conf.setAlignment(Pos.CENTER_LEFT);

        Button menu = new Button("Menu");
        menu.setOnAction(e -> menu(menu));
        menu.setFocusTraversable(false);
        menu.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(busca, Priority.SOMETIMES);
        busca.setMaxWidth(320);
        busca.setPrefWidth(240);
        busca.setMinWidth(150);
        topo.getChildren().addAll(Ui.escudo(c.id, 44), id, busca, sep(), dinheiro, sep(), conf, sep(), menu, quando, jogar);
        atualizarJogar();
        int nl = j.mensagensNaoLidas().size();
        preencherBotao(entradas.get("caixa"), nl);
        preencherBotao(entradas.get("mercado"), 0);
        preencherBotao(entradas.get("transferencias"), PainelTransferencias.pendentes(j, c));
        preencherBotao(entradas.get("vagas"), PainelVagas.querVoce(j));
    }

    /** Cabeçalho da carreira só da Kings: o time, a liga, a pesquisa, o menu, a data e o JOGAR. */
    private void topoKings(Jogo j) {
        var t = j.timeKings(j.mundo().tecnico.kingsTimeId);
        if (t == null) return;
        topo.setStyle(Tema.estiloCabecalho(t.cor1, t.cor2));
        faixa.setStyle("-fx-background-color: " + Tema.hex(Tema.faixaClube(t.cor1, t.cor2)) + ";");
        String liga = "Kings League";
        for (var l : j.ligasKings()) if (l.time(t.id) != null) liga = l.nome;
        int[] pos = j.posicaoKings();
        VBox id = new VBox(1, Ui.rotulo(t.nome, "cab-nome"), Ui.rotulo(j.mundo().tecnico.nome + "  ·  " + liga
                + (pos == null ? "" : "  ·  " + pos[0] + "º lugar"), "cab-meta"));
        Node vez = vezDe(j);
        if (vez != null) id.getChildren().add(0, vez);
        id.setMinWidth(0);
        HBox.setHgrow(id, Priority.ALWAYS);
        VBox quando = bloco(Textos.dataLonga(j.dataAtual()), "Temporada " + j.ano() + " · semana " + j.semana());
        ((Label) quando.getChildren().get(0)).getStyleClass().setAll("label", "cab-valor");
        ((Label) quando.getChildren().get(0)).setStyle("-fx-font-size: 15px;");
        HBox conf = confianca("Presidente", j.confiancaPresidenteKings());
        Tooltip.install(conf, new Tooltip("Confiança do presidente" + (t.presidente == null ? "" : " (" + t.presidente + ")")
                + ": sobe com a colocação na liga. Terminar o Split em último pode custar o cargo."));
        VBox confs = new VBox(3, conf);
        confs.setAlignment(Pos.CENTER_LEFT);
        Button menu = new Button("Menu");
        menu.setOnAction(e -> menu(menu));
        menu.setFocusTraversable(false);
        menu.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(busca, Priority.SOMETIMES);
        busca.setMaxWidth(320);
        busca.setPrefWidth(240);
        busca.setMinWidth(150);
        topo.getChildren().addAll(Ui.escudo(t.id, 44), id, busca, sep(), confs, sep(), menu, quando, jogar);
        atualizarJogar();
        preencherBotao(entradas.get("caixa"), j.mensagensNaoLidas().size());
        preencherBotao(entradas.get("vagas"), PainelVagas.querVoce(j));
    }

    /** Multi-treinador: de quem é a vez ("VEZ DE ANA · 1/2"), em cima do nome do clube; null no jogo de um técnico. */
    private static Node vezDe(Jogo j) {
        if (!j.multi()) return null;
        Label l = Ui.rotulo("VEZ DE " + j.mundo().tecnico.nome.toUpperCase(java.util.Locale.ROOT) + "  ·  " + (j.vez() + 1) + "/" + j.tecnicos().size(), "cab-rotulo");
        l.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffe08a;");
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    /** Texto de quem é a vez na barra de cima (testes); null no jogo de um técnico. */
    public String textoVez() {
        Node n = vezDe(sessao.jogo);
        return n instanceof Label l ? l.getText() : null;
    }

    private String posicaoNaLiga(Clube c) {
        for (Torneio t : sessao.jogo.competicoesDoAno()) {
            if (!"nacional".equals(t.ambito) || !c.pais.equals(t.pais) || t.divisao == null || t.divisao != c.divisao) continue;
            var ls = sessao.jogo.tabela(t.id);
            for (int i = 0; i < ls.size(); i++) if (ls.get(i).clubeId.equals(c.id) && ls.get(i).jogos > 0) return (i + 1) + "º lugar";
        }
        return null;
    }

    private static VBox bloco(String valor, String rotulo) {
        VBox v = new VBox(0, Ui.rotulo(valor, "cab-valor"), Ui.rotulo(rotulo, "cab-rotulo"));
        v.setAlignment(Pos.CENTER_LEFT);
        v.setMinWidth(Region.USE_PREF_SIZE);
        return v;
    }

    private static HBox confianca(String nome, double v) {
        ProgressBar p = new ProgressBar(Math.max(0, Math.min(100, v)) / 100.0);
        p.setPrefSize(84, 6);
        p.getStyleClass().add("barra-cab");
        if (v < 30) p.getStyleClass().add("baixa");
        else if (v < 50) p.getStyleClass().add("media");
        Label l = Ui.rotulo(nome, "cab-rotulo");
        l.setMinWidth(54);
        HBox h = new HBox(6, l, p);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static Region sep() {
        Region r = new Region();
        r.getStyleClass().add("cab-sep");
        r.setMinHeight(32);
        return r;
    }

    private void atualizarJogar() {
        Clube c = clube();
        String meu = c != null ? c.id : soKings() ? sessao.jogo.mundo().tecnico.kingsTimeId : null;
        Partida p = c != null ? sessao.fluxo.proximoJogo() : meu != null ? sessao.fluxo.proximoJogoKings() : null;
        // multi-treinador: quem não é o último passa a vez ao próximo técnico
        boolean passa = sessao.jogo.multi() && sessao.jogo.vez() + 1 < sessao.jogo.tecnicos().size();
        Label t = Ui.rotulo(passa ? "PRONTO  ›" : "JOGAR  ›", "jogar-texto");
        Label sub;
        Node escudo = null;
        if (p == null || meu == null) sub = Ui.rotulo("avançar", "jogar-sub");
        else {
            boolean casa = meu.equals(p.mandante);
            String adv = casa ? p.visitante : p.mandante;
            sub = Ui.rotulo((casa ? "x " : "em ") + sessao.jogo.mundo().nomeParticipante(adv), "jogar-sub");
            escudo = Imagens.escudo(adv, 26);
        }
        sub.setMaxWidth(130);
        VBox txt = new VBox(0, t, sub);
        HBox g = escudo == null ? new HBox(txt) : new HBox(10, escudo, txt);
        g.setAlignment(Pos.CENTER_LEFT);
        jogar.setGraphic(g);
        jogar.setText(null);
        jogar.setMaxWidth(Region.USE_PREF_SIZE);
        jogar.setMinWidth(Region.USE_PREF_SIZE);
        Tooltip.install(jogar, new Tooltip("Espaço ou Enter"));
    }

    private void menu(Node ancora) {
        ContextMenu cm = new ContextMenu();
        MenuItem salvar = new MenuItem("Salvar  (F2)");
        salvar.setOnAction(e -> App.atual().salvar());
        MenuItem salvarSair = new MenuItem("Salvar e voltar ao início");
        salvarSair.setOnAction(e -> App.atual().salvar(() -> App.atual().irParaInicio()));
        MenuItem sair = new MenuItem("Voltar ao início sem salvar");
        sair.setOnAction(e -> {
            if (App.atual().podeSairSemSalvar("Voltar ao início")) App.atual().irParaInicio();
        });
        MenuItem demissao = new MenuItem(clube() != null ? "Pedir demissão do clube" : soKings() ? "Pedir demissão do time da Kings" : "Pedir demissão");
        demissao.setOnAction(e -> {
            String emprego = sessao.jogo.empregoAtual();
            if (emprego == null) return;
            if (Ui.confirmar("Pedir demissão", "Deixar o comando do " + emprego + "?")) {
                sessao.jogo.pedirDemissao();
                App.atual().irParaHub(null);
            }
        });
        MenuItem opcoes = new MenuItem("Configurações");
        opcoes.setOnAction(e -> abrirOpcoes());
        MenuItem simular = new MenuItem("Simular temporada");
        simular.setOnAction(e -> App.atual().simularTemporada(null));
        cm.getItems().addAll(salvar, salvarSair, sair, new SeparatorMenuItem(), simular, opcoes, new SeparatorMenuItem(), demissao);
        if (ancora != null) cm.show(ancora, javafx.geometry.Side.BOTTOM, 0, 4);
        else cm.show(raiz, javafx.geometry.Side.TOP, raiz.getWidth() - 240, 70);
    }

    /** Aba do painel Kings League que abre na próxima vez: "competicoes", "time" ou "draft". */
    public static void abaKings(String id) { PainelKings.abrirAba(id); }

    /** Opções do jogo (valem para todos os saves). */
    public static void abrirOpcoes() {
        TelaOpcoes.abrir();
    }

    @Override
    public Parent raiz() { return raiz; }

    @Override
    public void aoMostrar() {
        mostradoEm = System.currentTimeMillis();
        Ui.aoAbrirClube = this::abrirParticipante;
        Ui.aoAbrirCompeticao = this::abrirCompeticao;
        Ui.aoAbrirJogador = this::abrirFichaJogadorPorId;
        Ui.aoAbrirAtletaKings = this::abrirAtletaKings;
        Ui.aoAbrirTecnico = this::abrirFichaTecnico;
        selecionar(atual);
    }

    @Override
    public boolean tecla(KeyEvent e) {
        for (Entrada en : entradas.values()) {
            if (en.tecla() != null && en.tecla() == e.getCode()) {
                selecionar(en.id());
                return true;
            }
        }
        if (e.isControlDown() && e.getCode() == KeyCode.F) {
            selecionar("financas");
            return true;
        }
        if (e.isControlDown() && e.getCode() == KeyCode.T) {
            selecionar("tabelas");
            return true;
        }
        boolean texto = raiz.getScene() != null && (raiz.getScene().getFocusOwner() instanceof TextInputControl
                || raiz.getScene().getFocusOwner() instanceof ComboBox);
        if ((e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) && !texto && !e.isControlDown()
                && !GuardaJogar.permite(mostradoEm, System.currentTimeMillis())) {
            return true; // a tecla que fechou a partida (ou repetida) não começa outra
        }
        if ((e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) && !texto && !e.isControlDown()) {
            App.atual().jogar();
            return true;
        }
        if ((e.isControlDown() && e.getCode() == KeyCode.K) || (e.getCode() == KeyCode.SLASH && !texto)) {
            busca.requestFocus();
            return true;
        }
        if (e.getCode() == KeyCode.ESCAPE && fichaAberta) {
            fecharFicha();
            return true;
        }
        if (e.getCode() == KeyCode.ESCAPE) {
            menu(null);
            return true;
        }
        return false;
    }

    /** Usado pelos painéis que precisam rolar. */
    static ScrollPane rolar(Node n) {
        return Ui.rolar(n);
    }
}
