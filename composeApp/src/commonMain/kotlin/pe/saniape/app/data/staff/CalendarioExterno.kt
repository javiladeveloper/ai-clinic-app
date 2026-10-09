package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Agenda traída de Google Calendar (o de un .ics) — estado para Más → Ajustes.
 * Gemelo de GET /api/staff/calendario (web). La autorización con Google se hace
 * en el navegador (Google no deja autorizar dentro de la app); elegir los
 * calendarios, la vista previa y la importación son nativas y usan los mismos
 * endpoints que la web (ver CalendarioVistaPrevia.kt).
 */
data class CuentaCalendario(val id: String, val cuenta: String, val activa: Boolean, val error: String?)

data class CalendarioTraido(
    val id: String,
    val nombre: String,
    val profesional: String?,
    val esIcs: Boolean,
    /** null = todavía no se revisó ni importó (se hace en la web). */
    val importadoEn: String?,
    val ultimaSync: String?,
    val error: String?,
    val citas: Int,
    /** Sin cupo, con error o con un cambio sin aplicar (Google: se reintentan solas). */
    val sinCupo: Int,
    /** Eventos que no se sabe de qué paciente son (se resuelven en la web). */
    val porRevisar: Int = 0,
    /** La importación inicial sigue (el servidor la continúa cada 10 min). */
    val enCurso: Boolean = false,
    /** "Enviar recordatorios a los pacientes" (WhatsApp). Apagado por defecto. */
    val recordatoriosPacientes: Boolean = false,
    val conexionId: String = "",
    val calendarioId: String = "",
    val terapeutaId: String? = null,
    /** Ajustes guardados de la fuente (punto de partida de la vista previa). */
    val config: ConfigFuenteCal = ConfigFuenteCal(),
    /** Plan Básico: todavía se puede importar (su única importación, o continuarla). Sin el campo → sí. */
    val puedeImportar: Boolean = true,
)

/** Un calendario de la cuenta de Google (GET /api/staff/calendario/calendarios). */
data class CalendarioGoogle(val id: String, val nombre: String, val principal: Boolean, val color: String?, val rol: String)

fun calendariosGoogleDe(o: JsonObject): List<CalendarioGoogle> = o.lista("calendarios").map {
    CalendarioGoogle(
        id = it.txt("id") ?: "", nombre = it.txt("nombre") ?: (it.txt("id") ?: "Calendario"),
        principal = it.bool("principal"), color = it.txt("color"), rol = it.txt("rol") ?: "reader",
    )
}.filter { it.id.isNotEmpty() }

data class EstadoCalendarioExterno(
    val disponible: Boolean,
    val cuentas: List<CuentaCalendario>,
    val calendarios: List<CalendarioTraido>,
    /** El plan sincroniza solo (Premium/Plus). Backend sin `plan` → true (el servidor decide igual). */
    val sincronizacion: Boolean = true,
    /** Básico que ya usó su única importación: no puede traer otra agenda. */
    val importacionUsada: Boolean = false,
)

private fun JsonObject.txt(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull == true
private fun JsonObject.boolONull(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.ent(k: String): Int = (this[k] as? JsonPrimitive)?.intOrNull ?: 0
private fun JsonObject.lista(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

fun estadoCalendarioDe(o: JsonObject): EstadoCalendarioExterno = EstadoCalendarioExterno(
    disponible = o.bool("googleConfigurado") && o.bool("llaveConfigurada"),
    sincronizacion = (o["plan"] as? JsonObject)?.boolONull("sincronizacion") ?: true,
    importacionUsada = (o["plan"] as? JsonObject)?.boolONull("importacionUsada") == true,
    cuentas = o.lista("conexiones").filter { it.txt("proveedor") == "google" }.map {
        CuentaCalendario(it.txt("id") ?: "", it.txt("cuenta") ?: "Cuenta de Google", it.txt("estado") == "activa", it.txt("ultimo_error"))
    },
    calendarios = o.lista("fuentes").map {
        CalendarioTraido(
            id = it.txt("id") ?: "",
            nombre = it.txt("nombre") ?: "Calendario",
            profesional = it.txt("terapeuta_nombre"),
            esIcs = it.txt("proveedor") == "ics",
            importadoEn = it.txt("importacion_inicial_at"),
            ultimaSync = it.txt("ultima_sync_at"),
            error = it.txt("ultimo_error"),
            citas = it.ent("citas"),
            sinCupo = it.ent("pendientes"),
            porRevisar = it.ent("porRevisar"),
            enCurso = it.txt("importacion_estado") == "en_curso",
            recordatoriosPacientes = (it["config"] as? JsonObject)?.bool("recordatoriosPacientes") == true,
            conexionId = it.txt("conexion_id") ?: "",
            calendarioId = it.txt("calendario_id") ?: "",
            terapeutaId = it.txt("terapeuta_id"),
            config = configFuenteDe(it["config"] as? JsonObject),
            puedeImportar = it.boolONull("puedeImportar") ?: true,
        )
    },
)

/** "hace 5 min" — gemelo de haceCuanto() de la web (lib/calendario/textos.ts). */
fun haceCuanto(isoMs: Long?, ahoraMs: Long): String {
    if (isoMs == null) return "nunca"
    val min = ((ahoraMs - isoMs) / 60_000).coerceAtLeast(0)
    if (min < 1) return "hace un momento"
    if (min < 60) return "hace $min min"
    val h = (min + 30) / 60
    if (h < 24) return "hace $h h"
    val d = (h + 12) / 24
    return if (d == 1L) "hace 1 día" else "hace $d días"
}

/**
 * La línea de estado de un calendario en "Tus calendarios en Sania" (mismos
 * textos que la web, app/(app)/configuracion/calendario/page.tsx).
 */
fun textoEstadoFuente(f: CalendarioTraido, hace: String, sincroniza: Boolean = true): String {
    val citas = "${f.citas} ${if (f.citas == 1) "cita importada" else "citas importadas"}"
    val base = when {
        f.enCurso -> "Importando… ${f.citas} citas hasta ahora (avanza sola cada 10 min)"
        f.importadoEn == null -> "Falta revisar e importar"
        f.esIcs -> "Importado $hace · ${f.citas} citas"
        sincroniza -> "Sincronizado $hace · $citas"
        else -> "$TEXTO_IMPORTADA_UNA_VEZ · $citas"
    }
    // Básico, ya importada: no hay sincronización ni otra importación; lo que quedó
    // pendiente se reintenta a mano tras ajustar el horario.
    val basico = !sincroniza && f.importadoEn != null && !f.enCurso
    val pend = when {
        f.sinCupo <= 0 -> ""
        basico -> " · ${f.sinCupo} sin cupo ($TEXTO_AJUSTA_Y_REINTENTA)"
        f.esIcs -> " · ${f.sinCupo} sin cupo (vuelve a subir el archivo tras revisar el horario)"
        else -> " · ${f.sinCupo} sin cupo o pendientes (se reintentan solas, cada vez con más espera)"
    }
    val rev = if (f.porRevisar > 0) " · ${f.porRevisar} por revisar" else ""
    return base + pend + rev
}

// ── Plan: Básico = UNA importación (web: lib/calendario/limite-plan.ts) ──
// Mismos textos que la web (lib/calendario/textos.ts).

/** El aviso ANTES de importar, en Básico. */
const val TEXTO_PLAN_UNA_IMPORTACION =
    "Tu plan incluye una importación. Para que los cambios de Google lleguen solos, pasa a Premium."

/** Básico que ya la usó: no puede traer otra agenda. */
const val TEXTO_PLAN_IMPORTACION_USADA =
    "Ya usaste la importación de tu plan. Para traer otra agenda o que los cambios de Google lleguen solos, pasa a Premium."

/** Básico: lo cumplible con lo que quedó sin cupo. */
const val TEXTO_AJUSTA_Y_REINTENTA =
    "ajusta el horario del profesional y toca “Reintentar las pendientes”, o agrégalas a mano en la agenda"

/** El botón para lo que no llegó (sin cupo / por revisar): no trae nada nuevo. */
const val BOTON_REINTENTAR_PENDIENTES = "Reintentar las pendientes"

/** En el reintento los ajustes no se editan: se aplican los de la importación (como la web). */
const val TEXTO_AJUSTES_REINTENTO =
    "Se usan los mismos ajustes de tu importación (fechas, pasadas, servicio, colores y recordatorios). Si ya ajustaste el horario del profesional, actualiza para ver cuáles entran ahora."

/** ¿La vista previa deja editar los ajustes? En el reintento, no (el servidor usa los de la importación). */
fun ajustesEditables(soloPendientes: Boolean): Boolean = !soloPendientes

/** La línea de una fuente importada en Básico (sin botón de sincronizar). */
const val TEXTO_IMPORTADA_UNA_VEZ = "Importada una vez — sincronización automática en Premium"

/** ¿Se pueden elegir más calendarios / conectar otra cuenta? (Básico que ya importó: no). */
fun puedeAgregarCalendarios(e: EstadoCalendarioExterno): Boolean = e.sincronizacion || !e.importacionUsada

/** Qué muestra cada calendario de "Tus calendarios en Sania" según el plan. */
data class AccionesFuente(
    /** Texto del botón de revisar/importar; null = no hay botón. */
    val revisar: String?,
    /** "↻ Sincronizar ahora" (solo planes con sincronización). */
    val sincronizar: Boolean,
    /** Línea con candado cuando el plan ya no deja importarlo. */
    val candado: String?,
    /** Básico, ya importada, con algo sin cupo o por revisar: "Reintentar las pendientes". */
    val reintentar: Boolean = false,
)

fun accionesFuente(f: CalendarioTraido, sincroniza: Boolean, leyendo: Boolean = false): AccionesFuente {
    val reintentar = !sincroniza && f.importadoEn != null && !f.enCurso && (f.sinCupo > 0 || f.porRevisar > 0)
    if (f.esIcs) return AccionesFuente(revisar = null, sincronizar = false, candado = null, reintentar = reintentar)
    val revisar = when {
        !f.puedeImportar -> null
        leyendo -> "Leyendo calendario…"
        f.importadoEn != null -> "Revisar de nuevo"
        else -> "Revisar e importar"
    }
    return AccionesFuente(
        revisar = revisar,
        sincronizar = sincroniza && f.importadoEn != null,
        candado = if (!f.puedeImportar && f.importadoEn == null) "🔒 Tu plan incluye una importación y ya la usaste." else null,
        reintentar = reintentar,
    )
}

/** Gemelo de textoQuitarFuente() de la web (lib/calendario/textos.ts). */
data class TextoQuitarFuente(val boton: String, val confirmar: String, val ayuda: String)

fun textoQuitarFuente(nombre: String, esIcs: Boolean): TextoQuitarFuente = if (esIcs) TextoQuitarFuente(
    boton = "Quitar archivo",
    confirmar = "¿Quitar el archivo \"$nombre\" de la lista? Las citas que ya se importaron se quedan en la agenda.",
    ayuda = "Al quitarlo, las citas ya importadas se quedan en la agenda.",
) else TextoQuitarFuente(
    boton = "Dejar de traer",
    confirmar = "¿Dejar de traer \"$nombre\"? Las citas que ya se importaron se quedan en la agenda; solo deja de sincronizar.",
    ayuda = "Al dejar de traerlo, las citas ya importadas se quedan en la agenda.",
)
