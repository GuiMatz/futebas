package br.futebas.ui.telas;

import br.futebas.competicao.FaseExec;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Mundo;
import br.futebas.partida.Concentracao;
import br.futebas.partida.Estilo;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.JogoParalelo;
import br.futebas.partida.Lance;
import br.futebas.partida.Marcacao;
import br.futebas.partida.ResultadoPartida;
import br.futebas.ui.App;
import br.futebas.ui.Opcoes;
import br.futebas.ui.Sessao;
import br.futebas.ui.Tela;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoPartida;
import br.futebas.ui.modelo.AcompanhamentoPartida.Atleta;
import br.futebas.ui.modelo.AcompanhamentoPartida.Estado;
import br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade;
import br.futebas.ui.modelo.Fluxo;
import br.futebas.ui.modelo.TabelaAoVivo;
import br.futebas.ui.modelo.Textos;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A partida ao vivo, no desenho do esboço: faixa do alto com a rodada (ou fase) e a logo do
 * campeonato; à esquerda, o jogo acompanhado (placar grande com os escudos, acontecimentos do
 * mandante à esquerda e do visitante à direita, "Fim de jogo"); no meio, JOGOS com todos os jogos
 * da rodada ao vivo (clicar num deles passa a acompanhá-lo); à direita, a TABELA mudando a cada
 * gol, com setas e zonas. Tática e trocas numa camada por cima (T, intervalo, lesão, expulsão), no
 * desenho da Escalação ({@link TrocasAoVivo}: campo com fotos, energia e banco). A coluna do meio
 * alterna entre JOGOS e MEU TIME (a energia de cada jogador ao vivo). Gol: bola, "GOL DO TIME!!" e
 * o autor/assistência embaixo, sem caixa.
 */
public final class TelaPartida implements Tela {

    private final Sessao sessao;
    private final BorderPane raiz = new BorderPane();
    // faixa da rodada
    private final Label rodadaTitulo = Ui.rotulo("", "rodada-titulo");
    private final Label rodadaSub = Ui.rotulo("", "rodada-sub");
    private final StackPane logoComp = new StackPane();
    // jogo acompanhado
    private final StackPane escudoM = new StackPane(), escudoV = new StackPane();
    private final Label nomeM = Ui.rotulo("", "placar-time"), nomeV = Ui.rotulo("", "placar-time");
    private final Label golsM = Ui.rotulo("0", "placar-gols"), golsV = Ui.rotulo("0", "placar-gols");
    private final Label relogio = Ui.rotulo("0'", "placar-relogio");
    private final Region trilhaCheia = new Region();
    private final VBox narracao = new VBox();
    private final ScrollPane rolagem = new ScrollPane(narracao);
    private final VBox narracaoOutro = new VBox();
    private final ScrollPane rolagemOutro = new ScrollPane(narracaoOutro);
    private final StackPane areaEventos = new StackPane();
    private final Label fimDeJogo = Ui.rotulo("FIM DE JOGO", "fim-de-jogo");
    private final VBox pe = new VBox(8);
    private String cabecalhoDe = "";
    // jogos e tabela
    private final VBox jogosLista = new VBox(4);
    private final VBox tabelaCorpo = new VBox(8);
    private final Label tituloTabela = Ui.rotulo("TABELA", "coluna-titulo");
    private final List<LinhaJogo> linhas = new ArrayList<>();
    private LinhaJogo minhaLinha;
    private int seguido = -1;
    private int eventosOutroMostrados = -1;
    private TabelaAoVivo tabelaVivo;
    private String assinaturaTabela = "";
    // rodapé
    private final List<ToggleButton> botoesVelocidade = new ArrayList<>();
    private final Button pausar = Ui.botaoTecla("Pausar", "P", this::alternarPausa, "rodape-botao");
    /** Simula o resto do jogo na hora (era "Ir para o fim"). */
    private final Button pular = Ui.botaoTecla("SIMULAR ATÉ O FIM", "Esc", this::pular, "rodape-botao", "rodape-forte");
    private final Button continuar = Ui.botaoTecla("Continuar", "Enter", this::continuar, "rodape-botao", "primario");
    private Button botaoTatica;

    // camada do técnico (intervalo, expulsão, lesão, tática a qualquer momento)
    private final StackPane camadas = new StackPane();
    private final StackPane sobreposicao = new StackPane();
    private final HBox taticaPainel = new HBox(24);
    private TrocasAoVivo trocas;
    /** Último pedido de pausa (intervalo, lesão, expulsão) que já abriu a tela de trocas. */
    private AcompanhamentoPartida.Pedido pedidoAtendido;
    // suspense do pênalti (requisito): letreiro no jogo, painel na disputa e a fila que segura a narração
    /** O suspense do pênalti em andamento na narração (SIMULAR ATÉ O FIM pula). */
    private SuspensePenalti.Toque suspenseAtual;
    /** Gol de pênalti ainda no suspense: o placar só conta depois do "GOL!" na narração (mandante, visitante). */
    private int[] golSegurado;
    /** "minuto#lado" do último "PÊNALTI para o X!" já narrado (o do técnico vem antes, na escolha do batedor). */
    private String penaltiMarcado;
    /** A disputa de pênaltis na narração (cobrança a cobrança); SIMULAR ATÉ O FIM mostra o resto na hora. */
    private SuspensePenalti.Toque disputaToque;
    private boolean disputaAndando;
    /** As bolinhas da disputa de pênaltis no placar (mandante, visitante): só aparecem na disputa. */
    private final SuspensePenalti.Bolinhas[] bolas = {new SuspensePenalti.Bolinhas(7), new SuspensePenalti.Bolinhas(7)};
    private boolean bolasAtivas;
    /** A escolha da ordem dos batedores aberta (null = fechada). */
    private OrdemPenaltis ordemAberta;
    private final java.util.ArrayDeque<Object[]> fila = new java.util.ArrayDeque<>();
    private boolean emSuspense;
    private Runnable aposFila;
    // coluna do meio: JOGOS ou MEU TIME (energia ao vivo)
    private final VBox meuTimeLista = new VBox(3);
    private final StackPane colunaMeio = new StackPane();
    private boolean mostrandoMeuTime;
    private String assinaturaMeuTime = "";
    /** Nomes dos dois times do jogo narrado (para "GOL DO TIME!!"). */
    private final String[] nomesTimes = {"", ""};
    /** Banco de frases (chances e título do gol), sem repetir em seguida. */
    private final br.futebas.partida.Frases frases = new br.futebas.partida.Frases(System.nanoTime());
    private final Label motivoPainel = Ui.rotulo("", "camada-motivo");
    private final Label tituloPainel = Ui.rotulo("", "titulo-tela");
    private boolean painelAberto, pulando;
    private boolean entreJogos;
    private Estado proximoEstado;
    private ResultadoPartida ultimoResultado;
    private KingsAoVivo kings;
    private VBox caixaPainel;
    private ResumoAoVivo resumo;
    private final List<OutroJogo> outros = new ArrayList<>();
    /** Opções: placares dos outros jogos e tabela mudando ao vivo (desligado = só no apito final). */
    private final boolean outrosAoVivo = App.automatico || Opcoes.outrosJogosAoVivo();
    private final boolean tabelaAoVivo = App.automatico || Opcoes.tabelaAoVivo();

    /** Um jogo da mesma rodada, jogado antes pelo motor e revelado conforme o minuto do seu jogo. */
    private static final class OutroJogo {
        final JogoParalelo j;

        OutroJogo(JogoParalelo j) {
            this.j = j;
        }

        int[] placar(int min, boolean fim) {
            return fim ? new int[] {j.golsMandante(), j.golsVisitante()} : j.placarAte(min);
        }

        int eventosAte(int min, boolean fim) {
            int n = 0;
            for (EventoJogo e : j.eventos()) if ((fim || e.minuto() <= min) && visivel(e)) n++;
            return n;
        }
    }

    /** Uma linha da coluna JOGOS (duas linhas: mandante e visitante, com o minuto à direita). */
    private final class LinhaJogo {
        final int indice; // -1 = o seu jogo; -2 = resultado fixo
        final String idM, idV;
        final Label gm = Ui.rotulo("", "jogo-gols"), gv = Ui.rotulo("", "jogo-gols");
        final Label min = Ui.rotulo("", "jogo-minuto");
        final HBox raiz;
        String ultimoPlacar = "";

        LinhaJogo(int indice, String idM, String idV) {
            this.indice = indice;
            this.idM = idM;
            this.idV = idV;
            Mundo m = sessao.jogo.mundo();
            VBox times = new VBox(3, lado(m, idM, gm), lado(m, idV, gv));
            HBox.setHgrow(times, Priority.ALWAYS);
            min.setMinWidth(40);
            min.setAlignment(Pos.CENTER_RIGHT);
            raiz = new HBox(10, times, min);
            raiz.setAlignment(Pos.CENTER_LEFT);
            raiz.getStyleClass().add("jogo-linha");
            if (indice >= -1) {
                raiz.setCursor(javafx.scene.Cursor.HAND);
                raiz.setOnMouseClicked(e -> seguir(indice));
            }
        }

        private HBox lado(Mundo m, String id, Label gols) {
            Label n = Ui.rotulo(Textos.nomeClube(m, id), "jogo-nome");
            n.setMinWidth(0);
            n.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(n, Priority.ALWAYS);
            HBox h = new HBox(8, Imagens.escudo(id, 18), n, gols);
            h.setAlignment(Pos.CENTER_LEFT);
            return h;
        }

        void mostrar(int[] p, String minuto) {
            String s = p == null ? "" : p[0] + "-" + p[1];
            gm.setText(p == null ? "" : String.valueOf(p[0]));
            gv.setText(p == null ? "" : String.valueOf(p[1]));
            min.setText(minuto);
            if (!ultimoPlacar.isEmpty() && !s.equals(ultimoPlacar) && !pulando && !terminou) piscar();
            ultimoPlacar = s;
        }

        private void piscar() {
            raiz.getStyleClass().add("jogo-gol");
            javafx.animation.PauseTransition t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(2600));
            t.setOnFinished(x -> raiz.getStyleClass().remove("jogo-gol"));
            t.play();
        }
    }

    private AcompanhamentoPartida acomp;
    private Estado ultimo;
    private String assinaturaTatica = "";
    private int exibidos;
    private Fluxo.Rodada rodada;
    private boolean terminou;

    public TelaPartida(Sessao sessao) {
        this.sessao = sessao;
        raiz.setTop(faixaRodada());

        for (ScrollPane sp : List.of(rolagem, rolagemOutro)) {
            sp.setFitToWidth(true);
            sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            sp.getStyleClass().add("narracao-fundo");
        }
        narracao.getStyleClass().add("narracao-fundo");
        narracaoOutro.getStyleClass().add("narracao-fundo");
        narracao.heightProperty().addListener((o, a, b) -> rolagem.setVvalue(1.0));
        narracaoOutro.heightProperty().addListener((o, a, b) -> rolagemOutro.setVvalue(1.0));
        areaEventos.getChildren().setAll(rolagem);
        VBox.setVgrow(areaEventos, Priority.ALWAYS);
        fimDeJogo.setVisible(false);
        fimDeJogo.setManaged(false);
        fimDeJogo.setMaxWidth(Double.MAX_VALUE);
        pe.getChildren().add(fimDeJogo);
        VBox bloco = new VBox(0, placar(), areaEventos, pe);
        bloco.getStyleClass().add("bloco-jogo");
        HBox.setHgrow(bloco, Priority.ALWAYS);
        bloco.setMinWidth(420);

        ScrollPane rolJogos = Ui.rolar(jogosLista);
        rolJogos.setFitToWidth(true);
        ScrollPane rolMeuTime = Ui.rolar(meuTimeLista);
        rolMeuTime.setFitToWidth(true);
        colunaMeio.getChildren().setAll(rolJogos);
        VBox.setVgrow(colunaMeio, Priority.ALWAYS);
        HBox abas = Ui.segmentos(List.of("JOGOS", "MEU TIME"), x -> x, "JOGOS", x -> {
            mostrandoMeuTime = "MEU TIME".equals(x);
            colunaMeio.getChildren().setAll(mostrandoMeuTime ? rolMeuTime : rolJogos);
            assinaturaMeuTime = "";
            if (ultimo != null) atualizarMeuTime(ultimo);
        });
        VBox colJogos = new VBox(8, abas, colunaMeio);
        colJogos.getStyleClass().add("coluna-partida");
        colJogos.setPrefWidth(290);
        colJogos.setMinWidth(240);

        ScrollPane rolTabela = Ui.rolar(tabelaCorpo);
        rolTabela.setFitToWidth(true);
        VBox.setVgrow(rolTabela, Priority.ALWAYS);
        VBox colTabela = new VBox(8, tituloTabela, rolTabela);
        colTabela.getStyleClass().add("coluna-partida");
        colTabela.setPrefWidth(340);
        colTabela.setMinWidth(300);

        HBox centro = new HBox(12, bloco, colJogos, colTabela);
        centro.setStyle("-fx-padding: 12 16 12 16;");
        raiz.setCenter(centro);

        // rodapé (requisito: "melhore esses botões"): velocidade em pílula, Pausar com ícone,
        // Tática e trocas, SIMULAR ATÉ O FIM e Continuar; botões de 38 px, sem selos de tecla (os atalhos valem)
        ToggleGroup g = new ToggleGroup();
        HBox vel = new HBox();
        vel.getStyleClass().add("vel-pilula");
        vel.setAlignment(Pos.CENTER_LEFT);
        for (Velocidade v : Velocidade.visiveis()) {
            ToggleButton b = new ToggleButton(v.nome);
            b.getStyleClass().add("vel-opcao");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setToggleGroup(g);
            b.setFocusTraversable(false);
            b.setOnAction(e -> {
                if (!b.isSelected()) b.setSelected(true);
                velocidade(v);
            });
            botoesVelocidade.add(b);
            vel.getChildren().add(b);
        }
        vel.setMinWidth(Region.USE_PREF_SIZE);
        continuar.setDisable(true);
        botaoTatica = Ui.botaoTecla("Tática e trocas", "T", () -> abrirPainel("Tática e trocas", ""), "rodape-botao");
        botaoTatica.setGraphic(Ui.rotulo("⇄", "icone-rodape-texto"));
        pular.setGraphic(iconeAvancar());
        mostrarPausado(false);
        for (Button b : List.of(pausar, botaoTatica, pular, continuar)) b.setMinWidth(Region.USE_PREF_SIZE);
        Label rotVel = Ui.rotulo("Velocidade", "rotulo-rodape");
        rotVel.setMinWidth(Region.USE_PREF_SIZE);
        HBox rodape = Ui.rodape(rotVel, vel, pausar, botaoTatica, Ui.espaco(), pular, continuar);
        rodape.getStyleClass().add("rodape-jogo");
        raiz.setBottom(rodape);
        marcarVelocidade(velocidadeInicial());
        trocas = new TrocasAoVivo(sessao, () -> acomp);
        montarSobreposicao();
        kings = new KingsAoVivo(sessao.jogo.mundo());
        kings.sessao(sessao);
        camadas.getChildren().setAll(raiz, sobreposicao, kings);
        mostrarBolinhas(false);
    }

    private static Velocidade velocidadeInicial() {
        return App.automatico ? Velocidade.RAPIDA : Opcoes.velocidade();
    }

    private Node faixaRodada() {
        VBox txt = new VBox(2, rodadaTitulo, rodadaSub);
        txt.setAlignment(Pos.CENTER_LEFT);
        logoComp.setMinSize(64, 64);
        HBox h = new HBox(16, txt, Ui.espaco(), logoComp);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("faixa-rodada");
        return h;
    }

    private Node placar() {
        nomeM.setMinWidth(0);
        nomeV.setMinWidth(0);
        nomeM.setWrapText(true);
        nomeV.setWrapText(true);
        nomeM.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        nomeV.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        VBox ladoM = new VBox(6, escudoM, nomeM, bolas[0]), ladoV = new VBox(6, escudoV, nomeV, bolas[1]);
        for (VBox v : List.of(ladoM, ladoV)) {
            v.setAlignment(Pos.CENTER);
            v.setPrefWidth(200);
            v.setMinWidth(110);
            HBox.setHgrow(v, Priority.ALWAYS);
        }
        Label traco = Ui.rotulo("–", "placar-gols");
        HBox numeros = new HBox(14, golsM, traco, golsV);
        numeros.setAlignment(Pos.CENTER);
        VBox meio = new VBox(4, numeros, relogio);
        meio.setAlignment(Pos.CENTER);
        meio.setMinWidth(170);
        HBox linha = new HBox(10, ladoM, meio, ladoV);
        linha.setAlignment(Pos.CENTER);

        Region trilha = new Region();
        trilha.getStyleClass().add("placar-trilha");
        trilhaCheia.getStyleClass().add("placar-trilha-cheia");
        trilhaCheia.setPrefWidth(0);
        trilhaCheia.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane barra = new StackPane(trilha, trilhaCheia);
        barra.setAlignment(Pos.CENTER_LEFT);
        trilha.setMaxWidth(Double.MAX_VALUE);
        VBox v = new VBox(10, linha, barra);
        v.getStyleClass().addAll("placar", "placar-cartao");
        return v;
    }

    @Override
    public Parent raiz() { return camadas; }

    // ------------------------------------------------------------------ painel do técnico (sobreposto)
    private void montarSobreposicao() {
        sobreposicao.getStyleClass().add("sobreposicao");
        sobreposicao.setVisible(false);
        taticaPainel.setAlignment(Pos.CENTER_LEFT);
        // rodapé da camada: só Voltar e Confirmar, à direita (regra do jogo)
        Button voltar = Ui.botaoTecla("Voltar", "Esc", this::fecharPainel);
        trocas.aoConfirmar(this::fecharPainel);
        Label ajuda = Ui.info("Arraste um reserva para a vaga (ou clique em quem sai e em quem entra); dois em campo trocam de posição. Nada muda até Confirmar trocas.");
        // requisito: a camada quase do tamanho da janela, com a tabela ao vivo e os números do jogo ao lado
        resumo = new ResumoAoVivo(sessao);
        caixaPainel = montarCaixaTrocas(sobreposicao, cabecalhoCamada(tituloPainel, fotoPainel, motivoPainel, ajuda), Ui.painel(null, taticaPainel),
                trocas.raiz(), resumo.raiz(), Ui.linha(10, Ui.espaco(), voltar, trocas.botaoSubstituir()), trocas::tamanho);
    }

    /**
     * A caixa da camada de trocas (futebol, multi-treinador e Kings): cabeçalho, tática e o campo com o
     * banco à esquerda, o resumo ao vivo (números e tabela) à direita e o rodapé. Ocupa quase a janela
     * toda ({@code .sobreposicao-trocas}: margem pequena) e o campo cresce com a altura da janela.
     */
    static VBox montarCaixaTrocas(StackPane camada, Node cabecalho, Node tatica, Node trocas, Node resumo, Node rodape,
                                  java.util.function.DoubleConsumer tamanhoCampo) {
        VBox esquerda = tatica == null ? new VBox(12, trocas) : new VBox(12, tatica, trocas);
        esquerda.setMinWidth(Region.USE_PREF_SIZE);
        HBox corpo = new HBox(16, esquerda, resumo);
        HBox.setHgrow(esquerda, Priority.SOMETIMES);
        HBox.setHgrow(resumo, Priority.ALWAYS);
        VBox.setVgrow(corpo, Priority.ALWAYS);
        VBox caixa = new VBox(12, cabecalho, corpo, rodape);
        caixa.getStyleClass().add("caixa-sobreposta");
        caixa.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        camada.getStyleClass().add("sobreposicao-trocas");
        camada.getChildren().add(caixa);
        // o campo: a altura que sobra na janela depois das margens, do cabeçalho, da tática e do rodapé
        Runnable ajustar = () -> {
            if (camada.getHeight() <= 0) return;
            javafx.geometry.Insets mc = camada.getInsets(), mx = caixa.getInsets();
            double fixo = mc.getTop() + mc.getBottom() + mx.getTop() + mx.getBottom() + altura(cabecalho) + altura(rodape)
                    + 2 * caixa.getSpacing() + (tatica == null ? 0 : altura(tatica) + esquerda.getSpacing());
            tamanhoCampo.accept(camada.getHeight() - fixo - 2);
        };
        camada.heightProperty().addListener((o, a, b) -> ajustar.run());
        camada.visibleProperty().addListener((o, a, b) -> {
            if (b) {
                ajustar.run();
                Platform.runLater(ajustar); // depois do CSS e do layout (alturas reais)
            }
        });
        return caixa;
    }

    /**
     * Cabeçalho da camada de trocas: título, a foto de quem motivou (lesionado/expulso, se houver) e o
     * nome, lado a lado, sem encolher um sobre o outro (bug corrigido: "Lesão" encavalado com o nome).
     */
    static HBox cabecalhoCamada(Label titulo, StackPane foto, Label motivo, Node ajuda) {
        titulo.setMinWidth(Region.USE_PREF_SIZE);
        motivo.setMinWidth(Region.USE_PREF_SIZE);
        foto.getStyleClass().add("camada-titulo-foto");
        foto.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        foto.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        HBox h = new HBox(10, titulo, foto, motivo, Ui.espaco(), ajuda);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    /** A foto ao lado do título (null = sem foto). */
    private void fotoNoTitulo(Integer jogadorId) {
        fotoPainel.getChildren().clear();
        if (jogadorId != null) fotoPainel.getChildren().add(trocas.fotoDe(jogadorId, 30, 30));
        fotoPainel.setVisible(jogadorId != null);
        fotoPainel.setManaged(jogadorId != null);
    }

    private final StackPane fotoPainel = new StackPane();

    /** Pausa o jogo e abre a tática com as trocas. */
    private void abrirPainel(String titulo, String motivo) {
        abrirPainel(titulo, motivo, null);
    }

    private void abrirPainel(String titulo, String motivo, Integer jogadorId) {
        if (terminou || ultimo == null || ultimo.meuLado() < 0 || painelAberto) return;
        painelAberto = true;
        if (acomp != null) acomp.pausar(true);
        mostrarPausado(true);
        tituloPainel.setText(titulo);
        motivoPainel.setText(motivo);
        motivoPainel.setVisible(motivo != null && !motivo.isEmpty());
        motivoPainel.setManaged(motivoPainel.isVisible());
        fotoNoTitulo(jogadorId);
        trocas.limpar();
        assinaturaTatica = "";
        atualizarLateral(ultimo);
        atualizarResumo();
        sobreposicao.setVisible(true);
        if (App.automatico) {
            javafx.animation.PauseTransition t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1400));
            t.setOnFinished(x -> fecharPainel());
            t.play();
        }
    }

    private static double altura(Node n) {
        return n instanceof Region r && r.getHeight() > 0 ? r.getHeight() : n.prefHeight(-1);
    }

    /** Os números do jogo e a tabela ao vivo na camada de trocas (no minuto de agora). */
    private void atualizarResumo() {
        if (resumo == null || ultimo == null) return;
        String meu = sessao.jogo.meuClube() == null ? null : sessao.jogo.meuClube().id;
        resumo.mostrar(ultimo.mandanteId(), ultimo.visitanteId(), acomp == null ? null : acomp.estatisticas(), ultimo.relogio(),
                placaresAgora(outrosAoVivo || outrosNoFim()), meu);
    }

    private void fecharPainel() {
        if (!painelAberto || batedorAberto) return;
        // trocas preparadas e não confirmadas: confirmar ou descartar antes de voltar ao jogo
        if (trocas.temPendentes()) {
            if (App.automatico || pulando || terminou) trocas.confirmar();
            else {
                int r = Ui.modal("Trocas não confirmadas", Ui.rotulo("Há " + trocas.pendentes().size()
                        + " troca(s) preparada(s). Confirmar antes de voltar ao jogo?", "texto-medio"), "Confirmar", "Descartar");
                if (r == 0) trocas.confirmar();
                else if (r == 1) trocas.descartar();
                else return;
            }
        }
        painelAberto = false;
        sobreposicao.setVisible(false);
        if (acomp != null && !terminou) acomp.pausar(false);
        mostrarPausado(false);
        trocas.limpar();
        assinaturaTatica = "";
    }

    public boolean painelAberto() { return painelAberto; }

    /** A escolha do batedor do pênalti está aberta (ferramentas de captura). */
    public boolean batedorAberto() { return batedorAberto; }

    /**
     * Ferramentas de captura ({@code FotoIntervalo}): abre a camada de trocas como numa lesão do 1º
     * jogador em campo do seu time (título, foto e nome), sem lesão de verdade no motor.
     */
    public void abrirComoLesao() {
        if (ultimo == null || ultimo.emCampo().isEmpty()) return;
        Atleta a = ultimo.emCampo().get(Math.min(3, ultimo.emCampo().size() - 1));
        abrirPainel("Lesão", a.nome(), a.id());
    }

    @Override
    public void aoMostrar() {
        if (acomp != null) return; // voltando de "entre jogos": o jogo já está em andamento
        Clube c = sessao.jogo.meuClube();
        Partida p = sessao.fluxo.proximoJogo();
        if (p != null) {
            preencherCabecalho(p.mandante, p.visitante);
            Torneio t = sessao.fluxo.torneioDe(p);
            preencherFaixa(t, p, TabelaAoVivo.de(t, p));
            relogio.setText(p.semana == sessao.jogo.semana() ? "0'" : "Semana " + p.semana);
        } else {
            relogio.setText("—");
            rodadaTitulo.setText("");
        }
        String meuId = c != null ? c.id : null;
        acomp = new AcompanhamentoPartida(meuId, velocidadeInicial(), e -> Platform.runLater(() -> atualizar(e)));
        acomp.esperarTecnico(true);
        // disputa de pênaltis: a ordem dos batedores (depois da narração que falta do jogo)
        acomp.aoPedirOrdem(po -> Platform.runLater(() -> quandoFilaVazia(() -> abrirOrdem(po))));
        if (!App.automatico) acomp.configurarPausas(Opcoes.taticaNoIntervalo(), Opcoes.pausarLesao(), Opcoes.pausarExpulsao(),
                Opcoes.escolherBatedor(), Opcoes.pausarGolMeu(), Opcoes.pausarGolSofrido());
        if (sessao.jogo.mundo().tecnico != null && sessao.jogo.mundo().tecnico.kingsTimeId != null) sessao.jogo.definirAoVivoKings(kings);
        java.util.concurrent.Callable<Fluxo.Rodada> rodar = () -> {
            try {
                Fluxo.Rodada r = sessao.fluxo.jogarProximaRodada(acomp);
                // virada do ano: as imagens da tela do fim de temporada carregam aqui, fora da thread da tela
                if (r.fimDeTemporada() != null) TelaFimTemporada.preaquecer(sessao, r.fimDeTemporada());
                return r;
            } finally {
                sessao.jogo.definirAoVivoKings(null);
            }
        };
        if (App.atual() != null) App.atual().trabalhar(null, rodar, this::fim);
        else {
            // sem a janela do jogo (ferramentas de captura): o motor numa thread própria
            Thread t = new Thread(() -> {
                try {
                    Fluxo.Rodada r = rodar.call();
                    Platform.runLater(() -> fim(r));
                } catch (Exception x) {
                    x.printStackTrace();
                }
            }, "partida-sem-app");
            t.setDaemon(true);
            t.start();
        }
    }

    private void preencherFaixa(Torneio t, Partida p, TabelaAoVivo tv) {
        logoComp.getChildren().clear();
        if (t == null) {
            rodadaTitulo.setText("AMISTOSO");
            rodadaSub.setText("");
            return;
        }
        FaseExec f = p.fase >= 0 && p.fase < t.fases.size() ? t.fases.get(p.fase) : null;
        String fase = Textos.fase(t, p);
        if (tv != null) {
            rodadaTitulo.setText("RODADA – " + tv.rodada);
            String sub = Textos.curto(t.nome);
            if (f != null && f.numGrupos > 1) sub += "  ·  Grupo " + (char) ('A' + Math.max(0, p.grupo));
            else if (t.fases.size() > 1 && !fase.isEmpty()) sub += "  ·  " + fase;
            rodadaSub.setText(sub);
        } else {
            rodadaTitulo.setText(fase.isEmpty() ? Textos.curto(t.nome).toUpperCase(java.util.Locale.ROOT) : fase.toUpperCase(java.util.Locale.ROOT));
            rodadaSub.setText(Textos.curto(t.nome));
        }
        logoComp.getChildren().add(Imagens.trofeu(t.id, t.ambito, t.pais, 64));
    }

    /** Monta a coluna JOGOS e a tabela quando começa um jogo seu (o motor já mandou os outros jogos). */
    private void prepararRodada(Estado e) {
        outros.clear();
        linhas.clear();
        jogosLista.getChildren().clear();
        seguido = -1;
        eventosOutroMostrados = -1;
        areaEventos.getChildren().setAll(rolagem);
        minhaLinha = new LinhaJogo(-1, e.mandanteId(), e.visitanteId());
        minhaLinha.raiz.getStyleClass().addAll("meu", "seguido");
        linhas.add(minhaLinha);
        jogosLista.getChildren().add(minhaLinha.raiz);
        List<JogoParalelo> js = e.paralelos() == null ? List.of() : e.paralelos();
        for (int i = 0; i < js.size(); i++) {
            outros.add(new OutroJogo(js.get(i)));
            LinhaJogo l = new LinhaJogo(i, js.get(i).mandanteId(), js.get(i).visitanteId());
            linhas.add(l);
            jogosLista.getChildren().add(l.raiz);
        }
        paralelosMontados = js;
        Partida p = ResumoAoVivo.acharPartida(sessao.jogo, e.mandanteId(), e.visitanteId());
        Torneio t = p == null ? null : sessao.fluxo.torneioDe(p);
        tabelaVivo = TabelaAoVivo.de(t, p);
        assinaturaTabela = "";
        if (p != null) preencherFaixa(t, p, tabelaVivo);
        tabelaCorpo.getChildren().clear();
        if (tabelaVivo == null && t != null && p != null && p.fase >= 0 && p.fase < t.fases.size()) {
            FaseExec f = t.fases.get(p.fase);
            tituloTabela.setText(f.nome == null ? "CHAVEAMENTO" : f.nome.toUpperCase(java.util.Locale.ROOT));
            Mundo m = sessao.jogo.mundo();
            try {
                tabelaCorpo.getChildren().add(PainelTabelas.chaveamento(t, f, id -> Textos.nomeClube(m, id)));
            } catch (RuntimeException ex) {
                // sem chaveamento desenhável
            }
        } else {
            tituloTabela.setText("TABELA");
        }
    }

    private List<JogoParalelo> paralelosMontados;

    private void preencherCabecalho(String idM, String idV) {
        String chave = idM + "|" + idV;
        if (chave.equals(cabecalhoDe)) return;
        cabecalhoDe = chave;
        Mundo m = sessao.jogo.mundo();
        nomeM.setText(Textos.nomeClube(m, idM));
        nomeV.setText(Textos.nomeClube(m, idV));
        escudoM.getChildren().setAll(Ui.escudo(idM, 84));
        escudoV.getChildren().setAll(Ui.escudo(idV, 84));
    }

    // ------------------------------------------------------------------ ao vivo
    private void atualizar(Estado e) {
        if (e.novoJogo() && ultimo != null && acomp != null && acomp.aguardandoProximo()) {
            // outro jogo seu na mesma semana: primeiro volta para a central
            entreJogos = true;
            proximoEstado = e;
            fecharPainel();
            relogio.setText("Fim");
            relogio.getStyleClass().add("parado");
            pausar.setDisable(true);
            pular.setDisable(true);
            continuar.setDisable(false);
            continuar.setText("Voltar à central");
            mostrarFim(true);
            if (ultimoResultado != null) narracao.getChildren().add(painelNumeros(ultimoResultado, false));
            pe.getChildren().add(Ui.aviso("Próximo jogo da semana: " + e.mandante() + " x " + e.visitante(), "aviso"));
            return;
        }
        if (e.terminado() && e.resultado() != null) ultimoResultado = e.resultado();
        if (e.novoJogo()) {
            if (ultimo != null) separador(e.mandante() + " x " + e.visitante());
            mostrarBolinhas(false);
            exibidos = 0;
            trocas.limpar();
            assinaturaTatica = "";
            assinaturaMeuTime = "";
            prepararRodada(e);
        } else if (e.paralelos() != null && e.paralelos() != paralelosMontados && ultimo != null) {
            prepararRodada(e);
        }
        ultimo = e;
        nomesTimes[0] = e.mandante();
        nomesTimes[1] = e.visitante();
        // pausa pedida pelo motor (gol com a opção ligada): o botão acompanha
        if (!painelAberto && acomp != null && !terminou) mostrarPausado(acomp.pausado());
        for (Lance l : e.novosLances()) enfileirar(l, e.meuLado());
        exibidos += e.novosLances().size();
        if (painelAberto) {
            atualizarLateral(e);
            atualizarResumo();
        }
        atualizarMeuTime(e);
        atualizarVivo();
        AcompanhamentoPartida.Pedido ped = e.pedido();
        if (ped != null && !pulando && !e.terminado()) {
            if (ped.candidatos() != null) abrirBatedor(ped.candidatos());
            else if (!painelAberto && ped != pedidoAtendido) {
                // o mesmo pedido volta a ser publicado enquanto o motor espera (ordens aplicadas): abre uma vez só
                pedidoAtendido = ped;
                String quem = ped.jogador() == null ? "" : ped.jogador();
                switch (ped.motivo()) {
                    case INTERVALO -> abrirPainel("Intervalo", e.mandante() + " " + e.golsMandante() + " x " + e.golsVisitante() + " " + e.visitante());
                    case EXPULSAO -> abrirPainel("Expulsão", quem, ped.jogadorId());
                    case LESAO -> abrirPainel("Lesão", quem, ped.jogadorId());
                    default -> { }
                }
            }
        }
    }

    /** Minuto que os outros jogos mostram (acompanham o seu, sem passar dos 90). */
    private int minutoOutros() {
        if (terminou || ultimo == null) return terminou ? 90 : 0;
        return ultimo.intervalo() ? 45 : Math.min(90, ultimo.minuto());
    }

    private boolean outrosNoFim() {
        return terminou || (ultimo != null && ultimo.terminado());
    }

    /** Atualiza o jogo acompanhado, a coluna JOGOS e a tabela. */
    private void atualizarVivo() {
        Estado e = ultimo;
        int min = minutoOutros();
        boolean fimOutros = outrosNoFim();
        String relOutros = fimOutros ? "Fim" : ultimo != null && ultimo.intervalo() ? "Int." : min + "'";
        int[] seg = golSegurado == null ? new int[2] : golSegurado;
        if (minhaLinha != null && e != null) minhaLinha.mostrar(new int[] {e.golsMandante() - seg[0], e.golsVisitante() - seg[1]}, terminou ? "Fim" : e.relogio());
        boolean revelar = outrosAoVivo || fimOutros;
        for (int i = 0; i < outros.size(); i++) linhas.get(i + 1).mostrar(revelar ? outros.get(i).placar(min, fimOutros) : null, revelar ? relOutros : "");
        // o jogo acompanhado
        if (seguido < 0 || seguido >= outros.size()) {
            if (e != null) {
                preencherCabecalho(e.mandanteId(), e.visitanteId());
                golsM.setText(String.valueOf(e.golsMandante() - seg[0]));
                golsV.setText(String.valueOf(e.golsVisitante() - seg[1]));
                relogio.setText(terminou ? relogio.getText() : e.relogio());
                double total = e.minuto() > 90 ? 120 : 90;
                ajustarTrilha(Math.min(1, (e.terminado() ? total : e.minuto()) / total));
                relogio.getStyleClass().remove("parado");
                if (e.intervalo() || e.terminado() || terminou || (acomp != null && acomp.pausado())) relogio.getStyleClass().add("parado");
                mostrarFim(e.terminado() || terminou);
            }
        } else {
            OutroJogo o = outros.get(seguido);
            preencherCabecalho(o.j.mandanteId(), o.j.visitanteId());
            int[] p = o.placar(min, fimOutros);
            golsM.setText(revelar ? String.valueOf(p[0]) : "–");
            golsV.setText(revelar ? String.valueOf(p[1]) : "–");
            relogio.setText(fimOutros ? "Fim" : relOutros);
            ajustarTrilha(fimOutros ? 1 : min / 90.0);
            int minVisto = revelar ? min : 0;
            int n = (revelar ? o.eventosAte(min, fimOutros) : 0) + (fimOutros ? 10000 : 0);
            if (n != eventosOutroMostrados) {
                eventosOutroMostrados = n;
                narracaoOutro.getChildren().clear();
                narracaoOutro.getChildren().add(linhaFase("0'", "Começa o jogo"));
                for (EventoJogo ev : o.j.eventos()) {
                    if (!fimOutros && ev.minuto() > minVisto) continue;
                    if (!visivel(ev)) continue;
                    String rel = ev.acrescimo() > 0 ? ev.minuto() + "+" + ev.acrescimo() + "'" : ev.minuto() + "'";
                    if (ev.gol()) {
                        String time = Textos.nomeClube(sessao.jogo.mundo(), ev.lado() == 0 ? o.j.mandanteId() : o.j.visitanteId());
                        String autor = ev.jogador() == null ? null : ev.jogador() + ("gol_contra".equals(ev.tipo()) ? " (contra)" : "penalti_gol".equals(ev.tipo()) ? " (pênalti)" : "");
                        narracaoOutro.getChildren().add(linhaGol(ev.lado() == 0, rel, time, autor, null, null));
                    } else {
                        narracaoOutro.getChildren().add(linhaEvento(ev.lado() == 0, rel, ev.jogador(), detalhe(ev), icone(ev), classe(ev)));
                    }
                }
                if (fimOutros) narracaoOutro.getChildren().add(linhaFase("90'", "Fim de jogo  " + o.j.golsMandante() + " x " + o.j.golsVisitante()));
            }
            mostrarFim(fimOutros);
        }
        atualizarTabela();
    }

    private void ajustarTrilha(double fracao) {
        trilhaCheia.setPrefWidth(trilhaCheia.getParent() == null ? 0 : ((Region) trilhaCheia.getParent()).getWidth() * fracao);
    }

    private void mostrarFim(boolean fim) {
        fimDeJogo.setVisible(fim);
        fimDeJogo.setManaged(fim);
    }

    /** Passa a acompanhar outro jogo da rodada (-1 = o seu). */
    private void seguir(int i) {
        seguido = i;
        eventosOutroMostrados = -1;
        for (LinhaJogo l : linhas) {
            l.raiz.getStyleClass().remove("seguido");
            if (l.indice == i) l.raiz.getStyleClass().add("seguido");
        }
        areaEventos.getChildren().setAll(i < 0 ? rolagem : rolagemOutro);
        atualizarVivo();
    }

    private static boolean visivel(EventoJogo e) {
        return switch (e.tipo()) {
            case "gol", "gol_contra", "penalti_gol", "penalti_perdido", "amarelo", "vermelho", "gol_x2" -> true;
            default -> false;
        };
    }

    private static String detalhe(EventoJogo e) {
        return switch (e.tipo()) {
            case "gol_contra" -> "Gol contra";
            case "penalti_gol" -> "Gol de pênalti";
            case "penalti_perdido" -> "Pênalti perdido";
            case "amarelo" -> "Cartão amarelo";
            case "vermelho" -> "Expulso";
            default -> "Gol";
        };
    }

    private static String icone(EventoJogo e) {
        return switch (e.tipo()) {
            case "gol", "gol_contra", "penalti_gol", "gol_x2" -> "gol";
            case "amarelo" -> "cartao";
            case "vermelho" -> "vermelho";
            default -> "";
        };
    }

    private String classe(EventoJogo e) {
        if (!e.gol()) return "vermelho".equals(e.tipo()) ? "evento-vermelho" : null;
        String meu = sessao.jogo.meuClube() == null ? null : sessao.jogo.meuClube().id;
        return "evento-gol";
    }

    // ------------------------------------------------------------------ tabela ao vivo
    /**
     * Placares de agora ({mandante, visitante, gols m, gols v}): o seu jogo (depois do apito inicial)
     * e, se {@code outrosTambem}, os outros jogos da rodada no minuto do seu.
     */
    private List<Object[]> placaresAgora(boolean outrosTambem) {
        List<Object[]> placares = new ArrayList<>();
        if (ultimo == null) return placares;
        int min = minutoOutros();
        boolean fim = outrosNoFim();
        if (ultimo.minuto() > 0 || ultimo.terminado()) placares.add(new Object[] {ultimo.mandanteId(), ultimo.visitanteId(), ultimo.golsMandante(), ultimo.golsVisitante()});
        if (outrosTambem && (min > 0 || fim)) {
            for (OutroJogo o : outros) {
                int[] p = o.placar(min, fim);
                placares.add(new Object[] {o.j.mandanteId(), o.j.visitanteId(), p[0], p[1]});
            }
        }
        return placares;
    }

    private void atualizarTabela() {
        if (tabelaVivo == null || ultimo == null) return;
        boolean fim = outrosNoFim();
        // tabela ao vivo desligada: a de antes da rodada até o apito final
        boolean mexe = tabelaAoVivo || fim;
        List<Object[]> placares = mexe ? placaresAgora(outrosAoVivo || fim) : new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append(mexe).append(ultimo.golsMandante()).append('-').append(ultimo.golsVisitante()).append(ultimo.minuto() > 0);
        for (Object[] p : placares) sb.append('|').append(p[2]).append('-').append(p[3]);
        String acomp = seguido < 0 ? ultimo.mandanteId() + ultimo.visitanteId() : outros.get(seguido).j.mandanteId() + outros.get(seguido).j.visitanteId();
        sb.append('#').append(acomp).append(placares.size());
        if (sb.toString().equals(assinaturaTabela)) return;
        assinaturaTabela = sb.toString();
        List<TabelaAoVivo.Linha> ls = tabelaVivo.agora(placares);
        String meu = sessao.jogo.meuClube() == null ? null : sessao.jogo.meuClube().id;
        Set<String> jogando = new HashSet<>();
        if (seguido < 0) jogando.addAll(List.of(ultimo.mandanteId(), ultimo.visitanteId()));
        else jogando.addAll(List.of(outros.get(seguido).j.mandanteId(), outros.get(seguido).j.visitanteId()));
        tabelaCorpo.getChildren().setAll(ResumoAoVivo.gradeTabela(sessao.jogo.mundo(), tabelaVivo, ls, meu, jogando));
    }

    // ------------------------------------------------------------------ pênalti a favor: quem bate?
    private boolean batedorAberto;

    private EscolhaBatedor escolhaBatedor;

    private void abrirBatedor(List<Atleta> candidatos) {
        if (batedorAberto) return;
        batedorAberto = true;
        painelAberto = true;
        Clube c = acomp == null ? sessao.jogo.meuClube() : sessao.jogo.clube(acomp.clubeId());
        Integer oficial = c == null || c.tatica == null ? null : c.tatica.cobradorPenalti;
        EscolhaBatedor caixa = new EscolhaBatedor(c, null, candidatos, oficial, trocas::fotoDe, this::responderBatedor);
        escolhaBatedor = caixa;
        sobreposicao.getChildren().setAll(caixa);
        sobreposicao.setVisible(true);
        if (App.automatico) {
            javafx.animation.PauseTransition t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1200));
            t.setOnFinished(x -> responderBatedor(candidatos.get(0).id()));
            t.play();
        }
    }

    private void responderBatedor(Integer id) {
        if (!batedorAberto) return;
        batedorAberto = false;
        escolhaBatedor = null;
        painelAberto = false;
        sobreposicao.setVisible(false);
        sobreposicao.getChildren().setAll(caixaPainel);
        if (acomp != null) acomp.responderBatedor(id);
    }

    private void separador(String texto) {
        HBox h = new HBox(Ui.rotulo(texto));
        h.getStyleClass().add("lance-separador");
        narracao.getChildren().add(h);
    }

    /** Um passo do suspense do pênalti na narração ("vai para a bola…", "Bateu…", "DEFENDEU FULANO!"). */
    static Node linhaPasso(boolean casa, String relogio, String texto, String classe) {
        Label txt = Ui.rotulo(texto, "penalti-passo");
        if (classe != null) txt.getStyleClass().add(classe);
        txt.setMinWidth(0);
        StackPane esq = new StackPane(), dir = new StackPane();
        esq.setAlignment(Pos.CENTER_RIGHT);
        dir.setAlignment(Pos.CENTER_LEFT);
        (casa ? esq : dir).getChildren().add(txt);
        HBox.setHgrow(esq, Priority.ALWAYS);
        HBox.setHgrow(dir, Priority.ALWAYS);
        esq.setPrefWidth(300);
        dir.setPrefWidth(300);
        esq.setMinWidth(0);
        dir.setMinWidth(0);
        StackPane meio = new StackPane(Ui.rotulo(relogio, "chance-min"));
        meio.setMinWidth(64);
        meio.setPrefWidth(64);
        HBox h = new HBox(10, esq, meio, dir);
        h.setAlignment(Pos.CENTER);
        h.getStyleClass().addAll("evento-linha", "linha-chance");
        return h;
    }

    /** Chance que não foi gol (chute pra fora, defesa, milagre, trave, gol anulado): linha curta. */
    private static boolean chance(Lance l) {
        if ("penalti".equals(l.jogada) || l.lado < 0) return false;
        return switch (l.tipo) {
            case "fora", "defesa", "milagre", "trave", "impedimento" -> true;
            default -> false;
        };
    }

    /** O contexto da frase: minuto, placar do lado do lance, seu time ou o adversário, quem chutou e o goleiro. */
    private static br.futebas.partida.Frases.Contexto contexto(Lance l, int meuLado, String time) {
        int pro = l.lado == 1 ? l.golsVisitante : l.golsMandante, contra = l.lado == 1 ? l.golsMandante : l.golsVisitante;
        String goleiro = "defesa".equals(l.tipo) || "milagre".equals(l.tipo) ? l.detalhe : null;
        return new br.futebas.partida.Frases.Contexto(l.minuto, l.acrescimo, meuLado < 0 ? null : l.lado == meuLado, pro, contra,
                l.jogador, goleiro, time, l.jogada);
    }

    /**
     * Uma chance que não foi gol, numa linha só e mais discreta que o gol: quem chutou e a legenda
     * ("Uhhh! Tirou tinta da trave!", "Milagre de Fulano!"). Gancho comum (a Kings usa a mesma).
     */
    static Node linhaChance(boolean casa, String relogio, String jogador, String legenda, Boolean meu) {
        Label txt = Ui.rotulo((jogador == null || jogador.isBlank() ? "" : jogador + "  ·  ") + legenda, "chance-texto");
        if (meu != null) txt.getStyleClass().add(meu ? "chance-minha" : "chance-deles");
        txt.setMinWidth(0);
        StackPane esq = new StackPane(), dir = new StackPane();
        esq.setAlignment(Pos.CENTER_RIGHT);
        dir.setAlignment(Pos.CENTER_LEFT);
        (casa ? esq : dir).getChildren().add(txt);
        HBox.setHgrow(esq, Priority.ALWAYS);
        HBox.setHgrow(dir, Priority.ALWAYS);
        esq.setPrefWidth(300);
        dir.setPrefWidth(300);
        esq.setMinWidth(0);
        dir.setMinWidth(0);
        StackPane meio = new StackPane(Ui.rotulo(relogio, "chance-min"));
        meio.setMinWidth(64);
        meio.setPrefWidth(64);
        HBox h = new HBox(10, esq, meio, dir);
        h.setAlignment(Pos.CENTER);
        h.getStyleClass().addAll("evento-linha", "linha-chance");
        return h;
    }

    private static boolean mostra(Lance l) {
        if (chance(l)) return true;
        if ("penalti".equals(l.jogada)) return true;
        return switch (l.tipo) {
            case "gol", "cartao", "vermelho", "substituicao", "lesao", "desfalque", "penalti", "inicio", "intervalo", "segundo_tempo", "prorrogacao",
                 "penalidades", "cobranca", "fim" -> true;
            default -> false;
        };
    }

    static Node linhaFase(String relogio, String texto) {
        Region a = new Region(), b = new Region();
        a.getStyleClass().add("evento-traco");
        b.getStyleClass().add("evento-traco");
        HBox.setHgrow(a, Priority.ALWAYS);
        HBox.setHgrow(b, Priority.ALWAYS);
        HBox h = new HBox(12, a, Ui.rotulo(relogio, "evento-min"), Ui.rotulo(texto, "evento-fase"), b);
        h.setAlignment(Pos.CENTER);
        h.getStyleClass().add("evento-linha");
        return h;
    }

    /** Um acontecimento: do mandante à esquerda, do visitante à direita, o minuto no meio. */
    static Node linhaEvento(boolean casa, String relogio, String jogador, String detalhe, String tipoIcone, String classeExtra) {
        Label nome = Ui.rotulo(jogador == null ? "" : jogador, "evento-nome");
        Label det = Ui.rotulo(detalhe, "evento-detalhe");
        nome.setMinWidth(0);
        det.setMinWidth(0);
        VBox txt = new VBox(0, nome, det);
        txt.setAlignment(casa ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if (jogador == null || jogador.isBlank()) txt.getChildren().remove(nome);
        HBox conteudo = casa ? new HBox(10, txt, icone(tipoIcone)) : new HBox(10, icone(tipoIcone), txt);
        conteudo.setAlignment(casa ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        conteudo.getStyleClass().addAll("evento-lado", casa ? "lado-casa" : "lado-fora");
        if (tipoIcone != null) conteudo.getStyleClass().add("ev-" + tipoIcone);
        if (classeExtra != null) conteudo.getStyleClass().add(classeExtra);
        StackPane esq = new StackPane(), dir = new StackPane();
        esq.setAlignment(Pos.CENTER_RIGHT);
        dir.setAlignment(Pos.CENTER_LEFT);
        (casa ? esq : dir).getChildren().add(conteudo);
        HBox.setHgrow(esq, Priority.ALWAYS);
        HBox.setHgrow(dir, Priority.ALWAYS);
        esq.setPrefWidth(300);
        dir.setPrefWidth(300);
        esq.setMinWidth(0);
        dir.setMinWidth(0);
        StackPane meio = new StackPane(Ui.rotulo(relogio, "evento-min"));
        meio.getStyleClass().add("evento-meio");
        meio.setMinWidth(64);
        meio.setPrefWidth(64);
        HBox h = new HBox(10, esq, meio, dir);
        h.setAlignment(Pos.CENTER);
        h.getStyleClass().add("evento-linha");
        return h;
    }

    /**
     * Gol na linha do tempo (requisito): sem a caixa; a bola, "GOL DO TIME!!" em negrito e
     * itálico, maior que o resto, e embaixo, menor, o autor e a assistência. {@code meu}: true = gol
     * do seu time (verde), false = sofrido (vermelho), null = neutro.
     */
    static Node linhaGol(boolean casa, String relogio, String time, String autor, String assistencia, Boolean meu) {
        return linhaGolTitulo(casa, relogio, "GOL DO " + (time == null ? "" : time.toUpperCase(java.util.Locale.ROOT)) + "!!", autor, assistencia, meu);
    }

    /**
     * Linha do gol com o título pronto (variações do {@link br.futebas.partida.Frases#tituloGol}: "TÁ LÁ
     * DENTRO! GOL DO TIME!", "QUE GOLAÇO!"...). Gancho comum: a Kings usa a mesma linha.
     */
    static Node linhaGolTitulo(boolean casa, String relogio, String tituloGol, String autor, String assistencia, Boolean meu) {
        Label titulo = Ui.rotulo(tituloGol, "gol-titulo");
        if (meu != null) titulo.getStyleClass().add(meu ? "gol-meu" : "gol-sofrido");
        titulo.setMinWidth(0);
        titulo.setWrapText(true);
        VBox txt = new VBox(1, titulo);
        if (autor != null && !autor.isBlank()) txt.getChildren().add(Ui.rotulo(autor, "gol-autor"));
        if (assistencia != null && !assistencia.isBlank()) txt.getChildren().add(Ui.rotulo("Assistência: " + assistencia, "gol-assist"));
        txt.setAlignment(casa ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        titulo.setTextAlignment(casa ? javafx.scene.text.TextAlignment.RIGHT : javafx.scene.text.TextAlignment.LEFT);
        Node bola = bola();
        bola.getStyleClass().add("bola-gol");
        HBox conteudo = casa ? new HBox(10, txt, bola) : new HBox(10, bola, txt);
        conteudo.setAlignment(casa ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        conteudo.getStyleClass().addAll("gol-lado", casa ? "lado-casa" : "lado-fora");
        StackPane esq = new StackPane(), dir = new StackPane();
        esq.setAlignment(Pos.CENTER_RIGHT);
        dir.setAlignment(Pos.CENTER_LEFT);
        (casa ? esq : dir).getChildren().add(conteudo);
        HBox.setHgrow(esq, Priority.ALWAYS);
        HBox.setHgrow(dir, Priority.ALWAYS);
        esq.setPrefWidth(300);
        dir.setPrefWidth(300);
        esq.setMinWidth(0);
        dir.setMinWidth(0);
        StackPane meio = new StackPane(Ui.rotulo(relogio, "evento-min"));
        meio.getStyleClass().add("evento-meio");
        meio.setMinWidth(64);
        meio.setPrefWidth(64);
        HBox h = new HBox(10, esq, meio, dir);
        h.setAlignment(Pos.CENTER);
        h.getStyleClass().addAll("evento-linha", "linha-gol");
        return h;
    }

    /**
     * Entrada do gol na narração (requisito: "meio animado"), ~550 ms: a linha cresce de leve e aparece,
     * a bola dá um giro e o título brilha por um instante. Quem chama decide (SIMULAR ATÉ O FIM e a
     * velocidade Ultra não animam). Vale para a partida, o multi e a Kings.
     */
    static void animarGol(Node linha) {
        javafx.util.Duration d = javafx.util.Duration.millis(320);
        linha.setOpacity(0);
        linha.setScaleX(0.88);
        linha.setScaleY(0.88);
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(180), linha);
        ft.setToValue(1);
        javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(d, linha);
        st.setToX(1);
        st.setToY(1);
        st.setInterpolator(new javafx.animation.Interpolator() { // passa um pouco do tamanho e volta (ease-out-back)
            @Override
            protected double curve(double t) {
                double k = 1.7, x = t - 1;
                return x * x * ((k + 1) * x + k) + 1;
            }
        });
        javafx.animation.ParallelTransition pt = new javafx.animation.ParallelTransition(ft, st);
        Node bola = linha.lookup(".bola-gol");
        if (bola != null) {
            javafx.animation.RotateTransition rt = new javafx.animation.RotateTransition(javafx.util.Duration.millis(550), bola);
            rt.setFromAngle(-200);
            rt.setToAngle(0);
            rt.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            pt.getChildren().add(rt);
        }
        Node titulo = linha.lookup(".gol-titulo");
        if (titulo != null) {
            javafx.scene.effect.DropShadow brilho = new javafx.scene.effect.DropShadow(0, javafx.scene.paint.Color.web("#f5c451"));
            brilho.setSpread(0.25);
            titulo.setEffect(brilho);
            javafx.animation.Timeline tl = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, new javafx.animation.KeyValue(brilho.radiusProperty(), 0)),
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(200), new javafx.animation.KeyValue(brilho.radiusProperty(), 16)),
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(560), new javafx.animation.KeyValue(brilho.radiusProperty(), 0)));
            tl.setOnFinished(e -> titulo.setEffect(null));
            pt.getChildren().add(tl);
        }
        pt.play();
    }

    /** Um pulso curto no número do placar (gol). */
    static void pulsar(Node n) {
        javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(150), n);
        st.setFromX(1);
        st.setFromY(1);
        st.setToX(1.28);
        st.setToY(1.28);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.play();
    }

    /** Ferramentas e capturas: uma amostra das linhas da narração (gol, cartões, troca, lesão, chances, pênalti, fases). */
    public static VBox amostraNarracao() {
        br.futebas.partida.Frases f = new br.futebas.partida.Frases(7);
        String[] nomes = {"Flamengo", "Palmeiras"};
        VBox v = new VBox();
        v.getStyleClass().add("narracao-fundo");
        List<Lance> ls = List.of(
                new Lance(0, 0, "inicio", "", -1, 0, 0, null, null, null, null),
                new Lance(8, 0, "defesa", "", 0, 0, 0, 9, "Pedro", "Weverton", null),
                new Lance(14, 0, "cartao", "Cartão amarelo para Aníbal Moreno (Palmeiras).", 1, 0, 0, 5, "Aníbal Moreno", null, null),
                new Lance(23, 0, "gol", "", 0, 1, 0, 10, "Arrascaeta", "Gerson", null),
                new Lance(31, 0, "trave", "", 1, 1, 0, 11, "Vitor Roque", null, null),
                new Lance(38, 0, "penalti", "PÊNALTI para o Palmeiras!", 1, 1, 0, null, null, null, "penalti"),
                new Lance(45, 1, "intervalo", "", -1, 1, 1, null, null, null, null),
                new Lance(52, 0, "lesao", "Léo Ortiz sente uma lesão e pede atendimento.", 0, 1, 1, 3, "Léo Ortiz", null, null),
                new Lance(53, 0, "substituicao", "Substituição no Flamengo: sai Léo Ortiz (lesionado), entra Danilo.", 0, 1, 1, 4, "Danilo", "Léo Ortiz", null),
                new Lance(67, 0, "vermelho", "Segundo amarelo! Aníbal Moreno está fora.", 1, 1, 1, 5, "Aníbal Moreno", null, null),
                new Lance(78, 0, "milagre", "", 0, 1, 1, 9, "Pedro", "Weverton", null),
                new Lance(90, 3, "gol", "", 1, 1, 2, 12, "Flaco López", null, "cabeca"),
                new Lance(90, 5, "fim", "", -1, 1, 2, null, null, null, null));
        for (Lance l : ls) {
            Node n = linhaDoLance(l, 0, nomes, f);
            if (n != null) v.getChildren().add(n);
        }
        return v;
    }

    /** Bola desenhada (branca, com o pentágono preto no meio e gomos em volta). */
    static Node bola() {
        double r = 11;
        Circle c = new Circle(r, r, r);
        c.setStyle("-fx-fill: white; -fx-stroke: #1b1f24; -fx-stroke-width: 1.4;");
        javafx.scene.Group g = new javafx.scene.Group(c);
        double pr = r * 0.38;
        javafx.scene.shape.Polygon pent = new javafx.scene.shape.Polygon();
        for (int k = 0; k < 5; k++) {
            double a = -Math.PI / 2 + k * 2 * Math.PI / 5;
            pent.getPoints().addAll(r + pr * Math.cos(a), r + pr * Math.sin(a));
        }
        pent.setStyle("-fx-fill: #1b1f24;");
        g.getChildren().add(pent);
        for (int k = 0; k < 5; k++) {
            double a = -Math.PI / 2 + k * 2 * Math.PI / 5;
            javafx.scene.shape.Line ln = new javafx.scene.shape.Line(r + pr * Math.cos(a), r + pr * Math.sin(a), r + r * 0.93 * Math.cos(a), r + r * 0.93 * Math.sin(a));
            ln.setStyle("-fx-stroke: #1b1f24; -fx-stroke-width: 1.2;");
            g.getChildren().add(ln);
        }
        StackPane s = new StackPane(g);
        s.setMinSize(2 * r + 2, 2 * r + 2);
        return s;
    }

    /** "Fulano (pênalti)", "Fulano (contra)", "Fulano, de cabeça"... a linha do autor no gol. */
    private static String autorDoGol(Lance l) {
        if (l.jogador == null) return null;
        String j = l.jogada == null ? "" : l.jogada;
        return l.jogador + switch (j) {
            case "contra" -> " (contra)";
            case "penalti" -> " (pênalti)";
            case "cabeca" -> ", de cabeça";
            case "falta" -> ", de falta";
            case "fora" -> ", de fora da área";
            default -> "";
        };
    }

    /**
     * A linha da narração de um lance (null se o lance não aparece). A tela dividida do multi-treinador
     * usa a mesma linha.
     */
    static Node linhaDoLance(Lance l, int meuLado, String[] nomesTimes, br.futebas.partida.Frases frases) {
        if (!mostra(l)) return null;
        boolean fase = switch (l.tipo) {
            case "inicio", "intervalo", "segundo_tempo", "prorrogacao", "penalidades", "fim" -> true;
            default -> false;
        };
        if (fase || l.lado < 0) return linhaFase(l.relogio(), textoFase(l));
        String time = l.lado >= 0 && l.lado < 2 ? nomesTimes[l.lado] : "";
        if ("gol".equals(l.tipo))
            return linhaGolTitulo(l.lado == 0, l.relogio(), frases.tituloGol(contexto(l, meuLado, time)), autorDoGol(l), l.detalhe,
                    meuLado < 0 ? null : l.lado == meuLado);
        if (chance(l))
            return linhaChance(l.lado == 0, l.relogio(), l.jogador, frases.chance(l.tipo, contexto(l, meuLado, time)), meuLado < 0 ? null : l.lado == meuLado);
        String extra = "vermelho".equals(l.tipo) || "desfalque".equals(l.tipo) ? "evento-vermelho" : null;
        return linhaEvento(l.lado == 0, l.relogio(), l.jogador, detalhe(l, frases), l.tipo, extra);
    }

    private void adicionarLance(Lance l, int meuLado) {
        if ("penalti".equals(l.tipo)) penaltiMarcado = l.minuto + "#" + l.lado;
        if (!mostra(l)) return;
        boolean fase = switch (l.tipo) {
            case "inicio", "intervalo", "segundo_tempo", "prorrogacao", "penalidades", "fim" -> true;
            default -> false;
        };
        Node linha;
        if (fase || l.lado < 0) {
            linha = linhaFase(l.relogio(), textoFase(l));
        } else if ("gol".equals(l.tipo)) {
            String time = l.lado >= 0 && l.lado < 2 ? nomesTimes[l.lado] : "";
            linha = linhaGolTitulo(l.lado == 0, l.relogio(), frases.tituloGol(contexto(l, meuLado, time)), autorDoGol(l), l.detalhe,
                    meuLado < 0 ? null : l.lado == meuLado);
        } else if (chance(l)) {
            String time = l.lado >= 0 && l.lado < 2 ? nomesTimes[l.lado] : "";
            linha = linhaChance(l.lado == 0, l.relogio(), l.jogador, frases.chance(l.tipo, contexto(l, meuLado, time)),
                    meuLado < 0 ? null : l.lado == meuLado);
        } else {
            String extra = "gol".equals(l.tipo) ? (meuLado < 0 || l.lado == meuLado ? "evento-gol" : "evento-gol-contra")
                    : "vermelho".equals(l.tipo) || "desfalque".equals(l.tipo) ? "evento-vermelho" : null;
            linha = linhaEvento(l.lado == 0, l.relogio(), l.jogador, detalhe(l, frases), l.tipo, extra);
        }
        narracao.getChildren().add(linha);
        boolean animar = acomp != null && acomp.velocidade() != Velocidade.INSTANTANEA && acomp.velocidade() != Velocidade.ULTRA && !pulando;
        if (animar && "gol".equals(l.tipo) && !fase && l.lado >= 0) {
            animarGol(linha);
            pulsar(l.lado == 0 ? golsM : golsV);
        } else if (animar) {
            linha.setOpacity(0);
            linha.setTranslateY(10);
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(260), linha);
            ft.setToValue(1);
            javafx.animation.TranslateTransition tt = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(260), linha);
            tt.setToY(0);
            new javafx.animation.ParallelTransition(ft, tt).play();
        }
    }

    private static String textoFase(Lance l) {
        return switch (l.tipo) {
            case "inicio" -> "Começa o jogo";
            case "intervalo" -> "Intervalo  " + l.golsMandante + " x " + l.golsVisitante;
            case "segundo_tempo" -> "Segundo tempo";
            case "prorrogacao" -> "Prorrogação";
            case "penalidades" -> "Disputa de pênaltis";
            case "fim" -> "Fim de jogo  " + l.golsMandante + " x " + l.golsVisitante;
            default -> l.texto;
        };
    }

    /** A linha de baixo do acontecimento: frase variada do banco comum (cartão, troca, lesão, pênalti) ou o texto do motor. */
    private static String detalhe(Lance l, br.futebas.partida.Frases frases) {
        if (frases != null) {
            String f = frases.evento(l.tipo, l.detalhe, l.texto);
            if (f != null) return f;
        }
        return switch (l.tipo) {
            case "gol" -> "contra".equals(l.jogada) ? "Gol contra" : "penalti".equals(l.jogada) ? "Gol de pênalti"
                    : "cabeca".equals(l.jogada) ? "Gol de cabeça" : "falta".equals(l.jogada) ? "Gol de falta" : "Gol";
            case "cartao" -> "Cartão amarelo";
            case "vermelho" -> "Expulso";
            case "lesao" -> "Lesão";
            case "desfalque" -> "Sai lesionado: um a menos";
            case "penalti" -> "Pênalti marcado";
            case "substituicao" -> l.texto.replaceFirst("^Substituição no [^:]*: ", "");
            case "cobranca" -> l.texto;
            default -> "penalti".equals(l.jogada) ? "Pênalti perdido" : l.texto;
        };
    }

    private static Node icone(String tipo) {
        Node n;
        switch (tipo == null ? "" : tipo) {
            case "gol" -> {
                Circle c = new Circle(5);
                c.setStyle("-fx-fill: #0b7a4b; -fx-stroke: white; -fx-stroke-width: 1.5;");
                n = c;
            }
            case "cartao" -> {
                Region r = new Region();
                r.getStyleClass().add("cartao-amarelo");
                n = r;
            }
            case "vermelho" -> {
                Region r = new Region();
                r.getStyleClass().add("cartao-vermelho");
                n = r;
            }
            case "substituicao" -> n = Ui.rotulo("⇄", "icone-sub");
            case "lesao", "desfalque" -> n = CampoEscalacao.cruzMedica();
            case "penalti" -> {
                Circle c = new Circle(4);
                c.getStyleClass().add("icone-penalti");
                n = c;
            }
            default -> n = new Region();
        }
        StackPane s = new StackPane(n);
        s.getStyleClass().add("icone-lance");
        s.setMinHeight(18);
        return s;
    }

    // ------------------------------------------------------------------ tática e trocas (camada)
    private void atualizarLateral(Estado e) {
        if (e.meuLado() < 0) return;
        trocas.atualizar(e);
        String st = e.estilo() + "|" + e.marcacao() + "|" + e.concentracao() + "|" + e.terminado();
        if (!st.equals(assinaturaTatica)) {
            assinaturaTatica = st;
            montarTatica(taticaPainel, e);
        }
    }

    /** Coluna MEU TIME: os seus em campo e no banco, com a energia de agora (cor: verde inteiro, vermelho cansado). */
    private void atualizarMeuTime(Estado e) {
        if (!mostrandoMeuTime || e == null) return;
        if (e.meuLado() < 0) {
            if (!"-".equals(assinaturaMeuTime)) {
                assinaturaMeuTime = "-";
                meuTimeLista.getChildren().setAll(Ui.rotulo("Você não joga esta partida.", "texto-suave"));
            }
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Atleta a : e.emCampo()) sb.append(a.id()).append(a.energia()).append(a.amarelo()).append(a.lesionado()).append(',');
        for (Atleta a : e.banco()) sb.append(a.id()).append(',');
        sb.append(e.subsFeitas());
        if (sb.toString().equals(assinaturaMeuTime)) return;
        assinaturaMeuTime = sb.toString();
        List<Node> l = new ArrayList<>();
        l.add(Ui.linha(8, Ui.rotulo("EM CAMPO", "coluna-titulo"), Ui.espaco(), Ui.rotulo(e.subsFeitas() + "/" + e.subsPermitidas() + " trocas", "texto-pequeno")));
        for (Atleta a : e.emCampo()) l.add(linhaEnergia(a, a.vaga()));
        if (!e.banco().isEmpty()) {
            l.add(Ui.rotulo("BANCO", "coluna-titulo"));
            for (Atleta a : e.banco()) l.add(linhaEnergia(a, a.natural()));
        }
        meuTimeLista.getChildren().setAll(l);
    }

    private static Node linhaEnergia(Atleta a, br.futebas.mundo.Posicao p) {
        Label n = Ui.rotulo(a.nome(), "jogo-nome");
        n.setMinWidth(0);
        n.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(n, Priority.ALWAYS);
        HBox h = new HBox(6, Ui.pos(p), n);
        if (a.lesionado()) {
            h.getChildren().add(CampoEscalacao.cruzMedica());
            h.getStyleClass().add("lesionado");
        }
        if (a.amarelo()) {
            Region r = new Region();
            r.getStyleClass().add("cartao-amarelo");
            h.getChildren().add(r);
        }
        h.getChildren().add(CampoEscalacao.barraEnergia(a.energia(), 44, true));
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("jogo-linha");
        return h;
    }

    private void montarTatica(HBox tatica, Estado e) {
        montarTatica(tatica, e, () -> acomp);
    }

    /** Estilo, marcação e concentração do seu lado (a tela dividida do multi-treinador usa o mesmo painel). */
    static void montarTatica(HBox tatica, Estado e, java.util.function.Supplier<AcompanhamentoPartida> fonte) {
        tatica.getChildren().clear();
        ComboBox<Estilo> estilo = new ComboBox<>();
        estilo.getItems().setAll(Estilo.values());
        estilo.setButtonCell(celulaEstilo());
        estilo.setCellFactory(x -> celulaEstilo());
        estilo.setValue(e.estilo());
        estilo.setPrefWidth(150);
        estilo.setFocusTraversable(false);
        estilo.valueProperty().addListener((o, a, b) -> {
            AcompanhamentoPartida acomp = fonte.get();
            if (b != null && acomp != null && b != e.estilo()) acomp.mudarEstilo(b);
        });
        HBox marc = Ui.segmentos(List.of(Marcacao.values()), x -> x.nome, e.marcacao(), x -> {
            AcompanhamentoPartida acomp = fonte.get();
            if (acomp != null) acomp.mudarMarcacao(x);
        });
        HBox conc = Ui.segmentos(List.of(Concentracao.values()), x -> switch (x) {
            case EQUILIBRADO -> "Equilibrado";
            case MEIO -> "Pelo meio";
            case LATERAIS -> "Laterais";
        }, e.concentracao(), x -> {
            AcompanhamentoPartida acomp = fonte.get();
            if (acomp != null) acomp.mudarConcentracao(x);
        });
        boolean fim = e.terminado();
        estilo.setDisable(fim);
        marc.setDisable(fim);
        conc.setDisable(fim);
        tatica.getChildren().addAll(Ui.dado("Estilo de jogo", estilo), Ui.dado("Marcação", marc), Ui.dado("Concentrar ataques", conc));
    }

    private static ListCell<Estilo> celulaEstilo() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Estilo s, boolean vazio) {
                super.updateItem(s, vazio);
                setText(vazio || s == null ? null : s.nome);
            }
        };
    }

    private void velocidade(Velocidade v) {
        if (!App.automatico) Opcoes.velocidade(v);
        if (acomp != null) acomp.velocidade(v);
        marcarVelocidade(v);
    }

    private void marcarVelocidade(Velocidade v) {
        for (int i = 0; i < botoesVelocidade.size(); i++) botoesVelocidade.get(i).setSelected(Velocidade.visiveis().get(i) == v);
    }

    private void alternarPausa() {
        if (acomp == null || terminou) return;
        acomp.pausar(!acomp.pausado());
        mostrarPausado(acomp.pausado());
        relogio.getStyleClass().remove("parado");
        if (acomp.pausado()) relogio.getStyleClass().add("parado");
    }

    // ------------------------------------------------------------------ suspense do pênalti
    /** Suspense ligado: velocidade com espera, sem "SIMULAR ATÉ O FIM" e fora das capturas. */
    private boolean suspenseLigado() {
        return !pulando && !App.automatico && acomp != null && acomp.velocidade().suspenseTotal() > 0;
    }

    /** As esperas do suspense na velocidade atual (as capturas aceleram pela mesma escala). */
    private long[] temposSuspense() {
        long[] t = acomp == null ? new long[3] : acomp.velocidade().suspensePenalti(); // ritmo fixo (~1,2 s), igual em toda velocidade
        for (int i = 0; i < t.length; i++) t[i] = Math.round(t[i] * AcompanhamentoPartida.escala);
        return t;
    }

    private void enfileirar(Lance l, int meuLado) {
        fila.add(new Object[] {l, meuLado});
        processarFila();
    }

    /** Solta a narração na ordem; o desfecho de um pênalti espera o letreiro (vai para a bola, bateu, resultado). */
    private void processarFila() {
        while (!emSuspense && !fila.isEmpty()) {
            Object[] x = fila.poll();
            Lance l = (Lance) x[0];
            int lado = (Integer) x[1];
            if (AcompanhamentoPartida.resultadoDePenalti(l) && suspenseLigado()) {
                // suspense na narração (requisito): uma linha por vez, devagar e no mesmo ritmo em qualquer velocidade
                emSuspense = true;
                String time = l.lado >= 0 && l.lado < 2 ? nomesTimes[l.lado] : "";
                String goleiro = "defesa".equals(l.tipo) || "milagre".equals(l.tipo) ? l.detalhe : null;
                SuspensePenalti.Desfecho d = SuspensePenalti.desfechoDoLance(l.tipo, l.texto);
                boolean jaMarcado = (l.minuto + "#" + l.lado).equals(penaltiMarcado);
                List<Runnable> linhas = new ArrayList<>();
                if (!jaMarcado) linhas.add(() -> narracao.getChildren().add(linhaPasso(l.lado == 0, l.relogio(),
                        "Pênalti marcado para o " + time + "!", "penalti-marcado")));
                String ida = SuspensePenalti.fraseIda(l.jogador), batida = SuspensePenalti.fraseBatida();
                linhas.add(() -> narracao.getChildren().add(linhaPasso(l.lado == 0, l.relogio(), ida, null)));
                linhas.add(() -> narracao.getChildren().add(linhaPasso(l.lado == 0, l.relogio(), batida, null)));
                if (d.gol()) {
                    golSegurado = new int[] {l.lado == 0 ? 1 : 0, l.lado == 1 ? 1 : 0};
                    atualizarVivo();
                }
                linhas.add(() -> {
                    if (golSegurado != null) {
                        golSegurado = null;
                        atualizarVivo();
                    }
                    if (d.gol()) adicionarLance(l, lado); // o gol no estilo do gol
                    else narracao.getChildren().add(linhaPasso(l.lado == 0, l.relogio(), SuspensePenalti.fraseResultado(d, goleiro), "penalti-erro"));
                });
                suspenseAtual = SuspensePenalti.emLinhas(linhas.size(), temposSuspense()[0], i -> linhas.get(i).run(), () -> {
                    if (golSegurado != null) {
                        golSegurado = null;
                        atualizarVivo();
                    }
                    emSuspense = false;
                    suspenseAtual = null;
                    processarFila();
                });
                return;
            }
            adicionarLance(l, lado);
        }
        if (!emSuspense && fila.isEmpty() && aposFila != null) {
            Runnable r = aposFila;
            aposFila = null;
            r.run();
        }
    }

    private void quandoFilaVazia(Runnable r) {
        aposFila = r;
        processarFila();
    }

    /**
     * A disputa de pênaltis (requisito): a narração do jogo é limpa e cada cobrança é narrada como
     * o pênalti no jogo ("X vai para a bola…", "Bateu…", o resultado), uma linha por vez no ritmo fixo do
     * suspense, alternando os times; no placar, as bolinhas de cada time (verde gol, vermelha erro, cinza
     * a bater). Sem suspense (SIMULAR ATÉ O FIM, capturas): tudo na hora, com as bolinhas. Depois, {@code fim}.
     */
    private void mostrarDisputa(ResultadoPartida ult, List<Lance> cobrancas, Runnable fim) {
        if (seguido >= 0) seguir(-1);
        List<SuspensePenalti.Cobranca> cs = cobrancasDaDisputa(cobrancas);
        narracao.getChildren().clear();
        narracao.getChildren().add(linhaFase("Pên.", "Disputa de pênaltis  ·  " + ult.mandante + " " + ult.golsMandante + " x "
                + ult.golsVisitante + " " + ult.visitante));
        for (SuspensePenalti.Bolinhas b : bolas) b.reiniciar();
        mostrarBolinhas(true);
        disputaAndando = true;
        pular.setDisable(false);
        relogio.setText("Pên. 0 x 0");
        int[] pen = new int[2];
        long ritmo = suspenseLigado() ? temposSuspense()[0] : 0;
        disputaToque = SuspensePenalti.disputaEmLinhas(cs, ritmo, (i, c, etapa, txt) -> {
            int lado = c.lado() == 1 ? 1 : 0;
            switch (etapa) {
                case SuspensePenalti.ALTERNADAS -> narracao.getChildren().add(linhaFase("Pên.", txt));
                case SuspensePenalti.IDA -> {
                    bolas[lado].vez();
                    narracao.getChildren().add(linhaPasso(lado == 0, "Pên.", txt, null));
                }
                case SuspensePenalti.BATIDA -> narracao.getChildren().add(linhaPasso(lado == 0, "Pên.", txt, null));
                default -> {
                    boolean gol = c.desfecho().gol();
                    bolas[lado].marcar(gol);
                    if (gol) pen[lado]++;
                    relogio.setText("Pên. " + pen[0] + " x " + pen[1]);
                    narracao.getChildren().add(linhaPasso(lado == 0, "Pên.", txt, gol ? "penalti-gol" : "penalti-erro"));
                }
            }
        }, () -> {
            disputaToque = null;
            String venc = pen[0] > pen[1] ? ult.mandante : ult.visitante;
            narracao.getChildren().add(linhaFase("Pên.", venc + " vence nos pênaltis: " + pen[0] + " x " + pen[1]));
            fim.run();
        });
    }

    /** As cobranças da disputa (lances "cobranca" do motor) para a narração: lado, batedor, desfecho e goleiro. */
    static List<SuspensePenalti.Cobranca> cobrancasDaDisputa(List<Lance> cobrancas) {
        List<SuspensePenalti.Cobranca> cs = new ArrayList<>();
        for (Lance l : cobrancas) {
            String jeito = null, goleiro = null;
            if (l.detalhe != null && l.detalhe.contains(":")) {
                jeito = l.detalhe.substring(0, l.detalhe.indexOf(':'));
                goleiro = l.detalhe.substring(l.detalhe.indexOf(':') + 1);
            }
            boolean gol = l.texto != null ? l.texto.startsWith("Converteu")
                    : jeito != null && List.of("angulo", "canto", "deslocou", "meio", "cavadinha").contains(jeito);
            cs.add(new SuspensePenalti.Cobranca(l.lado, l.jogador, SuspensePenalti.desfecho(gol, jeito),
                    goleiro == null || goleiro.isBlank() ? null : goleiro));
        }
        return cs;
    }

    /** Mostra (ou esconde) as bolinhas da disputa no placar. */
    private void mostrarBolinhas(boolean v) {
        bolasAtivas = v;
        for (SuspensePenalti.Bolinhas b : bolas) {
            b.setVisible(v);
            b.setManaged(v);
        }
    }

    /** Testes e capturas: as bolinhas do placar ("GGE--" mandante, visitante); null sem disputa. */
    String[] bolinhas() {
        return bolasAtivas ? new String[] {bolas[0].resumo(), bolas[1].resumo()} : null;
    }

    // ------------------------------------------------------------------ disputa de pênaltis: a ordem
    /**
     * A escolha da ordem dos batedores (o motor espera): já vem com a sugestão automática; o técnico
     * confirma ou reordena. Nas capturas, confirma sozinha.
     */
    private void abrirOrdem(AcompanhamentoPartida.PedidoOrdem p) {
        if (ordemAberta != null || pulando || acomp == null) {
            if (acomp != null && ordemAberta == null) acomp.responderOrdem(null);
            return;
        }
        if (painelAberto && !batedorAberto) fecharPainel();
        painelAberto = true;
        Label tit = Ui.rotulo("Disputa de pênaltis", "titulo-tela");
        Label sub = Ui.rotulo(p.time() + " x " + p.adversario() + "  ·  escolha a ordem dos batedores", "texto-medio");
        ordemAberta = new OrdemPenaltis(new VBox(2, tit, sub), p.sugestao(), this::responderOrdem);
        sobreposicao.getChildren().setAll(ordemAberta);
        sobreposicao.setVisible(true);
        relogio.setText("Pênaltis");
        if (App.automatico) {
            javafx.animation.PauseTransition t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1200));
            OrdemPenaltis o = ordemAberta;
            t.setOnFinished(x -> {
                if (ordemAberta == o) o.confirmar();
            });
            t.play();
        }
    }

    private void responderOrdem(List<Integer> ids) {
        if (ordemAberta == null) return;
        ordemAberta = null;
        painelAberto = false;
        sobreposicao.setVisible(false);
        sobreposicao.getChildren().setAll(caixaPainel);
        if (acomp != null) acomp.responderOrdem(ids);
    }

    /** Testes e capturas: a escolha da ordem aberta (null se não há). */
    OrdemPenaltis ordemAberta() { return ordemAberta; }

    /** Pausar ⇄ Retomar, com o ícone (duas barras / triângulo). */
    private void mostrarPausado(boolean pausado) {
        pausar.setText(pausado ? "Retomar" : "Pausar");
        pausar.setGraphic(pausado ? iconePlay() : iconePausa());
    }

    private static Node iconePausa() {
        javafx.scene.shape.Rectangle a = new javafx.scene.shape.Rectangle(0, 0, 3.5, 12), b = new javafx.scene.shape.Rectangle(6.5, 0, 3.5, 12);
        a.getStyleClass().add("icone-rodape");
        b.getStyleClass().add("icone-rodape");
        return new javafx.scene.Group(a, b);
    }

    private static Node iconePlay() {
        javafx.scene.shape.Polygon t = new javafx.scene.shape.Polygon(0, 0, 10, 6, 0, 12);
        t.getStyleClass().add("icone-rodape");
        return new javafx.scene.Group(t);
    }

    /** Avançar (dois triângulos) do SIMULAR ATÉ O FIM. */
    private static Node iconeAvancar() {
        javafx.scene.shape.Polygon a = new javafx.scene.shape.Polygon(0, 0, 7, 6, 0, 12), b = new javafx.scene.shape.Polygon(7, 0, 14, 6, 7, 12);
        a.getStyleClass().add("icone-rodape");
        b.getStyleClass().add("icone-rodape");
        return new javafx.scene.Group(a, b);
    }

    /** Os controles do rodapé (teste de largura: nada corta a 1366 e 1920 px). */
    List<javafx.scene.control.Control> controlesRodape() {
        List<javafx.scene.control.Control> l = new ArrayList<>(botoesVelocidade);
        l.addAll(List.of(pausar, botaoTatica, pular, continuar));
        return l;
    }

    private void pular() {
        if (ordemAberta != null) responderOrdem(null); // SIMULAR ATÉ O FIM: ordem automática
        if (disputaAndando) {
            pulando = true;
            if (disputaToque != null && !disputaToque.acabou()) disputaToque.pular();
            return;
        }
        if (terminou && !emSuspense) {
            continuar();
            return;
        }
        // SIMULAR ATÉ O FIM: sem suspense, o resto do jogo na hora
        pulando = true;
        if (suspenseAtual != null && !suspenseAtual.acabou()) suspenseAtual.pular();
        if (!terminou && acomp != null) {
            if (batedorAberto) responderBatedor(null);
            fecharPainel();
            acomp.cancelarEspera();
        }
    }

    // ------------------------------------------------------------------ fim da rodada
    private void fim(Fluxo.Rodada r) {
        rodada = r;
        terminou = true;
        if (!r.jogou()) {
            // semanas sem jogo (fim de temporada, demissão): segue direto
            App.atual().depoisDaRodada(r);
            return;
        }
        ResultadoPartida ult = r.narradas().isEmpty() ? null : r.narradas().get(r.narradas().size() - 1);
        List<Lance> dePenaltis = new ArrayList<>();
        int lado = ultimo == null ? -1 : ultimo.meuLado();
        if (ult != null) {
            nomesTimes[0] = ult.mandante;
            nomesTimes[1] = ult.visitante;
            for (int i = exibidos; i < ult.lances.size(); i++) {
                Lance l = ult.lances.get(i);
                if ("cobranca".equals(l.tipo)) dePenaltis.add(l);
                else enfileirar(l, lado);
            }
            exibidos = ult.lances.size();
        }
        fecharPainel();
        pausar.setDisable(true);
        continuar.setDisable(true);
        for (var b : botoesVelocidade) b.setDisable(true);
        // a narração que falta (e o suspense de um pênalti no último minuto) e, se houve, a disputa de pênaltis
        quandoFilaVazia(() -> {
            // com ou sem suspense (SIMULAR ATÉ O FIM: tudo na hora), a disputa limpa a narração e enche as bolinhas
            if (!dePenaltis.isEmpty() && ult != null) mostrarDisputa(ult, dePenaltis, () -> concluirFim(r, ult));
            else concluirFim(r, ult);
        });
    }

    /** O resto do fim de jogo (números, placar, Continuar), depois da narração e da disputa de pênaltis. */
    private void concluirFim(Fluxo.Rodada r, ResultadoPartida ult) {
        disputaAndando = false;
        pular.setDisable(true);
        continuar.setDisable(false);
        if (ultimo == null && !r.meusJogos().isEmpty()) {
            // jogo sem acompanhamento (delegado): monta a tela com o resultado
            Partida p = r.meusJogos().get(0);
            preencherCabecalho(p.mandante, p.visitante);
        }
        if (ult != null) {
            narracao.getChildren().add(painelNumeros(ult, false));
            if (seguido < 0) {
                golsM.setText(String.valueOf(ult.golsMandante));
                golsV.setText(String.valueOf(ult.golsVisitante));
            }
        }
        if (minhaLinha == null && ult != null) {
            minhaLinha = new LinhaJogo(-1, ult.mandanteId, ult.visitanteId);
            minhaLinha.raiz.getStyleClass().addAll("meu", "seguido");
            linhas.add(0, minhaLinha);
            jogosLista.getChildren().add(0, minhaLinha.raiz);
        }
        if (outros.isEmpty()) {
            for (Partida p : r.outrosResultados()) {
                if (p.golsMandante == null) continue;
                LinhaJogo l = new LinhaJogo(-2, p.mandante, p.visitante);
                l.mostrar(new int[] {p.golsMandante, p.golsVisitante}, "Fim");
                jogosLista.getChildren().add(l.raiz);
                if (jogosLista.getChildren().size() > 24) break;
            }
        }
        atualizarVivo();
        if (seguido < 0 && ult != null) {
            relogio.setText(ult.penaltisMandante != null ? (sessao.fluxo.soKings() ? "Shoot out " : "Pên. ") + ult.penaltisMandante + " x " + ult.penaltisVisitante : "Fim");
            if (minhaLinha != null) minhaLinha.mostrar(new int[] {ult.golsMandante, ult.golsVisitante}, "Fim");
        }
        relogio.getStyleClass().add("parado");
        mostrarFim(true);
        ajustarTrilha(1);
        raiz.requestFocus();
    }

    private VBox painelNumeros(ResultadoPartida res, boolean varios) {
        VBox numeros = new VBox(10,
                Ui.comparacao("Posse de bola", res.posse[0], res.posse[1], res.posse[0] + "%", res.posse[1] + "%"),
                Ui.comparacao("Finalizações", res.chutes[0], res.chutes[1], res.chutes[0] + "", res.chutes[1] + ""),
                Ui.comparacao("No gol", res.noGol[0], res.noGol[1], res.noGol[0] + "", res.noGol[1] + ""),
                Ui.comparacao("Gols esperados", res.xgMandante, res.xgVisitante, Textos.umaCasa(res.xgMandante), Textos.umaCasa(res.xgVisitante)),
                Ui.comparacao("Faltas", res.faltas[0], res.faltas[1], res.faltas[0] + "", res.faltas[1] + ""));
        VBox card = Ui.painel(varios ? res.mandante + " " + res.placar() + " " + res.visitante : "Números do jogo", numeros);
        if (res.melhorNome != null) card.getChildren().add(Ui.linha(8, Ui.etiqueta("Melhor em campo", "verde"), Ui.rotulo(res.melhorNome, "texto-forte")));
        card.setStyle("-fx-padding: 12 24 12 24;");
        VBox.setMargin(card, new javafx.geometry.Insets(10, 14, 10, 14));
        return card;
    }

    /** Uma linha de resultado: escudo, nome (à direita), placar, nome, escudo. */
    static HBox linhaResultado(Mundo m, Partida p, String meu) {
        Label a = Ui.nomeClube(p.mandante, Textos.nomeClube(m, p.mandante));
        Label b = Ui.nomeClube(p.visitante, Textos.nomeClube(m, p.visitante));
        for (Label l : List.of(a, b)) {
            l.setMinWidth(0);
            l.setPrefWidth(136);
            l.setMaxWidth(136);
        }
        a.setAlignment(Pos.CENTER_RIGHT);
        if (p.mandante.equals(meu)) a.getStyleClass().add("texto-forte");
        if (p.visitante.equals(meu)) b.getStyleClass().add("texto-forte");
        String placar = p.golsMandante == null ? "x" : p.golsMandante + " x " + p.golsVisitante;
        Label pl = Ui.rotulo(placar, "resultado-placar");
        HBox h = new HBox(8, a, Ui.escudo(p.mandante, 18), pl, Ui.escudo(p.visitante, 18), b);
        h.getStyleClass().add("resultado-linha");
        return h;
    }

    /** Tabela curta (posição, clube, pontos, jogos, saldo). */
    static TableView<br.futebas.competicao.LinhaTabela> miniTabela(Mundo m, List<br.futebas.competicao.LinhaTabela> ls, String meu) {
        TableView<br.futebas.competicao.LinhaTabela> tv = Ui.tabela();
        Map<String, Integer> pos = new HashMap<>();
        for (int i = 0; i < ls.size(); i++) pos.put(ls.get(i).clubeId, i + 1);
        Ui.colNum(tv, "#", 36, l -> pos.get(l.clubeId));
        TableColumn<br.futebas.competicao.LinhaTabela, String> c = Ui.colClube(tv, "Clube", 200, m, l -> l.clubeId);
        c.setSortable(false);
        TableColumn<br.futebas.competicao.LinhaTabela, Integer> p = Ui.colNum(tv, "P", 46, l -> l.pontos);
        p.getStyleClass().add("col-pontos");
        p.setCellFactory(x -> new TableCell<>() {
            {
                getStyleClass().add("num-forte");
            }

            @Override
            protected void updateItem(Integer v, boolean vazio) {
                super.updateItem(v, vazio);
                setText(vazio || v == null ? null : String.valueOf(v));
            }
        });
        Ui.colNum(tv, "J", 34, l -> l.jogos);
        Ui.colNum(tv, "SG", 40, br.futebas.competicao.LinhaTabela::saldo);
        for (TableColumn<br.futebas.competicao.LinhaTabela, ?> col : tv.getColumns()) col.setSortable(false);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.getItems().setAll(ls);
        Ui.estiloLinhas(tv, (l, row) -> {
            if (l.clubeId.equals(meu)) row.getStyleClass().add("meu-clube");
        });
        Ui.alturaPorLinhas(tv, ls.size());
        return tv;
    }

    /** Volta do intervalo entre dois jogos da semana: limpa a tela e libera o motor. */
    public void retomar() {
        if (!entreJogos) return;
        entreJogos = false;
        narracao.getChildren().clear();
        mostrarBolinhas(false);
        pe.getChildren().setAll(fimDeJogo);
        mostrarFim(false);
        paralelosMontados = null;
        cabecalhoDe = "";
        continuar.setText("Continuar");
        continuar.setDisable(true);
        pausar.setDisable(false);
        pular.setDisable(false);
        pulando = false;
        Estado e = proximoEstado;
        proximoEstado = null;
        ultimo = null;
        // o aviso do 2º jogo (antes de o motor montar os times) não é um estado de jogo
        if (e != null && e.meuLado() >= 0) atualizar(e);
        if (acomp != null) acomp.liberarProximo();
    }

    private void continuar() {
        if (disputaAndando || continuar.isDisabled() && !entreJogos) return;
        if (entreJogos) {
            App.atual().partidaPendente(this);
            return;
        }
        if (rodada == null) return;
        App.atual().depoisDaRodada(rodada);
    }

    public boolean terminou() { return terminou; }

    /** Minuto do jogo na tela (-1 antes do apito). */
    public int minuto() { return ultimo == null ? -1 : ultimo.minuto(); }

    public boolean entreJogos() { return entreJogos; }

    public boolean kingsVisivel() { return kings != null && kings.isVisible(); }

    /** Quantos jogos a coluna JOGOS mostra (capturas e testes). */
    public int jogosNaColuna() { return jogosLista.getChildren().size(); }

    /** Acompanha o i-ésimo dos outros jogos (capturas). */
    public void acompanharOutro(int i) {
        if (i < outros.size()) seguir(Math.max(-1, i));
    }

    @Override
    public boolean tecla(KeyEvent e) {
        KeyCode k = e.getCode();
        if (kings != null && kings.isVisible()) {
            if (k == KeyCode.ENTER || k == KeyCode.SPACE) kings.seguir();
            else if (k == KeyCode.ESCAPE) kings.pular();
            return true;
        }
        if (batedorAberto) {
            if (escolhaBatedor != null) escolhaBatedor.tecla(k);
            else if (k == KeyCode.ESCAPE) responderBatedor(null);
            return true;
        }
        if (ordemAberta != null) {
            if (k == KeyCode.ENTER) ordemAberta.confirmar();
            else if (k == KeyCode.ESCAPE) responderOrdem(null);
            return true;
        }
        if (painelAberto) {
            if (k == KeyCode.ENTER || k == KeyCode.ESCAPE || k == KeyCode.T) {
                fecharPainel();
                return true;
            }
            return false;
        }
        if (terminou || entreJogos) {
            if (k == KeyCode.ENTER || k == KeyCode.SPACE || k == KeyCode.ESCAPE) {
                // disputa de pênaltis (ou pênalti do último minuto) ainda no suspense: a tecla pula as esperas
                if (disputaAndando || emSuspense) pular();
                else continuar();
                return true;
            }
            return false;
        }
        switch (k) {
            case DIGIT1, NUMPAD1 -> velocidade(Velocidade.LENTA);
            case DIGIT2, NUMPAD2 -> velocidade(Velocidade.NORMAL);
            case DIGIT3, NUMPAD3 -> velocidade(Velocidade.RAPIDA);
            case DIGIT4, NUMPAD4 -> velocidade(Velocidade.ULTRA);
            case P, SPACE -> alternarPausa();
            case T -> abrirPainel("Tática e trocas", "");
            case ESCAPE, ENTER -> pular();
            case M -> seguir(-1);
            default -> {
                return false;
            }
        }
        return true;
    }
}
