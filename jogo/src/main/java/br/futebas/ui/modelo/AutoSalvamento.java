package br.futebas.ui.modelo;

import br.futebas.ui.Opcoes.AutoSalvar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Quando e onde salvar sozinho (sem JavaFX, testável): a frequência escolhida nas Opções e um
 * rodízio de até 3 arquivos "Nome (automático)", "Nome (automático 2)", "Nome (automático 3)".
 */
public final class AutoSalvamento {
    private AutoSalvamento() {}

    /**
     * Depois de uma semana do calendário (de {@code antes} para {@code depois}): está na hora de salvar?
     * Semanal: toda semana; mensal: quando o mês muda; fim de temporada: quando a temporada vira.
     */
    public static boolean devido(AutoSalvar f, LocalDate antes, LocalDate depois, boolean novaTemporada) {
        if (f == null) return false;
        return switch (f) {
            case DESLIGADO -> false;
            case SEMANAL -> true;
            case MENSAL -> novaTemporada || antes == null || depois == null
                    || antes.getYear() != depois.getYear() || antes.getMonthValue() != depois.getMonthValue();
            case TEMPORADA -> novaTemporada;
        };
    }

    /** Nome do arquivo da vaga {@code vaga} (1 = "(automático)", como antes das vagas). */
    public static String nome(String base, int vaga) {
        return base + (vaga <= 1 ? " (automático)" : " (automático " + vaga + ")");
    }

    /** Tira o sufixo de salvamento automático do nome (para continuar no save "de verdade"). */
    public static String semSufixo(String nome) {
        return nome == null ? null : nome.replaceFirst(" \\(automático( \\d+)?\\)$", "");
    }

    /**
     * Vaga a usar (1..n): a primeira que ainda não existe; se todas existem, a mais antiga.
     * {@code modificados[i]} = data da vaga i+1 (0 ou menos = não existe); só as {@code n} primeiras contam.
     */
    public static int escolherVaga(long[] modificados, int n) {
        n = Math.max(1, n);
        int melhor = 1;
        long maisAntigo = Long.MAX_VALUE;
        for (int v = 1; v <= n; v++) {
            long t = modificados != null && v - 1 < modificados.length ? modificados[v - 1] : 0;
            if (t <= 0) return v;
            if (t < maisAntigo) {
                maisAntigo = t;
                melhor = v;
            }
        }
        return melhor;
    }

    /** Vagas acima de {@code n} que devem ser apagadas (o técnico diminuiu o número de vagas). */
    public static List<Integer> sobrando(int n, int maximo) {
        List<Integer> l = new ArrayList<>();
        for (int v = Math.max(1, n) + 1; v <= maximo; v++) l.add(v);
        return l;
    }
}
