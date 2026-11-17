package br.futebas.competicao;

import br.futebas.dados.Db;
import br.futebas.dados.Formato;

import java.util.ArrayList;
import java.util.List;

/**
 * Formato de reserva para quando o banco ainda não tem o formato pesquisado de uma competição
 * (o banco é construído em paralelo). O motor usa isto só para não parar, e registra um aviso.
 */
public final class FormatoPadrao {
    private FormatoPadrao() {}

    public static Formato para(Db.Competicao c, int participantes) {
        Formato f = new Formato();
        f.participantes = participantes > 1 ? participantes : null;
        f.fases = new ArrayList<>();
        String ambito = c.ambito() == null ? "nacional" : c.ambito();
        switch (ambito) {
            case "estadual" -> {
                Formato.Fase a = new Formato.Fase("Primeira fase", "pontos_corridos");
                a.turnos = 1;
                a.classificam = participantes >= 8 ? 4 : 2;
                Formato.Fase b = new Formato.Fase("Finais", "mata_mata");
                b.ida_volta = true;
                b.desempate = "penaltis";
                f.fases.add(a);
                f.fases.add(b);
                f.rebaixados = participantes >= 10 ? 2 : 0;
            }
            case "continental" -> {
                Formato.Fase a = new Formato.Fase("Fase de grupos", "grupos");
                a.grupos = Math.max(1, participantes / 4);
                a.turnos = 2;
                a.classificam = 2;
                Formato.Fase b = new Formato.Fase("Mata-mata", "mata_mata");
                b.ida_volta = true;
                b.desempate = "penaltis";
                b.final_jogo_unico = true;
                f.fases.add(a);
                f.fases.add(b);
            }
            case "selecoes" -> {
                Formato.Fase a = new Formato.Fase("Fase de grupos", "grupos");
                a.grupos = Math.max(1, participantes / 4);
                a.turnos = 1;
                a.classificam = 2;
                Formato.Fase b = new Formato.Fase("Mata-mata", "mata_mata");
                b.desempate = "prorrogacao_penaltis";
                f.fases.add(a);
                f.fases.add(b);
            }
            case "copa_nacional" -> {
                Formato.Fase a = new Formato.Fase("Mata-mata", "mata_mata");
                a.ida_volta = true;
                a.desempate = "penaltis";
                f.fases.add(a);
            }
            default -> {
                if (c.divisao() == null) { // copa nacional antiga marcada como nacional
                    Formato.Fase a = new Formato.Fase("Mata-mata", "mata_mata");
                    a.ida_volta = true;
                    a.desempate = "penaltis";
                    f.fases.add(a);
                } else if (participantes > 24) {
                    Formato.Fase a = new Formato.Fase("Grupos", "grupos");
                    a.grupos = Math.max(2, participantes / 8);
                    a.turnos = 2;
                    a.classificam = 2;
                    Formato.Fase b = new Formato.Fase("Mata-mata", "mata_mata");
                    b.ida_volta = true;
                    b.desempate = "penaltis";
                    f.fases.add(a);
                    f.fases.add(b);
                    f.rebaixados = Math.min(8, participantes / 6);
                    f.promovidos = 4;
                } else {
                    Formato.Fase a = new Formato.Fase("Pontos corridos", "pontos_corridos");
                    a.turnos = 2;
                    f.fases.add(a);
                    int n = Math.max(2, participantes);
                    f.rebaixados = Math.min(4, n / 5);
                    f.promovidos = c.divisao() > 1 ? Math.min(4, n / 5) : 0;
                }
            }
        }
        f.observacoes = "Formato de reserva do motor (o banco ainda não tem o formato pesquisado).";
        return f;
    }

    static Formato.Vaga vaga(String para, Integer... pos) {
        Formato.Vaga v = new Formato.Vaga();
        v.para = para;
        v.posicoes = List.of(pos);
        return v;
    }
}
