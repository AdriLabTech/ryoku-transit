# Publicar y actualizar la app

Las versiones se distribuyen como **APK firmado en GitHub Releases**. La app consulta `https://api.github.com/repos/AdriLabTech/ryoku-transit/releases/latest` (constante `UpdateConfig.REPO`), descarga el APK de la release, comprueba tamaño y SHA-256, comprueba que es la misma app y más nueva, y lanza la instalación con `PackageInstaller`.

**Requisito:** el repositorio debe ser **público** (la API se consulta sin credenciales; no se puede meter un token en la app). Si cambia el nombre del repo, cambiar `UpdateConfig.REPO`.

## Una sola vez
1. `tools/make-keystore.sh` → crea `~/.config/ryoku-transit/release.jks` y `keystore.properties` (ignorado por git).
2. **Copia de seguridad** de `~/.config/ryoku-transit/` y de `keystore.properties` fuera del portátil. Si se pierde la clave, nadie podrá actualizar sin desinstalar (pierden favoritas y anotaciones).
3. Crear el repo público en GitHub, añadir el remoto y subir `main`.

## Cada versión
1. Subir `appVersion` en `app/build.gradle.kts` (`0.1.0` → `0.1.1`). `versionCode` se deriva solo.
2. Commit y `git push`.
3. `tools/release.sh publish` → pasa los tests, compila, verifica la firma con `apksigner`, crea `dist/ryoku-transit-X.Y.Z.apk` y la release `vX.Y.Z` en GitHub.

## Cómo se actualiza el móvil de un compañero
- Al abrir la app (máx. cada 6 h) busca versión nueva; con wifi la descarga sola y aparece el aviso «Versión X lista · Instalar ahora». Con datos móviles pregunta antes.
- Android siempre pide permitir «instalar apps desconocidas» a Ryoku Transit la primera vez, y puede pedir confirmar cada instalación. Desde Android 12, tras la primera actualización hecha por la propia app, el sistema puede instalar sin confirmación cuando la app no está en uso.
- En ESTADO → Versión hay un botón «Buscar actualizaciones».
- Las builds de depuración no comprueban solas (no tocar la red en los tests) y están firmadas con otra clave: no pueden actualizarse a un release.
