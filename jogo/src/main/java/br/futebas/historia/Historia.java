package br.futebas.historia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Histórico permanente do save (nada se apaga), de 2026 em diante: campeões e vices de cada
 * competição em cada temporada, artilheiros, prêmios, recordes, transferências marcantes, linha
 * do tempo e estatísticas anuais.
 * As carreiras simuladas ficam em cada jogador ({@code Jogador.carreira}).
 */
public class Historia {
    public List<Titulo> titulos = new ArrayList<>();
    public List<Premio> premios = new ArrayList<>();
    public Map<String, Recorde> recordes = new LinkedHashMap<>();
    public List<Evento> linhaDoTempo = new ArrayList<>();
    public Map<Integer, EstatAno> estatisticas = new TreeMap<>();
    public List<TemporadaTecnico> carreiraTecnico = new ArrayList<>();
    /** Rankings anuais: Bola de Ouro (Top 50) e Bola de Ouro jovem (sub-21, Top 50). */
    public List<Ranking> rankings = new ArrayList<>();
    /** Seleção (time ideal) de cada liga ao terminar. */
    public List<SelecaoDaLiga> selecoesDaLiga = new ArrayList<>();
    /** Lendas criadas no jogo (aposentados com carreira marcante desde 2026). */
    public List<Lenda> lendas = new ArrayList<>();
    /** Jogos de despedida. */
    public List<Despedida> despedidas = new ArrayList<>();

    /** Notícias do jogo (manchetes com foco no clube e nas competições do técnico), mais novas no fim. */
    public List<Noticia> noticias = new ArrayList<>();
    /** Notícias da semana ainda não publicadas (o {@link Noticiario} escolhe as melhores no fim da semana). */
    public List<Noticia> noticiasPendentes = new ArrayList<>();
    /** Memória do noticiário (sequências, marcos já noticiados): chave → valor. */
    public Map<String, Integer> pautas = new TreeMap<>();

    public Historia() {}

    /** Uma notícia: manchete + linha fina curta. */
    public static class Noticia {
        public int ano, semana;
        /** meu_time | tabela | tecnicos | zebra | titulo | divisoes | mercado | aposentadoria | recorde | kings | mundo */
        public String tipo;
        public String titulo;
        /** Linha fina (curta; pode ser null). */
        public String texto;
        /** 0 = seu clube, 1 = suas competições/sua liga, 2 = seu país, 3 = mundo. */
        public int foco;
        /** Importância dentro do foco (0–100). */
        public int peso;
        public String clubeId, competicaoId;
        /** Para não repetir a mesma notícia. */
        public String chave;

        public Noticia() {}

        /** Etiqueta curta para a tela. */
        public String etiqueta() {
            return switch (tipo == null ? "" : tipo) {
                case "meu_time" -> "Seu time";
                case "tabela" -> "Tabela";
                case "tecnicos" -> "Técnicos";
                case "zebra" -> "Zebra";
                case "titulo" -> "Campeões";
                case "divisoes" -> "Acesso e queda";
                case "mercado" -> "Mercado";
                case "aposentadoria" -> "Aposentadoria";
                case "recorde" -> "Recorde";
                case "kings" -> "Kings League";
                default -> "Mundo";
            };
        }

        /** Cor da etiqueta (classes da UI: verde, vermelha, ambar, azul, escura). */
        public String cor() {
            return switch (tipo == null ? "" : tipo) {
                case "meu_time" -> "verde";
                case "tecnicos", "zebra" -> "vermelha";
                case "mercado" -> "ambar";
                case "titulo", "recorde" -> "escura";
                default -> "azul";
            };
        }
    }

    public static class Titulo {
        public int ano;
        /** '2026' ou '2025/26'. */
        public String temporada;
        public String competicaoId, competicao, ambito, pais;
        public String campeaoId, campeao, viceId, vice;
        public Integer artilheiroId;
        public String artilheiro;
        public int golsArtilheiro;
        public int participantes;

        public Titulo() {}
    }

    public static class Premio {
        public int ano;
        /** bola_de_ouro | artilheiro_nacional | revelacao | bola_de_ouro_kings */
        public String tipo;
        public String titulo;
        public Integer jogadorId;
        public String jogador;
        public String clube;
        public double valor;

        public Premio() {}
    }

    public static class Recorde {
        public String nome;
        public String descricao;
        public double valor;
        public int ano;

        public Recorde() {}
    }

    public static class Evento {
        public int ano, semana;
        /** titulo | rebaixamento | acesso | falencia | kings | tecnico | estrela | mundo */
        public String tipo;
        public String texto;

        public Evento() {}

        public Evento(int ano, int semana, String tipo, String texto) {
            this.ano = ano;
            this.semana = semana;
            this.tipo = tipo;
            this.texto = texto;
        }
    }

    /** Números do ano para a ferramenta LongRun e para a tela "o mundo". */
    public static class EstatAno {
        public int ano;
        /** Jogos das ligas nacionais (todas, fora mata-mata). */
        public int jogosLiga, golsLiga, vitMandante, empates, vitVisitante;
        public int jogosTotal, golsTotal;
        public int jogosSelecoes, golsSelecoes;
        public int complementaresUsados, complementaresCriados;
        public int newgens, entradasBase, aposentadorias;
        public int falencias, demissoesTecnicos, transferencias, transferenciasInternacionais, leiloes;
        /** Gasto total em transferências (R$). */
        public long volumeTransferencias;
        public double elencoMedio;
        public int jogadoresAtivos, kingsLigasAtivas;
        public int avisosFormato;

        public EstatAno() {}

        public EstatAno(int ano) {
            this.ano = ano;
        }
    }

    /** Um colocado num ranking (posição 1 = vencedor). */
    public static class ItemRanking {
        public int posicao;
        public Integer jogadorId;
        public String jogador, clubeId, clube, pais;
        public int idade, jogos, gols, assistencias;
        public double nota, valor;

        public ItemRanking() {}
    }

    public static class Ranking {
        public int ano;
        /** "bola_de_ouro" ou "bola_de_ouro_jovem". */
        public String tipo;
        public String titulo;
        public List<ItemRanking> itens = new ArrayList<>();

        public Ranking() {}
    }

    /** Time ideal de uma liga (4-3-3), escolhido pela nota média da temporada. */
    public static class SelecaoDaLiga {
        public int ano;
        public String temporada, competicaoId, competicao, pais;
        public Integer divisao;
        /** Os 11 (GOL, LD, ZAG, ZAG, LE, VOL, MC, MEI, PD, CA, PE). */
        public List<ItemSelecao> time = new ArrayList<>();

        public SelecaoDaLiga() {}
    }

    public static class ItemSelecao {
        /** Sigla da vaga (Posicao.name()). */
        public String posicao;
        public Integer jogadorId;
        public String jogador, clubeId, clube;
        public int jogos, gols, assistencias;
        public double nota;

        public ItemSelecao() {}
    }

    /** Lenda criada no jogo. */
    public static class Lenda {
        public int ano;
        public Integer jogadorId;
        public String nome, pais, posicao;
        public int forcaAuge, jogos, gols, titulos;
        public double fama;
        /** Clubes da carreira (ids), o principal primeiro. */
        public List<String> clubes = new ArrayList<>();
        public String resumo;

        public Lenda() {}
    }

    /** Jogo de despedida de um jogador. */
    public static class Despedida {
        public int ano, semana;
        public Integer jogadorId;
        public String jogador, clubeId, clube, adversarioId, adversario, placar;

        public Despedida() {}
    }

    public static class TemporadaTecnico {
        public int ano;
        public String clubeId, clube;
        public int jogos, vitorias, empates, derrotas;
        public List<String> titulos = new ArrayList<>();
        public String saida;
        /** Linha da Kings League ({@code clubeId} = id do time da Kings; sem empates; shootouts à parte). */
        public boolean kings;
        public int vitoriasShootout, derrotasShootout;

        public TemporadaTecnico() {}
    }

    public EstatAno estat(int ano) {
        return estatisticas.computeIfAbsent(ano, EstatAno::new);
    }

    public void evento(int ano, int semana, String tipo, String texto) {
        linhaDoTempo.add(new Evento(ano, semana, tipo, texto));
    }

    public void recorde(String chave, String nome, String descricao, double valor, int ano) {
        Recorde r = recordes.get(chave);
        if (r == null || valor > r.valor) {
            r = new Recorde();
            r.nome = nome;
            r.descricao = descricao;
            r.valor = valor;
            r.ano = ano;
            recordes.put(chave, r);
        }
    }

    public Ranking ranking(String tipo, int ano) {
        for (Ranking r : rankings) if (r.ano == ano && r.tipo.equals(tipo)) return r;
        return null;
    }

    public SelecaoDaLiga selecaoDaLiga(String competicaoId, int ano) {
        SelecaoDaLiga achada = null;
        for (SelecaoDaLiga s : selecoesDaLiga) if (s.competicaoId.equals(competicaoId) && s.ano == ano) achada = s;
        return achada;
    }

    public List<Titulo> titulosDe(String competicaoId) {
        List<Titulo> l = new ArrayList<>();
        for (Titulo t : titulos) if (t.competicaoId.equals(competicaoId)) l.add(t);
        return l;
    }

    public List<Titulo> titulosDoClube(String clubeId) {
        List<Titulo> l = new ArrayList<>();
        for (Titulo t : titulos) if (clubeId.equals(t.campeaoId)) l.add(t);
        return l;
    }
}
