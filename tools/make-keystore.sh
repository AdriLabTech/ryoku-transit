#!/usr/bin/env bash
# Crea UNA VEZ la clave con la que se firman todas las versiones de la app.
# Si se pierde, los compañeros tendrán que desinstalar para poder actualizar: haz copia de seguridad del directorio.
set -euo pipefail
cd "$(dirname "$0")/.."

DIR="${KEYSTORE_DIR:-$HOME/.config/ryoku-transit}"
KS="$DIR/release.jks"
[ -e "$KS" ] && { echo "Ya existe $KS: no se sobrescribe." >&2; exit 1; }
[ -e keystore.properties ] && { echo "Ya existe keystore.properties: no se sobrescribe." >&2; exit 1; }

mkdir -p "$DIR"; chmod 700 "$DIR"
PASS="$(head -c 24 /dev/urandom | base64 | tr -d '/+=\n' | head -c 24)"
keytool -genkeypair -v -keystore "$KS" -alias ryoku-transit -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=Ryoku Transit, O=AdriLabTech, C=ES" >/dev/null
chmod 600 "$KS"

umask 077
cat > keystore.properties <<PROPS
storeFile=$KS
storePassword=$PASS
keyAlias=ryoku-transit
keyPassword=$PASS
PROPS

echo "Clave creada: $KS"
echo "Contraseñas en: $PWD/keystore.properties (ignorado por git)"
echo "HAZ COPIA de $DIR y de keystore.properties en un sitio privado (gestor de contraseñas / USB). Nunca a GitHub."
