# Compilar Sania para iPhone en la Mac (6-nov-2026)

**Para:** el agente de Claude Code que corre en la Mac de Jonathan (16 GB de RAM).
**Objetivo:** comprobar que la app compila para iPhone **en local**, porque en GitHub
Actions ya no alcanza la memoria. Por ahora no se sube nada a Apple.

---

## Por qué estás aquí

- En el CI (`.github/workflows/ios-release.yml`), la tarea
  `:composeApp:linkReleaseFrameworkIosArm64` muere con
  **`OutOfMemoryError: GC overhead limit exceeded`**.
  - Pasa en el runner `macos-15-intel` (14 GB) con `-Xmx10g`. Run 42308477768, 29 min.
  - En el runner M1 de 7 GB con `-Xmx6g` se arrastró más de 75 min sin terminar.
- **La causa es el tamaño.** El código compartido (`composeApp/src/commonMain`) pasó de
  **112 archivos / 0.95 MB** (1-ago, tag `ios-v2.9.10`, enlazaba con 4 GB) a
  **508 archivos / 7.8 MB** hoy. El enlace release optimizado de Kotlin/Native procesa
  toda la app de una vez, y la memoria que pide crece más rápido que el código.
- **Versiones:** Kotlin **2.1.0**, Compose Multiplatform **1.7.3**, AGP 8.13.2,
  Gradle 8.13.
- **La cuenta de Apple Developer** está en migración de individual a organización
  (Norac Labs). Mientras dure, **los beneficios están pausados**. **No intentes subir a
  TestFlight ni configurar Xcode Cloud**: va a fallar y no es un problema del código.

## Experimentos en curso (ramas en GitHub)

| Rama | Qué cambia | Estado al escribir esto |
|---|---|---|
| `master` | Nada (base) | OOM en el CI |
| `exp/ios-sin-optimizar` | `optimized = false` en el framework **release de iOS** (`composeApp/build.gradle.kts`). Menos memoria al enlazar, algo menos de rendimiento en el iPhone. Android no cambia. | Corriendo en el CI (run 42314677979) |
| `exp/ios-kotlin-nueva` | Kotlin 2.1.0 → **2.4.21** (`gradle/libs.versions.toml`); Compose MP sigue en 1.7.3 | Android compila OK en Windows. iOS corriendo en el CI (run 42315227589) |

## Reglas

- **NO hagas push a `master`.** Si cambias algo, trabaja en una rama propia
  (`mac/ios-...`), y usa `git add` solo con tus archivos.
- No cambies el comportamiento de Android: está en producción.
- Respeta `CLAUDE.md`: Jonathan corre la app. Tú compilas para **verificar**. Abre el
  simulador solo si él te lo pide.

---

## Paso 0 · Herramientas

```sh
echo "RAM: $(( $(sysctl -n hw.memsize) / 1073741824 )) GB"; uname -m; sw_vers -productVersion
xcodebuild -version        # debe ser Xcode 26.x (Apple rechaza binarios con SDK < iOS 26)
java -version              # JDK 17
```

Si falta algo, instálalo según la sección **"PRIMERA VEZ EN LA MAC"** de `CLAUDE.md`
(Xcode desde el App Store, Homebrew y `openjdk@17`). Xcode 26 necesita macOS 15.6 o
superior.

## Paso 1 · Memoria para Gradle

```sh
mkdir -p ~/.gradle
echo "org.gradle.jvmargs=-Xmx12g -XX:+UseParallelGC -Dfile.encoding=UTF-8" >> ~/.gradle/gradle.properties
```

El `gradle.properties` de `~/.gradle` le gana al del proyecto (`-Xmx4g`). Antes de
compilar, cierra Chrome, Slack y cualquier otra app pesada: son 16 GB en total.

## Paso 2 · Compilar `master`

```sh
git checkout master && git pull
./gradlew :composeApp:linkReleaseFrameworkIosArm64 --console=plain
```

La primera vez descarga el toolchain de Kotlin/Native (varios cientos de MB). Mientras
corre, mira el proceso `java` en el **Monitor de Actividad**, pestaña Memoria, y anota
el pico. Anota también cuánto tardó y cómo terminó (`BUILD SUCCESSFUL` o el error exacto).

## Paso 3 · Si da OOM, probar los experimentos

```sh
git checkout exp/ios-sin-optimizar && git pull
./gradlew :composeApp:linkReleaseFrameworkIosArm64 --console=plain
```

Luego prueba `exp/ios-kotlin-nueva` (Kotlin 2.4.21) de la misma forma. Si falla por algo
que no sea memoria, anota el error exacto y no lo arregles a ciegas.

## Paso 4 · Simulador (solo si Jonathan lo pide)

Para el simulador se compila el framework **debug** (`linkDebugFrameworkIosSimulatorArm64`),
que pesa mucho menos que el release. Lo más probable es que funcione aunque el release dé
OOM.

1. `open iosApp/iosApp.xcodeproj`
2. Elige el esquema **iosApp** y un iPhone del simulador, y dale **Run**. El script
   "Compile Kotlin Framework" llama a Gradle solo.
3. Revisa que la app abra, que el login con correo funcione y que **"Continuar con
   Google"** termine con la sesión iniciada. Este último se arregló en el commit
   `975d3cd` y nunca se probó en un iPhone.

## Paso 5 · Reportar

Escribe el resultado en **`docs/IOS-RESULTADO-MAC.md`** en una rama **`mac/ios-resultado`**
y haz push de esa rama. El agente de Windows lo va a leer desde GitHub. Usa este formato:

```markdown
# Resultado en la Mac — <fecha>
- Mac: <chip>, <RAM> GB, macOS <versión>, Xcode <versión>, JDK <versión>
- master: <OK / OOM / otro error> · <minutos> · pico de memoria de java: <GB>
- exp/ios-sin-optimizar: <...>
- exp/ios-kotlin-nueva: <... o "no probado">
- Simulador: <abre / no abre> · login correo <ok/falla> · login Google <ok/falla>
- Errores (texto exacto):
```

Al terminar, dile a Jonathan que el resultado está en esa rama.
