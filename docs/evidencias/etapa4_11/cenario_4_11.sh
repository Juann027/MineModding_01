#!/usr/bin/env bash
# Etapa 4.11: dois clientes reais (Juca, Bia). Espectador no meio da cena, /kill no meio da cena (passa e cancela),
# execucao PvP com sucesso (contador +1), Totem (FAILED, contador nao muda), Shift (CANCELLED) e /stop no meio.
cd "$(dirname "$0")/../../.."
export OUT=${OUT:-smoke/visual411}
. tools/visual-test/helpers.sh
A=:99 B=:98
step() { echo "=== $* ($(date +%T))" | tee -a "$OUT/passos.txt"; }
ask() { local n; n=$(wc -l < "$OUT/server.log"); srv "$*"; sleep 0.8; echo "> $*" >> "$OUT/consultas.txt"; tail -n +"$((n + 1))" "$OUT/server.log" | grep -v DEBUG >> "$OUT/consultas.txt"; }
both() { on $A shot "$1_juca"; on $B shot "$1_bia"; }
relaunch() { [ -f "$OUT/client-$2.log" ] && mv "$OUT/client-$2.log" "$OUT/client-$2-$(date +%H%M%S).log"; DISPLAY=$1 launch "$2"; }
reset() { srv "gamemode survival Juca"; srv "gamemode survival Bia"; srv "effect clear Bia"; srv "effect give Bia minecraft:instant_health 1 10";
  srv "tp Juca 0.5 -60 0.5 0 0"; srv "tp Bia 0.5 -60 2.0 180 0"; sleep 16; }   # 16 s: cooldown/recuperacao
zombie() { srv "summon minecraft:zombie $1 {NoAI:1b,PersistenceRequired:1b,Tags:[\"$2\"],CustomName:'\"$2\"',ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}"; }

rm -rf "$REPO/run/world"
start_display $A; start_display $B
step server; start_server
srv "difficulty easy"; srv "gamerule doMobSpawning false"; srv "time set day"; srv "gamerule doDaylightCycle false"
srv "gamerule doImmediateRespawn true"
relaunch $A Juca; relaunch $B Bia
reset

step "1 executor vira espectador no meio da cena"
ask "respectcraft admin finisher Juca Bia respectcraft:combo"; sleep 1.5; both 01a_mid
ask "gamemode spectator Juca"; sleep 1; both 01b_after; ask "data get entity Bia Health"
on $A hold w 0.8; ask "data get entity Juca Pos"
reset
step "1b vitima vira espectadora no meio da cena"
ask "respectcraft admin finisher Juca Bia respectcraft:combo"; sleep 1.5
ask "gamemode spectator Bia"; sleep 1; both 01c_victim_spectator
reset

step "2 /kill na vitima no meio da cena"
ask "respectcraft admin finisher Juca Bia respectcraft:combo"; sleep 1.5
ask "kill Bia"; sleep 1.5; both 02_kill
reset
step "2b /kill no mob vitima no meio da cena"
zombie "0.5 -60 2.0" zk; sleep 1
ask "respectcraft admin finisher Juca @e[tag=zk,limit=1] respectcraft:combo"; sleep 1.5
ask "kill @e[tag=zk]"; sleep 1; ask "execute if entity @e[tag=zk]"; both 02b_kill_mob
reset

step "3 execucao PvP com sucesso (contador +1)"
ask "respectcraft admin finisher Juca Bia respectcraft:takedown"; sleep 1.5; both 03a_mid; sleep 3; both 03b_done
reset

step "4 Totem na vitima: FAILED, contador nao sobe"
srv "item replace entity Bia weapon.offhand with minecraft:totem_of_undying"
ask "respectcraft admin finisher Juca Bia respectcraft:takedown"; sleep 4.5; both 04_totem
ask "data get entity Bia Health"; ask "data get entity Bia HandItems"
sleep 11
reset

step "5 Shift da vitima cancela"
ask "respectcraft admin finisher Juca Bia respectcraft:combo"; sleep 1.5
on $B hold shift 0.6; sleep 1; both 05_shift
reset

step "6 /stop no meio da cena"
ask "respectcraft admin finisher Juca Bia respectcraft:combo"; sleep 1.5; both 06a_mid
srv "stop"; until ! pgrep -f "runServer" > /dev/null; do sleep 2; done; sleep 2; both 06b_stopped
DISPLAY=$A closeclient; DISPLAY=$B closeclient
step done
{
  echo "## Sessoes, contador e mortes (log do servidor)"
  grep -E 'Finisher respectcraft:[a-z]+ by|Defeat counter|joined the game|left the game|Stopping server|was executed|was killed|fell out|died' "$OUT/server.log" | grep -v DEBUG
  echo; echo "## Chat"; cat "$OUT"/client-*.log | grep '\[CHAT\]' | sed 's/.*\[CHAT\] //' | grep -v '^\[Server:'
  echo; echo "## Erros (fora rede/som do ambiente)"
  cat "$OUT"/client-*.log "$OUT"/server.log | grep -E 'Exception|NoSuchMethod|NoClassDef|/ERROR\]' | grep -vE 'minecraftservices|sessionserver|SoundSystem|OpenAL|MinecraftClientException|UnknownHost|Yggdrasil|NativeIoException|Connection reset' | sort | uniq -c | head -40
  echo; echo "## Consultas"; cat "$OUT/consultas.txt"
} > "$OUT/resumo.txt"
