package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mensagem;
import br.futebas.mundo.Mundo;
import br.futebas.partida.Escalado;
import br.futebas.partida.Escalador;
import br.futebas.partida.TimePartida;
import br.futebas.ui.App;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * A página inicial da central, em três colunas: à esquerda o próximo jogo (com o JOGAR) e os
 * compromissos seguintes; no meio as notícias (caixa de entrada e mundo); à direita as tabelas
 * de todas as competições que o clube disputa, com o clube em destaque.
 */
final class PainelProximoJogo implements Hub.Painel {
    private final Hub hub;

    /** Clube do técnico ou, na carreira da Kings, o time da Kings (o painel é o mesmo). */
    private String meuId;
    private br.futebas.kings.LigaKings.TimeKings tk;

    /** Colunas da página e os quadros "de completar" que o equilíbrio distribui entre elas. */
    private VBox esquerda, meio, direita;
    private HBox colunas;
    private record Opcional(Node no, VBox preferida) {}
    private final List<Opcional> opcionais = new java.util.ArrayList<>();
    private boolean equilibrioPendente;
    /** Linhas a mais (jogos, manchetes) que só aparecem se a coluna delas tiver espaço sobrando. */
    private record Extras(List<Node> linhas) {}
    private final List<Extras> extras = new java.util.ArrayList<>();

    PainelProximoJogo(Hub hub) {
        this.hub = hub;
    }

    private Torneio torneio(Jogo j, Partida p) {
        Torneio t = hub.sessao().fluxo.torneioDe(p);
        if (t != null && t.partidas.contains(p)) return t;
        for (Torneio x : j.competicoesDoParticipante(meuId)) if (x.partidas.contains(p)) return x;
        return t;
    }

    @Override
    public Node construir() {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        Clube c = hub.clube();
        tk = c == null ? j.meuTimeKings() : null;
        meuId = c != null ? c.id : tk != null ? tk.id : null;
        VBox v = Ui.conteudo();
        extras.clear();
        if (meuId == null) return Ui.rolar(v);
        avisos(j, v);

        Partida p = c != null ? hub.sessao().fluxo.proximoJogo() : hub.sessao().fluxo.proximoJogoKings();
        Node jogo = p == null ? Ui.painelAcoes("Próximo jogo", null, Ui.vazio(c != null ? "Sem jogos marcados"
                : "Nenhum jogo marcado: a temporada da Kings começa na 2ª semana do ano.", "Avançar", () -> App.atual().jogar())) : confronto(j, m, c, p);
        esquerda = new VBox(16, jogo, proximos(j, m, c));
        meio = new VBox(16, noticias(j, m, c));
        direita = tabelas(j, m, c);
        esquerda.setPrefWidth(320);
        esquerda.setMinWidth(300);
        esquerda.setMaxWidth(340);
        direita.setPrefWidth(330);
        direita.setMinWidth(300);
        direita.setMaxWidth(360);
        meio.setMinWidth(260);
        // quadros que completam as colunas (requisito: sem espaços vazios); vão para a coluna mais curta
        opcionais.clear();
        opcional(ultimoResultado(j, m), esquerda);
        opcional(artilharia(j, m, c), direita);
        opcional(destaques(j, m, c), meio);
        if (c != null) opcional(financas(j, c), direita);
        colunas = new HBox(16, esquerda, meio, direita);
        HBox.setHgrow(meio, Priority.ALWAYS);
        colunas.setAlignment(Pos.TOP_LEFT);
        colunas.setFillHeight(true);
        for (Opcional o : opcionais) o.preferida().getChildren().add(o.no());
        for (Extras x : extras) for (Node n : x.linhas()) mostrar(n, false);
        colunas.sceneProperty().addListener((o, a, b) -> { if (b != null) agendarEquilibrio(); });
        colunas.widthProperty().addListener((o, a, b) -> { if (Math.abs(b.doubleValue() - a.doubleValue()) > 40) agendarEquilibrio(); });
        v.getChildren().add(colunas);
        // requisito (13/11): as colunas vão até o pé da tela, sem faixa vazia embaixo
        javafx.scene.control.ScrollPane sp = Ui.rolar(v);
        return sp;
    }

    // ------------------------------------------------------------------ notícias
    /** Manchetes na página inicial: poucas e compactas (o resto fica na caixa de entrada). */
    static final int MANCHETES = 7, MANCHETES_MAX = 10;

    private VBox noticias(Jogo j, Mundo m, Clube c) {
        int focoMax = br.futebas.ui.Opcoes.noticias().focoMaximo;
        List<br.futebas.ui.modelo.Manchetes.Item> l = br.futebas.ui.modelo.Manchetes.escolher(j.caixaDeEntrada(), j.noticias(120),
                j.ano(), j.semana(), c == null && tk != null, focoMax, MANCHETES_MAX);
        VBox lista = new VBox(0);
        List<Node> mais = new java.util.ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            Node n = manchete(j, l.get(i));
            lista.getChildren().add(n);
            if (i >= MANCHETES) mais.add(n);
        }
        extras.add(new Extras(mais));
        if (l.isEmpty()) lista.getChildren().add(Ui.vazio("Sem notícias por enquanto.", null, null));
        Button todas = Ui.botao("Ver todas  F9", () -> hub.selecionar("caixa"), "fantasma");
        return Ui.painelAcoes("Notícias", todas, lista);
    }

    /** Uma linha de manchete: etiqueta, título (até duas linhas), linha fina numa linha só e a data. */
    private Node manchete(Jogo j, br.futebas.ui.modelo.Manchetes.Item x) {
        Mensagem msg = x.mensagem();
        var n = x.noticia();
        Label etq = msg != null ? PainelCaixa.etiquetaTipo(msg.tipo) : Ui.etiqueta(n.etiqueta(), n.cor());
        etq.setMinWidth(84);
        etq.setPrefWidth(84);
        etq.setAlignment(Pos.CENTER);
        String titulo = msg != null ? msg.titulo : n.titulo;
        String fina = msg != null ? msg.texto : n.texto;
        boolean forte = msg != null ? !msg.lida : n.foco == 0;
        Label t = Ui.rotulo(titulo, "hub-noticia-titulo");
        if (forte) t.getStyleClass().add("forte");
        t.setWrapText(true);
        t.setMinWidth(0);
        t.setMaxHeight(38);
        VBox texto = new VBox(1, t);
        texto.setMinWidth(0);
        if (fina != null && !fina.isBlank()) {
            Label f = Ui.rotulo(fina.replace('\n', ' '), "hub-noticia-fina");
            f.setMinWidth(0);
            texto.getChildren().add(f);
        }
        HBox.setHgrow(texto, Priority.ALWAYS);
        Label data = Ui.rotulo(Textos.mes(x.semana()) + "/" + (x.ano() % 100), "manchete-data");
        data.setMinWidth(Region.USE_PREF_SIZE);
        HBox linha = new HBox(10, etq, texto, data);
        linha.setAlignment(Pos.TOP_LEFT);
        linha.getStyleClass().add("hub-noticia");
        if (!forte) linha.getStyleClass().add("lida");
        if (msg != null) {
            linha.setCursor(javafx.scene.Cursor.HAND);
            linha.setOnMouseClicked(e -> PainelCaixa.abrirMensagem(hub, msg));
            Tooltip.install(linha, new Tooltip("Abrir na caixa de entrada"));
        } else if (n.competicaoId != null || n.clubeId != null) {
            linha.setCursor(javafx.scene.Cursor.HAND);
            linha.setOnMouseClicked(e -> {
                if (n.clubeId != null) hub.abrirParticipante(n.clubeId);
                else hub.abrirCompeticao(n.competicaoId);
            });
        }
        return linha;
    }

    // ------------------------------------------------------------------ equilíbrio das colunas
    private void opcional(Node no, VBox preferida) {
        if (no != null) opcionais.add(new Opcional(no, preferida));
    }

    private void agendarEquilibrio() {
        if (equilibrioPendente) return;
        equilibrioPendente = true;
        javafx.application.Platform.runLater(() -> {
            equilibrioPendente = false;
            equilibrar();
        });
    }

    /**
     * Põe cada quadro "de completar" na coluna mais curta (na preferida, se ela estiver no máximo
     * 60 px acima da mais curta) e estica o último quadro de cada coluna até o fim da mais alta:
     * as três colunas terminam juntas, sem buraco embaixo.
     */
    private void equilibrar() {
        if (colunas == null || colunas.getScene() == null) return;
        VBox[] cols = {esquerda, meio, direita};
        for (VBox col : cols) {
            for (Opcional o : opcionais) col.getChildren().remove(o.no());
            for (Node x : col.getChildren()) VBox.setVgrow(x, Priority.NEVER);
        }
        for (Extras x : extras) for (Node n : x.linhas()) mostrar(n, false);
        // até a altura da tela, cabe tudo; passando dela, o quadro só entra se não deixar a coluna
        // muito mais alta que as outras (senão sobra um buraco embaixo das outras duas)
        double tela = colunas.getScene().getHeight() - colunas.localToScene(0, 0).getY() - 24;
        for (Opcional o : opcionais) {
            colunas.applyCss();
            colunas.layout();
            VBox menor = cols[0];
            double maior = 0;
            for (VBox col : cols) {
                if (altura(col) < altura(menor)) menor = col;
                maior = Math.max(maior, altura(col));
            }
            double limite = Math.max(tela, maior + SOBRA_MAX);
            VBox alvo = altura(o.preferida()) <= altura(menor) + 60 ? o.preferida() : menor;
            if (!cabe(o.no(), alvo, limite) && (alvo == menor || !cabe(o.no(), menor, limite))) continue;
        }
        // linhas a mais onde sobrou espaço (sem passar da coluna mais alta)
        colunas.applyCss();
        colunas.layout();
        double maior = 0;
        for (VBox col : cols) maior = Math.max(maior, altura(col));
        for (Extras x : extras) {
            VBox col = colunaDe(x.linhas());
            if (col == null) continue;
            for (Node n : x.linhas()) {
                mostrar(n, true);
                colunas.applyCss();
                colunas.layout();
                if (altura(col) > maior + 2) {
                    mostrar(n, false);
                    break;
                }
            }
        }
        for (VBox col : cols) if (!col.getChildren().isEmpty()) VBox.setVgrow(col.getChildren().get(col.getChildren().size() - 1), Priority.ALWAYS);
        // requisito (13/11): as colunas vão até o pé da tela (altura fixada aqui, uma vez; binding causava laço de layout)
        colunas.setMinHeight(Math.max(0, tela));
        colunas.requestLayout();
    }

    /** Quanto um quadro pode deixar a coluna dele mais alta que a mais alta das outras (px), depois da altura da tela. */
    private static final double SOBRA_MAX = 90;

    private static void mostrar(Node n, boolean sim) {
        n.setVisible(sim);
        n.setManaged(sim);
    }

    /** A coluna (das três) em que as linhas estão agora. */
    private VBox colunaDe(List<Node> linhas) {
        if (linhas.isEmpty()) return null;
        for (javafx.scene.Parent p = linhas.get(0).getParent(); p != null; p = p.getParent())
            if (p == esquerda || p == meio || p == direita) return (VBox) p;
        return null;
    }

    /** Põe o quadro na coluna e mede; passou do limite, tira de novo. */
    private boolean cabe(Node no, VBox col, double limite) {
        col.getChildren().add(no);
        colunas.applyCss();
        colunas.layout();
        if (altura(col) <= limite) return true;
        col.getChildren().remove(no);
        return false;
    }

    private static double altura(VBox col) {
        return col.getChildren().isEmpty() ? 0 : col.prefHeight(col.getWidth() > 0 ? col.getWidth() : -1);
    }

    // ------------------------------------------------------------------ tabelas das competições do clube
    private VBox tabelas(Jogo j, Mundo m, Clube c) {
        VBox v = new VBox(16);
        List<Torneio> ts = competicoes(j, c);
        for (Torneio t : ts) v.getChildren().add(tabela(j, m, t));
        if (ts.isEmpty()) v.getChildren().add(Ui.painel("Tabelas", Ui.vazio("Sem competições", null, null)));
        return v;
    }

    /** Competições do ano do seu time (clube ou time da Kings), na ordem das telas. */
    private List<Torneio> competicoes(Jogo j, Clube c) {
        List<Torneio> ts = new java.util.ArrayList<>();
        if (c != null) {
            for (Torneio t : j.competicoesDoAno()) if (t.participantes.contains(c.id)) ts.add(t);
        } else {
            for (Torneio t : j.competicoesDoParticipante(meuId)) if (t.ano == j.ano()) ts.add(t);
        }
        ts.sort(java.util.Comparator.comparingInt((Torneio t) -> PainelMundo.ordemCompeticao(t.id, t.ambito)));
        return ts;
    }

    /**
     * Requisito (12/11/2026): sem as faixas de aviso no topo da página inicial (convite,
     * negociações, leilões, escalação com desfalque); isso já está na caixa de entrada, nas Vagas,
     * no Mercado e na conferência da escalação antes do jogo. Fica só o draft da Kings, que é a vez
     * do técnico agir.
     */
    private void avisos(Jogo j, VBox v) {
        if (j.estadoDraft() != null) v.getChildren().add(Ui.aviso("Draft da Kings League: é a sua vez de escolher.", "aviso-verde",
                Ui.botao("Abrir o draft", () -> hub.selecionar("kings"))));
    }

    private Node confronto(Jogo j, Mundo m, Clube c, Partida p) {
        Torneio t = torneio(j, p);
        String fase = t == null ? "" : Textos.fase(t, p);
        boolean casa = meuId.equals(p.mandante);
        HBox cab = Ui.linha(10);
        if (t != null) cab.getChildren().add(Ui.trofeu(t.id, t.ambito, t.pais, 30));
        Label nomeT = t == null ? Ui.rotulo("Partida", "titulo-painel") : Ui.nomeCompeticao(t.id, Textos.curto(t.nome), "titulo-painel");
        nomeT.setMinWidth(0);
        VBox ct = new VBox(0, nomeT, Ui.rotulo((fase.isEmpty() ? "" : fase + "  ·  ") + Textos.dataCurta(p.data()), "texto-suave"));
        ct.setMinWidth(0);
        cab.getChildren().add(ct);

        VBox a = lado(m, p.mandante);
        VBox b = lado(m, p.visitante);
        Label x = Ui.rotulo("×", "numero-grande");
        x.setStyle("-fx-text-fill: #59616d;");
        HBox duelo = new HBox(8, a, x, b);
        duelo.setAlignment(Pos.CENTER);
        HBox.setHgrow(a, Priority.ALWAYS);
        HBox.setHgrow(b, Priority.ALWAYS);

        Clube mand = m.clube(p.mandante);
        boolean neutro = t != null && t.finalNeutra && p.rodadaMata >= 0 && p.fase == t.fases.size() - 1;
        String local = tk != null ? "Kings League · 7x7" : neutro ? "Campo neutro" : mand != null && mand.estadio != null ? mand.estadio : "";
        Button jogar = Ui.botao("JOGAR", () -> App.atual().jogar(), "jogar-grande");
        jogar.setFocusTraversable(false);
        jogar.setMaxWidth(Double.MAX_VALUE);
        Label tatica = Ui.rotulo(c != null ? c.tatica.formacao + "  ·  " + c.tatica.estilo.nome
                : tk.formacao + "  ·  " + (tk.titulares == null || tk.titulares.isEmpty() ? "escalação automática" : "sua escalação"), "texto-suave");
        Label onde = Ui.rotulo((casa ? "Em casa" : "Fora") + (local.isEmpty() ? "" : "  ·  " + local), "texto-medio");
        onde.setMinWidth(0);
        javafx.scene.control.CheckBox aux = new javafx.scene.control.CheckBox("Delegar ao auxiliar");
        aux.setSelected(j.delegadoAoAuxiliar(p.torneioId, p.id));
        aux.setFocusTraversable(false);
        aux.setOnAction(e -> j.delegarAoAuxiliar(p.torneioId, p.id, aux.isSelected()));
        VBox card = Ui.painelAcoes(null, null, cab, duelo, Ui.separadorH(), onde);
        if (c != null) card.getChildren().add(aux);
        card.getChildren().addAll(Ui.linha(8, tatica, Ui.espaco(), Ui.botao("Escalação", () -> hub.selecionar("escalacao"), "fantasma")), jogar);
        card.setSpacing(12);
        return card;
    }

    private VBox lado(Mundo m, String id) {
        VBox v = new VBox(6);
        v.setAlignment(Pos.TOP_CENTER);
        v.setMinWidth(0);
        v.setPrefWidth(120);
        Clube c = m.clube(id);
        v.getChildren().add(Ui.escudo(id, 64));
        Label nome = Ui.nomeClube(id, m.nomeParticipante(id), "titulo-painel");
        nome.setStyle("-fx-text-fill: -tinta;");
        nome.setWrapText(true);
        nome.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        nome.setAlignment(Pos.CENTER);
        // o nome ocupa sempre a altura de duas linhas: força e forma ficam alinhadas dos dois lados
        javafx.scene.layout.StackPane caixaNome = new javafx.scene.layout.StackPane(nome);
        caixaNome.setMinHeight(46);
        caixaNome.setPrefHeight(46);
        caixaNome.setMaxHeight(46);
        caixaNome.setAlignment(Pos.CENTER);
        v.getChildren().add(caixaNome);
        if (c != null) {
            int forca = (int) Math.round(Escalador.forcaTime(m, c));
            HBox info = Ui.linha(8, Ui.forca(forca));
            info.setAlignment(Pos.CENTER);
            HBox forma = Ui.forma(c.forma, 5);
            forma.setAlignment(Pos.CENTER);
            v.getChildren().addAll(info, forma);
        } else {
            br.futebas.kings.LigaKings.TimeKings k = hub.jogo().timeKings(id);
            if (k != null) {
                List<Double> fs = new java.util.ArrayList<>();
                for (var a : k.elenco) fs.add(a.forca);
                fs.sort(java.util.Comparator.reverseOrder());
                double s = 0;
                int n = Math.min(7, fs.size());
                for (int i = 0; i < n; i++) s += fs.get(i);
                HBox info = Ui.linha(8, Ui.forca(n == 0 ? 0 : (int) Math.round(s / n)));
                info.setAlignment(Pos.CENTER);
                List<String> forma = new java.util.ArrayList<>();
                for (Partida x : hub.jogo().partidasDoParticipante(id)) if (x.jogada) forma.add(Textos.resultadoPara(x, id));
                HBox fh = Ui.linha(3);
                for (int i = Math.max(0, forma.size() - 5); i < forma.size(); i++) fh.getChildren().add(Ui.forma(forma.get(i)));
                if (fh.getChildren().isEmpty()) fh.getChildren().add(Ui.rotulo("sem jogos", "texto-pequeno"));
                fh.setAlignment(Pos.CENTER);
                v.getChildren().addAll(info, fh);
            }
        }
        return v;
    }

    private VBox proximos(Jogo j, Mundo m, Clube c) {
        VBox v = Ui.painelAcoes("Próximos jogos", Ui.botao("Calendário  F6", () -> hub.selecionar("calendario"), "fantasma"));
        List<Partida> l = new java.util.ArrayList<>();
        if (c != null) l = j.proximasPartidas(c.id, 11);
        else for (Partida x : j.partidasDoParticipante(meuId)) if (!x.jogada && l.size() < 11) l.add(x);
        if (!l.isEmpty()) l = l.subList(1, l.size());
        if (l.isEmpty()) v.getChildren().add(Ui.vazio("Nenhum jogo marcado.", null, null));
        VBox g = new VBox(6);
        List<Node> mais = new java.util.ArrayList<>();
        int r = 0;
        for (Partida p : l) {
            Torneio t = torneio(j, p);
            boolean casa = meuId.equals(p.mandante);
            String adv = casa ? p.visitante : p.mandante;
            Label data = Ui.rotulo(Textos.dataCurta(p.data()), "texto-suave");
            data.setMinWidth(72);
            Label nome = Ui.nomeClube(adv, m.nomeParticipante(adv));
            HBox clube = Ui.linha(8, Ui.escudo(adv, 20), nome);
            nome.setMinWidth(0);
            clube.setMinWidth(0);
            if (t != null) Tooltip.install(clube, new Tooltip(Textos.curto(t.nome)));
            Label cf = Ui.etiqueta(casa ? "C" : "F", casa ? "verde" : null);
            HBox linha = Ui.linha(10, data, cf, clube);
            HBox.setHgrow(clube, Priority.ALWAYS);
            g.getChildren().add(linha);
            if (r++ >= 5) mais.add(linha);
        }
        if (r > 0) v.getChildren().add(g);
        extras.add(new Extras(mais));
        return v;
    }

    /** Tabela da fase atual do torneio (no mata-mata, a última fase de pontos já iniciada). */
    static List<LinhaTabela> tabelaAtual(Torneio t) {
        FaseExec f = t.faseAtual();
        if (f == null || f.mataMata()) {
            for (int i = t.fases.size() - 1; i >= 0; i--) if (!t.fases.get(i).mataMata() && t.fases.get(i).iniciada) return t.tabela(i, 0);
            return t.tabelaGeral();
        }
        return t.tabela(f.indice, 0);
    }

    private VBox tabela(Jogo j, Mundo m, Torneio t) {
        String cid = meuId;
        List<LinhaTabela> ls;
        FaseExec f = t.faseAtual();
        String nota = null;
        if (f != null && f.mataMata()) {
            String txt = t.campeao != null ? "Campeão: " + m.nomeParticipante(t.campeao)
                    : (f.nome == null ? "Mata-mata" : f.nome) + (f.confrontos.stream().anyMatch(x -> cid.equals(x.perdedor)) ? "  ·  eliminado" : "");
            // liga com playoffs (os splits da Kings): durante os playoffs a tabela da fase de pontos continua valendo
            FaseExec liga = null;
            for (int i = f.indice - 1; i >= 0 && liga == null; i--) {
                FaseExec x = t.fases.get(i);
                if (!x.mataMata() && x.iniciada && x.numGrupos <= 1) liga = x;
            }
            if (liga == null || t.campeao != null) {
                HBox cab = Ui.linha(8, Ui.trofeu(t.id, t.ambito, t.pais, 22), Ui.nomeCompeticao(t.id, Textos.curto(t.nome), "titulo-painel"));
                return Ui.painelAcoes(null, null, cab, Ui.paragrafo(txt, "texto-medio"));
            }
            nota = txt;
            f = liga;
        }
        int grupo = 0;
        if (f != null && !f.mataMata() && f.numGrupos > 1) {
            for (int g = 0; g < f.numGrupos; g++) for (LinhaTabela x : t.tabela(f.indice, g)) if (x.clubeId.equals(cid)) grupo = g;
            ls = t.tabela(f.indice, grupo);
        } else if (nota != null) {
            ls = t.tabela(f.indice, 0);
        } else {
            // a tabela vem do próprio torneio: os da Kings ficam na liga, fora da lista do ano, e buscar
            // pelo id podia achar outro torneio (a mini tabela ficava em "Não começou" com o split andando)
            ls = tabelaAtual(t);
        }
        boolean comecou = false;
        for (LinhaTabela x : ls) if (x.jogos > 0) comecou = true;
        int meu = 0;
        for (int i = 0; i < ls.size(); i++) if (ls.get(i).clubeId.equals(cid)) meu = i;
        Zonas zonas = Zonas.de(t, f, ls.size());
        int ini = Math.max(0, Math.min(meu - 3, ls.size() - 7)), fim = Math.min(ls.size(), ini + 7);
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(5);
        Label hj = Ui.rotulo("J", "texto-pequeno"), hsg = Ui.rotulo("SG", "texto-pequeno"), hp = Ui.rotulo("P", "texto-pequeno");
        for (Label x : List.of(hj, hsg, hp)) x.setMinWidth(Region.USE_PREF_SIZE);
        g.addRow(0, Ui.rotulo("#", "texto-pequeno"), Ui.rotulo("Clube", "texto-pequeno"), hj, hsg, hp);
        int r = 1;
        for (int i = ini; i < fim; i++) {
            LinhaTabela l = ls.get(i);
            boolean eu = l.clubeId.equals(cid);
            Label pos = Ui.rotulo(String.valueOf(i + 1), "zona");
            Zonas.Zona zz = zonas.porPosicao.get(i + 1);
            if (zz != null) pos.getStyleClass().add(zz.classe());
            HBox cl = Ui.clube(m, l.clubeId, 18);
            cl.setMinWidth(0);
            if (eu) {
                cl.getChildren().get(1).setStyle("-fx-font-weight: bold; -fx-text-fill: -acento-nav;");
            }
            Label pts = Ui.rotulo(String.valueOf(l.pontos), "pontos-destaque");
            Label jg = Ui.rotulo(String.valueOf(l.jogos), "numero"), sg = Ui.rotulo(String.valueOf(l.saldo()), "numero");
            for (Label x : List.of(pos, jg, sg, pts)) x.setMinWidth(Region.USE_PREF_SIZE);
            g.addRow(r++, pos, cl, jg, sg, pts);
            GridPane.setHgrow(cl, Priority.ALWAYS);
        }
        String titulo = Textos.curto(t.nome) + (f != null && f.numGrupos > 1 ? "  ·  Grupo " + (char) ('A' + grupo) : "");
        Label lt = Ui.rotulo(titulo, "titulo-painel");
        lt.setMinWidth(0);
        HBox cab = Ui.linha(8, Imagens.trofeu(t.id, t.ambito, t.pais, 22), lt);
        cab.setCursor(javafx.scene.Cursor.HAND);
        cab.setOnMouseClicked(e -> hub.abrirCompeticao(t.id));
        VBox v = Ui.painelAcoes(null, null, cab);
        if (nota != null) v.getChildren().add(Ui.rotulo(nota, "texto-medio"));
        if (ls.isEmpty() || !comecou) {
            // sem nenhum jogo: só a data do primeiro, sem a tabela de zeros
            Partida pri = null;
            for (Partida x : t.partidas) if (pri == null || x.sa < pri.sa || (x.sa == pri.sa && x.dia < pri.dia)) pri = x;
            v.getChildren().add(Ui.rotulo("Não começou" + (pri == null ? "" : "  ·  começa " + Textos.dataCurta(pri.data())), "texto-suave"));
        } else {
            v.getChildren().add(g);
            // as cores da mini tabela com o que significam (sobe, cai, classifica), numa linha
            if (!zonas.legenda.isEmpty()) v.getChildren().add(zonas.legendaCompacta());
        }
        return v;
    }

    // ------------------------------------------------------------------ quadros de completar
    /** Último jogo do seu time: placar, quem fez os gols (se a partida foi narrada) e a forma recente. */
    private VBox ultimoResultado(Jogo j, Mundo m) {
        List<Partida> ps = j.partidasDoParticipante(meuId);
        Partida p = null;
        List<String> forma = new java.util.ArrayList<>();
        for (Partida x : ps) {
            if (!x.jogada || x.golsMandante == null || x.golsVisitante == null) continue;
            p = x;
            forma.add(Textos.resultadoPara(x, meuId));
        }
        if (p == null) return null;
        Torneio t = torneio(j, p);
        String res = Textos.resultadoPara(p, meuId);
        VBox v = Ui.painelAcoes("Último resultado", Ui.forma(res));

        HBox cab = Ui.linha(8);
        if (t != null) cab.getChildren().add(Ui.trofeu(t.id, t.ambito, t.pais, 18));
        Label comp = Ui.rotulo((t == null ? "" : Textos.curto(t.nome) + "  ·  ") + Textos.dataCurta(p.data()), "texto-suave");
        comp.setMinWidth(0);
        cab.getChildren().add(comp);

        // gols: da última partida narrada, se for esta (mesmos times e placar)
        br.futebas.partida.ResultadoPartida r = tk != null ? j.ultimaPartidaKings() : j.ultimaPartidaNarrada();
        boolean temGols = r != null && p.mandante.equals(r.mandanteId) && p.visitante.equals(r.visitanteId)
                && r.golsMandante == p.golsMandante && r.golsVisitante == p.golsVisitante;
        // autor -> minutos (na ordem do jogo): "Ricardo Batista 38', 40'"
        List<java.util.Map<String, List<String>>> autoresPorLado = List.of(new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>());
        if (temGols) {
            boolean achou = false;
            for (br.futebas.partida.Lance l : r.lances) {
                if (!"gol".equals(l.tipo) || l.lado < 0 || l.lado > 1 || l.jogador == null) continue;
                autoresPorLado.get(l.lado).computeIfAbsent(l.jogador, k -> new java.util.ArrayList<>()).add(l.relogio());
                achou = true;
            }
            if (!achou) for (int[] g : r.gols) {
                Jogador a = g[1] > 0 ? m.jogador(g[1]) : null;
                if (a == null || g[0] < 0 || g[0] > 1) continue;
                String nome = a.nomeExibicao() + (g.length > 4 && g[4] == 1 ? " (contra)" : "");
                autoresPorLado.get(g[0]).computeIfAbsent(nome, k -> new java.util.ArrayList<>()).add(g[2] + "’");
            }
        }
        List<String> golsM = new java.util.ArrayList<>(), golsV = new java.util.ArrayList<>();
        for (int lado = 0; lado < 2; lado++) {
            List<String> dest = lado == 0 ? golsM : golsV;
            int k = 0, n = autoresPorLado.get(lado).size();
            for (var e : autoresPorLado.get(lado).entrySet()) {
                if (k++ == 4 && n > 5) {
                    dest.add("+" + (n - 4));
                    break;
                }
                dest.add(e.getKey() + " " + String.join(", ", e.getValue()));
            }
        }
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(2);
        int lin = 0;
        for (int lado = 0; lado < 2; lado++) {
            String id = lado == 0 ? p.mandante : p.visitante;
            int gols = lado == 0 ? p.golsMandante : p.golsVisitante;
            Label nome = Ui.nomeClube(id, m.nomeParticipante(id), meuId.equals(id) ? "texto-forte" : "label");
            nome.setMinWidth(0);
            HBox clube = Ui.linha(8, Ui.escudo(id, 22), nome);
            clube.setMinWidth(0);
            Integer pen = lado == 0 ? p.penaltisMandante : p.penaltisVisitante;
            Label placar = Ui.rotulo(gols + (pen != null ? " (" + pen + ")" : ""), "hub-placar");
            placar.setMinWidth(Region.USE_PREF_SIZE);
            g.add(clube, 0, lin);
            g.add(placar, 1, lin);
            GridPane.setHgrow(clube, Priority.ALWAYS);
            GridPane.setHalignment(placar, javafx.geometry.HPos.RIGHT);
            lin++;
            List<String> autores = lado == 0 ? golsM : golsV;
            if (!autores.isEmpty()) {
                Label quem = Ui.paragrafo("⚽ " + String.join(",  ", autores), "texto-pequeno");
                quem.setPadding(new javafx.geometry.Insets(0, 0, 4, 30));
                g.add(quem, 0, lin++, 2, 1);
            }
        }
        v.getChildren().addAll(cab, g);
        if (!temGols && p.melhor != null && !p.melhor.isBlank())
            v.getChildren().add(Ui.linha(6, Ui.rotulo("Melhor em campo", "texto-pequeno"), Ui.rotulo(p.melhor, "texto-medio")));
        HBox fh = Ui.linha(3);
        for (int i = Math.max(0, forma.size() - 6); i < forma.size(); i++) fh.getChildren().add(Ui.forma(forma.get(i)));
        v.getChildren().addAll(Ui.separadorH(), Ui.linha(8, Ui.rotulo("Forma", "texto-pequeno"), Ui.espaco(), fh));
        return v;
    }

    /** Uma linha de ranking (artilharia): o nome (link), o time e o número. */
    private record LinhaArt(Node nome, String time, int gols) {}

    /** Artilharia (top 5) da competição principal do seu time; na Kings, da liga da Kings. */
    private VBox artilharia(Jogo j, Mundo m, Clube c) {
        List<Torneio> ts = competicoes(j, c);
        Torneio alvo = null;
        String nomeAlvo = null;
        List<LinhaArt> linhas = new java.util.ArrayList<>();
        if (tk != null) {
            // Kings: os gols de verdade ficam nos atletas da liga (a artilharia do torneio só tem os jogos narrados)
            br.futebas.kings.LigaKings liga = j.ligaKingsDoTime(tk.id);
            if (liga != null) {
                record Ak(br.futebas.kings.LigaKings.AtletaKings a, br.futebas.kings.LigaKings.TimeKings t) {}
                List<Ak> ks = new java.util.ArrayList<>();
                for (var time : liga.times) for (var a : time.elenco) if (a.gols > 0) ks.add(new Ak(a, time));
                ks.sort((a, b) -> b.a().gols - a.a().gols);
                for (int i = 0; i < Math.min(5, ks.size()); i++) {
                    var a = ks.get(i).a();
                    var time = ks.get(i).t();
                    String cls = meuId.equals(time.id) ? "texto-forte" : "label";
                    Jogador x = a.jogadorId == null ? null : m.jogador(a.jogadorId);
                    Node nome = x != null ? Ui.nomeJogador(x.id, x.nomeExibicao(), cls) : Ui.nomeAtletaKings(null, time.id, a.nome, cls);
                    linhas.add(new LinhaArt(nome, time.id, a.gols));
                }
                for (Torneio t : ts) if (j.ligaKingsDaCompeticao(t) == liga) { alvo = t; break; }
                if (!linhas.isEmpty()) nomeAlvo = liga.nome;
            }
        }
        if (linhas.isEmpty()) {
            // competição principal: a liga nacional; sem ela, a que tem mais gols na artilharia
            Torneio melhor = null;
            int golsMelhor = -1;
            for (Torneio t : ts) {
                List<int[]> art = t.artilheiros(Integer.MAX_VALUE);
                if (art.isEmpty()) continue;
                int soma = 0;
                for (int[] a : art) soma += a[1];
                boolean liga = "nacional".equals(t.ambito) && t.divisao != null;
                if (liga) soma += 1_000_000;
                if (soma > golsMelhor) { melhor = t; golsMelhor = soma; }
            }
            if (melhor != null) {
                alvo = melhor;
                for (int[] a : melhor.artilheiros(5)) {
                    Jogador x = m.jogador(a[0]);
                    if (x == null) continue;
                    String time = tk != null && x.kingsTimeId != null ? x.kingsTimeId : x.clubeId;
                    linhas.add(new LinhaArt(Ui.nomeJogador(x.id, x.nomeExibicao(), meuId.equals(time) ? "texto-forte" : "label"), time, a[1]));
                }
            }
        }
        if (linhas.isEmpty()) return null;
        if (nomeAlvo == null && alvo != null) nomeAlvo = Textos.curto(alvo.nome);
        Label sub = Ui.rotulo(nomeAlvo == null ? "" : nomeAlvo, "texto-pequeno");
        sub.setMinWidth(0);
        if (alvo != null) {
            Torneio a = alvo;
            sub.getStyleClass().add("link");
            sub.setCursor(javafx.scene.Cursor.HAND);
            sub.setOnMouseClicked(e -> hub.abrirCompeticao(a.id));
        }
        VBox v = Ui.painelAcoes("Artilharia", sub);
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(5);
        int r = 0;
        for (LinhaArt l : linhas) {
            if (l.nome() instanceof Region rg) rg.setMinWidth(0);
            HBox quem = Ui.linha(8, l.time() == null ? new Region() : Ui.escudo(l.time(), 18), l.nome());
            quem.setMinWidth(0);
            if (l.time() != null) Tooltip.install(quem, new Tooltip(m.nomeParticipante(l.time())));
            g.addRow(r, Ui.rotulo(String.valueOf(r + 1), "texto-suave"), quem, Ui.rotulo(String.valueOf(l.gols()), "pontos-destaque"));
            GridPane.setHgrow(quem, Priority.ALWAYS);
            r++;
        }
        v.getChildren().add(g);
        return v;
    }

    /** Destaques do elenco: artilheiro, garçom e melhor nota da temporada e quem está fora (lesão/suspensão). */
    private VBox destaques(Jogo j, Mundo m, Clube c) {
        VBox v = Ui.painelAcoes("Destaques do elenco", Ui.botao("Elenco", () -> hub.selecionar("elenco"), "fantasma"));
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(6);
        int r = 0;
        if (c != null) {
            List<Jogador> el = j.elenco(c.id);
            Jogador gols = null, assist = null, nota = null;
            int maxJogos = 0;
            for (Jogador x : el) maxJogos = Math.max(maxJogos, x.jogosTemp);
            int minJogos = Math.max(1, maxJogos / 3);
            for (Jogador x : el) {
                if (x.golsTemp > 0 && (gols == null || x.golsTemp > gols.golsTemp)) gols = x;
                if (x.assistTemp > 0 && (assist == null || x.assistTemp > assist.assistTemp)) assist = x;
                if (x.jogosTemp >= minJogos && x.notaMediaTemp() > 0 && (nota == null || x.notaMediaTemp() > nota.notaMediaTemp())) nota = x;
            }
            if (gols != null) r = destaque(g, r, "Artilheiro", gols, gols.golsTemp + (gols.golsTemp == 1 ? " gol" : " gols"));
            if (assist != null) r = destaque(g, r, "Garçom", assist, assist.assistTemp + (assist.assistTemp == 1 ? " assistência" : " assistências"));
            if (nota != null) r = destaque(g, r, "Melhor nota", nota, String.format(java.util.Locale.ROOT, "%.1f", nota.notaMediaTemp()).replace('.', ','));
            if (r == 0) {
                List<Jogador> fortes = new java.util.ArrayList<>(el);
                fortes.sort((a, b) -> Double.compare(b.forca, a.forca));
                for (int i = 0; i < Math.min(3, fortes.size()); i++)
                    r = destaque(g, r, i == 0 ? "Mais fortes" : "", fortes.get(i), String.valueOf((int) Math.round(fortes.get(i).forca)));
            }
            if (r > 0) v.getChildren().add(g);
            // desfalques
            List<Jogador> fora = new java.util.ArrayList<>();
            for (Jogador x : el) if (x.lesaoSemanas > 0 || x.suspensao > 0) fora.add(x);
            v.getChildren().add(Ui.separadorH());
            if (fora.isEmpty()) {
                v.getChildren().add(Ui.linha(8, Ui.rotulo("Desfalques", "texto-pequeno"), Ui.espaco(), Ui.rotulo("nenhum, elenco completo", "texto-suave")));
            } else {
                VBox fl = new VBox(4, Ui.rotulo("Desfalques", "texto-pequeno"));
                for (int i = 0; i < Math.min(4, fora.size()); i++) {
                    Jogador x = fora.get(i);
                    String motivo = x.suspensao > 0 ? "Suspenso" : (x.lesao == null ? "Lesão" : x.lesao) + " · " + x.lesaoSemanas + " sem.";
                    Label nome = Ui.nomeJogador(x.id, x.nomeExibicao(), "label");
                    nome.setMinWidth(0);
                    Label mot = Ui.rotulo(motivo, "texto-suave");
                    mot.setMinWidth(Region.USE_PREF_SIZE);
                    fl.getChildren().add(Ui.linha(8, Ui.pos(x.posicao), nome, Ui.espaco(), mot));
                }
                if (fora.size() > 4) fl.getChildren().add(Ui.rotulo("e mais " + (fora.size() - 4), "texto-pequeno"));
                v.getChildren().add(fl);
            }
            return v;
        }
        if (tk == null || tk.elenco.isEmpty()) return null;
        // Kings: quem mais marcou no seu time (e, empatados, os mais fortes)
        List<br.futebas.kings.LigaKings.AtletaKings> el = new java.util.ArrayList<>(tk.elenco);
        el.sort((a, b) -> a.gols != b.gols ? b.gols - a.gols : Double.compare(b.forca, a.forca));
        for (int i = 0; i < Math.min(5, el.size()); i++) {
            var a = el.get(i);
            Jogador x = a.jogadorId == null ? null : m.jogador(a.jogadorId);
            Label nome = x != null ? Ui.nomeJogador(x.id, x.nomeExibicao(), "label") : Ui.nomeAtletaKings(null, tk.id, a.nome, "label");
            nome.setMinWidth(0);
            HBox quem = Ui.linha(8, Ui.pos(a.posicao), nome);
            quem.setMinWidth(0);
            if (a.presidente) quem.getChildren().add(Ui.etiqueta("presidente", "escura"));
            Label gols = Ui.rotulo(a.gols + (a.gols == 1 ? " gol" : " gols"), a.gols > 0 ? "texto-medio" : "texto-suave");
            gols.setMinWidth(Region.USE_PREF_SIZE);
            g.addRow(r++, quem, Ui.forca((int) Math.round(a.forca)), gols);
            GridPane.setHgrow(quem, Priority.ALWAYS);
            GridPane.setHalignment(gols, javafx.geometry.HPos.RIGHT);
        }
        v.getChildren().add(g);
        return v;
    }

    private static int destaque(GridPane g, int r, String rotulo, Jogador x, String valor) {
        Label rot = Ui.rotulo(rotulo, "texto-pequeno");
        rot.setMinWidth(Region.USE_PREF_SIZE);
        Label nome = Ui.nomeJogador(x.id, x.nomeExibicao(), "texto-forte");
        nome.setMinWidth(0);
        HBox quem = Ui.linha(8, Ui.pos(x.posicao), nome);
        quem.setMinWidth(0);
        Label val = Ui.rotulo(valor == null ? "" : valor, "texto-medio");
        val.setMinWidth(Region.USE_PREF_SIZE);
        g.addRow(r, rot, quem, val);
        GridPane.setHgrow(quem, Priority.ALWAYS);
        GridPane.setHalignment(val, javafx.geometry.HPos.RIGHT);
        return r + 1;
    }

    /** Finanças do clube: caixa, folha, resultado da temporada e dívida. */
    private VBox financas(Jogo j, Clube c) {
        Jogo.ResumoFinanceiro f;
        try {
            f = j.financas(c.id);
        } catch (RuntimeException e) {
            return null;
        }
        long res = f.temporada() == null ? 0 : f.temporada().receitas() - f.temporada().despesas();
        VBox v = Ui.painelAcoes("Finanças", Ui.botao("Abrir", () -> hub.selecionar("financas"), "fantasma"));
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(10);
        g.add(kpi("Em caixa", Textos.dinheiro(f.saldo()), f.saldo() < 0 ? "negativo" : null), 0, 0);
        g.add(kpi("Folha mensal", Textos.dinheiro(f.folhaMensal()), null), 1, 0);
        g.add(kpi("Saldo da temporada", (res >= 0 ? "+" : "") + Textos.dinheiro(res), res >= 0 ? "positivo" : "negativo"), 0, 1);
        g.add(kpi("Dívida", f.divida() > 0 ? Textos.dinheiro(f.divida()) : "nenhuma", f.divida() > 0 ? "negativo" : null), 1, 1);
        javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints();
        cc.setPercentWidth(50);
        javafx.scene.layout.ColumnConstraints cc2 = new javafx.scene.layout.ColumnConstraints();
        cc2.setPercentWidth(50);
        g.getColumnConstraints().addAll(cc, cc2);
        v.getChildren().add(g);
        return v;
    }

    private static VBox kpi(String rotulo, String valor, String classe) {
        Label val = Ui.rotulo(valor, "hub-kpi");
        if (classe != null) val.getStyleClass().add(classe);
        return new VBox(1, Ui.rotulo(rotulo, "dado-rotulo"), val);
    }
}
