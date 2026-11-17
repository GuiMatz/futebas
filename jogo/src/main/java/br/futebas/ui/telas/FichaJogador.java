package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.dados.Db;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Passagem;
import br.futebas.mundo.Posicao;
import br.futebas.ui.App;
import br.futebas.ui.comp.Foto;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.stream.Collectors;

/** Ficha do jogador em cartão: foto, força em destaque, atributos, contrato, ações e carreira. */
final class FichaJogador {
    private FichaJogador() {}

    static VBox construir(Jogo j, Jogador x, Node acoes) {
        Mundo m = j.mundo();
        Db.Jogador ficha = j.fichaReal(x.id);
        VBox v = new VBox(14);
        v.getStyleClass().add("painel");

        // cabeça: foto + identidade + força
        Label nome = Ui.rotulo(x.nomeExibicao(), "titulo-tela");
        nome.setStyle("-fx-font-size: 19px;");
        nome.setWrapText(true);
        HBox selos = Ui.linha(6, Ui.pos(x.posicao));
        if (x.topMundial) selos.getChildren().add(Ui.etiqueta("Top mundial", "escura"));
        else if (x.estrela) selos.getChildren().add(Ui.etiqueta("Estrela", "ambar"));
        if (x.aVenda) selos.getChildren().add(Ui.etiqueta("À venda", "azul"));
        if (x.vaiAposentar) selos.getChildren().add(Ui.etiqueta("Aposenta no fim da temporada", "ambar"));
        VBox id = new VBox(4, nome);
        if (x.nome != null && !x.nome.equals(x.nomeExibicao())) id.getChildren().add(Ui.paragrafo(x.nome, "texto-suave"));
        id.getChildren().add(selos);
        Clube c = m.clube(x.clubeId);
        HBox onde = Ui.linha(6);
        if (c != null) onde.getChildren().add(Ui.escudo(c.id, 18));
        onde.getChildren().add(Ui.rotulo(Textos.situacao(m, x), "texto-medio"));
        HBox pais = Ui.linha(6, Imagens.bandeira(x.pais, 12), Ui.rotulo(Textos.pais(x.pais) + " · " + x.idade(j.ano()) + " anos", "texto-medio"));
        id.getChildren().addAll(onde, pais);
        HBox.setHgrow(id, Priority.ALWAYS);
        VBox forca = new VBox(-4, Ui.forcaGrande(x.forcaExibida(), "numero-enorme"), Ui.rotulo("força", "texto-pequeno"));
        forca.setAlignment(Pos.TOP_CENTER);
        HBox topo = new HBox(14, Foto.de(j.foto(x.id), x.dbId, 96, 116), id, forca);
        topo.setAlignment(Pos.TOP_LEFT);
        v.getChildren().add(topo);

        // números rápidos
        int cpe = j.cpe(x.id);
        HBox nums = Ui.linha(18, Ui.dado("Moral", x.moral + ""), Ui.dado("POT", x.idade(j.ano()) <= 23 ? String.valueOf(cpe) : "—"),
                Ui.dado("Temporada", x.jogosTemp + " j · " + x.golsTemp + " g"),
                Ui.dado("Nota", x.jogosTemp > 0 ? Textos.umaCasa(x.notaMediaTemp()) : "—"));
        v.getChildren().add(nums);
        if (acoes != null) v.getChildren().add(acoes);

        // atributos
        v.getChildren().addAll(Ui.tituloPainel("Atributos"), atributos(x), Ui.rotulo("Em negrito, os que mais pesam na posição.", "texto-pequeno"));

        // dados
        v.getChildren().addAll(Ui.tituloPainel("Dados"), dados(j, x, ficha, true));

        // carreira: a real até 2026 e a simulada depois
        GridPane gs = carreira(j, x);
        if (gs != null) v.getChildren().addAll(Ui.tituloPainel("Carreira"), gs);

        // fontes
        VBox fs = fontes(j, x);
        // fontes saíram da ficha (regra do jogo): ficam em Opções › Créditos
        return v;
    }

    // ------------------------------------------------------------------ seções
    static GridPane atributos(Jogador x) {
        GridPane at = new GridPane();
        at.setHgap(10);
        at.setVgap(5);
        double[] pesos = x.posicao.pesosAtributos();
        for (int i = 0; i < 7; i++) {
            int val = (int) Math.round(x.atributos[i]);
            ProgressBar b = new ProgressBar(val / 100.0);
            b.setPrefSize(160, 7);
            b.setMaxWidth(Double.MAX_VALUE);
            b.getStyleClass().addAll("attr", Ui.classeForca(val));
            Label n = Ui.rotulo(Posicao.ATRIBUTOS[i], pesos[i] >= 0.2 ? "texto-forte" : "texto-medio");
            n.setMinWidth(84);
            Label num = Ui.rotulo(String.valueOf(val), "numero");
            num.setMinWidth(24);
            at.addRow(i, n, b, num);
            GridPane.setHgrow(b, Priority.ALWAYS);
        }
        return at;
    }

    /** Dados pessoais; {@code comContrato} inclui contrato, salário, multa e valor. */
    static GridPane dados(Jogo j, Jogador x, Db.Jogador ficha, boolean comContrato) {
        GridPane info = new GridPane();
        info.setHgap(12);
        info.setVgap(4);
        int r = 0;
        String sec = x.posicoesSec.isEmpty() ? "" : " (também " + x.posicoesSec.stream().map(Enum::name).collect(Collectors.joining(", ")) + ")";
        r = par(info, r, "Posição", x.posicao.nome + sec);
        r = par(info, r, "Nascimento", ficha != null && ficha.nascimento() != null ? data(ficha.nascimento()) : String.valueOf(x.anoNasc));
        r = par(info, r, "Pé", Textos.pe(x.pe));
        if (ficha != null && ficha.altura() != null) r = par(info, r, "Altura", ficha.altura() + " cm");
        if (ficha != null && ficha.caracteristicas() != null && !ficha.caracteristicas().isEmpty())
            r = par(info, r, "Características", String.join(", ",
                    ficha.caracteristicas().stream().map(br.futebas.mundo.Caracteristica::nome).toList()));
        r = par(info, r, "Condição", Textos.condicao(x) + (x.lesao != null && x.lesaoSemanas > 0 ? " · " + x.lesao : ""));
        if (comContrato) r = contrato(j, x, info, r);
        par(info, r, "No seu mundo", x.jogosCarreira + " jogos · " + x.golsCarreira + " gols");
        return info;
    }

    private static int contrato(Jogo j, Jogador x, GridPane info, int r) {
        r = par(info, r, "Contrato até", Textos.contrato(x));
        if (x.contrato != null) {
            r = par(info, r, "Salário", Textos.dinheiro(x.contrato.salario) + " por mês");
            if (x.contrato.multa > 0) r = par(info, r, "Multa rescisória", Textos.dinheiro(x.contrato.multa));
        }
        if (!x.amador()) r = par(info, r, "Valor de mercado", Textos.dinheiro(j.valorDeMercado(x.id)));
        return r;
    }

    /** Carreira (real até 2026 + simulada), da mais recente à mais antiga; null se vazia. */
    static GridPane carreira(Jogo j, Jogador x) {
        List<Jogo.LinhaCarreira> car = List.of();
        try {
            car = j.carreiraDoJogador(x.id);
        } catch (RuntimeException e) {
            // sem camada histórica
        }
        if (car.isEmpty()) return null;
        GridPane gs = new GridPane();
        gs.setHgap(14);
        gs.setVgap(6);
        int k = 0;
        for (int i = car.size() - 1; i >= 0; i--) {
            Jogo.LinhaCarreira l = car.get(i);
            String anos = l.anoInicio() == l.anoFim() || l.anoFim() <= 0 ? String.valueOf(l.anoInicio()) : l.anoInicio() + "–" + l.anoFim();
            String nomeC = (l.clube() == null ? "—" : l.clube()) + (l.emprestimo() ? " (empr.)" : "");
            Label clube = l.clubeId() == null ? Ui.rotulo(nomeC) : Ui.nomeClube(l.clubeId(), nomeC);
            clube.setMinWidth(0);
            HBox cl = Ui.linha(6, l.clubeId() == null ? new javafx.scene.layout.Region() : Ui.escudo(l.clubeId(), 16), clube);
            String num = (l.jogos() == null ? "—" : l.jogos() + " j") + " · " + (l.gols() == null ? "—" : l.gols() + " g");
            gs.addRow(k++, Ui.rotulo(anos, "manchete-data"), cl, Ui.rotulo(num, "texto-suave"));
            GridPane.setHgrow(cl, Priority.ALWAYS);
        }
        return gs;
    }

    /** Fontes do jogador real; null se não há. */
    static VBox fontes(Jogo j, Jogador x) {
        if (x.dbId == null) return null;
        List<Db.Fonte> fontes = j.fontesDoJogador(x.id);
        if (fontes.isEmpty()) return null;
        VBox fs = new VBox(2);
        for (Db.Fonte f : fontes) {
            Hyperlink h = new Hyperlink(f.titulo() != null && !f.titulo().isBlank() ? f.titulo() : f.url());
            h.setWrapText(true);
            h.setOnAction(e -> {
                try {
                    App.atual().getHostServices().showDocument(f.url());
                } catch (RuntimeException ex) {
                    App.atual().aviso(f.url());
                }
            });
            fs.getChildren().add(h);
        }
        return fs;
    }

    // ------------------------------------------------------------------ página inteira
    /** Página do jogador na largura toda: faixa no topo e abas (Visão geral, Carreira, Fontes). */
    static Node pagina(Jogo j, Jogador x, Runnable voltar) {
        Mundo m = j.mundo();
        Db.Jogador ficha = j.fichaReal(x.id);
        Clube c = m.clube(x.clubeId);
        HBox selos = Ui.linha(6, Ui.pos(x.posicao));
        if (x.topMundial) selos.getChildren().add(Ui.etiqueta("Top mundial", "escura"));
        else if (x.estrela) selos.getChildren().add(Ui.etiqueta("Estrela", "ambar"));
        if (x.aVenda) selos.getChildren().add(Ui.etiqueta("À venda", "azul"));
        if (x.vaiAposentar) selos.getChildren().add(Ui.etiqueta("Aposenta no fim da temporada", "ambar"));
        HBox onde = Ui.linha(8);
        if (c != null) onde.getChildren().addAll(Ui.escudo(c.id, 18), Ui.nomeClube(c.id, c.nome, "cab-meta"));
        else onde.getChildren().add(Ui.rotulo(Textos.situacao(m, x), "cab-meta"));
        onde.getChildren().addAll(Ui.rotulo("·", "cab-meta"), Imagens.bandeira(x.pais, 12),
                Ui.rotulo(Textos.pais(x.pais) + "  ·  " + x.idade(j.ano()) + " anos", "cab-meta"));
        VBox sub = new VBox(6, onde, selos);
        if (x.nome != null && !x.nome.equals(x.nomeExibicao())) sub.getChildren().add(0, Ui.rotulo(x.nome, "cab-meta"));
        int cpe = j.cpe(x.id);
        VBox forca = new VBox(-6, Ui.forcaGrande(x.forcaExibida(), "numero-enorme"), Ui.rotulo("força", "cab-rotulo"));
        forca.setAlignment(Pos.CENTER);
        VBox cab = Pagina.cabecalho(Foto.de(j.foto(x.id), x.dbId, 96, 116), x.nomeExibicao(), sub, c == null ? null : c.cor1, c == null ? null : c.cor2, voltar,
                forca,
                Pagina.bloco(x.amador() ? "—" : Textos.dinheiro(j.valorDeMercado(x.id)), "Valor"),
                Pagina.bloco(Textos.contrato(x), "Contrato"),
                Pagina.bloco(String.valueOf(x.moral), "Moral"),
                x.idade(j.ano()) <= 23 ? Pagina.bloco(String.valueOf(cpe), "POT") : null);

        // visão geral: atributos | dados | temporada + contrato
        VBox colA = Ui.painel("Atributos", atributos(x), Ui.rotulo("Em negrito, os que mais pesam na posição.", "texto-pequeno"));
        VBox colB = Ui.painel("Dados", dados(j, x, ficha, false));
        GridPane ct = new GridPane();
        ct.setHgap(12);
        ct.setVgap(4);
        contrato(j, x, ct, 0);
        HBox temp = Ui.linha(24, Ui.dado("Jogos", String.valueOf(x.jogosTemp)), Ui.dado("Gols", String.valueOf(x.golsTemp)),
                Ui.dado("Assist.", String.valueOf(x.assistTemp)), Ui.dado("Nota", x.jogosTemp > 0 ? Textos.umaCasa(x.notaMediaTemp()) : "—"),
                Ui.dado("Minutos", String.valueOf(x.minutosTemp)));
        VBox colC = new VBox(16, Ui.painel("Temporada", temp), Ui.painel("Contrato", ct));
        if (x.jogosSelecao > 0) colC.getChildren().add(Ui.painel("Seleção", Ui.linha(24, Ui.linha(8, Ui.escudo("sel-" + x.pais, 22),
                Ui.nomeClube("sel-" + x.pais, m.nomeParticipante("sel-" + x.pais), "texto-forte")),
                Ui.dado("Jogos", String.valueOf(x.jogosSelecao)), Ui.dado("Gols", String.valueOf(x.golsSelecao)))));
        HBox geral = Pagina.colunas(18, colA, colB, colC);
        colA.setPrefWidth(34);
        colB.setPrefWidth(33);
        colC.setPrefWidth(33);

        List<javafx.scene.control.Tab> tabs = new java.util.ArrayList<>();
        tabs.add(Pagina.aba("Visão geral", geral));
        GridPane car = carreira(j, x);
        VBox carP = Ui.painel("Carreira", car == null ? Ui.rotulo("Sem passagens registradas.", "texto-suave") : car,
                Ui.separadorH(), Ui.linha(24, Ui.dado("Jogos no seu mundo", String.valueOf(x.jogosCarreira)), Ui.dado("Gols no seu mundo", String.valueOf(x.golsCarreira))));
        tabs.add(Pagina.aba("Carreira", carP));
        VBox fs = fontes(j, x);
        // sem aba Fontes (regra do jogo): os créditos ficam em Opções › Créditos
        return Pagina.montar(cab, Pagina.abas("jogador", tabs.toArray(new javafx.scene.control.Tab[0])));
    }


    /**
     * Cartão de um atleta da Kings League, no mesmo desenho do cartão do jogador: atleta do banco
     * usa a ficha completa (com a vaga na Kings nos selos); atleta só da fonte (elenco real, presidente)
     * mostra foto da Kings (ou silhueta), posição, time, força, gols e de onde veio.
     */
    static VBox kings(Jogo j, br.futebas.kings.LigaKings.TimeKings t, br.futebas.kings.LigaKings.AtletaKings a, String vaga, Node acoes) {
        Mundo m = j.mundo();
        Jogador x = a.jogadorId == null ? null : m.jogador(a.jogadorId);
        if (x != null) {
            VBox v = construir(j, x, acoes);
            v.getChildren().add(1, Ui.linha(8, Ui.escudo(t.id, 18), Ui.rotulo(t.nome, "texto-medio"), Ui.etiqueta(vaga, a.presidente ? "ambar" : "azul"),
                    Ui.rotulo(a.gols + " gols na Kings", "texto-pequeno")));
            return v;
        }
        VBox v = new VBox(14);
        v.getStyleClass().add("painel");
        Label nome = Ui.rotulo(a.nome, "titulo-tela");
        nome.setStyle("-fx-font-size: 19px;");
        nome.setWrapText(true);
        HBox selos = Ui.linha(6, Ui.pos(a.posicao == null ? Posicao.MC : a.posicao), Ui.etiqueta(vaga, a.presidente ? "ambar" : "azul"));
        HBox onde = Ui.linha(6, Ui.escudo(t.id, 18), Ui.rotulo(t.nome, "texto-medio"));
        VBox id = new VBox(4, nome, selos, onde);
        if (a.pais != null || a.anoNasc != null)
            id.getChildren().add(Ui.linha(6, Imagens.bandeira(a.pais, 12), Ui.rotulo((a.pais == null ? "" : Textos.pais(a.pais))
                    + (a.anoNasc == null ? "" : (a.pais == null ? "" : " · ") + (j.ano() - a.anoNasc) + " anos"), "texto-medio")));
        HBox.setHgrow(id, Priority.ALWAYS);
        VBox forca = new VBox(-4, Ui.forcaGrande((int) Math.round(a.forca), "numero-enorme"), Ui.rotulo("força", "texto-pequeno"));
        forca.setAlignment(Pos.TOP_CENTER);
        HBox topo = new HBox(14, Foto.kings(a.foto, null, t.id, a.nome, 96, 116), id, forca);
        topo.setAlignment(Pos.TOP_LEFT);
        v.getChildren().add(topo);
        v.getChildren().add(Ui.linha(18, Ui.dado("Gols", String.valueOf(a.gols)), Ui.dado("Vaga", vaga),
                Ui.dado("Idade", a.anoNasc == null ? "—" : String.valueOf(j.ano() - a.anoNasc))));
        if (acoes != null) v.getChildren().add(acoes);
        GridPane info = new GridPane();
        info.setHgap(12);
        info.setVgap(4);
        int r = 0;
        r = par(info, r, "Posição", (a.posicao == null ? Posicao.MC : a.posicao).nome);
        r = par(info, r, "Time", t.nome);
        r = par(info, r, "Origem", a.presidente ? "Presidente do time (joga e nunca sai)" : "Elenco real da Kings League");
        if (a.fonte != null && !a.fonte.isBlank()) par(info, r, "Fonte", a.fonte);
        v.getChildren().addAll(Ui.tituloPainel("Dados"), info,
                Ui.paragrafo("Atleta que só existe na Kings League: sem atributos detalhados no banco; a força vem da fonte.", "texto-pequeno"));
        return v;
    }

    private static String data(String iso) {
        if (iso.length() >= 10) return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
        return iso;
    }

    private static int par(GridPane g, int r, String rotulo, String valor) {
        Label a = Ui.rotulo(rotulo, "texto-suave");
        Label b = Ui.paragrafo(valor);
        a.setMinWidth(110);
        g.addRow(r, a, b);
        GridPane.setValignment(a, javafx.geometry.VPos.TOP);
        GridPane.setHgrow(b, Priority.ALWAYS);
        return r + 1;
    }
}
