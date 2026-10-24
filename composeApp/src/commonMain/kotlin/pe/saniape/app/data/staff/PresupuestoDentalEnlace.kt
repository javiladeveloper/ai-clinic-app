package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase

// ─────────────────────────────────────────────────────────────────────────────
// «🔗 Enlace para aceptar» del presupuesto dental: lo que se CONGELA en
// `presupuestos_dentales` y el WhatsApp que lo lleva. Gemelo de
// lib/presupuesto-dental.ts + `congelar` / `generarEnlace` de
// components/odontologia/PresupuestoPanel.tsx (web): la página pública
// /presupuesto/<token> (web) lo muestra y el paciente lo acepta desde ahí.
//
// Lo usa «Lo pensará · enviar enlace» de la evaluación dental (auditoría de
// flujos 2026-10-24, odonto O1): completar no crea tratamientos sin «✓ Acepta
// hoy»; si el paciente lo pensará, le llega este enlace.
//
// La app no edita fases ni alternativas (la web sí): cada línea va con su fase
// SUGERIDA y sin opción B, así que no hay elecciones que congelar.
// ─────────────────────────────────────────────────────────────────────────────

/** Una línea del presupuesto tal como se congela en el enlace (`ItemPresupuesto` de la web). */
data class ItemPresupuestoDental(
    val clave: String,
    /** "odontograma" | "extra". */
    val tipo: String,
    val procedimientoId: String,
    val nombre: String,
    val detalle: String,
    val piezas: List<String>,
    /** 1 urgente · 2 rehabilitación · 3 estética. */
    val fase: Int,
    val precio: Double,
    // Solo para el documento (no cuentan en la huella).
    val hallazgo: String? = null,
    val precioUnitario: Double? = null,
    val preciosPieza: List<PrecioPieza>? = null,
)

private data class FaseInfo(val nombre: String, val emoji: String)
private val FASES = mapOf(
    1 to FaseInfo("Fase 1 · Urgente", "🔴"),
    2 to FaseInfo("Fase 2 · Rehabilitación", "🟡"),
    3 to FaseInfo("Fase 3 · Estética", "🟢"),
)

/** Gemelo de `faseSugerida`: urgente (endodoncia, extracción…), estética (blanqueamiento…), el resto rehabilitación. */
fun faseSugerida(nombreServicio: String, nombreHallazgo: String = ""): Int {
    val t = "$nombreServicio $nombreHallazgo".lowercase()
    if (Regex("endodon|conducto|pulp|extracci|exodon|fractura|absceso|dolor|urgen").containsMatchIn(t)) return 1
    if (Regex("blanque|carilla|est[eé]tic|sonrisa|contorne").containsMatchIn(t)) return 3
    return 2
}

/** El detalle de una línea del odontograma, como `detalleLinea` del panel web. */
fun detalleLineaPresupuesto(l: LineaPresupuesto): String = if (esLineaDeBoca(l)) {
    if ("BOCA" in l.piezas) "${l.hallazgoNombre} · Boca completa" else "${l.hallazgoNombre} generalizado"
} else "${l.hallazgoNombre} en pieza(s) ${l.piezas.joinToString(", ")}"

/**
 * Lo marcado como líneas del enlace (el `items` del panel web). [preciosManuales]
 * = los procedimientos con precio escrito a mano: ahí no viaja el precio por
 * pieza (manda el escrito). [extras] = servicios agregados (ya con su precio).
 */
fun itemsPresupuestoDental(
    lineas: List<LineaPresupuesto>,
    extras: List<ProcedimientoRef>,
    preciosManuales: Set<String> = emptySet(),
): List<ItemPresupuestoDental> =
    lineas.map { l ->
        val deBoca = esLineaDeBoca(l)
        ItemPresupuestoDental(
            clave = l.procedimientoId, tipo = "odontograma", procedimientoId = l.procedimientoId,
            nombre = l.nombre, detalle = detalleLineaPresupuesto(l), piezas = if (deBoca) emptyList() else l.piezas,
            fase = faseSugerida(l.nombre, l.hallazgoNombre), precio = l.subtotal,
            hallazgo = l.hallazgoNombre, precioUnitario = l.precioUnitario,
            preciosPieza = l.preciosPieza?.takeIf { l.procedimientoId !in preciosManuales },
        )
    } + extras.map { e ->
        ItemPresupuestoDental(
            clave = "extra-${e.id}", tipo = "extra", procedimientoId = e.id, nombre = e.nombre,
            detalle = "Tratamiento complementario", piezas = emptyList(), fase = faseSugerida(e.nombre),
            precio = e.precio, precioUnitario = e.precio,
        )
    }

/** Math.round de JS (la mitad sube), a 2 decimales. */
private fun r2(n: Double): Double = kotlin.math.floor(n * 100 + 0.5) / 100

/** Un número como lo escribe JSON.stringify (160 → "160", 80.5 → "80.5"). */
internal fun numeroJs(n: Double): String =
    if (n == kotlin.math.floor(n) && kotlin.math.abs(n) < 1e15) n.toLong().toString() else n.toString()

/** Un texto como lo escribe JSON.stringify. */
internal fun textoJs(s: String): String = buildString {
    append('"')
    for (ch in s) when {
        ch == '"' -> append("\\\"")
        ch == '\\' -> append("\\\\")
        ch == '\n' -> append("\\n")
        ch == '\r' -> append("\\r")
        ch == '\t' -> append("\\t")
        ch == '\b' -> append("\\b")
        ch == '\u000C' -> append("\\f")
        ch.code < 0x20 -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
        else -> append(ch)
    }
    append('"')
}

/**
 * Huella del CONTENIDO (`huellaPresupuesto` de la web): FNV-1a de 32 bits sobre
 * las líneas en forma canónica. Si ya hay un enlace vigente con la misma huella
 * se reutiliza en vez de crear otro (web y app comparan igual).
 */
fun huellaPresupuestoDental(items: List<ItemPresupuestoDental>): String {
    val filas = items.map { i ->
        // [procedimiento_id, nombre, detalle, piezas ordenadas, fase, precio, alternativa (null)]
        "[" + listOf(
            textoJs(i.procedimientoId), textoJs(i.nombre), textoJs(i.detalle),
            "[" + i.piezas.sorted().joinToString(",") { textoJs(it) } + "]",
            i.fase.toString(), numeroJs(r2(i.precio)), "null",
        ).joinToString(",") + "]"
    }.sorted()
    val canon = "[" + filas.joinToString(",") + "]"
    var h = 0x811c9dc5.toInt()
    for (ch in canon) {
        h = h xor ch.code
        h *= 0x01000193
    }
    return h.toUInt().toString(16).padStart(8, '0')
}

/** Subtotal de una fase y total (`totalesPresupuesto` sin alternativas). */
private fun totalesPorFase(items: List<ItemPresupuestoDental>): Pair<Map<Int, Double>, Double> {
    val porFase = mutableMapOf(1 to 0.0, 2 to 0.0, 3 to 0.0)
    for (it in items) {
        val f = if (it.fase in 1..3) it.fase else 2
        porFase[f] = r2(porFase.getValue(f) + maxOf(0.0, it.precio))
    }
    return porFase to r2(porFase.getValue(1) + porFase.getValue(2) + porFase.getValue(3))
}

/** Gemelo de `textoPresupuestoWhatsApp`: por fases, con el enlace para aceptar. */
fun textoPresupuestoDentalWhatsApp(items: List<ItemPresupuestoDental>, enlace: String?, moneda: String?): String {
    fun soles(n: Double) = formatearDinero(n, moneda)
    val (porFase, total) = totalesPorFase(items)
    val grupos = (1..3).map { f -> f to items.filter { (if (it.fase in 1..3) it.fase else 2) == f } }.filter { it.second.isNotEmpty() }
    val bloques = grupos.map { (f, its) ->
        val filas = its.joinToString("\n") { "• *${it.nombre}*${if (it.detalle.isNotEmpty()) " (${it.detalle})" else ""}: ${soles(it.precio)}" }
        "${FASES.getValue(f).emoji} *${FASES.getValue(f).nombre}* — ${soles(porFase.getValue(f))}\n$filas"
    }
    val partes = mutableListOf("🦷 *Presupuesto Dental*", bloques.joinToString("\n\n"), "*Total:* ${soles(total)}")
    if (grupos.size > 1) {
        val primera = grupos.first().first
        partes += "_Puedes empezar solo con la ${FASES.getValue(primera).nombre.lowercase()}: ${soles(porFase.getValue(primera))}._"
    }
    partes += if (enlace != null) "Revísalo con tu odontograma y acéptalo desde tu celular:\n$enlace"
        else "_Para coordinar tus citas o consultas, responde a este mensaje._"
    return partes.joinToString("\n\n")
}

/** Lo mínimo de un presupuesto ya guardado para decidir si se reutiliza. */
data class PresupuestoDentalGuardado(
    val id: String,
    val token: String,
    val estado: String,
    /** Instante ISO; null = sin vencimiento. */
    val venceAt: String?,
    val huella: String?,
    val pideFirma: Boolean,
    /** ¿Las elecciones guardadas son todas «A» (o no hay)? Las líneas de la app no tienen opción B. */
    val eleccionesTodasA: Boolean = true,
)

/** Gemelo de `estadoVisible`: un Enviado con la fecha pasada ya está vencido. */
fun estadoVisiblePresupuesto(estado: String, venceAt: String?, ahoraMs: Long): String {
    if (estado == "Aceptado" || estado == "Rechazado" || estado == "Vencido") return estado
    if (venceAt.isNullOrBlank()) return "Enviado"
    // Una fecha que no se entiende no se da por vigente: se congela otro.
    val vence = runCatching { kotlinx.datetime.Instant.parse(venceAt).toEpochMilliseconds() }.getOrNull() ?: return "Vencido"
    return if (vence < ahoraMs) "Vencido" else "Enviado"
}

/**
 * Gemelo de `guardadoReutilizable` (sin alternativas: la misma huella implica
 * las mismas elecciones). [guardados] del más reciente al más viejo:
 *  · el ÚLTIMO Aceptado con la misma huella → ese (no se pisa la aceptación);
 *  · si no, un Enviado vigente con la misma huella y la misma firma pedida;
 *  · si no, null → se congela uno nuevo.
 */
fun presupuestoReutilizable(
    guardados: List<PresupuestoDentalGuardado>, huella: String, pideFirma: Boolean, ahoraMs: Long,
): PresupuestoDentalGuardado? {
    val ultimo = guardados.firstOrNull()
    if (ultimo != null && estadoVisiblePresupuesto(ultimo.estado, ultimo.venceAt, ahoraMs) == "Aceptado" &&
        ultimo.huella == huella && ultimo.eleccionesTodasA) return ultimo
    return guardados.firstOrNull {
        estadoVisiblePresupuesto(it.estado, it.venceAt, ahoraMs) == "Enviado" && it.huella == huella &&
            it.pideFirma == pideFirma && it.eleccionesTodasA
    }
}

/** Token no adivinable de 64 hex (`nuevoTokenPresupuesto`): dos UUID aleatorios seguros. */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
fun nuevoTokenPresupuesto(): String = kotlin.uuid.Uuid.random().toHexString() + kotlin.uuid.Uuid.random().toHexString()

/** El snapshot que se congela (`SnapshotPresupuesto` de la web, mismas claves). */
fun snapshotPresupuestoDental(
    items: List<ItemPresupuestoDental>,
    hallazgos: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
): JsonObject {
    val porId = catalogo.associateBy { it.id }
    return buildJsonObject {
        put("version", 1)
        put("items", buildJsonArray {
            items.forEach { i ->
                add(buildJsonObject {
                    put("clave", i.clave); put("tipo", i.tipo); put("procedimiento_id", i.procedimientoId)
                    put("nombre", i.nombre); put("detalle", i.detalle)
                    put("piezas", JsonArray(i.piezas.map { JsonPrimitive(it) }))
                    put("fase", i.fase); put("precio", r2(i.precio)); put("alternativa", JsonNull)
                    if (i.tipo == "odontograma") put("hallazgo", i.hallazgo)
                    i.precioUnitario?.let { put("precio_unitario", r2(it)) }
                    if (i.tipo == "odontograma") put("precios_pieza", i.preciosPieza?.let { lista ->
                        JsonArray(lista.map { p -> buildJsonObject { put("pieza", p.pieza); put("precio", p.precio) } })
                    } ?: JsonNull)
                })
            }
        })
        // Hallazgos (sin notas clínicas: el enlace viaja por WhatsApp) para dibujar el odontograma.
        put("odontograma", JsonArray(hallazgos.mapNotNull { r ->
            val h = porId[r.hallazgoId] ?: return@mapNotNull null
            buildJsonObject {
                put("diente", r.diente); put("diente_hasta", r.dienteHasta)
                put("superficies", r.superficies?.let { s -> JsonArray(s.map { JsonPrimitive(it) }) } ?: JsonNull)
                put("estado", r.estado); put("hallazgo_id", r.hallazgoId)
                put("nombre", h.nombre); put("color", h.color); put("marca_ausente", h.marcaAusente)
                put("por_boca", h.porBoca); put("notas", JsonNull)
            }
        }))
        put("elecciones", buildJsonObject {})
        put("total", totalesPorFase(items).second)
    }
}

/** Lo que pasó al pedir el enlace. */
sealed interface ResultadoEnlacePresupuesto {
    /** El WhatsApp listo para abrir (con el enlace adentro). [reuso] = el que ya estaba vigente. */
    data class Listo(val urlWhatsApp: String, val reuso: Boolean) : ResultadoEnlacePresupuesto
    /** No hay nada que mandar o falta algo (el texto ya dice qué). */
    data class NoSe(val motivo: String) : ResultadoEnlacePresupuesto
}

/**
 * Congela (o reutiliza) el presupuesto a la vista y arma el WhatsApp con su
 * enlace. Lecturas y la escritura directas a Supabase con la RLS del staff,
 * como la web (clinica_id lo pone la base: DEFAULT get_clinica_id()).
 */
object PresupuestoDentalRepo {

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    suspend fun enlaceWhatsApp(
        pacienteId: String,
        items: List<ItemPresupuestoDental>,
        hallazgos: List<DienteHallazgo>,
        catalogo: List<HallazgoDental>,
        moneda: String?,
        pideFirma: Boolean = true,
    ): ResultadoEnlacePresupuesto {
        if (items.isEmpty()) return ResultadoEnlacePresupuesto.NoSe("No hay nada marcado en el presupuesto para enviar.")
        val sinPrecio = items.filter { it.precio <= 0.0 }.map { it.nombre }
        if (sinPrecio.isNotEmpty()) return ResultadoEnlacePresupuesto.NoSe("Pon el precio de: ${sinPrecio.joinToString(", ")}")
        val huella = huellaPresupuestoDental(items)
        val ahora = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val guardados = Supabase.client.postgrest["presupuestos_dentales"]
            .select(Columns.list("id, token, estado, vence_at, huella, pide_firma, elecciones")) {
                filter { eq("paciente_id", pacienteId) }
                order("created_at", Order.DESCENDING); limit(10)
            }.decodeList<JsonObject>().mapNotNull { o ->
                PresupuestoDentalGuardado(
                    id = o.s("id") ?: return@mapNotNull null, token = o.s("token") ?: return@mapNotNull null,
                    estado = o.s("estado").orEmpty(), venceAt = o.s("vence_at"), huella = o.s("huella"),
                    pideFirma = o.s("pide_firma") == "true",
                    eleccionesTodasA = (o["elecciones"] as? JsonObject)?.values?.none { (it as? JsonPrimitive)?.content == "B" } ?: true,
                )
            }
        val reuso = presupuestoReutilizable(guardados, huella, pideFirma, ahora)
        val token = reuso?.token ?: nuevoTokenPresupuesto().also { tk ->
            Supabase.client.postgrest["presupuestos_dentales"].insert(buildJsonObject {
                put("paciente_id", pacienteId); put("token", tk); put("huella", huella)
                put("snapshot", snapshotPresupuestoDental(items, hallazgos, catalogo))
                put("total", totalesPorFase(items).second); put("pide_firma", pideFirma)
            })
        }
        val telefono = runCatching {
            Supabase.client.postgrest["pacientes"].select(Columns.list("telefono")) {
                filter { eq("id", pacienteId) }; limit(1)
            }.decodeList<JsonObject>().firstOrNull()?.s("telefono")
        }.getOrNull()
        val texto = textoPresupuestoDentalWhatsApp(items, "${Supabase.SITE_URL}/presupuesto/$token", moneda)
        val url = enlaceWhatsAppSede(telefono, texto) ?: pe.saniape.app.ui.urlWhatsApp("", texto)
        return ResultadoEnlacePresupuesto.Listo(url, reuso = reuso != null)
    }
}
