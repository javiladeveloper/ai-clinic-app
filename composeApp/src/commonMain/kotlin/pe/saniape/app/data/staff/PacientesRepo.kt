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
    val precioPaquete: Double?,
    val precioPorSesion: Double?,
    val precioAcordado: Double?,
    /** La especialidad del procedimiento usa sesiones (fisio…) o es Consulta (medicina…). */
    val usaSesiones: Boolean,
) {
    /** Monto total acordado del tratamiento (igual que la web). */
    val montoAcordado: Double
        get() = precioAcordado
            ?: if (modalidad == "Paquete") (precioPaquete ?: 0.0)
            else (precioPorSesion ?: 0.0) * totalSesiones

    /** Es una Consulta (especialidad sin sesiones): no muestra contador/acciones de sesión. */
    val esConsulta: Boolean get() = !usaSesiones
}

/** Una sesión de un tratamiento (para la ficha). */
data class SesionFicha(
    val id: String,
    val numero: Int,
    val fecha: String,
    val hora: String?,
    val estado: String,
    val costo: Double?,
    val notas: String?,        // procedimientos realizados en la sesión
    val mejorias: String?,     // evolución/observaciones (desde la sesión #2)
    val duracion: Int?,
    val terapeutaNombre: String?,
    val motivoEstado: String?,
    val pagada: Boolean = false,   // tiene algún pago vinculado (sesion_id)
) {
    val pendiente: Boolean
        get() = estado == "Planificada" || estado == "En progreso" || estado == "Reprogramada"
}

/** Un pago de un tratamiento (para la PagoCard de la ficha). */
data class PagoFicha(
    val id: String,
    val monto: Double,
    val metodo: String,
    val notas: String?,
    val fecha: String,
    val numeroSesion: Int?,   // si el pago nació de una sesión (cobro), su número
)

/** Un procedimiento/servicio para crear un tratamiento. */
data class ProcedimientoRef(
    val id: String,
    val nombre: String,
    val precio: Double,
    val precioPaquete: Double?,
    val especialidadId: String?,
    val usaSesiones: Boolean,
)

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
            precio_paquete, precio_por_sesion, precio_acordado, terapeuta_id,
            procedimiento:procedimientos(nombre, especialidad:especialidades(usa_sesiones)),
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

    /** Actualiza datos del paciente desde la ficha (RLS de staff acota a su clínica). */
    suspend fun actualizarPaciente(
        id: String, nombre: String, telefono: String?, ocupacion: String?,
        edad: Int?, flag: String?, diagnostico: String?,
    ): Boolean = try {
        Supabase.client.postgrest["pacientes"].update({
            set("nombre", nombre)
            set("telefono", telefono)
            set("ocupacion", ocupacion)
            set("edad", edad)
            if (flag != null) set("flag", flag)
            set("diagnostico", diagnostico)
        }) { filter { eq("id", id) } }
        true
    } catch (_: Exception) { false }

    /** Cambia el estado del paciente (dar de baja / reactivar). */
    suspend fun cambiarEstadoPaciente(id: String, estado: String): Boolean = try {
        Supabase.client.postgrest["pacientes"].update({ set("estado", estado) }) { filter { eq("id", id) } }
        true
    } catch (_: Exception) { false }

    /** Sesiones de un tratamiento (para la ficha), ordenadas por número desc. */
    suspend fun sesionesDe(tratamientoId: String): List<SesionFicha> {
        val filas = Supabase.client.postgrest["sesiones"]
            .select(Columns.raw(
                "id, numero, fecha, hora, estado, costo, notas, mejorias, duracion, motivo_estado, " +
                    "terapeuta:terapeutas(nombre), pagos:pagos_tratamiento(id)"
            )) {
                filter { eq("tratamiento_id", tratamientoId) }
                order("numero", Order.DESCENDING)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull { o ->
            val tienePago = (o["pagos"] as? kotlinx.serialization.json.JsonArray)?.isNotEmpty() == true
            val terNombre = (o["terapeuta"] as? JsonObject)?.str("nombre")
            SesionFicha(
                id = o.str("id") ?: return@mapNotNull null,
                numero = o.int("numero") ?: 0,
                fecha = o.str("fecha") ?: "",
                hora = o.str("hora"),
                estado = o.str("estado") ?: "Planificada",
                costo = o.dbl("costo"),
                notas = o.str("notas"),
                mejorias = o.str("mejorias"),
                duracion = o.int("duracion"),
                terapeutaNombre = terNombre,
                motivoEstado = o.str("motivo_estado"),
                pagada = tienePago,
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

    /** Pagos registrados de un tratamiento (para la PagoCard de la ficha). */
    suspend fun pagosDe(tratamientoId: String): List<PagoFicha> {
        val filas = Supabase.client.postgrest["pagos_tratamiento"]
            .select(Columns.raw("id, monto, metodo, notas, fecha, sesion:sesiones(numero)")) {
                filter { eq("tratamiento_id", tratamientoId) }
                order("fecha", Order.DESCENDING)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull { o ->
            val numSes = ((o["sesion"] as? JsonObject)?.get("numero") as? JsonPrimitive)?.content?.toIntOrNull()
            PagoFicha(
                id = o.str("id") ?: return@mapNotNull null,
                monto = o.dbl("monto") ?: 0.0,
                metodo = o.str("metodo") ?: "Efectivo",
                notas = o.str("notas"),
                fecha = o.str("fecha") ?: "",
                numeroSesion = numSes,
            )
        }
    }

    /** Registra un pago vía endpoint (inserta pago + ingreso en caja + recalcula estado). */
    suspend fun registrarPago(
        tratamientoId: String, monto: Double, metodo: String,
        notas: String? = null, recordar: Boolean = false,
    ): Boolean {
        val tk = token() ?: return false
        val cuerpo = buildJsonObject {
            put("tratamientoId", tratamientoId)
            put("monto", monto)
            put("metodo", metodo)
            if (!notas.isNullOrBlank()) put("notas", notas)
            if (recordar) put("recordar", true)
        }
        val resp = http.post("${Supabase.SITE_URL}/api/staff/pago/registrar") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    /** Edita un pago (solo Admin): ajusta pago + movimiento + recalcula. */
    suspend fun editarPago(
        pagoId: String, monto: Double, metodo: String, notas: String? = null, fecha: String? = null,
    ): Boolean {
        val tk = token() ?: return false
        val cuerpo = buildJsonObject {
            put("pagoId", pagoId)
            put("monto", monto)
            put("metodo", metodo)
            if (!notas.isNullOrBlank()) put("notas", notas)
            if (!fecha.isNullOrBlank()) put("fecha", fecha)
        }
        val resp = http.post("${Supabase.SITE_URL}/api/staff/pago/editar") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    /** Borra un pago (solo Admin): elimina movimiento + pago + recalcula. */
    suspend fun borrarPago(pagoId: String): Boolean {
        val tk = token() ?: return false
        val resp = http.post("${Supabase.SITE_URL}/api/staff/pago/borrar") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("pagoId", pagoId) }.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    // ── Acciones de sesión (editar/revertir/borrar/reasignar) vía endpoint accion ──
    private suspend fun accionSesion(cuerpo: JsonObject): Boolean {
        val tk = token() ?: return false
        val resp = http.post("${Supabase.SITE_URL}/api/staff/sesion/accion") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    suspend fun editarSesion(
        sesionId: String, fecha: String, hora: String?, duracion: Int, costo: Double?, notas: String?,
    ): Boolean = accionSesion(buildJsonObject {
        put("accion", "editar"); put("sesionId", sesionId)
        put("fecha", fecha); put("duracion", duracion)
        if (!hora.isNullOrBlank()) put("hora", hora)
        if (costo != null) put("costo", costo)
        if (!notas.isNullOrBlank()) put("notas", notas)
    })

    suspend fun revertirSesion(sesionId: String): Boolean = accionSesion(buildJsonObject {
        put("accion", "revertir"); put("sesionId", sesionId)
    })

    suspend fun borrarSesion(sesionId: String, borrarPagos: Boolean): Boolean = accionSesion(buildJsonObject {
        put("accion", "borrar"); put("sesionId", sesionId); put("borrarPagos", borrarPagos)
    })

    suspend fun reasignarSesion(sesionId: String, terapeutaId: String): Boolean = accionSesion(buildJsonObject {
        put("accion", "reasignar"); put("sesionId", sesionId); put("terapeutaId", terapeutaId)
    })

    /** Cobrar una sesión (pago vinculado a la sesión) — reusa el endpoint de pago. */
    suspend fun cobrarSesion(
        tratamientoId: String, sesionId: String, monto: Double, metodo: String, notas: String? = null,
    ): Boolean {
        val tk = token() ?: return false
        val cuerpo = buildJsonObject {
            put("tratamientoId", tratamientoId)
            put("sesionId", sesionId)
            put("monto", monto)
            put("metodo", metodo)
            if (!notas.isNullOrBlank()) put("notas", notas)
        }
        val resp = http.post("${Supabase.SITE_URL}/api/staff/pago/registrar") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    /** Profesionales activos (para reasignar). */
    suspend fun terapeutasActivos(): List<RefNombre> {
        val filas = Supabase.client.postgrest["terapeutas"]
            .select(Columns.list("id, nombre, estado")) {
                filter { eq("estado", "Activo") }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull { RefNombre(it.str("id") ?: return@mapNotNull null, it.str("nombre") ?: "Profesional") }
    }

    // ── Acciones de tratamiento (crear/editar/ampliar/estado) vía endpoint accion ──
    private suspend fun accionTratamiento(cuerpo: JsonObject): Boolean {
        val tk = token() ?: return false
        val resp = http.post("${Supabase.SITE_URL}/api/staff/tratamiento/accion") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    suspend fun crearTratamiento(
        pacienteId: String, procedimientoId: String, terapeutaId: String?, modalidad: String,
        totalSesiones: Int?, precioPaquete: Double?, precioPorSesion: Double?, precioAcordado: Double?,
        diagnostico: String?,
    ): Boolean = accionTratamiento(buildJsonObject {
        put("accion", "crear"); put("pacienteId", pacienteId); put("procedimientoId", procedimientoId)
        if (terapeutaId != null) put("terapeutaId", terapeutaId)
        put("modalidad", modalidad)
        if (totalSesiones != null) put("totalSesiones", totalSesiones)
        if (precioPaquete != null) put("precioPaquete", precioPaquete)
        if (precioPorSesion != null) put("precioPorSesion", precioPorSesion)
        if (precioAcordado != null) put("precioAcordado", precioAcordado)
        if (!diagnostico.isNullOrBlank()) put("diagnostico", diagnostico)
    })

    suspend fun editarTratamiento(
        tratamientoId: String, totalSesiones: Int?, precioPaquete: Double?,
        precioPorSesion: Double?, precioAcordado: Double?,
    ): Boolean = accionTratamiento(buildJsonObject {
        put("accion", "editar"); put("tratamientoId", tratamientoId)
        if (totalSesiones != null) put("totalSesiones", totalSesiones)
        if (precioPaquete != null) put("precioPaquete", precioPaquete)
        if (precioPorSesion != null) put("precioPorSesion", precioPorSesion)
        if (precioAcordado != null) put("precioAcordado", precioAcordado)
    })

    suspend fun cambiarEstadoTratamiento(tratamientoId: String, estado: String): Boolean =
        accionTratamiento(buildJsonObject {
            put("accion", "estado"); put("tratamientoId", tratamientoId); put("estado", estado)
        })

    suspend fun ampliarTratamiento(tratamientoId: String, sesionesExtra: Int, montoExtra: Double, nota: String?): Boolean =
        accionTratamiento(buildJsonObject {
            put("accion", "ampliar"); put("tratamientoId", tratamientoId)
            put("sesionesExtra", sesionesExtra); put("montoExtra", montoExtra)
            if (!nota.isNullOrBlank()) put("nota", nota)
        })

    /** Crear una sesión para un tratamiento (reusa el endpoint de crear cita tipo Sesión). */
    suspend fun crearSesion(
        pacienteId: String, tratamientoId: String, terapeutaId: String?,
        fecha: String, hora: String,
    ): Boolean {
        val tk = token() ?: return false
        val cuerpo = buildJsonObject {
            put("pacienteId", pacienteId); put("tipo", "Sesión")
            put("tratamientoId", tratamientoId); put("fecha", fecha); put("hora", hora)
            if (terapeutaId != null) put("terapeutaId", terapeutaId)
            put("duracion", 45)
        }
        val resp = http.post("${Supabase.SITE_URL}/api/staff/cita/crear") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    /** Procedimientos activos (con especialidad + usa_sesiones + precios) para crear tratamiento. */
    suspend fun procedimientos(): List<ProcedimientoRef> {
        val filas = Supabase.client.postgrest["procedimientos"]
            .select(Columns.raw(
                "id, nombre, precio, precio_paquete, especialidad_id, " +
                    "especialidad:especialidades(usa_sesiones)"
            )) {
                filter { eq("estado", "Activo") }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull { o ->
            ProcedimientoRef(
                id = o.str("id") ?: return@mapNotNull null,
                nombre = o.str("nombre") ?: "Servicio",
                precio = o.dbl("precio") ?: 0.0,
                precioPaquete = o.dbl("precio_paquete"),
                especialidadId = o.str("especialidad_id"),
                usaSesiones = ((o["especialidad"] as? JsonObject)?.get("usa_sesiones") as? JsonPrimitive)
                    ?.content?.let { it != "false" } ?: true,
            )
        }
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
            val procObj = t["procedimiento"] as? JsonObject
            val proc = procObj?.str("nombre")
            // usa_sesiones de la especialidad del procedimiento (default true si no viene).
            val usaSes = ((procObj?.get("especialidad") as? JsonObject)?.get("usa_sesiones") as? JsonPrimitive)
                ?.content?.let { it != "false" } ?: true
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
                precioPaquete = t.dbl("precio_paquete"),
                precioPorSesion = t.dbl("precio_por_sesion"),
                precioAcordado = t.dbl("precio_acordado"),
                usaSesiones = usaSes,
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