package br.futebas.partida;

import br.futebas.Fixture;
import br.futebas.ferramentas.CalibrarMotor;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Rng;
import br.futebas.mundo.Simulador;
import br.futebas.mundo.StatusJogador;
import br.futebas.mundo.TipoContrato;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Balanço 2026-11, rodada 2: (1) fator de elite — no alto da escala a mesma diferença de força pesa
 * mais (o campeão da Champions ficava fora dos 8 mais fortes em metade das temporadas), e abaixo de
 * {@link MotorPartida#ELITE_DE} nada muda; (2) a IA empresta jovens e excedentes (eram ~0,5%).
 */
class EliteEEmprestimosTest {

    /** % de vitórias do mais forte com mando neutro alternado (média dos dois mandos). */
    static double vitorias(double fa, double fb, int jogos, OpcoesPartida op, long semente) {
        Rng rng = new Rng(semente);
        int v = 0;
        for (int i = 0; i < jogos; i++) {
            TimePartida a = CalibrarMotor.time("A", fa, rng, 0), b = CalibrarMotor.time("B", fb, rng, 0);
            a.estilo = Estilo.EQUILIBRADO;
            b.estilo = Estilo.EQUILIBRADO;
            ResultadoPartida r = i % 2 == 0 ? MotorPartida.simular(a, b, op, rng) : MotorPartida.simular(b, a, op, rng);
            int ga = i % 2 == 0 ? r.golsMandante : r.golsVisitante, gb = i % 2 == 0 ? r.golsVisitante : r.golsMandante;
            if (ga > gb) v++;
        }
        return 100.0 * v / jogos;
    }

    @Test
    void fatorDeEliteSoNoAltoDaEscala() {
        assertEquals(1.0, MotorPartida.fatorElite(70), 1e-12, "ligas menores: nada muda");
        assertEquals(1.0, MotorPartida.fatorElite(MotorPartida.ELITE_DE), 1e-12);
        // ligas: K 0,05 → 0,02 em 16/11/2026 (saúde do mundo, variedade de campeões): 84 dá 1,2 (antes 1,5)
        assertTrue(MotorPartida.fatorElite(84) > 1.15);
        assertTrue(MotorPartida.fatorElite(84, MotorPartida.ELITE_K_CONTINENTAL) > MotorPartida.fatorElite(84));
        assertEquals(MotorPartida.fatorElite(200), MotorPartida.fatorElite(MotorPartida.ELITE_DE + MotorPartida.ELITE_MAX), 1e-12, "limitado");
        OpcoesPartida liga = new OpcoesPartida(), cont = new OpcoesPartida();
        cont.eliteK = MotorPartida.ELITE_K_CONTINENTAL;
        // a mesma diferença (5 pontos): embaixo, igual nas duas; no alto, o favorito vence mais na continental
        double baixoLiga = vitorias(66, 61, 1500, liga, 3), baixoCont = vitorias(66, 61, 1500, cont, 3);
        assertEquals(baixoLiga, baixoCont, 1e-9, "abaixo de ELITE_DE o K da competição não muda nada");
        double altoLiga = vitorias(86, 81, 1500, liga, 3), altoCont = vitorias(86, 81, 1500, cont, 3);
        assertTrue(altoCont > altoLiga + 5, "continental no alto: " + altoCont + " vs liga " + altoLiga);
        assertTrue(altoLiga >= baixoLiga - 3, "no alto a diferença não pesa menos que embaixo: " + altoLiga + " vs " + baixoLiga);
    }

    @Test
    void iaEmprestaJovensEExcedentesNaJanela() {
        Simulador s = Simulador.novo(Fixture.banco(), 41, null, null);
        int emprestados = 0, prof = 0;
        for (int w = 0; w < 3; w++) s.avancarSemana();
        for (Jogador j : s.m.jogadores) {
            if (j.status != StatusJogador.PROFISSIONAL || j.amador()) continue;
            prof++;
            if (j.contrato != null && j.contrato.tipo == TipoContrato.EMPRESTIMO) {
                emprestados++;
                assertNotNull(s.m.clube(j.contrato.clubeOrigem), "empréstimo tem clube de origem");
                assertNotEquals(j.contrato.clubeOrigem, j.clubeId);
            }
        }
        assertTrue(emprestados > 0, "a IA empresta na 1ª semana da janela (" + prof + " profissionais)");
    }
}
