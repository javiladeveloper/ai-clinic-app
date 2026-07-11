# CI/CD — Despliegue automático de Sania (Android + iOS)

Con esto, publicar la app deja de ser un ritual manual: empujas un **tag** (o le das a
"Run workflow" en GitHub) y GitHub Actions compila, firma y sube a la tienda.

- **Android** → Play Store (canal `internal` por defecto, como borrador para que revises).
- **iOS** → TestFlight.

Los secretos NUNCA viven en el repo: se guardan en **GitHub → Settings → Secrets and
variables → Actions**. Los workflows los materializan en el runner y los borran al final.

---

## Cómo se dispara

| Acción | Qué hace |
|---|---|
| Push de un tag `android-v2.1.0` | Compila AAB + sube a Play Store (internal) |
| Push de un tag `ios-v2.1.0` | Compila IPA + sube a TestFlight |
| GitHub → Actions → "Android · Play Store" → Run workflow | Igual, a mano (puedes elegir internal/production) |
| GitHub → Actions → "iOS · TestFlight" → Run workflow | Igual, a mano |

Ejemplo para publicar la versión actual en ambas:
```sh
git tag android-v2.1.0 && git push origin android-v2.1.0
git tag ios-v2.1.0 && git push origin ios-v2.1.0
```

> **Antes de cada release sube el `versionCode`** en `composeApp/build.gradle.kts`
> (Android) y el `CFBundleVersion` en `iosApp/iosApp/Info.plist` (iOS). Play Store y
> App Store rechazan un build con un número repetido.

---

## Secretos a configurar (una sola vez)

### 🤖 Android (5 secretos)

| Secret | Qué es | De dónde sale |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | El `.jks` de subida en base64 | `base64 -w0 subida-keystore.jks` (Linux/Mac) o `[Convert]::ToBase64String([IO.File]::ReadAllBytes("subida-keystore.jks"))` (PowerShell) |
| `ANDROID_STORE_PASSWORD` | Contraseña del keystore | La que usas hoy en `keystore.properties` |
| `ANDROID_KEY_ALIAS` | Alias de la clave | Idem (`sania-subida`) |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave | Idem |
| `PLAY_STORE_JSON_KEY` | Cuenta de servicio de Google Play (JSON completo) | Ver abajo |

**Cuenta de servicio de Google Play** (para que Fastlane suba sin login manual):
1. Google Play Console → **Configuración → Acceso a API** → vincula un proyecto de
   Google Cloud.
2. En Google Cloud → **IAM → Cuentas de servicio** → crea una → genera una **clave JSON**.
3. De vuelta en Play Console → **Usuarios y permisos** → invita al email de esa cuenta
   de servicio con permiso de **"Administrar versiones"** (al menos para el canal internal).
4. Pega el contenido completo del JSON como el secret `PLAY_STORE_JSON_KEY`.

### 🍎 iOS (6 secretos)

| Secret | Qué es |
|---|---|
| `ASC_KEY_ID` | Key ID de la App Store Connect API Key |
| `ASC_ISSUER_ID` | Issuer ID de la API Key |
| `ASC_KEY_CONTENT` | El archivo `.p8` de la API Key, en base64 |
| `ASC_APPLE_ID` | (opcional) tu Apple ID de desarrollador |
| `IOS_DIST_CERT_BASE64` | Certificado de distribución `.p12` en base64 |
| `IOS_DIST_CERT_PASSWORD` | Contraseña del `.p12` |
| `IOS_PROVISION_PROFILE_BASE64` | Perfil de aprovisionamiento `.mobileprovision` en base64 |

**App Store Connect API Key** (para subir a TestFlight sin 2FA):
1. App Store Connect → **Usuarios y acceso → Integraciones → App Store Connect API** →
   genera una clave con rol **App Manager**.
2. Descarga el `.p8` (solo se puede una vez), anota el **Key ID** y el **Issuer ID**.
3. `base64 -i AuthKey_XXXX.p8` → pégalo en `ASC_KEY_CONTENT`.

**Certificado + perfil de firma** (si no usas `fastlane match`):
- El `.p12` sale de Keychain (exportas tu certificado "Apple Distribution").
- El `.mobileprovision` sale de developer.apple.com → Profiles (App Store).
- Ambos a base64 y pégalos en los secrets correspondientes.

> **Simplificación futura recomendada:** `fastlane match` guarda certificados y perfiles
> en un repo privado cifrado y los instala solo. Elimina el manejo manual de `.p12`/perfil.
> Se configura con `fastlane match init`. Vale la pena cuando el equipo crezca.

---

## Notas importantes

- **iOS solo compila en macOS** → el workflow usa `runs-on: macos-14` (runners de pago de
  GitHub, pero baratos para builds ocasionales). Android corre en `ubuntu-latest` (gratis).
- Los workflows dejan la versión de Android como **borrador** (`release_status: draft`):
  entras a Play Console y le das "Publicar" tú. Es un seguro para no lanzar sin querer.
- Si algo falla, el paso "Limpiar secretos del runner" corre igual (`if: always()`) para
  no dejar claves materializadas.
- **Verifica los nombres del scheme iOS** (`iosApp`) — si el proyecto Xcode usa otro
  nombre de scheme, ajústalo en `fastlane/Fastfile` (lane `beta`, `build_app scheme:`).

---

## 🔧 Errores que superamos al montar Android (registro real, para otros agentes)

El CI de Android **funciona** (probado 2026-07-11, versionCode 7 subió a Play Store internal).
Estos fueron los tropiezos en orden, con su causa y fix. Si un agente futuro reconfigura
o replica esto (ej. iOS, u otra app), que los tenga en cuenta:

### 1. `./gradlew: Permission denied` (en el runner Ubuntu)
- **Causa:** `gradlew` se commiteó desde Windows sin el bit de ejecutable; Linux no lo puede correr.
- **Fix:** `git update-index --chmod=+x gradlew` (queda modo 100755 en git) + `chmod +x ./gradlew`
  en el step del workflow como respaldo.

### 2. `You don't have write permissions for /var/lib/gems/...` (al instalar Fastlane)
- **Causa:** `gem install fastlane` global falla por permisos en el runner.
- **Fix:** Fastlane vía **Gemfile** (`gem "fastlane"`) + action `ruby/setup-ruby` con
  `bundler-cache: true`, e invocar con `bundle exec fastlane ...`. La action va **pineada
  a un SHA** (no `@v1`) porque el linter de seguridad (Codacy) marca las actions de terceros
  sin pinear como error.

### 3. `Authorization failed: invalid_grant - Invalid JWT Signature` (al subir a Play Store)
- **Causa (la más traicionera):** el JSON de la cuenta de servicio, pegado CRUDO en el secret
  de GitHub, corrompe la `private_key` (sus `\n` se rompen) → el token JWT no firma.
- **Fix:** pasar el JSON en **BASE64**. Secret `PLAY_STORE_JSON_KEY_B64` = `base64 -w0 key.json`;
  el workflow lo decodifica con `echo "$B64" | base64 -d > play-store-key.json`. A prueba de
  corrupción de saltos de línea. **NO usar el JSON crudo como secret.**

### 4. `Version code X has already been used`
- **Causa:** intentar subir un AAB con un `versionCode` que Play Store ya registró (aunque sea
  de un intento fallido previo o un AAB manual).
- **Fix:** subir `versionCode` en `composeApp/build.gradle.kts` antes de cada release. Ese
  error, paradójicamente, **confirma que todo el pipeline funciona** (autenticó, conectó,
  preparó el AAB) — solo faltaba el número nuevo.

### Cómo se creó la cuenta de servicio de Google Play (no está en Play Console donde uno cree)
- **"Acceso a la API" NO está dentro de la app** en Play Console: es config de la CUENTA de
  desarrollador (salir de la app → vista de cuenta). Y en algunas cuentas ni aparece en el menú.
- **Camino que sí funcionó:** crear la cuenta de servicio DIRECTO en Google Cloud Console:
  1. Google Cloud → crear proyecto (ej. `sania-500102`).
  2. Habilitar **"Google Play Android Developer API"** (imprescindible, se olvida).
  3. IAM → Cuentas de servicio → crear → generar clave **JSON**.
  4. Play Console → Usuarios y permisos → invitar el email de la cuenta de servicio con permiso
     de **versiones** ("Lanzar en canales de prueba" + opcional "Lanzar a producción").
  5. La propagación de permisos puede tardar ~minutos.

### Regla de oro para cada release
Subir el número de versión ANTES de taggear:
- Android: `versionCode` en `composeApp/build.gradle.kts`.
- iOS: `CFBundleVersion` en `iosApp/iosApp/Info.plist`.
Si no, la tienda rechaza con "ya usado".
