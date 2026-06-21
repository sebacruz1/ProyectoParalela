#!/usr/bin/env bash
# Fusiona logs/node*.log de los 3 (o N) nodos en una sola línea de tiempo,
# ordenada por marca de Lamport en vez de por reloj de pared de cada JVM
# (que no son comparables entre procesos). Sirve como evidencia del orden
# causal para el informe (punto 3.4 del enunciado).
#
# Uso: ./scripts/merge_logs.sh [archivo_salida]
#   por defecto escribe en logs/merged.log

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOGS_DIR="$ROOT_DIR/logs"
SALIDA="${1:-$LOGS_DIR/merged.log}"

if ! ls "$LOGS_DIR"/node*.log >/dev/null 2>&1; then
    echo "No se encontraron logs en $LOGS_DIR (esperaba logs/node<id>.log)" >&2
    exit 1
fi

awk -F'[][]' '
    {
        lamport = ""
        for (i = 1; i <= NF; i++) {
            if ($i ~ /^lamport=/) {
                split($i, partes, "=")
                lamport = partes[2]
                break
            }
        }
        if (lamport == "") {
            printf "9999999999\t%s\n", $0
        } else {
            printf "%010d\t%s\n", lamport, $0
        }
    }
' "$LOGS_DIR"/node*.log | sort -n -s -k1,1 | cut -f2- > "$SALIDA"

echo "Log fusionado y ordenado por marca Lamport: $SALIDA"
echo "Líneas: $(wc -l < "$SALIDA")"
