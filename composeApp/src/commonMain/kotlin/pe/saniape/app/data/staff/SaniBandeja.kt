package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

// LA BANDEJA DE SANI (el bot de WhatsApp) EN LA APP. Gemelo de app/(app)/mensajes
// web + lib/sani-bandeja.ts. Todo pasa por /api/staff/mensajes/* (proxy del
// servidor a LeadAI: la key nunca llega al teléfono). Sin websockets: sondeo suave
// (lista 20 s, conversación abierta 8 s), como la web.

/** Una conversación (fila de la bandeja; con `vista=bandeja` el resumen llega recortado). */
@Serializable
data class LeadSani(
    val id: String,
    val nombre: String? = null,
    val contactoExterno: String = "",
    val canalOrigen: String = "whatsapp",
    /** Vino de un anuncio Click-to-WhatsApp: su ventana es de 72 h, no 24. */
    val adsClickId: String? = null,
    /** 'historial' = chat viejo importado del celular de la clínica (coexistencia). */
    val origenEtiqueta: String? = null,
    val nivelInteres: String = "frio",
    /** nuevo | nutriendo | escalado | ganado | perdido */
    val estado: String = "nuevo",
    val resumenIA: String? = null,
    /** Solo en el detalle. */
    val borradorIA: String? = null,
    /** El bot deja de responder SOLO a esta conversación. */
    val botPausado: Boolean = false,
    val etiquetas: List<String> = emptyList(),
    val etapaEmbudo: String? = null,
    val asignadoA: String? = null,
    /** Solo en el detalle. */
    val nota: String? = null,
    val actualizadoEn: String = "",
) {
    val titulo: String get() = nombre?.takeIf { it.isNotBlank() } ?: contactoExterno
}

@Serializable
data class MensajeSani(
    val id: String,
    /** entrante | saliente */
    val direccion: String = "entrante",
    val contenido: String = "",
    val origen: String? = null,
    val creadoEn: String = "",
)

/** La conversación abierta. `totalMensajes` llega cuando se pidieron solo los últimos. */
data class DetalleLeadSani(val lead: LeadSani, val mensajes: List<MensajeSani>, val totalMensajes: Int?)

/** Etapa del recorrido, configurable por clínica (/api/staff/mensajes/contexto). */
@Serializable
data class EtapaSani(val id: String, val nombre: String = "", val color: String = "gray", val motor: String = "nuevo")

@Serializable
data class MiembroSani(val usuarioId: String, val email: String = "", val nombre: String? = null)

/** GET /api/staff/mensajes/resultados?mes=YYYY-MM — lo que Sani le trajo a la clínica en el mes. */
@Serializable
data class ResultadosSaniMes(
    val mes: String = "",
    val agendadas: Int = 0,
    val atendidas: Int = 0,
    val porAtender: Int = 0,
    val perdidas: Int = 0,
    val pacientesNuevos: Int = 0,
    /** null = quien mira no tiene pagos/finanzas: no se muestra. */
    val cobrado: Double? = null,
)

/** Una página de la bandeja y si LeadAI separó lo vivo del historial. */
data class PaginaBandeja(val items: List<LeadSani>, val siguienteCursor: String?, val separaHistorial: Boolean)

/** Lo vivo, el historial o todo (LeadAI anterior que no separa). Ver lib/sani-bandeja.ts. */
enum class TramoBandeja { VIVO, HISTORIAL, TODO }

sealed class RespuestaBandeja<out T> {
    data class Ok<T>(val dato: T) : RespuestaBandeja<T>()
    /** La clínica no tiene Sani activo (403 `leadai_inactivo`). */
    data object Inactivo : RespuestaBandeja<Nothing>()
    data class SinPermiso(val mensaje: String) : RespuestaBandeja<Nothing>()
    data class Error(val mensaje: String, val status: Int = 0) : RespuestaBandeja<Nothing>()
}

// ── Reglas puras (gemelas de la web) ────────────────────────────────────────

val ETAPAS_SANI_DEFAULT = listOf(
    EtapaSani("nuevo", "Nuevos", "navy", "nuevo"),
    EtapaSani("nutriendo", "En seguimiento", "amber", "nutriendo"),
    EtapaSani("escalado", "Necesitan tu ayuda", "red", "escalado"),
    EtapaSani("ganado", "Agendaron", "green", "ganado"),
    EtapaSani("perdido", "No agendaron", "gray", "perdido"),
)

/** Los nombres de fábrica de LeadAI (mundo de ventas) en palabras de clínica. */
fun nombreEtapaSani(e: EtapaSani): String = when (e.nombre) {
    "Ganados" -> "Agendaron"
    "Ganado" -> "Agendó"
    "Perdidos" -> "No agendaron"
    "Perdido" -> "No agendó"
    "Escalados" -> "Necesitan tu ayuda"
    "Escalado" -> "Necesita tu ayuda"
    else -> e.nombre
}

/** La etapa que se muestra: la personalizada si existe; si no, la que mapea al estado. */
fun etapaVisibleSani(l: LeadSani, etapas: List<EtapaSani>): EtapaSani =
    l.etapaEmbudo?.let { id -> etapas.firstOrNull { it.id == id } }
        ?: etapas.firstOrNull { it.motor == l.estado } ?: etapas.firstOrNull() ?: ETAPAS_SANI_DEFAULT.first()

/** Chat importado del celular de antes de Sani: va aparte, no cuenta en la bandeja. */
fun esHistorialSani(l: LeadSani): Boolean = l.origenEtiqueta == "historial"

/**
 * ¿Lo escribió Sani o una persona de la clínica? 'humano', 'ia_aprobada' e
 * 'ia_editada' pasaron por manos del equipo aunque la IA los redactara.
 */
fun esDelBot(origen: String?): Boolean = !(origen == "humano" || origen == "ia_aprobada" || origen == "ia_editada")

/**
 * Responder desde Sania sale por la API de WhatsApp y Meta lo cobra
 * (RESPONDER_DESDE_SANIA = false en la web, lib/mensajes-politica.ts): en
 * WhatsApp se redacta aquí y se envía desde el celular de la clínica. Instagram,
 * Messenger, TikTok y el chat externo sí se responden desde aquí.
 */
const val RESPONDER_DESDE_SANIA = false
const val MOTIVO_NO_RESPONDER =
    "Escribir desde aquí sale por la API de WhatsApp y Meta lo cobra. Responde desde el WhatsApp Business del celular de la clínica: es el mismo chat y no cuesta nada."

fun bloqueaRespuestaPorCanal(canal: String?): Boolean =
    !RESPONDER_DESDE_SANIA && !(canal == "instagram" || canal == "messenger" || canal == "tiktok" || canal == "externo")

fun nombreCanalSani(canal: String): String = when (canal) {
    "whatsapp" -> "WhatsApp"; "instagram" -> "Instagram"; "messenger" -> "Facebook"
    "tiktok" -> "TikTok"; "externo" -> "Manual"; else -> canal
}

fun iconoCanalSani(canal: String): String = when (canal) {
    "whatsapp" -> "💬"; "instagram" -> "📸"; "messenger" -> "💠"; "tiktok" -> "🎵"; else -> "✏️"
}

fun minutosDesde(iso: String, ahora: Instant = Clock.System.now()): Int =
    parsearInstante(iso)?.let { ((ahora - it).inWholeMinutes).toInt().coerceAtLeast(0) } ?: 0

fun haceTexto(min: Int): String = when {
    min < 1 -> "ahora"
    min < 60 -> "hace $min min"
    min < 60 * 24 -> "hace ${min / 60} h"
    else -> "hace ${min / 60 / 24} d"
}

/** La ventana de 24 h (72 h si vino de un anuncio) desde el último mensaje del paciente. */
data class VentanaSani(val abierta: Boolean, val restanMin: Int, val horas: Int)

fun ventanaSani(mensajes: List<MensajeSani>, vinoDeAnuncio: Boolean, ahora: Instant = Clock.System.now()): VentanaSani {
    val horas = if (vinoDeAnuncio) 72 else 24
    val ultimo = mensajes.filter { it.direccion == "entrante" }.mapNotNull { parsearInstante(it.creadoEn) }.maxOrNull()
        ?: return VentanaSani(false, 0, horas)
    val restanteMin = horas * 60L - (ahora - ultimo).inWholeMinutes
    return VentanaSani(restanteMin > 0, restanteMin.coerceAtLeast(0).toInt(), horas)
}

fun textoRestante(min: Int): String = if (min < 60) "$min min" else "${min / 60} h"

/** Lo fresco arriba (reemplaza su versión vieja); lo demás conserva su orden detrás. */
fun mezclarPaginaReciente(actuales: List<LeadSani>, recientes: List<LeadSani>): List<LeadSani> {
    val ids = recientes.map { it.id }.toSet()
    return recientes + actuales.filter { it.id !in ids }
}

/** Una página más vieja al final, sin duplicar. */
fun agregarPaginaVieja(actuales: List<LeadSani>, pagina: List<LeadSani>): List<LeadSani> {
    val ids = actuales.map { it.id }.toSet()
    return actuales + pagina.filter { it.id !in ids }
}

/** El aviso «🗂 N conversaciones anteriores en Historial» (mostrarAvisoHistorial). */
fun mostrarAvisoHistorial(historial: Int, vivas: Int, filtro: String, busqueda: String): Boolean =
    historial > 0 && filtro.isEmpty() && busqueda.isBlank() && vivas < 10

/** "2026-11" → "2026-10". */
fun mesAnteriorSani(mes: String): String {
    val a = mes.take(4).toIntOrNull() ?: return mes
    val m = mes.drop(5).take(2).toIntOrNull() ?: return mes
    return if (m == 1) "${a - 1}-12" else "$a-${(m - 1).toString().padStart(2, '0')}"
}

private val NOMBRES_MES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "setiembre", "octubre", "noviembre", "diciembre")
fun nombreMesSani(mes: String): String = NOMBRES_MES.getOrNull((mes.drop(5).take(2).toIntOrNull() ?: 1) - 1) ?: mes

private val jsonSani = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

internal fun parsearPaginaBandeja(cuerpo: String): PaginaBandeja {
    val o = jsonSani.parseToJsonElement(cuerpo) as? JsonObject ?: return PaginaBandeja(emptyList(), null, false)
    val items = (o["items"] as? JsonArray)?.mapNotNull { runCatching { jsonSani.decodeFromJsonElement(LeadSani.serializer(), it) }.getOrNull() }.orEmpty()
    val cursor = (o["siguienteCursor"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    val separa = (o["excluirOrigen"] as? JsonPrimitive)?.contentOrNull == "historial"
    return PaginaBandeja(items.filter { true }, cursor, separa)
}

internal fun parsearDetalleLead(cuerpo: String): DetalleLeadSani {
    val o = jsonSani.parseToJsonElement(cuerpo) as JsonObject
    val lead = jsonSani.decodeFromJsonElement(LeadSani.serializer(), o)
    val mensajes = (o["mensajes"] as? JsonArray)?.mapNotNull { runCatching { jsonSani.decodeFromJsonElement(MensajeSani.serializer(), it) }.getOrNull() }.orEmpty()
    val total = (o["totalMensajes"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    return DetalleLeadSani(lead, mensajes, total)
}

/**
 * Las llamadas a /api/staff/mensajes/… con el Bearer del staff. Los permisos los
 * vuelve a validar el servidor (`sani`/`marketing`, candado de solo lectura).
 */
object SaniBandejaRepo {
    private val http = crearHttpClient().config {
        install(HttpTimeout) {
            requestTimeoutMillis = 70_000
            socketTimeoutMillis = 70_000
            connectTimeoutMillis = 20_000
        }
    }
    private const val API = "/api/staff/mensajes"

    /** Mensajes que se bajan al abrir un chat; los anteriores, a pedido. */
    const val ULTIMOS_AL_ABRIR = 150

    private val cacheLeadai = mutableMapOf<String, Boolean>()

    private suspend fun token(): String? = runCatching { Supabase.client.auth.currentSessionOrNull()?.accessToken }.getOrNull()

    private suspend fun pedir(metodo: HttpMethod, ruta: String, cuerpo: JsonObject? = null): HttpResponse? {
        val tk = token() ?: return null
        return http.request("${Supabase.SITE_URL}$ruta") {
            method = metodo
            header("Authorization", "Bearer $tk")
            if (cuerpo != null) { contentType(ContentType.Application.Json); setBody(cuerpo.toString()) }
        }
    }

    private fun error(cuerpo: String?, generico: String): String = runCatching {
        ((jsonSani.parseToJsonElement(cuerpo ?: "") as? JsonObject)?.get("error") as? JsonPrimitive)?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: generico

    private suspend fun <T> leer(ruta: String, generico: String, metodo: HttpMethod = HttpMethod.Get, cuerpo: JsonObject? = null, parsear: (String) -> T): RespuestaBandeja<T> = try {
        val r = pedir(metodo, ruta, cuerpo) ?: return RespuestaBandeja.Error("Tu sesión venció: vuelve a entrar.", 401)
        val txt = runCatching { r.bodyAsText() }.getOrNull()
        when {
            r.status.value in 200..299 && txt != null -> RespuestaBandeja.Ok(parsear(txt))
            r.status.value == 403 && error(txt, "") == "leadai_inactivo" -> RespuestaBandeja.Inactivo
            r.status.value == 403 -> RespuestaBandeja.SinPermiso(error(txt, "No tienes acceso al módulo Mensajes."))
            r.status.value == 402 -> RespuestaBandeja.Error("Se agotó la cuota de IA de tu plan este mes", 402)
            else -> RespuestaBandeja.Error(error(txt, generico), r.status.value)
        }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        RespuestaBandeja.Error("Sin conexión: $generico")
    }

    /**
     * ¿La clínica tiene Sani (add-on LeadAI) activo? `configuracion.leadai_activo`,
     * la MISMA lectura que el menú de la web (useLeadaiActivo). Una vez por clínica.
     */
    suspend fun leadaiActivo(clinicaId: String): Boolean {
        cacheLeadai[clinicaId]?.let { return it }
        return try {
            val v = Supabase.client.postgrest["configuracion"]
                .select(Columns.list("valor")) { filter { eq("clave", "leadai_activo") } }
                .decodeList<JsonObject>().firstOrNull()?.get("valor")
            val activo = (v as? JsonPrimitive)?.contentOrNull == "true"
            cacheLeadai[clinicaId] = activo
            activo
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    fun limpiarCache() { cacheLeadai.clear() }

    /**
     * La ficha del paciente de esta conversación, si Sania guardó el vínculo: la
     * ficha que creó el bot (`pacientes.leadai_lead_id`) o, si no, el paciente de
     * una cita que agendó Sani (`citas.leadai_lead_id`). Lectura directa con la RLS
     * del staff (sin permiso para verlo, no aparece). (id, nombre) o null.
     */
    suspend fun pacienteVinculado(leadId: String): Pair<String, String>? = try {
        val p = Supabase.client.postgrest["pacientes"]
            .select(Columns.list("id, nombre")) { filter { eq("leadai_lead_id", leadId) }; limit(1) }
            .decodeList<JsonObject>().firstOrNull()
            ?: Supabase.client.postgrest["citas"]
                .select(Columns.raw("paciente:pacientes(id, nombre)")) { filter { eq("leadai_lead_id", leadId) }; limit(1) }
                .decodeList<JsonObject>().firstOrNull()?.get("paciente") as? JsonObject
        val id = (p?.get("id") as? JsonPrimitive)?.contentOrNull
        if (id == null) null else id to ((p["nombre"] as? JsonPrimitive)?.contentOrNull ?: "Paciente")
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Una página de la bandeja (parametrosPagina de la web). [sede] = multisede. */
    suspend fun pagina(tramo: TramoBandeja, cursor: String?, limite: Int, sede: String?): RespuestaBandeja<PaginaBandeja> {
        val q = buildString {
            append("limit=$limite&vista=bandeja")
            when (tramo) {
                TramoBandeja.VIVO -> append("&excluirOrigen=historial")
                TramoBandeja.HISTORIAL -> append("&origen=historial")
                TramoBandeja.TODO -> Unit
            }
            if (cursor != null) append("&cursor=${cursor.encodeURLParameter()}")
            if (!sede.isNullOrBlank()) append("&sede=${sede.encodeURLParameter()}")
        }
        return leer("$API/leads?$q", "no se pudo cargar la bandeja") { parsearPaginaBandeja(it) }
    }

    /** La conversación: los últimos [ultimos] mensajes (null = todos). */
    suspend fun detalle(id: String, ultimos: Int? = ULTIMOS_AL_ABRIR): RespuestaBandeja<DetalleLeadSani> =
        leer("$API/leads/$id${if (ultimos != null) "?ultimos=$ultimos" else ""}", "no se pudo abrir la conversación") { parsearDetalleLead(it) }

    /** Etapas del recorrido + equipo para asignar (una vez al abrir). */
    suspend fun contexto(): Pair<List<EtapaSani>, List<MiembroSani>>? =
        (leer("$API/contexto", "") { txt ->
            val o = jsonSani.parseToJsonElement(txt) as? JsonObject
            val etapas = (o?.get("etapas") as? JsonArray)?.mapNotNull { runCatching { jsonSani.decodeFromJsonElement(EtapaSani.serializer(), it) }.getOrNull() }.orEmpty()
            val miembros = (o?.get("miembros") as? JsonArray)?.mapNotNull { runCatching { jsonSani.decodeFromJsonElement(MiembroSani.serializer(), it) }.getOrNull() }.orEmpty()
            etapas to miembros
        } as? RespuestaBandeja.Ok)?.dato

    /** Cupo del plan: personas nuevas que atendió Sani este mes (usados, límite). null = sin dato. */
    suspend fun cupo(): Pair<Int, Int>? =
        (leer("$API/uso", "") { txt ->
            val c = (jsonSani.parseToJsonElement(txt) as? JsonObject)?.get("clientes") as? JsonObject
            val usados = (c?.get("usados") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0
            val limite = (c?.get("limite") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0
            usados to limite
        } as? RespuestaBandeja.Ok)?.dato?.takeIf { it.second > 0 }

    /** Lo que Sani trajo en el mes (franja «📈 Este mes, Sani te trajo…»). */
    suspend fun resultados(mes: String): ResultadosSaniMes? =
        (leer("$API/resultados?mes=$mes", "") { jsonSani.decodeFromString(ResultadosSaniMes.serializer(), it) } as? RespuestaBandeja.Ok)?.dato

    /**
     * Acción humana sobre la conversación (responder, sugerir_respuesta,
     * corregir_texto, mover_etapa, asignar, pausar_bot, activar_bot,
     * marcar_ganado, descartar). Devuelve el cuerpo (p. ej. el borrador sugerido).
     */
    suspend fun accion(id: String, tipo: String, extra: Map<String, JsonElement> = emptyMap()): RespuestaBandeja<JsonObject> =
        leer("$API/leads/$id/accion", "no se pudo completar la acción", HttpMethod.Post,
            JsonObject(mapOf("tipo" to JsonPrimitive(tipo)) + extra)) { txt ->
            runCatching { jsonSani.parseToJsonElement(txt) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
        }

    /** Etiquetas y/o nota privada de la ficha del contacto. */
    suspend fun ficha(id: String, etiquetas: List<String>? = null, nota: String? = null): RespuestaBandeja<Unit> =
        leer("$API/leads/$id/ficha", "no se pudo guardar", HttpMethod.Patch, buildJsonObject {
            if (etiquetas != null) put("etiquetas", JsonArray(etiquetas.map { JsonPrimitive(it) }))
            if (nota != null) put("nota", nota)
        }) { }

    /** El texto que devuelve sugerir/corregir (la web acepta las dos claves). */
    fun textoDe(o: JsonObject, vararg claves: String): String =
        claves.firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.contentOrNull?.takeIf { s -> s.isNotBlank() } }.orEmpty()
}
