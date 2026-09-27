package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.offline.CacheLectura
import pe.saniape.app.data.offline.ResultadoEscritura
import pe.saniape.app.data.offline.enviarOEncolarDetalle

/** Lo que el formulario de triaje precarga: la atención de la cita + edad y alergias del paciente. */
data class DatosTriaje(
    /** Valores guardados por columna (texto listo para el campo). */
    val valores: Map<String, String>,
    val motivo: String?,
    /** "09:12 por Lic. Ana" si ya se tomó un triaje a esta cita. */
    val yaTenia: String?,
    val edad: Int?,
    val alergias: String?,
)

/**
 * Sala de espera, triaje e historial de signos vitales (staff).
 *
 * LECTURAS directas a Supabase con el Bearer del staff (la RLS acota a la
 * clínica), con las mismas columnas que la web (lib/llegadas-dia.ts,
 * lib/historial-vitales.ts). ESCRITURAS por /api/staff/atencion/{llegada,triaje}
 * — la web decide qué se escribe (quién tomó el triaje, abrir la HC, no pisar
 * el motivo) — con la cola offline: sin señal quedan guardadas en el teléfono y
 * suben solas. Llegar dos veces o repetir el triaje no duplica nada (la web
 * escribe "solo si falta" y la cola manda su idempotency_key).
 */
object AtencionMedicaRepo {

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.txt(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }

    /**
     * Llegada/triaje/consulta de las citas de UN día, por cita. Por FECHA (no por
     * las ids) para ir en paralelo con la agenda. Con respaldo local: sin señal se
     * pinta lo último visto. null = no se pudo leer y no hay respaldo (la agenda
     * conserva lo que tenía).
     */
    suspend fun llegadasDelDia(fecha: String): Map<String, AtencionLlegada>? {
        val clave = "llegadas:$fecha"
        val filas: List<JsonObject> = try {
            Supabase.client.postgrest["atenciones_clinicas"]
                .select(Columns.raw(COLUMNAS_LLEGADA)) {
                    filter {
                        eq("fecha", fecha)
                        filterNot("cita_id", FilterOperator.IS, "null")
                    }
                    limit(500)
                }
                .decodeList<JsonObject>()
                .also { CacheLectura.guardar(clave, Json.encodeToString(JsonArray.serializer(), JsonArray(it))) }
        } catch (e: Exception) {
            val crudo = CacheLectura.leer(clave) ?: return null
            runCatching { json.parseToJsonElement(crudo).jsonArray.map { it.jsonObject } }.getOrNull() ?: return null
        }
        return filas.mapNotNull { aAtencionLlegada(it) }.associateBy { it.citaId }
    }

    /** "🔔 Llegó": el paciente pasa a la sala de espera. */
    suspend fun marcarLlegada(citaId: String): ResultadoEscritura =
        enviarOEncolarDetalle("atencion:llegada", "/api/staff/atencion/llegada", buildJsonObject { put("citaId", citaId) })

    /** "🩺 Triaje": signos vitales y medidas de la cita (solo las columnas que mide la clínica). */
    suspend fun guardarTriaje(citaId: String, vitales: JsonObject, motivo: String?): ResultadoEscritura =
        enviarOEncolarDetalle("atencion:triaje", "/api/staff/atencion/triaje", buildJsonObject {
            put("citaId", citaId)
            put("vitales", vitales)
            motivo?.trim()?.takeIf { it.isNotEmpty() }?.let { put("motivo", it) }
        })

    /**
     * Precarga del formulario de triaje: la atención de la cita (para corregir un
     * triaje ya tomado) y la edad/alergias del paciente, en paralelo. Sin señal
     * devuelve lo que se pudo (el formulario abre vacío y se puede guardar igual).
     */
    suspend fun datosTriaje(citaId: String, pacienteId: String?, hoyIso: String): DatosTriaje = coroutineScope {
        val aD = async {
            runCatching {
                Supabase.client.postgrest["atenciones_clinicas"]
                    .select(Columns.raw(
                        "triaje_at, triaje_por_nombre, motivo_consulta, " + (CAMPOS_VITALES + "perimetro_abdominal").joinToString(", ")
                    )) {
                        filter { eq("cita_id", citaId) }
                        limit(1)
                    }
                    .decodeList<JsonObject>().firstOrNull()
            }.getOrNull()
        }
        val pD = async {
            if (pacienteId == null) null else runCatching {
                Supabase.client.postgrest["pacientes"]
                    .select(Columns.raw("fecha_nacimiento, edad, alergias")) {
                        filter { eq("id", pacienteId) }
                        limit(1)
                    }
                    .decodeList<JsonObject>().firstOrNull()
            }.getOrNull()
        }
        val a = aD.await()
        val p = pD.await()
        val valores = buildMap {
            for (k in CAMPOS_VITALES + "perimetro_abdominal") {
                a?.txt(k)?.toDoubleOrNull()?.let { put(k, decimalCampo(it)) }
            }
        }
        val yaTenia = a?.txt("triaje_at")?.let { at ->
            val hora = parsearInstante(at)?.let { horaClinica(it) }
            listOfNotNull(hora, a.txt("triaje_por_nombre")?.let { "por $it" }).joinToString(" ").ifBlank { null }
        }
        DatosTriaje(
            valores = valores,
            motivo = a?.txt("motivo_consulta"),
            yaTenia = yaTenia,
            edad = p?.let { edadDe(it.txt("fecha_nacimiento"), it.txt("edad")?.toIntOrNull(), hoyIso) },
            alergias = p?.txt("alergias"),
        )
    }

    /**
     * Tomas de signos vitales del paciente en orden CRONOLÓGICO (la última al
     * final). UNA consulta, igual que `historialVitales` de la web. Con respaldo
     * local por paciente. null = no se pudo leer y no hay respaldo.
     */
    suspend fun historialVitales(pacienteId: String): List<TomaVital>? {
        val clave = "vitales:$pacienteId"
        val filas: List<JsonObject> = try {
            Supabase.client.postgrest["atenciones_clinicas"]
                .select(Columns.raw(SELECT_TOMAS_VITALES)) {
                    filter {
                        eq("paciente_id", pacienteId)
                        // Alguna medida cargada: una atención solo con anamnesis no es una "toma".
                        or {
                            CAMPOS_NUMERICOS_TOMA.filter { it != "imc" }.forEach { filterNot(it, FilterOperator.IS, "null") }
                        }
                    }
                    order("fecha", Order.DESCENDING)
                    order("hora", Order.DESCENDING, nullsFirst = false)
                    limit(MAX_TOMAS.toLong())
                }
                .decodeList<JsonObject>()
                .also { CacheLectura.guardar(clave, Json.encodeToString(JsonArray.serializer(), JsonArray(it))) }
        } catch (e: Exception) {
            val crudo = CacheLectura.leer(clave) ?: return null
            runCatching { json.parseToJsonElement(crudo).jsonArray.map { it.jsonObject } }.getOrNull() ?: return null
        }
        return filas.mapNotNull { aTomaVital(it) }.reversed()
    }
}

/** Valor guardado → texto del campo ("120", "36.5"). */
private fun decimalCampo(n: Double): String = decimal(n)

/** "09:12" en la zona de la clínica. */
private fun horaClinica(i: kotlinx.datetime.Instant): String {
    val l = i.toLocalDateTime(ZONA_CLINICA)
    return "${l.hour.toString().padStart(2, '0')}:${l.minute.toString().padStart(2, '0')}"
}
