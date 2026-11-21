#!/usr/bin/env bash
# Etapa 4.10.1: cliente real (Xvfb, llvmpipe) + servidor dedicado de desenvolvimento. Execucoes reais e regra de chefes.
# Confere: finalizacao comum mata zumbi (loot, XP, estatistica, conquista, mensagem de morte com o matador), caminho
# natural (Shift + clique direito, mao vazia), totem impede a morte (FAILED), cancelamento e dano externo sem golpe
# final, Wither recusado antes da cena (comando de admin e clique), Wither morre por dano comum, saida do cliente
# cancela sem golpe final. Resumo em $OUT/resumo.txt.
cd "$(dirname "$0")/../../.."
export OUT=${OUT:-smoke/visual4101}
. tools/visual-test/helpers.sh
step() { echo "=== $* ($(date +%T))" | tee -a "$OUT/passos.txt"; }
# Consulta no console e guarda a resposta (as ultimas linhas novas do log do servidor).
ask() { local n; n=$(wc -l < "$OUT/server.log"); srv "$*"; sleep 0.8; echo "> $*" >> "$OUT/consultas.txt"; tail -n +"$((n + 1))" "$OUT/server.log" | grep -v DEBUG >> "$OUT/consultas.txt"; }
start_display
step server; start_server
srv "gamerule doMobSpawning false"; srv "difficulty easy"; srv "kill @e[type=!minecraft:player]"
step client; launch Juca
srv "kill @e[type=!minecraft:player]"
srv "gamemode survival Juca"; srv "effect give Juca minecraft:resistance 900 4 true"
srv "effect give Juca minecraft:saturation 900 1 true"
srv "time set day"; srv "weather clear"; srv "gamerule doDaylightCycle false"
srv "scoreboard objectives add zk minecraft.killed:minecraft.zombie"
srv "tp Juca 0.5 -60 0.5 0 0"; sleep 1
# Zumbi vanilla parado, com capacete (nao queima de dia) e nome (o servidor registra a morte de entidade nomeada).
# $1 = tag, $2 = NBT extra.
zombie() { srv "execute at Juca run summon minecraft:zombie ^ ^ ^2 {NoAI:1b,PersistenceRequired:1b,Tags:[\"$1\"],CustomName:'\"$1\"',ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]$2}"; sleep 2; }

step "1 takedown kills a zombie (loot, XP, stats, advancement)"
zombie z_kill ',HandItems:[{id:"minecraft:diamond",count:1},{}],HandDropChances:[2.0f,0.0f]'
ask "xp query Juca points"
srv "respectcraft admin finisher Juca @e[tag=z_kill,limit=1] respectcraft:takedown"; sleep 1.5; shot 01_takedown_mid
sleep 2.4; shot 01_takedown_end
ask "execute if entity @e[type=minecraft:experience_orb]"
sleep 4
ask "execute if entity @e[tag=z_kill]"
ask "clear Juca minecraft:diamond 0"
ask "execute if entity @e[type=minecraft:item,nbt={Item:{id:\"minecraft:diamond\"}}]"
ask "xp query Juca points"
ask "scoreboard players get Juca zk"
srv "kill @e[type=minecraft:item]"; srv "kill @e[type=minecraft:experience_orb]"; srv "clear Juca"

step "2 natural flow: Shift + right click with empty hand on a weakened zombie"
srv "tp Juca 0.5 -60 0.5 0 0"; sleep 1
zombie z_natural ',Health:3.0f'
xdotool keydown shift; sleep 1.2; xdotool click 3; sleep 0.6; xdotool keyup shift
sleep 1.4; shot 02_natural_mid; sleep 6.5; shot 02_natural_end
ask "execute if entity @e[tag=z_natural]"
ask "scoreboard players get Juca zk"
srv "kill @e[type=minecraft:item]"; srv "kill @e[type=minecraft:experience_orb]"; srv "clear Juca"

step "3 totem of undying prevents the execution death (FAILED)"
zombie z_totem ',HandItems:[{id:"minecraft:totem_of_undying",count:1},{}]'
srv "respectcraft admin finisher Juca @e[tag=z_totem,limit=1] respectcraft:takedown"; sleep 3.1; shot 03_totem_end
sleep 1
ask "execute if entity @e[tag=z_totem]"
ask "data get entity @e[tag=z_totem,limit=1] HandItems"
srv "kill @e[tag=z_totem]"; sleep 1; srv "kill @e[type=minecraft:item]"; srv "kill @e[type=minecraft:experience_orb]"

step "4 cancelled with /stopitstopnow: no execution"
zombie z_cancel
srv "respectcraft admin finisher Juca @e[tag=z_cancel,limit=1] respectcraft:combo"; sleep 1.6; shot 04_cancel_mid
srv "execute as Juca run stopitstopnow"; sleep 1.5; shot 04_cancel_after
sleep 6.5
ask "execute if entity @e[tag=z_cancel]"
ask "data get entity @e[tag=z_cancel,limit=1] Health"
srv "kill @e[tag=z_cancel]"; sleep 1; srv "kill @e[type=minecraft:item]"; srv "kill @e[type=minecraft:experience_orb]"

step "5 external damage during the scene: scene cancelled (damage discarded, rule of 4.8), no execution"
zombie z_external
srv "respectcraft admin finisher Juca @e[tag=z_external,limit=1] respectcraft:combo"; sleep 1.5
ask "kill @e[tag=z_external]"; sleep 7.5; shot 05_external_after
ask "execute if entity @e[tag=z_external]"
ask "scoreboard players get Juca zk"
# Fora da cena, a morte comum funciona e nao credita execucao nenhuma.
ask "kill @e[tag=z_external]"; sleep 1.5
ask "execute if entity @e[tag=z_external]"
srv "kill @e[type=minecraft:item]"; srv "kill @e[type=minecraft:experience_orb]"; srv "kill @e[type=minecraft:zombie]"

step "6 Wither: regular finisher refused before the scene; ordinary damage still kills it"
srv "clear Juca"; srv "tp Juca 0.5 -60 0.5 0 0"; sleep 1
srv "execute at Juca run summon minecraft:wither ^ ^ ^3 {NoAI:1b,PersistenceRequired:1b,Tags:[\"boss\"],CustomName:'\"Boss\"'}"; sleep 3
ask "respectcraft admin finisher Juca @e[tag=boss,limit=1] respectcraft:takedown"
ask "calamidade status"
shot 06_wither_admin_refused
# Shift segurado antes do clique: o servidor precisa receber o agachar antes da interacao.
xdotool keydown shift; sleep 1.2; xdotool click 3; sleep 0.6; xdotool keyup shift; sleep 1
shot 06_wither_click_refused
ask "calamidade status"
srv "item replace entity Juca weapon.mainhand with minecraft:netherite_sword"; sleep 2
ask "data get entity @e[tag=boss,limit=1] Health"
xdotool click 1; sleep 1; shot 06_wither_melee
ask "data get entity @e[tag=boss,limit=1] Health"
ask "damage @e[tag=boss,limit=1] 400 minecraft:player_attack by Juca"
sleep 3; shot 06_wither_killed
ask "execute if entity @e[tag=boss]"
srv "kill @e[type=minecraft:item]"; srv "kill @e[type=minecraft:experience_orb]"; srv "clear Juca"

step "7 disconnect during the scene: no execution"
zombie z_disconnect
srv "respectcraft admin finisher Juca @e[tag=z_disconnect,limit=1] respectcraft:combo"; sleep 1.5
closeclient; sleep 3
ask "execute if entity @e[tag=z_disconnect]"
ask "data get entity @e[tag=z_disconnect,limit=1] Health"
srv "stop"; sleep 12
step done
{
  echo "## Sessoes (log do servidor)"
  grep -E 'Finisher respectcraft:[a-z]+ (by|refused)|Named entity|Boss defeat|has made the advancement|cannot be used' "$OUT/server.log" | grep -v DEBUG
  echo; echo "## Chat do cliente"; grep '\[CHAT\]' "$OUT/client-Juca.log" | sed 's/.*\[CHAT\] //' 
  echo; echo "## Consultas"; cat "$OUT/consultas.txt"
} > "$OUT/resumo.txt"
