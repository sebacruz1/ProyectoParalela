#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

PEERS_CONF="$ROOT_DIR/config/peers.conf"

echo "Compilando proyecto..."
javac -d out $(find src -name "*.java")

echo "Levantando cluster EpicGames (nodos definidos en $PEERS_CONF)..."

PIDS=()
IDS=()
while IFS=',' read -r id host puertoCliente puertoPeer; do
  id="$(echo "${id:-}" | xargs)"
  [[ -z "$id" || "$id" == \#* ]] && continue
  java -cp out cluster.Nodo "$id" "$PEERS_CONF" &
  PIDS+=("$!")
  IDS+=("$id")
done < "$PEERS_CONF"

cleanup() {
  echo
  echo "Deteniendo nodos..."
  kill "${PIDS[@]}" 2>/dev/null || true
}

trap cleanup INT TERM EXIT

echo "Nodos arriba (logs en logs/node<id>.log):"
for i in "${!IDS[@]}"; do
  echo "  Nodo ${IDS[$i]}  PID=${PIDS[$i]}"
done
echo
echo "Presiona Ctrl+C para detener todo."

wait "${PIDS[@]}"
