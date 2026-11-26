#!/usr/bin/env bash
# Consolidacao (4.10.1 + 4.10.2 + 4.11 finisher/chefes + 4.11 avisos/caca + Calamidade 0.14.0): pontos de integracao
# com TRES clientes reais (Xvfb, llvmpipe) e servidor dedicado de desenvolvimento. Ana paga, Juca caca, Bia e o alvo.
# Cobre: execucao SUCCESS com contrato aceito (paga uma vez so, contador +1), totem (FAILED: nao paga, contador igual),
# cancelamento (CANCELLED: nao paga), recusa contra chefe, efeitos encerrados e servidor parando no meio da cena.
# Comandos dos jogadores vao pelo console com `execute as` (sem xdotool). Resumo em $OUT/consultas.txt.
cd "$(dirname "$0")/../../.."
export OUT=${OUT:-smoke/consolidacao}
. tools/visual-test/helpers.sh
A=:99 B=:98 C=:97
step() { echo "=== $* ($(date +%T))" | tee -a "$OUT/passos.txt"; }
ask() { local n; n=$(wc -l < "$OUT/server.log"); srv "$*"; sleep 0.8; echo "> $*" >> "$OUT/consultas.txt"; tail -n +"$((n + 1))" "$OUT/server.log" | grep -v DEBUG >> "$OUT/consultas.txt"; }
note() { echo "# $*" | tee -a "$OUT/consultas.txt"; }
stage() { srv "tp Juca 0.5 -60 0.5 0 0"; srv "tp Bia 0.5 -60 2.5 180 0"; srv "tp Ana 6.5 -60 0.5 90 0"; sleep 1.5; }
lastid() { grep -oE '\[[0-9a-f]{6,8}\] Bia' "$OUT/server.log" | tail -1 | tr -d '[] ' | sed 's/Bia$//'; }
post() {  # Ana paga $1 diamantes por Bia; Juca aceita
  srv "item replace entity Ana weapon.mainhand with minecraft:diamond $1"
  ask "execute as Ana run respectcraft bounty place Bia 1"
  ask "respectcraft bounty list"
  local id; id=$(lastid); note "bounty id = $id"
  ask "execute as Juca run respectcraft bounty accept $id"
}
diamonds() { ask "clear $1 minecraft:diamond 0"; }

rm -rf "$REPO/run/world"
start_display $A; start_display $B; start_display $C
step server; start_server
srv "gamerule doMobSpawning false"; srv "gamerule naturalRegeneration false"; srv "gamerule doImmediateRespawn true"
srv "gamerule keepInventory true"; srv "difficulty easy"; srv "time set day"; srv "gamerule doDaylightCycle false"
step clients; DISPLAY=$A launch Juca; DISPLAY=$B launch Bia; DISPLAY=$C launch Ana
srv "kill @e[type=!minecraft:player]"
for p in Juca Bia Ana; do srv "gamemode survival $p"; srv "clear $p"; done
srv "effect give Juca minecraft:resistance 1200 4 true"
ask "list"

step "1 SUCCESS: contrato aceito, execucao mata Bia, recompensa paga uma vez"
stage; post 5
diamonds Juca
ask "respectcraft admin finisher Juca Bia respectcraft:takedown"
sleep 3; on $A shot 01_success_mid_juca; on $B shot 01_success_mid_bia
sleep 9; on $A shot 01_success_end_juca
ask "respectcraft bounty list"; diamonds Juca; diamonds Ana
note "pagamentos do contrato: $(grep -c 'claimed by Juca' "$OUT/server.log")"
ask "calamidade status"

step "2 FAILED (totem): contrato aceito, Bia sobrevive, nada pago"
stage; post 3
srv "item replace entity Bia weapon.offhand with minecraft:totem_of_undying"
ask "respectcraft admin finisher Juca Bia respectcraft:takedown"
sleep 12; on $B shot 02_totem_end_bia
ask "respectcraft bounty list"; diamonds Juca
note "pagamentos do contrato: $(grep -c 'claimed by Juca' "$OUT/server.log")"
srv "execute as Bia run stopitstopnow"; sleep 1
ask "calamidade status"

step "3 CANCELLED: Bia interrompe a cena, nada pago, contrato continua"
stage; srv "effect give Bia minecraft:instant_health 1 4 true"
ask "respectcraft admin finisher Juca Bia respectcraft:combo"
sleep 2; ask "execute as Bia run stopitstopnow"; sleep 2
on $B shot 03_cancel_bia
ask "respectcraft bounty list"; diamonds Juca
note "pagamentos do contrato: $(grep -c 'claimed by Juca' "$OUT/server.log")"
ask "calamidade status"

step "4 chefe: finalizacao comum recusada contra o Wither"
srv "summon minecraft:wither 0.5 -60 6 {Tags:[\"rc_w\"],NoAI:1b,PersistenceRequired:1b}"; sleep 1
ask "respectcraft finishers @e[tag=rc_w,limit=1]"
ask "respectcraft admin finisher Juca @e[tag=rc_w,limit=1] respectcraft:takedown"
ask "calamidade status"
srv "kill @e[tag=rc_w]"; sleep 1

step "5 servidor parando no meio da cena"
stage
ask "respectcraft admin finisher Juca Bia respectcraft:combo"
sleep 2; srv "stop"
until ! pgrep -f 'runServer' > /dev/null; do sleep 2; done
grep -E 'server stopping|CANCELLED|Stopping server|Saving worlds|Exception' "$OUT/server.log" | grep -v DEBUG | tail -15 >> "$OUT/consultas.txt"
step fim
pkill -f runClient || true; pkill Xvfb || true
