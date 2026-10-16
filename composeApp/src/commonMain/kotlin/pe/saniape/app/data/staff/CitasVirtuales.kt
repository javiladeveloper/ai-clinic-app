package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

// ─────────────────────────────────────────────────────────────────────────────
// Citas virtuales (videollamada: Google Meet o enlace pegado) — reglas PURAS.
// GEMELO de lib/citas-virtuales.ts (web): mismos nombres, mismos casos de test
// (lib/__tests__/citas-virtuales.test.ts). Si cambia uno, cambia el otro.
//
// La app NO decide el plan ni habla con Google: el servidor (/api/staff/cita/*)
// valida de nuevo y crea / mueve / borra el Meet. Esto solo sirve para que el
// formulario y la agenda digan lo mismo que la web antes de llegar al servidor.
//
//  · La cita es presencial salvo `modalidad = 'virtual'` (null = presencial).
//  · `enlace_origen`: meet_auto (Sania crea el Meet; Premium+), manual (pegado)
//    o servicio (el enlace fijo del servicio).
//  · El enlace es SIEMPRE https y sin caracteres que rompan un HTML o un mensaje
//    (mismo criterio que el CHECK de la base).
// ─────────────────────────────────────────────────────────────────────────────

const val MODALIDAD_PRESENCIAL = "presencial"
const val MODALIDAD_VIRTUAL = "virtual"
val MODALIDADES_CITA = listOf(MODALIDAD_PRESENCIAL, MODALIDAD_VIRTUAL)

const val ORIGEN_MEET_AUTO = "meet_auto"
const val ORIGEN_MANUAL = "manual"
const val ORIGEN_SERVICIO = "servicio"

const val ENLACE_MODO_MEET = "meet_auto"
const val ENLACE_MODO_FIJO = "fijo"

/** Tope del enlace (igual que el CHECK de la base). */
const val ENLACE_MAX = 500
/** El botón "Unirse" aparece este rato antes de la hora de la cita… */
const val MINUTOS_ANTES_UNIRSE = 15
/** …y sigue disponible este rato después de la hora en que termina. */
const val MINUTOS_DESPUES_UNIRSE = 60

const val AVISO_MEET_PLAN =
    "Tu plan no crea el Google Meet solo: pega el enlace de la videollamada (Meet, Zoom…). El Meet automático es de Premium."

/**
 * Mismo patrón que el CHECK `citas_enlace_virtual_chk` y que la web. Los espacios
 * van listados (los mismos que `\s` de JavaScript, NBSP incluido): `\s` de Kotlin
 * es solo ASCII y `(?U)` no existe en todas las plataformas (iOS).
 */
private const val ESPACIOS_JS = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
private val RE_ENLACE = Regex("^https://[^$ESPACIOS_JS<>\"'\\\\]+$")
private val RE_ESQUEMA = Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE)
private val RE_DOMINIO_SIN_ESQUEMA = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+(/|$)", RegexOption.IGNORE_CASE)
private val RE_HTTP = Regex("^http://", RegexOption.IGNORE_CASE)
private val RE_HTTPS = Regex("^https://", RegexOption.IGNORE_CASE)
private val RE_AUTORIDAD = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]*)")

sealed interface ResultadoEnlace {
    data class Ok(val url: String) : ResultadoEnlace
    data class Error(val error: String) : ResultadoEnlace
}

/**
 * Valida (y normaliza) el enlace de una videollamada. Mínimo tecleo: pegado sin
 * "https://" (`meet.google.com/abc-defg-hij`) se le agrega. Rechaza http://,
 * javascript:, data: y cualquier otro esquema. Gemelo de `validarEnlaceVirtual`.
 */
fun validarEnlaceVirtual(valor: Any?): ResultadoEnlace {
    if (valor !is String) return ResultadoEnlace.Error("Pega el enlace de la videollamada")
    var t = valor.trim()
    if (t.isEmpty()) return ResultadoEnlace.Error("Pega el enlace de la videollamada")
    if (t.length > ENLACE_MAX) return ResultadoEnlace.Error("El enlace es muy largo (máximo $ENLACE_MAX caracteres)")
    // Sin esquema y con forma de dominio ("zoom.us/j/123") → https://.
    if (!RE_ESQUEMA.containsMatchIn(t) && RE_DOMINIO_SIN_ESQUEMA.containsMatchIn(t)) t = "https://$t"
    if (RE_HTTP.containsMatchIn(t)) return ResultadoEnlace.Error("El enlace tiene que empezar con https:// (conexión segura)")
    // El esquema se compara sin mayúsculas: "HTTPS://" también vale, pero se guarda en minúsculas.
    if (RE_HTTPS.containsMatchIn(t)) t = "https://${t.substring(8)}"
    if (!RE_ENLACE.matches(t)) {
        return ResultadoEnlace.Error("Ese enlace no es válido: tiene que empezar con https:// y no llevar espacios")
    }
    if (t.length > ENLACE_MAX) return ResultadoEnlace.Error("El enlace es muy largo (máximo $ENLACE_MAX caracteres)")
    // Lo que la web comprueba con `new URL`: dominio con punto y sin usuario:clave.
    val autoridad = RE_AUTORIDAD.find(t)?.groupValues?.get(1).orEmpty()
    if (autoridad.isEmpty() || '@' in autoridad) return ResultadoEnlace.Error("Ese enlace no es válido")
    val host = hostDeAutoridad(autoridad) ?: return ResultadoEnlace.Error("Ese enlace no es válido")
    if ('.' !in host) return ResultadoEnlace.Error("Ese enlace no es válido")
    return ResultadoEnlace.Ok(t)
}

/** "sala.clinica.pe:8443" → "sala.clinica.pe". null = puerto que no es número o host vacío. */
private fun hostDeAutoridad(autoridad: String): String? {
    val sinUsuario = autoridad.substringAfterLast('@')
    val i = sinUsuario.lastIndexOf(':')
    val host = if (i >= 0) {
        val puerto = sinUsuario.substring(i + 1)
        if (puerto.isNotEmpty() && !puerto.all { it.isDigit() }) return null
        sinUsuario.substring(0, i)
    } else sinUsuario
    return host.lowercase().takeIf { it.isNotEmpty() }
}

/**
 * El enlace listo para abrir, revalidado (un dato viejo o tocado a mano en la
 * base no se abre sin pasar por la misma regla). null = no es válido.
 */
fun enlaceSeguro(url: String?): String? = (validarEnlaceVirtual(url) as? ResultadoEnlace.Ok)?.url

/** El dominio de un enlace (en minúsculas), o null si no tiene forma de URL. */
fun hostDeEnlace(url: String?): String? {
    val autoridad = RE_AUTORIDAD.find(url.orEmpty().trim())?.groupValues?.get(1) ?: return null
    return hostDeAutoridad(autoridad)
}

/** ¿La cita es por videollamada? */
fun esCitaVirtual(modalidad: String?): Boolean = modalidad == MODALIDAD_VIRTUAL

/** Nombre del servicio de videollamada a partir del enlace (para el botón y el mensaje). */
fun proveedorDeEnlace(url: String?): String {
    val host = hostDeEnlace(url) ?: return "Videollamada"
    return when {
        host == "meet.google.com" -> "Google Meet"
        host == "zoom.us" || host.endsWith(".zoom.us") -> "Zoom"
        host.endsWith("teams.microsoft.com") || host.endsWith("teams.live.com") -> "Microsoft Teams"
        host == "meet.jit.si" -> "Jitsi"
        host.endsWith("whereby.com") -> "Whereby"
        else -> "Videollamada"
    }
}

/** Lo que va junto a "Unirse": el proveedor o, si no se conoce, el dominio. */
fun etiquetaEnlace(url: String?): String {
    val p = proveedorDeEnlace(url)
    if (p != "Videollamada") return p
    return hostDeEnlace(url)?.removePrefix("www.")?.takeIf { it.isNotEmpty() } ?: p
}

/** Lo que dice el servicio (procedimientos.modalidad / enlace_modo / enlace_fijo). */
data class ServicioModalidad(
    val modalidad: String? = null,
    val enlaceModo: String? = null,
    val enlaceFijo: String? = null,
) {
    val esVirtual: Boolean get() = modalidad == MODALIDAD_VIRTUAL
}

/** Modalidad con la que nace una cita de este servicio. */
fun modalidadDeServicio(s: ServicioModalidad?): String =
    if (s?.esVirtual == true) MODALIDAD_VIRTUAL else MODALIDAD_PRESENCIAL

/** Lo que se guarda en la cita. [pedirMeet] = hay que crear el Meet (servidor). */
data class DecisionEnlace(
    /** null = no se escribe (presencial de siempre, o que la base herede la del servicio). */
    val modalidad: String?,
    val enlaceVirtual: String?,
    val enlaceOrigen: String?,
    val pedirMeet: Boolean,
    /** Algo que conviene decirle a quien agenda (sin bloquear). */
    val aviso: String? = null,
)

sealed interface ResultadoDecision {
    data class Ok(val valor: DecisionEnlace) : ResultadoDecision
    data class Error(val error: String) : ResultadoDecision
}

/**
 * Decide modalidad, enlace y origen de una cita a partir de lo que pidió quien
 * agenda y del servicio. Nunca bloquea por el plan. Gemelo de `resolverEnlaceCita`.
 *
 * Prioridad (cita virtual): enlace pegado > Meet pedido explícito > enlace fijo
 * del servicio > Meet del servicio (o por defecto si el plan lo tiene) > sin enlace.
 * `meet = false` explícito = no crear Meet.
 */
fun resolverEnlaceCita(
    modalidad: String? = null,
    enlace: String? = null,
    meet: Boolean? = null,
    servicio: ServicioModalidad? = null,
    meetPermitido: Boolean,
): ResultadoDecision {
    if (!modalidad.isNullOrEmpty() && modalidad !in MODALIDADES_CITA) {
        return ResultadoDecision.Error("La modalidad no es válida (presencial o virtual)")
    }
    val pedida: String? = when (modalidad) {
        MODALIDAD_VIRTUAL, MODALIDAD_PRESENCIAL -> modalidad
        else -> if (servicio?.esVirtual == true) MODALIDAD_VIRTUAL else null
    }
    if (pedida != MODALIDAD_VIRTUAL) {
        // Presencial elegido a mano se guarda (para que no herede un servicio virtual).
        return ResultadoDecision.Ok(DecisionEnlace(pedida, null, null, false))
    }
    val enlaceTexto = enlace?.trim().orEmpty()
    val fijo = if (servicio?.enlaceModo == ENLACE_MODO_FIJO && !servicio.enlaceFijo.isNullOrEmpty()) {
        (validarEnlaceVirtual(servicio.enlaceFijo) as? ResultadoEnlace.Ok)?.url
    } else null

    if (enlaceTexto.isNotEmpty()) {
        return when (val v = validarEnlaceVirtual(enlaceTexto)) {
            is ResultadoEnlace.Error -> ResultadoDecision.Error(v.error)
            is ResultadoEnlace.Ok -> ResultadoDecision.Ok(
                DecisionEnlace(MODALIDAD_VIRTUAL, v.url, if (v.url == fijo) ORIGEN_SERVICIO else ORIGEN_MANUAL, false),
            )
        }
    }
    val conMeet = DecisionEnlace(MODALIDAD_VIRTUAL, null, ORIGEN_MEET_AUTO, true)
    fun sinEnlace(aviso: String? = null) = DecisionEnlace(MODALIDAD_VIRTUAL, null, null, false, aviso)

    if (meet == true) return ResultadoDecision.Ok(if (meetPermitido) conMeet else sinEnlace(AVISO_MEET_PLAN))
    if (fijo != null) return ResultadoDecision.Ok(DecisionEnlace(MODALIDAD_VIRTUAL, fijo, ORIGEN_SERVICIO, false))
    if (meet == false) return ResultadoDecision.Ok(sinEnlace())
    val servicioPideMeet = servicio?.enlaceModo == ENLACE_MODO_MEET
    if (meetPermitido) return ResultadoDecision.Ok(conMeet)
    return ResultadoDecision.Ok(sinEnlace(if (servicioPideMeet) AVISO_MEET_PLAN else null))
}

// ── Formulario de la cita ────────────────────────────────────────────────────

/** Virtual: crear el Google Meet o usar/pegar un enlace. */
enum class ModoEnlace { MEET, ENLACE }

/** Lo que elige quien agenda en el formulario ("¿Dónde se atiende?"). */
data class ValorModalidad(
    val modalidad: String = MODALIDAD_PRESENCIAL,
    val modo: ModoEnlace = ModoEnlace.ENLACE,
    val enlace: String = "",
    /** Lo tocó a mano: cambiar de servicio ya no lo pisa. */
    val tocado: Boolean = false,
) {
    val esVirtual: Boolean get() = modalidad == MODALIDAD_VIRTUAL
}

val VALOR_MODALIDAD_INICIAL = ValorModalidad()

/** Cómo arranca el selector según el servicio (y si el plan crea el Meet). */
fun valorModalidadDeServicio(s: ServicioModalidad?, meetPermitido: Boolean): ValorModalidad {
    if (s?.esVirtual != true) return VALOR_MODALIDAD_INICIAL
    if (s.enlaceModo == ENLACE_MODO_FIJO && !s.enlaceFijo.isNullOrEmpty()) {
        return ValorModalidad(MODALIDAD_VIRTUAL, ModoEnlace.ENLACE, s.enlaceFijo, false)
    }
    return ValorModalidad(MODALIDAD_VIRTUAL, if (meetPermitido) ModoEnlace.MEET else ModoEnlace.ENLACE, "", false)
}

/** Columnas de la cita a partir del selector (vacías = no se escribe nada). */
data class CamposModalidad(
    /** null = no se manda (la presencial de siempre, o la base hereda la del servicio). */
    val modalidad: String? = null,
    val enlaceVirtual: String? = null,
    val enlaceOrigen: String? = null,
    val pedirMeet: Boolean = false,
    val aviso: String? = null,
)

sealed interface ResultadoCampos {
    data class Ok(val campos: CamposModalidad) : ResultadoCampos
    data class Error(val error: String) : ResultadoCampos
}

/**
 * Columnas de la cita a partir del selector. Gemelo de `camposModalidadCita`.
 * Error solo si el enlace escrito no es válido (vacío = se pega después).
 */
fun camposModalidadCita(v: ValorModalidad, servicio: ServicioModalidad?, meetPermitido: Boolean): ResultadoCampos {
    if (!v.esVirtual) {
        // Servicio virtual atendido en el local: se dice explícito (si no, la base lo haría virtual).
        return ResultadoCampos.Ok(
            if (servicio?.esVirtual == true) CamposModalidad(modalidad = MODALIDAD_PRESENCIAL) else CamposModalidad(),
        )
    }
    val r = resolverEnlaceCita(
        modalidad = MODALIDAD_VIRTUAL,
        enlace = if (v.modo == ModoEnlace.ENLACE) v.enlace else null,
        meet = v.modo == ModoEnlace.MEET,
        servicio = servicio,
        meetPermitido = meetPermitido,
    )
    return when (r) {
        is ResultadoDecision.Error -> ResultadoCampos.Error(r.error)
        is ResultadoDecision.Ok -> ResultadoCampos.Ok(
            CamposModalidad(MODALIDAD_VIRTUAL, r.valor.enlaceVirtual, r.valor.enlaceOrigen, r.valor.pedirMeet, r.valor.aviso),
        )
    }
}

/**
 * Lo que la app le manda a `POST /api/staff/cita/crear` (`modalidad`,
 * `enlaceVirtual`, `meet`). El servidor vuelve a decidir con el plan.
 * null = no se manda nada: la presencial de siempre, o un servicio virtual con
 * su enlace fijo que nadie tocó (la base lo hereda con `enlace_origen =
 * servicio`, igual que en cualquier otro camino de crear una cita).
 */
data class VirtualParaCrear(val modalidad: String, val enlaceVirtual: String?, val meet: Boolean?)

fun virtualParaCrear(v: ValorModalidad, servicio: ServicioModalidad?, campos: CamposModalidad): VirtualParaCrear? {
    val modalidad = campos.modalidad ?: return null
    if (modalidad == MODALIDAD_PRESENCIAL) return VirtualParaCrear(MODALIDAD_PRESENCIAL, null, null)
    if (!v.tocado && campos.enlaceOrigen == ORIGEN_SERVICIO && servicio?.esVirtual == true) return null
    return VirtualParaCrear(
        modalidad = MODALIDAD_VIRTUAL,
        enlaceVirtual = campos.enlaceVirtual,
        meet = if (campos.enlaceVirtual != null) false else campos.pedirMeet,
    )
}

// ── Ventana del botón "Unirse" ───────────────────────────────────────────────

data class VentanaUnirse(val abierta: Boolean, val desdeMs: Long, val hastaMs: Long, val antes: Boolean, val despues: Boolean)

/** Desde 15 min antes de la hora de la cita hasta 60 min después de que termina (sin duración: 45). */
fun ventanaUnirse(inicioMs: Long, duracionMin: Int?, ahoraMs: Long): VentanaUnirse {
    val dur = duracionMin?.takeIf { it > 0 } ?: 45
    val desde = inicioMs - MINUTOS_ANTES_UNIRSE * 60_000L
    val hasta = inicioMs + (dur + MINUTOS_DESPUES_UNIRSE) * 60_000L
    val antes = ahoraMs < desde
    val despues = ahoraMs > hasta
    return VentanaUnirse(!antes && !despues, desde, hasta, antes, despues)
}

/** Qué ve el paciente en su cita virtual (portal / app del paciente). */
enum class EstadoUnirsePaciente {
    /** Botón "Unirse" activo. */
    ABIERTA,
    /** Hay enlace, pero el botón se activa 15 min antes. */
    ANTES,
    /** Ya pasó la ventana. */
    CERRADA,
    /** La clínica todavía no puso el enlace. */
    SIN_ENLACE,
}

/**
 * Gemelo de `UnirsePortal` (app/(portal)/portal/page.tsx): con lo que manda
 * `GET /api/paciente/mis-citas` (`enlaceVirtual`, `unirseDesde`, `unirseHasta`, ISO UTC).
 */
fun estadoUnirsePaciente(enlace: String?, unirseDesde: String?, unirseHasta: String?, ahoraMs: Long): EstadoUnirsePaciente {
    if (enlace.isNullOrBlank()) return EstadoUnirsePaciente.SIN_ENLACE
    val desde = isoAMs(unirseDesde) ?: return EstadoUnirsePaciente.ANTES
    val hasta = isoAMs(unirseHasta)
    return when {
        ahoraMs < desde -> EstadoUnirsePaciente.ANTES
        hasta != null && ahoraMs > hasta -> EstadoUnirsePaciente.CERRADA
        else -> EstadoUnirsePaciente.ABIERTA
    }
}

/** "2026-10-20T19:45:00.000Z" → "2:45 p. m." en la zona de la cita (null = no se entiende). */
fun horaUnirseDesde(unirseDesde: String?, zona: String?): String? {
    val ms = isoAMs(unirseDesde) ?: return null
    val tz = runCatching { TimeZone.of(zona?.takeIf { it.isNotBlank() } ?: ZONA_POR_DEFECTO) }.getOrElse { TimeZone.of("America/Lima") }
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
    val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$h12:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "a. m." else "p. m."}"
}

private fun isoAMs(iso: String?): Long? =
    iso?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it.trim()).toEpochMilliseconds() }.getOrNull() }

// ── Estado del Meet de una cita (staff) ──────────────────────────────────────

/** Las columnas virtuales de una cita + el estado del Meet que dice el servidor. */
data class FilaVirtual(
    val id: String,
    val modalidad: String? = null,
    val enlaceVirtual: String? = null,
    val enlaceOrigen: String? = null,
    /** null = el servidor no lo dijo (todavía no se preguntó). */
    val meetPendiente: Boolean? = null,
    val meetError: String? = null,
) {
    val esVirtual: Boolean get() = esCitaVirtual(modalidad)
}

enum class EstadoMeet { LISTO, CREANDO, ERROR, SIN_ENLACE }

/** Estado del Meet para la UI (null = presencial, nada que decir). Gemelo de `estadoMeetCita`. */
fun estadoMeetCita(f: FilaVirtual?): EstadoMeet? {
    if (f == null || !f.esVirtual) return null
    if (!f.enlaceVirtual.isNullOrEmpty()) return EstadoMeet.LISTO
    if (!f.meetError.isNullOrEmpty()) return EstadoMeet.ERROR
    if (f.enlaceOrigen == ORIGEN_MEET_AUTO && f.meetPendiente == true) return EstadoMeet.CREANDO
    return EstadoMeet.SIN_ENLACE
}

/** El error del Meet pide ir a conectar / dar permiso en Google (Ajustes → Calendario). */
fun errorPideReconectar(meetError: String?): Boolean =
    meetError != null && Regex("Ajustes|conect|permiso", RegexOption.IGNORE_CASE).containsMatchIn(meetError)

/** Línea del enlace en los mensajes al paciente (WhatsApp prearmado). */
fun lineaEnlaceVirtual(url: String?): String {
    val u = url?.trim().orEmpty()
    return if (u.isNotEmpty()) "🎥 Enlace: $u" else "🎥 Cita por videollamada (le enviaremos el enlace)"
}

/** ¿Se puede entrar ya a la videollamada desde la agenda? (cita viva con enlace). */
fun puedeUnirseStaff(modalidad: String?, enlace: String?, estado: String?): Boolean =
    esCitaVirtual(modalidad) && !enlace.isNullOrBlank() && (estado == "Pendiente" || estado == "Confirmada")

// ── Capacidad del Meet (GET /api/staff/cita/virtual?terapeutaId=) ────────────

data class CapacidadMeet(
    /** El plan crea el Meet solo (Premium/Plus/prueba). */
    val permitido: Boolean,
    /** Además hay un Google Calendar conectado con permiso de crear eventos. */
    val disponible: Boolean,
    /** 'plan' | 'sin_conexion' | 'sin_permiso' | null. */
    val motivo: String? = null,
    /** 'profesional' | 'clinica' | 'cuenta' | null. */
    val origen: String? = null,
)

/** Texto bajo "Crear Google Meet automáticamente" según la capacidad. null = nada. */
fun textoCapacidadMeet(meetPlan: Boolean, cap: CapacidadMeet?): String? = when {
    !meetPlan -> "🔒 Es de Premium: pega el enlace de tu videollamada."
    cap == null -> null
    !cap.disponible && cap.motivo == "sin_permiso" -> "Falta permitir que Sania cree eventos en Google (Ajustes → Calendario, en la web)."
    !cap.disponible && cap.motivo == "plan" -> "🔒 El Google Meet automático es de Premium."
    !cap.disponible -> "No hay un Google Calendar conectado (Ajustes → Calendario, en la web)."
    cap.origen == "profesional" -> "Se crea al guardar, en el Google Calendar del profesional."
    else -> "Se crea al guardar, en el Google Calendar de la clínica."
}

private fun JsonObject.texto(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
private fun JsonObject.logico(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull

/** `{ meet: { permitido, disponible, motivo, origen } }` → capacidad (null = no se entiende). */
fun parsearCapacidadMeet(raiz: JsonObject?): CapacidadMeet? {
    val m = raiz?.get("meet") as? JsonObject ?: return null
    return CapacidadMeet(
        permitido = m.logico("permitido") ?: false,
        disponible = m.logico("disponible") ?: false,
        motivo = m.texto("motivo"),
        origen = m.texto("origen"),
    )
}

/** `{ cita: { id, modalidad, enlace_virtual, enlace_origen, meet_pendiente, meet_error } }`. */
fun parsearFilaVirtual(raiz: JsonObject?): FilaVirtual? {
    val c = raiz?.get("cita") as? JsonObject ?: return null
    val id = c.texto("id") ?: return null
    return FilaVirtual(
        id = id,
        modalidad = c.texto("modalidad"),
        enlaceVirtual = c.texto("enlace_virtual"),
        enlaceOrigen = c.texto("enlace_origen"),
        meetPendiente = c.logico("meet_pendiente"),
        meetError = c.texto("meet_error"),
    )
}

/** El resultado del Meet en una respuesta (`meet: { estado, enlace, mensaje? }`, `aviso?`). */
data class ResultadoMeet(val estado: String?, val enlace: String?, val mensaje: String?, val aviso: String?)

fun parsearResultadoMeet(raiz: JsonObject?): ResultadoMeet? {
    if (raiz == null) return null
    val m = raiz["meet"] as? JsonObject
    val aviso = raiz.texto("aviso")
    if (m == null && aviso == null) return null
    return ResultadoMeet(m?.texto("estado"), m?.texto("enlace"), m?.texto("mensaje"), aviso)
}

/** El toast tras crear / configurar una cita con Meet (null = nada que decir). Gemelo de `crearMeetDeCita`. */
data class AvisoMeet(val texto: String, val tipo: TipoAvisoMeet)
enum class TipoAvisoMeet { EXITO, INFO, ERROR }

const val FALLO_MEET = "No se pudo crear el Meet; pega un enlace o reintenta desde la cita."

fun avisoTrasMeet(r: ResultadoMeet?, exito: String = "Google Meet creado: ya está en la cita"): AvisoMeet? = when (r?.estado) {
    null -> null
    "listo" -> AvisoMeet(exito, TipoAvisoMeet.EXITO)
    "pendiente" -> if (r.mensaje.isNullOrBlank()) {
        AvisoMeet("El Meet se está creando: aparecerá en la cita en unos minutos", TipoAvisoMeet.INFO)
    } else AvisoMeet(r.mensaje, TipoAvisoMeet.ERROR)
    "error" -> AvisoMeet(r.mensaje?.takeIf { it.isNotBlank() } ?: FALLO_MEET, TipoAvisoMeet.ERROR)
    else -> null
}
