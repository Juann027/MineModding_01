#!/usr/bin/env bash
# Etapa 4.10.2, parte 2: continua no MESMO mundo da parte 1 (run/world, com pvp=false gravado no fim dela).
# 13b pvp=false com a mao vazia (convite natural e admin recusados); depois pvp=true e: convite recusado pela vitima,
# convite expirado, executor sai antes do aceite, vitima sai durante a recuperacao caida e volta, e o servidor para no
# meio de uma cena (volta sem estado preso). Resumo em $OUT/resumo.txt.
cd "$(dirname "$0")/../../.."
export OUT=${OUT:-smoke/visual4102b}
. tools/visual-test/helpers.sh
A=:99 B=:98
step() { echo "=== $* ($(date +%T))" | tee -a "$OUT/passos.txt"; }
ask() { local n; n=$(wc -l < "$OUT/server.log"); srv "$*"; sleep 0.8; echo "> $*" >> "$OUT/consultas.txt"; tail -n +"$((n + 1))" "$OUT/server.log" | grep -v DEBUG >> "$OUT/consultas.txt"; }
chat() { on "$1" xdotool key --delay 80 slash; sleep 0.8; on "$1" xdotool type --delay 30 "$2"; sleep 0.3; on "$1" xdotool key Return; sleep 1; }
both() { on $A shot "$1_juca"; on $B shot "$1_bia"; }
stage() { srv "tp Juca 0.5 -60 0.5 0 0"; srv "tp Bia 0.5 -60 2.5 180 0"; sleep 1.5; }
low() { srv "effect clear Bia"; srv "effect give Bia minecraft:instant_health 1 4 true"; sleep 0.6; srv "damage Bia 16 minecraft:generic"; sleep 0.6; }
shiftclick() { on $A xdotool keydown shift; sleep 1.2; on $A xdotool click 3; sleep 0.6; on $A xdotool keyup shift; sleep 0.8; }
relaunch() { [ -f "$OUT/client-$2.log" ] && mv "$OUT/client-$2.log" "$OUT/client-$2-$(date +%H%M%S).log"; DISPLAY=$1 launch "$2"; }
# Servidor no mundo existente com um valor de pvp (start_server regravaria server.properties). n = numero do log.
server_up() {
  sed -i '/^pvp=/d' "$REPO/run/server.properties"; echo "pvp=$1" >> "$REPO/run/server.properties"
  [ -f "$OUT/server.log" ] && mv "$OUT/server.log" "$OUT/server-$(date +%H%M%S).log"
  rm -f "$OUT/srv.fifo"; mkfifo "$OUT/srv.fifo"
  (cd "$REPO" && ./gradlew runServer --no-daemon --no-configuration-cache --console=plain < "$OUT/srv.fifo" > "$OUT/server.log" 2>&1) &
  sleep 100000 > "$OUT/srv.fifo" &
  until grep -q 'Done (' "$OUT/server.log" 2>/dev/null; do sleep 3; done
}
server_down() { srv "stop"; until ! pgrep -f "runServer" > /dev/null; do sleep 2; done; pkill -f "sleep 100000"; sleep 2; }
fresh() { srv "clear Juca"; srv "clear Bia"; srv "effect give Juca minecraft:resistance 1200 4 true"; }

[ -d "$REPO/run/world" ] || { echo "Rode a parte 1 antes (mundo em run/world)."; exit 1; }
start_display $A; start_display $B
step "server pvp=false"; server_up false
relaunch $A Juca; relaunch $B Bia
fresh

step "13b pvp=false, empty hand: natural invite and admin start refused; counter persisted"
ask "execute as Bia run respectcraft test defeats get"
stage; low
ask "data get entity Juca SelectedItem"
shiftclick; both 13b_pvp_refused
ask "respectcraft admin finisher Juca Bia respectcraft:takedown"
ask "calamidade status"

step "restart with pvp=true"
DISPLAY=$A closeclient; DISPLAY=$B closeclient
server_down; server_up true
relaunch $A Juca; relaunch $B Bia
fresh

step "14 invite declined by the victim in her chat: no scene"
stage; low
shiftclick
chat $B "respectcraft test finisher deny"; sleep 1; both 14_denied
chat $B "respectcraft test finisher accept"

step "15 invite expires after 15 s: accept refused"
srv "tp Juca 0.5 -60 0.5 0 0"; sleep 1
shiftclick; sleep 16
chat $B "respectcraft test finisher accept"; both 15_expired

step "16 inviter disconnects before the accept: invite dropped"
stage
shiftclick
DISPLAY=$A closeclient
chat $B "respectcraft test finisher accept"; on $B shot 16_after_bia
relaunch $A Juca; fresh

step "17 victim disconnects during the knocked-down recovery and comes back free"
stage
srv "item replace entity Bia weapon.offhand with minecraft:totem_of_undying"; sleep 1
ask "respectcraft admin finisher Juca Bia respectcraft:takedown"; sleep 4
DISPLAY=$B closeclient
relaunch $B Bia
ask "data get entity Bia Pos"
on $B hold w 1.0; ask "data get entity Bia Pos"
both 17_rejoined

step "18 server stops in the middle of the combo: both come back clean"
stage; srv "effect give Bia minecraft:instant_health 1 4 true"; sleep 1
ask "respectcraft admin finisher Juca Bia respectcraft:combo"; sleep 2; both 18_mid
server_down
sleep 3; both 18_disconnected
DISPLAY=$A closeclient; DISPLAY=$B closeclient
server_up true
relaunch $A Juca; relaunch $B Bia
ask "data get entity Bia Health"
ask "data get entity Bia Pos"
on $B hold w 1.0; ask "data get entity Bia Pos"
ask "data get entity Juca Pos"
on $A hold s 1.0; ask "data get entity Juca Pos"
both 18_rejoined
# Uma cena nova comeca normalmente depois da volta (nada preso em sessoes ou recuperacao).
srv "clear Juca"; stage
ask "respectcraft admin finisher Juca Bia respectcraft:takedown"; sleep 1.2; both 18_new_scene; sleep 3
ask "data get entity Bia Health"
DISPLAY=$A closeclient; DISPLAY=$B closeclient
server_down
step done
{
  echo "## Sessoes (log do servidor)"
  cat "$OUT"/server-*.log "$OUT/server.log" | grep -E 'Finisher respectcraft:[a-z]+ (by|refused)|Finisher refused|was executed|joined the game|left the game|PvP is not allowed|Stopping server' | grep -v DEBUG
  echo; echo "## Chat do Juca"; cat "$OUT"/client-Juca*.log 2>/dev/null | grep '\[CHAT\]' | sed 's/.*\[CHAT\] //' | grep -v '^\[Server:'
  echo; echo "## Chat da Bia"; cat "$OUT"/client-Bia*.log 2>/dev/null | grep '\[CHAT\]' | sed 's/.*\[CHAT\] //' | grep -v '^\[Server:'
  echo; echo "## Erros (fora rede/som do ambiente)"
  cat "$OUT"/client-*.log "$OUT"/server*.log | grep -E 'Exception|NoSuchMethod|NoClassDef|/ERROR\]' | grep -vE 'minecraftservices|sessionserver|SoundSystem|OpenAL|MinecraftClientException|UnknownHost' | sort | uniq -c | head -40
  echo; echo "## Consultas"; cat "$OUT/consultas.txt"
} > "$OUT/resumo.txt"
