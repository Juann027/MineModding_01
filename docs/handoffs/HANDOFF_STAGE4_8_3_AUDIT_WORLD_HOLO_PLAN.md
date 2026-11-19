# Handoff — Etapa 4.8.3: auditoria e arquitetura do menu holográfico físico (no mundo)

Status: **auditoria e plano, nada implementado.** Nenhum código foi alterado, nada foi apagado, nenhum PR foi integrado.
A Etapa 4.8.4 implementa o que está aqui depois da aprovação do dono. O PR #36 (Etapa 4.8.2, diagnóstico da
roleta) continua aberto e intocado; o plano abaixo depende dele (seção 6, passo 0).

Base auditada: `main` em `f14f8af` (merge do PR #35, Etapa 4.8.1).

## 1. Achados da auditoria

Classificação: **KEEP** (fica como está), **REUSE** (entra no sistema novo quase sem mudança), **REFACTOR** (fica,
mas precisa ser dividido/adaptado), **REMOVE LATER** (pode sair depois que o substituto existir e for validado),
**UNCERTAIN** (precisa de decisão do dono ou de conferência no jar).

### 1.1 Menu holográfico (`client.holo`, `ui.HoloGeometry`)

| Arquivo | Achado | Classe |
|---|---|---|
| `client/holo/HoloCharacterScreen.java` (1154 linhas) | É um `Screen`: enquanto aberto o jogo não processa WASD/pulo/mouse-look (causa raiz do "parece uma tela"). Mistura 8 responsabilidades: ciclo de vida, layout em arco (`onArc`, `animate`), câmera virtual (`updateCamera`, `offAxis`), picking (`pick`, `pickHot`), entrada (`mouseClicked`…`keyPressed`), decoração 3D (`backDecor`, `lightTrails`, `assemblyBeams`, `invocationGlow`), holograma do jogador (`drawPlayer`, `pedestal*`) e partículas ambientes. A composição em "unidades de layout" em volta de um olho (`eyeZ`, `centerX`) é aproveitável inteira no mundo (seção 4.2). | REFACTOR (dividir; a casca `Screen` sai) |
| `HoloCharacterScreen.java:220,222` | `openCreator()`/`openRoulette()` fazem `minecraft.setScreen(new CharacterCreationScreen/BirthRouletteScreen())` — os dois pontos que tiram o jogador do holograma. Chamados por `HoloNav.java:41,43`, `CharacterView.java:83`, `OverviewView.java:75`, `SpecialityView.java:83`. | REFACTOR (viram troca de modo/painel) |
| `HoloCharacterScreen.java:71` | `FIT_CACHE` estático (cache visual) não é limpo no logout; `SkillsView.java:32` `lastBranch` idem. Inofensivo, mas fora da regra "estáticos limpos no logout". | REFACTOR (limpar no `LoggingOut`) |
| `HoloCharacterScreen.java:1045`, `ui/HoloGeometry.java:79-80` | `new Vector3f`/`new Vector4f` por evento de arrasto e por teste de raio (≈ 24 por quadro). Pequeno, mas no mundo o picking roda a cada quadro sem tela. | REFACTOR (scratch reutilizável) |
| `client/holo/Holo3D.java` | Primitivas boas e reutilizáveis (`texturedPanel`, `segment`, `arc`, `beam`, `outline`, `glowAt`, `entity`), mas `begin/end` são específicos da GUI: trocam a projeção, **limpam o depth buffer** (linhas 56 e 84) e terminam com `Lighting.setupFor3DItems()`. No mundo isso apagaria a profundidade do cenário. | REFACTOR (separar lote de primitivas × estado GUI/mundo) |
| `Holo3D.java:206` `ring(...)` | Sem nenhuma chamada no projeto (o `vfx.ring` da roleta é `UiParticles`). | REMOVE LATER |
| `client/holo/HoloSurface.java:51-54` `ready()`, `pixelWidth()`, `pixelHeight()` | Sem chamadas. | REMOVE LATER (trivial) |
| `HoloSurface` (resto) | Framebuffer reaproveitado por painel, recorte, alfa pré-multiplicado, liberação em `close()`. Serve para o mundo como está; falta só mipmap (texto a 3+ blocos) e o lugar certo de redesenhar (fora da passada do mundo). | REUSE |
| `HoloPanel`, `HoloZone`, `HoloCanvas`, `HoloView` + `OverviewView`, `CharacterView`, `BodyView`, `SpecialityView`, `SkillsView`, `HoloNav`, `HoloSettings`, `HoloAnim`, `HoloStyle`, `HoloParticles`, `HoloConfig` | Conteúdo desenhado em coordenadas locais do painel, regiões registradas no desenho, clique por coordenada local. Nada disso depende de o painel estar na tela: é exatamente o que o mundo precisa. `HoloPanel.freeze` precisa de uma variante com matrizes do mundo. | REUSE |
| `ui/HoloGeometry.java` | Puro e testado no `SelfTest` (`holoGeometry`). `panel`, `intersect`, `hit`, `project` servem; `referenceDistance`, `offAxis`, `layoutToMenu`, `ndcToGui*`/`guiToNdc*` só existem para a câmera virtual. | KEEP + REMOVE LATER (funções da câmera virtual, quando o modo tela sair) |
| `client/holo/HoloBackdrop.java` + `shaders/fx/holo_backdrop.fsh` | Dessatura e escurece o **mundo inteiro** (pós-processamento do Rimulib). Com o jogador andando e lutando isso atrapalha, e como roda depois da passada do mundo tingiria também os painéis desenhados no mundo. | UNCERTAIN (desligado no modo mundo; decidir se fica para o modo tela ou sai) |
| `client/holo/HoloClient.java` | Registro da config, estágio do Rimulib e limpeza no logout. | REUSE (ganha os eventos do modo mundo) |

### 1.2 Criador, roleta e o framework de telas antigo

| Arquivo | Achado | Classe |
|---|---|---|
| `client/character/CharacterCreationScreen.java` | Lógica útil e sem dono próprio misturada com widgets: rascunho da aparência (5 índices), `randomize`, `preview()` → `CharacterAppearanceClientCache.setPreview`, `saveCharacter()` → `CharacterAppearanceUpdatePayload` e espera da confirmação por `revision` com timeout de 200 ticks (`tick`, linhas 286-313). `onClose` (321-330) com aparência criada sempre reabre o holograma: Esc nunca volta ao jogo. Depois de salvar abre `BirthRouletteScreen` ou o holograma (`303-304`). | REFACTOR (extrair `AppearanceDraft`) → REMOVE LATER (a tela) |
| `client/roulette/BirthRouletteScreen.java` | A máquina de estados da roleta (`IDLE/WAITING/SPINNING/REVEAL/DONE`, pedido com `rollCount`, resposta, timeout, `settleOn`, `restingAngle`, BONER) está dentro da tela, junto do desenho 2D da roda (`drawWheel`, `sector`), dos botões e dos VFX. `confirm()` (146-151) abre o holograma por `setScreen`. **O PR #36 reescreve justamente o bloco `WAITING` (requestId, `ERROR`, timeout separado).** | REFACTOR (extrair `RouletteSession` **depois** do #36) → REMOVE LATER (a tela) |
| `client/character/CharacterCreatorKeybinds.java:33-36` | K abre `CharacterCreationScreen` (sem aparência) ou o holograma; só com `mc.screen == null`; respeita `MenuApi` do Rimulib. | REFACTOR (vira alternar o holograma no mundo) |
| `client/ui/RespectScreen`, `RespectButton`, `CycleSelector`, `EntityPreview`, `UiParticles` | Usados **só** por `CharacterCreationScreen` e `BirthRouletteScreen` (e `RespectScreen` como base vazia de `HoloCharacterScreen`, que herda um `UiParticles vfx` e `layoutPanel` sem usar). Duplicam o que `HoloCanvas.button`, `HoloParticles` e `Holo3D.entity` já fazem. | REMOVE LATER (junto das duas telas) |
| `client/ui/Ui.java` | `fit`, `wrap`, `alpha`, `lerpColor`, `GLOW`, sons: usados pelo holograma e pelo painel do inventário. `TAB` (linha 16) não tem nenhum uso; `PANEL`/`BUTTON`/`titledPanel`/`nineSlice` só servem às telas antigas. | KEEP; `TAB` REMOVE LATER |
| `textures/gui/ui/tab.png` | Só referenciado pela constante `Ui.TAB` sem uso. | REMOVE LATER |
| `textures/gui/ui/panel.png`, `button.png` | Só telas antigas. | REMOVE LATER (com elas) |
| `textures/gui/skilltree/bg_*.png`, `node.png`, `textures/item/skilltree/*` | Usados (`SkillBranch.background()` → `SkillsView:158`, `SkillsView:29`, `SkillDef.iconTexture`). | KEEP |

### 1.3 HUD, rede, caches, idioma, legado

| Arquivo | Achado | Classe |
|---|---|---|
| `client/hud/BodyStatusInventoryPanel.java` | Painel do inventário (regra do CLAUDE.md). Repete a formatação de traço/necessidade que `BodyView.traitTooltip/needTooltip` já tem (o mesmo traço aparece formatado em 4 lugares: `BodyView`, `OverviewView`, `BirthRouletteScreen.drawTraits`, este painel). | KEEP (unificar os textos é opcional, baixa prioridade) |
| `client/hud/HudMeters.java`, `HudStatusClient.java` | Record com 6 campos (inclui Hidratação/Energia inativas) para o orbe ler só `emotion()`. | REMOVE LATER (o orbe pode ler `ProfileClientCache.needs()` direto); inofensivo |
| `network/ProfileClientCache.java`, `CharacterAppearanceClientCache.java`, `GameplayNetworking.java` | Caches só de exibição, `revision()`/`rollReplyRevision()` para detectar respostas, limpos no logout. Servem ao modo mundo sem mudança; nenhum pacote novo é necessário. | KEEP (não mexer: o #36 altera `GameplayNetworking`/`ProfileClientCache`) |
| `lang/en_us.json` | Sem uso: `key.respectcraft.skill_tree` (não existe tecla), `gui.respectcraft.bladder`, `gui.respectcraft.bowel`, `gui.respectcraft.emotion`. As demais chaves "sem referência literal" são montadas por id (skills, traços, raridades, `.desc` do Display). | REMOVE LATER |
| Namespace antigo | Nenhuma referência ativa. Só `familia_politica` em `profile/LegacyOriginIds.java:13` e no `SelfTest` (alias exigido para fichas antigas) e a nota de tema no `AGENTS.md`. | KEEP |
| `RespectCraftClient.java` | Um único handler de `LoggingOut` limpa as caches; o holograma limpa o próprio fundo em `HoloClient`. Sem handlers redundantes. | KEEP |

### 1.4 Resumo

- **Confirmado desnecessário (sem uso nenhum):** `Holo3D.ring`, `HoloSurface.ready/pixelWidth/pixelHeight`,
  `Ui.TAB` + `tab.png`, 4 chaves de idioma. Remoção trivial, sem risco; pode entrar na 4.8.4 ou depois.
- **Duplicado:** três prévias do jogador (`EntityPreview`, `Holo3D.entity`, nenhuma na roleta), dois sistemas de
  partículas (`UiParticles` 2D × `HoloParticles` 3D), dois de botões (widgets `RespectButton/CycleSelector` × zonas
  `HoloCanvas`), quatro formatações de traço. Todos os duplicados do lado antigo saem junto das duas telas.
- **Problema de ciclo de vida real:** só os estáticos visuais sem limpeza no logout (1.1). O resto (framebuffers
  liberados em `removed()`, arrasto solto quando a janela perde o foco, rotação da entidade restaurada em `finally`)
  está correto.
- **Nada a remover antes do substituto:** criador e roleta antigos continuam até o modo mundo passar no teste real.

## 2. Por que a 4.8.1 parece uma tela

1. `HoloCharacterScreen extends Screen`. Com `mc.screen != null` o vanilla não atualiza `KeyMapping` de movimento,
   solta o mouse e não gira a câmera. Nenhum ajuste de perspectiva resolve isso.
2. A "profundidade" é de uma câmera virtual própria (`HoloGeometry.offAxis` + `view`) desenhada **por cima** do
   mundo, com o depth buffer limpo; a câmera real só puxa a cena 60% e ela volta ao centro (`updateCamera`, âncora
   híbrida). Os painéis seguem a tela, não o mundo.
3. Criador e roleta são `Screen`s 2D separados.

## 3. Requisitos do modo mundo (do pedido)

WASD, pulo e mouse-look sempre ativos; painéis em posições fixas do mundo; perspectiva real ao andar; dá para passar
ao lado ou através; clique por teste espacial preciso; chamar de volta/reposicionar; tudo privado do cliente, sem
entidade ou bloco no servidor; nenhuma regra de jogo nova; servidor continua autoritativo.

## 4. Arquitetura proposta (NeoForge 21.1)

### 4.1 Peças

| Peça nova | Papel | Reaproveita |
|---|---|---|
| `client.holo.world.HoloWorld` | Singleton de cliente: estado `CLOSED → SUMMONING → ACTIVE → DISMISSING`, âncora, modo de fluxo (seção 6), dono de painéis/views/superfícies. Nada de estado de mundo; limpo no logout. | o que hoje é estado de `HoloCharacterScreen` |
| `HoloAnchor` | Posição (`Vec3`) e yaw fixados na invocação + matriz `layout → mundo`. | — |
| `HoloArrangement` | O arco atual (`onArc`, `animate`, `assemble`, `closeOut`, `fitHeight`) movido da tela, sem mudança de fórmula. | `HoloCharacterScreen.animate` |
| `HoloWorldRenderer` | Passada no `RenderLevelStageEvent` (painéis, contornos, brilhos, feixes, pedestal, holograma do jogador). | `Holo3D` (primitivas), `draw3D`, `backDecor`, `lightTrails`… |
| `HoloSurfaces` | Redesenha as superfícies dos painéis **antes** da passada do mundo, num `RenderFrameEvent.Pre`, com cadência (placa da frente a cada quadro, vizinhas ~10 Hz, ocultas nunca). | `HoloSurface`, `HoloView.draw`, `HoloCanvas` |
| `HoloInput` | Raio da mira, clique, roda, arrasto, modo cursor (seção 5). | `HoloPanel.zoneAt`, `HoloZone.Drag` |
| `ui.HoloGeometry` (funções novas, puras) | `layoutToWorld(anchor, unitsPerBlock)`, `rayPanel(modelInverse, origem, direção)`, alcance. Entram no `SelfTest`. | `panel`, `hit` |

### 4.2 Espaço e transformações (a decisão central)

A composição da 4.8.1 já é 3D: os painéis vivem em "unidades de layout" num cilindro em volta de um olho em
`(centerX, ?, eyeZ)`. No mundo basta trocar **duas** matrizes:

- `layout` + `view` + `projection` da câmera virtual → **uma** matriz `layoutToWorld` da âncora:
  `T(âncora) · rotY(-yaw) · S(s, -s, s) · T(-centerX, -centroY, -eyeZ)`, com `s` = blocos por unidade
  (ex.: placa de 320 unidades ≈ 1,45 bloco → `s ≈ 1/220`).
- Projeção e visão da câmera virtual → as **da câmera real** que o evento entrega
  (`getProjectionMatrix()`, `getModelViewMatrix()` e a posição da câmera subtraída).

Assim `onArc`, `assemble`, partículas, feixes e pedestal continuam iguais; o "olho" do arco passa a ser o ponto onde
o jogador estava ao invocar, e os painéis ficam parados ali enquanto ele anda. Layout em tamanho fixo (ex.:
640 × 360 unidades), independente da janela e da escala de GUI (as regras de GUI 2/3/320×240 deixam de valer para o
holograma; valem para o que sobrar de tela).

Coordenadas relativas à câmera (`âncora − câmera`) em `float` para não perder precisão longe da origem.

### 4.3 Desenho

- Painéis em `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS` (ou `AFTER_PARTICLES`; escolher no teste real):
  teste de profundidade **contra o mundo** ligado, sem gravar profundidade (parede na frente esconde o painel), sem
  limpar o depth buffer, sem neblina, brilho próprio (fullbright). Ordem por distância à câmera real (já existe
  `drawOrder`/`depth`).
- `Holo3D` vira um lote de primitivas sem estado próprio + dois `begin/end`: o atual (GUI) e um de mundo que salva e
  devolve só mistura, cull, máscara de profundidade e cor do shader.
- Superfícies: mesma `TextureTarget`; densidade fixa por config (ex.: 3 px por unidade), filtro linear e mipmap
  (`glGenerateMipmap` depois do desenho) para o texto não serrilhar a 3-4 blocos. Redesenho fora da passada do mundo
  (o `HoloSurface.begin` troca framebuffer e projeção; no meio do `LevelRenderer` isso é arriscado).
- Holograma do jogador: `Holo3D.entity` com a `PoseStack` do mundo (pés no pedestal da âncora). Rig GeckoLib, Body
  Layers e prévia do criador continuam pelo renderer vanilla (sem cancelar `RenderPlayerEvent.Pre`, regra do rig).
- Fundo do Rimulib desligado no modo mundo (o jogador está jogando; e o pós-processamento tingiria os painéis).
- Esconder (não fechar) durante a câmera cinematográfica (`CinematicCamera` ativa) e com `hideGui` (F1).

### 4.4 Ciclo de vida

| Evento | Ação |
|---|---|
| K com `mc.screen == null`, jogador vivo, não espectador, menu do Rimulib fechado | invoca na frente da mira; K de novo dispensa |
| Tecla "Recall" (nova, configurável) | reposiciona a âncora na mira atual com a mesma animação de montagem |
| Afastou > N blocos da âncora (config, ex. 10) | dispensa sozinho (ou recall automático, opção) |
| Morte, `ClientPlayerNetworkEvent.Clone` (respawn/dimensão), `LoggingOut`, troca de nível | fecha na hora, libera framebuffers, limpa prévia de aparência e estáticos visuais |
| Tela aberta (inventário, chat, pausa) | painéis ficam visíveis, entrada desligada |
| Reload de recursos / resize | superfícies recriadas no próximo redesenho |

Invocação: âncora a ~1,6 bloco na horizontal da mira, na altura dos olhos; um `level.clip` do cliente encurta a
distância (mínimo ~0,8) se houver parede. Sem colisão: o jogador atravessa os painéis.

### 4.5 Rimulib

APIs públicas usadas hoje: `UiApi.text/measure` (fontes, continua dentro das superfícies), `FxApi` (fundo),
`MenuApi.isOpen/capturesInput` (K não abre por cima). Nenhuma API de UI no mundo do Rimulib 1.3.0 foi conferida: o
jar não está no cache desta sessão. **UNCERTAIN:** no início da 4.8.4, `javap` no jar para ver se existe algo de
painel no mundo; o plano não depende disso (desenho próprio no `RenderLevelStageEvent`).

## 5. Controles propostos

| Ação | Controle |
|---|---|
| Andar, pular, agachar, olhar | vanilla, sempre (não há `Screen`) |
| Apontar | **mira** (centro da tela): raio da câmera real até os planos dos painéis, até o alcance de interação do jogador; ponto de mira e realce de hover desenhados no painel |
| Clicar | botão de ataque (ou usar) **só quando a mira está num painel interativo**: `InputEvent.InteractionKeyMappingTriggered` cancelado, sem balanço da mão. Fora dos painéis, ataque e mineração normais |
| Rolar / trocar seção | roda sobre um painel: cancelada no `InputEvent.MouseScrollingEvent` (hotbar não troca); fora dos painéis a roda volta a ser a hotbar |
| Arrastar (árvore de skills, girar holograma, mudar escala) | segurar ataque com a mira no painel; posição pelo raio no plano do painel capturado (`HoloZone.Drag`, já existe) |
| Modo cursor (opcional) | tecla configurável (ex.: Alt esquerdo) segurada: solta o mouse, a câmera para de girar, WASD continua; raio pelo cursor (desprojeção com as matrizes do quadro). Para quem prefere apontar com cursor |
| Navegar | clicar na placa lateral ou no chip (já existe); números 1-5 **não** são capturados (hotbar) |
| Dispensar / chamar de volta | K / tecla Recall; Esc continua abrindo o menu de pausa |

Tudo continua sendo intenção para o servidor (`CharacterAppearanceUpdatePayload`, `BirthRollRequest`,
`BirthConfirmRequest`, `SkillPurchaseRequest`). Nenhum pacote novo.

## 6. Migração das interfaces de personagem

### 6.1 Separar lógica de apresentação

| Novo | Sai de | Conteúdo | Lado |
|---|---|---|---|
| `client.character.AppearanceDraft` | `CharacterCreationScreen` | 5 índices, `set*`, `randomize`, prévia (`setPreview`/`clearPreview`), `save()` com espera por `revision` e timeout 200 ticks, estado `EDITING/SAVING/SAVED/ERROR` | cliente, sem desenho |
| núcleo puro da roleta (`ui.RouletteMachine` ou similar) | `BirthRouletteScreen` (versão **pós-#36**) | fases, ângulo/easing, `settleOn`, `restingAngle`, decisão por resposta (`ROLLED/STALE/NO_REROLLS/ERROR`, `requestId`), timeout; recebe ficha/resposta como entrada e devolve eventos (tique, revelação, BONER) | puro, testável no `SelfTest` |
| `client.roulette.RouletteSession` | idem | liga o núcleo a `ProfileClientCache` e `PacketDistributor` | cliente |
| `client.holo.CreatorView`, `RouletteView` (`HoloView`) | — | desenho com `HoloCanvas`: seletores com setas, paleta, aleatório, salvar; roda num painel próprio (fatias iguais, chances do servidor em cada fatia, dica com a tabela), atributos revelados, traços, Spin/Spin again/Continue | cliente |

Regras preservadas: a roda sempre para na raridade da ficha; chances só do servidor (`ProfileClientCache.odds()`);
um novo giro; Continue envia `BirthConfirmRequest` e descarta o giro restante; elegibilidade de traços e
`withTraitsCoherentWith` continuam no servidor; ficha legada mostra a origem; nenhuma Speciality ou traço novo.

### 6.2 Fluxo único

| Situação do jogador | K invoca |
|---|---|
| Sem aparência (`!created`) | modo **CREATE**: placa do criador + holograma do jogador no pedestal (prévia ao vivo) |
| Aparência salva, sem nascimento (`!born`) | modo **BIRTH**: placa da roleta na frente, criador como placa vizinha (editar antes de girar) |
| Nascido, resultado não confirmado | **HUB** com a placa da roleta disponível (Spin again / Continue) |
| Nascido e confirmado | **HUB** (5 seções de hoje); roleta só para ver o resultado |

`openCreator()`/`openRoulette()` passam a trocar o modo/a placa da frente dentro do `HoloWorld`; salvar no criador
avança CREATE → BIRTH quando o servidor confirma; Continue avança BIRTH → HUB. Nenhum `setScreen` no fluxo. A
seção Character continua sendo onde se edita a aparência depois (abre a placa do criador no arco).

### 6.3 O que acontece com as telas antigas

- 4.8.4: `CharacterCreationScreen` e `BirthRouletteScreen` deixam de ser abertas pelo fluxo, mas **ficam no código**
  até o teste real aprovar o modo mundo.
- **UNCERTAIN (decisão do dono):** manter `HoloCharacterScreen` como modo de reserva por config de cliente
  (`mode = world | screen`, padrão `world`) durante uma etapa, para comparar e para quem usa mod de shader; ou
  removê-lo já na 4.8.4.
- 4.8.5 (limpeza): apagar as duas telas, `RespectScreen`, `RespectButton`, `CycleSelector`, `EntityPreview`,
  `UiParticles`, `panel.png`/`button.png`/`tab.png`, `Ui.TAB`/`PANEL`/`BUTTON`/`titledPanel`/`nineSlice`, as funções
  da câmera virtual em `HoloGeometry`, as chaves de idioma órfãs e (se não houver modo reserva) `HoloCharacterScreen`
  e `HoloBackdrop`.

## 7. Sequência de implementação da 4.8.4

0. **Pré-requisito:** o dono decide o PR #36 (integra ou fecha). A extração da roleta parte da versão final de
   `BirthRouletteScreen`/`GameplayNetworking`; começar antes gera conflito no mesmo bloco `WAITING`.
1. `HoloGeometry`: `layoutToWorld`, raio no painel em espaço de mundo, alcance; casos no `SelfTest` (ida e volta
   layout → mundo → local, raio paralelo, atrás da câmera, painel girado).
2. `Holo3D` dividido (lote de primitivas + estado GUI + estado mundo). A tela atual continua funcionando igual.
3. `HoloWorld` com ciclo de vida, âncora, invocar/recall/dispensar e eventos de limpeza; desenha as 5 seções atuais
   no mundo, ainda sem interação. Superfícies redesenhadas no `RenderFrameEvent.Pre`.
4. `HoloInput`: mira, cancelamento de ataque/uso e da roda só sobre painel, arrasto, hover, som; modo cursor.
5. Efeitos portados: contornos, brilhos, feixes, partículas, pedestal e holograma do jogador.
6. `AppearanceDraft` + `CreatorView`; modo CREATE; criador antigo fora do fluxo.
7. Núcleo da roleta + `RouletteSession` + `RouletteView`; modos BIRTH e roleta no HUB; roleta antiga fora do fluxo.
8. K, tecla Recall e tecla do modo cursor; config de cliente (distância, escala, densidade da superfície, alcance,
   modo); textos novos em inglês no `en_us.json`.
9. Remoções triviais da seção 1.4 e limpeza dos estáticos no logout.
10. Handoff 4.8.4, `CLAUDE.md`/`AGENTS.md`/`README.md`, teste real com capturas.

## 8. Riscos e testes de regressão

### Riscos

| Risco | Mitigação |
|---|---|
| Clique no painel acertando a entidade/bloco atrás (ou o contrário) | cancelar só quando o raio acerta painel interativo **mais perto** que o `hitResult` vanilla; teste explícito |
| Texto ilegível de longe ou inclinado | densidade fixa + mipmap; placa da frente perpendicular à mira na invocação; alcance de leitura na config |
| Custo de 12 framebuffers e redesenho | cadência por painel; chips desenhados como geometria; medir ms/quadro como na 4.8.1 |
| Estado GL vazando para o `LevelRenderer` | `begin/end` do mundo restauram tudo; teste com F3, água, vidro, partículas e neblina |
| Mods de shader (Iris/Oculus) e `RenderLevelStageEvent` | fora do escopo de teste; modo reserva (6.3) se o dono quiser |
| Prévia de aparência vazar | `clearPreview` em toda saída (dispensa, morte, logout, troca de modo); a prévia já é só local |
| Conflito com o PR #36 | passo 0 |
| Assinaturas do NeoForge 21.1 (`RenderLevelStageEvent`, `RenderFrameEvent`, `InputEvent.*`) | conferir no jar 21.1.265 antes de codar (regra do CLAUDE.md) |

### Testes

- Automáticos: `./gradlew build`, `bash .github/scripts/server-smoke-test.sh` (`SelfTest` com os casos novos de
  geometria e do núcleo da roleta: `ROLLED`, `STALE`, `NO_REROLLS`, `ERROR`, timeout, resposta atrasada, novo giro
  gasto uma vez só), `python3 tools/player_anim/bbsync.py`.
- Cliente real (Xvfb, um cenário por item): invocar; andar em volta e capturar de 3 ângulos; pular e girar a
  câmera com os painéis abertos; passar através; clicar seção, chip e skill pela mira; mineração e ataque normais
  fora dos painéis; roda sobre painel × hotbar; arrastar a árvore; jogador novo do zero: criar → girar → novo giro →
  Continue → HUB; erro e timeout da roleta (com o #36: `/respectcraft admin roulette_fail`); morrer, trocar de
  dimensão, deslogar com os painéis abertos; F1 e F5; finalização cinematográfica com painéis abertos.
- Multiplayer (dois clientes + servidor dedicado): o outro jogador não vê nada; aparência e ficha só mudam pela
  confirmação do servidor; Body Layers e rig do jogador continuam corretos para os dois.
