package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

/** Un movimiento del kardex (para la caja del día). */
data class MovimientoCaja(
    val id: String,
    val tipo: String,            // Ingreso | Egreso
    val categoria: String?,
    val descripcion: String?,
    val monto: Double,
    val metodoPago: String?,     // Efectivo / Yape / … (null en egresos o datos viejos)
    val pacienteNombre: String?,
)

/**
 * Caja de HOY (esencial del gestor/recepción en el celular): los movimientos del día con
 * lectura directa (RLS aísla por clínica). El detalle completo/cierre formal vive en la web.
 */
object CajaRepo {

    private fun hoyISO(): String {
        val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double? =
        (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()

    /**
     * Registra un movimiento MANUAL en el kardex (mismo insert que /finanzas web:
     * RLS pone la clínica; la fecha usa el DEFAULT de hoy). Devuelve null si entró,
     * o el mensaje de error humano (comprobante repetido, sesión vencida…).
     */
    suspend fun registrarMovimiento(
        tipo: String, categoria: String, descripcion: String?, monto: Double,
        metodo: String?, comprobante: String?,
    ): String? = try {
        Supabase.client.postgrest["movimientos"].insert(
            kotlinx.serialization.json.buildJsonObject {
                put("tipo", kotlinx.serialization.json.JsonPrimitive(tipo))
                put("categoria", kotlinx.serialization.json.JsonPrimitive(categoria))
                if (!descripcion.isNullOrBlank()) put("descripcion", kotlinx.serialization.json.JsonPrimitive(descripcion.trim()))
                put("monto", kotlinx.serialization.json.JsonPrimitive(monto))
                if (!metodo.isNullOrBlank()) put("metodo_pago", kotlinx.serialization.json.JsonPrimitive(metodo))
                // '' NO es NULL: el índice único de comprobante cuenta el string vacío
                // (mismo bug ya cazado en la web, DALU 2026-08-31).
                comprobante?.trim()?.takeIf { it.isNotBlank() }?.let {
                    put("comprobante", kotlinx.serialization.json.JsonPrimitive(it))
                }
            }
        )
        null
    } catch (e: Exception) {
        val msg = e.message ?: ""
        when {
            Regex("uq_movimiento_comprobante", RegexOption.IGNORE_CASE).containsMatchIn(msg) ->
                "Ya existe un movimiento con ese comprobante — revisa la lista"
            Regex("jwt|token|expired", RegexOption.IGNORE_CASE).containsMatchIn(msg) ->
                "Tu sesión expiró — vuelve a entrar"
            else -> "No se pudo registrar. Revisa tu conexión."
        }
    }

    suspend fun movimientosDeHoy(): List<MovimientoCaja> {
        val filas = Supabase.client.postgrest["movimientos"]
            .select(Columns.raw("id, tipo, categoria, descripcion, monto, metodo_pago, fecha, created_at, paciente:pacientes(nombre)")) {
                filter { eq("fecha", hoyISO()) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull { o ->
            MovimientoCaja(
                id = o.str("id") ?: return@mapNotNull null,
                tipo = o.str("tipo") ?: "Ingreso",
                categoria = o.str("categoria"),
                descripcion = o.str("descripcion"),
                monto = o.dbl("monto") ?: 0.0,
                metodoPago = o.str("metodo_pago"),
                pacienteNombre = (o["paciente"] as? JsonObject)?.str("nombre"),
            )
        }
    }
}
