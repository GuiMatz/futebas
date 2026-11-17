package br.futebas.ui.telas;

import br.futebas.competicao.Torneio;
import br.futebas.historia.Historia;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.Tela;
import br.futebas.ui.comp.Foto;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Fluxo;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fim de temporada: a sua campanha (com as tacas conquistadas), o balanço da diretoria, os
 * campeões do ano com os troféus, quem subiu e quem caiu, os prêmios e as manchetes.
 */
public final class TelaFimTemporada implements Tela {
    private final Sessao sessao;
    private final BorderPane raiz = new BorderPane();

    public TelaFimTemporada(Sessao sessao, Fluxo.ResumoTemporada r) {
        this.sessao = sessao;
        Mundo m = sessao.jogo.mundo();
        Clube c = r.clubeId() == null ? null : m.clube(r.clubeId());
        String sub = (r.clube() == null ? "" : r.clube() + "  ·  ") + (m.tecnico == null ? "" : m.tecnico.nome);
        // carreira da Kings: o resumo vem com o id do time da Kings (escudo e cores do time)
        var tk = c == null && r.clubeId() != null ? sessao.jogo.timeKings(r.clubeId()) : null;
        raiz.setTop(Ui.cabecalhoTela(c != null ? Imagens.escudo(c.id, 46) : tk != null ? Imagens.escudo(tk.id, 46) : null, "Temporada " + r.ano() + " encerrada", sub,
                c != null ? c.cor1 : tk != null ? tk.cor1 : null, c != null ? c.cor2 : tk != null ? tk.cor2 : null,
                Ui.blocoCabecalho(String.valueOf(sessao.jogo.ano()), "próxima temporada")));

        VBox v = Ui.conteudo();
        v.setMaxWidth(1240);
        if (r.clube() != null) {
            VBox campanha = campanha(r);
            VBox dir = diretoria(r);
            dir.setMaxHeight(Region.USE_PREF_SIZE);
            HBox topo = new HBox(16, campanha, dir);
            topo.setAlignment(Pos.TOP_LEFT);
            HBox.setHgrow(campanha, Priority.ALWAYS);
            dir.setPrefWidth(360);
            dir.setMinWidth(320);
            v.getChildren().add(topo);
        } else if (r.veredito() != null) {
            v.getChildren().add(Ui.aviso(r.veredito(), "aviso"));
        }
        // campeões em colunas (Mundial · Nacional · Estadual · Kings League); sem a lista de acessos e
        // rebaixamentos nem as manchetes do ano (regra do jogo)
        v.getChildren().add(campeoes(m, r));
        Historia.SelecaoDaLiga sel = selecaoDaMinhaLiga(m, r);
        if (sel != null) {
            VBox ps = Ui.painel("Seleção do campeonato", PainelPremios.campoFixo(sessao.jogo, sel, 540));
            ps.setAlignment(Pos.TOP_CENTER);
            v.getChildren().add(ps);
        }
        v.getChildren().add(premios(m, r));
        javafx.scene.layout.StackPane centro = new javafx.scene.layout.StackPane(v);
        centro.setAlignment(Pos.TOP_CENTER);
        raiz.setCenter(Ui.rolar(centro));

        raiz.setBottom(Ui.rodape(Ui.botao("Ver o mundo", () -> App.atual().irParaHub("mundo")), Ui.espaco(),
                Ui.botaoTecla("Começar " + sessao.jogo.ano(), "Enter", this::seguir, "primario")));
    }

    // ------------------------------------------------------------------ partes
    /**
     * Carrega antes, fora da thread da tela, as imagens que esta tela usa (troféus, escudos dos
     * campeões, fotos dos premiados), nos mesmos tamanhos: a tela monta sem travar na virada do ano.
     */
    public static void preaquecer(Sessao sessao, Fluxo.ResumoTemporada r) {
        if (r == null) return;
        try {
            for (Fluxo.LinhaCampanha l : r.campanha()) {
                Imagens.trofeu(l.competicaoId(), l.ambito(), null, 84);
                Imagens.trofeu(l.competicaoId(), l.ambito(), null, 24);
            }
            for (Historia.Titulo t : r.campeoes()) {
                Imagens.trofeu(t.competicaoId, t.ambito, t.pais, 46);
                if (t.campeaoId != null) Imagens.escudo(t.campeaoId, 34);
            }
            Mundo m = sessao.jogo.mundo();
            for (Historia.Premio p : r.premios()) {
                Jogador j = p.jogadorId == null ? null : m.jogador(p.jogadorId);
                if (j != null) Foto.de(sessao.jogo.foto(j.id), j.dbId, 44, 52);
            }
            if (r.clubeId() != null) Imagens.escudo(r.clubeId(), 46);
        } catch (RuntimeException e) {
            // só um adiantamento: a tela carrega o que faltar
        }
    }

    private VBox campanha(Fluxo.ResumoTemporada r) {
        int jogos = r.vitorias() + r.empates() + r.derrotas();
        int aprov = jogos == 0 ? 0 : (int) Math.round(100.0 * (3 * r.vitorias() + r.empates()) / (3.0 * jogos));
        HBox numeros = Ui.linha(28, numero(r.vitorias(), "vitórias"), numero(r.empates(), "empates"), numero(r.derrotas(), "derrotas"),
                numero(aprov, "% de aproveitamento"));
        VBox p = Ui.painel("Sua temporada", numeros);
        List<Fluxo.LinhaCampanha> titulos = new ArrayList<>();
        for (Fluxo.LinhaCampanha l : r.campanha()) if (l.campeao()) titulos.add(l);
        if (!titulos.isEmpty()) {
            FlowPane tacas = new FlowPane(12, 12);
            for (Fluxo.LinhaCampanha l : titulos) {
                Label n = Ui.nomeCompeticao(l.competicaoId(), Textos.curto(l.competicao()), "texto-forte");
                n.setWrapText(true);
                n.setMaxWidth(150);
                n.setAlignment(Pos.CENTER);
                n.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
                VBox t = new VBox(6, Ui.trofeu(l.competicaoId(), l.ambito(), null, 84), n);
                t.getStyleClass().add("trofeu-caixa");
                t.setPrefWidth(168);
                tacas.getChildren().add(t);
            }
            p.getChildren().addAll(Ui.separadorH(), Ui.rotulo(titulos.size() == 1 ? "Título conquistado" : titulos.size() + " títulos conquistados", "titulo-menor"), tacas);
        }
        if (!r.campanha().isEmpty()) {
            VBox linhas = new VBox(0);
            for (Fluxo.LinhaCampanha l : r.campanha()) {
                Label n = Ui.nomeCompeticao(l.competicaoId(), Textos.curto(l.competicao()));
                n.setMinWidth(0);
                String cor = l.campeao() ? "verde" : l.resultado().startsWith("Vice") ? "azul" : l.resultado().contains("lugar") ? null : "";
                Node res = cor == null || cor.isEmpty() ? Ui.rotulo(l.resultado(), "texto-medio") : Ui.etiqueta(l.resultado(), cor);
                HBox h = Ui.linha(10, Ui.trofeu(l.competicaoId(), l.ambito(), null, 24), n, Ui.espaco(), res);
                h.setStyle("-fx-padding: 5 0 5 0; -fx-border-color: transparent transparent #30353d transparent;");
                linhas.getChildren().add(h);
            }
            p.getChildren().addAll(Ui.separadorH(), linhas);
        }
        return p;
    }

    private static VBox numero(int n, String rotulo) {
        VBox v = new VBox(0, Ui.rotulo(String.valueOf(n), "numero-temporada"), Ui.rotulo(rotulo, "texto-pequeno"));
        v.setMinWidth(Region.USE_PREF_SIZE);
        return v;
    }

    private VBox diretoria(Fluxo.ResumoTemporada r) {
        double conf = Math.max(0, Math.min(100, r.confianca()));
        ProgressBar b = new ProgressBar(conf / 100.0);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setPrefHeight(8);
        if (conf < 35) b.getStyleClass().add("baixa");
        else if (conf < 55) b.getStyleClass().add("media");
        Label numero = Ui.rotulo(Math.round(conf) + "%", "numero-grande");
        numero.setStyle("-fx-text-fill: " + (conf < 35 ? "#ff6a5c" : conf < 55 ? "#e3bd2e" : "#39c47f") + ";");
        String ver = r.veredito() == null ? "" : r.veredito();
        String balanco = null;
        int i = ver.indexOf(" (Balanço: ");
        if (i > 0) {
            balanco = ver.substring(i + 11, ver.length() - (ver.endsWith(".)") ? 2 : 1));
            ver = ver.substring(0, i);
        }
        Label txt = Ui.paragrafo(ver, "veredito-texto");
        boolean kings = r.clubeId() != null && sessao.jogo.mundo().clube(r.clubeId()) == null && sessao.jogo.timeKings(r.clubeId()) != null;
        VBox p = Ui.painel(kings ? "Presidente" : "Diretoria", Ui.linha(10, numero, Ui.rotulo("de confiança", "texto-suave")), b, txt);
        if (balanco != null && !balanco.isBlank()) {
            VBox itens = new VBox(4);
            for (String s : balanco.split(";")) {
                String x = s.trim();
                if (x.isEmpty()) continue;
                itens.getChildren().add(Ui.paragrafo("· " + Character.toUpperCase(x.charAt(0)) + x.substring(1), "texto-medio"));
            }
            p.getChildren().addAll(Ui.separadorH(), Ui.rotulo("O que pesou no balanço", "titulo-menor"), itens);
        }
        return p;
    }

    private VBox campeoes(Mundo m, Fluxo.ResumoTemporada r) {
        String meuPais = r.clubeId() != null && m.clube(r.clubeId()) != null ? m.clube(r.clubeId()).pais : "BRA";
        String[] nomes = {"Mundial", "Nacional", "Estadual", "Kings League"};
        List<List<Historia.Titulo>> cols = new ArrayList<>();
        for (int k = 0; k < 4; k++) cols.add(new ArrayList<>());
        for (Historia.Titulo t : r.campeoes()) {
            if (!relevante(m, t, meuPais)) continue;
            cols.get(coluna(t)).add(t);
        }
        HBox linha = new HBox(14);
        for (int k = 0; k < 4; k++) {
            List<Historia.Titulo> l = cols.get(k);
            l.sort(Comparator.comparingInt((Historia.Titulo t) -> ordem(t, meuPais)).thenComparing(t -> t.pais == null ? "" : t.pais).thenComparing(t -> t.competicao));
            VBox col = new VBox(8);
            for (Historia.Titulo t : l) {
                Label nome = Ui.rotulo(t.campeao, "campeao-nome");
                Label comp = Ui.rotulo(Textos.curto(t.competicao), "campeao-comp");
                nome.setMinWidth(0);
                comp.setMinWidth(0);
                VBox txt = new VBox(2, nome, comp);
                txt.setMinWidth(0);
                HBox.setHgrow(txt, Priority.ALWAYS);
                HBox h = new HBox(10, Ui.trofeu(t.competicaoId, t.ambito, t.pais, 40), Imagens.escudo(t.campeaoId, 30), txt);
                h.setAlignment(Pos.CENTER_LEFT);
                h.getStyleClass().add("cartao-campeao");
                if (t.campeaoId != null && t.campeaoId.equals(r.clubeId())) h.getStyleClass().add("meu");
                col.getChildren().add(h);
            }
            if (l.isEmpty()) col.getChildren().add(Ui.rotulo("—", "texto-suave"));
            VBox p = Ui.painel(nomes[k], col);
            p.setPrefWidth(300);
            p.setMinWidth(0);
            HBox.setHgrow(p, Priority.ALWAYS);
            linha.getChildren().add(p);
        }
        int mostrados = 0;
        for (List<Historia.Titulo> l : cols) mostrados += l.size();
        Node acao = r.campeoes().size() > mostrados ? Ui.botao("Todos os " + r.campeoes().size() + " campeões", () -> App.atual().irParaHub("outros"), "fantasma") : null;
        return Ui.painelAcoes("Campeões de " + r.ano(), acao, linha);
    }

    /** 0 mundial (seleções, continentais, mundiais) · 1 nacional · 2 estadual · 3 Kings League. */
    private static int coluna(Historia.Titulo t) {
        String a = t.ambito == null ? "" : t.ambito;
        if ("kings".equals(a) || (t.competicaoId != null && t.competicaoId.startsWith("kings"))) return 3;
        if ("estadual".equals(a) || "regional".equals(a)) return 2;
        if (t.pais == null || "continental".equals(a) || "selecoes".equals(a) || "mundial".equals(a)) return 0;
        return 1;
    }

    /** Destaques: o país do técnico, competições continentais e de seleções, e a 1ª divisão dos países em detalhe. */
    private static boolean relevante(Mundo m, Historia.Titulo t, String meuPais) {
        if (coluna(t) == 3) return true; // Kings League: todos os campeões (ligas, copas e mundiais)
        if (t.pais == null || "continental".equals(t.ambito) || "selecoes".equals(t.ambito) || "mundial".equals(t.ambito)) return true;
        if (t.pais.equals(meuPais)) return true;
        var p = m.pais(t.pais);
        if (p != null && !p.detalhado) return false;
        Torneio tt = m.torneioAnterior(t.competicaoId);
        if (tt == null) tt = m.torneio(t.competicaoId);
        return "nacional".equals(t.ambito) && tt != null && tt.divisao != null && tt.divisao == 1;
    }

    private static int ordem(Historia.Titulo t, String meuPais) {
        if ("selecoes".equals(t.ambito)) return 0;
        if (t.pais == null || "continental".equals(t.ambito) || "mundial".equals(t.ambito)) return 1;
        if (t.pais.equals(meuPais)) return 2 + PainelMundo.ordemCompeticao(t.competicaoId, t.ambito);
        return 100 + PainelMundo.ordemCompeticao(t.competicaoId, t.ambito);
    }

    private VBox movimentos(String titulo, List<Fluxo.Movimento> l, boolean sobe, String meu) {
        VBox lista = new VBox(0);
        for (Fluxo.Movimento x : l) {
            Label seta = Ui.rotulo(sobe ? "▲" : "▼", sobe ? "seta-sobe" : "seta-cai");
            Label n = Ui.rotulo(x.nome(), x.clubeId().equals(meu) ? "texto-forte" : "label");
            n.setMinWidth(0);
            HBox h = Ui.linha(8, seta, Imagens.escudo(x.clubeId(), 20), n, Ui.espaco(), Ui.rotulo(x.texto(), "texto-pequeno"));
            h.getStyleClass().add("movimento");
            lista.getChildren().add(h);
        }
        if (l.isEmpty()) lista.getChildren().add(Ui.rotulo(sobe ? "Ninguém subiu de divisão no seu país." : "Ninguém caiu no seu país.", "texto-suave"));
        return Ui.painelAcoes(titulo, l.isEmpty() ? null : Ui.etiqueta(String.valueOf(l.size()), sobe ? "verde" : "vermelha"), lista);
    }

    private Historia.SelecaoDaLiga selecaoDaMinhaLiga(Mundo m, Fluxo.ResumoTemporada r) {
        if (r.clubeId() == null) return null;
        try {
            for (Torneio t : m.torneiosAnteriores) {
                if (!"nacional".equals(t.ambito) || t.divisao == null || !t.participantes.contains(r.clubeId())) continue;
                Historia.SelecaoDaLiga s = sessao.jogo.selecaoDaLiga(t.id, r.ano());
                if (s == null) s = sessao.jogo.selecaoDaLiga(t.id, r.ano() + 1);
                if (s != null && !s.time.isEmpty()) return s;
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    private VBox premios(Mundo m, Fluxo.ResumoTemporada r) {
        VBox lista = new VBox(10);
        for (Historia.Premio p : r.premios()) {
            if (!"bola_de_ouro".equals(p.tipo) && !"revelacao".equals(p.tipo)) continue; // só os dois principais (regra do jogo)
            Jogador j = p.jogadorId == null ? null : m.jogador(p.jogadorId);
            Node foto = Foto.de(j == null ? null : sessao.jogo.foto(j.id), j == null ? null : j.dbId, 44, 52);
            Label t = Ui.rotulo(p.titulo, "texto-pequeno");
            Label n = Ui.rotulo(p.jogador, "titulo-painel");
            Label c = Ui.rotulo(p.clube == null ? "" : p.clube, "texto-medio");
            VBox txt = new VBox(1, t, n, c);
            txt.setMinWidth(0);
            lista.getChildren().add(Ui.linha(12, foto, txt));
        }
        if (lista.getChildren().isEmpty()) lista.getChildren().add(Ui.rotulo("Sem prêmios nesta temporada.", "texto-suave"));
        return Ui.painel("Prêmios", lista);
    }

    private VBox manchetes(Fluxo.ResumoTemporada r) {
        VBox lista = new VBox(0);
        for (Historia.Evento e : r.manchetes()) {
            VBox x = new VBox(2, Ui.rotulo(Textos.mes(e.semana) + "/" + e.ano, "manchete-data"), Ui.paragrafo(e.texto, "manchete-texto"));
            x.getStyleClass().add("manchete");
            x.setStyle("-fx-padding: 6 0 6 0;");
            lista.getChildren().add(x);
        }
        if (r.manchetes().isEmpty()) lista.getChildren().add(Ui.rotulo("Um ano sem grandes notícias.", "texto-suave"));
        return Ui.painel("Manchetes do ano", lista);
    }

    private void seguir() {
        App.atual().irParaHub("jogo");
    }

    @Override
    public Parent raiz() { return raiz; }

    @Override
    public boolean tecla(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
            seguir();
            return true;
        }
        return false;
    }
}
