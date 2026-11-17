package br.futebas.partida;

import br.futebas.ferramentas.CalibrarMotor;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requisitos (12/11/2026): "quando o cara é expulso não pode trocar ele do jogo, mas pode trocar
 * a posição dele para alguém ficar na posição dele"; "trocar o jogador de lugar no campo não é troca";
 * e "tem bugs nas substituições durante o jogo". Tudo no motor de verdade (o mesmo do futebol ao vivo).
 */
class TrocasNoMotorTest {

    private static Escalado de(List<Escalado> l, int id) {
        for (Escalado e : l) if (e.id == id) return e;
        return null;
    }

    /** Expulsão no seu time: o expulso não sai nem é substituído; alguém em campo vai para a vaga dele sem gastar troca. */
    @Test
    void expulsoNaoSaiMasAVagaDeleRecebeOutro() {
        Rng rng = new Rng(11);
        int casos = 0;
        for (int i = 0; i < 600 && casos < 6; i++) {
            int[] visto = {0};
            List<String> erros = new ArrayList<>();
            AoVivo vivo = new AoVivo() {
                @Override public void minuto(Controle c) {}

                @Override
                public void pausa(Controle c, Pausa motivo, int lado, Integer jogadorId) {
                    if (motivo != Pausa.EXPULSAO || lado != 0 || jogadorId == null || visto[0] > 0) return;
                    visto[0]++;
                    int subs = c.substituicoesFeitas(lado);
                    int emCampo = c.emCampo(lado).size();
                    Escalado expulso = de(c.vagas(lado), jogadorId);
                    if (expulso == null) { erros.add("o expulso continua na vaga dele"); return; }
                    if (de(c.emCampo(lado), jogadorId) != null) erros.add("expulso fora do campo");
                    // não dá para substituir o expulso
                    if (!c.banco(lado).isEmpty() && c.substituir(lado, jogadorId, c.banco(lado).get(0).id)) erros.add("substituiu o expulso");
                    // alguém em campo (não o goleiro, a não ser que o expulso seja o goleiro) vai para a vaga dele
                    Escalado quem = null;
                    for (Escalado e : c.emCampo(lado)) if ((e.vaga == Posicao.GOL) == (expulso.vaga == Posicao.GOL) && e.vaga != expulso.vaga) { quem = e; break; }
                    if (quem == null) for (Escalado e : c.emCampo(lado)) if (e.vaga != expulso.vaga) { quem = e; break; }
                    if (quem == null) return;
                    Posicao vagaDoExpulso = expulso.vaga, vagaDeQuem = quem.vaga;
                    int idxExpulso = c.vagas(lado).indexOf(expulso);
                    if (!c.trocarPosicoes(lado, quem.id, jogadorId)) { erros.add("não remanejou para a vaga do expulso"); return; }
                    if (c.substituicoesFeitas(lado) != subs) erros.add("troca de posição gastou substituição");
                    if (c.emCampo(lado).size() != emCampo) erros.add("mudou quantos estão em campo");
                    Escalado q = de(c.emCampo(lado), quem.id);
                    if (q == null || q.vaga != vagaDoExpulso) erros.add("quem foi remanejado não está na vaga do expulso");
                    if (c.vagas(lado).get(idxExpulso).id != quem.id) erros.add("a vaga do expulso não mostra quem foi para lá");
                    Escalado ex = de(c.vagas(lado), jogadorId);
                    if (ex == null || ex.vaga != vagaDeQuem) erros.add("o buraco não foi para a vaga de quem saiu dela");
                    if (de(c.emCampo(lado), jogadorId) != null) erros.add("o expulso voltou ao campo");
                    if (!c.sairam(lado).contains(jogadorId)) erros.add("o expulso não conta como quem saiu");
                    // duas vagas vazias não trocam
                    if (c.trocarPosicoes(lado, jogadorId, jogadorId)) erros.add("trocou o expulso com ele mesmo");
                }
            };
            OpcoesPartida op = new OpcoesPartida();
            op.aoVivo = vivo;
            op.ladoHumano = 0;
            ResultadoPartida r = MotorPartida.simular(CalibrarMotor.time("A", 72, rng, 2), CalibrarMotor.time("B", 70, rng, 2), op, rng);
            assertTrue(erros.isEmpty(), "jogo " + i + ": " + erros);
            if (visto[0] > 0) {
                casos++;
                assertTrue(r.golsMandante >= 0 && r.golsVisitante >= 0);
            }
        }
        assertTrue(casos > 0, "houve expulsões no seu time");
    }

    /** Substituições ao vivo: entra e sai, limite, quem saiu não volta, troca de posição não gasta troca, 11 em campo. */
    @Test
    void substituicoesNoMotor() {
        Rng rng = new Rng(7);
        for (int i = 0; i < 40; i++) {
            List<String> erros = new ArrayList<>();
            AoVivo vivo = new AoVivo() {
                @Override public void minuto(Controle c) {}

                @Override
                public void pausa(Controle c, Pausa motivo, int lado, Integer jogadorId) {
                    if (motivo != Pausa.INTERVALO) return;
                    int lado0 = 0;
                    int fora = c.sairam(lado0).size();
                    int limite = c.substituicoesPermitidas();
                    // troca de posição entre dois em campo: não gasta substituição
                    List<Escalado> ec = c.emCampo(lado0);
                    int s0 = c.substituicoesFeitas(lado0);
                    if (ec.size() >= 3 && !c.trocarPosicoes(lado0, ec.get(1).id, ec.get(2).id)) erros.add("troca de posição recusada");
                    if (c.substituicoesFeitas(lado0) != s0) erros.add("troca de posição gastou substituição");
                    Set<Integer> sairam = new HashSet<>();
                    while (c.substituicoesFeitas(lado0) < limite && !c.banco(lado0).isEmpty()) {
                        Escalado sai = null;
                        for (Escalado e : c.emCampo(lado0)) if (e.vaga != Posicao.GOL) { sai = e; break; }
                        Escalado entra = c.banco(lado0).get(0);
                        Posicao vaga = sai.vaga;
                        int antes = c.substituicoesFeitas(lado0), emCampo = c.emCampo(lado0).size(), banco = c.banco(lado0).size();
                        if (!c.substituir(lado0, sai.id, entra.id)) { erros.add("substituição recusada"); return; }
                        if (c.substituicoesFeitas(lado0) != antes + 1) erros.add("contador de trocas");
                        if (c.emCampo(lado0).size() != emCampo) erros.add("em campo mudou de tamanho");
                        if (c.banco(lado0).size() != banco - 1) erros.add("banco não diminuiu");
                        Escalado novo = de(c.emCampo(lado0), entra.id);
                        if (novo == null || novo.vaga != vaga) erros.add("quem entrou não ficou na vaga de quem saiu");
                        if (de(c.banco(lado0), sai.id) != null) erros.add("quem saiu voltou ao banco");
                        if (!c.sairam(lado0).contains(sai.id)) erros.add("quem saiu não ficou marcado");
                        // quem saiu não volta
                        if (!c.banco(lado0).isEmpty() && c.substituir(lado0, entra.id, sai.id)) erros.add("quem saiu voltou");
                        sairam.add(sai.id);
                    }
                    if (c.substituicoesFeitas(lado0) > limite) erros.add("passou do limite");
                    // acabou o limite: outra substituição é recusada, mas trocar de posição continua
                    ec = c.emCampo(lado0);
                    if (c.substituicoesFeitas(lado0) >= limite && !c.banco(lado0).isEmpty() && c.substituir(lado0, ec.get(3).id, c.banco(lado0).get(0).id))
                        erros.add("substituiu além do limite");
                    if (!c.trocarPosicoes(lado0, ec.get(3).id, ec.get(4).id)) erros.add("sem trocas sobrando, a troca de posição foi recusada");
                    if (c.sairam(lado0).size() != fora + sairam.size()) erros.add("lista de quem saiu");
                }
            };
            OpcoesPartida op = new OpcoesPartida();
            op.aoVivo = vivo;
            op.ladoHumano = 0;
            ResultadoPartida r = MotorPartida.simular(CalibrarMotor.time("A", 72, rng, 2), CalibrarMotor.time("B", 70, rng, 2), op, rng);
            assertTrue(erros.isEmpty(), "jogo " + i + ": " + erros);
            // o placar continua batendo com os gols narrados
            int gols = 0;
            for (EventoJogo e : r.eventos) if (e.gol()) gols++;
            assertEquals(r.golsMandante + r.golsVisitante, gols, "placar coerente depois das trocas");
        }
    }

    /**
     * A vaga do expulso fica vazia no setor dela, sem mudar o nível geral (a calibragem do "um a menos"
     * continua nas constantes do motor). Remanejar um meia para a lateral do expulso cobre a lateral.
     */
    @Test
    void perfilComVagaVazia() {
        Posicao[] f = Formacoes.vagas("4-4-2");
        List<Escalado> onze = new ArrayList<>();
        int ld = -1, mc = -1;
        for (int i = 0; i < f.length; i++) {
            onze.add(new Escalado(i, "J" + i, f[i], f[i], 70));
            if (f[i] == Posicao.LD && ld < 0) ld = i;
            if ((f[i] == Posicao.MC || f[i] == Posicao.VOL) && mc < 0) mc = i;
        }
        assertTrue(ld >= 0 && mc >= 0, "4-4-2 com lateral direito e meio-campo");
        PerfilTime cheio = PerfilTime.de(onze, null);
        PerfilTime buraco = PerfilTime.de(onze, null, Set.of(ld));
        assertEquals(cheio.ataque + cheio.defesa + cheio.meio, buraco.ataque + buraco.defesa + buraco.meio, 1e-6, "nível geral igual");
        assertTrue(buraco.defesaLados < cheio.defesaLados, "sem o lateral, a defesa pelos lados cai");
        // o meia vai para a lateral; o buraco vai para o meio
        Escalado lat = onze.get(ld), meia = onze.get(mc);
        Posicao v = lat.vaga;
        lat.vaga = meia.vaga;
        meia.vaga = v;
        PerfilTime remanejado = PerfilTime.de(onze, null, Set.of(ld));
        assertTrue(remanejado.defesaLados > buraco.defesaLados, "com o meia na lateral, a lateral fica coberta");
        // o nível só cai pelo meia fora de posição (o buraco em si não muda de valor ao mudar de lugar)
        double nivel = remanejado.ataque + remanejado.defesa + remanejado.meio, nivelCheio = cheio.ataque + cheio.defesa + cheio.meio;
        assertTrue(nivel <= nivelCheio + 1e-6 && nivel > nivelCheio * 0.9, "nível depois do remanejamento: " + nivel + " de " + nivelCheio);
        // sem ausentes: o mesmo perfil de antes
        PerfilTime igual = PerfilTime.de(onze, null, Set.of());
        assertEquals(PerfilTime.de(onze, null).ataque, igual.ataque, 1e-12);
    }
}
