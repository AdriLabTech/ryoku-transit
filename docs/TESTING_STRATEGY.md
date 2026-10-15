# Estrategia de pruebas

| Nivel | Qué | Dónde | Estado |
|---|---|---|---|
| Unitarias (JVM) | Calendario de servicio (rango, día, excepciones), vigencia del feed, llegadas (horario, referencia, tras medianoche, cambio horario), RSS (parseo y rechazo de DOCTYPE) | `app/src/test` | **29 tests** (incluye modelo de retraso, evaluación walk-forward y detector de subida) |
| Calidad de datos | Coordenadas, paradas duplicadas, fuera de Córdoba, referencias rotas, calendarios invertidos, tiempos no monótonos | `tools/build_db.py` + informe | 0 defectos en el feed actual |
| UI (instrumentada) | Búsqueda → parada → favorita → pestaña Favoritas; pantalla Estado declara límites; mapa con alternativa en lista | `app/src/androidTest` | **6 tests** en emulador Pixel API 36 (incluye anotación manual y ajuste tras ≥20 observaciones sintéticas) |
| Visuales | Capturas con `tools/shot.sh` | `docs/screenshots` | Manual |

## Ejecutar
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # con emulador
```

Pruebas manuales en emulador: servicio de espera con GPS simulado (`adb emu geo fix`, 30 s parado + ~10 m/s) → notificación → tarjeta de confirmación → observación guardada.

## Huecos declarados
- La detección automática no se ha probado con un autobús real; el emulador simula posiciones.
- Sin tests de integración del adaptador SQLite con una BD mínima en JVM (se cubren indirectamente en los tests UI con la BD real).
- Sin tests de fallo de red del RSS (se probó a mano que carga; el camino de caché/fallo no tiene prueba automatizada).
- Sin pruebas de rendimiento ni de batería medidas.
- Los tests de UI dependen de que el feed siga caducado o vigente de forma consistente: comprueban «Horario» (ambos casos).
