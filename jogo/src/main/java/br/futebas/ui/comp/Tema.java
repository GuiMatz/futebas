package br.futebas.ui.comp;

import javafx.scene.paint.Color;

/** Cores derivadas do clube (o cabeçalho usa as cores do clube do técnico como destaque). */
public final class Tema {
    private Tema() {}

    public static final String TINTA = "#111b24";
    public static final String GRAMADO = "#0b7a4b";

    public static Color cor(String hex, Color padrao) {
        if (hex == null || hex.isBlank()) return padrao;
        try {
            return Color.web(hex.trim());
        } catch (IllegalArgumentException e) {
            return padrao;
        }
    }

    public static double luminancia(Color c) {
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
    }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    /** Fundo do cabeçalho: a cor mais escura do clube, escurecida até o texto branco ficar legível. */
    public static Color fundoClube(String cor1, String cor2) {
        Color a = cor(cor1, Color.web(TINTA)), b = cor(cor2, Color.web(TINTA));
        Color c = luminancia(a) <= luminancia(b) ? a : b;
        if (luminancia(c) > 0.62) c = Color.web(TINTA);
        int guarda = 0;
        while (luminancia(c) > 0.30 && guarda++ < 12) c = c.deriveColor(0, 1.0, 0.86, 1);
        return c;
    }

    /** Faixa de destaque sob o cabeçalho: a outra cor do clube (ou o verde do jogo). */
    public static Color faixaClube(String cor1, String cor2) {
        Color a = cor(cor1, null), b = cor(cor2, null);
        Color fundo = fundoClube(cor1, cor2);
        Color outra = a == null ? b : b == null ? a : (luminancia(a) <= luminancia(b) ? b : a);
        if (outra == null || Math.abs(luminancia(outra) - luminancia(fundo)) < 0.12) return Color.web(GRAMADO);
        return outra;
    }

    private static double saturacao(Color c) {
        return c.getSaturation();
    }

    /**
     * Cor de acento a partir das cores do clube: a primeira que tenha contraste com o fundo claro
     * (nem quase branca nem quase preta); se as duas forem neutras (preto e branco), um grafite.
     */
    public static Color acento(String cor1, String cor2) {
        Color a = cor(cor1, null), b = cor(cor2, null);
        for (Color c : new Color[] {a, b}) {
            if (c == null) continue;
            double l = luminancia(c);
            if (l > 0.06 && l < 0.72 && saturacao(c) > 0.25) return l > 0.55 ? c.deriveColor(0, 1, 0.78, 1) : c;
        }
        for (Color c : new Color[] {a, b}) {
            if (c != null && saturacao(c) > 0.35 && luminancia(c) >= 0.72) return c.deriveColor(0, 1, 0.62, 1);
        }
        return Color.web("#26313c");
    }

    /**
     * Variáveis de acento para o CSS (ver {@code -acento*} no tema): botão principal, aba ativa,
     * linha do meu clube, JOGAR, foco e o indicador da navegação escura.
     */
    public static String estiloAcento(String cor1, String cor2) {
        Color c = acento(cor1, cor2);
        Color superficie = Color.web("#262a31");
        // no tema escuro o acento precisa aparecer: clareia as cores muito escuras
        Color nav = c;
        int guarda = 0;
        while (luminancia(nav) < 0.34 && guarda++ < 10) nav = misturar(nav, Color.WHITE, 0.22);
        if (saturacao(c) < 0.2) nav = Color.web("#d9dee4");
        if (luminancia(c) < 0.12) c = misturar(c, nav, 0.5);
        Color hover = c.deriveColor(0, 1, 1.12, 1);
        Color claro = misturar(c, superficie, 0.80);
        Color medio = misturar(c, superficie, 0.62);
        Color escuro = nav;
        String texto = luminancia(c) > 0.6 ? TINTA : "white";
        Color base = fundoClube(cor1, cor2);
        Color topoNav = misturar(base, Color.web("#111418"), 0.55);
        return "-acento: " + hex(c) + "; -acento-hover: " + hex(hover) + "; -acento-claro: " + hex(claro) + "; -acento-medio: " + hex(medio)
                + "; -acento-escuro: " + hex(escuro) + "; -acento-texto: " + texto + "; -acento-nav: " + hex(nav)
                + "; -nav-fundo: linear-gradient(to bottom, " + hex(topoNav) + " 0%, #111418 65%);";
    }

    private static Color misturar(Color a, Color b, double t) {
        return new Color(a.getRed() + (b.getRed() - a.getRed()) * t, a.getGreen() + (b.getGreen() - a.getGreen()) * t,
                a.getBlue() + (b.getBlue() - a.getBlue()) * t, 1);
    }

    public static String estiloCabecalho(String cor1, String cor2) {
        Color f = fundoClube(cor1, cor2);
        return "-fx-background-color: linear-gradient(to right, " + hex(f) + " 0%, " + hex(f.deriveColor(0, 1, 0.78, 1)) + " 100%);";
    }
}
