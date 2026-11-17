package br.futebas.mundo;

import java.util.EnumMap;
import java.util.Map;

/**
 * As 10 posições do jogo (as 9 do FUT.AE mais o volante). Cada uma traz o setor, os pesos de
 * ataque, defesa e meio da vaga (portados de {@code simulation.py}) e os pesos dos atributos
 * que formam a força (modelo do Brasfoot: média ponderada que soma 1).
 */
public enum Posicao {
    GOL('G', "Goleiro", 0.0, 0.0, 0.0),
    ZAG('D', "Zagueiro", 0.08, 1.0, 0.2),
    LD('D', "Lateral direito", 0.25, 0.75, 0.35),
    LE('D', "Lateral esquerdo", 0.25, 0.75, 0.35),
    VOL('M', "Volante", 0.2, 0.8, 0.95),
    MC('M', "Meio-campista", 0.3, 0.65, 1.0),
    MEI('M', "Meia", 0.6, 0.3, 0.9),
    PE('A', "Ponta esquerda", 0.8, 0.2, 0.4),
    PD('A', "Ponta direita", 0.8, 0.2, 0.4),
    CA('A', "Centroavante", 1.0, 0.08, 0.2);

    public final char setor;
    public final String nome;
    public final double pesoAtaque, pesoDefesa, pesoMeio;

    Posicao(char setor, String nome, double ataque, double defesa, double meio) {
        this.setor = setor;
        this.nome = nome;
        this.pesoAtaque = ataque;
        this.pesoDefesa = defesa;
        this.pesoMeio = meio;
    }

    // ------------------------------------------------------------- encaixe fora de posição
    private static final Map<Posicao, Map<Posicao, Double>> ENCAIXE = new EnumMap<>(Posicao.class);

    private static void e(Posicao natural, Object... pares) {
        Map<Posicao, Double> m = new EnumMap<>(Posicao.class);
        for (int i = 0; i < pares.length; i += 2) m.put((Posicao) pares[i], (Double) pares[i + 1]);
        ENCAIXE.put(natural, m);
    }

    static {
        // Tabela de positions.py; o volante (VOL) é um MC mais defensivo.
        // Regra do jogo: pontas (PE/PD) jogam nas duas pontas sem perda.
        e(GOL, GOL, 1.0);
        e(ZAG, ZAG, 1.0, LD, 0.85, LE, 0.85, VOL, 0.85, MC, 0.8);
        e(LD, LD, 1.0, LE, 0.9, ZAG, 0.85, PD, 0.85, MC, 0.8, VOL, 0.8);
        e(LE, LE, 1.0, LD, 0.9, ZAG, 0.85, PE, 0.85, MC, 0.8, VOL, 0.8);
        e(VOL, VOL, 1.0, MC, 0.95, ZAG, 0.85, MEI, 0.8, LD, 0.75, LE, 0.75);
        e(MC, MC, 1.0, VOL, 0.95, MEI, 0.9, ZAG, 0.85, LD, 0.8, LE, 0.8);
        e(MEI, MEI, 1.0, MC, 0.9, PE, 0.85, PD, 0.85, CA, 0.85, VOL, 0.8);
        e(PE, PE, 1.0, PD, 1.0, MEI, 0.85, CA, 0.85, LE, 0.8);
        e(PD, PD, 1.0, PE, 1.0, MEI, 0.85, CA, 0.85, LD, 0.8);
        e(CA, CA, 1.0, PE, 0.85, PD, 0.85, MEI, 0.8);
    }

    /** Rendimento (0 a 1) de quem é desta posição jogando na vaga; 0 = não pode. */
    public double encaixe(Posicao vaga) {
        Double v = ENCAIXE.get(this).get(vaga);
        return v == null ? 0.0 : v;
    }

    public static Posicao de(String sigla) {
        if (sigla == null) return MC;
        try {
            return Posicao.valueOf(sigla.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return switch (sigla.trim().toUpperCase()) {
                case "ATA", "SA", "CF", "ST" -> CA;
                case "LAT" -> LD;
                case "MD" -> PD;
                case "ME" -> PE;
                case "CB" -> ZAG;
                case "GK" -> GOL;
                default -> MC;
            };
        }
    }

    // ------------------------------------------------------------- atributos (FM-lite)
    /** Atributos: 0 goleiro, 1 velocidade, 2 passe, 3 armação, 4 desarme, 5 finalização, 6 técnica. */
    public static final String[] ATRIBUTOS = {"Goleiro", "Velocidade", "Passe", "Armação", "Desarme", "Finalização", "Técnica"};

    /** Pesos dos 7 atributos na força desta posição (somam 1). */
    public double[] pesosAtributos() {
        return switch (this) {
            case GOL -> new double[]{0.60, 0.15, 0.15, 0.0, 0.0, 0.0, 0.10};
            case ZAG -> new double[]{0.0, 0.15, 0.10, 0.0, 0.55, 0.0, 0.20};
            case LD, LE -> new double[]{0.0, 0.30, 0.20, 0.05, 0.30, 0.0, 0.15};
            case VOL -> new double[]{0.0, 0.10, 0.25, 0.10, 0.40, 0.0, 0.15};
            case MC -> new double[]{0.0, 0.10, 0.30, 0.25, 0.20, 0.05, 0.10};
            case MEI -> new double[]{0.0, 0.10, 0.25, 0.40, 0.0, 0.10, 0.15};
            case PE, PD -> new double[]{0.0, 0.40, 0.10, 0.10, 0.0, 0.25, 0.15};
            case CA -> new double[]{0.0, 0.15, 0.05, 0.05, 0.0, 0.50, 0.25};
        };
    }
}
