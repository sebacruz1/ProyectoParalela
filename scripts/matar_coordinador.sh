#!/usr/bin/env bash

# Uso: ./scripts/matar_coordinador.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOGS_DIR="$ROOT_DIR/logs"

ULTIMA_LINEA=$(grep -h "SOY COORDINADOR" "$LOGS_DIR"/node*.log 2>/dev/null | sort | tail -1)

if [[ -z "$ULTIMA_LINEA" ]]; then
    echo "No se encontró ningún 'SOY COORDINADOR' en $LOGS_DIR/node*.log. ¿Está el cluster corriendo?" >&2
    exit 1
fi

NODE_ID=$(echo "$ULTIMA_LINEA" | sed -n 's/.*\[node=\([0-9]*\)\].*/\1/p')

if [[ -z "$NODE_ID" ]]; then
    echo "No se pudo extraer el id de nodo de: $ULTIMA_LINEA" >&2
    exit 1
fi

PID_FILE="$LOGS_DIR/node$NODE_ID.pid"
if [[ ! -f "$PID_FILE" ]]; then
    echo "No existe $PID_FILE (¿el nodo $NODE_ID sigue corriendo?)" >&2
    exit 1
fi

PID=$(cat "$PID_FILE")
echo "Coordinador actual: nodo $NODE_ID (PID $PID)"
echo "Última línea vista: $ULTIMA_LINEA"
echo "Matando con kill -9..."
kill -9 "$PID"
echo "Hecho. Mirá logs/node*.log para ver la reelección y la recuperación."
