package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/** Medicamento sugerido para la receta: aprendido de la clínica o del petitorio PNUME. */
@Serializable
data class SugerenciaMedicamento(
    val dci: String,
    val concentracion: String? = null,
    val forma: String? = null,
    val via: String? = null,
    val usos: Int = 0,
    /** "clinica" | "petitorio" */
    val origen: String? = null,
)

/**
 * La consulta guiada contra el servidor (docs/app-contrato-atencion.md en la web):
 * `/api/staff/atencion/…`, el buscador CIE-10, las recetas y los imprimibles.
 *
 * Las escrituras van DIRECTO al servidor, sin cola offline: `control`,
 * `indicar-procedimiento` y `examen-resultado` crean filas y no se reintentan a
 * ciegas; el resto se repite a mano sin daño. Sin señal → `SIN_RED` y la
 * pantalla decide. Cada escritura devuelve el JSON del servidor en
 * [ResultadoEscritura.cuerpo] (la receta emitida, el id de la cita de control…).
 *
 * Ojo con el 401: en cie10/receta/imprimir el token vencido llega con
 * `codigo: SIN_PERMISO`. Para saber si hay que re-entrar, mirar `status == 401`.
 */
object AtencionRepo {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
    private val http = crearHttpClient()

    private const val SIN_RED = "SIN_RED"
    private const val MSJ_SIN_RED = "Sin conexión. Revisa tu internet."

    /** Textos de la atención (claves de `guardar`, §3 del contrato). */
    private val CAMPOS_TEXTO = listOf(
        "motivo_consulta", "tiempo_enfermedad", "relato", "funciones_biologicas",
        "examen_fisico", "plan_trabajo", "tratamiento", "observaciones",
    )

    /** Vitales que `guardar` reemplaza siempre (ausente = se borra). */
    private val CAMPOS_VITALES = listOf(
        "presion_sistolica", "presion_diastolica", "frecuencia_cardiaca",
        "frecuencia_respiratoria", "temperatura", "saturacion_o2", "peso", "talla",
    )

    /** Resultado de abrir la consulta. [Error.status] 401 = sesión vencida. */
    sealed class Carga {
        data class Ok(val datos: DatosConsultaApp) : Carga()
        data class Error(val mensaje: String, val codigo: String?, val sinRed: Boolean, val status: Int = 0) : Carga()
    }

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val sinSesion = ResultadoEscritura(
        registrada = false,
        rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val sinRed = ResultadoEscritura(registrada = false, rechazo = RechazoServidor(MSJ_SIN_RED, SIN_RED))

    // ── Lecturas ─────────────────────────────────────────────────────────────

    /** GET consulta: toda la pantalla en una ida (y la marca "En consulta" si puede atender). */
    suspend fun cargar(citaId: String): Carga {
        val tk = token() ?: return Carga.Error("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", sinRed = false, status = 401)
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/atencion/consulta") {
                header("Authorization", "Bearer $tk")
                parameter("cita", citaId)
            }
            val texto = resp.bodyAsText()
            if (resp.status.value in 200..299) {
                runCatching { Carga.Ok(parsearConsulta(texto)) }.getOrElse {
                    Carga.Error("No se pudo leer la consulta.", "RESPUESTA_INVALIDA", sinRed = false, status = resp.status.value)
                }
            } else {
                val r = resultadoDeRespuesta(resp.status.value, texto).rechazo!!
                Carga.Error(r.error, r.codigo, sinRed = false, status = r.status)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Carga.Error(MSJ_SIN_RED, SIN_RED, sinRed = true)
        }
    }

    /** Buscador CIE-10 (máx. 30). `q` < 2 letras = los frecuentes del rubro. Vacío si falla. */
    suspend fun buscarCie10(q: String, dental: Boolean): List<DiagnosticoCie> {
        val tk = token() ?: return emptyList()
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/cie10") {
                header("Authorization", "Bearer $tk")
                parameter("q", q.take(80))
                if (dental) parameter("dental", "1")
            }
            if (resp.status.value !in 200..299) emptyList() else parsearCie10(resp.bodyAsText())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // Una búsqueda cancelada (llegó otra letra) no devuelve vacío: si no, pisaría la más nueva.
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Medicamentos aprendidos (los más usados primero) + petitorio PNUME. Vacío si falla. */
    suspend fun sugerenciasMedicamentos(): List<SugerenciaMedicamento> {
        val tk = token() ?: return emptyList()
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/receta/sugerencias") {
                header("Authorization", "Bearer $tk")
            }
            if (resp.status.value !in 200..299) emptyList() else parsearSugerencias(resp.bodyAsText())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * HTML imprimible. [tipo]: `indicaciones` (id = CITA), `receta` o
     * `consentimiento` (id = el documento). null si no se pudo.
     */
    suspend fun htmlImprimible(tipo: String, id: String): String? {
        val tk = token() ?: return null
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/imprimir/${tipo.encodeURLPathPart()}/${id.encodeURLPathPart()}") {
                header("Authorization", "Bearer $tk")
            }
            if (resp.status.value in 200..299) resp.bodyAsText() else null
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    // ── Escrituras ───────────────────────────────────────────────────────────

    /**
     * Guarda la atención. REEMPLAZA la atención completa: [b] debe ser el estado
     * entero de la pantalla. `cuerpo.atencion` trae la fila guardada; un borrador
     * en blanco responde 200 con `vacia: true` (no es error).
     */
    suspend fun guardar(citaId: String, b: BorradorAtencion): ResultadoEscritura =
        postJson("/api/staff/atencion/guardar", cuerpoGuardar(citaId, b))

    /** "✓ Terminar atención". Hay que [guardar] antes. `textos` solo alimenta las frases aprendidas. */
    suspend fun terminar(citaId: String, notaProcedimiento: String?, textos: Map<String, String>): ResultadoEscritura =
        postJson("/api/staff/atencion/terminar", buildJsonObject {
            put("citaId", citaId)
            put("notaProcedimiento", notaProcedimiento)
            put("textos", JsonObject(textos.mapValues { JsonPrimitive(it.value) }))
        })

    /** "📅 Próximo control". `fecha` YYYY-MM-DD, `hora` HH:mm. `cuerpo.citaId` = la cita creada. */
    suspend fun control(citaId: String, fecha: String, hora: String, motivo: String?): ResultadoEscritura =
        postJson("/api/staff/atencion/control", buildJsonObject {
            put("citaId", citaId)
            put("fecha", fecha)
            put("hora", hora.take(5))
            put("motivo", motivo?.trim()?.takeIf { it.isNotEmpty() }?.take(200))
        })

    /**
     * Indica un procedimiento. [cuando]: "hoy" | "programar" (este exige fecha y hora).
     * `cuerpo`: `tratamientoId`, `citaId`, `consentimientos`, `avisoConsentimiento`.
     */
    suspend fun indicarProcedimiento(
        citaId: String, servicioId: String, cuando: String,
        fecha: String?, hora: String?, diagnostico: String?,
    ): ResultadoEscritura =
        postJson("/api/staff/atencion/indicar-procedimiento", buildJsonObject {
            put("citaId", citaId)
            put("servicioId", servicioId)
            put("cuando", cuando)
            put("fecha", fecha)
            put("hora", hora?.take(5))
            put("diagnostico", diagnostico?.trim()?.takeIf { it.isNotEmpty() })
        })

    /**
     * Adjunta el resultado del examen en la posición [indice] de `atencion.examenes`
     * (foto o PDF, hasta 15 MB). `cuerpo.examenes` = la lista actualizada.
     */
    suspend fun adjuntarResultado(
        citaId: String, indice: Int, bytes: ByteArray, nombre: String, mime: String, lectura: String?,
    ): ResultadoEscritura {
        val tk = token() ?: return sinSesion
        return try {
            val resp = http.post("${Supabase.SITE_URL}/api/staff/atencion/examen-resultado") {
                header("Authorization", "Bearer $tk")
                setBody(MultiPartFormDataContent(formData {
                    append("citaId", citaId)
                    append("indice", indice.toString())
                    lectura?.trim()?.takeIf { it.isNotEmpty() }?.let { append("lectura", it) }
                    append("archivo", bytes, Headers.build {
                        append(HttpHeaders.ContentType, mime)
                        append(HttpHeaders.ContentDisposition, "filename=\"${nombre.replace("\"", "")}\"")
                    })
                }))
            }
            aResultado(resp)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            sinRed
        }
    }

    /** "Firmó" / "No aceptó". [resultado]: "Firmado" | "Rechazado". [id] = el consentimiento (de ESA cita). */
    suspend fun firmaConsentimiento(citaId: String, id: String, resultado: String): ResultadoEscritura =
        postJson("/api/staff/atencion/consentimiento-firma", buildJsonObject {
            put("citaId", citaId)
            put("consentimientoId", id)
            put("resultado", resultado)
        })

    /** Hoja de filiación de la HC. Parcial: lo que no venga no se toca. `cuerpo.hc` = la HC. */
    suspend fun guardarFiliacion(pacienteId: String, filiacion: Map<String, String?>): ResultadoEscritura =
        postJson("/api/staff/atencion/filiacion", buildJsonObject {
            put("pacienteId", pacienteId)
            put("filiacion", JsonObject(filiacion.mapValues { JsonPrimitive(it.value) }))
        })

    /**
     * Emite una receta ([cuerpo] lo arma el diálogo, con su `claveCliente`).
     * `cuerpo.receta` = la receta; `cuerpo.repetida` = ya se había emitido con esa clave.
     */
    suspend fun emitirReceta(cuerpo: JsonObject): ResultadoEscritura =
        postJson("/api/staff/receta/emitir", cuerpo)

    private suspend fun postJson(endpoint: String, cuerpo: JsonObject): ResultadoEscritura {
        val tk = token() ?: return sinSesion
        return try {
            val resp = http.post("${Supabase.SITE_URL}$endpoint") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(cuerpo.toString())
            }
            aResultado(resp).also { r ->
                if (r.registrada) pe.saniape.app.tutoriales.TareasEscritura.emitir(endpoint, cuerpo, r.cuerpo)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            sinRed
        }
    }

    private suspend fun aResultado(resp: HttpResponse): ResultadoEscritura =
        resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())

    // ── Puras (testeables sin red) ───────────────────────────────────────────

    /** El JSON de `GET consulta` tal cual (los modelos lo espejan, ver Atencion.kt). */
    internal fun parsearConsulta(cuerpo: String): DatosConsultaApp =
        json.decodeFromString(DatosConsultaApp.serializer(), cuerpo)

    internal fun parsearCie10(cuerpo: String): List<DiagnosticoCie> =
        runCatching {
            json.parseToJsonElement(cuerpo).jsonObject["resultados"]?.jsonArray
                ?.map { json.decodeFromJsonElement(DiagnosticoCie.serializer(), it) }
        }.getOrNull().orEmpty()

    /** `cuerpo.examenes` de examen-resultado (la lista ya con el documento). null si no vino. */
    internal fun examenesDeRespuesta(cuerpo: JsonObject?): List<ExamenSolicitado>? =
        runCatching {
            (cuerpo?.get("examenes") as? JsonArray)
                ?.map { json.decodeFromJsonElement(ExamenSolicitado.serializer(), it) }
        }.getOrNull()

    internal fun parsearSugerencias(cuerpo: String): List<SugerenciaMedicamento> =
        runCatching {
            json.parseToJsonElement(cuerpo).jsonObject["sugerencias"]?.jsonArray
                ?.map { json.decodeFromJsonElement(SugerenciaMedicamento.serializer(), it) }
        }.getOrNull().orEmpty()

    /**
     * El cuerpo de `guardar` (§3). Como REEMPLAZA la atención:
     *  · textos: van los que tiene el borrador (el que falta queda vacío en el servidor);
     *    `nota_procedimiento` solo si está en el borrador (si no viene, no se toca).
     *  · vitales: los 8 van SIEMPRE, con el texto tal cual ("36,5") o null si está vacío;
     *    `perimetro_abdominal` solo si está en el borrador (si no viene, no se toca).
     *  · `diagnosticos` y `examenes` van siempre (con `tipo` "P" por defecto: encodeDefaults).
     */
    internal fun cuerpoGuardar(citaId: String, b: BorradorAtencion): JsonObject {
        fun vital(v: String?): JsonPrimitive = v?.trim()?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull
        val borrador = buildJsonObject {
            put("terapeutaId", b.terapeutaId)
            b.tratamientoId?.let { put("tratamientoId", it) }
            for (k in CAMPOS_TEXTO) b.textos[k]?.let { put(k, it) }
            b.textos["nota_procedimiento"]?.let { put("nota_procedimiento", it) }
            for (k in CAMPOS_VITALES) put(k, vital(b.vitales[k]))
            if ("perimetro_abdominal" in b.vitales) put("perimetro_abdominal", vital(b.vitales["perimetro_abdominal"]))
            put("diagnosticos", json.encodeToJsonElement(b.diagnosticos))
            put("examenes", json.encodeToJsonElement(b.examenes))
        }
        return buildJsonObject {
            put("citaId", citaId)
            put("borrador", borrador)
        }
    }

    /**
     * Respuesta HTTP → resultado. 2xx = registrada (con el JSON en `cuerpo`).
     * Si no, el rechazo con el `error`/`codigo` del servidor; si trae varios
     * `errores` (422 de validación), se muestran todos, uno por línea.
     */
    internal fun resultadoDeRespuesta(status: Int, cuerpo: String?): ResultadoEscritura {
        val obj = cuerpo?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        if (status in 200..299) return ResultadoEscritura(registrada = true, cuerpo = obj)
        fun str(k: String) = (obj?.get(k) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val errores = (obj?.get("errores") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
            .orEmpty()
        val mensaje = when {
            errores.size > 1 -> errores.joinToString("\n")
            else -> str("error") ?: errores.firstOrNull() ?: "No se pudo completar (HTTP $status)."
        }
        return ResultadoEscritura(
            registrada = false,
            rechazo = RechazoServidor(mensaje, str("codigo"), status),
            cuerpo = obj,
        )
    }
}
