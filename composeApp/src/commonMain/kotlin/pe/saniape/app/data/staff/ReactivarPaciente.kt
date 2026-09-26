package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.nuevaIdemKey

/**
 * Paciente DADO DE BAJA que vuelve (paridad con la web, 2026-09-26).
 *
 * "Doy de baja a un paciente y, si luego vuelve, al buscarlo me aparece como
 * nuevo; lo registro y debería llenarme lo que ya tenía, solo modifico algunas
 * cosas, y vuelve a estar activo." La app NO reimplementa la regla: detecta la
 * ficha con GET /api/staff/paciente/de-baja y reactiva con POST
 * /api/staff/paciente/reactivar — los mismos endpoints que la web. El servidor
 * decide el estado de vuelta (tratamiento activo → En tratamiento; si no, el
 * previo a la baja) y ACTUALIZA la misma ficha: nunca crea otra, el historial
 * queda intacto. Permiso: 'datos_personales' (como el botón Reactivar web).
 */
data class FichaDeBaja(
    val id: String,
    val nombre: String,
    val dni: String?,
    val telefono: String?,
    val email: String?,
    val edad: Int?,
    val ocupacion: String?,
    val diagnostico: String?,
    val observaciones: String?,
    val antecedentes: String?,
    val alergias: String?,
    val medicacionActual: String?,
    val tipoPatologia: String?,
    val patologias: List<String>,
    val flag: String?,
    val talla: Int?,
    val peso: Double?,
    val nTratamientos: Int,
    /** "dd/mm/aaaa" en hora de Lima (lo formatea el servidor). */
    val fechaBajaTexto: String?,
)

/** Resultado de reactivar: el estado real en que quedó, o el error del servidor. */
data class ResultadoReactivar(val ok: Boolean, val id: String?, val estado: String?, val error: String?)

private fun JsonObject.txt(k: String): String? =
    (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }

/** JSON de /de-baja → [FichaDeBaja]. null si no trae id o nombre. Puro (testeable). */
fun parsearFichaDeBaja(o: JsonObject): FichaDeBaja? {
    val id = o.txt("id") ?: return null
    val nombre = o.txt("nombre") ?: return null
    return FichaDeBaja(
        id = id, nombre = nombre, dni = o.txt("dni"), telefono = o.txt("telefono"),
        email = o.txt("email"), edad = o.txt("edad")?.toDoubleOrNull()?.toInt(),
        ocupacion = o.txt("ocupacion"), diagnostico = o.txt("diagnostico"),
        observaciones = o.txt("observaciones"), antecedentes = o.txt("antecedentes"),
        alergias = o.txt("alergias"), medicacionActual = o.txt("medicacion_actual"),
        tipoPatologia = o.txt("tipo_patologia"),
        patologias = (o["patologias"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() } }
            ?: emptyList(),
        flag = o.txt("flag"),
        talla = o.txt("talla")?.toDoubleOrNull()?.toInt(),
        peso = o.txt("peso")?.toDoubleOrNull(),
        nTratamientos = o.txt("n_tratamientos")?.toIntOrNull() ?: 0,
        fechaBajaTexto = o.txt("fecha_baja_texto"),
    )
}

/** "Rosa estaba dado de baja el 12/05/2026 · ¿Reactivar su ficha?" */
fun textoFichaDeBaja(f: FichaDeBaja): String =
    "${f.nombre} estaba dado de baja" + (f.fechaBajaTexto?.let { " el $it" } ?: "") + " · ¿Reactivar su ficha?"

/** ¿El documento escrito está completo como para buscarlo? (mismo criterio que la web) */
fun documentoCompleto(doc: String, paisDoc: String): Boolean {
    val d = doc.trim()
    return if (paisDoc == "PE") d.length == 8 && d.all { it.isDigit() }
    else d.count { it.isLetterOrDigit() } >= 5
}

object ReactivarRepo {
    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /** Ficha de baja de ESTA clínica con ese documento exacto, o null (sin permiso/sin red = null). */
    suspend fun porDocumento(doc: String): FichaDeBaja? = try {
        val tk = token() ?: return null
        val resp = http.get("${Supabase.SITE_URL}/api/staff/paciente/de-baja?dni=${doc.trim().encodeURLParameter()}") {
            header("Authorization", "Bearer $tk")
        }
        if (!resp.status.isSuccess()) null
        else ((Json.parseToJsonElement(resp.bodyAsText()) as? JsonObject)?.get("resultados") as? JsonArray)
            ?.firstNotNullOfOrNull { (it as? JsonObject)?.let(::parsearFichaDeBaja) }
    } catch (_: Exception) { null }

    /**
     * Reactiva la ficha aplicando los datos corregidos ([cambios] con los nombres
     * de columna: nombre, telefono, email, ocupacion, diagnostico, observaciones,
     * antecedentes, alergias, medicacion_actual, tipo_patologia, patologias,
     * flag, edad, talla, peso). Vacío = no se toca.
     */
    suspend fun reactivar(pacienteId: String, cambios: JsonObject = JsonObject(emptyMap())): ResultadoReactivar = try {
        val tk = token() ?: return ResultadoReactivar(false, null, null, "Sesión vencida. Vuelve a entrar.")
        val cuerpo = buildJsonObject {
            put("pacienteId", pacienteId)
            put("cambios", cambios)
            put("idempotency_key", nuevaIdemKey())
        }
        val resp = http.post("${Supabase.SITE_URL}/api/staff/paciente/reactivar") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        val o = runCatching { Json.parseToJsonElement(resp.bodyAsText()) as? JsonObject }.getOrNull()
        if (resp.status.isSuccess() && o?.txt("id") != null) ResultadoReactivar(true, o.txt("id"), o.txt("estado"), null)
        else ResultadoReactivar(false, null, null, o?.txt("error") ?: "No se pudo reactivar")
    } catch (_: Exception) {
        ResultadoReactivar(false, null, null, "No se pudo reactivar. Revisa tu conexión.")
    }
}
