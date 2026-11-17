package br.futebas.competicao;

import br.futebas.Balanceamento;
import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.dados.Formato;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Pais;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Monta as competições de clubes de UMA temporada de um calendário ('ano' = jan–dez ou
 * 'europeu' = ago–mai), em todos os países desse calendário e nas copas continentais dele.
 * <ul>
 *   <li>Divisões nacionais (por país): na temporada inicial, os participantes reais
 *       ({@code ligas_clubes}); depois, acesso e rebaixamento simulados.</li>
 *   <li>Estaduais: clubes do estado, pela participação real no início e depois por rebaixamento/força.</li>
 *   <li>Copa nacional e continentais: participantes reais no início; depois as vagas dos
 *       formatos (posições na liga, campeão da copa, campeão continental) e o ranking.</li>
 *   <li>Datas reais da tabela {@code calendarios} (repetidas ano a ano); sem elas, um calendário
 *       padrão. Datas FIFA ficam livres; as ligas jan–dez param no torneio de seleções de meio de ano.</li>
 * </ul>
 */
public final class MontadorTemporada {
    private final Mundo m;
    private final BancoMundo db;
    private final String calendario;
    private final int anoInicio;
    private final boolean inicial;
    private final Map<String, Formato> formatos = new HashMap<>();

    /**
     * @param anoInicio ano em que a temporada começa (2026 para '2026'; 2025 para '2025/26')
     * @param inicial   primeira temporada do mundo: participantes reais do banco
     */
    public MontadorTemporada(Mundo m, BancoMundo db, String calendario, int anoInicio, boolean inicial) {
        this.m = m;
        this.db = db;
        this.calendario = calendario;
        this.anoInicio = anoInicio;
        this.inicial = inicial;
    }

    public static String rotulo(String calendario, int anoInicio) {
        return "europeu".equals(calendario) ? anoInicio + "/" + String.format("%02d", (anoInicio + 1) % 100) : String.valueOf(anoInicio);
    }

    /** Calendário de uma competição de clubes: o da própria competição, o do país ou o da confederação. */
    public static String calendarioDe(Db.Competicao c, BancoMundo db) {
        if (c.calendario() != null && !c.calendario().isBlank() && !"evento".equals(c.calendario())) return c.calendario();
        if (c.pais() != null) {
            Db.Pais p = db.pais(c.pais());
            if (p != null) return p.europeu() ? "europeu" : "ano";
        }
        return "UEFA".equalsIgnoreCase(c.confederacao()) ? "europeu" : "ano";
    }

    /** 0 = principal (Champions, Libertadores), 1 = secundária, 2 = terciária. */
    public static int nivelContinental(Db.Competicao c) {
        String s = (c.id() + " " + c.nome()).toLowerCase(Locale.ROOT);
        if (s.contains("conference") || s.contains("condor") || s.contains("cóndor")) return 2;
        if (s.contains("taca-atlantica") || s.contains("desafio")) return 1;
        if (s.contains("europa") || s.contains("sul-americana") || s.contains("sudamericana") || s.contains("sulamericana")
                || s.contains("cup") || s.contains("recopa")) return 1;
        return 0;
    }

    public List<Torneio> montar() {
        List<Db.Competicao> comps = new ArrayList<>();
        for (Db.Competicao c : db.competicoes.values()) {
            String amb = c.ambito() == null ? "" : c.ambito();
            if (amb.equals("kings") || amb.equals("selecoes")) continue;
            if (!calendario.equals(m.calendarioUnico ? "ano" : calendarioDe(c, db))) continue;
            if (foraDaMontagem(c)) continue;
            if ("estadual".equals(amb) && nivel(c) >= 2) {
                // divisões de acesso dos estaduais: só nos países em detalhe
                Pais p = m.pais(c.pais() == null ? "BRA" : c.pais());
                if (p != null && !p.detalhado) continue;
            }
            Formato f = formatoDe(c);
            if (f == null) continue;
            formatos.put(c.id(), f);
            comps.add(c);
        }
        comps.sort(Comparator.comparingInt(MontadorTemporada::ordem).thenComparing(c -> c.pais() == null ? "" : c.pais())
                .thenComparing(c -> c.divisao() == null ? 0 : c.divisao()).thenComparing(Db.Competicao::id));
        definirDivisoes(comps);
        List<Torneio> lista = new ArrayList<>();
        Set<String> jaContinental = new HashSet<>();
        for (Db.Competicao c : comps) {
            Formato fmt = formatos.get(c.id());
            String amb = c.ambito() == null ? "nacional" : c.ambito();
            List<String> part = switch (amb) {
                case "estadual" -> estadual(c, fmt);
                case "continental" -> CompeticoesFicticias.CONDOR.equals(c.id()) ? CompeticoesFicticias.participantesCondor(m, this, jaContinental)
                        : INTERCONTINENTAL_FIFA.equals(c.id()) ? campeoesContinentais()
                        : MUNDIAL_CLUBES.equals(c.id()) ? participantesMundial(tamanho(c, fmt))
                        : Conmebol.LIBERTADORES.equals(c.id()) ? Conmebol.participantesLibertadores(m, this, db, inicial, jaContinental)
                        : Conmebol.SUL_AMERICANA.equals(c.id()) ? Conmebol.participantesSulAmericana(m, this, db, inicial, jaContinental,
                                m.semente * 7_919L + anoInicio * 31L + 17)
                        : continental(c, fmt, jaContinental);
                case "copa_nacional" -> copa(c, fmt);
                default -> c.divisao() != null ? divisao(c.pais(), c.divisao()) : copa(c, fmt);
            };
            if (part.size() < 2) {
                m.aviso("Competição " + c.id() + " em " + rotulo(calendario, anoInicio) + " sem participantes suficientes; não disputada.");
                continue;
            }
            Torneio t = novoTorneio(c, fmt, part);
            lista.add(t);
        }
        return lista;
    }

    private Torneio novoTorneio(Db.Competicao c, Formato fmt, List<String> part) {
        Torneio t = new Torneio();
        t.id = c.id();
        t.nome = c.nome();
        t.ambito = c.ambito() == null ? "nacional" : c.ambito();
        t.pais = c.pais();
        t.uf = c.uf();
        t.confederacao = c.confederacao();
        t.divisao = "nacional".equals(t.ambito) ? c.divisao() : "estadual".equals(t.ambito) ? Integer.valueOf(nivel(c)) : null;
        t.calendario = calendario;
        t.temporada = rotulo(calendario, anoInicio);
        t.ano = "europeu".equals(calendario) ? anoInicio + 1 : anoInicio;
        t.formato = fmt;
        t.regras = RegrasEpoca.resolver(db.regras, anoInicio, c.pais() == null ? "mundo" : c.pais(), c.id());
        t.semente = m.semente * 1_000_003L + anoInicio * 7919L + c.id().hashCode();
        t.participantes = part;
        if (fmt != null && fmt.estreantesDepois() > 0 && !Conmebol.trata(c.id())) {
            // entradas escalonadas: mais fortes primeiro (divisão, depois reputação); eles estreiam por último
            List<String> ord = new ArrayList<>(part);
            ord.sort(Comparator.comparingInt((String id) -> {
                Clube x = m.clube(id);
                return x == null || x.divisao <= 0 ? 99 : x.divisao;
            }).thenComparingDouble(id -> m.clube(id) == null ? 0 : -m.clube(id).reputacao).thenComparing(id -> id));
            t.participantes = ord;
        }
        int[] jan = janela(c);
        t.semanaInicio = jan[0];
        t.semanaFim = jan[1];
        t.semanasJogo = semanasLivres(jan[0], jan[1], t.ambito);
        t.planejar();
        if (Conmebol.trata(c.id())) {
            Conmebol.calendario(t, anoInicio);
            t.finalNeutra = true;
        }
        for (String a : t.avisos) m.aviso(a);
        m.historia.estat(t.ano).avisosFormato += t.avisos.size();
        return t;
    }

    private Formato formatoDe(Db.Competicao c) {
        Db.FormatoLinha fl = db.formato(c.id(), anoInicio);
        if (MUNDIAL_CLUBES.equals(c.id()) && (fl == null || fl.formato().participantes == null)) {
            // edições futuras sem formato definido no banco (2029: "a definir"): repete o de 2025 (32 clubes)
            Db.FormatoLinha f25 = db.formato(c.id(), 2025);
            if (f25 != null && f25.formato().participantes != null) fl = f25;
        }
        if (Conmebol.trata(c.id())) return Conmebol.ajustar(c.id(), fl == null ? null : fl.formato()); // formato real 2026 (motor)
        if (fl != null) return fl.formato();
        int n = db.participantesIniciais(c.id()).size();
        Torneio ant = m.torneioAnterior(c.id());
        if (ant != null) n = ant.participantes.size();
        if (n < 2) {
            m.aviso("Sem formato e sem participantes no banco para " + c.id() + ": competição não disputada.");
            return null;
        }
        m.aviso("Sem formato pesquisado para " + c.id() + ": usando o formato de reserva do motor.");
        return FormatoPadrao.para(c, n);
    }

    private static int ordem(Db.Competicao c) {
        String amb = c.ambito() == null ? "nacional" : c.ambito();
        return switch (amb) {
            case "nacional" -> c.divisao() != null ? c.divisao() : 9;
            case "estadual" -> 10;
            case "copa_nacional" -> 11;
            case "continental" -> 20 + nivelContinental(c);
            default -> 30;
        };
    }

    // ===================================================================== calendário
    private int[] janela(Db.Competicao c) {
        Db.CalendarioLinha cal = db.calendario(c.id(), anoInicio);
        LocalDate de = cal == null ? null : Db.data(cal.de()), ate = cal == null ? null : Db.data(cal.ate());
        if (m.calendarioUnico && de != null && ate != null && de.getYear() != ate.getYear()) {
            // calendário único: a competição real é ago–mai; no FUTÉBAS ela cabe em jan–dez
            LocalDate[] p = janelaUnica(c);
            return new int[]{Calendario.absoluta(p[0]), Calendario.absoluta(p[1])};
        }
        if (de != null && ate != null && !ate.isBefore(de)) {
            int shift = anoInicio - de.getYear();
            if ("europeu".equals(calendario) && de.getMonthValue() < 7) shift--; // temporada começou no ano anterior
            de = de.plusYears(shift);
            ate = ate.plusYears(shift);
            return new int[]{Calendario.absoluta(de), Calendario.absoluta(ate)};
        }
        LocalDate[] p = janelaPadrao(c);
        return new int[]{Calendario.absoluta(p[0]), Calendario.absoluta(p[1])};
    }

    /**
     * Janela no calendário único das competições que na vida real são ago–mai (ligas e copas
     * europeias, Champions/Europa/Conference): ligas jan–dez (como no Brasfoot), copas fev–nov,
     * continentais com a fase de liga no 1º semestre e as finais em novembro.
     */
    private LocalDate[] janelaUnica(Db.Competicao c) {
        int a = anoInicio;
        String amb = c.ambito() == null ? "nacional" : c.ambito();
        return switch (amb) {
            case "copa_nacional" -> new LocalDate[]{LocalDate.of(a, 2, 11), LocalDate.of(a, 11, 15)};
            case "continental" -> switch (nivelContinental(c)) {
                case 0 -> new LocalDate[]{LocalDate.of(a, 2, 4), LocalDate.of(a, 11, 28)};
                case 1 -> new LocalDate[]{LocalDate.of(a, 2, 11), LocalDate.of(a, 11, 21)};
                default -> new LocalDate[]{LocalDate.of(a, 2, 18), LocalDate.of(a, 11, 14)};
            };
            default -> c.divisao() != null && c.divisao() == 1 ? new LocalDate[]{LocalDate.of(a, 1, 17), LocalDate.of(a, 12, 6)}
                    : new LocalDate[]{LocalDate.of(a, 1, 24), LocalDate.of(a, 11, 29)};
        };
    }

    private LocalDate[] janelaPadrao(Db.Competicao c) {
        int a = anoInicio;
        String amb = c.ambito() == null ? "nacional" : c.ambito();
        if ("europeu".equals(calendario)) {
            return switch (amb) {
                case "copa_nacional" -> new LocalDate[]{LocalDate.of(a, 9, 1), LocalDate.of(a + 1, 5, 20)};
                case "continental" -> switch (nivelContinental(c)) {
                    case 0 -> new LocalDate[]{LocalDate.of(a, 9, 16), LocalDate.of(a + 1, 5, 30)};
                    case 1 -> new LocalDate[]{LocalDate.of(a, 9, 25), LocalDate.of(a + 1, 5, 20)};
                    default -> new LocalDate[]{LocalDate.of(a, 10, 2), LocalDate.of(a + 1, 5, 27)};
                };
                default -> new LocalDate[]{LocalDate.of(a, 8, 15), LocalDate.of(a + 1, 5, 24)};
            };
        }
        return switch (amb) {
            case "estadual" -> new LocalDate[]{LocalDate.of(a, 1, 14), LocalDate.of(a, 4, 5)};
            case "copa_nacional" -> new LocalDate[]{LocalDate.of(a, 2, 18), LocalDate.of(a, 11, 15)};
            case "continental" -> nivelContinental(c) == 0 ? new LocalDate[]{LocalDate.of(a, 2, 4), LocalDate.of(a, 11, 28)}
                    : new LocalDate[]{LocalDate.of(a, 3, 4), LocalDate.of(a, 11, 21)};
            default -> c.divisao() != null && c.divisao() == 1 ? new LocalDate[]{LocalDate.of(a, 4, 5), LocalDate.of(a, 12, 6)}
                    : new LocalDate[]{LocalDate.of(a, 4, 12), LocalDate.of(a, 11, 22)};
        };
    }

    /** Semanas da janela sem data FIFA (e sem o torneio de seleções de meio de ano, nas ligas jan–dez). */
    List<Integer> semanasLivres(int de, int ate, String ambito) {
        List<Integer> l = new ArrayList<>();
        for (int s = de; s <= ate; s++) {
            int sem = Calendario.semana(s), ano = Calendario.ano(s);
            boolean bloq = false;
            for (int f : Balanceamento.JANELAS_FIFA) if (sem == f) bloq = true;
            if (!"estadual".equals(ambito) && "ano".equals(calendario) && ano % 2 == 0
                    && sem >= Balanceamento.SEMANA_TORNEIO_SELECOES_DE && sem <= Balanceamento.SEMANA_TORNEIO_SELECOES_ATE) bloq = true;
            if (!bloq) l.add(s);
        }
        if (l.isEmpty()) for (int s = de; s <= ate; s++) l.add(s);
        return l;
    }

    // ===================================================================== divisões
    private int tamanho(Db.Competicao c, Formato f) {
        if (f != null && f.fases != null && !f.fases.isEmpty()) {
            Formato.Fase f0 = f.fases.get(0);
            if ("especial".equals(f0.tipo) && f0.fallback != null && f0.fallback.participantes != null && f0.fallback.participantes > 1)
                return f0.fallback.participantes;
        }
        if (f != null && f.participantes != null && f.participantes > 1) return f.participantes;
        List<String> real = db.participantesIniciais(c.id());
        if (!real.isEmpty()) return real.size();
        Torneio ant = m.torneioAnterior(c.id());
        if (ant != null) return ant.participantes.size();
        return 20;
    }

    private boolean ativo(String id) {
        Clube c = m.clube(id);
        return c != null && c.existe;
    }

    /** Ranking nacional de um país: divisão, colocação na divisão, reputação. */
    public List<Clube> rankingNacional(String pais) {
        List<Clube> l = new ArrayList<>();
        for (Clube c : m.clubes.values()) if (c.existe && pais != null && pais.equals(c.pais)) l.add(c);
        l.sort(Comparator.comparingInt((Clube c) -> c.divisao == 0 ? 99 : c.divisao)
                .thenComparingInt(c -> c.posicaoDivisao)
                .thenComparingDouble(c -> -c.reputacao)
                .thenComparing(c -> c.id));
        return l;
    }

    private void definirDivisoes(List<Db.Competicao> comps) {
        Map<String, List<Db.Competicao>> porPais = new LinkedHashMap<>();
        for (Db.Competicao c : comps) {
            if ("nacional".equals(c.ambito()) && c.divisao() != null && c.pais() != null) porPais.computeIfAbsent(c.pais(), k -> new ArrayList<>()).add(c);
        }
        for (Map.Entry<String, List<Db.Competicao>> e : porPais.entrySet()) definirDivisoes(e.getKey(), e.getValue());
    }

    /**
     * Quantos sobem da divisão: o {@code promovidos} do formato; sem ele (muitos formatos do banco só
     * têm {@code rebaixados}), os rebaixados da divisão de cima — senão a liga ficava fechada e os
     * rebaixados voltavam para completar a divisão de cima.
     */
    private int promovidos(List<Db.Competicao> divs, Db.Competicao dc) {
        Torneio t = m.torneioAnterior(dc.id());
        if (t != null && t.formato != null && t.formato.promovidos != null) return t.formato.promovidos;
        for (Db.Competicao x : divs) {
            if (x.divisao() != dc.divisao() - 1) continue;
            Torneio a = m.torneioAnterior(x.id());
            if (a != null && a.formato != null && a.formato.rebaixados != null) return a.formato.rebaixados;
        }
        return 0;
    }

    /** Quantos caem: o {@code rebaixados} do formato; sem ele, os promovidos da divisão de baixo. */
    private int rebaixados(List<Db.Competicao> divs, Db.Competicao dc) {
        Torneio t = m.torneioAnterior(dc.id());
        if (t != null && t.formato != null && t.formato.rebaixados != null) return t.formato.rebaixados;
        for (Db.Competicao x : divs) {
            if (x.divisao() != dc.divisao() + 1) continue;
            Torneio a = m.torneioAnterior(x.id());
            if (a != null && a.formato != null && a.formato.promovidos != null) return a.formato.promovidos;
        }
        return 0;
    }

    private void definirDivisoes(String pais, List<Db.Competicao> divs) {
        divs.sort(Comparator.comparingInt(Db.Competicao::divisao));
        boolean temAnterior = false;
        for (Db.Competicao c : divs) if (m.torneioAnterior(c.id()) != null) temAnterior = true;
        boolean reais = inicial || !temAnterior;

        Map<String, double[]> chave = new HashMap<>(); // {divisao nova, grupo, posicao}
        if (!reais) {
            for (Db.Competicao dc : divs) {
                Torneio t = m.torneioAnterior(dc.id());
                if (t == null) continue;
                int d = dc.divisao();
                boolean temAbaixo = false;
                for (Db.Competicao x : divs) if (x.divisao() == d + 1) temAbaixo = true;
                int prom = promovidos(divs, dc), reb = rebaixados(divs, dc);
                List<String> cl = t.classificacao;
                Integer ficam = FICAM_NA_ULTIMA.get(t.id);
                if (!temAbaixo && reb == 0 && ficam != null) reb = Math.max(0, cl.size() - ficam); // Série D: só os 32 melhores ficam
                for (int i = 0; i < cl.size(); i++) {
                    String id = cl.get(i);
                    int nova = d;
                    int grupo = 1;
                    if (d > 1 && i < prom) {
                        nova = d - 1;
                        grupo = 2;
                    } else if (i >= cl.size() - reb) {
                        nova = temAbaixo ? d + 1 : 98; // última divisão: sai da liga
                        grupo = 0;
                    }
                    Clube c = m.clube(id);
                    if (c != null && c.rebaixamentoForcado) {
                        nova = 99;
                        c.rebaixamentoForcado = false;
                    }
                    chave.put(id, new double[]{nova, grupo, i});
                }
            }
        }
        for (Clube c : m.clubes.values()) if (pais.equals(c.pais)) c.divisao = 0;

        Set<String> usados = new HashSet<>();
        List<String> fila = new ArrayList<>(chave.keySet());
        fila.sort(Comparator.comparingDouble((String id) -> chave.get(id)[0])
                // quem cai de cima e quem sobe de baixo antes de quem fica: divisão maior que o formato (1º ano
                // com todos os clubes reais) volta ao tamanho cortando os piores que ficariam, nunca o promovido
                .thenComparingDouble(id -> chave.get(id)[1] == 1 ? 3 : chave.get(id)[1])
                .thenComparingDouble(id -> chave.get(id)[2])
                .thenComparing(id -> id));
        int ponteiro = 0;
        for (Db.Competicao c : divs) {
            int n = tamanho(c, formatos.get(c.id()));
            List<String> membros = new ArrayList<>();
            if (reais) {
                for (String id : db.participantesIniciais(c.id())) {
                    Clube cl = m.clube(id);
                    // rodada 2 do balanço: no 1º ano entram TODOS os clubes reais da divisão (antes os n primeiros
                    // por id ficavam e o resto sumia: Paraguai 16 × 12, Equador B 16 × 12, Uruguai 2ª 20 × 14);
                    // do 2º ano em diante o tamanho volta ao do formato pelo acesso/queda (fila da classificação)
                    if (cl != null && cl.existe && !usados.contains(id)) membros.add(id);
                }
            } else {
                while (membros.size() < n && ponteiro < fila.size()) {
                    String id = fila.get(ponteiro++);
                    if (ativo(id) && !usados.contains(id) && chave.get(id)[0] < 98) membros.add(id);
                }
            }
            if (membros.size() < n && !(reais && db.participantesIniciais(c.id()).size() > 0 && inicial)) {
                for (String id : candidatosDeFora(pais, c.id())) {
                    if (membros.size() >= n) break;
                    if (!usados.contains(id) && !membros.contains(id)) membros.add(id);
                }
                // sem candidatos de fora: quem saiu da última divisão continua
                for (int i = fila.size() - 1; i >= 0 && membros.size() < n; i--) {
                    String id = fila.get(i);
                    if (ativo(id) && !usados.contains(id) && !membros.contains(id) && chave.get(id)[0] == 98) membros.add(id);
                }
            }
            for (String id : membros) {
                usados.add(id);
                m.clube(id).divisao = c.divisao();
            }
        }
        for (Clube c : m.clubes.values()) {
            if (!pais.equals(c.pais)) continue;
            double[] k = chave.get(c.id);
            c.posicaoDivisao = k == null ? 50 : (int) k[2] + (k[1] == 0 ? -1 : k[1] == 2 ? 50 : 0);
        }
    }

    /**
     * Última divisão que se renova pelos estaduais: quantos ficam (Série D 2026: quem chegou à 3ª fase,
     * 32; os outros voltam a depender do estadual).
     */
    static final Map<String, Integer> FICAM_NA_ULTIMA = Map.of("bra-4", 32);
    /** Vagas estaduais na Série D por posição do estado no ranking (CBF 2026: 1º 4; 2º–9º 3; 10º–27º 2). */
    static final int[] VAGAS_ESTADUAIS_SERIE_D = {4, 3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};

    /**
     * Vagas estaduais (Brasil): os mais bem colocados de cada estadual fora das Séries A–D, com a
     * cota do estado pela força dos clubes dele (aproxima o Ranking Nacional de Federações).
     */
    private List<String> vagasEstaduais(String pais) {
        Map<String, List<String>> porUf = new HashMap<>();
        Map<String, Double> forcaUf = new HashMap<>();
        for (Torneio t : m.torneiosAnteriores) {
            if (!"estadual".equals(t.ambito) || !pais.equals(t.pais) || t.uf == null) continue;
            List<String> l = porUf.computeIfAbsent(t.uf, k -> new ArrayList<>());
            for (String id : t.classificacao) {
                Clube c = m.clube(id);
                if (c != null && c.existe && c.divisao == 0 && !l.contains(id)) l.add(id);
            }
            double f = 0;
            for (String id : t.participantes) if (m.clube(id) != null) f += m.clube(id).reputacao;
            forcaUf.merge(t.uf, f, Math::max);
        }
        List<String> ufs = new ArrayList<>(porUf.keySet());
        ufs.sort(Comparator.comparingDouble((String u) -> -forcaUf.getOrDefault(u, 0.0)).thenComparing(u -> u));
        List<String> l = new ArrayList<>();
        for (int i = 0; i < ufs.size(); i++) {
            int cota = i < VAGAS_ESTADUAIS_SERIE_D.length ? VAGAS_ESTADUAIS_SERIE_D[i] : 1;
            List<String> doUf = porUf.get(ufs.get(i));
            for (int k = 0; k < Math.min(cota, doUf.size()); k++) l.add(doUf.get(k));
        }
        return l;
    }

    /** Clubes do país sem divisão, na ordem de preferência para entrar na última divisão. */
    private List<String> candidatosDeFora(String pais, String compId) {
        LinkedHashSet<String> l = new LinkedHashSet<>();
        for (Torneio t : m.torneiosAnteriores) {
            if (t.formato == null || t.formato.vagas == null) continue;
            for (Formato.Vaga v : t.formato.vagas) {
                if (!compId.equals(v.para) || v.posicoes == null) continue;
                for (int pos : v.posicoes) {
                    if (pos - 1 < t.classificacao.size()) {
                        Clube c = m.clube(t.classificacao.get(pos - 1));
                        if (c != null && c.existe && c.divisao == 0 && pais.equals(c.pais)) l.add(c.id);
                    }
                }
            }
        }
        if (FICAM_NA_ULTIMA.containsKey(compId)) l.addAll(vagasEstaduais(pais));
        List<String[]> est = new ArrayList<>();
        for (Torneio t : m.torneiosAnteriores) {
            if (!"estadual".equals(t.ambito) || !pais.equals(t.pais)) continue;
            for (int i = 0; i < t.classificacao.size(); i++) {
                Clube c = m.clube(t.classificacao.get(i));
                if (c != null && c.existe && c.divisao == 0) est.add(new String[]{c.id, String.valueOf((double) i / Math.max(1, t.classificacao.size()) + i * 0.001)});
            }
        }
        est.sort(Comparator.comparingDouble((String[] a) -> Double.parseDouble(a[1])).thenComparing(a -> a[0]));
        for (String[] a : est) l.add(a[0]);
        List<Clube> resto = new ArrayList<>();
        for (Clube c : m.clubes.values()) if (c.existe && pais.equals(c.pais) && c.divisao == 0 && !c.virtual) resto.add(c);
        resto.sort(Comparator.comparingDouble((Clube c) -> -c.reputacao).thenComparing(c -> c.id));
        for (Clube c : resto) l.add(c.id);
        return new ArrayList<>(l);
    }

    private List<String> divisao(String pais, int d) {
        List<String> l = new ArrayList<>();
        for (Clube c : rankingNacional(pais)) if (c.divisao == d) l.add(c.id);
        return l;
    }

    // ===================================================================== estaduais
    static int nivel(Db.Competicao c) {
        if (c.divisao() != null && c.divisao() >= 1) return c.divisao();
        // banco sem a coluna divisao: o sufixo do id diz o nível (est-sp-2, est-sp-3…)
        java.util.regex.Matcher mt = NIVEL_ID.matcher(c.id());
        return mt.find() ? Math.max(1, Integer.parseInt(mt.group(1))) : 1;
    }

    private static final java.util.regex.Pattern NIVEL_ID = java.util.regex.Pattern.compile("^est-[a-z]{2}-(\\d)$");

    /** Divisões do estadual do mesmo estado (1ª, 2ª, 3ª…), em ordem; só a 1ª se o banco não tem as outras. */
    private List<Db.Competicao> niveisDoEstado(Db.Competicao c) {
        List<Db.Competicao> l = new ArrayList<>();
        for (Db.Competicao x : db.competicoes.values()) {
            if (!"estadual".equals(x.ambito()) || x.uf() == null || !x.uf().equals(c.uf())) continue;
            if (c.pais() != null && x.pais() != null && !c.pais().equals(x.pais())) continue;
            if (!formatos.containsKey(x.id()) && formatoDe(x) == null) continue;
            l.add(x);
        }
        l.sort(Comparator.comparingInt(MontadorTemporada::nivel).thenComparing(Db.Competicao::id));
        return l;
    }

    private final Map<String, Map<String, List<String>>> piramides = new HashMap<>();

    /**
     * Pirâmide do estadual: cada divisão = a do ano passado − rebaixados − promovidos + quem desce
     * da divisão de cima + quem sobe da de baixo ({@code promovidos}/{@code rebaixados} do formato; 2 se faltar).
     * No primeiro ano (ou sem o ano anterior), os participantes do banco.
     */
    private Map<String, List<String>> piramide(List<Db.Competicao> niveis) {
        String chave = niveis.get(0).id();
        Map<String, List<String>> pronta = piramides.get(chave);
        if (pronta != null) return pronta;
        Map<String, List<String>> r = new LinkedHashMap<>();
        boolean temAnterior = !inicial;
        for (Db.Competicao x : niveis) if (m.torneioAnterior(x.id()) == null) temAnterior = false;
        Set<String> usados = new HashSet<>();
        if (!temAnterior) {
            for (Db.Competicao x : niveis) {
                List<String> l = new ArrayList<>();
                for (String id : db.participantesIniciais(x.id())) if (ativo(id) && usados.add(id)) l.add(id);
                r.put(x.id(), l);
            }
        } else {
            int k = niveis.size();
            List<List<String>> ant = new ArrayList<>();
            int[] prom = new int[k], reb = new int[k];
            for (int i = 0; i < k; i++) {
                Torneio t = m.torneioAnterior(niveis.get(i).id());
                ant.add(t.classificacao);
                Formato f = t.formato;
                prom[i] = i == 0 ? 0 : f != null && f.promovidos != null ? f.promovidos : 2;
                reb[i] = i == k - 1 ? 0 : f != null && f.rebaixados != null ? f.rebaixados : 2;
            }
            for (int i = 0; i < k; i++) {
                List<String> cl = ant.get(i), l = new ArrayList<>();
                int n = cl.size();
                for (int p = prom[i]; p < n - reb[i]; p++) l.add(cl.get(p));
                if (i > 0) { // rebaixados da divisão de cima
                    List<String> cima = ant.get(i - 1);
                    for (int p = Math.max(0, cima.size() - reb[i - 1]); p < cima.size(); p++) l.add(cima.get(p));
                }
                if (i < k - 1) { // promovidos da divisão de baixo
                    List<String> baixo = ant.get(i + 1);
                    for (int p = 0; p < Math.min(prom[i + 1], baixo.size()); p++) l.add(baixo.get(p));
                }
                List<String> fin = new ArrayList<>();
                for (String id : l) if (ativo(id) && usados.add(id)) fin.add(id);
                r.put(niveis.get(i).id(), fin);
            }
        }
        piramides.put(chave, r);
        return r;
    }

    private List<String> estadual(Db.Competicao c, Formato f) {
        List<Db.Competicao> niveis = niveisDoEstado(c);
        if (niveis.size() > 1) {
            Map<String, List<String>> pir = piramide(niveis);
            List<String> part = new ArrayList<>(pir.getOrDefault(c.id(), List.of()));
            int n = tamanho(c, f);
            if (part.size() < n && nivel(c) == niveis.size() - 1 + nivel(niveis.get(0))) {
                // última divisão do estado completa com clubes do estado fora da pirâmide
                Set<String> todos = new HashSet<>();
                for (List<String> l : pir.values()) todos.addAll(l);
                for (Clube cl : rankingNacional(c.pais() == null ? "BRA" : c.pais())) {
                    if (part.size() >= n) break;
                    if (c.uf() != null && c.uf().equals(cl.uf) && !todos.contains(cl.id)) part.add(cl.id);
                }
            }
            if (part.size() > n) part = new ArrayList<>(part.subList(0, n));
            return part;
        }
        return estadualUnico(c, f);
    }

    private List<String> estadualUnico(Db.Competicao c, Formato f) {
        String uf = c.uf();
        List<Clube> doEstado = new ArrayList<>();
        for (Clube cl : rankingNacional(c.pais() == null ? "BRA" : c.pais())) if (uf != null && uf.equals(cl.uf)) doEstado.add(cl);
        int n = Math.min(tamanho(c, f), Math.max(doEstado.size(), db.participantesIniciais(c.id()).size()));
        Torneio ant = m.torneioAnterior(c.id());
        List<String> part = new ArrayList<>();
        if (inicial || ant == null) {
            for (String id : db.participantesIniciais(c.id())) if (ativo(id) && part.size() < n) part.add(id);
        } else {
            int reb = ant.formato != null && ant.formato.rebaixados != null ? ant.formato.rebaixados
                    : (doEstado.size() > n ? Math.max(1, n / 8) : 0);
            List<String> cl = ant.classificacao;
            for (int i = 0; i < cl.size() - reb && part.size() < n; i++) if (ativo(cl.get(i))) part.add(cl.get(i));
        }
        for (Clube cl : doEstado) {
            if (part.size() >= n) break;
            if (!part.contains(cl.id)) part.add(cl.id);
        }
        Map<String, Integer> pos = new HashMap<>();
        for (int i = 0; i < doEstado.size(); i++) pos.put(doEstado.get(i).id, i);
        if (ant != null) for (int i = 0; i < ant.classificacao.size(); i++) pos.merge(ant.classificacao.get(i), i, Math::min);
        part.sort(Comparator.comparingInt((String id) -> pos.getOrDefault(id, 999)).thenComparing(id -> id));
        return part;
    }

    // ===================================================================== copa nacional
    /** Copas regionais do Brasil: estados de cada uma. */
    static final Map<String, Set<String>> REGIONAIS = Map.of(
            "bra-copa-nordeste", Set.of("AL", "BA", "CE", "MA", "PB", "PE", "PI", "RN", "SE"),
            "bra-copa-verde", Set.of("AC", "AP", "AM", "PA", "RO", "RR", "TO", "DF", "GO", "MT", "MS", "ES"),
            "bra-copa-sul-sudeste", Set.of("PR", "SC", "RS", "SP", "RJ", "MG"));

    /**
     * Copa regional: clubes dos estados da região, pela colocação nos estaduais do ano anterior (e
     * pelo ranking); fora quem jogou competição continental na temporada passada e, na Sul-Sudeste,
     * os das Séries A e B.
     */
    private List<String> regional(Db.Competicao c, Formato f) {
        Set<String> ufs = REGIONAIS.get(c.id());
        int n = tamanho(c, f);
        Set<String> fora = new HashSet<>();
        for (Torneio t : m.torneiosAnteriores) if ("continental".equals(t.ambito)) fora.addAll(t.participantes);
        boolean soDeBaixo = c.id().equals("bra-copa-sul-sudeste");
        java.util.function.Predicate<Clube> ok = cl -> cl != null && cl.existe && !cl.virtual && cl.uf != null && ufs.contains(cl.uf)
                && !fora.contains(cl.id) && !(soDeBaixo && cl.divisao >= 1 && cl.divisao <= 2);
        List<String[]> est = new ArrayList<>();
        for (Torneio t : m.torneiosAnteriores) {
            if (!"estadual".equals(t.ambito) || t.uf == null || !ufs.contains(t.uf)) continue;
            for (int i = 0; i < t.classificacao.size(); i++) {
                Clube cl = m.clube(t.classificacao.get(i));
                if (ok.test(cl)) est.add(new String[]{cl.id, String.valueOf(i + (double) i / 1000)});
            }
        }
        est.sort(Comparator.comparingDouble((String[] a) -> Double.parseDouble(a[1])).thenComparing(a -> a[0]));
        LinkedHashSet<String> part = new LinkedHashSet<>();
        for (String[] a : est) if (part.size() < n) part.add(a[0]);
        for (Clube cl : rankingNacional(c.pais() == null ? "BRA" : c.pais())) {
            if (part.size() >= n) break;
            if (ok.test(cl)) part.add(cl.id);
        }
        List<String> l = new ArrayList<>(part);
        ordenarPorForca(l);
        return l;
    }

    private List<String> copa(Db.Competicao c, Formato f) {
        if (REGIONAIS.containsKey(c.id())) return regional(c, f);
        int n = tamanho(c, f);
        LinkedHashSet<String> part = new LinkedHashSet<>();
        if (inicial || m.torneioAnterior(c.id()) == null) {
            for (String id : db.participantesIniciais(c.id())) if (ativo(id)) part.add(id);
        }
        if (!inicial) {
            part.addAll(qualificadosPorVaga(c.id(), new HashSet<>()));
            for (Torneio t : m.torneiosAnteriores) {
                if ("estadual".equals(t.ambito) && t.campeao != null && ativo(t.campeao) && c.pais() != null && c.pais().equals(t.pais)) part.add(t.campeao);
            }
        }
        for (Clube cl : rankingNacional(c.pais())) {
            if (part.size() >= n) break;
            part.add(cl.id);
        }
        List<String> l = new ArrayList<>(part);
        if (l.size() > n) l = new ArrayList<>(l.subList(0, n));
        ordenarPorForca(l);
        return l;
    }

    /**
     * Clubes classificados para {@code compId} pelas vagas dos formatos das competições que já
     * terminaram (posição na liga, campeão da copa...). Vaga de quem já está classificado
     * ({@code ja} ou repetido) passa para o próximo colocado do mesmo torneio.
     */
    private List<String> qualificadosPorVaga(String compId, Set<String> ja) {
        List<String> l = new ArrayList<>();
        for (Db.Competicao fonte : db.competicoes.values()) {
            Torneio t = m.ultimoEncerrado(fonte.id());
            if (t == null || t.formato == null || t.formato.vagas == null) continue;
            for (Formato.Vaga v : t.formato.vagas) {
                if (!compId.equals(v.para) || v.posicoes == null) continue;
                for (int pos : v.posicoes) {
                    int idx = pos - 1;
                    while (idx < t.classificacao.size() && (l.contains(t.classificacao.get(idx)) || ja.contains(t.classificacao.get(idx))
                            || !ativo(t.classificacao.get(idx)))) idx++;
                    if (idx < t.classificacao.size()) l.add(t.classificacao.get(idx));
                }
            }
        }
        return l;
    }

    // ===================================================================== continentais
    private List<String> continental(Db.Competicao c, Formato f, Set<String> jaContinental) {
        int n = tamanho(c, f);
        LinkedHashSet<String> part = new LinkedHashSet<>();
        if (inicial || m.ultimoEncerrado(c.id()) == null && db.participantesIniciais(c.id()).size() > 0 && m.torneioAnterior(c.id()) == null) {
            for (String id : db.participantesIniciais(c.id())) if (ativo(id) && !jaContinental.contains(id)) part.add(id);
        } else {
            Torneio ultimo = m.ultimoEncerrado(c.id());
            if (campeaoDefende(c) && ultimo != null && ultimo.campeao != null && ativo(ultimo.campeao) && !jaContinental.contains(ultimo.campeao))
                part.add(ultimo.campeao);
            if (CompeticoesFicticias.SUL_AMERICANA.equals(c.id())) {
                // o campeão da Copa Cóndor (fictícia) ganha vaga na Sul-Americana
                Torneio condor = m.ultimoEncerrado(CompeticoesFicticias.CONDOR);
                if (condor != null && condor.campeao != null && ativo(condor.campeao) && !jaContinental.contains(condor.campeao)) part.add(condor.campeao);
            }
            for (String id : qualificadosPorVaga(c.id(), jaContinental)) part.add(id);
        }
        if (part.size() < n) {
            // completa pelo ranking da confederação (reputação), sem quem já está em outra continental
            String conf = c.confederacao();
            List<Clube> pool = new ArrayList<>();
            for (Clube cl : m.clubes.values()) {
                if (!cl.existe || jaContinental.contains(cl.id) || part.contains(cl.id)) continue;
                if (conf != null && !conf.equalsIgnoreCase(cl.confederacao)) continue;
                if (conf == null && c.pais() != null && !c.pais().equals(cl.pais)) continue;
                pool.add(cl);
            }
            pool.sort(Comparator.comparingDouble((Clube x) -> -x.reputacao).thenComparing(x -> x.id));
            for (Clube cl : pool) {
                if (part.size() >= n) break;
                part.add(cl.id);
            }
        }
        List<String> l = new ArrayList<>(part);
        if (l.size() > n) l = new ArrayList<>(l.subList(0, n));
        jaContinental.addAll(l);
        ordenarPorForca(l);
        return l;
    }

    /** O campeão defende o título (Libertadores, Champions...)? Não nas supercopas, recopas e torneios da FIFA (jogo entre campeões). */
    static boolean campeaoDefende(Db.Competicao c) {
        String id = c.id();
        if (id.contains("supercopa") || id.contains("recopa") || id.contains("intercontinental") || id.contains("mundial")) return false;
        return c.confederacao() != null && !"FIFA".equalsIgnoreCase(c.confederacao());
    }

    public static final String MUNDIAL_CLUBES = "fifa-mundial-clubes", INTERCONTINENTAL_FIFA = "fifa-intercontinental";

    /**
     * Competições que esta montagem não cria: os jogos únicos de agosto (Taça Atlântica, Desafio; são
     * criados por {@link CompeticoesFicticias#jogosDeAgosto} com os dois campeões; antes a montagem
     * os recriava a partir do 2º ano com clubes pelo ranking), a cópia antiga "intercontinental" do
     * banco quando existe a da FIFA (eram duas Intercontinentais por ano) e o Mundial de Clubes fora
     * dos anos dele (quadrienal desde 2025; antes era anual a partir de 2029).
     */
    private boolean foraDaMontagem(Db.Competicao c) {
        String id = c.id();
        if (CompeticoesFicticias.TACA_ATLANTICA.equals(id) || CompeticoesFicticias.DESAFIO.equals(id)) return true;
        if ("intercontinental".equals(id) && db.competicoes.containsKey(INTERCONTINENTAL_FIFA)) return true;
        return MUNDIAL_CLUBES.equals(id) && Math.floorMod(anoInicio - 2025, 4) != 0;
    }

    /** Principal competição continental de clubes de cada confederação (campeões vão à Intercontinental). */
    static final String[][] CONTINENTAIS_PRINCIPAIS = {{"UEFA", "uefa-champions"}, {"CONMEBOL", "conmebol-libertadores"},
            {"CONCACAF", "concacaf-champions"}, {"AFC", "afc-champions-elite"}, {"CAF", "caf-champions"}, {"OFC", null}};

    /**
     * Copa Intercontinental da FIFA: o último campeão da principal continental de cada confederação
     * (OFC sem competição no banco: o clube de maior reputação). Antes o campeão anterior da própria
     * Intercontinental entrava sempre (Barcelona campeão 10 vezes seguidas).
     */
    private List<String> campeoesContinentais() {
        List<String> l = new ArrayList<>();
        for (String[] cc : CONTINENTAIS_PRINCIPAIS) {
            String id = null;
            if (cc[1] != null) {
                Torneio t = m.ultimoEncerrado(cc[1]);
                if (t != null && t.campeao != null && ativo(t.campeao)) id = t.campeao;
            }
            if (id == null) id = melhorDaConfederacao(cc[0], l);
            if (id != null && !l.contains(id)) l.add(id);
        }
        ordenarPorForca(l);
        return l;
    }

    private String melhorDaConfederacao(String conf, List<String> fora) {
        Clube melhor = null;
        for (Clube cl : m.clubes.values()) {
            if (!cl.existe || cl.virtual || !conf.equalsIgnoreCase(cl.confederacao) || fora.contains(cl.id)) continue;
            if (melhor == null || cl.reputacao > melhor.reputacao || (cl.reputacao == melhor.reputacao && cl.id.compareTo(melhor.id) < 0)) melhor = cl;
        }
        return melhor == null ? null : melhor.id;
    }

    /** Vagas do Mundial de Clubes por confederação (FIFA 2025: 12, 6, 4, 4, 4, 1 + país-sede). */
    static final Object[][] VAGAS_MUNDIAL = {{"UEFA", 12}, {"CONMEBOL", 6}, {"AFC", 4}, {"CAF", 4}, {"CONCACAF", 4}, {"OFC", 1}};

    /**
     * Mundial de Clubes (32): por confederação, os campeões da principal continental nos últimos 4 anos
     * e depois os de maior reputação (no máximo 2 por país, fora os campeões); a vaga da sede fica com
     * o melhor clube que sobrou. Antes eram só 4 clubes (campeões da Ásia/África) e times como o
     * US Bitam e o Dhaka United ganhavam o Mundial.
     */
    private List<String> participantesMundial(int n) {
        List<String> l = new ArrayList<>();
        for (Object[] v : VAGAS_MUNDIAL) {
            String conf = (String) v[0];
            int vagas = (Integer) v[1];
            String comp = null;
            for (String[] cc : CONTINENTAIS_PRINCIPAIS) if (cc[0].equals(conf)) comp = cc[1];
            List<String> daConf = new ArrayList<>();
            if (comp != null) {
                for (br.futebas.historia.Historia.Titulo ti : m.historia.titulos) {
                    if (comp.equals(ti.competicaoId) && ti.ano >= anoInicio - 4 && ti.campeaoId != null && ativo(ti.campeaoId)
                            && !daConf.contains(ti.campeaoId) && !l.contains(ti.campeaoId)) daConf.add(ti.campeaoId);
                }
            }
            while (daConf.size() > vagas) daConf.remove(0);
            List<Clube> pool = new ArrayList<>();
            for (Clube cl : m.clubes.values()) if (cl.existe && !cl.virtual && conf.equalsIgnoreCase(cl.confederacao)) pool.add(cl);
            pool.sort(Comparator.comparingDouble((Clube x) -> -x.reputacao).thenComparing(x -> x.id));
            for (Clube cl : pool) {
                if (daConf.size() >= vagas) break;
                if (daConf.contains(cl.id) || l.contains(cl.id)) continue;
                int k = 0;
                for (String id : daConf) if (cl.pais.equals(m.clube(id).pais)) k++;
                if (k >= 2) continue;
                daConf.add(cl.id);
            }
            l.addAll(daConf);
        }
        List<Clube> resto = new ArrayList<>();
        for (Clube cl : m.clubes.values()) if (cl.existe && !cl.virtual && !l.contains(cl.id)) resto.add(cl);
        resto.sort(Comparator.comparingDouble((Clube x) -> -x.reputacao).thenComparing(x -> x.id));
        for (Clube cl : resto) {
            if (l.size() >= n) break;
            l.add(cl.id);
        }
        if (l.size() > n) l = new ArrayList<>(l.subList(0, n));
        ordenarPorForca(l);
        return l;
    }

    private void ordenarPorForca(List<String> l) {
        l.sort(Comparator.comparingDouble((String id) -> -m.clube(id).reputacao).thenComparing(id -> id));
    }

    /** Países do calendário que esta montagem cobre. */
    public static List<Pais> paisesDoCalendario(Mundo m, String calendario) {
        List<Pais> l = new ArrayList<>();
        for (Pais p : m.paises.values()) if (calendario.equals(p.calendario)) l.add(p);
        return l;
    }
}
