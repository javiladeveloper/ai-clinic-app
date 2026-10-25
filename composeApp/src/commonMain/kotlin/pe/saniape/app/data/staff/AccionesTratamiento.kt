package pe.saniape.app.data.staff

// QUÉ ACCIONES DE LA TARJETA DEL TRATAMIENTO VAN A LA VISTA Y CUÁLES AL «⋯»
// (pedido del dueño, 24/10/2026: «crecieron demasiado los botones»). Gemelo de
// `lib/acciones-tratamiento.ts` de la web: mismas acciones, mismos momentos,
// mismos grupos.
//
// La tarjeta abierta llegó a tener ~40 toques a la vez (Agendar, la columna
// «⋯ Opciones del tratamiento», «Dar de alta» abajo…). A la vista quedan solo
// los del MOMENTO del plan y el resto va al «⋯ Más», agrupado. Aquí vive solo
// el REPARTO: si una acción se ofrece o no (permisos, solo lectura, rol, tipo
// de tratamiento) lo sigue decidiendo la tarjeta con sus mismas condiciones de
// siempre, y pasa la lista de las disponibles.

enum class AccionTratamiento {
    Agendar, Editar, Ampliar, SesionPasada, Alta,
    Reactivar, Suspender, NoVolvio, Cancelar,
    Receta, Derivar, Eliminar,
}

/** Los grupos del «⋯», en este orden (los títulos de la web: clínico · plan · estado · peligro). */
val GRUPOS_MENU_TRATAMIENTO: List<GrupoMenu<AccionTratamiento>> = listOf(
    GrupoMenu("Clínico", listOf(AccionTratamiento.Receta, AccionTratamiento.Derivar)),
    GrupoMenu("Plan", listOf(AccionTratamiento.Agendar, AccionTratamiento.Ampliar, AccionTratamiento.SesionPasada, AccionTratamiento.Alta)),
    GrupoMenu("Estado", listOf(AccionTratamiento.Reactivar, AccionTratamiento.Suspender, AccionTratamiento.NoVolvio, AccionTratamiento.Cancelar)),
    GrupoMenu(GRUPO_PELIGRO, listOf(AccionTratamiento.Eliminar)),
)

/** En rojo en el menú (como `ROJAS` de AccionesTratamiento.tsx). Cancelar queda en su grupo Estado. */
val ROJAS_TRATAMIENTO = setOf(AccionTratamiento.Cancelar, AccionTratamiento.Eliminar)

/**
 * - EN_CURSO: Activo con sesiones por hacer → lo de todos los días es agendar.
 * - TERMINADO: hizo todas sus sesiones (o quedó Completado) → toca decidir:
 *   ampliar o dar de alta (la misma regla de [tratamientoTerminado]).
 * - PAUSADO: Suspendido (incluye «No volvió») o Cancelado → reactivarlo.
 * - CERRADO: Alta y cualquier otro estado.
 */
enum class MomentoTratamiento { EN_CURSO, TERMINADO, PAUSADO, CERRADO }

fun momentoTratamiento(t: TratamientoParaTerminar?): MomentoTratamiento = when {
    t == null -> MomentoTratamiento.CERRADO
    t.estado == "Completado" || tratamientoTerminado(t) -> MomentoTratamiento.TERMINADO
    t.estado == "Activo" -> MomentoTratamiento.EN_CURSO
    t.estado == "Suspendido" || t.estado == "Cancelado" -> MomentoTratamiento.PAUSADO
    else -> MomentoTratamiento.CERRADO
}

/** Lo que va a la vista en cada momento, en el orden de la web (si está disponible). */
private val A_LA_VISTA: Map<MomentoTratamiento, List<AccionTratamiento>> = mapOf(
    MomentoTratamiento.EN_CURSO to listOf(AccionTratamiento.Agendar, AccionTratamiento.Editar),
    MomentoTratamiento.TERMINADO to listOf(AccionTratamiento.Ampliar, AccionTratamiento.Alta, AccionTratamiento.Editar),
    MomentoTratamiento.PAUSADO to listOf(AccionTratamiento.Reactivar, AccionTratamiento.Editar),
    // Alta con control pendiente (seguimiento post-alta) sigue pudiendo agendar.
    MomentoTratamiento.CERRADO to listOf(AccionTratamiento.Agendar, AccionTratamiento.Editar),
)

/**
 * La acción del momento, la del botón lleno: Agendar › Dar de alta › Reactivar
 * (los mismos tres que la web pinta destacados). Si ninguna está, no hay
 * primaria y todo lo visible va con contorno.
 */
private val DESTACADAS = listOf(AccionTratamiento.Agendar, AccionTratamiento.Alta, AccionTratamiento.Reactivar)

/**
 * Reparte las [disponibles] de la tarjeta. Las visibles son EXACTAMENTE las de la
 * web (máx. 3); la destacada pasa adelante como primaria. Sin destacada,
 * [PlanAcciones.primaria] es null y las visibles quedan en [PlanAcciones.secundarias].
 */
fun repartirAccionesTratamiento(
    t: TratamientoParaTerminar?,
    disponibles: Collection<AccionTratamiento>,
): PlanAcciones<AccionTratamiento> {
    val vista = A_LA_VISTA.getValue(momentoTratamiento(t)).filter { it in disponibles }
    val primaria = DESTACADAS.firstOrNull { it in vista }
    val orden = listOfNotNull(primaria) + vista.filter { it != primaria }
    val plan = repartirAcciones(orden, GRUPOS_MENU_TRATAMIENTO, disponibles)
    return if (primaria == null) plan.copy(primaria = null, secundarias = plan.visibles) else plan
}
