package br.futebas.mundo;

import br.futebas.Balanceamento;
import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Torneio;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Sub-20: competições espelho, pirâmide própria, base pela peneira e juniores no profissional. */
class Sub20Test {

    @Test
    void piramidePropriaEJuniores() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 77, "bra-1-01", "Técnico Teste");
        Mundo m = j.mundo();
        Torneio a20 = j.competicaoSub20("bra-1"), b20 = j.competicaoSub20("bra-2");
        assertNotNull(a20, "Série A sub-20 existe");
        assertNotNull(b20);
        assertEquals("bra-1-sub20", a20.id);
        assertEquals(new HashSet<>(m.torneio("bra-1").participantes), new HashSet<>(a20.participantes), "2026 espelha o profissional");
        assertNotNull(j.competicaoSub20("bra-copa"), "copa sub-20");
        assertNotNull(j.competicaoSub20("conmebol-libertadores"), "continental sub-20");
        assertNotNull(j.competicaoSub20("eng-1"), "liga europeia sub-20 (ago–mai)");
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe) continue;
            assertTrue(c.base.size() >= Balanceamento.SUB20_BASE_MINIMA, "todo clube tem base: " + c.id + " " + c.base.size());
            assertTrue(j.elencoSub20(c.id).size() >= 11, "sub-20 com 11: " + c.id);
        }
        for (int id : m.clube("bra-1-01").base) assertTrue(j.talentoEstimado(id) >= 1 && j.talentoEstimado(id) <= 10);

        // júnior escalado no profissional pelo técnico
        List<Jogador> elenco = j.elenco("bra-1-01");
        List<Integer> tit = new ArrayList<>();
        Jogador junior = null;
        for (int id : m.clube("bra-1-01").base) if (m.jogador(id).idade(m.ano) >= 16 && (junior == null || m.jogador(id).forca > junior.forca)) junior = m.jogador(id);
        assertNotNull(junior);
        tit.add(junior.id);
        for (Jogador x : elenco) if (tit.size() < 11 && x.disponivel()) tit.add(x.id);
        j.usarSub20NaEscalacao(true); // a caixa "Sub-20" da escalação
        assertTrue(j.definirEscalacao(tit, List.of()), "júnior pode ser titular no profissional");

        j.avancarAteFimDaTemporada();
        Torneio a20v = null, b20v = null;
        for (Torneio t : m.torneiosSub20Anteriores) {
            if (t.id.equals("bra-1-sub20") && t.ano == 2026) a20v = t;
            if (t.id.equals("bra-2-sub20") && t.ano == 2026) b20v = t;
        }
        assertNotNull(a20v);
        assertNotNull(b20v);
        assertTrue(a20v.encerrado && a20v.campeao != null);
        Torneio a20n = j.competicaoSub20("bra-1");
        assertEquals(2027, a20n.ano);
        int reb = a20v.formato.rebaixados, prom = b20v.formato.promovidos;
        List<String> cl = a20v.classificacao;
        for (int i = 0; i < reb; i++) assertFalse(a20n.participantes.contains(cl.get(cl.size() - 1 - i)), "rebaixado no sub-20 sai");
        for (int i = 0; i < prom; i++) assertTrue(a20n.participantes.contains(b20v.classificacao.get(i)), "promovido no sub-20 sobe");
        // a pirâmide sub-20 é própria: diferente da profissional
        Set<String> pro = new HashSet<>(m.torneio("bra-1").participantes);
        assertNotEquals(pro, new HashSet<>(a20n.participantes), "sub-20 não segue o profissional");
        assertTrue(j.historia().titulos.stream().anyMatch(t -> "sub20".equals(t.ambito)), "títulos sub-20 na história");
    }
}
