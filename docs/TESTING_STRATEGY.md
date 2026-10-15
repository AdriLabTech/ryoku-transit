# Estrategia de pruebas

| Nivel | Qué | Dónde | Estado |
|---|---|---|---|
| Unitarias (JVM) | Calendario de servicio (rango, día, excepciones), vigencia del feed, llegadas (horario, referencia, tras medianoche, cambio horario), RSS (parseo y rechazo de DOCTYPE) | `app/src/test` | **12 tests** |
| Calidad de datos | Coordenadas, paradas duplicadas, fuera de Córdoba, referencias rotas, calendarios invertidos, tiempos no monótonos | `tools/build_db.py` + informe | 0 defectos en el feed actual |
| UI (instrumentada) | Búsqueda → parada → favorita → pestaña Favoritas; pantalla Estado declara límites; mapa con alternativa en lista | `app/src/androidTest` | **3 tests** en emulador Pixel API 36 |
| Visuales | Capturas con `tools/shot.sh` | `docs/screenshots` | Manual |

## Ejecutar
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # con emulador
```

## Huecos declarados
- Sin tests de integración del adaptador SQLite con una BD mínima en JVM (se cubren indirectamente en los tests UI con la BD real).
- Sin tests de fallo de red del RSS (se probó a mano que carga; el camino de caché/fallo no tiene prueba automatizada).
- Sin pruebas de rendimiento ni de batería medidas.
- Los tests de UI dependen de que el feed siga caducado o vigente de forma consistente: comprueban «Horario» (ambos casos).
