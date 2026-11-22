package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

// 📢 LLAMADO EN PANTALLA (21/11/2026, plan Plus) — gemelo de «📢 Llamar en
// pantalla» del menú de /citas web (lib/llamado-pantalla.ts). El televisor de la
// sala de espera muestra «Juan P. → Consultorio 3». La app solo pide el llamado:
// el nombre recortado, el consultorio y el plan los decide el servidor
// (POST /api/staff/cita/llamar, que ya acepta Bearer).

/** Una pantalla ACTIVA de la clínica. [sedeId] null = la de toda la clínica. */
data class PantallaLlamadoRef(val sedeId: String?)

/** La pantalla que le toca a una sede: la suya o, si no tiene, la general (gemela de pantallaDeSede). */
fun pantallaDeSede(pantallas: List<PantallaLlamadoRef>, sedeId: String?): PantallaLlamadoRef? =
    (if (sedeId != null) pantallas.firstOrNull { it.sedeId == sedeId } else null) ?: pantallas.firstOrNull { it.sedeId == null }

/**
 * ¿Se ofrece «📢 Llamar en pantalla»? Cita de HOY en curso (Pendiente o
 * Confirmada) y con una pantalla activa para su sede. El permiso (agendar o
 * sesiones) lo resuelve quien llama, como en la web (`puedeMoverSala`).
 */
fun ofreceLlamarPantalla(estado: String, fecha: String, hoy: String, sedeId: String?, pantallas: List<PantallaLlamadoRef>): Boolean =
    (estado == "Pendiente" || estado == "Confirmada") && fecha.take(10) == hoy && pantallaDeSede(pantallas, sedeId) != null

/** Lo que se le dice a quien pulsó (el aviso emergente). */
data class ResultadoLlamado(val ok: Boolean, val texto: String)

/**
 * Puro: el aviso a partir de la respuesta del servidor.
 * 200 `{ ok, llamado: true, nombre, destino }` → «📢 Elena H. → Consultorio 1».
 * 200 `{ llamado: false, motivo }` → por qué no salió. Error → su mensaje.
 */
fun resultadoLlamado(exito: Boolean, o: JsonObject?): ResultadoLlamado {
    fun s(k: String): String? = (o?.get(k) as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }
    if (!exito) return ResultadoLlamado(false, s("error") ?: "No se pudo llamar al paciente.")
    val llamado = (o?.get("llamado") as? JsonPrimitive)?.booleanOrNull == true
    if (!llamado) return ResultadoLlamado(false, when (s("motivo")) {
        "sin_pantalla" -> "La clínica no tiene una pantalla activa para esta sede."
        "repetido" -> "Ya se llamó hace un momento."
        else -> "No se pudo llamar al paciente."
    })
    val nombre = s("nombre"); val destino = s("destino")
    return ResultadoLlamado(true, when {
        nombre != null && destino != null -> "📢 $nombre → $destino"
        nombre != null -> "📢 Llamando a $nombre"
        else -> "📢 Llamado en pantalla"
    })
}

object LlamadoPantallaRepo {
    private val http = crearHttpClient()

    /** Las pantallas activas de la clínica (RLS). Sin la tabla o sin señal: ninguna (no se ofrece el botón). */
    suspend fun pantallasActivas(): List<PantallaLlamadoRef> = runCatching {
        Supabase.client.postgrest["pantallas_llamado"].select(Columns.list("sede_id, activa")) { filter { eq("activa", true) } }
            .decodeList<JsonObject>().map { o -> PantallaLlamadoRef((o["sede_id"] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }) }
    }.getOrDefault(emptyList())

    /** Pide el llamado. Repetirlo es «llamar de nuevo». */
    suspend fun llamar(citaId: String): ResultadoLlamado {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return ResultadoLlamado(false, "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val r = http.post("${Supabase.SITE_URL}/api/staff/cita/llamar") {
                header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("citaId", citaId) }.toString())
            }
            val cuerpo = runCatching { Json.parseToJsonElement(r.bodyAsText()).jsonObject }.getOrNull()
            resultadoLlamado(r.status.isSuccess(), cuerpo)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            ResultadoLlamado(false, "Sin conexión. Revisa tu internet.")
        }
    }
}
