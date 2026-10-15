package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.offline.CacheLectura
import pe.saniape.app.data.staff.PuntoSerieHc
import pe.saniape.app.data.staff.ZonaCapilar
import pe.saniape.app.data.staff.escalaDe
import pe.saniape.app.data.staff.redondearTrico

/** Una zona medida en una visita: solo densidad (pelos/cm²) y miniaturización (%). */
data class ZonaCapilarPortal(val zona: ZonaCapilar, val nombre: String, val densidad: Double?, val miniaturizacionPct: Double?)

/** Una tricoscopía VISIBLE del paciente (sin observaciones, signos ni trasplante: no vienen). */
data class EvaluacionCapilarPortal(
    val id: String,
    val fecha: String,
    /** hamilton_norwood · ludwig · sinclair · difuso · otro · null (para la silueta). */
    val escala: String?,
    val escalaNombre: String?,
    val grado: String?,
    val zonas: List<ZonaCapilarPortal>,
    val clinicaNombre: String?,
    val profesional: String?,
)

/** Respuesta de /api/paciente/mi-evolucion-capilar: cronológica (la última es la más reciente). */
data class MiEvolucionCapilar(val evaluaciones: List<EvaluacionCapilarPortal>) {
    val tieneDatos: Boolean get() = evaluaciones.isNotEmpty()

    companion object {
        val VACIA = MiEvolucionCapilar(emptyList())
    }
}

private fun JsonObject.txtCap(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
private fun JsonObject.numCap(k: String): Double? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }

/**
 * Convierte el JSON de /api/paciente/mi-evolucion-capilar (contrato en la web:
 * docs/app-contrato-portal-paciente.md §8). Tolerante: zonas desconocidas o sin
 * densidad ni miniaturización se descartan; evaluaciones sin id/fecha se
 * saltan; sin clasificación ni zonas, fuera (nada que mostrar). Se reordena
 * cronológicamente (la caché o un servidor viejo). Null = no es JSON válido.
 */
fun parsearMiEvolucionCapilar(texto: String): MiEvolucionCapilar? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    val evals = (raiz["evaluaciones"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = o.txtCap("id") ?: return@mapNotNull null
        val fecha = o.txtCap("fecha")?.take(10) ?: return@mapNotNull null
        val esc = escalaDe(o.txtCap("escala"))
        val zonas = (o["zonas"] as? JsonArray).orEmpty().mapNotNull { zEl ->
            val z = zEl as? JsonObject ?: return@mapNotNull null
            val zc = ZonaCapilar.de(z.txtCap("zona")) ?: return@mapNotNull null
            val d = z.numCap("densidad")
            val m = z.numCap("miniaturizacion_pct")
            if (d == null && m == null) null else ZonaCapilarPortal(zc, z.txtCap("nombre") ?: zc.nombre, d, m)
        }.distinctBy { it.zona }.sortedBy { it.zona.ordinal }
        if (esc == null && zonas.isEmpty()) return@mapNotNull null
        EvaluacionCapilarPortal(
            id = id, fecha = fecha,
            escala = esc?.id,
            escalaNombre = o.txtCap("escala_nombre") ?: esc?.nombre,
            grado = if (esc != null) o.txtCap("grado") else null,
            zonas = zonas,
            clinicaNombre = (o["clinica"] as? JsonObject)?.txtCap("nombre"),
            profesional = o.txtCap("profesional"),
        )
    }
    // sortedBy es estable: a igual fecha se respeta el orden del servidor (el de creación).
    return MiEvolucionCapilar(evals.distinctBy { it.id }.sortedBy { it.fecha })
}

// ─── Presentación (pura, testeable) ──────────────────────────────────────────

/** La clasificación a mostrar: la de la última evaluación que la tenga ("Hamilton-Norwood III vértex"). */
fun clasificacionPortal(items: List<EvaluacionCapilarPortal>): EvaluacionCapilarPortal? = items.lastOrNull { it.escala != null }

fun textoClasificacionPortal(e: EvaluacionCapilarPortal): String? =
    listOfNotNull(e.escalaNombre, e.grado).joinToString(" ").ifBlank { null }

/** Por zona: el último valor de densidad y el cambio desde la primera visita que la midió (null con una sola). */
data class ResumenZonaPortal(val zona: ZonaCapilar, val valor: Double, val cambio: Double?, val desde: String)

fun resumenDensidadPortal(items: List<EvaluacionCapilarPortal>): List<ResumenZonaPortal> = ZonaCapilar.entries.mapNotNull { z ->
    val pts = items.mapNotNull { e -> e.zonas.firstOrNull { it.zona == z }?.densidad?.let { e.fecha to it } }
    if (pts.isEmpty()) return@mapNotNull null
    val primera = pts.first()
    val ultima = pts.last()
    ResumenZonaPortal(z, ultima.second, if (pts.size > 1) redondearTrico(ultima.second - primera.second, 1) else null, primera.first)
}

/** Densidad o miniaturización de una zona en el tiempo. */
fun serieCapilarPortal(items: List<EvaluacionCapilarPortal>, zona: ZonaCapilar, miniaturizacion: Boolean): List<PuntoSerieHc> =
    items.mapNotNull { e ->
        val z = e.zonas.firstOrNull { it.zona == zona } ?: return@mapNotNull null
        (if (miniaturizacion) z.miniaturizacionPct else z.densidad)?.let { PuntoSerieHc(e.fecha, it) }
    }

/** Las zonas con ≥ 2 puntos de esa medida (las que tienen gráfico). */
fun zonasConGraficoPortal(items: List<EvaluacionCapilarPortal>, miniaturizacion: Boolean): List<ZonaCapilar> =
    ZonaCapilar.entries.filter { serieCapilarPortal(items, it, miniaturizacion).size >= 2 }

/** ¿Hay al menos dos visitas con miniaturización? (para ofrecer el cambio de medida). */
fun hayMiniaturizacionPortal(items: List<EvaluacionCapilarPortal>): Boolean =
    items.count { e -> e.zonas.any { it.miniaturizacionPct != null } } >= 2

/**
 * 🔬 Mi evolución capilar (portal). Mismo camino seguro que mi-nutricion: API
 * web con el Bearer del paciente; el servidor decide qué fichas son suyas
 * (identidad verificada, nunca RLS). 403 (cuenta sin ficha) / 404 (servidor
 * sin la sección) → "sin datos" y la sección no aparece; 500/sin red → null
 * (la UI conserva lo que tenga). Caché por USUARIO.
 */
object MiEvolucionCapilarRepo {

    private val http = crearHttpClient()
    private const val RUTA = "/api/paciente/mi-evolucion-capilar"

    private fun clave(): String? =
        Supabase.client.auth.currentUserOrNull()?.id?.let { "portal:mi-evolucion-capilar:$it" }

    /** Lo último guardado para este usuario, o null si no hay nada. */
    fun guardada(): MiEvolucionCapilar? {
        val k = clave() ?: return null
        val crudo = CacheLectura.leer(k) ?: return null
        return parsearMiEvolucionCapilar(crudo)
    }

    /** Desde el servidor. Null = no se pudo saber (la UI conserva lo que tenga). */
    suspend fun cargar(): MiEvolucionCapilar? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        val resp = withTimeoutOrNull(12_000) {
            runCatching {
                http.get("${Supabase.SITE_URL}$RUTA") { header("Authorization", "Bearer $tk") }
            }.getOrNull()
        } ?: return null
        if (resp.status == HttpStatusCode.Forbidden || resp.status == HttpStatusCode.NotFound) {
            clave()?.let { CacheLectura.borrar(it) }
            return MiEvolucionCapilar.VACIA
        }
        if (resp.status != HttpStatusCode.OK) return null
        val texto = runCatching { resp.bodyAsText() }.getOrNull() ?: return null
        val datos = parsearMiEvolucionCapilar(texto) ?: return null
        clave()?.let { k -> if (datos.tieneDatos) CacheLectura.guardar(k, texto) else CacheLectura.borrar(k) }
        return datos
    }
}
