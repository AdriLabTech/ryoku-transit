# Arquitectura

Android nativo, Kotlin 2.4, Jetpack Compose (sin Material 3: componentes propios), AGP 9.4.1, Gradle 9.8.1, `minSdk 26`, `compileSdk 37`. Un módulo `:app`, capas por paquete.

```
tools/build_db.py ──► app/src/main/assets/transit.db (SQLite, 11 MB)
                              │ (copia a filesDir una vez por versionCode)
 data/   TransitDb · SqliteSchedule (ScheduleSource) · NoticesSource (RSS) · Favorites
 domain/ Model · ServiceCalendar · ArrivalCalculator · FeedInfo   ← Kotlin puro, tests JVM
 ui/     Theme (tokens) · Components · NetworkMap · Screens · App (navegación)
 AppViewModel: estado, reloj de 15 s, preferencias
```

## Límites
- `domain` no importa Android. `ArrivalCalculator` depende de `ScheduleSource` (interfaz) y de un `ServiceCalendar`; los tests usan un fake.
- La UI solo ve `Arrival`, `Stop`, `Route`, `FeedInfo`: ningún detalle del proveedor.
- Zona horaria: `Europe/Madrid` para mostrar; los tiempos GTFS se tratan como segundos desde «mediodía − 12 h» del día de servicio (robusto ante el cambio horario), con soporte de horas >24:00 (consulta del día anterior desplazado 86 400 s).

## Punto de extensión para el AUCORSA autorizado
Hoy la única capacidad de datos es `Capability.{STATIC_NETWORK, SCHEDULED_ARRIVALS, SERVICE_NOTICES}`. Al obtener acceso, se añadirá una fuente de llegadas que devuelva `Arrival` con una nueva `Basis.LIVE` y marca temporal de la fuente y de ingestión, y la UI ya distingue la base de cada hora. **No** se ha creado esa interfaz vacía: se dibujará con el contrato real (YAGNI y «sin capacidades ficticias»).

## Persistencia y red
- Red estática: SQLite de solo lectura generada por script (no Room: sin necesidad de ORM ni KSP).
- Preferencias y favoritas: `SharedPreferences` (identificadores solamente).
- Red: solo el RSS (`HttpURLConnection`, timeouts 6/8 s, un reintento, caché en disco, marca «copia guardada» si falla). Sin polling en segundo plano. Ubicación solo al pulsar «cerca de mí» (permiso aproximado, último fix conocido).
- Sin credenciales en cliente ni en git.

## Mapa
Mapa esquemático propio en `Canvas`: geometrías (`shapes`) y paradas del GTFS en proyección equirrectangular, con gestos de arrastre/pellizco/toque. Sin teselas de terceros (sin claves, cuotas ni términos de OSM/Google), coherente con el estilo plano a tinta. Límite: no hay callejero de fondo.
