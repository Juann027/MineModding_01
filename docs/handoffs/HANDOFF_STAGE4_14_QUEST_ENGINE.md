# Handoff — Etapa 4.14: Quest Engine & System Notifications

Versão pública inalterada (`0.5.7`); sem release; sem dependências novas. **Base:** a branch do PR #55 (Etapa 4.13.3,
morango de gameplay, música das frutas e Calamidade_Awakening 0.15.0), que ainda não estava na `main` quando a etapa
começou; o PR desta etapa depende dele.

## Auditoria (início)

- `main` em #54; #55 aberto com o que a etapa pede para preservar (frutas, música, Calamidade 0.15.0) → trabalho sobre ele.
- Avisos da 4.11 (`HoloNotices`, `HoloNotice`, `NoticeClientCache`, `HoloToasts`, `HoloConfig`, `HoloNetworking`,
  `NoticeFx`) reaproveitados; nenhum sistema de avisos paralelo.
- Progresso por jogador: mesmo mecanismo da ficha (attachment salvo e copiado na morte). Recompensa de habilidade e
  pontos: `ProfileService.set` (único dono da ficha). Eventos: `AdvancementEvent.AdvancementEarnEvent`,
  `LivingDeathEvent` (LOWEST), `FinisherSessionEvent.Completed`, `BossDefeatEvent`, `PlayerChangedDimensionEvent`,
  `OnDatapackSyncEvent` (conferidos com `javap` no NeoForge 21.1.265).
- Nenhuma mudança arquitetural grande: um pacote novo (`quest`) + extensões dos avisos e do menu K.

## 4.14.1 — Notification HUD V2

- `HoloNotice` ganhou apresentação (`NoticePresentation`: TOAST/CINEMATIC/BOTH), estilo (`NoticeStyle`, 11 estilos com
  cor/glifo/prioridade/som), subtítulo, duração (0,5–20 s; 0 = padrão do jogador), grupo e `modifiers` (ids de
  fenômenos). Construtor antigo mantido: avisos da 4.11 continuam cartões. Protocolo `holo` → `"2"`.
- Fila pura `NoticeQueue` (limite 6, prioridade, FIFO, mesmo grupo substitui, acima do limite sai o menos prioritário);
  `NoticeClientCache`: duplicatas por chave (32 lembradas), interrupção só por aviso ≥ 2 de prioridade acima e depois
  de 0,25 s (saída rápida de 0,18 s), som com intervalo mínimo de 300 ms, histórico agrupado, limpeza no logout.
- `HoloCinematic`: camada da GUI (acima dos cartões): vidro escuro, moldura, cantoneiras luminosas, glifo + estilo,
  título, subtítulo, texto, barra de duração (≥ 1,2 s), abertura linha→altura. Sem `Screen`, sem capturar mouse, não
  pausa; 1ª e 3ª pessoa. Desligada → vira cartão. Movimento reduzido = só esmaecer; alto contraste = vidro opaco sem
  efeitos.
- Config (`respectcraft-holomenu-client.toml`): `cinematicNotices`, `cinematicScale`, `cinematicX/Y`,
  `cinematicOpacity`, `noticeVolume`, `cinematicSeconds`, `cinematicAnimation`, `cinematicEffects`, e (4.14.3)
  `questTracker`, `trackerScale`, `trackerX/Y`, `trackerOpacity`. Painel Display com páginas Menu / Notices / Tracker
  e botão **Preview** (aviso só local, sem histórico). As preferências do Calamidade (`post.screenEffects()`) só existem
  dentro de um passe de pós; a janela respeita as opções de acessibilidade do próprio menu.
- **Holographic UI Phenomena (preparação):** `client.holo.HoloPhenomena` (`Look` mutável, `Modifier`, registro por id,
  globais liga/desliga, limpo no logout) aplicado pela janela e pelo rastreador. Nenhum fenômeno registrado.
- `/respectcraft notice styles` e `/respectcraft notice cinematic <estilo> [segundos] [jogadores]`.

## 4.14.2 — Quest Engine

Pacote `quest` (formato em [`docs/QUESTS.md`](../QUESTS.md)):

| Classe | Papel |
|---|---|
| `QuestDef` (+ `Flags`, `Notices`) | Definição (JSON, `FILE_CODEC`), validação, assinatura dos objetivos. |
| `QuestCondition` | Condições por registro de tipos (`register`), estado × acontecimento, `all/any/not` limitados. |
| `QuestObjective` (+ `Type`) | Objetivos por registro de tipos: `kill`, `finisher`, `boss_defeat`, `advancement`, `enter_dimension`, `complete_quest`. |
| `QuestReward` | `experience`, `item`, `skill`, `skill_points`, `quest`. Sem comandos. |
| `QuestState`, `QuestRules` | Ciclo de vida, transições, limites. |
| `QuestProgress`, `QuestLog` | Progresso salvo (attachment `respectcraft:quests`). |
| `QuestEvent`, `QuestTrigger`, `QuestContext`, `QuestMatcher` | Acontecimento puro, gatilhos, estado consultável, alvo id/#tag/*. |
| `QuestCatalog`, `QuestRegistry` | Índice por gatilho; carga por datapack (`data/<ns>/quest`). |
| `QuestEngine` | Motor **puro** (no `SelfTest`): handle, reconcile, sweep, accept/abandon/claim/track, death, forceDiscover, reset. |
| `QuestService` | Único dono no servidor: eventos → motor → grava, avisa, concede, sincroniza. |
| `QuestCommands` | `/respectcraft quest list|info|player|diagnose|discover|reset` (permissão 2). |
| `network.QuestNetworking`, `QuestClientCache` | Snapshot/atualização individuais; pedido do cliente. |

- **Eventos, não polling:** cada evento olha só as missões indexadas pelo gatilho. Varredura de 5 s apenas do registro
  de cada jogador online (tempo limite e reabertura de repetíveis). Login e `/reload`: `reconcile` (descoberta por
  estado, objetivos retroativos, migração).
- **Finalizações:** só `Completed` com `SUCCESS` + `killConfirmed()`. CANCELLED/FAILED nunca. A morte vanilla da
  execução conta só para `kill`; o `finisher` só pelo evento do Finisher Engine → nenhuma dupla contagem.
- **Evento revelador:** objetivos das ativas são processados antes da descoberta; quem revela não conta.
- **Segurança:** o cliente só envia `QuestActionRequest(ação, id)`; servidor valida existência, estado descoberto,
  transição, requisitos e limite (8 pedidos/s, attachment sem save). Missão oculta nunca vai no pacote; pista (`hint`)
  vai sem id. Pacotes repetidos de resgate encontram `claimed` e são recusados.
- **Persistência e garantias reais:** `QuestLog` fica no NBT do jogador junto com XP, inventário e ficha; o Minecraft
  grava o arquivo inteiro (temporário + troca). O resgate grava `claimed` antes de entregar, no mesmo tick. Numa queda,
  o jogador volta ao último salvamento inteiro: ou a missão ainda `completed` sem nada entregue, ou `claimed` com tudo
  entregue. Itens que não couberam e caíram no chão ficam no chunk (salvo à parte) e podem se perder numa queda entre
  os dois salvamentos. Não há atomicidade além disso. A roleta de novo giro zera as habilidades (regra antiga): uma
  habilidade dada por missão também some nesse caso.
- **IDs/compatibilidade:** id da missão = caminho do arquivo; ids sem definição ficam no registro (comando `player`
  mostra "kept"); objetivos alterados migram sem apagar; formato futuro recusado com log.

## 4.14.3 — Menu K e rastreador

- Seção **08 Quests** (`Section.QUESTS`, ícone `montanha`, tecla 8 na tela de reserva): filtros, cartões por estado/
  categoria (secretas em violeta), progresso, detalhe (descrição, objetivos com barra, recompensas, requisitos, tempo),
  Accept / Try again / Track / Untrack / Abandon (segundo clique) / Claim; "Rumors" com as pistas. Ponto aceso no chip
  = novas/mudadas + por resgatar.
- `QuestTrackerHud`: painel compacto (até 3 rastreadas), borda direita abaixo dos efeitos por padrão, some com F1/F3.
- Textos `en_us` + **`pt_br.json` novo, só com as chaves da 4.14** (pedido explícito do dono; o resto cai no inglês).

## 4.14.4 — Missões iniciais

| Id | Tipo | Descoberta | Objetivos | Recompensas |
|---|---|---|---|---|
| `respectcraft:journey/a_journey_begins` | comum, aceitação e resgate manuais | `advancement minecraft:story/root` (bancada) | Stone Age, Getting an Upgrade, 3 × `#minecraft:undead` | 60 XP, 6 pães, 1 ponto de habilidade, revela a 3 |
| `respectcraft:secret/a_fruitful_encounter` | secreta, automática, única, não abandonável | 1ª finalização SUCCESS de `respectcraft:strawberry` | mais 5 execuções de morango | 150 XP (automático) |
| `respectcraft:journey/beyond_the_unknown` | cadeia, condição composta, pista | `all[quest a_journey_begins claimed, any[Stone Age, Enter the Nether], not in_dimension inverted_realm]` | entrar no Inverted Realm, 1 agua-viva filhote | 100 XP, habilidade `combate/punhos_de_aco` |

Avisos especiais na 3: descoberta `portal_available`, conclusão `mysterious`. Limitação: o Inverted Realm ainda só é
acessível por `/respectcraft realm enter` (operadores), dito no `requirements_text`.

## Testes executados

- `./gradlew build` OK. Teste de fumaça do servidor dedicado OK: `RespectCraft self-test: OK` (inclui `noticesV2` e
  `QuestSelfTest`: 7 definições inválidas recusadas, descoberta por evento/estado, aceitação manual/automática,
  incremental, tag, alvo errado, conclusão, resgate único, cadeia composta, retroativo, dimensão, secreta sem contar o
  evento revelador, morte comum ≠ finalização, repetível com intervalo, falha na morte sem nova tentativa, tempo
  limite, abandonar, rastrear com limite, transições arbitrárias, Codec NBT, missão sem definição mantida, migração e as
  três missões do jar do começo ao fim); `quests: 3 loaded, 0 invalid`; `quest list`; `notice styles`; janela de 0,5 s.
- Dois clientes reais (Xvfb, Ana e Beto), evidências em `docs/evidencias/stage4_14/` (`server_quest_log.txt`):
  - janela andando + girando a câmera (1ª pessoa), em 3ª pessoa, janela de 0,5 s (vídeo → folha), fila com
    prioridade/interrupção (folha), escala/posição/volume/duração alterados pelo Display + Preview, desligada → cartão,
    1280×720 GUI 3 com movimento reduzido (Beto);
  - Quest 1 descoberta só para Ana (Beto nada); aceitar/rastrear pelo menu; rastreador na HUD; objetivos de conquista
    e de mortos-vivos; conclusão; **três cliques em Claim → uma entrega** (6 pães, 1 ponto) e Quest 3 revelada;
  - Quest 2: SUCCESS revelou com 0/5; **FAILED** (morango invulnerável, sobrevivência) e **CANCELLED**
    (`/stopitstopnow` e dano) não contaram; Ana morreu no meio e o progresso continuou após o respawn; 5 SUCCESS →
    concluída e resgatada sozinha; reset + descoberta capturada;
  - Quest 3: aceitar, `realm enter` (troca de dimensão conta), agua-viva → conclusão `mysterious`; resgate com a
    habilidade (`skills=1`);
  - **reinício do servidor** + reconexão: progresso e snapshot iguais; Beto com 0 missões e depois a própria Quest 1;
    `diagnose`; `/reload` com datapack de teste: 4 carregadas, 1 recusada com motivo, repetível concluiu → reabriu
    duas vezes, datapack removido → progresso mantido ("kept").
- **Não testado com cliente:** pacotes forjados/ids de missões alheias (sem cliente modificado; coberto no motor e no
  `SelfTest`: aceitar secreta não descoberta é recusado), o modo mundo do menu com a seção Quests (testado no modo
  SCREEN; a mesma `HoloView` serve às duas cascas), GUI 320×240, conquistas de outros mods (mesmo evento do NeoForge).

## Limitações e próximos passos

- Janela cinematográfica no modo SCREEN fica atrás da tela do menu (camadas da GUI vêm antes da `Screen`); no modo
  mundo (padrão) fica na frente.
- Interrupção corta a janela de menor prioridade sem reexibi-la (continua no histórico Alerts).
- Sem fenômenos registrados; sem portais de missão, invasões, Time Warp (roadmap).
