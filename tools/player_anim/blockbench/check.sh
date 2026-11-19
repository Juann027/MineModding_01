#!/usr/bin/env bash
# Abre os .bbmodel do jogador no Blockbench de verdade (versao web, compilada do codigo oficial) e confere que o
# "Export Animations" do editor gera exatamente o .animation.json do jogo. Nao precisa de Blockbench instalado.
#
#   bash tools/player_anim/blockbench/check.sh [dir_de_trabalho]
#
# Requisitos: git, node/npm, python3 e playwright (Chromium). Baixa o Blockbench (tag BB_TAG) e o plugin GeckoLib
# do repositorio oficial de plugins para o dir de trabalho (padrao: /tmp/respectcraft-blockbench), fora do projeto.
set -euo pipefail
BB_TAG=${BB_TAG:-v5.2.2}
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
HERE="$REPO/tools/player_anim/blockbench"
WORK=${1:-/tmp/respectcraft-blockbench}
mkdir -p "$WORK"
if [ ! -f "$WORK/src/dist/bundle.js" ]; then
  rm -rf "$WORK/src" "$WORK/plugins"
  git clone -q --depth 1 --branch "$BB_TAG" https://github.com/JannisX11/blockbench "$WORK/src"
  git clone -q --depth 1 --filter=blob:none --sparse https://github.com/JannisX11/blockbench-plugins "$WORK/plugins"
  git -C "$WORK/plugins" sparse-checkout set --no-cone 'plugins/geckolib/*'
  (cd "$WORK/src" && npm ci --ignore-scripts --no-audit --no-fund >/dev/null && node ./build.js --target=web >/dev/null)
fi
cp "$WORK/plugins/plugins/geckolib/geckolib.js" "$WORK/src/geckolib_plugin.js"
(cd "$WORK/src" && exec python3 -m http.server 8765 --bind 127.0.0.1 >/dev/null 2>&1) &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
sleep 1
OUT="$WORK/out"; mkdir -p "$OUT"
ANIM="$REPO/src/main/resources/assets/respectcraft/animations/player/respectcraft_player_combat.animation.json"
fail=0
for f in respectcraft_player_combat respectcraft_combo_scene; do
  node "$HERE/probe.js" "$REPO/model_sources/player/$f.bbmodel" "$OUT" > "$OUT/$f.txt"
  python3 -c "import json,sys; t=open(sys.argv[1]).read(); d=json.loads(t[:t.find('\npose')] if '\npose' in t else t); \
print(sys.argv[2], 'Blockbench', d['version'], '| plugin GeckoLib:', d['plugin'], '| formato:', d['formatName'], '| bones:', d['groups'], '| clips:', len(d['anims']))" "$OUT/$f.txt" "$f"
done
# O que o Blockbench exportou tem de ser o arquivo do jogo (a cena dupla: cada rig com o seu clip).
python3 - "$OUT" "$ANIM" "$REPO/tools/player_anim" <<'PY' || fail=1
import json, sys
sys.path.insert(0, sys.argv[3])
import bbsync
out, anim = sys.argv[1], sys.argv[2]
game = json.load(open(anim))["animations"]
clips = json.load(open(f"{out}/respectcraft_player_combat.export.json"))["animations"]
problems = bbsync.diff_clips(game, clips, only=list(clips))
scene = json.load(open(f"{out}/respectcraft_combo_scene.export.json"))["animations"]["combo_scene"]
for prefix, clip in bbsync.SCENE_CLIPS.items():
    bones = {b[len(prefix):]: v for b, v in scene["bones"].items() if (b.startswith(prefix) if prefix else not b.startswith("Alvo_"))}
    part = {clip: {"animation_length": scene["animation_length"], "bones": bones}}
    problems += bbsync.diff_clips(game, part, only=[clip])
print(f"Export do Blockbench x {anim.split('/')[-1]}: {len(problems)} diferenca(s)")
for p in problems[:20]:
    print("  ", p)
sys.exit(1 if problems else 0)
PY
exit $fail
