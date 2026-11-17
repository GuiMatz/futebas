package br.futebas.mundo;

import br.futebas.Fixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * O índice de partidas por data do {@link Simulador} (desempenho da semana) dá sempre a mesma
 * resposta que {@code Torneio.diaDaProximaData}: uma temporada e meia conferindo cada pergunta,
 * passando pela virada do ano e pelos mata-matas que criam partidas no meio da semana.
 */
class IndiceDatasTest {
    @Test
    void indiceConfereComOTorneio() {
        boolean antes = Simulador.verificarIndice;
        Simulador.verificarIndice = true;
        try {
            Simulador s = Simulador.novo(Fixture.banco(), 77, "bra-1-02", "Teste");
            assertDoesNotThrow(() -> {
                s.avancarTemporada();
                for (int i = 0; i < 30; i++) s.avancarSemana();
            });
        } finally {
            Simulador.verificarIndice = antes;
        }
    }
}
