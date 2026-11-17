package br.futebas.ui.telas;

import br.futebas.competicao.Partida;
import br.futebas.historia.Historia;
import br.futebas.mundo.Mensagem;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Fluxo;
import br.futebas.ui.modelo.Textos;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.Set;

/**
 * "Avançando o calendário": um cartão sobre a central com a data correndo, a barra até o
 * próximo jogo e as manchetes que vão saindo. Para sozinho no seu dia de jogo ou quando chega
 * algo que pede atenção (proposta, convite, convocação); Esc para antes.
 */
public final class Processando extends StackPane {
    private static final Set<String> PEDEM_ATENCAO = Set.of("proposta", "selecao");
    private static boolean rapidoPadrao;

    private final Sessao sessao;
    private final Label data = Ui.rotulo("", "proc-data");
    private final Label dia = Ui.rotulo("", "proc-dia");
    private final ProgressBar barra = new ProgressBar(0);
    private final Label alvo = Ui.rotulo("", "texto-suave");
    private final VBox feed = new VBox(6);
    private final Button parar = Ui.botaoTecla("Parar", "Esc", this::parar);
    private final CheckBox rapido = new CheckBox("Rápido");
    private volatile boolean pararPedido;
    private volatile boolean veloz = rapidoPadrao;
    private Runnable aoTerminar;

    public Processando(Sessao sessao) {
        this.sessao = sessao;
        getStyleClass().add("sobreposicao");
        barra.setMaxWidth(Double.MAX_VALUE);
        barra.setPrefHeight(6);
        rapido.setSelected(rapidoPadrao);
        rapido.setFocusTraversable(false);
        rapido.selectedProperty().addListener((o, a, b) -> {
            veloz = b;
            rapidoPadrao = b;
        });
        feed.getChildren().add(Ui.rotulo("As notícias aparecem aqui enquanto os dias passam.", "texto-suave"));
        feed.setMinHeight(190);
        feed.setPrefHeight(190);
        VBox caixa = new VBox(12, Ui.rotulo("Avançando o calendário", "titulo-menor"), dia, data, barra, alvo, Ui.separadorH(), feed,
                Ui.linha(10, rapido, Ui.espaco(), parar));
        caixa.getStyleClass().add("caixa-sobreposta");
        caixa.setMaxSize(560, javafx.scene.layout.Region.USE_PREF_SIZE);
        caixa.setPrefWidth(560);
        getChildren().add(caixa);
        setAlignment(Pos.CENTER);
    }

    /** Avança semana a semana até a semana do próximo jogo (ou um motivo para parar). */
    public void iniciar(java.util.function.Consumer<Fluxo.Rodada> fimDeTemporada, Runnable chegouAoJogo, Runnable voltar) {
        Partida p = sessao.fluxo.proximoJogo();
        int sa0 = sessao.jogo.mundo().semanaAbsoluta();
        int total = p == null ? 1 : Math.max(1, p.sa - sa0);
        alvo.setText(p == null ? "Até a próxima competição" : "Próximo jogo: " + Textos.dataCurta(p.data()) + "  ·  "
                + sessao.jogo.mundo().nomeParticipante(sessao.jogo.meuClube().id.equals(p.mandante) ? p.visitante : p.mandante));
        mostrarData();
        sessao.fluxo.manchetesNovas();
        App.atual().trabalhar(null, () -> {
            int feitas = 0;
            while (!pararPedido && sessao.jogo.meuClube() != null && !sessao.fluxo.temJogoNaSemana()) {
                Fluxo.Rodada r = sessao.fluxo.avancarSemana();
                feitas++;
                List<Historia.Evento> ev = sessao.fluxo.manchetesNovas();
                List<Mensagem> msgs = r.novasMensagens();
                double prog = Math.min(1, feitas / (double) total);
                Platform.runLater(() -> semana(ev, msgs, prog));
                if (r.fimDeTemporada() != null || r.demitido()) return r;
                String motivo = null;
                for (Mensagem m : msgs) if (PEDEM_ATENCAO.contains(m.tipo)) motivo = m.titulo;
                if (motivo != null) {
                    String mm = motivo;
                    Platform.runLater(() -> paradoPor(mm));
                    return null;
                }
                if (!veloz) dormir(180);
                if (feitas > 60) break;
            }
            return null;
        }, r -> {
            if (r != null) {
                fechar();
                fimDeTemporada.accept(r);
            } else if (pararPedido || sessao.jogo.meuClube() == null) {
                if (aoTerminar == null) {
                    fechar();
                    voltar.run();
                }
            } else if (aoTerminar == null) {
                fechar();
                chegouAoJogo.run();
            }
        });
    }

    private void mostrarData() {
        var d = sessao.jogo.dataAtual();
        data.setText(Textos.dataLonga(d));
        String[] dias = {"Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado", "Domingo"};
        dia.setText(dias[d.getDayOfWeek().getValue() - 1] + "  ·  semana " + sessao.jogo.semana());
    }

    private void semana(List<Historia.Evento> ev, List<Mensagem> msgs, double prog) {
        mostrarData();
        barra.setProgress(prog);
        for (Mensagem m : msgs) if (!"resultado".equals(m.tipo)) linha(PainelCaixa.etiquetaTipo(m.tipo), m.titulo);
        int n = 0;
        for (int i = ev.size() - 1; i >= 0 && n < 3; i--, n++) linha(Ui.etiqueta("Mundo", "azul"), ev.get(i).texto);
    }

    private void linha(javafx.scene.Node etiqueta, String texto) {
        if (feed.getChildren().size() == 1 && feed.getChildren().get(0) instanceof Label l0 && l0.getStyleClass().contains("texto-suave")) feed.getChildren().clear();
        Label t = Ui.rotulo(texto, "texto-medio");
        t.setMinWidth(0);
        HBox h = Ui.linha(8, etiqueta, t);
        h.setOpacity(0);
        feed.getChildren().add(0, h);
        FadeTransition f = new FadeTransition(Duration.millis(220), h);
        f.setToValue(1);
        f.play();
        while (feed.getChildren().size() > 5) feed.getChildren().remove(feed.getChildren().size() - 1);
        for (int i = 0; i < feed.getChildren().size(); i++) if (i > 0) feed.getChildren().get(i).setOpacity(1 - i * 0.16);
    }

    /** Algo pede a atenção do técnico: para e explica. */
    private void paradoPor(String motivo) {
        alvo.setText("Parado: " + motivo);
        alvo.getStyleClass().setAll("label", "kings-aviso");
        aoTerminar = () -> {
            fechar();
            App.atual().irParaHub("caixa");
        };
        parar.setText("Ver na caixa de entrada");
        parar.setOnAction(e -> aoTerminar.run());
    }

    public void parar() {
        if (aoTerminar != null) {
            aoTerminar.run();
            return;
        }
        pararPedido = true;
        parar.setDisable(true);
        parar.setText("Parando...");
    }

    private void fechar() {
        if (getParent() instanceof javafx.scene.layout.Pane pai) pai.getChildren().remove(this);
    }

    private static void dormir(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
