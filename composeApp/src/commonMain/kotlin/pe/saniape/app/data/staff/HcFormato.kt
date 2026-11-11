package pe.saniape.app.data.staff

/**
 * Cómo se lee un campo largo de la historia clínica en el celular (pedido de
 * Jonathan 10/11/2026: el examen oftalmológico «se ve desordenado», todo apiñado
 * en la columna de la derecha).
 *
 * Un valor de varios renglones («Conjuntiva: OD Tranquila · OI Tranquila\n
 * Córnea: …») se parte en filas: [FilaHc.nombre] = lo que va antes del primer
 * «:», y [FilaHc.partes] = el resto, separado por « · » cuando cada parte es de
 * un ojo (OD / OI / AO) — así cada ojo va en su renglón.
 */
data class FilaHc(val nombre: String?, val partes: List<String>)

/** ¿Este valor se muestra apilado (etiqueta arriba, filas abajo)? */
fun valorHcLargo(valor: String): Boolean = valor.contains('\n') || valor.length > 60

fun filasDeValorHc(valor: String): List<FilaHc> =
    valor.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { linea ->
        val i = linea.indexOf(':')
        // «PIO: OD 14 mmHg» → nombre «PIO»; una hora («08:40») no es un nombre.
        val nombre = if (i in 1..40 && !linea[i - 1].isDigit()) linea.substring(0, i).trim() else null
        val resto = if (nombre != null) linea.substring(i + 1).trim() else linea
        // «SC OD 20/70 · OI 20/60 | AE OD 20/25 · OI 20/25»: cada tramo «|» en su renglón.
        FilaHc(nombre, resto.split(" | ").map { it.trim() }.filter { it.isNotEmpty() }.flatMap { porOjo(it) })
    }

/**
 * Un tramo con 2+ partes de un ojo («OD 14 mmHg · OI 14 mmHg · Aire · 08:40»):
 * cada ojo en su renglón y lo demás junto («Aire (no contacto) · 08:40»). Sin
 * ojos (un texto con «·»), el tramo entero.
 */
private fun porOjo(tramo: String): List<String> {
    val trozos = tramo.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
    if (trozos.count { OJO.containsMatchIn(it) } < 2) return listOf(tramo)
    val out = mutableListOf<String>()
    var suelto = mutableListOf<String>()
    fun soltar() { if (suelto.isNotEmpty()) { out += suelto.joinToString(" · "); suelto = mutableListOf() } }
    for (t in trozos) {
        if (OJO.containsMatchIn(t)) { soltar(); out += t } else suelto += t
    }
    soltar()
    return out
}

private val OJO = Regex("^(OD|OI|AO)\\b")
