package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.ia.PropostaJogador;
import br.futebas.ia.PropostaTecnico;
import br.futebas.kings.LigaKings;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mensagem;
import br.futebas.mundo.Mundo;
import br.futebas.ui.App;
import br.futebas.ui.comp.Foto;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Caixa de entrada no estilo dos jogos de técnico atuais: filtros por categoria no topo, lista de
 * mensagens à esquerda (remetente, assunto, data, não lidas em negrito, agrupadas por período) e
 * a mensagem inteira à direita, com as ações dela (responder proposta, ver jogador, ir às
 * transferências...), marcar como não lida e excluir.
 */
final class PainelCaixa implements Hub.Painel {
    private final Hub hub;
    private static String filtroAntes = "todas";
    private static Mensagem selecionadaAntes;

    private final ListView<Object> lista = new ListView<>();
    private final BorderPane leitura = new BorderPane();
    private final TextField busca = new TextField();
    private String filtro = filtroAntes;
    private HBox filtros;
    private Label contador, selo;
    private Button todasLidas;

    PainelCaixa(Hub hub) {
        this.hub = hub;
    }

    // ------------------------------------------------------------------ usados por outras telas
    /** Abre a caixa de entrada já com esta mensagem aberta (a página inicial usa nas manchetes). */
    static void abrirMensagem(Hub hub, Mensagem m) {
        filtroAntes = "todas";
        selecionadaAntes = m;
        hub.selecionar("caixa");
    }

    static String tipo(String t) {
        if (t == null) return "Aviso";
        return switch (t) {
            case "jogador" -> "Elenco";
            case "diretoria" -> "Diretoria";
            case "torcida" -> "Torcida";
            case "mercado" -> "Mercado";
            case "proposta" -> "Proposta";
            case "resultado" -> "Resultado";
            case "kings" -> "Kings League";
            case "financas" -> "Finanças";
            case "selecao" -> "Seleção";
            default -> "Mundo";
        };
    }

    static Label etiquetaTipo(String t) {
        String cor = t == null ? null : switch (t) {
            case "diretoria", "selecao" -> "escura";
            case "torcida" -> "vermelha";
            case "mercado", "proposta" -> "ambar";
            case "resultado", "jogador" -> "verde";
            case "financas" -> "azul";
            default -> null;
        };
        Label l = Ui.etiqueta(tipo(t), cor);
        l.setMinWidth(78);
        l.setAlignment(Pos.CENTER);
        return l;
    }

    // ------------------------------------------------------------------ filtros
    private record Filtro(String id, String nome) {}

    private static final List<Filtro> FILTROS = List.of(new Filtro("todas", "Todas"), new Filtro("naolidas", "Não lidas"),
            new Filtro("propostas", "Propostas"), new Filtro("elenco", "Elenco"), new Filtro("diretoria", "Diretoria"),
            new Filtro("mercado", "Mercado"), new Filtro("financas", "Finanças"), new Filtro("competicoes", "Competições"),
            new Filtro("kings", "Kings League"), new Filtro("mundo", "Mundo"));

    private static boolean passa(String filtro, Mensagem m) {
        String t = m.tipo == null ? "" : m.tipo;
        return switch (filtro) {
            case "naolidas" -> !m.lida;
            case "propostas" -> "proposta".equals(t);
            case "elenco" -> "jogador".equals(t);
            case "diretoria" -> "diretoria".equals(t) || "torcida".equals(t);
            case "mercado" -> "mercado".equals(t);
            case "financas" -> "financas".equals(t);
            case "competicoes" -> "resultado".equals(t) || "selecao".equals(t);
            case "kings" -> "kings".equals(t);
            case "mundo" -> !List.of("proposta", "jogador", "diretoria", "torcida", "mercado", "financas", "resultado", "selecao", "kings").contains(t);
            default -> true;
        };
    }

    @Override
    public Node construir() {
        PainelFinancas.registrar(hub.jogo());
        contador = Ui.rotulo("", "cx-sub");
        selo = Ui.rotulo("", "cx-selo");
        selo.setMinWidth(Region.USE_PREF_SIZE);
        filtros = new HBox(8);
        filtros.getStyleClass().add("cx-filtros");
        filtros.setAlignment(Pos.CENTER_LEFT);
        busca.setPromptText("Buscar mensagens");
        busca.getStyleClass().add("cx-busca");
        busca.textProperty().addListener((o, a, b) -> recarregar(null));
        todasLidas = Ui.botao("Marcar todas como lidas", () -> {
            hub.jogo().marcarMensagensComoLidas();
            hub.atualizarTopo();
            recarregar(selecionada());
        }, "cx-botao");
        todasLidas.setGraphic(icone(ICONE_LIDAS, "cx-botao-icone"));
        todasLidas.setGraphicTextGap(8);

        // barra do topo: ícone, título com o selo de não lidas e o resumo; à direita a busca e a ação
        StackPane envelope = new StackPane(icone(ICONE_CAIXA, "cx-topo-icone"));
        envelope.getStyleClass().add("cx-topo-icone-fundo");
        envelope.setMinSize(40, 40);
        envelope.setMaxSize(40, 40);
        VBox titulo = new VBox(1, Ui.linha(10, Ui.rotulo("Caixa de entrada", "cx-titulo"), selo), contador);
        titulo.setAlignment(Pos.CENTER_LEFT);
        HBox cab = Ui.linha(12, envelope, titulo, Ui.espaco(), campoBusca(), todasLidas);
        cab.getStyleClass().add("cx-topo-linha");
        VBox topo = new VBox(cab, filtros);
        topo.getStyleClass().add("cx-topo");

        lista.getStyleClass().add("cx-lista");
        lista.setCellFactory(x -> new Celula());
        lista.setPlaceholder(Ui.rotulo("Nenhuma mensagem", "texto-suave"));
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b instanceof Mensagem m) mostrar(m);
            else if (b instanceof String) {
                // cabeçalho de período: não é selecionável
                int i = lista.getSelectionModel().getSelectedIndex();
                int ia = a == null ? -1 : lista.getItems().indexOf(a);
                int prox = i + (ia > i ? -1 : 1);
                if (prox >= 0 && prox < lista.getItems().size() && lista.getItems().get(prox) instanceof Mensagem)
                    lista.getSelectionModel().select(prox);
                else if (a != null) lista.getSelectionModel().select(a);
            }
        });
        lista.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE && selecionada() != null) excluir(selecionada());
        });
        VBox esq = new VBox(lista);
        VBox.setVgrow(lista, Priority.ALWAYS);
        esq.getStyleClass().add("cx-coluna-lista");
        esq.setPrefWidth(540); // lista maior (requisito 15/11)
        esq.setMinWidth(340);

        leitura.getStyleClass().add("cx-leitura");
        HBox corpo = new HBox(14, esq, leitura);
        HBox.setHgrow(leitura, Priority.ALWAYS);
        VBox.setVgrow(corpo, Priority.ALWAYS);

        VBox v = Ui.conteudo(topo, corpo);
        recarregar(selecionadaAntes);
        return v;
    }

    private Mensagem selecionada() {
        Object o = lista.getSelectionModel().getSelectedItem();
        return o instanceof Mensagem m ? m : null;
    }

    private void montarFiltros(List<Mensagem> todas) {
        filtros.getChildren().clear();
        HBox segmento = new HBox(2);
        segmento.getStyleClass().add("cx-segmento");
        segmento.setAlignment(Pos.CENTER_LEFT);
        segmento.setMinWidth(Region.USE_PREF_SIZE);
        HBox categorias = new HBox(6);
        categorias.setAlignment(Pos.CENTER_LEFT);
        for (Filtro f : FILTROS) {
            boolean principal = f.id().equals("todas") || f.id().equals("naolidas");
            long n = todas.stream().filter(m -> passa(f.id(), m)).count();
            long nl = todas.stream().filter(m -> !m.lida && passa(f.id(), m)).count();
            if (n == 0 && !principal && !f.id().equals(filtro)) continue;
            HBox chip = Ui.linha(6, Ui.rotulo(f.nome(), "cx-chip-nome"));
            if (principal) {
                long num = f.id().equals("todas") ? n : nl;
                if (num > 0) chip.getChildren().add(Ui.rotulo(String.valueOf(num), f.id().equals("naolidas") ? "cx-chip-num" : "cx-chip-total"));
                chip.getStyleClass().add("cx-seg");
            } else {
                chip.getChildren().add(Ui.rotulo(String.valueOf(nl > 0 ? nl : n), nl > 0 ? "cx-chip-num" : "cx-chip-total"));
                chip.getStyleClass().add("cx-chip");
            }
            if (f.id().equals(filtro)) chip.getStyleClass().add("ativo");
            chip.setMinWidth(Region.USE_PREF_SIZE);
            chip.setCursor(javafx.scene.Cursor.HAND);
            chip.setOnMouseClicked(e -> {
                filtro = f.id();
                filtroAntes = filtro;
                recarregar(selecionada());
            });
            (principal ? segmento : categorias).getChildren().add(chip);
        }
        filtros.getChildren().add(segmento);
        if (!categorias.getChildren().isEmpty()) {
            Region div = new Region();
            div.getStyleClass().add("cx-divisor");
            // muitas categorias numa tela estreita: rolam de lado em vez de cortar
            ScrollPane rolo = new ScrollPane(categorias);
            rolo.getStyleClass().add("cx-categorias");
            rolo.setFitToHeight(true);
            rolo.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            rolo.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            rolo.setPannable(true);
            rolo.setOnScroll(e -> {
                double w = categorias.getWidth() - rolo.getViewportBounds().getWidth();
                if (w > 0) rolo.setHvalue(rolo.getHvalue() - e.getDeltaY() / w);
            });
            HBox.setHgrow(rolo, Priority.ALWAYS);
            filtros.getChildren().addAll(div, Ui.rotulo("Categorias", "cx-filtros-rotulo"), rolo);
        }
    }

    /** Campo de busca arredondado com a lupa dentro e um "x" para limpar. */
    private Node campoBusca() {
        Node lupa = icone(ICONE_LUPA, "cx-busca-icone");
        lupa.setMouseTransparent(true);
        Label limpar = Ui.rotulo("\u2715", "cx-busca-limpar");
        limpar.setCursor(javafx.scene.Cursor.HAND);
        limpar.setOnMouseClicked(e -> busca.clear());
        limpar.visibleProperty().bind(busca.textProperty().isNotEmpty());
        StackPane sp = new StackPane(busca, lupa, limpar);
        StackPane.setAlignment(lupa, Pos.CENTER_LEFT);
        StackPane.setMargin(lupa, new javafx.geometry.Insets(0, 0, 0, 12));
        StackPane.setAlignment(limpar, Pos.CENTER_RIGHT);
        StackPane.setMargin(limpar, new javafx.geometry.Insets(0, 10, 0, 0));
        sp.setPrefWidth(260);
        sp.setMinWidth(180);
        sp.setMaxWidth(300);
        return sp;
    }

    private static final String ICONE_LUPA = "M6.5 1a5.5 5.5 0 0 1 4.38 8.83l3.65 3.64-1.06 1.06-3.64-3.65A5.5 5.5 0 1 1 6.5 1zm0 1.5a4 4 0 1 0 0 8 4 4 0 0 0 0-8z";
    private static final String ICONE_LIDAS = "M1 8.5l1.1-1.1 3 3L12.9 2.6 14 3.7 5.1 12.6zM6.6 12.6l-1-1 1.1-1.1 1 1L15.9 3.3 17 4.4z";
    private static final String ICONE_CAIXA = "M2 3h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H2a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zm.6 2v.3l7.4 5 7.4-5V5zm14.8 2.2l-7.4 5-7.4-5V15.5h14.8z";

    private static Node icone(String caminho, String classe) {
        javafx.scene.shape.SVGPath sp = new javafx.scene.shape.SVGPath();
        sp.setContent(caminho);
        sp.getStyleClass().add(classe);
        return sp;
    }

    /** Selo de não lidas e o resumo embaixo do título. */
    private void atualizarContador() {
        List<Mensagem> todas = hub.jogo().caixaDeEntrada();
        int total = todas.size();
        int naoLidas = (int) todas.stream().filter(m -> !m.lida).count();
        contador.setText(total == 0 ? "Nenhuma mensagem" : total + (total == 1 ? " mensagem" : " mensagens")
                + (naoLidas == 0 ? "  ·  tudo lido" : ""));
        selo.setText(naoLidas == 0 ? "" : naoLidas + (naoLidas == 1 ? " não lida" : " não lidas"));
        selo.setVisible(naoLidas > 0);
        selo.setManaged(naoLidas > 0);
        if (todasLidas != null) todasLidas.setDisable(naoLidas == 0);
    }

    /** Refaz a lista (mantendo a mensagem escolhida, se ainda estiver visível). */
    private void recarregar(Mensagem manter) {
        Jogo j = hub.jogo();
        List<Mensagem> todas = new ArrayList<>(j.caixaDeEntrada());
        java.util.Collections.reverse(todas);
        // não lidas primeiro (a mais nova no topo), depois o resto por período (regra do jogo)
        List<Mensagem> ordem = new ArrayList<>();
        for (Mensagem x : todas) if (!x.lida) ordem.add(x);
        int naoLidas = ordem.size();
        for (Mensagem x : todas) if (x.lida) ordem.add(x);
        todas = ordem;
        montarFiltros(todas);
        atualizarContador();
        String q = norm(busca.getText());
        List<Object> itens = new ArrayList<>();
        String grupo = null;
        int absAgora = Calendario.absoluta(j.ano(), j.semana());
        for (Mensagem m : todas) {
            if (!passa(filtro, m)) continue;
            if (!q.isEmpty() && !norm(m.titulo + " " + m.texto + " " + remetente(j, m)).contains(q)) continue;
            String g = !m.lida && naoLidas > 0 ? "Não lidas" : periodo(absAgora, m);
            if (!g.equals(grupo)) {
                itens.add(g);
                grupo = g;
            }
            itens.add(m);
        }
        lista.getItems().setAll(itens);
        Object alvo = manter != null && itens.contains(manter) ? manter : null;
        // regra do jogo (15/11): ao abrir, a mensagem mais recente, a do topo da lista
        if (alvo == null) for (Object o : itens) if (o instanceof Mensagem) { alvo = o; break; }
        if (alvo != null) {
            lista.getSelectionModel().select(alvo);
            int i = lista.getItems().indexOf(alvo);
            lista.scrollTo(manter == alvo ? Math.max(0, i - 3) : 0);
            mostrar((Mensagem) alvo);
        } else {
            lista.getSelectionModel().clearSelection();
            leitura.setTop(null);
            leitura.setCenter(Ui.vazio(todas.isEmpty() ? "A caixa de entrada está vazia." : "Nenhuma mensagem neste filtro.", null, null));
        }
    }

    private static String norm(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static String periodo(int absAgora, Mensagem m) {
        int abs = Calendario.absoluta(m.ano, m.semana);
        int d = absAgora - abs;
        if (d <= 0) return "Esta semana";
        if (d == 1) return "Semana passada";
        if (d < 5) return "Últimas semanas";
        LocalDate dt = Calendario.inicio(abs);
        String mes = dt.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("pt-BR"));
        return Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + " de " + dt.getYear();
    }

    private static String quando(Mensagem m) {
        return Textos.dataCurta(Calendario.inicio(Calendario.absoluta(m.ano, m.semana)));
    }

    // ------------------------------------------------------------------ remetente
    private PropostaJogador propostaJogador(Jogo j, Mensagem m) {
        if (m.propostaId == null || convite(m)) return null;
        for (PropostaJogador p : j.mundo().propostas) if (p.id == m.propostaId) return p;
        return null;
    }

    private PropostaTecnico propostaTecnico(Jogo j, Mensagem m) {
        if (m.propostaId == null) return null;
        PropostaTecnico achada = null;
        for (PropostaTecnico p : j.mundo().propostasTecnico) if (p.id == m.propostaId) achada = p;
        if (achada == null) return null;
        // ids de propostas de jogador e de emprego são contadores diferentes: o título decide
        if (convite(m)) return achada;
        return propostaJogadorExiste(j, m) ? null : achada;
    }

    private static boolean propostaJogadorExiste(Jogo j, Mensagem m) {
        for (PropostaJogador p : j.mundo().propostas) if (p.id == m.propostaId) return true;
        return false;
    }

    private static boolean convite(Mensagem m) {
        return m.titulo != null && m.titulo.startsWith("Convite");
    }

    private String remetente(Jogo j, Mensagem m) {
        Clube c = hub.clube();
        String clube = c == null ? "" : " · " + c.nome;
        String t = m.tipo == null ? "" : m.tipo;
        return switch (t) {
            case "diretoria" -> "Diretoria" + clube;
            case "jogador" -> "Departamento de futebol" + clube;
            case "torcida" -> "Torcida" + clube;
            case "mercado" -> "Departamento de transferências";
            case "financas" -> "Departamento financeiro";
            case "resultado" -> "Assessoria de imprensa";
            case "selecao" -> "Federação";
            case "kings" -> "Kings League";
            case "proposta" -> {
                PropostaTecnico pt = propostaTecnico(j, m);
                if (pt != null) yield pt.kingsTimeId != null ? "Presidência · " + nomeKings(j, pt.kingsTimeId, pt.clube)
                        : pt.selecaoPais != null ? "Federação · " + pt.clube : "Diretoria · " + pt.clube;
                PropostaJogador pj = propostaJogador(j, m);
                if (pj != null) {
                    String outro = "recebida".equals(pj.direcao) || pj.direcao == null ? pj.compradorId : pj.vendedorId;
                    String nome = outro != null ? Textos.nomeClube(j.mundo(), outro) : pj.comprador;
                    yield nome == null ? "Departamento de transferências" : nome;
                }
                yield "Departamento de transferências";
            }
            default -> "Notícias";
        };
    }

    private static String nomeKings(Jogo j, String id, String padrao) {
        LigaKings.TimeKings t = j.timeKings(id);
        return t != null ? t.nome : padrao == null ? id : padrao;
    }

    /** Ícone do remetente: escudo de quem manda, logo da Kings, bandeira ou um símbolo. */
    private Node icone(Jogo j, Mensagem m, double tam, boolean clicavel) {
        Clube c = hub.clube();
        String t = m.tipo == null ? "" : m.tipo;
        String meuEscudo = c != null ? c.id : j.mundo().tecnico != null ? j.mundo().tecnico.kingsTimeId : null;
        Node n = switch (t) {
            case "diretoria", "jogador", "torcida" -> meuEscudo == null ? simbolo("F", tam) : escudo(meuEscudo, tam, clicavel);
            case "kings" -> {
                String tk = j.mundo().tecnico == null ? null : j.mundo().tecnico.kingsTimeId;
                LigaKings l = j.ligaKingsDoTime(tk);
                yield Imagens.logoKings(l == null ? null : l.id, tam);
            }
            case "selecao" -> {
                String pais = j.mundo().tecnico == null ? null : j.mundo().tecnico.selecaoPais;
                yield pais == null ? simbolo("⚑", tam) : Imagens.bandeiraRedonda(pais, tam);
            }
            case "proposta" -> {
                PropostaTecnico pt = propostaTecnico(j, m);
                if (pt != null) {
                    if (pt.kingsTimeId != null) yield escudo(pt.kingsTimeId, tam, clicavel);
                    if (pt.selecaoPais != null) yield Imagens.bandeiraRedonda(pt.selecaoPais, tam);
                    if (pt.clubeId != null) yield escudo(pt.clubeId, tam, clicavel);
                }
                PropostaJogador pj = propostaJogador(j, m);
                if (pj != null) {
                    String outro = "recebida".equals(pj.direcao) || pj.direcao == null ? pj.compradorId : pj.vendedorId;
                    if (outro != null) yield escudo(outro, tam, clicavel);
                }
                yield simbolo("⇄", tam);
            }
            case "mercado" -> simbolo("⇄", tam);
            case "financas" -> simbolo("$", tam);
            case "resultado" -> meuEscudo == null ? simbolo("⚽", tam) : escudo(meuEscudo, tam, clicavel);
            default -> simbolo("◉", tam);
        };
        StackPane s = new StackPane(n);
        s.setMinSize(tam, tam);
        s.setPrefSize(tam, tam);
        s.setMaxSize(tam, tam);
        return s;
    }

    private static Node escudo(String id, double tam, boolean clicavel) {
        return clicavel ? Ui.escudo(id, tam) : Imagens.escudo(id, tam);
    }

    private static Node simbolo(String s, double tam) {
        Label l = Ui.rotulo(s, "cx-simbolo");
        l.setStyle("-fx-font-size: " + Math.round(tam * 0.46) + "px;");
        StackPane p = new StackPane(l);
        p.getStyleClass().add("cx-simbolo-fundo");
        p.setMinSize(tam, tam);
        p.setPrefSize(tam, tam);
        p.setMaxSize(tam, tam);
        return p;
    }

    // ------------------------------------------------------------------ célula da lista
    private final class Celula extends ListCell<Object> {
        Celula() {
            getStyleClass().add("cx-celula");
        }

        @Override
        protected void updateItem(Object o, boolean vazio) {
            super.updateItem(o, vazio);
            getStyleClass().removeAll("cx-grupo-celula", "cx-nao-lida");
            setText(null);
            if (vazio || o == null) {
                setGraphic(null);
                return;
            }
            if (o instanceof String g) {
                getStyleClass().add("cx-grupo-celula");
                setGraphic(Ui.rotulo(g.toUpperCase(Locale.ROOT), "cx-grupo"));
                return;
            }
            Mensagem m = (Mensagem) o;
            Jogo j = hub.jogo();
            if (!m.lida) getStyleClass().add("cx-nao-lida");
            Label de = Ui.rotulo(remetente(j, m), "cx-remetente");
            Label assunto = Ui.rotulo(m.titulo, m.lida ? "cx-assunto" : "cx-assunto-forte");
            assunto.getStyleClass().add("cx-assunto-grande");
            Label previa = Ui.rotulo(m.texto == null ? "" : m.texto.replace('\n', ' '), "cx-previa");
            for (Label l : List.of(de, assunto, previa)) {
                l.setMinWidth(0);
                l.setMaxWidth(Double.MAX_VALUE);
            }
            Label data = Ui.rotulo(quando(m), "cx-data");
            data.setMinWidth(Region.USE_PREF_SIZE);
            // o tema da mensagem logo de cara (requisito 15/11): etiqueta do tipo antes do remetente
            HBox topo = Ui.linha(8, etiquetaTipo(m.tipo), de, Ui.espaco(), data);
            HBox.setHgrow(de, Priority.ALWAYS);
            VBox txt = new VBox(1, topo, assunto, previa);
            txt.setMinWidth(0);
            HBox.setHgrow(txt, Priority.ALWAYS);
            Region marca = new Region();
            marca.getStyleClass().add(m.lida ? "cx-marca-lida" : "cx-marca");
            HBox h = new HBox(12, marca, icone(j, m, 42, false), txt);
            h.setAlignment(Pos.CENTER_LEFT);
            h.setMinWidth(0);
            h.prefWidthProperty().bind(lista.widthProperty().subtract(30));
            h.maxWidthProperty().bind(lista.widthProperty().subtract(30));
            if (m.propostaId != null && pendente(j, m)) txt.getChildren().add(Ui.etiqueta("Requer resposta", "ambar"));
            setGraphic(h);
        }
    }

    /** A mensagem tem algo esperando o técnico? */
    private boolean pendente(Jogo j, Mensagem m) {
        PropostaTecnico pt = propostaTecnico(j, m);
        if (pt != null) return "pendente".equals(pt.estado);
        PropostaJogador pj = propostaJogador(j, m);
        return pj != null && pj.minhaVez();
    }

    // ------------------------------------------------------------------ leitura
    private void mostrar(Mensagem m) {
        selecionadaAntes = m;
        Jogo j = hub.jogo();
        Mundo mu = j.mundo();
        if (!m.lida) {
            m.lida = true;
            hub.atualizarTopo();
            atualizarContador();
            lista.refresh();
            int nl = j.mensagensNaoLidas().size();
            int total = j.caixaDeEntrada().size();
            contador.setText(nl == 0 ? total + (total == 1 ? " mensagem" : " mensagens") : nl + (nl == 1 ? " não lida" : " não lidas") + " de " + total);
        }
        // cabeçalho: remetente, data e ações gerais
        Label de = Ui.rotulo(remetente(j, m), "cx-leitura-remetente");
        Label data = Ui.rotulo(Textos.dataLonga(Calendario.inicio(Calendario.absoluta(m.ano, m.semana))), "texto-suave");
        VBox quem = new VBox(2, de, Ui.linha(8, etiquetaTipo(m.tipo), data));
        Button naoLida = Ui.botao("Marcar como não lida", () -> {
            m.lida = false;
            hub.atualizarTopo();
            recarregar(m);
        }, "fantasma");
        Button excluir = Ui.botao("Excluir", () -> excluir(m), "fantasma");
        HBox cab = Ui.linha(14, icone(j, m, 52, true), quem, Ui.espaco(), naoLida, excluir);
        cab.getStyleClass().add("cx-leitura-cabeca");

        Label assunto = Ui.paragrafo(m.titulo, "cx-leitura-assunto");
        Label texto = Ui.paragrafo(m.texto == null ? "" : m.texto, "cx-leitura-texto");
        texto.setMaxWidth(720);
        VBox corpo = new VBox(14, assunto, texto);
        Node anexo = anexo(j, mu, m);
        if (anexo != null) corpo.getChildren().add(anexo);
        Node acoes = acoes(j, m);
        if (acoes != null) corpo.getChildren().add(acoes);
        corpo.getStyleClass().add("cx-leitura-corpo");
        ScrollPane sp = Ui.rolar(corpo);
        sp.getStyleClass().add("cx-leitura-rolar");
        leitura.setTop(cab);
        leitura.setCenter(sp);
    }

    private void excluir(Mensagem m) {
        List<Object> itens = lista.getItems();
        int i = itens.indexOf(m);
        Mensagem prox = null;
        for (int k = i + 1; k < itens.size() && prox == null; k++) if (itens.get(k) instanceof Mensagem x) prox = x;
        for (int k = i - 1; k >= 0 && prox == null; k--) if (itens.get(k) instanceof Mensagem x) prox = x;
        hub.jogo().caixaDeEntrada().remove(m);
        hub.atualizarTopo();
        recarregar(prox);
    }

    /** Cartão ligado à mensagem: o convite (clube/seleção/time da Kings) ou a negociação do jogador. */
    private Node anexo(Jogo j, Mundo mu, Mensagem m) {
        PropostaTecnico pt = propostaTecnico(j, m);
        if (pt != null) {
            Node marca;
            String nome;
            String sub;
            if (pt.kingsTimeId != null) {
                marca = Ui.escudo(pt.kingsTimeId, 48);
                nome = nomeKings(j, pt.kingsTimeId, pt.clube);
                LigaKings l = j.ligaKingsDoTime(pt.kingsTimeId);
                sub = l == null ? "Kings League" : Textos.ligaKings(l.id, l.nome);
            } else if (pt.selecaoPais != null) {
                marca = Imagens.bandeira(pt.selecaoPais, 32);
                nome = pt.clube;
                sub = "Seleção nacional";
            } else {
                marca = Ui.escudo(pt.clubeId, 48);
                nome = pt.clube;
                Clube c = mu.clube(pt.clubeId);
                sub = c == null ? "" : Textos.pais(c.pais) + (c.divisao > 0 ? "  ·  " + Textos.divisao(c.divisao) : "");
            }
            String alvo = pt.kingsTimeId != null ? pt.kingsTimeId : pt.selecaoPais != null ? "sel-" + pt.selecaoPais : pt.clubeId;
            Label n = Ui.nomeClube(alvo, nome, "cx-anexo-nome");
            VBox id = new VBox(2, n, Ui.rotulo(sub, "texto-suave"));
            HBox h = Ui.linha(14, marca, id, Ui.espaco(), estadoConvite(pt.estado));
            h.getStyleClass().add("cx-anexo");
            return h;
        }
        PropostaJogador p = propostaJogador(j, m);
        if (p == null) return null;
        Jogador x = mu.jogador(p.jogadorId);
        Node foto = x == null ? Foto.silhueta(54, 64) : Foto.de(j.foto(x.id), x.dbId, 54, 64);
        Label nome = Ui.nomeJogador(p.jogadorId, x == null ? "Jogador" : x.nomeExibicao(), "cx-anexo-nome");
        HBox dadosJog = x == null ? new HBox() : Ui.linha(8, Ui.pos(x.posicao), Ui.rotulo(x.idade(j.ano()) + " anos", "texto-suave"), Ui.forca(x.forcaExibida()));
        VBox id = new VBox(4, nome, dadosJog);
        // de → para
        Node de = p.vendedorId == null ? Ui.rotulo("Sem clube", "texto-suave") : Ui.clube(mu, p.vendedorId, 22);
        Node para = p.compradorId == null ? Ui.rotulo(p.comprador == null ? "Exterior" : p.comprador, "texto-medio") : Ui.clube(mu, p.compradorId, 22);
        HBox rota = Ui.linha(10, de, Ui.rotulo("→", "cx-seta"), para);
        String valor = p.valor > 0 ? Textos.dinheiro(p.valor) : "emprestimo".equals(p.tipo) ? "Empréstimo" : "Sem custo";
        VBox termos = new VBox(4, Ui.dado("emprestimo".equals(p.tipo) ? "Empréstimo" : "Taxa", valor));
        if (p.valorContra != null) termos.getChildren().add(Ui.dado("Pedem", Textos.dinheiro(p.valorContra)));
        if (p.salario > 0) termos.getChildren().add(Ui.dado("Salário", Textos.dinheiro(p.salario) + "/mês"));
        VBox meio = new VBox(10, id, rota);
        HBox.setHgrow(meio, Priority.ALWAYS);
        VBox dir = new VBox(8, PainelTransferencias.chip(p), termos);
        dir.setAlignment(Pos.TOP_RIGHT);
        HBox h = new HBox(14, foto, meio, dir);
        h.getStyleClass().add("cx-anexo");
        return h;
    }

    private static Label estadoConvite(String e) {
        return switch (e == null ? "" : e) {
            case "pendente" -> Ui.etiqueta("Aguardando a sua resposta", "ambar");
            case "aceita" -> Ui.etiqueta("Aceito", "verde");
            case "recusada" -> Ui.etiqueta("Recusado", "vermelha");
            case "expirada" -> Ui.etiqueta("Expirado", "escura");
            default -> Ui.etiqueta(e, null);
        };
    }

    /** Botões da mensagem: responder, ver, ir para a tela certa. */
    private Node acoes(Jogo j, Mensagem m) {
        HBox h = new HBox(8);
        h.getStyleClass().add("cx-acoes");
        PropostaTecnico pt = propostaTecnico(j, m);
        PropostaJogador pj = pt == null ? propostaJogador(j, m) : null;
        boolean temClube = hub.clube() != null;
        if (pt != null && "pendente".equals(pt.estado)) {
            boolean selecao = pt.selecaoPais != null;
            boolean kings = pt.kingsTimeId != null;
            h.getChildren().addAll(Ui.botao("Aceitar o convite", () -> {
                String nome = kings ? nomeKings(j, pt.kingsTimeId, pt.clube) : pt.clube;
                // carreira exclusiva: ou clube, ou time da Kings (a seleção é um cargo a mais)
                String atual = j.empregoAtual();
                String deixa = atual == null ? "" : " Você vai deixar o " + atual + ".";
                boolean ok = selecao ? Ui.confirmar("Assumir a seleção", "Comandar a " + nome + "?" + (atual == null ? "" : " Você continua no " + atual + "."))
                        : kings ? Ui.confirmar("Kings League", "Assumir o " + nome + "?" + deixa)
                        : Ui.confirmar("Mudar de clube", "Assumir o " + nome + "?" + deixa);
                if (ok && j.aceitarEmprego(pt.id)) {
                    App.atual().irParaHub(selecao ? "selecoes" : kings ? "kings" : "jogo");
                }
            }, "primario"), Ui.botao("Recusar", () -> {
                j.recusarEmprego(pt.id);
                hub.atualizarTopo();
                recarregar(m);
            }));
        } else if (pj != null) {
            Node resp = PainelTransferencias.acoes(hub, pj, () -> recarregar(m));
            if (resp instanceof HBox rh && !rh.getChildren().isEmpty()) h.getChildren().add(resp);
            h.getChildren().add(Ui.botao("Ver jogador", () -> hub.abrirFichaJogadorPorId(pj.jogadorId), "fantasma"));
            if (temClube) h.getChildren().add(Ui.botao("Abrir transferências", () -> {
                PainelTransferencias.abrirAba("recebida".equals(pj.direcao) || pj.direcao == null ? "recebidas" : "feitas");
                hub.selecionar("transferencias");
            }, "fantasma"));
        } else {
            String t = m.tipo == null ? "" : m.tipo;
            switch (t) {
                case "mercado", "proposta" -> {
                    if (temClube) h.getChildren().add(Ui.botao("Abrir transferências", () -> hub.selecionar("transferencias")));
                }
                case "financas" -> {
                    if (temClube) h.getChildren().add(Ui.botao("Abrir finanças", () -> hub.selecionar("financas")));
                }
                case "jogador" -> {
                    if (temClube) h.getChildren().add(Ui.botao("Abrir elenco", () -> hub.selecionar("elenco")));
                }
                case "diretoria", "torcida" -> {
                    if (temClube) h.getChildren().add(Ui.botao("Abrir clube", () -> hub.selecionar("clube")));
                }
                case "resultado" -> h.getChildren().add(Ui.botao("Ver campeonatos", () -> hub.selecionar("tabelas")));
                case "selecao" -> h.getChildren().add(Ui.botao("Abrir seleções", () -> hub.selecionar("selecoes")));
                case "kings" -> h.getChildren().add(Ui.botao("Abrir Kings League", () -> hub.selecionar("kings")));
                default -> {
                }
            }
        }
        return h.getChildren().isEmpty() ? null : h;
    }
}
