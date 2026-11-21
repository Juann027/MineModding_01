# Handoff — Etapa 4.10.1: execuções reais e arquitetura de finalizações exclusivas de chefe

Status: **implementada, PR aberto aguardando aprovação do dono (sem merge automático)**. Base: `main` em `f63a448`
(depois da Etapa 4.10). Versão continua `0.5.7` (sem release). Calamidade_Awakening, `FinisherFx`, `FinisherFxDef`,
shaders, `CinematicCamera` e animações **não foram tocados**.

## 1. Pedido

Finalizações concluídas passam a matar o alvo de verdade. Regra permanente: **chefes podem ser derrotados normalmente,
mas só a finalização exclusiva designada para aquele chefe pode executá-lo pelo Finisher Engine; finalizações comuns
nunca executam chefes.** Nenhum chefe, finalização exclusiva, desbloqueio, conquista ou recompensa é implementado nesta
etapa — só a base de elegibilidade e o desenho do futuro.

## 2. Implementado agora (Implemented Now)

### 2.1 Execuções reais

- Fluxo: servidor valida → cena começa → clips sincronizados → a cena chega ao fim (`finishAt`) →
  `FinisherTestMechanic.complete` libera os participantes (`release`), encerra os efeitos (`FinisherFx.end(COMPLETE)`) e
  chama **uma vez** `FinisherExecution.execute` → o Minecraft processa a morte → a sessão já está limpa.
- Golpe final = `LivingEntity.hurt` com o tipo de dano **`respectcraft:finisher_execution`** (dados em
  `data/respectcraft/damage_type/finisher_execution.json`, `scaling: never`), causado pelo executor (`DamageSource`
  com entidade direta e causadora = executor). Por isso valem: eventos de dano/morte, crédito do matador
  (`lastHurtByPlayer`), loot e XP de morte por jogador, estatísticas (`minecraft.killed`), critérios de conquista
  (ex.: *Monster Hunter*), mensagem de morte (`death.attack.respectcraft.finisher_execution*` → "X was executed by Y").
- Tags do tipo (`data/minecraft/tags/damage_type/`): `bypasses_armor`, `bypasses_shield`, `bypasses_effects`,
  `bypasses_enchantments`, `bypasses_cooldown`, `no_knockback`. **Não** `bypasses_invulnerability`: totem,
  criativo/`Invulnerable` e invulnerabilidade de respawn continuam valendo. Dano = `(vida + absorção + vida máx.) × 4 + 20`
  (finito, ≤ 1e6), sem forçar vida a zero e sem `discard()`.
- Proteções revalidadas na hora do golpe: participantes presentes e no mesmo nível, regra de alvos, PvP
  (`victim.canHarmPlayer(attacker)`: `pvp=false` e times sem fogo amigo) e mods de proteção (`AttackEntityEvent` do
  NeoForge, cancelável) para executor jogador.
- Resultado (`FinisherExecution.Outcome`): **`SUCCESS`** só quando o `LivingDeathEvent` dessa execução chega ao fim sem
  ser cancelado (ouvinte `onDeathConfirmed`, prioridade LOWEST); **`FAILED`** quando o dano foi recusado ("damage was
  prevented"), a vítima sobreviveu ("death was prevented", ex.: totem) ou houve erro interno (log `error`, nunca lança);
  **`CANCELLED`** quando a cena foi interrompida (nenhum golpe final). Cena concluída ≠ morte confirmada.
- `FinisherSessionEvent.getOutcome()` em todo evento; `Completed.getResult()`/`killConfirmed()` com o motivo.
- Idempotência: `complete` sai se a sessão não estiver mais no mapa; `release` tira a sessão do mapa **antes** do golpe.
  Uma sessão nunca fere duas vezes. Cancelamentos (Shift, `/stopitstopnow`, dano, morte externa, saída, troca de
  dimensão, distância) passam por `abort`, que nunca chama o golpe final. Log do servidor:
  `Finisher <id> by <executor> on <alvo> (<nome>): SUCCESS|FAILED (<motivo>)` e `...: CANCELLED (<motivo>)`.
- Jogador vítima (consentimento pelo convite, como antes): morre como qualquer alvo. O contador 🍆💦 continua +1 ao fim da
  cena (regra antiga). Se sobreviver (totem, proteção) e a finalização não tiver `recoveryInScene`, volta a recuperação
  caída de antes (`knocked_down`, 10 s).
- O convite e o início são recusados se o PvP não permitir (`"PvP is not allowed between you and <nome> ..."`).

### 2.2 Proteção de chefes no servidor

- **Quem é chefe** (`FinisherParticipants.kindOf` → `Kind.BOSS`): tag comum `c:bosses` (NeoForge; inclui Wither e
  Dragão do Fim) **ou** a nova tag de dados `respectcraft:bosses` (`data/respectcraft/tags/entity_type/bosses.json`,
  vazia). Partes de entidades de várias partes (`PartEntity`, ex.: Dragão do Fim) contam como a dona
  (`FinisherParticipants.owner`). Nunca por vida, tamanho, nome ou barra de chefe (não há API comum para a barra; mods
  que a usam sem a tag entram pela `respectcraft:bosses` num pacote de dados, sem código).
- **Regra por finalização**: `FinisherDef.targeting()` (`FinisherTargeting`, padrão `REGULAR`):
  - `REGULAR` → jogadores e mobs `ELIGIBLE`; chefe `BOSS_REQUIRES_EXCLUSIVE`.
  - `exclusiveTo(<tipo de entidade do chefe>)` → só esse chefe `ELIGIBLE`; outro chefe, mob ou jogador
    `EXCLUSIVE_OTHER_TARGET`.
- **Onde**: `FinisherTestMechanic.start` (todo início: clique, convite aceito, mob → jogador e
  `/respectcraft admin finisher`) escolhe a finalização **antes** das travas — `Finishers.pickFor` só sorteia o que pode
  executar o alvo (chefe sem exclusiva liberada = nenhuma) — e checa alvo e PvP **antes** de palco, animação, trava,
  efeito do Calamidade ou cooldown. Recusa = nada acontece no mundo. Mensagem em inglês, ex.:
  `Regular finishers cannot be used against Wither. Bosses can only be executed by their own exclusive finisher.` /
  `The takedown finisher cannot be used against Wither. ...` (admin). No clique (Shift + botão direito, mão vazia) a
  recusa sai antes das regras de tamanho, vida e distância. `FinisherExecution` checa a regra de novo (defesa).
- **Combate normal intacto**: nada mudou em dano comum; o teste de fumaça mata o Wither com um golpe de golem e o
  cliente real com espada e dano de jogador (derrota `NORMAL`).
- `BossDefeats` (revisto): a janela de 100 ticks depois da cena saiu. `BossDefeatEvent.Method.FINISHER` só quando a
  morte confirmada veio do dano de execução **e** o crédito (`respectcraft_boss_finisher_credit`, id mantido) foi
  gravado no mesmo tick — o crédito só existe durante o golpe final de uma exclusiva do próprio chefe
  (`markExecution`/`clearExecution`). Espada depois da cena, chefe que sobreviveu ou finalização comum = `NORMAL`.
  `BossDefeats.onFinisherCompleted` foi removido. O log `Boss defeat ...` passou de `debug` para `info`.
- Comando novo (permissão 2): `/respectcraft finishers <alvo>` → `Finisher eligibility for <tipo> (<PLAYER|MOB|BOSS>):`
  e o veredito de cada finalização do catálogo.

### 2.3 Arquivos

| Arquivo | Mudança |
|---|---|
| `finisher/FinisherTargeting.java` (novo) | Regra pura de alvos (`REGULAR`, `exclusiveTo`, `Verdict`) |
| `finisher/FinisherExecution.java` (novo) | Golpe final, tipo de dano, confirmação de morte, `Outcome`/`Result`, regras puras `damageFor`/`outcome` |
| `finisher/FinisherDef.java` | Campo `targeting` (nulo → `REGULAR`), `withTargeting`; construtores antigos mantidos |
| `finisher/Finishers.java` | `pick` só comuns; `pickFor` (alvo + habilidade) com catálogo injetável para testes |
| `finisher/FinisherParticipants.java` | Tag `respectcraft:bosses`, `isBossType`, `owner` (partes) |
| `finisher/FinisherSessionEvent.java` | `getOutcome()`; `Completed` com `Result` |
| `finisher/BossDefeats.java`, `BossDefeatEvent.java` | Crédito só no golpe final; `decide(..., executionDamage)` |
| `testgame/FinisherTestMechanic.java` | `StartResult`; elegibilidade antes das travas; chefes no clique; PvP; golpe final em `complete`; logs |
| `command/RespectCraftCommands.java` | Mensagem da recusa no admin; `/respectcraft finishers <alvo>` |
| `RespectCraft.java` | Ouvinte `FinisherExecution::onDeathConfirmed` (LOWEST); sai `BossDefeats::onFinisherCompleted` |
| `data/respectcraft/damage_type/finisher_execution.json`, `data/minecraft/tags/damage_type/*.json`, `data/respectcraft/tags/entity_type/bosses.json` | Dados novos |
| `lang/en_us.json` | Mensagens de morte |
| `selftest/SelfTest.java` | `bossDefeats` revisto, `finisherExecutions` novo |
| `.github/scripts/server-smoke-test.sh` | Wither/Dragão/zumbi, elegibilidade, Wither morto por golem |
| `docs/evidencias/etapa4_10_1/` | Roteiro do cliente real, capturas e resumo |

Ids novos (estáveis): tipo de dano `respectcraft:finisher_execution`, tag `respectcraft:bosses`. Nenhum id removido.

## 3. Testes

Ver seção 6 (resultados). Regras puras no `SelfTest` (`finisherExecutions`, `bossDefeats`): comum × jogador/mob/chefe,
exclusiva × chefe certo/outro chefe/mob/jogador, catálogo de produção todo comum e recusado contra Wither e Dragão,
`pickFor` (exclusiva nunca sorteada contra mob/jogador, chefe A só a de A, exclusiva travada por habilidade, chefe sem
exclusiva = nada) com **fixtures isoladas nunca registradas**, dano finito acima da vida, `SUCCESS`/`FAILED`/`CANCELLED`,
crédito de chefe só no mesmo tick do golpe, tags de chefe e tipo de dano registrado (sem `bypasses_invulnerability`).

## 4. Planejado para etapas futuras (Planned for Future Stages) — NÃO implementado

Nada abaixo existe no código. É o desenho para quando os chefes do RespectCraft chegarem.

1. **Finalizações exclusivas de chefe.** Cada chefe do mod ganha uma `FinisherDef` própria, registrada em
   `Finishers` com `.withTargeting(FinisherTargeting.exclusiveTo(<tipo do chefe>))`, com clips de jogador e
   `EntityFinisherClip` no rig do chefe (só quando os assets existirem). O catálogo já é o registro: não criar registro
   paralelo. Um chefe com variantes no mesmo tipo de entidade precisaria de uma regra mais fina (hoje a chave é o tipo).
2. **Compatibilidade finalização × chefe**: já é `FinisherTargeting.check`; o motor não muda.
3. **Desbloqueio**: hoje o único portão é `requiresSkill` (Skill Trees), que `pickFor` já respeita. O desbloqueio por
   progressão entra como um predicado a mais em `pickFor`/`start` (ex.: `FinisherUnlocks.has(player, id)`), com dados
   do jogador num attachment próprio (sincronizado no login/mudança, nunca a cada tick). Toda exclusiva deve ter portão.
4. **Persistência**: attachment novo com Codec (ex.: finalizações desbloqueadas e execuções já recompensadas por chefe).
5. **Conquistas**: ouvinte de `BossDefeatEvent` com `Method.FINISHER` (e só ele) dispara um critério próprio
   (`CriterionTrigger` do mod) para o jogador creditado e a finalização; morte comum continua com as conquistas vanilla.
6. **Skill Tree, permissão de invocar outro chefe, armas e habilidades especiais**: recompensas lidas desse attachment;
   cada uma pedida e especificada pelo dono antes.
7. **Verificação no servidor**: tudo acima decidido no servidor; o cliente só exibe.
8. **Recompensa duplicada**: `BossDefeatEvent` já sai no máximo uma vez por chefe (`respectcraft_boss_defeat_posted`);
   a recompensa por jogador deve marcar "já concedida" no attachment do jogador (por chefe/finalização) antes de conceder.

### Personagem feminina / Summoner (só design)

Plano futuro do dono: personagens femininas usarão um sistema próprio de **Summoner** em vez das finalizações comuns.
**Nada disso foi implementado**: finalizações continuam iguais para qualquer corpo; criador, corpo, roleta, ficha e
atributos não mudaram. Quando vier, a decisão de "finalização × invocação" deve ser do servidor, pelo corpo salvo
(`ProfileService.body`/aparência), sem tirar nada de quem já tem.

## 5. Limitações conhecidas

- Jogador × jogador (execução, PvP desligado, times) coberto por regra e código, **não** testado com dois clientes reais.
- Mobs com `NoAI`/sem IA nada mudam; um mob que já está morrendo (`isDeadOrDying`) não é ferido de novo (FAILED).
- O contador 🍆💦 conta a cena concluída mesmo se o golpe final falhar (regra antiga mantida; mudar exige pedido).
- Só um chefe por tipo de entidade pode ter exclusiva (chave = tipo de entidade).
- Testes visuais só com llvmpipe (sem GPU real).

### 5.1 Observações registradas para revisão futura (pedido do dono, NÃO alterar ainda)

Comportamentos atuais, mantidos de propósito até o dono testar e decidir. Nenhum deles deve ser mudado sem pedido.

1. **Contador de derrotas com golpe final impedido.** Em `FinisherTestMechanic.complete`, o contador 🍆💦 do jogador
   vítima sobe **antes** do golpe final, só por a cena ter terminado (regra da Etapa 4.2). Se a morte for impedida por
   um Totem da Imortalidade (ou proteção, PvP, invulnerabilidade), o resultado é `FAILED`, mas a derrota já foi contada.
   Decisão pendente: contar só com `SUCCESS` ou manter "cena concluída = derrota".
2. **`/kill` durante a cena é suprimido.** `FinisherTestMechanic.onIncomingDamage` cancela **todo** dano a um
   participante de sessão ativa e cancela a cena (regra da Etapa 4.8), inclusive `/kill` (`minecraft:generic_kill`,
   que ignora invulnerabilidade). A cena vira `CANCELLED (interrupted by damage)`, o alvo continua vivo e um segundo
   `/kill` (fora da cena) mata normalmente. Visto no cliente real (cenário 5). Decisão pendente: deixar passar dano com
   `bypasses_invulnerability` (ex.: `/kill`, vazio) ou manter a supressão.
3. **PvP e execução com dois clientes sem validação real.** Execução de jogador × jogador, recusa com `pvp=false`,
   times sem fogo amigo, totem em jogador vítima, recuperação caída de quem sobrevive e mob × jogador (opt-in) estão
   cobertos por código e regras puras, mas **não** foram testados com dois clientes reais em multiplayer.

## 6. Resultados

| Teste | Resultado |
|---|---|
| `./gradlew build` (local e CI) | OK |
| `bash .github/scripts/server-smoke-test.sh` (local) | OK — `RespectCraft self-test: OK`; zumbi `MOB` com as três `ELIGIBLE`; Wither e Dragão do Fim `BOSS` com as três `BOSS_REQUIRES_EXCLUSIVE`; Wither morto por golpe de golem (`SmokeWither was slain by Iron Golem`, `Boss defeat minecraft:wither (NORMAL)`) |
| `python3 tools/player_anim/bbsync.py` / `test_export.py` | OK / 0 falhas |
| `python3 tools/fx/check_shaders.py` | Local sem `glslangValidator` (só a cópia do `calamidade_fx.glsl` conferida); no CI, passo estrito OK. Nenhum shader mudou |
| CI do GitHub (workflow Build) | Verde nos commits de código |

### Cliente real (Xvfb, llvmpipe, 1 cliente + servidor dedicado, mundo novo)

Roteiro `docs/evidencias/etapa4_10_1/cenario_cliente_real.sh`; resumo completo em `docs/evidencias/etapa4_10_1/resumo.txt`.

| Cenário | O que se viu |
|---|---|
| 1. Takedown (admin) × zumbi com diamante | `SUCCESS (killed)`; `z_kill was executed by Juca`; diamante no inventário (loot de morte por jogador); XP 0 → 6; `minecraft.killed:zombie` = 1; conquista *Monster Hunter*; câmera cinematográfica no meio (`01_takedown_mid.jpg`) e primeira pessoa de volta no fim (`01_takedown_end.jpg`) |
| 2. Caminho natural: Shift + botão direito, mão vazia, zumbi com 3/20 | Sorteou `combo`; `SUCCESS (killed)`; estatística = 2 |
| 3. Zumbi com totem da imortalidade | `FAILED (death was prevented)`; zumbi vivo; totem consumido (`HandItems` vazio); chat `Execution failed: z_totem survived` |
| 4. `/stopitstopnow` no meio do combo | `CANCELLED (player request)`; zumbi vivo com 20/20 depois do fim previsto da cena; câmera restaurada (`04_cancel_after.jpg`) |
| 5. `/kill` no zumbi no meio da cena | `CANCELLED (interrupted by damage)` — regra da 4.8: dano durante a cena cancela e é descartado; nenhum golpe final (estatística continua 2); o segundo `/kill`, fora da cena, mata normalmente |
| 6. Wither | Admin: `The takedown finisher cannot be used against Boss...` e clique (Shift + direito): `Regular finishers cannot be used against Boss...` (`06_wither_click_refused.jpg`), sem cena, sem trava e `/calamidade status` com `0 / 64`; espada de netherite: 300 → 292,3; `damage ... player_attack by Juca`: `Boss was slain by Juca`, `Boss defeat minecraft:wither (NORMAL), credited Juca`, Estrela do Nether no chão |
| 7. Cliente fechado no meio do combo | `CANCELLED (disconnect)`; zumbi vivo com 20/20 |

Não testado com cliente real: jogador × jogador (execução de jogador, PvP desligado, times, totem em jogador), mob × jogador,
Dragão do Fim pelo clique (coberto pelo teste de fumaça e pela regra de partes), dois clientes, GPU real.
