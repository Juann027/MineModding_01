# Handoff — Etapa 4.8.4-C: criador de personagem e roleta de nascimento como janelas holográficas no mundo

Status: **implementada, testada em cliente real, aguardando o teste pessoal do dono.** Base: `main` em `9807de4`
(merge do PR #39, Etapa 4.8.4-B, aprovado pelo dono com CI verde) **mais a branch do PR #36** (Etapa 4.8.2, roleta
com `requestId`/`ERROR`/timeout), integrada só nesta branch (merge local `3b43807`, conflitos apenas em `CLAUDE.md` e
no índice de handoffs). **O PR #36 não foi integrado à `main`**: este PR o contém; integrar este PR integra o #36.
`mod_version` não mudou; nenhuma release.

Pedido: criador e roleta viram janelas holográficas do mesmo arco físico da 4.8.4-B (nada de `Screen` no modo mundo),
criador só com Body, Hairstyle e Hair Color, fluxo do primeiro personagem Criador → Roleta → Hub no mundo, sem mexer
na identidade visual, na âncora, no arranjo, no movimento, no Recall, na navegação, nas Skill Trees nem na segurança
do clique.

## 1. Rede da roleta e o PR #36

- A janela usa **a rede corrigida do #36** sem reescrevê-la: `BirthRollRequest(reroll, expectedRollCount, requestId)`,
  `BirthRollReply(outcome, rollCount, requestId)`, `Outcome.ERROR`, `ProfileClientCache.nextRollRequestId`, mesmas
  mensagens e mesmas linhas de log (`Birth roulette request #N …`). Nenhum pacote, handler ou estado novo.
- A máquina de estados que o #36 deixou dentro de `BirthRouletteScreen` foi **extraída sem mudar o comportamento** para
  `ui.RouletteMachine` (pura) + `client.roulette.RouletteSession` (cache, pacotes, sons, log). A tela antiga e a janela
  usam as duas; não há segunda implementação.
- Se o dono recusar o #36, este PR precisa ser refeito sem ele (a extração depende do `requestId`).

## 1.1 Causa raiz do erro da roleta (confirmada pelo dono)

Stack trace do dono: `IllegalArgumentException: No implementation of the random number generator algorithm
"L64X128MixRandom" is available` em `ProfileService.roll` (giro → `ERROR` do #36; antes do #36, timeout). O algoritmo
fica no módulo opcional `jdk.random`, que o runtime Java do dono não tem. Correção: `BirthRoulette.newRandom()` =
`new SplittableRandom()` (`java.base`, implementa `RandomGenerator`), usado no giro (`ProfileService.roll`), em
`/respectcraft speciality simulate`, na escolha de finalização (`FinisherTestMechanic`) e no `SelfTest` (que lançaria a
mesma exceção). Chances, Specialities, traços, atributos e novo giro intactos. Testes: runtime com só `java.base`
(jlink) reproduz a exceção antiga e roda o `BirthRoulette.roll` real com o gerador novo (200 000 giros nas chances
aprovadas); servidor dedicado num runtime com todos os módulos **menos** `jdk.random` + cliente real: Spin, erro
injetado no novo giro (ERROR #2, giro intacto), novo giro, Continue, reconectar, `speciality simulate 100000`; zero
ocorrências da exceção. `SelfTest.rouletteRandom`.

## 2. Lógica sem desenho (uma só para as duas cascas)

| Peça | Papel |
|---|---|
| `client/character/AppearanceDraft.java` (novo) | Rascunho do criador: Body, Hairstyle, Hair Color; prévia só local (`setPreview`); `save()` envia `CharacterAppearanceUpdatePayload` e só termina com a sincronização do servidor (`revision` maior e pacote igual), timeout de 200 ticks → `ERROR`; `discard()` limpa a prévia. Pele/rosto sempre "conta". |
| `ui/RouletteMachine.java` (novo, puro) | Fases `IDLE → WAITING → SPINNING → REVEAL → DONE`, giro, desaceleração até a raridade da ficha (`RouletteLayout.restingRotation`), revelação dos 6 atributos, avisos `ERROR/TIMEOUT/NO_REROLLS/STALE` com o número do pedido, resposta atrasada aplicada uma vez. `SelfTest.rouletteMachine`. |
| `client/roulette/RouletteSession.java` (novo) | Liga a máquina a `ProfileClientCache` (só a resposta chegada **depois** do pedido conta), envia `BirthRollRequest`/`BirthConfirmRequest`, toca os sons, grava os logs da 4.8.2, BONER. |
| `CharacterCreationScreen`, `BirthRouletteScreen` | Telas de reserva (modo `SCREEN`) agora só desenham sobre `AppearanceDraft`/`RouletteSession`; o criador antigo também perdeu Skin e Face. |

## 3. Skin e Face removidos

- Criador (janela e tela de reserva) só tem Body, Hairstyle e Hair Color; a seção Character do Hub também deixou de
  listar Skin/Face e passou a dizer "Hair Color".
- Servidor (`CharacterAppearanceService.apply`): grava sempre pele/rosto "conta" (`withAccountSkinAndFace`), mesmo que
  um cliente mande outra coisa.
- Render: `CharacterAppearance.customSkinTone()/customFace()` são sempre falsos → as Body Layers de pele/rosto nunca
  aparecem; o modelo mostra a skin da conta. **Nenhum save é invalidado**: o pacote antigo continua válido (mesmos
  bits), os valores velhos só são ignorados e somem no próximo Save. `SelfTest.creatorOptions`.
- Ficam para a 4.8.5: `SKIN_IDS/FACE_IDS`, as Body Layers `face/*` e `skin/*`, texturas `face_*.png` e as chaves de
  idioma `creator.skin*`/`creator.face*` (sem uso agora).

## 4. Janelas no arco (`client/holo`)

| Peça | Papel |
|---|---|
| `HoloPopup.java` (novo) | Base das janelas: tipo (`CREATOR`/`ROULETTE`), tamanho, cabeçalho (título, "Step N of 2" no primeiro personagem, ✕ que dispensa os painéis), janela de confirmação. |
| `CreatorView.java` (novo) | Palco "Preview" à esquerda (o holograma do jogador sobe nele, arrastar gira, botão Random), seletores [◀ valor ▶] grandes para Body e Hairstyle (clique nas setas ou no valor, **roda sobre o seletor** troca), paleta de Hair Color (clique ou roda), dica, Back e Save. Save → `HoloMenu.creatorSaved`: personagem sem nascimento vai para a roleta, senão volta à seção Character. |
| `RouletteView.java` (novo) | Roda com 6 fatias iguais e as chances do servidor em cada fatia, ponteiro, dica com a tabela de chances, nota "Equal slices"; Speciality com selo da raridade e chance; atributos revelados um a um com barras; traço (dica com a descrição) e pontos; aviso de erro/timeout (texto inteiro na dica); Spin / "Spin again (N)" e Continue; Back. **Novo giro** e **Continue com giro sobrando** pedem segundo clique em até 2,5 s ("Confirm?"), porque a mira pode clicar sem querer andando. |
| `HoloMenu.java` | Placa `popup` (em `all`, desenhada pelas mesmas superfícies e pela mesma passada 3D). `openPopup/closePopup`, `exclusive()` (Display ou janela: só ela responde), `animatePopup` (vem do fundo, fica `POPUP_DZ = 80` unidades à frente das placas, empurra o arco para trás, esmaecido e sem resposta), holograma do jogador no palco do criador e escondido atrás da roleta, faíscas `burstOnPopup`. A janela continua aberta no Recall (remonta na posição nova). |
| `HoloZone`/`HoloPanel` | `HoloZone.scroll` + `HoloPanel.scrollZoneAt`: região com roda própria (seletor do criador). |
| `HoloWorld.java` | `Host.openCreator/openRoulette` abrem a janela (antes: fechavam os painéis e abriam a tela). `toggle()` (K) segue o fluxo `flowPopup()`. `target()/pickRay` respeitam `exclusive()`. Roda: zona com roda → troca o valor; com janela aberta o arco nunca gira. `onScreenOpening` no modo mundo troca **qualquer** abertura de `CharacterCreationScreen`/`BirthRouletteScreen` pela janela. |
| `CharacterCreatorKeybinds` | No modo mundo K sempre chama `HoloWorld.toggle()` (inclusive a primeira vez); as telas antigas só no modo `SCREEN`. |

### Fluxo e regras

| Situação | K (modo mundo) |
|---|---|
| Sem aparência confirmada | Painéis + janela do **criador** ("Step 1 of 2"). Back/✕ fecham os painéis sem salvar (não há Hub sem personagem). |
| Aparência salva, sem nascimento | Painéis + janela da **roleta** ("Step 2 of 2"). Back volta ao Hub (dá para girar depois pelo botão Birth). |
| Nascido (confirmado ou não) | Hub; Appearance/Birth abrem as janelas por cima. |

- Nada é salvo antes de Save; o servidor continua dono da aparência, do sorteio, do novo giro e da confirmação.
- Fechar a janela, dispensar, morrer, trocar de dimensão ou desconectar descarta a prévia; a ficha não muda.
- Clique: a janela é a única que responde; clique nas placas de trás é **consumido** (nada no mundo, nada nelas). A
  segurança da 4.8.4-B continua igual (o holograma é dono quando a janela está mais perto que o alvo do vanilla).

## 5. Testes

Automáticos (código final desta branch):

| Comando | Resultado |
|---|---|
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` | OK — `RespectCraft self-test: OK` (inclui `rouletteMachine`, `creatorOptions` e os casos do #36) |
| `python3 tools/player_anim/bbsync.py` | OK |

**Cliente real** (Xvfb + openbox sem os atalhos de Alt, Mesa llvmpipe, 960 × 540, GUI 2, *Fast*) contra o servidor
dedicado de desenvolvimento (criativo, mundo plano). Entrada real por `xdotool`; mira posta por `/tp` com yaw/pitch
calculados da geometria da janela; conferências pelo servidor (`data get`, `execute if block`). Evidências em
`docs/evidencias/etapa4_8_4c/` (18 folhas, 2 vídeos):

| Pedido | Resultado | Evidência |
|---|---|---|
| Jogador novo: K abre o criador no mundo | Janela "Step 1 of 2", holograma no palco, Body/Hairstyle/Hair Color, Back/Save | `01_…` |
| Só Body, Hairstyle e Hair Color | Janela, tela de reserva e seção Character sem Skin/Face | `01_…`, `14_…`, `16_…` |
| Controles | Seta: Male → Female; roda sobre Hairstyle: Short → Curly; swatch Red; dicas | `02_…` |
| Prévia gira, Random | Arrastar no palco gira o holograma; Random troca o cabelo | `03_…` |
| Save | Espera o servidor e abre a roleta ("Step 2 of 2") | `04_…` |
| Roleta: Spin → resposta → animação → resultado | Gira esperando, desacelera e para na raridade gravada (Scatborn, Epic 10%); atributos, traço, pontos | `05_…`, vídeo `20_…` |
| Novo giro uma vez | Primeiro clique arma; segundo fora da janela de 2,5 s não gasta (`rerolls: 1`); dois cliques seguidos: novo giro (`roll_count 2`, sem giro) | `06_…` |
| Continue | Com giro sobrando pede "Confirm?"; volta ao Hub (Overview) com o resultado; servidor zera o giro | `07_…` |
| Mob atrás da janela | 5 esquerdos + 3 direitos: vida da vaca 10,0 → 10,0; sem painéis: 10,0 → 8,25 | `08_…` |
| Bloco atrás da roleta | 5 cliques: ouro intacto; sem painéis o mesmo clique quebra | `09_…` |
| Erro e timeout (#36) | `roulette_fail`: "Server error (request #1)…" na hora (log `#1` no servidor e no cliente); servidor congelado (`SIGSTOP`): "No answer from the server (request #2)…"; ao retomar, a resposta atrasada é aplicada uma vez (giro não gasto) | `10_…` |
| Andar, pular, olhar, Recall com a janela aberta | Janela parada no mundo; R remonta na frente da mira com a janela | vídeo `19_…`, `11_…` |
| Abrir/fechar repetidas vezes | 3× K/K; Birth/Back, Appearance/✕ | `11_…`, `17_…` |
| Morte, dimensão | Criador aberto + morte: fecha; respawn + K OK. Roleta aberta + Nether: fecha; volta + K OK | `12_…` |
| Desconectar com edição não salva | Body → Male (só prévia), Disconnect pelo menu de pausa; ao voltar: Female (confirmada), ficha igual | `13_…` |
| Persistência | Ficha (Speciality, atributos, traço, sem giro) igual depois de reconectar | `13_…` |
| Hub e Skill Trees | Chip Skills, compra pelo servidor (`combate/punhos_de_aco`) | `14_…` |
| Modo cursor (Alt) | Fluxo inteiro de um jogador novo pelo cursor: Save → Spin → Continue (Confirm?) → Hub | `15_…` |
| Clique no meio do pulo | Body trocou | `17_…` |
| Roda × hotbar | Roda na janela (fora e sobre seletor): slot 0 → 0, Hairstyle Short → Mohawk; sem painéis 0 → 1 | `18_…` |
| Reserva `mode = "SCREEN"` | Hub em tela, criador antigo (sem Skin/Face), Save → roleta antiga, Spin, Continue → Hub; servidor confirma | `16_…` |
| Tela antiga no modo mundo | Nunca apareceu em nenhum fluxo acima | — |

Achados e corrigidos no teste real: janela um pouco distante para ler (passou de 50 para 80 unidades à frente); pés
do holograma cobriam o Random e a dica do palco; nome da cor do cabelo ilegível no vidro (agora texto claro + amostra);
"Step 2 of 2" sumia logo depois do giro (agora fica até Continue); rótulo de confirmação cortado ("Confirm?").

**Não verificado** (honesto): GPU real, *Fancy/Fabulous*, mods de shader; sobrevivência (só criativo, o caminho é o
mesmo); dois clientes ao mesmo tempo; Windows/macOS; controle; F5 com a janela aberta (a prévia aparece no jogador
real em terceira pessoa, só neste cliente); redimensionar a janela; custo por quadro; BONER real na janela (efeito
só visual, coberto pelo mesmo código da tela).

## 6. Limitações conhecidas

- **Empilhado sobre o PR #36.** Integrar este PR integra o #36 (rede `"5"`: cliente e servidor no mesmo build).
- Ao girar, o servidor solta partículas de Totem of Undying no jogador (comportamento antigo de `ProfileService`); com a
  tela antiga elas ficavam escondidas, no mundo aparecem grandes perto da câmera.
- A janela tem tamanho fixo no layout; de longe o texto fica pequeno (chegar perto ou R ajuda).
- Prévia do criador vale para o modelo do próprio jogador inteiro neste cliente (F5 mostra a prévia até fechar).
- Continue/Spin again pedem dois cliques (decisão da 4.8.3, seção 5.1); a compra de skill continua com um clique.
- Telas antigas continuam no código (reserva `SCREEN`); saem na 4.8.5 junto de `RespectScreen`, `CycleSelector`,
  `EntityPreview`, `UiParticles`, as Body Layers de pele/rosto e as chaves de idioma sem uso.

## 7. Arquivos

Novos: `client/character/AppearanceDraft.java`, `client/roulette/RouletteSession.java`, `ui/RouletteMachine.java`,
`client/holo/HoloPopup.java`, `CreatorView.java`, `RouletteView.java`, este handoff, `docs/evidencias/etapa4_8_4c/*`.

Alterados: `character/CharacterAppearance.java`, `CharacterAppearanceService.java`,
`client/character/CharacterCreationScreen.java`, `CharacterCreatorKeybinds.java`,
`client/roulette/BirthRouletteScreen.java`, `client/holo/HoloMenu.java`, `HoloWorld.java`, `HoloPanel.java`,
`HoloZone.java`, `CharacterView.java`, `selftest/SelfTest.java`, `lang/en_us.json` (21 chaves novas),
`CLAUDE.md`, `AGENTS.md`, `README.md`, `docs/handoffs/README.md`. Mais os arquivos do PR #36 (ver o handoff 4.8.2).

Não tocados: visual, âncora, posição, Recall e arranjo das placas; seções do Hub; Skill Trees; entrada da 4.8.4-B;
Specialities, chances, atributos, traços, regras de novo giro e persistência; origens legadas; Hidratação/Energia;
finalizações; mobs; registries.

## 8. Próxima etapa — 4.8.5 (limpeza)

Depois do teste do dono: apagar `CharacterCreationScreen`, `BirthRouletteScreen`, `HoloCharacterScreen` (se o modo
mundo passar na regressão), `RespectScreen`, `RespectButton`, `CycleSelector`, `EntityPreview`, `UiParticles`,
`HoloBackdrop`, as funções da câmera virtual de `HoloGeometry`, o enum `Section` da tela (mover para `HoloMenu`),
`panel.png`/`button.png`/`tab.png`, as Body Layers e texturas de pele/rosto e as chaves de idioma sem uso. Conferir as
referências antes de cada remoção (seção 1.5 da 4.8.3).
