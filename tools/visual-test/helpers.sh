#!/usr/bin/env bash
# Ajudantes do teste visual do cliente real em tela virtual (Linux, sem GPU). Uso: `. tools/visual-test/helpers.sh`
# Requisitos: xvfb, openbox, xdotool, imagemagick, ffmpeg e Mesa (llvmpipe). Veja HANDOFF_GECKOLIB_STAGE1.md.
export DISPLAY=${DISPLAY:-:99} LIBGL_ALWAYS_SOFTWARE=1
OUT=${OUT:-smoke/visual}; mkdir -p "$OUT"
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)

start_display() { Xvfb "$DISPLAY" -screen 0 854x480x24 -nolisten tcp & sleep 2; openbox & sleep 1; }

# Servidor dedicado de desenvolvimento com console por FIFO (offline, mundo plano).
start_server() {
  mkdir -p "$REPO/run"; echo "eula=true" > "$REPO/run/eula.txt"
  printf 'online-mode=false\nlevel-type=minecraft\\:flat\ngenerate-structures=false\nspawn-protection=0\ngamemode=creative\ndifficulty=peaceful\n' > "$REPO/run/server.properties"
  rm -f "$OUT/srv.fifo"; mkfifo "$OUT/srv.fifo"
  (cd "$REPO" && ./gradlew runServer --no-daemon --no-configuration-cache --console=plain < "$OUT/srv.fifo" > "$OUT/server.log" 2>&1) &
  sleep 100000 > "$OUT/srv.fifo" &
  until grep -q 'Done (' "$OUT/server.log" 2>/dev/null; do sleep 3; done
}
srv() { echo "$*" > "$OUT/srv.fifo"; sleep 0.7; }   # comando no console do servidor

# Cliente real conectando sozinho ao servidor local. $1 = nome do jogador (define a skin padrao offline).
launch() {
  local dir="$REPO/build/run-client-$1"; mkdir -p "$dir"
  [ -f "$dir/options.txt" ] || printf 'pauseOnLostFocus:false\nrenderDistance:4\nmaxFps:30\ntutorialStep:none\nnarrator:0\nonboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\ngraphicsMode:0\nsoundCategory_master:0.0\nguiScale:2\nentityShadows:false\n' > "$dir/options.txt"
  local n; n=$(grep -c "$1 joined the game" "$OUT/server.log")
  (cd "$REPO" && MC_USER=$1 ./gradlew runClient -I tools/visual-test/client.init.gradle --no-daemon --no-configuration-cache --console=plain > "$OUT/client-$1.log" 2>&1) &
  until [ "$(grep -c "$1 joined the game" "$OUT/server.log")" -gt "$n" ]; do sleep 3; done; sleep 12
}
closeclient() { xdotool search --name "Minecraft NeoForge" | head -1 | xargs -I{} xdotool windowclose {}; sleep 7; }

key()  { xdotool key --delay 80 "$@"; sleep 0.4; }          # ex.: key F5; key F8; key F9
shot() { import -window root "$OUT/$1.png"; }                # captura de tela
rec()  { ffmpeg -loglevel error -y -f x11grab -framerate 20 -video_size 854x480 -i "$DISPLAY" -t "$2" "$OUT/$1.mp4" & }
# Folha de quadros: sheet <nome> <fps> <colunas>
sheet() { rm -rf "$OUT/fr_$1"; mkdir "$OUT/fr_$1"; ffmpeg -loglevel error -i "$OUT/$1.mp4" -vf "fps=$2,crop=220:260:317:190" "$OUT/fr_$1/%03d.png"; montage "$OUT/fr_$1"/*.png -tile "${3}x" -geometry +1+1 "$OUT/sheet_$1.png"; }
