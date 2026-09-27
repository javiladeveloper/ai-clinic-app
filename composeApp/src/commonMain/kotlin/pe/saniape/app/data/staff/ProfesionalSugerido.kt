package pe.saniape.app.data.staff

/**
 * Qué profesional viene puesto al agendar la cita de un tratamiento, y con qué
 * fecha abre "Nueva cita" desde la agenda.
 *
 * GEMELO de `lib/profesional-sugerido.ts` y `lib/prefill-cita.ts`
 * (fechaParaNuevaCita) de la web. Reglas puras, con tests.
 *
 * Pedido de Jonathan (2026-09-26): "si el paciente ya tuvo un diagnóstico, al
 * agendar la intervención el profesional debería ser el mismo por defecto; si
 * recepción quiere, lo cambia".
 *
 * Orden: el profesional del TRATAMIENTO (basta que siga activo) → el de la
 * EVALUACIÓN/diagnóstico más reciente (primero una de la misma especialidad),
 * si sigue activo y atiende esa especialidad → nada (el formulario sigue como
 * siempre). Nunca pisa una elección hecha a mano: eso lo cuida la pantalla.
 */

enum class OrigenSugerencia { Tratamiento, Diagnostico }

data class SugerenciaProfesional(val id: String, val origen: OrigenSugerencia) {
    val texto: String
        get() = if (origen == OrigenSugerencia.Diagnostico) "Mismo profesional de su diagnóstico"
        else "Profesional de su tratamiento"
}

/** Una cita que evaluó al paciente (completada). [hora] "HH:MM" o "HH:MM:SS". */
data class EvaluacionPrevia(
    val terapeutaId: String?,
    val especialidadId: String?,
    val fecha: String,
    val hora: String? = null,
)

/**
 * ¿Puede atender? [activos] = ids de profesionales activos con sus especialidades
 * (vacía = sin restricción, igual que la web). Sin [especialidadId] basta con estar activo.
 */
fun puedeAtender(terapeutaId: String?, activos: Map<String, List<String>>, especialidadId: String?): Boolean {
    if (terapeutaId.isNullOrBlank()) return false
    val esps = activos[terapeutaId] ?: return false
    if (especialidadId.isNullOrBlank() || esps.isEmpty()) return true
    return especialidadId in esps
}

fun profesionalSugerido(
    tratamientoTerapeutaId: String?,
    evaluaciones: List<EvaluacionPrevia>,
    especialidadId: String?,
    activos: Map<String, List<String>>,
): SugerenciaProfesional? {
    if (puedeAtender(tratamientoTerapeutaId, activos, null)) {
        return SugerenciaProfesional(tratamientoTerapeutaId!!, OrigenSugerencia.Tratamiento)
    }
    fun clave(e: EvaluacionPrevia) = "${e.fecha} ${(e.hora ?: "").take(5)}"
    val recientes = evaluaciones.filter { !it.terapeutaId.isNullOrBlank() }.sortedByDescending { clave(it) }
    val mismaEsp = if (especialidadId.isNullOrBlank()) emptyList()
    else recientes.filter { it.especialidadId == especialidadId }
    for (e in mismaEsp + recientes) {
        if (puedeAtender(e.terapeutaId, activos, especialidadId)) {
            return SugerenciaProfesional(e.terapeutaId!!, OrigenSugerencia.Diagnostico)
        }
    }
    return null
}

/**
 * Fecha con la que abre "Nueva cita" desde la agenda: el día que se está
 * mirando si es futuro; hoy si es hoy o ya pasó (un día pasado no es un
 * espacio que se esté buscando).
 */
fun fechaParaNuevaCita(seleccionada: String?, hoy: String): String {
    val s = seleccionada?.take(10)
    if (s == null || !Regex("""\d{4}-\d{2}-\d{2}""").matches(s)) return hoy
    return if (s > hoy) s else hoy
}

/**
 * Hora inicial de una cita nueva: un día futuro arranca 09:00 (como la web);
 * hoy, la próxima hora en punto ([horaHoy]).
 */
fun horaInicialNuevaCita(fecha: String, hoy: String, horaHoy: String): String =
    if (fecha > hoy) "09:00" else horaHoy
