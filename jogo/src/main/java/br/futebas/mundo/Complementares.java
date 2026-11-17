package br.futebas.mundo;

import br.futebas.partida.Escalador;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Jogadores complementares (regra 5 e seção 7.4): quando um clube não tem 18 jogadores
 * disponíveis (ou 2 goleiros) para um dia de jogo, o clube contrata REFORÇOS DE EMERGÊNCIA:
 * jogadores gerados comuns (nome real do país, força pelo nível do clube), com contrato
 * profissional até o fim da temporada. Vale para a IA e para o técnico (que recebe mensagem).
 *
 * <p>Requisito (15/11/2026): acabou o "Amador em teste #N" e o contrato de teste. Saves
 * antigos: {@link #limparSaveAntigo} aposenta os amadores que ainda existirem.
 */
public final class Complementares {
    private Complementares() {}

    public static final int MINIMO_DIA_DE_JOGO = 18;
    public static final int MINIMO_GOLEIROS = 2;

    /** Garante elenco para a partida. Retorna quantos reforços foram contratados. */
    public static int garantir(Simulador s, Clube c) {
        if (c.virtual) return 0;
        Mundo m = s.m;
        List<Jogador> disp = Escalador.disponiveis(m, c);
        int goleiros = 0;
        for (Jogador j : disp) if (j.posicao == Posicao.GOL) goleiros++;
        int total = disp.size();
        if (goleiros >= MINIMO_GOLEIROS && total >= MINIMO_DIA_DE_JOGO) return 0;
        int fim = s.mercado.fimDaTemporada(c);
        List<Jogador> novos = new ArrayList<>();
        while (goleiros < MINIMO_GOLEIROS) {
            Jogador j = s.populador.reforco(c, Posicao.GOL, fim);
            if (j == null) break;
            novos.add(j);
            goleiros++;
            total++;
        }
        while (total < MINIMO_DIA_DE_JOGO) {
            Jogador j = s.populador.reforco(c, posicaoQueFalta(m, c), fim);
            if (j == null) break;
            novos.add(j);
            total++;
        }
        if (!novos.isEmpty()) {
            m.historia.estat(m.ano).complementaresUsados += novos.size();
            m.historia.estat(m.ano).complementaresCriados += novos.size();
            if (c.humano) {
                StringBuilder sb = new StringBuilder();
                for (Jogador j : novos) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(j.nomeExibicao()).append(" (").append(j.posicao.name()).append(", ").append(j.idade(m.ano)).append(" anos)");
                }
                m.mensagemDoClube(c.id, "jogador", "Reforços de emergência",
                        "Faltavam jogadores para o dia de jogo; o clube contratou " + sb + " até o fim da temporada, com salário mínimo.");
            }
        }
        return novos.size();
    }

    private static Posicao posicaoQueFalta(Mundo m, Clube c) {
        int[] cont = new int[Posicao.values().length];
        for (Jogador j : Escalador.disponiveis(m, c)) cont[j.posicao.ordinal()]++;
        Posicao[] desejo = {Posicao.ZAG, Posicao.ZAG, Posicao.LD, Posicao.LE, Posicao.VOL, Posicao.MC, Posicao.MEI,
                Posicao.PE, Posicao.PD, Posicao.CA, Posicao.CA, Posicao.ZAG, Posicao.MC, Posicao.CA, Posicao.VOL, Posicao.PE};
        int[] quer = new int[Posicao.values().length];
        for (Posicao p : desejo) {
            quer[p.ordinal()]++;
            if (quer[p.ordinal()] > cont[p.ordinal()]) return p;
        }
        return Posicao.MC;
    }

    /**
     * Save de antes de 15/11/2026: os amadores em teste (ativos e os guardados na reserva do clube)
     * saem do elenco e se aposentam (continuam no mundo como aposentados, para o histórico de jogos
     * e gols não perder referência). Retorna quantos estavam ativos no clube de um técnico.
     */
    public static int limparSaveAntigo(Mundo m) {
        Set<Integer> ids = new LinkedHashSet<>();
        int doTecnico = 0;
        for (Clube c : m.clubes.values()) {
            for (int id : c.amadores) {
                ids.add(id);
                if (c.humano) doTecnico++;
            }
            ids.addAll(c.amadoresReserva);
            c.amadores.clear();
            c.amadoresReserva.clear();
            c.contadorAmador = 0;
        }
        for (Jogador j : m.jogadores) if (j.amador() && j.status != StatusJogador.APOSENTADO) ids.add(j.id);
        for (int id : ids) {
            Jogador j = m.jogador(id);
            if (j == null) continue;
            Clube c = m.clube(j.clubeId);
            if (c != null) {
                c.elenco.remove(Integer.valueOf(id));
                c.tatica.titulares.replaceAll(x -> x != null && x == id ? -1 : x);
                c.tatica.reservas.removeIf(x -> x != null && x == id);
            }
            if (j.status != StatusJogador.APOSENTADO) {
                j.status = StatusJogador.APOSENTADO;
                j.anoAposentadoria = m.ano;
            }
            j.clubeId = null;
            j.contrato = null;
        }
        return doTecnico;
    }
}
