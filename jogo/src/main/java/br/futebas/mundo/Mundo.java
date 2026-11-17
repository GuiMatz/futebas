package br.futebas.mundo;

import br.futebas.competicao.Torneio;
import br.futebas.historia.Historia;
import br.futebas.ia.PropostaJogador;
import br.futebas.ia.PropostaTecnico;
import br.futebas.kings.LigaKings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Todo o estado de um mundo (o que vai para o arquivo salvo). Só dados: a lógica fica no
 * {@link Simulador}. Coleções com ordem fixa, para que a mesma semente gere o mesmo mundo.
 * O jogo sempre começa em 1º de janeiro de 2026.
 */
public class Mundo {
    public static final int VERSAO = 2;
    /** Ano em que todo jogo começa (e "presente" dos dados reais). */
    public static final int ANO_PRESENTE = 2026;

    public int versao = VERSAO;
    public long semente;
    public Rng rng;
    /** Sempre 2026 (mantido para as telas: "desde 2026"). */
    public int anoInicial = ANO_PRESENTE;
    public int ano;
    /** Próxima semana a ser jogada (1..52). */
    public int semana = 1;
    public String banco;

    public Map<String, Pais> paises = new LinkedHashMap<>();
    public Map<String, Clube> clubes = new LinkedHashMap<>();
    /**
     * Jogadores "vivos" no mundo (em atividade, livres, na base ou aposentados há pouco).
     * Os aposentados antigos vão para {@link #arquivados} em registros compactos; o id não é
     * mais o índice da lista: use {@link #jogador(int)}.
     */
    public List<Jogador> jogadores = new ArrayList<>();
    /** Aposentados antigos em registros compactos, em ordem de id. */
    public List<Arquivado> arquivados = new ArrayList<>();
    /** Técnicos da IA (clubes e seleções), empregados e no mercado. */
    public List<Treinador> treinadores = new ArrayList<>();
    public int proximoTreinador = 1;
    /** Próximo id livre (saves antigos: 0, recalculado ao montar o índice). */
    public int proximoId;
    /** Id do banco → id no mundo. */
    public Map<Integer, Integer> porDbId = new TreeMap<>();

    /** Competições em andamento: as das duas temporadas atuais (jan–dez e ago–mai) e as de seleções. */
    public List<Torneio> torneios = new ArrayList<>();
    /** Temporada anterior de cada calendário (para acesso, rebaixamento, vagas e telas). */
    public List<Torneio> torneiosAnteriores = new ArrayList<>();
    /** Sub-20 (só simulação, pirâmides próprias): competições em andamento e da temporada anterior. */
    public List<Torneio> torneiosSub20 = new ArrayList<>();
    public List<Torneio> torneiosSub20Anteriores = new ArrayList<>();
    public List<LigaKings> kings = new ArrayList<>();
    /** Calendário único jan–dez em todos os países (jogos novos; false = save antigo com ago–mai). */
    public boolean calendarioUnico;
    /** Jogo criado com os times customizados (Kings League IOS): fica no save. */
    public boolean timesCustom;
    /** Copas do Mundo da Kings (Clubs e Nations) deste ano e do anterior. */
    public List<Torneio> kingsMundiais = new ArrayList<>();
    public Map<String, Selecao> selecoes = new LinkedHashMap<>();
    /** Ligas das Nações (UEFA e CONCACAF): divisão de cada país (1 = Liga A). */
    public Map<String, Integer> divisaoNations = new LinkedHashMap<>();
    public List<Leilao> leiloes = new ArrayList<>();

    public Historia historia = new Historia();
    public List<Mensagem> mensagens = new ArrayList<>();
    /**
     * O técnico humano. No modo multi-treinador ({@link #tecnicos}), é o técnico DA VEZ: as listas
     * deste Mundo (caixa, convites, vagas recusadas, carreira) são as dele, e os outros guardam as suas
     * no próprio {@link Tecnico} até a vez deles ({@link #ativar}).
     */
    public Tecnico tecnico;
    /**
     * Modo multi-treinador (hot-seat): todos os técnicos humanos, na ordem das vezes. null (saves
     * antigos e jogo de um técnico só) = só {@link #tecnico}.
     */
    public List<Tecnico> tecnicos;
    /** Índice do técnico da vez em {@link #tecnicos} (os anteriores já mandaram jogar nesta rodada). */
    public int vez;
    public List<PropostaJogador> propostas = new ArrayList<>();
    public List<PropostaTecnico> propostasTecnico = new ArrayList<>();
    public int proximaProposta = 1;
    /** Vagas de técnico abertas nos clubes (aba Vagas; saves antigos: null = vazia). */
    public List<br.futebas.ia.Vaga> vagas = new ArrayList<>();
    /** Clubes/times da Kings cujas vagas o técnico recusou (somem da aba Vagas até abrir uma vaga nova). */
    public List<String> vagasRecusadas = new ArrayList<>();
    public int proximaVaga = 1;
    public int proximoAmistoso = 1;
    /** Avisos do motor (formatos especiais, dados faltando). */
    public List<String> avisos = new ArrayList<>();

    private transient Map<String, Torneio> indiceTorneios;
    private transient Map<Integer, Treinador> porIdTreinador;
    /** Índice transitório id → jogador ativo (null = arquivado ou inexistente). */
    private transient ArrayList<Jogador> porId;

    public Mundo() {}

    /**
     * O jogador com esse id. Para um aposentado arquivado, devolve um esboço montado na hora
     * (aposentado, sem clube; mudar o esboço não muda o mundo).
     */
    public Jogador jogador(int id) {
        Jogador j = ativo(id);
        if (j != null || id < 0) return j;
        Arquivado a = arquivado(id);
        return a == null ? null : a.esboco();
    }

    /** Técnico da IA pelo id (null se não existe mais). */
    public Treinador treinador(Integer id) {
        if (id == null) return null;
        if (porIdTreinador == null) {
            Map<Integer, Treinador> ix = new HashMap<>();
            for (Treinador t : treinadores) ix.put(t.id, t);
            porIdTreinador = ix;
        }
        return porIdTreinador.get(id);
    }

    public void reindexarTreinadores() {
        porIdTreinador = null;
    }

    /** O jogador ativo (não arquivado) com esse id, ou null. */
    public Jogador ativo(int id) {
        ArrayList<Jogador> ix = indice();
        return id >= 0 && id < ix.size() ? ix.get(id) : null;
    }

    /** O registro compacto de um aposentado arquivado, ou null. */
    public Arquivado arquivado(int id) {
        int lo = 0, hi = arquivados.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int v = arquivados.get(mid).id;
            if (v < id) lo = mid + 1;
            else if (v > id) hi = mid - 1;
            else return arquivados.get(mid);
        }
        return null;
    }

    /** Total de jogadores que já passaram pelo mundo (ativos + arquivados). */
    public int totalJogadores() {
        return jogadores.size() + arquivados.size();
    }

    private ArrayList<Jogador> indice() {
        if (porId == null) {
            int max = proximoId;
            for (Jogador j : jogadores) max = Math.max(max, j.id + 1);
            for (Arquivado a : arquivados) max = Math.max(max, a.id + 1);
            proximoId = max;
            ArrayList<Jogador> ix = new ArrayList<>(max + 1024);
            for (int i = 0; i < max; i++) ix.add(null);
            for (Jogador j : jogadores) ix.set(j.id, j);
            porId = ix;
        }
        return porId;
    }

    /**
     * Save menor: o nome do clube na carreira (vivos e arquivados) só vai para o arquivo quando é
     * diferente do nome atual do clube. Devolve o que foi tirado, para {@link #devolverNomes}.
     */
    public List<Object[]> omitirNomesDeClube() {
        List<Object[]> tirados = new ArrayList<>();
        // nome conhecido igual ao nome: não grava (volta ao carregar)
        // e igual à primeira palavra do nome: grava ""
        for (Jogador j : jogadores) {
            String c = codigoNomeConhecido(j.nome, j.nomeConhecido);
            if (c != j.nomeConhecido) {
                tirados.add(new Object[]{j, j.nomeConhecido});
                j.nomeConhecido = c;
            }
        }
        for (Arquivado a : arquivados) {
            String c = codigoNomeConhecido(a.nome, a.nomeConhecido);
            if (c != a.nomeConhecido) {
                tirados.add(new Object[]{a, -1, a.nomeConhecido});
                a.nomeConhecido = c;
            }
        }
        for (Jogador j : jogadores)
            for (Passagem p : j.carreira)
                if (p.clubeNome != null && p.clubeId != null && clubes.containsKey(p.clubeId) && p.clubeNome.equals(clubes.get(p.clubeId).nome)) {
                    tirados.add(new Object[]{p, p.clubeNome});
                    p.clubeNome = null;
                }
        for (Arquivado a : arquivados)
            if (a.clubeIds != null)
                for (int i = 0; i < a.clubeIds.length; i++)
                    if (a.clubeNomes[i] != null && a.clubeIds[i] != null && clubes.containsKey(a.clubeIds[i]) && a.clubeNomes[i].equals(clubes.get(a.clubeIds[i]).nome)) {
                        tirados.add(new Object[]{a, i, a.clubeNomes[i]});
                        a.clubeNomes[i] = null;
                    }
        return tirados;
    }

    private static String codigoNomeConhecido(String nome, String nc) {
        if (nc == null || nome == null) return nc;
        if (nc.equals(nome)) return null;
        if (!nc.isEmpty() && nome.startsWith(nc + " ")) return "";
        return nc;
    }

    private static String nomeConhecidoDe(String nome, String nc) {
        if (nome == null) return nc;
        if (nc == null) return nome;
        if (nc.isEmpty()) {
            int i = nome.indexOf(' ');
            return i > 0 ? nome.substring(0, i) : nome;
        }
        return nc;
    }

    /** Desfaz {@link #omitirNomesDeClube} depois de gravar. */
    public static void devolverNomes(List<Object[]> tirados) {
        for (Object[] t : tirados) {
            if (t[0] instanceof Passagem p) p.clubeNome = (String) t[1];
            else if (t[0] instanceof Jogador j) j.nomeConhecido = (String) t[1];
            else if ((Integer) t[1] < 0) ((Arquivado) t[0]).nomeConhecido = (String) t[2];
            else ((Arquivado) t[0]).clubeNomes[(Integer) t[1]] = (String) t[2];
        }
    }

    /** Ao carregar: nome conhecido ausente = o nome; nome de clube ausente na carreira = o nome atual do clube. */
    public void completarNomesDeClube() {
        for (Jogador j : jogadores) j.nomeConhecido = nomeConhecidoDe(j.nome, j.nomeConhecido);
        for (Arquivado a : arquivados) a.nomeConhecido = nomeConhecidoDe(a.nome, a.nomeConhecido);
        for (Jogador j : jogadores)
            for (Passagem p : j.carreira)
                if (p.clubeNome == null && p.clubeId != null && clubes.containsKey(p.clubeId)) p.clubeNome = clubes.get(p.clubeId).nome;
        for (Arquivado a : arquivados)
            if (a.clubeIds != null)
                for (int i = 0; i < a.clubeIds.length; i++)
                    if (a.clubeNomes[i] == null && a.clubeIds[i] != null && clubes.containsKey(a.clubeIds[i])) a.clubeNomes[i] = clubes.get(a.clubeIds[i]).nome;
    }

    /**
     * Depois de carregar um save: uma cópia só de cada texto repetido (ids e nomes de clubes na
     * carreira, países, posições secundárias), que o Gson cria um por um.
     */
    public void compactarTextos() {
        Arquivado.Interner in = new Arquivado.Interner();
        for (Jogador j : jogadores) {
            j.pais = in.s(j.pais);
            j.pais2 = in.s(j.pais2);
            j.clubeId = in.s(j.clubeId);
            for (Passagem p : j.carreira) {
                p.clubeId = in.s(p.clubeId);
                p.clubeNome = in.s(p.clubeNome);
            }
            for (int i = 0; i < j.caracteristicas.size(); i++) j.caracteristicas.set(i, in.s(j.caracteristicas.get(i)));
        }
        for (Arquivado a : arquivados) {
            a.pais = in.s(a.pais);
            if (a.clubeIds != null) {
                for (int i = 0; i < a.clubeIds.length; i++) {
                    a.clubeIds[i] = in.s(a.clubeIds[i]);
                    a.clubeNomes[i] = in.s(a.clubeNomes[i]);
                }
            }
        }
    }

    /**
     * Antes de salvar (memória/arquivo): números quebrados dos jogadores ficam com 3 casas (a força
     * na tela é inteira); 17 dígitos por atributo eram metade do save. Muda o mundo em memória
     * também, então salvar e continuar dá o mesmo futuro que carregar.
     */
    public void arredondarParaSalvar() {
        for (Jogador j : jogadores) {
            for (int i = 0; i < j.atributos.length; i++) j.atributos[i] = r1(j.atributos[i]);
            j.forca = r3(j.forca);
            j.potencial = r3(j.potencial);
            j.potencialRef = r3(j.potencialRef);
            j.fama = r3(j.fama);
            j.somaNotasTemp = r3(j.somaNotasTemp);
        }
        for (Clube c : clubes.values()) {
            c.reputacao = r3(c.reputacao);
            c.torcida = r3(c.torcida);
            c.estrutura = r3(c.estrutura);
            c.nivelAtual = r3(c.nivelAtual);
            c.confiancaDiretoria = r3(c.confiancaDiretoria);
            c.confiancaTorcida = r3(c.confiancaTorcida);
        }
        for (Treinador t : treinadores) {
            t.avaliacao = r3(t.avaliacao);
            t.reputacao = r3(t.reputacao);
        }
    }

    private static double r1(double v) {
        return Double.isFinite(v) ? Math.round(v * 10.0) / 10.0 : v;
    }

    private static double r3(double v) {
        return Double.isFinite(v) ? Math.round(v * 1000.0) / 1000.0 : v;
    }

    /** Refaz o índice (depois de mexer em {@link #jogadores} por fora). */
    public void reindexar() {
        porId = null;
    }

    /**
     * Aposentado sem história não vai para o arquivo (memória e save): fica só quem alguém ainda
     * procura — seleção, estrela, técnico, Kings, fama, auge ≥ 72, 60+ gols, 200+ jogos, ou real do
     * banco com auge ≥ 65 ou 100+ jogos. {@link #jogador(int)} devolve null para os outros.
     */
    static boolean descartavel(Jogador j) {
        if (j.estrela || j.topMundial || j.virouTecnico || j.kingsPais != null || j.jogosSelecao > 0 || j.fama >= 20) return false;
        int auge = (int) Math.round(j.forca);
        for (Passagem p : j.carreira) auge = Math.max(auge, p.forca);
        if (auge >= 72 || j.golsCarreira >= 60 || j.jogosCarreira >= 200) return false;
        boolean real = j.origem == Origem.REAL;
        return !(real && (auge >= 65 || j.jogosCarreira >= 100));
    }

    /** Ids de jogadores que o histórico cita (artilheiros de títulos, prêmios, rankings, seleções da liga, lendas, despedidas). */
    java.util.Set<Integer> citadosNoHistorico() {
        java.util.Set<Integer> s = new java.util.HashSet<>();
        for (br.futebas.historia.Historia.Titulo t : historia.titulos) if (t.artilheiroId != null) s.add(t.artilheiroId);
        for (br.futebas.historia.Historia.Premio p : historia.premios) if (p.jogadorId != null) s.add(p.jogadorId);
        for (br.futebas.historia.Historia.Ranking r : historia.rankings) for (br.futebas.historia.Historia.ItemRanking i : r.itens) if (i.jogadorId != null) s.add(i.jogadorId);
        for (br.futebas.historia.Historia.SelecaoDaLiga x : historia.selecoesDaLiga) for (br.futebas.historia.Historia.ItemSelecao i : x.time) if (i.jogadorId != null) s.add(i.jogadorId);
        for (br.futebas.historia.Historia.Lenda l : historia.lendas) if (l.jogadorId != null) s.add(l.jogadorId);
        for (br.futebas.historia.Historia.Despedida d : historia.despedidas) if (d.jogadorId != null) s.add(d.jogadorId);
        return s;
    }

    /**
     * Arquivo enxuto (regra do jogo 16/11/2026, saúde do mundo): quem se aposenta sem nunca ter jogado
     * numa 1ª divisão nem pela seleção (e sem ser estrela, técnico ou lenda) vai para o arquivo sem a
     * carreira detalhada. Saves antigos não têm {@link Jogador#jogouElite}: vale também um clube da
     * carreira que hoje está na 1ª divisão.
     */
    boolean enxuto(Jogador j) {
        if (j.jogouElite || j.estrela || j.topMundial || j.virouTecnico || j.jogosSelecao > 0 || j.fama >= 20) return false;
        for (Passagem p : j.carreira) {
            Clube c = p.clubeId == null ? null : clubes.get(p.clubeId);
            if (c != null && c.divisao == 1) return false;
        }
        return true;
    }

    /**
     * Arquiva os aposentados que passam no filtro: saem de {@link #jogadores} e viram
     * registros compactos. Devolve quantos foram arquivados.
     */
    public int arquivar(java.util.function.Predicate<Jogador> filtro) {
        indice();
        Arquivado.Interner in = new Arquivado.Interner();
        List<Arquivado> novos = new ArrayList<>();
        List<Jogador> ficam = new ArrayList<>(jogadores.size());
        java.util.Set<Integer> citados = null;
        for (Jogador j : jogadores) {
            if (j.status == StatusJogador.APOSENTADO && filtro.test(j)) {
                porId.set(j.id, null);
                if (descartavel(j)) continue; // ninguém olha para ele: some do mundo
                boolean enxuto = enxuto(j);
                if (enxuto && j.origem != Origem.REAL && j.golsCarreira < 60 && j.jogosCarreira < 200) {
                    // gerado pelo jogo, sem 1ª divisão nem seleção: só fica se o histórico o cita (16/11/2026)
                    if (citados == null) citados = citadosNoHistorico();
                    if (!citados.contains(j.id)) continue;
                }
                novos.add(Arquivado.de(j, in, enxuto));
            } else ficam.add(j);
        }
        if (ficam.size() == jogadores.size()) return 0;
        jogadores = ficam;
        arquivados.addAll(novos);
        arquivados.sort(java.util.Comparator.comparingInt(a -> a.id));
        return novos.size();
    }

    /**
     * Fecha uma linha da carreira do técnico humano (fim do ano ou troca de clube): clube, J/V/E/D
     * desde a última linha, títulos do ano e o motivo da saída (null = continua).
     */
    public void registrarTemporadaTecnico(String saida) {
        if (tecnico != null && tecnico.clubeId == null && tecnico.kingsTimeId != null) {
            registrarTemporadaKings(saida);
            return;
        }
        if (tecnico == null || tecnico.clubeId == null || clube(tecnico.clubeId) == null) return;
        Historia.TemporadaTecnico tt = new Historia.TemporadaTecnico();
        tt.ano = ano;
        tt.clubeId = tecnico.clubeId;
        tt.clube = clube(tecnico.clubeId).nome;
        int[] k = tecnico.marcoCarreira == null ? new int[4] : tecnico.marcoCarreira;
        tt.jogos = tecnico.jogos - k[0];
        tt.vitorias = tecnico.vitorias - k[1];
        tt.empates = tecnico.empates - k[2];
        tt.derrotas = tecnico.derrotas - k[3];
        for (String s : tecnico.titulos) if (s.startsWith(ano + " ")) tt.titulos.add(s);
        tt.saida = saida;
        historia.carreiraTecnico.add(tt);
        tecnico.marcoCarreira = new int[]{tecnico.jogos, tecnico.vitorias, tecnico.empates, tecnico.derrotas};
    }

    /**
     * Linha da carreira na Kings League (fim do ano, troca de emprego, demissão): time, J/V/D (com os
     * shootouts à parte) desde a última linha da Kings e os títulos da Kings no ano. Balanço 2026-11, rodada 2.
     */
    public void registrarTemporadaKings(String saida) {
        if (tecnico == null || tecnico.kingsTimeId == null || kings == null) return;
        String nome = null;
        for (br.futebas.kings.LigaKings l : kings) for (br.futebas.kings.LigaKings.TimeKings t : l.times) if (t.id.equals(tecnico.kingsTimeId)) nome = t.nome;
        if (nome == null) return;
        Historia.TemporadaTecnico tt = new Historia.TemporadaTecnico();
        tt.ano = ano;
        tt.kings = true;
        tt.clubeId = tecnico.kingsTimeId;
        tt.clube = nome;
        int[] k = tecnico.marcoKings == null || tecnico.marcoKings.length < 5 ? new int[5] : tecnico.marcoKings;
        tt.jogos = tecnico.kingsJogos - k[0];
        tt.vitorias = tecnico.kingsVitorias - k[1];
        tt.derrotas = tecnico.kingsDerrotas - k[2];
        tt.vitoriasShootout = tecnico.kingsVitoriasShootout - k[3];
        tt.derrotasShootout = tecnico.kingsDerrotasShootout - k[4];
        for (String s : tecnico.titulos) if (s.startsWith(ano + " ") && s.toLowerCase(java.util.Locale.ROOT).contains("kings")) tt.titulos.add(s);
        tt.saida = saida;
        historia.carreiraTecnico.add(tt);
        tecnico.marcoKings = new int[]{tecnico.kingsJogos, tecnico.kingsVitorias, tecnico.kingsDerrotas, tecnico.kingsVitoriasShootout, tecnico.kingsDerrotasShootout};
    }

    public Clube clube(String id) {
        return id == null ? null : clubes.get(id);
    }

    public Pais pais(String id) {
        return id == null ? null : paises.get(id);
    }

    public Selecao selecao(String paisOuId) {
        if (paisOuId == null) return null;
        return selecoes.get(paisOuId.startsWith("sel-") ? paisOuId.substring(4) : paisOuId);
    }

    /** Torneio em andamento (o mais recente com esse id). */
    public Torneio torneio(String competicaoId) {
        Torneio achado = null;
        for (Torneio t : torneios) if (t.id.equals(competicaoId) && (achado == null || t.ano >= achado.ano)) achado = t;
        // torneios da Kings ficam na própria liga (não na lista do ano): tabela, partidas e páginas os acham aqui
        if (achado == null && kings != null)
            for (br.futebas.kings.LigaKings l : kings) if (l.torneio != null && l.torneio.id.equals(competicaoId)) achado = l.torneio;
        return achado;
    }

    public Torneio torneioAnterior(String competicaoId) {
        Torneio achado = null;
        for (Torneio t : torneiosAnteriores) if (t.id.equals(competicaoId) && (achado == null || t.ano >= achado.ano)) achado = t;
        return achado;
    }

    /** Edição encerrada mais recente da competição (em andamento ou anterior). */
    public Torneio ultimoEncerrado(String competicaoId) {
        Torneio achado = null;
        for (List<Torneio> l : List.of(torneios, torneiosAnteriores)) {
            for (Torneio t : l) {
                if (!t.id.equals(competicaoId) || !t.encerrado) continue;
                if (achado == null || t.ano > achado.ano) achado = t;
            }
        }
        return achado;
    }

    public int semanaAbsoluta() {
        return Calendario.absoluta(ano, semana);
    }

    public Clube clubeHumano() {
        return tecnico == null ? null : clube(tecnico.clubeId);
    }

    public void mensagem(String tipo, String titulo, String texto) {
        mensagens.add(new Mensagem(ano, semana, tipo, titulo, texto));
    }

    // ===================================================================== multi-treinador
    /** Mais de um técnico humano no mesmo jogo (hot-seat)? */
    public boolean multi() {
        return tecnicos != null && tecnicos.size() > 1;
    }

    /** Os técnicos humanos (no jogo de um técnico só: ele, ou nenhum). */
    public List<Tecnico> todosTecnicos() {
        if (multi()) return tecnicos;
        return tecnico == null ? List.of() : List.of(tecnico);
    }

    /** O técnico humano que treina o clube (null = IA). */
    public Tecnico tecnicoDoClube(String clubeId) {
        if (clubeId == null) return null;
        for (Tecnico t : todosTecnicos()) if (clubeId.equals(t.clubeId)) return t;
        return null;
    }

    /** O técnico humano que comanda o time da Kings (null = IA). */
    public Tecnico tecnicoDoTimeKings(String timeId) {
        if (timeId == null) return null;
        for (Tecnico t : todosTecnicos()) if (timeId.equals(t.kingsTimeId)) return t;
        return null;
    }

    /**
     * Passa a vez para o técnico {@code i}: as listas do técnico da vez vão para o objeto dele e as do
     * técnico {@code i} vêm para o Mundo. No jogo de um técnico só, não faz nada.
     */
    public void ativar(int i) {
        if (!multi() || i < 0 || i >= tecnicos.size()) return;
        Tecnico atual = tecnico;
        Tecnico novo = tecnicos.get(i);
        if (atual == novo) {
            vez = i;
            return;
        }
        if (atual != null) {
            atual.caixa = mensagens;
            atual.convites = propostasTecnico;
            atual.recusadas = vagasRecusadas;
            atual.carreira = historia.carreiraTecnico;
        }
        mensagens = novo.caixa != null ? novo.caixa : new ArrayList<>();
        propostasTecnico = novo.convites != null ? novo.convites : new ArrayList<>();
        vagasRecusadas = novo.recusadas != null ? novo.recusadas : new ArrayList<>();
        historia.carreiraTecnico = novo.carreira != null ? novo.carreira : new ArrayList<>();
        novo.caixa = null;
        novo.convites = null;
        novo.recusadas = null;
        novo.carreira = null;
        tecnico = novo;
        vez = i;
    }

    /** Roda {@code r} com cada técnico humano na vez (e volta ao da vez). Um técnico só: roda uma vez. */
    public void paraCadaTecnico(Runnable r) {
        if (!multi()) {
            r.run();
            return;
        }
        int v = vez;
        try {
            for (int i = 0; i < tecnicos.size(); i++) {
                ativar(i);
                r.run();
            }
        } finally {
            ativar(v);
        }
    }

    /** Roda {@code r} com o técnico {@code t} na vez (e volta ao da vez). */
    public void comTecnico(Tecnico t, Runnable r) {
        if (!multi() || t == null || t == tecnico) {
            r.run();
            return;
        }
        int v = vez;
        try {
            ativar(tecnicos.indexOf(t));
            r.run();
        } finally {
            ativar(v);
        }
    }

    /** Uma mensagem na caixa do técnico {@code t} (null ou o da vez: a caixa do Mundo). */
    public void mensagemPara(Tecnico t, Mensagem msg) {
        if (t == null || t == tecnico || !multi()) {
            mensagens.add(msg);
            return;
        }
        if (t.caixa == null) t.caixa = new ArrayList<>();
        t.caixa.add(msg);
    }

    /** Mensagem para o técnico do clube (um técnico só: a caixa dele, como {@link #mensagem}). */
    public Mensagem mensagemDoClube(String clubeId, String tipo, String titulo, String texto) {
        Mensagem msg = new Mensagem(ano, semana, tipo, titulo, texto);
        mensagemPara(multi() ? tecnicoDoClube(clubeId) : null, msg);
        return msg;
    }

    /** Mensagem para todos os técnicos humanos (avisos do mundo, como leilões). */
    public void mensagemTodos(String tipo, String titulo, String texto) {
        if (!multi()) {
            mensagem(tipo, titulo, texto);
            return;
        }
        for (Tecnico t : tecnicos) mensagemPara(t, new Mensagem(ano, semana, tipo, titulo, texto));
    }

    /** Depois de carregar: o técnico da vez é o mesmo objeto da lista (o JSON grava os dois). */
    public void normalizarTecnicos() {
        if (tecnicos == null) return;
        if (tecnicos.isEmpty()) {
            tecnicos = null;
            return;
        }
        if (vez < 0 || vez >= tecnicos.size()) vez = 0;
        if (tecnico != null) tecnicos.set(vez, tecnico);
        else tecnico = tecnicos.get(vez);
    }

    public void aviso(String texto) {
        if (avisos.size() < 5000 && !avisos.contains(texto)) avisos.add(texto);
    }

    public Jogador novoJogador() {
        ArrayList<Jogador> ix = indice();
        Jogador j = new Jogador();
        j.id = proximoId++;
        jogadores.add(j);
        while (ix.size() <= j.id) ix.add(null);
        ix.set(j.id, j);
        return j;
    }

    /** Nome para mostrar de um participante de competição (clube ou seleção). */
    public String nomeParticipante(String id) {
        Clube c = clube(id);
        if (c != null) return c.nome;
        Selecao s = selecao(id);
        if (s != null) return s.nome;
        if (id != null && kings != null) for (LigaKings l : kings) {
            LigaKings.TimeKings t = l.time(id);
            if (t != null && t.nome != null) return t.nome;
        }
        return id;
    }

    /** Índice auxiliar (não salvo). */
    public Map<String, Torneio> indiceTorneios() {
        if (indiceTorneios == null) indiceTorneios = new HashMap<>();
        return indiceTorneios;
    }
}
