package br.futebas.mundo;

import br.futebas.Fixture;
import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Todo jogador real tem fonte; jogadores gerados só usam nomes reais do país (nomes_gerados). */
class FontesENomesTest {

    @Test
    void todoJogadorRealTemFonte() {
        BancoMundo db = Fixture.banco();
        assertEquals(3, db.jogadoresSemFonte, "os 3 reais sem fonte do banco fictício são descartados");
        for (Db.Jogador dj : db.jogadores.values()) if (dj.real()) assertTrue(dj.numFontes() >= 1, dj.nome());
        Simulador s = Simulador.novo(db, 11, null, null);
        s.avancarTemporada();
        for (Jogador j : s.m.jogadores) {
            if (j.origem != Origem.REAL) continue;
            assertNotNull(j.dbId, "jogador real sem id do banco");
            Db.Jogador dj = db.jogadores.get(j.dbId);
            assertNotNull(dj);
            assertTrue(dj.real());
            assertTrue(dj.numFontes() >= 1, "todo jogador real tem pelo menos uma fonte");
            assertEquals(dj.nome(), j.nome, "o nome vem do banco, nunca inventado");
        }
    }

    @Test
    void geradosSoComNomesDoPais() {
        Simulador s = Simulador.novo(Fixture.banco(), 21, null, null);
        for (int i = 0; i < 2; i++) s.avancarTemporada();
        Map<String, Set<String>> pre = new HashMap<>(), sob = new HashMap<>();
        for (Db.Nome n : Fixture.banco().nomes) (n.tipo().equals("prenome") ? pre : sob).computeIfAbsent(n.pais(), k -> new HashSet<>()).add(n.nome());
        Set<String> nomesDb = new HashSet<>();
        for (Db.Jogador dj : Fixture.banco().jogadores.values()) nomesDb.add(dj.nome());
        Pattern rotulo = Pattern.compile("Amador em teste #\\d+ \\(.+\\)");
        int peneira = 0;
        for (Jogador j : s.m.jogadores) {
            switch (j.origem) {
                case REAL -> assertTrue(nomesDb.contains(j.nome));
                case COMPLEMENTAR_TESTE -> assertTrue(rotulo.matcher(j.nome).matches());
                case GERADO -> {
                    if (j.dbId != null) {
                        assertTrue(nomesDb.contains(j.nome), "gerado do banco mantém o nome do banco");
                        continue;
                    }
                    peneira++;
                    String[] partes = j.nome.split(" ");
                    assertEquals(2, partes.length, j.nome);
                    assertTrue(pre.getOrDefault(j.pais, Set.of()).contains(partes[0]), "prenome do país " + j.pais + ": " + j.nome);
                    assertTrue(sob.getOrDefault(j.pais, Set.of()).contains(partes[1]), "sobrenome do país " + j.pais + ": " + j.nome);
                    // júnior da peneira ou do Sub-20 (16 a 20 anos, regra do jogo 13/11/2026) ou reforço de emergência (18 a 30)
                    assertTrue(j.anoNasc >= 2026 - 30, "gerado com idade fora das faixas: " + j.anoNasc);
                }
            }
        }
        assertTrue(peneira > 0, "a peneira revelou juniores");
        assertTrue(s.m.historia.estat(2026).newgens > 0);
    }
}
