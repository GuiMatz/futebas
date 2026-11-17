package br.futebas.ui;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.partida.AoVivo;
import br.futebas.ui.modelo.Fluxo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bugs corrigidos: "dizia que eu ia enfrentar um time num dia e tudo mudou para outro" e "delegar
 * competição não está funcionando". Uma temporada inteira (clube e carreira só da Kings): o jogo
 * anunciado antes de avançar é o que é jogado; competição delegada nunca abre o jogo ao vivo.
 */
class ProximoJogoTest {

    static String chave(Partida p) {
        return p == null ? "null" : p.torneioId + "#" + p.id + " " + p.mandante + " x " + p.visitante + " " + p.data();
    }

    /** Grava os jogos que passaram pela tela ao vivo (um Controle por jogo) e faz o papel da tela no 2º jogo da semana. */
    static final class Gravador implements AoVivo {
        final Map<Controle, String> jogos = new IdentityHashMap<>();
        Sessao s;
        br.futebas.ui.modelo.AntesDoJogo antes;
        Set<String> comPassos;

        @Override
        public void minuto(Controle c) {
            jogos.computeIfAbsent(c, x -> x.time(0).clubeId + " x " + x.time(1).clubeId);
        }

        @Override
        public void antesDoProximoJogo(String mandanteId, String mandante, String visitanteId, String visitante) {
            if (s != null) passos(s, antes, comPassos); // a tela voltou à central: pré-jogo e escalação
        }
    }

    /** O que o App faz ao JOGAR: os passos até a partida; grava o jogo que teve pré-jogo E escalação. */
    static void passos(Sessao s, br.futebas.ui.modelo.AntesDoJogo antes, Set<String> comPassos) {
        Partida p = br.futebas.ui.modelo.AntesDoJogo.partida(s);
        List<br.futebas.ui.modelo.AntesDoJogo.Passo> l = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            br.futebas.ui.modelo.AntesDoJogo.Passo x = antes.proximo(s, true);
            if (x == br.futebas.ui.modelo.AntesDoJogo.Passo.PARTIDA) break;
            l.add(x);
        }
        if (p != null && l.equals(List.of(br.futebas.ui.modelo.AntesDoJogo.Passo.PRE_JOGO, br.futebas.ui.modelo.AntesDoJogo.Passo.ESCALACAO)))
            comPassos.add(br.futebas.ui.modelo.AntesDoJogo.chave(p));
    }

    @Test
    void clubeOAnunciadoEOJogadoEDelegadoSemTelaAoVivo() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 61, FluxoTest.clubeSerieA(), "T");
        Sessao s = new Sessao(j, "proximo-jogo");
        String meu = j.meuClube().id;
        // delega a copa nacional (se houver) e deixa a liga com o técnico
        String delegada = null;
        for (Torneio t : j.competicoesDoAno()) {
            if (t.participantes.contains(meu) && !"nacional".equals(t.ambito) && !"amistoso".equals(t.ambito)) {
                delegada = t.id;
                break;
            }
        }
        if (delegada != null) j.delegarAoAuxiliar(delegada, null, true);
        List<String> erros = new ArrayList<>();
        int jogos = 0, delegados = 0, ano = j.ano();
        br.futebas.ui.modelo.AntesDoJogo antes = new br.futebas.ui.modelo.AntesDoJogo();
        Set<String> comPassos = new HashSet<>();
        for (int i = 0; i < 80 && j.ano() == ano && j.meuClube() != null; i++) {
            // semanas sem jogo passam sem os passos (como o App: o calendário corre até a semana do jogo)
            while (j.meuClube() != null && !s.fluxo.temJogoNaSemana() && j.ano() == ano) s.fluxo.avancarSemana();
            if (j.ano() != ano || j.meuClube() == null) break;
            passos(s, antes, comPassos);
            Partida anunciado = s.fluxo.proximoJogo();
            Gravador g = new Gravador();
            g.s = s;
            g.antes = antes;
            g.comPassos = comPassos;
            Fluxo.Rodada r = s.fluxo.jogarProximaRodada(g);
            if (r.fimDeTemporada() != null || r.demitido()) break;
            if (r.meusJogos().isEmpty()) continue;
            Partida primeiro = r.meusJogos().get(0);
            if (anunciado != primeiro) erros.add("anunciado " + chave(anunciado) + " / jogado " + chave(primeiro));
            // na ordem das datas
            for (int k = 1; k < r.meusJogos().size(); k++)
                if (r.meusJogos().get(k).dia < r.meusJogos().get(k - 1).dia) erros.add("fora de ordem: " + r.meusJogos());
            Set<String> aoVivo = new HashSet<>(g.jogos.values());
            for (Partida p : r.meusJogos()) {
                jogos++;
                boolean del = j.delegadoAoAuxiliar(p.torneioId, p.id);
                if (del) delegados++;
                boolean vivo = aoVivo.contains(p.mandante + " x " + p.visitante);
                if (del && vivo) erros.add("delegado com tela ao vivo: " + chave(p));
                if (!del && !vivo) erros.add("jogo do técnico sem tela ao vivo: " + chave(p));
                if (!del && !comPassos.contains(br.futebas.ui.modelo.AntesDoJogo.chave(p)))
                    erros.add("jogo sem pré-jogo e escalação antes: " + chave(p));
            }
        }
        assertTrue(jogos > 20, "jogou a temporada: " + jogos);
        if (delegada != null) assertTrue(delegados > 0, "a competição delegada teve jogos: " + delegada);
        assertTrue(erros.isEmpty(), erros.size() + " problemas:\n" + String.join("\n", erros.subList(0, Math.min(15, erros.size()))));
    }

    @Test
    void kingsOAnunciadoEOJogado() {
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 17, null, "kb-2", "Técnico");
        Sessao s = new Sessao(j, "proximo-jogo-kings");
        s.fluxo.avancarSemana();
        String meu = j.mundo().tecnico.kingsTimeId;
        br.futebas.ui.modelo.AntesDoJogo antesK = new br.futebas.ui.modelo.AntesDoJogo();
        Set<String> comPassosK = new HashSet<>();
        List<String> erros = new ArrayList<>();
        int jogos = 0, ano = j.ano();
        for (int i = 0; i < 80 && j.ano() == ano && s.fluxo.soKings(); i++) {
            Partida anunciado = s.fluxo.proximoJogoKings();
            if (anunciado == null) break;
            passos(s, antesK, comPassosK);
            Set<Partida> antes = new HashSet<>();
            for (Torneio t : j.competicoesKings()) for (Partida p : t.partidas) if (!p.jogada && p.envolve(meu)) antes.add(p);
            Fluxo.Rodada r = s.fluxo.jogarProximaRodada(null);
            List<Partida> jogados = new ArrayList<>();
            for (Partida p : antes) if (p.jogada) jogados.add(p);
            jogados.sort(java.util.Comparator.comparingLong((Partida p) -> p.dia).thenComparingInt(p -> p.data));
            if (jogados.isEmpty()) {
                // draft da Kings pendente: a liga espera o técnico escolher e o jogo passa (com a data nova) para a semana seguinte
                boolean draft = j.ligaDoDraft() != null || anunciado.sa > j.mundo().semanaAbsoluta() - 1;
                if (r.fimDeTemporada() == null && !draft) erros.add("semana sem jogo depois de anunciar " + chave(anunciado));
                continue;
            }
            jogos += jogados.size();
            if (jogados.get(0) != anunciado) erros.add("anunciado " + chave(anunciado) + " / jogado " + chave(jogados.get(0)));
            if (!comPassosK.contains(br.futebas.ui.modelo.AntesDoJogo.chave(jogados.get(0))))
                erros.add("jogo da Kings sem pré-jogo e escalação antes: " + chave(jogados.get(0)));
            if (r.fimDeTemporada() != null) break;
        }
        assertTrue(jogos > 5, "jogou a Kings: " + jogos);
        assertTrue(erros.isEmpty(), erros.size() + " problemas:\n" + String.join("\n", erros.subList(0, Math.min(15, erros.size()))));
    }

    /** Delegar: competição pelo id base (vale no ano seguinte), partida avulsa só naquele ano, exceção dentro da delegada. */
    @Test
    void delegacaoPorCompeticaoPartidaEExcecao() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 62, FluxoTest.clubeSerieA(), "T");
        br.futebas.mundo.Tecnico t = j.mundo().tecnico;
        int ano = j.ano();
        j.delegarAoAuxiliar("bra-1", null, true);
        assertTrue(j.competicaoDelegada("bra-1"));
        assertTrue(t.delegado("bra-1", 5, ano) && t.delegado("bra-1", 5, ano + 1), "a competição vale nas próximas edições");
        // desmarcar um jogo da competição delegada: o técnico comanda esse
        j.delegarAoAuxiliar("bra-1", 5, false);
        assertFalse(j.delegadoAoAuxiliar("bra-1", 5));
        assertTrue(j.delegadoAoAuxiliar("bra-1", 6));
        assertTrue(j.competicaoDelegada("bra-1"), "a exceção não desmarca a competição");
        // desfazer a competição limpa as exceções
        j.delegarAoAuxiliar("bra-1", null, false);
        assertFalse(j.delegadoAoAuxiliar("bra-1", 6));
        // partida avulsa: só a deste ano (o id da partida se repete todo ano)
        j.delegarAoAuxiliar("bra-copa", 3, true);
        assertTrue(j.delegadoAoAuxiliar("bra-copa", 3));
        assertFalse(t.delegado("bra-copa", 3, ano + 1), "a partida avulsa não passa para o ano seguinte");
    }
}
