package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Selecao;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Seleções: a Copa do Mundo 2026 (grupos e mata-mata), a sua seleção ou a do seu país, outros torneios e o ranking. */
public final class PainelSelecoes implements Hub.Painel {
    private final Sessao sessao;
    private static int abaAntes;

    public PainelSelecoes(Sessao sessao) {
        this.sessao = sessao;
    }

    private Jogo j() { return sessao.jogo; }

    @Override
    public Node construir() {
        Jogo j = j();
        Selecao minha = j.minhaSelecao();
        Clube meu = j.meuClube();
        String pais = minha != null ? minha.pais : meu != null ? meu.pais : "BRA";
        TabPane abas = new TabPane();
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        boolean tenho = minha != null;
        abas.getTabs().addAll(Ui.abaSobDemanda("Competições", () -> new PainelTabelas(sessao, true).soFiltro("Seleções").construir()),
                Ui.abaSobDemanda("Todas as seleções", this::todas),
                Ui.abaSobDemanda(tenho ? "Minha seleção" : "Seleção " + Textos.pais(pais), () -> selecao(pais, tenho)),
                Ui.abaSobDemanda("Ranking", this::ranking));
        abas.getSelectionModel().select(Math.min(abaAntes, 3));
        Ui.ativarSobDemanda(abas);
        abas.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> abaAntes = b.intValue());
        HBox cab = Ui.linha(12, Ui.titulo("Seleções"), Ui.espaco());
        if (minha != null) cab.getChildren().add(Ui.linha(8, Imagens.bandeira(minha.pais, 16), Ui.rotulo("Você comanda a seleção " + minha.nome, "texto-medio")));
        VBox v = new VBox(12, cab, abas);
        VBox.setVgrow(abas, Priority.ALWAYS);
        v.setStyle("-fx-padding: 20 24 16 24;");
        return v;
    }

    /** Todas as seleções com busca; clicar abre a ficha (técnico, convocados, ranking, títulos). */
    private Node todas() {
        javafx.scene.control.TextField busca = new javafx.scene.control.TextField();
        busca.setPromptText("Buscar seleção");
        busca.setPrefWidth(260);
        javafx.scene.control.TableView<Selecao> tv = Ui.tabela();
        List<Selecao> rk = j().rankingSelecoes();
        Ui.colNum(tv, "#", 44, s -> rk.indexOf(s) + 1);
        Ui.colNo(tv, "Seleção", 240, s -> Ui.linha(8, Imagens.bandeira(s.pais, 13), Ui.nomeClube("sel-" + s.pais, s.nome)));
        Ui.col(tv, "Confederação", 120, s -> s.confederacao == null ? "" : s.confederacao);
        Ui.colForca(tv, "Força", s -> (int) Math.round(s.forca));
        Ui.col(tv, "Títulos", 220, s -> String.join(", ", s.titulos));
        tv.getItems().setAll(rk);
        busca.textProperty().addListener((o, a, q) -> {
            String n = q == null ? "" : java.text.Normalizer.normalize(q.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            List<Selecao> l = new java.util.ArrayList<>();
            for (Selecao s : rk) {
                String nn = java.text.Normalizer.normalize(s.nome.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
                if (n.isBlank() || nn.contains(n)) l.add(s);
            }
            tv.getItems().setAll(l);
        });
        tv.setRowFactory(x -> {
            javafx.scene.control.TableRow<Selecao> r = new javafx.scene.control.TableRow<>();
            r.setOnMouseClicked(e -> {
                if (r.getItem() != null && App.atual().hub() != null) {
                    Hub h = App.atual().hub();
                    h.abrirFicha(Fichas.selecao(h, r.getItem().pais, h::fecharFicha));
                }
            });
            r.setCursor(javafx.scene.Cursor.HAND);
            return r;
        });
        VBox p = Ui.painelTabela("Seleções", busca, tv);
        VBox.setVgrow(p, Priority.ALWAYS);
        return p;
    }

    private Node copa() {
        Torneio t = j().copaDoMundo();
        if (t == null) return Ui.painelAcoes(null, null, Ui.vazio("Copa do Mundo 2026 · junho e julho", null, null));
        return Ui.rolar(torneio(t));
    }

    /** Fases de grupos lado a lado e o chaveamento, para qualquer torneio de seleções. */
    private Node torneio(Torneio t) {
        Mundo m = j().mundo();
        String meu = j().minhaSelecao() == null ? null : j().minhaSelecao().id;
        VBox v = new VBox(16);
        HBox cab = Ui.linha(14, Ui.trofeu(t.id, t.ambito, t.pais, 56), new VBox(2, Ui.nomeCompeticao(t.id, Textos.curto(t.nome) + (t.nome.contains(String.valueOf(t.ano)) ? "" : " " + t.ano), "titulo-painel"),
                Ui.rotulo(t.participantes.size() + " seleções" + (t.faseAtual() != null && t.faseAtual().nome != null ? " · " + t.faseAtual().nome : ""), "texto-suave")));
        if (t.campeao != null) cab.getChildren().addAll(Ui.espaco(), new VBox(2, Ui.rotulo("Campeã", "texto-pequeno"), Ui.clube(m, t.campeao, 26, "titulo-painel")));
        v.getChildren().add(cab);
        List<FaseExec> mata = new ArrayList<>();
        for (FaseExec f : t.fases) {
            if (f.mataMata()) {
                mata.add(f);
                continue;
            }
            if (!f.iniciada && !f.concluida && t.gruposFixos == null) continue;
            FlowPane grupos = new FlowPane(14, 14);
            int ng = Math.max(1, f.numGrupos);
            for (int g = 0; g < ng; g++) {
                List<LinhaTabela> ls = t.tabela(f.indice, g);
                if (ls.isEmpty()) continue;
                int gg = g;
                java.util.function.Supplier<Node> grupo = () -> {
                    TableView<LinhaTabela> tv = PainelTabelas.tabelaClassificacao(m, t, ls, m::nomeParticipante, meu, 0, 0, f.porGrupo, true);
                    VBox p = Ui.painelTabela(ng > 1 ? "Grupo " + (char) ('A' + gg) : f.nome, null, tv);
                    p.setPrefWidth(400);
                    return p;
                };
                grupos.getChildren().add(g < PainelTabelas.GRUPOS_NA_HORA ? grupo.get() : Ui.adiado(grupo, 400, PainelTabelas.alturaGrupo(ls.size())));
            }
            if (!grupos.getChildren().isEmpty()) v.getChildren().addAll(Ui.rotulo(f.nome == null ? "Fase de grupos" : f.nome, "titulo-menor"), grupos);
        }
        if (!mata.isEmpty()) {
            ScrollPane sp = new ScrollPane(PainelTabelas.chaveamento(t, mata, m::nomeParticipante, meu));
            sp.setFitToHeight(true);
            sp.setPrefHeight(560);
            sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            v.getChildren().addAll(Ui.rotulo("Mata-mata", "titulo-menor"), sp);
        }
        return v;
    }

    private Node selecao(String pais, boolean minha) {
        Jogo j = j();
        Mundo m = j.mundo();
        Selecao s = j.selecao(pais);
        if (s == null) return Ui.painelAcoes(null, null, Ui.vazio("O seu país não tem seleção cadastrada.", null, null));
        List<Jogador> conv = new ArrayList<>(j.convocados(pais));
        conv.sort(Comparator.comparingInt((Jogador x) -> x.posicao.ordinal()).thenComparingDouble(x -> -x.forca));
        TableView<Jogador> t = Ui.tabela();
        Ui.colPos(t, x -> x.posicao);
        Ui.col(t, "Jogador", 200, Jogador::nomeExibicao);
        Ui.colNum(t, "Idade", 52, x -> x.idade(j.ano()));
        Ui.colForca(t, "Força", Jogador::forcaExibida);
        Ui.colClube(t, "Clube", 220, m, x -> x.clubeId);
        Ui.colNum(t, "Jogos", 56, x -> x.jogosSelecao);
        Ui.colNum(t, "Gols", 56, x -> x.golsSelecao);
        t.getItems().setAll(conv);
        t.setPlaceholder(Ui.rotulo("A convocação sai antes do primeiro jogo da seleção no ano.", "texto-suave"));
        int pos = j.rankingSelecoes().indexOf(s) + 1;
        HBox cab = Ui.linha(18, Imagens.bandeira(pais, 40), new VBox(2, Ui.rotulo(s.nome, "titulo-tela"), Ui.rotulo("Técnico: " + (minha ? m.tecnico.nome : s.tecnico == null ? "—" : s.tecnico), "texto-suave")),
                Ui.espaco(), Ui.dado("Ranking", pos > 0 ? pos + "º" : "—"), Ui.dado("Força", Ui.forca((int) Math.round(s.forca))),
                Ui.dado("Jogos", s.vitorias + "V " + s.empates + "E " + s.derrotas + "D"));
        if (minha) cab.getChildren().add(Ui.botao("Deixar a seleção", () -> {
            if (Ui.confirmar("Deixar a seleção", "Deixar o comando da seleção " + s.nome + "? Você continua no clube.")) {
                j.deixarSelecao();
                br.futebas.ui.App.atual().irParaHub("selecoes");
            }
        }, "perigo"));
        VBox tab = Ui.painelTabela("Convocados" + (conv.isEmpty() ? "" : " (" + conv.size() + ")"), Ui.rotulo("convocação automática dos melhores do país", "texto-pequeno"), t);
        VBox v = new VBox(14, Ui.painelAcoes(null, null, cab), tab);
        if (!s.titulos.isEmpty()) v.getChildren().add(1, Ui.painel("Títulos", Ui.paragrafo(String.join(" · ", s.titulos))));
        VBox.setVgrow(tab, Priority.ALWAYS);
        return v;
    }

    private Node torneios() {
        VBox v = new VBox(20);
        for (Torneio t : j().torneiosDeSelecoes()) {
            if (t == j().copaDoMundo()) continue;
            v.getChildren().add(torneio(t));
        }
        if (v.getChildren().isEmpty()) v.getChildren().add(Ui.painelAcoes(null, null, Ui.vazio("Nenhum torneio nesta temporada", null, null)));
        return Ui.rolar(v);
    }

    private Node ranking() {
        TableView<Selecao> tv = Ui.tabela();
        List<Selecao> rk = j().rankingSelecoes();
        java.util.Map<Selecao, Integer> pos = new java.util.HashMap<>();
        for (int i = 0; i < rk.size(); i++) pos.put(rk.get(i), i + 1);
        Ui.colNum(tv, "#", 46, pos::get);
        Ui.colNo(tv, "Seleção", 240, s -> {
            HBox h = Ui.linha(8, Imagens.bandeira(s.pais, 14), Ui.nomeClube("sel-" + s.pais, s.nome));
            if (s.humano) h.getChildren().add(Ui.etiqueta("você", "verde"));
            return h;
        });
        Ui.col(tv, "Confederação", 110, s -> s.confederacao == null ? "" : s.confederacao);
        Ui.colNum(tv, "Elo", 70, s -> (int) Math.round(s.elo));
        Ui.colForca(tv, "Força", s -> (int) Math.round(s.forca));
        Ui.col(tv, "Títulos no seu mundo", 260, s -> String.join(", ", s.titulos));
        tv.getItems().setAll(rk);
        VBox p = Ui.painelTabela(null, null, tv);
        VBox v = new VBox(8, Ui.rotulo("Força Elo: sobe e desce com os resultados.", "texto-suave"), p);
        VBox.setVgrow(p, Priority.ALWAYS);
        return v;
    }

    @SuppressWarnings("unused")
    private static Label nada() { return new Label(); }
}
