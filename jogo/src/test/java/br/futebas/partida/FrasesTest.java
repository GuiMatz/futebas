package br.futebas.partida;

import br.futebas.ferramentas.CalibrarMotor;
import br.futebas.mundo.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Banco de frases (requisito): variedade de verdade, sem repetir em seguida, uma linha curta. */
class FrasesTest {

    /** Legenda/título como a tela gera para os lances de um jogo narrado. */
    static List<String> legendas(ResultadoPartida r, Frases f, int meuLado) {
        List<String> l = new ArrayList<>();
        for (Lance x : r.lances) {
            if (x.lado < 0 || "penalti".equals(x.jogada)) continue;
            String time = x.lado == 0 ? r.mandante : r.visitante;
            int pro = x.lado == 1 ? x.golsVisitante : x.golsMandante, contra = x.lado == 1 ? x.golsMandante : x.golsVisitante;
            String goleiro = "defesa".equals(x.tipo) || "milagre".equals(x.tipo) ? x.detalhe : null;
            Frases.Contexto c = new Frases.Contexto(x.minuto, x.acrescimo, x.lado == meuLado, pro, contra, x.jogador, goleiro, time, x.jogada);
            switch (x.tipo) {
                case "fora", "defesa", "milagre", "trave", "impedimento" -> l.add(x.relogio() + " " + x.jogador + " · " + f.chance(x.tipo, c));
                case "gol" -> l.add(x.relogio() + " " + f.tituloGol(c) + " (" + x.jogador + ")");
                default -> { }
            }
        }
        return l;
    }

    @Test
    void variedadeSemRepetirEmSeguida() {
        Frases f = new Frases(7);
        Rng rng = new Rng(3);
        OpcoesPartida op = new OpcoesPartida();
        op.narrar = true;
        List<String> perdidas = new ArrayList<>();
        String anterior = null;
        while (perdidas.size() < 200) {
            ResultadoPartida r = MotorPartida.simular(CalibrarMotor.time("Flamengo", 72, rng, 3), CalibrarMotor.time("Palmeiras", 72, rng, 3), op, rng);
            for (Lance x : r.lances) {
                if (x.lado < 0 || "penalti".equals(x.jogada) || !List.of("fora", "defesa", "milagre", "trave").contains(x.tipo)) continue;
                String goleiro = "defesa".equals(x.tipo) || "milagre".equals(x.tipo) ? x.detalhe : null;
                String s = f.chance(x.tipo, new Frases.Contexto(x.minuto, x.acrescimo, x.lado == 0, 0, 0, x.jogador, goleiro, "Flamengo", x.jogada));
                assertNotEquals(anterior, s, "não repete a mesma frase em seguida");
                assertTrue(s.length() <= 64, "uma linha curta: " + s);
                anterior = s;
                perdidas.add(s);
            }
        }
        Set<String> distintas = new HashSet<>(perdidas);
        assertTrue(distintas.size() >= 10, "variedade: " + distintas.size());
        // gols: variações além de "GOL DO TIME!!"
        Set<String> titulos = new HashSet<>();
        for (int i = 0; i < 60; i++) titulos.add(f.tituloGol(new Frases.Contexto(30 + i, 0, true, 1 + i % 3, i % 3, "Pedro", null, "Flamengo", i % 4 == 0 ? "fora" : null)));
        assertTrue(titulos.size() >= 8, "títulos de gol variados: " + titulos);
    }

    @Test
    void amostraDeUmJogo() {
        Rng rng = new Rng(11);
        OpcoesPartida op = new OpcoesPartida();
        op.narrar = true;
        Frases f = new Frases(11);
        for (int k = 0; k < 3; k++) {
            ResultadoPartida r = MotorPartida.simular(CalibrarMotor.time("Flamengo", 74, rng, 4), CalibrarMotor.time("Palmeiras", 73, rng, 4), op, rng);
            List<String> l = legendas(r, f, 0);
            assertTrue(l.size() >= 3, "um jogo tem chances na linha do tempo: " + l.size());
            System.out.println("== amostra (" + r.placar() + ")");
            for (String s : l) System.out.println(s);
        }
    }

    /** A linha já é "Nome · legenda": a legenda nunca repete o nome ("Flamengo 7 · Inacreditável o que Flamengo 7 perdeu!"). */
    @Test
    void legendaNaoRepeteONomeDoJogador() {
        for (String[] banco : List.of(Frases.FORA, Frases.FORA_DE_LONGE, Frases.FORA_CABECA, Frases.DEFESA, Frases.MILAGRE, Frases.TRAVE,
                Frases.PERDEU, Frases.UFA, Frases.TARDE, Frases.IMPEDIMENTO, Frases.GOL, Frases.GOLACO, Frases.GOL_CABECA, Frases.GOL_TARDE,
                Frases.GOL_ACRESCIMO, Frases.GOL_EMPATE, Frases.GOL_FRENTE_TARDE, Frases.GOL_CONTRA))
            for (String s : banco) assertFalse(s.contains("{j}"), "sem o nome do jogador: " + s);
        Frases f = new Frases(5);
        String[] jogadas = {null, "rebote", "contra_ataque", "fora", "cabeca", "falta"};
        for (int i = 0; i < 400; i++) {
            Frases.Contexto c = new Frases.Contexto(10 + i % 85, 0, i % 3 == 0 ? null : i % 3 == 1, 1, 1, "Flamengo 7", "Rossi", "Flamengo", jogadas[i % jogadas.length]);
            for (String tipo : new String[] {"fora", "defesa", "milagre", "trave", "impedimento"}) {
                String s = f.chance(tipo, c);
                assertFalse(s.toLowerCase(java.util.Locale.ROOT).contains("flamengo 7"), "repetiu o nome: " + s);
            }
            assertFalse(f.tituloGol(c).toLowerCase(java.util.Locale.ROOT).contains("flamengo 7"), "título repetiu o nome");
        }
    }
}
