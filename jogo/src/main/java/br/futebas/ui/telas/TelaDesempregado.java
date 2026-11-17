package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.historia.Historia;
import br.futebas.ia.PropostaTecnico;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Mensagem;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Tecnico;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.Tela;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Fluxo;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * O técnico sem emprego: os convites (de clubes, de times da Kings League e de seleções) em
 * cartões, candidaturas (clubes que procuram técnico e vagas abertas da Kings), a carreira até
 * aqui e as últimas mensagens. Dá para esperar uma semana, esperar um convite ou ver o mundo.
 */
public final class TelaDesempregado implements Tela {
    private final Sessao sessao;
    private final BorderPane raiz = new BorderPane();
    private boolean vendoMundo;

    public TelaDesempregado(Sessao sessao) {
        this.sessao = sessao;
        montar();
    }

    private void montar() {
        Jogo j = sessao.jogo;
        Tecnico t = j.mundo().tecnico;
        Button mundo = Ui.botao(vendoMundo ? "Voltar aos convites" : "Ver o mundo", () -> {
            vendoMundo = !vendoMundo;
            montar();
        });
        Button salvar = Ui.botaoTecla("Salvar", "F2", () -> App.atual().salvar());
        Button menu = Ui.botao("Menu principal", () -> {
            if (App.atual().podeSairSemSalvar("Menu principal")) App.atual().irParaInicio();
        });
        for (Button b : List.of(mundo, salvar, menu)) b.setFocusTraversable(false);
        raiz.setTop(Ui.cabecalhoTela(null, "Sem emprego", t.nome + "  ·  " + Textos.dataLonga(j.dataAtual()), null, null,
                Ui.blocoCabecalho(String.valueOf(Math.round(t.reputacao)), "reputação"), Ui.separadorCabecalho(),
                Ui.blocoCabecalho(String.valueOf(t.pontosRanking), "pontos no ranking"), Ui.separadorCabecalho(), mundo, salvar, menu));
        if (vendoMundo) {
            raiz.setCenter(new PainelMundo(sessao).construir());
            raiz.setBottom(null);
            return;
        }
        // as vagas (clubes e Kings) ficam numa lista, como na aba Vagas; convites de seleção à parte
        VBox convites = new VBox(16, PainelVagas.lista(j, this::montar, () -> { }));
        if (j.propostasDeEmprego().stream().anyMatch(p -> p.selecaoPais != null)) convites.getChildren().add(0, convites(j));
        VBox dir = new VBox(16, carreira(j), mensagens(j));
        dir.setPrefWidth(400);
        dir.setMinWidth(360);
        convites.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        dir.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox corpo = new HBox(16, convites, dir);
        corpo.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(convites, Priority.ALWAYS);
        VBox v = Ui.conteudo(corpo);
        raiz.setCenter(Ui.rolar(v));
        if (j.multi()) {
            // multi-treinador: o calendário só anda quando todos mandam jogar; aqui o técnico passa a vez
            raiz.setBottom(Ui.rodape(Ui.rotulo("Vez de " + t.nome + " (" + (j.vez() + 1) + "/" + j.tecnicos().size()
                    + "). As vagas abrem quando um clube demite o técnico.", "rotulo-rodape"), Ui.espaco(),
                    Ui.botaoTecla("Passar a vez", "Espaço", () -> App.atual().jogar(), "primario")));
            return;
        }
        raiz.setBottom(Ui.rodape(Ui.rotulo("As vagas abrem quando um clube demite o técnico (e na virada do ano da Kings).", "rotulo-rodape"), Ui.espaco(),
                Ui.botaoTecla("Aguardar uma semana", "Espaço", () -> esperar(false)),
                Ui.botao("Aguardar uma vaga", () -> esperar(true), "primario")));
    }

    private VBox convites(Jogo j) {
        Mundo m = j.mundo();
        List<PropostaTecnico> ps = j.propostasDeEmprego();
        VBox lista = new VBox(10);
        ps.removeIf(p -> p.selecaoPais == null); // clubes e Kings estão na lista de vagas
        for (PropostaTecnico p : ps) lista.getChildren().add(conviteSelecao(j, p));
        if (ps.isEmpty()) lista.getChildren().add(Ui.vazio("Nenhum convite", "Aguardar convite", () -> esperar(true)));
        return Ui.painelAcoes("Convites de seleção", ps.isEmpty() ? null : Ui.etiqueta(String.valueOf(ps.size()), "verde"), lista);
    }

    private Node conviteSelecao(Jogo j, PropostaTecnico p) {
        Label n = Ui.rotulo(p.clube, "titulo-painel");
        Label exp = Ui.rotulo("", "texto-medio");
        VBox txt = new VBox(3, Ui.linha(8, n, Ui.etiqueta("Seleção", "ambar")), exp, Ui.rotulo(prazo(j, p), "texto-pequeno"));
        HBox.setHgrow(txt, Priority.ALWAYS);
        txt.setMinWidth(0);
        HBox h = new HBox(14, Imagens.bandeira(p.selecaoPais, 34), txt, botoes(j, p, true));
        h.getStyleClass().addAll("convite", "selecao");
        return h;
    }

    private static String prazo(Jogo j, PropostaTecnico p) {
        int faltam = p.semanaLimite - j.mundo().semanaAbsoluta();
        return faltam <= 0 ? "Responda agora" : faltam == 1 ? "Responda até a próxima semana" : "Responda em até " + faltam + " semanas";
    }

    private Node botoes(Jogo j, PropostaTecnico p, boolean selecao) {
        Button aceitar = Ui.botao("Aceitar", () -> {
            if (j.aceitarEmprego(p.id)) {
                if (j.meuClube() != null) App.atual().irParaHub(selecao ? "selecoes" : "jogo");
                else if (sessao.fluxo.soKings()) App.atual().irParaHub("kings");
                else montar();
            }
        }, "primario");
        Button recusar = Ui.botao("Recusar", () -> {
            j.recusarEmprego(p.id);
            montar();
        });
        HBox h = Ui.linha(8, recusar, aceitar);
        h.setAlignment(Pos.CENTER_RIGHT);
        return h;
    }

    private VBox carreira(Jogo j) {
        Tecnico t = j.mundo().tecnico;
        // clube + Kings
        HBox nums = Ui.linha(22, Ui.dado("Jogos", String.valueOf(t.jogos + t.kingsJogos)), Ui.dado("Vitórias", String.valueOf(t.vitorias + t.kingsVitorias)),
                Ui.dado("Empates", String.valueOf(t.empates)), Ui.dado("Derrotas", String.valueOf(t.derrotas + t.kingsDerrotas)),
                Ui.dado("Títulos", String.valueOf(t.titulos.size())));
        VBox p = Ui.painel("Sua carreira", Ui.linha(10, Ui.estrelas(t.reputacao, 15), Ui.rotulo("reputação " + Math.round(t.reputacao), "texto-medio")), nums);
        List<Historia.TemporadaTecnico> l = new ArrayList<>(j.historia().carreiraTecnico);
        Collections.reverse(l);
        if (!l.isEmpty()) {
            TableView<Historia.TemporadaTecnico> tv = Ui.tabela();
            Ui.colNum(tv, "Ano", 48, x -> x.ano);
            Ui.colClube(tv, "Clube", 150, j.mundo(), x -> x.clubeId);
            Ui.col(tv, "V-E-D", 70, x -> x.vitorias + "-" + x.empates + "-" + x.derrotas);
            Ui.col(tv, "Saída", 90, x -> x.saida == null ? "" : x.saida);
            tv.getItems().setAll(l);
            Ui.alturaPorLinhas(tv, Math.min(8, l.size()));
            p.getChildren().add(tv);
        }
        if (!t.titulos.isEmpty()) {
            VBox tit = new VBox(2);
            for (int i = t.titulos.size() - 1, n = 0; i >= 0 && n < 6; i--, n++) tit.getChildren().add(Ui.rotulo(t.titulos.get(i), "texto-medio"));
            p.getChildren().addAll(Ui.rotulo("Títulos", "titulo-menor"), tit);
        }
        return p;
    }

    private VBox mensagens(Jogo j) {
        VBox lista = new VBox(10);
        List<Mensagem> l = j.caixaDeEntrada();
        for (int i = l.size() - 1, n = 0; i >= 0 && n < 6; i--) {
            Mensagem x = l.get(i);
            if ("resultado".equals(x.tipo)) continue;
            n++;
            lista.getChildren().add(new VBox(1, Ui.linha(8, Ui.rotulo(x.titulo, "texto-forte"), Ui.espaco(), Ui.rotulo(Textos.mes(x.semana) + "/" + x.ano, "texto-pequeno")),
                    Ui.paragrafo(x.texto, "texto-medio")));
        }
        if (lista.getChildren().isEmpty()) lista.getChildren().add(Ui.rotulo("Nenhuma mensagem.", "texto-suave"));
        return Ui.painel("Últimas mensagens", lista);
    }

    private void esperar(boolean ateConvite) {
        if (sessao.jogo.multi()) {
            App.atual().jogar(); // multi-treinador: passa a vez
            return;
        }
        if (ateConvite) {
            // "Aguardar convites": a mesma simulação semana a semana (para no convite, na vaga ao alcance ou no fim do ano)
            App.atual().simularTemporada(this::montar);
            return;
        }
        App.atual().trabalhar(ateConvite ? "Aguardando convites..." : "Avançando uma semana...", () -> {
            Fluxo.ResumoTemporada fim = null;
            int limite = ateConvite ? 60 : 1;
            for (int i = 0; i < limite; i++) {
                Fluxo.Rodada r = sessao.fluxo.avancarSemana();
                if (r.fimDeTemporada() != null) fim = r.fimDeTemporada();
                if (!sessao.jogo.propostasDeEmprego().isEmpty() || fim != null) break;
                if (ateConvite && sessao.jogo.vagasDeEmprego().stream().anyMatch(v -> !"Fora do seu alcance".equals(v.interesse()))) break;
            }
            return fim;
        }, fim -> {
            if (fim != null) App.atual().mostrar(new TelaFimTemporada(sessao, fim));
            else montar();
        });
    }

    @Override
    public Parent raiz() { return raiz; }

    @Override
    public boolean tecla(KeyEvent e) {
        if (vendoMundo) {
            if (e.getCode() == KeyCode.ESCAPE) {
                vendoMundo = false;
                montar();
                return true;
            }
            return false;
        }
        if (e.getCode() == KeyCode.SPACE) {
            esperar(false);
            return true;
        }
        return false;
    }
}
