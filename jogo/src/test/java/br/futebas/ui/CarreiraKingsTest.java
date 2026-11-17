package br.futebas.ui;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.ia.PropostaTecnico;
import br.futebas.kings.LigaKings;
import br.futebas.mundo.Clube;
import br.futebas.ui.modelo.Fluxo;
import br.futebas.ui.telas.Hub;
import br.futebas.ui.telas.TelaDesempregado;
import br.futebas.ui.telas.TelaFimTemporada;
import br.futebas.ui.telas.TelaNovoJogo;
import javafx.application.Platform;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regra do jogo: "ou você assume um time normal ou assume da Kings; você pode mudar de time
 * durante a carreira". Carreira só da Kings por uma temporada inteira (com a central montando),
 * troca clube → Kings e Kings → clube, nunca os dois, e saves antigos com os dois.
 */
class CarreiraKingsTest {
    private static boolean fx;

    @BeforeAll
    static void iniciarFx() throws Exception {
        CountDownLatch l = new CountDownLatch(1);
        try {
            Platform.startup(l::countDown);
            fx = l.await(10, TimeUnit.SECONDS);
        } catch (IllegalStateException jaIniciado) {
            fx = true;
        } catch (Throwable semTela) {
            fx = false;
        }
    }

    private static void naTela(Runnable r) throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch l = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                erro.set(t);
            } finally {
                l.countDown();
            }
        });
        assertTrue(l.await(120, TimeUnit.SECONDS), "a tela respondeu");
        if (erro.get() != null) throw new AssertionError("erro ao montar a tela", erro.get());
    }

    private static LigaKings brasil(Jogo j) {
        return j.ligasKings().stream().filter(l -> l.id.equals("kings-brasil")).findFirst().orElseThrow();
    }

    /** Nunca clube e time da Kings ao mesmo tempo (e o time marcado como humano bate com o técnico). */
    private static void exclusivo(Jogo j) {
        var t = j.mundo().tecnico;
        assertFalse(t.clubeId != null && t.kingsTimeId != null, "clube e Kings ao mesmo tempo");
        int humanos = 0;
        for (LigaKings l : j.ligasKings()) for (LigaKings.TimeKings tk : l.times) if (tk.humano) {
            humanos++;
            assertEquals(t.kingsTimeId, tk.id, "time da Kings humano sem ser o do técnico");
        }
        assertTrue(humanos <= 1);
        int clubes = 0;
        for (Clube c : j.mundo().clubes.values()) if (c.humano) clubes++;
        assertTrue(clubes <= 1);
        if (t.kingsTimeId != null) assertEquals(0, clubes, "técnico da Kings com clube humano");
    }

    @Test
    void jogoNovoSoComUmaEscolha() {
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 5, FluxoTest.clubeSerieA(), "kb-1", "T");
        assertNull(j.meuClube(), "novoJogoKings ignora o clube: carreira exclusiva");
        assertEquals("kb-1", j.mundo().tecnico.kingsTimeId);
        exclusivo(j);
        // escolher a Kings no jogo novo de quem tinha clube deixa o clube
        Jogo c = Jogo.novoJogo(Fixture.banco(), 6, FluxoTest.clubeSerieA(), "T");
        assertNotNull(c.meuClube());
        assertTrue(c.assumirTimeKings(brasil(c).times.get(0).id));
        assertNull(c.meuClube());
        exclusivo(c);
    }

    @Test
    void temporadaInteiraSoComTimeDaKings() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        Jogo j = Jogo.novoJogoKings(Fixture.banco(), 17, null, "kb-2", "Técnico");
        Sessao s = new Sessao(j, "so-kings-temporada");
        assertTrue(s.fluxo.soKings());
        s.fluxo.avancarSemana(); // a temporada da Kings começa na 2ª semana
        assertNotNull(s.fluxo.proximoJogoKings(), "o calendário da Kings tem jogos");
        Fluxo.ResumoTemporada fim = null;
        int rodadas = 0;
        for (int i = 0; i < 120 && fim == null; i++) {
            Fluxo.Rodada r = s.fluxo.jogarProximaRodada(null);
            exclusivo(j);
            fim = r.fimDeTemporada();
            rodadas++;
            if (rodadas % 12 == 1 && s.fluxo.soKings()) {
                naTela(() -> {
                    Hub h = new Hub(s);
                    new Scene(h.raiz(), 1280, 800).getStylesheets().add(App.class.getResource("/br/futebas/ui/tema.css").toExternalForm());
                    h.aoMostrar();
                    for (String p : h.paineis()) {
                        h.selecionar(p);
                        h.raiz().applyCss();
                        h.raiz().layout();
                    }
                });
            }
        }
        assertNotNull(fim, "a temporada da Kings termina");
        assertEquals(2027, j.ano());
        Fluxo.ResumoTemporada resumo = fim;
        if (s.fluxo.soKings()) {
            assertEquals(j.mundo().tecnico.kingsTimeId, resumo.clubeId(), "o resumo é do time da Kings");
            assertFalse(resumo.campanha().isEmpty(), "campanha nas competições da Kings");
        }
        naTela(() -> {
            new Scene(new TelaFimTemporada(s, resumo).raiz(), 1280, 800);
            Hub h = new Hub(s);
            new Scene(h.raiz(), 1280, 800);
            h.aoMostrar();
            for (String p : h.paineis()) h.selecionar(p);
        });
        // segue na temporada seguinte (draft na tela pelo auxiliar) sem erro
        for (int i = 0; i < 4; i++) {
            s.fluxo.jogarProximaRodada(null);
            exclusivo(j);
        }
        // pedir demissão do time da Kings: sem emprego, a tela de desempregado monta com candidaturas
        if (s.fluxo.soKings()) {
            j.pedirDemissao();
            assertNull(j.mundo().tecnico.kingsTimeId);
            assertFalse(s.fluxo.soKings());
        }
        naTela(() -> new Scene(new TelaDesempregado(s).raiz(), 1280, 800));
    }

    @Test
    void trocaDeClubeParaKingsEVoltaParaClube() throws Exception {
        String clube = FluxoTest.clubeSerieA();
        Jogo j = Jogo.novoJogo(Fixture.banco(), 23, clube, "Técnico");
        Sessao s = new Sessao(j, "troca");
        j.avancarSemana();
        LigaKings liga = brasil(j);
        // clube → Kings: aceitar o convite do presidente deixa o clube
        PropostaTecnico pk = j.simulador().kings.proporKings(liga, liga.times.get(3));
        assertEquals("Você vai deixar o " + j.meuClube().nome, "Você vai deixar o " + j.empregoAtual());
        assertTrue(j.aceitarEmprego(pk.id));
        assertNull(j.meuClube(), "aceitar a Kings = deixar o clube");
        assertFalse(j.clube(clube).humano);
        assertEquals(liga.times.get(3).id, j.mundo().tecnico.kingsTimeId);
        assertTrue(s.fluxo.soKings());
        exclusivo(j);
        for (int i = 0; i < 3; i++) {
            s.fluxo.jogarProximaRodada(null);
            exclusivo(j);
        }
        if (fx) naTela(() -> {
            Hub h = new Hub(s);
            new Scene(h.raiz(), 1280, 800);
            h.aoMostrar();
            assertEquals("jogo", h.painelAtual(), "a central abre no Próximo jogo da Kings");
        });
        // Kings → clube: aceitar o convite de um clube deixa o time da Kings
        Clube outro = null;
        for (Clube c : j.mundo().clubes.values()) if (!c.virtual && c.existe && c.divisao == 1 && !c.id.equals(clube)) { outro = c; break; }
        assertNotNull(outro);
        String tk = j.mundo().tecnico.kingsTimeId;
        if (tk != null) {
            j.simulador().diretoria.proporAoHumano(outro);
            PropostaTecnico pc = null;
            for (PropostaTecnico p : j.propostasDeEmprego()) if (outro.id.equals(p.clubeId)) pc = p;
            assertNotNull(pc);
            assertTrue(j.aceitarEmprego(pc.id));
            assertEquals(outro.id, j.meuClube().id);
            assertNull(j.mundo().tecnico.kingsTimeId, "aceitar um clube = deixar o time da Kings");
            assertFalse(j.timeKings(tk).humano);
            assertFalse(s.fluxo.soKings());
            exclusivo(j);
            j.avancarSemana();
            exclusivo(j);
        }
    }

    /** Ids das propostas de emprego (clube ou Kings) — nenhuma pode virar mensagem na caixa. */
    private static void semMensagemDeEmprego(Jogo j) {
        java.util.Set<Integer> emprego = new java.util.HashSet<>();
        for (PropostaTecnico p : j.mundo().propostasTecnico) if (p.selecaoPais == null) emprego.add(p.id);
        for (var msg : j.caixaDeEntrada())
            assertFalse("proposta".equals(msg.tipo) && msg.propostaId != null && emprego.contains(msg.propostaId), "proposta de emprego na caixa: " + msg.titulo);
    }

    /** Força a demissão do técnico da IA de um clube (diretoria sem confiança). */
    private static Clube demitirTecnicoDaIa(Jogo j, String exceto) {
        Clube alvo = null;
        for (Clube c : j.mundo().clubes.values())
            if (!c.virtual && c.existe && !c.humano && c.divisao == 1 && !c.id.equals(exceto) && c.treinadorId != null) { alvo = c; break; }
        assertNotNull(alvo);
        for (int i = 0; i < 3; i++) {
            alvo.confiancaDiretoria = 0;
            alvo.semanasDesdeTroca = 20;
            alvo.jogosTemp = Math.max(alvo.jogosTemp, 10);
            j.avancarSemana();
            Clube a = alvo;
            if (j.mundo().vagas.stream().anyMatch(v -> v.clubeId.equals(a.id))) break;
        }
        return alvo;
    }

    @Test
    void semMensagensDeEmpregoNumaTemporada() {
        String clube = FluxoTest.clubeSerieA();
        Jogo j = Jogo.novoJogo(Fixture.banco(), 33, clube, "Técnico");
        j.mundo().tecnico.reputacao = 90;
        while (j.ano() == 2026) {
            if (j.meuClube() != null) j.meuClube().confiancaDiretoria = 90;
            j.avancarSemana();
        }
        semMensagemDeEmprego(j);
        // os clubes que demitiram o técnico ficaram na lista de vagas (abertas ou já fechadas)
        assertNotNull(j.mundo().vagas);
    }

    @Test
    void vagaAbreNaDemissaoEDaParaSeCandidatarEAceitar() {
        String clube = FluxoTest.clubeSerieA();
        Jogo j = Jogo.novoJogo(Fixture.banco(), 29, clube, "Técnico");
        j.avancarSemana();
        Clube alvo = demitirTecnicoDaIa(j, clube);
        Jogo.VagaEmprego v = null;
        for (Jogo.VagaEmprego x : j.vagasDeEmprego()) if (x.id().equals(alvo.id)) v = x;
        assertNotNull(v, "a demissão abre uma vaga na aba Vagas");
        assertEquals("clube", v.tipo());
        assertEquals("Técnico demitido", v.motivo());
        assertTrue(v.fechaEm() > v.abertaEm() && v.fechaEm() - v.abertaEm() <= 4, "fecha em 1 a 4 semanas");
        semMensagemDeEmprego(j);
        // candidatura: a diretoria responde em 1 a 2 semanas (com reputação alta, aceita)
        j.mundo().tecnico.reputacao = 100;
        for (Jogo.VagaEmprego x : j.vagasDeEmprego()) if (x.id().equals(alvo.id)) v = x;
        assertTrue(v.podeCandidatar());
        assertTrue(j.candidatarVaga(v));
        assertFalse(j.candidatarVaga(v), "uma candidatura por vaga");
        Jogo.VagaEmprego depois = null;
        for (int i = 0; i < 4; i++) {
            if (j.meuClube() != null) j.meuClube().confiancaDiretoria = 90;
            j.avancarSemana();
            for (Jogo.VagaEmprego x : j.vagasDeEmprego()) if (x.id().equals(alvo.id)) depois = x;
            if (depois != null && (depois.proposta() != null || "Recusada".equals(depois.situacao()))) break;
        }
        assertNotNull(depois);
        // a resposta pode ser recusa (10% com reputação alta); recusada e vencida, a vaga some da aba
        br.futebas.ia.Vaga bruta = j.mundo().vagas.stream().filter(q -> q.clubeId.equals(alvo.id)).findFirst().orElseThrow();
        assertNotNull(bruta.resposta, "a diretoria respondeu: " + depois);
        assertTrue(j.caixaDeEntrada().stream().anyMatch(msg -> "vaga".equals(msg.tipo)), "uma nota curta com a resposta");
        semMensagemDeEmprego(j);
        if (depois.proposta() != null) {
            assertTrue(depois.podeAceitar());
            assertTrue(j.aceitarVaga(depois));
            assertEquals(alvo.id, j.meuClube().id, "aceitar = assumir (e deixar o clube anterior)");
            assertFalse(j.clube(clube).humano);
        }
        exclusivo(j);
    }

    @Test
    void desempregadoUsaAsVagasDeClubeEKings() throws Exception {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 31, FluxoTest.clubeSerieA(), "Técnico");
        j.avancarSemana();
        j.pedirDemissao();
        assertNull(j.empregoAtual());
        j.mundo().tecnico.reputacao = 100;
        // vaga aberta da Kings (time sem técnico)
        LigaKings.TimeKings vaga = brasil(j).times.get(5);
        vaga.vagaAte = j.mundo().semanaAbsoluta() + 6;
        Jogo.VagaEmprego vk = null;
        for (Jogo.VagaEmprego x : j.vagasDeEmprego()) if (x.id().equals(vaga.id)) vk = x;
        assertNotNull(vk, "vaga da Kings na lista");
        assertEquals("kings", vk.tipo());
        boolean ok = false;
        for (int i = 0; i < 20 && !ok; i++) {
            vaga.vagaAte = j.mundo().semanaAbsoluta() + 6;
            ok = j.candidatarVaga(vk);
        }
        assertTrue(ok, "o presidente aceita com reputação alta");
        Jogo.VagaEmprego quer = null;
        for (Jogo.VagaEmprego x : j.vagasDeEmprego()) if (x.id().equals(vaga.id)) quer = x;
        assertNotNull(quer.proposta(), "agora o time da Kings quer você");
        assertTrue(j.aceitarVaga(quer));
        assertTrue(j.tecnicoDaKings());
        exclusivo(j);
        semMensagemDeEmprego(j);
        if (fx) {
            Sessao s = new Sessao(j, "vagas");
            naTela(() -> {
                new Scene(new TelaDesempregado(s).raiz(), 1280, 800);
                Hub h = new Hub(s);
                new Scene(h.raiz(), 1280, 800);
                h.aoMostrar();
                h.selecionar("vagas");
                assertEquals("vagas", h.painelAtual());
            });
        }
    }

    @Test
    void saveAntigoComClubeEKingsFicaComOClube() throws Exception {
        String clube = FluxoTest.clubeSerieA();
        Jogo j = Jogo.novoJogo(Fixture.banco(), 31, clube, "Técnico");
        j.avancarSemana();
        // estado antigo (antes da regra): clube + time da Kings
        LigaKings.TimeKings tk = brasil(j).times.get(1);
        tk.humano = true;
        j.mundo().tecnico.kingsTimeId = tk.id;
        Path arq = Files.createTempDirectory("futebas-kings").resolve("antigo.futebas.json.gz");
        j.salvar(arq);
        Jogo c = Jogo.carregar(arq, Fixture.banco());
        assertEquals(clube, c.meuClube().id, "fica o clube");
        assertNull(c.mundo().tecnico.kingsTimeId, "o time da Kings volta para a IA");
        assertFalse(c.timeKings(tk.id).humano);
        assertTrue(c.caixaDeEntrada().stream().anyMatch(m -> m.texto != null && m.texto.contains("deixou o " + tk.nome)), "aviso na caixa");
        exclusivo(c);
    }

    @Test
    void novoJogoEscolheClubeOuKings() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        naTela(() -> {
            TelaNovoJogo t = new TelaNovoJogo();
            new Scene(t.raiz(), 1280, 800);
            t.selecionarTimeKings("kb-1");
        });
    }
}
