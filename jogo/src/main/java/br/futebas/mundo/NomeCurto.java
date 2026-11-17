package br.futebas.mundo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Nome de exibição de um jogador real. Usa o {@code nome_conhecido} do banco (o nome popular:
 * "Cafu", "Marcos"). Quando o banco só tem o nome completo e ele é longo, encurta o PRÓPRIO
 * nome real (prenome + último sobrenome, ou o prenome composto) — nunca inventa nada.
 * O nome completo aparece só na ficha do jogador.
 */
public final class NomeCurto {
    private NomeCurto() {}

    /** Até este tamanho o nome completo é mostrado como está. */
    static final int LIMITE = 16;

    private static final Set<String> PARTICULAS = Set.of("da", "de", "do", "das", "dos", "e", "di", "del", "della", "van", "von", "der", "den", "la", "le", "y");
    private static final Set<String> SUFIXOS = Set.of("júnior", "junior", "jr", "jr.", "filho", "neto", "sobrinho", "segundo", "terceiro");
    /** Segundos prenomes comuns em nomes compostos brasileiros ("Carlos Henrique", "João Pedro"). */
    private static final Set<String> COMPOSTOS = Set.of("henrique", "pedro", "paulo", "carlos", "eduardo", "fernando", "augusto",
            "victor", "vitor", "vítor", "gabriel", "lucas", "sandro", "marcos", "antônio", "antonio", "felipe", "filipe", "miguel",
            "luiz", "luís", "luis", "josé", "jose", "roberto", "ricardo", "alberto", "césar", "cesar", "guilherme", "rafael",
            "vinícius", "vinicius", "emanuel", "manoel", "manuel", "alexandre", "renato", "rodrigo", "marcelo", "maria", "hugo", "gustavo");

    /** Nome a exibir: o conhecido, se houver e for diferente do completo; senão, o completo encurtado. */
    public static String de(String nome, String conhecido) {
        if (conhecido != null && !conhecido.isBlank()) {
            String k = conhecido.trim();
            if (nome == null || !k.equalsIgnoreCase(nome.trim()) || k.length() <= LIMITE) return k;
            return encurtar(k);
        }
        return nome == null ? "" : encurtar(nome.trim());
    }

    /** Encurta um nome completo real: "Oscar dos Santos Emboaba Júnior" → "Oscar Emboaba". */
    public static String encurtar(String nome) {
        if (nome == null) return "";
        if (nome.length() <= LIMITE || nome.contains("#") || nome.contains("(")) return nome;
        String[] t = nome.trim().split("\\s+");
        if (t.length <= 2) return nome;
        List<String> sobrenomes = new ArrayList<>();
        for (int i = 1; i < t.length; i++) {
            String w = t[i].toLowerCase(Locale.ROOT);
            if (PARTICULAS.contains(w) || SUFIXOS.contains(w)) continue;
            sobrenomes.add(t[i]);
        }
        if (sobrenomes.isEmpty()) return t[0] + " " + t[t.length - 1];
        if (COMPOSTOS.contains(t[1].toLowerCase(Locale.ROOT))) return t[0] + " " + t[1];
        return t[0] + " " + sobrenomes.get(sobrenomes.size() - 1);
    }
}
