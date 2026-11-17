package br.futebas.ui.telas;

import br.futebas.ui.Opcoes;
import br.futebas.ui.Opcoes.AutoSalvar;
import br.futebas.ui.Opcoes.Escala;
import br.futebas.ui.Opcoes.Moeda;
import br.futebas.ui.Opcoes.Noticias;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Opções do jogo, dentro da janela (camada por cima da tela atual): seções à esquerda (Partida,
 * Jogo, Interface, Salvamento), uma seção por vez à direita, opções agrupadas sob subtítulos, uma
 * por linha com o controle alinhado numa coluna fixa. Tudo vale na hora (sem botão de salvar).
 */
public final class TelaOpcoes extends StackPane {
    public static final List<String> SECOES = List.of("Partida", "Jogo", "Interface", "Salvamento", "Créditos");
    private static final double COLUNA_CONTROLE = 360;
    private static String ultimaSecao = "Partida";
    private static TelaOpcoes aberta;

    private final Map<String, Button> botoes = new LinkedHashMap<>();
    private final VBox conteudo = new VBox(0);
    private final ScrollPane rolagem = new ScrollPane();
    private String secao;

    /** Abre (ou traz para a frente) as Opções. */
    public static TelaOpcoes abrir() {
        if (Ui.camadaModal == null) return null;
        if (aberta != null && aberta.getParent() != null) return aberta;
        aberta = new TelaOpcoes();
        Ui.camadaModal.getChildren().add(aberta);
        Ui.camadaModal.setVisible(true);
        javafx.application.Platform.runLater(aberta::requestFocus);
        return aberta;
    }

    public static boolean estaAberta() { return aberta != null && aberta.getParent() != null; }

    private TelaOpcoes() {
        getStyleClass().add("sobreposicao");
        setFocusTraversable(true);

        VBox nav = new VBox(2);
        nav.getStyleClass().add("pref-nav");
        for (String s : SECOES) {
            Button b = new Button(s);
            b.getStyleClass().setAll("nav-item");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setFocusTraversable(false);
            b.setOnAction(e -> mostrar(s));
            botoes.put(s, b);
            nav.getChildren().add(b);
        }

        conteudo.getStyleClass().add("pref-conteudo");
        conteudo.setMaxWidth(720);
        conteudo.setPrefWidth(720);
        StackPane limite = new StackPane(conteudo);
        limite.setAlignment(Pos.TOP_LEFT);
        rolagem.setContent(limite);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagem.getStyleClass().add("pref-rolagem");

        Button restaurar = Ui.botao("Restaurar padrões", this::restaurar);
        Button fechar = Ui.botaoTecla("Fechar", "Esc", this::fechar, "primario");
        HBox rodape = new HBox(10, restaurar, Ui.espaco(), fechar);
        rodape.setAlignment(Pos.CENTER_LEFT);
        rodape.getStyleClass().add("pref-rodape");

        Label titulo = Ui.rotulo("Configurações", "titulo-tela");
        HBox topo = new HBox(titulo);
        topo.getStyleClass().add("pref-topo");

        BorderPane caixa = new BorderPane();
        caixa.getStyleClass().add("pref-caixa");
        caixa.setTop(topo);
        caixa.setLeft(nav);
        caixa.setCenter(rolagem);
        caixa.setBottom(rodape);
        caixa.setMaxSize(1000, 720);
        caixa.setPrefSize(1000, 720);
        caixa.setMinSize(0, 0);
        getChildren().add(caixa);

        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (Ui.camadaModal.getChildren().indexOf(this) != Ui.camadaModal.getChildren().size() - 1) return; // um diálogo por cima
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
                e.consume();
            } else if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN) {
                int i = SECOES.indexOf(secao) + (e.getCode() == KeyCode.UP ? -1 : 1);
                if (i >= 0 && i < SECOES.size()) mostrar(SECOES.get(i));
                e.consume();
            }
        });
        mostrar(ultimaSecao);
    }

    public void mostrar(String s) {
        secao = s;
        ultimaSecao = s;
        botoes.forEach((k, b) -> {
            b.getStyleClass().remove("ativo");
            if (k.equals(s)) b.getStyleClass().add("ativo");
        });
        conteudo.getChildren().setAll(Ui.rotulo(s, "pref-secao"));
        switch (s) {
            case "Partida" -> partida();
            case "Jogo" -> jogo();
            case "Interface" -> interfaceDoJogo();
            case "Créditos" -> creditos();
            default -> salvamento();
        }
        rolagem.setVvalue(0);
    }

    public String secao() { return secao; }

    public void fechar() {
        Ui.camadaModal.getChildren().remove(this);
        Ui.camadaModal.setVisible(!Ui.camadaModal.getChildren().isEmpty());
        aberta = null;
    }

    private void restaurar() {
        if (!Ui.confirmar("Restaurar padrões", "Voltar todas as opções ao padrão?")) return;
        Opcoes.restaurarPadroes();
        mostrar(secao);
    }

    // ------------------------------------------------------------------ seções
    private void partida() {
        grupo("Ritmo");
        linha("Velocidade da partida", null, segmentos(Velocidade.visiveis(), v -> v.nome, Opcoes.velocidade(), Opcoes::velocidade));
        linha("Velocidade da Kings League", null, segmentos(Velocidade.visiveis(), v -> v.nome, Opcoes.velocidadeKings(), Opcoes::velocidadeKings));
        grupo("Pausas");
        linha("Gol do meu time", null, chave(Opcoes::pausarGolMeu, Opcoes::pausarGolMeu));
        linha("Gol sofrido", null, chave(Opcoes::pausarGolSofrido, Opcoes::pausarGolSofrido));
        linha("Lesão no meu time", null, chave(Opcoes::pausarLesao, Opcoes::pausarLesao));
        linha("Expulsão no meu time", null, chave(Opcoes::pausarExpulsao, Opcoes::pausarExpulsao));
        grupo("Durante o jogo");
        linha("Tática no intervalo", null, chave(Opcoes::taticaNoIntervalo, Opcoes::taticaNoIntervalo));
        linha("Escolher o batedor de pênalti", "Desligado: cobrador definido na escalação", chave(Opcoes::escolherBatedor, Opcoes::escolherBatedor));
        linha("Outros jogos ao vivo", "Desligado: placares só no fim", chave(Opcoes::outrosJogosAoVivo, Opcoes::outrosJogosAoVivo));
        linha("Tabela ao vivo", "Desligado: atualiza no fim", chave(Opcoes::tabelaAoVivo, Opcoes::tabelaAoVivo));
    }

    private void jogo() {
        grupo("Elenco");
        linha("Renovação automática em carreiras novas", "No jogo atual: Elenco", chave(Opcoes::renovacaoAutomaticaNovos, Opcoes::renovacaoAutomaticaNovos));
        grupo("Antes da partida");
        linha("Escalação antes de cada jogo", "Campo, banco e formação com o botão JOGAR", chave(Opcoes::escalacaoAntesDoJogo, Opcoes::escalacaoAntesDoJogo));
        linha("Avisar escalação com desfalques", "Lesionados, suspensos e convocados", chave(Opcoes::confirmarEscalacao, Opcoes::confirmarEscalacao));
        grupo("Amistosos");
        linha("Amistosos novos com o auxiliar", null, chave(Opcoes::delegarAmistosos, Opcoes::delegarAmistosos));
        grupo("Kings League");
        linha("Times customizados", "Kings League IOS e Kings Cup IOS nos jogos novos", chave(Opcoes::timesCustomizados, Opcoes::timesCustomizados));
        grupo("Notícias");
        linha("Notícias na central", null, segmentos(List.of(Noticias.values()), n -> n.nome, Opcoes.noticias(), Opcoes::noticias));
    }

    private void interfaceDoJogo() {
        grupo("Tela");
        linha("Escala da interface", null, segmentos(List.of(Escala.values()), e -> e.nome, Opcoes.escala(), Opcoes::escala));
        linha("Tela cheia", "F11", chave(Opcoes::telaCheia, Opcoes::telaCheia));
        grupo("Exibição");
        linha("Valores em dinheiro", null, segmentos(List.of(Moeda.values()), m -> m.nome, Opcoes.moeda(), Opcoes::moeda));
        linha("Fotos dos jogadores", "Desligado: silhuetas", chave(Opcoes::fotos, Opcoes::fotos));
    }

    private void salvamento() {
        grupo("Automático");
        ComboBox<AutoSalvar> cb = new ComboBox<>();
        cb.getItems().setAll(AutoSalvar.values());
        cb.setValue(Opcoes.autoSalvar());
        Function<AutoSalvar, String> nome = a -> a.nome;
        cb.setCellFactory(x -> celula(nome));
        cb.setButtonCell(celula(nome));
        cb.setPrefWidth(200);
        cb.setFocusTraversable(false);
        cb.valueProperty().addListener((o, a, b) -> {
            if (b != null) Opcoes.autoSalvar(b);
        });
        linha("Salvamento automático", null, cb);
        linha("Arquivos automáticos", "Em rodízio, o mais antigo é substituído",
                segmentos(List.of(1, 2, 3), String::valueOf, Opcoes.vagasAutomaticas(), Opcoes::vagasAutomaticas));
        grupo("Ao sair");
        linha("Confirmar ao sair sem salvar", null, chave(Opcoes::confirmarSair, Opcoes::confirmarSair));
    }

    /** Créditos: de onde vêm os dados e as imagens (regra do jogo: as fontes ficam aqui, não na ficha). */
    private void creditos() {
        grupo("FUTÉBAS");
        credito("Versão", versao());
        credito("Plataforma", "Java 21 · JavaFX 21");
        grupo("Dados de jogadores e clubes");
        credito("Elencos, contratos e transferências", "Transfermarkt (transfermarkt-datasets)");
        credito("Dados biográficos", "Wikidata");
        credito("Elencos atuais e temporadas", "Wikipédia");
        credito("Referência de atributos", "EA SPORTS FC");
        credito("Divisões inferiores", "Bases de dados da comunidade");
        grupo("Competições e regulamentos");
        credito("Estatísticas históricas", "RSSSF · RSSSF Brasil");
        credito("Regulamentos", "CBF · CONMEBOL · UEFA · FIFA · IFAB · federações estaduais");
        credito("Formatos e calendários", "Wikipédia");
        grupo("Kings League");
        credito("Times, elencos, regras e imagens", "kingsleague.pro");
        grupo("Imagens");
        credito("Escudos e fotos de jogadores", "Transfermarkt · Wikipédia · Wikimedia Commons");
        credito("Bandeiras", "Wikimedia Commons");
        grupo("Aviso legal");
        credito("Marcas registradas", "Nomes, escudos, fotos e marcas citados pertencem aos seus respectivos proprietários. "
                + "O FUTÉBAS não é afiliado, patrocinado nem endossado por nenhuma liga, federação, clube ou empresa citada.");
        credito("Distribuição", "Projeto pessoal, sem fins comerciais.");
    }

    private static String versao() {
        String v = TelaOpcoes.class.getPackage() != null ? TelaOpcoes.class.getPackage().getImplementationVersion() : null;
        return v != null ? v : "0.1.0";
    }

    private void credito(String titulo, String texto) {
        Label t = Ui.rotulo(titulo, "pref-rotulo");
        Label d = Ui.paragrafo(texto, "pref-sub");
        VBox v = new VBox(2, t, d);
        v.getStyleClass().add("pref-linha");
        conteudo.getChildren().add(v);
    }

    // ------------------------------------------------------------------ peças
    private void grupo(String titulo) {
        Label l = Ui.rotulo(titulo.toUpperCase(java.util.Locale.ROOT), "pref-grupo");
        l.setMaxWidth(Double.MAX_VALUE);
        conteudo.getChildren().add(l);
    }

    private void linha(String rotulo, String sub, Node controle) {
        VBox txt = new VBox(1, Ui.rotulo(rotulo, "pref-rotulo"));
        if (sub != null) txt.getChildren().add(Ui.rotulo(sub, "pref-sub"));
        txt.setAlignment(Pos.CENTER_LEFT);
        txt.setMinWidth(0);
        HBox.setHgrow(txt, Priority.ALWAYS);
        StackPane col = new StackPane(controle);
        col.setAlignment(Pos.CENTER_RIGHT);
        col.setMinWidth(COLUNA_CONTROLE);
        col.setPrefWidth(COLUNA_CONTROLE);
        col.setMaxWidth(COLUNA_CONTROLE);
        HBox h = new HBox(16, txt, col);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("pref-linha");
        conteudo.getChildren().add(h);
    }

    private static <T> ListCell<T> celula(Function<T, String> nome) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean vazio) {
                super.updateItem(item, vazio);
                setText(vazio || item == null ? null : nome.apply(item));
            }
        };
    }

    private static <T> Node segmentos(List<T> valores, Function<T, String> nome, T atual, Consumer<T> aoEscolher) {
        HBox h = Ui.segmentos(valores, nome, atual, aoEscolher);
        h.getStyleClass().add("pref-segmentos");
        h.setMaxWidth(Region.USE_PREF_SIZE);
        return h;
    }

    private static Node chave(Supplier<Boolean> valor, Consumer<Boolean> mudar) {
        Chave c = new Chave(valor.get());
        c.ligado.addListener((o, a, b) -> mudar.accept(b));
        return c;
    }

    /** Chave liga/desliga (trilho e bolinha), na cor de destaque quando ligada. */
    public static final class Chave extends StackPane {
        public final BooleanProperty ligado = new SimpleBooleanProperty();
        private final Region trilho = new Region();
        private final Circle bola = new Circle(8);

        public Chave(boolean inicial) {
            getStyleClass().add("chave");
            trilho.getStyleClass().add("chave-trilho");
            bola.getStyleClass().add("chave-bola");
            trilho.setMinSize(40, 22);
            trilho.setPrefSize(40, 22);
            trilho.setMaxSize(40, 22);
            getChildren().addAll(trilho, bola);
            setMaxSize(40, 22);
            setMinSize(40, 22);
            setCursor(javafx.scene.Cursor.HAND);
            ligado.addListener((o, a, b) -> desenhar());
            ligado.set(inicial);
            desenhar();
            setOnMouseClicked(e -> ligado.set(!ligado.get()));
        }

        private void desenhar() {
            boolean on = ligado.get();
            pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), on);
            StackPane.setAlignment(bola, on ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            StackPane.setMargin(bola, new javafx.geometry.Insets(0, 3, 0, 3));
        }
    }
}
