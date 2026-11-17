package br.futebas.ui.telas;

import br.futebas.competicao.Partida;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Tecnico;
import br.futebas.ui.Sessao;
import br.futebas.ui.Tela;
import br.futebas.ui.comp.Tema;
import br.futebas.ui.comp.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Multi-treinador: "Vez de <Nome>". Aparece entre as vezes dos técnicos, para o computador passar de
 * mão sem que um veja a central do outro. "Começar a vez" abre a central dele.
 */
public final class TelaVez implements Tela {
    private final StackPane raiz = new StackPane();
    private final Runnable comecar;

    public TelaVez(Sessao sessao, Runnable comecar) {
        this.comecar = comecar;
        var jogo = sessao.jogo;
        Tecnico t = jogo.mundo().tecnico;
        List<Tecnico> todos = jogo.tecnicos();
        int i = jogo.vez(), n = todos.size();
        Clube c = jogo.meuClube();
        var tk = c == null ? jogo.meuTimeKings() : null;
        String time = c != null ? c.nome : tk != null ? tk.nome + " (Kings League)" : "Sem clube";
        String id = c != null ? c.id : tk != null ? tk.id : null;
        String cor1 = c != null ? c.cor1 : tk != null ? tk.cor1 : null, cor2 = c != null ? c.cor2 : tk != null ? tk.cor2 : null;

        Label rotulo = Ui.rotulo("VEZ DE", "texto-suave");
        Label nome = Ui.rotulo(t == null ? "Técnico" : t.nome, "titulo-tela");
        nome.setStyle("-fx-font-size: 40px;");
        Label sub = Ui.rotulo(time + "  ·  técnico " + (i + 1) + " de " + n, "texto-medio");
        VBox caixa = new VBox(10);
        caixa.setAlignment(Pos.CENTER);
        caixa.getChildren().add(rotulo);
        if (id != null) caixa.getChildren().add(Ui.escudo(id, 96));
        caixa.getChildren().addAll(nome, sub);
        String jogoTxt = proximoJogo(sessao);
        if (jogoTxt != null) caixa.getChildren().add(Ui.rotulo(jogoTxt, "texto-medio"));
        // quem já mandou jogar e quem falta nesta rodada
        StringBuilder ordem = new StringBuilder();
        for (int k = 0; k < n; k++) ordem.append(k == 0 ? "" : "   ›   ").append(k < i ? "✓ " : k == i ? "● " : "").append(todos.get(k).nome);
        caixa.getChildren().add(Ui.rotulo(ordem.toString(), "texto-suave"));
        Label aviso = Ui.rotulo("Os outros técnicos não olham: a central a seguir é só de " + (t == null ? "você" : t.nome) + ".", "texto-pequeno");
        Button ir = Ui.botaoTecla("Começar a vez", "Enter", this::ir, "jogar-grande");
        ir.setMinWidth(Region.USE_PREF_SIZE);
        caixa.getChildren().addAll(aviso, ir);
        caixa.getStyleClass().add("caixa-sobreposta");
        caixa.setMaxSize(640, Region.USE_PREF_SIZE);
        caixa.setStyle("-fx-padding: 32 40 32 40;");
        raiz.getChildren().add(caixa);
        raiz.setStyle(cor1 == null ? "-fx-background-color: #10151c;" : Tema.estiloCabecalho(cor1, cor2));
    }

    /** "Próximo jogo: A x B (Competição)" do técnico da vez nesta semana, ou null. */
    private static String proximoJogo(Sessao s) {
        try {
            Partida p = s.jogo.meuClube() != null ? (s.fluxo.temJogoNaSemana() ? s.fluxo.proximoJogo() : null)
                    : s.fluxo.soKings() ? s.fluxo.proximoJogoKings() : null;
            if (p == null) return s.jogo.meuClube() != null || s.fluxo.soKings() ? "Sem jogo nesta rodada" : null;
            var m = s.jogo.mundo();
            var tor = s.fluxo.torneioDe(p);
            return "Jogo da rodada: " + m.nomeParticipante(p.mandante) + " x " + m.nomeParticipante(p.visitante) + (tor == null ? "" : "  (" + tor.nome + ")");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean foi;

    private void ir() {
        if (foi) return;
        foi = true;
        comecar.run();
    }

    @Override
    public Parent raiz() { return raiz; }

    @Override
    public boolean tecla(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
            ir();
            return true;
        }
        return false;
    }

    /** Para os testes: o texto principal (nome do técnico da vez). */
    String nomeMostrado() {
        for (Node x : ((VBox) raiz.getChildren().get(0)).getChildren()) if (x instanceof Label l && l.getStyleClass().contains("titulo-tela")) return l.getText();
        return null;
    }
}
