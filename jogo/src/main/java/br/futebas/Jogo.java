package br.futebas;

import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.financas.Financas;
import br.futebas.historia.Historia;
import br.futebas.ia.PropostaJogador;
import br.futebas.ia.PropostaTecnico;
import br.futebas.kings.LigaKings;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Contrato;
import br.futebas.mundo.FinancasClube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mensagem;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Origem;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.Salvamento;
import br.futebas.mundo.Simulador;
import br.futebas.mundo.StatusJogador;
import br.futebas.mundo.TipoContrato;
import br.futebas.partida.Concentracao;
import br.futebas.partida.Estilo;
import br.futebas.partida.Marcacao;
import br.futebas.partida.Formacoes;
import br.futebas.partida.ResultadoPartida;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fachada do motor para a interface (pacote {@code ui}). Tudo o que a tela precisa passa por
 * aqui: criar/carregar/salvar, avançar o tempo, consultar tabelas, elencos, finanças,
 * mensagens e história, e as ações do técnico (escalação, tática, treino, mercado, Kings).
 * Os objetos devolvidos (Clube, Jogador, Torneio...) são o estado vivo do mundo: a interface
 * só deve LER deles e usar os métodos desta classe para mudar algo.
 */
public final class Jogo {
    private final Simulador sim;
    private final Mundo m;

    private Jogo(Simulador sim) {
        this.sim = sim;
        this.m = sim.m;
        // camisa do avatar dos atletas da Kings que só têm nome (o jogo aberto mais recente)
        br.futebas.mundo.PerfilFoto.coresKings = id -> {
            LigaKings.TimeKings t = id == null || sim.kings == null ? null : sim.kings.timeKings(id);
            return t == null ? null : new String[]{t.cor1, t.cor2};
        };
    }

    // =================================================================== criar / carregar / salvar
    public static BancoMundo abrirBanco(Path banco) {
        return BancoMundo.carregar(banco);
    }

    /** Novo jogo em 1º/jan/2026. {@code clubeId} null = modo "só assistir". */
    public static Jogo novoJogo(BancoMundo db, long semente, String clubeId, String nomeTecnico) {
        return new Jogo(Simulador.novo(db, semente, clubeId, nomeTecnico));
    }

    /** Novo jogo com os times customizados (Kings League IOS) quando {@code custom} não é null. */
    public static Jogo novoJogo(BancoMundo db, long semente, String clubeId, String nomeTecnico, br.futebas.kings.KingsCustom.Definicao custom) {
        return new Jogo(Simulador.novo(db, semente, clubeId, nomeTecnico, custom));
    }

    public static Jogo novoJogo(Path banco, long semente, String clubeId, String nomeTecnico) {
        return novoJogo(BancoMundo.carregar(banco), semente, clubeId, nomeTecnico);
    }

    /** Sinônimo de {@link #novoJogo(BancoMundo, long, String, String)}. */
    public static Jogo novo(BancoMundo db, long semente, String clubeId, String nomeTecnico) {
        return novoJogo(db, semente, clubeId, nomeTecnico);
    }

    // =================================================================== multi-treinador (hot-seat)
    /** Limite de técnicos humanos num jogo multi-treinador. */
    public static final int MAX_TECNICOS = 2; // regra do jogo: no máximo 2 técnicos

    /** Um técnico do jogo novo: nome e clube OU time da Kings (carreira exclusiva, regra do jogo). */
    public record EscolhaTecnico(String nome, String clubeId, String kingsTimeId) {}

    /**
     * Jogo novo com vários técnicos humanos no mesmo computador (2 a {@link #MAX_TECNICOS}), na ordem
     * das vezes. Com uma escolha só, é o jogo normal de um técnico.
     */
    public static Jogo novoJogoMulti(BancoMundo db, long semente, List<EscolhaTecnico> escolhas, br.futebas.kings.KingsCustom.Definicao custom) {
        if (escolhas == null || escolhas.isEmpty()) throw new IllegalArgumentException("Escolha pelo menos um técnico.");
        if (escolhas.size() > MAX_TECNICOS) throw new IllegalArgumentException("No máximo " + MAX_TECNICOS + " técnicos.");
        java.util.Set<String> usados = new java.util.HashSet<>();
        for (EscolhaTecnico e : escolhas) {
            String id = e.kingsTimeId() != null ? e.kingsTimeId() : e.clubeId();
            if (id == null) throw new IllegalArgumentException("Técnico sem clube: " + e.nome());
            if (!usados.add(id)) throw new IllegalArgumentException("Dois técnicos no mesmo time: " + id);
        }
        EscolhaTecnico e0 = escolhas.get(0);
        Jogo j = e0.kingsTimeId() != null ? novoJogoKings(db, semente, null, e0.kingsTimeId(), e0.nome(), custom)
                : novoJogo(db, semente, e0.clubeId(), e0.nome(), custom);
        if (escolhas.size() == 1) return j;
        Mundo m = j.m;
        m.tecnicos = new ArrayList<>(List.of(m.tecnico));
        m.vez = 0;
        for (int i = 1; i < escolhas.size(); i++) {
            EscolhaTecnico e = escolhas.get(i);
            br.futebas.mundo.Tecnico t = new br.futebas.mundo.Tecnico(e.nome() == null || e.nome().isBlank() ? "Técnico " + (i + 1) : e.nome().trim());
            m.tecnicos.add(t);
            m.ativar(i);
            if (e.kingsTimeId() != null) {
                t.reputacao = 25;
                if (!j.assumirTimeKings(e.kingsTimeId())) throw new IllegalArgumentException("Time da Kings inválido: " + e.kingsTimeId());
            } else {
                Clube c = m.clube(e.clubeId());
                if (c == null || c.virtual || c.humano) throw new IllegalArgumentException("Clube inválido para o técnico: " + e.clubeId());
                t.clubeId = c.id;
                t.reputacao = Math.max(15, c.reputacao * 0.7);
                c.humano = true;
                m.mensagem("diretoria", "Bem-vindo", "Bem-vindo ao " + c.nome + ". A diretoria deseja uma boa temporada.");
            }
        }
        m.ativar(0);
        return j;
    }

    /** Jogo com mais de um técnico humano (hot-seat)? */
    public boolean multi() { return m.multi(); }

    /** Os técnicos humanos na ordem das vezes (um técnico só: ele). */
    public List<br.futebas.mundo.Tecnico> tecnicos() { return m.todosTecnicos(); }

    /** Índice do técnico da vez (0 no jogo de um técnico só). */
    public int vez() { return m.multi() ? m.vez : 0; }

    /** Passa a vez ao técnico {@code i}: caixa, convites, finanças e telas passam a ser as dele. */
    public void passarVez(int i) { m.ativar(i); }

    /** Tela dividida do modo multi-treinador (null = sem tela). */
    public void definirAoVivoMulti(br.futebas.partida.AoVivoMulti aoVivo) {
        sim.aoVivoMulti = aoVivo;
        // a mesma tela acompanha os jogos da Kings dos técnicos (se souber)
        sim.kings.aoVivoMulti = aoVivo instanceof br.futebas.kings.AoVivoKingsMulti k ? k : null;
    }

    // =================================================================== escolha do clube (país → liga → clube)
    public record OpcaoPais(String id, String nome, String confederacao, String calendario, String bandeiraPng, int clubes) {}

    public record OpcaoLiga(String id, String nome, String ambito, Integer divisao, String uf, int clubes) {}

    public record OpcaoClube(String id, String nome, String sigla, String pais, String uf, double reputacao, Integer divisao,
                             String escudoPng, String cor1, String cor2) {}

    /** Países com clubes jogáveis (Brasil primeiro, depois pelo ranking FIFA e pelo nome). */
    public static List<OpcaoPais> paises(BancoMundo db) {
        java.util.Map<String, Integer> n = new java.util.HashMap<>();
        for (Db.Clube c : db.clubes.values()) n.merge(c.pais(), 1, Integer::sum);
        List<OpcaoPais> l = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : n.entrySet()) {
            Db.Pais p = db.pais(e.getKey());
            l.add(new OpcaoPais(e.getKey(), p != null && p.nome() != null ? p.nome() : e.getKey(), p == null ? null : p.confederacao(),
                    p == null ? "ano" : p.calendario(), p == null ? null : p.bandeiraPng(), e.getValue()));
        }
        l.sort(Comparator.comparingInt((OpcaoPais o) -> "BRA".equals(o.id()) ? 0 : 1)
                .thenComparingInt(o -> db.pais(o.id()) != null && db.pais(o.id()).rankingFifa() != null ? db.pais(o.id()).rankingFifa() : 999)
                .thenComparing(OpcaoPais::nome));
        return l;
    }

    /** Ligas (divisões e estaduais) de um país, na ordem: divisões, depois estaduais. */
    public static List<OpcaoLiga> ligas(BancoMundo db, String pais) {
        List<OpcaoLiga> l = new ArrayList<>();
        for (Db.Competicao c : db.competicoes.values()) {
            if (!pais.equals(c.pais())) continue;
            boolean liga = "nacional".equals(c.ambito()) && c.divisao() != null;
            if (!liga && !"estadual".equals(c.ambito())) continue;
            l.add(new OpcaoLiga(c.id(), c.nome(), c.ambito(), c.divisao(), c.uf(), db.participantesIniciais(c.id()).size()));
        }
        l.sort(Comparator.comparingInt((OpcaoLiga o) -> o.divisao() == null ? 99 : o.divisao()).thenComparing(OpcaoLiga::nome));
        return l;
    }

    /** Clubes de uma liga na temporada inicial, dos maiores para os menores. */
    public static List<OpcaoClube> clubes(BancoMundo db, String ligaId) {
        Db.Competicao comp = db.competicoes.get(ligaId);
        List<OpcaoClube> l = new ArrayList<>();
        for (String id : db.participantesIniciais(ligaId)) {
            Db.Clube c = db.clubes.get(id);
            if (c != null) l.add(opcao(db, c, comp != null && "nacional".equals(comp.ambito()) ? comp.divisao() : divisaoInicial(db, id)));
        }
        l.sort(Comparator.comparingDouble((OpcaoClube o) -> -o.reputacao()).thenComparing(OpcaoClube::nome));
        return l;
    }

    /** Todos os clubes de um país (inclui os sem divisão nacional). */
    public static List<OpcaoClube> clubesDoPais(BancoMundo db, String pais) {
        List<OpcaoClube> l = new ArrayList<>();
        for (Db.Clube c : db.clubes.values()) if (pais.equals(c.pais())) l.add(opcao(db, c, divisaoInicial(db, c.id())));
        l.sort(Comparator.comparingDouble((OpcaoClube o) -> -o.reputacao()).thenComparing(OpcaoClube::nome));
        return l;
    }

    /** Todos os clubes que disputam alguma divisão nacional em 2026 (para atalhos e testes). */
    public static List<OpcaoClube> clubesDisponiveis(BancoMundo db) {
        List<OpcaoClube> l = new ArrayList<>();
        for (Db.Clube c : db.clubes.values()) {
            Integer d = divisaoInicial(db, c.id());
            if (d != null) l.add(opcao(db, c, d));
        }
        l.sort(Comparator.comparingDouble((OpcaoClube o) -> -o.reputacao()).thenComparing(OpcaoClube::nome));
        return l;
    }

    private static OpcaoClube opcao(BancoMundo db, Db.Clube c, Integer div) {
        return new OpcaoClube(c.id(), c.nome(), c.sigla(), c.pais(), c.uf(), c.reputacao() == null ? 0 : c.reputacao(), div,
                c.escudoPng(), c.cor1(), c.cor2());
    }

    private static Integer divisaoInicial(BancoMundo db, String clubeId) {
        for (Db.Competicao comp : db.competicoes.values()) {
            if (comp.divisao() != null && "nacional".equals(comp.ambito()) && db.participantesIniciais(comp.id()).contains(clubeId)) return comp.divisao();
        }
        return null;
    }

    public static Path pastaSaves() {
        return Salvamento.pastaPadrao();
    }

    public Path salvar(String nome) {
        return Salvamento.salvar(m, pastaSaves().resolve(nome + Salvamento.EXTENSAO));
    }

    public Path salvar(Path arquivo) {
        return Salvamento.salvar(m, arquivo);
    }

    /** Carrega um jogo salvo. {@code banco} null = usa o caminho gravado no arquivo. */
    public static Jogo carregar(Path arquivo, Path banco) {
        Mundo mundo = Salvamento.carregar(arquivo);
        Path b = banco != null ? banco : Paths.get(mundo.banco);
        return new Jogo(Simulador.retomar(mundo, BancoMundo.carregar(b))).carreiraExclusiva();
    }

    public static Jogo carregar(Path arquivo, BancoMundo db) {
        return new Jogo(Simulador.retomar(Salvamento.carregar(arquivo), db)).carreiraExclusiva();
    }

    /**
     * Regra do jogo: ou você treina um clube, ou um time da Kings League, nunca os dois. Saves
     * antigos com os dois ficam com o CLUBE; o time da Kings volta para a IA (aviso na caixa).
     */
    Jogo carreiraExclusiva() {
        if (m.tecnico == null || m.tecnico.kingsTimeId == null || m.clubeHumano() == null) return this;
        LigaKings.TimeKings t = sim.kings.timeKings(m.tecnico.kingsTimeId);
        String nome = t == null ? m.tecnico.kingsTimeId : t.nome;
        deixarTimeKings();
        m.mensagem("kings", "Kings League", "Agora o técnico comanda só um time: você segue no " + m.clubeHumano().nome
                + " e deixou o " + nome + " (Kings League).");
        return this;
    }

    // =================================================================== tempo
    public record ResumoSemana(int ano, int semana, List<Partida> jogosDoUsuario, List<ResultadoPartida> narradas,
                               int novasMensagens, boolean novaTemporada) {}

    /** Joga a semana atual (todas as competições) e passa para a próxima. */
    /**
     * Kings: joga o jogo do seu time adiado de uma rodada dupla, sem avançar a semana (cada jogo com a
     * sua tela de fim). null se não havia jogo adiado.
     */
    public ResumoSemana jogarAdiadoKings() {
        if (!sim.kings.jogarAdiado()) return null;
        return new ResumoSemana(m.ano, m.semana, new ArrayList<>(sim.kings.jogosDoHumanoNaSemana), new ArrayList<>(sim.kings.narradasNaSemana), 0, false);
    }

    /** Há jogo do seu time da Kings adiado (rodada dupla) esperando o "Jogar"? */
    public boolean temJogoAdiadoKings() { return sim.kings.jogoAdiado() != null; }

    public ResumoSemana avancarSemana() {
        int ano = m.ano, semana = m.semana, msgs = m.mensagens.size();
        List<Partida> jogos = sim.avancarSemana();
        // mensagem esquecida há mais de 2 semanas vira lida sozinha (regra do jogo)
        int agoraAbs = br.futebas.mundo.Calendario.absoluta(m.ano, m.semana);
        for (br.futebas.mundo.Mensagem x : m.mensagens)
            if (!x.lida && agoraAbs - br.futebas.mundo.Calendario.absoluta(x.ano, x.semana) > 2) x.lida = true;
        return new ResumoSemana(ano, semana, jogos, new ArrayList<>(sim.narradasDaSemana), m.mensagens.size() - msgs, m.ano != ano);
    }

    /** Avança até o próximo jogo do clube do técnico (ou uma semana, se não houver). */
    public ResumoSemana avancarAteProximoJogo() {
        ResumoSemana r;
        int limite = 60;
        do {
            r = avancarSemana();
        } while (r.jogosDoUsuario().isEmpty() && !r.novaTemporada() && --limite > 0);
        return r;
    }

    public void avancarAteFimDaTemporada() {
        sim.avancarTemporada();
    }

    public int ano() { return m.ano; }

    public int semana() { return m.semana; }

    public boolean soAssistindo() { return m.tecnico == null; }

    public Mundo mundo() { return m; }

    public Simulador simulador() { return sim; }

    public BancoMundo banco() { return sim.db; }

    // =================================================================== consultas
    public Clube clube(String id) { return m.clube(id); }

    public Clube meuClube() { return m.clubeHumano(); }

    public Jogador jogador(int id) { return m.jogador(id); }

    /**
     * Maiores artilheiros do seu mundo, ativos e aposentados (os arquivados vêm como esboço:
     * aposentado, sem clube, com a carreira). Para as telas de história.
     */
    public List<Jogador> artilheirosHistoricos(int limite) {
        List<Jogador> ativos = new ArrayList<>();
        for (Jogador x : m.jogadores) if (x.golsCarreira > 0 && !x.amador()) ativos.add(x);
        List<br.futebas.mundo.Arquivado> arq = new ArrayList<>();
        for (br.futebas.mundo.Arquivado a : m.arquivados) if (a.golsCarreira > 0 && a.origem != Origem.COMPLEMENTAR_TESTE) arq.add(a);
        java.util.Comparator<int[]> ordem = java.util.Comparator.comparingInt((int[] v) -> -v[0]).thenComparingInt(v -> v[1]);
        List<int[]> todos = new ArrayList<>(); // {gols, id, 0 ativo / 1 arquivado, índice}
        for (int i = 0; i < ativos.size(); i++) todos.add(new int[]{ativos.get(i).golsCarreira, ativos.get(i).id, 0, i});
        for (int i = 0; i < arq.size(); i++) todos.add(new int[]{arq.get(i).golsCarreira, arq.get(i).id, 1, i});
        todos.sort(ordem);
        List<Jogador> l = new ArrayList<>();
        for (int[] v : todos.subList(0, Math.min(Math.max(0, limite), todos.size()))) l.add(v[2] == 0 ? ativos.get(v[3]) : arq.get(v[3]).esboco());
        return l;
    }

    /** Aposentados arquivados (registros compactos), em ordem de id. Só leitura. */
    public List<br.futebas.mundo.Arquivado> aposentadosArquivados() { return java.util.Collections.unmodifiableList(m.arquivados); }

    /** Quantos jogadores já passaram pelo mundo (ativos + arquivados). */
    public int totalJogadoresNoMundo() { return m.totalJogadores(); }

    public List<Torneio> competicoesDoAno() { return m.torneios; }

    public Torneio competicao(String id) { return m.torneio(id); }

    /** Tabela da fase atual (grupo 0, ou o único grupo dos pontos corridos). */
    public List<LinhaTabela> tabela(String competicaoId) {
        Torneio t = m.torneio(competicaoId);
        if (t == null) return List.of();
        FaseExec f = t.faseAtual();
        if (f == null || f.mataMata()) {
            for (int i = t.fases.size() - 1; i >= 0; i--) if (!t.fases.get(i).mataMata() && t.fases.get(i).iniciada) return t.tabela(i, 0);
            return t.tabelaGeral();
        }
        return t.tabela(f.indice, 0);
    }

    public List<LinhaTabela> tabela(String competicaoId, int fase, int grupo) {
        Torneio t = m.torneio(competicaoId);
        return t == null ? List.of() : t.tabela(fase, grupo);
    }

    public List<Partida> partidas(String competicaoId) {
        Torneio t = m.torneio(competicaoId);
        return t == null ? List.of() : t.partidas;
    }

    /** Todas as partidas do clube nas competições em andamento, em ordem de data. */
    public List<Partida> calendarioDoClube(String clubeId) {
        // a barra do topo (próximo jogo) pede o calendário a cada painel aberto; percorrer todas as
        // partidas do ano custava alguns ms a cada vez com o banco completo. Guardado enquanto a
        // semana, a lista de torneios e o número de partidas não mudam (a Agenda só mexe nos dias
        // no começo da semana ou quando cria partidas).
        long chave = chaveCalendario();
        if (clubeId != null && clubeId.equals(calClube) && chave == calChave && calCache != null) return new ArrayList<>(calCache);
        List<Partida> l = new ArrayList<>();
        for (Torneio t : m.torneios) l.addAll(t.partidasDoClube(clubeId));
        l.sort(Comparator.comparingInt((Partida p) -> p.sa).thenComparingLong(p -> p.dia).thenComparingInt(p -> p.data));
        calClube = clubeId;
        calChave = chave;
        calCache = new ArrayList<>(l);
        return l;
    }

    private transient String calClube;
    private transient long calChave;
    private transient List<Partida> calCache;

    private long chaveCalendario() {
        long k = m.ano * 1_000_003L + m.semana;
        k = k * 31 + System.identityHashCode(m.torneios) + m.torneios.size();
        for (Torneio t : m.torneios) k = k * 31 + t.partidas.size() + System.identityHashCode(t);
        return k;
    }

    public List<Partida> proximasPartidas(String clubeId, int n) {
        List<Partida> l = new ArrayList<>();
        for (Partida p : calendarioDoClube(clubeId)) if (!p.jogada && l.size() < n) l.add(p);
        return l;
    }

    public List<Jogador> elenco(String clubeId) { return sim.elenco(clubeId); }

    public List<Jogador> base(String clubeId) { return sim.base(clubeId); }

    public record ResumoFinanceiro(long saldo, long divida, long limiteEmprestimo, long folhaMensal, FinancasClube temporada) {}

    public ResumoFinanceiro financas(String clubeId) {
        Clube c = m.clube(clubeId);
        List<Jogador> todos = new ArrayList<>(elenco(clubeId));
        todos.addAll(base(clubeId));
        return new ResumoFinanceiro(c.dinheiro, c.divida, Financas.limiteEmprestimo(c, m.ano), Financas.folhaMensal(todos), c.financas);
    }

    /** Valor de mercado do jogador no clube atual dele (Brasfoot: 4·F² × base do clube). */
    public long valorDeMercado(int jogadorId) {
        Jogador j = m.jogador(jogadorId);
        return Financas.valorMercado(j, m.clube(j.clubeId), m.ano);
    }

    /** Salário que o jogador pede para jogar no seu clube (ou no clube dele, se você não tem clube). */
    public long salarioPedido(int jogadorId) {
        Jogador j = m.jogador(jogadorId);
        Clube destino = m.clubeHumano() != null ? m.clubeHumano() : m.clube(j.clubeId);
        return Financas.salarioPedido(j, destino, m.ano);
    }

    /** Preço sugerido do ingresso de liga do seu clube (R$). */
    public long precoIngressoSugerido() {
        Clube c = m.clubeHumano();
        return c == null ? 0 : Financas.precoSugerido(c, m.ano);
    }

    /** Preço do ingresso de liga em vigor (o fixado ou o sugerido). */
    public long precoIngresso() {
        Clube c = m.clubeHumano();
        return c == null ? 0 : c.precoIngresso > 0 ? c.precoIngresso : Financas.precoSugerido(c, m.ano);
    }

    /**
     * Fixa o preço do ingresso (0 = automático, o sugerido). Acima do sugerido vem menos gente
     * (Brasfoot: cada unidade fora do sugerido muda o público em ~3%).
     */
    public void definirPrecoIngresso(long preco) {
        Clube c = m.clubeHumano();
        if (c != null) c.precoIngresso = Math.max(0, preco);
    }

    /** Público estimado do próximo jogo em casa na liga contra um adversário médio (para a tela de finanças). */
    public int publicoEstimado() {
        Clube c = m.clubeHumano();
        return c == null ? 0 : Financas.publico(c, null, null, m.ano);
    }

    public List<Mensagem> caixaDeEntrada() { return m.mensagens; }

    /**
     * Notícias (manchete {@code titulo} + linha fina {@code texto}, etiqueta {@code etiqueta()} e cor {@code cor()}):
     * o seu clube primeiro, depois as suas competições e a sua liga; do mundo, só os grandes acontecimentos.
     * Semana mais recente primeiro.
     */
    public List<Historia.Noticia> noticias(int limite) { return br.futebas.historia.Noticiario.ultimas(m.historia, limite); }

    /** Notícias de um clube (mais recentes primeiro). */
    public List<Historia.Noticia> noticiasDoClube(String clubeId, int limite) {
        List<Historia.Noticia> l = new ArrayList<>();
        for (Historia.Noticia n : br.futebas.historia.Noticiario.ultimas(m.historia, Integer.MAX_VALUE)) {
            if (l.size() >= limite) break;
            if (clubeId.equals(n.clubeId)) l.add(n);
        }
        return l;
    }

    public List<Mensagem> mensagensNaoLidas() {
        List<Mensagem> l = new ArrayList<>();
        for (Mensagem x : m.mensagens) if (!x.lida) l.add(x);
        return l;
    }

    public void marcarMensagensComoLidas() {
        for (Mensagem x : m.mensagens) x.lida = true;
    }

    public Historia historia() { return m.historia; }

    /** Data do próximo dia de jogo (início da semana atual + 5 dias). */
    public java.time.LocalDate dataAtual() {
        return br.futebas.mundo.Calendario.inicio(m.semanaAbsoluta());
    }

    // =================================================================== mundo: países, detalhe, assets
    public br.futebas.mundo.Pais pais(String id) { return m.pais(id); }

    public List<br.futebas.mundo.Pais> paisesDoMundo() { return new ArrayList<>(m.paises.values()); }

    /** Liga ou desliga a simulação lance a lance de um país (o resto do mundo usa o caminho rápido). */
    public void definirDetalhe(String pais, boolean detalhado) { sim.definirDetalhe(pais, detalhado); }

    /** Competições em andamento de um país (ligas, copa, estaduais). */
    public List<Torneio> competicoesDoPais(String pais) {
        List<Torneio> l = new ArrayList<>();
        for (Torneio t : m.torneios) if (pais.equals(t.pais)) l.add(t);
        l.sort(Comparator.comparingInt((Torneio t) -> t.divisao == null ? 99 : t.divisao).thenComparing(t -> t.id));
        return l;
    }

    /** Copas continentais em andamento (das duas temporadas). */
    public List<Torneio> continentais() {
        List<Torneio> l = new ArrayList<>();
        for (Torneio t : m.torneios) if ("continental".equals(t.ambito)) l.add(t);
        return l;
    }

    /** Caminho do escudo do clube (relativo à raiz do jogo; pode não existir: a tela mostra o padrão). */
    public String escudo(String clubeId) {
        Db.Clube c = sim.db.clubes.get(clubeId);
        if (c != null && c.escudoPng() != null) return c.escudoPng();
        return "assets/escudos/" + clubeId + ".png";
    }

    /**
     * Foto do jogador para {@code ui.comp.Foto.de}: uma chave de avatar ({@link br.futebas.mundo.PerfilFoto})
     * que leva a foto real (se houver) e, para quem não tem foto, o rosto gerado com a camisa do clube
     * (ou do time da Kings). null só para jogador inexistente (vaga vazia = silhueta).
     */
    public String foto(int jogadorId) {
        Jogador j = m.jogador(jogadorId);
        if (j == null) return null;
        String real = null;
        if (j.dbId != null) {
            Db.Jogador dj = sim.db.jogadores.get(j.dbId);
            real = dj != null && dj.fotoPng() != null ? dj.fotoPng() : "assets/fotos/" + j.dbId + ".png";
        }
        String c1 = null, c2 = null;
        Clube c = j.clubeId == null ? null : m.clube(j.clubeId);
        if (c != null) {
            c1 = c.cor1;
            c2 = c.cor2;
        } else if (j.kingsTimeId != null) {
            LigaKings.TimeKings tk = sim.kings.timeKings(j.kingsTimeId);
            if (tk != null) {
                c1 = tk.cor1;
                c2 = tk.cor2;
            }
        }
        return br.futebas.mundo.PerfilFoto.de(j, c1, c2, real).chave();
    }

    public String bandeira(String pais) {
        Db.Pais p = sim.db.pais(pais);
        return p != null && p.bandeiraPng() != null ? p.bandeiraPng() : "assets/bandeiras/" + pais + ".png";
    }

    // =================================================================== seleções
    public br.futebas.mundo.Selecao selecao(String pais) { return m.selecao(pais); }

    /** Ranking das seleções (Elo). */
    public List<br.futebas.mundo.Selecao> rankingSelecoes() { return sim.selecoes.ranking(); }

    /** Seleção comandada pelo técnico (null se nenhuma). */
    public br.futebas.mundo.Selecao minhaSelecao() { return sim.selecoes.humana(); }

    public List<Jogador> convocados(String pais) {
        List<Jogador> l = new ArrayList<>();
        br.futebas.mundo.Selecao s = m.selecao(pais);
        if (s != null) for (int id : s.convocados) if (m.jogador(id) != null) l.add(m.jogador(id));
        return l;
    }

    /** Copa do Mundo em andamento ou a última disputada (null antes da primeira). */
    public Torneio copaDoMundo() {
        Torneio t = m.torneio(br.futebas.selecao.Selecoes.COPA);
        return t != null ? t : m.torneioAnterior(br.futebas.selecao.Selecoes.COPA);
    }

    /** Torneios de seleções em andamento ou marcados no ano. */
    public List<Torneio> torneiosDeSelecoes() {
        List<Torneio> l = new ArrayList<>();
        for (Torneio t : m.torneios) if ("selecoes".equals(t.ambito)) l.add(t);
        return l;
    }

    /** Deixa a seleção (continua no clube). */
    public void deixarSelecao() {
        br.futebas.mundo.Selecao s = sim.selecoes.humana();
        if (s == null) return;
        s.humano = false;
        m.tecnico.selecaoPais = null;
        m.mensagem("selecao", "Seleção", "Você deixou o comando da seleção " + s.nome + ".");
    }

    // =================================================================== base (CPE), estádio, leilões, amistosos
    /**
     * CPE (capacidade potencial estimada) do jeito do Brasfoot: o potencial mostrado com um erro
     * fixo por jogador (o real pode ser um pouco maior ou menor).
     */
    public int cpe(int jogadorId) {
        Jogador j = m.jogador(jogadorId);
        if (j == null) return 0;
        // regra do jogo (13/11): o POT mostra o potencial de verdade, sem erro de olheiro, e nunca abaixo da força atual
        return (int) Math.max(1, Math.min(99, Math.max(Math.round(j.potencial), Math.round(j.forca))));
    }

    /** Encomenda a ampliação do estádio (custo e prazo de obra). */
    public ResultadoNegociacao ampliarEstadio(int lugares) {
        Clube c = meu();
        if (c.obraFim > 0) return new ResultadoNegociacao(false, "Já há uma obra em andamento.");
        int l = Math.max(1000, Math.min(lugares, br.futebas.Balanceamento.CAPACIDADE_MAXIMA - c.capacidade));
        if (l <= 0 || c.capacidade >= br.futebas.Balanceamento.CAPACIDADE_MAXIMA) return new ResultadoNegociacao(false, "O estádio já está no tamanho máximo.");
        long custo = Financas.custoObra(l, m.ano);
        if (c.dinheiro < custo) return new ResultadoNegociacao(false, "Dinheiro insuficiente: a obra custa " + Financas.formatar(custo) + ".");
        c.dinheiro -= custo;
        c.financas.obras += custo;
        c.obraLugares = l;
        c.obraFim = m.semanaAbsoluta() + Financas.semanasObra(l);
        String txt = "Ampliação de " + l + " lugares encomendada por " + Financas.formatar(custo) + ". Expansão com término em "
                + br.futebas.mundo.Calendario.texto(br.futebas.mundo.Calendario.inicio(c.obraFim)) + ".";
        m.mensagem("financas", "Obra no estádio", txt);
        return new ResultadoNegociacao(true, txt);
    }

    public List<br.futebas.mundo.Leilao> leiloesAbertos() {
        List<br.futebas.mundo.Leilao> l = new ArrayList<>();
        for (br.futebas.mundo.Leilao x : m.leiloes) if ("aberto".equals(x.estado)) l.add(x);
        return l;
    }

    /** Lance do humano num leilão (vale o maior lance quando o leilão fechar). */
    public ResultadoNegociacao darLance(int leilaoId, long valor) {
        Clube c = meu();
        for (br.futebas.mundo.Leilao l : m.leiloes) {
            if (l.id != leilaoId || !"aberto".equals(l.estado)) continue;
            if (valor < l.lanceMinimo) return new ResultadoNegociacao(false, "Lance abaixo do mínimo (" + Financas.formatar(l.lanceMinimo) + ").");
            if (valor <= l.maiorLance) return new ResultadoNegociacao(false, "Há um lance maior: " + Financas.formatar(l.maiorLance) + ".");
            if (c.dinheiro < valor) return new ResultadoNegociacao(false, "Dinheiro insuficiente em caixa.");
            l.maiorLance = valor;
            l.compradorId = c.id;
            return new ResultadoNegociacao(true, "Lance registrado: " + Financas.formatar(valor) + ".");
        }
        return new ResultadoNegociacao(false, "Leilão encerrado.");
    }

    /** Marca um amistoso na próxima semana livre (Brasfoot F9). */
    public Torneio marcarAmistoso(String adversarioId, boolean emCasa) { return sim.marcarAmistoso(adversarioId, emCasa); }

    /** Torneio amistoso com 2 ou 3 convidados (total 3–4 clubes) em semanas livres; null se não deu. */
    public Torneio marcarTorneioAmistoso(String nome, List<String> convidados) { return sim.marcarTorneioAmistoso(nome, convidados); }

    /**
     * Clube que revelou o jogador (primeira passagem de clube que não foi empréstimo), para o jogo de
     * despedida: revelação × último clube. Null se foi revelado pelo clube atual ou não há carreira.
     */
    public String clubeRevelacao(int jogadorId) {
        Jogador j = m.jogador(jogadorId);
        if (j == null) return null;
        List<LinhaCarreira> car = new ArrayList<>(carreiraDoJogador(jogadorId));
        car.sort(Comparator.comparingInt(LinhaCarreira::anoInicio));
        for (LinhaCarreira l : car) {
            if (l.emprestimo() || l.clubeId() == null) continue;
            br.futebas.mundo.Clube c = m.clube(l.clubeId());
            if (c == null || c.virtual) continue;
            return c.id.equals(j.clubeId) ? null : c.id;
        }
        return null;
    }

    /** Jogo de despedida de um jogador seu (30+ anos): ele joga e se aposenta depois; null se não deu. */
    public Torneio marcarJogoDeDespedida(int jogadorId, String adversarioId) { return sim.marcarJogoDeDespedida(jogadorId, adversarioId); }

    /**
     * Entrega ao auxiliar (escalação automática, tática da IA, sem jogo ao vivo): a competição
     * inteira ({@code partidaId} null) ou uma partida dela. {@code delegar=false} desfaz.
     */
    public void delegarAoAuxiliar(String competicaoId, Integer partidaId, boolean delegar) {
        if (m.tecnico == null || competicaoId == null) return;
        br.futebas.mundo.Tecnico t = m.tecnico;
        if (t.auxiliarCompeticoes == null) t.auxiliarCompeticoes = new java.util.TreeSet<>();
        if (t.auxiliarPartidas == null) t.auxiliarPartidas = new java.util.TreeSet<>();
        if (t.auxiliarExcecoes == null) t.auxiliarExcecoes = new java.util.TreeSet<>();
        if (partidaId == null) {
            // a competição inteira (pelo id base): as exceções avulsas dela deixam de valer
            if (delegar) t.auxiliarCompeticoes.add(competicaoId);
            else t.auxiliarCompeticoes.remove(competicaoId);
            t.auxiliarExcecoes.removeIf(k -> k.startsWith(competicaoId + "#"));
            t.auxiliarPartidas.removeIf(k -> k.startsWith(competicaoId + "#"));
        } else {
            // uma partida: com a competição delegada, desmarcar vira exceção (o técnico comanda essa)
            String k = br.futebas.mundo.Tecnico.chavePartida(competicaoId, partidaId, m.ano);
            boolean daCompeticao = t.auxiliarCompeticoes.contains(competicaoId);
            if (delegar) {
                t.auxiliarExcecoes.remove(k);
                if (!daCompeticao) t.auxiliarPartidas.add(k);
            } else {
                t.auxiliarPartidas.remove(k);
                if (daCompeticao) t.auxiliarExcecoes.add(k);
            }
        }
    }

    /** A partida (ou a competição) está com o auxiliar? */
    public boolean delegadoAoAuxiliar(String competicaoId, int partidaId) {
        return m.tecnico != null && m.tecnico.delegado(competicaoId, partidaId, m.ano);
    }

    /** A competição inteira está com o auxiliar (o quadro "Delegar competições" lê isto)? */
    public boolean competicaoDelegada(String competicaoId) {
        return m.tecnico != null && m.tecnico.auxiliarCompeticoes != null && m.tecnico.auxiliarCompeticoes.contains(competicaoId);
    }

    /** Jogos de despedida já feitos (história). */
    public List<br.futebas.historia.Historia.Despedida> despedidas() { return m.historia.despedidas; }

    /** Pontos do técnico no ranking (títulos + 1 a 5 por jogo). */
    public int rankingTecnico() { return m.tecnico == null ? 0 : m.tecnico.pontosRanking; }

    public ResultadoPartida ultimaPartidaNarrada() { return sim.ultimaNarrada; }

    public List<ResultadoPartida> partidasNarradasDaSemana() { return sim.narradasDaSemana; }

    public List<String> formacoes() { return Formacoes.nomes(); }

    private Torneio torneioQualquer(String competicaoId) {
        Torneio t = m.torneio(competicaoId);
        if (t == null) t = m.torneioAnterior(competicaoId);
        if (t == null) t = competicaoSub20(competicaoId);
        if (t == null) t = competicaoKings(competicaoId);
        return t;
    }

    /** Reservas relacionados no banco na competição (Brasil 2026: 12; Premier League: 9); 9 se não achar. */
    public int reservasPermitidas(String competicaoId) {
        Torneio t = torneioQualquer(competicaoId);
        return t == null || t.regras == null ? 9 : t.regras.reservas(t);
    }

    /** Substituições permitidas na competição (2026: 5); 5 se não achar. */
    public int substituicoesPermitidas(String competicaoId) {
        Torneio t = torneioQualquer(competicaoId);
        return t == null || t.regras == null ? 5 : t.regras.substituicoes;
    }

    /** Reservas e substituições do jogo: {reservas, substituições} da competição da partida. */
    public int[] limitesDaPartida(Partida p) {
        String id = p == null ? null : p.torneioId;
        return new int[]{reservasPermitidas(id), substituicoesPermitidas(id)};
    }

    /** Rótulos das 11 vagas da formação para a tela (4-4-2/4-5-1: "ME"/"MD" nos abertos do meio). */
    public List<String> rotulosFormacao(String formacao) { return List.of(Formacoes.rotulos(formacao)); }

    public List<String> avisosDoMotor() { return m.avisos; }

    // =================================================================== tática, escalação, treino
    private Clube meu() {
        Clube c = m.clubeHumano();
        if (c == null) throw new IllegalStateException("Você não está comandando nenhum clube.");
        return c;
    }

    public void definirTatica(String formacao, Estilo estilo) {
        Clube c = meu();
        if (formacao != null && Formacoes.TODAS.containsKey(formacao)) {
            if (!formacao.equals(c.tatica.formacao)) c.tatica.titulares.clear();
            c.tatica.formacao = formacao;
        }
        if (estilo != null) c.tatica.estilo = estilo;
    }

    /** Formação, estilo, marcação e concentração de ataques de uma vez (null = não muda). */
    public void definirTatica(String formacao, Estilo estilo, Marcacao marcacao, Concentracao concentracao) {
        definirTatica(formacao, estilo);
        definirMarcacao(marcacao);
        definirConcentracao(concentracao);
    }

    /** Marcação leve, normal ou pesada (null = não muda). */
    public void definirMarcacao(Marcacao marcacao) {
        if (marcacao != null) meu().tatica.marcacao = marcacao;
    }

    /** Concentrar ataques: equilibrado, pelo meio ou pelas laterais (null = não muda). */
    public void definirConcentracao(Concentracao concentracao) {
        if (concentracao != null) meu().tatica.concentracao = concentracao;
    }

    /** Marcação atual do seu clube (NORMAL se não houver clube). */
    public Marcacao marcacao() {
        Clube c = m.clubeHumano();
        return c == null ? Marcacao.NORMAL : c.tatica.marcacao();
    }

    /** Concentração de ataques atual do seu clube (EQUILIBRADO se não houver clube). */
    public Concentracao concentracao() {
        Clube c = m.clubeHumano();
        return c == null ? Concentracao.EQUILIBRADO : c.tatica.concentracao();
    }

    /** Escalação manual: 11 ids na ordem das vagas da formação e os reservas. */
    public boolean definirEscalacao(List<Integer> titulares, List<Integer> reservas) {
        Clube c = meu();
        if (titulares == null || titulares.size() != 11) return false;
        c.tatica.titulares = new ArrayList<>(titulares);
        c.tatica.reservas = reservas == null ? new ArrayList<>() : new ArrayList<>(reservas);
        return true;
    }

    /** Caixa "Sub-20" da escalação: o time Sub-20 aparece nos não relacionados e pode ser relacionado. */
    public boolean usaSub20NaEscalacao() {
        Clube c = meu();
        return c != null && c.tatica.usarSub20;
    }

    /** Marca ou desmarca a caixa "Sub-20"; desmarcada, quem é do Sub-20 sai da escalação salva. */
    public void usarSub20NaEscalacao(boolean marcar) {
        Clube c = meu();
        if (c == null) return;
        c.tatica.usarSub20 = marcar;
        if (marcar) return;
        java.util.Set<Integer> base = new java.util.HashSet<>(c.base);
        c.tatica.titulares.replaceAll(id -> id != null && base.contains(id) ? -1 : id);
        c.tatica.reservas.removeIf(id -> id == null || base.contains(id));
        if (c.tatica.capitao != null && base.contains(c.tatica.capitao)) c.tatica.capitao = null;
        if (c.tatica.cobradorPenalti != null && base.contains(c.tatica.cobradorPenalti)) c.tatica.cobradorPenalti = null;
        if (c.tatica.cobradorFalta != null && base.contains(c.tatica.cobradorFalta)) c.tatica.cobradorFalta = null;
    }

    public void escalacaoAutomatica() {
        Clube c = meu();
        c.tatica.titulares.clear();
        c.tatica.reservas.clear();
    }

    public void definirCobradores(Integer capitao, Integer penalti, Integer falta) {
        Clube c = meu();
        c.tatica.capitao = capitao;
        c.tatica.cobradorPenalti = penalti;
        c.tatica.cobradorFalta = falta;
    }

    /** Treino de um jogador: "principais", "secundarias" ou null (o auxiliar decide). */
    public void definirTreino(int jogadorId, String treino) {
        Jogador j = m.jogador(jogadorId);
        if (j != null && meu().id.equals(j.clubeId)) j.treino = treino;
    }

    public void definirTreinoDoElenco(String treino) {
        for (Jogador j : elenco(meu().id)) j.treino = treino;
    }

    // =================================================================== mercado
    public record ResultadoNegociacao(boolean aceito, String mensagem) {}

    /** Busca no mercado (livres, à venda, de outros clubes e do exterior). */
    public List<Jogador> buscarJogadores(Posicao posicao, int forcaMin, int idadeMax, long precoMax, int limite) {
        List<Jogador> l = new ArrayList<>();
        String meuId = m.clubeHumano() == null ? "" : m.clubeHumano().id;
        for (Jogador j : m.jogadores) {
            if (j.amador() || j.status == StatusJogador.APOSENTADO || j.status == StatusJogador.DISPENSADO || j.status == StatusJogador.BASE) continue;
            if (meuId.equals(j.clubeId)) continue;
            if (posicao != null && j.posicao != posicao) continue;
            if (j.forca < forcaMin || j.idade(m.ano) > idadeMax) continue;
            if (precoMax > 0 && sim.mercado.precoPedido(j) > precoMax) continue;
            l.add(j);
        }
        l.sort(Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id));
        return l.size() > limite ? new ArrayList<>(l.subList(0, limite)) : l;
    }

    public long precoPedido(int jogadorId) { return sim.mercado.precoPedido(m.jogador(jogadorId)); }

    /** Proposta de compra (ou contratação do exterior / de jogador livre, com valor 0). */
    /**
     * Proposta de compra (regras do jogo: assíncrona). O clube dono responde em 1–7 dias (aceita,
     * contraproposta ou recusa); jogador livre vai direto ao contrato. Depois: {@link #proporContrato},
     * {@link #confirmarNegocio}. {@code aceito} = proposta enviada (o negócio NÃO está fechado).
     */
    public ResultadoNegociacao proporCompra(int jogadorId, long valor, int mesesContrato) {
        return proporCompra(jogadorId, valor, mesesContrato, null);
    }

    /**
     * Proposta de compra com o salário oferecido: a única confirmação do técnico (requisito).
     * Se o clube aceitar a taxa e o jogador o salário, o negócio fecha sozinho.
     */
    public ResultadoNegociacao proporCompra(int jogadorId, long valor, int mesesContrato, Long salario) {
        String[] erro = new String[1];
        PropostaJogador p = sim.negociacoes.comprar(jogadorId, valor, mesesContrato, salario, erro);
        return p == null ? new ResultadoNegociacao(false, erro[0]) : new ResultadoNegociacao(true, p.mensagem);
    }

    /** A negociação em andamento mais recente por esse jogador (enviada ou recebida); null se não há. */
    public PropostaJogador negociacaoPor(int jogadorId) {
        PropostaJogador achada = null;
        for (PropostaJogador p : m.propostas) if (p.jogadorId == jogadorId && p.ativa() && (achada == null || p.id > achada.id)) achada = p;
        return achada;
    }

    /** Renovação: o jogador aceita se o salário oferecido cobrir o que ele pede (a moral pesa). */
    public ResultadoNegociacao renovarContrato(int jogadorId, long salario, int meses) {
        Clube c = meu();
        Jogador j = m.jogador(jogadorId);
        if (j == null || !c.id.equals(j.clubeId) || j.contrato == null || j.contrato.teste()) return new ResultadoNegociacao(false, "Contrato inválido.");
        long pede = Financas.salarioPedido(j, c, m.ano);
        double exigencia = j.moral < 40 ? 1.15 : j.moral > 75 ? 0.9 : 1.0;
        if (salario < pede * exigencia) return new ResultadoNegociacao(false, "Salário menor que o pedido (" + Financas.formatar((long) (pede * exigencia)) + ").");
        if (salario > pede * 3) return new ResultadoNegociacao(false, "Salário maior que o limite para esse jogador.");
        int m6 = meses <= 6 ? 6 : meses <= 12 ? 12 : Math.min(60, (int) Math.round(meses / 12.0) * 12); // até 5 anos
        j.contrato.salario = salario;
        j.contrato.fim = sim.mercado.fimContrato(m6);
        j.contrato.multa = Financas.multaRescisoria(j, c, m.ano);
        j.moral = Math.min(100, j.moral + 8);
        return new ResultadoNegociacao(true, j.nomeExibicao() + " renovou por " + m6 + " meses.");
    }

    /** Lista de transferências: o jogador fica à venda (preço 0 = tira da lista); a IA pode mandar propostas. */
    public void colocarAVenda(int jogadorId, long preco) {
        Jogador j = m.jogador(jogadorId);
        if (j == null || meu() == null || !meu().id.equals(j.clubeId) || j.amador()) return;
        j.aVenda = preco > 0;
        j.precoPedido = preco;
    }

    /**
     * Lista de empréstimos (regras do jogo: não empresta na hora): o jogador entra na lista e os
     * clubes da IA podem pedir emprestado nas semanas de janela (propostas recebidas).
     */
    public ResultadoNegociacao emprestar(int jogadorId) {
        Clube c = meu();
        Jogador j = m.jogador(jogadorId);
        if (c == null || j == null || !c.id.equals(j.clubeId) || j.contrato == null || j.contrato.tipo != TipoContrato.PROFISSIONAL)
            return new ResultadoNegociacao(false, "Não é possível emprestar este jogador.");
        j.aEmprestimo = true;
        return new ResultadoNegociacao(true, j.nomeExibicao() + " está na lista de empréstimos. Os interessados vão mandar propostas.");
    }

    /** Tira o jogador das listas de transferências e de empréstimos. */
    public void tirarDasListas(int jogadorId) {
        Jogador j = m.jogador(jogadorId);
        if (j == null || meu() == null || !meu().id.equals(j.clubeId)) return;
        j.aVenda = false;
        j.precoPedido = 0;
        j.aEmprestimo = false;
    }

    /** Seus jogadores na lista de transferências. */
    public List<Jogador> listaTransferencias() {
        List<Jogador> l = new ArrayList<>();
        if (meu() != null) for (Jogador j : elenco(meu().id)) if (j.aVenda) l.add(j);
        return l;
    }

    /** Seus jogadores na lista de empréstimos. */
    public List<Jogador> listaEmprestimos() {
        List<Jogador> l = new ArrayList<>();
        if (meu() != null) for (Jogador j : elenco(meu().id)) if (j.aEmprestimo) l.add(j);
        return l;
    }

    /** Taxa cobrada pelo clube dono para emprestar o jogador até o fim da temporada (0 = não empresta). */
    public long taxaEmprestimo(int jogadorId) {
        Jogador j = m.jogador(jogadorId);
        Clube c = m.clubeHumano();
        if (j == null || c == null || j.amador() || j.status != StatusJogador.PROFISSIONAL || j.contrato == null
                || j.contrato.tipo != TipoContrato.PROFISSIONAL || c.id.equals(j.clubeId)) return 0;
        return Financas.arred(Math.max(Financas.salarioMinimo(m.ano), Financas.valorMercado(j, m.clube(j.clubeId), m.ano) * 0.08));
    }

    /**
     * Pede um jogador de outro clube brasileiro emprestado até o fim da temporada. O dono só
     * empresta quem não é titular dele (fora dos 14 mais fortes) ou jovem sem espaço; o seu clube
     * paga a taxa e o salário enquanto ele estiver aqui. Ele volta na virada do ano.
     */
    public ResultadoNegociacao pedirEmprestado(int jogadorId) {
        Clube c = meu();
        Jogador j = m.jogador(jogadorId);
        if (c == null || j == null || j.amador() || c.id.equals(j.clubeId)) return new ResultadoNegociacao(false, "Jogador indisponível.");
        Clube dono = m.clube(j.clubeId);
        if (dono == null || dono.virtual || dono.humano || j.status != StatusJogador.PROFISSIONAL || j.contrato == null)
            return new ResultadoNegociacao(false, "Só dá para pedir emprestado jogador com contrato num clube.");
        if (sim.mercado.fimDaTemporada(c) - m.semanaAbsoluta() < 6) return new ResultadoNegociacao(false, "A temporada está no fim: peça o empréstimo na próxima.");
        // recusas óbvias saem na hora (titular, clube grande demais); o resto o dono responde em alguns dias
        String motivo = br.futebas.ia.Mercado.motivoRecusaEmprestimo(m, j, dono, c);
        if (motivo != null) return new ResultadoNegociacao(false, motivo);
        String[] erro = new String[1];
        PropostaJogador p = sim.negociacoes.pedirEmprestado(jogadorId, taxaEmprestimo(jogadorId), erro);
        return p == null ? new ResultadoNegociacao(false, erro[0]) : new ResultadoNegociacao(true, p.mensagem);
    }

    /** Rescinde o contrato (paga metade dos salários restantes). */
    public void dispensar(int jogadorId) {
        Clube c = meu();
        Jogador j = m.jogador(jogadorId);
        if (j == null || !c.id.equals(j.clubeId)) return;
        if (j.contrato != null && j.contrato.tipo != TipoContrato.EMPRESTIMO) {
            int semanas = Math.max(0, j.contrato.fim - m.semanaAbsoluta());
            long multa = Financas.arred(j.contrato.salario * (semanas / 4.33) * 0.5);
            c.dinheiro -= multa;
            c.financas.salarios += multa;
        }
        if (j.contrato != null && j.contrato.tipo == TipoContrato.EMPRESTIMO) sim.mercado.devolverEmprestado(j);
        else sim.mercado.liberar(j, c);
    }

    public boolean promoverDaBase(int jogadorId) {
        Clube c = meu();
        Jogador j = m.jogador(jogadorId);
        if (j == null || !c.base.contains(jogadorId)) return false;
        c.base.remove(Integer.valueOf(jogadorId));
        c.elenco.add(jogadorId);
        j.noSub20 = false;
        j.status = StatusJogador.PROFISSIONAL;
        j.contrato = Contrato.profissional(Financas.salarioPedido(j, c, m.ano), sim.mercado.fimDaTemporada(c) + 52, Financas.multaRescisoria(j, c, m.ano));
        return true;
    }

    /** Base → Sub-20: o júnior passa a jogar pelo time sub-20 (continua sem contrato profissional). */
    public boolean subirAoSub20(int jogadorId) {
        Clube c = meu();
        Jogador j = m.jogador(jogadorId);
        if (c == null || j == null || !c.base.contains(jogadorId) || j.noSub20) return false;
        c.sub20Montado = true;
        sim.sub20.entrar(j);
        return true;
    }

    /** Profissional com idade de sub-20 → time sub-20 (regra do jogo). Continua com o contrato; volta com "Promover". */
    public boolean mandarAoSub20(int jogadorId) {
        Clube c = meu();
        Jogador j = m.jogador(jogadorId);
        if (c == null || j == null || !c.elenco.contains(jogadorId) || !j.sub20(m.ano) || j.amador()) return false;
        if (j.contrato != null && j.contrato.tipo == TipoContrato.EMPRESTIMO) return false;
        c.elenco.remove(Integer.valueOf(jogadorId));
        c.base.add(jogadorId);
        c.sub20Montado = true;
        j.noSub20 = true;
        j.status = StatusJogador.BASE;
        j.aVenda = false;
        j.aEmprestimo = false;
        c.tatica.titulares.replaceAll(id -> id == jogadorId ? -1 : id);
        c.tatica.reservas.removeIf(id -> id == jogadorId);
        return true;
    }

    /** Sub-20 → Base (sai do time sub-20). */
    public boolean devolverABase(int jogadorId) {
        Clube c = meu();
        Jogador j = m.jogador(jogadorId);
        if (c == null || j == null || !c.base.contains(jogadorId) || !j.noSub20) return false;
        sim.sub20.sair(j);
        return true;
    }

    /** Propostas da IA por jogadores seus (em andamento e as encerradas recentes), com {@code situacao()} e prazo. */
    public List<PropostaJogador> propostasRecebidas() {
        List<PropostaJogador> l = new ArrayList<>();
        if (m.multi()) {
            // multi-treinador: só as do clube do técnico da vez; as de outro técnico humano aparecem como recebidas
            Clube c = m.clubeHumano();
            if (c == null) return l;
            for (PropostaJogador p : m.propostas) {
                if (!c.id.equals(p.vendedorId)) continue;
                if (p.entreHumanos) l.add(vistaDoVendedor(p));
                else if ("recebida".equals(p.direcao) || p.direcao == null && "pendente".equals(p.estado)) l.add(p);
            }
            return l;
        }
        for (PropostaJogador p : m.propostas) if ("recebida".equals(p.direcao) || p.direcao == null && "pendente".equals(p.estado)) l.add(p);
        return l;
    }

    /**
     * Multi-treinador: a proposta de outro técnico humano vista pelo vendedor (cópia com a mesma id):
     * "recebida" quando a vez é dele, "aguardando" quando espera o comprador.
     */
    private static PropostaJogador vistaDoVendedor(PropostaJogador p) {
        PropostaJogador v = new PropostaJogador();
        v.id = p.id;
        v.jogadorId = p.jogadorId;
        v.compradorId = p.compradorId;
        v.comprador = p.comprador;
        v.vendedorId = p.vendedorId;
        v.valor = p.valor;
        v.pago = p.pago;
        v.salario = p.salario;
        v.tipo = p.tipo;
        v.direcao = "recebida";
        v.criada = p.criada;
        v.diaResposta = p.diaResposta;
        v.diaExpira = p.diaExpira;
        v.valorContra = p.valorContra;
        v.meses = p.meses;
        v.semanaChegada = p.semanaChegada;
        v.diaChegada = p.diaChegada;
        v.mensagem = p.mensagem;
        v.entreHumanos = true;
        v.estado = switch (p.estado == null ? "" : p.estado) {
            case "aguardando" -> "recebida";
            case "contraproposta", "contraproposta_jogador", "aceita", "aguardando_confirmacao" -> "aguardando";
            default -> p.estado;
        };
        return v;
    }

    /** As suas propostas por jogadores de outros clubes (e livres), com {@code situacao()} e prazo. */
    public List<PropostaJogador> propostasEnviadas() {
        List<PropostaJogador> l = new ArrayList<>();
        Clube c = m.multi() ? m.clubeHumano() : null;
        for (PropostaJogador p : m.propostas)
            if ("enviada".equals(p.direcao) && (c == null || c.id.equals(p.compradorId)) && (!m.multi() || c != null)) l.add(p);
        return l;
    }

    /**
     * Responder: proposta recebida (aceitar = aguardando a sua confirmação; recusar) ou
     * contraproposta do outro lado (aceitar o valor/salário pedido; recusar encerra).
     */
    public boolean responderProposta(int propostaId, boolean aceitar) {
        for (PropostaJogador p : m.propostas) {
            if (p.id != propostaId || p.direcao != null || !"pendente".equals(p.estado)) continue;
            // proposta de save antigo: comportamento antigo
            p.estado = aceitar ? "aceita" : "recusada";
            if (!aceitar) return true;
            Jogador j = m.jogador(p.jogadorId);
            Clube vendedor = m.clube(p.vendedorId), comprador = m.clube(p.compradorId);
            if (j == null || vendedor == null || comprador == null || !vendedor.id.equals(j.clubeId)) return false;
            sim.mercado.transferir(j, comprador, p.valor);
            return true;
        }
        PropostaJogador ph = sim.negociacoes.proposta(propostaId);
        if (ph != null && ph.entreHumanos && m.clubeHumano() != null && m.clubeHumano().id.equals(ph.vendedorId))
            return sim.negociacoes.responderComoVendedor(propostaId, aceitar);
        return sim.negociacoes.responder(propostaId, aceitar);
    }

    /** Contraproposta (novo valor) a uma proposta recebida ou à contraproposta do outro clube. */
    public boolean contraproposta(int propostaId, long valor) {
        PropostaJogador ph = sim.negociacoes.proposta(propostaId);
        if (ph != null && ph.entreHumanos && m.clubeHumano() != null && m.clubeHumano().id.equals(ph.vendedorId))
            return sim.negociacoes.contraproporComoVendedor(propostaId, valor);
        return sim.negociacoes.contrapropor(propostaId, valor);
    }

    /** Termos com o jogador depois da taxa aceita (ou jogador livre): devolve a resposta dele. */
    public String proporContrato(int propostaId, long salario, int meses) { return sim.negociacoes.proporContrato(propostaId, salario, meses); }

    /** Confirmação final do negócio (fora da janela fica "aguardando a janela"). Devolve o resultado em texto. */
    public String confirmarNegocio(int propostaId) { return sim.negociacoes.confirmar(propostaId); }

    public boolean cancelarProposta(int propostaId) { return sim.negociacoes.cancelar(propostaId); }

    /** Prazo da proposta (data limite para a resposta/confirmação); null se não tem. */
    public java.time.LocalDate prazoProposta(int propostaId) {
        PropostaJogador p = sim.negociacoes.proposta(propostaId);
        return p == null || p.diaExpira <= 0 ? null : sim.negociacoes.data(p.diaExpira);
    }

    /**
     * Negócios fechados fora da janela que envolvem o seu clube (chegadas e saídas): o jogador continua
     * no clube de origem até a abertura da janela do comprador ({@link PropostaJogador#chegada()}).
     */
    public List<PropostaJogador> transferenciasPendentes() {
        List<PropostaJogador> l = new ArrayList<>();
        Clube c = meu();
        if (c == null) return l;
        for (PropostaJogador p : m.propostas)
            if ("aguardando_janela".equals(p.estado) && (c.id.equals(p.compradorId) || c.id.equals(p.vendedorId))) l.add(p);
        return l;
    }

    /** Todos os negócios do mundo esperando a janela (IA × IA também). */
    public List<PropostaJogador> transferenciasPendentesDoMundo() {
        List<PropostaJogador> l = new ArrayList<>();
        for (PropostaJogador p : m.propostas) if ("aguardando_janela".equals(p.estado)) l.add(p);
        return l;
    }

    /** Data em que o jogador se muda (acerto fora da janela); null se ele não tem negócio esperando a janela. */
    public java.time.LocalDate chegadaPrevista(int jogadorId) {
        for (PropostaJogador p : m.propostas) if (p.jogadorId == jogadorId && "aguardando_janela".equals(p.estado)) return p.chegada();
        return null;
    }

    /** A janela de transferências do seu clube está aberta nesta semana? */
    public boolean janelaAberta() {
        Clube c = meu();
        return c != null && br.futebas.ia.Mercado.janelaAberta(c.calendario, m.semana);
    }

    /**
     * Pede dinheiro ao banco (até o limite de crédito). A dívida inteira passa a ser paga em
     * {@link Financas#PRAZO_EMPRESTIMO_MESES} parcelas mensais, mais os juros.
     */
    public long pedirEmprestimo(long valor) {
        Clube c = meu();
        if (c == null) return 0;
        long limite = Financas.limiteEmprestimo(c, m.ano);
        long v = Math.max(0, Math.min(valor, limite - c.divida));
        if (v <= 0) return 0;
        c.divida += v;
        c.dinheiro += v;
        c.dividaParcelas = Financas.PRAZO_EMPRESTIMO_MESES;
        return v;
    }

    /**
     * Pagamento extra (amortização) escolhido pelo técnico: nunca passa do saldo em caixa nem do
     * total devido. O número de parcelas continua o mesmo, então as próximas ficam menores.
     */
    public long pagarEmprestimo(long valor) {
        Clube c = meu();
        if (c == null) return 0;
        long v = Math.max(0, Math.min(valor, maximoAmortizar()));
        c.divida -= v;
        c.dinheiro -= v;
        if (c.divida <= 0) {
            c.divida = 0;
            c.dividaParcelas = 0;
        } else if (c.dividaParcelas <= 0) c.dividaParcelas = Financas.PRAZO_EMPRESTIMO_MESES;
        return v;
    }

    /** O máximo que dá para pagar agora: o menor entre o total devido e o saldo em caixa. */
    public long maximoAmortizar() {
        Clube c = meu();
        return c == null ? 0 : Math.max(0, Math.min(c.divida, c.dinheiro));
    }

    /**
     * Situação da dívida no banco para a tela de finanças: total devido, a próxima parcela (data,
     * amortização e juros), quantas faltam e o cronograma completo.
     */
    public record SituacaoDivida(long total, double jurosMes, int parcelasRestantes, Financas.Parcela proxima,
                                 List<Financas.Parcela> cronograma, long jurosTotais, long limite, long disponivel, long maximoAmortizar) {}

    public SituacaoDivida situacaoDivida() {
        Clube c = meu();
        if (c == null) return new SituacaoDivida(0, Financas.JUROS_MES, 0, null, List.of(), 0, 0, 0, 0);
        List<Financas.Parcela> cr = Financas.cronograma(c, m.semanaAbsoluta());
        long juros = 0;
        for (Financas.Parcela p : cr) juros += p.juros();
        long limite = Financas.limiteEmprestimo(c, m.ano);
        return new SituacaoDivida(c.divida, Financas.JUROS_MES, cr.size(), cr.isEmpty() ? null : cr.get(0), cr, juros,
                limite, Math.max(0, limite - c.divida), maximoAmortizar());
    }

    // =================================================================== carreira do técnico
    public List<PropostaTecnico> propostasDeEmprego() {
        List<PropostaTecnico> l = new ArrayList<>();
        for (PropostaTecnico p : m.propostasTecnico) if ("pendente".equals(p.estado)) l.add(p);
        return l;
    }

    public boolean aceitarEmprego(int propostaId) {
        return sim.diretoria.aceitar(propostaId);
    }

    /** Recusa um convite de clube. */
    public boolean recusarEmprego(int propostaId) {
        for (PropostaTecnico p : m.propostasTecnico) {
            if (p.id == propostaId && "pendente".equals(p.estado)) {
                p.estado = "recusada";
                return true;
            }
        }
        return false;
    }

    public void pedirDemissao() {
        if (m.clubeHumano() == null && m.tecnico != null && m.tecnico.kingsTimeId != null) {
            LigaKings.TimeKings t = sim.kings.timeKings(m.tecnico.kingsTimeId);
            m.registrarTemporadaTecnico("Pediu demissão"); // linha da Kings na carreira
            deixarTimeKings();
            m.mensagem("kings", "Demissão", "Você deixou o comando do " + (t == null ? "time" : t.nome) + " na Kings League.");
            m.historia.evento(m.ano, m.semana, "tecnico", m.tecnico.nome + " deixa o " + (t == null ? "time" : t.nome) + " na Kings League.");
            return;
        }
        Clube c = meu();
        m.registrarTemporadaTecnico("Pediu demissão");
        c.humano = false;
        m.tecnico.clubeId = null;
        m.mensagem("diretoria", "Demissão", "Você deixou o comando do " + c.nome + ".");
        for (Clube x : m.clubes.values()) {
            if (!x.virtual && x.existe && x != c && Math.abs(x.reputacao - c.reputacao) < 10 && m.rng.chance(0.02)) sim.diretoria.proporAoHumano(x);
        }
    }

    // =================================================================== Kings League
    public List<LigaKings> ligasKings() { return m.kings; }

    /**
     * Escolher um time da Kings: só no JOGO NOVO (antes da primeira semana). Durante a carreira o
     * técnico só chega a um time da Kings por convite aceito ({@link #aceitarEmprego}) ou
     * candidatura ({@link #candidatarKings}); aqui devolve falso. Carreira exclusiva (regra do
     * dono): quem assume um time da Kings deixa o clube.
     */
    public boolean assumirTimeKings(String timeId) {
        if (!jogoNovo()) return false;
        if (m.tecnico == null) {
            // jogo novo só com um time da Kings (sem clube): o técnico existe sem clube
            if (sim.kings.timeKings(timeId) == null) return false;
            m.tecnico = new br.futebas.mundo.Tecnico("Técnico");
        }
        for (LigaKings l : m.kings) {
            LigaKings.TimeKings t = l.time(timeId);
            if (t == null || br.futebas.kings.Kings.ehMundial(l)) continue;
            Clube c = m.clubeHumano();
            if (c != null) {
                c.humano = false;
                m.tecnico.clubeId = null;
            }
            sim.kings.assumir(timeId);
            m.mensagem("kings", "Kings League", "Você assumiu o " + t.nome + " (" + l.nome + ").");
            return true;
        }
        return false;
    }

    /**
     * Jogo novo como técnico de um time da Kings (carreira exclusiva: sem clube). {@code clubeId}
     * é ignorado (fica na assinatura por compatibilidade: ou clube, ou Kings).
     */
    public static Jogo novoJogoKings(BancoMundo db, long semente, String clubeId, String kingsTimeId, String nomeTecnico) {
        return novoJogoKings(db, semente, clubeId, kingsTimeId, nomeTecnico, null);
    }

    /** Idem, com os times customizados (Kings League IOS) quando {@code custom} não é null. */
    public static Jogo novoJogoKings(BancoMundo db, long semente, String clubeId, String kingsTimeId, String nomeTecnico,
                                     br.futebas.kings.KingsCustom.Definicao custom) {
        Jogo j = novoJogo(db, semente, null, nomeTecnico, custom);
        if (j.m.tecnico == null) {
            j.m.tecnico = new br.futebas.mundo.Tecnico(nomeTecnico == null || nomeTecnico.isBlank() ? "Técnico" : nomeTecnico);
            j.m.tecnico.reputacao = 25;
        }
        if (!j.assumirTimeKings(kingsTimeId)) throw new IllegalArgumentException("Time da Kings inválido: " + kingsTimeId);
        return j;
    }

    /** Ainda no jogo novo (nenhuma semana jogada): a escolha do time da Kings é livre. */
    public boolean jogoNovo() {
        return m.ano == br.futebas.Balanceamento.ANO_INICIAL && m.semana == 1;
    }

    /**
     * Times da Kings a que dá para se candidatar: no jogo novo, todos; na carreira, só os SEM
     * técnico (vaga aberta). Fora disso, só por convite recebido.
     */
    public List<LigaKings.TimeKings> vagasKings() { return sim.kings.vagas(); }

    /**
     * Candidatura a um time de {@link #vagasKings()}: se o presidente aceitar (no jogo novo, sempre),
     * chega um convite (devolvido) para {@link #aceitarEmprego}; null = recusado ou fora da regra.
     */
    public PropostaTecnico candidatarKings(String timeId) { return sim.kings.candidatar(timeId); }

    /** Competições da Kings em andamento no ano: Split 1, Kings Cup e Split 2 de cada liga + Copas do Mundo. */
    public List<Torneio> competicoesKings() {
        List<Torneio> l = new ArrayList<>();
        for (LigaKings liga : m.kings) l.addAll(liga.competicoes);
        for (Torneio t : m.kingsMundiais) if (t.ano == m.ano) l.add(t);
        return l;
    }

    /**
     * Competição da Kings pelo id: {@code kings-<liga>-split1}, {@code -split2}, {@code -cup},
     * {@code kings-mundial-clubes}, {@code kings-mundial-selecoes} (a mais recente); null se não há.
     */
    public Torneio competicaoKings(String id) {
        Torneio achado = null;
        for (LigaKings liga : m.kings) for (Torneio t : liga.competicoes) if (t.id.equals(id) && (achado == null || t.ano > achado.ano)) achado = t;
        for (Torneio t : m.kingsMundiais) if (t.id.equals(id) && (achado == null || t.ano > achado.ano)) achado = t;
        return achado;
    }

    /** Time da Kings pelo id (de qualquer liga, inclusive as seleções da Kings World Cup Nations). */
    public LigaKings.TimeKings timeKings(String id) { return sim.kings.timeKings(id); }

    public void deixarTimeKings() {
        if (m.tecnico == null) return;
        for (LigaKings l : m.kings) for (LigaKings.TimeKings t : l.times)
            if (!m.multi() || t.id.equals(m.tecnico.kingsTimeId)) t.humano = false; // multi: só o time dele
        m.tecnico.kingsTimeId = null;
    }

    /** O time da Kings do técnico (null se ele não treina um). */
    public LigaKings.TimeKings meuTimeKings() {
        return m.tecnico == null ? null : sim.kings.timeKings(m.tecnico.kingsTimeId);
    }

    /** Carreira da Kings: o técnico treina um time da Kings (e, pela regra, nenhum clube). */
    public boolean tecnicoDaKings() {
        return m.clubeHumano() == null && meuTimeKings() != null;
    }

    /** Nome do emprego atual (clube ou time da Kings); null se desempregado. */
    public String empregoAtual() {
        Clube c = m.clubeHumano();
        if (c != null) return c.nome;
        LigaKings.TimeKings t = meuTimeKings();
        return t == null ? null : t.nome;
    }

    /**
     * Colocação do seu time da Kings na competição de liga mais recente do ano em que ele já
     * jogou: {posição, times, jogos}; null sem time ou sem jogos.
     */
    public int[] posicaoKings() {
        LigaKings.TimeKings t = meuTimeKings();
        LigaKings l = t == null ? null : ligaKingsDoTime(t.id);
        if (l == null) return null;
        int[] achado = null;
        for (Torneio x : l.competicoes) {
            if (x.ano != m.ano || x.fases.isEmpty() || !x.participantes.contains(t.id)) continue;
            List<LinhaTabela> tab;
            try {
                tab = x.tabelaGeral();
            } catch (RuntimeException e) {
                continue;
            }
            for (int i = 0; i < tab.size(); i++) {
                LinhaTabela ln = tab.get(i);
                if (t.id.equals(ln.clubeId) && ln.jogos > 0) achado = new int[]{i + 1, tab.size(), ln.jogos};
            }
        }
        return achado;
    }

    /**
     * Confiança do presidente no técnico da Kings (0–100), guardada no save (rodada 2 do balanço):
     * começa em 60 ao assumir, sobe com vitórias/títulos, cai com derrotas (menos contra quem é mais
     * forte); abaixo de {@code Kings.CONFIANCA_DEMISSAO_KINGS} (20), depois de 6 jogos no time, o
     * presidente demite. Último no Split na virada do ano: −20. Sem time da Kings: 60. Save antigo sem o
     * valor: a estimativa pela colocação (1º ≈ 85, último ≈ 20) até o próximo jogo.
     */
    public double confiancaPresidenteKings() {
        if (m.tecnico != null && m.tecnico.confiancaKings != null && m.tecnico.kingsTimeId != null) return m.tecnico.confiancaKings;
        int[] p = posicaoKings();
        if (p == null || p[1] < 2) return 60;
        return 85 - 65.0 * (p[0] - 1) / (p[1] - 1);
    }

    /** Carreira na Kings League: {jogos, vitórias, derrotas, vitórias nos shootouts, derrotas nos shootouts} (separada de V/E/D do futebol). */
    public int[] recordeKings() {
        br.futebas.mundo.Tecnico t = m.tecnico;
        return t == null ? new int[5] : new int[]{t.kingsJogos, t.kingsVitorias, t.kingsDerrotas, t.kingsVitoriasShootout, t.kingsDerrotasShootout};
    }

    /** Linhas da carreira do técnico só da Kings ({@code TemporadaTecnico.kings}); as do futebol continuam em {@code historia.carreiraTecnico}. */
    public List<br.futebas.historia.Historia.TemporadaTecnico> carreiraKings() {
        List<br.futebas.historia.Historia.TemporadaTecnico> l = new ArrayList<>();
        for (br.futebas.historia.Historia.TemporadaTecnico tt : m.historia.carreiraTecnico) if (tt.kings) l.add(tt);
        return l;
    }

    // =================================================================== vagas de técnico (aba Vagas)
    /**
     * Uma linha da aba Vagas: {@code tipo} "clube" ou "kings"; {@code interesse} "Quer você"
     * (o clube já quer: dá para aceitar direto), "Interessado", "Talvez" ou "Fora do seu alcance";
     * {@code situacao} null, "Aguardando resposta" ou "Recusada". {@code abertaEm}/{@code fechaEm} em
     * semanas absolutas. {@code proposta} = o convite pendente (se houver), {@code vaga} = a vaga do clube.
     */
    public record VagaEmprego(String tipo, String id, String nome, String onde, String pais, double reputacao, String motivo,
                              int abertaEm, int fechaEm, String interesse, String situacao, PropostaTecnico proposta,
                              br.futebas.ia.Vaga vaga) {
        public boolean podeAceitar() { return proposta != null || "clube".equals(tipo) && "Interessado".equals(interesse) && situacao == null; }

        public boolean podeCandidatar() { return proposta == null && situacao == null; }

        /**
         * Grupo na aba Vagas (requisito): 0 = propostas que você recebeu, 1 = vagas a que você
         * pode se candidatar, 2 = vagas acima do seu calibre ("Fora do seu alcance").
         */
        public int grupo() { return proposta != null ? 0 : "Fora do seu alcance".equals(interesse) ? 2 : 1; }
    }

    /** Títulos dos grupos da aba Vagas, na ordem de {@link VagaEmprego#grupo()}. */
    public static final String[] GRUPOS_VAGAS = {"Propostas recebidas", "Pode se candidatar", "Acima do seu calibre"};

    private static String interesse(double chance) {
        return chance >= 0.75 ? "Interessado" : chance >= 0.4 ? "Talvez" : "Fora do seu alcance";
    }

    /**
     * Vagas de técnico em aberto: convites pendentes (clubes e times da Kings que querem você),
     * vagas dos clubes que demitiram o técnico (fecham em 1 a 4 semanas) e, na carreira, os times
     * da Kings sem técnico. Nunca o seu próprio emprego. Ordem (requisito): propostas recebidas,
     * depois as vagas a que você pode se candidatar e por último as acima do seu calibre; dentro de
     * cada grupo, as mais novas primeiro.
     */
    public List<VagaEmprego> vagasDeEmprego() {
        List<VagaEmprego> l = new ArrayList<>();
        if (m.tecnico == null) return l;
        java.util.Set<String> vistos = new java.util.HashSet<>();
        if (m.vagasRecusadas != null) vistos.addAll(m.vagasRecusadas); // recusadas não aparecem
        int agora = m.semanaAbsoluta();
        for (PropostaTecnico p : m.propostasTecnico) {
            if (!"pendente".equals(p.estado) || p.selecaoPais != null) continue;
            if (p.kingsTimeId != null) {
                LigaKings.TimeKings t = sim.kings.timeKings(p.kingsTimeId);
                LigaKings lg = ligaKingsDoTime(p.kingsTimeId);
                if (t == null || p.kingsTimeId.equals(m.tecnico.kingsTimeId) || !vistos.add(t.id)) continue;
                l.add(new VagaEmprego("kings", t.id, t.nome, lg == null ? "Kings League" : lg.nome, lg == null ? null : lg.pais, 0,
                        "Quer contratar você", p.ano == 0 ? agora : br.futebas.mundo.Calendario.absoluta(p.ano, p.semana), p.semanaLimite, "Quer você", null, p, null));
            } else {
                Clube c = m.clube(p.clubeId);
                if (c == null || c.humano || !vistos.add(c.id)) continue;
                br.futebas.ia.Vaga v = vagaAberta(c.id);
                l.add(new VagaEmprego("clube", c.id, c.nome, divisaoTexto(c), c.pais, c.reputacao, v != null ? v.motivo : "Quer contratar você",
                        p.ano == 0 ? agora : br.futebas.mundo.Calendario.absoluta(p.ano, p.semana), p.semanaLimite, "Quer você", null, p, v));
            }
        }
        if (m.vagas != null) for (br.futebas.ia.Vaga v : m.vagas) {
            if (!v.aberta()) continue;
            Clube c = m.clube(v.clubeId);
            if (c == null || c.humano || !vistos.add(c.id)) continue;
            String sit = v.respostaEm > 0 && v.resposta == null ? "Aguardando resposta" : "recusada".equals(v.resposta) ? "Recusada" : null;
            l.add(new VagaEmprego("clube", c.id, c.nome, divisaoTexto(c), c.pais, c.reputacao, v.motivo, v.abertaEm, v.fechaEm,
                    interesse(sim.diretoria.chanceCandidatura(c)), sit, null, v));
        }
        if (!jogoNovo()) for (LigaKings.TimeKings t : sim.kings.vagas()) {
            if (t.vagaAte <= 0 || !vistos.add(t.id)) continue;
            LigaKings lg = ligaKingsDoTime(t.id);
            double chance = Math.min(0.9, 0.25 + m.tecnico.reputacao / 100.0);
            l.add(new VagaEmprego("kings", t.id, t.nome, lg == null ? "Kings League" : lg.nome, lg == null ? null : lg.pais, 0, "Sem técnico",
                    Math.max(0, t.vagaAte - 6), t.vagaAte, interesse(chance), null, null, null));
        }
        l.sort(Comparator.comparingInt(VagaEmprego::grupo).thenComparingInt(x -> -x.abertaEm()).thenComparing(VagaEmprego::nome));
        return l;
    }

    /**
     * Recusa uma vaga/convite (regra do jogo): o convite fica recusado e a vaga some da aba Vagas.
     * Uma vaga nova do mesmo clube (outra demissão) volta a aparecer.
     */
    public boolean recusarVaga(VagaEmprego v) {
        if (v == null) return false;
        if (v.proposta() != null) recusarEmprego(v.proposta().id);
        if (m.vagasRecusadas == null) m.vagasRecusadas = new ArrayList<>();
        if (!m.vagasRecusadas.contains(v.id())) m.vagasRecusadas.add(v.id());
        return true;
    }

    private br.futebas.ia.Vaga vagaAberta(String clubeId) {
        if (m.vagas != null) for (br.futebas.ia.Vaga v : m.vagas) if (v.aberta() && clubeId.equals(v.clubeId)) return v;
        return null;
    }

    private String divisaoTexto(Clube c) {
        br.futebas.mundo.Pais p = m.pais(c.pais);
        String pais = p == null ? c.pais : p.nome;
        return c.divisao > 0 ? pais + " · " + c.divisao + "ª divisão" : pais;
    }

    /**
     * Candidatura a uma vaga: clube = a diretoria responde em 1 a 2 semanas (nota curta na caixa
     * com a resposta; aceita = vira convite "Quer você"); time da Kings = o presidente decide na
     * hora (regra da candidatura da Kings). Devolve falso se não dá para se candidatar.
     */
    public boolean candidatarVaga(VagaEmprego v) {
        if (v == null || !v.podeCandidatar()) return false;
        if ("kings".equals(v.tipo())) return candidatarKings(v.id()) != null;
        if (v.vaga() == null || !v.vaga().aberta() || v.vaga().respostaEm > 0) return false;
        int agora = m.semanaAbsoluta();
        v.vaga().respostaEm = agora + 1 + Math.floorMod(v.id().hashCode() + agora, 2);
        if (m.multi() && m.tecnico != null) v.vaga().candidato = m.tecnico.nome;
        v.vaga().fechaEm = Math.max(v.vaga().fechaEm, v.vaga().respostaEm);
        return true;
    }

    /**
     * Aceita a vaga (deixa o emprego atual: a interface confirma antes). Vale para quem já quer
     * você (convite pendente) e para clubes "Interessado" (a diretoria fecha na hora).
     */
    public boolean aceitarVaga(VagaEmprego v) {
        if (v == null || !v.podeAceitar()) return false;
        if (v.proposta() != null) return aceitarEmprego(v.proposta().id);
        Clube c = m.clube(v.id());
        if (c == null) return false;
        sim.diretoria.proporAoHumano(c);
        for (PropostaTecnico p : m.propostasTecnico) if (c.id.equals(p.clubeId) && "pendente".equals(p.estado)) return aceitarEmprego(p.id);
        return false;
    }

    /** Lista de prioridades para o próximo draft do seu time da Kings. */
    public void definirPrioridadesDraft(List<Integer> jogadorIds) {
        if (m.tecnico == null || m.tecnico.kingsTimeId == null) return;
        for (LigaKings l : m.kings) {
            LigaKings.TimeKings t = l.time(m.tecnico.kingsTimeId);
            if (t != null) t.prioridadesDraft = new ArrayList<>(jogadorIds);
        }
    }

    public List<Jogador> elegiveisDraft(String ligaId, int limite) {
        for (LigaKings l : m.kings) {
            if (!l.id.equals(ligaId)) continue;
            List<Jogador> e = sim.kings.elegiveis(l, false);
            return e.size() > limite ? new ArrayList<>(e.subList(0, limite)) : e;
        }
        return List.of();
    }

    public ResultadoPartida ultimaPartidaKings() { return sim.kings.ultimaNarrada; }

    /**
     * Draft da Kings esperando você (o seu time está na vez); null se não há. Campos: {@code ordem}
     * (id do time por escolha), {@code atual}, {@code vez()}, {@code disponiveis}, {@code escolhas}.
     * Se você avançar a semana sem escolher, na semana seguinte o auxiliar escolhe pelas prioridades.
     */
    public LigaKings.EstadoDraft estadoDraft() {
        LigaKings l = sim.kings.draftPendente();
        return l == null ? null : l.draftAtual;
    }

    /** Liga do draft pendente (para nomes dos times). */
    public LigaKings ligaDoDraft() { return sim.kings.draftPendente(); }

    /** Disponíveis no draft, os mais valiosos primeiro, com filtros (posição null = todas; idadeMax 0 = sem limite). */
    public List<Jogador> disponiveisNoDraft(br.futebas.mundo.Posicao posicao, int idadeMax, int limite) {
        LigaKings.EstadoDraft e = estadoDraft();
        List<Jogador> l = new ArrayList<>();
        if (e == null) return l;
        for (int id : e.disponiveis) {
            Jogador j = m.ativo(id);
            if (j == null || (posicao != null && j.posicao != posicao) || (idadeMax > 0 && j.idade(m.ano) > idadeMax)) continue;
            l.add(j);
            if (l.size() >= limite) break;
        }
        return l;
    }

    /** Sua escolha no draft (as da IA seguem sozinhas até a sua próxima vez). */
    public boolean escolherNoDraft(int jogadorId) { return sim.kings.escolherNoDraft(jogadorId); }

    /** Dispensa um jogador do seu time da Kings (fica sem time e pode ser contratado por outro). */
    public boolean dispensarDaKings(int jogadorId) {
        return m.tecnico != null && m.tecnico.kingsTimeId != null && sim.kings.dispensar(m.tecnico.kingsTimeId, jogadorId);
    }

    /**
     * Proposta de wildcard do seu time da Kings a um jogador elegível sem time na Kings (regra do jogo:
     * wildcard é por proposta). Sem contrato, o jogador responde; com contrato, primeiro o clube. A
     * resposta chega na caixa de entrada em até uma semana. Falso se não deu para propor.
     */
    public boolean contratarNaKings(int jogadorId) {
        return m.tecnico != null && m.tecnico.kingsTimeId != null && sim.kings.contratar(m.tecnico.kingsTimeId, jogadorId);
    }

    /** Custo de uma proposta de wildcard ao jogador (taxa ao clube, se ele tem contrato, + salário da temporada). */
    public long custoWildcardKings(int jogadorId) {
        Jogador j = m.ativo(jogadorId);
        return j == null ? 0 : sim.kings.custoWildcard(j);
    }

    /** Taxa ao clube (0 sem contrato) e salário da temporada de uma proposta de wildcard: {taxa, salário}. */
    public long[] partesWildcardKings(int jogadorId) {
        Jogador j = m.ativo(jogadorId);
        if (j == null) return new long[]{0, 0};
        long custo = sim.kings.custoWildcard(j), sal = sim.kings.salarioKings(j);
        return new long[]{custo - sal, sal};
    }

    /** Caixa do seu time da Kings livre para propostas (caixa menos o reservado nas propostas em andamento). */
    public long caixaKingsDisponivel() {
        LigaKings.TimeKings t = meuTimeKings();
        LigaKings l = t == null ? null : ligaKingsDoTime(t.id);
        return t == null ? 0 : br.futebas.kings.DinheiroKings.disponivel(l, t);
    }

    /** Dinheiro reservado nas propostas em andamento do seu time da Kings. */
    public long caixaKingsComprometido() {
        LigaKings.TimeKings t = meuTimeKings();
        LigaKings l = t == null ? null : ligaKingsDoTime(t.id);
        return t == null ? 0 : br.futebas.kings.DinheiroKings.comprometido(l, t);
    }

    // =================================================================== Kings: treino (o mesmo do futebol)
    /** Foco do treino de um atleta do seu time da Kings ("principais", "secundarias" ou null = auxiliar). */
    public void definirTreinoKings(String chave, String treino) {
        LigaKings.TimeKings t = meuTimeKings();
        if (t == null || chave == null) return;
        for (LigaKings.AtletaKings a : t.elenco) if (chave.equals(a.chave())) a.treino = treino;
    }

    /** O mesmo foco para o elenco inteiro do seu time da Kings. */
    public void definirTreinoKingsElenco(String treino) {
        LigaKings.TimeKings t = meuTimeKings();
        if (t != null) for (LigaKings.AtletaKings a : t.elenco) a.treino = treino;
    }

    /** Idade de um atleta da Kings (jogador do mundo, ano de nascimento ou 28). */
    public int idadeKings(LigaKings.AtletaKings a) { return br.futebas.kings.TreinoKings.idade(m, a); }

    /** Quanto a força do atleta muda por semana com o foco atual (escala da Kings). */
    public double evolucaoSemanalKings(LigaKings.AtletaKings a) {
        return a == null || a.presidente ? 0 : br.futebas.kings.TreinoKings.delta(idadeKings(a), a.treino);
    }

    // =================================================================== Kings: escalação 7x7 do técnico humano
    /** Formações 7x7 da Kings (goleiro + linhas). */
    public List<String> formacoesKings() { return br.futebas.kings.MotorKings.FORMACOES; }

    /** Vagas da formação (a 1ª é o goleiro), para desenhar o campo. */
    public List<br.futebas.mundo.Posicao> vagasKings(String formacao) {
        return br.futebas.kings.MotorKings.vagasDaFormacao(formacao, 7);
    }

    /** Os 7 que entram em campo no seu time da Kings (a sua escalação ou, sem ela, os 7 mais fortes). */
    public List<LigaKings.AtletaKings> escaladosKings() {
        LigaKings.TimeKings t = meuTimeKings();
        if (t == null) return List.of();
        List<LigaKings.AtletaKings> l = br.futebas.kings.MotorKings.escalados(t);
        if (l != null) return l.size() > 7 ? new ArrayList<>(l.subList(0, 7)) : l;
        // automática: cada um na sua posição (regra do jogo), goleiro de ofício no gol
        List<LigaKings.AtletaKings> auto = br.futebas.kings.MotorKings.escalacaoAutomatica(t, t.formacao);
        return auto.size() > 7 ? new ArrayList<>(auto.subList(0, 7)) : auto;
    }

    /**
     * Escalação do seu time da Kings: formação e as chaves dos 7 ({@code AtletaKings.chave()}, a 1ª
     * é o goleiro). {@code titulares} vazio = automática. O motor da Kings usa a escalação (força
     * com o encaixe de cada um na vaga e os autores dos gols só entre os 7).
     */
    public boolean definirEscalacaoKings(String formacao, List<String> titulares) {
        LigaKings.TimeKings t = meuTimeKings();
        if (t == null) return false;
        if (formacao != null && br.futebas.kings.MotorKings.FORMACOES.contains(formacao)) t.formacao = formacao;
        List<String> l = new ArrayList<>();
        if (titulares != null) for (String k : titulares) {
            if (k == null || l.contains(k)) continue;
            for (LigaKings.AtletaKings a : t.elenco) if (k.equals(a.chave())) l.add(k);
        }
        t.titulares = l.size() > 7 ? new ArrayList<>(l.subList(0, 7)) : l;
        return true;
    }

    /** Ordem dos cobradores de shootout (chaves de atleta; vazio = automático). */
    public void definirCobradoresKings(List<String> chaves) {
        LigaKings.TimeKings t = meuTimeKings();
        if (t != null) t.cobradores = chaves == null ? new ArrayList<>() : new ArrayList<>(chaves);
    }

    public void definirCapitaoKings(String chave) {
        LigaKings.TimeKings t = meuTimeKings();
        if (t != null) t.capitao = chave;
    }

    /** Presidente que bate o pênalti do presidente (null = eles revezam). */
    public void definirBatedorPresidente(String nome) {
        LigaKings.TimeKings t = meuTimeKings();
        if (t != null) t.batedorPresidente = nome != null && t.nomesPresidentes().contains(nome) ? nome : null;
    }

    /** Força média dos 7 escalados com o encaixe na formação (0 sem time). */
    public double forcaEscalacaoKings() {
        LigaKings.TimeKings t = meuTimeKings();
        if (t == null) return 0;
        List<LigaKings.AtletaKings> esc = escaladosKings();
        List<br.futebas.mundo.Posicao> vagas = vagasKings(t.formacao);
        boolean manual = t.titulares != null && !t.titulares.isEmpty(), gk = br.futebas.kings.MotorKings.temGoleiro(t);
        double s = 0;
        for (int i = 0; i < esc.size(); i++) s += esc.get(i).forca * (manual ? br.futebas.kings.MotorKings.encaixe(esc.get(i).posicao, vagas.get(i), gk) : 1);
        return esc.isEmpty() ? 0 : s / 7;
    }

    /**
     * Mercado da Kings para o seu time: quem pode ser contratado agora (sem time na Kings e elegível
     * pelas regras da liga: passe livre, divisões de baixo, veteranos, aposentados que escolheram a Kings).
     */
    public List<Jogador> mercadoKings(int limite) {
        LigaKings.TimeKings t = meuTimeKings();
        LigaKings l = t == null ? null : ligaKingsDoTime(t.id);
        if (l == null) return List.of();
        List<Jogador> e = sim.kings.elegiveis(l, false);
        e.removeIf(j -> j.kingsTimeId != null);
        return e.size() > limite ? new ArrayList<>(e.subList(0, limite)) : e;
    }

    /** De onde vem um jogador do draft/mercado da Kings: aposentado, passe livre, divisão inferior ou clube. */
    public String origemKings(Jogador j) {
        if (j == null) return "";
        if (j.status == br.futebas.mundo.StatusJogador.APOSENTADO) return "Aposentado";
        if (j.status == br.futebas.mundo.StatusJogador.LIVRE) return "Passe livre";
        Clube c = m.clube(j.clubeId);
        if (c == null) return "Sem clube";
        if (c.divisao == 0) return "Sem divisão nacional · " + c.nome;
        return "Divisão inferior · " + c.nome; // 1ª divisão nunca entra (regra do jogo)
    }

    /** Vagas de wildcard abertas no seu time da Kings e propostas em andamento: {vagas, propostas}. */
    public int[] vagasWildcardKings() {
        LigaKings.TimeKings t = meuTimeKings();
        LigaKings l = t == null ? null : ligaKingsDoTime(t.id);
        if (l == null) return new int[]{0, 0};
        return new int[]{br.futebas.kings.Kings.vagasWildcard(t), br.futebas.kings.Kings.pendentes(l, t)};
    }

    /** Drafts já feitos da liga do seu time (mais novo primeiro); vazio sem time. */
    public List<LigaKings.EstadoDraft> draftsAnteriores(String ligaId) {
        for (LigaKings l : m.kings) if (l.id.equals(ligaId)) {
            List<LigaKings.EstadoDraft> r = new ArrayList<>(l.draftsAnteriores == null ? List.of() : l.draftsAnteriores);
            java.util.Collections.reverse(r);
            return r;
        }
        return List.of();
    }

    /** Acompanhamento ao vivo do seu jogo da Kings (carta secreta e pênalti do presidente); null = a IA decide. */
    public void definirAoVivoKings(br.futebas.kings.AoVivoKings aoVivo) { sim.kings.aoVivo = aoVivo; }

    // =================================================================== interface (ao vivo e fontes)
    /** Liga (ou desliga, com null) o acompanhamento ao vivo do jogo do técnico. */
    public void definirAoVivo(br.futebas.partida.AoVivo aoVivo) { sim.aoVivo = aoVivo; }

    /**
     * Os outros jogos da rodada do último jogo ao vivo do técnico (mesma competição e data), com
     * os eventos resumidos (gol, cartão, pênalti). O {@code AoVivo.outrosJogos} recebe a mesma lista
     * antes do apito inicial.
     */
    public List<br.futebas.partida.JogoParalelo> outrosJogosDaRodada() { return sim.ultimosParalelos; }

    /**
     * Renovação automática: contratos que vencem são renovados sozinhos (salário pedido pelo
     * jogador) para quem tem força ≥ {@code forcaMinima} (0 = quem está no nível do time).
     */
    public void definirRenovacaoAutomatica(boolean ativa, int forcaMinima) {
        if (m.tecnico == null) return;
        m.tecnico.renovacaoAutomatica = ativa;
        m.tecnico.renovacaoForcaMinima = Math.max(0, forcaMinima);
    }

    public boolean renovacaoAutomatica() { return m.tecnico != null && m.tecnico.renovacaoAutomatica; }

    public int renovacaoForcaMinima() { return m.tecnico == null ? 0 : m.tecnico.renovacaoForcaMinima; }

    // =================================================================== camada histórica
    /** Uma linha de carreira: {@code real} = dados até 2026 (banco); senão, temporada simulada no save. */
    public record LinhaCarreira(String clubeId, String clube, int anoInicio, int anoFim, boolean emprestimo, Integer jogos, Integer gols, boolean real) {}

    /** Carreira do jogador: a real até 2026 (se houver no banco) e a simulada desde então. */
    public List<LinhaCarreira> carreiraDoJogador(int jogadorId) {
        List<LinhaCarreira> l = new ArrayList<>();
        Jogador j = m.jogador(jogadorId);
        if (j == null) return l;
        if (j.dbId != null) {
            for (var c : sim.db.historico().porJogador.getOrDefault(j.dbId, List.of())) {
                l.add(new LinhaCarreira(c.clubeId(), c.clubeNome(), c.anoInicio() == null ? 0 : c.anoInicio(), c.anoFim() == null ? 0 : c.anoFim(),
                        c.emprestimo(), c.jogos(), c.gols(), true));
            }
        }
        for (var p : j.carreira) l.add(new LinhaCarreira(p.clubeId, p.clubeNome, p.ano, Math.max(p.ano, p.anoFim), p.emprestimo, p.jogos, p.gols, false));
        return l;
    }

    /** Uma lenda: {@code tipo} "real" (banco, {@code lendaId}) ou "jogo" (criada no save, {@code jogadorId}). */
    public record LendaInfo(String tipo, Integer lendaId, Integer jogadorId, String nome, String pais, String posicao, Integer forcaAuge,
                            Double fama, String resumo, String fotoPng, List<String> clubes) {}

    /** Lendas reais e do jogo, filtros opcionais (null = todos); as mais famosas primeiro. */
    public List<LendaInfo> lendas(String pais, String clubeId, br.futebas.mundo.Posicao posicao) {
        List<LendaInfo> l = new ArrayList<>();
        var h = sim.db.historico();
        for (var d : h.lendas.values()) {
            List<String> clubes = new ArrayList<>();
            for (var c : h.porLenda.getOrDefault(d.id(), List.of())) if (c.clubeId() != null && !clubes.contains(c.clubeId())) clubes.add(c.clubeId());
            l.add(new LendaInfo("real", d.id(), null, d.nomeConhecido() != null ? d.nomeConhecido() : d.nome(), d.pais(), d.posicao(), d.forcaAuge(),
                    d.fama(), d.resumo(), d.fotoPng(), clubes));
        }
        for (var d : m.historia.lendas) l.add(new LendaInfo("jogo", null, d.jogadorId, d.nome, d.pais, d.posicao, d.forcaAuge, d.fama, d.resumo, null, d.clubes));
        l.removeIf(x -> (pais != null && !pais.equalsIgnoreCase(x.pais())) || (clubeId != null && !x.clubes().contains(clubeId))
                || (posicao != null && !posicao.name().equalsIgnoreCase(x.posicao()) && !posicao.nome.equalsIgnoreCase(x.posicao())));
        l.sort(Comparator.comparingDouble((LendaInfo x) -> -(x.fama() == null ? 0 : x.fama())).thenComparing(LendaInfo::nome));
        return l;
    }

    /** Uma vaga do melhor 11 de todos os tempos. */
    public record VagaOnze(String posicao, String nome, Integer lendaId, Integer jogadorId, boolean doJogo, String criterio) {}

    /**
     * Melhor 11 de todos os tempos do clube: o real (banco) e, por cima, os grandes do save (lendas
     * do jogo e jogadores com muitos jogos e força alta no clube) quando superam o titular da vaga.
     */
    public List<VagaOnze> onzeHistorico(String clubeId) {
        var h = sim.db.historico();
        List<VagaOnze> l = new ArrayList<>();
        java.util.Map<String, Integer> notaReal = new java.util.HashMap<>();
        for (var o : h.onze) {
            if (!clubeId.equals(o.clubeId())) continue;
            String nome = null;
            Integer auge = null;
            if (o.lendaId() != null && h.lendas.containsKey(o.lendaId())) {
                var d = h.lendas.get(o.lendaId());
                nome = d.nomeConhecido() != null ? d.nomeConhecido() : d.nome();
                auge = d.forcaAuge();
            } else if (o.jogadorId() != null && sim.db.jogadores.containsKey(o.jogadorId())) {
                var dj = sim.db.jogadores.get(o.jogadorId());
                nome = dj.nomeExibicao();
                auge = dj.forca();
            }
            l.add(new VagaOnze(o.posicaoSlot(), nome, o.lendaId(), o.jogadorId(), false, o.criterio()));
            notaReal.put(o.posicaoSlot() + "#" + (l.size() - 1), auge == null ? 80 : auge);
        }
        // grandes do save no clube: força no auge + bônus por jogos (só quem tem 100+ jogos lá)
        record Cand(int id, String nome, String pos, double nota) {}
        List<Cand> cands = new ArrayList<>();
        java.util.function.Consumer<Jogador> ver = j -> {
            int jogos = 0, auge = 0;
            for (var p : j.carreira) if (clubeId.equals(p.clubeId)) { jogos += p.jogos; auge = Math.max(auge, p.forca); }
            if (jogos >= 100) cands.add(new Cand(j.id, j.nomeExibicao(), j.posicao.name(), auge + Math.min(8, jogos / 50.0)));
        };
        for (Jogador j : m.jogadores) if (!j.amador() && !j.carreira.isEmpty()) ver.accept(j);
        for (var a : m.arquivados) if (a.jogosCarreira >= 100) ver.accept(a.esboco());
        cands.sort(Comparator.comparingDouble((Cand c) -> -c.nota()));
        java.util.Set<Integer> usados = new java.util.HashSet<>();
        for (int i = 0; i < l.size(); i++) {
            VagaOnze v = l.get(i);
            double atual = notaReal.getOrDefault(v.posicao() + "#" + i, 80);
            for (Cand c : cands) {
                if (usados.contains(c.id()) || !mesmaVaga(v.posicao(), c.pos()) || c.nota() <= atual) continue;
                l.set(i, new VagaOnze(v.posicao(), c.nome(), null, c.id(), true, "no jogo: auge e jogos pelo clube"));
                usados.add(c.id());
                break;
            }
        }
        if (l.isEmpty()) {
            // sem 11 real no banco: monta só com o save (4-3-3)
            String[] slots = {"GOL", "LD", "ZAG", "ZAG", "LE", "VOL", "MC", "MEI", "PD", "CA", "PE"};
            for (String s : slots) for (Cand c : cands) {
                if (usados.contains(c.id()) || !mesmaVaga(s, c.pos())) continue;
                l.add(new VagaOnze(s, c.nome(), null, c.id(), true, "no jogo"));
                usados.add(c.id());
                break;
            }
        }
        return l;
    }

    private static boolean mesmaVaga(String slot, String pos) {
        if (slot == null || pos == null) return false;
        String a = slot.toUpperCase(java.util.Locale.ROOT), b = pos.toUpperCase(java.util.Locale.ROOT);
        if (a.equals(b)) return true;
        java.util.Map<String, String> setor = java.util.Map.ofEntries(java.util.Map.entry("GOL", "G"), java.util.Map.entry("ZAG", "D"), java.util.Map.entry("LD", "D"),
                java.util.Map.entry("LE", "D"), java.util.Map.entry("VOL", "M"), java.util.Map.entry("MC", "M"), java.util.Map.entry("MEI", "M"),
                java.util.Map.entry("PE", "A"), java.util.Map.entry("PD", "A"), java.util.Map.entry("CA", "A"));
        return setor.containsKey(a) && setor.get(a).equals(setor.get(b)) && !a.equals("GOL");
    }

    /** Campeão de uma temporada: {@code real} = até 2025/26 (banco); senão, do save. */
    public record CampeaoInfo(String temporada, String campeaoId, String campeao, String viceId, String vice, boolean real) {}

    /** Campeões da competição: os reais até 2025/26 e os do save, em ordem. */
    public List<CampeaoInfo> campeoes(String competicaoId) {
        List<CampeaoInfo> l = new ArrayList<>();
        java.util.Set<String> temporadas = new java.util.HashSet<>();
        for (var t : m.historia.titulosDe(competicaoId)) {
            l.add(new CampeaoInfo(t.temporada != null ? t.temporada : String.valueOf(t.ano), t.campeaoId, t.campeao, t.viceId, t.vice, false));
            temporadas.add(t.temporada != null ? t.temporada : String.valueOf(t.ano));
        }
        for (var c : sim.db.historico().campeoes) {
            if (!competicaoId.equals(c.competicaoId()) || temporadas.contains(c.temporada())) continue;
            l.add(new CampeaoInfo(c.temporada(), c.campeaoClubeId(), c.campeaoNome(), c.viceClubeId(), c.viceNome(), true));
        }
        l.sort(Comparator.comparing(CampeaoInfo::temporada));
        return l;
    }

    // =================================================================== técnicos da IA
    /** Técnico da IA pelo id (ficha). */
    public br.futebas.mundo.Treinador treinador(int id) { return m.treinador(id); }

    /** Técnico da IA do clube (null no seu clube ou se estiver vago). */
    public br.futebas.mundo.Treinador treinadorDoClube(String clubeId) {
        Clube c = m.clube(clubeId);
        return c == null ? null : m.treinador(c.treinadorId);
    }

    /** Técnico da IA da seleção. */
    public br.futebas.mundo.Treinador treinadorDaSelecao(String pais) {
        var s = m.selecao(pais);
        return s == null ? null : m.treinador(s.treinadorId);
    }

    /** Técnicos sem emprego (mercado), os mais bem avaliados primeiro. */
    public List<br.futebas.mundo.Treinador> treinadoresLivres(int limite) {
        List<br.futebas.mundo.Treinador> l = new ArrayList<>();
        for (var t : m.treinadores) if (t.livre()) l.add(t);
        l.sort(Comparator.comparingDouble((br.futebas.mundo.Treinador t) -> -t.avaliacao).thenComparingInt(t -> t.id));
        return l.size() > limite ? new ArrayList<>(l.subList(0, limite)) : l;
    }

    // =================================================================== pesquisa
    /**
     * Resultado da pesquisa. {@code tipo}: "competicao" (id = id da competição), "clube" (id do
     * clube), "selecao" (id = país), "tecnico" (id = id do técnico da IA, ou "humano"), "jogador"
     * (id = id do jogador; aposentados arquivados também).
     */
    public record ResultadoBusca(String tipo, String id, String nome, String detalhe) {}

    static String normalizar(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Pesquisa por texto (sem acento, sem caixa) em competições, clubes, seleções, técnicos e
     * jogadores. Quem começa com o texto vem antes de quem só contém; ordem por tipo nessa ordem.
     */
    public List<ResultadoBusca> buscar(String texto, int limite) {
        String q = normalizar(texto).trim();
        List<ResultadoBusca> comeca = new ArrayList<>(), contem = new ArrayList<>();
        if (q.length() < 2) return List.of();
        java.util.function.BiConsumer<ResultadoBusca, String> ver = (r, nome) -> {
            String n = normalizar(nome);
            if (n.startsWith(q) || n.contains(" " + q)) comeca.add(r);
            else if (n.contains(q)) contem.add(r);
        };
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (var t : m.torneios) if (vistos.add(t.id)) ver.accept(new ResultadoBusca("competicao", t.id, t.nome, t.pais), t.nome);
        for (var t : m.torneiosAnteriores) if (vistos.add(t.id)) ver.accept(new ResultadoBusca("competicao", t.id, t.nome, t.pais), t.nome);
        for (var dc : sim.db.competicoes.values()) if (vistos.add(dc.id())) ver.accept(new ResultadoBusca("competicao", dc.id(), dc.nome(), dc.pais()), dc.nome());
        for (Clube c : m.clubes.values()) if (!c.virtual && c.existe) ver.accept(new ResultadoBusca("clube", c.id, c.nome, c.pais), c.nome);
        for (var s : m.selecoes.values()) ver.accept(new ResultadoBusca("selecao", s.pais, s.nome, s.confederacao), s.nome);
        for (LigaKings liga : m.kings) {
            for (Torneio t : liga.competicoes) if (vistos.add(t.id)) ver.accept(new ResultadoBusca("competicao", t.id, t.nome, liga.pais), t.nome);
            for (LigaKings.TimeKings tk : liga.times) if (tk.nome != null && vistos.add(tk.id)) ver.accept(new ResultadoBusca("kings", tk.id, tk.nome, liga.nome), tk.nome);
        }
        for (Torneio t : m.kingsMundiais) if (vistos.add(t.id)) ver.accept(new ResultadoBusca("competicao", t.id, t.nome, null), t.nome);
        if (m.tecnico != null) ver.accept(new ResultadoBusca("tecnico", "humano", m.tecnico.nome, "você"), m.tecnico.nome);
        for (var t : m.treinadores) {
            Clube c = m.clube(t.clubeId);
            String det = c != null ? c.nome : t.selecaoPais != null ? "Seleção " + t.selecaoPais : "sem clube";
            ver.accept(new ResultadoBusca("tecnico", String.valueOf(t.id), t.nome, det), t.nome);
        }
        int antes = comeca.size() + contem.size();
        for (Jogador j : m.jogadores) {
            if (j.amador()) continue;
            Clube c = m.clube(j.clubeId);
            ver.accept(new ResultadoBusca("jogador", String.valueOf(j.id), j.nomeExibicao(), c != null ? c.nome : j.status.name().toLowerCase()), j.nomeExibicao());
        }
        for (var a : m.arquivados) ver.accept(new ResultadoBusca("jogador", String.valueOf(a.id), a.nomeExibicao(), "aposentado"), a.nomeExibicao());
        List<ResultadoBusca> l = new ArrayList<>(comeca);
        l.addAll(contem);
        return l.size() > limite ? new ArrayList<>(l.subList(0, limite)) : l;
    }

    // =================================================================== prêmios
    /** Bola de Ouro do ano (Top 50, posição 1 = vencedor); vazio se o ano não terminou. */
    public List<br.futebas.historia.Historia.ItemRanking> bolaDeOuro(int ano) {
        var r = m.historia.ranking("bola_de_ouro", ano);
        return r == null ? List.of() : r.itens;
    }

    /** Bola de Ouro jovem (até 21 anos, Top 50). */
    public List<br.futebas.historia.Historia.ItemRanking> bolaDeOuroJovens(int ano) {
        var r = m.historia.ranking("bola_de_ouro_jovem", ano);
        return r == null ? List.of() : r.itens;
    }

    /** Seleção (time ideal) da liga na temporada que terminou no ano dado; null se não houver. */
    public br.futebas.historia.Historia.SelecaoDaLiga selecaoDaLiga(String competicaoId, int ano) {
        return m.historia.selecaoDaLiga(competicaoId, ano);
    }

    /** Todas as seleções de liga de um ano. */
    public List<br.futebas.historia.Historia.SelecaoDaLiga> selecoesDaLiga(int ano) {
        List<br.futebas.historia.Historia.SelecaoDaLiga> l = new ArrayList<>();
        for (var s : m.historia.selecoesDaLiga) if (s.ano == ano) l.add(s);
        return l;
    }

    /** Jogadores sem clube (passe livre), os melhores primeiro: contratáveis sem taxa, só salário. */
    public List<Jogador> jogadoresLivres(int limite) {
        List<Jogador> l = new ArrayList<>();
        for (Jogador x : m.jogadores) if (x.status == StatusJogador.LIVRE && !x.amador() && x.kingsTimeId == null) l.add(x);
        l.sort(Comparator.comparingDouble((Jogador x) -> -x.forca).thenComparingInt(x -> x.id));
        return l.size() > limite ? new ArrayList<>(l.subList(0, limite)) : l;
    }

    /** Ficha do banco mestre de um jogador real (null para amadores e gerados). */
    public Db.Jogador fichaReal(int jogadorId) {
        Jogador j = m.jogador(jogadorId);
        return j == null || j.dbId == null ? null : sim.db.jogadores.get(j.dbId);
    }


    /** Fontes (URLs) de onde vieram os dados de um jogador real. Lidas sob demanda do banco. */
    public List<Db.Fonte> fontesDoJogador(int jogadorId) {
        Jogador j = m.jogador(jogadorId);
        List<Db.Fonte> l = new ArrayList<>();
        if (j == null || j.dbId == null || sim.db.caminho == null) return l;
        String sql = "SELECT fonte_id FROM jogador_fontes WHERE jogador_id = ?";
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + sim.db.caminho.toAbsolutePath());
             java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, j.dbId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Db.Fonte f = sim.db.fontes.get(rs.getInt(1));
                    if (f != null) l.add(f);
                }
            }
        } catch (java.sql.SQLException e) {
            // sem fontes legíveis: a tela mostra a lista vazia
        }
        return l;
    }

    // =================================================================== sub-20 (só simulação)
    /** Competições sub-20 em andamento (ids {@code <comp>-sub20}), só dos países em detalhe. */
    public List<Torneio> competicoesSub20() { return java.util.Collections.unmodifiableList(m.torneiosSub20); }

    /** Competições sub-20 da temporada anterior de cada calendário (tabelas finais). */
    public List<Torneio> competicoesSub20Anteriores() { return java.util.Collections.unmodifiableList(m.torneiosSub20Anteriores); }

    /** Competições sub-20 de um país (em andamento), divisões primeiro. */
    public List<Torneio> competicoesSub20DoPais(String pais) {
        List<Torneio> l = new ArrayList<>();
        for (Torneio t : m.torneiosSub20) if (pais != null && pais.equals(t.pais)) l.add(t);
        l.sort(Comparator.comparingInt((Torneio t) -> t.divisao == null ? 99 : t.divisao).thenComparing(t -> t.id));
        return l;
    }

    /**
     * A competição sub-20 mais recente com esse id (aceita o id profissional: "bra-1" → "bra-1-sub20"),
     * em andamento ou, se não houver, a da temporada anterior; null se não existe.
     */
    public Torneio competicaoSub20(String id) {
        if (id == null) return null;
        String id20 = br.futebas.mundo.Sub20.ehSub20(id) ? id : id + br.futebas.mundo.Sub20.SUFIXO;
        Torneio achado = null;
        for (Torneio t : m.torneiosSub20) if (t.id.equals(id20) && (achado == null || t.ano > achado.ano)) achado = t;
        if (achado != null) return achado;
        for (Torneio t : m.torneiosSub20Anteriores) if (t.id.equals(id20) && (achado == null || t.ano > achado.ano)) achado = t;
        return achado;
    }

    /** Tabela da competição sub-20 (fase de pontos mais recente; mesma regra de {@link #tabela(String)}). */
    public List<LinhaTabela> tabelaSub20(String id) {
        Torneio t = competicaoSub20(id);
        if (t == null) return List.of();
        FaseExec f = t.faseAtual();
        if (f == null || f.mataMata()) {
            for (int i = t.fases.size() - 1; i >= 0; i--) if (!t.fases.get(i).mataMata() && t.fases.get(i).iniciada) return t.tabela(i, 0);
            return t.tabelaGeral();
        }
        return t.tabela(f.indice, 0);
    }

    /** Partidas do sub-20 do clube nas competições sub-20 em andamento, em ordem de data. */
    public List<Partida> calendarioSub20(String clubeId) {
        List<Partida> l = new ArrayList<>();
        for (Torneio t : m.torneiosSub20) l.addAll(t.partidasDoClube(clubeId));
        l.sort(Comparator.comparingInt((Partida p) -> p.sa).thenComparingLong(p -> p.dia).thenComparingInt(p -> p.data));
        return l;
    }

    /** Elenco sub-20 do clube: base + profissionais com até 20 anos no ano, os mais fortes primeiro. */
    public List<Jogador> elencoSub20(String clubeId) {
        Clube c = m.clube(clubeId);
        // a tela pediu o Sub-20 de um clube que ainda não tem: monta agora (23 jogadores, regra do jogo)
        if (c != null && !c.sub20Montado) sim.sub20.garantir(c);
        return sim.sub20.elenco(c);
    }

    /** Força do time sub-20 do clube (escala dos jogadores). */
    public double forcaSub20(String clubeId) { return sim.sub20.forca(m.clube(clubeId)); }

    /** Os que o sub-20 põe em campo (os 11 mais fortes sem lesão, como na simulação); menos de 11 = juniores anônimos completam. */
    public List<Jogador> onzeSub20(String clubeId) {
        Clube c = m.clube(clubeId);
        if (c != null && !c.sub20Montado) sim.sub20.garantir(c);
        return sim.sub20.onze(c);
    }

    /** Força dos juniores anônimos que completam o sub-20. */
    public double forcaJuniorSub20(String clubeId) {
        Clube c = m.clube(clubeId);
        return c == null ? 0 : br.futebas.mundo.Sub20.forcaJunior(c);
    }

    /** Divisão do clube na pirâmide sub-20 do país (0 = fora dela). */
    public int divisaoSub20(String clubeId) {
        Clube c = m.clube(clubeId);
        return c == null ? 0 : c.divisaoSub20;
    }

    /**
     * Talento 1–10 estimado (CPE do Brasfoot): o talento escondido com erro fixo por jogador —
     * 15% exato, 45% um a menos, 40% um a mais (limitado a 1–10).
     */
    public int talentoEstimado(int jogadorId) {
        Jogador j = m.jogador(jogadorId);
        if (j == null) return 0;
        long h = (m.semente * 131 + j.id) * 0x9E3779B97F4A7C15L;
        int r = (int) Math.floorMod(h >>> 24, 100L);
        int erro = r < 15 ? 0 : r < 60 ? -1 : 1;
        return Math.max(1, Math.min(10, j.talento() + erro));
    }

    /** O talento estimado em estrelas (Brasfoot: 1–2 → 1★, 3 → 1,5★ … 10 → 5★), de meia em meia. */
    public double estrelasTalento(int jogadorId) {
        int t = talentoEstimado(jogadorId);
        return t == 0 ? 0 : Math.max(1, t / 2.0);
    }

    // =================================================================== utilidades
    /** Rótulo de origem para a tela (real / gerado; "Amador" só nos aposentados de saves antigos). */
    public static String origem(Jogador j) {
        return switch (j.origem) {
            case REAL -> "Real";
            case COMPLEMENTAR_TESTE -> "Amador";
            case GERADO -> "Gerado";
        };
    }

    public static boolean ehReal(Jogador j) { return j.origem == Origem.REAL; }

    // =================================================================== páginas (somente leitura)
    /** Nome de qualquer participante: clube, seleção ("sel-XXX") ou time da Kings League. */
    public String nomeParticipante(String id) {
        if (id == null) return "—";
        return m.nomeParticipante(id);
    }

    /**
     * Todas as edições guardadas de uma competição (em andamento, anteriores, Kings e sub-20), da
     * mais recente para a mais antiga. Vazia se o id não existe no save.
     */
    public List<Torneio> edicoes(String competicaoId) {
        List<Torneio> l = new ArrayList<>();
        if (competicaoId == null) return l;
        java.util.function.Consumer<List<Torneio>> add = ts -> {
            for (Torneio t : ts) if (competicaoId.equals(t.id) && !l.contains(t)) l.add(t);
        };
        add.accept(m.torneios);
        add.accept(m.torneiosAnteriores);
        add.accept(m.torneiosSub20);
        add.accept(m.torneiosSub20Anteriores);
        for (LigaKings liga : m.kings) {
            add.accept(liga.competicoes);
            if (liga.torneio != null) add.accept(List.of(liga.torneio));
        }
        add.accept(m.kingsMundiais);
        l.sort(Comparator.comparingInt((Torneio t) -> -t.ano).thenComparing(t -> t.encerrado));
        return l;
    }

    /** Edição mais relevante: a em andamento, senão a última guardada (null se não há nenhuma). */
    public Torneio competicaoQualquer(String competicaoId) {
        Torneio t = m.torneio(competicaoId);
        if (t != null) return t;
        List<Torneio> l = edicoes(competicaoId);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Dados do banco sobre a competição (nome, âmbito, país, divisão); null se não estiver no banco. */
    public Db.Competicao competicaoDoBanco(String competicaoId) {
        return competicaoId == null ? null : sim.db.competicoes.get(competicaoId);
    }

    /** Liga da Kings League a que pertence o time (null se não é time da Kings). */
    public LigaKings ligaKingsDoTime(String timeId) {
        if (timeId == null) return null;
        for (LigaKings l : m.kings) if (l.time(timeId) != null) return l;
        return null;
    }

    /** Liga da Kings dona da competição (null nas Copas do Mundo da Kings e fora da Kings). */
    public LigaKings ligaKingsDaCompeticao(Torneio t) {
        if (t == null) return null;
        for (LigaKings l : m.kings) if (l.torneio == t || l.competicoes.contains(t) || (t.id != null && t.id.startsWith(l.id + "-"))) return l;
        return null;
    }

    /** Partidas de um participante (clube, seleção ou time da Kings) em todas as competições guardadas do ano. */
    public List<Partida> partidasDoParticipante(String id) {
        List<Partida> l = new ArrayList<>();
        java.util.Set<Torneio> vistos = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.function.Consumer<Torneio> add = t -> {
            if (t != null && vistos.add(t) && t.participantes.contains(id)) l.addAll(t.partidasDoClube(id));
        };
        for (Torneio t : m.torneios) add.accept(t);
        for (LigaKings liga : m.kings) for (Torneio t : liga.competicoes) add.accept(t);
        for (Torneio t : m.kingsMundiais) add.accept(t);
        l.sort(Comparator.comparingInt((Partida p) -> p.sa).thenComparingLong(p -> p.dia).thenComparingInt(p -> p.data));
        return l;
    }

    /** Competições do ano de que o participante faz parte (clube, seleção ou time da Kings). */
    public List<Torneio> competicoesDoParticipante(String id) {
        List<Torneio> l = new ArrayList<>();
        for (Torneio t : m.torneios) if (t.participantes.contains(id) && !"amistoso".equals(t.ambito)) l.add(t);
        for (LigaKings liga : m.kings) for (Torneio t : liga.competicoes) if (t.participantes.contains(id) && !l.contains(t)) l.add(t);
        for (Torneio t : m.kingsMundiais) if (t.ano == m.ano && t.participantes.contains(id) && !l.contains(t)) l.add(t);
        return l;
    }

    /** Um título: do save ou da história real (até 2025/26). */
    public record TituloInfo(String competicaoId, String competicao, String ambito, String pais, String temporada, boolean real) {}

    /** Títulos de um participante: os do save e os reais do banco, em ordem de temporada. */
    public List<TituloInfo> titulosDoParticipante(String id) {
        List<TituloInfo> l = new ArrayList<>();
        java.util.Set<String> chaves = new java.util.HashSet<>();
        for (Historia.Titulo t : m.historia.titulos) {
            if (!id.equals(t.campeaoId)) continue;
            String temp = t.temporada != null ? t.temporada : String.valueOf(t.ano);
            l.add(new TituloInfo(t.competicaoId, t.competicao, t.ambito, t.pais, temp, false));
            chaves.add(t.competicaoId + "|" + temp);
        }
        try {
            for (Db.CampeaoHistorico c : sim.db.historico().campeoes) {
                if (!id.equals(c.campeaoClubeId()) || chaves.contains(c.competicaoId() + "|" + c.temporada())) continue;
                Db.Competicao dc = sim.db.competicoes.get(c.competicaoId());
                l.add(new TituloInfo(c.competicaoId(), dc == null ? c.competicaoId() : dc.nome(), dc == null ? null : dc.ambito(),
                        dc == null ? null : dc.pais(), c.temporada(), true));
            }
        } catch (RuntimeException e) {
            // sem camada histórica
        }
        l.sort(Comparator.comparing(TituloInfo::temporada));
        return l;
    }
}
