package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.kings.LigaKings;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Selecao;
import br.futebas.mundo.StatusJogador;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Páginas de seleção e de time da Kings League, e as peças de equipe que a página do clube
 * também usa: jogos (com a competição de cada um), competições do ano, forma e sala de troféus.
 */
final class PaginasEquipe {
    private PaginasEquipe() {}

    // ================================================================== seleção
    static Node selecao(Hub hub, String pais, Runnable voltar) {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        Selecao s = j.selecao(pais);
        if (s == null) return Ui.conteudo(Ui.vazio("Seleção não encontrada.", "Voltar", voltar));
        String id = Selecao.id(s.pais);
        List<Selecao> rk = j.rankingSelecoes();
        br.futebas.mundo.Treinador tr = j.treinadorDaSelecao(s.pais);
        Node tecnico;
        if (s.humano && m.tecnico != null) tecnico = Ui.rotulo(m.tecnico.nome + " (você)", "cab-valor-medio");
        else if (tr != null) tecnico = Ui.nomeTecnico(tr.id, tr.nome, "cab-valor-medio");
        else tecnico = Ui.rotulo(s.tecnico == null ? "—" : s.tecnico, "cab-valor-medio");
        List<Partida> ps = j.partidasDoParticipante(id);
        HBox sub = Pagina.origem(Imagens.bandeira(s.pais, 14), Textos.pais(s.pais) + (s.confederacao == null ? "" : "  ·  " + s.confederacao));
        VBox cab = Pagina.cabecalho(Imagens.escudo(id, 76), s.nome, sub, null, null, voltar,
                Pagina.bloco((rk.indexOf(s) + 1) + "º", "Ranking"),
                Pagina.bloco(String.valueOf(Math.round(s.elo)), "Elo"),
                Pagina.bloco(Ui.forca((int) Math.round(s.forca)), "Força"),
                Pagina.bloco(tecnico, "Técnico"),
                Pagina.bloco(s.vitorias + "-" + s.empates + "-" + s.derrotas, "V-E-D desde 2026"));

        List<Jogador> conv = j.convocados(s.pais);
        boolean provaveis = conv.isEmpty();
        if (provaveis) {
            for (Jogador x : m.jogadores) {
                if (x == null || !s.pais.equals(x.pais) || x.status == StatusJogador.APOSENTADO || x.amador()) continue;
                conv.add(x);
            }
            conv.sort(Comparator.comparingDouble((Jogador x) -> -x.forca));
            if (conv.size() > 26) conv = new ArrayList<>(conv.subList(0, 26));
        }
        conv.sort(Comparator.comparingInt((Jogador x) -> x.posicao.ordinal()).thenComparingDouble(x -> -x.forca));
        TableView<Jogador> tv = Ui.tabela();
        Ui.colPos(tv, x -> x.posicao);
        Ui.colNo(tv, "Jogador", 220, x -> {
            Label n = Ui.nomeJogador(x.id, x.nomeExibicao());
            n.setMinWidth(0);
            return n;
        });
        Ui.colNum(tv, "Idade", 54, x -> x.idade(j.ano()));
        Ui.colForca(tv, "Força", Jogador::forcaExibida);
        Ui.colNum(tv, "Jogos", 56, x -> x.jogosSelecao);
        Ui.colNum(tv, "Gols", 52, x -> x.golsSelecao);
        Ui.colClube(tv, "Clube", 240, m, x -> x.clubeId);
        tv.getItems().setAll(conv);
        Ui.alturaPorLinhas(tv, Math.max(3, Math.min(28, conv.size())));
        VBox elenco = Ui.painelTabela(provaveis ? "Prováveis convocados" : "Convocados", Ui.rotulo(conv.size() + " jogadores", "texto-suave"), tv);

        // visão geral
        VBox esq = new VBox(16, proximosEUltimos(j, ps, id, 5, 5));
        VBox dir = new VBox(16);
        dir.getChildren().add(Ui.painel("Forma", forma(ps, id, 10)));
        List<Jogador> destaques = new ArrayList<>(conv);
        destaques.sort(Comparator.comparingDouble((Jogador x) -> -x.forca));
        VBox top = new VBox(6);
        for (int i = 0; i < Math.min(6, destaques.size()); i++) {
            Jogador x = destaques.get(i);
            top.getChildren().add(Ui.linha(8, Ui.pos(x.posicao), Ui.nomeJogador(x.id, x.nomeExibicao(), "texto-forte"), Ui.espaco(),
                    x.clubeId == null ? new Region() : Ui.escudo(x.clubeId, 18), Ui.forca(x.forcaExibida())));
        }
        if (!top.getChildren().isEmpty()) dir.getChildren().add(Ui.painel("Destaques", top));
        dir.getChildren().add(Ui.painel("Competições", competicoes(j, id, true)));
        HBox geral = Pagina.colunas(18, esq, dir);
        esq.setPrefWidth(55);
        dir.setPrefWidth(45);

        return Pagina.montar(cab, Pagina.abas("selecao",
                Pagina.aba("Visão geral", geral),
                Pagina.aba("Elenco", elenco),
                Pagina.abaSob("Jogos", () -> Ui.painel(null, listaJogos(j, ps, id))),
                Pagina.abaSob("Competições", () -> competicoes(j, id, false)),
                Pagina.aba("Títulos", salaTitulos(j, id, s.titulos))));
    }

    // ================================================================== time da Kings
    static Node timeKings(Hub hub, String timeId, Runnable voltar) {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        LigaKings liga = j.ligaKingsDoTime(timeId);
        LigaKings.TimeKings t = liga == null ? null : liga.time(timeId);
        if (t == null) return Ui.conteudo(Ui.vazio("Time não encontrado.", "Voltar", voltar));
        List<Partida> ps = j.partidasDoParticipante(t.id);
        Torneio atual = liga.torneio;
        String posicao = "—";
        LinhaTabela minha = null;
        if (atual != null) {
            List<LinhaTabela> ls = tabelaKings(atual);
            for (int i = 0; i < ls.size(); i++) if (ls.get(i).clubeId.equals(t.id)) {
                posicao = (i + 1) + "º";
                minha = ls.get(i);
            }
        }
        String tecnico = t.humano && m.tecnico != null ? m.tecnico.nome + " (você)" : t.vagaAte > 0 ? "vaga aberta" : "—";
        HBox sub = Pagina.origem(liga.pais == null ? null : Imagens.bandeira(liga.pais, 14), Textos.ligaKings(liga.id, liga.nome));
        VBox cab = Pagina.cabecalho(Imagens.escudo(t.id, 76), t.nome, sub, t.cor1, t.cor2, voltar,
                Pagina.bloco(t.presidente == null ? "—" : t.presidente, "Presidente"),
                Pagina.bloco(tecnico, "Técnico"),
                Pagina.bloco(posicao, atual == null ? "Posição" : Textos.curto(atual.nome).replace(String.valueOf(atual.ano), "").replace(liga.nome, "").trim()),
                minha == null ? null : Pagina.bloco(minha.pontos + " pts", minha.vitorias + "V " + minha.derrotas + "D"
                        + (minha.vitoriasPenaltis + minha.derrotasPenaltis > 0 ? " · SO " + minha.vitoriasPenaltis + "-" + minha.derrotasPenaltis : "")),
                Pagina.bloco(String.valueOf(t.elenco.size()), "Elenco"));

        // elenco dividido: wildcards e draft
        List<LigaKings.AtletaKings> wild = new ArrayList<>(), draft = new ArrayList<>(), outros = new ArrayList<>();
        for (LigaKings.AtletaKings a : t.elenco) {
            String tp = a.tipo == null ? "" : a.tipo;
            if (tp.equals("wildcard") || tp.equals("mantido") || a.presidente) wild.add(a);
            else if (tp.equals("draft")) draft.add(a);
            else outros.add(a);
        }
        VBox elenco = new VBox(16);
        Node presidentes = presidentesJogadores(t, j.ano());
        if (presidentes != null) elenco.getChildren().add(presidentes);
        if (!wild.isEmpty()) elenco.getChildren().add(tabelaAtletas(j, m, t.id, "Wildcards", wild));
        if (!draft.isEmpty()) elenco.getChildren().add(tabelaAtletas(j, m, t.id, "Draft", draft));
        if (!outros.isEmpty()) elenco.getChildren().add(tabelaAtletas(j, m, t.id, wild.isEmpty() && draft.isEmpty() ? "Elenco" : "Outros", outros));
        if (elenco.getChildren().isEmpty()) elenco.getChildren().add(Ui.vazio("Elenco ainda não formado.", null, null));

        // visão geral: tabela da liga + jogos
        VBox esq = new VBox(16);
        if (atual != null) {
            List<LinhaTabela> ls = tabelaKings(atual);
            TableView<LinhaTabela> tv = PainelTabelas.tabelaClassificacao(m, atual, ls, j::nomeParticipante, t.id, 0, 0, liga.regras.classificados_playoff, true);
            Node acoes = Ui.nomeCompeticao(atual.id, Textos.curto(atual.nome), "texto-suave");
            esq.getChildren().add(Ui.painelTabela("Classificação", acoes, tv));
        } else esq.getChildren().add(Ui.painel("Classificação", Ui.rotulo("A temporada ainda não começou.", "texto-suave")));
        VBox dir = new VBox(16, proximosEUltimos(j, ps, t.id, 4, 5), Ui.painel("Forma", forma(ps, t.id, 10)));
        Node presGeral = presidentesJogadores(t, j.ano());
        if (presGeral != null) dir.getChildren().add(0, presGeral);
        List<LigaKings.AtletaKings> art = new ArrayList<>(t.elenco);
        art.removeIf(a -> a.gols <= 0);
        art.sort((a, b) -> b.gols - a.gols);
        if (!art.isEmpty()) {
            VBox l = new VBox(6);
            for (int i = 0; i < Math.min(5, art.size()); i++) {
                LigaKings.AtletaKings a = art.get(i);
                l.getChildren().add(Ui.linha(8, Ui.pos(a.posicao), nomeAtleta(m, t.id, a, "texto-forte"), Ui.espaco(), Ui.rotulo(a.gols + " gols", "numero")));
            }
            dir.getChildren().add(Ui.painel("Artilheiros do time", l));
        }
        HBox geral = Pagina.colunas(18, esq, dir);
        esq.setPrefWidth(52);
        dir.setPrefWidth(48);

        VBox comp = competicoes(j, t.id, false);
        return Pagina.montar(cab, Pagina.abas("kings",
                Pagina.aba("Visão geral", geral),
                Pagina.aba("Elenco", elenco),
                Pagina.abaSob("Jogos", () -> Ui.painel(null, listaJogos(j, ps, t.id))),
                Pagina.aba("Competições", comp),
                Pagina.aba("Títulos", salaTitulos(j, t.id, List.of()))));
    }

    private static List<LinhaTabela> tabelaKings(Torneio t) {
        FaseExec f = t.faseAtual();
        if (f == null || f.mataMata()) {
            for (int i = t.fases.size() - 1; i >= 0; i--) if (!t.fases.get(i).mataMata() && t.fases.get(i).iniciada) return t.tabela(i, 0);
            return t.tabelaGeral();
        }
        return t.tabela(f.indice, 0);
    }

    /** Presidentes que também jogam: foto, nome, posição, força e idade (ficam no time para sempre). */
    private static Node presidentesJogadores(LigaKings.TimeKings t, int ano) {
        List<LigaKings.AtletaKings> l = new ArrayList<>();
        for (LigaKings.AtletaKings a : t.elenco) if (a.presidente) l.add(a);
        if (l.isEmpty()) return null;
        HBox h = new HBox(14);
        for (LigaKings.AtletaKings a : l) {
            Node foto = br.futebas.ui.comp.Foto.kings(a.foto, null, t.id, a.nome, 64, 78);
            VBox info = new VBox(3, Ui.nomeAtletaKings(null, t.id, a.nome, "texto-forte"),
                    Ui.linha(6, Ui.pos(a.posicao), Ui.rotulo((int) Math.round(a.forca) + "", "numero"),
                            Ui.rotulo(a.anoNasc == null ? "" : (ano - a.anoNasc) + " anos", "texto-suave")),
                    Ui.rotulo(a.gols + " gols", "texto-pequeno"));
            h.getChildren().add(Ui.linha(10, foto, info));
        }
        return Ui.painel(l.size() == 1 ? "Presidente e jogador" : "Presidentes e jogadores", h);
    }

    private static Label nomeAtleta(Mundo m, String timeId, LigaKings.AtletaKings a, String... classes) {
        Jogador x = a.jogadorId == null ? null : m.jogador(a.jogadorId);
        return x != null ? Ui.nomeJogador(x.id, x.nomeExibicao(), classes) : Ui.nomeAtletaKings(null, timeId, a.nome, classes);
    }

    private static VBox tabelaAtletas(Jogo j, Mundo m, String timeId, String titulo, List<LigaKings.AtletaKings> l) {
        l.sort(Comparator.comparingInt((LigaKings.AtletaKings a) -> a.posicao.ordinal()).thenComparingDouble(a -> -a.forca));
        TableView<LigaKings.AtletaKings> tv = Ui.tabela();
        Ui.colPos(tv, a -> a.posicao);
        Ui.colNo(tv, "Jogador", 230, a -> {
            Label n = nomeAtleta(m, timeId, a);
            n.setMinWidth(0);
            Jogador x = a.jogadorId == null ? null : m.jogador(a.jogadorId);
            return Ui.linha(6, br.futebas.ui.comp.Foto.kings(x == null ? a.foto : j.foto(x.id), x == null ? null : x.dbId, timeId, a.nome, 22, 26), n);
        });
        Ui.colNum(tv, "Idade", 54, a -> {
            Jogador x = a.jogadorId == null ? null : m.jogador(a.jogadorId);
            if (x == null && a.anoNasc != null) return j.ano() - a.anoNasc;
            return x == null || x.anoNasc <= 0 ? null : x.idade(j.ano());
        });
        Ui.colForca(tv, "Força", a -> (int) Math.round(a.forca));
        Ui.col(tv, "Vaga", 120, a -> a.tipo == null ? "" : switch (a.tipo) {
            case "wildcard" -> "Wildcard";
            case "presidente" -> "Presidente (wildcard)";
            case "mantido" -> "Mantido";
            case "draft" -> "Draft";
            case "11o" -> "11º jogador";
            case "coringa" -> "Coringa";
            case "selecao" -> "Convocado";
            default -> "Elenco inicial";
        });
        Ui.col(tv, "Carreira", 230, a -> {
            Jogador x = a.jogadorId == null ? null : m.jogador(a.jogadorId);
            if (x == null) return a.presidente ? "presidente do time" : "";
            return x.status == StatusJogador.APOSENTADO ? "ex-profissional" : Textos.situacao(m, x);
        });
        Ui.colNum(tv, "Gols", 54, a -> a.gols);
        tv.getItems().setAll(l);
        Ui.alturaPorLinhas(tv, Math.max(2, l.size()));
        return Ui.painelTabela(titulo, Ui.rotulo(l.size() + " jogadores", "texto-suave"), tv);
    }

    // ================================================================== peças comuns
    /** Próximos e últimos jogos lado a lado. */
    static VBox proximosEUltimos(Jogo j, List<Partida> ps, String id, int nProx, int nUlt) {
        List<Partida> feitos = new ArrayList<>(), proximos = new ArrayList<>();
        for (Partida p : ps) (p.jogada ? feitos : proximos).add(p);
        List<Partida> ult = new ArrayList<>(feitos.subList(Math.max(0, feitos.size() - nUlt), feitos.size()));
        java.util.Collections.reverse(ult);
        List<Partida> prox = proximos.subList(0, Math.min(nProx, proximos.size()));
        VBox v = new VBox(16);
        v.getChildren().add(Ui.painel("Próximos jogos", prox.isEmpty() ? Ui.rotulo("Sem jogos marcados.", "texto-suave") : listaJogos(j, prox, id)));
        v.getChildren().add(Ui.painel("Últimos resultados", ult.isEmpty() ? Ui.rotulo("Nenhum jogo disputado.", "texto-suave") : listaJogos(j, ult, id)));
        return v;
    }

    /** Jogos do participante: data, competição, mando, adversário e resultado. */
    static Node listaJogos(Jogo j, List<Partida> ps, String id) {
        if (ps.isEmpty()) return Ui.rotulo("Sem jogos nesta temporada.", "texto-suave");
        Mundo m = j.mundo();
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(6);
        Map<String, Torneio> cache = new HashMap<>();
        int r = 0;
        for (Partida p : ps) {
            Torneio t = cache.computeIfAbsent(p.torneioId == null ? "" : p.torneioId, j::competicaoQualquer);
            boolean casa = id.equals(p.mandante);
            String adv = casa ? p.visitante : p.mandante;
            Label data = Ui.rotulo(Textos.dataCurta(p.data()), "texto-pequeno");
            data.setMinWidth(70);
            Node troféu = t == null ? new Region() : Ui.trofeu(t.id, t.ambito, t.pais, 18);
            Label comp = t == null ? Ui.rotulo("") : Ui.nomeCompeticao(t.id, Textos.curto(t.nome), "texto-suave");
            comp.setMaxWidth(190);
            Label mando = Ui.rotulo(casa ? "C" : "F", "texto-pequeno");
            Label nomeAdv = Ui.nomeClube(adv, j.nomeParticipante(adv), "texto-forte");
            nomeAdv.setMinWidth(0);
            HBox advB = Ui.linha(8, Ui.escudo(adv, 18), nomeAdv);
            Node res;
            if (p.jogada && p.golsMandante != null) {
                int pro = casa ? p.golsMandante : p.golsVisitante, contra = casa ? p.golsVisitante : p.golsMandante;
                String pen = p.penaltisMandante != null ? " (" + (casa ? p.penaltisMandante : p.penaltisVisitante) + "–" + (casa ? p.penaltisVisitante : p.penaltisMandante) + ")" : "";
                res = Ui.linha(6, Ui.forma(Textos.resultadoPara(p, id)), Ui.rotulo(pro + " – " + contra + pen, "numero"));
            } else res = Ui.rotulo("—", "texto-suave");
            g.addRow(r++, data, troféu, comp, mando, advB, res);
        }
        return g;
    }

    /** Últimos resultados em V/E/D. */
    static HBox forma(List<Partida> ps, String id, int n) {
        List<Integer> pts = new ArrayList<>();
        for (Partida p : ps) {
            if (!p.jogada || p.golsMandante == null) continue;
            String r = Textos.resultadoPara(p, id);
            pts.add("V".equals(r) ? 3 : "E".equals(r) ? 1 : 0);
        }
        return Ui.forma(pts, n);
    }

    /** Competições do ano: troféu, nome (abre a página) e a situação do participante. */
    static VBox competicoes(Jogo j, String id, boolean compacto) {
        VBox v = new VBox(compacto ? 8 : 12);
        for (Torneio t : j.competicoesDoParticipante(id)) {
            Label nome = Ui.nomeCompeticao(t.id, Textos.curto(t.nome), "texto-forte");
            nome.setMinWidth(0);
            Label sit = Ui.rotulo(situacao(t, id), "texto-suave");
            if (compacto) {
                v.getChildren().add(Ui.linha(10, Ui.trofeu(t.id, t.ambito, t.pais, 22), nome, Ui.espaco(), sit));
            } else {
                VBox txt = new VBox(2, nome, sit);
                VBox caixa = Ui.painel(null, Ui.linha(14, Ui.trofeu(t.id, t.ambito, t.pais, 44), txt));
                v.getChildren().add(caixa);
            }
        }
        if (v.getChildren().isEmpty()) v.getChildren().add(Ui.rotulo("Sem competições nesta temporada.", "texto-suave"));
        return v;
    }

    /** "3º · 24 pts", "Oitavas de final", "Eliminado", "Campeão"... */
    static String situacao(Torneio t, String id) {
        if (id.equals(t.campeao)) return "Campeão";
        if (id.equals(t.vice)) return "Vice-campeão";
        FaseExec f = t.faseAtual();
        if (t.encerrado || f == null) {
            int p = t.classificacao.indexOf(id);
            return p >= 0 ? (p + 1) + "º lugar" : "Encerrado";
        }
        if (!f.iniciada && f.indice == 0) return "A começar";
        boolean jogou = false;
        for (Partida p : t.partidas) if (p.envolve(id)) {
            jogou = true;
            break;
        }
        if (!jogou) return "Aguarda a estreia";
        if (f.mataMata()) {
            if (f.vivos.contains(id) || f.clubes.contains(id)) {
                for (var c : f.confrontos) if ((id.equals(c.a) || id.equals(c.b)) && c.vencedor != null && !id.equals(c.vencedor)) return "Eliminado · " + (f.nome == null ? "mata-mata" : f.nome);
                return f.nome == null ? "Mata-mata" : f.nome;
            }
            return "Eliminado";
        }
        for (int g = 0; g < Math.max(1, f.numGrupos); g++) {
            List<LinhaTabela> ls = t.tabela(f.indice, g);
            for (int i = 0; i < ls.size(); i++) {
                if (!ls.get(i).clubeId.equals(id)) continue;
                String grupo = f.numGrupos > 1 ? " · Grupo " + (char) ('A' + g) : "";
                return (i + 1) + "º" + grupo + " · " + ls.get(i).pontos + " pts";
            }
        }
        if (!f.clubes.isEmpty() && !f.clubes.contains(id)) return f.indice > 0 ? "Eliminado" : "—";
        return f.nome == null ? "Em andamento" : f.nome;
    }

    /** Sala de troféus: uma vitrine por competição (reais até 2025/26 e do save), com os anos. */
    static VBox salaTitulos(Jogo j, String id, List<String> extras) {
        Map<String, List<Jogo.TituloInfo>> porComp = new LinkedHashMap<>();
        List<Jogo.TituloInfo> ts = new ArrayList<>(j.titulosDoParticipante(id));
        ts.sort(Comparator.comparingInt((Jogo.TituloInfo t) -> PainelMundo.ordemCompeticao(t.competicaoId(), t.ambito())).thenComparing(Jogo.TituloInfo::temporada));
        for (Jogo.TituloInfo t : ts) porComp.computeIfAbsent(t.competicaoId(), k -> new ArrayList<>()).add(t);
        FlowPane fp = new FlowPane(12, 12);
        int total = 0;
        for (List<Jogo.TituloInfo> l : porComp.values()) {
            Jogo.TituloInfo t0 = l.get(0);
            total += l.size();
            List<String> anos = new ArrayList<>();
            for (Jogo.TituloInfo t : l) anos.add(t.temporada());
            Label n = Ui.nomeCompeticao(t0.competicaoId(), Textos.curto(t0.competicao()), "texto-forte");
            n.setWrapText(true);
            n.setMaxWidth(140);
            n.setAlignment(Pos.CENTER);
            n.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            Label a = Ui.paragrafo(String.join(", ", anos), "texto-pequeno");
            a.setMaxWidth(140);
            a.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            VBox b = new VBox(6, Ui.trofeu(t0.competicaoId(), t0.ambito(), t0.pais(), 70), Ui.rotulo(l.size() + "×", "trofeu-conta"), n, a);
            b.setAlignment(Pos.TOP_CENTER);
            b.getStyleClass().add("trofeu-caixa");
            b.setPrefWidth(160);
            b.setMinWidth(160);
            fp.getChildren().add(b);
        }
        VBox v = new VBox(16);
        if (fp.getChildren().isEmpty() && (extras == null || extras.isEmpty())) {
            v.getChildren().add(Ui.vazio("Nenhum título registrado.", null, null));
            return v;
        }
        if (!fp.getChildren().isEmpty()) v.getChildren().add(Ui.painelAcoes("Sala de troféus", Ui.rotulo(total + (total == 1 ? " título" : " títulos"), "texto-suave"), fp));
        if (extras != null && !extras.isEmpty() && fp.getChildren().isEmpty()) {
            VBox l = new VBox(4);
            for (String e : extras) l.getChildren().add(Ui.rotulo(e, "texto-medio"));
            v.getChildren().add(Ui.painel("Títulos", l));
        }
        return v;
    }
}
