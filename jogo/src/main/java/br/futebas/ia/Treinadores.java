package br.futebas.ia;

import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Populador;
import br.futebas.mundo.Rng;
import br.futebas.mundo.Selecao;
import br.futebas.mundo.Treinador;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mercado de técnicos da IA: cada clube começa com o técnico real do banco (avaliação pelo
 * nível do clube), as seleções com o técnico real delas; demitidos voltam ao mercado; clubes sem
 * técnico contratam do mercado (ou de fora, um técnico gerado); ex-jogadores entram no mercado
 * ao se aposentar; técnicos velhos ou parados há muito tempo se aposentam e saem da lista.
 */
public final class Treinadores {
    /** Idade em que o técnico se aposenta; anos parado até sair do mercado. */
    static final int IDADE_APOSENTADORIA = 72, ANOS_PARADO = 5;

    private final Mundo m;
    private final BancoMundo db;
    private final Rng rng;
    private final Populador populador;

    public Treinadores(Mundo m, BancoMundo db, Populador populador) {
        this.m = m;
        this.db = db;
        this.rng = m.rng;
        this.populador = populador;
    }

    // ================================================================== criação
    public void criar() {
        Map<String, Db.Tecnico> porClube = new HashMap<>(), porSelecao = new HashMap<>();
        for (Db.Tecnico t : db.tecnicos) {
            if (t.clubeId() != null) porClube.putIfAbsent(t.clubeId(), t);
            if (t.selecao() != null) porSelecao.putIfAbsent(t.selecao().toUpperCase(), t);
        }
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe) continue;
            Db.Tecnico dt = porClube.get(c.id);
            Treinador t = dt != null ? real(dt) : gerado(c.pais, c.nivel() - 1);
            t.avaliacao = Math.max(30, Math.min(95, c.nivel() + rng.gauss(0, 3)));
            t.reputacao = Math.max(5, Math.min(100, c.reputacao * 0.8 + rng.gauss(0, 5)));
            empregar(t, c);
        }
        for (Selecao s : m.selecoes.values()) {
            Db.Tecnico dt = porSelecao.get(s.pais);
            Treinador t;
            if (dt != null) t = real(dt);
            else if (s.tecnico != null && !s.tecnico.isBlank()) {
                t = novo();
                t.nome = s.tecnico;
                t.pais = s.pais;
                t.origem = "real";
                t.anoNasc = m.ano - rng.entre(42, 64);
            } else continue;
            t.avaliacao = Math.max(40, Math.min(95, 50 + (s.elo - 1500) / 20.0 + rng.gauss(0, 3)));
            t.reputacao = Math.max(20, Math.min(100, 40 + (s.elo - 1500) / 15.0));
            t.selecaoPais = s.pais;
            t.desde = m.ano;
            t.ultimoAnoEmpregado = m.ano;
            s.treinadorId = t.id;
            s.tecnico = t.nome;
        }
    }

    private Treinador novo() {
        Treinador t = new Treinador();
        t.id = m.proximoTreinador++;
        m.treinadores.add(t);
        m.reindexarTreinadores();
        return t;
    }

    private Treinador real(Db.Tecnico dt) {
        Treinador t = novo();
        t.dbId = dt.id();
        t.nome = dt.nome();
        t.pais = dt.pais();
        t.origem = "real";
        t.anoNasc = m.ano - rng.entre(38, 66);
        return t;
    }

    /** Técnico gerado (nome real do país) com a avaliação pedida. */
    Treinador gerado(String pais, double avaliacao) {
        Treinador t = novo();
        String nome = populador.nomeGerado(pais);
        t.nome = nome == null ? "Técnico " + t.id : nome;
        t.pais = pais;
        t.origem = "gerado";
        t.anoNasc = m.ano - rng.entre(36, 58);
        t.avaliacao = Math.max(25, Math.min(90, avaliacao + rng.gauss(0, 2)));
        t.reputacao = Math.max(5, t.avaliacao - 40);
        t.ultimoAnoEmpregado = m.ano;
        return t;
    }

    private void empregar(Treinador t, Clube c) {
        t.clubeId = c.id;
        t.selecaoPais = null;
        t.desde = m.ano;
        t.ultimoAnoEmpregado = m.ano;
        c.treinadorId = t.id;
    }

    // ================================================================== mercado
    /** O clube demite o técnico (volta ao mercado). */
    public void demitir(Clube c) {
        Treinador t = m.treinador(c.treinadorId);
        c.treinadorId = null;
        if (t == null) return;
        sair(t, c.nome);
        t.demissoes++;
        t.reputacao = Math.max(1, t.reputacao - 3);
    }

    private void sair(Treinador t, String onde) {
        t.historico.add((t.desde == m.ano ? String.valueOf(m.ano) : t.desde + "–" + m.ano) + " " + onde);
        t.clubeId = null;
        t.selecaoPais = null;
        t.ultimoAnoEmpregado = m.ano;
    }

    /**
     * Contrata para o clube o melhor técnico livre que aceita (avaliação até o nível do clube + 6,
     * preferindo os do país); sem ninguém, um técnico gerado.
     */
    public Treinador contratar(Clube c) {
        return contratar(c, null);
    }

    /** Como {@link #contratar(Clube)}, sem recontratar {@code excluir} (o técnico que o clube acabou de demitir). */
    public Treinador contratar(Clube c, Treinador excluir) {
        double nivel = c.nivel();
        Treinador melhor = null;
        double mv = -1e9;
        for (Treinador t : m.treinadores) {
            if (!t.livre() || t == excluir || t.avaliacao > nivel + 6) continue;
            double v = t.avaliacao + (c.pais != null && c.pais.equals(t.pais) ? 3 : 0) + t.reputacao * 0.02 - Math.max(0, nivel - 10 - t.avaliacao);
            if (v > mv) {
                mv = v;
                melhor = t;
            }
        }
        if (melhor == null || melhor.avaliacao < nivel - 12) melhor = gerado(c.pais, nivel - 2);
        empregar(melhor, c);
        return melhor;
    }

    /** Semana: clubes da IA sem técnico contratam; clube do humano não tem técnico da IA. */
    public void semana() {
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe) continue;
            if (c.humano) {
                if (c.treinadorId != null) demitir(c);
            } else if (c.treinadorId == null) {
                contratar(c);
            }
        }
    }

    /** Resultado de um jogo para o técnico do clube. */
    public void registrarJogo(Clube c, int pro, int contra) {
        Treinador t = m.treinador(c.treinadorId);
        if (t == null) return;
        t.jogos++;
        if (pro > contra) t.vitorias++;
        else if (pro == contra) t.empates++;
        else t.derrotas++;
    }

    public void titulo(String clubeId, String competicao) {
        Clube c = m.clube(clubeId);
        Treinador t = c == null ? null : m.treinador(c.treinadorId);
        if (t == null) return;
        t.titulos.add(competicao);
        t.reputacao = Math.min(100, t.reputacao + 4);
        t.avaliacao = Math.min(95, t.avaliacao + 0.5);
    }

    /**
     * Ao se aposentar, o jogador pode virar técnico: mais chance com fama, experiência e estrela.
     * Devolve o técnico novo (no mercado) ou null.
     */
    public Treinador talvezVirarTecnico(Jogador j) {
        int idade = j.idade(m.ano);
        if (idade < 30 || j.amador()) return null;
        double p = 0.02 + 0.25 * j.fama / 100.0 + (j.jogosCarreira >= 300 ? 0.08 : 0) + (j.estrela ? 0.10 : 0);
        if (!rng.chance(Math.min(0.6, p))) return null;
        Treinador t = novo();
        t.nome = j.nomeExibicao();
        t.pais = j.pais;
        t.origem = "ex_jogador";
        t.jogadorId = j.id;
        t.anoNasc = j.anoNasc;
        double max = j.forca;
        for (var pa : j.carreira) max = Math.max(max, pa.forca);
        t.avaliacao = Math.max(30, Math.min(90, max * 0.8 + j.fama * 0.1 + rng.gauss(0, 4)));
        t.reputacao = Math.max(5, Math.min(80, j.fama * 0.6));
        t.ultimoAnoEmpregado = m.ano;
        j.virouTecnico = true;
        return t;
    }

    /** Fim do ano: técnicos velhos ou parados há muito se aposentam e saem da lista. */
    public int fimDoAno() {
        List<Treinador> ficam = new ArrayList<>(m.treinadores.size());
        int saem = 0;
        for (Treinador t : m.treinadores) {
            boolean sai = t.livre() && (t.idade(m.ano) >= IDADE_APOSENTADORIA || m.ano - t.ultimoAnoEmpregado > ANOS_PARADO);
            if (sai) saem++;
            else ficam.add(t);
        }
        if (saem > 0) {
            m.treinadores = ficam;
            m.reindexarTreinadores();
        }
        return saem;
    }
}
