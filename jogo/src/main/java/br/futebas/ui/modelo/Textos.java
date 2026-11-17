package br.futebas.ui.modelo;

import br.futebas.competicao.Partida;
import br.futebas.competicao.Torneio;
import br.futebas.financas.Financas;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Contrato;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.StatusJogador;
import br.futebas.mundo.TipoContrato;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Textos curtos e formatação para as telas (tudo em português do Brasil). */
public final class Textos {
    private Textos() {}

    /** Nome curto de uma Kings League: "Kings League Brasil", "Kings League Espanha"... (sem patrocinador). */
    public static String ligaKings(String id, String nomeOriginal) {
        if (id == null) return nomeOriginal;
        return switch (id) {
            case "kings-brasil" -> "Kings League Brasil";
            case "kings-espanha" -> "Kings League Espanha";
            case "kings-italia" -> "Kings League Itália";
            case "kings-franca" -> "Kings League França";
            case "kings-alemanha" -> "Kings League Alemanha";
            case "kings-americas" -> "Kings League Américas";
            case "kings-mena" -> "Kings League MENA";
            case "kings-ios" -> "Kings League IOS";
            case "kings-world-cup-clubs" -> "Kings World Cup Clubs";
            case "kings-world-cup-nations" -> "Kings World Cup Nations";
            default -> nomeOriginal;
        };
    }

    /** Ordem das Kings Leagues nas listas (Brasil primeiro, mundiais por último). */
    public static int ordemLigaKings(String id) {
        List<String> o = List.of("kings-brasil", "kings-espanha", "kings-italia", "kings-franca", "kings-alemanha",
                "kings-americas", "kings-mena", "kings-ios", "kings-world-cup-clubs", "kings-world-cup-nations");
        int i = o.indexOf(id);
        return i < 0 ? 50 : i;
    }

    private static final String[] MESES = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};

    /** Dinheiro no formato das Opções: abreviado ("R$ 46,5 mi", padrão) ou completo ("R$ 46.500.000"). */
    public static String dinheiro(long v) {
        return br.futebas.ui.Opcoes.moeda() == br.futebas.ui.Opcoes.Moeda.COMPLETA ? dinheiroCompleto(v) : Financas.formatar(v);
    }

    public static String dinheiroCompleto(long v) {
        java.text.DecimalFormat f = new java.text.DecimalFormat("#,##0", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.forLanguageTag("pt-BR")));
        return (v < 0 ? "-R$ " : "R$ ") + f.format(Math.abs(v));
    }

    public static String mes(int semana) {
        int i = Math.max(0, Math.min(11, (semana - 1) * 12 / 52));
        return MESES[i];
    }

    public static String data(int ano, int semana) {
        return "Semana " + semana + " · " + mes(semana) + "/" + ano;
    }

    public static String divisao(int d) {
        return switch (d) {
            case 1 -> "Série A";
            case 2 -> "Série B";
            case 3 -> "Série C";
            case 4 -> "Série D";
            default -> "Sem divisão";
        };
    }

    public static String divisao(Integer d) {
        return d == null ? "—" : divisao(d.intValue());
    }

    /** Divisão no vocabulário do país: "Série A" no Brasil, "1ª divisão" nos outros. */
    public static String divisao(String pais, Integer d) {
        if (d == null || d <= 0) return "sem divisão nacional";
        return "BRA".equals(pais) ? divisao(d.intValue()) : d + "ª divisão";
    }

    public static String contrato(Jogador j) {
        Contrato c = j.contrato;
        if (c == null) return "—";
        if (c.fim == Integer.MAX_VALUE) return "—";
        int ano = (c.fim - 1) / 52;
        int sem = c.fim - ano * 52;
        String s = mes(sem) + "/" + ano;
        return c.tipo == TipoContrato.EMPRESTIMO ? "Empr. até " + s : s;
    }

    public static String condicao(Jogador j) {
        if (j.lesaoSemanas > 0) return "Lesionado (" + j.lesaoSemanas + " sem.)";
        if (j.suspensao > 0) return "Suspenso (" + j.suspensao + ")";
        if (j.status == StatusJogador.APOSENTADO) return "Aposentado";
        return "Apto";
    }

    public static String situacao(Mundo m, Jogador j) {
        return switch (j.status) {
            case APOSENTADO -> "Aposentado";
            case LIVRE, DISPENSADO -> "Sem clube";
            default -> {
                Clube c = m.clube(j.clubeId);
                yield c == null ? "Sem clube" : c.nome;
            }
        };
    }

    public static String nomeClube(Mundo m, String id) {
        Clube c = m.clube(id);
        return c == null ? (id == null ? "—" : m.nomeParticipante(id)) : c.nome;
    }

    /** Até 3 letras para o escudo: "Flamengo" → FLA, "São Paulo" → SP, "Vasco da Gama" → VG. */
    public static String iniciais(String nome) {
        if (nome == null || nome.isBlank()) return "?";
        Set<String> ignorar = Set.of("de", "da", "do", "das", "dos", "e", "esporte", "clube", "futebol", "ec", "fc", "sc", "ac");
        String[] partes = nome.trim().split("[\\s\\-]+");
        StringBuilder sb = new StringBuilder();
        int validas = 0;
        for (String p : partes) if (!ignorar.contains(p.toLowerCase(Locale.ROOT))) validas++;
        if (validas <= 1) {
            String w = partes[0];
            for (String p : partes) if (!ignorar.contains(p.toLowerCase(Locale.ROOT))) w = p;
            String limpa = w.replaceAll("[^\\p{L}0-9]", "");
            return limpa.substring(0, Math.min(3, limpa.length())).toUpperCase(Locale.ROOT);
        }
        for (String p : partes) {
            if (ignorar.contains(p.toLowerCase(Locale.ROOT)) || p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (sb.length() == 3) break;
        }
        return sb.toString();
    }

    public static String fase(Torneio t, Partida p) {
        if (p.fase < 0 || p.fase >= t.fases.size()) return "";
        String nome = t.fases.get(p.fase).nome;
        if (p.rodadaMata >= 0 && t.fases.get(p.fase).pernas > 1) nome += p.perna == 0 ? " (ida)" : " (volta)";
        return nome == null ? "" : nome;
    }

    /** "V", "E" ou "D" do ponto de vista do clube (com pênaltis, se houve). */
    public static String resultadoPara(Partida p, String clubeId) {
        if (!p.jogada) return "";
        boolean casa = clubeId.equals(p.mandante);
        int pro = casa ? p.golsMandante : p.golsVisitante, contra = casa ? p.golsVisitante : p.golsMandante;
        if (pro == contra && p.penaltisMandante != null) {
            int pp = casa ? p.penaltisMandante : p.penaltisVisitante, pc = casa ? p.penaltisVisitante : p.penaltisMandante;
            return pp > pc ? "V" : "D";
        }
        return pro > contra ? "V" : pro == contra ? "E" : "D";
    }

    public static String ordinal(int n) {
        return n + "º";
    }

    /** Potencial mostrado com erro (como na peneira do Brasfoot): uma faixa, nunca o número exato. */
    public static String potencialEstimado(Jogador j, int ano) {
        if (j.idade(ano) > 24) return "—";
        int erro = 3 + Math.floorMod(j.id * 7, 5);
        int centro = (int) Math.round(j.potencial + (Math.floorMod(j.id * 13, 7) - 3));
        int lo = Math.max(1, centro - erro), hi = Math.min(99, centro + erro);
        return lo + "–" + hi;
    }

    public static String pe(String pe) {
        if (pe == null) return "—";
        return switch (pe) {
            case "D" -> "Direito";
            case "E" -> "Esquerdo";
            case "A" -> "Ambos";
            default -> pe;
        };
    }

    public static String pais(String iso) {
        if (iso == null) return "—";
        br.futebas.dados.BancoMundo db = br.futebas.ui.Sessao.bancoCarregado() ? br.futebas.ui.Sessao.banco() : null;
        br.futebas.dados.Db.Pais p = db == null ? null : db.pais(iso);
        return p != null && p.nome() != null ? p.nome() : iso;
    }

    /** Nome da divisão do clube no país dele (o nome real da liga, se estiver em andamento). */
    public static String divisaoDe(Mundo m, Clube c) {
        if (c == null) return "";
        if (c.divisao <= 0) return "Sem divisão nacional";
        for (Torneio t : m.torneios) {
            if ("nacional".equals(t.ambito) && c.pais.equals(t.pais) && t.divisao != null && t.divisao == c.divisao) return curto(t.nome);
        }
        return "BRA".equals(c.pais) ? divisao(c.divisao) : c.divisao + "ª divisão";
    }

    /** Nome curto de competição para listas e tabelas. */
    public static String curto(String nome) {
        if (nome == null) return "";
        String s = nome.replace("Campeonato Brasileiro ", "Brasileirão ").replace("Campeonato ", "");
        int p = s.indexOf(" (");
        if (p > 6) s = s.substring(0, p);
        return s;
    }

    /** Porte do clube pela reputação (escala do Brasfoot: municipal a mundial). */
    public static String porte(double reputacao) {
        if (reputacao >= 85) return "Mundial";
        if (reputacao >= 70) return "Continental";
        if (reputacao >= 52) return "Nacional";
        if (reputacao >= 36) return "Regional";
        if (reputacao >= 20) return "Estadual";
        return "Municipal";
    }

    private static final String[] DIAS = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};
    private static final String[] MESES_LONGOS = {"janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto",
            "setembro", "outubro", "novembro", "dezembro"};

    /** "14 de março de 2026". */
    public static String dataLonga(java.time.LocalDate d) {
        return d.getDayOfMonth() + " de " + MESES_LONGOS[d.getMonthValue() - 1] + " de " + d.getYear();
    }

    /** "sáb, 14/03". */
    public static String dataCurta(java.time.LocalDate d) {
        return DIAS[d.getDayOfWeek().getValue() - 1] + ", " + String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
    }

    public static String milhar(long v) {
        return String.format("%,d", v).replace(',', '.');
    }

    public static String porcento(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v);
    }

    public static String umaCasa(double v) {
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }
}
