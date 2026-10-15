# Sistema de diseño: Ryoku Kami

Fuente canónica: nota del vault `09 - Claude/Referencias/Estilo visual Ryoku Kami`. La app la implementa en `ui/Theme.kt` y `ui/Components.kt`.

## Tokens (`RyokuColors`)
Grafito (por defecto) y Washi (opcional, desde ESTADO). Valores iguales a la nota: `paper #211D1A/#F3ECE0`, `raised`, `high`, `ink`, `inkDim`, `inkMuted`, `inkFaint`, `secondary` (egeo), `gold`, `brand` (disco bermellón, solo marca), `error`, `warn`, `ok`, líneas al 16/30/48 %. Las pantallas solo usan `Ryoku.c.*`.

| Semántica | Token |
|---|---|
| Acción primaria | `ink` relleno (botón seleccionado) |
| Fondo / superficie | `paper` / `raised` |
| Texto | `ink` › `inkDim` › `inkMuted` |
| Horario vigente | `ok` con ✓ |
| Referencia / desactualizado | `warn` con ▲ y etiqueta `REF` |
| Error / sin cobertura | `error` con ✕ |
| Información | `secondary` con ● |
| Favorita | `gold` ★ |

## Tipografía
Komiki Mono (única; Regular y Bold), escala Display 40 · H1 28 · H2 20 · H3 16 · Cuerpo 14 · Pequeño 12 · Etiqueta 11 (mayúsculas, tracking .08). Al ser monoespaciada, las cifras de los minutos no bailan al actualizarse. Los minutos usan el tamaño H1 y son lo más grande de cada fila. *Licencia de Komiki Mono no revisada*: la fuente está empaquetada solo para esta app privada y no debe distribuirse.

## Forma y movimiento
Radio 4 dp, borde 1 dp, sin sombras (jerarquía por tono y borde), espacios en múltiplos de 4. Control mínimo 48 dp. Sin animaciones decorativas: no hay movimiento que respetar; las listas usan claves estables y la hora no cambia de tamaño al actualizar.

## Componentes
`RouteBadge` (color de línea del feed con texto negro/blanco calculado por contraste), `ArrivalRow`, `StopCard`, `StatusPill`, `Banner` (barra tonal), `SearchField`, `FavoriteToggle`, `EmptyState`, `LoadingBlock`, `GhostButton`, `SectionLabel` (`00 // …`), `NetworkMap`, barra inferior propia. Estados cubiertos: cargando, vacío, error, sin conexión (RSS con copia), sin cobertura y datos de referencia.

## Decisiones visuales
- Marca = disco bermellón; sin kanji ni halftone (regla Ryoku Kami).
- Mapa: puntos de 16 dp de fondo, paradas cuadradas, ruta resaltada en `secondary`, parada seleccionada con disco bermellón.
- El horario de referencia usa `inkDim` en los minutos y `warn` en la etiqueta para que no se confunda con información de hoy.

## Verificación visual (emulador Pixel API 36, 2026-10-15)
Inspeccionadas capturas de: búsqueda, parada, líneas, detalle de línea, mapa, avisos, estado, Grafito y Washi (parada), y parada con texto del sistema al 160 %. Defectos encontrados y corregidos: barra inferior que partía «FAVORITAS» a 160 % (se limita su escala a 1,2×), mapa que desbordaba su caja, paradas del mapa demasiado grandes, hint de búsqueda que partía línea. **No inspeccionado:** pantalla pequeña (<5″), modo apaisado, tablet, Favoritas con datos tras reinicio (cubierto por test UI, no por captura). Capturas en `docs/screenshots/`.
