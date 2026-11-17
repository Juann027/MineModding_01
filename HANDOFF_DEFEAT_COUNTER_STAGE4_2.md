# Handoff — Etapa 4.2: contador de derrotas acima do nick

## O que já existia (não foi recriado)
O contador já estava implementado desde `dc3e3e6` (criador de personagem + teste de finalizador):
`testgame.DefeatCounter` (servidor), `DefeatCountSyncPayload` + `DefeatCountClientCache` (sync) e
`client.testgame.DefeatCounterNameplate` (desenho). A lógica de incremento e de sincronização foi mantida.

**Por que sumiu das demonstrações:** o nameplate usava `scale(-0.025, -0.025, 0.025)` (convenção até o 1.20). No
1.21.1 o `EntityRenderer.renderNameTag` usa `scale(0.025, -0.025, 0.025)`; com o X negativo o texto ficava espelhado,
de costas para a câmera, e era descartado pelo culling — nunca aparecia.

## Origem dos dados e como aumenta
- Valor no `persistentData` do jogador, chave `tupinicraft_special_defeats` (estável; não renomear). Mantida para não
  perder dados salvos (o padrão novo do projeto seria um attachment; documentado como diferença).
- **+1 só para o ALVO** em `FinisherTestMechanic.complete`, chamado pelo tick do servidor quando o relógio da sessão
  termina. A sessão sai do mapa antes do incremento (`SESSIONS.get(...) != s` → retorna), então não há incremento
  duplicado; pacotes do cliente só carregam intenções (convite/aceite) e não chegam ao `complete`.
- Cancelamentos (Shift, `/stopitstopnow`), dano, morte, desconexão e afastamento passam por `abort` → **não contam**.
- O executor não recebe nada. Dano/morte comuns não contam.
- Comandos de admin `/tupinitest defeats add|set <jogador>` (permissão 2) continuam existindo para testes manuais;
  são operações explícitas de admin, não contagem automática. `/tupinitest defeats get` mostra o próprio valor.
- Regra pura `DefeatCounter.next` (+1 saturado, nunca negativo) entrou no `SelfTest`.

## Como é salvo
- `persistentData` vai no `.dat` do jogador → sobrevive a reconexão e reinício.
- **Correção:** `onClone` agora copia sempre (antes só na morte). A NeoForge só copia o subcompound
  `PlayerPersisted` no `restoreFrom`, então voltar do End (clone sem morte) zerava o contador.
- Troca comum de dimensão mantém o mesmo `ServerPlayer` (sem perda); agora também reenvia o valor.

## Como é sincronizado (mecanismo existente)
`DefeatCountSyncPayload(uuid, count)` servidor → cliente: para todos a cada mudança; no login o novo jogador recebe os
valores de todos e todos recebem o dele; `StartTracking`, respawn e troca de dimensão reenviam; logout manda `-1`
(remove do cache). O cliente só guarda o mapa UUID → valor (limpo ao sair do servidor).

## Como aparece acima do nome (`DefeatCounterNameplate`)
- Ouvinte de `RenderNameTagEvent` com prioridade `LOWEST`. Desenha uma linha `[berinjela][gotas] N` uma linha acima
  do nick, com a mesma âncora (`EntityAttachment.NAME_TAG` + 0,5), billboard, escala 0,025, fundo e as duas passadas
  (translúcida através de paredes + normal; só normal agachado) do vanilla 1.21.1. Sobe junto quando há objetivo
  `BELOW_NAME` no scoreboard.
- Mesmas regras de visibilidade do nick: `canRender` do evento (outro mod que esconde o nick esconde o contador) ou a
  cópia de `LivingEntityRenderer.shouldShowName` (distância 64/32 agachado, invisibilidade, `nametagVisibility` das
  equipes, F1, não no próprio jogador/câmera, não montado) + `NAMETAG_DISTANCE` da NeoForge. Como no vanilla, **você
  não vê o próprio contador** — use `/tupinitest defeats get`.
- Ícones: fonte bitmap própria `assets/tupinicraft/font/defeat_icons.json` (U+E000 berinjela, U+E001 gotas,
  `textures/font/defeat_icons.png`, 8×8 cada). A centralização usa `Font.width` do conjunto inteiro (0, 10, 1000 OK).
- Número em lilás `0xE3B5FF`; o nick não é alterado. Removido o sufixo `[TESTE]` durante finalizações.
- Destaque opcional: quando o valor recebido sobe, `DefeatCountClientCache.flashProgress` dá 700 ms de brilho
  (lilás → branco) e um pulo de escala de até 30%.
- O rig GeckoLib é uma camada do `PlayerRenderer`, então o nick e o contador são os do renderer vanilla
  (Classic/Slim/Body Layers não interferem).

## Testes executados
| Teste | Resultado |
|---|---|
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` (inclui `/tupini selftest` com a regra nova) | OK |
| Dois clientes reais (Xvfb, llvmpipe) + servidor dedicado: Dev e Juca | — |
| Contador 0 acima do nick nos dois clientes | OK (`01`) |
| Finalização válida Dev → Juca: Juca +1, Dev continua 0 | OK (`02`, `03`) |
| Morte dos dois (slime) e respawn: valores mantidos | OK |
| Finalização Juca → Dev cancelada por Shift: Dev continua 0 | OK (`04`) |
| Finalização válida Juca → Dev: cliente do Juca vê Dev = 1 sem reconectar | OK (`05`) |
| Juca desconecta e reconecta: Dev vê Juca = 1, Juca vê Dev = 1 | OK (`06`) |
| `defeats set Juca 1000` + ida e volta ao Nether: 1000 centralizado; Dev invisível esconde nick e contador | OK (`07`) |

Evidências em `docs/evidencias/defeat_counter_stage4_2/`.

## Limitações
- Não testado: volta do End (coberto pela correção do `onClone`, não visualmente), equipes com `nametagVisibility`,
  objetivo `BELOW_NAME`, GPU real, mais de dois clientes, o pulo de escala visto quadro a quadro.
- Finalização contra mob não conta (só alvos jogadores têm contador).
- Dados continuam em `persistentData` (não attachment) para preservar saves.
- Versão mantida em `0.5.6` (sem release automática, a pedido).

## PR e commit
Ver a descrição do PR desta etapa (número e commit de merge informados na entrega).
