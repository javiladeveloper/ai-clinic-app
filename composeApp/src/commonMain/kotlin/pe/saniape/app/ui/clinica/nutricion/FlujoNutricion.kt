package pe.saniape.app.ui.clinica.nutricion

/**
 * El FLUJO de nutrición sin buscar la pestaña (gemelo de `nutriFlujo` de la
 * ficha web y de `ofrecerMedidasNutri` de /citas):
 *
 *   agenda: se completa un control → "⚖️ Registrar medidas" (aviso que no
 *   bloquea; "📅 Agendar siguiente" sigue saliendo como siempre) → ficha en
 *   ⚖️ Medidas con el formulario abierto y la medición vinculada a la cita →
 *   al guardar, "🥗 Continuar con el plan alimentario →" → plan nuevo (parte
 *   del último) vinculado a la misma cita → al guardar, imprimir / WhatsApp.
 */

/** Cómo se abre la ficha desde la agenda: en qué pestaña, con qué cita y si el formulario arranca abierto. */
data class AperturaNutricion(
    /** "medidas" o "plan". */
    val tab: String,
    /** La cita de la que viene: la medición y el plan nuevos quedan vinculados a ella. */
    val citaId: String? = null,
    /** true = el formulario (medidas) o el editor (plan nuevo) arranca abierto. */
    val abrirFormulario: Boolean = false,
    /** El tratamiento de esa cita (para agendar el siguiente control desde el plan). */
    val tratamientoId: String? = null,
)

/**
 * Estado del flujo dentro de la ficha. Los "abrir de una" valen UNA vez: al
 * pasar a otra pestaña se apagan (volver no reabre el formulario); la cita se
 * conserva para lo que se registre después.
 */
data class FlujoNutricionFicha(
    val citaId: String? = null,
    val abrirMedidas: Boolean = false,
    val abrirPlan: Boolean = false,
    /** El tratamiento de la cita de la que se viene (si lo tiene). */
    val tratamientoId: String? = null,
) {
    /** Se cambió de pestaña: lo que no es de esa pestaña ya no se abre solo. */
    fun alCambiarTab(tab: String): FlujoNutricionFicha =
        copy(abrirMedidas = abrirMedidas && tab == "medidas", abrirPlan = abrirPlan && tab == "plan")

    /**
     * El formulario / editor ya se abrió (o se salió a agendar): no se vuelve a
     * abrir solo al remontar la pestaña (volver de crear cita, de la HC…). La
     * cita y el tratamiento se conservan.
     */
    fun consumido(): FlujoNutricionFicha = copy(abrirMedidas = false, abrirPlan = false)

    /** "Continuar con el plan alimentario →": el editor del plan abre de una (misma cita). */
    fun continuarConPlan(): FlujoNutricionFicha = copy(abrirMedidas = false, abrirPlan = true)

    companion object {
        fun desde(a: AperturaNutricion?): FlujoNutricionFicha = if (a == null) FlujoNutricionFicha() else FlujoNutricionFicha(
            citaId = a.citaId,
            abrirMedidas = a.abrirFormulario && a.tab == "medidas",
            abrirPlan = a.abrirFormulario && a.tab == "plan",
            tratamientoId = a.tratamientoId,
        )
    }
}

/**
 * ¿Se ofrece "⚖️ Registrar medidas" tras completar? Solo una cita de nutrición
 * con paciente, a quien puede escribir en la ficha ('pacientes' o 'sesiones'),
 * y si la cita quedó completada EN el servidor (sin señal quedó en la cola: el
 * formulario de medidas necesita red). Independiente de "📅 Agendar siguiente".
 */
fun ofrecerMedidasTrasCompletar(
    esNutricion: Boolean,
    pacienteId: String?,
    puedePacientes: Boolean,
    puedeSesiones: Boolean,
    encolada: Boolean,
): Boolean = esNutricion && !pacienteId.isNullOrBlank() && (puedePacientes || puedeSesiones) && !encolada

/** Cuánto queda a la vista el aviso de medidas si nadie lo toca (la web: 12 s). */
const val VIDA_OFERTA_MEDIDAS_MS = 15_000L
