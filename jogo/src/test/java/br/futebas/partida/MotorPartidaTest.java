package br.futebas.partida;

import br.futebas.ferramentas.CalibrarMotor;
import br.futebas.mundo.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MotorPartidaTest {

    @Test
    void ligaComDistribuicaoRealista() {
        // 10 temporadas (3800 jogos): com 4 o desvio dos empates (~1,1 ponto) batia no limite por sorteio
        double[] l = CalibrarMotor.liga(10, 17); // {gols/jogo, mandante%, empate%, visitante%}
        assertTrue(l[0] > 2.3 && l[0] < 3.2, "gols por jogo: " + l[0]);
        assertTrue(l[1] > 42 && l[1] < 52, "vitórias do mandante: " + l[1]);
        assertTrue(l[2] > 22 && l[2] < 31, "empates: " + l[2]);
        assertTrue(l[1] > l[3] + 10, "o mando de campo tem peso");
    }

    @Test
    void placaresMenosExageradosEntreDesiguais() {
        double[] m = CalibrarMotor.medir(85, 65, 3000, 3);
        // regra do jogo: 85 x 65 pode ser goleada de ~3 gols, mas não de 5
        assertTrue(m[0] < 3.8, "o forte não pode golear sempre: " + m[0]);
        assertTrue(m[2] > 70, "mas vence quase sempre: " + m[2]);
        assertTrue(m[4] > 1.5, "zebra existe: " + m[4]);
    }

    @Test
    void narracaoEmPortuguesENotas() {
        Rng rng = new Rng(4);
        OpcoesPartida op = new OpcoesPartida();
        op.narrar = true;
        ResultadoPartida r = MotorPartida.simular(CalibrarMotor.time("Casa", 72, rng, 4), CalibrarMotor.time("Fora", 70, rng, 4), op, rng);
        assertTrue(r.lances.get(0).texto.startsWith("Rola a bola!"));
        assertEquals("fim", r.lances.get(r.lances.size() - 1).tipo);
        assertTrue(r.lances.stream().anyMatch(l -> l.tipo.equals("intervalo")));
        assertEquals(r.golsMandante + r.golsVisitante, r.gols.size());
        assertEquals(r.golsMandante + r.golsVisitante, r.lances.stream().filter(l -> l.tipo.equals("gol")).count());
        for (double n : r.notas.values()) assertTrue(n >= 3 && n <= 10);
        assertNotNull(r.melhorNome);
        assertNotNull(r.comentario);
        assertTrue(r.posse[0] + r.posse[1] == 100);
        // caminho rápido: mesmo motor, sem textos
        ResultadoPartida rapido = MotorPartida.simular(CalibrarMotor.time("Casa", 72, rng, 4), CalibrarMotor.time("Fora", 70, rng, 4), new OpcoesPartida(), rng);
        assertTrue(rapido.lances.isEmpty());
        assertFalse(rapido.notas.isEmpty());
    }

    @Test
    void penaltisSempreTemVencedor() {
        Rng rng = new Rng(8);
        for (int i = 0; i < 300; i++) {
            Penaltis.Disputa d = Penaltis.disputar(CalibrarMotor.time("A", 70, rng, 4), CalibrarMotor.time("B", 70, rng, 4), rng, i % 10 == 0);
            assertNotEquals(d.golsMandante(), d.golsVisitante());
        }
    }

    @Test
    void mesmoRngMesmaPartida() {
        ResultadoPartida a = MotorPartida.simular(CalibrarMotor.time("A", 70, new Rng(1), 4), CalibrarMotor.time("B", 66, new Rng(2), 4), new OpcoesPartida(), new Rng(77));
        ResultadoPartida b = MotorPartida.simular(CalibrarMotor.time("A", 70, new Rng(1), 4), CalibrarMotor.time("B", 66, new Rng(2), 4), new OpcoesPartida(), new Rng(77));
        assertEquals(a.placar(), b.placar());
        assertEquals(a.notas, b.notas);
    }
}
