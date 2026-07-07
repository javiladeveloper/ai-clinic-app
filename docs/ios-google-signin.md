# iOS — Login con Google (Fase 2)

Cablear el botón "Continuar con Google" del **portal del paciente** en iOS. El staff
(clínicas) usa email+contraseña y **ya funciona** — esto es solo para pacientes.

## Cómo funciona (arquitectura del puente)

El SDK **GoogleSignIn** es dependencia del proyecto **Xcode** (Swift), no de Kotlin. El
flujo nativo lo corre Swift; Kotlin solo recibe el `idToken` y lo intercambia con Supabase
(igual que Android). El puente es el `object` Kotlin:

```
composeApp/.../auth/AuthGoogle.ios.kt  →  object GoogleSignInPuente { var proveedorIdToken }
```

Desde Swift se ve como `GoogleSignInPuente.shared.proveedorIdToken`. Swift lo setea con el
flujo GIDSignIn; mientras no esté seteado, el botón muestra "disponible pronto".

El `idToken` debe tener **audience = Web Client ID** (el mismo que valida Supabase y que ya
usa Android). Eso se logra con `GIDServerClientID` en el Info.plist.

- **Web Client ID (Supabase / serverClientID):**
  `942581341329-7qfpdj6fsol2bkkhj2movknju4tets1q.apps.googleusercontent.com`
  (el mismo de `AuthGoogle.android.kt`; NO se toca).

---

## Paso 1 — Crear el OAuth Client ID **tipo iOS** (Google Cloud)  ← lo haces tú

1. [Google Cloud Console](https://console.cloud.google.com/) → **APIs & Services → Credentials**
   (mismo proyecto donde vive el Web Client ID de arriba).
2. **+ Create Credentials → OAuth client ID**.
3. **Application type: iOS**.
4. **Bundle ID:** `pe.saniape.app`
5. Crear. Copia dos cosas de la pantalla:
   - **iOS Client ID:** `NNN-xxxx.apps.googleusercontent.com`
   - **REVERSED_CLIENT_ID (iOS URL scheme):** `com.googleusercontent.apps.NNN-xxxx`
     (es el Client ID al revés; Google lo muestra ahí mismo).

> Pásame esos dos valores y relleno el Info.plist por ti (Paso 3).

---

## Paso 2 — Añadir el SDK GoogleSignIn en Xcode  ← lo haces tú (UI de Xcode)

1. Xcode → **File → Add Package Dependencies…**
2. URL: `https://github.com/google/GoogleSignIn-iOS`
3. **Dependency Rule:** Up to Next Major (7.x). **Add Package**.
4. Añade el producto **GoogleSignIn** al target **iosApp**.

---

## Paso 3 — Info.plist  ← lo relleno yo con tus valores del Paso 1

Se añaden 3 cosas al `iosApp/iosApp/Info.plist`:

```xml
<key>GIDClientID</key>
<string>TU_IOS_CLIENT_ID.apps.googleusercontent.com</string>

<key>GIDServerClientID</key>
<string>942581341329-7qfpdj6fsol2bkkhj2movknju4tets1q.apps.googleusercontent.com</string>

<key>CFBundleURLTypes</key>
<array>
  <dict>
    <key>CFBundleURLSchemes</key>
    <array>
      <string>com.googleusercontent.apps.TU_REVERSED_CLIENT_ID</string>
    </array>
  </dict>
</array>
```

---

## Paso 4 — Código Swift  ← lo dejo listo yo (se activa al añadir el SDK)

**`iosApp/iosApp/GoogleSignInBridge.swift`** (archivo nuevo):

```swift
import UIKit
import GoogleSignIn
import ComposeApp

/// Conecta el flujo nativo GoogleSignIn con el puente Kotlin (GoogleSignInPuente).
enum GoogleSignInBridge {
    static func install() {
        GoogleSignInPuente.shared.proveedorIdToken = { callback in
            guard let root = topViewController() else {
                callback(nil, "No se pudo presentar el login de Google.")
                return
            }
            GIDSignIn.sharedInstance.signIn(withPresenting: root) { result, error in
                if let error = error {
                    callback(nil, error.localizedDescription)
                    return
                }
                guard let idToken = result?.user.idToken?.tokenString else {
                    callback(nil, "No se obtuvo el idToken de Google.")
                    return
                }
                callback(idToken, nil)   // Kotlin lo intercambia con Supabase
            }
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene
        var vc = scene?.keyWindow?.rootViewController
        while let presented = vc?.presentedViewController { vc = presented }
        return vc
    }
}
```

**`iosApp/iosApp/iOSApp.swift`** (modificar):

```swift
import SwiftUI
import GoogleSignIn

@main
struct iOSApp: App {
    init() {
        GoogleSignInBridge.install()          // ← setea el puente al arrancar
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
                .ignoresSafeArea(.keyboard)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)   // ← callback OAuth
                }
        }
    }
}
```

> `GIDSignIn` lee `GIDClientID` y `GIDServerClientID` del Info.plist automáticamente, así que
> no hace falta `GIDConfiguration` en código.

---

## Paso 5 — Probar

▶ Run → portal de paciente → **Continuar con Google** → hoja nativa de Google → al aceptar,
Supabase crea/inicia la sesión y la app entra. Verifica en Supabase (Auth > Users) que
el usuario aparece con provider Google.

## Checklist

- [ ] iOS Client ID creado (Paso 1)
- [ ] SDK GoogleSignIn añadido (Paso 2)
- [ ] Info.plist con GIDClientID + GIDServerClientID + URL scheme (Paso 3)
- [ ] GoogleSignInBridge.swift + iOSApp.swift (Paso 4)
- [ ] Login probado (Paso 5)
