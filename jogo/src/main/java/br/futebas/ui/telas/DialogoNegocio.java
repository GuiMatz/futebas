package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.ui.comp.CampoDinheiro;
import br.futebas.ui.comp.Foto;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Tema;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Diálogos de negociação do Mercado (requisito, 13/11/2026: "melhore essa porra"): cabeçalho com
 * foto, nome, posição, idade, força e o clube atual na cor dele; referências em cartões pequenos; a
 * oferta num campo grande com os atalhos como segmentos; contrato em botões; e um resumo do custo em
 * uma linha ao lado dos botões. Só monta a tela: as chamadas ao Jogo ficam no PainelMercado.
 */
public final class DialogoNegocio {
    private DialogoNegocio() {}

    public enum Tipo { COMPRA, LIVRE, EMPRESTIMO }

    static final int[] MESES = {6, 12, 24, 36, 48, 60}; // até 5 anos (regra do jogo)
    private static final List<Integer> OPCOES_MESES = List.of(6, 12, 24, 36, 48, 60);

    /** O diálogo montado: conteúdo, resumo (vai ao lado dos botões) e os valores escolhidos. */
    public static final class Montagem {
        public final VBox conteudo = new VBox();
        public final HBox rodape = new HBox();
        public CampoDinheiro oferta, salario;
        int meses = 12;
        Runnable atualizar = () -> {};

        public int meses() { return meses; }
    }

    public static Montagem montar(Jogo j, Clube meu, Jogador x, Tipo tipo) {
        Montagem mt = new Montagem();
        Clube atual = x.clubeId == null ? null : j.mundo().clube(x.clubeId);
        mt.conteudo.getStyleClass().add("ng");
        for (Region r : new Region[] {mt.conteudo}) {
            r.setMinWidth(LARGURA);
            r.setPrefWidth(LARGURA);
            r.setMaxWidth(LARGURA);
        }
        mt.conteudo.getChildren().add(cabecalho(j, x, atual, tipo));

        VBox corpo = new VBox(14);
        corpo.getStyleClass().add("ng-corpo");
        Jogo.ResumoFinanceiro fin = j.financas(meu.id);
        long caixa = meu.dinheiro;
        long podePagar = Math.max(0, caixa + Math.max(0, fin.limiteEmprestimo() - fin.divida()));
        long salPedido = j.salarioPedido(x.id);

        // ---------------------------------------------------------------- referências
        List<Node> chips = new ArrayList<>();

        Resumo resumo = new Resumo();
        Label nota = Ui.rotulo("", "ng-nota");
        nota.setMinWidth(0);
        nota.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        mt.rodape.getChildren().add(nota);
        mt.rodape.setAlignment(Pos.CENTER_LEFT);
        mt.rodape.setMinWidth(0);
        mt.rodape.setPrefWidth(10);
        mt.rodape.setMaxWidth(Double.MAX_VALUE);

        switch (tipo) {
            case COMPRA -> {
                long pedido = j.precoPedido(x.id);
                chips.add(new Cartao("Preço pedido", pedido == 0 ? "Livre" : Textos.dinheiro(pedido)).no);
                if (x.contrato != null && x.contrato.multa > 0) chips.add(new Cartao("Multa rescisória", Textos.dinheiro(x.contrato.multa)).no);
                chips.add(new Cartao("Salário pedido", Textos.dinheiro(salPedido), "por mês").no);
                Cartao cxCaixa = new Cartao("Em caixa", Textos.dinheiro(caixa), podePagar > caixa ? "+ crédito " + Textos.dinheiro(podePagar - caixa) : null);
                chips.add(cxCaixa.no);
                corpo.getChildren().add(gradeCartoes(chips));

                mt.oferta = new CampoDinheiro(pedido, pedido, podePagar, true);
                mt.oferta.campo().getStyleClass().add("grande");
                corpo.getChildren().add(secao("Sua oferta", mt.oferta));
                // requisito (uma confirmação só): o salário vai junto; se o clube e o jogador aceitarem, fecha sozinho
                mt.salario = new CampoDinheiro(salPedido, salPedido, 0, true);
                corpo.getChildren().add(secao("Salário mensal", mt.salario));
                corpo.getChildren().add(secao("Duração do contrato", contrato(mt)));
                nota.setText("Se o clube e o jogador aceitarem, fecha sem outra confirmação.");
                Runnable at = () -> {
                    long of = mt.oferta.valor(), sal = mt.salario.valor(), depois = caixa - of;
                    cxCaixa.alerta(of > podePagar ? "ng-perigo" : of > caixa ? "ng-atencao" : null);
                    resumo.definir(Textos.dinheiro(of) + "  +  " + Textos.dinheiro(sal) + "/mês por " + duracao(mt.meses), depois);
                };
                mt.oferta.aoMudar(at);
                mt.salario.aoMudar(at);
                mt.atualizar = at;
            }
            case LIVRE -> {
                chips.add(new Cartao("Transferência", "Grátis", "passe livre").no);
                chips.add(new Cartao("Salário pedido", Textos.dinheiro(salPedido), "por mês").no);
                Cartao cxCaixa = new Cartao("Em caixa", Textos.dinheiro(caixa));
                chips.add(cxCaixa.no);
                corpo.getChildren().add(gradeCartoes(chips));
                mt.salario = new CampoDinheiro(salPedido, salPedido, 0, true);
                mt.salario.campo().getStyleClass().add("grande");
                corpo.getChildren().add(secao("Salário mensal", mt.salario));
                corpo.getChildren().add(secao("Duração do contrato", contrato(mt)));
                nota.setText("Ele responde na hora: assina ou recusa.");
                Runnable at = () -> resumo.definir(Textos.dinheiro(mt.salario.valor()) + "/mês por " + duracao(mt.meses), caixa);
                mt.salario.aoMudar(at);
                mt.atualizar = at;
            }
            case EMPRESTIMO -> {
                long taxa = j.taxaEmprestimo(x.id);
                long sal = x.contrato == null ? 0 : x.contrato.salario;
                chips.add(new Cartao("Taxa do empréstimo", Textos.dinheiro(taxa)).no);
                if (x.contrato != null) chips.add(new Cartao("Salário dele", Textos.dinheiro(sal), "por mês, pago por você").no);
                chips.add(new Cartao("Duração", "Fim da temporada", "depois volta ao clube").no);
                Cartao cxCaixa = new Cartao("Em caixa", Textos.dinheiro(caixa));
                if (taxa > caixa) cxCaixa.alerta(taxa > podePagar ? "ng-perigo" : "ng-atencao");
                chips.add(cxCaixa.no);
                corpo.getChildren().add(gradeCartoes(chips));
                long depois = caixa - taxa;
                resumo.definir(Textos.dinheiro(taxa) + (sal > 0 ? "  +  " + Textos.dinheiro(sal) + "/mês até o fim da temporada" : ""), depois);
                nota.setText("O clube dele responde em até uma semana.");
                mt.atualizar = () -> {};
            }
        }
        corpo.getChildren().add(resumo.no);
        mt.conteudo.getChildren().add(corpo);
        mt.atualizar.run();
        return mt;
    }

    // ------------------------------------------------------------------ partes
    private static Node cabecalho(Jogo j, Jogador x, Clube atual, Tipo tipo) {
        Node foto = Foto.de(j.foto(x.id), x.dbId, 84, 100);
        Label sobre = Ui.rotulo(switch (tipo) {
            case COMPRA -> "PROPOSTA DE TRANSFERÊNCIA";
            case LIVRE -> "CONTRATAÇÃO · PASSE LIVRE";
            case EMPRESTIMO -> "PEDIDO DE EMPRÉSTIMO";
        }, "ng-sobre");
        Label nome = Ui.rotulo(x.nomeExibicao(), "ng-nome");
        nome.setMinWidth(0);
        HBox info = Ui.linha(8, Ui.pos(x.posicao),
                Ui.rotulo(x.posicao == null ? "" : x.posicao.nome, "ng-info"),
                Ui.rotulo("·", "ng-info-sep"), Ui.rotulo(x.idade(j.ano()) + " anos", "ng-info"),
                Ui.rotulo("·", "ng-info-sep"), Imagens.bandeira(x.pais, 12), Ui.rotulo(Textos.pais(x.pais), "ng-info"));
        HBox clube = atual == null
                ? Ui.linha(8, Ui.rotulo("Sem clube", "ng-clube"))
                : Ui.linha(8, Imagens.escudo(atual.id, 22), Ui.rotulo(atual.nome, "ng-clube"));
        VBox id = new VBox(3, sobre, nome, info, clube);
        VBox.setMargin(clube, new javafx.geometry.Insets(4, 0, 0, 0));
        id.setAlignment(Pos.CENTER_LEFT);
        id.setMinWidth(0);
        HBox.setHgrow(id, Priority.ALWAYS);

        int f = x.forcaExibida();
        Label nf = Ui.forcaGrande(f, "ng-forca");
        VBox forca = new VBox(-4, nf, Ui.rotulo("FORÇA", "ng-forca-rotulo"));
        forca.setAlignment(Pos.CENTER);
        forca.getStyleClass().add("ng-forca-caixa");
        forca.setMinWidth(Region.USE_PREF_SIZE);

        HBox h = new HBox(16, foto, id, forca);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("ng-cabecalho");
        h.setStyle(atual == null ? "-fx-background-color: linear-gradient(to right, #2b3038 0%, #20242a 100%);"
                : Tema.estiloCabecalho(atual.cor1, atual.cor2));
        // filete na outra cor do clube, embaixo do cabeçalho
        Region filete = new Region();
        filete.setMinHeight(3);
        filete.setPrefHeight(3);
        filete.setStyle("-fx-background-color: " + (atual == null ? "#3a4049" : Tema.hex(Tema.faixaClube(atual.cor1, atual.cor2))) + ";");
        return new VBox(h, filete);
    }

    private static Node gradeCartoes(List<Node> chips) {
        HBox h = new HBox(8);
        for (Node n : chips) {
            HBox.setHgrow(n, Priority.ALWAYS);
            ((Region) n).setMaxWidth(Double.MAX_VALUE);
            ((Region) n).setPrefWidth(10);
            h.getChildren().add(n);
        }
        return h;
    }

    private static Node secao(String rotulo, Node campo) {
        return new VBox(6, Ui.rotulo(rotulo.toUpperCase(java.util.Locale.ROOT), "ng-rotulo"), campo);
    }

    private static Node contrato(Montagem mt) {
        HBox seg = Ui.segmentosCheios(OPCOES_MESES, m -> m == 6 ? "6 meses" : m == 12 ? "1 ano" : (m / 12) + " anos", mt.meses, m -> {
            mt.meses = m;
            mt.atualizar.run();
        });
        seg.getStyleClass().add("ng-contrato");
        return seg;
    }

    static final double LARGURA = 620;

    /** Faixa do resumo: custo à esquerda, caixa depois do negócio à direita (vermelho se ficar negativo). */
    private static final class Resumo {
        final HBox no;
        final Label custo = Ui.rotulo("", "ng-resumo-valor"), depois = Ui.rotulo("", "ng-resumo-valor");

        Resumo() {
            custo.setMinWidth(0);
            custo.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
            depois.setMinWidth(Region.USE_PREF_SIZE);
            VBox esq = new VBox(1, Ui.rotulo("CUSTO", "ng-cartao-rotulo"), custo);
            esq.setMinWidth(0);
            HBox.setHgrow(esq, Priority.ALWAYS);
            Label rd = Ui.rotulo("CAIXA DEPOIS", "ng-cartao-rotulo");
            VBox dir = new VBox(1, rd, depois);
            dir.setAlignment(Pos.CENTER_RIGHT);
            dir.setMinWidth(Region.USE_PREF_SIZE);
            no = new HBox(16, esq, dir);
            no.setAlignment(Pos.CENTER_LEFT);
            no.getStyleClass().add("ng-resumo");
        }

        void definir(String c, long caixaDepois) {
            custo.setText(c);
            depois.setText(Textos.dinheiro(caixaDepois));
            depois.getStyleClass().remove("ng-negativo");
            if (caixaDepois < 0) depois.getStyleClass().add("ng-negativo");
        }
    }

    static String duracao(int meses) {
        return meses == 6 ? "6 meses" : meses == 12 ? "1 ano" : (meses / 12) + " anos";
    }

    /** Cartão pequeno de referência: rótulo em cima, valor, e uma linha opcional. */
    private static final class Cartao {
        final VBox no;

        Cartao(String rotulo, String valor) { this(rotulo, valor, null); }

        Cartao(String rotulo, String valor, String sub) {
            Label r = Ui.rotulo(rotulo.toUpperCase(java.util.Locale.ROOT), "ng-cartao-rotulo");
            Label v = Ui.rotulo(valor, "ng-cartao-valor");
            r.setMinWidth(0);
            v.setMinWidth(0);
            no = new VBox(2, r, v);
            if (sub != null) {
                Label s = Ui.rotulo(sub, "ng-cartao-sub");
                s.setMinWidth(0);
                s.setWrapText(true);
                no.getChildren().add(s);
            }
            no.getStyleClass().add("ng-cartao");
            no.setMinWidth(0);
        }

        void alerta(String classe) {
            no.getStyleClass().removeAll("ng-perigo", "ng-atencao");
            if (classe != null) no.getStyleClass().add(classe);
        }
    }

    /** Mostra a montagem como diálogo da janela; devolve true se confirmou (Enter) e false se cancelou (Esc). */
    static boolean mostrar(Montagem mt, String confirmar) {
        javafx.application.Platform.runLater(() -> {
            if (mt.oferta != null) mt.oferta.campo().requestFocus();
            else if (mt.salario != null) mt.salario.campo().requestFocus();
        });
        return Ui.modalCaixa(null, mt.conteudo, "caixa-negocio", mt.rodape, confirmar, "Cancelar") == 0;
    }

    /** Para a ferramenta de PNG: a caixa completa, sem mostrar. */
    public static VBox caixa(Montagem mt, String confirmar) {
        return Ui.caixaModal(null, mt.conteudo, "caixa-negocio", mt.rodape, k -> {}, confirmar, "Cancelar");
    }
}
