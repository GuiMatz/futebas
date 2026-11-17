package br.futebas.ui.telas;

import br.futebas.competicao.FaseExec;
import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.dados.Formato;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Ui;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zonas coloridas de uma tabela de pontos, tiradas das regras da competição naquela temporada:
 * vagas continentais (1º nível, fase prévia, 2º e 3º níveis), acesso (verde), classificação para a
 * fase seguinte (azul), playoff (âmbar) e rebaixamento (vermelho). Cada posição recebe uma classe de
 * estilo e a legenda diz o que acontece ("▲ Sobe para a Série A2", "Classificados para as quartas
 * de final", "▼ Rebaixados para a Série A3").
 *
 * <p>Requisito (12/11/2026): nos estaduais não ficava claro quem sobe e quem cai. Os formatos
 * dos estaduais têm fase de liga seguida de mata-mata (antes a tabela só ganhava zonas com uma ou
 * duas fases) e muitos não trazem {@code promovidos}/{@code rebaixados}; o motor usa 2 nesses casos
 * na pirâmide do estado ({@code MontadorTemporada.piramide}), e as zonas seguem o motor.
 */
final class Zonas {
    /** Uma zona: classe CSS ("zona-c1"...) e o texto da legenda. */
    record Zona(String classe, String rotulo) {}

    final Map<Integer, Zona> porPosicao = new TreeMap<>();
    final Map<String, String> legenda = new LinkedHashMap<>();
    /** Regras sem cor na tabela (decididas em outra fase): "▲ 2 sobem para a Série A1 (os finalistas)". */
    final List<String> notas = new ArrayList<>();

    private Zonas() {}

    boolean vazia() { return porPosicao.isEmpty() && notas.isEmpty(); }

    /** Zonas da fase {@code f} (fase de pontos; mata-mata não tem zona). {@code n} = clubes na tabela. */
    static Zonas de(Torneio t, FaseExec f, int n) {
        Zonas z = new Zonas();
        if (t == null || n == 0 || (f != null && f.mataMata())) return z;
        Formato fo = t.formato;
        boolean grupos = f != null && f.numGrupos > 1;
        int idx = f == null ? 0 : f.indice;
        FaseExec prox = idx + 1 < t.fases.size() ? t.fases.get(idx + 1) : null;
        // a tabela desta fase decide o fim (vagas, acesso direto) se não há fase de pontos/mata-mata depois
        boolean decide = prox == null;
        boolean unicaLiga = !grupos && t.fases.size() <= 2;
        // vagas continentais
        if (fo != null && fo.vagas != null && unicaLiga) {
            for (Formato.Vaga v : fo.vagas) {
                if (v.para == null || v.posicoes == null || v.posicoes.isEmpty()) continue;
                int nivel = nivel(v.para);
                if (nivel == 0) continue;
                String nome = nomeCompeticao(v.para);
                List<Integer> ps = v.posicoes;
                // Libertadores com 5+ vagas: a última vai à fase prévia (modelo da CONMEBOL)
                boolean previa = nivel == 1 && v.para.contains("libertadores") && ps.size() >= 5;
                for (int i = 0; i < ps.size(); i++) {
                    int p = ps.get(i);
                    if (p < 1 || p > n || z.porPosicao.containsKey(p)) continue;
                    boolean ultimaPrevia = previa && i == ps.size() - 1 && ps.size() > 1;
                    String classe = ultimaPrevia ? "zona-c1p" : "zona-c" + nivel;
                    String rot = ultimaPrevia ? "Pré-" + nome : nome;
                    z.porPosicao.put(p, new Zona(classe, rot));
                    z.legenda.putIfAbsent(classe, rot);
                }
            }
        }
        // Copa Cóndor (3ª da América do Sul, fictícia): as vagas são do motor (CompeticoesFicticias), não do
        // formato. Ficam logo abaixo das vagas da Libertadores/Sul-Americana na 1ª divisão (requisito 13/11)
        Integer vagasCondor = t.pais == null ? null : br.futebas.competicao.CompeticoesFicticias.VAGAS_CONDOR.get(t.pais);
        if (vagasCondor != null && unicaLiga && "nacional".equals(t.ambito) && t.divisao != null && t.divisao == 1) {
            int ult = 0;
            for (int p : z.porPosicao.keySet()) ult = Math.max(ult, p);
            if (ult > 0) {
                for (int p = ult + 1; p <= Math.min(n, ult + vagasCondor); p++) z.porPosicao.putIfAbsent(p, new Zona("zona-c3", "Copa Cóndor"));
                z.legenda.putIfAbsent("zona-c3", "Copa Cóndor");
            }
        }
        Niveis niv = niveis(t);
        int prom = niv.prom, queda = niv.queda;
        String sobe = niv.cima == null ? "Acesso" : "▲ Sobe para " + comArtigo(niv.cima);
        String cai = niv.baixo == null ? "▼ Rebaixados" : "▼ Rebaixados para " + comArtigo(niv.baixo);
        boolean playoffAcesso = false;
        String faseRebaixamento = null;
        if (fo != null && fo.fases != null) for (Formato.Fase fa : fo.fases) {
            String nf = fa.nome == null ? "" : fa.nome.toLowerCase(Locale.ROOT);
            if (nf.contains("playoff") && (nf.contains("acesso") || prom > 0)) playoffAcesso = true;
            if (nf.contains("rebaixa")) faseRebaixamento = fa.nome;
        }
        // acesso
        if (prom > 0 && !grupos) {
            if (decide) {
                for (int p = 1; p <= Math.min(n, prom); p++) z.porPosicao.putIfAbsent(p, new Zona("zona-acesso", sobe));
                z.legenda.putIfAbsent("zona-acesso", sobe);
            } else if (playoffAcesso && t.fases.size() == 2) {
                // promovidos diretos + os que vão ao playoff de acesso (a próxima fase leva os seguintes)
                int direto = Math.max(1, prom / 2);
                for (int p = 1; p <= Math.min(n, direto); p++) z.porPosicao.putIfAbsent(p, new Zona("zona-acesso", sobe));
                for (int p = direto + 1; p <= Math.min(n, direto + 4); p++) z.porPosicao.putIfAbsent(p, new Zona("zona-playoff", "Playoff de acesso"));
                z.legenda.putIfAbsent("zona-acesso", sobe);
                z.legenda.putIfAbsent("zona-playoff", "Playoff de acesso");
            }
        }
        // classificação para a fase seguinte
        if (prox != null && !grupos && f != null) {
            int sai = f.saida > 0 ? f.saida : prox.entrantes;
            if (sai > 0 && sai < n) {
                boolean playoff = prox.nome != null && prox.nome.toLowerCase(Locale.ROOT).contains("playoff");
                String classe = playoff ? "zona-playoff" : "zona-classifica";
                String rot = "Classificados para " + faseComArtigo(prox, sai);
                for (int p = 1; p <= Math.min(n, sai); p++) z.porPosicao.putIfAbsent(p, new Zona(classe, rot));
                z.legenda.putIfAbsent(classe, rot);
            }
        }
        if (prom > 0 && !decide && !(playoffAcesso && t.fases.size() == 2))
            z.notas.add((prom == 1 ? "▲ 1 sobe" : "▲ " + prom + " sobem") + (niv.cima == null ? "" : " para " + comArtigo(niv.cima))
                    + (prom == 2 ? " (os finalistas)" : prom == 1 ? " (o campeão)" : " (pela classificação final)"));
        // rebaixamento
        if (queda > 0) {
            if (faseRebaixamento != null || grupos) {
                z.notas.add("▼ " + queda + (queda == 1 ? " cai" : " caem") + (niv.baixo == null ? "" : " para " + comArtigo(niv.baixo))
                        + (faseRebaixamento != null ? " (" + faseRebaixamento.replaceAll("\\s*\\(.*\\)", "") + ")" : " (piores campanhas)"));
            } else {
                for (int p = Math.max(1, n - queda + 1); p <= n; p++) z.porPosicao.put(p, new Zona("zona-queda", cai));
                z.legenda.put("zona-queda", cai);
            }
        }
        return z;
    }

    /** Pirâmide da competição: quantos sobem/caem e as divisões de cima e de baixo. */
    record Niveis(int prom, int queda, Db.Competicao cima, Db.Competicao baixo) {}

    private static final Pattern NIVEL_ID = Pattern.compile("^(est-[a-z]{2})-(\\d)$");

    static int nivelDe(String id, Integer divisao) {
        if (divisao != null && divisao >= 1) return divisao;
        Matcher mt = NIVEL_ID.matcher(id == null ? "" : id);
        return mt.find() ? Math.max(1, Integer.parseInt(mt.group(2))) : 1;
    }

    /**
     * Quantos sobem e caem, como no motor: o {@code promovidos}/{@code rebaixados} do formato; nas
     * pirâmides (nacional ou estadual) sem o número no formato, 2 (o padrão de
     * {@code MontadorTemporada.piramide}). A divisão de cima não tem acesso; a última só tem queda se
     * o formato disser.
     */
    static Niveis niveis(Torneio t) {
        Formato fo = t.formato;
        Integer fp = fo == null ? null : fo.promovidos, fr = fo == null ? null : fo.rebaixados;
        boolean piramide = "nacional".equals(t.ambito) || "estadual".equals(t.ambito);
        Db.Competicao cima = null, baixo = null;
        int nivel = nivelDe(t.id, t.divisao);
        BancoMundo db = Sessao.bancoCarregado() ? Sessao.banco() : null;
        if (piramide && db != null) {
            int nc = Integer.MIN_VALUE, nb = Integer.MAX_VALUE;
            for (Db.Competicao c : db.competicoes.values()) {
                if (!t.ambito.equals(c.ambito()) || c.id().equals(t.id)) continue;
                if (t.pais != null && c.pais() != null && !t.pais.equals(c.pais())) continue;
                if ("estadual".equals(t.ambito) && (t.uf == null || !t.uf.equals(c.uf()))) continue;
                if ("nacional".equals(t.ambito) && t.pais == null) continue;
                int nv = nivelDe(c.id(), c.divisao());
                if (nv < nivel && nv > nc) { nc = nv; cima = c; }
                if (nv > nivel && nv < nb) { nb = nv; baixo = c; }
            }
        }
        int prom, queda;
        if (piramide && db != null) {
            prom = cima == null ? 0 : fp != null ? fp : 2;
            queda = baixo == null ? (fr != null ? fr : 0) : fr != null ? fr : 2;
        } else {
            prom = t.divisao != null && t.divisao > 1 && fp != null ? fp : 0;
            queda = fr != null ? fr : 0;
        }
        return new Niveis(prom, queda, cima, baixo);
    }

    /** "a Série A2", "o Módulo I", "a Segunda Divisão"; nome curto sem o "Campeonato Paulista". */
    static String comArtigo(Db.Competicao c) {
        String n = br.futebas.ui.modelo.Textos.curto(c.nome());
        for (String chave : List.of("Série ", "Módulo ", "Segunda Divisão", "Terceira Divisão", "Primeira Divisão", "Divisão de Acesso")) {
            int i = n.indexOf(chave);
            if (i > 0) {
                n = n.substring(i);
                break;
            }
        }
        String s = n.toLowerCase(Locale.ROOT);
        if (s.startsWith("módulo") || s.startsWith("campeonato") || s.startsWith("brasileirão")) return "o " + n;
        if (s.startsWith("série") || s.startsWith("segunda") || s.startsWith("terceira") || s.startsWith("primeira") || s.startsWith("divisão")
                || s.startsWith("liga") || s.startsWith("copa")) return "a " + n;
        return n;
    }

    /** "as quartas de final", "as semifinais", "a final", "a 2ª fase". */
    static String faseComArtigo(FaseExec prox, int sai) {
        String n = prox.nome;
        if (n == null || n.isBlank()) n = sai == 8 ? "Quartas de final" : sai == 4 ? "Semifinais" : sai == 2 ? "Final" : sai == 16 ? "Oitavas de final" : "próxima fase";
        String s = n.toLowerCase(Locale.ROOT);
        if (s.startsWith("quartas") || s.startsWith("oitavas") || s.startsWith("semifinais") || s.startsWith("finais")) return "as " + lower1(n);
        if (s.startsWith("semifinal") || s.startsWith("final") || s.startsWith("fase") || s.startsWith("segunda") || s.startsWith("próxima")
                || s.startsWith("taça") || s.startsWith("liguilla")) return "a " + lower1(n);
        if (s.startsWith("playoff") || s.startsWith("hexagonal") || s.startsWith("quadrangular") || s.startsWith("octogonal")
                || s.startsWith("triangular") || s.startsWith("mata") || s.startsWith("grupo")) return "o " + lower1(n);
        return n;
    }

    private static String lower1(String n) {
        if (n.length() < 2 || Character.isUpperCase(n.charAt(1))) return n;
        return Character.toLowerCase(n.charAt(0)) + n.substring(1);
    }

    /** 1 = principal do continente, 2 = segunda, 3 = terceira; 0 = não é vaga continental. */
    static int nivel(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        if (s.contains("conference")) return 3;
        if (s.contains("sul-americana") || s.contains("sudamericana") || s.contains("europa") || s.contains("champions-league-two")
                || s.contains("confed") || s.contains("afc-cup") || s.contains("leagues-cup") || s.contains("afc-champions-2")) return 2;
        if (s.contains("libertadores") || s.contains("champions") || s.contains("concacaf")) return 1;
        return 0;
    }

    static String nomeCompeticao(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        if (s.contains("libertadores")) return "Libertadores";
        if (s.contains("sul-americana") || s.contains("sudamericana")) return "Sul-Americana";
        if (s.contains("uefa-champions")) return "Champions League";
        if (s.contains("uefa-europa")) return "Europa League";
        if (s.contains("conference")) return "Conference League";
        if (s.contains("afc-champions-league-two") || s.contains("afc-champions-2")) return "AFC Champions League Two";
        if (s.contains("afc-champions")) return "AFC Champions League";
        if (s.contains("caf-champions") || s.contains("caf-liga")) return "Liga dos Campeões da CAF";
        if (s.contains("confed")) return "Copa das Confederações";
        if (s.contains("concacaf")) return "Concacaf Champions Cup";
        return id;
    }

    /** Legenda com as cores e os textos (e, embaixo, as regras decididas em outra fase). */
    javafx.scene.layout.VBox legenda() {
        FlowPane h = new FlowPane(16, 6);
        legenda.forEach((classe, rot) -> {
            Label l = Ui.rotulo(" ", "zona", classe);
            l.setMinSize(14, 14);
            l.setPrefSize(14, 14);
            h.getChildren().add(Ui.linha(6, l, Ui.rotulo(rot, "texto-suave")));
        });
        javafx.scene.layout.VBox v = new javafx.scene.layout.VBox(4);
        v.getStyleClass().add("legenda-zonas");
        if (!h.getChildren().isEmpty()) v.getChildren().add(h);
        for (String n : notas) v.getChildren().add(Ui.rotulo(n, "texto-suave", n.startsWith("▼") ? "nota-queda" : "nota-acesso"));
        return v;
    }

    /** Legenda curta das mini tabelas: cores com textos curtos, quebrando linha se precisar. */
    FlowPane legendaCompacta() {
        FlowPane h = new FlowPane(10, 4);
        legenda.forEach((classe, rot) -> {
            Label l = Ui.rotulo(" ", "zona", classe);
            l.setMinSize(10, 10);
            l.setPrefSize(10, 10);
            l.setMaxSize(10, 10);
            String curto = classe.equals("zona-acesso") ? "Sobe" : classe.equals("zona-queda") ? "Cai" : classe.equals("zona-classifica") ? "Classifica"
                    : classe.equals("zona-playoff") ? "Playoff" : rot;
            Label t = Ui.rotulo(curto, "texto-pequeno");
            t.setMinWidth(Region.USE_PREF_SIZE);
            javafx.scene.control.Tooltip.install(t, new javafx.scene.control.Tooltip(rot));
            HBox item = Ui.linha(4, l, t);
            item.setMinWidth(Region.USE_PREF_SIZE);
            h.getChildren().add(item);
        });
        return h;
    }
}
