package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.kings.LigaKings;
import br.futebas.kings.MotorKings;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Posicao;
import br.futebas.partida.Concentracao;
import br.futebas.partida.Escalado;
import br.futebas.partida.Escalador;
import br.futebas.partida.Estilo;
import br.futebas.partida.Formacoes;
import br.futebas.partida.Marcacao;
import br.futebas.ui.comp.Foto;
import br.futebas.ui.comp.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Escalação e tática — a MESMA tela para um clube (11 em campo) e para um time da Kings League (7
 * em campo; requisito: "é só um futebol com 7 pessoas"). O campo com as fotos nas vagas da
 * formação, à esquerda Titulares / Reservas / Não relacionados, à direita a Tática e o cartão
 * "Capitão e cobradores". Clique numa vaga e depois noutra vaga ou num jogador da lista para trocar
 * (ou arraste). O que muda entre os dois fica no {@link Plantel}: {@link DoClube} e {@link DaKings}.
 * O campo em si é o {@link CampoEscalacao} (o mesmo da troca ao vivo). Com {@code antesDoJogo}, é o
 * passo "escalação para o jogo" que aparece antes de cada partida, com Voltar e JOGAR.
 */
final class PainelEscalacao implements Hub.Painel {

    /** Um atleta da escalação: jogador do clube ou atleta da Kings (do banco ou só da fonte). */
    record Atleta(String chave, String nome, Posicao posicao, int forcaExibida, double forca, boolean disponivel,
                  String etiqueta, Jogador jogador, String caminhoFoto, Integer dbId, String timeKings, Integer energia) {
        Node foto(double w, double h) {
            return timeKings != null ? Foto.kings(caminhoFoto, dbId, timeKings, nome, w, h) : Foto.de(caminhoFoto, dbId, w, h);
        }
    }

    /**
     * O elenco que a escalação edita. Chaves de atleta são strings (id do jogador no clube,
     * {@code AtletaKings.chave()} na Kings); vaga vazia = null.
     */
    abstract static class Plantel {
        /** Quantos jogam (11 ou 7). */
        abstract int emCampo();

        abstract String cor();

        abstract List<String> formacoes();

        abstract String formacao();

        abstract void definirFormacao(String f, List<String> onze);

        abstract Posicao[] vagas(String formacao);

        String rotulo(int i, Posicao p) { return p.name(); }

        /** Pontos do campo de cada vaga (x, y em px de W×H). */
        abstract double[][] coordenadas(String formacao, Posicao[] vagas);

        /** Todos que podem ser escalados, já ordenados. */
        abstract List<Atleta> todos();

        abstract List<String> titulares();

        abstract List<String> reservasSalvas();

        /** Reservas permitidos (o resto fica nos não relacionados). */
        abstract int tamanhoBanco();

        /** Todo mundo que não começa fica no banco (Kings: trocas ilimitadas, sem não relacionados). */
        boolean bancoTodos() { return false; }

        abstract void salvar(List<String> onze, List<String> reservas);

        abstract void automatica();

        abstract double encaixe(Atleta a, Posicao vaga);

        abstract String capitao();

        /** Força em campo (média com o encaixe). */
        double forcaEmCampo(List<Atleta> onze, Posicao[] vagas) {
            double s = 0;
            for (int i = 0; i < onze.size(); i++) {
                Atleta a = onze.get(i);
                if (a == null) continue;
                double e = encaixe(a, vagas[i]);
                s += a.forca() * (e > 0 ? e : 0.7);
            }
            return s / emCampo();
        }

        /** Linhas do cartão Tática abaixo da formação (estilo, marcação...). */
        List<Node> taticaExtra() { return List.of(); }

        /** Recarrega as listas da tela (quem pode ser escalado mudou). */
        Runnable aoMudarLista = () -> {};

        /** Cartão "Capitão e cobradores" (as opções são os titulares). */
        abstract VBox cobradores(List<Atleta> titulares, Runnable redesenhar);

        /** Cartões a mais abaixo dos cobradores. */
        List<Node> cartoesExtra() { return List.of(); }

        String ajuda() { return "Arraste entre o campo e as listas, ou clique num jogador e depois no outro para trocar."; }

        /** Texto do contador de reservas. */
        String contaReservas(int n) { return n + " de " + tamanhoBanco(); }

        /** Mostra a energia dos jogadores (clube: sim; Kings: o rodízio tem energia só dentro do jogo). */
        boolean temEnergia() { return false; }

        /** "Mandante x Visitante · competição" do próximo jogo (passo antes do jogo). */
        String proximoJogo() { return null; }
    }

    private final Hub hub;
    private Plantel plantel;
    /** Passo antes do jogo: o que fazem Voltar e JOGAR (null = a tela normal de Escalação). */
    private final Runnable voltar, jogar;
    private final CampoEscalacao campo = new CampoEscalacao(new CampoEscalacao.Acoes() {
        @Override public void clicar(int vaga) { clicarVaga(vaga); }

        @Override
        public void soltar(int vaga, String s) {
            ultimaVaga = vaga;
            if (s.startsWith("v:")) trocarVagas(Integer.parseInt(valor(s)), vaga);
            else if (s.startsWith("j:")) colocar(vaga, valor(s));
            else if (s.startsWith("r:")) doBanco(vaga, valor(s));
        }

        @Override public String arraste(int vaga) { return onze.get(vaga) == null ? null : "v:" + vaga; }

        @Override
        public void fantasma(int vaga, Dragboard db) {
            Atleta a = atleta(onze.get(vaga));
            if (a == null) return;
            PainelEscalacao.this.fantasma(db, a);
            arrastando = a.chave();
        }

        @Override
        public double encaixeDoArraste(int vaga) {
            Atleta x = atleta(arrastando);
            return x == null ? 1 : plantel.encaixe(x, plantel.vagas(plantel.formacao())[vaga]);
        }

        @Override
        public void menu(int vaga, Node dono, javafx.scene.input.ContextMenuEvent e) {
            Atleta a = atleta(onze.get(vaga));
            if (a != null) mostrarMenu(a, () -> limparVaga(vaga), dono, e);
        }

        @Override public void fimArraste() { arrastando = null; }
    });
    private final TableView<Atleta> lista = Ui.tabela();
    private final Label forcaTime = new Label();
    private final VBox cartaoCobradores = new VBox();
    private List<String> onze = new ArrayList<>();
    private List<String> reservas = new ArrayList<>();
    private final Map<String, Atleta> porChave = new LinkedHashMap<>();
    private final VBox blocoTitulares = new VBox(1), blocoReservas = new VBox(1);
    private final Label contaReservas = Ui.rotulo("", "texto-pequeno");
    private int vagaSelecionada = -1;
    /** Atleta sendo arrastado (para colorir as vagas pelo encaixe) e a vaga que acabou de receber alguém. */
    private String arrastando;
    private int ultimaVaga = -1;
    private String assinaturaCobradores = "";

    PainelEscalacao(Hub hub) {
        this(hub, null, null);
    }

    /** O passo "escalação para o jogo" (antes de cada partida): Voltar volta à central, JOGAR começa o jogo. */
    PainelEscalacao(Hub hub, Runnable voltar, Runnable jogar) {
        this.hub = hub;
        this.voltar = voltar;
        this.jogar = jogar;
    }

    boolean antesDoJogo() { return jogar != null; }

    private Jogo jogo() { return hub.jogo(); }

    private Mundo m() { return hub.jogo().mundo(); }

    private Atleta atleta(String chave) { return chave == null ? null : porChave.get(chave); }

    @Override
    public Node construir() {
        Clube c = hub.clube();
        LigaKings.TimeKings tk = c == null ? jogo().meuTimeKings() : null;
        if (c == null && tk == null) return Ui.conteudo(Ui.titulo("Escalação e tática"), Ui.rotulo("Sem time para escalar.", "texto-suave"));
        plantel = c != null ? new DoClube(hub, c) : new DaKings(hub, tk);
        plantel.aoMudarLista = () -> {
            carregar();
            desenhar();
        };
        carregar();
        campo.cor(plantel.cor());

        ChoiceBox<String> formacao = new ChoiceBox<>();
        formacao.getItems().setAll(plantel.formacoes());
        formacao.getSelectionModel().select(plantel.formacao());
        formacao.setPrefWidth(140);
        formacao.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b == null || b.equals(plantel.formacao())) return;
            plantel.definirFormacao(b, onze);
            carregar();
            desenhar();
        });
        forcaTime.getStyleClass().addAll("numero-grande", "forca-texto");
        List<Node> filhos = new ArrayList<>();
        filhos.add(Ui.linha(10, Ui.rotulo("Formação", "texto-medio"), Ui.espaco(), formacao));
        filhos.addAll(plantel.taticaExtra());
        filhos.add(Ui.separadorH());
        filhos.add(Ui.linha(8, Ui.rotulo("Força em campo", "texto-medio"), Ui.espaco(), forcaTime));
        filhos.add(Ui.botao("Escalação automática", () -> {
            plantel.automatica();
            carregar();
            desenhar();
        }));
        VBox tatica = Ui.painelAcoes("Tática", null, filhos.toArray(Node[]::new));
        VBox lateral = new VBox(16, tatica, cartaoCobradores);
        lateral.getChildren().addAll(plantel.cartoesExtra());
        lateral.setPrefWidth(262);
        lateral.setMinWidth(262);
        lateral.setMaxWidth(290);

        Ui.colPos(lista, Atleta::posicao);
        Ui.colForca(lista, "Força", Atleta::forcaExibida);
        if (plantel.temEnergia()) Ui.colNum(lista, "En.", 46, Atleta::energia);
        Ui.colNo(lista, "Jogador", 150, x -> {
            Label n = Ui.rotulo(x.nome());
            n.setMinWidth(0);
            HBox h = Ui.linha(6, n);
            if (x.etiqueta() != null) h.getChildren().add(Ui.etiqueta(x.etiqueta(), corEtiqueta(x.etiqueta())));
            listas(x, h);
            return h;
        });
        lista.setRowFactory(x -> linhaNaoRelacionado());
        // à esquerda: titulares, reservas e não relacionados (como nos jogos de técnico atuais)
        VBox pTit = Ui.painel("Titulares", blocoTitulares);
        VBox pRes = Ui.painelAcoes("Reservas", contaReservas, blocoReservas);
        pTit.setStyle("-fx-padding: 8 10 8 10; -fx-spacing: 4;");
        pRes.setStyle("-fx-padding: 8 10 8 10; -fx-spacing: 4;");
        VBox esquerda = new VBox(10, pTit, pRes);
        if (!plantel.bancoTodos()) {
            VBox pNao = Ui.painelTabela("Não relacionados", null, lista);
            VBox.setVgrow(pNao, Priority.ALWAYS);
            pNao.setMinHeight(280);
            esquerda.getChildren().add(pNao);
        } else {
            VBox.setVgrow(pRes, Priority.ALWAYS);
        }
        javafx.scene.control.ScrollPane rolEsq = new javafx.scene.control.ScrollPane(esquerda);
        rolEsq.setFitToWidth(true);
        rolEsq.setFitToHeight(true);
        rolEsq.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        rolEsq.setPrefWidth(310);
        rolEsq.setMinWidth(290);
        rolEsq.setMaxWidth(380);

        // no centro: o campo cresce com a janela mantendo a proporção
        CampoEscalacao.Suporte suporte = campo.escalavel();

        // à direita: a tática
        javafx.scene.control.ScrollPane rolDir = Ui.rolar(lateral);
        rolDir.setPrefWidth(272);
        rolDir.setMinWidth(262);
        rolDir.setMaxWidth(320);

        HBox tudo = new HBox(12, rolEsq, suporte, rolDir);
        VBox.setVgrow(tudo, Priority.ALWAYS);
        Label ajuda = Ui.info(plantel.ajuda());
        VBox v;
        if (antesDoJogo()) {
            // passo antes do jogo: o adversário no título, Voltar e JOGAR embaixo
            String jogo = plantel.proximoJogo();
            VBox titulo = new VBox(2, Ui.titulo("Escalação para o jogo"), Ui.rotulo(jogo == null ? "" : jogo, "texto-suave"));
            javafx.scene.control.CheckBox pular = new javafx.scene.control.CheckBox("Não mostrar antes dos jogos");
            pular.setFocusTraversable(false);
            pular.setSelected(!br.futebas.ui.Opcoes.escalacaoAntesDoJogo());
            pular.setOnAction(e -> br.futebas.ui.Opcoes.escalacaoAntesDoJogo(!pular.isSelected()));
            javafx.scene.control.Button bJogar = Ui.botao("JOGAR  ›", jogar, "jogar-grande");
            bJogar.setFocusTraversable(false);
            bJogar.setPrefWidth(220);
            HBox acoes = Ui.linha(12, Ui.botaoTecla("Voltar", "Esc", voltar), Ui.info("Energia: verde inteiro, vermelho cansado. Quem joga seguido cansa."),
                    Ui.espaco(), pular, bJogar);
            v = new VBox(12, Ui.linha(12, titulo, Ui.espaco(), ajuda), tudo, acoes);
        } else {
            v = new VBox(12, Ui.linha(12, Ui.titulo("Escalação e tática"), Ui.espaco(), ajuda), tudo);
        }
        v.setStyle("-fx-padding: 18 24 18 24;");
        desenhar();
        return v;
    }

    /** Cor da etiqueta: suspenso âmbar, Sub-20 azul, o resto (lesão) vermelho. */
    static String corEtiqueta(String et) {
        return "Susp.".equals(et) ? "ambar" : ETIQUETA_SUB20.equals(et) ? "azul" : "vermelha";
    }

    static final String ETIQUETA_SUB20 = "Sub-20";

    private TableRow<Atleta> linhaNaoRelacionado() {
        TableRow<Atleta> r = new TableRow<>() {
            @Override
            protected void updateItem(Atleta a, boolean vazio) {
                super.updateItem(a, vazio);
                getStyleClass().removeAll("indisponivel", "meu-clube");
                if (!vazio && a != null && !a.disponivel()) getStyleClass().add("indisponivel");
            }
        };
        r.setOnMouseClicked(e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            if (r.getItem() != null && vagaSelecionada >= 0) colocar(vagaSelecionada, r.getItem().chave());
        });
        r.setOnContextMenuRequested(e -> {
            if (r.getItem() != null) mostrarMenu(r.getItem(), null, r, e);
        });
        r.setOnDragDetected(e -> {
            if (r.getItem() == null) return;
            iniciarArraste(r, "j:" + r.getItem().chave(), r.getItem());
            e.consume();
        });
        r.setOnDragDone(e -> arrastando = null);
        r.setOnDragOver(e -> {
            String s = e.getDragboard().getString();
            if (s != null && (s.startsWith("v:") || s.startsWith("r:"))) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        r.setOnDragEntered(e -> {
            String s = e.getDragboard().getString();
            if (s != null && (s.startsWith("v:") || s.startsWith("r:")) && r.getItem() != null) r.getStyleClass().add("alvo-linha");
        });
        r.setOnDragExited(e -> r.getStyleClass().remove("alvo-linha"));
        r.setOnDragDropped(e -> {
            String s = e.getDragboard().getString();
            if (s != null && s.startsWith("v:") && r.getItem() != null) {
                int vaga = Integer.parseInt(valor(s));
                ultimaVaga = vaga;
                colocar(vaga, r.getItem().chave());
            } else if (s != null && s.startsWith("r:")) {
                // reserva volta para os não relacionados (ou troca com este)
                int k = reservas.indexOf(valor(s));
                if (k >= 0) {
                    if (r.getItem() != null) reservas.set(k, r.getItem().chave());
                    else reservas.remove(k);
                    salvar();
                }
            }
            e.setDropCompleted(true);
            e.consume();
        });
        return r;
    }

    private static String valor(String arraste) {
        return arraste.substring(arraste.indexOf(':') + 1);
    }

    private void iniciarArraste(Node origem, String conteudo, Atleta a) {
        Dragboard db = origem.startDragAndDrop(TransferMode.MOVE);
        ClipboardContent cc = new ClipboardContent();
        cc.putString(conteudo);
        db.setContent(cc);
        fantasma(db, a);
        arrastando = a.chave();
    }

    private static Label minimo(Label l) {
        l.setMinWidth(Label.USE_PREF_SIZE);
        return l;
    }

    /** Carrega os titulares e o banco do plantel (vagas vazias = null). */
    private void carregar() {
        vagaSelecionada = -1;
        porChave.clear();
        for (Atleta a : plantel.todos()) porChave.put(a.chave(), a);
        onze = new ArrayList<>();
        for (String k : plantel.titulares()) onze.add(k != null && porChave.containsKey(k) && !onze.contains(k) ? k : null);
        while (onze.size() < plantel.emCampo()) onze.add(null);
        while (onze.size() > plantel.emCampo()) onze.remove(onze.size() - 1);
        reservas = new ArrayList<>();
        for (String k : plantel.reservasSalvas()) if (porChave.containsKey(k) && !onze.contains(k) && !reservas.contains(k) && reservas.size() < plantel.tamanhoBanco()) reservas.add(k);
        completarBanco();
        assinaturaCobradores = "";
    }

    private void completarBanco() {
        if (plantel.bancoTodos()) {
            for (String k : porChave.keySet()) if (!onze.contains(k) && !reservas.contains(k)) reservas.add(k);
        }
    }

    private List<Atleta> titulares() {
        List<Atleta> l = new ArrayList<>();
        for (String k : onze) l.add(atleta(k));
        return l;
    }

    /** Monta os blocos de titulares (na ordem da formação) e de reservas (com vagas vazias). */
    private void montarBlocos(Posicao[] vagas) {
        blocoTitulares.getChildren().clear();
        for (int i = 0; i < plantel.emCampo(); i++) blocoTitulares.getChildren().add(linhaBloco(atleta(onze.get(i)), vagas[i], i, -1));
        blocoReservas.getChildren().clear();
        // banco sempre na ordem das posições (GOL, LD, ZAG, LE, VOL, MC, MEI, PE, PD, CA; o mais forte primeiro),
        // também depois de trocas e de arrastar e soltar (requisito)
        ordenarBanco();
        // só os reservas e uma vaga vazia para soltar (as 12 linhas vazias empurravam os não relacionados)
        int vagasBanco = plantel.bancoTodos() ? reservas.size() : Math.min(plantel.tamanhoBanco(), reservas.size() + 1);
        for (int k = 0; k < vagasBanco; k++) blocoReservas.getChildren().add(linhaBloco(k < reservas.size() ? atleta(reservas.get(k)) : null, null, -1, k));
        contaReservas.setText(plantel.contaReservas(reservas.size()));
    }

    private void ordenarBanco() {
        reservas.sort(java.util.Comparator.comparingInt((String k) -> {
            Atleta a = atleta(k);
            return CampoEscalacao.ordemBanco(a == null ? null : a.posicao());
        }).thenComparingDouble(k -> {
            Atleta a = atleta(k);
            return a == null ? 0 : -a.forcaExibida();
        }));
    }

    /** Uma linha de titular (vaga >= 0) ou de reserva (banco >= 0); arrastável e alvo de arraste. */
    private Node linhaBloco(Atleta a, Posicao vaga, int indiceVaga, int indiceBanco) {
        Label nome = Ui.rotulo(a == null ? "—" : a.nome());
        nome.setMinWidth(0);
        if (a == null) nome.getStyleClass().add("texto-suave");
        Label insignia = vaga != null ? Ui.pos(vaga) : (a == null ? Ui.rotulo("", "pos") : Ui.pos(a.posicao()));
        if (vaga != null && indiceVaga >= 0) insignia.setText(plantel.rotulo(indiceVaga, vaga));
        HBox h = Ui.linha(7, insignia, nome, Ui.espaco());
        if (a != null) {
            if (vaga != null && plantel.encaixe(a, vaga) < 0.95) h.getChildren().add(Ui.rotulo(a.posicao().name(), "texto-pequeno"));
            if (a.jogador() != null && a.jogador().lesaoSemanas > 0) h.getChildren().add(CampoEscalacao.cruzMedica());
            if (a.etiqueta() != null) h.getChildren().add(Ui.etiqueta(a.etiqueta(), corEtiqueta(a.etiqueta())));
            listas(a, h);
            if (a.energia() != null) h.getChildren().add(CampoEscalacao.barraEnergia(a.energia(), 26, false));
            h.getChildren().add(Ui.forca(a.forcaExibida()));
        }
        h.getStyleClass().addAll("atleta", "atleta-denso");
        h.setMinHeight(22);
        if (indiceVaga >= 0 && indiceVaga == vagaSelecionada) h.getStyleClass().add("sai");
        h.setOnContextMenuRequested(e -> {
            if (a == null) return;
            mostrarMenu(a, indiceVaga >= 0 ? () -> limparVaga(indiceVaga) : () -> {
                reservas.remove(a.chave());
                vagaSelecionada = -1;
                salvar();
            }, h, e);
        });
        h.setOnMouseClicked(e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            if (indiceVaga >= 0) clicarVaga(indiceVaga);
            else if (vagaSelecionada >= 0 && a != null) doBanco(vagaSelecionada, a.chave());
        });
        if (a != null) {
            String conteudo = indiceVaga >= 0 ? "v:" + indiceVaga : "r:" + a.chave();
            h.setOnDragDetected(e -> {
                iniciarArraste(h, conteudo, a);
                e.consume();
            });
            h.setOnDragDone(e -> arrastando = null);
        }
        h.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        h.setOnDragEntered(e -> h.getStyleClass().add("entra"));
        h.setOnDragExited(e -> h.getStyleClass().remove("entra"));
        h.setOnDragDropped(e -> {
            String s = e.getDragboard().getString();
            if (s != null && s.indexOf(':') > 0) {
                String x = valor(s);
                if (indiceVaga >= 0) {
                    ultimaVaga = indiceVaga;
                    if (s.startsWith("v:")) trocarVagas(Integer.parseInt(x), indiceVaga);
                    else if (s.startsWith("j:")) colocar(indiceVaga, x);
                    else if (s.startsWith("r:")) doBanco(indiceVaga, x);
                } else {
                    soltarNoBanco(s, x, a, indiceBanco);
                }
            }
            e.setDropCompleted(true);
            e.consume();
        });
        return h;
    }

    /** Solta no banco, na vaga {@code indiceBanco} (ocupada por {@code ali} ou vazia). */
    private void soltarNoBanco(String s, String x, Atleta ali, int indiceBanco) {
        if (s.startsWith("v:")) {
            int vaga = Integer.parseInt(x);
            String tit = onze.get(vaga);
            if (ali != null) {
                onze.set(vaga, ali.chave());
                reservas.set(indiceBanco, tit);
            } else if (tit != null) {
                onze.set(vaga, null);
                reservas.add(tit);
            }
            salvar();
        } else if (s.startsWith("j:")) {
            if (ali != null) reservas.set(indiceBanco, x);
            else if (reservas.size() < plantel.tamanhoBanco()) reservas.add(x);
            salvar();
        } else if (s.startsWith("r:") && ali != null) {
            int k = reservas.indexOf(x);
            if (k >= 0) {
                reservas.set(k, ali.chave());
                reservas.set(indiceBanco, x);
                salvar();
            }
        }
    }

    /** Reserva entra na vaga; o titular vai para o lugar dele no banco. */
    private void doBanco(int vaga, String reserva) {
        int k = reservas.indexOf(reserva);
        String titular = onze.get(vaga);
        if (k >= 0) {
            if (titular != null) reservas.set(k, titular);
            else reservas.remove(k);
        }
        onze.set(vaga, reserva);
        vagaSelecionada = -1;
        salvar();
    }

    // ================================================================== campo
    private void desenhar() {
        String f = plantel.formacao();
        Posicao[] vagas = plantel.vagas(f);
        double[][] xy = plantel.coordenadas(f, vagas);
        String cap = plantel.capitao();
        List<CampoEscalacao.Peca> pecas = new ArrayList<>();
        for (int i = 0; i < plantel.emCampo(); i++) {
            Atleta a = atleta(onze.get(i));
            String rot = plantel.rotulo(i, vagas[i]);
            if (a == null) {
                pecas.add(CampoEscalacao.Peca.vazia(rot));
                continue;
            }
            pecas.add(new CampoEscalacao.Peca(true, a.nome(), rot, a.foto(50, 50), a.forcaExibida(), a.chave().equals(cap),
                    plantel.encaixe(a, vagas[i]) < 0.95, !a.disponivel(), a.energia() == null ? null : a.energia().doubleValue(), null, false,
                    false, a.jogador() != null && a.jogador().lesaoSemanas > 0));
        }
        campo.desenhar(pecas, xy, vagaSelecionada, ultimaVaga);
        int forca = (int) Math.round(plantel.forcaEmCampo(titulares(), vagas));
        forcaTime.setText(String.valueOf(forca));
        forcaTime.getStyleClass().removeAll("f1", "f2", "f3", "f4", "f5", "f6");
        forcaTime.getStyleClass().add(Ui.classeForca(forca));
        List<Atleta> resto = new ArrayList<>();
        for (Atleta a : porChave.values()) if (!onze.contains(a.chave()) && !reservas.contains(a.chave())) resto.add(a);
        lista.getItems().setAll(resto);
        montarBlocos(vagas);
        lista.refresh();
        String assinatura = String.join(",", onze.stream().map(String::valueOf).toList()) + "|" + cap;
        if (!assinatura.equals(assinaturaCobradores)) {
            assinaturaCobradores = assinatura;
            cartaoCobradores.getChildren().setAll(plantel.cobradores(titulares(), this::desenharSemCobradores));
        }
        ultimaVaga = -1;
    }

    /** Redesenha depois de mudar capitão/cobradores (o cartão em si fica). */
    private void desenharSemCobradores() {
        assinaturaCobradores = String.join(",", onze.stream().map(String::valueOf).toList()) + "|" + plantel.capitao();
        desenhar();
    }

    /** Imagem que acompanha o mouse no arraste: cartão com foto, nome e força. */
    private void fantasma(Dragboard db, Atleta a) {
        CampoEscalacao.fantasma(db, campo, a.foto(30, 36), a.nome(), a.posicao(), a.forcaExibida());
    }

    /**
     * Botão direito num atleta: lista de transferências, lista de empréstimos, rescindir (jogador
     * do meu clube) e "Limpar posição" (tira da vaga ou do banco; {@code limpar} null = não está escalado).
     */
    private void mostrarMenu(Atleta a, Runnable limpar, Node dono, javafx.scene.input.ContextMenuEvent e) {
        javafx.scene.control.ContextMenu cm = new javafx.scene.control.ContextMenu();
        Jogador j = a.jogador();
        Clube meu = hub.clube();
        if (j != null && a.timeKings() == null && meu != null && meu.id.equals(j.clubeId)) {
            cm.getItems().addAll(new PainelElenco(hub).itensMenu(j, false));
        }
        if (limpar != null) {
            if (!cm.getItems().isEmpty()) cm.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            javafx.scene.control.MenuItem l = new javafx.scene.control.MenuItem("Limpar posição");
            l.setOnAction(x -> limpar.run());
            cm.getItems().add(l);
        }
        if (cm.getItems().isEmpty()) return;
        cm.show(dono, e.getScreenX(), e.getScreenY());
        e.consume();
    }

    /** Esvazia a vaga do campo (na Kings o atleta volta ao banco; no clube, aos não relacionados). */
    /** LT = lista de transferências, LE = lista de empréstimos. */
    private static void listas(Atleta a, HBox h) {
        Jogador j = a.jogador();
        if (j == null) return;
        if (j.aVenda) h.getChildren().add(Ui.etiqueta("LT", "azul"));
        if (j.aEmprestimo) h.getChildren().add(Ui.etiqueta("LE", "azul"));
    }

    private void limparVaga(int vaga) {
        String k = onze.get(vaga);
        if (k == null) return;
        onze.set(vaga, null);
        if (plantel.bancoTodos() && !reservas.contains(k)) reservas.add(k);
        vagaSelecionada = -1;
        salvar();
    }

    private void clicarVaga(int vaga) {
        if (vagaSelecionada < 0) vagaSelecionada = vaga;
        else if (vagaSelecionada == vaga) vagaSelecionada = -1;
        else {
            trocarVagas(vagaSelecionada, vaga);
            return;
        }
        desenhar();
    }

    private void trocarVagas(int a, int b) {
        if (a == b) return;
        String x = onze.get(a);
        onze.set(a, onze.get(b));
        onze.set(b, x);
        vagaSelecionada = -1;
        salvar();
    }

    private void colocar(int vaga, String chave) {
        if (atleta(chave) == null) return;
        int ja = onze.indexOf(chave);
        if (ja >= 0) onze.set(ja, onze.get(vaga));
        else if (reservas.contains(chave)) {
            doBanco(vaga, chave);
            return;
        }
        String saiu = onze.get(vaga);
        onze.set(vaga, chave);
        if (saiu != null && plantel.bancoTodos() && !onze.contains(saiu)) reservas.add(saiu);
        vagaSelecionada = -1;
        salvar();
    }

    private void salvar() {
        reservas.removeIf(k -> k == null || onze.contains(k));
        completarBanco();
        if (!plantel.bancoTodos()) while (reservas.size() > plantel.tamanhoBanco()) reservas.remove(reservas.size() - 1);
        if (!onze.contains(null) && onze.stream().distinct().count() == plantel.emCampo()) plantel.salvar(onze, reservas);
        desenhar();
    }

    static List<Integer> copia(Integer... ids) {
        return new ArrayList<>(Arrays.asList(ids));
    }

    // ================================================================== cartões comuns
    /** Linha "rótulo + escolha" do cartão de cobradores (o mesmo visual para clube e Kings). */
    static ChoiceBox<String> escolha(GridPane g, int linha, String rotulo, List<String> opcoes, int selecionada, Consumer<Integer> aoEscolher) {
        ChoiceBox<String> cb = new ChoiceBox<>();
        cb.getItems().setAll(opcoes);
        cb.getSelectionModel().select(Math.max(0, Math.min(selecionada, opcoes.size() - 1)));
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPrefWidth(150);
        GridPane.setHgrow(cb, Priority.ALWAYS);
        cb.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            if (b != null && b.intValue() >= 0) aoEscolher.accept(b.intValue());
        });
        g.addRow(linha, minimo(Ui.rotulo(rotulo, "texto-medio")), cb);
        return cb;
    }

    static GridPane grade() {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        return g;
    }

    // ================================================================== clube (11)
    /** Clube: 11 em campo, banco pela regra da competição, estilo, marcação e cobradores de pênalti e falta. */
    static final class DoClube extends Plantel {
        private final Hub hub;
        private final Clube c;
        private int banco = 12;

        DoClube(Hub hub, Clube c) {
            this.hub = hub;
            this.c = c;
            // vendido/emprestado esperando a janela (ou que já saiu) não fica na escalação salva
            Escalador.limparEscalacao(hub.jogo().mundo(), c);
            try {
                var prox = hub.sessao().fluxo.proximoJogo();
                if (prox != null) banco = Math.max(5, hub.jogo().limitesDaPartida(prox)[0]);
            } catch (RuntimeException e) {
                banco = 12;
            }
        }

        private Jogo jogo() { return hub.jogo(); }

        @Override int emCampo() { return 11; }

        @Override String cor() { return c.cor1; }

        @Override List<String> formacoes() { return Formacoes.nomes(); }

        @Override String formacao() { return c.tatica.formacao; }

        @Override void definirFormacao(String f, List<String> onze) { jogo().definirTatica(f, null); }

        @Override Posicao[] vagas(String f) { return Formacoes.vagas(f); }

        @Override
        String rotulo(int i, Posicao p) {
            try {
                List<String> r = jogo().rotulosFormacao(c.tatica.formacao);
                if (i < r.size()) return r.get(i);
            } catch (RuntimeException e) {
                // sem rótulos: usa a posição
            }
            return p.name();
        }

        @Override double[][] coordenadas(String f, Posicao[] vagas) { return CampoEscalacao.coordenadasOnze(f, vagas); }

        private Atleta de(Jogador j) {
            String et = j.lesaoSemanas > 0 ? "Lesão" : j.suspensao > 0 ? "Susp."
                    : j.noSub20 && c.base.contains(j.id) ? ETIQUETA_SUB20 : null;
            return new Atleta(String.valueOf(j.id), j.nomeExibicao(), j.posicao, j.forcaExibida(), j.forca, j.disponivel(), et, j,
                    jogo().foto(j.id), j.dbId, null, j.energia);
        }

        @Override boolean temEnergia() { return true; }

        @Override
        String proximoJogo() {
            try {
                var p = hub.sessao().fluxo.proximoJogo();
                if (p == null) return null;
                Mundo m = jogo().mundo();
                var t = hub.sessao().fluxo.torneioDe(p);
                return m.nomeParticipante(p.mandante) + " x " + m.nomeParticipante(p.visitante)
                        + (t == null ? "" : "  ·  " + br.futebas.ui.modelo.Textos.curto(t.nome));
            } catch (RuntimeException e) {
                return null;
            }
        }

        @Override
        List<Atleta> todos() {
            // negociado esperando a janela: não joga mais pelo clube (regra do jogo)
            java.util.Set<Integer> saindo = Escalador.deSaida(jogo().mundo(), c);
            List<Jogador> l = new ArrayList<>(jogo().elenco(c.id));
            // o Sub-20 só aparece com a caixa "Sub-20" marcada na Tática (regra do jogo)
            if (jogo().usaSub20NaEscalacao())
                for (Jogador b : jogo().base(c.id)) if (b.noSub20 && b.idade(jogo().ano()) >= 16) l.add(b);
            l.removeIf(x -> saindo.contains(x.id));
            l.sort(Comparator.comparingInt((Jogador x) -> x.posicao.ordinal()).thenComparingDouble(x -> -x.forca));
            List<Atleta> r = new ArrayList<>();
            for (Jogador j : l) r.add(de(j));
            return r;
        }

        @Override
        List<String> titulares() {
            List<String> l = new ArrayList<>();
            if (c.tatica.titulares.size() == 11) {
                for (int id : c.tatica.titulares) l.add(id >= 0 ? String.valueOf(id) : null);
                return l;
            }
            try {
                for (Escalado e : Escalador.montar(hub.jogo().mundo(), c, 3, banco).titulares) l.add(String.valueOf(e.id));
            } catch (RuntimeException ex) {
                // elenco curto: vagas vazias (o clube contrata reforços de emergência)
            }
            return l;
        }

        @Override
        List<String> reservasSalvas() {
            List<String> l = new ArrayList<>();
            for (int id : c.tatica.reservas) l.add(String.valueOf(id));
            if (l.isEmpty() || c.tatica.titulares.size() != 11) {
                // banco sugerido: o que o auxiliar relacionaria (um goleiro e os melhores de cada setor)
                try {
                    for (Escalado e : Escalador.montar(hub.jogo().mundo(), c, 3, banco).reservas) if (!l.contains(String.valueOf(e.id))) l.add(String.valueOf(e.id));
                } catch (RuntimeException ex) {
                    // elenco curto
                }
            }
            return l;
        }

        @Override int tamanhoBanco() { return banco; }

        @Override
        void salvar(List<String> onze, List<String> reservas) {
            List<Integer> a = new ArrayList<>(), b = new ArrayList<>();
            for (String k : onze) a.add(Integer.parseInt(k));
            for (String k : reservas) b.add(Integer.parseInt(k));
            jogo().definirEscalacao(a, b);
        }

        @Override void automatica() { jogo().escalacaoAutomatica(); }

        @Override double encaixe(Atleta a, Posicao vaga) { return a.jogador() == null ? 1 : Escalador.encaixe(a.jogador(), vaga); }

        @Override String capitao() { return c.tatica.capitao == null ? null : String.valueOf(c.tatica.capitao); }

        @Override
        double forcaEmCampo(List<Atleta> onze, Posicao[] vagas) {
            double s = 0;
            for (int i = 0; i < onze.size(); i++) {
                Atleta a = onze.get(i);
                if (a == null) continue;
                double e = encaixe(a, vagas[i]);
                s += a.forca() * (e > 0 ? e : 0.7);
            }
            return s / 11;
        }

        @Override
        List<Node> taticaExtra() {
            Label descEstilo = Ui.paragrafo(descricao(c.tatica.estilo), "texto-pequeno");
            ChoiceBox<Estilo> estilo = new ChoiceBox<>();
            estilo.getItems().setAll(Estilo.values());
            estilo.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(Estilo e) { return e == null ? "" : e.nome; }

                @Override
                public Estilo fromString(String x) { return null; }
            });
            estilo.setValue(c.tatica.estilo);
            estilo.setPrefWidth(140);
            estilo.valueProperty().addListener((o, a, e) -> {
                if (e == null) return;
                jogo().definirTatica(null, e);
                descEstilo.setText(descricao(e));
            });
            Label descMarcacao = Ui.paragrafo(descricao(jogo().marcacao()), "texto-pequeno");
            HBox marcacao = Ui.segmentos(List.of(Marcacao.values()), x -> x.nome, jogo().marcacao(), x -> {
                jogo().definirMarcacao(x);
                descMarcacao.setText(descricao(x));
            });
            Label descAtaques = Ui.paragrafo(descricao(jogo().concentracao()), "texto-pequeno");
            HBox ataques = Ui.segmentos(List.of(Concentracao.values()), x -> switch (x) {
                case EQUILIBRADO -> "Equilibrado";
                case MEIO -> "Pelo meio";
                case LATERAIS -> "Laterais";
            }, jogo().concentracao(), x -> {
                jogo().definirConcentracao(x);
                descAtaques.setText(descricao(x));
            });
            // quadradinho "Sub-20": marcado, o time Sub-20 aparece nos não relacionados (etiqueta "Sub-20")
            javafx.scene.control.CheckBox sub20 = new javafx.scene.control.CheckBox("Sub-20");
            sub20.setFocusTraversable(false);
            sub20.setSelected(jogo().usaSub20NaEscalacao());
            sub20.setTooltip(new javafx.scene.control.Tooltip("Mostra o time Sub-20 nos não relacionados para relacionar ou escalar. "
                    + "Desmarcado, o Sub-20 fica de fora (também da escalação automática)."));
            sub20.setOnAction(e -> {
                jogo().usarSub20NaEscalacao(sub20.isSelected());
                aoMudarLista.run();
            });
            return List.of(Ui.linha(10, Ui.rotulo("Estilo", "texto-medio"), Ui.espaco(), estilo), descEstilo,
                    Ui.separadorH(), Ui.rotulo("Marcação", "texto-medio"), marcacao, descMarcacao,
                    Ui.separadorH(), Ui.rotulo("Concentrar ataques", "texto-medio"), ataques, descAtaques,
                    Ui.separadorH(), sub20);
        }

        @Override
        VBox cobradores(List<Atleta> titulares, Runnable redesenhar) {
            List<String> nomes = new ArrayList<>(List.of("Automático"));
            List<Integer> ids = new ArrayList<>();
            ids.add(null);
            for (Atleta a : titulares) {
                if (a == null || a.jogador() == null) continue;
                ids.add(a.jogador().id);
                nomes.add(a.nome() + " (" + a.posicao().name() + ")");
            }
            Integer[] v = {c.tatica.capitao, c.tatica.cobradorPenalti, c.tatica.cobradorFalta};
            GridPane g = grade();
            String[] rot = {"Capitão", "Pênaltis", "Faltas"};
            for (int k = 0; k < 3; k++) {
                int qual = k;
                escolha(g, k, rot[k], nomes, v[k] == null ? 0 : Math.max(0, ids.indexOf(v[k])), i -> {
                    v[qual] = i <= 0 || i >= ids.size() ? null : ids.get(i);
                    jogo().definirCobradores(v[0], v[1], v[2]);
                    redesenhar.run();
                });
            }
            return Ui.painel("Capitão e cobradores", g);
        }

        private static String descricao(Marcacao m) {
            return switch (m) {
                case LEVE -> "Menos faltas, cartões e cansaço; em troca, o adversário cria mais.";
                case NORMAL -> "Pressão e disciplina na medida.";
                case PESADA -> "O adversário cria menos, mas o time faz mais faltas, leva mais cartões e cansa mais.";
            };
        }

        private static String descricao(Concentracao c) {
            return switch (c) {
                case EQUILIBRADO -> "Ataca por todos os lados.";
                case MEIO -> "Rende mais contra um meio-campo fraco.";
                case LATERAIS -> "Rende mais contra laterais fracos.";
            };
        }

        private static String descricao(Estilo e) {
            if (e == null) return "";
            return switch (e) {
                case EQUILIBRADO -> "Nem expõe, nem se fecha. Leva vantagem sobre contra-ataque e retranca.";
                case ATAQUE_TOTAL -> "Cria muito mais, mas cede espaço. Bom contra equilibrado e retranca.";
                case CONTRA_ATAQUE -> "Cria menos e converte melhor. Bom contra ataque total e pressão alta.";
                case PRESSAO_ALTA -> "Sufoca o rival, mas tira mais cartões. Bom contra equilibrado e ataque total.";
                case RETRANCA -> "Fecha a defesa. Bom contra contra-ataque e pressão alta.";
            };
        }
    }

    // ================================================================== Kings League (7)
    /**
     * Time da Kings League: 7 em campo (goleiro + linhas), todo o resto no banco (substituições
     * ilimitadas), capitão, ordem dos cobradores de shootout, quem bate o pênalti do presidente.
     * Usa os ganchos do motor ({@code definirEscalacaoKings}, {@code definirCobradoresKings}...).
     */
    static final class DaKings extends Plantel {
        /** Quantos cobradores de shootout dá para ordenar (depois a ordem se repete). */
        static final int COBRADORES = 5;
        private final Hub hub;
        private final LigaKings.TimeKings t;

        DaKings(Hub hub, LigaKings.TimeKings t) {
            this.hub = hub;
            this.t = t;
        }

        private Jogo jogo() { return hub.jogo(); }

        @Override int emCampo() { return 7; }

        @Override String cor() { return t.cor1; }

        @Override List<String> formacoes() { return jogo().formacoesKings(); }

        @Override String formacao() { return t.formacao; }

        @Override
        void definirFormacao(String f, List<String> onze) {
            boolean manual = t.titulares != null && !t.titulares.isEmpty() && !onze.contains(null);
            jogo().definirEscalacaoKings(f, manual ? onze : List.of());
        }

        @Override Posicao[] vagas(String f) { return jogo().vagasKings(f).subList(0, 7).toArray(Posicao[]::new); }

        @Override double[][] coordenadas(String f, Posicao[] vagas) { return CampoEscalacao.coordenadasSete(f); }

        Atleta de(LigaKings.AtletaKings a) {
            Mundo m = jogo().mundo();
            Jogador j = a.jogadorId == null ? null : m.jogador(a.jogadorId);
            String nome = j != null ? j.nomeExibicao() : a.nome;
            Posicao p = a.posicao != null ? a.posicao : j != null ? j.posicao : Posicao.MC;
            String foto = j != null ? jogo().foto(j.id) : a.foto;
            return new Atleta(a.chave(), nome, p, (int) Math.round(a.forca), a.forca, true, null, j, foto, j == null ? null : j.dbId, t.id, null);
        }

        @Override
        List<Atleta> todos() {
            List<LigaKings.AtletaKings> l = new ArrayList<>(t.elenco);
            l.sort(Comparator.comparingInt((LigaKings.AtletaKings a) -> a.posicao == null ? 99 : a.posicao.ordinal()).thenComparingDouble(a -> -a.forca));
            List<Atleta> r = new ArrayList<>();
            for (LigaKings.AtletaKings a : l) r.add(de(a));
            return r;
        }

        @Override
        List<String> titulares() {
            List<String> l = new ArrayList<>();
            for (LigaKings.AtletaKings a : jogo().escaladosKings()) l.add(a.chave());
            return l;
        }

        @Override List<String> reservasSalvas() { return List.of(); }

        @Override int tamanhoBanco() { return Math.max(0, t.elenco.size() - 7); }

        @Override boolean bancoTodos() { return true; }

        @Override void salvar(List<String> onze, List<String> reservas) { jogo().definirEscalacaoKings(t.formacao, onze); }

        @Override void automatica() { jogo().definirEscalacaoKings(t.formacao, List.of()); }

        @Override
        double encaixe(Atleta a, Posicao vaga) {
            return MotorKings.encaixe(a.posicao(), vaga, MotorKings.temGoleiro(t));
        }

        @Override String capitao() { return t.capitao; }

        @Override String contaReservas(int n) { return n + " · trocas ilimitadas"; }

        @Override
        String proximoJogo() {
            try {
                var p = hub.sessao().fluxo.proximoJogoKings();
                if (p == null) return null;
                return jogo().mundo().nomeParticipante(p.mandante) + " x " + jogo().mundo().nomeParticipante(p.visitante) + "  ·  Kings League";
            } catch (RuntimeException e) {
                return null;
            }
        }

        @Override
        String ajuda() {
            return "Kings League: 7 em campo e substituições ilimitadas (todo o elenco fica no banco). Arraste entre o campo e as listas, "
                    + "ou clique num jogador e depois no outro para trocar.";
        }

        @Override
        VBox cobradores(List<Atleta> titulares, Runnable redesenhar) {
            List<Atleta> elenco = todos();
            elenco.sort(Comparator.comparingDouble((Atleta a) -> -a.forca()));
            GridPane g = grade();
            int linha = 0;
            // capitão: entre os 7
            List<String> nomesCap = new ArrayList<>(List.of("Automático"));
            List<String> chavesCap = new ArrayList<>();
            chavesCap.add(null);
            for (Atleta a : titulares) {
                if (a == null) continue;
                chavesCap.add(a.chave());
                nomesCap.add(a.nome() + " (" + a.posicao().name() + ")");
            }
            escolha(g, linha++, "Capitão", nomesCap, t.capitao == null ? 0 : Math.max(0, chavesCap.indexOf(t.capitao)), i -> {
                jogo().definirCapitaoKings(i <= 0 ? null : chavesCap.get(i));
                redesenhar.run();
            });
            // pênalti do presidente
            List<String> pres = t.nomesPresidentes();
            if (!pres.isEmpty()) {
                List<String> op = new ArrayList<>(List.of(pres.size() > 1 ? "Revezam" : "Automático"));
                op.addAll(pres);
                escolha(g, linha++, "Pênalti do presidente", op, t.batedorPresidente == null ? 0 : Math.max(0, op.indexOf(t.batedorPresidente)),
                        i -> jogo().definirBatedorPresidente(i <= 0 ? null : op.get(i)));
            }
            // shootouts: ordem (qualquer um do elenco pode bater; vazio = o motor escolhe)
            List<String> nomes = new ArrayList<>(List.of("Automático"));
            List<String> chaves = new ArrayList<>();
            chaves.add(null);
            for (Atleta a : elenco) {
                chaves.add(a.chave());
                nomes.add(a.nome() + " (" + a.posicao().name() + ")");
            }
            String[] ordem = new String[COBRADORES];
            for (int k = 0; k < COBRADORES && t.cobradores != null && k < t.cobradores.size(); k++) ordem[k] = chaves.contains(t.cobradores.get(k)) ? t.cobradores.get(k) : null;
            Label titulo = Ui.rotulo("Batedores do shoot out", "texto-medio");
            titulo.setStyle("-fx-padding: 4 0 0 0;");
            g.add(titulo, 0, linha++, 2, 1);
            for (int k = 0; k < COBRADORES; k++) {
                int qual = k;
                escolha(g, linha++, (k + 1) + "º cobrador", nomes, ordem[k] == null ? 0 : Math.max(0, chaves.indexOf(ordem[k])), i -> {
                    ordem[qual] = i <= 0 ? null : chaves.get(i);
                    List<String> l = new ArrayList<>();
                    for (String x : ordem) if (x != null && !l.contains(x)) l.add(x);
                    jogo().definirCobradoresKings(l);
                });
            }
            Label nota = Ui.paragrafo("A ordem se repete na morte súbita; \"Automático\" deixa o motor escolher os melhores finalizadores.", "texto-pequeno");
            return Ui.painel("Capitão e cobradores", g, nota);
        }
    }
}
