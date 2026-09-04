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
        // Las dos fuentes viven juntas: las metas viejas (por persona) y los
        // ESQUEMAS por niveles (las pirámides, 2026-09-03). Una clínica migra
        // de una a otra sin que la app se entere; el que no tiene nada ve nada.
        return metasViejas(tk) + esquemas(tk)
    }

    private suspend fun metasViejas(tk: String): List<AvanceMeta> = runCatching {
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

    /**
     * ESQUEMAS POR NIVELES (/api/staff/comision-plantillas): las pirámides
     * Bronce/Plata/Oro/Diamante de DALU. El endpoint ya devuelve al
     * profesional SOLO su avance y sin montos; acá se traduce cada
     * (esquema, avance) a la misma tarjeta de meta, para no inventar otra UI:
     * texto = "Plata · 2 para Oro", cumplida = está en el tope.
     */
    private suspend fun esquemas(tk: String): List<AvanceMeta> = runCatching {
        val resp = http.get("${Supabase.SITE_URL}/api/staff/comision-plantillas") {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status != HttpStatusCode.OK) return emptyList()
        val o = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        (o["plantillas"]?.jsonArray ?: return emptyList()).flatMap { el ->
            val p = el.jsonObject
            val nombre = p.str("nombre") ?: "Mi esquema"
            val rango = p.str("etiquetaRango")
            val tramos = p["tramos"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
            val topeObjetivo = tramos.maxOfOrNull { it.int("objetivo") } ?: 0
            (p["avances"]?.jsonArray ?: return@flatMap emptyList()).map { av ->
                val a = av.jsonObject
                val siguiente = a.str("siguienteNivel")
                val actual = a.str("nivelActual")
                val objetivo = tramos.firstOrNull { it.str("nombre") == siguiente }?.int("objetivo") ?: topeObjetivo
                AvanceMeta(
                    id = "${p.str("id")}:${a.str("terapeutaId")}",
                    etiqueta = if (rango != null) "$nombre · $rango" else nombre,
                    texto = a.str("texto") ?: "",
                    logrado = a.int("logrado"),
                    objetivo = objetivo,
                    progreso = a.int("progreso"),
                    // En el tope de la pirámide no hay "siguiente": eso es cumplir.
                    cumplida = actual != null && siguiente == null,
                    faltan = a.int("faltanParaSiguiente"),
                )
            }
        }
    }.getOrElse { emptyList() }
}
