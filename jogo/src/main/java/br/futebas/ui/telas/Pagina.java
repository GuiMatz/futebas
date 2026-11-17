package br.futebas.ui.telas;

import br.futebas.ui.comp.Ui;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

/**
 * Peças comuns das páginas de entidade (clube, seleção, time da Kings, competição), no molde
 * dos jogos de técnico atuais: faixa no topo com as cores da entidade, números grandes à direita
 * e abas logo abaixo. A aba escolhida é lembrada por tipo de página.
 */
final class Pagina {
    private Pagina() {}

    private static final Map<String, String> abaLembrada = new HashMap<>();

    /** Faixa do topo: ícone grande, nome, linha de origem, blocos de números e o Voltar. */
    static VBox cabecalho(Node icone, String titulo, Node sub, String cor1, String cor2, Runnable voltar, Node... blocos) {
        VBox faixa = Ui.cabecalhoTela(icone, titulo, null, cor1, cor2);
        HBox topo = (HBox) faixa.getChildren().get(0);
        topo.getStyleClass().add("cab-pagina");
        topo.setStyle((topo.getStyle() == null ? "" : topo.getStyle()) + "-fx-min-height: 104; -fx-padding: 14 20 14 18;");
        VBox id = (VBox) topo.getChildren().get(icone == null ? 0 : 1);
        Label nome = (Label) id.getChildren().get(0);
        nome.setStyle("-fx-font-size: 26px;");
        // o nome da entidade não vira "Ca..." quando a faixa tem muitos blocos: os blocos é que apertam
        nome.setMinWidth(Region.USE_PREF_SIZE);
        id.setMinWidth(Region.USE_PREF_SIZE);
        if (sub != null) id.getChildren().add(sub);
        id.setSpacing(4);
        for (Node b : blocos) {
            if (b == null) continue;
            topo.getChildren().addAll(Ui.separadorCabecalho(), b);
        }
        if (voltar != null) {
            Region r = new Region();
            r.setMinWidth(8);
            Button v = Ui.botaoTecla("Voltar", "Esc", voltar);
            topo.getChildren().addAll(r, v);
        }
        return faixa;
    }

    /** Bloco "valor grande + rótulo" da faixa. */
    static VBox bloco(String valor, String rotulo) {
        return Ui.blocoCabecalho(valor, rotulo);
    }

    /** Bloco com um nó no lugar do valor (escudo + nome, estrelas...). */
    static VBox bloco(Node valor, String rotulo) {
        VBox v = new VBox(2, valor, Ui.rotulo(rotulo, "cab-rotulo"));
        v.setMinWidth(Region.USE_PREF_SIZE);
        return v;
    }

    /** Linha de origem da faixa (bandeira + texto). */
    static HBox origem(Node bandeira, String texto) {
        Label l = Ui.rotulo(texto, "cab-meta");
        return bandeira == null ? Ui.linha(8, l) : Ui.linha(8, bandeira, l);
    }

    /** Abas da página; {@code tipo} guarda a última aba escolhida (ex.: "competicao"). */
    static TabPane abas(String tipo, Tab... tabs) {
        TabPane tp = new TabPane();
        tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tp.getStyleClass().add("abas-pagina");
        for (Tab t : tabs) if (t != null) tp.getTabs().add(t);
        String lembrada = abaLembrada.get(tipo);
        if (lembrada != null) for (Tab t : tp.getTabs()) if (lembrada.equals(t.getText())) tp.getSelectionModel().select(t);
        tp.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) abaLembrada.put(tipo, b.getText());
        });
        Ui.ativarSobDemanda(tp);
        return tp;
    }

    /** Como {@link #aba}, montada só quando aberta. */
    static Tab abaSob(String nome, java.util.function.Supplier<Node> conteudo) {
        return Ui.abaSobDemanda(nome, () -> {
            VBox v = new VBox(16, conteudo.get());
            v.setStyle("-fx-padding: 4 2 16 2;");
            return Ui.rolar(v);
        });
    }

    /** Como {@link #abaFixa}, montada só quando aberta. */
    static Tab abaFixaSob(String nome, java.util.function.Supplier<Node> conteudo) {
        return Ui.abaSobDemanda(nome, conteudo);
    }

    /** Aba com conteúdo rolável (e margem interna). */
    static Tab aba(String nome, Node conteudo) {
        VBox v = new VBox(16, conteudo);
        v.setStyle("-fx-padding: 4 2 16 2;");
        Tab t = new Tab(nome, Ui.rolar(v));
        return t;
    }

    /** Aba cujo conteúdo cuida da própria rolagem (tabelas grandes, jogos por rodada). */
    static Tab abaFixa(String nome, Node conteudo) {
        return new Tab(nome, conteudo);
    }

    /** Página inteira: faixa no topo e abas ocupando o resto. */
    static VBox montar(Node cabecalho, TabPane abas) {
        VBox corpo = new VBox(0, abas);
        VBox.setVgrow(abas, Priority.ALWAYS);
        corpo.setStyle("-fx-padding: 0 22 0 22;");
        VBox v = new VBox(10, cabecalho, corpo);
        VBox.setVgrow(corpo, Priority.ALWAYS);
        return v;
    }

    /** Colunas lado a lado que dividem a largura (pesos relativos). */
    static HBox colunas(double espaco, Node... cols) {
        HBox h = new HBox(espaco);
        h.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        for (Node c : cols) {
            if (c == null) continue;
            if (c instanceof Region r) {
                r.setMaxWidth(Double.MAX_VALUE);
                r.setMinWidth(0);
                r.setPrefWidth(10);
            }
            HBox.setHgrow(c, Priority.ALWAYS);
            h.getChildren().add(c);
        }
        return h;
    }
}
