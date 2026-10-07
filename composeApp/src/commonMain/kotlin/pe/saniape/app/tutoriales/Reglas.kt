package pe.saniape.app.tutoriales

/**
 * Reglas puras de la UI de tutoriales (centro de ayuda, píldora, buscador).
 * Gemelas de `lib/tutoriales/filtrar.ts` de la web; aquí solo se aplican sobre
 * lo que el servidor ya filtró.
 */
object ReglasTutorial {

    const val CLAVE_PILDORAS_OFF = "pildoras:off"
    const val CONOCE_MENU = "conoce-menu"

    fun hecho(id: String, progreso: Map<String, String>): Boolean = progreso[id] == "completado"

    private val SIN_TILDE = mapOf(
        'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a', 'ã' to 'a',
        'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
        'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o', 'õ' to 'o',
        'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u',
        'ñ' to 'n', 'ç' to 'c',
    )

    /** Minúsculas y sin tildes (como `normalize('NFD')` de la web). */
    fun normalizar(s: String): String =
        s.lowercase().map { SIN_TILDE[it] ?: it }.joinToString("").trim()

    /** Buscador: TODAS las palabras deben aparecer (título, descripción o claves). */
    fun buscar(q: String, tutoriales: List<TutorialApp>): List<TutorialApp> {
        val palabras = normalizar(q).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (palabras.isEmpty()) return tutoriales
        return tutoriales.filter { t ->
            val texto = normalizar((listOf(t.titulo, t.descripcion) + t.claves).joinToString(" "))
            palabras.all { texto.contains(it) }
        }
    }

    /** Sugeridos de una pantalla, en el orden del servidor, pendientes primero. */
    fun sugeridos(guia: PantallaGuiaApp?, catalogo: CatalogoTutoriales, progreso: Map<String, String>): List<TutorialApp> {
        val lista = guia?.sugeridos.orEmpty().mapNotNull { catalogo.tutorial(it) }
        return lista.filter { !hecho(it.id, progreso) } + lista.filter { hecho(it.id, progreso) }
    }

    /**
     * Píldora "¿Primera vez aquí?": misma regla que el servidor, recalculada con
     * el progreso LOCAL (el del servidor quedó viejo en cuanto el usuario empezó
     * algo). null = no mostrar. Nunca en clínicas sin Primeros pasos (DALU).
     */
    fun pildora(
        guia: PantallaGuiaApp?,
        catalogo: CatalogoTutoriales,
        progreso: Map<String, String>,
        primerosPasosActivos: Boolean,
    ): TutorialApp? {
        val clave = guia?.clavePildora ?: return null
        if (!primerosPasosActivos) return null
        if (progreso[CLAVE_PILDORAS_OFF] != null || progreso[clave] != null) return null
        return sugeridos(guia, catalogo, progreso)
            .firstOrNull { !hecho(it.id, progreso) && progreso[it.id] == null && it.id != CONOCE_MENU }
    }

    /** Categorías del centro de ayuda (mismo orden y títulos que la web). */
    val CATEGORIAS: List<Pair<String, String>> = listOf(
        "basico" to "Lo del día a día",
        "cobros" to "Cobros y caja",
        "clinico" to "Herramientas clínicas",
        "equipo" to "Tu equipo",
        "crecer" to "Atraer pacientes",
    )

    /** Texto que acompaña la espera del paso en la tarjeta (como la web). */
    fun textoEspera(tipo: String): String? = when (tipo) {
        "clic" -> "Tócalo tú, te espero"
        "aparece" -> "Hazlo tú, te espero"
        "desaparece" -> "Termina y seguimos"
        "pantalla" -> "Ábrelo y seguimos"
        "valor" -> "Complétalo y seguimos"
        "tarea" -> "Guárdalo y listo"
        else -> null
    }
}

/**
 * Plural del nombre del personal ("Profesional" → "Profesionales",
 * "Fisioterapeuta" → "Fisioterapeutas"). Solo para el rótulo de "Más".
 */
fun pluralPersonal(singular: String): String {
    val s = singular.trim().ifBlank { "Profesional" }
    if (s.endsWith("s", ignoreCase = true)) return s
    val ultima = s.last().lowercaseChar()
    return if (ultima in "aeiouáéíóú") "${s}s" else "${s}es"
}
