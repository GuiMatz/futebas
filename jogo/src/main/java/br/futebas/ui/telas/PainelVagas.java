package br.futebas.ui.telas;

import br.futebas.Jogo;
import br.futebas.mundo.Calendario;
import br.futebas.ui.App;
import br.futebas.ui.comp.Imagens;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Vagas de técnico (requisito: "as vagas ficam em aberto em alguma aba e, caso a pessoa
 * queira, ela aceita"): clubes que demitiram o técnico, times da Kings sem técnico e quem já
 * quer você. Filtros Todas / Clubes / Kings League e país; cada linha com "Candidatar-se"
 * (a diretoria responde em alguns dias) ou "Aceitar" (deixa o emprego atual, com confirmação).
 */
final class PainelVagas implements Hub.Painel {
    private static String filtroTipo = "todas", filtroPais = null;
    private final Hub hub;

    PainelVagas(Hub hub) {
        this.hub = hub;
    }

    /** Quantas vagas querem você (contador da navegação). */
    static int querVoce(Jogo j) {
        int n = 0;
        for (Jogo.VagaEmprego v : j.vagasDeEmprego()) if (v.proposta() != null) n++;
        return n;
    }

    @Override
    public Node construir() {
        VBox v = Ui.conteudo(Ui.linha(10, Ui.titulo("Vagas"), Ui.info("Vagas de técnico em aberto. Candidate-se e a diretoria responde em alguns dias; "
                + "quem já quer você (ou está interessado) pode ser aceito na hora. Aceitar = deixar o emprego atual.")));
        VBox lista = lista(hub.jogo(), hub::atualizar, () -> hub.atualizarTopo());
        v.getChildren().add(lista);
        VBox.setVgrow(lista, Priority.ALWAYS);
        return Ui.rolar(v);
    }

    /** A lista com os filtros (também usada pela tela sem emprego). */
    static VBox lista(Jogo j, Runnable recarregar, Runnable depoisDeAgir) {
        List<Jogo.VagaEmprego> todas = j.vagasDeEmprego();
        TreeSet<String> paises = new TreeSet<>();
        for (Jogo.VagaEmprego x : todas) if (x.pais() != null) paises.add(x.pais());
        HBox filtros = Ui.linha(6);
        for (String[] f : new String[][] {{"todas", "Todas"}, {"clube", "Clubes"}, {"kings", "Kings League"}}) {
            Button b = Ui.botao(f[1], () -> {
                filtroTipo = f[0];
                recarregar.run();
            }, filtroTipo.equals(f[0]) ? "primario" : "fantasma");
            b.setFocusTraversable(false);
            filtros.getChildren().add(b);
        }
        ComboBox<String> pais = new ComboBox<>();
        pais.getItems().add("Todos os países");
        for (String p : paises) pais.getItems().add(p);
        pais.setValue(filtroPais == null || !paises.contains(filtroPais) ? "Todos os países" : filtroPais);
        pais.setOnAction(e -> {
            String x = pais.getValue();
            filtroPais = x == null || x.startsWith("Todos") ? null : x;
            recarregar.run();
        });
        pais.setButtonCell(celulaPais());
        pais.setCellFactory(x -> celulaPais());
        filtros.getChildren().addAll(Ui.espaco(), pais);

        List<Jogo.VagaEmprego> vs = new ArrayList<>();
        for (Jogo.VagaEmprego x : todas) {
            if (!"todas".equals(filtroTipo) && !filtroTipo.equals(x.tipo())) continue;
            if (filtroPais != null && !filtroPais.equals(x.pais())) continue;
            vs.add(x);
        }
        VBox linhas = new VBox(8);
        // Requisito: primeiro as propostas recebidas, depois as que dá para se candidatar e
        // por último as acima do seu calibre, cada grupo com o seu título.
        int[] porGrupo = new int[Jogo.GRUPOS_VAGAS.length];
        for (Jogo.VagaEmprego x : vs) porGrupo[x.grupo()]++;
        for (int g = 0; g < Jogo.GRUPOS_VAGAS.length; g++) {
            if (porGrupo[g] == 0) continue;
            Label t = Ui.tituloPainel(Jogo.GRUPOS_VAGAS[g]);
            HBox cab = Ui.linha(8, t, Ui.etiqueta(String.valueOf(porGrupo[g]), g == 0 ? "verde" : g == 2 ? "vermelha" : null));
            cab.setAlignment(Pos.CENTER_LEFT);
            if (g > 0 && !linhas.getChildren().isEmpty()) cab.setStyle("-fx-padding: 10 0 0 0;");
            linhas.getChildren().add(cab);
            for (Jogo.VagaEmprego x : vs) if (x.grupo() == g) linhas.getChildren().add(linha(j, x, recarregar, depoisDeAgir));
        }
        if (vs.isEmpty()) linhas.getChildren().add(Ui.rotulo(todas.isEmpty() ? "Nenhuma vaga aberta agora. Elas abrem quando um clube demite o técnico."
                : "Nenhuma vaga com esses filtros.", "texto-suave"));
        return Ui.painelAcoes("Vagas abertas", Ui.etiqueta(String.valueOf(todas.size()), todas.isEmpty() ? null : "verde"), filtros, linhas);
    }

    private static javafx.scene.control.ListCell<String> celulaPais() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String p, boolean vazio) {
                super.updateItem(p, vazio);
                setText(null);
                setGraphic(vazio || p == null ? null : p.startsWith("Todos") ? Ui.rotulo(p) : Ui.linha(6, Imagens.bandeira(p, 12), Ui.rotulo(Textos.pais(p))));
            }
        };
    }

    private static Node linha(Jogo j, Jogo.VagaEmprego x, Runnable recarregar, Runnable depoisDeAgir) {
        Label nome = Ui.nomeClube(x.id(), x.nome(), "texto-forte");
        nome.setMinWidth(0);
        HBox meta = Ui.linha(6);
        if (x.pais() != null) meta.getChildren().add(Imagens.bandeira(x.pais(), 11));
        meta.getChildren().add(Ui.rotulo(x.onde(), "texto-pequeno"));
        if ("clube".equals(x.tipo())) meta.getChildren().add(Ui.estrelas(x.reputacao(), 10));
        else meta.getChildren().add(Ui.etiqueta("Kings League", "ambar"));
        VBox id = new VBox(1, nome, meta);
        id.setMinWidth(0);
        HBox.setHgrow(id, Priority.ALWAYS);
        VBox motivo = new VBox(1, Ui.rotulo(x.motivo(), "texto-medio"),
                Ui.rotulo("aberta em " + Textos.dataCurta(Calendario.inicio(Math.max(1, x.abertaEm()))) + "  ·  fecha em " + Textos.dataCurta(Calendario.inicio(Math.max(1, x.fechaEm()))), "texto-pequeno"));
        motivo.setMinWidth(220);
        motivo.setPrefWidth(250);
        String cor = switch (x.interesse()) {
            case "Quer você", "Interessado" -> "verde";
            case "Talvez" -> "ambar";
            default -> null;
        };
        Node interesse = Ui.etiqueta(x.interesse(), cor);
        HBox acoes = Ui.linha(8);
        acoes.setAlignment(Pos.CENTER_RIGHT);
        acoes.setMinWidth(230);
        if (x.situacao() != null) acoes.getChildren().add(Ui.etiqueta(x.situacao(), "Recusada".equals(x.situacao()) ? "vermelha" : null));
        if (x.podeCandidatar() && !"Quer você".equals(x.interesse())) acoes.getChildren().add(Ui.botao("Candidatar-se", () -> {
            boolean ok = j.candidatarVaga(x);
            if ("kings".equals(x.tipo())) Ui.informar(x.nome(), ok ? "O presidente aceitou: a vaga agora quer você." : "O presidente preferiu outro técnico.");
            else if (!ok) Ui.informar(x.nome(), "Não dá para se candidatar a esta vaga.");
            depoisDeAgir.run();
            recarregar.run();
        }));
        if (x.podeAceitar()) {
            acoes.getChildren().add(Ui.botao("Recusar", () -> {
                j.recusarVaga(x);
                depoisDeAgir.run();
                recarregar.run();
            }, "perigo"));
            acoes.getChildren().add(Ui.botao("Aceitar", () -> aceitar(j, x), "primario"));
        }
        Node escudo = Ui.escudo(x.id(), 36);
        HBox h = Ui.linha(12, escudo, id, motivo, interesse, acoes);
        h.getStyleClass().add("convite");
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    /** Aceitar: deixa o emprego atual (confirmação "Você vai deixar o X") e abre a central do novo. */
    private static void aceitar(Jogo j, Jogo.VagaEmprego x) {
        String atual = j.empregoAtual();
        if (!Ui.confirmar(x.nome(), "Assumir o " + x.nome() + "?" + (atual == null ? "" : " Você vai deixar o " + atual + "."))) return;
        if (j.aceitarVaga(x)) App.atual().irParaHub("kings".equals(x.tipo()) ? "kings" : "jogo");
        else Ui.informar(x.nome(), "A vaga não está mais disponível.");
    }
}
