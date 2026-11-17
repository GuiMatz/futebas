package br.futebas.ui;

import br.futebas.ui.Opcoes.AutoSalvar;
import br.futebas.ui.Opcoes.Escala;
import br.futebas.ui.Opcoes.Moeda;
import br.futebas.ui.Opcoes.Noticias;
import br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade;
import br.futebas.ui.modelo.Textos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opções: padrões iguais ao comportamento de antes, gravar/ler e restaurar. Usa um nó de teste. */
class OpcoesTest {
    private static final String NO = "futebas-teste-opcoes";

    @BeforeEach
    void limpo() throws Exception {
        apagarNo();
        Opcoes.usarNo(NO);
    }

    @AfterEach
    void depois() throws Exception {
        apagarNo();
        Opcoes.usarNo(System.getProperty("futebas.prefs", "futebas-teste"));
    }

    private static void apagarNo() throws Exception {
        if (Preferences.userRoot().nodeExists(NO)) Preferences.userRoot().node(NO).removeNode();
    }

    @Test
    void padroesRepetemOComportamentoAntigo() {
        assertEquals(Velocidade.NORMAL, Opcoes.velocidade());
        assertFalse(Opcoes.pausarGolMeu());
        assertFalse(Opcoes.pausarGolSofrido());
        assertTrue(Opcoes.pausarLesao());
        assertTrue(Opcoes.pausarExpulsao());
        assertTrue(Opcoes.taticaNoIntervalo());
        assertTrue(Opcoes.escolherBatedor());
        assertTrue(Opcoes.outrosJogosAoVivo());
        assertTrue(Opcoes.tabelaAoVivo());
        assertFalse(Opcoes.renovacaoAutomaticaNovos());
        assertFalse(Opcoes.confirmarEscalacao());
        assertTrue(Opcoes.escalacaoAntesDoJogo(), "a escalação antes do jogo aparece por padrão");
        assertFalse(Opcoes.delegarAmistosos());
        assertEquals(Noticias.TODAS, Opcoes.noticias());
        assertEquals(Escala.E100, Opcoes.escala());
        assertEquals(Moeda.ABREVIADA, Opcoes.moeda());
        assertTrue(Opcoes.telaCheia());
        assertTrue(Opcoes.fotos());
        assertEquals(AutoSalvar.TEMPORADA, Opcoes.autoSalvar());
        assertEquals(1, Opcoes.vagasAutomaticas());
        assertTrue(Opcoes.confirmarSair());
    }

    @Test
    void gravaELeDeVoltaMesmoSemCache() {
        Opcoes.velocidade(Velocidade.LENTA);
        Opcoes.pausarGolMeu(true);
        Opcoes.pausarLesao(false);
        Opcoes.tabelaAoVivo(false);
        Opcoes.noticias(Noticias.LIGA);
        Opcoes.escala(Escala.E125);
        Opcoes.moeda(Moeda.COMPLETA);
        Opcoes.fotos(false);
        Opcoes.autoSalvar(AutoSalvar.MENSAL);
        Opcoes.vagasAutomaticas(3);
        Opcoes.confirmarSair(false);
        Opcoes.usarNo(NO); // esvazia o cache: lê das preferências
        assertEquals(Velocidade.LENTA, Opcoes.velocidade());
        assertTrue(Opcoes.pausarGolMeu());
        assertFalse(Opcoes.pausarLesao());
        assertFalse(Opcoes.tabelaAoVivo());
        assertEquals(Noticias.LIGA, Opcoes.noticias());
        assertEquals(Escala.E125, Opcoes.escala());
        assertEquals(Moeda.COMPLETA, Opcoes.moeda());
        assertFalse(Opcoes.fotos());
        assertEquals(AutoSalvar.MENSAL, Opcoes.autoSalvar());
        assertEquals(3, Opcoes.vagasAutomaticas());
        assertFalse(Opcoes.confirmarSair());
    }

    @Test
    void vagasFicamEntreUmETres() {
        Opcoes.vagasAutomaticas(9);
        assertEquals(3, Opcoes.vagasAutomaticas());
        Opcoes.vagasAutomaticas(0);
        assertEquals(1, Opcoes.vagasAutomaticas());
    }

    @Test
    void valorInvalidoNasPreferenciasVoltaAoPadrao() {
        Preferences.userRoot().node(NO).put("velocidade", "TURBO");
        Opcoes.usarNo(NO);
        assertEquals(Velocidade.NORMAL, Opcoes.velocidade());
    }

    @Test
    void instantaneaSalvaViraUltraRapida() {
        Opcoes.velocidade(Velocidade.INSTANTANEA);
        assertEquals(Velocidade.ULTRA, Opcoes.velocidade(), "a Instantânea saiu da tela; a preferência antiga vira Ultra rápida");
        assertFalse(Velocidade.visiveis().contains(Velocidade.INSTANTANEA));
        assertEquals(java.util.List.of(Velocidade.LENTA, Velocidade.NORMAL, Velocidade.RAPIDA, Velocidade.ULTRA), Velocidade.visiveis());
        assertTrue(Velocidade.ULTRA.msPorMinuto >= 100 && Velocidade.ULTRA.msPorMinuto <= 150);
        Opcoes.restaurarPadroes();
    }

    @Test
    void restaurarPadroesApagaTudo() {
        Opcoes.velocidade(Velocidade.INSTANTANEA);
        Opcoes.escolherBatedor(false);
        Opcoes.autoSalvar(AutoSalvar.DESLIGADO);
        int[] avisos = {0};
        Opcoes.aoMudar(() -> avisos[0]++);
        Opcoes.restaurarPadroes();
        assertTrue(avisos[0] > 0);
        assertEquals(Velocidade.NORMAL, Opcoes.velocidade());
        assertTrue(Opcoes.escolherBatedor());
        assertEquals(AutoSalvar.TEMPORADA, Opcoes.autoSalvar());
        Opcoes.usarNo(NO);
        assertEquals(Velocidade.NORMAL, Opcoes.velocidade());
    }

    @Test
    void formatoDoDinheiroSegueAOpcao() {
        assertEquals("R$ 46,5 mi", Textos.dinheiro(46_500_000));
        Opcoes.moeda(Moeda.COMPLETA);
        assertEquals("R$ 46.500.000", Textos.dinheiro(46_500_000));
        assertEquals("-R$ 1.200", Textos.dinheiro(-1_200));
    }
}
