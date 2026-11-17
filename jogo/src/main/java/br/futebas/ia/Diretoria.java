package br.futebas.ia;

import br.futebas.mundo.Clube;
import br.futebas.mundo.Contrato;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Rng;
import br.futebas.mundo.Tecnico;
import br.futebas.partida.Estilo;
import br.futebas.partida.Formacoes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Confiança da diretoria e da torcida (Brasfoot), demissão de técnicos da IA, demissão e
 * convites do técnico humano (decisão 14.4: ele pode ser demitido e trocar de clube).
 */
public final class Diretoria {
    private final Mundo m;
    private final Rng rng;
    /** Seleções (para convites ao técnico). */
    public br.futebas.selecao.Selecoes selecoes;
    /** Kings League (convites de times da Kings ao técnico humano). */
    public br.futebas.kings.Kings kings;

    /** Mercado de técnicos (o Simulador liga). */
    public Treinadores treinadores;
    /** Noticiário (o Simulador liga). */
    public br.futebas.historia.Noticiario noticiario;

    public Diretoria(Mundo m) {
        this.m = m;
        this.rng = m.rng;
    }

    /** Depois de cada jogo: confiança sobe ou desce conforme o resultado esperado. */
    public void depoisDoJogo(Clube c, Clube adv, int pro, int contra, boolean mandante, double forcaC, double forcaAdv) {
        if (c.virtual) return;
        double esperado = 1.35 + 1.1 * Math.tanh((forcaC - forcaAdv + (mandante ? 3 : -3)) / 8.0);
        int pts = pro > contra ? 3 : pro == contra ? 1 : 0;
        double delta = (pts - esperado) * 3.0;
        c.confiancaDiretoria = clamp(c.confiancaDiretoria + delta);
        double torcida = delta;
        if (adv != null && c.rivais.contains(adv.id)) torcida *= 2;
        if (contra - pro >= 4) torcida -= 8;
        c.confiancaTorcida = clamp(c.confiancaTorcida + torcida);
        c.registrarForma(pts);
        if (c.humano && adv != null && c.rivais.contains(adv.id)) {
            if (pts == 3) m.mensagemDoClube(c.id, "torcida", "Festa no clássico", "A torcida comemora a vitória sobre o " + adv.nome + ".");
            else if (pts == 0) m.mensagemDoClube(c.id, "torcida", "Torcida irritada", "A derrota para o " + adv.nome + " deixou a torcida na bronca.");
        }
    }

    /** Toda semana: confiança volta devagar ao normal; IA demite; humano pode ser demitido. */
    public void semana() {
        for (Clube c : m.clubes.values()) {
            if (c.virtual) continue;
            c.semanasDesdeTroca++;
            c.confiancaDiretoria += (55 - c.confiancaDiretoria) * 0.02;
            c.confiancaTorcida += (55 - c.confiancaTorcida) * 0.02;
            if (c.dinheiro < 0 && c.divida > 0 && -c.dinheiro > c.divida) c.confiancaDiretoria -= 1; // abismo financeiro
            if (c.humano) continue;
            if (c.confiancaDiretoria < limiteDemissaoIA(c) && c.semanasDesdeTroca > 8 && c.jogosTemp >= 5) demitirIA(c);
        }
        // cada técnico humano (multi-treinador: um de cada vez, com as listas dele)
        m.paraCadaTecnico(() -> {
            Tecnico t = m.tecnico;
            if (t != null && t.clubeId != null) {
                Clube c = m.clube(t.clubeId);
                if (c.confiancaDiretoria < 30 && c.confiancaDiretoria >= 15 && m.semana % 4 == 0) {
                    m.mensagem("diretoria", "Diretoria preocupada", "A diretoria do " + c.nome + " cobra melhores resultados.");
                }
                if (c.confiancaDiretoria < br.futebas.Balanceamento.DEMISSAO_DIRETORIA && c.jogosTemp >= 6) demitirHumano(c, "Demitido");
            }
            expirarConvites();
        });
        expirarPropostas();
        processarVagas();
    }

    private void demitirIA(Clube c) {
        c.demissoesTecnico++;
        c.semanasDesdeTroca = 0;
        br.futebas.mundo.Treinador antigo = m.treinador(c.treinadorId), novo = null;
        if (treinadores != null) {
            treinadores.demitir(c);
            novo = treinadores.contratar(c, antigo);
        }
        if (noticiario != null) noticiario.tecnicoDemitido(c, antigo == null ? null : antigo.nome, novo == null ? null : novo.nome);
        c.confiancaDiretoria = 55;
        Estilo[] estilos = Estilo.values();
        c.tatica.estilo = estilos[rng.inteiro(estilos.length)];
        List<String> f = Formacoes.nomes();
        c.tatica.formacao = f.get(rng.inteiro(f.size()));
        m.historia.estat(m.ano).demissoesTecnicos++;
        if (br.futebas.mundo.Sonda.Atual.s != null) br.futebas.mundo.Sonda.Atual.s.demissao(c);
        abrirVaga(c, "Técnico demitido");
        m.paraCadaTecnico(() -> convidarHumano(c));
    }

    private void demitirHumano(Clube c, String motivo) {
        Tecnico t = m.tecnico;
        m.registrarTemporadaTecnico("Demitido");
        c.humano = false;
        c.confiancaDiretoria = 55;
        c.semanasDesdeTroca = 0;
        t.clubeId = null;
        t.demissoes++;
        t.reputacao = Math.max(5, t.reputacao - 5);
        m.mensagem("diretoria", motivo, "A diretoria do " + c.nome + " decidiu encerrar o seu trabalho. Você está livre no mercado.");
        if (noticiario != null) noticiario.humanoDemitido(c);
        m.historia.evento(m.ano, m.semana, "tecnico", t.nome + " deixa o " + c.nome + ".");
        // clubes menores fazem convites
        List<Clube> cand = new ArrayList<>();
        for (Clube x : m.clubes.values()) if (!x.virtual && x.existe && x != c && !x.humano && x.reputacao <= c.reputacao + 5) cand.add(x);
        cand.sort(Comparator.comparingDouble((Clube x) -> Math.abs(x.reputacao - (c.reputacao - 10))).thenComparing(x -> x.id));
        for (int i = 0; i < Math.min(3, cand.size()); i++) proporAoHumano(cand.get(i));
    }

    /** Clube que demitiu o técnico pode convidar o humano, se a reputação dele permitir. */
    private void convidarHumano(Clube c) {
        Tecnico t = m.tecnico;
        if (t == null) return;
        Clube atual = m.clube(t.clubeId);
        // técnico da Kings (sem clube) vale como um clube pequeno; no máximo 3 convites de clube pendentes
        double repAtual = atual != null ? atual.reputacao : t.kingsTimeId != null ? 20 : 0;
        if (c.reputacao <= repAtual) return;
        int pendentes = 0;
        for (PropostaTecnico p : m.propostasTecnico) if ("pendente".equals(p.estado) && p.clubeId != null) pendentes++;
        if (pendentes >= 3) return;
        if (t.reputacao < c.reputacao * 0.6) return;
        if (!rng.chance(0.5)) return;
        proporAoHumano(c);
    }

    public void proporAoHumano(Clube c) {
        for (PropostaTecnico p : m.propostasTecnico) if (c.id.equals(p.clubeId) && "pendente".equals(p.estado)) return;
        PropostaTecnico p = new PropostaTecnico();
        p.id = m.proximaProposta++;
        p.clubeId = c.id;
        p.clube = c.nome;
        p.ano = m.ano;
        p.semana = m.semana;
        p.semanaLimite = Contrato.semanaAbsoluta(m.ano, m.semana) + 4;
        m.propostasTecnico.add(p);
        // requisito: sem mensagem de proposta de emprego; o interesse aparece na aba Vagas
    }

    // ===================================================================== vagas de técnico (aba Vagas)
    /** A diretoria demitiu o técnico da IA: a vaga fica aberta de 1 a 4 semanas (até fechar com alguém). */
    void abrirVaga(Clube c, String motivo) {
        if (m.vagas == null) m.vagas = new ArrayList<>();
        int agora = m.semanaAbsoluta();
        for (Vaga v : m.vagas) if (v.aberta() && c.id.equals(v.clubeId)) return;
        Vaga v = new Vaga();
        v.id = m.proximaVaga++;
        v.clubeId = c.id;
        v.motivo = motivo;
        v.abertaEm = agora;
        // sem gastar o sorteio do mundo: a duração sai do id do clube e da semana
        v.fechaEm = agora + 1 + Math.floorMod(c.id.hashCode() * 31 + agora, 4);
        m.vagas.add(v);
        if (m.vagasRecusadas != null) m.vagasRecusadas.remove(c.id); // vaga nova: volta a aparecer
    }

    /** Chance de a diretoria aceitar a candidatura do humano (pela reputação dele × a do clube). */
    public double chanceCandidatura(Clube c) {
        Tecnico t = m.tecnico;
        if (t == null || c == null) return 0;
        double r = t.reputacao / Math.max(1, c.reputacao);
        return r >= 0.9 ? 0.9 : r >= 0.6 ? 0.45 : r >= 0.45 ? 0.15 : 0.03;
    }

    /** Toda semana: responde as candidaturas vencidas e fecha as vagas (o clube contratou um técnico). */
    private void processarVagas() {
        if (m.vagas == null) return;
        int agora = m.semanaAbsoluta();
        for (Vaga v : m.vagas) {
            if (!v.aberta()) continue;
            Clube c = m.clube(v.clubeId);
            if (c == null || c.humano) {
                v.estado = "fechada";
                continue;
            }
            if (v.respostaEm > 0 && v.resposta == null && agora >= v.respostaEm) {
                // multi-treinador: a resposta vai para quem se candidatou
                Tecnico cand = null;
                if (m.multi() && v.candidato != null) for (Tecnico x : m.tecnicos) if (v.candidato.equals(x.nome)) cand = x;
                m.comTecnico(cand, () -> {
                    boolean sim = rng.chance(chanceCandidatura(c));
                    v.resposta = sim ? "aceita" : "recusada";
                    if (sim) {
                        proporAoHumano(c);
                        v.fechaEm = Math.max(v.fechaEm, agora + 3); // espera a sua resposta
                        m.mensagem("vaga", "Candidatura aceita", "O " + c.nome + " aceitou a sua candidatura. Responda na aba Vagas.");
                    } else {
                        m.mensagem("vaga", "Candidatura recusada", "O " + c.nome + " preferiu outro técnico.");
                    }
                });
            }
            if (agora >= v.fechaEm && !(v.respostaEm > 0 && v.resposta == null)) v.estado = "fechada";
        }
        m.vagas.removeIf(v -> !v.aberta() && agora - v.fechaEm > 8);
    }

    /**
     * O humano aceita um convite. Carreira exclusiva (regra do jogo): ou clube, ou time da Kings.
     * Convite da Kings = deixa o clube; convite de clube = deixa o time da Kings. Seleção continua
     * sendo um cargo a mais.
     */
    public boolean aceitar(int propostaId) {
        for (PropostaTecnico p : m.propostasTecnico) {
            if (p.id != propostaId || !"pendente".equals(p.estado)) continue;
            if (p.kingsTimeId != null) {
                if (kings == null || kings.timeKings(p.kingsTimeId) == null) return false;
                Clube antigo = m.clube(m.tecnico.clubeId);
                if (antigo != null) {
                    m.registrarTemporadaTecnico("Saiu para o " + p.clube);
                    antigo.humano = false;
                    m.tecnico.clubeId = null;
                    m.historia.evento(m.ano, m.semana, "tecnico", m.tecnico.nome + " deixa o " + antigo.nome + " rumo ao " + p.clube + " (Kings League).");
                }
                if (antigo == null && m.tecnico.kingsTimeId != null && !m.tecnico.kingsTimeId.equals(p.kingsTimeId))
                    m.registrarTemporadaTecnico("Saiu para o " + p.clube); // troca de time na Kings: fecha a linha da Kings
                if (!kings.assumir(p.kingsTimeId)) return false;
                p.estado = "aceita";
                for (PropostaTecnico o : m.propostasTecnico) if (o != p && "pendente".equals(o.estado) && o.kingsTimeId != null) o.estado = "recusada";
                m.mensagem("kings", "Kings League", "Você é o novo técnico do " + p.clube + "."
                        + (antigo != null ? " Você deixou o " + antigo.nome + "." : ""));
                m.historia.evento(m.ano, m.semana, "tecnico", m.tecnico.nome + " assume o " + p.clube + " na Kings League.");
                return true;
            }
            if (p.selecaoPais != null) {
                br.futebas.mundo.Selecao s = m.selecao(p.selecaoPais);
                if (s == null) return false;
                br.futebas.mundo.Selecao antiga = m.tecnico.selecaoPais == null ? null : m.selecao(m.tecnico.selecaoPais);
                if (antiga != null) antiga.humano = false;
                s.humano = true;
                m.tecnico.selecaoPais = s.pais;
                p.estado = "aceita";
                m.mensagem("selecao", "Seleção " + s.nome, "Você é o novo técnico da seleção " + s.nome + ", sem deixar o seu clube.");
                m.historia.evento(m.ano, m.semana, "tecnico", m.tecnico.nome + " assume a seleção " + s.nome + ".");
                return true;
            }
            Clube novo = m.clube(p.clubeId);
            if (novo == null || m.multi() && novo.humano) return false; // multi: outro técnico humano chegou antes
            String timeKings = null;
            if (m.tecnico.kingsTimeId != null) {
                br.futebas.kings.LigaKings.TimeKings tk = kings == null ? null : kings.timeKings(m.tecnico.kingsTimeId);
                timeKings = tk == null ? m.tecnico.kingsTimeId : tk.nome;
                if (m.tecnico.clubeId == null) m.registrarTemporadaTecnico("Saiu para o " + novo.nome); // fecha a linha da Kings
                for (br.futebas.kings.LigaKings l : m.kings) for (br.futebas.kings.LigaKings.TimeKings t : l.times)
                    if (!m.multi() || t.id.equals(m.tecnico.kingsTimeId)) t.humano = false; // multi: só o time dele
                m.tecnico.kingsTimeId = null;
                m.historia.evento(m.ano, m.semana, "tecnico", m.tecnico.nome + " deixa o " + timeKings + " (Kings League) rumo ao " + novo.nome + ".");
            }
            Clube antigo = m.clube(m.tecnico.clubeId);
            if (antigo != null) m.registrarTemporadaTecnico("Saiu para o " + novo.nome);
            if (antigo != null) {
                antigo.humano = false;
                m.historia.evento(m.ano, m.semana, "tecnico", m.tecnico.nome + " deixa o " + antigo.nome + " rumo ao " + novo.nome + ".");
            }
            novo.humano = true;
            novo.confiancaDiretoria = 65;
            novo.semanasDesdeTroca = 0;
            m.tecnico.clubeId = novo.id;
            p.estado = "aceita";
            for (PropostaTecnico o : m.propostasTecnico) if (o != p && "pendente".equals(o.estado) && o.selecaoPais == null && o.kingsTimeId == null) o.estado = "recusada";
            m.mensagem("diretoria", "Novo clube", "Bem-vindo ao " + novo.nome + ". A diretoria confia no seu trabalho."
                    + (timeKings != null ? " Você deixou o " + timeKings + " na Kings League." : ""));
            if (noticiario != null) noticiario.humanoContratado(novo);
            return true;
        }
        return false;
    }

    private void expirarConvites() {
        int agora = m.semanaAbsoluta();
        for (PropostaTecnico p : m.propostasTecnico) if ("pendente".equals(p.estado) && p.semanaLimite < agora) p.estado = "expirada";
    }

    private void expirarPropostas() {
        int agora = m.semanaAbsoluta();
        for (PropostaJogador p : m.propostas) if ("pendente".equals(p.estado) && p.semanaLimite < agora) p.estado = "expirada";
    }

    /**
     * Balanço da temporada (antes da virada): compara o que se esperava do clube (tamanho dele
     * dentro da divisão) com o que aconteceu (colocação, títulos, acesso, rebaixamento, caixa).
     * A confiança passa a refletir esse balanço; campeão nunca termina com a diretoria insatisfeita.
     */
    public void avaliarTemporada(List<br.futebas.competicao.Torneio> torneios, java.util.Set<String> paises) {
        java.util.Map<String, List<br.futebas.competicao.Torneio>> porClube = new java.util.HashMap<>();
        for (br.futebas.competicao.Torneio t : torneios) {
            if ("kings".equals(t.ambito) || "amistoso".equals(t.ambito)) continue;
            for (String id : t.participantes) porClube.computeIfAbsent(id, k -> new ArrayList<>()).add(t);
        }
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe || !paises.contains(c.pais)) continue;
            double nota = 55;
            List<String> motivos = new ArrayList<>();
            boolean tituloGrande = false, tituloMenor = false, caiu = false, subiu = false;
            for (br.futebas.competicao.Torneio t : porClube.getOrDefault(c.id, List.of())) {
                boolean campeao = c.id.equals(t.campeao);
                if ("nacional".equals(t.ambito) && t.divisao != null) {
                    int n = t.classificacao.size();
                    int pos = t.posicaoFinal(c.id);
                    if (n == 0 || pos > n) continue;
                    int esperado = 1;
                    for (String id : t.participantes) {
                        Clube o = m.clube(id);
                        if (o != null && o != c && o.reputacao > c.reputacao) esperado++;
                    }
                    double rel = (double) (esperado - pos) / n; // >0: melhor que o esperado
                    nota += Math.max(-30, Math.min(30, rel * 70));
                    int prom = t.formato != null && t.formato.promovidos != null ? t.formato.promovidos : 0;
                    int reb = t.formato != null && t.formato.rebaixados != null ? t.formato.rebaixados : 0;
                    if (t.divisao > 1 && pos <= prom) subiu = true;
                    if (reb > 0 && pos > n - reb) caiu = true;
                    if (campeao) {
                        if (t.divisao == 1) tituloGrande = true;
                        else tituloMenor = true;
                    }
                    if (!campeao) motivos.add(pos + "º lugar " + br.futebas.mundo.Artigos.em(t.nome) + " " + t.nome + (pos < esperado ? " (acima do esperado)" : pos > esperado + 2 ? " (abaixo do esperado)" : ""));
                } else if (campeao) {
                    if ("estadual".equals(t.ambito)) {
                        tituloMenor = true;
                        nota += 8;
                    } else {
                        tituloGrande = true;
                    }
                }
                if (campeao) motivos.add(0, "título: " + t.nome);
            }
            if (tituloGrande) nota += 25;
            if (tituloMenor) nota += 10;
            if (subiu) {
                nota += 18;
                motivos.add("acesso");
            }
            if (caiu) {
                nota -= 25;
                motivos.add("rebaixamento");
            }
            long limite = br.futebas.financas.Financas.limiteEmprestimo(c, m.ano);
            if (c.dinheiro < 0 && c.divida >= limite * 0.8) {
                nota -= 10;
                motivos.add("finanças no vermelho");
            } else if (c.dinheiro > c.financas.saldoInicio && c.dinheiro > 0) {
                nota += 4;
            }
            if (tituloGrande || subiu) nota = Math.max(nota, 76);
            else if (tituloMenor) nota = Math.max(nota, 58);
            nota = clamp(nota);
            c.notaTemporada = nota;
            // a nova temporada começa com um mínimo de crédito (ninguém é demitido na 6ª rodada pelo ano anterior)
            c.confiancaDiretoria = Math.max(30, clamp(0.25 * c.confiancaDiretoria + 0.75 * nota));
            if (tituloGrande || subiu) c.confiancaDiretoria = Math.max(c.confiancaDiretoria, 75);
            c.vereditoDiretoria = motivos.isEmpty() ? null : String.join("; ", motivos.subList(0, Math.min(3, motivos.size())));
            // rebaixado: a IA quase sempre troca o técnico; o humano cai junto se o trabalho foi ruim
            // (antes ninguém era demitido pela queda: o humano caiu 4 vezes seguidas sem perder o emprego)
            if (caiu && !tituloGrande) {
                if (c.humano) {
                    Tecnico dono = m.tecnicoDoClube(c.id);
                    if (nota < br.futebas.Balanceamento.DEMISSAO_HUMANO_REBAIXADO && dono != null)
                        m.comTecnico(dono, () -> demitirHumano(c, "Demitido após o rebaixamento"));
                } else if (c.treinadorId != null && rng.chance(br.futebas.Balanceamento.DEMISSAO_IA_REBAIXADO)) {
                    demitirIA(c);
                }
            }
        }
    }

    /** Confiança abaixo da qual a diretoria da IA demite (países mais impacientes demitem antes). */
    static double limiteDemissaoIA(Clube c) {
        return br.futebas.Balanceamento.DEMISSAO_IA * br.futebas.Balanceamento.impaciencia(c.pais);
    }

    /** Fim de temporada: reputação do técnico humano e convites de clubes maiores. */
    public void fimDeTemporada(int titulos, double desempenho) {
        Tecnico t = m.tecnico;
        if (t == null) return;
        t.reputacao = Math.max(5, Math.min(100, t.reputacao + titulos * 6 + desempenho * 4));
        Clube atual = m.clube(t.clubeId);
        double base = atual == null ? 20 : atual.reputacao;
        List<Clube> cand = new ArrayList<>();
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe || c.humano) continue;
            if (c.reputacao > base && c.reputacao <= t.reputacao + 15 && c.confiancaDiretoria < 45) cand.add(c);
        }
        cand.sort(Comparator.comparingDouble((Clube c) -> -c.reputacao).thenComparing(c -> c.id));
        for (int i = 0; i < Math.min(2, cand.size()); i++) if (rng.chance(0.5)) proporAoHumano(cand.get(i));
        convidarParaSelecao();
    }

    /**
     * Convite de seleção (Brasfoot: após bons trabalhos): a seleção do país do clube do técnico,
     * se a reputação dele estiver à altura; senão, outra seleção do tamanho dele.
     */
    void convidarParaSelecao() {
        Tecnico t = m.tecnico;
        // multi-treinador: sem seleções (os jogos de seleção não têm a tela dividida)
        if (t == null || t.selecaoPais != null || selecoes == null || m.multi()) return;
        if (t.reputacao < br.futebas.Balanceamento.REPUTACAO_CONVITE_SELECAO) return;
        for (PropostaTecnico p : m.propostasTecnico) if (p.selecaoPais != null && "pendente".equals(p.estado)) return;
        Clube atual = m.clube(t.clubeId);
        br.futebas.mundo.Selecao escolhida = null;
        List<br.futebas.mundo.Selecao> rk = selecoes.ranking();
        if (atual != null) {
            br.futebas.mundo.Selecao s = m.selecao(atual.pais);
            if (s != null && prestigio(s, rk) <= t.reputacao + 5) escolhida = s;
        }
        if (escolhida == null) {
            for (br.futebas.mundo.Selecao s : rk) {
                if (s.convocados.size() < 11) continue;
                if (prestigio(s, rk) <= t.reputacao && rng.chance(0.4)) {
                    escolhida = s;
                    break;
                }
            }
        }
        if (escolhida == null || escolhida.humano) return;
        PropostaTecnico p = new PropostaTecnico();
        p.id = m.proximaProposta++;
        p.selecaoPais = escolhida.pais;
        p.clube = "Seleção " + escolhida.nome;
        p.ano = m.ano;
        p.semana = m.semana;
        p.semanaLimite = Contrato.semanaAbsoluta(m.ano, m.semana) + 6;
        m.propostasTecnico.add(p);
        var msg = new br.futebas.mundo.Mensagem(m.ano, m.semana, "proposta", "Convite da seleção " + escolhida.nome,
                "A federação quer você como técnico da seleção " + escolhida.nome + " (sem deixar o clube).");
        msg.propostaId = p.id;
        m.mensagens.add(msg);
    }

    /** Prestígio (0..100) de uma seleção pela posição no ranking. */
    static double prestigio(br.futebas.mundo.Selecao s, List<br.futebas.mundo.Selecao> rk) {
        int pos = rk.indexOf(s);
        return Math.max(10, 100 - pos * 1.5);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }
}
