package br.futebas.kings;

import br.futebas.competicao.Torneio;
import br.futebas.mundo.Posicao;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma Kings League (uma por país), ativada na data real de início (tabela {@code kings_ligas}).
 * Tem formato, regras e calendário próprios, que nunca bloqueiam o futebol normal.
 */
public class LigaKings {
    public String id, nome, pais;
    public int anoInicio, semanaInicio = 1;
    public RegrasKings regras = new RegrasKings();
    public DraftKings draft = new DraftKings();
    public List<TimeKings> times = new ArrayList<>();
    public boolean ativa;
    public int temporadas;
    /** Ano da temporada em andamento (ou da última). */
    public int anoTemporada;
    /** Competição atual (ou a última) da liga: a que está rolando entre Split 1, Kings Cup e Split 2. */
    public Torneio torneio;
    /** Competições da temporada: {@code <id>-split1}, {@code <id>-cup}, {@code <id>-split2}. */
    public List<Torneio> competicoes = new ArrayList<>();
    public List<String> campeoes = new ArrayList<>();
    public String fontes;
    /** Liga customizada (Opções → Times customizados), lida de dados/custom; sem país. */
    public boolean custom;
    /** Nome da Kings Cup desta liga (null = "Kings Cup"). */
    public String nomeCopa;
    /** Liga sem país: de onde vem o pool do draft (IOS: Brasil). */
    public String paisDraft;
    /**
     * Escala de força dos atletas ({@link Kings#VERSAO_FORCA}). 0 = save de antes da escala única
     * (12/11/2026): ao carregar, os atletas são reescalados uma vez. Sem inicializador de propósito:
     * o Gson usa o construtor, e um save antigo (sem o campo) precisa ler 0.
     * <p>A regra do jogador 13 acabou (12/11/2026): os campos {@code convidados13}, {@code candidatos13},
     * {@code usos13Liga}, {@code usos13Playoff}, {@code proximo13} e {@code jogador13Liga/Playoff}
     * saíram; em saves antigos o Gson ignora essas chaves.
     */
    public int versaoForca;
    /** Draft em andamento (esperando a escolha do técnico humano); null quando não há. */
    public EstadoDraft draftAtual;
    /** Drafts concluídos (escolhas em ordem, sem a lista de disponíveis), do mais antigo ao mais novo. */
    public List<EstadoDraft> draftsAnteriores = new ArrayList<>();

    /**
     * Propostas de wildcard em andamento (regra do jogo, 12/11/2026: wildcard é contratação por
     * proposta). Jogador com contrato: primeiro o clube responde, depois o jogador.
     */
    public List<PropostaKings> propostas = new ArrayList<>();

    /** Proposta de um time da Kings a um jogador (wildcard). */
    public static class PropostaKings {
        public String timeId;
        public int jogadorId;
        /** Semana absoluta em que a próxima resposta sai. */
        public int respostaSa;
        /** "clube" (esperando o clube do jogador), "jogador" (esperando o jogador), "aceita", "recusada". */
        public String etapa;
        /** Taxa paga ao clube do jogador (sai do caixa do time quando o jogador aceita). */
        public long taxa;
        /** Salário da temporada oferecido ao jogador (sai do caixa quando ele aceita). */
        public long salario;

        /** Custo total da proposta (reservado no caixa enquanto está em andamento). */
        public long custo() { return taxa + salario; }

        public PropostaKings() {}
    }

    /** Uma escolha do draft. */
    public static class Escolha {
        public int numero, rodada;
        public String timeId, time;
        public Integer jogadorId;
        public String jogador;
        public boolean humano;

        public Escolha() {}
    }

    /**
     * Estado do draft da temporada: a ordem completa das escolhas (um id de time por escolha),
     * a escolha da vez ({@code atual}), quem ainda está disponível e o que já foi escolhido.
     */
    public static class EstadoDraft {
        public int ano, semanaCriado;
        public List<String> ordem = new ArrayList<>();
        public int atual;
        public List<Integer> disponiveis = new ArrayList<>();
        public List<Escolha> escolhas = new ArrayList<>();

        public EstadoDraft() {}

        public boolean terminado() { return atual >= ordem.size(); }

        /** Time da vez (null se acabou). */
        public String vez() { return terminado() ? null : ordem.get(atual); }
    }

    public LigaKings() {}

    public TimeKings time(String id) {
        for (TimeKings t : times) if (t.id.equals(id)) return t;
        return null;
    }

    /** Regras próprias (JSON {@code kings_ligas.regras}); campos ausentes ficam no padrão. */
    public static class RegrasKings {
        public int jogadores_em_campo = 7;
        public int duracao_min = 40;
        public int pontos_vitoria = 3;
        public int pontos_vitoria_penaltis = 2;
        public int pontos_derrota_penaltis = 1;
        /** dado | carta_secreta | penalti_presidente | gol_duplo | shootout */
        public List<String> especiais = new ArrayList<>(List.of("dado", "carta_secreta", "penalti_presidente", "gol_duplo", "shootout"));
        public int classificados_playoff = 8;
        public int turnos = 1;
        /** Semanas que a temporada dura. */
        public int semanas = 14;
        /** Pontuação 2026 (kings_ligas.regras 'pontuacao'): vitória = 1 ponto em qualquer caso, derrota = 0. */
        public boolean pontuacao2026;

        public RegrasKings() {}
    }

    /** Regras do draft (JSON {@code kings_ligas.draft}). */
    public static class DraftKings {
        public int jogadores_por_time = 12;
        public int rodadas = 10;
        /** serpente | linear */
        public String ordem = "serpente";
        /** Quantos jogadores cada time mantém da temporada anterior antes do draft. */
        public int mantidos = 0;
        /** Vagas escolhidas fora do draft (ex.: "11º jogador" e coringas). */
        public int onze = 1;
        public int coringas = 1;
        public int idade_minima = 30;
        public int aposentado_max_anos = 5;

        public DraftKings() {}
    }

    /** Um time da Kings League. */
    public static class TimeKings {
        public String id, nome, presidente, escudoPng, cor1, cor2;
        public List<AtletaKings> elenco = new ArrayList<>();
        public boolean humano;
        /** Prioridades do humano para o draft (ids de jogadores do mundo). */
        public List<Integer> prioridadesDraft = new ArrayList<>();
        /** Vaga de técnico aberta até esta semana absoluta (0 = sem vaga): o presidente decide. */
        public int vagaAte;
        /**
         * Presidentes que também jogam (modelo de cada um): só o Kelvin Oliveira (G3X) e os presidentes
         * da liga customizada IOS. Ficam no elenco para sempre, ocupam uma vaga de wildcard (contam
         * para os 12), jogam o 7x7 e os shootouts e nunca são dispensados, draftados ou trocados.
         * Os outros presidentes não são atletas: só batem o pênalti do presidente.
         */
        public List<AtletaKings> presidentesJogadores = new ArrayList<>();
        /**
         * Escalação do técnico humano (vazia = automática, os 7 mais fortes): chaves de atleta
         * ({@link AtletaKings#chave()}) na ordem das vagas da formação (a 1ª é o goleiro).
         */
        public List<String> titulares = new ArrayList<>();
        /** Formação 7x7 do humano: goleiro + linhas ("2-3-1", "3-2-1", "2-2-2", "3-1-2", "1-3-2", "4-2"). */
        public String formacao = "2-3-1";
        /** Capitão e ordem dos cobradores de shootout (chaves de atleta); vazio = automático. */
        public String capitao;
        public List<String> cobradores = new ArrayList<>();
        /** Presidente que bate o pênalti do presidente (um dos {@link #nomesPresidentes()}); null = revezam. */
        public String batedorPresidente;

        /**
         * Dinheiro do time (regra do jogo, 12/11/2026: "Kings com dinheiro de verdade"): todo time recebe
         * o mesmo orçamento por temporada ({@link Kings#ORCAMENTO_TEMPORADA}), ganha os prêmios
         * (liga, Kings Cup, Copa do Mundo) e gasta nas propostas de wildcard (taxa ao clube + salário
         * da temporada). Nunca fica negativo. Saves antigos (0 e {@code anoOrcamento} 0) recebem o
         * orçamento ao carregar.
         */
        public long caixa;
        /** Ano do último orçamento recebido (0 = nunca). */
        public int anoOrcamento;
        /** Extrato (mais antigo primeiro; no máximo {@link Kings#EXTRATO_MAX}). */
        public List<MovimentoKings> extrato = new ArrayList<>();

        public TimeKings() {}

        /** Soma dos movimentos do tipo no ano ("orcamento", "premio", "contratacao", "treino"); negativos são gastos. */
        public long total(int ano, String tipo) {
            long s = 0;
            if (extrato != null) for (MovimentoKings x : extrato) if (x.ano == ano && (tipo == null || tipo.equals(x.tipo))) s += x.valor;
            return s;
        }

        /** Os presidentes (pessoas) do time: "Gaules / Kelvin Oliveira" → [Gaules, Kelvin Oliveira]. */
        public List<String> nomesPresidentes() {
            List<String> l = new ArrayList<>();
            if (presidente != null) for (String p : presidente.split("\\s*/\\s*|\\s*&\\s*")) if (!p.isBlank()) l.add(p.trim());
            return l;
        }
    }

    /** Uma linha do extrato do time da Kings. */
    public static class MovimentoKings {
        public int ano, semana;
        /** "orcamento", "premio", "contratacao", "treino". */
        public String tipo;
        public String descricao;
        /** Positivo = entrada; negativo = gasto. */
        public long valor;

        public MovimentoKings() {}
    }

    /**
     * Um atleta da Kings League. Ou é um jogador do mundo ({@code jogadorId}), ou um nome lido
     * da fonte em {@code kings_elencos} (nunca inventado).
     */
    public static class AtletaKings {
        public Integer jogadorId;
        public String nome;
        public String fonte;

        /** Identidade estável no elenco (jogador do mundo ou nome da fonte/presidente-jogador). */
        public String chave() { return jogadorId != null ? "j" + jogadorId : "n" + nome; }
        public double forca;
        public Posicao posicao = Posicao.MC;
        /** draft | wildcard | 11o | coringa | mantido | real | livre | selecao | presidente */
        public String tipo;
        public int gols;
        /** Presidente que também joga (nunca sai do time; ocupa vaga de wildcard). */
        public boolean presidente;
        /** Atleta fora do mundo (presidente-jogador): id fixo (foto), ano de nascimento e país. */
        public String idFixo, foto, pais;
        public Integer anoNasc;
        /** Clube de onde veio ao entrar na Kings (draft, wildcard...) e a divisão dele naquela hora; null = sem clube. */
        public String clubeOrigem;
        public Integer divisaoOrigem;
        /**
         * Foco do treino (o mesmo do futebol): "principais", "secundarias" ou null (o auxiliar decide).
         * Os times da IA treinam sozinhos (auxiliar). Ver {@link Kings#treinar}.
         */
        public String treino;
        /** Salário da temporada pago ao contratar (wildcard por proposta); 0 = draft ou elenco real. */
        public long salario;

        public AtletaKings() {}

        public AtletaKings copia() {
            AtletaKings a = new AtletaKings();
            a.jogadorId = jogadorId;
            a.nome = nome;
            a.fonte = fonte;
            a.forca = forca;
            a.posicao = posicao;
            a.tipo = tipo;
            a.presidente = presidente;
            a.idFixo = idFixo;
            a.foto = foto;
            a.pais = pais;
            a.anoNasc = anoNasc;
            a.treino = treino;
            return a;
        }
    }
}
