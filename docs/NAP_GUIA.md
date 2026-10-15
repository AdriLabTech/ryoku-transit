# Guía: cuenta en el NAP, descarga e importación del GTFS

> **Antes de empezar (verificado 2026-10-15):** la ficha pública del NAP para «Autobús urbano de Córdoba» (https://nap.transportes.gob.es/ConjuntoDato/Detail/1375) declara **rango de fechas 24/2/2026 – 30/3/2026** y «Actualizado el 25/2/2026». Es decir, el NAP ofrece el **mismo feed caducado** que ya está en la app (2,28 MB frente a 2,39 MB del espejo; mismos 8.780 viajes/37 rutas/721 paradas). **Registrarte no te dará horarios vigentes** salvo que AUCORSA suba una versión nueva. Merece la pena crearse la cuenta solo para comprobar la ficha de vez en cuando; el feed vigente hay que pedírselo a AUCORSA.

## 1. Crear la cuenta de usuario (gratuita)
1. Abre https://nap.transportes.gob.es/ y pulsa **Registrarse** (o ve directo a https://nap.transportes.gob.es/Account/SignIn).
2. Elige **«Soy un usuario»** (consumir datos). *«Soy un proveedor»* es para quien publica datos: no lo necesitas.
3. Rellena el formulario real: **Email, Contraseña (y confirmación), Nombre, Apellidos, Organismo o Entidad y Puesto que ocupa**. Para uso personal puedes indicar «Particular» / «Estudiante» (campos que el formulario muestra; cuáles son obligatorios lo marca el propio formulario con «(*)»).
4. Marca las dos casillas: condiciones generales de uso y política de condiciones de uso (léelas antes) y pulsa **Registrarse**.
5. Si el portal envía un correo de confirmación, confirma desde él. *No he podido completar el registro yo mismo, así que este paso (y el aspecto exacto tras registrarte) no está verificado.*
6. Inicia sesión en https://nap.transportes.gob.es/Account/Login.

## 2. Descargar el GTFS de AUCORSA
1. Abre la ficha: https://nap.transportes.gob.es/ConjuntoDato/Detail/1375 (o busca «Aucorsa» en Listado → buscador).
2. **Mira primero el «Rango de fechas» y «Actualizado el»**. Si el fin es posterior a hoy, merece la pena seguir; si sigue en 30/3/2026, no hay nada nuevo.
3. Con sesión iniciada, pulsa **Descargar** (GTFS, ~2,3 MB) y guarda el `.zip` (p. ej. en `~/Descargas`). No lo descomprimas.

## 3. Importarlo en la app
```bash
cd ~/Proyectos/ryoku-transit
tools/import_gtfs.sh ~/Descargas/<fichero>.zip
```
El script reconstruye la base, muestra vigencia, recuentos y defectos, avisa si es el mismo fichero o si sigue caducado, y copia el zip a `data/gtfs-raw/` (fuera de git). Después:
```bash
./gradlew :app:testDebugUnitTest
tools/run-emulator.sh          # o instala en tu móvil
```
Si el feed es vigente, la app deja de usar el modo «horario de referencia» y el banner pasa a «Horario vigente hasta …». Las anotaciones hechas sobre el horario de referencia se conservan, pero **se aprenden por separado** de las del horario vigente.

## 4. Mientras tanto
- Reenvía o amplía el correo a AUCORSA pidiendo (a) el GTFS vigente y su URL estable y (b) el alta en su servicio web autenticado (ver `DATA_SOURCES.md`, preguntas abiertas).
- Usa las anotaciones (`LEARNING.md`): son lo único que mejora la precisión sin su API.
