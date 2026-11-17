package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.kings.LigaKings;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.partida.ResultadoPartida;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Kings League: as competições da Kings (cada liga com Split 1, Kings Cup e Split 2; depois a
 * Kings World Cup Clubs e, por último, a Kings World Cup Nations), as vagas abertas de técnico
 * (só times sem técnico) e, para quem treina um time, o elenco e as prioridades do draft.
 */
final class PainelKings implements Hub.Painel {
    private static final String CLUBES = "kings-world-cup-clubs", NACOES = "kings-world-cup-nations";
    private static final String MUNDIAL_CLUBES = "kings-mundial-clubes", MUNDIAL_NACOES = "kings-mundial-selecoes";

    private final Hub hub;
    /** Liga escolhida na lista (id da liga ou de um mundial) e competição escolhida dentro dela. */
    private static String ligaAntes, compAntes;
    private static String abaAntes = "competicoes";

    /** Aba que abre na próxima vez (competicoes, time, draft). */
    static void abrirAba(String id) { abaAntes = id; }

    private final StackPane detalhe = new StackPane();
    private final VBox navLigas = new VBox(2);

    PainelKings(Hub hub) {
        this.hub = hub;
    }

    @Override
    public Node construir() {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        if (j.estadoDraft() != null) {
            Node d = new PainelDraft(hub).construir();
            if (d != null) return d;
        }
        String meuTime = m.tecnico == null ? null : m.tecnico.kingsTimeId;
        LigaKings minhaLiga = j.ligaKingsDoTime(meuTime);
        LigaKings.TimeKings meu = minhaLiga == null ? null : minhaLiga.time(meuTime);

        HBox cab = Ui.linha(12, Imagens.logoKings(null, 34), Ui.titulo("Kings League"), Ui.espaco());
        if (meu != null) {
            Label nome = Ui.nomeClube(meu.id, meu.nome, "texto-forte");
            HBox chip = Ui.linha(8, Ui.escudo(meu.id, 26), new VBox(0, Ui.rotulo("Seu time", "texto-pequeno"), nome));
            chip.getStyleClass().add("kg-meu-chip");
            cab.getChildren().add(chip);
        }

        TabPane abas = new TabPane();
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        abas.getTabs().add(aba("competicoes", "Competições", competicoes(j, m, minhaLiga, meuTime)));
        if (meu != null) {
            abas.getTabs().add(aba("draft", "Prioridades do draft", Ui.rolar(draft(j, m, minhaLiga, meu))));
            abas.getTabs().add(aba("pool", "No draft", Ui.rolar(noDraft(j, m, minhaLiga, meu))));
            abas.getTabs().add(aba("draftados", "Draftados", Ui.rolar(draftados(j, m, minhaLiga, meu))));
        }
        for (Tab t : abas.getTabs()) if (t.getId().equals(abaAntes)) abas.getSelectionModel().select(t);
        abas.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) abaAntes = b.getId();
        });

        VBox v = Ui.conteudo(cab);
        Node vagas = vagas(j, meu != null);
        if (vagas != null) v.getChildren().add(vagas);
        v.getChildren().add(abas);
        VBox.setVgrow(abas, Priority.ALWAYS);
        return v;
    }

    private static Tab aba(String id, String nome, Node n) {
        Tab t = new Tab(nome, n);
        t.setId(id);
        return t;
    }

    // ================================================================== competições
    /** Uma entrada da lista da esquerda: uma liga (com as competições do ano) ou um dos mundiais. */
    private record Entrada(String id, String nome, String pais, LigaKings liga) {}

    private List<Entrada> entradas(Jogo j) {
        List<Entrada> l = new ArrayList<>();
        List<LigaKings> ligas = new ArrayList<>(j.ligasKings());
        ligas.sort(Comparator.comparingInt((LigaKings x) -> Textos.ordemLigaKings(x.id)).thenComparing(x -> x.nome));
        for (LigaKings x : ligas) {
            if (br.futebas.kings.Kings.ehMundial(x)) continue;
            l.add(new Entrada(x.id, Textos.ligaKings(x.id, x.nome), x.pais, x));
        }
        l.add(new Entrada(CLUBES, Textos.ligaKings(CLUBES, "Kings World Cup Clubs"), null, null));
        l.add(new Entrada(NACOES, Textos.ligaKings(NACOES, "Kings World Cup Nations"), null, null));
        return l;
    }

    private Node competicoes(Jogo j, Mundo m, LigaKings minhaLiga, String meuId) {
        List<Entrada> es = entradas(j);
        if (ligaAntes == null || es.stream().noneMatch(e -> e.id().equals(ligaAntes)))
            ligaAntes = minhaLiga != null ? minhaLiga.id : es.isEmpty() ? null : es.get(0).id();
        navLigas.getStyleClass().add("kg-nav");
        Runnable[] redesenhar = new Runnable[1];
        redesenhar[0] = () -> {
            navLigas.getChildren().clear();
            boolean mundiais = false;
            for (Entrada e : es) {
                if (e.liga() == null && !mundiais) {
                    mundiais = true;
                    navLigas.getChildren().add(Ui.rotulo("MUNDIAIS", "kg-nav-grupo"));
                }
                Label nome = Ui.rotulo(e.nome(), "kg-nav-nome");
                nome.setMinWidth(0);
                VBox txt = new VBox(0, nome);
                if (e.liga() != null) {
                    String sub = e.liga().ativa ? "Temporada " + e.liga().anoTemporada : e.liga().anoInicio > j.ano() ? "Estreia em " + e.liga().anoInicio : "Sem jogos agora";
                    txt.getChildren().add(Ui.rotulo(sub, "texto-pequeno"));
                }
                HBox.setHgrow(txt, Priority.ALWAYS);
                HBox row = Ui.linha(10, Imagens.logoKings(e.id(), 30), txt);
                if (e.pais() != null) row.getChildren().add(Imagens.bandeira(e.pais(), 12));
                if (minhaLiga != null && e.id().equals(minhaLiga.id)) row.getChildren().add(Ui.etiqueta("Seu time", "verde"));
                row.getStyleClass().add("kg-nav-item");
                if (e.id().equals(ligaAntes)) row.getStyleClass().add("ativo");
                row.setOnMouseClicked(ev -> {
                    ligaAntes = e.id();
                    compAntes = null;
                    redesenhar[0].run();
                });
                navLigas.getChildren().add(row);
            }
            Entrada atual = es.stream().filter(e -> e.id().equals(ligaAntes)).findFirst().orElse(null);
            detalhe.getChildren().setAll(atual == null ? Ui.vazio("Nenhuma liga da Kings.", null, null) : entrada(j, m, atual, meuId));
        };
        redesenhar[0].run();
        ScrollPane nav = Ui.rolar(navLigas);
        nav.setPrefWidth(260);
        nav.setMinWidth(230);
        nav.getStyleClass().add("kg-nav-rolar");
        HBox h = new HBox(16, nav, detalhe);
        HBox.setHgrow(detalhe, Priority.ALWAYS);
        h.setStyle("-fx-padding: 14 0 0 0;");
        return h;
    }

    private static String nomeComp(Torneio t) {
        if (t.id.endsWith("-split1")) return "Split 1";
        if (t.id.endsWith("-split2")) return "Split 2";
        if (t.id.endsWith("-cup")) return "Kings Cup";
        if (t.id.equals(MUNDIAL_CLUBES)) return "Kings World Cup Clubs";
        if (t.id.equals(MUNDIAL_NACOES)) return "Kings World Cup Nations";
        return Textos.curto(t.nome);
    }

    private static int ordemComp(Torneio t) {
        return t.id.endsWith("-split1") ? 0 : t.id.endsWith("-cup") ? 1 : t.id.endsWith("-split2") ? 2 : 3;
    }

    private static String status(Jogo j, Torneio t) {
        if (t.encerrado) return "Encerrado";
        boolean jogou = t.partidas.stream().anyMatch(p -> p.jogada);
        if (jogou) return "Em andamento";
        int abs = Calendario.absoluta(t.ano, t.semanaInicio);
        return "Começa em " + Textos.dataCurta(Calendario.inicio(abs));
    }

    private Node entrada(Jogo j, Mundo m, Entrada e, String meuId) {
        List<Torneio> comps = new ArrayList<>();
        if (e.liga() != null) comps.addAll(e.liga().competicoes);
        else {
            Torneio t = j.competicaoKings(e.id().equals(CLUBES) ? MUNDIAL_CLUBES : MUNDIAL_NACOES);
            if (t != null) comps.add(t);
        }
        comps.sort(Comparator.comparingInt(PainelKings::ordemComp));

        // faixa da liga
        Node logo = Imagens.logoKings(e.id(), 64);
        Label nome = Ui.rotulo(e.nome(), "kg-liga-nome");
        VBox id = new VBox(4, nome);
        if (e.liga() != null) {
            LigaKings l = e.liga();
            HBox sub = Ui.linha(8);
            if (l.pais != null) sub.getChildren().addAll(Imagens.bandeira(l.pais, 13), Ui.rotulo(Textos.pais(l.pais), "texto-suave"));
            sub.getChildren().add(Ui.rotulo(l.times.size() + " times" + (l.ativa ? "  ·  temporada " + l.anoTemporada : ""), "texto-suave"));
            id.getChildren().add(sub);
        } else {
            id.getChildren().add(Ui.rotulo(e.id().equals(CLUBES) ? "Campeões das ligas da Kings" : "Seleções da Kings", "texto-suave"));
        }
        HBox faixa = Ui.linha(16, logo, id, Ui.espaco());
        if (e.liga() != null && !e.liga().campeoes.isEmpty()) {
            VBox camp = new VBox(2, Ui.rotulo("ÚLTIMOS CAMPEÕES", "kg-rotulo"));
            List<String> cs = e.liga().campeoes;
            for (int i = cs.size() - 1; i >= Math.max(0, cs.size() - 3); i--) camp.getChildren().add(Ui.rotulo(cs.get(i), "texto-pequeno"));
            faixa.getChildren().add(camp);
        }
        faixa.getStyleClass().add("kg-faixa");

        VBox v = new VBox(14, faixa);
        if (comps.isEmpty()) {
            String quando = e.liga() == null ? "Ainda não disputado neste ano"
                    : e.liga().anoInicio > 3000 ? "Data a confirmar"
                    : e.liga().anoInicio > j.ano() ? "Estreia em " + e.liga().anoInicio
                    : "Próxima temporada a partir de " + Textos.mes(e.liga().semanaInicio) + "/" + (e.liga().semanaInicio < j.semana() ? j.ano() + 1 : j.ano());
            v.getChildren().add(Ui.painel(null, Ui.vazio(quando, null, null)));
            if (e.liga() != null) v.getChildren().add(times(e.liga()));
            return Ui.rolar(v);
        }
        // escolha da competição (Split 1 / Kings Cup / Split 2)
        Torneio escolhida = null;
        for (Torneio t : comps) if (t.id.equals(compAntes)) escolhida = t;
        if (escolhida == null && e.liga() != null && comps.contains(e.liga().torneio)) escolhida = e.liga().torneio;
        if (escolhida == null) escolhida = comps.get(0);
        if (comps.size() > 1) {
            HBox seg = new HBox(8);
            for (Torneio t : comps) {
                HBox b = Ui.linha(8, Imagens.trofeu(t.id, "kings", t.pais, 22), Ui.rotulo(nomeComp(t), "kg-seg-nome"));
                b.getStyleClass().add("kg-seg");
                if (t == escolhida) b.getStyleClass().add("ativo");
                b.setOnMouseClicked(ev -> {
                    compAntes = t.id;
                    detalhe.getChildren().setAll(entrada(j, m, e, meuId));
                });
                seg.getChildren().add(b);
            }
            v.getChildren().add(seg);
        }
        v.getChildren().add(competicao(j, m, escolhida, e.liga(), meuId));
        return Ui.rolar(v);
    }

    /** Uma competição: troféu, situação, campeão, tabela/grupos/chaveamento e rodadas. */
    private Node competicao(Jogo j, Mundo m, Torneio t, LigaKings liga, String meuId) {
        Function<String, String> nome = j::nomeParticipante;
        Label tit = Ui.nomeCompeticao(t.id, nomeComp(t) + " " + t.ano, "kg-comp-nome");
        String st = status(j, t);
        Label chip = Ui.etiqueta(st, t.encerrado ? "escura" : st.equals("Em andamento") ? "verde" : "azul");
        HBox cab = Ui.linha(12, Ui.trofeu(t.id, "kings", t.pais, 46), new VBox(4, tit, chip), Ui.espaco());
        if (t.campeao != null) {
            HBox camp = Ui.linha(8, Ui.etiqueta("Campeão", "verde"), Ui.escudo(t.campeao, 26), Ui.nomeClube(t.campeao, nome.apply(t.campeao), "texto-forte"));
            cab.getChildren().add(camp);
        }
        cab.getChildren().add(Ui.botao("Página da competição", () -> hub.abrirCompeticao(t.id), "fantasma"));
        VBox topo = Ui.painel(null, cab);

        // fase atual
        VBox esq = new VBox(12);
        FaseExec f = t.faseAtual();
        if (f != null && !f.iniciada && f.indice == 0 && !t.encerrado && (t.fases.isEmpty() || !t.fases.get(0).iniciada)) f = null;
        if (f == null && t.encerrado && !t.fases.isEmpty()) f = t.fases.get(t.fases.size() - 1);
        if (f == null) {
            FlowPane fp = new FlowPane(14, 10);
            for (String p : t.participantes) fp.getChildren().add(Ui.linha(8, Ui.escudo(p, 22), Ui.nomeClube(p, nome.apply(p))));
            esq.getChildren().add(Ui.painel("Participantes", fp));
        } else if (f.mataMata()) {
            List<FaseExec> mata = new ArrayList<>();
            for (FaseExec x : t.fases) if (x.mataMata()) mata.add(x);
            ScrollPane sp = new ScrollPane(PainelTabelas.chaveamento(t, mata, nome, meuId));
            sp.setFitToHeight(true);
            sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            sp.setPrefViewportHeight(360);
            esq.getChildren().add(Ui.painel(f.nome == null ? "Mata-mata" : f.nome, sp));
            // a fase de liga encerrada continua visível embaixo
            for (FaseExec x : t.fases) if (!x.mataMata() && x.numGrupos <= 1 && x.iniciada) esq.getChildren().add(tabela(m, t, x, liga, nome, meuId));
        } else if (f.numGrupos > 1) {
            esq.getChildren().add(Ui.painel(f.nome == null ? "Grupos" : f.nome, PainelTabelas.conteudoFase(m, t, f, nome, meuId)));
        } else {
            esq.getChildren().add(tabela(m, t, f, liga, nome, meuId));
        }

        // rodadas
        VBox dir = new VBox(14);
        Map<Integer, List<Partida>> porData = new TreeMap<>();
        for (Partida p : t.partidas) porData.computeIfAbsent(p.data, k -> new ArrayList<>()).add(p);
        List<Partida> ultima = null, proxima = null;
        for (List<Partida> ps : porData.values()) {
            if (ps.stream().anyMatch(p -> p.jogada)) ultima = ps;
            if (proxima == null && ps.stream().anyMatch(p -> !p.jogada)) proxima = ps;
        }
        if (proxima != null) dir.getChildren().add(rodada("Próxima rodada", t, proxima, nome, meuId));
        if (ultima != null && ultima != proxima) dir.getChildren().add(rodada("Última rodada", t, ultima, nome, meuId));
        if (proxima == null && ultima == null) dir.getChildren().add(Ui.painel("Jogos", Ui.rotulo("Jogos ainda não sorteados", "texto-suave")));

        VBox v = new VBox(14, topo, esq, dir);
        v.setMinWidth(0);
        return v;
    }

    private static Node tabela(Mundo m, Torneio t, FaseExec f, LigaKings liga, Function<String, String> nome, String meuId) {
        List<LinhaTabela> linhas = t.tabela(f.indice, 0);
        Zonas z = Zonas.de(t, f, linhas.size());
        int classificam = t.fases.size() > f.indice + 1 ? f.saida : 0;
        if (liga != null && classificam == 0) classificam = liga.regras.classificados_playoff;
        TableView<LinhaTabela> tv = PainelTabelas.tabelaClassificacao(m, t, linhas, nome, meuId, 0, 0, classificam, true, z.vazia() ? null : z);
        VBox p = Ui.painelTabela(f.nome == null ? "Classificação" : f.nome, null, tv);
        if (z.vazia()) return p;
        return new VBox(8, p, z.legenda());
    }

    private static VBox rodada(String titulo, Torneio t, List<Partida> ps, Function<String, String> nome, String meuId) {
        Partida p0 = ps.get(0);
        String fase = Textos.fase(t, p0);
        Label sub = Ui.rotulo(Textos.dataCurta(p0.data()) + (fase.isEmpty() ? "" : "  ·  " + fase), "texto-suave");
        List<Partida> l = ps.size() > 12 ? ps.subList(0, 12) : ps;
        return Ui.painelAcoes(titulo, sub, PainelTabelas.listaJogos(l, nome, meuId));
    }

    /** Times da liga (quando ainda não há competição no ano). */
    private Node times(LigaKings l) {
        FlowPane fp = Ui.gradeFluida(150, 10);
        for (LigaKings.TimeKings t : l.times) {
            Label nome = Ui.nomeClube(t.id, t.nome, "nome");
            nome.setWrapText(true);
            nome.setMaxWidth(Double.MAX_VALUE);
            nome.setMinHeight(34);
            nome.setAlignment(Pos.TOP_CENTER);
            Label pres = Ui.rotulo(t.presidente == null ? " " : t.presidente, "texto-pequeno");
            VBox card = new VBox(6, Ui.escudo(t.id, 56), nome, pres);
            card.setAlignment(Pos.TOP_CENTER);
            card.getStyleClass().add("cartao-clube");
            fp.getChildren().add(card);
        }
        return Ui.painel("Times", fp);
    }

    // ================================================================== vagas abertas
    /**
     * Vagas de técnico: só times sem técnico (vaga aberta). No início do jogo, quem ainda não tem
     * time da Kings escolhe um numa linha compacta (liga → time), sem lista de todos os times.
     */
    private Node vagas(Jogo j, boolean temTime) {
        List<LigaKings.TimeKings> vs;
        try {
            vs = j.vagasKings();
        } catch (RuntimeException e) {
            return null;
        }
        if (vs == null || vs.isEmpty()) return null;
        int agora = Calendario.absoluta(j.ano(), j.semana());
        List<LigaKings.TimeKings> abertas = new ArrayList<>();
        for (LigaKings.TimeKings t : vs) if (t.vagaAte >= agora && t.vagaAte > 0) abertas.add(t);
        // carreira exclusiva: a escolha de início só aparece para quem ainda não tem emprego
        boolean inicio = j.jogoNovo() && !temTime && j.meuClube() == null;
        // regra do jogo (13/11): as vagas da Kings ficam só no painel Vagas; aqui só a escolha de início de carreira
        if (!inicio) return null;
        VBox lista = new VBox(8);
        for (LigaKings.TimeKings t : abertas) {
            LigaKings l = j.ligaKingsDoTime(t.id);
            Label nome = Ui.nomeClube(t.id, t.nome, "texto-forte");
            VBox txt = new VBox(1, nome, Ui.rotulo((l == null ? "" : Textos.ligaKings(l.id, l.nome)) + (t.presidente == null ? "" : "  ·  " + t.presidente), "texto-pequeno"));
            HBox.setHgrow(txt, Priority.ALWAYS);
            Label ate = Ui.rotulo("até " + Textos.dataCurta(Calendario.inicio(t.vagaAte)), "texto-pequeno");
            lista.getChildren().add(Ui.linha(12, Ui.escudo(t.id, 30), txt, ate, botaoCandidatar(j, t)));
        }
        if (inicio && abertas.isEmpty()) {
            ComboBox<LigaKings.TimeKings> cb = new ComboBox<>();
            cb.getItems().setAll(vs);
            cb.setPromptText("Escolha um time");
            cb.setPrefWidth(300);
            cb.setCellFactory(x -> celulaTime(j));
            cb.setButtonCell(celulaTime(j));
            Button b = Ui.botao("Candidatar-se", () -> {
                LigaKings.TimeKings t = cb.getValue();
                if (t != null) candidatar(j, t);
            }, "primario");
            b.disableProperty().bind(cb.valueProperty().isNull());
            lista.getChildren().add(Ui.linha(10, Ui.rotulo("Início de carreira", "texto-medio"), cb, b));
        }
        VBox p = Ui.painelAcoes("Vagas abertas", abertas.isEmpty() ? null : Ui.etiqueta(String.valueOf(abertas.size()), "verde"), lista);
        p.getStyleClass().add("kg-vagas");
        return p;
    }

    private static ListCell<LigaKings.TimeKings> celulaTime(Jogo j) {
        return new ListCell<>() {
            @Override
            protected void updateItem(LigaKings.TimeKings t, boolean vazio) {
                super.updateItem(t, vazio);
                if (vazio || t == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                LigaKings l = j.ligaKingsDoTime(t.id);
                setText(null);
                setGraphic(Ui.linha(8, Imagens.escudo(t.id, 18), Ui.rotulo(t.nome), Ui.rotulo(l == null ? "" : Textos.ligaKings(l.id, l.nome), "texto-pequeno")));
            }
        };
    }

    private Button botaoCandidatar(Jogo j, LigaKings.TimeKings t) {
        return Ui.botao("Candidatar-se", () -> candidatar(j, t), "primario");
    }

    private void candidatar(Jogo j, LigaKings.TimeKings t) {
        var r = j.candidatarKings(t.id);
        if (r == null) Ui.informar("Kings League", "O presidente recusou.");
        else Ui.informar("Kings League", "Convite recebido: responda na caixa de entrada.");
        hub.atualizarTopo();
        hub.atualizar();
    }

    private Node draft(Jogo j, Mundo m, LigaKings liga, LigaKings.TimeKings time) {
        List<Jogador> eleg = j.elegiveisDraft(liga.id, 80);
        ListView<Jogador> esq = new ListView<>();
        ListView<Jogador> dir = new ListView<>();
        esq.getItems().setAll(eleg);
        List<Jogador> pri = new ArrayList<>();
        for (int id : time.prioridadesDraft) if (m.jogador(id) != null) pri.add(m.jogador(id));
        dir.getItems().setAll(pri);
        for (ListView<Jogador> lv : List.of(esq, dir)) {
            lv.setCellFactory(x -> new ListCell<>() {
                @Override
                protected void updateItem(Jogador p, boolean vazio) {
                    super.updateItem(p, vazio);
                    if (vazio || p == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    setText(null);
                    setGraphic(Ui.linha(8, Ui.pos(p.posicao), Ui.rotulo(p.nomeExibicao()), Ui.espaco(),
                            Ui.rotulo(p.idade(j.ano()) + " anos", "texto-suave"), Ui.forca(p.forcaExibida())));
                }
            });
            lv.setPrefHeight(420);
        }
        Runnable salvar = () -> {
            List<Integer> ids = new ArrayList<>();
            for (Jogador x : dir.getItems()) ids.add(x.id);
            j.definirPrioridadesDraft(ids);
        };
        VBox botoes = new VBox(6,
                Ui.botao("Adicionar ▸", () -> {
                    Jogador x = esq.getSelectionModel().getSelectedItem();
                    if (x != null && !dir.getItems().contains(x)) dir.getItems().add(x);
                    salvar.run();
                }),
                Ui.botao("▲ Subir", () -> mover(dir, -1, salvar)),
                Ui.botao("▼ Descer", () -> mover(dir, 1, salvar)),
                Ui.botao("Remover", () -> {
                    int i = dir.getSelectionModel().getSelectedIndex();
                    if (i >= 0) dir.getItems().remove(i);
                    salvar.run();
                }, "perigo"));
        for (Node n : botoes.getChildren()) ((Button) n).setMaxWidth(Double.MAX_VALUE);
        botoes.setAlignment(Pos.CENTER);
        VBox a = new VBox(6, Ui.rotulo("Elegíveis", "titulo-menor"), esq);
        VBox b = new VBox(6, Ui.rotulo("Suas prioridades", "titulo-menor"), dir);
        HBox.setHgrow(a, Priority.ALWAYS);
        HBox.setHgrow(b, Priority.ALWAYS);
        HBox h = new HBox(10, a, botoes, b);
        Label regra = Ui.rotulo(liga.draft.rodadas + " rodadas  ·  " + liga.draft.ordem + "  ·  " + liga.draft.jogadores_por_time + " por time", "texto-suave");
        VBox p = Ui.painelAcoes("Draft", regra, h);
        VBox v = new VBox(p);
        v.setStyle("-fx-padding: 14 0 0 0;");
        return v;
    }

    // ================================================================== draft: quem está no draft e os draftados
    private static String filtroPosDraft, origemDraft;
    private static int anoDraftados;

    /** Todos os elegíveis para o próximo draft da liga (com a origem e a sua marca de prioridade). */
    private Node noDraft(Jogo j, Mundo m, LigaKings liga, LigaKings.TimeKings time) {
        List<Jogador> todos = j.elegiveisDraft(liga.id, 400);
        ComboBox<String> pos = new ComboBox<>();
        pos.getItems().add("Todas as posições");
        for (br.futebas.mundo.Posicao p : br.futebas.mundo.Posicao.values()) pos.getItems().add(p.name());
        pos.setValue(filtroPosDraft == null ? "Todas as posições" : filtroPosDraft);
        pos.setOnAction(e -> {
            filtroPosDraft = pos.getValue() == null || pos.getValue().startsWith("Todas") ? null : pos.getValue();
            hub.atualizar();
        });
        ComboBox<String> ori = new ComboBox<>();
        ori.getItems().addAll("Todas as origens", "Passe livre", "Divisão inferior", "Aposentado", "Ex-elite");
        ori.setValue(origemDraft == null ? "Todas as origens" : origemDraft);
        ori.setOnAction(e -> {
            origemDraft = ori.getValue() == null || ori.getValue().startsWith("Todas") ? null : ori.getValue();
            hub.atualizar();
        });
        List<Jogador> l = new ArrayList<>();
        for (Jogador x : todos) {
            if (filtroPosDraft != null && !x.posicao.name().equals(filtroPosDraft)) continue;
            if (origemDraft != null && !j.origemKings(x).startsWith(origemDraft)) continue;
            l.add(x);
        }
        TableView<Jogador> tv = Ui.tabela();
        Ui.colNo(tv, "", 34, x -> time.prioridadesDraft.contains(x.id) ? Ui.etiqueta((time.prioridadesDraft.indexOf(x.id) + 1) + "º", "verde") : new Label());
        Ui.colPos(tv, x -> x.posicao);
        Ui.colNo(tv, "Jogador", 220, x -> Ui.nomeJogador(x.id, x.nomeExibicao()));
        Ui.colNum(tv, "Idade", 56, x -> x.idade(j.ano()));
        Ui.colForca(tv, "Força", Jogador::forcaExibida);
        Ui.col(tv, "Origem", 220, j::origemKings);
        Ui.colNo(tv, "", 120, x -> time.prioridadesDraft.contains(x.id) ? Ui.botao("Tirar", () -> {
            List<Integer> pr = new ArrayList<>(time.prioridadesDraft);
            pr.remove(Integer.valueOf(x.id));
            j.definirPrioridadesDraft(pr);
            hub.atualizar();
        }, "fantasma") : Ui.botao("Priorizar", () -> {
            List<Integer> pr = new ArrayList<>(time.prioridadesDraft);
            pr.add(x.id);
            j.definirPrioridadesDraft(pr);
            hub.atualizar();
        }));
        tv.getItems().setAll(l);
        Ui.alturaPorLinhas(tv, Math.min(18, Math.max(6, l.size())));
        HBox topo = Ui.linha(14, Ui.dado("No draft", String.valueOf(todos.size())), Ui.dado("Suas prioridades", String.valueOf(time.prioridadesDraft.size())),
                Ui.espaco(), pos, ori);
        VBox v = new VBox(12, topo, Ui.painelTabela(null, Ui.info("Quem pode ser escolhido no próximo draft da " + Textos.ligaKings(liga.id, liga.nome)
                + ": passe livre, divisões de baixo, veteranos fora da elite e aposentados que escolheram a Kings. Clique na coluna para ordenar."), tv));
        v.setStyle("-fx-padding: 14 0 0 0;");
        return v;
    }

    /** Resultados dos drafts já feitos (escolha a escolha), com as suas escolhas em destaque. */
    private Node draftados(Jogo j, Mundo m, LigaKings liga, LigaKings.TimeKings time) {
        List<LigaKings.EstadoDraft> ds = j.draftsAnteriores(liga.id);
        VBox v = new VBox(12);
        v.setStyle("-fx-padding: 14 0 0 0;");
        if (ds.isEmpty()) {
            v.getChildren().add(Ui.painel(null, Ui.rotulo("Nenhum draft feito ainda: em 2026 os times usam os elencos reais; o primeiro draft é no começo de 2027.", "texto-suave")));
        } else {
            ComboBox<Integer> ano = new ComboBox<>();
            for (LigaKings.EstadoDraft d : ds) ano.getItems().add(d.ano);
            LigaKings.EstadoDraft atual = ds.get(0);
            for (LigaKings.EstadoDraft d : ds) if (d.ano == anoDraftados) atual = d;
            ano.setValue(atual.ano);
            ano.setOnAction(e -> {
                anoDraftados = ano.getValue() == null ? 0 : ano.getValue();
                hub.atualizar();
            });
            TableView<LigaKings.Escolha> tv = Ui.tabela();
            Ui.colNum(tv, "Nº", 48, x -> x.numero);
            Ui.colNum(tv, "Rodada", 64, x -> x.rodada);
            Ui.colNo(tv, "Time", 220, x -> Ui.linha(8, Ui.escudo(x.timeId, 20), Ui.nomeClube(x.timeId, x.time, x.timeId.equals(time.id) ? "texto-forte" : "label")));
            Ui.colNo(tv, "Jogador", 220, x -> Ui.nomeAtletaKings(x.jogadorId, x.timeId, x.jogador == null ? "—" : x.jogador, x.timeId.equals(time.id) ? "texto-forte" : "label"));
            Ui.colNo(tv, "", 110, x -> x.timeId.equals(time.id) ? Ui.etiqueta(x.humano ? "Sua escolha" : "Auxiliar", x.humano ? "verde" : null) : new Label());
            tv.getItems().setAll(atual.escolhas);
            Ui.alturaPorLinhas(tv, Math.min(20, Math.max(6, atual.escolhas.size())));
            v.getChildren().add(Ui.painelTabela("Draft " + atual.ano, ano, tv));
        }
        // elencos atuais: draftados × wildcards
        TableView<LigaKings.TimeKings> el = Ui.tabela();
        Ui.colNo(el, "Time", 220, x -> Ui.linha(8, Ui.escudo(x.id, 20), Ui.nomeClube(x.id, x.nome, x.id.equals(time.id) ? "texto-forte" : "label")));
        Ui.colNum(el, "Wildcards", 80, x -> (int) x.elenco.stream().filter(a -> "wildcard".equals(a.tipo) || "coringa".equals(a.tipo) || "mantido".equals(a.tipo) || a.presidente).count());
        Ui.colNum(el, "Draftados", 80, x -> (int) x.elenco.stream().filter(a -> "draft".equals(a.tipo)).count());
        Ui.colNum(el, "Elenco", 70, x -> x.elenco.size());
        el.getItems().setAll(liga.times);
        Ui.alturaPorLinhas(el, liga.times.size());
        v.getChildren().add(Ui.painelTabela("Elencos da liga", null, el));
        return v;
    }

    private static void mover(ListView<Jogador> lv, int d, Runnable salvar) {
        int i = lv.getSelectionModel().getSelectedIndex();
        int k = i + d;
        if (i < 0 || k < 0 || k >= lv.getItems().size()) return;
        Jogador x = lv.getItems().remove(i);
        lv.getItems().add(k, x);
        lv.getSelectionModel().select(k);
        salvar.run();
    }
}
