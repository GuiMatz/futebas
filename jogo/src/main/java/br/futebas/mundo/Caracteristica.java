package br.futebas.mundo;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Nome de exibição (pt-BR) das características dos jogadores.
 * No banco elas ficam como códigos sem acento ("saida_do_gol"); na tela, sempre use {@link #nome(String)}.
 */
public final class Caracteristica {
    private Caracteristica() {}

    private static final Map<String, String> NOMES = Map.ofEntries(
            Map.entry("finalizacao", "Finalização"),
            Map.entry("velocidade", "Velocidade"),
            Map.entry("marcacao", "Marcação"),
            Map.entry("desarme", "Desarme"),
            Map.entry("passe", "Passe"),
            Map.entry("cabeceio", "Cabeceio"),
            Map.entry("drible", "Drible"),
            Map.entry("cruzamento", "Cruzamento"),
            Map.entry("resistencia", "Resistência"),
            Map.entry("armacao", "Armação"),
            Map.entry("colocacao", "Colocação"),
            Map.entry("saida_do_gol", "Saída do gol"),
            Map.entry("reflexo", "Reflexo"),
            Map.entry("defesa_de_penalti", "Defesa de pênalti"),
            Map.entry("lancamento", "Lançamento"),
            Map.entry("chute_de_longe", "Chute de longe"),
            Map.entry("cobranca_de_falta", "Cobrança de falta"),
            Map.entry("lideranca", "Liderança"),
            Map.entry("forca_fisica", "Força física"),
            Map.entry("posicionamento", "Posicionamento"),
            Map.entry("visao_de_jogo", "Visão de jogo"),
            Map.entry("penalti", "Pênalti"));

    /** "saida_do_gol" → "Saída do gol". Códigos desconhecidos viram texto legível (sem sublinhado, inicial maiúscula). */
    public static String nome(String codigo) {
        if (codigo == null || codigo.isBlank()) return "";
        String chave = Normalizer.normalize(codigo.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        String n = NOMES.get(chave);
        if (n != null) return n;
        String t = codigo.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
