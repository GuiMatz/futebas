package br.futebas.competicao;

import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.dados.Formato;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Rng;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Libertadores e Sul-Americana no formato real (CONMEBOL 2026; fontes em docs/fontes.md, seção
 * "Formato CONMEBOL 2026"). O banco tem as fases, mas não liga as duas competições nem diz em que fase
 * cada vaga entra; este é o ajuste do motor.
 *
 * <p><b>Libertadores</b> (47): 28 direto nos grupos (os 2 campeões, ARG 5, BRA 5, os outros 8 países 2 cada),
 * 13 na 2ª fase (ARG 1, BRA 2, CHL 2, COL 2, os outros 1) e 6 na 1ª fase (BOL, ECU, PRY, PER, URY, VEN 1).
 * 1ª, 2ª e 3ª fases em ida e volta; 8 grupos de 4 (sem dois do mesmo país); 1º e 2º às oitavas (1ºs × 2ºs,
 * o 1º decide em casa); chave fixa até a final única. <b>Perdedores da 3ª fase → grupos da Sul-Americana;
 * 3ºs dos grupos → playoffs da Sul-Americana.</b>
 *
 * <p><b>Sul-Americana</b> (44 + 12 da Libertadores = 56): ARG 6 e BRA 6 direto nos grupos; os outros 8
 * países 4 cada na 1ª fase (jogo único entre clubes do mesmo país, 16 confrontos). Grupos: 16 da 1ª fase +
 * 12 + 4 eliminados da 3ª fase da Libertadores, 8 grupos de 4; só o 1º vai direto às oitavas; o 2º joga o
 * playoff (ida e volta, chave fixa 9×24...16×17) contra os 3ºs da Libertadores; oitavas (1ºs de grupo ×
 * vencedores do playoff, sorteio), quartas e semifinal em ida e volta, final única em sede neutra.
 * Sem gol fora; empate no agregado → pênaltis; prorrogação só na final.
 *
 * <p>Vagas por país pela tabela da liga do ano anterior (campeão, depois o campeão da copa nacional,
 * depois os seguintes da tabela), como o motor faz nas outras copas; o 1º ano usa as listas reais do banco.
 */
public final class Conmebol {
    private Conmebol() {}

    public static final String LIBERTADORES = "conmebol-libertadores", SUL_AMERICANA = CompeticoesFicticias.SUL_AMERICANA;
    static final String[] PAISES = {"ARG", "BRA", "BOL", "CHL", "COL", "ECU", "PRY", "PER", "URY", "VEN"};

    /** Libertadores 2026 por país: {grupos, 2ª fase, 1ª fase} (sem os dois campeões, que vão aos grupos). */
    public static final Map<String, int[]> VAGAS_LIBERTADORES = new LinkedHashMap<>();
    /** Sul-Americana 2026 por país: ARG e BRA entram nos grupos, os outros na 1ª fase. */
    public static final Map<String, Integer> VAGAS_SUL_AMERICANA = new LinkedHashMap<>();

    static {
        VAGAS_LIBERTADORES.put("ARG", new int[]{5, 1, 0});
        VAGAS_LIBERTADORES.put("BRA", new int[]{5, 2, 0});
        VAGAS_LIBERTADORES.put("BOL", new int[]{2, 1, 1});
        VAGAS_LIBERTADORES.put("CHL", new int[]{2, 2, 0});
        VAGAS_LIBERTADORES.put("COL", new int[]{2, 2, 0});
        VAGAS_LIBERTADORES.put("ECU", new int[]{2, 1, 1});
        VAGAS_LIBERTADORES.put("PRY", new int[]{2, 1, 1});
        VAGAS_LIBERTADORES.put("PER", new int[]{2, 1, 1});
        VAGAS_LIBERTADORES.put("URY", new int[]{2, 1, 1});
        VAGAS_LIBERTADORES.put("VEN", new int[]{2, 1, 1});
        for (String p : PAISES) VAGAS_SUL_AMERICANA.put(p, p.equals("ARG") || p.equals("BRA") ? 6 : 4);
    }

    public static final int LIB_GRUPOS = 28, LIB_SEGUNDA = 13, LIB_PRIMEIRA = 6;
    public static final int SULA_GRUPOS = 12, SULA_PRIMEIRA = 32, SULA_DA_LIB_GRUPOS = 4, SULA_DA_LIB_PLAYOFF = 8;

    /** Janela de cada fase {mês, dia, mês, dia} (calendário real de 2026, repetido ano a ano). */
    static final int[][] DATAS_LIBERTADORES = {{2, 3, 2, 12}, {2, 17, 2, 26}, {3, 3, 3, 12}, {4, 7, 5, 28},
            {8, 11, 8, 20}, {9, 8, 9, 17}, {10, 14, 10, 22}, {11, 28, 11, 28}};
    static final int[][] DATAS_SUL_AMERICANA = {{3, 3, 3, 5}, {4, 7, 5, 28}, {7, 21, 7, 30},
            {8, 11, 8, 20}, {9, 8, 9, 17}, {10, 13, 10, 21}, {11, 21, 11, 21}};

    public static boolean trata(String competicaoId) {
        return LIBERTADORES.equals(competicaoId) || SUL_AMERICANA.equals(competicaoId);
    }

    // ================================================================== formatos
    /** O formato do banco com as fases no formato real (desempates e vagas do banco mantidos). */
    public static Formato ajustar(String competicaoId, Formato banco) {
        Formato f = new Formato();
        if (banco != null) {
            f.pontos_vitoria = banco.pontos_vitoria;
            f.desempates = banco.desempates;
            f.vagas = banco.vagas;
            f.observacoes = banco.observacoes;
        }
        if (LIBERTADORES.equals(competicaoId)) {
            f.participantes = LIB_GRUPOS + LIB_SEGUNDA + LIB_PRIMEIRA;
            Formato.Fase p1 = mata("Primeira fase", true, "sorteio");
            p1.classificam = 3;
            Formato.Fase p2 = mata("Segunda fase", true, "sorteio");
            p2.entram = LIB_SEGUNDA;
            p2.classificam = 8;
            Formato.Fase p3 = mata("Terceira fase", true, "sorteio");
            p3.classificam = 4;
            Formato.Fase g = grupos();
            g.entram = LIB_GRUPOS;
            f.fases = new ArrayList<>(List.of(p1, p2, p3, g, mata("Oitavas de final", true, "potes"), mata("Quartas de final", true, "chave"),
                    mata("Semifinal", true, "chave"), finalUnica()));
        } else {
            f.participantes = SULA_GRUPOS + SULA_PRIMEIRA;
            Formato.Fase p1 = mata("Primeira fase", false, "pares");
            p1.classificam = SULA_PRIMEIRA / 2;
            p1.mando_melhor_campanha = false;
            p1.observacoes = "Jogo único entre clubes do mesmo país (fora ARG e BRA); manda o 1º sorteado; empate → pênaltis.";
            Formato.Fase g = grupos();
            g.entram = SULA_GRUPOS;
            g.entram_externos = SULA_DA_LIB_GRUPOS;
            g.origem_externos = LIBERTADORES + ":eliminados_previa";
            g.classificam_direto = 1;
            g.observacoes = "16 da 1ª fase + ARG 6 + BRA 6 + 4 eliminados da 3ª fase da Libertadores. 1º → oitavas; 2º → playoffs.";
            Formato.Fase po = mata("Playoffs das oitavas", true, "1x8");
            po.entram_externos = SULA_DA_LIB_PLAYOFF;
            po.origem_externos = LIBERTADORES + ":terceiros_grupos";
            po.classificam = 8;
            po.observacoes = "2ºs de grupo (decidem em casa) × 3ºs dos grupos da Libertadores, chave fixa pela campanha (9×24 ... 16×17).";
            f.fases = new ArrayList<>(List.of(p1, g, po, mata("Oitavas de final", true, "potes"), mata("Quartas de final", true, "chave"),
                    mata("Semifinal", true, "chave"), finalUnica()));
        }
        return f;
    }

    private static Formato.Fase mata(String nome, boolean idaVolta, String chaveamento) {
        Formato.Fase f = new Formato.Fase(nome, "mata_mata");
        f.ida_volta = idaVolta;
        f.gol_fora = false;
        f.desempate = "penaltis";
        f.mando_melhor_campanha = true;
        f.chaveamento = chaveamento;
        return f;
    }

    private static Formato.Fase grupos() {
        Formato.Fase g = new Formato.Fase("Fase de grupos", "grupos");
        g.grupos = 8;
        g.turnos = 2;
        g.classificam = 2;
        g.chaveamento = "sorteio";
        g.separar_paises = true;
        return g;
    }

    private static Formato.Fase finalUnica() {
        Formato.Fase f = mata("Final", false, "chave");
        f.desempate = "prorrogacao_penaltis";
        f.mando_melhor_campanha = false;
        f.observacoes = "Jogo único em sede definida pela CONMEBOL; empate → prorrogação → pênaltis.";
        return f;
    }

    // ================================================================== calendário
    /** Datas de cada fase no calendário real (a Sul-Americana precisa dos grupos da Libertadores acabados antes do playoff). */
    static void calendario(Torneio t, int ano) {
        int[][] datas = LIBERTADORES.equals(t.id) ? DATAS_LIBERTADORES : DATAS_SUL_AMERICANA;
        if (t.fases.size() != datas.length || t.totalDatas <= 0) return;
        List<Integer> livres = t.semanasJogo;
        List<Integer> res = new ArrayList<>();
        for (int i = 0; i < t.totalDatas; i++) res.add(0);
        for (FaseExec f : t.fases) {
            if (f.numDatas <= 0) continue;
            int[] j = datas[f.indice];
            int de = Calendario.absoluta(LocalDate.of(ano, j[0], j[1])), ate = Math.max(de, Calendario.absoluta(LocalDate.of(ano, j[2], j[3])));
            List<Integer> w = new ArrayList<>();
            for (int s : livres) if (s >= de && s <= ate) w.add(s);
            if (w.isEmpty()) for (int s = de; s <= ate; s++) w.add(s);
            for (int i = 0; i < f.numDatas; i++) res.set(f.dataInicial + i, w.get((int) ((long) i * w.size() / f.numDatas)));
        }
        for (int d = 1; d < res.size(); d++) if (res.get(d) < res.get(d - 1)) res.set(d, res.get(d - 1));
        t.semanasDatas = res;
        t.semanaInicio = res.get(0);
        t.semanaFim = Math.max(t.semanaFim, res.get(res.size() - 1));
        t.reprogramarPartidas();
    }

    // ================================================================== participantes
    /**
     * Fila de cada país para as vagas: no 1º ano os clubes da lista real do banco (na ordem do ranking
     * nacional); depois o campeão da liga, o campeão da copa nacional e o resto da tabela da liga do ano
     * anterior; por fim o ranking nacional (divisão, posição, reputação).
     */
    private static List<String> fila(Mundo m, MontadorTemporada mt, BancoMundo db, String pais, List<String> real, boolean inicial) {
        LinkedHashSet<String> l = new LinkedHashSet<>();
        List<Clube> ranking = mt.rankingNacional(pais);
        if (inicial) {
            for (Clube c : ranking) if (real.contains(c.id)) l.add(c.id);
        } else {
            List<String> tabela = new ArrayList<>();
            for (Db.Competicao c : db.competicoes.values()) {
                if (!"nacional".equals(c.ambito()) || !pais.equals(c.pais()) || c.divisao() == null || c.divisao() != 1) continue;
                Torneio t = m.torneioAnterior(c.id());
                if (t != null && t.encerrado) tabela = t.classificacao;
            }
            Torneio copa = m.torneioAnterior(pais.toLowerCase(java.util.Locale.ROOT) + "-copa");
            if (!tabela.isEmpty()) l.add(tabela.get(0));
            if (copa != null && copa.encerrado && copa.campeao != null && m.clube(copa.campeao) != null) l.add(copa.campeao);
            l.addAll(tabela);
        }
        for (Clube c : ranking) if (c.divisao == 1) l.add(c.id);
        for (Clube c : ranking) l.add(c.id);
        List<String> r = new ArrayList<>();
        for (String id : l) {
            Clube c = m.clube(id);
            if (c != null && c.existe && !c.virtual && pais.equals(c.pais)) r.add(id);
        }
        return r;
    }

    /** Tira {@code n} da fila (sem quem já está em {@code fora}). */
    private static List<String> tirar(List<String> fila, int n, Set<String> fora) {
        List<String> l = new ArrayList<>();
        for (String id : fila) {
            if (l.size() >= n) break;
            if (fora.add(id)) l.add(id);
        }
        return l;
    }

    /** Completa vagas que um país não preencheu com os clubes mais fortes da CONMEBOL que sobraram. */
    private static void completar(Mundo m, List<String> l, int n, Set<String> fora) {
        if (l.size() >= n) return;
        List<Clube> pool = new ArrayList<>();
        for (Clube c : m.clubes.values()) if (c.existe && !c.virtual && "CONMEBOL".equalsIgnoreCase(c.confederacao) && !fora.contains(c.id)) pool.add(c);
        pool.sort(Comparator.comparingDouble((Clube c) -> -c.reputacao).thenComparing(c -> c.id));
        for (Clube c : pool) {
            if (l.size() >= n) break;
            if (fora.add(c.id)) l.add(c.id);
        }
    }

    private static void porForca(Mundo m, List<String> l) {
        l.sort(Comparator.comparingDouble((String id) -> -m.clube(id).reputacao).thenComparing(id -> id));
    }

    /**
     * Libertadores: [28 dos grupos][13 da 2ª fase][6 da 1ª fase], cada bloco do mais forte para o mais fraco
     * (a ordem que as entradas escalonadas do {@link Torneio} esperam).
     */
    static List<String> participantesLibertadores(Mundo m, MontadorTemporada mt, BancoMundo db, boolean inicial, Set<String> jaContinental) {
        Set<String> fora = new java.util.HashSet<>(jaContinental);
        List<String> real = db.participantesIniciais(LIBERTADORES);
        List<String> grupos = new ArrayList<>(), segunda = new ArrayList<>(), primeira = new ArrayList<>();
        if (!inicial) {
            // os dois campeões (Libertadores e Sul-Americana) vão direto aos grupos, fora das vagas do país
            for (String comp : List.of(LIBERTADORES, SUL_AMERICANA)) {
                Torneio t = m.ultimoEncerrado(comp);
                if (t != null && t.campeao != null && m.clube(t.campeao) != null && m.clube(t.campeao).existe && fora.add(t.campeao)) grupos.add(t.campeao);
            }
        }
        for (Map.Entry<String, int[]> e : VAGAS_LIBERTADORES.entrySet()) {
            List<String> fila = fila(m, mt, db, e.getKey(), real, inicial);
            grupos.addAll(tirar(fila, e.getValue()[0], fora));
            segunda.addAll(tirar(fila, e.getValue()[1], fora));
            primeira.addAll(tirar(fila, e.getValue()[2], fora));
        }
        if (grupos.size() > LIB_GRUPOS) { // 1º ano: os campeões estão na lista real e ocupam vaga do país
            porForca(m, grupos);
            while (grupos.size() > LIB_GRUPOS) segunda.add(0, grupos.remove(grupos.size() - 1));
        }
        if (segunda.size() > LIB_SEGUNDA) {
            porForca(m, segunda);
            while (segunda.size() > LIB_SEGUNDA) primeira.add(0, segunda.remove(segunda.size() - 1));
        }
        completar(m, grupos, LIB_GRUPOS, fora);
        completar(m, segunda, LIB_SEGUNDA, fora);
        completar(m, primeira, LIB_PRIMEIRA, fora);
        porForca(m, grupos);
        porForca(m, segunda);
        porForca(m, primeira);
        List<String> l = new ArrayList<>(grupos);
        l.addAll(segunda);
        l.addAll(primeira.subList(0, Math.min(LIB_PRIMEIRA, primeira.size())));
        jaContinental.addAll(l);
        return l;
    }

    /**
     * Sul-Americana: [12 de ARG e BRA, do mais forte para o mais fraco][32 da 1ª fase em pares do mesmo
     * país, o mandante primeiro]. O campeão da Copa Cóndor (fictícia) ganha a 1ª vaga do país dele.
     */
    static List<String> participantesSulAmericana(Mundo m, MontadorTemporada mt, BancoMundo db, boolean inicial, Set<String> jaContinental, long semente) {
        Set<String> fora = new java.util.HashSet<>(jaContinental);
        List<String> real = db.participantesIniciais(SUL_AMERICANA);
        String condor = null;
        if (!inicial) {
            Torneio t = m.ultimoEncerrado(CompeticoesFicticias.CONDOR);
            if (t != null && t.campeao != null && m.clube(t.campeao) != null && m.clube(t.campeao).existe && !fora.contains(t.campeao)) condor = t.campeao;
        }
        Rng rng = new Rng(semente);
        List<String> grupos = new ArrayList<>(), primeira = new ArrayList<>(), soltos = new ArrayList<>();
        for (Map.Entry<String, Integer> e : VAGAS_SUL_AMERICANA.entrySet()) {
            String pais = e.getKey();
            List<String> fila = new ArrayList<>(fila(m, mt, db, pais, real, inicial));
            if (condor != null && pais.equals(m.clube(condor).pais)) {
                fila.remove(condor);
                fila.add(0, condor);
            }
            List<String> doPais = tirar(fila, e.getValue(), fora);
            if (pais.equals("ARG") || pais.equals("BRA")) {
                grupos.addAll(doPais);
                continue;
            }
            // 1ª fase: confrontos sorteados entre os clubes do país; o 1º sorteado manda
            rng.embaralhar(doPais);
            for (int i = 0; i + 1 < doPais.size(); i += 2) {
                primeira.add(doPais.get(i));
                primeira.add(doPais.get(i + 1));
            }
            if (doPais.size() % 2 == 1) soltos.add(doPais.get(doPais.size() - 1));
        }
        completar(m, grupos, SULA_GRUPOS, fora);
        // vagas que sobraram na 1ª fase (país sem clubes): os soltos e os mais fortes que sobraram, em pares
        List<String> extra = new ArrayList<>(soltos);
        completar(m, extra, soltos.size() + Math.max(0, SULA_PRIMEIRA - primeira.size() - soltos.size()), fora);
        for (int i = 0; i + 1 < extra.size() && primeira.size() < SULA_PRIMEIRA; i += 2) {
            primeira.add(extra.get(i));
            primeira.add(extra.get(i + 1));
        }
        porForca(m, grupos);
        List<String> l = new ArrayList<>(grupos.subList(0, Math.min(SULA_GRUPOS, grupos.size())));
        l.addAll(primeira.subList(0, Math.min(SULA_PRIMEIRA, primeira.size())));
        jaContinental.addAll(l);
        return l;
    }
}
