package pe.saniape.app.data.staff

/**
 * CONTROLES POST-TRATAMIENTO (gemelo de lib/controles-pendientes.ts de la web).
 *
 * Decisión del dueño (2026-10-08): un tratamiento de SERVICIO ÚNICO o por
 * UNIDADES con controles pendientes queda ABIERTO ('Activo', "En control")
 * hasta que se complete, cancele o borre el ÚLTIMO control. El estado lo
 * decide el SERVIDOR (los endpoints de cita y tratamiento/accion);
 * esto es solo para PINTAR el recorrido, el badge y qué botones se ofrecen.
 *
 * Un control es una cita con el marcador `[control:N]` en las notas.
 */

/** Lo mínimo de una cita del tratamiento para razonar sobre sus controles. */
data class CitaCtl(val id: String, val tipo: String?, val estado: String?, val notas: String?)

private val RE_CITA_CONTROL = Regex("""\[control:\d+]""")

fun esCitaDeControl(notas: String?): Boolean = notas != null && RE_CITA_CONTROL.containsMatchIn(notas)

/** Control que todavía falta atender (Completado, cancelado o "no asistió" = resuelto). */
fun esControlPendiente(c: CitaCtl): Boolean =
    esCitaDeControl(c.notas) && (c.estado == "Pendiente" || c.estado == "Confirmada")

data class ResumenControles(val total: Int, val hechos: Int, val pendientes: Int)

fun resumenControles(citas: List<CitaCtl>): ResumenControles {
    val vivos = citas.filter { esCitaDeControl(it.notas) && it.estado != "Cancelada" }
    val pendientes = vivos.count { esControlPendiente(it) }
    val hechos = vivos.count { it.estado == "Completada" }
    return ResumenControles(hechos + pendientes, hechos, pendientes)
}

/**
 * ¿El servicio ya se realizó? Con controles pendientes el tratamiento sigue
 * 'Activo' y el estado solo ya no lo dice. Mismas señales que la web: cerrado
 * (Completado/Alta), escalera de controles arrancada, o una cita propia
 * completada que no es control, ni evaluación, ni la de origen del plan.
 */
fun servicioRealizadoSegun(t: TratamientoPaciente, citas: List<CitaCtl>): Boolean {
    if (t.estado == "Completado" || t.estado == "Alta") return true
    if (!t.controlAnclaje.isNullOrBlank() || t.controlIndice > 0) return true
    return citas.any {
        it.estado == "Completada" && it.tipo != "Evaluación" && !esCitaDeControl(it.notas) && it.id != t.citaOrigenId
    }
}

/**
 * "Realizado" para PINTAR: sin controles pendientes manda el estado (así
 * "↩ Revertir" sigue funcionando como siempre); con controles pendientes, las
 * señales de [servicioRealizadoSegun].
 */
fun realizadoParaRecorrido(t: TratamientoPaciente, citas: List<CitaCtl>): Boolean {
    if (t.estado == "Completado" || t.estado == "Alta") return true
    return resumenControles(citas).pendientes > 0 && servicioRealizadoSegun(t, citas)
}

/** Realizado y con controles por atender: la UI dice "En control" (no "Activo"/"Por hacer"). */
fun enControl(t: TratamientoPaciente, citas: List<CitaCtl>): Boolean {
    val espera = t.tipo == TipoTratamiento.SERVICIO_UNICO || t.tipo == TipoTratamiento.UNIDADES
    if (!espera || t.estado != "Activo") return false
    return resumenControles(citas).pendientes > 0 && servicioRealizadoSegun(t, citas)
}

/** "Control", "Controles" o el avance "Control 1/2" (como etiquetaControles de la web). */
fun etiquetaControles(r: ResumenControles): String =
    if (r.pendientes == 0) (if (r.total > 1) "Controles" else "Control")
    else if (r.total > 1) "Control ${r.hechos}/${r.total}" else "Control"
