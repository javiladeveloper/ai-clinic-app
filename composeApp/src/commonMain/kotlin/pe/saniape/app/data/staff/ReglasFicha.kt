package pe.saniape.app.data.staff

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Reglas PURAS de la ficha y la agenda (sin red ni Compose), para poder
 * testearlas. Son gemelas de lo que la web ya hace; ninguna decide dinero ni
 * estados en la base: eso lo hacen los endpoints de staff de la web.
 */

// ── Fecha de la clínica ──

/**
 * Zona horaria de las clínicas (gemela de `getLocalToday()` de la web, que
 * fuerza America/Lima). Con la del teléfono, un celular con la zona mal puesta
 * (o en UTC) proponía "mañana" a partir de las 19:00 — el mismo bug que dejaba
 * los cobros de la noche fechados al día siguiente en la web.
 */
val ZONA_CLINICA: TimeZone = runCatching { TimeZone.of("America/Lima") }
    .getOrElse { TimeZone.currentSystemDefault() }

/** "Hoy" (AAAA-MM-DD) en la zona de la clínica. [ahora] inyectable para los tests. */
fun hoyClinicaIso(ahora: Instant = Clock.System.now()): String =
    ahora.toLocalDateTime(ZONA_CLINICA).date.aIso()

/** [baseIso] + [dias] días (AAAA-MM-DD). Si la base no es una fecha, se devuelve tal cual. */
fun sumarDiasIso(baseIso: String, dias: Int): String =
    runCatching { LocalDate.parse(baseIso.take(10)).plus(DatePeriod(days = dias)).aIso() }
        .getOrDefault(baseIso)

private fun LocalDate.aIso(): String =
    "$year-${monthNumber.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"

// ── Cambiar el estado de una sesión (menú ⋯) ──

/** Estados que se fijan desde el menú ⋯ de una sesión pendiente (como la web). */
val ESTADOS_MENU_SESION = listOf("Reprogramada", "No asistió", "Cancelada", "Otro")

/** Título del modal, con las mismas palabras que la ficha web. */
fun tituloCambioEstadoSesion(estado: String): String = when (estado) {
    "Reprogramada" -> "📅 Reprogramar sesión"
    "No asistió" -> "🚫 El paciente faltó"
    "Cancelada" -> "✗ Cancelar sesión"
    else -> "⋯ Otro motivo"
}

/** ¿El motivo es obligatorio? Igual que la web: solo en "Otro" (sin él no dice nada). */
fun motivoObligatorio(estado: String): Boolean = estado == "Otro"

/**
 * Valida el cambio antes de enviarlo. Devuelve el mensaje de error o null si
 * se puede confirmar. Reprogramar exige una fecha válida (la web lo rechaza con
 * "Selecciona una fecha válida para reprogramar").
 */
fun validarCambioEstadoSesion(estado: String, motivo: String, fecha: String?, hora: String?): String? {
    if (estado == "Reprogramada") {
        if (fecha == null || !Regex("""^\d{4}-\d{2}-\d{2}$""").matches(fecha)) return "Selecciona una fecha válida para reprogramar"
        if (hora != null && hora.isNotBlank() && !Regex("""^\d{2}:\d{2}$""").matches(hora)) return "Selecciona una hora válida"
    }
    if (motivoObligatorio(estado) && motivo.isBlank()) return "Escribe el motivo"
    return null
}

// ── Dar de alta ──

/** Texto de la confirmación de alta (gemelo del de la ficha web). */
fun textoConfirmarAlta(sesionesPendientes: Int?): String = when {
    sesionesPendientes != null && sesionesPendientes > 0 ->
        "Quedan $sesionesPendientes ${if (sesionesPendientes == 1) "sesión" else "sesiones"} sin realizar: " +
            "se cerrarán (no se cobran ni cuentan como atendidas). El historial se conserva."
    else -> "Se cierra el tratamiento. El historial se conserva."
}

// ── Encuesta de satisfacción al alta ──

/** El enlace público de la encuesta lo abre el paciente, así que va SIEMPRE a producción. */
const val SITIO_PUBLICO = "https://www.saniape.com"

/**
 * Enlace de WhatsApp al paciente (gemelo de `enlaceWhatsApp` de la web): menos
 * de 9 dígitos = no hay a quién escribir (null); un celular peruano de 9 dígitos
 * que empieza en 9 lleva el 51 delante o wa.me no abre su chat.
 */
fun enlaceWhatsApp(contacto: String?, texto: String? = null): String? {
    var digitos = contacto.orEmpty().filter { it.isDigit() }
    if (digitos.length < 9) return null
    if (digitos.length == 9 && digitos.startsWith("9")) digitos = "51$digitos"
    val base = "https://wa.me/$digitos"
    val t = texto?.trim().orEmpty()
    return if (t.isNotEmpty()) "$base?text=${codificarUrl(t)}" else base
}

/** El mensaje que se le manda al paciente (mismo texto que la ficha web). */
fun textoEncuestaAlta(nombrePaciente: String?, token: String, sitio: String = SITIO_PUBLICO): String {
    val nombre = nombrePaciente.orEmpty().trim().split(" ").firstOrNull().orEmpty()
    return "Hola $nombre 👋 Gracias por confiar en nosotros. ¿Nos cuentas cómo te atendimos? " +
        "Son 10 segundos: $sitio/encuesta/$token"
}

/** encodeURIComponent (UTF-8), sin depender de la plataforma. */
fun codificarUrl(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val ch = b.toInt().toChar()
        val libre = (ch in 'A'..'Z') || (ch in 'a'..'z') || (ch in '0'..'9') ||
            ch == '-' || ch == '_' || ch == '.' || ch == '!' || ch == '~' || ch == '*' || ch == '\'' || ch == '(' || ch == ')'
        if (b >= 0 && libre) append(ch)
        else {
            val v = b.toInt() and 0xFF
            append('%'); append("0123456789ABCDEF"[v shr 4]); append("0123456789ABCDEF"[v and 0xF])
        }
    }
}

// ── Ficha dada de baja ──

/** Un paciente dado de baja: su ficha se consulta, pero no acepta registros nuevos. */
fun fichaInactiva(estadoPaciente: String?): Boolean = estadoPaciente == "Inactivo"

// ── Completar una cita que evalúa ──

/**
 * ¿Hay que pedir quién atendió antes de completar? Gemelo de la regla del
 * servidor (`/api/staff/cita/completar`, código SIN_PROFESIONAL): la cita no
 * tiene profesional y quien completa no es un profesional vinculado (recepción
 * desde el móvil). Solo en Evaluación/Consulta: en una sesión el servidor no la
 * rechaza.
 */
fun pideProfesionalAlCompletar(tipoCita: String?, terapeutaCita: String?, miTerapeutaId: String?): Boolean =
    terapeutaCita.isNullOrBlank() && miTerapeutaId.isNullOrBlank() &&
        (tipoCita == "Evaluación" || tipoCita == "Consulta")
