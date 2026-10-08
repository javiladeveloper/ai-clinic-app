package pe.saniape.app.data.staff

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Retención (CRM de recuperación). Lógica PURA, gemela de lib/retencion-llamadas.ts
// y lib/cuotas.ts de la web. El cálculo pesado (última sesión, días sin venir,
// saldo…) lo hace la base (funciones retencion_resumen / retencion_llamar): acá
// solo se traducen filtros, se priorizan listas y se arman las exportaciones.
// Si cambia una regla en la web, cambia acá.
// ─────────────────────────────────────────────────────────────────────────────

/** Una fila de `retencion_llamar` (un tratamiento con su paciente). */
data class FilaRetencion(
    val tratamientoId: String,
    val pacienteId: String,
    val nombre: String,
    val dni: String?,
    val telefono: String?,
    val terapeutaId: String?,
    val profesional: String?,
    val servicio: String?,
    val especialidad: String?,
    val modalidad: String?,
    val estado: String,
    val noVolvio: Boolean,
    val totalSesiones: Int,
    val completadas: Int,
    val quedan: Int,
    val usaSesiones: Boolean,
    val fechaInicio: String?,
    val ultimaSesion: String?,
    val diasSin: Int?,
    val proximaCita: String?,
    val proximaHora: String?,
    val proximoControl: String?,
    val pagado: Double,
    val saldo: Double,
    val diagnostico: String?,
    val motivo: String?,
    val edad: Int?,
)

data class OpcionRet(val id: String, val nombre: String)

data class OpcionesRetencion(
    val profesionales: List<OpcionRet> = emptyList(),
    val servicios: List<OpcionRet> = emptyList(),
    val especialidades: List<OpcionRet> = emptyList(),
    val modalidades: List<String> = emptyList(),
)

/** Lo que devuelve `retencion_resumen`. */
data class ResumenRetencion(
    val noVuelven: Int = 0,
    val probableAbandono: Int = 0,
    val dineroRiesgo: Double = 0.0,
    val controles: Int = 0,
    val pctCompletan: Int? = null,
    val recuperadosMes: Int = 0,
    val nuncaEmpezaron: Int = 0,
    val opciones: OpcionesRetencion = OpcionesRetencion(),
)

private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
private fun JsonObject.d(k: String): Double = s(k)?.toDoubleOrNull() ?: 0.0
private fun JsonObject.i(k: String): Int = s(k)?.toDoubleOrNull()?.toInt() ?: 0
private fun JsonObject.iN(k: String): Int? = s(k)?.toDoubleOrNull()?.toInt()
private fun JsonObject.b(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false

fun filaRetencionDesde(o: JsonObject): FilaRetencion? {
    val trat = o.s("tratamiento_id") ?: return null
    return FilaRetencion(
        tratamientoId = trat,
        pacienteId = o.s("paciente_id") ?: return null,
        nombre = o.s("nombre").orEmpty(),
        dni = o.s("dni"), telefono = o.s("telefono"),
        terapeutaId = o.s("terapeuta_id"), profesional = o.s("profesional"),
        servicio = o.s("servicio"), especialidad = o.s("especialidad"), modalidad = o.s("modalidad"),
        estado = o.s("estado").orEmpty(), noVolvio = o.b("no_volvio"),
        totalSesiones = o.i("total_sesiones"), completadas = o.i("completadas"), quedan = o.i("quedan"),
        usaSesiones = o.b("usa_sesiones"),
        fechaInicio = o.s("fecha_inicio"), ultimaSesion = o.s("ultima_sesion"), diasSin = o.iN("dias_sin"),
        proximaCita = o.s("proxima_cita"), proximaHora = o.s("proxima_hora"), proximoControl = o.s("proximo_control"),
        pagado = o.d("pagado"), saldo = o.d("saldo"),
        diagnostico = o.s("diagnostico"), motivo = o.s("motivo"), edad = o.iN("edad"),
    )
}

private fun opciones(a: JsonArray?): List<OpcionRet> = a.orEmpty().mapNotNull {
    val o = it as? JsonObject ?: return@mapNotNull null
    OpcionRet(o.s("id") ?: return@mapNotNull null, o.s("nombre").orEmpty())
}

fun resumenRetencionDesde(o: JsonObject): ResumenRetencion {
    val op = o["opciones"] as? JsonObject
    return ResumenRetencion(
        noVuelven = o.i("no_vuelven"), probableAbandono = o.i("probable_abandono"),
        dineroRiesgo = o.d("dinero_riesgo"), controles = o.i("controles"),
        pctCompletan = o.iN("pct_completan"), recuperadosMes = o.i("recuperados_mes"),
        nuncaEmpezaron = o.i("nunca_empezaron"),
        opciones = OpcionesRetencion(
            profesionales = opciones(op?.get("profesionales") as? JsonArray),
            servicios = opciones(op?.get("servicios") as? JsonArray),
            especialidades = opciones(op?.get("especialidades") as? JsonArray),
            modalidades = (op?.get("modalidades") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        ),
    )
}

// ── Umbrales ────────────────────────────────────────────────────────────────

const val DIAS_NO_VUELVE = 14
const val DIAS_PROBABLE_ABANDONO = 45
const val DIAS_ABANDONO_CRITICO = 60
const val POR_PAGINA_RET = 50
const val TOPE_EXPORTAR_RET = 5000

val ATAJOS_DIAS = listOf(7, 15, 30, 45, 60, 90)
val ESTADOS_TRATAMIENTO = listOf("Activo", "Suspendido", "Completado", "Alta", "Cancelado")
val MODALIDADES_RET = listOf("Paquete", "Sesión suelta", "Unidades", "Consulta")

/** Claves de orden de `retencion_llamar`. */
enum class OrdenRet(val clave: String, val etiqueta: String, val asc1: Boolean) {
    DIAS("dias", "Última sesión", false),
    NOMBRE("nombre", "Nombre", true),
    SESION("sesion", "Sesión", false),
    QUEDAN("quedan", "Le quedan", false),
    PROXIMA("proxima", "Próxima cita", true),
    SALDO("saldo", "Saldo", false),
    PROFESIONAL("profesional", "Profesional", true),
    SERVICIO("servicio", "Servicio", true),
    CONTROL("control", "Control", true),
}

// ── Filtros ─────────────────────────────────────────────────────────────────

data class FiltrosLlamadas(
    val q: String = "",
    /** "" · "7".."90" (hace N+ días) · "rango". */
    val ultima: String = "",
    val ultimaDesde: String = "",
    val ultimaHasta: String = "",
    val sinCita: Boolean = false,
    val sesionDesde: Int? = null,
    val sesionHasta: Int? = null,
    val quedanMin: Int? = null,
    val quedanMax: Int? = null,
    /** Vacío = todos los estados. */
    val estados: List<String> = emptyList(),
    val profesional: String = "",
    val servicio: String = "",
    val especialidad: String = "",
    val modalidad: String = "",
    /** "" · "si" · "no". */
    val saldo: String = "",
    val orden: OrdenRet = OrdenRet.DIAS,
    val asc: Boolean = false,
    val pagina: Int = 1,
)

data class PresetRet(val id: String, val label: String, val ayuda: String, val filtros: FiltrosLlamadas)

val PRESETS_RET: List<PresetRet> = listOf(
    PresetRet(
        "abandono", "Probable abandono ($DIAS_PROBABLE_ABANDONO+ días)",
        "Seguramente ya no vuelven: llamar o cerrar como \"No volvió\"",
        FiltrosLlamadas(estados = listOf("Activo"), ultima = "45", sinCita = true, quedanMin = 1),
    ),
    PresetRet(
        "por_terminar", "Por terminar el paquete",
        "Les quedan 1 o 2 sesiones: buen momento para ofrecer continuar",
        FiltrosLlamadas(estados = listOf("Activo"), quedanMin = 1, quedanMax = 2),
    ),
    PresetRet(
        "mitad", "Sesión 5 a 10",
        "Llegaron a la sesión 5 a 10: en curso y también los que ya terminaron",
        FiltrosLlamadas(sesionDesde = 5, sesionHasta = 10),
    ),
    PresetRet(
        "terminaron", "Terminaron su tratamiento",
        "Dados de alta o paquete completado: llamar para un control o mantenimiento",
        FiltrosLlamadas(estados = listOf("Completado", "Alta")),
    ),
)

fun aplicarPresetRet(id: String): FiltrosLlamadas = PRESETS_RET.firstOrNull { it.id == id }?.filtros ?: FiltrosLlamadas()

/** ¿Los filtros actuales son exactamente un preset? (ignora orden y página). */
fun presetActivoRet(f: FiltrosLlamadas): String {
    val base = f.copy(orden = OrdenRet.DIAS, asc = false, pagina = 1)
    return PRESETS_RET.firstOrNull { it.filtros == base }?.id.orEmpty()
}

fun diasMinimos(f: FiltrosLlamadas): Int? = if (f.ultima.isNotEmpty() && f.ultima != "rango") f.ultima.toIntOrNull() else null

/** `p_filtros` de `retencion_llamar`. Solo manda lo que filtra. */
fun filtrosARpc(f: FiltrosLlamadas): JsonObject = buildJsonObject {
    if (f.q.isNotBlank()) put("q", f.q.trim())
    if (f.estados.isNotEmpty()) put("estados", buildJsonArray { f.estados.forEach { add(JsonPrimitive(it)) } })
    diasMinimos(f)?.let { put("dias_min", it) }
    if (f.ultima == "rango") {
        if (f.ultimaDesde.isNotEmpty()) put("ultima_desde", f.ultimaDesde)
        if (f.ultimaHasta.isNotEmpty()) put("ultima_hasta", f.ultimaHasta)
    }
    if (f.sinCita) put("sin_cita", true)
    // Desde > hasta: se entiende al revés (5–10 escrito como 10–5).
    var sd = f.sesionDesde; var sh = f.sesionHasta
    if (sd != null && sh != null && sd > sh) { val t = sd; sd = sh; sh = t }
    sd?.let { put("sesion_desde", it) }; sh?.let { put("sesion_hasta", it) }
    var qmin = f.quedanMin; var qmax = f.quedanMax
    if (qmin != null && qmax != null && qmin > qmax) { val t = qmin; qmin = qmax; qmax = t }
    qmin?.let { put("quedan_min", it) }; qmax?.let { put("quedan_max", it) }
    if (f.profesional.isNotEmpty()) put("terapeuta_id", f.profesional)
    if (f.servicio.isNotEmpty()) put("procedimiento_id", f.servicio)
    if (f.especialidad.isNotEmpty()) put("especialidad_id", f.especialidad)
    if (f.modalidad.isNotEmpty()) put("modalidad", f.modalidad)
    if (f.saldo.isNotEmpty()) put("saldo", f.saldo)
}

fun contarFiltrosAvanzados(f: FiltrosLlamadas): Int {
    var n = 0
    if (f.sesionDesde != null || f.sesionHasta != null) n++
    if (f.quedanMin != null || f.quedanMax != null) n++
    if (f.estados.isNotEmpty()) n++
    if (f.profesional.isNotEmpty()) n++
    if (f.servicio.isNotEmpty()) n++
    if (f.especialidad.isNotEmpty()) n++
    if (f.modalidad.isNotEmpty()) n++
    if (f.saldo.isNotEmpty()) n++
    return n
}

fun hayFiltrosRet(f: FiltrosLlamadas): Boolean = f.q.isNotBlank() || f.ultima.isNotEmpty() || f.sinCita || contarFiltrosAvanzados(f) > 0

/** Los filtros en palabras (encabezado de la hoja impresa). */
fun describirFiltros(f: FiltrosLlamadas, profesional: String? = null, servicio: String? = null, especialidad: String? = null): List<String> {
    val out = mutableListOf<String>()
    PRESETS_RET.firstOrNull { it.id == presetActivoRet(f) }?.let { out += it.label }
    if (f.q.isNotBlank()) out += "Búsqueda: \"${f.q.trim()}\""
    diasMinimos(f)?.let { out += "Última sesión hace $it+ días" }
    if (f.ultima == "rango" && (f.ultimaDesde.isNotEmpty() || f.ultimaHasta.isNotEmpty())) {
        val p = listOfNotNull(
            f.ultimaDesde.takeIf { it.isNotEmpty() }?.let { "desde ${fechaCortaRet(it)}" },
            f.ultimaHasta.takeIf { it.isNotEmpty() }?.let { "hasta ${fechaCortaRet(it)}" },
        )
        out += "Última sesión ${p.joinToString(" ")}"
    }
    if (f.sinCita) out += "Sin cita futura"
    fun rango(label: String, d: Int?, h: Int?) {
        if (d != null && h != null) out += if (d == h) "$label $d" else "$label $d a $h"
        else if (d != null) out += "$label $d o más"
        else if (h != null) out += "$label $h o menos"
    }
    rango("Sesión", f.sesionDesde, f.sesionHasta)
    rango("Le quedan", f.quedanMin, f.quedanMax)
    out += if (f.estados.isEmpty()) "Todos los estados" else "Tratamiento: ${f.estados.joinToString(", ")}"
    if (f.profesional.isNotEmpty()) out += "Profesional: ${profesional ?: "—"}"
    if (f.servicio.isNotEmpty()) out += "Servicio: ${servicio ?: "—"}"
    if (f.especialidad.isNotEmpty()) out += "Especialidad: ${especialidad ?: "—"}"
    if (f.modalidad.isNotEmpty()) out += "Modalidad: ${f.modalidad}"
    if (f.saldo == "si") out += "Con saldo pendiente"
    if (f.saldo == "no") out += "Sin saldo pendiente"
    return out
}

// ── Las otras dos listas, sobre la misma función ────────────────────────────

/** "No vuelven": misma regla que el cron de alertas y la web. */
fun rpcNoVuelven(): JsonObject = buildJsonObject {
    put("estados", buildJsonArray { add(JsonPrimitive("Activo")) })
    put("usa_sesiones", true); put("quedan_min", 1); put("sin_cita", true); put("dias_min", DIAS_NO_VUELVE)
}

/** "Controles": próximo control vencido o de hoy, sin cita agendada. */
fun rpcControles(): JsonObject = buildJsonObject {
    put("estados", buildJsonArray { add(JsonPrimitive("Activo")) })
    put("control_vencido", true); put("sin_cita", true)
}

enum class SituacionPago { DEBE_NO_VOLVIO, PARCIAL_NO_VOLVIO, PAGO_TODO_NO_VOLVIO }

data class NoVuelveConPrioridad(val fila: FilaRetencion, val situacion: SituacionPago, val score: Double, val prioridad: String)

/**
 * Ordena "No vuelven" por prioridad (deuda ponderada por lo ya recibido, con piso
 * 0.25 para quien no empezó; quien pagó TODO y no vino pesa por lo pagado). El
 * nivel alta/media/baja es relativo al conjunto visible. Gemelo de priorizarNoVuelven.
 */
fun priorizarNoVuelven(filas: List<FilaRetencion>): List<NoVuelveConPrioridad> {
    val conScore = filas.map { f ->
        val saldo = max(0.0, f.saldo)
        val situacion = when {
            saldo <= 0.005 -> SituacionPago.PAGO_TODO_NO_VOLVIO
            f.pagado <= 0.005 -> SituacionPago.DEBE_NO_VOLVIO
            else -> SituacionPago.PARCIAL_NO_VOLVIO
        }
        val frac = if (f.totalSesiones > 0) min(1.0, f.completadas.toDouble() / f.totalSesiones) else 0.0
        val peso = max(frac, 0.25)
        val pagoSinRecibir = saldo <= 0.005 && f.pagado > 0 && f.completadas == 0
        val score = if (pagoSinRecibir) f.pagado else if (saldo > 0) saldo * peso else 0.0
        Triple(f, situacion, score)
    }
    val maxScore = max(conScore.maxOfOrNull { it.third } ?: 1.0, 1.0)
    return conScore.map { (f, s, sc) ->
        NoVuelveConPrioridad(f, s, sc, when {
            sc >= 0.6 * maxScore -> "alta"
            sc >= 0.25 * maxScore -> "media"
            else -> "baja"
        })
    }.sortedWith(compareByDescending<NoVuelveConPrioridad> { it.score }.thenByDescending { it.fila.diasSin ?: 0 })
}

// ── Presentación ────────────────────────────────────────────────────────────

/** 45+ días sin venir con sesiones pendientes, tratamiento vivo → "critico" (60+) o "probable". */
fun nivelAbandono(f: FilaRetencion): String? {
    if (f.estado != "Activo" || !f.usaSesiones || f.quedan <= 0) return null
    val d = f.diasSin ?: 0
    return when {
        d >= DIAS_ABANDONO_CRITICO -> "critico"
        d >= DIAS_PROBABLE_ABANDONO -> "probable"
        else -> null
    }
}

fun haceDias(n: Int?): String = when {
    n == null -> ""
    n <= 0 -> "hoy"
    n == 1 -> "ayer"
    else -> "hace $n días"
}

/** 'YYYY-MM-DD' → 'dd/mm/aa'. */
fun fechaCortaRet(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val p = iso.take(10).split("-")
    if (p.size != 3) return iso
    return "${p[2]}/${p[1]}/${p[0].takeLast(2)}"
}

/** "5 / 10" — o solo "5" si no cuenta sesiones. */
fun textoSesionRet(f: FilaRetencion): String = if (f.totalSesiones > 0) "${f.completadas} / ${f.totalSesiones}" else f.completadas.toString()

/** Un control vencido se agenda hoy; uno futuro, en su fecha (gemelo de fechaParaControl). */
fun fechaParaControlRet(proximoControl: String?, hoy: String): String {
    val f = proximoControl.orEmpty().take(10)
    return if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(f) && f >= hoy) f else hoy
}

// ── Exportación ─────────────────────────────────────────────────────────────

val COLUMNAS_EXPORTAR_RET = listOf(
    "Paciente", "DNI", "Edad", "Teléfono", "Motivo de consulta", "Diagnóstico", "Especialidad", "Servicio",
    "Inicio del tratamiento", "Estado del tratamiento", "Sesión", "Le quedan", "Última sesión", "Días sin venir",
    "Próxima cita", "Profesional", "Saldo (S/)", "Resultado de la llamada",
)

fun filaExportable(f: FilaRetencion): List<String> = listOf(
    f.nombre, f.dni.orEmpty(), f.edad?.toString().orEmpty(), f.telefono.orEmpty(), f.motivo.orEmpty(), f.diagnostico.orEmpty(),
    f.especialidad.orEmpty(), f.servicio.orEmpty(), fechaCortaRet(f.fechaInicio),
    if (f.noVolvio) "${f.estado} (No volvió)" else f.estado,
    textoSesionRet(f), if (f.totalSesiones > 0) f.quedan.toString() else "",
    if (f.ultimaSesion != null) fechaCortaRet(f.ultimaSesion) else "Sin sesiones",
    f.diasSin?.toString().orEmpty(),
    if (f.proximaCita != null) fechaCortaRet(f.proximaCita) + (f.proximaHora?.let { " " + it.take(5) } ?: "") else "",
    f.profesional.orEmpty(),
    if (f.saldo > 0.005) ((f.saldo * 100).roundToInt() / 100.0).toString() else "0",
    "",
)

/** "27/09 a las 20:14" en hora de Lima (los timestamptz vienen en UTC). */
fun fechaHoraLima(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val d = kotlinx.datetime.Instant.parse(iso).toLocalDateTime(ZONA_CLINICA)
        "${d.dayOfMonth.toString().padStart(2, '0')}/${d.monthNumber.toString().padStart(2, '0')} a las ${d.hour.toString().padStart(2, '0')}:${d.minute.toString().padStart(2, '0')}"
    }.getOrDefault(fechaCortaRet(iso))
}

/** CSV con BOM y comillas (abre bien en Excel); neutraliza fórmulas. */
fun csvDe(filas: List<List<String>>): String {
    // Una celda que empieza con = + - @ se tomaría como fórmula en Excel: se antepone '.
    fun cel(s: String): String {
        val seguro = if (s.isNotEmpty() && s[0] in "=+-@\t\r") "'" + s else s
        return "\"" + seguro.replace("\"", "\"\"") + "\""
    }
    return "﻿" + filas.joinToString("\r\n") { r -> r.joinToString(",") { cel(it) } }
}

private fun esc(s: String?): String = s.orEmpty().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/** Hoja imprimible para el equipo de llamadas (todo lo de la base se escapa). */
fun htmlHojaLlamadas(clinica: String, fecha: String, filtros: List<String>, filas: List<FilaRetencion>, total: Int): String {
    val cuerpo = filas.mapIndexed { i, f ->
        val ultima = if (f.ultimaSesion != null) "${fechaCortaRet(f.ultimaSesion)}<br><span class=\"s\">${esc(haceDias(f.diasSin))}</span>"
        else "Sin sesiones<br><span class=\"s\">inicio ${esc(haceDias(f.diasSin))}</span>"
        val saldo = if (f.saldo > 0.005) "S/ ${(f.saldo * 100).roundToInt() / 100.0}" else "—"
        val sub = listOfNotNull(f.dni?.let { "DNI ${esc(it)}" }, f.edad?.let { "$it años" }).joinToString(" · ")
        val dx = (f.diagnostico ?: f.motivo)?.let { "<br><span class=\"dx\">${esc(it)}</span>" }.orEmpty()
        "<tr><td class=\"n\">${i + 1}</td><td><b>${esc(f.nombre)}</b>${if (sub.isNotEmpty()) "<br><span class=\"s\">$sub</span>" else ""}</td>" +
            "<td class=\"tel\">${esc(f.telefono ?: "—")}</td>" +
            "<td>${esc(f.servicio ?: "—")}${f.profesional?.let { "<br><span class=\"s\">${esc(it)}</span>" }.orEmpty()}$dx</td>" +
            "<td class=\"c\">${esc(textoSesionRet(f))}</td><td>$ultima</td>" +
            "<td class=\"c\">${if (f.proximaCita != null) esc(fechaCortaRet(f.proximaCita)) else "—"}</td>" +
            "<td class=\"r\">$saldo</td><td class=\"res\"><span class=\"chk\">☐ Contestó &nbsp; ☐ Agendó &nbsp; ☐ No volverá</span></td></tr>"
    }.joinToString("")
    val nota = if (total > filas.size) "<p class=\"s\">Se muestran ${filas.size} de $total.</p>" else ""
    return """<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Lista de llamadas — ${esc(clinica)}</title>
<style>
@page { size: A4 landscape; margin: 1.2cm; }
* { box-sizing: border-box; }
body { font-family: Lato, 'Segoe UI', Arial, sans-serif; color: #1e2d5e; margin: 0; font-size: 11px; }
h1 { font-size: 18px; margin: 0; }
.meta { color: #6b7280; margin: 2px 0 8px; border-bottom: 2px solid #2c3e7a; padding-bottom: 8px; }
.filtros span { background: #eef0f9; border: 1px solid #dde1f0; border-radius: 10px; padding: 1px 8px; margin-right: 4px; display: inline-block; }
table { width: 100%; border-collapse: collapse; margin-top: 8px; }
th { text-align: left; font-size: 9px; text-transform: uppercase; color: #6b7280; border-bottom: 1px solid #bcc4e8; padding: 4px; }
td { border-bottom: 1px solid #dde1f0; padding: 5px 4px; vertical-align: top; }
tr { break-inside: avoid; }
.c { text-align: center; } .r { text-align: right; } .tel { white-space: nowrap; }
.s { color: #6b7280; font-size: 9.5px; } .dx { font-size: 9.5px; font-style: italic; }
.res { min-width: 180px; height: 34px; } .chk { color: #6b7280; font-size: 9px; white-space: nowrap; }
</style></head><body>
<h1>Lista de llamadas · ${esc(clinica)}</h1>
<div class="meta">Generada el ${esc(fecha)} · $total ${if (total == 1) "tratamiento" else "tratamientos"}</div>
<div class="filtros">${filtros.joinToString("") { "<span>${esc(it)}</span>" }}</div>
$nota
<table><thead><tr><th>#</th><th>Paciente</th><th>Teléfono</th><th>Servicio</th><th>Sesión</th><th>Última sesión</th><th>Próx. cita</th><th>Saldo</th><th>Resultado de la llamada</th></tr></thead>
<tbody>$cuerpo</tbody></table>
<p class="s">Información confidencial de pacientes — uso interno de la clínica.</p>
</body></html>"""
}

// ── Cuotas por cobrar (odontología) — gemelo de lib/cuotas.ts ────────────────

data class CuotaPlan(val numero: Int, val monto: Double, val vence: String)

enum class EstadoCuota { PAGADA, ATRASADA, VENCE_HOY, POR_VENCER, PROGRAMADA }

data class CuotaConEstado(
    val numero: Int, val monto: Double, val vence: String,
    val estado: EstadoCuota, val pagado: Double, val saldo: Double, val diasAtraso: Int, val diasParaVencer: Int,
)

private fun r2(n: Double) = (n * 100).roundToInt() / 100.0

/** Aplica lo pagado del tratamiento a las cuotas en orden (inicial, 1, 2…) y calcula su estado respecto de [hoy]. */
fun estadoCuotas(plan: List<CuotaPlan>, totalPagado: Double, hoy: String): List<CuotaConEstado> {
    var restante = max(0.0, totalPagado)
    var proximaMarcada = false
    val h = LocalDate.parse(hoy.take(10))
    return plan.sortedBy { it.numero }.map { c ->
        val pagado = r2(min(restante, c.monto))
        restante = r2(restante - pagado)
        val saldo = r2(c.monto - pagado)
        val dif = h.daysUntil(LocalDate.parse(c.vence.take(10)))
        val estado = when {
            saldo <= 0.005 -> EstadoCuota.PAGADA
            dif < 0 -> EstadoCuota.ATRASADA
            dif == 0 -> EstadoCuota.VENCE_HOY
            !proximaMarcada || dif <= 7 -> EstadoCuota.POR_VENCER
            else -> EstadoCuota.PROGRAMADA
        }
        if (estado == EstadoCuota.POR_VENCER || estado == EstadoCuota.VENCE_HOY) proximaMarcada = true
        CuotaConEstado(c.numero, c.monto, c.vence, estado, pagado, if (saldo <= 0.005) 0.0 else saldo,
            if (estado == EstadoCuota.ATRASADA) -dif else 0, max(0, dif))
    }
}

fun etiquetaCuota(numero: Int, nCuotas: Int): String = if (numero == 0) "Inicial" else "$numero de $nCuotas"

fun textoEstadoCuota(c: CuotaConEstado): String {
    val parcial = c.pagado > 0 && c.saldo > 0
    return when (c.estado) {
        EstadoCuota.PAGADA -> "Pagada"
        EstadoCuota.ATRASADA -> "Atrasada ${c.diasAtraso} día${if (c.diasAtraso == 1) "" else "s"}${if (parcial) " · parcial" else ""}"
        EstadoCuota.VENCE_HOY -> if (parcial) "Vence hoy · parcial" else "Vence hoy"
        EstadoCuota.POR_VENCER -> if (parcial) "Por vencer · parcial" else "Por vencer"
        EstadoCuota.PROGRAMADA -> "Programada"
    }
}

/** ¿Se avisa esta cuota en Retención? Atrasada, de hoy, o por vencer en ≤ 7 días. */
fun cuotaParaAvisar(c: CuotaConEstado): Boolean =
    c.estado == EstadoCuota.ATRASADA || c.estado == EstadoCuota.VENCE_HOY ||
        (c.estado == EstadoCuota.POR_VENCER && c.diasParaVencer <= 7)
