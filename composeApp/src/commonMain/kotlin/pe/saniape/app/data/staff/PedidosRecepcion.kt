package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEnvio
import kotlin.math.floor

// ─────────────────────────────────────────────────────────────────────────────
// 🧾 PEDIDO PARA RECEPCIÓN — GEMELO de lib/pedidos-recepcion.ts (web 2026-10-16).
// El profesional arma en la atención un pedido de PRODUCTOS / KITS y SERVICIOS
// EXTRA; a recepción le llega pendiente de cobro y lo cobra (productos → venta;
// cada servicio → tratamiento "Sesión suelta" con su pago). Toda la regla vive en
// el servidor (/api/staff/pedido/*): aquí solo lo que la pantalla muestra y la
// huella (djb2) con la que el cobro detecta que el pedido cambió.
// Base sin la migración: GET responde `disponible: false` y la app esconde todo.
// ─────────────────────────────────────────────────────────────────────────────

/** Una línea del pedido (así se guarda en `pedidos_recepcion.items`). */
data class ItemPedido(
    val clave: String,
    /** "producto" | "kit" | "servicio". */
    val tipo: String,
    val refId: String,
    val nombre: String,
    val cantidad: Int,
    /** Precio ACORDADO por unidad (o por atención, en un servicio). */
    val precioUnit: Double,
    /** Precio de lista del catálogo (lo pone el servidor). null = sin lista. */
    val precioLista: Double?,
    val notas: String?,
)

/** La cita de la atención (para cobrarla junto con el pedido si sigue sin pagar). */
data class CitaPedido(
    val id: String,
    val fecha: String?,
    val tipo: String?,
    val estado: String?,
    val costo: Double?,
    val pagadaAt: String?,
)

data class PedidoRecepcion(
    val id: String,
    val pacienteId: String,
    val citaId: String?,
    val sedeId: String?,
    val moneda: String,
    val terapeutaId: String?,
    val items: List<ItemPedido>,
    val total: Double,
    val notas: String?,
    /** "Pendiente" | "Cobrado" | "Anulado". */
    val estado: String,
    val pacienteNombre: String?,
    val terapeutaNombre: String?,
    val cita: CitaPedido?,
)

data class OpcionCatalogoPedido(val id: String, val nombre: String, val precio: Double?, val extra: String? = null)

/** Lo que se puede pedir (GET /api/staff/pedido/catalogo). */
data class CatalogoPedido(
    val moneda: String,
    /** La sede cobra en otra moneda que el catálogo: el precio es obligatorio. */
    val sinLista: Boolean,
    val productos: List<OpcionCatalogoPedido>,
    val kits: List<OpcionCatalogoPedido>,
    val servicios: List<OpcionCatalogoPedido>,
)

/** Gemelos de los CHECK de la migración (LIMITES_PEDIDO). */
object LimitesPedido {
    const val ITEMS = 40
    const val CANTIDAD_MAX = 999
    const val NOTAS_ITEM = 200
    const val NOTAS = 500
}

/** Math.round de JS (mitad hacia arriba): Kotlin redondea al par. */
private fun redondeoMitadArriba(x: Double): Double = floor(x + 0.5)

/** Subtotal de una línea, redondeado a la moneda. */
fun subtotalItemPedido(cantidad: Int, precioUnit: Double, moneda: String?): Double = redondearPrecio(cantidad * precioUnit, moneda)

data class TotalesPedido(val productos: Double, val servicios: Double, val total: Double)

/** Totales del pedido: productos (venta) y servicios (tratamientos) por separado. */
fun totalesPedido(items: List<Triple<String, Int, Double>>, moneda: String?): TotalesPedido {
    var productos = 0.0
    var servicios = 0.0
    for ((tipo, cantidad, precio) in items) {
        val s = subtotalItemPedido(cantidad, precio, moneda)
        if (tipo == "servicio") servicios += s else productos += s
    }
    productos = redondearPrecio(productos, moneda)
    servicios = redondearPrecio(servicios, moneda)
    return TotalesPedido(productos, servicios, redondearPrecio(productos + servicios, moneda))
}

fun totalesPedidoDe(items: List<ItemPedido>, moneda: String?): TotalesPedido =
    totalesPedido(items.map { Triple(it.tipo, it.cantidad, it.precioUnit) }, moneda)

/** "2 productos · 1 servicio" — la línea corta del chip y del aviso. */
fun resumenPedido(items: List<ItemPedido>): String {
    val prod = items.filter { it.tipo != "servicio" }.sumOf { it.cantidad }
    val serv = items.count { it.tipo == "servicio" }
    val partes = mutableListOf<String>()
    if (prod > 0) partes += "$prod ${if (prod == 1) "producto" else "productos"}"
    if (serv > 0) partes += "$serv ${if (serv == 1) "servicio" else "servicios"}"
    return partes.joinToString(" · ").ifEmpty { "Sin ítems" }
}

/** (Math.round(x * 100) / 100).toFixed(2) de JS. */
private fun dosDecimales(x: Double): String {
    val cent = redondeoMitadArriba(x * 100).toLong()
    val neg = cent < 0
    val a = kotlin.math.abs(cent)
    return (if (neg) "-" else "") + "${a / 100}.${(a % 100).toString().padStart(2, '0')}"
}

/**
 * Huella de lo que recepción VIO al cobrar (ítems, cantidades y precios), gemela
 * de `huellaPedido`: djb2 sobre un texto canónico. Si el profesional cambió el
 * pedido entre que se abrió el cobro y se tocó "Cobrar", el servidor responde 409
 * PEDIDO_CAMBIO en vez de cobrar otro total.
 */
fun huellaPedido(items: List<ItemPedido>): String {
    val texto = items.joinToString(";") { i ->
        listOf(i.clave, i.tipo, i.refId, redondeoMitadArriba(i.cantidad.toDouble()).toLong().toString(), dosDecimales(i.precioUnit)).joinToString("|")
    }
    var h = 5381L
    for (ch in texto) h = ((h * 33) and 0xFFFFFFFFL) xor ch.code.toLong() and 0xFFFFFFFFL
    return "${items.size}-${h.toString(16)}"
}

/** ¿Se puede editar / cobrar / anular? (la base también lo hace cumplir). */
fun puedeTocarPedido(estado: String?): Boolean = estado == "Pendiente"

/** El monto de la cita que se puede cobrar con el pedido (consulta/evaluación con precio y sin pagar); 0 = no. */
fun citaCobrableConPedido(p: PedidoRecepcion): Double {
    val c = p.cita ?: return 0.0
    if (c.tipo == "Sesión" || c.estado == "Cancelada" || c.pagadaAt != null) return 0.0
    return (c.costo ?: 0.0).takeIf { it > 0 } ?: 0.0
}

// ── Lectura de la API ──

private fun JsonObject.t(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
private fun JsonObject.n(k: String): Double? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

private val TIPOS_PEDIDO = setOf("producto", "kit", "servicio")

/** Ítems guardados (jsonb) → tipados, tolerando filas viejas o rotas (gemelo de `itemsDeFila`). */
fun itemsPedidoDeJson(raw: Any?): List<ItemPedido> {
    val arr = raw as? JsonArray ?: return emptyList()
    return arr.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val tipo = o.t("tipo")?.takeIf { it in TIPOS_PEDIDO } ?: return@mapNotNull null
        ItemPedido(
            clave = o.t("clave") ?: return@mapNotNull null,
            tipo = tipo,
            refId = o.t("ref_id") ?: return@mapNotNull null,
            nombre = o.t("nombre").orEmpty(),
            cantidad = (o.n("cantidad") ?: 1.0).toInt().takeIf { it > 0 } ?: 1,
            precioUnit = o.n("precio_unit") ?: 0.0,
            precioLista = o.n("precio_lista"),
            notas = o.t("notas"),
        )
    }
}

fun pedidoDeJson(o: JsonObject): PedidoRecepcion? {
    val id = o.t("id") ?: return null
    val pac = o["paciente"] as? JsonObject
    val ter = o["terapeuta"] as? JsonObject
    val cita = o["cita"] as? JsonObject
    return PedidoRecepcion(
        id = id,
        pacienteId = o.t("paciente_id").orEmpty(),
        citaId = o.t("cita_id"),
        sedeId = o.t("sede_id"),
        moneda = o.t("moneda")?.let(::normalizarMoneda) ?: "PEN",
        terapeutaId = o.t("terapeuta_id"),
        items = itemsPedidoDeJson(o["items"]),
        total = o.n("total") ?: 0.0,
        notas = o.t("notas")?.takeIf { it.isNotBlank() },
        estado = o.t("estado") ?: "Pendiente",
        pacienteNombre = pac?.t("nombre"),
        terapeutaNombre = ter?.t("nombre"),
        cita = cita?.let { c ->
            c.t("id")?.let { cid -> CitaPedido(cid, c.t("fecha"), c.t("tipo"), c.t("estado"), c.n("costo"), c.t("pagada_at")) }
        },
    )
}

fun catalogoPedidoDeJson(o: JsonObject): CatalogoPedido {
    fun lista(k: String, extra: (JsonObject) -> String? = { null }) = (o[k] as? JsonArray).orEmpty().mapNotNull { e ->
        val x = e as? JsonObject ?: return@mapNotNull null
        OpcionCatalogoPedido(x.t("id") ?: return@mapNotNull null, x.t("nombre").orEmpty(), x.n("precio"), extra(x))
    }
    return CatalogoPedido(
        moneda = o.t("moneda")?.let(::normalizarMoneda) ?: "PEN",
        sinLista = (o["sinLista"] as? JsonPrimitive)?.booleanOrNull == true,
        productos = lista("productos") { x -> (x.n("stock") ?: 0.0).let { if (it <= 0) "Sin stock" else "Stock ${it.toLong()}" } },
        kits = lista("kits") { "Kit" },
        servicios = lista("servicios"),
    )
}

/** Una línea del formulario (precio escrito como texto: "" = el de lista). */
data class LineaPedidoForm(
    val clave: String?,
    val tipo: String,
    val refId: String,
    val nombre: String,
    val cantidad: Int,
    val precio: String,
    val lista: Double?,
    val notas: String,
)

/** El cuerpo de POST /api/staff/pedido/guardar (sin la clave de idempotencia). */
fun cuerpoGuardarPedido(id: String?, citaId: String?, pacienteId: String?, lineas: List<LineaPedidoForm>, notas: String): JsonObject = buildJsonObject {
    if (id != null) put("id", id) else {
        if (citaId != null) put("citaId", citaId) else put("citaId", JsonNull)
        if (pacienteId != null) put("pacienteId", pacienteId) else put("pacienteId", JsonNull)
    }
    put("items", buildJsonArray {
        lineas.forEach { l ->
            add(buildJsonObject {
                put("tipo", l.tipo); put("ref_id", l.refId); put("cantidad", l.cantidad)
                val p = l.precio.trim()
                if (p.isEmpty()) put("precio_unit", JsonNull) else put("precio_unit", p.replace(',', '.'))
                val nl = l.notas.trim()
                if (nl.isNotEmpty()) put("notas", nl) else put("notas", JsonNull)
                if (l.clave != null) put("clave", l.clave) else put("clave", JsonNull)
            })
        }
    })
    val n = notas.trim()
    if (n.isNotEmpty()) put("notas", n) else put("notas", JsonNull)
}

/** Lista de pendientes + si la función existe en esta base. */
data class PedidosPendientes(val pedidos: List<PedidoRecepcion>, val total: Int, val disponible: Boolean) {
    /** Por cita: el chip "🧾 Pedido pendiente" de cada tarjeta. */
    val porCita: Map<String, List<PedidoRecepcion>> get() = pedidos.filter { it.citaId != null }.groupBy { it.citaId!! }
}

object PedidosRepo {
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** La base dijo "no hay tabla": no se vuelve a preguntar en esta sesión. */
    var noDisponible = false
        private set

    private suspend fun leer(params: Map<String, String>, ruta: String = "/api/staff/pedido"): JsonObject? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        return try {
            val r = http.get("${Supabase.SITE_URL}$ruta") {
                header("Authorization", "Bearer $tk")
                params.forEach { (k, v) -> parameter(k, v) }
            }
            if (!r.status.isSuccess()) return null
            json.parseToJsonElement(r.bodyAsText()) as? JsonObject
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Pedidos Pendientes (lo que la RLS deja ver). null = sin red / sin permiso (la agenda sigue igual). */
    suspend fun pendientes(): PedidosPendientes? {
        if (noDisponible) return PedidosPendientes(emptyList(), 0, false)
        val o = leer(mapOf("estado" to "Pendiente")) ?: return null
        val disponible = (o["disponible"] as? JsonPrimitive)?.booleanOrNull != false
        if (!disponible) noDisponible = true
        val lista = (o["pedidos"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::pedidoDeJson) }
        val total = (o["total"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() ?: lista.size
        return PedidosPendientes(lista, maxOf(total, lista.size), disponible)
    }

    /** Un pedido por id (el cobro lo relee si cambió). null = no está o no se pudo leer. */
    suspend fun porId(id: String): PedidoRecepcion? =
        (leer(mapOf("id" to id))?.get("pedidos") as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.let(::pedidoDeJson) }

    /** El catálogo para la sede de la cita. null = no se pudo leer. */
    suspend fun catalogo(citaId: String?): CatalogoPedido? =
        leer(citaId?.let { mapOf("citaId" to it) } ?: emptyMap(), "/api/staff/pedido/catalogo")?.let(::catalogoPedidoDeJson)

    /** Resultado de una escritura: ok, el cuerpo y el rechazo (con su código). */
    data class Envio(val ok: Boolean, val sinRed: Boolean, val cuerpo: JsonObject?, val rechazo: RechazoServidor?)

    private suspend fun enviar(ruta: String, cuerpo: JsonObject, idemKey: String): Envio {
        val (res, rechazo, resp) = runCatching {
            pe.saniape.app.data.offline.Sincronizador.enviarAhoraCompleto(ruta, cuerpo, idemKey)
        }.getOrElse {
            if (it is kotlin.coroutines.cancellation.CancellationException) throw it
            Triple(ResultadoEnvio.SIN_RED, null, null)
        }
        return Envio(res == ResultadoEnvio.OK, res == ResultadoEnvio.SIN_RED, resp, rechazo)
    }

    /** Crea o edita (solo Pendiente). DIRECTO: el profesional tiene que ver que llegó. */
    suspend fun guardar(cuerpo: JsonObject, idemKey: String): Envio = enviar("/api/staff/pedido/guardar", cuerpo, idemKey)

    /** Cobra el pedido con UN medio de pago y la huella de lo que se vio. */
    suspend fun cobrar(id: String, metodo: String, huella: String, idemKey: String): Envio =
        enviar("/api/staff/pedido/cobrar", buildJsonObject { put("id", id); put("metodo", metodo); put("huella", huella) }, idemKey)

    /** Anula un pedido que NO se cobró. */
    suspend fun anular(id: String, motivo: String): Envio =
        enviar("/api/staff/pedido/anular", buildJsonObject { put("id", id); put("motivo", motivo) }, pe.saniape.app.data.offline.nuevaIdemKey())

    /** Cobra la cita de la atención junto con el pedido (idempotente en el servidor). */
    suspend fun cobrarCita(citaId: String, metodo: String, idemKey: String): Envio =
        enviar("/api/staff/cita/cobrar", buildJsonObject { put("citaId", citaId); put("metodo", metodo); put("modo", "cobrar") }, idemKey)
}
