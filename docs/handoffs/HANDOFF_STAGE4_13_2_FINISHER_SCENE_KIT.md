# Handoff — Etapa 4.13.2: Finisher Scene Authoring Kit

Ferramenta de autoria conjunta de finalizações registrada no roadmap (2.1): o jogador (ATTACKER) e a vítima (CustomRig
ou outro jogador) no **mesmo projeto do Blockbench**, no palco do Finisher Engine, e a separação em **um clip por
participante**. Nada novo roda no jogo além de uma cena de teste (fixture). Versão continua `0.5.7`.

## O que existe

| Onde | O quê |
|---|---|
| `tools/scenekit/scenekit.py` | biblioteca (só stdlib): composição, atlas PNG, compilação `.bbmodel` → export (o subconjunto do `bbsync`), separação, linguagem da timeline, linhas Java |
| `tools/scenekit/compose_scene.py` | spec → `<cena>.bbmodel` + `<cena>.scene.json` (manifesto com mapa explícito de bones) |
| `tools/scenekit/split_scene.py` | manifesto + export do plugin (ou `--bbmodel`) → clips por papel, `export_meta.json`, `events.txt`, `validation.txt`; `--install` com proteção (`installed_manifest.json`) |
| `tools/scenekit/make_templates.py` | gera templates, demo técnica, exemplos e os clips da cena de teste; protegido por hash (`generated_manifest.json`, como `make_assets.py`/`export.py`) |
| `tools/scenekit/test_scenekit.py` | 47 verificações (CI) |
| `tools/scenekit/blockbench/` | `check_scene.sh` + `probe_scene.js` + `verify_probe.py`: Blockbench web real (código oficial + plugin GeckoLib, Chromium) — job `scenekit-blockbench` do CI |
| `tools/scenekit/package_kit.py` | ZIP portátil `respectcraft-scenekit-v1.zip` (artefato `respectcraft-scenekit` do CI) |
| `scenekit/` | `README.md` (guia do Blockbench Web), `CONTRACT.md`, `CLAUDE_CHROME.md` (Claude normal + `/blockbench-web-geckolib-v2`), `scenes/` (specs, templates, manifestos), `examples/` |
| `rig.Rigs.AUTHORING_DEMO` | cena de teste `respectcraft:dev/authoring_demo` (fora do catálogo; `/respectcraft rig demo <alvo> authoring`): clip do jogador em `animations/player/respectcraft_dev_scenes.animation.json`, clip da vítima em `animations/entity/dev_rig_mannequin_b.animation.json` (override no binding do manequim B), eventos/câmera copiados do relatório |

Templates: `respectcraft_player_vs_strawberry_template.bbmodel` (rig oficial + morango completo do artista, 105
grupos, 246 cubos) e `respectcraft_player_vs_fruit_mannequin_template.bbmodel` (manequim de referência da família).
Cada um com `scene.<cena>` (vazia, só a calibração) e `scene.<cena>_demo` (coreografia técnica de 3 s).

## Decisões

- **Generaliza o que havia:** o truque do Combo (grupo de palco girando a vítima em volta de um pivô, sem mexer em
  coordenadas) virou `STAGE_VICTIM` com pivô resolvido para qualquer yaw; a separação por prefixo do `bbsync` virou
  mapa explícito no manifesto (`manifest_from_prefixes` lê a cena antiga do Combo: `""`/`Alvo_`/`Alvo_Palco`).
- **Escala real:** referencial = px do executor. Jogador 1 bloco = 17,0667 px (15/16 do `PlayerRenderer`); CustomRig
  16/`render_scale`. Diferença = keyframe constante em `STAGE_VICTIM_SCALE` (16/15 para o morango), calibração que o
  split reconhece e descarta. Distância: spec > binding > família > automática (morango 1,15; manequim B 1,6).
- **Texturas:** atlas só no projeto de autoria (1 px = 1 UV, UVs por face e box UV deslocados; testado pixel a pixel).
  Obrigatório: o formato GeckoLib não tem UV por textura (Blockbench real). `--textures multi` fica só como diagnóstico.
- **Eventos com dono** numa linha da timeline (`@attacker hit 2`, `@victim rig:hide:shell`, `@scene camera ...`,
  `@scene execute`...): só o que o engine já entende; `rig:`/`calamidade:` vão para o timeline do clip do mob, o resto
  vira linha Java do registro. Duplicata em dois donos, evento sem dono, `execute` fora do fim, parte inexistente,
  canais *sound*/*particle* e palco animado são `ERROR`. Raiz que não volta a zero é `WARN` (movimento visual).
- **Split preserva o export do plugin** (bezier, catmullrom, Molang passam intactos; nenhuma conversão de eixo). Do
  `.bbmodel`, só lineares/easing (o resto é `ERROR`, nunca some).
- **Proteção:** templates salvos pelo Blockbench não são regerados sem `--force`; `--install` recusa a biblioteca de
  combate (`export_manifest.json`), arquivos do kit de rigs e deste gerador, fontes (`model_sources/`, kits) e
  arquivos existentes que o kit não gravou.
- **Morango no jogo:** o binding/geo/png do artista não foram tocados; por isso a cena de teste usa o **manequim B**
  (fixture, binding escrito à mão). Dar o clip ao morango = `animation_file` + `clip_overrides` no binding dele, com
  o pedido do dono. Contra o morango a cena de teste é recusada antes de começar (`requiresClip`).

## Validação

- `python3 tools/scenekit/test_scenekit.py`: 0 falhas — Combo PvP separado = clips do jogo (0 diff); PvP ida e volta
  sem perder keyframe; bones/pivôs/hierarquia/cubos preservados; UUIDs únicos; atlas pixel a pixel e UVs; multi com
  bytes originais; palco (1,15/1,6 blocos, âncora, escala, lateral/yaw); separação da demo (duração 60 ticks nos dois,
  sem `STAGE_*`/prefixos, bones ⊆ geo do jogo, keyframes idênticos, timeline só na vítima); 11 casos de `ERROR`;
  proteção da instalação; CLI reproduz o exemplo; geradores em dia; ZIP.
- `validate_rig.py --repo`, `test_rigkit.py`, `make_assets.py --check`, `bbsync.py`: ok.
- `./gradlew build` e `server-smoke-test.sh`: OK (novas verificações: clip do manequim B resolvido por BINDING, recusa
  de quem não tem o clip; `RigSelfTest`: override com a duração da cena e partes no tick 22).
- **Dois clientes reais** (servidor dedicado, Xvfb): a cena começou no manequim B (recusada no morango), o observador
  viu soco e polpa exposta; terminou `SUCCESS (killed)`; segunda cena cancelada com `/stopitstopnow` no meio →
  `CANCELLED (player request)`, casca de volta (`Rig parts ...: {}`), jogador em pé.
  Evidência: `docs/evidencias/stage4_13_2/two_clients_authoring_demo.png`.
- **Blockbench real 5.2.2 + plugin GeckoLib** (job `scenekit-blockbench` do CI; daqui o github.com estava bloqueado):
  os três projetos abrem como GeckoLib Animated Model (105/73/74 bones, 246/45/47 cubos), tocam sem erro, a vítima
  fica exatamente na âncora (−19,63 px; manequim B −27,31 px), o save preserva bones/cubos/texturas/animações e o
  *Export Animations* separado bate com os exemplos (0 diferença). Contatos medidos no editor: golpe 3,2 px e agarrão
  6,4 px (morango e manequim), 11,2 px no manequim B. Achados aplicados: o export acrescenta `;` a cada linha da
  timeline (o parser aceita) e o formato GeckoLib **não tem UV por textura** (atlas obrigatório; `multi` só
  diagnóstico). O campo `respectcraft_scene` da raiz não sobrevive ao save do Blockbench: a fonte é o `.scene.json`.

## Limitações

- Coreografia de demonstração é técnica (não é finalização). Contatos são medidos e reportados, não ajustados.
- `yaw_offset` ≠ 0: posição conferida; o sentido do giro do Blockbench × yaw do jogo não foi validado em cena real.
- Linhas Java dos eventos são coladas à mão no registro (de propósito: a integração é decisão do dono).
- O campo `respectcraft_scene` na raiz do `.bbmodel` é só informativo; o manifesto `.scene.json` é a fonte.
