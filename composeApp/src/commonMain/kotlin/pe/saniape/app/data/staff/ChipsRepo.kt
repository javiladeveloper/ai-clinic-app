package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * Chips clínicos por especialidad (técnicas, diagnóstico, síntomas, patología)
 * contra el contrato de la web (`docs/app-contrato-chips.md`).
 *
 * LECTURA: `GET /api/staff/chips?campo=&especialidadId=` → propias de la clínica
 * + base de la especialidad + pool del rubro, ya mezclado. UNA vez por
 * (campo, especialidad) y por sesión de la app (caché en memoria): el
 * formulario lo pide al abrirse y al escribir solo filtra en memoria.
 * Si el endpoint no está (servidor viejo, dev), cae a las tablas de siempre.
 *
 * REGISTRO: RPC `registrar_chips` con la especialidad, en segundo plano. Nunca
 * bloquea ni hace fallar el guardado principal. Sin señal se descarta: no se
 * encola (es solo para sugerir, y lo mismo se volverá a escribir).
 */
object ChipsRepo {
    private val http = crearHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val cache = HashMap<String, List<ChipSugerido>>()

    private const val LIMITE = 250

    private fun clave(campo: String, especialidadId: String?) = "$campo|${especialidadId.orEmpty()}"

    /**
     * Chips de un campo para una especialidad (null = las activas de la clínica).
     * Nunca lanza: sin nada que sugerir devuelve vacío.
     */
    suspend fun cargar(campo: String, especialidadId: String?): List<ChipSugerido> {
        val k = clave(campo, especialidadId)
        mutex.withLock { cache[k] }?.let { return it }
        val delServidor = viaEndpoint(campo, especialidadId)
        if (delServidor != null) {
            mutex.withLock { cache[k] = delServidor }
            return delServidor
        }
        // Fallback (no se guarda en caché: la próxima apertura vuelve a probar el endpoint).
        return runCatching { propiasLegacy(campo, especialidadId) }.getOrDefault(emptyList())
    }

    /** Solo los textos, en orden (técnicas y diagnóstico). */
    suspend fun textos(campo: String, especialidadId: String?): List<String> =
        cargar(campo, especialidadId).map { it.texto }

    /** Lo registrado hace un momento: la próxima apertura del formulario lo trae. */
    suspend fun invalidar(campo: String) {
        mutex.withLock { cache.keys.removeAll { it.startsWith("$campo|") } }
    }

    private suspend fun viaEndpoint(campo: String, especialidadId: String?): List<ChipSugerido>? {
        val tk = runCatching { Supabase.client.auth.currentSessionOrNull()?.accessToken }.getOrNull() ?: return null
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/chips") {
                header("Authorization", "Bearer $tk")
                parameter("campo", campo)
                if (!especialidadId.isNullOrBlank()) parameter("especialidadId", especialidadId)
                parameter("limite", LIMITE)
            }
            if (resp.status.value !in 200..299) null else parsearChipsEndpoint(resp.bodyAsText())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Lo de siempre: lo propio de la clínica (esa especialidad + generales), más usado primero. */
    private suspend fun propiasLegacy(campo: String, especialidadId: String?): List<ChipSugerido> {
        val tabla = when (campo) {
            CampoChip.TECNICA -> "tecnicas_sesion"
            CampoChip.DIAGNOSTICO -> "diagnosticos_frecuentes"
            else -> return emptyList()
        }
        val filas = Supabase.client.postgrest[tabla]
            .select(Columns.list("nombre, usos, especialidad_id")) {
                order("usos", Order.DESCENDING)
                limit(400)
            }
            .decodeList<JsonObject>()
        fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
        val propias = filas
            .filter { especialidadId == null || it.str("especialidad_id").let { e -> e == null || e == especialidadId } }
            .mapNotNull { f -> f.str("nombre")?.let { ChipFila(it, f.str("usos")?.toDoubleOrNull() ?: 1.0) } }
        return combinarChips(propias = propias, limite = LIMITE)
    }

    /**
     * Suma un uso a cada texto (o lo crea) en lo propio de la clínica, con la
     * especialidad. En segundo plano: vuelve al instante y nunca lanza.
     * [textos] ya partidos (TecnicasNormalizar.partir / trocearDiagnostico).
     */
    fun registrar(campo: String, textos: List<String>, especialidadId: String?, nombrePaciente: String? = null) {
        val limpios = textosARegistrar(textos, campo, nombrePaciente)
        if (limpios.isEmpty()) return
        scope.launch {
            runCatching { registrarAhora(campo, limpios, especialidadId, nombrePaciente) }
            invalidar(campo)
        }
    }

    /** Técnicas tal como quedan en la sesión ("TENS + Compresa"): se parten con el normalizador. */
    fun registrarTecnicas(texto: String?, especialidadId: String?, nombrePaciente: String? = null) {
        if (texto.isNullOrBlank()) return
        registrar(CampoChip.TECNICA, TecnicasNormalizar.partir(texto), especialidadId, nombrePaciente)
    }

    /** Diagnóstico libre ("Lumbalgia, contractura"): se trocea por coma / ; / salto. */
    fun registrarDiagnostico(texto: String?, especialidadId: String?, nombrePaciente: String? = null) {
        if (texto.isNullOrBlank()) return
        registrar(CampoChip.DIAGNOSTICO, trocearDiagnostico(texto), especialidadId, nombrePaciente)
    }

    private suspend fun registrarAhora(campo: String, textos: List<String>, especialidadId: String?, nombrePaciente: String?) {
        try {
            Supabase.client.postgrest.rpc(
                "registrar_chips",
                buildJsonObject {
                    put("p_campo", campo)
                    putJsonArray("p_textos") { textos.forEach { add(it) } }
                    put("p_especialidad_id", especialidadId?.takeIf { it.isNotBlank() })
                    put("p_nombre_paciente", nombrePaciente?.takeIf { it.isNotBlank() })
                },
            )
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Base sin la migración (dev): el camino de siempre. Sin señal u otro
            // error: se descarta (no se encola; solo alimenta sugerencias).
            if (esFaltaFuncion(e.message)) registrarLegacy(campo, textos, especialidadId)
        }
    }

    /** Antes de la RPC: leer, sumar, escribir (por clave plegada). clinica_id por DEFAULT. */
    private suspend fun registrarLegacy(campo: String, textos: List<String>, especialidadId: String?) {
        val tabla = if (campo == CampoChip.TECNICA) "tecnicas_sesion" else "diagnosticos_frecuentes"
        val existentes = Supabase.client.postgrest[tabla]
            .select(Columns.list("id, nombre, usos"))
            .decodeList<JsonObject>()
        fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
        val porClave = existentes.associateBy { claveChip(it.str("nombre").orEmpty()) }
        for (nombre in textos) {
            val previa = porClave[claveChip(nombre)]
            val id = previa?.str("id")
            if (previa != null && id != null) {
                val usos = previa.str("usos")?.toIntOrNull() ?: 1
                Supabase.client.postgrest[tabla].update(buildJsonObject { put("usos", usos + 1) }) { filter { eq("id", id) } }
            } else {
                Supabase.client.postgrest[tabla].insert(buildJsonObject {
                    put("nombre", nombre)
                    if (!especialidadId.isNullOrBlank()) put("especialidad_id", especialidadId)
                })
            }
        }
    }
}
