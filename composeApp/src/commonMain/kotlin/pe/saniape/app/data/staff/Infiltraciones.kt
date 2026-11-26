package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add

/**
 * 💉 INFILTRACIONES ARTICULARES (25/11/2026, FISIOSALUD — reumatología). Gemelo de
 * `lib/infiltraciones.ts` (web): mismas sustancias, mismas validaciones y textos.
 * Una fila por articulación infiltrada; la articulación es un id del mapa articular
 * ([ARTICULACIONES_REUMA]). API: /api/staff/infiltracion (GET / POST / DELETE).
 */

/** «Corticoide + Anestésico» juntos es lo habitual: va primero y arranca elegido. */
val SUSTANCIAS_INFILTRACION = listOf(
    "Corticoide + Anestésico", "Corticoide", "Anestésico", "Ácido hialurónico", "PRP", "Colágeno", "Proloterapia", "Otra",
)
const val SUSTANCIA_INICIAL_INFILTRACION = "Corticoide + Anestésico"
const val INFILTRACION_DOSIS_MAX = 100.0
const val INFILTRACIONES_NO_DISPONIBLE = "Las infiltraciones aún no están disponibles en este servidor."

data class Infiltracion(
    val id: String,
    val articulacion: String,
    val sustancia: String,
    val producto: String? = null,
    val dosisMl: Double? = null,
    val guiaEcografica: Boolean = false,
    val eva: Int? = null,
    val lote: String? = null,
    val caducidad: String? = null,
    val observaciones: String? = null,
    val fecha: String = "",
    val citaId: String? = null,
    val createdAt: String? = null,
)

/** Lo que se llena en la pantalla (una o varias articulaciones con los mismos datos). */
data class BorradorInfiltracion(
    val articulaciones: List<String> = emptyList(),
    val sustancia: String = SUSTANCIA_INICIAL_INFILTRACION,
    val producto: String = "",
    val dosisMl: String = "",
    val eco: Boolean = false,
    val eva: Int? = null,
    val lote: String = "",
    val caducidad: String = "",
    val observaciones: String = "",
)

private val FECHA_ISO = Regex("""^\d{4}-\d{2}-\d{2}$""")
private fun numeroInfil(s: String): Double? = s.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: Double.NaN }

/** Lista de errores para el usuario (vacía = válido). Mismos textos que la web. */
fun validarInfiltracion(b: BorradorInfiltracion): List<String> {
    val e = mutableListOf<String>()
    if (b.articulaciones.isEmpty()) e += "Elige en el mapa las articulaciones que infiltraste."
    else if (b.articulaciones.any { articulacionReumaDe(it) == null }) e += "Elige la articulación en el mapa."
    val sus = b.sustancia.trim()
    if (sus.length < 2 || sus.length > 60) e += "Elige la sustancia aplicada."
    if (sus == "Otra" && b.producto.isBlank()) e += "Escribe cuál sustancia se aplicó."
    numeroInfil(b.dosisMl)?.let { d ->
        if (d.isNaN() || d <= 0 || d > INFILTRACION_DOSIS_MAX) e += "La dosis va en ml, entre 0 y ${INFILTRACION_DOSIS_MAX.toInt()}."
    }
    b.eva?.let { if (it !in 0..10) e += "El dolor (EVA) va de 0 a 10." }
    if (b.caducidad.isNotBlank() && !FECHA_ISO.matches(b.caducidad.trim())) e += "La caducidad no es una fecha válida."
    if (b.producto.trim().length > 120) e += "El nombre del producto es muy largo."
    if (b.lote.trim().length > 60) e += "El lote es muy largo."
    if (b.observaciones.trim().length > 2000) e += "Las observaciones son muy largas."
    return e
}

/** El cuerpo del POST (la web acepta `articulaciones[]`: una fila por cada una). */
fun cuerpoInfiltracion(pacienteId: String, citaId: String?, fecha: String, b: BorradorInfiltracion): JsonObject = buildJsonObject {
    put("pacienteId", pacienteId)
    if (citaId != null) put("citaId", citaId) else put("citaId", JsonNull)
    put("fecha", fecha)
    putJsonArray("articulaciones") { b.articulaciones.distinct().forEach { add(it) } }
    put("sustancia", b.sustancia.trim())
    b.producto.trim().takeIf { it.isNotEmpty() }?.let { put("producto", it) }
    numeroInfil(b.dosisMl)?.takeIf { !it.isNaN() }?.let { put("dosisMl", it) }
    put("guiaEcografica", b.eco)
    b.eva?.let { put("eva", it) }
    b.lote.trim().takeIf { it.isNotEmpty() }?.let { put("lote", it) }
    b.caducidad.trim().takeIf { it.isNotEmpty() }?.let { put("caducidad", it) }
    b.observaciones.trim().takeIf { it.isNotEmpty() }?.let { put("observaciones", it) }
}

private fun JsonObject.txtInfil(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

fun infiltracionDeJson(o: JsonObject): Infiltracion? {
    val id = o.txtInfil("id") ?: return null
    val art = o.txtInfil("articulacion") ?: return null
    return Infiltracion(
        id = id, articulacion = art, sustancia = o.txtInfil("sustancia") ?: "",
        producto = o.txtInfil("producto"),
        dosisMl = (o["dosis_ml"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() },
        guiaEcografica = (o["guia_ecografica"] as? JsonPrimitive)?.booleanOrNull ?: false,
        eva = (o["eva"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }?.toInt(),
        lote = o.txtInfil("lote"), caducidad = o.txtInfil("caducidad"), observaciones = o.txtInfil("observaciones"),
        fecha = o.txtInfil("fecha") ?: "", citaId = o.txtInfil("cita_id"), createdAt = o.txtInfil("created_at"),
    )
}

private fun dosisTxt(d: Double): String = (if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()).replace('.', ',')

/** «Rodilla derecha · Corticoide 1 ml · con guía ecográfica» — una línea del resumen. */
fun resumenInfiltracion(i: Infiltracion): String {
    val art = articulacionReumaDe(i.articulacion)?.nombre ?: i.articulacion
    val sus = if (i.producto != null) "${i.sustancia} (${i.producto})" else i.sustancia
    val dosis = i.dosisMl?.let { " ${dosisTxt(it)} ml" } ?: ""
    return "$art · $sus$dosis${if (i.guiaEcografica) " · con guía ecográfica" else ""}"
}

/** Las de ESA articulación fuera de esta consulta, la más reciente primero («ver infiltración anterior»). */
fun anterioresInfiltracion(lista: List<Infiltracion>, articulacion: String, citaId: String?): List<Infiltracion> =
    lista.filter { it.articulacion == articulacion && (citaId == null || it.citaId != citaId) }
        .sortedWith(compareByDescending<Infiltracion> { it.fecha }.thenByDescending { it.createdAt ?: "" })

fun vecesPorArticulacion(lista: List<Infiltracion>): Map<String, Int> = lista.groupingBy { it.articulacion }.eachCount()

/** Más reciente primero, como el GET. */
fun ordenarInfiltraciones(l: List<Infiltracion>): List<Infiltracion> =
    l.sortedWith(compareByDescending<Infiltracion> { it.fecha }.thenByDescending { it.createdAt ?: "" })

// ── ¿Cuándo aparece la pestaña? Gemelo de mapaInfiltraciones / accesoModuloEspecialidad (lib/flujo.ts) ──

/** Del contexto: `especialidadesInfiltraciones`, `soloInfiltraciones`, `especialidadesInfiltracionesApagadas`. */
data class MapaInfiltraciones(val ids: List<String> = emptyList(), val solo: Boolean = false, val apagadas: List<String> = emptyList())

enum class AccesoModulo { EDITAR, LEER }

fun esServicioInfiltracion(nombre: String?): Boolean = nombre?.contains("infiltr", ignoreCase = true) == true

/** Un servicio «Infiltración…» enciende el registro, salvo que su especialidad lo tenga APAGADO. */
fun servicioActivaInfiltracion(mapa: MapaInfiltraciones, nombre: String?, especialidadId: String?): Boolean =
    esServicioInfiltracion(nombre) && !(especialidadId != null && especialidadId in mapa.apagadas)

/**
 * EDITAR → encendido para este paciente; LEER → apagado pero ya tiene registros (la
 * historia no se esconde); null → sin pestaña.
 */
fun accesoInfiltraciones(
    mapa: MapaInfiltraciones,
    atenciones: List<AtencionFisio> = emptyList(),
    especialidadesDeQuienMira: List<String>? = null,
    tieneRegistros: Boolean = false,
    activoPorOtraVia: Boolean = false,
): AccesoModulo? {
    val activo = activoPorOtraVia || mapa.solo || (mapa.ids.isNotEmpty() && (
        atenciones.any { atencionEnMapaEspecialidad(mapa.ids, false, it.especialidadId, it.especialidadServicioId, it.especialidadesProfesional) } ||
            especialidadesDeQuienMira.orEmpty().any { it.isNotBlank() && it in mapa.ids }
        ))
    return if (activo) AccesoModulo.EDITAR else if (tieneRegistros) AccesoModulo.LEER else null
}

/** ¿Hay que preguntar si ya tiene registros? Solo si puede salir LEER sin estar encendido. */
fun infiltracionesNecesitaConteo(mapa: MapaInfiltraciones): Boolean = (mapa.ids.isNotEmpty() && !mapa.solo) || mapa.apagadas.isNotEmpty()
