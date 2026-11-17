package br.futebas.ui.modelo;

import br.futebas.competicao.Confronto;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mata-mata "como está agora": o agregado de cada confronto da rodada com os placares parciais, quem
 * passa se acabar assim e do que um time precisa ("precisa de 1 gol"). Mesmas regras do
 * {@code Torneio} (agregado, gol fora quando a fase usa, desempate). Não depende de JavaFX.
 */
public final class MataMataAoVivo {

    /**
     * Um confronto agora. {@code golsA}/{@code golsB}: agregado de {@code a} e {@code b} (com o placar
     * parcial); {@code ida}: gols de a e b nas pernas já jogadas antes desta (null se jogo único ou 1ª perna);
     * {@code decisivo}: esta é a última perna; {@code passa}: quem avança se acabar assim (null =
     * desempate: pênaltis ou prorrogação; também null fora da perna decisiva).
     */
    public record Situacao(String a, String b, int golsA, int golsB, int pernas, boolean decisivo, int[] ida, String passa,
                           String desempate) {
        public boolean envolve(String id) { return id != null && (id.equals(a) || id.equals(b)); }
    }

    private final Torneio torneio;
    private final FaseExec fase;
    private final Partida minha;

    private MataMataAoVivo(Torneio t, FaseExec f, Partida minha) {
        this.torneio = t;
        this.fase = f;
        this.minha = minha;
    }

    /** O mata-mata do jogo {@code minha}, ou null se a fase não é de mata-mata. */
    public static MataMataAoVivo de(Torneio t, Partida minha) {
        if (t == null || minha == null || minha.fase < 0 || minha.fase >= t.fases.size()) return null;
        FaseExec f = t.fases.get(minha.fase);
        if (!f.mataMata() || minha.confronto < 0 || minha.confronto >= f.confrontos.size()) return null;
        return new MataMataAoVivo(t, f, minha);
    }

    /** Nome da fase (ex.: "Oitavas de final"). */
    public String nomeFase() { return fase.nome; }

    /**
     * Os confrontos desta rodada com placar ao vivo ({mandante, visitante, gols m, gols v}), o do
     * jogo acompanhado primeiro.
     */
    public List<Situacao> agora(List<Object[]> placares) {
        Map<Integer, Situacao> r = new LinkedHashMap<>();
        Situacao mia = situacao(minha, placarDe(placares, minha.mandante, minha.visitante));
        if (mia != null) r.put(minha.confronto, mia);
        for (Object[] p : placares) {
            Partida q = partidaDaRodada((String) p[0], (String) p[1]);
            if (q == null || r.containsKey(q.confronto)) continue;
            Situacao s = situacao(q, new int[] {(Integer) p[2], (Integer) p[3]});
            if (s != null) r.put(q.confronto, s);
        }
        return new ArrayList<>(r.values());
    }

    /** Do que {@code clube} precisa no confronto, em poucas palavras ("Precisa de 1 gol", "Classificado"). */
    public String precisa(Situacao s, String clube) {
        if (s == null || !s.envolve(clube)) return "";
        boolean dois = s.pernas() > 1;
        int meu = clube.equals(s.a()) ? s.golsA() : s.golsB(), dele = clube.equals(s.a()) ? s.golsB() : s.golsA();
        if (!s.decisivo()) {
            if (meu == dele) return "Empate na ida: tudo aberto para a volta";
            return (meu > dele ? "Leva vantagem de " : "Perde por ") + gols(Math.abs(meu - dele)) + " para a volta";
        }
        if (clube.equals(s.passa())) return dois ? "Classificado pelo agregado" : "Classificado se acabar assim";
        if (s.passa() == null) return (dois ? "Agregado empatado: " : "Empate: ") + s.desempate();
        // quantos gols a mais na perna de hoje levam ao desempate e à vaga
        Partida q = partidaDaRodada(s.a(), s.b());
        if (q == null) q = minha;
        int[] base = placarParcial(s, q);
        int ladoMeu = clube.equals(q.mandante) ? 0 : 1;
        Integer empata = null;
        for (int k = 1; k <= 12; k++) {
            int[] p = base.clone();
            p[ladoMeu] += k;
            Situacao x = situacao(q, p);
            if (x == null) break;
            if (clube.equals(x.passa())) {
                if (empata != null && empata < k) return "Precisa de " + gols(empata) + " para levar aos " + s.desempate() + ", " + k + " para passar";
                return "Precisa de " + gols(k);
            }
            if (x.passa() == null && empata == null) empata = k;
        }
        return "Eliminado se acabar assim";
    }

    private static String gols(int n) { return n + (n == 1 ? " gol" : " gols"); }

    /** O placar parcial da perna de hoje de um confronto (recuperado do agregado). */
    private int[] placarParcial(Situacao s, Partida q) {
        int[] antes = antesDesta(q);
        // antes[0] = gols de "a" nas pernas anteriores, antes[1] = de "b"
        Confronto c = fase.confrontos.get(q.confronto);
        int hojeA = s.golsA() - antes[0], hojeB = s.golsB() - antes[1];
        return q.mandante.equals(c.a) ? new int[] {hojeA, hojeB} : new int[] {hojeB, hojeA};
    }

    private static int[] placarDe(List<Object[]> placares, String m, String v) {
        for (Object[] p : placares) if (m.equals(p[0]) && v.equals(p[1])) return new int[] {(Integer) p[2], (Integer) p[3]};
        return new int[2];
    }

    /** A partida desta rodada do mata-mata com esse mandante e visitante (null = não é desta fase/rodada). */
    private Partida partidaDaRodada(String m, String v) {
        for (Partida p : fase.partidas) {
            if (p.rodadaMata == minha.rodadaMata && p.perna == minha.perna && m.equals(p.mandante) && v.equals(p.visitante)
                    && p.confronto >= 0 && p.confronto < fase.confrontos.size()) return p;
        }
        return null;
    }

    private Partida partida(int id) {
        if (id >= 0 && id < torneio.partidas.size() && torneio.partidas.get(id).id == id) return torneio.partidas.get(id);
        for (Partida p : torneio.partidas) if (p.id == id) return p;
        return null;
    }

    /** Gols de a e b (do confronto) nas pernas anteriores à de {@code q}, e os gols fora de cada um. */
    private int[] antesDesta(Partida q) {
        Confronto c = fase.confrontos.get(q.confronto);
        int[] r = new int[4];
        for (int pid : c.partidas) {
            Partida p = partida(pid);
            if (p == null || p.perna >= q.perna || !p.jogada || p.golsMandante == null) continue;
            somar(r, c, p.mandante, p.golsMandante, p.golsVisitante);
        }
        return r;
    }

    /** Soma um jogo em r = {gols a, gols b, fora a, fora b}. */
    private static void somar(int[] r, Confronto c, String mandante, int gm, int gv) {
        if (mandante.equals(c.a)) {
            r[0] += gm;
            r[1] += gv;
            r[3] += gv;
        } else {
            r[1] += gm;
            r[0] += gv;
            r[2] += gv;
        }
    }

    private Situacao situacao(Partida q, int[] placar) {
        if (q == null || q.confronto < 0 || q.confronto >= fase.confrontos.size()) return null;
        Confronto c = fase.confrontos.get(q.confronto);
        if (c.a == null || c.b == null) return null;
        int[] ag = antesDesta(q);
        int[] ida = q.perna > 0 ? new int[] {ag[0], ag[1]} : null;
        somar(ag, c, q.mandante, placar[0], placar[1]);
        int pernas = Math.max(1, c.partidas.size());
        boolean decisivo = q.perna >= pernas - 1;
        String des = fase.def == null || fase.def.desempate == null ? "penaltis" : fase.def.desempate;
        String textoDes = des.startsWith("prorrogacao") ? "prorrogação" : "pênaltis";
        String passa = null;
        if (decisivo) {
            if (ag[0] != ag[1]) passa = ag[0] > ag[1] ? c.a : c.b;
            else if (pernas > 1 && fase.def != null && Boolean.TRUE.equals(fase.def.gol_fora) && ag[2] != ag[3]) passa = ag[2] > ag[3] ? c.a : c.b;
            else if (des.equals("melhor_campanha")) passa = c.a;
            else if (des.equals("visitante") && pernas == 1) passa = q.visitante;
        }
        return new Situacao(c.a, c.b, ag[0], ag[1], pernas, decisivo, ida, passa, textoDes);
    }
}
