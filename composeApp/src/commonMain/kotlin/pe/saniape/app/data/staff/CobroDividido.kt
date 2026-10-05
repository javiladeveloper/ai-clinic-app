package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/**
 * COBRO DIVIDIDO de una consulta/evaluación: pagada con varios medios
 * (DALU 2026-10-03: "S/ 20 en efectivo y S/ 20 por Yape"), para que el arqueo
 * por método cuadre.
 *
 * Gemelo de `lib/cobro-dividido.ts` de la web, que es la MISMA regla que aplica
 * el servidor (/api/staff/cita/cobrar): si la pantalla habilitara un "Cobrar"
 * que el servidor rechaza, recepción quedaría atascada con el paciente delante.
 *
 * Todo se compara en CÉNTIMOS: 0.1 + 0.2 no es 0.3 en coma flotante, y una
 * caja que "casi" cuadra no cuadra.
 */

const val MIN_PARTES_COBRO = 2
const val MAX_PARTES_COBRO = 4
private const val MAX_LARGO_METODO = 60

/** Código del 400 de validación del reparto (`PAGO_DIVIDIDO_INVALIDO`). */
const val CODIGO_PAGO_DIVIDIDO_INVALIDO = "PAGO_DIVIDIDO_INVALIDO"
/** 409: un abono dividido quedó a medias y solo se completa con el MISMO reparto. */
const val CODIGO_COBRO_PARCIAL_PREVIO = "COBRO_PARCIAL_PREVIO"

/** Un medio de pago con su monto, como lo espera `pagos` en el cuerpo del cobro. */
data class PartePago(val metodo: String, val monto: Double)

/** Una fila del formulario: el monto es el TEXTO del campo (vacío mientras se escribe). */
data class FilaPago(val metodo: String, val monto: String = "")

/** Soles → céntimos enteros. */
fun aCentimos(monto: Double): Long = kotlin.math.round(monto * 100).toLong()

/** "12,5" / "12.5" → 12.5; null si no es un número. */
fun montoDeTexto(texto: String): Double? =
    texto.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }

private fun dosDecimales(monto: Double): Boolean =
    kotlin.math.abs(monto * 100 - kotlin.math.round(monto * 100)) < 1e-6

/** Lo que falta (positivo) o sobra (negativo) para llegar al total, en céntimos. */
fun diferenciaCentimos(montos: List<Double?>, total: Double): Long =
    aCentimos(total) - montos.sumOf { aCentimos(it ?: 0.0) }

/** 2000 → "S/ 20.00" (céntimos; signo ignorado). */
fun solesDeCentimos(centimos: Long): String {
    val c = kotlin.math.abs(centimos)
    return "S/ ${c / 100}.${(c % 100).toString().padStart(2, '0')}"
}

/** El reparto validado, o por qué no se puede cobrar (en castellano, listo para mostrar). */
sealed class ValidacionPagos {
    data class Ok(val pagos: List<PartePago>) : ValidacionPagos()
    data class Error(val mensaje: String) : ValidacionPagos()
}

/**
 * Valida el reparto contra el costo de la cita, con las MISMAS reglas y textos
 * que el servidor: de 2 a 4 medios, cada uno con nombre y monto mayor que cero
 * (máx. 2 decimales), y la suma igual al total al céntimo. Devuelve las partes
 * normalizadas (método sin espacios sobrantes, monto redondeado a céntimos).
 */
fun validarPagosDivididos(pagos: List<PartePago>, total: Double): ValidacionPagos {
    if (pagos.size < MIN_PARTES_COBRO || pagos.size > MAX_PARTES_COBRO) {
        return ValidacionPagos.Error("Un pago dividido lleva de $MIN_PARTES_COBRO a $MAX_PARTES_COBRO medios de pago.")
    }
    val normalizados = pagos.mapIndexed { i, p ->
        val n = i + 1
        val metodo = p.metodo.trim()
        if (metodo.isEmpty()) return ValidacionPagos.Error("Falta el medio de pago del pago $n.")
        if (metodo.length > MAX_LARGO_METODO) return ValidacionPagos.Error("El medio de pago del pago $n es demasiado largo.")
        if (!p.monto.isFinite() || p.monto <= 0) return ValidacionPagos.Error("El monto del pago $n debe ser mayor que cero.")
        if (!dosDecimales(p.monto)) return ValidacionPagos.Error("El monto del pago $n tiene más de dos decimales.")
        PartePago(metodo, aCentimos(p.monto) / 100.0)
    }
    val dif = diferenciaCentimos(normalizados.map { it.monto }, total)
    if (dif != 0L) {
        val tot = solesDeCentimos(aCentimos(total))
        return ValidacionPagos.Error(
            if (dif > 0) "Los pagos no suman el total de $tot: faltan ${solesDeCentimos(dif)}."
            else "Los pagos no suman el total de $tot: sobran ${solesDeCentimos(dif)}."
        )
    }
    return ValidacionPagos.Ok(normalizados)
}

/** Dos filas para empezar: el método preferido y el siguiente, sin montos. */
fun filasIniciales(metodos: List<String>, preferido: String? = null): List<FilaPago> {
    val lista = metodos.ifEmpty { listOf("Efectivo") }
    val primero = preferido?.takeIf { it in lista } ?: lista.first()
    val segundo = lista.firstOrNull { it != primero } ?: primero
    return listOf(FilaPago(primero), FilaPago(segundo))
}

/** La fila nueva de "+ Agregar otro medio": el primer método que aún no se usa. */
fun filaNueva(metodos: List<String>, filas: List<FilaPago>): FilaPago {
    val lista = metodos.ifEmpty { listOf("Efectivo") }
    return FilaPago(lista.firstOrNull { m -> filas.none { it.metodo == m } } ?: lista.first())
}

/**
 * Las filas como `pagos`. Un monto que no es número va como NaN: la validación
 * lo rechaza ("debe ser mayor que cero"), igual que la web.
 */
fun pagosDeFilas(filas: List<FilaPago>): List<PartePago> =
    filas.map { PartePago(it.metodo, montoDeTexto(it.monto) ?: Double.NaN) }

/** Las partes validadas si las filas cuadran con el total; null si no. */
fun repartoValido(filas: List<FilaPago>, total: Double): List<PartePago>? =
    (validarPagosDivididos(pagosDeFilas(filas), total) as? ValidacionPagos.Ok)?.pagos

/** Diferencia de las FILAS (texto) con el total, en céntimos (vacío o no-número = 0). */
fun diferenciaFilas(filas: List<FilaPago>, total: Double): Long =
    diferenciaCentimos(filas.map { montoDeTexto(it.monto) }, total)

/**
 * El estado en vivo bajo las filas: "Falta S/ X", "Sobra S/ X", el error de
 * validación si suma bien pero algo no vale (un monto 0, tres decimales…) o
 * "✓ Suma S/ X" cuando cuadra.
 */
fun estadoReparto(filas: List<FilaPago>, total: Double): String {
    val dif = diferenciaFilas(filas, total)
    if (dif > 0) return "Falta ${solesDeCentimos(dif)}"
    if (dif < 0) return "Sobra ${solesDeCentimos(dif)}"
    return when (val v = validarPagosDivididos(pagosDeFilas(filas), total)) {
        is ValidacionPagos.Error -> v.mensaje
        is ValidacionPagos.Ok -> "✓ Suma ${solesDeCentimos(aCentimos(total))}"
    }
}

/**
 * "Poner lo que falta en X": índice de la fila que recibe lo que falta (la
 * primera vacía o, si no hay, la última) y su nuevo texto. null si no falta nada
 * o si el resultado no sería positivo.
 */
fun completarFaltante(filas: List<FilaPago>, total: Double): Pair<Int, String>? {
    if (filas.isEmpty()) return null
    val dif = diferenciaFilas(filas, total)
    if (dif <= 0) return null
    val vacia = filas.indexOfFirst { it.monto.isBlank() }
    val destino = if (vacia >= 0) vacia else filas.lastIndex
    val nuevo = aCentimos(montoDeTexto(filas[destino].monto) ?: 0.0) + dif
    if (nuevo <= 0) return null
    return destino to "${nuevo / 100}.${(nuevo % 100).toString().padStart(2, '0')}"
}

/** "Efectivo + Yape" (sin repetir, en el orden en que se pagó). */
fun etiquetaMetodos(pagos: List<PartePago>): String =
    pagos.map { it.metodo.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" + ")

/**
 * El cuerpo de POST /api/staff/cita/cobrar. Con [pagos] (cobro dividido) va
 * `pagos` EN LUGAR de `metodo`; 'gratis' no lleva dinero, así que nunca manda
 * `pagos`. [fecha] vacía = la de la cita.
 */
fun cuerpoCobrarCita(
    citaId: String, metodo: String, modo: String, fecha: String?, pagos: List<PartePago>? = null,
): JsonObject = buildJsonObject {
    put("citaId", citaId)
    val dividido = !pagos.isNullOrEmpty() && modo != "gratis"
    if (dividido) {
        put("pagos", JsonArray(pagos!!.map { p ->
            buildJsonObject { put("metodo", p.metodo); put("monto", p.monto) }
        }))
    } else {
        put("metodo", metodo)
    }
    put("modo", modo)
    if (!fecha.isNullOrBlank()) put("fecha", fecha)
}

/** ¿El rechazo es "incierto" (5xx / timeout de Vercel)? El cobro pudo haber entrado. */
fun rechazoIncierto(r: RechazoServidor?): Boolean = r != null && r.status >= 500

/**
 * El "no" del cobro, legible para recepción. El servidor ya escribe textos en
 * castellano; aquí solo se completan los casos que necesitan decir QUÉ hacer.
 */
fun mensajeRechazoCobro(r: RechazoServidor, dividido: Boolean): String = when {
    r.codigo == CODIGO_COBRO_PARCIAL_PREVIO ->
        if (dividido) "Este cobro quedó a medias. Para completarlo, repítelo con el MISMO reparto (mismos medios y montos); si no, revisa los pagos del tratamiento en la ficha."
        else "Este cobro quedó a medias (fue dividido en varios medios): revisa los pagos del tratamiento en la ficha antes de volver a cobrar."
    rechazoIncierto(r) ->
        "No se pudo confirmar el cobro. Revisa la conexión y vuelve a tocar Cobrar " +
            (if (dividido) "con el mismo reparto: " else "") + "no se cobrará dos veces."
    else -> r.error
}

/** Resultado del cobro: la escritura y si la cita YA estaba cobrada (no se registró de nuevo). */
data class ResultadoCobro(val escritura: ResultadoEscritura, val yaEstaba: Boolean = false) {
    val registrada: Boolean get() = escritura.registrada
    val encolada: Boolean get() = escritura.encolada
    val rechazo: RechazoServidor? get() = escritura.rechazo
}

/** `{ ok: true, yaEstaba: true }` → true. */
fun yaEstabaDe(respuesta: JsonObject?): Boolean =
    (respuesta?.get("yaEstaba") as? JsonPrimitive)?.content == "true"
