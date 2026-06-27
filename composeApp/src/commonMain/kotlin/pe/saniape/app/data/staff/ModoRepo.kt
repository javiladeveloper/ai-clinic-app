package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * Cambia el modo activo (clínica ↔ paciente) y la clínica activa, vía /api/modo
 * (que valida contra perfiles y hace upsert en sesion_clinica_activa). La RLS del
 * servidor resuelve get_clinica_id() con eso. La app guarda el modo localmente.
 */
object ModoRepo {

    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /** modo = "clinica" | "paciente". clinicaId opcional (para activar una clínica). */
    suspend fun cambiar(modo: String, clinicaId: String? = null): Boolean {
        val tk = token() ?: return false
        val cuerpo = buildJsonObject {
            put("modo", modo)
            if (clinicaId != null) put("clinica_id", clinicaId)
        }
        val resp = http.post("${Supabase.SITE_URL}/api/modo") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        return resp.status == HttpStatusCode.OK
    }
}
