package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

// «📋 CONTEXTO DEL CASO» en el cierre / evolución de una sesión (QA psicología
// 2026-10-23). Gemelo de `components/fisio/PanelCasoCierre.tsx` (web): lo que el
// profesional LEE mientras escribe la evolución — lo anotado en la consulta /
// evaluación que originó el tratamiento — más la sesión anterior. La elección de
// la atención y las secciones las arma el servidor (`lib/contexto-caso.ts`,
// GET /api/staff/contexto-caso); acá solo se lee y se decide cómo se muestra.
// En el celular todo va arriba y plegable: el contexto abierto en la sesión #1;
// la sesión anterior cerrada, salvo en fisioterapia (DALU igual que antes).

/** Una sección del contexto ("Motivo", "Desde cuándo", "Plan terapéutico"…). */
data class SeccionContextoCaso(val clave: String, val etiqueta: String, val texto: String)

/**
 * El contexto del caso. [fuenteTitulo]/[fuenteFecha]: de qué atención sale
 * ("Consulta · 12/10"); null = solo datos sueltos (diagnóstico del tratamiento…).
 */
data class ContextoCaso(
    val fuenteTitulo: String?,
    val fuenteFecha: String?,
    val secciones: List<SeccionContextoCaso>,
) {
    /** ¿Hay algo que mostrar? Sin secciones no hay bloque. */
    val tieneDatos: Boolean get() = secciones.isNotEmpty()

    /** El subtítulo del bloque: "Consulta · 12/10" (null sin fuente). */
    fun detalleFuente(): String? = fuenteTitulo?.let { t ->
        t + (fuenteFecha?.let { " · ${fechaDiaMes(it)}" } ?: "")
    }
}

private fun JsonObject.str(k: String): String? =
    (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

/**
 * La respuesta del servidor (`{ ok, psico, contexto: { fuente, secciones[] } }`)
 * → [ContextoCaso]. Las secciones sin texto se saltan. null = respuesta ilegible.
 */
fun parsearContextoCaso(raiz: JsonObject?): ContextoCaso? {
    val ctx = raiz?.get("contexto") as? JsonObject ?: return null
    val fuente = ctx["fuente"] as? JsonObject
    val secciones = (ctx["secciones"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val texto = o.str("texto")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        SeccionContextoCaso(
            clave = o.str("clave") ?: "",
            etiqueta = o.str("etiqueta") ?: "",
            texto = texto,
        )
    }
    return ContextoCaso(
        fuenteTitulo = fuente?.str("titulo")?.takeIf { it.isNotBlank() },
        fuenteFecha = fuente?.str("fecha")?.takeIf { it.isNotBlank() },
        secciones = secciones,
    )
}

/** "2026-10-12" → "12/10" (como la cabecera de la sesión anterior del cierre). */
fun fechaDiaMes(iso: String): String {
    val p = iso.take(10).split("-")
    return if (p.size == 3) "${p[2]}/${p[1]}" else iso
}

/**
 * ¿El «Contexto del caso» arranca abierto? En la sesión #1 (o sin número y sin
 * sesión anterior): es cuando más falta lo de la consulta. `primeraSesion` de
 * CierreSesion (web).
 */
fun contextoAbiertoInicial(numero: Int?, hayAnterior: Boolean): Boolean =
    numero == 1 || ((numero == null || numero <= 0) && !hayAnterior)

/**
 * ¿La «Sesión anterior» arranca abierta? En fisioterapia sí (DALU la tenía a la
 * vista y la sigue teniendo, web caef9c72); en el resto, plegada.
 */
fun anteriorAbiertaInicial(esFisio: Boolean): Boolean = esFisio

/** Texto largo (más de 180 caracteres o 4 líneas): se muestra recortado con «Ver más». */
fun esTextoLargo(texto: String, maxCaracteres: Int = 180, maxLineas: Int = 4): Boolean {
    val t = texto.trim()
    return t.length > maxCaracteres || t.split('\n').size > maxLineas
}

private val etiquetasCamposPsico = listOf(
    "Temas trabajados y evolución", "Estado emocional / examen mental",
    "Tareas e indicaciones", "Plan para la próxima sesión",
)

/**
 * Psicología: la evolución de la sesión anterior leída campo por campo
 * (etiqueta, texto), sin los vacíos. La nota se guarda con rótulos
 * ([textoEvolucionPsico]); una nota libre sale entera como "Temas trabajados y
 * evolución". Vacío = no hay evolución.
 */
fun camposEvolucionPsicoAnterior(mejorias: String?): List<Pair<String, String>> {
    if (mejorias.isNullOrBlank()) return emptyList()
    val e = leerEvolucionPsico(mejorias)
    return etiquetasCamposPsico.zip(listOf(e.evolucion, e.estado, e.tareas, e.plan))
        .filter { it.second.isNotBlank() }
}

/** Lectura del contexto del caso (solo lectura, sin cola offline). */
object ContextoCasoRepo {
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /**
     * GET /api/staff/contexto-caso. Exige «Historia clínica» (servidor): quien no
     * lo tiene ni lo pide. null = sin acceso, sin red o sin datos — el cierre
     * funciona igual, sin el bloque.
     */
    suspend fun leer(pacienteId: String, tratamientoId: String?, especialidadId: String?): ContextoCaso? {
        val tk = token() ?: return null
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/contexto-caso") {
                header("Authorization", "Bearer $tk")
                parameter("pacienteId", pacienteId)
                tratamientoId?.takeIf { it.isNotBlank() }?.let { parameter("tratamientoId", it) }
                especialidadId?.takeIf { it.isNotBlank() }?.let { parameter("especialidadId", it) }
            }
            if (resp.status.value !in 200..299) null
            else parsearContextoCaso(runCatching { json.parseToJsonElement(resp.bodyAsText()) as? JsonObject }.getOrNull())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
