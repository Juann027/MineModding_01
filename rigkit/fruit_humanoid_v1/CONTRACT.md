# Contrato técnico — `respectcraft:fruit_humanoid_v1` (versão 1)

Família humanoide de referência do Custom Rig Framework do RespectCraft (Etapa 4.12). Serve de base para mobs
antropomórficos (as futuras frutas) que **compartilham animações**. A fonte de verdade é o manifesto
[`fruit_humanoid_v1.rig.json`](fruit_humanoid_v1.rig.json) (o mesmo arquivo que o mod lê e que o validador confere);
este documento só o explica. Se os dois divergirem, vale o `.rig.json`.

Alvo de exportação: **Blockbench (Web ou desktop) + plugin "GeckoLib Animation Utils"**, formato
**GeckoLib Animated Model** (tipo *Entity*), consumido pelo **GeckoLib 4.9.3** no Minecraft 1.21.1 / NeoForge.

## 1. Unidades, eixos e orientação

| Item | Valor |
|---|---|
| Unidade | pixel do Blockbench; **16 px = 1 bloco** |
| Origem | `root` em `[0, 0, 0]` = chão, entre os pés (posição da entidade) |
| Para cima | +Y |
| **Frente do modelo** | **−Z (norte no Blockbench)**: o rosto olha para −Z |
| Direita do modelo | **+X** (o braço direito fica em X positivo no Blockbench) |
| Altura de referência | 32 px (2,0 blocos) até o topo da cabeça |
| Escala no jogo | 1:1 (`render_scale` 1.0); mude no binding, não no modelo |

O `.geo.json` exportado guarda X **invertido** (convenção Bedrock/GeckoLib): o braço direito aparece com pivô X
negativo no arquivo. Isso é normal; o validador desinverte antes de comparar. Os keyframes também são invertidos na
exportação (rotação X e Y, posição X) — o plugin faz isso sozinho, não corrija à mão.

## 2. Árvore de bones

```
└── root  (REQ) pivot [0, 0, 0]
    └── pelvis  (REQ) pivot [0, 12, 0]
        ├── torso  (REQ) pivot [0, 13, 0]
        │   ├── head  (REQ) pivot [0, 24, 0]
        │   │   ├── face  (opt) pivot [0, 28, -4]
        │   │   │   ├── brow_right  (opt) pivot [1.5, 30, -4]
        │   │   │   ├── brow_left  (opt) pivot [-1.5, 30, -4]
        │   │   │   ├── eye_right  (opt) pivot [1.5, 28.5, -4]
        │   │   │   ├── eye_left  (opt) pivot [-1.5, 28.5, -4]
        │   │   │   └── mouth  (opt) pivot [0, 26, -4]
        │   │   └── head_shell  (opt) pivot [0, 24, 0]
        │   ├── torso_shell  (opt) pivot [0, 13, 0]
        │   ├── torso_core  (opt) pivot [0, 13, 0]
        │   ├── right_arm  (REQ) pivot [5, 22, 0]
        │   │   └── right_forearm  (REQ) pivot [5.5, 17, 0]
        │   │       └── right_hand  (REQ) pivot [5.5, 12.5, 0]
        │   └── left_arm  (REQ) pivot [-5, 22, 0]
        │       └── left_forearm  (REQ) pivot [-5.5, 17, 0]
        │           └── left_hand  (REQ) pivot [-5.5, 12.5, 0]
        ├── right_thigh  (REQ) pivot [2, 12, 0]
        │   └── right_shin  (REQ) pivot [2, 6.5, 0]
        │       └── right_foot  (REQ) pivot [2, 1.5, 0]
        └── left_thigh  (REQ) pivot [-2, 12, 0]
            └── left_shin  (REQ) pivot [-2, 6.5, 0]
                └── left_foot  (REQ) pivot [-2, 1.5, 0]
```

Pivôs em coordenadas do Blockbench (campo *Pivot Point* do grupo). Os nomes são **exatos** (minúsculas,
`snake_case`). Cada pivô é a **articulação**: ombro, cotovelo, pulso, quadril, joelho, tornozelo, pescoço.

| Bone | Obrigatório | Papel |
|---|---|---|
| `root` | sim, **pivô exato** | origem da entidade; animações compartilhadas nunca o animam |
| `pelvis` | sim | quadril/centro de massa; o **único** bone que animações compartilhadas podem transladar |
| `torso` | sim | cintura; carrega peito, braços e cabeça |
| `head` | sim | pescoço |
| `right_arm` / `left_arm` | sim | ombros |
| `right_forearm` / `left_forearm` | sim | cotovelos |
| `right_hand` / `left_hand` | sim | pulsos |
| `right_thigh` / `left_thigh` | sim | quadris |
| `right_shin` / `left_shin` | sim | joelhos |
| `right_foot` / `left_foot` | sim | tornozelos |
| `face`, `brow_*`, `eye_*`, `mouth` | não | expressões faciais (rotação/posição pequenas, escala) |
| `head_shell`, `torso_shell` | não | **parte `shell`**: camada externa (casca, pele, placa) — visível por padrão, destacável |
| `torso_core` | não | **parte `core`**: componente interno (polpa, reator) — oculto por padrão |

Bones obrigatórios **podem não ter cubos** (articulação vazia). Bones opcionais podem faltar: o que os anima é
ignorado pelo GeckoLib nesse modelo.

## 3. Bones livres (o que cada modelo pode acrescentar)

Qualquer bone novo precisa de um destes prefixos (senão o validador avisa, e o mod recusa no binding):

- `extra_` — componentes próprios do modelo (ex.: `extra_crown` do abacaxi, `extra_stem`, `extra_hair_01`). Podem ficar
  sob qualquer bone. Animações compartilhadas nunca os tocam; declare-os em `extra_bones` no binding.
- `socket_` — marcadores de socket (bones **vazios**, ver §5).
- `expr_` — variantes de expressão (ex.: `expr_mouth_open`, `expr_mouth_smirk`) para mostrar/ocultar como partes.

## 4. Partes manipuláveis (ocultar / expor / destacar)

| Parte | Bones | Padrão | Destacável |
|---|---|---|---|
| `shell` | `torso_shell`, `head_shell` | visível | sim |
| `core` | `torso_core` | oculto | não |

Ocultar um bone oculta também os filhos (GeckoLib `setHidden`). Coloque a geometria da casca **dentro** de
`*_shell` e a do interior **dentro** de `torso_core`; o corpo "normal" fica nos bones obrigatórios. Partes novas de
um modelo (ex.: `leaf` → `extra_leaf`) vão no binding (`parts`). Uma parte **nunca** pode conter bone obrigatório.

Em jogo a troca é só visual, sincronizada e temporária: animações podem pedir `rig:hide:<parte>`,
`rig:show:<parte>` ou `rig:reset` no *timeline* (Effects → Timeline no Blockbench, por exemplo
`rig:hide:shell;rig:show:core;`). Ao fim (ou cancelamento) da cena o mod devolve o estado anterior.

## 5. Sockets (pontos semânticos)

| Socket | Bone | Deslocamento (px) | Uso |
|---|---|---|---|
| `head` | head | [0, 4, 0] | topo/centro da cabeça |
| `chest` | torso | [0, 6, −2.5] | peito (frente) |
| `right_hand` / `left_hand` | *_hand | [0, −2, 0] | palma (segurar, agarrar) |
| `grab_point` | torso | [0, 8, −2] | gola, onde o executor agarra |
| `impact_point` | torso | [0, 5, −3] | onde o golpe acerta (partículas de impacto) |
| `ground_contact` | root | [0, 0, 0] | contato com o chão |
| `vfx_core` | torso | [0, 4, 0] | emissão de efeitos do núcleo |
| `weapon_right` | right_hand | [0, −3, −1] | encaixe de arma |

Posição = pivô do bone + deslocamento (ambos × proporção do modelo). Marcadores opcionais: um grupo **vazio**
`socket_<nome>` filho do bone, com o pivô na posição do socket (o template já tem todos). Locators do Blockbench não
servem: o GeckoLib 4.9.3 lê mas não os leva ao modelo. Se o seu modelo precisar de um socket em outro lugar, mova o
marcador e declare o override em `sockets` no binding.

**Servidor × cliente.** O servidor só conhece a **pose de repouso** (o GeckoLib não roda animações no servidor):
socket no mundo = posição da entidade + pose de repouso girada pelo corpo. Usado para efeitos e alinhamento, nunca para
dano ou colisão exatos. A pose **animada** só existe no cliente (`GeoBone.getWorldPosition()` dos marcadores,
apenas visual).

## 6. Proporções diferentes (frutas com formatos distintos)

Cada modelo declara `proportion: [sx, sy, sz]` no binding: os pivôs esperados são os do contrato × proporção
(o `root` nunca escala). Exemplos: banana alta e magra `[0.9, 1.15, 0.9]`; melancia larga `[1.4, 1.0, 1.3]`.

Regras para não quebrar o contato nas finalizações:

1. **Pivôs nas articulações** (± `pivot_tolerance` = 2 px depois da proporção). A forma em volta é livre.
2. Animações compartilhadas usam **rotações**; translação só no `pelvis`, pequena e voltando a zero.
3. O palco da cena (distância executor–vítima) é por modelo: `alignment.distance` no binding (ex.: melancia 1.6).
4. Mãos e pontos de golpe por modelo: mova os marcadores `socket_*` e/ou declare `sockets` no binding.
5. Caixa de colisão (largura/altura da entidade) acompanha a silhueta; o validador avisa se a altura do modelo
   ficar mais de 20% longe de `32 × sy`.

O que **não** existe: retargeting automático. Se a animação compartilhada não fica boa num modelo, faça um clip
próprio dele (`clip_overrides`) — compatibilidade é sempre explícita.

## 7. Texturas e UV

- PNG RGBA; tamanho livre (múltiplo de 16 recomendado: 64×64, 128×128). O tamanho declarado no geo
  (`texture_width/height`, *Project → UV size*) deve ser igual ao do PNG.
- Box UV ou UV por face, os dois servem. Sem texturas animadas (o mod não usa `.mcmeta` nas entidades).
- Partes que brilham: `nome_glowmask.png` só se o renderer do mob pedir (não é padrão da família).
- Um arquivo por modelo: `textures/entity/<entidade>.png`.

## 8. Animações

- Nomes: compartilhadas `animation.fruit_humanoid_v1.<chave>`; próprias de um modelo
  `animation.<entidade>.<chave>` (ex.: `animation.banana.takedown_victim`).
- Compartilhadas hoje (arquivo `animations/rig/fruit_humanoid_v1.animation.json`):

  | Chave | Duração | Laço | Uso |
  |---|---|---|---|
  | `idle` | 40 ticks (2 s) | sim | parado |
  | `finisher.takedown_victim` | 60 ticks (3 s) | não | vítima do Takedown (golpes em 0,7 / 1,3 / 2,2 s) |
  | `finisher.demo_peel` | 60 ticks (3 s) | não | cena de teste das partes (troca casca/núcleo em 1,1 s) |

- Clip de finalização tem **exatamente** a duração da cena (o mod recusa a cena se não tiver).
- 20 ticks = 1 s. Controlador GeckoLib da família: **`main`** (o mob registra as animações como gatilho nele).
- Animações compartilhadas só animam os bones de `shared_animation_bones` (todos os obrigatórios menos `root`, mais
  os do rosto).

## 9. Exportação para GeckoLib 4.9.3

1. *File → Export → Export GeckoLib Model* → `geo/entity/<entidade>.geo.json`.
2. *Animation → Export Animations* → `animations/entity/<entidade>.animation.json` (só os clips próprios).
3. *Texture → Save As* → `textures/entity/<entidade>.png`.
4. Guarde o `.bbmodel` (fonte editável).
5. Rode o validador (veja o README do kit) e entregue os quatro arquivos + o binding.

## 10. Pode mudar livremente × quebra a compatibilidade

**Livre:** toda a geometria (cubos, tamanhos, rotação de cubos, inflate), cores e textura, UV, quantos cubos quiser
em cada bone, bones `extra_`/`expr_`/`socket_`, proporção (declarada), posição dos marcadores de socket (com
override), animações próprias, rotação inicial de bones **opcionais**.

**Quebra (o validador acusa erro):** renomear ou remover um bone obrigatório; mudar o pai de um bone obrigatório;
mover o pivô do `root`; trocar esquerda e direita / virar o modelo para +Z; clip de finalização com duração diferente
da cena; parte contendo bone obrigatório; bone extra sem prefixo no binding.

**Degrada (aviso):** pivô de articulação fora da tolerância; bone opcional sob outro pai; animação compartilhada
transladando outro bone que não o `pelvis`; altura muito diferente da proporção; textura de tamanho diferente do geo;
rotação inicial em bone obrigatório (as animações compartilhadas somam a partir dela — evite).

Mudanças que quebram o contrato viram uma **versão nova** (`contract_version`, ou uma família `..._v2`).
