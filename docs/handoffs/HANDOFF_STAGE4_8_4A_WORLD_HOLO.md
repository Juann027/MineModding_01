# Handoff — Etapa 4.8.4-A: menu holográfico no mundo (render e movimento)

Status: **implementada, testada em cliente real, aguardando aprovação do dono.** Base: `main` em `2f46e89` (merge do
PR #37, auditoria 4.8.3). O PR #36 (roleta) continua aberto e intocado; nada desta etapa mexe na rede, no Spin, no
criador ou na roleta. `mod_version` não mudou.

Escopo pedido: substituir a apresentação em tela do menu K por placas **de fato no mundo**, com WASD, pulo e mouse-look
sempre ativos, e só a interação mínima (invocar, dispensar, chamar de volta). Clique, rolagem, arrasto, criador e roleta
no mundo ficam para a 4.8.4-B/C.

## 1. Arquitetura

O arranjo em arco da 4.8.1 já era 3D (placas em "unidades de layout" num cilindro em volta de um olho). A 4.8.4-A
separou esse núcleo da casca `Screen` e trocou só as matrizes:

| Peça | Papel |
|---|---|
| `client/holo/HoloMenu.java` (novo) | **Núcleo comum**: seções, placas, chips, fita, painel Display, animações de montagem/troca/fechamento, superfícies (`drawSurfaces`), passada 3D (`render3D`), holograma do jogador, partículas, hover (`pick` por NDC, `pickRay` pelo raio da mira). Recebe da casca `view`, `layout`, `projection` e `unit` (unidades do olho por unidade de layout: pixels de GUI na tela, **blocos** no mundo). `Host` = o que muda entre as cascas (abrir criador/roleta, fechar, relayout). |
| `client/holo/HoloWorld.java` (novo) | **Casca do mundo (padrão)**. Âncora = posição dos olhos + yaw do jogador na invocação, gravadas em coordenadas de mundo e nunca recalculadas pela câmera. Layout fixo 640 × 360 unidades. `RenderFrameEvent.Pre`: relógio, animação, hover pela mira, redesenho das superfícies (com mipmap; as que ficaram fora da vista não são redesenhadas). `RenderLevelStageEvent` **AFTER_LEVEL**: `layoutToWorld` relativo à câmera + rotação da câmera real + projeção real (com o balanço da caminhada), esmaecimento de perto/por trás e a passada 3D. Ciclo de vida, K/Recall e a troca da tela 4.8.1 pelos painéis no mundo. |
| `client/holo/HoloCharacterScreen.java` | Virou **casca fina** da tela (câmera virtual, cursor, fundo do Rimulib) em volta do `HoloMenu`. Reserva temporária (`mode = "SCREEN"`), sem recursos novos. O enum `Section` continua aqui (o criador o usa). |
| `client/holo/Holo3D.java` | `beginWorld/endWorld`: mesma projeção da câmera, model-view zerada (vértices já relativos à câmera), teste de profundidade **contra o cenário** sem gravar, **sem limpar o depth buffer**, sem neblina, cull desligado; devolve projeção, model-view, máscara, cull, mistura, neblina e as luzes do mundo (para a mão). `entity` devolve a orientação de câmera do dispatcher. |
| `client/holo/HoloSurface.java` | `begin(null)` fora de tela; `mipmap()` (trilinear) para o texto a alguns blocos. |
| `client/holo/HoloPanel.java` | `inverseModelView`, `rayHit` (raio da mira no espaço do olho), `eyeLocal`, `offscreen` (recorte por clip space), `culled`. |
| `ui/HoloGeometry.java` | Puro: `layoutToWorld`, `rayLocal`/`rayHit` (raio de mundo → ponto local), `distanceToRect`. |
| `ui/HoloPlacement.java` (novo) | Puro: escolha da âncora contra paredes (seção 2). |
| `client/holo/HoloConfig.java` | `mode` (`WORLD`/`SCREEN`, padrão `WORLD`), `worldDistance` (2,0), `surfaceDensity` (3 px/unidade), `dismissDistance` (16 blocos). |
| `client/character/CharacterCreatorKeybinds.java` | K alterna os painéis no mundo; nova tecla **Recall** (`key.respectcraft.holo_recall`, **R**). Sem aparência, K continua abrindo o criador antigo. |
| `HoloCanvas`, `HoloView`, `HoloNav`, `HoloSettings`, 5 views | Só trocaram o tipo do dono (`HoloCharacterScreen` → `HoloMenu`); o conteúdo é o mesmo. |

Matriz central (`HoloGeometry.layoutToWorld`):
`T(âncora − câmera) · rotY(180° − yaw) · S(s, −s, s) · T(−centerX, −layoutH/2, −eyeZ)`, com `s = distância / eyeZ`.
Composta com a rotação da câmera (`event.getModelViewMatrix()` no AFTER_LEVEL = `camera.rotation()` conjugada) dá a
mesma `visão · layout` que a tela usava; por isso `onArc`, `assemble`, feixes, partículas e pedestal não mudaram. O
hover usa o mesmo `modelView` congelado do desenho (raio −z no espaço do olho).

Reaproveitado da 4.8.1 sem mudança de fórmula: composição em arco, `HoloView`s, `HoloCanvas`, `HoloNav`, `HoloSettings`,
`HoloAnim`, `HoloStyle`, `HoloParticles`, `HoloSurface` (framebuffer por painel), primitivas do `Holo3D`, holograma do
jogador. O fundo do Rimulib (`HoloBackdrop`) fica só no modo tela (o jogador está jogando; o pós-processamento
tingiria os painéis).

## 2. Posição, Recall e ciclo de vida

- **Invocação (K)**: olho do arco = olhos do jogador; a placa da frente a `worldDistance` (2,0 blocos) na direção do
  yaw, na altura dos olhos. O arco inteiro escala em volta do olho, então aproximar não muda o tamanho aparente.
- **Contra paredes** (`HoloPlacement.choose`, só leitura do nível, um `level.clip` por amostra): amostras nos cantos e
  meio das 5 posições de placa, na fita e nos chips. Aceita quando a placa da frente, a fita e os chips centrais estão
  livres e no máximo 30% das amostras das vizinhas batem. Ordem: distância 2,0 → 1,7 → 1,4 → 1,1 → 0,8 → 0,7 na
  direção da mira; depois o arco girado −25°, +25°, −50°, +50°. Nada coube → o candidato menos obstruído (no empate, o
  mais perto) e a dica "Tight space…" na barra de ação. Placas das pontas podem entrar na parede: o teste de
  profundidade as esconde.
- **Recall (R)**: refaz a escolha a partir da mira atual e repete a montagem. Só age com os painéis abertos.
- **Fecha na hora** (libera framebuffers): morte, respawn e troca de dimensão (`ClientPlayerNetworkEvent.Clone`, nível
  diferente), descarregar o nível, logout, espectador, menu do Rimulib, telas incompatíveis (morte, créditos,
  carregamento, desconexão, título, criador, roleta, tela 4.8.1). **Dispensa com animação**: K, botão ✕ (quando houver
  clique), afastar-se mais que `dismissDistance`.
- **Continua visível** com inventário, chat e pausa (sem hover). Escondido sem fechar durante a câmera
  cinematográfica e com F1.
- **Tela 4.8.1 no modo mundo**: quem pede `new HoloCharacterScreen(...)` (fim do criador, Continue da roleta, Esc do
  criador) é trocado por `ScreenEvent.Opening.setNewScreen(null)` + invocação no mundo — sem tocar no código do criador
  e da roleta.
- Cada K/Recall grava uma linha `Holographic panels summoned|recalled: anchor=(x, y, z) yaw=… frontDistance=… fits=…`
  no log do cliente.

## 3. Controles

| Ação | Controle |
|---|---|
| Invocar / dispensar | **K** (`key.respectcraft.creator`) |
| Chamar de volta para a frente da mira | **R** (`key.respectcraft.holo_recall`; R não é usado pelo vanilla 1.21.1; configurável em Controles) |
| Andar, pular, agachar, olhar, atacar, usar | vanilla, sempre (não há `Screen` nem entrada capturada) |
| Hover (protótipo) | a mira sobre uma placa realça o elemento e desenha o ponto de mira nela; **não clica** |

## 4. Testes

Automáticos (rodados nesta sessão, código final):

| Comando | Resultado |
|---|---|
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` | OK — `RespectCraft self-test: OK`; servidor dedicado sem classes de cliente |
| `python3 tools/player_anim/bbsync.py` | OK |

`SelfTest.holoWorld` (novo): olho do arco cai na âncora e a frente segue o yaw (4 yaws); direita/baixo do layout; raio
da câmera → mesmo ponto local da placa ancorada a partir de 3 posições de câmera diferentes; placa atrás da câmera
recusada; distância ao retângulo; posição em campo aberto (2,0), parede na frente (aproxima), pilar na frente (gira o
arco 50°), cubículo (avisa e usa a menor distância).

**Cliente real** (Xvfb + openbox, Mesa llvmpipe, 960 × 540, gráficos *Fast*, GUI 2) contra o servidor dedicado de
desenvolvimento, jogador `Dev` novo. Evidências em `docs/evidencias/etapa4_8_4a/`:

| # | Pedido | Resultado | Evidência |
|---|---|---|---|
| — | Jogador novo: K → criador → Save → roleta → Spin → **Continue** | Continue (que abria a tela 4.8.1) cai nos painéis no mundo, HUD normal | `09_continue_da_roleta_cai_no_mundo.jpg` |
| 1–5 | K invoca; andar para trás/frente, strafe, pular, girar a câmera | Painéis parados: diminuem ao se afastar, passam por trás ao atravessar, saem e voltam ao campo de visão no mesmo lugar | `01_…jpg`, vídeo `12_video_andar_pular_olhar.mp4` |
| 6 | Virar de costas | Nenhum painel na tela; ao voltar, no mesmo lugar | `01_…` (m10/m11) |
| 7–8 | Dar a volta; ver de lado e por trás | Volta de 360° (câmera em círculo, sempre olhando para a âncora): frente → perfil → verso esmaecido com texto espelhado → outro perfil → frente; prédios do fundo confirmam o movimento | `02_…jpg`, `03_…jpg`, vídeo `13_video_volta_360.mp4`; com WASD: `04_…jpg`, vídeo `14_video_atravessar_e_olhar_para_tras.mp4` |
| 9 | Afastar, Recall, nova posição | Âncora nova `(8.50, −58.38, −6.50) yaw −90` na frente da mira; recuar mostra que ela fica; o lugar antigo fica vazio | `05_recall_nova_ancora.jpg` |
| 10 | Fechar com K | Dissolução e controles normais (andar e pular depois) | `04_…` (c01–c03) |
| 11 | Paredes e sala pequena | Campo aberto 2,0; sala 3 × 3 1,4; parede a 1,5 bloco 1,4; corredor 1 × 2 0,7 — sempre `fits=true`, mesmo tamanho aparente, frente fora da parede, vizinhas escondidas pela parede | `06_…jpg` |
| 12 | Dimensão, respawn, desconexão | Ida ao Nether fecha (volta sem painel velho); `kill` fecha atrás da tela de morte; respawn e K de novo OK; Disconnect pelo menu de pausa com painéis abertos: sem exceção | `07_…jpg` |
| — | Protótipo de hover pela mira | Chip "Body & Traits" acende e avança com a mira nele | `08_prototipo_hover_pela_mira.jpg` |
| — | Reserva `mode = "SCREEN"` | Tela 4.8.1 abre, hover/dica/fundo e clique no chip (Speciality vem para a frente) funcionam pelo núcleo novo | `10_reserva_modo_tela.jpg` |

Achado e corrigido no teste real: vistas por trás, as placas sumiam e só o contorno ficava
(`11_achado_antes_da_correcao_placa_sumia_por_tras.jpg`) — o `RenderType` do holograma do jogador religa o cull ao
terminar. `Holo3D` desliga o cull em cada lote das primitivas.

Fechar a janela do cliente com `xdotool windowclose` termina com `X Error … BadWindow (X_WarpPointer)` do GLFW: é o
próprio harness matando a janela, não o mod (a desconexão pelo menu termina limpa).

**Não verificado** (honesto): dois clientes ao mesmo tempo (o outro jogador não ver nada é garantido pela construção —
nada sai do cliente —, mas não foi rodado); gráficos *Fancy*/*Fabulous*; GPU de verdade e mods de shader
(Iris/Oculus); F5, F1 e finalização cinematográfica com os painéis abertos; dispensa automática por distância (16
blocos); redimensionar a janela com os painéis abertos; custo em ms/quadro (não medido; llvmpipe a ~15-30 fps antes e
depois).

## 5. Limitações conhecidas

- **Sem interação nos painéis no mundo** (escopo): clique, roda e arrasto continuam sendo do mundo. Mirar numa placa e
  clicar **ataca/minera/usa o que está atrás**. A 4.8.4-B resolve (decisão 0.5 da auditoria).
- No modo mundo não dá para trocar de seção, abrir Display, Appearance ou Birth pela fita (precisam de clique). As
  dicas (`tooltip`) do `HoloCanvas` são calculadas mas não exibidas.
- `scale` (tamanho do menu, painel Display) não vale no modo mundo; lá vale `worldDistance` (o tamanho aparente é
  fixo).
- A posição só é avaliada em K/Recall: um bloco colocado depois pode cortar uma placa (o teste de profundidade a
  esconde).
- O arco gira em volta do ponto onde o jogador estava: andar até lá deixa o jogador "dentro" do arco.
- Verso das placas: vidro esmaecido com o texto espelhado (holograma "físico"). Placa atravessada pela câmera esmaece.
- Holograma do jogador continua opaco e em brilho total (como na 4.8.1).
- R pode coincidir com tecla de outro mod (é configurável).
- Primeira tecla K sem aparência ainda abre o criador antigo em tela (4.8.4-C).

## 6. Arquivos

Novos: `client/holo/HoloMenu.java`, `client/holo/HoloWorld.java`, `ui/HoloPlacement.java`, este handoff,
`docs/evidencias/etapa4_8_4a/*`.

Alterados: `client/holo/HoloCharacterScreen.java` (casca), `Holo3D`, `HoloSurface`, `HoloPanel`, `HoloConfig`,
`HoloClient` (eventos), `HoloCanvas`, `HoloView`, `HoloNav`, `HoloSettings`, `OverviewView`, `CharacterView`, `BodyView`,
`SpecialityView`, `SkillsView` (dono), `client/character/CharacterCreatorKeybinds.java` (K/R), `ui/HoloGeometry.java`,
`selftest/SelfTest.java` (`holoWorld`), `lang/en_us.json` (3 chaves), `CLAUDE.md`, `AGENTS.md`, `README.md`,
`docs/handoffs/README.md`.

Não tocados: criador, roleta e sua rede (PR #36), Specialities, traços, atributos, necessidades, Skill Trees,
finalizações, mobs, chefes, origens legadas, Hidratação/Energia/Cantil/Rede, registries e assets.

## 7. Próxima etapa — 4.8.4-B (interação no mundo)

Leia este handoff e as seções 5 e 5.1 da 4.8.3. Base técnica pronta:

1. **Dono do clique**: no `InputEvent.InteractionKeyMappingTriggered`, `menu.pickRay(reach, out)` (mesmas matrizes do
   desenho) contra a distância do `hitResult` vanilla; placa mais perto → cancelar com `setSwingHand(false)` e capturar
   até o botão subir (conferir no jar 21.1.265 se a mineração contínua passa pelo evento; senão `setDown(false)` no
   ataque durante a captura). Mesma regra para usar e pegar bloco.
2. **Clique**: `HoloPanel.zoneAt(out.x, out.y)` + `z.click` (como o `mouseClicked` da tela); placa lateral traz a seção
   para a frente (`menu.select`); `menu.clickBurst` para as faíscas.
3. **Arrasto**: guardar o painel capturado e usar `HoloGeometry.rayLocal(panel.inverseModelView, 0,0,0, 0,0,−1, out)`
   (sem recorte pelo retângulo) a cada quadro, como o `mouseDragged` da tela.
4. **Roda**: `InputEvent.MouseScrollingEvent` cancelado só com a mira numa placa (rola a frente ou gira o arco); fora
   dela a hotbar.
5. **Dicas**: `menu.tooltip` desenhado numa camada de GUI perto da mira (`RegisterGuiLayersEvent`).
6. **Display** e botões da fita (Appearance/Birth/✕) passam a funcionar sozinhos com o clique; `Host.relayout` do
   mundo hoje é vazio (decidir se `scale` muda `worldDistance`).
7. Modo cursor opcional (seção 5 da 4.8.3) e testes: clique com mob/bloco atrás (sem dano, sem quebra, sem balanço,
   slot igual), arrastar para fora da placa sem minerar, roda × hotbar.

4.8.4-C (criador e roleta no mundo, `AppearanceDraft`, núcleo da roleta) só depois do #36 integrado (decisão 0.1). A
tela `HoloCharacterScreen`, `HoloBackdrop` e as funções da câmera virtual de `HoloGeometry` saem na 4.8.5, depois do
teste de regressão do modo mundo; o enum `Section` precisa sair da tela junto.
