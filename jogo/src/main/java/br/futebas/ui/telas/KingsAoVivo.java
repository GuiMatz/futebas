package br.futebas.ui.telas;

import br.futebas.competicao.FaseExec;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.kings.AoVivoKings;
import br.futebas.kings.LigaKings;
import br.futebas.kings.MotorKings;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Posicao;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.Frases;
import br.futebas.ui.App;
import br.futebas.ui.comp.BotoesKings;
import br.futebas.ui.comp.CartaKings;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoKings;
import br.futebas.ui.modelo.Textos;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * O jogo da Kings League do técnico na MESMA tela da partida do futebol (requisito: "é só um
 * futebol com 7 pessoas"): faixa da rodada com a competição e a taça, o cartão do placar com os
 * escudos e a barra do tempo, a narração lance a lance (mandante à esquerda, visitante à direita),
 * a coluna da Kings (fase, pênalti do presidente, suspensão e jogador estrela), a coluna da tabela e
 * o rodapé com "SIMULAR ATÉ O FIM" e "Continuar".
 * <p>12/11/2026 (requisitos): as cartas secretas são cartas no estilo das oficiais ({@link CartaKings});
 * a sua carta e o pênalti do presidente são botões pequenos ao lado de "Tática e trocas"
 * ({@link BotoesKings}, o mesmo componente da tela dividida do multi-treinador); o jogo só para no intervalo (e no fim); cobranças
 * com suspense ({@link SuspensePenalti}: "vai para a bola…", "bateu…", o resultado) e a disputa de
 * shootouts no painel de bolinhas; escolhas da carta (jogador estrela, suspensão, pênalti invertido)
 * no menu rápido do botão da carta, que não para o jogo (sem escolha em 5 s, o automático); chances que não viram
 * gol com as legendas do banco comum ({@link Frases}); gol de shootout e do presidente com a linha de
 * gol. Os componentes da Kings (cartas, seletor, letreiros, disputa) são camadas por cima da tela,
 * prontas para irem para a TelaPartida.
 */
final class KingsAoVivo extends StackPane implements AoVivoKings {
    private final Mundo m;
    private final AcompanhamentoKings acomp = new AcompanhamentoKings(0, e -> Platform.runLater(() -> mostrar(e)));
    private boolean emJogo;
    private String idA, idB;
    private LigaKings liga;
    private Torneio torneio;
    private Partida partida;

    private final Label rodadaTitulo = Ui.rotulo("KINGS LEAGUE", "rodada-titulo"), rodadaSub = Ui.rotulo("", "rodada-sub");
    private final StackPane logoComp = new StackPane();
    private final StackPane escA = new StackPane(), escB = new StackPane();
    private final Label nomeA = Ui.rotulo("", "placar-time"), nomeB = Ui.rotulo("", "placar-time");
    private final Label golsA = Ui.rotulo("0", "placar-gols"), golsB = Ui.rotulo("0", "placar-gols");
    private final Label relogio = Ui.rotulo("0'", "placar-relogio");
    private final Label fase = Ui.rotulo("", "kings-fase");
    private final Label emCampo = Ui.rotulo("", "texto-pequeno");
    private final Region trilhaCheia = new Region();
    private final VBox lances = new VBox();
    private final ScrollPane rolagem = new ScrollPane(lances);
    private final VBox tabelaCorpo = new VBox(8);
    private final Label tituloTabela = Ui.rotulo("TABELA", "coluna-titulo");
    /** A coluna da Kings (fase, carta do adversário, efeitos): componente próprio. */
    private final PainelKingsLateral lateral = new PainelKingsLateral();
    /** A sua carta e o pênalti do presidente: botões pequenos ao lado de "Tática e trocas" (requisito, 12/11/2026). */
    private final BotoesKings botoes = new BotoesKings(acomp);
    private String resultadoPresidente;
    private final Label aviso = Ui.rotulo("", "kings-aviso");
    private final Button seguir = Ui.botaoTecla("Continuar", "Enter", this::seguir, "primario");

    // camadas da Kings (por cima da tela)
    private final Label letreiro = Ui.rotulo("", "kings-letreiro");
    private final StackPane camadaLetreiro = new StackPane(letreiro);
    private PauseTransition apagarLetreiro;
    private final Label suspenseTitulo = Ui.rotulo("", "suspense-titulo"), suspensePasso = Ui.rotulo("", "suspense-passo");
    private final StackPane camadaSuspense = new StackPane();
    private SuspensePenalti.Toque toque;
    private final StackPane camadaDisputa = new StackPane();
    /** Shoot out do desempate: bolinhas no placar e as cobranças na narração (o mesmo sistema da disputa de pênaltis). */
    private final SuspensePenalti.Bolinhas[] bolas = {new SuspensePenalti.Bolinhas(7), new SuspensePenalti.Bolinhas(7)};
    private final ShootoutNarrado shootout = new ShootoutNarrado(lances, bolas, relogio);
    /** A escolha da ordem do shoot out aberta (null = fechada). */
    private OrdemPenaltis ordemAberta;
    private AoVivoKings.Cobranca ultimaCobranca;

    private final Frases frases = new Frases(System.nanoTime());
    private AcompanhamentoKings.Estado ultimo;
    private int vistos, minutoAtual;
    /** Estado das cartas do jogo (lado 0 / 1): tipo, usada, resultado e efeitos em curso. */
    private final MotorKings.Carta[] tipoCarta = new MotorKings.Carta[2];
    private final boolean[] cartaUsada = new boolean[2];
    private final String[] resultadoCarta = new String[2];
    private final String[] estrela = new String[2], suspenso = new String[2];
    private final int[] suspensoAte = new int[2];
    private String ultimoResultadoInvertido;

    KingsAoVivo(Mundo m) {
        this.m = m;
        getStyleClass().add("tela-kings-ao-vivo");
        setStyle("-fx-background-color: -fundo;");
        setVisible(false);

        // faixa da rodada (a mesma da partida)
        VBox txt = new VBox(2, rodadaTitulo, rodadaSub);
        txt.setAlignment(Pos.CENTER_LEFT);
        logoComp.setMinSize(64, 64);
        HBox faixa = new HBox(16, txt, Ui.espaco(), logoComp);
        faixa.setAlignment(Pos.CENTER_LEFT);
        faixa.getStyleClass().add("faixa-rodada");

        // cartão do placar
        for (Label n : List.of(nomeA, nomeB)) {
            n.setMinWidth(0);
            n.setWrapText(true);
            n.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        }
        VBox ladoA = new VBox(6, escA, nomeA, bolas[0]), ladoB = new VBox(6, escB, nomeB, bolas[1]);
        for (VBox v : List.of(ladoA, ladoB)) {
            v.setAlignment(Pos.CENTER);
            v.setPrefWidth(200);
            v.setMinWidth(110);
            HBox.setHgrow(v, Priority.ALWAYS);
        }
        HBox numeros = new HBox(14, golsA, Ui.rotulo("–", "placar-gols"), golsB);
        numeros.setAlignment(Pos.CENTER);
        VBox meio = new VBox(4, numeros, relogio, emCampo);
        meio.setAlignment(Pos.CENTER);
        meio.setMinWidth(170);
        HBox linha = new HBox(10, ladoA, meio, ladoB);
        linha.setAlignment(Pos.CENTER);
        Region trilha = new Region();
        trilha.getStyleClass().add("placar-trilha");
        trilhaCheia.getStyleClass().add("placar-trilha-cheia");
        trilhaCheia.setPrefWidth(0);
        trilhaCheia.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane barra = new StackPane(trilha, trilhaCheia);
        barra.setAlignment(Pos.CENTER_LEFT);
        trilha.setMaxWidth(Double.MAX_VALUE);
        barra.widthProperty().addListener((o, a, b) -> progresso());
        fase.setMaxWidth(Double.MAX_VALUE);
        fase.setAlignment(Pos.CENTER);
        VBox placar = new VBox(10, linha, barra, fase);
        placar.getStyleClass().addAll("placar", "placar-cartao");

        // narração
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagem.getStyleClass().add("narracao-fundo");
        lances.getStyleClass().add("narracao-fundo");
        lances.heightProperty().addListener((o, a, b) -> rolagem.setVvalue(1));
        VBox.setVgrow(rolagem, Priority.ALWAYS);
        VBox bloco = new VBox(0, placar, rolagem);
        bloco.getStyleClass().add("bloco-jogo");
        HBox.setHgrow(bloco, Priority.ALWAYS);
        bloco.setMinWidth(420);

        // coluna da Kings: sua carta, presidente, carta do adversário e a fase (PainelKingsLateral)
        PainelKingsLateral colKings = lateral;

        ScrollPane rolTabela = Ui.rolar(tabelaCorpo);
        rolTabela.setFitToWidth(true);
        VBox.setVgrow(rolTabela, Priority.ALWAYS);
        VBox colTabela = new VBox(8, tituloTabela, rolTabela);
        colTabela.getStyleClass().add("coluna-partida");
        colTabela.setPrefWidth(320);
        colTabela.setMinWidth(260);

        HBox centro = new HBox(12, bloco, colKings, colTabela);
        centro.setStyle("-fx-padding: 12 16 12 16;");
        Button pular = Ui.botaoTecla("SIMULAR ATÉ O FIM", "Esc", this::pular, "primario");
        Button trocasBt = Ui.botao("Tática e trocas", this::abrirTrocas);
        // velocidade própria da Kings (requisito: "a kings ta muito rapido"), na mesma pílula da partida
        javafx.scene.control.ToggleGroup gv = new javafx.scene.control.ToggleGroup();
        HBox vel = new HBox();
        vel.getStyleClass().add("vel-pilula");
        vel.setAlignment(Pos.CENTER_LEFT);
        var atual = App.automatico ? null : br.futebas.ui.Opcoes.velocidadeKings();
        for (var v : br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade.visiveis()) {
            javafx.scene.control.ToggleButton b = new javafx.scene.control.ToggleButton(v.nome);
            b.getStyleClass().add("vel-opcao");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setToggleGroup(gv);
            b.setFocusTraversable(false);
            b.setSelected(v == atual);
            b.setOnAction(ev -> {
                if (!b.isSelected()) b.setSelected(true);
                if (!App.automatico) br.futebas.ui.Opcoes.velocidadeKings(v);
                acomp.velocidade(AcompanhamentoKings.ms(v));
            });
            vel.getChildren().add(b);
        }
        vel.setMinWidth(Region.USE_PREF_SIZE);
        Label rotVel = Ui.rotulo("Velocidade", "rotulo-rodape");
        rotVel.setMinWidth(Region.USE_PREF_SIZE);
        HBox rodape = Ui.rodape(rotVel, vel, aviso, Ui.espaco(), botoes, trocasBt, pular, seguir);

        // camadas: letreiro curto, suspense da cobrança e a disputa de shootouts (as escolhas da carta são o menu rápido do BotoesKings)
        letreiro.getStyleClass().add("kings-letreiro");
        letreiro.setMaxWidth(640);
        letreiro.setWrapText(true);
        camadaLetreiro.setMouseTransparent(true);
        camadaLetreiro.setVisible(false);
        StackPane.setAlignment(letreiro, Pos.TOP_CENTER);
        camadaLetreiro.setStyle("-fx-padding: 84 0 0 0;");
        VBox caixaSuspense = new VBox(8, suspenseTitulo, suspensePasso);
        caixaSuspense.setAlignment(Pos.CENTER);
        caixaSuspense.getStyleClass().add("suspense-faixa");
        caixaSuspense.setMaxSize(600, Region.USE_PREF_SIZE);
        camadaSuspense.getChildren().add(caixaSuspense);
        camadaSuspense.setMouseTransparent(true);
        camadaSuspense.setVisible(false);
        camadaDisputa.setMouseTransparent(true);
        camadaDisputa.setVisible(false);
        camadaTrocas.setVisible(false);
        camadaTrocas.setStyle("-fx-background-color: rgba(0,0,0,0.45);");
        camadaOrdem.setVisible(false);
        camadaOrdem.setStyle("-fx-background-color: rgba(0,0,0,0.45);");
        getChildren().addAll(new BorderPane(centro, faixa, null, rodape, null), camadaLetreiro, camadaSuspense, camadaDisputa, camadaTrocas, camadaOrdem);
        seguir.setDisable(true);
        // shoot out: o técnico escolhe a ordem dos batedores (a mesma tela do futebol)
        acomp.aoPedirOrdem(sug -> Platform.runLater(() -> abrirOrdem(sug)));
    }

    boolean emJogo() { return emJogo; }

    @Override
    public List<String> ordemShootout(int lado, List<LigaKings.AtletaKings> sugestao) {
        return acomp.ordemShootout(lado, sugestao);
    }

    /** A tela de ordem do shoot out (o motor espera); nas capturas confirma sozinha. */
    private void abrirOrdem(List<LigaKings.AtletaKings> sug) {
        if (ordemAberta != null || acomp.pulando()) {
            if (ordemAberta == null) acomp.responderOrdem(null);
            return;
        }
        String meu = acomp.meuLado() == 1 ? nome(idB) : nome(idA), rival = acomp.meuLado() == 1 ? nome(idA) : nome(idB);
        VBox cab = new VBox(2, Ui.rotulo("Shoot out", "titulo-tela"), Ui.rotulo(meu + " x " + rival + "  ·  escolha a ordem dos batedores", "texto-medio"));
        OrdemPenaltis o = ShootoutNarrado.ordem(cab, sug, this::responderOrdem);
        ordemAberta = o;
        camadaOrdem.getChildren().setAll(o);
        camadaOrdem.setVisible(true);
        if (App.automatico) {
            PauseTransition t = new PauseTransition(Duration.millis(1200));
            t.setOnFinished(x -> {
                if (ordemAberta == o) o.confirmar();
            });
            t.play();
        }
    }

    private void responderOrdem(List<String> chaves) {
        if (ordemAberta == null) return;
        ordemAberta = null;
        camadaOrdem.setVisible(false);
        camadaOrdem.getChildren().clear();
        acomp.responderOrdem(chaves);
    }

    private final StackPane camadaOrdem = new StackPane();

    /** Testes e capturas: a escolha da ordem do shoot out aberta e as bolinhas. */
    OrdemPenaltis ordemAberta() { return ordemAberta; }

    String[] bolinhasShootout() { return shootout.ativo() ? shootout.resumo() : null; }

    // ------------------------------------------------------------------ AoVivoKings (thread do motor)
    @Override
    public void minuto(ControleKings c) {
        if (!emJogo) comecar();
        acomp.minuto(c);
    }

    @Override
    public void pausa(ControleKings c, String motivo) {
        if (!emJogo) comecar();
        acomp.pausa(c, motivo);
        if ("fim".equals(motivo)) {
            emJogo = false;
            Platform.runLater(() -> setVisible(false));
        }
    }

    @Override
    public void cobranca(ControleKings c, AoVivoKings.Cobranca k) {
        if (!emJogo) comecar();
        acomp.cobranca(c, k);
    }

    /** Todos os jogos da Kings que podem ser do técnico: ligas (Split, Cup) e Copas do Mundo. */
    private List<Torneio> torneiosKings() {
        List<Torneio> l = new ArrayList<>();
        for (LigaKings x : m.kings) l.addAll(x.competicoes);
        l.addAll(m.kingsMundiais);
        return l;
    }

    /** Descobre o jogo (o próximo não jogado do time do técnico, em qualquer competição da Kings) e o lado dele. */
    private void comecar() {
        emJogo = true;
        vistos = 0;
        String meu = m.tecnico == null ? null : m.tecnico.kingsTimeId;
        Partida p = null;
        Torneio dono = null;
        if (meu != null) for (Torneio t : torneiosKings())
            for (Partida q : t.partidas)
                if (!q.jogada && q.envolve(meu) && (p == null || q.sa < p.sa || q.sa == p.sa && q.data < p.data)) {
                    p = q;
                    dono = t;
                }
        torneio = dono;
        liga = null;
        if (dono != null) for (LigaKings l : m.kings) if (l.competicoes.contains(dono)) liga = l;
        partida = p;
        idA = p == null ? null : p.mandante;
        idB = p == null ? null : p.visitante;
        int lado = p == null ? 0 : meu.equals(p.mandante) ? 0 : 1;
        acomp.novoJogo(lado);
        for (int s = 0; s < 2; s++) {
            tipoCarta[s] = null;
            cartaUsada[s] = false;
            resultadoCarta[s] = null;
            estrela[s] = null;
            suspenso[s] = null;
        }
        ultimaCobranca = null;
        String a = idA, b = idB;
        Partida jogo = p;
        Platform.runLater(() -> {
            lances.getChildren().clear();
            nomeA.setText(nome(a));
            nomeB.setText(nome(b));
            escA.getChildren().setAll(Ui.escudo(a, 84));
            escB.getChildren().setAll(Ui.escudo(b, 84));
            // sua carta (botão pequeno, virada até o motor sortear) e a do adversário virada com "?"
            String meuT = lado == 0 ? a : b, rivalT = lado == 0 ? b : a;
            lateral.novoJogo(rivalT == null ? null : Ui.escudo(rivalT, 14), nome(rivalT));
            LigaKings.TimeKings tk = timeKings(meuT);
            botoes.novoJogo(tk == null ? null : tk.batedorPresidente != null ? tk.batedorPresidente : tk.presidente);
            resultadoPresidente = null;
            camadaDisputa.setVisible(false);
            camadaDisputa.getChildren().clear();
            shootout.esconder();
            Torneio t = torneio;
            String f = t != null && jogo != null ? Textos.fase(t, jogo) : "";
            rodadaTitulo.setText((f == null || f.isBlank() ? "Kings League" : f).toUpperCase(Locale.ROOT));
            rodadaSub.setText((t == null ? "Kings League" : Textos.curto(t.nome)) + "  ·  7 contra 7");
            logoComp.getChildren().setAll(t == null ? Imagens.logoKings(liga == null ? null : liga.id, 56) : Imagens.trofeu(t.id, t.ambito, t.pais, 64));
            seguir.setText("Continuar");
            minutoAtual = 0;
            progresso();
            tabela();
            setVisible(true);
            App.atual().cena().getRoot().requestFocus();
        });
    }

    private CartaKings cartaDoLado(int lado) { return lado == acomp.meuLado() ? botoes.carta() : lateral.rival(); }

    private LigaKings.TimeKings timeKings(String id) {
        if (id == null) return null;
        for (LigaKings l : m.kings) {
            LigaKings.TimeKings t = l.time(id);
            if (t != null) return t;
        }
        return null;
    }

    private String nome(String id) {
        if (id == null) return "";
        LigaKings.TimeKings t = timeKings(id);
        return t == null ? id : t.nome;
    }

    private String meuId() { return acomp.meuLado() == 0 ? idA : idB; }

    private String presidenteNome() {
        LigaKings.TimeKings t = timeKings(meuId());
        return t == null || t.presidente == null ? "" : ": " + t.presidente;
    }

    private void progresso() {
        if (!(trilhaCheia.getParent() instanceof Region p)) return;
        trilhaCheia.setPrefWidth(p.getWidth() * Math.min(1, minutoAtual / 40.0));
    }

    /** Tabela da fase atual da competição (no mata-mata, o nome da fase). */
    private void tabela() {
        tabelaCorpo.getChildren().clear();
        Torneio t = torneio;
        if (t == null) return;
        FaseExec f = t.faseAtual();
        tituloTabela.setText(f != null && f.mataMata() ? "FASE" : "TABELA");
        if (f == null || f.mataMata()) {
            tabelaCorpo.getChildren().add(Ui.paragrafo((f == null || f.nome == null ? "Mata-mata" : f.nome) + ": jogo único, quem vence avança.", "texto-medio"));
            return;
        }
        int grupo = partida == null ? 0 : Math.max(0, partida.grupo);
        List<LinhaTabela> ls = t.tabela(f.indice, f.numGrupos > 1 ? grupo : 0);
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(3);
        g.getStyleClass().add("tabela-vivo");
        g.addRow(0, Ui.rotulo("#", "texto-pequeno"), Ui.rotulo("Clube", "texto-pequeno"), Ui.rotulo("J", "texto-pequeno"),
                Ui.rotulo("SG", "texto-pequeno"), Ui.rotulo("P", "texto-pequeno"));
        Zonas zonas = Zonas.de(t, f, ls.size());
        String meu = meuId();
        int r = 1;
        for (LinhaTabela l : ls) {
            Label pos = Ui.rotulo(String.valueOf(r), "zona");
            Zonas.Zona zz = zonas.porPosicao.get(r);
            if (zz != null) pos.getStyleClass().add(zz.classe());
            HBox cl = Ui.clube(m, l.clubeId, 16);
            cl.setMinWidth(0);
            if (cl.getChildren().size() > 1 && cl.getChildren().get(1) instanceof Label nome) {
                if (l.clubeId.equals(meu)) nome.setStyle("-fx-font-weight: bold; -fx-text-fill: -acento-nav;");
                else if (l.clubeId.equals(idA) || l.clubeId.equals(idB)) nome.setStyle("-fx-font-weight: bold;");
            }
            Label pts = Ui.rotulo(String.valueOf(l.pontos), "pontos-destaque");
            pts.setStyle("-fx-font-weight: bold;");
            g.addRow(r++, pos, cl, Ui.rotulo(String.valueOf(l.jogos), "numero"), Ui.rotulo(String.valueOf(l.saldo()), "numero"), pts);
            GridPane.setHgrow(cl, Priority.ALWAYS);
        }
        tabelaCorpo.getChildren().add(g);
    }

    // ------------------------------------------------------------------ tela
    private void mostrar(AcompanhamentoKings.Estado e) {
        ultimo = e;
        if (camadaTrocas.isVisible()) {
            atualizarTrocas();
            atualizarResumo();
        }
        golsA.setText(String.valueOf(e.golsA()));
        golsB.setText(String.valueOf(e.golsB()));
        if (!shootout.ativo()) relogio.setText(e.minuto() + "'");
        minutoAtual = e.minuto();
        progresso();
        emCampo.setText(e.emCampoA() + " x " + e.emCampoB() + " em campo");
        fase.setText(textoFase(e.fase(), e.dado()));
        // shoot out do desempate: limpa a narração e começa as bolinhas (antes dos lances deste estado)
        if ("shootouts".equals(e.pausa()) || e.cobranca() != null && "disputa".equals(e.cobranca().tipo())) shootout.comecar(nome(idA), nome(idB));
        List<EventoJogo> ev = e.eventos();
        for (int i = vistos; i < ev.size(); i++) lance(ev.get(i));
        vistos = ev.size();
        if (e.cobranca() != null && e.cobranca() != ultimaCobranca) {
            ultimaCobranca = e.cobranca();
            suspense(e.cobranca());
        }
        atualizarCartas(e);
        lateral.fase(textoFase(e.fase(), e.dado()), e.minuto());
        efeitos();
        String p = e.pausa();
        seguir.setDisable(p == null || !("intervalo".equals(p) || "fim".equals(p)));
        if ("fim".equals(p)) {
            tabela();
            seguir.setText("Continuar"); // sempre para a tela de fim (o 2º jogo da semana fica para o próximo "Jogar")
        }
        if (p != null && ("intervalo".equals(p) || "fim".equals(p)) && App.automatico) {
            PauseTransition t = new PauseTransition(Duration.millis("fim".equals(p) ? 1500 : 600));
            t.setOnFinished(x -> acomp.seguir());
            t.play();
        }
        if (p != null) switch (p) {
            case "carta_disponivel" -> letreiro("A janela da carta secreta abriu: use a carta no botão ao lado de \"Tática e trocas\".");
            case "dado" -> letreiro("O DADO decidiu: " + (e.dado() == null ? "" : e.dado()) + "!");
            case "match_ball" -> letreiro("MATCH BALL: vence quem chegar a " + (Math.max(e.golsA(), e.golsB()) + 1) + " gols.");
            case "shootouts" -> letreiro("Empate: decisão no SHOOT OUT!");
            default -> { }
        }
        aviso.setText(p == null ? "" : switch (p) {
            case "intervalo" -> "Intervalo. Continue quando quiser.";
            case "fim" -> outroJogoNaSemana() ? "Fim de jogo. Há outro jogo seu nesta semana: ele começa depois da central." : "Fim de jogo. Continue para ver o resumo.";
            default -> "";
        });
    }

    /** O time do técnico tem outro jogo da Kings nesta mesma semana (formato com rodada dupla)? */
    private boolean outroJogoNaSemana() {
        String meu = meuId();
        if (meu == null) return false;
        int sa = m.semanaAbsoluta();
        for (Torneio t : torneiosKings()) for (Partida q : t.partidas) if (!q.jogada && q.sa == sa && q.envolve(meu) && q != partida) return true;
        return false;
    }

    private void atualizarCartas(AcompanhamentoKings.Estado e) {
        botoes.atualizar(e);
        int eu = acomp.meuLado();
        lateral.armas(armas(e, resultadoCarta[eu], resultadoPresidente));
    }

    /** Os cartões do quadro "Suas armas" (também no multi): estado da carta e do pênalti do presidente. */
    static PainelKingsLateral.Arma[] armas(AcompanhamentoKings.Estado e, String resultadoCartaMeu, String resultadoPresidente) {
        MotorKings.Carta c = e.minhaCarta();
        int min = e.minuto();
        PainelKingsLateral.Arma carta;
        if (c == null) {
            carta = new PainelKingsLateral.Arma("Carta secreta", "USADA", "usada",
                    resultadoCartaMeu == null ? "Você já usou a sua carta neste jogo." : resultadoCartaMeu);
        } else if (e.podeUsarCarta()) {
            carta = new PainelKingsLateral.Arma("Carta: " + c.nome, "USE AGORA", "ok",
                    efeitoCarta(c) + " Fecha no " + (min < 17 ? "17'" : "36'") + ".");
        } else {
            String quando = min < 5 ? "Libera no 5' (faltam " + (5 - min) + " min)."
                    : min < 23 ? "Pausada no gol duplo e no dado. Volta no 23' (faltam " + (23 - min) + " min)."
                    : "O tempo de usar acabou (só até o 35').";
            boolean fim = min >= 36;
            carta = new PainelKingsLateral.Arma("Carta: " + c.nome, fim ? "FECHADA" : "AGUARDE", fim ? "usada" : "espera",
                    efeitoCarta(c) + " " + quando);
        }
        PainelKingsLateral.Arma pres;
        if (resultadoPresidente != null) {
            pres = new PainelKingsLateral.Arma("Pênalti do presidente", "USADO", "usada", resultadoPresidente);
        } else if (e.podePresidente()) {
            pres = new PainelKingsLateral.Arma("Pênalti do presidente", "USE AGORA", "ok",
                    "O presidente cobra um pênalti. Vale até o 35', fora do gol duplo e do dado.");
        } else {
            String quando = min < 5 ? "Libera no 5' (faltam " + (5 - min) + " min)."
                    : min >= 17 && min < 23 ? "Pausado no gol duplo e no dado. Volta no 23' (faltam " + (23 - min) + " min)."
                    : min >= 36 ? "O tempo de usar acabou (só até o 35')."
                    : "Seu time não tem presidente para cobrar.";
            boolean fim = min >= 36;
            pres = new PainelKingsLateral.Arma("Pênalti do presidente", fim ? "FECHADO" : "AGUARDE", fim ? "usada" : "espera", quando);
        }
        return new PainelKingsLateral.Arma[] {carta, pres};
    }

    /** O que a carta faz, em uma frase (quadro "Suas armas", requisito: "deixe isso mais claro"). */
    private static String efeitoCarta(MotorKings.Carta c) {
        return switch (c) {
            case GOL_DUPLO, CORINGA -> "Seus gols valem 2 por 4 minutos.";
            case SANCAO -> "Um jogador do rival fica 4 minutos fora.";
            case PENALTI_SHOOTOUT -> "Seu time ganha um shoot out.";
            case JOGADOR_ESTRELA -> "Escolha um jogador: os gols dele valem 2.";
            case PENALTI -> "Seu time ganha um pênalti.";
            case PENALTI_INVERTIDO -> "O rival cobra um pênalti; se errar, o gol é seu.";
        };
    }

    private void efeitos() {
        List<String> l = new ArrayList<>();
        for (int s = 0; s < 2; s++) {
            String time = s == 0 ? nome(idA) : nome(idB);
            if (estrela[s] != null) l.add("⭐ " + estrela[s] + " (" + time + "): gols valem 2");
            if (suspenso[s] != null) l.add("Suspenso: " + suspenso[s] + " (" + time + "), volta em " + Math.max(0, suspensoAte[s] - minutoAtual) + "'");
        }
        lateral.efeitos(String.join("\n", l));
    }

    static String textoFase(String f, String dado) {
        if (f == null) return "";
        return switch (f) {
            case "rampa" -> "Rampa inicial: os times entram aos poucos (1v1 até 5v5)";
            case "gol_duplo" -> "Gol duplo: cada gol vale 2";
            case "dado" -> "Dado: " + (dado == null ? "" : dado);
            case "match_ball" -> "Match ball";
            default -> "7 contra 7";
        };
    }

    // ------------------------------------------------------------------ letreiros e suspense
    private void letreiro(String texto) {
        letreiro.setText(texto);
        camadaLetreiro.setVisible(true);
        if (apagarLetreiro != null) apagarLetreiro.stop();
        apagarLetreiro = new PauseTransition(Duration.millis(App.automatico ? 300 : 2200));
        apagarLetreiro.setOnFinished(x -> camadaLetreiro.setVisible(false));
        apagarLetreiro.play();
    }

    private static SuspensePenalti.Desfecho desfecho(String res) {
        return switch (res == null ? "" : res) {
            case "GOL" -> SuspensePenalti.Desfecho.GOL;
            case "DEFENDEU" -> SuspensePenalti.Desfecho.DEFESA;
            default -> SuspensePenalti.desfecho(false, "fora");
        };
    }

    /** As etapas da cobrança ("vai para a bola…", "bateu…", o resultado) enquanto o motor espera. */
    private void suspense(AoVivoKings.Cobranca k) {
        long[] ms = acomp.esperasSuspense();
        SuspensePenalti.Desfecho d = desfecho(k.resultado());
        if ("disputa".equals(k.tipo())) {
            // como a disputa de pênaltis do futebol: uma linha por vez na narração, bolinhas no placar
            shootout.cobranca(k.lado(), k.cobrador(), d, k.goleiro(), ms[0]);
            return;
        }
        // pênalti na narração (requisito): uma linha por vez, no ritmo fixo de ~1,2 s, como no futebol
        String titulo = switch (k.tipo()) {
            case "penalti_carta" -> "Pênalti da carta para o " + k.time() + "!";
            case "penalti_invertido" -> "Pênalti invertido: o " + k.time() + " bate!";
            case "penalti_presidente" -> "Pênalti do presidente do " + k.time() + "!";
            default -> "Shoot out do " + k.time() + "!";
        };
        String res = SuspensePenalti.fraseResultado(d, k.goleiro());
        if ("penalti_invertido".equals(k.tipo())) res = d.gol() ? "Converteu… nada acontece." : res + " Errou: gol para o adversário!";
        if (toque != null && !toque.acabou()) toque.pular();
        camadaSuspense.setVisible(false);
        boolean golDaCobranca = "penalti_invertido".equals(k.tipo()) ? !d.gol() : d.gol();
        boolean casa = k.lado() == 0;
        String min = minutoAtual + "'", ida = SuspensePenalti.fraseIda(k.cobrador()), batida = SuspensePenalti.fraseBatida(), fim = res;
        List<Runnable> linhas = List.of(
                () -> lances.getChildren().add(TelaPartida.linhaPasso(casa, min, titulo, "penalti-marcado")),
                () -> lances.getChildren().add(TelaPartida.linhaPasso(casa, min, ida, null)),
                () -> lances.getChildren().add(TelaPartida.linhaPasso(casa, min, batida, null)),
                () -> lances.getChildren().add(TelaPartida.linhaPasso(casa, min, fim, golDaCobranca ? "penalti-gol" : "penalti-erro")));
        toque = SuspensePenalti.emLinhas(linhas.size(), ms[0], x -> linhas.get(x).run(), null);
    }

    // ------------------------------------------------------------------ narração
    /** Linha de baixo do lance (o nome do jogador vai em cima, como na partida). */
    private static String detalheLance(String tipo, String time) {
        return switch (tipo) {
            case "shootout_perdido" -> "Shoot out perdido";
            case "penalti_presidente_perdido" -> "Pênalti do presidente: perdeu";
            case "amarelo" -> "Amarelo: 2 minutos fora";
            case "vermelho" -> "Vermelho: 5 minutos fora";
            case "disputa_gol" -> "Disputa: marcou";
            case "disputa_perdido" -> "Disputa: perdeu";
            default -> "";
        };
    }

    private static String resultadoCurto(String tipo) {
        return tipo.endsWith("_gol") ? "GOL" : tipo.endsWith("_defendeu") ? "DEFENDEU" : "PRA FORA";
    }

    private void lance(EventoJogo e) {
        if ("disputa_gol".equals(e.tipo()) || "disputa_perdido".equals(e.tipo())) {
            // o shoot out já está na narração, cobrança a cobrança: o evento só garante a bolinha
            shootout.comecar(nome(idA), nome(idB));
            shootout.evento(e.lado(), "disputa_gol".equals(e.tipo()));
            return;
        }
        String quem = e.jogador() == null ? "" : e.jogador();
        String time = e.lado() == 0 ? nome(idA) : nome(idB);
        boolean meu = e.lado() == acomp.meuLado();
        String min = e.minuto() + "'";
        String tg = e.tipo();
        int lado = e.lado() == 1 ? 1 : 0;
        // fases, dado e cartas: a linha de fase da partida (traço — minuto — texto — traço)
        String faseTxt = switch (tg) {
            case "carta_usada" -> "Carta secreta do " + time + ": " + quem;
            case "carta_sorteada" -> meu ? "Sua carta secreta: " + quem : null;
            case "dado" -> "O dado rolou: " + quem;
            case "fase" -> quem;
            case "jogador_estrela" -> "⭐ " + quem + " é o jogador estrela do " + time + ": gols valem 2";
            case "suspensao" -> "Suspensão: " + quem + " (" + time + ") fora por 4 minutos";
            case "suspensao_fim" -> quem + " volta ao jogo (" + time + ")";
            default -> null;
        };
        switch (tg) {
            case "carta_sorteada" -> tipoCarta[lado] = cartaPorNome(quem);
            case "carta_usada" -> {
                cartaUsada[lado] = true;
                MotorKings.Carta c = cartaPorNome(quem.contains("→") ? quem.substring(quem.indexOf("→") + 1).trim() : quem);
                CartaKings k = cartaDoLado(lado);
                if (lado != acomp.meuLado()) {
                    tipoCarta[lado] = c;
                    lateral.resultadoRival((c == null ? quem : c.nome) + " · usada no " + min);
                }
                if (k != null && c != null) {
                    if (!k.aberta() || k.carta() != c) k.virar(c);
                    PauseTransition t = new PauseTransition(Duration.millis(380));
                    t.setOnFinished(x -> usadaNoLado(lado, resultadoCarta[lado]));
                    t.play();
                }
                letreiro("Carta secreta do " + time + ": " + quem + "!");
            }
            case "jogador_estrela" -> {
                estrela[lado] = quem;
                cartaFeita(lado, "⭐ " + quem);
            }
            case "suspensao" -> {
                // a suspensão é do rival de quem usou a carta
                suspenso[lado] = quem;
                suspensoAte[lado] = e.minuto() + 4;
                cartaFeita(1 - lado, "SUSPENDEU " + quem);
                letreiro("Suspensão: " + quem + " fora por 4 minutos");
            }
            case "suspensao_fim" -> suspenso[lado] = null;
            case "penalti_presidente_gol", "penalti_presidente_perdido" -> {
                if (lado == acomp.meuLado()) {
                    resultadoPresidente = tg.endsWith("_gol") ? "GOL" : "PERDEU";
                    botoes.presidenteCobrado(resultadoPresidente);
                }
            }
            case "penalti_carta_gol", "penalti_carta_defendeu", "penalti_carta_fora" -> {
                cartaFeita(lado, resultadoCurto(tg));
                if (!tg.endsWith("_gol")) {
                    // erro: a linha com cobrador, goleiro e resultado (no gol, a linha do gol mostra)
                    lances.getChildren().add(TelaPartida.linhaEvento(lado == 0, min, null, "Pênalti da carta: " + quem, null, null));
                }
                letreiro("PÊNALTI DA CARTA: " + quem.replace(" · ", "… ") + "!");
            }
            case "penalti_invertido_gol", "penalti_invertido_defendeu", "penalti_invertido_fora" -> {
                boolean converteu = tg.endsWith("_gol");
                ultimoResultadoInvertido = quem;
                cartaFeita(lado, converteu ? "CONVERTEU" : "ERROU · GOL");
                String txt = "Pênalti invertido: " + quem + (converteu ? " — converteu, nada acontece" : " — errou! Gol para o " + time);
                if (converteu) lances.getChildren().add(TelaPartida.linhaEvento(lado == 0, min, null, txt, null, null));
                letreiro(txt);
            }
            case "shootout_gol", "shootout_perdido" -> {
                if (cartaUsada[lado] && resultadoCarta[lado] == null && tipoCarta[lado] == MotorKings.Carta.PENALTI_SHOOTOUT)
                    cartaFeita(lado, tg.endsWith("_gol") ? "GOL" : "ERROU");
            }
            default -> { }
        }
        if (faseTxt != null) {
            lances.getChildren().add(TelaPartida.linhaFase(min, faseTxt));
            return;
        }
        // chances que não viram gol: legendas do banco comum (o mesmo do futebol)
        if (tg.startsWith("chance_")) {
            String chutador = quem, goleiro = null;
            if (quem.contains("|")) {
                chutador = quem.substring(0, quem.indexOf('|'));
                goleiro = quem.substring(quem.indexOf('|') + 1);
                if (goleiro.isBlank()) goleiro = null;
            }
            String tipo = switch (tg) {
                case "chance_defesa" -> "defesa";
                case "chance_trave" -> "trave";
                default -> "fora";
            };
            int[] placar = placarAte(e);
            Frases.Contexto c = new Frases.Contexto(e.minuto() * 90 / 40, 0, acomp.meuLado() < 0 ? null : meu, placar[lado], placar[1 - lado],
                    chutador, goleiro, time, "chance_bloqueio".equals(tg) ? "rebote" : null);
            String legenda = "chance_bloqueio".equals(tg) ? "Bloqueado! " + frases.chance("fora", c) : frases.chance(tipo, c);
            lances.getChildren().add(TelaPartida.linhaChance(lado == 0, min, chutador, legenda, acomp.meuLado() < 0 ? null : meu));
            return;
        }
        // gol (requisito): bola, "GOL DO TIME!!" e o contexto embaixo — também shoot-out, presidente e cartas
        if (tg.equals("gol") || tg.equals("gol_x2") || tg.startsWith("penalti_gol") || tg.startsWith("shootout_gol") || tg.startsWith("penalti_presidente_gol")) {
            String base = tg.endsWith("_x2") ? tg.substring(0, tg.length() - 3) : tg;
            boolean x2 = tg.endsWith("_x2");
            String autor = switch (base) {
                case "penalti_gol" -> quem.isBlank()
                        ? "Pênalti invertido: " + (ultimoResultadoInvertido == null ? "o rival errou" : ultimoResultadoInvertido.replaceAll(" · .*", "") + " errou")
                        : "Pênalti da carta: " + quem;
                case "shootout_gol" -> "Shoot out de " + (quem.isBlank() ? time : quem);
                case "penalti_presidente_gol" -> "Pênalti do presidente" + (quem.isBlank() ? "" : ": " + quem);
                default -> quem;
            };
            if (x2) autor = (estrela[lado] != null && estrela[lado].equals(quem) ? "GOL DUPLO de " + quem + " (jogador estrela)" : autor + " · gol duplo, vale 2");
            int[] pl = placarAte(e);
            pl[lado] += e.valor();
            Frases.Contexto gc = new Frases.Contexto(e.minuto() * 90 / 40, 0, acomp.meuLado() < 0 ? null : meu, pl[lado], pl[1 - lado], quem, null, time, null);
            Node linhaGol = TelaPartida.linhaGolTitulo(e.lado() == 0, min, frases.tituloGol(gc), autor, null, acomp.meuLado() < 0 ? null : meu);
            lances.getChildren().add(linhaGol);
            // entrada animada do gol (não com SIMULAR ATÉ O FIM nem na Ultra rápida)
            var vel = br.futebas.ui.Opcoes.velocidadeKings();
            if (!acomp.pulando() && !App.automatico && vel != br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade.ULTRA) {
                TelaPartida.animarGol(linhaGol);
                TelaPartida.pulsar(e.lado() == 0 ? golsA : golsB);
            }
            return;
        }
        String det = detalheLance(tg, time);
        if (det.isEmpty()) return;
        boolean gol = tg.contains("gol") && !tg.contains("perdido");
        String icone = gol ? "gol" : "amarelo".equals(tg) ? "cartao" : "vermelho".equals(tg) ? "vermelho" : null;
        String classe = gol ? (meu ? "evento-gol" : "evento-gol-contra") : "vermelho".equals(tg) ? "evento-vermelho" : null;
        lances.getChildren().add(TelaPartida.linhaEvento(e.lado() == 0, min, quem.isBlank() ? null : quem, det, icone, classe));
    }

    /** Placar (gols) até o evento, pela lista de eventos do jogo. */
    private int[] placarAte(EventoJogo alvo) {
        int[] p = new int[2];
        if (ultimo == null) return p;
        for (EventoJogo x : ultimo.eventos()) {
            if (x == alvo) break;
            if (x.gol() && x.lado() >= 0 && x.lado() <= 1) p[x.lado()] += x.valor();
        }
        return p;
    }

    /** O resultado da carta do lado ("GOL", "ERROU", "⭐ X"...): aparece na carta ("USADA · GOL"). */
    private void cartaFeita(int lado, String resultado) {
        resultadoCarta[lado] = resultado;
        if (lado != acomp.meuLado()) lateral.resultadoRival((tipoCarta[lado] == null ? "Carta" : tipoCarta[lado].nome) + " · " + resultado);
        CartaKings k = cartaDoLado(lado);
        if (k != null && cartaUsada[lado]) {
            PauseTransition t = new PauseTransition(Duration.millis(420));
            t.setOnFinished(x -> usadaNoLado(lado, resultado));
            t.play();
        }
    }

    /** A carta do lado fica cinza com "USADA · resultado" (a sua, no botão; a do adversário, na coluna). */
    private void usadaNoLado(int lado, String resultado) {
        if (lado == acomp.meuLado()) botoes.cartaUsada(resultado);
        else if (lateral.rival() != null) lateral.rival().usada(resultado);
    }

    private static MotorKings.Carta cartaPorNome(String nome) {
        if (nome == null) return null;
        String n = nome.trim();
        for (MotorKings.Carta c : MotorKings.Carta.values()) if (c.nome.equalsIgnoreCase(n)) return c;
        for (MotorKings.Carta c : MotorKings.Carta.values()) if (n.startsWith(c.nome)) return c;
        return null;
    }

    // ------------------------------------------------------------------ trocas (ilimitadas, de volta)
    private final StackPane camadaTrocas = new StackPane();
    private br.futebas.ui.Sessao sessao;
    private TrocasAoVivo trocas;
    /** Ids das trocas (a tela de trocas usa int; a Kings usa a chave do atleta). */
    private final java.util.Map<String, Integer> idDe = new java.util.HashMap<>();
    private final List<LigaKings.AtletaKings> porId = new ArrayList<>();

    /** A sessão (fotos e cores do time): a {@code TelaPartida} passa ao criar. */
    void sessao(br.futebas.ui.Sessao s) { this.sessao = s; }

    private int id(LigaKings.AtletaKings a) {
        return idDe.computeIfAbsent(a.chave(), k -> {
            porId.add(a);
            return porId.size() - 1;
        });
    }

    /**
     * Trocas da Kings na mesma tela do futebol ({@link TrocasAoVivo} com o campo de 7): ilimitadas,
     * quem sai pode voltar (o goleiro também); arrastar/clicar só prepara, "Confirmar" aplica. O jogo
     * espera enquanto a tela está aberta. Rodapé: só Voltar e Confirmar.
     */
    private void abrirTrocas() {
        if (!emJogo || sessao == null) return;
        acomp.pausarTrocas(true);
        if (trocas == null) {
            trocas = new TrocasAoVivo(sessao, new TrocasAoVivo.Fonte() {
                @Override
                public void aplicar(List<TrocasAoVivo.Mudanca> ms) {
                    for (TrocasAoVivo.Mudanca x : ms) {
                        if (!x.substituicao() || x.a() >= porId.size() || x.b() >= porId.size()) continue;
                        acomp.substituir(porId.get(x.a()).chave(), porId.get(x.b()).chave());
                    }
                }

                @Override public boolean trocaDePosicao() { return false; }

                @Override
                public Node foto(int i, double w, double h) {
                    if (i < 0 || i >= porId.size()) return null;
                    LigaKings.AtletaKings a = porId.get(i);
                    var j = a.jogadorId == null ? null : m.jogador(a.jogadorId);
                    var tk = sessao.jogo.meuTimeKings();
                    String foto = j != null ? sessao.jogo.foto(j.id) : a.foto;
                    return br.futebas.ui.comp.Foto.kings(foto, j == null ? null : j.dbId, tk == null ? null : tk.id, a.nome, w, h);
                }

                @Override public double encaixe(int i, Posicao natural, Posicao vaga) { return 1; }

                @Override
                public String cor() {
                    var tk = sessao.jogo.meuTimeKings();
                    return tk == null ? null : tk.cor1;
                }

                @Override public Integer capitao() { return -1; }
            });
            Button voltar = Ui.botaoTecla("Voltar", "Esc", this::voltarDasTrocas);
            trocas.aoConfirmar(this::fecharTrocas);
            // a mesma camada do futebol: quase a janela toda, com os números e a tabela ao vivo à direita
            camadaTrocas.getChildren().clear();
            resumo = new ResumoAoVivo(sessao);
            TelaPartida.montarCaixaTrocas(camadaTrocas, Ui.linha(12, Ui.rotulo("Trocas", "titulo-tela"), Ui.espaco(),
                    Ui.info("Ilimitadas: quem sai pode voltar, o goleiro também. Arraste um reserva para a vaga; nada muda até Confirmar.")),
                    null, trocas.raiz(), resumo.raiz(), Ui.linha(10, Ui.espaco(), voltar, trocas.botaoSubstituir()), trocas::tamanho);
        }
        trocas.limpar();
        atualizarTrocas();
        atualizarResumo();
        camadaTrocas.setVisible(true);
    }

    private ResumoAoVivo resumo;

    /** Números do jogo e a tabela da competição com o placar de agora (na camada de trocas). */
    private void atualizarResumo() {
        if (resumo == null || acomp == null) return;
        List<Object[]> placares = new ArrayList<>();
        if (ultimo != null && ultimo.minuto() > 0 && idA != null && idB != null) placares.add(new Object[] {idA, idB, ultimo.golsA(), ultimo.golsB()});
        resumo.mostrar(torneio, partida, idA, idB, acomp.estatisticas(), minutoAtual + "'", placares, meuId());
    }

    /** O seu time agora (em campo e banco, com a energia), como o estado da tela de trocas. */
    private void atualizarTrocas() {
        if (trocas == null || acomp == null || acomp.meuLado() < 0) return;
        List<br.futebas.ui.modelo.AcompanhamentoPartida.Atleta> campo = new ArrayList<>(), banco = new ArrayList<>();
        for (Object[] o : acomp.elencoAoVivo) {
            LigaKings.AtletaKings a = (LigaKings.AtletaKings) o[0];
            Posicao p = a.posicao == null ? Posicao.MC : a.posicao;
            var x = new br.futebas.ui.modelo.AcompanhamentoPartida.Atleta(id(a), a.nome, p, p, (int) Math.round(a.forca), false,
                    (int) Math.round((Double) o[2]));
            ((Boolean) o[1] ? campo : banco).add(x);
        }
        trocas.atualizar(new br.futebas.ui.modelo.AcompanhamentoPartida.Estado(minutoAtual, false, false, minutoAtual, 0, false,
                nome(idA), nome(idB), idA, idB, 0, 0, List.of(), acomp.meuLado(), List.copyOf(campo), List.copyOf(banco),
                java.util.Set.of(), List.copyOf(campo), List.of(), java.util.Map.of(), 0, Integer.MAX_VALUE, null, null, null,
                null, List.of(), null));
    }

    /** Voltar: com trocas preparadas, pergunta Confirmar ou Descartar. */
    private void voltarDasTrocas() {
        if (trocas != null && trocas.temPendentes()) {
            int r = App.automatico ? 0 : Ui.modal("Trocas não confirmadas", Ui.rotulo("Há " + trocas.pendentes().size()
                    + " troca(s) preparada(s). Confirmar antes de voltar ao jogo?", "texto-medio"), "Confirmar", "Descartar");
            if (r == 0) trocas.confirmar();
            else if (r == 1) trocas.descartar();
            else return;
        }
        fecharTrocas();
    }

    private void fecharTrocas() {
        camadaTrocas.setVisible(false);
        acomp.pausarTrocas(false);
    }

    void seguir() {
        if (ordemAberta != null) {
            ordemAberta.confirmar();
            return;
        }
        acomp.seguir();
        seguir.setDisable(true);
    }

    void pular() {
        if (camadaTrocas.isVisible()) {
            if (trocas != null) trocas.confirmar();
            fecharTrocas();
        }
        acomp.pularParaOFim();
        if (toque != null && !toque.acabou()) toque.pular();
        camadaSuspense.setVisible(false);
        if (ordemAberta != null) responderOrdem(null); // ordem automática
        shootout.pular();
    }

    @SuppressWarnings("unused")
    private static Node vazio() { return new FlowPane(); }
}
