package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * Avisa cuando una consulta llegó a su TOPE de filas. Gemelo de `avisarSiTope`
 * (lib/tope-consulta.ts): el aviso va a `errores_app` vía `/api/error-cliente`
 * (lo ve el super-admin en 📡 Observabilidad), no al usuario. Best-effort: si el
 * reporte falla, se ignora; avisar de un problema no puede causar otro.
 */
object TopeConsulta {
    private val http by lazy { crearHttpClient() }

    suspend fun avisar(donde: String, detalle: String, tope: Int) {
        runCatching {
            val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken
            http.post("${Supabase.SITE_URL}/api/error-cliente") {
                if (tk != null) header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("ruta", donde)
                        put("mensaje", "tope de consulta alcanzado: $detalle ($tope filas)")
                        putJsonObject("contexto") { put("tope", tope); put("detalle", detalle) }
                    }.toString()
                )
            }
        }
    }
}
