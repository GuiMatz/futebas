package br.futebas.mundo;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Artigo certo para o nome de uma competição nos textos ("o Campeonato", "a Copa", "a
 * Libertadores", "a Série A", "o Brasileirão"): {@link #de} dá "do/da", {@link #em} dá "no/na".
 */
public final class Artigos {
    private Artigos() {}

    private static final Set<String> FEMININAS = Set.of("copa", "liga", "serie", "ligue", "libertadores", "sul-americana", "sudamericana",
            "sulamericana", "champions", "taca", "supercopa", "recopa", "premier", "bundesliga", "eredivisie", "primeira", "primera", "superliga",
            "super", "mls", "kings", "conference", "europa", "a-league", "j1", "j2", "k", "saudi", "segunda", "tercera", "terceira", "superlig",
            "allsvenskan", "eliteserien", "ekstraklasa", "challenge", "championship", "national", "pro", "uefa", "conmebol", "concacaf", "afc", "caf",
            "nations", "world", "fa", "efl", "a", "b", "finalissima", "eurocopa", "euro");
    private static final Set<String> MASCULINAS = Set.of("campeonato", "torneio", "mundial", "brasileirao", "paulistao", "carioca", "paulista",
            "mineiro", "gaucho", "gauchao", "baiano", "baianao", "estadual", "amistoso", "quadrangular", "triangular", "jogo", "trofeu", "jamor",
            "clausura", "apertura", "scudetto", "calcio", "torneo", "campeonatos");

    private static String sem(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** A competição é tratada no feminino ("a Copa", "a Libertadores")? */
    public static boolean feminina(String nome) {
        if (nome == null || nome.isBlank()) return false;
        String n = sem(nome.trim());
        String primeira = n.split("[\\s]+")[0];
        if (MASCULINAS.contains(primeira)) return false;
        if (FEMININAS.contains(primeira)) return true;
        return n.matches(".*\\b(liga|league|ligue|cup|copa|serie|lig)\\b.*");
    }

    /** "o" ou "a". */
    public static String o(String nome) { return feminina(nome) ? "a" : "o"; }

    /** "do" ou "da". */
    public static String de(String nome) { return feminina(nome) ? "da" : "do"; }

    /** "no" ou "na". */
    public static String em(String nome) { return feminina(nome) ? "na" : "no"; }
}
