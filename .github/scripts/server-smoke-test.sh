#!/usr/bin/env bash
# Teste de fumaça do servidor dedicado:
#  1. sobe o servidor de desenvolvimento (Dist.DEDICATED_SERVER) e confirma que carrega sem
#     erros de classes exclusivas do cliente;
#  2. pelo console, inicia uma eleição e para o servidor durante a INSCRIÇÃO;
#  3. sobe o servidor de novo e confirma que a eleição continua na mesma fase;
#  4. avança as fases (sem candidatos → encerra sem vencedor) e para o servidor.
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

# run_server <nome> <comandos...>: envia cada comando após o servidor ficar pronto e termina com "stop".
run_server() {
  local name="$1"; shift
  local log="smoke/${name}.log"
  local fifo="smoke/${name}.fifo"
  rm -f "$fifo"; mkfifo "$fifo"
  ./gradlew runServer --no-daemon --no-configuration-cache --console=plain < "$fifo" > "$log" 2>&1 &
  local pid=$!
  exec 3>"$fifo"

  local ready=0
  for _ in $(seq 1 900); do
    if grep -q 'Done (' "$log"; then ready=1; break; fi
    if ! kill -0 "$pid" 2>/dev/null; then break; fi
    sleep 1
  done
  if [ "$ready" != 1 ]; then
    fail "[$name] servidor não ficou pronto"
    exec 3>&-; kill "$pid" 2>/dev/null; wait "$pid"; return
  fi

  for cmd in "$@"; do
    echo "$cmd" >&3
    sleep 2
  done
  echo "stop" >&3
  exec 3>&-

  for _ in $(seq 1 180); do
    if ! kill -0 "$pid" 2>/dev/null; then break; fi
    sleep 1
  done
  kill "$pid" 2>/dev/null
  wait "$pid"
  echo "----- [$name] trecho relevante do log -----"
  grep -E 'ELEI|tupinicraft|TupiniCraft|Exception|Error' "$log" | head -80
}

run_server first \
  "brasil eleicao status" \
  "brasil admin eleicao iniciar prefeito senador" \
  "brasil eleicao status"

run_server second \
  "brasil eleicao status" \
  "brasil admin eleicao avancar" \
  "brasil eleicao status"

grep -q 'Done (' smoke/first.log || fail "primeira inicialização não concluiu"
grep -q 'Done (' smoke/second.log || fail "segunda inicialização não concluiu"

if grep -E 'NoClassDefFoundError|ClassNotFoundException|invalid dist|RuntimeDistCleaner' smoke/*.log; then
  fail "erro de classe exclusiva do cliente no servidor dedicado"
fi
if grep -E 'Exception (in|while)|Caught exception|Failed to (load|save)' smoke/*.log | grep -i tupinicraft; then
  fail "exceção envolvendo o tupinicraft"
fi
grep -qE 'Come.{1,3}aram as inscri.{1,4}es para Prefeito e Senador' smoke/first.log \
  || fail "eleição não foi iniciada pelo comando admin"
grep -qE 'Fase: Inscri' smoke/second.log \
  || fail "eleição não sobreviveu ao reinício do servidor (esperado: Fase: Inscrição)"
grep -qE 'Ningu.{1,3}m se inscreveu para Prefeito' smoke/second.log \
  || fail "avanço de fase sem candidatos não encerrou a disputa"
grep -qE 'Nenhuma elei.{1,4}o ativa' smoke/second.log \
  || fail "eleição sem candidatos não voltou para INATIVA"

if [ "$FAILED" = 0 ]; then
  echo "Teste de fumaça do servidor dedicado: OK"
fi
exit "$FAILED"
