package br.futebas.ia;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Origem;
import br.futebas.mundo.StatusJogador;
import br.futebas.mundo.TipoContrato;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Diretoria (campeão fica satisfeito), empréstimo para o clube do técnico e moral inicial variada. */
class TemporadaDiretoriaTest {

    @Test
    void campeaoNuncaTerminaComADiretoriaInsatisfeita() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 7, null, null);
        int verificados = 0;
        for (int ano = 0; ano < 3; ano++) {
            j.avancarAteFimDaTemporada();
            for (Torneio t : j.mundo().torneiosAnteriores) {
                if (t.campeao == null || !"nacional".equals(t.ambito) || !"ano".equals(t.calendario)) continue;
                Clube c = j.clube(t.campeao);
                if (c.virtual) continue;
                verificados++;
                assertTrue(c.confiancaDiretoria >= 75, c.nome + " campeão da " + t.nome + " com confiança " + c.confiancaDiretoria);
                assertTrue(c.notaTemporada >= 75);
            }
        }
        assertTrue(verificados > 0);
    }

    @Test
    void moralInicialVariaEntreJogadores() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 11, "bra-1-01", "T");
        Set<Integer> morais = new HashSet<>();
        for (Jogador x : j.mundo().jogadores) if (x.origem == Origem.REAL) morais.add(x.moral);
        assertTrue(morais.size() >= 15, "valores de moral distintos: " + morais.size());
    }

    @Test
    void pedirJogadorEmprestado() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 12, "bra-1-01", "T");
        Clube meu = j.meuClube();
        meu.dinheiro = 50_000_000;
        Jogador alvo = null;
        Clube dono = null;
        // garante um clube da IA com elenco cheio (o banco fictício tem elencos curtos)
        Clube cheio = null;
        for (Clube c : j.mundo().clubes.values()) if (!c.virtual && !c.humano && c.divisao > 0 && (cheio == null || c.elenco.size() > cheio.elenco.size())) cheio = c;
        for (int i = 0; cheio.elenco.size() < 18; i++) {
            Jogador x = j.mundo().novoJogador();
            x.nome = "Reserva " + i;
            x.anoNasc = 1975;
            x.forca = 30 + i;
            x.status = StatusJogador.PROFISSIONAL;
            x.clubeId = cheio.id;
            x.contrato = br.futebas.mundo.Contrato.profissional(1000, br.futebas.mundo.Contrato.semanaAbsoluta(1997, 52), 10000);
            cheio.elenco.add(x.id);
        }
        for (Clube c : java.util.List.of(cheio)) {
            Jogador pior = null;
            for (int id : c.elenco) {
                Jogador x = j.jogador(id);
                if (x.contrato != null && x.contrato.tipo == TipoContrato.PROFISSIONAL && x.status == StatusJogador.PROFISSIONAL
                        && (pior == null || x.forca < pior.forca)) pior = x;
            }
            if (pior != null) {
                alvo = pior;
                dono = c;
                break;
            }
        }
        assertNotNull(alvo, "há um reserva para pedir emprestado");
        long caixa = meu.dinheiro;
        long taxa = j.taxaEmprestimo(alvo.id);
        assertTrue(taxa > 0);
        Jogo.ResultadoNegociacao r = j.pedirEmprestado(alvo.id);
        assertTrue(r.aceito(), r.mensagem());
        // assíncrono: o dono responde em até 7 dias; depois a confirmação final
        var p = j.negociacaoPor(alvo.id);
        assertEquals("aguardando", p.estado);
        for (int k = 0; k < 2 && "aguardando".equals(p.estado); k++) j.avancarSemana();
        if ("expirada".equals(p.estado)) return; // 10%: o dono não respondeu
        // o pedido já é a confirmação do técnico (sem confirmação dupla): aceito, fecha sozinho
        boolean manual = "aguardando_confirmacao".equals(p.estado);
        caixa = meu.dinheiro;
        if (manual) j.confirmarNegocio(p.id);
        if ("aguardando_janela".equals(p.estado)) while (!"concluida".equals(p.estado) && j.ano() == 2026) j.avancarSemana();
        assertEquals("concluida", p.estado, p.mensagem);
        assertEquals(meu.id, alvo.clubeId);
        assertTrue(meu.elenco.contains(alvo.id));
        assertFalse(dono.elenco.contains(alvo.id));
        assertEquals(TipoContrato.EMPRESTIMO, alvo.contrato.tipo);
        if (manual) assertEquals(caixa - taxa, meu.dinheiro); // fechado na semana: o caixa também teve salários
        // o titular do dono não é emprestado
        Jogador titular = null;
        for (int id : dono.elenco) {
            Jogador x = j.jogador(id);
            if (titular == null || x.forca > titular.forca) titular = x;
        }
        assertFalse(j.pedirEmprestado(titular.id).aceito());
        // volta ao dono na virada do ano
        j.avancarAteFimDaTemporada();
        assertEquals(dono.id, alvo.clubeId, "o empréstimo termina com a temporada");
    }
}
