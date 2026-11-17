package br.futebas.ui.telas;

import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Posicao;
import br.futebas.partida.Escalador;
import br.futebas.partida.Formacoes;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Foto;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoPartida;
import br.futebas.ui.modelo.AcompanhamentoPartida.Atleta;
import br.futebas.ui.modelo.AcompanhamentoPartida.Estado;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Trocas ao vivo (intervalo, lesão, expulsão ou a tecla T) no desenho da Escalação: o seu time no
 * {@link CampoEscalacao} (foto, posição, força e a energia de agora) e o banco ao lado.
 *
 * <p>Regra do jogo: "a troca só acontece depois que confirma". Arrastar um reserva para a vaga (ou
 * clicar em quem sai e em quem entra) e trocar dois de posição só <b>preparam</b> a mudança: o
 * reserva aparece na vaga com o destaque de pendente e quem sai fica marcado no banco. Nada vai para
 * o motor até "Confirmar trocas (n)"; "Desfazer" limpa tudo. O contador mostra feitas + pendentes /
 * limite e não deixa preparar além do limite. Quem saiu não volta; o expulso fica apagado na vaga.
 *
 * <p>Expulsão (requisito: "quando o cara é expulso não pode trocar ele do jogo, mas pode trocar
 * a posição dele para alguém ficar na posição dele"): o expulso (e quem saiu lesionado sem troca) não
 * sai nem entra, mas a vaga dele aceita a troca de posição com alguém em campo; o buraco passa para a
 * posição de quem foi remanejado. Troca de posição nunca gasta substituição.
 */
final class TrocasAoVivo {
    /** Uma mudança preparada: substituição (sai → entra) ou troca de posição (a ⇄ b). */
    record Mudanca(boolean substituicao, int a, int b) {}

    /**
     * De onde vêm as trocas e para onde vão: o futebol ({@link AcompanhamentoPartida}) ou a Kings (7 em
     * campo, trocas ilimitadas e de volta). Os padrões são os do futebol (fotos e encaixe pelo jogador).
     */
    interface Fonte {
        /** Aplica as mudanças confirmadas, na ordem. */
        void aplicar(List<Mudanca> mudancas);

        /** Dois em campo podem trocar de posição? (Kings: não) */
        default boolean trocaDePosicao() { return true; }

        /** Muda a formação no jogo (futebol); false = esta fonte não muda formação (Kings). */
        default boolean mudarFormacao(String formacao) { return false; }

        /** A fonte aceita troca de formação no meio do jogo? */
        default boolean temFormacao() { return false; }

        /** Foto do atleta (null = a do jogador do mundo com esse id). */
        default Node foto(int id, double w, double h) { return null; }

        /** Encaixe na vaga (NaN = pelo jogador do mundo). */
        default double encaixe(int id, Posicao natural, Posicao vaga) { return Double.NaN; }

        /** Pontos das vagas no campo (null = formação de 11 do clube). */
        default double[][] coordenadas(Posicao[] vagas) { return null; }

        /** Cor do aro (null = a do clube). */
        default String cor() { return null; }

        /** Id do capitão (null = o do clube). */
        default Integer capitao() { return null; }
    }

    private final Sessao sessao;
    private final Fonte fonte;
    /** O clube de quem troca (padrão: o do técnico da vez; multi-treinador: o do lado na tela dividida). */
    private Supplier<Clube> clube;
    private Runnable aoConfirmar;
    private final CampoEscalacao campo;
    private final VBox banco = new VBox(2);
    private final javafx.scene.control.ScrollPane rolBanco;
    private final Label contador = Ui.rotulo("", "texto-medio");
    private final Button confirmar = Ui.botao("Confirmar", this::confirmarEFechar, "primario");
    private final HBox raiz;
    private final CampoEscalacao.Suporte suporte;
    private final List<Mudanca> pendentes = new ArrayList<>();
    /** Formação do time no jogo (null = a da escalação) e a escolhida e ainda não confirmada (null = nenhuma). */
    private String formacaoAtual, formacaoPendente;
    private final javafx.scene.control.ComboBox<String> seletorFormacao = new javafx.scene.control.ComboBox<>();
    private boolean mexendoSeletor;
    private Estado ultimo;
    private String assinatura = "";
    /** Seleção por clique: um do campo (exibido) e/ou um do banco. */
    private Integer saiId, entraId;
    /** Quem está sendo arrastado (para colorir as vagas). */
    private Integer arrastando;

    /** Futebol: as trocas vão para o {@link AcompanhamentoPartida}. */
    TrocasAoVivo(Sessao sessao, Supplier<AcompanhamentoPartida> acomp) {
        this(sessao, new Fonte() {
            @Override
            public void aplicar(List<Mudanca> ms) {
                AcompanhamentoPartida a = acomp.get();
                if (a == null) return;
                for (Mudanca m : ms) {
                    if (m.substituicao()) a.substituir(m.a(), m.b());
                    else a.trocarPosicoes(m.a(), m.b());
                }
            }

            @Override
            public boolean mudarFormacao(String formacao) {
                AcompanhamentoPartida a = acomp.get();
                if (a == null) return false;
                a.mudarFormacao(formacao);
                return true;
            }

            @Override
            public boolean temFormacao() { return true; }
        });
    }

    /** Multi-treinador: as trocas de um técnico que não é o da vez (o clube dele vem de {@code clube}). */
    TrocasAoVivo(Sessao sessao, Supplier<AcompanhamentoPartida> acomp, Supplier<Clube> clube) {
        this(sessao, acomp);
        this.clube = clube;
    }

    TrocasAoVivo(Sessao sessao, Fonte fonte) {
        this.sessao = sessao;
        this.fonte = fonte;
        this.clube = sessao.jogo::meuClube;
        this.campo = new CampoEscalacao(new AcoesCampo());
        // tamanho fixo: antes o campo esticava até a altura da lista do banco e encolhia a cada troca (banco menor)
        suporte = campo.escalavel(390, 480);
        suporte.setMinSize(390, 480);
        suporte.setMaxSize(390, 480);
        Tooltip.install(confirmar, new Tooltip("Aplica as trocas preparadas e volta ao jogo (até lá nada muda)"));
        // o banco inteiro à vista (até 12 reservas sem rolar, a requisito); sem o quadro "Saíram"
        rolBanco = Ui.rolar(banco);
        rolBanco.setFitToWidth(true);
        rolBanco.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(rolBanco, Priority.ALWAYS);
        VBox pBanco = Ui.painelAcoes("Banco", contador, rolBanco);
        pBanco.setStyle("-fx-padding: 8 10 8 10; -fx-spacing: 4;");
        VBox.setVgrow(pBanco, Priority.ALWAYS);
        VBox lateral = new VBox(10, pBanco);
        if (fonte.temFormacao()) {
            // formação no meio do jogo (requisito): fica preparada como as trocas e vale ao Confirmar
            seletorFormacao.getItems().setAll(Formacoes.nomes());
            seletorFormacao.setFocusTraversable(false);
            seletorFormacao.setPrefWidth(140);
            seletorFormacao.valueProperty().addListener((o, x, f) -> {
                if (!mexendoSeletor && f != null) prepararFormacao(f);
            });
            Tooltip.install(seletorFormacao, new Tooltip("Muda a formação (não gasta substituição): cada um vai para a vaga em que rende mais; depois dá para trocar posições"));
            HBox linhaF = new HBox(8, Ui.rotulo("Formação", "texto-medio"), seletorFormacao);
            linhaF.setAlignment(Pos.CENTER_LEFT);
            lateral.getChildren().add(0, linhaF);
        }
        lateral.setPrefWidth(340);
        lateral.setMinWidth(280);
        lateral.setMaxWidth(460); // telas largas: o resto vai para a tabela ao vivo da camada
        raiz = new HBox(16, suporte, lateral);
        HBox.setHgrow(suporte, Priority.NEVER);
        HBox.setHgrow(lateral, Priority.ALWAYS);
        // a altura também não depende do banco (o banco rola)
        raiz.setMinHeight(480);
        raiz.setPrefHeight(480);
        raiz.setMaxHeight(480);
    }

    Node raiz() { return raiz; }

    /**
     * Altura do campo (e da área do banco) na camada: a tela de trocas acompanha o tamanho da janela
     * (requisito: "pode aumentar mais um pouco"). O tamanho continua fixo enquanto a camada está
     * aberta (o campo não encolhe a cada troca); a largura segue a proporção do campo.
     */
    void tamanho(double altura) {
        double h = Math.max(420, Math.min(760, Math.floor(altura)));
        double w = Math.round(h * 390.0 / 480.0);
        if (Math.abs(suporte.getMaxHeight() - h) < 0.5) return;
        suporte.setMinSize(w, h);
        suporte.setPrefSize(w, h);
        suporte.setMaxSize(w, h);
        raiz.setMinHeight(h);
        raiz.setPrefHeight(h);
        raiz.setMaxHeight(h);
    }

    /** Escala e tamanho do campo (teste do bug "a escalação fica menor a cada troca"). */
    double escalaCampo() { return suporte.escala(); }

    double larguraCampo() { return suporte.getWidth(); }

    double alturaCampo() { return suporte.getHeight(); }

    /** "Confirmar (n)": aplica as preparadas e fecha (o rodapé tem só Voltar e Confirmar, regra do jogo). */
    Button botaoSubstituir() { return confirmar; }

    /** O que fazer depois de confirmar (fechar a camada). */
    void aoConfirmar(Runnable r) { this.aoConfirmar = r; }

    private void confirmarEFechar() {
        confirmar();
        if (aoConfirmar != null) aoConfirmar.run();
    }

    /** Desfaz uma mudança preparada (o botão de desfazer ao lado dela). */
    void desfazer(Mudanca m) {
        pendentes.remove(m);
        redesenhar();
    }

    /** Trocas ilimitadas (Kings). */
    private boolean ilimitadas() { return ultimo != null && ultimo.subsPermitidas() >= 1000; }

    boolean temPendentes() { return !pendentes.isEmpty() || formacaoPendente != null; }

    /** A formação do time agora (a confirmada no jogo, ou a da escalação). */
    String formacaoAtual() {
        if (formacaoAtual != null) return formacaoAtual;
        Clube c = meuClube();
        return c != null && c.tatica != null ? c.tatica.formacao : null;
    }

    /** A formação escolhida e ainda não confirmada (null = nenhuma). */
    String formacaoPendente() { return formacaoPendente; }

    /**
     * Prepara a troca de formação: as vagas exibidas passam a ser as da formação nova (cada um na vaga em
     * que rende mais); as trocas de posição já preparadas saem (eram das vagas antigas), as
     * substituições ficam. Vale no Confirmar e não gasta substituição.
     */
    boolean prepararFormacao(String f) {
        if (ultimo == null || ultimo.terminado() || !fonte.temFormacao() || !Formacoes.nomes().contains(f)) return false;
        if (Formacoes.vagas(f).length != ultimo.vagas().size()) return false;
        formacaoPendente = f.equals(formacaoAtual()) ? null : f;
        pendentes.removeIf(m -> !m.substituicao());
        saiId = null;
        entraId = null;
        redesenhar();
        return true;
    }

    /** O banco inteiro cabe sem rolar (teste: até 12 reservas a 1366×768)? */
    boolean bancoCabeSemRolar() {
        return banco.getHeight() <= rolBanco.getViewportBounds().getHeight() + 0.5;
    }

    List<Mudanca> pendentes() { return List.copyOf(pendentes); }

    /** Limpa a seleção (ao abrir a camada, ou num jogo novo). As trocas pendentes ficam (a tela pergunta antes de fechar). */
    void limpar() {
        saiId = null;
        entraId = null;
        assinatura = "";
    }

    void atualizar(Estado e) {
        if (e == null || e.meuLado() < 0) return;
        ultimo = e;
        // o jogo andou (expulsão, troca do motor...): o que não vale mais sai das pendentes
        validarPendentes();
        List<Atleta> vagas = vagasExibidas();
        if (saiId != null && vagas.stream().noneMatch(a -> a.id() == saiId)) saiId = null;
        if (entraId != null && bancoExibido().stream().noneMatch(a -> a.id() == entraId)) entraId = null;
        StringBuilder sb = new StringBuilder();
        for (Atleta a : e.vagas()) sb.append(a.id()).append(a.vaga()).append(a.energia()).append(a.amarelo()).append(a.lesionado()).append(',');
        sb.append('|');
        for (Atleta a : e.banco()) sb.append(a.id()).append(',');
        sb.append('|').append(e.sairam()).append(e.subsFeitas()).append(e.terminado()).append(saiId).append(entraId).append(pendentes).append(formacaoPendente);
        String sig = sb.toString();
        int subsPend = subsPendentes();
        int nPend = pendentes.size() + (formacaoPendente != null ? 1 : 0);
        confirmar.setText(nPend == 0 ? "Confirmar" : "Confirmar (" + nPend + ")");
        if (fonte.temFormacao()) {
            mexendoSeletor = true;
            seletorFormacao.setValue(formacaoPendente != null ? formacaoPendente : formacaoAtual());
            seletorFormacao.setDisable(e.terminado());
            mexendoSeletor = false;
        }
        String pend = subsPend == 0 ? "" : " + " + subsPend + " pendente" + (subsPend > 1 ? "s" : "");
        contador.setText(ilimitadas() ? (subsPend == 0 ? "trocas ilimitadas" : subsPend + " pendente" + (subsPend > 1 ? "s" : "") + " · ilimitadas")
                : subsPend == 0 ? e.subsFeitas() + "/" + e.subsPermitidas() + " substituições" : e.subsFeitas() + pend + " / " + e.subsPermitidas());
        if (sig.equals(assinatura)) return;
        assinatura = sig;
        desenharCampo(e);
        desenharBanco(e);
    }

    static boolean podeTrocar(Estado e) {
        return !e.terminado() && e.subsFeitas() < e.subsPermitidas() && !e.banco().isEmpty();
    }

    // ------------------------------------------------------------------ mudanças preparadas
    private int subsPendentes() {
        int n = 0;
        for (Mudanca m : pendentes) if (m.substituicao()) n++;
        return n;
    }

    /** Ainda cabe mais uma substituição (feitas + pendentes &lt; limite)? */
    boolean cabeSubstituicao() {
        return ultimo != null && !ultimo.terminado() && (ilimitadas() || ultimo.subsFeitas() + subsPendentes() < ultimo.subsPermitidas());
    }

    /** Prepara a substituição: {@code sai} (em campo, exibido) dá a vaga a {@code entra} (do banco, exibido). */
    boolean preparar(int sai, int entra) {
        if (vagasExibidas().stream().noneMatch(a -> a.id() == sai && jogavel(a))) return false; // expulso não sai
        if (bancoExibido().stream().noneMatch(a -> a.id() == entra)) return false;
        // quem "sai" ainda nem entrou (substituição preparada): troca o reserva escolhido, sem gastar outra
        for (int i = 0; i < pendentes.size(); i++) {
            Mudanca m = pendentes.get(i);
            if (m.substituicao() && m.b() == sai) {
                pendentes.set(i, new Mudanca(true, m.a(), entra));
                trocarId(i + 1, sai, entra);
                saiId = null;
                entraId = null;
                redesenhar();
                return true;
            }
        }
        if (!cabeSubstituicao()) return false;
        pendentes.add(new Mudanca(true, sai, entra));
        saiId = null;
        entraId = null;
        redesenhar();
        return true;
    }

    /**
     * Prepara a troca de posição entre dois em campo, ou entre alguém em campo e a vaga vazia de um
     * expulso (ou de quem saiu lesionado sem troca). Não gasta substituição.
     */
    boolean prepararTroca(int a, int b) {
        if (ultimo == null || ultimo.terminado() || a == b || !fonte.trocaDePosicao()) return false;
        if (!trocaValida(vagasExibidas(), a, b)) return false;
        pendentes.add(new Mudanca(false, a, b));
        saiId = null;
        redesenhar();
        return true;
    }

    /** Aplica as preparadas no jogo, na ordem em que foram feitas. */
    void confirmar() {
        if (pendentes.isEmpty() && formacaoPendente == null) return;
        if (formacaoPendente != null && fonte.mudarFormacao(formacaoPendente)) formacaoAtual = formacaoPendente;
        formacaoPendente = null;
        if (!pendentes.isEmpty()) fonte.aplicar(List.copyOf(pendentes));
        pendentes.clear();
        limpar();
        if (ultimo != null) atualizar(ultimo);
    }

    /** Desfaz todas as preparadas. */
    void descartar() {
        pendentes.clear();
        formacaoPendente = null;
        limpar();
        if (ultimo != null) atualizar(ultimo);
    }

    /** Tira das pendentes o que o jogo invalidou (expulsão, lesão, troca feita pelo motor, fim de jogo). */
    private void validarPendentes() {
        if (ultimo == null || pendentes.isEmpty()) return;
        if (ultimo.terminado()) {
            pendentes.clear();
            formacaoPendente = null;
            return;
        }
        List<Mudanca> ok = new ArrayList<>();
        List<Mudanca> todas = new ArrayList<>(pendentes);
        pendentes.clear();
        for (Mudanca m : todas) {
            List<Atleta> v = vagasExibidas();
            boolean valida = m.substituicao()
                    ? v.stream().anyMatch(x -> x.id() == m.a() && jogavel(x)) && bancoExibido().stream().anyMatch(x -> x.id() == m.b())
                      && (ilimitadas() || ultimo.subsFeitas() + subsPendentes() < ultimo.subsPermitidas())
                    : trocaValida(v, m.a(), m.b());
            if (valida) pendentes.add(m);
        }
    }

    /** As vagas como ficam com as preparadas (a posição de cada vaga não muda). */
    List<Atleta> vagasExibidas() {
        List<Atleta> v = new ArrayList<>(ultimo == null ? List.of() : ultimo.vagas());
        if (formacaoPendente != null && !v.isEmpty()) {
            // formação nova preparada: cada um na vaga em que rende mais (como o motor fará no Confirmar)
            Posicao[] novas = Formacoes.vagas(formacaoPendente);
            if (novas != null && novas.length == v.size()) {
                Posicao[] nat = new Posicao[v.size()];
                for (int i = 0; i < nat.length; i++) nat[i] = v.get(i).natural();
                int[] d = Escalador.remapear(nat, novas);
                Atleta[] ordem = new Atleta[novas.length];
                for (int i = 0; i < d.length; i++) {
                    Atleta x = v.get(i);
                    ordem[d[i]] = new Atleta(x.id(), x.nome(), x.natural(), novas[d[i]], x.forca(), x.amarelo(), x.energia(), x.lesionado());
                }
                v = new ArrayList<>(java.util.Arrays.asList(ordem));
            }
        }
        for (Mudanca m : pendentes) {
            int ia = indice(v, m.a());
            if (ia < 0) continue;
            if (m.substituicao()) {
                Atleta entra = doBanco(m.b());
                if (entra == null) continue;
                Posicao vaga = v.get(ia).vaga();
                v.set(ia, new Atleta(entra.id(), entra.nome(), entra.natural(), vaga, entra.forca(), entra.amarelo(), entra.energia(), entra.lesionado()));
            } else {
                int ib = indice(v, m.b());
                if (ib < 0) continue;
                Atleta x = v.get(ia), y = v.get(ib);
                v.set(ia, new Atleta(y.id(), y.nome(), y.natural(), x.vaga(), y.forca(), y.amarelo(), y.energia(), y.lesionado()));
                v.set(ib, new Atleta(x.id(), x.nome(), x.natural(), y.vaga(), x.forca(), x.amarelo(), x.energia(), x.lesionado()));
            }
        }
        return v;
    }

    /** O banco sem quem já está preparado para entrar. */
    List<Atleta> bancoExibido() {
        Set<Integer> entram = new HashSet<>();
        for (Mudanca m : pendentes) if (m.substituicao()) entram.add(m.b());
        List<Atleta> l = new ArrayList<>();
        if (ultimo != null) for (Atleta a : ultimo.banco()) if (!entram.contains(a.id())) l.add(a);
        // banco na ordem das posições, do goleiro ao centroavante (o mais forte primeiro), requisito
        l.sort(java.util.Comparator.comparingInt((Atleta a) -> CampoEscalacao.ordemBanco(a.natural())).thenComparingInt(a -> -a.forca()));
        return l;
    }

    /** Ids na vaga com mudança preparada (entra ou troca). */
    private Set<Integer> idsPendentes() {
        Set<Integer> s = new HashSet<>();
        for (Mudanca m : pendentes) {
            if (m.substituicao()) s.add(m.b());
            else {
                s.add(m.a());
                s.add(m.b());
            }
        }
        return s;
    }

    private Atleta doBanco(int id) {
        if (ultimo == null) return null;
        for (Atleta a : ultimo.banco()) if (a.id() == id) return a;
        return null;
    }

    private Atleta doCampoOriginal(int id) {
        if (ultimo == null) return null;
        for (Atleta a : ultimo.vagas()) if (a.id() == id) return a;
        return null;
    }

    private static int indice(List<Atleta> l, int id) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).id() == id) return i;
        return -1;
    }

    private boolean jogavel(Atleta a) {
        return a != null && ultimo != null && !ultimo.terminado() && !ultimo.expulso(a.id()) && !ultimo.foraPorLesao(a.id());
    }

    /** Vaga vazia: o expulso (ou quem saiu lesionado sem troca) continua na vaga, mas não joga. */
    private boolean vazia(Atleta a) {
        return a != null && ultimo != null && !ultimo.terminado() && (ultimo.expulso(a.id()) || ultimo.foraPorLesao(a.id()));
    }

    /** Troca de posição: os dois nas vagas, pelo menos um jogando; o outro pode ser uma vaga vazia. */
    private boolean trocaValida(List<Atleta> v, int a, int b) {
        Atleta x = null, y = null;
        for (Atleta t : v) {
            if (t.id() == a) x = t;
            if (t.id() == b) y = t;
        }
        if (x == null || y == null || a == b) return false;
        return jogavel(x) && (jogavel(y) || vazia(y)) || vazia(x) && jogavel(y);
    }

    /** Depois de trocar o reserva de uma substituição preparada, as mudanças seguintes passam a citar o novo. */
    private void trocarId(int desde, int antigo, int novo) {
        for (int i = desde; i < pendentes.size(); i++) {
            Mudanca m = pendentes.get(i);
            if (m.substituicao()) {
                if (m.a() == antigo) pendentes.set(i, new Mudanca(true, novo, m.b()));
            } else if (m.a() == antigo || m.b() == antigo) {
                pendentes.set(i, new Mudanca(false, m.a() == antigo ? novo : m.a(), m.b() == antigo ? novo : m.b()));
            }
        }
    }

    // ------------------------------------------------------------------ campo
    private Clube meuClube() { return clube.get(); }

    private Jogador jogador(int id) { return id < 0 ? null : sessao.jogo.mundo().jogador(id); }

    /** A foto de um atleta desta tela (a do cabeçalho da camada: lesionado/expulso). */
    Node fotoDe(int id, double w, double h) { return foto(id, w, h); }

    private Node foto(int id, double w, double h) {
        Node f = fonte.foto(id, w, h);
        if (f != null) return f;
        Jogador j = jogador(id);
        return j == null ? Foto.silhueta(w, h) : Foto.de(sessao.jogo.foto(id), j.dbId, w, h);
    }

    private double encaixe(int id, Posicao natural, Posicao vaga) {
        double e = fonte.encaixe(id, natural, vaga);
        if (!Double.isNaN(e)) return e;
        Jogador j = jogador(id);
        if (j != null) return Escalador.encaixe(j, vaga);
        return natural == null ? 1 : natural.encaixe(vaga);
    }

    private void desenharCampo(Estado e) {
        Clube c = meuClube();
        campo.cor(fonte.cor() != null ? fonte.cor() : c == null ? null : c.cor1);
        List<Atleta> vagas = vagasExibidas();
        Posicao[] pos = new Posicao[vagas.size()];
        for (int i = 0; i < pos.length; i++) pos[i] = vagas.get(i).vaga();
        double[][] xy = fonte.coordenadas(pos);
        if (xy == null) {
            String formacao = formacaoPendente != null ? formacaoPendente : formacaoAtual();
            if (formacao == null || !java.util.Arrays.equals(Formacoes.vagas(formacao), pos)) formacao = null;
            xy = CampoEscalacao.coordenadasOnze(formacao, pos);
        }
        Integer cap = fonte.capitao() != null ? fonte.capitao() : c != null && c.tatica != null ? c.tatica.capitao : null;
        Set<Integer> pend = idsPendentes();
        Set<Integer> entram = new HashSet<>();
        for (Mudanca m : pendentes) if (m.substituicao()) entram.add(m.b());
        List<CampoEscalacao.Peca> pecas = new ArrayList<>();
        int sel = -1;
        for (int i = 0; i < vagas.size(); i++) {
            Atleta a = vagas.get(i);
            boolean expulso = e.expulso(a.id()), foraLesao = e.foraPorLesao(a.id()), fora = expulso || foraLesao;
            boolean pendente = pend.contains(a.id());
            if (saiId != null && saiId == a.id()) sel = i;
            String marca = expulso ? "Expulso" : foraLesao ? "Um a menos" : pendente ? (entram.contains(a.id()) ? "Entra" : "Troca") : null;
            pecas.add(new CampoEscalacao.Peca(true, a.nome(), a.vaga().name(), foto(a.id(), 50, 50), a.forca(),
                    cap != null && cap == a.id(), !fora && encaixe(a.id(), a.natural(), a.vaga()) < 0.95, false,
                    fora ? null : (double) a.energia(), marca, fora, a.amarelo() && !fora, a.lesionado(), pendente));
        }
        campo.desenhar(pecas, xy, sel, -1);
    }

    private final class AcoesCampo implements CampoEscalacao.Acoes {
        private Atleta naVaga(int vaga) {
            List<Atleta> v = vagasExibidas();
            return vaga >= v.size() ? null : v.get(vaga);
        }

        @Override
        public void clicar(int vaga) {
            Atleta a = naVaga(vaga);
            if (vazia(a)) {
                // vaga do expulso: não sai nem entra ninguém nela, mas alguém em campo pode ir para lá
                entraId = null;
                if (saiId != null && saiId != a.id() && prepararTroca(saiId, a.id())) return;
                saiId = saiId != null && saiId == a.id() ? null : a.id();
                redesenhar();
                return;
            }
            if (!jogavel(a)) return;
            if (saiId != null && vaziaId(saiId)) {
                // escolheu a vaga vazia antes: este vai para lá
                int v0 = saiId;
                if (prepararTroca(v0, a.id())) return;
            }
            if (entraId != null) {
                // reserva escolhido antes: prepara a substituição
                int en = entraId;
                if (!preparar(a.id(), en)) redesenhar();
                return;
            }
            if (saiId == null) saiId = a.id();
            else if (saiId == a.id()) saiId = null;
            else {
                // dois em campo: troca de posição (preparada)
                int outro = saiId;
                if (prepararTroca(outro, a.id())) return;
            }
            redesenhar();
        }

        @Override
        public void soltar(int vaga, String s) {
            Atleta a = naVaga(vaga);
            if (s.length() < 2) return;
            if (s.startsWith("v")) {
                // quem ia sair (ainda não confirmado) arrastado de volta ao campo: desfaz a troca dele
                voltarAoCampo(Integer.parseInt(s.substring(1)));
                return;
            }
            if (vazia(a)) {
                // reserva na vaga do expulso: não (o time segue com um a menos); alguém do campo: troca de posição
                if (s.startsWith("s")) prepararTroca(Integer.parseInt(s.substring(1)), a.id());
                return;
            }
            if (!jogavel(a)) return;
            int id = Integer.parseInt(s.substring(1));
            if (s.startsWith("e")) preparar(a.id(), id);
            else if (s.startsWith("s") && id != a.id()) prepararTroca(id, a.id());
        }

        @Override
        public String arraste(int vaga) {
            Atleta a = naVaga(vaga);
            return jogavel(a) ? "s" + a.id() : null;
        }

        @Override
        public void fantasma(int vaga, Dragboard db) {
            Atleta a = naVaga(vaga);
            if (a == null) return;
            arrastando = a.id();
            CampoEscalacao.fantasma(db, campo, foto(a.id(), 30, 36), a.nome(), a.natural(), a.forca());
        }

        @Override
        public double encaixeDoArraste(int vaga) {
            Atleta alvo = naVaga(vaga);
            if (arrastando == null || alvo == null) return Double.NaN;
            if (vazia(alvo) && bancoExibido().stream().anyMatch(b -> b.id() == arrastando)) return Double.NaN; // reserva não entra no lugar do expulso
            if (!jogavel(alvo) && !vazia(alvo)) return Double.NaN;
            Atleta x = acharAtleta(arrastando);
            if (x == null) x = doCampoOriginal(arrastando);
            return x == null ? Double.NaN : encaixe(x.id(), x.natural(), alvo.vaga());
        }

        @Override public void fimArraste() { arrastando = null; }
    }

    /** Desfaz a substituição preparada em que {@code saiId} sairia (ele volta para a vaga). */
    private void voltarAoCampo(int sai) {
        for (Mudanca m : List.copyOf(pendentes)) {
            if (m.substituicao() && m.a() == sai) {
                desfazer(m);
                return;
            }
        }
    }

    private boolean vaziaId(int id) {
        for (Atleta a : vagasExibidas()) if (a.id() == id) return vazia(a);
        return false;
    }

    private Atleta acharAtleta(int id) {
        for (Atleta a : vagasExibidas()) if (a.id() == id) return a;
        for (Atleta a : bancoExibido()) if (a.id() == id) return a;
        return null;
    }

    private void redesenhar() {
        assinatura = "";
        if (ultimo != null) atualizar(ultimo);
    }

    // ------------------------------------------------------------------ banco
    private void desenharBanco(Estado e) {
        banco.getChildren().clear();
        boolean pode = cabeSubstituicao() && !bancoExibido().isEmpty();
        for (Atleta a : bancoExibido()) banco.getChildren().add(linhaBanco(a, pode));
        // as preparadas (quem vai sair; trocas de posição), cada uma com o botão para desfazer só ela
        for (Mudanca m : pendentes) {
            Atleta sai = m.substituicao() ? doCampoOriginal(m.a()) : acharAtleta(m.a());
            if (sai == null) continue;
            Atleta outro = m.substituicao() ? null : acharAtleta(m.b());
            Label n = Ui.rotulo(m.substituicao() ? sai.nome() : sai.nome() + " ⇄ " + (outro == null ? "?" : outro.nome())
                    + (outro != null && (vazia(sai) || vazia(outro)) ? " (vaga do expulso)" : ""));
            n.setMinWidth(0);
            Button volta = Ui.botao("↺", () -> desfazer(m), "fantasma");
            volta.setFocusTraversable(false);
            Tooltip.install(volta, new Tooltip("Desfazer esta troca"));
            HBox h = Ui.linha(8, Ui.pos(sai.natural()), n, Ui.espaco(), Ui.etiqueta(m.substituicao() ? "Sai" : "Troca", "azul"), volta);
            h.getStyleClass().addAll("atleta", "sai-pendente");
            if (m.substituicao()) {
                // requisito: puxar de volta para o time quem ia sair, enquanto não confirmou
                h.setCursor(javafx.scene.Cursor.OPEN_HAND);
                Tooltip.install(h, new Tooltip("Arraste de volta para o campo (ou clique em ↺) para desfazer"));
                Atleta quem = sai;
                h.setOnDragDetected(x -> {
                    Dragboard db = h.startDragAndDrop(TransferMode.MOVE);
                    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                    cc.putString("v" + quem.id());
                    db.setContent(cc);
                    CampoEscalacao.fantasma(db, campo, foto(quem.id(), 30, 36), quem.nome(), quem.natural(), quem.forca());
                    x.consume();
                });
            }
            h.setStyle("-fx-padding: 1 8 1 6;");
            h.setMinHeight(30);
            h.setPrefHeight(30);
            h.setMaxHeight(30);
            banco.getChildren().add(h);
        }
        if (banco.getChildren().isEmpty()) banco.getChildren().add(Ui.rotulo("Ninguém no banco.", "texto-suave"));
    }

    private Node linhaBanco(Atleta a, boolean pode) {
        // linha compacta (~30 px): foto pequena, posição, nome, energia e força
        Node f = CampoEscalacao.fotoRedonda(foto(a.id(), 20, 20), 20, meuClube() == null ? null : meuClube().cor1, false);
        Label n = Ui.rotulo(a.nome());
        n.setMinWidth(0);
        HBox h = Ui.linha(7, f, Ui.pos(a.natural()), n);
        h.setMinHeight(30);
        h.setPrefHeight(30);
        h.setMaxHeight(30);
        if (a.amarelo()) {
            Region r = new Region();
            r.getStyleClass().add("cartao-amarelo");
            h.getChildren().add(r);
        }
        h.getChildren().addAll(Ui.espaco(), CampoEscalacao.barraEnergia(a.energia(), 40, true), Ui.forca(a.forca()));
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("atleta");
        h.setStyle("-fx-padding: 1 8 1 6;");
        if (entraId != null && entraId == a.id()) h.getStyleClass().add("entra");
        if (!pode) {
            h.setStyle(h.getStyle() + " -fx-cursor: default;");
            return h;
        }
        h.setCursor(javafx.scene.Cursor.OPEN_HAND);
        h.setOnMouseClicked(x -> {
            if (saiId != null && vaziaId(saiId)) saiId = null; // ninguém entra no lugar do expulso
            if (saiId != null) {
                // quem sai escolhido antes: prepara a substituição
                int s = saiId;
                if (preparar(s, a.id())) return;
            }
            entraId = entraId != null && entraId == a.id() ? null : a.id();
            redesenhar();
        });
        h.setOnDragDetected(x -> {
            Dragboard db = h.startDragAndDrop(TransferMode.MOVE);
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString("e" + a.id());
            db.setContent(cc);
            arrastando = a.id();
            CampoEscalacao.fantasma(db, campo, foto(a.id(), 30, 36), a.nome(), a.natural(), a.forca());
            x.consume();
        });
        h.setOnDragDone(x -> arrastando = null);
        h.setOnDragOver(x -> {
            String s = x.getDragboard().getString();
            if (s != null && s.startsWith("s")) x.acceptTransferModes(TransferMode.MOVE);
            x.consume();
        });
        h.setOnDragEntered(x -> {
            String s = x.getDragboard().getString();
            if (s != null && s.startsWith("s")) h.getStyleClass().add("entra");
        });
        h.setOnDragExited(x -> {
            if (entraId == null || entraId != a.id()) h.getStyleClass().remove("entra");
        });
        h.setOnDragDropped(x -> {
            String s = x.getDragboard().getString();
            if (s != null && s.startsWith("s")) preparar(Integer.parseInt(s.substring(1)), a.id());
            x.setDropCompleted(true);
            x.consume();
        });
        return h;
    }
}
