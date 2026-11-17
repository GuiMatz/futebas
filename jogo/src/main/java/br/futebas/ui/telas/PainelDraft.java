package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.kings.LigaKings;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Posicao;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Draft da Kings League com o técnico na vez: a ordem das escolhas (as já feitas com o nome do
 * jogador), o disponível com filtros e o botão de escolher. Depois da sua escolha, as da IA
 * aparecem uma a uma até a sua próxima vez.
 */
final class PainelDraft {
    private final Hub hub;
    private final VBox ordem = new VBox(2);
    private final ScrollPane rolOrdem = Ui.rolar(ordem);
    private final TableView<Jogador> pool = Ui.tabela();
    private final ComboBox<String> posicao = new ComboBox<>();
    private final ComboBox<String> idade = new ComboBox<>();
    private final Label vez = Ui.rotulo("", "titulo-painel");
    private final Button escolher = Ui.botao("Escolher", this::escolher, "primario");
    private int mostradas;

    PainelDraft(Hub hub) {
        this.hub = hub;
    }

    Node construir() {
        Jogo j = hub.jogo();
        LigaKings liga = j.ligaDoDraft();
        LigaKings.EstadoDraft e = j.estadoDraft();
        if (liga == null || e == null) return null;
        mostradas = e.escolhas.size();

        posicao.getItems().add("Todas as posições");
        for (Posicao p : Posicao.values()) posicao.getItems().add(p.name() + " · " + p.nome);
        posicao.getSelectionModel().select(0);
        idade.getItems().setAll("Qualquer idade", "Até 35", "Até 38", "Até 41");
        idade.getSelectionModel().select(0);
        posicao.valueProperty().addListener((o, a, b) -> preencherPool());
        idade.valueProperty().addListener((o, a, b) -> preencherPool());

        Ui.colPos(pool, x -> x.posicao);
        Ui.colNo(pool, "Jogador", 200, x -> {
            Label n = Ui.rotulo(x.nomeExibicao());
            n.setMinWidth(0);
            return Ui.linha(7, Imagens.bandeira(x.pais, 11), n);
        });
        Ui.colNum(pool, "Idade", 54, x -> x.idade(j.ano()));
        Ui.colForca(pool, "Força", Jogador::forcaExibida);
        Ui.col(pool, "Situação", 190, x -> x.status == br.futebas.mundo.StatusJogador.APOSENTADO ? "aposentado"
                : x.status == br.futebas.mundo.StatusJogador.LIVRE ? "passe livre" : Textos.situacao(j.mundo(), x));
        ScrollPane detalhe = new ScrollPane();
        detalhe.setFitToWidth(true);
        detalhe.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detalhe.setPrefWidth(340);
        detalhe.setMinWidth(320);
        pool.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            atualizarBotao();
            detalhe.setContent(b == null ? null : FichaJogador.construir(j, b, null));
        });
        pool.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) escolher();
        });

        VBox esq = Ui.painelAcoes("Ordem das escolhas", Ui.rotulo(e.ordem.size() + " escolhas", "texto-suave"), rolOrdem);
        VBox.setVgrow(rolOrdem, Priority.ALWAYS);
        esq.setPrefWidth(320);
        esq.setMinWidth(300);
        VBox dir = Ui.painelTabela("Disponíveis", Ui.linha(8, posicao, idade), pool);
        HBox.setHgrow(dir, Priority.ALWAYS);
        HBox corpo = new HBox(16, esq, dir, detalhe);
        VBox.setVgrow(corpo, Priority.ALWAYS);
        Label exp = Ui.info("Se avançar a semana sem escolher, o auxiliar escolhe pelas suas prioridades.");
        VBox v = Ui.conteudo(Ui.linha(12, Ui.titulo("Draft  ·  " + liga.nome), exp, Ui.espaco()), Ui.linha(12, vez, Ui.espaco(), escolher), corpo);
        montarOrdem(liga, e, e.escolhas.size());
        preencherPool();
        return v;
    }

    private void montarOrdem(LigaKings liga, LigaKings.EstadoDraft e, int ate) {
        ordem.getChildren().clear();
        String meu = hub.jogo().mundo().tecnico == null ? null : hub.jogo().mundo().tecnico.kingsTimeId;
        Node atual = null;
        for (int i = 0; i < e.ordem.size(); i++) {
            String tid = e.ordem.get(i);
            LigaKings.TimeKings t = liga.time(tid);
            LigaKings.Escolha esc = i < ate && i < e.escolhas.size() ? e.escolhas.get(i) : null;
            Label n = Ui.rotulo(esc != null ? esc.jogador : i == ate ? "na vez" : "—", esc != null ? "label" : "texto-suave");
            n.setMinWidth(0);
            HBox h = Ui.linha(8, Ui.rotulo(String.valueOf(i + 1), "manchete-data"), Ui.escudo(tid, 18),
                    Ui.nomeClube(tid, t == null ? tid : t.nome, tid.equals(meu) ? "texto-forte" : "texto-medio"), Ui.espaco(), n);
            h.getStyleClass().add("atleta");
            if (i == ate) {
                h.getStyleClass().add("entra");
                atual = h;
            }
            ordem.getChildren().add(h);
        }
        boolean minha = ate < e.ordem.size() && e.ordem.get(ate).equals(meu);
        vez.setText(e.terminado() && ate >= e.ordem.size() ? "Draft encerrado" : minha ? "É a sua vez" : "Escolhendo...");
        atualizarBotao();
        if (atual != null) {
            Node a = atual;
            javafx.application.Platform.runLater(() -> rolOrdem.setVvalue(Math.max(0, ordem.getChildren().indexOf(a) - 4) / (double) Math.max(1, ordem.getChildren().size())));
        }
    }

    private void preencherPool() {
        int idx = posicao.getSelectionModel().getSelectedIndex();
        Posicao p = idx <= 0 ? null : Posicao.values()[idx - 1];
        int imax = switch (idade.getSelectionModel().getSelectedIndex()) {
            case 1 -> 35;
            case 2 -> 38;
            case 3 -> 41;
            default -> 0;
        };
        pool.getItems().setAll(hub.jogo().disponiveisNoDraft(p, imax, 200));
    }

    private void atualizarBotao() {
        LigaKings.EstadoDraft e = hub.jogo().estadoDraft();
        String meu = hub.jogo().mundo().tecnico == null ? null : hub.jogo().mundo().tecnico.kingsTimeId;
        boolean minha = e != null && !e.terminado() && e.vez() != null && e.vez().equals(meu) && mostradas >= e.atual;
        escolher.setDisable(!minha || pool.getSelectionModel().getSelectedItem() == null);
    }

    private void escolher() {
        Jogador x = pool.getSelectionModel().getSelectedItem();
        if (x == null || escolher.isDisable()) return;
        Jogo j = hub.jogo();
        LigaKings liga = j.ligaDoDraft();
        LigaKings.EstadoDraft antes = j.estadoDraft();
        int ja = antes == null ? 0 : antes.escolhas.size();
        if (!j.escolherNoDraft(x.id)) {
            Ui.informar("Draft", "Não deu para escolher " + x.nomeExibicao() + ".");
            return;
        }
        LigaKings.EstadoDraft e = j.estadoDraft();
        if (e == null || liga == null) {
            hub.atualizar();
            return;
        }
        // revela as escolhas da IA uma a uma
        mostradas = ja + 1;
        montarOrdem(liga, e, mostradas);
        preencherPool();
        int total = e.escolhas.size();
        if (total <= mostradas) return;
        Timeline tl = new Timeline();
        for (int k = mostradas + 1; k <= total; k++) {
            int ate = k;
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(350.0 * (k - mostradas)), ev -> {
                mostradas = ate;
                montarOrdem(liga, e, ate);
            }));
        }
        tl.play();
    }

    @SuppressWarnings("unused")
    private static List<Node> nada() { return new ArrayList<>(); }

    @SuppressWarnings("unused")
    private static Pos alinhamento() { return Pos.CENTER; }
}
