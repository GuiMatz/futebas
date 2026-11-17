package br.futebas.ui;

import br.futebas.Jogo;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.telas.TelaDesempregado;
import br.futebas.ui.telas.TelaFimTemporada;
import br.futebas.ui.telas.TelaInicio;
import br.futebas.ui.telas.TelaNovoJogo;
import br.futebas.ui.telas.TelaPartida;
import br.futebas.ui.telas.Hub;
import br.futebas.ui.modelo.Fluxo;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** A janela do jogo: troca de telas, trabalho em segundo plano, atalhos globais e avisos. */
public class App extends Application {
    private static App instancia;

    private Stage palco;
    private Scene cena;
    private final StackPane camadaTela = new StackPane();
    private final StackPane camadaEspera = new StackPane();
    /** Camada do "avançando o calendário" (sobre a tela atual). */
    private final StackPane camadaProcesso = new StackPane();
    private br.futebas.ui.telas.Processando processo;

    public boolean processando() { return processo != null && processo.getParent() != null || simulando != null; }
    private final Label textoEspera = new Label();
    private final StackPane camadaToast = new StackPane();
    private Tela telaAtual;
    private Sessao sessao;
    private Hub hub;
    private volatile boolean ocupado;
    private ExecutorService trabalhador = novoTrabalhador();

    private static ExecutorService novoTrabalhador() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "futebas-motor");
            t.setDaemon(true);
            return t;
        });
    }

    public static App atual() { return instancia; }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        instancia = this;
        palco = stage;
        StackPane camadaModal = new StackPane();
        camadaModal.setVisible(false);
        Ui.camadaModal = camadaModal;
        StackPane raiz = new StackPane(camadaTela, camadaProcesso, camadaEspera, camadaModal, camadaToast);
        camadaProcesso.setPickOnBounds(false);
        javafx.scene.control.ProgressBar pi = new javafx.scene.control.ProgressBar(-1);
        pi.setPrefSize(260, 5);
        textoEspera.getStyleClass().add("titulo-menor");
        VBox caixa = new VBox(14, textoEspera, pi);
        caixa.setAlignment(Pos.CENTER);
        caixa.getStyleClass().add("espera-caixa");
        caixa.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        camadaEspera.getChildren().add(caixa);
        camadaEspera.getStyleClass().add("espera");
        camadaEspera.setVisible(false);
        camadaToast.setPickOnBounds(false);
        camadaToast.setMouseTransparent(true);
        camadaToast.setAlignment(Pos.BOTTOM_CENTER);
        camadaToast.setStyle("-fx-padding: 0 0 40 0;");

        carregarFontes();
        escalavel = new Escalavel(raiz);
        cena = new Scene(escalavel, 1280, 800);
        cena.setFill(javafx.scene.paint.Color.web("#1c1f24")); // sem frestas brancas com a escala de 90%
        String css = App.class.getResource("/br/futebas/ui/tema.css").toExternalForm();
        cena.getStylesheets().add(css);
        Ui.folhaDeEstilo = css;
        Ui.janela = stage;
        cena.addEventFilter(KeyEvent.KEY_PRESSED, this::tecla);

        stage.setTitle("FUTÉBAS");
        for (int t : new int[] {256, 128, 64, 48, 32, 24, 16}) {
            var url = App.class.getResource("/br/futebas/ui/icone/icone-" + t + ".png");
            if (url != null) stage.getIcons().add(new javafx.scene.image.Image(url.toExternalForm()));
        }
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setScene(cena);
        stage.setOnCloseRequest(e -> {
            if (!automatico && !podeSairSemSalvar("Sair do jogo")) {
                e.consume();
                return;
            }
            trabalhador.shutdownNow();
            Platform.exit();
        });
        // tela cheia (padrão): sai pelas Opções, F11 ou Alt+Enter; o Esc continua sendo do jogo
        stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
        stage.setFullScreenExitHint("");
        Opcoes.aoMudar(() -> {
            if (Platform.isFxApplicationThread()) aplicarOpcoes();
            else Platform.runLater(this::aplicarOpcoes);
        });
        mostrar(new TelaInicio());
        aplicarOpcoes();
        stage.show();
        aquecerNoMenu();

        List<String> args = getParameters().getRaw();
        String dir = System.getProperty("futebas.capturas");
        if (args.contains("--capturas") || dir != null) {
            br.futebas.ui.comp.Imagens.emSegundoPlano = false; // fotos de tela sem escudo ainda chegando
            new Capturas(this, dir != null ? Path.of(dir) : Caminhos.raiz().resolve("tools").resolve("screens")).iniciar();
        }
    }

    // ------------------------------------------------------------------ opções de interface
    private Escalavel escalavel;

    /** Aplica as opções de interface (escala e tela cheia) na janela. */
    private void aplicarOpcoes() {
        if (escalavel != null) escalavel.fator(Opcoes.escala().fator);
        // capturas automáticas ficam sempre em janela (tamanho fixo das imagens)
        boolean cheia = Opcoes.telaCheia() && !automatico && System.getProperty("futebas.capturas") == null;
        if (palco != null && palco.isFullScreen() != cheia) palco.setFullScreen(cheia);
    }

    /**
     * Raiz da cena que escala a interface inteira (Opções → Escala): o conteúdo é diagramado no
     * tamanho da janela dividido pelo fator e desenhado ampliado, então tudo cresce junto.
     */
    static final class Escalavel extends javafx.scene.layout.Pane {
        private final javafx.scene.layout.Region conteudo;
        private final javafx.scene.transform.Scale escala = new javafx.scene.transform.Scale(1, 1, 0, 0);
        private double fator = 1;

        Escalavel(javafx.scene.layout.Region conteudo) {
            this.conteudo = conteudo;
            conteudo.getTransforms().add(escala);
            getChildren().add(conteudo);
        }

        void fator(double f) {
            if (f <= 0 || f == fator) return;
            fator = f;
            escala.setX(f);
            escala.setY(f);
            requestLayout();
        }

        @Override
        protected void layoutChildren() {
            conteudo.resizeRelocate(0, 0, getWidth() / fator, getHeight() / fator);
        }
    }

    /** Opção "confirmar ao sair sem salvar": pergunta se há progresso não salvo. */
    public boolean podeSairSemSalvar(String titulo) {
        if (sessao == null || !Opcoes.confirmarSair() || !sessao.naoSalvo()) return true;
        return Ui.confirmar(titulo, "O progresso não salvo será perdido. Continuar?");
    }

    /** Fontes do jogo (Bahnschrift condensada e seminegrita), embutidas nos recursos. */
    public static void carregarFontes() {
        for (String f : new String[] {"Placar", "PlacarMedio", "Titulo"}) {
            var u = App.class.getResource("/br/futebas/ui/fontes/" + f + ".ttf");
            if (u != null) javafx.scene.text.Font.loadFont(u.toExternalForm(), 12);
        }
    }

    // ------------------------------------------------------------------ telas
    public void mostrar(Tela t) {
        telaAtual = t;
        // fora da central, clicar num clube/jogador/técnico leva à central com a ficha aberta
        Ui.aoAbrirClube = sessao == null || sessao.jogo.meuClube() == null || t instanceof TelaPartida ? null
                : id -> irParaFicha(h -> h.abrirParticipante(id));
        Ui.aoAbrirCompeticao = sessao == null || sessao.jogo.meuClube() == null || t instanceof TelaPartida ? null
                : id -> irParaFicha(h -> h.abrirCompeticao(id));
        Ui.aoAbrirJogador = sessao == null || sessao.jogo.meuClube() == null || t instanceof TelaPartida ? null
                : id -> irParaFicha(h -> h.abrirFichaJogadorPorId(id));
        Ui.aoAbrirTecnico = sessao == null || sessao.jogo.meuClube() == null || t instanceof TelaPartida ? null
                : id -> irParaFicha(h -> h.abrirFichaTecnico(id));
        var meu = sessao == null ? null : sessao.jogo.meuClube();
        camadaTela.setStyle(meu == null ? "" : br.futebas.ui.comp.Tema.estiloAcento(meu.cor1, meu.cor2));
        Parent r = t.raiz();
        camadaTela.getChildren().setAll(r);
        t.aoMostrar();
        r.requestFocus();
    }

    public Tela telaAtual() { return telaAtual; }

    public Sessao sessao() { return sessao; }

    public Stage palco() { return palco; }

    public Scene cena() { return cena; }

    public void irParaInicio() {
        pendente = null;
        // bug corrigido (16/11): voltou ao menu com uma tarefa do jogo anterior presa (no multi o motor
        // segura a trava esperando a vez do outro técnico) e o Novo jogo abria vazio, porque o
        // "ocupado" ignorava o carregamento. O jogo antigo é abandonado: o trabalhador velho vai embora.
        if (ocupado) {
            trabalhador.shutdownNow();
            trabalhador = novoTrabalhador();
            ocupado = false;
            camadaEspera.setVisible(false);
        }
        sessao = null;
        hub = null;
        mostrar(new TelaInicio());
    }

    public void novoJogo() {
        mostrar(new TelaNovoJogo());
    }

    /** Abre um jogo (novo ou carregado) na tela certa. */
    public void abrir(Sessao s) {
        sessao = s;
        hub = null;
        if (!automatico) s.fluxo.aposSemana((antes, depois, nova) -> {
            Path p = s.autoSalvarSeDevido(antes, depois, nova);
            if (p != null) Platform.runLater(() -> aviso("Salvo automaticamente"));
        });
        if (s.jogo.multi()) {
            // multi-treinador: continua a rodada na vez de quem estava jogando (ou começa uma nova)
            if (s.jogo.vez() > 0) comecarVez(s.jogo.vez());
            else prepararRodadaMulti();
            return;
        }
        if (s.jogo.soAssistindo()) {
            // o modo "só assistir" saiu do jogo: saves antigos nesse modo não abrem mais
            sessao = null;
            Ui.informar("Jogo salvo sem técnico", "Este jogo foi salvo no antigo modo \"só assistir\", que não existe mais. Comece um novo jogo.");
            irParaInicio();
        }
        else if (s.jogo.meuClube() == null && !s.fluxo.soKings()) mostrar(new TelaDesempregado(s));
        else irParaHub(null);
    }

    private void irParaFicha(java.util.function.Consumer<Hub> abrir) {
        if (ocupado || sessao == null) return;
        if (!(telaAtual instanceof Hub)) irParaHub(null);
        Hub h = hub();
        if (h != null) abrir.accept(h);
    }

    public Hub hub() {
        if (hub == null && sessao != null) hub = new Hub(sessao);
        return hub;
    }

    public void irParaHub(String painel) {
        if (sessao == null) return;
        if (celebrar(() -> irParaHub(painel))) return; // título da semana ainda não celebrado (ex.: semanas sem jogo)
        if (sessao.jogo.meuClube() == null && !sessao.jogo.soAssistindo() && !sessao.fluxo.soKings()) {
            mostrar(new TelaDesempregado(sessao));
            return;
        }
        Hub h = hub();
        if (painel != null) h.selecionar(painel);
        mostrar(h);
    }

    /** O botão "Jogar": vai para a partida (ou para o fim de temporada / demissão). */
    /** Partida com outro jogo seu ainda na mesma semana: a central aparece entre os dois. */
    private TelaPartida pendente;

    public boolean temPendente() { return pendente != null; }

    public void partidaPendente(TelaPartida t) {
        pendente = t;
        irParaHub(null);
    }

    public void jogar() {
        if (sessao != null && sessao.jogo.multi()) {
            jogarMulti();
            return;
        }
        if (pendente != null) {
            // 2º jogo da semana: também passa pelo pré-jogo e pela escalação (regra do jogo)
            if (passoAntes()) return;
            TelaPartida t = pendente;
            pendente = null;
            mostrar(t);
            t.retomar();
            return;
        }
        if (sessao == null || ocupado) return;
        if (sessao.jogo.meuClube() != null && !sessao.fluxo.temJogoNaSemana()) {
            // semanas sem jogo: o calendário corre num cartão sobre a central
            processo = new br.futebas.ui.telas.Processando(sessao);
            camadaProcesso.getChildren().setAll(processo);
            processo.iniciar(this::depoisDaRodada, () -> {
                processo = null;
                if (!passoAntes()) comecarPartida();
            }, () -> {
                processo = null;
                irParaHub(null);
            });
            return;
        }
        if (passoAntes()) return;
        comecarPartida();
    }

    // ------------------------------------------------------------------ multi-treinador (hot-seat)
    /** Os passos antes do jogo de cada técnico (pré-jogo e escalação, uma vez por partida de cada um). */
    private final java.util.Map<br.futebas.mundo.Tecnico, br.futebas.ui.modelo.AntesDoJogo> antesMulti = new java.util.IdentityHashMap<>();

    /**
     * Multi-treinador: o "Jogar" do técnico da vez. Passa pelo pré-jogo e pela escalação dele e passa a
     * vez ao próximo; o "Jogar" do último joga a rodada na tela dividida.
     */
    private void jogarMulti() {
        // entre dois jogos da mesma semana o motor está parado (ocupado) esperando as vezes dos técnicos
        if (sessao == null || ocupado && multiPendente == null) return;
        if (!automatico) {
            br.futebas.ui.modelo.AntesDoJogo a = antesMulti.computeIfAbsent(sessao.jogo.mundo().tecnico, k -> new br.futebas.ui.modelo.AntesDoJogo());
            br.futebas.ui.modelo.AntesDoJogo.Passo passo = a.proximo(sessao, Opcoes.escalacaoAntesDoJogo());
            if (passo != br.futebas.ui.modelo.AntesDoJogo.Passo.PARTIDA && sessao.jogo.meuClube() != null) {
                Hub h = hub();
                if (telaAtual != h) mostrar(h);
                if (passo == br.futebas.ui.modelo.AntesDoJogo.Passo.PRE_JOGO) h.abrirPreJogo(br.futebas.ui.modelo.AntesDoJogo.partida(sessao));
                else h.abrirEscalacaoAntesDoJogo();
                return;
            }
        }
        int prox = proximaVez(sessao.jogo.vez());
        if (prox >= 0) {
            comecarVez(prox);
            return;
        }
        if (multiPendente != null) {
            // 2º jogo da semana: todos os técnicos dele mandaram jogar; a tela dividida volta e o grupo começa
            br.futebas.ui.telas.TelaPartidaMulti t = multiPendente;
            multiPendente = null;
            mostrar(t);
            t.retomarGrupo();
            return;
        }
        mostrar(new br.futebas.ui.telas.TelaPartidaMulti(sessao));
    }

    /** Tela dividida esperando os técnicos do 2º jogo da semana voltarem da central. */
    private br.futebas.ui.telas.TelaPartidaMulti multiPendente;

    /** O próximo técnico com vez depois de {@code depois} (no 2º jogo da semana, só os que jogam nele); -1 = nenhum. */
    private int proximaVez(int depois) {
        List<br.futebas.mundo.Tecnico> ts = sessao.jogo.tecnicos();
        for (int k = depois + 1; k < ts.size(); k++) {
            List<String> prox = multiPendente == null ? null : multiPendente.clubesDoProximoGrupo();
            if (prox == null || prox.contains(ts.get(k).clubeId) || ts.get(k).kingsTimeId != null && prox.contains(ts.get(k).kingsTimeId)) return k;
        }
        return -1;
    }

    /**
     * Multi-treinador, regra do jogo ("sempre tem que voltar para o menu após"): depois de um grupo de
     * jogos, se algum técnico joga de novo na mesma semana, cada técnico desse jogo volta à central (na
     * vez dele) antes de o próximo grupo começar.
     */
    public void multiEntreGrupos(br.futebas.ui.telas.TelaPartidaMulti t, List<String> clubes) {
        multiPendente = t;
        int prox = proximaVez(-1);
        if (prox < 0) {
            multiPendente = null;
            t.retomarGrupo();
            return;
        }
        comecarVez(prox);
    }

    /** Multi-treinador: a vez do técnico {@code i} (a tela "Vez de <Nome>" e depois a central dele). */
    public void comecarVez(int i) {
        if (sessao == null) return;
        // entre dois jogos da mesma semana o motor está parado esperando (e segura a trava): passa a vez direto
        if (sessao.trava.tryLock()) {
            try {
                sessao.jogo.passarVez(i);
            } finally {
                sessao.trava.unlock();
            }
        } else sessao.jogo.passarVez(i);
        hub = null; // a central é de cada técnico
        pendente = null;
        // requisito (16/11): sem a tela "Vez de", troca direto para a central do outro técnico
        // (o cabeçalho da central já mostra "VEZ DE NOME · 1/2")
        entrarNaVez();
    }

    /** Depois do "Vez de": o resumo da temporada que ele ainda não viu, ou a central (ou "Sem clube"). */
    private void entrarNaVez() {
        if (sessao == null) return;
        Fluxo.ResumoTemporada fim = sessao.fluxo.tirarFimPendente(sessao.jogo.mundo().tecnico);
        if (fim != null) {
            mostrar(new TelaFimTemporada(sessao, fim));
            return;
        }
        // draft da Kings na vez deste técnico: a escolha é dele (abre a aba da Kings, com o draft)
        var tec = sessao.jogo.mundo().tecnico;
        var draft = sessao.jogo.estadoDraft();
        if (tec != null && tec.kingsTimeId != null && draft != null && tec.kingsTimeId.equals(draft.vez())) {
            irParaHub("kings");
            return;
        }
        irParaHub(null);
    }

    /** Multi-treinador: começo de uma rodada (o calendário corre até a semana de jogo; a vez é do 1º técnico). */
    public void prepararRodadaMulti() {
        if (sessao == null) return;
        trabalhar("Avançando o calendário...", () -> {
            sessao.jogo.passarVez(0);
            return sessao.fluxo.avancarAteRodadaMulti();
        }, r -> comecarVez(0));
    }

    /** Multi-treinador: depois da rodada na tela dividida, a próxima rodada. */
    public void depoisDaRodadaMulti(Fluxo.Rodada r) {
        if (celebrar(this::prepararRodadaMulti)) return;
        prepararRodadaMulti();
    }

    /** Pré-jogo → escalação → partida, para todo jogo não delegado (clube e Kings), uma vez cada por partida. */
    private final br.futebas.ui.modelo.AntesDoJogo antes = new br.futebas.ui.modelo.AntesDoJogo();

    /** Mostra o próximo passo antes do jogo; false = pode começar a partida. Nunca nas capturas. */
    private boolean passoAntes() {
        if (automatico || sessao == null) return false;
        br.futebas.ui.modelo.AntesDoJogo.Passo passo = antes.proximo(sessao, Opcoes.escalacaoAntesDoJogo());
        if (passo == br.futebas.ui.modelo.AntesDoJogo.Passo.PARTIDA) return false;
        Hub h = hub();
        if (telaAtual != h) mostrar(h);
        if (passo == br.futebas.ui.modelo.AntesDoJogo.Passo.PRE_JOGO) h.abrirPreJogo(br.futebas.ui.modelo.AntesDoJogo.partida(sessao));
        else h.abrirEscalacaoAntesDoJogo();
        return true;
    }

    /** Começa a partida da semana (com a opção "confirmar escalação com desfalques"). */
    private void comecarPartida() {
        if (!automatico && Opcoes.confirmarEscalacao() && sessao.jogo.meuClube() != null && sessao.fluxo.temJogoNaSemana()) {
            br.futebas.competicao.Partida p = sessao.fluxo.proximoJogo();
            boolean delegado = p != null && sessao.jogo.delegadoAoAuxiliar(p.torneioId, p.id);
            List<String> problemas = delegado ? List.of() : br.futebas.ui.modelo.ConferirEscalacao.problemas(sessao.jogo.mundo(), sessao.jogo.meuClube());
            if (!problemas.isEmpty()) {
                javafx.scene.layout.VBox lista = new javafx.scene.layout.VBox(4);
                for (String x : problemas) lista.getChildren().add(Ui.rotulo("•  " + x, "texto-medio"));
                int r = Ui.modal("Escalação com desfalques", lista, "Jogar assim", "Ir para a escalação", "Cancelar");
                if (r == 1) {
                    irParaHub("escalacao");
                    return;
                }
                if (r != 0) {
                    irParaHub(null);
                    return;
                }
            }
        }
        mostrar(new TelaPartida(sessao));
    }

    /**
     * "Simular temporada" (Calendário, menu da central e "Aguardar convites"): o auxiliar comanda os
     * jogos e o calendário anda semana a semana num cartão, até o fim da temporada, "Parar" ou algo que
     * peça o técnico. {@code depois} (opcional) roda no fim em vez de voltar à central.
     */
    public void simularTemporada(Runnable depois) {
        if (sessao == null || ocupado || processando()) return;
        if (sessao.jogo.multi()) {
            aviso("Simular temporada não existe no jogo com vários treinadores");
            return;
        }
        br.futebas.ui.telas.SimulandoTemporada sim = new br.futebas.ui.telas.SimulandoTemporada(sessao);
        camadaProcesso.getChildren().setAll(sim);
        simulando = sim;
        sim.iniciar(sem -> {
            simulando = null;
            if (sem != null && (sem.fimDeTemporada() || sem.rodada().demitido())) {
                depoisDaRodada(sem.rodada());
            } else if (depois != null) {
                depois.run();
            } else if (sem != null && sem.parada() != null && sem.parada().startsWith("Draft")) {
                irParaHub("kings");
            } else if (sem != null && sem.parada() != null) {
                irParaHub(sessao.jogo.propostasDeEmprego().isEmpty() ? "caixa" : "vagas");
            } else {
                irParaHub(null);
            }
        });
    }

    private br.futebas.ui.telas.SimulandoTemporada simulando;

    /** O cartão da simulação está na tela (Esc para). */
    public boolean simulandoTemporada() { return simulando != null; }

    public void pararSimulacao() {
        if (simulando != null) simulando.parar();
    }

    /** Depois de uma rodada: decide a próxima tela. */
    public void depoisDaRodada(Fluxo.Rodada r) {
        if (sessao != null && sessao.jogo.multi()) {
            depoisDaRodadaMulti(r);
            return;
        }
        // título: a tela de campeão vem antes do resto (fim de temporada, central...)
        if (celebrar(() -> depoisDaRodada(r))) return;
        if (r.fimDeTemporada() != null) {
            mostrar(new TelaFimTemporada(sessao, r.fimDeTemporada()));
        } else if (r.demitido() || sessao.jogo.meuClube() == null && !sessao.fluxo.soKings()) {
            mostrar(new TelaDesempregado(sessao));
        } else {
            irParaHub(null);
        }
    }

    // ------------------------------------------------------------------ tela de campeão
    /**
     * Mostra os títulos ainda não celebrados (uma tela por título, em sequência) e depois roda
     * {@code depois}. Devolve false se não havia nada (ou no modo automático, que pula a tela).
     */
    private boolean celebrar(Runnable depois) {
        if (sessao == null || !sessao.fluxo.temConquistas()) return false;
        List<Fluxo.Conquista> l = sessao.fluxo.tirarConquistas();
        if (automatico || l.isEmpty()) return false;
        mostrarConquista(l, 0, depois);
        return true;
    }

    private void mostrarConquista(List<Fluxo.Conquista> l, int i, Runnable depois) {
        if (i >= l.size()) {
            depois.run();
            return;
        }
        mostrar(new br.futebas.ui.telas.TelaCampeao(sessao, l.get(i), sessao.jogo.multi(), () -> mostrarConquista(l, i + 1, depois)));
    }

    // ------------------------------------------------------------------ aquecimento (desempenho)
    /**
     * Enquanto o jogador olha o menu: o banco (~3 s com o banco completo) e as classes da interface
     * carregam em segundo plano; "Continuar" e "Novo jogo" já os encontram prontos.
     */
    private void aquecerNoMenu() {
        Thread t = new Thread(() -> {
            br.futebas.ui.comp.Aquecimento.classes();
            try {
                Sessao.banco();
            } catch (RuntimeException e) {
                // sem banco agora: quem precisar tenta de novo (e mostra o erro)
            }
        }, "futebas-aquecer");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    /**
     * Com a espera à vista (abrindo um jogo): monta uma cena de aquecimento fora da janela, para a 1ª
     * central não pagar a primeira vez do CSS, das fontes e das peles dos controles. Um instante
     * depois, para a espera aparecer antes.
     */
    private void aquecerNaEspera() {
        javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(javafx.util.Duration.millis(120));
        p.setOnFinished(e -> br.futebas.ui.comp.Aquecimento.tela(Ui.folhaDeEstilo));
        p.play();
    }

    // ------------------------------------------------------------------ trabalho em segundo plano
    public boolean ocupado() { return ocupado; }

    /** Roda {@code tarefa} fora da tela (com a trava do mundo) e entrega o resultado na tela. */
    public <T> void trabalhar(String mensagem, Callable<T> tarefa, Consumer<T> depois) {
        if (ocupado) return;
        ocupado = true;
        textoEspera.setText(mensagem);
        camadaEspera.setVisible(mensagem != null);
        Sessao s = sessao;
        if (s == null) aquecerNaEspera();
        trabalhador.submit(() -> {
            T res;
            if (s != null) s.trava.lock();
            try {
                res = tarefa.call();
                // jogo aberto (carregado ou novo): os escudos e troféus da 1ª central vão para o cache ainda aqui
                if (res instanceof Sessao nova && s == null) Hub.prepararCentral(nova);
            } catch (Throwable ex) {
                Platform.runLater(() -> {
                    ocupado = false;
                    camadaEspera.setVisible(false);
                    erro(ex);
                });
                return;
            } finally {
                if (s != null) s.trava.unlock();
            }
            T fim = res;
            Platform.runLater(() -> {
                ocupado = false;
                camadaEspera.setVisible(false);
                try {
                    if (depois != null) depois.accept(fim);
                } catch (RuntimeException ex) {
                    erro(ex);
                }
            });
        });
    }

    /** Modo automático (capturas de tela): erros vão para o log, sem diálogos. */
    public static volatile boolean automatico;
    public static final java.util.List<String> errosAutomaticos = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void erro(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        System.err.println(sw);
        if (automatico) {
            errosAutomaticos.add(sw.toString());
            return;
        }
        String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        Ui.informar("Algo deu errado", msg);
    }

    public void aviso(String texto) {
        Label l = new Label(texto);
        StackPane t = new StackPane(l);
        t.getStyleClass().add("toast");
        t.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
        camadaToast.getChildren().setAll(t);
        PauseTransition p = new PauseTransition(Duration.seconds(2.4));
        p.setOnFinished(e -> camadaToast.getChildren().remove(t));
        p.play();
    }

    // ------------------------------------------------------------------ salvar
    public void salvar() {
        salvar(null);
    }

    /** Salva (pergunta o nome na primeira vez) e depois roda {@code depois}, se houver. */
    public void salvar(Runnable depois) {
        if (sessao == null || ocupado) return;
        if (sessao.nomeSave == null) {
            var r = Ui.perguntar("Salvar jogo", "Nome do arquivo:", sessao.nomeSugerido());
            if (r.isEmpty() || r.get().isBlank()) return;
            sessao.nomeSave = r.get().trim().replaceAll("[\\\\/:*?\"<>|]", "");
        }
        trabalhar("Salvando...", sessao::salvar, p -> {
            aviso("Jogo salvo: " + sessao.nomeSave);
            if (depois != null) depois.run();
        });
    }

    public static Path pastaSaves() { return Jogo.pastaSaves(); }

    // ------------------------------------------------------------------ teclado
    private void tecla(KeyEvent e) {
        if (e.getCode() == KeyCode.F11 || e.getCode() == KeyCode.ENTER && e.isAltDown()) {
            Opcoes.telaCheia(!palco.isFullScreen());
            e.consume();
            return;
        }
        if (ocupado && camadaEspera.isVisible()) {
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.F2 && sessao != null && !(telaAtual instanceof TelaPartida)) {
            salvar();
            e.consume();
            return;
        }
        if (Ui.camadaModal != null && Ui.camadaModal.isVisible()) return; // o diálogo dentro da janela recebe as teclas
        if (processando()) {
            if (e.getCode() == KeyCode.ESCAPE) {
                if (simulando != null) simulando.parar();
                else if (processo != null) processo.parar();
            }
            e.consume();
            return;
        }
        if (telaAtual != null && telaAtual.tecla(e)) e.consume();
    }
}
