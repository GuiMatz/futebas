package br.futebas.ui.telas;

import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Tecnico;
import br.futebas.partida.Lance;
import br.futebas.ui.App;
import br.futebas.ui.Opcoes;
import br.futebas.ui.Sessao;
import br.futebas.ui.Tela;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoPartida;
import br.futebas.ui.modelo.AcompanhamentoPartida.Estado;
import br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade;
import br.futebas.ui.modelo.Fluxo;
import br.futebas.ui.modelo.MesaAoVivo;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-treinador (regra do jogo): a rodada ao vivo numa tela só, dividida. Cada jogo de técnico
 * humano tem a sua parte (2 jogos: metade e metade; mais: em grade) com placar, relógio e narração, e
 * o botão "Tática e trocas" de cada técnico fica no lado dele (duelo direto: mandante à esquerda,
 * visitante à direita). Os jogos andam juntos ({@link MesaAoVivo}); no intervalo, a tática e as trocas
 * abrem para um técnico de cada vez, na ordem, e só depois o segundo tempo segue.
 */
public final class TelaPartidaMulti implements Tela {
    private final Sessao sessao;
    private final BorderPane raiz = new BorderPane();
    private final StackPane camadas = new StackPane();
    private final GridPane grade = new GridPane();
    private final Label titulo = Ui.rotulo("RODADA AO VIVO", "rodada-titulo");
    private final Label sub = Ui.rotulo("", "rodada-sub");
    private final FlowPane resultados = new FlowPane(16, 6);
    private final List<ToggleButton> botoesVelocidade = new ArrayList<>();
    private final Button pausar = Ui.botaoTecla("Pausar", "P", this::alternarPausa, "rodape-botao");
    private final Button pular = Ui.botaoTecla("SIMULAR ATÉ O FIM", "Esc", this::pular, "rodape-botao", "rodape-forte");
    private final Button continuar = Ui.botaoTecla("Continuar", "Enter", this::continuar, "rodape-botao", "primario");
    private final br.futebas.partida.Frases frases = new br.futebas.partida.Frases(System.nanoTime());

    // camada da tática e trocas (um técnico de cada vez)
    private final StackPane sobreposicao = new StackPane();
    private final Label tituloPainel = Ui.rotulo("", "titulo-tela");
    private final Label motivoPainel = Ui.rotulo("", "camada-motivo");
    private final HBox taticaPainel = new HBox(24);
    private final StackPane trocasLugar = new StackPane();
    private final StackPane confirmarLugar = new StackPane();
    private Lado painelDe;
    private boolean painelPedido;
    private final ArrayDeque<Runnable> filaPainel = new ArrayDeque<>();

    private MesaAoVivo mesa;
    private final List<Quadro> quadros = new ArrayList<>();
    /** Cada técnico humano em jogo ao vivo (criado na thread do motor, a parte da tela vem depois). */
    private final Map<AcompanhamentoPartida, Lado> lados = new ConcurrentHashMap<>();
    private Fluxo.Rodada rodada;
    private boolean terminou;
    /** 2º jogo de técnico na semana: a tela espera os técnicos voltarem da central (regra do jogo). */
    private boolean entreGrupos;
    private List<String> clubesDoProximo = List.of();
    /** Técnico escolhendo o batedor do pênalti (na parte dele da tela). */
    private Lado batedorDe;
    /** Técnico escolhendo a ordem dos batedores da disputa de pênaltis (na parte dele da tela) e a escolha aberta. */
    private Lado ordemDe;
    private OrdemPenaltis ordemAberta;
    /** Disputas de pênaltis ainda sendo narradas (Continuar espera). */
    private int disputasAndando;
    // Kings no multi: as partes dos jogos da Kings e o técnico com a camada de trocas aberta
    private final List<QuadroKings> quadrosKings = new ArrayList<>();
    private final Map<br.futebas.ui.modelo.AcompanhamentoKings, LadoKings> ladosKings = new ConcurrentHashMap<>();
    private LadoKings kingsDe;
    private boolean kingsPedido;

    /** Um técnico num jogo ao vivo: o acompanhamento dele, as trocas e o botão no lado dele. */
    private final class Lado {
        final AcompanhamentoPartida acomp;
        final String clubeId;
        final String tecnico;
        TrocasAoVivo trocas;
        Quadro quadro;
        Button botao;
        Estado ultimo;
        AcompanhamentoPartida.Pedido atendido;

        Lado(AcompanhamentoPartida acomp, String clubeId, String tecnico) {
            this.acomp = acomp;
            this.clubeId = clubeId;
            this.tecnico = tecnico;
        }
    }

    /** A parte da tela de um jogo. */
    private final class Quadro {
        final MesaAoVivo.Jogo jogo;
        final String[] nomes = {"", ""};
        final Label golsM = Ui.rotulo("0", "placar-gols"), golsV = Ui.rotulo("0", "placar-gols");
        final Label relogio = Ui.rotulo("0'", "placar-relogio");
        final VBox narracao = new VBox();
        final ScrollPane rolagem = new ScrollPane(narracao);
        final StackPane raiz = new StackPane();
        /** Escolha do batedor, por cima da parte deste jogo (o outro jogo espera). */
        final VBox camadaBatedor = new VBox(8);
        /** Quem narra (o primeiro acompanhamento: no duelo, os dois veem os mesmos lances). */
        AcompanhamentoPartida narrador;
        boolean fim;
        /** As bolinhas da disputa de pênaltis no placar (só aparecem na disputa). */
        final SuspensePenalti.Bolinhas[] bolas = {new SuspensePenalti.Bolinhas(6), new SuspensePenalti.Bolinhas(6)};
        /** A disputa sendo narrada (SIMULAR ATÉ O FIM / Esc pula). */
        SuspensePenalti.Toque disputa;

        Quadro(MesaAoVivo.Jogo jogo) {
            this.jogo = jogo;
            Mundo m = sessao.jogo.mundo();
            nomes[0] = m.nomeParticipante(jogo.mandanteId);
            nomes[1] = m.nomeParticipante(jogo.visitanteId);
            narrador = jogo.lados.isEmpty() ? null : jogo.lados.get(0);
            String comp = competicao(jogo.mandanteId, jogo.visitanteId);
            Label cab = Ui.rotulo(comp == null ? "" : comp.toUpperCase(java.util.Locale.ROOT), "coluna-titulo");
            if (jogo.duelo()) cab.setText(cab.getText() + (cab.getText().isEmpty() ? "" : "  ·  ") + "DUELO DE TÉCNICOS");

            VBox ladoM = lado(jogo.mandanteId, nomes[0]), ladoV = lado(jogo.visitanteId, nomes[1]);
            ladoM.getChildren().add(bolas[0]);
            ladoV.getChildren().add(bolas[1]);
            for (SuspensePenalti.Bolinhas b : bolas) {
                b.setVisible(false);
                b.setManaged(false);
            }
            HBox numeros = new HBox(10, golsM, Ui.rotulo("–", "placar-gols"), golsV);
            numeros.setAlignment(Pos.CENTER);
            VBox meio = new VBox(2, numeros, relogio);
            meio.setAlignment(Pos.CENTER);
            meio.setMinWidth(120);
            HBox placar = new HBox(8, ladoM, meio, ladoV);
            placar.setAlignment(Pos.CENTER);
            placar.getStyleClass().addAll("placar", "placar-cartao");

            rolagem.setFitToWidth(true);
            rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            rolagem.getStyleClass().add("narracao-fundo");
            narracao.getStyleClass().add("narracao-fundo");
            narracao.heightProperty().addListener((o, a, b) -> rolagem.setVvalue(1.0));
            VBox.setVgrow(rolagem, Priority.ALWAYS);

            // os botões de cada técnico, no lado dele (mandante à esquerda, visitante à direita)
            HBox botoes = new HBox(10);
            botoes.setAlignment(Pos.CENTER);
            Node esq = new Region(), dir = new Region();
            for (AcompanhamentoPartida a : jogo.lados) {
                Lado l = lados.get(a);
                if (l == null) continue;
                l.quadro = this;
                l.botao = Ui.botao("⇄  Tática e trocas · " + l.tecnico, () -> abrirPainel(l, "Tática e trocas", "", false), "rodape-botao");
                l.botao.setMinWidth(Region.USE_PREF_SIZE);
                if (a.clubeId().equals(jogo.mandanteId)) esq = l.botao;
                else dir = l.botao;
                l.trocas = new TrocasAoVivo(sessao, () -> l.acomp, () -> sessao.jogo.clube(l.clubeId));
                l.trocas.aoConfirmar(TelaPartidaMulti.this::fecharPainel);
            }
            botoes.getChildren().addAll(esq, Ui.espaco(), dir);

            VBox conteudo = new VBox(8, cab, placar, rolagem, botoes);
            conteudo.getStyleClass().add("bloco-jogo");
            conteudo.setStyle("-fx-padding: 10;");
            // a caixa (EscolhaBatedor) já vem com o fundo; a camada só centraliza com uma margem
            camadaBatedor.setAlignment(Pos.CENTER);
            camadaBatedor.setMaxSize(500, Region.USE_PREF_SIZE);
            StackPane.setMargin(camadaBatedor, new javafx.geometry.Insets(12));
            camadaBatedor.setVisible(false);
            raiz.getChildren().setAll(conteudo, camadaBatedor);
            raiz.setMinWidth(320);
        }

        private VBox lado(String id, String nome) {
            Label n = Ui.rotulo(nome, "placar-time");
            n.setWrapText(true);
            n.setMinWidth(0);
            n.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            VBox v = new VBox(4, Ui.escudo(id, 44), n);
            Tecnico t = sessao.jogo.mundo().tecnicoDoClube(id);
            if (t != null) v.getChildren().add(Ui.rotulo("Técnico: " + t.nome, "texto-pequeno"));
            v.setAlignment(Pos.CENTER);
            v.setPrefWidth(160);
            v.setMinWidth(90);
            HBox.setHgrow(v, Priority.ALWAYS);
            return v;
        }

        // ---------------------------------------------- suspense do pênalti no jogo (igual ao jogo de um técnico)
        /** Lances esperando a vez na narração ({lance, meuLado}): o suspense de um pênalti segura os seguintes. */
        final ArrayDeque<Object[]> fila = new ArrayDeque<>();
        boolean emSuspense;
        /** O suspense em andamento (SIMULAR ATÉ O FIM pula). */
        SuspensePenalti.Toque suspense;
        /** Pênalti já anunciado na narração ("minuto#lado"): o suspense não repete o "Pênalti marcado". */
        String penaltiMarcado;
        /** O placar que o motor mandou; o gol de pênalti só aparece depois do "GOL!" na narração. */
        final int[] golsEstado = new int[2];
        int[] golSegurado;
        Runnable aposFila;

        private boolean suspenseLigado() {
            return !App.automatico && mesa != null && !mesa.pulando() && mesa.velocidade().suspenseTotal() > 0;
        }

        private void mostrarGols() {
            int[] s = golSegurado;
            golsM.setText(String.valueOf(Math.max(0, golsEstado[0] - (s == null ? 0 : s[0]))));
            golsV.setText(String.valueOf(Math.max(0, golsEstado[1] - (s == null ? 0 : s[1]))));
        }

        private void adicionar(Lance l, int meu) {
            if ("penalti".equals(l.tipo)) penaltiMarcado = l.minuto + "#" + l.lado;
            Node linha = TelaPartida.linhaDoLance(l, meu, nomes, frases);
            if (linha == null) return;
            narracao.getChildren().add(linha);
            if ("gol".equals(l.tipo) && l.lado >= 0 && animarGols()) {
                TelaPartida.animarGol(linha);
                TelaPartida.pulsar(l.lado == 0 ? golsM : golsV);
            }
        }

        /** Solta a narração na ordem; o pênalti vem uma linha por vez ("vai para a bola…", "Bateu…", o resultado). */
        void processarFila() {
            while (!emSuspense && !fila.isEmpty()) {
                Object[] x = fila.poll();
                Lance l = (Lance) x[0];
                int meu = (Integer) x[1];
                if (AcompanhamentoPartida.resultadoDePenalti(l) && suspenseLigado()) {
                    emSuspense = true;
                    String time = l.lado >= 0 && l.lado < 2 ? nomes[l.lado] : "";
                    String goleiro = "defesa".equals(l.tipo) || "milagre".equals(l.tipo) ? l.detalhe : null;
                    SuspensePenalti.Desfecho d = SuspensePenalti.desfechoDoLance(l.tipo, l.texto);
                    boolean casa = l.lado == 0;
                    List<Runnable> linhas = new ArrayList<>();
                    if (!(l.minuto + "#" + l.lado).equals(penaltiMarcado))
                        linhas.add(() -> narracao.getChildren().add(TelaPartida.linhaPasso(casa, l.relogio(), "Pênalti marcado para o " + time + "!", "penalti-marcado")));
                    String ida = SuspensePenalti.fraseIda(l.jogador), batida = SuspensePenalti.fraseBatida();
                    linhas.add(() -> narracao.getChildren().add(TelaPartida.linhaPasso(casa, l.relogio(), ida, null)));
                    linhas.add(() -> narracao.getChildren().add(TelaPartida.linhaPasso(casa, l.relogio(), batida, null)));
                    if (d.gol()) {
                        golSegurado = new int[] {casa ? 1 : 0, casa ? 0 : 1};
                        mostrarGols();
                    }
                    linhas.add(() -> {
                        if (golSegurado != null) {
                            golSegurado = null;
                            mostrarGols();
                        }
                        if (d.gol()) adicionar(l, meu); // o gol no estilo do gol
                        else narracao.getChildren().add(TelaPartida.linhaPasso(casa, l.relogio(), SuspensePenalti.fraseResultado(d, goleiro), "penalti-erro"));
                    });
                    long ritmo = Math.round(Velocidade.RITMO_PENALTI_MS * AcompanhamentoPartida.escala);
                    suspense = SuspensePenalti.emLinhas(linhas.size(), ritmo, i -> linhas.get(i).run(), () -> {
                        if (golSegurado != null) {
                            golSegurado = null;
                            mostrarGols();
                        }
                        emSuspense = false;
                        suspense = null;
                        processarFila();
                    });
                    return;
                }
                adicionar(l, meu);
            }
            if (!emSuspense && fila.isEmpty() && aposFila != null) {
                Runnable r = aposFila;
                aposFila = null;
                r.run();
            }
        }

        /** SIMULAR ATÉ O FIM: o suspense em andamento termina na hora. */
        void pularSuspense() {
            if (suspense != null && !suspense.acabou()) suspense.pular();
            processarFila();
        }

        /**
         * A disputa de pênaltis deste jogo (requisito): a narração é limpa e cada cobrança é narrada como o
         * pênalti no jogo, uma linha por vez no ritmo do suspense, com as bolinhas no placar. SIMULAR ATÉ O FIM
         * (ou capturas): tudo na hora, com as bolinhas.
         */
        void mostrarDisputa(br.futebas.partida.ResultadoPartida r, List<Lance> cobrancas) {
            // um pênalti do jogo ainda no suspense: a disputa espera ele terminar
            if (emSuspense || !fila.isEmpty()) {
                aposFila = () -> mostrarDisputa(r, cobrancas);
                processarFila();
                return;
            }
            List<SuspensePenalti.Cobranca> cs = TelaPartida.cobrancasDaDisputa(cobrancas);
            narracao.getChildren().clear();
            narracao.getChildren().add(TelaPartida.linhaFase("Pên.", "Disputa de pênaltis  ·  " + r.golsMandante + " x " + r.golsVisitante));
            for (SuspensePenalti.Bolinhas b : bolas) {
                b.reiniciar();
                b.setVisible(true);
                b.setManaged(true);
            }
            relogio.setText("Pên. 0 x 0");
            disputasAndando++;
            continuar.setDisable(true);
            int[] pen = new int[2];
            boolean semEspera = App.automatico || mesa == null || mesa.pulando() || mesa.velocidade() == Velocidade.INSTANTANEA;
            long ritmo = semEspera ? 0 : Math.round(Velocidade.RITMO_PENALTI_MS * AcompanhamentoPartida.escala);
            disputa = SuspensePenalti.disputaEmLinhas(cs, ritmo, (i, c, etapa, txt) -> {
                int lado = c.lado() == 1 ? 1 : 0;
                switch (etapa) {
                    case SuspensePenalti.ALTERNADAS -> narracao.getChildren().add(TelaPartida.linhaFase("Pên.", txt));
                    case SuspensePenalti.IDA -> {
                        bolas[lado].vez();
                        narracao.getChildren().add(TelaPartida.linhaPasso(lado == 0, "Pên.", txt, null));
                    }
                    case SuspensePenalti.BATIDA -> narracao.getChildren().add(TelaPartida.linhaPasso(lado == 0, "Pên.", txt, null));
                    default -> {
                        boolean gol = c.desfecho().gol();
                        bolas[lado].marcar(gol);
                        if (gol) pen[lado]++;
                        relogio.setText("Pên. " + pen[0] + " x " + pen[1]);
                        narracao.getChildren().add(TelaPartida.linhaPasso(lado == 0, "Pên.", txt, gol ? "penalti-gol" : "penalti-erro"));
                    }
                }
            }, () -> {
                disputa = null;
                narracao.getChildren().add(TelaPartida.linhaFase("Pên.", nomes[pen[0] > pen[1] ? 0 : 1] + " vence nos pênaltis: " + pen[0] + " x " + pen[1]));
                disputasAndando = Math.max(0, disputasAndando - 1);
                if (terminou && disputasAndando == 0) continuar.setDisable(false);
            });
        }

        void atualizar(AcompanhamentoPartida de, Estado e) {
            if (de != narrador) return;
            int meu = jogo.duelo() ? -1 : e.meuLado();
            for (Lance l : e.novosLances()) fila.add(new Object[] {l, meu});
            golsEstado[0] = e.golsMandante();
            golsEstado[1] = e.golsVisitante();
            processarFila();
            mostrarGols();
            if (e.terminado() && e.resultado() != null && e.resultado().penaltisMandante != null)
                relogio.setText("Pên. " + e.resultado().penaltisMandante + " x " + e.resultado().penaltisVisitante);
            else if (disputa == null) relogio.setText(e.relogio());
            if (e.terminado() && !fim) {
                fim = true;
                relogio.getStyleClass().add("parado");
                for (AcompanhamentoPartida a : jogo.lados) {
                    Lado l = lados.get(a);
                    if (l != null && l.botao != null) l.botao.setDisable(true);
                }
            }
        }
    }

    public TelaPartidaMulti(Sessao sessao) {
        this.sessao = sessao;
        VBox txt = new VBox(2, titulo, sub);
        HBox faixa = new HBox(16, txt, Ui.espaco(), resultados);
        faixa.setAlignment(Pos.CENTER_LEFT);
        faixa.getStyleClass().add("faixa-rodada");
        raiz.setTop(faixa);
        grade.setHgap(12);
        grade.setVgap(12);
        grade.setStyle("-fx-padding: 12 16 12 16;");
        raiz.setCenter(grade);

        ToggleGroup g = new ToggleGroup();
        HBox vel = new HBox();
        vel.getStyleClass().add("vel-pilula");
        vel.setAlignment(Pos.CENTER_LEFT);
        Velocidade v0 = velocidadeInicial();
        for (Velocidade v : Velocidade.visiveis()) {
            ToggleButton b = new ToggleButton(v.nome);
            b.getStyleClass().add("vel-opcao");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setToggleGroup(g);
            b.setFocusTraversable(false);
            b.setSelected(v == v0);
            b.setOnAction(e -> {
                if (!b.isSelected()) b.setSelected(true);
                velocidade(v);
            });
            botoesVelocidade.add(b);
            vel.getChildren().add(b);
        }
        vel.setMinWidth(Region.USE_PREF_SIZE);
        continuar.setDisable(true);
        for (Button b : List.of(pausar, pular, continuar)) b.setMinWidth(Region.USE_PREF_SIZE);
        Label rotVel = Ui.rotulo("Velocidade", "rotulo-rodape");
        rotVel.setMinWidth(Region.USE_PREF_SIZE);
        HBox rodape = Ui.rodape(rotVel, vel, pausar, Ui.espaco(), pular, continuar);
        rodape.getStyleClass().add("rodape-jogo");
        raiz.setBottom(rodape);
        montarSobreposicao();
        camadas.getChildren().setAll(raiz, sobreposicao);
    }

    private static Velocidade velocidadeInicial() {
        return App.automatico ? Velocidade.RAPIDA : Opcoes.velocidade();
    }

    @Override
    public Parent raiz() { return camadas; }

    // ------------------------------------------------------------------ começo
    @Override
    public void aoMostrar() {
        if (mesa != null) return;
        prepararMesa();
        App.atual().trabalhar(null, this::rodar, this::fim);
    }

    /** Testes: a rodada sem o App (o motor numa thread; o fim na thread da tela). */
    void rodarSemApp() {
        if (mesa != null) return;
        prepararMesa();
        Thread t = new Thread(() -> {
            Fluxo.Rodada r = rodar();
            Platform.runLater(() -> fim(r));
        }, "rodada-multi-teste");
        t.setDaemon(true);
        t.start();
    }

    private Fluxo.Rodada rodar() {
        Fluxo.Rodada r = sessao.fluxo.jogarSemanaMulti(mesa);
        if (r.fimDeTemporada() != null) TelaFimTemporada.preaquecer(sessao, r.fimDeTemporada());
        return r;
    }

    private void prepararMesa() {
        var jogo = sessao.jogo;
        sub.setText("Semana " + jogo.semana() + " de " + jogo.ano() + "  ·  " + String.join(" × ", jogo.tecnicos().stream().map(t -> t.nome).toList()));
        mesa = new MesaAoVivo(velocidadeInicial(), this::ordemDoClube, this::criarAcompanhamento);
        mesa.fabricaKings(this::criarAcompKings);
        mesa.ouvinte(new MesaAoVivo.Ouvinte() {
            @Override
            public void grupo(List<MesaAoVivo.Jogo> jogos) {
                Platform.runLater(() -> {
                    montarGrupo(jogos);
                    mesa.colunasProntas();
                });
            }

            @Override
            public void antesDoGrupo(List<String> clubes) {
                Platform.runLater(() -> avisarProximoGrupo(clubes));
            }
        });
    }

    /** Ordem do técnico do clube nas vezes (as pausas seguem essa ordem). */
    private int ordemDoClube(String clubeId) {
        List<Tecnico> ts = sessao.jogo.tecnicos();
        for (int i = 0; i < ts.size(); i++) if (clubeId != null && (clubeId.equals(ts.get(i).clubeId) || clubeId.equals(ts.get(i).kingsTimeId))) return i;
        return 99;
    }

    /** Na thread do motor: o acompanhamento de um técnico (o ouvinte leva cada foto do jogo para a tela). */
    private AcompanhamentoPartida criarAcompanhamento(String clubeId) {
        AcompanhamentoPartida[] ref = new AcompanhamentoPartida[1];
        AcompanhamentoPartida a = new AcompanhamentoPartida(clubeId, velocidadeInicial(), e -> Platform.runLater(() -> atualizar(ref[0], e)));
        ref[0] = a;
        a.esperarTecnico(true);
        // regra do jogo: no intervalo, cada técnico mexe no time (na ordem); lesão e expulsão pelas Opções.
        // Pênalti a favor: o técnico escolhe o batedor na parte dele da tela (regra do jogo), pelas Opções.
        a.configurarPausas(true, !App.automatico && Opcoes.pausarLesao(), !App.automatico && Opcoes.pausarExpulsao(),
                App.automatico || Opcoes.escolherBatedor(), false, false);
        // disputa de pênaltis: cada técnico escolhe a ordem dele (na parte dele da tela); depois, as cobranças narradas
        a.aoPedirOrdem(p -> Platform.runLater(() -> abrirOrdem(lados.get(a), p)));
        a.aoDisputar((r, cs) -> Platform.runLater(() -> {
            Lado l = lados.get(a);
            if (l != null && l.quadro != null) l.quadro.mostrarDisputa(r, cs);
        }));
        Tecnico t = sessao.jogo.mundo().tecnicoDoClube(clubeId);
        lados.put(a, new Lado(a, clubeId, t == null ? "Técnico" : t.nome));
        return a;
    }

    /** Monta as partes da tela: 2 jogos lado a lado; 3 ou 4 em 2×2; mais, em 3 colunas. */
    private void montarGrupo(List<MesaAoVivo.Jogo> jogos) {
        quadros.clear();
        quadrosKings.clear();
        grade.getChildren().clear();
        grade.getColumnConstraints().clear();
        grade.getRowConstraints().clear();
        int n = jogos.size();
        int cols = n <= 2 ? n : n <= 4 ? 2 : 3;
        int linhas = (n + cols - 1) / Math.max(1, cols);
        for (int c = 0; c < cols; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setHgrow(Priority.ALWAYS);
            grade.getColumnConstraints().add(cc);
        }
        for (int r = 0; r < linhas; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setPercentHeight(100.0 / linhas);
            rc.setVgrow(Priority.ALWAYS);
            grade.getRowConstraints().add(rc);
        }
        for (int i = 0; i < n; i++) {
            if (jogos.get(i).kings) {
                QuadroKings q = new QuadroKings(jogos.get(i));
                quadrosKings.add(q);
                grade.add(q.raiz, i % cols, i / cols);
                continue;
            }
            Quadro q = new Quadro(jogos.get(i));
            quadros.add(q);
            grade.add(q.raiz, i % cols, i / cols);
        }
        titulo.setText(n == 1 && jogos.get(0).duelo() ? "DUELO AO VIVO" : "RODADA AO VIVO  ·  " + n + (n == 1 ? " JOGO" : " JOGOS"));
    }

    /**
     * 2º jogo de técnico na mesma semana (regra do jogo: "sempre tem que voltar para o menu após"): o
     * fim deste grupo fica na tela e "Voltar à central" leva cada técnico desse jogo à vez dele; o
     * último "Jogar" volta para esta tela e o grupo começa ({@link #retomarGrupo}).
     */
    private void avisarProximoGrupo(List<String> clubes) {
        clubesDoProximo = clubes;
        filaPainel.clear();
        if (painelDe != null || kingsDe != null) fecharPainel();
        for (QuadroKings q : quadrosKings) {
            q.fim = true;
            q.relogio.setText("Fim");
        }
        if (App.automatico && App.atual() == null) { // testes sem o App: segue direto
            mesa.liberarGrupo();
            return;
        }
        entreGrupos = true;
        for (Quadro q : quadros) {
            q.relogio.getStyleClass().add("parado");
            if (!q.fim) q.relogio.setText("Fim");
        }
        pausar.setDisable(true);
        pular.setDisable(true);
        continuar.setText("Voltar à central");
        continuar.setDisable(false);
        List<String> nomes = new ArrayList<>();
        for (String id : clubes) {
            Tecnico tc = sessao.jogo.mundo().tecnicoDoClube(id);
            if (tc != null && !nomes.contains(tc.nome)) nomes.add(tc.nome);
        }
        titulo.setText("OUTRO JOGO NESTA SEMANA  ·  " + String.join(" e ", nomes) + " voltam à central");
        if (App.automatico) continuar();
    }

    /** Os técnicos do 2º grupo mandaram jogar: a tela volta e os jogos começam. */
    public void retomarGrupo() {
        if (!entreGrupos) return;
        entreGrupos = false;
        continuar.setText("Continuar");
        continuar.setDisable(true);
        pausar.setDisable(false);
        pular.setDisable(false);
        mesa.liberarGrupo();
    }

    /** Clubes humanos do próximo grupo da semana (a {@code App} dá a vez só aos técnicos deles). */
    public List<String> clubesDoProximoGrupo() { return clubesDoProximo; }

    public boolean entreGrupos() { return entreGrupos; }


    // ================================================================== Kings (7x7) no multi
    /** Um técnico da Kings num jogo ao vivo: acompanhamento, cartas/presidente (PainelKingsLateral) e trocas de 7. */
    private final class LadoKings {
        final br.futebas.ui.modelo.AcompanhamentoKings acomp;
        final String timeId;
        final String tecnico;
        PainelKingsLateral lateral;
        /** A carta e o pênalti do presidente deste técnico (botões pequenos ao lado de "Tática e trocas"). */
        br.futebas.ui.comp.BotoesKings botoesKings;
        TrocasAoVivo trocas;
        QuadroKings quadro;
        Button botao;
        br.futebas.ui.modelo.AcompanhamentoKings.Estado ultimo;
        String pausaAtendida;
        final Map<String, Integer> idDe = new java.util.HashMap<>();
        final List<br.futebas.kings.LigaKings.AtletaKings> porId = new ArrayList<>();

        LadoKings(br.futebas.ui.modelo.AcompanhamentoKings acomp, String timeId, String tecnico) {
            this.acomp = acomp;
            this.timeId = timeId;
            this.tecnico = tecnico;
        }

        int id(br.futebas.kings.LigaKings.AtletaKings a) {
            return idDe.computeIfAbsent(a.chave(), k -> {
                porId.add(a);
                return porId.size() - 1;
            });
        }
    }

    /** A parte da tela de um jogo da Kings: placar, fase, narração, a coluna da Kings de cada técnico e os botões. */
    private final class QuadroKings {
        final MesaAoVivo.Jogo jogo;
        final String[] nomes = {"", ""};
        final Label golsA = Ui.rotulo("0", "placar-gols"), golsB = Ui.rotulo("0", "placar-gols");
        final Label relogio = Ui.rotulo("0'", "placar-relogio");
        final Label fase = Ui.rotulo("", "kings-fase");
        final VBox narracao = new VBox();
        final ScrollPane rolagem = new ScrollPane(narracao);
        final StackPane raiz = new StackPane();
        /** Lugar dos botões de cada técnico (Tática e trocas; ícones da carta e do presidente ficam ao lado). */
        final HBox botoes = new HBox(10);
        LadoKings narrador;
        int vistos;
        Object ultimaCobranca;
        boolean fim;
        /** Shoot out do desempate: as bolinhas no placar e as cobranças na narração (o sistema da disputa de pênaltis). */
        final SuspensePenalti.Bolinhas[] bolas = {new SuspensePenalti.Bolinhas(6), new SuspensePenalti.Bolinhas(6)};
        final ShootoutNarrado shootout = new ShootoutNarrado(narracao, bolas, relogio);

        QuadroKings(MesaAoVivo.Jogo jogo) {
            this.jogo = jogo;
            var tA = sessao.jogo.timeKings(jogo.mandanteId);
            var tB = sessao.jogo.timeKings(jogo.visitanteId);
            nomes[0] = tA == null ? jogo.mandanteId : tA.nome;
            nomes[1] = tB == null ? jogo.visitanteId : tB.nome;
            Label cab = Ui.rotulo("KINGS LEAGUE  ·  7x7" + (jogo.duelo() ? "  ·  DUELO DE TÉCNICOS" : ""), "coluna-titulo");
            HBox numeros = new HBox(10, golsA, Ui.rotulo("–", "placar-gols"), golsB);
            numeros.setAlignment(Pos.CENTER);
            VBox meio = new VBox(2, numeros, relogio, fase);
            meio.setAlignment(Pos.CENTER);
            meio.setMinWidth(120);
            VBox ladoA = ladoTime(jogo.mandanteId, nomes[0]), ladoB = ladoTime(jogo.visitanteId, nomes[1]);
            ladoA.getChildren().add(bolas[0]);
            ladoB.getChildren().add(bolas[1]);
            HBox placar = new HBox(8, ladoA, meio, ladoB);
            placar.setAlignment(Pos.CENTER);
            placar.getStyleClass().addAll("placar", "placar-cartao");
            rolagem.setFitToWidth(true);
            rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            rolagem.getStyleClass().add("narracao-fundo");
            narracao.getStyleClass().add("narracao-fundo");
            narracao.heightProperty().addListener((o, a, b) -> rolagem.setVvalue(1.0));
            HBox.setHgrow(rolagem, Priority.ALWAYS);
            HBox meioLinha = new HBox(8);
            Node esq = new Region(), dir = new Region();
            for (br.futebas.ui.modelo.AcompanhamentoKings a : jogo.ladosKings) {
                LadoKings l = ladosKings.get(a);
                if (l == null) continue;
                if (narrador == null) narrador = l;
                l.quadro = this;
                l.lateral = new PainelKingsLateral();
                var meuT = sessao.jogo.timeKings(l.timeId);
                String rivalId = l.timeId.equals(jogo.mandanteId) ? jogo.visitanteId : jogo.mandanteId;
                l.lateral.novoJogo(Ui.escudo(rivalId, 14), sessao.jogo.mundo().nomeParticipante(rivalId));
                l.botoesKings = new br.futebas.ui.comp.BotoesKings(l.acomp);
                l.botoesKings.novoJogo(meuT == null ? null : meuT.batedorPresidente != null ? meuT.batedorPresidente : meuT.presidente);
                l.lateral.setPrefWidth(220);
                l.lateral.setMinWidth(180);
                l.botao = Ui.botao("⇄  Tática e trocas · " + l.tecnico, () -> abrirTrocasKings(l, "Tática e trocas", false), "rodape-botao");
                l.botao.setMinWidth(Region.USE_PREF_SIZE);
                l.trocas = trocasKings(l);
                l.trocas.aoConfirmar(TelaPartidaMulti.this::fecharPainel);
                HBox barra = new HBox(6, l.botao, l.botoesKings);
                barra.setAlignment(Pos.CENTER);
                if (l.timeId.equals(jogo.mandanteId)) esq = barra;
                else dir = barra;
            }
            for (br.futebas.ui.modelo.AcompanhamentoKings a : jogo.ladosKings) {
                LadoKings l = ladosKings.get(a);
                if (l != null && l.timeId.equals(jogo.mandanteId)) meioLinha.getChildren().add(l.lateral);
            }
            meioLinha.getChildren().add(rolagem);
            for (br.futebas.ui.modelo.AcompanhamentoKings a : jogo.ladosKings) {
                LadoKings l = ladosKings.get(a);
                if (l != null && !l.timeId.equals(jogo.mandanteId)) meioLinha.getChildren().add(l.lateral);
            }
            VBox.setVgrow(meioLinha, Priority.ALWAYS);
            botoes.setAlignment(Pos.CENTER);
            botoes.getChildren().addAll(esq, Ui.espaco(), dir);
            VBox conteudo = new VBox(8, cab, placar, meioLinha, botoes);
            conteudo.getStyleClass().add("bloco-jogo");
            conteudo.setStyle("-fx-padding: 10;");
            raiz.getChildren().setAll(conteudo);
            raiz.setMinWidth(320);
        }

        private VBox ladoTime(String id, String nome) {
            Label n = Ui.rotulo(nome, "placar-time");
            n.setWrapText(true);
            n.setMinWidth(0);
            n.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            VBox v = new VBox(4, Ui.escudo(id, 44), n);
            Tecnico t = sessao.jogo.mundo().tecnicoDoTimeKings(id);
            if (t != null) v.getChildren().add(Ui.rotulo("Técnico: " + t.nome, "texto-pequeno"));
            v.setAlignment(Pos.CENTER);
            v.setPrefWidth(160);
            v.setMinWidth(90);
            HBox.setHgrow(v, Priority.ALWAYS);
            return v;
        }

        void atualizar(LadoKings de, br.futebas.ui.modelo.AcompanhamentoKings.Estado e) {
            if (de != narrador) return;
            golsA.setText(String.valueOf(e.golsA()));
            golsB.setText(String.valueOf(e.golsB()));
            if (!shootout.ativo()) relogio.setText(fim ? "Fim" : e.minuto() + "'");
            fase.setText(KingsAoVivo.textoFase(e.fase(), e.dado()));
            br.futebas.kings.AoVivoKings.Cobranca kc = e.cobranca();
            if ("shootouts".equals(e.pausa()) || kc != null && "disputa".equals(kc.tipo())) shootout.comecar(nomes[0], nomes[1]);
            List<br.futebas.partida.EventoJogo> ev = e.eventos();
            for (int i = vistos; i < ev.size(); i++) {
                br.futebas.partida.EventoJogo x = ev.get(i);
                if ("disputa_gol".equals(x.tipo()) || "disputa_perdido".equals(x.tipo())) {
                    // o shoot out já vai na narração cobrança a cobrança: o evento só garante a bolinha
                    shootout.comecar(nomes[0], nomes[1]);
                    shootout.evento(x.lado(), "disputa_gol".equals(x.tipo()));
                    continue;
                }
                Node linha = x.gol() ? TelaPartida.linhaGol(x.lado() == 0, x.minuto() + "'", nomes[Math.max(0, Math.min(1, x.lado()))], x.jogador(), null, null)
                        : TelaPartida.linhaEvento(x.lado() == 0, x.minuto() + "'", x.jogador(), x.tipo().replace('_', ' '), x.tipo(), null);
                narracao.getChildren().add(linha);
                if (x.gol() && animarGols()) {
                    TelaPartida.animarGol(linha);
                    TelaPartida.pulsar(x.lado() == 0 ? golsA : golsB);
                }
            }
            vistos = ev.size();
            // cobrança (pênalti da carta, do presidente, shootout): as linhas do suspense na narração
            br.futebas.kings.AoVivoKings.Cobranca k = e.cobranca();
            if (k != null && k != ultimaCobranca && "disputa".equals(k.tipo())) {
                ultimaCobranca = k;
                SuspensePenalti.Desfecho d = "GOL".equals(k.resultado()) ? SuspensePenalti.Desfecho.GOL
                        : "DEFENDEU".equals(k.resultado()) ? SuspensePenalti.Desfecho.DEFESA : SuspensePenalti.desfecho(false, "fora");
                shootout.cobranca(k.lado(), k.cobrador(), d, k.goleiro(),
                        App.automatico || mesa.pulando() ? 0 : AcompanhamentoPartida.Velocidade.RITMO_PENALTI_MS);
            } else if (k != null && k != ultimaCobranca) {
                ultimaCobranca = k;
                String[] passos = {k.cobrador() + " vai para a bola…", "Bateu…", k.resultado()};
                boolean casa = k.lado() == 0;
                SuspensePenalti.emLinhas(passos.length, App.automatico || mesa.pulando() ? 0 : AcompanhamentoPartida.Velocidade.RITMO_PENALTI_MS,
                        i -> narracao.getChildren().add(TelaPartida.linhaPasso(casa, "Pên.", passos[i], null)), null);
            }
        }
    }

    /** Na thread do motor: o acompanhamento do time da Kings de um técnico. */
    private br.futebas.ui.modelo.AcompanhamentoKings criarAcompKings(String timeId, int lado) {
        br.futebas.ui.modelo.AcompanhamentoKings[] ref = new br.futebas.ui.modelo.AcompanhamentoKings[1];
        ref[0] = new br.futebas.ui.modelo.AcompanhamentoKings(lado, e -> Platform.runLater(() -> atualizarKings(ref[0], e)));
        // shoot out: cada técnico escolhe a ordem dele, na parte dele da tela
        ref[0].aoPedirOrdem(sug -> Platform.runLater(() -> abrirOrdemKings(ladosKings.get(ref[0]), sug)));
        Tecnico t = sessao.jogo.mundo().tecnicoDoTimeKings(timeId);
        ladosKings.put(ref[0], new LadoKings(ref[0], timeId, t == null ? "Técnico" : t.nome));
        return ref[0];
    }

    /** Técnico da Kings escolhendo a ordem do shoot out. */
    private LadoKings ordemKingsDe;

    /** Shoot out: a tela de ordem na parte do jogo desse técnico, com o nome dele no topo (no duelo, um depois do outro). */
    private void abrirOrdemKings(LadoKings l, List<br.futebas.kings.LigaKings.AtletaKings> sug) {
        if (l == null || l.quadro == null || mesa.pulando()) {
            if (l != null) l.acomp.responderOrdem(null);
            return;
        }
        if (painelDe != null || batedorDe != null || kingsDe != null || ordemDe != null || ordemKingsDe != null) {
            filaPainel.add(() -> abrirOrdemKings(l, sug));
            return;
        }
        ordemKingsDe = l;
        var tk = sessao.jogo.timeKings(l.timeId);
        Label cab = Ui.rotulo(l.tecnico.toUpperCase(java.util.Locale.ROOT) + " — ordem do shoot out" + (tk == null ? "" : " (" + tk.nome + ")"), "texto-forte");
        cab.setStyle("-fx-text-fill: white;");
        cab.setWrapText(true);
        HBox topo = new HBox(cab);
        topo.setStyle("-fx-background-color: #26313c; -fx-padding: 8 12 8 12; -fx-background-radius: 6;");
        OrdemPenaltis o = ShootoutNarrado.ordem(new VBox(6, topo, Ui.rotulo("Vez de " + l.tecnico + ": escolha a ordem dos batedores", "texto-medio")),
                sug, ch -> responderOrdemKings(l, ch));
        o.setStyle("-fx-padding: 12;");
        ordemAberta = o;
        l.quadro.raiz.getChildren().add(o);
        if (App.automatico) {
            javafx.animation.PauseTransition t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
            t.setOnFinished(x -> {
                if (ordemAberta == o) o.confirmar();
            });
            t.play();
        }
    }

    private void responderOrdemKings(LadoKings l, List<String> chaves) {
        if (ordemKingsDe != l) return;
        OrdemPenaltis o = ordemAberta;
        ordemKingsDe = null;
        ordemAberta = null;
        if (o != null && l.quadro != null) l.quadro.raiz.getChildren().remove(o);
        l.acomp.responderOrdem(chaves);
        Runnable prox = filaPainel.poll();
        if (prox != null) prox.run();
    }

    private void atualizarKings(br.futebas.ui.modelo.AcompanhamentoKings a, br.futebas.ui.modelo.AcompanhamentoKings.Estado e) {
        LadoKings l = a == null ? null : ladosKings.get(a);
        if (l == null) return;
        l.ultimo = e;
        if (l.quadro != null) l.quadro.atualizar(l, e);
        if (l.botoesKings != null) l.botoesKings.atualizar(e);
        if (l.lateral != null) {
            l.lateral.fase(KingsAoVivo.textoFase(e.fase(), e.dado()), e.minuto());
            l.lateral.armas(KingsAoVivo.armas(e, null, null));
        }
        if (kingsDe == l) atualizarTrocasKings(l);
        // intervalo: a vez deste técnico de mexer no time (a mesa já pôs na ordem)
        String chave = e.pausa() == null ? null : e.pausa() + "#" + e.minuto();
        if ("intervalo".equals(e.pausa()) && !chave.equals(l.pausaAtendida) && !mesa.pulando()) {
            l.pausaAtendida = chave;
            abrirTrocasKings(l, "Intervalo", true);
        }
    }

    /** As trocas de 7 da Kings (ilimitadas, de volta), na mesma tela de trocas do futebol. */
    private TrocasAoVivo trocasKings(LadoKings l) {
        return new TrocasAoVivo(sessao, new TrocasAoVivo.Fonte() {
            @Override
            public void aplicar(List<TrocasAoVivo.Mudanca> ms) {
                for (TrocasAoVivo.Mudanca x : ms) {
                    if (!x.substituicao() || x.a() >= l.porId.size() || x.b() >= l.porId.size()) continue;
                    l.acomp.substituir(l.porId.get(x.a()).chave(), l.porId.get(x.b()).chave());
                }
            }

            @Override public boolean trocaDePosicao() { return false; }

            @Override
            public Node foto(int i, double w, double h) {
                if (i < 0 || i >= l.porId.size()) return null;
                var a = l.porId.get(i);
                var j = a.jogadorId == null ? null : sessao.jogo.mundo().jogador(a.jogadorId);
                String foto = j != null ? sessao.jogo.foto(j.id) : a.foto;
                return br.futebas.ui.comp.Foto.kings(foto, j == null ? null : j.dbId, l.timeId, a.nome, w, h);
            }

            @Override public double encaixe(int i, br.futebas.mundo.Posicao natural, br.futebas.mundo.Posicao vaga) { return 1; }

            @Override
            public String cor() {
                var tk = sessao.jogo.timeKings(l.timeId);
                return tk == null ? null : tk.cor1;
            }

            @Override public Integer capitao() { return -1; }
        });
    }

    private void atualizarTrocasKings(LadoKings l) {
        if (l.trocas == null || l.acomp.meuLado() < 0) return;
        List<AcompanhamentoPartida.Atleta> campo = new ArrayList<>(), banco = new ArrayList<>();
        for (Object[] o : l.acomp.elencoAoVivo) {
            var a = (br.futebas.kings.LigaKings.AtletaKings) o[0];
            br.futebas.mundo.Posicao p = a.posicao == null ? br.futebas.mundo.Posicao.MC : a.posicao;
            var x = new AcompanhamentoPartida.Atleta(l.id(a), a.nome, p, p, (int) Math.round(a.forca), false, (int) Math.round((Double) o[2]));
            ((Boolean) o[1] ? campo : banco).add(x);
        }
        int min = l.ultimo == null ? 0 : l.ultimo.minuto();
        var q = l.quadro;
        l.trocas.atualizar(new AcompanhamentoPartida.Estado(min, false, false, min, 0, false, q == null ? "" : q.nomes[0], q == null ? "" : q.nomes[1],
                q == null ? null : q.jogo.mandanteId, q == null ? null : q.jogo.visitanteId, 0, 0, List.of(), l.acomp.meuLado(), List.copyOf(campo),
                List.copyOf(banco), java.util.Set.of(), List.copyOf(campo), List.of(), java.util.Map.of(), 0, Integer.MAX_VALUE, null, null, null,
                null, List.of(), null));
    }

    /**
     * Trocas da Kings de um técnico: na mesma camada do futebol, com o nome dele e a cor do time no
     * topo; um técnico de cada vez (fila). {@code pedido}: o intervalo (o motor espera ele seguir).
     */
    private void abrirTrocasKings(LadoKings l, String tit, boolean pedido) {
        if (!pedido && (terminou || l.quadro == null || l.quadro.fim)) return;
        if (painelDe != null || batedorDe != null || kingsDe != null) {
            if (kingsDe != l || pedido) filaPainel.add(() -> abrirTrocasKings(l, tit, pedido));
            return;
        }
        kingsDe = l;
        kingsPedido = pedido;
        if (!pedido) mesa.pausar(true);
        mostrarPausado(true);
        var tk = sessao.jogo.timeKings(l.timeId);
        tituloPainel.setText(l.tecnico.toUpperCase(java.util.Locale.ROOT) + " — " + tit + (tk == null ? "" : " (" + tk.nome + ")"));
        tituloPainel.setStyle((tk == null ? "-fx-background-color: #26313c;" : br.futebas.ui.comp.Tema.estiloCabecalho(tk.cor1, tk.cor2))
                + "-fx-text-fill: white; -fx-padding: 4 12 4 12; -fx-background-radius: 6;");
        motivoPainel.setText("Kings · trocas ilimitadas (quem sai pode voltar)");
        fotoPainel.getChildren().clear();
        fotoPainel.setVisible(false);
        fotoPainel.setManaged(false);
        motivoPainel.setVisible(true);
        motivoPainel.setManaged(true);
        taticaPainel.getChildren().clear();
        trocasLugar.getChildren().setAll(l.trocas.raiz());
        confirmarLugar.getChildren().setAll(l.trocas.botaoSubstituir());
        if (alturaCampo > 0) l.trocas.tamanho(alturaCampo);
        l.trocas.limpar();
        atualizarTrocasKings(l);
        atualizarResumoKings(l);
        sobreposicao.setVisible(true);
        if (App.automatico) {
            javafx.animation.PauseTransition t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(800));
            t.setOnFinished(x -> {
                if (kingsDe == l) fecharPainel();
            });
            t.play();
        }
    }

    private void fecharKings() {
        LadoKings l = kingsDe;
        if (l.trocas.temPendentes()) {
            if (App.automatico || mesa.pulando() || terminou) l.trocas.confirmar();
            else {
                int r = Ui.modal("Trocas não confirmadas", Ui.rotulo("Há " + l.trocas.pendentes().size()
                        + " troca(s) preparada(s). Confirmar antes de voltar ao jogo?", "texto-medio"), "Confirmar", "Descartar");
                if (r == 0) l.trocas.confirmar();
                else if (r == 1) l.trocas.descartar();
                else return;
            }
        }
        kingsDe = null;
        sobreposicao.setVisible(false);
        l.trocas.limpar();
        if (kingsPedido) l.acomp.seguir();
        else mesa.pausar(false);
        mostrarPausado(mesa.pausado());
        Runnable prox = filaPainel.poll();
        if (prox != null) prox.run();
    }

    // ------------------------------------------------------------------ fotos do jogo
    private void atualizar(AcompanhamentoPartida a, Estado e) {
        Lado l = a == null ? null : lados.get(a);
        if (l == null) return;
        l.ultimo = e;
        if (l.quadro != null) l.quadro.atualizar(a, e);
        if (painelDe == l) atualizarPainel(l);
        AcompanhamentoPartida.Pedido ped = e.pedido();
        if (ped != null && ped != l.atendido && !mesa.pulando() && !e.terminado()) {
            l.atendido = ped;
            if (ped.candidatos() != null) {
                abrirBatedor(l, ped.candidatos());
                return;
            }
            String quem = ped.jogador() == null ? "" : ped.jogador();
            switch (ped.motivo()) {
                case INTERVALO -> abrirPainel(l, "Intervalo", e.mandante() + " " + e.golsMandante() + " x " + e.golsVisitante() + " " + e.visitante(), true);
                case EXPULSAO -> abrirPainel(l, "Expulsão", quem, true, ped.jogadorId());
                case LESAO -> abrirPainel(l, "Lesão", quem, true, ped.jogadorId());
                default -> a.pausar(false);
            }
        }
    }

    // ------------------------------------------------------------------ tática e trocas
    private void montarSobreposicao() {
        sobreposicao.getStyleClass().add("sobreposicao");
        sobreposicao.setVisible(false);
        taticaPainel.setAlignment(Pos.CENTER_LEFT);
        Button voltar = Ui.botaoTecla("Voltar", "Esc", this::fecharPainel);
        Label ajuda = Ui.info("Arraste um reserva para a vaga (ou clique em quem sai e em quem entra); dois em campo trocam de posição. Nada muda até Confirmar.");
        // a mesma camada do jogo de um técnico: quase a janela toda, números e tabela ao vivo à direita
        resumo = new ResumoAoVivo(sessao);
        TelaPartida.montarCaixaTrocas(sobreposicao, TelaPartida.cabecalhoCamada(tituloPainel, fotoPainel, motivoPainel, ajuda), Ui.painel(null, taticaPainel),
                trocasLugar, resumo.raiz(), Ui.linha(10, Ui.espaco(), voltar, confirmarLugar), h -> {
                    alturaCampo = h;
                    if (painelDe != null) painelDe.trocas.tamanho(h);
                    if (kingsDe != null) kingsDe.trocas.tamanho(h);
                });
    }

    private ResumoAoVivo resumo;
    /** Altura do campo na camada (acompanha a janela; vale para o técnico que abrir a seguir). */
    private double alturaCampo;

    /** Números e tabela ao vivo do jogo de um técnico: os placares de agora de todos os jogos da rodada. */
    private void atualizarResumo(Lado l) {
        Estado e = l.ultimo;
        if (resumo == null || e == null) return;
        List<Object[]> placares = new ArrayList<>();
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (Lado x : lados.values()) {
            Estado q = x.ultimo;
            if (q == null || q.mandanteId() == null || !vistos.add(q.mandanteId() + "|" + q.visitanteId())) continue;
            if (q.minuto() > 0 || q.terminado()) placares.add(new Object[] {q.mandanteId(), q.visitanteId(), q.golsMandante(), q.golsVisitante()});
        }
        int min = e.terminado() ? 90 : e.intervalo() ? 45 : Math.min(90, e.minuto());
        if (e.paralelos() != null && min > 0) {
            for (br.futebas.partida.JogoParalelo j : e.paralelos()) {
                if (!vistos.add(j.mandanteId() + "|" + j.visitanteId())) continue;
                int[] p = e.terminado() ? new int[] {j.golsMandante(), j.golsVisitante()} : j.placarAte(min);
                placares.add(new Object[] {j.mandanteId(), j.visitanteId(), p[0], p[1]});
            }
        }
        resumo.mostrar(e.mandanteId(), e.visitanteId(), l.acomp.estatisticas(), e.relogio(), placares, l.clubeId);
    }

    /** Kings: os números do jogo e a tabela da competição com o placar de agora. */
    private void atualizarResumoKings(LadoKings l) {
        if (resumo == null || l.quadro == null) return;
        var q = l.quadro;
        var e = l.ultimo;
        List<Object[]> placares = new ArrayList<>();
        if (e != null && e.minuto() > 0) placares.add(new Object[] {q.jogo.mandanteId, q.jogo.visitanteId, e.golsA(), e.golsB()});
        resumo.mostrar(q.jogo.mandanteId, q.jogo.visitanteId, l.acomp.estatisticas(), e == null ? "" : e.minuto() + "'", placares, l.timeId);
    }

    /**
     * Abre a tática e as trocas de um técnico. Se outro técnico está com a camada aberta, entra na fila
     * (um de cada vez). {@code pedido}: o motor parou para ele (intervalo, lesão, expulsão).
     */
    private void abrirPainel(Lado l, String tit, String motivo, boolean pedido) {
        abrirPainel(l, tit, motivo, pedido, null);
    }

    /** A foto de quem motivou a pausa (lesionado/expulso) ao lado do título. */
    private final StackPane fotoPainel = new StackPane();

    private void abrirPainel(Lado l, String tit, String motivo, boolean pedido, Integer jogadorId) {
        if (l.ultimo == null || l.ultimo.meuLado() < 0) {
            if (pedido) l.acomp.pausar(false);
            return;
        }
        if (!pedido && (terminou || l.ultimo.terminado())) return;
        if (painelDe != null || batedorDe != null || kingsDe != null || ordemDe != null) {
            if (painelDe != l || pedido) filaPainel.add(() -> abrirPainel(l, tit, motivo, pedido, jogadorId));
            return;
        }
        painelDe = l;
        painelPedido = pedido;
        if (!pedido) mesa.pausar(true);
        mostrarPausado(true);
        // de quem é a tela, na cor do time dele (regra do jogo)
        tituloPainel.setText(cabecalho(l, tit));
        tituloPainel.setStyle(corDoLado(l) + "-fx-text-fill: white; -fx-padding: 4 12 4 12; -fx-background-radius: 6;");
        motivoPainel.setText(motivo);
        motivoPainel.setVisible(motivo != null && !motivo.isEmpty());
        motivoPainel.setManaged(motivoPainel.isVisible());
        fotoPainel.getChildren().clear();
        if (jogadorId != null) fotoPainel.getChildren().add(l.trocas.fotoDe(jogadorId, 30, 30));
        fotoPainel.setVisible(jogadorId != null);
        fotoPainel.setManaged(jogadorId != null);
        trocasLugar.getChildren().setAll(l.trocas.raiz());
        confirmarLugar.getChildren().setAll(l.trocas.botaoSubstituir());
        if (alturaCampo > 0) l.trocas.tamanho(alturaCampo);
        l.trocas.limpar();
        atualizarPainel(l);
        sobreposicao.setVisible(true);
        if (App.automatico) {
            javafx.animation.PauseTransition t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1400));
            t.setOnFinished(x -> {
                if (painelDe == l) fecharPainel();
            });
            t.play();
        }
    }

    /** "ANA — Intervalo (Flamengo)": de quem é a tela. */
    private String cabecalho(Lado l, String o) {
        Clube c = sessao.jogo.clube(l.clubeId);
        return l.tecnico.toUpperCase(java.util.Locale.ROOT) + " — " + o + (c == null ? "" : " (" + c.nome + ")");
    }

    private String corDoLado(Lado l) {
        Clube c = sessao.jogo.clube(l.clubeId);
        return c == null ? "-fx-background-color: #26313c;" : br.futebas.ui.comp.Tema.estiloCabecalho(c.cor1, c.cor2);
    }

    /**
     * Pênalti a favor: a escolha do batedor aparece na parte do jogo desse técnico, com o nome dele e a
     * cor do time no topo; os outros jogos esperam (a {@link MesaAoVivo} segura a vez).
     */
    /** A escolha do batedor aberta (e o título de quem é a vez, para os testes). */
    private EscolhaBatedor escolhaBatedor;
    private String tituloBatedor;

    private void abrirBatedor(Lado l, List<AcompanhamentoPartida.Atleta> candidatos) {
        if (l.quadro == null) {
            l.acomp.responderBatedor(null);
            return;
        }
        if (painelDe != null || batedorDe != null || kingsDe != null || ordemDe != null) {
            filaPainel.add(() -> abrirBatedor(l, candidatos));
            return;
        }
        batedorDe = l;
        mostrarPausado(true);
        Clube clube = sessao.jogo.clube(l.clubeId);
        Integer oficial = clube == null || clube.tatica == null ? null : clube.tatica.cobradorPenalti;
        tituloBatedor = cabecalho(l, "escolha o batedor");
        escolhaBatedor = new EscolhaBatedor(clube, l.tecnico, candidatos, oficial, l.trocas::fotoDe, id -> responderBatedor(l, id));
        VBox c = l.quadro.camadaBatedor;
        c.getChildren().setAll(escolhaBatedor);
        c.setVisible(true);
        if (App.automatico) {
            javafx.animation.PauseTransition t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
            t.setOnFinished(x -> {
                if (batedorDe == l) responderBatedor(l, candidatos.isEmpty() ? null : candidatos.get(0).id());
            });
            t.play();
        }
    }

    private void responderBatedor(Lado l, Integer id) {
        if (batedorDe != l) return;
        batedorDe = null;
        escolhaBatedor = null;
        l.quadro.camadaBatedor.setVisible(false);
        l.quadro.camadaBatedor.getChildren().clear();
        l.acomp.responderBatedor(id);
        mostrarPausado(mesa.pausado());
        Runnable prox = filaPainel.poll();
        if (prox != null) prox.run();
    }

    /**
     * Disputa de pênaltis: a escolha da ordem dos batedores aparece na parte do jogo desse técnico, com o
     * nome dele e a cor do time no topo (no duelo, primeiro o mandante, depois o visitante). Já vem com a
     * sugestão automática; o motor espera a confirmação.
     */
    private void abrirOrdem(Lado l, AcompanhamentoPartida.PedidoOrdem p) {
        if (l == null || l.quadro == null || mesa.pulando()) {
            if (l != null) l.acomp.responderOrdem(null);
            return;
        }
        if (painelDe != null || batedorDe != null || kingsDe != null || ordemDe != null) {
            filaPainel.add(() -> abrirOrdem(l, p));
            return;
        }
        ordemDe = l;
        Label cab = Ui.rotulo(cabecalho(l, "ordem dos pênaltis"), "texto-forte");
        cab.setStyle("-fx-text-fill: white;");
        cab.setWrapText(true);
        HBox topo = new HBox(cab);
        topo.setStyle(corDoLado(l) + "-fx-padding: 8 12 8 12; -fx-background-radius: 6;");
        Label vez = Ui.rotulo("Vez de " + l.tecnico + ": " + p.time() + " x " + p.adversario(), "texto-medio");
        OrdemPenaltis o = new OrdemPenaltis(new VBox(6, topo, vez), p.sugestao(), ids -> responderOrdem(l, ids));
        o.setStyle("-fx-padding: 12;");
        ordemAberta = o;
        l.quadro.raiz.getChildren().add(o);
        if (App.automatico) {
            javafx.animation.PauseTransition t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
            t.setOnFinished(x -> {
                if (ordemAberta == o) o.confirmar();
            });
            t.play();
        }
    }

    private void responderOrdem(Lado l, List<Integer> ids) {
        if (ordemDe != l) return;
        OrdemPenaltis o = ordemAberta;
        ordemDe = null;
        ordemAberta = null;
        if (o != null && l.quadro != null) l.quadro.raiz.getChildren().remove(o);
        l.acomp.responderOrdem(ids);
        Runnable prox = filaPainel.poll();
        if (prox != null) prox.run();
    }

    /** Testes: o cabeçalho da escolha da ordem dos pênaltis aberta (null se não há). */
    String ordemAbertaDe() {
        return ordemDe == null ? null : cabecalho(ordemDe, "ordem dos pênaltis");
    }

    /** Testes e capturas: a escolha da ordem aberta. */
    OrdemPenaltis ordemAberta() { return ordemAberta; }

    /** Testes e capturas: as bolinhas do i-ésimo jogo ("GGE--" mandante, visitante). */
    String[] bolinhas(int i) {
        Quadro q = quadros.get(i);
        return new String[] {q.bolas[0].resumo(), q.bolas[1].resumo()};
    }

    /** Disputas de pênaltis ainda sendo narradas. */
    int disputasAndando() { return disputasAndando; }

    /** Animação do gol na narração: não com SIMULAR ATÉ O FIM nem na Ultra rápida (requisito). */
    private boolean animarGols() {
        return mesa != null && !mesa.pulando() && mesa.velocidade() != Velocidade.ULTRA && mesa.velocidade() != Velocidade.INSTANTANEA;
    }

    /** Testes: o cabeçalho da escolha do batedor aberta (null se não há). */
    String batedorAberto() {
        if (batedorDe == null || batedorDe.quadro == null) return null;
        return tituloBatedor;
    }

    private String assinaturaTatica = "";

    private void atualizarPainel(Lado l) {
        if (l.ultimo == null || l.ultimo.meuLado() < 0) return;
        l.trocas.atualizar(l.ultimo);
        atualizarResumo(l);
        Estado e = l.ultimo;
        String st = l.clubeId + "|" + e.estilo() + "|" + e.marcacao() + "|" + e.concentracao() + "|" + e.terminado();
        if (!st.equals(assinaturaTatica)) {
            assinaturaTatica = st;
            TelaPartida.montarTatica(taticaPainel, e, () -> l.acomp);
        }
    }

    private void fecharPainel() {
        if (kingsDe != null) {
            fecharKings();
            return;
        }
        Lado l = painelDe;
        if (l == null) return;
        if (l.trocas.temPendentes()) {
            if (App.automatico || mesa.pulando() || terminou) l.trocas.confirmar();
            else {
                int r = Ui.modal("Trocas não confirmadas", Ui.rotulo("Há " + l.trocas.pendentes().size()
                        + " troca(s) preparada(s). Confirmar antes de voltar ao jogo?", "texto-medio"), "Confirmar", "Descartar");
                if (r == 0) l.trocas.confirmar();
                else if (r == 1) l.trocas.descartar();
                else return;
            }
        }
        painelDe = null;
        assinaturaTatica = "";
        sobreposicao.setVisible(false);
        l.trocas.limpar();
        if (painelPedido) l.acomp.pausar(false);
        else mesa.pausar(false);
        mostrarPausado(mesa.pausado());
        Runnable prox = filaPainel.poll();
        if (prox != null) prox.run();
    }

    // ------------------------------------------------------------------ rodapé
    private void velocidade(Velocidade v) {
        if (!App.automatico) Opcoes.velocidade(v);
        if (mesa != null) mesa.velocidade(v);
        for (ToggleButton b : botoesVelocidade) b.setSelected(b.getText().equals(v.nome));
    }

    private void alternarPausa() {
        if (mesa == null || terminou || painelDe != null) return;
        mesa.pausar(!mesa.pausado());
        mostrarPausado(mesa.pausado());
    }

    private void mostrarPausado(boolean p) {
        pausar.setText(p ? "Retomar" : "Pausar");
    }

    private void pular() {
        if (ordemDe != null) responderOrdem(ordemDe, null); // ordem automática
        if (ordemKingsDe != null) responderOrdemKings(ordemKingsDe, null);
        for (QuadroKings q : quadrosKings) q.shootout.pular();
        for (Quadro q : quadros) q.pularSuspense();
        if (disputasAndando > 0) {
            // disputa de pênaltis narrando: o resto na hora (as bolinhas ficam)
            for (Quadro q : quadros) if (q.disputa != null && !q.disputa.acabou()) q.disputa.pular();
            if (terminou) return;
        }
        if (terminou) {
            continuar();
            return;
        }
        if (mesa == null) return;
        filaPainel.clear();
        if (painelDe != null || kingsDe != null) fecharPainel();
        if (batedorDe != null) responderBatedor(batedorDe, null);
        mesa.pular();
        for (Quadro q : quadros) q.pularSuspense();
    }

    // ------------------------------------------------------------------ fim
    private void fim(Fluxo.Rodada r) {
        rodada = r;
        terminou = true;
        entreGrupos = false;
        continuar.setText("Continuar");
        filaPainel.clear();
        if (painelDe != null || kingsDe != null) fecharPainel();
        if (batedorDe != null) responderBatedor(batedorDe, null);
        for (QuadroKings q : quadrosKings) {
            q.fim = true;
            q.relogio.setText("Fim");
            q.relogio.getStyleClass().add("parado");
        }
        if (quadros.isEmpty() && quadrosKings.isEmpty() && !r.jogou() && App.atual() != null) {
            // nenhum técnico jogou (todos sem emprego, fim de temporada...): segue direto
            App.atual().depoisDaRodadaMulti(r);
            return;
        }
        pausar.setDisable(true);
        pular.setDisable(true);
        for (ToggleButton b : botoesVelocidade) b.setDisable(true);
        // os resultados de todos os técnicos (também os jogos sem tela: auxiliar e Kings)
        Mundo m = sessao.jogo.mundo();
        resultados.getChildren().clear();
        for (Partida p : r.meusJogos()) {
            if (p.golsMandante == null) continue;
            HBox h = Ui.linha(6, Ui.escudo(p.mandante, 18), Ui.rotulo(m.nomeParticipante(p.mandante) + "  " + p.golsMandante + " x " + p.golsVisitante
                    + "  " + m.nomeParticipante(p.visitante), "texto-forte"), Ui.escudo(p.visitante, 18));
            resultados.getChildren().add(h);
        }
        if (quadros.isEmpty() && quadrosKings.isEmpty()) titulo.setText("RESULTADOS DA RODADA");
        for (Quadro q : quadros) {
            q.relogio.getStyleClass().add("parado");
            if (!q.fim) q.relogio.setText("Fim");
        }
        continuar.setDisable(disputasAndando > 0); // a disputa de pênaltis ainda narrando: Continuar espera
        raiz.requestFocus();
    }

    private void continuar() {
        if (entreGrupos) {
            App a = App.atual();
            if (a != null) a.multiEntreGrupos(this, clubesDoProximo);
            else retomarGrupo();
            return;
        }
        if (!terminou || rodada == null || disputasAndando > 0) return;
        App.atual().depoisDaRodadaMulti(rodada);
    }

    /** Nome da competição do jogo desta semana entre os dois. */
    private String competicao(String mandante, String visitante) {
        Mundo m = sessao.jogo.mundo();
        int sa = m.semanaAbsoluta();
        for (Torneio t : m.torneios)
            for (Partida p : t.partidas)
                if (p.sa == sa && mandante.equals(p.mandante) && visitante.equals(p.visitante)) return t.nome;
        return null;
    }

    // ------------------------------------------------------------------ testes e capturas
    /** Quantos jogos a tela mostra juntos. */
    public int jogosNaTela() { return quadros.size() + quadrosKings.size(); }

    /** Testes: quantos jogos da Kings a tela mostra. */
    public int jogosKingsNaTela() { return quadrosKings.size(); }

    public boolean terminou() { return terminou; }

    @Override
    public boolean tecla(KeyEvent e) {
        KeyCode k = e.getCode();
        if (batedorDe != null) {
            if (escolhaBatedor != null) escolhaBatedor.tecla(k);
            else if (k == KeyCode.ESCAPE) responderBatedor(batedorDe, null);
            return true;
        }
        if (ordemDe != null) {
            if (k == KeyCode.ENTER && ordemAberta != null) ordemAberta.confirmar();
            else if (k == KeyCode.ESCAPE) responderOrdem(ordemDe, null);
            return true;
        }
        if (ordemKingsDe != null) {
            if (k == KeyCode.ENTER && ordemAberta != null) ordemAberta.confirmar();
            else if (k == KeyCode.ESCAPE) responderOrdemKings(ordemKingsDe, null);
            return true;
        }
        if (painelDe != null || kingsDe != null) {
            if (k == KeyCode.ENTER || k == KeyCode.ESCAPE) {
                fecharPainel();
                return true;
            }
            return false;
        }
        if (terminou || entreGrupos) {
            if (k == KeyCode.ENTER || k == KeyCode.SPACE || k == KeyCode.ESCAPE) {
                if (disputasAndando > 0) pular(); // disputa de pênaltis narrando: a tecla pula as esperas
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
            case ESCAPE -> pular();
            default -> {
                return false;
            }
        }
        return true;
    }
}
