package pe.saniape.app.data.staff

/**
 * Nueva cita de tipo Sesión para un paciente SIN tratamiento activo — gemelo de
 * `lib/cita-sin-tratamiento.ts` (web, rama fix/sesion-paciente-sin-tratamiento;
 * contrato en docs/sesion-paciente-sin-tratamiento.md).
 *
 * Una sesión va siempre dentro de un tratamiento: antes la app dejaba guardar una
 * cita "Sesión" huérfana (sin tratamiento, el servidor no crea la sesión). Ahora,
 * en TODAS las clínicas, el formulario lo dice y ofrece crearlo ahí mismo (queda
 * elegido y la cita sale como su Sesión #1), o a quién pedírselo. El alta rápida
 * del paciente en una Sesión, en cambio, SOLO en las clínicas que entran por
 * Sesión (psicología): con Consulta/Evaluación el paciente nuevo entra por ahí.
 */

/**
 * ¿La clínica (o la especialidad de la cita) entra SOLO por Sesión? Entre los
 * tipos que el formulario ofrece está Sesión y no hay Consulta ni Evaluación.
 */
fun entraSoloPorSesion(tiposVisibles: List<String>): Boolean =
    "Sesión" in tiposVisibles && "Consulta" !in tiposVisibles && "Evaluación" !in tiposVisibles

/** Qué se muestra bajo el paciente de una Sesión cuando no tiene tratamiento activo. */
enum class AvisoSinTratamiento {
    /** Nada que avisar (tiene tratamiento, no es Sesión, viene con uno prefijado, aún cargando…). */
    NINGUNO,

    /** "Este paciente aún no tiene tratamiento" + "＋ Crear tratamiento". */
    OFRECER_CREAR,

    /** Sin permiso de tratamientos: "pídeselo a su profesional o al Admin". */
    PEDIR_A_QUIEN_PUEDE,
}

/**
 * El aviso para el paciente elegido. [tratamientosCargados]: la lista de SUS
 * tratamientos llegó bien (si la consulta falló no se afirma que no tenga).
 * [tratamientoPrefijado]: la cita vino con su tratamiento (agendar tras crear uno).
 * [puedeSesiones]: el permiso con el que se gestionan tratamientos y sesiones.
 */
fun avisoSinTratamiento(
    tipo: String,
    pacienteElegido: Boolean,
    tratamientosCargados: Boolean,
    hayTratamientos: Boolean,
    tratamientoPrefijado: Boolean,
    puedeSesiones: Boolean,
): AvisoSinTratamiento = when {
    tipo != "Sesión" || !pacienteElegido || !tratamientosCargados || hayTratamientos || tratamientoPrefijado ->
        AvisoSinTratamiento.NINGUNO
    puedeSesiones -> AvisoSinTratamiento.OFRECER_CREAR
    else -> AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE
}

/** Título del aviso ([termino] = "paciente", "cliente"… según la clínica). */
fun tituloSinTratamiento(aviso: AvisoSinTratamiento, termino: String): String = when (aviso) {
    AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE -> "Este $termino necesita un tratamiento"
    else -> "Este $termino aún no tiene tratamiento"
}

/**
 * El detalle del aviso. [entrada]: el nombre de la otra puerta de la clínica
 * (su Consulta/Evaluación, con el nombre del flujo), o null si solo entra por Sesión.
 */
fun detalleSinTratamiento(aviso: AvisoSinTratamiento, entrada: String?): String = when (aviso) {
    AvisoSinTratamiento.OFRECER_CREAR ->
        "Una sesión va siempre dentro de un tratamiento. Créalo aquí: esta cita queda como su Sesión #1, " +
            "con la fecha, hora y profesional que ya elegiste." +
            (entrada?.let { " Si viene por algo nuevo, elige $it arriba." } ?: "")
    AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE ->
        "Pídeselo a su profesional o al Admin: una sesión va siempre dentro de un tratamiento." +
            (entrada?.let { " Si viene por algo nuevo, agéndale una $it." } ?: "")
    AvisoSinTratamiento.NINGUNO -> ""
}

/** Lo que dice "Guardar" si se intenta agendar la Sesión sin tratamiento (null = puede). */
fun mensajeFaltaTratamiento(aviso: AvisoSinTratamiento, termino: String): String? = when (aviso) {
    AvisoSinTratamiento.OFRECER_CREAR -> "Crea el tratamiento del $termino para agendar la sesión."
    AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE ->
        "El $termino necesita un tratamiento: pídeselo a su profesional o al Admin."
    AvisoSinTratamiento.NINGUNO -> null
}

/** Modo de cobro efectivo: el del servicio; si no fija uno válido, el de su especialidad. */
fun modoCobroServicio(p: ProcedimientoRef): String =
    p.modoCobro?.trim()?.takeIf { it == "simple" || it == "sesiones" || it == "unidades" }
        ?: if (p.usaSesiones) "sesiones" else "simple"

/**
 * Servicios que se ofrecen al crear el tratamiento DESDE una cita de Sesión: solo
 * los que van por sesiones (un pago único o por unidades no se agenda como
 * "Sesión #1") y de la especialidad de la cita si eligió una (los servicios sin
 * especialidad sirven siempre). `serviciosParaSesion` de la web.
 */
fun serviciosParaSesion(servicios: List<ProcedimientoRef>, especialidadId: String?): List<ProcedimientoRef> =
    servicios.filter { s ->
        modoCobroServicio(s) == "sesiones" &&
            (especialidadId.isNullOrBlank() || s.especialidadId == null || s.especialidadId == especialidadId)
    }

/**
 * Costo de la Sesión #1 de un tratamiento recién creado (`costoPrimeraSesion` de
 * la web): en un paquete la sesión no se cobra suelta (null); en "Sesión suelta",
 * su precio por sesión.
 */
fun costoPrimeraSesion(modalidad: String?, precioPorSesion: Double?): Double? =
    if (modalidad == "Sesión suelta") precioPorSesion else null

/**
 * Tras crear el tratamiento desde la cita: cuál queda elegido. El del id que
 * devolvió el servidor; sin id (respuesta vieja), el único que haya.
 */
fun tratamientoCreadoEnLista(lista: List<TratamientoRef>, idCreado: String?): TratamientoRef? =
    idCreado?.let { id -> lista.firstOrNull { it.id == id } } ?: lista.singleOrNull()
