package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.mundo.Posicao;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Foto;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Lendas: os maiores da história real e os que viram lenda no seu mundo, com filtros por país,
 * posição e clube, foto, resumo e clubes.
 */
public final class PainelLendas implements Hub.Painel {
    private final Sessao sessao;

    public PainelLendas(Sessao sessao) {
        this.sessao = sessao;
    }

    @Override
    public Node construir() {
        Jogo j = sessao.jogo;
        List<Jogo.LendaInfo> todas;
        try {
            todas = j.lendas(null, null, null);
        } catch (RuntimeException e) {
            todas = List.of();
        }
        if (todas.isEmpty()) return Ui.conteudo(Ui.titulo("Lendas"), Ui.painel(null, Ui.vazio("Nenhuma lenda", null, null)));
        TreeSet<String> paises = new TreeSet<>((a, b) -> Textos.pais(a).compareTo(Textos.pais(b)));
        for (Jogo.LendaInfo l : todas) if (l.pais() != null) paises.add(l.pais());
        ComboBox<String> pais = new ComboBox<>();
        pais.getItems().add("Todos os países");
        pais.getItems().addAll(paises);
        pais.setValue("Todos os países");
        pais.setVisibleRowCount(16);
        pais.setButtonCell(celulaPais());
        pais.setCellFactory(x -> celulaPais());
        ComboBox<String> posicao = new ComboBox<>();
        posicao.getItems().add("Todas as posições");
        for (Posicao p : Posicao.values()) posicao.getItems().add(p.name() + " · " + p.nome);
        posicao.setValue("Todas as posições");
        CheckBox meuClube = new CheckBox(j.meuClube() == null ? "Só do meu clube" : "Só do " + j.meuClube().nome);
        meuClube.setDisable(j.meuClube() == null);
        ComboBox<String> tipo = new ComboBox<>();
        tipo.getItems().setAll("Reais e do seu mundo", "Só da história real", "Só do seu mundo");
        tipo.setValue(tipo.getItems().get(0));
        FlowPane grade = Ui.gradeFluida(300, 12);
        grade.setStyle("-fx-padding: 2 2 16 2;");
        Label conta = Ui.rotulo("", "texto-suave");
        Runnable filtrar = () -> {
            String ps = pais.getSelectionModel().getSelectedIndex() <= 0 ? null : pais.getValue();
            int pi = posicao.getSelectionModel().getSelectedIndex();
            Posicao pp = pi <= 0 ? null : Posicao.values()[pi - 1];
            String cl = meuClube.isSelected() && j.meuClube() != null ? j.meuClube().id : null;
            List<Jogo.LendaInfo> l = new ArrayList<>();
            try {
                for (Jogo.LendaInfo x : j.lendas(ps, cl, pp)) {
                    int t = tipo.getSelectionModel().getSelectedIndex();
                    if (t == 1 && !"real".equals(x.tipo())) continue;
                    if (t == 2 && "real".equals(x.tipo())) continue;
                    l.add(x);
                }
            } catch (RuntimeException e) {
                // sem camada histórica
            }
            grade.getChildren().clear();
            for (int i = 0; i < Math.min(150, l.size()); i++) grade.getChildren().add(cartao(j, l.get(i)));
            conta.setText(l.size() + (l.size() == 1 ? " lenda" : " lendas"));
            if (l.isEmpty()) grade.getChildren().add(Ui.vazio("Nenhuma lenda com esses filtros.", null, null));
        };
        pais.valueProperty().addListener((o, a, b) -> filtrar.run());
        posicao.valueProperty().addListener((o, a, b) -> filtrar.run());
        tipo.valueProperty().addListener((o, a, b) -> filtrar.run());
        meuClube.setOnAction(e -> filtrar.run());
        filtrar.run();
        var sp = Ui.rolar(grade);
        VBox v = Ui.conteudo(Ui.linha(12, Ui.titulo("Lendas"), conta, Ui.espaco(), tipo, pais, posicao, meuClube), sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
        return v;
    }

    private static javafx.scene.control.ListCell<String> celulaPais() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String p, boolean vazio) {
                super.updateItem(p, vazio);
                setText(null);
                if (vazio || p == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(p.length() == 3 ? Ui.linha(8, Imagens.bandeira(p, 12), Ui.rotulo(Textos.pais(p))) : Ui.rotulo(p));
            }
        };
    }

    private Node cartao(Jogo j, Jogo.LendaInfo l) {
        // sem foto real: rosto gerado (lenda do banco pelo id, do jogo pelo jogador)
        String chave = (l.lendaId() != null ? "lenda" + l.lendaId() : "jogador" + l.jogadorId());
        String caminho = l.fotoPng() == null && l.jogadorId() != null && j.jogador(l.jogadorId()) != null ? j.foto(l.jogadorId())
                : br.futebas.mundo.PerfilFoto.deNome(chave, l.nome(), l.pais(), 0, null, null).comFoto(l.fotoPng()).chave();
        Node foto = Foto.de(caminho, null, 76, 92);
        Label nome = Ui.rotulo(l.nome(), "titulo-painel");
        nome.setWrapText(true);
        nome.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox meta = Ui.linha(8, l.pais() == null ? new javafx.scene.layout.Region() : Imagens.bandeira(l.pais(), 12),
                Ui.rotulo(Textos.pais(l.pais()), "texto-medio"));
        if (l.posicao() != null) meta.getChildren().add(Ui.etiqueta(l.posicao(), null));
        HBox selo = Ui.linha(8, "real".equals(l.tipo()) ? Ui.etiqueta("história real", null) : Ui.etiqueta("lenda do seu mundo", "verde"));
        if (l.forcaAuge() != null) selo.getChildren().addAll(Ui.rotulo("auge", "texto-pequeno"), Ui.forca(l.forcaAuge()));
        VBox txt = new VBox(4, nome, meta, selo);
        txt.setMinWidth(0);
        HBox.setHgrow(txt, Priority.ALWAYS);
        HBox topo = new HBox(12, foto, txt);
        topo.setAlignment(Pos.TOP_LEFT);
        VBox card = new VBox(10, topo);
        if (l.resumo() != null && !l.resumo().isBlank()) card.getChildren().add(Ui.paragrafo(l.resumo(), "texto-medio"));
        if (l.clubes() != null && !l.clubes().isEmpty())
            card.getChildren().add(Ui.paragrafo("Clubes: " + String.join(", ", l.clubes().subList(0, Math.min(6, l.clubes().size()))), "texto-pequeno"));
        card.getStyleClass().add("painel");
        return card;
    }
}
