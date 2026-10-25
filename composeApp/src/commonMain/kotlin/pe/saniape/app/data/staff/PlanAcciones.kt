package pe.saniape.app.data.staff

// REPARTO DE ACCIONES: QUÉ VA A LA VISTA Y QUÉ AL «⋯ Más» (pedido del dueño,
// 24–25/10/2026: «reducir la cantidad de botones»). Gemelo de
// `lib/menu-acciones.ts` de la web.
//
// Cada tarjeta tiene su regla (AccionesCita, AccionesTratamiento,
// AccionesSesion, AccionesFicha, AccionesPago). Todas siguen el mismo patrón:
//   1. A la vista, solo lo del MOMENTO: 1 primaria (botón lleno) + máx. 2
//      secundarias; el 💰 compacto es un extra fijo.
//   2. El resto al «⋯ Más», agrupado.
//   3. Lo peligroso (cancelar, revertir, eliminar, dar de baja) en rojo y AL
//      FINAL del menú.
// La regla solo REPARTE: si una acción se ofrece o no (permisos, plan, rubro,
// solo lectura) lo sigue decidiendo la pantalla, con sus condiciones de
// siempre, y pasa la lista de las disponibles. Lo que hoy no se muestra,
// tampoco aparece en el menú.

/** Un grupo del menú «⋯ Más»: su título (vacío = solo el separador) y sus acciones, en orden. */
data class GrupoMenu<A>(val titulo: String, val acciones: List<A>)

/** Título del grupo donde caen las acciones peligrosas (al final, sin título: solo el separador). */
const val GRUPO_PELIGRO = ""

/** Título del grupo de las disponibles que la regla no ubicó en ningún grupo. */
const val GRUPO_OTRAS = "Otras"

/** Dónde va cada acción de una tarjeta. */
data class PlanAcciones<A>(
    /** La acción del momento (botón lleno). */
    val primaria: A?,
    /** A lo sumo dos más, visibles (contorno). */
    val secundarias: List<A> = emptyList(),
    /** 💰 compacto junto a la píldora de pago (solo con deuda). */
    val cobro: A? = null,
    /** «⋯ Más»: el resto, por grupo y en orden. Vacío = sin «⋯». */
    val menu: List<GrupoMenu<A>> = emptyList(),
) {
    val visibles: List<A> get() = listOfNotNull(primaria) + secundarias
    val enMenu: List<A> get() = menu.flatMap { it.acciones }
}

/**
 * Reparte [disponibles]:
 * - Las de [aLaVista] (en ese orden, hasta [max]) a la vista: la primera es la
 *   primaria, las siguientes las secundarias.
 * - [cobro] (si está disponible y no quedó a la vista) va al 💰 compacto.
 * - El resto a los [grupos] del menú, en su orden. Una disponible que no figure
 *   en ningún grupo NO se pierde: cae en «Otras».
 * - Las [rojas] se sacan de su grupo a uno propio AL FINAL, en el orden de [rojas].
 */
fun <A> repartirAcciones(
    aLaVista: List<A>,
    grupos: List<GrupoMenu<A>>,
    disponibles: Collection<A>,
    max: Int = 3,
    cobro: A? = null,
    rojas: List<A> = emptyList(),
): PlanAcciones<A> {
    val hay = LinkedHashSet(disponibles)
    val visibles = aLaVista.filter { it in hay }.distinct().take(max)
    val cobroFinal = cobro?.takeIf { it in hay && it !in visibles }
    val usadas = HashSet<A>(visibles).apply { cobroFinal?.let { add(it) } }
    val menu = mutableListOf<GrupoMenu<A>>()
    for (g in grupos) {
        val acciones = g.acciones.filter { it in hay && it !in usadas }.distinct()
        usadas.addAll(acciones)
        if (acciones.isNotEmpty()) menu.add(GrupoMenu(g.titulo, acciones))
    }
    val sueltas = hay.filter { it !in usadas }
    if (sueltas.isNotEmpty()) menu.add(GrupoMenu(GRUPO_OTRAS, sueltas))
    return PlanAcciones(
        primaria = visibles.firstOrNull(),
        secundarias = visibles.drop(1),
        cobro = cobroFinal,
        menu = ordenarMenu(menu, rojas),
    )
}

/**
 * El menú tal como se dibuja: sin grupos vacíos y con las [rojas] sacadas de su
 * grupo a uno propio AL FINAL (lo peligroso nunca queda entre lo de todos los
 * días), ordenadas como vienen en [rojas].
 */
fun <A> ordenarMenu(menu: List<GrupoMenu<A>>, rojas: List<A>): List<GrupoMenu<A>> {
    if (rojas.isEmpty()) return menu.filter { it.acciones.isNotEmpty() }
    val peligro = mutableListOf<A>()
    val grupos = mutableListOf<GrupoMenu<A>>()
    for (g in menu) {
        val (rojo, normales) = g.acciones.partition { it in rojas }
        peligro.addAll(rojo)
        if (normales.isNotEmpty()) grupos.add(GrupoMenu(g.titulo, normales))
    }
    if (peligro.isNotEmpty()) grupos.add(GrupoMenu(GRUPO_PELIGRO, peligro.sortedBy { rojas.indexOf(it) }))
    return grupos
}

/**
 * La tarjeta de cita ([planAccionesCita]) en la forma común, para dibujarla con
 * el mismo «⋯ Más» que el resto de la app. No cambia su reparto.
 */
fun PlanAccionesCita.comoPlan(): PlanAcciones<AccionCitaItem> = PlanAcciones(
    primaria = primaria,
    secundarias = listOfNotNull(secundaria),
    cobro = cobro,
    menu = menu.map { (g, items) -> GrupoMenu(g.titulo, items) },
)
