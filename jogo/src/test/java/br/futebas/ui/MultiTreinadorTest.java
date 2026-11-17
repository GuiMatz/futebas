package br.futebas.ui;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.ia.PropostaJogador;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mensagem;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Tecnico;
import br.futebas.partida.AoVivo;
import br.futebas.ui.modelo.AcompanhamentoPartida;
import br.futebas.ui.modelo.Fluxo;
import br.futebas.ui.modelo.MesaAoVivo;
import br.futebas.ui.modelo.ResumoSave;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Modo multi-treinador (requisito, ver {@code docs/multitreinador.md}): vários técnicos humanos
 * no mesmo computador, cada um na sua vez; os jogos ao vivo andam juntos e as pausas seguem a ordem
 * dos técnicos; caixa, carreira e vagas de cada um; transferência entre dois técnicos; save com todos.
 */
class MultiTreinadorTest {

    /** Dois clubes da mesma liga (1ª divisão do Brasil fictício): se enfrentam na temporada. */
    static String[] doisDaMesmaLiga() {
        List<String> ids = new ArrayList<>();
        String pais = null;
        for (Jogo.OpcaoClube o : Jogo.clubesDisponiveis(Fixture.banco())) {
            if (!Integer.valueOf(1).equals(o.divisao())) continue;
            if (pais == null) pais = o.pais();
            if (pais.equals(o.pais())) ids.add(o.id());
            if (ids.size() == 2) return new String[]{ids.get(0), ids.get(1)};
        }
        throw new IllegalStateException("sem dois clubes da 1ª divisão no banco de teste");
    }

    static Jogo dois(long semente) {
        String[] c = doisDaMesmaLiga();
        return Jogo.novoJogoMulti(Fixture.banco(), semente,
                List.of(new Jogo.EscolhaTecnico("Ana", c[0], null), new Jogo.EscolhaTecnico("Beto", c[1], null)), null);
    }

    /** A caixa de entrada de um técnico (a do Mundo, se for o da vez). */
    static List<Mensagem> caixa(Mundo m, Tecnico t) {
        return t == m.tecnico ? m.mensagens : t.caixa == null ? List.of() : t.caixa;
    }

    @Test
    void doisTecnicosVariasRodadasNaOrdemDasVezes() {
        Jogo j = dois(11);
        Mundo m = j.mundo();
        Sessao s = new Sessao(j, null);
        assertTrue(j.multi());
        assertEquals(2, j.tecnicos().size());
        Tecnico ana = j.tecnicos().get(0), beto = j.tecnicos().get(1);
        assertSame(ana, m.tecnico, "o jogo começa na vez do primeiro técnico");
        assertNotEquals(ana.clubeId, beto.clubeId);
        assertTrue(m.clube(ana.clubeId).humano && m.clube(beto.clubeId).humano);
        assertTrue(caixa(m, beto).stream().anyMatch(x -> x.texto.contains(m.clube(beto.clubeId).nome)), "boas-vindas na caixa do Beto");
        assertFalse(m.mensagens.stream().anyMatch(x -> x.texto.contains("Bem-vindo ao " + m.clube(beto.clubeId).nome)), "e não na da Ana");

        int rodadas = 0;
        List<String> vezes = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            s.fluxo.avancarAteRodadaMulti();
            assertTrue(s.fluxo.algumJogoNaSemana() || j.ano() > 2026, "a rodada para na semana de jogo");
            // a vez passa por todos, na ordem
            for (int i = 0; i < j.tecnicos().size(); i++) {
                j.passarVez(i);
                assertSame(j.tecnicos().get(i), m.tecnico);
                assertEquals(i, j.vez());
                vezes.add(m.tecnico.nome);
                // a central é do técnico da vez
                assertEquals(m.tecnico.clubeId, j.meuClube().id);
            }
            int jogosAntes = ana.jogos + beto.jogos;
            Fluxo.Rodada r = s.fluxo.jogarSemanaMulti(null);
            j.passarVez(0);
            assertTrue(r.jogou(), "a semana teve jogo de técnico");
            assertTrue(ana.jogos + beto.jogos > jogosAntes, "os jogos contam para os técnicos");
            rodadas++;
        }
        assertEquals(8, rodadas);
        for (int k = 0; k < vezes.size(); k++) assertEquals(k % 2 == 0 ? "Ana" : "Beto", vezes.get(k));
        assertTrue(ana.jogos >= 6 && beto.jogos >= 6, "os dois jogaram: " + ana.jogos + " / " + beto.jogos);
        // os placares vão para a caixa de quem jogou
        String nomeAna = m.clube(ana.clubeId).nome, nomeBeto = m.clube(beto.clubeId).nome;
        for (Mensagem x : caixa(m, ana)) if ("resultado".equals(x.tipo) && x.texto.contains(" x ")) assertTrue(x.texto.contains(nomeAna), x.texto);
        for (Mensagem x : caixa(m, beto)) if ("resultado".equals(x.tipo) && x.texto.contains(" x ")) assertTrue(x.texto.contains(nomeBeto), x.texto);
        assertSame(m.tecnico, m.tecnicos.get(m.vez));
    }

    @Test
    void dueloDiretoAoVivoComPausasNaOrdem() {
        Jogo j = dois(12);
        Mundo m = j.mundo();
        Sessao s = new Sessao(j, null);
        Tecnico ana = j.tecnicos().get(0), beto = j.tecnicos().get(1);
        List<String> pausas = Collections.synchronizedList(new ArrayList<>());
        List<Integer> grupos = Collections.synchronizedList(new ArrayList<>());
        AcompanhamentoPartida.escala = 0;
        try {
            Partida duelo = null;
            for (int k = 0; k < 60 && duelo == null; k++) {
                s.fluxo.avancarAteRodadaMulti();
                pausas.clear();
                MesaAoVivo mesa = mesa(s, pausas);
                mesa.ouvinte(new MesaAoVivo.Ouvinte() {
                    @Override
                    public void grupo(List<MesaAoVivo.Jogo> jogos) {
                        grupos.add(jogos.size());
                        mesa.colunasProntas();
                    }

                    @Override
                    public void antesDoGrupo(List<String> clubes) {
                        mesa.liberarGrupo();
                    }
                });
                int aj = ana.jogos, bj = beto.jogos;
                Fluxo.Rodada r = s.fluxo.jogarSemanaMulti(mesa);
                j.passarVez(0);
                for (Partida p : r.meusJogos()) if (p.envolve(ana.clubeId) && p.envolve(beto.clubeId)) duelo = p;
                if (duelo != null) {
                    // os dois técnicos jogaram o duelo, e cada um conta o jogo na carreira dele
                    assertTrue(ana.jogos > aj && beto.jogos > bj);
                    int[] pa = {ana.vitorias, ana.empates, ana.derrotas}, pb = {beto.vitorias, beto.empates, beto.derrotas};
                    assertEquals(ana.jogos, pa[0] + pa[1] + pa[2]);
                    assertEquals(beto.jogos, pb[0] + pb[1] + pb[2]);
                    // intervalo do duelo: primeiro o técnico do mandante, depois o do visitante
                    String casa = duelo.mandante, fora = duelo.visitante;
                    int ic = pausas.indexOf("INTERVALO:" + casa), iv = pausas.indexOf("INTERVALO:" + fora);
                    assertTrue(ic >= 0 && iv >= 0, "os dois técnicos param no intervalo: " + pausas);
                    assertTrue(ic < iv, "mandante antes do visitante: " + pausas);
                } else if (!grupos.isEmpty()) {
                    // jogos separados ao mesmo tempo: no intervalo, primeiro a Ana (técnico 1), depois o Beto
                    int ia = pausas.indexOf("INTERVALO:" + ana.clubeId), ib = pausas.indexOf("INTERVALO:" + beto.clubeId);
                    if (ia >= 0 && ib >= 0 && grupos.get(grupos.size() - 1) >= 2) assertTrue(ia < ib, "a ordem das vezes vale no intervalo: " + pausas);
                }
            }
            assertNotNull(duelo, "os dois clubes da mesma liga se enfrentam");
            assertTrue(grupos.stream().anyMatch(n -> n >= 2), "em alguma rodada os dois jogos rodaram juntos: " + grupos);
        } finally {
            AcompanhamentoPartida.escala = 1.0;
        }
    }

    /** Mesa sem tela: as pausas são atendidas na hora e anotadas (motivo:clube), na ordem em que abriram. */
    private static MesaAoVivo mesa(Sessao s, List<String> pausas) {
        List<Tecnico> ts = s.jogo.tecnicos();
        return new MesaAoVivo(AcompanhamentoPartida.Velocidade.INSTANTANEA, id -> {
            for (int i = 0; i < ts.size(); i++) if (id.equals(ts.get(i).clubeId) || id.equals(ts.get(i).kingsTimeId)) return i;
            return 99;
        }, clubeId -> {
            AcompanhamentoPartida[] ref = new AcompanhamentoPartida[1];
            ref[0] = new AcompanhamentoPartida(clubeId, AcompanhamentoPartida.Velocidade.INSTANTANEA, e -> {
                if (e.pedido() == null) return;
                pausas.add(e.pedido().motivo() + ":" + clubeId);
                // o técnico escolhe o batedor (o último da lista, para não ser sempre o cobrador definido)
                if (e.pedido().candidatos() != null) ref[0].responderBatedor(e.pedido().candidatos().get(e.pedido().candidatos().size() - 1).id());
                else ref[0].pausar(false);
            });
            ref[0].esperarTecnico(true);
            ref[0].configurarPausas(true, false, false, true, false, false);
            return ref[0];
        });
    }

    /**
     * Regras do jogo: (1) o 2º jogo de um técnico na mesma semana nunca começa direto: antes de montar os
     * times, a tela volta às vezes dos técnicos desse jogo; (2) o pênalti a favor para para o técnico
     * escolher o batedor, também no multi.
     */
    @Test
    void segundoJogoDaSemanaVoltaAsVezesEBatedorEscolhido() {
        Jogo j = dois(17);
        Sessao s = new Sessao(j, null);
        Tecnico ana = j.tecnicos().get(0), beto = j.tecnicos().get(1);
        List<String> pausas = Collections.synchronizedList(new ArrayList<>());
        List<String> eventos = Collections.synchronizedList(new ArrayList<>());
        AcompanhamentoPartida.escala = 0;
        try {
            for (int k = 0; k < 45; k++) {
                s.fluxo.avancarAteRodadaMulti();
                MesaAoVivo mesa = mesa(s, pausas);
                mesa.ouvinte(new MesaAoVivo.Ouvinte() {
                    @Override
                    public void grupo(List<MesaAoVivo.Jogo> jogos) {
                        eventos.add("grupo");
                        mesa.colunasProntas();
                    }

                    @Override
                    public void antesDoGrupo(List<String> clubes) {
                        // os clubes do próximo jogo são de técnicos, e nenhum jogo deles foi montado ainda
                        assertFalse(clubes.isEmpty());
                        for (String id : clubes) assertTrue(id.equals(ana.clubeId) || id.equals(beto.clubeId), id);
                        eventos.add("antes");
                        mesa.liberarGrupo();
                    }
                });
                s.fluxo.jogarSemanaMulti(mesa);
                j.passarVez(0);
                if (j.ano() > 2026) break;
            }
        } finally {
            AcompanhamentoPartida.escala = 1.0;
        }
        // depois de cada volta às vezes vem um grupo novo (nunca dois grupos seguidos sem voltar na mesma semana)
        for (int i = 0; i < eventos.size(); i++) if ("antes".equals(eventos.get(i))) assertEquals("grupo", eventos.get(i + 1), eventos.toString());
        assertTrue(eventos.contains("antes"), "em alguma semana um técnico jogou duas vezes: " + eventos.size() + " grupos");
        assertTrue(pausas.stream().anyMatch(x -> x.startsWith("PENALTI_A_FAVOR:")), "o técnico escolheu o batedor de algum pênalti: " + pausas.size());
    }

    @Test
    void transferenciaEntreDoisTecnicosNaVezDoVendedor() {
        Jogo j = dois(13);
        Mundo m = j.mundo();
        Tecnico ana = j.tecnicos().get(0), beto = j.tecnicos().get(1);
        Clube ca = m.clube(ana.clubeId), cb = m.clube(beto.clubeId);
        // a Ana quer um jogador do Beto (o mais barato do elenco)
        Jogador alvo = null;
        for (int id : cb.elenco) {
            Jogador x = m.jogador(id);
            if (x.contrato == null || x.amador()) continue;
            if (alvo == null || j.valorDeMercado(x.id) < j.valorDeMercado(alvo.id)) alvo = x;
        }
        assertNotNull(alvo);
        long valor = Math.max(1, j.valorDeMercado(alvo.id));
        ca.dinheiro = Math.max(ca.dinheiro, valor * 3);
        long salario = Math.round(j.salarioPedido(alvo.id) * 1.3) + 1;
        Jogo.ResultadoNegociacao r = j.proporCompra(alvo.id, valor, 24, salario);
        assertTrue(r.aceito(), r.mensagem());
        PropostaJogador p = j.propostasEnviadas().stream().filter(x -> x.entreHumanos).findFirst().orElseThrow();
        assertEquals("aguardando", p.estado, "o Beto responde na vez dele (a IA não responde)");
        // a semana corre e ninguém responde pelo Beto
        new Sessao(j, null).fluxo.avancarSemana();
        j.passarVez(0);
        assertEquals("aguardando", p.estado);
        assertTrue(j.propostasRecebidas().isEmpty(), "a proposta não aparece como recebida para a Ana");
        // vez do Beto: a proposta está na caixa e na aba Transferências dele
        j.passarVez(1);
        assertTrue(m.mensagens.stream().anyMatch(x -> p.id == (x.propostaId == null ? -1 : x.propostaId)), "a proposta chegou na caixa do Beto");
        PropostaJogador vista = j.propostasRecebidas().stream().filter(x -> x.id == p.id).findFirst().orElseThrow();
        assertTrue(vista.minhaVez(), "na vez do Beto, é ele quem responde");
        long caixaBeto = cb.dinheiro;
        assertTrue(j.responderProposta(p.id, true));
        assertEquals(ca.id, alvo.clubeId, "o jogador foi para o clube da Ana: " + p.estado + " / " + p.mensagem);
        assertTrue(cb.dinheiro > caixaBeto, "o Beto recebeu o dinheiro");
        assertSame(beto, m.tecnico, "continua a vez do Beto");
        // a Ana fica sabendo na vez dela
        j.passarVez(0);
        assertTrue(m.mensagens.stream().anyMatch(x -> p.id == (x.propostaId == null ? -1 : x.propostaId)), "a resposta chegou na caixa da Ana");
    }

    @Test
    void salvarECarregarComTodosOsTecnicos(@org.junit.jupiter.api.io.TempDir Path dir) {
        Jogo j = dois(14);
        Sessao s = new Sessao(j, null);
        for (int k = 0; k < 3; k++) {
            s.fluxo.avancarAteRodadaMulti();
            s.fluxo.jogarSemanaMulti(null);
        }
        j.passarVez(1); // salvo no meio da rodada, na vez do Beto
        Mundo m = j.mundo();
        Tecnico ana = j.tecnicos().get(0), beto = j.tecnicos().get(1);
        int caixaAna = ana.caixa.size(), caixaBeto = m.mensagens.size(), jogosAna = ana.jogos, jogosBeto = beto.jogos;
        Path arq = dir.resolve("multi" + br.futebas.mundo.Salvamento.EXTENSAO);
        j.salvar(arq);
        ResumoSave rs = ResumoSave.de(j);
        assertTrue(rs.multi());
        assertEquals("Ana, Beto", rs.tecnicos());
        rs.gravar(arq);
        ResumoSave lido = ResumoSave.ler(arq);
        assertNotNull(lido);
        assertEquals("Ana, Beto", lido.tecnicos(), "o Continuar mostra o jogo com vários técnicos");
        assertEquals("Ana, Beto", ResumoSave.extrair(arq).tecnicos(), "também lendo direto do save");

        Jogo c = Jogo.carregar(arq, Fixture.banco());
        Mundo mc = c.mundo();
        assertTrue(c.multi());
        assertEquals(2, c.tecnicos().size());
        assertEquals(1, c.vez(), "volta na vez de quem estava jogando");
        assertSame(mc.tecnico, mc.tecnicos.get(1), "o técnico da vez é o mesmo objeto da lista");
        assertEquals("Beto", mc.tecnico.nome);
        assertEquals(beto.clubeId, c.meuClube().id);
        assertEquals(caixaBeto, mc.mensagens.size(), "a caixa do Beto");
        assertEquals(caixaAna, mc.tecnicos.get(0).caixa.size(), "a caixa da Ana guardada");
        assertEquals(jogosAna, mc.tecnicos.get(0).jogos);
        assertEquals(jogosBeto, mc.tecnico.jogos);
        assertTrue(mc.clube(ana.clubeId).humano && mc.clube(beto.clubeId).humano);
        // e o jogo segue
        c.passarVez(0);
        assertEquals("Ana", mc.tecnico.nome);
        assertEquals(caixaAna, mc.mensagens.size());
        Sessao sc = new Sessao(c, "multi");
        sc.fluxo.avancarAteRodadaMulti();
        assertTrue(sc.fluxo.jogarSemanaMulti(null).jogou());
    }

    @Test
    void saveAntigoDeUmTecnicoAbreComoAntes(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 15, FluxoTest.clubeSerieA(), "Solo");
        Sessao s = new Sessao(j, null);
        s.fluxo.jogarProximaRodada(null);
        Path arq = dir.resolve("antigo" + br.futebas.mundo.Salvamento.EXTENSAO);
        j.salvar(arq);
        // um save de antes do modo multi: sem "tecnicos", sem "vez" e sem as listas novas do técnico
        JsonObject o;
        try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(arq)), StandardCharsets.UTF_8)) {
            o = JsonParser.parseReader(r).getAsJsonObject();
        }
        o.remove("tecnicos");
        o.remove("vez");
        JsonObject t = o.getAsJsonObject("tecnico");
        for (String k : List.of("caixa", "convites", "recusadas", "carreira")) t.remove(k);
        try (Writer w = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(arq)), StandardCharsets.UTF_8)) {
            w.write(o.toString());
        }
        Jogo c = Jogo.carregar(arq, Fixture.banco());
        assertFalse(c.multi());
        assertNull(c.mundo().tecnicos);
        assertEquals(1, c.tecnicos().size());
        assertEquals(0, c.vez());
        assertEquals("Solo", c.mundo().tecnico.nome);
        assertEquals(j.meuClube().id, c.meuClube().id);
        assertEquals(j.mundo().mensagens.size(), c.mundo().mensagens.size());
        c.passarVez(1); // sem efeito no jogo de um técnico
        assertEquals("Solo", c.mundo().tecnico.nome);
        assertFalse(ResumoSave.de(c).multi());
        // o jogo de um técnico segue igual
        Fluxo.Rodada r = new Sessao(c, "antigo").fluxo.jogarProximaRodada(null);
        assertTrue(r.jogou());
    }

    /**
     * Kings no multi (requisito): os jogos da Kings dos técnicos também vão para a tela dividida, ao
     * vivo e juntos; o intervalo segue a ordem dos técnicos; o draft espera a escolha do técnico do time,
     * na vez dele.
     */
    @Test
    void kingsAoVivoNaTelaDivididaEDraftNaVez() {
        Jogo j = Jogo.novoJogoMulti(Fixture.banco(), 18, List.of(new Jogo.EscolhaTecnico("Ana", null, "kb-1"),
                new Jogo.EscolhaTecnico("Beto", null, "kb-2")), null);
        Sessao s = new Sessao(j, null);
        Tecnico ana = j.tecnicos().get(0), beto = j.tecnicos().get(1);
        List<String> intervalos = Collections.synchronizedList(new ArrayList<>());
        List<Integer> grupos = Collections.synchronizedList(new ArrayList<>());
        boolean[] duelo = new boolean[1];
        int escolhas = 0;
        for (int k = 0; k < 30; k++) {
            s.fluxo.avancarAteRodadaMulti();
            // draft: o time da vez é de um técnico? ele escolhe, na vez dele
            for (int g = 0; g < 40; g++) {
                var d = j.estadoDraft();
                if (d == null || d.vez() == null) break;
                Tecnico dono = j.mundo().tecnicoDoTimeKings(d.vez());
                if (dono == null) break;
                j.passarVez(j.tecnicos().indexOf(dono));
                List<Jogador> disp = j.disponiveisNoDraft(null, 0, 1);
                if (disp.isEmpty()) break;
                assertTrue(j.escolherNoDraft(disp.get(0).id), "o técnico escolhe no draft na vez dele");
                escolhas++;
            }
            j.passarVez(0);
            MesaAoVivo mesa = mesa(s, new ArrayList<>());
            mesa.fabricaKings((time, lado) -> {
                br.futebas.ui.modelo.AcompanhamentoKings[] ref = new br.futebas.ui.modelo.AcompanhamentoKings[1];
                ref[0] = new br.futebas.ui.modelo.AcompanhamentoKings(lado, e -> {
                    if ("intervalo".equals(e.pausa())) {
                        intervalos.add(time + "@" + e.minuto());
                        ref[0].seguir();
                    }
                });
                return ref[0];
            });
            mesa.ouvinte(new MesaAoVivo.Ouvinte() {
                @Override
                public void grupo(List<MesaAoVivo.Jogo> jogos) {
                    if (jogos.stream().allMatch(x -> x.kings)) grupos.add(jogos.size());
                    if (jogos.stream().anyMatch(x -> x.kings && x.timesKings.size() > 1)) duelo[0] = true;
                    mesa.colunasProntas();
                }

                @Override
                public void antesDoGrupo(List<String> clubes) {
                    mesa.liberarGrupo();
                }
            });
            intervalos.clear();
            duelo[0] = false;
            int ja = ana.kingsJogos, jb = beto.kingsJogos;
            s.fluxo.jogarSemanaMulti(mesa);
            j.passarVez(0);
            // jogos separados ao mesmo tempo: o intervalo da Ana (técnico 1) vem antes do do Beto
            // intervalos no mesmo minuto (jogos juntos): o da Ana (técnico 1) vem antes do do Beto
            if (intervalos.size() >= 2) {
                String[] x = intervalos.get(0).split("@"), y = intervalos.get(1).split("@");
                // duelo direto (Ana × Beto no mesmo jogo): o time A primeiro, como no futebol (doc da MesaAoVivo)
                if (!duelo[0] && x[1].equals(y[1]) && !x[0].equals(y[0])) assertEquals("kb-1", x[0], "o intervalo segue a ordem dos técnicos: " + intervalos);
            }
        }
        assertTrue(ana.kingsJogos > 0 && beto.kingsJogos > 0, "os dois jogaram: " + ana.kingsJogos + " / " + beto.kingsJogos);
        assertFalse(grupos.isEmpty(), "os jogos da Kings dos técnicos foram para a tela dividida");
        assertTrue(grupos.stream().anyMatch(n -> n >= 2) || grupos.stream().anyMatch(n -> n == 1), "grupos: " + grupos);
        System.out.println("kings multi: grupos=" + grupos + " escolhas no draft=" + escolhas);
    }

    @Test
    void clubeOuKingsNuncaOsDoisEUmTimePorTecnico() {
        String[] c = doisDaMesmaLiga();
        Jogo j = Jogo.novoJogoMulti(Fixture.banco(), 16, List.of(new Jogo.EscolhaTecnico("Ana", c[0], null),
                new Jogo.EscolhaTecnico("Kel", null, "kb-1")), null);
        Tecnico ana = j.tecnicos().get(0), kel = j.tecnicos().get(1);
        assertEquals(c[0], ana.clubeId);
        assertNull(ana.kingsTimeId);
        assertNull(kel.clubeId);
        assertEquals("kb-1", kel.kingsTimeId);
        assertSame(kel, j.mundo().tecnicoDoTimeKings("kb-1"));
        // o mesmo time para dois técnicos não pode
        assertThrows(IllegalArgumentException.class, () -> Jogo.novoJogoMulti(Fixture.banco(), 16,
                List.of(new Jogo.EscolhaTecnico("A", c[0], null), new Jogo.EscolhaTecnico("B", c[0], null)), null));
        // a vez do técnico da Kings: central da Kings
        j.passarVez(1);
        assertNull(j.meuClube());
        assertEquals("kb-1", j.meuTimeKings().id);
        Sessao s = new Sessao(j, null);
        assertTrue(s.fluxo.soKings());
        for (int k = 0; k < 6; k++) {
            s.fluxo.avancarAteRodadaMulti();
            s.fluxo.jogarSemanaMulti(null);
        }
        j.passarVez(0);
        assertTrue(ana.jogos > 0 && kel.kingsJogos > 0, "os dois jogam: " + ana.jogos + " / " + kel.kingsJogos);
        assertNull(kel.clubeId, "carreira exclusiva");
    }
}
