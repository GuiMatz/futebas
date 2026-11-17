package br.futebas.mundo;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.partida.Escalador;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requisitos (15/11/2026): acabou o "amador em teste" (quem falta no dia de jogo vira reforço
 * de emergência, um jogador gerado comum; saves antigos aposentam os amadores) e a peneira do
 * técnico traz uma turma por temporada: 5 garotos, às vezes 6 ou 7, de 15 a 17 anos.
 */
class ReforcosEPeneiraTest {

    @Test
    void clubeSemElencoContrataReforcosGeradosComuns() {
        Simulador s = Simulador.novo(Fixture.banco(), 7, null, null);
        Clube c = s.m.clube("bra-4-32");
        while (c.elenco.size() > 8) s.mercado.liberar(s.m.jogador(c.elenco.get(0)), c);
        while (!c.base.isEmpty()) s.mercado.liberar(s.m.jogador(c.base.get(0)), c); // sem o sub-20 também
        int antes = c.elenco.size();
        int novos = Complementares.garantir(s, c);
        assertTrue(novos > 0);
        assertEquals(antes + novos, c.elenco.size(), "os reforços entram no elenco profissional");
        assertTrue(Escalador.disponiveis(s.m, c).size() >= Complementares.MINIMO_DIA_DE_JOGO);
        int goleiros = 0;
        for (Jogador j : Escalador.disponiveis(s.m, c)) if (j.posicao == Posicao.GOL) goleiros++;
        assertTrue(goleiros >= Complementares.MINIMO_GOLEIROS);
        for (int id : c.elenco.subList(antes, c.elenco.size())) {
            Jogador j = s.m.jogador(id);
            assertEquals(Origem.GERADO, j.origem);
            assertFalse(j.amador());
            assertFalse(j.nome.contains("Amador"), j.nome);
            assertEquals(2, j.nome.split(" ").length, "nome real do país: " + j.nome);
            assertEquals(TipoContrato.PROFISSIONAL, j.contrato.tipo);
            assertEquals(s.mercado.fimDaTemporada(c), j.contrato.fim, "contrato até o fim da temporada");
            int idade = j.idade(s.m.ano);
            assertTrue(idade >= 18 && idade <= 30);
        }
        assertEquals(0, Complementares.garantir(s, c), "com elenco completo não contrata mais ninguém");
        for (int i = 0; i < 20; i++) s.avancarSemana();
        for (Jogador j : s.m.jogadores) {
            assertFalse(j.amador(), "nenhum amador em teste no mundo novo");
            assertTrue(j.contrato == null || j.contrato.tipo != TipoContrato.TESTE);
        }
        assertTrue(c.amadores.isEmpty());
    }

    @Test
    void saveAntigoAposentaOsAmadoresEmTeste() throws Exception {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 5, "bra-1-01", "T");
        Mundo m = j.mundo();
        Clube meu = j.meuClube();
        // como um save de antes: um amador ativo no clube do técnico (relacionado) e um na reserva
        Jogador ativo = amadorAntigo(m, meu, 1);
        Jogador reserva = amadorAntigo(m, meu, 2);
        meu.amadores.add(ativo.id);
        meu.amadoresReserva.add(reserva.id);
        reserva.status = StatusJogador.DISPENSADO;
        reserva.contrato = null;
        meu.tatica.reservas.add(ativo.id);
        Path arq = Files.createTempDirectory("futebas-amador").resolve("velho.futebas.json.gz");
        j.salvar(arq);

        Jogo c = Jogo.carregar(arq, Fixture.banco());
        Mundo cm = c.mundo();
        Clube cc = c.meuClube();
        assertTrue(cc.amadores.isEmpty() && cc.amadoresReserva.isEmpty());
        for (int id : new int[]{ativo.id, reserva.id}) {
            Jogador x = cm.jogador(id);
            assertNotNull(x, "continua no mundo (histórico)");
            assertEquals(StatusJogador.APOSENTADO, x.status);
            assertNull(x.clubeId);
            assertNull(x.contrato);
            assertFalse(cc.elenco.contains(id));
            assertFalse(cc.tatica.reservas.contains(id));
        }
        for (Jogador x : c.elenco(cc.id)) assertFalse(x.amador());
        assertTrue(cm.mensagens.stream().anyMatch(x -> "Amadores dispensados".equals(x.titulo)));
        c.avancarSemana(); // segue jogando
    }

    private static Jogador amadorAntigo(Mundo m, Clube c, int n) {
        Jogador a = m.novoJogador();
        a.nome = "Amador em teste #" + n + " (" + c.nome + ")";
        a.nomeConhecido = a.nome;
        a.origem = Origem.COMPLEMENTAR_TESTE;
        a.posicao = Posicao.MC;
        a.pais = c.pais;
        a.anoNasc = m.ano - 25;
        Evolucao.criarAtributos(a, 40, m.rng);
        a.status = StatusJogador.PROFISSIONAL;
        a.clubeId = c.id;
        Contrato k = new Contrato();
        k.tipo = TipoContrato.TESTE;
        k.jogosRestantes = 3;
        k.fim = Integer.MAX_VALUE;
        a.contrato = k;
        return a;
    }

    /** Garotos da peneira (na base, fora do Sub-20). */
    private static List<Jogador> peneira(Jogo j) {
        List<Jogador> l = new ArrayList<>();
        for (Jogador x : j.base(j.meuClube().id)) if (!x.noSub20) l.add(x);
        return l;
    }

    @Test
    void peneiraAnualDoTecnicoTrazDeCincoASete() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 92, "bra-1-01", "T");
        Mundo m = j.mundo();
        Clube meu = j.meuClube();
        j.elencoSub20(meu.id);
        int inicial = peneira(j).size();
        assertTrue(inicial >= 5 && inicial <= 7, "turma inicial: " + inicial);
        Jogador escolhido = peneira(j).get(0);
        assertTrue(j.subirAoSub20(escolhido.id));
        int ano = m.ano;
        j.avancarAteFimDaTemporada();
        assertEquals(ano + 1, m.ano);
        List<Jogador> turma = peneira(j);
        assertTrue(turma.size() >= 5 && turma.size() <= 7, "turma da temporada: " + turma.size());
        for (Jogador x : turma) {
            int idade = x.idade(m.ano);
            assertTrue(idade >= 15 && idade <= 17, "idade " + idade);
            assertEquals(meu.pais, x.pais, "nacionalidade do clube");
            assertEquals(Origem.GERADO, x.origem);
        }
        assertTrue(m.mensagens.stream().anyMatch(x -> ("Peneira " + m.ano).equals(x.titulo)), "mensagem da peneira na caixa");
        assertTrue(escolhido.noSub20 && meu.base.contains(escolhido.id), "o escolhido segue no Sub-20");
    }

    @Test
    void peneiraSorteiaCincoNaMaioriaEAsVezesSeisOuSete() {
        int[] cont = new int[8];
        Jogo j = Jogo.novoJogo(Fixture.banco(), 3, "bra-1-01", "T");
        Mundo m = j.mundo();
        Clube meu = j.meuClube();
        j.elencoSub20(meu.id);
        for (int k = 0; k < 200; k++) {
            int n = j.simulador().sub20.peneiraDoTecnico(meu, false);
            cont[n]++;
            for (int id : new ArrayList<>(meu.base)) {
                Jogador x = m.ativo(id);
                if (x != null && !x.noSub20) meu.base.remove(Integer.valueOf(id));
            }
            m.ano++; // outra temporada, outro sorteio
        }
        assertEquals(200, cont[5] + cont[6] + cont[7]);
        assertTrue(cont[5] > cont[6] && cont[6] > cont[7] && cont[7] > 0, "5 mais comum: " + cont[5] + "/" + cont[6] + "/" + cont[7]);
    }

    @Test
    void mesmaSementeMesmaPeneira() {
        assertEquals(nomesDaPeneira(), nomesDaPeneira());
    }

    private static List<String> nomesDaPeneira() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 41, "bra-1-01", "T");
        j.elencoSub20(j.meuClube().id);
        j.avancarAteFimDaTemporada();
        List<String> l = new ArrayList<>();
        for (Jogador x : peneira(j)) l.add(x.nome + "/" + x.anoNasc + "/" + Math.round(x.potencial));
        return l;
    }
}
