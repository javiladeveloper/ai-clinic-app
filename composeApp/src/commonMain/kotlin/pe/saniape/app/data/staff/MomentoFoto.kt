package pe.saniape.app.data.staff

// ─────────────────────────────────────────────────────────────────────────────
// El MOMENTO de una foto evolutiva (documentos_paciente.momento).
//
// La web guarda el nombre del tipo de imagen tal cual: "Antes", "Durante",
// "Después" (con tilde; lib/tipos-imagen.ts). La app guardaba "Despues" sin
// tilde y su comparador solo buscaba ese valor: las fotos «Después» subidas
// desde la web no se comparaban en la app. Ahora se LEEN las dos variantes
// (con o sin tilde, en mayúsculas o minúsculas) y se ESCRIBE el valor de la
// web. Las fotos viejas no se reetiquetan: se leen igual.
// ─────────────────────────────────────────────────────────────────────────────

const val MOMENTO_ANTES = "Antes"
const val MOMENTO_DURANTE = "Durante"

/** El valor que guarda la web (lib/tipos-imagen.ts): con tilde. */
const val MOMENTO_DESPUES = "Después"

/** Los momentos de la evolución, en orden: los que ofrece la app al subir. */
val MOMENTOS_EVOLUCION: List<String> = listOf(MOMENTO_ANTES, MOMENTO_DURANTE, MOMENTO_DESPUES)

private fun sinTildesMomento(s: String): String = buildString {
    for (ch in s.lowercase()) append(
        when (ch) { 'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú', 'ü' -> 'u'; else -> ch }
    )
}

/**
 * El momento como lo escribe la web: "despues", "DESPUÉS", " Despues " →
 * "Después" (igual con Antes y Durante). Otro tipo de imagen de la clínica
 * ("Inicial", "Zona de tratamiento"…) queda tal cual, sin espacios de más.
 * Vacío → null.
 */
fun momentoCanonico(m: String?): String? {
    val t = m?.trim().orEmpty()
    if (t.isEmpty()) return null
    return when (sinTildesMomento(t)) {
        "antes" -> MOMENTO_ANTES
        "durante" -> MOMENTO_DURANTE
        "despues" -> MOMENTO_DESPUES
        else -> t
    }
}

/** ¿La foto es de este momento? (con o sin tilde, mayúsculas o minúsculas). */
fun esMomento(m: String?, momento: String): Boolean = momentoCanonico(m) == momentoCanonico(momento)

/** Orden de la galería: Antes → Durante → Después; los demás, con Durante (como la web). */
fun ordenMomento(m: String?): Int = when (momentoCanonico(m)) {
    MOMENTO_ANTES -> 0
    MOMENTO_DESPUES -> 2
    else -> 1
}
