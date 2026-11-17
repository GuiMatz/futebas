package br.futebas.ui.telas;

import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoPartida.Atleta;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Escolha da ordem dos batedores da disputa de pênaltis (requisito: "aparece a tela, você escolhe a
 * ordem"). Vem preenchida com a sugestão automática (quem terminou em campo: o cobrador e depois do mais forte
 * ao mais fraco); o técnico confirma ou reordena arrastando a linha ou
 * pelas setas. Os 5 primeiros batem a série; os demais, nas alternadas. A mesma peça serve para a
 * partida e para a tela dividida do multi-treinador.
 */
public final class OrdemPenaltis extends VBox {
    private final List<Atleta> sugestao;
    private final List<Atleta> ordem;
    private final VBox lista = new VBox(2);
    private final Consumer<List<Integer>> aoConfirmar;
    private boolean respondido;
    private int arrastando = -1;

    /**
     * {@code cabecalho}: o título (no multi, de quem é a vez, na cor do time); {@code aoConfirmar}
     * recebe os ids na ordem.
     */
    public OrdemPenaltis(Node cabecalho, List<Atleta> sugestao, Consumer<List<Integer>> aoConfirmar) {
        super(10);
        this.sugestao = List.copyOf(sugestao);
        this.ordem = new ArrayList<>(sugestao);
        this.aoConfirmar = aoConfirmar;
        getStyleClass().addAll("caixa-sobreposta", "ordem-penaltis");
        Label ajuda = Ui.rotulo("Arraste a linha ou use as setas. Os 5 primeiros batem a série; os outros, nas alternadas (um de cada vez, até alguém errar).", "texto-pequeno", "pen-ajuda");
        ajuda.setWrapText(true);
        Button auto = Ui.botao("Ordem automática", this::automatica);
        Button ok = Ui.botaoTecla("Confirmar ordem", "Enter", this::confirmar, "primario");
        lista.getStyleClass().add("pen-lista");
        HBox botoes = Ui.linha(10, auto, Ui.espaco(), ok);
        botoes.setStyle("-fx-padding: 6 0 0 0;");
        getChildren().addAll(cabecalho, ajuda, lista, botoes);
        setMaxSize(580, Region.USE_PREF_SIZE);
        desenhar();
    }

    /** Os ids na ordem atual. */
    List<Integer> ids() {
        List<Integer> l = new ArrayList<>();
        for (Atleta a : ordem) l.add(a.id());
        return l;
    }

    /** Os nomes na ordem atual (testes). */
    List<String> nomes() {
        List<String> l = new ArrayList<>();
        for (Atleta a : ordem) l.add(a.nome());
        return l;
    }

    /** Move o batedor da posição {@code de} para {@code para}. */
    public void mover(int de, int para) {
        if (de < 0 || de >= ordem.size() || para < 0 || para >= ordem.size() || de == para) return;
        ordem.add(para, ordem.remove(de));
        desenhar();
    }

    /** Volta à sugestão. */
    void automatica() {
        ordem.clear();
        ordem.addAll(sugestao);
        desenhar();
    }

    /** Confirma a ordem na tela (uma vez só). */
    void confirmar() {
        if (respondido) return;
        respondido = true;
        aoConfirmar.accept(ids());
    }

    boolean respondido() { return respondido; }

    private void desenhar() {
        lista.getChildren().clear();
        for (int i = 0; i < ordem.size(); i++) {
            if (i == 5) lista.getChildren().add(separador());
            lista.getChildren().add(linha(i, ordem.get(i)));
        }
    }

    /** "ALTERNADAS" com um fio até a borda. */
    private static HBox separador() {
        Label sep = Ui.rotulo("ALTERNADAS", "pen-separador");
        sep.setMinWidth(Region.USE_PREF_SIZE);
        Region fio = new Region();
        fio.getStyleClass().add("pen-fio");
        HBox.setHgrow(fio, Priority.ALWAYS);
        HBox h = new HBox(10, sep, fio);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("pen-separador-linha");
        return h;
    }

    private HBox linha(int i, Atleta a) {
        Label num = Ui.rotulo(String.valueOf(i + 1), "pen-num");
        num.setMinWidth(Region.USE_PREF_SIZE);
        Label nome = Ui.rotulo(a.nome(), "pen-nome");
        nome.setMinWidth(0);
        Button sobe = Ui.botao("▲", () -> mover(i, i - 1), "fantasma", "pen-seta");
        Button desce = Ui.botao("▼", () -> mover(i, i + 1), "fantasma", "pen-seta");
        sobe.setDisable(i == 0);
        desce.setDisable(i == ordem.size() - 1);
        for (Button b : List.of(sobe, desce)) {
            b.setFocusTraversable(false);
            b.setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox setas = new HBox(2, sobe, desce);
        setas.setAlignment(Pos.CENTER);
        Region alca = new Region();
        alca.getStyleClass().add("pen-alca");
        HBox h = new HBox(10, alca, num, Ui.pos(a.vaga()), nome, Ui.espaco(), Ui.forca(a.forca()), setas);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().addAll("pen-linha", i < 5 ? "pen-serie" : "pen-alternada");
        if (i % 2 == 1) h.getStyleClass().add("pen-par");
        // arrastar a linha para outra posição: a marca (fio na cor do acento) mostra onde ela cai
        h.setOnDragDetected(e -> {
            arrastando = i;
            Dragboard db = h.startDragAndDrop(TransferMode.MOVE);
            javafx.scene.SnapshotParameters sp = new javafx.scene.SnapshotParameters();
            sp.setFill(javafx.scene.paint.Color.TRANSPARENT);
            db.setDragView(h.snapshot(sp, null), e.getX(), e.getY());
            ClipboardContent c = new ClipboardContent();
            c.putString("pen:" + i);
            db.setContent(c);
            h.getStyleClass().add("pen-arrastando");
            e.consume();
        });
        h.setOnDragOver(e -> {
            if (arrastando >= 0 && arrastando != i) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        h.setOnDragEntered(e -> {
            if (arrastando >= 0 && arrastando != i) h.getStyleClass().add(arrastando > i ? "pen-cai-acima" : "pen-cai-abaixo");
        });
        h.setOnDragExited(e -> h.getStyleClass().removeAll("pen-cai-acima", "pen-cai-abaixo"));
        h.setOnDragDropped(e -> {
            int de = arrastando;
            arrastando = -1;
            e.setDropCompleted(de >= 0);
            e.consume();
            if (de >= 0) mover(de, i);
        });
        h.setOnDragDone(e -> {
            arrastando = -1;
            h.getStyleClass().remove("pen-arrastando");
        });
        return h;
    }
}
