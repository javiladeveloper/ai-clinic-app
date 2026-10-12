package pe.saniape.app.data.staff

/**
 * Agendar una Sesión a un paciente SIN tratamiento (psicología) — gemelo de la web
 * (docs/sesion-paciente-sin-tratamiento.md, rama fix/sesion-paciente-sin-tratamiento).
 *
 * En una clínica que entra directo a sesiones (sin Consulta ni Evaluación) la
 * Sesión es la primera cita: el paciente nuevo no tiene tratamiento todavía y no
 * había por dónde agendarlo. Ahora se lo puede elegir; el formulario dice "aún no
 * tiene tratamiento" y ofrece crearlo ahí mismo (queda elegido y la cita sale como
 * Sesión #1). Las clínicas con Consulta/Evaluación no cambian: el paciente nuevo
 * entra por ahí y su tratamiento nace de la evaluación.
 */

/** ¿El flujo entra directo a sesiones? (preset de psicología: sin Consulta ni Evaluación) */
fun flujoDirectoASesiones(flujo: FlujoClinica): Boolean = !flujo.usaConsulta && !flujo.usaEvaluacion

/** Qué se muestra bajo el paciente de una Sesión cuando no tiene tratamiento activo. */
enum class AvisoSinTratamiento {
    /** Nada que avisar (tiene tratamiento, no es Sesión, flujo con Consulta/Evaluación, aún cargando…). */
    NINGUNO,

    /** "Este paciente aún no tiene tratamiento" + "＋ Crear tratamiento". */
    OFRECER_CREAR,

    /** Sin permiso de tratamientos: "pídeselo a su profesional o al Admin". */
    PEDIR_A_QUIEN_PUEDE,
}

/**
 * El aviso para el paciente elegido. [tratamientosCargados]: la lista de SUS
 * tratamientos llegó bien (si la consulta falló no se afirma que no tenga).
 * [puedeSesiones]: el permiso con el que se crean tratamientos (el servidor lo
 * exige igual).
 */
fun avisoSinTratamiento(
    tipo: String,
    directoASesiones: Boolean,
    pacienteElegido: Boolean,
    tratamientosCargados: Boolean,
    hayTratamientos: Boolean,
    puedeSesiones: Boolean,
): AvisoSinTratamiento = when {
    tipo != "Sesión" || !directoASesiones || !pacienteElegido || !tratamientosCargados || hayTratamientos ->
        AvisoSinTratamiento.NINGUNO
    puedeSesiones -> AvisoSinTratamiento.OFRECER_CREAR
    else -> AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE
}

/** Textos del aviso ([termino] = "paciente", "cliente"… según la clínica). */
fun tituloSinTratamiento(termino: String): String = "Este $termino aún no tiene tratamiento"

fun detalleSinTratamiento(aviso: AvisoSinTratamiento): String = when (aviso) {
    AvisoSinTratamiento.OFRECER_CREAR ->
        "Créalo aquí: queda elegido en la cita y esta se agenda como la Sesión #1."
    AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE ->
        "Para agendarle sesiones necesita un tratamiento: pídeselo a su profesional o al Admin."
    AvisoSinTratamiento.NINGUNO -> ""
}

/** Lo que dice el "Guardar" si se intenta agendar sin crear antes el tratamiento. */
fun mensajeFaltaTratamiento(aviso: AvisoSinTratamiento, termino: String): String? = when (aviso) {
    AvisoSinTratamiento.OFRECER_CREAR -> "${tituloSinTratamiento(termino)}: créalo para agendarle la sesión."
    AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE ->
        "${tituloSinTratamiento(termino)}: pídeselo a su profesional o al Admin."
    AvisoSinTratamiento.NINGUNO -> null
}

/**
 * Tras crear el tratamiento desde la cita: cuál queda elegido. El del id que
 * devolvió el servidor; sin id (respuesta vieja), el único que haya.
 */
fun tratamientoCreadoEnLista(lista: List<TratamientoRef>, idCreado: String?): TratamientoRef? =
    idCreado?.let { id -> lista.firstOrNull { it.id == id } } ?: lista.singleOrNull()
