# FUTÉBAS: contrato de dados v2 (substitui a seção 1 de arquitetura.md)

`dados/mundo.sqlite`, gerado por `coleta/montar_banco.py` (Python). O Java só lê. `meta.versao_esquema = 2`.

```sql
CREATE TABLE fontes (id INTEGER PRIMARY KEY, url TEXT UNIQUE NOT NULL, tipo TEXT, titulo TEXT, acessado_em TEXT, confiabilidade INTEGER, licenca TEXT);

CREATE TABLE paises (
  id TEXT PRIMARY KEY,            -- ISO-3: BRA, ARG, ENG (Inglaterra), ESP...
  nome TEXT NOT NULL,             -- pt-BR: 'Brasil', 'Inglaterra'
  confederacao TEXT,              -- CONMEBOL UEFA CONCACAF AFC CAF OFC
  calendario TEXT NOT NULL,       -- 'ano' (jan–dez) | 'europeu' (ago–mai)
  bandeira_png TEXT,              -- assets/bandeiras/<id>.png
  ranking_fifa INTEGER,
  cor1 TEXT, cor2 TEXT
);

CREATE TABLE clubes (
  id TEXT PRIMARY KEY,            -- slug estável: 'flamengo', 'real-madrid', 'boca-juniors' (sufixo -uf/-pais só se houver colisão)
  nome TEXT NOT NULL,             -- nome popular curto: 'Flamengo'
  nome_completo TEXT, sigla TEXT, -- sigla de 3 letras: 'FLA'
  cidade TEXT, uf TEXT, pais TEXT NOT NULL,
  fundacao TEXT,
  cor1 TEXT, cor2 TEXT,           -- '#RRGGBB'
  estadio TEXT, capacidade INTEGER,
  reputacao REAL,                 -- 0..100
  torcida REAL,                   -- 0..100
  orcamento_ref INTEGER,          -- caixa/receita anual aproximada em R$ (pode ser estimada pela liga/valor do elenco)
  escudo_png TEXT, escudo_fonte TEXT, escudo_licenca TEXT,
  tm_id INTEGER, wikidata TEXT,
  rival_ids TEXT                  -- JSON
);
CREATE TABLE clube_fontes (clube_id TEXT, fonte_id INTEGER, PRIMARY KEY (clube_id, fonte_id));

CREATE TABLE jogadores (
  id INTEGER PRIMARY KEY,
  nome TEXT NOT NULL, nome_conhecido TEXT NOT NULL,   -- nome_conhecido = como aparece na tela ('Vini Jr.', 'Arrascaeta')
  nascimento TEXT,                -- AAAA-MM-DD
  pais TEXT NOT NULL, pais2 TEXT, -- nacionalidade esportiva (+ segunda)
  posicao TEXT NOT NULL,          -- GOL ZAG LD LE VOL MC MEI PE PD CA
  posicoes_sec TEXT, pe TEXT, altura INTEGER,
  clube_id TEXT,                  -- clube atual: elencos 2026/27 em meta.elencos_ate (2026-11-11) (NULL = passe livre)
  emprestado_de TEXT,             -- clube dono, se emprestado (TM: linha futura de devolução; Wikidata P1642)
  contrato_ate TEXT,              -- AAAA-MM-DD
  camisa INTEGER,
  valor_mercado INTEGER,          -- em euros (Transfermarkt), se houver
  forca INTEGER NOT NULL,         -- 1..99 atual (escala Brasfoot/FC)
  potencial INTEGER NOT NULL,     -- 1..99
  atributos TEXT,                 -- JSON opcional: {"ritmo":..,"finalizacao":..,"passe":..,"drible":..,"defesa":..,"fisico":..,"goleiro":..}
  caracteristicas TEXT,           -- JSON até 2
  origem TEXT NOT NULL,           -- 'real' | 'gerado'
  tm_id INTEGER, wikidata TEXT, ea_id INTEGER,
  foto_png TEXT, foto_fonte TEXT, foto_licenca TEXT,
  conflito TEXT
);
CREATE TABLE jogador_fontes (jogador_id INTEGER, fonte_id INTEGER, PRIMARY KEY (jogador_id, fonte_id));
-- Todo jogador com origem='real' tem >= 1 fonte. montar_banco.py falha se não tiver.

CREATE TABLE ligas_clubes (competicao_id TEXT, temporada TEXT, clube_id TEXT, PRIMARY KEY (competicao_id, temporada, clube_id));
-- quem disputa cada liga/divisão/estadual/copa na temporada inicial ('2026' ou '2025/26'). Continental: participantes reais 2026 / 2025/26.
CREATE TABLE tabelas_iniciais (competicao_id TEXT, temporada TEXT, fase TEXT, grupo TEXT, clube_id TEXT, j INT, v INT, e INT, d INT, gp INT, gc INT, pts INT, fonte TEXT);
-- só para ligas agosto–maio já em andamento em 1/jan/2026 (opcional; se faltar o motor simula o 1º turno)

CREATE TABLE competicoes (
  id TEXT PRIMARY KEY,            -- 'bra-1', 'bra-2', 'eng-1', 'est-sp', 'bra-copa', 'conmebol-libertadores', 'uefa-champions', 'fifa-copa-do-mundo', 'kings-brasil'
  nome TEXT NOT NULL, ambito TEXT NOT NULL,  -- nacional | copa_nacional | estadual | continental | selecoes | kings
  pais TEXT, uf TEXT, confederacao TEXT, divisao INTEGER,
  calendario TEXT,                -- 'ano' | 'europeu' | 'evento'
  logo_png TEXT
);
CREATE TABLE formatos (competicao_id TEXT, valido_de INTEGER, valido_ate INTEGER, formato TEXT NOT NULL, fontes TEXT NOT NULL);
-- formato = JSON da seção 2 de arquitetura.md (+ fase tipo 'liga_suica' para a nova Champions: 36 clubes, 8 jogos, tabela única)
CREATE TABLE calendarios (pais TEXT, competicao_id TEXT, temporada TEXT, de TEXT, ate TEXT, fontes TEXT); -- datas reais AAAA-MM-DD
CREATE TABLE regras (regra TEXT, valor TEXT, valido_de TEXT, ambito TEXT, fonte TEXT);

CREATE TABLE selecoes (pais TEXT PRIMARY KEY, tecnico TEXT, ranking_fifa INTEGER, classificada_copa_2026 INTEGER, grupo_copa_2026 TEXT, fonte TEXT);

CREATE TABLE kings_ligas (id TEXT PRIMARY KEY, nome TEXT, pais TEXT, inicio TEXT, regras TEXT, draft TEXT, fontes TEXT, logo_png TEXT);
CREATE TABLE kings_times (id TEXT PRIMARY KEY, liga_id TEXT, nome TEXT, presidente TEXT, escudo_png TEXT, cor1 TEXT, cor2 TEXT, fonte TEXT);
CREATE TABLE kings_elencos (time_id TEXT, temporada TEXT, jogador_id INTEGER, nome_fonte TEXT, tipo TEXT, fonte TEXT);

CREATE TABLE nomes_gerados (pais TEXT, tipo TEXT, nome TEXT, freq INTEGER);  -- prenome | sobrenome, tirados de jogadores reais do país
CREATE TABLE meta (chave TEXT PRIMARY KEY, valor TEXT);
```

## Escala de força (1–99)

Mesma escala do EA FC: ~90 craque mundial, ~80 titular de clube grande europeu, ~72 titular de Série A, ~65 Série B, ~58 Série C, ~52 Série D. Se houver nota do EA FC 26 com fonte, use-a. Senão, estime por valor de mercado + nível da liga + idade (fórmula documentada em `docs/fontes.md`).

## Camada histórica (v2.1) — requisitos

```sql
CREATE TABLE carreira (jogador_id INTEGER, lenda_id INTEGER, clube_id TEXT, clube_nome TEXT, clube_pais TEXT,
  ano_inicio INTEGER, ano_fim INTEGER, emprestimo INTEGER DEFAULT 0, jogos INTEGER, gols INTEGER, fonte_id INTEGER);
  -- carreira real até 2026; jogador_id = jogador ativo; lenda_id = lenda (aposentado)
CREATE TABLE lendas (id INTEGER PRIMARY KEY, nome TEXT NOT NULL, nome_conhecido TEXT NOT NULL, nascimento TEXT, falecimento TEXT,
  pais TEXT, posicao TEXT, forca_auge INTEGER, fama REAL, foto_png TEXT, foto_fonte TEXT, foto_licenca TEXT,
  wikidata TEXT, tm_id INTEGER, resumo TEXT, fonte_id INTEGER);  -- jogadores históricos já aposentados
CREATE TABLE onze_historico (clube_id TEXT, posicao_slot TEXT, ordem INTEGER, lenda_id INTEGER, jogador_id INTEGER, criterio TEXT);
  -- melhor 11 de todos os tempos por clube (4-3-3 ou 4-4-2), real até 2026
CREATE TABLE campeoes_historicos (competicao_id TEXT, temporada TEXT, campeao_clube_id TEXT, campeao_nome TEXT,
  vice_clube_id TEXT, vice_nome TEXT, fonte_id INTEGER);  -- campeões reais até 2025/26
```
No jogo, a partir de 2026, a história do save se soma a isso: novos campeões, novas lendas (jogadores que se aposentam com carreira marcante) e o melhor 11 de cada clube recalculado.

### Convenções da camada histórica (implementadas em `coleta/historia.py`)

- `lendas.id` = número do QID do Wikidata (Pelé `Q12897` → `12897`), estável entre montagens. Lendas só do FUT.AE (sem item no Wikidata): `900000000 + id do FUT.AE`.
- `carreira`: uma linha por passagem; `jogador_id` (ativo) **ou** `lenda_id` preenchido. `clube_id` só quando o clube existe no banco; senão `clube_nome`/`clube_pais` vêm da fonte. `ano_fim` NULL = passagem em aberto. Seleções nacionais não entram (os jogos por seleção só pesam na fama).
- `onze_historico`: 4-3-3, `ordem` 1..11 na sequência GOL, LD, ZAG, ZAG, LE, VOL, MC, MEI, PE, CA, PD; `lenda_id` ou `jogador_id` (em atividade).
- `campeoes_historicos.temporada`: `'2019'` (ano) ou `'2018/19'` (europeu). Só temporadas encerradas até 31/12/2025 (a 2025/26 e a de 2026 são decididas no save). Em competições de seleções, `campeao_clube_id` = código ISO-3 do país (`'BRA'`, `'ARG'`).

### Kings League: troféus (v2.1)

```sql
ALTER: kings_ligas ganha trofeu_png TEXT   -- assets/kings/trofeus/<liga_id>.png
```
`kings_times.escudo_png` = `assets/kings/escudos/<time_id>.png`, `kings_ligas.logo_png` = `assets/kings/ligas/<liga_id>.png`, logo geral em `assets/kings/kings-league.png`, troféu da Kings Cup em `assets/kings/trofeus/kings-cup.png` (todos PNG transparente ≤256 px, do site oficial kingsleague.pro; URLs na tabela `fontes`).

### Estaduais: divisões inferiores (v2.1)

`est-<uf>` = elite (divisao 1); `est-<uf>-2`, `est-<uf>-3`… = divisões de baixo (divisao 2, 3…), com `formatos.formato.sobe_para` = id da divisão de cima e `promovidos`.
