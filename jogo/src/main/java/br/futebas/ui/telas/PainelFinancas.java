package br.futebas.ui.telas;

import br.futebas.Balanceamento;
import br.futebas.Jogo;
import br.futebas.financas.Financas;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Clube;
import br.futebas.mundo.FinancasClube;
import br.futebas.ui.comp.CampoDinheiro;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Finanças: números do caixa no topo; embaixo, receitas × despesas (temporada atual e anterior),
 * orçamento salarial e projeção, evolução do caixa mês a mês (com a projeção até o fim da
 * temporada), estádio e banco.
 */
final class PainelFinancas implements Hub.Painel {
    private final Hub hub;

    PainelFinancas(Hub hub) {
        this.hub = hub;
    }

    // ================================================================== registro do caixa (sessão)
    /** Caixa do seu clube por semana absoluta, guardado enquanto o jogo está aberto (para o gráfico). */
    private static final Map<String, TreeMap<Integer, Long>> CAIXA = new HashMap<>();

    /** Anota o caixa do seu clube nesta semana (chamado pelas telas de gestão). */
    static void registrar(Jogo j) {
        if (j == null) return;
        Clube c = j.meuClube();
        if (c == null) return;
        CAIXA.computeIfAbsent(c.id, k -> new TreeMap<>()).put(Calendario.absoluta(j.ano(), j.semana()), c.dinheiro);
    }

    /** Orçamento mensal da folha: a parte da receita de referência que a diretoria aceita gastar em salários. */
    static long orcamentoFolha(Clube c, int ano) {
        double receita = Math.max(Financas.receitaReferencia(c, ano), c.receitaUltimaTemporada);
        return Financas.arred(receita * Balanceamento.FOLHA_ALVO / 12.0);
    }

    /** Barra "usado de limite" com os valores à direita. */
    static VBox barraUso(String rotulo, long usado, long limite) {
        double f = limite <= 0 ? 0 : usado / (double) limite;
        ProgressBar b = new ProgressBar(Math.min(1, f));
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("fin-uso");
        if (f > 1) b.getStyleClass().add("fin-uso-estouro");
        else if (f > 0.9) b.getStyleClass().add("fin-uso-alerta");
        Label pct = Ui.rotulo(Math.round(f * 100) + "%", "numero", f > 1 ? "negativo" : "texto-medio");
        HBox cab = Ui.linha(8, Ui.rotulo(rotulo, "texto-medio"), Ui.espaco(), Ui.rotulo(Textos.dinheiro(usado) + " de " + Textos.dinheiro(limite), "numero"), pct);
        return new VBox(6, cab, b);
    }

    private static int inicioTemporada(Clube c, Jogo j) {
        if ("europeu".equals(c.calendario)) {
            int v = Balanceamento.SEMANA_VIRADA_EUROPEIA;
            return Calendario.absoluta(j.semana() >= v ? j.ano() : j.ano() - 1, v);
        }
        return Calendario.absoluta(j.ano(), 1);
    }

    private static int fimTemporada(Clube c, Jogo j) {
        if ("europeu".equals(c.calendario)) {
            int v = Balanceamento.SEMANA_VIRADA_EUROPEIA;
            return Calendario.absoluta(j.semana() >= v ? j.ano() + 1 : j.ano(), v - 1);
        }
        return Calendario.absoluta(j.ano(), 52);
    }

    // ================================================================== tela
    @Override
    public Node construir() {
        Jogo j = hub.jogo();
        Clube c = hub.clube();
        if (c == null && j.meuTimeKings() != null) return kings(j, j.meuTimeKings());
        if (c == null) return Ui.conteudo(Ui.titulo("Finanças"), Ui.vazio("Sem clube.", null, null));
        registrar(j);
        Jogo.ResumoFinanceiro f = j.financas(c.id);
        FinancasClube t = f.temporada();
        long res = t.receitas() - t.despesas();
        Projecao pj = projetar(j, c, f);

        HBox kpis = new HBox(16, kpi("Em caixa", Textos.dinheiro(f.saldo()), f.saldo() < 0 ? "negativo" : null),
                kpi("Dívida no banco", Textos.dinheiro(f.divida()), f.divida() > 0 ? "negativo" : null),
                kpi("Folha mensal", Textos.dinheiro(f.folhaMensal()), null),
                kpi("Resultado da temporada", (res >= 0 ? "+" : "") + Textos.dinheiro(res), res >= 0 ? "positivo" : "negativo"));
        for (Node n : kpis.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        // receitas × despesas | orçamento e projeção
        VBox contas = contas(t, c.financasAnterior);
        VBox lado = orcamento(j, c, f, pj);
        HBox linha1 = new HBox(16, contas, lado);
        HBox.setHgrow(contas, Priority.ALWAYS);
        contas.setPrefWidth(620);
        lado.setPrefWidth(380);
        lado.setMinWidth(340);
        lado.setMaxWidth(440);

        VBox grafico = evolucao(j, c, f, pj);

        HBox linha3 = new HBox(16, estadio(j, c), banco(j, f));
        for (Node n : linha3.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
            ((VBox) n).setPrefWidth(500);
        }
        return Ui.rolar(Ui.conteudo(Ui.titulo("Finanças"), kpis, linha1, grafico, linha3));
    }

    /**
     * O mesmo painel para o time da Kings, agora com dinheiro de verdade (regra do jogo, 12/11/2026):
     * os mesmos números no topo (caixa, orçamento, prêmios e gastos da temporada), receitas × despesas,
     * o caixa livre para propostas (o reservado nas propostas em andamento sai dele), a tabela de
     * prêmios, o histórico por temporada e o extrato.
     */
    private Node kings(Jogo j, br.futebas.kings.LigaKings.TimeKings tk) {
        int ano = j.ano();
        long orc = tk.total(ano, "orcamento"), prem = tk.total(ano, "premio"), gastos = -tk.total(ano, "contratacao"), treino = -tk.total(ano, "treino");
        HBox kpis = new HBox(16, kpi("Em caixa", Textos.dinheiro(tk.caixa), tk.caixa <= 0 ? "negativo" : null),
                kpi("Orçamento da temporada", Textos.dinheiro(br.futebas.kings.Kings.ORCAMENTO_TEMPORADA), null),
                kpi("Prêmios em " + ano, "+" + Textos.dinheiro(prem), prem > 0 ? "positivo" : null),
                kpi("Gastos em " + ano, Textos.dinheiro(gastos + treino), gastos + treino > 0 ? "negativo" : null));
        for (Node n : kpis.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        // receitas × despesas da temporada
        GridPane g = new GridPane();
        g.getStyleClass().add("fin-tabela");
        g.setHgap(14);
        g.setVgap(8);
        String[][] linhas = {
                {"Orçamento da liga", "+" + Textos.dinheiro(orc), "igual para todo time, por temporada"},
                {"Prêmios", "+" + Textos.dinheiro(prem), "títulos de liga, Kings Cup e Copa do Mundo"},
                {"Wildcards", "−" + Textos.dinheiro(gastos), "taxa ao clube + salário da temporada"},
                {"Treino", "−" + Textos.dinheiro(treino), "o treino não custa"},
                {"Draft e elenco real", Textos.dinheiro(0), "a liga paga"}};
        int r = 0;
        for (String[] l : linhas) {
            Label n = Ui.rotulo(l[0], "texto-suave");
            n.setMinWidth(150);
            Label v = Ui.rotulo(l[1], "numero");
            v.setMinWidth(110);
            g.addRow(r++, n, v, Ui.rotulo(l[2], "texto-pequeno"));
        }
        long saldo = orc + prem - gastos - treino;
        Label res = Ui.rotulo((saldo >= 0 ? "+" : "") + Textos.dinheiro(saldo), "numero");
        res.getStyleClass().add(saldo >= 0 ? "positivo" : "negativo");
        g.addRow(r, Ui.rotulo("Resultado " + ano, "texto-forte"), res);
        VBox contas = Ui.painelAcoes("Receitas e despesas", Ui.rotulo("temporada " + ano, "texto-suave"), g,
                Ui.paragrafo("O caixa nunca fica negativo: sem dinheiro, não sai proposta (nem a sua, nem a da IA). Presidente: "
                        + (tk.presidente == null ? "—" : tk.presidente) + ".", "texto-pequeno"));

        // caixa livre, reservado e prêmios
        long livre = j.caixaKingsDisponivel(), reservado = j.caixaKingsComprometido();
        int max = PainelMercado.maximoKings();
        ProgressBar b = new ProgressBar(Math.min(1, tk.elenco.size() / (double) max));
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("fin-uso");
        if (tk.elenco.size() >= max) b.getStyleClass().add("fin-uso-alerta");
        VBox uso = new VBox(6, Ui.linha(8, Ui.rotulo("Vagas no elenco", "texto-medio"), Ui.espaco(), Ui.rotulo(tk.elenco.size() + " de " + max, "numero")), b);
        GridPane premios = new GridPane();
        premios.setHgap(14);
        premios.setVgap(4);
        premios.addRow(0, Ui.rotulo("Título de liga (cada Split)", "texto-medio"), Ui.rotulo("+" + Textos.dinheiro(br.futebas.kings.Kings.PREMIO_LIGA), "numero"));
        premios.addRow(1, Ui.rotulo("Kings Cup", "texto-medio"), Ui.rotulo("+" + Textos.dinheiro(br.futebas.kings.Kings.PREMIO_COPA), "numero"));
        premios.addRow(2, Ui.rotulo("Kings World Cup Clubs", "texto-medio"), Ui.rotulo("+" + Textos.dinheiro(br.futebas.kings.Kings.PREMIO_MUNDIAL), "numero"));
        VBox lado = Ui.painelAcoes("Caixa para propostas", null,
                Ui.linha(18, Ui.dado("Livre", Textos.dinheiro(livre)), Ui.dado("Reservado em propostas", Textos.dinheiro(reservado))),
                uso, Ui.separadorH(), Ui.rotulo("Prêmios", "titulo-menor"), premios,
                Ui.botao("Mercado", () -> hub.selecionar("mercado")));
        HBox linha1 = new HBox(16, contas, lado);
        HBox.setHgrow(contas, Priority.ALWAYS);
        contas.setPrefWidth(620);
        lado.setPrefWidth(380);
        lado.setMinWidth(340);
        lado.setMaxWidth(440);

        // histórico por temporada e extrato
        TreeMap<Integer, long[]> porAno = new TreeMap<>(java.util.Comparator.reverseOrder());
        List<br.futebas.kings.LigaKings.MovimentoKings> ext = tk.extrato == null ? List.of() : tk.extrato;
        for (var x : ext) {
            long[] a = porAno.computeIfAbsent(x.ano, k -> new long[4]);
            switch (x.tipo == null ? "" : x.tipo) {
                case "orcamento" -> a[0] += x.valor;
                case "premio" -> a[1] += x.valor;
                default -> a[2] -= x.valor;
            }
        }
        GridPane hist = new GridPane();
        hist.setHgap(18);
        hist.setVgap(4);
        hist.addRow(0, Ui.rotulo("Temporada", "texto-suave"), Ui.rotulo("Orçamento", "texto-suave"), Ui.rotulo("Prêmios", "texto-suave"),
                Ui.rotulo("Gastos", "texto-suave"), Ui.rotulo("Saldo do ano", "texto-suave"));
        int hr = 1;
        for (Map.Entry<Integer, long[]> e : porAno.entrySet()) {
            long[] a = e.getValue();
            long sd = a[0] + a[1] - a[2];
            hist.addRow(hr++, Ui.rotulo(String.valueOf(e.getKey()), "texto-forte"), Ui.rotulo(Textos.dinheiro(a[0]), "numero"),
                    Ui.rotulo("+" + Textos.dinheiro(a[1]), "numero"), Ui.rotulo("−" + Textos.dinheiro(a[2]), "numero"),
                    Ui.rotulo((sd >= 0 ? "+" : "") + Textos.dinheiro(sd), "numero"));
        }
        VBox historico = Ui.painelAcoes("Histórico", null, porAno.isEmpty() ? Ui.rotulo("Sem movimentos ainda.", "texto-suave") : hist);
        VBox extrato = new VBox(4);
        for (int i = ext.size() - 1, n = 0; i >= 0 && n < 30; i--, n++) {
            var x = ext.get(i);
            Label v = Ui.rotulo((x.valor >= 0 ? "+" : "−") + Textos.dinheiro(Math.abs(x.valor)), "numero");
            v.getStyleClass().add(x.valor >= 0 ? "positivo" : "negativo");
            v.setMinWidth(100);
            Label quando = Ui.rotulo("S" + x.semana + "/" + x.ano, "texto-suave");
            quando.setMinWidth(70);
            extrato.getChildren().add(Ui.linha(10, quando, Ui.rotulo(x.descricao == null ? "" : x.descricao, "texto-medio"), Ui.espaco(), v));
        }
        VBox caixaExtrato = Ui.painelAcoes("Extrato", Ui.rotulo("últimos 30", "texto-suave"),
                extrato.getChildren().isEmpty() ? Ui.rotulo("Sem movimentos ainda.", "texto-suave") : extrato);
        HBox linha2 = new HBox(16, historico, caixaExtrato);
        HBox.setHgrow(caixaExtrato, Priority.ALWAYS);
        historico.setPrefWidth(460);
        caixaExtrato.setPrefWidth(560);
        return Ui.rolar(Ui.conteudo(Ui.titulo("Finanças"), kpis, linha1, linha2));
    }

    private static VBox kpi(String rotulo, String valor, String classe) {
        Label v = Ui.rotulo(valor, "numero-grande");
        v.setStyle("-fx-font-size: 28px;");
        if (classe != null) v.getStyleClass().add(classe);
        VBox b = Ui.painelAcoes(null, null, Ui.rotulo(rotulo, "texto-suave"), v);
        b.setSpacing(2);
        b.setMinWidth(170);
        return b;
    }

    // ------------------------------------------------------------------ receitas × despesas
    private VBox contas(FinancasClube t, FinancasClube ant) {
        GridPane g = new GridPane();
        g.getStyleClass().add("fin-tabela");
        ColumnConstraints c0 = new ColumnConstraints(150);
        ColumnConstraints c1 = new ColumnConstraints(80, 200, Double.MAX_VALUE);
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(110);
        c2.setHalignment(HPos.RIGHT);
        ColumnConstraints c3 = new ColumnConstraints(110);
        c3.setHalignment(HPos.RIGHT);
        ColumnConstraints c4 = new ColumnConstraints(70);
        c4.setHalignment(HPos.RIGHT);
        if (ant == null) {
            c3 = new ColumnConstraints(0);
            c4 = new ColumnConstraints(0);
        }
        g.getColumnConstraints().addAll(c0, c1, c2, c3, c4);
        int r = 0;
        g.addRow(r++, cab(""), cab(""), cab("Temporada"), cab(ant == null ? "" : "Anterior"), cab(ant == null ? "" : "Var."));
        long totRec = t.receitas(), totDesp = t.despesas();
        r = secao(g, r, "Receitas", "fin-receita", totRec, ant == null ? null : ant.receitas(), new Object[][]{
                {"Bilheteria", t.ingressos, ant == null ? null : ant.ingressos},
                {"TV e patrocínio", t.tvPatrocinio, ant == null ? null : ant.tvPatrocinio},
                {"Venda de jogadores", t.vendas, ant == null ? null : ant.vendas},
                {"Prêmios", t.premios, ant == null ? null : ant.premios},
                {"Outras", t.outros, ant == null ? null : ant.outros}});
        r = secao(g, r, "Despesas", "fin-despesa", totDesp, ant == null ? null : ant.despesas(), new Object[][]{
                {"Salários", t.salarios, ant == null ? null : ant.salarios},
                {"Compra de jogadores", t.compras, ant == null ? null : ant.compras},
                {"Juros", t.juros, ant == null ? null : ant.juros},
                {"Estádio e manutenção", t.obras, ant == null ? null : ant.obras}});
        long res = totRec - totDesp;
        Long resAnt = ant == null ? null : ant.receitas() - ant.despesas();
        Label l = Ui.rotulo("Resultado", "fin-total-nome");
        Label v = Ui.rotulo((res >= 0 ? "+" : "") + Textos.dinheiro(res), "fin-total", res >= 0 ? "positivo" : "negativo");
        Label va = Ui.rotulo(resAnt == null ? "" : (resAnt >= 0 ? "+" : "") + Textos.dinheiro(resAnt), "fin-valor-ant");
        HBox fundo = Ui.linha(0);
        fundo.getStyleClass().add("fin-linha-total");
        g.add(fundo, 0, r, 5, 1);
        g.add(l, 0, r);
        g.add(v, 2, r);
        g.add(va, 3, r);
        // composição: receitas e despesas lado a lado numa barra empilhada
        VBox p = Ui.painelAcoes("Receitas e despesas", Ui.rotulo("temporada " + temporadaTxt(), "texto-suave"), composicao(t), g);
        p.setSpacing(14);
        return p;
    }

    private String temporadaTxt() {
        Clube c = hub.clube();
        Jogo j = hub.jogo();
        if ("europeu".equals(c.calendario)) {
            int a = j.semana() >= Balanceamento.SEMANA_VIRADA_EUROPEIA ? j.ano() : j.ano() - 1;
            return a + "/" + String.format("%02d", (a + 1) % 100);
        }
        return String.valueOf(j.ano());
    }

    private static Label cab(String s) {
        return Ui.rotulo(s.toUpperCase(Locale.ROOT), "fin-cab");
    }

    private static int secao(GridPane g, int r, String nome, String classe, long total, Long totalAnt, Object[][] itens) {
        Label n = Ui.rotulo(nome, "fin-secao");
        Label tot = Ui.rotulo(Textos.dinheiro(total), "fin-subtotal", classe + "-texto");
        g.add(n, 0, r);
        g.add(tot, 2, r);
        g.add(Ui.rotulo(totalAnt == null ? "" : Textos.dinheiro(totalAnt), "fin-valor-ant"), 3, r);
        g.add(variacao(total, totalAnt, classe.equals("fin-despesa")), 4, r);
        r++;
        long maior = 1;
        for (Object[] it : itens) maior = Math.max(maior, (Long) it[1]);
        for (Object[] it : itens) {
            long v = (Long) it[1];
            Long a = (Long) it[2];
            Region barra = new Region();
            barra.getStyleClass().addAll("fin-barra", classe);
            double fr = total <= 0 ? 0 : v / (double) total;
            Region trilho = new Region();
            trilho.getStyleClass().add("fin-trilho");
            javafx.scene.layout.StackPane sp = new javafx.scene.layout.StackPane(trilho, barra);
            sp.setAlignment(Pos.CENTER_LEFT);
            barra.maxWidthProperty().bind(sp.widthProperty().multiply(fr));
            barra.prefWidthProperty().bind(sp.widthProperty().multiply(fr));
            barra.setMaxHeight(8);
            barra.setMinHeight(8);
            trilho.setMaxHeight(8);
            trilho.setMinHeight(8);
            sp.setMinWidth(60);
            Label pct = Ui.rotulo(total <= 0 ? "" : Math.round(fr * 100) + "%", "fin-pct");
            HBox bar = new HBox(8, sp, pct);
            bar.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(sp, Priority.ALWAYS);
            pct.setMinWidth(34);
            g.add(Ui.rotulo(String.valueOf(it[0]), "fin-item"), 0, r);
            g.add(bar, 1, r);
            g.add(Ui.rotulo(Textos.dinheiro(v), "fin-valor"), 2, r);
            g.add(Ui.rotulo(a == null ? "" : Textos.dinheiro(a), "fin-valor-ant"), 3, r);
            g.add(variacao(v, a, classe.equals("fin-despesa")), 4, r);
            r++;
        }
        g.add(Ui.separadorH(), 0, r++, 5, 1);
        return r;
    }

    private static Label variacao(long v, Long ant, boolean despesa) {
        if (ant == null || ant == 0) return Ui.rotulo("", "fin-var");
        double d = (v - ant) / (double) Math.abs(ant);
        String s = (d >= 0 ? "+" : "") + Math.round(d * 100) + "%";
        boolean bom = despesa ? d <= 0 : d >= 0;
        return Ui.rotulo(s, "fin-var", Math.abs(d) < 0.005 ? "texto-suave" : bom ? "positivo" : "negativo");
    }

    /** Duas barras empilhadas (receitas e despesas) com a composição por categoria. */
    private static Node composicao(FinancasClube t) {
        long maior = Math.max(1, Math.max(t.receitas(), t.despesas()));
        VBox v = new VBox(6,
                pilha("Receitas", maior, new long[]{t.ingressos, t.tvPatrocinio, t.vendas, t.premios, t.outros}, "fin-r", t.receitas()),
                pilha("Despesas", maior, new long[]{t.salarios, t.compras, t.juros, t.obras}, "fin-d", t.despesas()));
        return v;
    }

    private static Node pilha(String nome, long maior, long[] partes, String prefixo, long total) {
        HBox barra = new HBox();
        barra.getStyleClass().add("fin-pilha");
        javafx.scene.layout.StackPane caixa = new javafx.scene.layout.StackPane(barra);
        caixa.setAlignment(Pos.CENTER_LEFT);
        double escala = total / (double) maior;
        barra.maxWidthProperty().bind(caixa.widthProperty().multiply(escala));
        barra.prefWidthProperty().bind(caixa.widthProperty().multiply(escala));
        for (int i = 0; i < partes.length; i++) {
            if (partes[i] <= 0) continue;
            Region r = new Region();
            r.getStyleClass().add(prefixo + i);
            double fr = partes[i] / (double) Math.max(1, total);
            r.prefWidthProperty().bind(barra.widthProperty().multiply(fr));
            r.setMinWidth(0);
            r.setPrefHeight(14);
            barra.getChildren().add(r);
        }
        HBox.setHgrow(caixa, Priority.ALWAYS);
        Label n = Ui.rotulo(nome, "fin-item");
        n.setMinWidth(70);
        Label tot = Ui.rotulo(Textos.dinheiro(total), "fin-valor");
        tot.setMinWidth(110);
        tot.setAlignment(Pos.CENTER_RIGHT);
        HBox h = new HBox(10, n, caixa, tot);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    // ------------------------------------------------------------------ orçamento e projeção
    /** Caixa projetado no fim de cada mês até o fim da temporada. */
    private record Projecao(List<int[]> semanas, List<Long> caixa, long tvAReceber, long salariosAPagar, long bilheteriaPrevista, long fim) {}

    private Projecao projetar(Jogo j, Clube c, Jogo.ResumoFinanceiro f) {
        int agora = Calendario.absoluta(j.ano(), j.semana());
        int ini = inicioTemporada(c, j), fim = fimTemporada(c, j);
        int passadas = Math.max(1, agora - ini + 1);
        double bilhetePorSemana = f.temporada().ingressos / (double) passadas;
        long custeio = Financas.custeioMensal(c, j.ano());
        long caixa = c.dinheiro, tvPend = c.tvPendente, divida = c.divida;
        int parcelas = c.tvParcelas, parcelasDivida = Financas.parcelasRestantes(c);
        long tvRec = 0, sal = 0, bil = 0;
        List<int[]> sem = new ArrayList<>();
        List<Long> vals = new ArrayList<>();
        for (int a = agora + 1; a <= fim; a++) {
            long b = Math.round(bilhetePorSemana);
            caixa += b;
            bil += b;
            if (Calendario.semana(a) % 4 == 0) {
                if (parcelas > 0) {
                    long p = tvPend / parcelas;
                    tvPend -= p;
                    parcelas--;
                    caixa += p;
                    tvRec += p;
                }
                caixa -= f.folhaMensal() + custeio + Math.round(divida * Financas.JUROS_MES);
                if (divida > 0 && parcelasDivida > 0) { // parcela do empréstimo (amortização)
                    long amort = parcelasDivida == 1 ? divida : Math.min(divida, (divida + parcelasDivida - 1) / parcelasDivida);
                    divida -= amort;
                    caixa -= amort;
                    parcelasDivida--;
                }
                sal += f.folhaMensal();
                sem.add(new int[]{a});
                vals.add(caixa);
            }
        }
        if (sem.isEmpty() || sem.get(sem.size() - 1)[0] != fim) {
            sem.add(new int[]{fim});
            vals.add(caixa);
        }
        return new Projecao(sem, vals, tvRec, sal, bil, caixa);
    }

    private VBox orcamento(Jogo j, Clube c, Jogo.ResumoFinanceiro f, Projecao pj) {
        long orc = orcamentoFolha(c, j.ano());
        VBox folha = Ui.painel("Orçamento salarial", barraUso("Folha mensal", f.folhaMensal(), orc),
                grade(new String[][]{{"Folha anual", Textos.dinheiro(f.folhaMensal() * 12)},
                        {"Margem por mês", Textos.dinheiro(orc - f.folhaMensal())}}, orc - f.folhaMensal() < 0 ? 1 : -1));
        long fim = pj.fim();
        Label fimL = Ui.rotulo(Textos.dinheiro(fim), "fin-proj-valor", fim < 0 ? "negativo" : "positivo");
        VBox proj = Ui.painel("Projeção até o fim da temporada",
                Ui.linha(10, Ui.rotulo("Caixa previsto", "texto-medio"), Ui.espaco(), fimL),
                grade(new String[][]{{"TV e patrocínio a receber", "+" + Textos.dinheiro(pj.tvAReceber())},
                        {"Bilheteria prevista", "+" + Textos.dinheiro(pj.bilheteriaPrevista())},
                        {"Salários a pagar", "−" + Textos.dinheiro(pj.salariosAPagar())}}, -1));
        VBox v = new VBox(16, folha, proj);
        return v;
    }

    /** Pares rótulo/valor alinhados (valor à direita). {@code linhaNegativa} pinta uma linha de vermelho. */
    private static GridPane grade(String[][] linhas, int linhaNegativa) {
        GridPane g = new GridPane();
        g.setVgap(6);
        ColumnConstraints a = new ColumnConstraints();
        a.setHgrow(Priority.ALWAYS);
        ColumnConstraints b = new ColumnConstraints();
        b.setHalignment(HPos.RIGHT);
        g.getColumnConstraints().addAll(a, b);
        for (int i = 0; i < linhas.length; i++) {
            Label v = Ui.rotulo(linhas[i][1], "fin-valor");
            if (i == linhaNegativa) v.getStyleClass().add("negativo");
            g.addRow(i, Ui.rotulo(linhas[i][0], "texto-suave"), v);
        }
        return g;
    }

    // ------------------------------------------------------------------ evolução do caixa
    private VBox evolucao(Jogo j, Clube c, Jogo.ResumoFinanceiro f, Projecao pj) {
        int ini = inicioTemporada(c, j);
        int agora = Calendario.absoluta(j.ano(), j.semana());
        TreeMap<Integer, Long> reg = new TreeMap<>(CAIXA.getOrDefault(c.id, new TreeMap<>()).subMap(ini, true, agora, true));
        reg.put(ini, f.temporada().saldoInicio);
        reg.put(agora, c.dinheiro);
        // um ponto por mês no passado (o último registro de cada mês)
        Map<Integer, Integer> ultimoDoMes = new HashMap<>();
        for (int s : reg.keySet()) {
            var d = Calendario.inicio(s);
            ultimoDoMes.put(d.getYear() * 100 + d.getMonthValue(), s);
        }
        TreeMap<Integer, Long> real = new TreeMap<>();
        real.put(ini, reg.get(ini));
        for (int s : ultimoDoMes.values()) real.put(s, reg.get(s));
        real.put(agora, c.dinheiro);
        TreeMap<Integer, Long> proj = new TreeMap<>();
        proj.put(agora, c.dinheiro);
        for (int i = 0; i < pj.semanas().size(); i++) proj.put(pj.semanas().get(i)[0], pj.caixa().get(i));
        Grafico g = new Grafico(ini, fimTemporada(c, j), real, proj);
        HBox legenda = Ui.linha(16, item("fin-leg-real", "Caixa"), item("fin-leg-proj", "Projeção"));
        VBox p = Ui.painelAcoes("Evolução do caixa", legenda, g);
        return p;
    }

    private static HBox item(String classe, String nome) {
        Region r = new Region();
        r.getStyleClass().add(classe);
        r.setMinSize(18, 3);
        r.setMaxSize(18, 3);
        return Ui.linha(6, r, Ui.rotulo(nome, "texto-pequeno"));
    }

    /** Gráfico de linha simples (Canvas): caixa real até hoje e a projeção tracejada. */
    private static final class Grafico extends Pane {
        private final Canvas cv = new Canvas();
        private final int ini, fim;
        private final TreeMap<Integer, Long> real, proj;

        Grafico(int ini, int fim, TreeMap<Integer, Long> real, TreeMap<Integer, Long> proj) {
            this.ini = ini;
            this.fim = Math.max(ini + 1, fim);
            this.real = real;
            this.proj = proj;
            getChildren().add(cv);
            setPrefHeight(230);
            setMinHeight(200);
            widthProperty().addListener((o, a, b) -> desenhar());
            heightProperty().addListener((o, a, b) -> desenhar());
        }

        @Override
        protected void layoutChildren() {
            cv.setWidth(getWidth());
            cv.setHeight(getHeight());
            desenhar();
        }

        private void desenhar() {
            double w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            GraphicsContext g = cv.getGraphicsContext2D();
            g.clearRect(0, 0, w, h);
            double esq = 74, dir = 16, topo = 12, base = 28;
            long min = 0, max = 1;
            for (long v : real.values()) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            for (long v : proj.values()) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            double faixa = Math.max(1, max - min);
            min -= (long) (faixa * 0.08);
            max += (long) (faixa * 0.08);
            final long mn = min, mx = max;
            java.util.function.DoubleUnaryOperator px = s -> esq + (s - ini) / (double) (fim - ini) * (w - esq - dir);
            java.util.function.DoubleUnaryOperator py = v -> topo + (1 - (v - mn) / (double) (mx - mn)) * (h - topo - base);
            Font f = Font.font("Segoe UI", 11);
            g.setFont(f);
            // grade horizontal
            g.setLineWidth(1);
            for (int i = 0; i <= 4; i++) {
                double v = mn + (mx - mn) * i / 4.0;
                double y = Math.round(py.applyAsDouble(v)) + 0.5;
                g.setStroke(Color.web("#353a43"));
                g.strokeLine(esq, y, w - dir, y);
                g.setFill(Color.web("#8b95a2"));
                g.setTextAlign(TextAlignment.RIGHT);
                g.fillText(curto((long) v), esq - 8, y + 4);
            }
            // zero
            if (mn < 0 && mx > 0) {
                double y = Math.round(py.applyAsDouble(0)) + 0.5;
                g.setStroke(Color.web("#e5534b", 0.7));
                g.setLineDashes(3, 3);
                g.strokeLine(esq, y, w - dir, y);
                g.setLineDashes();
            }
            // meses no eixo x
            g.setTextAlign(TextAlignment.CENTER);
            g.setFill(Color.web("#8b95a2"));
            int mesAntes = -1;
            for (int s = ini; s <= fim; s++) {
                int mes = Calendario.inicio(s).getMonthValue();
                if (mes == mesAntes) continue;
                mesAntes = mes;
                double x = px.applyAsDouble(s);
                g.fillText(Textos.mes(Calendario.semana(s)), x + 10, h - 8);
                g.setStroke(Color.web("#2f343c"));
                g.strokeLine(Math.round(x) + 0.5, topo, Math.round(x) + 0.5, h - base);
            }
            // projeção
            g.setStroke(Color.web("#b7bfca"));
            g.setLineWidth(2);
            g.setLineDashes(6, 5);
            linha(g, proj, px, py);
            g.setLineDashes();
            // área e linha do real
            if (real.size() >= 2) {
                g.setFill(Color.web("#3ac47f", 0.14));
                g.beginPath();
                var first = real.firstEntry();
                g.moveTo(px.applyAsDouble(first.getKey()), py.applyAsDouble(Math.max(mn, 0)));
                for (var e : real.entrySet()) g.lineTo(px.applyAsDouble(e.getKey()), py.applyAsDouble(e.getValue()));
                g.lineTo(px.applyAsDouble(real.lastKey()), py.applyAsDouble(Math.max(mn, 0)));
                g.closePath();
                g.fill();
            }
            g.setStroke(Color.web("#3ac47f"));
            g.setLineWidth(2.5);
            linha(g, real, px, py);
            for (var e : real.entrySet()) ponto(g, px.applyAsDouble(e.getKey()), py.applyAsDouble(e.getValue()), "#3ac47f");
            if (!proj.isEmpty()) {
                var u = proj.lastEntry();
                double x = px.applyAsDouble(u.getKey()), y = py.applyAsDouble(u.getValue());
                ponto(g, x, y, "#b7bfca");
                g.setFill(Color.web(u.getValue() < 0 ? "#ff9288" : "#eef1f5"));
                g.setTextAlign(TextAlignment.RIGHT);
                g.fillText(curto(u.getValue()), x - 6, y - 8);
            }
            var hoje = real.lastEntry();
            if (hoje != null) {
                g.setFill(Color.web("#eef1f5"));
                g.setTextAlign(TextAlignment.CENTER);
                g.fillText("hoje", px.applyAsDouble(hoje.getKey()), Math.max(topo + 10, py.applyAsDouble(hoje.getValue()) - 10));
            }
        }

        private static void linha(GraphicsContext g, TreeMap<Integer, Long> pts, java.util.function.DoubleUnaryOperator px, java.util.function.DoubleUnaryOperator py) {
            if (pts.size() < 2) return;
            g.beginPath();
            boolean primeiro = true;
            for (var e : pts.entrySet()) {
                double x = px.applyAsDouble(e.getKey()), y = py.applyAsDouble(e.getValue());
                if (primeiro) g.moveTo(x, y);
                else g.lineTo(x, y);
                primeiro = false;
            }
            g.stroke();
        }

        private static void ponto(GraphicsContext g, double x, double y, String cor) {
            g.setFill(Color.web("#262a31"));
            g.fillOval(x - 4.5, y - 4.5, 9, 9);
            g.setStroke(Color.web(cor));
            g.setLineWidth(2);
            g.strokeOval(x - 4.5, y - 4.5, 9, 9);
        }

        private static String curto(long v) {
            double a = Math.abs(v);
            String s = a >= 1e9 ? String.format(Locale.ROOT, "%.1f bi", a / 1e9) : a >= 1e6 ? String.format(Locale.ROOT, "%.1f mi", a / 1e6)
                    : a >= 1e3 ? String.format(Locale.ROOT, "%.0f mil", a / 1e3) : String.valueOf((long) a);
            return (v < 0 ? "−" : "") + "R$ " + s.replace('.', ',');
        }
    }

    // ------------------------------------------------------------------ estádio e banco
    private VBox estadio(Jogo j, Clube c) {
        VBox v = Ui.painel("Estádio");
        v.getChildren().add(grade(new String[][]{{"Estádio", c.estadio == null ? "—" : c.estadio}, {"Capacidade", Textos.milhar(c.capacidade) + " lugares"},
                {"Público estimado", Textos.milhar(j.publicoEstimado())}}, -1));
        CampoDinheiro cp = new CampoDinheiro(j.precoIngresso(), 0, 0);
        TextField preco = cp.campo();
        long sugerido = j.precoIngressoSugerido();
        HBox ingresso = Ui.linha(8, rotuloCampo("Ingresso"), cp, Ui.espaco(),
                Ui.botao("Aplicar", () -> {
                    j.definirPrecoIngresso(PainelElenco.numero(preco.getText()));
                    hub.atualizar();
                }),
                Ui.botao("Sugerido (" + Textos.dinheiro(sugerido) + ")", () -> {
                    j.definirPrecoIngresso(0);
                    hub.atualizar();
                }, "fantasma"),
                Ui.info("Acima do sugerido vem menos gente; abaixo, o estádio enche mas cada ingresso rende menos."));
        v.getChildren().addAll(Ui.separadorH(), ingresso, Ui.separadorH());
        if (c.obraFim > 0) {
            v.getChildren().add(Ui.aviso("Obra em andamento: +" + Textos.milhar(c.obraLugares) + " lugares, término em "
                    + Textos.dataLonga(Calendario.inicio(c.obraFim)) + ".", "aviso"));
        } else {
            TextField lugares = new TextField("5000");
            lugares.setPrefWidth(90);
            Label custo = Ui.rotulo("", "texto-suave");
            Runnable calc = () -> {
                int l = (int) Math.min(200_000, PainelElenco.numero(lugares.getText()));
                custo.setText(l <= 0 ? "" : "Custo " + Textos.dinheiro(Financas.custoObra(l, j.ano())) + "  ·  " + Financas.semanasObra(l) + " semanas de obra");
            };
            lugares.textProperty().addListener((o, a, b) -> calc.run());
            calc.run();
            v.getChildren().addAll(Ui.linha(8, rotuloCampo("Ampliar"), lugares, Ui.rotulo("lugares", "texto-medio"), Ui.espaco(),
                    Ui.botao("Encomendar obra", () -> {
                        Jogo.ResultadoNegociacao r = j.ampliarEstadio((int) Math.min(200_000, PainelElenco.numero(lugares.getText())));
                        Ui.informar(r.aceito() ? "Obra encomendada" : "Obra não iniciada", r.mensagem());
                        hub.atualizar();
                    }, "primario")), custo);
        }
        return v;
    }

    private static Label rotuloCampo(String s) {
        Label l = Ui.rotulo(s, "texto-medio");
        l.setMinWidth(70);
        return l;
    }

    /**
     * Banco (requisito: "deixe claro quanto deve, quando pagar, não gaste muito dinheiro,
     * escolha quanto pagar"): total devido, a próxima parcela com data, valor e juros, o
     * cronograma das parcelas, e dois campos separados: pedir empréstimo e pagar (amortizar) um
     * valor escolhido, limitado ao caixa e ao total devido, com confirmação mostrando o caixa depois.
     */
    private VBox banco(Jogo j, Jogo.ResumoFinanceiro f) {
        Jogo.SituacaoDivida d = j.situacaoDivida();
        String jurosTxt = String.format(Locale.ROOT, "%.1f%% ao mês", d.jurosMes() * 100).replace('.', ',');
        VBox v = Ui.painelAcoes("Banco", Ui.info("O empréstimo é pago em " + Financas.PRAZO_EMPRESTIMO_MESES
                        + " parcelas mensais, descontadas no fechamento do mês (junto com a folha), mais juros de " + jurosTxt
                        + ". Pagar antes diminui as próximas parcelas e os juros. Pedir mais renegocia o prazo."),
                barraUso("Crédito usado", d.total(), d.limite()));
        if (d.total() > 0 && d.proxima() != null) {
            Financas.Parcela p = d.proxima();
            v.getChildren().add(grade(new String[][]{
                    {"Total devido", Textos.dinheiro(d.total())},
                    {"Próxima parcela", Textos.dataCurta(Calendario.inicio(p.semana() + 1).minusDays(1)) + "  ·  " + Textos.dinheiro(p.total())},
                    {"   amortização + juros", Textos.dinheiro(p.amortizacao()) + " + " + Textos.dinheiro(p.juros())},
                    {"Parcelas que faltam", d.parcelasRestantes() + " de " + Financas.PRAZO_EMPRESTIMO_MESES},
                    {"Juros", jurosTxt + "  ·  " + Textos.dinheiro(d.jurosTotais()) + " até quitar"}}, 0));
            v.getChildren().add(cronograma(d));
        } else {
            v.getChildren().add(grade(new String[][]{{"Total devido", "nenhuma dívida"}, {"Juros", jurosTxt}}, -1));
        }
        v.getChildren().add(grade(new String[][]{{"Limite de crédito", Textos.dinheiro(d.limite())},
                {"Disponível para pedir", Textos.dinheiro(d.disponivel())}}, -1));
        v.getChildren().add(Ui.separadorH());

        // pagar (amortizar): o técnico escolhe o valor; o padrão é a próxima parcela (o que couber no caixa)
        if (d.total() > 0) {
            long max = d.maximoAmortizar();
            long sugestao = d.proxima() == null ? 0 : Math.min(d.proxima().amortizacao(), max);
            CampoDinheiro cp = new CampoDinheiro(sugestao, 0, max);
            HBox atalhos = Ui.linha(4);
            if (d.proxima() != null) atalhos.getChildren().add(atalho("1 parcela", () -> cp.definir(Math.min(d.proxima().amortizacao(), max))));
            atalhos.getChildren().addAll(atalho("10% do caixa", () -> cp.definir(Math.min(max, Math.max(0, f.saldo()) / 10))),
                    atalho("Metade da dívida", () -> cp.definir(Math.min(max, d.total() / 2))),
                    atalho("Quitar", () -> cp.definir(max)));
            v.getChildren().addAll(Ui.rotulo("Pagar agora (amortizar)", "texto-forte"),
                    Ui.linha(8, rotuloCampo("Valor"), cp, Ui.espaco(), Ui.botao("Pagar", () -> pagar(j, f, d, cp.valor()), "primario")),
                    atalhos,
                    Ui.rotulo("Máximo agora: " + Textos.dinheiro(max) + (max < d.total() ? " (o que tem em caixa)" : " (quita a dívida)"), "texto-pequeno"));
            v.getChildren().add(Ui.separadorH());
        }

        // pedir empréstimo
        CampoDinheiro cb = new CampoDinheiro(Math.max(0, d.disponivel() / 10), 0, d.disponivel());
        Label simulacao = Ui.rotulo("", "texto-pequeno");
        Runnable simular = () -> {
            long q = Math.min(cb.valor(), d.disponivel());
            long nova = d.total() + q;
            int n = Financas.PRAZO_EMPRESTIMO_MESES;
            simulacao.setText(q <= 0 ? "" : "Dívida fica " + Textos.dinheiro(nova) + " em " + n + " parcelas de ~"
                    + Textos.dinheiro((nova + n - 1) / n) + " + juros (" + Textos.dinheiro(Math.round(nova * d.jurosMes())) + " no 1º mês).");
        };
        cb.campo().textProperty().addListener((o, a, b) -> simular.run());
        simular.run();
        v.getChildren().addAll(Ui.rotulo("Pedir empréstimo", "texto-forte"),
                Ui.linha(8, rotuloCampo("Valor"), cb, Ui.espaco(), Ui.botao("Pedir empréstimo", () -> {
                    long q = Math.min(cb.valor(), d.disponivel());
                    if (q <= 0) {
                        Ui.informar("Empréstimo", "O banco não libera mais crédito.");
                        return;
                    }
                    if (!Ui.confirmar("Empréstimo", "Pedir " + Textos.dinheiro(q) + " ao banco?\n" + simulacao.getText())) return;
                    long v2 = j.pedirEmprestimo(q);
                    Ui.informar("Empréstimo", v2 > 0 ? "O banco liberou " + Textos.dinheiro(v2) + "." : "O banco não liberou mais crédito.");
                    hub.atualizar();
                })), simulacao);
        return v;
    }

    private static javafx.scene.control.Button atalho(String txt, Runnable r) {
        javafx.scene.control.Button b = Ui.botao(txt, r, "chip");
        b.setFocusTraversable(false);
        return b;
    }

    /** Cronograma das parcelas (data, amortização, juros, total e saldo depois). */
    private static GridPane cronograma(Jogo.SituacaoDivida d) {
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(3);
        String[] cab = {"Vence", "Amortização", "Juros", "Parcela", "Saldo depois"};
        for (int i = 0; i < cab.length; i++) g.add(Ui.rotulo(cab[i], "texto-suave"), i, 0);
        int lin = 1;
        for (Financas.Parcela p : d.cronograma()) {
            g.add(Ui.rotulo(Textos.dataCurta(Calendario.inicio(p.semana() + 1).minusDays(1)), "texto-pequeno"), 0, lin);
            g.add(Ui.rotulo(Textos.dinheiro(p.amortizacao()), "texto-pequeno"), 1, lin);
            g.add(Ui.rotulo(Textos.dinheiro(p.juros()), "texto-pequeno"), 2, lin);
            g.add(Ui.rotulo(Textos.dinheiro(p.total()), lin == 1 ? "texto-forte" : "texto-pequeno"), 3, lin);
            g.add(Ui.rotulo(Textos.dinheiro(p.saldoDepois()), "texto-pequeno"), 4, lin);
            lin++;
        }
        g.getColumnConstraints().add(new ColumnConstraints());
        for (int c = 1; c < cab.length; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHalignment(HPos.RIGHT);
            g.getColumnConstraints().add(cc);
        }
        return g;
    }

    /** Pagamento extra com confirmação: mostra o caixa e a dívida depois e avisa se o caixa fica baixo. */
    private void pagar(Jogo j, Jogo.ResumoFinanceiro f, Jogo.SituacaoDivida d, long pedido) {
        long valor = Math.min(pedido, d.maximoAmortizar());
        if (valor <= 0) {
            Ui.informar("Pagar dívida", d.maximoAmortizar() <= 0 ? "Não há caixa para pagar agora." : "Escolha um valor maior que zero.");
            return;
        }
        long caixaDepois = f.saldo() - valor, dividaDepois = d.total() - valor;
        String aviso = caixaDepois < f.folhaMensal()
                ? "\nAtenção: o caixa fica abaixo de uma folha salarial (" + Textos.dinheiro(f.folhaMensal()) + ")." : "";
        String txt = "Pagar " + Textos.dinheiro(valor) + (pedido > valor ? " (o máximo possível agora)" : "") + "?\n"
                + "Caixa: " + Textos.dinheiro(f.saldo()) + " → " + Textos.dinheiro(caixaDepois) + "\n"
                + "Dívida: " + Textos.dinheiro(d.total()) + " → " + (dividaDepois <= 0 ? "quitada" : Textos.dinheiro(dividaDepois)) + aviso;
        if (!Ui.confirmar("Pagar dívida", txt)) return;
        long v2 = j.pagarEmprestimo(valor);
        Ui.informar("Pagar dívida", v2 > 0 ? "Você pagou " + Textos.dinheiro(v2) + " da dívida." : "Nada foi pago.");
        hub.atualizar();
    }
}
