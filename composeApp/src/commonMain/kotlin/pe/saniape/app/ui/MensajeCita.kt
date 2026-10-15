package pe.saniape.app.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * WhatsApp prearmado de una cita (el 💬 de la agenda: se abre el WhatsApp del
 * celular con el texto escrito; NO sale por la API ni cuesta). GEMELO de
 * `mensajeConfirmacionCita` de la web (lib/citas-atajos.ts): mismo texto.
 *
 * Centro OMER (2026-10-14) pidió nombre completo, fecha, hora, servicio y
 * dirección, y que al agendar diga "Hemos confirmado su cita en …". Antes salía
 * "el 2026-10-17 a las 3:00 PM" y, por un urlEncode roto, los emojis llegaban
 * como "�ﾟﾑﾋ".
 *
 * [recienAgendada] (la cita se creó hoy) = confirmación; si no, recordatorio.
 * Línea sin dato = no se escribe.
 */
fun mensajeCita(
    nombrePaciente: String?,
    fecha: String,
    hora: String?,
    clinica: String?,
    hoy: String,
    enlace: String? = null,
    servicio: String? = null,
    profesional: String? = null,
    direccion: String? = null,
    recienAgendada: Boolean = false,
): String {
    fun limpio(v: String?) = v?.trim().orEmpty()
    val nombre = nombreDeSaludo(nombrePaciente)?.let { nombreCompletoBonito(it) }.orEmpty()
    val completo = nombreCompletoBonito(nombrePaciente)
    val f = fecha.take(10)
    val dia = fechaHumana(f)
    val manana = runCatching { LocalDate.parse(hoy.take(10)).toEpochDays().plus(1).let { LocalDate.fromEpochDays(it).toString() } }.getOrNull()
    val cuando = when {
        dia.isEmpty() -> ""
        f == hoy.take(10) -> "hoy $dia"
        f == manana -> "mañana $dia"
        else -> dia
    }
    val horaTxt = if (hora.isNullOrBlank()) "" else horaHumana(hora)
    val enClinica = limpio(clinica).let { if (it.isEmpty()) "" else " en $it" }

    val encabezado = listOf(
        if (nombre.isNotEmpty()) "Hola $nombre 👋" else "Hola 👋",
        if (recienAgendada) "Hemos confirmado su cita$enClinica ✅" else "Le recordamos su cita$enClinica:",
    )
    val datos = listOfNotNull(
        completo.takeIf { it.isNotEmpty() }?.let { "🙋 Paciente: $it" },
        cuando.takeIf { it.isNotEmpty() }?.let { "📅 ${it.replaceFirstChar { c -> c.uppercase() }}" },
        horaTxt.takeIf { it.isNotEmpty() }?.let { "🕒 $it" },
        limpio(servicio).takeIf { it.isNotEmpty() }?.let { "🩺 $it" },
        limpio(profesional).takeIf { it.isNotEmpty() }?.let { "🧑‍⚕️ Con: $it" },
        limpio(direccion).takeIf { it.isNotEmpty() }?.let { "📍 $it" },
    )
    val cierre = if (recienAgendada) {
        listOfNotNull("Si necesita reprogramar, escríbanos por aquí.", enlace?.let { "Puede ver su cita aquí: $it" })
    } else {
        listOfNotNull("¿Nos confirma su asistencia?", enlace?.let { "Puede confirmar aquí: $it" })
    }
    return listOf(encabezado, datos, cierre).filter { it.isNotEmpty() }
        .joinToString("\n\n") { it.joinToString("\n") }
}

/** "JOSE LUIS DE LA CRUZ" → "Jose Luis de la Cruz" (casi todos llegan en mayúsculas). */
fun nombreCompletoBonito(nombre: String?): String {
    val menores = setOf("de", "del", "la", "las", "los", "y")
    return nombre.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .mapIndexed { i, p ->
            val min = p.lowercase()
            if (i > 0 && min in menores) min else min.replaceFirstChar { it.uppercase() }
        }.joinToString(" ")
}

/** "2026-10-17" → "sábado 17 de octubre" (como fechaHumana de la web). Inválida → "". */
fun fechaHumana(iso: String): String {
    val d = runCatching { LocalDate.parse(iso.take(10)) }.getOrNull() ?: return ""
    val dias = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    return "${dias[d.dayOfWeek.ordinal]} ${d.dayOfMonth} de ${meses[d.monthNumber - 1]}"
}

/** "15:00:00" → "3:00 p. m." (como horaHumana de la web, con espacios normales). */
fun horaHumana(hora24: String): String {
    val partes = hora24.trim().split(":")
    val h = partes.getOrNull(0)?.toIntOrNull() ?: return hora24.take(5)
    val m = (partes.getOrNull(1)?.toIntOrNull() ?: 0).toString().padStart(2, '0')
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12:$m ${if (h < 12) "a. m." else "p. m."}"
}

/**
 * ¿La cita se agendó HOY en el día de la clínica? [creada] es el created_at
 * (UTC, "2026-10-13T04:07:04.088816+00:00"); [hoy] "AAAA-MM-DD" de la zona.
 */
fun citaRecienAgendada(creada: String?, hoy: String, zona: TimeZone): Boolean {
    if (creada.isNullOrBlank()) return false
    val t = runCatching { Instant.parse(creada.trim().replace(' ', 'T')) }.getOrNull() ?: return false
    return t.toLocalDateTime(zona).date.toString() == hoy.take(10)
}
