package br.futebas.ui.telas;

import br.futebas.historia.Historia;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.SimulacaoTemporada;
import br.futebas.ui.modelo.Textos;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * "Simular temporada": cartão sobre a tela com a data, "semana X de Y" e a barra, o último
 * resultado do seu time e a posição na liga, as manchetes e "Parar" (para no fim da semana). Cada
 * semana é um passo em segundo plano ({@link SimulacaoTemporada#passo()}), com ~150 ms entre elas:
 * a tela não trava e dá para ver a temporada andando. A virada do ano mostra "Encerrando a
 * temporada…" com o indicador girando (o trabalho pesado fica fora da thread da tela). Clube e Kings.
 */
public final class SimulandoTemporada extends StackPane {
    static final int RESPIRO_MS = 150;

    private final Sessao sessao;
    private final SimulacaoTemporada sim;
    private final Label data = Ui.rotulo("", "proc-data");
    private final Label semana = Ui.rotulo("", "proc-dia");
    private final ProgressBar barra = new ProgressBar(0);
    private final ProgressIndicator girando = new ProgressIndicator();
    private final Label situacao = Ui.rotulo("", "texto-medio");
    private final Label resultado = Ui.rotulo("", "texto-forte");
    private final Label posicao = Ui.rotulo("", "texto-suave");
    private final VBox feed = new VBox(6);
    private final Button parar = Ui.botaoTecla("Parar", "Esc", this::parar, "primario");
    private volatile boolean pararPedido;
    private Consumer<SimulacaoTemporada.Semana> aoTerminar;

    public SimulandoTemporada(Sessao sessao) {
        this.sessao = sessao;
        this.sim = new SimulacaoTemporada(sessao);
        getStyleClass().add("sobreposicao");
        barra.setMaxWidth(Double.MAX_VALUE);
        barra.setPrefHeight(6);
        girando.setPrefSize(22, 22);
        girando.setVisible(false);
        girando.setManaged(false);
        feed.getChildren().add(Ui.rotulo("As notícias aparecem aqui enquanto a temporada anda.", "texto-suave"));
        feed.setMinHeight(150);
        feed.setPrefHeight(150);
        resultado.setWrapText(true);
        VBox caixa = new VBox(12, Ui.rotulo("Simulando a temporada", "titulo-menor"), semana, data, barra,
                Ui.linha(8, girando, situacao), resultado, posicao, Ui.separadorH(), feed, Ui.linha(10, Ui.espaco(), parar));
        caixa.getStyleClass().add("caixa-sobreposta");
        caixa.setMaxSize(560, javafx.scene.layout.Region.USE_PREF_SIZE);
        caixa.setPrefWidth(560);
        getChildren().add(caixa);
        setAlignment(Pos.CENTER);
    }

    /**
     * Começa; {@code fim} recebe a última semana (com o motivo de parar, ou null se o técnico parou)
     * depois que o auxiliar devolve os jogos.
     */
    public void iniciar(Consumer<SimulacaoTemporada.Semana> fim) {
        this.aoTerminar = fim;
        sim.iniciar();
        situacao.setText("O auxiliar comanda os jogos.");
        mostrarData();
        sessao.fluxo.manchetesNovas();
        proxima();
    }

    private void proxima() {
        if (pararPedido) {
            terminar(null);
            return;
        }
        boolean virada = sim.proximaEhVirada();
        if (virada) {
            situacao.setText("Encerrando a temporada…");
            girando.setVisible(true);
            girando.setManaged(true);
        }
        App.atual().trabalhar(null, () -> {
            SimulacaoTemporada.Semana s = sim.passo();
            List<Historia.Evento> ev = sessao.fluxo.manchetesNovas();
            // a tela do fim de temporada carrega troféus, escudos e fotos: já deixa as imagens prontas aqui, fora da tela
            if (s.fimDeTemporada()) TelaFimTemporada.preaquecer(sessao, s.rodada().fimDeTemporada());
            return new Object[] {s, ev};
        }, x -> {
            SimulacaoTemporada.Semana s = (SimulacaoTemporada.Semana) x[0];
            @SuppressWarnings("unchecked") List<Historia.Evento> ev = (List<Historia.Evento>) x[1];
            mostrar(s, ev);
            if (s.parada() != null) {
                terminar(s);
                return;
            }
            PauseTransition p = new PauseTransition(Duration.millis(App.automatico ? 1 : RESPIRO_MS));
            p.setOnFinished(e -> proxima());
            p.play();
        });
    }

    private void mostrarData() {
        data.setText(Textos.dataLonga(sessao.jogo.dataAtual()));
        semana.setText("Semana " + Math.min(sim.total(), sim.feitas() + 1) + " de " + sim.total());
        barra.setProgress(Math.min(1, sim.feitas() / (double) sim.total()));
    }

    private void mostrar(SimulacaoTemporada.Semana s, List<Historia.Evento> ev) {
        mostrarData();
        girando.setVisible(false);
        girando.setManaged(false);
        situacao.setText(s.parada() != null ? "Parado: " + s.parada() : "O auxiliar comanda os jogos.");
        if (!s.resultados().isEmpty()) resultado.setText(String.join("   ·   ", s.resultados()));
        if (s.posicao() != null) posicao.setText(s.posicao());
        int n = 0;
        for (int i = ev.size() - 1; i >= 0 && n < 3; i--, n++) linha(ev.get(i).texto);
    }

    private void linha(String texto) {
        if (feed.getChildren().size() == 1 && feed.getChildren().get(0) instanceof Label l0 && l0.getStyleClass().contains("texto-suave")) feed.getChildren().clear();
        Label t = Ui.rotulo(texto, "texto-medio");
        t.setMinWidth(0);
        HBox h = Ui.linha(8, Ui.etiqueta("Mundo", "azul"), t);
        h.setOpacity(0);
        feed.getChildren().add(0, h);
        FadeTransition f = new FadeTransition(Duration.millis(200), h);
        f.setToValue(1);
        f.play();
        while (feed.getChildren().size() > 4) feed.getChildren().remove(feed.getChildren().size() - 1);
    }

    /** "Parar": termina no fim da semana em andamento. */
    public void parar() {
        pararPedido = true;
        parar.setDisable(true);
        parar.setText("Parando…");
    }

    private void terminar(SimulacaoTemporada.Semana s) {
        sim.terminar();
        if (getParent() instanceof javafx.scene.layout.Pane pai) pai.getChildren().remove(this);
        if (aoTerminar != null) aoTerminar.accept(s);
    }
}
