package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Lee los datos del portal del paciente directo de Supabase con anon key + RLS
 * (misma base que la web). La RLS de Postgres limita lo visible a SUS registros.
 *
 * Decodificamos a JsonElement y mapeamos a mano porque los SELECT con joins
 * anidados (procedimiento, clinica, terapeuta) no calzan con data classes planas.
 */
object PortalRepo {

    private fun JsonObject.str(key: String): String? {
        val v = this[key]
        if (v == null || v is JsonNull) return null
        val p = v as? JsonPrimitive ?: return null
        return p.content
    }

    private fun JsonObject.nested(key: String): JsonObject? = (this[key] as? JsonObject)

    /** IDs de los registros de paciente del usuario (RLS limita a los suyos). */
    private suspend fun misPacienteIds(): List<String> {
        val email = Supabase.client.auth.currentUserOrNull()?.email ?: return emptyList()
        val filas = Supabase.client.postgrest["pacientes"]
            .select(Columns.list("id, email")) {
                filter { eq("email", email) }
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull { it.str("id") }
    }

    // Slug de la clínica habitual del paciente (la de su cita más reciente con slug).
    // Lo usa la pantalla de reservar para precargar "tu clínica de siempre".
    var clinicaHabitualSlug: String? = null
        private set

    /** Citas del paciente con profesional/clínica, separadas en próximas y pasadas. */
    suspend fun misCitas(): Pair<List<CitaPortal>, List<CitaPortal>> {
        val pacIds = misPacienteIds()
        if (pacIds.isEmpty()) return emptyList<CitaPortal>() to emptyList()

        val filas = Supabase.client.postgrest["citas"]
            .select(
                Columns.raw(
                    "id, fecha, hora, estado, tipo, " +
                        "terapeuta:terapeutas(nombre), clinica:clinicas(nombre, slug)"
                )
            ) {
                filter { isIn("paciente_id", pacIds) }
                order("fecha", Order.ASCENDING)
                order("hora", Order.ASCENDING)
            }
            .decodeList<JsonObject>()

        val citas = filas.map { o ->
            CitaPortal(
                id = o.str("id") ?: "",
                fecha = o.str("fecha") ?: "",
                hora = o.str("hora") ?: "",
                estado = o.str("estado") ?: "",
                tipo = o.str("tipo"),
                profesional = o.nested("terapeuta")?.str("nombre"),
                clinica = o.nested("clinica")?.str("nombre"),
            )
        }
        // Clínica habitual = la de la cita más próxima (futura) con slug; si no hay
        // próximas, la de la cita más reciente. Las citas vienen ordenadas por fecha
        // ascendente, así que filtramos las de hoy en adelante primero.
        val hoyParaSlug = hoyIso()
        clinicaHabitualSlug =
            filas.firstOrNull { (it.str("fecha") ?: "") >= hoyParaSlug && it.nested("clinica")?.str("slug") != null }
                ?.nested("clinica")?.str("slug")
                ?: filas.reversed().firstNotNullOfOrNull { it.nested("clinica")?.str("slug") }

        // Hoy en adelante = próximas; antes = pasadas (orden inverso para historial).
        val hoy = hoyIso()
        val proximas = citas.filter { it.fecha >= hoy && it.estado != "Cancelada" }
        val pasadas = citas.filter { it.fecha < hoy || it.estado == "Cancelada" }.reversed()
        return proximas to pasadas
    }

    /** Tratamientos del paciente con progreso + timeline de sesiones. */
    suspend fun misTratamientos(): List<Tratamiento> {
        val pacIds = misPacienteIds()
        if (pacIds.isEmpty()) return emptyList()

        val trats = Supabase.client.postgrest["tratamientos"]
            .select(
                Columns.raw(
                    "id, modalidad, estado, total_sesiones, sesiones_completadas, fecha_inicio, " +
                        "procedimiento:procedimientos(nombre), clinica:clinicas(nombre)"
                )
            ) {
                filter { isIn("paciente_id", pacIds) }
                order("fecha_inicio", Order.DESCENDING)
            }
            .decodeList<JsonObject>()

        val tratIds = trats.mapNotNull { it.str("id") }
        val sesionesPorTrat: Map<String, List<SesionMin>> =
            if (tratIds.isEmpty()) emptyMap() else cargarSesiones(tratIds)

        return trats.map { t ->
            val id = t.str("id") ?: ""
            val modalidad = t.str("modalidad")
            val total = t.str("total_sesiones")?.toIntOrNull() ?: 0
            val usaSesiones = modalidad != "Consulta" && total > 0
            Tratamiento(
                id = id,
                procedimiento = t.nested("procedimiento")?.str("nombre") ?: "Tratamiento",
                clinica = t.nested("clinica")?.str("nombre"),
                estado = t.str("estado") ?: "",
                usaSesiones = usaSesiones,
                totalSesiones = if (usaSesiones) total else null,
                sesionesCompletadas = t.str("sesiones_completadas")?.toIntOrNull() ?: 0,
                fechaInicio = t.str("fecha_inicio"),
                sesiones = (sesionesPorTrat[id] ?: emptyList())
                    .filter { it.estado != "Cancelada" && it.estado != "No asistió" },
            )
        }
    }

    private suspend fun cargarSesiones(tratIds: List<String>): Map<String, List<SesionMin>> {
        val ses = Supabase.client.postgrest["sesiones"]
            .select(Columns.list("tratamiento_id, numero, fecha, estado")) {
                filter { isIn("tratamiento_id", tratIds) }
                order("numero", Order.ASCENDING)
                limit(2000)
            }
            .decodeList<JsonObject>()
        val map = mutableMapOf<String, MutableList<SesionMin>>()
        for (o in ses) {
            val tid = o.str("tratamiento_id") ?: continue
            map.getOrPut(tid) { mutableListOf() }.add(
                SesionMin(
                    numero = o.str("numero")?.toIntOrNull() ?: 0,
                    fecha = o.str("fecha") ?: "",
                    estado = o.str("estado") ?: "",
                )
            )
        }
        return map
    }

    /** Fecha de hoy en formato ISO (yyyy-MM-dd) sin depender de java.time en common. */
    private fun hoyIso(): String {
        val now = Clock.System.now()
        val d = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val m = d.monthNumber.toString().padStart(2, '0')
        val day = d.dayOfMonth.toString().padStart(2, '0')
        return "${d.year}-$m-$day"
    }
}