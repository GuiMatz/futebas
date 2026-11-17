package br.futebas.competicao;

import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.dados.Formato;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Pais;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Competições FICTÍCIAS do FUTÉBAS (criadas para o jogo, não existem na vida real), registradas
 * pelo motor no banco carregado (o construtor do banco não é deste motor):
 * <ul>
 *   <li><b>Copa Cóndor</b> ({@code conmebol-condor}): 3ª competição de clubes da América do Sul (como
 *       a Conference League): vagas maiores para os países mais fracos, 8 grupos de 4, mata-mata em
 *       ida e volta e final única em campo neutro no fim de novembro. O campeão vai à Sul-Americana.</li>
 *   <li><b>Taça Atlântica</b> ({@code taca-atlantica}): jogo único entre o campeão da Copa Cóndor e o da
 *       UEFA Conference League, no começo de agosto (1ª edição quando os dois campeões existirem).</li>
 *   <li><b>Desafio Sul-Americana × Europa League</b> ({@code desafio-sulamericana-europa}): não há
 *       competição real entre esses campeões no banco/pesquisa; jogo único anual no começo de agosto.</li>
 * </ul>
 */
public final class CompeticoesFicticias {
    private CompeticoesFicticias() {}

    public static final String CONDOR = "conmebol-condor", TACA_ATLANTICA = "taca-atlantica", DESAFIO = "desafio-sulamericana-europa";
    public static final String SUL_AMERICANA = "conmebol-sul-americana", EUROPA = "uefa-europa", CONFERENCE = "uefa-conference";

    /** Vagas da Copa Cóndor por país (31) + o campeão = 32. */
    public static final Map<String, Integer> VAGAS_CONDOR = new LinkedHashMap<>();

    static {
        VAGAS_CONDOR.put("BRA", 2);
        VAGAS_CONDOR.put("ARG", 2);
        VAGAS_CONDOR.put("COL", 3);
        VAGAS_CONDOR.put("URY", 3);
        VAGAS_CONDOR.put("CHL", 3);
        VAGAS_CONDOR.put("PRY", 3);
        VAGAS_CONDOR.put("ECU", 3);
        VAGAS_CONDOR.put("PER", 4);
        VAGAS_CONDOR.put("BOL", 4);
        VAGAS_CONDOR.put("VEN", 4);
    }

    public static final int PARTICIPANTES_CONDOR = 32;

    /** Semanas do ano dos jogos únicos de agosto. */
    public static final int SEMANA_DESAFIO = 31, SEMANA_TACA = 32;

    /** Registra as competições fictícias no banco carregado (uma vez; não mexe nas que o banco já tem). */
    public static void registrar(BancoMundo db) {
        if (!db.competicoes.containsKey(CONDOR)) {
            db.competicoes.put(CONDOR, new Db.Competicao(CONDOR, "Copa Cóndor", "continental", null, null, "CONMEBOL", null, "ano",
                    "assets/competicoes/copa-condor.png"));
            db.formatos.computeIfAbsent(CONDOR, k -> new ArrayList<>()).add(new Db.FormatoLinha(CONDOR, 2026, null, formatoCondor(),
                    "fictícia (FUTÉBAS)"));
        }
        if (!db.competicoes.containsKey(TACA_ATLANTICA))
            db.competicoes.put(TACA_ATLANTICA, new Db.Competicao(TACA_ATLANTICA, "Taça Atlântica", "continental", null, null, null, null, "evento",
                    "assets/competicoes/taca-atlantica.png"));
        if (!db.competicoes.containsKey(DESAFIO))
            db.competicoes.put(DESAFIO, new Db.Competicao(DESAFIO, "Desafio Sul-Americana × Europa League", "continental", null, null, null, null, "evento", null));
    }

    static Formato formatoCondor() {
        Formato f = new Formato();
        f.participantes = PARTICIPANTES_CONDOR;
        Formato.Fase g = new Formato.Fase("Fase de grupos", "grupos");
        g.grupos = 8;
        g.turnos = 2;
        g.classificam = 2;
        Formato.Fase mm = new Formato.Fase("Mata-mata", "mata_mata");
        mm.ida_volta = true;
        mm.desempate = "penaltis";
        mm.chaveamento = "1x8";
        mm.final_jogo_unico = true;
        f.fases = new ArrayList<>(List.of(g, mm));
        f.observacoes = "Competição fictícia do FUTÉBAS: final única em campo neutro no fim de novembro.";
        return f;
    }

    /**
     * Participantes da Copa Cóndor: o campeão anterior e, por país, o vice da copa nacional e os
     * mais bem colocados da liga que não estão na Libertadores nem na Sul-Americana.
     */
    static List<String> participantesCondor(Mundo m, MontadorTemporada mt, Set<String> jaContinental) {
        LinkedHashSet<String> part = new LinkedHashSet<>();
        Torneio ult = m.ultimoEncerrado(CONDOR);
        if (ult != null && ult.campeao != null && ok(m.clube(ult.campeao)) && !jaContinental.contains(ult.campeao)) part.add(ult.campeao);
        Map<String, List<String>> fila = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : VAGAS_CONDOR.entrySet()) {
            String pais = e.getKey();
            int vagas = e.getValue();
            List<String> doPais = new ArrayList<>();
            fila.put(pais, doPais);
            for (Torneio t : m.torneiosAnteriores) {
                if ("copa_nacional".equals(t.ambito) && pais.equals(t.pais) && t.vice != null && !t.id.startsWith("bra-copa-")) {
                    if (ok(m.clube(t.vice)) && !jaContinental.contains(t.vice)) doPais.add(t.vice);
                }
            }
            for (Clube c : mt.rankingNacional(pais)) if (ok(c) && c.divisao == 1 && !jaContinental.contains(c.id)) doPais.add(c.id);
            for (Clube c : mt.rankingNacional(pais)) if (ok(c) && !jaContinental.contains(c.id)) doPais.add(c.id);
            for (String id : doPais) {
                if (vagas <= 0) break;
                if (part.add(id)) vagas--;
            }
        }
        // sempre 32: sem campeão anterior (1ª edição) ou com ele já na Sul-Americana/Libertadores (a vaga
        // que ele ganha), ou num país sem clubes suficientes, a vaga vai ao próximo da fila de cada país
        // (dos países com mais vagas para os com menos, um por vez)
        List<String> ordem = new ArrayList<>(fila.keySet());
        java.util.Collections.reverse(ordem);
        boolean achou = true;
        while (part.size() < PARTICIPANTES_CONDOR && achou) {
            achou = false;
            for (String pais : ordem) {
                if (part.size() >= PARTICIPANTES_CONDOR) break;
                for (String id : fila.get(pais)) {
                    if (part.add(id)) {
                        achou = true;
                        break;
                    }
                }
            }
        }
        List<String> l = new ArrayList<>(part);
        jaContinental.addAll(l);
        return l;
    }

    private static boolean ok(Clube c) {
        return c != null && c.existe && !c.virtual;
    }

    /**
     * Jogos únicos de agosto (Taça Atlântica e Desafio): criados na semana certa quando os dois
     * campeões existem; vão para {@code m.torneios} (calendário "evento").
     */
    public static void jogosDeAgosto(Mundo m, BancoMundo db) {
        if (m.semana == SEMANA_DESAFIO) jogoUnico(m, db, DESAFIO, "Desafio Sul-Americana × Europa League", SUL_AMERICANA, EUROPA);
        if (m.semana == SEMANA_TACA) jogoUnico(m, db, TACA_ATLANTICA, "Taça Atlântica", CONDOR, CONFERENCE);
    }

    private static void jogoUnico(Mundo m, BancoMundo db, String id, String nome, String compA, String compB) {
        for (Torneio t : m.torneios) if (t.id.equals(id) && t.ano == m.ano) return;
        Torneio a = m.ultimoEncerrado(compA), b = m.ultimoEncerrado(compB);
        if (a == null || b == null || a.campeao == null || b.campeao == null || a.campeao.equals(b.campeao)) return;
        if (!ok(m.clube(a.campeao)) || !ok(m.clube(b.campeao))) return;
        Torneio t = new Torneio();
        t.id = id;
        t.nome = nome + " " + m.ano;
        t.ambito = "continental";
        t.calendario = "evento";
        t.temporada = String.valueOf(m.ano);
        t.ano = m.ano;
        Formato f = new Formato();
        f.participantes = 2;
        Formato.Fase fin = new Formato.Fase("Final", "mata_mata");
        fin.desempate = "prorrogacao_penaltis";
        fin.final_jogo_unico = true;
        f.fases = new ArrayList<>(List.of(fin));
        t.formato = f;
        t.regras = RegrasEpoca.resolver(db.regras, m.ano, "mundo", id);
        t.semente = m.semente * 7_777L + m.ano * 13L + id.hashCode();
        t.participantes = new ArrayList<>(List.of(a.campeao, b.campeao));
        t.finalNeutra = true;
        int sa = m.semanaAbsoluta();
        t.semanaInicio = sa;
        t.semanaFim = sa;
        t.semanasJogo.add(sa);
        t.planejar();
        m.torneios.add(t);
        Pais p = m.pais(m.clube(a.campeao).pais);
        m.historia.evento(m.ano, m.semana, "titulo", nome + ": " + m.nomeParticipante(a.campeao) + " × " + m.nomeParticipante(b.campeao) + ".");
        if (p == null) return;
    }

    /** Semana absoluta da final da Copa Cóndor (fim de novembro). */
    static int semanaFinalCondor(int ano) {
        return Calendario.absoluta(java.time.LocalDate.of(ano, 11, 28));
    }
}
