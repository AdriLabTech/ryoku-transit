# Fuentes de datos

Verificado el **2026-10-15** salvo indicación. «Verificado» = descargado/consultado y su contenido inspeccionado en esta sesión.

## Tabla comparativa

| Fuente | Documentación | Datos verificados | Acceso | Disponibilidad | Frescura | Cobertura | Limitaciones | Términos | Confianza | Uso recomendado |
|---|---|---|---|---|---|---|---|---|---|---|
| **GTFS estático AUCORSA** (publicado por AUCORSA en el NAP; espejo en Mobility Database `mdb-2713`) | https://nap.transportes.gob.es/ (ficha «Autobús urbano de Córdoba») · https://mobilitydatabase.org/feeds/gtfs/mdb-2713 | `agency, calendar, calendar_dates, routes, shapes, stop_times, stops, trips` (sin `feed_info`, `frequencies`, `transfers`, `translations`). 37 rutas, 721 paradas, 8 780 viajes, 202 640 `stop_times`, 75 shapes | Descarga HTTP pública del **espejo** (`files.mobilitydatabase.org/mdb-2713/mdb-2713-202602260025/…zip`, 2,39 MB). El NAP exige **login** para descargar | **Verificada** (espejo). **Caducada**: vigencia 2026-02-25 → **2026-03-31** | Último snapshot del espejo: 2026-02-26. Hoy es 2026-10-15 | Toda la red urbana (la ficha NAP anterior citaba 36 rutas/657 paradas, versión de dic. 2025) | Caducado 6,5 meses; sin tiempo real; existe un feed más reciente en el NAP que no pude descargar sin cuenta | Licencia **no verificada** (NAP y Mobility Database no la mostraron en lo consultado). Uso privado asumido; no se redistribuye (`data/gtfs-raw/` fuera de git) | Alta en estructura, alta en caducidad (`calendar` leído) | Red, horarios programados, geometrías |
| **NAP** — https://nap.transportes.gob.es/ConjuntoDato/List | Mismo sitio | Listado HTML descargado (1,4 MB). Búsqueda externa indica ficha con frecuencia «hasta cada 6 meses», método pull, sin dataset de tiempo real | Registro necesario para descargar | **Condicional** (requiere cuenta) | Ficha vista por búsqueda: actualización 2025-12-03 (no verificado en origen: la ficha devolvió 404 sin sesión) | Nacional | Descarga bloqueada sin credenciales | Pendiente de revisar al registrarse | Media | Fuente oficial para refrescar el GTFS: **acción pendiente de Adrián** (crear cuenta) |
| **Mobility Database** `mdb-2713` | https://mobilitydatabase.org | 5 snapshots (2025-07-23, 2025-12-04, 2026-02-19/20/26); el feed original viene del NAP; `authentication_type` 2 en la fuente | Páginas y zips públicos; la API v1 exige token | **Verificada** | Último snapshot 2026-02-26; no se actualiza desde entonces en lo observado | Igual que arriba | Es un espejo de terceros del NAP | No comprobada | Media-alta | Espejo de descarga del GTFS mientras no haya cuenta NAP |
| **Transitland** — https://www.transit.land/ | https://www.transit.land/documentation | La página «Córdoba» lista 4 operadores incl. «Autobuses de Córdoba - S.A.». **No** obtuve el onestop_id del feed ni su estado; la API REST exige clave (el endpoint probado devolvió 404) | Web pública; API con clave | **Desconocida** para este feed | — | — | Sin verificación del feed | — | Baja | Solo descubrimiento; no es dependencia |
| **RSS de aucorsa.es** — https://aucorsa.es/feed/ | WordPress estándar | `<item>` con `title, link, pubDate, description` (HTTP 200, `application/rss+xml`, `lastBuildDate` 2026-10-14). Integrado y probado en emulador | Público, sin credenciales | **Verificada** | Actualización horaria declarada (`sy:updatePeriod hourly`) | Noticias y avisos de la web | **No** son alertas estructuradas (sin línea/parada afectada, sin rango temporal); mezcla noticias y avisos | `robots.txt` permite `/`; sin términos específicos hallados | Alta | Pestaña Avisos, con fuente y fecha de publicación |
| **Servicio web autenticado de AUCORSA** (declarado) | https://aucorsa.es/movilidad-accesible/ (sección «Servicios web», leída 2026-10-15) | Declaración oficial: «Datos estáticos» en GTFS y «Datos dinámicos» vía servicio web autenticado: estructura de líneas, coordenadas de recorridos y **estimaciones de llegada a cualquier parada**. No menciona posiciones de vehículos ni GTFS-RT. Procedimiento: «contactar con nosotros» | Autenticado; alta por contacto con AUCORSA | **Existente según AUCORSA, sin acceso**: no verificado operativo ni probado | — | Toda la red (según la declaración) | Sin documentación técnica pública; condiciones desconocidas | Pendientes de AUCORSA | Alta sobre la *declaración*, ninguna sobre el funcionamiento | **Fuente objetivo** para llegadas en tiempo real: adaptador futuro tras la autorización |
| **Web/PWA y `lightapi.aucorsa.es`** (estimaciones por parada) | Sin documentación pública | Existe «¿Cuándo llega mi bus?» en la web. Un índice WordPress REST público (`/wp-json/aucorsa/v1`) enumera rutas `estimations/stop`, `map/nodes`, `map/nearbusstops`… (solo se leyó el índice; **no** se llamó a ningún endpoint de datos). La web carga Cloudflare Turnstile | Interno de la web | **No autorizado**: sin documentación ni permiso; protegido por anti-bot | — | — | Usarlo sería depender de un endpoint interno no documentado y evadir un control anti-bot | Sin permiso | Alta (sobre su existencia) | **No usar.** Es el motivo de la solicitud a AUCORSA |
| **Google Maps (tiempo real de AUCORSA)** | Prensa: COPE 2026-04-27 | Anuncio de posiciones y llegadas en Google Maps; sección de incidencias «prevista» | Solo consumo dentro de Google Maps | **No hay API cruda** documentada | Continua (según la noticia) | Red | No se obtiene como datos; scraping prohibido | Términos de Google | Media (solo prensa) | Referencia manual para comparar a ojo; nunca fuente |
| **Ocupación** | https://aucorsa.es/aucorsa-incorpora-informacion-sobre-la-ocupacion-de-sus-autobuses/ (publicada «3 de agosto», 2026) | 4 niveles (bajo/medio/alto/muy alto) en paneles, web y app, junto a la llegada estimada | Solo UI propia | **Sin feed público** (la nota no menciona API/GTFS-RT) | Se actualiza en servicio | Flota con sensores | Sin acceso programático | — | Alta (nota oficial) | Ninguno por ahora |
| **GTFS-Realtime** (`VehiclePosition`, `TripUpdate`, `Alert`) | https://gtfs.org/documentation/realtime/reference/ | **Ningún feed AUCORSA encontrado** en NAP, Mobility Database ni la prensa | — | **Inaccesible/inexistente públicamente** | — | — | — | — | Media (ausencia de evidencia, no evidencia de ausencia) | Interfaz prevista para un futuro adaptador autorizado |
| Tráfico/cortes municipales, otras APIs regionales (CTAN) | — | **No investigadas a fondo**; ninguna aporta posición de bus. No se integra nada | — | Desconocida | — | — | — | — | — | Descartadas por ahora (ver `RESEARCH.md`) |

## Decisiones por caso de uso

| Caso | Fuente elegida | Estado |
|---|---|---|
| 1. Red estática (rutas/paradas) | GTFS del NAP vía espejo | Implementado |
| 2. Llegadas programadas | Mismo GTFS | Implementado; **hoy solo como referencia** por caducidad |
| 3. Llegadas en tiempo real | Servicio web autenticado de AUCORSA (existe según su web; sin acceso aún) | **Bloqueado** (esperando AUCORSA) |
| 4. Posición real de vehículos | Ninguna accesible; el servicio declarado solo cita estimaciones de llegada | **Bloqueado**, posiblemente inexistente |
| 5. Alertas de servicio | RSS aucorsa.es (parcial, sin asociación a líneas) | Implementado con límites declarados |
| 6. Observaciones históricas | Ninguna: requeriría tiempo real | **Bloqueado** |
| 7. Validación externa | Google Maps, solo comparación manual | No automatizable |

## Preguntas abiertas para AUCORSA (solicitud enviada, **sin respuesta**)
1. ¿Existe un servicio web/API documentado para terceros? ¿Autenticación, cuotas y condiciones de uso personal?
1b. Su web describe un «servicio web autenticado» con estimaciones de llegada: ¿cuál es el procedimiento de alta, el formato y el endpoint documentado?
2. ¿Publican GTFS-Realtime (`VehiclePosition`, `TripUpdate`, `Alert`)? ¿Con qué frecuencia y con qué identificadores de viaje?
3. ¿Cómo se casan los `trip_id` en tiempo real con el GTFS estático vigente?
4. ¿Cuál es la URL y la cadencia del GTFS vigente (el del NAP caducó el 2026-03-31) y su licencia?
5. ¿La ocupación se expone programáticamente? ¿Se puede conservar histórico para evaluación?

## Acciones pendientes del usuario
- Crear cuenta en el NAP y descargar el GTFS vigente de «Autobús urbano de Córdoba» (`tools/build_db.py` lo importa tal cual).
- Esperar/gestionar la respuesta de AUCORSA.

## Cómo reimportar
```bash
python3 tools/build_db.py <gtfs.zip> app/src/main/assets/transit.db --report data/import-report.json
```
El script aborta si falta un fichero obligatorio, cuenta defectos (coordenadas, duplicados, referencias rotas, tiempos no monótonos, rangos invertidos) y escribe `feed_start/feed_end` en la tabla `meta`. Resultado actual: **0 defectos** sobre el feed del 2026-02-26.
