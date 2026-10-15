# RYOKU TRANSIT

App Android personal (Kotlin + Jetpack Compose) para los autobuses urbanos de AUCORSA (Córdoba). Proyecto privado y no comercial: uso personal, aprendizaje y demostración técnica. No es la app oficial.

**Estado honesto (2026-10-15):** funciona con **horarios programados** del GTFS estático y avisos de la web de AUCORSA. **No hay posición de autobuses ni predicción en vivo**: no existe una fuente de tiempo real accesible y autorizada (ver [docs/DATA_SOURCES.md](docs/DATA_SOURCES.md)). Además, el último GTFS descargable **caducó el 2026-03-31**, así que hoy la app muestra un *horario de referencia* claramente etiquetado, nunca como información de hoy.

## Qué hace
- Busca paradas (nombre/código/cercanas con permiso de ubicación bajo demanda), lista líneas y sentidos.
- Próximas salidas por parada, con la base de la hora (`HORARIO` vigente o `REF` de referencia).
- Mapa esquemático propio (geometrías y paradas del GTFS, sin teselas de terceros) y alternativa en lista.
- Favoritas, avisos (RSS oficial de aucorsa.es) y pantalla **Estado** con diagnóstico de datos y capacidades.
- Estilo visual **Ryoku Kami** (Grafito por defecto, Washi opcional), Komiki Mono.

## Arranque
```bash
# requisitos: JDK 21, Android SDK (compileSdk 37), emulador o dispositivo
python3 tools/build_db.py data/gtfs-raw/<feed>.zip app/src/main/assets/transit.db --report data/import-report.json
./gradlew :app:testDebugUnitTest          # tests JVM
./gradlew :app:connectedDebugAndroidTest  # tests de UI (necesita emulador)
tools/run-emulator.sh                     # instala y abre la app
```
El feed fuente se descarga de Mobility Database (espejo del NAP); ver `docs/DATA_SOURCES.md`. `data/gtfs-raw/` no se versiona.

## Documentación
[Visión](docs/PROJECT_VISION.md) · [Investigación](docs/RESEARCH.md) · [Arquitectura](docs/ARCHITECTURE.md) · [Fuentes de datos](docs/DATA_SOURCES.md) · [Diseño](docs/DESIGN_SYSTEM.md) · [Accesibilidad](docs/ACCESSIBILITY.md) · [Predicción](docs/PREDICTION_STRATEGY.md) · [Pruebas](docs/TESTING_STRATEGY.md) · [Hoja de ruta](docs/ROADMAP.md) · [Decisiones](docs/DECISIONS.md)

Capturas de referencia en `docs/screenshots/` (emulador Pixel API 36).
