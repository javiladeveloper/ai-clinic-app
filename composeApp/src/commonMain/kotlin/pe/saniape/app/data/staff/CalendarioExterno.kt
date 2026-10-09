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
)

private fun JsonObject.txt(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull == true
private fun JsonObject.ent(k: String): Int = (this[k] as? JsonPrimitive)?.intOrNull ?: 0
private fun JsonObject.lista(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

fun estadoCalendarioDe(o: JsonObject): EstadoCalendarioExterno = EstadoCalendarioExterno(
    disponible = o.bool("googleConfigurado") && o.bool("llaveConfigurada"),
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
fun textoEstadoFuente(f: CalendarioTraido, hace: String): String {
    val base = when {
        f.enCurso -> "Importando… ${f.citas} citas hasta ahora (avanza sola cada 10 min)"
        f.importadoEn == null -> "Falta revisar e importar"
        f.esIcs -> "Importado $hace · ${f.citas} citas"
        else -> "Sincronizado $hace · ${f.citas} ${if (f.citas == 1) "cita importada" else "citas importadas"}"
    }
    val pend = if (f.sinCupo <= 0) "" else if (f.esIcs)
        " · ${f.sinCupo} sin cupo (vuelve a subir el archivo tras revisar el horario)"
    else " · ${f.sinCupo} sin cupo o pendientes (se reintentan solas, cada vez con más espera)"
    val rev = if (f.porRevisar > 0) " · ${f.porRevisar} por revisar" else ""
    return base + pend + rev
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
