package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * UN tratamiento con varios servicios (web 10/11/2026, tabla `tratamiento_servicios`).
 * Gemelo de lib/tratamiento-servicios.ts (`nombreTratamiento`): con 2+ líneas el
 * tratamiento se llama «OCT + Campimetría»; si no, el nombre de su servicio de siempre.
 *
 * App 2.71: también se CREAN (desde la Nueva cita) y se EDITAN (pago único sin
 * atender), como el TratamientoForm web. El servidor (`/api/staff/tratamiento/accion`,
 * `servicios`) pone el principal y el total: aquí solo se arma la lista.
 */
object TratamientoServicios {
    /** El select anidado de las líneas (va dentro de un `tratamientos(...)`). */
    const val SELECT_LINEAS = "servicios:tratamiento_servicios(orden, procedimiento_id, precio_acordado, duracion, procedimiento:procedimientos(nombre))"

    fun nombre(tratamiento: JsonObject?, porDefecto: String?): String? {
        val lineas = (tratamiento?.get("servicios") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return porDefecto
        if (lineas.size < 2) return porDefecto
        val nombres = lineas
            .sortedBy { (it["orden"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 }
            .mapNotNull { l ->
                ((l["procedimiento"] as? JsonObject)?.get("nombre") as? JsonPrimitive)?.content
                    ?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            }
        return if (nombres.isEmpty()) porDefecto else nombres.joinToString(" + ")
    }

    /** Las líneas guardadas de un tratamiento (para editarlas), en orden. */
    fun lineasGuardadas(tratamiento: JsonObject?): List<LineaServicio> =
        ((tratamiento?.get("servicios") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList())
            .sortedBy { (it["orden"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 }
            .mapNotNull { l ->
                val id = (l["procedimiento_id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
                    ?: return@mapNotNull null
                LineaServicio(
                    procedimientoId = id,
                    nombre = ((l["procedimiento"] as? JsonObject)?.get("nombre") as? JsonPrimitive)?.content
                        ?.takeIf { it != "null" },
                    precioLista = null,
                    precioAcordado = (l["precio_acordado"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
                    duracion = (l["duracion"] as? JsonPrimitive)?.content?.toIntOrNull(),
                )
            }
}

/** Una línea del tratamiento tal como va al servidor. */
data class LineaServicio(
    val procedimientoId: String,
    val nombre: String? = null,
    val precioLista: Double?,
    val precioAcordado: Double,
    val duracion: Int?,
)

/** Una fila «＋ Agregar otro servicio» mientras se escribe (precio vacío = el de lista o el de la campaña). */
data class FilaOtroServicio(val procedimientoId: String = "", val precio: String = "", val duracion: Int = 30)

/** Duraciones que se ofrecen por servicio agregado (igual que la web). */
val DURACIONES_OTRO_SERVICIO = listOf(10, 15, 20, 30, 45, 60, 90, 120)

/**
 * Qué servicios se pueden agregar a un pago único: otros de PAGO ÚNICO, de la
 * MISMA especialidad que el principal (Jonathan 10/11), sin el principal y sin
 * los tipos de consulta (esos no nacen tratamiento). Gemelo de `candidatosExtra`.
 */
fun candidatosOtroServicio(procs: List<ProcedimientoRef>, principal: ProcedimientoRef?): List<ProcedimientoRef> {
    if (principal == null) return emptyList()
    return procs.filter { p ->
        p.id != principal.id && p.tipoCita == null &&
            tipoTratamientoDe(p) == TipoTratamientoNuevo.SERVICIO_UNICO &&
            (principal.especialidadId == null || p.especialidadId == principal.especialidadId)
    }
}

/** ¿Se ofrece la fila de servicios? Pago único, no dental (los dentales van por piezas). */
fun ofreceOtrosServicios(tipo: TipoTratamientoNuevo?, principal: ProcedimientoRef?, mapa: MapaDental): Boolean =
    tipo == TipoTratamientoNuevo.SERVICIO_UNICO && principal != null &&
        !(mapa.activo && esServicioDental(principal.especialidadId, mapa))

/** El precio sugerido de un servicio agregado: el de una campaña de precio vigente, o null. */
fun precioCampaniaOtroServicio(camps: List<CampaniaApp>, p: ProcedimientoRef): Pair<CampaniaApp, Double>? {
    if (p.precio <= 0) return null
    val c = CatalogosCobroRepo.paraProcedimiento(camps, p.id).firstOrNull { !it.esPaquete } ?: return null
    return c to c.precioCon(p.precio)
}

/**
 * Las líneas completas (principal + filas elegidas). Sin filas elegidas → vacía:
 * el tratamiento de siempre. Precio vacío = el de la campaña o el de lista.
 */
fun lineasDelFormulario(
    principal: LineaServicio,
    filas: List<FilaOtroServicio>,
    procs: List<ProcedimientoRef>,
    camps: List<CampaniaApp> = emptyList(),
): List<LineaServicio> {
    val elegidas = filas.filter { it.procedimientoId.isNotBlank() && it.procedimientoId != principal.procedimientoId }
        .distinctBy { it.procedimientoId }
    if (elegidas.isEmpty()) return emptyList()
    return listOf(principal) + elegidas.map { f ->
        val p = procs.find { it.id == f.procedimientoId }
        val sugerido = p?.let { precioCampaniaOtroServicio(camps, it)?.second } ?: p?.precio ?: 0.0
        LineaServicio(
            procedimientoId = f.procedimientoId, nombre = p?.nombre, precioLista = p?.precio,
            precioAcordado = f.precio.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it >= 0 } ?: sugerido,
            duracion = f.duracion,
        )
    }
}

fun totalLineas(lineas: List<LineaServicio>): Double =
    kotlin.math.round(lineas.sumOf { it.precioAcordado } * 100) / 100

/** El `servicios` del cuerpo de crear/editar (camelCase, lo traduce el servidor). */
fun jsonLineas(lineas: List<LineaServicio>): JsonArray = buildJsonArray {
    lineas.forEach { l ->
        add(buildJsonObject {
            put("procedimientoId", l.procedimientoId)
            l.precioLista?.let { put("precioLista", it) }
            put("precioAcordado", l.precioAcordado)
            l.duracion?.let { put("duracion", it) }
        })
    }
}
