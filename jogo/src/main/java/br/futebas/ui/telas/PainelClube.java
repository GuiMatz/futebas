package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.competicao.Torneio;
import br.futebas.historia.Historia;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Tecnico;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** O clube: identidade, sala de troféus com as taças, rivais, linha do tempo e a carreira do técnico. */
final class PainelClube implements Hub.Painel {
    private final Hub hub;

    PainelClube(Hub hub) {
        this.hub = hub;
    }

    @Override
    public Node construir() {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        Clube c = hub.clube();
        if (c == null && j.meuTimeKings() != null) return kings(j, m, j.meuTimeKings());

        Label nome = Ui.titulo(c.nome);
        nome.setStyle("-fx-font-size: 28px;");
        HBox local = Ui.linha(8, Imagens.bandeira(c.pais, 14), Ui.rotulo((c.cidade == null ? "" : c.cidade + (c.uf == null ? "" : " (" + c.uf + ")") + " · ") + Textos.pais(c.pais), "texto-medio"));
        HBox selos = Ui.linha(8, Ui.etiqueta("Porte " + Textos.porte(c.reputacao).toLowerCase(), "escura"), Ui.etiqueta(Textos.divisaoDe(m, c), "verde"));
        VBox id = new VBox(6, nome, local, selos);
        HBox cab = Ui.linha(20, Ui.escudo(c.id, 110), id);

        HBox nums = new HBox(16, kpi("Reputação", Math.round(c.reputacao) + ""), kpi("Torcida", Math.round(c.torcida) + ""),
                kpi("Estrutura", Math.round(c.estrutura) + ""), kpi("Estádio", Textos.milhar(c.capacidade) + " lugares"),
                kpi("Diretoria", Math.round(c.confiancaDiretoria) + "% de confiança"));
        for (Node n : nums.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        VBox sala = salaDeTrofeus(j, c.id, j.competicoesDoAno());

        VBox rivais = Ui.painel("Rivais");
        FlowPane fr = new FlowPane(16, 10);
        for (String r : c.rivais) {
            Clube rc = m.clube(r);
            if (rc != null) fr.getChildren().add(Ui.clube(m, r, 28));
        }
        if (fr.getChildren().isEmpty()) rivais.getChildren().add(Ui.vazio("Sem rivais registrados.", null, null));
        else rivais.getChildren().add(fr);

        VBox historia = Ui.painel("História do clube no seu mundo");
        List<Historia.Titulo> ts = new ArrayList<>(m.historia.titulosDoClube(c.id));
        ts.sort((a, b) -> b.ano - a.ano);
        if (ts.isEmpty()) historia.getChildren().add(Ui.vazio("Nenhum título", null, null));
        for (Historia.Titulo t : ts.subList(0, Math.min(10, ts.size()))) {
            historia.getChildren().add(Ui.linha(10, Ui.rotulo(String.valueOf(t.ano), "numero"), Ui.trofeu(t.competicaoId, t.ambito, t.pais, 22),
                    Ui.nomeCompeticao(t.competicaoId, "Campeão: " + Textos.curto(t.competicao))));
        }
        HBox meio = new HBox(16, rivais, historia);
        HBox.setHgrow(rivais, Priority.ALWAYS);
        HBox.setHgrow(historia, Priority.ALWAYS);
        rivais.setPrefWidth(380);
        historia.setPrefWidth(420);

        return Ui.rolar(Ui.conteudo(cab, nums, sala, meio, carreira(j, m)));
    }

    /**
     * O mesmo painel para o time da Kings: identidade (escudo, liga, presidente), números do time,
     * sala de troféus (títulos reais e do save + as competições da Kings em disputa), história no
     * seu mundo e a carreira do técnico. Sem rivais, estádio e estrutura (a Kings não tem).
     */
    private Node kings(Jogo j, Mundo m, br.futebas.kings.LigaKings.TimeKings tk) {
        br.futebas.kings.LigaKings liga = j.ligaKingsDoTime(tk.id);
        String nomeLiga = liga == null ? "Kings League" : Textos.ligaKings(liga.id, liga.nome);
        Label nome = Ui.titulo(tk.nome);
        nome.setStyle("-fx-font-size: 28px;");
        HBox local = Ui.linha(8, Imagens.bandeira(liga == null ? null : liga.pais, 14),
                Ui.rotulo((liga == null || liga.pais == null ? "" : Textos.pais(liga.pais) + " · ") + nomeLiga, "texto-medio"));
        HBox selos = Ui.linha(8, Ui.etiqueta("Kings League · 7x7", "escura"), Ui.etiqueta(nomeLiga, "verde"));
        VBox id = new VBox(6, nome, local, selos);
        HBox cab = Ui.linha(20, Ui.escudo(tk.id, 110), id);
        int[] pos = j.posicaoKings();
        HBox nums = new HBox(16, kpi("Dono", tk.presidente == null ? "—" : tk.presidente),
                kpi("Colocação", pos == null ? "—" : pos[0] + "º de " + pos[1]), kpi("Elenco", tk.elenco.size() + " jogadores"),
                kpi("Formação", tk.formacao), kpi("Presidente", Math.round(j.confiancaPresidenteKings()) + "% de confiança"));
        for (Node n : nums.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        List<Torneio> doAno = new ArrayList<>();
        for (Torneio t : j.competicoesDoParticipante(tk.id)) if (t.ano == j.ano()) doAno.add(t);
        VBox sala = salaDeTrofeus(j, tk.id, doAno);
        VBox elenco = Ui.painel("Presidentes e destaques");
        for (var a : tk.elenco) if (a.presidente) elenco.getChildren().add(Ui.linha(10, Ui.pos(a.posicao), Ui.nomeAtletaKings(a.jogadorId, tk.id, a.nome), Ui.etiqueta("Presidente", "ambar"),
                Ui.espaco(), Ui.forca((int) Math.round(a.forca))));
        List<br.futebas.kings.LigaKings.AtletaKings> art = new ArrayList<>(tk.elenco);
        art.sort((a, b) -> b.gols - a.gols);
        for (var a : art.subList(0, Math.min(5, art.size()))) if (a.gols > 0)
            elenco.getChildren().add(Ui.linha(10, Ui.pos(a.posicao), Ui.nomeAtletaKings(a.jogadorId, tk.id, a.nome), Ui.espaco(), Ui.rotulo(a.gols + " gols", "texto-forte")));
        if (elenco.getChildren().size() == 1) elenco.getChildren().add(Ui.vazio("Sem gols ainda.", null, null));
        VBox historia = Ui.painel("História do time no seu mundo");
        List<Historia.Titulo> ts = new ArrayList<>(m.historia.titulosDoClube(tk.id));
        ts.sort((a, b) -> b.ano - a.ano);
        if (ts.isEmpty()) historia.getChildren().add(Ui.vazio("Nenhum título", null, null));
        for (Historia.Titulo t : ts.subList(0, Math.min(10, ts.size())))
            historia.getChildren().add(Ui.linha(10, Ui.rotulo(String.valueOf(t.ano), "numero"), Ui.trofeu(t.competicaoId, t.ambito, t.pais, 22),
                    Ui.nomeCompeticao(t.competicaoId, "Campeão: " + Textos.curto(t.competicao))));
        HBox meio = new HBox(16, elenco, historia);
        HBox.setHgrow(elenco, Priority.ALWAYS);
        HBox.setHgrow(historia, Priority.ALWAYS);
        elenco.setPrefWidth(380);
        historia.setPrefWidth(420);
        Node pagina = Ui.botao("Página do time", () -> hub.abrirParticipante(tk.id), "fantasma");
        return Ui.rolar(Ui.conteudo(Ui.linha(0, cab, Ui.espaco(), pagina), nums, sala, meio, carreira(j, m)));
    }

    private static VBox kpi(String rotulo, String valor) {
        VBox v = Ui.painelAcoes(null, null, Ui.rotulo(rotulo, "texto-suave"), Ui.rotulo(valor, "dado-valor"));
        v.setSpacing(2);
        return v;
    }

    /** Uma vitrine por competição: a taça, quantas vezes e os anos; as que o clube disputa aparecem apagadas. */
    private VBox salaDeTrofeus(Jogo j, String id, List<Torneio> doAno) {
        Map<String, List<Jogo.TituloInfo>> porComp = new LinkedHashMap<>();
        for (Jogo.TituloInfo t : j.titulosDoParticipante(id)) porComp.computeIfAbsent(t.competicaoId(), k -> new ArrayList<>()).add(t);
        FlowPane fp = new FlowPane(12, 12);
        for (var e : porComp.entrySet()) {
            Jogo.TituloInfo t0 = e.getValue().get(0);
            StringBuilder anos = new StringBuilder();
            for (Jogo.TituloInfo t : e.getValue()) anos.append(anos.length() == 0 ? "" : ", ").append(t.temporada());
            fp.getChildren().add(vitrine(t0.competicaoId(), t0.ambito(), t0.pais(), Textos.curto(t0.competicao()), e.getValue().size(), anos.toString()));
        }
        int conquistadas = fp.getChildren().size();
        for (Torneio t : doAno) {
            if (!t.participantes.contains(id) || porComp.containsKey(t.id) || "amistoso".equals(t.ambito)) continue;
            porComp.put(t.id, List.of());
            fp.getChildren().add(vitrine(t.id, t.ambito, t.pais, Textos.curto(t.nome), 0, "em disputa"));
        }
        VBox v = Ui.painelAcoes("Sala de troféus", Ui.rotulo(conquistadas == 0 ? "nenhum título ainda" : conquistadas + " competições vencidas", "texto-suave"), fp);
        if (fp.getChildren().isEmpty()) v.getChildren().add(Ui.vazio("Sem competições", null, null));
        return v;
    }

    private static VBox vitrine(String compId, String ambito, String pais, String nome, int vezes, String anos) {
        Label n = Ui.nomeCompeticao(compId, nome, "texto-forte");
        n.setWrapText(true);
        n.setMaxWidth(130);
        n.setAlignment(Pos.CENTER);
        n.setStyle("-fx-text-alignment: center;");
        Label conta = Ui.rotulo(vezes > 0 ? vezes + "×" : "", "trofeu-conta");
        Label a = Ui.paragrafo(anos, "texto-pequeno");
        a.setMaxWidth(130);
        a.setStyle("-fx-text-alignment: center;");
        VBox v = new VBox(6, Ui.trofeu(compId, ambito, pais, 76), n, conta, a);
        v.getStyleClass().add("trofeu-caixa");
        if (vezes == 0) {
            v.getStyleClass().add("vazio-trofeu");
            v.getChildren().remove(conta);
        }
        v.setPrefWidth(150);
        v.setMinWidth(150);
        return v;
    }

    private VBox carreira(Jogo j, Mundo m) {
        Tecnico t = m.tecnico;
        // a carreira soma clube e Kings (a Kings não tem empate: shootout decide)
        int jogos = t.jogos + t.kingsJogos, vit = t.vitorias + t.kingsVitorias, der = t.derrotas + t.kingsDerrotas;
        String aprov = jogos == 0 ? "—" : Math.round(100.0 * (3 * vit + t.empates) / (3.0 * jogos)) + "%";
        HBox nums = Ui.linha(28, Ui.dado("Jogos", String.valueOf(jogos)), Ui.dado("Vitórias", String.valueOf(vit)),
                Ui.dado("Empates", String.valueOf(t.empates)), Ui.dado("Derrotas", String.valueOf(der)), Ui.dado("Aproveitamento", aprov),
                Ui.dado("Reputação", String.valueOf(Math.round(t.reputacao))), Ui.dado("Pontos no ranking", String.valueOf(j.rankingTecnico())),
                Ui.dado("Demissões", String.valueOf(t.demissoes)));
        VBox v = Ui.painelAcoes("Carreira de " + t.nome, null, nums);
        if (!m.historia.carreiraTecnico.isEmpty()) {
            GridPane g = new GridPane();
            g.setHgap(14);
            g.setVgap(6);
            int r = 0;
            for (Historia.TemporadaTecnico tt : m.historia.carreiraTecnico) {
                g.addRow(r++, Ui.rotulo(String.valueOf(tt.ano), "numero"), tt.clubeId == null ? new Label() : Ui.escudo(tt.clubeId, 20), Ui.rotulo(tt.clube),
                        Ui.rotulo(tt.vitorias + "V " + tt.empates + "E " + tt.derrotas + "D", "texto-suave"),
                        Ui.rotulo(tt.titulos.isEmpty() ? (tt.saida == null ? "" : tt.saida) : String.join(", ", tt.titulos), tt.titulos.isEmpty() ? "texto-suave" : "texto-forte"));
            }
            v.getChildren().addAll(Ui.separadorH(), g);
        }
        return v;
    }
}
