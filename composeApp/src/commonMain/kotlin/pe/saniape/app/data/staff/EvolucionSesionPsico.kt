package pe.saniape.app.data.staff

/** Mismo formato legible que la web: se guarda UNA nota en sesiones.mejorias. */
data class EvolucionSesionPsico(
    val evolucion: String = "", val estado: String = "", val tareas: String = "", val plan: String = "",
)
private val etiquetasEvolucionPsico = listOf("Temas trabajados y evolución", "Estado emocional / examen mental", "Tareas e indicaciones", "Plan para la próxima sesión")
fun textoEvolucionPsico(b: EvolucionSesionPsico): String {
    if (b.estado.isBlank() && b.tareas.isBlank() && b.plan.isBlank()) return b.evolucion
    return etiquetasEvolucionPsico.zip(listOf(b.evolucion, b.estado, b.tareas, b.plan))
        .filter { it.second.isNotBlank() }.joinToString("\n\n") { "${it.first}:\n${it.second}" }
}
fun leerEvolucionPsico(texto: String): EvolucionSesionPsico {
    val patron = Regex("^(Temas trabajados y evolución|Estado emocional / examen mental|Tareas e indicaciones|Plan para la próxima sesión):\n", RegexOption.MULTILINE)
    val partes = patron.findAll(texto).toList()
    if (partes.isEmpty() || partes.first().range.first != 0) return EvolucionSesionPsico(evolucion = texto)
    val valores = MutableList(4) { "" }
    partes.forEachIndexed { i, m ->
        val fin = if (i + 1 < partes.size) partes[i + 1].range.first - 2 else texto.length
        valores[etiquetasEvolucionPsico.indexOf(m.groupValues[1])] = texto.substring(m.range.last + 1, fin.coerceAtLeast(m.range.last + 1))
    }
    return EvolucionSesionPsico(valores[0], valores[1], valores[2], valores[3])
}
fun esCierrePsicologia(esFisio: Boolean, esDental: Boolean, especialidadId: String?, especialidades: List<Triple<String, String?, String?>>): Boolean {
    if (esFisio || esDental) return false
    fun esPsico(e: Triple<String, String?, String?>) = e.second == "psicologia" && !e.third.orEmpty().contains("psiquiatr", ignoreCase = true)
    return if (!especialidadId.isNullOrBlank()) especialidades.firstOrNull { it.first == especialidadId }?.let(::esPsico) == true
        else especialidades.isNotEmpty() && especialidades.all(::esPsico)
}
