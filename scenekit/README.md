# RespectCraft — Finisher Scene Authoring Kit (v1)

Anime **o jogador (ATTACKER) e a vítima (VICTIM) juntos** num único projeto do Blockbench Web e depois exporte um clip
independente para cada um. Funciona à mão, ou pelo Claude normal no Chrome com a skill `/blockbench-web-geckolib-v2`
(veja [`CLAUDE_CHROME.md`](CLAUDE_CHROME.md)). A separação e a integração no mod são feitas depois pelo Claude Code no
repositório. Contrato completo: [`CONTRACT.md`](CONTRACT.md).

## Conteúdo

| Caminho | O que é |
|---|---|
| `scenes/player_vs_strawberry/respectcraft_player_vs_strawberry_template.bbmodel` | jogador × morango (`respectcraft:strawberry`, modelo artístico da Etapa 4.13.1 com texturas, sockets, expressões e partes) |
| `scenes/player_vs_fruit_mannequin/respectcraft_player_vs_fruit_mannequin_template.bbmodel` | jogador × manequim de referência da família `fruit_humanoid_v1` (ponto de partida para qualquer fruta da família) |
| `scenes/dev_player_vs_mannequin_b/` | fixture de teste do jogo (manequim B, outra proporção e distância 1,6): é de onde saíram os clips da cena `respectcraft:dev/authoring_demo` |
| `scenes/*/…scene.json` | manifesto da cena (mapa dos bones, palco, partes, sockets). **Não edite.** |
| `scenes/*/scene.spec.json` | entrada do compositor (só para recompor) |
| `examples/*/` | exportação de exemplo da demo técnica: export da cena, os dois clips separados, metadados, eventos e validação |
| `tools/compose_scene.py`, `tools/split_scene.py`, `tools/scenekit.py` | compositor e separador (Python 3.9+, só biblioteca padrão) |

Cada template tem duas animações: `scene.<cena>` (vazia, para a sua finalização) e `scene.<cena>_demo` (coreografia
técnica de 3 s só para testar a exportação: golpe no tick 14, troca de expressão, casca → polpa no tick 22, agarrão,
câmera e o marcador de execução no fim). **Não é uma finalização definitiva.**

## Passo a passo no Blockbench Web

1. Abra <https://web.blockbench.net>, instale o plugin **GeckoLib Models & Animations** (File → Plugins → procure
   "GeckoLib"). Sem ele o projeto abre como modelo genérico e o export sai errado.
2. File → Open Model → escolha o `.bbmodel` do template. Confira: formato *GeckoLib Animated Model*, os dois rigs, a
   vítima de frente para o jogador.
3. Aba **Animate**. Escolha `scene.<cena>` (ou crie a sua com *Duplicate* a partir dela: a duplicata leva o keyframe
   de calibração de `STAGE_VICTIM_SCALE`). Duração = a da finalização, em múltiplos de 0,05 s (20 ticks por segundo);
   deixe *snapping* em 20.
4. Anime **só bones `ATK_*` e `VIC_*`**: rotação, posição e escala; easing, bezier, step e Molang valem. Não mexa em
   bones, pivôs, cubos nem nomes (o mapa da cena quebra). Não anime `STAGE_*` (isso seria a entidade andando; a sessão
   não move entidades). Deslocamentos da raiz (`ATK_Root`, `VIC_root`) devem voltar a zero no fim.
5. Eventos: aba Animate → *Effects* → canal **timeline**, um keyframe no instante, uma linha por evento, sempre com dono
   (`@attacker`, `@victim`, `@scene`). Exemplos:
   ```
   @attacker hit 2
   @attacker sound respectcraft:finisher.whoosh
   @victim rig:hide:face_normal;rig:show:eyes_wide;rig:show:brows_panic;rig:show:mouth_open
   @victim rig:hide:shell;rig:show:core;rig:show:damage_torso
   @scene camera 75,3.0,1.2,1.0,0.95,-2
   @attacker note grab LeftHandAttachment -> victim socket_grab_point
   @scene execute
   ```
   Lista completa e regras de dono no [`CONTRACT.md`](CONTRACT.md#timeline-aba-effects--canal-timeline). Para *ver* uma
   expressão no editor, alterne a visibilidade (olho) dos grupos `VIC_expr_*`/`VIC_face`... — no jogo quem troca é a
   linha `rig:` (o manifesto lista os grupos de cada parte).
6. Contato: use os grupos `VIC_socket_*` (impacto, agarrão, mãos, cabeça...) e `ATK_RightHandAttachment` /
   `ATK_LeftHandAttachment` como alvos visuais dos golpes.
7. Salve: File → Save Project (`.bbmodel`). Exporte: File → Export → **Export Animations** (GeckoLib) → um
   `.animation.json` com todas as animações da cena. Entregue **os dois arquivos** (projeto + export) para a separação.
8. Nunca use *Export GeckoLib Model* / *Export Geometry* deste projeto: a geometria conjunta não vai para o jogo.

## Separar e integrar (terminal, Claude Code)

No repositório as ferramentas ficam em `tools/scenekit/`; no ZIP, em `tools/` (mesmos comandos).

```bash
python3 tools/scenekit/split_scene.py scenekit/scenes/player_vs_strawberry/respectcraft_player_vs_strawberry_template.scene.json \
  --export minha_cena.animation.json --animation scene.player_vs_strawberry --out build/scenekit/out
```

Saem: `<anim>.attacker.animation.json` e `<anim>.victim.animation.json` (nomes originais dos bones, keyframes como o
plugin exportou, sem segunda conversão de eixos), `<anim>.export_meta.json` (duração, palco, eventos, linhas Java),
`<anim>.events.txt` e `<anim>.validation.txt`. Código 1 = há `ERROR` (nada é descartado em silêncio).
Sem o export, `--bbmodel projeto.bbmodel` compila o próprio projeto (só keyframes lineares/easing; o resto vira ERROR).
`--install src/main/resources` grava/mescla os clips nos arquivos de destino da spec, recusando arquivos de outros
geradores (biblioteca de combate, kit de rigs), fontes e arquivos editados fora do kit. Depois: registrar o clip do
jogador (`PlayerAnimationDef` com os eventos do relatório), o `clip_overrides` do binding da vítima (ou o clip da
família) e a `FinisherDef` — com o pedido do dono.

## Recompor (outra vítima, distância, duração)

```bash
python3 tools/scenekit/compose_scene.py scenekit/scenes/player_vs_fruit_mannequin/scene.spec.json --out build/scenekit \
  --name minha_fruta_scene [--distance 1.4] [--duration 80] [--textures multi]
```

Troque `victim.bbmodel`/`binding`/`entity`/`hitbox_width` na spec para qualquer CustomRig compatível; para PvP use
`victim.kind = "player"` com o `.bbmodel` do jogador.
