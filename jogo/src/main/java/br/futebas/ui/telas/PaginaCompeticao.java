package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.dados.Db;
import br.futebas.dados.Formato;
import br.futebas.historia.Historia;
import br.futebas.kings.LigaKings;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Página de uma competição (liga, copa, estadual, continental, seleções, Kings): faixa com o
 * troféu e a temporada, e as abas Visão geral, Tabela/Chaveamento, Jogos, Estatísticas,
 * Campeões e Formato.
 */
final class PaginaCompeticao {
    private PaginaCompeticao() {}

    /** Página da competição {@code id}; {@code edicao} escolhe a temporada (null = a atual). */
    static Node construir(Hub hub, String id, Torneio edicao, Runnable voltar) {
        StackPane holder = new StackPane();
        holder.getChildren().setAll(pagina(hub, id, edicao, voltar, holder));
        return holder;
    }

    private static Node pagina(Hub hub, String id, Torneio edicao, Runnable voltar, StackPane holder) {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        List<Torneio> edicoes = j.edicoes(id);
        Torneio t = edicao != null ? edicao : j.competicaoQualquer(id);
        Db.Competicao dc = j.competicaoDoBanco(id);
        if (t == null && dc == null) return Ui.conteudo(Ui.vazio("Competição não encontrada.", "Voltar", voltar));
        String ambito = t != null ? t.ambito : dc.ambito();
        String pais = t != null ? t.pais : dc.pais();
        String nomeCompleto = t != null ? t.nome : dc.nome();
        String nome = Textos.curto(nomeCompleto);
        LigaKings liga = j.ligaKingsDaCompeticao(t);
        String meuId = meuId(j, ambito);
        Function<String, String> nomeDe = j::nomeParticipante;

        // ---------------------------------------------------------------- faixa
        String origem;
        Node bandeira = null;
        if ("kings".equals(ambito)) origem = liga != null ? Textos.ligaKings(liga.id, liga.nome) : "Kings League";
        else if (pais != null) {
            origem = Textos.pais(pais) + (t != null && t.uf != null ? " · " + t.uf : dc != null && dc.uf() != null ? " · " + dc.uf() : "");
            bandeira = Imagens.bandeira(pais, 14);
        } else origem = t != null && t.confederacao != null ? t.confederacao : dc != null && dc.confederacao() != null ? dc.confederacao() : "Internacional";
        if (liga != null && liga.pais != null) bandeira = Imagens.bandeira(liga.pais, 14);
        String tipo = tipoCompeticao(t, ambito, dc);
        HBox sub = Pagina.origem(bandeira, origem + "  ·  " + tipo);
        List<Node> blocos = new ArrayList<>();
        if (edicoes.size() > 1 && t != null) {
            ComboBox<Torneio> temp = new ComboBox<>();
            temp.getItems().setAll(edicoes);
            temp.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(Torneio x) { return x == null ? "" : temporada(x); }

                @Override
                public Torneio fromString(String s) { return null; }
            });
            temp.getSelectionModel().select(t);
            temp.valueProperty().addListener((o, a, b) -> {
                if (b != null && b != a) holder.getChildren().setAll(pagina(hub, id, b, voltar, holder));
            });
            blocos.add(Pagina.bloco(temp, "Temporada"));
        } else if (t != null) blocos.add(Pagina.bloco(temporada(t), "Temporada"));
        if (t != null) {
            blocos.add(Pagina.bloco(String.valueOf(t.participantes.size()), "Participantes"));
            blocos.add(Pagina.bloco(status(t), "Situação"));
            String campId = t.campeao;
            String rot = "Campeão";
            if (campId == null) {
                List<Jogo.CampeaoInfo> cs = campeoes(j, id);
                if (!cs.isEmpty()) {
                    campId = cs.get(cs.size() - 1).campeaoId();
                    rot = "Último campeão · " + cs.get(cs.size() - 1).temporada();
                }
            }
            if (campId != null) blocos.add(Pagina.bloco(participante(m, campId, nomeDe, 22, "cab-valor-medio"), rot));
        } else {
            List<Jogo.CampeaoInfo> cs = campeoes(j, id);
            if (!cs.isEmpty()) {
                Jogo.CampeaoInfo c = cs.get(cs.size() - 1);
                Node v = c.campeaoId() != null ? participante(m, c.campeaoId(), x -> c.campeao(), 22, "cab-valor-medio") : Ui.rotulo(c.campeao(), "cab-valor-medio");
                blocos.add(Pagina.bloco(v, "Último campeão · " + c.temporada()));
            }
        }
        VBox cab = Pagina.cabecalho(Imagens.trofeu(id, ambito, pais, 76), nome, sub, null, null, voltar, blocos.toArray(new Node[0]));

        // ---------------------------------------------------------------- abas
        List<Tab> tabs = new ArrayList<>();
        // só a aba aberta é montada; as outras na primeira vez que forem escolhidas (abria travando)
        if (t != null) {
            tabs.add(Pagina.abaSob("Visão geral", () -> visaoGeral(j, m, t, liga, nomeDe, meuId)));
            List<FaseExec> pontos = new ArrayList<>(), mata = new ArrayList<>();
            for (FaseExec f : t.fases) (f.mataMata() ? mata : pontos).add(f);
            if (!pontos.isEmpty()) tabs.add(Pagina.abaSob(pontos.stream().anyMatch(f -> f.numGrupos > 1) ? "Grupos" : "Tabela", () -> tabelas(m, t, pontos, nomeDe, meuId)));
            if (!mata.isEmpty()) {
                tabs.add(Pagina.abaFixaSob("Chaveamento", () -> {
                    ScrollPane sp = new ScrollPane(PainelTabelas.chaveamento(t, mata, nomeDe, meuId));
                    sp.setFitToHeight(false);
                    return sp;
                }));
            }
            tabs.add(Pagina.abaFixaSob("Jogos", () -> {
                Node jogos = PainelTabelas.jogos(t, nomeDe, meuId);
                VBox jv = new VBox(jogos);
                VBox.setVgrow(jogos, Priority.ALWAYS);
                jv.setStyle("-fx-padding: 4 2 12 2;");
                return jv;
            }));
            tabs.add(Pagina.abaSob("Estatísticas", () -> estatisticas(j, m, t, liga, nomeDe)));
        }
        tabs.add(Pagina.abaFixaSob("Campeões", () -> {
            Node camp = PainelTabelas.campeoes(j, id, ambito, pais);
            VBox cv = new VBox(camp);
            VBox.setVgrow(camp, Priority.ALWAYS);
            cv.setStyle("-fx-padding: 4 2 12 2;");
            return cv;
        }));
        tabs.add(Pagina.abaSob("Formato", () -> formato(t, liga, dc)));
        return Pagina.montar(cab, Pagina.abas("competicao", tabs.toArray(new Tab[0])));
    }

    private static String meuId(Jogo j, String ambito) {
        Mundo m = j.mundo();
        if ("kings".equals(ambito)) return m.tecnico == null ? null : m.tecnico.kingsTimeId;
        if ("selecoes".equals(ambito)) {
            var s = j.minhaSelecao();
            return s == null ? null : br.futebas.mundo.Selecao.id(s.pais);
        }
        return j.meuClube() == null ? null : j.meuClube().id;
    }

    private static List<Jogo.CampeaoInfo> campeoes(Jogo j, String id) {
        try {
            return j.campeoes(id);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    static String temporada(Torneio t) {
        return t.temporada != null ? t.temporada : String.valueOf(t.ano);
    }

    private static String status(Torneio t) {
        if (t.campeao != null || t.encerrado) return "Encerrado";
        FaseExec f = t.faseAtual();
        if (f != null && (f.iniciada || f.indice > 0)) return f.nome == null ? "Em andamento" : f.nome;
        return "A começar";
    }

    private static String tipoCompeticao(Torneio t, String ambito, Db.Competicao dc) {
        Integer div = t != null ? t.divisao : dc == null ? null : dc.divisao();
        String id = t != null ? t.id : dc == null ? null : dc.id();
        // jogos únicos fictícios entre campeões (requisito: aparecer como Mundial/Intercontinental)
        if (br.futebas.competicao.CompeticoesFicticias.TACA_ATLANTICA.equals(id)) return "Jogo único · campeão da Copa Cóndor × campeão da Conference League";
        if (br.futebas.competicao.CompeticoesFicticias.DESAFIO.equals(id)) return "Jogo único · campeão da Sul-Americana × campeão da Europa League";
        return switch (ambito == null ? "" : ambito) {
            case "nacional" -> div == null ? "Liga" : div + "ª divisão";
            case "copa_nacional" -> "Copa";
            case "estadual" -> div == null || div <= 1 ? "Estadual" : "Estadual · " + div + "ª divisão";
            case "continental" -> "Continental";
            case "mundial" -> "Mundial";
            case "selecoes" -> "Seleções";
            case "kings" -> "Kings League";
            case "amistoso" -> "Amistoso";
            default -> "Competição";
        };
    }

    /** Escudo + nome de participante que abre a página dele. */
    private static HBox participante(Mundo m, String id, Function<String, String> nome, double altura, String classe) {
        Label n = Ui.nomeClube(id, nome.apply(id), classe);
        n.setMinWidth(0);
        return Ui.linha(8, Ui.escudo(id, altura), n);
    }

    // ================================================================== visão geral
    private static Node visaoGeral(Jogo j, Mundo m, Torneio t, LigaKings liga, Function<String, String> nome, String meuId) {
        // esquerda: a fase em andamento (tabela compacta, grupos ou chaveamento)
        VBox esq = new VBox(16);
        FaseExec f = t.faseAtual();
        if (f == null || (!f.iniciada && f.indice == 0 && !t.encerrado)) {
            if (t.fases.isEmpty() || !t.fases.get(0).iniciada) f = null;
        }
        if (f == null && t.encerrado && !t.fases.isEmpty()) f = t.fases.get(t.fases.size() - 1);
        if (f == null) {
            FlowPane fp = new FlowPane(14, 10);
            for (String p : t.participantes) fp.getChildren().add(participante(m, p, nome, 22, "label"));
            esq.getChildren().add(Ui.painel("Participantes", fp));
        } else if (f.mataMata() || ((t.encerrado || f.clubes.size() <= 2 && f.indice > 0) && t.fases.stream().anyMatch(FaseExec::mataMata))) {
            List<FaseExec> mata = new ArrayList<>();
            for (FaseExec x : t.fases) if (x.mataMata()) mata.add(x);
            if (!f.mataMata()) f = mata.get(mata.size() - 1);
            ScrollPane sp = new ScrollPane(PainelTabelas.chaveamento(t, mata, nome, meuId));
            sp.setFitToHeight(true);
            sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            sp.setPrefViewportHeight(Math.min(560, 70 + 64 * Math.max(1, primeiraRodadaMata(mata))));
            esq.getChildren().add(Ui.painel(t.encerrado || f.nome == null ? "Chaveamento" : f.nome, sp));
        } else if (f.numGrupos > 1) {
            esq.getChildren().add(Ui.painel(f.nome == null ? "Grupos" : f.nome, PainelTabelas.conteudoFase(m, t, f, nome, meuId)));
        } else {
            List<LinhaTabela> linhas = t.tabela(f.indice, 0);
            Zonas z = Zonas.de(t, f, linhas.size());
            int classificam = t.fases.size() > f.indice + 1 ? f.saida : 0;
            if (liga != null && classificam == 0) classificam = liga.regras.classificados_playoff;
            TableView<LinhaTabela> tv = PainelTabelas.tabelaClassificacao(m, t, linhas, nome, meuId, 0, 0, classificam, true, z.vazia() ? null : z);
            VBox p = Ui.painelTabela(f.nome == null ? "Classificação" : f.nome, null, tv);
            esq.getChildren().add(p);
            if (!z.vazia()) esq.getChildren().add(z.legenda());
        }

        // direita: rodadas, artilheiros, notícias
        VBox dir = new VBox(16);
        Map<Integer, List<Partida>> porData = new TreeMap<>();
        for (Partida p : t.partidas) porData.computeIfAbsent(p.data, k -> new ArrayList<>()).add(p);
        List<Partida> ultima = null, proxima = null;
        for (List<Partida> ps : porData.values()) {
            if (ps.stream().anyMatch(p -> p.jogada)) ultima = ps;
            if (proxima == null && ps.stream().anyMatch(p -> !p.jogada)) proxima = ps;
        }
        if (proxima != null) dir.getChildren().add(rodada("Próxima rodada", t, proxima, nome, meuId));
        if (ultima != null && ultima != proxima) dir.getChildren().add(rodada("Última rodada", t, ultima, nome, meuId));
        if (proxima == null && ultima == null) dir.getChildren().add(Ui.painel("Jogos", Ui.rotulo("Jogos ainda não sorteados", "texto-suave")));

        List<Linha> art = artilheiros(m, t, liga, 5);
        if (!art.isEmpty()) {
            GridPane g = new GridPane();
            g.setHgap(12);
            g.setVgap(6);
            int i = 0;
            for (Linha a : art) {
                g.addRow(i, Ui.rotulo((i + 1) + "º", "numero"), a.no(), a.time(), Ui.rotulo(String.valueOf(a.valor()), "num-forte"));
                i++;
            }
            dir.getChildren().add(Ui.painel("Artilheiros", g));
        }
        Node noticias = noticias(m, t);
        if (noticias != null) dir.getChildren().add(noticias);
        HBox h = Pagina.colunas(18, esq, dir);
        esq.setPrefWidth(56);
        dir.setPrefWidth(44);
        return h;
    }

    private static int primeiraRodadaMata(List<FaseExec> mata) {
        int max = 1;
        for (FaseExec f : mata) {
            Map<Integer, Integer> porRodada = new HashMap<>();
            for (var c : f.confrontos) porRodada.merge(c.rodada, 1, Integer::sum);
            for (int v : porRodada.values()) max = Math.max(max, v);
        }
        return max;
    }

    private static VBox rodada(String titulo, Torneio t, List<Partida> ps, Function<String, String> nome, String meuId) {
        Partida p0 = ps.get(0);
        String fase = Textos.fase(t, p0);
        Label sub = Ui.rotulo(Textos.dataCurta(p0.data()) + (fase.isEmpty() ? "" : "  ·  " + fase), "texto-suave");
        List<Partida> l = ps.size() > 12 ? ps.subList(0, 12) : ps;
        return Ui.painelAcoes(titulo, sub, PainelTabelas.listaJogos(l, nome, meuId));
    }

    private static Node noticias(Mundo m, Torneio t) {
        String base = Textos.curto(t.nome).replaceAll("\\s+\\d{4}(/\\d{2})?$", "").trim();
        if (base.length() < 4) return null;
        String chave = base.toLowerCase(java.util.Locale.ROOT);
        List<Historia.Evento> evs = new ArrayList<>();
        List<Historia.Evento> todos = m.historia.linhaDoTempo;
        for (int i = todos.size() - 1; i >= 0 && evs.size() < 6; i--) {
            Historia.Evento e = todos.get(i);
            if (e.texto != null && e.texto.toLowerCase(java.util.Locale.ROOT).contains(chave)) evs.add(e);
        }
        if (evs.isEmpty()) return null;
        VBox v = new VBox(8);
        for (Historia.Evento e : evs) {
            Label txt = Ui.paragrafo(e.texto);
            txt.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(txt, Priority.ALWAYS);
            Label quando = Ui.rotulo(Textos.mes(e.semana) + "/" + e.ano, "texto-pequeno");
            quando.setMinWidth(64);
            v.getChildren().add(Ui.linha(10, quando, txt));
        }
        return Ui.painel("Notícias", v);
    }

    // ================================================================== tabelas
    private static Node tabelas(Mundo m, Torneio t, List<FaseExec> pontos, Function<String, String> nome, String meuId) {
        VBox v = new VBox(18);
        for (FaseExec f : pontos) {
            Node c = PainelTabelas.conteudoFase(m, t, f, nome, meuId);
            if (pontos.size() > 1 || f.nome != null) v.getChildren().add(Ui.rotulo(f.nome == null ? "Fase " + (f.indice + 1) : f.nome, "titulo-painel"));
            v.getChildren().add(c);
        }
        return v;
    }

    // ================================================================== estatísticas
    /** Linha de ranking: nó do nome, nó do time e o valor. */
    private record Linha(Node no, Node time, int valor) {}

    private static List<Linha> artilheiros(Mundo m, Torneio t, LigaKings liga, int n) {
        List<Linha> l = new ArrayList<>();
        for (int[] a : t.artilheiros(n)) {
            Jogador x = m.jogador(a[0]);
            if (x == null) continue;
            Node time = x.clubeId == null ? new Label() : Ui.clube(m, x.clubeId, 16, "texto-suave");
            if (liga != null && x.kingsTimeId != null) time = Ui.clube(m, x.kingsTimeId, 16, "texto-suave");
            l.add(new Linha(Ui.nomeJogador(x.id, x.nomeExibicao(), "texto-forte"), time, a[1]));
        }
        if (l.isEmpty() && liga != null) {
            List<Object[]> ks = new ArrayList<>();
            for (LigaKings.TimeKings tk : liga.times) for (LigaKings.AtletaKings a : tk.elenco) if (a.gols > 0) ks.add(new Object[] {a, tk});
            ks.sort((a, b) -> ((LigaKings.AtletaKings) b[0]).gols - ((LigaKings.AtletaKings) a[0]).gols);
            for (int i = 0; i < Math.min(n, ks.size()); i++) {
                LigaKings.AtletaKings a = (LigaKings.AtletaKings) ks.get(i)[0];
                LigaKings.TimeKings tk = (LigaKings.TimeKings) ks.get(i)[1];
                Jogador x = a.jogadorId == null ? null : m.jogador(a.jogadorId);
                Node no = x != null ? Ui.nomeJogador(x.id, x.nomeExibicao(), "texto-forte") : Ui.nomeAtletaKings(null, tk.id, a.nome, "texto-forte");
                l.add(new Linha(no, Ui.clube(m, tk.id, 16, "texto-suave"), a.gols));
            }
        }
        return l;
    }

    /** Números de cada equipe na competição. */
    static final class Num {
        final String id;
        int j, v, e, d, gp, gc, cartoes;

        Num(String id) { this.id = id; }
    }

    private static Node estatisticas(Jogo j, Mundo m, Torneio t, LigaKings liga, Function<String, String> nome) {
        Map<String, Num> nums = new LinkedHashMap<>();
        int jogos = 0, gols = 0, cartoes = 0;
        long publico = 0;
        int comPublico = 0;
        Partida goleada = null;
        for (Partida p : t.partidas) {
            if (!p.jogada || p.golsMandante == null) continue;
            jogos++;
            gols += p.golsMandante + p.golsVisitante;
            cartoes += p.cartoesMandante + p.cartoesVisitante;
            if (p.publico > 0) {
                publico += p.publico;
                comPublico++;
            }
            if (goleada == null || Math.abs(p.golsMandante - p.golsVisitante) > Math.abs(goleada.golsMandante - goleada.golsVisitante)
                    || (Math.abs(p.golsMandante - p.golsVisitante) == Math.abs(goleada.golsMandante - goleada.golsVisitante)
                    && p.golsMandante + p.golsVisitante > goleada.golsMandante + goleada.golsVisitante)) goleada = p;
            somar(nums.computeIfAbsent(p.mandante, Num::new), p.golsMandante, p.golsVisitante, p.cartoesMandante, Textos.resultadoPara(p, p.mandante));
            somar(nums.computeIfAbsent(p.visitante, Num::new), p.golsVisitante, p.golsMandante, p.cartoesVisitante, Textos.resultadoPara(p, p.visitante));
        }
        if (jogos == 0) return Ui.vazio("Nenhum jogo disputado ainda.", null, null);

        HBox resumo = Ui.linha(28, Ui.dado("Jogos", String.valueOf(jogos)), Ui.dado("Gols", String.valueOf(gols)),
                Ui.dado("Média de gols", Textos.umaCasa(gols / (double) jogos)), Ui.dado("Cartões", String.valueOf(cartoes)),
                Ui.dado("Público médio", comPublico == 0 ? "—" : Textos.milhar(publico / comPublico)));
        if (goleada != null) {
            Partida g = goleada;
            resumo.getChildren().add(Ui.dado("Maior goleada", Ui.linha(6, Ui.escudo(g.mandante, 16),
                    Ui.rotulo(g.golsMandante + " – " + g.golsVisitante, "dado-valor"), Ui.escudo(g.visitante, 16))));
        }
        VBox topo = Ui.painel("Resumo", resumo);

        // rankings
        List<Linha> art = artilheiros(m, t, liga, 15);
        VBox artP = Ui.painel("Artilharia", art.isEmpty() ? Ui.rotulo("Nenhum gol marcado ainda.", "texto-suave") : grade(art, "gols"));

        Node assist = null;
        if (!"kings".equals(t.ambito) && !"selecoes".equals(t.ambito)) {
            List<Jogador> todos = new ArrayList<>();
            for (String c : t.participantes) {
                if (m.clube(c) == null) continue;
                for (Jogador x : j.elenco(c)) if (x.assistTemp > 0) todos.add(x);
            }
            todos.sort((a, b) -> b.assistTemp - a.assistTemp);
            List<Linha> la = new ArrayList<>();
            for (int i = 0; i < Math.min(10, todos.size()); i++) {
                Jogador x = todos.get(i);
                la.add(new Linha(Ui.nomeJogador(x.id, x.nomeExibicao(), "texto-forte"), Ui.clube(m, x.clubeId, 16, "texto-suave"), x.assistTemp));
            }
            if (!la.isEmpty()) assist = Ui.painelAcoes("Assistências", Ui.rotulo("temporada", "texto-suave"), grade(la, "assist."));
        }

        TableView<Num> tv = Ui.tabela();
        Ui.colClube(tv, "Equipe", 230, m, x -> x.id);
        Ui.colNum(tv, "J", 40, x -> x.j);
        Ui.colNum(tv, "V", 40, x -> x.v);
        Ui.colNum(tv, "E", 40, x -> x.e);
        Ui.colNum(tv, "D", 40, x -> x.d);
        Ui.colNum(tv, "GP", 44, x -> x.gp);
        Ui.colNum(tv, "GC", 44, x -> x.gc);
        Ui.colNum(tv, "SG", 44, x -> x.gp - x.gc);
        Ui.colNum(tv, "Gols/J", 60, x -> x.j == 0 ? 0.0 : Math.round(10.0 * x.gp / x.j) / 10.0);
        Ui.colNum(tv, "Cartões", 64, x -> x.cartoes);
        List<Num> ls = new ArrayList<>(nums.values());
        ls.sort((a, b) -> b.gp - a.gp);
        tv.getItems().setAll(ls);
        Ui.alturaPorLinhas(tv, Math.min(24, Math.max(3, ls.size())));
        VBox equipes = Ui.painelTabela("Equipes", null, tv);

        Num ataque = ls.get(0);
        Num defesa = ls.stream().filter(x -> x.j > 0).min((a, b) -> Double.compare(a.gc / (double) a.j, b.gc / (double) b.j)).orElse(ataque);
        Num disc = ls.stream().filter(x -> x.j > 0).min((a, b) -> Double.compare(a.cartoes / (double) a.j, b.cartoes / (double) b.j)).orElse(ataque);
        HBox destaques = Ui.linha(28, Ui.dado("Melhor ataque", Ui.linha(6, participante(m, ataque.id, nome, 18, "dado-valor"), Ui.rotulo(ataque.gp + " gols", "texto-suave"))),
                Ui.dado("Melhor defesa", Ui.linha(6, participante(m, defesa.id, nome, 18, "dado-valor"), Ui.rotulo(defesa.gc + " sofridos", "texto-suave"))),
                Ui.dado("Mais disciplinado", Ui.linha(6, participante(m, disc.id, nome, 18, "dado-valor"), Ui.rotulo(disc.cartoes + " cartões", "texto-suave"))));
        topo.getChildren().addAll(Ui.separadorH(), destaques);

        VBox dir = new VBox(16, artP);
        if (assist != null) dir.getChildren().add(assist);
        HBox meio = Pagina.colunas(18, equipes, dir);
        equipes.setPrefWidth(58);
        dir.setPrefWidth(42);
        return new VBox(16, topo, meio);
    }

    private static void somar(Num n, int pro, int contra, int cartoes, String r) {
        n.j++;
        n.gp += pro;
        n.gc += contra;
        n.cartoes += cartoes;
        if ("V".equals(r)) n.v++;
        else if ("E".equals(r)) n.e++;
        else n.d++;
    }

    private static GridPane grade(List<Linha> l, String unidade) {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(6);
        int i = 0;
        for (Linha a : l) {
            Label pos = Ui.rotulo((i + 1) + "º", "numero");
            pos.setMinWidth(28);
            g.addRow(i, pos, a.no(), a.time(), Ui.rotulo(a.valor() + " " + unidade, "numero"));
            i++;
        }
        return g;
    }

    // ================================================================== formato
    private static Node formato(Torneio t, LigaKings liga, Db.Competicao dc) {
        VBox v = new VBox(16);
        Formato fo = t != null ? t.formato : null;
        HBox geral = Ui.linha(28);
        int part = t != null ? t.participantes.size() : fo != null && fo.participantes != null ? fo.participantes : 0;
        if (part > 0) geral.getChildren().add(Ui.dado("Participantes", String.valueOf(part)));
        if (t != null) {
            geral.getChildren().add(Ui.dado("Fases", String.valueOf(t.fases.size())));
            geral.getChildren().add(Ui.dado("Vitória", t.pontosVitoria + " pontos"));
            if (t.penaltisNoEmpate) geral.getChildren().add(Ui.dado("Empate", "pênaltis (" + t.pontosVitoriaPenaltis + "/" + t.pontosDerrotaPenaltis + " pts)"));
            geral.getChildren().add(Ui.dado("Calendário", calendario(t)));
        }
        if (fo != null && fo.promovidos != null && fo.promovidos > 0 && t != null && t.divisao != null && t.divisao > 1)
            geral.getChildren().add(Ui.dado("Acesso", String.valueOf(fo.promovidos)));
        if (fo != null && fo.rebaixados != null && fo.rebaixados > 0) geral.getChildren().add(Ui.dado("Rebaixamento", String.valueOf(fo.rebaixados)));
        if (!geral.getChildren().isEmpty()) v.getChildren().add(Ui.painel("Geral", geral));

        if (t != null && !t.fases.isEmpty()) {
            GridPane g = new GridPane();
            g.setHgap(26);
            g.setVgap(8);
            String[] cab = {"Fase", "Sistema", "Grupos", "Jogos", "Classificam", "Desempate"};
            for (int c = 0; c < cab.length; c++) g.add(Ui.rotulo(cab[c], "texto-pequeno"), c, 0);
            int r = 1;
            for (FaseExec f : t.fases) {
                Formato.Fase d = f.def;
                String sistema = switch (f.tipo == null ? "" : f.tipo) {
                    case "pontos_corridos" -> f.jogosLiga > 0 ? "Liga única" : "Pontos corridos";
                    case "grupos" -> "Grupos";
                    case "mata_mata" -> "Mata-mata";
                    default -> f.tipo == null ? "—" : f.tipo;
                };
                if (d != null && "liga_suica".equals(d.tipo)) sistema = "Fase de liga";
                String grupos = f.numGrupos > 1 ? f.numGrupos + " × " + Math.max(1, f.entrantes / Math.max(1, f.numGrupos)) : "—";
                String jogos;
                if (f.mataMata()) {
                    boolean finalUnica = d != null && Boolean.TRUE.equals(d.final_jogo_unico);
                    jogos = f.pernas > 1 ? "ida e volta" + (finalUnica ? " · final única" : "") : "jogo único";
                } else if (f.jogosLiga > 0) jogos = f.jogosLiga + " por equipe";
                else jogos = f.turnos <= 1 ? "turno único" : f.turnos == 2 ? "turno e returno" : f.turnos + " turnos";
                String classif = f.mataMata() ? "—" : f.numGrupos > 1 ? f.porGrupo + " por grupo" + (f.terceiros > 0 ? " + " + f.terceiros + " 3º" : "")
                        : f.saida > 0 && t.fases.size() > f.indice + 1 ? String.valueOf(f.saida) : "—";
                String desemp = "—";
                if (f.mataMata() && d != null) {
                    List<String> ps = new ArrayList<>();
                    if (Boolean.TRUE.equals(d.gol_fora)) ps.add("gol fora");
                    if (d.desempate != null) ps.add(switch (d.desempate) {
                        case "penaltis" -> "pênaltis";
                        case "prorrogacao_penaltis" -> "prorrogação e pênaltis";
                        case "melhor_campanha" -> "melhor campanha";
                        case "visitante" -> "gol fora";
                        default -> d.desempate;
                    });
                    if (!ps.isEmpty()) desemp = String.join(", ", ps);
                    else desemp = "pênaltis";
                }
                g.addRow(r++, Ui.rotulo(f.nome == null ? "Fase " + (f.indice + 1) : f.nome, "texto-forte"), Ui.rotulo(sistema), Ui.rotulo(grupos),
                        Ui.rotulo(jogos), Ui.rotulo(classif), Ui.rotulo(desemp, "texto-suave"));
            }
            v.getChildren().add(Ui.painel("Fases", g));
        }

        List<String> crit = t != null && !t.criterios.isEmpty() ? t.criterios : fo != null && fo.desempates != null ? fo.desempates : List.of();
        if (!crit.isEmpty()) {
            VBox l = new VBox(4);
            int i = 1;
            for (String c : crit) l.getChildren().add(Ui.linha(10, Ui.rotulo(i++ + "º", "numero"), Ui.rotulo(criterio(c))));
            v.getChildren().add(Ui.painel("Critérios de desempate", l));
        }

        if (fo != null && fo.vagas != null && !fo.vagas.isEmpty()) {
            GridPane g = new GridPane();
            g.setHgap(20);
            g.setVgap(6);
            int r = 0;
            for (Formato.Vaga vg : fo.vagas) {
                if (vg.para == null || vg.posicoes == null || vg.posicoes.isEmpty()) continue;
                List<String> ps = new ArrayList<>();
                for (int p : vg.posicoes) ps.add(p + "º");
                g.addRow(r++, Ui.trofeu(vg.para, "continental", null, 20), Ui.nomeCompeticao(vg.para, Zonas.nomeCompeticao(vg.para), "texto-forte"),
                        Ui.rotulo(String.join(", ", ps), "texto-suave"));
            }
            if (r > 0) v.getChildren().add(Ui.painel("Vagas", g));
        }

        if (liga != null) {
            LigaKings.RegrasKings rk = liga.regras;
            HBox h = Ui.linha(28, Ui.dado("Em campo", rk.jogadores_em_campo + " × " + rk.jogadores_em_campo), Ui.dado("Duração", rk.duracao_min + " min"),
                    Ui.dado("Vitória", rk.pontos_vitoria + " pts"), Ui.dado("Shoot out", rk.pontos_vitoria_penaltis + "/" + rk.pontos_derrota_penaltis + " pts"),
                    Ui.dado("Playoff", rk.classificados_playoff + " times"));
            FlowPane esp = new FlowPane(8, 8);
            for (String e : rk.especiais) esp.getChildren().add(Ui.etiqueta(especial(e), "escura"));
            LigaKings.DraftKings dk = liga.draft;
            HBox d = Ui.linha(28, Ui.dado("Elenco", dk.jogadores_por_time + " jogadores"), Ui.dado("Draft", dk.rodadas + " rodadas"),
                    Ui.dado("Ordem", "serpente".equals(dk.ordem) ? "serpente" : dk.ordem), Ui.dado("Idade mínima", dk.idade_minima + " anos"));
            // o baralho das cartas secretas, com a mesma carta do jogo ao vivo (versão mini)
            FlowPane baralho = new FlowPane(8, 8);
            for (br.futebas.kings.MotorKings.Carta c : new java.util.LinkedHashSet<>(java.util.List.of(br.futebas.kings.MotorKings.BARALHO))) {
                br.futebas.ui.comp.CartaKings k = br.futebas.ui.comp.CartaKings.mini(c);
                javafx.scene.control.Tooltip.install(k, new javafx.scene.control.Tooltip(c.nome + ": " + br.futebas.ui.comp.CartaKings.efeito(c)));
                baralho.getChildren().add(k);
            }
            v.getChildren().add(Ui.painel("Regras da Kings League", h, esp, Ui.separadorH(), d, Ui.separadorH(),
                    Ui.rotulo("Cartas secretas (baralho de " + br.futebas.kings.MotorKings.BARALHO.length + ")", "titulo-menor"), baralho));
        }
        if (v.getChildren().isEmpty()) v.getChildren().add(Ui.vazio("Sem formato registrado para esta temporada.", null, null));
        return v;
    }

    private static String calendario(Torneio t) {
        if (t.semanaInicio <= 0 || t.semanaFim <= 0) return "—";
        int ai = t.semanaInicio / 52, si = t.semanaInicio % 52, af = t.semanaFim / 52, sf = t.semanaFim % 52;
        if (si == 0) {
            si = 52;
            ai--;
        }
        if (sf == 0) {
            sf = 52;
            af--;
        }
        if (ai < 1900 || af < 1900) return "—";
        return Textos.mes(si) + "/" + ai + " – " + Textos.mes(sf) + "/" + af;
    }

    private static String criterio(String c) {
        if (c == null) return "";
        return switch (c) {
            case "pontos" -> "Pontos";
            case "vitorias" -> "Vitórias";
            case "saldo", "saldo_gols" -> "Saldo de gols";
            case "gols_pro", "gols" -> "Gols marcados";
            case "gols_contra" -> "Gols sofridos";
            case "confronto_direto", "confronto" -> "Confronto direto";
            case "gols_fora" -> "Gols fora de casa";
            case "cartoes", "fair_play" -> "Cartões (fair play)";
            case "sorteio" -> "Sorteio";
            case "empates" -> "Empates";
            case "derrotas" -> "Menos derrotas";
            default -> Character.toUpperCase(c.charAt(0)) + c.substring(1).replace('_', ' ');
        };
    }

    private static String especial(String e) {
        return switch (e) {
            case "dado" -> "Dado";
            case "carta_secreta" -> "Carta secreta";
            case "penalti_presidente" -> "Pênalti do presidente";
            case "gol_duplo" -> "Gol duplo";
            case "shootout" -> "Shoot out";
            case "match_ball" -> "Match ball";
            default -> e.replace('_', ' ');
        };
    }
}
