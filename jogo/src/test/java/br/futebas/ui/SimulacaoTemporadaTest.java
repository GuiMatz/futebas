package br.futebas.ui;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Clube;
import br.futebas.ui.modelo.SimulacaoTemporada;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** "Simular temporada": semana a semana dá o mesmo mundo que avançar as semanas direto, e as delegações voltam ao fim. */
class SimulacaoTemporadaTest {

    static String assinatura(Jogo j) {
        StringBuilder sb = new StringBuilder();
        List<Torneio> ts = new ArrayList<>(j.mundo().torneios);
        ts.addAll(j.competicoesKings());
        for (Torneio t : ts) for (Partida p : t.partidas) if (p.jogada) sb.append(t.id).append(p.id).append(':').append(p.golsMandante).append('-').append(p.golsVisitante).append(';');
        for (Clube c : j.mundo().clubes.values()) sb.append(c.id).append('=').append(c.dinheiro).append(';');
        return sb.toString();
    }

    @Test
    void semanaASemanaIgualAAvancarDireto() {
        int n = 20;
        Jogo a = Jogo.novoJogo(Fixture.banco(), 77, FluxoTest.clubeSerieA(), "T");
        a.delegarAoAuxiliar("bra-1", 3, true);
        Sessao sa = new Sessao(a, "sim-a");
        SimulacaoTemporada sim = new SimulacaoTemporada(sa);
        sim.iniciar();
        int passos = 0;
        while (passos < n) {
            SimulacaoTemporada.Semana s = sim.passo();
            passos++;
            if (s.parada() != null && s.fimDeTemporada()) break;
        }
        sim.terminar();
        assertFalse(a.mundo().tecnico.auxiliarTudo, "o auxiliar devolve os jogos");
        assertTrue(a.delegadoAoAuxiliar("bra-1", 3), "a delegação avulsa do técnico continua");
        assertFalse(a.delegadoAoAuxiliar("bra-1", 4), "e só ela");

        Jogo b = Jogo.novoJogo(Fixture.banco(), 77, FluxoTest.clubeSerieA(), "T");
        b.delegarAoAuxiliar("bra-1", 3, true);
        Sessao sb = new Sessao(b, "sim-b");
        b.mundo().tecnico.auxiliarTudo = true;
        for (int i = 0; i < passos; i++) sb.fluxo.avancarSemana();
        b.mundo().tecnico.auxiliarTudo = false;
        assertEquals(assinatura(b), assinatura(a), "mesmo mundo depois de " + passos + " semanas");
        assertEquals(b.semana(), a.semana());
    }

    @Test
    void paraNoFimDaTemporada() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 78, FluxoTest.clubeSerieA(), "T");
        Sessao s = new Sessao(j, "sim-fim");
        SimulacaoTemporada sim = new SimulacaoTemporada(s);
        sim.iniciar();
        SimulacaoTemporada.Semana ult = null;
        for (int i = 0; i < 60; i++) {
            ult = sim.passo();
            if (ult.parada() != null) break;
        }
        sim.terminar();
        assertNotNull(ult);
        assertNotNull(ult.parada(), "para em algum momento");
        if (ult.fimDeTemporada()) assertEquals(sim.total(), sim.feitas());
    }
}
