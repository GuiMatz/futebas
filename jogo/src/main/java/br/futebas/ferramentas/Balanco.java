package br.futebas.ferramentas;

import br.futebas.Jogo;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.historia.Historia;
import br.futebas.ia.Mercado;
import br.futebas.ia.PropostaTecnico;
import br.futebas.kings.KingsCustom;
import br.futebas.kings.LigaKings;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Origem;
import br.futebas.mundo.Passagem;
import br.futebas.mundo.Selecao;
import br.futebas.mundo.Sonda;
import br.futebas.mundo.StatusJogador;
import br.futebas.mundo.TipoContrato;
import br.futebas.mundo.Treinador;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.ResultadoPartida;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Simulação longa com técnico humano (clube da Série A, opcionalmente Kings custom) medindo
 * finanças, resultados, jogadores, técnicos, notícias e Kings League. Só observa (via {@link Sonda}).
 * <pre>uso: Balanco [--semente 1000] [--fim 2045] [--clube internacional] [--custom 0|1] [--saida arquivo]</pre>
 */
public final class Balanco implements Sonda {
    final Jogo jogo;
    final Mundo m;
    final PrintStream out;

    Balanco(Jogo jogo, PrintStream out) {
        this.jogo = jogo;
        this.m = jogo.mundo();
        this.out = out;
    }

    // ================================================================== contadores
    /** Por competição (id base): jogos, gols, mandante, empate, visitante, zebras (mais fraco por ≥ 5 vence), jogos com diferença ≥ 5, amarelos, vermelhos, jogos detalhados. */
    final Map<String, long[]> porComp = new TreeMap<>();
    final Map<String, long[]> porCompAno = new TreeMap<>();
    long lesoes, lesoesSemanas, lesoesLongas;
    final long[] lesaoHist = new long[30];
    final Map<Integer, Long> lesoesAno = new TreeMap<>();
    // transferências
    final Map<Integer, List<Long>> taxasAno = new TreeMap<>();
    final Map<Integer, long[]> transfAno = new TreeMap<>(); // total, pagas, livres, fora da janela (com clube de origem), internacionais
    final long[] transfSemana = new long[53];
    final List<String> foraDaJanela = new ArrayList<>();
    final Map<Integer, Long> demissoesAno = new TreeMap<>();
    final Map<Integer, Long> demissoesBra1 = new TreeMap<>();
    final Map<Integer, Map<String, Integer>> demissoesLiga = new TreeMap<>();
    // Kings
    final Map<String, long[]> kings = new TreeMap<>();
    /** jogos, gols, shootouts, match balls, min de match ball, viradas no match ball, cartas usadas, pres. cobrados, pres. gols, faltas 1v1 (shootout fora das cartas), amarelos, vermelhos, gols de presidente-jogador, gols totais com autor, shootout gols(carta+falta) */
    static final String[] KINGS_COL = {"jogos", "gols", "shootouts", "matchball", "minMB", "viradaMB", "cartas", "presCob", "presGol", "falta1v1", "amar", "verm",
            "golPresJog", "golAutor", "mbMax"};
    final Map<String, Map<String, Integer>> artilhariaKings = new TreeMap<>();
    final Map<String, Integer> dadoFaces = new TreeMap<>();
    final Map<String, Integer> cartasUsadas = new TreeMap<>();
    // notícias
    final Set<Historia.Noticia> vistas = Collections.newSetFromMap(new IdentityHashMap<>());
    final Map<Integer, Map<String, Integer>> noticiasTipo = new TreeMap<>();
    final Map<Integer, int[]> noticiasFoco = new TreeMap<>();
    final Map<Integer, Integer> noticiasDup = new TreeMap<>();
    final Map<Integer, Set<String>> titulosAno = new TreeMap<>();
    final Map<String, Integer> manchetesRepetidas = new HashMap<>();
    int maxNoticiasSemana;
    // aposentadorias
    final Map<Integer, Integer> anuncio = new HashMap<>(); // jogador → semana absoluta do anúncio
    final Map<Integer, StatusJogador> statusAntes = new HashMap<>();
    final Map<Integer, List<Integer>> idadesAposent = new TreeMap<>();
    final Map<Integer, int[]> aposentAno = new TreeMap<>(); // total, de profissional, sem anúncio (prof.), desistiu (livre), anúncio < 20 semanas
    // torneios
    final Set<Torneio> encerrados = Collections.newSetFromMap(new IdentityHashMap<>());
    final Set<Torneio> iniciados = Collections.newSetFromMap(new IdentityHashMap<>());
    final Map<String, TreeMap<Integer, Integer>> participantes = new TreeMap<>();
    final Map<String, List<String>> ultimaTabela = new HashMap<>();
    final Map<String, Set<String>> participantesLiga = new HashMap<>();
    int checagensAcesso, falhasAcesso;
    final List<String> falhasAcessoEx = new ArrayList<>();
    final Map<Integer, String> serieD = new TreeMap<>();
    final List<String> kingsEmpates = new ArrayList<>();
    int kingsTabelas;
    final Map<String, String> excecoes = new LinkedHashMap<>();
    final List<String> linhasAno = new ArrayList<>();

    static String base(String id) {
        return id.replaceAll("-\\d{4}$", "");
    }

    long[] comp(Map<String, long[]> mp, String k, int n) {
        return mp.computeIfAbsent(k, x -> new long[n]);
    }

    double forcaDe(Torneio t, String id) {
        if ("selecoes".equals(t.ambito)) {
            Selecao s = m.selecao(id);
            return s == null ? 0 : s.forca;
        }
        Clube c = m.clube(id);
        return c == null ? 0 : c.nivelAtual;
    }

    @Override
    public void partida(Torneio t, Partida p, ResultadoPartida r) {
        if ("amistoso".equals(t.ambito)) return;
        String k = "nacional".equals(t.ambito) ? base(t.id) + (p.rodadaMata >= 0 ? "#mm" : "") : base(t.id);
        if ("estadual".equals(t.ambito)) k = "estaduais-" + t.pais;
        long[] a = comp(porComp, k, 12);
        long[] b = comp(porCompAno, m.ano + "|" + ("nacional".equals(t.ambito) && t.divisao != null && p.rodadaMata < 0 ? "ligas" : t.ambito), 12);
        double fm = forcaDe(t, p.mandante), fv = forcaDe(t, p.visitante);
        for (long[] x : new long[][]{a, b}) {
            x[0]++;
            x[1] += r.golsMandante + r.golsVisitante;
            if (r.golsMandante > r.golsVisitante) x[2]++;
            else if (r.golsMandante == r.golsVisitante) x[3]++;
            else x[4]++;
            if ((fm + 5 <= fv && r.golsMandante > r.golsVisitante) || (fv + 5 <= fm && r.golsVisitante > r.golsMandante)) x[5]++;
            if (Math.abs(fm - fv) >= 5) x[6]++;
            if (!r.eventos.isEmpty() || !r.lances.isEmpty()) {
                x[9]++;
                x[7] += r.amarelos.size();
                x[8] += r.vermelhos.size();
            }
            if (Math.abs(r.golsMandante - r.golsVisitante) >= 5) x[10]++;
        }
    }

    @Override
    public void partidaKings(Torneio t, Partida p, ResultadoPartida r) {
        String tipo = t.id.startsWith("kings-ios") ? (t.id.endsWith("-cup") ? "IOS-cup" : "IOS-split")
                : t.id.startsWith("kings-mundial-clubes") ? "KWC-clubes" : t.id.startsWith("kings-mundial-selecoes") ? "KWC-nacoes"
                : t.id.endsWith("-cup") ? "reais-cup" : "reais-split";
        long[] k = comp(kings, tipo, KINGS_COL.length);
        k[0]++;
        k[1] += r.golsMandante + r.golsVisitante;
        if (r.penaltisMandante != null) k[2]++;
        boolean mb = false;
        int minMb = -1;
        int[] placar36 = new int[2];
        int cartaPs = 0, shootoutEv = 0;
        for (EventoJogo e : r.eventos) {
            if ("fase".equals(e.tipo()) && "Match ball".equals(e.jogador())) mb = true;
            if (e.gol() && e.minuto() < 36) placar36[e.lado()] += e.valor();
            if (mb && e.gol()) minMb = Math.max(minMb, e.minuto());
            switch (e.tipo()) {
                case "carta_usada" -> {
                    if (!e.jogador().startsWith("Coringa →")) k[6]++;
                    cartasUsadas.merge(e.jogador(), 1, Integer::sum);
                    if (e.jogador().endsWith("Shoot Out")) cartaPs++;
                }
                case "penalti_presidente_gol" -> { k[7]++; k[8]++; }
                case "penalti_presidente_perdido" -> k[7]++;
                case "amarelo" -> k[10]++;
                case "vermelho" -> k[11]++;
                case "dado" -> dadoFaces.merge(e.jogador(), 1, Integer::sum);
                default -> {}
            }
            String tb = e.tipo().replace("_x2", "");
            if (tb.equals("shootout_gol") || tb.equals("shootout_perdido")) shootoutEv++;
            if (e.gol() && e.jogador() != null && !e.tipo().startsWith("penalti_presidente")) {
                k[13] += e.valor();
                LigaKings.TimeKings tk = jogo.timeKings(e.lado() == 0 ? p.mandante : p.visitante);
                boolean pres = false;
                if (tk != null) for (LigaKings.AtletaKings x : tk.elenco) if (x.presidente && x.nome.equals(e.jogador())) pres = true;
                if (pres) k[12] += e.valor();
                String chave = t.id.replaceAll("-\\d{4}$", "") + " " + t.ano;
                artilhariaKings.computeIfAbsent(chave, x -> new HashMap<>()).merge(e.jogador() + (pres ? " [PRES]" : "") + " (" + (tk == null ? "?" : tk.nome) + ")",
                        e.valor(), Integer::sum);
            }
            // rodada 2: o gol do pênalti do presidente é do time e não entra na artilharia (regra do motor)
        }
        k[9] += Math.max(0, shootoutEv - cartaPs);
        if (mb) {
            k[3]++;
            int dur = Math.max(1, minMb - 36 + 1);
            k[4] += dur;
            k[14] = Math.max(k[14], dur);
            int lider = placar36[0] > placar36[1] ? 0 : 1;
            int venc = r.golsMandante > r.golsVisitante ? 0 : 1;
            if (lider != venc) k[5]++;
        }
    }

    @Override
    public void transferencia(Jogador j, Clube origem, Clube destino, long preco) {
        long[] t = transfAno.computeIfAbsent(m.ano, x -> new long[6]);
        t[0]++;
        if (preco > 0) {
            t[1]++;
            taxasAno.computeIfAbsent(m.ano, x -> new ArrayList<>()).add(preco);
        } else t[2]++;
        if (origem != null && !origem.pais.equals(destino.pais)) t[4]++;
        transfSemana[Math.min(52, m.semana)]++;
        if (origem != null && !Mercado.janelaAberta(destino.calendario, m.semana)) {
            t[3]++;
            if (foraDaJanela.size() < 15) foraDaJanela.add(m.ano + "/s" + m.semana + " " + j.nomeExibicao() + " " + origem.nome + " → " + destino.nome + " ("
                    + destino.calendario + ", R$ " + preco + ")");
        }
    }

    @Override
    public void lesao(Jogador j, int semanas) {
        lesoes++;
        lesoesSemanas += semanas;
        if (semanas >= 10) lesoesLongas++;
        lesaoHist[Math.min(29, semanas)]++;
        lesoesAno.merge(m.ano, 1L, Long::sum);
    }

    @Override
    public void demissao(Clube c) {
        demissoesAno.merge(m.ano, 1L, Long::sum);
        if (c.divisao == 1) demissoesLiga.computeIfAbsent(m.ano, x -> new TreeMap<>()).merge(c.pais, 1, Integer::sum);
    }

    // ================================================================== semana a semana
    /** Empréstimos ativos na semana 40 (no fim do ano todos já voltaram): {emprestados, profissionais}. */
    final int[] emprestMeio = new int[2];

    void semana() {
        if (m.semana == 40) {
            emprestMeio[0] = 0;
            emprestMeio[1] = 0;
            for (Jogador j : m.jogadores) {
                if (j.status != StatusJogador.PROFISSIONAL || j.amador()) continue;
                emprestMeio[1]++;
                if (j.contrato != null && j.contrato.tipo == TipoContrato.EMPRESTIMO) emprestMeio[0]++;
            }
        }
        int sa = m.semanaAbsoluta();
        // notícias novas
        int novas = 0;
        for (Historia.Noticia n : m.historia.noticias) {
            if (!vistas.add(n)) continue;
            novas++;
            noticiasTipo.computeIfAbsent(n.ano, x -> new TreeMap<>()).merge(n.tipo, 1, Integer::sum);
            noticiasFoco.computeIfAbsent(n.ano, x -> new int[4])[Math.max(0, Math.min(3, n.foco))]++;
            if (!titulosAno.computeIfAbsent(n.ano, x -> new HashSet<>()).add(n.titulo)) {
                noticiasDup.merge(n.ano, 1, Integer::sum);
                manchetesRepetidas.merge(n.titulo, 1, Integer::sum);
            }
        }
        maxNoticiasSemana = Math.max(maxNoticiasSemana, novas);
        if (vistas.size() > 5000) {
            vistas.clear();
            vistas.addAll(m.historia.noticias);
        }
        // aposentadorias
        for (Jogador j : m.jogadores) {
            if (j.vaiAposentar) anuncio.putIfAbsent(j.id, sa);
            StatusJogador antes = statusAntes.put(j.id, j.status);
            if (j.status == StatusJogador.APOSENTADO && antes != null && antes != StatusJogador.APOSENTADO) {
                int[] a = aposentAno.computeIfAbsent(m.ano, x -> new int[5]);
                a[0]++;
                if (antes == StatusJogador.PROFISSIONAL) {
                    a[1]++;
                    Integer an = anuncio.get(j.id);
                    if (an == null) a[2]++;
                    else if (sa - an < 20) a[4]++;
                    idadesAposent.computeIfAbsent(m.ano, x -> new ArrayList<>()).add(j.idade(m.ano));
                } else if (antes == StatusJogador.LIVRE) a[3]++;
                anuncio.remove(j.id);
            }
        }
        // torneios
        List<Torneio> todos = new ArrayList<>(m.torneios);
        todos.addAll(m.torneiosAnteriores);
        for (Torneio t : todos) {
            if (iniciados.add(t)) {
                inicioTorneio(t);
                String b = base(t.id);
                if (CHAVE.contains(b) || ("selecoes".equals(t.ambito) && !b.startsWith("eliminatorias") && !b.contains("nations-league-"))) {
                    List<String> p = new ArrayList<>(t.participantes);
                    p.sort(Comparator.comparingDouble((String id) -> -forcaDe(t, id)));
                    rankInicial.put(t.id + "#" + t.ano, p);
                    Map<String, Double> fz = new HashMap<>();
                    for (String id : p) fz.put(id, forcaDe(t, id));
                    forcaEntrada.put(t.id + "#" + t.ano, fz);
                }
            }
            entraramDepois(t);
            if (t.encerrado && encerrados.add(t)) fimTorneio(t);
        }
        List<Torneio> ks = new ArrayList<>(m.kingsMundiais);
        for (LigaKings l : m.kings) ks.addAll(l.competicoes);
        for (Torneio t : ks) if (t.encerrado && encerrados.add(t)) fimKings(t);
        if (encerrados.size() > 20000) encerrados.retainAll(new HashSet<>(todos)); // memória
        if (iniciados.size() > 20000) iniciados.retainAll(new HashSet<>(todos));
    }

    void inicioTorneio(Torneio t) {
        if (!"nacional".equals(t.ambito) || t.divisao == null) return;
        String chave = t.pais + "#" + t.divisao;
        Set<String> part = new HashSet<>(t.participantes);
        // campeão da divisão de baixo sobe; último da de cima desce
        List<String> baixo = ultimaTabela.get(t.pais + "#" + (t.divisao + 1));
        List<String> cima = ultimaTabela.get(t.pais + "#" + (t.divisao - 1));
        List<String> mesma = ultimaTabela.get(chave);
        if (baixo != null && !baixo.isEmpty() && mesma != null) {
            checagensAcesso++;
            if (!part.contains(baixo.get(0))) {
                falhasAcesso++;
                if (falhasAcessoEx.size() < 25) falhasAcessoEx.add(t.ano + " " + t.id + ": campeão da div " + (t.divisao + 1) + " (" + nome(baixo.get(0)) + ") não subiu");
            }
        }
        if (cima != null && !cima.isEmpty() && mesma != null) {
            checagensAcesso++;
            if (!part.contains(cima.get(cima.size() - 1))) {
                falhasAcesso++;
                if (falhasAcessoEx.size() < 25) falhasAcessoEx.add(t.ano + " " + t.id + ": último da div " + (t.divisao - 1) + " (" + nome(cima.get(cima.size() - 1)) + ") não caiu");
            }
        }
        if ("BRA".equals(t.pais) && t.divisao == 4) {
            Set<String> antes = participantesLiga.getOrDefault(chave, Set.of());
            Set<String> d3 = participantesLiga.getOrDefault("BRA#3", Set.of());
            int novos = 0, deEstaduais = 0;
            for (String c : t.participantes) {
                if (antes.contains(c) || d3.contains(c)) continue;
                novos++;
                deEstaduais++;
            }
            serieD.put(t.ano, t.participantes.size() + " clubes, " + novos + " novos (fora da D e da C do ano anterior: vindos dos estaduais)");
        }
        participantesLiga.put(chave, part);
    }

    String nome(String id) {
        Clube c = m.clube(id);
        return c == null ? id : c.nome;
    }

    void fimTorneio(Torneio t) {
        if ("amistoso".equals(t.ambito)) return;
        if (!"nacional".equals(t.ambito) && !"estadual".equals(t.ambito) || ("nacional".equals(t.ambito) && t.divisao != null && t.divisao <= 2 && List.of("BRA", "ENG", "ESP").contains(t.pais)))
            participantes.computeIfAbsent(base(t.id), x -> new TreeMap<>()).put(t.ano, t.participantes.size());
        if ("nacional".equals(t.ambito) && t.divisao != null && !t.classificacao.isEmpty()) ultimaTabela.put(t.pais + "#" + t.divisao, new ArrayList<>(t.classificacao));
    }

    void fimKings(Torneio t) {
        for (FaseExec f : t.fases) {
            if (f.mataMata() || !f.iniciada) continue;
            for (int g = 0; g < f.grupos.size(); g++) {
                kingsTabelas++;
                for (LinhaTabela l : t.tabela(f.indice, g)) {
                    if (l.empates > 0 && kingsEmpates.size() < 12) {
                        LigaKings.TimeKings tk = jogo.timeKings(l.clubeId);
                        kingsEmpates.add(t.id + " " + t.ano + " " + (tk == null ? l.clubeId : tk.nome) + ": V" + l.vitorias + " E" + l.empates + " D" + l.derrotas + ", " + l.pontos + " pts");
                    }
                    if (l.empates > 0) kingsEmpatesTotal++;
                }
            }
        }
    }

    int kingsEmpatesTotal, contratacoesHumano;

    // ================================================================== fim do ano
    static double q(List<Double> l, double p) {
        if (l.isEmpty()) return Double.NaN;
        List<Double> s = new ArrayList<>(l);
        Collections.sort(s);
        return s.get((int) Math.min(s.size() - 1, Math.floor(p * s.size())));
    }

    static String classe(Jogador j) {
        if (j.dbId == null) return j.amador() ? "amador" : "newgen";
        return j.origem == Origem.REAL ? "real" : "dbgerado";
    }

    static final List<String> LIGAS_FIN = List.of("bra-1", "bra-2", "bra-3", "bra-4", "eng-1", "esp-1", "ita-1", "deu-1", "fra-1", "prt-1", "arg-1", "usa-1", "sau-1", "jpn-1");

    void fimDoAno(int ano, double tempo) {
        StringBuilder sb = new StringBuilder();
        Historia.EstatAno e = m.historia.estat(ano);
        Runtime rt = Runtime.getRuntime();
        sb.append(String.format(Locale.ROOT, "%n######## %d  (%.1f s, heap %d MB)  falências %d | transf %d | demissões %d | newgens %d | aposent %d | ativos %d | vivos %d arq %d%n",
                ano, tempo, (rt.totalMemory() - rt.freeMemory()) >> 20, e.falencias, e.transferencias, e.demissoesTecnicos, e.newgens, e.aposentadorias, e.jogadoresAtivos,
                m.jogadores.size(), m.arquivados.size()));
        // humano
        if (m.tecnico != null) {
            Clube c = m.clube(m.tecnico.clubeId);
            sb.append(String.format(Locale.ROOT, "  humano: %s %s (div %s, pos %s) | diretoria %.0f | V-E-D %d-%d-%d | títulos %d | kings %s%n", m.tecnico.nome,
                    c == null ? "(sem clube)" : c.nome, c == null ? "-" : c.divisao, c == null ? "-" : c.posicaoDivisao, c == null ? 0 : c.confiancaDiretoria,
                    m.tecnico.vitorias, m.tecnico.empates, m.tecnico.derrotas, m.tecnico.titulos.size(), m.tecnico.kingsTimeId));
        }
        // finanças por liga
        sb.append("  finanças (R$ mi): liga n | caixa p10/p50/p90/max | dívida p90 | folha/receita p50/p90 | receita p50 | caixa<0 | nível p50/max\n");
        Map<String, List<Clube>> porLiga = new LinkedHashMap<>();
        for (Torneio t : m.torneiosAnteriores) if (LIGAS_FIN.contains(base(t.id))) porLiga.put(base(t.id), clubes(t));
        for (Torneio t : m.torneios) if (LIGAS_FIN.contains(base(t.id)) && !porLiga.containsKey(base(t.id))) porLiga.put(base(t.id), clubes(t));
        for (String lid : LIGAS_FIN) {
            List<Clube> cs = porLiga.get(lid);
            if (cs == null || cs.isEmpty()) continue;
            List<Double> caixa = new ArrayList<>(), div = new ArrayList<>(), razao = new ArrayList<>(), rec = new ArrayList<>(), niv = new ArrayList<>();
            int neg = 0;
            for (Clube c : cs) {
                caixa.add(c.dinheiro / 1e6);
                div.add(c.divida / 1e6);
                niv.add(c.nivel());
                long folha = 0;
                for (int id : c.elenco) {
                    Jogador j = m.jogador(id);
                    if (j != null && j.contrato != null) folha += j.contrato.salario;
                }
                long r = c.financasAnterior == null ? 0 : c.financasAnterior.receitas() - c.financasAnterior.vendas;
                if (r > 0) razao.add(folha * 12.0 / r);
                rec.add(r / 1e6);
                if (c.dinheiro < 0) neg++;
            }
            sb.append(String.format(Locale.ROOT, "    %-6s %2d | %8.1f %8.1f %8.1f %8.1f | %7.1f | %.2f %.2f | %8.1f | %d | %.1f/%.1f%n", lid, cs.size(), q(caixa, .1), q(caixa, .5), q(caixa, .9),
                    q(caixa, 1), q(div, .9), q(razao, .5), q(razao, .9), q(rec, .5), neg, q(niv, .5), q(niv, 1)));
        }
        // mundo: caixa
        List<Double> todosCaixa = new ArrayList<>();
        int negativos = 0, existentes = 0;
        Clube rico = null;
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe) continue;
            existentes++;
            if (c.dinheiro < 0) negativos++;
            todosCaixa.add(c.dinheiro / 1e6);
            if (rico == null || c.dinheiro > rico.dinheiro) rico = c;
        }
        sb.append(String.format(Locale.ROOT, "    mundo: %d clubes, caixa<0 %d (%.1f%%), p50 %.1f p99 %.1f | mais rico %s %.0f mi%n", existentes, negativos, 100.0 * negativos / existentes,
                q(todosCaixa, .5), q(todosCaixa, .99), rico == null ? "-" : rico.nome, rico == null ? 0 : rico.dinheiro / 1e6));
        List<Long> taxas = taxasAno.getOrDefault(ano, List.of());
        List<Double> td = new ArrayList<>();
        for (long x : taxas) td.add(x / 1e6);
        long[] tr = transfAno.getOrDefault(ano, new long[6]);
        sb.append(String.format(Locale.ROOT, "  transferências (sonda): %d (pagas %d, livres %d, internacionais %d) | FORA DA JANELA c/ clube: %d | taxa p50 %.2f p90 %.1f p99 %.1f max %.1f mi | volume %.0f mi%n",
                tr[0], tr[1], tr[2], tr[4], tr[3], q(td, .5), q(td, .9), q(td, .99), q(td, 1), e.volumeTransferencias / 1e6));
        // jogadores
        Map<String, List<Double>> forcaClasse = new TreeMap<>();
        List<Jogador> prof = new ArrayList<>();
        int livres = 0, livres60 = 0, emprest = 0, lesionados = 0, base = 0;
        List<Double> potNewgen = new ArrayList<>();
        Map<Integer, List<Double>> porIdadeTop = new TreeMap<>();
        for (Jogador j : m.jogadores) {
            if (j.status == StatusJogador.LIVRE) {
                livres++;
                if (j.forca >= 60) livres60++;
            }
            if (j.status == StatusJogador.BASE) base++;
            if (j.status != StatusJogador.PROFISSIONAL || j.amador()) continue;
            prof.add(j);
            forcaClasse.computeIfAbsent(classe(j), x -> new ArrayList<>()).add(j.forca);
            if (j.contrato != null && j.contrato.tipo == TipoContrato.EMPRESTIMO) emprest++;
            if (j.lesaoSemanas > 0) lesionados++;
            if (j.dbId == null && j.idade(ano) <= 19) potNewgen.add(j.potencial);
        }
        prof.sort(Comparator.comparingDouble((Jogador j) -> -j.forca));
        sb.append("  força dos profissionais por origem: n | p50/p90/p99/max | ≥80 ≥85 ≥90\n");
        for (Map.Entry<String, List<Double>> x : forcaClasse.entrySet()) {
            List<Double> l = x.getValue();
            long c80 = l.stream().filter(v -> v >= 80).count(), c85 = l.stream().filter(v -> v >= 85).count(), c90 = l.stream().filter(v -> v >= 90).count();
            sb.append(String.format(Locale.ROOT, "    %-9s %6d | %.1f/%.1f/%.1f/%.1f | %d %d %d%n", x.getKey(), l.size(), q(l, .5), q(l, .9), q(l, .99), q(l, 1), c80, c85, c90));
        }
        Map<String, Integer> top100 = new TreeMap<>(), top1000 = new TreeMap<>();
        double idadeTop = 0;
        for (int i = 0; i < Math.min(1000, prof.size()); i++) {
            Jogador j = prof.get(i);
            if (i < 100) {
                top100.merge(classe(j), 1, Integer::sum);
                idadeTop += j.idade(ano);
            }
            top1000.merge(classe(j), 1, Integer::sum);
            porIdadeTop.computeIfAbsent(j.idade(ano), x -> new ArrayList<>()).add(j.forca);
        }
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(5, prof.size()); i++) {
            Jogador j = prof.get(i);
            top.append(String.format(Locale.ROOT, "%s %.0f (%d, %s, %s); ", j.nomeExibicao(), j.forca, j.idade(ano), classe(j), nome(j.clubeId)));
        }
        sb.append(String.format(Locale.ROOT, "  top100 %s idade média %.1f | top1000 %s | top5: %s%n", top100, idadeTop / Math.max(1, Math.min(100, prof.size())), top1000, top));
        sb.append(String.format(Locale.ROOT, "  newgens ≤19 anos: %d, potencial p50/p90/p99 %.1f/%.1f/%.1f | livres %d (≥60: %d) | base %d | emprestados %d (semana 40: %d de %d profissionais, %.1f%%) | lesionados agora %d (%.1f%%)%n",
                potNewgen.size(), q(potNewgen, .5), q(potNewgen, .9), q(potNewgen, .99), livres, livres60, base, emprest, emprestMeio[0], emprestMeio[1], 100.0 * emprestMeio[0] / Math.max(1, emprestMeio[1]), lesionados, 100.0 * lesionados / Math.max(1, prof.size())));
        int[] ap = aposentAno.getOrDefault(ano, new int[5]);
        List<Double> ida = new ArrayList<>();
        for (int x : idadesAposent.getOrDefault(ano, List.of())) ida.add((double) x);
        sb.append(String.format(Locale.ROOT, "  aposentadorias: %d (de profissional %d, SEM anúncio %d, anúncio < 20 sem %d, desistiram livres %d) | idade p10/p50/p90 %.0f/%.0f/%.0f%n",
                ap[0], ap[1], ap[2], ap[4], ap[3], q(ida, .1), q(ida, .5), q(ida, .9)));
        // técnicos
        int empregados = 0, exJog = 0, exJogEmp = 0, livresT = 0;
        for (Treinador t : m.treinadores) {
            if (t.aposentado) continue;
            boolean emp = t.clubeId != null || t.selecaoPais != null;
            if (emp) empregados++;
            else livresT++;
            if ("ex_jogador".equals(t.origem)) {
                exJog++;
                if (emp) exJogEmp++;
            }
        }
        sb.append(String.format(Locale.ROOT, "  técnicos: demissões %d (1ª div por país %s) | empregados %d, livres %d | ex-jogadores %d (empregados %d)%n",
                demissoesAno.getOrDefault(ano, 0L), demissoesLiga.getOrDefault(ano, Map.of()), empregados, livresT, exJog, exJogEmp));
        // partidas do ano
        for (Map.Entry<String, long[]> x : porCompAno.entrySet()) {
            if (!x.getKey().startsWith(ano + "|")) continue;
            long[] a = x.getValue();
            sb.append(String.format(Locale.ROOT, "  jogos %-16s %7d | %.2f gols | M/E/V %.1f/%.1f/%.1f | zebra %.1f%% (dos desiguais %.1f%%) | cartões/j (det. %d) %.2f am %.3f verm%n",
                    x.getKey().substring(5), a[0], a[1] / (double) a[0], 100.0 * a[2] / a[0], 100.0 * a[3] / a[0], 100.0 * a[4] / a[0], 100.0 * a[5] / a[0],
                    100.0 * a[5] / Math.max(1, a[6]), a[9], a[7] / (double) Math.max(1, a[9]), a[8] / (double) Math.max(1, a[9])));
        }
        sb.append(String.format(Locale.ROOT, "  lesões no ano: %d%n", lesoesAno.getOrDefault(ano, 0L)));
        // títulos
        StringBuilder tit = new StringBuilder("  títulos: ");
        for (Historia.Titulo t : m.historia.titulos) {
            if (t.ano != ano) continue;
            String b = base(t.competicaoId);
            if (CHAVE.contains(b) || "selecoes".equals(t.ambito) && !b.startsWith("eliminatorias") && !b.contains("nations-league-") || b.startsWith("kings-ios")
                    || b.startsWith("kings-mundial") || b.equals("kings-brasil-split1") || b.equals("kings-espanha-split1"))
                tit.append(b).append('=').append(t.campeao).append(postoForca(t)).append("; ");
        }
        sb.append(tit).append('\n');
        // kings: elencos
        if (ano % 5 == 0 || ano == 2026) {
            for (LigaKings l : m.kings) {
                if (l.times.isEmpty()) continue;
                int mn = 99, mx = 0, pres = 0, ativosClube = 0;
                StringBuilder exElite = new StringBuilder();
                List<Double> fs = new ArrayList<>(), fp = new ArrayList<>();
                for (LigaKings.TimeKings tk : l.times) {
                    mn = Math.min(mn, tk.elenco.size());
                    mx = Math.max(mx, tk.elenco.size());
                    for (LigaKings.AtletaKings a : tk.elenco) {
                        if (a.presidente) {
                            pres++;
                            fp.add(a.forca);
                        } else fs.add(a.forca);
                        Jogador j = a.jogadorId == null ? null : m.ativo(a.jogadorId);
                        if (j != null && j.status == StatusJogador.PROFISSIONAL) {
                            Clube c = m.clube(j.clubeId);
                            if (c != null && c.divisao == 1 && c.nivel() >= 65) {
                                ativosClube++;
                                if (ativosClube <= 3) exElite.append(String.format(Locale.ROOT, "%s %.0f %d anos %s(%s, nível %.0f, %s); ", a.nome, j.forca, j.idade(ano), c.nome, c.pais, c.nivel(), a.tipo));
                            }
                        }
                    }
                }
                sb.append(String.format(Locale.ROOT, "  kings %s: %d times, elenco %d–%d, presidentes-jogadores %d (força %s), ativa %s, temporadas %d | força dos outros p50/p90/max %.0f/%.0f/%.0f | jogando na elite (1ª div nível ≥ 65) %d%n",
                        l.id, l.times.size(), mn, mx, pres, fp.isEmpty() ? "-" : String.format(Locale.ROOT, "%.0f", q(fp, .5)), l.ativa, l.temporadas, q(fs, .5), q(fs, .9), q(fs, 1), ativosClube));
                if (exElite.length() > 0) sb.append("      ex.: ").append(exElite).append("%n".formatted());
            }
            for (Torneio t : m.kingsMundiais) if (t.ano == ano) {
                StringBuilder p = new StringBuilder();
                for (String id : t.participantes) {
                    LigaKings.TimeKings tk = jogo.timeKings(id);
                    p.append(tk == null ? id : tk.nome).append(", ");
                }
                sb.append("  ").append(t.id).append(" ").append(t.ano).append(" (").append(t.participantes.size()).append("): ").append(p).append('\n');
            }
        }
        // artilharia kings do ano (IOS e duas reais)
        for (Map.Entry<String, Map<String, Integer>> x : artilhariaKings.entrySet()) {
            if (!x.getKey().endsWith(" " + ano)) continue;
            if (!x.getKey().startsWith("kings-ios") && !x.getKey().startsWith("kings-brasil") && !x.getKey().startsWith("kings-mundial")) continue;
            List<Map.Entry<String, Integer>> l = new ArrayList<>(x.getValue().entrySet());
            l.sort((a, b) -> b.getValue() - a.getValue());
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < Math.min(6, l.size()); i++) s.append(l.get(i).getKey()).append(' ').append(l.get(i).getValue()).append("; ");
            sb.append("  artilharia ").append(x.getKey()).append(": ").append(s).append('\n');
        }
        if (ano % 4 == 2 || ano % 4 == 1) {
            List<Selecao> sel = new ArrayList<>(m.selecoes.values());
            sel.sort(Comparator.comparingDouble((Selecao x) -> -x.forca));
            StringBuilder ss = new StringBuilder("  seleções força/ref: ");
            for (int i = 0; i < Math.min(16, sel.size()); i++) ss.append(String.format(Locale.ROOT, "%s %.1f/%.1f, ", sel.get(i).pais, sel.get(i).forca, sel.get(i).forcaReferencia));
            sb.append(ss).append('\n');
        }
        out.print(sb);
        out.flush();
        if (ano % 5 == 0 || ano == 2026) {
            PrintStream antes = System.out;
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
            try {
                Diagnostico.forcas(m, String.valueOf(ano));
            } finally {
                System.setOut(antes);
            }
            out.print(buf.toString(StandardCharsets.UTF_8));
        }
        // peak/idade (só o último ano é guardado)
        ultimoPorIdade = porIdadeTop;
    }

    Map<Integer, List<Double>> ultimoPorIdade = Map.of();
    /** Posto do campeão na força entre os participantes, por competição: soma e contagem. */
    final Map<String, int[]> postoCampeao = new TreeMap<>();
    /** Participantes ordenados pela força no começo de cada competição-chave (id#ano). */
    final Map<String, List<String>> rankInicial = new HashMap<>();
    /** Força de cada participante quando entrou (os que chegam depois, como os 3ºs da Libertadores na Sul-Americana). */
    final Map<String, Map<String, Double>> forcaEntrada = new HashMap<>();

    /** Participantes que entraram no meio da competição: entram no ranking com a força de quando chegaram. */
    void entraramDepois(Torneio t) {
        String k = t.id + "#" + t.ano;
        List<String> rk = rankInicial.get(k);
        Map<String, Double> fz = forcaEntrada.get(k);
        if (rk == null || fz == null || rk.size() >= t.participantes.size()) return;
        for (String id : t.participantes) if (!fz.containsKey(id)) fz.put(id, forcaDe(t, id));
        List<String> novo = new ArrayList<>(fz.keySet());
        novo.sort(Comparator.comparingDouble((String id) -> -fz.get(id)).thenComparing(id -> id));
        rankInicial.put(k, novo);
    }

    String postoForca(Historia.Titulo ti) {
        Torneio t = null;
        List<Torneio> l = new ArrayList<>(m.torneiosAnteriores);
        l.addAll(m.torneios);
        for (Torneio x : l) if (x.id.equals(ti.competicaoId) && x.ano == ti.ano) t = x;
        if (t == null || ti.campeaoId == null || "kings".equals(ti.ambito)) return "";
        final Torneio tt = t;
        List<String> p = rankInicial.get(t.id + "#" + t.ano);
        if (p == null) {
            p = new ArrayList<>(t.participantes);
            p.sort(Comparator.comparingDouble((String id) -> -forcaDe(tt, id)));
        }
        int k = p.indexOf(ti.campeaoId) + 1;
        if (k <= 0) return "";
        int[] a = postoCampeao.computeIfAbsent(base(ti.competicaoId), x -> new int[4]);
        a[0] += k;
        a[1]++;
        if (k == 1) a[2]++;
        if (k > 8) a[3]++;
        return "(#" + k + "/" + p.size() + ")";
    }

    static final Set<String> CHAVE = Set.of("bra-1", "bra-2", "bra-copa", "conmebol-libertadores", "conmebol-sul-americana", "conmebol-condor", "uefa-champions", "uefa-europa",
            "uefa-conference", "eng-1", "esp-1", "ita-1", "deu-1", "fra-1", "prt-1", "arg-1", "fifa-mundial-clubes", "fifa-intercontinental", "taca-atlantica",
            "desafio-sulamericana-europa", "intercontinental");

    List<Clube> clubes(Torneio t) {
        List<Clube> l = new ArrayList<>();
        for (String id : t.participantes) {
            Clube c = m.clube(id);
            if (c != null && !c.virtual) l.add(c);
        }
        return l;
    }

    // ================================================================== resumo final
    void resumo() {
        StringBuilder sb = new StringBuilder("\n================ RESUMO ================\n");
        sb.append("== Partidas por competição: jogos | gols/j | M/E/V % | zebra % (dos desiguais) | goleadas≥5 % | cartões/j amarelo vermelho (detalhados)\n");
        for (Map.Entry<String, long[]> x : porComp.entrySet()) {
            long[] a = x.getValue();
            if (a[0] < 200 && !x.getKey().contains("copa") && !x.getKey().startsWith("fifa") && !x.getKey().startsWith("afc") && !x.getKey().startsWith("caf")
                    && !x.getKey().startsWith("ofc") && !x.getKey().startsWith("conmebol") && !x.getKey().startsWith("uefa")) continue;
            if (x.getKey().matches("^[a-z]{3}-[3-9].*") && !x.getKey().startsWith("bra")) continue;
            sb.append(String.format(Locale.ROOT, "  %-34s %7d | %.2f | %.1f/%.1f/%.1f | %.1f (%.1f) | %.1f | %.2f %.3f%n", x.getKey(), a[0], a[1] / (double) a[0], 100.0 * a[2] / a[0],
                    100.0 * a[3] / a[0], 100.0 * a[4] / a[0], 100.0 * a[5] / a[0], 100.0 * a[5] / Math.max(1, a[6]), 100.0 * a[10] / a[0], a[7] / (double) Math.max(1, a[9]),
                    a[8] / (double) Math.max(1, a[9])));
        }
        sb.append("\n== Participantes por competição (ano → n; só os valores distintos)\n");
        for (Map.Entry<String, TreeMap<Integer, Integer>> x : participantes.entrySet()) {
            Map<Integer, List<Integer>> inv = new TreeMap<>();
            for (Map.Entry<Integer, Integer> y : x.getValue().entrySet()) inv.computeIfAbsent(y.getValue(), k -> new ArrayList<>()).add(y.getKey());
            sb.append("  ").append(x.getKey()).append(": ");
            for (Map.Entry<Integer, List<Integer>> y : inv.entrySet()) sb.append(y.getKey()).append(" (").append(y.getValue().size()).append("x, ").append(y.getValue().get(0)).append(") ");
            sb.append('\n');
        }
        sb.append(String.format("%n== Acesso/queda: %d checagens, %d falhas%n", checagensAcesso, falhasAcesso));
        for (String s : falhasAcessoEx) sb.append("  ").append(s).append('\n');
        sb.append("== Série D: ").append(serieD).append('\n');
        // dinastias
        sb.append("\n== Campeões (distintos | mais títulos | maior sequência)\n");
        Map<String, List<Historia.Titulo>> porComp2 = new TreeMap<>();
        for (Historia.Titulo t : m.historia.titulos) porComp2.computeIfAbsent(base(t.competicaoId), k -> new ArrayList<>()).add(t);
        for (Map.Entry<String, List<Historia.Titulo>> x : porComp2.entrySet()) {
            String b = x.getKey();
            if (!CHAVE.contains(b) && !("selecoes".equals(x.getValue().get(0).ambito) && !b.startsWith("eliminatorias") && !b.contains("nations-league-"))
                    && !b.startsWith("kings-ios") && !b.startsWith("kings-mundial") && !b.startsWith("kings-brasil")) continue;
            Map<String, Integer> cont = new TreeMap<>();
            int seq = 0, maxSeq = 0;
            String ant = null, quemSeq = null;
            StringBuilder lista = new StringBuilder();
            for (Historia.Titulo t : x.getValue()) {
                cont.merge(t.campeao, 1, Integer::sum);
                seq = t.campeao.equals(ant) ? seq + 1 : 1;
                if (seq > maxSeq) {
                    maxSeq = seq;
                    quemSeq = t.campeao;
                }
                ant = t.campeao;
                lista.append(t.ano % 100).append(':').append(t.campeao.length() > 14 ? t.campeao.substring(0, 14) : t.campeao).append(' ');
            }
            List<Map.Entry<String, Integer>> l = new ArrayList<>(cont.entrySet());
            l.sort((a, c) -> c.getValue() - a.getValue());
            sb.append(String.format("  %-28s %2d edições, %2d campeões | %s | seq %d (%s)%n      %s%n", b, x.getValue().size(), cont.size(),
                    l.subList(0, Math.min(4, l.size())), maxSeq, quemSeq, lista));
        }
        // lesões
        sb.append(String.format(Locale.ROOT, "%n== Lesões: %d (%.1f semanas em média; ≥10 semanas %.1f%%) | histograma semanas: ", lesoes, lesoesSemanas / (double) Math.max(1, lesoes),
                100.0 * lesoesLongas / Math.max(1, lesoes)));
        for (int i = 1; i < 30; i++) if (lesaoHist[i] > 0) sb.append(i).append(':').append(lesaoHist[i]).append(' ');
        sb.append('\n');
        sb.append("== Transferências por semana do ano: ");
        for (int i = 1; i <= 52; i++) if (transfSemana[i] > 0) sb.append(i).append(':').append(transfSemana[i]).append(' ');
        sb.append("\n== Exemplos fora da janela (com clube de origem):\n");
        for (String s : foraDaJanela) sb.append("  ").append(s).append('\n');
        // pico
        sb.append("== Top 1000 do último ano por idade (n, média): ");
        for (Map.Entry<Integer, List<Double>> x : ultimoPorIdade.entrySet())
            sb.append(x.getKey()).append(':').append(x.getValue().size()).append('/').append(String.format(Locale.ROOT, "%.1f", x.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0))).append(' ');
        sb.append('\n');
        // idade de pico dos aposentados com auge >= 75 (carreira temporada a temporada)
        Map<Integer, Integer> pico = new TreeMap<>();
        int npico = 0;
        for (Jogador j : m.jogadores) {
            if (j.status != StatusJogador.APOSENTADO || j.carreira.isEmpty()) continue;
            int melhor = 0, anoMelhor = 0;
            for (Passagem p : j.carreira) if (p.forca > melhor && p.ano >= 2026) {
                melhor = p.forca;
                anoMelhor = p.ano;
            }
            if (melhor >= 75 && anoMelhor > 0) {
                pico.merge(anoMelhor - j.anoNasc, 1, Integer::sum);
                npico++;
            }
        }
        sb.append("== Idade do auge (aposentados vivos na memória, auge ≥ 75 desde 2026, n=").append(npico).append("): ").append(pico).append('\n');
        // notícias
        sb.append("\n== Notícias por ano: tipo | foco [seu clube, suas comps, país, mundo] | manchetes repetidas no ano\n");
        for (Map.Entry<Integer, Map<String, Integer>> x : noticiasTipo.entrySet()) {
            int[] f = noticiasFoco.get(x.getKey());
            sb.append("  ").append(x.getKey()).append(": ").append(x.getValue()).append(" | ").append(Arrays.toString(f)).append(" | dup ")
                    .append(noticiasDup.getOrDefault(x.getKey(), 0)).append('\n');
        }
        sb.append("  máx. notícias numa semana: ").append(maxNoticiasSemana).append('\n');
        List<Map.Entry<String, Integer>> rep = new ArrayList<>(manchetesRepetidas.entrySet());
        rep.sort((a, b) -> b.getValue() - a.getValue());
        sb.append("  manchetes mais repetidas: ");
        for (int i = 0; i < Math.min(12, rep.size()); i++) sb.append('"').append(rep.get(i).getKey()).append("\" ×").append(rep.get(i).getValue() + 1).append("; ");
        sb.append('\n');
        // kings
        sb.append("\n== Kings: ").append(String.join(" ", KINGS_COL)).append('\n');
        for (Map.Entry<String, long[]> x : kings.entrySet()) {
            long[] k = x.getValue();
            double j = Math.max(1, k[0]);
            sb.append(String.format(Locale.ROOT, "  %-12s jogos %d | gols/j %.2f | shootouts %.1f%% | match ball %.1f%% (dur. média %.2f min, máx %d; viradas %.1f%%) | cartas/j %.2f | pênalti pres./j %.2f (conv %.0f%%) | faltas 1v1/j %.2f | amar/j %.2f verm/j %.3f | gols presidente-jogador %.1f%% dos gols com autor%n",
                    x.getKey(), k[0], k[1] / j, 100 * k[2] / j, 100 * k[3] / j, k[4] / (double) Math.max(1, k[3]), k[14], 100.0 * k[5] / Math.max(1, k[3]), k[6] / j, k[7] / j,
                    100.0 * k[8] / Math.max(1, k[7]), k[9] / j, k[10] / j, k[11] / j, 100.0 * k[12] / Math.max(1, k[13])));
        }
        sb.append("  dado: ").append(dadoFaces).append(" | cartas usadas: ").append(cartasUsadas).append('\n');
        sb.append(String.format("  tabelas Kings conferidas: %d; linhas com EMPATE (E>0): %d%n", kingsTabelas, kingsEmpatesTotal));
        for (String s : kingsEmpates) sb.append("    ").append(s).append('\n');
        // artilharias IOS: quantas vezes um presidente foi artilheiro
        int comps = 0, presTop = 0, presTop3 = 0;
        for (Map.Entry<String, Map<String, Integer>> x : artilhariaKings.entrySet()) {
            if (!x.getKey().startsWith("kings-ios")) continue;
            comps++;
            List<Map.Entry<String, Integer>> l = new ArrayList<>(x.getValue().entrySet());
            l.sort((a, b) -> b.getValue() - a.getValue());
            if (!l.isEmpty() && l.get(0).getKey().contains("[PRES]")) presTop++;
            for (int i = 0; i < Math.min(3, l.size()); i++) if (l.get(i).getKey().contains("[PRES]")) presTop3++;
        }
        sb.append(String.format("  IOS: %d competições; artilheiro = presidente em %d; presidentes no top 3: %d de %d vagas%n", comps, presTop, presTop3, 3 * comps));
        sb.append("\n== Posto de força do campeão entre os participantes (média | quantas vezes o mais forte | fora do top 8)\n");
        for (Map.Entry<String, int[]> x : postoCampeao.entrySet())
            sb.append(String.format(Locale.ROOT, "  %-28s %.1f | %d de %d | %d%n", x.getKey(), x.getValue()[0] / (double) x.getValue()[1], x.getValue()[2], x.getValue()[1], x.getValue()[3]));
        sb.append("  técnico humano: demissões ").append(m.tecnico == null ? 0 : m.tecnico.demissoes).append(", contratações aceitas ").append(contratacoesHumano).append('\n');
        if (!excecoes.isEmpty()) {
            sb.append("\n== EXCEÇÕES\n");
            for (Map.Entry<String, String> x : excecoes.entrySet()) sb.append(x.getKey()).append('\n').append(x.getValue()).append('\n');
        } else sb.append("\n== Nenhuma exceção.\n");
        out.print(sb);
        out.flush();
    }

    // ================================================================== main
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) a.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        long semente = Long.parseLong(a.getOrDefault("semente", "1000"));
        int fim = Integer.parseInt(a.getOrDefault("fim", "2045"));
        String clube = a.getOrDefault("clube", "internacional");
        boolean custom = "1".equals(a.getOrDefault("custom", "0"));
        Path raiz = LongRun.acharRaiz();
        Path saida = Paths.get(a.getOrDefault("saida", raiz.resolve("tools").resolve("balanco-" + semente + ".txt").toString()));
        Files.createDirectories(saida.toAbsolutePath().getParent());
        PrintStream out = new PrintStream(Files.newOutputStream(saida), true, StandardCharsets.UTF_8);
        BancoMundo db = BancoMundo.carregar(raiz.resolve("dados").resolve("mundo.sqlite"));
        KingsCustom.Definicao def = custom ? KingsCustom.carregar(KingsCustom.localizar(raiz)) : null;
        long t0 = System.nanoTime();
        Jogo jogo = custom ? Jogo.novoJogo(db, semente, clube, "Balanço", def) : Jogo.novoJogo(db, semente, clube, "Balanço"); // carreira exclusiva: o balanço segue um clube
        Balanco b = new Balanco(jogo, out);
        out.printf(Locale.ROOT, "FUTÉBAS Balanço | semente %d | %s | custom %s | criado em %.1f s | %d clubes, %d jogadores%n", semente, clube, custom, (System.nanoTime() - t0) / 1e9,
                b.m.clubes.size(), b.m.jogadores.size());
        Sonda.Atual.s = b;
        if (b.m.tecnico != null) b.m.tecnico.renovacaoAutomatica = true; // técnico "razoável": renova o elenco
        while (b.m.ano <= fim) {
            int ano = b.m.ano;
            long ti = System.nanoTime();
            while (b.m.ano == ano) {
                try {
                    jogo.avancarSemana();
                } catch (Throwable ex) {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    ex.printStackTrace(new java.io.PrintWriter(sw));
                    String k = ex.getClass().getName() + ": " + ex.getMessage();
                    b.excecoes.merge(ano + "/s" + b.m.semana + " " + k, sw.toString().lines().limit(18).reduce("", (x, y) -> x + "    " + y + "\n"), (x, y) -> x);
                    out.println("!! EXCEÇÃO " + ano + "/s" + b.m.semana + ": " + k);
                    if (b.excecoes.size() > 30) throw ex;
                    // pula a semana para não travar
                    b.m.semana++;
                    if (b.m.semana > 52) {
                        b.m.semana = 1;
                        b.m.ano++;
                    }
                }
                b.semana();
                // humano desempregado aceita o primeiro convite de clube
                if (b.m.tecnico != null && b.m.tecnico.clubeId == null) {
                    PropostaTecnico melhor = null;
                    double nota = -1;
                    for (PropostaTecnico p : jogo.propostasDeEmprego()) {
                        Clube c = p.clubeId == null ? null : b.m.clube(p.clubeId);
                        if (c == null) continue;
                        double x = c.reputacao + ("BRA".equals(c.pais) ? 40 : 0);
                        if (x > nota) {
                            nota = x;
                            melhor = p;
                        }
                    }
                    if (melhor != null && jogo.aceitarEmprego(melhor.id)) b.contratacoesHumano++;
                }
                if (b.m.tecnico != null && b.m.tecnico.clubeId != null) jogo.escalacaoAutomatica();
            }
            b.fimDoAno(ano, (System.nanoTime() - ti) / 1e9);
        }
        b.resumo();
        out.printf(Locale.ROOT, "Total: %.1f min%n", (System.nanoTime() - t0) / 60e9);
        out.close();
    }
}
