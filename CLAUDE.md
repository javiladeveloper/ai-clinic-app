# Sania — App nativa (Kotlin Multiplatform)

App nativa de **Sania** (SaaS clínico multi-tenant multi-rubro). Comparte la MISMA
base Supabase que la web (`saniape.com`), que actúa como **backend**. Repo web:
`../ai-clinic-dashboard` (Next.js). Branch de trabajo de la app: **`master`**.

**Principio rector:** la app NO reimplementa reglas de negocio; las **consume** de la
web. Lecturas directas a Supabase con Bearer del staff (RLS `get_clinica_id()`);
escrituras complejas (completar sesión / comisión / kardex / pago / contador) vía
endpoints **`/api/staff/*`** de la web. Cada módulo de la app debe ser **lo más
parecido posible al de la web**, no una versión recortada.

## Plataformas

- **Android**: completo y **en producción** (Play Store, Closed Testing → producción).
  El usuario compila/corre desde **Android Studio** (emulador Pixel_8).
- **iOS**: **en construcción (Fase 1)**. Ver sección abajo.

> ⚠️ **REGLA del dueño:** el USUARIO compila y corre la app (Android Studio / Xcode).
> El agente solo compila para **verificar** (`compileDebugKotlinAndroid` en Android;
> `compileKotlinIosSimulatorArm64` en iOS). NO hacer `installDebug`, `screencap`, ni
> abrir emuladores para "probar" salvo que el usuario lo pida explícitamente.

## 🚀 CI/CD — Despliegue automático (LEER antes de tocar el CI)

Android despliega solo: `git tag android-v2.x.y && git push origin android-v2.x.y` →
GitHub Actions compila el AAB firmado y lo sube a Play Store (internal, borrador).
**Antes:** subir `versionCode` en `composeApp/build.gradle.kts` (si no, la tienda rechaza).

📖 **`docs/ci-cd-despliegue.md`** tiene TODO: los secrets a configurar, cómo se creó la
cuenta de servicio de Google Play, y — clave — **los 4 errores que superamos** al montarlo
(gradlew +x, gem permisos, "Invalid JWT Signature" → JSON en base64, "version code usado").
Si vas a tocar el CI o montar iOS, léelo primero para no repetir los tropiezos.

## ⚡ Patrón de FLUIDEZ (para que la app no se sienta lenta)

Regla que seguimos al cargar datos, para que los flujos diarios se sientan rápidos:
1. **Paralelizar `await`s independientes** con `coroutineScope { async {…} }` en vez de
   pedirlos en serie (ej. `hitosDe` pide citas + próxima sesión en paralelo).
2. **No vaciar la pantalla al recargar**: mantener el contenido visible + un aviso sutil
   "Actualizando…" en vez de un spinner que borra todo (ver `PantallaFichaPaciente`).
3. **Recarga parcial tras una acción de 1 ítem**: no re-cargar toda la ficha/lista por
   completar una sesión; actualizar solo lo que cambió.
Aplica este patrón a cualquier pantalla/repo nuevo con cargas.

---

## 🖥️ PRIMERA VEZ EN LA MAC — instalar antes de nada

En la Mac (todavía sin herramientas). Orden:

1. **Xcode** — App Store → buscar "Xcode" → instalar (~15 GB, tarda). Luego:
   ```sh
   sudo xcodebuild -license accept
   xcode-select --install     # Command Line Tools (a veces ya vienen con Xcode)
   ```
2. **Homebrew** (gestor de paquetes de macOS):
   ```sh
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   ```
3. **JDK 17** (Gradle lo necesita):
   ```sh
   brew install openjdk@17
   sudo ln -sfn $(brew --prefix)/opt/openjdk@17/libexec/openjdk.jdk \
     /Library/Java/JavaVirtualMachines/openjdk-17.jdk
   ```
4. **(Opcional) Android Studio** — solo si quieres editar Kotlin cómodo. Para iOS NO
   es imprescindible: basta Xcode + el `./gradlew` del repo.
5. **Clonar el repo:**
   ```sh
   git clone git@github.com:javiladeveloper/ai-clinic-app.git
   cd ai-clinic-app        # branch master (ya es el default)
   ```
   > La primera `./gradlew` descarga el toolchain de Kotlin/Native para Apple
   > (varios cientos de MB, solo la primera vez).

**Cuenta Apple Developer:** NO hace falta para el simulador (Fase 1). Solo para
iPhone físico + App Store (Fase 3).

---

## 🍎 iOS — DÓNDE NOS QUEDAMOS (2026-07-07)

Se agregó la plataforma **iOS** a la app (antes era Android-only). **Todo el andamiaje
se escribió desde Windows, donde iOS NO compila** (el toolchain Apple de Kotlin/Native
solo existe en macOS; en Windows `compileKotlinIosSimulatorArm64` sale `SKIPPED`).

**Por eso el trabajo del agente en la Mac es: VERIFICAR que esto compila y corre.**

### ✅ Qué ya está hecho (commiteado en `master`)

- `composeApp/build.gradle.kts`: targets `iosX64/iosArm64/iosSimulatorArm64` + framework
  estático `ComposeApp`; `iosMain.dependencies` con `ktor-client-darwin`.
- `composeApp/src/iosMain/`: los **13 `actual`** de las declaraciones `expect` de
  `commonMain`. Implementados **de verdad**:
  - `data/Preferencias.ios.kt` (NSUserDefaults)
  - `data/HttpClienteFactory.ios.kt` (engine Darwin)
  - `data/Supabase.ios.kt` (`siteUrlPlataforma`: debug→localhost:3000, release→prod)
  - `ui/Acciones.ios.kt` (UIApplication.openURL + UIPasteboard)
  - `ui/SonidoIntro.ios.kt` (AVAudioEngine — mismo shimmer que Android)
  - `ui/ComprimirImagen.ios.kt` (UIImage + JPEG)
  - `ui/BotonAtras.ios.kt` (no-op: iOS no tiene botón atrás global)
  - `auth/AppScope.ios.kt` (MainScope)
  - `MainViewController.kt` (`ComposeUIViewController { App() }` — entry point)
  - **Stubs Fase 2** (marcados `TODO(iOS Fase 2)`, compilan y no rompen):
    `auth/AuthGoogle.ios.kt`, `ui/PushNativo.ios.kt`, `ui/reservar/MapaClinicas.ios.kt`,
    `ui/CamaraFoto.ios.kt`, `ui/SelectorArchivo.ios.kt`, `ui/reservar/Ubicacion.ios.kt`
- `iosApp/`: proyecto Xcode (SwiftUI `iOSApp.swift` + `ContentView.swift` que monta el
  UIViewController de Compose), `Info.plist` con permisos cámara/fotos/ubicación ya
  puestos (para Fase 2), `Assets.xcassets`, `Configuration/Config.xcconfig`.
- `gradle.properties`: `kotlin.native.ignoreDisabledTargets=true`.
- `docs/ios-setup.md`: guía de puesta en marcha.

### 🎯 QUÉ DEBE REVISAR EL AGENTE EN LA MAC (en orden)

1. **Compilar el Kotlin de iOS** (caza errores en los `actual` que Windows no pudo ver):
   ```sh
   ./gradlew :composeApp:compileKotlinIosSimulatorArm64
   ```
   Si falla, casi seguro serán ajustes de **binding de Foundation/UIKit** en los
   archivos de `src/iosMain/` (nombres de API, tipos de `Map` en `openURL`,
   `useContents`/`usePinned` en cinterop, o el cast `String as NSString`). Arreglar
   ahí. El resto de la app (commonMain) ya compila en Android, así que los errores
   deberían venir SOLO de `iosMain`.

2. **Correr en el simulador** (para el simulador NO hace falta cuenta Apple Developer):
   - Abrir `iosApp/iosApp.xcodeproj` en Xcode.
   - Elegir un simulador (iPhone 15) y **▶ Run**. Xcode dispara Gradle
     (`embedAndSignAppleFrameworkForXcode`) automáticamente.
   - **Meta Fase 1:** ver la app abrir + intro de marca + **login de clínica
     (usuario+contraseña) funcionando** (Supabase Auth es multiplataforma).
   - Cuenta de prueba staff: `admin_test@admin.com` / `Admin-Test-2026` (ver más abajo).

3. Reportar al usuario qué compiló, qué corrió, y qué stubs faltan cablear (Fase 2).

### ⏭️ Fase 2 iOS (después de que la app abra)

Cablear los stubs nativos (cada uno tiene su `TODO` con la receta):
- **AuthGoogle** → SDK GoogleSignIn iOS (GIDSignIn) → idToken → Supabase. Necesita OAuth
  Client ID tipo iOS + REVERSED_CLIENT_ID como URL scheme + GIDClientID en Info.plist.
- **PushNativo** → APNs (UNUserNotificationCenter) o FCM iOS; capability Push + clave APNs.
- **MapaClinicas** → MapKit (MKMapView vía UIKitView interop).
- **CamaraFoto / SelectorArchivo / Ubicacion** → UIImagePickerController / PHPicker /
  CLLocationManager.

### ⏭️ Fase 3 iOS

Cuenta **Apple Developer** (99 USD/año) para iPhone físico + publicar en App Store
(firma, certificados, TestFlight).

---

## Dev en vivo (backend local)

- La app **debug** apunta a `http://10.0.2.2:3000` (Android) / `http://localhost:3000`
  (iOS simulador) = Next.js local del repo web (`npm run dev`) + Supabase **cloud**.
- La app **release** apunta a `https://www.saniape.com`.

## Cuentas de prueba

- Staff/clínica: `admin_test@admin.com` / `Admin-Test-2026` (Admin de "Clinica Test").
- Profesional: `demo.fisio@dalu.test` / `Demo-Fisio-2026`.
- (NO tocar datos de la clínica **DALU** en prod: es un cliente real.)

## Firma Android (Play Store) — NO commitear

`keystore.properties`, `*.jks`, `pepk.jar`, `upload_certificate.pem` están en
`.gitignore`. Las claves de firma viven fuera del repo (backup del usuario).
