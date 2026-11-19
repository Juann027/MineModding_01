# Animações do jogador: autoria, Blockbench e exportação

Guia curto para criar e editar clips do rig do jogador sem perder trabalho. Detalhes técnicos em
`docs/handoffs/HANDOFF_PLAYER_ANIMATIONS_STAGE4.md` (convenções do rig) e
`docs/handoffs/HANDOFF_PLAYER_ANIMATIONS_STAGE4_1.md` (esta etapa).

## Quem é fonte de verdade

| Arquivo | Papel |
|---|---|
| `tools/player_anim/poses.py`, `clips.py` | **fonte** dos 12 clips da biblioteca de combate (quando `master.json` = `gerador`) |
| `model_sources/player/respectcraft_player_combat.bbmodel` | projeto do Blockbench com rig + 12 clips; gerado pelo script **ou** editado à mão (`master.json` = `blockbench`) |
| `model_sources/player/respectcraft_combo_scene.bbmodel` | dois rigs no palco, só para ajustar o tempo da coreografia `combo` (não exporte o geo dele) |
| `src/main/resources/assets/respectcraft/animations/player/respectcraft_player_combat.animation.json` | **artefato** que o jogo lê; sai do `export.py` ou do *Export Animations* do Blockbench |
| `tools/player_anim/master.json` | escolhe o mestre desses três arquivos: `"gerador"` (padrão) ou `"blockbench"` |
| `tools/player_anim/export_manifest.json` | hash do que o `export.py` gravou por último (não edite) |

Não há conversão automática do `.bbmodel` para `poses.py`/`clips.py`. Editou no Blockbench? Então o Blockbench
passa a ser o mestre (passo 6 abaixo) ou você copia os valores para `poses.py` à mão.

Clips novos e independentes (um `.bbmodel` seu, com um `.animation.json` próprio) não passam pelo `export.py`:
o script só grava os três arquivos da tabela.

## Editar no Blockbench

1. **Abrir:** Blockbench 5.x com o plugin **GeckoLib Models & Animations** (4.2.x) instalado
   (File → Plugins). Sem o plugin o arquivo abre como "Generic Model" e o export não sai no formato do GeckoLib.
   Abra `model_sources/player/respectcraft_player_combat.bbmodel`.
2. **Editar:** aba *Animate*, escolha o clip (`animation.respectcraft.player.<nome>`), mexa só em keyframes.
   Não mexa em bones, pivôs nem cubos (o geo do jogo não é reexportado daqui). Os nomes dos bones têm de existir no
   rig: `Root`, `Body`, `Head`, `RightArm`, `LeftArm`, `RightLeg`, `LeftLeg` e os demais do outliner.
3. **Salvar:** File → Save Project (o próprio `.bbmodel`).
4. **Exportar para o GeckoLib:** File → Export → *Export Animations* (GeckoLib) para
   `src/main/resources/assets/respectcraft/animations/player/respectcraft_player_combat.animation.json`.
   Confira: `python3 tools/player_anim/bbsync.py` (também roda no CI) diz se `.bbmodel` e `.animation.json` batem.
5. **Integrar ao mod:** clip novo → registre em `PlayerAnimations` (duração em ticks = segundos × 20):
   `public static final PlayerAnimationDef REVERENCIA = register(library("reverencia", 30, 3, 10).build());`
   Mudou a duração de um clip existente? Atualize os ticks no registro (o cliente avisa no log se divergir).
   Teste: `./gradlew build`, depois `/respectcraft anim play <player> respectcraft:reverencia` em `runClient` + `runServer`.
6. **Não perder a edição:** em `tools/player_anim/master.json` troque `"mestre"` para `"blockbench"` e faça commit
   junto com o `.bbmodel` e o `.animation.json`. A partir daí o `export.py` nunca grava esses arquivos no repositório
   (nem com `--force`). Mesmo sem isso, ele recusa sobrescrever um arquivo que mudou desde a última exportação.

## Gerar pelo script (`master.json` = `gerador`)

```bash
python3 tools/player_anim/export.py --dry-run      # mostra o que mudaria; não grava nada
python3 tools/player_anim/export.py                # valida e grava (recusa se algum arquivo foi editado fora)
python3 tools/player_anim/export.py --out /tmp/g   # grava em outro lugar para comparar (diff -r)
python3 tools/player_anim/export.py --force        # descarta edições externas (copia antes para .backup/)
python3 tools/player_anim/export.py --check        # saída 1 se algo mudaria (útil antes de commit)
python3 tools/player_anim/test_export.py           # testa a proteção do exportador em diretório temporário
```

Estado de cada arquivo no relatório: `novo`, `igual`, `atualizar` (o script gravou por último; seguro) ou
`EDITADO` (mudou fora do script: recusado sem `--force`). Saídas: 0 ok, 1 `--check` com diferenças, 2 conflito,
3 mestre = blockbench, 4 validação falhou. Antes de gravar, o script valida estrutura, bones do rig, a continuidade
dos encadeamentos (fim de `knockdown` = início de `hands_and_knees` = início de `get_up`) e a ida e volta
`.bbmodel` → export do Blockbench → `.animation.json`; a gravação é atômica.

## Convenção dos valores (importante)

O `.animation.json` usa a convenção do GeckoLib/Bedrock; o `.bbmodel` guarda a do Blockbench. Ao exportar, o
Blockbench **inverte X e Y da rotação e X da posição**. Copiar números entre os dois arquivos à mão exige essa
troca de sinal (`bbsync.flip`). Os `.bbmodel` da Etapa 4 tinham os números sem a troca (clips espelhados no editor);
corrigido na Etapa 4.1.

## Conferir no Blockbench de verdade sem instalar nada

```bash
bash tools/player_anim/blockbench/check.sh
```

Baixa o Blockbench (código oficial, versão web) e o plugin GeckoLib para `/tmp`, abre os dois `.bbmodel` num
Chromium sem tela e compara o *Export Animations* do editor com o arquivo do jogo.

## Sequências (vários clips sem voltar à pose em pé)

```java
AnimationSequence queda = AnimationSequence.of(KNOCKDOWN).thenFor(HANDS_AND_KNEES, 40).then(GET_UP);
PlayerAnimationService.playSequence(player, queda, new CompoundTag(), false);
PlayerAnimationService.advance(player); // opcional: sai do loop antes do tempo
```

Teste: `/respectcraft anim sequence <player> fall`, `/respectcraft anim sequence <player> knockdown hands_and_knees@60 get_up`,
`/respectcraft anim next <player>` e `/respectcraft anim stop <player>`. Loops dentro de sequência exigem duração (`@ticks`).
