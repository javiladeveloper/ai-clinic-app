package pe.saniape.app.ui.clinica.atencion

import pe.saniape.app.data.staff.FraseFrecuenteApp

// ─────────────────────────────────────────────────────────────────────────────
// FRASES RÁPIDAS DE PEDIATRÍA (2026-10-15) — GEMELO de `FRASES_BASE.pediatria`
// de lib/frases-clinicas.ts (mismos textos, mismo orden, mismas etiquetas).
// Motivo, examen e indicaciones pensados para niños. Aprende en las MISMAS
// claves que medicina (como gineco): solo cambia la base.
// ─────────────────────────────────────────────────────────────────────────────

private object ExamenPediatria {
    const val general = "Niño en aparente buen estado general, activo, reactivo, hidratado"
    const val piel = "Piel y mucosas húmedas, sin palidez, sin lesiones ni exantemas"
    const val orofaringe = "Orofaringe sin alteraciones"
    const val oidos = "Otoscopía: membranas timpánicas íntegras, nacaradas, con reflejo luminoso"
    const val pulmones = "Murmullo vesicular pasa bien en ambos campos pulmonares, sin ruidos agregados, sin tirajes"
    const val corazon = "Ruidos cardiacos rítmicos, sin soplos"
    const val abdomen = "Abdomen blando, depresible, no doloroso, ruidos hidroaéreos presentes, sin visceromegalia"
    const val neuro = "Neurológico: activo, sin signos meníngeos, desarrollo acorde a la edad"
    val todos = listOf(general, piel, orofaringe, oidos, pulmones, corazon, abdomen, neuro)
}

private const val SIGNOS_ALARMA_PEDIATRIA = "Acudir por emergencia si el niño presenta: respiración rápida o se le hunden las costillas, fiebre que no baja o convulsión, no quiere lactar ni beber, vomita todo lo que toma, está muy dormido o decaído, ojos hundidos o no orina en 6 horas"

/** Un chip base: `c(texto, etiqueta)` de la web (sin etiqueta = el texto). */
private fun c(campo: String, texto: String, etiqueta: String = texto) =
    FraseFrecuenteApp(campo = campo, texto = texto, usos = 0, etiqueta = etiqueta)

/** Base de pediatría por campo ("motivo", "tiempo", "examen", "indicaciones"). */
internal val FRASES_BASE_PEDIATRIA: Map<String, List<FraseFrecuenteApp>> = mapOf(
    "motivo" to listOf(
        "Fiebre", "Tos", "Secreción nasal", "Dolor de garganta", "Dolor de oído", "Vómitos", "Diarrea",
        "Dolor de barriga", "No quiere comer", "Llanto persistente", "Erupción en la piel",
        "Control de niño sano (CRED)", "Vacunas", "Certificado de salud escolar",
    ).map { c("motivo", it) },
    "tiempo" to listOf("Desde hoy", "1 día", "2–3 días", "1 semana", "Más de 2 semanas").map { c("tiempo", it) },
    "examen" to listOf(
        c("examen", ExamenPediatria.todos.joinToString("\n"), "Examen normal"),
        c("examen", ExamenPediatria.general, "Buen estado general"),
        c("examen", ExamenPediatria.piel, "Piel y mucosas"),
        c("examen", "Fontanela anterior normotensa", "Fontanela normal"),
        c("examen", ExamenPediatria.orofaringe),
        c("examen", "Orofaringe congestiva"),
        c("examen", "Amígdalas hipertróficas con exudado", "Amígdalas con exudado"),
        c("examen", ExamenPediatria.oidos, "Oídos normales"),
        c("examen", "Otoscopía: membrana timpánica abombada y eritematosa", "Otitis"),
        c("examen", ExamenPediatria.pulmones, "Pulmones normales"),
        c("examen", "Sibilantes espiratorios difusos, con tirajes subcostales", "Sibilantes / tirajes"),
        c("examen", ExamenPediatria.corazon, "Corazón normal"),
        c("examen", ExamenPediatria.abdomen, "Abdomen normal"),
        c("examen", "Signo del pliegue negativo, llenado capilar menor de 2 segundos", "Sin deshidratación"),
        c("examen", ExamenPediatria.neuro, "Neurológico normal"),
        c("examen", "Peso y talla adecuados para la edad", "Crecimiento adecuado"),
    ),
    "indicaciones" to listOf(
        c("indicaciones", "Paracetamol según el peso, solo si hay fiebre (más de 38 °C), como indica la receta", "Paracetamol si fiebre"),
        c("indicaciones", "Medios físicos para la fiebre: ropa ligera y baño con agua tibia", "Medios físicos"),
        c("indicaciones", "Lavados nasales con suero fisiológico antes de lactar, comer y dormir", "Lavado nasal"),
        c("indicaciones", "Sales de rehidratación oral en pequeños sorbos después de cada deposición líquida o vómito", "Sales de rehidratación"),
        c("indicaciones", "Continuar la lactancia materna y la alimentación habitual, en pequeñas cantidades y más seguido", "Seguir lactancia / alimentación"),
        c("indicaciones", "No automedicar al niño: darle solo lo indicado en la receta", "No automedicar"),
        c("indicaciones", "Vacunas al día según el calendario", "Vacunas al día"),
        c("indicaciones", "Control en 48 horas", "Control en 48 h"),
        c("indicaciones", SIGNOS_ALARMA_PEDIATRIA, "Signos de alarma (niño)"),
    ),
)

private fun lineas(texto: String): List<String> = texto.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Base + aprendidas (chipsYOpciones de la web): la base se reordena por uso
 * (estable), los bloques de varias líneas ("Examen normal") van primero, y las
 * aprendidas que no están en la base se mezclan con los simples por uso.
 */
internal fun frasesConBase(base: List<FraseFrecuenteApp>, aprendidas: List<FraseFrecuenteApp>): List<FraseFrecuenteApp> {
    if (base.isEmpty()) return aprendidas
    val usos = HashMap<String, Int>()
    for (f in aprendidas) usos[normalizarFrase(f.texto)] = (usos[normalizarFrase(f.texto)] ?: 0) + f.usos
    val baseOrden = base.map { b -> b.copy(usos = if (lineas(b.texto).size > 1) 0 else usos[normalizarFrase(b.texto)] ?: 0) }
        .withIndex().sortedWith(compareByDescending<IndexedValue<FraseFrecuenteApp>> { it.value.usos }.thenBy { it.index })
        .map { it.value }
    val bloques = baseOrden.filter { lineas(it.texto).size > 1 }
    val simples = baseOrden.filter { lineas(it.texto).size <= 1 }
    val enBase = base.flatMap { lineas(it.texto) }.map { normalizarFrase(it) }.toSet()
    val propias = aprendidas.filter { normalizarFrase(it.texto) !in enBase }
        .sortedByDescending { it.usos }
        .distinctBy { normalizarFrase(it.texto) }
    val mezcla = (simples + propias).withIndex()
        .sortedWith(compareByDescending<IndexedValue<FraseFrecuenteApp>> { it.value.usos }.thenBy { it.index })
        .map { it.value }
    return bloques + mezcla
}
