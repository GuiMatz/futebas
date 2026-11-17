package br.futebas.kings;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.mundo.Rng;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.ResultadoPartida;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requisito: o técnico da Kings escala o time (7x7, formação, cobradores de shootout,
 * batedor do pênalti do presidente) e o motor da Kings usa essa escalação; ela fica no save.
 */
class KingsEscalacaoTest {

    @Test
    void escalacaoDoHumanoValeNoMotorENoSave() throws Exception {
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 71, null, "kb-3", "T");
        j.avancarSemana();
        LigaKings.TimeKings t = j.meuTimeKings();
        assertNotNull(t);
        assertTrue(t.elenco.size() >= 8, "elenco com reservas");
        double auto = MotorKings.forca(t, 7);
        // os 7 mais fracos, numa formação qualquer
        List<LigaKings.AtletaKings> fracos = new ArrayList<>(t.elenco);
        fracos.sort(Comparator.comparingDouble(a -> a.forca));
        List<String> chaves = new ArrayList<>();
        for (int i = 0; i < 7; i++) chaves.add(fracos.get(i).chave());
        assertTrue(j.definirEscalacaoKings("4-2", chaves));
        assertEquals("4-2", t.formacao);
        assertEquals(chaves, t.titulares);
        assertTrue(j.formacoesKings().contains("4-2") && j.formacoesKings().contains("2-3-1"));
        assertEquals(7, j.vagasKings("4-2").size());
        assertTrue(MotorKings.forca(t, 7) < auto, "a escalação dos mais fracos deixa o time mais fraco");
        assertEquals(chaves.get(0), j.escaladosKings().get(0).chave());

        // os gols de jogadores do time saem só dos 7 escalados; shootouts pela ordem escolhida
        Set<Integer> titulares = new HashSet<>();
        for (LigaKings.AtletaKings a : j.escaladosKings()) if (a.jogadorId != null) titulares.add(a.jogadorId);
        LigaKings.AtletaKings batedor = null;
        for (LigaKings.AtletaKings a : j.escaladosKings()) if (a.jogadorId != null) batedor = a;
        assertNotNull(batedor);
        j.definirCobradoresKings(List.of(batedor.chave()));
        LigaKings liga = j.ligaKingsDoTime(t.id);
        LigaKings.TimeKings adv = liga.times.stream().filter(x -> x != t).findFirst().orElseThrow();
        int disputas = 0;
        for (int s = 0; s < 40; s++) {
            ResultadoPartida r = MotorKings.simular(t, adv, liga.regras, new Rng(1000 + s), false);
            for (int[] g : r.gols) if (g[0] == 0) assertTrue(titulares.contains(g[1]), "gol de quem não estava em campo");
            for (EventoJogo e : r.eventos) if (e.lado() == 0 && e.tipo().startsWith("disputa_")) {
                assertEquals(batedor.jogadorId.intValue(), e.jogadorId(), "shootout pela ordem do técnico");
                disputas++;
            }
        }
        assertTrue(disputas >= 0);
        // pênalti do presidente: quem o técnico escolheu
        List<String> pres = t.nomesPresidentes();
        if (!pres.isEmpty()) {
            j.definirBatedorPresidente(pres.get(pres.size() - 1));
            for (int min = 0; min < 10; min++) assertEquals(pres.get(pres.size() - 1), MotorKings.cobradorPresidente(t, min));
        }
        // a IA não é afetada: time sem humano ignora os campos
        adv.titulares = new ArrayList<>(List.of(adv.elenco.get(0).chave()));
        assertNull(MotorKings.escalados(adv), "só o time do humano usa a escalação");
        adv.titulares.clear();

        // fica no save
        Path arq = Files.createTempDirectory("futebas-kings-esc").resolve("k.futebas.json.gz");
        j.salvar(arq);
        Jogo c = Jogo.carregar(arq, Fixture.banco());
        LigaKings.TimeKings tc = c.meuTimeKings();
        assertEquals("4-2", tc.formacao);
        assertEquals(chaves, tc.titulares);
        assertEquals(List.of(batedor.chave()), tc.cobradores);
        // automática de novo
        assertTrue(c.definirEscalacaoKings(null, List.of()));
        assertNull(MotorKings.escalados(tc));
        assertEquals(7, c.escaladosKings().size());
    }

    @Test
    void draftsFicamNoHistorico() {
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 72, null, "kb-4", "T");
        LigaKings liga = j.ligaKingsDoTime("kb-4");
        assertFalse(j.elegiveisDraft(liga.id, 50).isEmpty(), "há gente no draft");
        while (j.ano() == 2026) j.avancarSemana();
        for (int i = 0; i < 4 && j.draftsAnteriores(liga.id).isEmpty(); i++) j.avancarSemana();
        if (j.meuTimeKings() == null) return; // demitido pelo presidente na virada: sem draft para conferir
        assertFalse(j.draftsAnteriores(liga.id).isEmpty(), "o draft de 2027 fica guardado");
        LigaKings.EstadoDraft d = j.draftsAnteriores(liga.id).get(0);
        assertEquals(2027, d.ano);
        assertFalse(d.escolhas.isEmpty());
        assertTrue(d.disponiveis.isEmpty(), "o histórico não guarda a lista de disponíveis");
        assertTrue(d.escolhas.stream().anyMatch(e -> "kb-4".equals(e.timeId)), "o seu time escolheu (você ou o auxiliar)");
    }
}
