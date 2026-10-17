package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pe.saniape.app.data.Supabase

// ─────────────────────────────────────────────────────────────────────────────
// CAMPAÑA QUE «INCLUYE LA EVALUACIÓN» (cita de entrada) — GEMELO de
// lib/campania-entrada.ts (web 2026-10-16). Pedido de DALU ("10 sesiones a
// S/ 600, incluye la evaluación") y, por regla del dueño, para TODAS las
// clínicas: la cita de entrada es la Consulta, la Evaluación o la Valoración de
// cada flujo. Gratis SOLO si compra: lo pagado en esa cita se descuenta del plan
// que nace de ella (lo hace la BASE, trigger trg_tratamiento_campania_entrada);
// si no compra, se cobró normal. Aquí solo textos, quién ofrece la opción y la
// preselección del cobro.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Cómo se llama la cita de entrada en ESTE flujo, en minúscula: "evaluación",
 * "consulta", "valoración"… Con las dos, "consulta o evaluación". Sin ninguna:
 * "cita de entrada".
 */
fun nombreCitaEntrada(f: FlujoClinica?): String {
    val c = (f?.labelConsulta?.takeIf { it.isNotBlank() } ?: "Consulta").trim().lowercase()
    val e = (f?.labelEvaluacion?.takeIf { it.isNotBlank() } ?: "Evaluación").trim().lowercase()
    if (f?.usaConsulta == true && f.usaEvaluacion) return if (c == e) c else "$c o $e"
    if (f?.usaConsulta == true) return c
    if (f?.usaEvaluacion == true) return e
    return "cita de entrada"
}

/** "la evaluación" / "el diagnóstico". */
fun conArticulo(nombre: String): String {
    val primera = nombre.split(' ').firstOrNull()?.takeIf { it.isNotEmpty() } ?: nombre
    return "${if (primera.endsWith("o", ignoreCase = true)) "el" else "la"} $nombre"
}

/** El texto de la casilla de la campaña: (título, detalle). */
fun etiquetaIncluyeEntrada(nombre: String): Pair<String, String> {
    val art = conArticulo(nombre)
    return "Incluye $art" to
        "Lo pagado en $art de entrada se descuenta del paquete cuando el tratamiento se crea desde esa cita. Si el paciente no lo toma, $art se cobra normal."
}

/** Solo campañas de TRATAMIENTO: una de alcance 'citas' ya es el descuento de la cita misma. */
fun admiteIncluyeEntrada(alcance: String?): Boolean = alcance != "citas"

/** ¿Esta campaña (guardada) descuenta la cita de entrada? */
fun campaniaIncluyeEntrada(alcance: String?, incluyeEntrada: Boolean?): Boolean =
    admiteIncluyeEntrada(alcance) && incluyeEntrada == true

/**
 * El campo para guardar: null = NO se manda (una campaña sin la opción guarda lo
 * de siempre, y un servidor sin la columna no se entera). Con alcance 'citas',
 * false solo si ya la tenía.
 */
fun campoIncluyeEntrada(valor: Boolean, alcance: String?, yaLaTenia: Boolean = false): Boolean? {
    if (alcance == "citas") return if (yaLaTenia) false else null
    if (!valor && !yaLaTenia) return null
    return valor
}

/** Lo mínimo de un tratamiento para elegir a cuál va el abono (y si su campaña incluye la entrada). */
data class TratamientoAbonoCampania(
    val id: String,
    val estado: String?,
    val citaOrigenId: String?,
    val createdAt: String?,
    val campaniaAlcance: String? = null,
    val campaniaIncluyeEntrada: Boolean? = null,
)

/**
 * A qué tratamiento va el abono de una cita (gemelo de `elegirTratamientoParaAbono`
 * de lib/anticipo-cita.ts): el que NACIÓ de esta cita (vivo); si no, el Activo
 * más reciente; si no, ninguno.
 */
fun elegirTratamientoParaAbono(tratamientos: List<TratamientoAbonoCampania>, citaId: String): TratamientoAbonoCampania? {
    val recientes = tratamientos.sortedByDescending { it.createdAt.orEmpty() }
    fun vivo(t: TratamientoAbonoCampania) = t.estado != "Eliminado" && t.estado != "Cancelado"
    return recientes.firstOrNull { it.citaOrigenId == citaId && vivo(it) }
        ?: recientes.firstOrNull { it.estado == "Activo" }
}

/**
 * ¿El cobro de esta cita debe abrir en «Cobrar y abonar al tratamiento»? Sí
 * cuando el destino del abono es el plan que NACIÓ de esta cita y su campaña
 * incluye la entrada: lo pagado va al plan (gemelo de `debeAbonarPorCampania`).
 */
fun debeAbonarPorCampania(tratamientos: List<TratamientoAbonoCampania>, citaId: String): Boolean {
    val t = elegirTratamientoParaAbono(tratamientos, citaId) ?: return false
    if (t.citaOrigenId != citaId) return false
    return campaniaIncluyeEntrada(t.campaniaAlcance, t.campaniaIncluyeEntrada)
}

object CampaniaEntradaRepo {
    private fun JsonObject.txt(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

    /**
     * Lee los tratamientos que pueden recibir el abono de la cita (el que nació de
     * ella o los activos) con su campaña. `campania:campanias(*)` (no la columna
     * por nombre): sin la migración la lectura sigue funcionando. Ante cualquier
     * error → false (el cobro se ve como siempre).
     */
    suspend fun debeAbonar(pacienteId: String, citaId: String): Boolean = runCatching {
        val filas = Supabase.client.postgrest["tratamientos"]
            .select(Columns.raw("id, estado, cita_origen_id, created_at, campania:campanias(*)")) {
                filter {
                    eq("paciente_id", pacienteId)
                    neq("estado", "Eliminado")
                    or { eq("cita_origen_id", citaId); eq("estado", "Activo") }
                }
            }
            .decodeList<JsonObject>()
        val lista = filas.mapNotNull { o ->
            val camp = (o["campania"] as? JsonObject) ?: ((o["campania"] as? JsonArray)?.firstOrNull() as? JsonObject)
            TratamientoAbonoCampania(
                id = o.txt("id") ?: return@mapNotNull null,
                estado = o.txt("estado"),
                citaOrigenId = o.txt("cita_origen_id"),
                createdAt = o.txt("created_at"),
                campaniaAlcance = camp?.txt("alcance"),
                campaniaIncluyeEntrada = camp?.txt("incluye_entrada")?.let { it == "true" },
            )
        }
        debeAbonarPorCampania(lista, citaId)
    }.getOrElse {
        if (it is kotlin.coroutines.cancellation.CancellationException) throw it
        false
    }
}
