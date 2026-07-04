package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Un pago registrado del tratamiento (informativo para el paciente). */
data class PagoInfo(val fecha: String, val monto: Double, val metodo: String?)

/** Cuenta del tratamiento: costo, pagado, saldo y detalle de pagos (informativo). */
data class Saldo(
    val acordado: Double,
    val pagado: Double,
    val saldo: Double,
    val estado: String,
    val pagos: List<PagoInfo> = emptyList(),
)

/** Documento del paciente visible en su portal. */
data class Documento(val id: String, val nombre: String, val categoria: String, val path: String, val fecha: String)

/**
 * Datos de la pestaña Salud que NO se pueden leer con anon key (usan service_role
 * en la web: firmar URLs de storage, leer config de saldo). Se piden al API web
 * con el Bearer token del paciente.
 */
object SaludRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double =
        (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
    private fun JsonObject.intp(k: String): Int =
        (this[k] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    private fun JsonObject.bool(k: String): Boolean =
        (this[k] as? JsonPrimitive)?.content == "true"

    /**
     * Tratamientos del paciente (progreso + timeline) desde el API web con Bearer.
     * La web los lee con service_role (la RLS de tratamientos/sesiones es solo de
     * staff), así que aquí NO se pueden leer directo de Supabase con anon key.
     */
    suspend fun tratamientos(): List<Tratamiento> {
        val tk = token() ?: return emptyList()
        val resp = http.get("${Supabase.SITE_URL}/api/paciente/mi-tratamiento") {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status != HttpStatusCode.OK) return emptyList()
        val arr = json.parseToJsonElement(resp.bodyAsText()).jsonObject["tratamientos"] as? JsonArray
            ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            val ses = (o["sesiones"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull {
                val s = it.jsonObject
                SesionMin(
                    numero = s.intp("numero"),
                    fecha = s.str("fecha") ?: "",
                    estado = s.str("estado") ?: "",
                )
            }
            Tratamiento(
                id = o.str("id") ?: return@mapNotNull null,
                procedimiento = o.str("procedimiento") ?: "Tratamiento",
                clinica = o.str("clinica"),
                clinicaLogo = o.str("clinicaLogo"),
                estado = o.str("estado") ?: "",
                usaSesiones = o.bool("usaSesiones"),
                totalSesiones = (o["totalSesiones"] as? JsonPrimitive)?.content?.toIntOrNull(),
                sesionesCompletadas = o.intp("sesionesCompletadas"),
                fechaInicio = o.str("fechaInicio"),
                sesiones = ses,
            )
        }
    }

    /** Saldos por tratamiento_id (solo de clínicas que lo habilitaron). */
    suspend fun saldos(): Map<String, Saldo> {
        val tk = token() ?: return emptyMap()
        val resp = http.get("${Supabase.SITE_URL}/api/paciente/mis-pagos") {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status != HttpStatusCode.OK) return emptyMap()
        val obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject["saldos"]?.jsonObject ?: return emptyMap()
        return obj.mapNotNull { (id, v) ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val pagos = (o["pagos"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull {
                val p = it.jsonObject
                PagoInfo(
                    fecha = p.str("fecha") ?: return@mapNotNull null,
                    monto = p.dbl("monto"),
                    metodo = p.str("metodo"),
                )
            }
            id to Saldo(o.dbl("acordado"), o.dbl("pagado"), o.dbl("saldo"), o.str("estado") ?: "", pagos)
        }.toMap()
    }

    /** Documentos del paciente (no fotos evolutivas). */
    suspend fun documentos(): List<Documento> {
        val tk = token() ?: return emptyList()
        val resp = http.get("${Supabase.SITE_URL}/api/paciente/mis-documentos") {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status != HttpStatusCode.OK) return emptyList()
        val arr = json.parseToJsonElement(resp.bodyAsText()).jsonObject["documentos"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull {
            val o = it.jsonObject
            Documento(
                id = o.str("id") ?: return@mapNotNull null,
                nombre = o.str("nombre") ?: "Documento",
                categoria = o.str("categoria") ?: "",
                path = o.str("path") ?: return@mapNotNull null,
                fecha = o.str("fecha") ?: "",
            )
        }
    }

    /** URL firmada temporal para abrir un documento. */
    suspend fun urlDocumento(path: String): String? {
        val tk = token() ?: return null
        val resp = http.get("${Supabase.SITE_URL}/api/paciente/mis-documentos") {
            header("Authorization", "Bearer $tk")
            url { parameters.append("path", path) }
        }
        if (resp.status != HttpStatusCode.OK) return null
        return json.parseToJsonElement(resp.bodyAsText()).jsonObject.str("url")
    }

    // ── DNI de la cuenta: la llave que enlaza el portal con sus fichas ──

    /** DNI reclamado por la cuenta, o null si aún no tiene (→ pedirlo). */
    suspend fun dniCuenta(): String? {
        val tk = token() ?: return null
        val resp = http.get("${Supabase.SITE_URL}/api/paciente/dni") {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status != HttpStatusCode.OK) return null
        return json.parseToJsonElement(resp.bodyAsText()).jsonObject.str("dni")
    }

    /**
     * Reclama el DNI para la cuenta (el server valida RENIEC + coincidencia de
     * nombre + único + fijo 30 días). Devuelve null si ok; si no, el mensaje de error.
     */
    suspend fun reclamarDni(dni: String): String? {
        val tk = token() ?: return "Sesión expirada"
        val resp = http.post("${Supabase.SITE_URL}/api/paciente/dni") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody("""{"dni":"$dni"}""")
        }
        if (resp.status == HttpStatusCode.OK) return null
        return runCatching {
            json.parseToJsonElement(resp.bodyAsText()).jsonObject.str("error")
        }.getOrNull() ?: "No se pudo guardar tu DNI"
    }

    /** Citas del portal desde el API web (email O DNI — no solo email). */
    suspend fun misCitas(): Pair<List<CitaPortal>, List<CitaPortal>>? {
        val tk = token() ?: return null
        val resp = http.get("${Supabase.SITE_URL}/api/paciente/mis-citas") {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status != HttpStatusCode.OK) return null
        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        fun mapear(k: String): List<CitaPortal> =
            (root[k] as? JsonArray ?: JsonArray(emptyList())).mapNotNull {
                val o = it.jsonObject
                CitaPortal(
                    id = o.str("id") ?: return@mapNotNull null,
                    fecha = o.str("fecha") ?: "",
                    hora = o.str("hora") ?: "",
                    estado = o.str("estado") ?: "",
                    tipo = o.str("tipo"),
                    profesional = o.str("profesional"),
                    clinica = o.str("clinica"),
                    clinicaSlug = o.str("clinicaSlug"),
                )
            }
        return mapear("proximas") to mapear("pasadas")
    }
}
