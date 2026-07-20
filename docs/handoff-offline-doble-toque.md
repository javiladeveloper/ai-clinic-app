# Handoff — App: evitar el doble registro al tocar dos veces (cola offline)

**Fecha:** 2026-07-19
**Repo:** `ai-clinic-app` (branch `master`) · afecta a la versión **2.6.0 / versionCode 15**
**Estado:** ✅ **RESUELTO** el 2026-07-20. Se añadió el bloqueo por clave lógica en
`EnviarOEncolar.kt` (set `enVuelo`): si la misma gestión ya está en curso, el segundo toque
no se manda y se avisa *"Esta operación se está guardando — espera unos segundos, no la
repitas"*. Este documento queda como registro de por qué la idempotencia no bastaba.

## El problema en una frase

Sin señal, el usuario toca "Completar sesión", la app **encola y libera el botón de
inmediato**; como no ve que se guardó, **vuelve a tocar** → se encolan **dos operaciones
distintas** y al volver la conexión se crean **dos sesiones** (o dos pagos).

## Por qué la idempotencia NO lo cubre

Es el punto que hay que entender antes de tocar nada.

La idempotencia del servidor (Fase 1, ya en producción) evita duplicados cuando **la misma
operación se reenvía**: comparte `idempotency_key`, el servidor la reconoce y devuelve el
resultado anterior sin volver a ejecutar.

Pero aquí no es el mismo envío repetido: son **dos acciones distintas del usuario**. Y en
`composeApp/src/commonMain/kotlin/pe/saniape/app/data/offline/EnviarOEncolar.kt:28`:

```kotlin
val idemKey = nuevaIdemKey()   // ← clave NUEVA en cada llamada
```

Cada toque genera su propia clave, así que para el servidor son **dos operaciones legítimas
y diferentes**. Las guarda las dos. La idempotencia funciona perfecto; simplemente no es el
mecanismo que resuelve este caso.

## Dónde está la causa

`composeApp/src/commonMain/kotlin/pe/saniape/app/data/staff/PacientesRepo.kt:281`

```kotlin
private suspend fun postStaff(path: String, cuerpo: JsonObject): Boolean {
    val tipo = path.removePrefix("/api/staff/").replace('/', ':')
    return enviarOEncolar(tipo, path, cuerpo)   // sin señal: encola y devuelve true YA
}
```

`enviarOEncolar` devuelve `true` apenas encola (`EnviarOEncolar.kt`), así que la pantalla
apaga su `guardando` y el botón vuelve a estar disponible aunque la operación **todavía no
llegó al servidor**.

Con señal no pasa: el envío es inline y `true` significa "el servidor ya lo guardó".

## Qué se espera (mismo comportamiento que tendrá la web)

| Situación | Qué debe ver el usuario |
|---|---|
| Guardando | Botón bloqueado: "Guardando…" |
| Sin conexión | "Sin conexión — se guardará al volver la señal" (ya existe) |
| **Intenta repetir la misma acción** | **Bloqueado** + "Esta operación se está guardando, no la repitas" |
| Sincronizado | "✓ Guardado" y se libera |

## Enfoque sugerido

Dos capas; la primera es la importante.

### 1. Bloquear por operación en curso (evita el problema en la UI)

Un registro de "operaciones en vuelo" por **clave lógica** (no por clave de idempotencia).
La clave lógica identifica *qué* se está haciendo, no *qué envío* es: por ejemplo
`sesion:estado:<sesionId>` o `pago:registrar:<tratamientoId>`.

- Antes de encolar/enviar, si ya hay una operación en vuelo con esa clave lógica →
  **no encolar** y avisar *"Esta operación se está guardando, no la repitas"*.
- La clave se libera cuando la operación **sincroniza de verdad** (queda `hecha`) o falla
  definitivamente — **no** al encolar.
- Sitio natural: `EnviarOEncolar.kt` (por donde pasan todas las escrituras) + consultar la
  cola (`ColaRepo`) para saber si hay una pendiente con esa clave lógica. Ojo: hoy
  `cola_operaciones` no guarda clave lógica; habría que añadir la columna o derivarla del
  `tipo` + un id del payload.

### 2. Deduplicar en la cola (red de seguridad)

Al encolar, si ya existe una operación **pendiente** con la misma clave lógica, no crear
otra (o reemplazar el payload de la existente, que suele ser lo deseado: el último estado
gana). Así, aunque la UI falle en bloquear, no se acumulan duplicados.

## Cómo verificar que quedó bien

En el emulador, **modo avión**:
1. Completar una sesión → aviso "se guardará al volver la señal", chip 🕐 1.
2. Volver a tocar "Completar" en la misma sesión → **no debe encolarse otra**; debe salir el
   aviso de "se está guardando".
3. Quitar el modo avión → sincroniza y en la web debe aparecer **UNA** sesión.
4. Repetir con **registrar pago** (aquí un duplicado es doble ingreso en el kardex).

## Contexto útil

- La cola vive en `composeApp/src/commonMain/kotlin/pe/saniape/app/data/offline/`
  (`ColaRepo`, `Sincronizador`, `EnviarOEncolar`, `EstadoSync`, `Traductor`).
- El esquema está en `composeApp/src/commonMain/sqldelight/pe/saniape/app/db/Cola.sq`.
- Tests de la lógica pura: `composeApp/src/commonTest/.../TraductorTest.kt` y `OrdenColaTest.kt`
  (10 en total). Correr con `./gradlew :composeApp:testDebugUnitTest`.
- Compilar: `./gradlew :composeApp:compileDebugKotlinAndroid`.
- **La 2.6.0 aún no se ha probado en un dispositivo.** Conviene probarla antes o a la vez,
  por si aparecen otros ajustes y se hace un solo release.

## Equivalente en la web

El mismo comportamiento se está implementando en `ai-clinic-dashboard` (Fase 3): un helper
con reintento automático que **mantiene el botón bloqueado durante todo el reintento** y
rechaza el segundo intento con el mismo aviso. Conviene que los textos sean idénticos en
ambas plataformas.
