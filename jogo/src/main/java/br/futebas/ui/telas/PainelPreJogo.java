package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.partida.Escalado;
import br.futebas.partida.Escalador;
import br.futebas.partida.TimePartida;
import br.futebas.ui.App;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Tema;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pré-jogo (estilo dos jogos de técnico atuais): escudos grandes, competição e fase com o troféu,
 * estádio e data, forma e posição na tabela, confrontos recentes, desfalques, as escalações
 * (a sua e a provável do adversário), força por setor e as ações: Escalação, auxiliar, JOGAR.
 */
final class PainelPreJogo {
    private PainelPreJogo() {}

    /**
     * Um jogador na escalação do pré-jogo (clube: os 11; Kings: os 7). Gancho comum: a mesma tela
     * serve ao futebol e à Kings ("a Kings também é um futebol").
     */
    record Linha(br.futebas.mundo.Posicao pos, Integer jogadorId, String nome, int forca, boolean lesionado) {}

    static Node construir(Hub hub, Partida p, Runnable voltar) {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        Clube meu = hub.clube();
        Torneio t = hub.sessao().fluxo.torneioDe(p);
        Clube a = m.clube(p.mandante), b = m.clube(p.visitante);
        // Kings League: os mesmos quadros, com os 7 de cada time
        boolean kings = a == null && b == null && j.timeKings(p.mandante) != null;
        String meuId = meu != null ? meu.id : kings && j.meuTimeKings() != null ? j.meuTimeKings().id : null;
        List<Linha> ta = kings ? linhasKings(j, p.mandante) : linhas(time(m, a, meu));
        List<Linha> tb = kings ? linhasKings(j, p.visitante) : linhas(time(m, b, meu));

        // ---- topo: escudos, competição, data e estádio
        String fase = t == null ? "" : Textos.fase(t, p);
        VBox centro = new VBox(6);
        centro.setAlignment(Pos.CENTER);
        if (t != null) centro.getChildren().add(Ui.trofeu(t.id, t.ambito, t.pais, 58));
        Label comp = t == null ? Ui.rotulo("Amistoso", "prejogo-comp") : Ui.nomeCompeticao(t.id, Textos.curto(t.nome), "prejogo-comp");
        centro.getChildren().add(comp);
        if (!fase.isEmpty()) centro.getChildren().add(Ui.rotulo(fase, "prejogo-fase"));
        boolean neutro = t != null && t.finalNeutra && p.rodadaMata >= 0 && p.fase == t.fases.size() - 1;
        String estadio = neutro ? "Campo neutro" : a != null && a.estadio != null ? a.estadio : "";
        centro.getChildren().add(Ui.rotulo(Textos.dataLonga(p.data()) + (estadio.isEmpty() ? "" : "  ·  " + estadio), "prejogo-data"));
        centro.setMinWidth(260);
        HBox topo = new HBox(20, lado(j, m, p.mandante, t, p, ta), centro, lado(j, m, p.visitante, t, p, tb));
        topo.setAlignment(Pos.CENTER);
        topo.getStyleClass().add("prejogo-topo");
        if (meu != null) topo.setStyle(Tema.estiloCabecalho(meu.cor1, meu.cor2));
        else if (kings && j.meuTimeKings() != null) topo.setStyle(Tema.estiloCabecalho(j.meuTimeKings().cor1, j.meuTimeKings().cor2));

        // ---- colunas: escalação | comparação e confrontos | escalação
        VBox colA = new VBox(12, escalacao(ta, p.mandante.equals(meuId)), desfalques(j, m, p.mandante));
        VBox colB = new VBox(12, escalacao(tb, p.visitante.equals(meuId)), desfalques(j, m, p.visitante));
        VBox meio = new VBox(12, setores(ta, tb), confrontos(j, m, p.mandante, p.visitante, meuId));
        Region empurra = new Region();
        VBox.setVgrow(empurra, Priority.ALWAYS);
        meio.getChildren().add(empurra);
        for (VBox v : List.of(colA, colB)) {
            v.setPrefWidth(300);
            v.setMinWidth(260);
        }
        HBox.setHgrow(meio, Priority.ALWAYS);
        meio.setMinWidth(300);
        HBox colunas = new HBox(16, colA, meio, colB);

        // ---- ações
        Button esc = Ui.botaoTecla("Escalação", "F4", () -> hub.selecionar("escalacao"));
        ToggleButton aux = new ToggleButton("Delegar ao auxiliar");
        aux.setSelected(j.delegadoAoAuxiliar(p.torneioId, p.id));
        aux.setFocusTraversable(false);
        aux.getStyleClass().add("alternar");
        aux.setOnAction(e -> j.delegarAoAuxiliar(p.torneioId, p.id, aux.isSelected()));
        Button jogar = Ui.botao("JOGAR  ›", () -> App.atual().jogar(), "jogar-grande");
        jogar.setFocusTraversable(false);
        // ações no meio, embaixo, entre os quadros de desfalques (regra do jogo): JOGAR em destaque e,
        // abaixo, Escalação e auxiliar do mesmo tamanho; Voltar discreto
        Button volta = Ui.botaoTecla("‹  Voltar", "Esc", voltar);
        esc.setText("Escalação");
        for (javafx.scene.control.ButtonBase bt : List.of(esc, aux, volta)) {
            bt.getStyleClass().add("prejogo-botao");
            bt.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(bt, Priority.ALWAYS);
        }
        jogar.setMaxWidth(Double.MAX_VALUE);
        jogar.getStyleClass().add("prejogo-jogar");
        HBox secundarios = kings ? new HBox(8, volta, esc) : new HBox(8, volta, esc, aux);
        VBox acoes = new VBox(10, jogar, secundarios);
        acoes.getStyleClass().addAll("painel", "prejogo-acoes");
        meio.getChildren().add(acoes);

        VBox v = Ui.conteudo(topo, colunas);
        return Ui.rolar(v);
    }

    private static TimePartida time(Mundo m, Clube c, Clube meu) {
        if (c == null) return null;
        try {
            return c == meu ? Escalador.montar(m, c, 5) : Escalador.montarAutomatico(m, c, 5);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static VBox lado(Jogo j, Mundo m, String id, Torneio t, Partida p, List<Linha> escalados) {
        VBox v = new VBox(6);
        v.setAlignment(Pos.CENTER);
        v.setPrefWidth(280);
        v.setMinWidth(200);
        HBox.setHgrow(v, Priority.ALWAYS);
        v.getChildren().add(Ui.escudo(id, 110));
        Label nome = Ui.nomeClube(id, m.nomeParticipante(id), "prejogo-time");
        nome.setWrapText(true);
        nome.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        nome.setAlignment(Pos.CENTER);
        // duas linhas reservadas: os dois lados do cabeçalho ficam alinhados com nomes longos
        javafx.scene.layout.StackPane caixaNome = new javafx.scene.layout.StackPane(nome);
        caixaNome.setMinHeight(64);
        caixaNome.setPrefHeight(64);
        caixaNome.setMaxHeight(64);
        v.getChildren().add(caixaNome);
        Clube c = m.clube(id);
        if (c != null) {
            String pos = posicao(j, c, t, p);
            HBox info = Ui.linha(8, Ui.forca((int) Math.round(Escalador.forcaTime(m, c))));
            if (pos != null) info.getChildren().add(Ui.rotulo(pos, "prejogo-pos"));
            info.setAlignment(Pos.CENTER);
            HBox forma = Ui.forma(c.forma, 5);
            forma.setAlignment(Pos.CENTER);
            v.getChildren().addAll(info, forma);
        } else if (escalados != null && !escalados.isEmpty()) {
            // Kings: força dos 7 e a posição na tabela da competição
            double f = 0;
            for (Linha l : escalados) f += l.forca();
            HBox info = Ui.linha(8, Ui.forca((int) Math.round(f / escalados.size())));
            String pos = posicaoNaTabela(t, p, id);
            if (pos != null) info.getChildren().add(Ui.rotulo(pos, "prejogo-pos"));
            info.setAlignment(Pos.CENTER);
            v.getChildren().add(info);
        }
        return v;
    }

    /** "3º · 12 pts" na tabela da fase de pontos desta competição (clube ou Kings). */
    private static String posicaoNaTabela(Torneio t, Partida p, String id) {
        if (t == null || p.fase < 0 || p.fase >= t.fases.size()) return null;
        FaseExec f = t.fases.get(p.fase);
        if (f.mataMata()) return null;
        List<LinhaTabela> ls = t.tabela(f.indice, Math.max(0, p.grupo));
        for (int i = 0; i < ls.size(); i++) if (ls.get(i).clubeId.equals(id)) return (i + 1) + "º" + (ls.get(i).jogos == 0 ? "" : "  ·  " + ls.get(i).pontos + " pts");
        return null;
    }

    /** Os 11 do clube (o time que o motor monta). */
    private static List<Linha> linhas(TimePartida tp) {
        if (tp == null) return null;
        List<Linha> l = new ArrayList<>();
        for (Escalado e : tp.titulares) l.add(new Linha(e.vaga, e.id < 0 ? null : e.id, e.nome, (int) Math.round(e.forca), false));
        return l;
    }

    /** Os 7 de um time da Kings: a escalação do técnico ou, sem ela, os 7 mais fortes (goleiro primeiro), nas vagas da formação. */
    private static List<Linha> linhasKings(Jogo j, String timeId) {
        br.futebas.kings.LigaKings.TimeKings tk = j.timeKings(timeId);
        if (tk == null) return null;
        List<br.futebas.kings.LigaKings.AtletaKings> sete = br.futebas.kings.MotorKings.escalados(tk);
        if (sete == null) {
            sete = new ArrayList<>(tk.elenco);
            sete.sort(Comparator.comparingDouble((br.futebas.kings.LigaKings.AtletaKings x) -> -x.forca));
            for (int i = 0; i < sete.size(); i++) if (sete.get(i).posicao == br.futebas.mundo.Posicao.GOL) {
                sete.add(0, sete.remove(i));
                break;
            }
        }
        List<br.futebas.mundo.Posicao> vagas;
        try {
            vagas = j.vagasKings(tk.formacao);
        } catch (RuntimeException e) {
            vagas = List.of();
        }
        List<Linha> l = new ArrayList<>();
        for (int i = 0; i < Math.min(7, sete.size()); i++) {
            br.futebas.kings.LigaKings.AtletaKings x = sete.get(i);
            br.futebas.mundo.Posicao pos = i < vagas.size() ? vagas.get(i) : x.posicao != null ? x.posicao : br.futebas.mundo.Posicao.MC;
            Jogador jg = x.jogadorId == null ? null : j.mundo().jogador(x.jogadorId);
            l.add(new Linha(pos, x.jogadorId, jg != null ? jg.nomeExibicao() : x.nome, (int) Math.round(x.forca), false));
        }
        return l;
    }

    /** "3º" na tabela desta competição (fase de pontos) ou na liga nacional do clube. */
    private static String posicao(Jogo j, Clube c, Torneio t, Partida p) {
        if (t != null && p.fase >= 0 && p.fase < t.fases.size()) {
            FaseExec f = t.fases.get(p.fase);
            if (!f.mataMata()) {
                List<LinhaTabela> ls = t.tabela(f.indice, Math.max(0, p.grupo));
                for (int i = 0; i < ls.size(); i++) if (ls.get(i).clubeId.equals(c.id)) return (i + 1) + "º" + (ls.get(i).jogos == 0 ? "" : "  ·  " + ls.get(i).pontos + " pts");
            }
        }
        for (Torneio x : j.competicoesDoAno()) {
            if (!"nacional".equals(x.ambito) || !c.pais.equals(x.pais) || x.divisao == null || x.divisao != c.divisao) continue;
            List<LinhaTabela> ls = j.tabela(x.id);
            for (int i = 0; i < ls.size(); i++) if (ls.get(i).clubeId.equals(c.id)) return (i + 1) + "º " + Textos.curto(x.nome);
        }
        return null;
    }

    private static VBox escalacao(List<Linha> tp, boolean meu) {
        VBox v = Ui.painel(meu ? "Seu time" : "Provável escalação");
        if (tp == null || tp.isEmpty()) {
            v.getChildren().add(Ui.rotulo("—", "texto-suave"));
            return v;
        }
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(4);
        int r = 0;
        for (Linha e : tp) {
            Label n = e.jogadorId() == null ? Ui.rotulo(e.nome()) : Ui.nomeJogador(e.jogadorId(), e.nome());
            n.setMinWidth(0);
            HBox nome = Ui.linha(4, n);
            if (e.lesionado()) nome.getChildren().add(CampoEscalacao.cruzMedica());
            g.addRow(r++, Ui.pos(e.pos()), nome, Ui.forca(e.forca()));
            GridPane.setHgrow(nome, Priority.ALWAYS);
        }
        v.getChildren().add(g);
        return v;
    }

    private static VBox desfalques(Jogo j, Mundo m, String id) {
        VBox v = Ui.painel("Desfalques");
        List<Jogador> l = new ArrayList<>();
        try {
            for (Jogador x : j.elenco(id)) if (x.lesaoSemanas > 0 || x.suspensao > 0) l.add(x);
        } catch (RuntimeException e) {
            // seleção ou time sem elenco
        }
        l.sort(Comparator.comparingDouble((Jogador x) -> -x.forca));
        for (Jogador x : l) {
            Label n = Ui.nomeJogador(x.id, x.nome);
            n.setMinWidth(0);
            String motivo = x.suspensao > 0 ? "Suspenso" : (x.lesao == null ? "Lesão" : x.lesao) + " · " + x.lesaoSemanas + " sem.";
            HBox h = Ui.linha(8, Ui.pos(x.posicao), n);
            if (x.suspensao <= 0 && x.lesaoSemanas > 0) h.getChildren().add(CampoEscalacao.cruzMedica());
            h.getChildren().addAll(Ui.espaco(), Ui.etiqueta(motivo, x.suspensao > 0 ? "ambar" : "vermelha"));
            v.getChildren().add(h);
        }
        if (l.isEmpty()) v.getChildren().add(Ui.rotulo("Nenhum", "texto-suave"));
        return v;
    }

    private static VBox setores(List<Linha> a, List<Linha> b) {
        VBox v = Ui.painel("Força por setor");
        double[] fa = setores(a), fb = setores(b);
        String[] nomes = {"Defesa", "Meio", "Ataque", "Geral"};
        for (int i = 0; i < 4; i++) {
            v.getChildren().add(Ui.comparacao(nomes[i], fa[i], fb[i], String.valueOf(Math.round(fa[i])), String.valueOf(Math.round(fb[i]))));
        }
        return v;
    }

    /** Média de força por setor: {defesa (com o goleiro), meio, ataque, geral}. */
    private static double[] setores(List<Linha> tp) {
        double[] s = new double[4];
        int[] n = new int[4];
        if (tp == null) return s;
        for (Linha e : tp) {
            int k = e.pos() == null ? 1 : switch (e.pos().setor) {
                case 'G', 'D' -> 0;
                case 'A' -> 2;
                default -> 1;
            };
            s[k] += e.forca();
            n[k]++;
            s[3] += e.forca();
            n[3]++;
        }
        for (int i = 0; i < 4; i++) s[i] = n[i] == 0 ? 0 : s[i] / n[i];
        return s;
    }

    private static VBox confrontos(Jogo j, Mundo m, String a, String b, String meu) {
        VBox v = Ui.painel("Confrontos recentes");
        List<Partida> l = new ArrayList<>();
        List<Torneio> ts = new ArrayList<>(m.torneiosAnteriores);
        ts.addAll(m.torneios);
        try {
            ts.addAll(j.competicoesKings());
        } catch (RuntimeException e) {
            // sem Kings
        }
        for (Torneio t : ts) {
            for (Partida p : t.partidas) {
                if (!p.jogada || p.golsMandante == null) continue;
                if ((a.equals(p.mandante) && b.equals(p.visitante)) || (b.equals(p.mandante) && a.equals(p.visitante))) l.add(p);
            }
        }
        l.sort(Comparator.comparing(Partida::data));
        int v1 = 0, e = 0, v2 = 0;
        for (Partida p : l) {
            int ga = a.equals(p.mandante) ? p.golsMandante : p.golsVisitante, gb = a.equals(p.mandante) ? p.golsVisitante : p.golsMandante;
            if (ga > gb) v1++;
            else if (ga == gb) e++;
            else v2++;
        }
        if (!l.isEmpty()) {
            HBox resumo = Ui.linha(18, Ui.dado("Vitórias", Ui.rotulo(String.valueOf(v1), "numero-grande")), Ui.espaco(),
                    Ui.dado("Empates", Ui.rotulo(String.valueOf(e), "numero-grande")), Ui.espaco(),
                    Ui.dado("Vitórias", Ui.rotulo(String.valueOf(v2), "numero-grande")));
            v.getChildren().add(resumo);
        }
        for (int i = l.size() - 1, k = 0; i >= 0 && k < 5; i--, k++) {
            Partida p = l.get(i);
            HBox h = TelaPartida.linhaResultado(m, p, meu);
            Label d = Ui.rotulo(Textos.dataCurta(p.data()), "texto-pequeno");
            d.setMinWidth(70);
            h.getChildren().add(0, d);
            v.getChildren().add(h);
        }
        if (l.isEmpty()) v.getChildren().add(Ui.rotulo("Primeiro confronto", "texto-suave"));
        return v;
    }
}
