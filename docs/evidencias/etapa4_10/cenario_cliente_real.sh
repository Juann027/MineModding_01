#!/usr/bin/env bash
# Etapa 4.10: cliente real (Xvfb, llvmpipe) x zumbi vanilla com os efeitos do Calamidade_Awakening nas finalizacoes.
# Confere: efeito HERO da cena no F7 do Calamidade, golpes/quedas, nada ativo depois do fim ou do abort
# (/calamidade status no servidor e F7 no cliente) e a atmosfera contra chefe (Wither, tag c:bosses).
cd "$(dirname "$0")/../../.."
export OUT=smoke/visual410
. tools/visual-test/helpers.sh
step() { echo "=== $* ($(date +%T))"; }
status() { srv "calamidade status"; sleep 0.5; }
start_display
step server; start_server
# Zumbis somem no pacifico; em "easy" sem spawn natural (monstros em volta cancelariam a cena com dano).
srv "gamerule doMobSpawning false"; srv "difficulty easy"; srv "kill @e[type=!minecraft:player]"
step client; launch Juca
srv "kill @e[type=!minecraft:player]"
srv "gamemode survival Juca"; srv "effect give Juca minecraft:resistance 900 4 true"
srv "effect give Juca minecraft:saturation 900 1 true"
srv "time set day"; srv "weather clear"; srv "gamerule doDaylightCycle false"
# Zumbi vanilla parado e com capacete (nao queima de dia), na frente do jogador.
zombie() { srv "execute at Juca run summon minecraft:zombie ^ ^ ^2 {NoAI:1b,PersistenceRequired:1b,Tags:[\"$1\"],ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}"; sleep 2; }
key F7; sleep 1; shot 00_f7_idle
for fin in combo rasteira takedown; do
  step "$fin complete"
  zombie "z_$fin"
  case $fin in combo) secs=8;; rasteira) secs=3;; takedown) secs=4;; esac
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
rec boss_takedown 5
srv "respectcraft admin finisher Juca @e[tag=boss,limit=1] respectcraft:takedown"; sleep 2.2; shot boss_mid
sleep 3.5; shot boss_after_f7; status
srv "kill @e[tag=boss]"; sleep 1
srv "stop"; sleep 12
step done
