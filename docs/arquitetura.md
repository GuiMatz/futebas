# Arquitetura e contrato de dados

Este é o contrato entre as três partes do projeto: a coleta (Python), a base do mundo (SQLite) e o jogo (Java).

```
coleta/ (Python)  ──►  dados/mundo.sqlite  ──►  jogo/ (Java 21: motor + JavaFX)
pesquisa manual   ──►  dados/pesquisa/*.json ──► (importado para o mundo.sqlite pelo coleta/montar_banco.py)
```

- Ferramentas locais: `.ferramentas/jdk` (Temurin 21) e `.ferramentas/maven` (3.9.9). Use `tools/mvn.sh` (ou `tools\mvn.cmd`), que já aponta o `JAVA_HOME`.
- Python: ambiente virtual em `coleta/.venv`.
- **Determinismo:** o motor recebe uma semente (`long`). A mesma semente + mesmo banco = mesmo mundo.

## 1. Banco mestre `dados/mundo.sqlite`

Gerado do zero por `python coleta/montar_banco.py` (idempotente). O Java só **lê** este banco. O jogo salvo fica em outro arquivo (seção 3).

```sql
CREATE TABLE fontes (
  id INTEGER PRIMARY KEY,
  url TEXT NOT NULL UNIQUE,
  tipo TEXT NOT NULL,          -- clube | blog_historico | enciclopedia | base_dados | federacao | noticia | oficial
  titulo TEXT,
  acessado_em TEXT,            -- AAAA-MM-DD
  confiabilidade INTEGER       -- 1 (baixa) a 5 (alta)
);

CREATE TABLE clubes (
  id TEXT PRIMARY KEY,         -- slug estável, ex.: 'flamengo-rj', 'river-plate-arg'
  nome TEXT NOT NULL,          -- nome popular: 'Flamengo'
  nome_completo TEXT,
  cidade TEXT, uf TEXT,        -- uf só para o Brasil
  pais TEXT NOT NULL,          -- ISO-3: 'BRA', 'ARG'...
  fundacao TEXT,               -- AAAA-MM-DD ou AAAA
  extinto_em TEXT,
  cor1 TEXT, cor2 TEXT,        -- '#RRGGBB'
  estadio TEXT, capacidade INTEGER,
  wikidata TEXT, tm_id INTEGER,
  reputacao_ref REAL,          -- 0..100: tamanho real aproximado no ano de início dos dados (torcida/títulos). Só ponto de partida.
  rival_ids TEXT               -- JSON: ["fluminense-rj", ...]
);
CREATE TABLE clube_fontes (clube_id TEXT, fonte_id INTEGER, PRIMARY KEY (clube_id, fonte_id));

CREATE TABLE jogadores (
  id INTEGER PRIMARY KEY,
  nome TEXT NOT NULL,          -- nome completo (da fonte)
  nome_conhecido TEXT,         -- apelido de jogo: 'Romário'
  nascimento TEXT NOT NULL,    -- AAAA-MM-DD (ou AAAA-01-01 com nascimento_precisao='ano')
  nascimento_precisao TEXT DEFAULT 'dia',
  cidade_nasc TEXT, uf_nasc TEXT,
  pais TEXT NOT NULL,          -- nacionalidade esportiva ISO-3
  posicao TEXT NOT NULL,       -- GOL ZAG LD LE VOL MC MEI PE PD CA
  posicoes_sec TEXT,           -- JSON ["MEI","PE"]
  pe TEXT,                     -- D | E | A (ambos) | NULL
  altura INTEGER,              -- cm
  potencial_ref REAL NOT NULL, -- 1..100: estimativa do auge real
  fama_ref REAL,               -- 0..100: quanto ele foi conhecido (sitelinks, seleção)
  caracteristicas TEXT,        -- JSON, até 2: ["cabeceio","drible"]
  origem TEXT NOT NULL DEFAULT 'real',  -- real | complementar_teste | gerado_pos_presente (no banco mestre: só 'real')
  tm_id INTEGER, wikidata TEXT,
  foto_png TEXT, foto_fonte_url TEXT, foto_licenca TEXT,
  conflito TEXT                -- JSON com dados divergentes entre fontes, se houver
);
CREATE TABLE jogador_fontes (jogador_id INTEGER, fonte_id INTEGER, PRIMARY KEY (jogador_id, fonte_id));
-- Regra 1: todo jogador tem pelo menos uma linha em jogador_fontes. O montar_banco.py falha se não tiver.

CREATE TABLE carreira_real (   -- só referência para o simulador
  jogador_id INTEGER, clube_id TEXT,   -- clube_id pode ser NULL para clube fora do banco
  clube_nome TEXT, clube_pais TEXT,
  ano_inicio INTEGER, ano_fim INTEGER, -- NULL = em aberto
  emprestimo INTEGER DEFAULT 0,
  fonte_id INTEGER
);

CREATE TABLE competicoes (
  id TEXT PRIMARY KEY,         -- 'bra-serie-a', 'bra-copa-do-brasil', 'est-sp', 'conmebol-libertadores', 'kings-espanha'
  nome TEXT NOT NULL,
  ambito TEXT NOT NULL,        -- nacional | estadual | continental | kings
  pais TEXT, uf TEXT,
  divisao INTEGER,             -- 1..4 para ligas nacionais
  criada_em INTEGER, extinta_em INTEGER
);
CREATE TABLE formatos (
  competicao_id TEXT, valido_de INTEGER, valido_ate INTEGER,  -- anos, inclusive; valido_ate NULL = até hoje
  formato TEXT NOT NULL,       -- JSON, ver seção 2
  fontes TEXT NOT NULL         -- JSON [url, ...]
);
CREATE TABLE participantes_reais (  -- quem disputou de verdade, por ano (usado ao começar um jogo naquele ano)
  competicao_id TEXT, ano INTEGER, clube_id TEXT, fonte_id INTEGER,
  PRIMARY KEY (competicao_id, ano, clube_id)
);
CREATE TABLE campeoes_reais (competicao_id TEXT, ano INTEGER, clube_id TEXT, vice_id TEXT, fonte_id INTEGER);

CREATE TABLE regras (          -- uma linha por mudança de regra
  regra TEXT, valor TEXT, valido_de TEXT, ambito TEXT DEFAULT 'mundo', fonte TEXT NOT NULL
);
-- regras conhecidas: pontos_vitoria (2|3), substituicoes (int), cartoes (0|1), recuo_goleiro_mao (0|1),
-- gol_de_ouro (0|1), var (0|1), profissionalismo (0|1), janela_transferencias (texto)

CREATE TABLE calendarios (pais TEXT, valido_de INTEGER, valido_ate INTEGER, calendario TEXT, fontes TEXT);
-- calendario = JSON: {"semanas":52, "janelas":[{"competicao":"est-sp","de":3,"ate":15}, ...], "pausas":[...]} (semanas 1..52)

CREATE TABLE kings_ligas (id TEXT PRIMARY KEY, nome TEXT, pais TEXT, inicio TEXT, regras TEXT, draft TEXT, fontes TEXT);
CREATE TABLE kings_times (id TEXT PRIMARY KEY, liga_id TEXT, nome TEXT, presidente TEXT, temporada_inicial TEXT, fonte TEXT);
CREATE TABLE kings_elencos (time_id TEXT, temporada TEXT, jogador_id INTEGER, nome_fonte TEXT, tipo TEXT, fonte TEXT);
-- jogador_id liga a jogadores.id quando for o mesmo jogador; tipo: draft | 11o | coringa | ...

CREATE TABLE nomes_pos_presente (pais TEXT, tipo TEXT, nome TEXT, freq INTEGER); -- tipo: prenome | sobrenome (só de jogadores reais)
CREATE TABLE meta (chave TEXT PRIMARY KEY, valor TEXT);  -- versao_esquema, gerado_em, ...
```

## 2. Formato de competição (`formatos.formato`, JSON)

```json
{
  "participantes": 20,
  "pontos_vitoria": 3,
  "desempates": ["pontos","vitorias","saldo","gols_pro","confronto_direto","cartoes","sorteio"],
  "fases": [
    {"nome": "Turno único", "tipo": "pontos_corridos", "turnos": 2},
    {"nome": "Grupos", "tipo": "grupos", "grupos": 4, "turnos": 2, "classificam": 2},
    {"nome": "Quartas", "tipo": "mata_mata", "ida_volta": true, "gol_fora": false, "desempate": "penaltis",
     "mando_melhor_campanha": true, "chaveamento": "1x8"}
  ],
  "rebaixados": 4, "promovidos": 4,
  "vagas": [{"para": "conmebol-libertadores", "posicoes": [1,2,3,4]}],
  "observacoes": "texto livre"
}
```
- `tipo`: `pontos_corridos` | `grupos` | `mata_mata` | `quadrangular` (grupos de 4 com turno e returno).
- Uma fase recebe os classificados da anterior. `classificam` = por grupo. `chaveamento` = `1x8` (melhor contra pior) ou `sorteio`.
- Para formatos que o motor ainda não sabe rodar, use `"tipo":"especial"` com `observacoes`; o motor cai no formato mais próximo e avisa no log.

## 3. Jogo salvo

`%USERPROFILE%\Documents\FUTEBAS\saves\<nome>.futebas.json.gz`, escrito só pelo Java. Guarda o estado do mundo e o **histórico permanente** (campeões, artilheiros, recordes, carreiras simuladas).

## 4. Projeto Java (`jogo/`)

Pacote base `br.erasdofutebol`:

| Pacote | Conteúdo |
|---|---|
| `dados` | leitura do `mundo.sqlite` (JDBC xerial), modelos imutáveis do banco |
| `mundo` | estado do mundo: `Mundo`, `Clube`, `Jogador`, `Contrato`, `Tecnico`, passagem do tempo, evolução, aposentadoria, base, newgens, complementares |
| `competicao` | motor genérico de formatos, tabelas, desempates, calendário |
| `partida` | motor de partida (portado do FUT.AE `simulation.py`), narração |
| `ia` | transferências, propostas do exterior, demissão de técnicos |
| `kings` | Kings League: ligas, draft, partidas no formato próprio |
| `historia` | histórico permanente, "na vida real × no seu mundo" |
| `ui` | JavaFX |
| `ferramentas` | `LongRun` (simulações sem interface), `Validar` |

Dependências Maven: `org.xerial:sqlite-jdbc`, `com.google.code.gson:gson`, `org.openjfx:javafx-controls` 21, JUnit 5.
