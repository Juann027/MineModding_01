#!/usr/bin/env bash
# Etapa 4.10.2, correcao: servidor parando no meio de cenas. Dois clientes reais e DUAS cenas ao mesmo tempo
# (Juca executa um zumbi; outro zumbi executa a Bia). /stop no meio: cada sessao precisa terminar com
# "CANCELLED (server stopping)" no log (antes da correcao: nenhuma linha, nenhum Aborted). Depois o servidor volta no
# mesmo mundo e os dois entram livres, sem animacao nem camera presa, e uma cena nova funciona.
cd "$(dirname "$0")/../../.."
export OUT=${OUT:-smoke/visual4102c}
. tools/visual-test/helpers.sh
A=:99 B=:98
step() { echo "=== $* ($(date +%T))" | tee -a "$OUT/passos.txt"; }
ask() { local n; n=$(wc -l < "$OUT/server.log"); srv "$*"; sleep 0.8; echo "> $*" >> "$OUT/consultas.txt"; tail -n +"$((n + 1))" "$OUT/server.log" | grep -v DEBUG >> "$OUT/consultas.txt"; }
both() { on $A shot "$1_juca"; on $B shot "$1_bia"; }
relaunch() { [ -f "$OUT/client-$2.log" ] && mv "$OUT/client-$2.log" "$OUT/client-$2-$(date +%H%M%S).log"; DISPLAY=$1 launch "$2"; }
server_up() {
  [ -f "$OUT/server.log" ] && mv "$OUT/server.log" "$OUT/server-$(date +%H%M%S).log"
  rm -f "$OUT/srv.fifo"; mkfifo "$OUT/srv.fifo"
  (cd "$REPO" && ./gradlew runServer --no-daemon --no-configuration-cache --console=plain < "$OUT/srv.fifo" > "$OUT/server.log" 2>&1) &
  sleep 100000 > "$OUT/srv.fifo" &
  until grep -q 'Done (' "$OUT/server.log" 2>/dev/null; do sleep 3; done
}
server_down() { srv "stop"; until ! pgrep -f "runServer" > /dev/null; do sleep 2; done; pkill -f "sleep 100000"; sleep 2; }
zombie() { srv "summon minecraft:zombie $1 {NoAI:1b,PersistenceRequired:1b,Tags:[\"$2\"],CustomName:'\"$2\"',ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}"; }

rm -rf "$REPO/run/world"
start_display $A; start_display $B
step server; start_server
srv "difficulty easy"; srv "gamerule doMobSpawning false"; srv "time set day"; srv "gamerule doDaylightCycle false"
relaunch $A Juca; relaunch $B Bia
srv "gamemode survival Juca"; srv "gamemode survival Bia"

step "two scenes at once, then /stop in the middle"
srv "tp Juca 0.5 -60 0.5 0 0"; srv "tp Bia 10.5 -60 2.5 180 0"; sleep 1
zombie "0.5 -60 2.5" zj; zombie "10.5 -60 0.5" zb; sleep 2
ask "respectcraft admin finisher Juca @e[tag=zj,limit=1] respectcraft:combo"
ask "respectcraft admin finisher @e[tag=zb,limit=1] Bia respectcraft:combo"
sleep 1.5; both 01_mid
server_down
sleep 2; both 02_stopped

step "server back on the same world: both join free and a new scene works"
DISPLAY=$A closeclient; DISPLAY=$B closeclient
server_up
relaunch $A Juca; relaunch $B Bia
ask "data get entity Bia Pos"; on $B hold w 1.0; ask "data get entity Bia Pos"
ask "data get entity Juca Pos"; on $A hold s 1.0; ask "data get entity Juca Pos"
both 03_rejoined
srv "tp Juca 0.5 -60 0.5 0 0"; sleep 1
ask "respectcraft admin finisher Juca @e[tag=zj,limit=1] respectcraft:takedown"; sleep 1.2; both 04_new_scene; sleep 3
ask "execute if entity @e[tag=zj]"
DISPLAY=$A closeclient; DISPLAY=$B closeclient
server_down
step done
{
  echo "## Sessoes (log do servidor)"
  cat "$OUT"/server-*.log "$OUT/server.log" | grep -E 'Finisher respectcraft:[a-z]+ by|joined the game|left the game|Stopping server|was executed' | grep -v DEBUG
  echo; echo "## Chat"; cat "$OUT"/client-*.log | grep '\[CHAT\]' | sed 's/.*\[CHAT\] //' | grep -v '^\[Server:'
  echo; echo "## Erros (fora rede/som do ambiente)"
  cat "$OUT"/client-*.log "$OUT"/server*.log | grep -E 'Exception|NoSuchMethod|NoClassDef|/ERROR\]' | grep -vE 'minecraftservices|sessionserver|SoundSystem|OpenAL|MinecraftClientException|UnknownHost|Yggdrasil|NativeIoException|Connection reset' | sort | uniq -c | head -40
  echo; echo "## Consultas"; cat "$OUT/consultas.txt"
} > "$OUT/resumo.txt"
