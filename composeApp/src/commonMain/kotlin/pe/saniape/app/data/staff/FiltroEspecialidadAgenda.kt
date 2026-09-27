package pe.saniape.app.data.staff

/**
 * Filtro por especialidad de la agenda (varias a la vez).
 *
 * Jonathan, 26/09/2026: "En citas deberíamos tener un filtro por especialidad
 * para ver solo las especialidades que nos interesan". Gemelo de
 * `lib/filtro-especialidad-citas.ts` en la web, con los mismos casos de prueba.
 *
 * La especialidad de una cita se resuelve igual que la etiqueta de la agenda
 * web (`useEspecialidadDeCita`) y que [citaEsDental]: la de la cita → la del
 * servicio del tratamiento → la del profesional SOLO si tiene una. Lo que no se
 * puede saber cae en "Sin especialidad", que es una opción más: así ninguna
 * cita se esconde sin querer.
 *
 * Selección vacía = "Todas" (no filtra).
 */
const val SIN_ESPECIALIDAD = "sin"

/** De qué especialidad es la cita (id), o null si no se puede saber. */
fun especialidadIdDeCita(
    especialidadId: String?,
    especialidadServicioId: String?,
    especialidadesProfesional: List<String>?,
): String? {
    listOf(especialidadId, especialidadServicioId).firstOrNull { !it.isNullOrBlank() }?.let { return it }
    val prof = especialidadesProfesional.orEmpty().filter { it.isNotBlank() }
    return prof.singleOrNull()
}

/** La opción en la que cae un id: el propio si se ofrece; si no (sin dato o desactivada), "Sin especialidad". */
fun opcionDeEspecialidad(id: String?, ofrecidos: Set<String>): String =
    if (id != null && id in ofrecidos) id else SIN_ESPECIALIDAD

fun pasaFiltroEspecialidad(opcion: String, seleccion: Collection<String>): Boolean =
    seleccion.isEmpty() || opcion in seleccion

/**
 * La selección válida hoy: descarta lo que ya no se ofrece y, si quedan todas
 * marcadas, vuelve a "Todas".
 */
fun normalizarSeleccion(guardado: List<String>, ofrecidos: List<String>): List<String> {
    val validos = (ofrecidos + SIN_ESPECIALIDAD).toSet()
    val out = guardado.filter { it in validos }.distinct()
    return if (out.size >= validos.size) emptyList() else out
}

/** Marca o desmarca una opción. Desmarcar la última vuelve a "Todas". */
fun alternarOpcion(seleccion: List<String>, opcion: String, ofrecidos: List<String>): List<String> =
    normalizarSeleccion(if (opcion in seleccion) seleccion - opcion else seleccion + opcion, ofrecidos)

/** ¿Pasa la cita? [espsPorTerapeuta]: especialidades de cada profesional (último respaldo). */
fun citaPasaFiltroEspecialidad(
    cita: CitaStaff,
    seleccion: List<String>,
    espsPorTerapeuta: Map<String, List<String>>,
    ofrecidos: Set<String>,
): Boolean {
    if (seleccion.isEmpty()) return true
    val id = especialidadIdDeCita(
        cita.especialidadId, cita.especialidadServicioId,
        cita.terapeutaId?.let { espsPorTerapeuta[it] },
    )
    return pasaFiltroEspecialidad(opcionDeEspecialidad(id, ofrecidos), seleccion)
}

/** Guardado en preferencias: ids separados por coma (los uuid no llevan comas). */
fun codificarSeleccion(seleccion: List<String>): String? =
    seleccion.takeIf { it.isNotEmpty() }?.joinToString(",")

fun decodificarSeleccion(texto: String?): List<String> =
    texto?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
