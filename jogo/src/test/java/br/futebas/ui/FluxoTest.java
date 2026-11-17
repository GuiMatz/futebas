package br.futebas.ui;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.historia.Historia;
import br.futebas.partida.Lance;
import br.futebas.ui.modelo.AcompanhamentoPartida;
import br.futebas.ui.modelo.Fluxo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** O laço do jogo da interface (sem JavaFX): uma temporada inteira de técnico, ao vivo e com troca. */
class FluxoTest {

    /** Um clube da Série A de 2026 no banco de teste. */
    static String clubeSerieA() {
        for (Jogo.OpcaoClube o : Jogo.clubesDisponiveis(Fixture.banco())) if (Integer.valueOf(1).equals(o.divisao())) return o.id();
        throw new IllegalStateException("sem Série A no banco de teste");
    }

    @Test
    void temporadaInteiraPelaInterface() {
        String clube = clubeSerieA();
        Jogo j = Jogo.novoJogo(Fixture.banco(), 1234, clube, "Técnico");
        Fluxo f = new Fluxo(j);
        List<AcompanhamentoPartida.Estado> estados = new ArrayList<>();
        int[] ordenou = {0};
        AcompanhamentoPartida[] ref = new AcompanhamentoPartida[1];
        ref[0] = new AcompanhamentoPartida(clube, AcompanhamentoPartida.Velocidade.INSTANTANEA, e -> {
            estados.add(e);
            // primeira partida: no minuto 60, o técnico troca o pior de linha pelo primeiro do banco
            if (ordenou[0] == 0 && e.meuLado() >= 0 && e.minuto() == 60 && !e.intervalo() && !e.banco().isEmpty()) {
                var sai = e.emCampo().stream().filter(a -> a.vaga() != br.futebas.mundo.Posicao.GOL).findFirst().orElseThrow();
                ref[0].substituir(sai.id(), e.banco().get(0).id());
                ordenou[0] = 1;
            }
        });
        Fluxo.ResumoTemporada fim = null;
        int jogos = 0;
        boolean viuTroca = false;
        for (int i = 0; i < 200 && fim == null; i++) {
            Fluxo.Rodada r = f.jogarProximaRodada(ref[0]);
            jogos += r.meusJogos().size();
            for (var res : r.narradas()) for (Lance l : res.lances) if ("substituicao".equals(l.tipo) && l.texto.contains(j.meuClube() == null ? "?" : j.meuClube().nome)) viuTroca = true;
            if (r.jogou()) {
                Partida p = r.meusJogos().get(0);
                assertTrue(p.jogada);
                assertNotNull(f.torneioDe(p));
            }
            fim = r.fimDeTemporada();
            if (r.demitido()) fim = f.avancarTemporada();
        }
        assertNotNull(fim, "a temporada terminou");
        assertEquals(2026, fim.ano());
        assertEquals(2027, j.ano());
        assertTrue(jogos > 20, "jogou a temporada: " + jogos);
        assertTrue(ordenou[0] == 1, "houve oportunidade de troca");
        assertTrue(viuTroca, "a troca pedida pela interface entrou na narração");
        assertFalse(estados.isEmpty());
        assertTrue(estados.stream().anyMatch(AcompanhamentoPartida.Estado::terminado));
        assertFalse(fim.campeoes().isEmpty());
        for (Historia.Titulo t : fim.campeoes()) assertEquals(2026, t.ano);
        assertNotNull(fim.veredito());
        // segunda temporada começa normalmente
        Fluxo.Rodada r = f.jogarProximaRodada(null);
        assertEquals(2027, r.ano());
    }

    @Test
    void acompanharSemMexerNaoMudaOMundo() {
        Jogo a = Jogo.novoJogo(Fixture.banco(), 77, "bra-1-01", "A");
        Jogo b = Jogo.novoJogo(Fixture.banco(), 77, "bra-1-01", "B");
        Fluxo fa = new Fluxo(a), fb = new Fluxo(b);
        for (int i = 0; i < 8; i++) {
            fa.jogarProximaRodada(new AcompanhamentoPartida("bra-1-01", AcompanhamentoPartida.Velocidade.INSTANTANEA, null));
            fb.jogarProximaRodada(null);
        }
        List<String> pa = new ArrayList<>(), pb = new ArrayList<>();
        for (Partida p : a.calendarioDoClube("bra-1-01")) pa.add(p.placar());
        for (Partida p : b.calendarioDoClube("bra-1-01")) pb.add(p.placar());
        assertEquals(pb, pa);
    }

    @Test
    void soAssistirComManchetes() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 5, null, null);
        Fluxo f = new Fluxo(j);
        Fluxo.ResumoTemporada r = f.avancarTemporada();
        assertNotNull(r);
        assertFalse(f.manchetesNovas().isEmpty());
        assertTrue(f.manchetesNovas().isEmpty(), "manchetes não se repetem");
    }
}
