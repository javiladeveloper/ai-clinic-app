package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.MedicamentoReceta
import pe.saniape.app.data.PrescriptorReceta
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.offline.CacheLectura

/**
 * Una receta del paciente vista por el STAFF (tabla `recetas`, espejo de `Receta`
 * de lib/recetas.ts). A diferencia del portal, llegan también las ANULADAS: el
 * historial de la ficha las muestra tachadas con su motivo.
 */
data class RecetaStaff(
    val id: String,
    val numero: Int?,
    val fecha: String,
    val validaHasta: String,
    val estado: String,
    val motivoAnulacion: String?,
    val diagnostico: String?,
    val cie10: String?,
    val indicacionesGenerales: String?,
    val prescriptor: PrescriptorReceta?,
    val establecimiento: String?,
    val items: List<MedicamentoReceta>,
) {
    val anulada: Boolean get() = estado == "Anulada"
    val numeroTexto: String get() = formatearNumeroReceta(numero)
}

/** Columnas que la ficha necesita (no el `*` de la web: sin clinica_id ni datos del paciente). */
const val SELECT_RECETAS_STAFF =
    "id, numero, fecha, valida_hasta, estado, motivo_anulacion, diagnostico, cie10, " +
        "indicaciones_generales, items, prescriptor, establecimiento"

private fun JsonObject.s(k: String): String? =
    (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }

/**
 * Fila de `recetas` → RecetaStaff. Tolerante: una receta sin id se descarta; un
 * ítem sin DCI también (la base lo exige, pero una fila rara no rompe la lista).
 * La cantidad va como "21 tabletas" (la web la imprime además en letras).
 */
fun aRecetaStaff(o: JsonObject): RecetaStaff? {
    val id = o.s("id") ?: return null
    val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { e ->
        val it = e as? JsonObject ?: return@mapNotNull null
        val dci = it.s("dci") ?: return@mapNotNull null
        val cantidad = it.s("cantidad")?.toDoubleOrNull()?.let { n -> decimal(n) }
        MedicamentoReceta(
            dci = dci,
            marca = it.s("marca"),
            concentracion = it.s("concentracion") ?: "",
            forma = it.s("forma") ?: "",
            via = it.s("via") ?: "",
            dosis = it.s("dosis") ?: "",
            frecuencia = it.s("frecuencia") ?: "",
            duracion = it.s("duracion") ?: "",
            cantidadTexto = listOfNotNull(cantidad, it.s("unidad")).joinToString(" "),
            indicaciones = it.s("indicaciones"),
            indicacionTexto = "",
        )
    }
    val p = o["prescriptor"] as? JsonObject
    return RecetaStaff(
        id = id,
        numero = o.s("numero")?.toDoubleOrNull()?.toInt(),
        fecha = o.s("fecha")?.take(10) ?: "",
        validaHasta = o.s("valida_hasta")?.take(10) ?: "",
        estado = o.s("estado") ?: "Emitida",
        motivoAnulacion = o.s("motivo_anulacion"),
        diagnostico = o.s("diagnostico"),
        cie10 = o.s("cie10"),
        indicacionesGenerales = o.s("indicaciones_generales"),
        prescriptor = p?.s("nombre")?.let { nombre ->
            PrescriptorReceta(
                nombre = nombre,
                colegiatura = p.s("colegiatura") ?: "",
                profesion = p.s("profesion"),
                especialidad = p.s("especialidad"),
            )
        },
        establecimiento = (o["establecimiento"] as? JsonObject)?.s("nombre"),
        items = items,
    )
}

/**
 * Recetas del paciente para la ficha (solo LECTURA en la app: emitir y anular
 * se hacen en la web). Mismo orden que `listarRecetasPaciente`: la más nueva
 * primero. Con respaldo local por paciente. null = no se pudo y no hay respaldo.
 */
object RecetasStaffRepo {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun recetasDe(pacienteId: String): List<RecetaStaff>? {
        val clave = "recetas-staff:$pacienteId"
        val filas: List<JsonObject> = try {
            Supabase.client.postgrest["recetas"]
                .select(Columns.raw(SELECT_RECETAS_STAFF)) {
                    filter { eq("paciente_id", pacienteId) }
                    order("numero", Order.DESCENDING)
                    limit(200)
                }
                .decodeList<JsonObject>()
                .also { CacheLectura.guardar(clave, Json.encodeToString(JsonArray.serializer(), JsonArray(it))) }
        } catch (e: Exception) {
            val crudo = CacheLectura.leer(clave) ?: return null
            runCatching { json.parseToJsonElement(crudo).jsonArray.map { it.jsonObject } }.getOrNull() ?: return null
        }
        return filas.mapNotNull { aRecetaStaff(it) }
    }

    /**
     * Solo en una clínica MIXTA (fisio + medicina…): ¿el paciente ya tiene alguna
     * receta? y ¿de qué especialidades es quien mira? — lo que le falta a
     * `pacienteRecibeRecetas` además de las citas y tratamientos que la ficha ya
     * tiene. En paralelo, livianas (una fila como máximo / las del profesional).
     */
    suspend fun datosMixta(pacienteId: String, miTerapeutaId: String?): Pair<Boolean, List<String>> =
        kotlinx.coroutines.coroutineScope {
            val tiene = async {
                runCatching {
                    Supabase.client.postgrest["recetas"]
                        .select(Columns.list("id")) { filter { eq("paciente_id", pacienteId) }; limit(1) }
                        .decodeList<JsonObject>().isNotEmpty()
                }.getOrDefault(false)
            }
            val mira = async {
                if (miTerapeutaId == null) emptyList() else runCatching {
                    Supabase.client.postgrest["terapeuta_especialidades"]
                        .select(Columns.list("especialidad_id")) { filter { eq("terapeuta_id", miTerapeutaId) } }
                        .decodeList<JsonObject>().mapNotNull { it.s("especialidad_id") }
                }.getOrDefault(emptyList())
            }
            tiene.await() to mira.await()
        }
}
