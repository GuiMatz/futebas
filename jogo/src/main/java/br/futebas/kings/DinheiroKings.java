package br.futebas.kings;

import br.futebas.mundo.Mundo;

/**
 * Dinheiro dos times da Kings League (regra do jogo, 12/11/2026), no lugar do modelo informativo:
 * <ul>
 *   <li>todo time recebe o mesmo orçamento por temporada, {@link Kings#ORCAMENTO_TEMPORADA} (R$ 1,5 mi);</li>
 *   <li>prêmios: título de liga (Split) +{@link Kings#PREMIO_LIGA}, Kings Cup +{@link Kings#PREMIO_COPA},
 *   Kings World Cup Clubs +{@link Kings#PREMIO_MUNDIAL};</li>
 *   <li>gastos: as propostas de wildcard (taxa ao clube, se ele tem contrato, + salário da temporada).
 *   O draft, os elencos reais, os atletas genéricos da IOS e os reforços de emergência do auxiliar
 *   (menos de 7 na hora do jogo) não custam ao time. O treino não custa.</li>
 * </ul>
 * O caixa nunca fica negativo: quem não tem dinheiro não contrata (IA e humano).
 */
public final class DinheiroKings {
    private DinheiroKings() {}

    /** Entrada no caixa (orçamento, prêmio). */
    public static void creditar(Mundo m, LigaKings.TimeKings tk, String tipo, String descricao, long valor) {
        if (tk == null || valor <= 0) return;
        tk.caixa += valor;
        registrar(m, tk, tipo, descricao, valor);
    }

    /** Gasto: só se houver caixa (nunca fica negativo). Falso = sem dinheiro, nada muda. */
    public static boolean debitar(Mundo m, LigaKings.TimeKings tk, String tipo, String descricao, long valor) {
        if (tk == null) return false;
        if (valor <= 0) return true;
        if (tk.caixa < valor) return false;
        tk.caixa -= valor;
        registrar(m, tk, tipo, descricao, -valor);
        return true;
    }

    private static void registrar(Mundo m, LigaKings.TimeKings tk, String tipo, String descricao, long valor) {
        if (tk.extrato == null) tk.extrato = new java.util.ArrayList<>();
        LigaKings.MovimentoKings x = new LigaKings.MovimentoKings();
        x.ano = m.ano;
        x.semana = m.semana;
        x.tipo = tipo;
        x.descricao = descricao;
        x.valor = valor;
        tk.extrato.add(x);
        while (tk.extrato.size() > Kings.EXTRATO_MAX) tk.extrato.remove(0);
    }

    /** O orçamento da temporada (uma vez por ano por time). */
    public static void orcamento(Mundo m, LigaKings.TimeKings tk) {
        if (tk == null || tk.anoOrcamento >= m.ano) return;
        tk.anoOrcamento = m.ano;
        creditar(m, tk, "orcamento", "Orçamento da temporada " + m.ano, Kings.ORCAMENTO_TEMPORADA);
    }

    /** Dinheiro reservado nas propostas em andamento do time. */
    public static long comprometido(LigaKings liga, LigaKings.TimeKings tk) {
        long s = 0;
        if (liga != null && liga.propostas != null) for (LigaKings.PropostaKings p : liga.propostas) if (p.timeId.equals(tk.id)) s += p.custo();
        return s;
    }

    /** Caixa livre para novas propostas (caixa menos o que já está reservado). */
    public static long disponivel(LigaKings liga, LigaKings.TimeKings tk) {
        return Math.max(0, tk.caixa - comprometido(liga, tk));
    }

    /** "R$ 1,5 mi", "R$ 120 mil", "R$ 900" (textos das mensagens e notícias). */
    public static String reais(long v) {
        long a = Math.abs(v);
        String s;
        if (a >= 1_000_000) {
            double mi = a / 1_000_000.0;
            s = (mi == Math.floor(mi) ? String.valueOf((long) mi) : String.format(java.util.Locale.ROOT, "%.1f", mi).replace('.', ',')) + " mi";
        } else if (a >= 1_000) s = (a / 1000) + " mil";
        else s = String.valueOf(a);
        return (v < 0 ? "−R$ " : "R$ ") + s;
    }

    /** Prêmio pela competição vencida (0 se não dá prêmio): Split, Kings Cup ou Kings World Cup Clubs. */
    public static long premio(String competicaoId) {
        if (competicaoId == null) return 0;
        if (competicaoId.equals(Kings.MUNDIAL_CLUBES)) return Kings.PREMIO_MUNDIAL;
        if (competicaoId.endsWith("-cup")) return Kings.PREMIO_COPA;
        if (competicaoId.endsWith("-split1") || competicaoId.endsWith("-split2")) return Kings.PREMIO_LIGA;
        return 0;
    }
}
