package br.futebas.mundo;

import java.time.LocalDate;

/**
 * Tempo do jogo. O mundo anda de semana em semana; cada ano tem 52 semanas (a 52ª vai até 31/12).
 * A "semana absoluta" é {@code ano * 52 + semana} (semana 1..52), a mesma unidade dos contratos.
 */
public final class Calendario {
    private Calendario() {}

    public static int absoluta(int ano, int semana) {
        return ano * 52 + semana;
    }

    public static int ano(int absoluta) {
        return (absoluta - 1) / 52;
    }

    public static int semana(int absoluta) {
        return absoluta - ano(absoluta) * 52;
    }

    /** Semana absoluta em que cai a data. */
    public static int absoluta(LocalDate d) {
        return absoluta(d.getYear(), Math.min(52, (d.getDayOfYear() - 1) / 7 + 1));
    }

    /** Primeiro dia da semana absoluta. */
    public static LocalDate inicio(int absoluta) {
        return LocalDate.of(ano(absoluta), 1, 1).plusDays(7L * (semana(absoluta) - 1));
    }

    /** Dia de jogo dentro da semana: 0 = fim de semana (5º dia), 1 = meio de semana (2º dia). */
    public static LocalDate diaDeJogo(int absoluta, int ordem) {
        LocalDate d = inicio(absoluta).plusDays(ordem % 2 == 0 ? 5 : 2);
        return d.getYear() != ano(absoluta) ? LocalDate.of(ano(absoluta), 12, 31) : d;
    }

    private static final String[] MESES = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};

    public static String texto(LocalDate d) {
        return String.format("%02d/%s/%d", d.getDayOfMonth(), MESES[d.getMonthValue() - 1], d.getYear());
    }
}
