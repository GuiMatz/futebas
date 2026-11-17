package br.futebas.kings;

import br.futebas.competicao.Executor;
import br.futebas.competicao.Partida;
import br.futebas.competicao.RegrasEpoca;
import br.futebas.competicao.Torneio;
import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.dados.Formato;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Evolucao;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;
import br.futebas.mundo.StatusJogador;
import br.futebas.partida.Lance;
import br.futebas.partida.ResultadoPartida;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kings League. As ligas que já existem em 2026 começam com os elencos reais de 2026
 * (kings_elencos); as que nascem depois, na data real de início. Nas temporadas seguintes, um
 * draft escolhe entre jogadores que EXISTEM no mundo simulado e estão em baixa (veteranos,
 * força caindo), aposentados há pouco ou sem clube. Calendário próprio: as rodadas acontecem na mesma semana dos jogos normais, sem
 * bloquear nada. O técnico humano pode comandar um time da Kings e um clube ao mesmo tempo.
 */
public final class Kings implements Executor {
    private final Mundo m;
    private final BancoMundo db;
    private final Rng rng;
    /** Acompanhamento ao vivo do jogo Kings do técnico (cartas e pênalti do presidente). */
    public transient AoVivoKings aoVivo;
    /** Shootouts decididos dentro do jogo, à espera do torneio pedir. */
    private final java.util.Map<Partida, int[]> shootouts = new java.util.IdentityHashMap<>();

    /** Última partida narrada da Kings (time do humano). */
    public ResultadoPartida ultimaNarrada;
    /**
     * Jogos do time da Kings do técnico na semana (e as narrações): o Simulador junta aos jogos do
     * usuário para a tela de fim de jogo ser a mesma do futebol (antes a Kings voltava direto ao
     * início). Limpos no começo de cada semana.
     */
    public final transient List<Partida> jogosDoHumanoNaSemana = new ArrayList<>();
    public final transient List<ResultadoPartida> narradasNaSemana = new ArrayList<>();
    /** Notícias da Kings (resultados, tabela, cartas, draft, wildcards, técnicos, títulos) no noticiário do jogo. */
    final transient br.futebas.historia.NoticiasKings noticias;

    public Kings(Mundo m, BancoMundo db) {
        this.m = m;
        this.db = db;
        this.rng = m.rng;
        this.noticias = new br.futebas.historia.NoticiasKings(m);
        reaplicarPosicoes();
    }

    /**
     * Saves antigos (e jogo carregado): os atletas da Kings sem jogador do mundo ficavam todos MC;
     * recebem as posições oficiais de {@link PosicoesKings} e todo time fica com goleiro.
     */
    void reaplicarPosicoes() {
        if (m.kings == null) return;
        java.util.Map<String, String> mapa = null;
        for (LigaKings liga : m.kings) for (LigaKings.TimeKings tk : liga.times) {
            // só o save com o defeito antigo (todos os atletas da fonte em MC): reaplicar em save bom mudaria
            // posições já equilibradas (e a escalação automática por posição), quebrando o determinismo
            boolean todosMC = true, algum = false;
            for (LigaKings.AtletaKings a : tk.elenco) {
                if (a.jogadorId != null || a.presidente) continue;
                algum = true;
                if (a.posicao != Posicao.MC) todosMC = false;
            }
            if (!algum || !todosMC) continue;
            if (mapa == null) mapa = PosicoesKings.mapa(db);
            PosicoesKings.aplicar(tk, mapa);
        }
    }

    /** Cria as ligas do banco (inativas até a data de início). */
    public static List<LigaKings> criarLigas(BancoMundo db) {
        List<LigaKings> l = new ArrayList<>();
        for (Db.KingsLiga k : db.kingsLigas.values()) {
            LigaKings liga = new LigaKings();
            liga.versaoForca = VERSAO_FORCA;
            liga.id = k.id();
            liga.nome = k.nome();
            liga.pais = k.pais();
            liga.fontes = k.fontes();
            liga.anoInicio = Db.ano(k.inicio(), 2026);
            liga.semanaInicio = semanaDe(k.inicio());
            lerRegras(liga, k.regrasJson());
            lerDraft(liga, k.draftJson());
            if (liga.regras.especiais == null) liga.regras.especiais = new ArrayList<>();
            for (Db.KingsTime t : db.kingsTimes.values()) {
                if (!liga.id.equals(t.ligaId())) continue;
                LigaKings.TimeKings tk = new LigaKings.TimeKings();
                tk.id = t.id();
                tk.nome = t.nome();
                tk.presidente = t.presidente();
                tk.escudoPng = t.escudoPng();
                tk.cor1 = t.cor1();
                tk.cor2 = t.cor2();
                liga.times.add(tk);
            }
            l.add(liga);
        }
        herdarBase(l, db);
        presidentesJogadores(l, db);
        return l;
    }

    /** Ligas do banco + a liga customizada (Kings League IOS) quando {@code custom} não é null. */
    public static List<LigaKings> criarLigas(BancoMundo db, KingsCustom.Definicao custom) {
        List<LigaKings> l = criarLigas(db);
        if (custom != null && !custom.times.isEmpty()) l.add(KingsCustom.criarLiga(custom, br.futebas.Balanceamento.ANO_INICIAL));
        return l;
    }

    // ===================================================================== presidentes-jogadores
    /** Idade mínima para jogar na Kings (o banco não traz regra; o draft só chama quem tem 18+). */
    public static final int IDADE_MINIMA_JOGAR = 16;

    /** Um presidente-jogador conhecido de uma liga real (não está no banco como jogador). */
    public record PresidenteJogador(String timeId, String nome, Posicao posicao, double forca, int anoNasc, String pais) {}

    /**
     * Presidentes que também jogam nas ligas reais (lista explícita): Kelvin Oliveira, ex-ponta de
     * Porto e Palmeiras, presidente e jogador do G3X (o Gaules é presidente, mas não joga).
     */
    public static final List<PresidenteJogador> PRESIDENTES_JOGADORES = List.of(
            new PresidenteJogador("kings-brasil-g3x", "Kelvin Oliveira", Posicao.PD, 66, 1993, "BRA"));

    /**
     * Presidentes que também jogam (regra do jogo, 12/11/2026): nas ligas reais, só o Kelvin
     * Oliveira (G3X); na liga customizada (IOS), todos os presidentes (ver {@link KingsCustom}).
     * Os outros presidentes não são atletas: não jogam, não ocupam vaga e não contam para os 12;
     * só batem o pênalti do presidente. Também limpa saves antigos, em que presidentes que
     * apareciam nas súmulas como jogador (detecção genérica, removida) entravam no elenco.
     */
    public static void presidentesJogadores(List<LigaKings> ligas, BancoMundo db) {
        for (LigaKings liga : ligas) for (LigaKings.TimeKings tk : liga.times) {
            if (tk.presidentesJogadores == null) tk.presidentesJogadores = new ArrayList<>();
            if (!liga.custom) {
                tk.presidentesJogadores.removeIf(a -> !jogaNaLigaReal(tk.id, a.nome));
                tk.elenco.removeIf(a -> a.presidente && !jogaNaLigaReal(tk.id, a.nome));
                for (PresidenteJogador pj : PRESIDENTES_JOGADORES)
                    if (pj.timeId().equals(tk.id)) adicionarModelo(tk, pj.nome(), pj.posicao(), escalaKings(pj.forca()), pj.anoNasc(), pj.pais());
            }
            garantirPresidentes(tk);
        }
    }

    /** O presidente {@code nome} do time {@code timeId} (liga real) joga? Só os da lista explícita. */
    private static boolean jogaNaLigaReal(String timeId, String nome) {
        for (PresidenteJogador pj : PRESIDENTES_JOGADORES) if (pj.timeId().equals(timeId) && pj.nome().equalsIgnoreCase(nome)) return true;
        return false;
    }

    private static void adicionarModelo(LigaKings.TimeKings tk, String nome, Posicao pos, double forca, int anoNasc, String pais) {
        for (LigaKings.AtletaKings a : tk.presidentesJogadores) if (a.nome.equalsIgnoreCase(nome)) return;
        LigaKings.AtletaKings a = new LigaKings.AtletaKings();
        a.nome = nome;
        a.idFixo = "pres-" + tk.id + "-" + nome.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        a.posicao = pos;
        a.forca = forca;
        a.anoNasc = anoNasc;
        a.pais = pais;
        a.presidente = true;
        a.tipo = "presidente";
        a.fonte = "kings_elencos (presidente que joga)";
        tk.presidentesJogadores.add(a);
    }

    /** Recoloca no elenco os presidentes-jogadores que faltarem (eles nunca saem do time). */
    public static void garantirPresidentes(LigaKings.TimeKings tk) {
        if (tk.presidentesJogadores == null) return;
        int i = 0;
        for (LigaKings.AtletaKings modelo : tk.presidentesJogadores) {
            boolean tem = false;
            for (LigaKings.AtletaKings a : tk.elenco) if (a.presidente && modelo.nome.equalsIgnoreCase(a.nome)) tem = true;
            if (!tem) tk.elenco.add(Math.min(i, tk.elenco.size()), modelo.copia());
            i++;
        }
    }

    /** Presidente-jogador com esse nome numa liga real (para as seleções da Kings World Cup Nations). */
    private LigaKings.AtletaKings modeloPresidente(String nome) {
        if (nome == null) return null;
        for (LigaKings l : m.kings) {
            if (l.custom) continue;
            for (LigaKings.TimeKings t : l.times) if (t.presidentesJogadores != null)
                for (LigaKings.AtletaKings a : t.presidentesJogadores) if (a.nome.equalsIgnoreCase(nome)) return a;
        }
        return null;
    }

    /** Presidentes-jogadores do elenco (ficam sempre; ocupam vaga de wildcard). */
    static List<LigaKings.AtletaKings> presidentesNoElenco(LigaKings.TimeKings t) {
        List<LigaKings.AtletaKings> l = new ArrayList<>();
        for (LigaKings.AtletaKings a : t.elenco) if (a.presidente) l.add(a);
        return l;
    }

    /**
     * Lê as regras da liga com tolerância: o JSON da pesquisa mistura números e textos
     * descritivos. Números conhecidos são usados; as regras especiais são reconhecidas pela
     * presença da chave (dado, gol_duplo, carta_secreta/cartas, penalti_presidente...).
     */
    static void lerRegras(LigaKings liga, String json) {
        if (json == null || json.isBlank()) return;
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) return;
            o = e.getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        LigaKings.RegrasKings r = liga.regras;
        r.jogadores_em_campo = inteiro(o, "jogadores_em_campo", r.jogadores_em_campo);
        r.duracao_min = inteiro(o, "duracao_min", r.duracao_min);
        r.pontos_vitoria = inteiro(o, "pontos_vitoria", r.pontos_vitoria);
        r.pontos_vitoria_penaltis = inteiro(o, "pontos_vitoria_penaltis", r.pontos_vitoria_penaltis);
        r.pontos_derrota_penaltis = inteiro(o, "pontos_derrota_penaltis", r.pontos_derrota_penaltis);
        r.classificados_playoff = inteiro(o, "classificados_playoff", r.classificados_playoff);
        r.turnos = inteiro(o, "turnos", r.turnos);
        r.semanas = inteiro(o, "semanas", r.semanas);
        String tudo = o.toString().toLowerCase(java.util.Locale.ROOT);
        if (PONTO_POR_VITORIA.matcher(texto(o, "pontuacao")).find() || PONTO_POR_VITORIA.matcher(tudo).find()) usarPontuacao2026(r);
        int playoff = playoffDoSplit(o);
        if (playoff > 0) r.classificados_playoff = playoff;
        if (o.has("especiais") && o.get("especiais").isJsonArray()) {
            r.especiais = new ArrayList<>();
            for (JsonElement x : o.getAsJsonArray("especiais")) if (x.isJsonPrimitive()) r.especiais.add(x.getAsString());
        } else {
            List<String> esp = new ArrayList<>();
            for (String chave : o.keySet()) {
                String c = chave.toLowerCase();
                if (c.contains("dado")) esp.add("dado");
                else if (c.contains("gol_duplo") || c.contains("gol_dobro")) esp.add("gol_duplo");
                else if (c.contains("carta")) esp.add("carta_secreta");
                else if (c.contains("presidente")) esp.add("penalti_presidente");
                else if (c.contains("desempate") || c.contains("shootout")) esp.add("shootout");
            }
            if (!esp.isEmpty()) r.especiais = esp;
        }
    }

    private static final java.util.regex.Pattern PONTO_POR_VITORIA = java.util.regex.Pattern.compile(
            "vit[oó]ria\\s*=\\s*1 ponto|1 ponto por vit[oó]ria");
    private static final java.util.regex.Pattern PLAYOFFS_COM = java.util.regex.Pattern.compile("playoffs com ([0-9]+)|os ([0-9]+) primeiros");

    /** Kings 2025/26 e 2026/27: vitória = 1 ponto, derrota = 0, também no match ball e nos shootouts. */
    static void usarPontuacao2026(LigaKings.RegrasKings r) {
        r.pontuacao2026 = true;
        r.pontos_vitoria = 1;
        r.pontos_vitoria_penaltis = 1;
        r.pontos_derrota_penaltis = 0;
    }

    /** Texto de uma chave (procura também dentro dos objetos de primeiro nível); "" se faltar. */
    private static String texto(JsonObject o, String chave) {
        if (o.has(chave)) return o.get(chave).isJsonPrimitive() ? o.get(chave).getAsString().toLowerCase(java.util.Locale.ROOT) : o.get(chave).toString().toLowerCase(java.util.Locale.ROOT);
        for (String k : o.keySet()) if (o.get(k).isJsonObject()) {
            String t = texto(o.getAsJsonObject(k), chave);
            if (!t.isEmpty()) return t;
        }
        return "";
    }

    /** Times nos playoffs pelo formato do split 2026 ("playoffs com 7", "os 4 primeiros"; play-in = +1 vaga). */
    static int playoffDoSplit(JsonObject o) {
        String f = texto(o, "formato_split_2026");
        if (f.isEmpty()) f = texto(o, "formato_split_2026_27");
        if (f.isEmpty()) return 0;
        java.util.regex.Matcher mt = PLAYOFFS_COM.matcher(f);
        if (!mt.find()) return 0;
        int n = Integer.parseInt(mt.group(1) != null ? mt.group(1) : mt.group(2));
        return f.contains("play-in") ? n + 1 : n;
    }

    /** Ligas cujas regras dizem "base: regras gerais (kings-espanha)" herdam a pontuação dela. */
    static void herdarBase(List<LigaKings> ligas, BancoMundo db) {
        LigaKings base = null;
        for (LigaKings l : ligas) if ("kings-espanha".equals(l.id)) base = l;
        if (base == null) return;
        for (LigaKings l : ligas) {
            if (l == base) continue;
            Db.KingsLiga k = db.kingsLigas.get(l.id);
            String json = k == null ? null : k.regrasJson();
            if (json == null || !json.contains("\"base\"")) continue;
            if (base.regras.pontuacao2026 && !l.regras.pontuacao2026) usarPontuacao2026(l.regras);
        }
    }

    static void lerDraft(LigaKings liga, String json) {
        if (json == null || json.isBlank()) return;
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) return;
            o = e.getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        LigaKings.DraftKings d = liga.draft;
        d.jogadores_por_time = inteiro(o, "jogadores_por_time", d.jogadores_por_time);
        d.rodadas = inteiro(o, "rodadas", d.rodadas);
        d.mantidos = inteiro(o, "mantidos", d.mantidos);
        d.onze = inteiro(o, "onze", d.onze);
        d.coringas = inteiro(o, "coringas", d.coringas);
        d.idade_minima = inteiro(o, "idade_minima", d.idade_minima);
        d.aposentado_max_anos = inteiro(o, "aposentado_max_anos", d.aposentado_max_anos);
        if (o.has("ordem") && o.get("ordem").isJsonPrimitive()) {
            String ordem = o.get("ordem").getAsString().toLowerCase();
            d.ordem = ordem.contains("serp") || ordem.contains("snake") ? "serpente" : "linear";
        }
    }

    private static int inteiro(JsonObject o, String chave, int padrao) {
        if (!o.has(chave)) return padrao;
        JsonElement e = o.get(chave);
        if (!e.isJsonPrimitive()) return padrao;
        try {
            if (e.getAsJsonPrimitive().isNumber()) return e.getAsInt();
            java.util.regex.Matcher mt = java.util.regex.Pattern.compile("[0-9]+").matcher(e.getAsString());
            return mt.find() ? Integer.parseInt(mt.group()) : padrao;
        } catch (RuntimeException ex) {
            return padrao;
        }
    }

    static int semanaDe(String data) {
        try {
            return Math.min(52, Math.max(1, java.time.LocalDate.parse(data.substring(0, 10)).getDayOfYear() / 7 + 1));
        } catch (RuntimeException e) {
            return 1;
        }
    }

    // ===================================================================== semana
    /** Ligas que são as Copas do Mundo da Kings (contêineres: não jogam temporada própria). */
    public static boolean ehMundial(LigaKings l) {
        return l.id != null && l.id.startsWith("kings-world-cup");
    }

    /** @param sa semana absoluta atual */
    public void semana(int sa) {
        preJogadasMulti.clear(); // multi-treinador: jogos simulados no grupo da semana anterior
        jogaramMulti.clear();
        int semana = m.semana;
        mundiais(sa);
        for (LigaKings liga : m.kings) {
            if (liga.times.size() < 2 || ehMundial(liga)) continue;
            boolean chegouADataInicial = m.ano > liga.anoInicio || (m.ano == liga.anoInicio && semana >= liga.semanaInicio);
            if (!chegouADataInicial) continue;
            if (!liga.ativa) {
                liga.ativa = true;
                m.historia.evento(m.ano, semana, "kings", "Kings League: começa a " + liga.nome + ".");
                novaTemporada(liga, true);
            } else if (liga.anoTemporada < m.ano && semana >= SEMANA_TEMPORADA) {
                List<String> anterior = liga.competicoes.isEmpty() ? null : liga.competicoes.get(0).classificacao;
                novaTemporada(liga, false);
                abrirVagas(liga, anterior);
            }
            if (liga.draftAtual != null) {
                if (liga.draftAtual.semanaCriado == sa) {
                    // espera o técnico escolher (uma semana): os jogos desta semana passam para a próxima,
                    // e o calendário mostra a data nova (antes o próximo jogo anunciado não era jogado)
                    for (Torneio t : liga.competicoes) for (Partida p : t.partidas) if (!p.jogada && p.sa == sa) {
                        p.sa = sa + 1;
                        p.semana = br.futebas.mundo.Calendario.semana(p.sa);
                        p.ano = br.futebas.mundo.Calendario.ano(p.sa);
                        p.dia += 7;
                    }
                    continue;
                }
                avancarDraft(liga, true);
            }
            TreinoKings.semana(m, liga); // treino da semana (o humano escolhe o foco; a IA treina sozinha)
            responderPropostas(liga, sa);
            preencherVagasIA(liga);
            for (Torneio t : liga.competicoes) {
                if (t.encerrado || sa < t.semanaInicio) continue;
                if (liga.torneio != t && (liga.torneio == null || liga.torneio.encerrado)) iniciarCompeticao(liga, t);
                jogarAteSemDobrar(t, sa);
                if (t.encerrado) encerrar(liga, t);
            }
            vagasDeTecnico(liga);
            noticias.semana(liga);
        }
    }

    /**
     * Joga as datas da competição até a semana, MAS um jogo só do time do técnico por chamada (bug do
     * dono, 12/11/2026: "está indo direto para o próximo jogo"): se o time dele já jogou nesta rodada do
     * Fluxo, a data seguinte que o envolve fica adiada (e a competição para ali); ela é jogada sozinha
     * no próximo "Jogar" ({@link #jogarAdiado}), com o próprio pré-jogo, partida e tela de fim.
     */
    void jogarAteSemDobrar(Torneio t, int sa) {
        String meu = m.tecnico == null || m.multi() ? null : m.tecnico.kingsTimeId;
        while (!t.encerrado && t.proximaData < t.totalDatas && t.semanaDaData(t.proximaData) <= sa) {
            if (meu != null && !jogosDoHumanoNaSemana.isEmpty() && dataEnvolve(t, t.proximaData, meu)) return;
            t.jogarProximaData(this);
        }
    }

    private static boolean dataEnvolve(Torneio t, int data, String time) {
        for (Partida p : t.partidas) if (p.data == data && !p.jogada && p.envolve(time)) return true;
        return false;
    }

    /** Há um jogo do time do técnico adiado de uma semana que já passou (rodada dupla)? */
    public Partida jogoAdiado() {
        String meu = m.tecnico == null || m.multi() ? null : m.tecnico.kingsTimeId;
        if (meu == null) return null;
        int agora = m.semanaAbsoluta();
        for (Torneio t : torneiosAtivos())
            if (!t.encerrado && t.proximaData < t.totalDatas && t.semanaDaData(t.proximaData) < agora)
                for (Partida p : t.partidas) if (p.data == t.proximaData && !p.jogada && p.envolve(meu)) return p;
        return null;
    }

    private List<Torneio> torneiosAtivos() {
        List<Torneio> l = new ArrayList<>();
        for (LigaKings liga : m.kings) l.addAll(liga.competicoes);
        l.addAll(m.kingsMundiais);
        return l;
    }

    /**
     * Joga a data adiada do time do técnico (sem avançar a semana): só ela, ao vivo. Os jogos dele
     * ficam em {@link #jogosDoHumanoNaSemana}. Falso se não havia nada adiado.
     */
    public boolean jogarAdiado() {
        Partida p = jogoAdiado();
        if (p == null) return false;
        jogosDoHumanoNaSemana.clear();
        narradasNaSemana.clear();
        for (LigaKings liga : m.kings) for (Torneio t : liga.competicoes) {
            if (!t.partidas.contains(p)) continue;
            if (liga.torneio != t && (liga.torneio == null || liga.torneio.encerrado)) iniciarCompeticao(liga, t);
            t.jogarProximaData(this);
            if (t.encerrado) encerrar(liga, t);
            return true;
        }
        for (Torneio t : m.kingsMundiais) {
            if (!t.partidas.contains(p)) continue;
            t.jogarProximaData(this);
            if (t.encerrado) encerrarMundial(t);
            return true;
        }
        return false;
    }

    /** Sem acompanhamento ao vivo (fim de ano): os jogos que sobraram são jogados direto, sem encadear telas. */
    private boolean semAoVivo;

    /** Fim de ano: competições da Kings em andamento terminam antes do ano virar. */
    public void fimDoAno() {
        semAoVivo = true;
        try {
            fimDoAnoSemTela();
        } finally {
            semAoVivo = false;
        }
    }

    private void fimDoAnoSemTela() {
        for (LigaKings liga : m.kings) {
            for (Torneio t : liga.competicoes) {
                if (t.encerrado) continue;
                if (liga.torneio != t) iniciarCompeticao(liga, t);
                t.concluir(this);
                encerrar(liga, t);
            }
        }
        for (Torneio t : m.kingsMundiais) {
            if (t.encerrado) continue;
            t.concluir(this);
            encerrarMundial(t);
        }
    }

    /** Começo de uma competição da liga: vira a "atual" (telas). */
    private void iniciarCompeticao(LigaKings liga, Torneio t) {
        liga.torneio = t;
    }

    private void encerrar(LigaKings liga, Torneio t) {
        if (t.processado) return;
        t.processado = true;
        if (t.campeao == null) return;
        LigaKings.TimeKings campeao = liga.time(t.campeao);
        if (campeao == null) return;
        liga.campeoes.add(liga.anoTemporada + " " + rotulo(t) + ": " + campeao.nome);
        titulo(t, campeao, t.vice == null ? null : liga.time(t.vice), liga.times.size());
    }

    private static String rotulo(Torneio t) {
        return t.id.endsWith("-split1") ? "Split 1" : t.id.endsWith("-split2") ? "Split 2" : t.id.endsWith("-cup") ? "Kings Cup" : t.nome;
    }

    private void titulo(Torneio t, LigaKings.TimeKings campeao, LigaKings.TimeKings vice, int participantes) {
        var tit = new br.futebas.historia.Historia.Titulo();
        tit.ano = t.ano;
        tit.temporada = t.temporada;
        tit.competicaoId = t.id;
        tit.competicao = t.nome;
        tit.ambito = "kings";
        tit.pais = t.pais;
        tit.campeaoId = campeao.id;
        tit.campeao = campeao.nome;
        if (vice != null) {
            tit.viceId = vice.id;
            tit.vice = vice.nome;
        }
        tit.participantes = participantes;
        m.historia.titulos.add(tit);
        long premio = DinheiroKings.premio(t.id);
        if (premio > 0) {
            String r = rotulo(t);
            DinheiroKings.creditar(m, campeao, "premio", "Prêmio: campeão " + (r.startsWith("Split") ? "do " : "da ") + r
                    + (r.contains(String.valueOf(t.ano)) ? "" : " " + t.ano), premio);
        }
        noticias.titulo(t, campeao, vice);
        if (m.multi() && m.tecnicoDoTimeKings(campeao.id) != null && m.tecnicoDoTimeKings(campeao.id) != m.tecnico) {
            m.comTecnico(m.tecnicoDoTimeKings(campeao.id), () -> {
                m.tecnico.titulos.add(t.ano + " " + t.nome);
                m.tecnico.reputacao = Math.min(100, m.tecnico.reputacao + 1);
                m.tecnico.confiancaKings = Math.min(100, confiancaKings() + 15);
                m.mensagem("kings", "Campeão da Kings League", "O " + campeao.nome + " é campeão: " + t.nome + "!");
            });
        } else if (m.tecnico != null && campeao.id.equals(m.tecnico.kingsTimeId)) {
            m.tecnico.titulos.add(t.ano + " " + t.nome);
            m.tecnico.reputacao = Math.min(100, m.tecnico.reputacao + 1);
            m.tecnico.confiancaKings = Math.min(100, confiancaKings() + 15); // título: o presidente se derrete
            m.mensagem("kings", "Campeão da Kings League", "O " + campeao.nome + " é campeão: " + t.nome + "!");
        }
    }

    // ===================================================================== dinheiro (regra do jogo, 12/11/2026)
    /** Orçamento igual para todo time da Kings, por temporada: R$ 1,5 mi. */
    public static final long ORCAMENTO_TEMPORADA = 1_500_000L;
    /** Prêmios (R$): título de liga (cada Split), Kings Cup e Kings World Cup Clubs. */
    public static final long PREMIO_LIGA = 500_000L, PREMIO_COPA = 300_000L, PREMIO_MUNDIAL = 1_000_000L;
    /** Linhas guardadas no extrato de cada time. */
    public static final int EXTRATO_MAX = 120;

    /** Semana do ano em que a temporada da Kings começa (depois da 1ª), a do Split 2 e a duração da Kings Cup. */
    public static final int SEMANA_TEMPORADA = 2, SEMANA_SPLIT2 = 36, SEMANAS_COPA = 6;

    /**
     * Temporada da liga (um ano): Split 1 (fase de liga + playoffs do formato 2026), Kings Cup
     * (grupos + mata-mata, ou mata-mata) e Split 2. Ids: {@code <liga>-split1}, {@code <liga>-cup},
     * {@code <liga>-split2}. O draft é no começo do ano.
     */
    private void novaTemporada(LigaKings liga, boolean primeira) {
        liga.temporadas++;
        liga.anoTemporada = m.ano;
        for (LigaKings.TimeKings tk : liga.times) DinheiroKings.orcamento(m, tk); // antes do draft: os wildcards custam
        boolean comElencoReal = !liga.custom && primeira && montarElencosReais(liga, true);
        // Kings League IOS (requisito): sem draft do mundo; presidente + atletas genéricos, completados todo ano
        if (liga.custom) completarCustom(liga);
        else if (!comElencoReal) draft(liga);
        int inicio = m.semanaAbsoluta();
        int fimAno = br.futebas.mundo.Calendario.absoluta(m.ano, 52);
        Torneio s1 = split(liga, 1, inicio);
        int copaIni = s1.semanaFim + 2;
        Torneio copa = copa(liga, copaIni, Math.min(fimAno, copaIni + SEMANAS_COPA - 1));
        // o Split 2 começa na semana 36 ou antes, se precisar de mais semanas para ter uma rodada por semana
        int s2datas = datasDoSplit(liga);
        int s2ini = Math.max(Math.min(br.futebas.mundo.Calendario.absoluta(m.ano, SEMANA_SPLIT2), fimAno - Math.max(4, Math.max(liga.regras.semanas, s2datas)) + 1),
                copa.semanaFim + 2);
        Torneio s2 = split(liga, 2, Math.min(s2ini, fimAno - 4));
        liga.competicoes = new ArrayList<>(List.of(s1, copa, s2));
        iniciarCompeticao(liga, s1);
    }

    private Torneio base(LigaKings liga, String sufixo, String nome) {
        Torneio t = new Torneio();
        t.id = liga.id + "-" + sufixo;
        t.nome = liga.nome + " " + nome + " " + m.ano;
        t.ambito = "kings";
        t.pais = liga.pais;
        t.ano = m.ano;
        t.semente = m.semente * 977 + m.ano * 31L + t.id.hashCode();
        t.regras = new RegrasEpoca();
        t.penaltisNoEmpate = true;
        t.pontosVitoriaPenaltis = liga.regras.pontos_vitoria_penaltis;
        t.pontosDerrotaPenaltis = liga.regras.pontos_derrota_penaltis;
        for (LigaKings.TimeKings tk : liga.times) t.participantes.add(tk.id);
        t.calendario = "kings";
        t.temporada = String.valueOf(m.ano);
        return t;
    }

    private Torneio split(LigaKings liga, int n, int inicio) {
        Torneio t = base(liga, "split" + n, "Split " + n);
        Formato f = new Formato();
        Formato.Fase fl = new Formato.Fase("Fase de liga", "pontos_corridos");
        fl.turnos = Math.max(1, liga.regras.turnos);
        // ímpar (ex.: 7): o mata-mata dá folga ao 1º colocado, como no split 2026
        fl.classificam = Math.max(2, Math.min(liga.regras.classificados_playoff, liga.times.size()));
        Formato.Fase po = new Formato.Fase("Playoffs", "mata_mata");
        po.ida_volta = false;
        po.desempate = "penaltis";
        po.chaveamento = "1x8";
        f.fases = new ArrayList<>(List.of(fl, po));
        f.desempates = List.of("pontos", "saldo", "gols_pro", "confronto_direto", "sorteio");
        f.pontos_vitoria = liga.regras.pontos_vitoria;
        t.formato = f;
        int fimAno = br.futebas.mundo.Calendario.absoluta(m.ano, 52);
        t.semanaInicio = inicio;
        // uma rodada por semana (regra do jogo: nunca dois jogos do mesmo time na semana); só aperta
        // (rodada dupla) se o ano acabar antes — exceção: a Kings League IOS, com 20 times
        t.semanaFim = Math.min(fimAno, inicio + Math.max(4, Math.max(liga.regras.semanas, datas(t))) - 1);
        t.planejar();
        return t;
    }

    /**
     * Quantas datas (rodadas) a competição tem: planeja uma cópia descartável com o mesmo formato
     * e participantes. Serve para o calendário dar uma semana para cada rodada.
     */
    static int datas(Torneio modelo) {
        Torneio x = new Torneio();
        x.id = modelo.id + "-contagem";
        x.nome = modelo.nome;
        x.ambito = modelo.ambito;
        x.ano = modelo.ano;
        x.semente = modelo.semente;
        x.regras = modelo.regras;
        x.formato = modelo.formato;
        x.participantes = new ArrayList<>(modelo.participantes);
        x.semanaInicio = 1;
        x.semanaFim = 52;
        x.planejar();
        return x.totalDatas;
    }

    private int datasDoSplit(LigaKings liga) {
        Torneio t = base(liga, "split2", "Split 2");
        Formato f = new Formato();
        Formato.Fase fl = new Formato.Fase("Fase de liga", "pontos_corridos");
        fl.turnos = Math.max(1, liga.regras.turnos);
        fl.classificam = Math.max(2, Math.min(liga.regras.classificados_playoff, liga.times.size()));
        Formato.Fase po = new Formato.Fase("Playoffs", "mata_mata");
        po.ida_volta = false;
        po.desempate = "penaltis";
        po.chaveamento = "1x8";
        f.fases = new ArrayList<>(List.of(fl, po));
        f.pontos_vitoria = liga.regras.pontos_vitoria;
        t.formato = f;
        return datas(t);
    }

    /** Kings Cup: 2 grupos (turno único) + mata-mata com 4 ou 8; liga pequena: só mata-mata. */
    private Torneio copa(LigaKings liga, int de, int ate) {
        Torneio t = base(liga, "cup", "Kings Cup");
        if (liga.nomeCopa != null) t.nome = liga.nomeCopa + " " + m.ano;
        Formato f = new Formato();
        int n = liga.times.size();
        f.fases = new ArrayList<>();
        if (n >= 16) {
            // liga grande (Kings League IOS, 20 times): 4 grupos de 5, os 2 primeiros às quartas
            Formato.Fase g = new Formato.Fase("Fase de grupos", "grupos");
            g.grupos = 4;
            g.turnos = 1;
            g.classificam = 2;
            f.fases.add(g);
        } else if (n >= 8) {
            Formato.Fase g = new Formato.Fase("Fase de grupos", "grupos");
            g.grupos = 2;
            g.turnos = 1;
            g.classificam = n >= 10 ? 4 : 2;
            f.fases.add(g);
        }
        Formato.Fase mm = new Formato.Fase("Mata-mata", "mata_mata");
        mm.ida_volta = false;
        mm.desempate = "penaltis";
        mm.chaveamento = "1x8";
        f.fases.add(mm);
        f.desempates = List.of("pontos", "saldo", "gols_pro", "confronto_direto", "sorteio");
        f.pontos_vitoria = liga.regras.pontos_vitoria;
        t.formato = f;
        t.semanaInicio = de;
        int fimAno = br.futebas.mundo.Calendario.absoluta(m.ano, 52);
        t.semanaFim = Math.min(Math.max(de, fimAno), Math.max(Math.max(de, ate), de + datas(t) - 1)); // uma rodada por semana
        t.planejar();
        return t;
    }

    // ===================================================================== Copas do Mundo da Kings
    /** Semanas das Copas do Mundo: Nations em janeiro (São Paulo, 03–17/01/2026), Clubs no fim de julho (26/07–01/08/2026). */
    public static final int SEMANA_MUNDIAL_SELECOES = 1, SEMANA_MUNDIAL_CLUBES = 30;
    public static final String MUNDIAL_CLUBES = "kings-mundial-clubes", MUNDIAL_SELECOES = "kings-mundial-selecoes";
    /** Semanas de antecedência com que a Kings World Cup Clubs é marcada. */
    static final int ANUNCIO_MUNDIAL = 4;

    private void mundiais(int sa) {
        if (m.semana == SEMANA_MUNDIAL_SELECOES && mundial(MUNDIAL_SELECOES, m.ano) == null) criarMundialSelecoes();
        // a Clubs é marcada (e anunciada no "Próximo jogo") 4 semanas antes de começar: senão o jogo dela
        // aparecia do nada, no lugar do jogo anunciado do Split 2
        if (m.semana >= SEMANA_MUNDIAL_CLUBES - ANUNCIO_MUNDIAL && m.semana <= SEMANA_MUNDIAL_CLUBES && mundial(MUNDIAL_CLUBES, m.ano) == null) criarMundialClubes();
        for (Torneio t : m.kingsMundiais) {
            if (t.encerrado || sa < t.semanaInicio) continue;
            jogarAteSemDobrar(t, sa);
            if (t.encerrado) encerrarMundial(t);
        }
        m.kingsMundiais.removeIf(t -> t.ano < m.ano - 1);
    }

    /** A Copa do Mundo da Kings ({@link #MUNDIAL_CLUBES} ou {@link #MUNDIAL_SELECOES}) do ano; null se não houve. */
    public Torneio mundial(String id, int ano) {
        for (Torneio t : m.kingsMundiais) if (t.id.equals(id) && t.ano == ano) return t;
        return null;
    }

    private LigaKings liga(String id) {
        for (LigaKings l : m.kings) if (l.id.equals(id)) return l;
        return null;
    }

    private Torneio mundialBase(String id, String nome, List<String> part, int grupos) {
        Torneio t = new Torneio();
        t.id = id;
        t.nome = nome + " " + m.ano;
        t.ambito = "kings";
        t.ano = m.ano;
        t.semente = m.semente * 991 + m.ano * 37L + id.hashCode();
        t.regras = new RegrasEpoca();
        t.penaltisNoEmpate = true;
        LigaKings.RegrasKings r = regrasPadrao();
        t.pontosVitoriaPenaltis = r.pontos_vitoria_penaltis;
        t.pontosDerrotaPenaltis = r.pontos_derrota_penaltis;
        t.participantes = new ArrayList<>(part);
        t.calendario = "kings";
        t.temporada = String.valueOf(m.ano);
        Formato f = new Formato();
        Formato.Fase g = new Formato.Fase("Fase de grupos", "grupos");
        g.grupos = grupos;
        g.turnos = 1;
        int porGrupo = part.size() / grupos;
        if (8 % grupos == 0) {
            g.classificam = Math.max(1, Math.min(porGrupo - 1, 8 / grupos));
        } else {
            g.classificam = 1;
            g.melhores_terceiros = 8 - grupos; // 5 grupos: os 5 primeiros + os 3 melhores segundos
        }
        Formato.Fase mm = new Formato.Fase("Mata-mata", "mata_mata");
        mm.ida_volta = false;
        mm.desempate = "penaltis";
        mm.chaveamento = "1x8";
        f.fases = new ArrayList<>(List.of(g, mm));
        f.desempates = List.of("pontos", "saldo", "gols_pro", "confronto_direto", "sorteio");
        f.pontos_vitoria = r.pontos_vitoria;
        t.formato = f;
        t.semanaInicio = MUNDIAL_CLUBES.equals(id) ? br.futebas.mundo.Calendario.absoluta(m.ano, Math.max(m.semana, SEMANA_MUNDIAL_CLUBES)) : m.semanaAbsoluta();
        t.semanaFim = t.semanaInicio + Math.max(2, datas(t) - 1); // uma rodada por semana (antes: tudo em 3 semanas)
        t.planejar();
        return t;
    }

    /** Regras gerais (Kings League Espanha) para as Copas do Mundo. */
    LigaKings.RegrasKings regrasPadrao() {
        LigaKings esp = liga("kings-espanha");
        return esp != null ? esp.regras : new LigaKings.RegrasKings();
    }

    /**
     * Kings World Cup Clubs: um por liga em cada rodada de escolha — campeões das competições já
     * encerradas no ano, depois os vices, depois a classificação do Split 1 — até 16 (múltiplo de 4).
     */
    private void criarMundialClubes() {
        List<String> part = new ArrayList<>();
        List<LigaKings> ligas = new ArrayList<>();
        for (LigaKings l : m.kings) if (l.ativa && !ehMundial(l) && l.anoTemporada == m.ano && l.times.size() >= 2) ligas.add(l);
        if (ligas.isEmpty()) return;
        // liga customizada (Kings League IOS): 1 vaga garantida, a do seu campeão; entra na 1ª volta
        ligas.sort(Comparator.comparingInt((LigaKings l) -> l.custom ? 0 : 1));
        boolean achou = true;
        while (part.size() < 16 && achou) {
            achou = false;
            for (LigaKings l : ligas) {
                if (part.size() >= 16) break;
                if (l.custom && temVaga(l, part)) continue; // liga custom: só 1 vaga
                List<String> ordem = new ArrayList<>();
                for (Torneio t : l.competicoes) if (t.encerrado && t.campeao != null) ordem.add(t.campeao);
                for (Torneio t : l.competicoes) if (t.encerrado && t.vice != null) ordem.add(t.vice);
                if (!l.competicoes.isEmpty()) {
                    Torneio s1 = l.competicoes.get(0);
                    ordem.addAll(s1.encerrado ? s1.classificacao : s1.participantes);
                }
                for (LigaKings.TimeKings tk : l.times) ordem.add(tk.id);
                for (String id : ordem) {
                    if (part.contains(id)) continue;
                    part.add(id);
                    achou = true;
                    break;
                }
            }
        }
        int n = part.size() / 4 * 4;
        if (n < 4) return;
        part = new ArrayList<>(part.subList(0, n));
        Torneio t = mundialBase(MUNDIAL_CLUBES, "Kings World Cup Clubs", part, n / 4);
        m.kingsMundiais.add(t);
        m.historia.evento(m.ano, m.semana, "kings", "Começa a Kings World Cup Clubs com " + n + " times.");
        noticias.mundialComeca(t);
    }

    private static boolean temVaga(LigaKings l, List<String> part) {
        for (LigaKings.TimeKings tk : l.times) if (part.contains(tk.id)) return true;
        return false;
    }

    /**
     * Kings World Cup Nations: em 2026, as seleções reais (times e elencos da liga
     * kings-world-cup-nations); depois (ou sem elencos), seleções montadas com os jogadores da Kings
     * de cada país (12 melhores; países com 7+), até 20 (5 grupos) ou o maior múltiplo de 4.
     */
    private void criarMundialSelecoes() {
        LigaKings nac = liga("kings-world-cup-nations");
        if (nac == null) {
            nac = new LigaKings();
            nac.versaoForca = VERSAO_FORCA;
            nac.id = "kings-world-cup-nations";
            nac.nome = "Kings World Cup Nations";
            m.kings.add(nac);
        }
        boolean reais = m.ano == br.futebas.Balanceamento.ANO_INICIAL && !nac.times.isEmpty() && montarElencosReais(nac, false);
        if (reais) {
            nac.times.removeIf(t -> t.elenco.size() < 5);
        } else {
            nac.times.clear();
            Map<String, List<LigaKings.AtletaKings>> porPais = new LinkedHashMap<>();
            for (LigaKings l : m.kings) {
                if (ehMundial(l)) continue;
                for (LigaKings.TimeKings tk : l.times) for (LigaKings.AtletaKings a : tk.elenco) {
                    if (a.presidente && a.jogadorId == null && a.pais != null && !l.custom) {
                        porPais.computeIfAbsent(a.pais, k -> new ArrayList<>()).add(a);
                        continue;
                    }
                    Jogador j = a.jogadorId == null ? null : m.jogador(a.jogadorId);
                    if (j == null || j.pais == null) continue;
                    porPais.computeIfAbsent(j.pais, k -> new ArrayList<>()).add(a);
                }
            }
            for (Map.Entry<String, List<LigaKings.AtletaKings>> e : porPais.entrySet()) {
                List<LigaKings.AtletaKings> l = e.getValue();
                if (l.size() < 7) continue;
                l.sort(Comparator.comparingDouble((LigaKings.AtletaKings a) -> -a.forca).thenComparing(a -> a.nome));
                LigaKings.TimeKings tk = new LigaKings.TimeKings();
                tk.id = "kwcn-" + e.getKey().toLowerCase(java.util.Locale.ROOT);
                br.futebas.mundo.Selecao s = m.selecao(e.getKey());
                tk.nome = s != null ? s.nome : e.getKey();
                for (int i = 0; i < Math.min(12, l.size()); i++) {
                    LigaKings.AtletaKings src = l.get(i), a = new LigaKings.AtletaKings();
                    a.jogadorId = src.jogadorId;
                    a.nome = src.nome;
                    a.posicao = src.posicao;
                    a.forca = src.forca;
                    a.tipo = "selecao";
                    a.idFixo = src.idFixo;
                    a.foto = src.foto;
                    a.anoNasc = src.anoNasc;
                    a.pais = src.pais;
                    tk.elenco.add(a);
                }
                nac.times.add(tk);
            }
            if (nac.times.size() < SELECOES_KWC) completarSelecoesKings(nac, porPais);
            nac.times.sort(Comparator.comparingDouble((LigaKings.TimeKings tk) -> -MotorKings.forca(tk, 7)).thenComparing(tk -> tk.id));
        }
        int n = nac.times.size() >= 20 ? 20 : nac.times.size() / 4 * 4;
        if (n < 4) return;
        List<String> part = new ArrayList<>();
        for (int i = 0; i < n; i++) part.add(nac.times.get(i).id);
        Torneio t = mundialBase(MUNDIAL_SELECOES, "Kings World Cup Nations", part, n == 20 ? 5 : n / 4);
        m.kingsMundiais.add(t);
    }

    /** Seleções que a Kings World Cup Nations procura ter (2026: 20). */
    public static final int SELECOES_KWC = 20;

    /**
     * Poucos países com 7+ atletas nas ligas Kings (depois de 2026 os elencos vêm do draft de cada
     * país; em 2035 sobravam 4 seleções na Copa): as seleções mais fortes completam o elenco com
     * ex-profissionais do país (aposentados há até 5 anos, livres e veteranos fora da elite), como
     * na Kings World Cup Nations real.
     */
    private void completarSelecoesKings(LigaKings nac, Map<String, List<LigaKings.AtletaKings>> porPais) {
        Set<String> ja = new HashSet<>();
        for (LigaKings.TimeKings tk : nac.times) ja.add(tk.id);
        List<br.futebas.mundo.Selecao> sel = new ArrayList<>(m.selecoes.values());
        sel.sort(Comparator.comparingDouble((br.futebas.mundo.Selecao x) -> -x.forca).thenComparing(x -> x.pais));
        Set<String> paises = new LinkedHashSet<>();
        for (br.futebas.mundo.Selecao x : sel) {
            if (paises.size() >= 2 * SELECOES_KWC) break;
            if (x.pais != null && !ja.contains("kwcn-" + x.pais.toLowerCase(java.util.Locale.ROOT))) paises.add(x.pais);
        }
        Set<Integer> ocupados = new HashSet<>();
        for (List<LigaKings.AtletaKings> l : porPais.values()) for (LigaKings.AtletaKings a : l) if (a.jogadorId != null) ocupados.add(a.jogadorId);
        Map<String, List<Jogador>> extras = new HashMap<>();
        for (Jogador j : m.jogadores) {
            if (j.pais == null || !paises.contains(j.pais) || j.amador() || j.reforco || ocupados.contains(j.id)) continue;
            int idade = j.idade(m.ano);
            if (idade < 18 || idade > 45) continue;
            boolean ok = switch (j.status) {
                case APOSENTADO -> !j.virouTecnico && m.ano - j.anoAposentadoria <= 5;
                case LIVRE -> idade >= 28;
                case PROFISSIONAL -> {
                    Clube c = m.clube(j.clubeId);
                    yield idade >= 30 && (c == null || c.divisao != 1); // nunca da 1ª divisão
                }
                default -> false;
            };
            if (ok) extras.computeIfAbsent(j.pais, k -> new ArrayList<>()).add(j);
        }
        for (String pais : paises) {
            if (nac.times.size() >= SELECOES_KWC) break;
            List<LigaKings.AtletaKings> base = new ArrayList<>(porPais.getOrDefault(pais, List.of()));
            List<Jogador> ex = extras.getOrDefault(pais, new ArrayList<>());
            ex.sort(Comparator.comparingDouble((Jogador j) -> -valorDraft(j)).thenComparingInt(j -> j.id));
            if (base.size() + ex.size() < 7) continue;
            LigaKings.TimeKings tk = new LigaKings.TimeKings();
            tk.id = "kwcn-" + pais.toLowerCase(java.util.Locale.ROOT);
            br.futebas.mundo.Selecao s = m.selecao(pais);
            tk.nome = s != null ? s.nome : pais;
            base.sort(Comparator.comparingDouble((LigaKings.AtletaKings a) -> -a.forca).thenComparing(a -> a.nome));
            for (LigaKings.AtletaKings src : base) {
                if (tk.elenco.size() >= 12) break;
                LigaKings.AtletaKings a = src.copia();
                a.tipo = "selecao";
                a.presidente = false;
                tk.elenco.add(a);
            }
            for (Jogador j : ex) {
                if (tk.elenco.size() >= 12) break;
                LigaKings.AtletaKings a = new LigaKings.AtletaKings();
                a.jogadorId = j.id;
                a.nome = j.nomeExibicao();
                a.posicao = j.posicao;
                a.forca = forcaAtleta(j);
                a.tipo = "selecao";
                tk.elenco.add(a);
            }
            nac.times.add(tk);
        }
    }

    private void encerrarMundial(Torneio t) {
        if (t.processado) return;
        t.processado = true;
        if (t.campeao == null) return;
        LigaKings.TimeKings c = timeKings(t.campeao), v = t.vice == null ? null : timeKings(t.vice);
        if (c == null) return;
        titulo(t, c, v, t.participantes.size());
        m.historia.evento(m.ano, m.semana, "kings", c.nome + " vence a " + t.nome + ".");
    }

    /** Time da Kings pelo id, em qualquer liga (inclui as seleções da Copa do Mundo). */
    public LigaKings.TimeKings timeKings(String id) {
        if (id == null) return null;
        for (LigaKings l : m.kings) {
            LigaKings.TimeKings t = l.time(id);
            if (t != null) return t;
        }
        return null;
    }

    // ===================================================================== técnico humano: vagas e convites
    /** Vagas de técnico abertas há mais de 6 semanas: a IA preencheu. */
    private void vagasDeTecnico(LigaKings liga) {
        int agora = m.semanaAbsoluta();
        for (LigaKings.TimeKings tk : liga.times) if (tk.vagaAte > 0 && agora > tk.vagaAte) tk.vagaAte = 0;
    }

    /**
     * Virada da temporada da liga: o presidente decide. Os 2 piores do Split 1 anterior podem
     * trocar de técnico (vaga aberta por 6 semanas; convite ao técnico humano pela reputação dele);
     * o humano no último lugar pode ser demitido.
     */
    private void abrirVagas(LigaKings liga, List<String> classificacaoAnterior) {
        if (classificacaoAnterior == null || classificacaoAnterior.size() < 4) return;
        int agora = m.semanaAbsoluta();
        int n = classificacaoAnterior.size();
        for (int i = n - 2; i < n; i++) {
            LigaKings.TimeKings tk = liga.time(classificacaoAnterior.get(i));
            if (tk == null) continue;
            if (tk.humano) {
                // último na virada do ano: a confiança cai 20; abaixo do limite, o presidente demite (rodada 2: era 35% de chance)
                br.futebas.mundo.Tecnico dono = m.multi() ? m.tecnicoDoTimeKings(tk.id) : m.tecnico;
                if (i == n - 1 && dono != null && tk.id.equals(dono.kingsTimeId)) {
                    final LigaKings.TimeKings fim = tk;
                    m.comTecnico(dono, () -> {
                        m.tecnico.confiancaKings = Math.max(0, confiancaKings() - 20);
                        if (m.tecnico.confiancaKings < CONFIANCA_DEMISSAO_KINGS) demitirHumanoKings(fim, "terminou o Split em último");
                    });
                }
                continue;
            }
            if (!rng.chance(0.5)) continue;
            tk.vagaAte = agora + 6;
            noticias.tecnicoDemitido(liga, tk, i == n - 1);
            if (m.tecnico != null && m.tecnico.kingsTimeId == null && rng.chance(Math.min(0.6, 0.1 + m.tecnico.reputacao / 150.0)))
                proporKings(liga, tk);
        }
    }


    // ===================================================================== técnico humano: carreira e presidente
    /**
     * Confiança do presidente no técnico humano da Kings (Balanço 2026-11, rodada 2): guardada no save
     * ({@code Tecnico.confiancaKings}), começa em 60 ao assumir, sobe com vitórias e títulos e cai com
     * derrotas, pesando a força do adversário (perder para quem é mais forte custa metade). Presidente
     * impaciente mas justo: abaixo de 20, depois dos primeiros 6 jogos no time, demite.
     */
    public static final double CONFIANCA_INICIAL_KINGS = 60, CONFIANCA_DEMISSAO_KINGS = 20;
    public static final int KINGS_JOGOS_PROTEGIDOS = 6;

    public double confiancaKings() {
        return m.tecnico == null || m.tecnico.confiancaKings == null ? CONFIANCA_INICIAL_KINGS : m.tecnico.confiancaKings;
    }

    /** Depois de um jogo do time do técnico humano: carreira da Kings (V/D, shootouts) e confiança do presidente. */
    void resultadoDoHumano(LigaKings.TimeKings meu, LigaKings.TimeKings adv, ResultadoPartida r, int lado, LigaKings.RegrasKings regras) {
        br.futebas.mundo.Tecnico tec = m.tecnico;
        int gp = lado == 0 ? r.golsMandante : r.golsVisitante, gc = lado == 0 ? r.golsVisitante : r.golsMandante;
        boolean shootout = gp == gc;
        boolean venceu = shootout ? r.penaltisMandante != null && (lado == 0 ? r.penaltisMandante > r.penaltisVisitante : r.penaltisVisitante > r.penaltisMandante) : gp > gc;
        tec.kingsJogos++;
        tec.kingsJogosNoTime++;
        if (venceu) tec.kingsVitorias++;
        else tec.kingsDerrotas++;
        if (shootout && venceu) tec.kingsVitoriasShootout++;
        if (shootout && !venceu) tec.kingsDerrotasShootout++;
        // ranking de técnicos (como no futebol): vitória 3 (+1 fora de casa), derrota nos shootouts 1
        if (venceu) tec.pontosRanking += 3 + (lado == 1 ? 1 : 0);
        else if (shootout) tec.pontosRanking += 1;
        int n = regras == null ? 7 : regras.jogadores_em_campo;
        double dif = MotorKings.forca(meu, n) - MotorKings.forca(adv, n);
        double delta = venceu ? (shootout ? 2 : 4) + (dif < -3 ? 2 : 0) : -(shootout ? 3 : 5) * (dif < -3 ? 0.5 : dif > 3 ? 1.3 : 1.0);
        double c = confiancaKings() + delta;
        c += 0.03 * (55 - c); // memória curta: volta devagar para o neutro
        tec.confiancaKings = Math.max(0, Math.min(100, c));
        if (tec.confiancaKings < CONFIANCA_DEMISSAO_KINGS && tec.kingsJogosNoTime >= KINGS_JOGOS_PROTEGIDOS)
            demitirHumanoKings(meu, "sequência de maus resultados");
    }

    /** O presidente demite o técnico humano: linha da carreira, vaga aberta por 6 semanas, mensagem. */
    void demitirHumanoKings(LigaKings.TimeKings tk, String motivo) {
        if (m.tecnico == null || !tk.id.equals(m.tecnico.kingsTimeId)) return;
        m.registrarTemporadaTecnico("Demitido");
        tk.humano = false;
        tk.vagaAte = m.semanaAbsoluta() + 6;
        m.tecnico.kingsTimeId = null;
        m.tecnico.confiancaKings = null;
        m.tecnico.demissoes++;
        m.mensagem("kings", "Demitido da Kings", "O presidente " + (tk.presidente == null ? "" : tk.presidente + " ") + "decidiu trocar o técnico do "
                + tk.nome + " (" + motivo + ").");
        m.historia.evento(m.ano, m.semana, "tecnico", m.tecnico.nome + " é demitido do " + tk.nome + " (Kings League).");
        noticias.humanoDemitido(tk, motivo);
    }
    /** Convite de um time da Kings ao técnico humano (mesmo fluxo dos clubes: aceitar ou recusar). */
    public br.futebas.ia.PropostaTecnico proporKings(LigaKings liga, LigaKings.TimeKings tk) {
        for (br.futebas.ia.PropostaTecnico p : m.propostasTecnico) if (tk.id.equals(p.kingsTimeId) && "pendente".equals(p.estado)) return p;
        br.futebas.ia.PropostaTecnico p = new br.futebas.ia.PropostaTecnico();
        p.id = m.proximaProposta++;
        p.kingsTimeId = tk.id;
        p.kingsLigaId = liga.id;
        p.clube = tk.nome;
        p.ano = m.ano;
        p.semana = m.semana;
        p.semanaLimite = m.semanaAbsoluta() + 3;
        m.propostasTecnico.add(p);
        // requisito: sem mensagem de proposta de emprego; o convite aparece na aba Vagas
        return p;
    }

    /** Jogo novo (nenhuma semana jogada): qualquer time da Kings aceita candidatura. */
    private boolean jogoNovo() {
        return m.ano == br.futebas.Balanceamento.ANO_INICIAL && m.semana == 1;
    }

    /**
     * Times da Kings a que o humano pode se candidatar (regra do jogo): no jogo novo, todos os
     * times das ligas ativas; na carreira, só os que estão SEM técnico (vaga aberta). Nunca o
     * time que ele já treina.
     */
    public List<LigaKings.TimeKings> vagas() {
        List<LigaKings.TimeKings> l = new ArrayList<>();
        int agora = m.semanaAbsoluta();
        boolean novo = jogoNovo();
        for (LigaKings liga : m.kings) {
            if (ehMundial(liga) || !(liga.ativa || (novo && liga.anoInicio <= m.ano))) continue;
            for (LigaKings.TimeKings tk : liga.times) if (!tk.humano && (novo || tk.vagaAte >= agora)) l.add(tk);
        }
        return l;
    }

    /**
     * Candidatura: só a um time de {@link #vagas()} (fora disso, só convites recebidos). No jogo
     * novo o presidente sempre aceita; na carreira decide pela reputação (recusou = vaga fecha).
     * Aceitando, chega um convite (o mesmo de {@link #proporKings}); null = recusado ou proibido.
     * Quem já treina um time da Kings pode se candidatar a uma vaga aberta para mudar.
     */
    public br.futebas.ia.PropostaTecnico candidatar(String timeId) {
        if (m.tecnico == null || timeId == null || timeId.equals(m.tecnico.kingsTimeId)) return null;
        boolean novo = jogoNovo();
        for (LigaKings liga : m.kings) {
            LigaKings.TimeKings tk = liga.time(timeId);
            if (tk == null || ehMundial(liga) || !vagas().contains(tk)) continue;
            double chance = Math.min(0.9, 0.25 + m.tecnico.reputacao / 100.0);
            if (!novo && !rng.chance(chance)) {
                m.mensagem("kings", "Candidatura recusada", "O presidente do " + tk.nome + " preferiu outro técnico.");
                tk.vagaAte = 0;
                return null;
            }
            return proporKings(liga, tk);
        }
        return null;
    }

    /** O técnico passa a comandar o time da Kings (convite aceito, ou escolha no jogo novo). */
    public boolean assumir(String timeId) {
        LigaKings.TimeKings tk = null;
        for (LigaKings l : m.kings) if (!ehMundial(l) && l.time(timeId) != null) tk = l.time(timeId);
        if (tk == null || m.tecnico == null) return false;
        if (m.multi()) {
            // multi-treinador: o time de outro técnico humano não está disponível; só o time antigo dele volta à IA
            br.futebas.mundo.Tecnico dono = m.tecnicoDoTimeKings(tk.id);
            if (dono != null && dono != m.tecnico) return false;
            for (LigaKings ll : m.kings) for (LigaKings.TimeKings tt : ll.times) if (tt.id.equals(m.tecnico.kingsTimeId)) tt.humano = false;
        } else
            for (LigaKings ll : m.kings) for (LigaKings.TimeKings tt : ll.times) tt.humano = false;
        tk.humano = true;
        tk.vagaAte = 0;
        m.tecnico.kingsTimeId = tk.id;
        m.tecnico.confiancaKings = CONFIANCA_INICIAL_KINGS; // presidente novo, confiança nova
        m.tecnico.kingsJogosNoTime = 0;
        return true;
    }

    // ===================================================================== elencos reais
    /**
     * Primeira temporada: elencos reais (kings_elencos), de preferência os de 2026 (a mais recente
     * até 2026; para ligas futuras, a primeira registrada). Retorna falso se não houver dados.
     */
    private boolean montarElencosReais(LigaKings liga, boolean vincular) {
        boolean algum = false;
        for (LigaKings.TimeKings tk : liga.times) {
            tk.elenco.clear();
            String temporada = null, primeira = null;
            for (Db.KingsElenco e : db.kingsElencos) {
                if (!tk.id.equals(e.timeId()) || e.temporada() == null) continue;
                if (primeira == null || e.temporada().compareTo(primeira) < 0) primeira = e.temporada();
                if (Db.ano(e.temporada(), 9999) <= m.ano && (temporada == null || e.temporada().compareTo(temporada) > 0)) temporada = e.temporada();
            }
            if (temporada == null) temporada = primeira;
            if (temporada == null) continue;
            for (Db.KingsElenco e : db.kingsElencos) {
                if (!tk.id.equals(e.timeId()) || !temporada.equals(e.temporada())) continue;
                if ("presidente".equals(e.tipo())) {
                    // presidente que joga: no time dele entra pelo modelo; na seleção (Nations), como convocado
                    LigaKings.AtletaKings pj = vincular ? null : modeloPresidente(e.nomeFonte());
                    if (pj != null && tk.elenco.stream().noneMatch(x -> pj.nome.equalsIgnoreCase(x.nome))) {
                        LigaKings.AtletaKings c = pj.copia();
                        c.presidente = false;
                        c.tipo = "selecao";
                        tk.elenco.add(c);
                        algum = true;
                    }
                    continue;
                }
                if (e.tipo() != null && (e.tipo().equals("tecnico") || e.tipo().equals("staff"))) continue;
                LigaKings.AtletaKings a = new LigaKings.AtletaKings();
                a.tipo = e.tipo() == null ? "real" : e.tipo();
                Integer wid = e.jogadorId() == null ? null : m.porDbId.get(e.jogadorId());
                if (wid != null && vincular && m.jogador(wid) != null && m.clube(m.jogador(wid).clubeId) != null
                        && m.clube(m.jogador(wid).clubeId).divisao == 1) continue; // regra do jogo: ninguém da 1ª divisão na Kings
                if (wid != null) {
                    Jogador j = m.jogador(wid);
                    a.jogadorId = j.id;
                    a.nome = j.nomeExibicao();
                    a.posicao = j.posicao;
                    a.forca = forcaKings(j);
                    if (vincular) j.kingsTimeId = tk.id;
                } else if (e.jogadorId() != null && db.jogadores.containsKey(e.jogadorId())) {
                    Db.Jogador dj = db.jogadores.get(e.jogadorId());
                    a.nome = dj.nomeExibicao();
                    a.fonte = e.fonte();
                    a.posicao = Posicao.de(dj.posicao());
                    a.forca = escalaKings(dj.forca());
                } else if (e.nomeFonte() != null && e.fonte() != null && !e.fonte().isBlank()) {
                    a.nome = e.nomeFonte();
                    a.fonte = e.fonte();
                    a.forca = escalaKings(50 + rng.gauss(0, 5));
                } else {
                    continue; // sem fonte não entra (regra 1)
                }
                tk.elenco.add(a);
                algum = true;
            }
            garantirPresidentes(tk);
            PosicoesKings.aplicar(tk, PosicoesKings.mapa(db)); // posições oficiais (antes todos ficavam MC, sem goleiro)
            limitarElenco(m, tk); // elencos reais de 2026 têm 15–22 nomes: ficam os 12 melhores (regra do jogo)
            PosicoesKings.aplicar(tk, PosicoesKings.mapa(db)); // de novo, com o elenco cortado (equilíbrio dos sem posição)
        }
        return algum;
    }

    // ===================================================================== força e tamanho do elenco
    /**
     * Escala de força da Kings (regra do jogo, 12/11/2026: "arrume overall dos jogadores da kings").
     * Os atletas reais da Kings vinham do banco com ~50 (mínimo ~36) enquanto os profissionais do
     * draft mantinham a força do clube (até 80) e os presidentes da IOS têm 75: escalas diferentes.
     * Uma escala só: até 52 ganha +12; o bônus some em linha reta até 72; de 72 para cima, nada
     * (teto 80). 50 → 62, 60 → 67,2, 66 → 69,6, 70 → 71,2, 74 → 74, 80 → 80. Monótona (nunca
     * inverte a ordem) e idempotente a partir de 72. Os presidentes da IOS (75) ficam como estão.
     */
    public static double escalaKings(double f) {
        return Math.min(FORCA_MAX, f + ESCALA_BONUS * Math.max(0, Math.min(1, (ESCALA_PIVO - f) / ESCALA_FAIXA)));
    }

    static final double ESCALA_BONUS = 12, ESCALA_PIVO = 72, ESCALA_FAIXA = 20;
    /** Versão da escala de força gravada na liga ({@link LigaKings#versaoForca}); saves com 0 são reescalados ao carregar. */
    public static final int VERSAO_FORCA = 1;

    /** Força de um jogador do mundo na Kings (elenco real). */
    static double forcaKings(Jogador j) {
        return escalaKings(j.forca);
    }

    /** Força de quem entra pelo draft, wildcard, mantido, contratação ou seleção (aposentado perde 2,5 por ano parado). */
    double forcaAtleta(Jogador j) {
        return escalaKings(Math.max(20, valorDraft(j) - j.fama * 0.15 - (j.estrela ? 4 : 0)));
    }

    /** Teto do elenco da Kings (regra do jogo): 6 wildcards + 6 draftados = 12, contando os presidentes que jogam. */
    public static final int MAX_ELENCO = 12;

    /**
     * Corta o elenco em {@link #MAX_ELENCO}: ficam os presidentes que jogam, o melhor goleiro (se
     * houver) e os mais fortes (no máximo 2 goleiros, salvo se faltar gente). Devolve os que saíram.
     */
    static List<LigaKings.AtletaKings> cortarElenco(LigaKings.TimeKings tk) {
        if (tk.elenco.size() <= MAX_ELENCO) return List.of();
        List<LigaKings.AtletaKings> fica = new ArrayList<>();
        for (LigaKings.AtletaKings a : tk.elenco) if (a.presidente && fica.size() < MAX_ELENCO) fica.add(a);
        List<LigaKings.AtletaKings> resto = new ArrayList<>(tk.elenco);
        resto.removeAll(fica);
        resto.sort(Comparator.comparingDouble((LigaKings.AtletaKings a) -> -a.forca).thenComparing(a -> a.nome == null ? "" : a.nome));
        if (fica.stream().noneMatch(a -> a.posicao == Posicao.GOL))
            for (LigaKings.AtletaKings a : resto) if (a.posicao == Posicao.GOL && fica.size() < MAX_ELENCO) { fica.add(a); break; }
        resto.removeAll(fica);
        List<LigaKings.AtletaKings> sobra = new ArrayList<>();
        for (LigaKings.AtletaKings a : resto) {
            if (fica.size() >= MAX_ELENCO) break;
            if (a.posicao == Posicao.GOL && fica.stream().filter(x -> x.posicao == Posicao.GOL).count() >= 2) { sobra.add(a); continue; }
            fica.add(a);
        }
        for (LigaKings.AtletaKings a : sobra) if (fica.size() < MAX_ELENCO) fica.add(a);
        List<LigaKings.AtletaKings> saem = new ArrayList<>(tk.elenco);
        saem.removeAll(fica);
        tk.elenco.retainAll(fica);
        return saem;
    }

    /** {@link #cortarElenco} e quem sai fica sem time na Kings (volta ao pool do draft). */
    static void limitarElenco(Mundo m, LigaKings.TimeKings tk) {
        for (LigaKings.AtletaKings a : cortarElenco(tk)) {
            Jogador j = a.jogadorId == null ? null : m.jogador(a.jogadorId);
            if (j != null && tk.id.equals(j.kingsTimeId)) j.kingsTimeId = null;
        }
        if (tk.titulares != null) tk.titulares.removeIf(k -> tk.elenco.stream().noneMatch(a -> k.equals(a.chave())));
        if (tk.cobradores != null) tk.cobradores.removeIf(k -> tk.elenco.stream().noneMatch(a -> k.equals(a.chave())));
    }

    /**
     * Saves antigos (ao carregar): tira convidados de jogador 13 que tenham ficado no elenco (a
     * regra acabou), reescala a força para {@link #escalaKings} (uma vez por liga, pela
     * {@link LigaKings#versaoForca}; os presidentes da IOS ficam em 75) e corta todo elenco em 12.
     */
    public static void ajustarSaveAntigo(Mundo m) {
        if (m.kings == null) return;
        for (LigaKings l : m.kings) {
            for (LigaKings.TimeKings tk : l.times) tk.elenco.removeIf(a -> "13".equals(a.tipo));
            if (l.versaoForca < VERSAO_FORCA) {
                for (LigaKings.TimeKings tk : l.times) {
                    for (LigaKings.AtletaKings a : tk.elenco) if (!(l.custom && a.presidente)) a.forca = escalaKings(a.forca);
                    if (!l.custom && tk.presidentesJogadores != null)
                        for (LigaKings.AtletaKings a : tk.presidentesJogadores) a.forca = escalaKings(a.forca);
                }
                l.versaoForca = VERSAO_FORCA;
            }
            for (LigaKings.TimeKings tk : l.times) limitarElenco(m, tk);
            // dinheiro da Kings (12/11/2026): saves de antes recebem o orçamento da temporada em andamento
            if (l.ativa && !ehMundial(l)) for (LigaKings.TimeKings tk : l.times) if (tk.anoOrcamento == 0) DinheiroKings.orcamento(m, tk);
        }
    }

    // ===================================================================== draft
    /**
     * Ao se aposentar, o jogador pode escolher a Kings League: mais chance com fama e até ~38
     * anos, e muito mais se o país dele tem uma liga Kings. Ele entra no draft da liga do país
     * escolhido ({@code kingsPais}). Quem virou técnico não vai.
     */
    public void talvezIrParaKings(Jogador j) {
        if (j.virouTecnico || j.amador() || j.reforco || m.kings.isEmpty()) return;
        int idade = j.idade(m.ano);
        if (idade > 40) return;
        String mesmoPais = null;
        List<String> paises = new ArrayList<>();
        for (LigaKings l : m.kings) {
            if (l.pais == null) continue;
            if (!paises.contains(l.pais)) paises.add(l.pais);
            if (l.pais.equalsIgnoreCase(j.pais)) mesmoPais = l.pais;
        }
        if (paises.isEmpty()) return;
        double p = (mesmoPais != null ? 0.25 : 0.03) + 0.004 * j.fama + (idade <= 36 ? 0.05 : 0);
        if (!m.rng.chance(Math.min(0.7, p))) return;
        j.kingsPais = mesmoPais != null ? mesmoPais : paises.get(m.rng.inteiro(paises.size()));
    }

    /** Quem pode ser escolhido no draft: veteranos, em baixa, sem clube ou aposentados há pouco. */
    public List<Jogador> elegiveis(LigaKings liga, boolean qualquerPais) {
        Set<Integer> ocupados = new HashSet<>();
        for (LigaKings l : m.kings) for (LigaKings.TimeKings t : l.times) for (LigaKings.AtletaKings a : t.elenco) if (a.jogadorId != null) ocupados.add(a.jogadorId);
        List<Jogador> l = new ArrayList<>();
        String pais = liga.pais != null ? liga.pais : liga.paisDraft;
        for (Jogador j : m.jogadores) {
            if (j.amador() || j.reforco || ocupados.contains(j.id)) continue;
            if (!qualquerPais && pais != null && !pais.equalsIgnoreCase(j.pais)
                    && !(j.status == StatusJogador.APOSENTADO && pais.equalsIgnoreCase(j.kingsPais))) continue;
            int idade = j.idade(m.ano);
            if (idade < 18 || idade > 48) continue;
            if (j.forca > FORCA_MAX) continue; // regra do jogo: ninguém acima de 80 no draft
            boolean ok = switch (j.status) {
                case APOSENTADO -> !j.virouTecnico && j.kingsPais != null && m.ano - j.anoAposentadoria <= liga.draft.aposentado_max_anos
                        && (qualquerPais || pais == null || pais.equalsIgnoreCase(j.kingsPais));
                case LIVRE -> true;
                case PROFISSIONAL -> {
                    Clube c = m.clube(j.clubeId);
                    // regra do jogo (12/11/2026): nunca jogador de clube da 1ª divisão. Clube sem divisão
                    // nacional (regional/amador): todos; 2ª divisão para baixo: veteranos, quem não joga
                    // no clube e nomes conhecidos (antes da regra, até clube fraco da 1ª entrava).
                    // Draftado ou contratado, ele deixa o clube (ver deixarClubeAoEntrarNaKings).
                    if (c == null) yield true;
                    if (c.divisao == 1) yield false;
                    if (c.divisao == 0) yield true;
                    yield idade >= liga.draft.idade_minima || j.forca < c.nivel() - 8 || (c.divisao >= 3 || c.nivel() < 55) && j.fama >= 25;
                }
                default -> false;
            };
            if (ok) l.add(j);
        }
        l.sort(Comparator.comparingDouble((Jogador j) -> -preferenciaDraft(j)).thenComparingInt(j -> j.id));
        return l;
    }

    /** Teto de força na Kings (regra do jogo: ninguém acima de 80). */
    public static final double FORCA_MAX = 80;

    /**
     * Ordem de preferência no draft: a força/fama de sempre, com bônus para quem está sem clube
     * (regra do jogo: o draft usa mais os jogadores sem clube) e um menor para aposentados.
     */
    double preferenciaDraft(Jogador j) {
        // regra do jogo: sem contrato primeiro, depois clube sem divisão nacional e aposentados; divisões de baixo por último
        double bonus = switch (j.status) {
            case LIVRE -> 12;
            case APOSENTADO -> 5;
            case PROFISSIONAL -> {
                Clube c = m.clube(j.clubeId);
                yield c == null ? 12 : c.divisao == 0 ? 8 : 0;
            }
            default -> 0;
        };
        return valorDraft(j) + bonus;
    }

    /** Clube de 1ª divisão com nível (16 melhores) a partir disto é "elite": seus jogadores não vão ao draft. */
    public static final double KINGS_NIVEL_ELITE = 60;

    double valorDraft(Jogador j) {
        double f = j.forca;
        if (j.status == StatusJogador.APOSENTADO) f -= 2.5 * Math.max(0, m.ano - j.anoAposentadoria);
        return f + j.fama * 0.15 + (j.estrela ? 4 : 0);
    }

    /** Kings 2026: elenco = 6 wildcards (os melhores nomes disponíveis) + 6 draftados. */
    public static final int WILDCARDS = 6, DRAFTADOS = 6;

    /**
     * Draft da temporada: os wildcards (6 por time, em ordem, os melhores nomes) e depois 6 rodadas
     * (serpente ou linear, pela regra da liga). Se o técnico humano comanda um time da liga, o draft
     * para na vez dele ({@link LigaKings#draftAtual}) e continua com {@link #escolherNoDraft}.
     */
    void draft(LigaKings liga) {
        LigaKings.DraftKings d = liga.draft;
        // ordem: pior campanha anterior escolhe primeiro
        List<LigaKings.TimeKings> ordem = new ArrayList<>(liga.times);
        if (liga.torneio != null && !liga.torneio.classificacao.isEmpty()) {
            List<String> cl = liga.torneio.classificacao;
            ordem.sort(Comparator.comparingInt((LigaKings.TimeKings t) -> -cl.indexOf(t.id)).thenComparing(t -> t.id));
        } else {
            rng.embaralhar(ordem);
        }
        int necessarios = liga.times.size() * (WILDCARDS + DRAFTADOS);
        List<Jogador> pool = elegiveis(liga, false);
        if (pool.size() < necessarios * 1.2) {
            Set<Integer> ja = new HashSet<>();
            for (Jogador j : pool) ja.add(j.id);
            for (Jogador j : elegiveis(liga, true)) if (ja.add(j.id)) pool.add(j);
            pool.sort(Comparator.comparingDouble((Jogador j) -> -preferenciaDraft(j)).thenComparingInt(j -> j.id));
        }
        if (pool.size() > necessarios * 4 + 60) {
            List<Jogador> famosos = new ArrayList<>(pool);
            famosos.sort(Comparator.comparingDouble((Jogador j) -> -j.fama).thenComparingInt(j -> j.id));
            List<Jogador> corte = new ArrayList<>(pool.subList(0, necessarios * 4));
            Set<Integer> ja = new HashSet<>();
            for (Jogador j : corte) ja.add(j.id);
            for (int i = 0; i < Math.min(60, famosos.size()); i++) if (ja.add(famosos.get(i).id)) corte.add(famosos.get(i));
            pool = corte;
        }
        // mantidos
        for (LigaKings.TimeKings t : liga.times) {
            List<LigaKings.AtletaKings> presidentes = presidentesNoElenco(t);
            List<LigaKings.AtletaKings> velhos = new ArrayList<>(t.elenco);
            velhos.removeAll(presidentes);
            for (LigaKings.AtletaKings a : velhos) if (a.jogadorId != null && m.ativo(a.jogadorId) != null) m.ativo(a.jogadorId).kingsTimeId = null;
            t.elenco.clear();
            t.elenco.addAll(presidentes); // presidentes-jogadores nunca saem: ocupam vaga de wildcard
            garantirPresidentes(t);
            int fixos = t.elenco.size();
            velhos.sort(Comparator.comparingDouble((LigaKings.AtletaKings a) -> -a.forca).thenComparing(a -> a.nome));
            for (LigaKings.AtletaKings a : velhos) {
                if (t.elenco.size() - fixos >= d.mantidos || t.elenco.size() >= WILDCARDS) break; // mantidos ocupam vagas de wildcard
                if (a.jogadorId == null) continue;
                Jogador j = m.ativo(a.jogadorId);
                if (j == null || j.idade(m.ano) > 45 || daPrimeiraDivisao(j)) continue;
                pool.remove(j);
                adicionar(t, j, "mantido");
            }
        }
        // wildcards (regra do jogo, 12/11/2026): contratação por PROPOSTA, não escolha. A IA propõe aos
        // melhores nomes (valor + fama), um por time em cada volta; quem recusa segue no pool. O time do
        // técnico humano fica com as vagas abertas: ele manda as propostas pelo Mercado da Kings.
        List<Jogador> porNome = new ArrayList<>(pool);
        porNome.sort(Comparator.comparingDouble((Jogador j) -> -(preferenciaDraft(j) + j.fama * 0.2)).thenComparingInt(j -> j.id));
        Map<String, Set<Integer>> recusaram = new HashMap<>();
        for (int r = 0; r < WILDCARDS; r++) {
            for (LigaKings.TimeKings t : ordem) {
                if (t.humano || t.elenco.size() >= WILDCARDS) continue; // os mantidos e presidentes ocupam vagas de wildcard
                Set<Integer> nao = recusaram.computeIfAbsent(t.id, k -> new HashSet<>());
                for (int tent = 0; tent < 8; tent++) {
                    Jogador j = null;
                    for (Jogador x : porNome) if (!nao.contains(x.id)) { j = x; break; }
                    if (j == null) break;
                    // a IA respeita o caixa: quem não cabe no orçamento fica de fora (caixa nunca negativo)
                    if (custoWildcard(j) <= t.caixa && aceitaNaHora(t, j)) {
                        porNome.remove(j);
                        pool.remove(j);
                        contratarPago(liga, t, j);
                        break;
                    }
                    nao.add(j.id);
                }
            }
        }
        // as 6 rodadas do draft
        LigaKings.EstadoDraft e = new LigaKings.EstadoDraft();
        e.ano = m.ano;
        e.semanaCriado = m.semanaAbsoluta();
        for (int r = 0; r < DRAFTADOS; r++) {
            List<LigaKings.TimeKings> rodada = new ArrayList<>(ordem);
            if ("serpente".equals(d.ordem) && r % 2 == 1) java.util.Collections.reverse(rodada);
            for (LigaKings.TimeKings t : rodada) e.ordem.add(t.id);
        }
        for (Jogador j : pool) e.disponiveis.add(j.id);
        liga.draftAtual = e;
        avancarDraft(liga, false);
    }

    /** Escolhas da IA até a vez do humano (ou até o fim). {@code forcarHumano}: escolhe pelo humano também. */
    void avancarDraft(LigaKings liga, boolean forcarHumano) {
        LigaKings.EstadoDraft e = liga.draftAtual;
        if (e == null) return;
        while (!e.terminado()) {
            LigaKings.TimeKings t = liga.time(e.vez());
            if (t.humano && !forcarHumano) return; // espera o técnico (multi-treinador: o dono do time, na vez dele)
            List<Jogador> pool = new ArrayList<>();
            for (int id : e.disponiveis) {
                Jogador j = m.ativo(id);
                if (j != null && !daPrimeiraDivisao(j)) pool.add(j); // o clube pode ter subido depois de montar o pool
            }
            Jogador j = escolher(t, pool);
            registrarEscolha(liga, t, j);
        }
        concluirDraft(liga);
    }

    private void registrarEscolha(LigaKings liga, LigaKings.TimeKings t, Jogador j) {
        LigaKings.EstadoDraft e = liga.draftAtual;
        LigaKings.Escolha es = new LigaKings.Escolha();
        es.numero = e.atual + 1;
        es.rodada = e.atual / Math.max(1, liga.times.size()) + 1;
        es.timeId = t.id;
        es.time = t.nome;
        es.humano = t.humano;
        if (j != null && (t.elenco.size() >= MAX_ELENCO || daPrimeiraDivisao(j))) j = null; // elenco cheio (12) ou 1ª divisão: a escolha passa
        if (j != null) {
            es.jogadorId = j.id;
            es.jogador = j.nomeExibicao();
            e.disponiveis.remove(Integer.valueOf(j.id));
            adicionar(t, j, "draft");
        }
        e.escolhas.add(es);
        e.atual++;
    }

    private void concluirDraft(LigaKings liga) {
        LigaKings.EstadoDraft feito = liga.draftAtual;
        if (feito != null) {
            // histórico (aba Draftados): só as escolhas
            feito.disponiveis = new ArrayList<>();
            if (liga.draftsAnteriores == null) liga.draftsAnteriores = new ArrayList<>();
            liga.draftsAnteriores.add(feito);
            while (liga.draftsAnteriores.size() > 15) liga.draftsAnteriores.remove(0);
        }
        liga.draftAtual = null;
        if (feito != null) noticias.draft(liga, feito);
        for (LigaKings.TimeKings t : liga.times) {
            limitarElenco(m, t);
            PosicoesKings.garantirGoleiro(t);
        }
        if (m.tecnico != null && m.tecnico.kingsTimeId != null && liga.time(m.tecnico.kingsTimeId) != null) {
            m.mensagem("kings", "Draft " + br.futebas.mundo.Artigos.de(liga.nome) + " " + liga.nome, "O draft foi concluído. Confira o elenco do seu time.");
        }
    }

    /** O técnico escolhe na vez dele. Retorna false se não é a vez dele ou o jogador não está disponível. */
    public boolean escolherNoDraft(int jogadorId) {
        for (LigaKings liga : m.kings) {
            LigaKings.EstadoDraft e = liga.draftAtual;
            if (e == null || e.terminado()) continue;
            LigaKings.TimeKings t = liga.time(e.vez());
            if (t == null || !t.humano) continue;
            if (!e.disponiveis.contains(jogadorId) || m.ativo(jogadorId) == null || daPrimeiraDivisao(m.ativo(jogadorId))) return false;
            registrarEscolha(liga, t, m.ativo(jogadorId));
            avancarDraft(liga, false);
            return true;
        }
        return false;
    }

    /** O draft que espera o técnico (null se nenhum). */
    public LigaKings draftPendente() {
        for (LigaKings liga : m.kings) if (liga.draftAtual != null && !liga.draftAtual.terminado()) return liga;
        return null;
    }

    /** Tira o jogador do time da Kings: ele fica sem time e volta a poder ser contratado na Kings. */
    public boolean dispensar(String timeId, int jogadorId) {
        for (LigaKings liga : m.kings) {
            LigaKings.TimeKings t = liga.time(timeId);
            if (t == null) continue;
            boolean saiu = t.elenco.removeIf(a -> !a.presidente && a.jogadorId != null && a.jogadorId == jogadorId);
            Jogador j = m.ativo(jogadorId);
            if (saiu && j != null) j.kingsTimeId = null;
            return saiu;
        }
        return false;
    }

    /**
     * Proposta de wildcard do time (técnico humano, pelo Mercado da Kings): sem contrato, vai direto ao
     * jogador; com contrato, primeiro ao clube dele (nunca 1ª divisão), depois ao jogador. A resposta
     * sai em até uma semana (1–7 dias). Falso se o elenco (com as propostas em andamento) já tem 12,
     * se já há proposta a ele ou se ele está fora das regras.
     */
    public boolean contratar(String timeId, int jogadorId) {
        for (LigaKings liga : m.kings) {
            LigaKings.TimeKings t = liga.time(timeId);
            if (t == null) continue;
            Jogador j = m.ativo(jogadorId);
            if (j == null || j.kingsTimeId != null || t.elenco.size() + pendentes(liga, t) >= MAX_ELENCO) return false;
            for (LigaKings.PropostaKings p : liga.propostas) if (p.jogadorId == jogadorId && p.timeId.equals(timeId)) return false;
            if (!elegiveis(liga, true).contains(j)) return false;
            // regra do jogo: o técnico não faz proposta que o caixa (menos o já reservado) não paga
            if (custoWildcard(j) > DinheiroKings.disponivel(liga, t)) return false;
            LigaKings.PropostaKings p = new LigaKings.PropostaKings();
            p.timeId = timeId;
            p.jogadorId = jogadorId;
            Clube c = m.clube(j.clubeId);
            p.etapa = c != null && j.contrato != null ? "clube" : "jogador";
            p.taxa = c != null && j.contrato != null ? taxaKings(j) : 0;
            p.salario = salarioKings(j);
            p.respostaSa = m.semanaAbsoluta() + 1;
            liga.propostas.add(p);
            return true;
        }
        return false;
    }

    /** Propostas em andamento do time (contam no teto de 12). */
    public static int pendentes(LigaKings liga, LigaKings.TimeKings t) {
        int n = 0;
        if (liga.propostas != null) for (LigaKings.PropostaKings p : liga.propostas) if (p.timeId.equals(t.id)) n++;
        return n;
    }

    /** Vagas de wildcard abertas do time (6 menos wildcards, mantidos, presidentes que jogam e contratados). */
    public static int vagasWildcard(LigaKings.TimeKings t) {
        int ocupadas = 0;
        for (LigaKings.AtletaKings a : t.elenco) if (!"draft".equals(a.tipo)) ocupadas++;
        return Math.max(0, Math.min(WILDCARDS - ocupadas, MAX_ELENCO - t.elenco.size()));
    }

    /**
     * Taxa ao clube do jogador com contrato (sai do caixa do time da Kings). Escala do orçamento de
     * R$ 1,5 mi: força 60 → ~R$ 74 mil, 70 → ~R$ 122 mil, 80 → ~R$ 182 mil.
     */
    public long taxaKings(Jogador j) {
        return Math.max(10_000L, Math.round(Math.pow(Math.max(30, j.forca) - 25, 2) * 60) / 1000 * 1000);
    }

    /**
     * Salário da temporada do wildcard (pago ao assinar): R$ 20 mil + pela força na escala da Kings
     * (70 → ~R$ 68 mil, 80 → ~R$ 128 mil) + R$ 500 por ponto de fama.
     */
    public long salarioKings(Jogador j) {
        double fk = forcaAtleta(j);
        return Math.round(20_000 + Math.pow(Math.max(50, fk) - 50, 2) * 120 + Math.max(0, j.fama) * 500) / 1000 * 1000;
    }

    /** Custo total de um wildcard: taxa ao clube (se tem contrato) + salário da temporada. */
    public long custoWildcard(Jogador j) {
        return (m.clube(j.clubeId) != null && j.contrato != null ? taxaKings(j) : 0) + salarioKings(j);
    }

    /** IA: paga o wildcard (taxa + salário) e o põe no elenco. Só chame depois de conferir o caixa. */
    private void contratarPago(LigaKings liga, LigaKings.TimeKings t, Jogador j) {
        long taxa = m.clube(j.clubeId) != null && j.contrato != null ? taxaKings(j) : 0, sal = salarioKings(j);
        if (!DinheiroKings.debitar(m, t, "contratacao", "Wildcard " + j.nomeExibicao() + (taxa > 0 ? " (taxa + salário)" : " (salário)"), taxa + sal)) return;
        adicionar(t, j, "wildcard");
        t.elenco.get(t.elenco.size() - 1).salario = sal;
        noticias.wildcard(liga, t, j, taxa + sal);
    }

    /** O clube (com contrato) libera o jogador? Clube sem divisão nacional libera mais; o craque do time, menos. */
    double chanceClubeAceitar(Jogador j) {
        Clube c = m.clube(j.clubeId);
        if (c == null || j.contrato == null) return 1;
        double p = c.divisao == 0 ? 0.75 : 0.55;
        if (j.forca > c.nivel() + 4) p -= 0.25; // um dos melhores do clube
        if (j.idade(m.ano) >= 33) p += 0.15;
        return Math.max(0.1, Math.min(0.95, p));
    }

    /** O jogador aceita jogar na Kings? Sem contrato quase sempre; aposentado e de clube sem divisão, quase sempre. */
    double chanceJogadorAceitar(Jogador j) {
        return switch (j.status) {
            case LIVRE -> 0.9;
            case APOSENTADO -> 0.8;
            case PROFISSIONAL -> {
                Clube c = m.clube(j.clubeId);
                yield c == null ? 0.9 : c.divisao == 0 ? 0.7 : 0.55;
            }
            default -> 0.5;
        };
    }

    /**
     * Proposta da IA (resposta na hora): com contrato, o clube responde primeiro e depois o jogador.
     * Jogador do clube do técnico humano: a IA não faz proposta (a negociação com o clube humano pela
     * caixa de entrada ainda não existe).
     */
    boolean aceitaNaHora(LigaKings.TimeKings t, Jogador j) {
        if (daPrimeiraDivisao(j)) return false;
        Clube c = m.clube(j.clubeId);
        if (c != null && c.humano) return false;
        if (c != null && j.contrato != null && !rng.chance(chanceClubeAceitar(j))) return false;
        return rng.chance(chanceJogadorAceitar(j));
    }

    /** Respostas das propostas que venceram (clube e depois jogador) e mensagens ao técnico. */
    void responderPropostas(LigaKings liga, int sa) {
        if (liga.propostas == null || liga.propostas.isEmpty()) return;
        for (LigaKings.PropostaKings p : new ArrayList<>(liga.propostas)) {
            if (p.respostaSa > sa) continue;
            LigaKings.TimeKings t = liga.time(p.timeId);
            Jogador j = m.ativo(p.jogadorId);
            boolean meu = m.tecnico != null && p.timeId.equals(m.tecnico.kingsTimeId);
            String nome = j == null ? "o jogador" : j.nomeExibicao();
            if (t == null || j == null || j.kingsTimeId != null || t.elenco.size() >= MAX_ELENCO || daPrimeiraDivisao(j)) {
                liga.propostas.remove(p);
                if (meu) m.mensagem("kings", "Proposta encerrada", "A proposta a " + nome + " foi encerrada (ele já tem time na Kings ou o elenco está cheio).");
                continue;
            }
            if ("clube".equals(p.etapa)) {
                Clube c = m.clube(j.clubeId);
                if (rng.chance(chanceClubeAceitar(j))) {
                    p.etapa = "jogador";
                    p.respostaSa = sa + 1;
                    if (meu) m.mensagem("kings", "Clube aceitou", "O " + (c == null ? "clube" : c.nome) + " aceitou liberar " + nome
                            + " (taxa de " + DinheiroKings.reais(p.taxa) + "). Agora é com o jogador.");
                } else {
                    liga.propostas.remove(p);
                    if (meu) m.mensagem("kings", "Proposta recusada", "O " + (c == null ? "clube" : c.nome) + " recusou liberar " + nome + " para a Kings.");
                }
                continue;
            }
            liga.propostas.remove(p);
            if (rng.chance(chanceJogadorAceitar(j))) {
                if (!DinheiroKings.debitar(m, t, "contratacao", "Wildcard " + nome + (p.taxa > 0 ? " (taxa + salário)" : " (salário)"), p.custo())) {
                    if (meu) m.mensagem("kings", "Proposta encerrada", nome + " aceitou, mas o caixa do " + t.nome + " não cobre a taxa e o salário.");
                    continue;
                }
                adicionar(t, j, "wildcard");
                t.elenco.get(t.elenco.size() - 1).salario = p.salario;
                noticias.wildcard(liga, t, j, p.custo());
                if (meu) m.mensagem("kings", "Wildcard contratado", nome + " aceitou a proposta e é wildcard do " + t.nome + " (custou "
                        + DinheiroKings.reais(p.custo()) + ").");
            } else if (meu) {
                m.mensagem("kings", "Proposta recusada", nome + " recusou a proposta do " + t.nome + ".");
            }
        }
    }

    /**
     * Vagas abertas dos times da IA (dispensas, aposentadorias): uma proposta por semana a cada time
     * com menos de 12. Time do humano com menos de 7 na hora do jogo: o auxiliar completa (ver {@link #jogar}).
     */
    void preencherVagasIA(LigaKings liga) {
        if (liga.custom) {
            completarCustom(liga); // IOS: alguém saiu → atleta genérico
            return;
        }
        if (liga.draftAtual != null || !liga.ativa || ehMundial(liga)) return;
        List<Jogador> pool = null;
        for (LigaKings.TimeKings t : liga.times) {
            if (t.humano || t.elenco.size() >= MAX_ELENCO) continue;
            if (pool == null) pool = elegiveis(liga, false);
            boolean wildcard = vagasWildcard(t) > 0;
            for (Jogador j : pool) {
                if (j.kingsTimeId != null) continue;
                if (wildcard && custoWildcard(j) > t.caixa) continue; // a IA só propõe o que pode pagar
                if (aceitaNaHora(t, j)) {
                    if (wildcard) contratarPago(liga, t, j);
                    else adicionar(t, j, "draft"); // vaga de draft (reposição): a liga paga
                }
                break; // uma proposta por semana
            }
        }
    }

    /** Gerador de nomes (prenome + sobrenome reais do país, sorteados: {@code Populador.nomeGerado}); null nos testes sem Simulador. */
    public transient java.util.function.Function<String, String> nomeGerado;

    /** Todas as ligas customizadas (IOS) completas até 12 (no mundo novo e ao carregar). */
    public void completarLigasCustom() {
        if (m.kings != null) for (LigaKings l : m.kings) if (l.custom) completarCustom(l);
    }

    /**
     * Kings League IOS (requisito, 12/11/2026): cada time é o presidente (que joga) e atletas
     * GENÉRICOS gerados (nomes do gerador do jogo, nunca gente real) até 12, com posições para um
     * elenco equilibrado (2 goleiros, defesa, meio e ataque) e força na escala da IOS (~69 ± 3,
     * entre 60 e 76). Completado no mundo novo e sempre que alguém sai.
     */
    void completarCustom(LigaKings liga) {
        for (LigaKings.TimeKings t : liga.times) {
            garantirPresidentes(t);
            int guarda = 0;
            while (t.elenco.size() < MAX_ELENCO && guarda++ < 40) t.elenco.add(atletaGenerico(liga, t));
        }
    }

    /** Alvo de um elenco de 12 por setor: goleiro, defesa, meio, ataque. */
    private static final int[] ALVO_SETOR = {2, 4, 3, 3};

    private LigaKings.AtletaKings atletaGenerico(LigaKings liga, LigaKings.TimeKings t) {
        int[] cont = new int[4];
        for (LigaKings.AtletaKings a : t.elenco) cont[PosicoesKings.setor(a.posicao)]++;
        int s = cont[0] == 0 ? 0 : 1;
        double pior = Double.MAX_VALUE;
        if (cont[0] > 0) for (int k = 0; k < 4; k++) {
            double r = cont[k] / (double) ALVO_SETOR[k];
            if (r < pior) {
                pior = r;
                s = k;
            }
        }
        Set<String> usados = new HashSet<>();
        for (LigaKings.TimeKings x : liga.times) for (LigaKings.AtletaKings a : x.elenco) if (a.nome != null) usados.add(a.nome);
        String pais = liga.paisDraft != null ? liga.paisDraft : "BRA";
        String nome = null;
        for (int i = 0; i < 20 && (nome == null || usados.contains(nome)); i++) nome = nomeGerado == null ? null : nomeGerado.apply(pais);
        if (nome == null || usados.contains(nome)) nome = "Atleta " + t.nome + " " + (t.elenco.size() + 1);
        LigaKings.AtletaKings a = new LigaKings.AtletaKings();
        a.nome = nome;
        a.posicao = PosicoesKings.posicao(s == 0 ? "GOL" : s == 1 ? "DEF" : s == 2 ? "MEI" : "ATA", nome);
        a.forca = Math.round(Math.max(60, Math.min(76, 69 + rng.gauss(0, 3))) * 10) / 10.0;
        a.tipo = vagasWildcard(t) > 0 ? "wildcard" : "draft";
        a.fonte = "gerado (Kings League IOS)";
        a.pais = pais;
        a.anoNasc = m.ano - (19 + rng.inteiro(14));
        a.idFixo = "ios-ger-" + t.id + "-" + nome.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return a;
    }

    /** O auxiliar completa o time do humano até 7 (sem 7, não há 7x7). */
    void completarAteSete(LigaKings liga, LigaKings.TimeKings t) {
        if (liga == null || t.elenco.size() >= 7) return;
        int antes = t.elenco.size();
        for (Jogador j : elegiveis(liga, true)) {
            if (t.elenco.size() >= 7) break;
            if (j.kingsTimeId != null) continue;
            Clube c = m.clube(j.clubeId);
            if (c != null && c.humano || daPrimeiraDivisao(j)) continue;
            if (rng.chance(chanceJogadorAceitar(j))) adicionar(t, j, "wildcard");
        }
        if (t.elenco.size() > antes && m.tecnico != null && t.id.equals(m.tecnico.kingsTimeId))
            m.mensagem("kings", "O auxiliar completou o elenco", "Faltavam atletas para o 7x7: o auxiliar contratou " + (t.elenco.size() - antes)
                    + " wildcard(s). Use o Mercado da Kings para preencher as vagas abertas.");
    }

    private Jogador escolher(LigaKings.TimeKings t, List<Jogador> pool) {
        if (pool.isEmpty()) return null;
        if (t.humano) {
            for (int id : t.prioridadesDraft) {
                Jogador j = m.jogador(id);
                if (j != null && pool.contains(j)) return j;
            }
        }
        // a IA equilibra posições: precisa de goleiro?
        boolean temGoleiro = false;
        for (LigaKings.AtletaKings a : t.elenco) if (a.posicao == Posicao.GOL) temGoleiro = true;
        if (!temGoleiro && t.elenco.size() >= 3) {
            for (Jogador j : pool) if (j.posicao == Posicao.GOL) return j;
        }
        return pool.get(0);
    }

    /**
     * O jogador está HOJE num clube da 1ª divisão? (regra do jogo: nunca entra na Kings). Conferido na
     * hora de cada escolha/contratação: listas montadas antes (pool do draft, propostas em andamento)
     * podem ter ficado velhas com o acesso na virada do ano.
     */
    boolean daPrimeiraDivisao(Jogador j) {
        Clube c = j == null ? null : m.clube(j.clubeId);
        return c != null && c.divisao == 1;
    }

    private void adicionar(LigaKings.TimeKings t, Jogador j, String tipo) {
        LigaKings.AtletaKings a = new LigaKings.AtletaKings();
        Clube origem = m.clube(j.clubeId);
        if (origem != null) {
            a.clubeOrigem = origem.id;
            a.divisaoOrigem = origem.divisao;
        }
        a.jogadorId = j.id;
        a.nome = j.nomeExibicao();
        a.posicao = j.posicao;
        a.forca = forcaAtleta(j);
        a.tipo = tipo;
        t.elenco.add(a);
        j.kingsTimeId = t.id;
        deixarClubeAoEntrarNaKings(j, t);
    }

    /** Regra do jogo: quem é draftado (ou vira wildcard) deixa o clube em que estava e fica só na Kings. */
    void deixarClubeAoEntrarNaKings(Jogador j, LigaKings.TimeKings t) {
        Clube c = m.clube(j.clubeId);
        if (c == null) return;
        c.elenco.remove(Integer.valueOf(j.id));
        c.base.remove(Integer.valueOf(j.id));
        if (c.humano) m.mensagem("jogador", "Saída para a Kings League", j.nomeExibicao() + " deixou o clube para jogar no " + t.nome + " (Kings League).");
        j.clubeId = null;
        j.contrato = null;
        j.aVenda = false;
        j.aEmprestimo = false;
        j.status = StatusJogador.LIVRE;
    }

    // ===================================================================== partidas
    /** Liga dona da competição (null nas Copas do Mundo da Kings). */
    private LigaKings ligaDe(Torneio t) {
        for (LigaKings l : m.kings) if (l.torneio == t || l.competicoes.contains(t)) return l;
        return null;
    }

    @Override
    public ResultadoPartida jogar(Torneio t, Partida p, boolean prorrogacao) {
        LigaKings liga = ligaDe(t);
        LigaKings.RegrasKings regras = liga != null ? liga.regras : regrasPadrao();
        LigaKings.TimeKings a = timeKings(p.mandante), b = timeKings(p.visitante);
        if (liga != null) for (LigaKings.TimeKings x : List.of(a, b)) if (x.humano) completarAteSete(liga, x);
        PosicoesKings.garantirGoleiro(a); // seleções/elencos montados sem goleiro: o mais fraco de linha vai pro gol
        PosicoesKings.garantirGoleiro(b);
        if (m.multi()) return jogarMulti(t, p, a, b, regras);
        boolean narrar = m.tecnico != null && (a.id.equals(m.tecnico.kingsTimeId) || b.id.equals(m.tecnico.kingsTimeId));
        AoVivoKings tela = semAoVivo ? null : aoVivo;
        int lado = !narrar ? -1 : a.id.equals(m.tecnico.kingsTimeId) ? 0 : 1;
        // jogo delegado ao auxiliar (competição ou partida): sem a tela ao vivo, só o resultado
        boolean auxiliar = narrar && m.tecnico.delegado(t.id, p.id, m.ano);
        ResultadoPartida r = MotorKings.simular(a, b, regras, rng.derivar(p.id + 7L * t.ano), narrar, narrar && !auxiliar ? tela : null, lado);
        if (narrar) ultimaNarrada = r;
        if (lado >= 0) {
            jogosDoHumanoNaSemana.add(p);
            narradasNaSemana.add(r);
        }
        if (lado >= 0) resultadoDoHumano(lado == 0 ? a : b, lado == 0 ? b : a, r, lado, regras);
        noticias.partida(liga, t, p, r, a, b);
        if (br.futebas.mundo.Sonda.Atual.s != null) br.futebas.mundo.Sonda.Atual.s.partidaKings(t, p, r);
        if (r.penaltisMandante != null) shootouts.put(p, new int[]{r.penaltisMandante, r.penaltisVisitante});
        return r;
    }

    /**
     * Multi-treinador: jogo da Kings sem a tela ao vivo (o resultado aparece no fim da rodada). Cada
     * lado de um técnico humano conta para a carreira dele (com ele na vez, para as mensagens dele).
     */
    private ResultadoPartida jogarMulti(Torneio t, Partida p, LigaKings.TimeKings a, LigaKings.TimeKings b, LigaKings.RegrasKings regras) {
        br.futebas.mundo.Tecnico ta = m.tecnicoDoTimeKings(a.id), tb = m.tecnicoDoTimeKings(b.id);
        boolean narrar = ta != null || tb != null;
        ResultadoPartida pronto = preJogadasMulti.remove(p);
        final ResultadoPartida r = pronto != null ? pronto : narrar && aoVivoMulti != null ? jogarGrupoMulti(t, p)
                : MotorKings.simular(a, b, regras, rng.derivar(p.id + 7L * t.ano), narrar, null, -1);
        if (narrar) {
            ultimaNarrada = r;
            jogosDoHumanoNaSemana.add(p);
            narradasNaSemana.add(r);
        }
        if (ta != null) m.comTecnico(ta, () -> resultadoDoHumano(a, b, r, 0, regras));
        if (tb != null) m.comTecnico(tb, () -> resultadoDoHumano(b, a, r, 1, regras));
        noticias.partida(ligaDe(t), t, p, r, a, b);
        if (br.futebas.mundo.Sonda.Atual.s != null) br.futebas.mundo.Sonda.Atual.s.partidaKings(t, p, r);
        if (r.penaltisMandante != null) shootouts.put(p, new int[]{r.penaltisMandante, r.penaltisVisitante});
        return r;
    }

    /** Multi-treinador: a tela dividida dos jogos da Kings (null = sem tela). */
    public transient AoVivoKingsMulti aoVivoMulti;
    /** Multi-treinador: jogos da Kings de técnicos já simulados no grupo, à espera do torneio chegar neles. */
    private final Map<Partida, ResultadoPartida> preJogadasMulti = new java.util.HashMap<>();
    /** Multi-treinador: times de técnicos que já jogaram ao vivo nesta semana. */
    private final java.util.Set<String> jogaramMulti = new java.util.HashSet<>();

    /**
     * Multi-treinador: o jogo {@code p} e os outros jogos da Kings dos técnicos humanos na mesma data
     * desta semana rodam juntos (uma thread por jogo), ao vivo na tela dividida.
     */
    private ResultadoPartida jogarGrupoMulti(Torneio t, Partida p) {
        int sa = m.semanaAbsoluta();
        List<Object[]> grupo = new ArrayList<>();
        java.util.Set<String> usados = new java.util.HashSet<>();
        List<Object[]> cands = new ArrayList<>();
        cands.add(new Object[]{t, p});
        for (Torneio tq : torneiosAtivos()) {
            if (tq.encerrado || tq.proximaData >= tq.totalDatas || tq.semanaDaData(tq.proximaData) > sa) continue;
            for (Partida q : tq.partidas) {
                if (q == p || q.jogada || q.data != tq.proximaData || q.mandante == null || q.visitante == null) continue;
                if (m.tecnicoDoTimeKings(q.mandante) == null && m.tecnicoDoTimeKings(q.visitante) == null) continue;
                cands.add(new Object[]{tq, q});
            }
        }
        for (Object[] o : cands) {
            Torneio tq = (Torneio) o[0];
            Partida q = (Partida) o[1];
            if (usados.contains(q.mandante) || usados.contains(q.visitante)) continue;
            usados.add(q.mandante);
            usados.add(q.visitante);
            LigaKings liga = ligaDe(tq);
            LigaKings.RegrasKings rg = liga != null ? liga.regras : regrasPadrao();
            LigaKings.TimeKings x = timeKings(q.mandante), y = timeKings(q.visitante);
            if (x == null || y == null) continue;
            if (liga != null) for (LigaKings.TimeKings z : List.of(x, y)) if (z.humano) completarAteSete(liga, z);
            PosicoesKings.garantirGoleiro(x);
            PosicoesKings.garantirGoleiro(y);
            boolean hx = m.tecnicoDoTimeKings(x.id) != null, hy = m.tecnicoDoTimeKings(y.id) != null;
            AoVivoKings av = aoVivoMulti.partida(x.id, y.id, hx, hy);
            // sorteio na thread do motor (a ordem do mundo não depende das threads)
            grupo.add(new Object[]{q, x, y, rg, rng.derivar(q.id + 7L * tq.ano), av, new boolean[]{hx, hy}, null});
        }
        // rodada dupla: quem já jogou nesta semana volta à central antes do próximo jogo
        List<String> humanos = new ArrayList<>();
        for (Object[] g : grupo) for (LigaKings.TimeKings z : List.of((LigaKings.TimeKings) g[1], (LigaKings.TimeKings) g[2]))
            if (m.tecnicoDoTimeKings(z.id) != null) humanos.add(z.id);
        boolean repetiu = false;
        for (String id : humanos) if (jogaramMulti.contains(id)) repetiu = true;
        if (repetiu) aoVivoMulti.antesDoGrupo(humanos);
        jogaramMulti.addAll(humanos);
        aoVivoMulti.iniciarGrupo();
        List<Thread> ths = new ArrayList<>();
        try {
            for (Object[] g : grupo) {
                Thread th = new Thread(() -> {
                    try {
                        g[7] = MotorKings.simular((LigaKings.TimeKings) g[1], (LigaKings.TimeKings) g[2], (LigaKings.RegrasKings) g[3],
                                (br.futebas.mundo.Rng) g[4], true, (AoVivoKings) g[5], ((boolean[]) g[6])[0], ((boolean[]) g[6])[1]);
                    } catch (RuntimeException e) {
                        g[7] = e;
                    } finally {
                        if (g[5] != null) aoVivoMulti.terminou((AoVivoKings) g[5]);
                    }
                }, "kings-multi");
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
        ResultadoPartida meu = null;
        for (Object[] g : grupo) {
            if (g[7] instanceof RuntimeException e) throw e;
            if (g[0] == p) meu = (ResultadoPartida) g[7];
            else preJogadasMulti.put((Partida) g[0], (ResultadoPartida) g[7]);
        }
        return meu;
    }

    @Override
    public int[] penaltis(Torneio t, Partida p) {
        int[] jaDecidido = shootouts.remove(p);
        if (jaDecidido != null) return jaDecidido; // o motor de 2026 já decide nos shootouts
        LigaKings liga = ligaDe(t);
        LigaKings.RegrasKings regras = liga != null ? liga.regras : regrasPadrao();
        LigaKings.TimeKings a = timeKings(p.mandante), b = timeKings(p.visitante);
        double fa = MotorKings.forca(a, regras.jogadores_em_campo), fb = MotorKings.forca(b, regras.jogadores_em_campo);
        int ga = 0, gb = 0;
        // shootout 1x1 da Kings: 5 cobranças, depois alternadas
        for (int i = 0; i < 5; i++) {
            if (rng.chance(0.55 + (fa - fb) * 0.004)) ga++;
            if (rng.chance(0.55 + (fb - fa) * 0.004)) gb++;
        }
        int k = 0;
        while (ga == gb && k++ < 30) {
            if (rng.chance(0.55)) ga++;
            if (rng.chance(0.55)) gb++;
        }
        if (ga == gb) ga++;
        if (ultimaNarrada != null && ultimaNarrada.mandanteId != null && ultimaNarrada.mandanteId.equals(a.id) && ultimaNarrada.visitanteId.equals(b.id)) {
            ultimaNarrada.penaltisMandante = ga;
            ultimaNarrada.penaltisVisitante = gb;
            ultimaNarrada.lances.add(new Lance(liga.regras.duracao_min, 0, "penalidades",
                    "Shoot out: " + a.nome + " " + ga + " x " + gb + " " + b.nome + ".", -1, 0, 0, null, null, null, null));
        }
        return new int[]{ga, gb};
    }

    /** Tabela simples por time (gols de cada atleta), para a tela da Kings. */
    public static Map<String, Integer> artilharia(LigaKings liga) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (LigaKings.TimeKings t : liga.times) for (LigaKings.AtletaKings a : t.elenco) if (a.gols > 0) m.put(a.nome + " (" + t.nome + ")", a.gols);
        return m;
    }
}
