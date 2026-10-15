# Aprendizaje con tus anotaciones

Objetivo: corregir poco a poco las horas que muestra la app con lo que de verdad pasa en tus paradas, sin tiempo real de AUCORSA. **No es predicción en vivo**: es una corrección estadística del horario según tus anotaciones. Se activa solo si demuestra que mejora.

## Cómo se anota
1. **A mano:** en una parada, toca una fila → «Ahora», «−1», «−2» o «−5 min» (cuándo llegó realmente). Las filas con hora ya pasada (hasta 10 min) siguen visibles con «¿pasó?» para poder anotar retrasos.
2. **Automático:** botón «Estoy esperando aquí». Un servicio en primer plano (notificación visible, máx. 25 min, cancelable) detecta que esperaste junto a la parada y luego te moviste a velocidad de autobús. Te pide **confirmar la línea** (o «No era un autobús»). Sin confirmación no se guarda nada.

Detalle del detector (`BoardingDetector`): espera ≥ 20 s a ≤ 60 m de la parada y < 1,5 m/s; después velocidad ≥ 7 m/s (25 km/h) sostenida 8 s, con arranque (≥ 3 m/s) menos de 2 min tras salir de la zona. Velocidad = máx(la del receptor, desplazamiento en ventana de 5 s − precisión), robusta al GPS de baja calidad y a receptores que informan 0. Caminar (1,4) y bici (5,5) no disparan.
La hora anotada es la del *arranque*, que aproxima la salida del bus de la parada; es la hora programada de salida la que se compara.

## Qué se guarda y qué no
Solo: línea, sentido, parada, viaje, día de servicio, hora programada, hora real, base (vigente/referencia), origen (manual/auto) y marca de registro. **Nunca** posiciones GPS (viven en memoria del servicio), ni se envía nada fuera: `observations.db` está en el móvil. Se puede borrar todo desde ESTADO.

## El modelo (`DelayModel`)
- Error = hora real − hora programada. Se descartan errores fuera de −10…+30 min (probable viaje mal emparejado).
- Estimación **robusta** (mediana) con **contracción jerárquica** hacia el nivel superior: global → línea → línea × franja horaria × tipo de día → ×parada. Con pocas muestras manda el nivel general; con 0 datos, 0 corrección. `k = 4`.
- Un modelo por **base**: lo aprendido sobre el horario de referencia no se mezcla con el vigente.
- Se aplica a una fila solo si hay ≥ 3 observaciones de esa línea y la corrección redondea a ≥ 1 min. La fila muestra `09:55 → ≈09:58 · AJUSTADO (n obs.)`; el horario original siempre se ve.

## Cuándo se activa (la salvaguarda)
`Evaluation.walkForward`: orden cronológico; cada anotación se predice con un modelo entrenado **solo con las anteriores** (sin fuga). Se calculan MAE, RMSE y sesgo del horario y del modelo. **El ajuste se activa para una base solo si hay ≥ 8 evaluaciones y MAE(modelo) ≤ 0,95 × MAE(horario).** Si no mejora, la app lo dice en ESTADO y no cambia nada. Los tests prueban que un retraso sistemático se aprende (MAE < 50 %), que el ruido puro **no** activa el ajuste y que un cambio de régimen no se «adivina».

## Límites honestos
- Con el feed caducado, anotas contra el horario de *referencia* (marzo): si en octubre el horario real es otro, el error mezcla «horario distinto» y «retraso»; la evaluación lo detectará (no mejorará) o lo absorberá como corrección útil para ese modo, pero no es retraso puro. Un GTFS vigente lo mejora.
- Hacen falta decenas de anotaciones por línea para ver mejoría real; con una parada y una línea, antes. Una sola persona sesga hacia sus propias paradas y horas.
- El emparejamiento viaje↔llegada en modo auto se hace por cercanía de hora dentro de ±15 min y línea confirmada.
- La detección se verificó en el emulador con GPS simulado y con tests; **no** con un autobús real.
- Sin evaluación de intervalos de incertidumbre: no se muestra ninguna «confianza».
