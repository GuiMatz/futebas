package br.futebas.ui.telas;

import br.futebas.mundo.Clube;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Tema;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoPartida.Atleta;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pênalti a favor: quem bate? (requisito: "ta muito feio essa tela arrume isso"). Cabeçalho na cor do
 * clube com o escudo; uma lista simples (no estilo da ordem dos pênaltis) com a foto, o nome, a posição, a
 * energia e a força de cada um, do recomendado (o cobrador definido na escalação, se está em campo; senão o
 * mais forte) para baixo. Clique na linha, tecla 1–9 ou Enter (o recomendado); "Deixar o cobrador definido"
 * (Esc) responde {@code null}. A mesma peça serve para a partida e para a tela dividida do multi-treinador.
 */
public final class EscolhaBatedor extends VBox {
    /** A foto de um jogador (id, largura, altura). */
    public interface Fotos {
        Node foto(int id, double largura, double altura);
    }

    private final List<Atleta> ordem = new ArrayList<>();
    private final Consumer<Integer> aoEscolher;
    private final Integer recomendado;
    private final boolean oficialEmCampo;
    private boolean respondido;

    /**
     * {@code tecnico}: o nome do técnico no multi (null no jogo de um técnico); {@code oficial}: o cobrador
     * definido na escalação (ou null); {@code aoEscolher} recebe o id escolhido, ou null (cobrador definido).
     */
    public EscolhaBatedor(Clube clube, String tecnico, List<Atleta> candidatos, Integer oficial, Fotos fotos, Consumer<Integer> aoEscolher) {
        super(0);
        this.aoEscolher = aoEscolher;
        getStyleClass().addAll("caixa-sobreposta", "caixa-batedor");
        ordem.addAll(candidatos);
        ordem.sort(Comparator.comparingInt(Atleta::forca).reversed());
        boolean emCampo = false;
        for (Atleta a : ordem) if (oficial != null && a.id() == oficial) emCampo = true;
        oficialEmCampo = emCampo;
        Integer rec = emCampo ? oficial : ordem.isEmpty() ? null : ordem.get(0).id();
        recomendado = rec;
        if (rec != null) {
            // o recomendado no topo; os outros do mais forte ao mais fraco
            for (int i = 0; i < ordem.size(); i++) if (ordem.get(i).id() == rec) ordem.add(0, ordem.remove(i));
        }

        VBox lista = new VBox(4);
        lista.getStyleClass().add("bt-lista");
        for (int i = 0; i < ordem.size(); i++) lista.getChildren().add(linha(i, ordem.get(i), fotos));
        ScrollPane rolagem = Ui.rolar(lista);
        rolagem.getStyleClass().add("bt-rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        Label ajuda = Ui.rotulo("Clique no batedor ou use as teclas 1 a " + Math.min(9, ordem.size()) + ".", "bt-ajuda");
        ajuda.setWrapText(true);
        VBox corpo = new VBox(8, ajuda, rolagem);
        corpo.getStyleClass().add("bt-corpo");
        VBox.setVgrow(corpo, Priority.ALWAYS);

        Button padrao = Ui.botaoTecla("Deixar o cobrador definido", "Esc", () -> escolher(null), "fantasma");
        padrao.setMinWidth(Region.USE_PREF_SIZE);
        Atleta r = atleta(rec);
        Button bater = Ui.botaoTecla(r == null ? "Confirmar" : "Bate " + r.nome(), "Enter", () -> escolher(rec), "primario");
        bater.setMinWidth(0);
        bater.getStyleClass().add("bt-bater");
        HBox acoes = new HBox(10, padrao, Ui.espaco(), bater);
        acoes.setAlignment(Pos.CENTER_LEFT);
        acoes.getStyleClass().add("bt-acoes");

        getChildren().addAll(cabecalho(clube, tecnico), corpo, acoes);
        setMaxSize(500, Region.USE_PREF_SIZE);
    }

    private Atleta atleta(Integer id) {
        if (id == null) return null;
        for (Atleta a : ordem) if (a.id() == id) return a;
        return null;
    }

    private static Node cabecalho(Clube clube, String tecnico) {
        Node escudo = clube == null ? new Region() : Imagens.escudo(clube.id, 44);
        Label sobre = Ui.rotulo("PÊNALTI A FAVOR", "bt-sobre");
        Label titulo = Ui.rotulo("Quem bate?", "bt-titulo");
        String quem = clube == null ? "" : clube.nome;
        if (tecnico != null) quem = (quem.isEmpty() ? "" : quem + "  ·  ") + "vez de " + tecnico;
        Label sub = Ui.rotulo(quem, "bt-sub");
        sub.setMinWidth(0);
        VBox txt = new VBox(1, sobre, titulo, sub);
        txt.setMinWidth(0);
        txt.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(txt, Priority.ALWAYS);
        HBox h = new HBox(14, escudo, txt);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("bt-cabecalho");
        h.setStyle(clube == null ? "-fx-background-color: linear-gradient(to right, #2b3038 0%, #20242a 100%);"
                : Tema.estiloCabecalho(clube.cor1, clube.cor2));
        Region filete = new Region();
        filete.setMinHeight(3);
        filete.setPrefHeight(3);
        filete.setStyle("-fx-background-color: " + (clube == null ? "#3a4049" : Tema.hex(Tema.faixaClube(clube.cor1, clube.cor2))) + ";");
        return new VBox(h, filete);
    }

    private HBox linha(int i, Atleta a, Fotos fotos) {
        boolean rec = recomendado != null && a.id() == recomendado;
        Label num = Ui.rotulo(i < 9 ? String.valueOf(i + 1) : "", "bt-num");
        num.setMinWidth(Region.USE_PREF_SIZE);
        if (i >= 9) num.setOpacity(0); // sem tecla: só guarda o alinhamento
        Node foto = fotos == null ? new Region() : fotos.foto(a.id(), 34, 40);
        Label nome = Ui.rotulo(a.nome(), "bt-nome");
        nome.setMinWidth(0);
        HBox info = new HBox(8, Ui.pos(a.vaga()));
        info.setAlignment(Pos.CENTER_LEFT);
        info.getChildren().add(CampoEscalacao.barraEnergia(a.energia(), 46, 5, true));
        if (a.amarelo()) info.getChildren().add(Ui.rotulo("", "bt-amarelo"));
        if (rec) info.getChildren().add(Ui.rotulo(oficialEmCampo ? "COBRADOR DEFINIDO" : "RECOMENDADO", "bt-selo"));
        VBox meio = new VBox(3, nome, info);
        meio.setMinWidth(0);
        meio.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(meio, Priority.ALWAYS);
        Label forca = Ui.forcaGrande(a.forca(), "bt-forca");
        forca.setMinWidth(Region.USE_PREF_SIZE);
        VBox fc = new VBox(-3, forca, Ui.rotulo("FORÇA", "bt-forca-rotulo"));
        fc.setAlignment(Pos.CENTER);
        fc.getStyleClass().add("bt-forca-caixa");
        fc.setMinWidth(Region.USE_PREF_SIZE);
        HBox h = new HBox(10, num, foto, meio, fc);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("bt-linha");
        if (rec) h.getStyleClass().add("bt-recomendado");
        h.setOnMouseClicked(e -> escolher(a.id()));
        return h;
    }

    /** Responde uma vez só ({@code null}: o cobrador definido). */
    public void escolher(Integer id) {
        if (respondido) return;
        respondido = true;
        aoEscolher.accept(id);
    }

    /** Teclas da tela: 1–9 escolhem a linha, Enter o recomendado, Esc o cobrador definido. Devolve se tratou. */
    public boolean tecla(KeyCode k) {
        if (k == KeyCode.ESCAPE) {
            escolher(null);
            return true;
        }
        if (k == KeyCode.ENTER) {
            escolher(recomendado);
            return true;
        }
        if (k.isDigitKey()) {
            String n = k.getName().replaceAll("\\D", "");
            if (!n.isEmpty()) {
                int i = Integer.parseInt(n) - 1;
                if (i >= 0 && i < Math.min(9, ordem.size())) {
                    escolher(ordem.get(i).id());
                    return true;
                }
            }
        }
        return false;
    }

    /** O id recomendado (cobrador definido em campo, senão o mais forte). */
    public Integer recomendado() { return recomendado; }

    /** Os nomes na ordem da lista (testes). */
    List<String> nomes() {
        List<String> l = new ArrayList<>();
        for (Atleta a : ordem) l.add(a.nome());
        return l;
    }
}
