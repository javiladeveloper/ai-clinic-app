package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

/** Una notificación de la clínica (campanita): cita confirmada/cancelada, reserva, alerta… */
data class NotificacionClinica(
    val id: String,
    val tipo: String?,
    val titulo: String,
    val cuerpo: String?,
    val icono: String?,       // emoji opcional
    val leida: Boolean,
    val createdAt: String?,   // ISO
)

/**
 * Centro de notificaciones IN-APP (misma tabla `notificaciones` que la campanita web; RLS
 * aísla por clínica). Nota: esto NO es push — con la app cerrada no suena (eso será FCM).
 */
object NotificacionesRepo {

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    /** Últimas 50, más recientes primero. */
    suspend fun listar(): List<NotificacionClinica> {
        val filas = Supabase.client.postgrest["notificaciones"]
            .select(Columns.raw("id, tipo, titulo, cuerpo, icono, leida_at, created_at")) {
                order("created_at", Order.DESCENDING)
                limit(50)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull { o ->
            NotificacionClinica(
                id = o.str("id") ?: return@mapNotNull null,
                tipo = o.str("tipo"),
                titulo = o.str("titulo") ?: "Notificación",
                cuerpo = o.str("cuerpo"),
                icono = o.str("icono"),
                leida = o.str("leida_at") != null,
                createdAt = o.str("created_at"),
            )
        }
    }

    /** Marca TODAS como leídas (al abrir la campanita, como la web). */
    suspend fun marcarTodasLeidas() {
        runCatching {
            Supabase.client.postgrest["notificaciones"].update(
                { set("leida_at", Clock.System.now().toString()) }
            ) { filter { exact("leida_at", null) } }
        }
    }
}
