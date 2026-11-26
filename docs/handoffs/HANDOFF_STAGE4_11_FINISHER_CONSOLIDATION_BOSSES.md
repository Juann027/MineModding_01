# Handoff — Etapa 4.11: consolidação do Finisher Engine e infraestrutura de chefes personalizados

Status: **PR #49 aberto (contador só com SUCCESS e `/kill` aprovados e aplicados), empilhado sobre o #48 (que está sobre o #47). Nada foi integrado na `main`.** `mod_version`
continua `0.5.7` (sem release).

## 1. Estado de integração encontrado

| Item | Estado em 2026-11-23 |
|---|---|
| `main` | `f63a448` = merge do PR #46 (Etapa 4.10, Calamidade 0.10.0) |
| PR #47 (4.10.1 execuções reais + regra de chefes + hotfix Calamidade 0.11.1) | aberto, `mergeable_state: clean`, **não integrado** |
| PR #48 (4.10.2 validação com dois clientes + correção do `/stop`) | aberto, base = branch do #47, **não integrado** |
| Esta etapa | branch `claude/brave-rubin-kivvr3`, criada a partir do topo do #48 (`80de935`) |

Ordem de merge necessária: **#47 → #48 → 4.11**. Esta etapa não muda nada do #47/#48; só acrescenta.

## 2. Revisão do ciclo de vida (Finisher Engine)

Revisado: `FinisherTestMechanic` (convite, `start`, tick, `complete`, `abort`, saídas, `/stop`), `FinisherExecution`,
`BossDefeats`, `FinisherSessionEvent`, `FinisherVisualClientCache`.

| Fase | Comportamento verificado | Resultado |
|---|---|---|
| Execução | escolha da finalização → regra de alvos/PvP → travas → clips → palco → FX → sessão no mapa (nessa ordem: toda recusa acontece antes de mexer no mundo) | ok |
| Conclusão | sessão sai do mapa **antes** do golpe final (uma execução por sessão); FX `COMPLETE`; cooldowns; contador; golpe; recuperação se sobreviveu; `Completed` | ok |
| Cancelamento | Shift, `/stopitstopnow`, dano, morte, saída, dimensão, distância, servidor parando → `abort` idempotente, FX `ABORT`, animações paradas, `Aborted` | ok |
| Recuperação | `INCAPACITATED` limpo por tempo, dimensão, morte, logout, `/stopitstopnow`, `/stop` | ok |
| Multiplayer | animações pelo relógio do servidor + `StartTracking` (validado na 4.10.2); `FinisherVisualSyncPayload` a todos | ok (ver 2.2) |
| Estado estático | `SESSIONS`, `PENDING`, `INCAPACITATED`, cooldowns limpos no `/stop` (4.10.2); `FinisherExecution.IN_PROGRESS` limpo no `finally` | ok |

### 2.1 Correção aplicada (única mudança de ciclo de vida)

**Participante que vira espectador no meio da cena** (`/gamemode spectator`): antes a cena continuava, o espectador
ficava preso no palco por teleporte e no fim o golpe final era sempre recusado (`FAILED`, o modo espectador é
invulnerável) — e o contador de derrotas subia mesmo assim. Agora o tick da sessão cancela:
`CANCELLED (participant became a spectator)`. Nenhuma outra regra mudou.

### 2.2 Achados sem mudança (registrados)

- `FinisherVisualClientCache` só recebe dados (`get` não tem chamador) e quem entra no meio da cena não recebe o
  `active=true`. Inofensivo hoje (a cena visível usa o estado de animação, sincronizado no `StartTracking`); se um
  dia alguém ler o cache, reenviar no `StartTracking`.
- Uma única exclusiva por tipo de chefe na `FinisherTargeting` (limite herdado da 4.10.1); a ficha de chefe aceita
  várias exclusivas, todas com `exclusiveTo(<mesmo tipo>)`.

## 3. Contador de derrotas e `/kill` — decisões do dono (aplicadas na segunda rodada do PR #49)

### 3.1 Contador só com `SUCCESS`
`FinisherTestMechanic.complete` agora incrementa **depois** do golpe final e só quando
`FinisherRules.countsDefeat(kind, outcome)` é verdadeiro: vítima jogador **e** `Outcome.SUCCESS` (morte confirmada).
`FAILED` (totem, proteção, PvP desligado no meio, invulnerabilidade) e `CANCELLED` nunca contam; mob e chefe nunca
contam (regra da 4.8 mantida em `countsDefeat(kind)`). O jogador morto ainda é o mesmo objeto no tick do golpe e o
`DefeatCounter.onClone` copia o valor para o respawn, então o incremento sobrevive à morte (validado: contador 1 depois
da execução PvP). Log novo: `Defeat counter of <nome> = <n>`.

### 3.2 `/kill` e dano que ignora invulnerabilidade
`onIncomingDamage`: dano com a tag `minecraft:bypasses_invulnerability` (`/kill` = `generic_kill`, vazio
`fell_out_of_world`, e qualquer tipo de mod com a tag) a um participante **cancela a cena e passa** normalmente
(`CANCELLED (interrupted by unblockable damage)`; o alvo morre). Regra pura: `FinisherRules.suppressSessionDamage`.
Todo outro dano continua descartado + cancela; o golpe automático do próprio parceiro mob continua descartado sem
cancelar. O contador não sobe (a cena foi cancelada).

### 3.3 Testes de regressão
- `SelfTest.defeats`: para cada `Outcome`, só `SUCCESS` contra jogador conta; mob e chefe nunca; `suppressSessionDamage`
  deixa passar só o dano que ignora invulnerabilidade; no servidor, `generic_kill` e `fell_out_of_world` têm a tag.
- Cenário de dois clientes reproduzível: `docs/evidencias/etapa4_11/cenario_4_11.sh`.

## 4. Infraestrutura de chefes personalizados (novo pacote `com.respectcraft.boss`)

Nenhum chefe, modelo, animação, finalização exclusiva ou recompensa foi criado. **O registro real está vazio.**

| Classe | Papel |
|---|---|
| `BossProfile` | Ficha do chefe (dados): tipo de entidade (identidade), ids das finalizações exclusivas, condições de execução. |
| `Bosses` | Registro em código (como `Finishers`). `register` valida contra o catálogo (exclusiva existe e é `exclusiveTo(este tipo)`, sem repetição) e recusa duplicata. `validate` e `checkExecution` são puras (no `SelfTest`). |
| `BossCondition` | Condições seladas: `HealthBelow(fração)`, `RequiresSkill(id)` (Skill Trees), `RequiresProgress(id)` (gancho de progressão; **sem sistema de progressão, sempre recusa**). `Context` = o que o servidor sabe. |
| `BossExecutionCheckEvent` | Evento no `NeoForge.EVENT_BUS` antes da cena contra o chefe; ouvintes (habilidades, progressão, fases) só podem **recusar** (`deny`, a primeira vale). Ninguém escuta hoje. |
| `BossDefeatEvent.getProfile()` | Ficha do chefe derrotado (vazio para vanilla/outros mods). Ponto para recompensas. |

**Identificação:** continua pelas tags (`c:bosses` ou `respectcraft:bosses`, `FinisherParticipants.kindOf`). A ficha
não decide quem é chefe; só acrescenta o que é dele. Chefe com tag e sem ficha (Wither, Dragão) = como antes.

**Fluxo de elegibilidade contra chefe** (`FinisherTestMechanic.eligibility`, servidor, antes de palco/animação/trava/FX):
1. `FinisherTargeting`: comum → `BOSS_REQUIRES_EXCLUSIVE`; exclusiva de outro → `EXCLUSIVE_OTHER_TARGET` (4.10.1).
2. Executor precisa ser jogador.
3. `Bosses.checkExecution`: ficha existe, a exclusiva está nela, todas as condições passam.
4. `BossExecutionCheckEvent` sem recusa.
5. PvP e travas comuns (distância, vida < 25%, cooldown, sessão ativa).

**Pontos de integração:**
- Habilidades: `BossCondition.RequiresSkill` e `FinisherDef.requiresSkill` (desbloqueio já existente).
- Progressão: `BossCondition.RequiresProgress` + `Context.progress` (hoje `key -> false`), ou ouvinte do `BossExecutionCheckEvent`.
- Execução: `FinisherSessionEvent.Completed/Aborted` (resultado `SUCCESS/FAILED/CANCELLED`).
- Derrota/recompensas: `BossDefeatEvent` (`NORMAL`/`FINISHER`, creditado, finalização, `getProfile()`); **ninguém concede nada**.

**Como um chefe entra (quando aprovado):** entidade em `ModEntities` + tag `respectcraft:bosses`; finalização em
`Finishers` com `.withTargeting(FinisherTargeting.exclusiveTo(tipo))` (e `EntityFinisherClip` se houver asset);
`Bosses.register(BossProfile.of(tipo).withExclusiveFinisher(id).withCondition(...))` depois dela.

Comando: `/respectcraft finishers <alvo>` agora mostra também `Boss profile: ...` para chefes.

## 5. Calamidade — fora do escopo
Nada do `fx`, shaders, `GeoFxKeyframes`, `VISUAL_FX` ou dependência foi tocado. `check_shaders.py`: cópia GLSL igual.

## 6. Validação (comandos rodados nesta sessão)

| Teste | Resultado |
|---|---|
| `./gradlew build` | OK (`build/libs/respectcraft-0.5.7.jar`) |
| `bash .github/scripts/server-smoke-test.sh` | OK — `RespectCraft self-test: OK`; Wither/Dragão `BOSS_REQUIRES_EXCLUSIVE` e `Boss profile: none (normal defeat only)`; Wither morto por golem = `NORMAL` |
| `SelfTest` | OK — bloco `bossRegistry` (14 casos) + regressões de contador/dano (3 casos) |
| `bbsync.py` / `test_export.py` | OK / 0 falhas |
| `check_shaders.py` | cópia igual; `glslangValidator` ausente aqui (passo estrito roda no CI) |
| CI do PR #49 (workflow Build) | ver o PR |

### 6.1 Dois clientes reais (Juca e Bia, servidor dedicado de desenvolvimento, Xvfb/llvmpipe)
Script: `docs/evidencias/etapa4_11/cenario_4_11.sh`; resumo (log, chat, consultas): `docs/evidencias/etapa4_11/resumo.txt`.

| Cenário | Log do servidor | Resultado |
|---|---|---|
| Executor vira espectador no meio do COMBO | `CANCELLED (participant became a spectator)` | Bia 20/20; Juca anda livre como espectador; barras de cinema somem (`01a`/`01b`) |
| Vítima vira espectadora no meio | `CANCELLED (participant became a spectator)` | câmera restaurada (`01c`) |
| `/kill Bia` no meio | `CANCELLED (interrupted by unblockable damage)` + `Bia was killed` | morre no primeiro `/kill`; contador não sobe (`02`) |
| `/kill` no zumbi vítima no meio | 1ª rodada: `CANCELLED (interrupted by unblockable damage)` + `zk was killed` | ok (ver nota) |
| Execução PvP (TAKEDOWN) | `Bia was executed by Juca` · `SUCCESS (killed)` · `Defeat counter of Bia = 1` | plaqueta 1 (`03b`) |
| Totem na mão secundária | `FAILED (death was prevented)`, **sem** linha de contador | Postmortal, recuperação de 10 s, vida 7 (`04`) |
| Shift real da vítima (xdotool) | `CANCELLED (cancelled with Shift)` | (`05`) |
| `/stop` no meio | `CANCELLED (server stopping)` antes de `Stopping server` | clientes em "Server closed" (`06b`) |

Erros: nenhum do RespectCraft/Calamidade; só `No key layers` do ambiente (servidor plano sem camadas).

Nota sobre a rodada: a primeira execução do script rodou sem `xdotool` instalado (Shift não foi apertado, então o
cenário 5 terminou em `SUCCESS` e o contador foi a 2 — comportamento correto para uma execução concluída). Na segunda,
com `xdotool`, tudo saiu como na tabela, mas o zumbi do cenário `/kill` queimou ao sol e a cena caiu antes por
`interrupted by damage` (dano comum, descartado e cancelado — também correto); o `/kill` dele foi validado na primeira
rodada. O script agora põe capacete no zumbi.

## 7. Limitações
- Sem chefes reais, o caminho exclusiva → `checkExecution` → cena só é coberto por regra pura (fixtures), não no mundo.
- `RequiresProgress` sempre recusa até existir um sistema de progressão.
- Só llvmpipe, clientes e servidor na mesma máquina (sem latência real).
- Nenhum chefe definitivo: design, habilidades, fases e exclusivas ficam com o dono.
