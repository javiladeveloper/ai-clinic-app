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
import kotlinx.serialization.json.doubleOrNull
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

/** Un concepto de un pago de planilla («Faltas (1)», resta). */
data class LineaPago(val concepto: String, val monto: Double, val resta: Boolean)

/**
 * Un pago de planilla YA HECHO de quien mira (26/11/2026), tal como lo devuelve
 * /api/staff/planilla/mia: el periodo y los conceptos vienen en texto del servidor.
 */
data class MiPagoPlanilla(
    val id: String, val periodo: String, val neto: Double,
    /** «30/11/2026», o null. */
    val pagadoEl: String?, val metodo: String?, val lineas: List<LineaPago>,
)

/** Parseo tolerante de «Mis pagos» (sin el agregado o sin pagos: lista vacía). Puro. */
fun parsearMisPagos(o: JsonObject?): List<MiPagoPlanilla> =
    (o?.get("pagos") as? JsonArray).orEmpty().mapNotNull {
        val p = it as? JsonObject ?: return@mapNotNull null
        MiPagoPlanilla(
            id = p.txt("id") ?: return@mapNotNull null,
            periodo = p.txt("periodo") ?: return@mapNotNull null,
            neto = (p["neto"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
            pagadoEl = p.txt("pagado_at")?.take(10)?.split("-")?.takeIf { f -> f.size == 3 }?.reversed()?.joinToString("/"),
            metodo = p.txt("metodo_pago"),
            lineas = (p["lineas"] as? JsonArray).orEmpty().mapNotNull { l ->
                val x = l as? JsonObject ?: return@mapNotNull null
                LineaPago(x.txt("concepto") ?: return@mapNotNull null, (x["monto"] as? JsonPrimitive)?.doubleOrNull ?: 0.0, x.si("resta"))
            },
        )
    }

/**
 * El cuerpo de marcar: con la llave del QR escaneado va como origen «qr». Lleva dónde
 * está el celular: desde el 27/11/2026 el servidor solo acepta la marca si es dentro
 * de la clínica. Puro.
 */
fun cuerpoMarcar(qr: String?, lat: Double? = null, lng: Double? = null, precision: Double? = null): JsonObject = buildJsonObject {
    put("accion", "marcar")
    if (qr.isNullOrBlank()) put("origen", "app") else { put("origen", "qr"); put("qr", qr) }
    if (lat != null && lng != null) {
        put("lat", lat); put("lng", lng)
        if (precision != null && precision >= 0) put("precision", precision)
    }
}

/** Lo que se le dice a quien no dio la ubicación: sin ella no hay marca. */
const val MENSAJE_SIN_UBICACION = "La asistencia se marca solo desde la clínica. Enciende la ubicación del celular y dale permiso a Sania."

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

    /** 💵 Mis pagos de planilla ya hechos. Lista vacía si no hay, no hay red o el servidor aún no lo tiene. */
    suspend fun misPagos(): List<MiPagoPlanilla> {
        val tk = token() ?: return emptyList()
        return try {
            val (ok, obj) = leer(http.get("${Supabase.SITE_URL}/api/staff/planilla/mia") { header("Authorization", "Bearer $tk") })
            if (ok) parsearMisPagos(obj) else emptyList()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Marca la entrada o la salida que toque (lo decide el servidor). */
    suspend fun marcar(qr: String? = null, lat: Double? = null, lng: Double? = null, precision: Double? = null): ResultadoMarca {
        val tk = token() ?: return ResultadoMarca(false, "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val (ok, obj) = leer(http.post("${Supabase.SITE_URL}/api/staff/asistencia") {
                header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json)
                // Con la llave del QR de la recepción, la marca queda como «QR» (el servidor la valida).
                setBody(cuerpoMarcar(qr, lat, lng, precision).toString())
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
