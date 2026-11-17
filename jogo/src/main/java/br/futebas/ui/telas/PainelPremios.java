package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.dados.NomesCompeticao;
import br.futebas.historia.Historia;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Posicao;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Foto;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Prêmios: Bola de Ouro (Top 50), Bola de Ouro jovem (Top 50 até 21 anos) e a seleção de cada
 * liga, por ano. Bola de Ouro: pódio dos 3 primeiros (foto grande, escudo, nota, gols e
 * assistências) e a lista do 4º em diante. Seleção da liga: competições agrupadas por país (o seu
 * primeiro) com busca; o time no campo da escalação (foto redonda, nota na cor da faixa, escudo do
 * clube) e, ao lado, a lista dos 11 com clube, jogos, gols, assistências e nota.
 */
public final class PainelPremios implements Hub.Painel {
    private final Sessao sessao;
    private static Integer anoAntes;
    private static int abaAntes;
    private static String ligaAntes;

    public PainelPremios(Sessao sessao) {
        this.sessao = sessao;
    }

    private Jogo j() { return sessao.jogo; }

    @Override
    public Node construir() {
        Historia h = j().historia();
        TreeSet<Integer> anos = new TreeSet<>(Comparator.reverseOrder());
        for (Historia.Ranking r : h.rankings) anos.add(r.ano);
        for (Historia.SelecaoDaLiga s : h.selecoesDaLiga) anos.add(s.ano);
        if (anos.isEmpty()) {
            return Ui.conteudo(Ui.titulo("Prêmios"), Ui.painel(null, Ui.vazio("A Bola de Ouro e as seleções das ligas saem em dezembro.", null, null)));
        }
        ComboBox<Integer> ano = new ComboBox<>();
        ano.getItems().setAll(anos);
        ano.setValue(anoAntes != null && anos.contains(anoAntes) ? anoAntes : anos.first());
        VBox v = Ui.conteudo();
        v.getStyleClass().add("premios");
        Runnable montar = () -> {
            int a = ano.getValue();
            anoAntes = a;
            TabPane abas = new TabPane();
            abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            abas.getTabs().addAll(
                    Ui.abaSobDemanda("Bola de Ouro", () -> ranking(j().bolaDeOuro(a), "Bola de Ouro " + a)),
                    Ui.abaSobDemanda("Bola de Ouro jovem", () -> ranking(j().bolaDeOuroJovens(a), "Bola de Ouro jovem " + a + " · até 21 anos")),
                    Ui.abaSobDemanda("Seleção da liga", () -> selecoes(j().selecoesDaLiga(a))));
            abas.getSelectionModel().select(abaAntes);
            Ui.ativarSobDemanda(abas);
            abas.getSelectionModel().selectedIndexProperty().addListener((o, x, y) -> abaAntes = y.intValue());
            VBox.setVgrow(abas, Priority.ALWAYS);
            v.getChildren().setAll(Ui.linha(12, Ui.titulo("Prêmios"), Ui.espaco(), Ui.rotulo("Temporada", "texto-medio"), ano), abas);
        };
        ano.valueProperty().addListener((o, x, y) -> montar.run());
        montar.run();
        return v;
    }

    // ================================================================== Bola de Ouro
    private Node ranking(List<Historia.ItemRanking> l, String titulo) {
        if (l == null || l.isEmpty()) return Ui.painel(null, Ui.vazio("Ainda não há ranking nesta temporada.", null, null));
        Mundo m = j().mundo();
        HBox podio = new HBox(14);
        podio.setAlignment(Pos.BOTTOM_CENTER);
        for (int k : new int[] {1, 0, 2}) if (k < l.size()) podio.getChildren().add(cartaoPodio(m, l.get(k), k));
        Label tit = Ui.rotulo(titulo, "premio-titulo");
        VBox topo = new VBox(10, tit, podio);
        topo.setAlignment(Pos.TOP_CENTER);
        topo.getStyleClass().add("premio-podio");

        TableView<Historia.ItemRanking> tv = Ui.tabela();
        Ui.colNum(tv, "#", 44, x -> x.posicao);
        Ui.colNo(tv, "Jogador", 220, x -> Ui.nomeJogador(x.jogadorId, x.jogador, "texto-forte"));
        Ui.colNo(tv, "País", 150, x -> {
            HBox hb = Ui.linha(6, Imagens.bandeira(x.pais, 12), Ui.rotulo(Textos.pais(x.pais), "texto-medio"));
            return hb;
        });
        Ui.colNum(tv, "Idade", 54, x -> x.idade);
        Ui.colClube(tv, "Clube", 210, m, x -> x.clubeId);
        Ui.colNum(tv, "J", 42, x -> x.jogos);
        Ui.colNum(tv, "G", 42, x -> x.gols);
        Ui.colNum(tv, "A", 42, x -> x.assistencias);
        var cn = Ui.colNo(tv, "Nota", 60, x -> Ui.nota(x.nota, x.jogos > 0));
        cn.getStyleClass().add("centro");
        tv.getItems().setAll(l.size() > 3 ? l.subList(3, l.size()) : List.of());
        Ui.estiloLinhas(tv, (x, row) -> {
            if (j().meuClube() != null && j().meuClube().id.equals(x.clubeId)) row.getStyleClass().add("meu-clube");
        });
        tv.setMinHeight(160);
        VBox tab = Ui.painelTabela("Do 4º ao " + l.size() + "º", null, tv);
        VBox.setVgrow(tv, Priority.ALWAYS);
        VBox.setVgrow(tab, Priority.ALWAYS);
        tab.setMinHeight(200);
        VBox caixa = new VBox(14, topo, tab);
        caixa.setPadding(new Insets(14, 0, 0, 0));
        return caixa;
    }

    private static final String[] MEDALHA = {"ouro", "prata", "bronze"};

    /**
     * Cartão do pódio (deitado, para a lista caber embaixo em 1366×768): foto grande com o escudo no
     * canto; ao lado a medalha e o nome, clube, país e idade e os números (nota, gols, assistências, jogos).
     */
    private Node cartaoPodio(Mundo m, Historia.ItemRanking x, int k) {
        Jogador jg = x.jogadorId == null ? null : m.jogador(x.jogadorId);
        double alto = k == 0 ? 124 : 104;
        Node f = Foto.de(jg == null ? null : j().foto(jg.id), jg == null ? null : jg.dbId, alto * 0.9, alto);
        StackPane foto = new StackPane(f);
        foto.getStyleClass().add("podio-foto");
        foto.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane escudo = new StackPane(Ui.escudo(x.clubeId, k == 0 ? 30 : 26));
        escudo.getStyleClass().add("podio-escudo");
        escudo.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane fotoEsc = new StackPane(foto, escudo);
        StackPane.setAlignment(escudo, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(escudo, new Insets(0, -8, -6, 0));
        fotoEsc.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        fotoEsc.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Label medalha = Ui.rotulo((k + 1) + "º", "podio-medalha", "podio-" + MEDALHA[k]);
        medalha.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Label nome = Ui.nomeJogador(x.jogadorId, x.jogador, "podio-nome");
        nome.setMinWidth(0);
        HBox topo = Ui.linha(8, medalha, nome);
        Label clube = Ui.nomeClube(x.clubeId, x.clube == null ? "Sem clube" : x.clube, "podio-clube");
        clube.setMinWidth(0);
        HBox pais = Ui.linha(6, Imagens.bandeira(x.pais, 11), Ui.rotulo(Textos.pais(x.pais) + " · " + x.idade + " anos", "podio-sub"));
        HBox nums = Ui.linha(14, numero(Ui.nota(x.nota, x.jogos > 0), "Nota"), numero(Ui.rotulo(String.valueOf(x.gols), "podio-num"), "Gols"),
                numero(Ui.rotulo(String.valueOf(x.assistencias), "podio-num"), "Assist."), numero(Ui.rotulo(String.valueOf(x.jogos), "podio-num"), "Jogos"));
        nums.setAlignment(Pos.CENTER_LEFT);
        nums.getStyleClass().add("podio-nums");
        VBox info = new VBox(3, topo, clube, pais, nums);
        VBox.setMargin(nums, new Insets(6, 0, 0, 0));
        info.setAlignment(Pos.CENTER_LEFT);
        info.setMinWidth(0);
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox v = new HBox(16, fotoEsc, info);
        v.setAlignment(Pos.CENTER_LEFT);
        v.getStyleClass().addAll("podio-cartao", "podio-cartao-" + MEDALHA[k]);
        v.setPrefWidth(k == 0 ? 380 : 340);
        v.setMinWidth(k == 0 ? 300 : 270);
        v.setMaxWidth(k == 0 ? 440 : 400);
        HBox.setHgrow(v, Priority.ALWAYS);
        return v;
    }

    private static Node numero(Node valor, String rotulo) {
        VBox v = new VBox(1, valor, Ui.rotulo(rotulo, "podio-rotulo"));
        v.setAlignment(Pos.CENTER);
        return v;
    }

    // ================================================================== seleção da liga
    /** Nome legível da liga: o do banco corrigido (nunca o id cru, "Primeira Divisão" e não "1º Divisão"). */
    public static String nomeLiga(Historia.SelecaoDaLiga s) {
        return Textos.curto(NomesCompeticao.corrigir(s.competicaoId, s.competicao, s.divisao));
    }

    private static String chaveBusca(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private Node selecoes(List<Historia.SelecaoDaLiga> l) {
        if (l == null || l.isEmpty()) return Ui.painel(null, Ui.vazio("Nenhuma liga encerrada nesta temporada.", null, null));
        String meuPais = j().meuClube() == null ? "BRA" : j().meuClube().pais;
        // grupos: o seu país, internacionais, depois os outros países em ordem alfabética
        List<Historia.SelecaoDaLiga> ls = new ArrayList<>(l);
        ls.sort(Comparator.comparingInt((Historia.SelecaoDaLiga s) -> s.pais == null ? 1 : meuPais.equals(s.pais) ? 0 : 2)
                .thenComparing(s -> chaveBusca(s.pais == null ? "" : Textos.pais(s.pais)))
                .thenComparingInt(s -> s.divisao == null ? 9 : s.divisao).thenComparing(PainelPremios::nomeLiga));
        Map<String, List<Historia.SelecaoDaLiga>> grupos = new LinkedHashMap<>();
        for (Historia.SelecaoDaLiga s : ls) grupos.computeIfAbsent(s.pais == null ? "" : s.pais, k -> new ArrayList<>()).add(s);

        StackPane detalhe = new StackPane();
        HBox.setHgrow(detalhe, Priority.ALWAYS);
        VBox lista = new VBox(2);
        lista.getStyleClass().add("premio-ligas");
        List<HBox> itens = new ArrayList<>();
        Consumer<Historia.SelecaoDaLiga>[] escolher = new Consumer[1];
        Map<Historia.SelecaoDaLiga, HBox> itemDe = new java.util.IdentityHashMap<>();
        escolher[0] = s -> {
            ligaAntes = s.competicaoId;
            for (HBox it : itens) it.getStyleClass().remove("selecionado");
            HBox it = itemDe.get(s);
            if (it != null) it.getStyleClass().add("selecionado");
            detalhe.getChildren().setAll(detalheSelecao(s));
        };
        TextField busca = new TextField();
        busca.setPromptText("Buscar liga ou país");
        busca.getStyleClass().add("cx-busca");
        Runnable filtrar = () -> {
            String q = chaveBusca(busca.getText()).trim();
            lista.getChildren().clear();
            for (var g : grupos.entrySet()) {
                String pais = g.getKey();
                String nomePais = pais.isEmpty() ? "Internacional" : Textos.pais(pais);
                List<HBox> doGrupo = new ArrayList<>();
                for (Historia.SelecaoDaLiga s : g.getValue()) {
                    if (!q.isEmpty() && !chaveBusca(nomeLiga(s)).contains(q) && !chaveBusca(nomePais).contains(q)) continue;
                    doGrupo.add(itemDe.get(s));
                }
                if (doGrupo.isEmpty()) continue;
                HBox cab = Ui.linha(8, pais.isEmpty() ? Ui.rotulo("◎", "premio-grupo-icone") : Imagens.bandeira(pais, 13),
                        Ui.rotulo(nomePais.toUpperCase(Locale.ROOT), "premio-grupo"));
                cab.getStyleClass().add("premio-grupo-cab");
                if (lista.getChildren().isEmpty()) cab.getStyleClass().add("primeiro");
                lista.getChildren().add(cab);
                lista.getChildren().addAll(doGrupo);
            }
            if (lista.getChildren().isEmpty()) lista.getChildren().add(Ui.rotulo("Nenhuma liga encontrada", "texto-suave"));
        };
        for (Historia.SelecaoDaLiga s : ls) {
            Label n = Ui.rotulo(nomeLiga(s), "premio-liga-nome");
            n.setMinWidth(0);
            n.setWrapText(true);
            n.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(n, Priority.ALWAYS);
            javafx.scene.control.Tooltip.install(n, new javafx.scene.control.Tooltip(nomeLiga(s) + (s.pais == null ? "" : " · " + Textos.pais(s.pais))));
            Node trofeu = Imagens.trofeu(s.competicaoId, "nacional", s.pais, 20);
            StackPane tr = new StackPane(trofeu);
            tr.setMinSize(22, 22);
            tr.setPrefSize(22, 22);
            HBox it = Ui.linha(8, tr, n);
            if (s.divisao != null) {
                Label d = Ui.rotulo(s.divisao + "ª", "premio-liga-div");
                d.setMinWidth(Region.USE_PREF_SIZE);
                it.getChildren().add(d);
            }
            it.getStyleClass().add("premio-liga");
            it.setCursor(javafx.scene.Cursor.HAND);
            it.setOnMouseClicked(e -> escolher[0].accept(s));
            itens.add(it);
            itemDe.put(s, it);
        }
        busca.textProperty().addListener((o, a, b) -> filtrar.run());
        filtrar.run();

        ScrollPane rolo = new ScrollPane(lista);
        rolo.setFitToWidth(true);
        rolo.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolo.getStyleClass().add("premio-ligas-rolo");
        VBox.setVgrow(rolo, Priority.ALWAYS);
        Label contagem = Ui.rotulo(ls.size() + (ls.size() == 1 ? " liga" : " ligas"), "texto-pequeno");
        VBox col = new VBox(10, campoBusca(busca), contagem, rolo);
        col.getStyleClass().add("premio-coluna");
        col.setPrefWidth(272);
        col.setMinWidth(240);
        col.setMaxWidth(290);

        Historia.SelecaoDaLiga inicial = ls.get(0);
        if (ligaAntes != null) for (Historia.SelecaoDaLiga s : ls) if (ligaAntes.equals(s.competicaoId)) inicial = s;
        escolher[0].accept(inicial);
        HBox raiz = new HBox(14, col, detalhe);
        raiz.setPadding(new Insets(14, 0, 0, 0));
        return raiz;
    }

    /** Busca arredondada com a lupa dentro e o "x" para limpar (a mesma da caixa de entrada). */
    private static Node campoBusca(TextField busca) {
        javafx.scene.shape.SVGPath lupa = new javafx.scene.shape.SVGPath();
        lupa.setContent("M6.5 1a5.5 5.5 0 0 1 4.38 8.83l3.65 3.64-1.06 1.06-3.64-3.65A5.5 5.5 0 1 1 6.5 1zm0 1.5a4 4 0 1 0 0 8 4 4 0 0 0 0-8z");
        lupa.getStyleClass().add("cx-busca-icone");
        lupa.setMouseTransparent(true);
        Label limpar = Ui.rotulo("✕", "cx-busca-limpar");
        limpar.setCursor(javafx.scene.Cursor.HAND);
        limpar.setOnMouseClicked(e -> busca.clear());
        limpar.visibleProperty().bind(busca.textProperty().isNotEmpty());
        StackPane sp = new StackPane(busca, lupa, limpar);
        StackPane.setAlignment(lupa, Pos.CENTER_LEFT);
        StackPane.setMargin(lupa, new Insets(0, 0, 0, 12));
        StackPane.setAlignment(limpar, Pos.CENTER_RIGHT);
        StackPane.setMargin(limpar, new Insets(0, 10, 0, 0));
        return sp;
    }

    /** A seleção escolhida: cabeçalho da liga, o campo e a lista dos 11. */
    private Node detalheSelecao(Historia.SelecaoDaLiga s) {
        Node trofeu = Ui.trofeu(s.competicaoId, "nacional", s.pais, 40);
        Label nome = Ui.nomeCompeticao(s.competicaoId, nomeLiga(s), "premio-liga-titulo");
        nome.setWrapText(true);
        nome.setMinHeight(Region.USE_PREF_SIZE);
        String sub = (s.pais == null ? "" : Textos.pais(s.pais) + "  ·  ") + (s.divisao == null ? "" : s.divisao + "ª divisão  ·  ")
                + (s.temporada == null ? String.valueOf(s.ano) : s.temporada);
        HBox linhaSub = Ui.linha(6, s.pais == null ? new Region() : Imagens.bandeira(s.pais, 12), Ui.rotulo(sub, "texto-medio"));
        VBox textos = new VBox(2, Ui.rotulo("SELEÇÃO DA LIGA", "premio-sobretitulo"), nome, linhaSub);
        double media = s.time.stream().mapToDouble(x -> x.nota).average().orElse(0);
        VBox mediaBox = new VBox(2, Ui.nota(media, !s.time.isEmpty()), Ui.rotulo("Nota média", "podio-rotulo"));
        mediaBox.setAlignment(Pos.CENTER);
        mediaBox.setMinWidth(Region.USE_PREF_SIZE);
        textos.setMinWidth(0);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox cab = Ui.linha(12, trofeu, textos, mediaBox);
        cab.getStyleClass().add("premio-cab-liga");

        Node campo = campo(j(), s);
        VBox lista = listaOnze(s);
        VBox.setVgrow(lista, Priority.ALWAYS);
        lista.setMaxHeight(11 * 54 + 40);
        Node destaques = destaques(s);
        // o campo ocupa a altura toda; à direita, o cabeçalho da liga, a lista dos 11 e (se couber) os destaques
        VBox direita = new VBox(12, cab, lista, destaques);
        direita.setMinWidth(340);
        direita.setPrefWidth(340);
        direita.setMaxWidth(700);
        direita.heightProperty().addListener((o, a, h) -> {
            boolean cabe = h.doubleValue() >= 760;
            if (destaques.isVisible() != cabe) {
                destaques.setVisible(cabe);
                destaques.setManaged(cabe);
            }
        });
        destaques.setVisible(false);
        destaques.setManaged(false);
        HBox.setHgrow(direita, Priority.ALWAYS);
        HBox corpo = new HBox(14, campo, direita);
        corpo.setAlignment(Pos.TOP_LEFT);
        corpo.getStyleClass().add("premio-detalhe");
        // o campo tem a largura que a altura pede (proporção do gramado): a coluna da direita fica colada nele
        if (campo instanceof Region rc) {
            rc.prefWidthProperty().bind(corpo.heightProperty().multiply(CampoEscalacao.W / CampoEscalacao.H));
            rc.setMinWidth(260);
            rc.setMaxWidth(Region.USE_PREF_SIZE);
        }
        return corpo;
    }

    /** Destaques da seleção: artilheiro, garçom, melhor nota e o clube com mais jogadores. */
    private Node destaques(Historia.SelecaoDaLiga s) {
        HBox h = new HBox(10);
        if (s.time.isEmpty()) return h;
        Historia.ItemSelecao art = s.time.stream().max(Comparator.comparingInt((Historia.ItemSelecao x) -> x.gols)).get();
        Historia.ItemSelecao gar = s.time.stream().max(Comparator.comparingInt((Historia.ItemSelecao x) -> x.assistencias)).get();
        Historia.ItemSelecao nota = s.time.stream().max(Comparator.comparingDouble((Historia.ItemSelecao x) -> x.nota)).get();
        Map<String, Integer> porClube = new LinkedHashMap<>();
        for (Historia.ItemSelecao x : s.time) if (x.clubeId != null) porClube.merge(x.clubeId, 1, Integer::sum);
        String clube = porClube.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        String nomeClube = clube == null ? "—" : s.time.stream().filter(x -> clube.equals(x.clubeId)).map(x -> x.clube).filter(java.util.Objects::nonNull).findFirst().orElse(clube);
        h.getChildren().addAll(
                destaque("ARTILHEIRO", Ui.escudo(art.clubeId, 26), Ui.nomeJogador(art.jogadorId, art.jogador, "destaque-nome"), art.gols + " gols"),
                destaque("GARÇOM", Ui.escudo(gar.clubeId, 26), Ui.nomeJogador(gar.jogadorId, gar.jogador, "destaque-nome"), gar.assistencias + " assistências"),
                destaque("MELHOR NOTA", Ui.escudo(nota.clubeId, 26), Ui.nomeJogador(nota.jogadorId, nota.jogador, "destaque-nome"), "nota " + Textos.umaCasa(nota.nota)),
                destaque("MAIS NA SELEÇÃO", Ui.escudo(clube, 26), Ui.nomeClube(clube, nomeClube, "destaque-nome"),
                        porClube.getOrDefault(clube, 0) + " jogadores"));
        return h;
    }

    private static Node destaque(String rotulo, Node escudo, Label nome, String valor) {
        nome.setMinWidth(0);
        VBox txt = new VBox(1, Ui.rotulo(rotulo, "destaque-rotulo"), nome, Ui.rotulo(valor, "destaque-valor"));
        txt.setMinWidth(0);
        HBox h = new HBox(8, escudo, txt);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("premio-destaque");
        h.setMinWidth(0);
        h.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(h, Priority.ALWAYS);
        return h;
    }

    /** Lista compacta dos 11: posição, escudo, nome (abre o perfil) e clube, J, G, A e nota. */
    private VBox listaOnze(Historia.SelecaoDaLiga s) {
        GridPane g = new GridPane();
        g.getStyleClass().add("premio-onze");
        g.setHgap(6);
        g.setVgap(0);
        double[] larg = {38, 24, -1, 26, 26, 26, 40};
        for (double w : larg) {
            ColumnConstraints cc = new ColumnConstraints();
            if (w > 0) {
                cc.setMinWidth(w);
                cc.setPrefWidth(w);
                cc.setHalignment(javafx.geometry.HPos.CENTER);
            } else {
                cc.setHgrow(Priority.ALWAYS);
                cc.setMinWidth(80);
            }
            g.getColumnConstraints().add(cc);
        }
        String[] cab = {"Pos", "", "Jogador", "J", "G", "A", "Nota"};
        for (int c = 0; c < cab.length; c++) g.add(Ui.rotulo(cab[c], "premio-onze-cab"), c, 0);
        int r = 1;
        Mundo m = j().mundo();
        for (Historia.ItemSelecao it : s.time) {
            Posicao p = posicao(it.posicao);
            Label pos = Ui.pos(p);
            Node esc = Ui.escudo(it.clubeId, 22);
            Label nome = Ui.nomeJogador(it.jogadorId, it.jogador, "premio-onze-nome");
            nome.setMinWidth(0);
            Clube c = it.clubeId == null ? null : m.clube(it.clubeId);
            String nomeClube = c != null ? c.nome : it.clube == null ? "Sem clube" : it.clube;
            Label clube = Ui.nomeClube(it.clubeId, nomeClube, "premio-onze-clube");
            clube.setMinWidth(0);
            VBox quem = new VBox(0, nome, clube);
            quem.setAlignment(Pos.CENTER_LEFT);
            quem.setMinWidth(0);
            Node[] cel = {pos, esc, quem, Ui.rotulo(String.valueOf(it.jogos), "premio-onze-num"), Ui.rotulo(String.valueOf(it.gols), "premio-onze-num"),
                    Ui.rotulo(String.valueOf(it.assistencias), "premio-onze-num"), Ui.nota(it.nota, it.jogos > 0)};
            for (int k = 0; k < cel.length; k++) {
                StackPane box = new StackPane(cel[k]);
                box.setAlignment(k == 2 ? Pos.CENTER_LEFT : Pos.CENTER);
                box.getStyleClass().add("premio-onze-celula");
                if (r % 2 == 0) box.getStyleClass().add("zebra");
                if (k == 0) box.getStyleClass().add("primeira");
                if (k == cel.length - 1) box.getStyleClass().add("ultima");
                box.setMinWidth(0);
                box.setMaxHeight(Double.MAX_VALUE);
                g.add(box, k, r);
            }
            r++;
        }
        g.getRowConstraints().add(new javafx.scene.layout.RowConstraints());
        for (int k = 1; k < r; k++) {
            javafx.scene.layout.RowConstraints rc = new javafx.scene.layout.RowConstraints();
            rc.setMinHeight(34);
            rc.setPrefHeight(38);
            rc.setMaxHeight(52);
            rc.setVgrow(Priority.ALWAYS);
            rc.setFillHeight(true);
            g.getRowConstraints().add(rc);
        }
        VBox.setVgrow(g, Priority.ALWAYS);
        VBox v = new VBox(0, g);
        v.getStyleClass().addAll("painel", "premio-onze-painel");
        return v;
    }

    private static Posicao posicao(String s) {
        if (s == null) return null;
        try {
            return Posicao.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * O time no campo da escalação: foto redonda (silhueta sobre preto sem foto real), nome
     * abreviado, [posição | nota] na cor da faixa e o escudo do clube no canto da foto; clicar abre o
     * perfil do jogador. O campo cresce com o espaço (mantendo a proporção).
     */
    static Node campo(Jogo jogo, Historia.SelecaoDaLiga s) {
        int n = s.time.size();
        Posicao[] vagas = new Posicao[n];
        List<CampoEscalacao.Peca> pecas = new ArrayList<>();
        Mundo m = jogo == null ? null : jogo.mundo();
        for (int i = 0; i < n; i++) {
            Historia.ItemSelecao it = s.time.get(i);
            Posicao p = posicao(it.posicao);
            vagas[i] = p == null ? Posicao.MC : p;
            Jogador jg = m == null || it.jogadorId == null ? null : m.jogador(it.jogadorId);
            Node foto = Foto.de(jg == null ? null : jogo.foto(jg.id), jg == null ? null : jg.dbId, CampoEscalacao.FOTO_D, CampoEscalacao.FOTO_D);
            Node esc = it.clubeId == null ? null : Imagens.escudo(it.clubeId, 18);
            pecas.add(CampoEscalacao.Peca.premio(it.jogador, vagas[i].name(), foto, it.nota, esc));
        }
        CampoEscalacao campo = new CampoEscalacao(new CampoEscalacao.Acoes() {
            @Override
            public void clicar(int vaga) {
                Integer id = vaga < n ? s.time.get(vaga).jogadorId : null;
                if (id != null && Ui.aoAbrirJogador != null) Ui.aoAbrirJogador.accept(id);
            }

            @Override public void soltar(int vaga, String conteudo) {}

            @Override public String arraste(int vaga) { return null; }

            @Override public double encaixeDoArraste(int vaga) { return Double.NaN; }
        });
        campo.cor("#d4a72c");
        campo.desenhar(pecas, coordenadas(vagas), -1, -1);
        for (int i = 0; i < n; i++) {
            // dica com o nome inteiro e o clube
            Historia.ItemSelecao it = s.time.get(i);
            Node vg = campo.getChildren().size() > i + 1 ? campo.getChildren().get(i + 1) : null;
            if (vg != null) {
                javafx.scene.control.Tooltip.install(vg, new javafx.scene.control.Tooltip(it.jogador + (it.clube == null ? "" : " · " + it.clube)
                        + "\n" + it.jogos + " jogos · " + it.gols + " gols · " + it.assistencias + " assist. · nota " + Textos.umaCasa(it.nota)));
                if (it.jogadorId != null) vg.setCursor(javafx.scene.Cursor.HAND);
            }
        }
        CampoEscalacao.Suporte sup = campo.escalavel();
        sup.setMinSize(260, 320);
        return sup;
    }

    /**
     * Pontos das vagas da seleção (4-3-3: volante atrás, meia-central e meia ofensivo à frente dele,
     * pontas abertas), na grade do campo da escalação; vagas repetidas (dois zagueiros) se abrem na linha.
     */
    static double[][] coordenadas(Posicao[] vagas) {
        Map<Posicao, double[][]> grade = Map.ofEntries(
                Map.entry(Posicao.GOL, new double[][] {{.5, .92}}),
                Map.entry(Posicao.LD, new double[][] {{.9, .72}}), Map.entry(Posicao.LE, new double[][] {{.1, .72}}),
                Map.entry(Posicao.ZAG, new double[][] {{.63, .76}, {.37, .76}, {.5, .78}}),
                Map.entry(Posicao.VOL, new double[][] {{.5, .56}, {.3, .56}, {.7, .56}}),
                Map.entry(Posicao.MC, new double[][] {{.27, .42}, {.73, .42}}),
                Map.entry(Posicao.MEI, new double[][] {{.73, .4}, {.27, .4}, {.5, .34}}),
                Map.entry(Posicao.PD, new double[][] {{.87, .2}}), Map.entry(Posicao.PE, new double[][] {{.13, .2}}),
                Map.entry(Posicao.CA, new double[][] {{.5, .16}, {.35, .17}, {.65, .17}}));
        Map<Posicao, Integer> usados = new java.util.EnumMap<>(Posicao.class);
        double[][] xy = new double[vagas.length][2];
        for (int i = 0; i < vagas.length; i++) {
            double[][] op = grade.getOrDefault(vagas[i], new double[][] {{.5, .45}});
            int k = usados.merge(vagas[i], 1, Integer::sum) - 1;
            double[] c = op[Math.min(k, op.length - 1)];
            xy[i] = new double[] {CampoEscalacao.W * c[0], 22 + (CampoEscalacao.H - 44) * c[1]};
        }
        return CampoEscalacao.arrumar(xy);
    }

    /** O campo de uma seleção num tamanho fixo (tela de fim de temporada). */
    static Node campoFixo(Jogo jogo, Historia.SelecaoDaLiga s, double altura) {
        Node c = campo(jogo, s);
        if (c instanceof Region r) {
            r.setPrefSize(altura * CampoEscalacao.W / CampoEscalacao.H, altura);
            r.setMinSize(altura * CampoEscalacao.W / CampoEscalacao.H, altura);
            r.setMaxSize(altura * CampoEscalacao.W / CampoEscalacao.H, altura);
        }
        return c;
    }

    // ================================================================== melhor 11 (ficha do clube)
    private static final Map<String, double[]> POS = Map.ofEntries(
            Map.entry("GOL", new double[] {0.5, 0.9}), Map.entry("LD", new double[] {0.85, 0.7}), Map.entry("LE", new double[] {0.15, 0.7}),
            Map.entry("VOL", new double[] {0.5, 0.52}), Map.entry("MC", new double[] {0.28, 0.45}), Map.entry("MEI", new double[] {0.72, 0.45}),
            Map.entry("PD", new double[] {0.84, 0.2}), Map.entry("PE", new double[] {0.16, 0.2}), Map.entry("CA", new double[] {0.5, 0.14}));

    /** Uma vaga desenhada no campo: posição ("GOL", "ZAG"...), nome, linha de baixo e escudo (pode ser null). */
    record Vaga(String posicao, String nome, String info, String escudo) {}

    /** Onze num campo, com as vagas repetidas (dois zagueiros, dois volantes...) espalhadas na linha. */
    static Node campoOnze(String titulo, List<Vaga> vagas) {
        double w = 560, h = 600;
        Pane p = new Pane();
        p.setPrefSize(w, h);
        p.setMinSize(w, h);
        p.setMaxSize(w, h);
        p.getStyleClass().add("campo");
        javafx.scene.canvas.Canvas linhas = new javafx.scene.canvas.Canvas(w, h);
        var g = linhas.getGraphicsContext2D();
        g.setStroke(javafx.scene.paint.Color.rgb(255, 255, 255, 0.35));
        g.setLineWidth(1.5);
        g.strokeRect(14, 14, w - 28, h - 28);
        g.strokeOval(w / 2 - 60, 14 - 60, 120, 120);
        g.strokeRect(w / 2 - 120, h - 14 - 110, 240, 110);
        g.strokeRect(w / 2 - 55, h - 14 - 40, 110, 40);
        p.getChildren().add(linhas);
        Map<String, Integer> total = new java.util.HashMap<>(), usados = new java.util.HashMap<>();
        for (Vaga v : vagas) total.merge(chave(v.posicao()), 1, Integer::sum);
        for (Vaga v : vagas) {
            String k = chave(v.posicao());
            double[] c = POS.getOrDefault(k, new double[] {0.5, 0.5});
            int nn = total.get(k), i = usados.merge(k, 1, Integer::sum) - 1;
            double x = c[0], y = c[1];
            if (nn > 1) x = nn == 2 ? (i == 0 ? x - 0.14 : x + 0.14) : 0.25 + 0.5 * i / (nn - 1.0);
            if (k.equals("ZAG")) y = 0.74;
            Label nome = Ui.rotulo(v.nome(), "vaga-nome");
            Label info = Ui.rotulo(v.info(), "vaga-pos");
            VBox vaga = v.escudo() == null ? new VBox(2, nome, info) : new VBox(2, Ui.escudo(v.escudo(), 30), nome, info);
            vaga.setAlignment(Pos.CENTER);
            vaga.setPrefWidth(140);
            double fx = x, fy = y;
            vaga.layoutXProperty().bind(vaga.widthProperty().divide(-2).add(fx * w));
            vaga.layoutYProperty().bind(vaga.heightProperty().divide(-2).add(fy * h));
            p.getChildren().add(vaga);
        }
        VBox box = new VBox(12, Ui.rotulo(titulo, "titulo-painel"), p);
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }

    private static String chave(String pos) {
        if (pos == null) return "MC";
        String s = pos.toUpperCase(Locale.ROOT);
        return switch (s) {
            case "GK", "G" -> "GOL";
            case "ME" -> "PE";
            case "MD" -> "PD";
            case "SA", "ATA" -> "CA";
            default -> s;
        };
    }
}
