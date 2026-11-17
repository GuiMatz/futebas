package br.futebas.ui.modelo;

import br.futebas.Jogo;
import br.futebas.competicao.FaseExec;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.historia.Historia;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Mensagem;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Tecnico;
import br.futebas.partida.AoVivo;
import br.futebas.partida.ResultadoPartida;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O laço do jogo, sem JavaFX: "jogar a próxima rodada" (pula as semanas sem jogo, joga a
 * semana do próximo jogo com acompanhamento ao vivo), fim de temporada, demissão e o modo
 * "só assistir". As telas chamam isto numa thread de trabalho e mostram o que voltar.
 */
public final class Fluxo {

    public record Rodada(int ano, int semana, List<Partida> meusJogos, List<ResultadoPartida> narradas,
                         List<Partida> outrosResultados, List<Mensagem> novasMensagens,
                         ResumoTemporada fimDeTemporada, boolean demitido) {
        public boolean jogou() { return !meusJogos.isEmpty(); }
    }

    public record LinhaCampanha(String competicaoId, String ambito, String competicao, String resultado) {
        public boolean campeao() { return "Campeão".equals(resultado); }
    }

    /**
     * Um título do time de um técnico humano (clube, seleção ou time da Kings), à espera da tela de
     * campeão. {@code ano} é o ano da temporada do torneio.
     */
    public record Conquista(String tecnico, String timeId, String time, String competicaoId, String competicao,
                            String ambito, String pais, int ano) {}

    /** Clube que mudou de divisão (0 = sem divisão nacional). */
    public record Movimento(String clubeId, String nome, String pais, int de, int para, String texto) {}

    public record ResumoTemporada(int ano, List<Historia.Titulo> campeoes, List<Movimento> acessos, List<Movimento> rebaixamentos,
                                  List<Historia.Premio> premios, String clubeId, String clube, int vitorias, int empates, int derrotas,
                                  List<LinhaCampanha> campanha, List<String> titulosDoTecnico, String veredito,
                                  double confianca, List<Historia.Evento> manchetes) {}

    private final Jogo jogo;
    private Map<String, Integer> divisoesAntes;
    private int[] tecnicoAntes;
    private String clubeAntes;
    /** Time da Kings no começo da temporada (carreira da Kings). */
    private String kingsAntes;
    private int eventosVistos;

    /** Avisado no fim de cada semana do calendário, na thread do motor (salvamento automático). */
    public interface AposSemana {
        void depois(java.time.LocalDate antes, java.time.LocalDate depois, boolean novaTemporada);
    }

    private volatile AposSemana aposSemana;

    public void aposSemana(AposSemana a) { aposSemana = a; }

    public Fluxo(Jogo jogo) {
        this.jogo = jogo;
        this.eventosVistos = jogo.historia().linhaDoTempo.size();
        marcarInicioTemporada();
    }

    public Jogo jogo() { return jogo; }

    private void marcarInicioTemporada() {
        Mundo mm = jogo.mundo();
        if (mm.multi()) {
            // multi-treinador: as marcas de cada técnico (o resumo do fim de temporada é de cada um)
            mm.paraCadaTecnico(() -> {
                Tecnico x = mm.tecnico;
                Clube cx = jogo.meuClube();
                marcas.put(x, new Object[]{new int[]{x.vitorias, x.empates, x.derrotas}, cx == null ? null : cx.id, x.kingsTimeId});
            });
        }
        Tecnico t = jogo.mundo().tecnico;
        tecnicoAntes = t == null ? new int[3] : new int[]{t.vitorias, t.empates, t.derrotas};
        Clube c = jogo.meuClube();
        clubeAntes = c == null ? null : c.id;
        kingsAntes = t == null ? null : t.kingsTimeId;
    }

    /** Há jogo do técnico marcado para a semana atual? */
    public boolean temJogoNaSemana() {
        Clube c = jogo.meuClube();
        if (c == null) return false;
        int w = jogo.semana();
        for (Partida p : jogo.calendarioDoClube(c.id)) if (!p.jogada && p.sa == jogo.mundo().semanaAbsoluta()) return true;
        return false;
    }

    /** Próximo jogo do técnico (null se não houver mais nada marcado no ano). */
    public Partida proximoJogo() {
        Clube c = jogo.meuClube();
        if (c == null) return null;
        List<Partida> l = jogo.proximasPartidas(c.id, 1);
        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * Avança até o próximo jogo do técnico e o joga (com {@code aoVivo}, se houver). Para antes
     * se a temporada acabar ou o técnico for demitido nas semanas sem jogo.
     */
    public Rodada jogarProximaRodada(AoVivo aoVivo) {
        int msgs = jogo.caixaDeEntrada().size();
        int limite = 60;
        if (soKings()) {
            // rodada dupla: o 2º jogo da semana ficou adiado e é jogado sozinho, sem avançar a semana
            Rodada adiado = jogarAdiadoKings(msgs);
            if (adiado != null) return adiado;
            // técnico só da Kings: avança até a semana com jogo do time dele
            while (limite-- > 0 && soKings() && !temJogoKingsNaSemana()) {
                Rodada r = semana(null, msgs);
                // um jogo do time dele numa semana "sem jogo" (competição criada na própria semana): para
                // aqui e mostra o fim desse jogo, em vez de seguir direto para o próximo
                if (r.jogou() || r.fimDeTemporada() != null || !r.novasMensagens().isEmpty() && jogo.propostasDeEmprego().size() > 0) return r;
            }
            return semana(aoVivo, msgs);
        }
        while (limite-- > 0 && jogo.meuClube() != null && !temJogoNaSemana()) {
            Rodada r = semana(null, msgs);
            if (r.fimDeTemporada() != null || r.demitido()) return r;
        }
        if (jogo.meuClube() == null) return semana(null, msgs);
        return semana(aoVivo, msgs);
    }

    /** Técnico sem clube que comanda um time da Kings League. */
    public boolean soKings() {
        return jogo.meuClube() == null && jogo.mundo().tecnico != null && jogo.mundo().tecnico.kingsTimeId != null;
    }

    /** Próximo jogo do seu time da Kings (carreira da Kings); null se não houver mais nada marcado no ano. */
    public Partida proximoJogoKings() {
        String id = jogo.mundo().tecnico == null ? null : jogo.mundo().tecnico.kingsTimeId;
        if (id == null) return null;
        Partida melhor = null;
        try {
            for (Torneio t : jogo.competicoesKings())
                for (Partida p : t.partidas)
                    if (!p.jogada && (id.equals(p.mandante) || id.equals(p.visitante)) && (melhor == null || p.sa < melhor.sa || p.sa == melhor.sa && p.data < melhor.data)) melhor = p;
        } catch (RuntimeException e) {
            return null;
        }
        return melhor;
    }

    /** O jogo adiado da rodada dupla da Kings, jogado sem avançar a semana; null se não havia. */
    private Rodada jogarAdiadoKings(int msgsAntes) {
        int titulosAntes = jogo.mundo().historia.titulos.size();
        Map<Tecnico, java.util.Set<String>> timesAntes = timesDosTecnicos();
        Jogo.ResumoSemana rs = jogo.jogarAdiadoKings();
        if (rs == null) return null;
        coletarConquistas(titulosAntes, timesAntes);
        List<Mensagem> novas = new ArrayList<>();
        List<Mensagem> todas = jogo.caixaDeEntrada();
        for (int i = Math.max(0, msgsAntes); i < todas.size(); i++) novas.add(todas.get(i));
        return new Rodada(rs.ano(), rs.semana(), rs.jogosDoUsuario(), rs.narradas(), outrosResultados(rs.jogosDoUsuario(), rs.ano()), novas, null, false);
    }

    /** O time da Kings do técnico joga nesta semana? */
    public boolean temJogoKingsNaSemana() {
        String id = jogo.mundo().tecnico == null ? null : jogo.mundo().tecnico.kingsTimeId;
        if (id == null) return false;
        int sa = jogo.mundo().semanaAbsoluta();
        try {
            for (Torneio t : jogo.competicoesKings())
                for (Partida p : t.partidas) if (!p.jogada && p.sa == sa && (id.equals(p.mandante) || id.equals(p.visitante))) return true;
        } catch (RuntimeException e) {
            return false;
        }
        return false;
    }

    /** Avança uma semana (sem pular). */
    public Rodada avancarSemana() {
        return semana(null, jogo.caixaDeEntrada().size());
    }

    private Rodada semana(AoVivo aoVivo, int msgsAntes) {
        Mundo m = jogo.mundo();
        boolean tinhaClube = jogo.meuClube() != null;
        boolean tinhaKings = soKings();
        if (m.semana >= 52) {
            divisoesAntes = new HashMap<>();
            for (Clube c : m.clubes.values()) if (!c.virtual) divisoesAntes.put(c.id, c.divisao);
        }
        int ano = m.ano, sem = m.semana;
        java.time.LocalDate dataAntes = jogo.dataAtual();
        int titulosAntes = m.historia.titulos.size();
        Map<Tecnico, java.util.Set<String>> timesAntes = timesDosTecnicos();
        Jogo.ResumoSemana rs;
        jogo.definirAoVivo(aoVivo);
        try {
            rs = jogo.avancarSemana();
        } finally {
            jogo.definirAoVivo(null);
        }
        coletarConquistas(titulosAntes, timesAntes);
        ResumoTemporada fim = null;
        if (rs.novaTemporada() && m.multi()) {
            // multi-treinador: um resumo para cada técnico, mostrado na vez dele
            m.paraCadaTecnico(() -> {
                usarMarcas(m.tecnico);
                fimPendente.put(m.tecnico, resumo(ano));
            });
            fim = fimPendente.get(m.tecnico);
            marcarInicioTemporada();
        } else {
            fim = rs.novaTemporada() ? resumo(ano) : null;
            if (fim != null) marcarInicioTemporada();
        }
        List<Partida> outros = outrosResultados(rs.jogosDoUsuario(), ano);
        List<Mensagem> novas = new ArrayList<>();
        List<Mensagem> todas = jogo.caixaDeEntrada();
        for (int i = Math.max(0, msgsAntes); i < todas.size(); i++) {
            Mensagem x = todas.get(i);
            // os placares já aparecem na tela da partida: não enchem a caixa de não lidas
            if ("resultado".equals(x.tipo)) x.lida = true;
            novas.add(x);
        }
        // perdeu o emprego (diretoria do clube ou presidente da Kings); trocar de emprego não conta
        boolean demitido = (tinhaClube || tinhaKings) && jogo.meuClube() == null && !jogo.soAssistindo() && !soKings();
        AposSemana a = aposSemana;
        if (a != null) a.depois(dataAntes, jogo.dataAtual(), rs.novaTemporada());
        return new Rodada(ano, sem, rs.jogosDoUsuario(), rs.narradas(), outros, novas, fim, demitido);
    }

    /** Os outros jogos das mesmas competições e semana dos jogos do técnico. */
    private List<Partida> outrosResultados(List<Partida> meus, int ano) {
        List<Partida> l = new ArrayList<>();
        if (meus.isEmpty()) return l;
        Mundo m = jogo.mundo();
        List<Torneio> ts = new ArrayList<>(m.ano == ano ? m.torneios : m.torneiosAnteriores);
        ts.addAll(jogo.competicoesKings()); // jogos do time da Kings: os outros jogos da rodada da Kings
        for (Partida p : meus) {
            for (Torneio t : ts) {
                if (!t.id.equals(p.torneioId)) continue;
                for (Partida q : t.partidas) if (q != p && q.jogada && q.data == p.data && !meus.contains(q)) l.add(q);
            }
        }
        return l;
    }

    /** Torneio de uma partida (no ano atual ou no anterior). */
    public Torneio torneioDe(Partida p) {
        for (Torneio t : jogo.mundo().torneios) if (t.id.equals(p.torneioId) && t.partidas.contains(p)) return t;
        for (Torneio t : jogo.mundo().torneiosAnteriores) if (t.id.equals(p.torneioId) && t.partidas.contains(p)) return t;
        for (Torneio t : jogo.competicoesKings()) if (t.id.equals(p.torneioId) && t.partidas.contains(p)) return t;
        Torneio t = jogo.competicao(p.torneioId);
        return t != null ? t : jogo.mundo().torneioAnterior(p.torneioId);
    }

    // ------------------------------------------------------------------ títulos (tela de campeão)
    /** Títulos dos técnicos humanos ainda não celebrados, na ordem em que saíram. */
    private final List<Conquista> conquistas = new ArrayList<>();

    /** Os times de cada técnico humano agora (clube, seleção, time da Kings). */
    Map<Tecnico, java.util.Set<String>> timesDosTecnicos() {
        Map<Tecnico, java.util.Set<String>> r = new java.util.IdentityHashMap<>();
        for (Tecnico t : jogo.mundo().todosTecnicos()) {
            java.util.Set<String> s = new java.util.HashSet<>();
            if (t.clubeId != null) s.add(t.clubeId);
            if (t.selecaoPais != null) s.add(br.futebas.mundo.Selecao.id(t.selecaoPais));
            if (t.kingsTimeId != null) s.add(t.kingsTimeId);
            r.put(t, s);
        }
        return r;
    }

    /**
     * Os títulos que saíram desde {@code antes} (índice na lista de títulos da história) e foram de um
     * time de técnico humano (o time de antes ou de depois da semana). Sub-20 não conta: é o clube, mas
     * não é o time do técnico.
     */
    void coletarConquistas(int antes, Map<Tecnico, java.util.Set<String>> timesAntes) {
        List<Historia.Titulo> ts = jogo.mundo().historia.titulos;
        if (ts.size() <= antes) return;
        Map<Tecnico, java.util.Set<String>> agora = timesDosTecnicos();
        for (int i = Math.max(0, antes); i < ts.size(); i++) {
            Historia.Titulo tit = ts.get(i);
            if (tit.campeaoId == null || "sub20".equals(tit.ambito) || tit.competicaoId != null && tit.competicaoId.contains("-sub20")) continue;
            for (Tecnico t : jogo.mundo().todosTecnicos()) {
                java.util.Set<String> a = timesAntes.get(t), d = agora.get(t);
                if (!(a != null && a.contains(tit.campeaoId) || d != null && d.contains(tit.campeaoId))) continue;
                Conquista c = new Conquista(t.nome, tit.campeaoId, tit.campeao, tit.competicaoId, tit.competicao, tit.ambito, tit.pais, tit.ano);
                synchronized (conquistas) {
                    if (!conquistas.contains(c)) conquistas.add(c);
                }
            }
        }
    }

    /** Há título a celebrar? */
    public boolean temConquistas() {
        synchronized (conquistas) {
            return !conquistas.isEmpty();
        }
    }

    /** Os títulos ainda não celebrados (de todos os técnicos, em ordem), tirando-os da fila. */
    public List<Conquista> tirarConquistas() {
        synchronized (conquistas) {
            List<Conquista> l = new ArrayList<>(conquistas);
            conquistas.clear();
            return l;
        }
    }

    // ------------------------------------------------------------------ multi-treinador
    /** Marcas do começo da temporada de cada técnico (multi): {v/e/d, clube, time da Kings}. */
    private final Map<Tecnico, Object[]> marcas = new java.util.IdentityHashMap<>();
    /** Resumo do fim de temporada de cada técnico (multi), à espera da vez dele. */
    private final Map<Tecnico, ResumoTemporada> fimPendente = new java.util.IdentityHashMap<>();

    private void usarMarcas(Tecnico t) {
        Object[] k = marcas.get(t);
        if (k == null) return;
        tecnicoAntes = (int[]) k[0];
        clubeAntes = (String) k[1];
        kingsAntes = (String) k[2];
    }

    /** O resumo da temporada que o técnico ainda não viu (e o tira da fila); null se não há. */
    public ResumoTemporada tirarFimPendente(Tecnico t) {
        return t == null ? null : fimPendente.remove(t);
    }

    /** O técnico tem jogo (clube ou Kings) marcado nesta semana? */
    public boolean temJogoNaSemana(Tecnico t) {
        Mundo m = jogo.mundo();
        int sa = m.semanaAbsoluta();
        if (t.clubeId != null) {
            for (Partida p : jogo.calendarioDoClube(t.clubeId)) if (!p.jogada && p.sa == sa) return true;
            return false;
        }
        if (t.kingsTimeId == null) return false;
        try {
            for (Torneio x : jogo.competicoesKings())
                for (Partida p : x.partidas) if (!p.jogada && p.sa == sa && (t.kingsTimeId.equals(p.mandante) || t.kingsTimeId.equals(p.visitante))) return true;
        } catch (RuntimeException e) {
            return false;
        }
        return false;
    }

    /** Algum técnico humano joga nesta semana? */
    public boolean algumJogoNaSemana() {
        for (Tecnico t : jogo.mundo().todosTecnicos()) if (temJogoNaSemana(t)) return true;
        return false;
    }

    private Map<Tecnico, String> empregos() {
        Map<Tecnico, String> e = new java.util.IdentityHashMap<>();
        for (Tecnico t : jogo.mundo().todosTecnicos()) e.put(t, t.clubeId != null ? t.clubeId : t.kingsTimeId);
        return e;
    }

    private boolean alguemPerdeuEmprego(Map<Tecnico, String> antes) {
        for (Map.Entry<Tecnico, String> x : antes.entrySet())
            if (x.getValue() != null && x.getKey().clubeId == null && x.getKey().kingsTimeId == null) return true;
        return false;
    }

    /**
     * Multi-treinador: corre as semanas em que nenhum técnico humano joga, até a semana da próxima
     * rodada (alguém joga). Para antes no fim da temporada ou quando um técnico perde o emprego.
     * Devolve a última semana corrida (null se já estava na semana de jogo).
     */
    public Rodada avancarAteRodadaMulti() {
        Rodada ult = null;
        int limite = 60;
        while (limite-- > 0 && !algumJogoNaSemana()) {
            boolean empregado = false;
            for (Tecnico t : jogo.mundo().todosTecnicos()) if (t.clubeId != null || t.kingsTimeId != null) empregado = true;
            if (!empregado && ult != null) break; // todos sem emprego: uma semana por rodada
            Map<Tecnico, String> antes = empregos();
            ult = semana(null, jogo.caixaDeEntrada().size());
            if (ult.fimDeTemporada() != null || alguemPerdeuEmprego(antes)) break;
        }
        return ult;
    }

    /**
     * Multi-treinador: joga a semana atual (todas as competições), com os jogos ao vivo dos técnicos
     * humanos juntos na tela dividida ({@code aoVivo}).
     */
    public Rodada jogarSemanaMulti(br.futebas.partida.AoVivoMulti aoVivo) {
        jogo.definirAoVivoMulti(aoVivo);
        try {
            return semana(null, jogo.caixaDeEntrada().size());
        } finally {
            jogo.definirAoVivoMulti(null);
        }
    }

    // ------------------------------------------------------------------ fim de temporada
    private ResumoTemporada resumo(int ano) {
        Mundo m = jogo.mundo();
        List<Historia.Titulo> campeoes = new ArrayList<>();
        for (Historia.Titulo t : m.historia.titulos) if (t.ano == ano) campeoes.add(t); // inclui a Kings (coluna própria na tela)
        List<Movimento> acessos = new ArrayList<>(), rebaix = new ArrayList<>();
        if (divisoesAntes != null) {
            for (Clube c : m.clubes.values()) {
                if (c.virtual) continue;
                Clube meu = jogo.meuClube();
                if (meu != null && !meu.pais.equals(c.pais)) continue;
                int antes = divisoesAntes.getOrDefault(c.id, 0), depois = c.divisao;
                if (antes == depois) continue;
                if (depois > 0 && (antes == 0 || depois < antes))
                    acessos.add(new Movimento(c.id, c.nome, c.pais, antes, depois, "sobe para a " + Textos.divisao(c.pais, depois)));
                else if (antes > 0 && (depois == 0 || depois > antes))
                    rebaix.add(new Movimento(c.id, c.nome, c.pais, antes, depois,
                            depois == 0 ? "perde a vaga na " + Textos.divisao(c.pais, antes) : "cai para a " + Textos.divisao(c.pais, depois)));
            }
        }
        List<Historia.Premio> premios = new ArrayList<>();
        for (Historia.Premio p : m.historia.premios) if (p.ano == ano) premios.add(p);
        Tecnico t = m.tecnico;
        int v = 0, e = 0, d = 0;
        List<String> titulos = new ArrayList<>();
        List<LinhaCampanha> campanha = new ArrayList<>();
        String clube = null, clubeId = null;
        double conf = 0;
        String veredito = null;
        if (t != null) {
            v = t.vitorias - tecnicoAntes[0];
            e = t.empates - tecnicoAntes[1];
            d = t.derrotas - tecnicoAntes[2];
            for (String s : t.titulos) if (s.startsWith(ano + " ")) titulos.add(s.substring(5));
            String cid = clubeAntes;
            Clube atual = jogo.meuClube();
            if (atual != null && !atual.id.equals(cid)) cid = atual.id;
            if (cid != null) {
                Clube c = m.clube(cid);
                clube = c.nome;
                clubeId = c.id;
                for (Torneio tt : m.torneiosAnteriores) {
                    if (!tt.participantes.contains(cid)) continue;
                    String res;
                    if (cid.equals(tt.campeao)) res = "Campeão";
                    else if (cid.equals(tt.vice)) res = "Vice-campeão";
                    else res = campanha(tt, cid);
                    campanha.add(new LinhaCampanha(tt.id, tt.ambito, tt.nome, res));
                }
                if (atual != null) {
                    conf = atual.confiancaDiretoria;
                    veredito = veredito(conf);
                    if (!titulos.isEmpty() && conf < 55) veredito = "A diretoria comemora o título, mas espera mais regularidade na próxima temporada.";
                    if (atual.vereditoDiretoria != null && atual.id.equals(clubeAntes)) veredito += " (Balanço: " + atual.vereditoDiretoria + ".)";
                }
            }
            String kid = t.kingsTimeId != null ? t.kingsTimeId : kingsAntes;
            if (cid == null && kid != null && jogo.timeKings(kid) != null) {
                // carreira da Kings: campanha nas competições da Kings do ano e o presidente no lugar da diretoria
                var tk = jogo.timeKings(kid);
                clube = tk.nome;
                clubeId = tk.id;
                List<Torneio> ts = new ArrayList<>();
                for (var liga : jogo.ligasKings()) for (Torneio tt : liga.competicoes) if (tt.ano == ano) ts.add(tt);
                for (Torneio tt : m.kingsMundiais) if (tt.ano == ano) ts.add(tt);
                int pos = 0, n = 0;
                for (Torneio tt : ts) {
                    if (!tt.participantes.contains(kid)) continue;
                    String res;
                    if (kid.equals(tt.campeao)) res = "Campeão";
                    else if (kid.equals(tt.vice)) res = "Vice-campeão";
                    else res = campanha(tt, kid);
                    campanha.add(new LinhaCampanha(tt.id, tt.ambito, tt.nome, res));
                    for (Partida p : tt.partidas) {
                        if (!p.jogada || p.golsMandante == null || p.golsVisitante == null || !(kid.equals(p.mandante) || kid.equals(p.visitante))) continue;
                        boolean casa = kid.equals(p.mandante);
                        int pro = casa ? p.golsMandante : p.golsVisitante, contra = casa ? p.golsVisitante : p.golsMandante;
                        int penPro = p.penaltisMandante == null ? 0 : casa ? p.penaltisMandante : p.penaltisVisitante;
                        int penContra = p.penaltisMandante == null ? 0 : casa ? p.penaltisVisitante : p.penaltisMandante;
                        boolean venceu = pro > contra || pro == contra && penPro > penContra;
                        if (venceu) v++;
                        else d++;
                    }
                    if (tt.id.endsWith("-split1") || tt.id.endsWith("-split2")) {
                        int pp = tt.posicaoFinal(kid);
                        if (pp < 999) {
                            pos = pp;
                            n = tt.participantes.size();
                        }
                    }
                }
                e = 0;
                if (t.kingsTimeId != null) {
                    conf = n < 2 ? 60 : 85 - 65.0 * (pos - 1) / (n - 1);
                    if (!titulos.isEmpty()) conf = Math.max(conf, 75);
                    veredito = conf >= 75 ? "O presidente está muito satisfeito com o seu trabalho no " + tk.nome + "."
                            : conf >= 55 ? "O presidente aprova a temporada e mantém você no comando."
                            : conf >= 35 ? "O presidente espera mais na próxima temporada."
                            : "O presidente está insatisfeito: terminar em último pode custar o cargo.";
                } else veredito = "O presidente do " + tk.nome + " decidiu trocar o técnico. Aguarde convites na caixa de entrada.";
            } else if (atual == null) veredito = "Você está sem clube. Aguarde convites na caixa de entrada.";
        }
        List<Historia.Evento> manchetes = new ArrayList<>();
        List<Historia.Evento> lt = m.historia.linhaDoTempo;
        for (int i = lt.size() - 1; i >= 0 && manchetes.size() < 12; i--) if (lt.get(i).ano == ano && !"titulo".equals(lt.get(i).tipo)) manchetes.add(lt.get(i));
        acessos.sort(java.util.Comparator.comparingInt((Movimento x) -> x.para()).thenComparing(Movimento::nome));
        rebaix.sort(java.util.Comparator.comparingInt((Movimento x) -> x.de()).thenComparing(Movimento::nome));
        return new ResumoTemporada(ano, campeoes, acessos, rebaix, premios, clubeId, clube, v, e, d, campanha, titulos, veredito, conf, manchetes);
    }

    /** Até onde o clube chegou: posição nos pontos corridos, ou a fase em que caiu. */
    static String campanha(Torneio t, String cid) {
        boolean temMataMata = false;
        for (FaseExec f : t.fases) if (f.mataMata()) temMataMata = true;
        if (!temMataMata && t.fases.size() <= 1) {
            int pos = t.posicaoFinal(cid);
            return pos < 999 ? Textos.ordinal(pos) + " lugar" : "Participou";
        }
        FaseExec ultima = null;
        for (FaseExec f : t.fases) {
            boolean esteve = f.clubes.contains(cid);
            for (var c : f.confrontos) if (cid.equals(c.a) || cid.equals(c.b)) esteve = true;
            if (esteve) ultima = f;
        }
        if (ultima == null) return "Participou";
        if (ultima.mataMata()) {
            for (var c : ultima.confrontos) {
                if (cid.equals(c.perdedor)) {
                    int mesmos = 0;
                    for (var x : ultima.confrontos) if (x.rodada == c.rodada) mesmos++;
                    String fase = mesmos == 1 ? "na final" : mesmos == 2 ? "na semifinal" : mesmos == 4 ? "nas quartas de final"
                            : mesmos == 8 ? "nas oitavas de final" : null;
                    return fase != null ? "Eliminado " + fase : "Eliminado: " + (ultima.nome == null ? "mata-mata" : ultima.nome);
                }
            }
        }
        return "Eliminado: " + (ultima.nome == null ? "fase " + (ultima.indice + 1) : ultima.nome);
    }

    public static String veredito(double confianca) {
        if (confianca >= 75) return "A diretoria está muito satisfeita com o seu trabalho e confia plenamente no projeto.";
        if (confianca >= 55) return "A diretoria aprova a temporada e mantém a confiança no seu trabalho.";
        if (confianca >= 35) return "A diretoria espera mais. A próxima temporada precisa ser melhor.";
        return "A diretoria está insatisfeita. O seu cargo está ameaçado.";
    }

    // ------------------------------------------------------------------ só assistir
    /** Manchetes novas desde a última chamada (linha do tempo do mundo). */
    public List<Historia.Evento> manchetesNovas() {
        List<Historia.Evento> lt = jogo.historia().linhaDoTempo;
        List<Historia.Evento> l = new ArrayList<>(lt.subList(Math.min(eventosVistos, lt.size()), lt.size()));
        eventosVistos = lt.size();
        return l;
    }

    /** Avança uma temporada inteira (só assistir ou técnico sem clube). */
    public ResumoTemporada avancarTemporada() {
        int ano = jogo.ano();
        ResumoTemporada r = null;
        while (jogo.ano() == ano) {
            Rodada x = avancarSemana();
            if (x.fimDeTemporada() != null) r = x.fimDeTemporada();
        }
        return r;
    }
}
