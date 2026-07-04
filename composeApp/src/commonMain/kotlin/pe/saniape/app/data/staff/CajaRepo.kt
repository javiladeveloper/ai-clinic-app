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
