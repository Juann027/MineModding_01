#!/usr/bin/env bash
# Teste de fumaça do servidor dedicado: sobe o servidor, confirma carregamento sem classes de cliente,
# invoca o Leão do Fim e consulta os catálogos e o autoteste das regras.
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
  for cmd in "summon tupinicraft:leao_do_fim 0 -60 0" "tupinianim list" "tupini selftest" "tupini skills list" "tupini finishers"; do
    echo "$cmd" >&3; sleep 5
  done
  echo "stop" >&3
  exec 3>&-
  for _ in $(seq 1 180); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
  kill "$pid" 2>/dev/null; wait "$pid"
fi

grep -v DEBUG "$log" | grep -E 'tupinicraft|TupiniCraft|RespectCraft|Exception|Error|Summon|summon|Unknown|entity' | head -60
[ "$(grep -c 'Summoned new' "$log")" -ge 1 ] || fail "Leão do Fim não foi invocado no servidor dedicado"
# Animation Manager carregado no servidor dedicado (catalogo e comando), sem classes de cliente.
grep -q 'Player animations: tupinicraft:idle, tupinicraft:wave' "$log" || fail "catálogo de animações de jogador não respondeu no servidor"
# Etapa 3: autoteste das regras (roleta, necessidades, habilidades, camera, Codecs) e catalogos.
grep -q 'RespectCraft self-test: OK' "$log" || { grep 'Self-test failed' "$log"; fail "autoteste das regras falhou"; }
grep -q 'Skills (18)' "$log" || fail "catálogo de habilidades não respondeu"
grep -q 'Finishers: tupinicraft:takedown, tupinicraft:rasteira' "$log" || fail "catálogo de finalizações não respondeu"
if grep -E 'NoClassDefFoundError|ClassNotFoundException|invalid dist|RuntimeDistCleaner' "$log"; then
  fail "erro de classe exclusiva do cliente no servidor dedicado"
fi
if grep -E 'Exception (in|while)|Caught exception|Failed to (load|save)' "$log" | grep -i tupinicraft; then
  fail "exceção envolvendo o tupinicraft"
fi
[ "$FAILED" = 0 ] && echo "Teste de fumaça do servidor dedicado: OK"
exit "$FAILED"
