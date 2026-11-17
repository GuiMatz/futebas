package br.futebas.mundo;

import java.util.ArrayList;
import java.util.List;

/** O técnico humano. Não envelhece: dá para jogar quantos anos quiser. */
public class Tecnico {
    public String nome;
    /** Clube atual (null = desempregado). */
    public String clubeId;
    /** Seleção que ele comanda ao mesmo tempo (país ISO-3), se aceitou um convite. */
    public String selecaoPais;
    /** Time da Kings League que ele comanda (exclusivo: com time da Kings, clubeId é null). */
    public String kingsTimeId;
    public double reputacao = 30;
    public int jogos, vitorias, empates, derrotas;
    public int pontosRanking;
    /** J/V/E/D quando a linha atual da carreira começou (fim do ano anterior ou troca de clube). */
    public int[] marcoCarreira = new int[4];
    /**
     * Carreira na Kings League, separada da do futebol (jogo da Kings não tem empate: shootout decide).
     * Vitórias/derrotas incluem as decididas nos shootouts. Balanço 2026-11, rodada 2.
     */
    public int kingsJogos, kingsVitorias, kingsDerrotas, kingsVitoriasShootout, kingsDerrotasShootout;
    /** Contadores da Kings quando a linha atual da carreira começou (como {@link #marcoCarreira}). */
    public int[] marcoKings = new int[5];
    /** Confiança do presidente do time da Kings (0–100), guardada no save; null = começa em 60. */
    public Double confiancaKings;
    /** Jogos da Kings no time atual (os primeiros {@code Kings.KINGS_JOGOS_PROTEGIDOS} protegem da demissão). */
    public int kingsJogosNoTime;
    public List<String> titulos = new ArrayList<>();
    public int demissoes;
    /** Renovação automática dos contratos que vencem (opção do técnico). */
    public boolean renovacaoAutomatica;
    /**
     * Competições entregues ao auxiliar, pelo id base da competição ("bra-1", "uefa-champions",
     * "kings-brasil-split1"): vale para as rodadas seguintes e para a edição do ano que vem.
     */
    public java.util.Set<String> auxiliarCompeticoes = new java.util.TreeSet<>();
    /** Partidas avulsas com o auxiliar ("competicaoId#partidaId#ano": o id da partida se repete todo ano). */
    public java.util.Set<String> auxiliarPartidas = new java.util.TreeSet<>();
    /** Partidas que o técnico comanda mesmo com a competição delegada ("competicaoId#partidaId#ano"). */
    public java.util.Set<String> auxiliarExcecoes = new java.util.TreeSet<>();

    public static String chavePartida(String competicaoId, int partidaId, int ano) {
        return competicaoId + "#" + partidaId + "#" + ano;
    }

    /**
     * "Simular temporada": enquanto ligado, o auxiliar comanda TODOS os jogos (não vai para o save; as
     * delegações do técnico continuam como estavam e voltam a valer ao desligar).
     */
    public transient boolean auxiliarTudo;

    /** O auxiliar comanda esta partida (escalação automática, tática da IA, sem jogo ao vivo)? */
    public boolean delegado(String competicaoId, int partidaId, int ano) {
        if (auxiliarTudo) return true;
        String k = chavePartida(competicaoId, partidaId, ano);
        if (auxiliarExcecoes != null && auxiliarExcecoes.contains(k)) return false;
        return (auxiliarCompeticoes != null && auxiliarCompeticoes.contains(competicaoId))
                || (auxiliarPartidas != null && auxiliarPartidas.contains(k));
    }
    /** Só renova sozinho quem tem pelo menos esta força (0 = quem está no nível do time: força ≥ nível − 5). */
    public int renovacaoForcaMinima;

    // ---------------------------------------------------------------- modo multi-treinador
    /*
     * Coisas de cada técnico que, no jogo de um técnico só, ficam direto no Mundo (caixa de entrada,
     * convites de emprego, vagas recusadas, carreira). No modo multi, o técnico da vez usa as listas do
     * Mundo e os outros guardam as suas aqui ({@link Mundo#ativar}). null = está nas listas do Mundo.
     */
    public List<Mensagem> caixa;
    public List<br.futebas.ia.PropostaTecnico> convites;
    public List<String> recusadas;
    public List<br.futebas.historia.Historia.TemporadaTecnico> carreira;

    public Tecnico() {}

    public Tecnico(String nome) {
        this.nome = nome;
    }
}
