#!/usr/bin/env bash
# Etapa 4.8: cliente real x Leao do Fim (rig GeckoLib proprio). Sem clips de mob: o Leao fica parado (Hold).
# HISTORICO: o Leao do Fim foi removido na Etapa 4.9.1; este roteiro nao roda mais (registro da evidencia da 4.8).
# Para repetir o cenario hoje, troque respectcraft:leao_do_fim por minecraft:zombie.
cd /home/user/MineModding_01
export OUT=smoke/visual48
. tools/visual-test/helpers.sh
step() { echo "=== $* ($(date +%T))"; }
start_display
step server; start_server
step client; launch Juca
srv "gamemode survival Juca"; srv "effect give Juca minecraft:resistance 600 4 true"
srv "time set day"; srv "weather clear"
key F5; sleep 1
# 1) PLAYER_VS_MOB completo: executor jogador, vitima Leao (sem contador para mob).
step pvm-complete
srv "execute at Juca run summon respectcraft:leao_do_fim ^ ^ ^2 {Tags:[\"t1\"]}"; sleep 2
srv "respectcraft admin finisher Juca @e[tag=t1,limit=1] respectcraft:takedown"; sleep 1.6; shot 01_player_vs_leao_mid
sleep 3; shot 02_player_vs_leao_after
srv "respectcraft anim stop @a"; sleep 1
# 2) Cancelamento: Leao sai da dimensao no meio da cena (remocao) -> limpeza; depois a mesma dupla volta a funcionar.
step pvm-abort-removed
srv "respectcraft admin finisher Juca @e[tag=t1,limit=1] respectcraft:combo"; sleep 2; shot 03_combo_mid
srv "execute in minecraft:the_nether run tp @e[tag=t1] 0 100 0"; sleep 2
srv "respectcraft anim stop @a"; sleep 1; shot 04_after_removal
# 3) Cancelamento por morte do Leao (dano = cancela a cena, dano anulado), sem sessao presa: segunda tentativa recomeca.
step pvm-abort-damage
srv "execute at Juca run summon respectcraft:leao_do_fim ^ ^ ^2 {Tags:[\"t2\"]}"; sleep 2
srv "respectcraft admin finisher Juca @e[tag=t2,limit=1] respectcraft:combo"; sleep 2
srv "kill @e[tag=t2]"; sleep 2
srv "respectcraft anim stop @a"; sleep 1
srv "respectcraft admin finisher Juca @e[tag=t2,limit=1] respectcraft:takedown"; sleep 4
# 4) MOB_VS_PLAYER: executor Leao, vitima jogador (contador do jogador continua +1, como antes).
step mvp
srv "execute at Juca run summon respectcraft:leao_do_fim ^ ^ ^2 {Tags:[\"t3\"]}"; sleep 2
srv "respectcraft admin finisher @e[tag=t3,limit=1] Juca respectcraft:takedown"; sleep 1.6; shot 05_leao_vs_player_mid
sleep 4; shot 06_leao_vs_player_after
# 5) Saida do jogador no meio da cena com mob: nada preso no servidor.
step disconnect
srv "execute as Juca run stopitstopnow"; sleep 1
srv "respectcraft admin finisher Juca @e[tag=t3,limit=1] respectcraft:combo"; sleep 2
srv "kick Juca"; sleep 4
closeclient
step relaunch; launch Juca
srv "respectcraft anim stop @a"; sleep 1
srv "respectcraft admin finisher Juca @e[tag=t3,limit=1] respectcraft:takedown"; sleep 1.6; shot 07_after_reconnect_mid
sleep 4
srv "stop"; sleep 10
step done
