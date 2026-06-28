package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** Un tratamiento del paciente (resumen para la lista/ficha). */
data class TratamientoPaciente(
    val id: String,
    val procedimiento: String?,
    val terapeutaId: String?,
    val terapeutaNombre: String?,
    val modalidad: String?,
    val estado: String?,
    val estadoPago: String?,
    val totalSesiones: Int,
    val sesionesCompletadas: Int,
)

/** Una sesión de un tratamiento (para la ficha). */
data class SesionFicha(
    val id: String,
    val numero: Int,
    val fecha: String,
    val hora: String?,
    val estado: String,
    val costo: Double?,
    val notas: String?,
    val motivoEstado: String?,
) {
    val pendiente: Boolean
        get() = estado == "Planificada" || estado == "En progreso" || estado == "Reprogramada"
}

/** Un paciente para la lista del staff (con sus tratamientos anidados). */
data class PacienteStaff(
    val id: String,
    val nombre: String,
    val dni: String?,
    val edad: Int?,
    val telefono: String?,
    val ocupacion: String?,
    val diagnostico: String?,
    val estado: String?,
    val flag: String?,          // verde / amarillo / rojo (semáforo)
    val tratamientos: List<TratamientoPaciente>,
) {
    /** Tratamientos en curso (Activo). */
    val tratamientosActivos: List<TratamientoPaciente>
        get() = tratamientos.filter { it.estado == "Activo" }
}

/**
 * Lee pacientes (lista + ficha) directo de Supabase con la RLS de staff. Espeja el
 * SELECT de usePacientes (web): pacientes + tratamientos(procedimiento, terapeuta).
 * Las escrituras complejas (crear paciente/tratamiento, pagos) van por endpoints.
 */
object PacientesRepo {

    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.int(k: String): Int? =
        (this[k] as? JsonPrimitive)?.content?.toIntOrNull()
    private fun JsonObject.dbl(k: String): Double? =
        (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()

    private const val SELECT = """
        id, nombre, dni, edad, telefono, ocupacion, diagnostico, estado, flag,
        tratamientos:tratamientos(
            id, modalidad, estado, estado_pago, total_sesiones, sesiones_completadas,
            terapeuta_id,
            procedimiento:procedimientos(nombre),
            terapeuta:terapeutas(id, nombre)
        )
    """

    /**
     * Lista de pacientes. [soloTerapeutaId] (scope del profesional vinculado): si no es
     * null, devuelve solo pacientes que tienen algún tratamiento con ese terapeuta.
     * El filtrado por scope se hace en memoria (igual que la web).
     */
    suspend fun listar(soloTerapeutaId: String? = null): List<PacienteStaff> {
        val filas = Supabase.client.postgrest["pacientes"]
            .select(Columns.raw(SELECT)) {
                order("created_at", Order.DESCENDING)
            }
            .decodeList<JsonObject>()
        val pacientes = filas.map { mapear(it) }
        return if (soloTerapeutaId == null) pacientes
        else pacientes.filter { p -> p.tratamientos.any { it.terapeutaId == soloTerapeutaId } }
    }

    /** Un paciente por id (para la ficha). */
    suspend fun porId(id: String): PacienteStaff? {
        val o = Supabase.client.postgrest["pacientes"]
            .select(Columns.raw(SELECT)) { filter { eq("id", id) } }
            .decodeList<JsonObject>().firstOrNull() ?: return null
        return mapear(o)
    }

    /** Sesiones de un tratamiento (para la ficha), ordenadas por número desc. */
    suspend fun sesionesDe(tratamientoId: String): List<SesionFicha> {
        val filas = Supabase.client.postgrest["sesiones"]
            .select(Columns.list("id, numero, fecha, hora, estado, costo, notas, motivo_estado, terapeuta_id")) {
                filter { eq("tratamiento_id", tratamientoId) }
                order("numero", Order.DESCENDING)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull { o ->
            SesionFicha(
                id = o.str("id") ?: return@mapNotNull null,
                numero = o.int("numero") ?: 0,
                fecha = o.str("fecha") ?: "",
                hora = o.str("hora"),
                estado = o.str("estado") ?: "Planificada",
                costo = o.dbl("costo"),
                notas = o.str("notas"),
                motivoEstado = o.str("motivo_estado"),
            )
        }
    }

    /** Cambia el estado de una sesión vía endpoint (sync cita + comisión + contador). */
    suspend fun cambiarEstadoSesion(
        sesionId: String, estado: String,
        motivo: String? = null, fecha: String? = null, hora: String? = null, notas: String? = null,
    ): Boolean {
        val tk = token() ?: return false
        val cuerpo = buildJsonObject {
            put("sesionId", sesionId)
            put("estado", estado)
            if (!motivo.isNullOrBlank()) put("motivo", motivo)
            if (!fecha.isNullOrBlank()) put("fecha", fecha)
            if (!hora.isNullOrBlank()) put("hora", hora)
            if (!notas.isNullOrBlank()) put("notas", notas)
        }
        val resp = http.post("${Supabase.SITE_URL}/api/staff/sesion/estado") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    /** Da de alta un tratamiento (paciente pasa a Alta si no le quedan otros en curso). */
    suspend fun darDeAlta(tratamientoId: String): Boolean {
        val tk = token() ?: return false
        val resp = http.post("${Supabase.SITE_URL}/api/staff/tratamiento/alta") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("tratamientoId", tratamientoId) }.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    private fun mapear(o: JsonObject): PacienteStaff {
        val trats = (o["tratamientos"] as? JsonArray ?: emptyList()).mapNotNull { rel ->
            val t = rel as? JsonObject ?: return@mapNotNull null
            val proc = (t["procedimiento"] as? JsonObject)?.str("nombre")
            val ter = t["terapeuta"] as? JsonObject
            TratamientoPaciente(
                id = t.str("id") ?: return@mapNotNull null,
                procedimiento = proc,
                terapeutaId = t.str("terapeuta_id"),
                terapeutaNombre = ter?.str("nombre"),
                modalidad = t.str("modalidad"),
                estado = t.str("estado"),
                estadoPago = t.str("estado_pago"),
                totalSesiones = t.int("total_sesiones") ?: 0,
                sesionesCompletadas = t.int("sesiones_completadas") ?: 0,
            )
        }
        return PacienteStaff(
            id = o.str("id") ?: "",
            nombre = o.str("nombre") ?: "Paciente",
            dni = o.str("dni"),
            edad = o.int("edad"),
            telefono = o.str("telefono"),
            ocupacion = o.str("ocupacion"),
            diagnostico = o.str("diagnostico"),
            estado = o.str("estado"),
            flag = o.str("flag"),
            tratamientos = trats,
        )
    }
}