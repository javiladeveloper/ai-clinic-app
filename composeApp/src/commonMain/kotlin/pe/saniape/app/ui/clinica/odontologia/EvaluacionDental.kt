package pe.saniape.app.ui.clinica.odontologia

import pe.saniape.app.data.staff.OdontogramaRepo

// Completar una EVALUACIÓN DENTAL en un solo paso (29/09/2026, el cambio que la
// web hizo el 28/09 tras la prueba de Jonathan en la demo dental): desde la
// revisión del odontograma se completa sin otra ventana — quién atendió se
// resuelve solo (profesionalDeEvaluacionDental), el diagnóstico sale de los
// hallazgos y el tratamiento se crea con el presupuesto a la vista si todavía
// no existe. Gemelo de `lib/evaluacion-dental.ts` de la web.
//
// El DINERO no se calcula aquí: los tratamientos se crean con el mismo camino
// que el botón "Crear N tratamiento(s)" del presupuesto (endpoint
// /api/staff/tratamiento/accion 'crear' con hallazgoIds).

/** Lo que pasó con el tratamiento al completar la evaluación. */
sealed interface ResultadoTratamientoEvaluacion {
    /** Se creó ahora, con el presupuesto a la vista. */
    data class Creado(
        val cuantos: Int,
        /** Agendar la primera cita del (primer) tratamiento creado; null = sin id (cola offline). */
        val oferta: pe.saniape.app.ui.clinica.pacientes.OfertaPrimeraCita? = null,
    ) : ResultadoTratamientoEvaluacion
    /** Ya había uno de esta evaluación (cita_origen_id): no se duplica. */
    data object YaExistia : ResultadoTratamientoEvaluacion
    /** Boca sana, o todo lo pendiente ya tiene tratamiento. */
    data object SinHallazgos : ResultadoTratamientoEvaluacion
    /** Hay hallazgos pendientes, pero ninguno con servicio (nada que cobrar). */
    data object SinServicio : ResultadoTratamientoEvaluacion
    /** El odontólogo desmarcó todo en el presupuesto: se respeta. */
    data object Desmarcado : ResultadoTratamientoEvaluacion
    /** «Lo pensará»: no se crea nada; el presupuesto queda sin aceptar. */
    data object SinAceptar : ResultadoTratamientoEvaluacion
}

/**
 * Qué decidió el paciente sobre el presupuesto al cerrar el Diagnóstico
 * (auditoría de flujos 2026-10-24, odonto O1; `DecisionPresupuesto` de la web).
 * Antes completar creaba los tratamientos de TODO lo presupuestado: inflaba el
 * saldo pendiente y lo sacaba de «Presupuestos sin aceptar». Ahora nada se crea
 * sin un «✓ Acepta hoy» explícito.
 *  · [ACEPTA]     → se crean los tratamientos de lo aceptado (lo marcado).
 *  · [LO_PENSARA] → no se crea nada; se le manda el enlace para aceptar.
 */
enum class DecisionPresupuesto { ACEPTA, LO_PENSARA }

/**
 * Puente entre la revisión (RevisionPrevia) y el presupuesto que el
 * odontograma monta adentro. El presupuesto registra aquí "crear lo que tengo
 * marcado": así, si el odontólogo cambió un precio, desmarcó una línea o sumó
 * un servicio, se crea EXACTAMENTE eso y no un presupuesto por defecto.
 * null = el presupuesto aún no cargó sus servicios.
 * Gemelo de `components/odontologia/registro-presupuesto.ts`.
 */
class RegistroPresupuesto {
    var crear: (suspend () -> ResultadoTratamientoEvaluacion)? = null
    /**
     * «Lo pensará · enviar enlace»: congela lo marcado y arma el WhatsApp con el
     * enlace para aceptar (el «🔗 Enlace para aceptar» del panel web). null = el
     * presupuesto aún no cargó.
     */
    var enviarEnlace: (suspend () -> pe.saniape.app.data.staff.ResultadoEnlacePresupuesto)? = null
}

/** Un "no" que el odontólogo tiene que leer (la evaluación queda SIN completar). */
class ErrorEvaluacionDental(mensaje: String) : Exception(mensaje)

/**
 * Antes de completar CON «✓ Acepta hoy»: si esta evaluación todavía no tiene
 * tratamiento, lo crea con lo marcado en el presupuesto. Con «Lo pensará» no
 * crea nada ([decision]; ni una consulta, como la web).
 *
 * Idempotente: si ya existe uno con `cita_origen_id` = la cita (lo creó el
 * botón del presupuesto, o un intento anterior que falló al completar), no crea
 * nada. Uno Cancelado no cuenta (revertir la evaluación lo cancela).
 *
 * Lanza [ErrorEvaluacionDental] si no se pudo revisar o crear: la revisión queda
 * abierta y la cita SIN completar, para no dejar una evaluación a medias.
 */
suspend fun asegurarTratamientoDeEvaluacion(
    pacienteId: String,
    citaId: String,
    registro: RegistroPresupuesto,
    /** OBLIGATORIA: sin un «Acepta hoy» explícito no se crea nada. */
    decision: DecisionPresupuesto,
): ResultadoTratamientoEvaluacion {
    if (decision != DecisionPresupuesto.ACEPTA) return ResultadoTratamientoEvaluacion.SinAceptar
    when (OdontogramaRepo.hayTratamientoDeEvaluacion(pacienteId, citaId)) {
        true -> return ResultadoTratamientoEvaluacion.YaExistia
        null -> throw ErrorEvaluacionDental("no se pudo revisar si ya había tratamiento. Revisa la conexión e intenta de nuevo.")
        false -> Unit
    }
    val crear = registro.crear
        ?: throw ErrorEvaluacionDental("el presupuesto todavía está cargando; espera un momento.")
    return crear()
}

/** Cómo se cuenta al odontólogo lo que pasó con el tratamiento ('' = nada que decir). */
fun textoTratamientoEvaluacion(r: ResultadoTratamientoEvaluacion): String = when (r) {
    is ResultadoTratamientoEvaluacion.Creado ->
        if (r.cuantos == 1) "1 tratamiento creado" else "${r.cuantos} tratamientos creados"
    ResultadoTratamientoEvaluacion.YaExistia -> ""
    ResultadoTratamientoEvaluacion.SinHallazgos -> "Sin tratamiento: no hay hallazgos pendientes"
    ResultadoTratamientoEvaluacion.SinServicio ->
        "Sin tratamiento: los hallazgos marcados no tienen un servicio (asígnalo en el presupuesto)"
    ResultadoTratamientoEvaluacion.Desmarcado -> "Sin tratamiento: desmarcaste todo el presupuesto"
    ResultadoTratamientoEvaluacion.SinAceptar ->
        "Sin tratamiento: el paciente lo pensará. El presupuesto queda en «Presupuestos sin aceptar»"
}

/**
 * ¿Hay algo que presupuestar? (hallazgos pendientes sin tratamiento). Boca sana
 * o todo ya con tratamiento → un solo «✓ Completar evaluación», como la web.
 */
fun hayPresupuestoPendiente(hallazgos: List<pe.saniape.app.data.staff.DienteHallazgo>): Boolean =
    hallazgos.any { it.estado == "Pendiente" && it.tratamientoId == null }
