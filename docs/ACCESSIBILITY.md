# Accesibilidad

Objetivo: directrices de accesibilidad de Android (touch, semántica, texto escalable) y contraste WCAG 2.2 AA.

| Requisito | Estado | Evidencia |
|---|---|---|
| Contraste texto ≥ 4,5:1 | Tokens medidos en la nota de estilo (todo texto ≥ 4,5:1 sobre `paper` y `raised`, ambos modos). Texto sobre colores de línea: negro o blanco por contraste calculado | Nota de estilo; `readableOn()` |
| Objetivos táctiles ≥ 48 dp | Controles y filas con `heightIn(min = 48 dp)` | Código; inspección visual |
| Estado no solo por color | Todo estado lleva icono (✓ ▲ ✕ ●) y palabra; la referencia lleva `REF` | Capturas |
| Semántica de lector de pantalla | `contentDescription` en filas de llegada (línea, destino, minutos, hora, base del dato), favorita (`stateDescription`), mapa, pestañas (`selected`), títulos (`heading`), cargas (`liveRegion`) | Test UI usa estas descripciones |
| Texto escalable | Probado a 160 % en parada; barra inferior limitada a 1,2× | Captura |
| Alternativa a mapa | Botón «Ver lista» + descripción del mapa + lista de paradas en detalle de línea | Test UI `mapOffersListAlternative` |
| Foco/teclado | Los elementos con `clickable` son enfocables; **no hay anillo de foco propio** (pendiente) | Sin verificar |
| Movimiento reducido | No hay animaciones propias | — |
| Permisos | Ubicación solo bajo demanda | Código |

**No verificado:** TalkBack real (orden de lectura, anuncios), navegación completa por teclado, daltonismo simulado, contraste real de las capturas con herramienta externa. Se hará en M6.
