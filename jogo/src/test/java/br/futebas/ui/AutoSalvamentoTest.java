package br.futebas.ui;

import br.futebas.ui.Opcoes.AutoSalvar;
import br.futebas.ui.modelo.AutoSalvamento;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quando o salvamento automático acontece e em qual arquivo (rodízio). */
class AutoSalvamentoTest {
    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 5), JAN_2 = LocalDate.of(2026, 1, 12), FEV = LocalDate.of(2026, 2, 2);

    @Test
    void desligadoNuncaSalva() {
        assertFalse(AutoSalvamento.devido(AutoSalvar.DESLIGADO, JAN_1, JAN_2, false));
        assertFalse(AutoSalvamento.devido(AutoSalvar.DESLIGADO, JAN_1, FEV, true));
        assertFalse(AutoSalvamento.devido(null, JAN_1, FEV, true));
    }

    @Test
    void semanalSalvaTodaSemana() {
        assertTrue(AutoSalvamento.devido(AutoSalvar.SEMANAL, JAN_1, JAN_2, false));
        assertTrue(AutoSalvamento.devido(AutoSalvar.SEMANAL, JAN_2, FEV, false));
    }

    @Test
    void mensalSoQuandoOMesMuda() {
        assertFalse(AutoSalvamento.devido(AutoSalvar.MENSAL, JAN_1, JAN_2, false));
        assertTrue(AutoSalvamento.devido(AutoSalvar.MENSAL, JAN_2, FEV, false));
        assertTrue(AutoSalvamento.devido(AutoSalvar.MENSAL, LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 4), false));
        assertTrue(AutoSalvamento.devido(AutoSalvar.MENSAL, JAN_1, JAN_2, true));
    }

    @Test
    void fimDeTemporadaSoNaVirada() {
        assertFalse(AutoSalvamento.devido(AutoSalvar.TEMPORADA, JAN_2, FEV, false));
        assertTrue(AutoSalvamento.devido(AutoSalvar.TEMPORADA, JAN_2, FEV, true));
    }

    @Test
    void umAnoDeSemanasDaOsSalvamentosPrometidos() {
        int semanal = 0, mensal = 0, temporada = 0;
        LocalDate d = LocalDate.of(2026, 1, 5);
        for (int s = 0; s < 52; s++) {
            LocalDate prox = d.plusWeeks(1);
            boolean vira = s == 51;
            if (AutoSalvamento.devido(AutoSalvar.SEMANAL, d, prox, vira)) semanal++;
            if (AutoSalvamento.devido(AutoSalvar.MENSAL, d, prox, vira)) mensal++;
            if (AutoSalvamento.devido(AutoSalvar.TEMPORADA, d, prox, vira)) temporada++;
            d = prox;
        }
        assertEquals(52, semanal);
        assertEquals(12, mensal);
        assertEquals(1, temporada);
    }

    @Test
    void rodizioUsaAVagaVaziaDepoisAMaisAntiga() {
        assertEquals(1, AutoSalvamento.escolherVaga(new long[] {0, 0, 0}, 3));
        assertEquals(2, AutoSalvamento.escolherVaga(new long[] {100, 0, 0}, 3));
        assertEquals(3, AutoSalvamento.escolherVaga(new long[] {100, 200, 0}, 3));
        assertEquals(1, AutoSalvamento.escolherVaga(new long[] {100, 200, 300}, 3));
        assertEquals(2, AutoSalvamento.escolherVaga(new long[] {400, 200, 300}, 3));
        // uma vaga só: sempre a mesma, como antes das opções
        assertEquals(1, AutoSalvamento.escolherVaga(new long[] {400, 0, 0}, 1));
        // com 2 vagas, a 3ª (sobra de antes) não conta
        assertEquals(2, AutoSalvamento.escolherVaga(new long[] {400, 200, 0}, 2));
        assertEquals(List.of(2, 3), AutoSalvamento.sobrando(1, 3));
        assertEquals(List.of(), AutoSalvamento.sobrando(3, 3));
    }

    @Test
    void nomesDasVagas() {
        assertEquals("Flamengo 2026 (automático)", AutoSalvamento.nome("Flamengo 2026", 1));
        assertEquals("Flamengo 2026 (automático 3)", AutoSalvamento.nome("Flamengo 2026", 3));
        assertEquals("Flamengo 2026", AutoSalvamento.semSufixo("Flamengo 2026 (automático)"));
        assertEquals("Flamengo 2026", AutoSalvamento.semSufixo("Flamengo 2026 (automático 2)"));
        assertEquals("Meu save", AutoSalvamento.semSufixo("Meu save"));
    }
}
