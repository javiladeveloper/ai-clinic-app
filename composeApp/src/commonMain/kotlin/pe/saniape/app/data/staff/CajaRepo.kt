package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
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

    // El día de la CLÍNICA (Lima), no el del teléfono: el movimiento se registra
    // con la fecha de Lima en el servidor, y la lista tiene que pedir la misma.
    private fun hoyISO(): String = hoyClinicaIso()

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double? =
        (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()

    /**
     * Registra un movimiento MANUAL en el kardex por /api/staff/movimiento/registrar:
     * la MISMA validación e insert que "+ Registrar Movimiento" de /finanzas web
     * (fecha de Lima, comprobante, método válido, sede). Antes la app insertaba
     * directo en la tabla y las reglas vivían en dos lados. Devuelve null si
     * entró, o el mensaje humano del servidor (comprobante repetido, sesión
     * vencida, sin permiso…).
     */
    suspend fun registrarMovimiento(
        tipo: String, categoria: String, descripcion: String?, monto: Double,
        metodo: String?, comprobante: String?,
    ): String? {
        val r = FinanzasRepo.registrar(tipo, categoria, descripcion?.trim().orEmpty(), monto, metodo, comprobante)
        return if (r.ok) null else (r.error ?: "No se pudo registrar. Revisa tu conexión.")
    }

    suspend fun movimientosDeHoy(): List<MovimientoCaja> {
        val filas = Supabase.client.postgrest["movimientos"]
            .select(Columns.raw("id, tipo, categoria, descripcion, monto, metodo_pago, fecha, created_at, paciente:pacientes(nombre)")) {
                filter {
                    eq("fecha", hoyISO())
                    // Multisede: los movimientos de la sede activa (la principal
                    // incluye los sin sede). "Todas las sedes": sin filtro.
                    filtroSede(SedeActiva.filtro)
                }
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
