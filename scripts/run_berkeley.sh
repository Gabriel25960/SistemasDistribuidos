#!/usr/bin/env bash
# Sobe 4 participantes (processos independentes) e depois o coordenador.
# Uso: ./scripts/run_berkeley.sh
cd "$(dirname "$0")/.."
[ -d out ] || ./scripts/build.sh
CFG=config/berkeley.properties
mkdir -p logs

PIDS=()
cleanup() { kill "${PIDS[@]}" 2>/dev/null; }
trap cleanup EXIT

# id  deslocamento_inicial_em_ms (cada participante comeca com um relogio diferente)
while read -r id off; do
  java -cp out berkeley.Participant --id "$id" --offset "$off" --config "$CFG" > /dev/null 2>&1 &
  PIDS+=($!)
done <<'LISTA'
1 30000
2 -45000
3 12000
4 -8000
LISTA

sleep 2
java -cp out berkeley.Coordinator --offset 5000 --config "$CFG"
echo
echo "Logs individuais em ./logs (coordenador.log, P1.log ... P4.log)"
