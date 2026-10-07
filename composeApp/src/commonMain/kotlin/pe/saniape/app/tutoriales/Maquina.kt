package pe.saniape.app.tutoriales

/**
 * Máquina de estados de un tutorial (PURA). Gemela de `lib/tutoriales/maquina.ts`
 * + `deteccion.ts` de la web, con el vocabulario de la app (pantallas en vez de
 * rutas, tareas en vez de `sania:tarea`). Si cambia una, cambia la otra: los
 * tests de paridad (MaquinaTutorialTest) cubren las mismas reglas.
 *
 *   inactivo ──iniciar──▶ activo ──(último paso o meta)──▶ completado
 *                           │  ▲
 *                 se fue de │  │ volvió a la pantalla / "Retomar"
 *                la pantalla▼  │
 *                          pausado
 *   activo|pausado ──saltar / posponer──▶ inactivo
 */
enum class Fase { INACTIVO, ACTIVO, PAUSADO, COMPLETADO }

data class EstadoTour(
    val fase: Fase,
    val tourId: String?,
    val paso: Int,
    val total: Int,
    /** Pausa restaurada al reabrir la app: solo "Retomar" la levanta. */
    val soloManual: Boolean = false,
) {
    val enCurso: Boolean get() = fase == Fase.ACTIVO || fase == Fase.PAUSADO
}

val INACTIVO = EstadoTour(Fase.INACTIVO, null, 0, 0)

/** Lo que el motor ve ahora mismo (para saltar pasos ya cumplidos). */
data class Entorno(val pantalla: String?, val visible: (String) -> Boolean)

/** Señales que el motor observa en la app. */
sealed class Senal {
    /** Tocó un elemento: las anclas que lo contienen. */
    data class Clic(val anclas: List<String>) : Senal()
    /** Cambió qué anclas están en pantalla. */
    data class Dom(val visible: (String) -> Boolean) : Senal()
    /** La pantalla actual. */
    data class Pantalla(val pantalla: String?) : Senal()
    /** Se escribió/eligió algo en un campo dentro de estas anclas. */
    data class Campo(val anclas: List<String>, val valor: String) : Senal()
    /** La app terminó una acción real (guardó de verdad). */
    data class Tarea(val tarea: String) : Senal()
}

/** Evento de medición: iniciado | paso | completado | saltado | pospuesto. */
data class Medicion(val evento: String, val paso: Int)

data class Transicion(val estado: EstadoTour, val medir: List<Medicion> = emptyList())

private fun igual(s: EstadoTour) = Transicion(s)

/** ¿El paso ocurre en esta pantalla? (sin pantallas = en cualquiera) */
fun pantallaCoincide(pantallas: List<String>, pantalla: String?): Boolean =
    pantallas.isEmpty() || (pantalla != null && pantalla in pantallas)

/** ¿La señal cumple la espera? */
fun cumpleEspera(e: EsperaApp, s: Senal): Boolean = when (e.tipo) {
    "clic" -> s is Senal.Clic && e.valor != null && e.valor in s.anclas
    "aparece" -> s is Senal.Dom && e.valor != null && s.visible(e.valor)
    "desaparece" -> s is Senal.Dom && e.valor != null && !s.visible(e.valor)
    "pantalla" -> s is Senal.Pantalla && s.pantalla != null && s.pantalla in e.opciones
    "valor" -> s is Senal.Campo && e.valor != null && e.valor in s.anclas && s.valor.trim().length >= (e.min ?: 1)
    "tarea" -> s is Senal.Tarea && s.tarea == e.valor
    else -> false // manual (o un tipo nuevo que esta versión no conoce: solo con el botón)
}

/**
 * ¿Este paso ya está hecho al llegar? Solo esperas de ESTADO (ya en la
 * pantalla, el diálogo ya abierto). Un toque o un guardado nunca se dan por hechos.
 */
fun yaCumplido(p: PasoApp, entorno: Entorno): Boolean {
    if (!p.saltarSiYa) return false
    val e = p.espera
    return when (e.tipo) {
        "pantalla" -> entorno.pantalla != null && entorno.pantalla in e.opciones
        "aparece" -> e.valor != null && entorno.visible(e.valor)
        "desaparece" -> e.valor != null && !entorno.visible(e.valor)
        else -> false
    }
}

fun anclaVisible(anclas: List<String>, visible: (String) -> Boolean): Boolean = anclas.any(visible)

private fun seSalta(p: PasoApp, entorno: Entorno): Boolean {
    if (p.opcional && !anclaVisible(p.anclasEfectivas, entorno.visible)) return true
    return yaCumplido(p, entorno)
}

/** Primer paso desde [desde] que NO esté ya cumplido. El ÚLTIMO nunca se salta. */
fun primerPendiente(pasos: List<PasoApp>, desde: Int, entorno: Entorno): Int {
    var i = desde
    while (i < pasos.size - 1 && seSalta(pasos[i], entorno)) i++
    return i
}

object Maquina {

    fun iniciar(tourId: String, pasos: List<PasoApp>, entorno: Entorno): Transicion {
        if (pasos.isEmpty()) return igual(INACTIVO)
        val paso = primerPendiente(pasos, 0, entorno)
        return Transicion(EstadoTour(Fase.ACTIVO, tourId, paso, pasos.size), listOf(Medicion("iniciado", paso)))
    }

    /** El paso actual se cumplió: al siguiente pendiente, o completado. */
    fun avanzar(s: EstadoTour, pasos: List<PasoApp>, entorno: Entorno): Transicion {
        if (!s.enCurso) return igual(s)
        val sig = s.paso + 1
        if (sig >= pasos.size) return completar(s)
        val paso = primerPendiente(pasos, sig, entorno)
        return Transicion(s.copy(fase = Fase.ACTIVO, paso = paso), listOf(Medicion("paso", paso)))
    }

    fun completar(s: EstadoTour): Transicion {
        if (s.fase == Fase.COMPLETADO || s.fase == Fase.INACTIVO) return igual(s)
        return Transicion(s.copy(fase = Fase.COMPLETADO, paso = s.total - 1), listOf(Medicion("completado", s.paso)))
    }

    fun pausar(s: EstadoTour): Transicion = if (s.fase == Fase.ACTIVO) igual(s.copy(fase = Fase.PAUSADO)) else igual(s)

    fun reanudar(s: EstadoTour): Transicion =
        if (s.fase == Fase.PAUSADO) igual(EstadoTour(Fase.ACTIVO, s.tourId, s.paso, s.total)) else igual(s)

    /** "Saltar" (no lo quiero) o "Lo hago después": termina y se mide dónde quedó. */
    fun abandonar(s: EstadoTour, como: String): Transicion {
        if (!s.enCurso) return igual(INACTIVO)
        return Transicion(INACTIVO, listOf(Medicion(como, s.paso)))
    }

    /** Cerrar la celebración final. */
    fun cerrar(): Transicion = igual(INACTIVO)

    /**
     * Una señal mientras hay un tutorial en curso:
     *  1. la META (guardó de verdad) lo completa desde cualquier paso;
     *  2. si cumple la espera del paso actual, avanza;
     *  3. si cambió la pantalla fuera de donde ocurre el paso, se pausa;
     *  4. si estaba en pausa y volvió a la pantalla del paso, se reanuda.
     */
    fun procesarSenal(s: EstadoTour, pasos: List<PasoApp>, meta: String?, senal: Senal, entorno: Entorno): Transicion {
        if (!s.enCurso) return igual(s)
        if (senal is Senal.Tarea && meta != null && senal.tarea == meta) return completar(s)
        val paso = pasos.getOrNull(s.paso) ?: return igual(s)

        if (s.fase == Fase.ACTIVO && cumpleEspera(paso.espera, senal)) return avanzar(s, pasos, entorno)

        if (senal is Senal.Pantalla) {
            val enLugar = pantallaCoincide(paso.pantallas, senal.pantalla)
            if (s.fase == Fase.ACTIVO && !enLugar) return pausar(s)
            if (s.fase == Fase.PAUSADO && enLugar && !s.soloManual) {
                val r = reanudar(s).estado
                return if (cumpleEspera(paso.espera, senal)) avanzar(r, pasos, entorno) else igual(r)
            }
        }
        return igual(s)
    }

    /** Progreso 0..1 para la barra. */
    fun progreso(s: EstadoTour): Float = when {
        s.fase == Fase.COMPLETADO -> 1f
        s.total == 0 -> 0f
        else -> minOf(1f, s.paso.toFloat() / s.total)
    }

    /** Al reabrir la app: siempre en pausa, y solo "Retomar" la levanta. */
    fun restaurar(tourId: String, paso: Int, total: Int): EstadoTour {
        val p = paso.coerceIn(0, maxOf(0, total - 1))
        return EstadoTour(Fase.PAUSADO, tourId, p, total, soloManual = true)
    }
}
