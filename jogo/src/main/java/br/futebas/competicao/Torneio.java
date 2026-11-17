package br.futebas.competicao;

import br.futebas.dados.Formato;
import br.futebas.mundo.Rng;
import br.futebas.partida.ResultadoPartida;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uma competição disputada num ano, dirigida pelo formato do banco (motor genérico de
 * formatos): pontos corridos, grupos, quadrangular e mata-mata (jogo único ou ida e volta,
 * gol fora, pênaltis, prorrogação). As datas (rodadas) são distribuídas entre a semana inicial
 * e a final da janela do calendário.
 */
public class Torneio {
    public String id;
    public String nome;
    /** nacional | copa_nacional | estadual | continental | selecoes | kings | amistoso */
    public String ambito;
    public String pais, uf, confederacao;
    /** 'ano' | 'europeu' | 'evento' e o rótulo da temporada ('2026', '2025/26'). */
    public String calendario = "ano";
    public String temporada;
    public Integer divisao;
    public int ano;
    public Formato formato;
    public int pontosVitoria = 3;
    public List<String> criterios = new ArrayList<>();
    /** Participantes do mais bem cotado para o menos (semeadura). */
    public List<String> participantes = new ArrayList<>();
    public List<FaseExec> fases = new ArrayList<>();
    public List<Partida> partidas = new ArrayList<>();
    /** Janela da competição em semanas absolutas (ano * 52 + semana). */
    public int semanaInicio = 1, semanaFim = 52;
    /** Semanas absolutas com data livre para a competição (vazia = todas entre início e fim). */
    public List<Integer> semanasJogo = new ArrayList<>();
    /**
     * Semana absoluta de cada data, quando a competição tem calendário por fase (Libertadores e
     * Sul-Americana: datas reais de cada fase); null ou tamanho diferente de {@link #totalDatas} = as
     * datas são espalhadas por igual em {@link #semanasJogo}.
     */
    public List<Integer> semanasDatas;
    /** Quem está jogando o torneio agora (para buscar estreantes de outra competição e o país dos clubes). */
    transient Executor exAtual;
    /** Jogo de despedida: o jogador homenageado (joga como titular e se aposenta depois). */
    public Integer despedidaJogadorId;
    /** Grupos definidos antes (ex.: sorteio real da Copa de 2026); ids por grupo. */
    public List<List<String>> gruposFixos;
    /** Tabela real em andamento (1/jan/2026): {j, v, e, d, gp, gc, pts} por clube, somada à fase 0. */
    public Map<String, int[]> tabelaBase;
    public int totalDatas, proximaData;
    public boolean encerrado;
    /** Seleções: convocação feita (os convocados saíram dos clubes). */
    public boolean convocacaoFeita;
    /** Seleções nas datas FIFA (eliminatórias, Liga das Nações): os jogadores não saem dos clubes. */
    public boolean datasFifa;
    /** Prêmios e histórico do fim do torneio já registrados. */
    public boolean processado;
    public String campeao, vice;
    /**
     * Torneios curtos no ano (Apertura/Clausura/Finalización/Intermedio; Balanço 2026-11, rodada 2):
     * índice da 1ª fase de cada torneio (vazio = liga normal), rótulo e campeão (id) de cada um.
     * Cada torneio recomeça com todos os participantes; a fase de final entre os campeões (Final
     * absoluta, definição...) é {@link #faseFinalCampeoes} (-1 = não há). A classificação final para
     * acesso/queda e vagas é a tabela somada das fases de pontos de todos os torneios.
     */
    public List<Integer> inicioTorneios = new ArrayList<>();
    public List<String> rotulosTorneios = new ArrayList<>();
    public List<String> campeoesTorneios = new ArrayList<>();
    public int faseFinalCampeoes = -1;
    public List<String> classificacao = new ArrayList<>();
    /** Gols por jogador (id do mundo) no torneio. */
    public Map<Integer, Integer> artilharia = new LinkedHashMap<>();
    public long semente;
    public List<String> avisos = new ArrayList<>();
    /** Regras da época para as partidas deste torneio. */
    public RegrasEpoca regras = new RegrasEpoca();
    /** Fase final em campo neutro (não usado no Brasil por padrão). */
    public boolean finalNeutra;
    /** Kings League: empate na fase de pontos vai para os pênaltis. */
    public boolean penaltisNoEmpate;
    /** Pontos de quem vence nos pênaltis e de quem perde (em vez de 1 a 1 no empate). */
    public int pontosVitoriaPenaltis = 1, pontosDerrotaPenaltis = 1;

    public Torneio() {}

    /** Depois de carregar um jogo salvo: religa as partidas às fases. */
    public void reconstruir() {
        for (FaseExec f : fases) f.partidas = new ArrayList<>();
        for (Partida p : partidas) if (p.fase >= 0 && p.fase < fases.size()) fases.get(p.fase).partidas.add(p);
    }

    // ======================================================================= planejamento

    /** Monta as fases a partir do formato e calcula quantas datas cada uma precisa. */
    public void planejar() {
        List<Formato.Fase> defs = formato == null ? new Formato().fasesOuPadrao() : formato.fasesOuPadrao();
        defs = dividirTorneiosCurtos(defs);
        if (formato != null && formato.pontos_vitoria != null) pontosVitoria = formato.pontos_vitoria;
        else pontosVitoria = regras.pontosVitoria;
        criterios = new ArrayList<>(formato == null ? new Formato().desempatesOuPadrao() : formato.desempatesOuPadrao());
        fases.clear();
        int n = participantes.size();
        int data = 0;
        // entradas escalonadas: os mais fortes (início da lista) estreiam nas fases finais
        int depois = 0;
        for (int k = 1; k < defs.size(); k++) depois += defs.get(k).novos();
        boolean escalonado = depois > 0 && depois < participantes.size();
        int fimBloco = depois;
        if (escalonado) n = participantes.size() - depois;
        int somarNaFase = -1, somar = 0; // liga suíça com play-off: os diretos voltam duas fases depois
        for (int k = 0; k < defs.size(); k++) {
            Formato.Fase d = efetiva(defs.get(k));
            Formato.Fase prox = k + 1 < defs.size() ? efetiva(defs.get(k + 1), false) : null;
            FaseExec f = new FaseExec();
            f.indice = k;
            f.def = d;
            // nome limpo para exibir: "Terceira fase (44 + 4 estreantes)" vira "Terceira fase" (regra do jogo)
            f.nome = d.nome != null ? d.nome.replaceAll("\\s*\\([^)]*\\)", "").trim() : "Fase " + (k + 1);
            if (f.nome.isEmpty()) f.nome = d.nome;
            if (escalonado && k > 0 && defs.get(k).novos() > 0) {
                int novos = defs.get(k).novos();
                fimBloco -= novos;
                f.estreantes = new ArrayList<>(participantes.subList(Math.max(0, fimBloco), Math.max(0, fimBloco) + novos));
                n += novos;
            } else if (k > 0 && d.participantes != null) n = Math.min(n, d.participantes);
            if (k > 0 && d.entram_externos != null && d.entram_externos > 0 && d.origem_externos != null) n += d.entram_externos;
            if (k == somarNaFase) n += somar;
            if (k > 0 && inicioTorneios.contains(k)) n = participantes.size(); // torneio curto seguinte: recomeça com todos
            if (k == faseFinalCampeoes) n = Math.max(2, (int) rotulosTorneios.stream().filter(r -> !"Intermedio".equals(r)).count());
            f.entrantes = n;
            f.tipo = tipoConcreto(d, n, prox == null);
            if (n < 2) {
                f.tipo = "pontos_corridos";
                f.numDatas = 0;
                f.saida = n;
                f.dataInicial = data;
                fases.add(f);
                continue;
            }
            switch (f.tipo) {
                case "pontos_corridos" -> {
                    f.turnos = d.turnos != null ? Math.max(1, d.turnos) : (prox == null ? 2 : 1);
                    f.numGrupos = 1;
                    f.numDatas = f.turnos * rodadas(n);
                    f.saida = d.classificam != null ? Math.min(n, d.classificam) : (prox == null ? n : saidaPadrao(n, prox));
                }
                case "liga_suica" -> {
                    f.numGrupos = 1;
                    f.jogosLiga = Math.max(1, Math.min(n - 1, d.jogos != null ? d.jogos : d.jogos_por_clube != null ? d.jogos_por_clube : 8));
                    f.numDatas = f.jogosLiga;
                    f.saida = d.classificam != null ? Math.min(n, d.classificam) : (prox == null ? n : saidaPadrao(n, prox));
                    if (d.classificam_direto != null && d.playoff != null && d.playoff.size() == 2 && k + 2 < defs.size()) {
                        // UEFA: 1º–8º direto às oitavas; 9º–24º no play-off
                        f.diretos = Math.max(0, Math.min(n, d.classificam_direto));
                        f.saida = Math.max(0, Math.min(n, d.playoff.get(1)) - f.diretos);
                        somarNaFase = k + 2;
                        somar = f.diretos;
                    }
                }
                case "grupos" -> {
                    boolean quad = "quadrangular".equals(d.tipo);
                    int g = d.grupos != null ? d.grupos : (quad ? (n + 3) / 4 : Math.max(1, n / 4));
                    g = Math.max(1, Math.min(g, n / 2));
                    f.numGrupos = g;
                    f.turnos = d.turnos != null ? Math.max(1, d.turnos) : (quad ? 2 : 1);
                    int maior = (n + g - 1) / g;
                    f.numDatas = f.turnos * rodadas(maior);
                    int cl = d.classificam != null ? d.classificam : (prox == null ? 1 : 2);
                    cl = Math.max(1, Math.min(cl, n / g));
                    f.porGrupo = cl;
                    f.terceiros = d.melhores_terceiros != null && cl < (n / g) ? Math.max(0, Math.min(g, d.melhores_terceiros)) : 0;
                    f.saida = Math.min(n, cl * g + f.terceiros);
                    if (d.classificam_direto != null && d.classificam_direto > 0 && d.classificam_direto < cl && f.terceiros == 0 && k + 2 < defs.size()) {
                        // CONMEBOL Sul-Americana: o 1º de cada grupo vai direto às oitavas; o 2º joga o play-off
                        f.diretos = d.classificam_direto * g;
                        f.saida = (cl - d.classificam_direto) * g;
                        somarNaFase = k + 2;
                        somar = f.diretos;
                    }
                    if ("copa_nacional".equals(ambito) && maior > GRUPO_MAXIMO_COPA && prox != null) {
                        // grupos gigantes numa copa (ex.: Copa da Rússia com os 104 clubes em 4 grupos de 26,
                        // turno e returno = 2.600 jogos): o formato do banco é de um "caminho" só; vira
                        // mata-mata em jogo único até o mesmo número de classificados
                        int alvo = Math.max(1, Math.min(n - 1, f.saida));
                        f.tipo = "mata_mata";
                        f.numGrupos = 1;
                        f.turnos = 1;
                        f.pernas = 1;
                        f.saida = alvo;
                        int vivos = n, rod = 0;
                        while (vivos > alvo) {
                            vivos -= Math.min(vivos - alvo, vivos / 2);
                            rod++;
                        }
                        f.rodadasMata = rod;
                        f.numDatas = rod;
                        avisos.add("Copa " + id + " (" + ano + "): grupos de " + maior + " na fase '" + f.nome + "' viraram mata-mata (" + n + " → " + alvo + ").");
                    }
                }
                default -> { // mata_mata
                    f.pernas = Boolean.TRUE.equals(d.ida_volta) ? 2 : 1;
                    int alvo;
                    if (d.classificam != null) alvo = d.classificam;
                    else if (prox == null) alvo = 1;
                    else if (prox.participantes != null) alvo = prox.participantes;
                    else alvo = Math.max(1, n / 2);
                    if (alvo >= n) {
                        // fase sem eliminação aqui (ex.: qualificação "especial" quando os 36 já vêm prontos)
                        f.saida = n;
                        f.rodadasMata = 0;
                        f.numDatas = 0;
                        break;
                    }
                    alvo = Math.max(1, Math.min(alvo, n - 1));
                    f.saida = alvo;
                    int vivos = n, rod = 0, datas = 0;
                    while (vivos > alvo) {
                        int elim = Math.min(vivos - alvo, vivos / 2);
                        boolean finalUnica = Boolean.TRUE.equals(d.final_jogo_unico) && vivos == 2 && alvo == 1;
                        datas += finalUnica ? 1 : f.pernas;
                        vivos -= elim;
                        rod++;
                    }
                    f.rodadasMata = rod;
                    f.numDatas = datas;
                }
            }
            f.dataInicial = data;
            data += f.numDatas;
            n = f.saida;
            fases.add(f);
        }
        totalDatas = data;
        proximaData = 0;
        if (!fases.isEmpty()) iniciarFase(0, escalonado ? participantes.subList(depois, participantes.size()) : participantes);
    }


    /** Rótulo de torneio curto pelo nome da fase (null = não é de um torneio curto ou é ambíguo). */
    static String rotuloTorneio(String nome) {
        String n = nome == null ? "" : nome.toLowerCase(java.util.Locale.ROOT);
        boolean ap = n.contains("apertura"), cl = n.contains("clausura"), fi = n.contains("finaliza"), in = n.contains("intermedio");
        int qt = (ap ? 1 : 0) + (cl ? 1 : 0) + (fi ? 1 : 0) + (in ? 1 : 0);
        if (qt != 1) return null;
        return ap ? "Apertura" : cl ? "Clausura" : fi ? "Finalización" : "Intermedio";
    }

    /**
     * Liga com dois (ou mais) torneios curtos em fases seguidas: guarda onde começa cada torneio,
     * descarta as fases de agregado que vêm depois (tabela anual, promedios: a classificação final já
     * é a soma) e mantém, se houver, uma fase de mata-mata final entre os campeões. Antes o motor
     * parava no 1º campeão e as fases do 2º torneio rodavam com 1 clube (24 fases no banco).
     */
    List<Formato.Fase> dividirTorneiosCurtos(List<Formato.Fase> defs) {
        inicioTorneios = new ArrayList<>();
        rotulosTorneios = new ArrayList<>();
        faseFinalCampeoes = -1;
        if (!"nacional".equals(ambito) || defs.size() < 2) return defs;
        String atual = null;
        int fimBlocos = -1;
        for (int k = 0; k < defs.size(); k++) {
            String r = rotuloTorneio(defs.get(k).nome);
            if (r == null) {
                if (k == 0) return defs; // a 1ª fase precisa ser de um torneio curto
                fimBlocos = k;
                break;
            }
            if (!r.equals(atual)) {
                inicioTorneios.add(k);
                rotulosTorneios.add(r);
                atual = r;
            }
            fimBlocos = k + 1;
        }
        if (inicioTorneios.size() < 2) {
            inicioTorneios = new ArrayList<>();
            rotulosTorneios = new ArrayList<>();
            return defs;
        }
        List<Formato.Fase> l = new ArrayList<>(defs.subList(0, fimBlocos));
        for (int k = fimBlocos; k < defs.size(); k++) {
            Formato.Fase d = efetiva(defs.get(k), false);
            String n = defs.get(k).nome == null ? "" : defs.get(k).nome.toLowerCase(java.util.Locale.ROOT);
            if (d != null && "mata_mata".equals(d.tipo) && n.matches(".*(final|playoff|defini).*")) {
                Formato.Fase fin = d.copia();
                fin.classificam = 1;
                fin.participantes = null;
                l.add(fin);
                faseFinalCampeoes = l.size() - 1;
                break;
            }
        }
        while (campeoesTorneios.size() < inicioTorneios.size()) campeoesTorneios.add(null);
        return l;
    }

    /** Torneio curto (índice) a que a fase pertence; -1 = nenhum. */
    int torneioDaFase(int k) {
        int t = -1;
        for (int i = 0; i < inicioTorneios.size(); i++) if (k >= inicioTorneios.get(i) && (faseFinalCampeoes < 0 || k < faseFinalCampeoes)) t = i;
        return t;
    }

    /** Quem entra na fase {@code k}: todos no começo de um torneio curto, os campeões na final entre eles, senão os classificados. */
    List<String> entrantesDa(int k, List<String> anteriores) {
        if (k > 0 && inicioTorneios.contains(k)) return participantes;
        if (k == faseFinalCampeoes) return campeoesDistintos();
        return anteriores;
    }

    /** Campeões dos torneios curtos principais (sem o Intermedio), sem repetir. */
    List<String> campeoesDistintos() {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < campeoesTorneios.size(); i++) {
            String c = campeoesTorneios.get(i);
            if (c != null && !"Intermedio".equals(rotulosTorneios.get(i)) && !l.contains(c)) l.add(c);
        }
        return l;
    }

    /** Fim de uma fase: se era a última de um torneio curto, guarda o campeão dele. */
    void talvezCampeaoDoTorneio(FaseExec f, List<String> cl) {
        int t = torneioDaFase(f.indice);
        if (t < 0 || cl.isEmpty()) return;
        boolean ultima = f.indice == fases.size() - 1 || inicioTorneios.contains(f.indice + 1) || f.indice + 1 == faseFinalCampeoes;
        if (!ultima) return;
        while (campeoesTorneios.size() <= t) campeoesTorneios.add(null);
        campeoesTorneios.set(t, f.mataMata() && !f.vivos.isEmpty() ? f.vivos.get(0) : cl.get(0));
    }
    /** Copa nacional com grupos maiores que isto (erro de formato do banco) vira mata-mata. */
    public static final int GRUPO_MAXIMO_COPA = 8;

    private static boolean suportado(String t) {
        return t != null && switch (t) {
            case "pontos_corridos", "mata_mata", "grupos", "liga_suica", "quadrangular", "triangular", "hexagonal", "octogonal" -> true;
            default -> false;
        };
    }

    Formato.Fase efetiva(Formato.Fase d) {
        return efetiva(d, true);
    }

    /**
     * Fase "especial" (formato que o motor não roda igual, ex.: melhor de três, módulos): usa a
     * aproximação pesquisada no banco ({@code fallback}); sem ela, deduz o tipo pela estrutura
     * da fase (grupos → grupos; ida/volta, desempate → mata-mata; turnos → pontos corridos) e,
     * em último caso, pelo nome — só este último caso gera aviso.
     */
    Formato.Fase efetiva(Formato.Fase d, boolean avisar) {
        if (d == null || d.tipo == null || suportado(d.tipo)) return d;
        Formato.Fase e = d.copia();
        Formato.Fase fb = d.fallback;
        if (fb != null && suportado(fb.tipo)) {
            e.tipo = fb.tipo;
            if (fb.turnos != null) e.turnos = fb.turnos;
            if (fb.grupos != null) e.grupos = fb.grupos;
            if (fb.classificam != null) e.classificam = fb.classificam;
            if (fb.participantes != null) e.participantes = fb.participantes;
            if (fb.ida_volta != null) e.ida_volta = fb.ida_volta;
            if (fb.gol_fora != null) e.gol_fora = fb.gol_fora;
            if (fb.desempate != null) e.desempate = fb.desempate;
            if (fb.mando_melhor_campanha != null) e.mando_melhor_campanha = fb.mando_melhor_campanha;
            if (fb.chaveamento != null) e.chaveamento = fb.chaveamento;
            if (fb.final_jogo_unico != null) e.final_jogo_unico = fb.final_jogo_unico;
            return e;
        }
        if (d.grupos != null && d.grupos > 1) {
            e.tipo = "grupos";
            return e;
        }
        if (d.ida_volta != null || d.gol_fora != null || d.desempate != null || d.chaveamento != null) {
            e.tipo = "mata_mata";
            return e;
        }
        if (d.turnos != null || d.classificam != null) {
            e.tipo = "pontos_corridos";
            return e;
        }
        String nm = d.nome == null ? "" : d.nome.toLowerCase(java.util.Locale.ROOT);
        if (nm.matches(".*(triangular|quadrangular|hexagonal|octogonal|grupo).*") && !nm.contains("final")) e.tipo = "grupos";
        else if (nm.matches(".*(final|semi|quartas|oitavas|playoff|repescagem|decis|finais).*")) e.tipo = "mata_mata";
        else e.tipo = "pontos_corridos";
        if (avisar) avisos.add("Fase especial '" + (d.nome == null ? "?" : d.nome) + "' em " + id + " (" + ano
                + ") sem estrutura no banco: aproximada como " + e.tipo + " pelo nome.");
        return e;
    }

    private String tipoConcreto(Formato.Fase d, int n, boolean ultima) {
        String t = d.tipo == null ? "pontos_corridos" : d.tipo;
        switch (t) {
            case "pontos_corridos", "mata_mata", "grupos", "liga_suica":
                return t;
            case "quadrangular", "triangular", "hexagonal", "octogonal":
                return "grupos";
            default:
                String esc = n <= 16 ? "mata_mata" : "pontos_corridos";
                avisos.add("Formato '" + t + "' em " + id + " (" + ano + ", fase " + (d.nome == null ? "?" : d.nome)
                        + ") ainda não é suportado; usando " + esc + ".");
                return esc;
        }
    }

    private static int saidaPadrao(int n, Formato.Fase prox) {
        if (prox.participantes != null) return Math.min(n, prox.participantes);
        return n;
    }

    static int rodadas(int n) {
        return n % 2 == 0 ? n - 1 : n;
    }

    public int semanaDaData(int d) {
        if (semanasDatas != null && semanasDatas.size() == totalDatas && d >= 0 && d < totalDatas) return semanasDatas.get(d);
        if (totalDatas <= 0) return semanasJogo.isEmpty() ? semanaInicio : semanasJogo.get(0);
        if (!semanasJogo.isEmpty()) return semanasJogo.get((int) Math.min(semanasJogo.size() - 1, (long) d * semanasJogo.size() / totalDatas));
        int span = Math.max(1, semanaFim - semanaInicio + 1);
        return semanaInicio + (int) ((long) d * span / totalDatas);
    }

    private static boolean esperaOutraCompeticao(FaseExec f) {
        return f.def != null && f.def.entram_externos != null && f.def.entram_externos > 0 && f.def.origem_externos != null && f.numDatas > 0;
    }

    /** Depois de mudar as semanas das datas ({@link #semanasDatas}): acerta semana e dia das partidas já criadas. */
    public void reprogramarPartidas() {
        for (Partida p : partidas) {
            p.sa = semanaDaData(p.data);
            p.ano = br.futebas.mundo.Calendario.ano(p.sa);
            p.semana = br.futebas.mundo.Calendario.semana(p.sa);
            p.dia = diaDaData(p.data, p.sa);
        }
    }

    // ======================================================================= execução

    /** Joga todas as datas marcadas até a semana {@code semana} (inclusive). */
    public void jogarAte(int semana, Executor ex) {
        while (!encerrado && proximaData < totalDatas && semanaDaData(proximaData) <= semana) {
            jogarData(proximaData, ex);
            proximaData++;
        }
        if (!encerrado && proximaData >= totalDatas) finalizar();
    }

    /**
     * Dia (época) da próxima data a jogar até a {@code semana}, para jogar a semana em ordem de dia
     * entre as competições; com {@code timeChave}, o dia do jogo desse time na data (o técnico joga
     * na ordem dos dias dele). {@link Long#MAX_VALUE} = nada a jogar.
     */
    public long diaDaProximaData(int semana, String timeChave) {
        if (encerrado || proximaData >= totalDatas || semanaDaData(proximaData) > semana) return Long.MAX_VALUE;
        long min = Long.MAX_VALUE, doTime = Long.MAX_VALUE;
        for (Partida p : partidas) {
            if (p.data != proximaData || p.jogada) continue;
            min = Math.min(min, p.dia);
            if (timeChave != null && p.envolve(timeChave)) doTime = Math.min(doTime, p.dia);
        }
        if (doTime != Long.MAX_VALUE) return doTime;
        // rodada de mata-mata ainda sem jogos montados: o dia padrão da semana
        return min != Long.MAX_VALUE ? min : br.futebas.mundo.Calendario.diaDeJogo(semanaDaData(proximaData), 0).toEpochDay();
    }

    /** Joga só a próxima data (a semana em ordem de dia: {@code Simulador}). */
    public void jogarProximaData(Executor ex) {
        if (encerrado || proximaData >= totalDatas) return;
        jogarData(proximaData, ex);
        proximaData++;
        if (!encerrado && proximaData >= totalDatas) finalizar();
    }

    /** Joga tudo o que falta (fim de temporada). */
    public void concluir(Executor ex) {
        jogarAte(Integer.MAX_VALUE, ex);
        if (!encerrado) finalizar();
    }

    public FaseExec faseDaData(int d) {
        for (FaseExec f : fases) if (d >= f.dataInicial && d < f.dataInicial + f.numDatas) return f;
        return null;
    }

    void jogarData(int d, Executor ex) {
        exAtual = ex;
        FaseExec f = faseDaData(d);
        if (f == null) return;
        int criadas = partidas.size();
        if (!f.iniciada) {
            FaseExec ant = f.indice > 0 ? fases.get(f.indice - 1) : null;
            iniciarFase(f.indice, ant == null ? participantes : entrantesDa(f.indice, ant.classificados));
        }
        if (f.mataMata()) prepararRodadaMata(f, d);
        if (partidas.size() != criadas) ex.novasPartidas(this);
        List<Partida> adiadas = new ArrayList<>();
        for (Partida p : partidas) if (p.data == d && !p.jogada && ex.adiar(this, p)) adiadas.add(p);
        List<Partida> ordem = new ArrayList<>(partidas.size());
        for (Partida p : partidas) if (p.data == d && !adiadas.contains(p)) ordem.add(p);
        ordem.addAll(adiadas);
        for (Partida p : ordem) {
            if (p.data != d || p.jogada) continue;
            boolean unico = f.mataMata() && confrontoUnico(f, p);
            boolean prorrog = unico && f.def.desempate != null && f.def.desempate.startsWith("prorrogacao");
            ResultadoPartida r = ex.jogar(this, p, prorrog);
            aplicar(p, r);
            if (!f.mataMata() && penaltisNoEmpate && p.golsMandante.equals(p.golsVisitante)) {
                int[] pen = ex.penaltis(this, p);
                p.penaltisMandante = pen[0];
                p.penaltisVisitante = pen[1];
            }
        }
        int antesDoFim = partidas.size();
        if (f.mataMata()) resolverRodadaMata(f, d, ex);
        if (d == f.dataInicial + f.numDatas - 1) concluirFase(f);
        if (partidas.size() != antesDoFim) ex.novasPartidas(this); // próxima rodada do mata-mata já montada
    }

    private boolean confrontoUnico(FaseExec f, Partida p) {
        if (p.confronto < 0) return false;
        return f.confrontos.get(p.confronto).partidas.size() == 1;
    }

    public void aplicar(Partida p, ResultadoPartida r) {
        p.golsMandante = r.golsMandante;
        p.golsVisitante = r.golsVisitante;
        p.prorrogacao = r.prorrogacao;
        p.jogada = true;
        p.melhor = r.melhorNome;
        int cm = 0, cv = 0;
        for (int[] a : r.amarelos) if (a[0] == 0) cm++; else cv++;
        for (int[] a : r.vermelhos) if (a[0] == 0) cm += 3; else cv += 3;
        p.cartoesMandante = cm;
        p.cartoesVisitante = cv;
        for (int[] g : r.gols) {
            if (g[4] == 1 || g[1] < 0) continue; // gol contra ou jogador sem nome (time virtual)
            artilharia.merge(g[1], 1, Integer::sum);
        }
    }

    // ----------------------------------------------------------------------- fases
    void iniciarFase(int k, List<String> entrantes) {
        FaseExec f = fases.get(k);
        if (f.iniciada) return;
        f.iniciada = true;
        if (k > 0 && f.estreantes != null && !f.estreantes.isEmpty()) {
            // estreantes primeiro: são os cabeças de chave (mais fortes / mais bem colocados)
            List<String> l = new ArrayList<>(f.estreantes);
            for (String id : entrantes) if (!l.contains(id)) l.add(id);
            entrantes = l;
        }
        List<String> ext = k > 0 ? externos(f) : List.of();
        if (!ext.isEmpty()) {
            // vindos de outra competição (Libertadores -> Sul-Americana): entram por último (pote mais baixo /
            // adversários dos 2ºs de grupo no play-off) e passam a ser participantes deste torneio
            List<String> l = new ArrayList<>(entrantes);
            for (String id : ext) if (!l.contains(id)) l.add(id);
            entrantes = l;
            for (String id : ext) if (!participantes.contains(id)) participantes.add(id);
        }
        f.clubes = new ArrayList<>(entrantes.subList(0, Math.min(entrantes.size(), f.entrantes)));
        Rng rng = new Rng(semente * 31 + k);
        switch (f.tipo) {
            case "pontos_corridos" -> {
                f.grupos.add(new ArrayList<>(f.clubes));
                criarTurnos(f, 0, f.clubes, f.dataInicial, rodadas(f.clubes.size()));
            }
            case "liga_suica" -> {
                f.grupos.add(new ArrayList<>(f.clubes));
                criarLigaSuica(f, rng);
            }
            case "grupos" -> {
                int g = f.numGrupos;
                for (int i = 0; i < g; i++) f.grupos.add(new ArrayList<>());
                if (k == 0 && gruposFixos != null && gruposFixos.size() == g) {
                    for (int i = 0; i < g; i++) f.grupos.get(i).addAll(gruposFixos.get(i));
                    int maior = 0;
                    for (List<String> gr : f.grupos) maior = Math.max(maior, gr.size());
                    for (int i = 0; i < g; i++) criarTurnos(f, i, f.grupos.get(i), f.dataInicial, rodadas(maior));
                    break;
                }
                // potes pela semeadura; cada pote é sorteado entre os grupos
                boolean separar = Boolean.TRUE.equals(f.def.separar_paises) && exAtual != null;
                for (int pote = 0; pote * g < f.clubes.size(); pote++) {
                    List<String> p = new ArrayList<>(f.clubes.subList(pote * g, Math.min(f.clubes.size(), (pote + 1) * g)));
                    if (pote > 0 || "sorteio".equals(f.def.chaveamento)) {
                        if (separar) p = sortearSemRepetirPais(p, f.grupos, rng);
                        else rng.embaralhar(p);
                    }
                    for (int i = 0; i < p.size(); i++) f.grupos.get(i).add(p.get(i));
                }
                int maior = 0;
                for (List<String> gr : f.grupos) maior = Math.max(maior, gr.size());
                for (int i = 0; i < g; i++) criarTurnos(f, i, f.grupos.get(i), f.dataInicial, rodadas(maior));
            }
            default -> {
                f.vivos = new ArrayList<>(f.clubes);
                if (f.numDatas > 0) prepararRodadaMata(f, f.dataInicial);
            }
        }
        if (f.numDatas == 0) concluirFase(f);
    }

    /**
     * Sorteio de um pote entre os grupos sem dois clubes do mesmo país no mesmo grupo (regra da CONMEBOL
     * nos grupos da Libertadores e da Sul-Americana). Tenta sorteios até achar um válido; se não houver
     * (país com mais clubes que grupos), fica o de menos repetições.
     */
    private List<String> sortearSemRepetirPais(List<String> pote, List<List<String>> grupos, Rng rng) {
        List<String> melhor = null;
        int menos = Integer.MAX_VALUE;
        for (int tent = 0; tent < 400 && menos > 0; tent++) {
            List<String> p = new ArrayList<>(pote);
            rng.embaralhar(p);
            int rep = 0;
            for (int i = 0; i < p.size(); i++) {
                String pa = exAtual.paisDe(p.get(i));
                if (pa == null) continue;
                for (String o : grupos.get(i)) if (pa.equals(exAtual.paisDe(o))) rep++;
            }
            if (rep < menos) {
                menos = rep;
                melhor = p;
            }
        }
        return melhor;
    }

    /**
     * Estreantes de outra competição do mesmo ano ({@code origem_externos} = "competicao:tipo"):
     * <ul>
     *   <li>{@code terceiros_grupos}: o clube logo abaixo dos classificados em cada grupo da 1ª fase de grupos
     *       (o 3º), ordenados pela campanha (pontos, saldo, gols);</li>
     *   <li>{@code eliminados_previa}: os perdedores da fase de mata-mata logo antes dessa fase de grupos
     *       (3ª fase da Libertadores), na ordem da semeadura de lá.</li>
     * </ul>
     * Se a outra competição não existe ou a fase ainda não acabou, entram os que houver (com aviso).
     */
    List<String> externos(FaseExec f) {
        Formato.Fase d = f.def;
        if (d == null || d.entram_externos == null || d.entram_externos <= 0 || d.origem_externos == null) return List.of();
        String[] o = d.origem_externos.split(":", 2);
        Torneio src = exAtual == null || o.length < 2 ? null : exAtual.outroTorneio(this, o[0]);
        List<String> l = new ArrayList<>();
        if (src != null) {
            int g = -1;
            for (FaseExec x : src.fases) if (!x.mataMata() && x.numGrupos > 1) { g = x.indice; break; }
            if (g >= 0 && "terceiros_grupos".equals(o[1]) && src.fases.get(g).concluida) {
                FaseExec fg = src.fases.get(g);
                List<LinhaTabela> terc = new ArrayList<>();
                for (int gi = 0; gi < fg.grupos.size(); gi++) {
                    List<LinhaTabela> t = src.tabela(g, gi);
                    if (fg.porGrupo < t.size()) terc.add(t.get(fg.porGrupo));
                }
                terc.sort((a, b) -> a.pontos != b.pontos ? b.pontos - a.pontos : a.saldo() != b.saldo() ? b.saldo() - a.saldo()
                        : a.golsPro != b.golsPro ? b.golsPro - a.golsPro : a.clubeId.compareTo(b.clubeId));
                for (LinhaTabela x : terc) l.add(x.clubeId);
            } else if (g > 0 && "eliminados_previa".equals(o[1]) && src.fases.get(g - 1).mataMata() && src.fases.get(g - 1).concluida) {
                for (Confronto c : src.fases.get(g - 1).confrontos) if (c.perdedor != null) l.add(c.perdedor);
                l.sort(java.util.Comparator.comparingInt(src.participantes::indexOf));
            }
        }
        l.removeIf(participantes::contains);
        if (l.size() > d.entram_externos) l = new ArrayList<>(l.subList(0, d.entram_externos));
        if (l.size() < d.entram_externos)
            avisos.add(id + " (" + ano + "), " + f.nome + ": " + l.size() + " de " + d.entram_externos + " vindos de " + d.origem_externos + ".");
        return l;
    }

    private void criarTurnos(FaseExec f, int grupo, List<String> clubes, int dataBase, int rodadasPorTurno) {
        List<String> l = new ArrayList<>(clubes);
        if (l.size() < 2) return;
        if (l.size() % 2 == 1) l.add(null);
        int n = l.size();
        int rod = n - 1;
        List<String[]> primeiroTurno = new ArrayList<>();
        List<Integer> rodadaDe = new ArrayList<>();
        List<String> roda = new ArrayList<>(l);
        for (int r = 0; r < rod; r++) {
            for (int i = 0; i < n / 2; i++) {
                String a = roda.get(i), b = roda.get(n - 1 - i);
                if (a == null || b == null) continue;
                boolean inverte = i == 0 ? r % 2 == 1 : i % 2 == 1;
                primeiroTurno.add(inverte ? new String[]{b, a} : new String[]{a, b});
                rodadaDe.add(r);
            }
            // rotação (o primeiro fica fixo)
            String ultimo = roda.remove(n - 1);
            roda.add(1, ultimo);
        }
        for (int t = 0; t < f.turnos; t++) {
            for (int i = 0; i < primeiroTurno.size(); i++) {
                String[] par = primeiroTurno.get(i);
                Partida p = nova(f, dataBase + t * rodadasPorTurno + rodadaDe.get(i));
                p.grupo = grupo;
                boolean vira = t % 2 == 1;
                p.mandante = vira ? par[1] : par[0];
                p.visitante = vira ? par[0] : par[1];
            }
        }
    }

    /**
     * Fase de liga "suíça" (Champions 2024+): tabela única, cada clube faz {@code jogosLiga}
     * jogos contra adversários diferentes. Rodadas tiradas do rodízio completo (método do
     * círculo) sobre uma ordem sorteada por potes, espaçadas para variar os adversários.
     */
    private void criarLigaSuica(FaseExec f, Rng rng) {
        List<String> l = new ArrayList<>(f.clubes);
        int potes = 4, tam = (l.size() + potes - 1) / potes;
        List<List<String>> ps = new ArrayList<>();
        for (int i = 0; i < potes; i++) {
            List<String> p = new ArrayList<>(l.subList(Math.min(l.size(), i * tam), Math.min(l.size(), (i + 1) * tam)));
            rng.embaralhar(p);
            ps.add(p);
        }
        List<String> ordem = new ArrayList<>();
        for (int i = 0; i < tam; i++) for (List<String> p : ps) if (i < p.size()) ordem.add(p.get(i));
        if (ordem.size() % 2 == 1) ordem.add(null);
        int n = ordem.size(), rod = n - 1;
        int k = Math.min(f.jogosLiga, rod);
        List<String> roda = new ArrayList<>(ordem);
        java.util.Set<Integer> escolhidas = new java.util.HashSet<>();
        for (int i = 0; i < k; i++) escolhidas.add((int) ((long) i * rod / k) + (rod / k) / 2);
        int dataRel = 0;
        for (int r = 0; r < rod && dataRel < k; r++) {
            if (escolhidas.contains(r)) {
                for (int i = 0; i < n / 2; i++) {
                    String a = roda.get(i), b = roda.get(n - 1 - i);
                    if (a == null || b == null) continue;
                    boolean inverte = (i + r + dataRel) % 2 == 1;
                    Partida p = nova(f, f.dataInicial + dataRel);
                    p.grupo = 0;
                    p.mandante = inverte ? b : a;
                    p.visitante = inverte ? a : b;
                }
                dataRel++;
            }
            String ultimo = roda.remove(n - 1);
            roda.add(1, ultimo);
        }
    }

    /**
     * Dia da data na semana: com várias datas na mesma semana, cada uma num dia (antes, a partir da
     * 3ª data na semana todas caíam no mesmo dia: dois jogos do mesmo time no mesmo dia). Uma data:
     * o dia de sempre; duas: meio e fim de semana; três ou mais: espalhadas.
     */
    long diaDaData(int data, int sa) {
        int primeira = data, n = 0;
        while (primeira > 0 && semanaDaData(primeira - 1) == sa) primeira--;
        for (int d = primeira; d < totalDatas && semanaDaData(d) == sa; d++) n++;
        int k = data - primeira;
        if (n <= 2) {
            boolean outraDepois = n == 2 && k == 0;
            return br.futebas.mundo.Calendario.diaDeJogo(sa, outraDepois ? 1 : 0).toEpochDay();
        }
        int off = n == 3 ? new int[] {1, 3, 5}[k] : (int) Math.round(k * 6.0 / (n - 1));
        java.time.LocalDate dia = br.futebas.mundo.Calendario.inicio(sa).plusDays(Math.min(6, off));
        int ano = br.futebas.mundo.Calendario.ano(sa);
        return (dia.getYear() != ano ? java.time.LocalDate.of(ano, 12, 31) : dia).toEpochDay();
    }

    private Partida nova(FaseExec f, int data) {
        Partida p = new Partida();
        p.id = partidas.size();
        p.torneioId = id;
        p.data = data;
        p.sa = semanaDaData(data);
        p.ano = br.futebas.mundo.Calendario.ano(p.sa);
        p.semana = br.futebas.mundo.Calendario.semana(p.sa);
        p.dia = diaDaData(data, p.sa);
        p.fase = f.indice;
        partidas.add(p);
        f.partidas.add(p);
        return p;
    }

    // ----------------------------------------------------------------------- mata-mata
    private int rodadaDaData(FaseExec f, int d) {
        int off = d - f.dataInicial;
        int vivos = f.entrantes, rod = 0, acum = 0;
        while (vivos > f.saida) {
            boolean finalUnica = Boolean.TRUE.equals(f.def.final_jogo_unico) && vivos == 2 && f.saida == 1;
            int pernas = finalUnica ? 1 : f.pernas;
            if (off < acum + pernas) return rod;
            acum += pernas;
            vivos -= Math.min(vivos - f.saida, vivos / 2);
            rod++;
        }
        return rod - 1;
    }

    private int primeiraDataDaRodada(FaseExec f, int rodada) {
        int vivos = f.entrantes, rod = 0, acum = 0;
        while (vivos > f.saida && rod < rodada) {
            boolean finalUnica = Boolean.TRUE.equals(f.def.final_jogo_unico) && vivos == 2 && f.saida == 1;
            acum += finalUnica ? 1 : f.pernas;
            vivos -= Math.min(vivos - f.saida, vivos / 2);
            rod++;
        }
        return f.dataInicial + acum;
    }

    private void prepararRodadaMata(FaseExec f, int d) {
        int rod = rodadaDaData(f, d);
        for (Confronto c : f.confrontos) if (c.rodada == rod) return; // já criada
        List<String> vivos = f.vivos;
        if (vivos.size() <= f.saida) return;
        int elim = Math.min(vivos.size() - f.saida, vivos.size() / 2);
        int byes = vivos.size() - 2 * elim;
        List<String> jogam = new ArrayList<>(vivos.subList(byes, vivos.size()));
        List<String[]> pares = new ArrayList<>();
        String ch = f.def.chaveamento == null ? "" : f.def.chaveamento;
        Map<String, Integer> chave = ch.equals("chave") ? chaveAnterior(f, rod) : null;
        if (ch.equals("sorteio")) {
            List<String> s = new ArrayList<>(jogam);
            new Rng(semente * 131 + f.indice * 17 + rod).embaralhar(s);
            for (int i = 0; i + 1 < s.size(); i += 2) {
                String a = s.get(i), b = s.get(i + 1);
                if (jogam.indexOf(a) > jogam.indexOf(b)) { String t = a; a = b; b = t; }
                pares.add(new String[]{a, b});
            }
        } else if (ch.equals("pares")) {
            // confrontos já arranjados na ordem de entrada (1ª fase da Sul-Americana: clubes do mesmo país)
            for (int i = 0; i + 1 < jogam.size(); i += 2) pares.add(new String[]{jogam.get(i), jogam.get(i + 1)});
        } else if (ch.equals("potes")) {
            // oitavas da CONMEBOL: 1ºs de grupo (pote 1, decidem em casa) x pote 2, posição na chave sorteada
            int meio = jogam.size() / 2;
            List<String> p1 = new ArrayList<>(jogam.subList(0, meio)), p2 = new ArrayList<>(jogam.subList(meio, jogam.size()));
            Rng r = new Rng(semente * 131 + f.indice * 17 + rod);
            r.embaralhar(p1);
            r.embaralhar(p2);
            for (int i = 0; i < Math.min(p1.size(), p2.size()); i++) pares.add(new String[]{p1.get(i), p2.get(i)});
        } else if (chave != null) {
            // chave fixa desde o sorteio: vencedor do confronto 1 x vencedor do 2, 3 x 4...; decide em casa o de
            // melhor semeadura (ordem de entrada)
            List<String> s = new ArrayList<>(jogam);
            s.sort(java.util.Comparator.comparingInt((String x) -> chave.getOrDefault(x, 999)).thenComparingInt(jogam::indexOf));
            for (int i = 0; i + 1 < s.size(); i += 2) {
                String a = s.get(i), b = s.get(i + 1);
                if (jogam.indexOf(a) > jogam.indexOf(b)) { String t = a; a = b; b = t; }
                pares.add(new String[]{a, b});
            }
        } else {
            for (int i = 0; i < elim; i++) pares.add(new String[]{jogam.get(i), jogam.get(jogam.size() - 1 - i)});
        }
        boolean finalUnica = Boolean.TRUE.equals(f.def.final_jogo_unico) && vivos.size() == 2 && f.saida == 1;
        int pernas = finalUnica ? 1 : f.pernas;
        boolean mandoMelhor = f.def.mando_melhor_campanha == null || f.def.mando_melhor_campanha;
        int d0 = primeiraDataDaRodada(f, rod);
        for (String[] par : pares) {
            Confronto c = new Confronto(rod, par[0], par[1]);
            int ic = f.confrontos.size();
            f.confrontos.add(c);
            for (int perna = 0; perna < pernas; perna++) {
                Partida p = nova(f, d0 + perna);
                p.rodadaMata = rod;
                p.confronto = ic;
                p.perna = perna;
                boolean aEmCasa = pernas == 1 ? true : (mandoMelhor ? perna == 1 : perna == 0);
                p.mandante = aEmCasa ? par[0] : par[1];
                p.visitante = aEmCasa ? par[1] : par[0];
                c.partidas.add(p.id);
            }
        }
    }

    /**
     * Posição na chave de cada clube vivo: o índice do confronto que ele venceu na rodada anterior (desta
     * fase ou a última da fase de mata-mata anterior); null = não há rodada anterior de mata-mata.
     */
    private Map<String, Integer> chaveAnterior(FaseExec f, int rod) {
        FaseExec src = f;
        int r = rod - 1;
        if (rod == 0) {
            if (f.indice == 0 || !fases.get(f.indice - 1).mataMata()) return null;
            src = fases.get(f.indice - 1);
            r = src.rodadasMata - 1;
        }
        Map<String, Integer> pos = new HashMap<>();
        int i = 0;
        for (Confronto c : src.confrontos) if (c.rodada == r && c.vencedor != null) pos.put(c.vencedor, i++);
        return pos.isEmpty() ? null : pos;
    }

    private void resolverRodadaMata(FaseExec f, int d, Executor ex) {
        int rod = rodadaDaData(f, d);
        List<Confronto> daRodada = new ArrayList<>();
        for (Confronto c : f.confrontos) if (c.rodada == rod && c.vencedor == null) daRodada.add(c);
        if (daRodada.isEmpty()) return;
        // só resolve quando todas as pernas foram jogadas
        for (Confronto c : daRodada) for (int pid : c.partidas) if (!partidas.get(pid).jogada) return;
        for (Confronto c : daRodada) decidir(f, c, ex);
        List<String> novos = new ArrayList<>();
        for (String v : f.vivos) {
            boolean perdeu = false;
            for (Confronto c : daRodada) if (v.equals(c.perdedor)) perdeu = true;
            if (!perdeu) novos.add(v);
        }
        f.vivos = novos;
        if (f.vivos.size() > f.saida) prepararRodadaMata(f, primeiraDataDaRodada(f, rod + 1));
    }

    private void decidir(FaseExec f, Confronto c, Executor ex) {
        int ga = 0, gb = 0, foraA = 0, foraB = 0;
        Partida ultima = null;
        for (int pid : c.partidas) {
            Partida p = partidas.get(pid);
            ultima = p;
            if (p.mandante.equals(c.a)) {
                ga += p.golsMandante;
                gb += p.golsVisitante;
                foraB += p.golsVisitante;
            } else {
                gb += p.golsMandante;
                ga += p.golsVisitante;
                foraA += p.golsVisitante;
            }
        }
        String venc;
        if (ga != gb) venc = ga > gb ? c.a : c.b;
        else if (c.partidas.size() > 1 && Boolean.TRUE.equals(f.def.gol_fora) && foraA != foraB) venc = foraA > foraB ? c.a : c.b;
        else {
            String des = f.def.desempate == null ? "penaltis" : f.def.desempate;
            if (des.equals("melhor_campanha")) venc = c.a;
            else if (des.equals("visitante") && c.partidas.size() == 1) venc = ultima.visitante;
            else {
                int[] pen = ex.penaltis(this, ultima);
                ultima.penaltisMandante = pen[0];
                ultima.penaltisVisitante = pen[1];
                venc = pen[0] > pen[1] ? ultima.mandante : ultima.visitante;
            }
        }
        c.vencedor = venc;
        c.perdedor = venc.equals(c.a) ? c.b : c.a;
    }

    void concluirFase(FaseExec f) {
        if (f.concluida) return;
        f.concluida = true;
        List<String> cl = new ArrayList<>();
        if (f.mataMata()) {
            cl.addAll(f.vivos);
        } else {
            int porGrupo = f.numGrupos > 1 ? Math.max(1, f.porGrupo) : f.saida;
            if (f.grupos.size() <= 1) {
                for (LinhaTabela t : tabela(f.indice, 0)) cl.add(t.clubeId);
                if (f.diretos > 0 && f.indice + 2 < fases.size()) {
                    int dir = Math.min(f.diretos, cl.size());
                    fases.get(f.indice + 2).estreantes = new ArrayList<>(cl.subList(0, dir));
                    cl = new ArrayList<>(cl.subList(dir, Math.min(cl.size(), dir + f.saida)));
                } else cl = new ArrayList<>(cl.subList(0, Math.min(cl.size(), f.saida)));
            } else {
                // por posição no grupo, depois pela campanha
                List<LinhaTabela> geral = tabelaGeral();
                Map<String, Integer> posGeral = new HashMap<>();
                for (int i = 0; i < geral.size(); i++) posGeral.put(geral.get(i).clubeId, i);
                for (int pos = 0; pos < porGrupo; pos++) {
                    List<String> nivel = new ArrayList<>();
                    for (int g = 0; g < f.grupos.size(); g++) {
                        List<LinhaTabela> t = tabela(f.indice, g);
                        if (pos < t.size()) nivel.add(t.get(pos).clubeId);
                    }
                    nivel.sort((a, b) -> Integer.compare(posGeral.getOrDefault(a, 999), posGeral.getOrDefault(b, 999)));
                    cl.addAll(nivel);
                }
                if (f.diretos > 0 && f.indice + 2 < fases.size()) {
                    // 1ºs de grupo direto duas fases depois (Sul-Americana: oitavas); os outros vão ao play-off
                    int dir = Math.min(f.diretos, cl.size());
                    List<String> est = new ArrayList<>(cl.subList(0, dir));
                    for (String id : fases.get(f.indice + 2).estreantes) if (!est.contains(id)) est.add(id);
                    fases.get(f.indice + 2).estreantes = est;
                    cl = new ArrayList<>(cl.subList(dir, cl.size()));
                }
                if (f.terceiros > 0) {
                    // melhores colocados na posição seguinte (ex.: 8 melhores terceiros da Copa de 48)
                    List<LinhaTabela> prox = new ArrayList<>();
                    for (int g = 0; g < f.grupos.size(); g++) {
                        List<LinhaTabela> t = tabela(f.indice, g);
                        if (porGrupo < t.size()) prox.add(t.get(porGrupo));
                    }
                    prox.sort((a, b) -> a.pontos != b.pontos ? b.pontos - a.pontos : a.saldo() != b.saldo() ? b.saldo() - a.saldo()
                            : a.golsPro != b.golsPro ? b.golsPro - a.golsPro : a.clubeId.compareTo(b.clubeId));
                    for (int i = 0; i < Math.min(f.terceiros, prox.size()); i++) cl.add(prox.get(i).clubeId);
                }
            }
        }
        f.classificados = cl;
        talvezCampeaoDoTorneio(f, cl);
        if (f.indice == fases.size() - 1) finalizar();
        else if (!esperaOutraCompeticao(fases.get(f.indice + 1))) iniciarFase(f.indice + 1, entrantesDa(f.indice + 1, cl));
        // com estreantes de outra competição, a fase só começa na 1ª data dela (jogarData): a Libertadores
        // precisa ter terminado a fase de onde eles vêm
    }

    // ======================================================================= tabelas

    /** Tabela ordenada de um grupo de uma fase de pontos (grupo 0 nos pontos corridos). */
    public List<LinhaTabela> tabela(int fase, int grupo) {
        FaseExec f = fases.get(fase);
        if (grupo >= f.grupos.size()) return List.of();
        Map<String, LinhaTabela> m = new LinkedHashMap<>();
        for (String c : f.grupos.get(grupo)) m.put(c, linhaBase(c, fase));
        List<Partida> jogos = new ArrayList<>();
        for (Partida p : f.partidas) {
            if (p.grupo != grupo) continue;
            jogos.add(p);
            if (!p.jogada) continue;
            registrar(m, p);
        }
        return Desempate.ordenar(m.values(), criterios, jogos, semente);
    }


    /**
     * Fim de uma liga de torneios curtos: a classificação (acesso, queda, vagas) é a tabela somada das
     * fases de pontos de todos os torneios; o campeão é o da final entre os campeões, se houver, senão o
     * do último torneio (os outros campeões ficam em {@link #campeoesTorneios} e viram títulos à parte).
     */
    void finalizarTorneiosCurtos() {
        Map<String, LinhaTabela> m = new LinkedHashMap<>();
        for (String c : participantes) m.put(c, new LinhaTabela(c));
        List<Partida> jogos = new ArrayList<>();
        for (Partida p : partidas) {
            if (!p.jogada || p.fase < 0 || p.fase >= fases.size() || fases.get(p.fase).mataMata()) continue;
            registrar(m, p);
            jogos.add(p);
        }
        List<String> ordem = new ArrayList<>();
        for (LinhaTabela l : Desempate.ordenar(m.values(), criterios, jogos, semente)) ordem.add(l.clubeId);
        String camp = null, vice = null;
        if (faseFinalCampeoes >= 0 && faseFinalCampeoes < fases.size()) {
            FaseExec fin = fases.get(faseFinalCampeoes);
            if (fin.vivos.size() == 1) camp = fin.vivos.get(0);
            for (Confronto c : fin.confrontos) if (c.vencedor != null && c.vencedor.equals(camp)) vice = c.perdedor;
        }
        for (int i = campeoesTorneios.size() - 1; i >= 0 && camp == null; i--)
            if (!"Intermedio".equals(rotulosTorneios.get(i))) camp = campeoesTorneios.get(i);
        if (camp != null) {
            ordem.remove(camp);
            ordem.add(0, camp);
        }
        if (vice != null) {
            ordem.remove(vice);
            ordem.add(1, vice);
        }
        classificacao = ordem;
        campeao = ordem.isEmpty() ? null : ordem.get(0);
        this.vice = ordem.size() > 1 ? ordem.get(1) : null;
    }

    /**
     * Títulos dos torneios curtos além do campeão principal: {rótulo, clubeId}. Sem final entre os
     * campeões, o último torneio é o título principal (não repete); com final, todos entram.
     */
    public List<String[]> titulosTorneiosCurtos() {
        List<String[]> l = new ArrayList<>();
        if (inicioTorneios == null || inicioTorneios.size() < 2) return l;
        int ultimoPrincipal = -1;
        for (int i = 0; i < rotulosTorneios.size(); i++) if (!"Intermedio".equals(rotulosTorneios.get(i))) ultimoPrincipal = i;
        for (int i = 0; i < campeoesTorneios.size() && i < rotulosTorneios.size(); i++) {
            if (campeoesTorneios.get(i) == null || (faseFinalCampeoes < 0 && i == ultimoPrincipal)) continue;
            l.add(new String[]{rotulosTorneios.get(i), campeoesTorneios.get(i)});
        }
        return l;
    }
    /** Campanha somando todas as fases (usada para semeadura e classificação final). */
    public List<LinhaTabela> tabelaGeral() {
        Map<String, LinhaTabela> m = new LinkedHashMap<>();
        for (String c : participantes) m.put(c, linhaBase(c, 0));
        for (Partida p : partidas) if (p.jogada) registrar(m, p);
        return Desempate.ordenar(m.values(), criterios, partidas, semente);
    }

    private LinhaTabela linhaBase(String c, int fase) {
        LinhaTabela l = new LinhaTabela(c);
        int[] b = tabelaBase == null || fase != 0 ? null : tabelaBase.get(c);
        if (b != null) {
            l.jogos = b[0]; l.vitorias = b[1]; l.empates = b[2]; l.derrotas = b[3];
            l.golsPro = b[4]; l.golsContra = b[5]; l.pontos = b[6];
        }
        return l;
    }

    /**
     * Começa a competição já em andamento com a tabela real: as {@code datasJogadas} primeiras
     * datas são descartadas (os resultados reais já estão na tabela base).
     */
    public void aplicarTabelaInicial(Map<String, int[]> base, int datasJogadas) {
        tabelaBase = base;
        int corte = Math.max(0, Math.min(datasJogadas, totalDatas));
        partidas.removeIf(p -> p.data < corte);
        for (int i = 0; i < partidas.size(); i++) partidas.get(i).id = i;
        for (FaseExec f : fases) f.partidas.removeIf(p -> p.data < corte);
        proximaData = corte;
    }

    private void registrar(Map<String, LinhaTabela> m, Partida p) {
        LinhaTabela a = m.computeIfAbsent(p.mandante, LinhaTabela::new);
        LinhaTabela b = m.computeIfAbsent(p.visitante, LinhaTabela::new);
        a.registrar(p.golsMandante, p.golsVisitante, false, pontosVitoria, p.cartoesMandante);
        b.registrar(p.golsVisitante, p.golsMandante, true, pontosVitoria, p.cartoesVisitante);
        if (penaltisNoEmpate && p.penaltisMandante != null && p.golsMandante.equals(p.golsVisitante) && p.rodadaMata < 0) {
            // sem empate (Kings): quem vence os shootouts tem uma vitória, quem perde uma derrota
            boolean mVence = p.penaltisMandante > p.penaltisVisitante;
            LinhaTabela v = mVence ? a : b, d = mVence ? b : a;
            v.empates--;
            v.vitorias++;
            v.vitoriasPenaltis++;
            v.pontos += pontosVitoriaPenaltis - 1;
            d.empates--;
            d.derrotas++;
            d.derrotasPenaltis++;
            d.pontos += pontosDerrotaPenaltis - 1;
        }
    }

    // ======================================================================= final

    void finalizar() {
        if (encerrado) return;
        encerrado = true;
        if (inicioTorneios != null && inicioTorneios.size() >= 2) {
            finalizarTorneiosCurtos();
            return;
        }
        List<LinhaTabela> geral = tabelaGeral();
        Map<String, Integer> posGeral = new HashMap<>();
        for (int i = 0; i < geral.size(); i++) posGeral.put(geral.get(i).clubeId, i);
        Map<String, Integer> faseMax = new HashMap<>();
        Map<String, Integer> sub = new HashMap<>();
        for (FaseExec f : fases) {
            if (!f.iniciada) continue;
            Map<String, Integer> posFase = new HashMap<>();
            if (!f.mataMata()) {
                for (int g = 0; g < f.grupos.size(); g++) {
                    List<LinhaTabela> t = tabela(f.indice, g);
                    for (int i = 0; i < t.size(); i++) posFase.put(t.get(i).clubeId, i);
                }
            }
            for (String c : f.clubes) {
                faseMax.put(c, f.indice);
                if (f.mataMata()) {
                    int alc = f.rodadasMata; // passou por todas
                    for (Confronto x : f.confrontos) if (c.equals(x.perdedor)) alc = Math.min(alc, x.rodada);
                    sub.put(c, -alc);
                } else {
                    sub.put(c, posFase.getOrDefault(c, 99));
                }
            }
        }
        List<String> ordem = new ArrayList<>(participantes);
        ordem.sort((a, b) -> {
            int fa = faseMax.getOrDefault(a, -1), fb = faseMax.getOrDefault(b, -1);
            if (fa != fb) return Integer.compare(fb, fa);
            int sa = sub.getOrDefault(a, 99), sb = sub.getOrDefault(b, 99);
            if (sa != sb) return Integer.compare(sa, sb);
            return Integer.compare(posGeral.getOrDefault(a, 999), posGeral.getOrDefault(b, 999));
        });
        // campeão do mata-mata final
        FaseExec ult = fases.isEmpty() ? null : fases.get(fases.size() - 1);
        if (ult != null && ult.mataMata() && ult.vivos.size() == 1) {
            String c = ult.vivos.get(0);
            ordem.remove(c);
            ordem.add(0, c);
        }
        classificacao = ordem;
        campeao = ordem.isEmpty() ? null : ordem.get(0);
        vice = ordem.size() > 1 ? ordem.get(1) : null;
    }

    // ======================================================================= consultas

    public List<Partida> partidasDoClube(String clubeId) {
        List<Partida> l = new ArrayList<>();
        for (Partida p : partidas) if (p.envolve(clubeId)) l.add(p);
        return l;
    }

    public FaseExec faseAtual() {
        for (FaseExec f : fases) if (!f.concluida) return f;
        return fases.isEmpty() ? null : fases.get(fases.size() - 1);
    }

    public int posicaoFinal(String clubeId) {
        int i = classificacao.indexOf(clubeId);
        return i < 0 ? 999 : i + 1;
    }

    /** Artilheiros do torneio: lista de {jogadorId, gols}, do maior para o menor. */
    public List<int[]> artilheiros(int n) {
        List<int[]> l = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : artilharia.entrySet()) l.add(new int[]{e.getKey(), e.getValue()});
        l.sort((a, b) -> a[1] != b[1] ? Integer.compare(b[1], a[1]) : Integer.compare(a[0], b[0]));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    public List<String> participantesOrdenados() {
        return Collections.unmodifiableList(participantes);
    }
}
