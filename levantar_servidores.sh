#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

LOG_DIR="$ROOT_DIR/out/logs"
mkdir -p "$LOG_DIR"

echo "Compilando proyecto..."
javac -d out src/modelos/*.java src/store/*.java src/handlers/*.java src/servidor/*.java src/cliente/*.java

echo "Levantando servidores..."
java -cp out servidor.ServidorPrincipal > "$LOG_DIR/principal.log" 2>&1 &
PID_PRINCIPAL=$!
java -cp out servidor.ServidorTienda > "$LOG_DIR/tienda.log" 2>&1 &
PID_TIENDA=$!
java -cp out servidor.ServidorMatchmaking > "$LOG_DIR/matchmaking.log" 2>&1 &
PID_MATCHMAKING=$!

cleanup() {
  echo
  echo "Deteniendo servidores..."
  kill "$PID_PRINCIPAL" "$PID_TIENDA" "$PID_MATCHMAKING" 2>/dev/null || true
}

trap cleanup INT TERM EXIT

echo "Servidores arriba:"
echo "  Principal   PID=$PID_PRINCIPAL (log: $LOG_DIR/principal.log)"
echo "  Tienda      PID=$PID_TIENDA (log: $LOG_DIR/tienda.log)"
echo "  Matchmaking PID=$PID_MATCHMAKING (log: $LOG_DIR/matchmaking.log)"
echo
echo "Presiona Ctrl+C para detener todo."

wait "$PID_PRINCIPAL" "$PID_TIENDA" "$PID_MATCHMAKING"
