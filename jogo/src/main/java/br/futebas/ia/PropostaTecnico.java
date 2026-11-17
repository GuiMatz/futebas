package br.futebas.ia;

/** Convite de um clube (ou de uma seleção) para o técnico humano. */
public class PropostaTecnico {
    public int id;
    public String clubeId;
    public String clube;
    /** Convite de seleção (país ISO-3): o técnico comanda a seleção junto com o clube. */
    public String selecaoPais;
    /** Convite de um time da Kings League (aceitar = deixar o clube: ou clube, ou Kings). */
    public String kingsTimeId, kingsLigaId;
    public int ano, semana;
    /** Semana absoluta limite para responder. */
    public int semanaLimite;
    /** pendente | aceita | recusada | expirada | candidatura_recusada (candidatura do técnico a um clube) */
    public String estado = "pendente";

    public PropostaTecnico() {}
}
