# Handoff — Etapa 4.8.1: menu holográfico do personagem (tecla K)

Rework **completo da apresentação** do menu da tecla K. O `CharacterHubScreen` e a `SkillTreeScreen` planos foram
removidos e substituídos por `client.holo.HoloCharacterScreen`: painéis projetados em perspectiva real, com
profundidade, paralaxe, holograma 3D do próprio jogador, partículas, abertura/fechamento animados e transições entre
seções. **Nenhum sistema de jogo mudou**: ficha, Specialities, traços, atributos, necessidades, compras de habilidade,
save e rede são os mesmos; o menu só lê as caches do cliente e manda os mesmos pedidos ao servidor.
`mod_version` continua `0.5.7` (sem release).

Guia técnico usado: a skill `immersive-minecraft-ui` (anexada ao pedido), validada contra as APIs reais do Minecraft
1.21.1, NeoForge 21.1.265 e Rimulib 1.3.0 (assinaturas conferidas no jar com `javap`, nada suposto).

## 1. O que o jogador vê

| Seção | Conteúdo (todos dados já existentes) |
|---|---|
| 01 Overview (abre ao apertar K) | Speciality e raridade (ou origem legada / "não nasceu" com atalho para a roleta), traço de nascimento com polaridade, os seis atributos (valor exato + barra), pontos de habilidade, resumo das quatro necessidades |
| 02 Character | O próprio jogador em 3D (rig GeckoLib, Body Layers, skin da conta Classic/Slim, cabelo, rosto) num palco holográfico, girado por arrasto; ficha da aparência confirmada pelo servidor; botão para o Character Creator que já existia |
| 03 Body & Traits | Coom, Bexiga, Intestino e Emoção com o mesmo limite crítico do servidor (`NeedsRules.LOW/URGENT`), efeitos de consumíveis ativos, traço com polaridade, classificação (categoria · elegibilidade), descrição e situação do efeito. Hidratação/Energia não aparecem |
| 04 Speciality | Speciality atual com a cor da raridade (Common cinza, Uncommon verde, Rare azul, Epic roxo, Legendary dourado, BONER rosa), descrição completa, Speciality de nascimento quando for diferente, escada das seis raridades com as chances reais do servidor (`SpecialityOddsSync`). **Sem botão de troca** (não existe troca) |
| 05 Skills | A árvore que já existia (3 ramos, 18 habilidades, bloqueado/disponível/comprado, custo, pré-requisitos), arrastar para mover, clicar para pedir a compra (`SkillPurchaseRequest`, validada pelo servidor); a tela só comemora quando a ficha nova chega |

Barra lateral: as cinco seções (com selo de pontos em Skills) e as ações que o menu antigo tinha — **Appearance**
(Character Creator), **Birth/Birth roulette** (roleta), **Display** (novo, opções visuais) e **Back to game**.
Placa de identidade (nome, Speciality, raridade, traço, pontos) sob o holograma do jogador nas seções estreitas.

Controles: clique, roda (sobre a barra troca de seção; sobre o painel rola), arrastar (árvore, sliders, girar o
jogador), botão direito/meio arrastado inclina a cena (volta sozinha), `Tab`/`Shift+Tab`, `↑`/`↓`, `1`–`5`,
`PgUp`/`PgDn`, `←`/`→` (ramo da árvore), `R` recentraliza, `Esc` fecha o Display ou o menu, `K` fecha.

## 2. Arquitetura de renderização

```
HoloCharacterScreen (Screen, nao pausa)
 ├─ por painel: HoloSurface (TextureTarget transparente, reaproveitado)  ← GuiGraphics + HoloCanvas desenham o conteudo
 └─ Holo3D: passada em perspectiva dentro do render do Screen
      fundo (aneis, grade de chao) → jogador 3D → quads dos paineis (ordenados por profundidade)
      → contornos/varreduras/feixes/pedestal/mira (lote aditivo) → brilhos e particulas (lote aditivo)
```

| Classe (`com.respectcraft.client.holo`) | Papel |
|---|---|
| `HoloCharacterScreen` | Ciclo de vida, layout, linha do tempo das animações, câmera, roteamento de entrada, composição |
| `HoloPanel` | Retângulo de repouso, estado animado do quadro, matrizes congeladas (modelo, visão·modelo, PVM e inversa), regiões do quadro |
| `HoloSurface` | `TextureTarget` por painel; desenha na densidade real da tela; mistura que acumula alfa (cor pré-multiplicada) |
| `HoloCanvas` | Pincel do conteúdo: vidro, molduras, barras, pílulas, botões com hover animado, tipografia do Rimulib, registro de regiões e dicas |
| `Holo3D` | Passada 3D: troca/restaura projeção e model-view, profundidade, cull, neblina, luzes; quads texturizados, linhas, anéis, sprites, jogador |
| `HoloParticles` | Vetor fixo (260) de partículas em espaço de layout, um lote aditivo |
| `HoloView` + `OverviewView`, `CharacterView`, `BodyView`, `SpecialityView`, `SkillsView` | Conteúdo das seções (cabeçalho fixo + corpo rolável) |
| `HoloNav`, `HoloSettings` | Barra lateral/placa e painel modal Display |
| `HoloConfig`, `HoloBackdrop`, `HoloClient`, `HoloAnim`, `HoloStyle`, `HoloZone` | Config de cliente, fundo pelo Rimulib, registro, envelopes, paleta, regiões |
| `com.respectcraft.ui.HoloGeometry` | Matemática pura (JOML) de projeção, painel e clique — comum, testada no `SelfTest` |

**Por que superfícies + quads.** Cada painel é desenhado como GUI comum num framebuffer próprio e depois vira um quad
3D. Os vértices vão com a projeção real, então a GPU interpola as UVs com correção de perspectiva (o `w` carrega a
profundidade) usando os shaders do próprio jogo (`position_tex_color`/`position_color`) — sem shader de composição
próprio. A técnica de "cantos em clip-space" da skill não foi necessária.

**Texto nítido.** A projeção é **fora do eixo** (`HoloGeometry.offAxis`): o plano de referência (onde fica o painel
principal) mostra exatamente a tela em unidades de GUI e a superfície tem a densidade real (`guiScale × escala do
menu`), então em repouso cada texel cai num pixel. O deslocamento do olho pela paralaxe não move esse plano (conferido
no `SelfTest` e nas capturas: o painel principal fica idêntico pixel a pixel com o cursor nos cantos opostos).
Brilho e varredura são desenhados sob o texto ou só nas bordas; nada de blur/bloom/aberração no texto.

**Profundidade.** O jogador é desenhado antes, com profundidade; painéis, linhas e brilhos vêm depois com teste de
profundidade ligado e sem gravar. Os `RenderType` do jogo desligam o teste ao fim do próprio lote, então `Holo3D`
religa o teste em cada lote (bug achado no cliente real: o vidro cobria o jogador do palco).

## 3. Posição dos painéis e projeção

- Espaços: **layout** (unidades de GUI, y para baixo, como um `Screen`), **menu** (y para cima, origem no centro do
  plano de referência) e **olho** (câmera virtual em `0,0,0` olhando para `-z`).
- Câmera do menu: FOV vertical fixo de 42° (`HoloCharacterScreen.FOV`), independente do FOV do jogo — a leitura e o
  clique não mudam com o FOV (testado com FOV 110). Plano de referência a `H / (2·tan 21°)`.
- O menu ocupa no máximo 660 × 370 unidades, centralizado; na menor GUI (320 × 240) usa a tela inteira.
  "Menu size" acima de 100% nunca deixa o layout menor que 320 × 240.
- Barra lateral: `dz = +10`, girada 16° pela borda **de fora** (a de dentro recua: cabine). Placa: `dz = +14`, −16° pela
  borda de fora e 14° de inclinação. Painel principal no plano de referência. Modal Display a `+34`, empurra o resto
  para trás. Holograma do jogador a `+36` (encaixado) ou `+30` (palco). Fundo a `−320`, partículas de `−260` a `+70`.
- Seções estreitas encolhem o painel até o conteúdo (cache visual por seção); Skills e Character usam o painel largo.

**Ancoragem híbrida.** O menu nasce na frente da câmera. A paralaxe vem do cursor (olho desloca até 38 × 22 unidades,
com suavização e só depois de 0,42 s) e da câmera real: se ela gira (empurrão, giro do servidor), a cena acompanha 60%
do giro, limitada a 20°/14°, e a âncora volta a centralizar sozinha (~0,7 s). Arrastar com o botão direito/meio inclina
a cena até ±26°/±16°; ao soltar, uma mola recentraliza; `R` recentraliza na hora. Nada é criado no mundo (sem
entidades, blocos ou partículas do jogo).

## 4. Entrada (teste inverso)

Cada quadro congela as matrizes de todos os painéis **antes** de desenhar. O cursor vira raio pela inversa de
`projeção · visão · layout · modelo` direto no espaço local do painel (`HoloGeometry.hit`), e o painel com menor `t`
(mais perto) ganha; o Display, aberto, captura tudo. O conteúdo registra as regiões (`HoloZone`) **durante** o
desenho, e o clique usa as regiões do último quadro desenhado, com as mesmas matrizes — o que se vê é o que responde.
Arrastos capturam a região até soltar e seguem o cursor no plano do painel mesmo fora dela (`intersect` sem limite).
O cursor da janela é lido em pixels reais (`MouseHandler`), sem o arredondamento da GUI. Botões soltos fora da janela
(perda de foco) são detectados por `glfwGetMouseButton` e o arrasto é solto. A roda é sempre consumida pelo menu:
nunca chega na hotbar (conferido: slot 0 → 0 depois de rolar).

## 5. Ciclo de vida e limpeza

| Evento | O que acontece |
|---|---|
| K (fora de tela) | Abre na Overview; se o menu próprio do Rimulib (`MenuApi.isOpen/capturesInput`) estiver aberto, não abre por cima |
| K / Esc / Back to game | Fechamento animado (~0,26 s; 0,1 s com movimento reduzido) e então `setScreen(null)` (cursor capturado de novo, movimento volta) |
| Esc com Display aberto | Fecha só o Display (grava a config) |
| Outra tela (morte, troca de dimensão, desconexão, Appearance, Birth) | `removed()`: libera os 5 framebuffers, tira a camada do Rimulib, solta arrastos, limpa partículas, grava a config pendente |
| Resize / escala de GUI | `init()` refaz o layout; as superfícies só realocam quando o tamanho em pixels muda |
| Logout | `HoloBackdrop.clear()` (a camada do Rimulib nunca fica ativa sem o menu) |
| Fechamento travado | Força o fechamento depois de 1,5 s |

## 6. Animações e efeitos

- **Fase A (0–0,12 s):** ponto de luz e anel no centro, som curto (`ui.hover` grave).
- **Fase B (0,05–0,4 s):** feixes do ponto até cada painel; cada painel vem do fundo (`dz −170`), abre como uma linha
  que desdobra na vertical (mola curta), com o contorno se desenhando a partir do canto; cascata: barra, principal, placa.
- **Fase C (0,2–0,6 s):** conteúdo entra linha a linha (35 ms entre itens), barras crescem até o valor exato, holograma
  do jogador surge com mola, paralaxe liga a partir de 0,42 s. O clique já funciona a partir de ~0,1 s.
- **Troca de seção (~0,3 s):** o painel atual recua (`dz −130`, giro leve, some), o novo vem da frente (`+70`) com uma
  varredura holográfica; o marcador da barra desliza com mola; o jogador vai da placa para o palco (Character).
- **Fechamento:** conteúdo some primeiro, painéis recolhem numa linha e recuam, contorno vira faíscas.
- **Contínuo:** contornos com brilho, cantoneiras, faixa de varredura sob o texto, anéis de fundo girando, grade de
  chão em perspectiva, partículas subindo, anel de varredura no holograma, mira holográfica no ponto do clique, pulsos
  nas ligações da árvore, faíscas na compra/clique.
- **Rimulib (fundo):** passada própria `assets/respectcraft/shaders/fx/holo_backdrop.fsh` no estágio
  `FxApi.stage("respectcraft")`: dessatura (−65%), vinheta azul e varredura fraca **só no mundo** (antes da GUI). No
  cliente real a saturação média do chão caiu de 0,41 para 0,24 com o fundo ligado.

## 7. Integração com o Rimulib 1.3.0 (só API pública)

| API | Uso |
|---|---|
| `client.api.UiApi.text/measure` + `UiApi.Font.DISPLAY/LABEL/MONO` | Títulos, legendas e números. Medido no cliente real: o `y` é a **linha de base**; altura das maiúsculas ≈ 6 (LABEL/MONO) e ≈ 11 (DISPLAY) na escala 1 (`HoloCanvas.capHeight`). Se falhar, cai para o fonte vanilla |
| `client.api.FxApi.stage` + `Stage.add/remove` + `Frame.pass` | Fundo do mundo (seção 6) |
| `client.api.MenuApi.isOpen/capturesInput` | K não abre por cima do menu do Rimulib |

Achado importante: os campos de `Frame.screen()` (`desaturate`, `vignette`…) **não têm efeito sozinhos no 1.3.0** —
viram uniformes, mas o `composite.fsh` embutido só copia a cena. Use `Frame.pass` com shader próprio (como aqui).
`MenuApi`/`SkillApi`/`WhiteSpace` não foram usados para o menu: o menu do Rimulib é dele (itens `SkillHolder`), não um
painel espacial genérico, e o K precisa abrir sem item.

## 8. Acessibilidade, config e desempenho

`config/respectcraft-holomenu-client.toml` (cliente), também editável no painel **Display**: tamanho do menu
(80–125%), opacidade do vidro (50–100%; o texto fica sempre opaco), intensidade das animações (0–100%), movimento
reduzido (fades curtos, sem paralaxe, partículas, inclinação nem mola), alto contraste (vidro escuro opaco, texto mais
claro, sem linhas de varredura) e fundo do mundo. Tudo funciona com movimento reduzido/intensidade 0.

Desempenho: 5 framebuffers no máximo (barra, principal ×2 para a transição, placa, Display), criados uma vez e só
realocados quando o tamanho muda; partículas num vetor fixo; um lote por tipo (quads, linhas, brilhos); nenhum pacote
no hover (a compra só sai no clique). A passada do Rimulib mediu 5,2 ms por quadro no llvmpipe (CPU).
Nada do pacote `client` carrega no servidor dedicado (`HoloGeometry` é JOML puro).

## 9. Testes e resultados

Rodados nesta sessão de nuvem, com o código final:

| Comando | Resultado |
|---|---|
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` | OK — `RespectCraft self-test: OK` (inclui `holoGeometry`), nenhuma classe comum importando cliente, sem erro de dist |
| `python3 tools/player_anim/bbsync.py` | OK |

**Cliente real** (Xvfb + openbox, Mesa llvmpipe, `xdotool`, capturas ImageMagick, vídeo `ffmpeg` a 30 fps) contra o
servidor dedicado de desenvolvimento, jogador `Dev` (skin Slim). Roteiro em `tools/visual-test/helpers.sh`.

| # | Cenário | Resultado | Evidência |
|---|---|---|---|
| 1–2 | K abre; abertura em três fases (ponto/feixes → painéis desdobram do fundo → cascata) assentada ~0,55 s | OK | `01_abertura_quadros_30fps.jpg` |
| 3 | Perspectiva e paralaxe: cursor nos cantos opostos — painel principal idêntico pixel a pixel, barra, holograma, anéis e partículas se deslocam; botão direito inclina a cena e volta | OK | `03_…`, `10_…` |
| 4 | As cinco seções (por clique projetado, `1`–`5`, `Tab`) | OK | `02`, `04`, `06`, `07`, `11` |
| 5 | Prévia 3D: skin, Slim, cabelo, rosto, rig; na frente do vidro; arrastar gira | OK (bug de profundidade achado e corrigido) | `04`, `05` |
| 6–8 | Atributos exatos; quatro necessidades com o limite crítico do servidor; traço com classificação e descrição | OK | `02`, `06` |
| 9 | Nome longo ("The Foot Worshippers") ajustado ao painel, também em 320×240 | OK | `11` |
| 10 | Skills: troca de ramo, arrastar, compra validada pelo servidor (pontos 4→3→2→1, nó vira comprado, filhos liberados) em GUI 1, 2 e 3 | OK | `07`, `12` |
| 11 | Clique alinhado em painéis inclinados/projetados (abas da barra girada, nós, sliders) | OK | — |
| 12 | Roda: rola/troca seção, hotbar intacta (slot 0 → 0); arrastar slider no modal | OK | `08` |
| 13 | Esc e K fecham; movimento volta (Z 7,5 → 12,8 segurando W) e o mouse volta a girar a câmera (yaw 0 → 45°) | OK | `16` |
| 14 | Reabrir 6× seguidas, fechando no meio da abertura | OK, sem exceção | — |
| 15–16 | GUI 2 (1280×720 → 640×360) e GUI 3 (960×720 → 320×240, a menor); GUI 1 a 854×455 numa versão anterior | OK | `11`, `12` |
| 17 | FOV 110 com GUI 3: menu e clique iguais | OK | `11` |
| 18 | Redimensionar a janela com o menu aberto (1280×720 → 1000×600 → volta) | OK, layout refeito | `14` |
| 19 | Desconectar/reconectar (fechar e reabrir o cliente várias vezes; ficha e config mantidas) | OK | — |
| 20 | Servidor dedicado sem classe de cliente | OK (teste de fumaça) | — |
| — | Morte com o menu aberto (tela de morte assume, sem efeito preso); respawn e K de novo; troca para o Nether com o menu aberto | OK | `15`, `17` |
| — | Display: opacidade 90→60%, alto contraste, movimento reduzido (fade de ~0,15 s), fundo do mundo ligado/desligado; gravado no `.toml` | OK | `08`, `09`, `13` |
| — | Fundo do Rimulib: saturação média do chão 0,41 → 0,24 com o fundo ligado (só o 2D: 0,41) | OK | `02` |

Achados e corrigidos durante o teste real: Rimulib `UiApi.text` usa o `y` como linha de base; vidro cobrindo o jogador
(teste de profundidade desligado pelos `RenderType`); barra lateral e placa saindo da tela (giro pelo pivô central);
`ScreenFx.desaturate` sem efeito no 1.3.0 (trocado por passada própria); coluna da aparência apertada em 320×240.

## 10. Limitações conhecidas

- Teste visual só em software (Xvfb + llvmpipe, sem GPU real) e sem som (sem OpenAL no Xvfb).
- O texto dos painéis inclinados (barra lateral, placa) passa por filtro linear; só o painel principal em repouso é
  pixel a pixel. Durante a abertura/troca/inclinação o texto fica levemente suavizado até assentar.
- A altura das maiúsculas das fontes do Rimulib foi medida no cliente (GUI 2); se outra versão do Rimulib mudar as
  métricas, ajuste `HoloCanvas.capHeight`.
- Dicas (tooltips) são desenhadas planas sobre tudo, por legibilidade.
- Gamepad não é suportado (só mouse e teclado).
- As 15 árvores exclusivas de Specialities, troca de Speciality, Rage Mode e gravidez continuam fora do escopo.

## 11. Arquivos

- Novos: `client/holo/*` (20 classes), `ui/HoloGeometry.java`, `assets/respectcraft/shaders/fx/holo_backdrop.fsh`,
  chaves `gui.respectcraft.holo.*` e `gui.respectcraft.trait.category/eligibility/classification` no `en_us.json`.
- Removidos: `client/character/CharacterHubScreen.java`, `client/skilltree/SkillTreeScreen.java` e as chaves que só
  eles usavam (`gui.respectcraft.hub.title`, `hub.skills`, `skills.title`, `skills.back`).
- Alterados: `CharacterCreatorKeybinds` (abre o menu novo, K fecha, respeita o menu do Rimulib),
  `CharacterCreationScreen` (volta para a seção Character), `BirthRouletteScreen` (volta para a Overview),
  `RespectCraftClient` (config de cliente e estágio do Rimulib), `SelfTest` (`holoGeometry`).
- Evidências: `docs/evidencias/etapa4_8_1/`.

PR_E_MERGE
