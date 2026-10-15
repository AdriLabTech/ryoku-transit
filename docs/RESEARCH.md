# Investigación (2026-10-15)

Registro de lo que se hizo y se comprobó. La tabla de fuentes y las decisiones están en [DATA_SOURCES.md](DATA_SOURCES.md).

## Método
Descarga directa con `curl` de las páginas pedidas (aucorsa.es, /estado-del-servicio/, /movilidad-accesible/, /contacto/, NAP, transit.land), búsqueda web para localizar fichas y prensa, y descarga + inspección del GTFS real.

## Hallazgos
1. **Existe GTFS estático real y descargable** (espejo de Mobility Database del feed del NAP). Estructura limpia: 0 defectos con las comprobaciones de `tools/build_db.py`.
2. **Ese GTFS está caducado** (`calendar` y `calendar_dates` terminan el 2026-03-31) y hoy es 2026-10-15. Un feed más nuevo debe de existir en el NAP, pero su descarga exige cuenta. Consecuencia de producto: modo «horario de referencia» etiquetado, desactivable.
3. **AUCORSA declara un servicio web autenticado para terceros** (https://aucorsa.es/movilidad-accesible/, «Servicios web»): GTFS para datos estáticos y, para dinámicos, estructura de líneas, coordenadas de recorridos y estimaciones de llegada a cualquier parada, previo contacto con AUCORSA. Es la vía legítima hacia el tiempo real y encaja con la solicitud ya enviada. Es una declaración: no está verificado que funcione ni qué formato tiene. Esa misma página dice que todas las paradas urbanas tienen un QR a la web móvil con el tiempo de paso, y que existe un canal RSS.
4. **No hay GTFS-Realtime público conocido.** AUCORSA anunció tiempo real en Google Maps (COPE, 2026-04-27) y ocupación en paneles/web/app (nota oficial, 3 de agosto de 2026). Ninguna de las dos fuentes menciona API abierta, GTFS-RT ni datos descargables.
5. **La propia web tiene un servicio de estimaciones** (PWA «¿Cuándo llega mi bus?»). Su API interna no está documentada y la web carga Cloudflare Turnstile. No se consume (ver `DECISIONS.md` D-04). Se leyó únicamente el índice público de rutas de WordPress REST para documentar su existencia.
6. **El RSS de la web sí es utilizable** como fuente de avisos, con la limitación de que no es estructurado.
7. Los portales de feeds (NAP ficha, Transitland API) bloquean o exigen credenciales; lo que solo vi en extractos de búsqueda (p. ej. vigencia y recuento de la ficha NAP de diciembre de 2025) queda marcado como **no verificado en origen**.

## No investigado (declarado)
Contratos y pliegos del perfil de contratante de AUCORSA, datos abiertos municipales de tráfico y APIs del consorcio andaluz. No se hizo la revisión de licencias de NAP/Mobility Database. Se retoma en `ROADMAP.md`.

## Referencias
- https://aucorsa.es/ · /estado-del-servicio/ · /movilidad-accesible/ · /contacto/ · https://aucorsa.es/feed/
- https://nap.transportes.gob.es/ConjuntoDato/List
- https://mobilitydatabase.org/feeds/gtfs/mdb-2713
- https://www.transit.land/places/Spain/C%C3%B3rdoba
- https://gtfs.org/documentation/schedule/reference/ · https://gtfs.org/documentation/realtime/reference/
- COPE, 2026-04-27: «Aucorsa integra el sistema de posicionamiento de sus autobuses para ofrecer información en tiempo real»
- AUCORSA, 3 de agosto de 2026: «AUCORSA incorpora información sobre la ocupación de sus autobuses»
