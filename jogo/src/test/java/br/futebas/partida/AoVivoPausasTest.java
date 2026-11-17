package br.futebas.partida;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.mundo.Rng;
import br.futebas.ferramentas.CalibrarMotor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pausas do jogo ao vivo (intervalo, pênalti a favor, lesão, expulsão) e os outros jogos da rodada. */
class AoVivoPausasTest {

    static final class Gravador implements AoVivo {
        final List<Pausa> pausas = new ArrayList<>();
        final List<List<JogoParalelo>> rodadas = new ArrayList<>();
        int batedores;
        Integer escolhido;

        @Override public void minuto(Controle c) {}

        @Override
        public void pausa(Controle c, Pausa motivo, int lado, Integer jogadorId) {
            pausas.add(motivo);
            if (motivo == Pausa.INTERVALO) assertTrue(c.intervalo());
            if (motivo == Pausa.LESAO) {
                // o técnico troca o lesionado pelo primeiro do banco
                if (!c.banco(lado).isEmpty() && c.substituicoesFeitas(lado) < c.substituicoesPermitidas()) {
                    assertTrue(c.substituir(lado, jogadorId, c.banco(lado).get(0).id));
                }
            }
        }

        @Override
        public Integer escolherBatedor(Controle c, int lado, List<Escalado> candidatos) {
            batedores++;
            assertFalse(candidatos.isEmpty());
            escolhido = candidatos.get(candidatos.size() - 1).id;
            return escolhido;
        }

        @Override public void outrosJogos(List<JogoParalelo> jogos) { rodadas.add(jogos); }
    }

    @Test
    void pausasNoMotor() {
        Rng rng = new Rng(5);
        int intervalos = 0, penaltis = 0, lesoes = 0, expulsoes = 0, jogos = 400;
        int eventosGol = 0, gols = 0;
        for (int i = 0; i < jogos; i++) {
            Gravador g = new Gravador();
            OpcoesPartida op = new OpcoesPartida();
            op.aoVivo = g;
            op.ladoHumano = 0;
            ResultadoPartida r = MotorPartida.simular(CalibrarMotor.time("A", 72, rng, 2), CalibrarMotor.time("B", 70, rng, 2), op, rng);
            for (AoVivo.Pausa p : g.pausas) {
                if (p == AoVivo.Pausa.INTERVALO) intervalos++;
                if (p == AoVivo.Pausa.LESAO) lesoes++;
                if (p == AoVivo.Pausa.EXPULSAO) expulsoes++;
            }
            penaltis += g.batedores;
            for (EventoJogo e : r.eventos) if (e.gol()) eventosGol++;
            gols += r.golsMandante + r.golsVisitante;
            // o batedor escolhido é quem cobra
            if (g.escolhido != null) {
                boolean cobrou = false;
                for (EventoJogo e : r.eventos) if (e.tipo().startsWith("penalti") && e.lado() == 0 && e.jogadorId() == g.escolhido) cobrou = true;
                assertTrue(cobrou, "o batedor escolhido cobra o pênalti");
            }
        }
        assertEquals(jogos, intervalos, "o intervalo sempre pausa");
        assertTrue(penaltis > 0, "houve pênaltis a favor");
        assertTrue(lesoes > 10 && lesoes < 120, "lesões no jogo: " + lesoes);
        assertTrue(expulsoes > 0, "houve expulsões");
        assertEquals(gols, eventosGol, "todo gol vira evento");
    }

    @Test
    void outrosJogosDaRodadaChegamAntesDoApito() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 41, "bra-1-01", "Técnico Teste");
        Gravador g = new Gravador();
        j.definirAoVivo(g);
        for (int k = 0; k < 20 && g.rodadas.stream().noneMatch(l -> !l.isEmpty()); k++) j.avancarSemana();
        List<JogoParalelo> l = g.rodadas.stream().filter(x -> !x.isEmpty()).findFirst().orElseThrow();
        assertEquals(g.rodadas.get(g.rodadas.size() - 1), j.outrosJogosDaRodada());
        for (JogoParalelo p : l) {
            assertFalse(p.mandanteId().equals("bra-1-01") || p.visitanteId().equals("bra-1-01"));
            int[] fim = p.placarAte(200);
            assertEquals(p.golsMandante(), fim[0], "gols do evento = placar");
            assertEquals(p.golsVisitante(), fim[1]);
        }
        assertTrue(g.pausas.contains(AoVivo.Pausa.INTERVALO));
    }

    @Test
    void auxiliarJogaSemTelaAoVivo() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 43, "bra-1-01", "Técnico Teste");
        Gravador g = new Gravador();
        j.definirAoVivo(g);
        for (var t : j.mundo().torneios) if (t.participantes.contains("bra-1-01")) j.delegarAoAuxiliar(t.id, null, true);
        int jogos = 0;
        for (int k = 0; k < 8; k++) jogos += j.avancarSemana().jogosDoUsuario().size();
        assertTrue(jogos > 0, "houve jogos");
        assertTrue(g.pausas.isEmpty(), "delegado: sem pausas");
        assertTrue(j.delegadoAoAuxiliar(j.mundo().torneios.stream().filter(t -> t.participantes.contains("bra-1-01")).findFirst().get().id, 0));
    }
}
