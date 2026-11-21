# Handoff — Etapa 4.9.1: remoção do Leão do Fim

Status: **implementada e testada** (`./gradlew build`, `bbsync.py`, `test_export.py` e teste de fumaça do servidor
dedicado). Base: `main` em `2c829fa`. Versão continua `0.5.7` (sem release).

## 1. Pedido

O Leão do Fim era um asset antigo de outro projeto, sem papel no jogo. Pedido explícito do dono: remover por completo
(entidade, renderer, `/leaodofim`, ovo, aba criativa, traduções, geo, animação, texturas, scripts e `.bbmodel`), manter o
mecanismo de palco por tipo e todos os casos de vários rigs do `SelfTest`, e trocar o Leão por zumbis vanilla no teste
de fumaça.

## 2. Aviso de compatibilidade

**Leões do Fim que existam em mundos salvos somem** ao carregar o mundo com esta versão, e o item
`respectcraft:leao_do_fim_spawn_egg` some de inventários e baús (o Minecraft descarta ids de registro desconhecidos).
Faça backup dos mundos antes de atualizar. Os ids `respectcraft:leao_do_fim` e `respectcraft:leao_do_fim_spawn_egg`
ficam **aposentados** (listados no `CLAUDE.md`): não os reutilize.

## 3. O que saiu

| Tipo | Arquivos |
|---|---|
| Código | `entity/LeaoDoFimEntity`, `client/render/LeaoDoFimRenderer`, `command/LeaoDoFimCommands` (e o registro em `RespectCraft`/`RespectCraftClient`), `ModEntities.LEAO_DO_FIM` + atributos, `ModItems.LEAO_DO_FIM_SPAWN_EGG` (aba de ovos e aba do mod), `Finishers.LEAO_DO_FIM_STAGING` |
| Recursos | `geo/entity/leao_do_fim.geo.json`, `animations/entity/leao_do_fim.animation.json`, `textures/entity/leao_do_fim{,_glowmask}.png`, `models/item/leao_do_fim_spawn_egg.json`, duas chaves do `en_us.json` |
| Fontes/ferramentas | `model_sources/leao_do_fim.bbmodel`, `model_sources/lionxx{,_glowmask}.png` (texturas-fonte do mesmo modelo), `tools/export_leao.py`, `tools/validate_leao.py` |

## 4. O que mudou no lugar

- **Aba do mod:** o ícone passou a ser o ovo da Giant Jellyfish (`agua_viva_spawn_egg`).
- **Palco por tipo:** `FinisherParticipants.registerStaging` continua igual, só sem registros. Nenhum mob atual tem
  palco próprio (todos usam `Alignment.AUTO`).
- **`SelfTest`:** o id do Leão virou o id fictício `respectcraft:test_rig_mob` (nenhuma entidade registrada). Todos
  os casos de vários rigs continuam (resolve, Hold, clip por tipo/papel, alinhamento, palco, recusas). O palco de teste
  é registrado uma vez com `staging(id).orElseGet(registerStaging...)` porque o selftest pode rodar várias vezes; o id
  nunca casa com uma entidade real. Caso novo: palco duplicado é recusado (`IllegalStateException`).
- **Teste de fumaça:** três `minecraft:zombie` (com `time set midnight` antes e `PersistenceRequired` nos marcados, para
  não queimarem nem sumirem); a finalização mob × mob continua sendo recusada (`has no player participant`). Novo:
  o jar não pode conter nada com `leao`/`lionxx`.
- **Evidência da 4.8** (`docs/evidencias/etapa4_8/cenario_cliente_real.sh`): mantida como registro histórico, com uma
  nota de que o Leão saiu e de como repetir o cenário com zumbis.
- README, `CLAUDE.md`, `AGENTS.md` e a descrição do `neoforge.mods.toml` sem o Leão.

## 5. Decisões

- **SmartBrainLib continua declarada.** Nenhuma classe do mod a usa (nem o Leão usava: ele tinha goals vanilla), mas
  remover dependência não foi pedido. Fica anotado no `CLAUDE.md`/`AGENTS.md` para o dono decidir.
- Handoffs antigos que citam o Leão não foram reescritos (são históricos).

## 6. Testes

- `./gradlew build`: OK.
- `bash .github/scripts/server-smoke-test.sh`: OK (zumbis invocados, mob × mob recusado, `RespectCraft self-test: OK`).
- `python3 tools/player_anim/bbsync.py` e `test_export.py`: OK.
- Sem cliente real: a mudança não toca render, telas nem sincronização (só remove um mob).
