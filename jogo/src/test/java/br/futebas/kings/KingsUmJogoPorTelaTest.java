package br.futebas.kings;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.ui.modelo.Fluxo;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug corrigido (12/11/2026): "ainda está indo direto para o próximo jogo na Kings". Cada "Jogar" do
 * Fluxo (o que a TelaPartida chama) joga no máximo UM jogo do time do técnico ao vivo e devolve a
 * rodada com ele (a App mostra a tela de fim e, no Continuar, a central), também nas semanas de
 * rodada dupla da Kings League IOS: o 2º jogo fica adiado para o próximo "Jogar".
 */
class KingsUmJogoPorTelaTest {

    /** Conta os jogos ao vivo (cada "fim" é um jogo inteiro na tela). */
    static final class Tela implements AoVivoKings {
        int fins;

        @Override public void minuto(ControleKings c) {}

        @Override public void pausa(ControleKings c, String motivo) {
            if ("fim".equals(motivo)) fins++;
        }
    }

    @Test
    void cadaJogarUmJogoAoVivoComARodadaDele() {
        KingsCustom.Definicao def = KingsCustomEPresidentesTest.definicao();
        Jogo jogo = Jogo.novoJogoKings(Fixture.banco(), 23, null, def.times.get(0).id, "T", def);
        String meu = jogo.mundo().tecnico.kingsTimeId;
        Fluxo f = new Fluxo(jogo);
        Tela tela = new Tela();
        int rodadas = 0, jogos = 0, adiados = 0;
        Set<Partida> vistos = new HashSet<>();
        for (int i = 0; i < 90 && jogo.ano() == 2026 && f.soKings(); i++) {
            jogo.mundo().tecnico.confiancaKings = 100.0; // o presidente não demite no meio do teste
            if (jogo.temJogoAdiadoKings()) adiados++;
            jogo.definirAoVivoKings(tela); // como a TelaPartida: a tela da Kings ligada durante o "Jogar"
            int antes = tela.fins;
            Fluxo.Rodada r;
            try {
                r = f.jogarProximaRodada(null);
            } finally {
                jogo.definirAoVivoKings(null);
            }
            int aoVivo = tela.fins - antes;
            assertTrue(aoVivo <= 1, "dois jogos seguidos na tela num só Jogar (semana " + r.semana() + ")");
            if (aoVivo == 1) {
                assertTrue(r.jogou(), "jogo ao vivo sem tela de fim (a App voltaria direto)");
                assertEquals(1, r.meusJogos().size());
                assertEquals(1, r.narradas().size());
                assertTrue(r.meusJogos().get(0).envolve(meu));
                assertTrue(vistos.add(r.meusJogos().get(0)), "o mesmo jogo duas vezes");
                jogos++;
            }
            rodadas++;
            if (r.fimDeTemporada() != null) break;
        }
        assertTrue(jogos >= 20, "jogou a Kings ao vivo: " + jogos);
        java.util.Map<Integer, Integer> porSemana = new java.util.HashMap<>();
        for (Torneio t : jogo.competicoesKings()) for (Partida p : t.partidas) if (p.envolve(meu)) porSemana.merge(p.sa, 1, Integer::sum);
        assertTrue(adiados > 0, "houve rodada dupla (IOS) com o 2º jogo adiado: " + adiados);
        // nenhum jogo do time ficou sem ser jogado na semana dele por mais de uma rodada do Fluxo
        for (Torneio t : jogo.competicoesKings())
            for (Partida p : t.partidas) if (p.envolve(meu) && !p.jogada) assertTrue(p.sa >= jogo.mundo().semanaAbsoluta() - 1, "jogo esquecido: " + p.sa);
        assertTrue(rodadas > 0);
    }
}
