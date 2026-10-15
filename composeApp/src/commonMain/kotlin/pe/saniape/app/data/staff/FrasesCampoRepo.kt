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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * Autocompletado que aprende, ABIERTO (contrato §5 de `docs/app-contrato-chips.md`):
 * `GET/POST /api/staff/frases` con el Bearer del staff. Lo puro (registro,
 * filtros, mezcla) vive en FrasesCampo.kt.
 *
 * LECTURA: los campos de UNA pantalla en UN viaje (`?campos=a,b`), al abrirla.
 * Caché en memoria por clínica (abrir y cerrar el formulario no vuelve a
 * pedir); [limpiar] la vacía al salir o al cambiar de clínica. Un backend sin
 * la ruta (404), un campo que no conoce (400) o sin red → sin chips, sin aviso:
 * el campo de texto de siempre.
 *
 * REGISTRO: DESPUÉS de guardar, en segundo plano ("dispara y olvida"): nunca
 * bloquea ni hace fallar el guardado, nunca muestra error. Sin señal se
 * descarta (no se encola, como [ChipsRepo]: solo alimenta sugerencias y lo
 * mismo se volverá a escribir). Quien llama lo hace UNA vez por guardado del
 * usuario (nunca desde la cola offline): un reintento de la cola no vuelve a
 * aprender. Además, el mismo pedido en la misma sesión viaja una sola vez.
 */
object FrasesCampoRepo {
    private const val RUTA = "/api/staff/frases"
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Copia inmutable que se reemplaza entera (lectura sin candado). La clave
     * lleva la CLÍNICA: lo de una nunca se sirve en otra.
     */
    @kotlin.concurrent.Volatile
    private var cache: Map<String, Map<String, RespuestaFrasesCampo>> = emptyMap()

    /** Pedidos ya enviados en esta sesión (doble toque, salir dos veces): no se repiten. */
    @kotlin.concurrent.Volatile
    private var enviados: Set<String> = emptySet()
    private const val MAX_ENVIADOS = 200

    private fun clinicaActual(): String = StaffContextoRepo.actual?.clinicaId.orEmpty()

    private fun token(): String? = runCatching { Supabase.client.auth.currentSessionOrNull()?.accessToken }.getOrNull()

    private fun clave(campos: List<String>, especialidadId: String?, rubro: String?) =
        "${clinicaActual()}|${campos.sorted().joinToString(",")}|${especialidadId.orEmpty()}|${rubro.orEmpty()}"

    /** Los campos que se pueden pedir: del registro, sin repetir, hasta 20. */
    private fun validos(campos: List<String>): List<String> =
        campos.distinct().filter { esCampoAprendible(it) }.take(MAX_CAMPOS_POR_LECTURA)

    /** Al cerrar sesión o cambiar de clínica (StaffContextoRepo.limpiar, onCambioClinica). */
    fun limpiar() {
        cache = emptyMap()
        enviados = emptySet()
    }

    /** Lo ya cargado para esa pantalla, sin viaje (null = aún no): pinta los chips sin parpadeo al reabrir. */
    fun enCache(campos: List<String>, especialidadId: String? = null, rubro: String? = null): Map<String, RespuestaFrasesCampo>? =
        cache[clave(validos(campos), especialidadId, rubro)]

    /**
     * Base + aprendido de los [campos] de una pantalla, en UN viaje. Nunca
     * lanza: si no se pudo, vacío (y no se guarda en caché: la próxima apertura
     * vuelve a probar). [rubro] elige la base del rubro (indicaciones).
     */
    suspend fun cargar(
        campos: List<String>, especialidadId: String? = null, rubro: String? = null,
    ): Map<String, RespuestaFrasesCampo> {
        val pedidos = validos(campos)
        if (pedidos.isEmpty()) return emptyMap()
        val k = clave(pedidos, especialidadId, rubro)
        cache[k]?.let { return it }
        val tk = token() ?: return emptyMap()
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                parameter("campos", pedidos.joinToString(","))
                if (esUuid(especialidadId)) parameter("especialidadId", especialidadId)
                if (!rubro.isNullOrBlank()) parameter("rubro", rubro)
            }
            // 404 (servidor sin la ruta), 400 (un campo que aún no conoce), 401/403: sin chips.
            if (resp.status.value !in 200..299) return emptyMap()
            val o = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull() ?: return emptyMap()
            val r = parsearFrasesCampos(o, pedidos)
            // Sin clínica conocida no se guarda (no se puede saber de quién es).
            if (r.isNotEmpty() && clinicaActual().isNotEmpty()) cache = cache + (k to r)
            r
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Lo registrado hace un momento: la próxima apertura de esa pantalla lo trae. */
    private fun invalidar(campo: String) {
        cache = cache.filterKeys { k -> k.split('|').getOrNull(1)?.split(',')?.contains(campo) != true }
    }

    /**
     * Aprende lo NUEVO de un campo guardado. Vuelve al instante; todo (filtrar,
     * el POST) corre en segundo plano y nunca lanza ni avisa. Llamar DESPUÉS de
     * guardar, una vez por guardado del usuario.
     *
     * [texto] = el campo tal como se guardó; [antes] = lo que tenía (al abrir,
     * o lo que puso la plantilla); [pacienteId] es obligatorio en psico.* (sin
     * él no se aprende); [especialidadId] = la del servicio/cita.
     */
    fun registrar(
        campo: String,
        texto: String?,
        antes: String? = null,
        especialidadId: String? = null,
        pacienteId: String? = null,
        nombrePaciente: String? = null,
    ) {
        if (texto.isNullOrBlank()) return
        // El servidor exige uno de estos permisos (y el candado de solo lectura): sin
        // ellos no se hace el viaje. Sin contexto aún (arranque), decide el servidor.
        val ctx = StaffContextoRepo.actual
        if (ctx != null && !(ctx.puede("sesiones") || ctx.puede("citas") || ctx.puede("pacientes"))) return
        scope.launch {
            runCatching {
                if (!debeRegistrarFrases(campo, texto, antes, pacienteId, nombrePaciente)) return@runCatching
                val cuerpo = cuerpoRegistrarFrases(campo, texto, antes, especialidadId, pacienteId, nombrePaciente)
                val marca = "${clinicaActual()}|$cuerpo"
                if (marca in enviados) return@runCatching
                enviados = (if (enviados.size >= MAX_ENVIADOS) emptySet() else enviados) + marca
                val tk = token()
                val ok = tk != null && try {
                    http.post("${Supabase.SITE_URL}$RUTA") {
                        header("Authorization", "Bearer $tk")
                        contentType(ContentType.Application.Json)
                        setBody(cuerpo.toString())
                    }.status.value in 200..299
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                if (ok) invalidar(campo)
                // Sin señal o rechazo (403 SOLO_LECTURA, 400…): se descarta en silencio,
                // y el mismo pedido puede volver a intentarse en otro guardado.
                else enviados = enviados - marca
            }.onFailure { if (it is CancellationException) throw it }
        }
    }
}
