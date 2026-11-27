#!/usr/bin/env bash
# Confere os templates do Finisher Scene Authoring Kit no Blockbench web real (compilado do codigo oficial) com o plugin
# GeckoLib: abre, toca as animacoes, mede palco e contatos, exporta pelo plugin e separa o export com o split_scene.py
# (tem de bater com o exemplo do repositorio). Tambem abre a variante com texturas multiplas (--textures multi).
#
#   bash tools/scenekit/blockbench/check_scene.sh [dir_de_trabalho]
#
# Reaproveita a copia do Blockbench de tools/player_anim/blockbench/check.sh (mesmo BB_TAG e dir de trabalho).
set -euo pipefail
BB_TAG=${BB_TAG:-v5.2.2}
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
HERE="$REPO/tools/scenekit/blockbench"
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
OUT=${SCENEKIT_OUT:-"$REPO/build/scenekit/blockbench"}; mkdir -p "$OUT"
python3 "$REPO/tools/scenekit/compose_scene.py" "$REPO/scenekit/scenes/player_vs_strawberry/scene.spec.json" \
  --out "$OUT/multi" --textures multi --name player_vs_strawberry_multi >/dev/null
fail=0
for pair in \
  "scenekit/scenes/player_vs_strawberry/respectcraft_player_vs_strawberry_template" \
  "scenekit/scenes/player_vs_fruit_mannequin/respectcraft_player_vs_fruit_mannequin_template" \
  "scenekit/scenes/dev_player_vs_mannequin_b/dev_player_vs_mannequin_b"; do
  node "$HERE/probe_scene.js" "$REPO/$pair.bbmodel" "$REPO/$pair.scene.json" "$OUT" > /dev/null || fail=1
done
node "$HERE/probe_scene.js" "$OUT/multi/player_vs_strawberry_multi.bbmodel" "$OUT/multi/player_vs_strawberry_multi.scene.json" "$OUT" > /dev/null || fail=1
python3 "$HERE/verify_probe.py" "$REPO" "$OUT" || fail=1
exit $fail
