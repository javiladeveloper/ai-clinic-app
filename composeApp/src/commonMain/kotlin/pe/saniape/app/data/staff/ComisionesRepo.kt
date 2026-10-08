package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/**
 * 💰 Comisiones. TODO lo que tiene dinero va por los MISMOS endpoints que usa
 * la web (/api/staff/comision-plantillas/…): el servidor recalcula el monto
 * desde los tratamientos reales, cierra el doble pago con su índice único
 * (uq_pago_plantilla_tramo) y registra el egreso en la caja. La app no calcula
 * ni un sol: muestra lo que el servidor dijo y le pide que pague.
 *
 * Lecturas simples (personal activo, equipo) van directo a Supabase con la RLS.
 */
object ComisionesRepo {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val http = crearHttpClient()
    private const val BASE = "/api/staff/comision-plantillas"

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val sinSesion = ResultadoEscritura(
        registrada = false,
        rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val sinRed = ResultadoEscritura(
        registrada = false,
        rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"),
    )

    internal fun mensajeDeError(cuerpo: String): String? =
        runCatching { ((json.parseToJsonElement(cuerpo) as? JsonObject)?.get("error") as? JsonPrimitive)?.contentOrNull }
            .getOrNull()?.takeIf { it.isNotBlank() }

    internal fun parsearEsquemas(cuerpo: String): EsquemasComision? =
        runCatching { json.decodeFromString(EsquemasComision.serializer(), cuerpo) }.getOrNull()

    internal fun parsearHistorico(cuerpo: String): HistoricoComisiones? =
        runCatching { json.decodeFromString(HistoricoComisiones.serializer(), cuerpo) }.getOrNull()

    internal fun parsearDetalle(cuerpo: String): DetalleComision? =
        runCatching { json.decodeFromString(DetalleComision.serializer(), cuerpo) }.getOrNull()

    /** GET genérico: el dato o el motivo del fallo (texto para la pantalla). */
    private suspend fun <T> leer(
        ruta: String,
        serializer: KSerializer<T>,
        porDefecto: String,
        params: Map<String, String?> = emptyMap(),
    ): Pair<T?, String?> {
        val tk = token() ?: return null to "Tu sesión expiró. Vuelve a entrar."
        return try {
            val resp = http.get("${Supabase.SITE_URL}$ruta") {
                header("Authorization", "Bearer $tk")
                params.forEach { (k, v) -> if (!v.isNullOrBlank()) parameter(k, v) }
            }
            val texto = resp.bodyAsText()
            if (resp.status.value in 200..299) {
                runCatching { json.decodeFromString(serializer, texto) }.getOrNull()?.let { it to null }
                    ?: (null to "Respuesta inesperada del servidor.")
            } else null to (mensajeDeError(texto) ?: porDefecto)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            null to "Sin conexión. Revisa tu internet."
        }
    }

    /** Esquemas + avance de cada persona. `periodo` 'YYYY-MM' = un mes cerrado; null = en curso. */
    suspend fun esquemas(periodo: String?): Pair<EsquemasComision?, String?> =
        leer(BASE, EsquemasComision.serializer(), "No se pudieron cargar las comisiones", mapOf("periodo" to periodo))

    /** Qué paquetes contaron para un profesional (paciente → tratamiento → sesiones y pagos). */
    suspend fun detalle(plantillaId: String, terapeutaId: String, periodo: String?): Pair<DetalleComision?, String?> =
        leer("$BASE/detalle", DetalleComision.serializer(), "No se pudo cargar el detalle",
            mapOf("plantilla" to plantillaId, "terapeuta" to terapeutaId, "periodo" to periodo))

    /** Lo pagado (sistema + egresos de comisión registrados a mano). Solo Admin. */
    suspend fun historico(desde: String?, hasta: String?, terapeutaId: String?, origen: String?): Pair<HistoricoComisiones?, String?> =
        leer("$BASE/historico", HistoricoComisiones.serializer(), "No se pudo cargar el histórico",
            mapOf("desde" to desde, "hasta" to hasta, "terapeutaId" to terapeutaId, "origen" to origen))

    private suspend fun enviar(metodo: HttpMethod, ruta: String, cuerpo: JsonObject?, params: Map<String, String> = emptyMap()): ResultadoEscritura {
        val tk = token() ?: return sinSesion
        return try {
            val resp = http.request("${Supabase.SITE_URL}$ruta") {
                method = metodo
                header("Authorization", "Bearer $tk")
                params.forEach { (k, v) -> parameter(k, v) }
                if (cuerpo != null) {
                    contentType(ContentType.Application.Json)
                    setBody(cuerpo.toString())
                }
            }
            AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            sinRed
        }
    }

    /**
     * Crear/editar un esquema. `idNuevo` (alta) lo genera la pantalla UNA vez por
     * formulario: si el primer intento llegó y se perdió la respuesta, el
     * reintento actualiza ese mismo esquema en vez de crear un gemelo.
     */
    suspend fun guardar(form: FormEsquema, idNuevo: String?): ResultadoEscritura =
        enviar(HttpMethod.Post, BASE, ReglasComisiones.cuerpoGuardar(form, idNuevo))

    /** Quitar = desactivar (los pagos ya hechos siguen apuntando a él). */
    suspend fun quitar(id: String): ResultadoEscritura =
        enviar(HttpMethod.Delete, BASE, null, mapOf("id" to id))

    /**
     * Pagar el bono/porcentaje del período que se está viendo. El monto lo
     * recalcula el servidor; un segundo toque choca con el índice único (409).
     */
    suspend fun pagar(plantillaId: String, a: AvanceComision, metodo: String, periodoRef: String?): ResultadoEscritura =
        enviar(HttpMethod.Post, "$BASE/pagar", cuerpoPagar(plantillaId, a, metodo, periodoRef))

    internal fun cuerpoPagar(plantillaId: String, a: AvanceComision, metodo: String, periodoRef: String?): JsonObject = buildJsonObject {
        put("plantillaId", plantillaId)
        put("terapeutaId", a.terapeutaId)
        put("perfilId", a.perfilId)
        put("metodo", metodo)
        put("periodo", periodoRef)
    }

    /** Deshacer un pago: quita el egreso de la caja y reabre el tramo. */
    suspend fun anular(pagoId: String): ResultadoEscritura =
        enviar(HttpMethod.Post, "$BASE/anular", buildJsonObject { put("pagoId", pagoId) })

    /** Profesionales ACTIVOS (los que la web ofrece para asignar y filtrar). */
    suspend fun personalActivo(): List<PersonaComision> = try {
        Supabase.client.postgrest["terapeutas"]
            .select(Columns.list("id, nombre, perfil_id, estado")) {
                filter { eq("estado", "Activo") }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
            .mapNotNull { o ->
                val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                PersonaComision(id, (o["nombre"] as? JsonPrimitive)?.contentOrNull ?: "—", (o["perfil_id"] as? JsonPrimitive)?.contentOrNull)
            }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (_: Exception) { emptyList() }

    /** Miembros del equipo SIN ficha de profesional (recepción): para esquemas por evaluaciones. */
    suspend fun equipoSinFicha(personal: List<PersonaComision>): List<MiembroEquipoComision> = try {
        val conFicha = personal.mapNotNull { it.perfilId }.toSet()
        Supabase.client.postgrest["perfiles"]
            .select(Columns.list("id, nombre, rol")) { order("nombre", Order.ASCENDING) }
            .decodeList<JsonObject>()
            .mapNotNull { o ->
                val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                if (id in conFicha) return@mapNotNull null
                val nombre = (o["nombre"] as? JsonPrimitive)?.contentOrNull
                val rol = (o["rol"] as? JsonPrimitive)?.contentOrNull ?: ""
                MiembroEquipoComision(id, nombre ?: rol.ifBlank { "Sin nombre" }, rol)
            }
            .distinctBy { it.id }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (_: Exception) { emptyList() }
}
