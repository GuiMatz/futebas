package br.futebas.kings;

import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;

/**
 * Treino da Kings League (requisito, 12/11/2026): o mesmo do futebol (foco por jogador ou do
 * elenco inteiro: "principais", "secundarias" ou o auxiliar decide), na escala da Kings e com o teto
 * de {@link Kings#FORCA_MAX} (80). Uma vez por semana, cada atleta muda um pouco:
 * <ul>
 *   <li>base pela idade: até 21 +0,08; até 25 +0,04; até 29 +0,01; até 33 −0,02; depois −0,05 por semana;</li>
 *   <li>Principais: +0,03 por semana (a força sobe mais rápido);</li>
 *   <li>Secundárias: +0,01 e a queda da idade pela metade (segura os veteranos);</li>
 *   <li>Auxiliar decide (e os times da IA, que treinam sozinhos): principais até 29 anos, secundárias depois.</li>
 * </ul>
 * Força entre 20 e 80 (na Kings League IOS, os genéricos entre 60 e 76). Os presidentes que jogam não mudam (a força deles é fixa). Determinístico
 * (não usa o gerador do jogo).
 */
public final class TreinoKings {
    private TreinoKings() {}

    public static final String PRINCIPAIS = "principais", SECUNDARIAS = "secundarias";
    static final double MIN = 20;

    /** Uma semana de treino de todos os times da liga. */
    public static void semana(Mundo m, LigaKings liga) {
        // Kings League IOS: os atletas genéricos ficam na escala da IOS (60 a 76, regra do jogo)
        double min = liga.custom ? IOS_MIN : MIN, max = liga.custom ? IOS_MAX : Kings.FORCA_MAX;
        for (LigaKings.TimeKings tk : liga.times) for (LigaKings.AtletaKings a : tk.elenco) treinar(m, a, min, max);
    }

    /** Escala dos atletas genéricos da Kings League IOS. */
    static final double IOS_MIN = 60, IOS_MAX = 76;

    /** Uma semana de treino do atleta (presidentes que jogam: nada muda). */
    public static void treinar(Mundo m, LigaKings.AtletaKings a) {
        treinar(m, a, MIN, Kings.FORCA_MAX);
    }

    static void treinar(Mundo m, LigaKings.AtletaKings a, double min, double max) {
        if (a == null || a.presidente) return;
        double d = delta(idade(m, a), a.treino);
        if (d == 0) return;
        double antes = a.forca;
        double f = Math.max(min, Math.min(max, antes + d));
        // quem já está fora da faixa (não deve acontecer) não piora: acima do teto só desce, abaixo do piso só sobe
        if (antes > max) f = Math.min(antes, Math.max(min, antes + Math.min(0, d)));
        if (antes < min) f = Math.max(antes, Math.min(max, antes + Math.max(0, d)));
        a.forca = Math.round(f * 1000) / 1000.0;
    }

    /** Mudança semanal de força pela idade e pelo foco (null = o auxiliar decide). */
    public static double delta(int idade, String foco) {
        String f = foco == null ? (idade <= 29 ? PRINCIPAIS : SECUNDARIAS) : foco;
        double base = idade <= 21 ? 0.08 : idade <= 25 ? 0.04 : idade <= 29 ? 0.01 : idade <= 33 ? -0.02 : -0.05;
        if (PRINCIPAIS.equals(f)) return base + 0.03;
        return (base < 0 ? base * 0.5 : base) + 0.01;
    }

    /** Idade do atleta (jogador do mundo, ano de nascimento da fonte ou 28 se não se sabe). */
    public static int idade(Mundo m, LigaKings.AtletaKings a) {
        Jogador j = a.jogadorId == null ? null : m.jogador(a.jogadorId);
        if (j != null) return j.idade(m.ano);
        if (a.anoNasc != null && a.anoNasc > 1900) return m.ano - a.anoNasc;
        return 28;
    }
}
