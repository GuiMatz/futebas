package br.futebas.partida;

/**
 * Números de uma partida até agora (lado 0 = mandante, 1 = visitante): o que a tela de trocas/intervalo
 * mostra lado a lado. Ao vivo vêm do motor ({@link AoVivo.Controle#estatisticas()}); no fim, do
 * {@link ResultadoPartida}. Só leitura: calcular isto nunca mexe no sorteio do jogo.
 */
public record Estatisticas(int minuto, int[] posse, int[] finalizacoes, int[] noAlvo, int[] escanteios, int[] faltas,
                           int[] amarelos, int[] vermelhos, int[] impedimentos) {

    /** Os números finais de um resultado (amarelos e vermelhos contados pelas listas). */
    public static Estatisticas de(ResultadoPartida r) {
        int[] am = new int[2], vm = new int[2];
        for (int[] x : r.amarelos) if (x[0] == 0 || x[0] == 1) am[x[0]]++;
        for (int[] x : r.vermelhos) if (x[0] == 0 || x[0] == 1) vm[x[0]]++;
        return new Estatisticas(r.prorrogacao ? 120 : 90, r.posse.clone(), r.chutes.clone(), r.noGol.clone(), r.escanteios.clone(),
                r.faltas.clone(), am, vm, r.impedimentos.clone());
    }
}
