package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura
import pe.saniape.app.data.offline.nuevaIdemKey

/**
 * La evaluación psicológica contra el servidor (contrato:
 * docs/app-contrato-evaluacion-psico.md en la web): `/api/staff/evaluacion-psico/…`.
 *
 * Todo DIRECTO, sin cola offline: el autoguardado se repite solo (último gana)
 * y el resto se reintenta a mano. Las escrituras que no son idempotentes
 * (`test crear`, `informe emitir`, `agregar-cita`) llevan `idempotency_key`; si
 * fallan, el servidor responde SIN `codigo` (hay que mirar el HTTP).
 *
 * Si el servidor todavía no tiene estos endpoints (404 sin `codigo`) la pantalla
 * lo dice; los botones ni aparecen, porque [procedimientosEvaluacion] devuelve
 * vacío mientras la base no tenga la columna `tipo_clinico`.
 */
object EvaluacionPsicoRepo {

    private const val BASE = "/api/staff/evaluacion-psico"
    private const val MSJ_SIN_RED = "Sin conexión. Revisa tu internet."
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = crearHttpClient()

    sealed class Carga {
        data class Ok(val espacio: EspacioEvalPsico) : Carga()
        data class Error(val mensaje: String, val codigo: String?, val status: Int = 0, val sinRed: Boolean = false) : Carga()
    }

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val sinSesion = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val sinRed = ResultadoEscritura(registrada = false, rechazo = RechazoServidor(MSJ_SIN_RED, "SIN_RED"))

    // ── Qué servicios son evaluación psicológica ─────────────────────────────

    /** (clínica, cuándo, ids). Se pide una vez cada 5 minutos por clínica. */
    private var procsCache: Triple<String, Long, Set<String>>? = null
    private const val VIGENCIA_PROCS_MS = 5L * 60 * 1000

    fun limpiarCache() { procsCache = null; catalogoCache = null }

    /**
     * Ids de los servicios marcados `tipo_clinico = 'evaluacion_psicologica'`.
     * Consulta chica y APARTE (no en el select de la ficha): si la base aún no
     * tiene la columna, falla sola y devuelve vacío — nada aparece y la ficha
     * sigue igual. Sin servicios así (DALU y casi todas) tampoco cambia nada.
     */
    suspend fun procedimientosEvaluacion(): Set<String> {
        val clinica = StaffContextoRepo.actual?.clinicaId.orEmpty()
        val ahora = Clock.System.now().toEpochMilliseconds()
        procsCache?.takeIf { it.first == clinica && ahora - it.second < VIGENCIA_PROCS_MS }?.let { return it.third }
        val ids = try {
            Supabase.client.postgrest["procedimientos"]
                .select(Columns.list("id")) { filter { eq("tipo_clinico", TIPO_CLINICO_EVALUACION_PSICO) } }
                .decodeList<JsonObject>()
                .mapNotNull { (it["id"] as? JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() && s != "null" } }
                .toSet()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptySet() }
        procsCache = Triple(clinica, ahora, ids)
        return ids
    }

    /**
     * Profesionales que atienden el tratamiento (sus citas, sus sesiones y el
     * equipo de sus citas): con ellos la ficha decide si ofrece "🧠 Evaluación"
     * a quien solo atendió una cita. La base decide igual (RLS); esto evita
     * ofrecer un botón que respondería "sin acceso". Si algo falla, vacío.
     */
    suspend fun profesionalesDelTratamiento(tratamientoId: String): List<String> {
        fun ids(filas: List<JsonObject>, k: String) =
            filas.mapNotNull { (it[k] as? JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() && s != "null" } }
        val citas = try {
            Supabase.client.postgrest["citas"].select(Columns.list("id, terapeuta_id")) {
                filter { eq("tratamiento_id", tratamientoId) }
                limit(200)
            }.decodeList<JsonObject>()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        val sesiones = try {
            Supabase.client.postgrest["sesiones"].select(Columns.list("terapeuta_id")) {
                filter { eq("tratamiento_id", tratamientoId) }
                limit(200)
            }.decodeList<JsonObject>()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        val citaIds = ids(citas, "id")
        val equipo = if (citaIds.isEmpty()) emptyList() else try {
            Supabase.client.postgrest["cita_equipo"].select(Columns.list("terapeuta_id")) {
                filter { isIn("cita_id", citaIds) }
            }.decodeList<JsonObject>()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        return (ids(citas, "terapeuta_id") + ids(sesiones, "terapeuta_id") + ids(equipo, "terapeuta_id")).distinct()
    }

    /**
     * La URL para VER una foto protegida o el PDF del informe. Un solo lugar:
     *  1. si el servidor manda cómo verla ([url]: firmada, o un endpoint `/api/...`
     *     que responde `{ url }`), eso;
     *  2. si no, el endpoint del material protegido por id
     *     (`GET /api/staff/evaluacion-psico/foto?documentoId=` → `{ url }`, solo
     *     Admin o tratante; las rutas `protegido/` ya no se firman por path);
     *  3. con un servidor que aún no lo tiene (404/405), el firmado de siempre por
     *     `path` (`/api/documento`).
     * Sin acceso → null y la pantalla lo dice. La UI nunca arma rutas de Storage.
     */
    suspend fun urlDeArchivo(documentoId: String?, path: String?, url: String?): String? {
        val directa = url?.trim()?.ifBlank { null }
        if (directa != null) {
            if (directa.startsWith("http")) return directa
            if (directa.startsWith("/")) return urlDeEndpoint(directa).first
        }
        if (!documentoId.isNullOrBlank()) {
            val (u, status) = urlDeEndpoint("$BASE/foto?documentoId=$documentoId")
            if (u != null) return u
            // 403 = sin acceso (no se intenta por otro lado); otro error real, tampoco.
            if (status != 404 && status != 405) return null
        }
        val p = path?.trim()?.ifBlank { null } ?: return null
        return SolicitudesRepo.urlFirmada(p)
    }

    /** GET a un endpoint que responde `{ url }` → (url, status). */
    private suspend fun urlDeEndpoint(ruta: String): Pair<String?, Int> {
        val tk = token() ?: return null to 401
        return try {
            val resp = http.get("${Supabase.SITE_URL}$ruta") { header("Authorization", "Bearer $tk") }
            val st = resp.status.value
            if (st !in 200..299) null to st else urlDeRespuesta(resp.bodyAsText()) to st
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null to 0 }
    }

    internal fun urlDeRespuesta(cuerpo: String): String? =
        (runCatching { json.parseToJsonElement(cuerpo).jsonObject["url"] }.getOrNull() as? JsonPrimitive)
            ?.content?.takeIf { it.startsWith("http") }

    suspend fun urlDeFoto(f: FotoPsico): String? = urlDeArchivo(f.id, f.path, f.url)
    suspend fun urlDeInformePdf(d: DocumentoInformePsico): String? = urlDeArchivo(d.id, d.path, d.url)

    // ── Lecturas ─────────────────────────────────────────────────────────────

    /** GET: todo el espacio de trabajo. Uno de [tratamientoId] o [citaId]. */
    suspend fun cargar(tratamientoId: String? = null, citaId: String? = null): Carga {
        val tk = token() ?: return Carga.Error("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401)
        return try {
            val resp = http.get("${Supabase.SITE_URL}$BASE") {
                header("Authorization", "Bearer $tk")
                if (tratamientoId != null) parameter("tratamientoId", tratamientoId) else if (citaId != null) parameter("citaId", citaId)
            }
            aCarga(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            Carga.Error(MSJ_SIN_RED, "SIN_RED", sinRed = true)
        }
    }

    /** POST abrir: crea la evaluación si no existe (idempotente) y responde lo mismo que el GET. */
    suspend fun abrir(tratamientoId: String): Carga {
        val tk = token() ?: return Carga.Error("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401)
        return try {
            val resp = http.post("${Supabase.SITE_URL}$BASE/abrir") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("tratamientoId", tratamientoId) }.toString())
            }
            aCarga(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            Carga.Error(MSJ_SIN_RED, "SIN_RED", sinRed = true)
        }
    }

    internal fun aCarga(status: Int, cuerpo: String?): Carga {
        val r = AtencionRepo.resultadoDeRespuesta(status, cuerpo)
        if (!r.registrada) {
            val rz = r.rechazo
            return Carga.Error(mensajeErrorPsico(status, rz?.codigo, rz?.error), rz?.codigo, status)
        }
        val espacio = parsearEspacioPsico(r.cuerpo)
            ?: return Carga.Error("No se pudo leer la evaluación.", "RESPUESTA_INVALIDA", status)
        return Carga.Ok(espacio)
    }

    /** (usuario+clínica, cuándo, tests). El catálogo cambia poco: 10 min en memoria. */
    private var catalogoCache: Triple<String, Long, List<TestCatalogoPsico>>? = null
    private const val VIGENCIA_CATALOGO_MS = 10L * 60 * 1000

    /** Catálogo de tests (global de Sania + propios de la clínica). null = falló. */
    suspend fun catalogo(forzar: Boolean = false): List<TestCatalogoPsico>? {
        val clave = "${Supabase.client.auth.currentUserOrNull()?.id.orEmpty()}|${StaffContextoRepo.actual?.clinicaId.orEmpty()}"
        val ahora = Clock.System.now().toEpochMilliseconds()
        if (!forzar) catalogoCache?.takeIf { it.first == clave && ahora - it.second < VIGENCIA_CATALOGO_MS }?.let { return it.third }
        val tk = token() ?: return null
        return try {
            val resp = http.get("${Supabase.SITE_URL}$BASE/catalogo") { header("Authorization", "Bearer $tk") }
            if (resp.status.value !in 200..299) null
            else parsearCatalogoPsico(runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull())
                .also { catalogoCache = Triple(clave, ahora, it) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    /** HTML imprimible del informe (borrador con marca de agua o emitido). null si no se pudo. */
    suspend fun htmlInforme(informeId: String): String? {
        val tk = token() ?: return null
        return try {
            val resp = http.get("${Supabase.SITE_URL}$BASE/informe/html") {
                header("Authorization", "Bearer $tk")
                parameter("informeId", informeId)
            }
            if (resp.status.value in 200..299) resp.bodyAsText() else null
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    // ── Escrituras ───────────────────────────────────────────────────────────

    /** Guarda UN componente completo ([campo]: entrevista|fuentes|observacion|analisis|plan|diagnosticos). */
    suspend fun guardar(evaluacionId: String, campo: String, datos: JsonElement): ResultadoEscritura =
        postJson("$BASE/guardar", buildJsonObject {
            put("evaluacionId", evaluacionId)
            put("campo", campo)
            put("datos", datos)
        })

    suspend fun crearTestCatalogo(nombreCorto: String, nombre: String, escalas: List<String>, generaImagen: Boolean): ResultadoEscritura =
        postJson("$BASE/catalogo", buildJsonObject {
            put("nombreCorto", nombreCorto)
            put("nombre", nombre)
            put("categoria", "Propios de la clínica")
            putJsonArray("poblacion") { }
            put("tipoPuntaje", "directo")
            put("generaImagen", generaImagen)
            putJsonArray("escalas") { escalas.forEach { add(JsonPrimitive(it)) } }
        }).also { if (it.registrada) catalogoCache = null }

    /** Agrega un test aplicado (precarga escalas, fecha de hoy y edad). No idempotente: lleva clave. */
    suspend fun crearTest(evaluacionId: String, testId: String): ResultadoEscritura =
        postJson("$BASE/test", buildJsonObject {
            put("accion", "crear")
            put("evaluacionId", evaluacionId)
            put("testId", testId)
            put("idempotency_key", nuevaIdemKey())
        })

    suspend fun editarTest(testAplicadoId: String, datos: JsonObject): ResultadoEscritura =
        postJson("$BASE/test", buildJsonObject {
            put("accion", "editar")
            put("testAplicadoId", testAplicadoId)
            put("datos", datos)
        })

    suspend fun borrarTest(testAplicadoId: String): ResultadoEscritura =
        postJson("$BASE/test", buildJsonObject {
            put("accion", "borrar")
            put("testAplicadoId", testAplicadoId)
        })

    /**
     * Sube una foto o PDF (material protegido). [uso]: test (con [testAplicadoId]),
     * genograma o fuente. `cuerpo.foto` = la foto registrada.
     */
    suspend fun subirFoto(
        evaluacionId: String, bytes: ByteArray, nombre: String, mime: String?, uso: String, testAplicadoId: String?,
    ): ResultadoEscritura {
        val tk = token() ?: return sinSesion
        return try {
            val resp = http.post("${Supabase.SITE_URL}$BASE/foto") {
                header("Authorization", "Bearer $tk")
                setBody(MultiPartFormDataContent(formData {
                    append("evaluacionId", evaluacionId)
                    append("uso", uso)
                    if (testAplicadoId != null) append("testAplicadoId", testAplicadoId)
                    append("nombre", nombre)
                    append("archivo", bytes, Headers.build {
                        append(HttpHeaders.ContentType, mime ?: "application/octet-stream")
                        append(HttpHeaders.ContentDisposition, "filename=\"${nombre.replace("\"", "")}\"")
                    })
                }))
            }
            AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: CancellationException) { throw e } catch (_: Exception) { sinRed }
    }

    suspend fun borrarFoto(documentoId: String): ResultadoEscritura =
        postJson("$BASE/foto/borrar", buildJsonObject { put("documentoId", documentoId) })

    /** Arma (o vuelve a armar, pisando lo editado) el borrador del informe. */
    suspend fun armarInforme(evaluacionId: String): ResultadoEscritura =
        postJson("$BASE/informe", buildJsonObject { put("accion", "armar"); put("evaluacionId", evaluacionId) })

    suspend fun guardarInforme(informeId: String, contenido: ContenidoInformePsico): ResultadoEscritura =
        postJson("$BASE/informe", buildJsonObject {
            put("accion", "guardar")
            put("informeId", informeId)
            put("contenido", jsonContenidoInforme(contenido))
        })

    /** Congela el informe, genera el PDF y lo deja en los documentos del paciente. */
    suspend fun emitirInforme(informeId: String): ResultadoEscritura =
        postJson("$BASE/informe", buildJsonObject {
            put("accion", "emitir")
            put("informeId", informeId)
            put("idempotency_key", nuevaIdemKey())
        })

    /** "+ Agregar cita de evaluación": suma citas SIN cambiar el precio. `cuerpo.totalSesiones`. */
    suspend fun agregarCita(tratamientoId: String, cantidad: Int = 1): ResultadoEscritura =
        postJson("$BASE/agregar-cita", buildJsonObject {
            put("tratamientoId", tratamientoId)
            put("cantidad", cantidad.coerceIn(1, 20))
            put("idempotency_key", nuevaIdemKey())
        })

    /** Ata el tratamiento creado con el plan ("✓ Tratamiento creado") y copia los objetivos. */
    suspend fun vincularPlan(evaluacionId: String, tratamientoId: String): ResultadoEscritura =
        postJson("$BASE/plan-tratamiento", buildJsonObject {
            put("evaluacionId", evaluacionId)
            put("tratamientoId", tratamientoId)
        })

    /**
     * Crea el tratamiento por el camino de SIEMPRE (`/api/staff/tratamiento/accion`,
     * `accion: crear`), pero directo y leyendo el `id` que responde: hace falta
     * para atarlo al plan. Sin señal no se encola (no habría id que atar).
     */
    suspend fun crearTratamientoConId(cuerpo: JsonObject): ResultadoEscritura =
        postJson("/api/staff/tratamiento/accion", JsonObject(cuerpo + ("idempotency_key" to JsonPrimitive(nuevaIdemKey()))))

    private suspend fun postJson(endpoint: String, cuerpo: JsonObject): ResultadoEscritura {
        val tk = token() ?: return sinSesion
        return try {
            val resp = http.post("${Supabase.SITE_URL}$endpoint") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(cuerpo.toString())
            }
            AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: CancellationException) { throw e } catch (_: Exception) { sinRed }
    }
}

/** El texto para el usuario de un rechazo de estos endpoints. */
fun ResultadoEscritura.mensajePsico(): String {
    val r = rechazo ?: return "No se pudo completar."
    return mensajeErrorPsico(r.status, r.codigo, r.error)
}

/** Para los tests: el JSON tal cual a objeto (o null). */
internal fun jsonObjetoPsico(texto: String): JsonObject? =
    runCatching { Json.parseToJsonElement(texto) as? JsonObject }.getOrNull()
