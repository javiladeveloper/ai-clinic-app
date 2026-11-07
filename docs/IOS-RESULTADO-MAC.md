# Resultado en la Mac — 6-nov-2026

- **Mac:** Apple Silicon (arm64), **16 GB**, macOS 26.5.2, Xcode 26.6, JDK 17.0.19 (Temurin).
- Memoria Gradle usada en las pruebas: `-Xmx12g` en `~/.gradle/gradle.properties` (gana al `-Xmx4g` del proyecto).
- Con 16 GB **hay que cerrar Xcode** durante el link: abierto, el OS mató el compilador por presión de memoria (dos veces). Solo VS Code + el compilador.

## Resultados por rama (tarea `:composeApp:linkReleaseFrameworkIosArm64`)

| Rama | Kotlin | optimized | Resultado | Tiempo | Pico heap java |
|---|---|---|---|---|---|
| `master` | 2.1.0 | true | ❌ **OOM** (pide >10g; con 10g hace GC-thrashing) | — | no cabe en 16 GB |
| `exp/ios-sin-optimizar` | 2.1.0 | **false** | ❌ **AssertionError del compilador** | 6 min | 5 GB |
| `exp/ios-kotlin-nueva` | **2.4.21** | true | ❌ Inviable: entra en memoria pero el link monohilo LLVM **se arrastra >75 min** sin terminar (igual que el runner M1) | +75 min | ~5 GB |
| **`mac/ios-hibrido`** | **2.4.21** | **false** | ✅ **BUILD SUCCESSFUL** · framework `ComposeApp` 300 MB generado | 25 min* | **7.5 GB** |

\* 25 min = recompiló TODO desde cero (cambio de versión de Kotlin) + la Mac estaba tensa de memoria. Con caché y en un runner con más RAM/núcleos será bastante menos.

### Detalle del error de `exp/ios-sin-optimizar` (texto exacto)
```
e: Compilation failed: kfun:pe.saniape.app.ui.clinica.atencion#c$default(kotlin.String;kotlin.String;kotlin.String?;kotlin.Int){}pe.saniape.app.data.staff.FraseFrecuenteApp
e: java.lang.AssertionError: kfun:pe.saniape.app.ui.clinica.atencion#c$default(...)FraseFrecuenteApp
```
Es un **bug del compilador** Kotlin/Native 2.1.0 en la ruta sin-optimizar (no es el código ni es memoria). **Kotlin 2.4.21 ya NO lo tiene.**

## ✅ Camino que funciona (recomendado)

**Kotlin 2.4.21 + `optimized = false` para el framework release de iOS.** Está listo en la rama **`mac/ios-hibrido`** (pusheada). Junta lo bueno:
- **Sin OOM** (pico 7.5 GB, entra en 16 GB).
- **Sin el AssertionError** (compilador nuevo).
- Evita el link optimizado de >75 min.

### Enfoque híbrido (lo aplicado en `mac/ios-hibrido`)
En `composeApp/build.gradle.kts`, dentro de `binaries.framework { }`:
```kotlin
optimized = (project.findProperty("iosOptimized") == "true")
```
- **Por defecto `false`** → dev / TestFlight / CI: rápido y poca memoria. Diferencia de rendimiento en el iPhone, mínima.
- **Release final al App Store** → `./gradlew ... -PiosOptimized=true` (optimización completa; se acepta que tarde, se hace 1 vez por versión, idealmente en una máquina con mucha RAM).

### Qué falta decidir / validar
1. **Subir Kotlin a 2.4.21 en `master`** (hoy está en 2.1.0). En `exp/ios-kotlin-nueva` Android compiló OK; validar Android a fondo antes de mergear.
2. Compatibilidad **Compose Multiplatform 1.7.3 con Kotlin 2.4.21** (se dejó MP en 1.7.3). Compiló el framework; conviene correr la app y revisar que no haya warnings/incompatibilidades del compose-compiler.
3. **Simulador: NO probado** (Jonathan no lo pidió). El login Google (`975d3cd`) sigue sin probarse en iPhone.
4. Recordar: la cuenta Apple Developer está en migración a organización (Norac Labs) → **subir a TestFlight sigue en pausa**; esto es solo "compila / no compila".
