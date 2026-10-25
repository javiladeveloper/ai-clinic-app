package pe.saniape.app.data.staff

// Qué arranca elegido en «Nueva cita» al elegir un paciente — gemelo de
// `lib/prefill-cita.ts` (web). Antes la app abría siempre en «Consulta» y, aunque
// el paciente tuviera un solo tratamiento activo, había que abrir la lista y
// elegirlo (si no, «Elige el tratamiento»): +3 toques en el caso diario de DALU
// (auditoría de flujos 24/10/2026, F2). La web ya lo deducía.

/**
 * Qué tratamiento arranca elegido en una Sesión. El prefijado (p. ej. «Agendar
 * siguiente») manda si sigue activo; con UNO solo activo, ese (no es una decisión,
 * es un trámite); con dos o más, null (adivinar sería peor que preguntar).
 * `tratamientoInicial` de la web. [activos] = ids de los tratamientos ACTIVOS.
 */
fun tratamientoInicial(prefijado: String?, activos: List<String>): String? {
    if (prefijado != null && prefijado in activos) return prefijado
    return activos.singleOrNull()
}

/**
 * Tipo que corresponde al elegir un paciente: con un tratamiento ACTIVO por
 * sesiones ([vaPorSesiones]: no el pago único ni las unidades) → "Sesión"; sin él,
 * vuelve al tipo con que abrió el formulario ([tipoInicial]) si se ofrece.
 * null = no tocar el tipo: quien agenda ya lo tocó, el formulario vino con un tipo
 * prefijado (→ Evaluación, Repetir, la ficha) o la clínica no ofrece Sesión.
 * `tipoSegunPaciente` de la web. [modalidadesActivas]: las de los tratamientos
 * activos que deciden (los de la especialidad de la cita, ver [tratamientosQueDecidenTipo]).
 */
fun tipoSegunPaciente(
    modalidadesActivas: List<String?>,
    tipoInicial: String,
    tiposVisibles: List<String>,
    tocadoAMano: Boolean,
    prefijado: Boolean,
): String? {
    if (tocadoAMano || prefijado) return null
    if ("Sesión" !in tiposVisibles) return null
    if (modalidadesActivas.any(::vaPorSesiones)) return "Sesión"
    return tipoInicial.takeIf { it in tiposVisibles }
}

/**
 * Los tratamientos que deciden el tipo: solo los de la ESPECIALIDAD de la cita
 * (en una clínica mixta, un tratamiento dental activo no pasa a Sesión la cita de
 * fisio del mismo paciente); el de un servicio sin especialidad cuenta siempre.
 * Sin especialidad elegida, todos. `tratsQueDecidenTipo` de CitaForm (web).
 */
fun tratamientosQueDecidenTipo(tratamientos: List<TratamientoRef>, especialidadId: String?): List<TratamientoRef> =
    tratamientos.filter { it.estado == "Activo" }
        .filter { especialidadId == null || it.especialidadId == null || it.especialidadId == especialidadId }
