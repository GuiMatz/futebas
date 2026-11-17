package br.futebas.ia;

/** Uma proposta por um jogador (recebida pelo clube do humano ou feita por ele). */
public class PropostaJogador {
    public int id;
    public int jogadorId;
    /** Clube que quer o jogador (id), ou null quando é do exterior. */
    public String compradorId;
    /** Nome do comprador (clube do mundo). */
    public String comprador;
    /** Clube que vende (id), ou null para jogador livre/do exterior. */
    public String vendedorId;
    public long valor;
    /** Acerto fora da janela: o dinheiro já trocou de mãos na hora (regra do jogo); o jogador muda depois. */
    public boolean pago;
    public long salario;
    /** compra | emprestimo | teste */
    public String tipo = "compra";
    /**
     * Negociações do humano ({@link Negociacoes}): aguardando | recebida | contraproposta |
     * contraproposta_jogador | aceita | aguardando_confirmacao | aguardando_janela | concluida |
     * recusada | expirada | cancelada. Saves antigos: pendente | aceita | recusada | expirada.
     */
    public String estado = "pendente";
    /** enviada (o humano quer o jogador) | recebida (a IA quer um jogador do humano); null = antiga. */
    public String direcao;
    /** Dias absolutos (semana absoluta × 7 + dia): criação, resposta do outro lado (−1 = não responde) e prazo. */
    public int criada, diaResposta, diaExpira;
    /** Contraproposta do outro clube (valor) e do jogador (salário). */
    public Long valorContra, salarioContra;
    /**
     * Salário que o técnico já ofereceu junto com a proposta (requisito: uma confirmação só).
     * Com ele, quando o clube aceita a taxa, o contrato é proposto e o negócio fecha sozinho.
     * null = proposta antiga (o contrato é acertado depois, na aba Transferências).
     */
    public Long salarioOferta;
    /** Duração do contrato proposto (meses). */
    public int meses;
    /** Acerto fora da janela: semana absoluta e dia (epochDay) em que o jogador chega (0 = não marcado). */
    public int semanaChegada;
    public long diaChegada;
    /** Última novidade da negociação (texto para a tela). */
    public String mensagem;
    /**
     * Modo multi-treinador: proposta de um técnico humano por um jogador do clube de OUTRO técnico
     * humano. Fica "enviada" (do lado do comprador) e quem responde é o técnico vendedor, na vez dele.
     */
    public boolean entreHumanos;

    /** Negociação ainda em andamento. */
    public boolean ativa() {
        return switch (estado == null ? "" : estado) {
            case "pendente", "aguardando", "recebida", "contraproposta", "contraproposta_jogador", "aceita", "aguardando_confirmacao", "aguardando_janela" -> true;
            default -> false;
        };
    }

    /** A vez é do técnico (responder, propor contrato ou confirmar)? */
    public boolean minhaVez() {
        return switch (estado == null ? "" : estado) {
            case "pendente", "recebida", "contraproposta", "contraproposta_jogador", "aceita", "aguardando_confirmacao" -> true;
            default -> false;
        };
    }

    /** Data em que o jogador chega (acerto fora da janela); null se não há. */
    public java.time.LocalDate chegada() {
        return diaChegada > 0 ? java.time.LocalDate.ofEpochDay(diaChegada) : null;
    }

    /** Situação para a tela. */
    public String situacao() {
        return switch (estado == null ? "" : estado) {
            case "aguardando" -> "Aguardando resposta";
            case "pendente", "recebida" -> "Aguardando a sua resposta";
            case "contraproposta" -> "Contraproposta";
            case "contraproposta_jogador" -> "O jogador pediu mais";
            case "aceita" -> "Aceita: acerte o contrato";
            case "aguardando_confirmacao" -> "Aceita: aguardando confirmação";
            case "aguardando_janela" -> diaChegada > 0 ? "Fechado: chega em " + chegada().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "Fechado: aguardando a janela";
            case "concluida" -> "Concluída";
            case "recusada" -> "Recusada";
            case "expirada" -> "Expirada";
            case "cancelada" -> "Cancelada";
            default -> estado;
        };
    }
    public boolean exterior;
    public int semanaLimite;

    public PropostaJogador() {}
}
