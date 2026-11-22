# Handoff — Etapa 4.10.2: validação multiplayer com dois clientes reais

Status: **concluída, PR empilhado aberto aguardando aprovação do dono (sem merge automático)**. Base: a branch do PR #47
(Etapa 4.10.1 + hotfix do Calamidade 0.11.1), commit `2571b1c`, **ainda não integrada na `main`**. Este PR depende do
#47: integre o #47 primeiro (ou junto). Versão continua `0.5.7` (sem release). Nada da 4.10.1 foi refeito; Calamidade,
`FinisherFx`, shaders, câmera, animações, regra do contador de derrotas e regra do `/kill` durante a cena **não foram
tocados**. Não começa a Etapa 4.10.3.

## 1. Pedido

Validar de verdade, com **dois clientes reais** num servidor dedicado, o que a 4.10.1 deixou só coberto por código
(seção 5.1, item 3): jogador × jogador, PvP desligado e times sem fogo amigo, totem em jogador vítima, mob × jogador,
sincronização entre clientes, desconexão/interrupção/recuperação de estado; corrigir só defeitos reproduzidos.

## 2. Como foi testado

- Servidor dedicado de desenvolvimento (`runServer`, mundo plano novo) + **dois** `runClient` (Juca na tela `:99`, Bia na
  `:98`), Xvfb + llvmpipe, Calamidade 0.11.1. Entrada real nos clientes por `xdotool` (Shift + clique direito, W, Shift,
  chat com `/respectcraft test finisher accept|deny|mobs on|mobtest` e `/stopitstopnow` digitados no cliente).
- Roteiros reproduzíveis em `docs/evidencias/etapa4_10_2/`:
  - `cenario_dois_clientes.sh` — passos 1–13 (mundo novo a cada execução);
  - `cenario_dois_clientes_parte2.sh` — passos 13b–18 (mesmo mundo; reinícios com `pvp=false` e `pvp=true`);
  - `cenario_parada_do_servidor.sh` — validação da correção (seção 4).
- Resumo com o log do servidor, o chat dos dois clientes e as consultas: `docs/evidencias/etapa4_10_2/resumo.txt`.

## 3. Resultados

| # | Cenário | Resultado |
|---|---|---|
| 1 | Times com `friendlyFire false`: Shift + clique direito em Bia (4/20) e `admin finisher` | Recusados antes da cena: `PvP is not allowed between you and Bia (server PvP setting or team rules).`; `0 / 64` instâncias do Calamidade |
| 2 | **PvP pelo caminho natural**: convite por Shift + clique, Bia aceita no **próprio chat** | `combo ... (Bia): SUCCESS (killed)`; tela de morte da Bia "Bia was executed by Juca"; `playerKillCount` Juca = 1, `deathCount` Bia = 1; contador 🍆💦 = 1, plaqueta "1" vista pelo Juca depois do renascimento. Os dois clientes mostram o mesmo quadro da cena (câmera cinematográfica nos dois, anel do Calamidade) |
| 3 | **Totem** na mão secundária da Bia (`takedown`, admin) | `FAILED (death was prevented)`; totem consumido (inventário vazio), conquista *Postmortal*; Bia caída (`knocked_down`) e **presa** 10 s (andou 0,24 bloco segurando W), `Recovered.` e volta a andar; contador = 2 (regra antiga mantida) |
| 4 | Times sem fogo amigo **ligados no meio da cena** | `FAILED (PvP is not allowed)`; Bia 20/20, `You survived the execution (PvP is not allowed)`, recuperação caída; `/stopitstopnow` no chat dela solta (andou 3,3 blocos) |
| 5 | Bia aperta **Shift** no cliente durante o combo | `CANCELLED (cancelled with Shift)`; câmera dos dois de volta à primeira pessoa; Bia 20/20 |
| 6 | **Executor sai** (janela fechada) no meio do combo | `CANCELLED (disconnect)`; Bia livre (andou 4,2 blocos), câmera dela restaurada; Juca volta sem estado preso e vê a plaqueta "3" da Bia |
| 7 | **Vítima sai** no meio do combo | `CANCELLED (disconnect)`; Juca em primeira pessoa; Bia volta limpa, 20/20 |
| 8 | Bia **troca de dimensão** (`/respectcraft realm enter Bia`) no meio do combo | `CANCELLED (participant changed dimension)`; Bia no Inverted Realm com a câmera normal; `realm leave` devolve |
| 9 | **Mob × jogador** pelo caminho natural (`mobs on` + `mobtest` no chat da Bia) | `takedown by minecraft:zombie on minecraft:player (Bia): SUCCESS (killed)`, "Bia was executed by Zed"; Juca, de lado, vê a animação da Bia sincronizada com a **própria** câmera (só participantes têm câmera de cena) |
| 10 | Mob × jogador com a IA do zumbi ligada no meio da cena (`NoAI:0b`) | Cena não cancelada, `combo ... SUCCESS (killed)` (exceção da 4.8: o golpe da IA do próprio parceiro é anulado sem cancelar). Não há prova separada de que o zumbi chegou a bater |
| 11 | **Terceiro jogador** (Juca) bate no zumbi durante zumbi × Bia | `CANCELLED (interrupted by damage)`; Bia 20/20, zumbi 20/20 (o golpe é descartado) |
| 12 | Juca começa a ver no **meio** da cena (estava a 600 blocos, teleportado de volta) | Vê a Bia no meio do combo (sincronização por `StartTracking`); `SUCCESS (killed)` no fim; contador da Bia = 6, do Juca = 0 |
| 13b | Servidor reiniciado com **`pvp=false`** | Contador 6 persistido (plaqueta "6"); Shift + clique de mão vazia e `admin finisher` recusados com a mensagem de PvP |
| 14 | Convite recusado (`deny` no chat da Bia) | `Invite declined.` nos dois; `accept` depois = `There is no valid invite.`; nenhuma cena |
| 15 | Convite expirado (16 s) | `accept` = `There is no valid invite.` |
| 16 | Executor sai **antes** do aceite | `accept` = `There is no valid invite.` |
| 17 | Vítima sai durante a recuperação caída (totem) | Volta livre (andou 4,2 blocos). Obs.: a volta leva ~45 s, mais que os 10 s da recuperação, então o teste não separa "limpo no logout" de "expirou" |
| 18 | **Servidor parado (`/stop`) no meio do combo**, depois religado | Antes da correção: **nenhuma** linha `CANCELLED` nem `Aborted` (defeito, seção 4). Depois de religar: os dois entram livres (Bia 20/20), câmera normal, e uma cena nova termina em `SUCCESS (killed)` |
| — | Singleplayer (servidor integrado): "Save and Quit to Title" no meio de uma cena | `CANCELLED (disconnect)` antes de "Stopping server": no singleplayer o logout acontece normalmente (a hipótese de sessão presa ao reabrir o mundo **não** se confirmou) |

Erros nos logs: só os do ambiente (sem rede para os serviços da Mojang, sem placa de som); nenhum
`NoSuchMethodError`/`NoClassDefFoundError`/exceção do RespectCraft ou do Calamidade nos dois clientes nem no servidor.

Falsos alarmes investigados (não são defeitos): o nome da plaqueta 🍆💦 some atrás da faixa preta superior da câmera
cinematográfica quando o enquadramento põe o nick no alto da tela; os desenhos rosa na tela da Bia durante a
recuperação do passo 3/4 são partículas da Regeneração II dada pelo totem.

## 4. Defeito encontrado e corrigido

**Servidor parando não encerrava as cenas ativas.** Reproduzido no passo 18 e no roteiro
`cenario_parada_do_servidor.sh`: no `/stop`, `PlayerList.removeAll` desconecta os jogadores **sem** o
`PlayerLoggedOutEvent`, então `FinisherTestMechanic.onLogout` não rodava. A sessão não terminava: nenhum `CANCELLED` no
log, nenhum `FinisherSessionEvent.Aborted` (quebra o contrato "um evento de fim por sessão" da 4.8), nenhum
`FinisherFx.end`, e o estado estático (sessões, convites, recuperações, cooldowns medidos no tempo de jogo daquele mundo)
continuava no processo — no servidor integrado (singleplayer/LAN, o mesmo processo abre outro mundo depois) ele passava
para o servidor seguinte.

Correção mínima (`FinisherTestMechanic.onServerStopping`, registrado em `RespectCraft`): no `ServerStoppingEvent`
(antes de salvar e desconectar), toda sessão ativa passa pelo `abort` com o motivo `server stopping` — um `Aborted` por
sessão, efeitos e animações encerrados, **nunca** executa — e convites, recuperações (com o `knocked_down` parado) e
cooldowns são limpos. Nada mais mudou.

Validação (dois clientes, duas cenas ao mesmo tempo: Juca × zumbi e zumbi × Bia, `/stop` no meio):

```
Finisher respectcraft:combo by minecraft:player on minecraft:zombie: CANCELLED (server stopping)
Finisher respectcraft:combo by minecraft:zombie on minecraft:player: CANCELLED (server stopping)
Stopping server
```

Os clientes mostram "Connection Lost / Server closed"; religado, os dois entram livres em primeira pessoa. A mensagem de
chat "Finisher cancelled: server stopping" não chega a aparecer nos clientes (a desconexão vem em seguida); o log do
servidor registra. (A cena nova depois desse reinício não rodou porque o servidor religado leu `difficulty=peaceful` e
apagou os zumbis; a cena nova depois de reiniciar já tinha sido vista no passo 18.)

## 5. Testes

| Teste | Resultado |
|---|---|
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` | OK (`RespectCraft self-test: OK`) |
| `python3 tools/player_anim/bbsync.py` / `test_export.py` | OK / 0 falhas |
| `python3 tools/fx/check_shaders.py` | Cópia do `calamidade_fx.glsl` conferida; `glslangValidator` ausente na sessão (o CI roda a etapa estrita). Nenhum shader mudou |
| Dois clientes reais (seção 3) | Todos os passos com o resultado esperado; 1 defeito (seção 4) corrigido e revalidado |
| CI do GitHub (workflow Build) | Ver o PR |

Sem `SelfTest` novo: a correção não é regra pura (depende do ciclo de vida do servidor); o teste de fumaça para o
servidor com o ouvinte registrado sem erro, e o roteiro com dois clientes mostra o efeito.

## 6. Limitações e observações (inalteradas)

- As três observações da 4.10.1 (seção 5.1) continuam como estavam, por pedido do dono: o contador 🍆💦 sobe mesmo
  quando o golpe final falha (visto aqui com totem e com PvP desligado no meio da cena: contador 2 e 3); `/kill` durante a
  cena continua suprimido; agora **PvP com dois clientes está validado** (item 3 resolvido).
- Durante a recuperação caída a vítima não é protegida de dano de terceiros (comportamento anterior, não mudado).
- Só llvmpipe (sem GPU real); clientes e servidor na mesma máquina (sem latência real de rede).
- O passo 17 não distingue limpeza no logout de expiração da recuperação (a volta do cliente demora mais que 10 s).
