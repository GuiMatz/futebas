package br.futebas.competicao;

import br.futebas.dados.BancoMundo;
import br.futebas.ferramentas.LongRun;
import br.futebas.mundo.Simulador;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Formatos novos no banco real (pulado se {@code dados/mundo.sqlite} não existe): Champions com
 * fase de liga de 36 (8 jogos cada), playoff dos 9º–24º e mata-mata; Copa do Brasil 2026 (126
 * clubes, Série A entrando na 5ª fase).
 */
class FormatosReaisTest {

    @Test
    void championsECopaDoBrasil() {
        Path banco = LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite");
        Assumptions.assumeTrue(Files.exists(banco), "sem o banco real");
        Simulador s = Simulador.novo(BancoMundo.carregar(banco), 2027, null, null);
        Torneio copa = null, ucl = null;
        for (int k = 0; k < 120; k++) {
            s.avancarSemana();
            for (Torneio t : s.m.torneios) {
                if (t.id.equals("bra-copa") && t.ano == 2026) copa = t;
                if (t.id.equals("uefa-champions") && t.ano == 2026) ucl = t;
            }
            for (Torneio t : s.m.torneiosAnteriores) {
                if (t.id.equals("bra-copa") && t.ano == 2026) copa = t;
                if (t.id.equals("uefa-champions") && t.ano == 2026) ucl = t;
            }
            if (copa != null && copa.encerrado && ucl != null && ucl.encerrado) break;
        }
        assertNotNull(copa, "Copa do Brasil 2026");
        assertTrue(copa.encerrado && copa.campeao != null);
        assertTrue(copa.participantes.size() >= 120, "Copa do Brasil com ~126 clubes: " + copa.participantes.size());
        // clubes da Série A só estreiam na 5ª fase: no máximo 2 jogos antes das oitavas
        Set<String> serieA = new HashSet<>();
        for (Torneio t : s.m.torneiosAnteriores) if (t.id.equals("bra-1") && t.ano == 2026) serieA.addAll(t.participantes);
        for (Torneio t : s.m.torneios) if (t.id.equals("bra-1") && t.ano == 2026) serieA.addAll(t.participantes);
        assertEquals(20, serieA.size());
        assertTrue(copa.fases.size() >= 5);
        for (FaseExec f : copa.fases.subList(0, 4)) for (String id : f.clubes) assertFalse(serieA.contains(id), "Série A antes da 5ª fase: " + id + " em " + f.nome);
        for (String id : serieA) assertTrue(copa.fases.get(4).clubes.contains(id), "Série A entra na 5ª fase: " + id);

        assertNotNull(ucl, "Champions 2026 (calendário único)");
        assertTrue(ucl.encerrado && ucl.campeao != null);
        FaseExec liga = null;
        for (FaseExec f : ucl.fases) if ("liga_suica".equals(f.tipo)) liga = f;
        assertNotNull(liga, "fase de liga");
        assertEquals(36, liga.clubes.size(), "fase de liga com 36");
        List<LinhaTabela> tab = ucl.tabela(liga.indice, 0);
        assertEquals(36, tab.size());
        for (LinhaTabela l : tab) assertEquals(8, l.jogos, "8 jogos na fase de liga: " + l.clubeId);
        FaseExec playoff = ucl.fases.get(liga.indice + 1), oitavas = ucl.fases.get(liga.indice + 2);
        assertEquals(16, playoff.clubes.size(), "play-off com os 9º–24º");
        for (int i = 0; i < 16; i++) assertEquals(tab.get(8 + i).clubeId.equals(tab.get(8 + i).clubeId), playoff.clubes.contains(tab.get(8 + i).clubeId));
        assertEquals(16, oitavas.clubes.size(), "oitavas: 8 diretos + 8 do play-off");
        for (int i = 0; i < 8; i++) assertTrue(oitavas.clubes.contains(tab.get(i).clubeId), "top 8 direto às oitavas");
    
        // copas regionais com clubes da região
        for (String id : java.util.List.of("bra-copa-nordeste", "bra-copa-verde", "bra-copa-sul-sudeste")) {
            Torneio r = null;
            for (Torneio t : s.m.torneiosAnteriores) if (t.id.equals(id) && t.ano == 2026) r = t;
            for (Torneio t : s.m.torneios) if (t.id.equals(id) && t.ano == 2026) r = t;
            assertNotNull(r, id + " 2026 existe");
            assertTrue(r.participantes.size() >= 8, id + ": " + r.participantes.size());
            for (String c : r.participantes) assertTrue(MontadorTemporada.REGIONAIS.get(id).contains(s.m.clube(c).uf), id + " com clube de fora: " + c);
        }
        // Série D 2027 renovada pelos estaduais
        while (s.m.ano == 2026) s.avancarSemana();
        Torneio d26 = null, d27 = null;
        for (Torneio t : s.m.torneiosAnteriores) if (t.id.equals("bra-4") && t.ano == 2026) d26 = t;
        for (Torneio t : s.m.torneios) if (t.id.equals("bra-4") && t.ano == 2027) d27 = t;
        assertNotNull(d26);
        assertNotNull(d27);
        final Torneio d26f = d26;
        long novos = d27.participantes.stream().filter(x -> !d26f.participantes.contains(x)).count();
        assertTrue(novos >= 40, "Série D 2027 com clubes novos vindos dos estaduais: " + novos);
}
}
