package br.futebas.partida;

import br.futebas.Balanceamento;
import br.futebas.ferramentas.MedirTaticas;
import br.futebas.ferramentas.MedirTaticas.Medida;
import br.futebas.mundo.Posicao;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cada alavanca tática mexe na direção certa e com tamanho moderado (muitos jogos simulados). */
class TaticasTest {
    static final int N = 20000;

    @Test
    void marcacaoPesadaCedeMenosMasFazMaisFaltasECartoes() {
        Medida normal = MedirTaticas.medir(N, 3, 70, 70, t -> t.marcacao = Marcacao.NORMAL, null);
        Medida pesada = MedirTaticas.medir(N, 3, 70, 70, t -> t.marcacao = Marcacao.PESADA, null);
        Medida leve = MedirTaticas.medir(N, 3, 70, 70, t -> t.marcacao = Marcacao.LEVE, null);
        assertTrue(pesada.gc() < normal.gc() - 0.04, "pesada cede menos: " + pesada + " / " + normal);
        assertTrue(pesada.gc() > normal.gc() * 0.85, "mas não demais: " + pesada);
        assertTrue(pesada.amarelos() > normal.amarelos() * 1.4, "mais cartões");
        assertTrue(pesada.faltas() > normal.faltas() + 3, "mais faltas");
        assertTrue(leve.amarelos() < normal.amarelos() * 0.8, "leve: menos cartões");
        assertTrue(Math.abs(pesada.pontos() - normal.pontos()) < 0.12, "efeito moderado nos pontos");
    }

    @Test
    void concentrarRendeContraOSetorFraco() {
        var laterais = MedirTaticas.setor(-10, Posicao.LE, Posicao.LD);
        Medida eq = MedirTaticas.medir(N, 5, 70, 70, t -> t.concentracao = Concentracao.EQUILIBRADO, laterais);
        Medida lados = MedirTaticas.medir(N, 5, 70, 70, t -> t.concentracao = Concentracao.LATERAIS, laterais);
        Medida meio = MedirTaticas.medir(N, 5, 70, 70, t -> t.concentracao = Concentracao.MEIO, laterais);
        assertTrue(lados.gp() > meio.gp() + 0.1, "contra laterais fracos, atacar pelos lados: " + lados + " / " + meio);
        assertTrue(lados.gp() > eq.gp(), "e melhor que o equilibrado");
        assertTrue(lados.cabeca() > eq.cabeca() + 0.03, "mais gols dos jogadores de lado");
        assertTrue(meio.cabeca() < eq.cabeca() - 0.03, "pelo meio: menos gols dos jogadores de lado");
        var zaga = MedirTaticas.setor(-10, Posicao.ZAG, Posicao.VOL);
        Medida meio2 = MedirTaticas.medir(N, 6, 70, 70, t -> t.concentracao = Concentracao.MEIO, zaga);
        Medida lados2 = MedirTaticas.medir(N, 6, 70, 70, t -> t.concentracao = Concentracao.LATERAIS, zaga);
        assertTrue(meio2.gp() > lados2.gp() + 0.1, "contra zaga fraca, atacar pelo meio");
    }

    @Test
    void melhorConcentracaoLeOAdversario() {
        var a = br.futebas.ferramentas.CalibrarMotor.time("A", 70, new br.futebas.mundo.Rng(1), 0);
        var b = br.futebas.ferramentas.CalibrarMotor.time("B", 70, new br.futebas.mundo.Rng(1), 0);
        MedirTaticas.setor(-10, Posicao.LE, Posicao.LD).accept(b);
        assertEquals(Concentracao.LATERAIS, MotorPartida.melhorConcentracao(PerfilTime.de(a.titulares, null), PerfilTime.de(b.titulares, null)));
    }

    @Test
    void estiloMoralEEnergia() {
        Medida eq = MedirTaticas.medir(N, 1, 70, 70, t -> t.estilo = Estilo.EQUILIBRADO, null);
        Medida ret = MedirTaticas.medir(N, 1, 70, 70, t -> t.estilo = Estilo.RETRANCA, null);
        Medida atq = MedirTaticas.medir(N, 1, 70, 70, t -> t.estilo = Estilo.ATAQUE_TOTAL, null);
        assertTrue(ret.gc() < eq.gc() - 0.2 && ret.gp() < eq.gp() - 0.2, "retranca: jogo mais fechado");
        assertTrue(atq.gp() > eq.gp() + 0.1 && atq.gc() > eq.gc() + 0.05, "ataque total: jogo mais aberto");
        double baixa = 1 + Balanceamento.MORAL_PESO * (45 - Balanceamento.MORAL_NEUTRA) / 100.0;
        double alta = 1 + Balanceamento.MORAL_PESO * (85 - Balanceamento.MORAL_NEUTRA) / 100.0;
        Medida mb = MedirTaticas.medir(N, 8, 70, 70, MedirTaticas.condicao(baixa, 100), null);
        Medida ma = MedirTaticas.medir(N, 8, 70, 70, MedirTaticas.condicao(alta, 100), null);
        assertTrue(ma.pontos() > mb.pontos() + 0.08 && ma.pontos() < mb.pontos() + 0.4, "moral: " + ma + " / " + mb);
        Medida cansado = MedirTaticas.medir(N, 9, 70, 70, MedirTaticas.condicao(1 - Balanceamento.ENERGIA_PESO * 0.4, 60), null);
        Medida inteiro = MedirTaticas.medir(N, 9, 70, 70, MedirTaticas.condicao(1, 100), null);
        assertTrue(cansado.pontos() < inteiro.pontos() - 0.08, "energia baixa pesa");
    }
}
