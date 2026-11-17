package br.futebas.ui.telas;

import br.futebas.kings.LigaKings;
import br.futebas.ui.modelo.AcompanhamentoPartida.Atleta;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * O shoot out do desempate da Kings com o mesmo sistema da disputa de pênaltis do futebol (pedido do
 * dono): a narração é limpa ao começar, cada cobrança aparece uma linha por vez ("X sai do meio-campo…",
 * "Bateu…", o resultado) e as bolinhas verde/vermelha ficam no placar. A mecânica e o número de cobranças
 * são os da Kings (o motor manda cada cobrança ao vivo). Serve à Kings ao vivo e à tela dividida.
 */
final class ShootoutNarrado {
    private final VBox narracao;
    private final SuspensePenalti.Bolinhas[] bolas;
    private final Label relogio;
    private final int[] gols = new int[2], anunciadas = new int[2], eventos = new int[2];
    private int cobrancas;
    private SuspensePenalti.Toque toque;
    private boolean ativo;

    ShootoutNarrado(VBox narracao, SuspensePenalti.Bolinhas[] bolas, Label relogio) {
        this.narracao = narracao;
        this.bolas = bolas;
        this.relogio = relogio;
        esconder();
    }

    boolean ativo() { return ativo; }

    /** Jogo novo: sem bolinhas. */
    void esconder() {
        if (toque != null && !toque.acabou()) toque.pular();
        toque = null;
        ativo = false;
        for (SuspensePenalti.Bolinhas b : bolas) {
            b.reiniciar();
            b.setVisible(false);
            b.setManaged(false);
        }
    }

    /** Começa o shoot out: limpa a narração e mostra as bolinhas cinza. */
    void comecar(String a, String b) {
        if (ativo) return;
        ativo = true;
        cobrancas = 0;
        for (int s = 0; s < 2; s++) gols[s] = anunciadas[s] = eventos[s] = 0;
        narracao.getChildren().clear();
        narracao.getChildren().add(TelaPartida.linhaFase("SO", "Shoot out  ·  " + a + " x " + b));
        for (SuspensePenalti.Bolinhas x : bolas) {
            x.reiniciar();
            x.setVisible(true);
            x.setManaged(true);
        }
        relogio.setText("Shoot out 0 x 0");
    }

    /** Uma cobrança do desempate (o motor espera o suspense): as três linhas no ritmo {@code ritmoMs}. */
    void cobranca(int lado, String cobrador, SuspensePenalti.Desfecho d, String goleiro, long ritmoMs) {
        if (toque != null && !toque.acabou()) toque.pular();
        int s = lado == 1 ? 1 : 0;
        if (cobrancas == 10) narracao.getChildren().add(TelaPartida.linhaFase("SO", SuspensePenalti.TEXTO_ALTERNADAS));
        cobrancas++;
        anunciadas[s]++;
        bolas[s].vez();
        String ida = SuspensePenalti.fraseIdaShootout(cobrador), batida = SuspensePenalti.fraseBatida(),
                res = SuspensePenalti.fraseResultado(d, goleiro);
        boolean casa = s == 0;
        List<Runnable> linhas = List.of(
                () -> narracao.getChildren().add(TelaPartida.linhaPasso(casa, "SO", ida, null)),
                () -> narracao.getChildren().add(TelaPartida.linhaPasso(casa, "SO", batida, null)),
                () -> {
                    if (bolas[s].batidas() < anunciadas[s]) marcar(s, d.gol());
                    narracao.getChildren().add(TelaPartida.linhaPasso(casa, "SO", res, d.gol() ? "penalti-gol" : "penalti-erro"));
                });
        toque = SuspensePenalti.emLinhas(linhas.size(), ritmoMs, i -> linhas.get(i).run(), null);
    }

    /**
     * O evento da cobrança no motor ("disputa_gol"/"disputa_perdido"): garante a bolinha mesmo quando a
     * cobrança não foi narrada (SIMULAR ATÉ O FIM).
     */
    void evento(int lado, boolean gol) {
        int s = lado == 1 ? 1 : 0;
        eventos[s]++;
        if (bolas[s].batidas() < eventos[s]) marcar(s, gol);
    }

    private void marcar(int s, boolean gol) {
        bolas[s].marcar(gol);
        if (gol) gols[s]++;
        relogio.setText("Shoot out " + gols[0] + " x " + gols[1]);
    }

    /** SIMULAR ATÉ O FIM: o resto na hora. */
    void pular() {
        if (toque != null && !toque.acabou()) toque.pular();
    }

    /** Testes: as bolinhas ("GGE--" de cada lado). */
    String[] resumo() {
        return new String[] {bolas[0].resumo(), bolas[1].resumo()};
    }

    // ------------------------------------------------------------------ a ordem dos batedores
    /** A tela de ordem (a mesma do futebol) para o shoot out; {@code resposta} recebe as chaves na ordem. */
    static OrdemPenaltis ordem(Node cabecalho, List<LigaKings.AtletaKings> sugestao, Consumer<List<String>> resposta) {
        List<Atleta> at = new ArrayList<>();
        for (int i = 0; i < sugestao.size(); i++) {
            LigaKings.AtletaKings a = sugestao.get(i);
            at.add(new Atleta(i, a.nome, a.posicao, a.posicao, (int) Math.round(a.forca), false, 100));
        }
        return new OrdemPenaltis(cabecalho, at, ids -> {
            List<String> ch = new ArrayList<>();
            for (int id : ids) if (id >= 0 && id < sugestao.size()) ch.add(sugestao.get(id).chave());
            resposta.accept(ch);
        });
    }
}
