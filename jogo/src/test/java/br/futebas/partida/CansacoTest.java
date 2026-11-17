package br.futebas.partida;

import br.futebas.Balanceamento;
import br.futebas.ferramentas.CalibrarMotor;
import br.futebas.mundo.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Cansaço no jogo, como no Brasfoot: energia por jogador, mais rápida com idade, estilo e marcação. */
class CansacoTest {

    /** Energia média no apito final dos titulares de linha que jogaram os 90 minutos. */
    private static double energiaFinal(int jogos, int idade, Estilo estilo, Marcacao marc) {
        Rng rng = new Rng(5);
        OpcoesPartida op = new OpcoesPartida();
        op.ano = 2026;
        op.substituicoes = 0;
        double soma = 0;
        int n = 0;
        for (int i = 0; i < jogos; i++) {
            TimePartida a = CalibrarMotor.time("A", 70, rng, 0), b = CalibrarMotor.time("B", 70, rng, 0);
            for (Escalado e : a.titulares) e.anoNasc = 2026 - idade;
            a.estilo = estilo;
            a.marcacao = marc;
            ResultadoPartida r = MotorPartida.simular(a, b, op, rng);
            for (Escalado e : a.titulares) {
                if (e.vaga == br.futebas.mundo.Posicao.GOL || r.minutos.getOrDefault(e.id, 0) < 90 || r.lesoesIds().contains(e.id)) continue;
                soma += r.energia.get(e.id);
                n++;
            }
        }
        return soma / n;
    }

    @Test
    void energiaCaiComIdadeEstiloEMarcacao() {
        double base = energiaFinal(40, 28, Estilo.EQUILIBRADO, Marcacao.NORMAL);
        double velho = energiaFinal(40, 34, Estilo.EQUILIBRADO, Marcacao.NORMAL);
        double jovem = energiaFinal(40, 19, Estilo.EQUILIBRADO, Marcacao.NORMAL);
        double intenso = energiaFinal(40, 28, Estilo.PRESSAO_ALTA, Marcacao.PESADA);
        assertEquals(100 - Balanceamento.ENERGIA_GASTO[2], base, 1.5, "28 anos, jogo inteiro: gasta o da tabela");
        assertTrue(velho < base - 3 && jovem > base + 4, "idade: " + jovem + " / " + base + " / " + velho);
        assertTrue(intenso < base - 8, "pressão alta + marcação pesada cansam: " + intenso);
    }

    @Test
    void cansadoRendeMenosEOTimeDeEntradaNaoMuda() {
        TimePartida a = CalibrarMotor.time("A", 70, new Rng(1), 4), b = CalibrarMotor.time("B", 70, new Rng(2), 4);
        List<Double> antes = new ArrayList<>();
        for (Escalado e : a.titulares) antes.add(e.energia);
        OpcoesPartida op = new OpcoesPartida();
        ResultadoPartida r1 = MotorPartida.simular(a, b, op, new Rng(9));
        ResultadoPartida r2 = MotorPartida.simular(a, b, op, new Rng(9));
        assertEquals(r1.placar(), r2.placar(), "o motor não gasta a energia do time de entrada");
        for (int i = 0; i < a.titulares.size(); i++) assertEquals(antes.get(i), a.titulares.get(i).energia);
        Escalado e = new Escalado(1, "x", br.futebas.mundo.Posicao.MC, br.futebas.mundo.Posicao.MC, 70);
        e.folego = 1 - Balanceamento.FADIGA_JOGO * 0.2;
        assertTrue(e.rende() < e.rendeInteiro(), "cansado rende menos");
    }

    @Test
    void iaTrocaQuemEstaCansado() {
        Rng rng = new Rng(8);
        int inteiro = trocas(rng, 100, 2000), cansado = trocas(rng, 62, 1992);
        assertTrue(cansado > inteiro * 1.3 && cansado > 200, "a IA troca mais com o time cansado: " + cansado + " x " + inteiro);
    }

    /** Titulares de A substituídos em 200 jogos, com a energia e o ano de nascimento dados. */
    private static int trocas(Rng rng, double energia, int anoNasc) {
        OpcoesPartida op = new OpcoesPartida();
        op.ano = 2026;
        int n = 0;
        for (int i = 0; i < 200; i++) {
            TimePartida a = CalibrarMotor.time("A", 70, rng, 0), b = CalibrarMotor.time("B", 70, rng, 0);
            for (Escalado x : a.titulares) {
                x.energia = energia;
                x.anoNasc = anoNasc;
            }
            ResultadoPartida r = MotorPartida.simular(a, b, op, rng);
            for (Escalado x : a.titulares) if (r.minutos.getOrDefault(x.id, 90) < 90 && !r.lesoesIds().contains(x.id)) n++;
        }
        return n;
    }
}
