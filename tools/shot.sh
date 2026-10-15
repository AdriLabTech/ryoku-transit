#!/usr/bin/env bash
# Uso: tools/shot.sh <nombre> -> captura del emulador en $SHOTS (por defecto docs/screenshots)
set -e
OUT="${SHOTS:-$(dirname "$0")/../docs/screenshots}"; mkdir -p "$OUT"
"${ADB:-adb}" exec-out screencap -p > "$OUT/$1.png"
