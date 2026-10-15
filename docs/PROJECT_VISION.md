# Visión

**RYOKU TRANSIT** es una app personal para consultar los autobuses de AUCORSA (Córdoba) con más claridad y honestidad que una app genérica: cada hora muestra de dónde sale (horario publicado, referencia o —cuando exista— tiempo real) y su frescura.

## Preguntas que debe contestar
| Pregunta | Estado (2026-10-15) |
|---|---|
| ¿Qué líneas pasan por esta parada? | Sí (GTFS) |
| ¿Cuándo llega el próximo? | Sí, **horario**; hoy solo como referencia (feed caducado) |
| ¿Es en vivo o programado? ¿Cuándo se actualizó? | Sí: etiqueta `HORARIO`/`REF`, banner y pantalla Estado |
| ¿Dónde está el bus? | **No**: sin fuente de posiciones |
| ¿Hay incidencias? | Parcial: avisos RSS de la web, sin asociar a línea/parada |
| ¿Favoritas y atajos? | Paradas favoritas |
| ¿Cómo llego a mi destino? | No (planificador fuera del alcance actual) |

## Alcance y no-objetivos
Uso privado, sin publicación ni backend propio. No se compite con la app oficial: se busca mejor experiencia y rigor. No se inventan posiciones, esperas, incidencias, ocupación ni confianza. Nada de scraping de Google Maps ni de endpoints internos de aucorsa.es.

## Éxito
Una persona abre una parada y en menos de 3 toques sabe cuánto falta, de qué fuente sale ese dato y si debe fiarse. Cuando AUCORSA autorice su servicio web, solo hay que añadir un adaptador.
