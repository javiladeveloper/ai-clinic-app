package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** El avance del profesional hacia su meta de comisión. */
data class AvanceMeta(
    val id: String,
    /** Nombre de la meta, si tiene varias ("Bono anual"). */
    val etiqueta: String?,
    /** "7 de 10" · "¡Meta cumplida! 🎉" · "3 paquetes este mes" */
    val texto: String,
    val logrado: Int,
    val objetivo: Int,
    /** 0–100 */
    val progreso: Int,
    val cumplida: Boolean,
    /** Cuántas le faltan para el bono. */
    val faltan: Int,
)

/**
 * Metas de comisión del profesional (/api/staff/metas).
 *
 * Se pide al SERVIDOR y no se calcula aquí a propósito: el cálculo vive en la
 * web (lib/metas-comision.ts, con sus tests) y duplicarlo en Kotlin sería tener
 * dos verdades que se separan en cuanto una cambie.
 *
 * El endpoint ya filtra por quién pregunta: a un profesional le devuelve SOLO su
 * meta y SIN el monto del bono — él ve su avance, no el premio (decisión del
 * dueño, 2026-08-14). Por eso aquí no hay ningún campo de dinero: no llega.
 */
object MetasRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.int(k: String): Int =
        (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: 0
    private fun JsonObject.bool(k: String): Boolean =
        (this[k] as? JsonPrimitive)?.content == "true"

    /**
     * Sus metas del período en curso. Lista vacía si no tiene ninguna — es lo
     * normal: solo comisiona quien la clínica configuró.
     *
     * Best-effort: si falla la red devuelve vacío. Una meta que no carga no
     * puede dejar sin Inicio a nadie.
     */
    suspend fun mias(): List<AvanceMeta> {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return emptyList()
        return runCatching {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/metas") {
                header("Authorization", "Bearer $tk")
            }
            if (resp.status != HttpStatusCode.OK) return emptyList()
            val o = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            (o["metas"]?.jsonArray ?: return emptyList()).map { el ->
                val m = el.jsonObject
                AvanceMeta(
                    id = m.str("id") ?: "",
                    etiqueta = m.str("etiqueta"),
                    texto = m.str("texto") ?: "",
                    logrado = m.int("logrado"),
                    objetivo = m.int("objetivo"),
                    progreso = m.int("progreso"),
                    cumplida = m.bool("cumplida"),
                    faltan = m.int("faltan"),
                )
            }
        }.getOrElse { emptyList() }
    }
}
