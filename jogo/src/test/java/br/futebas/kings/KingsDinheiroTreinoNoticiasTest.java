package br.futebas.kings;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.historia.Historia;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mensagem;
import br.futebas.mundo.Rng;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.ResultadoPartida;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requisitos (12/11/2026) para a Kings: notícias da Kings na carreira da Kings, dinheiro de
 * verdade (orçamento igual de R$ 1,5 mi, prêmios, caixa nunca negativo, humano não gasta o que não
 * tem), treino na escala da Kings (teto 80) e o Coringa (vira qualquer carta ou rouba a do rival).
 */
class KingsDinheiroTreinoNoticiasTest {

    private static LigaKings ligaDe(Jogo j, String timeId) {
        for (LigaKings l : j.ligasKings()) if (l.time(timeId) != null) return l;
        throw new AssertionError("sem liga para " + timeId);
    }

    private static void caixaNuncaNegativo(Jogo j) {
        for (LigaKings l : j.ligasKings()) for (LigaKings.TimeKings t : l.times)
            assertTrue(t.caixa >= 0, "caixa negativo: " + t.nome + " " + t.caixa);
    }

    // ================================================================ notícias
    @Test
    void carreiraDaKingsTemNoticiasDaKings() {
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 71, null, "kb-2", "T");
        String meu = j.mundo().tecnico.kingsTimeId;
        for (int i = 0; i < 14; i++) j.avancarSemana();
        List<Historia.Noticia> kings = new ArrayList<>();
        for (Historia.Noticia n : j.mundo().historia.noticias)
            if ((n.competicaoId != null && n.competicaoId.startsWith("kings")) || meu.equals(n.clubeId)) kings.add(n);
        assertFalse(kings.isEmpty(), "a carreira da Kings tem notícias da Kings");
        assertTrue(kings.stream().anyMatch(n -> "meu_time".equals(n.tipo) && n.foco == 0 && meu.equals(n.clubeId)), "o resultado do seu time vira notícia");
        assertTrue(kings.stream().anyMatch(n -> n.foco == 1), "notícias da sua liga (zebras, tabela, cartas, draft...)");
        long tipos = kings.stream().map(n -> n.tipo).distinct().count();
        assertTrue(tipos >= 3, "variedade de notícias: " + kings.stream().map(n -> n.tipo).distinct().toList());
        for (Historia.Noticia n : j.mundo().historia.noticias) {
            String t = (n.titulo + " " + (n.texto == null ? "" : n.texto)).toLowerCase(Locale.ROOT);
            assertFalse(t.contains("pênalti shootout") || t.contains("penalti shootout") || t.contains("pênaltis:"), "na Kings é \"shoot out\": " + t);
        }
        // a caixa de entrada também recebe os placares do seu time
        boolean placar = false;
        for (Mensagem x : j.caixaDeEntrada()) if ("resultado".equals(x.tipo)) placar = true;
        assertTrue(placar, "o placar do jogo da Kings vai para a caixa de entrada");
    }

    @Test
    void foraDaCarreiraDaKingsSoCopasDoMundo() {
        String clube = null;
        for (Jogo.OpcaoClube o : Jogo.clubesDisponiveis(Fixture.banco())) if (clube == null && Integer.valueOf(1).equals(o.divisao())) clube = o.id();
        Jogo j = Jogo.novoJogo(Fixture.banco(), 72, clube, "T");
        for (int i = 0; i < 8; i++) j.avancarSemana();
        for (Historia.Noticia n : j.mundo().historia.noticias) {
            boolean doKings = n.competicaoId != null && n.competicaoId.startsWith("kings");
            if (doKings) assertTrue(n.competicaoId.startsWith("kings-mundial"), "carreira de clube: só as Copas do Mundo da Kings: " + n.titulo);
        }
    }

    @Test
    void leituraDoJogoVeMatchBallShootOutECartas() {
        ResultadoPartida r = new ResultadoPartida();
        r.mandante = "A";
        r.visitante = "B";
        r.eventos.add(new EventoJogo(3, 0, "gol", 1, -1, "Fulano"));
        r.eventos.add(new EventoJogo(10, 0, "carta_usada", 0, -1, "Pênalti"));
        r.eventos.add(new EventoJogo(10, 0, "penalti_carta_gol", 0, -1, "X (A) × Y · GOL"));
        r.eventos.add(new EventoJogo(10, 0, "penalti_gol", 0, -1, "X"));
        r.eventos.add(new EventoJogo(30, 0, "penalti_presidente_gol", 1, -1, "Pres"));
        r.eventos.add(new EventoJogo(36, 0, "fase", -1, -1, "Match ball"));
        r.eventos.add(new EventoJogo(37, 0, "gol", 0, -1, "X"));
        r.eventos.add(new EventoJogo(38, 0, "gol", 0, -1, "X"));
        r.golsMandante = 3;
        r.golsVisitante = 2;
        var l = br.futebas.historia.NoticiasKings.ler(r);
        assertTrue(l.matchBall());
        assertEquals(1, l.liderAos36(), "aos 36 o B vencia 2 x 1");
        assertEquals(0, l.vencedor());
        assertEquals(1, l.cartas().size());
        assertEquals("GOL", l.cartas().get(0)[2], "a carta Pênalti deu gol");
        assertEquals("GOL", l.presidente()[1]);
        assertEquals(3, l.golsPorAtleta().get("0|X"), "hat-trick do X");
        assertFalse(l.shootout());
    }

    // ================================================================ dinheiro
    @Test
    void orcamentoPremiosECaixaNuncaNegativo() {
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 73, null, "kb-1", "T");
        int semanas = 0;
        // a temporada inteira e o começo da seguinte (draft com wildcards pagos pela IA)
        while (j.ano() < 2027 || j.mundo().semana < 6) {
            j.avancarSemana();
            caixaNuncaNegativo(j);
            assertTrue(++semanas < 70, "não avançou");
        }
        LigaKings liga = ligaDe(j, "kb-1");
        for (LigaKings.TimeKings t : liga.times) {
            assertEquals(2027, t.anoOrcamento, "orçamento da temporada nova");
            assertEquals(Kings.ORCAMENTO_TEMPORADA, t.total(2026, "orcamento"), "o mesmo orçamento para todos: " + t.nome);
            assertEquals(Kings.ORCAMENTO_TEMPORADA, t.total(2027, "orcamento"));
        }
        // prêmio do campeão de cada competição encerrada da liga em 2026
        int premios = 0;
        for (Historia.Titulo tit : j.mundo().historia.titulos) {
            if (tit.ano != 2026 || tit.competicaoId == null || liga.time(tit.campeaoId) == null) continue;
            long p = DinheiroKings.premio(tit.competicaoId);
            if (p == 0) continue;
            LigaKings.TimeKings c = liga.time(tit.campeaoId);
            final String comp = tit.competicaoId;
            assertTrue(c.extrato.stream().anyMatch(x -> "premio".equals(x.tipo) && x.valor == p && x.ano == 2026),
                    "prêmio de " + comp + " para " + c.nome);
            premios++;
        }
        assertTrue(premios >= 2, "Split e Kings Cup dão prêmio (" + premios + ")");
        assertEquals(500_000L, DinheiroKings.premio("kings-brasil-split1"));
        assertEquals(300_000L, DinheiroKings.premio("kings-brasil-cup"));
        assertEquals(1_000_000L, DinheiroKings.premio(Kings.MUNDIAL_CLUBES));
        // a IA gastou nos wildcards do 2º ano e nunca ficou negativa
        long gastoIA = 0;
        for (LigaKings.TimeKings t : liga.times) if (!t.humano) gastoIA -= t.total(2027, "contratacao");
        assertTrue(gastoIA > 0, "a IA paga os wildcards");
    }

    @Test
    void humanoNaoFazPropostaQueNaoPodePagar() {
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 74, null, "kb-1", "T");
        j.avancarSemana();
        j.avancarSemana();
        LigaKings liga = ligaDe(j, "kb-1");
        LigaKings.TimeKings eu = liga.time("kb-1");
        Kings k = j.simulador().kings;
        // abre espaço no elenco
        while (eu.elenco.size() > 8) {
            LigaKings.AtletaKings a = eu.elenco.stream().filter(x -> !x.presidente).reduce((x, y) -> y).orElseThrow();
            eu.elenco.remove(a);
            if (a.jogadorId != null && j.mundo().jogador(a.jogadorId) != null) j.mundo().jogador(a.jogadorId).kingsTimeId = null;
        }
        List<Jogador> mercado = j.mercadoKings(50);
        assertTrue(mercado.size() >= 2, "há elegíveis no mercado da Kings");
        Jogador x = mercado.get(0), y = mercado.get(1);
        long cx = k.custoWildcard(x), cy = k.custoWildcard(y);
        assertTrue(cx > 0 && cy > 0, "proposta custa (salário da temporada)");
        // sem caixa: não sai
        eu.caixa = cx - 1;
        assertFalse(j.contratarNaKings(x.id), "não pode fazer proposta acima do caixa");
        // com o caixa exato: sai e fica reservado; a 2ª não cabe
        eu.caixa = cx;
        assertTrue(j.contratarNaKings(x.id));
        assertEquals(cx, DinheiroKings.comprometido(liga, eu));
        assertEquals(0, j.caixaKingsDisponivel());
        assertFalse(j.contratarNaKings(y.id), "o reservado conta: não dá para gastar duas vezes");
        // a resposta: se aceitar, sai do caixa (nunca negativo)
        for (int i = 0; i < 3; i++) {
            j.avancarSemana();
            caixaNuncaNegativo(j);
        }
        assertTrue(eu.caixa == 0 || eu.caixa == cx, "ou pagou tudo ou nada: " + eu.caixa);
        assertTrue(DinheiroKings.debitar(j.mundo(), eu, "contratacao", "teste", 0));
        assertFalse(DinheiroKings.debitar(j.mundo(), eu, "contratacao", "teste", eu.caixa + 1), "débito acima do caixa é recusado");
    }

    // ================================================================ treino
    @Test
    void treinoMudaAForcaDentroDoTeto() {
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 75, null, "kb-1", "T");
        // pelo foco e pela idade
        assertTrue(TreinoKings.delta(20, TreinoKings.PRINCIPAIS) > TreinoKings.delta(20, TreinoKings.SECUNDARIAS));
        assertTrue(TreinoKings.delta(36, TreinoKings.SECUNDARIAS) > TreinoKings.delta(36, TreinoKings.PRINCIPAIS), "secundárias seguram os veteranos");
        assertTrue(TreinoKings.delta(36, null) < 0, "veterano cai mesmo treinando");
        LigaKings.AtletaKings jovem = new LigaKings.AtletaKings();
        jovem.nome = "Jovem";
        jovem.anoNasc = j.ano() - 19;
        jovem.forca = 79.95;
        jovem.treino = TreinoKings.PRINCIPAIS;
        for (int i = 0; i < 20; i++) TreinoKings.treinar(j.mundo(), jovem);
        assertEquals(Kings.FORCA_MAX, jovem.forca, 1e-9, "teto 80 da Kings");
        LigaKings.AtletaKings pres = new LigaKings.AtletaKings();
        pres.nome = "Pres";
        pres.presidente = true;
        pres.forca = 75;
        pres.anoNasc = j.ano() - 20;
        TreinoKings.treinar(j.mundo(), pres);
        assertEquals(75, pres.forca, 1e-9, "presidente que joga: força fixa");
        LigaKings.AtletaKings velho = new LigaKings.AtletaKings();
        velho.nome = "Velho";
        velho.anoNasc = j.ano() - 38;
        velho.forca = 70;
        for (int i = 0; i < 52; i++) TreinoKings.treinar(j.mundo(), velho);
        assertTrue(velho.forca < 70 && velho.forca > 60, "veterano cai devagar: " + velho.forca);
        // o humano escolhe o foco (o mesmo da tela de treino do futebol) e a força muda com as semanas
        j.avancarSemana();
        j.avancarSemana(); // a temporada (e o elenco) começa na 2ª semana
        LigaKings.TimeKings eu = j.meuTimeKings();
        LigaKings.AtletaKings alvo = eu.elenco.stream().filter(a -> !a.presidente).findFirst().orElseThrow();
        j.definirTreinoKings(alvo.chave(), TreinoKings.PRINCIPAIS);
        assertEquals(TreinoKings.PRINCIPAIS, alvo.treino);
        j.definirTreinoKingsElenco(TreinoKings.SECUNDARIAS);
        for (LigaKings.AtletaKings a : eu.elenco) assertEquals(TreinoKings.SECUNDARIAS, a.treino);
        j.definirTreinoKingsElenco(null);
        double antes = alvo.forca;
        LigaKings liga = ligaDe(j, eu.id);
        for (int i = 0; i < 10; i++) TreinoKings.semana(j.mundo(), liga);
        assertNotEquals(antes, alvo.forca, "o treino mexe na força");
        for (LigaKings.TimeKings t : liga.times) for (LigaKings.AtletaKings a : t.elenco)
            assertTrue(a.forca <= Kings.FORCA_MAX + 1e-9 || a.presidente, "teto 80: " + a.nome + " " + a.forca);
    }

    // ================================================================ Coringa
    @Test
    void coringaViraACartaEscolhidaOuRoubaADoRival() {
        int roubou = 0;
        for (int i = 0; i < 40; i++) {
            final boolean escolher = i % 2 == 0;
            AoVivoKings u = new AoVivoKings() {
                boolean usou;

                @Override
                public void minuto(ControleKings c) {
                    if (!usou && c.podeUsarCarta(0)) usou = c.usarCarta(0, escolher ? MotorKings.Carta.PENALTI : null, null);
                }
            };
            ResultadoPartida r = MotorKings.simularComCarta(MotorKingsCartasTest.time("a"), MotorKingsCartasTest.time("b"), new Rng(900 + i),
                    MotorKings.Carta.CORINGA, u);
            EventoJogo vira = r.eventos.stream().filter(e -> e.tipo().equals("carta_usada") && e.lado() == 0 && e.jogador().startsWith("Coringa →"))
                    .findFirst().orElseThrow();
            if (escolher) {
                assertEquals("Coringa → Pênalti", vira.jogador());
                assertTrue(r.eventos.stream().anyMatch(e -> e.tipo().startsWith("penalti_carta_") && e.lado() == 0), "o Coringa virou Pênalti");
            } else {
                String sorteadaRival = r.eventos.stream().filter(e -> e.tipo().equals("carta_sorteada") && e.lado() == 1).findFirst().orElseThrow().jogador();
                boolean rivalUsouAntes = r.eventos.indexOf(r.eventos.stream().filter(e -> e.tipo().equals("carta_usada") && e.lado() == 1).findFirst().orElse(vira))
                        < r.eventos.indexOf(vira);
                if (!rivalUsouAntes) {
                    assertEquals("Coringa → " + sorteadaRival, vira.jogador(), "rouba a carta não usada do rival");
                    assertTrue(r.eventos.stream().noneMatch(e -> e.tipo().equals("carta_usada") && e.lado() == 1), "o rival fica sem carta");
                    roubou++;
                }
            }
        }
        assertTrue(roubou > 0, "o Coringa roubou alguma vez");
    }
}
