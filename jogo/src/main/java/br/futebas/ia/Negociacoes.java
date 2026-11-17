package br.futebas.ia;

import br.futebas.financas.Financas;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mensagem;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Rng;
import br.futebas.mundo.StatusJogador;
import br.futebas.mundo.TipoContrato;

import java.util.ArrayList;
import java.util.List;

/**
 * Negociações do técnico humano (regras do jogo): nada acontece na hora.
 * <ul>
 *   <li>Proposta enviada: o outro clube responde depois de 1–7 dias (1–2 na última semana da
 *       janela); 10% das vezes não responde e a proposta expira em 7 dias. Resposta: aceita,
 *       contraproposta (valor pedido) ou recusa.</li>
 *   <li>Taxa aceita (ou jogador livre): o técnico propõe salário e duração ao jogador, que aceita,
 *       pede mais (contraproposta) ou recusa.</li>
 *   <li>Proposta recebida (a IA quer um jogador seu): expira em 7 dias; aceitar, recusar ou
 *       contrapropor (a IA responde depois de alguns dias).</li>
 *   <li>Sempre há a confirmação final ({@link #confirmar}). Fora da janela, o negócio confirmado
 *       fica "aguardando a janela" e é registrado na primeira semana dela (livres: a qualquer momento).</li>
 * </ul>
 * Estados: {@code aguardando} (a vez é do outro lado), {@code recebida} (a vez é sua),
 * {@code contraproposta}, {@code aceita} (taxa acertada; falta o contrato do jogador),
 * {@code aguardando_confirmacao}, {@code aguardando_janela}, {@code concluida}, {@code recusada},
 * {@code expirada}, {@code cancelada}. IA × IA: dentro da janela do comprador, na hora; fora dela
 * (leilões), o negócio espera a janela ({@link #agendarIA}, {@code direcao = "ia"}).
 */
public final class Negociacoes {
    public static final int PRAZO_DIAS = 7;

    private final Mundo m;
    private final Mercado mercado;
    private final Rng rng;

    public Negociacoes(Mundo m, Mercado mercado) {
        this.m = m;
        this.mercado = mercado;
        this.rng = m.rng;
    }

    /** Dia absoluto no começo da semana atual (semana absoluta × 7). */
    public int hoje() {
        return m.semanaAbsoluta() * 7;
    }

    /** Janela aberta para o clube (livres e sem clube: sempre). */
    boolean janela(Clube c) {
        return c == null || Mercado.janelaAberta(c.calendario, m.semana);
    }

    /** Dias até a resposta: 1–7; na última semana da janela, 1–2. */
    int atraso(Clube c) {
        boolean ultima = c != null && Mercado.janelaAberta(c.calendario, m.semana) && !Mercado.janelaAberta(c.calendario, m.semana + 1);
        return ultima ? rng.entre(1, 2) : rng.entre(1, 7);
    }

    private Clube meu() {
        return m.clubeHumano();
    }

    public PropostaJogador proposta(int id) {
        for (PropostaJogador p : m.propostas) if (p.id == id) return p;
        return null;
    }

    private PropostaJogador nova(Jogador j, String tipo, String direcao) {
        PropostaJogador p = new PropostaJogador();
        p.id = m.proximaProposta++;
        p.jogadorId = j.id;
        p.tipo = tipo;
        p.direcao = direcao;
        p.criada = hoje();
        p.semanaLimite = Integer.MAX_VALUE; // o prazo é diaExpira (a expiração antiga é por semana)
        m.propostas.add(p);
        return p;
    }

    private void avisar(PropostaJogador p, String titulo, String texto) {
        Mensagem msg = new Mensagem(m.ano, m.semana, "proposta", titulo, texto);
        msg.propostaId = p.id;
        m.mensagemPara(dono(p), msg);
        p.mensagem = texto;
    }

    /**
     * Multi-treinador: o técnico de quem é a vez nesta negociação (recebida: o do vendedor; enviada:
     * o do comprador). Um técnico só: null (a caixa do Mundo, como sempre).
     */
    private br.futebas.mundo.Tecnico dono(PropostaJogador p) {
        if (!m.multi()) return null;
        return m.tecnicoDoClube("recebida".equals(p.direcao) ? p.vendedorId : p.compradorId);
    }

    // =================================================================== o humano compra / pede emprestado
    /** Proposta de compra (ou assinatura de livre). Devolve a proposta, ou null com o motivo em {@code erro[0]}. */
    public PropostaJogador comprar(int jogadorId, long valor, int meses, String[] erro) {
        return comprar(jogadorId, valor, meses, null, erro);
    }

    /**
     * Proposta de compra com o salário já oferecido ({@code salario} != null): é a única confirmação
     * do técnico (requisito). Quando o clube aceita a taxa, o contrato é proposto ao jogador e,
     * se ele aceitar, o negócio fecha sozinho (fora da janela, fica acertado para a abertura dela).
     */
    public PropostaJogador comprar(int jogadorId, long valor, int meses, Long salario, String[] erro) {
        Clube c = meu();
        Jogador j = m.ativo(jogadorId);
        if (c == null || j == null || j.amador()) return falha(erro, "Jogador indisponível.");
        if (c.id.equals(j.clubeId)) return falha(erro, "O jogador já é do seu clube.");
        if (j.status == StatusJogador.APOSENTADO || j.status == StatusJogador.BASE) return falha(erro, "Este jogador não está no mercado.");
        for (PropostaJogador x : m.propostas) if (x.jogadorId == j.id && "enviada".equals(x.direcao) && x.ativa()) return falha(erro, "Já há uma negociação em andamento por ele.");
        if (comprometido(j.id)) return falha(erro, "Ele já acertou com outro clube e só espera a janela abrir.");
        if (j.kingsTimeId != null && j.clubeId == null) return falha(erro, "Ele joga na Kings League.");
        Clube dono = m.clube(j.clubeId);
        boolean livre = j.status == StatusJogador.LIVRE || dono == null;
        if (!livre && c.dinheiro < valor) return falha(erro, "Dinheiro insuficiente em caixa.");
        PropostaJogador p = nova(j, "compra", "enviada");
        p.compradorId = c.id;
        p.comprador = c.nome;
        p.vendedorId = livre ? null : dono.id;
        p.valor = livre ? 0 : valor;
        p.meses = meses;
        p.salarioOferta = salario != null && salario > 0 ? salario : null;
        if (livre) {
            p.estado = "aceita";
            p.diaExpira = hoje() + 2 * PRAZO_DIAS;
            p.mensagem = "Jogador livre: proponha o contrato.";
        } else if (m.multi() && dono.humano && m.tecnicoDoClube(dono.id) != null) {
            // multi-treinador: o clube é de outro técnico humano, que responde na vez dele
            p.entreHumanos = true;
            p.estado = "aguardando";
            p.diaResposta = -1;
            p.diaExpira = hoje() + 2 * PRAZO_DIAS;
            p.mensagem = "Proposta enviada ao " + dono.nome + " (técnico " + m.tecnicoDoClube(dono.id).nome + ").";
            Mensagem msg = new Mensagem(m.ano, m.semana, "proposta", "Proposta por " + j.nomeExibicao(),
                    "O " + c.nome + " (técnico " + m.tecnico.nome + ") oferece " + Financas.formatar(valor) + " por " + j.nomeExibicao()
                            + ". Responda na aba Transferências.");
            msg.propostaId = p.id;
            m.mensagemPara(m.tecnicoDoClube(dono.id), msg);
        } else {
            p.estado = "aguardando";
            p.diaResposta = rng.chance(0.9) ? hoje() + atraso(dono) : -1;
            p.diaExpira = hoje() + PRAZO_DIAS;
            p.mensagem = "Proposta enviada ao " + dono.nome + ".";
        }
        return p;
    }

    /** Pedido de empréstimo de um jogador de outro clube (taxa {@code taxa}). */
    public PropostaJogador pedirEmprestado(int jogadorId, long taxa, String[] erro) {
        Clube c = meu();
        Jogador j = m.ativo(jogadorId);
        Clube dono = j == null ? null : m.clube(j.clubeId);
        if (c == null || j == null || dono == null || dono.humano || j.contrato == null || j.contrato.tipo != TipoContrato.PROFISSIONAL)
            return falha(erro, "Só dá para pedir emprestado jogador com contrato num clube.");
        if (c.dinheiro < taxa) return falha(erro, "Dinheiro insuficiente para a taxa de empréstimo (" + Financas.formatar(taxa) + ").");
        for (PropostaJogador x : m.propostas) if (x.jogadorId == j.id && "enviada".equals(x.direcao) && x.ativa()) return falha(erro, "Já há uma negociação em andamento por ele.");
        if (comprometido(j.id)) return falha(erro, "Ele já acertou com outro clube e só espera a janela abrir.");
        PropostaJogador p = nova(j, "emprestimo", "enviada");
        p.compradorId = c.id;
        p.comprador = c.nome;
        p.vendedorId = dono.id;
        p.valor = taxa;
        p.estado = "aguardando";
        p.diaResposta = rng.chance(0.9) ? hoje() + atraso(dono) : -1;
        p.diaExpira = hoje() + PRAZO_DIAS;
        p.mensagem = "Pedido de empréstimo enviado ao " + dono.nome + ".";
        return p;
    }

    private static PropostaJogador falha(String[] erro, String msg) {
        if (erro != null && erro.length > 0) erro[0] = msg;
        return null;
    }

    /** Resposta do clube dono (IA) à proposta do humano. */
    private void responderComoIA(PropostaJogador p) {
        Jogador j = m.ativo(p.jogadorId);
        Clube dono = m.clube(p.vendedorId), c = m.clube(p.compradorId);
        if (j == null || dono == null || c == null || !dono.id.equals(j.clubeId)) {
            fechar(p, "recusada", "O negócio não é mais possível.");
            return;
        }
        if ("emprestimo".equals(p.tipo)) {
            String motivo = Mercado.motivoRecusaEmprestimo(m, j, dono, c);
            if (motivo != null) fechar(p, "recusada", motivo);
            else {
                p.estado = "aguardando_confirmacao";
                p.diaExpira = hoje() + PRAZO_DIAS;
                p.mensagem = "O " + dono.nome + " aceita emprestar " + j.nomeExibicao() + ".";
                // o pedido de empréstimo já foi a confirmação do técnico: fecha sozinho
                fecharSeAcertado(p, "Empréstimo aceito");
            }
            return;
        }
        long pedido = mercado.precoPedido(j);
        long multa = j.contrato != null ? j.contrato.multa : 0;
        boolean pagaMulta = multa > 0 && p.valor >= multa;
        if (dono.reputacao > c.reputacao + 30 && !j.aVenda && !pagaMulta) {
            fechar(p, "recusada", j.nomeExibicao() + " não deseja se transferir.");
            return;
        }
        if (!pagaMulta && !dono.pais.equals(c.pais) && dono.reputacao > c.reputacao + 15 && !j.aVenda && rng.chance(0.5)) {
            fechar(p, "recusada", j.nomeExibicao() + " prefere continuar no " + dono.nome + ".");
            return;
        }
        if (p.valor >= pedido || pagaMulta) {
            p.estado = "aceita";
            p.diaExpira = hoje() + PRAZO_DIAS;
            if (p.salarioOferta != null) {
                p.mensagem = "O " + dono.nome + " aceita " + Financas.formatar(p.valor) + " por " + j.nomeExibicao() + ".";
                fecharSeAcertado(p, "Proposta aceita");
            } else avisar(p, "Proposta aceita", "O " + dono.nome + " aceita " + Financas.formatar(p.valor) + " por " + j.nomeExibicao()
                    + ". Agora acerte o contrato com o jogador.");
        } else if (p.valor >= pedido * 0.7) {
            p.estado = "contraproposta";
            p.valorContra = pedido;
            p.diaExpira = hoje() + PRAZO_DIAS;
            avisar(p, "Contraproposta", "O " + dono.nome + " pede " + Financas.formatar(pedido) + " por " + j.nomeExibicao() + ".");
        } else {
            fechar(p, "recusada", "Proposta recusada. O " + dono.nome + " pede " + Financas.formatar(pedido) + ".");
        }
    }

    /** Termos com o jogador (taxa já acertada): aceita, pede mais (contraproposta) ou recusa. */
    public String proporContrato(int propostaId, long salario, int meses) {
        PropostaJogador p = proposta(propostaId);
        if (p == null || !"enviada".equals(p.direcao) || !"compra".equals(p.tipo)) return "Proposta inválida.";
        if (!"aceita".equals(p.estado) && !"contraproposta_jogador".equals(p.estado)) return "Ainda não é hora do contrato.";
        Jogador j = m.ativo(p.jogadorId);
        Clube c = meu();
        if (j == null || c == null) return "Jogador indisponível.";
        long pede = Math.round(Financas.salarioPedido(j, c, m.ano) * (j.moral < 40 ? 1.1 : 1.0));
        p.meses = meses <= 0 ? 12 : meses;
        if (salario >= pede) {
            p.salario = salario;
            p.estado = "aguardando_confirmacao";
            p.diaExpira = hoje() + PRAZO_DIAS;
            p.mensagem = j.nomeExibicao() + " aceita " + Financas.formatar(salario) + " por " + p.meses + " meses. Confirme o negócio.";
        } else if (salario >= pede * 0.8) {
            p.estado = "contraproposta_jogador";
            p.salarioContra = pede;
            p.mensagem = j.nomeExibicao() + " pede " + Financas.formatar(pede) + " de salário.";
        } else if (rng.chance(0.3)) {
            fechar(p, "recusada", j.nomeExibicao() + " recusou a proposta de contrato e encerrou a conversa.");
        } else {
            p.mensagem = j.nomeExibicao() + " recusou o salário. Ele pede " + Financas.formatar(pede) + ".";
            p.salarioContra = pede;
        }
        return p.mensagem;
    }

    // =================================================================== a IA quer um jogador seu
    /** Proposta da IA por um jogador do humano (compra ou empréstimo): a vez é do técnico. */
    public PropostaJogador receber(Jogador j, Clube comprador, long valor, String tipo) {
        for (PropostaJogador x : m.propostas) if (x.jogadorId == j.id && "recebida".equals(x.direcao) && x.ativa()) return null;
        PropostaJogador p = nova(j, tipo, "recebida");
        p.compradorId = comprador.id;
        p.comprador = comprador.nome;
        p.vendedorId = j.clubeId;
        p.valor = valor;
        p.salario = Financas.salarioPedido(j, comprador, m.ano);
        p.estado = "recebida";
        p.diaExpira = hoje() + PRAZO_DIAS;
        String txt = "emprestimo".equals(tipo)
                ? "O " + comprador.nome + " quer " + j.nomeExibicao() + " emprestado até o fim da temporada."
                : "O " + comprador.nome + " oferece " + Financas.formatar(valor) + " por " + j.nomeExibicao() + ".";
        avisar(p, ("emprestimo".equals(tipo) ? "Pedido de empréstimo: " : "Proposta por ") + j.nomeExibicao(), txt);
        return p;
    }

    /** Responder a uma proposta recebida (ou aceitar a contraproposta do outro clube). */
    public boolean responder(int propostaId, boolean aceitar) {
        PropostaJogador p = proposta(propostaId);
        if (p == null) return false;
        if ("recebida".equals(p.direcao) && ("recebida".equals(p.estado) || "contraproposta".equals(p.estado))) {
            if (!aceitar) {
                fechar(p, "recusada", null);
                return true;
            }
            if ("contraproposta".equals(p.estado) && p.valorContra != null) p.valor = p.valorContra;
            p.estado = "aguardando_confirmacao"; // o comprador já acertou com o jogador
            p.diaExpira = hoje() + PRAZO_DIAS;
            return true;
        }
        if ("enviada".equals(p.direcao) && "contraproposta".equals(p.estado)) {
            if (!aceitar) {
                fechar(p, "recusada", null);
                return true;
            }
            p.valor = p.valorContra;
            p.estado = "aceita";
            p.diaExpira = hoje() + PRAZO_DIAS;
            if (p.salarioOferta != null) { // salário já oferecido: aceitar a contraproposta fecha o negócio
                proporContrato(p.id, p.salarioOferta, p.meses);
                if ("aguardando_confirmacao".equals(p.estado)) confirmar(p.id);
            }
            return true;
        }
        if ("enviada".equals(p.direcao) && "contraproposta_jogador".equals(p.estado) && aceitar) {
            proporContrato(p.id, p.salarioContra, p.meses);
            return true;
        }
        return false;
    }

    /** Contraproposta do técnico (novo valor): o outro clube responde em alguns dias. */
    public boolean contrapropor(int propostaId, long valor) {
        PropostaJogador p = proposta(propostaId);
        if (p == null || valor <= 0) return false;
        boolean minhaVez = "recebida".equals(p.direcao) && ("recebida".equals(p.estado) || "contraproposta".equals(p.estado))
                || "enviada".equals(p.direcao) && "contraproposta".equals(p.estado);
        if (!minhaVez) return false;
        p.valor = valor;
        p.valorContra = null;
        p.estado = "aguardando";
        Clube outro = m.clube("recebida".equals(p.direcao) ? p.compradorId : p.vendedorId);
        if (p.entreHumanos) {
            // o outro lado é um técnico humano: responde na vez dele
            p.diaResposta = -1;
            p.diaExpira = hoje() + 2 * PRAZO_DIAS;
            Jogador j = m.ativo(p.jogadorId);
            Mensagem msg = new Mensagem(m.ano, m.semana, "proposta", "Nova proposta", "O " + p.comprador + " sobe a oferta por "
                    + (j == null ? "o jogador" : j.nomeExibicao()) + " para " + Financas.formatar(valor) + ". Responda na aba Transferências.");
            msg.propostaId = p.id;
            m.mensagemPara(outro == null ? null : m.tecnicoDoClube(outro.id), msg);
            return true;
        }
        p.diaResposta = rng.chance(0.9) ? hoje() + atraso(outro) : -1;
        p.diaExpira = hoje() + PRAZO_DIAS;
        return true;
    }

    // =================================================================== entre dois técnicos humanos
    /** A vez é do técnico vendedor (proposta de outro técnico humano esperando resposta)? */
    public static boolean vezDoVendedor(PropostaJogador p) {
        return p.entreHumanos && "aguardando".equals(p.estado);
    }

    /**
     * O técnico vendedor (na vez dele) responde à proposta de outro técnico humano: aceitar segue como
     * se a IA tivesse aceitado (com o salário já oferecido, o negócio fecha sozinho), recusar encerra.
     */
    public boolean responderComoVendedor(int propostaId, boolean aceitar) {
        PropostaJogador p = proposta(propostaId);
        if (p == null || !vezDoVendedor(p)) return false;
        Jogador j = m.ativo(p.jogadorId);
        Clube dono = m.clube(p.vendedorId), c = m.clube(p.compradorId);
        if (j == null || dono == null || c == null || !dono.id.equals(j.clubeId)) {
            fechar(p, "recusada", "O negócio não é mais possível.");
            return true;
        }
        if (!aceitar) {
            fechar(p, "recusada", "O " + dono.nome + " recusou a proposta por " + j.nomeExibicao() + ".");
            return true;
        }
        // o resto do negócio (contrato com o jogador, confirmação) é do comprador: roda com ele na vez
        m.comTecnico(m.tecnicoDoClube(c.id), () -> {
            p.estado = "aceita";
            p.diaExpira = hoje() + PRAZO_DIAS;
            if (p.salarioOferta != null) {
                p.mensagem = "O " + dono.nome + " aceita " + Financas.formatar(p.valor) + " por " + j.nomeExibicao() + ".";
                fecharSeAcertado(p, "Proposta aceita");
            } else avisar(p, "Proposta aceita", "O " + dono.nome + " aceita " + Financas.formatar(p.valor) + " por " + j.nomeExibicao()
                    + ". Agora acerte o contrato com o jogador.");
        });
        return true;
    }

    /** O técnico vendedor pede outro valor: o comprador aceita, recusa ou contrapropõe na vez dele. */
    public boolean contraproporComoVendedor(int propostaId, long valor) {
        PropostaJogador p = proposta(propostaId);
        if (p == null || !vezDoVendedor(p) || valor <= 0) return false;
        Jogador j = m.ativo(p.jogadorId);
        Clube dono = m.clube(p.vendedorId);
        p.estado = "contraproposta";
        p.valorContra = valor;
        p.diaExpira = hoje() + 2 * PRAZO_DIAS;
        avisar(p, "Contraproposta", "O " + (dono == null ? "clube" : dono.nome) + " pede " + Financas.formatar(valor) + " por "
                + (j == null ? "o jogador" : j.nomeExibicao()) + ".");
        return true;
    }

    /** Resposta do comprador (IA) à contraproposta do técnico por um jogador dele. */
    private void responderContraDaIA(PropostaJogador p) {
        Jogador j = m.ativo(p.jogadorId);
        Clube comprador = m.clube(p.compradorId);
        if (j == null || comprador == null || !p.vendedorId.equals(j.clubeId)) {
            fechar(p, "recusada", "O negócio não é mais possível.");
            return;
        }
        long base = Math.max(1, Financas.valorMercado(j, m.clube(j.clubeId), m.ano));
        if (p.valor <= base * 1.35 && comprador.dinheiro >= p.valor) {
            p.estado = "aguardando_confirmacao";
            p.diaExpira = hoje() + PRAZO_DIAS;
            // a contraproposta do técnico já foi a confirmação dele: a venda fecha sozinha
            p.mensagem = "O " + comprador.nome + " aceita pagar " + Financas.formatar(p.valor) + " por " + j.nomeExibicao() + ".";
            fecharSeAcertado(p, "Contraproposta aceita");
        } else if (p.valor <= base * 1.7 && comprador.dinheiro >= base * 1.2) {
            p.estado = "contraproposta";
            p.direcao = "recebida";
            p.valorContra = Math.min(comprador.dinheiro, Financas.arred(base * 1.2));
            p.diaExpira = hoje() + PRAZO_DIAS;
            avisar(p, "Nova proposta", "O " + comprador.nome + " sobe a oferta por " + j.nomeExibicao() + " para " + Financas.formatar(p.valorContra) + ".");
        } else {
            fechar(p, "recusada", "O " + comprador.nome + " desistiu de " + j.nomeExibicao() + ".");
        }
    }

    /**
     * Requisito ("ainda tem que confirmar 3 vezes para vender e comprar"): o que o técnico já
     * confirmou (proposta com salário, pedido de empréstimo, contraproposta) fecha sozinho quando o
     * outro lado aceita. Se ainda faltar algo (o jogador pediu mais salário, faltou caixa), avisa.
     */
    private void fecharSeAcertado(PropostaJogador p, String titulo) {
        String antes = p.mensagem;
        if ("aceita".equals(p.estado) && p.salarioOferta != null) proporContrato(p.id, p.salarioOferta, p.meses);
        if ("aguardando_confirmacao".equals(p.estado)) {
            String r = confirmar(p.id);
            if ("aguardando_confirmacao".equals(p.estado)) p.mensagem = antes + " " + r + " Confirme o negócio na aba Transferências.";
        }
        switch (p.estado) {
            case "aguardando_janela", "recusada", "cancelada", "expirada" -> {} // aguardarJanela / fechar já avisaram
            case "concluida" -> {
                if (!("emprestimo".equals(p.tipo) && "enviada".equals(p.direcao))) // o empréstimo já avisa ("Reforço emprestado")
                    avisar(p, "Negócio fechado", antes + " " + p.mensagem);
            }
            default -> avisar(p, titulo, "contraproposta_jogador".equals(p.estado) || "aceita".equals(p.estado) ? antes + " " + p.mensagem : p.mensagem);
        }
    }

    // =================================================================== confirmação, cancelamento, semana
    /** Confirmação final do técnico: fecha o negócio (fora da janela, fica aguardando a janela). */
    public String confirmar(int propostaId) {
        PropostaJogador p = proposta(propostaId);
        if (p == null || !"aguardando_confirmacao".equals(p.estado)) return "Nada a confirmar.";
        Jogador j = m.ativo(p.jogadorId);
        if (j == null) {
            fechar(p, "cancelada", null);
            return "Jogador indisponível.";
        }
        boolean livre = p.vendedorId == null;
        Clube comprador = m.clube(p.compradorId);
        if (!livre && !janela(comprador)) return aguardarJanela(p, j, comprador);
        return executar(p, j);
    }

    /**
     * Acerto fora da janela do país do comprador (regra do jogo): o jogador continua no clube atual
     * (e jogando por ele) e só se muda na abertura da próxima janela do comprador.
     */
    String aguardarJanela(PropostaJogador p, Jogador j, Clube comprador) {
        Clube vend = m.clube(p.vendedorId);
        if (p.valor > 0 && vend != null) {
            if (comprador.humano && comprador.dinheiro < p.valor) return "Dinheiro insuficiente em caixa.";
            // regra do jogo: o dinheiro entra (e sai) na hora do acerto; só o jogador espera a janela
            comprador.dinheiro -= p.valor;
            comprador.financas.compras += p.valor;
            vend.dinheiro += p.valor;
            vend.financas.vendas += p.valor;
            p.pago = true;
        }
        p.estado = "aguardando_janela";
        p.semanaChegada = Mercado.proximaAbertura(comprador.calendario, m.semanaAbsoluta());
        p.diaChegada = Calendario.inicio(p.semanaChegada).toEpochDay();
        String quando = java.time.LocalDate.ofEpochDay(p.diaChegada).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
        p.mensagem = "Acerto concluído: " + j.nomeExibicao() + " chega na abertura da janela (" + quando + ").";
        Clube vendedor = m.clube(p.vendedorId);
        if (comprador.humano || (vendedor != null && vendedor.humano)) {
            Mensagem msg = new Mensagem(m.ano, m.semana, "mercado", "Acerto concluído", comprador.humano ? p.mensagem
                    : "Acerto concluído: " + j.nomeExibicao() + " vai para o " + comprador.nome + " na abertura da janela (" + quando + ").");
            msg.propostaId = p.id;
            m.mensagemPara(m.multi() ? m.tecnicoDoClube(comprador.humano ? comprador.id : vendedor.id) : null, msg);
            if (m.multi() && comprador.humano && vendedor != null && vendedor.humano) {
                Mensagem v = new Mensagem(m.ano, m.semana, "mercado", "Acerto concluído", "Acerto concluído: " + j.nomeExibicao()
                        + " vai para o " + comprador.nome + " na abertura da janela (" + quando + ").");
                v.propostaId = p.id;
                m.mensagemPara(m.tecnicoDoClube(vendedor.id), v);
            }
        }
        return p.mensagem;
    }

    /**
     * IA × IA fora da janela do comprador (ex.: leilão): o negócio fica marcado e o jogador se muda
     * na abertura da janela (mesma regra do humano).
     */
    public PropostaJogador agendarIA(Jogador j, Clube vendedor, Clube comprador, long valor, String tipo) {
        PropostaJogador p = new PropostaJogador();
        p.id = m.proximaProposta++;
        p.jogadorId = j.id;
        p.compradorId = comprador.id;
        p.comprador = comprador.nome;
        p.vendedorId = vendedor == null ? null : vendedor.id;
        p.valor = valor;
        p.tipo = tipo;
        p.direcao = "ia";
        p.criada = hoje();
        m.propostas.add(p);
        aguardarJanela(p, j, comprador);
        return p;
    }

    /** O jogador tem um negócio fechado esperando a janela (não pode ser negociado com outro clube). */
    public boolean comprometido(int jogadorId) {
        for (PropostaJogador p : m.propostas) if (p.jogadorId == jogadorId && "aguardando_janela".equals(p.estado)) return true;
        return false;
    }

    /** Ids dos jogadores com negócio fechado esperando a janela. */
    public java.util.Set<Integer> comprometidos() {
        java.util.Set<Integer> s = new java.util.HashSet<>();
        for (PropostaJogador p : m.propostas) if ("aguardando_janela".equals(p.estado)) s.add(p.jogadorId);
        return s;
    }

    private String executar(PropostaJogador p, Jogador j) {
        Clube comprador = m.clube(p.compradorId), vendedor = m.clube(p.vendedorId);
        if (comprador == null || (p.vendedorId != null && (vendedor == null || !vendedor.id.equals(j.clubeId)))) {
            estornar(p);
            fechar(p, "cancelada", null);
            return "O negócio não é mais possível.";
        }
        if ("emprestimo".equals(p.tipo)) {
            if (!p.pago) {
                if (comprador.dinheiro < p.valor && comprador.humano) return "Dinheiro insuficiente para a taxa.";
                comprador.dinheiro -= p.valor;
                comprador.financas.compras += p.valor;
                vendedor.dinheiro += p.valor;
                vendedor.financas.vendas += p.valor;
            }
            mercado.emprestar(j, vendedor, comprador);
            j.aEmprestimo = false;
            m.historia.estat(m.ano).transferencias++;
            fechar(p, "concluida", j.nomeExibicao() + " emprestado ao " + comprador.nome + ".");
            if (comprador.humano) m.mensagemDoClube(comprador.id, "mercado", "Reforço emprestado", j.nomeExibicao() + " chega emprestado pelo " + vendedor.nome + " até o fim da temporada.");
            return p.mensagem;
        }
        if (!p.pago && comprador.humano && comprador.dinheiro < p.valor) return "Dinheiro insuficiente em caixa.";
        mercado.transferir(j, comprador, p.valor, p.pago);
        j.aEmprestimo = false;
        if (comprador.humano && p.salario > 0) {
            j.contrato.salario = p.salario;
            j.contrato.fim = mercado.fimContrato(p.meses <= 0 ? 12 : p.meses);
        }
        fechar(p, "concluida", j.nomeExibicao() + " é do " + comprador.nome + ".");
        return p.mensagem;
    }

    /** Negócio pago no acerto que não se concretizou: o dinheiro volta. */
    private void estornar(PropostaJogador p) {
        if (!p.pago) return;
        p.pago = false;
        Clube comprador = m.clube(p.compradorId), vendedor = m.clube(p.vendedorId);
        if (comprador != null) {
            comprador.dinheiro += p.valor;
            comprador.financas.compras -= p.valor;
        }
        if (vendedor != null) {
            vendedor.dinheiro -= p.valor;
            vendedor.financas.vendas -= p.valor;
        }
    }

    public boolean cancelar(int propostaId) {
        PropostaJogador p = proposta(propostaId);
        if (p == null || !p.ativa()) return false;
        estornar(p);
        fechar(p, "cancelada", null);
        return true;
    }

    private void fechar(PropostaJogador p, String estado, String texto) {
        p.estado = estado;
        if (texto != null) {
            Mensagem msg = new Mensagem(m.ano, m.semana, "proposta", "Negociação: " + estado, texto);
            msg.propostaId = p.id;
            if (!"concluida".equals(estado)) m.mensagemPara(dono(p), msg);
            p.mensagem = texto;
        }
    }

    /** Toda semana: respostas que chegaram, prazos vencidos e negócios que esperavam a janela. */
    public void semana() {
        int fimSemana = hoje() + 7;
        for (PropostaJogador p : new ArrayList<>(m.propostas)) {
            if (p.direcao == null) continue; // propostas antigas (saves de antes)
            if ("aguardando".equals(p.estado) && p.diaResposta >= 0 && p.diaResposta < fimSemana) {
                if ("enviada".equals(p.direcao)) responderComoIA(p);
                else responderContraDaIA(p);
                continue;
            }
            if ("aguardando_janela".equals(p.estado)) {
                Jogador j = m.ativo(p.jogadorId);
                if (j == null) {
                    estornar(p);
                    fechar(p, "cancelada", null);
                }
                else if (janela(m.clube(p.compradorId))) executar(p, j);
                continue;
            }
            if (p.ativa() && p.diaExpira > 0 && p.diaExpira < fimSemana && !"aguardando_janela".equals(p.estado))
                fechar(p, "expirada", "A proposta por " + nome(p) + " expirou sem resposta.");
        }
        // memória: negociações encerradas há mais de 8 semanas saem da lista
        m.propostas.removeIf(p -> p.direcao != null && !p.ativa() && p.criada < hoje() - 56);
    }

    private String nome(PropostaJogador p) {
        Jogador j = m.jogador(p.jogadorId);
        return j == null ? "o jogador" : j.nomeExibicao();
    }

    /** IA: pedidos de empréstimo pelos jogadores da lista de empréstimos do humano (semanas de janela). */
    public void ofertasDeEmprestimo() {
        Clube c = meu();
        if (c == null || !janela(c)) return;
        for (int id : new ArrayList<>(c.elenco)) {
            Jogador j = m.ativo(id);
            if (j == null || !j.aEmprestimo || !rng.chance(0.35)) continue;
            Clube destino = mercado.destinoEmprestimo(j, c);
            if (destino != null) receber(j, destino, 0, "emprestimo");
        }
    }

    /** Data (calendário) de um dia absoluto de negociação. */
    public java.time.LocalDate data(int dia) {
        return Calendario.inicio(Math.floorDiv(dia, 7)).plusDays(Math.floorMod(dia, 7));
    }
}
