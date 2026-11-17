package br.futebas.ui.telas;

import br.futebas.competicao.CompeticoesFicticias;
import br.futebas.competicao.Confronto;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.ui.Sessao;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Tabelas de todas as competições: classificação com zonas, grupos lado a lado, chaveamento
 * em colunas, jogos por rodada e artilharia. A lista à esquerda filtra por região.
 */
public final class PainelTabelas implements Hub.Painel {
    private final Sessao sessao;
    private final boolean comoTecnico;
    /** Modo "Campeões": a direita mostra só os campeões da competição escolhida. */
    private boolean soCampeoes;

    private String filtroFixo;

    /** Abre já num filtro (ex.: "Seleções") e esconde a escolha de região. */
    public PainelTabelas soFiltro(String nomeFiltro) {
        filtroFixo = nomeFiltro;
        ultimoFiltro = nomeFiltro;
        return this;
    }

    public PainelTabelas modoCampeoes() {
        soCampeoes = true;
        return this;
    }
    private final BorderPane raiz = new BorderPane();
    private final ListView<Item> lista = new ListView<>();
    /** Seções recolhidas pelo usuário (lembradas entre aberturas). */
    private static final java.util.Set<String> recolhidas = new java.util.HashSet<>();

    /** Linha da lista: cabeçalho de seção (t == null) ou competição. */
    private record Item(String secao, Torneio t, int qtd) {}
    private final ComboBox<String> filtro = new ComboBox<>();
    private final Map<String, String> filtros = new LinkedHashMap<>();
    private static String ultimaCompeticao;
    private static String ultimoFiltro;
    private boolean anterior;
    /** Chave "Profissional | Sub-20" (lembrada entre aberturas). */
    private static boolean sub20;

    public PainelTabelas(Sessao sessao, boolean comoTecnico) {
        this.sessao = sessao;
        this.comoTecnico = comoTecnico;
    }

    private Mundo m() { return sessao.jogo.mundo(); }

    private String meuId() {
        Clube c = sessao.jogo.meuClube();
        return c == null ? null : c.id;
    }

    /** Abre a tabela de uma competição na próxima vez que o painel for montado. */
    public static void focar(String competicaoId) {
        ultimaCompeticao = competicaoId;
        ultimoFiltro = null;
    }

    /** Abas da coluna da esquerda (requisito, 12/11/2026), lembradas entre aberturas. */
    static final String ABA_MEUS = "Campeonatos", ABA_OUTROS = "Outros Campeonatos";
    static String abaLista;
    /** Aberto pelo menu da esquerda (Campeonatos / Outros Campeonatos): sem as abas dentro da tela. */
    private boolean semAbas;

    /** O menu da esquerda escolhe a lista (regra do jogo): a tela não mostra as próprias abas. */
    PainelTabelas lista(String aba) {
        abaLista = aba;
        semAbas = true;
        return this;
    }
    /** Largura do nome na lista (quebra em até 2 linhas, sem cortar). */
    private static final double LARG_NOME = 194;
    /** Competições sem edição nesta temporada mostradas na lista (jogos únicos de agosto ainda sem data). */
    private final java.util.Set<Torneio> previstas = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private VBox campoFiltro;

    private String aba() {
        if (filtroFixo != null) return ABA_OUTROS;
        if (abaLista == null) abaLista = meusIds().isEmpty() ? ABA_OUTROS : ABA_MEUS;
        return abaLista;
    }

    /** Ids do usuário nas competições: clube, time da Kings e seleção. */
    private java.util.Set<String> meusIds() {
        java.util.Set<String> s = new java.util.LinkedHashSet<>();
        Clube c = sessao.jogo.meuClube();
        if (c != null) s.add(c.id);
        try {
            var tk = sessao.jogo.meuTimeKings();
            if (tk != null) s.add(tk.id);
        } catch (RuntimeException e) {
            // sem Kings
        }
        try {
            var sel = sessao.jogo.minhaSelecao();
            if (sel != null && sel.id != null) s.add(sel.id);
        } catch (RuntimeException e) {
            // sem seleção
        }
        return s;
    }

    @Override
    public Node construir() {
        Clube meu = sessao.jogo.meuClube();
        String meuPais = meu != null ? meu.pais : "BRA";
        montarFiltros(meuPais);
        filtro.getItems().setAll(filtros.keySet());
        filtro.setMaxWidth(Double.MAX_VALUE);
        filtro.setVisibleRowCount(16);
        // bandeira nos países, ícone nas categorias (lista e botão)
        filtro.setCellFactory(x -> celulaFiltro());
        filtro.setButtonCell(celulaFiltro());
        String inicial = filtroFixo != null ? filtroFixo : ultimoFiltro != null && filtros.containsKey(ultimoFiltro) ? ultimoFiltro : Textos.pais(meuPais);
        if (filtroFixo == null && ultimaCompeticao != null && ultimoFiltro == null) {
            Torneio alvo = m().torneio(ultimaCompeticao);
            if (alvo != null) {
                boolean minha = false;
                for (String id : meusIds()) if (alvo.participantes.contains(id)) minha = true;
                if (minha && !(semAbas && ABA_OUTROS.equals(abaLista))) abaLista = ABA_MEUS; // entrou por "Outros": fica nela
                else {
                    abaLista = ABA_OUTROS;
                    inicial = "continental".equals(alvo.ambito) ? F_CONTINENTAIS : "selecoes".equals(alvo.ambito) ? "Seleções"
                            : "kings".equals(alvo.ambito) ? "Kings League" : alvo.pais != null ? Textos.pais(alvo.pais) : inicial;
                }
            } else {
                // sem edição neste ano (jogos únicos de agosto): fica na lista de continentais
                var dc = sessao.jogo.competicaoDoBanco(ultimaCompeticao);
                if (dc != null && "continental".equals(dc.ambito())) {
                    abaLista = ABA_OUTROS;
                    inicial = F_CONTINENTAIS;
                }
            }
        }
        filtro.getSelectionModel().select(inicial);
        filtro.valueProperty().addListener((o, a, b) -> {
            if (b != null && CABECALHO.equals(filtros.get(b))) {
                // título de grupo: não é escolhível
                javafx.application.Platform.runLater(() -> filtro.setValue(a));
                return;
            }
            ultimoFiltro = b;
            preencherLista();
        });
        ComboBox<String> temporada = new ComboBox<>();
        temporada.getItems().setAll("Temporada atual", "Temporada anterior");
        temporada.getSelectionModel().select(anterior ? 1 : 0);
        temporada.setMaxWidth(Double.MAX_VALUE);
        temporada.valueProperty().addListener((o, a, b) -> {
            anterior = "Temporada anterior".equals(b);
            preencherLista();
        });
        lista.getStyleClass().add("lista-lateral");
        java.util.Set<String> meusIds = meusIds();
        lista.setCellFactory(x -> new ListCell<>() {
            @Override
            protected void updateItem(Item it, boolean vazio) {
                super.updateItem(it, vazio);
                setText(null);
                setTooltip(null);
                getStyleClass().remove("secao-lista");
                if (vazio || it == null) {
                    setGraphic(null);
                    return;
                }
                if (it.t() == null) {
                    getStyleClass().add("secao-lista");
                    boolean fechada = fechada(it.secao());
                    Label seta = Ui.rotulo(fechada ? "▸" : "▾", "secao-seta");
                    HBox h = Ui.linha(8, seta, Ui.rotulo(it.secao(), "secao-titulo"), Ui.espaco(), Ui.rotulo(String.valueOf(it.qtd()), "item-escolha-meta"));
                    h.setMaxWidth(LARG_NOME + 36);
                    h.setPrefWidth(LARG_NOME + 36);
                    setGraphic(h);
                    return;
                }
                Torneio t = it.t();
                Label n = Ui.rotulo(Textos.curto(t.nome));
                // nome inteiro: quebra em até 2 linhas (antes cortava em "Copa Intercontinental da…")
                n.setWrapText(true);
                n.setPrefWidth(LARG_NOME);
                n.setMaxWidth(LARG_NOME);
                n.setMinHeight(Region.USE_PREF_SIZE);
                boolean meu = false;
                for (String id : meusIds) if (t.participantes.contains(id)) meu = true;
                if (meu) n.getStyleClass().add("texto-forte");
                VBox nomes = new VBox(1, n);
                String sub = subtitulo(t);
                if (sub != null) nomes.getChildren().add(Ui.rotulo(sub, "texto-pequeno"));
                HBox h = Ui.linha(8, Imagens.trofeu(t.id, t.ambito, t.pais, 22), nomes);
                h.setStyle("-fx-padding: 0 0 0 10;");
                setGraphic(h);
                setTooltip(new javafx.scene.control.Tooltip(t.nome + (sub == null ? "" : "\n" + sub)));
            }
        });
        lista.setOnMouseClicked(e -> {
            Item it = lista.getSelectionModel().getSelectedItem();
            if (it != null && it.t() == null) {
                alternar(it.secao());
                preencherLista();
            }
        });
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null && b.t() != null) mostrar(b.t());
        });
        HBox chave = Ui.segmentosCheios(List.of("Profissional", "Sub-20"), x -> x, sub20 ? "Sub-20" : "Profissional", x -> {
            sub20 = "Sub-20".equals(x);
            preencherLista();
        });
        chave.getStyleClass().add("chave-secundaria");
        campoFiltro = new VBox(3, Ui.rotulo("Mostrar", "texto-pequeno"), filtro);
        VBox campoTemporada = new VBox(3, Ui.rotulo("Temporada", "texto-pequeno"), temporada);
        HBox abas = Ui.segmentosCheios(List.of(ABA_MEUS, ABA_OUTROS), x -> x, aba(), x -> {
            abaLista = x;
            atualizarAba();
            preencherLista();
        });
        abas.getStyleClass().add("abas-lista");
        if (semAbas) {
            abas.setVisible(false);
            abas.setManaged(false);
        }
        VBox esq = soCampeoes ? new VBox(10, Ui.titulo("Campeões"), abas, chave, campoFiltro, lista)
                : new VBox(10, Ui.titulo(semAbas ? aba() : "Campeonatos"), abas, chave, campoFiltro, campoTemporada, lista);
        if (filtroFixo != null) {
            for (Node x : List.of(chave, abas, campoFiltro)) {
                x.setVisible(false);
                x.setManaged(false);
            }
        }
        VBox.setVgrow(lista, Priority.ALWAYS);
        esq.setPrefWidth(304);
        esq.setMinWidth(270);
        esq.setStyle("-fx-padding: 20 8 16 20;");
        raiz.setLeft(esq);
        atualizarAba();
        preencherLista();
        return raiz;
    }

    /** "Campeonatos" não tem o filtro (a lista é direta); "Outros Campeonatos" tem. */
    private void atualizarAba() {
        if (campoFiltro == null || filtroFixo != null) return;
        boolean outros = ABA_OUTROS.equals(aba());
        campoFiltro.setVisible(outros);
        campoFiltro.setManaged(outros);
    }

    /** Linha fina embaixo do nome: os jogos únicos fictícios dizem quem joga. */
    private static String subtitulo(Torneio t) {
        if (CompeticoesFicticias.TACA_ATLANTICA.equals(t.id)) return "Cóndor × Conference";
        return null;
    }

    /** Ordem das confederações na lista de continentais. */
    static final List<String> CONFEDERACOES = List.of("Mundial e intercontinental", "CONMEBOL", "UEFA", "CONCACAF", "AFC", "CAF", "OFC");

    /** Confederação (seção) de uma competição continental de clubes. */
    static String confederacao(Torneio t) {
        String id = t.id == null ? "" : t.id;
        if (id.startsWith("fifa-") || id.contains("intercontinental") || id.contains("mundial")
                || CompeticoesFicticias.DESAFIO.equals(id) || CompeticoesFicticias.TACA_ATLANTICA.equals(id))
            return CONFEDERACOES.get(0);
        for (String c : CONFEDERACOES) if (c.equalsIgnoreCase(t.confederacao)) return c;
        for (String c : CONFEDERACOES) if (id.startsWith(c.toLowerCase(java.util.Locale.ROOT) + "-")) return c;
        return CONFEDERACOES.get(0);
    }

    /** Importância dentro da confederação: principal, segunda, terceira, supercopa. */
    static int importancia(String id) {
        String s = id == null ? "" : id;
        if (s.equals("fifa-mundial-clubes")) return 0;
        if (s.equals("fifa-intercontinental")) return 1;
        if (s.equals(CompeticoesFicticias.DESAFIO)) return 2;
        if (s.equals(CompeticoesFicticias.TACA_ATLANTICA)) return 3;
        if (s.contains("recopa") || s.contains("supercopa") || s.contains("super-cup")) return 9;
        if (s.contains("condor") || s.contains("conference")) return 3;
        int n = Zonas.nivel(s);
        return n == 0 ? 5 : n;
    }

    private void preencherLista() {
        boolean meus = ABA_MEUS.equals(aba());
        String f = meus ? "meu" : filtros.getOrDefault(filtro.getValue(), "pais:" + (sessao.jogo.meuClube() != null ? sessao.jogo.meuClube().pais : "BRA"));
        java.util.Set<String> ids = meusIds();
        List<Torneio> ts = new ArrayList<>();
        List<Torneio> fonte = new ArrayList<>(anterior ? m().torneiosAnteriores : m().torneios);
        if (meus && !anterior) {
            // as do ano de cada time do usuário (as da Kings ficam nas ligas, fora da lista do ano)
            java.util.Set<Torneio> ja = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            ja.addAll(fonte);
            for (String id : ids) for (Torneio t : sessao.jogo.competicoesDoParticipante(id)) if (ja.add(t)) fonte.add(t);
        }
        boolean filtroSub20 = f.startsWith(SUB20_PREFIXO);
        if ((sub20 && filtroFixo == null) || filtroSub20) {
            fonte.clear();
            try {
                fonte.addAll(anterior ? sessao.jogo.competicoesSub20Anteriores() : sessao.jogo.competicoesSub20());
            } catch (RuntimeException e) {
                // sem sub-20
            }
        }
        java.util.Set<Torneio> vistos = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Torneio t : fonte) {
            if ("amistoso".equals(t.ambito)) continue;
            boolean ok;
            if (meus) {
                ok = false;
                for (String id : ids) if (t.participantes.contains(id)) ok = true;
            } else {
                if ("kings".equals(t.ambito) != "kings".equals(f)) continue;
                boolean internacional = "continental".equals(t.ambito) || "mundial".equals(t.ambito) || t.pais == null;
                ok = switch (f) {
                    case "continental" -> "continental".equals(t.ambito) || "mundial".equals(t.ambito);
                    case "selecoes" -> "selecoes".equals(t.ambito);
                    case "kings" -> true;
                    default -> filtroSub20
                            ? (f.equals(SUB20_PREFIXO + "continental") ? internacional : !internacional && f.substring(SUB20_PREFIXO.length()).equals(t.pais))
                            : f.startsWith("pais:") && f.substring(5).equals(t.pais) && !"continental".equals(t.ambito) && !"selecoes".equals(t.ambito);
                };
            }
            if (ok && vistos.add(t)) ts.add(t);
        }
        previstas.clear();
        if ("continental".equals(f) && !anterior && !sub20) {
            // jogos únicos de agosto (Desafio e Taça Atlântica): na lista mesmo antes de marcados
            for (String id : List.of(CompeticoesFicticias.DESAFIO, CompeticoesFicticias.TACA_ATLANTICA)) {
                boolean tem = false;
                for (Torneio t : ts) if (id.equals(t.id)) tem = true;
                var dc = sessao.jogo.competicaoDoBanco(id);
                if (tem || dc == null) continue;
                Torneio p = new Torneio();
                p.id = id;
                p.nome = dc.nome();
                p.ambito = "continental";
                p.ano = m().ano;
                previstas.add(p);
                ts.add(p);
            }
        }
        java.util.Map<String, List<Torneio>> secoes = new java.util.LinkedHashMap<>();
        String meuUf = sessao.jogo.meuClube() == null ? null : sessao.jogo.meuClube().uf;
        boolean porConfederacao = "continental".equals(f) || f.equals(SUB20_PREFIXO + "continental");
        List<String> ordemSecoes = meus ? List.of("Nacional", "Estadual", "Continental", "Seleção", "Sub-20", "Kings League")
                : porConfederacao ? CONFEDERACOES
                : List.of(SEC_LIGAS, "Copas nacionais", "Regionais", SEC_ESTADUAIS, "Continentais e mundiais", "Mundial", "Eliminatórias",
                "Continentais de seleções", "Nations League", "Sub-20", "Kings League");
        for (String sec : ordemSecoes) secoes.put(sec, new ArrayList<>());
        for (Torneio t : ts)
            secoes.computeIfAbsent(meus ? secaoMinha(t) : porConfederacao ? confederacao(t) : secao(t, filtroSub20), k -> new ArrayList<>()).add(t);
        // estaduais: uma seção por estado (o seu primeiro, depois em ordem), no lugar de "Estaduais"
        secoes = estaduaisPorEstado(secoes, meuUf);
        // o Mundial de Seleções da Kings fica por último
        List<Torneio> nacoes = secoes.remove("Kings World Cup Nations");
        if (nacoes != null) secoes.put("Kings World Cup Nations", nacoes);
        boolean porImportancia = porConfederacao || meus;
        List<Item> itens = new ArrayList<>();
        Torneio sel = null;
        for (var e : secoes.entrySet()) {
            List<Torneio> l = e.getValue();
            if (l.isEmpty()) continue;
            l.sort((a, b) -> {
                if (e.getKey().startsWith("Estadua") || e.getKey().startsWith(SEC_ESTADUAIS)) {
                    boolean ma = meuUf != null && meuUf.equals(a.uf), mb = meuUf != null && meuUf.equals(b.uf);
                    if (ma != mb) return ma ? -1 : 1;
                    int c = String.valueOf(a.uf).compareTo(String.valueOf(b.uf));
                    if (c != 0) return c;
                    int da = a.divisao == null ? 1 : a.divisao, db = b.divisao == null ? 1 : b.divisao;
                    if (da != db) return da - db;
                }
                int oa = PainelMundo.ordemCompeticao(a.id, a.ambito), ob = PainelMundo.ordemCompeticao(b.id, b.ambito);
                if (oa != ob) return oa - ob;
                if (porImportancia) {
                    int ia = importancia(a.id), ib = importancia(b.id);
                    if (ia != ib) return ia - ib;
                }
                return a.nome.compareTo(b.nome);
            });
            itens.add(new Item(e.getKey(), null, l.size()));
            boolean fechada = fechada(e.getKey());
            for (Torneio t : l) {
                if (t.id.equals(ultimaCompeticao)) sel = t;
                if (!fechada || t.id.equals(ultimaCompeticao)) itens.add(new Item(e.getKey(), t, 0));
            }
        }
        lista.getItems().setAll(itens);
        if (sel == null) for (Item it : itens) if (it.t() != null) {
            sel = it.t();
            break;
        }
        if (sel != null) {
            for (Item it : itens) if (it.t() == sel) {
                lista.getSelectionModel().select(it);
                lista.scrollTo(Math.max(0, itens.indexOf(it) - 3));
            }
        } else {
            raiz.setCenter(Ui.conteudo(Ui.vazio(meus ? "Seu time não disputa nenhuma competição nesta temporada" : "Nenhuma competição", null, null)));
        }
    }

    /** Seção na aba "Campeonatos": Nacional, Estadual, Continental, Seleção, Kings League. */
    private String secaoMinha(Torneio t) {
        String id = t.id == null ? "" : t.id;
        if (sub20 || id.contains("sub20")) return "Sub-20";
        if ("kings".equals(t.ambito) || id.startsWith("kings-")) return "Kings League";
        if ("selecoes".equals(t.ambito)) return "Seleção";
        if ("estadual".equals(t.ambito)) return "Estadual";
        if ("continental".equals(t.ambito) || "mundial".equals(t.ambito) || t.pais == null) return "Continental";
        return "Nacional";
    }

    /** Seção da lista: ligas, copas nacionais, regionais, estaduais (por estado), continentais, seleções, sub-20, Kings. */
    private String secao(Torneio t, boolean listaSub20) {
        String id = t.id == null ? "" : t.id;
        if (!sub20 && !listaSub20 && (id.endsWith("-sub20") || id.contains("sub20"))) return "Sub-20";
        if ("kings".equals(t.ambito) || id.startsWith("kings-")) {
            if (id.contains("mundial-selecoes") || id.contains("nations")) return "Kings World Cup Nations";
            if (id.contains("mundial-clubes") || id.contains("world-cup-clubs")) return "Kings World Cup Clubs";
            for (var l : sessao.jogo.ligasKings()) if (l.torneio == t || (l.id != null && id.contains(l.id))) return l.nome;
            return "Kings League";
        }
        if ("selecoes".equals(t.ambito)) {
            if (id.contains("copa-do-mundo") || id.contains("finalissima") || id.contains("mundial")) return "Mundial";
            if (id.contains("eliminat") || id.contains("qualif")) return "Eliminatórias";
            if (id.contains("nations") || id.contains("liga-das-nacoes")) return "Nations League";
            return "Continentais de seleções";
        }
        if ("continental".equals(t.ambito) || "mundial".equals(t.ambito) || t.pais == null) return "Continentais e mundiais";
        if ("estadual".equals(t.ambito)) return t.uf == null ? SEC_ESTADUAIS : SEC_ESTADUAIS + " · " + t.uf;
        if ("nacional".equals(t.ambito)) return SEC_LIGAS;
        if ("copa_nacional".equals(t.ambito)) {
            if (id.endsWith("-copa") || id.contains("supercopa") || id.contains("copa-do-brasil")) return "Copas nacionais";
            if (id.contains("nordeste") || id.contains("copa-verde") || id.contains("sul-sudeste") || id.contains("regional")) return "Regionais";
            return "Copas nacionais";
        }
        return "Copas nacionais";
    }

    private static int ordem(Torneio t) {
        if ("nacional".equals(t.ambito)) return t.divisao == null ? 5 : t.divisao;
        if ("copa_nacional".equals(t.ambito)) return 6;
        if ("continental".equals(t.ambito)) return 7;
        if ("selecoes".equals(t.ambito)) return 0;
        if ("estadual".equals(t.ambito)) return 9;
        return 10;
    }

    private void mostrar(Torneio t) {
        if (t == null) return;
        ultimaCompeticao = t.id;
        if (previstas.contains(t)) {
            mostrarPrevista(t);
            return;
        }
        if (soCampeoes) {
            Label titulo = Ui.titulo(Textos.curto(t.nome));
            HBox cab = Ui.linha(14, Ui.trofeu(t.id, t.ambito, t.pais, 64), new VBox(2, titulo,
                    Ui.rotulo(t.pais == null ? "Internacional" : Textos.pais(t.pais), "texto-suave")));
            Node corpo = campeoesAnteriores(t);
            VBox v = new VBox(12, cab, corpo);
            VBox.setVgrow(corpo, Priority.ALWAYS);
            v.setStyle("-fx-padding: 20 24 16 12;");
            raiz.setCenter(v);
            return;
        }
        Mundo m = m();
        TabPane abas = new TabPane();
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        int faseAtual = t.faseAtual() == null ? Math.max(0, t.fases.size() - 1) : t.faseAtual().indice;
        Function<String, String> nome = m::nomeParticipante;
        // fases de grupos consecutivas viram uma aba; mata-matas viram a aba "Chaveamento"
        List<FaseExec> mata = new ArrayList<>();
        int abaAtual = 0;
        for (FaseExec f : t.fases) {
            if (f.mataMata()) {
                mata.add(f);
                continue;
            }
            Tab tab = Ui.abaSobDemanda(f.nome == null ? "Fase " + (f.indice + 1) : f.nome, () -> Ui.rolar(conteudoFase(t, f, nome)));
            if (f.indice == faseAtual) abaAtual = abas.getTabs().size();
            abas.getTabs().add(tab);
        }
        if (!mata.isEmpty()) {
            Tab tab = Ui.abaSobDemanda("Chaveamento", () -> {
                ScrollPane sp = new ScrollPane(chaveamento(t, mata, nome, meuId()));
                sp.setFitToHeight(false);
                return sp;
            });
            if (mata.stream().anyMatch(f -> f.indice == faseAtual)) abaAtual = abas.getTabs().size();
            abas.getTabs().add(tab);
        }
        // só a aba aberta é montada (as outras quando escolhidas): abrir o menu travava com o banco completo
        abas.getTabs().addAll(Ui.abaSobDemanda("Jogos por rodada", () -> jogos(t, nome)), Ui.abaSobDemanda("Artilharia", () -> Ui.rolar(artilharia(t))),
                Ui.abaSobDemanda("Campeões anteriores", () -> campeoesAnteriores(t)));
        abas.getSelectionModel().select(abaAtual);
        Ui.ativarSobDemanda(abas);

        HBox cab = Ui.linha(14, Ui.trofeu(t.id, t.ambito, t.pais, 64));
        Label titulo = Ui.nomeCompeticao(t.id, Textos.curto(t.nome), "titulo-tela");
        String temporada = t.temporada != null ? t.temporada : String.valueOf(t.ano);
        String status;
        if (t.campeao != null) status = "Encerrado";
        else if (t.faseAtual() != null) status = (t.faseAtual().nome == null ? "Em andamento" : t.faseAtual().nome) + " · " + t.participantes.size() + " participantes";
        else status = "A começar";
        VBox id = new VBox(2, titulo, Ui.rotulo("Temporada " + temporada + "  ·  " + status, "texto-suave"));
        cab.getChildren().add(id);
        cab.getChildren().add(Ui.espaco());
        if (Ui.aoAbrirCompeticao != null) {
            java.util.function.Consumer<String> abrir = Ui.aoAbrirCompeticao;
            cab.getChildren().add(Ui.botao("Página da competição", () -> abrir.accept(t.id), "fantasma"));
        }
        if (t.campeao != null) {
            VBox camp = new VBox(2, Ui.rotulo("Campeão", "texto-pequeno"), Ui.clube(m, t.campeao, 28, "titulo-painel"));
            if (t.vice != null) camp.getChildren().add(Ui.linha(4, Ui.rotulo("Vice:", "texto-suave"), Ui.nomeClube(t.vice, m.nomeParticipante(t.vice), "texto-suave")));
            cab.getChildren().add(camp);
        }
        VBox v = new VBox(12, cab, abas);
        VBox.setVgrow(abas, Priority.ALWAYS);
        v.setStyle("-fx-padding: 20 24 16 12;");
        raiz.setCenter(v);
    }

    /**
     * Jogo único de agosto ainda sem edição nesta temporada (Desafio Sul-Americana × Europa League,
     * Taça Atlântica = Cóndor × Conference): o que é, quando acontece e os campeões anteriores.
     */
    private void mostrarPrevista(Torneio t) {
        boolean desafio = CompeticoesFicticias.DESAFIO.equals(t.id);
        String quem = desafio ? "o campeão da Copa Sul-Americana e o da UEFA Europa League" : "o campeão da Copa Cóndor e o da UEFA Conference League";
        Torneio ult = m().ultimoEncerrado(t.id);
        int semana = desafio ? CompeticoesFicticias.SEMANA_DESAFIO : CompeticoesFicticias.SEMANA_TACA;
        String quando = m().semana > semana
                ? "A edição deste ano não aconteceu (faltou um dos campeões, ou o mesmo clube ganhou as duas)."
                : "Marcado para o começo de agosto, quando os dois campeões estiverem definidos.";
        HBox cab = Ui.linha(14, Ui.trofeu(t.id, t.ambito, t.pais, 64), new VBox(2, Ui.nomeCompeticao(t.id, Textos.curto(t.nome), "titulo-tela"),
                Ui.rotulo("Mundial e intercontinental  ·  " + (desafio ? "Sul-Americana × Europa League" : "Cóndor × Conference"), "texto-suave")));
        cab.getChildren().add(Ui.espaco());
        if (Ui.aoAbrirCompeticao != null) {
            java.util.function.Consumer<String> abrir = Ui.aoAbrirCompeticao;
            cab.getChildren().add(Ui.botao("Página da competição", () -> abrir.accept(t.id), "fantasma"));
        }
        VBox info = Ui.painel("Como funciona", Ui.paragrafo("Jogo único em campo neutro entre " + quem + " (competição fictícia do FUTÉBAS). "
                + "Empate vai para a prorrogação e os pênaltis.", "texto-medio"), Ui.paragrafo(quando, "texto-suave"));
        if (ult != null && ult.campeao != null)
            info.getChildren().add(Ui.linha(8, Ui.rotulo("Última edição (" + (ult.temporada != null ? ult.temporada : String.valueOf(ult.ano)) + "):", "texto-suave"),
                    Ui.clube(m(), ult.campeao, 22, "texto-forte")));
        Node corpo = campeoesAnteriores(t);
        VBox v = new VBox(12, cab, info, corpo);
        VBox.setVgrow(corpo, Priority.ALWAYS);
        v.setStyle("-fx-padding: 20 24 16 12;");
        raiz.setCenter(v);
    }

    private Node campeoesAnteriores(Torneio t) {
        return campeoes(sessao.jogo, t.id, t.ambito, t.pais);
    }

    /** Campeões da competição (reais até 2025/26 + do save) e títulos por clube. */
    static Node campeoes(br.futebas.Jogo jogo, String compId, String ambito, String pais) {
        Mundo m = jogo.mundo();
        List<br.futebas.Jogo.CampeaoInfo> l = new ArrayList<>();
        try {
            l.addAll(jogo.campeoes(compId));
        } catch (RuntimeException e) {
            // sem camada histórica
        }
        if (l.isEmpty()) return Ui.vazio("Sem campeões registrados", null, null);
        java.util.Collections.reverse(l);
        TableView<br.futebas.Jogo.CampeaoInfo> tv = Ui.tabela();
        Ui.col(tv, "Temporada", 90, x -> x.temporada());
        Ui.colNo(tv, "Campeão", 240, x -> nomeComEscudo(m, x.campeaoId(), x.campeao(), true));
        Ui.colNo(tv, "Vice", 220, x -> nomeComEscudo(m, x.viceId(), x.vice(), false));
        tv.getItems().setAll(l);
        java.util.Map<String, Integer> conta = new java.util.HashMap<>();
        java.util.Map<String, String> idDe = new java.util.HashMap<>();
        for (var x : l) {
            if (x.campeao() == null) continue;
            conta.merge(x.campeao(), 1, Integer::sum);
            if (x.campeaoId() != null) idDe.put(x.campeao(), x.campeaoId());
        }
        List<java.util.Map.Entry<String, Integer>> rk = new ArrayList<>(conta.entrySet());
        rk.sort((a, b) -> b.getValue() - a.getValue());
        javafx.scene.layout.FlowPane maiores = new javafx.scene.layout.FlowPane(18, 10);
        for (int i = 0; i < Math.min(8, rk.size()); i++) {
            String nome = rk.get(i).getKey();
            String id = idDe.get(nome);
            maiores.getChildren().add(Ui.linha(8, id == null ? new javafx.scene.layout.Region() : Ui.escudo(id, 26), new VBox(0, Ui.rotulo(nome, "texto-forte"),
                    Ui.rotulo(rk.get(i).getValue() + (rk.get(i).getValue() == 1 ? " título" : " títulos"), "texto-pequeno"))));
        }
        Ui.estiloLinhas(tv, (x, row) -> {
            row.getStyleClass().remove("linha-historica");
            if (x.real()) row.getStyleClass().add("linha-historica");
        });
        // requisito: só o ano (sem a etiqueta "seu mundo" em cada linha) e um resumo simples
        String desde = l.get(l.size() - 1).temporada();
        Label marca = Ui.rotulo(l.size() + (l.size() == 1 ? " edição" : " edições") + (desde == null ? "" : "  ·  desde " + desde), "texto-suave");
        VBox titulos = Ui.painelAcoes("Títulos por clube", Imagens.trofeu(compId, ambito, pais, 26), maiores);
        VBox v = new VBox(12, titulos, Ui.painelTabela("Campeões, do mais recente ao mais antigo", marca, tv));
        VBox.setVgrow(v.getChildren().get(1), Priority.ALWAYS);
        return v;
    }

    private static Node nomeComEscudo(Mundo m, String id, String nome, boolean forte) {
        if (nome == null) return new Label();
        Label n = id == null ? Ui.rotulo(nome, forte ? "texto-forte" : "label") : Ui.nomeClube(id, nome, forte ? "texto-forte" : "label");
        n.setMinWidth(0);
        return id == null ? Ui.linha(8, n) : Ui.linha(8, Ui.escudo(id, 18), n);
    }

    private Node conteudoFase(Torneio t, FaseExec f, Function<String, String> nome) {
        return conteudoFase(m(), t, f, nome, meuId());
    }

    /**
     * Classificação de uma fase (grupos lado a lado ou tabela única) com as zonas e a legenda tiradas
     * das regras da competição ({@link Zonas}): quem sobe, quem cai, quem passa de fase.
     */
    static Node conteudoFase(Mundo m, Torneio t, FaseExec f, Function<String, String> nome, String meuId) {
        if (!f.iniciada && !f.concluida) return Ui.vazio("Esta fase ainda não começou.", null, null);
        Zonas zonas = Zonas.de(t, f, Math.max(1, t.tabela(f.indice, 0).size()));
        if (f.numGrupos > 1) {
            int classificam = Math.max(1, f.porGrupo);
            FlowPane grupos = new FlowPane(16, 16);
            for (int g = 0; g < f.numGrupos; g++) {
                List<LinhaTabela> linhas = t.tabela(f.indice, g);
                int gg = g;
                java.util.function.Supplier<Node> grupo = () -> {
                    TableView<LinhaTabela> tv = tabelaClassificacao(m, t, linhas, nome, meuId, 0, 0, classificam, true);
                    VBox p = Ui.painelTabela("Grupo " + (char) ('A' + gg), null, tv);
                    p.setPrefWidth(430);
                    return p;
                };
                // os primeiros grupos já na abertura; os de baixo (Copa: 12 grupos) chegam nos quadros seguintes
                grupos.getChildren().add(g < GRUPOS_NA_HORA ? grupo.get() : Ui.adiado(grupo, 430, alturaGrupo(linhas.size())));
            }
            VBox box = new VBox(10, grupos);
            FaseExec prox = f.indice + 1 < t.fases.size() ? t.fases.get(f.indice + 1) : null;
            HBox leg = Ui.linha(14);
            String quem = f.porGrupo == 1 ? "O 1º de cada grupo" : "Os " + f.porGrupo + " primeiros de cada grupo";
            if (prox != null) leg.getChildren().add(legenda("zona-classifica", quem + (f.terceiros > 0 ? " e os " + f.terceiros + " melhores terceiros" : "")
                    + " → " + Zonas.faseComArtigo(prox, f.porGrupo * f.numGrupos)));
            if (!leg.getChildren().isEmpty()) box.getChildren().add(leg);
            if (!zonas.notas.isEmpty()) box.getChildren().add(zonas.legenda());
            return box;
        }
        List<LinhaTabela> linhas = t.tabela(f.indice, 0);
        TableView<LinhaTabela> tv = tabelaClassificacao(m, t, linhas, nome, meuId, 0, 0, 0, false, zonas.vazia() ? null : zonas);
        VBox box = new VBox(10, Ui.painelTabela(null, null, tv));
        if (!zonas.vazia()) box.getChildren().add(zonas.legenda());
        return box;
    }

    /** Quantas tabelas de grupo montar na abertura (as que cabem na tela); as outras são adiadas. */
    static final int GRUPOS_NA_HORA = 6;

    /** Altura de um painel de grupo com {@code linhas} linhas (cabeçalho do painel + tabela). */
    static double alturaGrupo(int linhas) {
        return 40 + 32 + 30 * Math.max(1, linhas) + 4;
    }

    static HBox legenda(String classe, String texto) {
        Label l = Ui.rotulo(" ", "zona", classe);
        l.setMinHeight(14);
        l.setPrefSize(14, 14);
        l.setMinWidth(14);
        return Ui.linha(6, l, Ui.rotulo(texto, "texto-suave"));
    }

    /** Últimos resultados de cada participante dentro da competição. */
    private static Map<String, List<String>> formaNoTorneio(Torneio t) {
        Map<String, List<String>> f = new HashMap<>();
        if (t == null) return f;
        List<Partida> ps = new ArrayList<>(t.partidas);
        ps.sort((a, b) -> a.sa != b.sa ? a.sa - b.sa : a.data - b.data);
        for (Partida p : ps) {
            if (!p.jogada || p.golsMandante == null) continue;
            f.computeIfAbsent(p.mandante, k -> new ArrayList<>()).add(Textos.resultadoPara(p, p.mandante));
            f.computeIfAbsent(p.visitante, k -> new ArrayList<>()).add(Textos.resultadoPara(p, p.visitante));
        }
        return f;
    }

    /** Tabela de classificação com zonas e forma (reutilizada pela partida, Kings League e seleções). */
    static TableView<LinhaTabela> tabelaClassificacao(Mundo m, Torneio t, List<LinhaTabela> linhas, Function<String, String> nome, String meuId,
                                                      int acesso, int queda, int classificam, boolean compacta) {
        return tabelaClassificacao(m, t, linhas, nome, meuId, acesso, queda, classificam, compacta, null);
    }

    static TableView<LinhaTabela> tabelaClassificacao(Mundo m, Torneio t, List<LinhaTabela> linhas, Function<String, String> nome, String meuId,
                                                      int acesso, int queda, int classificam, boolean compacta, Zonas zonas) {
        TableView<LinhaTabela> tv = Ui.tabela();
        List<LinhaTabela> ls = new ArrayList<>(linhas);
        int n = ls.size();
        Map<String, List<String>> forma = compacta ? Map.of() : formaNoTorneio(t);
        Map<String, Integer> posicao = new HashMap<>();
        for (int i = 0; i < n; i++) posicao.put(ls.get(i).clubeId, i);
        TableColumn<LinhaTabela, LinhaTabela> pos = Ui.colNo(tv, "", 40, l -> {
            int i = posicao.get(l.clubeId);
            Label z = Ui.rotulo(String.valueOf(i + 1), "zona");
            if (zonas != null && !zonas.porPosicao.isEmpty()) {
                Zonas.Zona zz = zonas.porPosicao.get(i + 1);
                if (zz != null) z.getStyleClass().add(zz.classe());
                return z;
            }
            if (i < acesso) z.getStyleClass().add("zona-acesso");
            else if (i < classificam) z.getStyleClass().add("zona-classifica");
            else if (queda > 0 && i >= n - queda) z.getStyleClass().add("zona-queda");
            return z;
        });
        pos.setStyle("-fx-alignment: center;");
        boolean selecoes = !ls.isEmpty() && ls.get(0).clubeId != null && ls.get(0).clubeId.startsWith("sel-");
        TableColumn<LinhaTabela, String> clube = Ui.col(tv, selecoes ? "Seleção" : "Clube", compacta ? 170 : 240, l -> l.clubeId);
        clube.setCellFactory(x -> new TableCell<>() {
            @Override
            protected void updateItem(String id, boolean vazio) {
                super.updateItem(id, vazio);
                if (vazio || id == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label n = Ui.nomeClube(id, nome.apply(id));
                n.setMinWidth(0);
                n.textFillProperty().bind(textFillProperty());
                setGraphic(Ui.linha(8, Ui.escudo(id, 20), n));
                setText(null);
            }
        });
        TableColumn<LinhaTabela, Integer> pts = Ui.colNum(tv, "P", 52, l -> l.pontos);
        pts.getStyleClass().add("col-pontos"); // pontos em destaque (regra do jogo)
        pts.setCellFactory(x -> new TableCell<>() {
            {
                getStyleClass().add("num-forte");
            }

            @Override
            protected void updateItem(Integer v, boolean vazio) {
                super.updateItem(v, vazio);
                setText(vazio || v == null ? null : String.valueOf(v));
            }
        });
        Ui.colNum(tv, "J", 36, l -> l.jogos);
        Ui.colNum(tv, "V", 36, l -> l.vitorias);
        // sem empate (Kings: shootouts) não há coluna E; as vitórias/derrotas nos shootouts ficam em VS/DS
        boolean semEmpate = t != null && t.penaltisNoEmpate;
        if (!semEmpate) Ui.colNum(tv, "E", 36, l -> l.empates);
        Ui.colNum(tv, "D", 36, l -> l.derrotas);
        if (semEmpate) {
            Ui.colNum(tv, "VS", 40, l -> l.vitoriasPenaltis);
            Ui.colNum(tv, "DS", 40, l -> l.derrotasPenaltis);
        }
        if (!compacta) {
            Ui.colNum(tv, "GP", 40, l -> l.golsPro);
            Ui.colNum(tv, "GC", 40, l -> l.golsContra);
        }
        Ui.colNum(tv, "SG", 42, LinhaTabela::saldo);
        if (!compacta) {
            Ui.colNum(tv, "%", 44, l -> l.jogos == 0 ? null : (int) Math.round(l.aproveitamento()));
            Ui.colNo(tv, "Últimos", 120, l -> {
                List<String> r = forma.getOrDefault(l.clubeId, List.of());
                HBox h = new HBox(3);
                h.setAlignment(Pos.CENTER_LEFT);
                for (int i = Math.max(0, r.size() - 5); i < r.size(); i++) h.getChildren().add(Ui.forma(r.get(i)));
                return h;
            });
        }
        for (TableColumn<LinhaTabela, ?> c : tv.getColumns()) c.setSortable(false);
        tv.getItems().setAll(ls);
        Ui.estiloLinhas(tv, (l, row) -> {
            row.getStyleClass().removeIf(c -> c.startsWith("tinta-zona"));
            if (l.clubeId.equals(meuId)) row.getStyleClass().add("meu-clube");
            else if (zonas != null && posicao.get(l.clubeId) != null) {
                Zonas.Zona zz = zonas.porPosicao.get(posicao.get(l.clubeId) + 1);
                if (zz != null) row.getStyleClass().add("tinta-" + zz.classe());
            }
        });
        if (compacta || n <= 24) Ui.alturaPorLinhas(tv, n);
        else tv.setPrefHeight(32 + 30 * 20);
        return tv;
    }

    /** Assinatura antiga (Kings League e partida). */
    static TableView<LinhaTabela> tabelaClassificacao(Mundo m, List<LinhaTabela> linhas, Function<String, String> nome, String meuId,
                                                      int acesso, int queda, int classificam) {
        return tabelaClassificacao(m, null, linhas, nome, meuId, acesso, queda, classificam, true);
    }

    /** Chaveamento em colunas (uma por rodada do mata-mata), com escudos e placares. */
    static Node chaveamento(Torneio t, List<FaseExec> fases, Function<String, String> nome, String meuId) {
        HBox colunas = new HBox(18);
        colunas.setStyle("-fx-padding: 4 4 12 4;");
        colunas.setAlignment(Pos.CENTER_LEFT);
        boolean algum = false;
        for (FaseExec f : fases) {
            if (f.confrontos.isEmpty()) {
                if (!f.clubes.isEmpty() || f.iniciada) continue;
                continue;
            }
            Map<Integer, List<Confronto>> porRodada = new TreeMap<>();
            for (Confronto c : f.confrontos) porRodada.computeIfAbsent(c.rodada, k -> new ArrayList<>()).add(c);
            for (Map.Entry<Integer, List<Confronto>> e : porRodada.entrySet()) {
                algum = true;
                int qtd = e.getValue().size();
                // fase de uma rodada só: o nome real da fase (CONMEBOL: "Primeira fase" com 16 confrontos,
                // "Playoffs das oitavas" com 8); fases de várias rodadas: o nome pela quantidade de confrontos
                String titulo = f.rodadasMata == 1 && f.nome != null && !f.nome.isBlank() ? f.nome
                        : qtd == 1 ? "Final" : qtd == 2 ? "Semifinal" : qtd == 4 ? "Quartas de final" : qtd == 8 ? "Oitavas de final"
                        : qtd == 16 ? "16 avos de final" : (f.nome == null ? "Rodada " + (e.getKey() + 1) : f.nome);
                VBox col = new VBox(10);
                col.setAlignment(Pos.CENTER);
                col.getChildren().add(Ui.rotulo(titulo, "titulo-menor"));
                for (Confronto c : e.getValue()) col.getChildren().add(caixaConfronto(t, c, nome, meuId));
                colunas.getChildren().add(col);
            }
        }
        if (!algum) {
            VBox v = new VBox(Ui.vazio("Chaveamento ainda não sorteado", null, null));
            for (FaseExec f : fases) if (!f.clubes.isEmpty()) {
                FlowPane fp = new FlowPane(10, 8);
                for (String id : f.clubes) fp.getChildren().add(Ui.linha(6, Ui.escudo(id, 18), Ui.nomeClube(id, nome.apply(id))));
                v.getChildren().add(Ui.painel("Classificados · " + (f.nome == null ? "mata-mata" : f.nome), fp));
                break;
            }
            return v;
        }
        return colunas;
    }

    static Node chaveamento(Torneio t, FaseExec f, Function<String, String> nome) {
        return chaveamento(t, List.of(f), nome, null);
    }

    private static Node caixaConfronto(Torneio t, Confronto c, Function<String, String> nome, String meuId) {
        int ga = 0, gb = 0;
        Integer pa = null, pb = null;
        boolean jogado = false;
        List<String> pernas = new ArrayList<>();
        for (int pid : c.partidas) {
            if (pid < 0 || pid >= t.partidas.size()) continue;
            Partida p = t.partidas.get(pid);
            if (!p.jogada || p.golsMandante == null) continue;
            jogado = true;
            boolean aCasa = p.mandante.equals(c.a);
            ga += aCasa ? p.golsMandante : p.golsVisitante;
            gb += aCasa ? p.golsVisitante : p.golsMandante;
            pernas.add(aCasa ? p.golsMandante + "-" + p.golsVisitante : p.golsVisitante + "-" + p.golsMandante);
            if (p.penaltisMandante != null) {
                pa = aCasa ? p.penaltisMandante : p.penaltisVisitante;
                pb = aCasa ? p.penaltisVisitante : p.penaltisMandante;
            }
        }
        VBox box = new VBox(0, linhaConfronto(c.a, nome, jogado ? ga : null, pa, c.a.equals(c.vencedor), c.vencedor != null, c.a.equals(meuId)),
                Ui.separadorH(), linhaConfronto(c.b, nome, jogado ? gb : null, pb, c.b.equals(c.vencedor), c.vencedor != null, c.b.equals(meuId)));
        box.getStyleClass().add("painel");
        box.setStyle("-fx-padding: 4 10 4 10; -fx-spacing: 0;");
        box.setPrefWidth(220);
        box.setMaxWidth(220);
        if (pernas.size() > 1) {
            VBox v = new VBox(2, box, Ui.rotulo("Jogos: " + String.join(" · ", pernas), "texto-pequeno"));
            v.setAlignment(Pos.CENTER_LEFT);
            return v;
        }
        return box;
    }

    private static HBox linhaConfronto(String id, Function<String, String> nome, Integer gols, Integer pen, boolean venceu, boolean decidido, boolean meu) {
        Label n = id == null ? Ui.rotulo("a definir") : Ui.nomeClube(id, nome.apply(id));
        n.setMaxWidth(140);
        if (venceu || meu) n.getStyleClass().add("texto-forte");
        if (decidido && !venceu) n.getStyleClass().add("texto-suave");
        Label g = Ui.rotulo(gols == null ? "" : gols + (pen != null ? " (" + pen + ")" : ""), "numero");
        if (venceu) g.setStyle("-fx-font-weight: bold;");
        HBox h = Ui.linha(8, Ui.escudo(id, 18), n, Ui.espaco(), g);
        h.setMinHeight(28);
        return h;
    }

    // ------------------------------------------------------------------ jogos por rodada
    private Node jogos(Torneio t, Function<String, String> nome) {
        return jogos(t, nome, meuId());
    }

    /** Jogos por rodada com setas para navegar (abre na primeira rodada com jogo por fazer). */
    static Node jogos(Torneio t, Function<String, String> nome, String meuId) {
        Map<Integer, List<Partida>> porData = new TreeMap<>();
        for (Partida p : t.partidas) porData.computeIfAbsent(p.data, k -> new ArrayList<>()).add(p);
        List<Integer> datas = new ArrayList<>(porData.keySet());
        if (datas.isEmpty()) return Ui.vazio("Jogos ainda não sorteados", null, null);
        int atual = datas.size() - 1;
        for (int i = 0; i < datas.size(); i++) {
            if (porData.get(datas.get(i)).stream().anyMatch(p -> !p.jogada)) {
                atual = i;
                break;
            }
        }
        int[] idx = {atual};
        VBox corpo = new VBox(0);
        Label rotulo = Ui.rotulo("", "titulo-painel");
        Button ant = Ui.botao("‹", () -> {}, "fantasma"), prox = Ui.botao("›", () -> {}, "fantasma");
        Runnable desenhar = () -> {
            List<Partida> ps = porData.get(datas.get(idx[0]));
            Partida p0 = ps.get(0);
            String fase = Textos.fase(t, p0);
            rotulo.setText("Rodada " + (idx[0] + 1) + " de " + datas.size() + (fase.isEmpty() ? "" : "  ·  " + fase));
            corpo.getChildren().setAll(listaJogos(ps, nome, meuId));
            ant.setDisable(idx[0] == 0);
            prox.setDisable(idx[0] == datas.size() - 1);
        };
        ant.setOnAction(e -> {
            idx[0]--;
            desenhar.run();
        });
        prox.setOnAction(e -> {
            idx[0]++;
            desenhar.run();
        });
        desenhar.run();
        VBox v = new VBox(10, Ui.linha(6, ant, rotulo, prox), Ui.rolar(corpo));
        VBox.setVgrow(v.getChildren().get(1), Priority.ALWAYS);
        return v;
    }

    /** Lista de jogos com escudos e placar ao centro (também usada na tela da partida). */
    static VBox listaJogos(List<Partida> ps, Function<String, String> nome, String meuId) {
        VBox v = new VBox(0);
        v.getStyleClass().add("painel");
        v.setStyle("-fx-padding: 4 12 4 12; -fx-spacing: 0;");
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(0);
        int r = 0;
        for (Partida p : ps) {
            Label a = Ui.nomeClube(p.mandante, nome.apply(p.mandante));
            Label b = Ui.nomeClube(p.visitante, nome.apply(p.visitante));
            a.setMaxWidth(Double.MAX_VALUE);
            a.setAlignment(Pos.CENTER_RIGHT);
            if (meuId != null && p.envolve(meuId)) {
                a.getStyleClass().add("texto-forte");
                b.getStyleClass().add("texto-forte");
            }
            String pl = p.jogada && p.golsMandante != null ? p.golsMandante + " – " + p.golsVisitante : Textos.dataCurta(p.data());
            Label placar = Ui.rotulo(pl, p.jogada ? "numero" : "texto-pequeno");
            placar.setMinWidth(78);
            placar.setAlignment(Pos.CENTER);
            if (p.jogada) placar.setStyle("-fx-background-color: #353a43; -fx-background-radius: 4; -fx-padding: 1 6 1 6; -fx-font-weight: bold;");
            Label pen = Ui.rotulo(p.penaltisMandante != null ? "pên. " + p.penaltisMandante + "–" + p.penaltisVisitante : "", "texto-pequeno");
            HBox ha = Ui.linha(8, a, Ui.escudo(p.mandante, 20));
            ha.setAlignment(Pos.CENTER_RIGHT);
            HBox hb = Ui.linha(8, Ui.escudo(p.visitante, 20), b, pen);
            g.addRow(r, ha, placar, hb);
            GridPane.setHgrow(ha, Priority.ALWAYS);
            GridPane.setHgrow(hb, Priority.ALWAYS);
            Region linha = new Region();
            linha.setMinHeight(32);
            g.add(linha, 3, r);
            r++;
        }
        javafx.scene.layout.ColumnConstraints c0 = new javafx.scene.layout.ColumnConstraints(), c1 = new javafx.scene.layout.ColumnConstraints(),
                c2 = new javafx.scene.layout.ColumnConstraints(), c3 = new javafx.scene.layout.ColumnConstraints();
        c0.setPercentWidth(42);
        c1.setPercentWidth(14);
        c2.setPercentWidth(42);
        c3.setPercentWidth(2);
        c1.setHalignment(javafx.geometry.HPos.CENTER);
        g.getColumnConstraints().setAll(c0, c1, c2, c3);
        v.getChildren().add(g);
        return v;
    }

    private Node artilharia(Torneio t) {
        return artilharia(m(), t);
    }

    static Node artilharia(Mundo m, Torneio t) {
        List<int[]> art = t.artilheiros(30);
        if (art.isEmpty()) return Ui.vazio("Nenhum gol marcado ainda.", null, null);
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(6);
        int i = 0;
        for (int[] a : art) {
            Jogador j = m.jogador(a[0]);
            Label pos = Ui.rotulo((i + 1) + "º", "numero");
            pos.setMinWidth(30);
            Label nomeJ = j == null ? Ui.rotulo("?") : Ui.nomeJogador(j.id, j.nomeExibicao(), i < 3 ? "texto-forte" : "label");
            HBox clube = j == null || j.clubeId == null ? new HBox() : Ui.clube(m, j.clubeId, 18, "texto-suave");
            Label gols = Ui.rotulo(a[1] + (a[1] == 1 ? " gol" : " gols"), "numero");
            g.addRow(i++, pos, j == null ? new Label() : Ui.pos(j.posicao), nomeJ, clube, gols);
        }
        return Ui.painelAcoes(null, null, g);
    }

    /** Célula do filtro: bandeira para "pais:XXX", ícone para as categorias. */
    /** Valor dos títulos de grupo no menu Mostrar (não escolhíveis). */
    private static final String CABECALHO = "#";
    private static final String SUB20_PREFIXO = "sub20:";
    static final String F_CONTINENTAIS = "Mundiais e continentais";
    static final String SEC_LIGAS = "Ligas nacionais", SEC_ESTADUAIS = "Estaduais";
    /** Seções recolhidas por padrão que o usuário abriu (estaduais de outros estados). */
    private static final java.util.Set<String> abertas = new java.util.HashSet<>();

    /**
     * Menu Mostrar da aba "Outros Campeonatos" em grupos com título (requisito, 13/11/2026):
     * Internacional (mundiais e continentais, seleções), Países (o seu primeiro, depois em ordem,
     * com bandeira), Sub-20 (continentais e cada país) e, por último, a Kings League com a logo.
     */
    private void montarFiltros(String meuPais) {
        filtros.clear();
        java.text.Collator col = java.text.Collator.getInstance(new java.util.Locale("pt", "BR"));
        col.setStrength(java.text.Collator.PRIMARY);
        java.util.Comparator<String> porNome = (a, b) -> col.compare(Textos.pais(a), Textos.pais(b));
        filtros.put("§Internacional", CABECALHO);
        filtros.put(F_CONTINENTAIS, "continental");
        filtros.put("Seleções", "selecoes");
        filtros.put("§Países", CABECALHO);
        filtros.put(Textos.pais(meuPais), "pais:" + meuPais);
        java.util.Set<String> paises = new java.util.TreeSet<>(porNome);
        for (Torneio t : m().torneios) if (t.pais != null && !t.pais.equals(meuPais) && !"kings".equals(t.ambito)) paises.add(t.pais);
        for (String p : paises) filtros.put(Textos.pais(p), "pais:" + p);
        java.util.Set<String> paises20 = new java.util.TreeSet<>(porNome);
        boolean continentais20 = false;
        try {
            for (List<Torneio> l : List.of(sessao.jogo.competicoesSub20(), sessao.jogo.competicoesSub20Anteriores()))
                for (Torneio t : l) {
                    if ("continental".equals(t.ambito) || "mundial".equals(t.ambito) || t.pais == null) continentais20 = true;
                    else paises20.add(t.pais);
                }
        } catch (RuntimeException e) {
            // sem sub-20
        }
        if (!paises20.isEmpty() || continentais20) {
            filtros.put("§Sub-20", CABECALHO);
            if (paises20.remove(meuPais)) filtros.put("Sub-20 · " + Textos.pais(meuPais), SUB20_PREFIXO + meuPais);
            if (continentais20) filtros.put("Sub-20 · Continentais", SUB20_PREFIXO + "continental");
            for (String p : paises20) filtros.put("Sub-20 · " + Textos.pais(p), SUB20_PREFIXO + p);
        }
        filtros.put("§Kings League", CABECALHO);
        filtros.put("Kings League", "kings");
    }

    /** Estaduais de outros estados começam recolhidos (a lista do país fica curta). */
    private boolean recolhidaPorPadrao(String secao) {
        if (!secao.startsWith(SEC_ESTADUAIS + " · ")) return false;
        Clube c = sessao.jogo.meuClube();
        return c == null || c.uf == null || !secao.equals(SEC_ESTADUAIS + " · " + c.uf);
    }

    private boolean fechada(String secao) {
        return recolhidas.contains(secao) || recolhidaPorPadrao(secao) && !abertas.contains(secao);
    }

    private void alternar(String secao) {
        if (recolhidaPorPadrao(secao)) {
            if (!abertas.remove(secao)) abertas.add(secao);
        } else if (!recolhidas.remove(secao)) recolhidas.add(secao);
    }

    /** Troca a seção "Estaduais" pelas seções de cada estado, no mesmo lugar (o seu estado primeiro). */
    private static java.util.Map<String, List<Torneio>> estaduaisPorEstado(java.util.Map<String, List<Torneio>> secoes, String meuUf) {
        List<String> estados = new ArrayList<>();
        for (String k : secoes.keySet()) if (k.startsWith(SEC_ESTADUAIS + " · ")) estados.add(k);
        if (estados.isEmpty()) return secoes;
        String meu = meuUf == null ? null : SEC_ESTADUAIS + " · " + meuUf;
        estados.sort((a, b) -> {
            if (a.equals(meu) != b.equals(meu)) return a.equals(meu) ? -1 : 1;
            return a.compareTo(b);
        });
        java.util.Map<String, List<Torneio>> r = new java.util.LinkedHashMap<>();
        for (var e : secoes.entrySet()) {
            if (e.getKey().startsWith(SEC_ESTADUAIS + " · ")) continue;
            if (e.getKey().equals(SEC_ESTADUAIS)) {
                r.put(SEC_ESTADUAIS, e.getValue());
                for (String k : estados) r.put(k, secoes.get(k));
                continue;
            }
            r.put(e.getKey(), e.getValue());
        }
        return r;
    }

    private javafx.scene.control.ListCell<String> celulaFiltro() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String k, boolean vazio) {
                super.updateItem(k, vazio);
                if (vazio || k == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String v = filtros.get(k);
                getStyleClass().remove("filtro-grupo");
                setDisable(false);
                if (CABECALHO.equals(v)) {
                    // título do grupo (não escolhível)
                    setText(null);
                    Label l = Ui.rotulo(k.substring(1), "secao-titulo");
                    setGraphic(l);
                    setDisable(true);
                    getStyleClass().add("filtro-grupo");
                    return;
                }
                setText(k);
                setGraphicTextGap(8);
                if (v != null && v.startsWith("pais:")) setGraphic(Imagens.bandeira(v.substring(5), 13));
                else if (v != null && v.startsWith(SUB20_PREFIXO) && !v.endsWith("continental")) setGraphic(Imagens.bandeira(v.substring(SUB20_PREFIXO.length()), 13));
                else if ("kings".equals(v)) setGraphic(Imagens.logoKings(null, 18));
                else {
                    String ic = v == null ? "" : switch (v) {
                        case "meu" -> "★";
                        case "continental" -> "◎";
                        case "selecoes" -> "⚑";
                        case "kings" -> "♛";
                        default -> "•";
                    };
                    Label l = Ui.rotulo(ic, "filtro-icone");
                    l.setMinWidth(18);
                    setGraphic(l);
                }
            }
        };
    }
}
