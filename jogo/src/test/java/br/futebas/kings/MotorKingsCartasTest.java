package br.futebas.kings;

import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.ResultadoPartida;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cartas secretas (regras do jogo, 12/11/2026): pênalti da carta sempre com resultado registrado,
 * pênalti invertido (rival bate; errou = gol de quem usou; nunca o goleiro), jogador estrela
 * escolhido (todo gol dele vale 2) e suspensão (um de linha do rival fora por 4 minutos).
 */
class MotorKingsCartasTest {

    /** Usa a carta na 1ª chance, escolhendo com {@code escolha} (sobre os atletas em campo dos dois lados). */
    static final class Usa implements AoVivoKings {
        final Function<ControleKings, String> escolha;
        final List<int[]> emCampoRival = new ArrayList<>(); // {minuto, em campo do lado 1}
        boolean usou;

        Usa(Function<ControleKings, String> escolha) { this.escolha = escolha; }

        @Override
        public void minuto(ControleKings c) {
            emCampoRival.add(new int[]{c.minuto(), c.emCampo(1)});
            if (!usou && c.podeUsarCarta(0)) usou = c.usarCarta(0, null, escolha.apply(c));
        }
    }

    static LigaKings.TimeKings time(String id) {
        return MotorKings2026Test.time(id, 65, "Pres " + id);
    }

    /**
     * Duelo de dois técnicos na Kings (multi): a IA não usa a carta nem o pênalti do presidente por
     * nenhum dos dois lados (antes o 2º técnico ficava com a IA decidindo por ele).
     */
    @Test
    void dueloDeDoisTecnicosSemIaDecidindo() {
        int iaUsou = 0;
        for (int i = 0; i < 40; i++) {
            AoVivoKings parado = c -> {};
            ResultadoPartida r = MotorKings.simular(time("a"), time("b"), new LigaKings.RegrasKings(), new Rng(500 + i), true, parado, true, true);
            for (EventoJogo e : r.eventos)
                assertFalse(e.tipo().equals("carta_usada") || e.tipo().startsWith("penalti_presidente"), "jogo " + i + ": a IA decidiu por um técnico: " + e.tipo());
            // um técnico só: o outro lado (IA) continua decidindo
            ResultadoPartida s = MotorKings.simular(time("a"), time("b"), new LigaKings.RegrasKings(), new Rng(500 + i), true, parado, true, false);
            for (EventoJogo e : s.eventos) {
                if (!e.tipo().equals("carta_usada") && !e.tipo().startsWith("penalti_presidente")) continue;
                assertEquals(1, e.lado(), "só o lado da IA");
                iaUsou++;
            }
        }
        assertTrue(iaUsou > 0, "a IA usa carta/presidente pelo lado dela");
    }

    @Test
    void penaltiDaCartaSempreTemResultado() {
        int gols = 0, erros = 0;
        for (int i = 0; i < 300; i++) {
            Usa u = new Usa(c -> null);
            ResultadoPartida r = MotorKings.simularComCarta(time("a"), time("b"), new Rng(100 + i), MotorKings.Carta.PENALTI, u);
            assertTrue(u.usou);
            List<EventoJogo> res = r.eventos.stream().filter(e -> e.tipo().startsWith("penalti_carta_") && e.lado() == 0).toList(); // o rival (IA) pode ter tirado outro Pênalti
            assertEquals(1, res.size(), "um resultado registrado por uso");
            EventoJogo e = res.get(0);
            assertEquals(0, e.lado());
            assertTrue(e.jogador().contains(" × ") && e.jogador().matches(".*· (GOL|DEFENDEU|PRA FORA)"), e.jogador());
            if (e.tipo().equals("penalti_carta_gol")) {
                gols++;
                int idx = r.eventos.indexOf(e);
                assertTrue(r.eventos.get(idx + 1).gol() && r.eventos.get(idx + 1).lado() == 0, "o gol entra no placar");
            } else erros++;
        }
        assertTrue(gols > 150 && erros > 20, gols + " gols, " + erros + " erros");
    }

    @Test
    void penaltiInvertidoNuncaGoleiroEErroVaiProPlacarDeQuemUsou() {
        int nada = 0, golDeQuemUsou = 0;
        for (int i = 0; i < 300; i++) {
            // tenta escolher o goleiro rival: não pode (vira o pior de linha)
            Usa u = new Usa(c -> c.emCampoAgora(1).stream().filter(a -> a.posicao == Posicao.GOL).map(LigaKings.AtletaKings::chave).findFirst().orElse(null));
            LigaKings.TimeKings a = time("a"), b = time("b");
            ResultadoPartida r = MotorKings.simularComCarta(a, b, new Rng(500 + i), MotorKings.Carta.PENALTI_INVERTIDO, u);
            EventoJogo e = r.eventos.stream().filter(x -> x.tipo().startsWith("penalti_invertido_") && x.lado() == 0).findFirst().orElseThrow();
            String cobrador = e.jogador().substring(0, e.jogador().indexOf(" ("));
            for (LigaKings.AtletaKings x : b.elenco) if (x.nome.equals(cobrador)) assertNotEquals(Posicao.GOL, x.posicao, "o goleiro não bate o invertido");
            int idx = r.eventos.indexOf(e);
            if (e.tipo().equals("penalti_invertido_gol")) {
                nada++;
                assertTrue(idx + 1 >= r.eventos.size() || !r.eventos.get(idx + 1).gol() || r.eventos.get(idx + 1).minuto() != e.minuto()
                        || !r.eventos.get(idx + 1).tipo().equals("penalti_gol"), "converteu: nada acontece");
            } else {
                golDeQuemUsou++;
                EventoJogo g = r.eventos.get(idx + 1);
                assertEquals("penalti_gol", g.tipo(), "errou: gol de quem usou a carta (vale 1)");
                assertEquals(0, g.lado());
                assertNull(g.jogador(), "o gol não é de nenhum atleta (fora da artilharia)");
            }
        }
        assertTrue(nada > 100 && golDeQuemUsou > 30, nada + " / " + golDeQuemUsou);
    }

    @Test
    void jogadorEstrelaEscolhidoTemTodosOsGolsDobrados() {
        int golsEstrela = 0;
        for (int i = 0; i < 200; i++) {
            String[] escolhido = new String[1];
            Usa u = new Usa(c -> {
                LigaKings.AtletaKings x = c.emCampoAgora(0).stream().filter(a -> a.posicao != Posicao.GOL).reduce((p, q) -> q).orElseThrow();
                escolhido[0] = x.nome;
                return x.chave();
            });
            ResultadoPartida r = MotorKings.simularComCarta(time("a"), time("b"), new Rng(900 + i), MotorKings.Carta.JOGADOR_ESTRELA, u);
            EventoJogo marca = r.eventos.stream().filter(e -> e.tipo().equals("jogador_estrela") && e.lado() == 0).findFirst().orElseThrow();
            assertEquals(escolhido[0], marca.jogador(), "a escolha do técnico vale");
            for (EventoJogo e : r.eventos) {
                if (e.minuto() < marca.minuto() || e.lado() != 0 || !e.gol() || e.jogador() == null) continue;
                if (e.jogador().equals(escolhido[0]) && !e.tipo().startsWith("disputa")) {
                    assertTrue(e.tipo().endsWith("_x2"), "gol da estrela vale 2: " + e.tipo());
                    golsEstrela++;
                }
            }
        }
        assertTrue(golsEstrela > 10, "a estrela marcou " + golsEstrela);
    }

    @Test
    void suspensaoTiraUmDeLinhaPor4Minutos() {
        for (int i = 0; i < 200; i++) {
            Usa u = new Usa(c -> c.emCampoAgora(1).stream().filter(a -> a.posicao == Posicao.GOL).map(LigaKings.AtletaKings::chave).findFirst().orElse(null));
            LigaKings.TimeKings b = time("b");
            ResultadoPartida r = MotorKings.simularComCarta(time("a"), b, new Rng(1300 + i), MotorKings.Carta.SANCAO, u);
            EventoJogo s = r.eventos.stream().filter(e -> e.tipo().equals("suspensao") && e.lado() == 1).findFirst().orElseThrow();
            assertEquals(1, s.lado());
            for (LigaKings.AtletaKings x : b.elenco) if (x.nome.equals(s.jogador())) assertNotEquals(Posicao.GOL, x.posicao, "o goleiro não pode ser suspenso");
            EventoJogo fim = r.eventos.stream().filter(e -> e.tipo().equals("suspensao_fim") && e.lado() == 1).findFirst().orElse(null);
            if (fim != null) assertEquals(4, fim.minuto() - s.minuto(), "fora por 4 minutos");
            for (EventoJogo e : r.eventos)
                if (e.gol() && e.lado() == 1 && s.jogador().equals(e.jogador()) && e.minuto() >= s.minuto() && e.minuto() < s.minuto() + 4)
                    fail("suspenso não marca");
            // um a menos durante a suspensão (fora da rampa e do dado, sem cartões no meio)
            int antes = -1, durante = -1;
            for (int[] x : u.emCampoRival) {
                if (x[0] == s.minuto()) antes = x[1];
                if (x[0] == s.minuto() + 1) durante = x[1];
            }
            if (antes == 7 && durante >= 0 && !temCartaoPerto(r, s.minuto())) assertEquals(6, durante, "um a menos");
        }
    }

    /** Kings: trocas ilimitadas e de volta, também o goleiro (requisito: "na Kings tem substituição"). */
    @Test
    void substituicoesIlimitadasEDeVolta() {
        String[] gk = new String[1], reserva = new String[1];
        boolean[] ok = new boolean[3];
        AoVivoKings tecnico = new AoVivoKings() {
            @Override
            public void minuto(ControleKings c) {
                if (c.minuto() == 8) {
                    gk[0] = c.emCampoAgora(0).stream().filter(a -> a.posicao == Posicao.GOL).findFirst().orElseThrow().chave();
                    reserva[0] = c.bancoAgora(0).get(0).chave();
                    ok[0] = c.substituir(0, gk[0], reserva[0]); // o goleiro também sai
                }
                if (c.minuto() == 10) ok[1] = c.substituir(0, reserva[0], gk[0]); // e volta
                if (c.minuto() == 12) {
                    int n = 0;
                    for (int i = 0; i < 6; i++) if (c.substituir(0, c.emCampoAgora(0).get(1).chave(), c.bancoAgora(0).get(0).chave())) n++;
                    ok[2] = n == 6 && c.emCampoAgora(0).stream().anyMatch(a -> a.chave().equals(gk[0]));
                }
            }
        };
        ResultadoPartida r = MotorKings.simularComCarta(time("a"), time("b"), new Rng(77), MotorKings.Carta.GOL_DUPLO, tecnico);
        assertTrue(ok[0] && ok[1], "o goleiro sai e volta");
        assertTrue(ok[2], "sem limite de trocas");
        assertTrue(r.lances.stream().filter(l -> "substituicao".equals(l.tipo) && l.lado == 0).count() >= 8);
    }

    private static boolean temCartaoPerto(ResultadoPartida r, int m) {
        for (EventoJogo e : r.eventos) if (e.lado() == 1 && (e.tipo().equals("amarelo") || e.tipo().equals("vermelho")) && e.minuto() >= m - 6 && e.minuto() <= m + 1) return true;
        return false;
    }
}
