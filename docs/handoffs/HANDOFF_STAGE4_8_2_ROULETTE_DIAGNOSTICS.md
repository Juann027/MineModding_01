# Handoff — Etapa 4.8.2: diagnóstico e tratamento de erro da Roleta de Nascimento

## 1. Contexto

Relato do dono: no cliente real, clicar em Spin mostrava "The server did not answer. Try again." (timeout de 10 s).
**A causa raiz não foi reproduzida.** Spin funcionou em seis configurações: cliente dev + servidor dedicado dev,
cliente dev em singleplayer, cliente de produção (instalador NeoForge 21.1.265 + jar construído + Rimulib, GeckoLib,
SmartBrainLib) em singleplayer, o mesmo aberto em LAN com a ficha zerada e a roleta aberta pelo menu K, mundo
sobrevivência novo pela tela de título, e cliente contra servidor dedicado de produção. Descartados: `jdk.random`
(presente em todos os runtimes Java da Mojang), Specialities por corpo (as 15 servem aos dois), pausa do
singleplayer, modificadores de atributo.

O único caminho encontrado que produz exatamente o sintoma: uma exceção dentro do handler do `BirthRollRequest` é
**engolida pelo NeoForge** (`NetworkRegistry.guard` só registra "Failed to process a synchronized task of the
payload") e nenhuma resposta volta. Por decisão do dono, esta etapa não inventa causa: torna o caminho de erro
estruturado e diagnosticável. Se o problema voltar, o `latest.log` dirá a causa.

## 2. Mudanças

- `BirthRoulette.Outcome.ERROR` (no fim; ordinais estáveis) e `BirthRoulette.afterFailure(rollCountAntes, rollCountDepois)`
  (puro): falha antes de gravar → `ERROR`; giro já gravado → `ROLLED` (nunca repete); ficha ilegível → `ERROR`.
- `BirthRollRequest(reroll, expectedRollCount, requestId)` e `BirthRollReply(outcome, rollCount, requestId)`; o
  registrar de `GameplayNetworking` passou para `"5"` (cliente e servidor precisam deste build).
- `ProfileService.requestRoll`: toda exceção (inclusive `LinkageError`; só `VirtualMachineError` é relançado) vira resposta. Log `ERROR` com `requestId`, nome do jogador, `reroll`,
  `expectedRollCount`, `rollCount` antes/depois, resposta enviada e o stack trace. **Não** registra atributos,
  traços, habilidades, aparência ou a ficha inteira. Envio da ficha/chances/resposta também protegido.
- Cliente (`BirthRouletteScreen`): cada pedido leva um id (`ProfileClientCache.nextRollRequestId`); só a resposta com
  esse id encerra a espera. `ERROR` → "Server error (request #N). Nothing was changed; try again." na hora; sem
  resposta em 200 ticks → "No answer from the server (request #N)…" (timeout separado). As duas mensagens vão ao log
  do cliente com o mesmo número. Texto cortado com `Ui.fit` e dica com o texto inteiro.
- Diagnóstico: `/respectcraft admin roulette_fail <player>` (permissão 2) faz o próximo pedido de giro do jogador
  lançar uma exceção antes de sortear. A marca fica em `persistentData` (`respectcraft_debug_fail_next_roll`),
  é consumida no uso e some com a morte do jogador.
- `SelfTest.rouletteFailures`: ordinais, `afterFailure`, ida e volta do pedido/resposta com `requestId` na rede,
  nova tentativa depois de `ERROR` vale e só então gasta o novo giro, giro gravado antes da falha não se repete.

Mantidos: sorteio só no servidor, 15 Specialities e chances, traços, seis atributos, um novo giro, Continuar,
persistência.

## 3. Testes

| Teste | Resultado |
|---|---|
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` | OK (`RespectCraft self-test: OK`, inclui os casos novos) |
| `python3 tools/player_anim/bbsync.py` | OK |
| Cliente real + servidor dedicado: falha injetada no primeiro giro | Erro em ~1 s com request #1; nada sorteado (`01_server_error.png`, dica `02_error_tooltip.png`) |
| Nova tentativa | Um único giro, novo giro (1) intacto (`03_retry_rolled.png`) |
| Falha injetada no novo giro | Resultado e "Spin again (1)" intactos (`04_reroll_error.png`) |
| Servidor congelado (`SIGSTOP`) durante o giro | Timeout separado com request #5 (`08_timeout.png`); ao retomar, a resposta atrasada é aplicada uma vez (`09_late_reply.png`) |
| Dois pedidos de novo giro com o servidor congelado | Um só giro; o segundo volta `STALE`, "Spin again (0)" (`10_duplicate_resolved.png`) |
| Continuar, reconectar | Mesma Speciality, atributos e traço; `rerolls=0` (`12_after_reconnect.png`) |
| Jar construído em servidor dedicado de produção (NeoForge 21.1.265) | Self-test OK; falha injetada no novo giro → ERROR #2, nova tentativa → um giro (`13_prod_server_error.png`) |

Capturas em `docs/evidencias/etapa4_8_2/`.

## Limitações

- A causa do relato original continua desconhecida; este build só a torna visível. Procurar no `latest.log` do
  servidor (ou do cliente, em singleplayer) por `Birth roulette request #`.
- `VirtualMachineError` (falta de memória etc.) não é tratado e cai no timeout.
- Versão de rede `"5"`: cliente e servidor precisam deste build.
