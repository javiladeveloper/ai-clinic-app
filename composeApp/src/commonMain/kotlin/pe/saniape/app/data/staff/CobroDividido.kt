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

/** 2000 → "S/ 20.00" (céntimos; signo ignorado). Con otra moneda: [dineroDeCentimos]. */
fun solesDeCentimos(centimos: Long): String = dineroDeCentimos(centimos, MONEDA_POR_DEFECTO)

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
fun validarPagosDivididos(
    pagos: List<PartePago>,
    total: Double,
    /** 1 = el pago de un tratamiento con saldo a favor (puede ir todo con saldo). */
    minPartes: Int = MIN_PARTES_COBRO,
    /** Moneda de la sede del cobro (solo cambia el símbolo de los textos). */
    moneda: String = MONEDA_POR_DEFECTO,
): ValidacionPagos {
    if (pagos.size < minPartes || pagos.size > MAX_PARTES_COBRO) {
        return ValidacionPagos.Error(
            if (minPartes <= 1) "Un pago lleva de 1 a $MAX_PARTES_COBRO medios de pago."
            else "Un pago dividido lleva de $minPartes a $MAX_PARTES_COBRO medios de pago."
        )
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
        val tot = dineroDeCentimos(aCentimos(total), moneda)
        return ValidacionPagos.Error(
            if (dif > 0) "Los pagos no suman el total de $tot: faltan ${dineroDeCentimos(dif, moneda)}."
            else "Los pagos no suman el total de $tot: sobran ${dineroDeCentimos(dif, moneda)}."
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
fun estadoReparto(filas: List<FilaPago>, total: Double, moneda: String = MONEDA_POR_DEFECTO): String {
    val dif = diferenciaFilas(filas, total)
    if (dif > 0) return "Falta ${dineroDeCentimos(dif, moneda)}"
    if (dif < 0) return "Sobra ${dineroDeCentimos(dif, moneda)}"
    return when (val v = validarPagosDivididos(pagosDeFilas(filas), total, moneda = moneda)) {
        is ValidacionPagos.Error -> v.mensaje
        is ValidacionPagos.Ok -> "✓ Suma ${dineroDeCentimos(aCentimos(total), moneda)}"
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
 * `pagos`. [fecha] vacía = HOY en Lima (lo pone el servidor). Con fecha va
 * también `fechaElegida: true`: es la que eligió recepción o la del momento en
 * que se actuó (cola sin señal), y el servidor la respeta. Sin esa marca, una
 * fecha igual a la de la cita se toma como el default de la app vieja (→ hoy).
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
    if (!fecha.isNullOrBlank()) { put("fecha", fecha); put("fechaElegida", true) }
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

// ── Con qué medios se pagó una cita (gemelo de lib/comprobante-cita.ts + metodosPagoDeCitas) ──
//
// El cobro de una Consulta/Evaluación entra a caja con `comprobante = cita:<id>`;
// si se dividió, las partes 2..4 van como `cita:<id>#2`… y la parte 1 dice
// "(parte 1 de n)" al final de su descripción. La cita no guarda el método.

private val RE_COMPROBANTE_CITA = Regex("""^cita:([^#\s]+)(?:#([2-9]|[1-9]\d+))?$""")

/** Comprobante de la parte [parte] (1 = el de siempre) del cobro de una cita. */
fun comprobanteCita(citaId: String, parte: Int = 1): String =
    if (parte <= 1) "cita:$citaId" else "cita:$citaId#$parte"

/** Comprobantes de las partes 2..n (solo existen si el cobro se dividió). */
fun comprobantesExtraDeCita(citaId: String): List<String> =
    (2..MAX_PARTES_COBRO).map { comprobanteCita(citaId, it) }

/** Id de la cita de un comprobante `cita:<id>` o `cita:<id>#n`; null si no lo es. */
fun citaIdDeComprobante(c: String?): String? = RE_COMPROBANTE_CITA.find(c.orEmpty().trim())?.groupValues?.get(1)

/** Número de parte (1 = base) de un comprobante de cita; null si no lo es. */
fun parteDeComprobante(c: String?): Int? {
    val m = RE_COMPROBANTE_CITA.find(c.orEmpty().trim()) ?: return null
    return m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
}

/** "(parte 1 de 3)" al final de la descripción de la parte base → 3; null si no es un cobro dividido. */
fun partesDeDescripcion(descripcion: String?): Int? {
    val n = Regex("""\(parte 1 de (\d+)\)\s*$""").find(descripcion.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
    return n?.takeIf { it in MIN_PARTES_COBRO..MAX_PARTES_COBRO }
}

/** "Efectivo + Yape" a partir de los métodos (sin repetir ni vacíos, en orden). */
fun etiquetaDeMetodos(metodos: List<String?>): String =
    metodos.map { it.orEmpty().trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" + ")

/** ¿La etiqueta es de un cobro con varios medios? (lo que habilita "Anular cobro", como la web). */
fun esCobroDividido(etiqueta: String?): Boolean = etiqueta?.contains(" + ") == true

/** Un movimiento de caja del cobro de una cita, tal como se lee de `movimientos`. */
data class MovimientoCobroCita(val comprobante: String, val metodo: String?, val descripcion: String? = null)

/** Citas cuyo cobro se dividió (su parte base dice "(parte 1 de n)"): solo de ellas se piden las partes 2..n. */
fun citasConCobroDividido(base: List<MovimientoCobroCita>): Set<String> =
    base.filter { partesDeDescripcion(it.descripcion) != null }.mapNotNull { citaIdDeComprobante(it.comprobante) }.toSet()

/**
 * Con qué se pagó cada cita: "Yape", o "Efectivo + Yape" si el cobro se dividió
 * ([extra] = las partes 2..n de esas citas). Las partes se ordenan por número.
 */
fun metodosPorCita(base: List<MovimientoCobroCita>, extra: List<MovimientoCobroCita> = emptyList()): Map<String, String> {
    val porCita = mutableMapOf<String, String>()
    for (m in base) {
        val id = citaIdDeComprobante(m.comprobante) ?: continue
        m.metodo?.trim()?.takeIf { it.isNotEmpty() }?.let { porCita[id] = it }
    }
    val divididas = citasConCobroDividido(base)
    if (divididas.isEmpty()) return porCita
    val partes = (base + extra).sortedBy { parteDeComprobante(it.comprobante) ?: 0 }
    for (id in divididas) {
        val etiqueta = etiquetaDeMetodos(partes.filter { citaIdDeComprobante(it.comprobante) == id }.map { it.metodo })
        if (etiqueta.isNotEmpty()) porCita[id] = etiqueta
    }
    return porCita
}

/**
 * ¿Ofrecer "Anular cobro"? Gemelo del menú de /citas web: solo el Admin con
 * permiso de pagos, en una Consulta/Evaluación con costo ya cobrada con VARIOS
 * medios. Un cobro simple se corrige de otra forma (el servidor responde 409).
 */
fun puedeAnularCobro(
    rol: String?, puedePagos: Boolean, tipo: String?, costo: Double?, estado: String?,
    pagadaAt: String?, medios: String?,
): Boolean = rol == "Admin" && puedePagos && tipo != "Sesión" && (costo ?: 0.0) > 0 &&
    estado != "Cancelada" && pagadaAt != null && esCobroDividido(medios)

/**
 * El resultado de "Anular cobro", legible. [status] 0 = sin respuesta (sin
 * señal o timeout: pudo haber entrado): no se encola a propósito — borra dinero de caja y debe hacerse con el
 * Admin mirando. Los 409 del servidor (cobro simple, caja cerrada) ya traen el
 * texto que dice qué hacer.
 */
fun mensajeAnularCobro(status: Int, error: String?): String = when {
    status == 0 -> "Sin conexión: no se pudo confirmar la anulación. Con señal, recarga la agenda y mira si la cita quedó por cobrar antes de reintentar."
    status == 401 -> "Tu sesión expiró. Vuelve a entrar."
    status == 403 -> error?.takeIf { it.isNotBlank() } ?: "Solo el administrador puede anular un cobro."
    status == 404 -> "No se encontró la cita (puede que la hayan borrado). Recarga la agenda."
    status >= 500 -> "No se pudo anular el cobro. Recarga la agenda para ver si quedó por cobrar antes de reintentar."
    !error.isNullOrBlank() -> error
    else -> "No se pudo anular el cobro (HTTP $status)."
}
