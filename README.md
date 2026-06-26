# Sania — App nativa (Android)

App nativa de **Sania** en **Kotlin Multiplatform (KMP) + Compose Multiplatform**.
Comparte la **misma base de datos Supabase** que la web (`saniape.com`).

Primer flujo: **Login con Google (nativo) + Portal del paciente (mis citas)**.

---

## Cómo abrir y compilar

1. Abre **Android Studio** (versión reciente: Ladybug/Koala o más nuevo).
2. `File > Open` → selecciona esta carpeta `ai-clinic-app`.
3. Espera el **Gradle sync** (la primera vez baja dependencias, tarda unos minutos).
4. Crea un emulador (`Device Manager > Create Device`, API 33+) o conecta tu teléfono
   con **depuración USB** activada.
5. Pulsa **Run ▶** (configuración `composeApp`).

> No necesitas instalar Gradle: el proyecto trae el **wrapper** (`gradlew`).

---

## ⚠️ Antes de que el login con Google funcione

Falta **un dato** que hay que pegar (es de Google Cloud, no se puede inventar):

### 1. Web Client ID de Google
Archivo: `composeApp/src/androidMain/kotlin/pe/saniape/app/auth/AuthGoogle.android.kt`
```kotlin
const val WEB_CLIENT_ID = "REEMPLAZAR_CON_WEB_CLIENT_ID.apps.googleusercontent.com"
```
- Es el **"Web client"** OAuth Client ID — **el mismo** que ya usa Supabase
  (Supabase Dashboard → Authentication → Providers → Google → *Client ID*).
- NO es el de tipo "Android".

### 2. Huella SHA-1 del certificado de depuración
Para que Google permita el login desde la app, hay que registrar la **SHA-1** de tu
keystore de debug en **Google Cloud Console** (un OAuth Client de tipo *Android* con
package `pe.saniape.app` + esa SHA-1).

Saca la SHA-1 con:
```bash
./gradlew signingReport
```
Copia la línea `SHA1:` de la variante `debug` y pégala en Google Cloud → Credenciales.

> Sin estos dos pasos, el botón "Continuar con Google" abre el selector pero el login
> falla. Todo lo demás (UI, lectura de citas) ya está listo.

---

## Estructura

```
composeApp/
├── build.gradle.kts                 # Módulo KMP (target Android)
└── src/
    ├── commonMain/kotlin/pe/saniape/app/
    │   ├── App.kt                   # Raíz: decide Login vs Portal según sesión
    │   ├── auth/AuthGoogle.kt       # expect: lanzador Google (común)
    │   ├── data/
    │   │   ├── Supabase.kt          # Cliente Supabase (MISMA base que la web)
    │   │   ├── Modelos.kt           # Cita, PacienteCuenta
    │   │   └── CitasRepo.kt         # Lee citas del paciente
    │   └── ui/
    │       ├── Tema.kt              # Colores de marca Sania
    │       ├── PantallaLogin.kt     # Login Google
    │       └── PantallaPortal.kt    # Lista de citas
    └── androidMain/
        ├── AndroidManifest.xml
        ├── kotlin/pe/saniape/app/
        │   ├── MainActivity.kt
        │   ├── SaniaApplication.kt
        │   └── auth/AuthGoogle.android.kt   # actual: Credential Manager (Google nativo)
        └── res/                     # íconos, colores, tema
```

---

## Base de datos

- URL: `https://rigohpumndmlbufngced.supabase.co` (en `data/Supabase.kt`)
- La `anon key` es **pública** (igual que en la web): la seguridad la da **RLS** en Postgres.
- La app lee las tablas `citas` y `pacientes` filtrando por el paciente autenticado.

---

## Estado

- [x] Esqueleto KMP que compila
- [x] Cliente Supabase compartido
- [x] Login Google nativo (falta pegar Web Client ID + SHA-1)
- [x] Portal: lista de citas del paciente
- [ ] Reservar cita desde la app
- [ ] iOS (KMP lo permite a futuro; hoy solo Android)