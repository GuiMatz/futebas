package br.futebas.kings;

import br.futebas.mundo.Rng;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.ResultadoPartida;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shoot out do desempate da Kings com o sistema da disputa de pênaltis (requisito): o técnico ao vivo
 * escolhe a ordem dos batedores; sem escolha (IA, sem tela), tudo como antes e determinístico.
 */
class ShootoutOrdemTest {

    @Test
    void tecnicoEscolheAOrdem() {
        int disputas = 0, pedidos = 0;
        for (int s = 0; s < 400 && disputas < 30; s++) {
            LigaKings.TimeKings a = MotorKings2026Test.time("a", 60, "Pres A"), b = MotorKings2026Test.time("b", 60, "Pres B");
            a.humano = true;
            List<List<String>> escolhida = new ArrayList<>();
            int[] chamadas = {0};
            AoVivoKings av = new AoVivoKings() {
                @Override public void minuto(ControleKings c) {}

                @Override
                public List<String> ordemShootout(int lado, List<LigaKings.AtletaKings> sugestao) {
                    assertEquals(0, lado, "só o lado do técnico");
                    assertFalse(sugestao.isEmpty());
                    chamadas[0]++;
                    List<String> ch = new ArrayList<>();
                    for (int i = sugestao.size() - 1; i >= 0; i--) ch.add(sugestao.get(i).chave()); // de trás para frente
                    escolhida.add(ch);
                    return ch;
                }
            };
            ResultadoPartida r = MotorKings.simular(a, b, new LigaKings.RegrasKings(), new Rng(500 + s), false, av, 0);
            if (r.penaltisMandante == null) {
                assertEquals(0, chamadas[0], "sem empate, sem shoot out");
                continue;
            }
            pedidos += chamadas[0];
            assertEquals(1, chamadas[0]);
            List<String> ordem = escolhida.get(0);
            int k = 0;
            for (EventoJogo e : r.eventos) {
                if (e.lado() != 0 || !e.tipo().startsWith("disputa_")) continue;
                String chave = "j" + e.jogadorId();
                assertEquals(ordem.get(k % ordem.size()), chave, "shoot out pela ordem escolhida");
                k++;
                disputas++;
            }
        }
        assertTrue(pedidos > 0 && disputas > 0);
    }

    @Test
    void semEscolhaMesmoResultado() {
        for (int s = 0; s < 60; s++) {
            ResultadoPartida x = MotorKings.simular(MotorKings2026Test.time("a", 60, "A"), MotorKings2026Test.time("b", 60, "B"),
                    new LigaKings.RegrasKings(), new Rng(s), false);
            ResultadoPartida y = MotorKings.simular(MotorKings2026Test.time("a", 60, "A"), MotorKings2026Test.time("b", 60, "B"),
                    new LigaKings.RegrasKings(), new Rng(s), false);
            assertEquals(x.placar(), y.placar());
            assertEquals(x.penaltisMandante, y.penaltisMandante);
            assertEquals(x.penaltisVisitante, y.penaltisVisitante);
        }
    }
}
