#!/usr/bin/env bash
# Teste de fumaça do servidor dedicado:
#  1. sobe o servidor de desenvolvimento (Dist.DEDICATED_SERVER) e confirma que carrega sem
#     erros de classes exclusivas do cliente;
#  2. pelo console, valida/aplica overrides de configuração, inicia uma eleição e para o servidor
#     durante a INSCRIÇÃO, depois de mudar a duração da votação;
#  3. sobe o servidor de novo e confirma: eleição na mesma fase, override persistido, reset para o TOML,
#     aldeões completando a disputa, prazo da votação congelado e consulta de mandatos;
#  Em todas as inicializações estão presentes as bibliotecas obrigatórias (GeckoLib, SmartBrainLib, SkillLib,
#  KubeJS, Rhino); a primeira confirma o carregamento das skills e o mob de teste.
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
  "summon tupinicraft:cabo_eleitoral 0 -60 0" \
  "brasil admin config get economia.saldo_inicial" \
  "brasil admin config set mandato.senador 60" \
  "brasil admin config set mandato.senador 0" \
  "brasil admin config set eleicao.automaticas talvez" \
  "brasil admin eleicao iniciar presidente" \
  "brasil admin config set eleicao.votacao_segundos 90" \
  "brasil eleicao status"

run_server second \
  "brasil eleicao status" \
  "brasil admin config get mandato.senador" \
  "brasil admin config reset mandato.senador" \
  "brasil admin config get mandato.senador" \
  "brasil admin eleicao avancar" \
  "brasil eleicao status" \
  "brasil admin eleicao avancar" \
  "brasil admin eleicao avancar" \
  "brasil mandatos brasil" \
  "brasil admin config reset eleicao.votacao_segundos"

grep -q 'Done (' smoke/first.log || fail "primeira inicialização não concluiu"
grep -qE 'Summoned new' smoke/first.log || fail "mob GeckoLib/SmartBrainLib não foi invocado no servidor dedicado"
grep -qE 'SkillLib detectado' smoke/first.log || fail "SkillLib não foi detectado"
grep -qE 'SkillLib: Loaded 4 skills' smoke/first.log || fail "SkillLib não carregou as 4 skills de teste"
grep -qE '4 regras de skill carregadas' smoke/first.log || fail "regras do TupiniCraft não carregaram"
if grep -E 'Failed to load skill' smoke/*.log; then fail "SkillLib rejeitou alguma skill"; fi
grep -qE 'economia.saldo_inicial' smoke/first.log || fail "chave economia.saldo_inicial não respondeu"
grep -q 'Done (' smoke/second.log || fail "segunda inicialização não concluiu"

if grep -E 'NoClassDefFoundError|ClassNotFoundException|invalid dist|RuntimeDistCleaner' smoke/*.log; then
  fail "erro de classe exclusiva do cliente no servidor dedicado"
fi
if grep -E 'Exception (in|while)|Caught exception|Failed to (load|save)' smoke/*.log | grep -i tupinicraft; then
  fail "exceção envolvendo o tupinicraft"
fi
grep -qE 'mandato.senador: 45 . 60 \(override' smoke/first.log \
  || fail "config set não aplicou o override"
grep -qE 'Valor inv.{1,3}lido para mandato.senador' smoke/first.log \
  || fail "valor fora dos limites não foi rejeitado"
grep -qE 'Valor inv.{1,3}lido para eleicao.automaticas' smoke/first.log \
  || fail "booleano inválido não foi rejeitado"
grep -qE 'Come.{1,3}aram as inscri.{1,4}es para Presidente da Rep' smoke/first.log \
  || fail "eleição não foi iniciada pelo comando admin"
grep -qiE 'fase: Inscri' smoke/second.log \
  || fail "eleição não sobreviveu ao reinício do servidor (esperado: Fase: Inscrição)"
grep -qE 'mandato.senador = 60 \[override' smoke/second.log \
  || fail "override de configuração não persistiu após reiniciar"
grep -qE 'mandato.senador voltou ao valor do TOML: 45' smoke/second.log \
  || fail "reset não restaurou o valor do TOML"
grep -qE 'Alde.{1,6}es entram na disputa para Presidente' smoke/second.log \
  || fail "candidatos aldeões não completaram a disputa"
grep -qE 'Vota.{1,4}o .{1,6} tempo restante: 0:5' smoke/second.log \
  || fail "mudar eleicao.votacao_segundos alterou a eleição em andamento (esperado: prazo congelado de 60 s)"
grep -qE 'venceu a elei.{1,4}o para Presidente|Ningu.{1,3}m venceu para Presidente|Empate t.{1,3}cnico para Presidente' smoke/second.log \
  || fail "apuração não terminou"
grep -qE '\[MANDATOS\] Brasil' smoke/second.log \
  || fail "consulta de mandatos não respondeu"

if [ "$FAILED" = 0 ]; then
  echo "Teste de fumaça do servidor dedicado: OK"
fi
exit "$FAILED"
