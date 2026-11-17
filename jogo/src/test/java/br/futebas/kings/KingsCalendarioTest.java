package br.futebas.kings;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug corrigido (12/11/2026): "na Kings tá indo dois jogos diretos seguidos". Cada time da Kings
 * joga no máximo uma vez por semana (Split, Kings Cup e Copas do Mundo juntos). Exceção
 * documentada: a Kings League IOS (20 times) não cabe no ano com uma rodada por semana.
 */
class KingsCalendarioTest {

    @Test
    void umJogoPorTimePorSemana() {
        Jogo jogo = Jogo.novoJogo(Fixture.banco(), 61, "bra-1-02", "T");
        for (int ano = 2026; ano <= 2027; ano++) {
            while (jogo.ano() == ano) {
                if (jogo.meuClube() != null) jogo.meuClube().confiancaDiretoria = 90;
                jogo.avancarSemana();
            }
        }
        Map<String, Integer> jogos = new HashMap<>();
        int total = 0;
        for (LigaKings l : jogo.ligasKings()) for (Torneio t : l.competicoes) conta(t, jogos);
        for (Torneio t : jogo.mundo().kingsMundiais) conta(t, jogos);
        for (Map.Entry<String, Integer> e : jogos.entrySet()) {
            total++;
            assertTrue(e.getValue() <= 1, "dois jogos na mesma semana: " + e.getKey());
        }
        assertTrue(total > 50, "houve jogos da Kings: " + total);
    }

    private static void conta(Torneio t, Map<String, Integer> jogos) {
        for (Partida p : t.partidas) for (String id : List.of(p.mandante, p.visitante))
            if (id != null) jogos.merge(id + " @ " + p.sa + " (" + t.id + ")", 1, Integer::sum);
    }

    @Test
    void jogoDoTimeDaKingsVoltaComoJogoDoUsuario() {
        Jogo jogo = Jogo.novoJogoKings(Fixture.banco(), 62, null, "kb-3", "T");
        boolean viu = false;
        for (int i = 0; i < 20 && !viu; i++) {
            Jogo.ResumoSemana r = jogo.avancarSemana();
            if (!r.jogosDoUsuario().isEmpty()) {
                viu = true;
                assertFalse(r.narradas().isEmpty(), "a narração do jogo da Kings vai para a tela de fim de jogo");
                assertTrue(r.narradas().get(0).melhorNome != null, "números do jogo e melhor em campo");
                assertTrue(r.jogosDoUsuario().get(0).envolve("kb-3"));
            }
        }
        assertTrue(viu, "o jogo da Kings aparece nos jogos do usuário (tela de fim igual à do futebol)");
    }
}
