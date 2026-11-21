#!/usr/bin/env bash
# Teste de fumaça do servidor dedicado: sobe o servidor, confirma carregamento sem classes de cliente,
# invoca zumbis vanilla e consulta os catálogos e o autoteste das regras.
set -uo pipefail
export LC_ALL=C.UTF-8

mkdir -p run smoke
echo "eula=true" > run/eula.txt
cat > run/server.properties <<'PROPS'
online-mode=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
PROPS

FAILED=0
fail() { echo "::error::$1"; FAILED=1; }

log=smoke/first.log; fifo=smoke/first.fifo
rm -f "$fifo"; mkfifo "$fifo"
./gradlew runServer --no-daemon --no-configuration-cache --console=plain < "$fifo" > "$log" 2>&1 &
pid=$!
exec 3>"$fifo"
ready=0
for _ in $(seq 1 900); do
  if grep -q 'Done (' "$log"; then ready=1; break; fi
  if ! kill -0 "$pid" 2>/dev/null; then break; fi
  sleep 1
done
if [ "$ready" != 1 ]; then
  fail "servidor não ficou pronto"
  exec 3>&-; kill "$pid" 2>/dev/null; wait "$pid"
else
  # O ultimo comando usa a raiz antiga de proposito: ela nao pode existir mais (Etapa 4.7.1).
  # Etapa 4.8: dois zumbis vanilla marcados (noite, para nao queimarem); uma finalizacao mob x mob precisa ser recusada
  # (sem jogador nao ha sessao). O Leao do Fim saiu do mod: o teste usa so mobs vanilla.
  # Etapa 4.9: Inverted Realm (dimensao em dados: gera a chegada e uma amostra 5x5) e as agua-vivas.
  # Etapa 4.10: /calamidade status confirma o Calamidade_Awakening carregado e nenhum efeito preso no servidor.
  # Etapa 4.10.1: classificacao de chefes e regra das finalizacoes (comuns nunca executam chefes) no servidor, e o
  # Wither continua morrendo por dano comum de combate (golpe de um golem; morto-vivo nao fere o Wither no vanilla),
  # anunciado como derrota NORMAL.
  for cmd in "time set midnight" "summon minecraft:zombie 0 -60 0" "summon minecraft:zombie 0 -60 0 {Tags:[\"rc_a\"],PersistenceRequired:1b}" "summon minecraft:zombie 2 -60 0 {Tags:[\"rc_b\"],PersistenceRequired:1b}" "respectcraft admin finisher @e[tag=rc_a,limit=1] @e[tag=rc_b,limit=1]" "summon minecraft:wither 8 -58 8 {Tags:[\"rc_w\"],NoAI:1b,PersistenceRequired:1b,CustomName:'\"SmokeWither\"'}" "summon minecraft:ender_dragon 20 -40 20 {Tags:[\"rc_d\"],NoAI:1b}" "respectcraft finishers @e[tag=rc_a,limit=1]" "respectcraft finishers @e[tag=rc_w,limit=1]" "respectcraft finishers @e[tag=rc_d,limit=1]" "summon minecraft:iron_golem 4 -60 8 {Tags:[\"rc_g\"],NoAI:1b,PersistenceRequired:1b}" "damage @e[tag=rc_w,limit=1] 2000 minecraft:mob_attack by @e[tag=rc_g,limit=1]" "kill @e[tag=rc_d]" "respectcraft anim list" "respectcraft selftest" "respectcraft skills list" "respectcraft finishers" "respectcraft speciality odds" "respectcraft realm check 2" "summon respectcraft:agua_viva_filhote 6 -55 6" "summon respectcraft:agua_viva 12 -50 12" "kill @e[type=respectcraft:agua_viva_filhote]" "calamidade status" "tupini selftest"; do
    echo "$cmd" >&3; sleep 5
  done
  echo "stop" >&3
  exec 3>&-
  for _ in $(seq 1 180); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
  kill "$pid" 2>/dev/null; wait "$pid"
fi

grep -v DEBUG "$log" | grep -E 'respectcraft|RespectCraft|Inverted Realm|Exception|Error|Summon|summon|Unknown|entity' | head -60
grep -q 'Summoned new Zombie' "$log" || fail "zumbi vanilla não foi invocado no servidor dedicado"

# Animation Manager carregado no servidor dedicado (catalogo e comando), sem classes de cliente.
grep -q 'Player animations: respectcraft:idle, respectcraft:wave' "$log" || fail "catálogo de animações de jogador não respondeu no servidor"
# Etapa 3: autoteste das regras (roleta, necessidades, habilidades, camera, Codecs) e catalogos.
grep -q 'RespectCraft self-test: OK' "$log" || { grep 'Self-test failed' "$log"; fail "autoteste das regras falhou"; }
grep -q 'Skills (18)' "$log" || fail "catálogo de habilidades não respondeu"
grep -q 'Finishers: respectcraft:takedown, respectcraft:rasteira' "$log" || fail "catálogo de finalizações não respondeu"
# Etapa 4.7: chances de raridade das Specialities carregadas da config do servidor (padrão aprovado).
grep -q 'boner = 0.5% (specialities: 1)' "$log" || fail "chances das Specialities não responderam"
# Etapa 4.7.1: mod id, config e comandos so com o nome RespectCraft.
[ -f run/config/respectcraft-speciality-server.toml ] || fail "config respectcraft-speciality-server.toml não foi gerada"
if ls run/config run/world/serverconfig 2>/dev/null | grep -i tupini; then fail "config com o prefixo antigo foi gerada"; fi
grep -q 'Unknown or incomplete command' "$log" || fail "a raiz de comando antiga /tupini ainda responde"
# Etapa 4.8: Rimulib 1.3.0 carregado no servidor dedicado e conectado pela API comum.
grep -q 'RespectCraft connected to Rimulib 1\.3\.' "$log" || fail "Rimulib não foi carregado/conectado no servidor dedicado"
# Etapa 4.8: finalizacao sem jogador recusada antes de comecar (nenhum mob fica preso numa sessao).
grep -q 'has no player participant' "$log" || fail "finalização mob x mob não foi recusada"
grep -q 'Could not start' "$log" || fail "comando de finalização mob x mob não respondeu com recusa"
# Etapa 4.10.1: zumbi = mob comum (todas as finalizacoes podem executa-lo); Wither e Dragao do Fim = chefes (c:bosses):
# toda finalizacao do catalogo e recusada contra eles. O Wither morre por dano comum (golpe de mob), sem imunidade.
grep -q 'Finisher eligibility for minecraft:zombie (MOB)' "$log" || fail "elegibilidade contra zumbi não respondeu"
grep -q 'Finisher eligibility for minecraft:wither (BOSS)' "$log" || fail "Wither não foi classificado como chefe"
grep -q 'Finisher eligibility for minecraft:ender_dragon (BOSS)' "$log" || fail "Dragão do Fim não foi classificado como chefe"
for id in takedown rasteira combo; do
  grep -q "respectcraft:$id: ELIGIBLE" "$log" || fail "finalização $id não ficou elegível contra mob comum"
  [ "$(grep -c "respectcraft:$id: BOSS_REQUIRES_EXCLUSIVE" "$log")" -ge 2 ] || fail "finalização $id não foi recusada contra os chefes"
done
grep -q 'Named entity .*SmokeWither.* died' "$log" || fail "o Wither não morreu por dano comum de combate"
grep -q 'Boss defeat minecraft:wither (NORMAL)' "$log" || fail "derrota comum do Wither não foi anunciada como NORMAL"
# Etapa 4.9: a dimensao Inverted Realm carrega (dimension_type, noise_settings, bioma e features em dados), gera
# terreno com o bioma certo e a hora fixa do ceu roxo; as agua-vivas sao invocadas no servidor dedicado.
grep -q 'Inverted Realm ready: arrival .* biome respectcraft:twisted_wilds, fixed time 12400' "$log" \
  || fail "dimensão Inverted Realm não carregou/gerou terreno"
grep -qE 'Summoned new (Baby Jellyfish|entity\.respectcraft\.agua_viva_filhote)' "$log" || fail "Baby Jellyfish não foi invocada"
grep -qE 'Summoned new (Giant Jellyfish|entity\.respectcraft\.agua_viva\b)' "$log" || fail "Giant Jellyfish não foi invocada"
# Etapa 4.10: Calamidade_Awakening 0.10.x carregado no servidor dedicado, efeitos das finalizacoes registrados no lado
# comum e nenhuma instancia ativa sem sessao (a recusa mob x mob acima nao comeca efeito nenhum).
grep -qE 'Calamidade_Awakening 0\.10\.[0-9]+ \(calamidade_awakening\)' "$log" \
  || fail "Calamidade_Awakening 0.10.x não foi carregado no servidor dedicado"
grep -q 'RespectCraft finisher FX registered on Calamidade_Awakening' "$log" \
  || fail "efeitos das finalizações não foram registrados no Calamidade"
grep -q 'Live instances on server: 0 / 64' "$log" || fail "/calamidade status não respondeu ou há efeito preso"
# Etapa 4.8: o jar do RespectCraft nao embute o Rimulib (o jogador instala ao lado).
jar=$(ls build/libs/respectcraft-*.jar 2>/dev/null | grep -v -- '-sources' | head -1)
if [ -z "$jar" ]; then fail "jar do RespectCraft não encontrado em build/libs"
elif unzip -l "$jar" | grep -E 'cyou/rimuru|META-INF/jarjar'; then fail "o jar embute classes do Rimulib"; fi
# Etapa 4.10: nem o Calamidade (o jogador instala ao lado; servidor e clientes na mesma versao).
if [ -n "$jar" ] && unzip -l "$jar" | grep -E 'dev/calamidade_awakening|calamidade_awakening-'; then
  fail "o jar embute o Calamidade_Awakening"
fi
# O Leao do Fim foi removido do mod (pedido do dono): nenhum recurso ou classe dele pode voltar ao jar.
if [ -n "$jar" ] && unzip -l "$jar" | grep -iE 'leao|lionxx'; then fail "o jar ainda contém recursos do Leão do Fim"; fi
# Etapa 4.8: nada fora de client importa classes exclusivas do cliente (Minecraft ou client.api do Rimulib).
if grep -rlE 'import (net\.minecraft\.client\.|cyou\.rimuru\.rimulib\.mc1211\.client\.|dev\.calamidade_awakening\.api\.v1\.client\.)' src/main/java --include=*.java \
    | grep -v '^src/main/java/com/respectcraft/client/'; then
  fail "classe comum importando código exclusivo do cliente"
fi
if grep -E 'NoClassDefFoundError|ClassNotFoundException|invalid dist|RuntimeDistCleaner' "$log"; then
  fail "erro de classe exclusiva do cliente no servidor dedicado"
fi
if grep -E 'Exception (in|while)|Caught exception|Failed to (load|save)' "$log" | grep -i respectcraft; then
  fail "exceção envolvendo o respectcraft"
fi
[ "$FAILED" = 0 ] && echo "Teste de fumaça do servidor dedicado: OK"
exit "$FAILED"
