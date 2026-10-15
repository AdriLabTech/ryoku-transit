# Decisiones

| ID | Decisión | Motivo / alternativa descartada |
|---|---|---|
| D-01 | Android nativo + Compose, sin backend | Encaja con el uso móvil y personal; sin servidor no hay secretos ni coste. PWA descartada: peor encaje con permisos y offline |
| D-02 | Compose sin Material 3 | Identidad Ryoku Kami: componentes propios; menos dependencias |
| D-03 | SQLite prebuilta generada por script, no Room | Datos de solo lectura y esquema estable; evita KSP/ORM |
| D-04 | No usar `lightapi.aucorsa.es` ni Google Maps | Endpoint interno no documentado, protegido por Turnstile; scraping vetado. Se pide acceso oficial |
| D-05 | Modo «horario de referencia» etiquetado y desactivable | El GTFS caducó el 2026-03-31; sin él la app estaría vacía. Es un día equivalente del feed, jamás «hoy» (`Basis.REFERENCE`, banner, `REF`) |
| D-06 | Mapa esquemático propio sin teselas | Sin claves, términos ni red; estilo plano a tinta |
| D-07 | Avisos por RSS oficial | Única fuente de avisos accesible; se declara que no es estructurada |
| D-08 | Grafito por defecto, Washi opcional | Regla de estilo de Adrián |
| D-09 | `Basis.LIVE` no existe todavía | Evita capacidades ficticias |
| D-10 | JDK 21 / Java 17 de destino; `minSdk 26` | Cobertura amplia; `java.time` disponible sin desugaring |
| D-11 | `compileSdk 37` | Lo exigen las dependencias actuales (core-ktx 1.19) |
