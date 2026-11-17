package br.futebas.kings;

import br.futebas.mundo.Posicao;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Times customizados (Opções → Jogo → Times customizados): a "Kings League IOS" e a "Kings Cup
 * IOS", lidas de {@code dados/custom/kings-ios.json} (fora do banco e de repositório público).
 * Cada time tem um presidente que também joga (regra do jogo, 12/11/2026: fora o Kelvin no G3X,
 * só os presidentes da IOS jogam): força 75 (fora da escala {@link Kings#escalaKings}), atacante,
 * 18 anos (o mais novo com a idade mínima da Kings), fixo no time para sempre, ocupando uma vaga de
 * wildcard e contando para o teto de 12 atletas.
 */
public final class KingsCustom {
    private KingsCustom() {}

    public static final String ARQUIVO = "dados/custom/kings-ios.json";
    public static final String LIGA_ID = "kings-ios";

    /** Definição lida do JSON (ou montada em teste). */
    public static final class Definicao {
        public String id = LIGA_ID, nome = "Kings League IOS", nomeCopa = "Kings Cup IOS", estadio, cidade, inicio = "2026-01-08";
        public String regrasJson, draftJson;
        public List<Time> times = new ArrayList<>();
    }

    public static final class Time {
        public String id, nome, presidente, presidenteId, escudo, foto, cor1, cor2;
        public int idade = 18;
        public double forca = 75;
        public Posicao posicao = Posicao.CA;
        public String pais = "BRA";
    }

    /** Procura o arquivo a partir da raiz do jogo (e das pastas acima da atual); null se não houver. */
    public static Path localizar(Path raiz) {
        List<Path> cand = new ArrayList<>();
        if (raiz != null) cand.add(raiz.resolve(ARQUIVO));
        for (Path p = Path.of("").toAbsolutePath(); p != null && cand.size() < 6; p = p.getParent()) cand.add(p.resolve(ARQUIVO));
        for (Path p : cand) if (Files.isRegularFile(p)) return p;
        return null;
    }

    public static Definicao carregar(Path arquivo) {
        try {
            return ler(Files.readString(arquivo, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler " + arquivo + ": " + e.getMessage(), e);
        }
    }

    public static Definicao ler(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        Definicao d = new Definicao();
        JsonObject l = o.has("liga") ? o.getAsJsonObject("liga") : new JsonObject();
        d.id = s(l, "id", d.id);
        d.nome = s(l, "nome", d.nome);
        d.nomeCopa = s(l, "nome_copa", d.nomeCopa);
        d.estadio = s(l, "estadio", null);
        d.cidade = s(l, "cidade", null);
        d.inicio = s(l, "inicio", d.inicio);
        if (l.has("regras")) d.regrasJson = l.get("regras").toString();
        if (l.has("draft")) d.draftJson = l.get("draft").toString();
        JsonObject pj = o.has("presidente_jogador") ? o.getAsJsonObject("presidente_jogador") : new JsonObject();
        JsonArray ts = o.has("times") ? o.getAsJsonArray("times") : new JsonArray();
        for (JsonElement e : ts) {
            JsonObject x = e.getAsJsonObject();
            Time t = new Time();
            t.id = s(x, "id", null);
            t.nome = s(x, "nome", t.id);
            t.presidente = s(x, "presidente", null);
            t.presidenteId = s(x, "presidente_id", t.id + "-presidente");
            t.escudo = s(x, "escudo", null);
            t.foto = s(x, "foto", null);
            t.cor1 = s(x, "cor1", null);
            t.cor2 = s(x, "cor2", null);
            t.idade = x.has("idade") ? x.get("idade").getAsInt() : pj.has("idade") ? pj.get("idade").getAsInt() : 18;
            t.forca = pj.has("forca") ? pj.get("forca").getAsDouble() : 75;
            t.posicao = pj.has("posicao") ? Posicao.de(pj.get("posicao").getAsString()) : Posicao.CA;
            t.pais = s(pj, "pais", "BRA");
            if (t.id != null && t.presidente != null) d.times.add(t);
        }
        return d;
    }

    private static String s(JsonObject o, String k, String padrao) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : padrao;
    }

    /** A liga do jogo a partir da definição (sem país; calendário de ano, como a Kings League Brasil). */
    public static LigaKings criarLiga(Definicao d, int anoInicial) {
        LigaKings liga = new LigaKings();
        liga.id = d.id;
        liga.nome = d.nome;
        liga.nomeCopa = d.nomeCopa;
        liga.pais = null;
        liga.paisDraft = "BRA"; // calendário e pool do draft como a Kings League Brasil
        liga.custom = true;
        liga.versaoForca = Kings.VERSAO_FORCA;
        liga.fontes = "times customizados (" + ARQUIVO + ")";
        liga.anoInicio = Math.max(anoInicial, br.futebas.dados.Db.ano(d.inicio, anoInicial));
        liga.semanaInicio = Kings.semanaDe(d.inicio);
        // padrão: regras gerais 2026 (vitória = 1 ponto), 20 times em turno único, 8 nos playoffs
        Kings.usarPontuacao2026(liga.regras);
        liga.regras.classificados_playoff = 8;
        liga.regras.semanas = 22;
        Kings.lerRegras(liga, d.regrasJson);
        Kings.usarPontuacao2026(liga.regras);
        liga.draft.mantidos = 0;
        Kings.lerDraft(liga, d.draftJson);
        for (Time t : d.times) {
            LigaKings.TimeKings tk = new LigaKings.TimeKings();
            tk.id = t.id;
            tk.nome = t.nome;
            tk.presidente = t.presidente;
            tk.escudoPng = t.escudo;
            tk.cor1 = t.cor1;
            tk.cor2 = t.cor2;
            LigaKings.AtletaKings a = new LigaKings.AtletaKings();
            a.nome = t.presidente;
            a.idFixo = t.presidenteId;
            a.foto = t.foto != null ? t.foto : "assets/custom/ios/fotos/" + t.presidenteId + ".png";
            a.forca = t.forca;
            a.posicao = t.posicao;
            a.pais = t.pais;
            a.anoNasc = anoInicial - Math.max(Kings.IDADE_MINIMA_JOGAR, t.idade);
            a.presidente = true;
            a.tipo = "presidente";
            a.fonte = "custom";
            tk.presidentesJogadores.add(a);
            Kings.garantirPresidentes(tk);
            liga.times.add(tk);
        }
        return liga;
    }
}
