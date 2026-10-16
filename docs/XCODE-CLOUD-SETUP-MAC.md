# Xcode Cloud para Sania — configuración inicial en la Mac

**Para:** el agente que trabaja en la Mac de Jonathan.
**Fecha:** 2026-10-16.
**Objetivo:** dejar creado el workflow de Xcode Cloud de Sania. La primera
configuración solo se puede hacer desde Xcode en una Mac; después todo se maneja
desde la web de App Store Connect.

## Por qué

- Los builds de iOS hoy corren en GitHub Actions (`.github/workflows/ios-release.yml`).
  Ahí cada minuto de macOS cuenta x10: un build de ~20 min gasta 200 de los 3.000
  minutos del plan, y en septiembre se agotaron.
- Xcode Cloud trae **25 horas al mes incluidas** con la membresía de Apple.
- En julio se intentó y falló ("Sania — Build 8 failed", `PhaseScriptExecution`):
  la fase "Compile Kotlin Framework" llama a `./gradlew` y la máquina de Xcode Cloud
  no trae Java. **Ya está arreglado en el repo** (ver abajo); falta solo crear el
  workflow.

## Lo que ya está hecho en el repo (no rehacer)

| Archivo | Qué hace |
|---|---|
| `iosApp/ci_scripts/ci_post_clone.sh` | Instala JDK 17 y lo fija en `~/.gradle/gradle.properties`; cambia la firma de Release a **automática solo en el clon de Xcode Cloud**; copia la versión de Android (`versionName`) al `Info.plist` y usa `CI_BUILD_NUMBER + 1000` como build. |
| `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` | Esquema `iosApp` compartido (Xcode Cloud solo ve esquemas compartidos). Archive = Release. |
| `iosApp/iosApp/Info.plist` | Versión 2.59.0 (igual que Android). |
| `.gitattributes` | `*.sh` siempre con LF. |

## Reglas

- **NO cambies la firma en `project.pbxproj`.** Release firma a mano (perfil
  "Sania App Store") porque GitHub Actions instala ese certificado. Xcode Cloud la
  pasa a automática en su propia copia con `ci_post_clone.sh`.
- **NO dejes "Branch Changes" como condición de inicio.** Compilaría en cada push y
  se comería las 25 horas.
- **NO subas** `DerivedData`, `build/`, `.gradle/` ni nada de `xcuserdata/`.
- Los pasos con la **cuenta de Apple** y el **permiso de GitHub** los hace Jonathan
  en pantalla. Guíalo; no le pidas contraseñas ni códigos.
- Responde en español.

## Pasos

### 0. Requisitos (verifica)
```sh
xcodebuild -version              # debe ser Xcode 26.x
java -version || echo "sin Java (solo hace falta para el paso 2, opcional)"
```
Jonathan debe estar logueado en **Xcode → Settings → Accounts** con su Apple ID,
Team **KGZQMM72VX**.

### 1. Clonar y verificar
```sh
git clone https://github.com/javiladeveloper/ai-clinic-app.git
cd ai-clinic-app
xcodebuild -list -project iosApp/iosApp.xcodeproj   # debe listar el esquema "iosApp"
ls -l iosApp/ci_scripts/ci_post_clone.sh             # debe ser ejecutable (-rwxr-xr-x)
```
Si `ci_post_clone.sh` no es ejecutable: `chmod +x` y commitea **solo** ese cambio.

### 2. (Opcional pero útil) Compilar el Kotlin de iOS en la Mac
iOS no se compila desde el 1-ago (versión 2.9.10) y Android ya va en 2.59.0. Si hay
errores en `composeApp/src/iosMain`, salen acá en minutos en vez de esperar 20 en la nube:
```sh
./gradlew :composeApp:compileKotlinIosArm64
```
Si falla, los errores casi seguro están en `composeApp/src/iosMain/` (bindings de
UIKit/Foundation) o en algo de `commonMain` que solo existe en Android. Arréglalos,
corre de nuevo y commitea. **Avísale a Jonathan qué arreglaste.**

### 3. Abrir el proyecto en Xcode
```sh
open iosApp/iosApp.xcodeproj
```
No hace falta compilar en Xcode. Si se queda "Indexing", déjalo.

### 4. Crear el workflow (lo hace Jonathan, tú lo guías)
1. Menú **Integrate → Create Workflow…** (o **Product → Xcode Cloud → Create Workflow**).
2. Producto: **iosApp** → app **Sania** (`pe.saniape.app`), que ya existe en App Store Connect.
3. Cuando pida acceso al repositorio: **Grant Access** → se abre App Store Connect →
   conectar **GitHub** → instalar la app "Xcode Cloud" **solo en el repo
   `javiladeveloper/ai-clinic-app`**.
4. De vuelta en Xcode, editar el workflow **antes de guardar**:
   - **Name:** `TestFlight`
   - **Environment:** Xcode **26** (Latest Release) · macOS Latest
   - **Start Conditions:** borrar **Branch Changes** → agregar **Manual Start**
   - **Actions → Archive – iOS:** Deployment Preparation = **TestFlight (Internal Testing Only)**
   - **Post-Actions → TestFlight Internal Testing:** agregar el grupo de testers
5. **Save.**

### 5. Primer build
En Xcode: **Report Navigator (⌘9) → Cloud → TestFlight → Start Build** sobre `master`.
El primer build tarda (descarga Kotlin/Native desde cero, sin caché).

Revisa en el log de Xcode Cloud que el paso `ci_post_clone.sh` imprima:
```
JDK para Gradle: ...
Firma: automática (solo en Xcode Cloud)
Versión 2.59.0, build 10xx
```

## Si algo falla

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| `PhaseScriptExecution failed` en "Compile Kotlin Framework" | Java no encontrado o error de Kotlin | Mira el log de `ci_post_clone.sh`; si el JDK se instaló, el error es de compilación: corre el paso 2 en la Mac |
| Error de firma / "No profiles for 'pe.saniape.app'" | La firma no pasó a automática | Verifica que el log diga "Firma: automática"; revisa los `sed` del script contra `project.pbxproj` |
| "Scheme iosApp not found" | Esquema no compartido | Confirma `xcodebuild -list`; el archivo está en `xcshareddata/xcschemes/` |
| `OutOfMemoryError: Java heap space` en "Compile Kotlin Framework" | El enlace del framework release necesita más de 4 GB (pasó en GitHub Actions el 16-oct con la 2.59.0) | El script ya fija `-Xmx6g`; si vuelve a pasar, revisa que el log muestre esa línea en `~/.gradle/gradle.properties` |
| Build number repetido en TestFlight | Choca con uno de GitHub Actions | Sube el `+ 1000` del script |

## Al terminar, reporta a Jonathan
1. Si el Kotlin de iOS compiló en la Mac (paso 2) y qué se arregló.
2. Si el workflow quedó creado con **Manual Start** y TestFlight interno.
3. El resultado del primer build en Xcode Cloud (o el error exacto).

Después de esto, los builds se lanzan desde la web (App Store Connect → Sania →
Xcode Cloud), sin Mac.
