package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.offline.CacheLectura
import pe.saniape.app.data.staff.hoyClinicaIso

/** Un medicamento de la receta, tal como lo manda /api/paciente/recetas. */
data class MedicamentoReceta(
    val dci: String,
    val marca: String?,
    val concentracion: String,
    val forma: String,
    val via: String,
    val dosis: String,
    val frecuencia: String,
    val duracion: String,
    /** "21 (veintiuno) tabletas" — la cantidad con letras, como en la receta impresa. */
    val cantidadTexto: String,
    val indicaciones: String?,
    /** "1 tableta vía oral cada 8 horas por 7 días. Después de comer" */
    val indicacionTexto: String,
)

/** Datos del profesional que firmó la receta (sin ids internos). */
data class PrescriptorReceta(
    val nombre: String,
    val colegiatura: String,
    val profesion: String?,
    val especialidad: String?,
)

/** Una receta EMITIDA del paciente (las anuladas no llegan). */
data class RecetaPortal(
    val id: String,
    /** "N° 000012" */
    val numeroTexto: String,
    val fecha: String,
    val validaHasta: String,
    /**
     * Vigente HOY. Se recalcula en la app con la fecha de la clínica: la receta
     * puede venir de la caché de hace días y el `vigente` del servidor estar viejo.
     */
    val vigente: Boolean,
    val diagnostico: String?,
    val cie10: String?,
    val indicacionesGenerales: String?,
    val clinica: String?,
    val clinicaLogo: String?,
    val prescriptor: PrescriptorReceta?,
    val items: List<MedicamentoReceta>,
)

/** Respuesta completa: las recetas + el aviso de "copia informativa". */
data class RecetasDelPaciente(val recetas: List<RecetaPortal>, val aviso: String)

/** Si el servidor no manda aviso (no debería pasar), se muestra este. */
const val AVISO_RECETA_POR_DEFECTO =
    "Copia informativa. Para comprar en farmacia presenta la receta impresa, firmada y sellada por tu médico."

/**
 * Convierte el JSON de /api/paciente/recetas (contrato en la web:
 * docs/receta-medica-normativa.md §7). Tolerante: una receta sin id o sin
 * medicamentos se descarta en vez de romper la lista entera. Null = el texto
 * no es JSON válido.
 */
fun parsearRecetas(texto: String, hoy: String): RecetasDelPaciente? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }

    val arr = raiz["recetas"] as? JsonArray ?: JsonArray(emptyList())
    val recetas = arr.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val items = (o["items"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull { ie ->
            val it = ie as? JsonObject ?: return@mapNotNull null
            val dci = it.str("dci") ?: return@mapNotNull null
            MedicamentoReceta(
                dci = dci,
                marca = it.str("marca"),
                concentracion = it.str("concentracion") ?: "",
                forma = it.str("forma") ?: "",
                via = it.str("via") ?: "",
                dosis = it.str("dosis") ?: "",
                frecuencia = it.str("frecuencia") ?: "",
                duracion = it.str("duracion") ?: "",
                cantidadTexto = it.str("cantidadTexto")
                    ?: listOfNotNull(it.str("cantidad"), it.str("unidad")).joinToString(" "),
                indicaciones = it.str("indicaciones"),
                indicacionTexto = it.str("indicacionTexto") ?: "",
            )
        }
        if (items.isEmpty()) return@mapNotNull null
        val validaHasta = o.str("validaHasta") ?: ""
        val p = o["prescriptor"] as? JsonObject
        RecetaPortal(
            id = o.str("id") ?: return@mapNotNull null,
            numeroTexto = o.str("numeroTexto") ?: o.str("numero")?.let { "N° $it" } ?: "",
            fecha = o.str("fecha") ?: "",
            validaHasta = validaHasta,
            // Misma regla que estaVigente() de la web: hoy <= válida hasta.
            vigente = if (validaHasta.length >= 10) hoy.take(10) <= validaHasta.take(10)
                else (o["vigente"] as? JsonPrimitive)?.content == "true",
            diagnostico = o.str("diagnostico"),
            cie10 = o.str("cie10"),
            indicacionesGenerales = o.str("indicacionesGenerales"),
            clinica = o.str("clinica"),
            clinicaLogo = o.str("clinicaLogo"),
            prescriptor = p?.str("nombre")?.let { nombre ->
                PrescriptorReceta(
                    nombre = nombre,
                    colegiatura = p.str("colegiatura") ?: "",
                    profesion = p.str("profesion"),
                    especialidad = p.str("especialidad"),
                )
            },
            items = items,
        )
    }
    return RecetasDelPaciente(recetas, raiz.str("aviso") ?: AVISO_RECETA_POR_DEFECTO)
}

/**
 * Recetas del paciente (portal). Mismo camino seguro que mis-citas/mi-tratamiento:
 * API web con el Bearer del paciente; el servidor decide qué fichas son suyas.
 *
 * Tolerante a propósito: la app puede estar más nueva que el servidor (la web con
 * recetas se publica después). 404/500/sin red → null, y la sección simplemente no
 * aparece; nunca un error que confunda a un paciente de fisio que no tiene recetas.
 *
 * Caché por USUARIO: el paciente suele buscar su receta en la farmacia, donde la
 * señal falla. Se pinta lo último conocido y el servidor lo corrige.
 */
object RecetasRepo {

    private val http = crearHttpClient()

    private fun clave(): String? =
        Supabase.client.auth.currentUserOrNull()?.id?.let { "portal:recetas:$it" }

    /** Lo último guardado para este usuario, o null si no hay nada. */
    fun guardadas(): RecetasDelPaciente? {
        val k = clave() ?: return null
        val crudo = CacheLectura.leer(k) ?: return null
        return parsearRecetas(crudo, hoyClinicaIso())
    }

    /** Desde el servidor. Null = no se pudo saber (la UI conserva lo que tenga). */
    suspend fun cargar(): RecetasDelPaciente? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        val resp = withTimeoutOrNull(12_000) {
            runCatching {
                http.get("${Supabase.SITE_URL}/api/paciente/recetas") {
                    header("Authorization", "Bearer $tk")
                }
            }.getOrNull()
        } ?: return null
        // 403 = la cuenta aún no está vinculada a ninguna ficha: no tiene recetas.
        if (resp.status == HttpStatusCode.Forbidden) {
            clave()?.let { CacheLectura.borrar(it) }
            return RecetasDelPaciente(emptyList(), AVISO_RECETA_POR_DEFECTO)
        }
        if (resp.status != HttpStatusCode.OK) return null
        val texto = runCatching { resp.bodyAsText() }.getOrNull() ?: return null
        val datos = parsearRecetas(texto, hoyClinicaIso()) ?: return null
        clave()?.let { k ->
            if (datos.recetas.isEmpty()) CacheLectura.borrar(k) else CacheLectura.guardar(k, texto)
        }
        return datos
    }
}
