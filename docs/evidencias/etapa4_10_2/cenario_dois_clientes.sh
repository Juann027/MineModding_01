#!/usr/bin/env bash
# Etapa 4.10.2: validacao multiplayer com DOIS clientes reais (Xvfb, llvmpipe) + servidor dedicado de desenvolvimento.
# Juca (executor, tela :99) e Bia (vitima, tela :98). Cobre: jogador x jogador pelo caminho natural (Shift + clique
# direito, convite aceito no chat do cliente da vitima), times sem fogo amigo (recusa e troca no meio da cena), totem
# na vitima jogador, Shift da vitima, saida do executor e da vitima no meio da cena com volta, troca de dimensao no meio
# da cena, mob x jogador (caminho natural e admin), golpe da IA do proprio mob, terceiro jogador batendo no mob,
# sincronizacao para quem comeca a ver no meio da cena (StartTracking) e, depois de reiniciar o servidor com pvp=false,
# recusa do PvP e contadores persistidos. Resumo em $OUT/resumo.txt.
cd "$(dirname "$0")/../../.."
export OUT=${OUT:-smoke/visual4102}
. tools/visual-test/helpers.sh
A=:99 B=:98
step() { echo "=== $* ($(date +%T))" | tee -a "$OUT/passos.txt"; }
# Consulta no console e guarda a resposta (as linhas novas do log do servidor).
ask() { local n; n=$(wc -l < "$OUT/server.log"); srv "$*"; sleep 0.8; echo "> $*" >> "$OUT/consultas.txt"; tail -n +"$((n + 1))" "$OUT/server.log" | grep -v DEBUG >> "$OUT/consultas.txt"; }
note() { echo "# $*" >> "$OUT/consultas.txt"; }
# Comando digitado no chat de um cliente real: chat <tela> <comando sem a barra>.
chat() { on "$1" xdotool key --delay 80 slash; sleep 0.8; on "$1" xdotool type --delay 30 "$2"; sleep 0.3; on "$1" xdotool key Return; sleep 1; }
both() { on $A shot "$1_juca"; on $B shot "$1_bia"; }
# Palco padrao: Juca em (0.5, -60, 0.5) olhando para o sul; Bia 2 blocos a frente, de frente para ele.
stage() {
  srv "tp Juca 0.5 -60 0.5 0 0"; srv "tp Bia 0.5 -60 2.5 180 0"; sleep 1.5
}
low() { srv "effect clear Bia"; srv "effect give Bia minecraft:instant_health 1 4 true"; sleep 0.6; srv "damage Bia 16 minecraft:generic"; sleep 0.6; }
pick() { local n; n=$(grep -c "$1" "$OUT/server.log"); echo "$n"; }
# Shift + clique direito no cliente do Juca (mao vazia) olhando para quem estiver na frente.
shiftclick() { on $A xdotool keydown shift; sleep 1.2; on $A xdotool click 3; sleep 0.6; on $A xdotool keyup shift; sleep 0.8; }
clean() { srv "kill @e[type=minecraft:item]"; srv "kill @e[type=minecraft:experience_orb]"; }
# Volta de um cliente: guarda o log anterior (launch sobrescreve client-<nome>.log). relaunch <tela> <nome>
relaunch() { mv "$OUT/client-$2.log" "$OUT/client-$2-$(date +%H%M%S).log"; DISPLAY=$1 launch "$2"; }

# Mundo novo a cada execucao (o mundo de run/ guardaria mortes, times e contadores da execucao anterior).
rm -rf "$REPO/run/world"
start_display $A; start_display $B
step server; start_server
srv "gamerule doMobSpawning false"; srv "gamerule naturalRegeneration false"; srv "gamerule doImmediateRespawn false"
srv "gamerule keepInventory true"; srv "difficulty easy"; srv "kill @e[type=!minecraft:player]"
srv "time set day"; srv "weather clear"; srv "gamerule doDaylightCycle false"
step "clients"; DISPLAY=$A launch Juca; DISPLAY=$B launch Bia
srv "kill @e[type=!minecraft:player]"
srv "op Juca"
srv "gamemode survival Juca"; srv "gamemode survival Bia"
srv "effect give Juca minecraft:resistance 1200 4 true"; srv "effect give Juca minecraft:saturation 1200 1 true"
srv "scoreboard objectives add pk playerKillCount"; srv "scoreboard objectives add bd deathCount"
srv "scoreboard objectives add zk minecraft.killed:minecraft.zombie"
srv "scoreboard objectives setdisplay list bd"
ask "list"
ask "respectcraft finishers Bia"

step "1 teams without friendly fire: natural invite and admin start refused, nothing staged"
srv "team add allies"; srv "team modify allies friendlyFire false"; srv "team join allies Juca"; srv "team join allies Bia"
stage; low
ask "data get entity Bia Health"
shiftclick; both 01_team_invite_refused
ask "respectcraft admin finisher Juca Bia respectcraft:takedown"
ask "calamidade status"
srv "team leave Juca"; srv "team leave Bia"; srv "team remove allies"

step "2 PvP natural flow: Shift + right click on Bia, Bia accepts in her own chat, execution kills Bia"
stage; low
ask "data get entity Bia Health"
shiftclick; on $B shot 02_invite_bia
chat $B "respectcraft test finisher accept"
sleep 1.2; both 02_mid_a
sleep 1.3; both 02_mid_b
on $A rec 02_scene_juca 7; on $B rec 02_scene_bia 7
sleep 7.5; both 02_after
ask "data get entity Bia Health"
ask "scoreboard players get Juca pk"
ask "scoreboard players get Bia bd"
ask "execute as Bia run respectcraft test defeats get"
# Tela de morte da Bia: botao "Respawn" (GUI 2, 854x480: x = 427, y = 214 com a barra de titulo da janela).
on $B shot 02_death_screen_bia
on $B xdotool mousemove 427 214 click 1; sleep 4; on $B shot 02_respawned_bia
# Dai em diante, renascer sem a tela de morte.
srv "gamerule doImmediateRespawn true"
ask "data get entity Bia Health"
stage; sleep 1; both 02_nameplate
clean

step "3 Totem of Undying on the player victim: FAILED, totem used, Bia knocked down and recovers"
stage
srv "item replace entity Bia weapon.offhand with minecraft:totem_of_undying"; sleep 1
ask "respectcraft admin finisher Juca Bia respectcraft:takedown"
sleep 3.1
# Recuperacao caida (10 s): logo depois do FAILED, Bia tenta andar e deve continuar no lugar.
ask "data get entity Bia Pos"
on $B hold w 1.2; ask "data get entity Bia Pos"
both 03_knocked_down
ask "data get entity Bia Health"
ask "data get entity Bia Inventory"
ask "execute as Bia run respectcraft test defeats get"
sleep 7
# Depois de "Recovered." ela anda de novo.
on $B hold w 1.0; ask "data get entity Bia Pos"
clean

step "4 friendly fire turned off in the middle of the scene: execution refused at the final blow"
srv "team add allies"; srv "team modify allies friendlyFire false"; srv "team join allies Juca"
stage
srv "effect give Bia minecraft:instant_health 1 4 true"; sleep 1
ask "respectcraft admin finisher Juca Bia respectcraft:takedown"; sleep 0.5
srv "team join allies Bia"
sleep 2.5; both 04_after
ask "data get entity Bia Health"
# A vitima se solta da recuperacao com o comando no proprio chat.
chat $B "stopitstopnow"; sleep 1
on $B hold w 0.8; ask "data get entity Bia Pos"
srv "team leave Juca"; srv "team leave Bia"; srv "team remove allies"

step "5 victim presses Shift in her client during the combo: CANCELLED, both cameras restored"
stage
ask "respectcraft admin finisher Juca Bia respectcraft:combo"; sleep 2; both 05_mid
on $B hold shift 0.8; sleep 1.5; both 05_after
ask "data get entity Bia Health"
sleep 1

step "6 executor disconnects in the middle of the combo: CANCELLED, Bia free; Juca comes back clean"
stage
ask "respectcraft admin finisher Juca Bia respectcraft:combo"; sleep 2
DISPLAY=$A closeclient; on $B shot 06_after_bia
ask "data get entity Bia Pos"
on $B hold w 1.0; ask "data get entity Bia Pos"
relaunch $A Juca
srv "effect give Juca minecraft:resistance 1200 4 true"
stage; sleep 1; both 06_rejoined

step "7 victim disconnects in the middle of the combo: CANCELLED, Juca restored; Bia comes back clean"
stage
ask "respectcraft admin finisher Juca Bia respectcraft:combo"; sleep 2
DISPLAY=$B closeclient; on $A shot 07_after_juca
relaunch $B Bia
stage; sleep 1; both 07_rejoined
ask "data get entity Bia Health"

step "8 victim changes dimension in the middle of the combo: CANCELLED, Bia arrives in the Inverted Realm, back again"
stage
ask "respectcraft admin finisher Juca Bia respectcraft:combo"; sleep 1.5
ask "respectcraft realm enter Bia"; sleep 4; both 08_realm
ask "respectcraft realm leave Bia"; sleep 4
stage; sleep 1; both 08_back

step "9 mob vs player, natural opt-in API: zombie executes Bia, Juca watches from the side"
srv "tp Juca 4.5 -60 1.5 90 0"; srv "tp Bia 0.5 -60 2.5 180 0"; sleep 1
srv "summon minecraft:zombie 0.5 -60 0.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"zm\"],CustomName:'\"Zed\"',ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}"; sleep 2
chat $B "respectcraft test finisher mobs on"
low
chat $B "respectcraft test finisher mobtest"
sleep 1.2; both 09_mid
sleep 3; both 09_after
ask "data get entity Bia Health"
ask "execute as Bia run respectcraft test defeats get"
sleep 3
srv "kill @e[tag=zm]"; clean

step "10 mob vs player with the zombie AI on: its own blow is part of the scene (no cancel); Bia executed"
srv "tp Juca 4.5 -60 1.5 90 0"; srv "tp Bia 0.5 -60 2.5 180 0"; sleep 1
srv "summon minecraft:zombie 0.5 -60 0.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"zm\"],CustomName:'\"Zed\"',ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}"; sleep 2
ask "respectcraft admin finisher @e[tag=zm,limit=1] Bia respectcraft:combo"; sleep 0.5
srv "data merge entity @e[tag=zm,limit=1] {NoAI:0b}"
sleep 3; both 10_mid
sleep 4.5; both 10_after
ask "data get entity Bia Health"
sleep 3
srv "kill @e[tag=zm]"; clean

step "11 a third player (Juca) hits the zombie during the mob scene: CANCELLED, Bia alive"
srv "tp Bia 0.5 -60 2.5 180 0"; srv "tp Juca 2.5 -60 0.5 90 0"; sleep 1
srv "summon minecraft:zombie 0.5 -60 0.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"zm\"],CustomName:'\"Zed\"',ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}"; sleep 2
ask "respectcraft admin finisher @e[tag=zm,limit=1] Bia respectcraft:combo"; sleep 1.5
on $A xdotool click 1; sleep 1.5; both 11_after
ask "data get entity Bia Health"
ask "data get entity @e[tag=zm,limit=1] Health"
srv "kill @e[tag=zm]"; clean

step "12 Juca starts watching in the middle of the mob scene (tracking sync), then Bia is executed"
srv "tp Bia 0.5 -60 2.5 180 0"; srv "tp Juca 600.5 -60 0.5 0 0"; sleep 5
srv "summon minecraft:zombie 0.5 -60 0.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"zm\"],CustomName:'\"Zed\"',ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}"; sleep 2
ask "respectcraft admin finisher @e[tag=zm,limit=1] Bia respectcraft:combo"; sleep 1
srv "tp Juca 4.5 -60 1.5 90 0"; sleep 2.5; both 12_tracking_mid
sleep 4; both 12_after
ask "data get entity Bia Health"
sleep 3
srv "kill @e[tag=zm]"; clean
ask "execute as Bia run respectcraft test defeats get"
ask "execute as Juca run respectcraft test defeats get"
ask "scoreboard players get Bia bd"

step "13 server restart with pvp=false: counters persisted; natural invite and admin start refused"
DISPLAY=$A closeclient; DISPLAY=$B closeclient
srv "stop"; sleep 20
sed -i 's/^pvp=.*//' "$REPO/run/server.properties"; echo "pvp=false" >> "$REPO/run/server.properties"
# Mesmo servidor e mundo; start_server regravaria server.properties sem o pvp=false.
rm -f "$OUT/srv.fifo"; mkfifo "$OUT/srv.fifo"; mv "$OUT/server.log" "$OUT/server-1.log"
(cd "$REPO" && ./gradlew runServer --no-daemon --no-configuration-cache --console=plain < "$OUT/srv.fifo" > "$OUT/server.log" 2>&1) &
sleep 100000 > "$OUT/srv.fifo" &
until grep -q 'Done (' "$OUT/server.log" 2>/dev/null; do sleep 3; done
relaunch $A Juca; relaunch $B Bia
ask "execute as Bia run respectcraft test defeats get"
stage; low; sleep 1; both 13_nameplate
shiftclick; both 13_pvp_invite_refused
ask "respectcraft admin finisher Juca Bia respectcraft:takedown"
ask "data get entity Bia Health"
DISPLAY=$A closeclient; DISPLAY=$B closeclient
srv "stop"; sleep 15
step done
{
  echo "## Sessoes (log do servidor)"
  cat "$OUT/server-1.log" "$OUT/server.log" | grep -E 'Finisher respectcraft:[a-z]+ (by|refused)|Finisher refused|was executed|joined the game|left the game|used a totem|cannot be used|PvP is not allowed' | grep -v DEBUG
  echo; echo "## Chat do Juca"; cat "$OUT"/client-Juca*.log 2>/dev/null | grep '\[CHAT\]' | sed 's/.*\[CHAT\] //'
  echo; echo "## Chat da Bia"; cat "$OUT"/client-Bia*.log 2>/dev/null | grep '\[CHAT\]' | sed 's/.*\[CHAT\] //'
  echo; echo "## Erros dos clientes e do servidor"
  cat "$OUT"/client-*.log "$OUT/server-1.log" "$OUT/server.log" | grep -E 'Exception|NoSuchMethod|NoClassDef|\[Render thread/ERROR\]|\[Server thread/ERROR\]' | sort | uniq -c | head -40
  echo; echo "## Consultas"; cat "$OUT/consultas.txt"
} > "$OUT/resumo.txt"
