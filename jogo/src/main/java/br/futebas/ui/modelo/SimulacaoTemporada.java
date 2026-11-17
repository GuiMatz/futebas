package br.futebas.ui.modelo;

import br.futebas.competicao.LinhaTabela;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Mensagem;
import br.futebas.ui.Sessao;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "Simular temporada" (requisito: "simule rápido a temporada, mas não instantâneo"): o
 * auxiliar comanda todos os jogos ({@code Tecnico.auxiliarTudo}) e o calendário anda UMA semana por
 * passo — a tela chama {@link #passo()} em segundo plano, mostra o progresso e chama o próximo depois
 * de um respiro. Para sozinho no que pede o técnico (proposta de emprego, demissão, draft da Kings,
 * proposta/convite na caixa) e no fim da temporada. Sem tela (os testes usam a mesma classe).
 */
public final class SimulacaoTemporada {
    private static final Set<String> PEDEM_ATENCAO = Set.of("proposta", "selecao");

    /** Uma semana simulada: a rodada, o motivo de parar (null = continua) e um resumo para a tela. */
    public record Semana(Fluxo.Rodada rodada, String parada, boolean fimDeTemporada, List<String> resultados, String posicao) {}

    private final Sessao s;
    private final int ano, semanaInicial;
    private boolean ativa;

    public SimulacaoTemporada(Sessao s) {
        this.s = s;
        this.ano = s.jogo.ano();
        this.semanaInicial = s.jogo.semana();
    }

    /** Liga o auxiliar para todos os jogos. */
    public void iniciar() {
        if (s.jogo.mundo().tecnico != null) s.jogo.mundo().tecnico.auxiliarTudo = true;
        ativa = true;
    }

    /** Desliga: as delegações do técnico (competições e jogos avulsos) voltam a valer como antes. */
    public void terminar() {
        if (s.jogo.mundo().tecnico != null) s.jogo.mundo().tecnico.auxiliarTudo = false;
        ativa = false;
    }

    public boolean ativa() { return ativa; }

    /** Semanas da temporada (para a barra): da semana em que começou até a 52. */
    public int total() { return Math.max(1, 52 - semanaInicial + 1); }

    /** Quantas já foram (para a barra). */
    public int feitas() { return s.jogo.ano() != ano ? total() : Math.max(0, s.jogo.semana() - semanaInicial); }

    /** A próxima semana fecha a temporada (a tela mostra "Encerrando a temporada…")? */
    public boolean proximaEhVirada() { return s.jogo.semana() >= 52; }

    /** Simula uma semana (rodar em segundo plano, com a trava do mundo). */
    public Semana passo() {
        int propostas = s.jogo.propostasDeEmprego().size();
        boolean tinhaDraft = s.jogo.ligaDoDraft() != null;
        Fluxo.Rodada r = s.fluxo.avancarSemana();
        String parada = null;
        if (r.fimDeTemporada() != null) parada = "Fim da temporada";
        else if (r.demitido()) parada = "Você foi demitido";
        else if (s.jogo.propostasDeEmprego().size() > propostas) parada = "Proposta de emprego";
        else if (!tinhaDraft && s.jogo.ligaDoDraft() != null) parada = "Draft da Kings: a sua escolha";
        else if (s.jogo.meuClube() == null && !s.fluxo.soKings()
                && s.jogo.vagasDeEmprego().stream().anyMatch(v -> !"Fora do seu alcance".equals(v.interesse()))) parada = "Vaga ao seu alcance";
        else {
            for (Mensagem m : r.novasMensagens()) if (PEDEM_ATENCAO.contains(m.tipo)) parada = m.titulo;
        }
        List<String> res = new ArrayList<>();
        String meu = meuId();
        for (Partida p : r.meusJogos()) {
            if (p.golsMandante == null) continue;
            res.add(s.jogo.mundo().nomeParticipante(p.mandante) + " " + p.golsMandante + " x " + p.golsVisitante + " "
                    + s.jogo.mundo().nomeParticipante(p.visitante));
        }
        return new Semana(r, parada, r.fimDeTemporada() != null, res, posicao(meu));
    }

    private String meuId() {
        if (s.jogo.meuClube() != null) return s.jogo.meuClube().id;
        var tk = s.jogo.meuTimeKings();
        return tk == null ? null : tk.id;
    }

    /** "3º no Campeonato Brasileiro" (liga do clube ou a competição da Kings em andamento). */
    private String posicao(String id) {
        if (id == null) return null;
        try {
            List<Torneio> ts = new ArrayList<>(s.jogo.meuClube() != null ? s.jogo.competicoesDoAno() : s.jogo.competicoesKings());
            for (Torneio t : ts) {
                boolean liga = "nacional".equals(t.ambito) && t.divisao != null || "kings".equals(t.ambito);
                if (!liga || !t.participantes.contains(id) || t.encerrado && s.jogo.meuClube() == null) continue;
                List<LinhaTabela> ls = s.jogo.tabela(t.id);
                for (int i = 0; i < ls.size(); i++) if (ls.get(i).clubeId.equals(id)) return (i + 1) + "º no " + Textos.curto(t.nome);
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }
}
