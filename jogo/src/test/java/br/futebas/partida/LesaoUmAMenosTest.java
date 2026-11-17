package br.futebas.partida;

import br.futebas.ferramentas.CalibrarMotor;
import br.futebas.mundo.Rng;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requisito: lesionado sem substituição possível sai e o time fica com um a menos (como na
 * expulsão nas chances, mas sem cartão nem suspensão). Com trocas, a IA substitui.
 */
class LesaoUmAMenosTest {

    @Test
    void semTrocasOLesionadoSaiEOTimeFicaComUmAMenos() {
        Rng rng = new Rng(4);
        OpcoesPartida op = new OpcoesPartida();
        op.narrar = true;
        op.substituicoes = 0;
        int desfalques = 0;
        for (int i = 0; i < 400; i++) {
            TimePartida a = CalibrarMotor.time("A", 70, rng, 0), b = CalibrarMotor.time("B", 70, rng, 0);
            ResultadoPartida r = MotorPartida.simular(a, b, op, rng);
            Set<Integer> vermelhos = new HashSet<>();
            for (int[] v : r.vermelhos) vermelhos.add(v[1]);
            for (Lance l : r.lances) {
                if (!"desfalque".equals(l.tipo)) continue;
                desfalques++;
                int id = l.jogadorId;
                assertTrue(l.texto.contains("sai lesionado") && l.texto.contains("um a menos"), l.texto);
                assertTrue(r.lesoesIds().contains(id), "é um dos lesionados");
                assertFalse(vermelhos.contains(id), "não conta como expulsão (sem suspensão)");
                assertEquals(Math.max(1, l.minuto), (int) r.minutos.get(id), "saiu do jogo no minuto da lesão");
                // depois de sair, não faz mais nada no jogo
                for (Lance x : r.lances) {
                    if (x.jogadorId != null && x.jogadorId == id && x.minuto > l.minuto && !"fim".equals(x.tipo))
                        fail("o lesionado que saiu apareceu depois: " + x.texto);
                }
            }
        }
        // ~0,12 lesão por time por jogo: ~96 em 400 jogos
        assertTrue(desfalques > 50, "lesões sem troca viram um a menos: " + desfalques);
    }

    @Test
    void comTrocasAIASubstituiOLesionado() {
        Rng rng = new Rng(6);
        OpcoesPartida op = new OpcoesPartida();
        op.narrar = true;
        op.substituicoes = 5;
        int lesoes = 0, desfalques = 0;
        for (int i = 0; i < 400; i++) {
            TimePartida a = CalibrarMotor.time("A", 70, rng, 0), b = CalibrarMotor.time("B", 70, rng, 0);
            ResultadoPartida r = MotorPartida.simular(a, b, op, rng);
            lesoes += r.lesoes.size();
            for (Lance l : r.lances) if ("desfalque".equals(l.tipo)) desfalques++;
        }
        assertTrue(lesoes > 50);
        assertTrue(desfalques < lesoes * 0.25, "com trocas, quase sempre substitui: " + desfalques + " de " + lesoes);
    }
}
