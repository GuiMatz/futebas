package br.futebas.ui.telas;

import br.futebas.ui.comp.CartaKings;
import br.futebas.ui.comp.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Coluna lateral da Kings no jogo ao vivo, só com a informação do jogo (requisito, 12/11/2026:
 * a sua carta e o pênalti do presidente viraram botões pequenos ao lado de "Tática e trocas",
 * {@link br.futebas.ui.comp.BotoesKings}):
 * <ul>
 *   <li>"Suas armas": uma linha com o estado da sua carta e do pênalti do presidente;</li>
 *   <li>a carta do adversário: virada ("?") até ele usar, depois aberta com o resultado;</li>
 *   <li>a fase (rampa, 7x7, gol duplo, dado, match ball) com a barra do jogo, o próximo acontecimento
 *   ("Dado em 3 min") e os efeitos em curso (jogador estrela, suspensão).</li>
 * </ul>
 */
final class PainelKingsLateral extends VBox {
    private final StackPane lugarRival = new StackPane();
    private CartaKings rival;
    private final VBox armas = new VBox(8);
    private final Label nomeRival = Ui.rotulo("", "texto-pequeno"), resultadoRival = Ui.rotulo("Ainda não usou.", "texto-pequeno");
    private final Label faseNome = Ui.rotulo("", "texto-forte"), proximo = Ui.rotulo("", "texto-pequeno");
    private final ProgressBar progresso = new ProgressBar(0);
    private final Label efeitos = Ui.rotulo("", "texto-pequeno");

    PainelKingsLateral() {
        super(12);
        getStyleClass().addAll("coluna-partida", "kings-lateral");
        setPrefWidth(260);
        setMinWidth(230);

        resultadoRival.setWrapText(true);
        VBox txtRival = new VBox(2, nomeRival, resultadoRival);
        HBox.setHgrow(txtRival, Priority.ALWAYS);
        HBox adv = new HBox(12, lugarRival, txtRival);
        adv.setAlignment(Pos.CENTER_LEFT);

        progresso.setMaxWidth(Double.MAX_VALUE);
        progresso.getStyleClass().add("kings-progresso");
        VBox fase = new VBox(4, faseNome, progresso, proximo);
        efeitos.setWrapText(true);

        getChildren().addAll(titulo("Fase do jogo"), fase, efeitos, Ui.separadorH(), titulo("Carta do adversário"), adv,
                Ui.separadorH(), titulo("Suas armas"), armas);
    }

    private static Label titulo(String t) {
        return Ui.rotulo(t, "titulo-painel");
    }

    /** Jogo novo: a carta do adversário virada e o nome dele. */
    void novoJogo(Node escudoRival, String rivalNome) {
        rival = CartaKings.miniVirada(escudoRival);
        lugarRival.getChildren().setAll(rival);
        nomeRival.setText(rivalNome == null ? "" : rivalNome);
        resultadoRival.setText("Ainda não usou.");
        armas.getChildren().clear();
        efeitos.setText("");
    }

    CartaKings rival() { return rival; }

    /**
     * Uma arma no quadro "Suas armas" (requisito: "deixe isso mais claro"): o nome, um selo de
     * estado ({@code ok} = pode usar, {@code espera} = ainda não, {@code usada}) e uma linha explicando.
     */
    record Arma(String titulo, String selo, String estado, String detalhe) {}

    /** O quadro "Suas armas": um cartão por arma (carta e pênalti do presidente). */
    void armas(Arma... l) {
        armas.getChildren().clear();
        for (Arma a : l) {
            Label selo = Ui.rotulo(a.selo(), "arma-selo", "arma-" + a.estado());
            selo.setMinWidth(Region.USE_PREF_SIZE);
            Label tit = Ui.rotulo(a.titulo(), "arma-titulo");
            tit.setWrapText(true);
            Region esp = new Region();
            HBox.setHgrow(esp, Priority.ALWAYS);
            HBox topo = new HBox(8, tit, esp, selo);
            topo.setAlignment(Pos.CENTER_LEFT);
            Label det = Ui.rotulo(a.detalhe(), "arma-detalhe");
            det.setWrapText(true);
            VBox card = new VBox(4, topo, det);
            card.getStyleClass().addAll("arma-cartao", "arma-cartao-" + a.estado());
            armas.getChildren().add(card);
        }
    }

    void resultadoRival(String t) { resultadoRival.setText(t); }

    void efeitos(String t) { efeitos.setText(t); }

    /** A fase (texto), a barra do jogo (0–1) e o próximo acontecimento. */
    void fase(String nome, int minuto) {
        faseNome.setText(nome);
        progresso.setProgress(Math.min(1, minuto / 36.0));
        proximo.setText(proximoEvento(minuto));
    }

    /** "Dado em 3 min", "Gol duplo em 5 min"... pelo relógio da Kings (rampa 0–4, gol duplo 17–19, dado 20–22, fim 36). */
    static String proximoEvento(int min) {
        if (min < 5) return "7 contra 7 em " + (5 - min) + " min · janela da carta abre no 5'";
        if (min < 17) return "Gol duplo em " + (17 - min) + " min";
        if (min < 20) return "Dado em " + (20 - min) + " min (intervalo antes)";
        if (min < 23) return "Todos de volta (7x7) em " + (23 - min) + " min";
        if (min < 36) return "Fim do tempo em " + (36 - min) + " min · depois match ball ou shoot out";
        return "Match ball: quem marcar o próximo vence";
    }
}
