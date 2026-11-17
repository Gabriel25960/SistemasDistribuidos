#!/usr/bin/env bash
# Demonstracao automatica da Eleicao em Anel: sobe 8 processos independentes e executa
#   1) eleicao inicial  2) encerramento do coordenador  3) nova eleicao
# Uso: ./scripts/run_anel.sh
cd "$(dirname "$0")/.."
[ -d out ] || ./scripts/build.sh
CFG=config/anel.properties
mkdir -p logs
rm -f logs/N*.log

PIDS=()
cleanup() { kill "${PIDS[@]}" 2>/dev/null; }
trap cleanup EXIT

for id in 1 2 3 4 5 6 7 8; do
  java -cp out anel.Node --id "$id" --config "$CFG" > /dev/null 2>&1 < /dev/null &
  PIDS+=($!)
done
sleep 4

status() { for id in "$@"; do java -cp out anel.Control --to "$id" --cmd status --config "$CFG"; done; }

echo "### CENARIO 1 - eleicao inicial (iniciada pelo processo 3)"
java -cp out anel.Control --to 3 --cmd election --config "$CFG"
sleep 6
status 1 2 3 4 5 6 7 8

echo
echo "### CENARIO 2 - encerrando o coordenador (processo 8)"
java -cp out anel.Control --to 8 --cmd quit --config "$CFG"
sleep 14

echo
echo "### CENARIO 3 - estado apos a nova eleicao (processo 8 fora do anel)"
status 1 2 3 4 5 6 7
echo
echo "### Registros de coordenador nos logs (ordem cronologica)"
grep -h "NOVO COORDENADOR" logs/N*.log | sort
echo
echo "Logs completos em ./logs (N1.log ... N8.log)"
