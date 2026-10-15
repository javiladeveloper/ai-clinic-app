package pe.saniape.app.data.staff

import kotlinx.coroutines.CancellationException

/**
 * NUTRICIÓN — ¿la Consulta que se completa es la PRIMERA (la que evalúa) o un
 * CONTROL del paciente que ya está en seguimiento? Gemelo PURO de
 * `lib/nutricion-control.ts` de la web (2026-10-15).
 *
 * El preset de nutrición es "Consulta → Controles → Alta": sin Evaluación
 * aparte, `esCitaQueEvalua` toma TODA Consulta como la que evalúa, y cada
 * control mensual pedía diagnóstico como la primera vez.
 *
 * Regla (solo citas de nutrición, por especialidad — quien llama lo decide con
 * [citaEsNutricion]; nunca por preset, que también usan medicina y gineco):
 * una Consulta es CONTROL si el paciente ya está en seguimiento:
 *  · está ligada a un tratamiento que NO nació de ella;
 *  · ya tiene un plan alimentario de otra atención anterior (o de la ficha, de
 *    un día anterior); el que se hizo EN esta cita no cuenta: es su primera;
 *  · ya tuvo otra atención de nutrición COMPLETADA antes que esta.
 * Si no, es la primera: evalúa (diagnóstico + plan), como siempre.
 *
 * El control se completa como cualquier consulta de seguimiento (sin pedir
 * diagnóstico) y ofrece "⚖️ Registrar medidas" ([ofrecerMedidasTrasCompletar]).
 * Las lecturas viven en [NutricionControlRepo].
 */

/** Lo mínimo de la cita para decidir. */
data class CitaNutricionControl(
    val id: String,
    val tipo: String?,
    val fecha: String,
    val hora: String? = null,
    val tratamientoId: String? = null,
)

/** Una atención de nutrición ya completada del paciente. */
data class AtencionNutricion(val id: String, val fecha: String, val hora: String? = null)

/** Un plan alimentario: de qué cita salió (null = hecho desde la ficha) y su fecha. */
data class PlanNutricionRef(val citaId: String?, val fecha: String)

/** Lo que ya se sabe del seguimiento del paciente (de [armarSeguimientoNutricion]). */
data class SeguimientoNutricion(
    /** Atenciones de NUTRICIÓN del paciente ya completadas (puede incluir la propia: se descarta). */
    val atenciones: List<AtencionNutricion> = emptyList(),
    /** Sus planes alimentarios. */
    val planes: List<PlanNutricionRef> = emptyList(),
    /** Tratamiento al que está ligada la cita → la cita de la que nació (null = sin cita de origen). */
    val origenTratamiento: Map<String, String?>? = null,
)

private fun momento(fecha: String, hora: String?): String = "${fecha.take(10)} ${(hora ?: "").take(5)}"

/** ¿Esta Consulta de nutrición es un CONTROL (no la primera, que evalúa)? Ver la regla arriba. */
fun esControlDeNutricion(cita: CitaNutricionControl, s: SeguimientoNutricion): Boolean {
    if (cita.tipo != "Consulta") return false
    val fecha = cita.fecha.take(10)
    val trat = cita.tratamientoId
    val origen = s.origenTratamiento
    if (!trat.isNullOrBlank() && origen != null && origen.containsKey(trat) && origen[trat] != cita.id) return true
    // Un plan de ANTES (o del mismo día, de otra atención): el de un control
    // posterior no convierte en control a la primera consulta completada tarde.
    if (s.planes.any { p ->
            val f = p.fecha.take(10)
            if (p.citaId != null) p.citaId != cita.id && f <= fecha else f < fecha
        }) return true
    val yo = momento(cita.fecha, cita.hora)
    return s.atenciones.any { a -> a.id != cita.id && momento(a.fecha, a.hora) < yo }
}

/**
 * ¿La Consulta de nutrición ya completada lleva el chip "🩺 Falta plan"? No si
 * es un control ni si el paciente ya tiene un plan alimentario (en nutrición
 * ESE es el plan). [tienePlanTratamiento] = la regla de siempre.
 */
fun faltaPlanNutricion(cita: CitaNutricionControl, s: SeguimientoNutricion, tienePlanTratamiento: Boolean): Boolean {
    if (tienePlanTratamiento) return false
    if (s.planes.isNotEmpty()) return false
    return !esControlDeNutricion(cita, s)
}

/**
 * Tope de filas por lectura. Si una respuesta lo ALCANZA no se decide con
 * datos a medias: null y quien llama sigue con la regla de siempre.
 */
const val TOPE_SEGUIMIENTO = 1000

/** Fila cruda de `citas` (completadas) para armar el seguimiento. */
data class FilaCitaNutricion(
    val id: String,
    val pacienteId: String,
    val fecha: String,
    val hora: String? = null,
    val especialidadId: String? = null,
    val terapeutaId: String? = null,
)

/** Fila cruda de `planes_alimentacion`. */
data class FilaPlanNutricion(val pacienteId: String, val citaId: String?, val fecha: String)

/**
 * Filas leídas → seguimiento por paciente (gemelo del armado de
 * `leerSeguimientoNutricion`). [citas] null = no se pudo leer → null.
 * [planes] null = sin permiso o falló: se decide solo con las atenciones.
 * [origen] null = no se pudieron leer los tratamientos (no inventa controles).
 * Una lectura que llega al [TOPE_SEGUIMIENTO] → null.
 */
fun armarSeguimientoNutricion(
    pacienteIds: List<String>,
    citas: List<FilaCitaNutricion>?,
    planes: List<FilaPlanNutricion>?,
    origen: Map<String, String?>?,
    esNutricion: (FilaCitaNutricion) -> Boolean,
): Map<String, SeguimientoNutricion>? {
    val ids = pacienteIds.filter { it.isNotBlank() }.distinct()
    if (ids.isEmpty()) return emptyMap()
    if (citas == null || citas.size >= TOPE_SEGUIMIENTO) return null
    if (planes != null && planes.size >= TOPE_SEGUIMIENTO) return null
    val origenOk = origen ?: emptyMap()
    return ids.associateWith { id ->
        SeguimientoNutricion(
            atenciones = citas.filter { it.pacienteId == id && esNutricion(it) }
                .map { AtencionNutricion(it.id, it.fecha, it.hora) },
            planes = planes.orEmpty().filter { it.pacienteId == id }.map { PlanNutricionRef(it.citaId, it.fecha) },
            origenTratamiento = origenOk,
        )
    }
}

/**
 * La parte de [abreEvaluacionAlCompletar] que no necesita leer nada: true/false
 * si ya se sabe; null = es una Consulta de nutrición y hay que mirar el
 * seguimiento (solo entonces se lee, y se muestra "Abriendo…").
 */
fun abreEvaluacionSinLeer(
    tipo: String?,
    pacienteId: String?,
    evalua: Boolean,
    dental: Boolean,
    citaEsNutricion: Boolean,
): Boolean? {
    if (!evalua) return false
    if (tipo != "Consulta" || pacienteId.isNullOrBlank() || dental || !citaEsNutricion) return true
    return null
}

/**
 * Al tocar "✓ Completar": ¿se abre "✓ Completar evaluación"? Gemelo de
 * `abreEvaluacionAlCompletar` de la web.
 *  · [evalua] = esCitaQueEvalua de la cita (la regla de siempre): sin eso, no;
 *  · lo dental, o una cita que no es de nutrición: exactamente como antes;
 *  · la Consulta de nutrición evalúa salvo que sea un CONTROL. Una lectura
 *    SOLO en ese caso; si falla (null o excepción) evalúa como siempre.
 */
suspend fun abreEvaluacionAlCompletar(
    cita: CitaNutricionControl,
    pacienteId: String?,
    evalua: Boolean,
    dental: Boolean,
    citaEsNutricion: Boolean,
    leerSeguimiento: suspend () -> SeguimientoNutricion?,
): Boolean {
    abreEvaluacionSinLeer(cita.tipo, pacienteId, evalua, dental, citaEsNutricion)?.let { return it }
    val s = try {
        leerSeguimiento()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    return !(s != null && esControlDeNutricion(cita, s))
}

/**
 * Ya decidido [abreEvaluacionAlCompletar]: ¿el cierre abre una ventana (la de
 * la evaluación, o la de la Sesión)? false = se completa directo (la Consulta
 * de seguimiento, el control de nutrición). "✓ Completar" de la tarjeta y
 * "✓ Sí vino" de las vencidas, como la web (citas/page.tsx).
 */
fun abreVentanaAlCompletar(abreEvaluacion: Boolean, tipo: String?): Boolean =
    abreEvaluacion || tipo == "Sesión"

// ─────────────────────────────────────────────────────────────────────────────
// "📅 Agendar siguiente control" — el control de un paciente PROPIO.
//
// 'agendar' es de recepción. La nutricionista ("Médico / Especialista":
// sesiones sí, agendar no) igual puede dejarle el próximo control a SU
// paciente: /api/staff/cita/crear lo acepta con el MISMO criterio
// (permiteControlPropioNutricion). Acotado a NUTRICIÓN a propósito: en fisio el
// agendar=false del rol es decisión de la clínica.
// ─────────────────────────────────────────────────────────────────────────────

/** Lo mínimo de una cita / tratamiento / plan para saber quién atendió. */
data class FilaAtendio(val terapeutaId: String?, val estado: String? = null)

/** ¿Este profesional ya atendió a este paciente? (una cita completada, un tratamiento o un plan alimentario suyos) */
fun atendioAlPaciente(
    miTerapeutaId: String?,
    citas: List<FilaAtendio> = emptyList(),
    tratamientos: List<FilaAtendio> = emptyList(),
    planes: List<FilaAtendio> = emptyList(),
): Boolean {
    if (miTerapeutaId.isNullOrBlank()) return false
    return citas.any { it.terapeutaId == miTerapeutaId && it.estado == "Completada" } ||
        tratamientos.any { it.terapeutaId == miTerapeutaId && it.estado != "Eliminado" } ||
        planes.any { it.terapeutaId == miTerapeutaId }
}

/**
 * ¿Puede crear ESTA Consulta sin el permiso 'agendar'? Todo a la vez:
 * Consulta · 'sesiones' · es profesional y la cita es SUYA · especialidad de
 * nutrición (por rubro) · ya atendió al paciente. (El gate del servidor.)
 */
fun permiteControlPropioNutricion(
    tipo: String?,
    puedeSesiones: Boolean,
    miTerapeutaId: String?,
    terapeutaCita: String?,
    especialidadEsNutricion: Boolean,
    atendioAntes: Boolean,
): Boolean = tipo == "Consulta" && puedeSesiones && !miTerapeutaId.isNullOrBlank() &&
    terapeutaCita == miTerapeutaId && especialidadEsNutricion && atendioAntes

/**
 * Los rubros de la especialidad de la cita (o, sin ella, los de TODAS las del
 * profesional) → ¿es de nutrición? Lo que no se pudo leer (lista vacía) es NO.
 */
fun especialidadEsNutricionPorRubros(rubros: List<String?>): Boolean =
    rubros.isNotEmpty() && rubros.all { it == "nutricion" }

/**
 * ¿Se ofrece "📅 Agendar siguiente control" al guardar el plan alimentario?
 * Con 'agendar' (recepción), o con el MISMO criterio que el endpoint para el
 * profesional sin 'agendar': 'sesiones' + es profesional + ya atendió al
 * paciente (Enfermera / Técnica, sin ficha de profesional, no lo ven). Nunca
 * en solo lectura, con la ficha de baja, ni si ya tiene una cita por delante.
 */
fun ofreceAgendarControlNutri(
    puedeAgendar: Boolean,
    puedeSesiones: Boolean,
    miTerapeutaId: String?,
    atendioAntes: Boolean,
    soloLectura: Boolean,
    inactivo: Boolean,
    citasPorDelante: Int,
): Boolean {
    if (soloLectura || inactivo || citasPorDelante > 0) return false
    return puedeAgendar || (puedeSesiones && !miTerapeutaId.isNullOrBlank() && atendioAntes)
}
