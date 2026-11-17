package br.futebas.competicao;

import br.futebas.Fixture;
import br.futebas.dados.Db;
import br.futebas.ferramentas.CalibrarMotor;
import br.futebas.mundo.Rng;
import br.futebas.partida.Lance;
import br.futebas.partida.MotorPartida;
import br.futebas.partida.OpcoesPartida;
import br.futebas.partida.ResultadoPartida;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegrasEpocaTest {

    @Test
    void regrasMudamNoAnoCertoEPorAmbito() {
        String f = "https://exemplo.test/fonte-ficticia";
        List<Db.Regra> r = List.of(new Db.Regra("pontos_vitoria", "3", "1995-01-01", "mundo", f),
                new Db.Regra("substituicoes", "3", "1995-01-01", "mundo", f), new Db.Regra("substituicoes", "5", "2020-06-01", "mundo", f),
                new Db.Regra("var", "1", "2019-04-01", "BRA", f), new Db.Regra("gol_de_ouro", "1", "1995-01-01", "mundo", f),
                new Db.Regra("gol_de_ouro", "0", "2005-01-01", "mundo", f));
        RegrasEpoca a1995 = RegrasEpoca.resolver(r, 1995, "BRA", "bra-serie-a");
        RegrasEpoca a2019 = RegrasEpoca.resolver(r, 2019, "BRA", "bra-serie-a");
        RegrasEpoca a2021 = RegrasEpoca.resolver(r, 2021, "BRA", "bra-serie-a");
        RegrasEpoca arg2021 = RegrasEpoca.resolver(r, 2021, "ARG", null);
        assertEquals(3, a1995.pontosVitoria);
        assertEquals(3, a1995.substituicoes);
        assertTrue(a1995.golDeOuro);
        assertFalse(a2019.golDeOuro);
        assertEquals(3, a2019.substituicoes);
        assertTrue(a2019.var);
        assertEquals(5, a2021.substituicoes);
        assertFalse(arg2021.var, "VAR do banco fictício só vale para o Brasil");
    }

    @Test
    void padraoDaEpocaSemDados() {
        RegrasEpoca r1980 = RegrasEpoca.resolver(List.of(), 1980, "BRA", null);
        assertEquals(2, r1980.pontosVitoria);
        assertTrue(r1980.recuoGoleiroMao);
        RegrasEpoca r2022 = RegrasEpoca.resolver(List.of(), 2022, "BRA", null);
        assertEquals(5, r2022.substituicoes);
        assertEquals(3, r2022.pontosVitoria);
    }

    @Test
    void substituicoesDaEpocaNoMotor() {
        Rng rng = new Rng(3);
        for (int subs : new int[]{3, 5}) {
            int max = 0;
            for (int i = 0; i < 200; i++) {
                OpcoesPartida op = new OpcoesPartida();
                op.narrar = true;
                op.substituicoes = subs;
                ResultadoPartida res = MotorPartida.simular(CalibrarMotor.time("A", 70, rng, 5), CalibrarMotor.time("B", 70, rng, 5), op, rng);
                int[] cont = new int[2];
                for (Lance l : res.lances) if ("substituicao".equals(l.tipo)) cont[l.lado]++;
                max = Math.max(max, Math.max(cont[0], cont[1]));
            }
            assertTrue(max <= subs, "não pode passar de " + subs + " substituições");
            assertTrue(max >= Math.min(3, subs), "as substituições precisam acontecer");
        }
    }

    @Test
    void golDeOuroEncerraAProrrogacao() {
        Rng rng = new Rng(9);
        int comProrrogacao = 0;
        for (int i = 0; i < 400; i++) {
            OpcoesPartida op = new OpcoesPartida();
            op.prorrogacao = true;
            op.golDeOuro = true;
            op.narrar = true;
            ResultadoPartida r = MotorPartida.simular(CalibrarMotor.time("A", 70, rng, 3), CalibrarMotor.time("B", 70, rng, 3), op, rng);
            if (!r.prorrogacao) continue;
            comProrrogacao++;
            int golsNaProrrogacao = 0;
            for (int[] g : r.gols) if (g[2] > 90) golsNaProrrogacao++;
            assertTrue(golsNaProrrogacao <= 1, "gol de ouro: no máximo um gol na prorrogação");
        }
        assertTrue(comProrrogacao > 20);
    }
}
