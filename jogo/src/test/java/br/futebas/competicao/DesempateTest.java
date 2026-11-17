package br.futebas.competicao;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DesempateTest {

    static LinhaTabela l(String id, int pts, int v, int gp, int gc, int cartoes) {
        LinhaTabela t = new LinhaTabela(id);
        t.pontos = pts;
        t.vitorias = v;
        t.golsPro = gp;
        t.golsContra = gc;
        t.cartoes = cartoes;
        return t;
    }

    static Partida jogo(String a, String b, int ga, int gb) {
        Partida p = new Partida();
        p.mandante = a;
        p.visitante = b;
        p.golsMandante = ga;
        p.golsVisitante = gb;
        p.jogada = true;
        return p;
    }

    static List<String> ids(List<LinhaTabela> l) {
        List<String> s = new ArrayList<>();
        for (LinhaTabela t : l) s.add(t.clubeId);
        return s;
    }

    @Test
    void criteriosEmCascata() {
        List<LinhaTabela> t = List.of(l("a", 10, 3, 8, 5, 0), l("b", 10, 2, 10, 5, 0), l("c", 12, 4, 6, 6, 0), l("d", 10, 3, 9, 5, 0));
        List<String> o = ids(Desempate.ordenar(t, List.of("pontos", "vitorias", "saldo", "gols_pro"), List.of(), 1));
        assertEquals(List.of("c", "d", "a", "b"), o);
        // trocando a ordem: saldo antes de vitórias
        o = ids(Desempate.ordenar(t, List.of("pontos", "saldo", "vitorias"), List.of(), 1));
        assertEquals(List.of("c", "b", "d", "a"), o);
    }

    @Test
    void confrontoDiretoEntreDois() {
        List<LinhaTabela> t = List.of(l("a", 10, 3, 8, 5, 0), l("b", 10, 3, 8, 5, 0));
        List<Partida> jogos = List.of(jogo("a", "b", 0, 1), jogo("b", "a", 1, 1));
        assertEquals(List.of("b", "a"), ids(Desempate.ordenar(t, List.of("pontos", "confronto_direto"), jogos, 1)));
    }

    @Test
    void confrontoDiretoEntreTresUsaMinitabela() {
        List<LinhaTabela> t = List.of(l("a", 9, 3, 5, 5, 0), l("b", 9, 3, 5, 5, 0), l("c", 9, 3, 5, 5, 0), l("z", 20, 6, 10, 2, 0));
        List<Partida> jogos = List.of(jogo("a", "b", 1, 0), jogo("b", "c", 2, 0), jogo("c", "a", 3, 0), jogo("z", "a", 0, 5));
        // minitabela: a 3 pts (saldo -2), b 3 pts (saldo +1), c 3 pts (saldo +1, gols 3) -> c, b, a
        List<String> o = ids(Desempate.ordenar(t, List.of("pontos", "confronto_direto"), jogos, 1));
        assertEquals(List.of("z", "c", "b", "a"), o);
    }

    @Test
    void cartoesEMenosCartoesNaFrente() {
        List<LinhaTabela> t = List.of(l("a", 5, 1, 3, 3, 9), l("b", 5, 1, 3, 3, 4));
        assertEquals(List.of("b", "a"), ids(Desempate.ordenar(t, List.of("pontos", "saldo", "cartoes"), List.of(), 1)));
    }

    @Test
    void sorteioDeterministico() {
        List<LinhaTabela> t = List.of(l("a", 5, 1, 3, 3, 0), l("b", 5, 1, 3, 3, 0), l("c", 5, 1, 3, 3, 0));
        List<String> o1 = ids(Desempate.ordenar(t, List.of("pontos"), List.of(), 77));
        List<String> o2 = ids(Desempate.ordenar(t, List.of("pontos"), List.of(), 77));
        assertEquals(o1, o2);
    }
}
