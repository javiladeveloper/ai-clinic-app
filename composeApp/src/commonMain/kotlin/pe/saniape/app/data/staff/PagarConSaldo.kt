package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEnvio
import pe.saniape.app.data.offline.Sincronizador
import kotlin.random.Random

/**
 * PAGAR CON SALDO A FAVOR (pago mixto) y el saldo de los CANCELADOS — 2026-10-06.
 * Contrato: docs/app-contrato-pagar-con-saldo.md (web). Gemelo de
 * lib/pagar-con-saldo.ts en lo que es PANTALLA (validar el reparto, agrupar el
 * historial, textos); el dinero lo decide el servidor:
 *
 *  · El disponible se PREGUNTA (GET /api/staff/pago/saldo-a-favor): la app no lo
 *    calcula para pagar.
 *  · El pago va con `pagos` (partes) e `idempotency_key` UUID, que el servidor
 *    usa también como grupo del pago: un reintento con la MISMA clave nunca gasta
 *    el saldo dos veces.
 *  · NO se encola sin señal: el servidor tiene que ver el saldo en vivo (otra
 *    recepción pudo usarlo). Sin conexión se avisa y no se guarda nada.
 */

const val METODO_SALDO_A_FAVOR = "Saldo a favor"
const val SALDO_TIPO_USO = "uso"
const val SALDO_TIPO_CONSUMO = "consumo"

/** Con saldo, las partes REALES van de 0 a 3: la del saldo ya ocupa uno de los 4 medios. */
const val MAX_PARTES_RESTO = MAX_PARTES_COBRO - 1

const val CODIGO_SALDO_INSUFICIENTE = "SALDO_INSUFICIENTE"
const val CODIGO_SALDO_EXCEDE_DEUDA = "SALDO_EXCEDE_DEUDA"
const val CODIGO_SALDO_YA_APLICADO = "SALDO_YA_APLICADO"
const val CODIGO_DESTINO_NO_VALIDO = "DESTINO_NO_VALIDO"
const val CODIGO_SALDO_EN_SESION = "SALDO_EN_SESION"
const val CODIGO_SALDO_MONTO_INVALIDO = "SALDO_MONTO_INVALIDO"
const val CODIGO_SALDO_CONSUMO = "SALDO_CONSUMO"
const val CODIGO_PACIENTE_INACTIVO = "PACIENTE_INACTIVO"
const val CODIGO_SOLO_ADMIN = "SOLO_ADMIN"
const val CODIGO_NO_CANCELADO = "NO_CANCELADO"
const val CODIGO_SIN_SALDO_QUE_LIBERAR = "SIN_SALDO_QUE_LIBERAR"

/** ¿Esta parte es la del saldo a favor? (sin distinguir mayúsculas ni espacios, como el servidor). */
fun esMetodoSaldo(metodo: String?): Boolean =
    metodo.orEmpty().trim().equals(METODO_SALDO_A_FAVOR, ignoreCase = true)

/** "S/ 1,205.97" / "-S/ 205.97" (gemelo de formatearSoles de la web: el signo antes del símbolo). */
fun soles(monto: Double): String {
    val c = aCentimos(monto)
    val abs = kotlin.math.abs(c)
    val enteros = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (c < 0) "-" else "") + "S/ $enteros.${(abs % 100).toString().padStart(2, '0')}"
}

/** Céntimos → texto de un campo de monto ("205.97"). */
fun textoMonto(monto: Double): String {
    val c = kotlin.math.abs(aCentimos(monto))
    return "${c / 100}.${(c % 100).toString().padStart(2, '0')}"
}

// ── Disponible (GET /api/staff/pago/saldo-a-favor) ──

/** De qué tratamiento sale el saldo (en el orden en que se consume: el más antiguo primero). */
data class OrigenSaldo(val tratamientoId: String, val nombre: String, val estado: String?, val disponible: Double)

data class SaldoDisponible(val disponible: Double, val origenes: List<OrigenSaldo> = emptyList())

private val jsonSaldo = Json { ignoreUnknownKeys = true }

private fun JsonObject.texto(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
private fun JsonObject.numero(k: String): Double? = (this[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

/** El 200 del GET → disponible (puro). Cuerpo ilegible → null. */
fun parsearSaldoDisponible(cuerpo: String): SaldoDisponible? {
    val o = runCatching { jsonSaldo.parseToJsonElement(cuerpo).jsonObject }.getOrNull() ?: return null
    val origenes = (o["origenes"] as? JsonArray).orEmpty().mapNotNull { e ->
        val x = e as? JsonObject ?: return@mapNotNull null
        OrigenSaldo(
            tratamientoId = x.texto("tratamientoId") ?: return@mapNotNull null,
            nombre = x.texto("nombre") ?: "Tratamiento",
            estado = x.texto("estado"),
            disponible = (x.numero("disponible") ?: 0.0).coerceAtLeast(0.0),
        )
    }
    return SaldoDisponible((o.numero("disponible") ?: 0.0).coerceAtLeast(0.0), origenes)
}

/** Tope de la parte con saldo: min(disponible, deuda del tratamiento), al céntimo. 0 si no hay deuda. */
fun saldoUsable(disponible: Double, deuda: Double): Double {
    val c = minOf(aCentimos(disponible), aCentimos(deuda))
    return if (c > 0) c / 100.0 else 0.0
}

/** Reparto inicial: cuánto con saldo y cuánto queda para el medio habitual (si alcanza, todo con saldo). */
fun repartoSugerido(total: Double, disponible: Double): Pair<Double, Double> {
    val t = maxOf(aCentimos(total), 0L)
    val s = minOf(maxOf(aCentimos(disponible), 0L), t)
    return s / 100.0 to (t - s) / 100.0
}

// ── Validar el pago (la MISMA regla que el servidor: validarPartesConSaldo) ──

sealed class ValidacionPartes {
    data class Ok(val partes: List<PartePago>, val saldo: Double) : ValidacionPartes()
    data class Error(val mensaje: String) : ValidacionPartes()
}

/**
 * Valida las partes contra el total: de 1 a 4, cada una > 0 con 2 decimales, la
 * suma exacta al céntimo y a lo sumo UNA con "Saldo a favor".
 */
fun validarPartesConSaldo(partes: List<PartePago>, total: Double): ValidacionPartes {
    if (!(total > 0)) return ValidacionPartes.Error("Monto inválido")
    val v = validarPagosDivididos(partes, total, minPartes = 1)
    if (v is ValidacionPagos.Error) return ValidacionPartes.Error(v.mensaje)
    val ok = (v as ValidacionPagos.Ok).pagos
    val deSaldo = ok.filter { esMetodoSaldo(it.metodo) }
    if (deSaldo.size > 1) return ValidacionPartes.Error("El saldo a favor va en una sola parte.")
    return ValidacionPartes.Ok(ok, deSaldo.firstOrNull()?.monto ?: 0.0)
}

/**
 * Lo que se ve en el formulario → las partes del pago. [filasResto] null = el
 * resto con UN medio ([metodoResto]); si no, el reparto del resto en varios
 * medios (la UI del cobro dividido). Los textos son los de PagoCard web.
 */
fun partesDelFormulario(
    total: Double?, saldoTexto: String, usable: Double, metodoResto: String, filasResto: List<FilaPago>? = null,
): ValidacionPartes {
    if (total == null || !(total > 0)) return ValidacionPartes.Error("Indica el monto del pago.")
    val parteSaldo = montoDeTexto(saldoTexto) ?: 0.0
    if (!(parteSaldo > 0)) return ValidacionPartes.Error("Indica cuánto se paga con saldo a favor.")
    if (aCentimos(parteSaldo) > aCentimos(usable)) {
        return ValidacionPartes.Error("Con saldo a favor puedes usar hasta ${soles(usable)}.")
    }
    val restoC = aCentimos(total) - aCentimos(parteSaldo)
    if (restoC < 0) return ValidacionPartes.Error("El saldo a favor no puede ser mayor que el monto del pago.")
    val saldo = PartePago(METODO_SALDO_A_FAVOR, parteSaldo)
    val partes = when {
        restoC == 0L -> listOf(saldo)
        filasResto == null -> listOf(saldo, PartePago(metodoResto, restoC / 100.0))
        else -> {
            if (filasResto.size > MAX_PARTES_RESTO) {
                return ValidacionPartes.Error("Con saldo a favor, el resto va en hasta $MAX_PARTES_RESTO medios.")
            }
            if (filasResto.any { esMetodoSaldo(it.metodo) }) {
                return ValidacionPartes.Error("El saldo a favor va en una sola parte.")
            }
            when (val v = validarPagosDivididos(pagosDeFilas(filasResto), restoC / 100.0)) {
                is ValidacionPagos.Error -> return ValidacionPartes.Error(v.mensaje)
                is ValidacionPagos.Ok -> listOf(saldo) + v.pagos
            }
        }
    }
    return validarPartesConSaldo(partes, total)
}

/** "Saldo a favor S/ 205.97 + Yape S/ 284.03" (la parte con saldo primero). */
fun resumenPartes(partes: List<PartePago>): String =
    partes.sortedBy { if (esMetodoSaldo(it.metodo)) 0 else 1 }
        .joinToString(" + ") { "${it.metodo.trim()} ${soles(it.monto)}" }

/** El cuerpo de POST /api/staff/pago/registrar con `pagos` (sin la clave: la pone quien envía). */
fun cuerpoPagoConSaldo(
    tratamientoId: String, total: Double, partes: List<PartePago>, notas: String?, recordar: Boolean, fecha: String? = null,
): JsonObject = buildJsonObject {
    put("tratamientoId", tratamientoId)
    put("monto", aCentimos(total) / 100.0)
    put("pagos", JsonArray(partes.map { p ->
        buildJsonObject { put("metodo", p.metodo.trim()); put("monto", aCentimos(p.monto) / 100.0) }
    }))
    if (!fecha.isNullOrBlank()) put("fecha", fecha)
    if (!notas.isNullOrBlank()) put("notas", notas.trim())
    if (recordar) put("recordar", true)
}

/** UUID v4 ("8-4-4-4-12"): el servidor solo lo usa como grupo del pago si tiene esa forma. */
fun nuevoUuid(random: Random = Random): String {
    val b = random.nextBytes(16)
    b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte()
    b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
    val h = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}"
}

private val RE_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
fun esUuid(s: String?): Boolean = s != null && RE_UUID.matches(s)

// ── Cómo se MUESTRA un pago (gemelo de agruparPagos de lib/pagar-con-saldo.ts) ──

enum class TipoPagoMostrado { NORMAL, MIXTO, CONSUMO }

data class PagoMostrado(
    /** Id estable (el de la primera parte). */
    val clave: String,
    val tipo: TipoPagoMostrado,
    val fecha: String,
    /** Total del pago (en un consumo, negativo). */
    val monto: Double,
    /** "Yape", "Saldo a favor + Yape"… */
    val metodo: String,
    /** "Saldo a favor S/ 205.97 + Yape S/ 284.03" · "Saldo aplicado a Ortodoncia". null en un pago simple. */
    val detalle: String?,
    val partes: List<PagoFicha>,
) {
    /** La parte pagada con saldo (si la hay). */
    val uso: PagoFicha? get() = partes.firstOrNull { it.saldoTipo == SALDO_TIPO_USO }
    /** Se muestra como UNA línea agrupada (pago mixto, varias partes o consumo), no como el pago de siempre. */
    val agrupado: Boolean get() = tipo != TipoPagoMostrado.NORMAL || partes.size > 1
}

/**
 * Agrupa las filas de un tratamiento: las partes de un pago mixto (mismo
 * `grupo_pago_id`) en UNA línea, la parte con saldo primero; un consumo como
 * "Saldo aplicado a <tratamiento>". Respeta el orden de llegada.
 */
fun agruparPagos(filas: List<PagoFicha>): List<PagoMostrado> {
    data class Acum(val clave: String, val fecha: String, val consumo: Boolean, val partes: MutableList<PagoFicha>)
    val orden = mutableListOf<Acum>()
    val porGrupo = mutableMapOf<String, Acum>()
    for (f in filas) {
        if (f.saldoTipo == SALDO_TIPO_CONSUMO) { orden += Acum(f.id, f.fecha, true, mutableListOf(f)); continue }
        val g = f.grupoPagoId
        val existente = g?.let { porGrupo[it] }
        if (existente != null) { existente.partes += f; continue }
        val a = Acum(f.id, f.fecha, false, mutableListOf(f))
        orden += a
        if (g != null) porGrupo[g] = a
    }
    return orden.map { a ->
        if (a.consumo) {
            val f = a.partes.first()
            PagoMostrado(f.id, TipoPagoMostrado.CONSUMO, f.fecha, f.monto, METODO_SALDO_A_FAVOR,
                "Saldo aplicado a ${f.destinoNombre?.takeIf { it.isNotBlank() } ?: "otro tratamiento"}", a.partes)
        } else {
            val partes = a.partes.sortedBy { if (it.saldoTipo == SALDO_TIPO_USO) 0 else 1 }
            val conSaldo = partes.any { it.saldoTipo == SALDO_TIPO_USO }
            val detalle = if (partes.size > 1 || conSaldo)
                partes.joinToString(" + ") { "${it.metodo.trim().ifEmpty { "Pago" }} ${soles(it.monto)}" } else null
            PagoMostrado(
                clave = a.clave,
                tipo = if (conSaldo) TipoPagoMostrado.MIXTO else TipoPagoMostrado.NORMAL,
                fecha = a.fecha,
                monto = partes.sumOf { aCentimos(it.monto) } / 100.0,
                metodo = partes.map { it.metodo.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" + "),
                detalle = detalle,
                partes = partes,
            )
        }
    }
}

/** Pagado del tratamiento = Σ de TODAS sus filas, también las negativas (contrato §6). */
fun pagadoNeto(filas: List<PagoFicha>): Double = filas.sumOf { aCentimos(it.monto) } / 100.0

/** ¿Este tratamiento ya DIO saldo a otro? (tiene filas 'consumo'). */
fun yaDioSaldo(filas: List<PagoFicha>): Boolean = filas.any { it.saldoTipo == SALDO_TIPO_CONSUMO }

// ── Mensajes ──

const val MENSAJE_PAGO_SALDO_SIN_RED =
    "Sin conexión: el pago con saldo a favor no se guarda para después porque necesita el saldo en vivo. " +
        "Con señal, vuelve a tocar Guardar: no se cobrará dos veces."
const val MENSAJE_PAGO_SALDO_INCIERTO =
    "No se pudo confirmar el pago. Revisa la conexión y vuelve a tocar Guardar sin cambiar nada: no se cobrará dos veces."

/** `disponible` / `deuda` del cuerpo del rechazo (409 del saldo). */
fun numeroDelRechazo(r: RechazoServidor, campo: String): Double? = r.datos?.numero(campo)

/**
 * El "no" del pago con saldo, legible. El servidor ya escribe en castellano; aquí
 * se completa lo que necesita decir QUÉ hacer, con su texto si no vino ninguno.
 */
fun mensajeRechazoPagoSaldo(r: RechazoServidor): String {
    val delServidor = r.error.takeIf { it.isNotBlank() && !it.startsWith("HTTP ") }
    return when {
        r.codigo == CODIGO_SALDO_INSUFICIENTE -> {
            val d = numeroDelRechazo(r, "disponible")
            if (d != null) "El saldo a favor cambió: ahora hay ${soles(d)} disponibles (otra recepción pudo usarlo). Ajusta la parte con saldo y vuelve a intentar."
            else delServidor ?: "El saldo a favor ya no alcanza para ese monto. Ajusta la parte con saldo y vuelve a intentar."
        }
        r.codigo == CODIGO_SALDO_EXCEDE_DEUDA -> {
            val d = numeroDelRechazo(r, "deuda")
            when {
                d != null && d > 0.005 -> "Con saldo a favor se puede pagar hasta lo que se debe de este tratamiento (${soles(d)})."
                d != null -> "Este tratamiento no tiene deuda: no hace falta usar saldo a favor."
                else -> delServidor ?: "La parte con saldo pasa de lo que se debe de este tratamiento."
            }
        }
        r.codigo == CODIGO_DESTINO_NO_VALIDO -> "No se puede usar saldo a favor en un tratamiento cancelado o eliminado."
        r.codigo == CODIGO_SALDO_EN_SESION -> "El saldo a favor paga el tratamiento, no el cobro de una sesión."
        r.codigo == CODIGO_SALDO_YA_APLICADO ->
            delServidor ?: "Primero deshaz el saldo aplicado a otro tratamiento: este dinero ya se usó para pagarlo."
        r.codigo == CODIGO_SALDO_CONSUMO ->
            delServidor ?: "Esto es saldo aplicado a otro tratamiento: se deshace borrando el pago donde se usó."
        r.codigo == CODIGO_PAGO_DIVIDIDO_INVALIDO -> delServidor ?: "Las partes del pago no son válidas."
        r.codigo == CODIGO_SALDO_MONTO_INVALIDO -> "El monto con saldo a favor no es válido (mayor que cero, dos decimales)."
        r.codigo == CODIGO_PACIENTE_INACTIVO ->
            delServidor ?: "El paciente está dado de baja: reactívalo desde su ficha antes de registrarle cobros."
        r.status == 401 -> "Tu sesión expiró. Vuelve a entrar."
        r.status == 403 -> delServidor ?: "No tienes permiso para registrar cobros."
        r.status == 404 -> "No se encontró el tratamiento (puede que lo hayan eliminado). Recarga la ficha."
        r.status >= 500 -> MENSAJE_PAGO_SALDO_INCIERTO
        else -> delServidor ?: "No se pudo registrar el pago (HTTP ${r.status})."
    }
}

/** El "no" de editar/borrar un pago con saldo o de liberar/revocar (ya trae su texto del servidor). [status] 0 = sin respuesta. */
fun mensajeOperacionSaldo(status: Int, r: RechazoServidor?, queSeHacia: String): String {
    val delServidor = r?.error?.takeIf { it.isNotBlank() && !it.startsWith("HTTP ") }
    return when {
        status == 0 -> "Sin conexión: no se pudo confirmar $queSeHacia. Con señal, recarga la ficha y revisa los pagos antes de reintentar."
        r?.codigo == CODIGO_SALDO_YA_APLICADO ->
            delServidor ?: "Primero deshaz el saldo aplicado a otro tratamiento: este dinero ya se usó para pagarlo."
        r?.codigo == CODIGO_SALDO_INSUFICIENTE || r?.codigo == CODIGO_SALDO_EXCEDE_DEUDA -> mensajeRechazoPagoSaldo(r)
        r?.codigo == CODIGO_SIN_SALDO_QUE_LIBERAR ->
            delServidor ?: "No hay nada que pasar a saldo a favor: lo pagado ya se atendió (o no se puede saber cuánto quedó sin atender)."
        r?.codigo == CODIGO_NO_CANCELADO -> "Solo se puede pasar a saldo a favor lo pagado de un tratamiento cancelado."
        status == 401 -> "Tu sesión expiró. Vuelve a entrar."
        status == 403 -> delServidor ?: "Solo el administrador puede hacerlo."
        status == 404 -> delServidor ?: "No se encontró (puede que ya no exista). Recarga la ficha."
        status >= 500 -> "No se pudo confirmar $queSeHacia. Recarga la ficha y revisa los pagos antes de reintentar."
        delServidor != null -> delServidor
        else -> "No se pudo completar (HTTP $status)."
    }
}

// ── Saldo de un tratamiento CANCELADO (solo el Admin lo libera) ──

data class LiberacionSaldo(val monto: Double, val liberadoAt: String?, val liberadoPor: String?, val repetido: Boolean)

/** El 200 de /api/staff/tratamiento/liberar-saldo (puro). */
fun parsearLiberacion(o: JsonObject?): LiberacionSaldo? {
    o ?: return null
    return LiberacionSaldo(
        monto = (o.numero("monto") ?: 0.0).coerceAtLeast(0.0),
        liberadoAt = o.texto("liberadoAt"),
        liberadoPor = o.texto("liberadoPor"),
        repetido = o.texto("repetido") == "true",
    )
}

/** "2026-10-06T15:00:00Z" → "06/10/2026" en la zona de la clínica (America/Lima). Si no se entiende, la fecha tal cual. */
fun fechaLiberacion(iso: String): String = runCatching {
    val d = Instant.parse(iso.trim().replace(' ', 'T').let { if (Regex("[+-]\\d{2}$").containsMatchIn(it)) "$it:00" else it })
        .toLocalDateTime(ZONA_CLINICA).date
    "${d.dayOfMonth.toString().padStart(2, '0')}/${d.monthNumber.toString().padStart(2, '0')}/${d.year}"
}.getOrElse {
    val f = iso.take(10).split('-')
    if (f.size == 3) "${f[2]}/${f[1]}/${f[0]}" else iso
}

// ── Eliminar un tratamiento con pagos (el aviso; gemelo de resumenPagosAlEliminar) ──

data class ResumenEliminar(
    /** Pagos REALES (los que están en caja). */
    val reales: List<PagoFicha>,
    /** Lo que este tratamiento RECIBIÓ con saldo: al eliminarlo vuelve al paciente (siempre). */
    val recibido: Double,
    /** Lo que DIO como saldo, por tratamiento destino: si se revierte, ese otro vuelve a deberlo. */
    val dado: List<Pair<String, Double>>,
) {
    val totalReales: Double get() = reales.sumOf { aCentimos(it.monto) } / 100.0
    val tieneDinero: Boolean get() = reales.isNotEmpty() || recibido > 0.005 || dado.isNotEmpty()
}

fun resumenAlEliminar(filas: List<PagoFicha>): ResumenEliminar {
    val reales = filas.filter { it.saldoTipo == null }
    val recibido = filas.filter { it.saldoTipo == SALDO_TIPO_USO }.sumOf { aCentimos(it.monto) } / 100.0
    val dado = filas.filter { it.saldoTipo == SALDO_TIPO_CONSUMO }
        .groupBy { it.destinoNombre?.takeIf { n -> n.isNotBlank() } ?: "otro tratamiento" }
        .map { (nombre, l) -> nombre to l.sumOf { -aCentimos(it.monto) } / 100.0 }
    return ResumenEliminar(reales, recibido, dado)
}

/** El "no" de eliminar (parcial incluido: el servidor ya dice qué quedó hecho). */
fun mensajeEliminarTratamiento(status: Int, r: RechazoServidor?): String = when {
    status == 0 -> "Sin conexión: no se pudo confirmar. Con señal, recarga la ficha y mira si el tratamiento sigue antes de reintentar."
    r?.codigo == CODIGO_SALDO_YA_APLICADO -> r.error.ifBlank { "Primero deshaz el saldo aplicado a otro tratamiento." }
    status == 401 -> "Tu sesión expiró. Vuelve a entrar."
    status == 403 -> r?.error?.takeIf { it.isNotBlank() } ?: "No tienes permiso para eliminar tratamientos."
    !r?.error.isNullOrBlank() && r?.error?.startsWith("HTTP ") == false -> r.error
    else -> "No se pudo eliminar el tratamiento (HTTP $status). Recarga la ficha antes de reintentar."
}

// ── Red ──

/** Resultado de consultar el disponible. */
sealed class ConsultaSaldo {
    data class Ok(val saldo: SaldoDisponible) : ConsultaSaldo()
    /** Sin conexión: no se pudo preguntar (la opción no se ofrece y se dice por qué). */
    data object SinRed : ConsultaSaldo()
    /** El servidor no lo dio (sin permiso, versión vieja…): la opción simplemente no aparece. */
    data object NoDisponible : ConsultaSaldo()
}

/** Resultado del pago con saldo. */
sealed class ResultadoPagoSaldo {
    data class Ok(val saldoAplicado: Double, val estadoPago: String?) : ResultadoPagoSaldo()
    data class Rechazo(val mensaje: String, val rechazo: RechazoServidor) : ResultadoPagoSaldo()
    /** Sin respuesta (sin señal o timeout): pudo haber entrado. La clave se conserva. */
    data object SinRed : ResultadoPagoSaldo()
}

/**
 * Lecturas y escrituras del saldo a favor. Todas DIRECTAS (nunca a la cola
 * offline): el saldo es de todos los que atienden al paciente y el resultado se
 * tiene que ver en el momento.
 */
object PagarConSaldoRepo {

    private val http = crearHttpClient()

    /** Claves de pagos con respuesta INCIERTA, por cuerpo exacto: el reintento de la MISMA operación la reusa. */
    private val clavesInciertas = mutableMapOf<String, String>()
    private val mutexClaves = Mutex()

    /** Disponible del paciente SIN contar [tratamientoId] (el que se va a pagar). */
    suspend fun saldoDisponible(pacienteId: String, tratamientoId: String?): ConsultaSaldo {
        val tk = runCatching { Supabase.client.auth.currentSessionOrNull()?.accessToken }.getOrNull()
            ?: return ConsultaSaldo.NoDisponible
        return try {
            val q = "pacienteId=$pacienteId" + (tratamientoId?.let { "&tratamientoId=$it" } ?: "")
            val resp = http.get("${Supabase.SITE_URL}/api/staff/pago/saldo-a-favor?$q") {
                header("Authorization", "Bearer $tk")
            }
            if (resp.status.value != 200) ConsultaSaldo.NoDisponible
            else parsearSaldoDisponible(resp.bodyAsText())?.let { ConsultaSaldo.Ok(it) } ?: ConsultaSaldo.NoDisponible
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("SaniaSaldo: no se pudo consultar el saldo a favor — ${e.message}")
            ConsultaSaldo.SinRed
        }
    }

    private suspend fun enviar(endpoint: String, cuerpo: JsonObject, clave: String) =
        runCatching { Sincronizador.enviarAhoraCompleto(endpoint, cuerpo, clave) }
            .getOrElse {
                if (it is kotlin.coroutines.cancellation.CancellationException) throw it
                Triple(ResultadoEnvio.SIN_RED, null, null)
            }

    /**
     * Registra el pago de un tratamiento con saldo a favor (+ el resto con los
     * medios de siempre). Ante una respuesta INCIERTA (sin señal, timeout, 5xx)
     * la clave se CONSERVA y el reintento del mismo pago la reusa: el servidor lo
     * reconoce y no gasta el saldo dos veces. Se suelta con una respuesta cierta.
     */
    suspend fun registrar(cuerpo: JsonObject): ResultadoPagoSaldo {
        val firma = cuerpo.toString()
        val clave = mutexClaves.withLock { clavesInciertas[firma] } ?: nuevoUuid()
        val (res, rechazo, respuesta) = enviar("/api/staff/pago/registrar", cuerpo, clave)
        val incierto = res == ResultadoEnvio.SIN_RED || (res == ResultadoEnvio.RECHAZADO && rechazoIncierto(rechazo))
        mutexClaves.withLock { if (incierto) clavesInciertas[firma] = clave else clavesInciertas.remove(firma) }
        return when (res) {
            ResultadoEnvio.OK -> {
                // Tutoriales: cobro registrado (+ pago con saldo si lo usó).
                pe.saniape.app.tutoriales.TareasEscritura.emitir("/api/staff/pago/registrar", cuerpo, respuesta)
                ResultadoPagoSaldo.Ok(
                    saldoAplicado = respuesta?.numero("saldoAplicado") ?: 0.0,
                    estadoPago = respuesta?.texto("estadoPago"),
                )
            }
            ResultadoEnvio.SIN_RED -> ResultadoPagoSaldo.SinRed
            ResultadoEnvio.RECHAZADO -> {
                val r = rechazo ?: RechazoServidor("Error del servidor", status = 500)
                ResultadoPagoSaldo.Rechazo(mensajeRechazoPagoSaldo(r), r)
            }
        }
    }

    /** Envía y devuelve null si salió bien; si no, el texto a mostrar. */
    private suspend fun operar(endpoint: String, cuerpo: JsonObject, queSeHacia: String): Pair<String?, JsonObject?> {
        val (res, rechazo, respuesta) = enviar(endpoint, cuerpo, nuevoUuid())
        return when (res) {
            ResultadoEnvio.OK -> null to respuesta
            ResultadoEnvio.SIN_RED -> mensajeOperacionSaldo(0, null, queSeHacia) to null
            ResultadoEnvio.RECHAZADO -> mensajeOperacionSaldo(rechazo?.status ?: 500, rechazo, queSeHacia) to null
        }
    }

    /** Cambia el monto de la parte pagada con saldo (solo Admin). null = hecho. */
    suspend fun editarParteSaldo(pagoId: String, monto: Double): String? =
        operar("/api/staff/pago/editar", buildJsonObject {
            put("pagoId", pagoId); put("monto", aCentimos(monto) / 100.0)
        }, "el cambio").first

    /**
     * Borra un pago (solo Admin). [borrarGrupo] = el pago mixto COMPLETO: la parte
     * con saldo vuelve a sus tratamientos de origen; las reales se llevan su
     * ingreso y su comisión. null = hecho. Un parcial ya trae su texto.
     */
    suspend fun borrar(pagoId: String, borrarGrupo: Boolean): String? =
        operar("/api/staff/pago/borrar", buildJsonObject {
            put("pagoId", pagoId); if (borrarGrupo) put("borrarGrupo", true)
        }, "el borrado").first

    /** "Pasar lo pagado no atendido a saldo a favor" (solo Admin). */
    suspend fun liberar(tratamientoId: String): Pair<LiberacionSaldo?, String?> {
        val (error, json) = operar("/api/staff/tratamiento/liberar-saldo",
            buildJsonObject { put("tratamientoId", tratamientoId) }, "el cambio")
        return if (error != null) null to error else (parsearLiberacion(json) ?: LiberacionSaldo(0.0, null, null, false)) to null
    }

    /** Deshacer la liberación (solo Admin). null = hecho. */
    suspend fun revocar(tratamientoId: String): String? =
        operar("/api/staff/tratamiento/revocar-saldo",
            buildJsonObject { put("tratamientoId", tratamientoId) }, "el cambio").first

    /**
     * Elimina un tratamiento (solo Admin) decidiendo qué pasa con su dinero
     * ([revertirPagos]). Lo que recibió con saldo vuelve siempre al paciente; si
     * algo no se puede, el servidor NO lo elimina y dice por qué. null = hecho.
     */
    suspend fun eliminarTratamiento(tratamientoId: String, revertirPagos: Boolean): String? {
        val (res, rechazo, _) = enviar("/api/staff/tratamiento/accion", buildJsonObject {
            put("accion", "estado"); put("tratamientoId", tratamientoId); put("estado", "Eliminado")
            put("revertirPagos", revertirPagos)
        }, nuevoUuid())
        return when (res) {
            ResultadoEnvio.OK -> null
            ResultadoEnvio.SIN_RED -> mensajeEliminarTratamiento(0, null)
            ResultadoEnvio.RECHAZADO -> mensajeEliminarTratamiento(rechazo?.status ?: 500, rechazo)
        }
    }

    /** Nombre de quien liberó el saldo (perfiles, con la RLS del staff). null si no se pudo leer. */
    suspend fun nombrePerfil(uid: String): String? = runCatching {
        Supabase.client.postgrest["perfiles"]
            .select(Columns.list("nombre")) { filter { eq("id", uid) }; limit(1) }
            .decodeList<JsonObject>().firstOrNull()?.texto("nombre")
    }.getOrElse { if (it is kotlin.coroutines.cancellation.CancellationException) throw it; null }
}
