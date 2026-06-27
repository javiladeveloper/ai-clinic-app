package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** Una cita del staff (lista de agenda), con joins de paciente/terapeuta/tratamiento. */
data class CitaStaff(
    val id: String,
    val fecha: String,
    val hora: String,
    val estado: String,
    val tipo: String?,
    val costo: Double?,
    val duracion: Int?,
    val origen: String?,
    val confirmadaPorPaciente: Boolean,
    val numeroSesion: Int?,
    val terapeutaId: String?,
    val terapeutaNombre: String?,
    val pacienteId: String?,
    val pacienteNombre: String?,
    val pacienteTelefono: String?,
    val tratamientoId: String?,
    val procedimiento: String?,
)

/**
 * Lee la agenda (citas) directo de Supabase con la RLS de staff. Las acciones de
 * escritura (completar/revertir/cancelar) van por endpoints (misma lógica que la web).
 * Confirmar y reprogramar son escrituras simples → directas a Supabase.
 */
object AgendaRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double? =
        (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
    private fun JsonObject.nested(k: String): JsonObject? = this[k] as? JsonObject

    /** Citas de un día (yyyy-MM-dd). Si miTerapeutaId != null, solo las suyas (scope). */
    suspend fun citasDelDia(fecha: String, miTerapeutaId: String?): List<CitaStaff> {
        val filas = Supabase.client.postgrest["citas"]
            .select(Columns.raw(SELECT_CITA)) {
                filter {
                    eq("fecha", fecha)
                    if (miTerapeutaId != null) eq("terapeuta_id", miTerapeutaId)
                }
                order("hora", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
        return filas.map { mapearCita(it) }
    }

    /** Columnas comunes de una cita (con joins). Una sola fuente. */
    const val SELECT_CITA =
        "id, fecha, hora, estado, tipo, costo, duracion, origen, confirmada_por_paciente, " +
            "terapeuta_id, paciente_id, tratamiento_id, " +
            "paciente:pacientes(nombre, telefono), terapeuta:terapeutas(nombre), " +
            "tratamiento:tratamientos!citas_tratamiento_id_fkey(procedimiento:procedimientos(nombre)), " +
            "sesion:sesiones!citas_sesion_id_fkey(numero)"

    fun mapearCita(o: JsonObject): CitaStaff {
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
            fun obj(k: String) = o[k] as? JsonObject
            return CitaStaff(
                id = s("id") ?: "",
                fecha = s("fecha") ?: "",
                hora = s("hora") ?: "",
                estado = s("estado") ?: "",
                tipo = s("tipo"),
                costo = s("costo")?.toDoubleOrNull(),
                duracion = s("duracion")?.toIntOrNull(),
                origen = s("origen"),
                confirmadaPorPaciente = s("confirmada_por_paciente") == "true",
                numeroSesion = (obj("sesion")?.get("numero") as? JsonPrimitive)?.content?.toIntOrNull(),
                terapeutaId = s("terapeuta_id"),
                terapeutaNombre = (obj("terapeuta")?.get("nombre") as? JsonPrimitive)?.content?.takeIf { it != "null" },
                pacienteId = s("paciente_id"),
                pacienteNombre = (obj("paciente")?.get("nombre") as? JsonPrimitive)?.content?.takeIf { it != "null" },
                pacienteTelefono = (obj("paciente")?.get("telefono") as? JsonPrimitive)?.content?.takeIf { it != "null" },
                tratamientoId = s("tratamiento_id"),
                procedimiento = (obj("tratamiento")?.get("procedimiento") as? JsonObject)
                    ?.get("nombre")?.let { (it as? JsonPrimitive)?.content?.takeIf { v -> v != "null" } },
            )
    }

    /** Confirmar (escritura simple, sin efectos colaterales) → directo a Supabase. */
    suspend fun confirmar(citaId: String): Boolean = try {
        Supabase.client.postgrest["citas"].update({ set("estado", "Confirmada") }) {
            filter { eq("id", citaId) }
        }
        true
    } catch (_: Exception) { false }

    /** Reprogramar (cambia fecha/hora). Sincroniza la sesión vinculada si es tipo Sesión. */
    suspend fun reprogramar(citaId: String, fecha: String, hora: String): Boolean = try {
        Supabase.client.postgrest["citas"].update({
            set("fecha", fecha); set("hora", hora)
        }) { filter { eq("id", citaId) } }
        true
    } catch (_: Exception) { false }

    // ── Acciones complejas vía endpoints (kardex/comisión/contador) ──
    suspend fun completar(
        citaId: String,
        observaciones: String? = null,
        diagnostico: String? = null,
        derivarEspecialidadId: String? = null,
    ): Boolean {
        val tk = token() ?: return false
        val cuerpo = buildJsonObject {
            put("citaId", citaId)
            if (observaciones != null) put("observaciones", observaciones)
            if (!diagnostico.isNullOrBlank()) put("diagnostico", diagnostico)
            if (!derivarEspecialidadId.isNullOrBlank()) put("derivarEspecialidadId", derivarEspecialidadId)
        }
        val resp = http.post("${Supabase.SITE_URL}/api/staff/cita/completar") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    private suspend fun postSimple(ruta: String, citaId: String): Boolean {
        val tk = token() ?: return false
        val resp = http.post("${Supabase.SITE_URL}/api/staff/cita/$ruta") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("citaId", citaId) }.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    suspend fun revertir(citaId: String) = postSimple("revertir", citaId)
    suspend fun cancelar(citaId: String) = postSimple("cancelar", citaId)

    /** Especialidades activas de la clínica (para el selector de derivación). */
    suspend fun especialidades(): List<EspecialidadRef> {
        val filas = Supabase.client.postgrest["especialidades"]
            .select(Columns.list("id, nombre")) {
                filter { eq("estado", "Activa") }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull {
            val id = it.str("id") ?: return@mapNotNull null
            EspecialidadRef(id, it.str("nombre") ?: "")
        }
    }

    // ── Datos para el formulario de crear cita (lectura directa, RLS de staff) ──

    /** Pacientes activos (no Inactivo) para el selector. */
    suspend fun pacientesParaSelector(): List<RefNombre> {
        val filas = Supabase.client.postgrest["pacientes"]
            .select(Columns.list("id, nombre, estado")) {
                filter { neq("estado", "Inactivo") }
                order("nombre", Order.ASCENDING)
                limit(500)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull {
            val id = it.str("id") ?: return@mapNotNull null
            RefNombre(id, it.str("nombre") ?: "Paciente")
        }
    }

    /** Terapeutas activos para el selector de profesional. */
    suspend fun terapeutasActivos(): List<RefNombre> {
        val filas = Supabase.client.postgrest["terapeutas"]
            .select(Columns.list("id, nombre, estado")) {
                filter { eq("estado", "Activo") }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull {
            val id = it.str("id") ?: return@mapNotNull null
            RefNombre(id, it.str("nombre") ?: "Profesional")
        }
    }

    /** Tratamientos activos de un paciente (para tipo Sesión). */
    suspend fun tratamientosActivos(pacienteId: String): List<TratamientoRef> {
        val filas = Supabase.client.postgrest["tratamientos"]
            .select(
                Columns.raw("id, modalidad, terapeuta_id, procedimiento:procedimientos(nombre)")
            ) {
                filter { eq("paciente_id", pacienteId); eq("estado", "Activo") }
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull {
            val id = it.str("id") ?: return@mapNotNull null
            TratamientoRef(
                id = id,
                procedimiento = it.nested("procedimiento")?.str("nombre") ?: "Tratamiento",
                modalidad = it.str("modalidad") ?: "",
                terapeutaId = it.str("terapeuta_id"),
            )
        }
    }

    /** Precios por defecto (consulta/evaluación) de la configuración. */
    suspend fun precios(): Pair<Double, Double> {
        val filas = Supabase.client.postgrest["configuracion"]
            .select(Columns.list("clave, valor")) {
                filter { isIn("clave", listOf("precio_consulta", "precio_evaluacion")) }
            }
            .decodeList<JsonObject>()
        var consulta = 0.0; var evaluacion = 40.0
        for (o in filas) {
            when (o.str("clave")) {
                "precio_consulta" -> consulta = o.str("valor")?.toDoubleOrNull() ?: 0.0
                "precio_evaluacion" -> evaluacion = o.str("valor")?.toDoubleOrNull() ?: 40.0
            }
        }
        return consulta to evaluacion
    }

    /**
     * Pasa una Consulta a Evaluación (igual que la web): completa la consulta origen
     * (si está Pendiente/Confirmada) y crea la cita de Evaluación con sus datos.
     */
    suspend fun pasarAEvaluacion(
        citaOrigen: CitaStaff, fecha: String, hora: String, costo: Double, notas: String?,
    ): Boolean {
        // 1) Completar la consulta origen si aún no lo está.
        if (citaOrigen.estado == "Pendiente" || citaOrigen.estado == "Confirmada") {
            completar(citaOrigen.id)
        }
        // 2) Crear la evaluación con el mismo paciente/profesional.
        return crearCita(
            pacienteId = citaOrigen.pacienteId ?: return false,
            tipo = "Evaluación", fecha = fecha, hora = hora,
            terapeutaId = citaOrigen.terapeutaId, tratamientoId = null,
            costo = costo, duracion = 60, notas = notas,
        )
    }

    /** Crea una cita vía endpoint (maneja sesión vinculada + notificación). */
    suspend fun crearCita(
        pacienteId: String, tipo: String, fecha: String, hora: String,
        terapeutaId: String?, tratamientoId: String?, costo: Double, duracion: Int, notas: String?,
    ): Boolean {
        val tk = token() ?: return false
        val cuerpo = buildJsonObject {
            put("pacienteId", pacienteId)
            put("tipo", tipo)
            put("fecha", fecha)
            put("hora", hora)
            if (terapeutaId != null) put("terapeutaId", terapeutaId)
            if (tratamientoId != null) put("tratamientoId", tratamientoId)
            put("costo", costo)
            put("duracion", duracion)
            if (!notas.isNullOrBlank()) put("notas", notas)
        }
        val resp = http.post("${Supabase.SITE_URL}/api/staff/cita/crear") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        return resp.status == HttpStatusCode.OK
    }
}

data class EspecialidadRef(val id: String, val nombre: String)
data class RefNombre(val id: String, val nombre: String)
data class TratamientoRef(val id: String, val procedimiento: String, val modalidad: String, val terapeutaId: String?)

/** Una derivación pendiente (un médico derivó a un paciente; recepción decide). */
data class Derivacion(val id: String, val pacienteId: String?, val pacienteNombre: String?, val especialidadDestino: String?)

/** Una cita de mañana con su riesgo de no-show calculado. */
data class CitaManana(val cita: CitaStaff, val riesgo: ResultadoRiesgo)

/** Banners de la agenda. */
data class BannersAgenda(
    val manana: List<CitaManana>,
    val vencidas: List<CitaStaff>,
    val derivaciones: List<Derivacion>,
)

/** Extiende AgendaRepo con las cargas de banners (mañana/vencidas/derivaciones). */
object AgendaBanners {
    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double? =
        (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
    private fun JsonObject.nested(k: String): JsonObject? = this[k] as? JsonObject

    private fun mapCita(o: JsonObject) = AgendaRepo.mapearCita(o)

    private const val SEL = AgendaRepo.SELECT_CITA

    suspend fun cargar(hoy: String, manana: String, miTerapeutaId: String?, esGestor: Boolean): BannersAgenda {
        // Mañana: citas de mañana no completadas.
        val mananaCitas = Supabase.client.postgrest["citas"]
            .select(Columns.raw(SEL)) {
                filter {
                    eq("fecha", manana)
                    neq("estado", "Completada")
                    neq("estado", "Cancelada")
                    if (miTerapeutaId != null) eq("terapeuta_id", miTerapeutaId)
                }
                order("hora", Order.ASCENDING)
            }
            .decodeList<JsonObject>().map { mapCita(it) }

        // Vencidas: fecha < hoy + estado Pendiente/Confirmada.
        val vencidas = Supabase.client.postgrest["citas"]
            .select(Columns.raw(SEL)) {
                filter {
                    lt("fecha", hoy)
                    isIn("estado", listOf("Pendiente", "Confirmada"))
                    if (miTerapeutaId != null) eq("terapeuta_id", miTerapeutaId)
                }
                order("fecha", Order.DESCENDING)
                limit(50)
            }
            .decodeList<JsonObject>().map { mapCita(it) }

        // Derivaciones pendientes (solo gestor/recepción).
        val derivaciones = if (esGestor) {
            Supabase.client.postgrest["solicitudes"]
                .select(Columns.raw(
                    "id, paciente_id, descripcion, paciente:pacientes(nombre), " +
                        "especialidad_destino:especialidades!solicitudes_especialidad_destino_id_fkey(nombre)"
                )) {
                    filter { eq("tipo", "Derivacion"); eq("estado", "Pendiente") }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<JsonObject>().mapNotNull {
                    Derivacion(
                        id = it.str("id") ?: return@mapNotNull null,
                        pacienteId = it.str("paciente_id"),
                        pacienteNombre = it.nested("paciente")?.str("nombre"),
                        especialidadDestino = it.nested("especialidad_destino")?.str("nombre"),
                    )
                }
        } else emptyList()

        // Riesgo de no-show por cada cita de mañana: necesita el historial de citas
        // pasadas del paciente (faltas vs total). Igual que la web.
        val mananaConRiesgo = calcularRiesgos(mananaCitas, hoy).sortedByDescending { it.riesgo.score }

        return BannersAgenda(mananaConRiesgo, vencidas, derivaciones)
    }

    /** Calcula el riesgo de no-show de cada cita de mañana usando su historial. */
    private suspend fun calcularRiesgos(citas: List<CitaStaff>, hoy: String): List<CitaManana> {
        if (citas.isEmpty()) return emptyList()
        val pacIds = citas.mapNotNull { it.pacienteId }.distinct()
        // Historial: citas pasadas de esos pacientes (estado + para contar faltas).
        val historial = if (pacIds.isEmpty()) emptyList() else
            Supabase.client.postgrest["citas"]
                .select(Columns.list("paciente_id, estado, fecha")) {
                    filter { isIn("paciente_id", pacIds); lt("fecha", hoy) }
                }
                .decodeList<JsonObject>()
        // Acumular faltas/total por paciente (falta = Cancelada/Pendiente/Confirmada con fecha pasada).
        data class Acc(var faltas: Int = 0, var total: Int = 0)
        val acc = mutableMapOf<String, Acc>()
        for (h in historial) {
            val pid = h.str("paciente_id") ?: continue
            val a = acc.getOrPut(pid) { Acc() }
            a.total++
            val est = h.str("estado")
            if (est == "Cancelada" || est == "Pendiente" || est == "Confirmada") a.faltas++
        }
        return citas.map { cita ->
            val a = cita.pacienteId?.let { acc[it] } ?: Acc()
            val riesgo = calcularRiesgoNoShow(
                SenalesNoShow(
                    faltasPrevias = a.faltas,
                    citasPrevias = a.total,
                    confirmadaPorPaciente = cita.confirmadaPorPaciente,
                    pendiente = cita.estado == "Pendiente",
                    esOnline = cita.origen == "online",
                    sinTelefono = cita.pacienteTelefono.isNullOrBlank(),
                )
            )
            CitaManana(cita, riesgo)
        }
    }

    /** Marca una derivación como procesada. */
    suspend fun marcarDerivacion(id: String): Boolean = try {
        Supabase.client.postgrest["solicitudes"].update({ set("estado", "Completada") }) {
            filter { eq("id", id) }
        }
        true
    } catch (_: Exception) { false }
}
