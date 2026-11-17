package br.futebas.ui.modelo;

import br.futebas.historia.Historia;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Mensagem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Escolhe as poucas manchetes da página inicial (requisito: "diminuir o tanto de notícias"):
 * mistura a caixa de entrada e o noticiário, dá pontos pela relevância (o seu time, as suas
 * competições, na carreira da Kings as mensagens da Kings; não lidas e propostas pesam mais) e
 * pela idade, e deixa o resto do mundo (as megatransferências) em no máximo {@link #MUNDO_MAX}
 * destaques. Nada é apagado: a lista completa continua na caixa de entrada.
 */
public final class Manchetes {
    /** Quantas notícias do resto do mundo entram na página inicial. */
    public static final int MUNDO_MAX = 2;
    /** Notícias mais velhas que isso (semanas) só entram se faltar assunto. */
    public static final int IDADE_MAX = 8;
    /** Do mesmo tipo (resultado do seu time, mercado...), no máximo isso antes de completar com o resto. */
    public static final int POR_TIPO = 4;

    /** Uma manchete escolhida: a mensagem ou a notícia de origem (uma das duas é null). */
    public record Item(Mensagem mensagem, Historia.Noticia noticia, int pontos, boolean mundo) {
        public int ano() { return mensagem != null ? mensagem.ano : noticia.ano; }

        public int semana() { return mensagem != null ? mensagem.semana : noticia.semana; }
    }

    private Manchetes() {}

    /**
     * @param mensagens caixa de entrada (qualquer ordem)
     * @param noticias  noticiário do motor
     * @param kings     carreira só da Kings (o técnico treina um time da Kings e nenhum clube)
     * @param focoMax   opção "notícias do mundo" (0–3): notícias com foco maior ficam de fora
     */
    public static List<Item> escolher(List<Mensagem> mensagens, List<Historia.Noticia> noticias, int ano, int semana,
                                      boolean kings, int focoMax, int limite) {
        int agora = Calendario.absoluta(ano, semana);
        List<Item> todos = new ArrayList<>();
        for (Mensagem x : mensagens) {
            if (x == null || "resultado".equals(x.tipo)) continue;
            todos.add(new Item(x, null, pontos(x, kings) - 5 * idade(agora, x.ano, x.semana), mundo(x)));
        }
        for (Historia.Noticia n : noticias) {
            if (n == null || n.foco > focoMax) continue;
            boolean doMundo = n.foco >= 3 && !(kings && kingsRelacionada(n));
            todos.add(new Item(null, n, pontos(n, kings) - 5 * idade(agora, n.ano, n.semana), doMundo));
        }
        todos.sort(Comparator.comparingInt((Item i) -> -i.pontos()).thenComparingInt(i -> -Calendario.absoluta(i.ano(), i.semana())));
        List<Item> l = new ArrayList<>();
        java.util.Map<String, Integer> porTipo = new java.util.HashMap<>();
        int mundo = 0;
        // primeiro as recentes e variadas (no máximo POR_TIPO do mesmo tipo); se faltar assunto, o resto completa
        for (int passo = 0; passo < 2 && l.size() < limite; passo++) {
            for (Item i : todos) {
                if (l.size() >= limite) break;
                if (l.contains(i)) continue;
                boolean velha = idade(agora, i.ano(), i.semana()) > IDADE_MAX;
                String tipo = i.mensagem() != null ? "m:" + i.mensagem().tipo : "n:" + i.noticia().tipo;
                if (passo == 0 && (velha || porTipo.getOrDefault(tipo, 0) >= POR_TIPO)) continue;
                if (i.mundo() && mundo >= MUNDO_MAX) continue;
                if (i.mundo()) mundo++;
                porTipo.merge(tipo, 1, Integer::sum);
                l.add(i);
            }
        }
        return l;
    }

    private static int idade(int agora, int ano, int semana) {
        return Math.max(0, agora - Calendario.absoluta(ano, semana));
    }

    private static boolean mundo(Mensagem x) {
        String t = x.tipo == null ? "" : x.tipo;
        return !List.of("proposta", "jogador", "diretoria", "torcida", "mercado", "financas", "selecao", "kings").contains(t);
    }

    private static int pontos(Mensagem x, boolean kings) {
        String t = x.tipo == null ? "" : x.tipo;
        int p = x.lida ? 45 : 70;
        switch (t) {
            case "proposta" -> p += x.lida ? 5 : 20;
            case "diretoria", "jogador", "torcida" -> p += 5;
            case "kings" -> p += kings ? 20 : 0;
            default -> { if (mundo(x)) p -= 30; }
        }
        return p;
    }

    private static int pontos(Historia.Noticia n, boolean kings) {
        int peso = Math.max(0, Math.min(100, n.peso));
        if (kings && kingsRelacionada(n)) return 75 + peso / 10;
        return switch (n.foco) {
            case 0 -> 85 + peso / 10;
            case 1 -> 60 + peso / 10;
            case 2 -> 38 + peso / 10;
            default -> 10 + peso / 5;
        };
    }

    private static boolean kingsRelacionada(Historia.Noticia n) {
        return (n.competicaoId != null && n.competicaoId.startsWith("kings")) || (n.clubeId != null && n.clubeId.startsWith("kings"));
    }
}
