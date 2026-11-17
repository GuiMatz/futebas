package br.futebas.kings;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.dados.Db;
import br.futebas.mundo.Jogador;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KingsTest {

    private static void semana(Jogo jogo) {
        if (jogo.meuClube() != null) jogo.meuClube().confiancaDiretoria = 90;
        jogo.avancarSemana();
    }

    @Test
    void elencosReaisDe2026EDepoisDraft() {
        Jogo jogo = Jogo.novoJogo(Fixture.banco(), 31, "bra-1-02", "Técnico Teste");
        LigaKings liga = jogo.ligasKings().stream().filter(l -> l.id.equals("kings-brasil")).findFirst().orElseThrow();
        assertTrue(jogo.assumirTimeKings(liga.times.get(0).id), "no jogo novo pode escolher o time da Kings");
        semana(jogo);
        assertFalse(jogo.assumirTimeKings(liga.times.get(1).id), "durante a carreira, só por convite");
        assertTrue(liga.ativa, "liga que já existe em 2026 começa ativa");
        Set<String> nomesFonte = new HashSet<>();
        for (Db.KingsElenco e : Fixture.banco().kingsElencos) {
            if (e.nomeFonte() != null) nomesFonte.add(e.nomeFonte());
            if (e.jogadorId() != null && Fixture.banco().jogadores.containsKey(e.jogadorId()))
                nomesFonte.add(Fixture.banco().jogadores.get(e.jogadorId()).nomeExibicao());
        }
        for (LigaKings.TimeKings t : liga.times) {
            assertFalse(t.elenco.isEmpty());
            for (LigaKings.AtletaKings a : t.elenco) {
                // elenco real com fonte; vaga aberta na 1ª semana pode ser preenchida por um jogador real do banco (wildcard)
                br.futebas.mundo.Jogador x = a.jogadorId == null ? null : jogo.mundo().jogador(a.jogadorId);
                boolean doBanco = x != null && !x.amador() && x.dbId != null && Fixture.banco().jogadores.containsKey(x.dbId);
                assertTrue(nomesFonte.contains(a.nome) || doBanco, "nome sem fonte: " + a.nome);
            }
        }
        assertNull(jogo.meuClube(), "carreira exclusiva: quem assume a Kings no jogo novo deixa o clube");
        while (jogo.ano() == 2026) semana(jogo);
        assertTrue(liga.campeoes.size() >= 1);
        while (jogo.ano() == 2027 && liga.temporadas < 2) semana(jogo);
        assertEquals(2, liga.temporadas);
        // draft interativo: o técnico escolhe na vez dele
        LigaKings.EstadoDraft e = jogo.estadoDraft();
        assertNotNull(e, "draft esperando o técnico");
        assertEquals(jogo.mundo().tecnico.kingsTimeId, e.vez());
        int escolhidos = 0;
        while (jogo.estadoDraft() != null) {
            int id = jogo.estadoDraft().disponiveis.get(0);
            assertTrue(jogo.escolherNoDraft(id));
            escolhidos++;
            final int fid = id;
            assertTrue(liga.time(jogo.mundo().tecnico.kingsTimeId).elenco.stream().anyMatch(x -> x.jogadorId != null && x.jogadorId == fid));
        }
        assertEquals(LigaKings.class, liga.getClass());
        assertEquals(6, escolhidos, "6 escolhas do técnico");
        LigaKings.TimeKings meu = liga.time(jogo.mundo().tecnico.kingsTimeId);
        for (LigaKings.TimeKings t : liga.times) {
            assertTrue(t.elenco.size() <= Kings.MAX_ELENCO, t.nome);
            assertEquals(6, t.elenco.stream().filter(x -> "draft".equals(x.tipo)).count(), "6 draftados: " + t.nome);
            long wild = t.elenco.stream().filter(x -> "wildcard".equals(x.tipo) || "mantido".equals(x.tipo)).count();
            // wildcard é por proposta (regra do jogo): a IA propõe no começo da temporada; o humano pelo Mercado
            if (t == meu) assertEquals(0, t.elenco.stream().filter(x -> "wildcard".equals(x.tipo)).count(), "as vagas de wildcard do humano ficam abertas");
            else assertTrue(wild >= 3 && wild <= 6, t.nome + ": " + wild);
        }
        assertEquals(Kings.WILDCARDS - meu.elenco.stream().filter(x -> "mantido".equals(x.tipo)).count(), jogo.vagasWildcardKings()[0], "vagas abertas (6 menos os mantidos)");
        // o técnico propõe aos melhores do mercado da Kings; as respostas chegam em até uma semana (clube e jogador)
        int propostas = 0;
        int vagas = jogo.vagasWildcardKings()[0];
        for (Jogador x : jogo.mercadoKings(40)) if (propostas < vagas && jogo.contratarNaKings(x.id)) propostas++;
        assertEquals(vagas, propostas);
        assertFalse(jogo.contratarNaKings(jogo.mercadoKings(80).get(30).id), "as propostas em andamento contam no teto de 12");
        for (int i = 0; i < 3; i++) semana(jogo);
        assertEquals(0, jogo.vagasWildcardKings()[1], "todas as propostas responderam");
        assertTrue(meu.elenco.stream().anyMatch(x -> "wildcard".equals(x.tipo)), "alguém aceitou");
        int alguem = meu.elenco.get(0).jogadorId;
        assertTrue(jogo.dispensarDaKings(alguem));
        assertNull(jogo.jogador(alguem).kingsTimeId);
        for (LigaKings.TimeKings t : liga.times) {
            for (LigaKings.AtletaKings a : t.elenco) {
                assertNotNull(a.jogadorId, "no draft só entram jogadores do mundo");
                Jogador j = jogo.jogador(a.jogadorId);
                assertNotNull(j);
            }
        }
        assertTrue(liga.time(jogo.mundo().tecnico.kingsTimeId).humano);
    }
}
