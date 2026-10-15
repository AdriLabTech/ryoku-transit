#!/usr/bin/env bash
# Importa un GTFS descargado a mano (p. ej. del NAP) y reconstruye la base de la app.
# Uso: tools/import_gtfs.sh ~/Descargas/gtfs.zip
set -euo pipefail
cd "$(dirname "$0")/.."
ZIP="${1:?uso: tools/import_gtfs.sh <gtfs.zip>}"
[ -f "$ZIP" ] || { echo "No existe $ZIP" >&2; exit 1; }

NEW_SHA=$(sha256sum "$ZIP" | cut -d' ' -f1)
OLD_SHA=$(python3 -I -c "import json;print(json.load(open('data/import-report.json'))['meta']['sha256'])" 2>/dev/null || echo none)
TMPDB=$(mktemp -d)/transit.db
python3 -I tools/build_db.py "$ZIP" "$TMPDB" --report "$TMPDB.json" >/dev/null
python3 -I - "$TMPDB.json" "$NEW_SHA" "$OLD_SHA" <<'PY'
import json, sys, datetime
r = json.load(open(sys.argv[1])); m = r["meta"]
end = datetime.datetime.strptime(m["feed_end"], "%Y%m%d").date()
print(f"Vigencia del feed: {m['feed_start']} -> {m['feed_end']}   (hoy: {datetime.date.today()})")
print("Recuentos:", r["counts"]); print("Defectos:", r["defects"] or "ninguno")
if sys.argv[2] == sys.argv[3]: print("AVISO: es EXACTAMENTE el mismo fichero que ya está importado."); 
if end < datetime.date.today(): print("AVISO: el feed sigue CADUCADO; la app seguirá en modo horario de referencia.")
sys.exit(2 if r["defects"] else 0)
PY
cp "$ZIP" "data/gtfs-raw/$(date +%Y%m%d)-$(basename "$ZIP")"
cp "$TMPDB" app/src/main/assets/transit.db
cp "$TMPDB.json" data/import-report.json
echo "Base actualizada. Ahora: ./gradlew :app:testDebugUnitTest && tools/run-emulator.sh"
