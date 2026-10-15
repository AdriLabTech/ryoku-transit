# Estrategia de predicción

## Punto de partida
**Línea base y única etapa implementada: A (horario).** Es un horario publicado, no una predicción. No hay posiciones, ni llegadas en vivo, ni observaciones reales, así que **no existe ninguna predicción en la app y no se afirma precisión alguna.**

## Qué falta para avanzar
| Etapa | Requisito | Estado |
|---|---|---|
| A. Horario | GTFS vigente | Hecho (feed caducado → referencia) |
| B. Basada en posición | Posiciones recientes autorizadas + geometría (ya hay `shapes` y `shape_dist_traveled`) | Bloqueada: sin fuente |
| C. Segmentos históricos | Observaciones propias con marca de tiempo, legítimamente guardadas | Bloqueada |
| D. Modelos estadísticos/ML | Datos suficientes y etapas B/C validadas | No procede |

## Qué datos se necesitan (a pedir a AUCORSA)
Llegadas estimadas por parada y su timestamp, identificadores de viaje casables con el GTFS, y permiso para conservar histórico. Sin hora real de paso (verdad terreno) no hay forma honesta de evaluar errores.

## Evaluación (diseño, sin ejecutar)
Cuando existan datos: MAE, RMSE, sesgo, error por horizonte (2/5/10 min), por línea y sentido, hora del día, frescura del dato y cobertura de predicciones válidas; partición cronológica sin fuga; comparación con el horario y con la estimación oficial. Cualquier intervalo de incertidumbre se derivaría de un método validado y calibrado; **no se mostrará un porcentaje inventado**.

## Qué no se ha hecho y por qué
No hay pipeline de evaluación ejecutable: sin observaciones no hay nada que medir, y construir uno vacío sería aparentar trabajo. Se escribirá junto al adaptador autorizado.
