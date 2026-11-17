package br.futebas.historia;

import br.futebas.competicao.FaseExec;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.kings.DinheiroKings;
import br.futebas.kings.Kings;
import br.futebas.kings.LigaKings;
import br.futebas.kings.MotorKings;
import br.futebas.mundo.Artigos;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Rng;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.ResultadoPartida;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Noticiário da Kings League (requisito, 12/11/2026: "a carreira da Kings tem o início e a
 * caixa vazios"). O {@link Noticiario} pulava as competições da Kings; aqui elas viram notícias com o
 * mesmo tom e a mesma variedade do futebol, com foco no time e na liga do técnico:
 * <ul>
 *   <li>jogo do seu time: resultado (goleada, virada no match ball, shoot out, zebra, tropeço), hat-trick,
 *   pênalti do presidente, cartas usadas e o que deram;</li>
 *   <li>a sua liga: zebras, goleadas, viradas no match ball, o Coringa que rouba carta, liderança,
 *   briga pelo título, quem vai aos playoffs, técnicos trocados, draft (1ª escolha e "steals"),
 *   wildcards de peso;</li>
 *   <li>títulos (Split, Kings Cup, Kings World Cup) e o começo da Copa do Mundo.</li>
 * </ul>
 * Fora da carreira da Kings, só os campeões das Copas do Mundo da Kings (como notícia do mundo).
 * Sempre "shoot out" (nunca "pênalti shootout"). As notícias entram como candidatas da semana e o
 * {@link Noticiario} publica as melhores; o resultado do seu jogo também vai para a caixa de entrada.
 * Textos determinísticos (semente do mundo + chave), sem mexer no gerador do jogo.
 */
public final class NoticiasKings {
    private final Mundo m;

    public NoticiasKings(Mundo m) {
        this.m = m;
    }

    // ================================================================ foco
    private String meu() {
        return m.tecnico == null ? null : m.tecnico.kingsTimeId;
    }

    /** O técnico treina um time da Kings? */
    public boolean carreira() {
        return meu() != null;
    }

    private LigaKings minhaLiga() {
        String meu = meu();
        if (meu == null || m.kings == null) return null;
        for (LigaKings l : m.kings) if (!Kings.ehMundial(l) && l.time(meu) != null) return l;
        return null;
    }

    /** 0 = o seu time; 1 = a sua liga (ou uma Copa do Mundo em que você joga); 2 = outra liga da Kings; 3 = mundo. */
    int foco(LigaKings liga, Torneio t, String... times) {
        String meu = meu();
        if (meu == null) return 3;
        for (String x : times) if (meu.equals(x)) return 0;
        if (liga != null && liga.time(meu) != null) return 1;
        if (t != null && t.participantes.contains(meu)) return 1;
        return 2;
    }

    private void nova(String tipo, int foco, int peso, String chave, String titulo, String texto, String timeId, String compId) {
        if (titulo == null || titulo.isBlank()) return;
        Historia.Noticia n = new Historia.Noticia();
        n.ano = m.ano;
        n.semana = m.semana;
        n.tipo = tipo;
        n.foco = foco;
        n.peso = Math.max(0, Math.min(100, peso));
        n.chave = chave;
        n.titulo = titulo;
        n.texto = texto;
        n.clubeId = timeId;
        n.competicaoId = compId;
        m.historia.noticiasPendentes.add(n);
    }

    // ================================================================ textos
    String escolher(String chave, String... opcoes) {
        long h = m.semente * 0x9E3779B97F4A7C15L ^ (long) chave.hashCode() * 0xBF58476D1CE4E5B9L ^ (m.ano * 53L + m.semana);
        return opcoes[new Rng(h).inteiro(opcoes.length)];
    }

    private String texto(String chave, Map<String, String> v, String... opcoes) {
        return Noticiario.preencher(escolher(chave, opcoes), v);
    }

    private static void time(Map<String, String> v, String k, String nome) {
        if (nome == null) return;
        boolean fem = Noticiario.clubeFeminino(nome);
        v.put(k, nome);
        v.put("o" + k, (fem ? "a " : "o ") + nome);
        v.put("do" + k, (fem ? "da " : "do ") + nome);
        v.put("ao" + k, (fem ? "à " : "ao ") + nome);
        v.put("no" + k, (fem ? "na " : "no ") + nome);
        v.put("pelo" + k, (fem ? "pela " : "pelo ") + nome);
    }

    static Map<String, String> vars(String a, String b, String c, String... livres) {
        Map<String, String> v = new LinkedHashMap<>();
        time(v, "A", a);
        time(v, "B", b);
        if (c != null) {
            boolean fem = Artigos.feminina(c);
            v.put("C", c);
            v.put("oC", (fem ? "a " : "o ") + c);
            v.put("doC", (fem ? "da " : "do ") + c);
            v.put("noC", (fem ? "na " : "no ") + c);
            v.put("peloC", (fem ? "pela " : "pelo ") + c);
        }
        for (int i = 0; i + 1 < livres.length; i += 2) v.put(livres[i], livres[i + 1]);
        return v;
    }

    /** "G3X 5 x 4 Porcinos (shoot out 3 x 2)". */
    static String placar(String a, String b, ResultadoPartida r) {
        String s = a + " " + r.golsMandante + " x " + r.golsVisitante + " " + b;
        if (r.penaltisMandante != null) s += " (shoot out " + r.penaltisMandante + " x " + r.penaltisVisitante + ")";
        return s;
    }

    private static String nomeCurto(Torneio t) {
        if (t == null) return "Kings League";
        String n = t.nome;
        return t.temporada != null && n.endsWith(" " + t.temporada) ? n.substring(0, n.length() - t.temporada.length() - 1) : n;
    }

    // ================================================================ partida
    /** O que aconteceu no jogo (pelos eventos do motor). */
    public record Leitura(boolean matchBall, int liderAos36, boolean shootout, int vencedor, List<String[]> cartas, String[] presidente,
                   Map<String, Integer> golsPorAtleta, boolean coringaRoubou, String dado) {}

    public static Leitura ler(ResultadoPartida r) {
        boolean mb = false, roubou = false;
        int[] p = new int[2];
        int lider36 = -1;
        List<String[]> cartas = new ArrayList<>();
        String[] pres = new String[2];
        Map<String, Integer> gols = new LinkedHashMap<>();
        String dado = null;
        for (EventoJogo e : r.eventos) {
            String t = e.tipo();
            if ("fase".equals(t) && "Match ball".equals(e.jogador())) {
                mb = true;
                lider36 = p[0] > p[1] ? 0 : p[1] > p[0] ? 1 : -1;
            }
            if (e.gol() && e.lado() >= 0 && e.lado() <= 1) {
                p[e.lado()] += e.valor();
                if (!t.startsWith("penalti_presidente") && e.jogador() != null && !e.jogador().isBlank())
                    gols.merge(e.lado() + "|" + e.jogador(), 1, Integer::sum);
            }
            if ("carta_usada".equals(t) && e.lado() >= 0) {
                String c = e.jogador() == null ? "" : e.jogador();
                if (c.startsWith("Coringa →")) {
                    // a 2ª linha do Coringa: o que ele virou (substitui a 1ª)
                    if (!cartas.isEmpty() && cartas.get(cartas.size() - 1)[0].equals(String.valueOf(e.lado()))) cartas.remove(cartas.size() - 1);
                    cartas.add(new String[]{String.valueOf(e.lado()), c, null});
                } else cartas.add(new String[]{String.valueOf(e.lado()), c, null});
            }
            if (t.startsWith("penalti_carta_") || t.startsWith("penalti_invertido_")) {
                String res = t.endsWith("_gol") ? "GOL" : t.endsWith("_defendeu") ? "DEFENDEU" : "PRA FORA";
                for (int i = cartas.size() - 1; i >= 0; i--) if (cartas.get(i)[2] == null) {
                    cartas.get(i)[2] = res;
                    break;
                }
            }
            if ("jogador_estrela".equals(t) || "suspensao".equals(t)) {
                for (int i = cartas.size() - 1; i >= 0; i--) if (cartas.get(i)[2] == null) {
                    cartas.get(i)[2] = e.jogador();
                    break;
                }
            }
            if (t.startsWith("penalti_presidente") && e.lado() >= 0 && e.lado() <= 1) pres[e.lado()] = t.contains("_gol") ? "GOL" : "PERDEU";
            if ("dado".equals(t)) dado = e.jogador();
        }
        for (EventoJogo e : r.eventos) if ("carta_usada".equals(e.tipo()) && e.jogador() != null && e.jogador().startsWith("Coringa →")) {
            // roubou: a carta virada é uma carta que o adversário tinha (a sorteada dele)
            for (EventoJogo s : r.eventos) if ("carta_sorteada".equals(s.tipo()) && s.lado() == 1 - e.lado()
                    && e.jogador().endsWith(s.jogador()) && !usou(r, 1 - e.lado())) roubou = true;
        }
        int venc = r.golsMandante > r.golsVisitante ? 0 : r.golsVisitante > r.golsMandante ? 1
                : r.penaltisMandante == null ? -1 : r.penaltisMandante > r.penaltisVisitante ? 0 : 1;
        return new Leitura(mb, lider36, r.penaltisMandante != null, venc, cartas, pres, gols, roubou, dado);
    }

    private static boolean usou(ResultadoPartida r, int lado) {
        for (EventoJogo e : r.eventos) if ("carta_usada".equals(e.tipo()) && e.lado() == lado) return true;
        return false;
    }

    /** Depois de cada jogo da Kings: o do seu time e os da sua liga (ou da Copa do Mundo em que você está). */
    public void partida(LigaKings liga, Torneio t, Partida p, ResultadoPartida r, LigaKings.TimeKings a, LigaKings.TimeKings b) {
        try {
            String meu = meu();
            if (meu == null || a == null || b == null || r == null) return;
            int foco = foco(liga, t, a.id, b.id);
            if (foco >= 2) return;
            Leitura l = ler(r);
            if (foco == 0) meuJogo(t, p, r, a, b, meu.equals(a.id) ? 0 : 1, l);
            else jogoDaLiga(t, p, r, a, b, l);
        } catch (RuntimeException e) {
            // notícia nunca derruba a simulação
        }
    }

    private static double forca(LigaKings.TimeKings t) {
        return MotorKings.forcaPublica(t);
    }

    private void meuJogo(Torneio t, Partida p, ResultadoPartida r, LigaKings.TimeKings a, LigaKings.TimeKings b, int lado, Leitura l) {
        LigaKings.TimeKings eu = lado == 0 ? a : b, adv = lado == 0 ? b : a;
        int pro = lado == 0 ? r.golsMandante : r.golsVisitante, contra = lado == 0 ? r.golsVisitante : r.golsMandante;
        boolean venceu = l.vencedor() == lado;
        String ch = t.id + "#" + t.ano + "#" + p.id;
        String pl = placar(a.nome, b.nome, r);
        String linha = nomeCurto(t) + " · " + pl;
        String P = pro + " x " + contra;
        if (l.shootout()) P += " (shoot out " + (lado == 0 ? r.penaltisMandante + " x " + r.penaltisVisitante : r.penaltisVisitante + " x " + r.penaltisMandante) + ")";
        Map<String, String> v = vars(eu.nome, adv.nome, nomeCurto(t), "P", P, "G", String.valueOf(pro), "S", String.valueOf(contra));
        double dif = forca(eu) - forca(adv);
        int margem = pro - contra;
        String tit;
        int peso;
        if (l.shootout()) {
            tit = venceu ? texto("kso#" + ch, v, "Nos shoot outs, {A} vence {oB}", "Frieza no shoot out: {A} bate {oB}", "{A} empata no tempo e leva a melhor no shoot out contra {oB}",
                    "Decisão no shoot out: deu {A}")
                    : texto("kso#" + ch, v, "{A} perde para {oB} no shoot out", "Shoot out cruel: {A} cai diante {doB}", "{A} empata no tempo, mas o shoot out é {doB}");
            peso = 68;
        } else if (l.matchBall() && l.liderAos36() == 1 - lado && venceu) {
            tit = texto("kmb#" + ch, v, "Virada no match ball: {A} bate {oB}", "{A} busca no match ball e vence {oB}", "Match ball dramático: {A} vira e vence {oB} por {P}");
            peso = 74;
        } else if (l.matchBall() && l.liderAos36() == lado && !venceu) {
            tit = texto("kmb#" + ch, v, "{A} cede a virada no match ball para {oB}", "Match ball cruel: {A} perde para {oB}", "{B} vira no match ball e derruba {oA}");
            peso = 72;
        } else if (venceu && margem >= 4) {
            tit = texto("kgo#" + ch, v, "{A} atropela {oB}: {P}", "Show {doA}: {P} sobre {oB}", "Goleada {noC}: {A} {P} {B}", "{A} passeia contra {oB}");
            peso = 70 + margem;
        } else if (!venceu && -margem >= 4) {
            tit = texto("kgo#" + ch, v, "Vexame: {A} leva {S} {doB}", "{A} é atropelado {peloB}: {G} x {S}", "Noite para esquecer: {B} {S} x {G} {A}");
            peso = 70 - margem;
        } else if (venceu && dif <= -4) {
            tit = texto("kze#" + ch, v, "Zebra! {A} derruba {oB}", "{A} surpreende {oB} e vence por {P}", "Vitória histórica {doA} sobre {oB}");
            peso = 66;
        } else if (!venceu && dif >= 4) {
            tit = texto("kze#" + ch, v, "Tropeço: {A} perde para {oB}", "{A} decepciona e cai diante {doB}", "Zebra contra {oA}: derrota para {oB}");
            peso = 62;
        } else {
            tit = venceu ? texto("kre#" + ch, v, "{A} vence {oB} por {P}", "Vitória {doA}: {P} sobre {oB}", "{A} bate {oB} {noC}", "{A} supera {oB} e soma mais uma")
                    : texto("kre#" + ch, v, "{A} perde para {oB}", "Derrota {doA} diante {doB}", "{B} vence {oA} por {S} x {G}", "{A} tropeça contra {oB}");
            peso = venceu ? 46 : 42;
        }
        nova("meu_time", 0, peso, "kjogo#" + ch, tit, linha, eu.id, t.id);
        // a caixa de entrada também recebe o placar (como o jogo do futebol)
        m.mensagem("resultado", nomeCurto(t), pl);

        // hat-trick (gols de jogo; o pênalti do presidente não conta)
        for (Map.Entry<String, Integer> e : l.golsPorAtleta().entrySet()) {
            if (e.getValue() < 3 || !e.getKey().startsWith(lado + "|")) continue;
            String quem = e.getKey().substring(2);
            Map<String, String> vj = vars(eu.nome, adv.nome, nomeCurto(t), "J", quem, "N", String.valueOf(e.getValue()));
            nova("meu_time", 0, 60 + e.getValue() * 3, "khat#" + ch + "#" + quem, texto("khat#" + ch + quem, vj,
                    "{J} marca {N} vezes contra {oB}", "Hat-trick de {J} {noC}", "Noite de {J}: {N} gols contra {oB}", "{J} brilha com {N} gols diante {doB}"), linha, eu.id, t.id);
        }
        // pênalti do presidente (o seu e o do adversário)
        for (int s = 0; s < 2; s++) {
            String res = l.presidente()[s];
            if (res == null) continue;
            LigaKings.TimeKings tk = s == 0 ? a : b;
            String pres = tk.presidente == null ? "o presidente" : tk.presidente;
            Map<String, String> vp = vars(tk.nome, (s == 0 ? b : a).nome, nomeCurto(t), "PR", pres);
            boolean meuPres = tk == eu;
            String tp = "GOL".equals(res)
                    ? texto("kpres#" + ch + s, vp, "Pênalti do presidente: {PR} bate e marca para {oA}", "{PR} vai para a bola e converte o pênalti do presidente",
                    "Presidente artilheiro: {PR} marca {noC}")
                    : texto("kpres#" + ch + s, vp, "{PR} perde o pênalti do presidente", "{PR} desperdiça o pênalti do presidente contra {oB}",
                    "Pênalti do presidente desperdiçado: {PR} não marca");
            nova("kings", meuPres ? 0 : 1, meuPres ? 54 : 44, "kpres#" + ch + "#" + s, tp, linha, tk.id, t.id);
        }
        // cartas usadas pelo seu time (e a do adversário, quando decide)
        for (String[] c : l.cartas()) {
            int s = Integer.parseInt(c[0]);
            LigaKings.TimeKings tk = s == 0 ? a : b, outro = s == 0 ? b : a;
            String nome = c[1].replace("Coringa → ", "");
            String res = c[2];
            Map<String, String> vc = vars(tk.nome, outro.nome, nomeCurto(t), "K", nome, "R", res == null ? "" : res);
            String tc;
            if (c[1].startsWith("Coringa →") && l.coringaRoubou() && tk == eu)
                tc = texto("kcar#" + ch + s, vc, "Coringa! {A} rouba a carta {doB} ({K})", "{A} usa o Coringa e fica com a carta {doB}");
            else if (nome.startsWith("Pênalti Invertido"))
                tc = "GOL".equals(res) ? texto("kcar#" + ch + s, vc, "Pênalti invertido {doA} não funciona: {oB} converte", "{B} converte o pênalti invertido {doA}")
                        : texto("kcar#" + ch + s, vc, "Pênalti invertido dá certo: {oB} erra e o gol é {doA}", "Carta certeira: {B} erra o pênalti invertido {doA}");
            else if (nome.startsWith("Pênalti"))
                tc = "GOL".equals(res) ? texto("kcar#" + ch + s, vc, "Carta Pênalti vira gol {doA}", "{A} usa a carta Pênalti e marca contra {oB}")
                        : "DEFENDEU".equals(res) ? texto("kcar#" + ch + s, vc, "Carta Pênalti {doA} para no goleiro {doB}", "Goleiro {doB} defende a carta Pênalti {doA}")
                        : texto("kcar#" + ch + s, vc, "{A} usa a carta Pênalti e chuta para fora", "Carta Pênalti desperdiçada: {A} manda para fora");
            else if (nome.startsWith("Jogador Estrela"))
                tc = texto("kcar#" + ch + s, vc, "{R} é o jogador estrela {doA}: os gols dele valem 2", "Carta Jogador Estrela: {A} aposta em {R}");
            else if (nome.startsWith("Suspensão"))
                tc = texto("kcar#" + ch + s, vc, "Carta Suspensão: {A} tira {R} {doB} por 4 minutos", "{A} usa a Suspensão e deixa {oB} com um a menos");
            else if (nome.startsWith("Shoot Out"))
                tc = texto("kcar#" + ch + s, vc, "{A} usa a carta Shoot Out contra {oB}", "Carta Shoot Out: {A} ganha uma cobrança contra {oB}");
            else if (nome.startsWith("Gol Duplo"))
                tc = texto("kcar#" + ch + s, vc, "{A} ativa o Gol Duplo contra {oB}", "Carta Gol Duplo: por 4 minutos, cada gol {doA} vale 2");
            else continue;
            nova("kings", tk == eu ? 0 : 1, tk == eu ? 50 : 40, "kcar#" + ch + "#" + s, tc, linha, tk.id, t.id);
        }
    }

    private void jogoDaLiga(Torneio t, Partida p, ResultadoPartida r, LigaKings.TimeKings a, LigaKings.TimeKings b, Leitura l) {
        if (l.vencedor() < 0) return;
        LigaKings.TimeKings w = l.vencedor() == 0 ? a : b, x = l.vencedor() == 0 ? b : a;
        String ch = t.id + "#" + t.ano + "#" + p.id;
        String linha = nomeCurto(t) + " · " + placar(a.nome, b.nome, r);
        int gw = l.vencedor() == 0 ? r.golsMandante : r.golsVisitante, gx = l.vencedor() == 0 ? r.golsVisitante : r.golsMandante;
        Map<String, String> v = vars(w.nome, x.nome, nomeCurto(t), "P", gw + " x " + gx);
        double gap = forca(x) - forca(w);
        if (gap >= 4) {
            nova("zebra", 1, 45 + (int) Math.round(gap * 2), "kzeb#" + ch, texto("kzeb#" + ch, v,
                    "Zebra {noC}: {A} derruba {oB}", "{A} surpreende e vence {oB}", "Deu zebra: {oB} perde para {oA}", "{A} faz história e bate {oB}"), linha, w.id, t.id);
        }
        if (l.matchBall() && l.liderAos36() == (l.vencedor() == 0 ? 1 : 0)) {
            nova("kings", 1, 50, "kmbl#" + ch, texto("kmbl#" + ch, v,
                    "Virada no match ball: {A} bate {oB}", "{A} vira no match ball e vence {oB}", "Match ball eletrizante: {A} supera {oB}"), linha, w.id, t.id);
        }
        if (gw - gx >= 6 && !l.shootout()) {
            nova("recorde", 1, 52 + gw - gx, "kgol#" + ch, texto("kgol#" + ch, v,
                    "Goleada {noC}: {A} {P} {B}", "{A} atropela {oB} por {P}"), linha, w.id, t.id);
        }
        if (l.coringaRoubou()) {
            for (String[] c : l.cartas()) if (c[1].startsWith("Coringa →")) {
                LigaKings.TimeKings tk = "0".equals(c[0]) ? a : b, outro = tk == a ? b : a;
                Map<String, String> vc = vars(tk.nome, outro.nome, nomeCurto(t), "K", c[1].replace("Coringa → ", ""));
                nova("kings", 1, 42, "kcor#" + ch, texto("kcor#" + ch, vc, "Coringa! {A} rouba a carta {doB}", "{A} usa o Coringa e leva a carta {doB} ({K})"), linha, tk.id, t.id);
            }
        }
        for (int s = 0; s < 2; s++) if ("GOL".equals(l.presidente()[s]) && Math.abs(r.golsMandante - r.golsVisitante) <= 1 && !l.shootout() && l.vencedor() == s) {
            LigaKings.TimeKings tk = s == 0 ? a : b;
            Map<String, String> vp = vars(tk.nome, (s == 0 ? b : a).nome, nomeCurto(t), "PR", tk.presidente == null ? "o presidente" : tk.presidente);
            nova("kings", 1, 46, "kprl#" + ch + s, texto("kprl#" + ch + s, vp,
                    "Gol do presidente decide: {PR} dá a vitória {aoA}", "{PR} marca o pênalti do presidente e {oA} vence por um gol"), linha, tk.id, t.id);
        }
    }

    // ================================================================ semana: tabela, liderança, playoffs
    /** Fim da semana de uma liga: liderança, briga pelo título e playoffs (só na liga do técnico). */
    public void semana(LigaKings liga) {
        try {
            if (liga == null || liga != minhaLiga()) return;
            Torneio t = liga.torneio;
            if (t == null) return;
            FaseExec f = t.faseAtual();
            if (f == null) return;
            String meu = meu();
            FaseExec liga0 = t.fases == null || t.fases.isEmpty() ? null : t.fases.get(0);
            // playoffs: só nos Splits (fase de liga em pontos corridos e depois o mata-mata; a Kings Cup tem grupos ou é só mata-mata)
            if (liga0 != null && liga0.concluida && f.mataMata() && !liga0.mataMata() && liga0.numGrupos <= 1 && t.id.contains("-split"))
                playoffs(liga, t, liga0, meu);
            if (f.mataMata() || f.numGrupos > 1) return;
            List<LinhaTabela> tab = t.tabela(f.indice, 0);
            if (tab.size() < 2 || tab.get(0).jogos == 0) return;
            LinhaTabela lider = tab.get(0), seg = tab.get(1);
            LigaKings.TimeKings tl = liga.time(lider.clubeId), ts = liga.time(seg.clubeId);
            if (tl == null || ts == null) return;
            String c = nomeCurto(t);
            // mudança de líder
            String kLider = "klid#" + t.id + "#" + t.ano;
            Integer antes = m.historia.pautas.get(kLider);
            int hash = tl.id.hashCode();
            if (antes == null || antes != hash) {
                m.historia.pautas.put(kLider, hash);
                if (lider.pontos > seg.pontos || lider.jogos >= 3) {
                    Map<String, String> v = vars(tl.nome, ts.nome, c, "N", String.valueOf(lider.pontos));
                    boolean eu = tl.id.equals(meu);
                    nova("tabela", eu ? 0 : 1, eu ? 66 : 52, kLider + "#" + m.semanaAbsoluta(), eu
                            ? texto(kLider + m.semana, v, "{A} assume a liderança {doC}", "{A} é o novo líder {doC}", "Topo da tabela: {A} lidera {oC}")
                            : texto(kLider + m.semana, v, "{A} assume a liderança {doC}", "Novo líder {noC}: {A}", "{A} passa {oB} e lidera {oC}"),
                            tl.nome + " " + lider.pontos + " pts · " + ts.nome + " " + seg.pontos + " pts", tl.id, t.id);
                }
            }
            // briga pelo título: faltando até 2 rodadas, líder com 1 ponto (ou menos) de vantagem
            int faltam = 0;
            for (Partida p : t.partidas) if (p.fase == f.indice && !p.jogada && p.envolve(tl.id)) faltam++;
            if (faltam > 0 && faltam <= 2 && lider.pontos - seg.pontos <= 1) {
                String k = "kbri#" + t.id + "#" + t.ano + "#" + faltam;
                Map<String, String> v = vars(tl.nome, ts.nome, c, "F", faltam == 1 ? "na última rodada" : "nas duas últimas rodadas");
                boolean eu = tl.id.equals(meu) || ts.id.equals(meu);
                nova("tabela", eu ? 0 : 1, eu ? 70 : 56, k, texto(k, v,
                        "Briga pela ponta {doC}: {A} e {B} decidem {F}", "{A} e {B} disputam a liderança {doC} {F}", "Liderança {doC} em aberto entre {A} e {B}"),
                        tl.nome + " " + lider.pontos + " · " + ts.nome + " " + seg.pontos, tl.id, t.id);
            }
            // o seu time na tabela (a cada 4 semanas), com a zona de playoffs
            if (meu != null && m.semana % 4 == 0) {
                int pos = 0;
                for (int i = 0; i < tab.size(); i++) if (tab.get(i).clubeId.equals(meu)) pos = i + 1;
                LigaKings.TimeKings eu = liga.time(meu);
                int vagas = f.def == null || f.def.classificam == null ? liga.regras.classificados_playoff : f.def.classificam;
                if (pos > 0 && eu != null && pos != 1) {
                    String k = "kpos#" + t.id + "#" + m.semanaAbsoluta();
                    Map<String, String> v = vars(eu.nome, null, c, "N", pos + "º", "V", String.valueOf(vars0(vagas)));
                    String tp = pos <= vagas ? texto(k, v, "{A} está em {N} {noC} e na zona de playoffs", "{N} lugar: {A} segue entre os {V} dos playoffs")
                            : texto(k, v, "{A} é {N} e está fora da zona de playoffs", "Alerta: {A} em {N}, fora dos {V} dos playoffs");
                    nova("tabela", 0, pos <= vagas ? 48 : 58, k, tp, null, eu.id, t.id);
                }
            }
        } catch (RuntimeException e) {
            // notícia nunca derruba a simulação
        }
    }

    private static int vars0(int n) {
        return Math.max(1, n);
    }

    private void playoffs(LigaKings liga, Torneio t, FaseExec fl, String meu) {
        String k = "kpo#" + t.id + "#" + t.ano;
        if (m.historia.pautas.containsKey(k)) return;
        m.historia.pautas.put(k, m.semanaAbsoluta());
        List<String> nomes = new ArrayList<>();
        for (String id : fl.classificados) {
            LigaKings.TimeKings x = liga.time(id);
            if (x != null) nomes.add(x.nome);
        }
        if (nomes.isEmpty()) return;
        Map<String, String> v = vars(null, null, nomeCurto(t), "L", lista(nomes));
        nova("tabela", 1, 58, k, texto(k, v, "Definidos os playoffs {doC}: {L}", "{L} vão aos playoffs {doC}"), null, null, t.id);
        LigaKings.TimeKings eu = meu == null ? null : liga.time(meu);
        if (eu != null) {
            boolean dentro = fl.classificados.contains(meu);
            Map<String, String> ve = vars(eu.nome, null, nomeCurto(t));
            nova("meu_time", 0, dentro ? 72 : 74, k + "#eu", dentro
                    ? texto(k + "eu", ve, "{A} está nos playoffs {doC}", "Classificado: {A} vai aos playoffs", "{A} garante vaga nos playoffs {doC}")
                    : texto(k + "eu", ve, "{A} fica fora dos playoffs {doC}", "Eliminado: {A} não vai aos playoffs", "Fim do sonho: {A} fora dos playoffs"), null, eu.id, t.id);
        }
    }

    static String lista(List<String> l) {
        if (l.isEmpty()) return "";
        if (l.size() == 1) return l.get(0);
        return String.join(", ", l.subList(0, l.size() - 1)) + " e " + l.get(l.size() - 1);
    }

    // ================================================================ títulos e Copas do Mundo
    /** Campeão de uma competição da Kings (Split, Kings Cup, Kings World Cup). */
    public void titulo(Torneio t, LigaKings.TimeKings campeao, LigaKings.TimeKings vice) {
        try {
            if (t == null || campeao == null) return;
            boolean mundial = t.id.startsWith("kings-mundial");
            LigaKings liga = null;
            if (m.kings != null) for (LigaKings l : m.kings) if (l.competicoes.contains(t)) liga = l;
            String meu = meu();
            int foco = foco(liga, t, campeao.id, vice == null ? null : vice.id);
            if (!carreira()) foco = 3;
            if (foco >= 2 && !mundial) return; // outras ligas: só as Copas do Mundo
            String k = "ktit#" + t.id + "#" + t.ano;
            String c = nomeCurto(t);
            Map<String, String> v = vars(campeao.nome, vice == null ? null : vice.nome, c, "T", String.valueOf(t.ano));
            long premio = DinheiroKings.premio(t.id);
            String linha = (vice == null ? c : "Vice: " + vice.nome) + (premio > 0 ? " · prêmio de " + DinheiroKings.reais(premio) : "");
            boolean meuCampeao = campeao.id.equals(meu), meuVice = vice != null && vice.id.equals(meu);
            if (meuCampeao) {
                nova("titulo", 0, 100, k, texto(k, v, "É campeão! {A} conquista {oC}", "{A} é campeão {doC}", "Taça na mão: {A} vence {oC}",
                        "Festa: {oA} levanta a taça {doC}"), linha, campeao.id, t.id);
            } else if (meuVice) {
                nova("titulo", 0, 84, k, texto(k, v, "{B} fica com o vice {doC}", "Quase: {B} perde o título {doC} para {oA}",
                        "{A} é campeão e {oB} fica com o vice"), linha, vice.id, t.id);
            } else {
                nova(foco >= 3 ? "mundo" : "titulo", foco, mundial ? 80 : 64, k, texto(k, v, "{A} é campeão {doC}", "{A} conquista {oC}",
                        "Título {doC} fica com {oA}", "{A} vence {oC} e soma mais uma taça"), linha, campeao.id, t.id);
            }
        } catch (RuntimeException e) {
            // notícia nunca derruba a simulação
        }
    }

    /** Começa a Kings World Cup Clubs (notícia para quem está na Kings; o seu time participando, foco 0). */
    public void mundialComeca(Torneio t) {
        if (!carreira() || t == null) return;
        String meu = meu();
        boolean dentro = t.participantes.contains(meu);
        String k = "kwc#" + t.id + "#" + t.ano;
        LigaKings.TimeKings eu = timeKings(meu);
        Map<String, String> v = vars(eu == null ? null : eu.nome, null, nomeCurto(t), "N", String.valueOf(t.participantes.size()));
        nova(dentro ? "meu_time" : "kings", dentro ? 0 : 2, dentro ? 70 : 50, k, dentro
                ? texto(k, v, "{A} está na {C}", "{A} disputa {oC} com {N} times", "Hora da Copa do Mundo: {A} entra na {C}")
                : texto(k, v, "Começa a {C} com {N} times", "{C}: {N} times em busca da taça"), "Prêmio ao campeão: " + DinheiroKings.reais(Kings.PREMIO_MUNDIAL),
                dentro ? meu : null, t.id);
    }

    private LigaKings.TimeKings timeKings(String id) {
        if (id == null || m.kings == null) return null;
        for (LigaKings l : m.kings) {
            LigaKings.TimeKings t = l.time(id);
            if (t != null) return t;
        }
        return null;
    }

    // ================================================================ draft, wildcards e técnicos
    /** Draft concluído: a 1ª escolha, os "steals" (escolha tardia de quem é forte) e as escolhas do seu time. */
    public void draft(LigaKings liga, LigaKings.EstadoDraft e) {
        try {
            if (liga != minhaLiga() || e == null || e.escolhas.isEmpty()) return;
            String meu = meu();
            String nomeLiga = liga.nome;
            List<LigaKings.Escolha> feitas = new ArrayList<>();
            for (LigaKings.Escolha x : e.escolhas) if (x.jogadorId != null) feitas.add(x);
            if (feitas.isEmpty()) return;
            LigaKings.Escolha primeira = feitas.get(0);
            String k = "kdr1#" + liga.id + "#" + e.ano;
            Map<String, String> v = vars(primeira.time, null, nomeLiga, "J", primeira.jogador);
            boolean eu = primeira.timeId.equals(meu);
            nova("mercado", eu ? 0 : 1, eu ? 70 : 56, k, texto(k, v, "{J} é a 1ª escolha do draft {doC}", "Draft {doC}: {A} abre com {J}",
                    "{A} escolhe {J} com a 1ª escolha do draft"), "Draft " + e.ano, primeira.timeId, null);
            // steal: escolhido na 2ª metade do draft com força entre as melhores escolhas
            List<double[]> forcas = new ArrayList<>();
            for (LigaKings.Escolha x : feitas) forcas.add(new double[]{forcaDraftado(liga, x)});
            double[] ord = forcas.stream().mapToDouble(d -> d[0]).sorted().toArray();
            double corte = ord[(int) Math.floor(ord.length * 0.8)];
            int steals = 0;
            for (int i = feitas.size() / 2; i < feitas.size() && steals < 2; i++) {
                LigaKings.Escolha x = feitas.get(i);
                if (forcas.get(i)[0] < corte || forcas.get(i)[0] <= 0) continue;
                steals++;
                boolean dele = x.timeId.equals(meu);
                String ks = "kdrs#" + liga.id + "#" + e.ano + "#" + x.jogadorId;
                Map<String, String> vs = vars(x.time, null, nomeLiga, "J", x.jogador, "N", String.valueOf(x.numero));
                nova("mercado", dele ? 0 : 1, dele ? 64 : 50, ks, texto(ks, vs, "Steal do draft: {A} fica com {J} na {N}ª escolha",
                        "{J} sobra até a {N}ª escolha e vai para {oA}", "Achado do draft: {J} vai para {oA}"), "Draft " + e.ano, x.timeId, null);
            }
            // as escolhas do seu time
            if (meu != null) {
                List<String> minhas = new ArrayList<>();
                for (LigaKings.Escolha x : feitas) if (x.timeId.equals(meu)) minhas.add(x.jogador);
                LigaKings.TimeKings tk = liga.time(meu);
                if (!minhas.isEmpty() && tk != null) {
                    String km = "kdrm#" + liga.id + "#" + e.ano;
                    Map<String, String> vm = vars(tk.nome, null, nomeLiga, "L", lista(minhas), "N", String.valueOf(minhas.size()));
                    nova("meu_time", 0, 60, km, texto(km, vm, "{A} fecha o draft com {N} reforços", "Draft {doA}: {L}", "Os draftados {doA}: {L}"),
                            lista(minhas), tk.id, null);
                }
            }
        } catch (RuntimeException ex) {
            // notícia nunca derruba a simulação
        }
    }

    private double forcaDraftado(LigaKings liga, LigaKings.Escolha x) {
        LigaKings.TimeKings t = liga.time(x.timeId);
        if (t == null) return 0;
        for (LigaKings.AtletaKings a : t.elenco) if (x.jogadorId.equals(a.jogadorId)) return a.forca;
        Jogador j = m.jogador(x.jogadorId);
        return j == null ? 0 : Kings.escalaKings(j.forca);
    }

    /** Wildcard contratado: o seu (sempre) e os de peso da sua liga (fama ou força). */
    public void wildcard(LigaKings liga, LigaKings.TimeKings t, Jogador j, long custo) {
        try {
            if (liga != minhaLiga() || t == null || j == null) return;
            boolean eu = t.id.equals(meu());
            if (!eu && j.fama < 40 && j.forca < 72 && !j.estrela) return;
            String k = "kwil#" + t.id + "#" + j.id + "#" + m.ano;
            Map<String, String> v = vars(t.nome, null, liga.nome, "J", j.nomeExibicao(), "V", DinheiroKings.reais(custo));
            nova("mercado", eu ? 0 : 1, eu ? 62 : 48 + (int) Math.min(20, j.fama / 4), k, eu
                    ? texto(k, v, "{J} é o novo wildcard {doA}", "{A} anuncia {J} como wildcard", "Reforço: {J} acerta com {oA}")
                    : texto(k, v, "{A} contrata {J} como wildcard", "{J} é wildcard {doA}", "Wildcard de peso: {J} vai para {oA}"),
                    "Custo: " + v.get("V"), t.id, null);
        } catch (RuntimeException e) {
            // notícia nunca derruba a simulação
        }
    }

    /** Time da IA da sua liga troca de técnico (virada da temporada, entre os piores). */
    public void tecnicoDemitido(LigaKings liga, LigaKings.TimeKings tk, boolean ultimo) {
        if (liga != minhaLiga() || tk == null) return;
        String k = "kdem#" + tk.id + "#" + m.semanaAbsoluta();
        Map<String, String> v = vars(tk.nome, null, liga.nome, "PR", tk.presidente == null ? "o presidente" : tk.presidente);
        nova("tecnicos", 1, ultimo ? 60 : 54, k, texto(k, v, "{A} demite o técnico", "{PR} troca o comando {doA}", "Mudança no banco {doA}",
                "Fim da linha: {A} fica sem técnico"), ultimo ? "Último colocado no Split anterior" : "Campanha ruim no Split anterior", tk.id, null);
    }

    /** O técnico humano foi demitido do time da Kings. */
    public void humanoDemitido(LigaKings.TimeKings tk, String motivo) {
        if (m.tecnico == null || tk == null) return;
        String k = "kdemh#" + tk.id + "#" + m.semanaAbsoluta();
        Map<String, String> v = vars(tk.nome, null, null, "T", m.tecnico.nome, "PR", tk.presidente == null ? "O presidente" : tk.presidente);
        nova("tecnicos", 0, 98, k, texto(k, v, "{A} demite {T}", "{T} é demitido {doA}", "{PR} decide: {T} deixa o comando {doA}"), motivo, tk.id, null);
    }
}
