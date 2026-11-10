package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.offline.OpPendiente

/**
 * Reglas puras para crear tratamientos desde el presupuesto dental (Fase 1,
 * críticos de dinero: .superpowers/sdd/dental-fase1-reglas.md). Sin Compose ni
 * red, para probarlas enteras.
 */

/** El servidor (R4) ya ató esas piezas a otro tratamiento: no se creó nada. */
const val CODIGO_PIEZAS_YA_PRESUPUESTADAS = "PIEZAS_YA_PRESUPUESTADAS"
const val MENSAJE_PIEZAS_YA_PRESUPUESTADAS = "Estas piezas ya tienen un tratamiento: actualiza el presupuesto"

private const val ENDPOINT_TRATAMIENTO = "/api/staff/tratamiento/accion"

/**
 * Hallazgo de PIEZA guardado en "BOCA": no tiene pieza, así que no se puede
 * cobrar (R2). Solo los hallazgos `porBoca` van a la boca entera. Gemelo de la
 * condición de `agruparPresupuesto` en lib/odontograma.ts (solo 'BOCA').
 */
fun esHallazgoSinPieza(r: DienteHallazgo, hal: HallazgoDental?): Boolean =
    hal != null && !hal.porBoca && r.diente == "BOCA"

/**
 * Lo que el presupuesto puede ofrecer: sin tratamiento y fuera de [excluidos]
 * (lo que está en la cola sin señal o se acaba de crear y la vista aún no se
 * recargó). Así lo encolado no se vuelve a ofrecer ni se crea dos veces.
 */
fun libresParaPresupuesto(hallazgos: List<DienteHallazgo>, excluidos: Set<String>): List<DienteHallazgo> =
    hallazgos.filter { it.tratamientoId == null && it.id !in excluidos }

/**
 * Las líneas y extras sin precio (S/ 0): no se crea ninguno (R5). Un tratamiento
 * en S/ 0 queda «pago pendiente» para siempre y no se cobra nunca.
 */
fun nombresSinPrecio(lineas: List<LineaPresupuesto>, extras: List<ProcedimientoRef>): List<String> =
    lineas.filter { it.subtotal <= 0.0 }.map { it.nombre } + extras.filter { it.precio <= 0.0 }.map { it.nombre }

/** Un «crear tratamiento» que espera señal en la cola local. */
data class TratamientoEnCola(
    val procedimientoId: String?,
    val diagnostico: String?,
    val precioAcordado: Double?,
    val hallazgoIds: List<String>,
)

/**
 * Los «crear tratamiento» con piezas del odontograma de [pacienteId] que siguen
 * en la cola offline. Se leen del payload encolado: sobrevive a cerrar la app.
 */
fun tratamientosEnCola(ops: List<OpPendiente>, pacienteId: String): List<TratamientoEnCola> = ops.mapNotNull { op ->
    if (op.endpoint != ENDPOINT_TRATAMIENTO) return@mapNotNull null
    val o = runCatching { Json.parseToJsonElement(op.payload).jsonObject }.getOrNull() ?: return@mapNotNull null
    fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
    if (s("accion") != "crear" || s("pacienteId") != pacienteId) return@mapNotNull null
    val ids = (o["hallazgoIds"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    if (ids.isEmpty()) return@mapNotNull null
    TratamientoEnCola(s("procedimientoId"), s("diagnostico"), (o["precioAcordado"] as? JsonPrimitive)?.doubleOrNull, ids)
}

/** Las piezas que ya van en un «crear tratamiento» encolado (no se ofrecen otra vez). */
fun hallazgosEnCola(ops: List<OpPendiente>, pacienteId: String): Set<String> =
    tratamientosEnCola(ops, pacienteId).flatMap { it.hallazgoIds }.toSet()

/**
 * ¿El presupuesto que se ve quedó viejo para estas piezas? Sí, si al releer
 * alguna ya tiene tratamiento (lo creó la web, otro equipo u otro toque), ya no
 * existe, o ya va en la cola. [frescos] null = no se pudo releer (sin señal):
 * se deja seguir, la cola y el servidor (409) protegen.
 */
fun presupuestoDesactualizado(
    hallazgoIds: Collection<String>,
    frescos: List<DienteHallazgo>?,
    enCola: Set<String>,
): Boolean {
    if (hallazgoIds.any { it in enCola }) return true
    if (frescos == null) return false
    val porId = frescos.associateBy { it.id }
    return hallazgoIds.any { id -> porId[id]?.let { it.tratamientoId != null } ?: true }
}

/**
 * Marcas del presupuesto tras cambiar las líneas (R5: lo creado sale de la lista).
 * Solo las líneas NUEVAS (no vistas antes) entran marcadas; lo que el odontólogo
 * desmarcó sigue desmarcado y lo que desapareció se olvida. Devuelve
 * (marcadas, vistas) para la siguiente vez.
 */
fun marcasTrasCambio(
    previas: Set<String>,
    vistas: Set<String>,
    actuales: List<String>,
): Pair<Set<String>, Set<String>> {
    val ahora = actuales.toSet()
    val nuevas = ahora - vistas
    return ((previas intersect ahora) + nuevas) to ahora
}

/** 409 con `parcial`: el tratamiento SÍ se creó (trae `id`) pero sus piezas no se pudieron atar ni deshacer. */
fun esCreacionParcial(status: Int, cuerpo: kotlinx.serialization.json.JsonObject?): Boolean =
    status == 409 && (cuerpo?.get("parcial") as? JsonPrimitive)?.contentOrNull == "true" &&
        !(cuerpo["id"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()
