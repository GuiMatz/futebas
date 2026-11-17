package br.futebas.mundo;

import br.futebas.Balanceamento;
import br.futebas.competicao.Executor;
import br.futebas.competicao.FormatoPadrao;
import br.futebas.competicao.MontadorTemporada;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.dados.Db;
import br.futebas.dados.Formato;
import br.futebas.historia.Historia;
import br.futebas.partida.ResultadoPartida;
import br.futebas.selecao.Selecoes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sub-20 (requisito): cada competição profissional dos países em detalhe tem a sua versão
 * sub-20 (id {@code <comp>-sub20}), só simulada pelo caminho rápido, com pirâmide própria.
 * <ul>
 *   <li>2026: as divisões e os participantes espelham o profissional (só os clubes dos países em detalhe).</li>
 *   <li>Depois: acesso e rebaixamento pelos resultados do sub-20 (promovidos/rebaixados do formato);
 *       copas e continentais pelas vagas dos formatos aplicadas às tabelas sub-20.</li>
 *   <li>Time: os melhores com até 20 anos no ano (base + profissionais jovens); faltando gente,
 *       completa com juniores anônimos de {@code SUB20_COMPLETA} × nível do clube.</li>
 * </ul>
 * Os jogos do sub-20 não contam nas estatísticas do jogador profissional (só na artilharia do torneio).
 */
public final class Sub20 implements Executor {
    public static final String SUFIXO = "-sub20";

    private final Simulador s;
    private final Mundo m;
    private final Map<String, double[]> cacheForca = new HashMap<>();
    private final Map<String, int[]> cacheIds = new HashMap<>();
    private int semanaCache = -1;

    Sub20(Simulador s) {
        this.s = s;
        this.m = s.m;
    }

    public static boolean ehSub20(String competicaoId) {
        return competicaoId != null && competicaoId.endsWith(SUFIXO);
    }

    public static String idPro(String competicaoId) {
        return ehSub20(competicaoId) ? competicaoId.substring(0, competicaoId.length() - SUFIXO.length()) : competicaoId;
    }

    private boolean detalhado(String pais) {
        Pais p = m.pais(pais);
        return p != null && p.detalhado;
    }

    private boolean apto(Clube c) {
        return c != null && c.existe && !c.virtual && detalhado(c.pais);
    }

    // ======================================================================= base
    /** Na criação do mundo: todo clube dos países em detalhe ganha uma base (o Sub-20 de 23 vem em {@link #garantir}). */
    void garantirBases() {
        for (Clube c : m.clubes.values()) {
            if (!apto(c)) continue;
            double media = Balanceamento.potencialPeneira(Math.max(c.nivel(), c.nivelInicial), c.estrutura);
            int guarda = 0;
            while (c.base.size() < Balanceamento.SUB20_BASE_MINIMA && guarda++ < 30) {
                Jogador j = s.populador.newgen(c.pais, c, media);
                if (j == null) break;
                int idade = m.rng.entre(16, 19);
                j.anoNasc = m.ano - idade;
                Evolucao.criarAtributos(j, Evolucao.forcaInicial(j.potencial, idade, m.rng), m.rng);
            }
        }
    }

    // ======================================================================= time de 23
    /** Jogadores gerados para completar Sub-20 nesta sessão (medida). */
    public int gerados;

    /** Salário do jogador do Sub-20 (requisito: R$ 500 por mês, corrigido pela época). */
    public static long salario(int ano) {
        return Math.round(Balanceamento.SUB20_SALARIO * br.futebas.financas.Financas.fatorEpoca(ano));
    }

    /** Quantos estão no time Sub-20 do clube agora. */
    public int tamanho(Clube c) {
        int n = 0;
        for (int id : c.base) {
            Jogador j = m.ativo(id);
            if (j != null && j.noSub20 && !j.amador()) n++;
        }
        return n;
    }

    /** O júnior passa a ser do time Sub-20 (contrato de base com o salário do Sub-20). */
    public void entrar(Jogador j) {
        j.noSub20 = true;
        if (j.contrato != null && j.contrato.tipo == TipoContrato.BASE) j.contrato.salario = salario(m.ano);
    }

    /** Sub-20 -> Base: volta ao salário de júnior da base. */
    public void sair(Jogador j) {
        j.noSub20 = false;
        if (j.contrato != null && j.contrato.tipo == TipoContrato.BASE)
            j.contrato.salario = br.futebas.financas.Financas.arred(br.futebas.financas.Financas.salarioMinimo(m.ano) * 0.4);
    }

    /**
     * Garante o Sub-20 do clube (requisito: todo clube tem Sub-20 com 23): na primeira vez os
     * melhores juniores da base sobem; faltando gente, o clube gera jogadores novos.
     */
    public void garantir(Clube c) {
        if (c == null || c.virtual || !c.existe) return;
        boolean primeira = !c.sub20Montado;
        if (primeira) {
            c.sub20Montado = true;
            puxarDaBase(c);
        }
        completar(c, 16, 20);
        if (primeira && c.humano) peneiraDoTecnico(c, true);
    }

    /**
     * Peneira do técnico (requisito 15/11/2026): uma turma por temporada, normalmente 5 garotos,
     * às vezes 6 ou 7 ({@link Balanceamento#PENEIRA_TECNICO_PESOS}); 15–17 anos, nacionalidade do
     * clube, potencial variado e, às vezes, um talento. Ele escolhe quem sobe ao Sub-20; os outros
     * saem do mundo na virada ({@link #dispensarNaoEscolhidos}). Na 1ª vez (jogo novo) conta quem já
     * está na base fora do Sub-20. Sorteios num Rng derivado do mundo (mesma semente, mesma turma).
     * Retorna quantos garotos chegaram.
     */
    public int peneiraDoTecnico(Clube c, boolean inicial) {
        if (c == null || c.virtual || !c.existe) return 0;
        Rng r = m.rng.derivar(31L * c.id.hashCode() + m.ano);
        int n = Balanceamento.PENEIRA_TECNICO_MIN + r.peso(Balanceamento.PENEIRA_TECNICO_PESOS);
        int tem = 0;
        if (inicial) for (int id : c.base) {
            Jogador j = m.ativo(id);
            if (j != null && !j.noSub20) tem++;
        }
        // a turma é da temporada que começa (no calendário de janeiro, a virada acontece ainda no ano velho)
        int anoRef = inicial || "europeu".equals(c.calendario) ? m.ano : m.ano + 1;
        if (c.nivelInicial <= 0) c.nivelInicial = c.nivel();
        double media = Balanceamento.potencialPeneira(Math.max(c.nivel(), c.nivelInicial), c.estrutura);
        List<Jogador> turma = new ArrayList<>();
        for (int k = tem; k < n; k++) {
            Jogador j = s.populador.newgen(c.pais, c, media);
            if (j == null) break;
            turma.add(j);
        }
        if (turma.isEmpty()) return 0;
        Jogador talento = r.chance(Balanceamento.PENEIRA_TECNICO_TALENTO) ? turma.get(r.inteiro(turma.size())) : null;
        for (Jogador j : turma) {
            int idade = r.entre(15, 17);
            j.anoNasc = anoRef - idade;
            if (j == talento) {
                j.talento = Math.max(j.talento, 8);
                j.potencial = Math.min(Balanceamento.POTENCIAL_MAX_PENEIRA,
                        j.potencial + r.real(Balanceamento.CRAQUE_BONUS_MIN, Balanceamento.CRAQUE_BONUS_MAX));
                j.potencialRef = j.potencial;
            }
            Evolucao.criarAtributos(j, Evolucao.forcaInicial(j.potencial, idade, r), r);
        }
        m.mensagemDoClube(c.id, "jogador", "Peneira " + anoRef,
                turma.size() + " garotos chegaram da peneira (15 a 17 anos). Veja em Base › Base e peneira e suba ao Sub-20 quem você quiser; "
                        + "quem não subir vai embora no fim da temporada.");
        return turma.size();
    }

    /** Fim da temporada: a turma nova da peneira de cada técnico dos países desse calendário. */
    int peneirasDosTecnicos(Set<String> paises) {
        int n = 0;
        for (Clube c : m.clubes.values())
            if (temPeneiraDoTecnico(c) && paises.contains(c.pais)) n += peneiraDoTecnico(c, false);
        return n;
    }

    /** O clube recebe a peneira anual do técnico (e não a peneira comum da IA)? */
    public static boolean temPeneiraDoTecnico(Clube c) {
        return c.humano && !c.virtual && c.existe && c.sub20Montado;
    }

    /** Juniores da base (fora do Sub-20, com idade de Sub-20) sobem até completar 23, os melhores primeiro. */
    private void puxarDaBase(Clube c) {
        int tem = tamanho(c);
        if (tem >= Balanceamento.SUB20_ELENCO) return;
        List<Jogador> b = new ArrayList<>();
        for (int id : c.base) {
            Jogador j = m.ativo(id);
            if (j != null && !j.noSub20 && j.sub20(m.ano) && !j.amador()) b.add(j);
        }
        b.sort(Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id));
        for (Jogador j : b) {
            if (tem >= Balanceamento.SUB20_ELENCO) break;
            entrar(j);
            tem++;
        }
    }

    /** Gera jogadores (nacionalidade do clube, força pelo nível dele) até o Sub-20 ter 23. */
    private void completar(Clube c, int idadeMin, int idadeMax) {
        int falta = Balanceamento.SUB20_ELENCO - tamanho(c);
        if (falta <= 0) return;
        double media = Balanceamento.potencialPeneira(Math.max(c.nivel(), c.nivelInicial), c.estrutura);
        for (int k = 0; k < falta; k++) {
            Jogador j = s.populador.newgen(c.pais, c, media);
            if (j == null) break;
            int idade = m.rng.entre(idadeMin, idadeMax);
            j.anoNasc = m.ano - idade;
            Evolucao.criarAtributos(j, Evolucao.forcaInicial(j.potencial, idade, m.rng), m.rng);
            entrar(j);
            gerados++;
        }
        cacheForca.remove(c.id);
        cacheIds.remove(c.id);
    }

    /**
     * Fim da temporada nos clubes com Sub-20 (antes da peneira): no clube do técnico, os juniores da
     * peneira que ele não subiu ao Sub-20 vão embora para sempre (saem do mundo).
     */
    void dispensarNaoEscolhidos(Set<String> paises) {
        Set<Integer> saem = new HashSet<>();
        for (Clube c : m.clubes.values()) {
            if (!c.humano || c.virtual || !c.existe || !c.sub20Montado || !paises.contains(c.pais)) continue;
            int n = 0;
            for (int id : new ArrayList<>(c.base)) {
                Jogador j = m.ativo(id);
                if (j == null || j.noSub20 || j.amador()) continue;
                c.base.remove(Integer.valueOf(id));
                saem.add(id);
                n++;
            }
            if (n > 0) m.mensagemDoClube(c.id, "jogador", "Peneira encerrada",
                    n + (n == 1 ? " júnior da peneira que não subiu ao Sub-20 deixou o clube." : " juniores da peneira que não subiram ao Sub-20 deixaram o clube."));
        }
        sumir(saem);
    }

    /** Jogadores que saem do mundo de vez (não viram agentes livres nem aparecem em outro lugar). */
    void sumir(Set<Integer> ids) {
        if (ids.isEmpty()) return;
        for (int id : ids) {
            Jogador j = m.ativo(id);
            if (j == null) continue;
            Clube c = m.clube(j.clubeId);
            if (c != null) {
                c.base.remove(Integer.valueOf(id));
                c.tatica.titulares.replaceAll(x -> x != null && x == id ? -1 : x);
                c.tatica.reservas.removeIf(x -> x != null && x == id);
            }
            j.noSub20 = false;
            j.clubeId = null;
            j.contrato = null;
            j.status = StatusJogador.DISPENSADO;
        }
        m.jogadores.removeIf(j -> ids.contains(j.id));
        m.reindexar();
    }

    /**
     * Fim da temporada, depois da peneira e das promoções: todo clube com Sub-20 volta a ter 23
     * (a IA sobe primeiro os juniores da peneira; o técnico escolhe os dele, e o resto é gerado).
     */
    void reporTodos(Set<String> paises) {
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe || !c.sub20Montado || !paises.contains(c.pais)) continue;
            if (!c.humano) puxarDaBase(c);
            completar(c, 16, 17);
        }
    }

    // ======================================================================= montagem
    /**
     * Monta as competições sub-20 da temporada a partir das profissionais recém-montadas (mesmo
     * calendário, mesmas semanas). {@code inicial}: espelha o profissional.
     */
    void abrir(List<Torneio> pro, boolean inicial) {
        // todo clube dos países dessas competições tem o Sub-20 de 23 antes de montar as tabelas
        Set<String> paisesPro = new HashSet<>();
        for (Torneio t : pro) if (t.pais != null && detalhado(t.pais)) paisesPro.add(t.pais);
        for (Clube c : m.clubes.values()) if (apto(c) && paisesPro.contains(c.pais)) garantir(c);
        List<Torneio> novos = new ArrayList<>();
        Map<String, List<Torneio>> divisoes = new LinkedHashMap<>();
        for (Torneio t : pro) {
            if ("nacional".equals(t.ambito) && t.divisao != null && t.pais != null && detalhado(t.pais))
                divisoes.computeIfAbsent(t.pais, k -> new ArrayList<>()).add(t);
        }
        for (Map.Entry<String, List<Torneio>> e : divisoes.entrySet()) piramide(e.getKey(), e.getValue(), inicial, novos);
        // estaduais com divisões de acesso: cada divisão tem o seu Sub-20, com pirâmide própria do
        // estado (requisito: os clubes pequenos, como o Colo Colo-BA da Série B baiana, também jogam)
        Map<String, List<Torneio>> estados = new LinkedHashMap<>();
        for (Torneio t : pro)
            if ("estadual".equals(t.ambito) && t.pais != null && detalhado(t.pais)) estados.computeIfAbsent(t.pais + "|" + t.uf, k -> new ArrayList<>()).add(t);
        Set<String> feitos = new HashSet<>();
        for (List<Torneio> l : estados.values()) {
            if (l.size() < 2) continue;
            piramideEstadual(l, inicial, novos);
            for (Torneio t : l) feitos.add(t.id);
        }
        Set<String> jaContinental = new HashSet<>();
        for (Torneio t : pro) {
            List<String> part;
            switch (t.ambito == null ? "" : t.ambito) {
                case "estadual" -> {
                    if (feitos.contains(t.id) || t.pais == null || !detalhado(t.pais) || (t.divisao != null && t.divisao > 1)) continue;
                    part = estadual(t, inicial);
                }
                case "copa_nacional" -> {
                    if (t.pais == null || !detalhado(t.pais) || t.id.startsWith("bra-copa-") && !t.id.equals("bra-copa-do-brasil")) continue;
                    part = copa(t, inicial);
                }
                case "continental" -> part = continental(t, inicial, jaContinental);
                default -> {
                    continue;
                }
            }
            if (part.size() < 4) continue;
            novos.add(novo(t, part));
        }
        for (Torneio t : novos) for (String id : t.participantes) {
            Clube c = m.clube(id);
            if (c != null && !c.sub20Montado) garantir(c);
        }
        m.torneiosSub20.addAll(novos);
    }

    /**
     * Pirâmide Sub-20 de um estado com divisões de acesso: 2026 espelha o profissional; depois cada
     * divisão = a do ano passado - promovidos - rebaixados + quem desce de cima + quem sobe de baixo
     * (do formato; 2 se faltar, como no profissional). Completa com os clubes do estado de fora (pela
     * divisão do profissional e a força do Sub-20); todo clube do estadual profissional joga um Sub-20.
     */
    private void piramideEstadual(List<Torneio> divs, boolean inicial, List<Torneio> novos) {
        divs.sort(Comparator.comparingInt(t -> t.divisao == null ? 1 : t.divisao));
        int k = divs.size();
        boolean temAnterior = !inicial;
        for (Torneio t : divs) if (anterior(t.id + SUFIXO) == null) temAnterior = false;
        Map<String, Integer> divPro = new HashMap<>();
        for (int i = 0; i < k; i++) for (String id : divs.get(i).participantes) divPro.putIfAbsent(id, i);
        List<List<String>> membros = new ArrayList<>();
        Set<String> usados = new HashSet<>();
        if (!temAnterior) {
            for (Torneio t : divs) {
                List<String> l = new ArrayList<>();
                for (String id : t.participantes) if (apto(m.clube(id)) && usados.add(id)) l.add(id);
                membros.add(l);
            }
        } else {
            List<List<String>> ant = new ArrayList<>();
            int[] prom = new int[k], reb = new int[k];
            for (int i = 0; i < k; i++) {
                Torneio a = anterior(divs.get(i).id + SUFIXO);
                ant.add(a.classificacao);
                Formato f = a.formato;
                prom[i] = i == 0 ? 0 : f != null && f.promovidos != null ? f.promovidos : 2;
                reb[i] = i == k - 1 ? 0 : f != null && f.rebaixados != null ? f.rebaixados : 2;
            }
            for (int i = 0; i < k; i++) {
                List<String> cl = ant.get(i), l = new ArrayList<>();
                for (int p = prom[i]; p < cl.size() - reb[i]; p++) l.add(cl.get(p));
                if (i > 0) {
                    List<String> cima = ant.get(i - 1);
                    for (int p = Math.max(0, cima.size() - reb[i - 1]); p < cima.size(); p++) l.add(cima.get(p));
                }
                if (i < k - 1) {
                    List<String> baixo = ant.get(i + 1);
                    for (int p = 0; p < Math.min(prom[i + 1], baixo.size()); p++) l.add(baixo.get(p));
                }
                List<String> fin = new ArrayList<>();
                for (String id : l) if (apto(m.clube(id)) && usados.add(id)) fin.add(id);
                membros.add(fin);
            }
        }
        Torneio t0 = divs.get(0);
        List<Clube> fora = new ArrayList<>();
        for (Clube c : m.clubes.values())
            if (apto(c) && !usados.contains(c.id) && t0.pais.equals(c.pais) && java.util.Objects.equals(t0.uf, c.uf)) fora.add(c);
        fora.sort(Comparator.comparingInt((Clube c) -> divPro.getOrDefault(c.id, 99)).thenComparingDouble(c -> -forca(c)).thenComparing(c -> c.id));
        int pf = 0;
        for (int i = 0; i < k; i++) {
            int n = divs.get(i).participantes.size();
            while (membros.get(i).size() < n && pf < fora.size()) membros.get(i).add(fora.get(pf++).id);
        }
        // quem disputa o estadual profissional e ficou sem vaga entra na última divisão
        for (; pf < fora.size(); pf++) if (divPro.containsKey(fora.get(pf).id)) membros.get(k - 1).add(fora.get(pf).id);
        for (int i = 0; i < k; i++) if (membros.get(i).size() >= 4) novos.add(novo(divs.get(i), membros.get(i)));
    }

    private Torneio novo(Torneio pro, List<String> part) {
        Torneio t = new Torneio();
        t.id = pro.id + SUFIXO;
        t.nome = pro.nome + " Sub-20";
        t.ambito = pro.ambito;
        t.pais = pro.pais;
        t.uf = pro.uf;
        t.confederacao = pro.confederacao;
        t.divisao = pro.divisao;
        t.calendario = pro.calendario;
        t.temporada = pro.temporada;
        t.ano = pro.ano;
        t.regras = pro.regras;
        t.semente = pro.semente * 31 + 20;
        int n = pro.participantes.size();
        t.formato = part.size() == n ? copiaFormato(pro.formato)
                : FormatoPadrao.para(new Db.Competicao(t.id, t.nome, t.ambito, t.pais, t.uf, t.confederacao, t.divisao, t.calendario, null), part.size());
        t.participantes = new ArrayList<>(part);
        if (t.formato != null && t.formato.estreantesDepois() > 0) {
            // entradas escalonadas: os mais fortes do sub-20 estreiam por último
            t.participantes.sort(Comparator.comparingInt((String id) -> divSub20(m.clube(id))).thenComparingDouble(id -> -forca(m.clube(id))).thenComparing(id -> id));
        }
        t.semanaInicio = pro.semanaInicio;
        t.semanaFim = pro.semanaFim;
        t.semanasJogo = new ArrayList<>(pro.semanasJogo);
        t.planejar();
        t.avisos.clear();
        return t;
    }

    /** Cópia do formato com as vagas apontando para as competições sub-20. */
    static Formato copiaFormato(Formato f) {
        if (f == null) return null;
        Formato c = new Formato();
        c.participantes = f.participantes;
        c.pontos_vitoria = f.pontos_vitoria;
        c.desempates = f.desempates;
        c.fases = f.fases;
        c.rebaixados = f.rebaixados;
        c.promovidos = f.promovidos;
        c.observacoes = f.observacoes;
        if (f.vagas != null) {
            c.vagas = new ArrayList<>();
            for (Formato.Vaga v : f.vagas) {
                Formato.Vaga x = new Formato.Vaga();
                x.para = v.para == null ? null : v.para + SUFIXO;
                x.posicoes = v.posicoes;
                c.vagas.add(x);
            }
        }
        return c;
    }

    private static int divSub20(Clube c) {
        return c == null || c.divisaoSub20 <= 0 ? 99 : c.divisaoSub20;
    }

    private Torneio anterior(String id) {
        Torneio a = null;
        for (Torneio t : m.torneiosSub20Anteriores) if (t.id.equals(id) && (a == null || t.ano > a.ano)) a = t;
        return a;
    }

    private Torneio ultimoEncerrado(String id) {
        Torneio a = null;
        for (List<Torneio> l : List.of(m.torneiosSub20, m.torneiosSub20Anteriores))
            for (Torneio t : l) if (t.id.equals(id) && t.encerrado && (a == null || t.ano > a.ano)) a = t;
        return a;
    }

    /** Pirâmide nacional sub-20 do país: espelho em 2026, depois acesso e rebaixamento próprios. */
    private void piramide(String pais, List<Torneio> divs, boolean inicial, List<Torneio> novos) {
        divs.sort(Comparator.comparingInt(t -> t.divisao));
        boolean temAnterior = !inicial;
        for (Torneio t : divs) if (anterior(t.id + SUFIXO) == null) temAnterior = false;
        Map<String, double[]> chave = new HashMap<>(); // {divisão nova, grupo, posição}
        if (temAnterior) {
            for (int k = 0; k < divs.size(); k++) {
                Torneio a = anterior(divs.get(k).id + SUFIXO);
                int d = divs.get(k).divisao;
                boolean temAbaixo = k + 1 < divs.size();
                int prom = a.formato != null && a.formato.promovidos != null ? a.formato.promovidos : 0;
                int reb = a.formato != null && a.formato.rebaixados != null ? a.formato.rebaixados : 0;
                List<String> cl = a.classificacao;
                for (int i = 0; i < cl.size(); i++) {
                    int nova = d, grupo = 1;
                    if (k > 0 && i < prom) {
                        nova = divs.get(k - 1).divisao;
                        grupo = 2;
                    } else if (i >= cl.size() - reb) {
                        nova = temAbaixo ? divs.get(k + 1).divisao : 98;
                        grupo = 0;
                    }
                    chave.put(cl.get(i), new double[]{nova, grupo, i});
                }
            }
        }
        for (Clube c : m.clubes.values()) if (pais.equals(c.pais)) c.divisaoSub20 = 0;
        List<String> fila = new ArrayList<>(chave.keySet());
        fila.sort(Comparator.comparingDouble((String id) -> chave.get(id)[0]).thenComparingDouble(id -> chave.get(id)[1])
                .thenComparingDouble(id -> chave.get(id)[2]).thenComparing(id -> id));
        // de fora da pirâmide: pela divisão do profissional e pela força do sub-20
        List<Clube> fora = new ArrayList<>();
        for (Clube c : m.clubes.values()) if (apto(c) && pais.equals(c.pais) && (!chave.containsKey(c.id) || chave.get(c.id)[0] >= 98)) fora.add(c);
        fora.sort(Comparator.comparingDouble((Clube c) -> chave.containsKey(c.id) ? 1 : 0)
                .thenComparingInt(c -> c.divisao <= 0 ? 99 : c.divisao).thenComparingDouble(c -> -forca(c)).thenComparing(c -> c.id));
        Set<String> usados = new HashSet<>();
        int ponteiro = 0, pf = 0;
        for (Torneio pro : divs) {
            int n = pro.participantes.size();
            List<String> membros = new ArrayList<>();
            if (!temAnterior) {
                for (String id : pro.participantes) if (apto(m.clube(id)) && usados.add(id)) membros.add(id);
            } else {
                while (membros.size() < n && ponteiro < fila.size()) {
                    String id = fila.get(ponteiro++);
                    if (apto(m.clube(id)) && chave.get(id)[0] < 98 && usados.add(id)) membros.add(id);
                }
                while (membros.size() < n && pf < fora.size()) {
                    String id = fora.get(pf++).id;
                    if (usados.add(id)) membros.add(id);
                }
            }
            for (String id : membros) m.clube(id).divisaoSub20 = pro.divisao;
            if (membros.size() >= 4) novos.add(novo(pro, membros));
        }
        for (Clube c : m.clubes.values()) {
            if (!pais.equals(c.pais)) continue;
            double[] k = chave.get(c.id);
            c.posicaoSub20 = k == null ? 50 : (int) k[2];
        }
    }

    /** Ranking sub-20 do país: divisão sub-20, posição nela, força do time sub-20. */
    private List<Clube> ranking(String pais, String uf) {
        List<Clube> l = new ArrayList<>();
        for (Clube c : m.clubes.values()) if (apto(c) && pais.equals(c.pais) && (uf == null || uf.equals(c.uf))) l.add(c);
        l.sort(Comparator.comparingInt(Sub20::divSub20).thenComparingInt(c -> c.posicaoSub20).thenComparingDouble(c -> -forca(c)).thenComparing(c -> c.id));
        return l;
    }

    private List<String> estadual(Torneio pro, boolean inicial) {
        int n = pro.participantes.size();
        LinkedHashSet<String> part = new LinkedHashSet<>();
        Torneio ant = anterior(pro.id + SUFIXO);
        if (inicial || ant == null) {
            for (String id : pro.participantes) if (apto(m.clube(id))) part.add(id);
        } else {
            int reb = ant.formato != null && ant.formato.rebaixados != null ? ant.formato.rebaixados : Math.max(1, n / 8);
            for (int i = 0; i < ant.classificacao.size() - reb; i++) if (apto(m.clube(ant.classificacao.get(i)))) part.add(ant.classificacao.get(i));
        }
        for (Clube c : ranking(pro.pais, pro.uf)) {
            if (part.size() >= n) break;
            part.add(c.id);
        }
        List<String> l = new ArrayList<>(part);
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    private List<String> copa(Torneio pro, boolean inicial) {
        int n = pro.participantes.size();
        LinkedHashSet<String> part = new LinkedHashSet<>();
        if (inicial) {
            for (String id : pro.participantes) if (apto(m.clube(id))) part.add(id);
        } else {
            part.addAll(qualificados(pro.id + SUFIXO, Set.of()));
        }
        for (Clube c : ranking(pro.pais, null)) {
            if (part.size() >= n) break;
            part.add(c.id);
        }
        List<String> l = new ArrayList<>(part);
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    private List<String> continental(Torneio pro, boolean inicial, Set<String> ja) {
        int n = pro.participantes.size();
        LinkedHashSet<String> part = new LinkedHashSet<>();
        String id20 = pro.id + SUFIXO;
        Torneio ultimo = ultimoEncerrado(id20);
        if (inicial || ultimo == null && anterior(id20) == null) {
            for (String id : pro.participantes) if (apto(m.clube(id)) && !ja.contains(id)) part.add(id);
        } else {
            if (ultimo != null && ultimo.campeao != null && apto(m.clube(ultimo.campeao)) && !ja.contains(ultimo.campeao)) part.add(ultimo.campeao);
            part.addAll(qualificados(id20, ja));
        }
        if (part.size() < n) {
            List<Clube> pool = new ArrayList<>();
            for (Clube c : m.clubes.values()) {
                if (!apto(c) || ja.contains(c.id) || part.contains(c.id) || c.divisaoSub20 <= 0) continue;
                if (pro.confederacao != null && !pro.confederacao.equalsIgnoreCase(c.confederacao)) continue;
                if (pro.confederacao == null && pro.pais != null && !pro.pais.equals(c.pais)) continue;
                pool.add(c);
            }
            pool.sort(Comparator.comparingInt(Sub20::divSub20).thenComparingInt(c -> c.posicaoSub20).thenComparingDouble(c -> -forca(c)).thenComparing(c -> c.id));
            for (Clube c : pool) {
                if (part.size() >= n) break;
                part.add(c.id);
            }
        }
        List<String> l = new ArrayList<>(part);
        if (l.size() > n) l = new ArrayList<>(l.subList(0, n));
        // continental menor que o profissional: potência de 2 ou múltiplo de 4 para o formato de reserva
        if (l.size() < n && l.size() > 4) l = new ArrayList<>(l.subList(0, l.size() / 4 * 4));
        ja.addAll(l);
        return l;
    }

    /** Classificados para {@code id20} pelas vagas dos formatos sub-20 já encerrados. */
    private List<String> qualificados(String id20, Set<String> ja) {
        List<String> l = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (List<Torneio> lista : List.of(m.torneiosSub20, m.torneiosSub20Anteriores)) {
            for (Torneio t0 : lista) {
                if (!vistos.add(t0.id)) continue;
                Torneio t = ultimoEncerrado(t0.id);
                if (t == null || t.formato == null || t.formato.vagas == null) continue;
                for (Formato.Vaga v : t.formato.vagas) {
                    if (!id20.equals(v.para) || v.posicoes == null) continue;
                    for (int pos : v.posicoes) {
                        int idx = pos - 1;
                        while (idx < t.classificacao.size() && (l.contains(t.classificacao.get(idx)) || ja.contains(t.classificacao.get(idx))
                                || !apto(m.clube(t.classificacao.get(idx))))) idx++;
                        if (idx < t.classificacao.size()) l.add(t.classificacao.get(idx));
                    }
                }
            }
        }
        return l;
    }

    // ======================================================================= semana e fim
    void semana(int sa) {
        for (Torneio t : m.torneiosSub20) {
            t.jogarAte(sa, this);
            if (t.encerrado && !t.processado) encerrar(t);
        }
    }

    /** Fim da temporada de um calendário: conclui e move para "anteriores". */
    void encerrarTemporada(String cal) {
        List<Torneio> daTemporada = new ArrayList<>();
        for (Torneio t : m.torneiosSub20) if (cal.equals(t.calendario)) daTemporada.add(t);
        for (Torneio t : daTemporada) {
            if (!t.encerrado) t.concluir(this);
            if (!t.processado) encerrar(t);
        }
        for (Torneio t : daTemporada) {
            if (!"nacional".equals(t.ambito)) continue;
            for (int i = 0; i < t.classificacao.size(); i++) {
                Clube c = m.clube(t.classificacao.get(i));
                if (c != null) c.posicaoSub20 = i;
            }
        }
        m.torneiosSub20Anteriores.removeIf(t -> cal.equals(t.calendario));
        m.torneiosSub20Anteriores.addAll(daTemporada);
        m.torneiosSub20.removeAll(daTemporada);
    }

    private void encerrar(Torneio t) {
        t.processado = true;
        if (t.campeao == null) return;
        Historia.Titulo tit = new Historia.Titulo();
        tit.ano = t.ano;
        tit.temporada = t.temporada;
        tit.competicaoId = t.id;
        tit.competicao = t.nome;
        tit.ambito = "sub20";
        tit.pais = t.pais;
        tit.campeaoId = t.campeao;
        tit.campeao = m.nomeParticipante(t.campeao);
        if (t.vice != null) {
            tit.viceId = t.vice;
            tit.vice = m.nomeParticipante(t.vice);
        }
        tit.participantes = t.participantes.size();
        List<int[]> art = t.artilheiros(1);
        if (!art.isEmpty()) {
            Jogador j = m.jogador(art.get(0)[0]);
            if (j != null) {
                tit.artilheiroId = j.id;
                tit.artilheiro = j.nomeExibicao();
                tit.golsArtilheiro = art.get(0)[1];
            }
        }
        m.historia.titulos.add(tit);
        Clube h = m.clubeHumano();
        if (h != null && h.id.equals(t.campeao)) m.mensagem("resultado", "Título do sub-20", "O sub-20 do " + h.nome + " é campeão: " + t.nome + " " + t.temporada + "!");
    }

    // ======================================================================= time e partidas
    /** Jogadores do sub-20 do clube (até 20 anos no ano: base + profissionais jovens), os melhores primeiro. */
    public List<Jogador> elenco(Clube c) {
        List<Jogador> l = new ArrayList<>();
        if (c == null) return l;
        // clube do técnico: Base → Sub-20 → Profissional (na primeira vez, os melhores juniores sobem)
        if (c.humano && !c.sub20Montado) garantir(c);
        if (c.sub20Montado) {
            // o time Sub-20 do clube (23, requisito)
            for (int id : c.base) {
                Jogador j = m.ativo(id);
                if (j != null && j.noSub20 && !j.amador()) l.add(j);
            }
            l.sort(Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id));
            return l;
        }
        // clube ainda sem Sub-20 montado (fora dos países em detalhe): os jovens do clube
        for (List<Integer> ids : List.of(c.base, c.elenco)) {
            for (int id : ids) {
                Jogador j = m.ativo(id);
                if (j != null && j.sub20(m.ano) && !j.amador()) l.add(j);
            }
        }
        l.sort(Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id));
        return l;
    }

    /** O time que joga: os 11 mais fortes do sub-20 sem lesão (pode ter menos; o resto são juniores anônimos). */
    public List<Jogador> onze(Clube c) {
        List<Jogador> l = new ArrayList<>();
        for (Jogador j : elenco(c)) {
            if (l.size() >= 11) break;
            if (j.lesaoSemanas > 0) continue;
            l.add(j);
        }
        return l;
    }

    /** Força dos juniores anônimos que completam o time quando faltam jogadores. */
    public static double forcaJunior(Clube c) {
        return Balanceamento.SUB20_COMPLETA * c.nivel();
    }

    /** Força do time sub-20 (média dos 11 melhores, completando com juniores anônimos). */
    public double forca(Clube c) {
        if (c == null) return 30;
        semanaAtual();
        double[] f = cacheForca.get(c.id);
        if (f != null) return f[0];
        double s = 0;
        int n = 0;
        List<Integer> ids = new ArrayList<>();
        for (Jogador j : onze(c)) {
            s += j.forca;
            ids.add(j.id);
            n++;
        }
        double completa = forcaJunior(c);
        double v = (s + (11 - n) * completa) / 11.0;
        cacheForca.put(c.id, new double[]{v});
        int[] arr = new int[ids.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
        cacheIds.put(c.id, arr);
        return v;
    }

    private void semanaAtual() {
        int sa = m.semanaAbsoluta();
        if (sa != semanaCache) {
            semanaCache = sa;
            cacheForca.clear();
            cacheIds.clear();
        }
    }

    @Override
    public ResultadoPartida jogar(Torneio t, Partida p, boolean prorrogacao) {
        Clube cm = m.clube(p.mandante), cv = m.clube(p.visitante);
        double fa = forca(cm), fb = forca(cv);
        Rng rng = m.rng;
        int[] g = Selecoes.placarRapido(fa, fb, true, rng);
        ResultadoPartida r = new ResultadoPartida();
        r.mandante = cm.nome;
        r.visitante = cv.nome;
        r.mandanteId = cm.id;
        r.visitanteId = cv.id;
        r.golsMandante = g[0];
        r.golsVisitante = g[1];
        if (prorrogacao && g[0] == g[1]) {
            r.prorrogacao = true;
            if (rng.chance(0.35)) {
                if (rng.chance(1 / (1 + Math.exp(-0.08 * (fa - fb))))) r.golsMandante++;
                else r.golsVisitante++;
            }
        }
        autores(r, 0, cacheIds.get(cm.id), r.golsMandante, rng);
        autores(r, 1, cacheIds.get(cv.id), r.golsVisitante, rng);
        estatisticas(r, p, cacheIds.get(cm.id), cacheIds.get(cv.id));
        return r;
    }

    /**
     * Jogos, gols, assistências e nota dos onze de cada lado (aba Sub-20). Usa um sorteio próprio,
     * derivado do jogo, para não mexer na sequência do mundo (resultados e saves continuam iguais).
     */
    private void estatisticas(ResultadoPartida r, Partida p, int[] idsA, int[] idsB) {
        java.util.SplittableRandom sr = new java.util.SplittableRandom(31L * p.id + 977L * m.semanaAbsoluta() + (p.mandante == null ? 0 : p.mandante.hashCode()));
        int[][] lados = {idsA, idsB};
        java.util.Map<Integer, double[]> nota = new java.util.HashMap<>();
        for (int s = 0; s < 2; s++) {
            if (lados[s] == null) continue;
            int pro = s == 0 ? r.golsMandante : r.golsVisitante, contra = s == 0 ? r.golsVisitante : r.golsMandante;
            double base = 6.2 + (pro > contra ? 0.5 : pro < contra ? -0.4 : 0) - (contra >= 3 ? 0.3 : 0);
            for (int id : lados[s]) {
                Jogador j = m.ativo(id);
                if (j == null) continue;
                j.sub20Jogos++;
                nota.put(id, new double[]{base + (sr.nextDouble() - 0.5) * 1.2});
            }
        }
        for (int[] g : r.gols) {
            int s = g[0], autor = g[1];
            int[] ids = s == 0 ? idsA : idsB;
            if (autor >= 0) {
                Jogador j = m.ativo(autor);
                if (j != null) j.sub20Gols++;
                if (nota.containsKey(autor)) nota.get(autor)[0] += 0.9;
            }
            // assistência: 70% dos gols, de outro jogador do time (meias e pontas mais)
            if (ids != null && ids.length > 1 && sr.nextDouble() < 0.7) {
                int tentativas = 0, a;
                do {
                    a = ids[sr.nextInt(ids.length)];
                    Jogador x = m.ativo(a);
                    boolean criador = x != null && (x.posicao == Posicao.MEI || x.posicao == Posicao.PE || x.posicao == Posicao.PD || x.posicao == Posicao.MC);
                    if (a != autor && x != null && x.posicao != Posicao.GOL && (criador || sr.nextDouble() < 0.4)) break;
                } while (++tentativas < 8);
                Jogador x = m.ativo(a);
                if (a != autor && x != null) {
                    x.sub20Assist++;
                    if (nota.containsKey(a)) nota.get(a)[0] += 0.5;
                }
            }
        }
        for (var e : nota.entrySet()) {
            Jogador j = m.ativo(e.getKey());
            if (j != null) j.sub20SomaNotas += Math.max(3, Math.min(10, e.getValue()[0]));
        }
    }

    private void autores(ResultadoPartida r, int lado, int[] ids, int gols, Rng rng) {
        for (int k = 0; k < gols; k++) {
            int autor = -1;
            if (ids != null && ids.length > 0 && rng.chance(0.85)) {
                // atacantes marcam mais
                double[] w = new double[ids.length];
                for (int i = 0; i < ids.length; i++) {
                    Jogador j = m.ativo(ids[i]);
                    w[i] = j == null ? 0.1 : switch (j.posicao) {
                        case CA -> 5;
                        case PE, PD -> 3;
                        case MEI -> 2.5;
                        case MC -> 1.2;
                        case GOL -> 0.01;
                        default -> 0.4;
                    };
                }
                autor = ids[rng.peso(w)];
            }
            r.gols.add(new int[]{lado, autor, rng.entre(1, 90), 0, 0});
        }
    }

    /** Sub-20: a competição sub-20 correspondente do mesmo ano (Libertadores Sub-20 → Sul-Americana Sub-20). */
    @Override
    public Torneio outroTorneio(Torneio atual, String competicaoId) {
        String id = competicaoId + SUFIXO;
        for (List<Torneio> l : List.of(m.torneiosSub20, m.torneiosSub20Anteriores))
            for (Torneio t : l) if (t.id.equals(id) && t.ano == atual.ano) return t;
        return null;
    }

    @Override
    public int[] penaltis(Torneio t, Partida p) {
        int a = 0, b = 0;
        for (int i = 0; i < 5; i++) {
            if (m.rng.chance(0.72)) a++;
            if (m.rng.chance(0.72)) b++;
        }
        int k = 0;
        while (a == b && k++ < 20) {
            if (m.rng.chance(0.72)) a++;
            if (m.rng.chance(0.72)) b++;
        }
        if (a == b) a++;
        return new int[]{a, b};
    }
}
