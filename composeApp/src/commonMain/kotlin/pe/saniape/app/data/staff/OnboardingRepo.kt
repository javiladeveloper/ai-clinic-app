package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** Una tarea de "Primeros pasos" (se marca sola en el servidor leyendo la base). */
@Serializable
data class TareaPrimerosPasos(
    /** profesional | paciente | cita | cobro | equipo | whatsapp | pagina */
    val clave: String,
    val icono: String = "",
    val titulo: String = "",
    val desc: String = "",
    val hecho: Boolean = false,
)

/** `GET /api/onboarding/estado`: la tarjeta "Primeros pasos". */
@Serializable
data class EstadoPrimerosPasos(
    val mostrar: Boolean = false,
    val estado: String? = null,
    val tareas: List<TareaPrimerosPasos> = emptyList(),
    val hechas: Int = 0,
    val total: Int = 0,
    val completas: Boolean = false,
    val tieneEjemplo: Boolean = false,
    val slug: String? = null,
)

/** `GET/POST /api/onboarding/ejemplo`: qué se borraría / qué se borró. */
@Serializable
data class ResumenEjemplo(
    val ok: Boolean = false,
    val pacientes: List<String> = emptyList(),
    val citas: Int = 0,
    val tratamientos: List<String> = emptyList(),
    val cobros: Double = 0.0,
    val n: Int = 0,
    val borrado: Boolean = false,
)

/**
 * Texto de la confirmación de "Borrar datos de ejemplo", con los NOMBRES de hoy
 * (gemelo de `textoConfirmacionEjemplo` de la web).
 */
fun textoConfirmacionEjemplo(r: ResumenEjemplo): String {
    val partes = listOfNotNull(
        r.pacientes.takeIf { it.isNotEmpty() }?.let { "Paciente${if (it.size > 1) "s" else ""}: ${it.joinToString(", ")}" },
        r.citas.takeIf { it > 0 }?.let { "$it cita${if (it > 1) "s" else ""}" },
        r.tratamientos.takeIf { it.isNotEmpty() }?.let { "Tratamiento${if (it.size > 1) "s" else ""}: ${it.joinToString(", ")}" },
        r.cobros.takeIf { it > 0 }?.let { "Cobros por ${simboloActivo()} ${dosDecimales(it)}" },
    )
    return if (partes.isEmpty()) "No hay datos de ejemplo." else "${partes.joinToString(" · ")}. Nada más."
}

private fun dosDecimales(n: Double): String {
    val cent = kotlin.math.round(n * 100).toLong()
    return "${cent / 100}.${(cent % 100).toString().padStart(2, '0')}"
}

/** URL pública de la clínica (`https://<slug>.saniape.com`), gemela de `urlClinica` de la web. */
fun urlPaginaClinica(slug: String, siteUrl: String = Supabase.SITE_URL): String {
    val prod = siteUrl.contains("saniape", ignoreCase = true)
    val host = if (prod) siteUrl.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "").replace(Regex("^www\\.", RegexOption.IGNORE_CASE), "").trimEnd('/')
    else "saniape.com" // en debug el sitio es el Next local: la página pública real vive en prod
    return "https://$slug.$host"
}

/**
 * Onboarding v2 (Primeros pasos, datos de ejemplo, página pública). Todo vive
 * en la web: la app solo pide y pinta.
 */
object OnboardingRepo {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val http by lazy { crearHttpClient() }

    /** Resultado de una llamada: dato, o el mensaje para la persona. */
    sealed class R<out T> {
        data class Ok<T>(val dato: T) : R<T>()
        data class Error(val mensaje: String, val status: Int = 0) : R<Nothing>()
    }

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    internal fun parsearEstado(texto: String): EstadoPrimerosPasos? =
        runCatching { json.decodeFromString(EstadoPrimerosPasos.serializer(), texto) }.getOrNull()

    internal fun parsearEjemplo(texto: String): ResumenEjemplo? =
        runCatching { json.decodeFromString(ResumenEjemplo.serializer(), texto) }.getOrNull()

    private fun error(resp: HttpResponse, cuerpo: String, porDefecto: String): R.Error =
        R.Error(pe.saniape.app.tutoriales.mensajeError(cuerpo) ?: porDefecto, resp.status.value)

    private suspend fun <T> llamar(porDefecto: String, bloque: suspend (String) -> R<T>): R<T> {
        val tk = token() ?: return R.Error("Tu sesión expiró. Vuelve a entrar.", 401)
        return try { bloque(tk) } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            R.Error("Sin conexión. Revisa tu internet.")
        }
    }

    suspend fun estado(): R<EstadoPrimerosPasos> = llamar("No se pudieron cargar tus primeros pasos.") { tk ->
        val resp = http.get("${Supabase.SITE_URL}/api/onboarding/estado") { header("Authorization", "Bearer $tk") }
        val cuerpo = resp.bodyAsText()
        if (resp.status.value in 200..299) parsearEstado(cuerpo)?.let { R.Ok(it) } ?: R.Error("Respuesta inesperada del servidor.")
        else error(resp, cuerpo, "No se pudieron cargar tus primeros pasos.")
    }

    /** Minimizar / descartar / volver a mostrar la tarjeta (solo Admin). */
    suspend fun cambiarEstado(estado: String): R<String> = llamar("No se pudo guardar. Intenta de nuevo.") { tk ->
        val resp = http.patch("${Supabase.SITE_URL}/api/onboarding/primeros-pasos") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody("""{"estado":"$estado"}""")
        }
        val cuerpo = resp.bodyAsText()
        if (resp.status.value in 200..299) R.Ok(estado) else error(resp, cuerpo, "No se pudo guardar. Intenta de nuevo.")
    }

    /** Simulación: qué se borraría (para la confirmación). */
    suspend fun simularEjemplo(): R<ResumenEjemplo> = llamar("No se pudo revisar los datos de ejemplo.") { tk ->
        val resp = http.get("${Supabase.SITE_URL}/api/onboarding/ejemplo") { header("Authorization", "Bearer $tk") }
        val cuerpo = resp.bodyAsText()
        if (resp.status.value in 200..299) parsearEjemplo(cuerpo)?.let { R.Ok(it) } ?: R.Error("Respuesta inesperada del servidor.")
        else error(resp, cuerpo, "No se pudo revisar los datos de ejemplo.")
    }

    /** Borra los datos de ejemplo en UNA transacción (POST = DELETE en el contrato). */
    suspend fun borrarEjemplo(): R<ResumenEjemplo> = llamar("No se pudieron borrar los datos de ejemplo.") { tk ->
        val resp = http.post("${Supabase.SITE_URL}/api/onboarding/ejemplo") { header("Authorization", "Bearer $tk") }
        val cuerpo = resp.bodyAsText()
        if (resp.status.value in 200..299) parsearEjemplo(cuerpo)?.let { R.Ok(it) } ?: R.Ok(ResumenEjemplo(ok = true, borrado = true))
        else error(resp, cuerpo, "No se pudieron borrar los datos de ejemplo.")
    }

    private var paginaAvisada = false

    /** "Ya vi / compartí mi página": una vez por sesión de la app (idempotente en el servidor). */
    suspend fun avisarPaginaVista() {
        if (paginaAvisada) return
        val tk = token() ?: return
        try {
            val r = http.post("${Supabase.SITE_URL}/api/onboarding/pagina") { header("Authorization", "Bearer $tk") }
            if (r.status.value in 200..299) paginaAvisada = true
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) { /* se reintenta la próxima vez */ }
    }

    /** Slug de la página pública de la clínica (null = no tiene). Lectura directa con RLS. */
    suspend fun slugClinica(clinicaId: String): String? = try {
        Supabase.client.postgrest["clinicas"]
            .select(Columns.list("slug")) { filter { eq("id", clinicaId) } }
            .decodeList<JsonObject>()
            .firstOrNull()?.let { (it["slug"] as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (_: Exception) { null }
}
