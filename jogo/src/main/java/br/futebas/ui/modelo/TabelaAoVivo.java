package br.futebas.ui.modelo;

import br.futebas.competicao.FaseExec;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabela de um grupo "como está agora": a tabela de antes da rodada (sem os jogos da mesma data,
 * mesmo os que o motor já jogou) mais os placares parciais. Não depende de JavaFX.
 */
public final class TabelaAoVivo {
    /** Uma linha: a campanha parcial, a posição agora e a de antes da rodada (1 = líder). */
    public record Linha(LinhaTabela l, int posicao, int antes) {
        public int variacao() { return antes - posicao; }
    }

    public final Torneio torneio;
    public final FaseExec fase;
    public final int grupo;
    /** Rodada da fase (jogos da campanha mais longa antes desta data, mais um). */
    public final int rodada;
    private final Map<String, LinhaTabela> antes = new LinkedHashMap<>();
    private final Map<String, Integer> posAntes = new HashMap<>();

    private TabelaAoVivo(Torneio t, FaseExec f, int grupo, Partida minha) {
        this.torneio = t;
        this.fase = f;
        this.grupo = grupo;
        List<LinhaTabela> atual = t.tabela(f.indice, grupo);
        for (LinhaTabela l : atual) antes.put(l.clubeId, copia(l));
        // tira os jogos desta data que o motor já registrou
        for (Partida p : f.partidas) {
            if (p.grupo != grupo || p.data != minha.data || !p.jogada || p.golsMandante == null) continue;
            aplicar(p, p.mandante, p.visitante, p.golsMandante, p.golsVisitante, -1);
        }
        List<LinhaTabela> ord = ordenar(new ArrayList<>(antes.values()), null);
        int max = 0;
        for (int i = 0; i < ord.size(); i++) {
            posAntes.put(ord.get(i).clubeId, i + 1);
            max = Math.max(max, ord.get(i).jogos);
        }
        rodada = max + 1;
    }

    /** A tabela ao vivo do jogo {@code minha}, ou null se ele não é de fase de pontos. */
    public static TabelaAoVivo de(Torneio t, Partida minha) {
        if (t == null || minha == null || minha.fase < 0 || minha.fase >= t.fases.size()) return null;
        FaseExec f = t.fases.get(minha.fase);
        if (f.mataMata()) return null;
        try {
            return new TabelaAoVivo(t, f, Math.max(0, minha.grupo), minha);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public boolean contem(String clubeId) { return antes.containsKey(clubeId); }

    /** Classificação com os placares parciais ({mandante, visitante, gols m, gols v}). */
    public List<Linha> agora(List<Object[]> placares) {
        Map<String, LinhaTabela> m = new LinkedHashMap<>();
        for (LinhaTabela l : antes.values()) m.put(l.clubeId, copia(l));
        for (Object[] p : placares) {
            String a = (String) p[0], b = (String) p[1];
            LinhaTabela la = m.get(a), lb = m.get(b);
            if (la == null || lb == null) continue;
            int ga = (Integer) p[2], gb = (Integer) p[3];
            la.registrar(ga, gb, false, torneio.pontosVitoria, 0);
            lb.registrar(gb, ga, true, torneio.pontosVitoria, 0);
        }
        List<LinhaTabela> ord = ordenar(new ArrayList<>(m.values()), posAntes);
        List<Linha> r = new ArrayList<>();
        for (int i = 0; i < ord.size(); i++) {
            LinhaTabela l = ord.get(i);
            r.add(new Linha(l, i + 1, posAntes.getOrDefault(l.clubeId, i + 1)));
        }
        return r;
    }

    private void aplicar(Partida p, String a, String b, int ga, int gb, int sinal) {
        LinhaTabela la = antes.get(a), lb = antes.get(b);
        if (la == null || lb == null) return;
        // shootout (Kings, sem empate): a tabela já contou vitória/derrota com os pontos próprios
        Boolean mVence = torneio.penaltisNoEmpate && p.penaltisMandante != null && ga == gb && p.rodadaMata < 0
                ? p.penaltisMandante > p.penaltisVisitante : null;
        tirar(la, ga, gb, mVence == null ? null : mVence);
        tirar(lb, gb, ga, mVence == null ? null : !mVence);
    }

    private void tirar(LinhaTabela l, int pro, int contra, Boolean venceuShootout) {
        if (venceuShootout != null) {
            l.jogos--;
            l.golsPro -= pro;
            l.golsContra -= contra;
            if (venceuShootout) {
                l.vitorias--;
                l.vitoriasPenaltis--;
                l.pontos -= torneio.pontosVitoriaPenaltis;
            } else {
                l.derrotas--;
                l.derrotasPenaltis--;
                l.pontos -= torneio.pontosDerrotaPenaltis;
            }
            return;
        }
        l.jogos--;
        l.golsPro -= pro;
        l.golsContra -= contra;
        if (pro > contra) {
            l.vitorias--;
            l.pontos -= torneio.pontosVitoria;
        } else if (pro == contra) {
            l.empates--;
            l.pontos -= 1;
        } else {
            l.derrotas--;
        }
    }

    private static List<LinhaTabela> ordenar(List<LinhaTabela> ls, Map<String, Integer> desempate) {
        Comparator<LinhaTabela> c = Comparator.comparingInt((LinhaTabela l) -> -l.pontos)
                .thenComparingInt(l -> -l.vitorias)
                .thenComparingInt(l -> -l.saldo())
                .thenComparingInt(l -> -l.golsPro);
        if (desempate != null) c = c.thenComparingInt(l -> desempate.getOrDefault(l.clubeId, 999));
        ls.sort(c);
        return ls;
    }

    private static LinhaTabela copia(LinhaTabela o) {
        LinhaTabela l = new LinhaTabela(o.clubeId);
        l.jogos = o.jogos;
        l.vitorias = o.vitorias;
        l.empates = o.empates;
        l.derrotas = o.derrotas;
        l.golsPro = o.golsPro;
        l.golsContra = o.golsContra;
        l.pontos = o.pontos;
        l.cartoes = o.cartoes;
        l.golsFora = o.golsFora;
        l.vitoriasPenaltis = o.vitoriasPenaltis;
        l.derrotasPenaltis = o.derrotasPenaltis;
        return l;
    }
}
