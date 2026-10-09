package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

// ─────────────────────────────────────────────────────────────────────────────
// Campañas de descuento. Gemelo de app/(app)/campanias en la web. Lectura
// directa (RLS por clínica); escrituras por /api/staff/campania (la misma
// lógica de lib/campanias-acciones.ts que usa la web). La vigencia y las
// etiquetas espejan lib/campanias.ts: si cambia una, cambia la otra.
// ─────────────────────────────────────────────────────────────────────────────

/** Una fila de `campanias` con los servicios que alcanza. */
data class CampaniaGestion(
    val id: String,
    val nombre: String,
    val descripcion: String? = null,
    /** paquete_fijo | precio_fijo | porcentaje | monto_fijo */
    val tipo: String,
    val cantidad: Int? = null,
    val precio: Double? = null,
    val valor: Double? = null,
    /** todos | servicios | citas */
    val alcance: String = "todos",
    /** Solo con alcance 'citas': Evaluación | Consulta | ambas. */
    val aplicaA: String? = null,
    val fechaInicio: String = "",
    val fechaFin: String? = null,
    val activo: Boolean = true,
    val serviciosIds: List<String> = emptyList(),
)

/** Estado de una campaña frente a hoy (espejo de la lista web). */
enum class EstadoCampania { VIGENTE, INACTIVA, PROGRAMADA, VENCIDA }

/** Gemelo de `campaniaVigente`: activa + entre desde/hasta (fechas inclusivas). */
fun campaniaVigente(c: CampaniaGestion, hoy: String): Boolean {
    if (!c.activo) return false
    if (c.fechaInicio.isNotBlank() && c.fechaInicio > hoy) return false
    if (!c.fechaFin.isNullOrBlank() && c.fechaFin < hoy) return false
    return true
}

fun estadoCampania(c: CampaniaGestion, hoy: String): EstadoCampania = when {
    campaniaVigente(c, hoy) -> EstadoCampania.VIGENTE
    !c.activo -> EstadoCampania.INACTIVA
    c.fechaInicio > hoy -> EstadoCampania.PROGRAMADA
    else -> EstadoCampania.VENCIDA
}

private fun fmtNum(n: Double): String = montoCorto(n)

/** Gemelo de `etiquetaCampania`: "10 sesiones a S/500", "10% de descuento"… (moneda de la sede activa). */
fun etiquetaCampania(c: CampaniaGestion, moneda: String = monedaActiva()): String = when (c.tipo) {
    "paquete_fijo" -> "${c.cantidad ?: 0} sesiones a ${dineroCorto(c.precio ?: 0.0, moneda)}"
    "precio_fijo" -> dineroCorto(c.precio ?: 0.0, moneda)
    "porcentaje" -> "${fmtNum(c.valor ?: 0.0)}% de descuento"
    "monto_fijo" -> "${dineroCorto(c.valor ?: 0.0, moneda)} de descuento"
    else -> c.nombre
}

/** Los 4 tipos con su etiqueta y ejemplo (los del formulario web), con la moneda dada. */
fun tiposCampania(moneda: String = monedaActiva()): List<Triple<String, String, String>> = listOf(
    Triple("paquete_fijo", "📦 Paquete promocional", "10 sesiones a ${dineroCorto(500.0, moneda)}"),
    Triple("precio_fijo", "🏷️ Precio de oferta", "Masajes a ${dineroCorto(50.0, moneda)}"),
    Triple("porcentaje", "％ Descuento porcentual", "10% en todos los paquetes"),
    Triple("monto_fijo", "➖ Descuento en ${nombreMonedaPlural(moneda)}", "${dineroCorto(30.0, moneda)} menos en la evaluación"),
)

/** Los 4 tipos con la moneda de la sede activa (PEN: los de siempre). */
val TIPOS_CAMPANIA: List<Triple<String, String, String>> get() = tiposCampania()

/** Un regalo del formulario (texto tal cual se escribe). */
data class RegaloForm(val procedimientoId: String = "", val dias: String = "1", val precio: String = "")

/** Estado del formulario de una campaña (todo texto). */
data class FormCampania(
    val nombre: String = "",
    val descripcion: String = "",
    val tipo: String = "paquete_fijo",
    val cantidad: String = "",
    val precio: String = "",
    val valor: String = "",
    val alcance: String = "todos",
    val aplicaA: String = "Evaluación",
    val serviciosIds: List<String> = emptyList(),
    val desde: String = "",
    val hasta: String = "",
    val regalos: List<RegaloForm> = emptyList(),
    val activo: Boolean = true,
    /**
     * false = los regalos de una campaña existente NO se pudieron leer: el cuerpo no manda la
     * clave `regalos` (ausente = el servidor no los toca). Mandar [] los borraría.
     */
    val regalosCargados: Boolean = true,
) {
    companion object {
        fun desde(c: CampaniaGestion?, hoy: String): FormCampania = if (c == null) FormCampania(desde = hoy) else FormCampania(
            nombre = c.nombre,
            descripcion = c.descripcion.orEmpty(),
            tipo = c.tipo,
            cantidad = c.cantidad?.toString().orEmpty(),
            precio = c.precio?.let(::numeroEnCampo).orEmpty(),
            valor = c.valor?.let(::numeroEnCampo).orEmpty(),
            alcance = c.alcance,
            aplicaA = c.aplicaA ?: "Evaluación",
            serviciosIds = c.serviciosIds,
            desde = c.fechaInicio.ifBlank { hoy },
            hasta = c.fechaFin.orEmpty(),
            activo = c.activo,
            regalosCargados = false,
        )
    }
}

private fun aDec(t: String): Double? = t.trim().replace(',', '.').toDoubleOrNull()

/** Qué impide guardar (null = nada). Espejo de `validar()` del CampaniaForm web; el servidor revalida. */
fun problemaFormCampania(f: FormCampania): String? {
    if (f.nombre.isBlank()) return "Ponle un nombre a la campaña."
    when (f.tipo) {
        "paquete_fijo" -> {
            if ((f.cantidad.trim().toIntOrNull() ?: 0) <= 0) return "Indica cuántas sesiones incluye el paquete."
            if (aDec(f.precio).let { it == null || it < 0 }) return "Indica el precio del paquete."
        }
        "precio_fijo" -> if (aDec(f.precio).let { it == null || it < 0 }) return "Indica el precio de oferta."
        "porcentaje" -> if (aDec(f.valor).let { it == null || it < 0 || it > 100 }) return "El descuento debe ir entre 0 y 100%."
        "monto_fijo" -> if (aDec(f.valor).let { it == null || it < 0 }) return "Indica cuántos ${nombreMonedaPlural(monedaActiva())} se descuentan."
    }
    if (f.alcance == "servicios" && f.serviciosIds.isEmpty()) return "Elige al menos un servicio, o cambia el alcance a \"Todos\"."
    if (f.alcance == "citas" && f.tipo == "paquete_fijo") {
        return "Un paquete de sesiones no puede descontar una consulta o evaluación. Usa precio fijo, porcentaje o monto."
    }
    if (f.desde.isBlank()) return "Indica desde cuándo vale la campaña."
    if (f.hasta.isNotBlank() && f.hasta < f.desde) return "La fecha de fin no puede ser antes de la de inicio."
    if (f.regalos.any { it.procedimientoId.isNotBlank() && it.precio.isNotBlank() && aDec(it.precio).let { p -> p == null || p < 0 } }) {
        return "Revisa el precio de los regalos."
    }
    return null
}

/** Cuerpo de /api/staff/campania (port de `handleSubmit` del form web). `id` solo al editar. */
fun cuerpoGuardarCampania(f: FormCampania, id: String?): JsonObject = buildJsonObject {
    if (id != null) put("id", id)
    put("nombre", f.nombre.trim())
    put("descripcion", f.descripcion.trim().ifEmpty { null })
    put("tipo", f.tipo)
    put("alcance", f.alcance)
    put("aplica_a", if (f.alcance == "citas") f.aplicaA else null)
    put("fecha_inicio", f.desde)
    put("fecha_fin", f.hasta.ifBlank { null })
    put("cantidad", if (f.tipo == "paquete_fijo") f.cantidad.trim().toIntOrNull() else null)
    put("precio", if (f.tipo == "paquete_fijo" || f.tipo == "precio_fijo") aDec(f.precio) else null)
    put("valor", if (f.tipo == "porcentaje" || f.tipo == "monto_fijo") aDec(f.valor) else null)
    put("activo", f.activo)
    put("servicios_ids", buildJsonArray { if (f.alcance == "servicios") f.serviciosIds.forEach { add(JsonPrimitive(it)) } })
    if (f.regalosCargados) put("regalos", buildJsonArray {
        f.regalos.filter { it.procedimientoId.isNotBlank() }.forEach { r ->
            add(buildJsonObject {
                put("procedimiento_id", r.procedimientoId)
                put("dias_habiles", maxOf(1, r.dias.trim().toIntOrNull() ?: 1))
                put("precio", aDec(r.precio) ?: 0.0)
            })
        }
    })
}

private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

internal fun parsearCampania(o: JsonObject): CampaniaGestion? {
    val id = o.str("id") ?: return null
    return CampaniaGestion(
        id = id,
        nombre = o.str("nombre") ?: "Campaña",
        descripcion = o.str("descripcion"),
        tipo = o.str("tipo") ?: "porcentaje",
        cantidad = o.str("cantidad")?.toDoubleOrNull()?.toInt(),
        precio = o.str("precio")?.toDoubleOrNull(),
        valor = o.str("valor")?.toDoubleOrNull(),
        alcance = o.str("alcance") ?: "todos",
        aplicaA = o.str("aplica_a"),
        fechaInicio = o.str("fecha_inicio").orEmpty(),
        fechaFin = o.str("fecha_fin"),
        activo = o.str("activo") != "false",
        serviciosIds = (o["campania_servicios"] as? JsonArray)
            ?.mapNotNull { ((it as? JsonObject)?.str("procedimiento_id")) }.orEmpty(),
    )
}

/** Resultado de "Avisar": cuántos recibieron el push y cuántos no tienen la app. */
data class ResultadoAviso(val enviados: Int, val sinApp: Int)

object CampaniasRepo {

    private val http = crearHttpClient()

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val sinSesion = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val sinRed = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"),
    )

    /** Todas las campañas (como la web: la más reciente primero). */
    suspend fun listar(): List<CampaniaGestion> =
        Supabase.client.postgrest["campanias"]
            .select(Columns.raw("*, campania_servicios(procedimiento_id)")) { order("fecha_inicio", Order.DESCENDING) }
            .decodeList<JsonObject>()
            .mapNotNull(::parsearCampania)

    /** Regalos de una campaña (consulta plana, como el form web). */
    suspend fun regalosDe(id: String): List<RegaloForm> =
        Supabase.client.postgrest["campania_regalos"]
            .select(Columns.list("procedimiento_id, dias_habiles, precio")) { filter { eq("campania_id", id) } }
            .decodeList<JsonObject>()
            .mapNotNull { r ->
                val p = r.str("procedimiento_id") ?: return@mapNotNull null
                val precio = r.str("precio")?.toDoubleOrNull() ?: 0.0
                RegaloForm(p, r.str("dias_habiles")?.toDoubleOrNull()?.toInt()?.toString() ?: "1", if (precio > 0) numeroEnCampo(precio) else "")
            }

    private suspend fun enviar(llamada: suspend (String) -> HttpResponse): ResultadoEscritura {
        val tk = token() ?: return sinSesion
        return try {
            val resp = llamada(tk)
            AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            sinRed
        }
    }

    /** Crear (sin `id`) o editar (con `id`): ver [cuerpoGuardarCampania]. */
    suspend fun guardar(cuerpo: JsonObject): ResultadoEscritura = enviar { tk ->
        val url = "${Supabase.SITE_URL}/api/staff/campania"
        if (cuerpo["id"] != null) http.patch(url) {
            header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo.toString())
        } else http.post(url) {
            header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo.toString())
        }
    }

    /** Activar / desactivar (estado explícito: reintentar no lo da vuelta). */
    suspend fun cambiarEstado(id: String, activo: Boolean): ResultadoEscritura = enviar { tk ->
        http.post("${Supabase.SITE_URL}/api/staff/campania/estado") {
            header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("id", id); put("activo", activo) }.toString())
        }
    }

    suspend fun eliminar(id: String): ResultadoEscritura = enviar { tk ->
        http.post("${Supabase.SITE_URL}/api/staff/campania/eliminar") {
            header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("id", id) }.toString())
        }
    }

    /** 📣 Avisar por notificación a los pacientes con la app. segmento: "inactivos" | "todos". Tope 1/semana (lo aplica el servidor). */
    suspend fun avisar(campaniaId: String, segmento: String): Pair<ResultadoAviso?, String?> {
        val r = enviar { tk ->
            http.post("${Supabase.SITE_URL}/api/staff/campania/avisar") {
                header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("campaniaId", campaniaId); put("segmento", segmento) }.toString())
            }
        }
        if (!r.registrada) return null to (r.rechazo?.error ?: "No se pudo enviar el aviso")
        fun n(k: String) = (r.cuerpo?.get(k) as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        return ResultadoAviso(n("enviados"), n("sinApp")) to null
    }
}
