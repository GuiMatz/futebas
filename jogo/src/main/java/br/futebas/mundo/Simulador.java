package br.futebas.mundo;

import br.futebas.Balanceamento;
import br.futebas.competicao.Executor;
import br.futebas.competicao.MontadorTemporada;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.financas.Financas;
import br.futebas.historia.Historia;
import br.futebas.ia.Diretoria;
import br.futebas.ia.Mercado;
import br.futebas.kings.Kings;
import br.futebas.kings.LigaKings;
import br.futebas.partida.Escalado;
import br.futebas.partida.Escalador;
import br.futebas.partida.Concentracao;
import br.futebas.partida.Estilo;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.JogoParalelo;
import br.futebas.partida.Marcacao;
import br.futebas.partida.PerfilTime;
import br.futebas.partida.Lance;
import br.futebas.partida.MotorPartida;
import br.futebas.partida.OpcoesPartida;
import br.futebas.partida.Penaltis;
import br.futebas.partida.ResultadoPartida;
import br.futebas.partida.TimePartida;
import br.futebas.selecao.Selecoes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * O motor do mundo inteiro: passa o tempo semana a semana a partir de 1º/jan/2026 e joga todas
 * as competições que caem na semana — ligas e copas de cada país no seu calendário (jan–dez ou
 * ago–mai), estaduais, continentais, seleções (datas FIFA, Copa do Mundo...) e Kings League.
 * Partidas: lance a lance nos países "detalhados" e nos jogos do humano; rápidas (Poisson pela
 * força do time) no resto do mundo. Roda mercado, diretoria, finanças, evolução, peneira e as
 * viradas de temporada de cada calendário. Não tem interface; a fachada é {@code br.futebas.Jogo}.
 */
public final class Simulador implements Executor {
    public final Mundo m;
    public final BancoMundo db;
    public final Populador populador;
    public final Mercado mercado;
    /** Negociações do técnico humano (assíncronas, com confirmação). */
    public final br.futebas.ia.Negociacoes negociacoes;
    public final Diretoria diretoria;
    public final br.futebas.ia.Treinadores treinadores;
    public final Kings kings;
    public final Selecoes selecoes;
    /** Competições sub-20 (só simulação, pirâmides próprias). */
    public final Sub20 sub20;
    /** Noticiário (manchetes com foco no técnico). */
    public final br.futebas.historia.Noticiario noticiario;

    /** Partidas narradas na última semana (jogos do humano). */
    public final List<ResultadoPartida> narradasDaSemana = new ArrayList<>();
    public ResultadoPartida ultimaNarrada;
    /** Acompanhamento ao vivo do jogo do técnico (interface); null = sem acompanhamento. */
    public transient br.futebas.partida.AoVivo aoVivo;
    private final Map<String, TimePartida[]> escalacoes = new HashMap<>();
    /** Opções dos jogos com tela ao vivo (mesma chave de {@link #escalacoes}): a disputa de pênaltis pede a ordem a ela. */
    private final Map<String, OpcoesPartida> vivos = new HashMap<>();
    /** Pré-simulação silenciosa do 1º turno europeu (na criação do mundo). */
    private boolean silencioso;
    private final Map<String, Rapido> cacheRapido = new HashMap<>();
    /** Partidas jogadas desde a criação (para medição). */
    public long partidasDetalhadas, partidasRapidas;

    private Simulador(Mundo m, BancoMundo db) {
        this.m = m;
        this.db = db;
        br.futebas.competicao.CompeticoesFicticias.registrar(db);
        this.populador = new Populador(m, db);
        this.mercado = new Mercado(m);
        this.negociacoes = new br.futebas.ia.Negociacoes(m, mercado);
        this.mercado.negociacoes = negociacoes;
        this.diretoria = new Diretoria(m);
        this.kings = new Kings(m, db);
        this.kings.nomeGerado = populador::nomeGerado; // atletas genéricos da Kings League IOS
        this.selecoes = new Selecoes(m, db);
        this.diretoria.selecoes = selecoes;
        this.treinadores = new br.futebas.ia.Treinadores(m, db, populador);
        this.diretoria.treinadores = treinadores;
        this.diretoria.kings = kings;
        this.sub20 = new Sub20(this);
        this.noticiario = new br.futebas.historia.Noticiario(m);
        this.diretoria.noticiario = noticiario;
        this.mercado.noticiario = noticiario;
    }

    // ======================================================================= criação
    /** Novo mundo em 1º/jan/2026. {@code clubeHumano} null = só assistir. */
    public static Simulador novo(BancoMundo db, long semente, String clubeHumano, String nomeTecnico) {
        return novo(db, semente, clubeHumano, nomeTecnico, null);
    }

    /** Novo mundo; {@code custom} != null liga os times customizados (Kings League IOS). */
    public static Simulador novo(BancoMundo db, long semente, String clubeHumano, String nomeTecnico, br.futebas.kings.KingsCustom.Definicao custom) {
        Mundo m = new Mundo();
        m.semente = semente;
        m.rng = new Rng(semente);
        m.ano = Balanceamento.ANO_INICIAL;
        m.semana = 1;
        m.banco = db.caminho == null ? null : db.caminho.toString();
        m.calendarioUnico = Balanceamento.CALENDARIO_UNICO;
        Simulador s = new Simulador(m, db);
        for (Db.Pais dp : db.paises.values()) s.adicionarPais(dp.id());
        for (Db.Clube dc : db.clubes.values()) s.adicionarClube(dc);
        s.populador.inicial();
        for (Clube c : m.clubes.values()) c.virtual = c.elenco.isEmpty() && c.base.isEmpty();
        s.atualizarNiveis();
        s.definirDetalhePadrao(clubeHumano);
        s.sub20.garantirBases();
        s.selecoes.criar();
        s.treinadores.criar();
        m.kings = Kings.criarLigas(db, custom);
        m.timesCustom = custom != null;
        s.kings.completarLigasCustom(); // Kings League IOS: presidente + atletas genéricos até 12
        if (clubeHumano != null) {
            Clube c = m.clube(clubeHumano);
            if (c == null || c.virtual) throw new IllegalArgumentException("Clube inválido para o técnico: " + clubeHumano);
            m.tecnico = new Tecnico(nomeTecnico == null || nomeTecnico.isBlank() ? "Técnico" : nomeTecnico);
            m.tecnico.clubeId = c.id;
            m.tecnico.reputacao = Math.max(15, c.reputacao * 0.7);
            c.humano = true;
            m.mensagem("diretoria", "Bem-vindo", "Bem-vindo ao " + c.nome + ". A diretoria deseja uma boa temporada.");
        }
        // calendário único: tudo começa do zero em jan/2026; senão (antigo) a temporada ago–mai 2025/26 já em andamento
        List<Torneio> europeus = m.calendarioUnico ? new ArrayList<>() : new MontadorTemporada(m, db, "europeu", m.ano - 1, true).montar();
        m.torneios.addAll(europeus);
        s.meioDeTemporada(europeus);
        s.sub20.abrir(europeus, true);
        s.sub20.semana(m.semanaAbsoluta() - 1);
        s.abrirTemporada("ano", m.ano, true);
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !"europeu".equals(c.calendario)) continue;
            c.tvPendente += Financas.tvPatrocinio(c, m.ano) / 2; // meia temporada pela frente
            c.tvParcelas = Balanceamento.TV_PARCELAS / 2;
        }
        m.torneios.addAll(s.selecoes.torneiosDoAno(m.ano));
        s.calibrarFinancas(true);
        s.ajustarAgenda();
        return s;
    }

    /**
     * Fixa a divisão de referência da receita e calibra a escala salarial de cada clube pelo
     * elenco (Brasfoot: salário = força × base do clube). Na criação do mundo, os contratos
     * passam a seguir a escala; ao carregar um jogo antigo, só calibra o que faltar.
     */
    void calibrarFinancas(boolean contratos) {
        // freio de receita: no jogo novo e em save antigo sem ele; num save de agora fica o da abertura da temporada
        // (recalcular no meio da temporada mudaria o futuro depois de carregar)
        boolean semFreio = true;
        for (Clube c : m.clubes.values()) if (c.receitaFreio > 0) { semFreio = false; break; }
        if (contratos || semFreio) Financas.atualizarFreio(m.clubes.values());
        for (Clube c : m.clubes.values()) {
            if (c.divisaoInicial == null) c.divisaoInicial = c.divisao;
            if (c.capacidadeInicial <= 0) c.capacidadeInicial = c.capacidade;
            if (c.virtual || (!contratos && c.fatorSalarial > 0)) continue;
            List<Jogador> l = new ArrayList<>();
            for (int id : c.elenco) l.add(m.jogador(id));
            Financas.calibrar(c, l, m.ano);
            if (!contratos) continue;
            for (Jogador j : l) {
                if (j == null || j.contrato == null || j.contrato.teste()) continue;
                Clube paga = m.clube(j.clubeId);
                if (paga == null) continue;
                if (j.contrato.tipo == TipoContrato.PROFISSIONAL || j.contrato.tipo == TipoContrato.EMPRESTIMO) {
                    j.contrato.salario = Financas.salarioNaEscala(j, paga, m.ano);
                    j.contrato.multa = Financas.multaRescisoria(j, paga, m.ano);
                }
            }
        }
    }

    /** Retoma um mundo carregado de um arquivo salvo. */
    public static Simulador retomar(Mundo m, BancoMundo db) {
        for (Torneio t : m.torneios) t.reconstruir();
        for (Torneio t : m.torneiosAnteriores) t.reconstruir();
        for (Torneio t : m.torneiosSub20) t.reconstruir();
        for (Torneio t : m.torneiosSub20Anteriores) t.reconstruir();
        for (LigaKings l : m.kings) {
            if (l.competicoes.isEmpty() && l.torneio != null) l.competicoes.add(l.torneio); // save de antes dos splits
            for (Torneio t : l.competicoes) {
                t.reconstruir();
                // o arquivo grava a competição atual duas vezes: religa ao mesmo objeto da lista
                if (l.torneio != null && t.id.equals(l.torneio.id) && t.ano == l.torneio.ano) l.torneio = t;
            }
            if (l.torneio != null && !l.competicoes.contains(l.torneio)) l.torneio.reconstruir();
        }
        for (Torneio t : m.kingsMundiais) t.reconstruir();
        Kings.presidentesJogadores(m.kings, db); // saves antigos: presidentes que jogam (ex.: Kelvin no G3X)
        Kings.ajustarSaveAntigo(m); // saves antigos: sem jogador 13, força na escala única da Kings e elencos de no máximo 12
        // saves de antes de 15/11/2026: os amadores em teste saem dos elencos (aposentados)
        if (Complementares.limparSaveAntigo(m) > 0)
            m.mensagem("jogador", "Amadores dispensados", "Os amadores em teste deixaram o clube. Agora, quando faltar gente para um jogo, o clube contrata reforços de emergência.");
        m.compactarTextos();
        Simulador s = new Simulador(m, db);
        if (m.treinadores.isEmpty()) s.treinadores.criar(); // save de antes dos técnicos
        s.calibrarFinancas(false);
        return s;
    }

    Pais adicionarPais(String id) {
        Pais p = m.paises.get(id);
        if (p != null) return p;
        p = new Pais();
        p.id = id;
        Db.Pais dp = db.pais(id);
        p.nome = dp != null && dp.nome() != null ? dp.nome() : id;
        p.confederacao = dp != null ? dp.confederacao() : null;
        p.calendario = m.calendarioUnico ? "ano"
                : dp != null && dp.europeu() ? "europeu" : (dp == null && "UEFA".equals(p.confederacao) ? "europeu" : "ano");
        p.rankingFifa = dp != null ? dp.rankingFifa() : null;
        p.cor1 = dp != null ? dp.cor1() : null;
        p.cor2 = dp != null ? dp.cor2() : null;
        m.paises.put(id, p);
        return p;
    }

    void adicionarClube(Db.Clube dc) {
        Clube c = new Clube();
        c.id = dc.id();
        c.nome = dc.nome();
        c.sigla = dc.sigla();
        c.pais = dc.pais();
        Pais p = adicionarPais(dc.pais());
        c.calendario = p.calendario;
        c.confederacao = p.confederacao;
        c.uf = dc.uf();
        c.cidade = dc.cidade();
        c.cor1 = dc.cor1();
        c.cor2 = dc.cor2();
        c.estadio = dc.estadio();
        double rep = dc.reputacao() != null ? dc.reputacao() : 30;
        c.reputacao = rep;
        c.reputacaoInicial = rep;
        c.torcida = dc.torcida() != null ? dc.torcida() : rep;
        c.estrutura = 20 + 0.5 * rep;
        c.capacidade = dc.capacidade() != null && dc.capacidade() > 0 ? dc.capacidade() : (int) (3000 + 600 * rep);
        c.orcamentoRef = dc.orcamentoRef() == null ? 0 : dc.orcamentoRef();
        if (dc.rivais() != null) c.rivais.addAll(dc.rivais());
        c.capacidadeInicial = c.capacidade;
        c.dinheiro = Financas.arred(Financas.receitaReferencia(c, m.ano) * Balanceamento.CAIXA_INICIAL);
        c.tatica.formacao = m.rng.chance(0.5) ? "4-4-2" : (m.rng.chance(0.5) ? "4-3-3" : "4-2-3-1");
        m.clubes.put(c.id, c);
    }

    /** Países com simulação completa: todos (mundo pequeno) ou os mais fortes + o do técnico. */
    void definirDetalhePadrao(String clubeHumano) {
        if (m.clubes.size() <= Balanceamento.LIMITE_CLUBES_TUDO_DETALHADO) {
            for (Pais p : m.paises.values()) p.detalhado = true;
            return;
        }
        Map<String, List<Double>> reps = new HashMap<>();
        for (Clube c : m.clubes.values()) reps.computeIfAbsent(c.pais, k -> new ArrayList<>()).add(c.reputacao);
        List<Pais> l = new ArrayList<>(m.paises.values());
        Map<String, Double> peso = new HashMap<>();
        for (Pais p : l) {
            List<Double> r = new ArrayList<>(reps.getOrDefault(p.id, List.of()));
            r.sort(Comparator.reverseOrder());
            double s = 0;
            for (int i = 0; i < Math.min(16, r.size()); i++) s += r.get(i);
            peso.put(p.id, s);
        }
        l.sort(Comparator.comparingDouble((Pais p) -> -peso.get(p.id)).thenComparing(p -> p.id));
        for (int i = 0; i < l.size(); i++) l.get(i).detalhado = i < Balanceamento.PAISES_DETALHADOS;
        Clube h = m.clube(clubeHumano);
        if (h != null) m.pais(h.pais).detalhado = true;
        Pais bra = m.pais("BRA");
        if (bra != null) bra.detalhado = true;
    }

    /** Liga ou desliga a simulação lance a lance de um país. */
    public void definirDetalhe(String pais, boolean detalhado) {
        Pais p = m.pais(pais);
        if (p != null) p.detalhado = detalhado;
    }

    /**
     * Ligas ago–mai já no meio da temporada em 1º/jan/2026: usa a tabela real se o banco tiver
     * ({@code tabelas_iniciais}); senão simula em silêncio (caminho rápido) o que já foi jogado.
     */
    void meioDeTemporada(List<Torneio> lista) {
        int inicio = m.semanaAbsoluta();
        silencioso = true;
        try {
            for (Torneio t : lista) {
                List<Db.TabelaInicial> tab = db.tabelaInicial(t.id);
                if (!tab.isEmpty() && t.fases.size() == 1 && "pontos_corridos".equals(t.fases.get(0).tipo)) {
                    Map<String, int[]> base = new HashMap<>();
                    int soma = 0;
                    for (Db.TabelaInicial x : tab) {
                        if (!t.participantes.contains(x.clubeId())) continue;
                        base.put(x.clubeId(), new int[]{x.j(), x.v(), x.e(), x.d(), x.gp(), x.gc(), x.pts()});
                        soma += x.j();
                    }
                    if (base.size() == t.participantes.size()) {
                        int jogos = Math.round((float) soma / base.size());
                        int porData = t.participantes.size() % 2 == 0 ? 1 : 0;
                        int datas = porData == 1 ? jogos : jogos * t.participantes.size() / (t.participantes.size() - 1);
                        t.aplicarTabelaInicial(base, datas);
                        continue;
                    }
                    m.aviso("Tabela inicial incompleta para " + t.id + ": primeiro turno simulado.");
                }
                t.jogarAte(inicio - 1, this);
                if (t.encerrado && !t.processado) encerrarTorneio(t);
            }
        } finally {
            silencioso = false;
        }
        for (Jogador j : m.jogadores) {
            j.jogosTemp = j.minutosTemp = j.minutosMarco = 0;
            j.golsTemp = j.assistTemp = 0;
            j.somaNotasTemp = 0;
        }
    }

    // ======================================================================= semana
    /** Avança uma semana. Retorna as partidas do humano (clube ou seleção) jogadas nela. */
    public List<Partida> avancarSemana() {
        narradasDaSemana.clear();
        escalacoes.clear();
        vivos.clear();
        paralelos.clear();
        cacheRapido.clear();
        preJogadas.clear();
        gruposNaSemana = 0;
        if (!m.calendarioUnico && m.semana == Balanceamento.SEMANA_VIRADA_EUROPEIA) viradaEuropeia();
        // aviso de aposentadoria 6 meses antes do fim da temporada (europeu termina na virada de julho; "ano" em dezembro)
        if (!m.calendarioUnico && m.semana == Math.max(1, Balanceamento.SEMANA_VIRADA_EUROPEIA - 26)) anunciarAposentadorias("europeu");
        if (m.semana == 26) anunciarAposentadorias("ano");
        int sa = m.semanaAbsoluta();
        List<Partida> doHumano = new ArrayList<>();
        boolean torneioSelecoes = false;
        for (Torneio t : m.torneios) {
            if (!"selecoes".equals(t.ambito) || t.encerrado || sa < t.semanaInicio) continue;
            if (t.datasFifa) continue; // eliminatórias e Liga das Nações: as datas FIFA continuam (sem amistosos)
            torneioSelecoes = true;
            if (!t.convocacaoFeita) {
                t.convocacaoFeita = true;
                selecoes.iniciarTorneio(t);
            }
        }
        boolean datasOficiais = false;
        for (Torneio t : m.torneios) if (t.datasFifa && !t.encerrado && t.semanasJogo.contains(sa)) datasOficiais = true;
        if (!torneioSelecoes) for (int f : Balanceamento.JANELAS_FIFA) {
            if (f != m.semana) continue;
            if (datasOficiais) selecoes.convocarTodas(); // jogos oficiais na data FIFA: só atualiza as convocações
            else selecoes.dataFifa(m.rng);
        }
        br.futebas.competicao.CompeticoesFicticias.jogosDeAgosto(m, db);
        negociacoes.semana();
        mercado.semana();
        m.paraCadaTecnico(negociacoes::ofertasDeEmprestimo);
        String humanoId = m.tecnico == null ? null : m.tecnico.clubeId;
        String selHumana = m.tecnico == null || m.tecnico.selecaoPais == null ? null : Selecao.id(m.tecnico.selecaoPais);
        // regra do jogo: nenhum time com dois jogos no mesmo dia (o de menor prioridade muda de dia na semana)
        ajustarAgenda();
        // a semana em ordem de dia entre as competições (antes: na ordem da lista, e a liga do técnico
        // podia ser jogada antes da Champions anunciada como o próximo jogo)
        List<Torneio> daSemana = new ArrayList<>(m.torneios);
        int[] antes = new int[daSemana.size()];
        for (int i = 0; i < antes.length; i++) antes[i] = daSemana.get(i).proximaData;
        String chaveHumano = m.multi() ? null : humanoId != null ? humanoId : selHumana;
        Set<String> humanos = m.multi() ? clubesHumanos() : null;
        java.util.IdentityHashMap<Torneio, IndiceDatas> indice = new java.util.IdentityHashMap<>();
        while (true) {
            Torneio prox = null;
            long melhor = Long.MAX_VALUE;
            for (Torneio t : daSemana) {
                long d = diaDaProximaData(t, sa, chaveHumano, indice);
                if (d < melhor) {
                    melhor = d;
                    prox = t;
                }
            }
            if (prox == null) break;
            prox.jogarProximaData(this);
        }
        for (int i = 0; i < daSemana.size(); i++) {
            Torneio t = daSemana.get(i);
            t.jogarAte(sa, this); // fecha o que faltar (finalização)
            if (humanos != null) {
                for (Partida p : t.partidas) {
                    if (!p.jogada || p.sa != sa || p.data < antes[i]) continue;
                    if (humanos.contains(p.mandante) || humanos.contains(p.visitante)) doHumano.add(p);
                }
            } else if (humanoId != null || selHumana != null) {
                for (Partida p : t.partidas) {
                    if (!p.jogada || p.sa != sa || p.data < antes[i]) continue;
                    if ((humanoId != null && p.envolve(humanoId)) || (selHumana != null && p.envolve(selHumana))) doHumano.add(p);
                }
            }
            if (t.encerrado && !t.processado) encerrarTorneio(t);
        }
        doHumano.sort(java.util.Comparator.comparingLong((Partida p) -> p.dia).thenComparingInt(p -> p.data));
        sub20.semana(sa);
        kings.jogosDoHumanoNaSemana.clear();
        kings.narradasNaSemana.clear();
        kings.semana(sa);
        // jogos do time da Kings do técnico: entram como jogos do usuário (tela de fim de jogo igual à do futebol)
        doHumano.addAll(kings.jogosDoHumanoNaSemana);
        narradasDaSemana.addAll(kings.narradasNaSemana);
        semanal(m.semana);
        noticiario.fecharSemana(sa);
        m.semana++;
        if (m.semana > 52) {
            virarAno();
            ajustarAgenda(); // calendário novo: já sem dois jogos do mesmo time no mesmo dia
        }
        return doHumano;
    }

    /** Partidas de um torneio por data (para {@link #diaDaProximaData}); refeito quando a lista muda. */
    private static final class IndiceDatas {
        List<Partida> lista;
        int tamanho = -1;
        Partida ultima;
        final Map<Integer, List<Partida>> porData = new HashMap<>();
    }

    /** {@code -Dfutebas.verificarIndice=true}: confere cada resposta com {@code Torneio.diaDaProximaData}. */
    static volatile boolean verificarIndice = Boolean.getBoolean("futebas.verificarIndice");

    /**
     * O mesmo que {@code t.diaDaProximaData(semana, timeChave)}, sem varrer a temporada inteira do
     * torneio: a semana pergunta isso a todos os torneios a cada data jogada (com o banco completo,
     * ~20% do tempo da semana). Só a lista de partidas da data é indexada; dia, jogada e times são
     * lidos ao vivo, e o índice é refeito se o torneio ganhar partidas.
     */
    private static long diaDaProximaData(Torneio t, int semana, String timeChave, Map<Torneio, IndiceDatas> indice) {
        if (t.encerrado || t.proximaData >= t.totalDatas || t.semanaDaData(t.proximaData) > semana) return Long.MAX_VALUE;
        IndiceDatas ix = indice.computeIfAbsent(t, k -> new IndiceDatas());
        List<Partida> ps = t.partidas;
        if (ix.lista != ps || ix.tamanho != ps.size() || (!ps.isEmpty() && ix.ultima != ps.get(ps.size() - 1))) {
            ix.porData.clear();
            for (Partida p : ps) ix.porData.computeIfAbsent(p.data, k -> new ArrayList<>()).add(p);
            ix.lista = ps;
            ix.tamanho = ps.size();
            ix.ultima = ps.isEmpty() ? null : ps.get(ps.size() - 1);
        }
        long min = Long.MAX_VALUE, doTime = Long.MAX_VALUE;
        List<Partida> l = ix.porData.get(t.proximaData);
        if (l != null) for (Partida p : l) {
            if (p.data != t.proximaData || p.jogada) continue;
            min = Math.min(min, p.dia);
            if (timeChave != null && p.envolve(timeChave)) doTime = Math.min(doTime, p.dia);
        }
        long r = doTime != Long.MAX_VALUE ? doTime : min != Long.MAX_VALUE ? min : t.diaDaProximaData(semana, timeChave);
        if (verificarIndice && r != t.diaDaProximaData(semana, timeChave))
            throw new IllegalStateException("índice de datas divergiu em " + t.id + " (data " + t.proximaData + ")");
        return r;
    }

    /** Avança até o ano mudar (fim da temporada jan–dez). */
    public void avancarTemporada() {
        int ano = m.ano;
        while (m.ano == ano) avancarSemana();
    }

    /** Outra competição do mesmo ano (Sul-Americana busca na Libertadores os eliminados e os terceiros). */
    @Override
    public Torneio outroTorneio(Torneio atual, String competicaoId) {
        for (List<Torneio> l : List.of(m.torneios, m.torneiosAnteriores))
            for (Torneio t : l) if (t.id.equals(competicaoId) && t.ano == atual.ano) return t;
        return null;
    }

    @Override
    public String paisDe(String clubeId) {
        Clube c = m.clube(clubeId);
        return c == null ? null : c.pais;
    }

    @Override
    public void novasPartidas(Torneio t) {
        java.util.Set<String> times = new java.util.HashSet<>();
        for (Partida p : t.partidas) if (!p.jogada && p.mandante != null && p.visitante != null) {
            times.add(p.mandante);
            times.add(p.visitante);
        }
        List<Torneio> ts = new ArrayList<>(m.torneios);
        if (m.kings != null) for (br.futebas.kings.LigaKings l : m.kings) ts.addAll(l.competicoes);
        br.futebas.competicao.Agenda.resolver(ts, times);
    }

    /** Resolve dois jogos do mesmo time no mesmo dia (clubes, seleções e Kings). */
    public int ajustarAgenda() {
        List<Torneio> ts = new ArrayList<>(m.torneios);
        if (m.kings != null) for (br.futebas.kings.LigaKings l : m.kings) ts.addAll(l.competicoes);
        return br.futebas.competicao.Agenda.resolver(ts);
    }

    // ======================================================================= partidas
    /** Cache semanal da força de um clube para a partida rápida. */
    static final class Rapido {
        double forca;
        int[] ids = new int[0];
        double[] pesos = new double[0];
    }

    Rapido rapido(Clube c) {
        Rapido r = cacheRapido.get(c.id);
        if (r != null) return r;
        r = new Rapido();
        if (c.virtual) {
            r.forca = 45 + 0.42 * c.reputacao;
        } else {
            List<Jogador> disp = Escalador.disponiveis(m, c);
            disp.sort(Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id));
            int n = Math.min(14, disp.size());
            r.ids = new int[n];
            r.pesos = new double[n];
            double s = 0;
            int goleiro = 0, linha = 0;
            for (int i = 0; i < n; i++) {
                Jogador j = disp.get(i);
                r.ids[i] = j.id;
                r.pesos[i] = switch (j.posicao) {
                    case CA -> 5;
                    case PE, PD -> 3;
                    case MEI -> 2.5;
                    case MC -> 1.2;
                    case GOL -> 0;
                    default -> 0.4;
                } * (i < 11 ? 1 : 0.25);
                if (i < 11) {
                    if (j.posicao == Posicao.GOL) goleiro++;
                    else linha++;
                    s += j.forca;
                }
            }
            int titulares = Math.min(11, n);
            r.forca = titulares == 0 ? c.nivel() - 10 : (s + (11 - titulares) * (c.nivel() - 12)) / 11.0;
            if (goleiro == 0 && titulares > 0) r.forca -= 4;
        }
        cacheRapido.put(c.id, r);
        return r;
    }

    private boolean detalhado(Clube c) {
        Pais p = m.pais(c.pais);
        return p == null || p.detalhado;
    }

    /** Jogos já jogados da rodada em competições do técnico (chave: torneio#data), limpos a cada semana. */
    private final Map<String, List<JogoParalelo>> paralelos = new HashMap<>();
    /** Os outros jogos entregues à tela no último jogo ao vivo do técnico. */
    public List<JogoParalelo> ultimosParalelos = new ArrayList<>();

    private String idHumano() {
        return m.tecnico == null ? null : m.tecnico.clubeId;
    }

    /** Multi-treinador: os clubes de todos os técnicos humanos. */
    private Set<String> clubesHumanos() {
        Set<String> s = new HashSet<>();
        for (Tecnico t : m.todosTecnicos()) if (t.clubeId != null) s.add(t.clubeId);
        return s;
    }

    /** Multi-treinador: o clube é de um técnico humano que comanda ESTE jogo (não entregue ao auxiliar)? */
    private boolean ladoHumanoAtivo(Clube c, Torneio t, Partida p) {
        if (c == null || !c.humano) return false;
        Tecnico tec = m.tecnicoDoClube(c.id);
        return tec != null && !tec.delegado(t.id, p.id, m.ano);
    }

    private String selecaoHumana() {
        return m.tecnico == null || m.tecnico.selecaoPais == null ? null : Selecao.id(m.tecnico.selecaoPais);
    }

    private boolean envolveHumano(Partida p) {
        if (m.multi()) {
            Set<String> hs = clubesHumanos();
            return hs.contains(p.mandante) || hs.contains(p.visitante);
        }
        String h = idHumano(), s = selecaoHumana();
        return (h != null && p.envolve(h)) || (s != null && p.envolve(s));
    }

    private boolean torneioDoHumano(Torneio t) {
        if (m.multi()) {
            for (String id : clubesHumanos()) if (t.participantes.contains(id)) return true;
            return false;
        }
        String h = idHumano(), s = selecaoHumana();
        return (h != null && t.participantes.contains(h)) || (s != null && t.participantes.contains(s));
    }

    @Override
    public boolean adiar(Torneio t, Partida p) {
        return !silencioso && envolveHumano(p) && !delegado(t, p);
    }

    /** O técnico entregou esta partida (ou a competição inteira) ao auxiliar. */
    boolean delegado(Torneio t, Partida p) {
        if (m.multi()) {
            // multi: entregue ao auxiliar só se nenhum lado humano comanda o jogo
            return !ladoHumanoAtivo(m.clube(p.mandante), t, p) && !ladoHumanoAtivo(m.clube(p.visitante), t, p);
        }
        return m.tecnico != null && m.tecnico.delegado(t.id, p.id, m.ano);
    }

    @Override
    public ResultadoPartida jogar(Torneio t, Partida p, boolean prorrogacao) {
        boolean doHumano = !silencioso && torneioDoHumano(t);
        String chave = t.id + "#" + t.ano + "#" + p.data;
        if (doHumano && envolveHumano(p)) {
            ultimosParalelos = new ArrayList<>(paralelos.getOrDefault(chave, List.of()));
            if (aoVivo != null) aoVivo.outrosJogos(java.util.Collections.unmodifiableList(ultimosParalelos));
        }
        ResultadoPartida r = jogarPartida(t, p, prorrogacao);
        if (Sonda.Atual.s != null) Sonda.Atual.s.partida(t, p, r);
        if (doHumano && !envolveHumano(p)) paralelos.computeIfAbsent(chave, k -> new ArrayList<>()).add(paralelo(t, r));
        return r;
    }

    /** Resumo de um jogo para a tela da rodada (jogos rápidos: só os gols). */
    private JogoParalelo paralelo(Torneio t, ResultadoPartida r) {
        List<EventoJogo> ev = r.eventos;
        if (ev.isEmpty() && !r.gols.isEmpty()) {
            ev = new ArrayList<>();
            for (int[] g : r.gols) {
                Jogador j = g[1] >= 0 ? m.jogador(g[1]) : null;
                ev.add(new EventoJogo(g[2], 0, g[4] == 1 ? "gol_contra" : "gol", g[0], g[1], j == null ? null : j.nomeExibicao()));
            }
            ev.sort(java.util.Comparator.comparingInt(EventoJogo::minuto));
        }
        return new JogoParalelo(t.id, t.nome, r.mandanteId, r.mandante, r.visitanteId, r.visitante, List.copyOf(ev), r.golsMandante, r.golsVisitante);
    }

    private ResultadoPartida jogarPartida(Torneio t, Partida p, boolean prorrogacao) {
        if ("selecoes".equals(t.ambito)) return jogarSelecoes(t, p, prorrogacao);
        Clube cm = m.clube(p.mandante), cv = m.clube(p.visitante);
        recuperarElenco(cm, p.dia);
        recuperarElenco(cv, p.dia);
        boolean humano = m.tecnico != null && (cm.humano || cv.humano);
        if (silencioso || (!humano && !detalhado(cm) && !detalhado(cv))) return jogarRapido(t, p, prorrogacao, cm, cv);
        if (m.multi() && humano) return jogarPartidaMulti(t, p, prorrogacao, cm, cv);
        partidasDetalhadas++;
        if (!cm.virtual) Complementares.garantir(this, cm);
        if (!cv.virtual) Complementares.garantir(this, cv);
        boolean aux = humano && delegado(t, p);
        // 2º jogo do técnico na semana: a tela volta à central (pré-jogo e escalação) ANTES de montar o time
        if (humano && !aux && aoVivo != null && !silencioso) aoVivo.antesDoProximoJogo(cm.id, cm.nome, cv.id, cv.nome);
        TimePartida tm = cm.virtual ? Escalador.virtual(cm, 0, rapido(cm).forca, m.rng)
                : aux && cm.humano ? Escalador.montarAutomatico(m, cm, t.regras.substituicoes, t.regras.reservas(t)) : Escalador.montar(m, cm, t.regras.substituicoes, t.regras.reservas(t));
        TimePartida tv = cv.virtual ? Escalador.virtual(cv, 1, rapido(cv).forca, m.rng)
                : aux && cv.humano ? Escalador.montarAutomatico(m, cv, t.regras.substituicoes, t.regras.reservas(t)) : Escalador.montar(m, cv, t.regras.substituicoes, t.regras.reservas(t));
        if (t.despedidaJogadorId != null) {
            Jogador hom = m.ativo(t.despedidaJogadorId);
            if (hom != null && cm.id.equals(hom.clubeId) && !cm.virtual) Escalador.forcarTitular(tm, hom);
            if (hom != null && cv.id.equals(hom.clubeId) && !cv.virtual) Escalador.forcarTitular(tv, hom);
        }
        double fm0 = forca(tm), fv0 = forca(tv);
        if (!cm.humano || aux) {
            tm.estilo = estiloIA(cm, fm0 + 3, fv0, true);
            taticaIA(tm, tv, fm0 + 3, fv0, m.rng);
        }
        if (!cv.humano || aux) {
            tv.estilo = estiloIA(cv, fv0 - 3, fm0, false);
            taticaIA(tv, tm, fv0 - 3, fm0, m.rng);
        }
        OpcoesPartida op = opcoes(t, prorrogacao, humano);
        op.formaMandante = cm.formaTemporada;
        op.formaVisitante = cv.formaTemporada;
        op.ladoHumano = !humano || aux ? -1 : cm.humano ? 0 : 1;
        if (aux) op.aoVivo = null; // o auxiliar joga sem a tela ao vivo
        ResultadoPartida r = MotorPartida.simular(tm, tv, op, m.rng.derivar(p.id * 31L + t.semente));
        escalacoes.put(t.id + "#" + t.ano + "#" + p.id, new TimePartida[]{tm, tv});
        if (op.aoVivo != null && op.ladoHumano >= 0) vivos.put(t.id + "#" + t.ano + "#" + p.id, op);
        aplicarResultado(t, p, r, cm, cv, fm0, fv0);
        if (t.despedidaJogadorId != null) despedida(t, r, cm, cv);
        if (humano) {
            ultimaNarrada = r;
            narradasDaSemana.add(r);
        }
        return r;
    }


    // ======================================================================= multi-treinador
    /** Acompanhamento da tela dividida (multi-treinador); null = sem tela (testes, auxiliar). */
    public transient br.futebas.partida.AoVivoMulti aoVivoMulti;
    /** Jogos de técnicos humanos já simulados no grupo (juntos, ao vivo), à espera do torneio chegar neles. */
    private final Map<Partida, Preparada> preJogadas = new HashMap<>();
    /** Grupos de jogos ao vivo já jogados nesta semana. */
    private int gruposNaSemana;
    /** Partidas jogadas juntas no último grupo (testes e tela). */
    public int ultimoGrupo;

    /** Um jogo montado (times, táticas, opções e sorteio) pronto para simular. */
    private static final class Preparada {
        Torneio t;
        Partida p;
        Clube cm, cv;
        TimePartida tm, tv;
        double fm0, fv0;
        OpcoesPartida op;
        Rng rng;
        boolean vivo;
        volatile ResultadoPartida r;
        volatile RuntimeException erro;
    }

    /**
     * Jogo com técnico humano no modo multi-treinador: os jogos ao vivo de TODOS os técnicos humanos
     * nesta semana rodam juntos (um grupo), na mesma tela; o resultado de cada um entra quando o
     * torneio dele chega no jogo (a ordem do calendário fica a mesma).
     */
    private ResultadoPartida jogarPartidaMulti(Torneio t, Partida p, boolean prorrogacao, Clube cm, Clube cv) {
        Preparada pr = preJogadas.remove(p);
        if (pr == null) {
            boolean vivo = aoVivoMulti != null && (ladoHumanoAtivo(cm, t, p) || ladoHumanoAtivo(cv, t, p));
            List<Object[]> outros = vivo ? outrosDoGrupo(p) : List.of();
            if (vivo && gruposNaSemana++ > 0) {
                // 2º jogo de técnico na semana: antes de montar os times, cada técnico desse grupo volta à central
                List<String> clubes = new ArrayList<>();
                for (Clube c : List.of(cm, cv)) if (ladoHumanoAtivo(c, t, p)) clubes.add(c.id);
                for (Object[] o : outros) {
                    Torneio tq = (Torneio) o[0];
                    Partida q = (Partida) o[1];
                    for (String id : List.of(q.mandante, q.visitante)) if (ladoHumanoAtivo(m.clube(id), tq, q)) clubes.add(id);
                }
                aoVivoMulti.antesDoGrupo(clubes);
            }
            pr = preparar(t, p, prorrogacao, cm, cv);
            List<Preparada> grupo = new ArrayList<>(List.of(pr));
            if (vivo) {
                for (Object[] o : outros) {
                    Torneio tq = (Torneio) o[0];
                    Partida q = (Partida) o[1];
                    grupo.add(preparar(tq, q, false, m.clube(q.mandante), m.clube(q.visitante)));
                }
            }
            simularGrupo(grupo);
            for (Preparada x : grupo) if (x != pr) preJogadas.put(x.p, x);
        }
        ResultadoPartida r = pr.r;
        escalacoes.put(t.id + "#" + t.ano + "#" + p.id, new TimePartida[]{pr.tm, pr.tv});
        if (pr.op.aoVivo != null && pr.op.ladoHumano >= 0) vivos.put(t.id + "#" + t.ano + "#" + p.id, pr.op);
        aplicarResultado(t, p, r, cm, cv, pr.fm0, pr.fv0);
        if (t.despedidaJogadorId != null) despedida(t, r, cm, cv);
        ultimaNarrada = r;
        narradasDaSemana.add(r);
        return r;
    }

    /** Monta um jogo com lado humano (multi): escalação do técnico (ou do auxiliar), táticas e opções. */
    private Preparada preparar(Torneio t, Partida p, boolean prorrogacao, Clube cm, Clube cv) {
        partidasDetalhadas++;
        if (!cm.virtual) Complementares.garantir(this, cm);
        if (!cv.virtual) Complementares.garantir(this, cv);
        boolean hm = ladoHumanoAtivo(cm, t, p), hv = ladoHumanoAtivo(cv, t, p);
        Preparada pr = new Preparada();
        pr.t = t;
        pr.p = p;
        pr.cm = cm;
        pr.cv = cv;
        pr.tm = cm.virtual ? Escalador.virtual(cm, 0, rapido(cm).forca, m.rng)
                : cm.humano && !hm ? Escalador.montarAutomatico(m, cm, t.regras.substituicoes, t.regras.reservas(t)) : Escalador.montar(m, cm, t.regras.substituicoes, t.regras.reservas(t));
        pr.tv = cv.virtual ? Escalador.virtual(cv, 1, rapido(cv).forca, m.rng)
                : cv.humano && !hv ? Escalador.montarAutomatico(m, cv, t.regras.substituicoes, t.regras.reservas(t)) : Escalador.montar(m, cv, t.regras.substituicoes, t.regras.reservas(t));
        if (t.despedidaJogadorId != null) {
            Jogador hom = m.ativo(t.despedidaJogadorId);
            if (hom != null && cm.id.equals(hom.clubeId) && !cm.virtual) Escalador.forcarTitular(pr.tm, hom);
            if (hom != null && cv.id.equals(hom.clubeId) && !cv.virtual) Escalador.forcarTitular(pr.tv, hom);
        }
        pr.fm0 = forca(pr.tm);
        pr.fv0 = forca(pr.tv);
        if (!hm) {
            pr.tm.estilo = estiloIA(cm, pr.fm0 + 3, pr.fv0, true);
            taticaIA(pr.tm, pr.tv, pr.fm0 + 3, pr.fv0, m.rng);
        }
        if (!hv) {
            pr.tv.estilo = estiloIA(cv, pr.fv0 - 3, pr.fm0, false);
            taticaIA(pr.tv, pr.tm, pr.fv0 - 3, pr.fm0, m.rng);
        }
        pr.op = opcoes(t, prorrogacao, true);
        pr.op.formaMandante = cm.formaTemporada;
        pr.op.formaVisitante = cv.formaTemporada;
        pr.op.aoVivo = null;
        pr.vivo = hm || hv;
        pr.op.ladoHumano = hm ? 0 : hv ? 1 : -1;
        pr.op.ambosHumanos = hm && hv;
        if (pr.vivo && aoVivoMulti != null) {
            String chave = t.id + "#" + t.ano + "#" + p.data;
            br.futebas.partida.AoVivo av = aoVivoMulti.partida(cm.id, cv.id, pr.op);
            if (av != null) av.outrosJogos(java.util.Collections.unmodifiableList(new ArrayList<>(paralelos.getOrDefault(chave, List.of()))));
            pr.op.aoVivo = av;
        }
        pr.rng = m.rng.derivar(p.id * 31L + t.semente);
        return pr;
    }

    /**
     * Os outros jogos que entram no grupo ao vivo: o próximo jogo desta semana de cada técnico humano
     * (comandado por ele, já marcado), sem repetir clube e sem pular um jogo anterior dos clubes.
     */
    private List<Object[]> outrosDoGrupo(Partida primeiro) {
        int sa = m.semanaAbsoluta();
        List<Object[]> cands = new ArrayList<>();
        for (Torneio t : m.torneios) {
            if ("selecoes".equals(t.ambito) || t.encerrado) continue;
            for (Partida q : t.partidas) {
                if (q.jogada || q.sa != sa || q == primeiro || q.mandante == null || q.visitante == null || preJogadas.containsKey(q)) continue;
                Clube a = m.clube(q.mandante), b = m.clube(q.visitante);
                if (a == null || b == null) continue;
                if (!ladoHumanoAtivo(a, t, q) && !ladoHumanoAtivo(b, t, q)) continue;
                cands.add(new Object[]{t, q});
            }
        }
        cands.sort(Comparator.comparingLong((Object[] o) -> ((Partida) o[1]).dia).thenComparingInt(o -> ((Partida) o[1]).data));
        Set<String> usados = new HashSet<>(List.of(primeiro.mandante, primeiro.visitante));
        List<Object[]> l = new ArrayList<>();
        for (Object[] o : cands) {
            Partida q = (Partida) o[1];
            boolean fora = usados.contains(q.mandante) || usados.contains(q.visitante) || jogoAntes(q, q.mandante) || jogoAntes(q, q.visitante);
            // um clube que fica de fora (já tem jogo no grupo ou um jogo antes deste) não entra mais neste grupo
            usados.add(q.mandante);
            usados.add(q.visitante);
            if (!fora) l.add(o);
        }
        return l;
    }

    /** O clube tem outro jogo não jogado nesta semana antes de {@code q} (dia antes, ou mesma competição e data antes)? */
    private boolean jogoAntes(Partida q, String clubeId) {
        int sa = m.semanaAbsoluta();
        for (Torneio t : m.torneios) for (Partida x : t.partidas)
            if (x != q && !x.jogada && x.sa == sa && x.envolve(clubeId) && (x.dia < q.dia || x.dia == q.dia && t.partidas.contains(q) && x.data < q.data)) return true;
        return false;
    }

    /** Simula o grupo: juntos (uma thread por jogo) quando há tela; senão, um depois do outro. */
    private void simularGrupo(List<Preparada> grupo) {
        boolean vivo = aoVivoMulti != null && grupo.stream().anyMatch(x -> x.op.aoVivo != null);
        ultimoGrupo = grupo.size();
        if (!vivo) {
            for (Preparada x : grupo) x.r = MotorPartida.simular(x.tm, x.tv, x.op, x.rng);
            return;
        }
        aoVivoMulti.iniciarGrupo();
        List<Thread> ths = new ArrayList<>();
        try {
            for (Preparada x : grupo) {
                Thread th = new Thread(() -> {
                    try {
                        x.r = MotorPartida.simular(x.tm, x.tv, x.op, x.rng);
                    } catch (RuntimeException e) {
                        x.erro = e;
                        // a tela não pode ficar esperando um jogo que caiu
                        try {
                            if (x.op.aoVivo != null) x.op.aoVivo.fim(null);
                        } catch (RuntimeException ignorado) {
                            // nada a fazer
                        }
                    }
                }, "partida-multi-" + x.p.id);
                th.setDaemon(true);
                ths.add(th);
                th.start();
            }
            for (Thread th : ths) {
                boolean interrompido = false;
                while (th.isAlive()) {
                    try {
                        th.join();
                    } catch (InterruptedException e) {
                        interrompido = true;
                    }
                }
                if (interrompido) Thread.currentThread().interrupt();
            }
        } finally {
            aoVivoMulti.fimDoGrupo();
        }
        for (Preparada x : grupo) if (x.erro != null) throw x.erro;
    }

    /** Competição continental ou mundial de clubes (Champions, Libertadores, Mundial...): fator de elite maior. */
    static boolean continentalOuMundial(Torneio t) {
        return "continental".equals(t.ambito) || "mundial".equals(t.ambito) || "intercontinental".equals(t.ambito);
    }

    private OpcoesPartida opcoes(Torneio t, boolean prorrogacao, boolean humano) {
        OpcoesPartida op = new OpcoesPartida();
        op.narrar = humano;
        op.ano = m.ano;
        if (continentalOuMundial(t)) op.eliteK = br.futebas.partida.MotorPartida.ELITE_K_CONTINENTAL;
        op.substituicoes = t.regras.substituicoes;
        op.var = t.regras.var;
        op.golDeOuro = t.regras.golDeOuro;
        op.prorrogacao = prorrogacao;
        op.mando = !"selecoes".equals(t.ambito);
        if (humano) op.aoVivo = aoVivo;
        return op;
    }

    /** Partida rápida: placar Poisson pela força do time; gols distribuídos entre os titulares. */
    private ResultadoPartida jogarRapido(Torneio t, Partida p, boolean prorrogacao, Clube cm, Clube cv) {
        partidasRapidas++;
        Rapido a = rapido(cm), b = rapido(cv);
        Rng rng = m.rng;
        // elite (rodada 2 do balanço): no alto da escala a diferença de força pesa mais, como no lance a lance
        double meioF = (a.forca + b.forca) / 2, el = br.futebas.partida.MotorPartida.fatorElite(meioF, continentalOuMundial(t) ? br.futebas.partida.MotorPartida.ELITE_K_CONTINENTAL : br.futebas.partida.MotorPartida.ELITE_K);
        // forma da temporada (16/11/2026) somada à força
        double fa = a.forca + cm.formaTemporada, fb = b.forca + cv.formaTemporada;
        int[] g = br.futebas.selecao.Selecoes.placarRapido(meioF + el * (fa - meioF), meioF + el * (fb - meioF), true, rng);
        ResultadoPartida r = new ResultadoPartida();
        r.mandante = cm.nome;
        r.visitante = cv.nome;
        r.mandanteId = cm.id;
        r.visitanteId = cv.id;
        r.golsMandante = g[0];
        r.golsVisitante = g[1];
        if (prorrogacao && g[0] == g[1] && rng.chance(0.35)) {
            r.prorrogacao = true;
            if (rng.chance(1 / (1 + Math.exp(-0.08 * (a.forca - b.forca))))) r.golsMandante++;
            else r.golsVisitante++;
        } else if (prorrogacao && g[0] == g[1]) {
            r.prorrogacao = true;
        }
        marcadores(r, 0, a, r.golsMandante, rng);
        marcadores(r, 1, b, r.golsVisitante, rng);
        for (Rapido x : new Rapido[]{a, b}) {
            for (int i = 0; i < Math.min(11, x.ids.length); i++) {
                r.minutos.put(x.ids[i], 90);
                r.notas.put(x.ids[i], 6.0 + rng.gauss(0, 0.5));
            }
        }
        if (!silencioso) aplicarResultado(t, p, r, cm, cv, a.forca, b.forca);
        return r;
    }

    private static void marcadores(ResultadoPartida r, int lado, Rapido x, int gols, Rng rng) {
        for (int k = 0; k < gols; k++) {
            double tot = 0;
            for (double w : x.pesos) tot += w;
            int autor = -1;
            if (tot > 0) {
                double u = rng.real() * tot;
                for (int i = 0; i < x.pesos.length; i++) {
                    u -= x.pesos[i];
                    if (u <= 0) {
                        autor = x.ids[i];
                        break;
                    }
                }
                if (autor < 0) autor = x.ids[x.ids.length - 1];
            }
            r.gols.add(new int[]{lado, autor, rng.entre(1, 90), 0, 0});
        }
    }

    private ResultadoPartida jogarSelecoes(Torneio t, Partida p, boolean prorrogacao) {
        Selecao a = m.selecao(p.mandante), b = m.selecao(p.visitante);
        Selecao h = selecoes.humana();
        boolean humano = h != null && (h == a || h == b);
        // só o jogo da seleção do técnico é lance a lance: entre seleções da IA o modelo rápido
        // respeita melhor a diferença de força (o motor de clubes comprime as diferenças)
        ResultadoPartida r;
        if (humano && !silencioso) {
            partidasDetalhadas++;
            if (aoVivo != null && !delegado(t, p)) aoVivo.antesDoProximoJogo(a.id, a.nome, b.id, b.nome);
            TimePartida ta = selecoes.time(a, 0, t.regras.substituicoes, m.rng);
            TimePartida tb = selecoes.time(b, 1, t.regras.substituicoes, m.rng);
            double fa = forca(ta), fb = forca(tb);
            if (h != a) ta.estilo = estiloIA(null, fa, fb, true);
            if (h != b) tb.estilo = estiloIA(null, fb, fa, false);
            OpcoesPartida op = opcoes(t, prorrogacao, humano);
            op.ladoHumano = h == a ? 0 : 1;
            r = MotorPartida.simular(ta, tb, op, m.rng.derivar(p.id * 37L + t.semente));
            escalacoes.put(t.id + "#" + t.ano + "#" + p.id, new TimePartida[]{ta, tb});
            if (op.aoVivo != null && !delegado(t, p)) vivos.put(t.id + "#" + t.ano + "#" + p.id, op);
            for (Map.Entry<Integer, Integer> e : r.minutos.entrySet()) {
                Jogador j = m.jogador(e.getKey());
                if (j != null) j.jogosSelecao++;
            }
            for (int[] g : r.gols) {
                Jogador j = g[4] == 0 && g[1] >= 0 ? m.jogador(g[1]) : null;
                if (j != null) j.golsSelecao++;
            }
        } else {
            partidasRapidas++;
            double fa = selecoes.forcaRapida(a), fb = selecoes.forcaRapida(b);
            int[] g = Selecoes.placarSelecoes(fa, fb, false, m.rng);
            r = new ResultadoPartida();
            r.mandante = a.nome;
            r.visitante = b.nome;
            r.mandanteId = a.id;
            r.visitanteId = b.id;
            r.golsMandante = g[0];
            r.golsVisitante = g[1];
            if (prorrogacao && g[0] == g[1]) {
                r.prorrogacao = true;
                if (m.rng.chance(0.35)) {
                    if (m.rng.chance(1 / (1 + Math.exp(-0.15 * (fa - fb))))) r.golsMandante++;
                    else r.golsVisitante++;
                }
            }
        }
        Selecoes.atualizarElo(a, b, r.golsMandante, r.golsVisitante, true);
        Historia.EstatAno est = m.historia.estat(m.ano);
        est.jogosSelecoes++;
        est.golsSelecoes += r.golsMandante + r.golsVisitante;
        if (humano) {
            Tecnico tec = m.tecnico;
            int pro = h == a ? r.golsMandante : r.golsVisitante, contra = h == a ? r.golsVisitante : r.golsMandante;
            if (pro > contra) tec.pontosRanking += 3;
            else if (pro == contra) tec.pontosRanking += 1;
            m.mensagem("resultado", t.nome, r.placar());
            ultimaNarrada = r;
            narradasDaSemana.add(r);
        }
        return r;
    }

    @Override
    public int[] penaltis(Torneio t, Partida p) {
        TimePartida[] par = escalacoes.get(t.id + "#" + t.ano + "#" + p.id);
        if (par == null) {
            // partida rápida: cobranças com 75% de acerto (alternadas até desempatar)
            int a = 0, b = 0;
            for (int i = 0; i < 5; i++) {
                if (m.rng.chance(0.75)) a++;
                if (m.rng.chance(0.75)) b++;
            }
            int k = 0;
            while (a == b && k++ < 20) {
                if (m.rng.chance(0.75)) a++;
                if (m.rng.chance(0.75)) b++;
            }
            if (a == b) a++;
            return new int[]{a, b};
        }
        boolean narrar = ultimaNarrada != null && narradasDaSemana.contains(ultimaNarrada)
                && p.mandante.equals(ultimaNarrada.mandanteId) && p.visitante.equals(ultimaNarrada.visitanteId);
        // a ordem dos batedores: o técnico que acompanha ao vivo escolhe a dele (no duelo, o mandante e depois o
        // visitante); a IA, o auxiliar e o jogo sem tela usam a automática
        OpcoesPartida op = vivos.remove(t.id + "#" + t.ano + "#" + p.id);
        br.futebas.partida.AoVivo av = narrar && op != null ? op.aoVivo : null;
        List<List<Integer>> ordem = new ArrayList<>(java.util.Arrays.asList(null, null));
        if (av != null) {
            for (int s = 0; s < 2; s++) {
                if (s != op.ladoHumano && !op.ambosHumanos) continue;
                List<Integer> o = av.ordemPenaltis(s, java.util.Collections.unmodifiableList(Penaltis.ordemAutomatica(par[s])));
                ordem.set(s, o == null ? null : List.copyOf(o));
            }
        }
        Penaltis.Disputa d = Penaltis.disputar(par[0], par[1], m.rng.derivar(p.id * 17L + 3), narrar, ordem.get(0), ordem.get(1));
        if (narrar) {
            ultimaNarrada.penaltisMandante = d.golsMandante();
            ultimaNarrada.penaltisVisitante = d.golsVisitante();
            ultimaNarrada.lances.add(new Lance(120, 0, "penalidades", "A decisão vai para os pênaltis!", -1,
                    ultimaNarrada.golsMandante, ultimaNarrada.golsVisitante, null, null, null, null));
            for (Penaltis.Cobranca c : d.cobrancas()) {
                ultimaNarrada.lances.add(new Lance(120, 0, "cobranca", c.texto(), c.lado(), ultimaNarrada.golsMandante,
                        ultimaNarrada.golsVisitante, c.jogadorId(), c.nome(), c.detalheLance(), "penalti"));
            }
            if (av != null) {
                List<Lance> cobrancas = new ArrayList<>();
                for (Lance l : ultimaNarrada.lances) if ("cobranca".equals(l.tipo)) cobrancas.add(l);
                av.disputaPenaltis(ultimaNarrada, List.copyOf(cobrancas));
            }
        }
        return new int[]{d.golsMandante(), d.golsVisitante()};
    }

    private static final String[] LESOES = {"Lesão leve na coxa", "Entorse no tornozelo", "Lesão muscular", "Contusão no joelho",
            "Estiramento na panturrilha", "Lesão no tendão", "Fratura"};
    private static final int[][] LESAO_SEMANAS = {{1, 2}, {2, 3}, {3, 4}, {5, 8}, {6, 9}, {10, 16}, {12, 24}};
    private static final double[] LESAO_PESO = {35, 25, 18, 9, 6, 4, 3};

    void aplicarResultado(Torneio t, Partida p, ResultadoPartida r, Clube cm, Clube cv, double fm, double fv) {
        Historia.EstatAno est = m.historia.estat(m.ano);
        est.jogosTotal++;
        est.golsTotal += r.golsMandante + r.golsVisitante;
        if ("nacional".equals(t.ambito) && t.divisao != null && p.rodadaMata < 0) {
            est.jogosLiga++;
            est.golsLiga += r.golsMandante + r.golsVisitante;
            if (r.golsMandante > r.golsVisitante) est.vitMandante++;
            else if (r.golsMandante == r.golsVisitante) est.empates++;
            else est.vitVisitante++;
        }
        for (Clube c : new Clube[]{cm, cv}) {
            if (c.virtual) continue;
            for (int id : c.elenco) {
                Jogador j = m.jogador(id);
                if (j.suspensao > 0 && !r.minutos.containsKey(id)) j.suspensao--;
            }
        }
        int venc = r.vencedorLado();
        TimePartida[] esc = escalacoes.get(t.id + "#" + t.ano + "#" + p.id);
        Set<Integer> lesionados = r.lesoesIds();
        for (Map.Entry<Integer, Integer> e : r.minutos.entrySet()) {
            if (e.getKey() < 0) continue;
            Jogador j = m.jogador(e.getKey());
            Integer fimEnergia = r.energia.get(j.id);
            if (fimEnergia != null) j.energia = Math.max(0, Math.min(100, fimEnergia)); // lance a lance: a energia do apito final
            else gastarEnergia(j, e.getValue(), esc == null ? Marcacao.NORMAL : esc[cm.id.equals(j.clubeId) ? 0 : 1].marcacao);
            j.jogosTemp++;
            j.jogosCarreira++;
            j.minutosTemp += e.getValue();
            j.somaNotasTemp += r.notas.getOrDefault(j.id, 6.0);
            j.semanasSemJogar = 0;
            boolean doMandante = cm.id.equals(j.clubeId);
            boolean ganhou = venc == (doMandante ? 0 : 1);
            boolean perdeu = venc == (doMandante ? 1 : 0);
            j.moral = Math.max(0, Math.min(100, j.moral + 1 + (ganhou ? 2 : 0) - (perdeu ? 2 : 0)));
            if (esc != null) {
                // lance a lance: as lesões aconteceram no jogo
                if (!j.amador() && lesionados.contains(j.id)) lesionar(j);
            } else {
                double pl = 0.010 + 0.0008 * Math.max(0, j.idade(m.ano) - 28);
                if (!j.amador() && m.rng.chance(pl * e.getValue() / 90.0)) lesionar(j);
            }
        }
        for (int[] g : r.gols) {
            if (g[1] >= 0 && g[4] == 0) {
                Jogador j = m.jogador(g[1]);
                j.golsTemp++;
                j.golsCarreira++;
            }
            if (g[3] > 0) m.jogador(g[3]).assistTemp++;
        }
        for (int[] a : r.amarelos) {
            if (a[1] < 0) continue;
            Jogador j = m.jogador(a[1]);
            j.amarelos++;
            if (j.amarelos % 3 == 0) j.suspensao++;
        }
        for (int[] v : r.vermelhos) {
            if (v[1] < 0) continue;
            Jogador j = m.jogador(v[1]);
            int jogos = 1 + (m.rng.chance(0.25) ? 1 : 0) + (m.rng.chance(0.08) ? 1 : 0);
            j.suspensao += jogos;
            Clube c = m.clube(j.clubeId);
            if (c != null && c.humano) m.mensagemDoClube(c.id, "jogador", "Suspensão", j.nomeExibicao() + " foi expulso e cumprirá " + jogos + " jogo(s) de suspensão.");
        }
        cm.jogosTemp++;
        cv.jogosTemp++;
        cm.ultimoJogoSa = cv.ultimoJogoSa = m.semanaAbsoluta();
        if (!"amistoso".equals(t.ambito)) {
            treinadores.registrarJogo(cm, r.golsMandante, r.golsVisitante);
            treinadores.registrarJogo(cv, r.golsVisitante, r.golsMandante);
        }
        if (!cm.virtual) {
            int publico = Financas.publico(cm, cv, t, m.ano);
            p.publico = publico;
            long renda = Financas.renda(publico, t, cm, m.ano);
            boolean dividida = "copa_nacional".equals(t.ambito) && !cv.virtual;
            long parteMandante = dividida ? renda / 2 : renda;
            cm.dinheiro += parteMandante;
            cm.financas.ingressos += parteMandante;
            if (dividida) {
                cv.dinheiro += renda - parteMandante;
                cv.financas.ingressos += renda - parteMandante;
            }
        }
        if (!"amistoso".equals(t.ambito)) {
            diretoria.depoisDoJogo(cm, cv, r.golsMandante, r.golsVisitante, true, fm, fv);
            diretoria.depoisDoJogo(cv, cm, r.golsVisitante, r.golsMandante, false, fv, fm);
        }
        if (m.multi()) {
            // multi-treinador: cada lado humano conta para o técnico dele (duelo direto: os dois)
            for (int lado = 0; lado < 2; lado++) {
                Clube meu = lado == 0 ? cm : cv;
                Tecnico dono = meu.humano ? m.tecnicoDoClube(meu.id) : null;
                if (dono == null) continue;
                int pro = lado == 0 ? r.golsMandante : r.golsVisitante, contra = lado == 0 ? r.golsVisitante : r.golsMandante;
                dono.jogos++;
                if (pro > contra) dono.vitorias++;
                else if (pro == contra) dono.empates++;
                else dono.derrotas++;
                if (!"amistoso".equals(t.ambito)) {
                    int pts = pro > contra ? 3 : pro == contra ? 1 : 0;
                    if (pro > contra && lado == 1) pts++;
                    if (pro > contra && (Integer.valueOf(1).equals(t.divisao) || "continental".equals(t.ambito))) pts++;
                    dono.pontosRanking += pts;
                }
                m.mensagemPara(dono, new Mensagem(m.ano, m.semana, "resultado", t.nome, r.placar()));
            }
        }
        Tecnico tec = m.multi() ? null : m.tecnico;
        if (tec != null && (cm.humano || cv.humano)) {
            int pro = cm.humano ? r.golsMandante : r.golsVisitante, contra = cm.humano ? r.golsVisitante : r.golsMandante;
            tec.jogos++;
            if (pro > contra) tec.vitorias++;
            else if (pro == contra) tec.empates++;
            else tec.derrotas++;
            // ranking de técnicos: 1 a 5 pontos por jogo (resultado, mando e divisão)
            if (!"amistoso".equals(t.ambito)) {
                int pts = pro > contra ? 3 : pro == contra ? 1 : 0;
                if (pro > contra && cv.humano) pts++;
                if (pro > contra && (Integer.valueOf(1).equals(t.divisao) || "continental".equals(t.ambito))) pts++;
                tec.pontosRanking += pts;
            }
            m.mensagem("resultado", t.nome, r.placar());
        }
        if (!silencioso) noticiario.partida(t, p, r, cm, cv, fm, fv);
        int margem = Math.abs(r.golsMandante - r.golsVisitante);
        if (margem >= 6) m.historia.recorde("maior_goleada", "Maior goleada", r.placar() + " (" + t.nome + ", " + m.ano + ")", margem, m.ano);
    }

    /**
     * A IA escolhe o estilo do jogo pela diferença de força (o técnico da IA tem um estilo
     * preferido, que usa quando o jogo é equilibrado).
     */
    public static Estilo estiloIA(Clube c, double propria, double adversario, boolean mandante) {
        double d = propria - adversario;
        if (d >= 8) return mandante ? Estilo.ATAQUE_TOTAL : Estilo.PRESSAO_ALTA;
        if (d <= -10 && !mandante) return Estilo.RETRANCA;
        if (d <= -5) return Estilo.CONTRA_ATAQUE;
        Estilo pref = c == null ? Estilo.EQUILIBRADO : c.tatica.estilo;
        return pref == Estilo.RETRANCA ? Estilo.EQUILIBRADO : pref;
    }

    /**
     * Marcação e concentração de ataques da IA: marca forte quando é mais fraca, solta quando é
     * muito mais forte; às vezes lê o adversário e ataca pelo setor mais fraco dele.
     */
    public static void taticaIA(TimePartida t, TimePartida adv, double propria, double adversario, Rng rng) {
        double d = propria - adversario;
        t.marcacao = d <= -6 ? Marcacao.PESADA : d >= 10 ? Marcacao.LEVE : Marcacao.NORMAL;
        t.concentracao = Concentracao.EQUILIBRADO;
        if (rng.chance(0.4)) {
            t.concentracao = MotorPartida.melhorConcentracao(PerfilTime.de(t.titulares, t.capitao), PerfilTime.de(adv.titulares, adv.capitao));
        }
    }

    private static double forca(TimePartida t) {
        double s = 0;
        for (Escalado e : t.titulares) s += e.rende();
        return s / Math.max(1, t.titulares.size());
    }

    /** Descanso até o dia {@code dia}: recupera por dia conforme a idade (mais novo volta mais rápido). */
    void recuperarEnergia(Jogador j, long dia) {
        if (j.diaEnergia > 0 && dia > j.diaEnergia && j.energia < 100) {
            int idade = j.idade(m.ano);
            double[] r = Balanceamento.ENERGIA_RECUPERA_DIA;
            double porDia = idade <= 20 ? r[0] : idade <= 25 ? r[1] : idade <= 31 ? r[2] : idade <= 36 ? r[3] : r[4];
            j.energia = (int) Math.min(100, j.energia + Math.round(porDia * (dia - j.diaEnergia)));
        }
        if (dia > j.diaEnergia) j.diaEnergia = dia;
    }

    private void recuperarElenco(Clube c, long dia) {
        if (c == null || c.virtual) return;
        for (int id : c.elenco) {
            Jogador j = m.jogador(id);
            if (j != null) recuperarEnergia(j, dia);
        }
    }

    /** Energia gasta no jogo (Brasfoot: mais com a idade; a marcação pesada cansa mais). */
    void gastarEnergia(Jogador j, int minutos, Marcacao marc) {
        int idade = j.idade(m.ano);
        int[] g = Balanceamento.ENERGIA_GASTO;
        int base = idade <= 20 ? g[0] : idade <= 25 ? g[1] : idade <= 31 ? g[2] : idade <= 36 ? g[3] : g[4];
        double fm = Balanceamento.MARCACAO_ENERGIA[(marc == null ? Marcacao.NORMAL : marc).ordinal()];
        j.energia = Math.max(0, j.energia - (int) Math.round(base * fm * Math.min(120, minutos) / 90.0));
    }

    private void lesionar(Jogador j) {
        int k = m.rng.peso(LESAO_PESO);
        int semanas = m.rng.entre(LESAO_SEMANAS[k][0], LESAO_SEMANAS[k][1]);
        if (j.idade(m.ano) >= 32) semanas += m.rng.entre(0, 2);
        j.lesaoSemanas = Math.max(j.lesaoSemanas, semanas);
        j.lesao = LESOES[k];
        if (Sonda.Atual.s != null) Sonda.Atual.s.lesao(j, semanas);
        if (!silencioso) noticiario.lesao(j, semanas, LESOES[k]);
        Clube c = m.clube(j.clubeId);
        if (c != null && c.humano) {
            m.mensagemDoClube(c.id, "jogador", "Lesão", j.nomeExibicao() + ": " + LESOES[k].toLowerCase() + ". Fora por cerca de " + semanas + " semana(s)."
                    + (j.idade(m.ano) >= 32 ? " Com a idade, ele se machuca com mais facilidade." : ""));
        }
    }

    // ======================================================================= fim de torneio
    void encerrarTorneio(Torneio t) {
        t.processado = true;
        if ("selecoes".equals(t.ambito)) selecoes.encerrarTorneio(t);
        if (t.campeao == null) return;
        if ("amistoso".equals(t.ambito)) {
            if (t.participantes.size() >= 3) {
                Historia.Titulo tit = new Historia.Titulo();
                tit.ano = t.ano;
                tit.temporada = t.temporada;
                tit.competicaoId = t.id;
                tit.competicao = t.nome;
                tit.ambito = "amistoso";
                tit.pais = t.pais;
                tit.campeaoId = t.campeao;
                tit.campeao = m.nomeParticipante(t.campeao);
                tit.participantes = t.participantes.size();
                m.historia.titulos.add(tit);
                if (torneioDoHumano(t)) m.historia.evento(m.ano, m.semana, "titulo", tit.campeao + " vence o " + t.nome + ".");
            }
            return;
        }
        Historia.Titulo tit = new Historia.Titulo();
        tit.ano = t.ano;
        tit.temporada = t.temporada;
        tit.competicaoId = t.id;
        tit.competicao = t.nome;
        tit.ambito = t.ambito;
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
            tit.artilheiroId = j.id;
            tit.artilheiro = j.nomeExibicao();
            tit.golsArtilheiro = art.get(0)[1];
            if ("nacional".equals(t.ambito) && Integer.valueOf(1).equals(t.divisao)) {
                m.historia.recorde("artilheiro_liga", "Mais gols numa liga nacional", j.nomeExibicao() + " (" + tit.golsArtilheiro + " gols, "
                        + t.nome + " " + t.temporada + ")", tit.golsArtilheiro, t.ano);
            }
        }
        m.historia.titulos.add(tit);
        if (!silencioso) noticiario.titulo(t, tit);
        // torneios curtos (Apertura/Clausura...): cada campeão vira um título à parte (rodada 2 do balanço)
        for (String[] tc : t.titulosTorneiosCurtos()) {
            Historia.Titulo tt = new Historia.Titulo();
            tt.ano = t.ano;
            tt.temporada = t.temporada;
            tt.competicaoId = t.id + "-" + tc[0].toLowerCase(java.util.Locale.ROOT).replace("ó", "o");
            tt.competicao = t.nome + " — " + tc[0];
            tt.ambito = t.ambito;
            tt.pais = t.pais;
            tt.campeaoId = tc[1];
            tt.campeao = m.nomeParticipante(tc[1]);
            tt.participantes = t.participantes.size();
            m.historia.titulos.add(tt);
            treinadores.titulo(tc[1], tt.competicao + " " + t.temporada);
            if (relevante(t)) m.historia.evento(m.ano, m.semana, "titulo", tt.campeao + " é campeão: " + tt.competicao + " " + t.temporada + ".");
            for (Tecnico tq : m.todosTecnicos()) if (tc[1].equals(tq.clubeId)) tq.titulos.add(t.ano + " " + tt.competicao);
        }
        selecaoDaLiga(t);
        treinadores.titulo(t.campeao, t.nome + " " + t.temporada);
        if (relevante(t)) m.historia.evento(m.ano, m.semana, "titulo", tit.campeao + " é campeão: " + t.nome + " " + t.temporada + ".");
        Selecao selCamp = m.selecao(t.campeao);
        if ("selecoes".equals(t.ambito) && selCamp != null) {
            selCamp.titulos.add(t.nome);
        }
        double somaReceita = 0;
        int nReceita = 0;
        for (String id : t.participantes) {
            Clube c = m.clube(id);
            if (c == null || c.virtual) continue;
            somaReceita += Financas.receitaReferencia(c, m.ano);
            nReceita++;
        }
        double receitaMedia = nReceita == 0 ? 0 : somaReceita / nReceita;
        for (int i = 0; i < t.classificacao.size(); i++) {
            Clube c = m.clube(t.classificacao.get(i));
            if (c == null || c.virtual) continue;
            long pr = Financas.premio(t, i + 1, receitaMedia);
            c.dinheiro += pr;
            c.financas.premios += pr;
        }
        Clube camp = m.clube(t.campeao);
        if (camp != null && !camp.virtual) {
            double bonus = switch (t.ambito) {
                case "continental" -> MontadorTemporada.nivelContinental(new Db.Competicao(t.id, t.nome, t.ambito, null, null, null, null, null, null)) == 0 ? 6 : 3;
                case "estadual" -> camp.reputacao < 70 ? 1.5 : 0.5;
                case "copa_nacional" -> 3;
                default -> t.divisao == null ? 3 : t.divisao == 1 ? 5 : 1.5;
            };
            camp.reputacao = Math.min(100, camp.reputacao + bonus);
            camp.torcida = Math.min(100, camp.torcida + bonus * 0.6);
            camp.confiancaDiretoria = Math.min(100, camp.confiancaDiretoria + 15);
        }
        for (Tecnico tec : m.todosTecnicos()) {
            String meu = "selecoes".equals(t.ambito) ? (tec.selecaoPais == null ? null : Selecao.id(tec.selecaoPais)) : tec.clubeId;
            if (meu != null && meu.equals(t.campeao)) {
                tec.titulos.add(t.ano + " " + t.nome);
                tec.pontosRanking += pontosRanking(t, true);
                tec.reputacao = Math.min(100, tec.reputacao + ("selecoes".equals(t.ambito) ? 10 : 2));
                m.mensagemPara(tec, new Mensagem(m.ano, m.semana, "resultado", "CAMPEÃO!", "O " + tit.campeao + " é campeão: " + t.nome + " " + t.temporada + "!"));
            } else if (meu != null && meu.equals(t.vice)) {
                tec.pontosRanking += pontosRanking(t, false);
            }
        }
    }

    /** Vale manchete na linha do tempo? (países detalhados, continentais, seleções e o do técnico). */
    private boolean relevante(Torneio t) {
        if (!"nacional".equals(t.ambito) && !"estadual".equals(t.ambito) && !"copa_nacional".equals(t.ambito)) return true;
        if ("nacional".equals(t.ambito) && t.divisao != null && t.divisao > 2) return false;
        Pais p = m.pais(t.pais);
        Clube h = m.clubeHumano();
        return p == null || p.detalhado || (h != null && h.pais.equals(t.pais));
    }

    private static int pontosRanking(Torneio t, boolean campeao) {
        int c = switch (t.ambito) {
            case "selecoes" -> t.id.contains("copa-do-mundo") ? Balanceamento.RANKING_COPA_DO_MUNDO : Balanceamento.RANKING_SELECOES;
            case "continental" -> MontadorTemporada.nivelContinental(new Db.Competicao(t.id, t.nome, t.ambito, null, null, null, null, null, null)) == 0
                    ? Balanceamento.RANKING_CONTINENTAL_PRINCIPAL : Balanceamento.RANKING_CONTINENTAL;
            case "estadual" -> Balanceamento.RANKING_ESTADUAL;
            case "copa_nacional" -> Balanceamento.RANKING_COPA;
            default -> t.divisao == null ? Balanceamento.RANKING_COPA : t.divisao == 1 ? Balanceamento.RANKING_LIGA_1
                    : t.divisao == 2 ? Balanceamento.RANKING_LIGA_2 : Balanceamento.RANKING_LIGA_OUTRAS;
        };
        return campeao ? c : c * 2 / 5;
    }

    // ======================================================================= rotina semanal
    void semanal(int w) {
        int agora = m.semanaAbsoluta();
        long fimSemana = Calendario.inicio(agora).plusDays(7).toEpochDay();
        for (Jogador j : m.jogadores) {
            if (j.lesaoSemanas > 0) j.lesaoSemanas--;
            recuperarEnergia(j, fimSemana);
            if (j.status != StatusJogador.PROFISSIONAL || j.amador()) continue;
            j.semanasSemJogar++;
            j.moral += (int) Math.round((65 - j.moral) * 0.03);
            if (j.semanasSemJogar >= 4 && j.idade(m.ano) < 33 && j.lesaoSemanas == 0 && w % 2 == 0) {
                Clube c = m.clube(j.clubeId);
                if (c != null && c.jogosTemp > 0 && j.forca >= c.nivel() - 2) {
                    int antes = j.moral;
                    j.moral = Math.max(0, j.moral - 4);
                    if (c.humano && antes >= 30 && j.moral < 30) {
                        m.mensagemDoClube(c.id, "jogador", j.nomeExibicao() + " insatisfeito",
                                j.nomeExibicao() + ": \"Não quero continuar a esquentar o banco. Preciso jogar.\"");
                    }
                }
            }
        }
        if (w % 4 == 0) pagarSalarios();
        mercado.vencimentos();
        diretoria.semana();
        if (w % 2 == 0) atualizarNiveis();
        for (Clube c : m.clubes.values()) {
            if (c.obraFim > 0 && agora >= c.obraFim) {
                c.capacidade = Math.min(Balanceamento.CAPACIDADE_MAXIMA, c.capacidade + c.obraLugares);
                c.obraFim = 0;
                c.obraLugares = 0;
                if (c.humano) m.mensagemDoClube(c.id, "financas", "Estádio ampliado", "A obra terminou: o " + (c.estadio == null ? "estádio" : c.estadio)
                        + " agora tem " + c.capacidade + " lugares.");
            }
        }
        racha();
        treinadores.semana();
        if (w == 26) {
            evoluirTodos();
            for (Clube c : m.clubes.values()) c.jogosMarco = c.jogosTemp;
            for (Jogador j : m.jogadores) j.minutosMarco = j.minutosTemp;
        }
        // o técnico vê na escalação a energia com que o time vai entrar: descanso até o próximo jogo
        for (Clube c : m.clubes.values()) {
            if (!c.humano || c.virtual) continue;
            long prox = Long.MAX_VALUE;
            for (Torneio t : m.torneios) for (Partida p : t.partidasDoClube(c.id)) if (!p.jogada && p.dia < prox) prox = p.dia;
            if (prox != Long.MAX_VALUE && prox - fimSemana <= 7) recuperarElenco(c, prox);
        }
    }

    /** Racha por excesso de estrelas (Brasfoot): briga no grupo derruba a moral. */
    void racha() {
        List<Clube> alvo = new ArrayList<>();
        for (Tecnico t : m.todosTecnicos()) {
            Clube c = m.clube(t.clubeId);
            if (c != null) alvo.add(c);
        }
        for (Clube x : alvo) {
            int estrelas = 0;
            for (int id : x.elenco) if (m.jogador(id).estrela) estrelas++;
            if (estrelas <= Balanceamento.RACHA_ESTRELAS || !m.rng.chance(Balanceamento.RACHA_CHANCE_SEMANA)) continue;
            for (int id : x.elenco) {
                Jogador j = m.jogador(id);
                j.moral = Math.max(0, j.moral - (j.estrela ? 8 : 4));
            }
            if (x.humano) m.mensagemDoClube(x.id, "jogador", "Racha no elenco", "Estrelas demais no " + x.nome + ": os jogadores brigam por espaço e a moral do grupo caiu.");
        }
    }

    /** Força média dos 16 melhores de cada clube (escala dos jogadores), usada pela IA e pela base. */
    void atualizarNiveis() {
        double[] buf = new double[64];
        for (Clube c : m.clubes.values()) {
            if (c.virtual || c.elenco.isEmpty()) continue;
            int n = Math.min(buf.length, c.elenco.size());
            for (int i = 0; i < n; i++) buf[i] = m.jogador(c.elenco.get(i)).forca;
            java.util.Arrays.sort(buf, 0, n);
            int k = Math.min(16, n);
            double s = 0;
            for (int i = n - k; i < n; i++) s += buf[i];
            c.nivelAtual = s / k;
        }
    }

    void pagarSalarios() {
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe) continue;
            boolean parado = !c.humano && c.divisao == 0 && m.semanaAbsoluta() - c.ultimoJogoSa > Balanceamento.SALARIO_SEM_JOGO_SEMANAS;
            long folha = 0;
            if (!parado) for (int id : c.elenco) {
                Jogador j = m.jogador(id);
                if (j.contrato != null) folha += j.contrato.salario;
            }
            for (int id : c.base) {
                Jogador j = m.jogador(id);
                if (j.contrato != null) folha += j.contrato.salario;
            }
            if (c.tvParcelas > 0) {
                long parcela = c.tvPendente / c.tvParcelas;
                c.tvPendente -= parcela;
                c.tvParcelas--;
                c.dinheiro += parcela;
                c.financas.tvPatrocinio += parcela;
            }
            c.dinheiro -= folha;
            c.financas.salarios += folha;
            long custeio = Financas.custeioMensal(c, m.ano);
            c.dinheiro -= custeio;
            c.financas.obras += custeio;
            long juros = Math.round(c.divida * Financas.JUROS_MES);
            c.dinheiro -= juros;
            c.financas.juros += juros;
            if (c.humano) {
                if (c.divida > 0) {
                    long antes = c.divida, parcela = Financas.amortizacaoParcela(c);
                    long pago = Financas.pagarParcela(c);
                    if (c.divida == 0) m.mensagem("financas", "Empréstimo quitado", "O " + c.nome + " pagou a última parcela do empréstimo (" + Financas.formatar(antes) + ").");
                    else if (pago < parcela) m.mensagem("financas", "Parcela do empréstimo atrasada",
                            "Faltou caixa: o " + c.nome + " pagou " + Financas.formatar(pago) + " de " + Financas.formatar(parcela)
                                    + ". O resto fica para o próximo mês. Dívida: " + Financas.formatar(c.divida) + ".");
                }
                if (c.dinheiro < 0) m.mensagem("financas", "Saldo negativo", "O caixa do " + c.nome + " está negativo: " + Financas.formatar(c.dinheiro) + ".");
                continue;
            }
            long limite = Financas.limiteEmprestimo(c, m.ano);
            if (c.dinheiro < 0 && c.divida < limite) {
                long emp = Math.min(limite - c.divida, -c.dinheiro + limite / 10);
                c.divida += emp;
                c.dinheiro += emp;
            } else if (c.divida > 0 && c.dinheiro > limite / 4) {
                long pg = Math.min(c.divida, c.dinheiro - limite / 4);
                c.divida -= pg;
                c.dinheiro -= pg;
            }
        }
    }

    void evoluirTodos() {
        for (Jogador j : m.jogadores) {
            if (j.amador()) continue;
            double frac;
            double estr;
            Clube c = null;
            switch (j.status) {
                case PROFISSIONAL, BASE -> {
                    c = m.clube(j.clubeId);
                    int jogos = c == null ? 0 : c.jogosTemp - c.jogosMarco;
                    frac = jogos <= 0 ? 0.3 : Math.min(1, (j.minutosTemp - j.minutosMarco) / (jogos * 90.0));
                    if (j.status == StatusJogador.BASE) frac = Math.max(frac, 0.35);
                    estr = c == null ? 30 : c.estrutura;
                }
                case LIVRE -> {
                    frac = 0.05;
                    estr = 0;
                }
                default -> {
                    continue;
                }
            }
            double teto = c == null || c.virtual ? Evolucao.tetoClube(null) : Evolucao.tetoClube(c);
            boolean elite = c != null && c.reputacao >= Balanceamento.DECLINIO_ELITE_REP;
            Evolucao.evoluir(j, m.ano, frac, estr, teto, elite, m.rng);
        }
    }

    // ======================================================================= viradas de temporada
    /** Começo de julho: termina a temporada ago–mai e começa a próxima. */
    void viradaEuropeia() {
        int y = m.ano;
        encerrarTemporada("europeu");
        abrirTemporada("europeu", y, false);
    }

    /**
     * Fim de uma temporada de um calendário: conclui as competições, avalia diretorias,
     * aposenta, devolve emprestados, fecha as finanças, faz a peneira e sobe a base dos clubes
     * dos países desse calendário. As competições vão para "anteriores".
     */
    void encerrarTemporada(String cal) {
        List<Torneio> daTemporada = new ArrayList<>();
        for (Torneio t : m.torneios) if (cal.equals(t.calendario)) daTemporada.add(t);
        for (Torneio t : daTemporada) {
            if (!t.encerrado) t.concluir(this);
            if (!t.processado) encerrarTorneio(t);
        }
        Set<String> paises = new HashSet<>();
        for (Pais p : m.paises.values()) if (cal.equals(p.calendario)) paises.add(p.id);
        sub20.encerrarTemporada(cal);
        diretoria.avaliarTemporada(daTemporada, paises);
        int ano = m.ano;
        Historia.EstatAno est = m.historia.estat(ano);
        // negociação do técnico humano em andamento: o livre não desiste do futebol
        Set<Integer> negociando = new HashSet<>();
        for (br.futebas.ia.PropostaJogador p : m.propostas) if (p.ativa()) negociando.add(p.jogadorId);
        for (Jogador j : m.jogadores) {
            if (j.amador() || j.status == StatusJogador.APOSENTADO || j.status == StatusJogador.DISPENSADO) continue;
            Clube c = m.clube(j.clubeId);
            boolean daqui = c != null ? paises.contains(c.pais) : "ano".equals(cal);
            if (!daqui) continue;
            if (j.contrato != null && j.contrato.tipo == TipoContrato.EMPRESTIMO && j.status == StatusJogador.PROFISSIONAL) mercado.devolverEmprestado(j);
            if (j.status == StatusJogador.LIVRE) j.anosLivre++;
            else j.anosLivre = 0;
            boolean desiste = j.status == StatusJogador.LIVRE && j.kingsTimeId == null && !negociando.contains(j.id)
                    && (j.anosLivre >= Balanceamento.LIVRE_ANOS || (j.anosLivre >= Balanceamento.LIVRE_FRACO_ANOS && j.forca < Balanceamento.LIVRE_FRACO_FORCA));
            if ((j.status == StatusJogador.PROFISSIONAL || j.status == StatusJogador.LIVRE) && (desiste || j.vaiAposentar)) {
                aposentar(j, ano);
                est.aposentadorias++;
            }
        }
        fimDeTemporadaClubes(paises, ano);
        Set<String> ativos = new HashSet<>();
        for (Torneio t : daTemporada) ativos.addAll(t.participantes);
        sub20.dispensarNaoEscolhidos(paises); // peneira do técnico: quem não subiu ao Sub-20 vai embora
        est.entradasBase += populador.peneira(paises, ativos);
        est.entradasBase += sub20.peneirasDosTecnicos(paises); // turma nova do técnico: 5 (às vezes 6 ou 7)
        promoverBase(paises);
        sub20.reporTodos(paises); // todo Sub-20 volta a ter 23
        m.torneiosAnteriores.removeIf(t -> cal.equals(t.calendario));
        m.torneiosAnteriores.addAll(daTemporada);
        m.torneios.removeAll(daTemporada);
    }

    /** Começo de uma temporada: monta as competições e paga TV/patrocínio aos clubes do calendário. */
    void abrirTemporada(String cal, int anoInicio, boolean inicial) {
        Map<String, Integer> antes = new HashMap<>();
        for (Clube c : m.clubes.values()) if (cal.equals(c.calendario)) antes.put(c.id, c.divisao);
        List<Torneio> novos = new MontadorTemporada(m, db, cal, anoInicio, inicial).montar();
        Financas.atualizarFreio(m.clubes.values()); // a 3ª maior receita de cada liga (com as divisões novas), antes da TV
        sortearForma(cal, anoInicio);
        m.torneios.addAll(novos);
        sub20.abrir(novos, inicial);
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe || !cal.equals(c.calendario)) continue;
            c.jogosTemp = 0;
            c.jogosMarco = 0;
            // TV/patrocínio em parcelas mensais (o caixa não começa a temporada com o ano inteiro)
            c.tvPendente += Financas.tvPatrocinio(c, m.ano);
            c.tvParcelas = Balanceamento.TV_PARCELAS;
        }
        if (!inicial) manchetesDivisoes(antes);
        for (Tecnico tq : m.todosTecnicos()) {
            Clube h = m.clube(tq.clubeId);
            if (h == null || !cal.equals(h.calendario)) continue;
            List<String> nomes = new ArrayList<>();
            for (Torneio t : novos) if (t.participantes.contains(h.id)) nomes.add(t.nome);
            m.mensagemPara(tq, new Mensagem(m.ano, m.semana, "diretoria", "Temporada " + MontadorTemporada.rotulo(cal, anoInicio),
                    "Competições da temporada: " + (nomes.isEmpty() ? "nenhuma." : String.join(", ", nomes) + ".")));
        }
    }

    /**
     * Forma da temporada dos clubes do calendário (16/11/2026): normal com desvio FORMA_SD pontos, limitada a
     * ±FORMA_MAX; o clube de um técnico humano fica 0. Sorteio próprio (não gasta o RNG do mundo).
     */
    void sortearForma(String cal, int ano) {
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !cal.equals(c.calendario)) continue;
            if (c.humano) {
                c.formaTemporada = 0;
                continue;
            }
            Rng r = new Rng(m.semente * 0x9E3779B97F4A7C15L ^ c.id.hashCode() * 0xBF58476D1CE4E5B9L ^ ano * 0x94D049BB133111EBL);
            c.formaTemporada = Math.max(-Balanceamento.FORMA_MAX, Math.min(Balanceamento.FORMA_MAX, r.gauss(0, Balanceamento.FORMA_SD)));
        }
    }

    /** Virada do ano: fecha a temporada jan–dez, o ano civil (prêmios, carreiras) e abre o novo. */
    void virarAno() {
        int ano = m.ano;
        // torneios de seleções do ano
        List<Torneio> eventos = new ArrayList<>();
        for (Torneio t : m.torneios) if ("evento".equals(t.calendario)) eventos.add(t);
        for (Torneio t : eventos) {
            if (!t.encerrado) t.concluir(this);
            if (!t.processado) encerrarTorneio(t);
        }
        kings.fimDoAno();
        premiosDoAno();
        evoluirTodos();
        for (Jogador j : m.jogadores) {
            if (j.amador()) continue;
            if (j.jogosTemp > 0) {
                Clube c = m.clube(j.clubeId);
                Passagem pa = new Passagem(ano, c != null ? c.id : null, c != null ? c.nome : "Sem clube",
                        j.jogosTemp, j.golsTemp, j.assistTemp, Math.round(j.notaMediaTemp() * 10) / 10.0, j.forcaExibida());
                pa.emprestimo = j.contrato != null && j.contrato.tipo == TipoContrato.EMPRESTIMO;
                j.carreira.add(pa);
                if (c != null && c.divisao == 1) j.jogouElite = true;
            }
            j.zerarTemporada();
            resumirCarreiraAntiga(j, ano);
        }
        treinadores.fimDoAno();
        manchetesCarreiras(ano);
        encerrarTemporada("ano");
        arquivarAposentados(ano); // depois das aposentadorias da virada (antes vinha antes delas: ficavam vivos 1 ano)
        Historia.EstatAno est = m.historia.estat(ano);
        estatisticasDoAno(est);
        m.paraCadaTecnico(() -> {
            diretoria.fimDeTemporada(titulosHumano(ano), desempenhoHumano());
            m.registrarTemporadaTecnico(null);
        });
        m.torneiosAnteriores.removeIf(t -> "evento".equals(t.calendario));
        m.torneiosAnteriores.addAll(eventos);
        m.torneios.removeAll(eventos);
        m.ano++;
        m.semana = 1;
        m.leiloes.removeIf(l -> !"aberto".equals(l.estado));
        abrirTemporada("ano", m.ano, false);
        m.torneios.addAll(selecoes.torneiosDoAno(m.ano));
        mercado.vencimentos();
    }

    private int titulosHumano(int ano) {
        if (m.tecnico == null) return 0;
        int n = 0;
        for (String s : m.tecnico.titulos) if (s.startsWith(ano + " ")) n++;
        return n;
    }

    private double desempenhoHumano() {
        Clube c = m.clubeHumano();
        if (c == null) return 0;
        return (c.confiancaDiretoria - 55) / 20.0;
    }

    /**
     * Memória: aposentados de anos anteriores viram registros compactos ({@link Arquivado}).
     * Ficam vivos os que a Kings League ainda pode chamar (aposentados há pouco e bons) e os que
     * estão num time da Kings.
     */
    /**
     * Save menor: quem não é grande (auge < 70, sem estrela) guarda as temporadas de mais de 5 anos
     * atrás resumidas por passagem no clube (como o arquivo e a carreira real do banco).
     */
    static void resumirCarreiraAntiga(Jogador j, int ano) {
        if (j.carreira.size() <= 6 || j.estrela || j.topMundial) return;
        int auge = 0, antigas = 0;
        for (Passagem p : j.carreira) {
            auge = Math.max(auge, p.forca);
            if (Math.max(p.ano, p.anoFim) <= ano - 5) antigas++;
        }
        if (auge >= Arquivado.RESUMIR_ABAIXO || antigas < 2) return;
        List<Passagem> velhas = Arquivado.resumir(j.carreira.subList(0, antigas));
        if (velhas.size() == antigas) return;
        List<Passagem> nova = new ArrayList<>(velhas);
        nova.addAll(j.carreira.subList(antigas, j.carreira.size()));
        j.carreira = nova;
    }

    int arquivarAposentados(int ano) {
        Set<Integer> kingsIds = new HashSet<>();
        for (LigaKings l : m.kings) for (LigaKings.TimeKings t : l.times) for (LigaKings.AtletaKings a : t.elenco) if (a.jogadorId != null) kingsIds.add(a.jogadorId);
        // regra do jogo 16/11/2026 (saúde do mundo): quem não foi para a Kings é arquivado na mesma virada em que
        // se aposenta; quem escolheu a Kings fica vivo até ARQUIVAR_APOS_ANOS anos, mas só se ainda tiver força
        // para o draft (ARQUIVAR_FORCA, estrela ou Top Mundial); o resto vai no ano seguinte
        return m.arquivar(j -> ano >= j.anoAposentadoria && !kingsIds.contains(j.id)
                && (j.kingsPais == null || ano > j.anoAposentadoria && (ano - j.anoAposentadoria > Balanceamento.ARQUIVAR_APOS_ANOS
                        || j.forca < Balanceamento.ARQUIVAR_FORCA && !j.estrela && !j.topMundial)));
    }

    /**
     * Lenda do jogo: quem se aposenta com carreira marcante (fama, força no auge, gols, prêmios)
     * entra em {@code historia.lendas} e pode aparecer no melhor 11 dos clubes dele.
     */
    void talvezLenda(Jogador j, int ano) {
        if (j.amador()) return;
        int auge = (int) Math.round(j.forca);
        Map<String, Integer> jogosPorClube = new HashMap<>();
        for (Passagem p : j.carreira) {
            auge = Math.max(auge, p.forca);
            if (p.clubeId != null) jogosPorClube.merge(p.clubeId, p.jogos, Integer::sum);
        }
        int premios = 0;
        for (Historia.Premio p : m.historia.premios) if (Integer.valueOf(j.id).equals(p.jogadorId)) premios++;
        boolean marcante = j.fama >= 75 || auge >= 86 || j.golsCarreira >= 200 || j.topMundial || premios >= 2 || (j.estrela && j.jogosCarreira >= 250);
        if (!marcante || j.jogosCarreira < 50) return;
        Historia.Lenda l = new Historia.Lenda();
        l.ano = ano;
        l.jogadorId = j.id;
        l.nome = j.nomeExibicao();
        l.pais = j.pais;
        l.posicao = j.posicao.name();
        l.forcaAuge = auge;
        l.jogos = j.jogosCarreira;
        l.gols = j.golsCarreira;
        l.titulos = premios;
        l.fama = j.fama;
        List<String> clubes = new ArrayList<>(jogosPorClube.keySet());
        clubes.sort(Comparator.comparingInt((String c) -> -jogosPorClube.get(c)).thenComparing(c -> c));
        l.clubes = clubes;
        Clube principal = clubes.isEmpty() ? null : m.clube(clubes.get(0));
        l.resumo = l.nome + ": " + l.jogos + " jogos e " + l.gols + " gols desde 2026" + (principal == null ? "" : ", ídolo do " + principal.nome) + ".";
        m.historia.lendas.add(l);
        m.historia.evento(ano, m.semana, "lenda", l.nome + " se aposenta como lenda.");
    }

    /**
     * Seis meses antes do fim da temporada, quem vai parar anuncia (a decisão vale para o fim da temporada).
     * O dono do clube recebe mensagem; estrelas viram notícia.
     */
    void anunciarAposentadorias(String cal) {
        Set<String> paises = new HashSet<>();
        for (Pais p : m.paises.values()) if (cal.equals(p.calendario)) paises.add(p.id);
        int ano = m.ano;
        for (Jogador j : m.jogadores) {
            if (j.vaiAposentar || j.amador() || j.status != StatusJogador.PROFISSIONAL && j.status != StatusJogador.LIVRE) continue;
            Clube c = m.clube(j.clubeId);
            boolean daqui = c != null ? paises.contains(c.pais) : "ano".equals(cal);
            if (!daqui) continue;
            if (!Evolucao.aposenta(j, ano, j.status == StatusJogador.LIVRE, m.rng)) continue;
            j.vaiAposentar = true;
            noticiario.aposentadoriaAnunciada(j, c, j.idade(ano));
            if (c != null && c.humano)
                m.mensagem("jogador", "Aviso de aposentadoria", j.nomeExibicao() + " (" + j.idade(ano) + " anos) avisou que vai se aposentar no fim da temporada.");
            if (j.estrela || j.topMundial || j.fama >= 85)
                m.historia.evento(ano, m.semana, "estrela", j.nomeExibicao() + " anuncia que vai se aposentar no fim da temporada.");
        }
    }

    void aposentar(Jogador j, int ano) {
        Clube c = m.clube(j.clubeId);
        if (c != null) {
            c.elenco.remove(Integer.valueOf(j.id));
            c.base.remove(Integer.valueOf(j.id));
            if (c.humano) m.mensagemDoClube(c.id, "jogador", "Aposentadoria", j.nomeExibicao() + " anunciou a aposentadoria.");
        }
        if (j.estrela || j.fama >= 85) m.historia.evento(ano, m.semana, "estrela", j.nomeExibicao() + " se aposenta aos " + j.idade(ano) + " anos.");
        talvezLenda(j, ano);
        j.status = StatusJogador.APOSENTADO;
        j.vaiAposentar = false;
        j.anoAposentadoria = ano;
        if (treinadores.talvezVirarTecnico(j) == null) kings.talvezIrParaKings(j);
        j.clubeId = null;
        j.contrato = null;
        j.aVenda = false;
    }

    /** Manchetes de acesso e rebaixamento (países detalhados e o do técnico). */
    private void manchetesDivisoes(Map<String, Integer> antes) {
        Map<String, List<String>[]> porPaisDiv = new java.util.TreeMap<>();
        Clube h = m.clubeHumano();
        for (Clube c : m.clubes.values()) {
            Integer a = antes.get(c.id);
            if (a == null) continue;
            Pais p = m.pais(c.pais);
            if (p != null && !p.detalhado && (h == null || !h.pais.equals(c.pais))) continue;
            for (int d = 1; d <= 3; d++) {
                String k = c.pais + "#" + d;
                @SuppressWarnings("unchecked")
                List<String>[] par = porPaisDiv.computeIfAbsent(k, x -> new List[]{new ArrayList<>(), new ArrayList<>()});
                if (c.divisao == d && (a == 0 || a > d)) par[0].add(c.nome);
                if (a == d && (c.divisao == 0 || c.divisao > d)) par[1].add(c.nome);
            }
        }
        for (Map.Entry<String, List<String>[]> e : porPaisDiv.entrySet()) {
            String[] k = e.getKey().split("#");
            String pais = m.pais(k[0]) == null ? k[0] : m.pais(k[0]).nome;
            List<String> sobem = e.getValue()[0], caem = e.getValue()[1];
            sobem.sort(null);
            caem.sort(null);
            if (!sobem.isEmpty() && sobem.size() <= 8) m.historia.evento(m.ano, m.semana, "acesso", pais + ": sobem para a divisão " + k[1] + ": " + String.join(", ", sobem) + ".");
            if (!caem.isEmpty() && caem.size() <= 8) m.historia.evento(m.ano, m.semana, "rebaixamento", pais + ": caem da divisão " + k[1] + ": " + String.join(", ", caem) + ".");
        }
    }

    /** Jovens que despontam (nível de seleção antes dos 22). */
    private void manchetesCarreiras(int ano) {
        for (Jogador j : m.jogadores) {
            if (j.amador() || j.despontou || j.status != StatusJogador.PROFISSIONAL) continue;
            int idade = j.idade(ano);
            if (idade <= 21 && j.forca >= 78) {
                j.despontou = true;
                Clube c = m.clube(j.clubeId);
                m.historia.evento(ano, 52, "estrela", j.nomeExibicao() + ", " + idade + " anos, desponta" + (c != null ? " no " + c.nome : "")
                        + " (força " + j.forcaExibida() + ").");
            }
        }
    }

    private void premiosDoAno() {
        int ano = m.ano;
        Jogador craque = null, revelacao = null;
        double mc = 0, mr = 0;
        for (Jogador j : m.jogadores) {
            if (j.amador() || j.status != StatusJogador.PROFISSIONAL || j.jogosTemp < 15) continue;
            double v = j.notaMediaTemp() + j.golsTemp * 0.02 + j.forca * 0.04;
            if (v > mc) {
                mc = v;
                craque = j;
            }
            if (j.idade(ano) <= 21 && v > mr) {
                mr = v;
                revelacao = j;
            }
        }
        if (craque != null) premio(ano, "bola_de_ouro", "Bola de Ouro", craque, mc);
        if (revelacao != null) premio(ano, "revelacao", "Revelação do ano", revelacao, mr);
        rankingsBolaDeOuro(ano);
        List<Torneio> ligas = new ArrayList<>(m.torneios);
        ligas.addAll(m.torneiosAnteriores);
        for (Torneio tor : ligas) {
            if (!"nacional".equals(tor.ambito) || !Integer.valueOf(1).equals(tor.divisao) || !tor.encerrado || tor.ano != ano) continue;
            Pais p = m.pais(tor.pais);
            if (p != null && !p.detalhado) continue;
            List<int[]> art = tor.artilheiros(1);
            if (!art.isEmpty()) premio(ano, "artilheiro_nacional", "Artilheiro " + Artigos.de(tor.nome) + " " + tor.nome, m.jogador(art.get(0)[0]), art.get(0)[1]);
        }
    }

    /** Nota da temporada para os prêmios (nota média, gols, assistências, força). */
    static double valorPremio(Jogador j) {
        return j.notaMediaTemp() + j.golsTemp * 0.02 + j.assistTemp * 0.01 + j.forca * 0.04;
    }

    /** Bola de Ouro: Top 50 do ano e Top 50 jovens (até 21 anos), guardados na história. */
    void rankingsBolaDeOuro(int ano) {
        java.util.PriorityQueue<Jogador> top = new java.util.PriorityQueue<>(Comparator.comparingDouble(Simulador::valorPremio));
        java.util.PriorityQueue<Jogador> jovens = new java.util.PriorityQueue<>(Comparator.comparingDouble(Simulador::valorPremio));
        for (Jogador j : m.jogadores) {
            if (j.amador() || j.status != StatusJogador.PROFISSIONAL || j.jogosTemp < 15) continue;
            top.add(j);
            if (top.size() > 50) top.poll();
            if (j.idade(ano) <= 21) {
                jovens.add(j);
                if (jovens.size() > 50) jovens.poll();
            }
        }
        m.historia.rankings.add(ranking(ano, "bola_de_ouro", "Bola de Ouro " + ano, top));
        noticiario.bolaDeOuro(m.historia.ranking("bola_de_ouro", ano));
        m.historia.rankings.add(ranking(ano, "bola_de_ouro_jovem", "Bola de Ouro jovem (sub-21) " + ano, jovens));
    }

    private Historia.Ranking ranking(int ano, String tipo, String titulo, java.util.PriorityQueue<Jogador> fila) {
        List<Jogador> l = new ArrayList<>(fila);
        l.sort(Comparator.comparingDouble((Jogador j) -> -valorPremio(j)).thenComparingInt(j -> j.id));
        Historia.Ranking r = new Historia.Ranking();
        r.ano = ano;
        r.tipo = tipo;
        r.titulo = titulo;
        for (int i = 0; i < l.size(); i++) {
            Jogador j = l.get(i);
            Historia.ItemRanking it = new Historia.ItemRanking();
            it.posicao = i + 1;
            it.jogadorId = j.id;
            it.jogador = j.nomeExibicao();
            Clube c = m.clube(j.clubeId);
            it.clubeId = c == null ? null : c.id;
            it.clube = c == null ? null : c.nome;
            it.pais = j.pais;
            it.idade = j.idade(ano);
            it.jogos = j.jogosTemp;
            it.gols = j.golsTemp;
            it.assistencias = j.assistTemp;
            it.nota = Math.round(j.notaMediaTemp() * 100) / 100.0;
            it.valor = Math.round(valorPremio(j) * 100) / 100.0;
            r.itens.add(it);
        }
        return r;
    }

    /** Vagas da seleção da liga (4-3-3). */
    static final Posicao[] VAGAS_SELECAO_LIGA = {Posicao.GOL, Posicao.LD, Posicao.ZAG, Posicao.ZAG, Posicao.LE, Posicao.VOL, Posicao.MC,
            Posicao.MEI, Posicao.PD, Posicao.CA, Posicao.PE};

    /**
     * Seleção da liga ao terminar uma liga nacional (1ª e 2ª divisão dos países detalhados e a do
     * técnico): os melhores de cada vaga pela nota da temporada, entre quem jogou bastante.
     */
    void selecaoDaLiga(Torneio t) {
        if (!"nacional".equals(t.ambito) || t.divisao == null) return;
        boolean doHumano = torneioDoHumano(t);
        Pais p = m.pais(t.pais);
        if (!doHumano && (t.divisao > 2 || (p != null && !p.detalhado))) return;
        List<Jogador> cand = new ArrayList<>();
        int maxJogos = 0;
        for (String id : t.participantes) {
            Clube c = m.clube(id);
            if (c == null || c.virtual) continue;
            for (int jid : c.elenco) {
                Jogador j = m.jogador(jid);
                if (j == null || j.amador()) continue;
                maxJogos = Math.max(maxJogos, j.jogosTemp);
                cand.add(j);
            }
        }
        int minimo = Math.max(3, (int) (maxJogos * 0.5));
        cand.removeIf(j -> j.jogosTemp < minimo);
        if (cand.size() < 11) return;
        cand.sort(Comparator.comparingDouble((Jogador j) -> -(j.notaMediaTemp() + j.golsTemp * 0.04 + j.assistTemp * 0.02)).thenComparingInt(j -> j.id));
        Historia.SelecaoDaLiga s = new Historia.SelecaoDaLiga();
        s.ano = t.ano;
        s.temporada = t.temporada;
        s.competicaoId = t.id;
        s.competicao = t.nome;
        s.pais = t.pais;
        s.divisao = t.divisao;
        Set<Integer> usados = new HashSet<>();
        for (Posicao vaga : VAGAS_SELECAO_LIGA) {
            Jogador escolhido = null;
            for (Jogador j : cand) if (!usados.contains(j.id) && j.posicao == vaga) { escolhido = j; break; }
            if (escolhido == null) for (Jogador j : cand) if (!usados.contains(j.id) && j.posicao.setor == vaga.setor && (vaga == Posicao.GOL) == (j.posicao == Posicao.GOL)) { escolhido = j; break; }
            if (escolhido == null) continue;
            usados.add(escolhido.id);
            Historia.ItemSelecao it = new Historia.ItemSelecao();
            it.posicao = vaga.name();
            it.jogadorId = escolhido.id;
            it.jogador = escolhido.nomeExibicao();
            Clube c = m.clube(escolhido.clubeId);
            it.clubeId = c == null ? null : c.id;
            it.clube = c == null ? null : c.nome;
            it.jogos = escolhido.jogosTemp;
            it.gols = escolhido.golsTemp;
            it.assistencias = escolhido.assistTemp;
            it.nota = Math.round(escolhido.notaMediaTemp() * 100) / 100.0;
            s.time.add(it);
        }
        m.historia.selecoesDaLiga.add(s);
    }

    private void premio(int ano, String tipo, String titulo, Jogador j, double valor) {
        Historia.Premio p = new Historia.Premio();
        p.ano = ano;
        p.tipo = tipo;
        p.titulo = titulo;
        p.jogadorId = j.id;
        p.jogador = j.nomeExibicao();
        Clube c = m.clube(j.clubeId);
        p.clube = c == null ? null : c.nome;
        p.valor = Math.round(valor * 100) / 100.0;
        m.historia.premios.add(p);
        if (!"revelacao".equals(tipo)) {
            if (j.estrela && !j.topMundial && ("bola_de_ouro".equals(tipo) || j.fama >= 70)) {
                j.topMundial = true;
                m.historia.evento(ano, 52, "estrela", j.nomeExibicao() + " vira Top Mundial (" + titulo.toLowerCase() + ").");
            } else if (!j.estrela) {
                m.historia.evento(ano, 52, "estrela", j.nomeExibicao() + " vira estrela (" + titulo.toLowerCase() + ").");
            }
            j.estrela = true;
            j.fama = Math.min(100, j.fama + 10);
        } else {
            j.fama = Math.min(100, j.fama + 4);
        }
    }

    private void fimDeTemporadaClubes(Set<String> paises, int ano) {
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe || !paises.contains(c.pais)) continue;
            double alvo = c.reputacaoInicial + switch (c.divisao) {
                case 1 -> 4;
                case 2 -> -6;
                case 3 -> -12;
                case 4 -> -16;
                default -> -20;
            } * (c.divisao == 0 ? 1 : 0.7);
            c.reputacao = Math.max(3, Math.min(100, c.reputacao + 0.08 * (alvo - c.reputacao)));
            c.torcida = Math.max(3, Math.min(100, c.torcida + 0.08 * (c.reputacao - c.torcida)));
            c.estrutura = Math.max(5, c.estrutura - 0.5);
            long tv = Financas.tvPatrocinio(c, ano);
            if (!c.humano && c.dinheiro > tv * 0.5) {
                long inv = Financas.arred(c.dinheiro * 0.1);
                c.estrutura = Math.min(100, c.estrutura + Math.min(5, inv / (Math.max(1, tv) * 0.1)));
                c.dinheiro -= inv;
                c.financas.obras += inv;
            }
            if (!c.humano) reajusteDosRicos(c, ano);
            long limite = Financas.limiteEmprestimo(c, ano);
            c.receitaUltimaTemporada = c.financas.receitas() - c.financas.vendas;
            if (!c.humano && (c.dinheiro < 0 || c.divida > limite * 0.6)) cortarCustos(c, ano);
            if (c.dinheiro < -limite && c.divida >= limite * 0.95) c.temporadasNoVermelho++;
            else c.temporadasNoVermelho = 0;
            if (c.temporadasNoVermelho >= 3) falir(c, ano);
            c.financasAnterior = c.financas;
            c.financas = new FinancasClube();
            c.financas.saldoInicio = c.dinheiro;
        }
    }


    /**
     * Clube da IA com caixa sobrando (acima de CAIXA_TETO × receita) e folha abaixo da FOLHA_ALVO
     * reinveste em salários: sobe a escala salarial e reajusta os contratos (até REAJUSTE_RICO por ano,
     * sem passar da folha alvo). Balanço 2026-11, rodada 2: a folha da Premier League caía para ~0,27
     * da receita e o caixa mediano chegava a 2,3 receitas anuais (o excedente não tinha para onde ir).
     */
    void reajusteDosRicos(Clube c, int ano) {
        double e = Financas.receitaReferencia(c, ano);
        if (e <= 0 || c.dinheiro <= Balanceamento.CAIXA_TETO * e) return;
        long folha = 0;
        for (int id : c.elenco) {
            Jogador j = m.jogador(id);
            if (j != null && j.contrato != null) folha += j.contrato.salario;
        }
        double razao = folha * 12 / e;
        if (razao <= 0 || razao >= Balanceamento.FOLHA_ALVO) return;
        double passo = Math.min(Balanceamento.REAJUSTE_RICO, Balanceamento.FOLHA_ALVO / razao - 1);
        if (c.fatorSalarial > 0) c.fatorSalarial *= 1 + passo;
        for (int id : c.elenco) {
            Jogador j = m.jogador(id);
            if (j != null && j.contrato != null && j.contrato.tipo == TipoContrato.PROFISSIONAL && !j.contrato.teste())
                j.contrato.salario = Financas.arred(j.contrato.salario * (1 + passo));
        }
    }
    private void cortarCustos(Clube c, int ano) {
        long receita = Math.max(1, Math.max((long) Financas.receitaReferencia(c, ano), c.financas.receitas() - c.financas.vendas));
        List<Jogador> l = new ArrayList<>();
        for (int id : c.elenco) {
            Jogador j = m.jogador(id);
            if (j.contrato != null && j.contrato.tipo == TipoContrato.PROFISSIONAL) l.add(j);
        }
        l.sort(Comparator.comparingLong((Jogador j) -> -j.contrato.salario).thenComparingInt(j -> j.id));
        long folha = 0;
        for (Jogador j : l) folha += j.contrato.salario;
        int i = 0;
        while (folha * 12 > receita * Balanceamento.FOLHA_ALVO && i < l.size() && c.elenco.size() > 18) {
            Jogador j = l.get(i++);
            folha -= j.contrato.salario;
            if (Financas.valorMercado(j, c, ano) > 0 && m.rng.chance(0.5)) {
                j.aVenda = true;
                j.precoPedido = Financas.arred(Financas.valorMercado(j, c, ano) * 0.8);
            } else {
                mercado.liberar(j, c);
            }
        }
        Pais p = m.pais(c.pais);
        if (i > 0 && (p == null || p.detalhado)) m.historia.evento(ano, m.semana, "crise", "Crise financeira no " + c.nome + ": o clube corta salários.");
    }

    private void falir(Clube c, int ano) {
        c.falencias++;
        c.temporadasNoVermelho = 0;
        for (int id : new ArrayList<>(c.elenco)) {
            Jogador j = m.jogador(id);
            if (j.contrato != null && j.contrato.tipo == TipoContrato.EMPRESTIMO) mercado.devolverEmprestado(j);
            else if (c.elenco.size() > 18) mercado.liberar(j, c);
        }
        c.reputacao *= 0.6;
        c.torcida *= 0.8;
        c.estrutura *= 0.5;
        c.dinheiro = 0;
        c.divida = 0;
        c.rebaixamentoForcado = true;
        m.historia.estat(ano).falencias++;
        m.historia.evento(ano, m.semana, "falencia", "O " + c.nome + " decreta falência e recomeça do zero.");
        if (c.humano) m.mensagemDoClube(c.id, "financas", "Falência", "O " + c.nome + " decretou falência. O elenco foi desfeito e o clube recomeça sem divisão nacional.");
    }

    private void estatisticasDoAno(Historia.EstatAno est) {
        int clubes = 0, soma = 0;
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe || c.divisao == 0) continue;
            clubes++;
            soma += c.elenco.size();
        }
        est.elencoMedio = clubes == 0 ? 0 : Math.round(10.0 * soma / clubes) / 10.0;
        int ativos = 0;
        for (Jogador j : m.jogadores) if (!j.amador() && j.status == StatusJogador.PROFISSIONAL) ativos++;
        est.jogadoresAtivos = ativos;
        int k = 0;
        for (LigaKings l : m.kings) if (l.ativa) k++;
        est.kingsLigasAtivas = k;
    }

    private void promoverBase(Set<String> paises) {
        Set<Integer> saem = new HashSet<>();
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe || !paises.contains(c.pais)) continue;
            for (int id : new ArrayList<>(c.base)) {
                Jogador j = m.jogador(id);
                int idade = j.idade(m.ano);
                if (c.humano && c.sub20Montado) {
                    // Base -> Sub-20 -> Profissional: o técnico decide; só quem passa da idade do Sub-20 sobe
                    if (idade >= 20) {
                        c.base.remove(Integer.valueOf(id));
                        j.noSub20 = false;
                        j.status = StatusJogador.PROFISSIONAL;
                        if (j.contrato == null || j.contrato.tipo == TipoContrato.BASE)
                            j.contrato = Contrato.profissional(Financas.salarioPedido(j, c, m.ano), Populador.fimDeTemporada(c, Calendario.absoluta(m.ano + 2, 52)),
                                    Financas.multaRescisoria(j, c, m.ano));
                        c.elenco.add(id);
                        m.mensagemDoClube(c.id, "jogador", "Promovido do Sub-20", j.nomeExibicao() + " (" + idade + " anos, força " + j.forcaExibida()
                                + ") passou da idade do Sub-20 e sobe para o profissional.");
                    }
                    continue;
                }
                boolean pronto = idade >= 18 && (j.forca >= c.nivel() - 22 || c.elenco.size() < Balanceamento.ELENCO_MINIMO_IA || idade >= 19);
                // com o Sub-20 de 23, a IA só sobe quem cabe no elenco (ou quem já é do nível do time)
                if (pronto && c.sub20Montado && c.elenco.size() >= Balanceamento.ELENCO_ALVO && j.forca < c.nivel() - 10) pronto = false;
                if (pronto && (j.forca >= c.nivel() - 28 || c.humano || c.elenco.size() < Balanceamento.ELENCO_MINIMO_IA)) {
                    c.base.remove(Integer.valueOf(id));
                    j.status = StatusJogador.PROFISSIONAL;
                    j.contrato = Contrato.profissional(Financas.salarioPedido(j, c, m.ano), Populador.fimDeTemporada(c, Calendario.absoluta(m.ano + 2, 52)),
                            Financas.multaRescisoria(j, c, m.ano));
                    c.elenco.add(id);
                    if (c.humano) m.mensagemDoClube(c.id, "jogador", "Promovido da base", j.nomeExibicao() + " (" + idade + " anos, força " + j.forcaExibida() + ") sobe para o profissional.");
                } else if (idade >= 20) {
                    // júnior gerado do Sub-20 que nunca jogou no profissional: deixa o futebol (não lota o mercado)
                    if (c.sub20Montado && j.origem == Origem.GERADO && j.jogosCarreira == 0) saem.add(id);
                    else mercado.liberar(j, c);
                }
            }
        }
        sub20.sumir(saem);
    }

    // ======================================================================= amistosos
    /**
     * Amistoso do clube do humano contra outro clube, na próxima semana sem jogo dele (Brasfoot,
     * F9): renda toda do mandante; não conta para nada além de moral, finanças e ritmo.
     */
    /** Depois do jogo de despedida: registra na história e o jogador se aposenta. */
    private void despedida(Torneio t, ResultadoPartida r, Clube cm, Clube cv) {
        Jogador j = m.ativo(t.despedidaJogadorId);
        if (j == null || j.status == StatusJogador.APOSENTADO) return;
        Clube dele = m.clube(j.clubeId), adv = dele == cm ? cv : cm;
        Historia.Despedida d = new Historia.Despedida();
        d.ano = m.ano;
        d.semana = m.semana;
        d.jogadorId = j.id;
        d.jogador = j.nomeExibicao();
        d.clubeId = dele == null ? null : dele.id;
        d.clube = dele == null ? null : dele.nome;
        d.adversarioId = adv.id;
        d.adversario = adv.nome;
        d.placar = r.placar();
        m.historia.despedidas.add(d);
        int gols = 0;
        for (int[] g : r.gols) if (g[1] == j.id && g[4] == 0) gols++;
        m.historia.evento(m.ano, m.semana, "despedida", "Jogo de despedida de " + j.nomeExibicao() + ": " + r.placar()
                + (gols > 0 ? " (" + gols + " gol" + (gols > 1 ? "s" : "") + " do homenageado)" : "") + ".");
        aposentar(j, m.ano);
    }

    /** Próximas {@code n} semanas livres para todos os clubes dados (a partir da semana que vem). */
    private List<Integer> semanasLivres(List<String> clubes, int n) {
        Set<Integer> ocupadas = new HashSet<>();
        for (Torneio t : m.torneios) for (Partida p : t.partidas) {
            if (p.jogada) continue;
            for (String c : clubes) if (p.envolve(c)) ocupadas.add(p.sa);
        }
        List<Integer> l = new ArrayList<>();
        for (int sa = m.semanaAbsoluta() + 1; l.size() < n && sa < m.semanaAbsoluta() + 60; sa++) if (!ocupadas.contains(sa)) l.add(sa);
        return l;
    }

    private Torneio novoAmistoso(String nome, List<String> participantes, List<Integer> semanas, String tipoFase) {
        Clube c = m.clube(participantes.get(0));
        Torneio t = new Torneio();
        t.id = "amistoso-" + m.proximoAmistoso++;
        t.nome = nome;
        t.ambito = "amistoso";
        t.calendario = c.calendario;
        t.temporada = String.valueOf(Calendario.ano(semanas.get(0)));
        t.ano = Calendario.ano(semanas.get(0));
        t.pais = c.pais;
        t.semente = m.semente + t.id.hashCode();
        br.futebas.dados.Formato f = new br.futebas.dados.Formato();
        br.futebas.dados.Formato.Fase fa = new br.futebas.dados.Formato.Fase(participantes.size() == 2 ? "Jogo único" : "Turno único", tipoFase);
        fa.turnos = 1;
        f.fases = new ArrayList<>(List.of(fa));
        t.formato = f;
        t.participantes = new ArrayList<>(participantes);
        t.semanaInicio = semanas.get(0);
        t.semanaFim = semanas.get(semanas.size() - 1);
        t.semanasJogo = new ArrayList<>(semanas);
        t.planejar();
        m.torneios.add(t);
        return t;
    }

    /**
     * Torneio amistoso (quadrangular/triangular de pré-temporada): o seu clube e 2 ou 3
     * convidados, turno único em semanas livres de todos. O campeão vai para a história.
     */
    public Torneio marcarTorneioAmistoso(String nome, List<String> convidados) {
        Clube c = m.clubeHumano();
        if (c == null || convidados == null) return null;
        List<String> part = new ArrayList<>(List.of(c.id));
        for (String id : convidados) {
            Clube x = m.clube(id);
            if (x != null && !x.virtual && x != c && !part.contains(id)) part.add(id);
        }
        if (part.size() < 3 || part.size() > 4) return null;
        int datas = part.size() == 4 ? 3 : 3;
        List<Integer> semanas = semanasLivres(part, datas);
        if (semanas.size() < datas) return null;
        Torneio t = novoAmistoso(nome == null || nome.isBlank() ? (part.size() == 4 ? "Quadrangular amistoso" : "Triangular amistoso") : nome,
                part, semanas, "pontos_corridos");
        m.mensagem("diretoria", "Torneio amistoso", t.nome + " marcado: " + part.size() + " clubes, a partir de "
                + Calendario.texto(Calendario.diaDeJogo(semanas.get(0), 0)) + ".");
        return t;
    }

    /**
     * Jogo de despedida de um jogador do seu clube (30 anos ou mais): amistoso em casa contra o
     * convidado; ele joga como titular e se aposenta logo depois. Fica na história.
     */
    public Torneio marcarJogoDeDespedida(int jogadorId, String adversarioId) {
        Clube c = m.clubeHumano(), adv = m.clube(adversarioId);
        Jogador j = m.ativo(jogadorId);
        if (c == null || adv == null || adv == c || adv.virtual || j == null || !c.id.equals(j.clubeId) || j.idade(m.ano) < 30) return null;
        List<Integer> semanas = semanasLivres(List.of(c.id, adv.id), 1);
        if (semanas.isEmpty()) return null;
        Torneio t = novoAmistoso("Despedida de " + j.nomeExibicao(), List.of(c.id, adv.id), semanas, "pontos_corridos");
        t.despedidaJogadorId = j.id;
        m.mensagem("diretoria", "Jogo de despedida", "Jogo de despedida de " + j.nomeExibicao() + " contra o " + adv.nome + " em "
                + Calendario.texto(Calendario.diaDeJogo(semanas.get(0), 0)) + ".");
        return t;
    }

    public Torneio marcarAmistoso(String adversarioId, boolean emCasa) {
        Clube c = m.clubeHumano(), adv = m.clube(adversarioId);
        if (c == null || adv == null || adv == c || adv.virtual) return null;
        int sa = m.semanaAbsoluta() + 1;
        Set<Integer> ocupadas = new HashSet<>();
        for (Torneio t : m.torneios) for (Partida p : t.partidas) if (!p.jogada && (p.envolve(c.id) || p.envolve(adv.id))) ocupadas.add(p.sa);
        while (ocupadas.contains(sa)) sa++;
        Torneio t = new Torneio();
        t.id = "amistoso-" + m.proximoAmistoso++;
        t.nome = "Amistoso";
        t.ambito = "amistoso";
        t.calendario = c.calendario;
        t.temporada = String.valueOf(Calendario.ano(sa));
        t.ano = Calendario.ano(sa);
        t.pais = c.pais;
        t.semente = m.semente + t.id.hashCode();
        br.futebas.dados.Formato f = new br.futebas.dados.Formato();
        br.futebas.dados.Formato.Fase fa = new br.futebas.dados.Formato.Fase("Jogo único", "pontos_corridos");
        fa.turnos = 1;
        f.fases = new ArrayList<>(List.of(fa));
        t.formato = f;
        t.participantes = new ArrayList<>(emCasa ? List.of(c.id, adv.id) : List.of(adv.id, c.id));
        t.semanaInicio = sa;
        t.semanaFim = sa;
        t.semanasJogo = new ArrayList<>(List.of(sa));
        t.planejar();
        m.torneios.add(t);
        m.mensagem("diretoria", "Amistoso marcado", "Amistoso contra o " + adv.nome + " em " + Calendario.texto(Calendario.diaDeJogo(sa, 0)) + ".");
        return t;
    }

    // ======================================================================= consultas
    public List<Jogador> elenco(String clubeId) {
        Clube c = m.clube(clubeId);
        List<Jogador> l = new ArrayList<>();
        if (c == null) return l;
        for (int id : c.elenco) l.add(m.jogador(id));
        l.sort(Comparator.comparingInt((Jogador j) -> j.posicao.ordinal()).thenComparingDouble(j -> -j.forca));
        return l;
    }

    public List<Jogador> base(String clubeId) {
        Clube c = m.clube(clubeId);
        List<Jogador> l = new ArrayList<>();
        if (c == null) return l;
        for (int id : c.base) l.add(m.jogador(id));
        l.sort(Comparator.comparingDouble((Jogador j) -> -j.forca));
        return l;
    }
}
