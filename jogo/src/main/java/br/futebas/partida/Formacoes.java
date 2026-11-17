package br.futebas.partida;

import br.futebas.mundo.Posicao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static br.futebas.mundo.Posicao.*;

/** Formações disponíveis: 11 vagas cada, a primeira sempre o goleiro. */
public final class Formacoes {
    private Formacoes() {}

    public static final Map<String, Posicao[]> TODAS = new LinkedHashMap<>();

    static {
        TODAS.put("4-4-2", new Posicao[]{GOL, LD, ZAG, ZAG, LE, VOL, MC, PE, PD, CA, CA});
        TODAS.put("4-3-3", new Posicao[]{GOL, LD, ZAG, ZAG, LE, VOL, MC, MEI, PE, PD, CA});
        TODAS.put("4-2-3-1", new Posicao[]{GOL, LD, ZAG, ZAG, LE, VOL, VOL, PE, MEI, PD, CA});
        TODAS.put("4-5-1", new Posicao[]{GOL, LD, ZAG, ZAG, LE, VOL, MC, MC, PE, PD, CA});
        TODAS.put("3-5-2", new Posicao[]{GOL, ZAG, ZAG, ZAG, LD, LE, VOL, MC, MEI, CA, CA});
        TODAS.put("3-4-3", new Posicao[]{GOL, ZAG, ZAG, ZAG, LD, LE, VOL, MC, PE, PD, CA});
        TODAS.put("5-3-2", new Posicao[]{GOL, LD, ZAG, ZAG, ZAG, LE, VOL, MC, MEI, CA, CA});
        TODAS.put("4-4-2 losango", new Posicao[]{GOL, LD, ZAG, ZAG, LE, VOL, MC, MC, MEI, CA, CA});
    }

    /** Formações em que PE/PD são meias abertos (linha de 4 ou 5 no meio), não pontas. */
    private static final java.util.Set<String> MEIAS_ABERTOS = java.util.Set.of("4-4-2", "4-5-1");

    /**
     * Rótulo de cada vaga para a tela: a sigla da posição, mas nos 4-4-2 e 4-5-1 os abertos do
     * meio são "ME"/"MD" (meia esquerda/direita), não pontas.
     */
    public static String[] rotulos(String formacao) {
        Posicao[] v = vagas(formacao);
        String[] r = new String[v.length];
        boolean meias = MEIAS_ABERTOS.contains(formacao == null || !TODAS.containsKey(formacao) ? "4-4-2" : formacao);
        for (int i = 0; i < v.length; i++) r[i] = meias && v[i] == PE ? "ME" : meias && v[i] == PD ? "MD" : v[i].name();
        return r;
    }

    /** Nome por extenso do rótulo ("ME" → "Meia esquerda"). */
    public static String nomeRotulo(String rotulo) {
        return switch (rotulo) {
            case "ME" -> "Meia esquerda";
            case "MD" -> "Meia direita";
            default -> Posicao.valueOf(rotulo).nome;
        };
    }

    public static Posicao[] vagas(String formacao) {
        Posicao[] v = TODAS.get(formacao);
        return v != null ? v : TODAS.get("4-4-2");
    }

    public static List<String> nomes() {
        return List.copyOf(TODAS.keySet());
    }
}
