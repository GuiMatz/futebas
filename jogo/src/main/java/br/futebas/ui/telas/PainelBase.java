package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Base e Sub-20. Base: os juniores do clube com o CPE (mostrado com erro). Sub-20: o time sub-20
 * (até 20 anos, base + profissionais jovens), talento estimado, promover/dispensar, a divisão
 * sub-20 e as competições sub-20 do clube (só simulação).
 */
public final class PainelBase implements Hub.Painel {
    private final Hub hub;
    private static int abaAntes;

    PainelBase(Hub hub) {
        this.hub = hub;
    }

    @Override
    public Node construir() {
        TabPane abas = new TabPane();
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        abas.getTabs().addAll(Ui.abaSobDemanda("Base e peneira", this::base), Ui.abaSobDemanda("Sub-20", this::sub20));
        abas.getSelectionModel().select(Math.min(abaAntes, 1));
        Ui.ativarSobDemanda(abas);
        abas.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> abaAntes = b.intValue());
        VBox v = Ui.conteudo(Ui.titulo("Base"), abas);
        VBox.setVgrow(abas, Priority.ALWAYS);
        return v;
    }

    /** Abre direto na aba Sub-20. */
    public static void abrirSub20(Hub hub) {
        abaAntes = 1;
        hub.selecionar("base");
    }

    private ScrollPane lateral() {
        ScrollPane det = new ScrollPane();
        det.setFitToWidth(true);
        det.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        det.setPrefWidth(372);
        det.setMinWidth(340);
        det.setStyle("-fx-padding: 12 4 12 12;");
        return det;
    }

    private Node base() {
        Jogo j = hub.jogo();
        Clube c = hub.clube();
        // a base mostra só quem ainda não subiu ao sub-20 (Base → Sub-20 → Profissional)
        j.elencoSub20(c.id); // monta o sub-20 inicial, se ainda não foi montado
        List<Jogador> base = new ArrayList<>();
        for (Jogador x : j.base(c.id)) if (!x.noSub20) base.add(x);
        base.sort(Comparator.comparingInt((Jogador x) -> -j.cpe(x.id)));
        TableView<Jogador> t = Ui.tabela();
        Ui.colPos(t, x -> x.posicao);
        Ui.colNo(t, "Jogador", 200, x -> Ui.linha(7, Imagens.bandeira(x.pais, 11), Ui.rotulo(x.nomeExibicao())));
        Ui.colNum(t, "Idade", 54, x -> x.idade(j.ano()));
        Ui.colForca(t, "Força", Jogador::forcaExibida);
        Ui.colNo(t, "POT", 70, x -> {
            Label l = Ui.forcaGrande(j.cpe(x.id), "numero");
            l.setStyle("-fx-font-size: 16px;");
            return l;
        }).setComparator(Comparator.comparingInt(x -> j.cpe(x.id)));
        t.getItems().setAll(base);

        ScrollPane det = lateral();
        t.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b == null) det.setContent(null);
            else det.setContent(FichaJogador.construir(j, b, Ui.linha(8, subirSub20(j, b), dispensar(j, b))));
        });
        if (!base.isEmpty()) t.getSelectionModel().select(0);

        HBox resumo = Ui.linha(24, Ui.dado("Juniores", String.valueOf(base.size())),
                Ui.dado("Maior POT", base.isEmpty() ? "—" : String.valueOf(j.cpe(base.get(0).id))),
                Ui.info("POT: potencial estimado pelos olheiros (1 a 99), com erro. A cada temporada a peneira traz 5 garotos de 15 a 17 anos (às vezes 6 ou 7). "
                        + "Quem você não subir ao Sub-20 vai embora no fim da temporada; o Sub-20 é sempre completado até 23."));
        VBox tab = Ui.painelTabela(null, null, t);
        VBox v = new VBox(12, resumo, base.isEmpty() ? Ui.painelAcoes(null, null, Ui.vazio("Nenhum júnior na base", null, null)) : tab);
        v.setStyle("-fx-padding: 12 0 0 0;");
        VBox.setVgrow(tab, Priority.ALWAYS);
        BorderPane bp = new BorderPane(v);
        bp.setRight(det);
        return bp;
    }

    private Node subirSub20(Jogo j, Jogador b) {
        return Ui.botao("Subir ao Sub-20", () -> {
            if (j.subirAoSub20(b.id)) hub.atualizar();
        }, "primario");
    }

    private Node devolverBase(Jogo j, Jogador b) {
        return Ui.botao("Devolver à base", () -> {
            if (j.devolverABase(b.id)) hub.atualizar();
        });
    }

    private Node promover(Jogo j, Jogador b) {
        Node n = Ui.botao("Promover ao profissional", () -> {
            if (j.promoverDaBase(b.id)) {
                Ui.informar("Base", b.nomeExibicao() + " promovido ao profissional.");
                hub.atualizar();
            }
        }, "primario");
        n.setDisable(!j.base(hub.clube().id).contains(b));
        return n;
    }

    private Node dispensar(Jogo j, Jogador b) {
        return Ui.botao("Dispensar", () -> {
            if (!Ui.confirmar("Dispensar", "Dispensar " + b.nomeExibicao() + "?")) return;
            j.dispensar(b.id);
            hub.atualizar();
        }, "perigo");
    }

    // ------------------------------------------------------------------ sub-20
    private Node sub20() {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        Clube c = hub.clube();
        List<Jogador> elenco;
        try {
            elenco = new ArrayList<>(j.elencoSub20(c.id));
        } catch (RuntimeException e) {
            elenco = new ArrayList<>();
        }
        // sempre do goleiro ao atacante (requisito 13/11), o mais forte primeiro na mesma posição
        elenco.sort(Comparator.comparingInt((Jogador x) -> CampoEscalacao.ordemBanco(x.posicao)).thenComparingDouble(x -> -x.forca));
        List<Jogador> base = j.base(c.id);
        TableView<Jogador> t = Ui.tabela();
        Ui.colPos(t, x -> x.posicao);
        Ui.colNo(t, "Jogador", 200, x -> Ui.linha(7, Imagens.bandeira(x.pais, 11), Ui.nomeJogador(x.id, x.nomeExibicao())));
        Ui.colNum(t, "Idade", 54, x -> x.idade(j.ano()));
        Ui.colForca(t, "Força", Jogador::forcaExibida);
        Ui.colNo(t, "Talento", 96, x -> Ui.estrelas(j.estrelasTalento(x.id) * 20, 12))
                .setComparator(Comparator.comparingDouble(x -> j.estrelasTalento(x.id)));
        Ui.colNum(t, "POT", 54, x -> j.talentoEstimado(x.id));
        // temporada nos jogos do sub-20
        Ui.colNum(t, "J", 40, x -> x.sub20Jogos);
        Ui.colNum(t, "G", 40, x -> x.sub20Gols);
        Ui.colNum(t, "A", 40, x -> x.sub20Assist);
        var colNota = Ui.colNo(t, "Nota", 50, x -> Ui.rotulo(x.sub20Jogos == 0 ? "—" : String.format(java.util.Locale.ROOT, "%.1f", x.notaMediaSub20()),
                x.sub20Jogos == 0 ? "texto-suave" : "texto-forte"));
        colNota.setComparator((a, b) -> Double.compare(a.notaMediaSub20(), b.notaMediaSub20()));
        colNota.setSortable(true);
        t.getItems().setAll(elenco);
        ScrollPane det = lateral();
        t.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b == null) det.setContent(null);
            else det.setContent(FichaJogador.construir(j, b, new javafx.scene.layout.FlowPane(8, 8, promover(j, b), devolverBase(j, b), dispensar(j, b))));
        });
        if (!elenco.isEmpty()) t.getSelectionModel().select(0);

        int div = 0;
        double forca = 0;
        try {
            div = j.divisaoSub20(c.id);
            forca = j.forcaSub20(c.id);
        } catch (RuntimeException e) {
            // sem sub-20 neste país
        }
        HBox resumo = Ui.linha(24, Ui.dado("Jogadores", String.valueOf(elenco.size())), Ui.dado("Força", Ui.forca((int) Math.round(forca))),
                Ui.dado("Divisão", div <= 0 ? "—" : Textos.divisao(div)),
                Ui.info("POT do sub-20: potencial estimado de 1 a 10, com erro."));
        VBox tab = Ui.painelTabela(null, null, t);
        VBox esquerda;
        if (elenco.isEmpty()) {
            esquerda = new VBox(12, resumo, Ui.painelAcoes(null, null, Ui.vazio("Sem time sub-20", null, null)));
        } else {
            // Elenco | Escalação (só leitura: o time que a simulação põe em campo)
            javafx.scene.layout.StackPane conteudo = new javafx.scene.layout.StackPane(tab);
            VBox.setVgrow(conteudo, Priority.ALWAYS);
            List<Jogador> elencoFinal = elenco;
            // requisito (15/11): aba Campeonatos com os campeonatos sub-20 que o time joga
            HBox alternar = Ui.segmentos(List.of("Elenco", "Escalação", "Campeonatos"), x -> x, "Elenco",
                    x -> conteudo.getChildren().setAll("Elenco".equals(x) ? tab : "Campeonatos".equals(x) ? campeonatosSub20(j, m, c)
                            : escalacaoSub20(j, c, elencoFinal, t)));
            esquerda = new VBox(12, resumo, alternar, conteudo);
        }
        VBox.setVgrow(tab, Priority.ALWAYS);
        esquerda.setStyle("-fx-padding: 12 0 0 0;");
        HBox.setHgrow(esquerda, Priority.ALWAYS);

        VBox comps = competicoes(j, m, c);
        ScrollPane rc = Ui.rolar(comps);
        rc.setPrefWidth(340);
        rc.setMinWidth(300);
        // a ficha do jogador tem prioridade na altura (antes as competições cresciam e cortavam a foto)
        VBox dir = new VBox(12, det, rc);
        VBox.setVgrow(det, Priority.ALWAYS);
        det.setMinHeight(400);
        rc.setPrefHeight(280);
        rc.setMinHeight(150);
        HBox h = new HBox(12, esquerda, dir);
        return h;
    }

    /**
     * Escalação do sub-20, só para ver (requisito): os 11 que a simulação põe em campo (os mais
     * fortes sem lesão; faltando gente, juniores anônimos), distribuídos na formação do clube pelo
     * encaixe, e o banco (o resto do sub-20). Sem arrastar nem menus; clicar abre a ficha ao lado.
     */
    private Node escalacaoSub20(Jogo j, Clube c, List<Jogador> elenco, TableView<Jogador> tabela) {
        List<Jogador> onze;
        double junior;
        try {
            onze = j.onzeSub20(c.id);
            junior = j.forcaJuniorSub20(c.id);
        } catch (RuntimeException e) {
            onze = List.of();
            junior = 0;
        }
        String formacao = c.tatica != null && c.tatica.formacao != null ? c.tatica.formacao : "4-4-2";
        List<br.futebas.partida.Escalado> vagas = br.futebas.partida.Escalador.distribuir(onze, formacao);
        br.futebas.mundo.Posicao[] pos = br.futebas.partida.Formacoes.vagas(formacao);
        List<CampoEscalacao.Peca> pecas = new ArrayList<>();
        List<Jogador> naVaga = new ArrayList<>();
        Mundo m = j.mundo();
        for (int i = 0; i < pos.length; i++) {
            br.futebas.partida.Escalado e = i < vagas.size() ? vagas.get(i) : null;
            Jogador x = e == null ? null : m.jogador(e.id);
            naVaga.add(x);
            if (x == null) {
                pecas.add(new CampoEscalacao.Peca(true, "Júnior", pos[i].name(), null, (int) Math.round(junior), false, false, false, null, null, false));
            } else {
                pecas.add(new CampoEscalacao.Peca(true, x.nomeExibicao(), pos[i].name(), br.futebas.ui.comp.Foto.de(j.foto(x.id), x.dbId, 50, 50),
                        x.forcaExibida(), false, br.futebas.partida.Escalador.encaixe(x, pos[i]) < 0.95, false, null, null, false));
            }
        }
        CampoEscalacao campo = new CampoEscalacao(new CampoEscalacao.Acoes() {
            @Override
            public void clicar(int vaga) {
                Jogador x = vaga < naVaga.size() ? naVaga.get(vaga) : null;
                if (x != null) tabela.getSelectionModel().select(x);
            }

            @Override public void soltar(int vaga, String conteudo) {}

            @Override public String arraste(int vaga) { return null; }

            @Override public double encaixeDoArraste(int vaga) { return Double.NaN; }
        });
        campo.cor(c.cor1);
        campo.desenhar(pecas, CampoEscalacao.coordenadasOnze(formacao, pos), -1, -1);
        CampoEscalacao.Suporte suporte = campo.escalavel();

        VBox banco = new VBox(4);
        for (Jogador x : elenco) {
            if (onze.contains(x)) continue;
            Label n = Ui.nomeJogador(x.id, x.nomeExibicao());
            n.setMinWidth(0);
            HBox h = Ui.linha(7, Ui.pos(x.posicao), n, Ui.espaco());
            if (x.lesaoSemanas > 0) h.getChildren().add(Ui.etiqueta("Lesão", "vermelha"));
            h.getChildren().addAll(Ui.rotulo(x.idade(j.ano()) + " anos", "texto-pequeno"), Ui.forca(x.forcaExibida()));
            h.getStyleClass().add("atleta");
            h.setOnMouseClicked(ev -> tabela.getSelectionModel().select(x));
            banco.getChildren().add(h);
        }
        if (banco.getChildren().isEmpty()) banco.getChildren().add(Ui.rotulo("Ninguém no banco.", "texto-suave"));
        ScrollPane rolBanco = Ui.rolar(banco);
        rolBanco.setFitToWidth(true);
        VBox.setVgrow(rolBanco, Priority.ALWAYS);
        VBox pBanco = Ui.painelAcoes("Banco", Ui.rotulo((elenco.size() - onze.size()) + " jogadores", "texto-pequeno"), rolBanco);
        VBox.setVgrow(pBanco, Priority.ALWAYS);
        Label nota = Ui.info("Só para ver: o sub-20 joga com os 11 mais fortes sem lesão" + (onze.size() < 11
                ? "; faltam " + (11 - onze.size()) + ", completados por juniores (força " + Math.round(junior) + ")" : "")
                + ". Posições na formação do clube (" + formacao + ").");
        nota.setWrapText(true);
        VBox lado = new VBox(10, pBanco, nota);
        lado.setPrefWidth(300);
        lado.setMinWidth(240);
        HBox h = new HBox(12, suporte, lado);
        return h;
    }

    /** Os campeonatos sub-20 do clube na temporada: tabela completa de cada um (clique no nome abre a competição). */
    private Node campeonatosSub20(Jogo j, Mundo m, Clube c) {
        VBox v = new VBox(12);
        for (Torneio t : j.competicoesSub20()) {
            if (!t.participantes.contains(c.id)) continue;
            List<LinhaTabela> ls;
            try {
                ls = j.tabelaSub20(t.id);
            } catch (RuntimeException e) {
                ls = List.of();
            }
            GridPane g = new GridPane();
            g.setHgap(14);
            g.setVgap(5);
            String[] cab = {"#", "Clube", "J", "V", "E", "D", "SG", "P"};
            for (int k = 0; k < cab.length; k++) g.add(Ui.rotulo(cab[k], "texto-pequeno"), k, 0);
            for (int i = 0; i < ls.size(); i++) {
                LinhaTabela l = ls.get(i);
                HBox cl = Ui.clube(m, l.clubeId, 18);
                cl.setMinWidth(0);
                boolean meu = l.clubeId.equals(c.id);
                if (meu) cl.getChildren().get(1).setStyle("-fx-font-weight: bold; -fx-text-fill: -acento-nav;");
                g.addRow(i + 1, Ui.rotulo(String.valueOf(i + 1), "numero"), cl, Ui.rotulo(String.valueOf(l.jogos), "numero"),
                        Ui.rotulo(String.valueOf(l.vitorias), "numero"), Ui.rotulo(String.valueOf(l.empates), "numero"),
                        Ui.rotulo(String.valueOf(l.derrotas), "numero"), Ui.rotulo(String.valueOf(l.saldo()), "numero"),
                        Ui.rotulo(String.valueOf(l.pontos), "pontos-destaque"));
                GridPane.setHgrow(cl, Priority.ALWAYS);
            }
            HBox titulo = Ui.linha(8, Imagens.trofeu(t.id, t.ambito, t.pais, 24), Ui.rotulo(Textos.curto(t.nome), "titulo-painel"));
            titulo.setCursor(javafx.scene.Cursor.HAND);
            titulo.setOnMouseClicked(e -> hub.abrirCompeticao(t.id));
            VBox p = Ui.painelAcoes(null, Ui.botao("Abrir campeonato", () -> hub.abrirCompeticao(t.id), "fantasma"), titulo);
            p.getChildren().add(ls.isEmpty() ? Ui.rotulo("Tabela ainda sem jogos.", "texto-suave") : g);
            v.getChildren().add(p);
        }
        if (v.getChildren().isEmpty()) v.getChildren().add(Ui.vazio("O sub-20 não disputa campeonatos nesta temporada", null, null));
        return Ui.rolar(v);
    }

    private VBox competicoes(Jogo j, Mundo m, Clube c) {
        VBox v = new VBox(12);
        v.setStyle("-fx-padding: 12 0 0 0;");
        List<Torneio> ts = new ArrayList<>();
        for (Torneio t : j.competicoesSub20()) if (t.participantes.contains(c.id)) ts.add(t);
        for (Torneio t : ts) {
            List<LinhaTabela> ls;
            try {
                ls = j.tabelaSub20(t.id);
            } catch (RuntimeException e) {
                ls = List.of();
            }
            GridPane g = new GridPane();
            g.setHgap(8);
            g.setVgap(3);
            int r = 0;
            int meu = 0;
            for (int i = 0; i < ls.size(); i++) if (ls.get(i).clubeId.equals(c.id)) meu = i;
            int ini = Math.max(0, Math.min(meu - 3, ls.size() - 7));
            for (int i = ini; i < Math.min(ls.size(), ini + 7); i++) {
                LinhaTabela l = ls.get(i);
                HBox cl = Ui.clube(m, l.clubeId, 16);
                cl.setMinWidth(0);
                if (l.clubeId.equals(c.id)) cl.getChildren().get(1).setStyle("-fx-font-weight: bold; -fx-text-fill: -acento-nav;");
                g.addRow(r++, Ui.rotulo(String.valueOf(i + 1), "zona"), cl, Ui.rotulo(String.valueOf(l.jogos), "numero"),
                        Ui.rotulo(String.valueOf(l.pontos), "pontos-destaque"));
                GridPane.setHgrow(cl, Priority.ALWAYS);
            }
            HBox cab = Ui.linha(8, Imagens.trofeu(t.id, t.ambito, t.pais, 20), Ui.rotulo(Textos.curto(t.nome), "titulo-painel"));
            cab.setCursor(javafx.scene.Cursor.HAND);
            cab.setOnMouseClicked(e -> hub.abrirCompeticao(t.id));
            VBox p = Ui.painelAcoes(null, null, cab);
            if (r > 0) p.getChildren().add(g);
            v.getChildren().add(p);
        }
        // próximos jogos sub-20
        List<Partida> cal;
        try {
            cal = j.calendarioSub20(c.id);
        } catch (RuntimeException e) {
            cal = List.of();
        }
        VBox jogos = Ui.painel("Jogos");
        int n = 0;
        for (Partida p : cal) {
            if (p.jogada || n >= 5) continue;
            n++;
            boolean casa = c.id.equals(p.mandante);
            String adv = casa ? p.visitante : p.mandante;
            jogos.getChildren().add(Ui.linha(8, Ui.rotulo(Textos.dataCurta(p.data()), "texto-suave"), Ui.etiqueta(casa ? "C" : "F", casa ? "verde" : null),
                    Ui.clube(m, adv, 16)));
        }
        for (int i = cal.size() - 1, k = 0; i >= 0 && k < 3; i--) {
            Partida p = cal.get(i);
            if (!p.jogada) continue;
            k++;
            jogos.getChildren().add(1 + Math.min(k - 1, jogos.getChildren().size() - 1), TelaPartida.linhaResultado(m, p, c.id));
        }
        if (jogos.getChildren().size() > 1) v.getChildren().add(jogos);
        if (v.getChildren().isEmpty()) v.getChildren().add(Ui.painel("Competições", Ui.rotulo("—", "texto-suave")));
        return v;
    }
}
