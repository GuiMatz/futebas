package br.futebas.competicao;

import br.futebas.Fixture;
import br.futebas.Jogo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regra do jogo: "não pode ter dois jogos no mesmo dia usando o mesmo time". Uma temporada inteira
 * (mundo dos clubes, seleções e Kings): nenhum time com dois jogos no mesmo dia.
 */
class AgendaTest {

    static List<Torneio> todos(Jogo j) {
        List<Torneio> l = new ArrayList<>(j.mundo().torneios);
        l.addAll(j.competicoesKings());
        return l;
    }

    @Test
    void nenhumTimeComDoisJogosNoMesmoDia() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 61, "esp-1-01", "T");
        int ano = j.ano(), pior = 0;
        while (j.ano() == ano) {
            j.avancarSemana();
            if (j.ano() == ano) pior = Math.max(pior, Agenda.colisoes(todos(j)));
        }
        List<Torneio> ts = new ArrayList<>(j.mundo().torneiosAnteriores);
        ts.addAll(todos(j));
        int c = Agenda.colisoes(ts);
        System.out.println("colisões no fim da temporada: " + c + " (pior durante: " + pior + ")");
        for (String x : Agenda.exemplos(ts, 25)) System.out.println("  " + x);
        assertEquals(0, c, "times com dois jogos no mesmo dia");
        assertEquals(0, pior);
    }
}
