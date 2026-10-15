# RYOKU TRANSIT — instrucciones para agentes

- App Android nativa (Kotlin, Compose, sin Material 3), un módulo `:app`. Capas por paquete: `domain` (puro, testeable en JVM), `data` (SQLite, RSS, favoritas), `ui` (tema Ryoku Kami, componentes, pantallas).
- **Regla de oro:** nunca mostrar como en vivo lo que es horario. `Basis` es `SCHEDULED` o `REFERENCE`; no añadir `LIVE` hasta que exista una fuente verificada. Nada de datos inventados.
- Los datos salen de `tools/build_db.py` → `app/src/main/assets/transit.db` (11 MB). Cambiar el esquema exige tocar `SqliteSchedule` y el script a la vez.
- Estilo: `docs/DESIGN_SYSTEM.md`. Tokens solo vía `Ryoku.c` / `Ryoku.t`; sin colores sueltos ni sombras; texto de estado siempre con icono + palabra.
- Verificación: `./gradlew :app:testDebugUnitTest` y, con emulador, `:app:connectedDebugAndroidTest`; después mirar capturas (`tools/shot.sh`).
- Sin secretos en el repo. No usar endpoints internos de aucorsa.es (`lightapi`) ni raspar Google Maps: ver `docs/DATA_SOURCES.md`.
- Commits en español, estilo `docs:`/`feat:`/`fix:`. Sin `Co-Authored-By`.
