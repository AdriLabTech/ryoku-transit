#!/usr/bin/env bash
# Instala la app debug en el emulador/dispositivo conectado y la abre.
set -e
cd "$(dirname "$0")/.."
./gradlew --console=plain :app:assembleDebug
"${ADB:-adb}" install -r app/build/outputs/apk/debug/app-debug.apk
"${ADB:-adb}" shell am start -n com.ryoku.transit/.MainActivity
