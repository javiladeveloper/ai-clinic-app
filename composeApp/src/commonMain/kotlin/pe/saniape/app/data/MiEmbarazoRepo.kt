package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.offline.CacheLectura
import pe.saniape.app.data.staff.SIGNOS_ALARMA_EMB
import pe.saniape.app.data.staff.aNumeroEmb
import pe.saniape.app.data.staff.diasEntreEmb
import pe.saniape.app.data.staff.egDiasEn
import pe.saniape.app.data.staff.esFechaIsoEmb
import pe.saniape.app.data.staff.sumarDiasEmb
import pe.saniape.app.data.staff.textoEg
import pe.saniape.app.data.staff.trimestre

/** Un control prenatal tal como lo ve la paciente (sin notas internas). */
data class ControlPortalEmb(
    val id: String,
    val fecha: String,
    val egDias: Int?,
    val eg: String?,
    val peso: Double?,
    val paSistolica: Int?,
    val paDiastolica: Int?,
    val alturaUterina: Double?,
    val lcf: Int?,
)

/** Su embarazo EN CURSO en una clínica (contrato: docs/app-contrato-portal-paciente.md §9). */
data class EmbarazoPortal(
    val id: String,
    val fpp: String,
    /** Día 0: con él se recalcula la EG de cualquier día. */
    val inicio: String,
    val metodoDatacion: String,
    val egHoyDias: Int?,
    val egHoy: String?,
    val trimestre: Int?,
    val diasParaFpp: Int,
    val grupoSanguineo: String?,
    val rh: String?,
    val clinicaNombre: String?,
    val profesional: String?,
    /** Cronológicos (el primero es el más antiguo). */
    val controles: List<ControlPortalEmb>,
    val proximoControl: String?,
) {
    /** El "hoy" con el que el servidor calculó (FPP − días que faltan). */
    val hoyServidor: String get() = sumarDiasEmb(fpp, -diasParaFpp)

    /**
     * La EG / días a la FPP de [hoy] (lo guardado en la caché envejece: se
     * recalcula con el día 0, como dice el contrato). Nunca hacia atrás: si el
     * reloj del teléfono va detrás del de su sede, manda el del servidor.
     */
    fun vigente(hoy: String): EmbarazoPortal {
        val dia = if (hoy > hoyServidor) hoy else return this
        val eg = egDiasEn(inicio, dia)
        return copy(egHoyDias = eg, egHoy = textoEg(eg), trimestre = trimestre(eg), diasParaFpp = diasEntreEmb(dia, fpp))
    }
}

/** Respuesta de /api/paciente/mi-embarazo. */
data class MiEmbarazo(val embarazos: List<EmbarazoPortal>, val signosAlarma: List<String>) {
    val tieneDatos: Boolean get() = embarazos.isNotEmpty()

    companion object {
        val VACIA = MiEmbarazo(emptyList(), emptyList())
    }
}

private fun JsonElement?.primPortal(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
private fun JsonObject.txtP(k: String): String? = this[k].primPortal()?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
private fun JsonObject.numP(k: String): Double? = aNumeroEmb(txtP(k))
private fun JsonObject.intP(k: String): Int? = numP(k)?.takeIf { it == kotlin.math.floor(it) }?.toInt()

/**
 * JSON de /api/paciente/mi-embarazo → modelo. Tolerante: números como texto o
 * null; un embarazo sin id, FPP o inicio válidos se salta; los controles se
 * reordenan cronológicos. Sin la lista educativa (servidor viejo), la del
 * catálogo. Null = el texto no es JSON válido.
 */
fun parsearMiEmbarazo(texto: String): MiEmbarazo? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    val embarazos = (raiz["embarazos"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = o.txtP("id") ?: return@mapNotNull null
        val fpp = o.txtP("fpp")?.take(10)?.takeIf { esFechaIsoEmb(it) } ?: return@mapNotNull null
        val inicio = o.txtP("inicio")?.take(10)?.takeIf { esFechaIsoEmb(it) } ?: sumarDiasEmb(fpp, -280)
        val egHoy = o.intP("egHoyDias")
        val controles = (o["controles"] as? JsonArray).orEmpty().mapNotNull { ce ->
            val c = ce as? JsonObject ?: return@mapNotNull null
            val cid = c.txtP("id") ?: return@mapNotNull null
            val fecha = c.txtP("fecha")?.take(10) ?: return@mapNotNull null
            val eg = c.intP("egDias") ?: egDiasEn(inicio, fecha)
            ControlPortalEmb(cid, fecha, eg, c.txtP("eg") ?: textoEg(eg), c.numP("peso"), c.intP("paSistolica"),
                c.intP("paDiastolica"), c.numP("alturaUterina"), c.intP("lcf"))
        }.sortedBy { it.fecha }
        EmbarazoPortal(
            id = id, fpp = fpp, inicio = inicio, metodoDatacion = o.txtP("metodoDatacion") ?: "fur",
            egHoyDias = egHoy, egHoy = o.txtP("egHoy") ?: textoEg(egHoy), trimestre = o.intP("trimestre") ?: trimestre(egHoy),
            diasParaFpp = o.intP("diasParaFpp") ?: 0,
            grupoSanguineo = o.txtP("grupoSanguineo"), rh = o.txtP("rh"),
            clinicaNombre = (o["clinica"] as? JsonObject)?.txtP("nombre"), profesional = o.txtP("profesional"),
            controles = controles, proximoControl = o.txtP("proximoControl")?.take(10),
        )
    }
    val signos = (raiz["signosAlarma"] as? JsonArray).orEmpty().mapNotNull { it.primPortal()?.content?.trim()?.takeIf { s -> s.isNotEmpty() } }
    return MiEmbarazo(embarazos, signos.ifEmpty { SIGNOS_ALARMA_EMB.map { it.educativo } })
}

/**
 * 🤰 Mi embarazo (portal de la paciente). Mismo camino seguro que mi-nutrición:
 * API web con el Bearer de la paciente; el servidor decide qué fichas son suyas
 * (identidad verificada, nunca RLS) y no manda notas internas.
 *
 * Tolerante a propósito: 403 (cuenta sin ficha) o 404 (servidor sin la ruta) →
 * "sin datos" y la sección no aparece; 500/sin red → null (la UI conserva lo
 * que tenga). Caché por USUARIO: la gestante lo consulta donde la señal falla.
 */
object MiEmbarazoRepo {

    private val http = crearHttpClient()
    private const val RUTA = "/api/paciente/mi-embarazo"

    private fun clave(): String? =
        Supabase.client.auth.currentUserOrNull()?.id?.let { "portal:mi-embarazo:$it" }

    /** Lo último guardado para este usuario, o null si no hay nada. */
    fun guardada(): MiEmbarazo? {
        val k = clave() ?: return null
        val crudo = CacheLectura.leer(k) ?: return null
        return parsearMiEmbarazo(crudo)
    }

    /** Desde el servidor. Null = no se pudo saber (la UI conserva lo que tenga). */
    suspend fun cargar(): MiEmbarazo? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        val resp = withTimeoutOrNull(12_000) {
            runCatching { http.get("${Supabase.SITE_URL}$RUTA") { header("Authorization", "Bearer $tk") } }.getOrNull()
        } ?: return null
        if (resp.status == HttpStatusCode.Forbidden || resp.status == HttpStatusCode.NotFound) {
            clave()?.let { CacheLectura.borrar(it) }
            return MiEmbarazo.VACIA
        }
        if (resp.status != HttpStatusCode.OK) return null
        val texto = runCatching { resp.bodyAsText() }.getOrNull() ?: return null
        val datos = parsearMiEmbarazo(texto) ?: return null
        clave()?.let { k -> if (datos.tieneDatos) CacheLectura.guardar(k, texto) else CacheLectura.borrar(k) }
        return datos
    }
}
