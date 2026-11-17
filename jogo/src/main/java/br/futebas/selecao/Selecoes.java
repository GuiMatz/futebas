package br.futebas.selecao;

import br.futebas.Balanceamento;
import br.futebas.competicao.FormatoPadrao;
import br.futebas.competicao.RegrasEpoca;
import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.dados.Formato;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Pais;
import br.futebas.mundo.Rng;
import br.futebas.mundo.Selecao;
import br.futebas.mundo.StatusJogador;
import br.futebas.partida.Escalador;
import br.futebas.partida.TimePartida;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seleções nacionais: convocação automática (os 26 melhores do país, pela força e pela fase),
 * força Elo dinâmica, datas FIFA (amistosos e eliminatórias simplificadas) e os torneios:
 * <b>Copa do Mundo 2026</b> (48 seleções, 12 grupos de 4, 8 melhores terceiros, 16 avos;
 * grupos reais da tabela {@code selecoes}), Copas seguintes (2030, 2034...) com vagas por
 * confederação decididas pelo ranking, Copa América e Eurocopa nos anos certos.
 */
public final class Selecoes {
    private final Mundo m;
    private final BancoMundo db;

    public Selecoes(Mundo m, BancoMundo db) {
        this.m = m;
        this.db = db;
    }

    public static final String COPA = "fifa-copa-do-mundo", COPA_AMERICA = "conmebol-copa-america", EURO = "uefa-eurocopa";

    /** Vagas da Copa de 48 por confederação (a sobra vai para os melhores do ranking). */
    static final Map<String, Integer> VAGAS_COPA = new LinkedHashMap<>();

    static {
        VAGAS_COPA.put("UEFA", 16);
        VAGAS_COPA.put("CAF", 9);
        VAGAS_COPA.put("AFC", 8);
        VAGAS_COPA.put("CONMEBOL", 6);
        VAGAS_COPA.put("CONCACAF", 6);
        VAGAS_COPA.put("OFC", 1);
    }

    // =================================================================== criação
    public void criar() {
        Set<String> paises = new java.util.TreeSet<>(db.paises.keySet());
        for (Db.Selecao s : db.selecoes.values()) paises.add(s.pais());
        for (Jogador j : m.jogadores) if (j.pais != null) paises.add(j.pais);
        for (String p : paises) {
            Selecao s = new Selecao();
            s.pais = p;
            s.id = Selecao.id(p);
            Db.Pais dp = db.pais(p);
            s.nome = dp != null && dp.nome() != null ? dp.nome() : p;
            s.confederacao = dp != null ? dp.confederacao() : null;
            Db.Selecao ds = db.selecoes.get(p);
            Integer rank = ds != null && ds.rankingFifa() != null ? ds.rankingFifa() : dp != null ? dp.rankingFifa() : null;
            s.rankingFifaInicial = rank;
            s.tecnico = ds == null ? null : ds.tecnico();
            s.elo = rank != null ? Math.max(1150, 2100 - 7.0 * rank) : 1350;
            m.selecoes.put(p, s);
        }
        convocarTodas();
        // sem ranking FIFA: Elo pela força dos convocados
        for (Selecao s : m.selecoes.values()) {
            if (s.rankingFifaInicial == null && s.convocados.size() >= 11) s.elo = 1400 + (s.forca - 55) * 22;
            referencia(s);
        }
    }

    /** Força de referência da seleção (fixada uma vez): a de 2026 pelos jogadores, ou pelo ranking FIFA. */
    public static double referencia(Selecao s) {
        if (s.forcaReferencia <= 0) {
            double porRanking = s.rankingFifaInicial == null ? 0 : forcaPorElo(Math.max(1150, 2100 - 7.0 * s.rankingFifaInicial));
            s.forcaReferencia = Math.max(s.convocados.size() >= 11 ? s.forca : 0, porRanking);
            if (s.forcaReferencia <= 0) s.forcaReferencia = forcaPorElo(s.elo);
        }
        return s.forcaReferencia;
    }

    /**
     * Quanto falta (positivo) ou sobra (negativo) para a seleção voltar à força de referência,
     * pela força dos melhores jogadores atuais do país (só seleções com 11+ convocáveis).
     */
    public static double deficit(Selecao s) {
        double atual = s.convocados.size() >= 11 ? s.forca : 40;
        return referencia(s) - atual;
    }

    // =================================================================== convocação
    /** Índice país → jogadores que podem ser convocados (profissionais com clube). */
    Map<String, List<Jogador>> porPais() {
        Map<String, List<Jogador>> idx = new HashMap<>();
        for (Jogador j : m.jogadores) {
            if (j.status != StatusJogador.PROFISSIONAL || j.amador() || j.pais == null) continue;
            idx.computeIfAbsent(j.pais, k -> new ArrayList<>()).add(j);
        }
        return idx;
    }

    public void convocarTodas() {
        Map<String, List<Jogador>> idx = porPais();
        for (Selecao s : m.selecoes.values()) convocar(s, idx.getOrDefault(s.pais, List.of()));
    }

    /** Os 26 melhores do país pela força e pela fase (nota média da temporada), com 3 goleiros. */
    public void convocar(Selecao s, List<Jogador> candidatos) {
        List<Jogador> l = new ArrayList<>();
        for (Jogador j : candidatos) if (j.lesaoSemanas <= 2) l.add(j);
        l.sort(Comparator.comparingDouble((Jogador j) -> -valorConvocacao(j)).thenComparingInt(j -> j.id));
        List<Jogador> conv = new ArrayList<>();
        int goleiros = 0;
        for (Jogador j : l) {
            if (j.posicao == br.futebas.mundo.Posicao.GOL) {
                if (goleiros >= 3) continue;
                goleiros++;
            }
            conv.add(j);
            if (conv.size() >= Balanceamento.CONVOCADOS) break;
        }
        s.convocados.clear();
        for (Jogador j : conv) s.convocados.add(j.id);
        s.forca = conv.size() >= 11 ? forcaDosMelhores(conv) : forcaPorElo(s.elo);
    }

    /**
     * Força da seleção pelos melhores do país (top-heavy): os 11 mais fortes pesam 1, os
     * reservas até o 23º pesam pouco. Assim o país com craques é forte mesmo com pouca base.
     */
    public static double forcaDosMelhores(List<Jogador> conv) {
        List<Jogador> top = new ArrayList<>(conv);
        top.sort(Comparator.comparingDouble((Jogador j) -> -j.forca));
        double soma = 0, peso = 0;
        for (int i = 0; i < Math.min(23, top.size()); i++) {
            double w = i < 11 ? 1 : Balanceamento.SELECAO_PESO_RESERVAS;
            soma += w * top.get(i).forca;
            peso += w;
        }
        return peso == 0 ? 0 : soma / peso;
    }

    /** Placar de um jogo de seleções (rápido): diferenças de força pesam mais que nos clubes. */
    public static int[] placarSelecoes(double fa, double fb, boolean mando, Rng rng) {
        // elite (rodada 2 do balanço): entre as grandes seleções a diferença de força pesa mais
        double meio = (fa + fb) / 2, el = br.futebas.partida.MotorPartida.fatorElite(meio, Balanceamento.SELECAO_ELITE_K);
        fa = meio + el * (fa - meio);
        fb = meio + el * (fb - meio);
        double d = fa - fb + rng.gauss(0, Balanceamento.SELECAO_RUIDO) - rng.gauss(0, Balanceamento.SELECAO_RUIDO);
        double base = mando ? Balanceamento.SELECAO_GOLS_MANDANTE : Balanceamento.SELECAO_GOLS_NEUTRO;
        double baseB = mando ? Balanceamento.SELECAO_GOLS_VISITANTE : Balanceamento.SELECAO_GOLS_NEUTRO;
        double k = Balanceamento.SELECAO_SENSIBILIDADE * d;
        // o total de gols cresce com a diferença só como cosh^EXP (antes, cosh inteiro: goleadas demais)
        double esc = Math.pow(Math.cosh(k), Balanceamento.SELECAO_GOLS_EXP - 1);
        double la = base * Math.exp(k) * esc;
        double lb = baseB * Math.exp(-k) * esc;
        return new int[]{Math.min(9, rng.poisson(la)), Math.min(9, rng.poisson(lb))};
    }

    static double valorConvocacao(Jogador j) {
        double forma = j.jogosTemp >= 3 ? (j.notaMediaTemp() - 6.5) * 3 : 0;
        return j.forca + forma + (j.estrela ? 1 : 0);
    }

    public static double forcaPorElo(double elo) {
        return Math.max(35, Math.min(88, 55 + (elo - 1500) / 20.0));
    }

    /** Time da seleção para uma partida (convocados disponíveis; sem elenco, time virtual pelo Elo). */
    public TimePartida time(Selecao s, int lado, int substituicoes, Rng rng) {
        List<Jogador> l = new ArrayList<>();
        for (int id : s.convocados) {
            Jogador j = m.jogador(id);
            if (j != null && j.status != StatusJogador.APOSENTADO) l.add(j);
        }
        double nivel = l.size() >= 11 ? s.forca : forcaPorElo(s.elo);
        return Escalador.deLista(s.id, s.nome, l, "4-3-3", substituicoes, nivel, lado, rng);
    }

    /** Força para a partida rápida. */
    public double forcaRapida(Selecao s) {
        return s.convocados.size() >= 11 ? s.forca : forcaPorElo(s.elo);
    }

    /** Elo depois de um jogo (resultado do ponto de vista de a). */
    public static void atualizarElo(Selecao a, Selecao b, int ga, int gb, boolean neutro) {
        double da = a.elo + (neutro ? 0 : Balanceamento.ELO_MANDO) - b.elo;
        double esperado = 1 / (1 + Math.pow(10, -da / 400));
        double real = ga > gb ? 1 : ga == gb ? 0.5 : 0;
        double margem = Math.log(Math.abs(ga - gb) + 1) + 1;
        double delta = Balanceamento.ELO_K * margem * (real - esperado);
        a.elo += delta;
        b.elo -= delta;
        a.jogos++;
        b.jogos++;
        if (ga > gb) { a.vitorias++; b.derrotas++; }
        else if (ga == gb) { a.empates++; b.empates++; }
        else { a.derrotas++; b.vitorias++; }
    }

    // =================================================================== ranking
    public List<Selecao> ranking() {
        List<Selecao> l = new ArrayList<>(m.selecoes.values());
        l.sort(Comparator.comparingDouble((Selecao s) -> -s.elo).thenComparing(s -> s.pais));
        return l;
    }

    // =================================================================== torneios do ano
    /** Torneios de seleções que acontecem no ano (Copa do Mundo, Copa América, Eurocopa e os do banco). */
    public List<Torneio> torneiosDoAno(int ano) {
        List<Torneio> l = new ArrayList<>();
        Set<String> feitos = new HashSet<>();
        for (Db.Competicao c : db.competicoes.values()) {
            if (!"selecoes".equals(c.ambito())) continue;
            if (especial(c.id())) continue;
            if (!aconteceEm(c.id(), ano)) continue;
            Torneio t = torneio(c.id(), c.nome(), c.confederacao(), ano);
            feitos.add(tipo(c.id()));
            if (t != null) l.add(t);
        }
        if (!feitos.contains(COPA) && (ano - 2026) % 4 == 0) addSeNaoNulo(l, torneio(COPA, "Copa do Mundo FIFA", null, ano));
        if (!feitos.contains(COPA_AMERICA) && (ano - 2028) % 4 == 0 && ano >= 2028)
            addSeNaoNulo(l, torneio(COPA_AMERICA, "Copa América", "CONMEBOL", ano));
        if (!feitos.contains(EURO) && (ano - 2028) % 4 == 0 && ano >= 2028) addSeNaoNulo(l, torneio(EURO, "Eurocopa", "UEFA", ano));
        l.addAll(extrasDoAno(ano));
        return l;
    }

    private static void addSeNaoNulo(List<Torneio> l, Torneio t) {
        if (t != null) l.add(t);
    }

    static String tipo(String id) {
        String s = id.toLowerCase();
        if (s.contains("copa-do-mundo") || s.contains("world-cup")) return COPA;
        if (s.contains("copa-america")) return COPA_AMERICA;
        if (s.contains("euro")) return EURO;
        return id;
    }

    /** O torneio acontece no ano? Pela linha de calendário do banco (repetida a cada 4 anos) ou pelo ciclo padrão. */
    boolean aconteceEm(String id, int ano) {
        Db.CalendarioLinha cal = null;
        for (Db.CalendarioLinha c : db.calendarios) if (id.equals(c.competicaoId())) cal = c;
        if (cal != null) {
            int a = Db.ano(cal.de() != null ? cal.de() : cal.temporada(), -1);
            return a > 0 && ano >= a && (ano - a) % 4 == 0;
        }
        return switch (tipo(id)) {
            case COPA -> (ano - 2026) % 4 == 0;
            case COPA_AMERICA, EURO -> ano >= 2028 && (ano - 2028) % 4 == 0;
            default -> false;
        };
    }

    private Torneio torneio(String id, String nome, String conf, int ano) {
        List<String> part = participantes(id, conf, ano);
        if (part.size() < 2) return null;
        Formato f;
        Db.FormatoLinha fl = db.formato(id, ano);
        if (fl != null) f = fl.formato();
        else f = formatoPadrao(id, part.size(), conf);
        Torneio t = new Torneio();
        t.id = id;
        t.nome = nome + " " + ano;
        t.ambito = "selecoes";
        t.confederacao = conf;
        t.calendario = "evento";
        t.temporada = String.valueOf(ano);
        t.ano = ano;
        t.formato = f;
        t.regras = RegrasEpoca.resolver(db.regras, ano, "mundo", id);
        t.semente = m.semente * 31_337L + ano * 101L + id.hashCode();
        t.participantes = part;
        t.finalNeutra = true;
        int[] jan = janela(id, ano);
        t.semanaInicio = jan[0];
        t.semanaFim = jan[1];
        for (int s = jan[0]; s <= jan[1]; s++) t.semanasJogo.add(s);
        if (COPA.equals(tipo(id)) && ano == 2026) t.gruposFixos = gruposReais2026(part, f);
        t.planejar();
        return t;
    }

    private int[] janela(String id, int ano) {
        Db.CalendarioLinha cal = db.calendario(id, ano);
        LocalDate de = cal == null ? null : Db.data(cal.de()), ate = cal == null ? null : Db.data(cal.ate());
        if (de == null || ate == null) {
            de = LocalDate.of(ano, 6, 11);
            ate = LocalDate.of(ano, 7, 19);
            if (EURO.equals(tipo(id))) { de = LocalDate.of(ano, 6, 12); ate = LocalDate.of(ano, 7, 12); }
            if (COPA_AMERICA.equals(tipo(id))) { de = LocalDate.of(ano, 6, 15); ate = LocalDate.of(ano, 7, 14); }
        } else {
            int shift = ano - de.getYear();
            de = de.plusYears(shift);
            ate = ate.plusYears(shift);
        }
        return new int[]{Calendario.absoluta(de), Calendario.absoluta(ate)};
    }

    static Formato formatoPadrao(String id, int n, String conf) {
        Formato f = new Formato();
        f.participantes = n;
        f.fases = new ArrayList<>();
        Formato.Fase g = new Formato.Fase("Fase de grupos", "grupos");
        g.turnos = 1;
        g.classificam = 2;
        g.grupos = Math.max(1, n / 4);
        int potencia = 1;
        while (potencia * 2 <= n) potencia *= 2;
        int saida = Math.min(potencia, 2 * g.grupos + Math.max(0, g.grupos));
        // quantos terceiros completam a próxima potência de 2 (48 → 32: 8 terceiros; 24 → 16: 4)
        int alvo = 1;
        while (alvo * 2 <= 2 * g.grupos + g.grupos) alvo *= 2;
        if (alvo > 2 * g.grupos) g.melhores_terceiros = alvo - 2 * g.grupos;
        f.fases.add(g);
        Formato.Fase mm = new Formato.Fase("Mata-mata", "mata_mata");
        mm.desempate = "prorrogacao_penaltis";
        mm.chaveamento = "1x8";
        f.fases.add(mm);
        f.observacoes = "Formato padrão do motor para " + id + " (" + saida + ")";
        return f;
    }

    // =================================================================== participantes
    private List<String> participantes(String id, String conf, int ano) {
        String tipo = tipo(id);
        List<String> l = new ArrayList<>();
        if (COPA.equals(tipo) && ano > Balanceamento.ANO_INICIAL) {
            List<String> c = classificadosCopa(ano);
            if (c != null) return c;
        }
        if (COPA.equals(tipo) && ano == 2026) {
            for (Db.Selecao s : db.selecoes.values()) if (s.classificadaCopa2026() && m.selecoes.containsKey(s.pais())) l.add(Selecao.id(s.pais()));
            if (!l.isEmpty()) return ordenar(l);
        }
        List<Selecao> rk = ranking();
        int n;
        Db.FormatoLinha fl = db.formato(id, ano);
        if (fl != null && fl.formato().participantes != null) n = fl.formato().participantes;
        else n = switch (tipo) {
            case COPA -> 48;
            case EURO -> 24;
            case COPA_AMERICA -> 16;
            default -> id.startsWith("caf-copa-africa") || id.startsWith("afc-copa-asia") ? 24 : id.equals(COPA_OFC) ? 8 : 16;
        };
        if (COPA.equals(tipo)) {
            Map<String, Integer> usadas = new HashMap<>();
            for (Selecao s : rk) {
                if (l.size() >= n) break;
                Integer vagas = VAGAS_COPA.get(s.confederacao);
                if (vagas == null) continue;
                if (usadas.getOrDefault(s.confederacao, 0) >= vagas) continue;
                usadas.merge(s.confederacao, 1, Integer::sum);
                l.add(s.id);
            }
            for (Selecao s : rk) {
                if (l.size() >= n) break;
                if (!l.contains(s.id)) l.add(s.id); // repescagem: melhores do ranking que sobraram
            }
        } else {
            for (Selecao s : rk) if (l.size() < n && conf != null && conf.equalsIgnoreCase(s.confederacao)) l.add(s.id);
            if (COPA_AMERICA.equals(tipo)) for (Selecao s : rk) if (l.size() < n && "CONCACAF".equalsIgnoreCase(s.confederacao)) l.add(s.id);
        }
        // seleções sem nenhum jogador e sem ranking ficam de fora se houver alternativa
        return ordenar(l);
    }

    private List<String> ordenar(List<String> l) {
        l.sort(Comparator.comparingDouble((String id) -> -m.selecao(id).elo).thenComparing(id -> id));
        return l;
    }

    /** Grupos reais da Copa de 2026 (letra em {@code selecoes.grupo_copa_2026}); null se incompletos. */
    private List<List<String>> gruposReais2026(List<String> part, Formato f) {
        Map<String, List<String>> porLetra = new java.util.TreeMap<>();
        for (String id : part) {
            Db.Selecao ds = db.selecoes.get(id.substring(4));
            if (ds == null || ds.grupoCopa2026() == null || ds.grupoCopa2026().isBlank()) return null;
            porLetra.computeIfAbsent(ds.grupoCopa2026().trim().toUpperCase(), k -> new ArrayList<>()).add(id);
        }
        int g = f.fases != null && !f.fases.isEmpty() && f.fases.get(0).grupos != null ? f.fases.get(0).grupos : part.size() / 4;
        if (porLetra.size() != g) return null;
        return new ArrayList<>(porLetra.values());
    }

    // =================================================================== datas FIFA
    /**
     * Data FIFA: cada seleção com elenco faz 2 jogos (amistosos ou eliminatórias simplificadas)
     * contra vizinhas de ranking da mesma confederação. Resultado rápido pela força; só mexe no Elo.
     */
    public int dataFifa(Rng rng) {
        convocarTodas();
        Map<String, List<Selecao>> porConf = new java.util.TreeMap<>();
        for (Selecao s : ranking()) {
            if (s.convocados.size() < 11 && s.rankingFifaInicial == null) continue;
            porConf.computeIfAbsent(s.confederacao == null ? "?" : s.confederacao, k -> new ArrayList<>()).add(s);
        }
        int jogos = 0;
        for (List<Selecao> l : porConf.values()) {
            for (int rodada = 0; rodada < 2; rodada++) {
                for (int i = rodada; i + 1 < l.size(); i += 2) {
                    Selecao a = l.get(i), b = l.get(i + 1);
                    int[] g = placarSelecoes(forcaRapida(a), forcaRapida(b), true, rng);
                    atualizarElo(a, b, g[0], g[1], false);
                    for (Selecao s : new Selecao[]{a, b}) {
                        for (int k = 0; k < Math.min(11, s.convocados.size()); k++) {
                            Jogador j = m.jogador(s.convocados.get(k));
                            if (j != null) j.jogosSelecao++;
                        }
                    }
                    jogos++;
                }
            }
        }
        return jogos;
    }

    /** Placar rápido (Poisson) pela diferença de força. */
    public static int[] placarRapido(double fa, double fb, boolean mando, Rng rng) {
        double d = fa - fb + rng.gauss(0, Balanceamento.RAPIDO_RUIDO) - rng.gauss(0, Balanceamento.RAPIDO_RUIDO);
        double la = (mando ? Balanceamento.RAPIDO_GOLS_MANDANTE : 1.27) * Math.exp(Balanceamento.RAPIDO_SENSIBILIDADE * d);
        double lb = (mando ? Balanceamento.RAPIDO_GOLS_VISITANTE : 1.27) * Math.exp(-Balanceamento.RAPIDO_SENSIBILIDADE * d);
        return new int[]{Math.min(9, rng.poisson(la)), Math.min(9, rng.poisson(lb))};
    }

    // =================================================================== torneio em andamento
    /** Começo de um torneio de seleções: convoca e tira os convocados dos clubes. */
    public void iniciarTorneio(Torneio t) {
        Map<String, List<Jogador>> idx = porPais();
        for (String id : t.participantes) {
            Selecao s = m.selecao(id);
            if (s == null) continue;
            convocar(s, idx.getOrDefault(s.pais, List.of()));
            for (int jid : s.convocados) {
                Jogador j = m.jogador(jid);
                if (j != null) j.convocado = true;
            }
        }
        Selecao h = humana();
        if (h != null && t.participantes.contains(h.id)) {
            m.mensagem("selecao", t.nome, "Convocação da seleção " + (h.nome) + " para a " + t.nome + ": " + h.convocados.size() + " jogadores.");
        }
    }

    /** Fim do torneio: os convocados voltam aos clubes. */
    public void encerrarTorneio(Torneio t) {
        depoisDoTorneio(t);
        for (String id : t.participantes) {
            Selecao s = m.selecao(id);
            if (s == null) continue;
            for (int jid : s.convocados) {
                Jogador j = m.jogador(jid);
                if (j != null) j.convocado = false;
            }
        }
    }

    public Selecao humana() {
        return m.tecnico == null || m.tecnico.selecaoPais == null ? null : m.selecao(m.tecnico.selecaoPais);
    }

    /** Pais do mundo de uma seleção (pode não existir para seleções sem liga). */
    public Pais pais(Selecao s) {
        return m.pais(s.pais);
    }

    /** Ids das seleções ordenadas para a interface. */
    public List<String> ids() {
        return new ArrayList<>(m.selecoes.keySet());
    }

    /** Remove convocações penduradas (jogo carregado no meio de um torneio já encerrado). */
    public void limparConvocacoes() {
        Set<String> ativos = new HashSet<>();
        for (Torneio t : m.torneios) if ("selecoes".equals(t.ambito) && !t.encerrado) ativos.addAll(t.participantes);
        for (Selecao s : m.selecoes.values()) {
            if (ativos.contains(s.id)) continue;
            for (int jid : s.convocados) {
                Jogador j = m.jogador(jid);
                if (j != null) j.convocado = false;
            }
        }
    }

    static Formato.Vaga vagaVazia() {
        return null;
    }

    /** Usado só para formatos de reserva fora das seleções. */
    static Formato reserva(Db.Competicao c, int n) {
        return FormatoPadrao.para(c, n);
    }

    // =================================================================== competições de seleções (requisito)
    /** Eliminatórias por confederação (no ano anterior à Copa, nas datas FIFA). */
    public static final String[] CONFEDERACOES = {"CONMEBOL", "UEFA", "CONCACAF", "CAF", "AFC", "OFC"};
    public static final String REPESCAGEM = "fifa-repescagem-intercontinental", FINALISSIMA = "finalissima", COPA_OURO = "concacaf-copa-ouro",
            NATIONS_UEFA = "uefa-nations-league", NATIONS_CONCACAF = "concacaf-nations-league";

    /** Id das eliminatórias de uma confederação. */
    public static String idEliminatorias(String conf) {
        return "eliminatorias-" + conf.toLowerCase(java.util.Locale.ROOT);
    }

    /** Ids tratados aqui (fora do laço genérico dos torneios do banco). */
    static boolean especial(String id) {
        return id.startsWith("eliminatorias-") || id.startsWith(NATIONS_UEFA) || id.startsWith(NATIONS_CONCACAF) || id.equals(FINALISSIMA)
                || id.equals(COPA_OURO) || id.equals(REPESCAGEM);
    }

    /** Seleções da confederação que disputam (com jogadores ou ranking FIFA), da mais forte para a mais fraca. */
    private List<String> daConfederacao(String conf) {
        List<String> l = new ArrayList<>();
        for (Selecao s : ranking()) {
            if (!conf.equalsIgnoreCase(s.confederacao)) continue;
            if (s.convocados.size() < 11 && s.rankingFifaInicial == null) continue;
            l.add(s.id);
        }
        return l;
    }

    /** Torneio de seleções genérico jogado nas datas FIFA (sem tirar os jogadores dos clubes). */
    private Torneio torneioDatasFifa(String id, String nome, String conf, int ano, List<String> part, Formato f, int[] semanas) {
        if (part.size() < 2) return null;
        Torneio t = new Torneio();
        t.id = id;
        t.nome = nome + " " + ano;
        t.ambito = "selecoes";
        t.confederacao = conf;
        t.calendario = "evento";
        t.temporada = String.valueOf(ano);
        t.ano = ano;
        t.formato = f;
        t.regras = RegrasEpoca.resolver(db.regras, ano, "mundo", id);
        t.semente = m.semente * 31_337L + ano * 101L + id.hashCode();
        t.participantes = new ArrayList<>(part);
        t.finalNeutra = true;
        t.datasFifa = true;
        t.convocacaoFeita = true; // jogos nas datas FIFA: os jogadores não saem dos clubes
        t.semanaInicio = Calendario.absoluta(ano, semanas[0]);
        t.semanaFim = Calendario.absoluta(ano, semanas[semanas.length - 1]);
        for (int s : semanas) t.semanasJogo.add(Calendario.absoluta(ano, s));
        t.planejar();
        return t;
    }

    private static Formato grupos(int n, int grupos, int turnos, int classificam, Integer melhores) {
        Formato f = new Formato();
        f.participantes = n;
        Formato.Fase g = new Formato.Fase("Fase de grupos", "grupos");
        g.grupos = grupos;
        g.turnos = turnos;
        g.classificam = classificam;
        g.melhores_terceiros = melhores;
        f.fases = new ArrayList<>(List.of(g));
        return f;
    }

    private static Formato pontos(int n, int turnos) {
        Formato f = new Formato();
        f.participantes = n;
        Formato.Fase p = new Formato.Fase("Pontos corridos", "pontos_corridos");
        p.turnos = turnos;
        f.fases = new ArrayList<>(List.of(p));
        return f;
    }

    private static Formato mataMata(int n, int classificam, boolean finalUnica) {
        Formato f = new Formato();
        f.participantes = n;
        Formato.Fase mm = new Formato.Fase("Mata-mata", "mata_mata");
        mm.desempate = "prorrogacao_penaltis";
        mm.chaveamento = "1x8";
        mm.classificam = classificam;
        mm.final_jogo_unico = finalUnica;
        f.fases = new ArrayList<>(List.of(mm));
        return f;
    }

    /**
     * Eliminatórias (formatos reais aproximados): CONMEBOL 10 em turno e returno; UEFA 12 grupos
     * (1º direto + 4 melhores 2ºs = 16); CONCACAF 6 grupos (1ºs); CAF 9 grupos de 6; AFC 8 grupos;
     * OFC turno único. Jogos nas datas FIFA do ano anterior à Copa.
     */
    private Torneio eliminatorias(String conf, int ano) {
        List<String> part = daConfederacao(conf);
        int[] janelas = Balanceamento.JANELAS_FIFA;
        String id = idEliminatorias(conf), nome = "Eliminatórias " + conf + " da Copa do Mundo " + (ano + 1);
        Formato f;
        switch (conf) {
            case "CONMEBOL" -> {
                part = part.subList(0, Math.min(10, part.size()));
                f = pontos(part.size(), 2);
            }
            case "UEFA" -> {
                part = part.subList(0, Math.min(54, part.size()));
                f = grupos(part.size(), Math.max(1, Math.min(12, part.size() / 4)), 2, 1, 4);
            }
            case "CONCACAF" -> {
                part = part.subList(0, Math.min(30, part.size()));
                f = grupos(part.size(), Math.max(1, Math.min(6, part.size() / 3)), 2, 1, null);
            }
            case "CAF" -> {
                part = part.subList(0, Math.min(54, part.size()));
                f = grupos(part.size(), Math.max(1, Math.min(9, part.size() / 4)), 2, 1, null);
            }
            case "AFC" -> {
                part = part.subList(0, Math.min(40, part.size()));
                f = grupos(part.size(), Math.max(1, Math.min(8, part.size() / 4)), 2, 1, null);
            }
            default -> {
                part = part.subList(0, Math.min(10, part.size()));
                f = pontos(part.size(), 1);
            }
        }
        return torneioDatasFifa(id, nome, conf, ano, new ArrayList<>(part), f, janelas);
    }

    /** Classificados das eliminatórias encerradas do ano anterior (por confederação, na ordem final). */
    private Torneio anterior(String id, int ano) {
        Torneio a = null;
        for (List<Torneio> l : List.of(m.torneiosAnteriores, m.torneios)) for (Torneio t : l) if (t.id.equals(id) && t.ano == ano && t.encerrado) a = t;
        return a;
    }

    /** Participantes da Copa pelas eliminatórias do ano anterior + repescagem; null se não houve eliminatórias. */
    private List<String> classificadosCopa(int ano) {
        List<String> l = new ArrayList<>();
        boolean alguma = false;
        for (String conf : CONFEDERACOES) {
            Torneio e = anterior(idEliminatorias(conf), ano - 1);
            if (e == null) continue;
            alguma = true;
            int vagas = VAGAS_COPA.getOrDefault(conf, 0);
            for (String id : e.classificacao) if (vagas > 0 && !l.contains(id)) {
                l.add(id);
                vagas--;
            }
        }
        if (!alguma) return null;
        Torneio r = anterior(REPESCAGEM, ano - 1);
        if (r != null) for (String id : r.classificacao.subList(0, Math.min(2, r.classificacao.size()))) if (!l.contains(id)) l.add(id);
        for (Selecao s : ranking()) if (l.size() < 48 && !l.contains(s.id)) l.add(s.id);
        return ordenar(l);
    }

    /**
     * Repescagem intercontinental (logo depois das eliminatórias): o primeiro não classificado de
     * cada confederação (CONCACAF 2) em mata-mata; os 2 vencedores vão à Copa.
     */
    private Torneio repescagem(int ano) {
        List<String> part = new ArrayList<>();
        for (String conf : CONFEDERACOES) {
            Torneio e = anterior(idEliminatorias(conf), ano);
            if (e == null) return null;
            int vagas = VAGAS_COPA.getOrDefault(conf, 0), extra = "UEFA".equals(conf) ? 0 : "CONCACAF".equals(conf) ? 2 : 1;
            for (int i = vagas; i < Math.min(e.classificacao.size(), vagas + extra); i++) part.add(e.classificacao.get(i));
        }
        if (part.size() < 4) return null;
        part = ordenar(part).subList(0, 4);
        int sem = Math.min(51, m.semana + 1);
        return torneioDatasFifa(REPESCAGEM, "Repescagem intercontinental", "FIFA", ano, new ArrayList<>(part), mataMata(4, 2, true), new int[]{sem});
    }

    /** Divisões das Ligas das Nações (país → 1..4), iniciadas pelo ranking. */
    private Map<String, Integer> divisoesNations(String conf, int[] tamanhos) {
        Map<String, Integer> d = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : m.divisaoNations.entrySet()) {
            Selecao s = m.selecao(e.getKey());
            if (s != null && conf.equalsIgnoreCase(s.confederacao)) d.put(e.getKey(), e.getValue());
        }
        if (d.isEmpty()) {
            List<String> l = daConfederacao(conf);
            int k = 0, div = 1;
            for (String id : l) {
                while (div <= tamanhos.length && k >= tamanhos[div - 1]) {
                    div++;
                    k = 0;
                }
                d.put(m.selecao(id).pais, Math.min(div, tamanhos.length));
                k++;
            }
            m.divisaoNations.putAll(d);
        }
        return d;
    }

    private static final String[] LETRAS = {"a", "b", "c", "d"};

    /** Fase de liga da Liga das Nações (anos pares, datas FIFA de setembro a novembro): uma competição por divisão. */
    private List<Torneio> nations(String idBase, String nome, String conf, int ano, int[] tamanhos) {
        Map<String, Integer> d = divisoesNations(conf, tamanhos);
        List<Torneio> l = new ArrayList<>();
        int[] semanas = {37, 41, 46};
        for (int div = 1; div <= tamanhos.length; div++) {
            List<String> part = new ArrayList<>();
            for (Map.Entry<String, Integer> e : d.entrySet()) if (e.getValue() == div) part.add(Selecao.id(e.getKey()));
            if (part.size() < 3) continue;
            ordenar(part);
            int g = Math.max(1, part.size() / 4);
            Torneio t = torneioDatasFifa(idBase + "-" + LETRAS[div - 1], nome + " – Liga " + LETRAS[div - 1].toUpperCase(), conf, ano, part,
                    grupos(part.size(), g, 2, 1, null), semanas);
            if (t != null) l.add(t);
        }
        return l;
    }

    /**
     * Fim da Liga das Nações (quando todas as divisões da confederação no ano terminaram): entre
     * cada par de divisões vizinhas sobem os n melhores 1ºs de grupo e descem os n piores últimos,
     * com n = o menor número de grupos das duas. Antes cada divisão subia/descia um por grupo sem
     * olhar a vizinha (Liga D com 2 grupos recebia 4 e devolvia 2): as divisões mudavam de tamanho
     * (UEFA D de 8 para 12, CONCACAF B de 16 para 8).
     */
    private void promoverNations(Torneio t) {
        String base = t.id.substring(0, t.id.lastIndexOf('-'));
        Torneio[] divs = new Torneio[LETRAS.length];
        for (Torneio x : m.torneios) {
            if (x.ano != t.ano || !x.id.startsWith(base + "-")) continue;
            for (int i = 0; i < LETRAS.length; i++) if (x.id.equals(base + "-" + LETRAS[i])) divs[i] = x;
        }
        for (Torneio x : m.torneiosAnteriores) {
            if (x.ano != t.ano || !x.id.startsWith(base + "-")) continue;
            for (int i = 0; i < LETRAS.length; i++) if (x.id.equals(base + "-" + LETRAS[i]) && divs[i] == null) divs[i] = x;
        }
        for (Torneio x : divs) if (x != null && !x.encerrado) return; // espera as outras divisões
        String chave = base + "#" + t.ano;
        if (!nationsProcessadas.add(chave)) return;
        for (int i = 0; i + 1 < LETRAS.length; i++) {
            Torneio cima = divs[i], baixo = divs[i + 1];
            if (cima == null || baixo == null || cima.fases.isEmpty() || baixo.fases.isEmpty()) continue;
            List<br.futebas.competicao.LinhaTabela> ultimos = new ArrayList<>(), primeiros = new ArrayList<>();
            for (int g = 0; g < cima.fases.get(0).grupos.size(); g++) {
                List<br.futebas.competicao.LinhaTabela> tab = cima.tabela(0, g);
                if (tab.size() >= 2) ultimos.add(tab.get(tab.size() - 1));
            }
            for (int g = 0; g < baixo.fases.get(0).grupos.size(); g++) {
                List<br.futebas.competicao.LinhaTabela> tab = baixo.tabela(0, g);
                if (tab.size() >= 2) primeiros.add(tab.get(0));
            }
            java.util.Comparator<br.futebas.competicao.LinhaTabela> melhor = java.util.Comparator
                    .comparingDouble((br.futebas.competicao.LinhaTabela l) -> -l.pontos / (double) Math.max(1, l.jogos))
                    .thenComparingInt(l -> -l.saldo()).thenComparingInt(l -> -l.golsPro).thenComparing(l -> l.clubeId);
            primeiros.sort(melhor);
            ultimos.sort(melhor.reversed());
            int n = Math.min(primeiros.size(), ultimos.size());
            for (int k = 0; k < n; k++) {
                m.divisaoNations.put(m.selecao(primeiros.get(k).clubeId).pais, i + 1);
                m.divisaoNations.put(m.selecao(ultimos.get(k).clubeId).pais, i + 2);
            }
        }
    }

    /** Ligas das Nações (base#ano) já promovidas/rebaixadas (não precisa ir ao save: cada ano só fecha uma vez). */
    private final java.util.Set<String> nationsProcessadas = new java.util.HashSet<>();

    /** Fase final da Liga das Nações (ano ímpar): os 4 primeiros da Liga A do ano anterior. */
    private Torneio finaisNations(String idBase, String nome, String conf, int ano, int semana) {
        Torneio a = anterior(idBase + "-a", ano - 1);
        if (a == null) return null;
        List<String> part = new ArrayList<>(a.classificacao.subList(0, Math.min(4, a.classificacao.size())));
        if (part.size() < 4) return null;
        return torneioDatasFifa(idBase, nome + " – Finais", conf, ano, part, mataMata(4, 1, true), new int[]{semana, semana + 1});
    }

    /** Finalíssima: campeão da Copa América × campeão da Eurocopa (2026: Argentina × Espanha, campeões de 2024). */
    private Torneio finalissima(int ano) {
        List<String> part = new ArrayList<>();
        if (ano == Balanceamento.ANO_INICIAL) {
            if (m.selecao("ARG") != null) part.add(Selecao.id("ARG"));
            if (m.selecao("ESP") != null) part.add(Selecao.id("ESP"));
        } else {
            String ca = ultimoCampeao("copa-america"), eu = ultimoCampeao("euro");
            if (ca != null) part.add(ca);
            if (eu != null && !eu.equals(ca)) part.add(eu);
        }
        if (part.size() < 2) return null;
        return torneioDatasFifa(FINALISSIMA, "Finalíssima", "CONMEBOL/UEFA", ano, part, mataMata(2, 1, true), new int[]{13});
    }

    private String ultimoCampeao(String trechoId) {
        br.futebas.historia.Historia.Titulo achado = null;
        for (br.futebas.historia.Historia.Titulo t : m.historia.titulos) {
            if (!"selecoes".equals(t.ambito) || t.competicaoId == null || !t.competicaoId.contains(trechoId)) continue;
            if (achado == null || t.ano > achado.ano) achado = t;
        }
        return achado == null ? null : achado.campeaoId;
    }

    /** Copa Ouro da CONCACAF (anos ímpares, junho–julho): 16 seleções. */
    private Torneio copaOuro(int ano) {
        if (daConfederacao("CONCACAF").size() < 8) return null;
        return torneio(COPA_OURO, "Copa Ouro da CONCACAF", "CONCACAF", ano);
    }

    /** Copa das Nações da OFC (Oceania): 8 seleções, a cada 4 anos (a real foi em 2024). */
    public static final String COPA_OFC = "ofc-copa-nacoes";

    private Torneio copaOfc(int ano) {
        if (ano < 2028 || (ano - 2028) % 4 != 0 || daConfederacao("OFC").size() < 4) return null;
        return torneio(COPA_OFC, "Copa das Nações da OFC", "OFC", ano);
    }

    /** Competições de seleções extras do ano (eliminatórias, Ligas das Nações, Copa Ouro, Finalíssima). */
    List<Torneio> extrasDoAno(int ano) {
        List<Torneio> l = new ArrayList<>();
        if ((ano + 1 - 2026) % 4 == 0 && ano > Balanceamento.ANO_INICIAL)
            for (String conf : CONFEDERACOES) addSeNaoNulo(l, eliminatorias(conf, ano));
        if (ano % 2 == 0) {
            l.addAll(nations(NATIONS_UEFA, "Liga das Nações da UEFA", "UEFA", ano, new int[]{16, 16, 16, 16}));
            l.addAll(nations(NATIONS_CONCACAF, "Liga das Nações da CONCACAF", "CONCACAF", ano, new int[]{12, 16, 16}));
        } else {
            addSeNaoNulo(l, finaisNations(NATIONS_UEFA, "Liga das Nações da UEFA", "UEFA", ano, 23));
            addSeNaoNulo(l, finaisNations(NATIONS_CONCACAF, "Liga das Nações da CONCACAF", "CONCACAF", ano, 13));
            addSeNaoNulo(l, copaOuro(ano));
        }
        if ((ano - 2026) % 4 == 0) addSeNaoNulo(l, finalissima(ano));
        addSeNaoNulo(l, copaOfc(ano));
        return l;
    }

    /** Depois de cada torneio de seleções: Liga das Nações sobe/desce; eliminatórias completas → repescagem. */
    void depoisDoTorneio(Torneio t) {
        if (t.id.startsWith(NATIONS_UEFA + "-") || t.id.startsWith(NATIONS_CONCACAF + "-")) promoverNations(t);
        if (t.id.startsWith("eliminatorias-") && repescagemPendente(t.ano)) {
            Torneio r = repescagem(t.ano);
            if (r != null) m.torneios.add(r);
        }
    }

    private boolean repescagemPendente(int ano) {
        for (List<Torneio> l : List.of(m.torneios, m.torneiosAnteriores)) for (Torneio t : l) if (t.id.equals(REPESCAGEM) && t.ano == ano) return false;
        for (String conf : CONFEDERACOES) if (anterior(idEliminatorias(conf), ano) == null) return false;
        return true;
    }
}
