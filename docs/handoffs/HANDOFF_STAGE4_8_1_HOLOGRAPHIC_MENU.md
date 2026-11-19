# Handoff — Etapa 4.8.1: menu holográfico do personagem (tecla K)

Rework **completo da apresentação** do menu da tecla K. O `CharacterHubScreen` e a `SkillTreeScreen` planos foram
removidos e substituídos por `client.holo.HoloCharacterScreen`: **placas holográficas soltas no espaço, num arco em
volta do ponto de vista**, projetadas em perspectiva real, com profundidade, paralaxe, holograma 3D do próprio
jogador, feixes de luz, partículas, montagem/dobra animadas e navegação espacial entre seções.

> **Correção de direção (pedido do dono, antes do merge):** a primeira versão era um "painel de controle" plano
> (barra lateral fixa + painel central grande + cartão do jogador). Ela foi substituída pela composição em arco
> descrita aqui; a arquitetura de render/clique foi mantida e a composição foi refeita. **Nenhum sistema de jogo mudou**: ficha, Specialities, traços, atributos, necessidades, compras de habilidade,
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

**Composição espacial (o que flutua onde):**

- **Arco de placas:** cada seção é uma placa própria num cilindro centrado no olho. A seção escolhida fica na frente,
  no plano de referência (texto pixel a pixel); as vizinhas ficam a ±31°, viradas para o olho, 24% mais fundas, mais
  apagadas e um pouco abaixo; as de ±2 passos somem na lateral. O mundo aparece entre as placas.
- **Navegar = girar o arco:** a placa nova vem do lado para a frente e a antiga recua girando para o outro lado (mola
  levemente subamortecida). Clicar numa placa de lado a traz para a frente; chips, `←`/`→`, `Tab`, `1`–`5` e a roda
  (fora da placa da frente) também giram o arco. Placas de lado mostram o conteúdo de verdade, sem hover interno.
- **Fita de identidade** acima do arco, inclinada para baixo (de frente para o olho): marca, nome, Speciality com a cor
  da raridade, traço, pontos e as ações que o menu antigo tinha — **Appearance** (Character Creator), **Birth/Birth
  roulette** (roleta), **Display** (novo, opções visuais) e **fechar** — como ícones com dica.
- **Chips de seção** numa fileira curva abaixo do arco, inclinada para cima; o ativo avança e acende.
- **Holograma do jogador** de pé na frente, à esquerda, num pedestal (`z +56`); na seção Character ele sobe no palco
  da própria placa e acompanha o arco quando ela gira.
- **Rastros de luz** ligam a fita à placa da frente, a placa ao chip ativo e às vizinhas, com pulsos correndo por eles.

Controles: clique, roda (sobre a placa da frente rola; fora dela gira o arco), arrastar (árvore, sliders, girar o
jogador), botão direito/meio arrastado inclina a cena (volta sozinha), `←`/`→`/`Tab`/`Shift+Tab`/`1`–`5` giram o
arco, `↑`/`↓`/`PgUp`/`PgDn` rolam, `[`/`]` trocam o ramo da árvore, `R` recentraliza, `Esc` fecha o Display ou o
menu, `K` fecha.

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
| `HoloNav`, `HoloSettings` | Fita de identidade, chips de seção e painel modal Display |
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
- **Arco:** `onArc(painel, ângulo, raio, y, w, h)` põe o centro do painel em `olho + raio·(sen θ, 0, −cos θ)` e gira
  `−θ` (de frente para o olho). Raio da placa da frente = distância do plano de referência; vizinhas `×1,24`; ±2 passos
  `×1,36` e transparentes. Posição contínua `carousel` (em índices de seção) com mola; o ângulo de cada placa é
  `(índice − carousel)·31°`, então a transição é o próprio giro do arco.
- Fita: arco 0°, raio `×1,06` (atrás), inclinação −15° pela borda de baixo. Chips: cilindro `×1,01`, passo angular pela
  largura do chip, inclinação +16° pela borda de cima; o ativo avança `×0,03`. Modal Display a `+40`, empurra o resto
  para trás. Fundo (anéis) a `−360`, anel e raios de chão em volta do olho, partículas de `−300` a `+140`.
- Largura da placa: 46% da tela (290–350); na menor GUI (320 × 240) ocupa quase a tela, as vizinhas viram faixas nas
  bordas e o holograma do jogador só aparece no palco. "Menu size" acima de 100% nunca deixa o layout menor que 320 × 240.
- Placas de seção estreitas encolhem até o conteúdo (cache visual por seção); Skills e Character usam a altura inteira.

**Ancoragem híbrida.** O menu nasce na frente da câmera. A paralaxe vem do cursor (olho desloca até 38 × 22 unidades,
com suavização e só depois de 0,42 s) e da câmera real: se ela gira (empurrão, giro do servidor), a cena acompanha 60%
do giro, limitada a 20°/14°, e a âncora volta a centralizar sozinha (~0,7 s). Arrastar com o botão direito/meio inclina
a cena até ±26°/±16°; ao soltar, uma mola recentraliza; `R` recentraliza na hora. Nada é criado no mundo (sem
entidades, blocos ou partículas do jogo).

## 4. Entrada (teste inverso)

Cada quadro congela as matrizes de todos os painéis **antes** de desenhar. O cursor vira raio pela inversa de
`projeção · visão · layout · modelo` direto no espaço local do painel (`HoloGeometry.hit`), e o painel com menor `t`
(mais perto) ganha; o Display, aberto, captura tudo. Placas de lado só aceitam o clique que as traz para a frente
(o conteúdo delas aparece, mas sem hover nem regiões ativas); chips, fita e a placa da frente têm regiões normais. O conteúdo registra as regiões (`HoloZone`) **durante** o
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
| Outra tela (morte, troca de dimensão, desconexão, Appearance, Birth) | `removed()`: libera os framebuffers (5 placas, fita, 5 chips, Display), tira a camada do Rimulib, solta arrastos, limpa partículas, grava a config pendente |
| Resize / escala de GUI | `init()` refaz o layout; as superfícies só realocam quando o tamanho em pixels muda |
| Logout | `HoloBackdrop.clear()` (a camada do Rimulib nunca fica ativa sem o menu) |
| Fechamento travado | Força o fechamento depois de 1,5 s |

## 6. Animações e efeitos

- **Fase A (0–0,15 s):** ponto de luz à frente do olho, som curto (`ui.hover` grave).
- **Fase B (0,03–0,5 s):** feixes do ponto até cada placa; cada placa chega no ar **de perfil** (girada ~78° para o
  lado em que fica no arco), vinda do fundo (`dz −160`), e desdobra com mola enquanto o contorno se desenha; cascata:
  fita, placa da frente, vizinhas, chips um a um.
- **Fase C (0,25–0,7 s):** conteúdo entra linha a linha (35 ms entre itens), barras crescem até o valor exato, o
  holograma do jogador surge com mola, rastros de luz acendem, paralaxe liga a partir de 0,42 s. O clique já funciona
  a partir de ~0,1 s.
- **Troca de seção:** o arco gira com mola (~0,5 s, leve passada do ponto): as placas varrem o espaço girando,
  a nova vem para a frente com uma varredura holográfica; o chip ativo avança; o jogador sobe/desce do palco.
- **Fechamento (~0,3 s):** conteúdo some primeiro, as placas giram de perfil, encolhem e recuam, contornos viram faíscas.
- **Contínuo:** contornos com brilho, cantoneiras, faixa de varredura sob o texto, anéis de fundo girando, anel e
  raios de chão em volta do ponto de vista, partículas espalhadas em volume (entre e na frente das placas), pulsos
  correndo pelos rastros de luz, anel de varredura no holograma, mira holográfica no ponto do cursor, faíscas no clique.
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

Desempenho: um framebuffer por placa (5 seções, fita, 5 chips pequenos e o Display), criados uma vez e só realocados
quando o tamanho muda; placas fora do arco (±2 passos) não são desenhadas; partículas num vetor fixo; um lote por tipo (quads, linhas, brilhos); nenhum pacote
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

**Validação espacial pedida para o merge** (composição em arco, `docs/evidencias/etapa4_8_1/`):

| # | Pedido | Resultado | Evidência |
|---|---|---|---|
| 1 | Vista frontal com placas em profundidades diferentes | Placa da frente no plano de referência, vizinha girada e mais funda, fita inclinada acima, chips em arco abaixo, jogador à frente, mundo visível entre elas | `01_frontal_placas_em_profundidades.jpg` |
| 2 | Vista em perspectiva com giro/paralaxe visível | Botão direito inclina a cena: placas viram trapézios, a vizinha fica pendurada no arco, o jogador fica entre elas | `02_perspectiva_inclinada_paralaxe.jpg` |
| 3 | Transição entre seções com placas se movendo em 3D | Overview → Speciality (3 passos): Character e Body varrem o espaço girando, Speciality vem para a frente e assenta com mola | `03_transicao_arco_overview_para_speciality.jpg` (quadros a 30 fps) |
| 4 | Mouse num painel projetado inclinado | Cena inclinada (−26°): clique na aba "Survival" desenhada em ângulo troca o ramo da árvore; hover/clique no chip da ponta (girado e inclinado) e na placa lateral girada trazem a seção para a frente | `04_clique_em_aba_inclinada_survival.jpg`, `05_hover_e_clique_em_chip_e_placa_lateral.jpg` |
| 5 | Série de quadros da abertura | Ponto de luz → feixes → placas chegam de perfil e desdobram → jogador surge → chips um a um → vizinha gira para o lugar | `06_abertura_quadros.jpg` |
| — | Menor GUI (GUI 3, 960×720 → 320×240) | Placa da frente quase na tela toda, vizinhas como faixas inclinadas nas bordas, fita e chips cabem | `07_gui3_320x240.jpg` |
| — | Jogador no palco da placa Character | Rig, skin Slim, cabelo e rosto, na frente do vidro, girável por arrasto | `08_character_jogador_no_palco.jpg` |

Também conferido no cliente real (a maior parte na primeira versão, com a mesma arquitetura de render/clique/ciclo de
vida, e repetido na composição em arco onde a mudança tocava):

| Cenário | Resultado |
|---|---|
| K abre na Overview; Esc/K fecham com a dobra; movimento (Z 7,5 → 12,8 com W) e giro do mouse (yaw 0 → 45°) voltam | OK |
| Atributos exatos; quatro necessidades com o limite crítico do servidor; traço com classificação; nome longo ("The Foot Worshippers") ajustado também em 320×240 | OK |
| Skills: troca de ramo (inclusive com a cena inclinada), arrastar, compra validada pelo servidor (pontos 4→3→2→1) em GUI 1, 2 e 3 | OK |
| Roda: rola a placa da frente / gira o arco; hotbar intacta (slot 0 → 0) | OK |
| Reabrir 6× seguidas fechando no meio da abertura; redimensionar com o menu aberto; morte e ida ao Nether com o menu aberto; reconectar | OK, sem exceção |
| GUI 2 (1280×720) e GUI 3 (960×720 = 320×240); FOV 110 sem efeito no menu nem no clique | OK |
| Display: opacidade, alto contraste, movimento reduzido (fade de ~0,15 s), fundo do mundo; gravado no `.toml` | OK |
| Fundo do Rimulib: saturação média do chão 0,41 → 0,24 com o fundo ligado | OK |

Achados e corrigidos durante o teste real: Rimulib `UiApi.text` usa o `y` como linha de base; vidro cobrindo o jogador
(teste de profundidade desligado pelos `RenderType`); `ScreenFx.desaturate` sem efeito no 1.3.0 (trocado por passada
própria); coluna da aparência apertada em 320×240; na composição em arco, fileira de chips perto demais (saía da tela)
e holograma do jogador grande demais.

## 10. Limitações conhecidas

- Teste visual só em software (Xvfb + llvmpipe, sem GPU real) e sem som (sem OpenAL no Xvfb).
- O texto das placas inclinadas (vizinhas, fita, chips) passa por filtro linear; só a placa da frente em repouso é
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

## 12. PR

[Juann027/MineModding_01#35](https://github.com/Juann027/MineModding_01/pull/35) — **não integrado**: aguardando a
conferência visual do dono depois da correção de direção para a composição em arco.
