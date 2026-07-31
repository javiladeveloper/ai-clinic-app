---
name: lanzar-release
description: Usar al publicar una versión nueva de la app a Play Store o TestFlight. Cubre subir la versión, etiquetar y verificar que ambos CI queden verdes.
---

# Publicar una versión

Los tags disparan los workflows: `android-vX.Y.Z` → Play Store,
`ios-vX.Y.Z` → TestFlight. Un tag mal puesto publica o rompe un release, así
que el orden importa.

## Pasos

1. **Subir la versión** en `composeApp/build.gradle.kts`:

   - `versionCode` **+1 sí o sí**. Play Store rechaza un envío que repita un
     versionCode ya publicado, y el fallo aparece recién al final del CI.
   - `versionName` a la versión nueva.
   - Añadir una línea al historial comentado que hay encima, en el mismo
     formato (`// vNN/X.Y.Z = ...`). Se escribe para el dueño, no para
     desarrolladores: qué cambia para quien usa la app.

   `CFBundleVersion` de iOS es automático — no hay que tocarlo.

2. **Compilar antes de etiquetar.** Un tag con build roto deja un release a
   medias que hay que limpiar a mano:

   ```bash
   ./gradlew compileDebugKotlinAndroid
   ```

3. **Commit y push a `master`.**

4. **Etiquetar y empujar los dos tags:**

   ```bash
   git tag android-vX.Y.Z && git tag ios-vX.Y.Z
   git push origin android-vX.Y.Z ios-vX.Y.Z
   ```

5. **Verificar que ambos terminen verdes:**

   ```bash
   gh run list --limit 2 --json name,status,conclusion \
     -q '.[] | "\(.name): \(.status)/\(.conclusion // "en curso")"'
   ```

   Android tarda ~6 min; iOS entre 13 y 23. **No dar el release por hecho
   hasta ver `success` en los dos** — iOS es el que más falla.

   Consultar por ID (`gh run view <id>`) devuelve vacío en este entorno; usar
   `gh run list`.

## Si el CI falla

- **Falla en segundos y ningún paso aparece como fallido** → no es el código,
  es la cuota de GitHub Actions agotada. Se resuelve subiendo la cuota y
  reintentando el mismo commit con `gh run rerun <id>`.
- **iOS: cuelgue en `codesign`** → suele ser el keychain que se auto-bloquea,
  no el linker.
- **iOS exige Xcode 26.3** porque Apple pide SDK de iOS 26. No bajarlo.

## Después

Publicar en las tiendas no llega solo: Play Store puede tardar en revisar, y
TestFlight necesita que se reparta la build a los probadores.
