package br.futebas.ui;

import br.futebas.ui.modelo.GuardaJogar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Depois da partida, a tecla que voltou para a central não pode começar o próximo jogo. */
class GuardaJogarTest {
    @Test
    void teclaLogoDepoisDeVoltarNaoJoga() {
        long volta = 1_000_000;
        assertFalse(GuardaJogar.permite(volta, volta));
        assertFalse(GuardaJogar.permite(volta, volta + 300));
        assertFalse(GuardaJogar.permite(volta, volta + GuardaJogar.INTERVALO_MS - 1));
    }

    @Test
    void depoisDoIntervaloJogaNormalmente() {
        long volta = 1_000_000;
        assertTrue(GuardaJogar.permite(volta, volta + GuardaJogar.INTERVALO_MS));
        assertTrue(GuardaJogar.permite(volta, volta + 5_000));
    }
}
