# HANDOFF — Etapa 4: animações avançadas do jogador

Continuação da Etapa 3.1. Nada foi reconstruído: rig, Animation Manager, Finisher Engine, câmera cinematográfica,
Body Layers, roleta, necessidades e HUD continuam iguais. A etapa entrega **animações reais** (keyframes),
uma ferramenta para produzi-las e uma finalização de dois participantes integrada ao registro existente.

- Base: `main` em `9fea6c2` (merge do PR #20). Versão `0.5.6` (sem mudança → sem Release).
- Branch: `claude/affectionate-goodall-yj8ht6`.

## Rig utilizado (sem alterações)

`assets/tupinicraft/geo/player/tupinicraft_animated_player.geo.json` (fonte: `model_sources/player/tupinicraft_animated_player.bbmodel`).

```
Root (0,0,0) ─ CenterOfMass (0,12,0) ─ Waist (0,12,0)
  ├─ Body (0,12,0) ─ Neck (0,24,0) ─ Head (0,24,0) [HatLayer, HairAttachment, HeadAccessoryAttachment]
  │                ├─ RightClavicle (-4,22,0) ─ RightArm (-6,22,0) [Classic | Slim | camadas | RightHandAttachment]
  │                ├─ LeftClavicle  ( 4,22,0) ─ LeftArm  ( 6,22,0) [Classic | Slim | camadas | LeftHandAttachment]
  │                └─ FemaleBust, BodyAccessoryAttachment
  ├─ RightHip (-1.9,12,0) ─ RightLeg
  └─ LeftHip  ( 1.9,12,0) ─ LeftLeg
```

Os clips da Etapa 4 animam só `Root` (rotação e posição), `Body`, `Head`, `RightArm`, `LeftArm`, `RightLeg` e
`LeftLeg`. Braços Classic/Slim são filhos do mesmo bone, então um clip serve para as duas variantes; cabelo e busto
(Body Layers) acompanham a pose escrita de volta no `PlayerModel`, assim como armadura e itens.

### Convenções conferidas no jar do GeckoLib 4.9.3

- Rotação do keyframe: graus → radianos com **X e Y invertidos, Z mantido** (`BakedAnimationsAdapter`);
  posição: `translate(-x, y, z)/16` (`RenderUtil.translateMatrixToBone`); ordem Z·Y·X em torno do pivô.
- O geo é espelhado em X ao ser assado (pivô `(-x, y, z)`). No espaço assado o personagem olha para −Z, a direita dele
  é +X.
- No arquivo (como no Blockbench): `Body/Head/Root x+` inclina para a frente; `y+` gira o ombro direito para trás
  (olha para a direita); `z+` inclina para a esquerda. Braços `x−` levantam para a frente; `RightArm z+`/`LeftArm z−`
  abrem; com o braço à frente, `RightArm y−`/`LeftArm y+` fecham para dentro. Pernas `x−` à frente, `x+` atrás.
  `Root` posição: `z+` para trás, `y+` para cima.
- Curvas: o `easing` de um keyframe vale para o trecho que **chega** nele (`easeInOutSine`, `easeOutCubic`,
  `easeInQuad`, `catmullrom`, ...).
- O `PlayerRenderer` escala o rig por 15/16: 1 bloco no mundo = 17,07 px do rig.

## Animações criadas

Arquivo: `assets/tupinicraft/animations/player/tupinicraft_player_combat.animation.json`
(constante `PlayerAnimations.COMBAT_FILE`). Ids `tupinicraft:<nome>`, clips `animation.tupinicraft.player.<nome>`.
Todos usam `FULL_BODY`.

| Clip | Ticks | Loop | Conteúdo |
|---|---|---|---|
| `combat_idle` | 40 | sim | guarda ortodoxa (pé esquerdo à frente, corpo meio de lado, punhos no queixo) com respiração |
| `punch` | 26 | — | guarda → antecipação (giro para trás) → direto com avanço (impacto t13) → follow-through → neutra |
| `kick` | 34 | — | guarda → recolhe a perna → chute frontal (impacto t14) → recolhe → pousa → neutra |
| `hit_reaction` | 20 | — | dobra para a frente (cabeça e braços um tick atrasados) e se recompõe |
| `stagger` | 34 | — | cambaleia para os lados, braços abertos, firma |
| `knockdown` | 34 | — | empurrado → tropeça → joelhos (t20) → mãos (t23) → assenta em quatro apoios |
| `kneel` | 60 | — | ajoelha nos dois joelhos, fica, levanta |
| `hands_and_knees` | 40 | sim | quatro apoios (pose neutra reutilizável), respirando |
| `crawl` | 24 | sim | engatinhar no lugar (diagonais alternadas) |
| `get_up` | 40 | — | quatro apoios → empurra → planta o pé → um joelho → levanta |
| `finisher.combo_attacker` | 140 | — | executor da finalização "combo" |
| `finisher.combo_victim` | 140 | — | alvo da finalização "combo" |

`knockdown` termina exatamente na pose de `hands_and_knees`, que é a mesma do início de `get_up`.

## Finalização "combo" (dois participantes)

`Finishers.COMBO` (`tupinicraft:combo`, peso 3, sem requisito de habilidade, `recoveryInScene = true`).

| Tick | Executor | Alvo | Evento |
|---|---|---|---|
| 0–17 | entra na guarda | percebe, recua o peso, levanta as mãos | `sequence_start`, som de roupa (preparação) |
| 17–22 | antecipação do soco | encolhe (flinch) | 19 `whoosh` (golpe) |
| 25 | direto com avanço | dobra e recua | `finisher_hit 1`, tremor (impacto) |
| 27–40 | recolhe | cambaleia para a frente, grogue | — |
| 39–43 | recolhe a perna, chute frontal | recebe o pé | 37 `whoosh`, 43 `finisher_hit 2`, tremor, FOV |
| 44–62 | pousa e recua meio passo para a guarda | empurrado para trás, tropeça, joelhos no chão | 62 `finisher_fall 2,0.4` (poeira + baque), tremor |
| 65–98 | guarda olhando para baixo | mãos no chão, quatro apoios, respira, levanta o olhar | 65 `finisher_fall 1,0.1` |
| 98–138 | volta a encarar, passo à frente | levanta passando por um joelho, passo à frente | 98 som (recuperação), 124 som |
| 133–140 | baixa a guarda, neutra | neutra | 139 `sequence_end` (encerramento) |

- Sincronização: os dois clips têm 140 ticks e são iniciados pelo `PlayerAnimationService.playTogether` existente
  (mesmo `startGameTime`); o servidor dispara os eventos de cada um pelo mesmo relógio.
- Espaço: o palco continua sendo da sessão (`FinisherTestMechanic`: alvo a 1,15 bloco, frente a frente, ambos presos
  à âncora). Os keyframes só têm deslocamentos de corpo que **voltam a zero**: avanço do soco (4 px), empurrão do
  chute (6,5 px, devolvido com um passo no fim do `get_up`) e recuo do executor (3,5 px, devolvido com um passo).
  Hitboxes não mudam.
- Câmera: trilha própria em `CameraTrack` (estabelecimento → lateral no soco → baixa no chute → alta na queda →
  órbita lenta → saída). Tremor e FOV pelos eventos existentes.
- No fim o alvo já está de pé: `recoveryInScene` faz o `complete()` pular o `knocked_down` de 10 s (as outras
  finalizações continuam como antes). Cooldowns e contador não mudaram.

### Mudanças no código (mínimas)

- `PlayerAnimations`: `COMBAT_FILE` e os 10 clips da biblioteca (só dados).
- `Finishers`: `COMBO` e o tipo de evento `EVENT_FALL` (`finisher_fall`, dados `força,recuo`).
- `FinisherDef`: campo `recoveryInScene` (construtor antigo de 6 argumentos mantido, vale `false`).
- `FinisherTestMechanic`: trata `finisher_fall` (partículas do bloco do chão + `POOF` + som de queda) ao lado do
  `finisher_hit`; com `recoveryInScene` não deixa a vítima caída.
- `SelfTest`: o sorteio sem habilidades agora só exige finalizações sem requisito; checagens do combo e da biblioteca.

## Ferramenta de autoria (`tools/player_anim`)

```bash
python3 tools/player_anim/export.py                 # regrava o .animation.json e os .bbmodel e mostra o relatório
python3 tools/player_anim/export.py --sheets /tmp/a  # também gera folhas de quadros (PNG) para conferir
```

| Arquivo | Papel |
|---|---|
| `rig.py` | cinemática direta lendo o geo real, com as convenções do GeckoLib; amostra clips com as mesmas curvas |
| `build.py` | `P(...)` (pose), `grounded(...)` (apoia os cubos de contato no chão), `Clip.key/segment` (com defasagem por bone) |
| `poses.py` | poses-chave: guarda, soco, chute, reações, quatro apoios, ajoelhado |
| `clips.py` | biblioteca + coreografia; `fix_ground` insere chaves do `Root` onde a interpolação afundaria no chão |
| `export.py` | grava tudo e valida: penetração no chão por clip, distância do punho/pé ao tronco do alvo no impacto, interpenetração entre os dois |
| `preview.py` | prévia por software (cores por parte, sem skin) |
| `bbmodel.py` | grava os `.bbmodel` |

Relatório atual: nenhum clip passa de 0,3 px abaixo do chão; soco a 0,0 px e chute a 1,3 px do tronco do alvo no tick
do impacto; maior encosto fora dos impactos ≈ 2 px (punho da guarda × cabeça do alvo dobrado, logo após o soco).

## Blockbench

- `model_sources/player/tupinicraft_player_combat.bbmodel`: rig + os 12 clips (formato 5.0, plugin GeckoLib).
- `model_sources/player/tupinicraft_combo_scene.bbmodel`: **dois rigs** (executor na origem; alvo `Alvo_*` dentro do
  grupo `Alvo_Palco`, girado 180° a 17,07 px = 1 bloco) com a animação `combo_scene`. Só para ajustar o tempo entre os
  dois: **não exporte o geo dele**.
- Os `.bbmodel` foram gerados por script e **não foram abertos no Blockbench nesta sessão** (não havia Blockbench). O
  formato segue o do `.bbmodel` existente do rig; o que foi validado é o `.animation.json`, carregado pelo GeckoLib
  em jogo.

### Editar e exportar

1. Abra `tupinicraft_player_combat.bbmodel` (plugin GeckoLib instalado), aba Animate, escolha o clip.
2. Edite **apenas** os bones já usados (ou outros do rig: os nomes precisam existir no geo). Não mexa em pivôs nem
   em cubos: o geo do jogo não é reexportado daqui.
3. File → Export → *Export Animations* (GeckoLib) para
   `src/main/resources/assets/tupinicraft/animations/player/tupinicraft_player_combat.animation.json`.
4. Se mudar a duração, atualize os ticks no registro Java (o servidor não lê assets); o cliente avisa no log quando a
   duração registrada difere da do clip.
5. Ou se edita no Blockbench, ou em `clips.py`. Desde a Etapa 4.1 o `export.py` não sobrescreve edições externas
   (veja `tools/player_anim/README.md`); os `.bbmodel` desta etapa estavam espelhados no editor e foram corrigidos
   na 4.1 (`HANDOFF_PLAYER_ANIMATIONS_STAGE4_1.md`).

### Registrar um clip novo

```java
public static final PlayerAnimationDef REVERENCIA = register(library("reverencia", 30, 3, 10).build()); // em PlayerAnimations
```

O clip `animation.tupinicraft.player.reverencia` precisa existir em `COMBAT_FILE`. Teste com
`/tupinianim play <jogador> tupinicraft:reverencia`.

### Criar outra coreografia de dois participantes

1. Em `clips.py`, crie dois `Clip` com o **mesmo** comprimento e monte as poses com `segment(...)`, reutilizando
   `knockdown_frames`/`get_up_frames` e as poses de `poses.py`. Use `shift(pose, dz)` para deslocamentos de corpo e
   faça-os voltar a zero até o fim.
2. Ajuste `export.py` (placements/impactos) para validar a nova cena e rode `export.py --sheets`.
3. Registre em `Finishers` um `FinisherDef` com as duas animações (`FULL_BODY`), eventos `finisher_hit` no executor
   nos ticks de contato, `finisher_fall`/`camera_shake`/`camera_fov`/`sound` onde fizer sentido, uma `CameraTrack` e
   `recoveryInScene` conforme o fim do clip do alvo.
4. `/tupini admin finisher <executor> <alvo> <id>` inicia sem convite (teste).

## Testes realmente executados

| Teste | Resultado | Evidência (`docs/evidencias/etapa4/`) |
|---|---|---|
| Ferramenta: chão, contato dos golpes, interpenetração (todos os clips, todos os ticks) | OK (números acima) | 05, 06 |
| `./gradlew build` | OK | — |
| Teste de fumaça do servidor dedicado (autoteste com as novas checagens, lista de animações e finalizações) | OK | — |
| Cliente real (Xvfb, llvmpipe), servidor dedicado, **dois clientes**: Dev (skin **Slim**) e Juca (**Classic**) | — | — |
| Clips da biblioteca por `/tupinianim play` em Dev, vistos por Juca: punch, kick, knockdown, hands_and_knees, get_up | OK | 01 |
| Combo Dev × Juca: início junto, câmera cinematográfica, impacto com partículas, poeira da queda, quatro apoios, levantar, câmera de volta à primeira pessoa no fim | OK | 02, 03 |
| Mesmo instante visto pelos dois clientes (chute, queda) | poses coerentes | 03 |
| Cancelamento por Shift do alvo no meio da queda: cena para, os dois em pé, câmera restaurada | OK | 04 |

Não testado: Blockbench (indisponível); som (sem OpenAL no Xvfb, os eventos são os mesmos já usados); `crawl`, `kneel`,
`stagger`, `hit_reaction` e `combat_idle` em jogo (só na prévia por software; mesmo caminho dos outros clips);
cabelo e busto durante o combo (os personagens de teste não tinham essas peças ativas; o mecanismo é o mesmo validado
na Etapa 2); armadura e itens na mão durante o combo; GPU real; mais de dois clientes.

## Limitações conhecidas

- Não há encadeamento automático de clips: tocar `knockdown` e depois `hands_and_knees` por comandos separados mostra
  a pose vanilla por um instante entre eles. Cenas completas devem ser um clip só por participante (como o combo).
  **Resolvido na Etapa 4.1** com `AnimationSequence`/`playSequence`.
- `crawl` anima no lugar; quem quiser deslocamento no mundo precisa mover o jogador pelo servidor.
- Pernas sem joelho: ajoelhar e quatro apoios são aproximações no estilo Minecraft (coxa inclinada, perna deitada).
- Logo após o soco o punho da guarda do executor encosta ~2 px na cabeça do alvo dobrado.
- Os empurrões são visuais (keyframes do `Root`); a hitbox fica na âncora da sessão.
- `combat_idle` é `FULL_BODY` (postura parada); andar com ele trava as pernas na base.
