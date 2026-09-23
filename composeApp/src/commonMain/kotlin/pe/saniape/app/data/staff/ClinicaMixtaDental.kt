package pe.saniape.app.data.staff

/**
 * Qué es dental en una clínica que tiene odontología Y otras especialidades.
 *
 * Jonathan, 23/09/2026: "imagínate que una clínica tiene odonto y fisio…
 * debe poder coexistir bien". Con la decisión por CLÍNICA, al completar la
 * evaluación de un paciente de fisio se abría el odontograma, y cada ficha
 * tenía la pestaña 🦷. Ahora se decide por cita y por paciente.
 *
 * El MAPA lo arma la web (/api/staff/contexto: `especialidadesOdontologia`,
 * `soloOdontologia`); acá solo se aplica. Gemelo de `citaEsDental` /
 * `pacienteEsDental` en `lib/flujo.ts`, con los mismos casos de prueba.
 *
 * @property ids especialidades dentales de la clínica.
 * @property solo todas lo son (o no hay especialidades y el flujo es dental):
 *   no hay nada que distinguir.
 */
data class MapaDental(val ids: List<String> = emptyList(), val solo: Boolean = false) {
    /** ¿La clínica hace algo de odontología? */
    val activo: Boolean get() = solo || ids.isNotEmpty()
}

/**
 * ¿Este servicio se ofrece en el presupuesto dental? Lo de las especialidades
 * dentales y lo que no cuelga de ninguna; en una clínica solo dental, todo.
 * Gemelo de `esServicioDental` en la web: con Medicina General al lado, el
 * odontólogo no ve "Consulta médica" para ponerle precio a una caries.
 */
fun esServicioDental(especialidadId: String?, mapa: MapaDental): Boolean {
    if (mapa.solo || mapa.ids.isEmpty()) return true
    return especialidadId.isNullOrBlank() || especialidadId in mapa.ids
}

/**
 * ¿Esta CITA es dental? Decide si al completarla va primero el odontograma y
 * si su tarjeta ofrece "🦷 Odontograma".
 *
 * De lo más a lo menos seguro: la especialidad de la cita, la del servicio de
 * su tratamiento, y el profesional (dental solo si TODAS sus especialidades lo
 * son). Lo que no se sabe es NO: imponerle el odontograma a un fisio es peor
 * que que el dentista lo abra desde la ficha.
 */
fun citaEsDental(
    mapa: MapaDental,
    especialidadId: String? = null,
    especialidadServicioId: String? = null,
    especialidadesProfesional: List<String>? = null,
): Boolean {
    // `solo` primero: una dental sin especialidades tiene `ids` vacío.
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    listOf(especialidadId, especialidadServicioId).firstOrNull { !it.isNullOrBlank() }
        ?.let { return it in mapa.ids }
    val prof = especialidadesProfesional.orEmpty().filter { it.isNotBlank() }
    return prof.isNotEmpty() && prof.all { it in mapa.ids }
}

/**
 * ¿La ficha de este paciente muestra la pestaña 🦷?
 *
 * En una clínica mixta, solo si tiene algo dental (cita o tratamiento de
 * odontología, o hallazgos ya marcados) o si quien mira es dentista: el
 * odontólogo tiene que poder empezarle el odontograma a un paciente nuevo.
 */
fun pacienteEsDental(
    mapa: MapaDental,
    especialidadIds: List<String?> = emptyList(),
    tieneHallazgos: Boolean = false,
    especialidadesDeQuienMira: List<String>? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    if (tieneHallazgos) return true
    return especialidadIds.any { it != null && it in mapa.ids } ||
        especialidadesDeQuienMira.orEmpty().any { it in mapa.ids }
}
