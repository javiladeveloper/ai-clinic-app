package pe.saniape.app.data.staff

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * Reglas PURAS de la auditoría de fricciones de la agenda (2026-09-27), sin red
 * ni Compose, para poder testearlas. Gemelas de la web donde la web ya decide
 * (fechaSugeridaSiguienteSesion, metodoPagoPreferido).
 */

// ── "📅 Agendar siguiente" ──

/**
 * Fecha que se propone para la siguiente sesión (gemela de
 * `fechaSugeridaSiguienteSesion` de lib/sesiones-acciones.ts): [hoyIso] es el día
 * de la CLÍNICA (Lima); el salto es el intervalo de la serie del servicio si lo
 * tiene (>= 1), si no una semana.
 */
fun fechaSugeridaSiguienteSesion(hoyIso: String, intervaloDias: Int?): String {
    val n = if (intervaloDias != null && intervaloDias >= 1) intervaloDias else 7
    return sumarDiasIso(hoyIso, n)
}

/**
 * ¿Se ofrece "Agendar siguiente" tras completar una sesión? Solo si el
 * tratamiento sigue Activo, le quedan sesiones (con los contadores YA
 * sincronizados tras completar) y no tiene ya una sesión/cita futura agendada:
 * ofrecerla ahí crearía un duplicado.
 */
fun debeOfrecerSiguienteSesion(
    estadoTratamiento: String?, totalSesiones: Int, completadas: Int, hayFutura: Boolean,
): Boolean = estadoTratamiento == "Activo" && totalSesiones > completadas && !hayFutura

// ── Selectores de fecha ──

/**
 * "2026-10-02" → milisegundos UTC del inicio de ese día, que es lo que espera
 * `rememberDatePickerState(initialSelectedDateMillis = …)`. null si no es fecha
 * (el picker abre en hoy, como antes).
 */
fun isoAMillisUtc(iso: String?): Long? = runCatching {
    LocalDate.parse(iso!!.take(10)).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
}.getOrNull()

// ── Formulario de cita ──

/**
 * Título del formulario de cita: "Nueva cita" en el alta normal; con prefill
 * (Agendar siguiente, derivación, → Evaluación, control) nombra lo que se agenda
 * con el nombre de la clínica ("Agendar sesión", "Agendar diagnóstico").
 * Antes decía siempre "Nueva evaluación", también al agendar una sesión.
 */
fun tituloFormularioCita(conPrefill: Boolean, nombreTipo: String): String =
    if (!conPrefill || nombreTipo.isBlank()) "Nueva cita" else "Agendar ${nombreTipo.lowercase()}"

// ── Búsqueda de pacientes en el servidor ──

/**
 * Expresión regular (operador `imatch` de PostgREST, `~*` sin distinguir
 * mayúsculas) que tolera tildes y eñes: cada vocal acepta sus variantes con
 * tilde y la n acepta la ñ, así "jose" encuentra "José" y "nunez" a "Núñez".
 * Solo quedan letras y dígitos de lo escrito: ningún metacarácter del usuario
 * llega a la regex ni rompe el `or=(…)` de PostgREST. Espera una palabra de
 * [palabrasBusqueda].
 */
fun patronRegexSinTildes(palabra: String): String = buildString {
    for (ch in palabra.lowercase()) {
        if (!ch.isLetterOrDigit()) continue
        append(
            when (ch) {
                'a', 'á', 'à', 'ä', 'â' -> "[aáàäâAÁÀÄÂ]"
                'e', 'é', 'è', 'ë', 'ê' -> "[eéèëêEÉÈËÊ]"
                'i', 'í', 'ì', 'ï', 'î' -> "[iíìïîIÍÌÏÎ]"
                'o', 'ó', 'ò', 'ö', 'ô' -> "[oóòöôOÓÒÖÔ]"
                'u', 'ú', 'ù', 'ü', 'û' -> "[uúùüûUÚÙÜÛ]"
                'n', 'ñ' -> "[nñNÑ]"
                else -> ch.toString()
            }
        )
    }
}

/** Palabras útiles de la búsqueda (sin vacías, máx. 4 para no armar un filtro enorme). */
fun palabrasBusqueda(q: String): List<String> =
    q.trim().split(Regex("\\s+")).map { p -> p.filter { it.isLetterOrDigit() } }.filter { it.isNotBlank() }.take(4)

// ── Método de pago por defecto ──

/**
 * Método con el que arranca un cobro (gemela de `metodoPagoPreferido` de la web):
 * el último de ESE paciente; si no, el último del usuario en este teléfono; si
 * no, "Efectivo". Nunca propone uno que la clínica no ofrece ([disponibles]).
 */
fun elegirMetodoPago(
    delPaciente: String?, delUsuario: String?, disponibles: List<String>, porDefecto: String = "Efectivo",
): String {
    fun vale(m: String?) = !m.isNullOrBlank() && m in disponibles
    return when {
        vale(delPaciente) -> delPaciente!!
        vale(delUsuario) -> delUsuario!!
        vale(porDefecto) || disponibles.isEmpty() -> porDefecto
        else -> disponibles.first()
    }
}

/** Mapa paciente → método guardado como texto ("id=metodo" por línea). */
fun decodificarMetodosPorPaciente(crudo: String?): LinkedHashMap<String, String> {
    val m = LinkedHashMap<String, String>()
    crudo?.lineSequence()?.forEach { linea ->
        val i = linea.indexOf('=')
        if (i > 0 && i < linea.length - 1) m[linea.substring(0, i)] = linea.substring(i + 1)
    }
    return m
}

/**
 * Agrega (o mueve al final, como el más reciente) el método de [pacienteId] y
 * recorta a los [max] más recientes. Devuelve el texto a guardar.
 */
fun recordarMetodoEnMapa(crudo: String?, pacienteId: String, metodo: String, max: Int = 300): String {
    val m = decodificarMetodosPorPaciente(crudo)
    m.remove(pacienteId)
    m[pacienteId] = metodo.replace("\n", " ")
    val claves = m.keys.toList()
    if (claves.size > max) claves.take(claves.size - max).forEach { m.remove(it) }
    return m.entries.joinToString("\n") { "${it.key}=${it.value}" }
}

// ── "📅 Agendar control" ──

/**
 * Fecha con la que abre "Agendar control": la del próximo control que el
 * profesional dejó anotado en el tratamiento, si es una fecha válida de hoy en
 * adelante; si no (vacía, mal escrita o ya pasada), hoy. Antes abría siempre
 * hoy, y recepción tenía que ir a buscar la fecha a la ficha.
 */
fun fechaParaAgendarControl(proximoControl: String?, hoyIso: String): String {
    val f = proximoControl?.trim()?.take(10)
    return if (f != null && isoAMillisUtc(f) != null && f >= hoyIso) f else hoyIso
}

/** "2026-10-02" → "vie 02/10" (para avisos cortos). Si no es fecha, tal cual. */
fun fechaLegibleCorta(iso: String): String = runCatching {
    val d = LocalDate.parse(iso.take(10))
    val dia = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")[d.dayOfWeek.ordinal]
    "$dia ${d.dayOfMonth.toString().padStart(2, '0')}/${d.monthNumber.toString().padStart(2, '0')}"
}.getOrDefault(iso)
