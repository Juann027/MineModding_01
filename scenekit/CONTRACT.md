# Finisher Scene Authoring Kit — contrato v1

Um **projeto de autoria** (`.bbmodel`, GeckoLib Animated Model) com dois participantes de uma finalização do
RespectCraft no mesmo palco. Serve só para animar os dois juntos; o jogo recebe **um clip por participante**, separados
pelo `split_scene.py`. A geometria conjunta **nunca** vai para o jogo.

## Papéis

| Papel | Quem | Prefixo na cena | Destino do clip |
|---|---|---|---|
| ATTACKER | sempre o jogador (rig oficial `respectcraft_animated_player`) | `ATK_` | arquivo de jogador do kit (ex.: `animations/player/respectcraft_authored_scenes.animation.json`), registrado como `PlayerAnimationDef` |
| VICTIM | CustomRig de qualquer família (morango, manequim...) **ou** outro jogador (PvP) | `VIC_` | CustomRig: o `<entidade>.animation.json` + `clip_overrides` do binding; jogador: o mesmo arquivo de jogador |

Os prefixos são temporários: o manifesto (`<cena>.scene.json`) guarda o mapa explícito `nome na cena → (papel, bone
original)`. Renomear, criar ou apagar bones no Blockbench quebra o mapa (o split acusa `ERROR`).

## Palco (convenções reais do Finisher Engine)

* Referencial = px do rig do ATTACKER; frente `-Z`, `+X` = direita, pés em `y = 0`.
* Jogador: o `PlayerRenderer` desenha o rig a 15/16 → 1 bloco = **17,0667 px**. CustomRig: 1 bloco = 16 / `render_scale` px.
* VICTIM a `distance` blocos à frente e `lateral` à direita, olhando para o ATTACKER (yaw + 180 + `yaw_offset`), como
  `FinisherTestMechanic.start` / `FinisherParticipants.stage`. Distância: `stage` da spec > `alignment` do binding >
  da família > automática `max(1,15; (largura A + largura V)/2 + 0,25)`.
* Grupos de palco (não são bones, `export: false`, nunca exportados):
  * `STAGE_ATTACKER` — contém o rig do jogador sem mudança.
  * `STAGE_VICTIM` — gira a vítima (o ponto de giro é calculado para levar os pés dela à âncora; nenhuma coordenada
    do rig muda).
  * `STAGE_VICTIM_SCALE` — só quando as escalas diferem (jogador × CustomRig): **um keyframe de escala constante** em
    cada animação da cena (16/15 para `render_scale` 1). Ele é calibração: o split o reconhece e descarta. Animação
    nova na cena? Copie esse keyframe (ou crie a animação com o `compose_scene.py`).
* **Movimento visual × movimento real:** a sessão segura as duas entidades na âncora. Animar `STAGE_*` (a entidade
  andando) é `ERROR`. Deslocar a raiz (`Root`/`root`) é visual e deve voltar a zero no fim (senão `WARN`: o modelo
  "salta" de volta quando o clip acaba).

## O que é preservado

Bones, hierarquia, pivôs, rotações, visibilidade, cubos (from/to/origin/rotação/inflate/box UV/mirror), sockets
(`socket_*`), expressões (`expr_*`), partes destacáveis e extras. UUIDs são novos (determinísticos) e únicos. Os
modelos de origem só são lidos.

**Texturas:** padrão = **atlas** só do projeto de autoria (as texturas empilhadas 1 px = 1 UV, UVs por face e box UV
deslocados para a região de cada uma). **O atlas é obrigatório:** no Blockbench 5.2.2 o formato GeckoLib não tem UV
por textura (conferido no Blockbench real pelo CI), então `--textures multi` (texturas originais separadas) só serve
de diagnóstico: o editor lê todas na resolução do projeto e as UVs da vítima ficam erradas. Texturas do jogo continuam as
originais: nada da cena é exportado como textura.

## Timeline (aba *Effects* → canal *timeline*)

Uma linha = um evento, **sempre com dono**. Vários eventos no mesmo instante = várias linhas no mesmo keyframe.

| Linha | Dono permitido | Vai para |
|---|---|---|
| `@attacker hit 1..3` | attacker | `Finishers.EVENT_HIT` do clip do executor (impacto; marcador dos efeitos do Calamidade) |
| `@victim fall <força 1..3>,<recuo>` | victim | `Finishers.EVENT_FALL` do clip da vítima (poeira/baque; marcador) |
| `@attacker shake a,t` / `@victim shake a,t` | um participante | `PlayerAnimations.EVENT_CAMERA_SHAKE` da câmera **daquele** jogador |
| `@attacker fov m,t` / `@victim fov m,t` | um participante | `PlayerAnimations.EVENT_CAMERA_FOV` |
| `@attacker sound <id>` (ou `@victim`) | um só | `PlayerAnimations.EVENT_SOUND` (o servidor toca para todos: nunca nos dois) |
| `@victim rig:hide:<parte>` · `rig:show:<parte>` · `rig:reset` | participante CustomRig | keyframe de timeline do clip do mob (`RigVisuals`, servidor): expressões, rachaduras, polpa, ocultar partes |
| `@victim calamidade:<efeito>` | participante CustomRig | keyframe de timeline do mob (`GeoFxKeyframes`, servidor) |
| `@scene camera yaw,dist,altura,olhar,fov,roll` | scene | `CameraTrack.Key` da finalização |
| `@scene execute` | scene | marcador do golpe final real: **só no último tick** (a execução acontece no fim da cena) |
| `@<dono> note <tipo> <texto>` | qualquer | só relatório (`grab`, `release`, `reaction`, `contact`, `expression`, `crack`, `pulp`, `hide`, `camera`, `audio`, `vfx`, `movement`, `other`) |

Regras: o mesmo `hit`/`fall`/`sound`/instrução em dois donos no mesmo tick é `ERROR` (dispararia duas vezes); `rig:`/
`calamidade:` em jogador é `ERROR` (jogador usa `hit`/`fall` e o `FinisherFxDef`); keyframe fora do tick (20 fps) é
`ERROR`; keyframes nos canais *sound*/*particle* do Blockbench são `ERROR` (não têm dono: escreva a linha na timeline).
Linhas começando com `//` são comentários; o `;` que o plugin acrescenta ao fim de cada linha no export é aceito. Em `rig:` use troca explícita (`hide`/`show`) no meio da cena: `rig:reset`
devolve **todas** as partes ao padrão. A sessão restaura as partes no fim, no cancelamento e no golpe recusado.

Nada é API nova: são os eventos que o Finisher Engine já entende. Eventos de jogador viram linhas Java prontas no
relatório (`.events.txt`, `export_meta.json` → `java`) para o registro da finalização.

## Arquivos

| Arquivo | Quem escreve | Para quê |
|---|---|---|
| `scene.spec.json` | à mão | entrada do `compose_scene.py` (modelos, prefixos, entidade, binding/contrato, larguras, palco, duração, animações, contatos) |
| `<cena>.bbmodel` | `compose_scene.py`, depois o Blockbench | projeto de autoria |
| `<cena>.scene.json` | `compose_scene.py` (não edite) | manifesto: mapa de bones, palco, partes, sockets, contatos |
| `<cena>.animation.json` | *Export Animations* do plugin GeckoLib | entrada **preferida** do `split_scene.py` (mantém bezier, step, catmullrom, Molang) |
| `<anim>.attacker/victim.animation.json`, `.export_meta.json`, `.events.txt`, `.validation.txt` | `split_scene.py` | clips separados (nomes originais dos bones), metadados (duração, palco, eventos, Java) e relatório |

Nome dos clips: `<animation_prefix do participante><animação da cena sem "scene.">_<papel>`, ex.
`animation.strawberry.scene.player_vs_strawberry_demo_victim`. Duração: a da animação da cena, igual nos dois, em
ticks inteiros (`duration_ticks` da spec).

## Spec (`scene.spec.json`)

```json
{
  "format": "respectcraft:finisher_scene", "format_version": 1,
  "name": "respectcraft_player_vs_strawberry_template",
  "animation": "scene.player_vs_strawberry",
  "animations": ["scene.player_vs_strawberry", "scene.player_vs_strawberry_demo"],
  "duration_ticks": 60,
  "attacker": {"kind": "player", "prefix": "ATK_", "bbmodel": "...", "hitbox_width": 0.6,
               "export": {"file": "respectcraft:animations/player/...json", "animation_prefix": "animation.respectcraft.player.scene."}},
  "victim": {"kind": "custom_rig", "prefix": "VIC_", "bbmodel": "...", "entity": "respectcraft:strawberry",
             "rig_id": "respectcraft:fruit_humanoid_v1", "rig": "...rig.json", "binding": "...strawberry.json",
             "geo": "respectcraft:geo/entity/strawberry.geo.json", "hitbox_width": 0.8,
             "export": {"file": "respectcraft:animations/entity/strawberry.animation.json", "animation_prefix": "animation.strawberry.scene."}},
  "stage": {"distance": null, "lateral": 0, "yaw_offset": 0},
  "contacts": [{"event": "hit", "attacker_bone": "RightHandAttachment", "victim_bone": "socket_impact_point", "max_px": 10}]
}
```

PvP: `victim.kind = "player"` com o mesmo `.bbmodel` do jogador (sem grupo de escala). Qualquer CustomRig compatível
entra pelo seu `.bbmodel` + contrato/binding (família, proporção e número de bones livres). `hitbox_width` = largura da
caixa de colisão (`EntityType.sized`), usada na distância automática.
