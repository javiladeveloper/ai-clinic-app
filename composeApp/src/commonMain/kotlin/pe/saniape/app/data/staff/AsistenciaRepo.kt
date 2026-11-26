package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** Una marca de hoy, ya en texto (la hora es la de la clínica). */
data class MarcaHoy(val tipo: String, val hora: String, val medio: String)

/** Un día del mes, ya resumido por el servidor. */
data class DiaAsistencia(val fecha: String, val dia: String, val marcas: String, val estado: String, val detalle: String, val corregida: Boolean)

data class MesAsistencia(val dias: Int, val tardanzas: Int, val tardanzaTexto: String, val faltas: Int, val horas: String, val extra: String)

/**
 * «Mi asistencia» tal como la devuelve /api/staff/asistencia/mia: TODO viene
 * calculado por el servidor (tardanzas, horas, qué toca marcar). La app no
 * repite ninguna regla del control de asistencia.
 */
data class MiAsistencia(
    /** El agregado está activo en la clínica. */
    val activa: Boolean,
    /** Esta persona está en la lista de quienes marcan. */
    val enLista: Boolean,
    val nombre: String?,
    /** "entrada" | "salida": lo que toca marcar ahora. */
    val siguiente: String,
    val jornadaHoy: String?,
    val estadoHoy: String?,
    val tardanzaHoyMin: Int,
    val marcasHoy: List<MarcaHoy>,
    val mes: MesAsistencia,
    val dias: List<DiaAsistencia>,
)

data class ResultadoMarca(val ok: Boolean, val mensaje: String, val sinRed: Boolean = false)

private fun JsonObject.txt(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
private fun JsonObject.ent(k: String): Int = (this[k] as? JsonPrimitive)?.intOrNull ?: 0
private fun JsonObject.si(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull == true

/** Parseo tolerante (un backend viejo o un campo que falte no rompe la pantalla). Puro. */
fun parsearMiAsistencia(o: JsonObject): MiAsistencia {
    val mes = o["mes"] as? JsonObject
    return MiAsistencia(
        activa = o.si("activa"),
        enLista = o.si("enLista"),
        nombre = o.txt("nombre"),
        siguiente = if (o.txt("siguiente") == "salida") "salida" else "entrada",
        jornadaHoy = o.txt("jornadaHoy"),
        estadoHoy = o.txt("estadoHoy"),
        tardanzaHoyMin = o.ent("tardanzaHoyMin"),
        marcasHoy = (o["marcasHoy"] as? JsonArray).orEmpty().mapNotNull {
            val m = it as? JsonObject ?: return@mapNotNull null
            MarcaHoy(m.txt("tipo") ?: "entrada", m.txt("hora") ?: return@mapNotNull null, m.txt("medio") ?: "")
        },
        mes = MesAsistencia(
            dias = mes?.ent("dias") ?: 0, tardanzas = mes?.ent("tardanzas") ?: 0, tardanzaTexto = mes?.txt("tardanzaTexto") ?: "0 min",
            faltas = mes?.ent("faltas") ?: 0, horas = mes?.txt("horas") ?: "0 min", extra = mes?.txt("extra") ?: "0 min",
        ),
        dias = (o["dias"] as? JsonArray).orEmpty().mapNotNull {
            val d = it as? JsonObject ?: return@mapNotNull null
            DiaAsistencia(d.txt("fecha") ?: return@mapNotNull null, d.txt("dia") ?: "", d.txt("marcas") ?: "", d.txt("estado") ?: "", d.txt("detalle") ?: "", d.si("corregida"))
        },
    )
}

/** El cuerpo de marcar: con la llave del QR escaneado va como origen «qr». Puro. */
fun cuerpoMarcar(qr: String?): JsonObject = buildJsonObject {
    put("accion", "marcar")
    if (qr.isNullOrBlank()) put("origen", "app") else { put("origen", "qr"); put("qr", qr) }
}

/** «✓ Entrada registrada a las 08:02» a partir de la respuesta de marcar. Puro. */
fun mensajeDeMarca(o: JsonObject?): String {
    val tipo = if (o?.txt("tipo") == "salida") "Salida" else "Entrada"
    val hora = o?.txt("hora")
    return if (hora != null) "✓ $tipo registrada a las $hora" else "✓ $tipo registrada"
}

/**
 * 🕘 Control de asistencia en la app (26/11/2026): ver lo propio y marcar. Las
 * marcas las escribe el servidor con SU hora (nunca la del teléfono) por
 * /api/staff/asistencia; el resto del módulo (personal, reportes, correcciones)
 * vive en la web.
 */
object AsistenciaRepo {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private suspend fun leer(resp: HttpResponse): Pair<Boolean, JsonObject?> {
        val obj = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
        return (resp.status.value in 200..299) to obj
    }

    /** null = no se pudo leer (sin red o sesión vencida). */
    suspend fun mia(): MiAsistencia? {
        val tk = token() ?: return null
        return try {
            val (ok, obj) = leer(http.get("${Supabase.SITE_URL}/api/staff/asistencia/mia") { header("Authorization", "Bearer $tk") })
            if (ok && obj != null) parsearMiAsistencia(obj) else null
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Marca la entrada o la salida que toque (lo decide el servidor). */
    suspend fun marcar(qr: String? = null): ResultadoMarca {
        val tk = token() ?: return ResultadoMarca(false, "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val (ok, obj) = leer(http.post("${Supabase.SITE_URL}/api/staff/asistencia") {
                header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json)
                // Con la llave del QR de la recepción, la marca queda como «QR» (el servidor la valida).
                setBody(cuerpoMarcar(qr).toString())
            })
            if (ok) ResultadoMarca(true, mensajeDeMarca(obj))
            else ResultadoMarca(false, obj?.txt("error")?.takeIf { it.isNotBlank() } ?: "No se pudo marcar.")
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin respuesta: la marca PUDO haber entrado. No se reintenta sola (el
            // servidor rechaza una segunda en menos de un minuto, pero mejor mirar).
            ResultadoMarca(false, "Sin conexión. Revisa si tu marca quedó registrada antes de volver a marcar.", sinRed = true)
        }
    }
}
