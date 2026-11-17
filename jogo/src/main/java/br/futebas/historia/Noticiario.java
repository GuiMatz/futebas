package br.futebas.historia;

import br.futebas.competicao.Confronto;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.financas.Financas;
import br.futebas.mundo.Artigos;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Rng;
import br.futebas.partida.ResultadoPartida;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Noticiário do jogo: manchetes com foco no técnico (o clube dele primeiro, depois as competições
 * em que ele está e a liga/o país dele; do resto do mundo, só os grandes acontecimentos).
 * <p>
 * Os ganchos do motor (resultado, lesão, transferência, técnicos, títulos, aposentadorias, Bola de
 * Ouro) põem notícias candidatas em {@link Historia#noticiasPendentes}; no fim da semana
 * {@link #fecharSemana} acrescenta as da tabela, dos técnicos na corda bamba e dos mata-matas e
 * publica as melhores (no máximo {@link #POR_SEMANA}, sem repetir). A variação dos textos sai de um
 * gerador derivado da semente do mundo e da chave da notícia: é determinística e não consome o
 * gerador da simulação.
 */
public final class Noticiario {
    /** Notícias publicadas por semana (no máximo). */
    public static final int POR_SEMANA = 8;
    /** Por tipo, por semana (o seu time pode ter mais). */
    public static final int POR_TIPO = 3, POR_TIPO_MEU_TIME = 4, MUNDO_POR_SEMANA = 2;
    /** Quantas ficam guardadas no save. */
    public static final int GUARDADAS = 400;

    private final Mundo m;

    // cache da semana (competições do técnico)
    private int cacheSa = Integer.MIN_VALUE;
    private String cacheClube;
    private final Set<String> minhasComps = new HashSet<>();
    private final Set<String> clubesDaLiga = new HashSet<>();
    private Torneio minhaLiga;

    public Noticiario(Mundo m) {
        this.m = m;
    }

    // =================================================================== foco
    private Clube humano() {
        return m.clubeHumano();
    }

    private void cache() {
        Clube h = humano();
        String id = h == null ? null : h.id;
        int sa = m.semanaAbsoluta();
        if (sa == cacheSa && java.util.Objects.equals(id, cacheClube)) return;
        cacheSa = sa;
        cacheClube = id;
        minhasComps.clear();
        clubesDaLiga.clear();
        minhaLiga = null;
        if (h == null) return;
        for (Torneio t : m.torneios) {
            if ("amistoso".equals(t.ambito) || "kings".equals(t.ambito) || !t.participantes.contains(h.id)) continue;
            minhasComps.add(t.id);
            if ("nacional".equals(t.ambito) && t.divisao != null && (minhaLiga == null || t.ano > minhaLiga.ano)) minhaLiga = t;
        }
        if (minhaLiga != null) clubesDaLiga.addAll(minhaLiga.participantes);
    }

    /** A competição é uma das do técnico nesta temporada? */
    public boolean minhaCompeticao(String competicaoId) {
        cache();
        return competicaoId != null && minhasComps.contains(competicaoId);
    }

    /** Liga nacional do clube do técnico (null sem clube). */
    public Torneio minhaLiga() {
        cache();
        return minhaLiga;
    }

    /**
     * Foco de uma notícia: 0 = envolve o clube do técnico, 1 = uma competição dele ou um clube da
     * liga dele, 2 = um clube do país dele (até uma divisão abaixo), 3 = resto do mundo.
     */
    public int foco(String competicaoId, String... clubes) {
        Clube h = humano();
        if (h == null) return 3;
        for (String c : clubes) if (h.id.equals(c)) return 0;
        cache();
        if (competicaoId != null && minhasComps.contains(competicaoId)) return 1;
        for (String c : clubes) if (c != null && clubesDaLiga.contains(c)) return 1;
        for (String c : clubes) {
            Clube x = c == null ? null : m.clube(c);
            if (x != null && h.pais.equals(x.pais) && x.divisao > 0 && x.divisao <= Math.max(2, h.divisao + 1)) return 2;
        }
        return 3;
    }

    // =================================================================== candidatas e publicação
    private void nova(String tipo, int foco, int peso, String chave, String titulo, String texto, String clubeId, String competicaoId) {
        if (titulo == null) return;
        Historia.Noticia n = new Historia.Noticia();
        n.ano = m.ano;
        n.semana = m.semana;
        n.tipo = tipo;
        n.foco = foco;
        n.peso = Math.max(0, Math.min(100, peso));
        n.chave = chave;
        n.titulo = titulo;
        n.texto = texto;
        n.clubeId = clubeId;
        n.competicaoId = competicaoId;
        m.historia.noticiasPendentes.add(n);
    }

    /** Fim da semana: notícias da tabela, da corda bamba e dos mata-matas; publica as melhores. */
    public void fecharSemana(int sa) {
        Clube h = humano();
        if (h != null) {
            cache();
            tabela(h);
            cordaBamba(h);
            mataMatas(h, sa);
            artilharia(h);
        }
        publicar();
        if ((m.semana & 7) == 0) podarPautas(sa);
    }

    /** Escolhe as notícias da semana: foco e importância, sem repetir, no máximo {@link #POR_SEMANA}. */
    void publicar() {
        List<Historia.Noticia> p = m.historia.noticiasPendentes;
        if (p.isEmpty()) return;
        List<Historia.Noticia> ordem = new ArrayList<>(p);
        ordem.sort(Comparator.comparingInt((Historia.Noticia n) -> n.foco).thenComparingInt(n -> -n.peso));
        Set<String> chaves = new HashSet<>();
        List<Historia.Noticia> pub = m.historia.noticias;
        for (int i = Math.max(0, pub.size() - 80); i < pub.size(); i++) if (pub.get(i).chave != null) chaves.add(pub.get(i).chave);
        Set<String> titulos = new HashSet<>();
        Map<String, Integer> porTipo = new HashMap<>();
        int total = 0, mundo = 0;
        for (Historia.Noticia n : ordem) {
            if (total >= POR_SEMANA) break;
            if (n.chave != null && !chaves.add(n.chave)) continue;
            if (!titulos.add(n.titulo)) continue;
            int lim = "meu_time".equals(n.tipo) ? POR_TIPO_MEU_TIME : POR_TIPO;
            int k = porTipo.getOrDefault(n.tipo, 0);
            if (k >= lim) continue;
            if (n.foco >= 3 && mundo >= MUNDO_POR_SEMANA) continue;
            porTipo.put(n.tipo, k + 1);
            if (n.foco >= 3) mundo++;
            pub.add(n);
            total++;
        }
        p.clear();
        if (pub.size() > GUARDADAS) pub.subList(0, pub.size() - GUARDADAS).clear();
    }

    /** As {@code limite} notícias mais recentes (semana mais nova primeiro; na semana, pelo foco e importância). */
    public static List<Historia.Noticia> ultimas(Historia h, int limite) {
        List<Historia.Noticia> l = new ArrayList<>(h.noticias);
        l.sort(Comparator.comparingInt((Historia.Noticia n) -> -(n.ano * 100 + n.semana)).thenComparingInt(n -> n.foco).thenComparingInt(n -> -n.peso));
        return l.size() > limite ? new ArrayList<>(l.subList(0, limite)) : l;
    }

    private void podarPautas(int sa) {
        // marcos com semana absoluta guardada: some depois de 2 anos
        m.historia.pautas.entrySet().removeIf(e -> e.getKey().startsWith("@") && e.getValue() < sa - 104);
    }

    private Integer pauta(String k) {
        return m.historia.pautas.get(k);
    }

    private void pauta(String k, int v) {
        m.historia.pautas.put(k, v);
    }

    // =================================================================== textos
    /** Uma das opções, escolhida pela semente do mundo e pela chave (determinístico, não mexe no gerador do jogo). */
    String escolher(String chave, String... opcoes) {
        long h = m.semente * 0x9E3779B97F4A7C15L ^ (long) chave.hashCode() * 0xBF58476D1CE4E5B9L ^ (m.ano * 53L + m.semana);
        return opcoes[new Rng(h).inteiro(opcoes.length)];
    }

    private static final Set<String> CLUBES_FEMININOS = Set.of("juventus", "fiorentina", "roma", "lazio", "atalanta", "sampdoria", "udinese",
            "salernitana", "cremonese", "spezia", "reggina", "portuguesa", "ponte", "chapecoense", "lusa", "real sociedad", "internazionale", "inter de milao",
            "tuna", "francana", "ferroviaria", "desportiva", "caldense", "anapolina", "democrata", "real sociedade");

    private static String sem(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    static boolean clubeFeminino(String nome) {
        if (nome == null) return false;
        String n = sem(nome.trim());
        for (String f : CLUBES_FEMININOS) if (n.equals(f) || n.startsWith(f + " ")) return true;
        return false;
    }

    /** Preenche os marcadores: {A}/{B} (clubes, com {oA} {doA} {aoA} {noA} {peloA}), {C} (competição, idem), e os livres. */
    static String preencher(String tpl, Map<String, String> v) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < tpl.length()) {
            char ch = tpl.charAt(i);
            if (ch == '{') {
                int f = tpl.indexOf('}', i);
                String k = tpl.substring(i + 1, f);
                String val = v.get(k);
                sb.append(val == null ? "" : val);
                i = f + 1;
            } else {
                sb.append(ch);
                i++;
            }
        }
        return sb.toString().replaceAll(" {2,}", " ").replace(" :", ":").trim();
    }

    private static void clube(Map<String, String> v, String k, String nome) {
        if (nome == null) return;
        boolean fem = clubeFeminino(nome);
        v.put(k, nome);
        v.put("o" + k, (fem ? "a " : "o ") + nome);
        v.put("do" + k, (fem ? "da " : "do ") + nome);
        v.put("ao" + k, (fem ? "à " : "ao ") + nome);
        v.put("no" + k, (fem ? "na " : "no ") + nome);
        v.put("pelo" + k, (fem ? "pela " : "pelo ") + nome);
    }

    private static void comp(Map<String, String> v, String nome) {
        if (nome == null) return;
        boolean fem = Artigos.feminina(nome);
        v.put("C", nome);
        v.put("oC", (fem ? "a " : "o ") + nome);
        v.put("doC", (fem ? "da " : "do ") + nome);
        v.put("noC", (fem ? "na " : "no ") + nome);
        v.put("peloC", (fem ? "pela " : "pelo ") + nome);
        v.put("aoC", (fem ? "à " : "ao ") + nome);
    }

    private static Map<String, String> vars(String a, String b, String c, String... livres) {
        Map<String, String> v = new LinkedHashMap<>();
        clube(v, "A", a);
        clube(v, "B", b);
        comp(v, c);
        for (int i = 0; i + 1 < livres.length; i += 2) v.put(livres[i], livres[i + 1]);
        return v;
    }

    private String texto(String chave, Map<String, String> v, String... opcoes) {
        return preencher(escolher(chave, opcoes), v);
    }

    private static String nomeComp(Torneio t) {
        return t == null ? null : t.nome;
    }

    private static String placar(Clube cm, Clube cv, ResultadoPartida r) {
        String s = cm.nome + " " + r.golsMandante + " x " + r.golsVisitante + " " + cv.nome;
        if (r.penaltisMandante != null) s += " (" + r.penaltisMandante + " x " + r.penaltisVisitante + " nos pênaltis)";
        return s;
    }

    private static String extenso(int n) {
        return switch (n) {
            case 2 -> "dois";
            case 3 -> "três";
            case 4 -> "quatro";
            case 5 -> "cinco";
            case 6 -> "seis";
            case 7 -> "sete";
            case 8 -> "oito";
            case 9 -> "nove";
            case 10 -> "dez";
            default -> String.valueOf(n);
        };
    }

    // =================================================================== partida
    /** Depois de cada partida (todas): o jogo do técnico, zebras e goleadas nas competições dele. */
    public void partida(Torneio t, Partida p, ResultadoPartida r, Clube cm, Clube cv, double fm, double fv) {
        if ("amistoso".equals(t.ambito) || "kings".equals(t.ambito) || cm.virtual || cv.virtual) return;
        Clube h = humano();
        if (h == null) return;
        if (cm == h || cv == h) {
            meuJogo(t, p, r, cm, cv, cm == h ? 0 : 1, cm == h ? fm : fv, cm == h ? fv : fm);
            return;
        }
        if (!minhaCompeticao(t.id)) return;
        int dif = r.golsMandante - r.golsVisitante;
        int venc = r.vencedorLado();
        Clube w = venc == 0 ? cm : venc == 1 ? cv : null, l = venc == 0 ? cv : venc == 1 ? cm : null;
        double fw = venc == 0 ? fm : fv, fl = venc == 0 ? fv : fm;
        String ch = t.id + "#" + t.ano + "#" + p.id;
        // no mata-mata a zebra é a eliminação (fim da semana), não o jogo de ida
        if (w != null && p.rodadaMata < 0 && fl - fw >= 10 && l.reputacao >= 55 && r.penaltisMandante == null) {
            int gap = (int) Math.round(fl - fw);
            Map<String, String> v = vars(w.nome, l.nome, nomeComp(t));
            nova("zebra", 1, 45 + gap * 2, "zebra#" + ch, texto("zebra#" + ch, v,
                    "Zebra {noC}: {A} derruba {oB}",
                    "{A} surpreende e vence {oB}",
                    "Deu zebra: {oB} perde para {oA}",
                    "Tropeço inesperado: {B} cai diante {doA}",
                    "{A} faz história e bate {oB}"), placar(cm, cv, r), w.id, t.id);
        }
        goleadaRecorde(t, p, r, cm, cv, Math.abs(dif), false);
    }

    private void goleadaRecorde(Torneio t, Partida p, ResultadoPartida r, Clube cm, Clube cv, int margem, boolean meu) {
        if (margem < 4) return;
        String k = "goleada#" + t.id + "#" + t.ano;
        Integer antes = pauta(k);
        if (antes != null && margem <= antes) return;
        pauta(k, margem);
        if (antes == null && margem < 5) return; // a primeira goleada do campeonato só vira recorde a partir de 5
        Clube w = r.golsMandante > r.golsVisitante ? cm : cv, l = w == cm ? cv : cm;
        Map<String, String> v = vars(w.nome, l.nome, nomeComp(t), "P", Math.max(r.golsMandante, r.golsVisitante) + " x " + Math.min(r.golsMandante, r.golsVisitante));
        nova("recorde", meu ? 0 : 1, 55 + margem * 3, k + "#" + margem, texto(k + margem, v,
                "Maior goleada {doC}: {A} {P} {B}",
                "{A} aplica a maior goleada {doC} até aqui",
                "Recorde {noC}: {oA} atropela {oB} por {P}"), placar(cm, cv, r), w.id, t.id);
    }

    private void meuJogo(Torneio t, Partida p, ResultadoPartida r, Clube cm, Clube cv, int lado, double fh, double fa) {
        Clube h = lado == 0 ? cm : cv, adv = lado == 0 ? cv : cm;
        int pro = lado == 0 ? r.golsMandante : r.golsVisitante, contra = lado == 0 ? r.golsVisitante : r.golsMandante;
        int venc = r.vencedorLado();
        boolean ganhou = venc == lado, perdeu = venc == 1 - lado;
        boolean classico = h.rivais.contains(adv.id) || adv.rivais.contains(h.id);
        String ch = t.id + "#" + t.ano + "#" + p.id;
        String pl = placar(cm, cv, r);
        String linha = t.nome + " · " + pl;
        String P = pro + " x " + contra;
        Map<String, String> v = vars(h.nome, adv.nome, nomeComp(t), "P", P, "G", String.valueOf(pro), "S", String.valueOf(contra));
        // virada: esteve perdendo e ganhou
        boolean virada = false;
        if (ganhou && contra > 0) {
            List<int[]> gs = new ArrayList<>(r.gols);
            gs.sort(Comparator.comparingInt(g -> g[2]));
            int a = 0, b = 0;
            for (int[] g : gs) {
                if (g[0] == lado) a++;
                else b++;
                if (b > a) virada = true;
            }
        }
        int margem = pro - contra;
        if (ganhou && margem >= 3) {
            nova("meu_time", 0, 70 + margem * 3 + (classico ? 10 : 0), "jogo#" + ch, texto("gol#" + ch, v,
                    classico ? "Clássico é {doA}: goleada de {P} sobre {oB}" : "{A} atropela {oB}: {P}",
                    "Goleada {noC}: {A} {P} {B}",
                    "{A} passeia e faz {P} {noB}",
                    "Show {doA}: {P} sobre {oB}",
                    "Noite de gala: {A} goleia {oB} por {P}"), linha, h.id, t.id);
        } else if (perdeu && -margem >= 3) {
            nova("meu_time", 0, 72 - margem * 3 + (classico ? 10 : 0), "jogo#" + ch, texto("vex#" + ch, v,
                    classico ? "Vexame no clássico: {A} leva {S} {doB}" : "Vexame: {A} é goleado {peloB}",
                    "{A} sofre {S} gols e a torcida protesta",
                    "Noite para esquecer: {B} {S} x {G} {A}",
                    "Apagão {doA}: derrota por {S} x {G} para {oB}"), linha, h.id, t.id);
        } else if (virada) {
            nova("meu_time", 0, 66 + (classico ? 10 : 0), "jogo#" + ch, texto("vir#" + ch, v,
                    "Virada! {A} vence {oB} de virada",
                    "{A} busca a virada e bate {oB}",
                    "Que virada: {A} {P} {B}",
                    "De virada, {A} supera {oB} {noC}"), linha, h.id, t.id);
        } else if (classico) {
            String tpl = ganhou ? escolher("cla#" + ch, "{A} vence o clássico contra {oB}", "Clássico é {doA}: {P}", "Festa da torcida: {A} bate {oB} no clássico")
                    : perdeu ? escolher("cla#" + ch, "{A} perde o clássico para {oB}", "Clássico amargo: {B} vence {oA}", "Torcida na bronca após derrota no clássico")
                    : escolher("cla#" + ch, "Clássico termina empatado: {P}", "{A} e {B} ficam no empate no clássico", "Empate no clássico: {A} {P} {B}");
            nova("meu_time", 0, 68, "jogo#" + ch, preencher(tpl, v), linha, h.id, t.id);
        } else if (ganhou && fa - fh >= 8) {
            nova("meu_time", 0, 64, "jogo#" + ch, texto("her#" + ch, v,
                    "Zebra! {A} derruba {oB}",
                    "{A} surpreende {oB} e vence por {P}",
                    "Vitória histórica {doA} sobre {oB}"), linha, h.id, t.id);
        } else if (perdeu && fh - fa >= 8) {
            nova("meu_time", 0, 62, "jogo#" + ch, texto("tro#" + ch, v,
                    "Tropeço: {A} perde para {oB}",
                    "Zebra contra {oA}: derrota para {oB}",
                    "{A} decepciona e cai diante {doB}"), linha, h.id, t.id);
        } else {
            String tpl = ganhou ? escolher("res#" + ch, "{A} vence {oB} por {P}", "Vitória {doA}: {P} sobre {oB}", "{A} bate {oB} {noC}",
                    "Três pontos: {A} {P} {B}", "{A} supera {oB} e soma mais uma")
                    : perdeu ? escolher("res#" + ch, "{A} perde para {oB}", "Derrota {doA} diante {doB}", "{B} vence {oA} por {S} x {G}",
                    "{A} tropeça contra {oB}")
                    : escolher("res#" + ch, "{A} empata com {oB}", "Empate em {P} entre {A} e {B}", "{A} fica no {P} com {oB}", "Igualdade: {A} {P} {B}");
            if ("copa_nacional".equals(t.ambito) || "continental".equals(t.ambito)) {
                if (ganhou) tpl = escolher("cop#" + ch, "{A} vence {oB} {peloC}", "{A} bate {oB} e dá um passo {noC}", "Vitória {doA} {noC}: {P}");
            }
            nova("meu_time", 0, 40 + (ganhou ? 5 : 0), "jogo#" + ch, preencher(tpl, v), linha, h.id, t.id);
        }
        goleadaRecorde(t, p, r, cm, cv, Math.abs(margem), true);

        // hat-trick, artilheiro, estreia, expulsão
        Map<Integer, Integer> autores = new LinkedHashMap<>();
        for (int[] g : r.gols) if (g[0] == lado && g[4] == 0 && g[1] >= 0) autores.merge(g[1], 1, Integer::sum);
        for (Map.Entry<Integer, Integer> e : autores.entrySet()) {
            Jogador j = m.jogador(e.getKey());
            if (j == null || !h.id.equals(j.clubeId)) continue;
            Map<String, String> vj = vars(h.nome, adv.nome, nomeComp(t), "J", j.nomeExibicao(), "N", extenso(e.getValue()), "T", String.valueOf(j.golsTemp));
            if (e.getValue() >= 3) {
                nova("meu_time", 0, 66 + e.getValue() * 3, "hat#" + ch + "#" + j.id, texto("hat#" + ch + j.id, vj,
                        "{J} marca {N} vezes contra {oB}",
                        "Hat-trick de {J} {noC}",
                        "{J} brilha com {N} gols diante {doB}",
                        "Noite de {J}: {N} gols contra {oB}"), linha, h.id, t.id);
            }
            int gt = j.golsTemp, antes = gt - e.getValue();
            for (int marco : new int[]{10, 15, 20, 25, 30, 40, 50}) {
                if (antes < marco && gt >= marco) {
                    nova("meu_time", 0, 48 + marco / 2, "art#" + j.id + "#" + m.ano + "#" + marco, texto("art#" + j.id + marco, vj,
                            "{J} chega a {T} gols na temporada",
                            "Artilheiro {doA}, {J} alcança a marca de {T} gols",
                            "{J} não para: já são {T} gols {peloA}"), null, h.id, t.id);
                    break;
                }
            }
        }
        for (Map.Entry<Integer, Integer> e : r.minutos.entrySet()) {
            Integer chegada = pauta("@chegada#" + e.getKey());
            if (chegada == null) continue;
            Jogador j = m.jogador(e.getKey());
            m.historia.pautas.remove("@chegada#" + e.getKey());
            if (j == null || !h.id.equals(j.clubeId)) continue;
            int gols = autores.getOrDefault(j.id, 0);
            Map<String, String> vj = vars(h.nome, adv.nome, nomeComp(t), "J", j.nomeExibicao());
            String tpl = gols > 0 ? escolher("est#" + j.id, "{J} estreia com gol {peloA}", "Estreia com o pé direito: {J} marca na estreia", "{J} deixa o seu na estreia")
                    : escolher("est#" + j.id, "{J} faz a estreia {peloA}", "Reforço {doA}, {J} estreia contra {oB}", "{J} veste a camisa {doA} pela primeira vez");
            nova("meu_time", 0, gols > 0 ? 55 : 38, "est#" + j.id, preencher(tpl, vj), linha, h.id, t.id);
        }
        for (int[] x : r.vermelhos) {
            if (x[0] != lado || x[1] < 0) continue;
            Jogador j = m.jogador(x[1]);
            if (j == null) continue;
            Map<String, String> vj = vars(h.nome, adv.nome, nomeComp(t), "J", j.nomeExibicao());
            nova("meu_time", 0, 50, "exp#" + ch + "#" + j.id, texto("exp#" + ch + j.id, vj,
                    "{J} é expulso contra {oB}",
                    "Cartão vermelho: {J} desfalca {oA}",
                    "Expulsão de {J} complica {oA}"), linha, h.id, t.id);
        }
        sequencias(h, ganhou, perdeu, t);
    }

    /** Invencibilidade, vitórias seguidas e jejum (só o clube do técnico). */
    private void sequencias(Clube h, boolean ganhou, boolean perdeu, Torneio t) {
        String base = "seq#" + h.id + "#";
        int inv = pauta(base + "inv") == null ? 0 : pauta(base + "inv");
        int vit = pauta(base + "vit") == null ? 0 : pauta(base + "vit");
        int jej = pauta(base + "jej") == null ? 0 : pauta(base + "jej");
        int invAntes = inv, jejAntes = jej;
        inv = perdeu ? 0 : inv + 1;
        vit = ganhou ? vit + 1 : 0;
        jej = ganhou ? 0 : jej + 1;
        pauta(base + "inv", inv);
        pauta(base + "vit", vit);
        pauta(base + "jej", jej);
        String sa = String.valueOf(m.semanaAbsoluta());
        Map<String, String> v = vars(h.nome, null, nomeComp(t), "N", String.valueOf(inv), "V", String.valueOf(vit), "J", String.valueOf(jej));
        if (perdeu && invAntes >= 8) {
            v.put("N", String.valueOf(invAntes));
            nova("meu_time", 0, 58, "fiminv#" + h.id + "#" + sa, texto("fiminv#" + h.id + sa, v,
                    "Fim da invencibilidade: {A} perde depois de {N} jogos",
                    "Acabou a série: {A} cai após {N} jogos invicto"), null, h.id, t.id);
        }
        if (ganhou && jejAntes >= 5) {
            v.put("J", String.valueOf(jejAntes));
            nova("meu_time", 0, 56, "fimjej#" + h.id + "#" + sa, texto("fimjej#" + h.id + sa, v,
                    "Fim do jejum: {A} volta a vencer depois de {J} jogos",
                    "Alívio: {A} quebra a série de {J} jogos sem vitória"), null, h.id, t.id);
        }
        if (vit >= 4 && (vit == 4 || vit == 6 || vit == 8 || vit % 5 == 0)) {
            nova("meu_time", 0, 52 + vit, "vit#" + h.id + "#" + sa, texto("vit#" + h.id + vit, v,
                    "{A} emenda a {V}ª vitória seguida",
                    "Embalado, {A} chega a {V} vitórias consecutivas",
                    "Sequência: são {V} vitórias seguidas {doA}"), null, h.id, t.id);
        } else if (inv >= 6 && (inv == 6 || inv == 10 || inv % 5 == 0)) {
            nova("meu_time", 0, 50 + inv, "inv#" + h.id + "#" + sa, texto("inv#" + h.id + inv, v,
                    "{A} chega a {N} jogos de invencibilidade",
                    "Invicto há {N} jogos, {A} segue embalado",
                    "{N} jogos sem perder: a série {doA} continua"), null, h.id, t.id);
        }
        if (jej >= 4 && (jej == 4 || jej == 6 || jej == 8 || jej % 5 == 0)) {
            nova("meu_time", 0, 54 + jej, "jej#" + h.id + "#" + sa, texto("jej#" + h.id + jej, v,
                    "Crise: {A} chega a {J} jogos sem vencer",
                    "Jejum {doA} já dura {J} partidas",
                    "{J} jogos sem vitória: a pressão aumenta sobre {oA}"), null, h.id, t.id);
        }
    }

    // =================================================================== lesão
    /** Lesão de um jogador (o motor chama em todas); vira notícia se for titular do técnico e longa. */
    public void lesao(Jogador j, int semanas, String tipo) {
        Clube h = humano();
        if (h == null || !h.id.equals(j.clubeId) || semanas < 5) return;
        int melhores = 0;
        for (int id : h.elenco) {
            Jogador x = m.jogador(id);
            if (x != null && x != j && x.forca > j.forca) melhores++;
        }
        if (melhores >= 11) return; // não é titular
        Map<String, String> v = vars(h.nome, null, null, "J", j.nomeExibicao(), "L", tipo == null ? "lesão" : tipo.toLowerCase(Locale.ROOT),
                "W", String.valueOf(semanas));
        String k = "les#" + j.id + "#" + m.semanaAbsoluta();
        nova("meu_time", 0, 55 + Math.min(20, semanas), k, texto(k, v,
                "{J} sofre lesão e desfalca {oA} por {W} semanas",
                "Baixa {noA}: {J} fica fora por cerca de {W} semanas",
                "Desfalque: {J} tem {L} e só volta em {W} semanas",
                "Má notícia para {oA}: {J} vai parar por {W} semanas"), null, h.id, null);
    }

    // =================================================================== fim de semana
    private static int pendentes(Torneio t, FaseExec f, String clubeId) {
        int n = 0;
        for (Partida p : t.partidas) if (p.fase == f.indice && !p.jogada && p.envolve(clubeId)) n++;
        return n;
    }

    /** Tabela da liga do técnico: liderança, G4/zona de acesso, Z4, título e rebaixamento matemáticos. */
    private void tabela(Clube h) {
        Torneio t = minhaLiga;
        if (t == null || t.encerrado || t.fases.size() != 1 || !"pontos_corridos".equals(t.fases.get(0).tipo)) return;
        FaseExec f = t.fases.get(0);
        if (!f.iniciada) return;
        List<LinhaTabela> tab = t.tabela(0, 0);
        int n = tab.size();
        if (n < 6) return;
        int jogados = 0;
        for (LinhaTabela x : tab) if (x.clubeId.equals(h.id)) jogados = x.jogos;
        if (jogados < 3) return;
        int reb = t.formato != null && t.formato.rebaixados != null ? t.formato.rebaixados : 0;
        int prom = t.divisao != null && t.divisao > 1 && t.formato != null && t.formato.promovidos != null ? t.formato.promovidos : 0;
        int g = t.divisao != null && t.divisao == 1 ? (n >= 16 ? 4 : 0) : prom;
        String base = "@tab#" + t.id + "#" + t.ano + "#";
        int sa = m.semanaAbsoluta();
        Map<String, Integer> rest = new HashMap<>();
        for (LinhaTabela x : tab) rest.put(x.clubeId, pendentes(t, f, x.clubeId));
        int pos = 0;
        for (int i = 0; i < n; i++) if (tab.get(i).clubeId.equals(h.id)) pos = i + 1;
        String zona = pos == 1 ? "lider" : pos <= g ? "g" : reb > 0 && pos > n - reb ? "z" : "meio";
        int zonaCod = switch (zona) { case "lider" -> 1; case "g" -> 2; case "z" -> 4; default -> 3; };
        Integer antes = pauta(base + "zona");
        pauta(base + "zona", zonaCod);
        Map<String, String> v = vars(h.nome, null, t.nome, "N", String.valueOf(pos), "Z", "Z" + reb, "G", "G" + g);
        String ch = base + sa;
        if (antes != null && antes != zonaCod) {
            if (zonaCod == 1) {
                nova("tabela", 0, 74, ch + "lider", texto(ch + "lider", v,
                        "{A} assume a liderança {doC}",
                        "{A} é o novo líder {doC}",
                        "Topo da tabela: {A} lidera {oC}",
                        "{A} dorme na liderança {doC}"), null, h.id, t.id);
            } else if (antes == 1) {
                nova("tabela", 0, 66, ch + "perdeu", texto(ch + "perdeu", v,
                        "{A} perde a liderança {doC}",
                        "{A} deixa o topo e cai para {N}º",
                        "Liderança escapa: {A} agora é {N}º {noC}"), null, h.id, t.id);
            } else if (zonaCod == 2 && antes > 2) {
                boolean acesso = t.divisao != null && t.divisao > 1;
                nova("tabela", 0, 62, ch + "g", acesso ? texto(ch + "g", v,
                        "{A} entra na zona de acesso {doC}",
                        "{A} sobe para {N}º e entra no {G}",
                        "Rumo ao acesso: {A} já está no {G}")
                        : texto(ch + "g", v,
                        "{A} entra no {G} {doC}",
                        "{A} sobe para {N}º e entra no {G}",
                        "Na briga lá em cima: {A} entra no {G}"), null, h.id, t.id);
            } else if (zonaCod == 4) {
                nova("tabela", 0, 70, ch + "z", texto(ch + "z", v,
                        "{A} entra na zona de rebaixamento",
                        "Alerta: {A} cai para o {Z}",
                        "{A} afunda na tabela e está no {Z}",
                        "Sinal vermelho: {A} é {N}º e está no {Z}"), null, h.id, t.id);
            } else if (antes == 4) {
                nova("tabela", 0, 64, ch + "saiuz", texto(ch + "saiuz", v,
                        "{A} deixa a zona de rebaixamento",
                        "Respiro: {A} sai do {Z}",
                        "{A} reage e sai do {Z}"), null, h.id, t.id);
            }
        }
        // novo líder (outro clube) da liga
        LinhaTabela lider = tab.get(0);
        int iLider = t.participantes.indexOf(lider.clubeId);
        Integer liderAntes = pauta(base + "lider");
        pauta(base + "lider", iLider);
        if (liderAntes != null && liderAntes != iLider && !lider.clubeId.equals(h.id) && liderAntes >= 0 && liderAntes < t.participantes.size()) {
            Clube nl = m.clube(lider.clubeId);
            if (nl != null) {
                Map<String, String> vl = vars(nl.nome, m.nomeParticipante(t.participantes.get(liderAntes)), t.nome);
                nova("tabela", 1, 50, ch + "novolider", texto(ch + "novolider", vl,
                        "{A} toma a liderança {doC}",
                        "Troca no topo: {A} passa {oB}",
                        "{A} é o novo líder {doC}"), null, nl.id, t.id);
            }
        }
        // matemática (uma vez por temporada e clube)
        LinhaTabela seg = tab.get(1);
        boolean campeao = true;
        for (int i = 1; i < n; i++) if (tab.get(i).pontos + 3 * rest.get(tab.get(i).clubeId) >= lider.pontos) campeao = false;
        if (campeao && rest.get(seg.clubeId) + rest.get(lider.clubeId) > 0 && pauta(base + "titmat") == null) {
            pauta(base + "titmat", sa);
            Clube c = m.clube(lider.clubeId);
            if (c != null) {
                boolean meu = c == h;
                Map<String, String> vc = vars(c.nome, null, t.nome, "R", String.valueOf(rest.get(lider.clubeId)));
                nova("titulo", meu ? 0 : 1, meu ? 95 : 72, base + "titmat", meu ? texto(base + "titmat", vc,
                        "É campeão! {A} garante o título {doC} com {R} rodadas de antecedência",
                        "Título matemático: {A} é campeão {doC}",
                        "Não tem mais jeito: {oA} é campeão {doC}")
                        : texto(base + "titmat", vc,
                        "{A} garante o título {doC} por antecipação",
                        "Título matemático: {A} é campeão {doC}",
                        "{A} conquista {oC} com {R} rodadas de antecedência"), null, c.id, t.id);
            }
        }
        if (reb > 0) {
            for (int i = 0; i < n; i++) {
                LinhaTabela x = tab.get(i);
                int teto = x.pontos + 3 * rest.get(x.clubeId);
                int acima = 0;
                for (LinhaTabela y : tab) if (y != x && y.pontos > teto) acima++;
                String kc = base + "rebmat#" + x.clubeId;
                if (acima >= n - reb && rest.get(x.clubeId) > 0 && pauta(kc) == null) {
                    pauta(kc, sa);
                    Clube c = m.clube(x.clubeId);
                    if (c == null) continue;
                    boolean meu = c == h;
                    Map<String, String> vc = vars(c.nome, null, t.nome);
                    nova("divisoes", meu ? 0 : 1, meu ? 92 : 58, kc, texto(kc, vc,
                            "Rebaixamento confirmado: {A} cai {doC}",
                            "{A} está matematicamente rebaixado",
                            "Fim da linha: {oA} é rebaixado com rodadas de antecedência"), null, c.id, t.id);
                }
            }
            // rebaixamento iminente do clube do técnico
            if ("z".equals(zona) && pauta(base + "iminente") == null) {
                int r = rest.get(h.id);
                int salvo = tab.get(n - reb - 1).pontos;
                int falta = salvo - tab.get(pos - 1).pontos;
                if (r > 0 && r <= 6 && falta > 0 && falta >= 2 * r) {
                    pauta(base + "iminente", sa);
                    Map<String, String> vc = vars(h.nome, null, t.nome, "R", String.valueOf(r), "F", String.valueOf(falta));
                    nova("tabela", 0, 80, base + "iminente", texto(base + "iminente", vc,
                            "Rebaixamento à vista: {A} está a {F} pontos da salvação",
                            "{A} precisa de um milagre nas {R} rodadas finais",
                            "Queda iminente: faltam {R} rodadas e {oA} está a {F} pontos de sair do {Z}"), null, h.id, t.id);
                }
            }
        }
        if (prom > 0) {
            for (int i = 0; i < Math.min(n, prom); i++) {
                LinhaTabela x = tab.get(i);
                int alcancam = 0;
                for (LinhaTabela y : tab) if (y != x && y.pontos + 3 * rest.get(y.clubeId) >= x.pontos) alcancam++;
                String kc = base + "acesso#" + x.clubeId;
                if (alcancam < prom && rest.get(x.clubeId) > 0 && pauta(kc) == null) {
                    pauta(kc, sa);
                    Clube c = m.clube(x.clubeId);
                    if (c == null) continue;
                    boolean meu = c == h;
                    Map<String, String> vc = vars(c.nome, null, t.nome);
                    nova("divisoes", meu ? 0 : 1, meu ? 93 : 56, kc, texto(kc, vc,
                            "Acesso garantido: {A} sobe de divisão",
                            "{A} confirma o acesso por antecipação",
                            "Festa: {oA} garante a promoção"), null, c.id, t.id);
                }
            }
        }
    }

    /** Técnicos na corda bamba na liga do técnico (confiança da diretoria baixa) e pressão sobre ele. */
    private void cordaBamba(Clube h) {
        int sa = m.semanaAbsoluta();
        for (String id : clubesDaLiga) {
            Clube c = m.clube(id);
            if (c == null || c == h || c.virtual || c.treinadorId == null || c.jogosTemp < 4 || c.confiancaDiretoria >= 28) continue;
            String k = "@bamba#" + c.id;
            Integer antes = pauta(k);
            if (antes != null && antes > sa - 10) continue;
            pauta(k, sa);
            br.futebas.mundo.Treinador tr = m.treinador(c.treinadorId);
            if (tr == null) continue;
            int jogos = c.forma.size(), d = 0, vit = 0;
            for (int p : c.forma) {
                if (p == 0) d++;
                if (p == 3) vit++;
            }
            Map<String, String> v = vars(c.nome, null, minhaLiga == null ? null : minhaLiga.nome, "T", tr.nome);
            String linha = vit == 0 && jogos >= 3 ? "Sem vitória nos últimos " + jogos + " jogos" : d + " derrotas nos últimos " + jogos + " jogos";
            nova("tecnicos", 1, 60 + (int) (28 - c.confiancaDiretoria), "bamba#" + c.id + "#" + sa, texto(k + sa, v,
                    "{T} balança no comando {doA}",
                    "Corda bamba: {T} pode cair {noA}",
                    "Diretoria {doA} perde a paciência com {T}",
                    "Pressão total sobre {T} {noA}",
                    "Futuro de {T} {noA} está em jogo"), linha, c.id, minhaLiga == null ? null : minhaLiga.id);
        }
        String k = "@pressao#" + h.id;
        Integer antes = pauta(k);
        if (h.confiancaDiretoria < 30 && h.jogosTemp >= 4 && (antes == null || antes <= sa - 8) && m.tecnico != null) {
            pauta(k, sa);
            Map<String, String> v = vars(h.nome, null, null, "T", m.tecnico.nome);
            nova("tecnicos", 0, 78, "pressao#" + h.id + "#" + sa, texto(k + sa, v,
                    "{T} na corda bamba {noA}",
                    "Diretoria {doA} cobra {T} e o cargo balança",
                    "Pressão sobre {T}: a paciência {noA} está no fim"), null, h.id, null);
        }
    }

    /** Mata-matas decididos na semana nas competições do técnico: o clube dele e os gigantes eliminados. */
    private void mataMatas(Clube h, int sa) {
        for (Torneio t : m.torneios) {
            if (!minhasComps.contains(t.id)) continue;
            Map<Integer, Partida> porId = null;
            for (FaseExec f : t.fases) {
                if (!f.mataMata() || f.confrontos.isEmpty()) continue;
                for (Confronto c : f.confrontos) {
                    if (c.vencedor == null || c.perdedor == null || c.partidas.isEmpty()) continue;
                    if (porId == null) {
                        porId = new HashMap<>();
                        for (Partida p : t.partidas) porId.put(p.id, p);
                    }
                    Partida ult = null;
                    for (int id : c.partidas) {
                        Partida p = porId.get(id);
                        if (p != null && (ult == null || p.sa > ult.sa)) ult = p;
                    }
                    if (ult == null || ult.sa != sa) continue;
                    int mesmos = 0;
                    for (Confronto o : f.confrontos) if (o.rodada == c.rodada) mesmos++;
                    if (mesmos <= 1) continue; // final: notícia do título
                    String prox = switch (mesmos) {
                        case 2 -> "à final";
                        case 4 -> "às semifinais";
                        case 8 -> "às quartas de final";
                        case 16 -> "às oitavas de final";
                        default -> "à próxima fase";
                    };
                    Clube w = m.clube(c.vencedor), l = m.clube(c.perdedor);
                    if (w == null || l == null) continue;
                    String k = "mm#" + t.id + "#" + t.ano + "#" + f.indice + "#" + c.rodada + "#" + c.vencedor;
                    Map<String, String> v = vars(w.nome, l.nome, t.nome, "F", prox);
                    if (w == h) {
                        nova("meu_time", 0, mesmos <= 2 ? 88 : mesmos <= 4 ? 80 : 68, k, texto(k, v,
                                "{A} elimina {oB} e vai {F}",
                                "Classificado: {A} passa {peloB} e vai {F}",
                                "{A} despacha {oB} e avança {noC}"), t.nome, w.id, t.id);
                    } else if (l == h) {
                        nova("meu_time", 0, mesmos <= 2 ? 86 : 72, k, texto(k, v,
                                "{B} é eliminado {doC} {peloA}",
                                "Fim do sonho: {oB} cai diante {doA} {noC}",
                                "{A} elimina {oB} {doC}"), t.nome, l.id, t.id);
                    } else if (l.reputacao >= 70 && w.reputacao <= l.reputacao - 15) {
                        nova("zebra", 1, 60 + (int) (l.reputacao - w.reputacao), k, texto(k, v,
                                "Zebra {noC}: {A} elimina {oB}",
                                "Gigante fora: {B} cai diante {doA}",
                                "{A} derruba {oB} e vai {F}",
                                "Vexame {doB}: eliminado {peloA} {noC}"), t.nome, w.id, t.id);
                    }
                }
            }
        }
    }

    /** Artilheiro da liga do técnico: marcos de gols. */
    private void artilharia(Clube h) {
        Torneio t = minhaLiga;
        if (t == null || t.encerrado) return;
        List<int[]> art = t.artilheiros(1);
        if (art.isEmpty()) return;
        int gols = art.get(0)[1];
        int marco = 0;
        for (int x : new int[]{10, 15, 20, 25, 30, 35, 40}) if (gols >= x) marco = x;
        if (marco == 0) return;
        String k = "@art#" + t.id + "#" + t.ano;
        Integer antes = pauta(k);
        if (antes != null && antes >= marco) return;
        pauta(k, marco);
        Jogador j = m.jogador(art.get(0)[0]);
        if (j == null) return;
        Clube c = m.clube(j.clubeId);
        boolean meu = c == h;
        Map<String, String> v = vars(c == null ? "" : c.nome, null, t.nome, "J", j.nomeExibicao(), "N", String.valueOf(gols));
        nova("recorde", meu ? 0 : 1, 50 + marco / 2, "art#" + t.id + "#" + t.ano + "#" + marco, texto(k + marco, v,
                "{J} dispara na artilharia {doC} com {N} gols",
                "Artilheiro {doC}, {J} chega a {N} gols",
                "{J} {doA} lidera a artilharia com {N} gols",
                "Ninguém segura {J}: {N} gols {noC}"), null, c == null ? null : c.id, t.id);
    }

    // =================================================================== fim de competição
    private static boolean grandeDoMundo(Torneio t, Clube camp) {
        String id = t.id == null ? "" : t.id;
        if ("selecoes".equals(t.ambito))
            return id.contains("copa-do-mundo") || id.contains("copa-america") || id.contains("euro") || id.equals("finalissima");
        if ("continental".equals(t.ambito)) return id.contains("champions") || id.contains("libertadores") || id.contains("mundial");
        if ("nacional".equals(t.ambito) && Integer.valueOf(1).equals(t.divisao))
            return camp != null && camp.reputacao >= 80 && Set.of("ENG", "ESP", "ITA", "GER", "DEU", "FRA", "BRA", "ARG", "POR", "PRT").contains(t.pais);
        return false;
    }

    /** Título decidido (o motor chama em todos): campeões, vices, rebaixados e promovidos. */
    public void titulo(Torneio t, Historia.Titulo tit) {
        if ("amistoso".equals(t.ambito) || tit.campeaoId == null) return;
        Clube h = humano();
        String selH = m.tecnico == null || m.tecnico.selecaoPais == null ? null : br.futebas.mundo.Selecao.id(m.tecnico.selecaoPais);
        String kingsH = m.tecnico == null ? null : m.tecnico.kingsTimeId;
        boolean meuCampeao = (h != null && h.id.equals(tit.campeaoId)) || tit.campeaoId.equals(selH) || tit.campeaoId.equals(kingsH);
        boolean meuVice = tit.viceId != null && ((h != null && h.id.equals(tit.viceId)) || tit.viceId.equals(selH) || tit.viceId.equals(kingsH));
        if ("kings".equals(t.ambito) && !meuCampeao && !meuVice) return;
        Clube camp = m.clube(tit.campeaoId);
        int foco = meuCampeao || meuVice ? 0 : foco(t.id, tit.campeaoId, tit.viceId);
        boolean grande = grandeDoMundo(t, camp);
        if (foco >= 3 && !grande) return;
        if (foco == 2 && !"nacional".equals(t.ambito) && !"copa_nacional".equals(t.ambito) && !grande) return;
        String k = "tit#" + t.id + "#" + t.ano;
        String temp = t.temporada == null ? String.valueOf(t.ano) : t.temporada;
        if (t.nome.contains(temp) || t.nome.contains(String.valueOf(t.ano))) temp = "";
        Map<String, String> v = vars(tit.campeao, tit.vice, t.nome, "T", temp);
        String linha = tit.vice == null ? (t.nome + " " + v.get("T")).trim() : "Vice: " + tit.vice;
        if (meuCampeao && "selecoes".equals(t.ambito)) {
            nova("titulo", 0, 100, k, texto(k, v,
                    "É campeão! {A} conquista {oC}",
                    "Taça na mão: {A} vence {oC} {T}",
                    "{A} levanta a taça {doC}"), linha, tit.campeaoId, t.id);
        } else if (meuCampeao) {
            nova("titulo", 0, 100, k, texto(k, v,
                    "É campeão! {A} conquista {oC}",
                    "{A} é campeão {doC} {T}",
                    "Taça na mão: {A} vence {oC}",
                    "Festa: {oA} levanta a taça {doC}"), linha, tit.campeaoId, t.id);
        } else if (meuVice) {
            nova("titulo", 0, 84, k, texto(k, v,
                    "{B} fica com o vice {doC}",
                    "Quase: {B} perde o título {doC} para {oA}",
                    "{A} é campeão e {oB} fica com o vice"), linha, tit.viceId, t.id);
        } else if ("selecoes".equals(t.ambito)) {
            nova(foco >= 3 ? "mundo" : "titulo", foco, grande ? 88 : 60, k, texto(k, v,
                    "{A} conquista {oC} {T}",
                    "{A} vence {oC} {T}",
                    "{A} levanta a taça {doC}"), linha, tit.campeaoId, t.id);
        } else {
            nova(grande && foco >= 3 ? "mundo" : "titulo", foco, grande ? 80 : 60, k, texto(k, v,
                    "{A} é campeão {doC}",
                    "{A} conquista {oC} {T}",
                    "Título {doC} fica com {oA}",
                    "{A} vence {oC} e soma mais uma taça"), linha, tit.campeaoId, t.id);
        }
        if ("nacional".equals(t.ambito) && t.divisao != null && t.formato != null && !t.classificacao.isEmpty()) divisoes(t, h);
    }

    private void divisoes(Torneio t, Clube h) {
        int n = t.classificacao.size();
        int reb = t.formato.rebaixados == null ? 0 : t.formato.rebaixados;
        int prom = t.divisao > 1 && t.formato.promovidos != null ? t.formato.promovidos : 0;
        int foco = foco(t.id, t.classificacao.toArray(new String[0]));
        if (foco >= 3 || (foco == 2 && t.divisao > 2)) return;
        if (reb > 0 && reb < n) {
            List<String> caem = new ArrayList<>();
            for (int i = n - reb; i < n; i++) {
                String id = t.classificacao.get(i);
                Clube c = m.clube(id);
                if (c == null) continue;
                caem.add(c.nome);
                if (c == h) {
                    String k = "caiu#" + t.id + "#" + t.ano;
                    nova("divisoes", 0, 96, k, texto(k, vars(c.nome, null, t.nome),
                            "Rebaixado: {A} cai {doC}",
                            "Dia triste: {oA} está rebaixado",
                            "{A} não escapa e disputará a divisão de baixo"), null, c.id, t.id);
                }
            }
            String k = "caem#" + t.id + "#" + t.ano;
            Map<String, String> vl = vars(null, null, t.nome, "L", lista(caem));
            if (!caem.isEmpty()) nova("divisoes", foco == 0 ? 1 : foco, 62, k, caem.size() == 1
                    ? texto(k, vl, "Rebaixado {doC}: {L}", "{L} cai {doC}")
                    : texto(k, vl, "Rebaixados {doC}: {L}", "Caem {doC}: {L}", "{L} deixam {oC}"), null, null, t.id);
        }
        if (prom > 0 && prom < n) {
            List<String> sobem = new ArrayList<>();
            for (int i = 0; i < prom; i++) {
                Clube c = m.clube(t.classificacao.get(i));
                if (c == null) continue;
                sobem.add(c.nome);
                if (c == h) {
                    String k = "subiu#" + t.id + "#" + t.ano;
                    nova("divisoes", 0, 96, k, texto(k, vars(c.nome, null, t.nome),
                            "Acesso! {A} sobe de divisão",
                            "{A} conquista o acesso",
                            "Promovido: {oA} jogará a divisão de cima"), null, c.id, t.id);
                }
            }
            String k = "sobem#" + t.id + "#" + t.ano;
            Map<String, String> vl = vars(null, null, t.nome, "L", lista(sobem));
            if (!sobem.isEmpty()) nova("divisoes", foco == 0 ? 1 : foco, 60, k, sobem.size() == 1
                    ? texto(k, vl, "{L} conquista o acesso {noC}", "Acesso confirmado para {L}")
                    : texto(k, vl, "Sobem {doC}: {L}", "Acesso confirmado para {L}", "{L} conquistam o acesso {noC}"), null, null, t.id);
        }
    }

    private static String lista(List<String> l) {
        if (l.isEmpty()) return "";
        if (l.size() == 1) return l.get(0);
        return String.join(", ", l.subList(0, l.size() - 1)) + " e " + l.get(l.size() - 1);
    }

    // =================================================================== técnicos
    /** Clube da IA trocou de técnico. */
    public void tecnicoDemitido(Clube c, String antigo, String novo) {
        int foco = foco(null, c.id);
        if (foco >= 3 || (foco == 2 && c.reputacao < 70)) return;
        int sa = m.semanaAbsoluta();
        String k = "dem#" + c.id + "#" + sa;
        Map<String, String> v = vars(c.nome, null, null, "T", antigo == null ? "o técnico" : antigo, "N", novo == null ? "" : novo);
        String tit = antigo == null ? texto(k, v, "{A} troca de técnico", "Mudança no comando {doA}")
                : texto(k, v,
                "{A} demite {T}",
                "{T} não resiste e cai {noA}",
                "Fim da linha para {T} {noA}",
                "Diretoria {doA} demite {T}");
        nova("tecnicos", foco, 66 + (int) (c.reputacao / 5), k, tit, novo == null ? null : novo + " é o novo técnico", c.id, null);
    }

    /** O técnico humano foi demitido. */
    public void humanoDemitido(Clube c) {
        if (m.tecnico == null) return;
        String k = "demh#" + c.id + "#" + m.semanaAbsoluta();
        Map<String, String> v = vars(c.nome, null, null, "T", m.tecnico.nome);
        nova("tecnicos", 0, 98, k, texto(k, v, "{A} demite {T}", "{T} é demitido {doA}", "Fim de ciclo: {T} deixa o comando {doA}"), null, c.id, null);
    }

    /** O técnico humano assumiu um clube. */
    public void humanoContratado(Clube c) {
        if (m.tecnico == null) return;
        String k = "conh#" + c.id + "#" + m.semanaAbsoluta();
        Map<String, String> v = vars(c.nome, null, null, "T", m.tecnico.nome);
        nova("tecnicos", 0, 97, k, texto(k, v, "{T} é o novo técnico {doA}", "{A} anuncia {T} como treinador", "Novo comando: {T} assume {oA}"), null, c.id, null);
    }

    // =================================================================== mercado
    /** Transferência concluída (o motor chama em todas). */
    public void transferencia(Jogador j, Clube origem, Clube destino, long preco) {
        if (destino.humano) pauta("@chegada#" + j.id, m.semanaAbsoluta());
        int foco = foco(null, origem == null ? null : origem.id, destino.id);
        long grande = (long) (Financas.fatorEpoca(m.ano) * 150_000_000L);
        boolean bomba = preco >= grande || (preco > 0 && j.topMundial);
        boolean relevante = j.estrela || j.topMundial || j.fama >= 55 || j.forca >= 74 || preco >= grande / 10;
        if (foco >= 3 && !bomba) return;
        if (foco >= 1 && !relevante && !bomba) return;
        if (foco == 2 && !(j.fama >= 60 || bomba || j.forca >= 78)) return;
        String k = "tr#" + j.id + "#" + m.semanaAbsoluta();
        Map<String, String> v = vars(destino.nome, origem == null ? null : origem.nome, null, "J", j.nomeExibicao(), "V", Financas.formatar(preco));
        String tit;
        if (destino.humano) {
            tit = texto(k, v, "{A} anuncia {J}", "{J} é o novo reforço {doA}", "Reforço: {J} acerta com {oA}", "{A} fecha a contratação de {J}");
        } else if (origem != null && origem.humano) {
            tit = preco > 0 ? texto(k, v, "{J} deixa {oB} rumo {aoA}", "{B} vende {J} {aoA}", "Venda: {J} vai para {oA} por {V}")
                    : texto(k, v, "{J} deixa {oB} e vai para {oA}", "{J} se despede {doB}");
        } else if (origem == null || preco == 0) {
            tit = texto(k, v, "{A} contrata {J}", "{J} é o novo reforço {doA}", "{A} acerta com {J}, que estava sem clube");
        } else {
            tit = bomba ? texto(k, v, "Bomba no mercado: {J} troca {oB} pelo {A}", "Contratação de peso: {J} vai para {oA} por {V}", "{A} paga {V} por {J}")
                    : texto(k, v, "{A} tira {J} {doB}", "{J} deixa {oB} e reforça {oA}", "{A} contrata {J}, ex-{B}", "{B} negocia {J} com {oA}");
        }
        int peso = foco == 0 ? 70 : bomba ? 85 : (int) Math.min(80, 40 + j.forca / 4 + j.fama / 5);
        nova("mercado", foco, peso, k, tit, preco > 0 ? "Valor: " + Financas.formatar(preco) : null, destino.id, null);
    }

    // =================================================================== aposentadorias
    /** Jogador anunciou que vai se aposentar no fim da temporada. */
    public void aposentadoriaAnunciada(Jogador j, Clube c, int idade) {
        int foco = foco(null, c == null ? null : c.id);
        boolean astro = j.estrela || j.topMundial || j.fama >= 85;
        boolean notavel = astro || j.fama >= 50 || j.forca >= 72 || j.jogosCarreira >= 300;
        if (foco == 0 && !(notavel || j.jogosCarreira >= 100 || j.forca >= c.nivel() - 4)) return;
        if (foco >= 1 && foco <= 2 && !notavel) return;
        if (foco >= 3 && !astro) return;
        String k = "apo#" + j.id;
        Map<String, String> v = vars(c == null ? null : c.nome, null, null, "J", j.nomeExibicao(), "I", String.valueOf(idade));
        String tit = c == null ? texto(k, v, "{J} anuncia que vai pendurar as chuteiras", "Aos {I} anos, {J} anuncia a aposentadoria")
                : texto(k, v,
                "{J} anuncia que vai se aposentar no fim da temporada",
                "Aos {I} anos, {J} anuncia a despedida dos gramados",
                "Contagem regressiva: {J} vai pendurar as chuteiras",
                "{J} avisa que a temporada será a última da carreira");
        nova(foco >= 3 ? "mundo" : "aposentadoria", foco, (foco == 0 ? 72 : 50) + (astro ? 20 : 0), k, tit,
                c == null ? null : "Joga hoje " + (clubeFeminino(c.nome) ? "na " : "no ") + c.nome, c == null ? null : c.id, null);
    }

    // =================================================================== Bola de Ouro
    /** Bola de Ouro do ano (grande evento do mundo). */
    public void bolaDeOuro(Historia.Ranking r) {
        if (r == null || r.itens.isEmpty()) return;
        Historia.ItemRanking v1 = r.itens.get(0);
        String k = "bola#" + r.ano;
        Map<String, String> v = vars(v1.clube, null, null, "J", v1.jogador, "Y", String.valueOf(r.ano));
        nova("mundo", foco(null, v1.clubeId) == 0 ? 0 : 3, 90, k, texto(k, v,
                "{J} ganha a Bola de Ouro {Y}",
                "Melhor do mundo: {J} leva a Bola de Ouro",
                "Bola de Ouro {Y} é de {J}"), v1.clube == null ? null : "Joga " + (clubeFeminino(v1.clube) ? "na " : "no ") + v1.clube, v1.clubeId, null);
        Clube h = humano();
        if (h == null) return;
        for (Historia.ItemRanking it : r.itens) {
            if (it.posicao <= 1 || it.posicao > 10 || !h.id.equals(it.clubeId)) continue;
            String kk = "bola#" + r.ano + "#" + it.jogadorId;
            Map<String, String> vv = vars(h.nome, null, null, "J", it.jogador, "N", String.valueOf(it.posicao), "Y", String.valueOf(r.ano));
            nova("meu_time", 0, 70, kk, texto(kk, vv, "{J}, {doA}, termina em {N}º na Bola de Ouro", "{J} fica no top 10 da Bola de Ouro {Y}"), null, h.id, null);
        }
    }
}
