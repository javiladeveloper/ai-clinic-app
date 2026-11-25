package pe.saniape.app.data.staff

/*
 * 🦷 TRATAMIENTO DENTAL DIRECTO + UNIDADES QUE SE CUENTAN ANTES (24/11/2026).
 * Gemelos de lib/piezas-tratamiento-directo.ts y lib/unidades-precio.ts (web).
 *
 * · Al crear un tratamiento dental sin pasar por el diagnóstico, el formulario
 *   muestra las piezas PENDIENTES del odontograma (sin tratamiento, sin «ausente» ni
 *   trabajo previo), deja elegir cuáles cubre y las manda como `hallazgoIds`: las ata
 *   el servidor al crear.
 * · Zonas, piezas, uñas…: la cantidad se sabe al agendar y nace en 1 (antes quedaba
 *   vacía y el tratamiento «por definir», S/ 0 en la cita: depilación de LUMINA).
 *
 * Lógica pura (sin Compose ni red).
 */

data class PiezaPendiente(
    val id: String,
    val diente: String,
    /** «17», o «13–23» si el hallazgo abarca varias piezas. */
    val etiquetaDiente: String,
    val hallazgo: String,
    val color: String,
    /** El catálogo cobra este hallazgo con el servicio elegido. */
    val delServicio: Boolean,
    /** El servicio que el catálogo cobra por este hallazgo (null = ninguno). */
    val procedimientoId: String?,
)

private fun numeroPieza(d: String): Int = d.toIntOrNull() ?: 999

/** Lo que se puede sumar a un tratamiento nuevo, primero lo del servicio elegido. */
fun piezasPendientes(
    hallazgos: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
    procedimientoId: String?,
): List<PiezaPendiente> {
    val cat = catalogo.associateBy { it.id }
    return hallazgos
        .filter { it.estado == "Pendiente" && it.tratamientoId == null }
        .mapNotNull { h -> cat[h.hallazgoId]?.let { c -> h to c } }
        // Una pieza que falta no se trata; el trabajo previo (azul sin servicio) describe la boca.
        .filter { (_, c) -> !c.marcaAusente && !(c.color.equals(COLOR_REALIZADO, ignoreCase = true) && c.procedimientoId == null) }
        .map { (h, c) ->
            PiezaPendiente(
                id = h.id, diente = h.diente,
                etiquetaDiente = h.dienteHasta?.takeIf { it != h.diente }?.let { "${h.diente}–$it" } ?: h.diente,
                hallazgo = c.nombre, color = c.color,
                delServicio = procedimientoId != null && c.procedimientoId == procedimientoId,
                procedimientoId = c.procedimientoId,
            )
        }
        .sortedWith(compareBy<PiezaPendiente>({ !it.delServicio }, { numeroPieza(it.diente) }, { it.hallazgo }))
}

/** Las que vienen marcadas al elegir el servicio: las que el catálogo cobra con él. */
fun piezasSugeridas(pendientes: List<PiezaPendiente>): List<String> = pendientes.filter { it.delServicio }.map { it.id }

/** N piezas × precio de lista (0 sin piezas o sin precio). */
fun totalPorPiezas(cuantas: Int, precioLista: Double?): Double {
    val p = precioLista ?: return 0.0
    if (cuantas <= 0 || p <= 0.0) return 0.0
    return kotlin.math.round(cuantas * p * 100) / 100
}

/** En odontología el paso previo al tratamiento es el «Diagnóstico». */
fun nombrePasoPrevio(dental: Boolean, labelEvaluacion: String?): String {
    val l = labelEvaluacion?.trim().orEmpty()
    if (dental) return if (l.contains("diagn", ignoreCase = true)) l else "Diagnóstico"
    return l.ifEmpty { "Evaluación" }
}

private fun sinTildes(s: String): String = s.lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n')

/** ¿La unidad se cuenta antes de atender (zonas, piezas, uñas, áreas…)? */
fun unidadSeCuentaAntes(unidadLabel: String?): Boolean {
    val u = sinTildes(unidadLabel.orEmpty())
    return Regex("\\b(zonas?|piezas?|areas?|unas?|dientes?|sesion(es)?|puntos?|lesion(es)?|verrugas?|lunares?)\\b").containsMatchIn(u)
}

/** Con qué cantidad nace el campo al elegir el servicio: "1" si se cuenta antes; si no, vacío. */
fun cantidadInicialUnidades(unidadLabel: String?): String = if (unidadSeCuentaAntes(unidadLabel)) "1" else ""
