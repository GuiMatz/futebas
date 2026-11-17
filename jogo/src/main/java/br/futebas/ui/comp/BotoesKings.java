package br.futebas.ui.comp;

import br.futebas.kings.LigaKings;
import br.futebas.kings.MotorKings;
import br.futebas.mundo.Posicao;
import br.futebas.ui.modelo.AcompanhamentoKings;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Os dois botões da Kings ao lado de "Tática e trocas" (requisito, 12/11/2026): a sua carta
 * secreta (a carta pequena no desenho novo; clique = usar) e o pênalti do presidente. Pequenos, com
 * dica, no lugar das seções grandes da coluna lateral. Componente à parte que recebe o controle do
 * jogo ({@link AcompanhamentoKings}): a tela da Kings e a tela dividida do multi-treinador usam o mesmo.
 * <p>Escolhas da carta num menu rápido que não para o jogo (sem escolha em 5 s, o automático): jogador
 * estrela (um seu de linha), suspensão e pênalti invertido (um rival de linha, nunca o goleiro) e o
 * Coringa (roubar a carta do adversário ou virar qualquer outra carta).
 * <p>Uso: {@code new BotoesKings(acomp)}; a cada estado do jogo, {@link #atualizar}; no começo,
 * {@link #novoJogo}; quando a carta ou o pênalti saem, {@link #cartaUsada} / {@link #presidenteCobrado}.
 */
public final class BotoesKings extends HBox {
    /** Prazo do menu rápido antes do automático. */
    public static final Duration PRAZO = Duration.seconds(5);
    static final double LARGURA_CARTA = 21;

    private final AcompanhamentoKings acomp;
    private final Button botaoCarta = new Button(), botaoPresidente = new Button();
    private final Tooltip dicaCarta = new Tooltip(), dicaPresidente = new Tooltip();
    private CartaKings carta = CartaKings.pequenaVirada(LARGURA_CARTA);
    private MotorKings.Carta minha;
    private boolean cartaUsada, presidenteUsado, cliquei;
    private String resultadoCarta, presidente = "o presidente";
    private AcompanhamentoKings.Estado ultimo;
    private ContextMenu menu;
    private PauseTransition prazo;
    private Runnable aoUsar = () -> { }, aoCobrar = () -> { };

    public BotoesKings(AcompanhamentoKings acomp) {
        super(6);
        this.acomp = acomp;
        getStyleClass().add("kings-botoes");
        setAlignment(Pos.CENTER);
        botaoCarta.getStyleClass().add("kings-botao-icone");
        botaoCarta.setGraphic(carta);
        botaoCarta.setText("Carta");
        botaoCarta.setFocusTraversable(false);
        botaoCarta.setOnAction(e -> clicarCarta());
        botaoCarta.setTooltip(dicaCarta);
        botaoPresidente.getStyleClass().add("kings-botao-icone");
        botaoPresidente.setGraphic(iconePresidente());
        botaoPresidente.setText("Presidente");
        botaoPresidente.setFocusTraversable(false);
        botaoPresidente.setOnAction(e -> cobrar());
        botaoPresidente.setTooltip(dicaPresidente);
        for (Button b : List.of(botaoCarta, botaoPresidente)) {
            b.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
            b.setGraphicTextGap(6);
        }
        dicaCarta.setShowDelay(Duration.millis(250));
        dicaPresidente.setShowDelay(Duration.millis(250));
        getChildren().addAll(botaoCarta, botaoPresidente);
        textos();
    }

    /** Depois de usar a carta (a tela anima a carta do seu lado, lança o letreiro...). */
    public void aoUsarCarta(Runnable r) { aoUsar = r == null ? () -> { } : r; }

    /** Depois de cobrar o pênalti do presidente. */
    public void aoCobrarPresidente(Runnable r) { aoCobrar = r == null ? () -> { } : r; }

    /** A carta pequena do botão (a tela vira, brilha e marca "usada"). */
    public CartaKings carta() { return carta; }

    public Button botaoCarta() { return botaoCarta; }

    public Button botaoPresidente() { return botaoPresidente; }

    /** Jogo novo: carta virada, presidente disponível. {@code presidente}: quem bate (nome para a dica). */
    public void novoJogo(String presidente) {
        esconderMenu();
        minha = null;
        cartaUsada = false;
        cliquei = false;
        presidenteUsado = false;
        resultadoCarta = null;
        ultimo = null;
        this.presidente = presidente == null || presidente.isBlank() ? "o presidente" : presidente;
        carta = CartaKings.pequenaVirada(LARGURA_CARTA);
        botaoCarta.setGraphic(carta);
        botaoCarta.setDisable(false);
        botaoPresidente.setDisable(true);
        botaoCarta.getStyleClass().remove("pronto");
        botaoPresidente.getStyleClass().remove("pronto");
        textos();
    }

    /** Um estado novo do jogo: a sua carta (vira ao conhecer), se dá para usar agora e o presidente. */
    public void atualizar(AcompanhamentoKings.Estado e) {
        ultimo = e;
        if (e == null) return;
        MotorKings.Carta c = e.minhaCarta();
        if (c != null && minha != c) {
            minha = c;
            carta.virar(c);
        }
        boolean pode = c != null && e.podeUsarCarta() && !cartaUsada;
        carta.brilhar(pode);
        marcar(botaoCarta, pode);
        // a carta sumiu sem o seu clique: o Coringa do adversário roubou
        if (c == null && minha != null && !cartaUsada) cartaUsada(cliquei ? resultadoCarta : "ROUBADA");
        botaoPresidente.setDisable(presidenteUsado || !e.podePresidente());
        marcar(botaoPresidente, !presidenteUsado && e.podePresidente());
        if (menu != null && menu.isShowing() && !pode) esconderMenu();
        textos();
    }

    private static void marcar(Button b, boolean pronto) {
        if (pronto && !b.getStyleClass().contains("pronto")) b.getStyleClass().add("pronto");
        if (!pronto) b.getStyleClass().remove("pronto");
    }

    /** A carta saiu ("USADA · resultado"): cinza, sem clique. */
    public void cartaUsada(String resultado) {
        cartaUsada = true;
        if (resultado != null) resultadoCarta = resultado;
        esconderMenu();
        carta.usada(resultadoCarta);
        botaoCarta.setDisable(true);
        botaoCarta.getStyleClass().remove("pronto");
        textos();
    }

    /** O pênalti do presidente saiu ("GOL", "PERDEU"...). */
    public void presidenteCobrado(String resultado) {
        presidenteUsado = true;
        botaoPresidente.setDisable(true);
        botaoPresidente.getStyleClass().remove("pronto");
        if (resultado != null) dicaPresidente.setText("Pênalti do presidente: " + presidente + " cobrou · " + resultado);
    }

    private void textos() {
        if (minha == null) dicaCarta.setText("Sua carta secreta (aparece quando o jogo começa).\nJanelas: 5' a 16' e 23' a 35'.");
        else if (cartaUsada) dicaCarta.setText(minha.nome + ": USADA" + (resultadoCarta == null ? "" : " · " + resultadoCarta));
        else {
            boolean pode = ultimo != null && ultimo.podeUsarCarta();
            dicaCarta.setText("Sua carta secreta: " + minha.nome + "\n" + CartaKings.efeito(minha)
                    + (pode ? "\nClique para usar agora." : "\nJanelas: 5' a 16' e 23' a 35'."));
        }
        if (!presidenteUsado) dicaPresidente.setText("Pênalti do presidente: " + presidente + " bate · 1 por jogo"
                + (ultimo != null && ultimo.podePresidente() ? "\nClique para cobrar agora." : "\nFora da rampa, do gol duplo, do dado e do match ball."));
    }

    // ------------------------------------------------------------------ ações
    private void cobrar() {
        if (presidenteUsado || ultimo == null || !ultimo.podePresidente()) return;
        acomp.cobrarPenaltiPresidente();
        presidenteUsado = true;
        botaoPresidente.setDisable(true);
        botaoPresidente.getStyleClass().remove("pronto");
        aoCobrar.run();
    }

    private void clicarCarta() {
        AcompanhamentoKings.Estado e = ultimo;
        if (cartaUsada || e == null || e.minhaCarta() == null || !e.podeUsarCarta()) return;
        MotorKings.Carta c = e.minhaCarta();
        ContextMenu cm = new ContextMenu();
        if (c == MotorKings.Carta.CORINGA) {
            MenuItem roubar = new MenuItem("Roubar a carta do adversário");
            roubar.setOnAction(x -> usar(null, null));
            cm.getItems().addAll(roubar, new SeparatorMenuItem());
            for (MotorKings.Carta v : List.of(MotorKings.Carta.GOL_DUPLO, MotorKings.Carta.SANCAO, MotorKings.Carta.PENALTI_SHOOTOUT,
                    MotorKings.Carta.JOGADOR_ESTRELA, MotorKings.Carta.PENALTI, MotorKings.Carta.PENALTI_INVERTIDO)) {
                List<LigaKings.AtletaKings> alvos = alvos(v, e);
                if (alvos.isEmpty()) {
                    MenuItem it = new MenuItem("Virar " + v.nome);
                    it.setGraphic(CartaKings.pequena(v, 14));
                    it.setOnAction(x -> usar(v, null));
                    cm.getItems().add(it);
                } else {
                    Menu sub = new Menu("Virar " + v.nome);
                    sub.setGraphic(CartaKings.pequena(v, 14));
                    preencherAlvos(sub.getItems(), v, alvos);
                    cm.getItems().add(sub);
                }
            }
        } else {
            List<LigaKings.AtletaKings> alvos = alvos(c, e);
            if (alvos.isEmpty()) {
                usar(null, null);
                return;
            }
            MenuItem titulo = new MenuItem(c == MotorKings.Carta.JOGADOR_ESTRELA ? "Jogador estrela:" : c == MotorKings.Carta.SANCAO ? "Suspender:" : "Quem bate o invertido:");
            titulo.setDisable(true);
            cm.getItems().add(titulo);
            preencherAlvos(cm.getItems(), null, alvos);
        }
        esconderMenu();
        menu = cm;
        cm.show(botaoCarta, Side.TOP, 0, -4);
        // o jogo não para: sem escolha no prazo, o automático (Coringa: rouba a carta do adversário)
        prazo = new PauseTransition(PRAZO);
        prazo.setOnFinished(x -> {
            if (menu == cm && cm.isShowing()) {
                cm.hide();
                usar(null, null);
            }
        });
        prazo.play();
    }

    private void preencherAlvos(List<MenuItem> itens, MotorKings.Carta vira, List<LigaKings.AtletaKings> alvos) {
        for (LigaKings.AtletaKings a : alvos) {
            MenuItem it = new MenuItem(a.nome + " · " + (a.posicao == null ? "" : a.posicao.name()) + " " + Math.round(a.forca));
            it.setOnAction(x -> usar(vira, a.chave()));
            itens.add(it);
        }
        MenuItem auto = new MenuItem("Automático");
        auto.setOnAction(x -> usar(vira, null));
        itens.add(auto);
    }

    /** Quem pode ser escolhido pela carta (vazio = a carta não pede escolha). */
    static List<LigaKings.AtletaKings> alvos(MotorKings.Carta c, AcompanhamentoKings.Estado e) {
        List<LigaKings.AtletaKings> l = new ArrayList<>();
        if (c == MotorKings.Carta.JOGADOR_ESTRELA) {
            for (LigaKings.AtletaKings a : e.meusEmCampo()) if (a.posicao != Posicao.GOL) l.add(a);
        } else if (c == MotorKings.Carta.SANCAO || c == MotorKings.Carta.PENALTI_INVERTIDO) {
            for (LigaKings.AtletaKings a : e.rivaisEmCampo()) if (a.posicao != Posicao.GOL) l.add(a); // nunca o goleiro
        }
        return l;
    }

    private void usar(MotorKings.Carta vira, String alvo) {
        esconderMenu();
        if (cartaUsada) return;
        acomp.usarCarta(vira, alvo);
        cliquei = true;
        carta.brilhar(false);
        aoUsar.run();
    }

    private void esconderMenu() {
        if (prazo != null) prazo.stop();
        prazo = null;
        if (menu != null && menu.isShowing()) menu.hide();
        menu = null;
    }

    /** O menu rápido da carta está aberto? (testes) */
    public boolean menuAberto() { return menu != null && menu.isShowing(); }

    // ------------------------------------------------------------------ ícone do presidente
    /** Coroa sobre uma bola (traço preto num disco verde): o pênalti do presidente. */
    static Group iconePresidente() {
        Circle disco = new Circle(0, 0, 13, CartaKings.VERDE);
        disco.setStroke(CartaKings.CONTORNO);
        disco.setStrokeWidth(1.2);
        SVGPath coroa = new SVGPath();
        coroa.setContent("M -8 -2 L -9 -10 L -4 -6 L 0 -11 L 4 -6 L 9 -10 L 8 -2 Z");
        coroa.setFill(Color.BLACK);
        Group bola = CartaKings.bola(0, 5, 5);
        return new Group(disco, coroa, bola);
    }
}
