package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.ui.comp.Ui;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** Treino: duas opções por jogador (como no Brasfoot), ou deixar o auxiliar decidir. */
final class PainelTreino implements Hub.Painel {
    private static final String[] OPCOES = {"Auxiliar decide", "Principais", "Secundárias"};
    private final Hub hub;

    PainelTreino(Hub hub) {
        this.hub = hub;
    }

    @Override
    public Node construir() {
        Jogo j = hub.jogo();
        Clube c = hub.clube();
        if (c == null && j.meuTimeKings() != null) return kings(j, j.meuTimeKings());
        if (c == null) return Ui.conteudo(Ui.titulo("Treino"), Ui.vazio("Sem clube.", null, null));
        List<Jogador> todos = new ArrayList<>(j.elenco(c.id));
        todos.removeIf(Jogador::amador);
        TableView<Jogador> t = Ui.tabela();
        Ui.colPos(t, x -> x.posicao);
        Ui.col(t, "Jogador", 200, Jogador::nomeExibicao);
        Ui.colNum(t, "Idade", 54, x -> x.idade(j.ano()));
        Ui.colForca(t, "Força", Jogador::forcaExibida);
        Ui.colNum(t, "POT", 54, x -> x.idade(j.ano()) <= 23 ? j.cpe(x.id) : null);
        Ui.colNum(t, "Moral", 56, x -> x.moral);
        TableColumn<Jogador, Jogador> treino = new TableColumn<>("Foco do treino");
        treino.setCellValueFactory(d -> new javafx.beans.property.ReadOnlyObjectWrapper<>(d.getValue()));
        treino.setCellFactory(x -> new TableCell<>() {
            private final ChoiceBox<String> cb = new ChoiceBox<>(FXCollections.observableArrayList(OPCOES));

            @Override
            protected void updateItem(Jogador jog, boolean vazio) {
                super.updateItem(jog, vazio);
                if (vazio || jog == null) {
                    setGraphic(null);
                    return;
                }
                cb.setOnAction(null);
                cb.getSelectionModel().select(jog.treino == null ? 0 : "principais".equals(jog.treino) ? 1 : 2);
                cb.setOnAction(e -> {
                    int i = cb.getSelectionModel().getSelectedIndex();
                    j.definirTreino(jog.id, i == 0 ? null : i == 1 ? "principais" : "secundarias");
                });
                cb.setStyle("-fx-font-size: 11.5px; -fx-padding: -2 0 -2 0;");
                setGraphic(cb);
            }
        });
        treino.setPrefWidth(170);
        t.getColumns().add(treino);
        t.getItems().setAll(todos);

        VBox explica = Ui.painelAcoes("Treino", Ui.info("Principais: atributos da posição, a força sobe mais rápido. Secundárias: os outros atributos, mais versátil."),
                Ui.rotulo("Todo o elenco", "titulo-menor"),
                todoElenco(j, todos));
        explica.setPrefWidth(372);
        explica.setMinWidth(352);
        explica.setMaxHeight(VBox.USE_PREF_SIZE);
        VBox tab = Ui.painelTabela(null, null, t);
        HBox h = new HBox(16, tab, explica);
        HBox.setHgrow(tab, Priority.ALWAYS);
        VBox v = Ui.conteudo(Ui.titulo("Treino"), h);
        VBox.setVgrow(h, Priority.ALWAYS);
        return v;
    }

    /**
     * A mesma tela para o time da Kings (regra do jogo: "Kings = futebol com 7", as mesmas telas): foco
     * por atleta ou do elenco inteiro. Na Kings, o treino mexe na força da escala da Kings (teto 80):
     * Principais sobe mais rápido; Secundárias sobe menos e segura a queda dos veteranos; o auxiliar
     * escolhe pela idade. Os presidentes que jogam não mudam. Os times da IA treinam sozinhos.
     */
    private Node kings(Jogo j, br.futebas.kings.LigaKings.TimeKings tk) {
        List<br.futebas.kings.LigaKings.AtletaKings> todos = new ArrayList<>(tk.elenco);
        TableView<br.futebas.kings.LigaKings.AtletaKings> t = Ui.tabela();
        Ui.colPos(t, x -> x.posicao);
        Ui.col(t, "Jogador", 200, x -> x.nome + (x.presidente ? " (presidente)" : ""));
        Ui.colNum(t, "Idade", 54, j::idadeKings);
        Ui.colForca(t, "Força", x -> (int) Math.round(x.forca));
        Ui.col(t, "Por semana", 90, x -> x.presidente ? "fixa" : String.format(java.util.Locale.ROOT, "%+.2f", j.evolucaoSemanalKings(x)).replace('.', ','));
        TableColumn<br.futebas.kings.LigaKings.AtletaKings, br.futebas.kings.LigaKings.AtletaKings> treino = new TableColumn<>("Foco do treino");
        treino.setCellValueFactory(d -> new javafx.beans.property.ReadOnlyObjectWrapper<>(d.getValue()));
        treino.setCellFactory(x -> new TableCell<>() {
            private final ChoiceBox<String> cb = new ChoiceBox<>(FXCollections.observableArrayList(OPCOES));

            @Override
            protected void updateItem(br.futebas.kings.LigaKings.AtletaKings a, boolean vazio) {
                super.updateItem(a, vazio);
                if (vazio || a == null) {
                    setGraphic(null);
                    return;
                }
                if (a.presidente) {
                    setGraphic(Ui.rotulo("Presidente: força fixa", "texto-suave"));
                    return;
                }
                cb.setOnAction(null);
                cb.getSelectionModel().select(a.treino == null ? 0 : "principais".equals(a.treino) ? 1 : 2);
                cb.setOnAction(e -> {
                    int i = cb.getSelectionModel().getSelectedIndex();
                    j.definirTreinoKings(a.chave(), i == 0 ? null : i == 1 ? "principais" : "secundarias");
                    getTableView().refresh();
                });
                cb.setStyle("-fx-font-size: 11.5px; -fx-padding: -2 0 -2 0;");
                setGraphic(cb);
            }
        });
        treino.setPrefWidth(170);
        t.getColumns().add(treino);
        t.getItems().setAll(todos);

        java.util.Set<String> usados = new java.util.HashSet<>();
        for (var x : todos) if (!x.presidente) usados.add(x.treino == null ? "" : x.treino);
        String comum = usados.size() == 1 ? usados.iterator().next() : null;
        String atual = comum == null ? null : comum.isEmpty() ? "Auxiliar decide" : "principais".equals(comum) ? "Principais" : "Secundárias";
        Node segmentos = Ui.segmentosCheios(List.of("Principais", "Secundárias", "Auxiliar decide"), x -> x, atual, x -> {
            j.definirTreinoKingsElenco("Principais".equals(x) ? "principais" : "Secundárias".equals(x) ? "secundarias" : null);
            hub.atualizar();
        });
        VBox explica = Ui.painelAcoes("Treino", Ui.info("Kings League: o treino muda a força toda semana, na escala da Kings (teto "
                        + Math.round(br.futebas.kings.Kings.FORCA_MAX) + "). Principais: sobe mais rápido. Secundárias: sobe menos e segura a queda dos veteranos. "
                        + "Auxiliar decide: principais até 29 anos, secundárias depois. Os presidentes que jogam têm força fixa."),
                Ui.rotulo("Todo o elenco", "titulo-menor"), segmentos);
        explica.setPrefWidth(372);
        explica.setMinWidth(352);
        explica.setMaxHeight(VBox.USE_PREF_SIZE);
        VBox tab = Ui.painelTabela(null, null, t);
        HBox h = new HBox(16, tab, explica);
        HBox.setHgrow(tab, Priority.ALWAYS);
        VBox v = Ui.conteudo(Ui.titulo("Treino"), h);
        VBox.setVgrow(h, Priority.ALWAYS);
        return v;
    }

    /**
     * "Todo o elenco": controle segmentado na largura do cartão (antes três botões soltos que
     * vazavam pela direita). Marcado quando o elenco inteiro está no mesmo treino.
     */
    private Node todoElenco(Jogo j, List<Jogador> todos) {
        java.util.Set<String> usados = new java.util.HashSet<>();
        for (Jogador x : todos) usados.add(x.treino == null ? "" : x.treino);
        String comum = usados.size() == 1 ? usados.iterator().next() : null;
        String atual = comum == null ? null : comum.isEmpty() ? "Auxiliar decide" : "principais".equals(comum) ? "Principais" : "Secundárias";
        return Ui.segmentosCheios(List.of("Principais", "Secundárias", "Auxiliar decide"), x -> x, atual, x -> {
            j.definirTreinoDoElenco("Principais".equals(x) ? "principais" : "Secundárias".equals(x) ? "secundarias" : null);
            hub.atualizar();
        });
    }
}
