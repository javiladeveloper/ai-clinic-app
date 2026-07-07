# Sania iOS — puesta en marcha (Fase 1)

Objetivo de la Fase 1: **ver la app abrir en el simulador de iPhone**. Login de
clínica (usuario+contraseña), navegación, agenda y pacientes ya funcionan porque
esa lógica vive en `commonMain` (compartida con Android). Lo nativo pesado queda
como *stub* (ver "Pendiente Fase 2" abajo).

## Requisitos en la Mac

1. **Xcode** (App Store) + herramientas de línea de comandos:
   ```sh
   xcode-select --install
   sudo xcodebuild -license accept
   ```
2. **JDK 17** (el mismo que Android). Si usas Android Studio en la Mac, ya lo tienes.
3. Clonar/actualizar este repo en la Mac (branch `master`).

> No hace falta cuenta de Apple Developer para correr en el **simulador**. Solo se
> necesita (99 USD/año) para instalar en un **iPhone físico** y para publicar en la
> App Store — eso es Fase 3.

## Correr en el simulador

### Opción A — desde Xcode (recomendada para ver la app)
1. Abrir `iosApp/iosApp.xcodeproj` en Xcode.
2. Elegir un simulador (p. ej. *iPhone 15*) en el selector de arriba.
3. **▶ Run**. La primera vez tarda: Xcode dispara `./gradlew
   :composeApp:embedAndSignAppleFrameworkForXcode`, que compila el framework Kotlin
   y lo enlaza. Al terminar, la app abre en el simulador.

### Opción B — solo verificar que el Kotlin compila (sin Xcode)
```sh
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```
Esto **solo funciona en la Mac** (en Windows sale `SKIPPED`: el toolchain de
Kotlin/Native para Apple no existe en Windows). Es la forma rápida de cazar errores
en los `actual` de iOS sin abrir Xcode.

## Qué se agregó (estructura)

```
composeApp/
  build.gradle.kts            # + targets iosX64/iosArm64/iosSimulatorArm64 + framework "ComposeApp"
  src/iosMain/kotlin/...       # los `actual` de iOS (13 declaraciones expect cubiertas)
    MainViewController.kt      # punto de entrada: ComposeUIViewController { App() }
iosApp/
  iosApp.xcodeproj/            # proyecto Xcode
  iosApp/
    iOSApp.swift               # @main SwiftUI App
    ContentView.swift          # UIViewControllerRepresentable → MainViewController()
    Info.plist                 # permisos cámara/fotos/ubicación (para Fase 2)
    Assets.xcassets            # AppIcon + AccentColor
  Configuration/Config.xcconfig # BUNDLE_ID, APP_NAME, TEAM_ID (dejar TEAM_ID vacío para simulador)
```

## Config del proyecto Xcode a completar en la Mac

- **TEAM_ID**: en `iosApp/Configuration/Config.xcconfig`, para el simulador se deja
  vacío. Para iPhone físico / App Store, poner el Team ID de tu cuenta Apple Developer.
- **Firma**: Xcode → target *iosApp* → *Signing & Capabilities* → *Automatically
  manage signing* + tu equipo. (Solo necesario para dispositivo/publicación.)
- **AppIcon**: `Assets.xcassets/AppIcon.appiconset` está vacío (placeholder 1024×1024).
  Añadir el ícono de Sania antes de publicar.

## Pendiente Fase 2 (nativo de iOS)

Estos `actual` están como *stub* funcional (compilan y no rompen la app), marcados con
`TODO(iOS Fase 2)` en el código:

| Módulo | Stub actual | Qué falta |
|---|---|---|
| `AuthGoogle.ios.kt` | muestra "Google en iOS pronto" | SDK GoogleSignIn (GIDSignIn) → idToken → Supabase; OAuth Client ID tipo iOS + REVERSED_CLIENT_ID como URL scheme + GIDClientID en Info.plist |
| `PushNativo.ios.kt` | no-op | APNs (UNUserNotificationCenter) o FCM iOS; capability Push + clave APNs en Apple Developer; registrar token en `dispositivos_push` |
| `MapaClinicas.ios.kt` | aviso + lista debajo | MapKit (MKMapView vía UIKitView) con pines y enfoque |
| `CamaraFoto.ios.kt` | no-op | UIImagePickerController (cámara) |
| `SelectorArchivo.ios.kt` | no-op | UIDocumentPicker / PHPicker |
| `Ubicacion.ios.kt` | devuelve null | CLLocationManager |

`ComprimirImagen.ios.kt` **sí** está implementado de verdad (UIImage + JPEG).

El **staff (clínicas) puede loguearse en iOS desde ya** con usuario+contraseña
(Supabase Auth es multiplataforma). Solo el botón de Google del paciente está en stub.
