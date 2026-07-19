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
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.enviarOEncolar

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
    val especialidadId: String?,  // para filtrar por especialidad
    val notaRecepcion: String?,   // recordatorio del tratamiento vinculado (📌)
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

    /**
     * Citas paginadas (vista lista, sin filtrar por un día concreto). Igual que la web:
     * si [verHistorial] es false solo trae las de hoy en adelante; si es true, todas.
     * Ordena por fecha+hora. [pagina] de tamaño 10. [miTerapeutaId] acota el scope.
     */
    suspend fun citasPaginadas(
        verHistorial: Boolean, pagina: Int, miTerapeutaId: String?, hoy: String,
    ): List<CitaStaff> {
        val desde = pagina * PAGE_SIZE
        val filas = Supabase.client.postgrest["citas"]
            .select(Columns.raw(SELECT_CITA)) {
                filter {
                    if (!verHistorial) gte("fecha", hoy)
                    if (miTerapeutaId != null) eq("terapeuta_id", miTerapeutaId)
                }
                order("fecha", if (verHistorial) Order.DESCENDING else Order.ASCENDING)
                order("hora", Order.ASCENDING)
                range(desde.toLong(), (desde + PAGE_SIZE - 1).toLong())
            }
            .decodeList<JsonObject>()
        return filas.map { mapearCita(it) }
    }

    const val PAGE_SIZE = 10

    /** Columnas comunes de una cita (con joins). Una sola fuente. */
    const val SELECT_CITA =
        "id, fecha, hora, estado, tipo, costo, duracion, origen, confirmada_por_paciente, " +
            "terapeuta_id, paciente_id, tratamiento_id, especialidad_id, " +
            "paciente:pacientes(nombre, telefono), terapeuta:terapeutas(nombre), " +
            "tratamiento:tratamientos!citas_tratamiento_id_fkey(nota_recepcion, procedimiento:procedimientos(nombre)), " +
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
                especialidadId = s("especialidad_id"),
                notaRecepcion = (obj("tratamiento")?.get("nota_recepcion") as? JsonPrimitive)
                    ?.content?.takeIf { it != "null" && it.isNotBlank() },
            )
    }

    /** Confirmar (escritura simple, sin efectos colaterales) → directo a Supabase. */
    suspend fun confirmar(citaId: String): Boolean = try {
        Supabase.client.postgrest["citas"].update({ set("estado", "Confirmada") }) {
            filter { eq("id", citaId) }
        }
        true
    } catch (_: Exception) { false }

    /**
     * Reprogramar (cambia fecha/hora). Si la cita es tipo "Sesión", sincroniza también la
     * fila de `sesiones` vinculada (misma fecha/hora), para que la ficha del paciente y la
     * agenda no queden con fechas distintas para la misma sesión. La sesión se ubica por
     * (tratamiento_id + fecha vieja), igual que la web (sesiones-acciones).
     */
    suspend fun reprogramar(
        citaId: String, fecha: String, hora: String,
        tipo: String? = null, tratamientoId: String? = null, fechaAntes: String? = null,
    ): Boolean = try {
        Supabase.client.postgrest["citas"].update({
            set("fecha", fecha); set("hora", hora)
        }) { filter { eq("id", citaId) } }
        // Sincronizar la sesión vinculada (solo tipo Sesión con tratamiento y fecha previa).
        if (tipo == "Sesión" && tratamientoId != null && !fechaAntes.isNullOrBlank()) {
            Supabase.client.postgrest["sesiones"].update({
                set("fecha", fecha); set("hora", hora)
            }) {
                filter {
                    eq("tratamiento_id", tratamientoId)
                    eq("fecha", fechaAntes)
                }
            }
        }
        true
    } catch (_: Exception) { false }

    // ── Acciones complejas vía endpoints (kardex/comisión/contador) ──
    suspend fun completar(
        citaId: String,
        observaciones: String? = null,
        diagnostico: String? = null,
        derivarEspecialidadId: String? = null,
    ): Boolean {
        val cuerpo = buildJsonObject {
            put("citaId", citaId)
            if (observaciones != null) put("observaciones", observaciones)
            if (!diagnostico.isNullOrBlank()) put("diagnostico", diagnostico)
            if (!derivarEspecialidadId.isNullOrBlank()) put("derivarEspecialidadId", derivarEspecialidadId)
        }
        return encolarCita("completar", cuerpo)
    }

    /**
     * ENCOLA un POST de cita (no lo envía directo): queda guardado en el
     * dispositivo aunque no haya señal y el Sincronizador lo manda al volver la
     * conexión, sin duplicar (idempotency_key). Antes, un fallo de red perdía
     * la operación en silencio.
     */
    private suspend fun encolarCita(ruta: String, cuerpo: JsonObject): Boolean =
        enviarOEncolar("cita:$ruta", "/api/staff/cita/$ruta", cuerpo)

    private suspend fun postSimple(ruta: String, citaId: String): Boolean =
        encolarCita(ruta, buildJsonObject { put("citaId", citaId) })

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

    /** Terapeutas activos con sus especialidades (para filtrar por especialidad). */
    suspend fun terapeutasActivos(): List<TerapeutaRef> {
        val filas = Supabase.client.postgrest["terapeutas"]
            .select(Columns.raw(
                "id, nombre, estado, " +
                    "especialidades:terapeuta_especialidades(especialidad:especialidades(id, nombre))"
            )) {
                filter { eq("estado", "Activo") }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull { o ->
            val id = o.str("id") ?: return@mapNotNull null
            val espIds = (o["especialidades"] as? kotlinx.serialization.json.JsonArray ?: emptyList())
                .mapNotNull { rel ->
                    ((rel as? JsonObject)?.get("especialidad") as? JsonObject)?.str("id")
                }
            TerapeutaRef(id, o.str("nombre") ?: "Profesional", espIds)
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
     * Disponibilidad de TODOS los profesionales (activos) para una fecha/hora, para el
     * modal "ver horarios". Reusa DisponibilidadRepo (misma regla que la web). Si se pasa
     * [especialidadIds] (filtro), solo evalúa los profesionales de esa especialidad.
     */
    suspend fun disponibilidadProfesionales(
        fecha: String, hora: String, duracion: Int,
        soloTerapeutaIds: List<String>? = null,
    ): List<EstadoProfesional> {
        val activos = terapeutasActivos()
        val filtrados = if (soloTerapeutaIds != null) activos.filter { it.id in soloTerapeutaIds } else activos
        return filtrados.map { ter ->
            val d = runCatching {
                DisponibilidadRepo.verificar(ter.id, fecha, hora, duracion)
            }.getOrNull()
            val libre = d?.disponible == true && d.esAdvertencia == false
            EstadoProfesional(
                terapeutaId = ter.id,
                nombre = ter.nombre,
                libre = libre,
                etiqueta = when {
                    d == null -> "—"
                    !d.disponible -> d.motivo ?: "No disponible"
                    d.motivo != null -> d.motivo
                    else -> "Libre a las ${hora.take(5)}"
                },
                rangos = "",
                citasDia = "",
            )
        }
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
        especialidadId: String? = null, diagnostico: String? = null,
    ): Boolean {
        val tk = token() ?: return false
        val cuerpo = buildJsonObject {
            put("pacienteId", pacienteId)
            put("tipo", tipo)
            put("fecha", fecha)
            put("hora", hora)
            if (terapeutaId != null) put("terapeutaId", terapeutaId)
            if (tratamientoId != null) put("tratamientoId", tratamientoId)
            if (especialidadId != null) put("especialidadId", especialidadId)
            if (!diagnostico.isNullOrBlank()) put("diagnostico", diagnostico)
            put("costo", costo)
            put("duracion", duracion)
            if (!notas.isNullOrBlank()) put("notas", notas)
        }
        return encolarCita("crear", cuerpo)
    }
}

data class EspecialidadRef(val id: String, val nombre: String)
data class RefNombre(val id: String, val nombre: String)
/** Terapeuta con sus especialidades (para filtrar por especialidad en el form). */
data class TerapeutaRef(val id: String, val nombre: String, val especialidadIds: List<String>)
data class TratamientoRef(val id: String, val procedimiento: String, val modalidad: String, val terapeutaId: String?)

/**
 * Una solicitud pendiente de agendar (un profesional derivó o pidió un examen interno;
 * recepción decide cuándo y con quién). [tipo]: "Derivacion" | "Examen" (interno).
 */
data class Derivacion(
    val id: String, val pacienteId: String?, val pacienteNombre: String?,
    val especialidadDestino: String?, val especialidadDestinoId: String?,
    val tipo: String = "Derivacion", val descripcion: String? = null,
) {
    val esExamen: Boolean get() = tipo == "Examen"
}

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

        // Vencidas: fecha < hoy y >= hace 7 días + estado Pendiente/Confirmada. Las más
        // viejas (>7 días) las cierra solo el cron de la web (/api/cron/cerrar-citas-viejas),
        // igual que en la web → el banner no acumula ruido histórico.
        val hace7 = runCatching {
            kotlinx.datetime.LocalDate.parse(hoy)
                .plus(-7, kotlinx.datetime.DateTimeUnit.DAY).toString()
        }.getOrDefault(hoy)
        val vencidas = Supabase.client.postgrest["citas"]
            .select(Columns.raw(SEL)) {
                filter {
                    lt("fecha", hoy)
                    gte("fecha", hace7)
                    isIn("estado", listOf("Pendiente", "Confirmada"))
                    if (miTerapeutaId != null) eq("terapeuta_id", miTerapeutaId)
                }
                order("fecha", Order.DESCENDING)
                limit(50)
            }
            .decodeList<JsonObject>().map { mapCita(it) }

        // Pendientes de agendar (solo gestor/recepción): derivaciones + exámenes INTERNOS
        // (lugar=Interno), ambos con un área de destino. Recepción los agenda en esa área.
        val derivaciones = if (esGestor) {
            Supabase.client.postgrest["solicitudes"]
                .select(Columns.raw(
                    "id, tipo, lugar, paciente_id, descripcion, especialidad_destino_id, paciente:pacientes(nombre), " +
                        "especialidad_destino:especialidades!solicitudes_especialidad_destino_id_fkey(nombre)"
                )) {
                    filter {
                        eq("estado", "Pendiente")
                        or {
                            eq("tipo", "Derivacion")
                            and { eq("tipo", "Examen"); eq("lugar", "Interno") }
                        }
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<JsonObject>().mapNotNull {
                    Derivacion(
                        id = it.str("id") ?: return@mapNotNull null,
                        pacienteId = it.str("paciente_id"),
                        pacienteNombre = it.nested("paciente")?.str("nombre"),
                        especialidadDestino = it.nested("especialidad_destino")?.str("nombre"),
                        especialidadDestinoId = it.str("especialidad_destino_id"),
                        tipo = it.str("tipo") ?: "Derivacion",
                        descripcion = it.str("descripcion"),
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
