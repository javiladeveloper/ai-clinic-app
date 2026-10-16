package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/** Lectura de la pestaña: presupuestos + los tratamientos que salieron de ellos, o el error (con su código). */
data class LecturaPresupuestos(
    val items: List<Presupuesto>? = null,
    val tratamientos: List<TratDePresupuesto> = emptyList(),
    val error: String? = null,
    /** "PLAN_REQUERIDO" → candado de plan (Básico o vencido). */
    val codigo: String? = null,
)

/** Un tratamiento creado al convertir (respuesta de /convertir). */
data class TratamientoConvertido(
    val id: String,
    val clave: String,
    val nombre: String,
    val monto: Double,
    val modalidad: String?,
    val totalSesiones: Int?,
    val procedimientoId: String?,
    val terapeutaId: String?,
    val sedeId: String?,
    val yaExistia: Boolean,
)

fun tratamientosConvertidosDeJson(o: JsonObject?): List<TratamientoConvertido> =
    (o?.get("tratamientos") as? JsonArray).orEmpty().mapNotNull { el ->
        val x = el as? JsonObject ?: return@mapNotNull null
        fun s(k: String) = (x[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        val id = s("id") ?: return@mapNotNull null
        TratamientoConvertido(
            id = id, clave = s("clave").orEmpty(), nombre = s("nombre") ?: "Tratamiento",
            monto = (x["monto"] as? JsonPrimitive)?.doubleOrNull ?: 0.0, modalidad = s("modalidad"),
            totalSesiones = s("totalSesiones")?.toDoubleOrNull()?.toInt(), procedimientoId = s("procedimientoId"),
            terapeutaId = s("terapeutaId"), sedeId = s("sedeId"), yaExistia = s("yaExistia") == "true",
        )
    }

/**
 * 🧾 Presupuestos por /api/staff/presupuestos (+ /accion, /convertir, /plantillas)
 * con el Bearer del staff (docs/app-contrato-presupuestos.md). El servidor valida
 * permisos, plan (403 PLAN_REQUERIDO), solo lectura de soporte y el dinero
 * (lib/presupuestos.ts). DIRECTO, sin cola offline: el editor y la aceptación
 * necesitan la fila guardada (updated_at) para seguir.
 */
object PresupuestosRepo {

    private const val RUTA = "/api/staff/presupuestos"
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /** El usuario que arma (para saber si es el autor). */
    fun usuarioId(): String? = Supabase.client.auth.currentUserOrNull()?.id

    private val SIN_SESION = ResultadoEscritura(false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401))
    private val SIN_RED = ResultadoEscritura(false, rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"))

    private suspend fun leer(params: Map<String, String>): Pair<JsonObject?, LecturaPresupuestos?> {
        val tk = token() ?: return null to LecturaPresupuestos(error = "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                params.forEach { (k, v) -> parameter(k, v) }
            }
            val o = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
            if (resp.status.value in 200..299 && o != null) o to null
            else {
                val cod = (o?.get("codigo") as? JsonPrimitive)?.contentOrNull
                val msg = (o?.get("error") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                null to LecturaPresupuestos(
                    error = when {
                        resp.status.value == 404 && cod == null -> "Esta función todavía no está disponible. Actualiza más tarde."
                        else -> msg ?: "No se pudieron cargar los presupuestos"
                    },
                    codigo = cod,
                )
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            null to LecturaPresupuestos(error = "Sin conexión. Revisa tu internet.")
        }
    }

    /** Los del paciente (más reciente primero) y los tratamientos creados desde ellos. */
    suspend fun listar(pacienteId: String): LecturaPresupuestos {
        val (o, err) = leer(mapOf("pacienteId" to pacienteId))
        if (o == null) return err ?: LecturaPresupuestos(error = "No se pudieron cargar los presupuestos")
        return LecturaPresupuestos(
            items = (o["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::presupuestoDeJson) },
            tratamientos = (o["tratamientos"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::tratDePresupuestoDeJson) },
        )
    }

    /** Pendientes de la clínica (enviados, vencidos y aceptados sin convertir), con su paciente. */
    suspend fun pendientes(): LecturaPresupuestos {
        val (o, err) = leer(mapOf("pendientes" to "1"))
        if (o == null) return err ?: LecturaPresupuestos(error = "No se pudieron cargar los presupuestos")
        return LecturaPresupuestos(items = (o["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::presupuestoDeJson) })
    }

    private suspend fun enviar(metodo: HttpMethod, ruta: String, cuerpo: JsonObject): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.request("${Supabase.SITE_URL}$ruta") {
                    method = metodo
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(cuerpo.toString())
                }
                AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    /** Crea un borrador. `cuerpo["presupuesto"]` vuelve la fila. */
    suspend fun crear(pacienteId: String, d: DatosPresupuesto, citaId: String?, especialidadId: String?): ResultadoEscritura =
        enviar(HttpMethod.Post, RUTA, JsonObject(cuerpoDatosPresupuesto(d) + buildJsonObject {
            put("pacienteId", pacienteId)
            if (citaId != null) put("citaId", citaId) else put("citaId", JsonNull)
            if (especialidadId != null) put("especialidadId", especialidadId) else put("especialidadId", JsonNull)
        }))

    /** Edita (solo Borrador/Enviado). `cuerpo["volvioABorrador"]` = hay que reenviarlo. */
    suspend fun editar(id: String, d: DatosPresupuesto): ResultadoEscritura =
        enviar(HttpMethod.Patch, RUTA, JsonObject(cuerpoDatosPresupuesto(d) + buildJsonObject { put("id", id) }))

    /** "↻ Extender": solo la vigencia (no obliga a reenviar). */
    suspend fun extenderVigencia(id: String, vigenciaHasta: String): ResultadoEscritura =
        enviar(HttpMethod.Patch, RUTA, buildJsonObject { put("id", id); put("vigenciaHasta", vigenciaHasta) })

    /** Borra un BORRADOR. */
    suspend fun borrar(id: String): ResultadoEscritura = enviar(HttpMethod.Delete, RUTA, buildJsonObject { put("id", id) })

    /**
     * enviar · aceptar (con [updatedAt] OBLIGATORIO: 409 CAMBIO si otro lo editó) ·
     * rechazar (+ motivo) · reabrir · recordado.
     */
    suspend fun accion(id: String, accion: String, motivo: String? = null, updatedAt: String? = null): ResultadoEscritura =
        enviar(HttpMethod.Post, "$RUTA/accion", buildJsonObject {
            put("id", id); put("accion", accion)
            if (!motivo.isNullOrBlank()) put("motivo", motivo.trim().take(LimitesPresupuesto.MOTIVO))
            if (updatedAt != null) put("updatedAt", updatedAt)
        })

    /** Un tratamiento por ítem (idempotente). 409 PARCIAL trae lo que sí quedó en `tratamientos`. */
    suspend fun convertir(id: String, terapeutaId: String?, servicios: Map<String, String>): ResultadoEscritura =
        enviar(HttpMethod.Post, "$RUTA/convertir", buildJsonObject {
            put("id", id)
            if (terapeutaId != null) put("terapeutaId", terapeutaId) else put("terapeutaId", JsonNull)
            put("servicios", buildJsonObject { servicios.forEach { (k, v) -> put(k, v) } })
        })

    /**
     * Una parte del ADELANTO (el cobro de siempre: kardex, comisión, estado de pago).
     * [clave] = idempotencia: la misma para reintentar, nueva si cambia el monto o el método.
     */
    suspend fun registrarAdelanto(tratamientoId: String, monto: Double, metodo: String, notas: String, clave: String): ResultadoEscritura =
        enviar(HttpMethod.Post, "/api/staff/pago/registrar", buildJsonObject {
            put("tratamientoId", tratamientoId); put("monto", monto); put("metodo", metodo)
            put("notas", notas); put("idempotency_key", clave)
        })

    // ── Paquetes guardados (plantillas_presupuesto) ──

    suspend fun plantillas(): List<PlantillaPresupuesto> {
        val tk = token() ?: return emptyList()
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA/plantillas") { header("Authorization", "Bearer $tk") }
            if (resp.status.value !in 200..299) return emptyList()
            val o = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            (o["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::plantillaPresupuestoDeJson) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    /** Mismo nombre (sin mayúsculas) ACTUALIZA. */
    suspend fun guardarPlantilla(nombre: String, d: DatosPresupuesto, vigenciaDias: Int, especialidadId: String?): ResultadoEscritura =
        enviar(HttpMethod.Post, "$RUTA/plantillas", buildJsonObject {
            put("nombre", nombre.trim().take(LimitesPresupuesto.NOMBRE_PLANTILLA))
            put("titulo", d.titulo)
            put("items", itemsAJson(d.items))
            if (d.descuentoTipo != null) put("descuentoTipo", d.descuentoTipo) else put("descuentoTipo", JsonNull)
            put("descuentoValor", d.descuentoValor)
            if (d.notasPaciente != null) put("notasPaciente", d.notasPaciente) else put("notasPaciente", JsonNull)
            put("vigenciaDias", vigenciaDias.coerceIn(1, LimitesPresupuesto.VIGENCIA_MAX_DIAS))
            if (especialidadId != null) put("especialidadId", especialidadId) else put("especialidadId", JsonNull)
        })

    suspend fun borrarPlantilla(id: String): ResultadoEscritura =
        enviar(HttpMethod.Delete, "$RUTA/plantillas", buildJsonObject { put("id", id) })
}

/**
 * Los tratamientos que dejó /convertir: en el OK y también en el 409 PARCIAL (el
 * JSON del rechazo llega en `cuerpo`): lo creado se ve, se cobra y se agenda.
 */
fun tratamientosDeConversion(r: ResultadoEscritura): List<TratamientoConvertido> =
    tratamientosConvertidosDeJson(r.cuerpo ?: r.rechazo?.datos)

/** La fila que devolvió una escritura (`cuerpo["presupuesto"]`). */
fun presupuestoDeRespuesta(r: ResultadoEscritura): Presupuesto? =
    (r.cuerpo?.get("presupuesto") as? JsonObject)?.let(::presupuestoDeJson)

/** El mensaje para el usuario de un rechazo (el del servidor o el de la base, traducido). */
fun mensajeRechazoPresupuesto(r: ResultadoEscritura, porDefecto: String = "No se pudo guardar"): String {
    val m = r.rechazo?.error
    if (m.isNullOrBlank()) return porDefecto
    val traducido = mensajeErrorPresupuesto(m)
    return if (traducido != "No se pudo guardar el presupuesto") traducido else m
}
