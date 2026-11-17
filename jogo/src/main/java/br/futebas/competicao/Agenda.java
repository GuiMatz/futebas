package br.futebas.competicao;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Regra do jogo: "não pode ter dois jogos no mesmo dia usando o mesmo time". O motor joga por
 * semana; o dia ({@link Partida#dia}) de cada jogo sai da semana da data da competição, e duas
 * competições do mesmo time (liga + copa, liga + continental, Kings Liga + Copa...) caíam no mesmo
 * dia. Aqui, jogo a jogo: se um time tem dois jogos (não jogados) no mesmo dia, o de menor
 * prioridade (liga &gt; continental &gt; copa nacional &gt; estadual &gt; amistoso) vai para o dia livre mais
 * perto <b>na mesma semana</b> (a semana do motor não muda), livre para os dois times, de preferência
 * com 2 dias de descanso, sem passar à frente das outras datas da mesma competição desse time.
 */
public final class Agenda {
    private Agenda() {}

    /** Prioridade da competição (maior fica no dia). */
    public static int prioridade(Torneio t) {
        String a = t.ambito == null ? "" : t.ambito;
        return switch (a) {
            case "nacional" -> t.divisao != null ? 6 : 4;
            case "kings" -> t.id != null && t.id.contains("copa") ? 4 : 6;
            case "selecoes" -> 6;
            case "continental", "mundial", "intercontinental" -> 5;
            case "copa_nacional" -> 4;
            case "estadual" -> 3;
            case "amistoso" -> 1;
            default -> 2;
        };
    }

    /** Pares (time, dia) com mais de um jogo, contando jogados e não jogados. */
    public static int colisoes(List<Torneio> torneios) {
        Map<String, Map<Long, Integer>> n = new HashMap<>();
        for (Torneio t : torneios) {
            for (Partida p : t.partidas) {
                for (String time : new String[] {p.mandante, p.visitante}) {
                    if (time == null) continue;
                    n.computeIfAbsent(time, x -> new HashMap<>()).merge(p.dia, 1, Integer::sum);
                }
            }
        }
        int c = 0;
        for (Map<Long, Integer> m : n.values()) for (int v : m.values()) if (v > 1) c++;
        return c;
    }

    /** Exemplos de colisão ("time dia: comp#id, comp#id"), para os testes e o diagnóstico. */
    public static List<String> exemplos(List<Torneio> torneios, int n) {
        Map<String, Map<Long, List<Partida>>> m = new TreeMap<>();
        for (Torneio t : torneios) for (Partida p : t.partidas) for (String time : new String[] {p.mandante, p.visitante}) {
            if (time == null) continue;
            m.computeIfAbsent(time, x -> new TreeMap<>()).computeIfAbsent(p.dia, x -> new ArrayList<>()).add(p);
        }
        List<String> l = new ArrayList<>();
        for (Map.Entry<String, Map<Long, List<Partida>>> e : m.entrySet()) for (Map.Entry<Long, List<Partida>> d : e.getValue().entrySet()) {
            if (d.getValue().size() < 2 || l.size() >= n) continue;
            StringBuilder sb = new StringBuilder(e.getKey() + " " + java.time.LocalDate.ofEpochDay(d.getKey()) + ":");
            for (Partida p : d.getValue()) sb.append(' ').append(p.torneioId).append('#').append(p.id).append("(d").append(p.data).append(",sa").append(p.sa).append(')');
            l.add(sb.toString());
        }
        return l;
    }

    /** Resolve as colisões dos jogos ainda não jogados. Devolve quantos jogos mudaram de dia. */
    public static int resolver(List<Torneio> torneios) {
        return resolver(torneios, null);
    }

    /**
     * Só para os {@code times} (null = todos): usado quando um torneio cria partidas na hora de
     * jogá-las (fase nova, rodada de mata-mata), sem varrer o mundo inteiro de novo.
     */
    public static int resolver(List<Torneio> torneios, Set<String> times) {
        Set<String> envolvidos = null;
        if (times != null) {
            // os adversários desses times também entram (o dia novo tem de ser livre para os dois)
            envolvidos = new HashSet<>(times);
            for (Torneio t : torneios) for (Partida p : t.partidas) {
                if (p.mandante == null || p.visitante == null) continue;
                if (times.contains(p.mandante) || times.contains(p.visitante)) {
                    envolvidos.add(p.mandante);
                    envolvidos.add(p.visitante);
                }
            }
        }
        Map<Partida, Torneio> dono = new HashMap<>();
        Map<String, List<Partida>> porTime = new TreeMap<>();
        for (Torneio t : torneios) {
            for (Partida p : t.partidas) {
                if (p.mandante == null || p.visitante == null) continue;
                if (envolvidos != null && !envolvidos.contains(p.mandante) && !envolvidos.contains(p.visitante)) continue;
                dono.put(p, t);
                porTime.computeIfAbsent(p.mandante, x -> new ArrayList<>()).add(p);
                porTime.computeIfAbsent(p.visitante, x -> new ArrayList<>()).add(p);
            }
        }
        int movidas = 0;
        for (Map.Entry<String, List<Partida>> e : porTime.entrySet()) {
            if (times != null && !times.contains(e.getKey())) continue;
            Map<Long, List<Partida>> porDia = new TreeMap<>();
            for (Partida p : e.getValue()) porDia.computeIfAbsent(p.dia, x -> new ArrayList<>()).add(p);
            for (List<Partida> mesmoDia : porDia.values()) {
                if (mesmoDia.size() < 2) continue;
                // fica: o já jogado, senão o de maior prioridade (empate: o que já estava antes na lista)
                List<Partida> ordem = new ArrayList<>(mesmoDia);
                ordem.sort(Comparator.comparing((Partida p) -> !p.jogada).thenComparing(p -> -prioridade(dono.get(p))));
                for (int i = 1; i < ordem.size(); i++) {
                    Partida p = ordem.get(i);
                    if (p.jogada || p.dia != ordem.get(0).dia) continue;
                    Long novo = diaLivre(p, dono.get(p), porTime);
                    if (novo == null) continue;
                    p.dia = novo;
                    movidas++;
                }
            }
        }
        return movidas;
    }

    /** O melhor dia livre na semana do jogo para os dois times (null = não há). */
    private static Long diaLivre(Partida p, Torneio t, Map<String, List<Partida>> porTime) {
        LocalDate ini = br.futebas.mundo.Calendario.inicio(p.sa);
        int ano = br.futebas.mundo.Calendario.ano(p.sa);
        // limites: as datas vizinhas da mesma competição para esses times continuam antes/depois
        long min = Long.MIN_VALUE, max = Long.MAX_VALUE;
        Set<Long> ocupados = new HashSet<>();
        List<Long> outros = new ArrayList<>();
        for (String time : new String[] {p.mandante, p.visitante}) {
            for (Partida q : porTime.getOrDefault(time, List.of())) {
                if (q == p) continue;
                ocupados.add(q.dia);
                outros.add(q.dia);
                if (q.torneioId != null && q.torneioId.equals(p.torneioId)) {
                    if (q.data < p.data) min = Math.max(min, q.dia);
                    else if (q.data > p.data) max = Math.min(max, q.dia);
                }
            }
        }
        Long melhor = null;
        long melhorFolga = -1, melhorDist = Long.MAX_VALUE;
        for (int k = 0; k < 7; k++) {
            LocalDate d = ini.plusDays(k);
            if (d.getYear() != ano) continue;
            long dia = d.toEpochDay();
            if (dia == p.dia || ocupados.contains(dia) || dia <= min || dia >= max) continue;
            long folga = Long.MAX_VALUE;
            for (long o : outros) folga = Math.min(folga, Math.abs(o - dia));
            folga = Math.min(folga, 3); // 2 dias de descanso já basta; mais não desempata
            long dist = Math.abs(dia - p.dia);
            if (folga > melhorFolga || folga == melhorFolga && dist < melhorDist) {
                melhor = dia;
                melhorFolga = folga;
                melhorDist = dist;
            }
        }
        return melhor;
    }
}
