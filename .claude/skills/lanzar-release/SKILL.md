---
name: lanzar-release
description: Usar al publicar una versión nueva de la app (Play Store, TestFlight o App Store de producción). Cubre subir la versión, etiquetar con el prefijo correcto, la trampa del "What's New" en producción iOS, y verificar que el CI quede verde.
---

# Publicar una versión

El **prefijo del tag** decide a dónde va. Un tag mal puesto publica o rompe un
release, así que el prefijo importa:

| Tag | Destino |
|---|---|
| `android-vX.Y.Z` | Play Store — **prueba interna** (testers) |
| `produccion-vX.Y.Z` | Play Store — **PRODUCCIÓN, LIVE 100%** (publica al instante) |
| `ios-vX.Y.Z` | **TestFlight** (testers, sin revisión) |
| `ios-prod-vX.Y.Z` | **App Store**: ENVÍA A REVISIÓN de Apple; al aprobar publica solo |

> **`produccion-*` (Android) deja el AAB LIVE para todos en minutos.** Úsalo solo
> tras validar en interna (`android-v*`). También se puede a mano: Actions → Run
> workflow → track `production`.

> **iOS ≠ Android.** En Android el tag publica; en iOS `ios-prod-*` **envía a
> revisión** (Apple tarda horas/días y puede rechazar). Verde en el CI =
> "enviada a revisión", NO "publicada".

## Pasos

1. **Subir la versión** en `composeApp/build.gradle.kts`:
   - `versionCode` **+1 sí o sí** (Play rechaza un versionCode repetido; el fallo
     sale recién al final del CI).
   - `versionName` a la versión nueva.
   - Añadir la línea al historial comentado (`// vNN/X.Y.Z = ...`), escrita para
     el dueño: qué cambia para quien usa la app.

2. **Alinear la versión de iOS.** El `CFBundleShortVersionString` del
   `iosApp/iosApp/Info.plist` debe COINCIDIR con el `versionName`. El
   `CFBundleVersion` (build number) es automático — no se toca.
   > Trampa real: si Gradle está en 0.9.9 pero el Info.plist en 0.9.8, el
   > `ios-prod-*` sube la 0.9.8, no la 0.9.9.

3. **Compilar antes de etiquetar** (un tag con build roto deja un release a medias):
   ```bash
   ./gradlew compileDebugKotlinAndroid
   ```

4. **Commit y push a `master`.**

5. **⚠️ SOLO para producción iOS (`ios-prod-*`): el "What's New" es OBLIGATORIO.**
   El lane usa `skip_metadata`, así que NO sube las notas. Si la versión en App
   Store Connect no tiene **"Novedades de esta versión"**, el auto-envío falla con:
   `missing a required attribute 'whatsNew' ... cannot be reviewed`. El binario
   sube igual, pero NO se envía a revisión.
   - **Antes o después del tag:** en App Store Connect → la versión → llenar
     **"Novedades"**. Si el CI ya subió el binario, basta llenar Novedades y darle
     **Submit** a mano.
   - No mencionar **otras plataformas** (Android, etc.) en la ficha: el precheck lo
     marca y Apple puede rechazar.

6. **Etiquetar y empujar:**
   ```bash
   # TestFlight + Play:
   git tag android-vX.Y.Z && git tag ios-vX.Y.Z
   git push origin android-vX.Y.Z ios-vX.Y.Z
   # Producción App Store (aparte, a propósito):
   git tag ios-prod-vX.Y.Z && git push origin ios-prod-vX.Y.Z
   ```

7. **Verificar que terminen verdes:**
   ```bash
   gh run list --limit 3
   ```
   Android ~6 min; iOS 13-23. **No dar el release por hecho hasta ver `success`**
   — iOS es el que más falla.

## Si el CI falla

- **Android producción en verde pero la versión NO sale** → mirar Play Console →
  Producción: si está en **BORRADOR**, el lane subió con `release_status: draft`
  (pasó con la v2.9.10; ya corregido a `completed` en el Fastfile — si reaparece,
  revisar ese campo). Se destraba a mano: Revisar versión → Iniciar lanzamiento.
  El CI da verde igual porque subir el borrador sí funcionó.
- **iOS `ios-prod-*` falla en el submit con `whatsNew`** → ver paso 5: falta
  "Novedades". El binario ya subió; llena Novedades en ASC y dale Submit.
- **Falla en segundos, ningún paso rojo** → cuota de GitHub Actions agotada.
  `gh run rerun <id>`.
- **iOS: cuelgue en `codesign`** → keychain auto-bloqueado, no el linker.
- **iOS exige Xcode 26.3** (Apple pide SDK iOS 26). No bajarlo.

## Nota Sania — App Store bloqueado

El App Store de Sania está frenado por el **5.1.1(ix)** (exige cuenta de
organización). El tag `ios-prod-*` no pasará hasta resolver la cuenta
(empresa + D-U-N-S). TestFlight (`ios-v*`) sí funciona.

## Después

Publicar no llega solo: Play puede tardar en revisar, TestFlight necesita
repartir la build a los probadores, y App Store revisa antes de publicar.
