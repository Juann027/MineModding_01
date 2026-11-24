#!/usr/bin/env bash
# Migracao para o Calamidade_Awakening 0.14.0: cliente real (Xvfb, llvmpipe) conectado ao servidor dedicado.
# Confere: COMBO completo (golpes 1/2/3 com luz, anel e impact frame; camera cinematografica), TAKEDOWN completo,
# abort no meio (/stopitstopnow), recusa da finalizacao comum contra chefe (Wither) e nada ativo depois de cada saida
# (/calamidade status no servidor e F7 do Calamidade no cliente).
cd "$(dirname "$0")/../../.."
export OUT=smoke/visual_cal014; mkdir -p "$OUT"
. tools/visual-test/helpers.sh
step() { echo "=== $* ($(date +%T))"; }
status() { srv "calamidade status"; sleep 0.5; }
start_display
step server; start_server
srv "gamerule doMobSpawning false"; srv "difficulty easy"; srv "kill @e[type=!minecraft:player]"
step client; launch Juca
srv "kill @e[type=!minecraft:player]"
srv "gamemode survival Juca"; srv "effect give Juca minecraft:resistance 900 4 true"
srv "effect give Juca minecraft:saturation 900 1 true"
srv "time set day"; srv "weather clear"; srv "gamerule doDaylightCycle false"
zombie() { srv "execute at Juca run summon minecraft:zombie ^ ^ ^2 {NoAI:1b,PersistenceRequired:1b,Tags:[\"$1\"],ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}"; sleep 2; }
shot 00_before
key F7; sleep 1; shot 01_f7_idle
for fin in combo takedown; do
  step "$fin complete"
  zombie "z_$fin"
  case $fin in combo) secs=8;; takedown) secs=4;; esac
  rec "$fin" "$secs"
  srv "respectcraft admin finisher Juca @e[tag=z_$fin,limit=1] respectcraft:$fin"
  sleep "$secs"; sleep 1.5
  shot "${fin}_after_f7"; status
  srv "kill @e[tag=z_$fin]"; sleep 1
done
step abort
zombie z_abort
rec abort_combo 4
srv "respectcraft admin finisher Juca @e[tag=z_abort,limit=1] respectcraft:combo"; sleep 1.6
status
srv "execute as Juca run stopitstopnow"; sleep 1.5
shot abort_after_f7; status
srv "kill @e[tag=z_abort]"; sleep 2
step boss
srv "execute at Juca run summon minecraft:wither ^ ^ ^3 {NoAI:1b,Tags:[\"boss\"]}"; sleep 3
srv "respectcraft admin finisher Juca @e[tag=boss,limit=1] respectcraft:takedown"; sleep 2; status
srv "kill @e[tag=boss]"; sleep 1
key F7; sleep 0.5; shot zz_final
srv "stop"; sleep 12
step done
