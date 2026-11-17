package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.Tela;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * Novo jogo em 1º/jan/2026: país (com bandeira) → liga → grade de escudos com força e
 * reputação. Embaixo: o clube escolhido, o nome do técnico, os países simulados em detalhe
 * (com um padrão sensato) e o botão de começar.
 */
public final class TelaNovoJogo implements Tela {
    private static final int PAISES_DETALHADOS = 12;

    private final BorderPane raiz = new BorderPane();
    private final TextField buscaPais = new TextField();
    private final ListView<Jogo.OpcaoPais> listaPaises = new ListView<>();
    private final ListView<Jogo.OpcaoLiga> listaLigas = new ListView<>();
    private final TextField buscaClube = new TextField();
    private final FlowPane grade = Ui.gradeFluida(150, 10);
    private final Label tituloGrade = Ui.rotulo("", "titulo-tela");
    private final Label subGrade = Ui.rotulo("", "texto-suave");
    private final TextField nomeTecnico = new TextField();
    private final TextField semente = new TextField();
    private final HBox escolhido = Ui.linha(10);
    private final Button detalhe = new Button();
    private final Button comecar = new Button();
    private final FlowPane listaDetalhe = new FlowPane(8, 8);
    // multi-treinador: os técnicos já adicionados (nome + clube OU time da Kings), na ordem das vezes
    private final List<Jogo.EscolhaTecnico> treinadores = new ArrayList<>();
    private final List<String> timesTreinadores = new ArrayList<>();
    private final Map<String, String> paisDoTreinador = new HashMap<>();
    private final FlowPane listaTreinadores = new FlowPane(8, 6);
    private final HBox barraTreinadores = new HBox(10);
    private final Button adicionar = new Button("+ Adicionar treinador");
    private List<Jogo.OpcaoPais> paises = new ArrayList<>();
    private List<Jogo.OpcaoClube> clubes = new ArrayList<>();
    private Jogo.OpcaoClube selecionado;
    /** Time da Kings League escolhido NO LUGAR de um clube (carreira exclusiva: ou clube, ou Kings). */
    private Db.KingsTime kingsEscolhido;
    /** Times customizados (Opções → Jogo): a Kings League IOS entra na escolha e no jogo novo. */
    private br.futebas.kings.KingsCustom.Definicao custom;
    private Db.KingsLiga customLiga;
    private final List<Db.KingsTime> customTimes = new ArrayList<>();

    private List<Db.KingsLiga> kingsLigas() {
        List<Db.KingsLiga> l = new ArrayList<>(db.kingsLigas.values());
        if (customLiga != null) l.add(customLiga);
        return l;
    }

    private List<Db.KingsTime> kingsTimes() {
        List<Db.KingsTime> l = new ArrayList<>(db.kingsTimes.values());
        l.addAll(customTimes);
        return l;
    }

    /** Capturas: escolhe o país e a liga (ids do seletor, ex.: "KINGS", "kings:kings-ios"). */
    public void selecionarLiga(String paisId, String ligaId) {
        for (Jogo.OpcaoPais p : listaPaises.getItems()) if (p.id().equals(paisId)) listaPaises.getSelectionModel().select(p);
        for (Jogo.OpcaoLiga l : listaLigas.getItems()) if (l.id().equals(ligaId)) listaLigas.getSelectionModel().select(l);
    }

    /** Capturas/testes: escolhe um time da Kings (no lugar do clube) e mostra a barra de baixo. */
    public void selecionarTimeKings(String timeId) {
        if (db == null) return; // o banco ainda está abrindo
        for (Db.KingsTime kt : kingsTimes()) if (kt.id().equals(timeId)) {
            kingsEscolhido = kt;
            selecionado = null;
            if (cartaoSelecionado != null) cartaoSelecionado.getStyleClass().remove("selecionado");
            cartaoSelecionado = null;
            Jogo.OpcaoLiga lg = listaLigas.getSelectionModel().getSelectedItem();
            if (lg != null && lg.id().startsWith("kings:")) montarGradeKings(lg);
            mostrarEscolhido();
        }
    }

    /** Clube ou time da Kings escolhido (o que vale para o "Começar"); null se nada. */
    public String escolhaAtual() {
        return kingsEscolhido != null ? kingsEscolhido.id() : selecionado != null ? selecionado.id() : null;
    }

    /** Definição dos times customizados lida na abertura da tela (null se a opção estiver desligada). */
    public br.futebas.kings.KingsCustom.Definicao custom() { return custom; }

    private void lerCustom() {
        custom = null;
        customLiga = null;
        customTimes.clear();
        if (!br.futebas.ui.Opcoes.timesCustomizados()) return;
        java.nio.file.Path f = br.futebas.kings.KingsCustom.localizar(br.futebas.ui.Caminhos.raiz());
        if (f == null) return;
        try {
            custom = br.futebas.kings.KingsCustom.carregar(f);
        } catch (RuntimeException e) {
            custom = null;
            return;
        }
        customLiga = new Db.KingsLiga(custom.id, custom.nome, null, custom.inicio, custom.regrasJson, custom.draftJson, "custom", null);
        for (br.futebas.kings.KingsCustom.Time t : custom.times)
            customTimes.add(new Db.KingsTime(t.id, custom.id, t.nome, t.presidente, t.escudo, t.cor1, t.cor2, "custom"));
    }
    private Node cartaoSelecionado;
    private final Map<String, Node> cartoes = new HashMap<>();
    /** Onde a grade fica: uma grade só, ou várias (estaduais por divisão). */
    private final VBox areaGrade = new VBox(4);
    private Set<String> detalhados = new LinkedHashSet<>();
    private boolean detalheMexido;
    private Map<String, Integer> forcas = Map.of();
    private BancoMundo db;

    public TelaNovoJogo() {
        Button voltar = Ui.botaoTecla("Voltar", "Esc", () -> App.atual().irParaInicio());
        raiz.setTop(Ui.cabecalhoTela(null, "Novo jogo",
                "1º de janeiro de 2026  ·  escolha o país, a liga e o clube (ou um time da Kings League)",
                null, null, voltar));

        nomeTecnico.setText(System.getProperty("user.name", "Técnico"));
        nomeTecnico.setPrefWidth(180);
        semente.setPromptText("aleatória");
        semente.setPrefWidth(96);
        detalhe.setFocusTraversable(false);
        detalhe.setOnAction(e -> escolherDetalhe());
        comecar.getStyleClass().add("jogar-grande");
        comecar.setOnAction(e -> comecar());
        comecar.setMinWidth(Region.USE_PREF_SIZE);
        adicionar.setOnAction(e -> adicionarTreinador());
        adicionar.setFocusTraversable(false);
        adicionar.setMinWidth(Region.USE_PREF_SIZE);
        javafx.scene.control.Tooltip.install(adicionar, new javafx.scene.control.Tooltip(
                "Vários técnicos no mesmo computador (até " + Jogo.MAX_TECNICOS + "): guarda este técnico e o time escolhido, e você escolhe o próximo"));

        montarCarreira();
    }

    // ------------------------------------------------------------------ montagem
    private void montarCarreira() {
        buscaPais.setPromptText("Buscar país");
        buscaPais.textProperty().addListener((o, a, b) -> filtrarPaises());
        listaPaises.setCellFactory(x -> new ListCell<>() {
            @Override
            protected void updateItem(Jogo.OpcaoPais p, boolean vazio) {
                super.updateItem(p, vazio);
                setText(null);
                if (vazio || p == null) {
                    setGraphic(null);
                    return;
                }
                Label n = Ui.rotulo(p.nome());
                n.setMinWidth(0);
                Node ic = "KINGS".equals(p.id()) ? coroa() : Imagens.bandeira(p.id(), 15);
                HBox h = Ui.linha(10, ic, n, Ui.espaco(), Ui.rotulo(Textos.milhar(p.clubes()), "item-escolha-meta"));
                setGraphic(h);
            }
        });
        listaPaises.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> carregarLigas(b));
        VBox colPais = new VBox(8, Ui.rotulo("País", "coluna-titulo"), buscaPais, listaPaises);
        VBox.setVgrow(listaPaises, Priority.ALWAYS);
        colPais.getStyleClass().add("coluna-escolha");
        colPais.setPrefWidth(250);
        colPais.setMinWidth(250);

        listaLigas.setCellFactory(x -> new ListCell<>() {
            @Override
            protected void updateItem(Jogo.OpcaoLiga l, boolean vazio) {
                super.updateItem(l, vazio);
                setText(null);
                if (vazio || l == null) {
                    setGraphic(null);
                    return;
                }
                Label n = Ui.rotulo(Textos.curto(l.nome()));
                n.setWrapText(true);
                n.setMinWidth(0);
                boolean div = l.divisao() != null && "nacional".equals(l.ambito()) && !"BRA".equals(paisAtual());
                String meta = "*".equals(l.id()) ? "do maior para o menor" : l.clubes() + " clubes" + (div ? "  ·  " + Textos.divisao(paisAtual(), l.divisao()) : "");
                VBox t = new VBox(1, n, Ui.rotulo(meta, "item-escolha-meta"));
                t.setMinWidth(0);
                HBox.setHgrow(t, Priority.ALWAYS);
                Node ic = "*".equals(l.id()) ? caixaVazia(22) : l.id().startsWith("kings:") ? Imagens.logoKings(l.id().substring(6), 26) : Imagens.trofeu(l.id(), l.ambito(), null, 26);
                HBox h = Ui.linha(10, ic, t);
                h.setMaxWidth(200);
                setGraphic(h);
            }
        });
        listaLigas.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> carregarClubes(b));
        VBox colLiga = new VBox(8, Ui.rotulo("Liga", "coluna-titulo"), listaLigas);
        VBox.setVgrow(listaLigas, Priority.ALWAYS);
        colLiga.getStyleClass().add("coluna-escolha");
        colLiga.setPrefWidth(250);
        colLiga.setMinWidth(250);

        buscaClube.setPromptText("Buscar clube");
        buscaClube.setPrefWidth(200);
        buscaClube.textProperty().addListener((o, a, b) -> montarGrade());
        grade.getStyleClass().add("grade-clubes");
        areaGrade.getChildren().setAll(grade);
        ScrollPane sp = Ui.rolar(areaGrade);
        HBox cab = Ui.linha(12, new VBox(0, tituloGrade, subGrade), Ui.espaco(), buscaClube);
        cab.setStyle("-fx-padding: 16 20 12 20;");
        VBox centro = new VBox(0, cab, sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
        HBox.setHgrow(centro, Priority.ALWAYS);

        HBox corpo = new HBox(colPais, colLiga, centro);
        raiz.setCenter(corpo);

        HBox barra = new HBox(14, escolhido, Ui.espaco(),
                Ui.dado("Seu nome", nomeTecnico), adicionar, Ui.dado("Países em detalhe", detalhe), comecar); // sem campo de semente (regra do jogo 13/11): sempre aleatória
        barra.getStyleClass().add("barra-comecar");
        barra.setAlignment(Pos.CENTER_LEFT);
        barraTreinadores.getChildren().setAll(Ui.rotulo("TREINADORES", "coluna-titulo"), listaTreinadores);
        barraTreinadores.setAlignment(Pos.CENTER_LEFT);
        barraTreinadores.getStyleClass().add("barra-comecar");
        HBox.setHgrow(listaTreinadores, Priority.ALWAYS);
        barraTreinadores.setVisible(false);
        barraTreinadores.setManaged(false);
        raiz.setBottom(new VBox(0, barraTreinadores, barra));
        mostrarEscolhido();
    }

    // ------------------------------------------------------------------ multi-treinador
    /** "Adicionar treinador": guarda o nome e o time escolhido e limpa a escolha para o próximo técnico. */
    private void adicionarTreinador() {
        String id = selecionado != null ? selecionado.id() : kingsEscolhido != null ? kingsEscolhido.id() : null;
        if (id == null) {
            Ui.informar("Escolha o time", "Escolha o clube (ou o time da Kings League) deste técnico antes de adicioná-lo.");
            return;
        }
        if (treinadores.size() >= Jogo.MAX_TECNICOS) {
            Ui.informar("Limite de técnicos", "O jogo aceita até " + Jogo.MAX_TECNICOS + " técnicos.");
            return;
        }
        for (Jogo.EscolhaTecnico e : treinadores) {
            if (id.equals(e.clubeId()) || id.equals(e.kingsTimeId())) {
                Ui.informar("Time já escolhido", "Outro técnico já escolheu este time.");
                return;
            }
        }
        String nome = nomeTecnico.getText() == null || nomeTecnico.getText().isBlank() ? "Técnico " + (treinadores.size() + 1) : nomeTecnico.getText().trim();
        for (Jogo.EscolhaTecnico e : treinadores) {
            if (e.nome().equalsIgnoreCase(nome)) {
                Ui.informar("Nome repetido", "Já há um técnico chamado " + nome + ". Use outro nome.");
                return;
            }
        }
        // carreira exclusiva (regra do jogo): clube OU time da Kings
        treinadores.add(new Jogo.EscolhaTecnico(nome, selecionado != null ? id : null, selecionado == null ? id : null));
        timesTreinadores.add(selecionado != null ? selecionado.nome() : kingsEscolhido.nome());
        if (selecionado != null) paisDoTreinador.put(id, selecionado.pais());
        selecionado = null;
        kingsEscolhido = null;
        if (cartaoSelecionado != null) cartaoSelecionado.getStyleClass().remove("selecionado");
        cartaoSelecionado = null;
        nomeTecnico.setText("Técnico " + (treinadores.size() + 1));
        mostrarTreinadores();
        mostrarEscolhido();
    }

    private void mostrarTreinadores() {
        listaTreinadores.getChildren().clear();
        for (int i = 0; i < treinadores.size(); i++) {
            Jogo.EscolhaTecnico e = treinadores.get(i);
            int k = i;
            String id = e.clubeId() != null ? e.clubeId() : e.kingsTimeId();
            Button tirar = new Button("✕");
            tirar.setFocusTraversable(false);
            tirar.setOnAction(x -> {
                treinadores.remove(k);
                timesTreinadores.remove(k);
                mostrarTreinadores();
                mostrarEscolhido();
            });
            HBox chip = Ui.linha(6, Ui.rotulo((i + 1) + ".", "texto-pequeno"), Imagens.escudo(id, 22), Ui.rotulo(e.nome(), "texto-forte"),
                    Ui.rotulo(timesTreinadores.get(i), "texto-pequeno"), tirar);
            chip.getStyleClass().add("cartao");
            chip.setStyle("-fx-padding: 4 8 4 8;");
            listaTreinadores.getChildren().add(chip);
        }
        boolean tem = !treinadores.isEmpty();
        barraTreinadores.setVisible(tem);
        barraTreinadores.setManaged(tem);
    }

    /** Capturas/testes: os técnicos já adicionados. */
    public List<Jogo.EscolhaTecnico> treinadores() { return List.copyOf(treinadores); }

    /** Capturas/testes: adiciona o técnico com o time escolhido agora. */
    public void adicionarTreinador(String nome) {
        nomeTecnico.setText(nome);
        adicionarTreinador();
    }

    private static Node coroa() {
        javafx.scene.shape.SVGPath c = new javafx.scene.shape.SVGPath();
        c.setContent("M2 13 L1 3 L6 7 L11 1 L16 7 L21 3 L20 13 Z");
        c.setStyle("-fx-fill: #f5c451;");
        javafx.scene.layout.StackPane s = new javafx.scene.layout.StackPane(c);
        s.setMinSize(23, 15);
        s.setPrefSize(23, 15);
        return s;
    }

    private String paisAtual() {
        Jogo.OpcaoPais p = listaPaises.getSelectionModel().getSelectedItem();
        return p == null ? null : p.id();
    }

    private static Region caixaVazia(double t) {
        Region r = new Region();
        r.setMinSize(t, t);
        r.setPrefSize(t, t);
        return r;
    }

    // ------------------------------------------------------------------ dados
    @Override
    public void aoMostrar() {
        App.atual().trabalhar("Abrindo o banco de dados...", () -> {
            BancoMundo b = Sessao.banco();
            return new Object[] {b, forcasDosClubes(b), Jogo.paises(b)};
        }, r -> {
            db = (BancoMundo) r[0];
            @SuppressWarnings("unchecked") Map<String, Integer> f = (Map<String, Integer>) r[1];
            forcas = f;
            @SuppressWarnings("unchecked") List<Jogo.OpcaoPais> ps = (List<Jogo.OpcaoPais>) r[2];
            paises = new ArrayList<>(ps);
            lerCustom();
            int times = kingsTimes().size();
            if (!db.kingsLigas.isEmpty()) paises.add(Math.min(1, paises.size()), new Jogo.OpcaoPais("KINGS", "Kings League", null, null, null, times));
            detalhados = detalhePadrao(null);
            atualizarDetalhe();
            {
                filtrarPaises();
                listaPaises.getSelectionModel().select(0);
                listaPaises.requestFocus();
            }
        });
    }

    /** Força de cada clube: média dos 11 melhores do elenco real de 2026. */
    private static Map<String, Integer> forcasDosClubes(BancoMundo b) {
        Map<String, List<Integer>> por = new HashMap<>();
        for (Db.Jogador j : b.jogadores.values()) if (j.clubeId() != null) por.computeIfAbsent(j.clubeId(), k -> new ArrayList<>()).add(j.forca());
        Map<String, Integer> f = new HashMap<>();
        for (var e : por.entrySet()) {
            List<Integer> l = e.getValue();
            l.sort(Comparator.reverseOrder());
            int n = Math.min(11, l.size());
            double s = 0;
            for (int i = 0; i < n; i++) s += l.get(i);
            if (n > 0) f.put(e.getKey(), (int) Math.round(s / n));
        }
        return f;
    }

    /** O mesmo critério do motor: as ligas mais fortes (soma das 16 maiores reputações), o Brasil e o país do clube. */
    private Set<String> detalhePadrao(String paisDoClube) {
        Map<String, List<Double>> reps = new HashMap<>();
        for (Db.Clube c : db.clubes.values()) reps.computeIfAbsent(c.pais(), k -> new ArrayList<>()).add(c.reputacao() == null ? 0 : c.reputacao());
        List<String> ids = new ArrayList<>(reps.keySet());
        Map<String, Double> peso = new HashMap<>();
        for (String id : ids) {
            List<Double> r = reps.get(id);
            r.sort(Comparator.reverseOrder());
            double s = 0;
            for (int i = 0; i < Math.min(16, r.size()); i++) s += r.get(i);
            peso.put(id, s);
        }
        ids.sort(Comparator.comparingDouble((String id) -> -peso.get(id)).thenComparing(Function.identity()));
        Set<String> s = new LinkedHashSet<>();
        if (reps.containsKey("BRA")) s.add("BRA");
        for (int i = 0; i < Math.min(PAISES_DETALHADOS, ids.size()); i++) s.add(ids.get(i));
        if (paisDoClube != null) s.add(paisDoClube);
        return s;
    }

    private void atualizarDetalhe() {
        int n = detalhados.size();
        detalhe.setText(n + (n == 1 ? " país" : " países") + "  ·  configurar mundo");
        listaDetalhe.getChildren().clear();
        for (String id : detalhados) {
            HBox chip = Ui.linha(6, Imagens.bandeira(id, 12), Ui.rotulo(Textos.pais(id)));
            chip.setStyle("-fx-background-color: #313640; -fx-background-radius: 14; -fx-padding: 4 10 4 8;");
            listaDetalhe.getChildren().add(chip);
        }
    }

    private void filtrarPaises() {
        String q = norm(buscaPais.getText());
        List<Jogo.OpcaoPais> l = new ArrayList<>();
        for (Jogo.OpcaoPais p : paises) if (q.isEmpty() || norm(p.nome()).contains(q)) l.add(p);
        Jogo.OpcaoPais antes = listaPaises.getSelectionModel().getSelectedItem();
        listaPaises.getItems().setAll(l);
        if (antes != null && l.contains(antes)) listaPaises.getSelectionModel().select(antes);
        else if (!l.isEmpty() && !q.isEmpty()) listaPaises.getSelectionModel().select(0);
    }

    private void carregarLigas(Jogo.OpcaoPais p) {
        if (db == null || p == null) return;
        List<Jogo.OpcaoLiga> todasLigas = new ArrayList<>(Jogo.ligas(db, p.id()));
        List<Jogo.OpcaoLiga> ligas = new ArrayList<>();
        java.util.TreeMap<String, Integer> estados = new java.util.TreeMap<>();
        for (Jogo.OpcaoLiga o : todasLigas) {
            if ("estadual".equals(o.ambito()) && o.uf() != null) estados.merge(o.uf(), o.clubes(), Integer::sum);
            else ligas.add(o);
        }
        ligas.sort(Comparator.comparingInt((Jogo.OpcaoLiga o) -> "nacional".equals(o.ambito()) ? 0 : 1)
                .thenComparingInt(o -> o.divisao() == null ? 99 : o.divisao()).thenComparing(Jogo.OpcaoLiga::nome));
        for (var e : estados.entrySet()) ligas.add(new Jogo.OpcaoLiga("uf:" + e.getKey(), "Estaduais · " + e.getKey(), "estadual", null, e.getKey(), e.getValue()));
        ultimasLigas = todasLigas;
        // só as ligas de clubes: mundiais e seleções da Kings vêm por proposta, não na escolha inicial
        List<Db.KingsLiga> kls = kingsLigas();
        kls.sort(Comparator.comparingInt(kl -> Textos.ordemLigaKings(kl.id())));
        for (Db.KingsLiga kl : kls) {
            if (!"KINGS".equals(p.id()) || kl.id().startsWith("kings-world-cup")) continue;
            int n = 0;
            for (Db.KingsTime kt : kingsTimes()) if (kl.id().equals(kt.ligaId())) n++;
            ligas.add(new Jogo.OpcaoLiga("kings:" + kl.id(), Textos.ligaKings(kl.id(), kl.nome()), "kings", null, null, n));
        }
        if (!"KINGS".equals(p.id())) ligas.add(new Jogo.OpcaoLiga("*", "Todos os clubes do país", "todos", null, null, p.clubes()));
        listaLigas.getItems().setAll(ligas);
        listaLigas.getSelectionModel().select(0);
    }

    private List<Jogo.OpcaoLiga> ultimasLigas = List.of();

    private void carregarClubes(Jogo.OpcaoLiga lg) {
        if (lg != null && lg.id().startsWith("uf:")) {
            montarEstado(lg.uf());
            return;
        }
        areaGrade.getChildren().setAll(grade);
        if (lg != null && lg.id().startsWith("kings:")) {
            montarGradeKings(lg);
            return;
        }
        Jogo.OpcaoPais p = listaPaises.getSelectionModel().getSelectedItem();
        if (db == null || lg == null || p == null) {
            clubes = new ArrayList<>();
        } else {
            clubes = "*".equals(lg.id()) ? Jogo.clubesDoPais(db, p.id()) : Jogo.clubes(db, lg.id());
            tituloGrade.setText("*".equals(lg.id()) ? p.nome() : Textos.curto(lg.nome()));
        }
        montarGrade();
    }

    private void montarGrade() {
        String q = norm(buscaClube.getText());
        grade.getChildren().clear();
        cartoes.clear();
        cartaoSelecionado = null;
        int n = 0;
        for (Jogo.OpcaoClube o : clubes) {
            if (!q.isEmpty() && !norm(o.nome()).contains(q)) continue;
            if (n++ >= 240) break;
            Node c = cartao(o);
            cartoes.put(o.id(), c);
            grade.getChildren().add(c);
        }
        subGrade.setText(clubes.isEmpty() ? "Nenhum clube nesta liga." : clubes.size() + (clubes.size() == 1 ? " clube" : " clubes") + "  ·  do maior para o menor");
        if (selecionado != null && cartoes.containsKey(selecionado.id())) marcar(selecionado);
        else if (kingsEscolhido == null && !clubes.isEmpty() && grade.getChildren().size() > 0) { // navegar não troca o time da Kings escolhido
            for (Jogo.OpcaoClube o : clubes) if (cartoes.containsKey(o.id())) {
                selecionar(o);
                break;
            }
        }
    }

    /** Todos os clubes de um estado, agrupados pelas divisões do estadual. */
    private void montarEstado(String uf) {
        tituloGrade.setText("Estaduais · " + uf);
        areaGrade.getChildren().clear();
        cartoes.clear();
        cartaoSelecionado = null;
        java.util.Set<String> vistos = new java.util.HashSet<>();
        List<Jogo.OpcaoLiga> ls = new ArrayList<>();
        for (Jogo.OpcaoLiga o : ultimasLigas) if ("estadual".equals(o.ambito()) && uf.equals(o.uf())) ls.add(o);
        ls.sort(Comparator.comparingInt((Jogo.OpcaoLiga o) -> o.divisao() == null ? 1 : o.divisao()).thenComparing(Jogo.OpcaoLiga::nome));
        int total = 0;
        Jogo.OpcaoClube primeiro = null;
        for (Jogo.OpcaoLiga o : ls) {
            FlowPane g = Ui.gradeFluida(150, 10);
            g.setStyle("-fx-padding: 4 20 16 20;");
            for (Jogo.OpcaoClube c : Jogo.clubes(db, o.id())) {
                if (!vistos.add(c.id())) continue;
                Node card = cartao(c);
                cartoes.put(c.id(), card);
                g.getChildren().add(card);
                if (primeiro == null) primeiro = c;
                total++;
            }
            if (g.getChildren().isEmpty()) continue;
            Label t = Ui.rotulo(Textos.curto(o.nome()), "titulo-painel");
            t.setStyle("-fx-padding: 8 20 0 20;");
            areaGrade.getChildren().addAll(t, g);
        }
        subGrade.setText(total + " clubes  ·  todas as divisões do estadual");
        if (selecionado != null && cartoes.containsKey(selecionado.id())) marcar(selecionado);
        else if (primeiro != null && kingsEscolhido == null) selecionar(primeiro);
    }

    private void montarGradeKings(Jogo.OpcaoLiga lg) {
        areaGrade.getChildren().setAll(grade);
        String liga = lg.id().substring(6);
        tituloGrade.setText(lg.nome());
        subGrade.setText("Kings League");
        grade.getChildren().clear();
        cartoes.clear();
        cartaoSelecionado = null;
        for (Db.KingsTime kt : kingsTimes()) {
            if (!liga.equals(kt.ligaId())) continue;
            Label nome = Ui.rotulo(kt.nome(), "nome");
            nome.setWrapText(true);
            nome.setMaxWidth(Double.MAX_VALUE);
            nome.setMinHeight(34);
            nome.setAlignment(Pos.TOP_CENTER);
            Label pres = Ui.rotulo(kt.presidente() == null ? " " : "Presidente: " + kt.presidente(), "texto-pequeno");
            VBox v = new VBox(6, Imagens.escudo(kt.id(), 64), nome, pres);
            v.getStyleClass().add("cartao-clube");
            if (kingsEscolhido != null && kingsEscolhido.id().equals(kt.id())) v.getStyleClass().add("selecionado");
            v.setOnMouseClicked(e -> {
                // ou clube, ou time da Kings: escolher o time tira o clube escolhido
                kingsEscolhido = kt;
                selecionado = null;
                if (cartaoSelecionado != null) cartaoSelecionado.getStyleClass().remove("selecionado");
                cartaoSelecionado = null;
                montarGradeKings(lg);
                mostrarEscolhido();
                if (e.getClickCount() == 2) comecar();
            });
            grade.getChildren().add(v);
        }
    }

    private Node cartao(Jogo.OpcaoClube o) {
        Label nome = Ui.rotulo(o.nome(), "nome");
        nome.setWrapText(true);
        nome.setMaxWidth(Double.MAX_VALUE);
        nome.setMinHeight(34);
        nome.setAlignment(Pos.TOP_CENTER);
        Integer f = forcas.get(o.id());
        HBox nums = Ui.linha(6, f == null ? Ui.rotulo("—", "texto-suave") : Ui.forca(f), Ui.estrelas(o.reputacao(), 11));
        nums.setAlignment(Pos.CENTER);
        String meta = Textos.divisao(o.pais(), o.divisao());
        if (o.uf() != null) meta = o.uf() + "  ·  " + meta;
        VBox v = new VBox(6, Imagens.escudo(o.id(), 64), nome, nums, Ui.rotulo(meta, "texto-pequeno"));
        v.getStyleClass().add("cartao-clube");
        v.setOnMouseClicked(e -> {
            selecionar(o);
            if (e.getClickCount() == 2) comecar();
        });
        return v;
    }

    private void selecionar(Jogo.OpcaoClube o) {
        selecionado = o;
        kingsEscolhido = null; // ou clube, ou time da Kings
        marcar(o);
        if (!detalheMexido) {
            detalhados = detalhePadrao(o.pais());
            atualizarDetalhe();
        } else {
            detalhados.add(o.pais());
            atualizarDetalhe();
        }
        mostrarEscolhido();
    }

    private void marcar(Jogo.OpcaoClube o) {
        if (cartaoSelecionado != null) cartaoSelecionado.getStyleClass().remove("selecionado");
        cartaoSelecionado = cartoes.get(o.id());
        if (cartaoSelecionado != null) cartaoSelecionado.getStyleClass().add("selecionado");
    }

    private void mostrarEscolhido() {
        escolhido.getChildren().clear();
        if (selecionado == null) {
            if (kingsEscolhido != null) {
                Label k = Ui.rotulo(kingsEscolhido.nome(), "titulo-painel");
                String liga = "Kings League";
                for (Db.KingsLiga l : kingsLigas()) if (l.id().equals(kingsEscolhido.ligaId())) liga = Textos.ligaKings(l.id(), l.nome());
                String pres = kingsEscolhido.presidente() == null ? "" : "  ·  presidente: " + kingsEscolhido.presidente();
                Label sub = Ui.rotulo(liga + pres, "texto-pequeno");
                k.setMinWidth(0);
                sub.setMinWidth(0);
                VBox kt = new VBox(1, k, sub);
                kt.setMinWidth(0);
                kt.setMaxWidth(340);
                escolhido.getChildren().addAll(Imagens.escudo(kingsEscolhido.id(), 40), kt);
                HBox.setHgrow(escolhido, Priority.SOMETIMES);
                escolhido.setMinWidth(0);
                comecar.setText("Começar");
                comecar.setDisable(false);
                return;
            }
            escolhido.getChildren().add(Ui.rotulo(treinadores.isEmpty() ? "Nenhum clube" : "Escolha o time do próximo técnico (ou comece)", "texto-suave"));
            comecar.setText(treinadores.size() >= 2 ? "Começar (" + treinadores.size() + " técnicos)" : "Começar");
            comecar.setDisable(treinadores.size() < 2);
            return;
        }
        Db.Clube dc = db == null ? null : db.clubes.get(selecionado.id());
        String sub = (dc != null && dc.cidade() != null ? dc.cidade() + "  ·  " : "")
                + (dc != null && dc.estadio() != null ? dc.estadio() : Textos.divisao(selecionado.pais(), selecionado.divisao()));
        Label n = Ui.rotulo(selecionado.nome(), "titulo-painel");
        Label s = Ui.rotulo(sub, "texto-pequeno");
        n.setMinWidth(0);
        s.setMinWidth(0);
        VBox t = new VBox(1, n, s);
        t.setMinWidth(0);
        t.setMaxWidth(300);
        escolhido.getChildren().addAll(Imagens.escudo(selecionado.id(), 40), t);
        HBox.setHgrow(escolhido, Priority.SOMETIMES);
        escolhido.setMinWidth(0);
        comecar.setText("Começar");
        comecar.setDisable(false);
    }

    private void escolherDetalhe() {
        if (db == null) return;
        String meuPais = selecionado == null ? null : selecionado.pais();
        Set<String> padrao = detalhePadrao(meuPais);
        Set<String> marcados = new LinkedHashSet<>(detalhados);
        Map<String, CheckBox> caixas = new LinkedHashMap<>();
        Map<String, List<Jogo.OpcaoPais>> porConf = new java.util.TreeMap<>();
        for (Jogo.OpcaoPais p : paises) porConf.computeIfAbsent(p.confederacao() == null ? "Outras" : p.confederacao(), k -> new ArrayList<>()).add(p);
        Label contador = Ui.rotulo("", "titulo-painel");
        Label nota = Ui.paragrafo("", "texto-suave");
        nota.setMaxWidth(520);
        Map<String, Integer> clubesDe = new HashMap<>();
        for (Jogo.OpcaoPais p : paises) clubesDe.put(p.id(), p.clubes());
        Runnable contar = () -> {
            int clubes = 0;
            for (String id : marcados) clubes += clubesDe.getOrDefault(id, 0);
            contador.setText(marcados.size() + (marcados.size() == 1 ? " país" : " países") + "  ·  " + Textos.milhar(clubes) + " clubes em detalhe");
            int seg = (int) Math.round(6 + clubes * 0.006);
            nota.setText(clubes > 2500 ? "Muitos clubes em detalhe: cada temporada deve levar perto de " + seg + " segundos para simular (o jogo continua normal)." : "");
        };
        Map<String, CheckBox> porConfCaixa = new LinkedHashMap<>();
        Runnable sincronizar = () -> {
            caixas.forEach((id, cb) -> cb.setSelected(marcados.contains(id)));
            porConf.forEach((conf, l) -> porConfCaixa.get(conf).setSelected(l.stream().allMatch(p -> marcados.contains(p.id()))));
            contar.run();
        };
        VBox grupos = new VBox(12);
        for (var e : porConf.entrySet()) {
            CheckBox todosConf = new CheckBox(e.getKey());
            todosConf.getStyleClass().add("titulo-painel");
            porConfCaixa.put(e.getKey(), todosConf);
            todosConf.setOnAction(x -> {
                for (Jogo.OpcaoPais p : e.getValue()) {
                    if (todosConf.isSelected()) marcados.add(p.id());
                    else if (!p.id().equals(meuPais)) marcados.remove(p.id());
                }
                sincronizar.run();
            });
            FlowPane fp = new FlowPane(6, 6);
            for (Jogo.OpcaoPais p : e.getValue()) {
                CheckBox cb = new CheckBox();
                // caixinha, depois a bandeira e o nome com espaço (dentro do CheckBox ficavam colados)
                HBox item = new HBox(10, cb, Imagens.bandeira(p.id(), 12), Ui.rotulo(p.nome()));
                if (padrao.contains(p.id())) item.getChildren().add(Ui.etiqueta("padrão", "verde"));
                item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                item.setPrefWidth(200);
                item.setCursor(javafx.scene.Cursor.HAND);
                item.setOnMouseClicked(ev -> {
                    // clique fora da caixinha (bandeira/nome) também marca; na caixinha o próprio CheckBox cuida
                    if (!cb.isDisabled() && !cb.localToScene(cb.getBoundsInLocal()).contains(ev.getSceneX(), ev.getSceneY())) cb.fire();
                });
                item.visibleProperty().bind(cb.visibleProperty());
                item.managedProperty().bind(cb.managedProperty());
                cb.setOnAction(x -> {
                    if (cb.isSelected()) marcados.add(p.id());
                    else if (!p.id().equals(meuPais)) marcados.remove(p.id());
                    sincronizar.run();
                });
                cb.setUserData(norm(p.nome()));
                caixas.put(p.id(), cb);
                fp.getChildren().add(item);
            }
            grupos.getChildren().add(new VBox(6, todosConf, fp));
        }
        TextField busca = new TextField();
        busca.setPromptText("Buscar país");
        busca.textProperty().addListener((o, x, q) -> {
            String n = norm(q);
            caixas.values().forEach(cb -> {
                boolean ok = n.isEmpty() || ((String) cb.getUserData()).contains(n);
                cb.setVisible(ok);
                cb.setManaged(ok);
            });
        });
        java.util.function.Consumer<java.util.Collection<String>> aplicar = ids -> {
            marcados.clear();
            marcados.addAll(ids);
            if (meuPais != null) marcados.add(meuPais);
            sincronizar.run();
        };
        List<String> sulAmerica = new ArrayList<>();
        for (Jogo.OpcaoPais p : paises) if ("CONMEBOL".equals(p.confederacao())) sulAmerica.add(p.id());
        HBox atalhos = Ui.linha(6,
                Ui.botao("Selecionar todos", () -> aplicar.accept(caixas.keySet())),
                Ui.botao("Limpar", () -> aplicar.accept(List.of())),
                Ui.botao("Só o meu país", () -> aplicar.accept(meuPais == null ? List.of("BRA") : List.of(meuPais))),
                Ui.botao("América do Sul", () -> aplicar.accept(sulAmerica)),
                Ui.botao("Europa: 5 grandes", () -> aplicar.accept(List.of("ENG", "ESP", "ITA", "DEU", "FRA"))),
                Ui.botao("Principais ligas", () -> aplicar.accept(padrao)));
        ScrollPane sp = Ui.rolar(grupos);
        sp.setPrefSize(860, 420);
        Label exp = Ui.info("Países em detalhe jogam lance a lance, com artilharia, prêmios e manchetes; os outros usam um placar rápido. O país do seu clube fica sempre em detalhe.");
        VBox conteudo = new VBox(10, exp, atalhos, Ui.linha(12, busca, Ui.espaco(), contador), nota, sp);
        sincronizar.run();
        Dialog<Boolean> d = new Dialog<>();
        d.setTitle("Países em detalhe");
        d.setHeaderText("Países simulados em detalhe");
        d.getDialogPane().setContent(conteudo);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) d.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancelar");
        d.setResultConverter(b -> b == ButtonType.OK);
        Ui.dialogo(d).ifPresent(ok -> {
            if (!ok) return;
            Set<String> novo = new LinkedHashSet<>();
            for (Jogo.OpcaoPais p : paises) if (marcados.contains(p.id())) novo.add(p.id());
            if (selecionado != null) novo.add(selecionado.pais());
            detalheMexido = !novo.equals(padrao);
            detalhados = novo;
            atualizarDetalhe();
        });
    }

    private static String norm(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    // ------------------------------------------------------------------ começar
    private void comecar() {
        if (db == null) return;
        if (!treinadores.isEmpty()) {
            comecarMulti();
            return;
        }
        Jogo.OpcaoClube o = selecionado;
        if (o == null && kingsEscolhido == null) return;
        long seed;
        String txt = semente.getText() == null ? "" : semente.getText().trim();
        try {
            seed = txt.isEmpty() ? new Random().nextInt(1_000_000) : Long.parseLong(txt);
        } catch (NumberFormatException e) {
            seed = txt.hashCode();
        }
        long s = seed;
        String tecnico = nomeTecnico.getText() == null || nomeTecnico.getText().isBlank() ? "Técnico" : nomeTecnico.getText().trim();
        Set<String> det = new HashSet<>(detalhados);
        String kings = kingsEscolhido == null ? null : kingsEscolhido.id();
        var def = custom;
        if (kings == null && o != null) det.add(o.pais());
        App.atual().trabalhar("Montando o mundo de 2026...", () -> {
            // carreira exclusiva: time da Kings (sem clube) ou clube
            Jogo j = kings != null ? Jogo.novoJogoKings(db, s, null, kings, tecnico, def)
                    : Jogo.novoJogo(db, s, o.id(), tecnico, def);
            for (var p : j.paisesDoMundo()) j.definirDetalhe(p.id, det.contains(p.id));
            // opção "renovação automática em carreiras novas" (a chave do Elenco muda depois, por save)
            if (br.futebas.ui.Opcoes.renovacaoAutomaticaNovos()) j.definirRenovacaoAutomatica(true, 0);

            return new Sessao(j, null);
        }, sessao -> App.atual().abrir(sessao));
    }

    /** Multi-treinador: os técnicos adicionados (e o da escolha atual, se houver) num jogo só. */
    private void comecarMulti() {
        String idAtual = selecionado != null ? selecionado.id() : kingsEscolhido != null ? kingsEscolhido.id() : null;
        if (idAtual != null) {
            adicionarTreinador();
            if (selecionado != null || kingsEscolhido != null) return; // não entrou (repetido): o aviso já apareceu
        }
        List<Jogo.EscolhaTecnico> lista = new ArrayList<>(treinadores);
        if (lista.isEmpty()) return;
        long seed;
        String txt = semente.getText() == null ? "" : semente.getText().trim();
        try {
            seed = txt.isEmpty() ? new Random().nextInt(1_000_000) : Long.parseLong(txt);
        } catch (NumberFormatException e) {
            seed = txt.hashCode();
        }
        long s = seed;
        Set<String> det = new HashSet<>(detalhados);
        for (Jogo.EscolhaTecnico e : lista) if (e.clubeId() != null && paisDoTreinador.get(e.clubeId()) != null) det.add(paisDoTreinador.get(e.clubeId()));
        var def = custom;
        App.atual().trabalhar("Montando o mundo de 2026...", () -> {
            Jogo j = Jogo.novoJogoMulti(db, s, lista, def);
            for (var p : j.paisesDoMundo()) j.definirDetalhe(p.id, det.contains(p.id));
            if (br.futebas.ui.Opcoes.renovacaoAutomaticaNovos()) {
                for (int i = 0; i < j.tecnicos().size(); i++) {
                    j.passarVez(i);
                    j.definirRenovacaoAutomatica(true, 0);
                }
                j.passarVez(0);
            }
            return new Sessao(j, null);
        }, sessao -> App.atual().abrir(sessao));
    }

    @Override
    public Parent raiz() { return raiz; }

    @Override
    public boolean tecla(KeyEvent e) {
        boolean texto = raiz.getScene() != null && raiz.getScene().getFocusOwner() instanceof TextInputControl;
        if (e.getCode() == KeyCode.ENTER && (!texto || raiz.getScene().getFocusOwner() == nomeTecnico)) {
            comecar();
            return true;
        }
        if (e.getCode() == KeyCode.ESCAPE) {
            App.atual().irParaInicio();
            return true;
        }
        return false;
    }
}
