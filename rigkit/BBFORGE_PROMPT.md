# Prompt para o BbForgeVersionPro (copie tudo abaixo da linha)

Anexe junto: `fruit_humanoid_v1_template.bbmodel`, `CONTRACT.md` e `fruit_humanoid_v1.rig.json` (do kit) e as suas
imagens de referência.

---

Você vai modelar, texturizar e (se eu pedir) animar um mob para o mod de Minecraft **RespectCraft**
(Java 1.21.1, NeoForge, **GeckoLib 4.9.3**) no **Blockbench Web**, usando o **BbForgeVersionPro**. O mob segue um
**contrato de rig** fixo, porque vai compartilhar animações com outros mobs da mesma família. Respeitar o contrato é
mais importante que qualquer detalhe artístico. Anexei o template (`fruit_humanoid_v1_template.bbmodel`), o contrato
(`CONTRACT.md`, `fruit_humanoid_v1.rig.json`) e as imagens de referência.

## Mob desta sessão

- Nome/id da entidade: `<preencha: ex. banana>` (minúsculas, sem espaços)
- Descrição: `<preencha: ex. banana masculina, musculosa e arrogante>`
- Proporção em relação ao manequim (x = largura, y = altura, z = profundidade): `<ex. [0.9, 1.15, 0.9]>`
- Peças próprias desejadas: `<ex. cabinho, casca que descasca na frente>`

## Regras do contrato (obrigatórias)

1. **Formato:** projeto *GeckoLib Animated Model* (tipo Entity). Comece **abrindo o template**; nunca crie o esqueleto
   do zero. Unidade: 16 px = 1 bloco. Pés no chão em y = 0, entre os pés em x = 0, z = 0.
2. **Orientação:** a frente do modelo (rosto, peito) olha para **−Z (North)**. O lado **direito do personagem** fica em
   **+X**. Não espelhe nem gire o modelo inteiro.
3. **Bones obrigatórios** — mesmos nomes, mesma hierarquia, sem rotação nos grupos:

   ```
   root [0,0,0]  (pivô exato, nunca mova)
   └ pelvis [0,12,0]
     ├ torso [0,13,0]
     │ ├ head [0,24,0]
     │ ├ right_arm [5,22,0] → right_forearm [5.5,17,0] → right_hand [5.5,12.5,0]
     │ └ left_arm [-5,22,0] → left_forearm [-5.5,17,0] → left_hand [-5.5,12.5,0]
     ├ right_thigh [2,12,0] → right_shin [2,6.5,0] → right_foot [2,1.5,0]
     └ left_thigh [-2,12,0] → left_shin [-2,6.5,0] → left_foot [-2,1.5,0]
   ```

   Os números são os *Pivot Points* do manequim (proporção 1). Com a proporção do mob, cada pivô vira
   `pivô × proporção` (o `root` não muda). Tolerância: 2 px. **Pivô = articulação**: ombro, cotovelo, pulso, quadril,
   joelho, tornozelo, pescoço — a geometria deve dobrar exatamente ali.
4. **Bones opcionais** (mantenha os nomes se usar): rosto `face` (filho de `head`) com `brow_right`, `brow_left`,
   `eye_right`, `eye_left`, `mouth`; casca externa `torso_shell` (filho de `torso`) e `head_shell` (filho de `head`);
   interior `torso_core` (filho de `torso`, começa oculto no jogo).
5. **Bones novos** só com prefixo: `extra_` (peças do mob: cabinho, folhas, cabelo, músculos soltos, acessórios),
   `expr_` (variantes de expressão para mostrar/ocultar), `socket_` (marcadores). Coloque cada peça dentro do bone que
   a carrega (ex.: `extra_stem` dentro de `head`). Peças que podem ser arrancadas/expostas devem ficar em grupos
   próprios (um grupo por peça destacável), nunca misturadas com os cubos de um bone obrigatório.
6. **Sockets:** mantenha os grupos vazios `socket_head`, `socket_chest`, `socket_right_hand`, `socket_left_hand`,
   `socket_grab_point`, `socket_impact_point`, `socket_ground_contact`, `socket_vfx_core`, `socket_weapon_right`
   (sem cubos). Se a geometria mudou, mova o marcador para o lugar coerente (palma da mão, gola, centro do peito) e me
   diga a nova posição de cada um que mudou.
7. **Geometria livre:** apague os cubos cinza do manequim e crie os seus **dentro dos mesmos grupos**. Pode girar,
   escalar e inflar cubos, usar quantos quiser. Nunca gire/mova os grupos obrigatórios para "encaixar" a forma.
8. **Animações compartilhadas** (`animation.fruit_humanoid_v1.*`, já no template): não edite. Use-as para conferir
   se as articulações dobram bem no seu modelo. Clips próprios do mob se chamam `animation.<id>.<chave>` e, se forem de
   finalização, têm **exatamente** a duração da cena (Takedown: 3,00 s). Use rotações; translação só no `pelvis`,
   pequena e voltando a zero no fim. Troca de partes vai em *Effects → Timeline*: `rig:hide:shell;`, `rig:show:core;`.
9. **Textura:** PNG, tamanho igual ao *UV size* do projeto (64×64 ou 128×128 recomendado), pixel art coerente com
   Minecraft. Box UV ou per-face UV.

## Fluxo a partir das imagens de referência

1. **Leitura:** descreva em poucas linhas silhueta, proporções, cores dominantes e as peças próprias que você vê nas
   imagens. Proponha a proporção `[x, y, z]` e confirme comigo antes de modelar.
2. **Ajuste do esqueleto:** multiplique os pivôs pela proporção (exceto `root`). Não renomeie nada.
3. **Blockout:** volumes grandes por bone (tronco, cabeça, membros), conferindo de frente (−Z), de lado e de cima.
   Toque `animation.fruit_humanoid_v1.idle` e `...finisher.takedown_victim` e confira que nada atravessa o corpo e
   que cotovelos/joelhos dobram no lugar certo.
4. **Detalhe:** peças `extra_`, rosto (`face` e filhos), casca (`*_shell`) e interior (`torso_core`).
5. **Sockets:** reposicione os marcadores necessários.
6. **Textura** e revisão visual nos quatro lados.
7. **Exportação:** *Export GeckoLib Model* (`<id>.geo.json`), *Export Animations* só com os clips próprios
   (`<id>.animation.json`), PNG (`<id>.png`) e o `.bbmodel`.

## Entrega

- `<id>.bbmodel`, `<id>.geo.json`, `<id>.png` e (se houver) `<id>.animation.json`.
- Um resumo: proporção usada, lista dos bones `extra_`/`expr_` (nome, pai, pivô), partes destacáveis (nome → bones),
  sockets que mudaram (nome → bone e deslocamento), duração de cada clip próprio.
- Se o validador do kit estiver disponível (`python3 tools/validate_rig.py --rig fruit_humanoid_v1.rig.json --geo
  <id>.geo.json --texture <id>.png --bbmodel <id>.bbmodel`), rode e corrija todo `ERROR`; explique cada `WARN` que
  ficar. Não afirme que o modelo é "100% compatível": o validador só confere a estrutura; diga o que conferiu
  visualmente e o que ficou para conferir no jogo.

Não modifique o template original nem as animações compartilhadas; não invente bones obrigatórios novos; não exporte
em formato Java Block/Item nem Bedrock Entity.
