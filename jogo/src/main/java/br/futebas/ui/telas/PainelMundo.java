package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.historia.Historia;
import br.futebas.kings.LigaKings;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Tecnico;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * O mundo (histórico do seu save desde 2026): os campeões de cada competição com o troféu, o
 * histórico completo, artilheiros, a carreira e o ranking do técnico, as estrelas, recordes e
 * prêmios e a Kings League.
 */
public final class PainelMundo implements Hub.Painel {
    private final Sessao sessao;
    private static int abaAntes;
    private static String grupoAntes;
    private boolean embutido;

    public PainelMundo(Sessao sessao) {
        this.sessao = sessao;
    }

    /** Sem o título "Mundo" (quando a tela já tem uma aba com esse nome). */
    public PainelMundo embutido() {
        embutido = true;
        return this;
    }

    private Jogo j() { return sessao.jogo; }

    private Mundo m() { return sessao.jogo.mundo(); }

    @Override
    public Node construir() {
        TabPane abas = new TabPane();
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        // cada aba é montada quando aberta (antes o ranking de técnicos, a artilharia histórica e o
        // histórico eram calculados todos de uma vez ao abrir o menu)
        // só Histórico, Artilheiros e Técnicos (regra do jogo): os campeões ficam em Campeonatos
        abas.getTabs().addAll(Ui.abaSobDemanda("Histórico", this::historico), Ui.abaSobDemanda("Artilheiros", this::artilheiros),
                Ui.abaSobDemanda("Técnicos", this::tecnico));
        // sem as abas Estrelas e Kings League (a Kings tem a própria aba no menu) — regra do jogo
        if (m().tecnico == null) abas.getTabs().remove(2);
        abas.getSelectionModel().select(Math.min(abaAntes, abas.getTabs().size() - 1));
        Ui.ativarSobDemanda(abas);
        abas.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> abaAntes = b.intValue());
        int titulos = m().historia.titulos.size();
        Label resumo = Ui.rotulo("Desde 1º/jan/" + m().anoInicial + "  ·  " + titulos + (titulos == 1 ? " título decidido" : " títulos decididos"), "texto-suave");
        VBox v = embutido ? new VBox(12, abas) : Ui.conteudo(Ui.linha(10, Ui.titulo("Mundo"), Ui.espaco(), resumo), abas);
        if (embutido) v.setStyle("-fx-padding: 4 24 20 12;");
        VBox.setVgrow(abas, Priority.ALWAYS);
        return v;
    }

    public void selecionarAba(int i) {
        abaAntes = i;
    }

    // ------------------------------------------------------------------ campeões (com troféus)
    private record Grupo(String id, String nome) {}

    private Node campeoes() {
        Mundo m = m();
        Map<String, List<Historia.Titulo>> porComp = new LinkedHashMap<>();
        for (Historia.Titulo t : m.historia.titulos) if (!"kings".equals(t.ambito)) porComp.computeIfAbsent(t.competicaoId, k -> new ArrayList<>()).add(t);
        if (porComp.isEmpty()) return Ui.vazio("Nenhum campeão ainda", null, null);
        Map<String, List<String>> porGrupo = new LinkedHashMap<>();
        for (var e : porComp.entrySet()) porGrupo.computeIfAbsent(grupo(e.getValue().get(0)), k -> new ArrayList<>()).add(e.getKey());
        String meuPais = j().meuClube() != null ? j().meuClube().pais : "BRA";
        List<Grupo> grupos = new ArrayList<>();
        if (porGrupo.containsKey("selecoes")) grupos.add(new Grupo("selecoes", "Seleções"));
        if (porGrupo.containsKey("continental")) grupos.add(new Grupo("continental", "Continentais e mundiais"));
        List<String> paises = new ArrayList<>(porGrupo.keySet());
        paises.removeAll(List.of("selecoes", "continental"));
        paises.sort(Comparator.comparingInt((String p) -> p.equals(meuPais) ? 0 : m.pais(p) != null && m.pais(p).detalhado ? 1 : 2)
                .thenComparing(p -> Textos.pais(p)));
        for (String p : paises) grupos.add(new Grupo(p, Textos.pais(p)));

        ListView<Grupo> lista = new ListView<>();
        lista.getItems().setAll(grupos);
        lista.setCellFactory(x -> new ListCell<>() {
            @Override
            protected void updateItem(Grupo g, boolean vazio) {
                super.updateItem(g, vazio);
                setText(null);
                if (vazio || g == null) {
                    setGraphic(null);
                    return;
                }
                Node ic = g.id().equals("selecoes") || g.id().equals("continental")
                        ? Imagens.trofeu(g.id().equals("selecoes") ? "copa-do-mundo" : "mundial", "continental", null, 18)
                        : Imagens.bandeira(g.id(), 13);
                Label n = Ui.rotulo(g.nome());
                n.setMinWidth(0);
                HBox h = Ui.linha(10, ic, n, Ui.espaco(), Ui.rotulo(String.valueOf(porGrupo.get(g.id()).size()), "item-escolha-meta"));
                h.setMaxWidth(190);
                h.setPrefWidth(190);
                setGraphic(h);
            }
        });
        FlowPane grade = Ui.gradeFluida(300, 12);
        grade.setStyle("-fx-padding: 2 4 16 4;");
        Label titulo = Ui.rotulo("", "titulo-painel");
        boolean[] montando = {true};
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, g) -> {
            if (g == null) return;
            if (!montando[0]) grupoAntes = g.id();
            titulo.setText(g.nome());
            grade.getChildren().clear();
            List<String> comps = new ArrayList<>(porGrupo.get(g.id()));
            comps.sort(Comparator.comparingInt((String id) -> ordem(porComp.get(id).get(0))).thenComparing(id -> porComp.get(id).get(0).competicao));
            for (String id : comps) grade.getChildren().add(cartaoCompeticao(porComp.get(id)));
        });
        Grupo inicial = grupos.get(0);
        for (Grupo g : grupos) if (g.id().equals(grupoAntes) || (grupoAntes == null && g.id().equals(meuPais))) inicial = g;
        lista.getSelectionModel().select(inicial);
        lista.scrollTo(inicial);
        montando[0] = false;
        VBox col = new VBox(lista);
        col.getStyleClass().add("coluna-escolha");
        col.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: -linha;");
        VBox.setVgrow(lista, Priority.ALWAYS);
        col.setPrefWidth(240);
        col.setMinWidth(240);
        VBox dir = new VBox(10, titulo, Ui.rolar(grade));
        VBox.setVgrow(dir.getChildren().get(1), Priority.ALWAYS);
        HBox.setHgrow(dir, Priority.ALWAYS);
        return new HBox(16, col, dir);
    }

    private Node cartaoCompeticao(List<Historia.Titulo> ts) {
        List<Historia.Titulo> l = new ArrayList<>(ts);
        l.sort(Comparator.comparingInt((Historia.Titulo t) -> -t.ano));
        Historia.Titulo ult = l.get(0);
        Label comp = Ui.rotulo(Textos.curto(ult.competicao), "titulo-painel");
        comp.setWrapText(true);
        comp.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        Label nome = Ui.nomeClube(ult.campeaoId, ult.campeao, "campeao-nome");
        nome.setMinWidth(0);
        VBox atual = new VBox(1, nome, Ui.rotulo((ult.temporada == null ? String.valueOf(ult.ano) : ult.temporada)
                + (ult.vice == null ? "" : "  ·  vice: " + ult.vice), "campeao-comp"));
        atual.setMinWidth(0);
        HBox campeao = Ui.linha(10, Ui.escudo(ult.campeaoId, 34), atual);
        VBox txt = new VBox(8, comp, campeao);
        txt.setMinWidth(0);
        HBox.setHgrow(txt, Priority.ALWAYS);
        HBox topo = new HBox(14, Ui.trofeu(ult.competicaoId, ult.ambito, ult.pais, 64), txt);
        topo.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(10, topo);
        if (l.size() > 1) {
            VBox ant = new VBox(3);
            for (int i = 1; i < Math.min(4, l.size()); i++) {
                Historia.Titulo t = l.get(i);
                ant.getChildren().add(Ui.linha(8, Ui.rotulo(t.temporada == null ? String.valueOf(t.ano) : t.temporada, "manchete-data"), Ui.escudo(t.campeaoId, 16),
                        Ui.nomeClube(t.campeaoId, t.campeao, "texto-medio")));
            }
            Map<String, Integer> conta = new HashMap<>();
            for (Historia.Titulo t : l) conta.merge(t.campeao, 1, Integer::sum);
            String maior = Collections.max(conta.entrySet(), Map.Entry.comparingByValue()).getKey();
            int n = conta.get(maior);
            card.getChildren().addAll(Ui.separadorH(), ant);
            if (n > 1) card.getChildren().add(Ui.rotulo("Maior campeão: " + maior + " (" + n + ")", "texto-pequeno"));
        }
        card.getStyleClass().add("painel");
        return card;
    }

    private static String grupo(Historia.Titulo t) {
        if ("selecoes".equals(t.ambito)) return "selecoes";
        if (t.pais == null || "continental".equals(t.ambito) || "mundial".equals(t.ambito)) return "continental";
        return t.pais;
    }

    private static int ordem(Historia.Titulo t) {
        return ordemCompeticao(t.competicaoId, t.ambito);
    }

    /** Ordem de exibição: seleções, continentais, divisões nacionais, copa, supercopa, outras copas, estaduais. */
    static int ordemCompeticao(String id, String ambito) {
        if ("selecoes".equals(ambito)) return 0;
        if ("continental".equals(ambito) || "mundial".equals(ambito)) return 1;
        if ("nacional".equals(ambito)) {
            int i = id == null ? -1 : id.lastIndexOf('-');
            try {
                return 10 + Integer.parseInt(id.substring(i + 1));
            } catch (RuntimeException e) {
                return 19;
            }
        }
        if ("copa_nacional".equals(ambito)) {
            if (id != null && id.endsWith("-copa")) return 20;
            if (id != null && id.contains("supercopa")) return 21;
            return 22;
        }
        if ("estadual".equals(ambito)) return 40;
        return 30;
    }

    // ------------------------------------------------------------------ histórico completo
    private Node historico() {
        Mundo m = m();
        List<Historia.Titulo> ts = new ArrayList<>();
        for (Historia.Titulo t : m.historia.titulos) if (!"kings".equals(t.ambito)) ts.add(t);
        ts.sort(Comparator.comparingInt((Historia.Titulo t) -> -t.ano).thenComparingInt(PainelMundo::ordem).thenComparing(t -> t.competicao));
        TreeSet<String> comps = new TreeSet<>();
        for (Historia.Titulo t : ts) comps.add(Textos.curto(t.competicao));
        ComboBox<String> filtro = new ComboBox<>();
        filtro.getItems().add("Todas as competições");
        filtro.getItems().addAll(comps);
        filtro.getSelectionModel().select(0);
        filtro.setVisibleRowCount(16);
        TableView<Historia.Titulo> tv = Ui.tabela();
        Ui.col(tv, "Temporada", 84, t -> t.temporada == null ? String.valueOf(t.ano) : t.temporada);
        Ui.colNo(tv, "", 34, t -> Ui.trofeu(t.competicaoId, t.ambito, t.pais, 22));
        Ui.colNo(tv, "Competição", 230, t -> Ui.nomeCompeticao(t.competicaoId, Textos.curto(t.competicao)));
        Ui.colClube(tv, "Campeão", 200, m, t -> t.campeaoId);
        Ui.colClube(tv, "Vice", 180, m, t -> t.viceId);
        Ui.col(tv, "Artilheiro", 200, t -> t.artilheiro == null ? "" : t.artilheiro + " (" + t.golsArtilheiro + ")");
        tv.getItems().setAll(ts);
        filtro.valueProperty().addListener((o, a, s) -> {
            List<Historia.Titulo> l = new ArrayList<>();
            for (Historia.Titulo t : ts) if (s == null || s.equals(filtro.getItems().get(0)) || Textos.curto(t.competicao).equals(s)) l.add(t);
            tv.getItems().setAll(l);
        });
        VBox p = Ui.painelTabela("Todos os títulos", filtro, tv);
        VBox.setVgrow(p, Priority.ALWAYS);
        return new VBox(p);
    }

    // ------------------------------------------------------------------ artilheiros
    private Node artilheiros() {
        Mundo m = m();
        List<Jogador> todos = new ArrayList<>(j().artilheirosHistoricos(50));
        TableView<Jogador> tv = Ui.tabela();
        List<Jogador> ls = todos;
        Ui.colNum(tv, "#", 36, x -> ls.indexOf(x) + 1);
        Ui.colPos(tv, x -> x.posicao);
        Ui.colNo(tv, "Jogador", 170, x -> Ui.nomeJogador(x.id, x.nomeExibicao()));
        // gols em destaque (requisito 15/11): número grande, negrito, na cor de destaque
        Ui.colNo(tv, "Gols", 80, x -> {
            javafx.scene.control.Label g = Ui.rotulo(String.valueOf(x.golsCarreira), "artilharia-gols");
            g.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            return g;
        }).setComparator(Comparator.comparingInt(x -> x.golsCarreira));
        Ui.colNum(tv, "Jogos", 54, x -> x.jogosCarreira);
        Ui.colNum(tv, "Idade", 54, x -> x.idade(j().ano()));
        // escudo do clube ao lado (aposentado/sem clube: só o texto)
        Ui.colNo(tv, "Onde está", 200, x -> {
            if (x.clubeId != null && m.clube(x.clubeId) != null && x.status != br.futebas.mundo.StatusJogador.APOSENTADO) return Ui.clube(m, x.clubeId, 18);
            return Ui.rotulo(Textos.situacao(m, x), "texto-suave");
        });
        tv.getItems().setAll(todos);
        // só a artilharia geral (regra do jogo 15/11: sem "artilheiros por competição")
        VBox a = Ui.painelTabela("Artilharia", Ui.rotulo("gols na carreira, inclusive aposentados", "texto-pequeno"), tv);
        VBox.setVgrow(a, Priority.ALWAYS);
        return new VBox(a);
    }

    // ------------------------------------------------------------------ técnico
    private Node tecnico() {
        Tecnico t = m().tecnico;
        if (t == null) return Ui.vazio("Sem técnico", null, null);
        // carreira = clube + Kings (a Kings não tem empate)
        int vit = t.vitorias + t.kingsVitorias, der = t.derrotas + t.kingsDerrotas;
        int jogos = vit + t.empates + der;
        int aprov = jogos == 0 ? 0 : (int) Math.round(100.0 * (3 * vit + t.empates) / (3.0 * jogos));
        List<RankTec> ranking = rankingTecnicos();
        int minhaPos = 0;
        for (int i = 0; i < ranking.size(); i++) if (ranking.get(i).humano()) minhaPos = i + 1;
        HBox nums = Ui.linha(28, Ui.dado("Posição no ranking", minhaPos == 0 ? "—" : minhaPos + "º"), Ui.dado("Reputação", String.valueOf(Math.round(t.reputacao))),
                Ui.dado("Jogos", String.valueOf(jogos)), Ui.dado("V-E-D", vit + "-" + t.empates + "-" + der),
                Ui.dado("Aproveitamento", aprov + "%"), Ui.dado("Títulos", String.valueOf(t.titulos.size())), Ui.dado("Demissões", String.valueOf(t.demissoes)));
        String onde = t.clubeId == null ? "sem clube" : Textos.nomeClube(m(), t.clubeId);
        if (t.selecaoPais != null) onde += "  ·  seleção: " + Textos.pais(t.selecaoPais);
        VBox resumo = Ui.painel(t.nome, Ui.linha(10, Ui.estrelas(t.reputacao, 15), Ui.rotulo(onde, "texto-medio")), nums);
        List<Historia.TemporadaTecnico> l = new ArrayList<>(j().historia().carreiraTecnico);
        Collections.reverse(l);
        TableView<Historia.TemporadaTecnico> tv = Ui.tabela();
        tv.setPlaceholder(Ui.rotulo("A primeira temporada entra aqui quando terminar.", "texto-suave"));
        Ui.colNum(tv, "Ano", 54, x -> x.ano);
        Ui.colClube(tv, "Clube", 220, m(), x -> x.clubeId);
        Ui.colNum(tv, "J", 44, x -> x.jogos);
        Ui.colNum(tv, "V", 44, x -> x.vitorias);
        Ui.colNum(tv, "E", 44, x -> x.empates);
        Ui.colNum(tv, "D", 44, x -> x.derrotas);
        Ui.col(tv, "Títulos", 260, x -> String.join(", ", x.titulos));
        Ui.col(tv, "Saída", 120, x -> x.saida == null ? "" : x.saida);
        tv.getItems().setAll(l);
        Ui.alturaPorLinhas(tv, Math.max(1, Math.min(14, l.size())));
        VBox car = Ui.painelTabela("Temporada a temporada", null, tv);
        return Ui.rolar(new VBox(16, resumo, tabelaRanking(ranking), car));
    }

    /** Uma linha do ranking de técnicos (todos os técnicos do mundo + você). */
    private record RankTec(String nome, String clubeId, String pais, int jogos, int v, int e, int d, int titulos, boolean humano, double pontos) {
        int aproveitamento() { return jogos == 0 ? 0 : (int) Math.round(100.0 * (3 * v + e) / (3.0 * jogos)); }
    }

    /**
     * Ranking de técnicos (regra do jogo: títulos, vitórias, aproveitamento, derrotas...): 12 por título,
     * 2 por vitória, 1 por empate, −1 por derrota e até 40 pelo aproveitamento (pesa por completo a partir de 40 jogos).
     */
    private List<RankTec> rankingTecnicos() {
        List<RankTec> l = new ArrayList<>();
        for (br.futebas.mundo.Treinador x : m().treinadores) {
            int jg = x.vitorias + x.empates + x.derrotas;
            if (jg == 0 && x.titulos.isEmpty()) continue;
            l.add(rank(x.nome, x.aposentado ? null : x.clubeId, x.pais, jg, x.vitorias, x.empates, x.derrotas, x.titulos.size(), false));
        }
        Tecnico t = m().tecnico;
        if (t != null) {
            int v = t.vitorias + t.kingsVitorias, d = t.derrotas + t.kingsDerrotas;
            l.add(rank(t.nome, t.clubeId != null ? t.clubeId : t.kingsTimeId, null, v + t.empates + d, v, t.empates, d, t.titulos.size(), true));
        }
        l.sort((a, b) -> Double.compare(b.pontos(), a.pontos()));
        return l;
    }

    private static RankTec rank(String nome, String clube, String pais, int jg, int v, int e, int d, int tit, boolean humano) {
        double aprov = jg == 0 ? 0 : (3.0 * v + e) / (3.0 * jg);
        double pts = 12 * tit + 2 * v + e - d + 40 * aprov * Math.min(1, jg / 40.0);
        return new RankTec(nome, clube, pais, jg, v, e, d, tit, humano, Math.round(pts * 10) / 10.0);
    }

    private Node tabelaRanking(List<RankTec> todos) {
        List<RankTec> l = new ArrayList<>(todos.subList(0, Math.min(100, todos.size())));
        RankTec eu = null;
        for (RankTec x : todos) if (x.humano()) eu = x;
        if (eu != null && !l.contains(eu)) l.add(eu); // você sempre aparece
        TableView<RankTec> tv = Ui.tabela();
        tv.setPlaceholder(Ui.rotulo("O ranking começa depois dos primeiros jogos.", "texto-suave"));
        Ui.colNum(tv, "#", 44, x -> todos.indexOf(x) + 1);
        Ui.colNo(tv, "Técnico", 200, x -> {
            Label n = Ui.rotulo(x.nome(), x.humano() ? "texto-forte" : null);
            if (x.humano()) n.setStyle("-fx-text-fill: -acento-nav;");
            return x.pais() == null ? n : Ui.linha(7, Imagens.bandeira(x.pais(), 11), n);
        });
        Ui.colNo(tv, "Clube", 200, x -> x.clubeId() == null ? Ui.rotulo("sem clube", "texto-suave") : Ui.clube(m(), x.clubeId(), 18));
        Ui.colNum(tv, "Títulos", 60, RankTec::titulos);
        Ui.colNum(tv, "J", 50, RankTec::jogos);
        Ui.colNum(tv, "V", 50, RankTec::v);
        Ui.colNum(tv, "E", 50, RankTec::e);
        Ui.colNum(tv, "D", 50, RankTec::d);
        Ui.col(tv, "Aprov.", 66, x -> x.jogos() == 0 ? "—" : x.aproveitamento() + "%");
        var pts = Ui.colNum(tv, "Pontos", 70, RankTec::pontos);
        pts.getStyleClass().add("col-pontos");
        tv.getItems().setAll(l);
        Ui.alturaPorLinhas(tv, Math.min(15, Math.max(3, l.size())));
        return Ui.painelTabela("Ranking de técnicos", Ui.rotulo("12 por título · 2 por vitória · 1 por empate · −1 por derrota · até 40 pelo aproveitamento", "texto-pequeno"), tv);
    }

    // ------------------------------------------------------------------ estrelas
    private Node estrelas() {
        List<Jogador> l = new ArrayList<>();
        for (Jogador x : m().jogadores) if (x.status == br.futebas.mundo.StatusJogador.PROFISSIONAL && !x.amador()) l.add(x);
        l.sort(Comparator.comparingDouble((Jogador x) -> -x.forca).thenComparingInt(x -> x.id));
        if (l.size() > 100) l = new ArrayList<>(l.subList(0, 100));
        List<Jogador> ls = l;
        TableView<Jogador> tv = Ui.tabela();
        Ui.colNum(tv, "#", 40, x -> ls.indexOf(x) + 1);
        Ui.colPos(tv, x -> x.posicao);
        Ui.colNo(tv, "Jogador", 200, x -> Ui.nomeJogador(x.id, x.nomeExibicao() + (x.topMundial ? "  ★★" : x.estrela ? "  ★" : "")));
        Ui.colForca(tv, "Força", Jogador::forcaExibida);
        Ui.colNum(tv, "Idade", 56, x -> x.idade(j().ano()));
        Ui.colNo(tv, "País", 56, x -> Imagens.bandeira(x.pais, 13));
        Ui.colClube(tv, "Clube", 220, m(), x -> x.clubeId);
        tv.getItems().setAll(l);
        return Ui.painelTabela("Os 100 melhores do mundo agora", Ui.rotulo("★ estrela  ·  ★★ top mundial", "texto-pequeno"), tv);
    }

    // ------------------------------------------------------------------ recordes e prêmios
    private Node recordes() {
        Mundo m = m();
        VBox rec = new VBox(8);
        if (m.historia.recordes.isEmpty()) rec.getChildren().add(Ui.rotulo("Nenhum recorde ainda.", "texto-suave"));
        for (Historia.Recorde r : m.historia.recordes.values()) {
            rec.getChildren().add(new VBox(1, Ui.rotulo(r.nome, "texto-forte"), Ui.paragrafo(r.descricao, "texto-medio")));
        }
        TableView<Historia.Premio> tv = Ui.tabela();
        tv.setPlaceholder(Ui.rotulo("Os prêmios saem no fim de cada temporada.", "texto-suave"));
        List<Historia.Premio> ps = new ArrayList<>(m.historia.premios);
        ps.sort(Comparator.comparingInt((Historia.Premio p) -> -p.ano));
        Ui.colNum(tv, "Ano", 54, p -> p.ano);
        Ui.col(tv, "Prêmio", 300, p -> p.titulo);
        Ui.col(tv, "Jogador", 170, p -> p.jogador);
        Ui.col(tv, "Clube", 160, p -> p.clube == null ? "" : p.clube);
        tv.getItems().setAll(ps);
        VBox recP = Ui.painel("Recordes", rec);
        recP.setPrefWidth(380);
        recP.setMinWidth(320);
        VBox pr = Ui.painelTabela("Prêmios da temporada", null, tv);
        HBox.setHgrow(pr, Priority.ALWAYS);
        HBox h = new HBox(16, Ui.rolar(recP), pr);
        ((javafx.scene.control.ScrollPane) h.getChildren().get(0)).setMinWidth(340);
        ((javafx.scene.control.ScrollPane) h.getChildren().get(0)).setPrefWidth(380);
        return h;
    }

    /** Lista de manchetes (data, etiqueta do tipo, texto), usada também em outras telas. */
    static ListView<Historia.Evento> listaManchetes() {
        ListView<Historia.Evento> lv = new ListView<>();
        lv.setCellFactory(x -> new ListCell<>() {
            @Override
            protected void updateItem(Historia.Evento e, boolean vazio) {
                super.updateItem(e, vazio);
                setText(null);
                if (vazio || e == null) {
                    setGraphic(null);
                    return;
                }
                Label t = Ui.paragrafo(e.texto, "manchete-texto");
                t.maxWidthProperty().bind(lv.widthProperty().subtract(40));
                HBox topo = Ui.linha(8, Ui.rotulo(Textos.mes(e.semana) + "/" + e.ano, "manchete-data"));
                Node et = etiquetaEvento(e.tipo);
                if (et != null) topo.getChildren().add(et);
                VBox v = new VBox(3, topo, t);
                if ("titulo".equals(e.tipo)) t.getStyleClass().add("texto-forte");
                setGraphic(v);
            }
        });
        return lv;
    }

    private static Node etiquetaEvento(String tipo) {
        if (tipo == null) return null;
        return switch (tipo) {
            case "titulo" -> Ui.etiqueta("Título", "verde");
            case "acesso" -> Ui.etiqueta("Acesso", "verde");
            case "rebaixamento" -> Ui.etiqueta("Queda", "vermelha");
            case "falencia", "crise" -> Ui.etiqueta("Crise", "vermelha");
            case "kings" -> Ui.etiqueta("Kings League", "escura");
            case "tecnico" -> Ui.etiqueta("Técnico", "azul");
            case "estrela" -> Ui.etiqueta("Estrela", "ambar");
            case "transferencia" -> Ui.etiqueta("Mercado", "azul");
            default -> null;
        };
    }

    // ------------------------------------------------------------------ kings
    private Node kings() {
        FlowPane fp = Ui.gradeFluida(300, 12);
        List<LigaKings> ls = j().ligasKings();
        if (ls.isEmpty()) return Ui.vazio("Nenhuma Kings League cadastrada no banco.", null, null);
        for (LigaKings l : ls) {
            String status = l.ativa ? "Em atividade desde " + l.anoInicio + "  ·  " + l.temporadas + (l.temporadas == 1 ? " temporada" : " temporadas")
                    : l.anoInicio > 3000 ? "Início a confirmar" : "Começa em " + l.anoInicio;
            br.futebas.competicao.Torneio tk = l.torneio != null ? l.torneio : l.competicoes.isEmpty() ? null : l.competicoes.get(0);
            Node logoK = Imagens.logoKings(l.id, 34);
            if (tk != null && Ui.aoAbrirCompeticao != null) {
                logoK.setCursor(javafx.scene.Cursor.HAND);
                logoK.setOnMouseClicked(e -> Ui.aoAbrirCompeticao.accept(tk.id));
            }
            VBox c = new VBox(8, Ui.linha(10, logoK, tk == null ? Ui.rotulo(Textos.ligaKings(l.id, l.nome), "titulo-painel")
                    : Ui.nomeCompeticao(tk.id, Textos.ligaKings(l.id, l.nome), "titulo-painel")),
                    Ui.rotulo(status + "  ·  " + l.times.size() + " times", "texto-suave"));
            if (!l.campeoes.isEmpty()) {
                VBox camp = new VBox(2);
                for (int i = l.campeoes.size() - 1; i >= 0 && camp.getChildren().size() < 4; i--) camp.getChildren().add(Ui.rotulo(l.campeoes.get(i), "texto-medio"));
                c.getChildren().addAll(Ui.separadorH(), Ui.rotulo("Campeões", "titulo-menor"), camp);
            }
            c.getStyleClass().add("painel");
            fp.getChildren().add(c);
        }
        return Ui.rolar(fp);
    }
}
