package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

/**
 * Una sesión de la lista global (módulo Sesiones, fuera de la ficha). Espeja la
 * tabla de useSesiones (web): incluye paciente y servicio del tratamiento.
 */
data class SesionGlobal(
    val id: String,
    val numero: Int,
    val fecha: String,
    val hora: String?,
    val estado: String,
    val costo: Double?,
    val notas: String?,
    val mejorias: String?,
    val duracion: Int?,
    val motivoEstado: String?,
    val terapeutaId: String?,
    val terapeutaNombre: String?,
    val tratamientoId: String?,
    val pacienteId: String?,
    val pacienteNombre: String?,
    val procedimiento: String?,
    val modalidad: String?,
    val precioPorSesion: Double?,
    val precioAcordado: Double?,
) {
    val pendiente: Boolean
        get() = estado == "Planificada" || estado == "En progreso" || estado == "Reprogramada"

    /**
     * Costo a mostrar (igual que la web): si es Paquete no se muestra costo por
     * sesión; si es suelta, el costo de la sesión o, en su defecto, el del tratamiento.
     */
    val costoMostrar: Double?
        get() = if (modalidad == "Paquete") null
        else (costo?.takeIf { it > 0 } ?: precioAcordado ?: precioPorSesion)
}

/**
 * Lista global de sesiones de la clínica (RLS de staff acota a la clínica activa).
 * Las escrituras (completar/estado/editar/reasignar) reusan PacientesRepo, que
 * llama a los mismos endpoints de /api/staff/sesion — una sola fuente de verdad.
 */
object SesionesRepo {

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.int(k: String): Int? =
        (this[k] as? JsonPrimitive)?.content?.toIntOrNull()
    private fun JsonObject.dbl(k: String): Double? =
        (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()

    private const val SELECT = """
        id, numero, fecha, hora, estado, costo, notas, mejorias, duracion, motivo_estado, terapeuta_id,
        terapeuta:terapeutas(nombre),
        tratamiento:tratamientos(
            id, modalidad, precio_por_sesion, precio_acordado,
            paciente:pacientes(id, nombre),
            procedimiento:procedimientos(nombre)
        )
    """

    /**
     * Lista las sesiones (fecha desc, número desc). [soloTerapeutaId] (scope del
     * profesional vinculado): si no es null, filtra en memoria a sus sesiones,
     * igual que la web (`s.terapeuta_id === miTerapeutaId`).
     */
    suspend fun listar(soloTerapeutaId: String? = null, limite: Int = 400): List<SesionGlobal> {
        val filas = Supabase.client.postgrest["sesiones"]
            .select(Columns.raw(SELECT)) {
                order("fecha", Order.DESCENDING)
                order("numero", Order.DESCENDING)
                limit(limite.toLong())
            }
            .decodeList<JsonObject>()
        val sesiones = filas.mapNotNull { mapear(it) }
        return if (soloTerapeutaId == null) sesiones
        else sesiones.filter { it.terapeutaId == soloTerapeutaId }
    }

    private fun mapear(o: JsonObject): SesionGlobal? {
        val id = o.str("id") ?: return null
        val trat = o["tratamiento"] as? JsonObject
        val paciente = trat?.get("paciente") as? JsonObject
        val proc = trat?.get("procedimiento") as? JsonObject
        val terap = o["terapeuta"] as? JsonObject
        return SesionGlobal(
            id = id,
            numero = o.int("numero") ?: 0,
            fecha = o.str("fecha") ?: "",
            hora = o.str("hora"),
            estado = o.str("estado") ?: "Planificada",
            costo = o.dbl("costo"),
            notas = o.str("notas"),
            mejorias = o.str("mejorias"),
            duracion = o.int("duracion"),
            motivoEstado = o.str("motivo_estado"),
            terapeutaId = o.str("terapeuta_id"),
            terapeutaNombre = terap?.str("nombre"),
            tratamientoId = trat?.str("id"),
            pacienteId = paciente?.str("id"),
            pacienteNombre = paciente?.str("nombre"),
            procedimiento = proc?.str("nombre"),
            modalidad = trat?.str("modalidad"),
            precioPorSesion = trat?.dbl("precio_por_sesion"),
            precioAcordado = trat?.dbl("precio_acordado"),
        )
    }

    /**
     * Lo que necesita el diálogo de completar (el MISMO de la ficha) para una sesión
     * de la lista global: la sesión como la ve la ficha (con su pago), la anterior
     * (referencia de evolución y técnicas a repetir), las técnicas del plan y la
     * especialidad del servicio (¿dental? → "¿Qué se le hizo hoy?"). Dos lecturas
     * en paralelo; solo al tocar ✓ Completar.
     */
    suspend fun contextoCompletar(s: SesionGlobal): ContextoCompletar? = kotlinx.coroutines.coroutineScope {
        val tratId = s.tratamientoId ?: return@coroutineScope null
        val sesionesD = async { PacientesRepo.sesionesDe(tratId) }
        val tratD = async {
            runCatching {
                Supabase.client.postgrest["tratamientos"]
                    .select(Columns.raw("tecnicas_sugeridas, procedimiento:procedimientos(especialidad_id)")) {
                        filter { eq("id", tratId) }
                    }
                    .decodeList<JsonObject>().firstOrNull()
            }.getOrNull()
        }
        val sesiones = sesionesD.await()
        val actual = sesiones.firstOrNull { it.id == s.id } ?: return@coroutineScope null
        val anterior = sesiones.filter { it.numero < actual.numero }.maxByOrNull { it.numero }
        val trat = tratD.await()
        ContextoCompletar(
            ses = actual,
            anterior = anterior,
            tecnicasSugeridas = trat?.str("tecnicas_sugeridas"),
            especialidadId = (trat?.get("procedimiento") as? JsonObject)?.str("especialidad_id"),
        )
    }

    /** Servicios distintos presentes en las sesiones (para el filtro). */
    fun serviciosDe(sesiones: List<SesionGlobal>): List<String> =
        sesiones.mapNotNull { it.procedimiento }.distinct().sorted()
}

/** Datos para abrir el diálogo de completar de la ficha desde la lista global de sesiones. */
data class ContextoCompletar(
    val ses: SesionFicha,
    val anterior: SesionFicha?,
    val tecnicasSugeridas: String?,
    val especialidadId: String?,
)
