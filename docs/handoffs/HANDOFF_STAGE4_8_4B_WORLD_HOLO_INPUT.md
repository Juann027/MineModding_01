# Handoff — Etapa 4.8.4-B: interação com os painéis holográficos no mundo

Status: **implementada, testada em cliente real, aguardando o teste de jogo do dono.** Base: `main` em `ff4b291`
(merge do PR #38, Etapa 4.8.4-A, aprovado pelo dono com CI verde). O PR #36 (roleta) continua aberto e intocado.
`mod_version` não mudou; nenhuma release.

Escopo pedido: fazer os controles que já existem nas placas no mundo funcionarem com a mira (clique, hover, dicas,
arrasto, roda), sem nada atravessar a placa para o mundo, e com os controles normais do Minecraft intactos fora dela.
Aparência, tamanho, arranjo, animações, âncora, Recall e movimento livre da 4.8.4-A **não mudaram**. Criador e roleta
continuam nas telas antigas (4.8.4-C).

## 1. Arquitetura da entrada

| Peça | Papel |
|---|---|
| `ui/HoloInputArbiter.java` (novo, puro) | Dono de cada fonte de entrada (8 botões do mouse + ataque/uso/pegar bloco presos ao teclado). `hologramWins(acertou, distânciaPlaca, distânciaMundo, alcance)`: a placa ganha só no alcance e **estritamente mais perto** que o alvo do vanilla. `press` decide o dono no aperto, `release` devolve o mesmo dono e libera. No `SelfTest.holoInput`. |
| `client/holo/HoloWorldInput.java` (novo) | Roteador: eventos de entrada do NeoForge → arbiter → `HoloWorld`. Modo cursor. Limpeza (foco, telas, logout). |
| `client/holo/HoloWorld.java` | Mira: `aimRay` grava, a cada passada AFTER_LEVEL, o raio do vanilla (olhos e olhar da **entidade da câmera** no `partialTick` da câmera, o mesmo instante do `hitResult`) no espaço do olho do quadro, e a distância do `hitResult`. `ownsAim` (dono da mira), `target` (painel interativo + ponto local), `press`/`release`/`cancelDrag`/`updateDrag` (arrasto capturado segue a mira no plano do painel, sem recorte), `scroll`, dica numa camada de GUI. |
| `client/holo/HoloMenu.java` | `press(painel, x, y)`: caminho de clique **comum** à tela e ao mundo (placa lateral vem para a frente; região de arrasto é capturada; região de clique roda a ação com som e faíscas). `pickRay(o, d, alcance, interativo, out)`: `interativo = false` = qualquer placa visível (dona do clique, para nada atravessar), `true` = só as que respondem (Display aberto captura tudo). `sidePlateTooltip()`. `world` (dicas sem as teclas da tela). |
| `client/holo/HoloPanel.java` | `rayHit(o, d)` só pela **face da frente** (origem do lado +z local, o mesmo lado que a 4.8.4-A não esmaece); `rayPlane` sem recorte para o arrasto. |
| `client/holo/CharacterView.java` | Zona de arrasto invisível no palco: arrastar gira o holograma do jogador (o texto "Drag to rotate" já existia; no mundo não há a caixa de tela da 4.8.1). |
| `client/holo/HoloCharacterScreen.java` | Usa `HoloMenu.press` e `sidePlateTooltip` (mesmo comportamento). |
| `client/character/CharacterCreatorKeybinds.java` | Tecla nova `key.respectcraft.holo_cursor` (Alt esquerdo, segurar). |
| `client/holo/HoloClient.java` | Registra os eventos e a camada de GUI `respectcraft:holo_tooltip` (acima da mira). |

Fluxo de um clique: GLFW → `MouseHandler.onPress` → `InputEvent.MouseButton.Pre` → `HoloWorldInput.onMouseButton`:
botão de ataque/uso/pegar bloco com os painéis aceitando entrada e `ownsAim()` (ou modo cursor) → arbiter `HOLO` →
**evento cancelado** (o vanilla não prende o mouse, não toca no `KeyMapping`) → `HoloWorld.press` → `HoloMenu.press`.
O soltar do mesmo botão é do mesmo dono (cancelado e entregue ao arrasto, se houver). Ataque e uso (esquerdo e direito)
ativam; pegar bloco (meio) só é consumido.

Por que o clique bate com a placa: desenho, hover, clique, arrasto e roda usam as matrizes congeladas do último
quadro desenhado (`HoloPanel.inverseModelView`/`inverse`), e o raio é o do vanilla no mesmo quadro. Nada é
recalculado pela câmera entre o desenho e o clique; o hover mostra exatamente o que o clique pega (mesmo `target`).

## 2. Ganchos conferidos no NeoForge 21.1.265

Lidos nas fontes do jar (`build/moddev/artifacts/neoforge-21.1.265-sources.jar`), não da memória:

| Gancho | Onde dispara | Uso |
|---|---|---|
| `InputEvent.MouseButton.Pre` (cancelável) | `MouseHandler.onPress`, **antes** do grab do mouse, da tela e de `KeyMapping.set/click`; `activeButton` já foi atualizado antes (cancelar não o prende) | Dono no aperto; cancelado = sem ataque, mineração (`continueAttack` exige `keyAttack.isDown()`), uso, pegar bloco e balanço |
| `InputEvent.MouseScrollingEvent` (cancelável) | `MouseHandler.onScroll` sem tela, depois de acumular um passo inteiro, **antes** de `Inventory.swapPaint` | Cancelado só com a mira numa placa (ou no modo cursor) |
| `InputEvent.InteractionKeyMappingTriggered` (cancelável, `setSwingHand`) | `startAttack`, `continueAttack` (mineração segurada, só sobre bloco), `startUseItem`, `pickBlock` | Rede de segurança (dono holograma = cancela sem balanço); dono para ações presas ao teclado; repetição de um aperto do mundo não age atrás de uma placa |
| `ClientTickEvent.Post`, `RenderFrameEvent.Pre` | — | `poll`: soltar perdido (estado do GLFW), teclas do teclado soltas, modo cursor |
| `ScreenEvent.Opening` | — | Outra tela: solta arrasto e modo cursor |
| `MouseHandler.grabMouse/releaseMouse` | `grabMouse` centraliza o cursor, `missTime = 10000`, `ignoreFirstMove = true`, `KeyMapping.setAll()` (só teclado) | Modo cursor sem salto de câmera ao voltar |

## 3. Regras de entrada

- **Dono no aperto, até soltar.** Aperto na placa: nada vai ao mundo, mesmo se a mira sair da placa no meio (arrasto
  continua no plano da placa capturada). Aperto fora: é do mundo até soltar e nunca vira clique de placa; mas a
  repetição segurada (mineração contínua, uso repetido) **não age atrás de uma placa** que entrou na frente da mira e
  volta sozinha quando a mira sai dela (achado no teste: sem isso a mineração segurada quebrava o bloco atrás da placa).
- **Quem ganha a mira:** face da frente de uma placa visível (alfa > 5%, inclusive montando ou dissolvendo), no alcance
  de 6 blocos e **mais perto** que o `hitResult`. Bloco ou mob na frente da placa → mundo. Placa escondida atrás de
  parede → mundo. Por trás (verso esmaecido) → mundo.
- **Roda:** consumida só com a mira numa placa: sobre a placa da frente rola o conteúdo; nas outras (ou sem o que
  rolar) gira o arco. Fora das placas a hotbar troca normalmente. Teclas 1–9 nunca são capturadas.
- **Display aberto:** captura tudo; clique em outra placa o fecha (como na tela).
- **Botão do meio:** consumido sobre a placa (não pega bloco), sem ação no menu.
- **Clique que devolve o mouse ao jogo** (mouse solto sem tela, ex.: depois de perder o foco): com a mira numa placa
  é só foco (prende o mouse, nada no menu ou no mundo); fora dela segue o vanilla.

## 4. Modo cursor (opcional)

Tecla `key.respectcraft.holo_cursor`, **Alt esquerdo**, segurar (sem uso no vanilla 1.21.1; configurável em
Controles → RespectCraft). Só com os painéis abertos, aceitando entrada e a janela em foco. Solta o mouse
(`releaseMouse`, cursor no centro, onde estava a mira): a câmera para, WASD/pulo continuam; o raio vem do cursor
(desprojeção pela inversa de `projeção · visão · layout · placa` do quadro). Enquanto ativo, todo clique e roda são do
holograma (nada no mundo). Soltar a tecla prende o mouse de novo (`grabMouse`, primeiro movimento ignorado: sem salto).
Estado físico da tecla conferido a cada quadro (Alt+Tab não deixa o modo preso); se o modo acabar sem foco, o mouse é
preso de novo quando a janela voltar. Abrir uma tela, dispensar/fechar os painéis ou começar a dissolução encerra o
modo. A mira normal continua sendo o padrão; o modo cursor nunca é necessário para os botões.

## 5. Dicas

Camada de GUI `respectcraft:holo_tooltip` acima da mira: a mesma dica que a tela mostrava (`HoloCanvas.tooltip`,
placa lateral = "Click to bring this plate forward"), **acima e à direita da mira** (não cobre os chips logo abaixo;
vira para o outro lado se não couber), quebrada em até 220 px de GUI. No modo cursor, ao lado do cursor. Só com a placa
assentada (conteúdo > 60%), sem arrasto e sem tela aberta. Duas dicas ganharam versão do mundo (as da tela citavam
teclas que lá não valem): `gui.respectcraft.holo.key_hint_world` (chips) e `...settings.hint_world` (Display).

## 6. Ciclo de vida

Estado de entrada limpo (arrasto solto **sem** clique, modo cursor encerrado, mouse preso de novo quando cabe) ao:
dispensar com K ou ✕, Recall, morte, respawn, troca de dimensão/nível, logout, abrir qualquer tela (pausa, chat,
inventário, criador, roleta), câmera cinematográfica/F1 (esconde), janela sem foco (soltar perdido detectado pelo
estado do GLFW). Botão que o holograma pegou continua dele até subir, mesmo com os painéis já fechados (o soltar nunca
chega ao vanilla sem o aperto). Reserva `mode = "SCREEN"` intocada no comportamento (usa o mesmo `HoloMenu.press`).

## 7. Testes

Automáticos (rodados nesta sessão, código final):

| Comando | Resultado |
|---|---|
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` | OK — `RespectCraft self-test: OK` (inclui `holoInput`); servidor dedicado sem classes de cliente |

`SelfTest.holoInput` (novo): placa sem nada atrás ganha; placa na frente do bloco ganha; mob/bloco na frente = mundo;
fora do alcance = mundo; aperto na placa segue dela até soltar; aperto no mundo segue do mundo; fontes independentes;
fonte inválida; logout limpa; raio dos olhos fora da câmera → mesmo ponto local; olho atrás da placa detectado;
arrasto fora do retângulo continua no plano.

**Cliente real** (Xvfb + openbox, Mesa llvmpipe, 960 × 540, gráficos *Fast*, GUI 2) contra o servidor dedicado de
desenvolvimento, jogador `Dev` novo (K → criador → Save → roleta → Spin → Continue → painéis no mundo), **criativo**
(clique esquerdo quebra bloco na hora e mata/fere mob: o teste mais duro). Toda entrada é real (eventos X11 do
`xdotool`: clique, segurar, roda, movimento relativo do mouse, teclas); a mira foi posta por `/tp` com yaw/pitch
exatos ou pelo próprio mouse. Conferências pelo servidor (`execute if block(s)`, `data get`). Evidências em
`docs/evidencias/etapa4_8_4b/`:

| # | Pedido | Resultado | Evidência |
|---|---|---|---|
| 1–3 | K, mirar num chip, hover | Chip acende, ponto de mira na placa, dica acima da mira | `01_…jpg` |
| 4–5 | Clique abre a seção certa; Overview → Character → Body & Traits → Speciality → Skills → Overview | Todas pela mira; placa lateral clicada vem para a frente | `01_…`, `02_…`, `03_…` |
| 6 | Bloco atrás da placa (pedra a 3,5 blocos, placa a 2,0) | 5 cliques: pedra intacta. Controle sem painéis: o mesmo clique quebra | `04_…jpg` |
| 7 | Mob atrás (vaca NoAI a 2,55) | 5 esquerdos + 3 direitos: vida 10,0 → 10,0. Controle sem painéis: 10,0 → 8,75 | `04_…`, `06_…`, vídeo `20_…` |
| — | Balanço da mão | Quadros do vídeo: mão parada nos 6 cliques na placa; balanço só nos cliques no mundo depois do K | `06_…jpg`, `20_…mp4` |
| 8 | Segurar ataque na placa e virar 75° (mouse real) até um muro de ouro, 3 s | 24/24 blocos intactos; clique normal no muro depois minera | `05_…jpg`, vídeo `21_…` |
| 9 | Fora da placa | Clique minera, ataque fere, direito coloca tábua, mineração segurada quebra vários | `04_…`, `05_…`, `18_…` |
| 10 | Roda sobre a placa / fora | Sobre: arco gira, slot 0 → 0 (3 giros). Fora: 0 → 1 → 2 → 0 | `03_…jpg` |
| — | Direito e meio com tábuas na mão e pedra atrás | Nenhuma tábua colocada, 64 itens, item/slot iguais; sem painéis o direito coloca | — (log do servidor) |
| 11 | Andar e pular | Clique na placa lateral andando de lado (Speciality vem) e no meio do pulo (Body vem) | `07_…jpg`, vídeo `23_…` |
| — | Ângulos | Placas laterais giradas 31°; de lado (1,8 bloco à direita, 0,8 à frente) hover e clique no chip certo | `11_…jpg` |
| — | Arrasto | Árvore de Skills arrastada; nó clicado compra pelo servidor (*Skill unlocked: Iron Fists*, 2 → 1 ponto); Display: slider de opacidade 90% → 75% (gravado na config), toggle High contrast liga/desliga, Done; palco gira o holograma | `08_…`, `09_…`, `16_…` |
| — | Fita | Appearance abre o criador antigo (Esc volta aos painéis); Birth abre a roleta antiga; ✕ dispensa | `10_…jpg` |
| 12 | Recall, fechar, reabrir, desconectar | R com o botão segurado na placa e K com o botão segurado: soltar sobre o muro não minera; depois clique e andar normais. Esc com botão segurado → pausa → Back to Game: clique funciona; Disconnect com painéis abertos e botão segurado: sem exceção; reentrar e K de novo OK | `14_…jpg` |
| — | Morte e Nether segurando o botão | Fecham; respawn + K OK; mineração normal depois | `13_…jpg` |
| — | Foco perdido (xterm rouba o foco) | Botão segurado na placa e solto fora da janela: nada minerado; clique depois navega. Modo cursor com Alt solto fora da janela: mouse-look volta sozinho | `17_…jpg` |
| — | Modo cursor | Alt segurado: dica segue o cursor, clique no chip Skills navega, câmera parada (rotação 0,0), S anda; soltar Alt: mouse-look volta sem salto (4 × 30 px → 13,5° = 18° − 1º movimento ignorado) | `12_…jpg`, vídeo `22_…` |
| 13 | Reserva `mode = "SCREEN"` (troca ao vivo da config) | Tela abre, hover/dica, roda rola a placa (slot igual), clique no chip Skills, Esc fecha; volta ao modo mundo | `15_…jpg` |
| — | Aperto no mundo cruzando a placa | Antes da correção: mineração segurada quebrou a pedra atrás da placa. Depois: pedra intacta, muro sem placa continua sendo minerado | `18_…jpg`, vídeo `24_…` |

Achados e corrigidos no teste real:
1. Dica abaixo da mira cobria os chips vizinhos → passou para cima/direita.
2. Dicas citavam teclas da tela (←/→, Tab, 1–5, arrastar com o direito, R recentraliza) → versões do mundo.
3. Modo cursor + perder o foco: o soltar do Alt chegava sem foco, o mouse ficava solto e o clique para voltar caía na
   placa (câmera presa) → mouse preso de novo ao voltar o foco e clique de recaptura vira só foco.
4. Mineração segurada de um aperto do mundo atravessava a placa → repetição não age atrás de placa.

A maior parte da tabela rodou em builds anteriores às correções 3 e 4 (que só mexem no foco perdido e na repetição de
um aperto do mundo). A regressão no build final (mob atrás com esquerdo e direito, bloco atrás, direito sem colocar
tábua, roda, segurar e virar, aperto do mundo cruzando a placa, navegação por chip, mineração normal) passou de novo;
o foco perdido foi refeito no build da correção 3.

**Não verificado** (honesto): GPU de verdade, gráficos *Fancy*/*Fabulous*, mods de shader; dois clientes ao mesmo
tempo; sobrevivência (só criativo, que é mais duro para quebrar/atacar; o caminho do código é o mesmo); ataque/uso
religados a teclas do teclado (caminho pelo `InteractionKeyMappingTriggered`, coberto só pelo `SelfTest` das regras);
macOS (Ctrl+clique = direito no `MouseHandler`) e Windows; controle (gamepad); F5 com os painéis abertos (a mira é a do
vanilla, que vale na terceira pessoa, mas não foi rodado); redimensionar a janela; custo por quadro.

Ambiente: o openbox do Xvfb liga **Alt+botão esquerdo** a "mover janela" e engole o clique; o teste do modo cursor
rodou com o openbox sem esses atalhos (o mod não muda nada disso).

## 8. Limitações conhecidas

- **Alt+clique em Linux:** gerenciadores de janela com "Alt + arrastar move a janela" (Openbox, XFCE e outros) engolem
  o clique do modo cursor. A tecla é configurável (Controles → RespectCraft → *Holographic Cursor Mode*).
- Aperto do mundo segurado: enquanto a mira cruza uma placa a mineração/uso param (não atravessam), e o progresso de
  quebra do bloco anterior não é zerado pelo cliente (como qualquer cancelamento do evento no NeoForge).
- O direito também ativa os controles (como botões e alavancas do vanilla); sobre a placa não dá para comer/usar item.
- Modo cursor: no clique só vale a placa sob o cursor; uma placa escondida atrás de parede ainda pode ser apontada
  (sem comparação com o alvo do vanilla nesse modo).
- `Menu size` do Display não muda nada no mundo (lá vale `worldDistance`); a dica do Display no mundo diz isso.
- Teclas ←/→, Tab, 1–5 e `[`/`]` da tela não existem no mundo (decisão 0.5 da 4.8.3: 1–9 continuam da hotbar).
- Esc na roleta antiga volta ao jogo sem painéis (comportamento existente da roleta).
- Criador e roleta continuam em tela (4.8.4-C); a primeira tecla K sem aparência abre o criador antigo.

## 9. Arquivos

Novos: `client/holo/HoloWorldInput.java`, `ui/HoloInputArbiter.java`, este handoff, `docs/evidencias/etapa4_8_4b/*`.

Alterados: `client/holo/HoloWorld.java`, `HoloMenu.java`, `HoloPanel.java`, `HoloCharacterScreen.java`,
`CharacterView.java`, `HoloNav.java`, `HoloSettings.java`, `HoloClient.java`,
`client/character/CharacterCreatorKeybinds.java`, `selftest/SelfTest.java`, `lang/en_us.json` (3 chaves),
`CLAUDE.md`, `AGENTS.md`, `README.md`, `docs/handoffs/README.md`.

Não tocados: aparência e arranjo das placas, âncora/posição/Recall, conteúdo das seções, criador, roleta e sua rede,
ficha, traços, Specialities e chances, atributos, necessidades, Skill Trees (a compra é o pedido que já existia),
finalizações, mobs, chefes, registries, assets visuais. Nenhum pacote de rede novo.

## 10. Próxima etapa — 4.8.4-C (criador e roleta no mundo)

Pré-requisito (decisão 0.1 da 4.8.3): integrar o PR #36 (roleta com respostas estruturadas) antes. Depois, seguir as
seções 6.1 e 6.2 da 4.8.3:

1. `client.character.AppearanceDraft` (estado do criador sem desenho) e `CreatorView` (`HoloView`), placa do criador
   + holograma no pedestal como prévia ao vivo. Seletores: setas grandes e **roda sobre o seletor** troca o valor
   (`HoloWorld.scroll` hoje gira o arco: dar prioridade à zona sob a mira; uma `HoloZone` com rolagem própria resolve).
2. Núcleo puro da roleta (`ui.RouletteMachine`, versão pós-#36) + `RouletteSession` + `RouletteView` com Spin / Spin
   again / Continue como botões largos; confirmação de segundo clique em até ~2 s para o que gasta algo (novo giro,
   Continue, compra de skill — hoje a compra é clique simples, como na 4.8.1).
3. Fluxo único por modo (CREATE → BIRTH → HUB) dentro do `HoloWorld`, sem `setScreen`; `Host.openCreator/openRoulette`
   trocam a placa da frente em vez de fechar os painéis.
4. Entrada: tudo pelo `HoloMenu.press`/`HoloZone` existentes; o modo cursor é o recomendado para o criador.
5. Testes: os da seção 7 desta etapa continuam valendo como regressão (bloco/mob atrás, segurar e virar, roda × hotbar,
   foco, morte, desconexão), mais criar personagem e girar a roleta inteiros no mundo.

Na 4.8.5 (limpeza, depois do teste de regressão do modo mundo): sair `HoloCharacterScreen`, `HoloBackdrop`, as funções
da câmera virtual de `HoloGeometry` e o enum `Section` da tela.
