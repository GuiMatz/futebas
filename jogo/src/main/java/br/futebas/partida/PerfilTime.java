package br.futebas.partida;

import br.futebas.mundo.Posicao;

import java.util.List;

/**
 * Como o time joga, calculado jogador a jogador (port de {@code team_profile}): índices de
 * ataque, defesa e meio ponderados pelos pesos de cada vaga, volumes e goleiro.
 */
public final class PerfilTime {
    public static final double CAPITAO_MEIO = 1.5;

    public final double ataque, volumeAtaque, defesa, volumeDefesa, meio, goleiro;
    /**
     * Setores para "concentrar ataques": ataque e defesa pelo meio e pelas laterais (somas
     * ponderadas por um divisor fixo, como no Brasfoot: faltar gente no setor enfraquece o setor).
     */
    public double ataqueMeio, ataqueLados, defesaMeio, defesaLados;

    /** Divisores fixos (o que um 4-4-2 / 4-3-3 normal soma em cada setor). */
    static final double DIV_ATQ_MEIO = 2.75, DIV_ATQ_LADOS = 3.0, DIV_DEF_MEIO = 2.75, DIV_DEF_LADOS = 2.6;

    static double atqMeio(Posicao p) {
        return switch (p) { case CA, MEI -> 1.0; case MC -> 0.5; case VOL -> 0.25; default -> 0; };
    }

    static double atqLados(Posicao p) {
        return switch (p) { case PE, PD -> 1.0; case LE, LD -> 0.5; default -> 0; };
    }

    static double defMeio(Posicao p) {
        return switch (p) { case ZAG -> 1.0; case VOL -> 0.5; case MC -> 0.25; default -> 0; };
    }

    static double defLados(Posicao p) {
        return switch (p) { case LE, LD -> 1.0; case PE, PD -> 0.3; default -> 0; };
    }

    private PerfilTime(double ataque, double volAtaque, double defesa, double volDefesa, double meio, double goleiro) {
        this.ataque = ataque;
        this.volumeAtaque = volAtaque;
        this.defesa = defesa;
        this.volumeDefesa = volDefesa;
        this.meio = meio;
        this.goleiro = goleiro;
    }

    public static PerfilTime de(List<Escalado> onze, Integer capitao) {
        double sa = 0, va = 0, sd = 0, vd = 0, sm = 0, vm = 0, gol = 0;
        boolean temCapitao = false;
        double am = 0, al = 0, dm = 0, dl = 0;
        for (Escalado e : onze) {
            double r = e.rende();
            Posicao v = e.vaga;
            am += atqMeio(v) * r;
            al += atqLados(v) * r;
            dm += defMeio(v) * r;
            dl += defLados(v) * r;
            sa += v.pesoAtaque * r;
            va += v.pesoAtaque;
            sd += v.pesoDefesa * r;
            vd += v.pesoDefesa;
            sm += v.pesoMeio * r;
            vm += v.pesoMeio;
            if (v == Posicao.GOL) gol = Math.max(gol, r);
            if (capitao != null && e.id == capitao) temCapitao = true;
        }
        double ataque = va > 0 ? sa / va : 0;
        double defesa = vd > 0 ? sd / vd : 0;
        double meio = vm > 0 ? sm / vm : 0;
        if (temCapitao) meio += CAPITAO_MEIO;
        if (gol == 0) gol = 25; // sem goleiro: alguém de linha improvisado
        PerfilTime p = new PerfilTime(ataque, va, defesa, vd, meio, gol);
        p.ataqueMeio = am / DIV_ATQ_MEIO;
        p.ataqueLados = al / DIV_ATQ_LADOS;
        p.defesaMeio = dm / DIV_DEF_MEIO;
        p.defesaLados = dl / DIV_DEF_LADOS;
        return p;
    }

    /**
     * Perfil com vagas vazias ({@code ausentes}: expulsos e quem saiu lesionado sem troca, que
     * continuam na vaga da formação). Requisito: o técnico pode remanejar alguém para a posição
     * do expulso, então o buraco tem de ficar no setor onde ele está. A vaga vazia não rende nada no
     * setor dela, mas o nível geral (ataque + defesa + meio) é o mesmo do perfil cheio: o "um a menos"
     * continua sendo cobrado só pelas constantes de expulsão do motor (a calibragem não muda). Goleiro:
     * quem estiver na vaga de goleiro; com a vaga vazia, vale o do perfil cheio (como antes).
     */
    public static PerfilTime de(List<Escalado> onze, Integer capitao, java.util.Set<Integer> ausentes) {
        if (ausentes == null || ausentes.isEmpty()) return de(onze, capitao);
        boolean algum = false;
        for (Escalado e : onze) if (ausentes.contains(e.id)) algum = true;
        if (!algum) return de(onze, capitao);
        // nível de referência: o time cheio, com quem saiu rendendo o mesmo em qualquer vaga (o
        // "fantasma" do expulso não pode piorar o time só porque o buraco mudou de lugar)
        List<Escalado> ref = new java.util.ArrayList<>(onze.size());
        for (Escalado e : onze) {
            if (!ausentes.contains(e.id)) ref.add(e);
            else {
                Escalado f = e.copia(e.vaga, e.forca, false);
                f.encaixe = 1.0;
                ref.add(f);
            }
        }
        PerfilTime cheio = de(ref, capitao);
        double sa = 0, va = 0, sd = 0, vd = 0, sm = 0, vm = 0, gol = -1;
        boolean temCapitao = false;
        double am = 0, al = 0, dm = 0, dl = 0;
        for (Escalado e : onze) {
            boolean fora = ausentes.contains(e.id);
            double r = fora ? 0 : e.rende();
            Posicao v = e.vaga;
            am += atqMeio(v) * r;
            al += atqLados(v) * r;
            dm += defMeio(v) * r;
            dl += defLados(v) * r;
            sa += v.pesoAtaque * r;
            va += v.pesoAtaque;
            sd += v.pesoDefesa * r;
            vd += v.pesoDefesa;
            sm += v.pesoMeio * r;
            vm += v.pesoMeio;
            if (v == Posicao.GOL && !fora) gol = Math.max(gol, r);
            if (!fora && capitao != null && e.id == capitao) temCapitao = true;
        }
        double ataque = va > 0 ? sa / va : 0, defesa = vd > 0 ? sd / vd : 0, meio = vm > 0 ? sm / vm : 0;
        if (temCapitao) meio += CAPITAO_MEIO;
        double soma = ataque + defesa + meio, alvo = cheio.ataque + cheio.defesa + cheio.meio;
        double k = soma > 0 ? alvo / soma : 1;
        PerfilTime p = new PerfilTime(ataque * k, va, defesa * k, vd, meio * k, gol < 0 ? cheio.goleiro : gol);
        double ka = cheio.ataque > 0 && ataque > 0 ? ataque * k / cheio.ataque : 1, kd = cheio.defesa > 0 && defesa > 0 ? defesa * k / cheio.defesa : 1;
        double fam = cheio.ataqueMeio > 0 ? am / DIV_ATQ_MEIO / cheio.ataqueMeio : 1, fal = cheio.ataqueLados > 0 ? al / DIV_ATQ_LADOS / cheio.ataqueLados : 1;
        double fdm = cheio.defesaMeio > 0 ? dm / DIV_DEF_MEIO / cheio.defesaMeio : 1, fdl = cheio.defesaLados > 0 ? dl / DIV_DEF_LADOS / cheio.defesaLados : 1;
        // os setores seguem o buraco (cada um com o que sobrou nele) e o nível do lado (ataque/defesa) depois do ajuste
        double ma = (fam + fal) / 2, md = (fdm + fdl) / 2;
        p.ataqueMeio = cheio.ataqueMeio * (ma > 0 ? fam / ma : 1) * ka;
        p.ataqueLados = cheio.ataqueLados * (ma > 0 ? fal / ma : 1) * ka;
        p.defesaMeio = cheio.defesaMeio * (md > 0 ? fdm / md : 1) * kd;
        p.defesaLados = cheio.defesaLados * (md > 0 ? fdl / md : 1) * kd;
        return p;
    }

    /** Volumes de referência (4-3-3), como no FUT.AE. */
    public static final double VOLUME_ATAQUE_REF, VOLUME_DEFESA_REF;

    static {
        double va = 0, vd = 0;
        for (Posicao p : Formacoes.vagas("4-3-3")) {
            va += p.pesoAtaque;
            vd += p.pesoDefesa;
        }
        VOLUME_ATAQUE_REF = va;
        VOLUME_DEFESA_REF = vd;
    }
}
