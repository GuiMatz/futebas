package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Mundo;
import br.futebas.partida.Estatisticas;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.MataMataAoVivo;
import br.futebas.ui.modelo.TabelaAoVivo;
import br.futebas.ui.modelo.Textos;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Set;

/**
 * Coluna da direita da camada de trocas (intervalo, lesão, expulsão, tecla T), requisito: os
 * números do jogo até ali (posse, finalizações, no alvo, escanteios, faltas, cartões, impedimentos,
 * lado a lado com barras) e a tabela ao vivo da competição (com os placares de agora, o seu time
 * destacado e as zonas), ou, no mata-mata, o agregado de cada confronto e do que o seu time precisa.
 * A grade da tabela ao vivo é a mesma da coluna TABELA da {@link TelaPartida} ({@link #gradeTabela}).
 */
final class ResumoAoVivo {
    private final Sessao sessao;
    private final VBox raiz;
    private final VBox estat = new VBox(4);
    private final Label tituloEstat = Ui.rotulo("ESTATÍSTICAS", "coluna-titulo");
    private final VBox corpoTabela = new VBox(8);
    private final Label tituloTabela = Ui.rotulo("TABELA AO VIVO", "coluna-titulo");
    private final ScrollPane rolTabela;
    private final VBox colEstat;

    ResumoAoVivo(Sessao sessao) {
        this.sessao = sessao;
        colEstat = new VBox(8, tituloEstat, estat);
        colEstat.getStyleClass().addAll("coluna-partida", "resumo-estat");
        rolTabela = Ui.rolar(corpoTabela);
        rolTabela.setFitToWidth(true);
        rolTabela.setFitToHeight(true); // as linhas crescem para ocupar o painel (até um limite)
        corpoTabela.setAlignment(Pos.CENTER);
        rolTabela.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(rolTabela, Priority.ALWAYS);
        VBox colTabela = new VBox(8, tituloTabela, rolTabela);
        colTabela.getStyleClass().add("coluna-partida");
        VBox.setVgrow(colTabela, Priority.ALWAYS);
        raiz = new VBox(10, colEstat, colTabela);
        raiz.getStyleClass().add("resumo-ao-vivo");
        raiz.setMinWidth(330);
        raiz.setPrefWidth(400);
        raiz.setMaxWidth(760);
    }

    Node raiz() { return raiz; }

    /**
     * Mostra o jogo {@code idM} x {@code idV} agora: os números ({@code st}, null = sem números) e a
     * tabela com {@code placares} ({mandante, visitante, gols m, gols v}, o deste jogo incluído).
     * {@code meu}: o clube do técnico (destacado); {@code minuto}: o relógio do título.
     */
    void mostrar(String idM, String idV, Estatisticas st, String relogio, List<Object[]> placares, String meu) {
        Partida p = idM == null || idV == null ? null : acharPartida(sessao.jogo, idM, idV);
        mostrar(p == null ? null : sessao.fluxo.torneioDe(p), p, idM, idV, st, relogio, placares, meu);
    }

    /** Idem, com a competição e a partida já conhecidas (Kings). */
    void mostrar(Torneio t, Partida p, String idM, String idV, Estatisticas st, String relogio, List<Object[]> placares, String meu) {
        // nada mudou (o motor publica de novo a mesma foto durante a pausa): não redesenha nem perde a rolagem
        StringBuilder sb = new StringBuilder().append(idM).append(idV).append(relogio).append(meu).append(p == null ? -1 : p.id);
        for (Object[] x : placares) sb.append('|').append(x[0]).append(x[1]).append(x[2]).append('-').append(x[3]);
        if (st != null) sb.append(java.util.Arrays.toString(st.posse())).append(java.util.Arrays.toString(st.finalizacoes()))
                .append(java.util.Arrays.toString(st.noAlvo())).append(java.util.Arrays.toString(st.escanteios()))
                .append(java.util.Arrays.toString(st.faltas())).append(java.util.Arrays.toString(st.amarelos()))
                .append(java.util.Arrays.toString(st.vermelhos())).append(java.util.Arrays.toString(st.impedimentos()));
        if (sb.toString().equals(assinatura)) return;
        assinatura = sb.toString();
        Mundo m = sessao.jogo.mundo();
        meuLado = meu == null ? 0 : meu.equals(idV) ? 1 : 0;
        estatisticas(m, idM, idV, st, relogio);
        Set<String> jogando = new java.util.HashSet<>();
        if (idM != null) jogando.add(idM);
        if (idV != null) jogando.add(idV);
        tabela(m, t, p, placares, meu, jogando);
    }

    private String assinatura = "";

    // ------------------------------------------------------------------ estatísticas
    private void estatisticas(Mundo m, String idM, String idV, Estatisticas s, String relogio) {
        estat.getChildren().clear();
        colEstat.setVisible(s != null);
        colEstat.setManaged(s != null);
        if (s == null) return;
        tituloEstat.setText(relogio == null || relogio.isBlank() ? "ESTATÍSTICAS" : "ESTATÍSTICAS · " + relogio.toUpperCase(java.util.Locale.ROOT));
        HBox a = Ui.clube(m, idM, 18), b = Ui.clube(m, idV, 18);
        a.setMinWidth(0);
        b.setMinWidth(0);
        aDireita(b);
        HBox times = new HBox(8, a, Ui.espaco(), b);
        times.setAlignment(Pos.CENTER_LEFT);
        estat.getChildren().add(times);
        estat.getChildren().add(comparacao("Posse de bola", s.posse(), "%"));
        linha("Finalizações", s.finalizacoes());
        linha("No alvo", s.noAlvo());
        linha("Escanteios", s.escanteios());
        linha("Faltas", s.faltas());
        linha("Cartões amarelos", s.amarelos());
        linha("Cartões vermelhos", s.vermelhos());
        linha("Impedimentos", s.impedimentos());
    }

    private void linha(String rotulo, int[] v) {
        estat.getChildren().add(comparacao(rotulo, v, ""));
    }

    /** Lado do técnico (0 mandante, 1 visitante): a barra dele na cor de destaque, a do outro clara. */
    private int meuLado;

    /**
     * Uma estatística lado a lado (requisito: "muito sem contraste"): números grandes em negrito,
     * rótulo claro, barra grossa com o seu lado na cor de destaque e o outro em cinza-claro; quem
     * vence a estatística fica aceso, o outro apagado.
     */
    private Node comparacao(String rotulo, int[] v, String sufixo) {
        Label a = Ui.rotulo(v[0] + sufixo, "estat-num"), b = Ui.rotulo(v[1] + sufixo, "estat-num");
        Label r = Ui.rotulo(rotulo, "estat-rotulo");
        a.setMinWidth(Region.USE_PREF_SIZE);
        b.setMinWidth(Region.USE_PREF_SIZE);
        HBox topo = new HBox(6, a, Ui.espaco(), r, Ui.espaco(), b);
        topo.setAlignment(Pos.CENTER);
        double total = v[0] + v[1];
        double fa = total <= 0 ? 0.5 : v[0] / total;
        Region ra = new Region(), rb = new Region();
        ra.getStyleClass().addAll("estat-barra", meuLado == 0 ? "meu" : "rival");
        rb.getStyleClass().addAll("estat-barra", meuLado == 1 ? "meu" : "rival");
        if (v[0] > v[1]) {
            a.getStyleClass().add("vence");
            rb.getStyleClass().add("perde");
        } else if (v[1] > v[0]) {
            b.getStyleClass().add("vence");
            ra.getStyleClass().add("perde");
        }
        if (total <= 0) {
            ra.getStyleClass().add("vazia");
            rb.getStyleClass().add("vazia");
        }
        for (Region x : List.of(ra, rb)) {
            x.setMinHeight(8);
            x.setPrefHeight(8);
            x.setMinWidth(0);
        }
        HBox barra = new HBox(3, ra, rb);
        barra.widthProperty().addListener((o, x, w) -> {
            double util = Math.max(0, w.doubleValue() - 3);
            ra.setPrefWidth(util * fa);
            rb.setPrefWidth(util * (1 - fa));
        });
        VBox caixa = new VBox(1, topo, barra);
        caixa.getStyleClass().add("estat-linha");
        return caixa;
    }

    // ------------------------------------------------------------------ tabela / mata-mata
    private void tabela(Mundo m, Torneio t, Partida p, List<Object[]> placares, String meu, Set<String> jogando) {
        corpoTabela.getChildren().clear();
        TabelaAoVivo tv = TabelaAoVivo.de(t, p);
        if (tv != null) {
            String sub = Textos.curto(t.nome);
            tituloTabela.setText("TABELA AO VIVO · " + sub.toUpperCase(java.util.Locale.ROOT));
            List<TabelaAoVivo.Linha> ls = tv.agora(placares);
            VBox grade = gradeTabela(m, tv, ls, meu, jogando, true);
            VBox.setVgrow(grade, Priority.ALWAYS);
            corpoTabela.getChildren().add(grade);
            rolarAte(ls, meu);
            return;
        }
        MataMataAoVivo mm = MataMataAoVivo.de(t, p);
        if (mm != null) {
            tituloTabela.setText((mm.nomeFase() == null ? "MATA-MATA" : mm.nomeFase().toUpperCase(java.util.Locale.ROOT)) + " · AO VIVO");
            List<MataMataAoVivo.Situacao> ss = mm.agora(placares);
            for (MataMataAoVivo.Situacao s : ss) corpoTabela.getChildren().add(confronto(m, mm, s, meu));
            if (ss.isEmpty()) corpoTabela.getChildren().add(Ui.rotulo("Sem confrontos desta fase.", "texto-suave"));
            rolTabela.setVvalue(0);
            return;
        }
        tituloTabela.setText(t == null ? "AMISTOSO" : "TABELA");
        corpoTabela.getChildren().add(Ui.rotulo(t == null ? "Jogo sem tabela." : "Sem tabela nesta fase.", "texto-suave"));
    }

    /** Deixa a linha do seu time à vista (tabelas longas). */
    private void rolarAte(List<TabelaAoVivo.Linha> ls, String meu) {
        int i = -1;
        for (int k = 0; k < ls.size(); k++) if (ls.get(k).l().clubeId.equals(meu)) i = k;
        double f = i < 0 || ls.size() <= 1 ? 0 : Math.max(0, (i - 4.0) / Math.max(1, ls.size() - 9.0));
        double alvo = Math.min(1, f);
        Platform.runLater(() -> rolTabela.setVvalue(alvo));
    }

    /** Um confronto do mata-mata: escudos, agregado, a ida e a situação ("Precisa de 1 gol"). */
    private static Node confronto(Mundo m, MataMataAoVivo mm, MataMataAoVivo.Situacao s, String meu) {
        HBox a = Ui.clube(m, s.a(), 18), b = Ui.clube(m, s.b(), 18);
        a.setMinWidth(0);
        b.setMinWidth(0);
        aDireita(b);
        HBox.setHgrow(a, Priority.ALWAYS);
        HBox.setHgrow(b, Priority.ALWAYS);
        a.setMaxWidth(Double.MAX_VALUE);
        b.setMaxWidth(Double.MAX_VALUE);
        Label placar = Ui.rotulo(s.golsA() + " x " + s.golsB(), "confronto-agregado");
        placar.setMinWidth(Region.USE_PREF_SIZE);
        if (s.pernas() > 1) Tooltip.install(placar, new Tooltip(s.decisivo() ? "Agregado (com o placar de agora)" : "Placar da ida até agora"));
        HBox topo = new HBox(8, a, placar, b);
        topo.setAlignment(Pos.CENTER);
        for (Node n : List.of(a, b)) {
            if (!(n instanceof HBox h)) continue;
            String id = n == a ? s.a() : s.b();
            for (Node f : h.getChildren()) {
                if (!(f instanceof Label nome)) continue;
                if (id.equals(meu)) nome.setStyle("-fx-font-weight: bold; -fx-text-fill: -acento-nav;");
                else if (id.equals(s.passa())) nome.setStyle("-fx-font-weight: bold;");
            }
        }
        String det;
        if (s.ida() != null) det = "Ida " + s.ida()[0] + " x " + s.ida()[1] + " · agregado";
        else det = s.pernas() > 1 ? "Jogo de ida" : "Jogo único";
        if (s.decisivo() && s.passa() != null) det += " · passa " + Textos.nomeClube(m, s.passa());
        else if (s.decisivo() && !s.envolve(meu)) det += " · " + s.desempate();
        Label info = Ui.rotulo(det, "texto-pequeno");
        info.setMaxWidth(Double.MAX_VALUE);
        info.setAlignment(Pos.CENTER);
        VBox v = new VBox(4, topo, info);
        v.getStyleClass().add("confronto-vivo");
        if (s.envolve(meu)) {
            v.getStyleClass().add("meu"); // sem frase de situação (regra do jogo 13/11): só o agregado
        }
        return v;
    }

    /** Escudo à direita do nome (o visitante, encostado na direita). */
    private static void aDireita(HBox clube) {
        List<Node> filhos = new java.util.ArrayList<>(clube.getChildren());
        java.util.Collections.reverse(filhos);
        clube.getChildren().setAll(filhos);
        clube.setAlignment(Pos.CENTER_RIGHT);
    }

    /**
     * A grade da tabela ao vivo (posição na cor da zona, seta de sobe/desce, clube, J, SG, P) e a
     * legenda das zonas. O seu time com o fundo destacado; quem está jogando agora ({@code jogando}) em negrito.
     */
    static VBox gradeTabela(Mundo m, TabelaAoVivo tv, List<TabelaAoVivo.Linha> ls, String meu, Set<String> jogando) {
        return gradeTabela(m, tv, ls, meu, jogando, false);
    }

    /**
     * {@code grande}: a da camada de trocas, com letra maior e clara, colunas J/SG/P de largura fixa e
     * as linhas crescendo para ocupar a altura do painel.
     */
    static VBox gradeTabela(Mundo m, TabelaAoVivo tv, List<TabelaAoVivo.Linha> ls, String meu, Set<String> jogando, boolean grande) {
        Zonas zonas = Zonas.de(tv.torneio, tv.fase, ls.size());
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(3);
        g.getStyleClass().add("tabela-vivo");
        if (grande) g.getStyleClass().add("tabela-vivo-grande");
        g.setMaxWidth(Double.MAX_VALUE);
        double[] fixa = {grande ? 30 : -1, 14, -1, grande ? 30 : -1, grande ? 38 : -1, grande ? 40 : -1};
        for (int c = 0; c < 6; c++) {
            javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints();
            if (c == 2) {
                cc.setHgrow(Priority.ALWAYS);
                cc.setMinWidth(60);
            } else if (fixa[c] > 0) {
                cc.setMinWidth(fixa[c]);
                cc.setPrefWidth(fixa[c]);
                cc.setHalignment(c == 0 ? javafx.geometry.HPos.CENTER : javafx.geometry.HPos.RIGHT);
            } else {
                cc.setMinWidth(Region.USE_PREF_SIZE);
            }
            g.getColumnConstraints().add(cc);
        }
        if (grande) {
            g.setVgap(0);
            g.setMaxHeight(22 + ls.size() * 46.0); // linhas de até 46 px; o que sobrar fica em volta (centralizada)
            for (int i = 0; i <= ls.size(); i++) {
                javafx.scene.layout.RowConstraints rc = new javafx.scene.layout.RowConstraints();
                rc.setMinHeight(i == 0 ? 22 : 27);
                rc.setPrefHeight(i == 0 ? 22 : 30);
                rc.setMaxHeight(i == 0 ? 22 : 46);
                rc.setVgrow(i == 0 ? Priority.NEVER : Priority.ALWAYS);
                rc.setValignment(javafx.geometry.VPos.CENTER);
                g.getRowConstraints().add(rc);
            }
        }
        String cab = grande ? "tabela-cab" : "texto-pequeno";
        g.addRow(0, Ui.rotulo("#", cab), new Region(), Ui.rotulo("Clube", cab), Ui.rotulo("J", cab), Ui.rotulo("SG", cab), Ui.rotulo("P", cab));
        int r = 1;
        for (TabelaAoVivo.Linha l : ls) {
            boolean eMeu = l.l().clubeId.equals(meu);
            if (eMeu) {
                // fundo da linha inteira atrás das células
                Region fundo = new Region();
                fundo.getStyleClass().add("tabela-vivo-meu");
                if (grande) fundo.getStyleClass().add("forte");
                fundo.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                g.add(fundo, 0, r, 6, 1);
                GridPane.setMargin(fundo, grande ? new javafx.geometry.Insets(1, -6, 1, -6) : new javafx.geometry.Insets(-2, -4, -2, -4));
            }
            Label pos = Ui.rotulo(String.valueOf(l.posicao()), "zona");
            Zonas.Zona zz = zonas.porPosicao.get(l.posicao());
            if (zz != null) pos.getStyleClass().add(zz.classe());
            int d = l.variacao();
            Label seta = Ui.rotulo(d > 0 ? "▲" : d < 0 ? "▼" : "", d > 0 ? "seta-sobe" : "seta-desce");
            seta.setMinWidth(12);
            if (d != 0) Tooltip.install(seta, new Tooltip((d > 0 ? "Subiu " : "Caiu ") + Math.abs(d)));
            HBox cl = Ui.clube(m, l.l().clubeId, grande ? 20 : 16);
            cl.setMinWidth(0);
            if (cl.getChildren().size() > 1 && cl.getChildren().get(1) instanceof Label nome) {
                if (grande) nome.getStyleClass().add("tabela-nome");
                if (eMeu) nome.setStyle(grande ? "-fx-font-weight: bold; -fx-text-fill: white;" : "-fx-font-weight: bold; -fx-text-fill: -acento-nav;");
                else if (jogando.contains(l.l().clubeId)) nome.setStyle("-fx-font-weight: bold;");
            }
            Label pts = Ui.rotulo(String.valueOf(l.l().pontos), "pontos-destaque");
            pts.setStyle("-fx-font-weight: bold;");
            cl.setMaxWidth(Double.MAX_VALUE);
            String num = grande ? "tabela-num" : "numero";
            Node[] cel = {pos, seta, cl, Ui.rotulo(String.valueOf(l.l().jogos), num), Ui.rotulo(String.valueOf(l.l().saldo()), num), pts};
            for (int c = 0; c < cel.length; c++) g.add(cel[c], c, r);
            r++;
            GridPane.setHgrow(cl, Priority.ALWAYS);
        }
        VBox v = new VBox(8, g);
        if (grande) {
            VBox.setVgrow(g, Priority.ALWAYS);
            v.setAlignment(Pos.CENTER);
        }
        if (!zonas.legenda.isEmpty()) {
            FlowPane leg = new FlowPane(10, 4);
            zonas.legenda.forEach((classe, rot) -> {
                Label q = Ui.rotulo(" ", "zona", classe);
                q.setMinSize(10, 10);
                q.setPrefSize(10, 10);
                leg.getChildren().add(Ui.linha(4, q, Ui.rotulo(rot, "texto-pequeno")));
            });
            v.getChildren().add(leg);
        }
        return v;
    }

    /** Partida do motor com esses dois clubes mais perto da semana atual (para a tabela ao vivo). */
    static Partida acharPartida(Jogo jogo, String idM, String idV) {
        Partida melhor = null;
        int sem = jogo.semana();
        for (Torneio t : jogo.competicoesDoAno()) {
            for (Partida p : t.partidas) {
                if (!idM.equals(p.mandante) || !idV.equals(p.visitante)) continue;
                if (melhor == null || Math.abs(p.semana - sem) < Math.abs(melhor.semana - sem)) melhor = p;
            }
        }
        return melhor;
    }
}
