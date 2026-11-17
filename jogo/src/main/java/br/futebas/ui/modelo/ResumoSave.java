package br.futebas.ui.modelo;

import br.futebas.Jogo;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Mundo;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

/**
 * Resumo de um jogo salvo (clube, cores, técnico, data) num arquivo pequeno ao lado do save
 * ({@code <nome>.futebas.resumo}), para a tela de início mostrar o "Continuar" sem abrir o save.
 * Saves antigos sem resumo: {@link #extrair} lê só os campos necessários do save (streaming).
 */
public record ResumoSave(String clubeId, String clube, String cor1, String cor2, String escudo,
                         String tecnico, LocalDate data, int temporada, String tecnicos) {

    /** Save de um técnico só (sem a lista do modo multi-treinador). */
    public ResumoSave(String clubeId, String clube, String cor1, String cor2, String escudo, String tecnico, LocalDate data, int temporada) {
        this(clubeId, clube, cor1, cor2, escudo, tecnico, data, temporada, null);
    }

    /** Jogo com vários técnicos (multi-treinador)? */
    public boolean multi() { return tecnicos != null && !tecnicos.isBlank(); }

    public static final String EXTENSAO = ".futebas.resumo";

    public static Path arquivo(Path save) {
        String n = save.getFileName().toString();
        String ext = br.futebas.mundo.Salvamento.EXTENSAO;
        if (n.endsWith(ext)) n = n.substring(0, n.length() - ext.length());
        return save.resolveSibling(n + EXTENSAO);
    }

    /** Monta o resumo do jogo aberto. Chamar com o mundo travado (ou na thread do motor). */
    public static ResumoSave de(Jogo jogo) {
        Mundo m = jogo.mundo();
        String id = null, nome = null, c1 = null, c2 = null, esc = null, tec = null;
        if (m.tecnico != null) {
            tec = m.tecnico.nome;
            Clube c = m.clubeHumano();
            if (c != null) {
                id = c.id;
                nome = c.nome;
                c1 = c.cor1;
                c2 = c.cor2;
            } else if (m.tecnico.kingsTimeId != null) {
                id = m.tecnico.kingsTimeId;
                var tk = jogo.meuTimeKings(); // também os times customizados (fora do banco)
                if (tk != null) {
                    nome = tk.nome;
                    c1 = tk.cor1;
                    c2 = tk.cor2;
                }
            }
        }
        if (id != null && br.futebas.ui.Sessao.bancoCarregado()) {
            var db = br.futebas.ui.Sessao.banco();
            var dc = db.clubes.get(id);
            if (dc != null) esc = dc.escudoPng();
            var k = db.kingsTimes.get(id);
            if (k != null) {
                esc = k.escudoPng();
                if (nome == null) nome = k.nome();
                if (c1 == null) c1 = k.cor1();
                if (c2 == null) c2 = k.cor2();
            }
        }
        String todos = null;
        if (m.multi()) {
            // multi-treinador: o cartão mostra o técnico da vez (clube dele) e a lista de todos
            StringBuilder sb = new StringBuilder();
            for (br.futebas.mundo.Tecnico t : m.tecnicos) sb.append(sb.length() == 0 ? "" : ", ").append(t.nome);
            todos = sb.toString();
        }
        return new ResumoSave(id, nome, c1, c2, esc, tec, Calendario.inicio(m.semanaAbsoluta()), m.ano, todos);
    }

    public void gravar(Path save) {
        Properties p = new Properties();
        put(p, "clubeId", clubeId);
        put(p, "clube", clube);
        put(p, "cor1", cor1);
        put(p, "cor2", cor2);
        put(p, "escudo", escudo);
        put(p, "tecnico", tecnico);
        put(p, "data", data == null ? null : data.toString());
        put(p, "tecnicos", tecnicos);
        p.setProperty("temporada", String.valueOf(temporada));
        try (Writer w = Files.newBufferedWriter(arquivo(save), StandardCharsets.UTF_8)) {
            p.store(w, null);
        } catch (IOException e) {
            // sem resumo: a tela de início extrai do save
        }
    }

    private static void put(Properties p, String k, String v) {
        if (v != null) p.setProperty(k, v);
    }

    /** Resumo gravado ao lado do save (null se não existir ou estiver mais velho que o save). */
    public static ResumoSave ler(Path save) {
        Path a = arquivo(save);
        try {
            if (!Files.isRegularFile(a)) return null;
            if (Files.isRegularFile(save) && Files.getLastModifiedTime(a).toMillis() + 5_000 < Files.getLastModifiedTime(save).toMillis()) return null;
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(a, StandardCharsets.UTF_8)) {
                p.load(r);
            }
            String d = p.getProperty("data");
            return new ResumoSave(p.getProperty("clubeId"), p.getProperty("clube"), p.getProperty("cor1"), p.getProperty("cor2"),
                    p.getProperty("escudo"), p.getProperty("tecnico"), d == null ? null : LocalDate.parse(d),
                    Integer.parseInt(p.getProperty("temporada", "0")), p.getProperty("tecnicos"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Lê do próprio save (gzip + JSON, em streaming, sem montar o mundo) os campos do resumo e grava
     * o arquivo de resumo. Lento em saves grandes (lê o arquivo todo): chamar fora da thread da tela.
     */
    public static ResumoSave extrair(Path save) {
        int ano = 0, semana = 1;
        Map<String, String[]> clubes = new HashMap<>();
        String tecNome = null, tecClube = null, tecKings = null;
        List<String> nomes = new ArrayList<>();
        try (JsonReader r = new JsonReader(new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(save), 1 << 16), StandardCharsets.UTF_8)))) {
            r.setLenient(true);
            r.beginObject();
            while (r.hasNext()) {
                String k = r.nextName();
                switch (k) {
                    case "ano" -> ano = r.nextInt();
                    case "semana" -> semana = r.nextInt();
                    case "clubes" -> {
                        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); break; }
                        r.beginObject();
                        while (r.hasNext()) {
                            String id = r.nextName();
                            String[] v = new String[3];
                            if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue; }
                            r.beginObject();
                            while (r.hasNext()) {
                                String c = r.nextName();
                                int i = switch (c) { case "nome" -> 0; case "cor1" -> 1; case "cor2" -> 2; default -> -1; };
                                if (i >= 0 && r.peek() == JsonToken.STRING) v[i] = r.nextString();
                                else r.skipValue();
                            }
                            r.endObject();
                            clubes.put(id, v);
                        }
                        r.endObject();
                    }
                    case "tecnico" -> {
                        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); break; }
                        r.beginObject();
                        while (r.hasNext()) {
                            String c = r.nextName();
                            if (r.peek() != JsonToken.STRING) { r.skipValue(); continue; }
                            switch (c) {
                                case "nome" -> tecNome = r.nextString();
                                case "clubeId" -> tecClube = r.nextString();
                                case "kingsTimeId" -> tecKings = r.nextString();
                                default -> r.skipValue();
                            }
                        }
                        r.endObject();
                    }
                    case "tecnicos" -> {
                        // multi-treinador: os nomes de todos os técnicos
                        if (r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); break; }
                        r.beginArray();
                        while (r.hasNext()) {
                            if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue; }
                            r.beginObject();
                            while (r.hasNext()) {
                                String c = r.nextName();
                                if ("nome".equals(c) && r.peek() == JsonToken.STRING) nomes.add(r.nextString());
                                else r.skipValue();
                            }
                            r.endObject();
                        }
                        r.endArray();
                    }
                    default -> r.skipValue();
                }
            }
        } catch (Exception e) {
            return null;
        }
        if (ano == 0) return null;
        String id = tecClube != null ? tecClube : tecKings;
        String[] c = tecClube != null ? clubes.get(tecClube) : null;
        ResumoSave rs = new ResumoSave(id, c != null ? c[0] : null, c != null ? c[1] : null, c != null ? c[2] : null, null, tecNome,
                Calendario.inicio(Calendario.absoluta(ano, semana)), ano, nomes.size() > 1 ? String.join(", ", nomes) : null);
        rs.gravar(save);
        return rs;
    }
}
