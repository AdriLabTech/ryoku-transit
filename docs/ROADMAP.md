# Hoja de ruta

| Hito | Estado | Notas |
|---|---|---|
| M0 Investigación | **Hecho** (parcial en contratos/licencias) | `RESEARCH.md`, `DATA_SOURCES.md` |
| M1 Fundación | **Hecho** | Build, tests, tokens, docs |
| M2 Datos estáticos | **Hecho** | GTFS importado, calendario, geometrías |
| M3 Experiencia principal | **Hecho** (v0.1) | Paradas, líneas, mapa, favoritas, estados |
| M4 Datos dinámicos | **Bloqueado** | Solo RSS integrado; falta acceso a AUCORSA |
| M5 Histórico y predicción | **Parcial** | Anotaciones propias (manual + auto) y corrección evaluada walk-forward: `LEARNING.md`. Predicción en vivo sigue bloqueada |
| M6 Pulido | **En curso** | Faltan TalkBack, pantallas pequeñas/apaisado, rendimiento |

## Siguiente
1. **Adrián:** pedir a AUCORSA el GTFS vigente (el del NAP sigue siendo el caducado: `NAP_GUIA.md`) y reimportar con `tools/import_gtfs.sh`.
2. Seguimiento de la solicitud a AUCORSA; al llegar, escribir el adaptador con el contrato real.
3. Revisar licencias (NAP, Komiki Mono) y contratos/pliegos de AUCORSA.
4. Anotar con uso real y revisar en ESTADO si el ajuste mejora; probar la detección automática en un autobús real (hoy solo emulador).
5. M6: TalkBack, `Favoritas` con test de fallo de red, tamaños de pantalla.
6. Planificador de rutas y notificaciones: solo si hay datos que las justifiquen.
