package br.futebas.partida;

import br.futebas.ferramentas.CalibrarMotor;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requisito: mudar a formação no meio do jogo. Não gasta substituição, cada um vai para a vaga
 * nova em que rende mais e o perfil do time é refeito (o resto do jogo segue com a formação nova).
 */
class FormacaoNoJogoTest {

    @Test
    void remapearPeloMelhorEncaixe() {
        Posicao[] nat = Formacoes.vagas("4-4-2");
        Posicao[] novas = Formacoes.vagas("3-5-2");
        int[] d = Escalador.remapear(nat, novas);
        boolean[] usada = new boolean[novas.length];
        for (int i = 0; i < d.length; i++) {
            assertFalse(usada[d[i]], "cada vaga recebe um jogador");
            usada[d[i]] = true;
            if (nat[i] == Posicao.GOL) assertEquals(Posicao.GOL, novas[d[i]], "o goleiro fica no gol");
        }
        // na mesma formação ninguém sai do lugar
        int[] mesma = Escalador.remapear(nat, nat);
        for (int i = 0; i < mesma.length; i++) assertEquals(nat[i], nat[mesma[i]]);
    }

    @Test
    void mudaFormacaoNoIntervaloSemGastarSubstituicao() {
        Rng rng = new Rng(9);
        for (int k = 0; k < 30; k++) {
            List<String> erros = new ArrayList<>();
            AoVivo av = new AoVivo() {
                @Override
                public void minuto(Controle c) {}

                @Override
                public void pausa(Controle c, Pausa motivo, int lado, Integer jogadorId) {
                    if (motivo != Pausa.INTERVALO) return;
                    int subs = c.substituicoesFeitas(lado);
                    if (!c.mudarFormacao(lado, "3-5-2")) erros.add("não mudou");
                    Posicao[] vagas = new Posicao[c.vagas(lado).size()];
                    for (int i = 0; i < vagas.length; i++) vagas[i] = c.vagas(lado).get(i).vaga;
                    if (!Arrays.equals(Formacoes.vagas("3-5-2"), vagas)) erros.add("vagas " + Arrays.toString(vagas));
                    if (c.substituicoesFeitas(lado) != subs) erros.add("gastou substituição");
                    if (c.mudarFormacao(lado, "formação-que-não-existe")) erros.add("aceitou formação inválida");
                }
            };
            OpcoesPartida op = new OpcoesPartida();
            op.aoVivo = av;
            op.ladoHumano = 0;
            op.narrar = true;
            ResultadoPartida r = MotorPartida.simular(CalibrarMotor.time("A", 72, rng, 2), CalibrarMotor.time("B", 70, rng, 2), op, rng);
            assertTrue(erros.isEmpty(), erros.toString());
            assertTrue(r.lances.stream().anyMatch(l -> "tatica".equals(l.tipo) && l.texto.contains("3-5-2")), "a narração conta a mudança");
        }
    }
}
