package pe.saniape.app.data.staff

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// «Completar la evaluación → plan → agendar → cobrar» en UN recorrido (T3,
// 24/10/2026). Gemelo de lib/plan-tras-evaluacion.ts de la web (mismos casos en
// PlanTrasEvaluacionTest). Tras completar la evaluación, la agenda ofrece el
// paso «Plan de tratamiento» ya lleno —servicio sugerido, sesiones y precio del
// catálogo (negociable), quien evaluó, el diagnóstico— y «Crear plan y agendar»
// sigue con el plan entero (POST /api/staff/sesion/plan). El guardado es el de
// siempre (/api/staff/tratamiento/accion 'crear').

/** Lo que estas reglas leen de un servicio del catálogo. */
data class ServicioDelPlan(
    val id: String,
    val precio: Double? = null,
    val precioPaquete: Double? = null,
    val modoCobro: String? = null,
    val usaSesiones: Boolean = true,
    /** (cantidad de sesiones, precio total) de cada tarifario activo. */
    val tarifarios: List<Pair<Int, Double>> = emptyList(),
    /** El servicio de la cita de entrada (Consulta/Evaluación): nunca se sugiere como plan. */
    val tipoCita: String? = null,
)

fun ProcedimientoRef.aServicioPlan() = ServicioDelPlan(
    id = id, precio = precio, precioPaquete = precioPaquete, modoCobro = modoCobro, usaSesiones = usaSesiones,
    tarifarios = tarifarios.map { it.cantidadSesiones to it.precioTotal }, tipoCita = tipoCita,
)

/** Modo de cobro EFECTIVO (el mismo que TratamientoForm): 'sesiones' | 'simple' | 'unidades'. */
fun modoDeServicioPlan(s: ServicioDelPlan?): String? {
    if (s == null) return null
    return when (s.modoCobro) {
        "sesiones", "simple", "unidades" -> s.modoCobro
        else -> if (s.usaSesiones) "sesiones" else "simple"
    }
}

/** Cuántos planes nacidos de una evaluación lleva cada servicio. */
fun usosPorServicio(procedimientoIds: List<String?>): Map<String, Int> =
    procedimientoIds.filterNotNull().groupingBy { it }.eachCount()

/**
 * El servicio que viene ELEGIDO (candidatos = los del selector, en el orden del
 * catálogo): 1. el elegido en la evaluación; 2. si hay uno solo que pueda ser
 * plan, ese; 3. el más usado tras una evaluación (empate: el primero); 4. sin
 * historia, el único por sesiones; si no, ninguno.
 */
fun servicioSugerido(
    candidatos: List<ServicioDelPlan>, elegido: String? = null, usos: Map<String, Int> = emptyMap(), excluir: String? = null,
): String? {
    if (elegido != null && candidatos.any { it.id == elegido }) return elegido
    val planes = candidatos.filter { it.tipoCita == null && it.id != excluir }
    if (planes.size == 1) return planes[0].id
    var mejor: String? = null
    var maxUsos = 0
    for (c in planes) {
        val n = usos[c.id] ?: 0
        if (n > maxUsos) { maxUsos = n; mejor = c.id }
    }
    if (mejor != null) return mejor
    val porSesiones = planes.filter { modoDeServicioPlan(it) == "sesiones" }
    return porSesiones.singleOrNull()?.id
}

const val MODALIDAD_PAQUETE = "Paquete"
const val MODALIDAD_SUELTA = "Sesión suelta"

data class PlanPrellenado(val modalidad: String, val sesiones: Int)

/** Paquete si el catálogo tiene precio de paquete (tarifario de 10 o el primero; o precio de paquete); si no, suelta. */
fun planPrellenado(s: ServicioDelPlan): PlanPrellenado {
    if (s.tarifarios.isNotEmpty()) {
        val t = s.tarifarios.firstOrNull { it.first == 10 } ?: s.tarifarios[0]
        return PlanPrellenado(MODALIDAD_PAQUETE, t.first)
    }
    if ((s.precioPaquete ?: 0.0) > 0) return PlanPrellenado(MODALIDAD_PAQUETE, 10)
    return PlanPrellenado(MODALIDAD_SUELTA, 10)
}

/** Lista de un paquete de N: el tarifario de exactamente N; sin tarifarios, el precio de paquete. */
fun precioListaPaquete(s: ServicioDelPlan?, n: Int?): Double? {
    if (s == null) return null
    if (s.tarifarios.isNotEmpty()) return s.tarifarios.firstOrNull { it.first == n }?.second
    return s.precioPaquete?.takeIf { it > 0 }
}

/** El precio de LISTA del plan: el paquete de N, o una sesión. */
fun listaDelPlan(s: ServicioDelPlan?, modalidad: String, sesiones: Int): Double? {
    if (s == null) return null
    if (modalidad == MODALIDAD_PAQUETE) return precioListaPaquete(s, sesiones)
    return s.precio?.takeIf { it > 0 }
}

/** Lo que se guarda (las columnas del tratamiento) y el precio de referencia del cobro. */
data class ValoresPlan(
    val procedimientoId: String,
    val terapeutaId: String?,
    val modalidad: String,
    val totalSesiones: Int,
    val precioPaquete: Double?,
    val precioPorSesion: Double?,
    val precioAcordado: Double?,
    val diagnostico: String?,
    val citaOrigenId: String?,
    val fechaInicio: String,
    /** El paquete (acordado o lista); en suelta, una sesión. */
    val total: Double,
)

sealed class ResultadoValoresPlan {
    data class Ok(val valores: ValoresPlan) : ResultadoValoresPlan()
    data class Error(val mensaje: String) : ResultadoValoresPlan()
}

/**
 * Las MISMAS reglas que la rama «por sesiones» de TratamientoForm (web):
 *  - Paquete: precio_paquete = la lista (o lo escrito si no hay lista); el
 *    acordado = lo escrito solo si difiere de la lista. Sin lista ni precio no se crea.
 *  - Sesión suelta: total_sesiones 1, precio_por_sesion = lo escrito o el del servicio.
 * `precio` = el TEXTO del campo (vacío = la lista).
 */
fun valoresDelPlan(
    servicio: ServicioDelPlan, modalidad: String, sesiones: String, precio: String,
    diagnostico: String?, terapeutaId: String?, citaOrigenId: String?, hoy: String, moneda: String? = null,
): ResultadoValoresPlan {
    val n = sesiones.trim().toDoubleOrNull()?.let { floor(it).toInt() } ?: 0
    if (modalidad == MODALIDAD_PAQUETE && n !in 1..100) return ResultadoValoresPlan.Error("Indica cuántas sesiones (1 a 100).")
    val lectura = leerPrecio(precio, moneda)
    if (!lectura.ok) return ResultadoValoresPlan.Error("Precio: ${lectura.error}")
    val escrito = lectura.valor
    val lista = listaDelPlan(servicio, modalidad, n)
    val diag = diagnostico?.trim()?.ifBlank { null }
    val ter = terapeutaId?.ifBlank { null }
    val origen = citaOrigenId?.ifBlank { null }
    val porSesionServicio = servicio.precio
    if (modalidad == MODALIDAD_PAQUETE) {
        if (escrito == null && lista == null) return ResultadoValoresPlan.Error("Pon el precio del paquete. Si es gratis, escribe 0.")
        val paquete = lista ?: escrito
        val acordado = if (escrito != null && (lista == null || abs(escrito - lista) > 0.005)) escrito else null
        return ResultadoValoresPlan.Ok(ValoresPlan(
            procedimientoId = servicio.id, terapeutaId = ter, modalidad = MODALIDAD_PAQUETE, totalSesiones = n,
            precioPaquete = paquete, precioPorSesion = porSesionServicio,
            precioAcordado = if (lista == null) null else acordado,
            diagnostico = diag, citaOrigenId = origen, fechaInicio = hoy,
            total = redondearPrecio(acordado ?: paquete ?: 0.0, moneda),
        ))
    }
    val porSesion = escrito ?: porSesionServicio
    return ResultadoValoresPlan.Ok(ValoresPlan(
        procedimientoId = servicio.id, terapeutaId = ter, modalidad = MODALIDAD_SUELTA, totalSesiones = 1,
        precioPaquete = null, precioPorSesion = porSesion, precioAcordado = null,
        diagnostico = diag, citaOrigenId = origen, fechaInicio = hoy,
        total = redondearPrecio(porSesion ?: 0.0, moneda),
    ))
}

/** El abono que se registra: lo elegido, nunca más de lo que falta. 0 = nada. */
fun abonoAlPlan(elegido: Double?, saldoReal: Double?): Double {
    val e = elegido?.takeIf { it.isFinite() && it > 0 } ?: return 0.0
    if (saldoReal == null || !saldoReal.isFinite()) return redondeoJs(e * 100) / 100
    return max(0.0, redondeoJs(min(e, saldoReal) * 100) / 100)
}

private val DIAS_IDX = listOf("domingo", "lunes", "martes", "miercoles", "jueves", "viernes", "sabado")
private fun sinTildesDia(s: String) = s.lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')

/**
 * Desde cuándo se arma el plan entero tras una atención: el siguiente día que la
 * clínica atiende después de ella (si fue un día pasado, desde hoy). Gemelo de
 * `fechaPrimeraSesionTrasAtencion` (lib/escalera-controles).
 */
fun desdePlanTrasAtencion(hoy: String, fechaAtencion: String?, horario: List<DiaAtencion>): String {
    val h = hoy.take(10)
    val a = fechaAtencion?.take(10).orEmpty()
    var f = if (a.isNotEmpty() && a > h) a else h
    val activos = horario.filter { it.activo }.map { sinTildesDia(it.dia) }.toSet()
    repeat(14) {
        f = sumarDiasIso(f, 1)
        if (activos.isEmpty() || DIAS_IDX[diaSemanaPlan(f)] in activos) return f
    }
    return sumarDiasIso(if (a.isNotEmpty() && a > h) a else h, 1)
}
