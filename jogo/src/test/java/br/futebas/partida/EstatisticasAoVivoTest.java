package br.futebas.partida;

import br.futebas.ferramentas.CalibrarMotor;
import br.futebas.mundo.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Números do jogo até agora (tela de trocas/intervalo): ler as estatísticas a cada minuto não muda o
 * resultado, os números só crescem e no intervalo fazem sentido.
 */
class EstatisticasAoVivoTest {

    /** Lê os números a cada minuto e no intervalo, sem mexer em nada. */
    static final class Leitor implements AoVivo {
        final List<Estatisticas> vistas = new ArrayList<>();
        Estatisticas intervalo;

        @Override public void minuto(Controle c) { vistas.add(c.estatisticas()); }

        @Override
        public void pausa(Controle c, Pausa motivo, int lado, Integer jogadorId) {
            if (motivo == Pausa.INTERVALO) intervalo = c.estatisticas();
        }
    }

    /** O mesmo jogo, sem ninguém olhando: só o lado humano (sem trocas automáticas) igual nos dois. */
    static final class Mudo implements AoVivo {
        @Override public void minuto(Controle c) {}
    }

    private static ResultadoPartida jogar(long semente, AoVivo a) {
        Rng times = new Rng(semente);
        TimePartida m = CalibrarMotor.time("A", 74, times, 2), v = CalibrarMotor.time("B", 69, times, 2);
        OpcoesPartida op = new OpcoesPartida();
        op.aoVivo = a;
        op.ladoHumano = 0;
        op.narrar = true;
        return MotorPartida.simular(m, v, op, new Rng(semente * 31 + 7));
    }

    @Test
    void lerOsNumerosNaoMudaOJogo() {
        for (long s = 1; s <= 60; s++) {
            Leitor l = new Leitor();
            ResultadoPartida a = jogar(s, l), b = jogar(s, new Mudo());
            assertEquals(a.golsMandante, b.golsMandante, "gols do mandante, semente " + s);
            assertEquals(a.golsVisitante, b.golsVisitante, "gols do visitante, semente " + s);
            assertArrayEquals(a.chutes, b.chutes);
            assertArrayEquals(a.noGol, b.noGol);
            assertArrayEquals(a.posse, b.posse);
            assertArrayEquals(a.faltas, b.faltas);
            assertArrayEquals(a.escanteios, b.escanteios);
            assertEquals(a.lances.size(), b.lances.size());
            assertEquals(a.notas, b.notas);
        }
    }

    @Test
    void numerosCrescemEFazemSentidoNoIntervalo() {
        for (long s = 1; s <= 40; s++) {
            Leitor l = new Leitor();
            ResultadoPartida r = jogar(s, l);
            assertNotNull(l.intervalo, "números no intervalo");
            Estatisticas e = l.intervalo;
            assertEquals(100, e.posse()[0] + e.posse()[1]);
            assertTrue(e.posse()[0] >= 28 && e.posse()[0] <= 72, "posse " + e.posse()[0]);
            for (int lado = 0; lado < 2; lado++) {
                assertTrue(e.noAlvo()[lado] <= e.finalizacoes()[lado], "no alvo <= finalizações");
                assertTrue(e.faltas()[lado] >= 0 && e.faltas()[lado] < 20, "faltas no 1º tempo: " + e.faltas()[lado]);
                assertTrue(e.finalizacoes()[lado] <= r.chutes[lado] + 6, "finalizações no intervalo perto do final");
            }
            Estatisticas antes = null;
            for (Estatisticas x : l.vistas) {
                if (antes != null) for (int lado = 0; lado < 2; lado++) {
                    assertTrue(x.finalizacoes()[lado] >= antes.finalizacoes()[lado]);
                    assertTrue(x.faltas()[lado] >= antes.faltas()[lado]);
                    assertTrue(x.escanteios()[lado] >= antes.escanteios()[lado]);
                    assertTrue(x.amarelos()[lado] >= antes.amarelos()[lado]);
                }
                antes = x;
            }
            // no fim, os números do resultado
            Estatisticas fim = Estatisticas.de(r);
            assertArrayEquals(r.chutes, fim.finalizacoes());
            assertEquals(r.amarelos.size(), fim.amarelos()[0] + fim.amarelos()[1]);
        }
    }
}
