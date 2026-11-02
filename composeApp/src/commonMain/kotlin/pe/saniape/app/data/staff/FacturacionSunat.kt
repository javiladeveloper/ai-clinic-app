package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

// BOLETA / FACTURA SUNAT DE UN COBRO (add-on «Facturación electrónica», vía Norac).
// Gemelo de components/facturacion/ComprobanteSunat.tsx + lib/facturacion-cliente.ts
// de la web. Toda la regla vive en el servidor (/api/staff/comprobante-electronico/*):
// montos y conceptos salen del cobro, el receptor lo valida `validarReceptor`, la
// idempotencia la pone el servidor. Aquí solo se pregunta y se muestra. Con la
// facturación activa, la boleta/factura REEMPLAZA al recibo interno (como la web).

/** Una boleta (03), factura (01) o nota de crédito (07) de un cobro. */
@Serializable
data class ComprobanteSunat(
    val id: String,
    /** "01" factura · "03" boleta · "07" nota de crédito. */
    val tipo: String,
    val numero: String? = null,
    /** pendiente | queued | sent | accepted | observed | rejected | error | revisar */
    val estado: String = "pendiente",
    val mensaje: String? = null,
    @SerialName("importe_total") val importeTotal: Double = 0.0,
    @SerialName("referencia_id") val referenciaId: String? = null,
    @SerialName("anulado_at") val anuladoAt: String? = null,
    @SerialName("origen_tipo") val origenTipo: String? = null,
    @SerialName("origen_id") val origenId: String? = null,
)

/** El receptor sugerido (datos del paciente), como lo arma `receptorDePaciente` en el servidor. */
@Serializable
data class ReceptorSunat(
    @SerialName("tipo_doc") val tipoDoc: String = "0",
    @SerialName("num_doc") val numDoc: String = "-",
    @SerialName("razon_social") val razonSocial: String = "",
    val email: String? = null,
)

/** El cobro canónico (pago mixto → su id menor; cita abonada → el pago): lo decide el servidor. */
@Serializable
data class OrigenSunat(val tipo: String, val id: String)

/** GET /api/staff/comprobante-electronico[?origenTipo&origenId]. */
@Serializable
data class DatosCobroSunat(
    val habilitada: Boolean = false,
    val motivo: String? = null,
    val mensaje: String? = null,
    val comprobantes: List<ComprobanteSunat> = emptyList(),
    val origen: OrigenSunat? = null,
    val total: Double = 0.0,
    val moneda: String? = null,
    val sugerido: ReceptorSunat? = null,
)

/** Ficha del RUC (GET …/ruc/[ruc]). */
@Serializable
data class FichaRuc(
    val ruc: String = "",
    @SerialName("razon_social") val razonSocial: String = "",
    val direccion: String = "",
    val ubigeo: String? = null,
    val estado: String? = null,
    val condicion: String? = null,
)

/** El enlace público del PDF y el mensaje listo para WhatsApp (GET …/[id]/enlace). */
@Serializable
data class EnlaceSunat(
    val url: String? = null,
    @SerialName("pdf_url") val pdfUrl: String? = null,
    val telefono: String? = null,
    val texto: String = "",
)

/** Respuesta de una lectura: el dato o el mensaje del servidor (en palabras de recepción). */
sealed class RespuestaSunat<out T> {
    data class Ok<T>(val dato: T) : RespuestaSunat<T>()
    data class Error(val mensaje: String, val codigo: String? = null) : RespuestaSunat<Nothing>()
}

// ── Reglas puras (gemelas de la web; se prueban sin red) ────────────────────

/** SUNAT: una boleta a un cliente sin documento no puede pasar de S/ 700. */
const val TOPE_BOLETA_SIN_DOCUMENTO = 700.0

/** `esVigente` de lib/facturacion-norac.ts: lo rechazado o fallido no cuenta. */
fun esVigenteSunat(estado: String): Boolean = estado != "rejected" && estado != "error"

/** La boleta/factura vigente de un cobro (la última no rechazada ni anulada), si hay. */
fun comprobanteVigente(filas: List<ComprobanteSunat>): ComprobanteSunat? =
    filas.lastOrNull { it.tipo != "07" && esVigenteSunat(it.estado) && it.anuladoAt == null }

/** La nota de crédito que anuló el comprobante (cuando ya no hay uno vigente). */
fun notaCreditoVigente(filas: List<ComprobanteSunat>): ComprobanteSunat? =
    if (comprobanteVigente(filas) != null) null
    else filas.lastOrNull { it.tipo == "07" && esVigenteSunat(it.estado) }

/** La última que SUNAT rechazó o no se pudo emitir (para decir por qué, al volver a emitir). */
fun ultimoRechazo(filas: List<ComprobanteSunat>): ComprobanteSunat? =
    if (comprobanteVigente(filas) != null) null
    else filas.lastOrNull { it.tipo != "07" && !esVigenteSunat(it.estado) }

fun nombreTipoSunat(tipo: String): String = when (tipo) {
    "01" -> "Factura"
    "03" -> "Boleta"
    "07" -> "Nota de crédito"
    else -> "Comprobante"
}

/** ETIQUETA_ESTADO de la web. */
fun etiquetaEstadoSunat(estado: String): String = when (estado) {
    "pendiente" -> "Enviando…"
    "queued" -> "En cola (SUNAT no respondió)"
    "sent" -> "Enviado"
    "accepted" -> "Aceptado por SUNAT"
    "observed" -> "Aceptado con observaciones"
    "rejected" -> "Rechazado por SUNAT"
    "error" -> "No se pudo emitir"
    "revisar" -> "Revisar en SUNAT antes de volver a emitir"
    else -> estado
}

/** El color del chip: verde aceptado, ámbar en camino, rojo rechazado (colorEstado de la web). */
enum class TonoSunat { OK, CAMINO, MAL, ANULADO }

fun tonoEstadoSunat(estado: String, anulado: Boolean = false): TonoSunat = when {
    anulado -> TonoSunat.ANULADO
    estado == "accepted" -> TonoSunat.OK
    estado in setOf("observed", "queued", "sent", "pendiente", "revisar") -> TonoSunat.CAMINO
    else -> TonoSunat.MAL
}

/** Aceptado (con o sin observaciones): ya tiene PDF oficial y se puede mandar. */
fun aceptadoSunat(estado: String): Boolean = estado == "accepted" || estado == "observed"

/** Se le puede preguntar a Norac cómo terminó (POST …/refrescar). */
fun consultableSunat(estado: String): Boolean = estado == "queued" || estado == "sent" || estado == "revisar"

/**
 * El aviso tras emitir o reintentar (gemelo de `avisarResultado` de la web):
 * (texto, es un error?).
 */
fun avisoResultadoSunat(c: ComprobanteSunat): Pair<String, Boolean> {
    val nombre = nombreTipoSunat(c.tipo)
    val num = c.numero.orEmpty()
    return when (c.estado) {
        "accepted" -> "$nombre $num aceptada por SUNAT".replace("  ", " ") to false
        "observed" -> "$nombre $num aceptada con observaciones".replace("  ", " ") to false
        "queued", "sent" -> "$nombre $num enviada: SUNAT todavía no responde, se actualiza sola".replace("  ", " ") to false
        "revisar" -> "$nombre $num: hay que revisarla en SUNAT. No se emite otra para no duplicarla".replace("  ", " ") to true
        "pendiente" -> "No hubo respuesta de la facturación. Toca «Reintentar»: no se duplicará" to true
        else -> "$nombre rechazada: ${c.mensaje?.takeIf { it.isNotBlank() } ?: "revisa los datos del cliente e intenta de nuevo"}" to true
    }
}

/** Tipos de documento de la boleta (DOCS de la web). */
val DOCUMENTOS_BOLETA: List<Pair<String, String>> = listOf(
    "1" to "DNI", "4" to "Carné de extranjería", "7" to "Pasaporte", "0" to "Sin documento",
)

/** Boleta sin documento por encima del tope: SUNAT pide el DNI. */
fun boletaSinDocExcede(tipoDoc: String, total: Double): Boolean = tipoDoc == "0" && total > TOPE_BOLETA_SIN_DOCUMENTO

/** ¿Se puede tocar «Emitir»? (`listo` de la web; el servidor valida igual con `validarReceptor`). */
fun receptorListo(
    tipo: String, tipoDoc: String, numDoc: String, nombre: String,
    ruc: String, razon: String, total: Double,
): Boolean = if (tipo == "01") {
    ruc.length == 11 && ruc.all { it.isDigit() } && razon.isNotBlank()
} else {
    nombre.isNotBlank() && (tipoDoc == "0" || numDoc.isNotBlank()) && !boletaSinDocExcede(tipoDoc, total)
}

/** El receptor que va en el POST (mismo armado que `emitir()` de la web). */
fun cuerpoReceptor(
    tipo: String, tipoDoc: String, numDoc: String, nombre: String, email: String?,
    ruc: String, razon: String, direccion: String, ubigeo: String,
): JsonObject = if (tipo == "01") buildJsonObject {
    put("tipo_doc", "6"); put("num_doc", ruc.trim()); put("razon_social", razon.trim())
    if (direccion.isNotBlank()) put("direccion", buildJsonObject {
        put("direccion", direccion.trim())
        if (ubigeo.length == 6 && ubigeo.all { it.isDigit() }) put("ubigeo", ubigeo)
    })
} else buildJsonObject {
    put("tipo_doc", tipoDoc)
    put("num_doc", if (tipoDoc == "0") "-" else numDoc.trim())
    put("razon_social", nombre.trim())
    if (!email.isNullOrBlank()) put("email", email)
}

/**
 * El cobro canónico de un pago en la ficha (gemelo de `origenSunat` de PagoCard):
 * un pago mixto (varias partes, mismo grupo) se ata a su id MENOR.
 */
fun origenSunatDePago(pagoId: String, grupoPagoId: String?, pagos: List<PagoFicha>): String =
    if (grupoPagoId == null) pagoId
    else pagos.filter { it.grupoPagoId == grupoPagoId }.map { it.id }.minOrNull() ?: pagoId

/**
 * ¿Esta fila de pago lleva botón/chip SUNAT? Solo la fila canónica, con dinero que
 * facturar, y no el consumo/uso de saldo a favor suelto (mismo filtro que PagoCard).
 */
fun pagoLlevaSunat(p: PagoFicha, pagos: List<PagoFicha>): Boolean =
    origenSunatDePago(p.id, p.grupoPagoId, pagos) == p.id && p.monto > 0 &&
        p.saldoTipo != SALDO_TIPO_CONSUMO && !(p.saldoTipo == SALDO_TIPO_USO && p.grupoPagoId == null)

private val jsonSunat = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

/** `{ "error": "…" }` del servidor, o el genérico. */
internal fun mensajeErrorSunat(cuerpo: String?, generico: String): Pair<String, String?> =
    runCatching {
        val o = jsonSunat.parseToJsonElement(cuerpo ?: "") as? JsonObject
        val e = (o?.get("error") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val c = (o?.get("codigo") as? JsonPrimitive)?.contentOrNull
        (e ?: generico) to c
    }.getOrNull() ?: (generico to null)

internal fun parsearDatosSunat(cuerpo: String): DatosCobroSunat =
    jsonSunat.decodeFromString(DatosCobroSunat.serializer(), cuerpo)

/** `{ comprobante }` de emitir/refrescar (también el 409 YA_EMITIDO lo trae). */
internal fun comprobanteDeRespuesta(cuerpo: String?): ComprobanteSunat? = runCatching {
    val o = jsonSunat.parseToJsonElement(cuerpo ?: "") as? JsonObject
    (o?.get("comprobante") as? JsonObject)?.let { jsonSunat.decodeFromJsonElement(ComprobanteSunat.serializer(), it) }
}.getOrNull()

/**
 * Las llamadas a /api/staff/comprobante-electronico/… con el Bearer del staff.
 * Cliente con esperas largas: emitir pasa por Norac (en Render puede tardar dos
 * intentos de 75 s; el servidor tiene maxDuration 300) y la consulta de un cobro
 * con comprobantes en cola re-pregunta a Norac al vuelo.
 */
object FacturacionSunatRepo {
    private val http = crearHttpClient().config {
        install(HttpTimeout) {
            requestTimeoutMillis = 310_000
            socketTimeoutMillis = 310_000
            connectTimeoutMillis = 20_000
        }
    }
    private const val API = "/api/staff/comprobante-electronico"

    /** clinicaId → ¿emite? Una consulta por clínica y sesión de la app (como el SWR de la web). */
    private val cacheHabilitada = mutableMapOf<String, Boolean>()

    fun limpiarCache() { cacheHabilitada.clear() }

    private suspend fun token(): String? = runCatching { Supabase.client.auth.currentSessionOrNull()?.accessToken }.getOrNull()

    private suspend fun pedir(metodo: HttpMethod, ruta: String, cuerpo: JsonObject? = null, params: Map<String, String> = emptyMap()): HttpResponse? {
        val tk = token() ?: return null
        return http.request("${Supabase.SITE_URL}$ruta") {
            method = metodo
            header("Authorization", "Bearer $tk")
            params.forEach { (k, v) -> parameter(k, v) }
            if (cuerpo != null) { contentType(ContentType.Application.Json); setBody(cuerpo.toString()) }
        }
    }

    private suspend fun <T> intentar(generico: String, bloque: suspend () -> RespuestaSunat<T>): RespuestaSunat<T> = try {
        bloque()
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        RespuestaSunat.Error("Sin conexión o el servidor tardó demasiado: $generico", "SIN_RED")
    }

    /**
     * ¿La clínica emite boletas/facturas? Sin params: solo el estado del add-on.
     * Cualquier fallo (sin red, sin permiso, servidor viejo) = false: se queda el
     * recibo interno de siempre. Solo se cachea una respuesta cierta.
     */
    suspend fun habilitada(clinicaId: String, forzar: Boolean = false): Boolean {
        if (!forzar) cacheHabilitada[clinicaId]?.let { return it }
        return try {
            val r = pedir(HttpMethod.Get, API) ?: return false
            if (r.status.value !in 200..299) return false
            val h = parsearDatosSunat(r.bodyAsText()).habilitada
            cacheHabilitada[clinicaId] = h
            h
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** Los comprobantes de UN cobro + su total, moneda y el cliente sugerido. */
    suspend fun datos(origenTipo: String, origenId: String): RespuestaSunat<DatosCobroSunat> =
        intentar("no se pudo consultar el comprobante") {
            val r = pedir(HttpMethod.Get, API, params = mapOf("origenTipo" to origenTipo, "origenId" to origenId))
                ?: return@intentar RespuestaSunat.Error("Tu sesión venció: vuelve a entrar.")
            val txt = runCatching { r.bodyAsText() }.getOrNull()
            if (r.status.value in 200..299 && txt != null) RespuestaSunat.Ok(parsearDatosSunat(txt))
            else mensajeErrorSunat(txt, "No se pudo consultar la facturación").let { (m, c) -> RespuestaSunat.Error(m, c) }
        }

    /**
     * Emite la boleta (03) o factura (01) de ESE cobro. El 409 YA_EMITIDO (dos
     * toques, dos teléfonos) trae el que ya existe: se trata como hecho (la web igual).
     */
    suspend fun emitir(origenTipo: String, origenId: String, tipo: String, receptor: JsonObject?): RespuestaSunat<ComprobanteSunat?> =
        intentar("no se supo si llegó. Vuelve a abrir el cobro antes de emitir otra vez") {
            val cuerpo = buildJsonObject {
                put("origenTipo", origenTipo); put("origenId", origenId); put("tipo", tipo)
                if (receptor != null) put("receptor", receptor)
            }
            val r = pedir(HttpMethod.Post, "$API/emitir", cuerpo)
                ?: return@intentar RespuestaSunat.Error("Tu sesión venció: vuelve a entrar.")
            val txt = runCatching { r.bodyAsText() }.getOrNull()
            val (msg, codigo) = mensajeErrorSunat(txt, "No se pudo emitir")
            if (r.status.value in 200..299 || codigo == "YA_EMITIDO") RespuestaSunat.Ok(comprobanteDeRespuesta(txt))
            else RespuestaSunat.Error(msg, codigo)
        }

    /** Reintenta uno 'pendiente' con la MISMA clave de idempotencia (nunca duplica). */
    suspend fun reintentar(comprobanteId: String): RespuestaSunat<ComprobanteSunat?> =
        intentar("no se pudo reintentar") {
            val r = pedir(HttpMethod.Post, "$API/emitir", buildJsonObject { put("comprobanteId", comprobanteId) })
                ?: return@intentar RespuestaSunat.Error("Tu sesión venció: vuelve a entrar.")
            val txt = runCatching { r.bodyAsText() }.getOrNull()
            if (r.status.value in 200..299) RespuestaSunat.Ok(comprobanteDeRespuesta(txt))
            else mensajeErrorSunat(txt, "No se pudo reintentar").let { (m, c) -> RespuestaSunat.Error(m, c) }
        }

    /** Pregunta a Norac cómo terminó uno en cola / enviado / por revisar. */
    suspend fun refrescar(comprobanteId: String): RespuestaSunat<ComprobanteSunat?> =
        intentar("no se pudo consultar a SUNAT") {
            val r = pedir(HttpMethod.Post, "$API/${comprobanteId.encodeURLPathPart()}/refrescar")
                ?: return@intentar RespuestaSunat.Error("Tu sesión venció: vuelve a entrar.")
            val txt = runCatching { r.bodyAsText() }.getOrNull()
            if (r.status.value in 200..299) RespuestaSunat.Ok(comprobanteDeRespuesta(txt))
            else mensajeErrorSunat(txt, "No se pudo consultar a SUNAT").let { (m, c) -> RespuestaSunat.Error(m, c) }
        }

    /** Razón social y dirección fiscal del RUC (ficha SUNAT vía Norac). */
    suspend fun ruc(ruc: String): RespuestaSunat<FichaRuc> =
        intentar("no se pudo consultar el RUC") {
            val r = pedir(HttpMethod.Get, "$API/ruc/${ruc.encodeURLPathPart()}")
                ?: return@intentar RespuestaSunat.Error("Tu sesión venció: vuelve a entrar.")
            val txt = runCatching { r.bodyAsText() }.getOrNull()
            if (r.status.value in 200..299 && txt != null) RespuestaSunat.Ok(jsonSunat.decodeFromString(FichaRuc.serializer(), txt))
            else mensajeErrorSunat(txt, "No se pudo consultar el RUC").let { (m, c) -> RespuestaSunat.Error(m, c) }
        }

    /** Enlace público del PDF + el mensaje para WhatsApp + el celular del paciente. */
    suspend fun enlace(comprobanteId: String): RespuestaSunat<EnlaceSunat> =
        intentar("no se pudo obtener el enlace") {
            val r = pedir(HttpMethod.Get, "$API/${comprobanteId.encodeURLPathPart()}/enlace")
                ?: return@intentar RespuestaSunat.Error("Tu sesión venció: vuelve a entrar.")
            val txt = runCatching { r.bodyAsText() }.getOrNull()
            if (r.status.value in 200..299 && txt != null) RespuestaSunat.Ok(jsonSunat.decodeFromString(EnlaceSunat.serializer(), txt))
            else mensajeErrorSunat(txt, "No se pudo obtener el enlace").let { (m, c) -> RespuestaSunat.Error(m, c) }
        }

    /** El PDF oficial (lo genera Norac). */
    suspend fun pdf(comprobanteId: String): RespuestaSunat<ByteArray> =
        intentar("no se pudo obtener el PDF") {
            val r = pedir(HttpMethod.Get, "$API/${comprobanteId.encodeURLPathPart()}/pdf")
                ?: return@intentar RespuestaSunat.Error("Tu sesión venció: vuelve a entrar.")
            if (r.status.value in 200..299) RespuestaSunat.Ok(r.readRawBytes())
            else mensajeErrorSunat(runCatching { r.bodyAsText() }.getOrNull(), "No se pudo obtener el PDF").let { (m, c) -> RespuestaSunat.Error(m, c) }
        }

    /**
     * Los comprobantes de los pagos de un tratamiento, en UNA lectura (gemelo del
     * SWR `comprobantes_sunat_pagos` de PagoCard). Lectura directa con la RLS del
     * staff (permiso pagos/finanzas, su clínica). null = no se pudo leer.
     */
    suspend fun dePagos(pagoIds: List<String>): List<ComprobanteSunat>? {
        val ids = pagoIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return emptyList()
        return try {
            Supabase.client.postgrest["comprobantes_electronicos"]
                .select(Columns.raw("id, tipo, numero, estado, mensaje, importe_total, referencia_id, anulado_at, origen_tipo, origen_id")) {
                    filter { eq("origen_tipo", "pago"); isIn("origen_id", ids) }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<JsonObject>()
                .mapNotNull { runCatching { jsonSunat.decodeFromJsonElement(ComprobanteSunat.serializer(), it) }.getOrNull() }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * El pago recién registrado (para ofrecer su boleta): `/api/staff/pago/registrar`
     * no devuelve el id del pago simple, así que se busca el más nuevo del
     * tratamiento con ESE monto (y esa sesión, si la hay), creado en los últimos
     * minutos. Si el pago quedó en la cola offline no aparece y no se ofrece nada
     * (nunca la boleta de OTRO pago). null = no encontrado.
     */
    suspend fun pagoRecien(tratamientoId: String, monto: Double, sesionId: String? = null): String? = try {
        val filas = Supabase.client.postgrest["pagos_tratamiento"]
            .select(Columns.raw("id, monto, created_at, sesion_id")) {
                filter {
                    eq("tratamiento_id", tratamientoId)
                    if (sesionId != null) eq("sesion_id", sesionId)
                }
                order("created_at", Order.DESCENDING)
                limit(5)
            }
            .decodeList<JsonObject>()
        val ahora = kotlinx.datetime.Clock.System.now()
        filas.firstOrNull { o ->
            val m = (o["monto"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return@firstOrNull false
            val creado = (o["created_at"] as? JsonPrimitive)?.contentOrNull?.let { parsearInstante(it) } ?: return@firstOrNull false
            kotlin.math.abs(m - monto) < 0.005 && (ahora - creado).inWholeSeconds in -120..600
        }?.let { (it["id"] as? JsonPrimitive)?.contentOrNull }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Los ids de pago de una respuesta con partes (`pagoIds`), si vinieron. */
    internal fun pagoIdsDe(cuerpo: JsonObject?): List<String> =
        (cuerpo?.get("pagoIds") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
}
