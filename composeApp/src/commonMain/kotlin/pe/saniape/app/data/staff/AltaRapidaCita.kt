package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.nuevaIdemKey

/**
 * Alta RÁPIDA de paciente desde el buscador de "Crear cita" — gemelo de
 * `components/ui/BuscadorPaciente.tsx` (web): se escribe un documento que no
 * está en la clínica → (Perú) se consulta el padrón por DNI, "Es correcto,
 * registrar" crea la ficha y la deja elegida en la cita. Documento extranjero
 * (RUT, pasaporte) o sede fuera de Perú: el nombre se escribe a mano.
 *
 * La app no reimplementa nada: el padrón es GET /api/dni/[dni] y el alta es
 * POST /api/staff/paciente/crear (dedup por DNI + idempotencia). Aquí solo
 * vive lo puro (qué se ofrece, cómo se leen las respuestas), testeado.
 */

/** ¿Lo escrito parece un documento? Mismas tres formas que la web: DNI, RUT, pasaporte. */
fun pareceDocumento(texto: String): Boolean {
    val d = texto.trim()
    if (Regex("^\\d{8}$").matches(d)) return true                                        // DNI peruano
    if (d.any { it.isDigit() } && Regex("^[\\dkK.\\-\\s]{7,15}$").matches(d)) return true  // RUT chileno / CI
    return Regex("^[A-Za-z]{1,3}\\d{5,12}$").matches(d)                                    // pasaporte
}

/**
 * El documento a dar de alta, o null si no corresponde ofrecerlo: lo escrito no
 * parece un documento, o ya hay pacientes que coinciden (se elige uno de ellos).
 */
fun documentoParaAltaRapida(texto: String, hayCoincidencias: Boolean): String? {
    val d = texto.trim()
    return if (!hayCoincidencias && pareceDocumento(d)) d else null
}

/** ¿Se puede consultar el padrón (RENIEC)? Solo un DNI de 8 dígitos y solo en sedes de Perú. */
fun consultaPadron(documento: String, pais: String?): Boolean =
    usaReniec(pais) && Regex("^\\d{8}$").matches(documento.trim())

/**
 * ¿Se ofrece registrar al paciente desde la cita? Como la web: solo en Consulta
 * y Evaluación (las citas de paciente nuevo) y para quien puede agendar. En una
 * Sesión el paciente existe por fuerza (tiene tratamiento): ofrecer crearlo
 * ahí es un camino a duplicados.
 */
fun altaRapidaPermitida(tipo: String, puedeAgendar: Boolean): Boolean =
    puedeAgendar && (tipo == "Consulta" || tipo == "Evaluación")

/** Estado de la consulta al padrón / del registro (el `Hallazgo` de la web). */
sealed interface HallazgoPadron {
    data object Buscando : HallazgoPadron
    data class Encontrado(val nombre: String) : HallazgoPadron
    data object SinDatos : HallazgoPadron
    data class Error(val mensaje: String) : HallazgoPadron
    data object Creando : HallazgoPadron
}

private fun JsonObject.texto(k: String): String? =
    (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }

private fun leerObjeto(cuerpo: String): JsonObject? =
    runCatching { Json.parseToJsonElement(cuerpo) as? JsonObject }.getOrNull()

/** Respuesta de GET /api/dni/[dni] → hallazgo (mismos mensajes que la web). */
fun interpretarPadron(status: Int, cuerpo: String): HallazgoPadron {
    if (status == 503) return HallazgoPadron.Error("La búsqueda por DNI no está configurada.")
    if (status == 429) return HallazgoPadron.Error("Demasiadas búsquedas. Espera un momento.")
    val o = leerObjeto(cuerpo)
    val ok = (o?.get("success") as? JsonPrimitive)?.content == "true"
    val nombre = (o?.get("data") as? JsonObject)?.texto("nombre_completo")?.trim()
    return if (status in 200..299 && ok && !nombre.isNullOrBlank()) HallazgoPadron.Encontrado(nombre)
    else HallazgoPadron.SinDatos
}

/**
 * Resultado de POST /api/staff/paciente/crear. [inactivo] = ese documento es de
 * una ficha DADA DE BAJA: no se creó nada (hay que reactivarla, no elegirla).
 */
data class ResultadoAltaRapida(
    val id: String?,
    val inactivo: Boolean = false,
    val error: String? = null,
    /** Sede en que nació (pacientes por sede): la cita se agenda ahí. */
    val sedeId: String? = null,
) {
    val ok: Boolean get() = id != null && error == null
}

fun interpretarAltaRapida(status: Int, cuerpo: String): ResultadoAltaRapida {
    val o = leerObjeto(cuerpo)
    val id = o?.texto("id")
    if (status !in 200..299 || id == null) {
        return ResultadoAltaRapida(null, error = o?.texto("error") ?: "No se pudo registrar.")
    }
    val inactivo = (o["inactivo"] as? JsonPrimitive)?.content == "true"
    return ResultadoAltaRapida(id, inactivo = inactivo)
}

object AltaRapidaRepo {
    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /** Nombre en el padrón por DNI. Se pide con un toque (llamada externa con límite diario). */
    suspend fun consultarPadron(dni: String): HallazgoPadron = try {
        val tk = token() ?: return HallazgoPadron.Error("Sesión vencida. Vuelve a entrar.")
        val resp = http.get("${Supabase.SITE_URL}/api/dni/${dni.trim()}") { header("Authorization", "Bearer $tk") }
        interpretarPadron(resp.status.value, resp.bodyAsText())
    } catch (_: Exception) {
        HallazgoPadron.Error("No se pudo consultar. Revisa tu conexión.")
    }

    /**
     * Registra al paciente con nombre + documento y devuelve su id REAL (para
     * agendarle la cita ya). Directo, no por la cola: sin id real no hay cita.
     * El endpoint deduplica por DNI: si otro lo creó entre medias, devuelve ese.
     */
    suspend fun registrar(nombre: String, documento: String, tipoDocumento: String? = null): ResultadoAltaRapida = try {
        val tk = token() ?: return ResultadoAltaRapida(null, error = "Sesión vencida. Vuelve a entrar.")
        // Pacientes por sede: nace en la sede activa (igual que el alta completa).
        val sede = SedeActiva.estado.value.let { sedeParaPacienteNuevo(it.pacientesPorSede, it.filtro?.sedeId) }
        val cuerpo = buildJsonObject {
            put("nombre", nombre.trim())
            put("dni", documento.trim())
            // Tipo del documento (pasaporte, carné, CI…); sin tipo = el del país.
            tipoDocumentoAGuardar(tipoDocumento, documento)?.let { put("tipo_documento", it) }
            put("fecha_ingreso", hoyClinicaIso())
            if (sede != null) put("sede_id", sede)
            put("idempotency_key", "alta-rapida:${documento.trim()}:${nuevaIdemKey()}")
        }
        val resp = pe.saniape.app.ui.conIndicador {
            http.post("${Supabase.SITE_URL}/api/staff/paciente/crear") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(cuerpo.toString())
            }
        }
        val r = interpretarAltaRapida(resp.status.value, resp.bodyAsText()).copy(sedeId = sede)
        if (r.ok && !r.inactivo) pe.saniape.app.tutoriales.MotorTutoriales.tarea("paciente_creado")
        r
    } catch (_: Exception) {
        ResultadoAltaRapida(null, error = "No se pudo registrar. Revisa tu conexión.")
    }
}
