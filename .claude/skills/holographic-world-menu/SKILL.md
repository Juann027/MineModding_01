---
name: holographic-world-menu
description: Receita para construir um menu holográfico 3D em mod de Minecraft (NeoForge 1.21.1) do jeito que o RespectCraft fez o menu da tecla K — placas de GUI desenhadas em framebuffers, projetadas em arco no mundo (ou numa tela de reserva), com clique/hover/arrasto/roda pela mira, dono de entrada por aperto, modo cursor, janelas (popups) e ciclo de vida seguro. Use ao criar um menu holográfico novo, adicionar seção/janela ao menu K existente ou portar a técnica para outro mod.
---

# Menu holográfico no mundo (técnica do RespectCraft)

Referência viva neste repositório: `client/holo/*`, `ui/HoloGeometry`, `ui/HoloInputArbiter`, `ui/HoloPlacement`
e os handoffs `docs/handoffs/HANDOFF_STAGE4_8_1*`, `4_8_3*`, `4_8_4A*`, `4_8_4B*`, `4_8_4C*`.
Neste projeto, **estenda o que existe** (nova `HoloView` ou `HoloPopup`), não crie um segundo sistema.
Em outro projeto, recrie as peças abaixo com os mesmos papéis.

Alvo: Minecraft 1.21.1, NeoForge 21.1.x, JOML. Confira toda assinatura no jar real (`javap` / sources jar do
NeoForge) antes de usar — nada de API do NeoForge 21.2+ ou Forge antigo.

## 1. Ideia central

1. Cada placa é **GUI comum** (`GuiGraphics`) desenhada num `TextureTarget` próprio (a "superfície").
2. A superfície vira um **quad 3D** com a projeção real → a GPU faz correção de perspectiva nos UVs com os shaders do
   jogo (`position_tex_color`/`position_color`). Sem shader de composição próprio.
3. **Uma matriz por placa por quadro**, congelada antes de desenhar. Desenho, hover, clique, arrasto, roda e
   partículas usam exatamente essas matrizes → o que se vê é o que responde.
4. Regiões clicáveis (`HoloZone`) são **registradas durante o desenho**; o clique usa a lista do último quadro.
5. Núcleo comum (`HoloMenu`) + **cascas** que só fornecem `view`, `layout`, `projection` e `unit`:
   - **Mundo** (padrão, `HoloWorld`): âncora fixa no mundo, jogador anda/olha livre, nada de `Screen`.
   - **Tela** (reserva, `HoloCharacterScreen`): `Screen` com câmera virtual fora do eixo.

## 2. Peças e papéis

| Peça | Lado | Papel |
|---|---|---|
| `HoloGeometry` | comum, puro (JOML) | Projeção, matriz de placa, `layoutToWorld`, raio → ponto local, NDC↔GUI. Testável no servidor. |
| `HoloInputArbiter` | comum, puro | Dono de cada botão (NONE/HOLO/WORLD), decidido no aperto. |
| `HoloPlacement` | comum, puro | Escolhe âncora sem enfiar placas em paredes (`Sight` injetado = `level.clip`). |
| `HoloSurface` | cliente | `TextureTarget` transparente por placa, reaproveitado; `begin/end`, `scissor`, `mipmap`. |
| `HoloPanel` | cliente | Retângulo em layout + estado animado (dz, yaw, pitch, escala, alfa) + matrizes congeladas + zonas. |
| `HoloZone` | cliente | Região: `click`, `drag` (press/move/release), `scroll`, `enabled`. Pool reaproveitado. |
| `HoloCanvas` | cliente | Pincel: vidro, molduras, barras, botões com hover animado, texto (Rimulib `UiApi` com fallback vanilla), `zone(...)`, `tooltip(...)`. |
| `Holo3D` | cliente | Passada 3D: `begin/end` (tela) e `beginWorld/endWorld` (mundo); quads, linhas, brilhos, entidade. Salva e devolve todo estado GL. |
| `HoloView` | cliente | Conteúdo de uma seção: cabeçalho fixo + corpo rolável recortado. |
| `HoloPopup` | cliente | Janela exclusiva na frente do arco (criador, roleta…). |
| `HoloMenu` | cliente | Núcleo: placas, arco, animações, `drawSurfaces`, `render3D`, `pick`/`pickRay`, `press`, `exclusive()`. |
| `HoloWorld` / `HoloWorldInput` | cliente | Casca do mundo e roteador de entrada. |
| `HoloConfig` | cliente | Config de cliente (modo WORLD/SCREEN, distância, densidade, acessibilidade). |

Regra de lado: a matemática pura fica fora de `client` (vai para o `SelfTest`); tudo com `net.minecraft.client.*`
fica em `client`.

## 3. Espaços e matrizes

- **Layout**: unidades de GUI, x→direita, y→baixo, z→jogador (como um `Screen`). Retângulos das placas vivem aqui.
  No mundo o layout é fixo (640 × 360).
- **Placa local**: origem no canto superior esquerdo da placa, y para baixo — o mesmo espaço do conteúdo desenhado.
- **Olho**: câmera em (0,0,0) olhando para −z.

Matriz da placa (`HoloGeometry.panel`):
`T(x+px, y+py, dz) · rotY(yaw) · rotX(pitch) · S(sx, sy, 1) · T(−px, −py, 0)` (pivô relativo px,py).

Arco: centro da placa em `olho + raio·(sen θ, 0, −cos θ)`, girada `−θ`; ângulo = `(índice − carrossel)·31°`;
vizinhas com raio ×1,24, ±2 passos ×1,36 e transparentes. Navegar = animar `carrossel` com mola.

**Mundo** (`layoutToWorld`):
`T(âncora − câmera) · rotY(180° − yaw) · S(s, −s, s) · T(−centroX, −layoutH/2, −eyeZ)`, `s = distância / eyeZ`.
Use vértices relativos à câmera (evita perda de precisão longe da origem). Combine com
`event.getModelViewMatrix()` e `event.getProjectionMatrix()` do `RenderLevelStageEvent` (AFTER_LEVEL).

**Tela**: projeção **fora do eixo** (`offAxis`, `setFrustum`) com o plano de referência a `H / (2·tan(fov/2))`
(FOV fixo de 42°, independente do FOV do jogo). Em repouso a placa da frente cai pixel a pixel → texto nítido;
o deslocamento do olho (paralaxe) não mexe nesse plano.

Por placa, a cada quadro (`HoloPanel.freeze`): `model`, `modelView = view·layout·model`, `pvm`, `inverse(pvm)`,
`inverse(modelView)`, profundidade do centro (para ordenar).

## 4. Superfícies (texto nítido e transparência)

- Densidade = `guiScale × escala do menu` px por unidade (tela) ou ~3 px/unidade (mundo, `surfaceDensity`).
- Crie o `TextureTarget` uma vez; só `resize` quando o tamanho em pixels mudar. **Nunca aloque framebuffer por quadro.**
  Feche (`destroyBuffers`) em todo fechamento.
- `begin`: `flush` da GUI da tela (se houver), `clear` transparente, `bindWrite`, salva projeção e ordenação,
  ortho do tamanho lógico, `GuiGraphics` próprio.
- Mistura que **acumula alfa**: `blendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA)` → a
  textura sai pré-multiplicada; componha na cena com `ONE, ONE_MINUS_SRC_ALPHA`. Brilho = aditivo
  `(SRC_ALPHA, ONE, ZERO, ONE)`.
- `scissor` em pixels da superfície (o do `GuiGraphics` usa a janela, não serve).
- `end`: `flush`, desliga scissor, restaura projeção/mistura, volta a `getMainRenderTarget().bindWrite(true)`.
- Mundo: `glGenerateMipmap` + `LINEAR_MIPMAP_LINEAR` depois de desenhar (texto legível a alguns blocos).
- No mundo, desenhe as superfícies em `RenderFrameEvent.Pre` (antes do mundo) e pule as placas fora da vista
  (`offscreen()` por clip space).

## 5. Passada 3D

**Mundo** (`Holo3D.beginWorld`, dentro de AFTER_LEVEL):
- projeção da câmera real, model-view zerada (vértices já relativos à câmera);
- teste de profundidade **contra o cenário** (`LEQUAL`) sem gravar (`depthMask(false)`); **nunca limpe o depth buffer**;
- cull desligado (verso da placa visível, esmaecido), neblina desligada (`fogStart = MAX`);
- `endWorld` devolve projeção, model-view, máscara, cull, mistura, neblina e as luzes do mundo
  (`Lighting.setupLevel/setupNetherLevel` se desenhou entidade — senão a mão em 1ª pessoa fica escura).

**Armadilhas confirmadas em cliente real:**
- `RenderType`s do jogo desligam o teste de profundidade e religam o cull ao fim do lote → religue profundidade e
  desligue cull **em cada lote** das suas primitivas.
- Ordem: entidade/holograma do jogador primeiro (com profundidade), depois placas ordenadas por profundidade, depois
  linhas/contornos (aditivo), depois brilhos/partículas (aditivo).
- Placa atravessada pela câmera: esmaeça pela distância do olho ao retângulo (`distanceToRect`) em vez de deixar o
  plano próximo cortar.

## 6. Conteúdo: `HoloView` e `HoloCanvas`

```java
final class MinhaView extends HoloView {
    MinhaView(HoloMenu menu) { super(menu, Section.MINHA); }
    @Override float body(HoloCanvas c, float w, float vh, float since) {
        float y = 0F;
        c.type(HoloCanvas.Type.LABEL, tr("gui.meumod.holo.titulo"), 10F, y + 10F, 1F, c.text());
        boolean hot = c.over(10F, y + 20F, 80F, 16F);
        // ... desenhar botão com c.hoverAnim("meu.botao", hot)
        c.zone(10F, y + 20F, 80F, 16F).click = () -> enviarPedidoAoServidor();
        if (hot) c.tooltip(List.of(Component.translatable("gui.meumod.holo.dica")));
        return y + 40F; // altura usada (para a rolagem)
    }
}
```

Regras:
- A view **só lê caches do cliente e envia intenções** ao servidor. Nenhum dado de jogo próprio.
- Toda região interativa nasce de `c.zone(...)` no mesmo lugar onde foi desenhada.
- Arrasto: `zone.drag = new HoloZone.Drag(){...}` (capturado até soltar, segue no plano mesmo fora da região).
- Roda própria (seletor): `zone.scroll = amount -> ...` (tem prioridade sobre girar o arco/rolar).
- Texto nunca passa da placa: `fitSize`/`Ui.fit`/`Ui.wrap`, texto inteiro na dica.
- Rimulib `UiApi.text`: o `y` é a **linha de base** (ajuste com `capHeight`); tenha fallback para a fonte vanilla.
- Textos via `Component.translatable` (lang), nunca string solta.

## 7. Entrada

### Na tela (casca `Screen`)
Cursor em pixels reais (`MouseHandler`) → NDC → `HoloGeometry.hit(inverse(pvm), ...)` em cada placa → menor `t`
ganha → `zoneAt(local)`. Detecte botão solto fora da janela com `glfwGetMouseButton`. Roda sempre consumida.

### No mundo (`HoloWorldInput` + `HoloInputArbiter`)
- **Raio da mira = o do vanilla**: olhos e `getViewVector` da **entidade da câmera** no `partialTick` da câmera
  (mesmo instante do `mc.hitResult`), levados ao espaço do olho pela `view` do quadro. Distância do mundo = distância
  do `hitResult` (∞ se MISS).
- Placa só acerta pela **face da frente** (olho do lado +z local) e com alfa > ~5%.
- `hologramWins = acertou && dist ≤ alcance(6) && dist < distânciaDoMundo` (estritamente mais perto).
- `InputEvent.MouseButton.Pre` (cancelável, antes do grab e do `KeyMapping`): no aperto decide o dono;
  HOLO → cancela e chama `HoloMenu.press(painel, x, y)`; o soltar vai ao mesmo dono.
- `InputEvent.InteractionKeyMappingTriggered`: rede de segurança — cancele com `setSwingHand(false)` quando o dono for
  HOLO e **não deixe repetição segurada de um aperto do mundo agir atrás de uma placa** (senão a mineração atravessa).
- `InputEvent.MouseScrollingEvent`: cancele **só** com a mira numa placa; fora dela a hotbar troca normalmente.
- Teclas 1–9 nunca são capturadas. Nunca desligue controles vanilla de forma global.
- `ClientTickEvent.Post`/`RenderFrameEvent.Pre`: confira soltar perdido pelo estado do GLFW.
- **Modo cursor** (segurar uma tecla, ex. Alt esquerdo): `releaseMouse`, raio pela desprojeção do cursor; ao soltar,
  `grabMouse` (primeiro movimento ignorado = sem salto). Confira o estado físico da tecla todo quadro; ao perder foco,
  prenda o mouse de novo quando voltar. Em Linux, WMs com Alt+arrastar engolem o clique: deixe a tecla configurável.
- Dicas: camada de GUI acima da mira (`RegisterGuiLayersEvent.registerAbove(VanillaGuiLayers.CROSSHAIR, ...)`),
  acima/à direita da mira, só com a placa assentada.

## 8. Posição no mundo (`HoloPlacement`)

Na invocação (tecla) e no Recall: âncora = olhos + yaw do jogador; nunca recalcule pela câmera depois.
Amostras (cantos e meio das placas, fita, chips) em raios do arco, classificadas ESSENTIAL/IMPORTANT/OPTIONAL;
cada amostra = um `level.clip` do olho até o ponto. Tente distância 2,0 → … → 0,7 na mira; depois o arco girado
−25°, +25°, −50°, +50°. Nada coube → o menos obstruído (empate: mais perto) e aviso na barra de ação.
O arco escala em volta do olho: aproximar não muda o tamanho aparente.

## 9. Janelas (`HoloPopup`)

- Uma placa `popup` desenhada pelas mesmas superfícies e pela mesma passada 3D.
- Aberta: `exclusive()` → só ela responde; o arco recua (`POPUP_DZ`), esmaece e o clique nele é só consumido.
- Estado da janela fica numa classe **sem desenho** (ex.: `AppearanceDraft`, `RouletteSession` + máquina pura
  `RouletteMachine`), compartilhada com qualquer tela de reserva. Não duplique pedido/erro/timeout.
- Ação que gasta algo pede **segundo clique** em ~2,5 s (a mira clica sem querer andando).
- No modo mundo, intercepte `ScreenEvent.Opening` e troque telas antigas equivalentes pela janela
  (`setNewScreen(null)` + abrir a janela).

## 10. Ciclo de vida (obrigatório)

- **Fecha na hora** (libera framebuffers, solta arrasto, encerra modo cursor): morte, respawn/troca de dimensão
  (`ClientPlayerNetworkEvent.Clone` com nível diferente), `LevelEvent.Unload`, logout, espectador, telas
  incompatíveis (morte, créditos, carregamento, desconexão, título).
- **Dispensa animada**: tecla de abrir, botão ✕, afastar-se mais que `dismissDistance` (16 blocos).
- **Esconde sem fechar**: câmera cinematográfica, F1. Continua visível (sem hover) com inventário/chat/pausa.
- Fechamento travado (quadros parados): force após ~1,5–2 s.
- Estáticos só para cache visual do cliente, limpos no logout. Nada criado no mundo (sem entidades, blocos,
  partículas do jogo). Nada sai do cliente além dos pedidos que já existem.

## 11. Animação e acessibilidade

- Abertura em fases: ponto de luz → feixes → placas chegam de perfil (~78°) do fundo e desdobram com mola →
  conteúdo linha a linha (≈35 ms) → paralaxe só depois de ~0,42 s. Clique vale a partir de ~0,1 s.
- Troca de seção = giro do arco com mola levemente subamortecida (~0,5 s).
- Fechamento ~0,3 s: conteúdo some, placas giram de perfil e recuam.
- Config de cliente: tamanho, opacidade do vidro (texto sempre opaco), intensidade de animação, **movimento reduzido**
  (sem paralaxe/partículas/mola), alto contraste. Tudo precisa funcionar com intensidade 0.
- Partículas em vetor fixo (ex. 260); um lote por tipo; nenhum pacote no hover.

## 12. Rimulib 1.3.0 (opcional)

- `UiApi.text/measure` (fontes DISPLAY/LABEL/MONO) para tipografia.
- Fundo do mundo (só no modo tela): `FxApi.stage(modid)` + `Frame.pass` com shader próprio
  (`shaders/fx/holo_backdrop.fsh`). Os campos de `Frame.screen()` sozinhos **não fazem nada** no 1.3.0.
- `MenuApi.isOpen()` → não abrir por cima do menu do Rimulib (envolva em try/catch de `LinkageError`).

## 13. Testes

- Regras puras no `SelfTest`: geometria (desenho e clique com a mesma matriz; âncora segue o yaw; raio de 3 câmeras
  → mesmo ponto local; placa atrás recusada), arbiter (bloco/mob na frente = mundo; fora do alcance = mundo; dono
  até soltar), placement (campo aberto, parede, pilar, cubículo).
- Cliente real (Xvfb, `tools/visual-test/helpers.sh`), cenários mínimos: clique na placa com **bloco e mob atrás**
  em criativo (nada quebra/fere, mão não balança); segurar ataque na placa e virar até um bloco (nada minerado);
  roda sobre/fora (hotbar intacta fora); andar/pular/dar a volta (placas paradas); Recall; morte/dimensão/desconexão
  com o menu aberto e botão segurado; foco perdido; GUI 2 e menor GUI 320×240.
- Rode `./gradlew build` e o teste de fumaça; nunca diga que testou sem rodar.

## 14. Checklist para uma seção ou janela nova neste projeto

1. Nova `HoloView` (ou `HoloPopup`) em `client/holo`, registrada no `HoloMenu` (e no enum de seções, se for seção).
2. Desenho com `HoloCanvas`; zonas e dicas registradas no desenho.
3. Estado sem desenho numa classe própria se houver fluxo (pedido/resposta ao servidor autoritativo).
4. Chaves `gui.<modid>.holo.*` no `en_us.json`.
5. Nada de `Screen` no modo mundo; nada de framebuffer por quadro; estado GL devolvido.
6. Regra pura nova → `SelfTest`. Build + smoke test. Cliente real só se mexeu em render/entrada.
