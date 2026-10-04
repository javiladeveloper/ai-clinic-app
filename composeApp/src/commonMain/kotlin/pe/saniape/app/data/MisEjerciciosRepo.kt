package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Un ejercicio de apoyo, tal como lo manda /api/paciente/mis-ejercicios. */
data class EjercicioPaciente(
    val itemId: String,
    val nombre: String,
    val gifUrl: String?,
    val posturaInicialUrl: String?,
    val posturaFinalUrl: String?,
    val pasos: List<String>,
    /** Ids (`silla`, `banda_elastica`…); el nombre sale de `nombreMaterial`. */
    val materiales: List<String>,
    /** Lo que el fisio escribió para ESTE paciente; va antes que los pasos. */
    val indicaciones: String?,
    val vecesAlDia: Int,
    /** 1 = lunes … 7 = domingo. */
    val dias: List<Int>,
    val lado: String?,
    val carga: String?,
    val descansoSeg: Int?,
    /** Si no es null: "Si el dolor pasa de N de 10, detente". */
    val dolorMaximo: Int?,
    /** "2 series × 12 repeticiones" — ya armado por el servidor: se muestra tal cual. */
    val dosisTexto: String,
    /** "Lun · Mié · Vie" — ya armado por el servidor: se muestra tal cual. */
    val diasTexto: String,
    val tocaHoy: Boolean,
    val hechoHoy: Boolean,
    val dolorHoy: Int?,
)

/** Un plan vigente y visible del paciente (uno por tratamiento). */
data class PlanEjerciciosPaciente(
    val id: String,
    val titulo: String,
    /** 'sesion' | 'alta'. */
    val momento: String,
    /** "Sesión #4 · 24/06/26" · "Al terminar el tratamiento" — del servidor, tal cual. */
    val etiqueta: String,
    /** "Hasta la próxima sesión" · "Para seguir en casa" — del servidor, tal cual. */
    val vigencia: String,
    val motivo: String?,
    val indicaciones: String?,
    val precauciones: String?,
    val clinica: String?,
    val indicadoPor: String?,
    val ejercicios: List<EjercicioPaciente>,
)

/** Respuesta completa: los planes + el día de HOY según el servidor (hora de Lima). */
data class MisEjercicios(
    val planes: List<PlanEjerciciosPaciente>,
    val hoy: String,
    /** 1 = lunes … 7 = domingo (el de [hoy]). 0 si el servidor no lo mandó. */
    val diaSemana: Int,
)

/**
 * Convierte el JSON de GET /api/paciente/mis-ejercicios (contrato en la web:
 * docs/app-contrato-portal-paciente.md §3). Tolerante: un plan sin id o sin
 * ejercicios se descarta en vez de romper la sección. Null = no es JSON válido.
 *
 * Nada se recalcula: etiqueta, vigencia, textos de la dosis y de los días, si
 * toca hoy y si ya lo hizo vienen resueltos por el servidor.
 */
fun parsearMisEjercicios(texto: String): MisEjercicios? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it != "null" && it.isNotBlank() }
    fun JsonObject.ent(k: String): Int? =
        (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.toInt()
    fun JsonObject.si(k: String): Boolean = (this[k] as? JsonPrimitive)?.content == "true"
    fun JsonObject.textos(k: String): List<String> =
        (this[k] as? JsonArray).orEmpty().mapNotNull { e -> (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() } }

    val planes = (raiz["planes"] as? JsonArray).orEmpty().mapNotNull { el ->
        val p = el as? JsonObject ?: return@mapNotNull null
        val ejercicios = (p["ejercicios"] as? JsonArray).orEmpty().mapNotNull { ee ->
            val e = ee as? JsonObject ?: return@mapNotNull null
            EjercicioPaciente(
                itemId = e.str("itemId") ?: return@mapNotNull null,
                nombre = e.str("nombre") ?: "Ejercicio",
                gifUrl = e.str("gifUrl"),
                posturaInicialUrl = e.str("posturaInicialUrl"),
                posturaFinalUrl = e.str("posturaFinalUrl"),
                pasos = e.textos("pasos"),
                materiales = e.textos("materiales"),
                indicaciones = e.str("indicaciones"),
                vecesAlDia = e.ent("vecesAlDia") ?: 1,
                dias = (e["dias"] as? JsonArray).orEmpty().mapNotNull { d -> (d as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() },
                lado = e.str("lado"),
                carga = e.str("carga"),
                descansoSeg = e.ent("descansoSeg"),
                dolorMaximo = e.ent("dolorMaximo"),
                dosisTexto = e.str("dosisTexto").orEmpty(),
                diasTexto = e.str("diasTexto").orEmpty(),
                tocaHoy = e.si("tocaHoy"),
                hechoHoy = e.si("hechoHoy"),
                dolorHoy = e.ent("dolorHoy"),
            )
        }
        if (ejercicios.isEmpty()) return@mapNotNull null
        PlanEjerciciosPaciente(
            id = p.str("id") ?: return@mapNotNull null,
            titulo = p.str("titulo").orEmpty(),
            momento = p.str("momento") ?: "sesion",
            etiqueta = p.str("etiqueta").orEmpty(),
            vigencia = p.str("vigencia").orEmpty(),
            motivo = p.str("motivo"),
            indicaciones = p.str("indicaciones"),
            precauciones = p.str("precauciones"),
            clinica = p.str("clinica"),
            indicadoPor = p.str("indicadoPor"),
            ejercicios = ejercicios,
        )
    }
    return MisEjercicios(planes, raiz.str("hoy").orEmpty(), raiz.ent("diaSemana") ?: 0)
}

/** Aplica "hecho hoy" a la copia local del plan (respuesta optimista tras marcar; `conEjercicioMarcado` de la web). */
fun conEjercicioMarcado(plan: PlanEjerciciosPaciente, itemId: String, hecho: Boolean, dolor: Int?): PlanEjerciciosPaciente =
    plan.copy(ejercicios = plan.ejercicios.map { e ->
        if (e.itemId == itemId) e.copy(hechoHoy = hecho, dolorHoy = if (hecho) dolor else null) else e
    })

/** Body de POST /api/paciente/mis-ejercicios. Sin `clinica_id` ni paciente: el servidor sabe de quién es. */
internal fun cuerpoMarcarEjercicio(itemId: String, hecho: Boolean, dolor: Int?): JsonObject = buildJsonObject {
    put("itemId", itemId)
    put("hecho", hecho)
    put("dolor", if (hecho && dolor != null) JsonPrimitive(dolor) else JsonNull)
}

/** Qué pasó al marcar un ejercicio. [recargar] = el plan cambió (409): hay que volver a pedirlo. */
data class ResultadoMarcar(val ok: Boolean, val error: String? = null, val recargar: Boolean = false)

/**
 * Ejercicios de apoyo del paciente (portal). Mismo camino seguro que mis-citas y
 * mis-recetas: API web con el Bearer del paciente; el servidor decide qué fichas
 * son suyas (identidad verificada, nunca RLS).
 *
 * Tolerante a propósito: 404/500/sin red → null y la sección simplemente no
 * aparece; nunca un error que confunda a un paciente que no es de fisioterapia.
 *
 * SIN caché local (a diferencia de las recetas): "toca hoy" y "ya lo hice hoy"
 * son del día y los resuelve el servidor; una copia guardada de ayer mentiría.
 */
object MisEjerciciosRepo {

    private val http = crearHttpClient()
    private const val RUTA = "/api/paciente/mis-ejercicios"

    /** Desde el servidor. Null = no se pudo saber (la UI conserva lo que tenga). */
    suspend fun cargar(): MisEjercicios? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        val resp = withTimeoutOrNull(12_000) {
            runCatching {
                http.get("${Supabase.SITE_URL}$RUTA") { header("Authorization", "Bearer $tk") }
            }.getOrNull()
        } ?: return null
        // 403 = la cuenta aún no está vinculada a ninguna ficha: no tiene ejercicios.
        if (resp.status == HttpStatusCode.Forbidden) return MisEjercicios(emptyList(), "", 0)
        if (resp.status != HttpStatusCode.OK) return null
        val texto = runCatching { resp.bodyAsText() }.getOrNull() ?: return null
        return parsearMisEjercicios(texto)
    }

    /**
     * "✓ Ya lo hice hoy" (o deshacer): es lo que el fisio ve como cumplimiento.
     * Una fila por ejercicio y día en el servidor: repetir no duplica.
     */
    suspend fun marcar(itemId: String, hecho: Boolean, dolor: Int?): ResultadoMarcar {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken
            ?: return ResultadoMarcar(false, "Tu sesión expiró. Vuelve a entrar.")
        val resp = withTimeoutOrNull(12_000) {
            runCatching {
                http.post("${Supabase.SITE_URL}$RUTA") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(cuerpoMarcarEjercicio(itemId, hecho, dolor).toString())
                }
            }.getOrNull()
        } ?: return ResultadoMarcar(false, "Sin conexión. Inténtalo de nuevo.")
        if (resp.status == HttpStatusCode.OK) return ResultadoMarcar(true)
        val error = runCatching { Json.parseToJsonElement(resp.bodyAsText()).jsonObject["error"] as? JsonPrimitive }
            .getOrNull()?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }
        // 409 = el ejercicio ya no está en su plan (el fisio lo cambió): recargar.
        return ResultadoMarcar(false, error ?: "No se pudo guardar", recargar = resp.status == HttpStatusCode.Conflict)
    }
}
