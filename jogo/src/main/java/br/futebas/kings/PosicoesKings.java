package br.futebas.kings;

import br.futebas.dados.BancoMundo;
import br.futebas.mundo.Posicao;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Posições oficiais dos atletas da Kings League (site oficial, coletadas em
 * {@code coleta/intermediario/kings_posicoes.json} e copiadas para {@code assets/kings/posicoes.json},
 * que o pacote leva). Chave "&lt;time_id&gt;|&lt;nome_fonte&gt;" → GOL / DEF / MEI / ATA.
 *
 * <p>Antes, todo atleta sem jogador do mundo ficava MC (padrão de {@code AtletaKings}): nenhum time
 * tinha goleiro. Agora: GOL → GOL; DEF → ZAG (um em cada três LD/LE); MEI → MC (VOL/MEI pelo nome);
 * ATA → CA (um em cada três PE/PD). Quem não está no arquivo recebe a posição que o elenco mais
 * precisa ({@link #equilibrar}: ao menos 1 goleiro, depois defesa/meio/ataque equilibrados). A
 * variedade sai do hash do nome (determinística, sem gastar o {@code Rng}).
 */
public final class PosicoesKings {
    private PosicoesKings() {}

    public static final String ARQUIVO = "assets/kings/posicoes.json";
    private static Map<String, String> cache;
    private static Path carregado;

    /** Mapa chave → GOL/DEF/MEI/ATA (vazio se o arquivo não existir). */
    public static synchronized Map<String, String> mapa(BancoMundo db) {
        Path p = localizar(db);
        if (cache != null && java.util.Objects.equals(p, carregado)) return cache;
        Map<String, String> m = new HashMap<>();
        if (p != null) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject x = e.getValue().getAsJsonObject();
                    if (x.has("posicao") && !x.get("posicao").isJsonNull()) m.put(chaveNormal(e.getKey()), x.get("posicao").getAsString());
                }
            } catch (Exception ex) {
                System.err.println("[kings] posições: não deu para ler " + p + ": " + ex.getMessage());
            }
        }
        cache = m;
        carregado = p;
        return m;
    }

    private static Map<String, String> cacheNome;
    private static Map<String, String> fonteNome;

    /** Nome → setor, só para nomes sem ambiguidade (o mesmo atleta em dois times com setores diferentes fica de fora). */
    static synchronized Map<String, String> porNome(Map<String, String> mapa) {
        if (cacheNome != null && fonteNome == mapa) return cacheNome;
        Map<String, String> m = new HashMap<>();
        java.util.Set<String> ambiguos = new java.util.HashSet<>();
        for (Map.Entry<String, String> e : mapa.entrySet()) {
            int i = e.getKey().indexOf('|');
            String nome = e.getKey().substring(i + 1);
            String antes = m.putIfAbsent(nome, e.getValue());
            if (antes != null && !antes.equals(e.getValue())) ambiguos.add(nome);
        }
        for (String a : ambiguos) m.remove(a);
        cacheNome = m;
        fonteNome = mapa;
        return m;
    }

    static String chaveNormal(String k) {
        return k.toLowerCase(java.util.Locale.ROOT).trim();
    }

    public static String chave(String timeId, String nome) {
        return chaveNormal(timeId + "|" + nome);
    }

    static Path localizar(BancoMundo db) {
        List<Path> cand = new ArrayList<>();
        if (db != null && db.caminho != null) {
            Path raiz = db.caminho.toAbsolutePath().getParent();
            if (raiz != null && raiz.getParent() != null) cand.add(raiz.getParent().resolve(ARQUIVO));
        }
        for (Path p = Path.of("").toAbsolutePath(); p != null && cand.size() < 8; p = p.getParent()) cand.add(p.resolve(ARQUIVO));
        for (Path p : cand) if (Files.isRegularFile(p)) return p;
        return null;
    }

    /** A posição de jogo para o setor oficial (variedade pelo hash do nome). */
    public static Posicao posicao(String setor, String nome) {
        int h = Math.floorMod(nome == null ? 0 : nome.hashCode(), 6);
        return switch (setor == null ? "" : setor.toUpperCase(java.util.Locale.ROOT)) {
            case "GOL" -> Posicao.GOL;
            case "DEF" -> h == 0 ? Posicao.LD : h == 1 ? Posicao.LE : Posicao.ZAG;
            case "MEI" -> h == 0 ? Posicao.VOL : h == 1 ? Posicao.MEI : Posicao.MC;
            case "ATA" -> h == 0 ? Posicao.PE : h == 1 ? Posicao.PD : Posicao.CA;
            default -> null;
        };
    }

    /**
     * Aplica as posições oficiais aos atletas do time que não são jogadores do mundo (os do mundo
     * já têm posição) nem presidentes-jogadores; depois equilibra os que ficaram sem posição.
     * Retorna quantos receberam a posição do arquivo.
     */
    public static int aplicar(LigaKings.TimeKings t, Map<String, String> mapa) {
        if (t == null || t.elenco == null) return 0;
        List<LigaKings.AtletaKings> sem = new ArrayList<>();
        int n = 0;
        for (LigaKings.AtletaKings a : t.elenco) {
            if (a.jogadorId != null || a.presidente || a.nome == null) continue;
            String setor = mapa.get(chave(t.id, a.nome));
            if (setor == null) setor = porNome(mapa).get(chaveNormal(a.nome)); // seleções da KWC Nations: o mesmo atleta de outro time
            Posicao p = posicao(setor, a.nome);
            if (p != null) {
                a.posicao = p;
                n++;
            } else {
                sem.add(a);
            }
        }
        equilibrar(t, sem);
        return n;
    }

    static int setor(Posicao p) {
        return switch (p) {
            case GOL -> 0;
            case ZAG, LD, LE -> 1;
            case VOL, MC, MEI -> 2;
            default -> 3;
        };
    }

    /**
     * Dá posição aos atletas {@code livres} (sem posição conhecida) pela necessidade do elenco: 1
     * goleiro (2 com 10+ atletas), depois o setor com menos gente em relação ao alvo de um 7 (1-2-2-2)
     * e, por fim, garante ao menos 1 goleiro (o atleta mais fraco de linha vai para o gol se for preciso).
     */
    public static void equilibrar(LigaKings.TimeKings t, List<LigaKings.AtletaKings> livres) {
        Map<Integer, Integer> cont = new HashMap<>();
        List<LigaKings.AtletaKings> fixos = new ArrayList<>(t.elenco);
        fixos.removeAll(livres);
        for (LigaKings.AtletaKings a : fixos) cont.merge(setor(a.posicao), 1, Integer::sum);
        int goleirosAlvo = t.elenco.size() >= 10 ? 2 : 1;
        double[] alvo = {0, 2, 2.5, 2};
        List<LigaKings.AtletaKings> ordem = new ArrayList<>(livres);
        ordem.sort(Comparator.comparing((LigaKings.AtletaKings a) -> a.nome == null ? "" : a.nome));
        for (LigaKings.AtletaKings a : ordem) {
            int s;
            if (cont.getOrDefault(0, 0) < goleirosAlvo) s = 0;
            else {
                s = 1;
                double pior = Double.MAX_VALUE;
                for (int k = 1; k <= 3; k++) {
                    double r = cont.getOrDefault(k, 0) / alvo[k];
                    if (r < pior) {
                        pior = r;
                        s = k;
                    }
                }
            }
            a.posicao = s == 0 ? Posicao.GOL : posicao(s == 1 ? "DEF" : s == 2 ? "MEI" : "ATA", a.nome);
            cont.merge(s, 1, Integer::sum);
        }
        garantirGoleiro(t);
    }

    /** Sem goleiro no elenco: o atleta de linha mais fraco (nunca um presidente) vai para o gol. */
    public static void garantirGoleiro(LigaKings.TimeKings t) {
        if (t == null || t.elenco == null || t.elenco.isEmpty()) return;
        for (LigaKings.AtletaKings a : t.elenco) if (a.posicao == Posicao.GOL) return;
        LigaKings.AtletaKings pior = null;
        for (LigaKings.AtletaKings a : t.elenco) if (!a.presidente && (pior == null || a.forca < pior.forca)) pior = a;
        if (pior != null) pior.posicao = Posicao.GOL;
    }

    /** Contagem por setor (para diagnóstico e testes). */
    public static Map<Posicao, Integer> contagem(LigaKings.TimeKings t) {
        Map<Posicao, Integer> m = new EnumMap<>(Posicao.class);
        for (LigaKings.AtletaKings a : t.elenco) m.merge(a.posicao, 1, Integer::sum);
        return m;
    }
}
