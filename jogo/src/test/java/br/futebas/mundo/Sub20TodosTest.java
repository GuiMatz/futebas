package br.futebas.mundo;

import br.futebas.Balanceamento;
import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Torneio;
import br.futebas.partida.Escalador;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requisito (13/11/2026): todo clube tem Sub-20 de 23 (R$ 500 por mês), os clubes das divisões
 * de acesso do estadual jogam um estadual Sub-20, a peneira não escolhida vai embora na virada, o
 * Sub-20 é reposto a cada temporada e só entra na escalação com a caixa "Sub-20" marcada.
 */
class Sub20TodosTest {

    private static int noSub20(Mundo m, Clube c) {
        int n = 0;
        for (int id : c.base) {
            Jogador x = m.ativo(id);
            if (x != null && x.noSub20) n++;
        }
        return n;
    }

    @Test
    void todoClubeTemSub20De23EEstaduaisDeAcesso() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 91, "bra-1-01", "T");
        Mundo m = j.mundo();
        Set<String> nosTorneios = new HashSet<>();
        for (Torneio t : m.torneiosSub20) nosTorneios.addAll(t.participantes);
        assertFalse(nosTorneios.isEmpty());
        for (String id : nosTorneios) {
            Clube c = m.clube(id);
            assertTrue(c.sub20Montado, "Sub-20 montado: " + id);
            assertEquals(Balanceamento.SUB20_ELENCO, j.elencoSub20(id).size(), "23 no Sub-20: " + id);
            for (Jogador x : j.elencoSub20(id)) {
                assertTrue(x.idade(m.ano) >= 16 && x.idade(m.ano) <= 20, "idade de Sub-20: " + x.idade(m.ano));
                assertEquals(StatusJogador.BASE, x.status);
                if (x.contrato.tipo == TipoContrato.BASE) assertEquals(500, x.contrato.salario, "salário do Sub-20");
            }
        }
        // divisão de acesso do estadual também tem Sub-20, com os clubes dela
        Torneio a2 = j.competicaoSub20("est-sp-2");
        assertNotNull(a2, "Paulista A2 Sub-20");
        Set<String> pro2 = new HashSet<>(m.torneio("est-sp-2").participantes);
        assertEquals(pro2, new HashSet<>(a2.participantes), "2026 espelha a A2");
        Torneio a1 = j.competicaoSub20("est-sp");
        for (String id : a2.participantes) assertFalse(a1.participantes.contains(id), "um clube só numa divisão do Sub-20");

        // a tela pede o Sub-20 de um clube que ainda não tem: monta na hora com 23
        Clube semSub20 = null;
        for (Clube c : m.clubes.values()) if (!c.virtual && c.existe && !c.sub20Montado) semSub20 = c;
        if (semSub20 != null) assertEquals(Balanceamento.SUB20_ELENCO, j.elencoSub20(semSub20.id).size());
    }

    @Test
    void peneiraNaoEscolhidaSaiESub20Reposto() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 92, "bra-1-01", "T");
        Mundo m = j.mundo();
        Clube meu = m.clube("bra-1-01");
        assertEquals(Balanceamento.SUB20_ELENCO, j.elencoSub20(meu.id).size());
        // fora da caixa "Sub-20" o time Sub-20 não está disponível para o profissional
        Set<Integer> disp = new HashSet<>();
        for (Jogador x : Escalador.disponiveis(m, meu)) disp.add(x.id);
        for (Jogador x : j.elencoSub20(meu.id)) assertFalse(disp.contains(x.id), "Sub-20 escondido sem a caixa");
        j.usarSub20NaEscalacao(true);
        disp.clear();
        for (Jogador x : Escalador.disponiveis(m, meu)) disp.add(x.id);
        boolean algum = false;
        for (Jogador x : j.elencoSub20(meu.id)) if (x.disponivel() && x.idade(m.ano) >= 16) algum |= disp.contains(x.id);
        assertTrue(algum, "com a caixa, o Sub-20 pode ser relacionado");
        j.usarSub20NaEscalacao(false);

        // juniores da peneira (fora do Sub-20) que o técnico não escolheu
        List<Integer> peneira = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            Jogador x = m.ativo(j.elencoSub20(meu.id).get(k).id);
            assertTrue(j.devolverABase(x.id));
            peneira.add(x.id);
        }
        Jogador escolhido = m.ativo(peneira.remove(0));
        assertTrue(j.subirAoSub20(escolhido.id), "subiu de novo ao Sub-20");
        assertEquals(500, escolhido.contrato.salario);
        j.avancarAteFimDaTemporada();
        for (int id : peneira) {
            assertNull(m.ativo(id), "júnior não escolhido saiu do mundo");
            assertFalse(meu.base.contains(id));
        }
        assertTrue(m.ativo(escolhido.id) != null && escolhido.clubeId != null && escolhido.clubeId.equals(meu.id), "o escolhido continua no clube");
        // depois da virada todo Sub-20 voltou a ter 23
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe || !c.sub20Montado) continue;
            assertTrue(noSub20(m, c) >= Balanceamento.SUB20_ELENCO, "Sub-20 reposto: " + c.id + " " + noSub20(m, c));
        }
    }
}
