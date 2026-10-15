#!/usr/bin/env bash
# Uso:
#   tools/release.sh            compila y verifica dist/ryoku-transit-X.Y.Z.apk
#   tools/release.sh publish    además crea la Release vX.Y.Z en GitHub con ese APK (requiere gh autenticado)
# La versión sale de `appVersion` en app/build.gradle.kts (única fuente).
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f keystore.properties ] || { echo "Falta keystore.properties: ejecuta tools/make-keystore.sh primero." >&2; exit 1; }
VERSION="$(sed -n 's/^val appVersion = "\(.*\)"/\1/p' app/build.gradle.kts)"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "appVersion no válida: '$VERSION'" >&2; exit 1; }
TAG="v$VERSION"

echo "▶ Tests"
./gradlew :app:testDebugUnitTest -q
echo "▶ Compilando release $VERSION"
./gradlew :app:assembleRelease -q

SRC="app/build/outputs/apk/release/app-release.apk"
[ -f "$SRC" ] || { echo "No se generó $SRC (¿sin firmar?)" >&2; exit 1; }
SDK="$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)"; SDK="${SDK:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
[ -x "$APKSIGNER" ] || { echo "apksigner no encontrado en $SDK/build-tools" >&2; exit 1; }
"$APKSIGNER" verify --min-sdk-version 26 "$SRC"

mkdir -p dist
APK="dist/ryoku-transit-$VERSION.apk"
cp "$SRC" "$APK"
echo "✔ $APK  ($(du -h "$APK" | cut -f1))"
echo "  SHA-256: $(sha256sum "$APK" | cut -d' ' -f1)"
echo "  Firma:   $("$APKSIGNER" verify --print-certs "$APK" | sed -n 's/.*SHA-256 digest: //p' | head -1)"

[ "${1:-}" = "publish" ] || { echo "Para publicarla: tools/release.sh publish"; exit 0; }

git diff --quiet && git diff --cached --quiet || { echo "Hay cambios sin commitear: commitea antes de publicar." >&2; exit 1; }
git fetch -q origin
[ "$(git rev-parse HEAD)" = "$(git rev-parse @{u})" ] || { echo "HEAD no está sincronizado con origin: haz push antes." >&2; exit 1; }
gh release view "$TAG" >/dev/null 2>&1 && { echo "La release $TAG ya existe: sube appVersion." >&2; exit 1; }
gh release create "$TAG" "$APK" --target "$(git rev-parse HEAD)" --title "Ryoku Transit $VERSION" --generate-notes
echo "✔ Publicada $TAG. Los móviles con la app la detectarán en su próxima comprobación."
