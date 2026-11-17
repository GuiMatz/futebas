package br.futebas.ui.modelo;

/**
 * Depois de uma partida a central volta; a tecla que fechou a partida (Enter/Espaço, ou a
 * repetição de quem segura a tecla) não pode começar o próximo jogo. Só depois deste intervalo
 * o JOGAR pelo teclado vale de novo.
 */
public final class GuardaJogar {
    public static final long INTERVALO_MS = 900;

    private GuardaJogar() {}

    public static boolean permite(long mostradoEm, long agora) {
        return agora - mostradoEm >= INTERVALO_MS;
    }
}
