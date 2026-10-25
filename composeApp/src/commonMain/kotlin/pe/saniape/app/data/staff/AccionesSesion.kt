package pe.saniape.app.data.staff

// QUÉ ACCIONES DE LA FILA DE UNA SESIÓN VAN A LA VISTA Y CUÁLES AL «⋯»
// (pedido del dueño, 24–25/10/2026). Una sola regla para la fila de sesión de
// la ficha (TarjetaTratamiento) y la de Sesiones (PantallaSesiones): antes cada
// una ofrecía cosas distintas y con otras palabras («Cancelada» en un lado,
// «✕ Canceló» en el otro). Gemela de `lib/acciones-sesion.ts` (web).
//
// Solo REPARTE: si una acción se ofrece o no (permiso, Admin, pagos, receta…)
// lo sigue decidiendo cada pantalla con sus condiciones de siempre.
//
// - Pendiente (Planificada / En progreso / Reprogramada) → ✓ Completar.
// - Completada → 💳 Cobrar (si debe); si cerró el plan, ✓ Dar de alta / ＋ Ampliar;
//   si no, la receta. A lo sumo dos a la vista.
// - Anulada (Cancelada / No asistió / Otro) → ↩ Reactivar.
// - Lo peligroso (✕ Canceló, ↩ Revertir, 🗑 Borrar) en rojo y al final del «⋯».

enum class AccionSesion {
    Completar, Cobrar, Reactivar,
    Alta, Ampliar,
    VerReceta, Receta,
    Editar, Reprogramar, NoAsistio, Otro, Reasignar,
    Cancelo, Revertir, Borrar,
}

/** El momento de la sesión, por su estado. */
enum class MomentoSesion { PENDIENTE, COMPLETADA, ANULADA }

fun momentoSesion(estado: String?): MomentoSesion = when (estado) {
    "Completada" -> MomentoSesion.COMPLETADA
    "Cancelada", "No asistió", "Otro" -> MomentoSesion.ANULADA
    else -> MomentoSesion.PENDIENTE
}

/** El estado que fija cada acción de cambio de estado del menú (null = no es un cambio de estado). */
fun estadoDeAccionSesion(a: AccionSesion): String? = when (a) {
    AccionSesion.Reprogramar -> "Reprogramada"
    AccionSesion.NoAsistio -> "No asistió"
    AccionSesion.Cancelo -> "Cancelada"
    AccionSesion.Otro -> "Otro"
    else -> null
}

/** Las acciones de cambio de estado de una sesión pendiente, en el orden de [ESTADOS_MENU_SESION]. */
val ACCIONES_ESTADO_SESION: List<AccionSesion> =
    ESTADOS_MENU_SESION.mapNotNull { e -> AccionSesion.entries.firstOrNull { estadoDeAccionSesion(it) == e } }

/** Las palabras de cada acción (las de /sesiones web). La receta la pone cada pantalla. */
fun textoAccionSesion(a: AccionSesion): String = when (a) {
    AccionSesion.Completar -> "✓ Completar"
    AccionSesion.Cobrar -> "💳 Cobrar"
    AccionSesion.Reactivar -> "↩ Reactivar"
    AccionSesion.Alta -> "✓ Dar de alta"
    AccionSesion.Ampliar -> "＋ Ampliar"
    AccionSesion.VerReceta -> "🖨 Ver receta"
    AccionSesion.Receta -> "📝 Dar receta / indicaciones"
    AccionSesion.Editar -> "✏ Editar sesión"
    AccionSesion.Reprogramar -> "📅 Reprogramar"
    AccionSesion.NoAsistio -> "🚫 No asistió"
    AccionSesion.Otro -> "⚪ Otro"
    AccionSesion.Reasignar -> "👤 Cambiar profesional"
    AccionSesion.Cancelo -> "✕ Canceló"
    AccionSesion.Revertir -> "↩ Revertir"
    AccionSesion.Borrar -> "🗑 Borrar sesión"
}

/** Grupos del «⋯», en este orden; lo rojo sale a un grupo propio al final. */
val GRUPOS_MENU_SESION: List<GrupoMenu<AccionSesion>> = listOf(
    GrupoMenu("Sesión", listOf(AccionSesion.Editar, AccionSesion.Reprogramar, AccionSesion.NoAsistio, AccionSesion.Otro, AccionSesion.Reasignar)),
    GrupoMenu("Clínico", listOf(AccionSesion.VerReceta, AccionSesion.Receta)),
    GrupoMenu("Plan", listOf(AccionSesion.Alta, AccionSesion.Ampliar, AccionSesion.Reactivar)),
    GrupoMenu("Cobro", listOf(AccionSesion.Cobrar)),
    GrupoMenu(GRUPO_PELIGRO, listOf(AccionSesion.Cancelo, AccionSesion.Revertir, AccionSesion.Borrar)),
)

/** En rojo y al final del menú, en este orden. */
val ROJAS_SESION: List<AccionSesion> = listOf(AccionSesion.Cancelo, AccionSesion.Revertir, AccionSesion.Borrar)

private val A_LA_VISTA_SESION: Map<MomentoSesion, Pair<List<AccionSesion>, Int>> = mapOf(
    MomentoSesion.PENDIENTE to (listOf(AccionSesion.Completar) to 1),
    MomentoSesion.COMPLETADA to (listOf(AccionSesion.Cobrar, AccionSesion.Alta, AccionSesion.Ampliar,
        AccionSesion.VerReceta, AccionSesion.Receta) to 2),
    MomentoSesion.ANULADA to (listOf(AccionSesion.Reactivar) to 1),
)

/** Reparte las [disponibles] de la fila de una sesión en [estado]. */
fun repartirAccionesSesion(estado: String?, disponibles: Collection<AccionSesion>): PlanAcciones<AccionSesion> {
    val (vista, max) = A_LA_VISTA_SESION.getValue(momentoSesion(estado))
    return repartirAcciones(vista, GRUPOS_MENU_SESION, disponibles, max = max, rojas = ROJAS_SESION)
}
