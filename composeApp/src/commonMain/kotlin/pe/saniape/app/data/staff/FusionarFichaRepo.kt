package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.tutoriales.mensajeError
import kotlin.random.Random

// «🔀 Fusionar» del ⋯ de la ficha (11/11/2026, paridad con la web
// components/pacientes/FusionarPacientes.tsx). Solo Admin. La ficha abierta es la
// que QUEDA; la elegida se junta en ella. Mismo endpoint que la web
// (GET previa / POST /api/staff/paciente/fusionar). En los datos que chocan queda
// el de la ficha abierta (lo que hace el servidor si no se elige nada).

data class ChoqueFusion(val codigo: String, val bloquea: Boolean, val detalle: String)

data class PreviaFusion(
    /** «citas: 3» de la ficha que se junta, solo lo que tiene algo. */
    val seMueve: List<Pair<String, Int>>,
    val choques: List<ChoqueFusion>,
) {
    val bloqueada: Boolean get() = choques.any { it.bloquea }
}

object FusionarFichaRepo {
    sealed class Previa {
        data class Ok(val previa: PreviaFusion) : Previa()
        data class Error(val mensaje: String) : Previa()
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { crearHttpClient() }
    private fun JsonObject.txt(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    /** Nombres legibles de las tablas que más aparecen; el resto, tal cual. */
    private val NOMBRES = mapOf(
        "citas" to "citas", "sesiones" to "sesiones", "tratamientos" to "tratamientos", "pagos_tratamiento" to "pagos",
        "dientes_hallazgos" to "hallazgos del odontograma", "recetas" to "recetas", "documentos_paciente" to "documentos",
        "consentimientos_firmados" to "consentimientos",
    )

    /** GET previa → PreviaFusion (puro, para probarlo). */
    internal fun interpretarPrevia(status: Int, cuerpo: String): Previa {
        val o = runCatching { json.parseToJsonElement(cuerpo).jsonObject }.getOrNull()
        val previa = o?.get("previa") as? JsonObject
        if (status !in 200..299 || previa == null) {
            return Previa.Error(mensajeError(cuerpo) ?: if (status == 403) "Solo un Admin puede fusionar fichas." else "No se pudo revisar las fichas.")
        }
        val conteos = previa["conteos"] as? JsonObject
        val seMueve = conteos?.mapNotNull { (tabla, v) ->
            val n = ((v as? JsonObject)?.get("absorber") as? JsonPrimitive)?.intOrNull ?: 0
            if (n > 0) (NOMBRES[tabla] ?: tabla.replace('_', ' ')) to n else null
        }.orEmpty().sortedByDescending { it.second }
        val choques = (previa["choques"] as? JsonArray).orEmpty().mapNotNull { e ->
            val c = e as? JsonObject ?: return@mapNotNull null
            ChoqueFusion(
                codigo = c.txt("codigo") ?: "",
                bloquea = (c["bloquea"] as? JsonPrimitive)?.booleanOrNull == true,
                detalle = c.txt("detalle") ?: "",
            )
        }
        return Previa.Ok(PreviaFusion(seMueve, choques))
    }

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    suspend fun previa(conservarId: String, absorberId: String): Previa {
        val tk = token() ?: return Previa.Error("Tu sesión expiró. Vuelve a entrar.")
        return try {
            val r = http.get("${Supabase.SITE_URL}/api/staff/paciente/fusionar") {
                header("Authorization", "Bearer $tk")
                parameter("conservarId", conservarId); parameter("absorberId", absorberId)
            }
            interpretarPrevia(r.status.value, r.bodyAsText())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (_: Exception) {
            Previa.Error("Sin conexión. Revisa tu internet.")
        }
    }

    /** Clave del intento (8–100): la MISMA al reintentar, así la base no fusiona dos veces. */
    fun nuevaClave(): String = "app-" + (1..24).map { "abcdefghijkmnpqrstuvwxyz23456789"[Random.nextInt(32)] }.joinToString("")

    /** null = listo; si no, el mensaje de error. */
    suspend fun fusionar(conservarId: String, absorberId: String, clave: String): String? {
        val tk = token() ?: return "Tu sesión expiró. Vuelve a entrar."
        return try {
            val r = http.post("${Supabase.SITE_URL}/api/staff/paciente/fusionar") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("conservarId", conservarId); put("absorberId", absorberId); put("clave", clave)
                }.toString())
            }
            if (r.status.value in 200..299) null else mensajeError(r.bodyAsText()) ?: "No se pudo fusionar."
        } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (_: Exception) {
            "Sin conexión: no se sabe si se fusionó. Vuelve a intentarlo (no se duplica)."
        }
    }
}
