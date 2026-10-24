package pe.saniape.app.data.staff

/**
 * Una cita que alguien pidió VER (tocó el aviso de «cita nueva», la «siguiente»
 * del Inicio o una fila de la agenda de hoy): la agenda tiene que abrir su
 * detalle, no solo cambiar de pestaña.
 *
 * Reporte DALU (24/10/2026): recepción agendaba a la Dra. Katherine, ella veía la
 * cita, pero al tocarla «no la lleva a ningún lado» — se descartaba el id y solo
 * se cambiaba a la Agenda.
 *
 * [fecha] (ISO) viaja aparte porque la agenda tiene que posicionarse en ese día
 * ANTES de poder buscar la cita en su lista. null = no se sabe (el aviso no la trajo).
 */
data class CitaPedida(val id: String, val fecha: String?)

/** Qué hace la agenda con la [CitaPedida] según lo que ya tiene cargado. */
sealed interface PasoCitaPedida {
    /** Aún no: la agenda no terminó de cargar el día de la cita. */
    data object Esperar : PasoCitaPedida

    /** Está en la lista del día: se abre su detalle con lo ya cargado. */
    data class Abrir(val cita: CitaStaff) : PasoCitaPedida

    /**
     * El día ya cargó y no vino (filtro de profesional, otra sede, cita de equipo,
     * se movió de día…): hay que leerla por id antes de rendirse.
     */
    data object BuscarPorId : PasoCitaPedida
}

/**
 * Decide qué hacer con la cita pedida.
 *
 * [diaCargado] es el día cuya lista ya llegó completa (null = ninguno todavía, o se
 * cambió de día y la nueva aún no llega). [citas] es la lista CRUDA del día (con el
 * scope del profesional, sin los filtros de pantalla): si un filtro de especialidad
 * o de estado la oculta, igual se abre — quien la pidió tiene derecho a verla.
 */
fun resolverCitaPedida(pedida: CitaPedida, diaCargado: String?, citas: List<CitaStaff>): PasoCitaPedida {
    citas.firstOrNull { it.id == pedida.id }?.let { return PasoCitaPedida.Abrir(it) }
    if (diaCargado == null) return PasoCitaPedida.Esperar
    // Con fecha: hasta que no cargue ESE día, la ausencia no dice nada.
    if (pedida.fecha != null && diaCargado != pedida.fecha) return PasoCitaPedida.Esperar
    return PasoCitaPedida.BuscarPorId
}

/** Lo que se le dice a quien tocó una cita que no se puede mostrar. */
const val AVISO_CITA_NO_ENCONTRADA = "No encontramos esa cita en tu agenda"
const val AVISO_CITA_SIN_CONEXION = "No se pudo abrir la cita. Revisa tu conexión."

/** Resultado de leer la cita pedida por id. */
sealed interface DestinoCitaLeida {
    data class Abrir(val cita: CitaStaff) : DestinoCitaLeida
    data class Avisar(val mensaje: String) : DestinoCitaLeida
}

/**
 * La cita leída por id, ¿se muestra?
 *
 * La RLS ya la acota a la clínica; esto respeta además el scope de la agenda: el
 * profesional VINCULADO ([miTerapeutaId]) ve las suyas — las que atiende o en
 * las que está en el equipo ([equipo], ids de `cita_equipo`). Quien no está
 * vinculado (recepción, admin) ve todas, como en su agenda.
 *
 * [cita] null = no existe (se borró) o la RLS no la deja ver.
 */
fun destinoCitaLeida(cita: CitaStaff?, miTerapeutaId: String?, equipo: List<String> = emptyList()): DestinoCitaLeida {
    if (cita == null) return DestinoCitaLeida.Avisar(AVISO_CITA_NO_ENCONTRADA)
    if (miTerapeutaId == null || cita.terapeutaId == miTerapeutaId || miTerapeutaId in equipo) {
        return DestinoCitaLeida.Abrir(cita)
    }
    return DestinoCitaLeida.Avisar(AVISO_CITA_NO_ENCONTRADA)
}

/**
 * ¿Hace falta leer el equipo de la cita para decidir? Solo con agenda vinculada y
 * cuando la cita es de OTRO profesional (si es mía, o no hay scope, ya se sabe).
 */
fun hayQueLeerEquipo(cita: CitaStaff?, miTerapeutaId: String?): Boolean =
    cita != null && miTerapeutaId != null && cita.terapeutaId != miTerapeutaId

/**
 * Tocar el nombre del paciente cuando su ficha no se pudo abrir (no existe, sin
 * permiso, sin señal): nunca quedarse en silencio. Si se sabe de qué cita venía,
 * se vuelve a su detalle; si no, solo el aviso.
 */
const val AVISO_FICHA_NO_DISPONIBLE = "No pudimos abrir la ficha de este paciente"
