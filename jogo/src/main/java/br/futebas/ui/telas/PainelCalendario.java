package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Mundo;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Calendário do clube na temporada: data, competição, adversário, placar e resultado. Marca amistosos. */
final class PainelCalendario implements Hub.Painel {
    private final Hub hub;

    PainelCalendario(Hub hub) {
        this.hub = hub;
    }

    @Override
    public Node construir() {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        Clube c = hub.clube();
        if (c == null && j.meuTimeKings() != null) return kings(j, m, j.meuTimeKings());
        List<Partida> ps = j.calendarioDoClube(c.id);
        int v = 0, e = 0, d = 0, gp = 0, gc = 0;
        for (Partida p : ps) {
            String r = Textos.resultadoPara(p, c.id);
            if (r.equals("V")) v++;
            else if (r.equals("E")) e++;
            else if (r.equals("D")) d++;
            if (p.jogada && p.golsMandante != null) {
                boolean casa = c.id.equals(p.mandante);
                gp += casa ? p.golsMandante : p.golsVisitante;
                gc += casa ? p.golsVisitante : p.golsMandante;
            }
        }
        TableView<Partida> t = Ui.tabela();
        t.getStyleClass().add("calendario"); // nomes maiores e resultado em destaque (regra do jogo)
        Ui.col(t, "Data", 92, p -> Textos.dataCurta(p.data()));
        Ui.colNo(t, "Competição", 230, p -> {
            Torneio tt = hub.sessao().fluxo.torneioDe(p);
            if (tt == null) return new Label();
            Label n = "amistoso".equals(tt.ambito) ? Ui.rotulo(Textos.curto(tt.nome)) : Ui.nomeCompeticao(tt.id, Textos.curto(tt.nome));
            String fase = Textos.fase(tt, p);
            HBox h = Ui.linha(8, "amistoso".equals(tt.ambito) ? Imagens.trofeu(tt.id, tt.ambito, tt.pais, 22) : Ui.trofeu(tt.id, tt.ambito, tt.pais, 22), n);
            if (!fase.isEmpty() && !"amistoso".equals(tt.ambito)) h.getChildren().add(Ui.rotulo(fase, "texto-pequeno"));
            return h;
        });
        Ui.colNo(t, "", 44, p -> Ui.etiqueta(c.id.equals(p.mandante) ? "C" : "F", c.id.equals(p.mandante) ? "verde" : null));
        Ui.colClube(t, "Adversário", 220, m, p -> c.id.equals(p.mandante) ? p.visitante : p.mandante);
        Ui.colNo(t, "Placar", 96, p -> {
            if (!p.jogada || p.golsMandante == null) return Ui.rotulo("–", "texto-suave");
            boolean casa = c.id.equals(p.mandante);
            String s = (casa ? p.golsMandante : p.golsVisitante) + " – " + (casa ? p.golsVisitante : p.golsMandante);
            if (p.penaltisMandante != null) s += "  (" + (casa ? p.penaltisMandante : p.penaltisVisitante) + "–" + (casa ? p.penaltisVisitante : p.penaltisMandante) + ")";
            return placar(s, Textos.resultadoPara(p, c.id));
        });
        Ui.colNo(t, "Resultado", 96, p -> resultado(Textos.resultadoPara(p, c.id)));
        Ui.col(t, "Público", 90, p -> p.jogada && p.publico > 0 ? Textos.milhar(p.publico) : "").getStyleClass().add("direita");
        Ui.colNo(t, "Auxiliar", 76, p -> {
            if (p.jogada) return new Label();
            javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox();
            cb.setSelected(j.delegadoAoAuxiliar(p.torneioId, p.id));
            cb.setFocusTraversable(false);
            cb.setOnAction(x -> j.delegarAoAuxiliar(p.torneioId, p.id, cb.isSelected()));
            javafx.scene.control.Tooltip.install(cb, new javafx.scene.control.Tooltip("O auxiliar escala e comanda este jogo; você só vê o resultado."));
            return cb;
        });
        t.getItems().setAll(ps);
        int proxima = -1;
        for (int i = 0; i < ps.size(); i++) if (!ps.get(i).jogada) {
            proxima = i;
            break;
        }
        if (proxima >= 0) {
            t.scrollTo(Math.max(0, proxima - 3));
            t.getSelectionModel().select(proxima);
        }
        HBox resumo = Ui.linha(24, Ui.dado("Jogos", String.valueOf(ps.size())), Ui.dado("Vitórias", String.valueOf(v)),
                Ui.dado("Empates", String.valueOf(e)), Ui.dado("Derrotas", String.valueOf(d)), Ui.dado("Gols", gp + " – " + gc), Ui.espaco(),
                Ui.botao("Simular temporada", () -> br.futebas.ui.App.atual().simularTemporada(null)),
                Ui.botao("Delegar competições", this::delegar), Ui.botao("Jogo de despedida", this::despedida),
                Ui.botao("Torneio amistoso", this::torneio), Ui.botao("Marcar amistoso", this::amistoso, "primario"));
        VBox tab = Ui.painelTabela(null, null, t);
        VBox box = Ui.conteudo(Ui.titulo("Calendário " + j.ano()), resumo, tab);
        VBox.setVgrow(tab, Priority.ALWAYS);
        if (ps.isEmpty()) box.getChildren().set(2, Ui.painelAcoes(null, null, Ui.vazio("Nenhum jogo marcado nesta temporada.", "Marcar amistoso", this::amistoso)));
        return box;
    }

    /** Carreira da Kings: os jogos do seu time em Split 1, Kings Cup, Split 2 e Copas do Mundo da Kings. */
    private Node kings(Jogo j, Mundo m, br.futebas.kings.LigaKings.TimeKings time) {
        String id = time.id;
        List<Torneio> comps = j.competicoesDoParticipante(id);
        List<Partida> ps = new ArrayList<>();
        java.util.Map<Partida, Torneio> de = new java.util.IdentityHashMap<>();
        for (Torneio tt : comps) for (Partida p : tt.partidasDoClube(id)) {
            ps.add(p);
            de.put(p, tt);
        }
        ps.sort(Comparator.comparingInt((Partida p) -> p.sa).thenComparingLong(p -> p.dia).thenComparingInt(p -> p.data));
        int v = 0, d = 0, gp = 0, gc = 0;
        for (Partida p : ps) {
            String r = Textos.resultadoPara(p, id);
            if (r.equals("V")) v++;
            else if (r.equals("D")) d++;
            if (p.jogada && p.golsMandante != null) {
                boolean casa = id.equals(p.mandante);
                gp += casa ? p.golsMandante : p.golsVisitante;
                gc += casa ? p.golsVisitante : p.golsMandante;
            }
        }
        TableView<Partida> t = Ui.tabela();
        t.getStyleClass().add("calendario"); // nomes maiores e resultado em destaque (regra do jogo)
        Ui.col(t, "Data", 92, p -> Textos.dataCurta(p.data()));
        Ui.colNo(t, "Competição", 260, p -> {
            Torneio tt = de.get(p);
            if (tt == null) return new Label();
            HBox h = Ui.linha(8, Ui.trofeu(tt.id, tt.ambito, tt.pais, 22), Ui.nomeCompeticao(tt.id, Textos.curto(tt.nome)));
            String fase = Textos.fase(tt, p);
            if (!fase.isEmpty()) h.getChildren().add(Ui.rotulo(fase, "texto-pequeno"));
            return h;
        });
        Ui.colNo(t, "", 44, p -> Ui.etiqueta(id.equals(p.mandante) ? "C" : "F", id.equals(p.mandante) ? "verde" : null));
        Ui.colClube(t, "Adversário", 240, m, p -> id.equals(p.mandante) ? p.visitante : p.mandante);
        Ui.colNo(t, "Placar", 110, p -> {
            if (!p.jogada || p.golsMandante == null) return Ui.rotulo("–", "texto-suave");
            boolean casa = id.equals(p.mandante);
            String s = (casa ? p.golsMandante : p.golsVisitante) + " – " + (casa ? p.golsVisitante : p.golsMandante);
            if (p.penaltisMandante != null) s += "  (" + (casa ? p.penaltisMandante : p.penaltisVisitante) + "–" + (casa ? p.penaltisVisitante : p.penaltisMandante) + ")";
            return placar(s, Textos.resultadoPara(p, id));
        });
        Ui.colNo(t, "Resultado", 96, p -> resultado(Textos.resultadoPara(p, id)));
        t.getItems().setAll(ps);
        for (int i = 0; i < ps.size(); i++) if (!ps.get(i).jogada) {
            t.scrollTo(Math.max(0, i - 3));
            t.getSelectionModel().select(i);
            break;
        }
        HBox resumo = Ui.linha(24, Ui.dado("Jogos", String.valueOf(ps.size())), Ui.dado("Vitórias", String.valueOf(v)),
                Ui.dado("Derrotas", String.valueOf(d)), Ui.dado("Gols", gp + " – " + gc), Ui.espaco(),
                Ui.botao("Simular temporada", () -> br.futebas.ui.App.atual().simularTemporada(null)),
                Ui.botao("Delegar competições", this::delegar));
        VBox tab = Ui.painelTabela(null, null, t);
        // mesmo cabeçalho do clube; sem empates (a Kings decide nos shootouts) e sem amistosos; o auxiliar vale (Kings = futebol)
        VBox box = Ui.conteudo(Ui.titulo("Calendário " + j.ano()), resumo, tab);
        VBox.setVgrow(tab, Priority.ALWAYS);
        if (ps.isEmpty()) box.getChildren().set(2, Ui.painel(null, Ui.rotulo("Nenhum jogo marcado: a temporada da Kings começa na 2ª semana do ano.", "texto-suave")));
        return box;
    }

    /** Escolhe adversário e mando; o amistoso entra na próxima semana livre. */
    /**
     * Escolha de clube por busca (regra do jogo: digita o nome e ele pesquisa, sem lista infinita).
     * Mostra até 30 resultados enquanto digita; sem texto, os clubes do seu país mais conhecidos.
     */
    private static final class BuscaClube extends VBox {
        final javafx.beans.property.ObjectProperty<Clube> valor = new javafx.beans.property.SimpleObjectProperty<>();
        private final javafx.scene.control.ListView<Clube> lista = new javafx.scene.control.ListView<>();

        BuscaClube(List<Clube> todos, Clube inicial, boolean opcional, java.util.function.Supplier<javafx.scene.control.ListCell<Clube>> celula) {
            super(6);
            TextField busca = new TextField();
            busca.setPromptText("Digite o nome do clube");
            busca.setPrefWidth(320);
            lista.setCellFactory(x -> celula.get());
            lista.setPrefHeight(6 * 30 + 4);
            lista.setPrefWidth(320);
            Label escolhido = Ui.rotulo("", "texto-forte");
            Runnable filtrar = () -> {
                String q = norm(busca.getText());
                List<Clube> l = new ArrayList<>();
                for (Clube x : todos) {
                    if (!q.isEmpty() && !norm(x.nome).contains(q)) continue;
                    l.add(x);
                    if (l.size() >= 30) break;
                }
                lista.getItems().setAll(l);
            };
            busca.textProperty().addListener((o, a, b) -> filtrar.run());
            lista.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
                if (b != null) valor.set(b);
            });
            valor.addListener((o, a, b) -> escolhido.setText(b == null ? (opcional ? "Nenhum" : "—") : "Escolhido: " + b.nome));
            filtrar.run();
            valor.set(inicial);
            escolhido.setText(inicial == null ? (opcional ? "Nenhum" : "—") : "Escolhido: " + inicial.nome);
            getChildren().addAll(busca, lista, escolhido);
            if (opcional) getChildren().add(Ui.botao("Sem convidado", () -> {
                lista.getSelectionModel().clearSelection();
                valor.set(null);
            }, "fantasma"));
        }

        Clube getValue() { return valor.get(); }

        private static String norm(String s) {
            if (s == null) return "";
            return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
        }
    }

    private void amistoso() {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        Clube c = hub.clube();
        List<Clube> todos = new ArrayList<>();
        for (Clube x : m.clubes.values()) if (!x.virtual && x.existe && x != c) todos.add(x);
        todos.sort(Comparator.comparing((Clube x) -> !x.pais.equals(c.pais)).thenComparingDouble(x -> -x.reputacao));
        BuscaClube adv = new BuscaClube(todos, null, false, PainelCalendario::celulaClube);
        ComboBox<String> mando = new ComboBox<>();
        mando.getItems().setAll("Em casa", "Fora de casa");
        mando.getSelectionModel().select(0);
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.addRow(1, Ui.rotulo("Adversário", "texto-suave"), adv);
        g.addRow(2, Ui.rotulo("Mando", "texto-suave"), mando);
        g.add(Ui.info("O amistoso entra na próxima semana livre. O adversário pode recusar."), 0, 3, 2, 1);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Marcar amistoso");
        d.setHeaderText("Marcar amistoso");
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Ui.dialogo(d).ifPresent(b -> {
            if (b != ButtonType.OK || adv.getValue() == null) return;
            Torneio t = j.marcarAmistoso(adv.getValue().id, mando.getSelectionModel().getSelectedIndex() == 0);
            if (t != null && br.futebas.ui.Opcoes.delegarAmistosos()) j.delegarAoAuxiliar(t.id, null, true);
            if (t == null) Ui.informar("Amistoso", "O " + adv.getValue().nome + " recusou o convite ou não há semana livre.");
            else Ui.informar("Amistoso marcado", "Amistoso contra o " + adv.getValue().nome + " marcado.");
            hub.atualizar();
        });
    }

    /**
     * Competições inteiras nas mãos do auxiliar (clube ou time da Kings). A marca é o estado da
     * competição (antes vinha do próximo jogo: sem jogo marcado aparecia desmarcada e o OK desfazia).
     */
    private void delegar() {
        Jogo j = hub.jogo();
        Clube c = hub.clube();
        String meuId = c != null ? c.id : j.meuTimeKings() != null ? j.meuTimeKings().id : null;
        if (meuId == null) return;
        List<Torneio> comps = new ArrayList<>(c != null ? j.competicoesDoAno() : j.competicoesKings());
        VBox lista = new VBox(8);
        java.util.Map<Torneio, javafx.scene.control.CheckBox> marcas = new java.util.LinkedHashMap<>();
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (Torneio t : comps) {
            if (!t.participantes.contains(meuId) || "amistoso".equals(t.ambito) || t.encerrado || !vistos.add(t.id)) continue;
            javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox();
            cb.setSelected(j.competicaoDelegada(t.id));
            marcas.put(t, cb);
            lista.getChildren().add(Ui.linha(10, cb, Imagens.trofeu(t.id, t.ambito, t.pais, 22), Ui.rotulo(Textos.curto(t.nome))));
        }
        if (marcas.isEmpty()) lista.getChildren().add(Ui.rotulo("Nenhuma competição em andamento.", "texto-suave"));
        Label exp = Ui.info("Nas competições marcadas o auxiliar escala e comanda os jogos (sem tela ao vivo).");
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Delegar ao auxiliar");
        d.setHeaderText("Delegar competições ao auxiliar");
        d.getDialogPane().setContent(new VBox(12, exp, lista));
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Ui.dialogo(d).ifPresent(b -> {
            if (b != ButtonType.OK) return;
            marcas.forEach((t, cb) -> j.delegarAoAuxiliar(t.id, null, cb.isSelected()));
            hub.atualizar();
        });
    }

    /** Quadrangular (ou triangular) amistoso com 2 ou 3 convidados, nas semanas livres. */
    private void torneio() {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        Clube c = hub.clube();
        List<Clube> todos = new ArrayList<>();
        for (Clube x : m.clubes.values()) if (!x.virtual && x.existe && x != c) todos.add(x);
        todos.sort(Comparator.comparing((Clube x) -> !x.pais.equals(c.pais)).thenComparingDouble(x -> -x.reputacao));
        TextField nome = new TextField("Torneio " + c.nome);
        List<BuscaClube> convidados = new ArrayList<>();
        HBox colunas = new HBox(12);
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.addRow(0, Ui.rotulo("Nome", "texto-suave"), nome);
        for (int i = 0; i < 3; i++) {
            BuscaClube cb = new BuscaClube(todos, i < 2 && todos.size() > i * 2 ? todos.get(i * 2) : null, i == 2, PainelCalendario::celulaClube);
            convidados.add(cb);
            colunas.getChildren().add(new VBox(4, Ui.rotulo("Convidado " + (i + 1) + (i == 2 ? " (opcional)" : ""), "texto-suave"), cb));
        }
        // os três convidados lado a lado (cada um com a sua busca)
        g.add(colunas, 0, 1, 2, 1);
        g.add(Ui.info("Turno único nas próximas semanas livres."), 0, 2, 2, 1);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Torneio amistoso");
        d.setHeaderText("Criar torneio amistoso");
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Ui.dialogo(d).ifPresent(b -> {
            if (b != ButtonType.OK) return;
            List<String> ids = new ArrayList<>();
            for (BuscaClube cb : convidados) if (cb.getValue() != null && !ids.contains(cb.getValue().id)) ids.add(cb.getValue().id);
            if (ids.size() < 2) {
                Ui.informar("Torneio amistoso", "Escolha pelo menos dois convidados.");
                return;
            }
            Torneio t = j.marcarTorneioAmistoso(nome.getText() == null || nome.getText().isBlank() ? "Torneio amistoso" : nome.getText().trim(), ids);
            if (t != null && br.futebas.ui.Opcoes.delegarAmistosos()) j.delegarAoAuxiliar(t.id, null, true);
            if (t == null) Ui.informar("Torneio amistoso", "Não há semanas livres suficientes ou os convidados recusaram.");
            else Ui.informar("Torneio marcado", t.nome + " marcado com " + (ids.size() + 1) + " clubes.");
            hub.atualizar();
        });
    }

    /** Jogo de despedida de um jogador do elenco com 30 anos ou mais (ele se aposenta depois). */
    private void despedida() {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        Clube c = hub.clube();
        List<br.futebas.mundo.Jogador> veteranos = new ArrayList<>();
        for (br.futebas.mundo.Jogador x : j.elenco(c.id)) if (x.idade(j.ano()) >= 30) veteranos.add(x);
        veteranos.sort(Comparator.comparingInt((br.futebas.mundo.Jogador x) -> -x.idade(j.ano())));
        if (veteranos.isEmpty()) {
            Ui.informar("Jogo de despedida", "Só jogadores com 30 anos ou mais podem ter jogo de despedida.");
            return;
        }
        ComboBox<br.futebas.mundo.Jogador> jog = new ComboBox<>();
        jog.getItems().setAll(veteranos);
        jog.setValue(veteranos.get(0));
        jog.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(br.futebas.mundo.Jogador x) { return x == null ? "" : x.nomeExibicao() + " (" + x.idade(j.ano()) + " anos)"; }
            @Override public br.futebas.mundo.Jogador fromString(String s) { return null; }
        });
        jog.setPrefWidth(320);
        List<Clube> todos = new ArrayList<>();
        for (Clube x : m.clubes.values()) if (!x.virtual && x.existe && x != c) todos.add(x);
        todos.sort(Comparator.comparing((Clube x) -> !x.pais.equals(c.pais)).thenComparingDouble(x -> -x.reputacao));
        ComboBox<Clube> adv = new ComboBox<>();
        adv.getItems().setAll(todos.subList(0, Math.min(300, todos.size())));
        adv.setCellFactory(x -> celulaClube());
        adv.setButtonCell(celulaClube());
        adv.setValue(adv.getItems().isEmpty() ? null : adv.getItems().get(0));
        adv.setPrefWidth(320);
        adv.setVisibleRowCount(12);
        // despedida = clube que o revelou × o último clube dele (o seu); só escolhe se foi revelado aqui
        Runnable revelacao = () -> {
            br.futebas.mundo.Jogador x = jog.getValue();
            String rid = x == null ? null : j.clubeRevelacao(x.id);
            Clube rc = rid == null ? null : m.clube(rid);
            if (rc != null) {
                if (!adv.getItems().contains(rc)) adv.getItems().add(0, rc);
                adv.setValue(rc);
            }
            adv.setDisable(rc != null);
        };
        jog.valueProperty().addListener((o, a, b) -> revelacao.run());
        revelacao.run();
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.addRow(0, Ui.rotulo("Jogador", "texto-suave"), jog);
        g.addRow(1, Ui.rotulo("Clube que o revelou", "texto-suave"), adv);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Jogo de despedida");
        d.setHeaderText("Marcar jogo de despedida");
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Ui.dialogo(d).ifPresent(b -> {
            if (b != ButtonType.OK || jog.getValue() == null || adv.getValue() == null) return;
            Torneio t = j.marcarJogoDeDespedida(jog.getValue().id, adv.getValue().id);
            if (t == null) Ui.informar("Jogo de despedida", "Não deu para marcar: sem semana livre ou o adversário recusou.");
            else Ui.informar("Despedida marcada", "Despedida de " + jog.getValue().nomeExibicao() + " contra o " + adv.getValue().nome + ".");
            hub.atualizar();
        });
    }

    private static ListCell<Clube> celulaClube() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Clube x, boolean vazio) {
                super.updateItem(x, vazio);
                if (vazio || x == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                setGraphic(Ui.linha(8, Ui.escudo(x.id, 18), Ui.rotulo(x.nome), Ui.rotulo(Textos.pais(x.pais), "texto-pequeno")));
                setText(null);
            }
        };
    }

    /** Placar na cor do resultado (verde vitória, cinza empate, vermelho derrota). */
    private static Label placar(String s, String r) {
        Label l = Ui.rotulo(s, "cal-placar");
        if (!r.isEmpty()) l.getStyleClass().add("V".equals(r) ? "cal-v" : "D".equals(r) ? "cal-d" : "cal-e");
        return l;
    }

    /** Resultado por extenso numa pastilha: VITÓRIA / EMPATE / DERROTA. */
    private static Node resultado(String r) {
        if (r.isEmpty()) return new Label();
        Label l = Ui.rotulo("V".equals(r) ? "VITÓRIA" : "D".equals(r) ? "DERROTA" : "EMPATE", "cal-resultado");
        l.getStyleClass().add("V".equals(r) ? "cal-v" : "D".equals(r) ? "cal-d" : "cal-e");
        return l;
    }
}
