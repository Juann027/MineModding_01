# RespectCraft — Formato das missões (Quest Engine, Etapa 4.14)

Missões são **dados** dos datapacks do servidor: `data/<namespace>/quest/<caminho>.json`. O id da missão é
`<namespace>:<caminho>` (ex.: `data/respectcraft/quest/journey/a_journey_begins.json` →
`respectcraft:journey/a_journey_begins`) e é o **id persistente** do progresso dos jogadores — não renomeie um arquivo
publicado. `/reload` recarrega tudo; uma definição inválida é **pulada com log** (`RespectCraft quest definition
refused: <id>: <motivo>`) e as outras carregam. O progresso de um jogador numa missão sem definição carregada fica
guardado (e volta a valer quando ela voltar).

Conteúdo distribuído no jar ou num datapack pode ser lido fora do jogo: missões secretas ficam ocultas **no jogo** (nada
delas vai ao cliente antes da descoberta), não criptograficamente.

## Campos

| Campo | Tipo | Padrão | O que é |
|---|---|---|---|
| `format` | int | `1` | Versão do formato. Esta versão lê `1`; maior = recusada. |
| `category` | texto (≤ 32) | `general` | Rótulo no menu (`gui.respectcraft.holo.quests.category.<categoria>` se existir). |
| `sort` | int | `0` | Ordem no menu e no catálogo. |
| `title`, `description` | Component | — | Texto (`{"translate": "chave"}` recomendado; chaves em `en_us.json`/`pt_br.json`). |
| `hint` | Component | — | Pista mostrada como "rumor" (sem nome) enquanto a missão está oculta. Só aparece se declarada. |
| `discover` | condição | sempre | Quando a missão aparece. Ausente = disponível desde o login. |
| `requires` | condição (só estado) | — | Pré-requisito para aceitar. |
| `requirements_text` | lista de Component | `[]` | Pré-requisitos revelados ao jogador no detalhe. |
| `secret` | bool | `false` | Missão secreta (cor/rótulo próprios; nunca enviada antes de descoberta). |
| `accept` | `manual`/`auto` | `manual` | `auto` = ativa ao ser descoberta (se `requires` permitir). |
| `claim` | `manual`/`auto` | `manual` | `auto` = recompensa entregue ao concluir. |
| `repeatable` | bool | `false` | Depois de resgatada, reabre após `cooldown_ticks`. |
| `cooldown_ticks` | long | `0` | Intervalo da repetição (ticks do relógio do Overworld). |
| `time_limit_ticks` | long | `0` | Tempo para concluir depois de aceitar (0 = sem limite) → `expired`. |
| `fail_on_death` | bool | `false` | Morrer com a missão ativa → `failed`. |
| `abandonable` | bool | `true` | O jogador pode abandonar (→ `cancelled`). |
| `retry` | bool | `true` | Depois de `failed`/`expired`/`cancelled` pode aceitar de novo. |
| `objectives` | lista (1–8) | — | Objetivos (abaixo). |
| `rewards` | lista (≤ 8) | `[]` | Recompensas (abaixo). |
| `notices` | objeto | — | `discover`, `complete`, `fail` = estilo da janela (ids de `NoticeStyle`: `system_info`, `quest_discovered`, `secret_discovered`, `objective_updated`, `quest_completed`, `quest_failed`, `reward_available`, `warning`, `danger`, `portal_available`, `mysterious`); `discover_title` = título da janela de descoberta. |

## Estados

`hidden` → `available` (descoberta) → `active` (aceita) → `completed` (objetivos cumpridos) → `claimed`
(recompensa entregue). Também `failed`, `expired`, `cancelled`. Transições válidas em `QuestRules.canTransition`; o
cliente só **pede** Accept / Abandon / Claim / Track / Untrack, e o servidor confere estado, definição e requisitos.

## Condições (`discover`, `requires`)

Toda condição tem `type`. **Estado persistente** (vale no login, no reload e em qualquer evento):

| `type` | Campos | Verdade quando |
|---|---|---|
| `advancement` | `advancement` | o jogador já tem a conquista (obtida agora ou antes; nunca precisa repetir). |
| `quest` | `quest`, `states` (padrão `["completed","claimed"]`) | outra missão está num desses estados. |
| `in_dimension` | `dimension` | o jogador está na dimensão. |

**Acontecimento pontual** (só na avaliação daquele evento):

| `type` | Campos | Verdade quando |
|---|---|---|
| `advancement_earned` | `advancement` | a conquista foi obtida agora. |
| `entered_dimension` | `dimension` | o jogador acabou de chegar à dimensão. |
| `kill` | `entity` (id, `#tag` ou `*`) | o jogador acabou de matar (inclusive pela execução de uma finalização). |
| `finisher` | `victim`, `finisher`, `victim_kind` (`player`/`mob`/`boss`) | uma finalização acabou com **SUCCESS** e morte confirmada (nunca CANCELLED/FAILED). |
| `boss_defeat` | `entity`, `finisher_only` | um chefe acabou de ser derrotado com crédito para o jogador. |

Composição: `{"type":"all","conditions":[…]}`, `{"type":"any","conditions":[…]}`, `{"type":"not","condition":{…}}`.
Limites: profundidade 6, 24 nós. `requires` aceita só condições de estado.

## Objetivos

`{"id": "chave", "type": "...", "count": N, "target": "...", "description": {…}}`. `id` = chave do progresso
(`a-z 0-9 _ -`, ≤ 32, **estável**). `count` 1–10000. `description` opcional (senão o texto é gerado).

| `type` | `target` | Observações |
|---|---|---|
| `kill` | entidade, `#tag` ou `*` | Toda morte confirmada causada pelo jogador. |
| `finisher` | vítima (opcional) | Também `finisher` (id) e `victim_kind`. Só SUCCESS; a morte da execução não conta aqui duas vezes. |
| `boss_defeat` | chefe (opcional) | `BossDefeatEvent` com crédito. |
| `advancement` | conquista | Retroativo: já obtida conta ao aceitar/no login. Vale também para conquistas **sem display** (ocultas, ex.: `respectcraft:time_warp/*`, Etapa 4.15), entregues pelo `AdvancementProgressEvent`. |
| `enter_dimension` | dimensão | Retroativo se já estiver nela ao aceitar. |
| `complete_quest` | missão | Retroativo se já concluída. |

Missão ativa cujo arquivo mudou de objetivos: o progresso dos ids que continuam é mantido (limitado à nova meta), nada é
apagado, e se a nova meta já foi cumprida ela conclui (log `objectives changed in the definition`).

## Recompensas

| `type` | Campos | Efeito |
|---|---|---|
| `experience` | `amount` | Pontos de XP. |
| `item` | `id`, `amount` (1–576) | Itens no inventário; o que não cabe cai aos pés do jogador. |
| `skill` | `id` | Habilidade existente das Skill Trees, sem custo (ignorada se já tiver). |
| `skill_points` | `amount` (1–100) | Pontos de habilidade. |
| `quest` | `id` | Revela outra missão (ignorando a condição dela). |

Nenhuma recompensa executa comandos. O resgate grava `claimed` **antes** de entregar; pedido repetido (pacote
duplicado, reconexão) encontra `claimed` e é recusado. Garantias de persistência: ver o handoff 4.14.

## Exemplos

Missão secreta automática (as três do mod estão em `src/main/resources/data/respectcraft/quest/`):

```json
{
  "category": "secret",
  "title": {"translate": "quest.mypack.hidden.title"},
  "description": {"translate": "quest.mypack.hidden.description"},
  "secret": true,
  "discover": {"type": "finisher", "victim": "respectcraft:strawberry"},
  "accept": "auto",
  "claim": "auto",
  "objectives": [{"id": "more", "type": "finisher", "target": "respectcraft:strawberry", "count": 5}],
  "rewards": [{"type": "experience", "amount": 150}]
}
```

Diária repetível (reabre 1 dia de jogo depois de resgatada):

```json
{
  "category": "daily",
  "title": {"text": "Daily Hunt"},
  "description": {"text": "Defeat 10 undead."},
  "repeatable": true,
  "cooldown_ticks": 24000,
  "accept": "auto",
  "objectives": [{"id": "undead", "type": "kill", "target": "#minecraft:undead", "count": 10}],
  "rewards": [{"type": "item", "id": "minecraft:emerald", "amount": 3}]
}
```

Desafio com tempo, falha na morte e cadeia composta:

```json
{
  "title": {"text": "Into the Fire"},
  "description": {"text": "Reach the Nether within one day and survive."},
  "discover": {"type": "all", "conditions": [
    {"type": "quest", "quest": "respectcraft:journey/a_journey_begins", "states": ["claimed"]},
    {"type": "not", "condition": {"type": "in_dimension", "dimension": "minecraft:the_nether"}}
  ]},
  "time_limit_ticks": 24000,
  "fail_on_death": true,
  "retry": false,
  "objectives": [{"id": "nether", "type": "enter_dimension", "target": "minecraft:the_nether"}],
  "rewards": [{"type": "skill_points", "amount": 1}]
}
```

## Diagnóstico

`/respectcraft quest list | info <quest> | player <player> | diagnose <player> <quest> | discover <player> <quest> |
reset <player> <quest>` (permissão 2). `diagnose` mostra cada nó da condição com o resultado agora. Janela de teste:
`/respectcraft notice cinematic <estilo> [segundos] [jogadores]`.

## Exemplo da Etapa 4.15 (Time Warp)

`data/respectcraft/quest/secret/echoes_of_an_old_world.json`: secreta, descoberta ao estar no Time Warp, aceite e
resgate automáticos, três objetivos `advancement` com textos vagos (achar terra, achar onde alguém morou, voltar). As
conquistas ocultas `respectcraft:time_warp/explored_settlement` e `lingered` (exposição crítica) ficam prontas para
missões futuras sem aparecer no menu.
