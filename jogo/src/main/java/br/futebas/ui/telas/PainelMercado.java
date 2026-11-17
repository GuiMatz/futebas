package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.ia.PropostaJogador;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Leilao;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.StatusJogador;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.TreeSet;

/** Mercado: busca no mundo inteiro com filtros e leilões. */
final class PainelMercado implements Hub.Painel {
    private final Hub hub;
    private final ComboBox<String> posicao = new ComboBox<>();
    private final Spinner<Integer> forcaMin = new Spinner<>(1, 99, 55);
    private final Spinner<Integer> forcaMax = new Spinner<>(1, 99, 99);
    private final Spinner<Integer> idadeMax = new Spinner<>(15, 45, 45); // padrão: idade máxima (regra do jogo)
    private final TextField precoMax = new TextField();
    private final TextField nome = new TextField();
    private final ComboBox<String> origem = new ComboBox<>();
    /** Liga: país do clube do jogador (sem clube: a nacionalidade). */
    private final ComboBox<String> pais = new ComboBox<>();
    /** Nacionalidade do jogador (país principal ou o segundo). */
    private final ComboBox<String> nacionalidade = new ComboBox<>();
    private final TableView<Jogador> resultados = Ui.tabela();
    private final Label contagem = Ui.rotulo("", "texto-suave");
    private final ScrollPane detalhe = new ScrollPane();
    private static int abaAntes;

    PainelMercado(Hub hub) {
        this.hub = hub;
    }

    /** Abre o mercado numa aba (0 busca, 1 leilões). */
    static void abrirAba(Hub hub, int aba) {
        abaAntes = aba;
        hub.selecionar("mercado");
    }

    @Override
    public Node construir() {
        TabPane abas = new TabPane();
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Clube c = hub.clube();
        if (c == null && hub.jogo().meuTimeKings() != null) return kings(abas, hub.jogo().meuTimeKings());
        int leiloes = hub.jogo().leiloesAbertos().size();
        abas.getTabs().addAll(Ui.abaSobDemanda("Buscar jogadores", this::busca), Ui.abaSobDemanda("Leilões" + (leiloes > 0 ? " (" + leiloes + ")" : ""), this::leiloes));
        abas.getSelectionModel().select(Math.min(abaAntes, 1));
        Ui.ativarSobDemanda(abas);
        abas.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> abaAntes = b.intValue());
        HBox cab = Ui.linha(16, Ui.titulo("Mercado"), Ui.espaco(), Ui.dado("Em caixa", Textos.dinheiro(c.dinheiro)),
                Ui.dado("Elenco", hub.jogo().elenco(c.id).size() + " jogadores"));
        VBox v = new VBox(12, cab, abas);
        VBox.setVgrow(abas, Priority.ALWAYS);
        v.setStyle("-fx-padding: 20 24 16 24;");
        return v;
    }

    private static javafx.scene.control.ListCell<String> celulaPais() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String s, boolean vazio) {
                super.updateItem(s, vazio);
                if (vazio || s == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                boolean codigo = s.length() == 3;
                setText(codigo ? Textos.pais(s) : s);
                setGraphic(codigo ? br.futebas.ui.comp.Imagens.bandeira(s, 12) : null);
                setGraphicTextGap(7);
            }
        };
    }

    // ------------------------------------------------------------------ busca
    private Node busca() {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        posicao.getItems().setAll("Todas as posições");
        for (Posicao p : Posicao.values()) posicao.getItems().add(p.name() + " · " + p.nome);
        posicao.getSelectionModel().select(0);
        origem.getItems().setAll("Qualquer situação", "Passe livre (sem clube)", "No meu país", "No exterior", "À venda", "Disponíveis para empréstimo");
        origem.getSelectionModel().select(0);
        java.text.Collator col = java.text.Collator.getInstance(new java.util.Locale("pt", "BR"));
        col.setStrength(java.text.Collator.PRIMARY);
        TreeSet<String> ps = new TreeSet<>((a, b) -> {
            int c = col.compare(Textos.pais(a), Textos.pais(b));
            return c != 0 ? c : a.compareTo(b);
        });
        for (Clube x : m.clubes.values()) if (!x.virtual) ps.add(x.pais);
        // nacionalidades: códigos distintos primeiro (HashSet), depois a ordem por nome; antes cada um dos
        // ~200 mil jogadores entrava num TreeSet com o Collator e o menu levava ~0,5 s para abrir
        TreeSet<String> nacs = new TreeSet<>(ps.comparator());
        nacs.addAll(nacionalidades(m));
        pais.getItems().setAll("Todas as ligas");
        pais.getItems().addAll(ps);
        nacionalidade.getItems().setAll("Todas");
        nacionalidade.getItems().addAll(nacs);
        for (ComboBox<String> cb : List.of(pais, nacionalidade)) {
            cb.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(String s) { return s == null ? "" : s.length() == 3 ? Textos.pais(s) : s; }
                @Override public String fromString(String s) { return s; }
            });
            // bandeira ao lado do nome, na lista e no botão
            cb.setCellFactory(x -> celulaPais());
            cb.setButtonCell(celulaPais());
            cb.getSelectionModel().select(0);
            cb.setVisibleRowCount(14);
            cb.setPrefWidth(170);
        }
        javafx.scene.control.Tooltip.install(pais, new javafx.scene.control.Tooltip("País da liga em que o jogador joga"));
        javafx.scene.control.Tooltip.install(nacionalidade, new javafx.scene.control.Tooltip("Nacionalidade do jogador"));
        forcaMin.setPrefWidth(76);
        forcaMax.setPrefWidth(76);
        idadeMax.setPrefWidth(76);
        forcaMin.setEditable(true);
        forcaMax.setEditable(true);
        idadeMax.setEditable(true);
        precoMax.setPromptText("qualquer");
        precoMax.setPrefWidth(110);
        nome.setPromptText("Nome do jogador");
        nome.setPrefWidth(170);
        nome.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) buscar();
        });
        Button buscar = Ui.botao("Buscar", this::buscar, "primario");
        Button limpar = Ui.botao("Limpar", this::limparFiltros);
        FlowPane filtros = new FlowPane(10, 8, campo("Nome", nome), campo("Posição", posicao), campo("Liga", pais), campo("Nacionalidade", nacionalidade),
                campo("Situação", origem), campo("Força mín.", forcaMin), campo("Força máx.", forcaMax), campo("Idade máx.", idadeMax),
                campo("Preço máx. (R$)", precoMax), buscar, limpar);
        filtros.setAlignment(Pos.BOTTOM_LEFT);

        Ui.colPos(resultados, x -> x.posicao);
        Ui.colNo(resultados, "Jogador", 190, x -> {
            HBox h = Ui.linha(7, Imagens.bandeira(x.pais, 11), Ui.rotulo(x.nomeExibicao()));
            if (x.estrela) h.getChildren().add(Ui.etiqueta("★", "ambar"));
            if (x.aVenda) h.getChildren().add(Ui.etiqueta("LT", "azul"));
            if (x.aEmprestimo) h.getChildren().add(Ui.etiqueta("LE", "azul"));
            return h;
        });
        Ui.colNum(resultados, "Idade", 52, x -> x.idade(j.ano()));
        Ui.colForca(resultados, "Força", Jogador::forcaExibida);
        // temporada: jogos, gols, assistências e nota média (como no elenco)
        Ui.colNum(resultados, "J", 38, x -> x.jogosTemp);
        Ui.colNum(resultados, "G", 38, x -> x.golsTemp);
        Ui.colNum(resultados, "A", 38, x -> x.assistTemp);
        var colNota = Ui.colNo(resultados, "Nota", 48, x -> Ui.rotulo(x.jogosTemp == 0 ? "—" : String.format(Locale.ROOT, "%.1f", x.notaMediaTemp()),
                x.jogosTemp == 0 ? "texto-suave" : "texto-forte"));
        colNota.setComparator((a, b) -> Double.compare(a.notaMediaTemp(), b.notaMediaTemp()));
        colNota.setSortable(true);
        Ui.colNo(resultados, "Clube", 190, x -> {
            Clube cl = m.clube(x.clubeId);
            if (cl == null || x.status == StatusJogador.LIVRE) return Ui.etiqueta("Sem clube", "verde");
            return Ui.clube(m, cl.id, 18);
        });
        // preço e salário guardados por busca: ordenar pela coluna pede o valor de cada jogador várias vezes
        Ui.colDinheiro(resultados, "Preço", 100, x -> precos.computeIfAbsent(x.id, j::precoPedido));
        Ui.colDinheiro(resultados, "Salário", 96, x -> salarios.computeIfAbsent(x.id, j::salarioPedido));
        resultados.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b == null) detalhe.setContent(null);
            else {
                FlowPane acoes = new FlowPane(6, 6, Ui.botao("Fazer proposta", () -> propor(b), "primario"));
                if (j.taxaEmprestimo(b.id) > 0) acoes.getChildren().add(Ui.botao("Pedir emprestado", () -> pedirEmprestado(b)));
                detalhe.setContent(FichaJogador.construir(j, b, acoes));
            }
        });
        resultados.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && resultados.getSelectionModel().getSelectedItem() != null) propor(resultados.getSelectionModel().getSelectedItem());
        });
        resultados.setPlaceholder(semResultado);
        detalhe.setFitToWidth(true);
        detalhe.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detalhe.setPrefWidth(360);
        detalhe.setMinWidth(330);
        VBox tab = Ui.painelTabela(null, contagem, resultados);
        VBox centro = new VBox(12, filtros, tab);
        VBox.setVgrow(tab, Priority.ALWAYS);
        BorderPane bp = new BorderPane(centro);
        bp.setRight(detalhe);
        BorderPane.setMargin(detalhe, new javafx.geometry.Insets(0, 0, 0, 14));
        restaurarFiltros(); // os filtros ficam como você deixou até clicar em Limpar (regra do jogo)
        buscar();
        return bp;
    }

    private static java.util.Set<String> nacsCache;
    private static Mundo nacsMundo;
    private static int nacsTotal = -1;

    /** Nacionalidades dos jogadores do mundo (guardadas enquanto o número de jogadores não muda). */
    private static synchronized java.util.Set<String> nacionalidades(Mundo m) {
        if (nacsCache != null && nacsMundo == m && nacsTotal == m.jogadores.size()) return nacsCache;
        java.util.Set<String> s = new java.util.HashSet<>();
        for (Jogador x : m.jogadores) if (x.pais != null && x.pais.length() == 3) s.add(x.pais);
        nacsCache = s;
        nacsMundo = m;
        nacsTotal = m.jogadores.size();
        return s;
    }

    private static VBox campo(String rotulo, Node n) {
        return new VBox(2, Ui.rotulo(rotulo, "texto-pequeno"), n);
    }

    /** Volta todos os filtros da busca ao padrão e busca de novo. */
    private void limparFiltros() {
        nome.clear();
        posicao.getSelectionModel().select(0);
        pais.getSelectionModel().select(0);
        nacionalidade.getSelectionModel().select(0);
        origem.getSelectionModel().select(0);
        forcaMin.getValueFactory().setValue(1);
        forcaMax.getValueFactory().setValue(99);
        idadeMax.getValueFactory().setValue(45);
        precoMax.clear();
        buscar();
    }

    /** Filtros da busca guardados entre as visitas ao mercado (só o Limpar volta ao padrão). */
    private static String fNome = "", fPais, fNac, fPreco = "";
    private static int fPos, fOrigem, fMin = 55, fMax = 99, fIdade = 45;
    private static boolean fGuardado;

    private void guardarFiltros() {
        fGuardado = true;
        fNome = nome.getText() == null ? "" : nome.getText();
        fPos = Math.max(0, posicao.getSelectionModel().getSelectedIndex());
        fPais = pais.getValue();
        fNac = nacionalidade.getValue();
        fOrigem = Math.max(0, origem.getSelectionModel().getSelectedIndex());
        fMin = forcaMin.getValue();
        fMax = forcaMax.getValue();
        fIdade = idadeMax.getValue();
        fPreco = precoMax.getText() == null ? "" : precoMax.getText();
    }

    private void restaurarFiltros() {
        if (!fGuardado) return;
        nome.setText(fNome);
        posicao.getSelectionModel().select(Math.min(fPos, posicao.getItems().size() - 1));
        if (fPais != null && pais.getItems().contains(fPais)) pais.setValue(fPais);
        if (fNac != null && nacionalidade.getItems().contains(fNac)) nacionalidade.setValue(fNac);
        origem.getSelectionModel().select(Math.min(fOrigem, origem.getItems().size() - 1));
        forcaMin.getValueFactory().setValue(fMin);
        forcaMax.getValueFactory().setValue(fMax);
        idadeMax.getValueFactory().setValue(fIdade);
        precoMax.setText(fPreco);
    }

    private final Map<Integer, Long> precos = new HashMap<>(), salarios = new HashMap<>();

    private void buscar() {
        guardarFiltros();
        precos.clear();
        salarios.clear();
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        Posicao pos = posicao.getSelectionModel().getSelectedIndex() <= 0 ? null : Posicao.values()[posicao.getSelectionModel().getSelectedIndex() - 1];
        long preco = PainelElenco.numero(precoMax.getText());
        String q = nome.getText() == null ? "" : nome.getText().trim().toLowerCase(Locale.ROOT);
        int filtroOrigem = Math.max(0, origem.getSelectionModel().getSelectedIndex());
        String filtroPais = pais.getSelectionModel().getSelectedIndex() <= 0 ? null : pais.getValue();
        String filtroNac = nacionalidade.getSelectionModel().getSelectedIndex() <= 0 ? null : nacionalidade.getValue();
        int fmin = forcaMin.getValue(), fmax = forcaMax.getValue(), imax = idadeMax.getValue();
        String meu = hub.clube().id, meuPais = hub.clube().pais;
        int ano = j.ano();
        // a busca roda fora da thread da tela (são ~200 mil jogadores com o banco completo): a tabela
        // mostra "Buscando…" e recebe o resultado da busca mais recente; uma cópia da lista evita
        // mexer na lista do mundo enquanto ela anda
        Jogador[] todos = m.jogadores.toArray(new Jogador[0]);
        int seq = ++buscaSeq;
        contagem.setText("Buscando…");
        resultados.getItems().clear();
        resultados.setPlaceholder(buscando);
        BUSCA.execute(() -> {
            List<Jogador> l = new ArrayList<>();
            Map<Integer, Long> pr = new HashMap<>();
            try {
                for (Jogador x : todos) {
                    if (seq != buscaSeq) return; // já há uma busca mais nova
                    if (x.amador() || x.status == StatusJogador.APOSENTADO || x.status == StatusJogador.DISPENSADO || x.status == StatusJogador.BASE) continue;
                    if (meu.equals(x.clubeId)) continue;
                    if (pos != null && x.posicao != pos) continue;
                    if (x.forca < fmin || x.forca > fmax || x.idade(ano) > imax) continue;
                    if (!q.isEmpty() && !x.nomeExibicao().toLowerCase(Locale.ROOT).contains(q) && (x.nome == null || !x.nome.toLowerCase(Locale.ROOT).contains(q))) continue;
                    Clube cl = m.clube(x.clubeId);
                    if (filtroPais != null && (cl == null ? !filtroPais.equals(x.pais) : !filtroPais.equals(cl.pais))) continue;
                    if (filtroNac != null && !filtroNac.equals(x.pais) && !filtroNac.equals(x.pais2)) continue;
                    switch (filtroOrigem) {
                        case 1 -> { if (x.status != StatusJogador.LIVRE || x.kingsTimeId != null) continue; }
                        case 2 -> { if (cl == null || !meuPais.equals(cl.pais)) continue; }
                        case 3 -> { if (cl == null || meuPais.equals(cl.pais)) continue; }
                        case 4 -> { if (!x.aVenda) continue; }
                        case 5 -> { if (!x.aEmprestimo || j.taxaEmprestimo(x.id) <= 0) continue; }
                        default -> {}
                    }
                    if (preco > 0 && pr.computeIfAbsent(x.id, j::precoPedido) > preco) continue;
                    l.add(x);
                }
                l.sort(Comparator.comparingDouble((Jogador x) -> -x.forca).thenComparingInt(x -> x.id));
            } catch (RuntimeException e) {
                // o mundo mudou durante a busca (semana avançando): fica a lista que deu para montar
            }
            javafx.application.Platform.runLater(() -> {
                if (seq != buscaSeq) return;
                precos.putAll(pr);
                // todos os resultados (a tabela é virtualizada): time pequeno também precisa achar os mais fracos
                resultados.setPlaceholder(semResultado);
                resultados.getItems().setAll(l);
                int total = l.size();
                contagem.setText(Textos.milhar(total) + (total == 1 ? " jogador" : " jogadores"));
                if (!l.isEmpty()) resultados.getSelectionModel().select(0);
            });
        });
    }

    /** Uma busca de cada vez, fora da thread da tela. */
    private static final java.util.concurrent.ExecutorService BUSCA = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "futebas-busca-mercado");
        t.setDaemon(true);
        return t;
    });
    private volatile int buscaSeq;
    private final Node buscando = buscando();
    private final Node semResultado = Ui.rotulo("Nenhum jogador com esses filtros. Afrouxe a força mínima ou o preço.", "texto-suave");

    private static Node buscando() {
        javafx.scene.control.ProgressIndicator pi = new javafx.scene.control.ProgressIndicator();
        pi.setPrefSize(28, 28);
        return Ui.linha(10, pi, Ui.rotulo("Buscando jogadores…", "texto-suave"));
    }

    void propor(Jogador x) {
        propor(hub, x);
    }

    /** A proposta do Mercado, também aberta pela página do jogador. */
    static void propor(Hub hub, Jogador x) {
        if (x.status == StatusJogador.LIVRE || x.clubeId == null) {
            contratarLivre(hub, x);
            return;
        }
        if (Ui.semDialogos) return;
        Jogo j = hub.jogo();
        DialogoNegocio.Montagem mt = DialogoNegocio.montar(j, hub.clube(), x, DialogoNegocio.Tipo.COMPRA);
        if (!DialogoNegocio.mostrar(mt, "Enviar proposta")) return;
        int mm = mt.meses();
        long oferta = mt.oferta.valor();
        long sal = mt.salario.valor();
        // sem segunda confirmação (regra do jogo): o botão do diálogo já envia, com o salário
        Jogo.ResultadoNegociacao res = j.proporCompra(x.id, oferta, mm, sal > 0 ? sal : null);
        Ui.informar(res.aceito() ? "Proposta enviada" : "Proposta não enviada", res.mensagem());
        if (res.aceito()) hub.atualizar();
    }

    /**
     * Jogador sem contrato: não há clube para negociar, então é uma janela só (salário e duração)
     * e ele assina na hora, ou recusa. Antes a proposta parava em "acerte o contrato" na aba Transferências.
     */
    static void contratarLivre(Hub hub, Jogador x) {
        Jogo j = hub.jogo();
        DialogoNegocio.Montagem mt = DialogoNegocio.montar(j, hub.clube(), x, DialogoNegocio.Tipo.LIVRE);
        if (!DialogoNegocio.mostrar(mt, "Contratar")) return;
        int mm = mt.meses();
        var sal = mt.salario;
        Jogo.ResultadoNegociacao res = j.proporCompra(x.id, 0, mm);
        if (!res.aceito()) {
            Ui.informar("Contratação", res.mensagem());
            return;
        }
        var p = j.negociacaoPor(x.id);
        String msg = p == null ? res.mensagem() : j.proporContrato(p.id, sal.valor(), mm);
        if (p != null && "aguardando_confirmacao".equals(p.estado)) msg = j.confirmarNegocio(p.id);
        Ui.informar("Contratação", msg);
        hub.atualizar();
    }

    void pedirEmprestado(Jogador x) {
        pedirEmprestado(hub, x);
    }

    static void pedirEmprestado(Hub hub, Jogador x) {
        Jogo j = hub.jogo();
        if (!Ui.semDialogos && !DialogoNegocio.mostrar(DialogoNegocio.montar(j, hub.clube(), x, DialogoNegocio.Tipo.EMPRESTIMO), "Pedir emprestado")) return;
        Jogo.ResultadoNegociacao r = j.pedirEmprestado(x.id);
        Ui.informar(r.aceito() ? "Pedido enviado" : "Pedido não enviado", r.mensagem());
        if (r.aceito()) hub.atualizar();
    }

    // ------------------------------------------------------------------ Kings League
    /** Máximo de atletas no elenco da Kings: 12 (6 wildcards + 6 draftados, contando os presidentes que jogam). */
    static int maximoKings() { return br.futebas.kings.Kings.MAX_ELENCO; }

    /**
     * O mesmo Mercado para o time da Kings: busca com filtros, tabela e cartão do jogador ao lado.
     * Na Kings não há leilão (aba fora); contrata quem está sem time na Kings e é
     * elegível na liga (passe livre, divisões de baixo, veteranos, aposentados). Cada proposta custa a
     * taxa ao clube (se ele tem contrato) + o salário da temporada, e só sai se o caixa livre cobrir
     * (regra do jogo, 12/11/2026: dinheiro da Kings).
     */
    private Node kings(TabPane abas, br.futebas.kings.LigaKings.TimeKings tk) {
        Jogo j = hub.jogo();
        abas.getTabs().add(new Tab("Buscar jogadores", buscaKings(tk)));
        HBox cab = Ui.linha(16, Ui.titulo("Mercado"), Ui.espaco(), Ui.dado("Caixa livre", Textos.dinheiro(j.caixaKingsDisponivel())),
                Ui.dado("Elenco", tk.elenco.size() + " de " + maximoKings() + " jogadores"),
                Ui.dado("Wildcards", j.vagasWildcardKings()[0] + " vaga(s) aberta(s) · " + j.vagasWildcardKings()[1] + " proposta(s)"));
        VBox v = new VBox(12, cab);
        if (tk.elenco.size() >= maximoKings())
            v.getChildren().add(Ui.aviso("Elenco cheio (" + maximoKings() + "): dispense alguém no Elenco para contratar.", "aviso",
                    Ui.botao("Elenco", () -> hub.selecionar("elenco"))));
        v.getChildren().add(abas);
        VBox.setVgrow(abas, Priority.ALWAYS);
        v.setStyle("-fx-padding: 20 24 16 24;");
        return v;
    }

    private Node buscaKings(br.futebas.kings.LigaKings.TimeKings tk) {
        Jogo j = hub.jogo();
        posicao.getItems().setAll("Todas as posições");
        for (Posicao p : Posicao.values()) posicao.getItems().add(p.name() + " · " + p.nome);
        posicao.getSelectionModel().select(0);
        origem.getItems().setAll("Qualquer situação", "Passe livre", "Aposentado", "Divisão inferior", "Ex-elite");
        origem.getSelectionModel().select(0);
        forcaMin.getValueFactory().setValue(40);
        idadeMax.getValueFactory().setValue(45);
        forcaMin.setPrefWidth(76);
        idadeMax.setPrefWidth(76);
        forcaMin.setEditable(true);
        idadeMax.setEditable(true);
        nome.setPromptText("Nome do jogador");
        nome.setPrefWidth(170);
        nome.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) buscarKings();
        });
        Button buscar = Ui.botao("Buscar", this::buscarKings, "primario");
        FlowPane filtros = new FlowPane(10, 8, nome, posicao, origem, campo("Força mín.", forcaMin), campo("Idade máx.", idadeMax), buscar);
        filtros.setAlignment(Pos.BOTTOM_LEFT);
        Ui.colPos(resultados, x -> x.posicao);
        Ui.colNo(resultados, "Jogador", 190, x -> {
            HBox h = Ui.linha(7, Imagens.bandeira(x.pais, 11), Ui.rotulo(x.nomeExibicao()));
            if (x.estrela) h.getChildren().add(Ui.etiqueta("★", "ambar"));
            return h;
        });
        Ui.colNum(resultados, "Idade", 52, x -> x.idade(j.ano()));
        Ui.colForca(resultados, "Força", Jogador::forcaExibida);
        Ui.col(resultados, "Situação", 200, j::origemKings);
        Ui.colNum(resultados, "Custo (mil)", 86, x -> j.custoWildcardKings(x.id) / 1000);
        boolean cheio = tk.elenco.size() >= maximoKings();
        resultados.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b == null) detalhe.setContent(null);
            else {
                long custo = j.custoWildcardKings(b.id);
                boolean semCaixa = custo > j.caixaKingsDisponivel();
                Button contratar = Ui.botao("Fazer proposta · " + Textos.dinheiro(custo), () -> contratarKings(tk, b), "primario");
                contratar.setDisable(cheio || semCaixa);
                if (semCaixa) javafx.scene.control.Tooltip.install(contratar, new javafx.scene.control.Tooltip("Caixa livre insuficiente ("
                        + Textos.dinheiro(j.caixaKingsDisponivel()) + ")."));
                FlowPane acoes = new FlowPane(6, 6, contratar, Ui.botao("Prioridades do draft", () -> {
                    Hub.abaKings("draft");
                    hub.selecionar("kings");
                }));
                detalhe.setContent(FichaJogador.construir(j, b, acoes));
            }
        });
        resultados.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !cheio && resultados.getSelectionModel().getSelectedItem() != null)
                contratarKings(tk, resultados.getSelectionModel().getSelectedItem());
        });
        resultados.setPlaceholder(Ui.rotulo("Nenhum jogador elegível com esses filtros.", "texto-suave"));
        detalhe.setFitToWidth(true);
        detalhe.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detalhe.setPrefWidth(360);
        detalhe.setMinWidth(330);
        VBox tab = Ui.painelTabela(null, contagem, resultados);
        VBox centro = new VBox(12, filtros, tab);
        VBox.setVgrow(tab, Priority.ALWAYS);
        BorderPane bp = new BorderPane(centro);
        bp.setRight(detalhe);
        BorderPane.setMargin(detalhe, new javafx.geometry.Insets(0, 0, 0, 14));
        buscarKings();
        return bp;
    }

    private void buscarKings() {
        Jogo j = hub.jogo();
        Posicao pos = posicao.getSelectionModel().getSelectedIndex() <= 0 ? null : Posicao.values()[posicao.getSelectionModel().getSelectedIndex() - 1];
        String q = nome.getText() == null ? "" : nome.getText().trim().toLowerCase(Locale.ROOT);
        String filtroOrigem = origem.getSelectionModel().getSelectedIndex() <= 0 ? null : origem.getValue();
        int fmin = forcaMin.getValue(), imax = idadeMax.getValue();
        List<Jogador> todos = j.mercadoKings(Integer.MAX_VALUE);
        List<Jogador> l = new ArrayList<>();
        for (Jogador x : todos) {
            if (pos != null && x.posicao != pos) continue;
            if (x.forca < fmin || x.idade(j.ano()) > imax) continue;
            if (!q.isEmpty() && !x.nomeExibicao().toLowerCase(Locale.ROOT).contains(q) && (x.nome == null || !x.nome.toLowerCase(Locale.ROOT).contains(q))) continue;
            if (filtroOrigem != null && !j.origemKings(x).startsWith(filtroOrigem)) continue;
            l.add(x);
        }
        l.sort(Comparator.comparingDouble((Jogador x) -> -x.forca).thenComparingInt(x -> x.id));
        int total = l.size();
        if (l.size() > 400) l = new ArrayList<>(l.subList(0, 400));
        resultados.getItems().setAll(l);
        contagem.setText(total > l.size() ? "Mostrando os " + l.size() + " mais fortes de " + Textos.milhar(total) : Textos.milhar(total) + " jogadores elegíveis");
        if (!l.isEmpty()) resultados.getSelectionModel().select(0);
    }

    private void contratarKings(br.futebas.kings.LigaKings.TimeKings tk, Jogador x) {
        Jogo j = hub.jogo();
        boolean contrato = x.contrato != null && x.clubeId != null;
        long[] partes = j.partesWildcardKings(x.id);
        long custo = partes[0] + partes[1];
        if (custo > j.caixaKingsDisponivel()) {
            Ui.informar("Proposta de wildcard", "O caixa livre do " + tk.nome + " (" + Textos.dinheiro(j.caixaKingsDisponivel()) + ") não cobre "
                    + Textos.dinheiro(custo) + " (taxa + salário da temporada).");
            return;
        }
        if (!Ui.confirmar("Proposta de wildcard", "Fazer proposta a " + x.nomeExibicao() + " para ser wildcard do " + tk.nome + "?"
                + (contrato ? " Ele tem contrato: primeiro o clube dele responde (taxa de " + Textos.dinheiro(partes[0]) + "), depois o jogador."
                : " Ele está sem contrato: a proposta vai direto a ele.")
                + " Salário da temporada: " + Textos.dinheiro(partes[1]) + ". Total de " + Textos.dinheiro(custo)
                + ", reservado no caixa até a resposta (em até 7 dias).")) return;
        if (!j.contratarNaKings(x.id)) Ui.informar("Proposta de wildcard", "Não foi possível: elenco cheio (com as propostas em andamento), proposta já feita, caixa insuficiente ou jogador fora das regras da liga.");
        else Ui.informar("Proposta de wildcard", "Proposta enviada a " + x.nomeExibicao() + ". A resposta chega na caixa de entrada.");
        hub.atualizar();
    }

    // ------------------------------------------------------------------ leilões
    private Node leiloes() {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        List<Leilao> ls = j.leiloesAbertos();
        VBox v = new VBox(12);
        if (ls.isEmpty()) {
            v.getChildren().add(Ui.painelAcoes(null, null, Ui.vazio("Nenhum leilão aberto", null, null)));
            return Ui.rolar(v);
        }

        for (Leilao l : ls) {
            Jogador x = m.jogador(l.jogadorId);
            if (x == null) continue;
            Clube vend = m.clube(l.vendedorId);
            boolean meu = hub.clube().id.equals(l.compradorId);
            br.futebas.ui.comp.CampoDinheiro cl = new br.futebas.ui.comp.CampoDinheiro(
                    Math.max(l.lanceMinimo, l.maiorLance + Math.max(10_000, l.maiorLance / 10)), 0, 0);
            TextField lance = cl.campo();
            Button dar = Ui.botao("Dar lance", () -> {
                Jogo.ResultadoNegociacao r = j.darLance(l.id, PainelElenco.numero(lance.getText()));
                Ui.informar(r.aceito() ? "Lance registrado" : "Lance recusado", r.mensagem());
                hub.atualizar();
            }, "primario");
            VBox id = new VBox(3, Ui.linha(8, Ui.pos(x.posicao), Ui.rotulo(x.nomeExibicao(), "titulo-painel"), Ui.forca(x.forcaExibida())),
                    Ui.linha(8, vend == null ? new Label() : Ui.escudo(vend.id, 16), vend == null ? new Label() : Ui.nomeClube(vend.id, vend.nome, "texto-suave"), Ui.rotulo((vend == null ? "" : "· ") + x.idade(j.ano()) + " anos · valor "
                            + Textos.dinheiro(j.valorDeMercado(x.id)), "texto-suave")));
            HBox.setHgrow(id, Priority.ALWAYS);
            VBox lances = new VBox(2, Ui.rotulo("Lance mínimo " + Textos.dinheiro(l.lanceMinimo), "texto-pequeno"),
                    Ui.rotulo(l.maiorLance > 0 ? "Maior lance " + Textos.dinheiro(l.maiorLance) : "Sem lances", "texto-forte"));
            if (meu) lances.getChildren().add(Ui.etiqueta("Seu lance está na frente", "verde"));
            HBox h = Ui.linha(16, id, lances, cl, dar);
            v.getChildren().add(Ui.painelAcoes(null, null, h));
        }
        return Ui.rolar(v);
    }
}
