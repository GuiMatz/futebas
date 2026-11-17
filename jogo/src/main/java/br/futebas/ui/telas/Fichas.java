package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.historia.Historia;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Selecao;
import br.futebas.mundo.Tecnico;
import br.futebas.partida.Escalador;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fichas abertas pela pesquisa ou por um clique no escudo: clube, técnico e seleção. */
public final class Fichas {
    private Fichas() {}

    // ------------------------------------------------------------------ clube
    static Node clube(Hub hub, String id, Runnable voltar) {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        Clube c = m.clube(id);
        if (c == null) return Ui.conteudo(Ui.vazio("Clube não encontrado.", "Voltar", voltar));
        int forca = 0;
        try {
            forca = (int) Math.round(Escalador.forcaTime(m, c));
        } catch (RuntimeException e) {
            // sem elenco suficiente
        }
        br.futebas.mundo.Treinador tr = c.humano ? null : j.treinadorDoClube(c.id);
        Node tecnico;
        if (c.humano && m.tecnico != null) tecnico = Ui.rotulo(m.tecnico.nome + " (você)", "cab-valor-medio");
        else if (tr == null) tecnico = Ui.rotulo("—", "cab-valor-medio");
        else {
            Label lt = Ui.link(tr.nome, () -> hub.abrirFicha(treinador(hub, tr, hub::fecharFicha)), "cab-valor-medio");
            tecnico = lt;
        }
        String local = Textos.pais(c.pais) + (c.cidade == null ? "" : "  ·  " + c.cidade) + (c.uf == null ? "" : " (" + c.uf + ")");
        HBox sub = Pagina.origem(Imagens.bandeira(c.pais, 14), local);
        sub.getChildren().addAll(Ui.estrelas(c.reputacao, 13), Ui.rotulo(Textos.porte(c.reputacao), "cab-meta"));
        if (c.humano) sub.getChildren().add(Ui.etiqueta("Seu clube", "verde"));
        String divisao = c.virtual ? "—" : Textos.divisaoDe(m, c);
        VBox cab = Pagina.cabecalho(Imagens.escudo(c.id, 84), c.nome, sub, c.cor1, c.cor2, voltar,
                Pagina.bloco(Ui.forca(forca), "Força"),
                Pagina.bloco(tecnico, "Técnico"),
                Pagina.bloco(divisao, "Divisão"),
                Pagina.bloco(c.estadio == null ? "—" : c.estadio, c.capacidade > 0 ? Textos.milhar(c.capacidade) + " lugares" : "Estádio"));

        // elenco
        List<Jogador> el = new ArrayList<>(j.elenco(c.id));
        el.sort(Comparator.comparingInt((Jogador x) -> x.posicao.ordinal()).thenComparingDouble(x -> -x.forca));
        TableView<Jogador> tv = Ui.tabela();
        Ui.colPos(tv, x -> x.posicao);
        Ui.colNo(tv, "Jogador", 230, x -> {
            Label n = Ui.nomeJogador(x.id, x.nomeExibicao());
            n.setMinWidth(0);
            return Ui.linha(7, Imagens.bandeira(x.pais, 11), n);
        });
        Ui.colNum(tv, "Idade", 54, x -> x.idade(j.ano()));
        Ui.colForca(tv, "Força", Jogador::forcaExibida);
        Ui.colNum(tv, "Jogos", 56, x -> x.jogosTemp);
        Ui.colNum(tv, "Gols", 52, x -> x.golsTemp);
        Ui.colNum(tv, "Assist.", 60, x -> x.assistTemp);
        Ui.col(tv, "Contrato", 120, Textos::contrato);
        Ui.colDinheiro(tv, "Valor", 110, x -> x.amador() ? 0L : j.valorDeMercado(x.id));
        tv.getItems().setAll(el);
        Ui.alturaPorLinhas(tv, Math.max(4, Math.min(34, el.size())));
        VBox elenco = Ui.painelTabela("Elenco", Ui.rotulo(el.size() + " jogadores", "texto-suave"), tv);

        // visão geral
        List<Partida> cal = j.partidasDoParticipante(c.id);
        VBox esq = new VBox(16, PaginasEquipe.proximosEUltimos(j, cal, c.id, 5, 5));
        VBox dir = new VBox(16);
        dir.getChildren().add(Ui.painel("Forma", PaginasEquipe.forma(cal, c.id, 10)));
        dir.getChildren().add(Ui.painel("Competições", PaginasEquipe.competicoes(j, c.id, true)));
        List<Jogador> dest = new ArrayList<>(el);
        dest.sort(Comparator.comparingDouble((Jogador x) -> -x.forca));
        VBox top = new VBox(6);
        for (int i = 0; i < Math.min(6, dest.size()); i++) {
            Jogador x = dest.get(i);
            top.getChildren().add(Ui.linha(8, Ui.pos(x.posicao), Ui.nomeJogador(x.id, x.nomeExibicao(), "texto-forte"), Ui.espaco(),
                    Ui.rotulo(x.idade(j.ano()) + " anos", "texto-suave"), Ui.forca(x.forcaExibida())));
        }
        if (!top.getChildren().isEmpty()) dir.getChildren().add(Ui.painel("Destaques do elenco", top));
        if (!c.rivais.isEmpty()) {
            FlowPane fr = new FlowPane(16, 10);
            for (String r : c.rivais) if (m.clube(r) != null) fr.getChildren().add(Ui.clube(m, r, 24));
            if (!fr.getChildren().isEmpty()) dir.getChildren().add(Ui.painel("Rivais", fr));
        }
        HBox geral = Pagina.colunas(18, esq, dir);
        esq.setPrefWidth(55);
        dir.setPrefWidth(45);

        // história: sala de troféus (reais + do save) e linha do tempo do save
        VBox historia = PaginasEquipe.salaTitulos(j, c.id, List.of());
        List<Historia.Titulo> ts = new ArrayList<>(m.historia.titulosDoClube(c.id));
        ts.sort((x, y) -> y.ano - x.ano);
        if (!ts.isEmpty()) {
            VBox lt = new VBox(6);
            for (Historia.Titulo t : ts.subList(0, Math.min(15, ts.size())))
                lt.getChildren().add(Ui.linha(10, Ui.rotulo(t.temporada != null ? t.temporada : String.valueOf(t.ano), "numero"),
                        Ui.trofeu(t.competicaoId, t.ambito, t.pais, 20), Ui.nomeCompeticao(t.competicaoId, Textos.curto(t.competicao), "texto-forte")));
            historia.getChildren().add(Ui.painel("Conquistas no seu mundo", lt));
        }

        // melhor 11 de todos os tempos
        Node onzeNo = null;
        try {
            List<Jogo.VagaOnze> onze = j.onzeHistorico(c.id);
            if (onze != null && onze.size() >= 11) {
                List<PainelPremios.Vaga> vs = new ArrayList<>();
                for (Jogo.VagaOnze o : onze) vs.add(new PainelPremios.Vaga(o.posicao(), o.nome(), o.doJogo() ? "do seu mundo" : o.posicao(), null));
                VBox p11 = Ui.painel(null, PainelPremios.campoOnze("Melhor 11 de todos os tempos", vs));
                p11.setAlignment(Pos.TOP_CENTER);
                onzeNo = p11;
            }
        } catch (RuntimeException e) {
            // sem camada histórica
        }

        List<javafx.scene.control.Tab> abas = new ArrayList<>(List.of(
                Pagina.aba("Visão geral", geral),
                Pagina.aba("Elenco", elenco),
                Pagina.abaSob("Jogos", () -> Ui.painel(null, PaginasEquipe.listaJogos(j, cal, c.id))),
                Pagina.abaSob("Competições", () -> PaginasEquipe.competicoes(j, c.id, false)),
                Pagina.aba("História e títulos", historia)));
        if (onzeNo != null) abas.add(Pagina.aba("Onze histórico", onzeNo));
        return Pagina.montar(cab, Pagina.abas("clube", abas.toArray(new javafx.scene.control.Tab[0])));
    }

    // ------------------------------------------------------------------ técnico
    static Node tecnico(Hub hub, Runnable voltar) {
        Tecnico t = hub.jogo().mundo().tecnico;
        PainelMundo pm = new PainelMundo(hub.sessao());
        pm.selecionarAba(2); // Técnicos
        Button bVoltar = Ui.botaoTecla("Voltar", "Esc", voltar);
        VBox v = Ui.conteudo(Ui.linha(10, Ui.titulo(t == null ? "Técnico" : t.nome), Ui.espaco(), bVoltar), pm.construir());
        return v;
    }

    /** Técnico da IA (real, ex-jogador ou gerado): clube, nacionalidade, números e carreira. */
    public static Node treinador(Hub hub, br.futebas.mundo.Treinador t, Runnable voltar) {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        Button bVoltar = Ui.botaoTecla("Voltar", "Esc", voltar);
        String onde = t.clubeId != null ? Textos.nomeClube(m, t.clubeId) : t.selecaoPais != null ? "Seleção " + Textos.pais(t.selecaoPais)
                : t.aposentado ? "Aposentado" : "Sem clube";
        String origem = switch (t.origem == null ? "" : t.origem) {
            case "real" -> "técnico";
            case "ex_jogador" -> "ex-jogador";
            default -> "técnico";
        };
        Node icone = t.clubeId != null ? Ui.escudo(t.clubeId, 64) : t.selecaoPais != null ? Ui.escudo("sel-" + t.selecaoPais, 48) : Imagens.bandeira(t.pais, 30);
        VBox id = new VBox(4, Ui.titulo(t.nome), Ui.linha(8, Imagens.bandeira(t.pais, 13),
                Ui.rotulo(Textos.pais(t.pais) + (t.anoNasc > 0 ? "  ·  " + t.idade(j.ano()) + " anos" : "") + "  ·  " + origem, "texto-medio")),
                Ui.linha(10, Ui.estrelas(t.reputacao, 14), t.clubeId != null ? Ui.nomeClube(t.clubeId, onde, "texto-medio")
                        : t.selecaoPais != null ? Ui.nomeClube("sel-" + t.selecaoPais, onde, "texto-medio") : Ui.rotulo(onde, "texto-medio")));
        HBox.setHgrow(id, Priority.ALWAYS);
        HBox nums = Ui.linha(26, Ui.dado("Avaliação", Ui.forca((int) Math.round(t.avaliacao))), Ui.dado("Jogos", String.valueOf(t.jogos)),
                Ui.dado("V-E-D", t.vitorias + "-" + t.empates + "-" + t.derrotas), Ui.dado("Aproveitamento", Math.round(t.aproveitamento()) + "%"),
                Ui.dado("Títulos", String.valueOf(t.titulos.size())), Ui.dado("Demissões", String.valueOf(t.demissoes)));
        VBox cab = Ui.painel(null, Ui.linha(18, icone, id, bVoltar), Ui.separadorH(), nums);
        VBox carreira = new VBox(4);
        for (int i = t.historico.size() - 1; i >= 0; i--) carreira.getChildren().add(Ui.rotulo(t.historico.get(i), "texto-medio"));
        if (carreira.getChildren().isEmpty()) carreira.getChildren().add(Ui.rotulo("Sem passagens registradas desde 2026.", "texto-suave"));
        VBox tit = new VBox(4);
        for (int i = t.titulos.size() - 1; i >= 0; i--) tit.getChildren().add(Ui.rotulo(t.titulos.get(i), "texto-medio"));
        if (tit.getChildren().isEmpty()) tit.getChildren().add(Ui.rotulo("Nenhum título no seu mundo ainda.", "texto-suave"));
        VBox a = Ui.painel("Carreira", carreira), b = Ui.painel("Títulos", tit);
        HBox corpo = new HBox(16, a, b);
        HBox.setHgrow(a, Priority.ALWAYS);
        HBox.setHgrow(b, Priority.ALWAYS);
        a.setPrefWidth(400);
        b.setPrefWidth(400);
        corpo.setAlignment(Pos.TOP_LEFT);
        return Ui.rolar(Ui.conteudo(cab, corpo));
    }

    /** Seleção: página com abas (visão geral, elenco, jogos, competições, títulos). */
    static Node selecao(Hub hub, String pais, Runnable voltar) {
        return PaginasEquipe.selecao(hub, pais, voltar);
    }

    /** Time da Kings League: página com abas (elenco em wildcards e draft, tabela, jogos, títulos). */
    static Node timeKings(Hub hub, br.futebas.kings.LigaKings liga, br.futebas.kings.LigaKings.TimeKings t, Runnable voltar) {
        return PaginasEquipe.timeKings(hub, t.id, voltar);
    }

    /** Técnico de seleção (da IA): só o que o banco sabe. */
    static Node tecnicoSelecao(Hub hub, Selecao s, Runnable voltar) {
        Button bVoltar = Ui.botaoTecla("Voltar", "Esc", voltar);
        VBox p = Ui.painel(null, Ui.linha(16, Imagens.bandeira(s.pais, 40), new VBox(2, Ui.titulo(s.tecnico == null ? "Técnico" : s.tecnico),
                Ui.rotulo("Técnico da seleção " + s.nome, "texto-medio")), Ui.espaco(), bVoltar),
                Ui.separadorH(), Ui.linha(26, Ui.dado("Seleção", s.nome), Ui.dado("Confederação", s.confederacao == null ? "—" : s.confederacao),
                        Ui.dado("Força", Ui.forca((int) Math.round(s.forca))), Ui.dado("Títulos", String.valueOf(s.titulos.size()))));
        return Ui.conteudo(p);
    }

    /** Abre a competição no "Ver campeonatos". */
    static void competicao(Hub hub, Torneio t) {
        hub.abrirCompeticao(t.id);
    }
}
