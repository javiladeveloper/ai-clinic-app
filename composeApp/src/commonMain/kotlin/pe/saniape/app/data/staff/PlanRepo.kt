package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

// ─────────────────────────────────────────────────────────────────────────────
// Mi plan (suscripción). SOLO LECTURA: GET /api/staff/plan arma en un viaje lo
// que la página web /suscripcion muestra (plan, estado, vencimiento, límites y
// uso, catálogo de planes y add-ons con los precios de lib/plan.ts). Pagar o
// cambiar de plan NO se hace acá (política de Google Play sobre cobros dentro de
// la app): esos botones abren la web.
// ─────────────────────────────────────────────────────────────────────────────

data class FilaPlan(val ok: Boolean, val texto: String)

data class PlanCatalogo(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val precioMensual: Double?,
    val precioAnual: Double,
    val precioPorSede: Double?,
    val maxProfesionales: Int?,
    val incluye: List<FilaPlan>,
)

data class NivelAddon(val id: String, val nombre: String, val precio: Double, val resumen: String, val cupo: String?, val detalle: List<String>)

data class AddonCatalogo(
    val clave: String,
    val nombre: String,
    val precio: Double,
    val pagoUnico: Double?,
    val requierePlan: String?,
    val enConstruccion: Boolean,
    val descripcion: String,
    val detalle: List<String>,
    val niveles: List<NivelAddon>,
)

data class AddonActivo(val addon: String, val vence: String?, val precioMensual: Double?)

data class UsoPlan(
    val profesionalesActivos: Int,
    /** Tope total (en Básico es por sede × sedes). null = ilimitado. */
    val maxProfesionales: Int?,
    val maxProfesionalesPorSede: Int?,
    val sedesActivas: Int,
    val sedesPagadas: Int,
    val documentosUsadoBytes: Long?,
    val documentosMaxMB: Int?,
)

data class DatosPlan(
    /** Trial | Basico | Premium | Plus */
    val plan: String,
    val nombrePlan: String,
    val vencido: Boolean,
    val diasRestantes: Int?,
    val planVence: String?,
    val uso: UsoPlan,
    val planes: List<PlanCatalogo>,
    val addons: List<AddonCatalogo>,
    val addonsActivos: List<AddonActivo>,
) {
    val esTrial: Boolean get() = plan == "Trial"
    /** Las filas de lo que incluye el plan que tiene la clínica (si está en el catálogo). */
    val incluyeActual: List<FilaPlan>? get() = planes.firstOrNull { it.id == (if (vencido) "Basico" else plan) }?.incluye
}

/** Nivel del Agente Sani que identifica el precio mensual registrado (null si no lo identifica sin ambigüedad). */
fun nivelContratado(a: AddonCatalogo, precioMensual: Double?): NivelAddon? =
    if (precioMensual == null) null else a.niveles.firstOrNull { it.precio == precioMensual }

object PlanRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double? = str(k)?.toDoubleOrNull()
    private fun JsonObject.int(k: String): Int? = dbl(k)?.toInt()
    private fun JsonObject.bool(k: String): Boolean = str(k) == "true"
    private fun JsonObject.lista(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    private fun JsonObject.textos(k: String): List<String> =
        (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    /** Lo que muestra la pantalla, o el motivo del fallo. */
    suspend fun cargar(): Pair<DatosPlan?, String?> {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null to "Tu sesión expiró. Vuelve a entrar."
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/plan") { header("Authorization", "Bearer $tk") }
            val texto = resp.bodyAsText()
            if (resp.status.value in 200..299) {
                parsear(texto)?.let { it to null } ?: (null to "Respuesta inesperada del servidor.")
            } else {
                val msg = runCatching { json.parseToJsonElement(texto).jsonObject.str("error") }.getOrNull()
                null to (msg ?: "No se pudo cargar tu plan.")
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            null to "Sin conexión. Revisa tu internet."
        }
    }

    internal fun parsear(cuerpo: String): DatosPlan? = runCatching {
        val o = json.parseToJsonElement(cuerpo).jsonObject
        val u = o["uso"] as? JsonObject
        DatosPlan(
            plan = o.str("plan") ?: "Trial",
            nombrePlan = o.str("nombrePlan") ?: "Plan",
            vencido = o.bool("vencido"),
            diasRestantes = o.int("diasRestantes"),
            planVence = o.str("planVence"),
            uso = UsoPlan(
                profesionalesActivos = u?.int("profesionalesActivos") ?: 0,
                maxProfesionales = u?.int("maxProfesionales"),
                maxProfesionalesPorSede = u?.int("maxProfesionalesPorSede"),
                sedesActivas = u?.int("sedesActivas") ?: 1,
                sedesPagadas = u?.int("sedesPagadas") ?: 1,
                documentosUsadoBytes = u?.dbl("documentosUsadoBytes")?.toLong(),
                documentosMaxMB = u?.int("documentosMaxMB"),
            ),
            planes = o.lista("planes").map { p ->
                PlanCatalogo(
                    id = p.str("id").orEmpty(), nombre = p.str("nombre").orEmpty(), descripcion = p.str("descripcion").orEmpty(),
                    precioMensual = p.dbl("precio"), precioAnual = p.dbl("precioAnual") ?: 0.0, precioPorSede = p.dbl("precioPorSede"),
                    maxProfesionales = p.int("maxProfesionales"),
                    incluye = p.lista("incluye").map { FilaPlan(it.bool("ok"), it.str("texto").orEmpty()) },
                )
            },
            addons = o.lista("addonsCatalogo").map { a ->
                AddonCatalogo(
                    clave = a.str("clave").orEmpty(), nombre = a.str("nombre").orEmpty(), precio = a.dbl("precio") ?: 0.0,
                    pagoUnico = a.dbl("pagoUnico"), requierePlan = a.str("requierePlan"), enConstruccion = a.bool("enConstruccion"),
                    descripcion = a.str("descripcion").orEmpty(), detalle = a.textos("detalle"),
                    niveles = a.lista("niveles").map { n ->
                        NivelAddon(
                            n.str("id").orEmpty(), n.str("nombre").orEmpty(), n.dbl("precio") ?: 0.0,
                            n.str("resumen").orEmpty(), n.str("cupo"), n.textos("detalle"),
                        )
                    },
                )
            },
            addonsActivos = o.lista("addonsActivos").map { AddonActivo(it.str("addon").orEmpty(), it.str("vence"), it.dbl("precio_mensual")) },
        )
    }.getOrNull()
}
