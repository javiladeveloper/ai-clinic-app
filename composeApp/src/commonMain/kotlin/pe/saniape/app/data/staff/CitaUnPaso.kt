package pe.saniape.app.data.staff

// «Crear tratamiento y agendar cita» en UN toque (dueño, 24/10/2026).
// Gemelo de `lib/cita-un-paso.ts` (web).
//
// En «Nueva cita» de tipo Sesión, un paciente sin tratamiento → «＋ Crear
// tratamiento» abre el formulario del tratamiento (ModalCrearTratamiento). Antes,
// al crearlo se volvía a la cita y había que tocar «Guardar cita»: dos pantallas
// para una sola decisión. Ahora el formulario del tratamiento muestra abajo la
// cita que ya se eligió (fecha, hora, profesional, duración), deja cambiarla ahí
// mismo, y su botón crea el tratamiento Y guarda la cita con el MISMO guardado de
// PantallaCrearCita (la cita queda como su Sesión #1 / su atención / su
// intervención, según el modo del servicio).
//
// Estas reglas deciden qué ofrece ese bloque y en qué orden se hacen las cosas.
// Viven acá (con tests) y no dentro de los formularios.

/**
 * Qué se puede hacer con la cita desde el formulario del tratamiento:
 *
 * - [Estado.LISTO]: «Crear tratamiento y agendar cita» guarda las dos cosas.
 * - [Estado.FALTA]: a la cita le falta algo que se resuelve EN el bloque (la hora,
 *   una hora que ya pasó, un horario ocupado): el botón queda apagado y el bloque
 *   dice qué falta. «Solo crear tratamiento» sigue disponible.
 * - [Estado.NO]: lo que falta NO se elige en el bloque (p. ej. el enlace de la
 *   videollamada): el botón vuelve a ser «Crear tratamiento» de siempre y la cita
 *   se termina en su formulario.
 */
data class EstadoCitaUnPaso(val estado: Estado, val motivo: String?) {
    enum class Estado { LISTO, FALTA, NO }

    companion object {
        val LISTO = EstadoCitaUnPaso(Estado.LISTO, null)
    }
}

/** `estadoCitaUnPaso` de la web. */
fun estadoCitaUnPaso(
    fecha: String,
    hora: String,
    /** La fecha y hora elegidas ya pasaron. */
    horaPasada: Boolean,
    /** Marcó «Esta cita ya ocurrió (la registro después)». */
    regularizacion: Boolean,
    /** El horario no está disponible (DisponibilidadRepo, bloqueante). */
    conflicto: String?,
    /** Algo de la cita que solo se corrige en su formulario (null = nada). */
    fueraDelBloque: String?,
): EstadoCitaUnPaso {
    val falta = EstadoCitaUnPaso.Estado.FALTA
    if (!fueraDelBloque.isNullOrBlank()) return EstadoCitaUnPaso(EstadoCitaUnPaso.Estado.NO, fueraDelBloque)
    if (fecha.isBlank()) return EstadoCitaUnPaso(falta, "Falta la fecha de la cita")
    if (hora.isBlank()) return EstadoCitaUnPaso(falta, "Falta la hora de la cita")
    if (horaPasada && !regularizacion) {
        return EstadoCitaUnPaso(falta, "Esa hora ya pasó: elige otra o marca que la cita ya ocurrió")
    }
    // En una regularización (ya ocurrió) el horario del profesional no frena: igual que «Guardar cita».
    if (!conflicto.isNullOrBlank() && !(horaPasada && regularizacion)) return EstadoCitaUnPaso(falta, conflicto)
    return EstadoCitaUnPaso.LISTO
}

/**
 * ¿La cita de [fecha] a la [hora] ("HH:MM") ya pasó, con "ahora" en la zona de su
 * sede ([hoy] AAAA-MM-DD y [minutosAhora] desde la medianoche)? Un minuto que
 * corre no cuenta: la cita de las 10:00 a las 10:00 todavía no pasó (como
 * `fechaHoraEnZona(...) < new Date()` de la web, con minutos enteros).
 */
fun horaDeCitaYaPaso(fecha: String, hora: String, hoy: String, minutosAhora: Int): Boolean {
    if (fecha.isBlank() || hora.isBlank()) return false
    if (fecha != hoy) return fecha < hoy
    val p = hora.split(":")
    val min = (p.getOrNull(0)?.trim()?.toIntOrNull() ?: return false) * 60 + (p.getOrNull(1)?.trim()?.take(2)?.toIntOrNull() ?: 0)
    return min < minutosAhora
}

/**
 * Cambiaron a mano la FECHA DE INICIO del tratamiento: ¿a qué día pasa la cita?
 * Un tratamiento programado para más adelante empieza con su cita (como hacía al
 * volver a la Nueva cita). Uno que «ya empezó» (fecha pasada: se carga una
 * historia en papel) no arrastra la cita al pasado: la cita sigue donde está. El
 * paciente que ya está aquí (walk-in) se atiende hoy: no se mueve.
 * null = la cita no cambia. `fechaCitaPorInicio` de la web.
 */
fun fechaCitaPorInicio(fechaInicio: String?, fechaCita: String, hoy: String, ahoraAqui: Boolean = false): String? {
    if (ahoraAqui || fechaInicio.isNullOrBlank() || fechaInicio == fechaCita) return null
    return if (fechaInicio >= hoy) fechaInicio else null
}

/** Resultado del alta del tratamiento (guardarTratamientoNuevo). */
data class ResultadoAltaTratamiento(
    val ok: Boolean,
    val id: String? = null,
    val error: String? = null,
    /** Sin señal: quedó en la cola offline (todavía sin id). */
    val encolada: Boolean = false,
)

/** `ResultadoUnPaso` de la web, más el caso sin señal de la app. */
sealed class ResultadoUnPaso<out T> {
    /** La cita no pasó la verificación: no se creó NADA. */
    data class CitaInvalida(val error: String) : ResultadoUnPaso<Nothing>()
    /** El tratamiento no se creó (o quedó a medias): la cita no se intenta. */
    data class TratamientoFallido<T>(val error: String, val creado: T?) : ResultadoUnPaso<T>()
    /**
     * Sin señal: el tratamiento quedó en la cola y se creará al volver la red. La
     * cita NO se agenda a ciegas (no hay a qué tratamiento atarla todavía).
     */
    data object TratamientoEnCola : ResultadoUnPaso<Nothing>()
    /** Solo se pidió el tratamiento («Solo crear tratamiento»). */
    data class SoloTratamiento<T>(val creado: T) : ResultadoUnPaso<T>()
    /** Tratamiento creado, pero la cita no se guardó: queda elegido en la Nueva cita. */
    data class CitaFallida<T>(val error: String, val creado: T) : ResultadoUnPaso<T>()
    /** Las dos cosas. */
    data class Ambos<T>(val creado: T) : ResultadoUnPaso<T>()
}

/**
 * El orden del toque único. Primero se verifica la cita (con datos frescos: la
 * verificación de pantalla puede ir atrás); si no se puede agendar no se crea
 * nada, para no dejar un tratamiento suelto. Después el tratamiento, y recién con
 * él creado, la cita (el mismo guardado de «Guardar cita»). Si la cita falla, el
 * tratamiento ya existe: se avisa y se termina en la cita, nunca se lo vuelve a
 * crear. `crearTratamientoYAgendar` de la web.
 */
suspend fun <T> crearTratamientoYAgendar(
    agendar: Boolean,
    verificarCita: suspend () -> String?,
    crearTratamiento: suspend () -> ResultadoAltaTratamiento,
    /** Con el id del tratamiento recién creado (aunque algo de después fallara). */
    alCrear: suspend (id: String) -> T,
    agendarCita: suspend (creado: T) -> String?,
): ResultadoUnPaso<T> {
    if (agendar) {
        val error = verificarCita()
        if (error != null) return ResultadoUnPaso.CitaInvalida(error)
    }
    val r = crearTratamiento()
    if (r.encolada) return ResultadoUnPaso.TratamientoEnCola
    val creado = r.id?.let { alCrear(it) }
    if (!r.ok || creado == null) {
        return ResultadoUnPaso.TratamientoFallido(r.error ?: "No se pudo crear el tratamiento.", creado)
    }
    if (!agendar) return ResultadoUnPaso.SoloTratamiento(creado)
    val error = try {
        agendarCita(creado)
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        e.message?.takeIf { it.isNotBlank() } ?: "No se pudo guardar la cita."
    }
    return if (error != null) ResultadoUnPaso.CitaFallida(error, creado) else ResultadoUnPaso.Ambos(creado)
}
