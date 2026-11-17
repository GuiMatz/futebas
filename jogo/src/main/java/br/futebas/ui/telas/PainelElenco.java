package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.TipoContrato;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/** Elenco: tabela densa com a força como número principal e o cartão do jogador ao lado. */
final class PainelElenco implements Hub.Painel {
    private final Hub hub;
    private final ScrollPane detalhe = new ScrollPane();
    private static Integer selecionadoAntes;

    PainelElenco(Hub hub) {
        this.hub = hub;
    }

    @Override
    public Node construir() {
        Jogo j = hub.jogo();
        Clube c = hub.clube();
        if (c == null && j.meuTimeKings() != null) return kings(j, j.meuTimeKings());
        List<Jogador> elenco = j.elenco(c.id);
        TableView<Jogador> t = tabela(j, elenco);
        t.setRowFactory(x -> {
            TableRow<Jogador> r = new TableRow<>() {
                @Override
                protected void updateItem(Jogador item, boolean vazio) {
                    super.updateItem(item, vazio);
                    getStyleClass().remove("indisponivel");
                    if (!vazio && item != null && !item.disponivel()) getStyleClass().add("indisponivel");
                }
            };
            r.setOnContextMenuRequested(e -> {
                if (r.getItem() != null) menu(r.getItem()).show(r, e.getScreenX(), e.getScreenY());
            });
            return r;
        });
        long folha = 0;
        for (Jogador x : elenco) if (x.contrato != null) folha += x.contrato.salario;
        int media = 0;
        List<Jogador> top = elenco.stream().sorted((a, b) -> b.forcaExibida() - a.forcaExibida()).limit(16).toList();
        for (Jogador x : top) media += x.forcaExibida();
        media = top.isEmpty() ? 0 : media / top.size();
        HBox resumo = Ui.linha(24, Ui.dado("Jogadores", String.valueOf(elenco.size())), Ui.dado("Força média (16)", Ui.forca(media)),
                Ui.dado("Folha mensal", Textos.dinheiro(folha)), Ui.espaco(), renovacao(j), Ui.rotulo("Botão direito: negociar", "texto-pequeno"));
        VBox esquerda = new VBox(14, Ui.titulo("Elenco"), resumo, Ui.painelTabela(null, null, t));
        VBox.setVgrow(esquerda.getChildren().get(2), Priority.ALWAYS);
        esquerda.setStyle("-fx-padding: 20 12 20 24;");
        detalhe.setFitToWidth(true);
        detalhe.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detalhe.setPrefWidth(340);
        detalhe.setMinWidth(320);
        detalhe.setStyle("-fx-padding: 20 20 20 4;");
        BorderPane bp = new BorderPane(esquerda);
        bp.setRight(detalhe);
        t.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> mostrar(b));
        Jogador alvo = null;
        if (selecionadoAntes != null) for (Jogador x : elenco) if (x.id == selecionadoAntes) alvo = x;
        if (alvo == null && !elenco.isEmpty()) alvo = elenco.get(0);
        if (alvo != null) {
            t.getSelectionModel().select(alvo);
            t.scrollTo(Math.max(0, t.getItems().indexOf(alvo) - 4));
        }
        return bp;
    }

    /** Tabela de jogadores do clube (usada também pela base). */
    static TableView<Jogador> tabela(Jogo j, List<Jogador> elenco) {
        TableView<Jogador> t = Ui.tabela();
        int ano = j.ano();
        Ui.colPos(t, x -> x.posicao);
        Ui.colNo(t, "Jogador", 190, x -> {
            Label n = Ui.rotulo(x.nomeExibicao());
            HBox h = Ui.linha(7, Imagens.bandeira(x.pais, 11), n);
            if (x.topMundial) h.getChildren().add(Ui.etiqueta("Top", "escura"));
            else if (x.estrela) h.getChildren().add(Ui.etiqueta("★", "ambar"));
            if (x.contrato != null && x.contrato.tipo == TipoContrato.EMPRESTIMO) h.getChildren().add(Ui.etiqueta("empr.", "azul"));
            if (x.aVenda) h.getChildren().add(Ui.etiqueta("LT", "azul"));
            if (x.aEmprestimo) h.getChildren().add(Ui.etiqueta("LE", "azul"));
            return h;
        }).setComparator((a, b) -> a.nomeExibicao().compareTo(b.nomeExibicao()));
        Ui.colNum(t, "Idade", 52, x -> x.idade(ano));
        Ui.colForca(t, "Força", Jogador::forcaExibida);
        Ui.colNum(t, "Moral", 54, x -> x.moral);
        // estatísticas da temporada, como no Brasfoot
        Ui.colNum(t, "J", 38, x -> x.jogosTemp);
        Ui.colNum(t, "G", 38, x -> x.golsTemp);
        Ui.colNum(t, "A", 38, x -> x.assistTemp);
        var colNota = Ui.colNo(t, "Nota", 48, x -> Ui.rotulo(x.jogosTemp == 0 ? "—" : String.format(java.util.Locale.ROOT, "%.1f", x.notaMediaTemp()),
                x.jogosTemp == 0 ? "texto-suave" : "texto-forte"))
;
        colNota.setComparator((a, b) -> Double.compare(a.notaMediaTemp(), b.notaMediaTemp()));
        colNota.setSortable(true);
        Ui.colNo(t, "Condição", 92, x -> {
            if (x.lesaoSemanas > 0) return Ui.etiqueta("Lesão · " + x.lesaoSemanas + " sem.", "vermelha");
            if (x.suspensao > 0) return Ui.etiqueta("Suspenso · " + x.suspensao, "ambar");
            if (x.amarelos >= 2) return Ui.etiqueta(x.amarelos + " amarelos", "ambar");
            return Ui.etiqueta("Apto", "verde");
        });
        Ui.colDinheiro(t, "Salário", 96, x -> x.contrato == null ? 0L : x.contrato.salario);
        Ui.colDinheiro(t, "Valor", 100, x -> x.amador() ? 0L : j.valorDeMercado(x.id));
        t.getItems().setAll(elenco);
        return t;
    }

    private void mostrar(Jogador x) {
        if (x == null) {
            detalhe.setContent(null);
            return;
        }
        selecionadoAntes = x.id;
        detalhe.setContent(FichaJogador.construir(hub.jogo(), x, acoes(x)));
    }

    private ContextMenu menu(Jogador x) {
        ContextMenu cm = new ContextMenu();
        cm.getItems().addAll(itensMenu(x, true));
        return cm;
    }

    /**
     * Itens do botão direito num jogador do meu clube: lista de transferências, lista de
     * empréstimos, rescindir (e propor contrato, se {@code comContrato}). Usado também na escalação.
     */
    List<MenuItem> itensMenu(Jogador x, boolean comContrato) {
        Jogo j = hub.jogo();
        List<MenuItem> l = new java.util.ArrayList<>();
        boolean emprestado = x.contrato != null && x.contrato.tipo == TipoContrato.EMPRESTIMO;
        if (!emprestado) {
            MenuItem venda = new MenuItem(x.aVenda ? "Tirar da lista de transferências" : "Lista de transferências");
            venda.setOnAction(e -> venda(x));
            MenuItem emp = new MenuItem(x.aEmprestimo ? "Tirar da lista de empréstimos" : "Lista de empréstimos");
            emp.setOnAction(e -> emprestar(x));
            l.add(venda);
            l.add(emp);
            if (comContrato) {
                MenuItem renovar = new MenuItem("Propor novo contrato");
                renovar.setOnAction(e -> renovar(x));
                l.add(renovar);
            }
        }
        if (!emprestado && x.sub20(j.ano())) {
            MenuItem sub = new MenuItem("Mandar ao Sub-20");
            sub.setOnAction(e -> {
                if (j.mandarAoSub20(x.id)) hub.atualizar();
            });
            l.add(sub);
        }
        MenuItem resc = new MenuItem(emprestado ? "Devolver ao clube de origem" : "Rescindir contrato");
        resc.setOnAction(e -> rescindir(x, emprestado));
        l.add(resc);
        return l;
    }

    private Node acoes(Jogador x) {
        Jogo j = hub.jogo();
        FlowPane f = new FlowPane(6, 6);
        boolean emprestado = x.contrato != null && x.contrato.tipo == TipoContrato.EMPRESTIMO;
        if (!emprestado) {
            f.getChildren().addAll(Ui.botao("Renovar", () -> renovar(x)),
                    Ui.botao(x.aVenda ? "Tirar da venda" : "Vender", () -> venda(x)),
                    Ui.botao(x.aEmprestimo ? "Tirar do empréstimo" : "Emprestar", () -> emprestar(x)));
        }
        f.getChildren().add(Ui.botao(emprestado ? "Devolver" : "Rescindir", () -> rescindir(x, emprestado), "perigo"));
        ChoiceBox<String> treino = new ChoiceBox<>();
        treino.getItems().setAll("Treino: auxiliar decide", "Treino: principais", "Treino: secundárias");
        treino.getSelectionModel().select(x.treino == null ? 0 : "principais".equals(x.treino) ? 1 : 2);
        treino.getSelectionModel().selectedIndexProperty().addListener((o, a, b) ->
                j.definirTreino(x.id, b.intValue() == 0 ? null : b.intValue() == 1 ? "principais" : "secundarias"));
        f.getChildren().add(treino);
        return f;
    }

    private void venda(Jogador x) {
        Jogo j = hub.jogo();
        if (x.aVenda) j.colocarAVenda(x.id, 0);
        else {
            long multa = x.contrato == null ? 0 : x.contrato.multa;
            var r = Ui.perguntar("Pôr à venda", "Preço pedido por " + x.nomeExibicao() + " (R$). Valor de mercado: "
                    + Textos.dinheiro(j.valorDeMercado(x.id)) + (multa > 0 ? "; multa rescisória: " + Textos.dinheiro(multa) : "") + ".",
                    String.valueOf(j.valorDeMercado(x.id)));
            r.ifPresent(s -> j.colocarAVenda(x.id, numero(s)));
        }
        hub.atualizar();
    }

    private void emprestar(Jogador x) {
        if (x.aEmprestimo) hub.jogo().tirarDasListas(x.id);
        else {
            var r = hub.jogo().emprestar(x.id);
            if (!r.aceito()) Ui.informar("Empréstimo", r.mensagem());
        }
        hub.atualizar();
    }

    private void rescindir(Jogador x, boolean emprestado) {
        if (Ui.confirmar(emprestado ? "Devolver jogador" : "Rescindir contrato", emprestado ? "Devolver " + x.nomeExibicao() + " ao clube de origem?"
                : "Rescindir com " + x.nomeExibicao() + "? O clube paga metade dos salários que faltam.")) {
            hub.jogo().dispensar(x.id);
            hub.atualizar();
        }
    }

    private void renovar(Jogador x) {
        Jogo j = hub.jogo();
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Novo contrato");
        d.setHeaderText("Novo contrato para " + x.nomeExibicao());
        br.futebas.ui.comp.CampoDinheiro cs = new br.futebas.ui.comp.CampoDinheiro(j.salarioPedido(x.id), j.salarioPedido(x.id), 0);
        TextField sal = cs.campo();
        ChoiceBox<String> meses = new ChoiceBox<>();
        meses.getItems().setAll("6 meses", "1 ano", "2 anos", "3 anos", "4 anos", "5 anos"); // até 5 anos (regra do jogo)
        meses.getSelectionModel().select(1);
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.addRow(0, Ui.rotulo("Salário atual", "texto-suave"), Ui.rotulo(x.contrato == null ? "—" : Textos.dinheiro(x.contrato.salario) + " por mês"));
        g.addRow(1, Ui.rotulo("Pedido do jogador", "texto-suave"), Ui.rotulo(Textos.dinheiro(j.salarioPedido(x.id)) + " por mês (a moral pesa)"));
        g.addRow(2, Ui.rotulo("Novo salário por mês", "texto-suave"), cs);
        g.addRow(3, Ui.rotulo("Duração", "texto-suave"), meses);
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Ui.dialogo(d).ifPresent(b -> {
            if (b != ButtonType.OK) return;
            int m = new int[]{6, 12, 24, 36, 48, 60}[meses.getSelectionModel().getSelectedIndex()];
            var r = j.renovarContrato(x.id, numero(sal.getText()), m);
            Ui.informar("Novo contrato", r.mensagem());
            hub.atualizar();
        });
    }

    /** Renovação automática dos contratos que vencem (quem está no nível do time). */
    private static javafx.scene.control.CheckBox renovacao(br.futebas.Jogo j) {
        javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox("Renovação automática");
        cb.setSelected(j.renovacaoAutomatica());
        cb.setFocusTraversable(false);
        cb.setOnAction(e -> j.definirRenovacaoAutomatica(cb.isSelected(), j.renovacaoForcaMinima()));
        javafx.scene.control.Tooltip.install(cb, new javafx.scene.control.Tooltip(
                "Renova sozinho, pelo salário pedido, quem está no nível do time quando o contrato vence."));
        return cb;
    }

    // ================================================================== Kings League
    private static String selecionadoKings;

    /** Vaga do atleta no elenco da Kings (presidente, wildcard, draft...). */
    static String vagaKings(br.futebas.kings.LigaKings.AtletaKings a) {
        if (a.presidente) return "Presidente";
        return a.tipo == null ? "Elenco" : switch (a.tipo) {
            case "11o" -> "11º jogador";
            case "coringa", "wildcard" -> "Wildcard";
            case "mantido" -> "Mantido";
            case "real" -> "Elenco inicial";
            case "selecao" -> "Convocado";
            default -> "Draft";
        };
    }

    /**
     * O mesmo painel para o time da Kings: resumo em cima, a tabela do elenco (sem salário, moral e
     * condição, que a Kings não tem; com a vaga no elenco, a origem e os gols) e o cartão do atleta ao lado.
     */
    private Node kings(Jogo j, br.futebas.kings.LigaKings.TimeKings tk) {
        br.futebas.mundo.Mundo m = j.mundo();
        br.futebas.kings.LigaKings liga = j.ligaKingsDoTime(tk.id);
        String paisLiga = liga == null ? null : liga.pais;
        TableView<br.futebas.kings.LigaKings.AtletaKings> t = Ui.tabela();
        Ui.colPos(t, a -> a.posicao);
        Ui.colNo(t, "Jogador", 200, a -> {
            Jogador x = a.jogadorId == null ? null : m.jogador(a.jogadorId);
            Label n = x != null ? Ui.nomeJogador(x.id, x.nomeExibicao()) : Ui.nomeAtletaKings(null, tk.id, a.nome);
            HBox h = Ui.linha(7, Imagens.bandeira(x != null ? x.pais : a.pais != null ? a.pais : paisLiga, 11), n);
            if (a.presidente) h.getChildren().add(Ui.etiqueta("Presidente", "ambar"));
            else if (x != null && x.estrela) h.getChildren().add(Ui.etiqueta("★", "ambar"));
            return h;
        }).setComparator((a, b) -> a.nome.compareTo(b.nome));
        Ui.col(t, "Idade", 52, a -> {
            Jogador x = a.jogadorId == null ? null : m.jogador(a.jogadorId);
            return x != null ? String.valueOf(x.idade(j.ano())) : a.anoNasc != null ? String.valueOf(j.ano() - a.anoNasc) : "—";
        });
        Ui.colForca(t, "Força", a -> (int) Math.round(a.forca));
        Ui.colNo(t, "Vaga", 104, a -> Ui.etiqueta(vagaKings(a), a.presidente ? "ambar" : "Draft".equals(vagaKings(a)) ? null : "azul"));
        Ui.col(t, "Origem", 150, a -> a.jogadorId == null ? (a.presidente ? "Presidente do time" : "Elenco real") : j.origemKings(m.jogador(a.jogadorId)));
        Ui.colNum(t, "Gols", 54, a -> a.gols);
        Ui.colDinheiro(t, "Valor", 100, a -> a.jogadorId == null || m.jogador(a.jogadorId) == null ? 0L : j.valorDeMercado(a.jogadorId));
        List<br.futebas.kings.LigaKings.AtletaKings> el = new java.util.ArrayList<>(tk.elenco);
        el.sort(java.util.Comparator.comparingInt((br.futebas.kings.LigaKings.AtletaKings a) -> a.posicao == null ? 99 : a.posicao.ordinal())
                .thenComparingDouble(a -> -a.forca));
        t.getItems().setAll(el);
        long wild = tk.elenco.stream().filter(a -> "Wildcard".equals(vagaKings(a)) || "Mantido".equals(vagaKings(a)) || a.presidente).count();
        long draft = tk.elenco.stream().filter(a -> "Draft".equals(vagaKings(a))).count();
        int forca7 = (int) Math.round(j.forcaEscalacaoKings());
        HBox resumo = Ui.linha(24, Ui.dado("Jogadores", String.valueOf(tk.elenco.size())), Ui.dado("Força dos 7", Ui.forca(forca7)),
                Ui.dado("Wildcards", wild + (j.vagasWildcardKings()[0] > 0 ? " · " + j.vagasWildcardKings()[0] + " vaga(s) aberta(s)" : "")),
                Ui.dado("Draft", String.valueOf(draft)), Ui.espaco(),
                Ui.rotulo("Presidentes nunca saem do time; dispensado vai para o passe livre.", "texto-pequeno"));
        VBox esquerda = new VBox(14, Ui.titulo("Elenco"), resumo, Ui.painelTabela(null, null, t));
        VBox.setVgrow(esquerda.getChildren().get(2), Priority.ALWAYS);
        esquerda.setStyle("-fx-padding: 20 12 20 24;");
        detalhe.setFitToWidth(true);
        detalhe.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detalhe.setPrefWidth(340);
        detalhe.setMinWidth(320);
        detalhe.setStyle("-fx-padding: 20 20 20 4;");
        BorderPane bp = new BorderPane(esquerda);
        bp.setRight(detalhe);
        t.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b == null) {
                detalhe.setContent(null);
                return;
            }
            selecionadoKings = b.chave();
            FlowPane f = new FlowPane(6, 6);
            if (b.jogadorId != null && !b.presidente) f.getChildren().add(Ui.botao("Dispensar", () -> {
                if (Ui.confirmar("Dispensar da Kings", "Dispensar " + b.nome + "? Ele fica sem time na Kings e vai para o passe livre.")
                        && j.dispensarDaKings(b.jogadorId)) hub.atualizar();
            }, "perigo"));
            f.getChildren().add(Ui.botao("Escalação", () -> hub.selecionar("escalacao")));
            detalhe.setContent(FichaJogador.kings(j, tk, b, vagaKings(b), f));
        });
        br.futebas.kings.LigaKings.AtletaKings alvo = null;
        for (var a : el) if (a.chave().equals(selecionadoKings)) alvo = a;
        if (alvo == null && !el.isEmpty()) alvo = el.get(0);
        if (alvo != null) {
            t.getSelectionModel().select(alvo);
            t.scrollTo(Math.max(0, t.getItems().indexOf(alvo) - 4));
        }
        return bp;
    }

    static long numero(String s) {
        if (s == null) return 0;
        if (true) return br.futebas.ui.comp.CampoDinheiro.interpretar(s);
        String d = s.replaceAll("[^0-9]", "");
        if (d.isEmpty()) return 0;
        try {
            return Long.parseLong(d);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE / 4;
        }
    }
}
