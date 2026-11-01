package pe.saniape.app.data.staff

/**
 * Una ampliación registrada en las notas del tratamiento. Gemelo de
 * `ampliacionesDeNotas` (lib/tratamientos-acciones.ts, web): lee la línea que
 * deja ampliarTratamientoAccion — «Ampliación 2026-10-29: +5 sesión(es) por
 * S/ 300.00 — motivo» — para mostrarla junto a los pagos (DALU, 01/11/2026).
 */
data class AmpliacionRegistrada(val fecha: String, val sesiones: Int, val moneda: String, val monto: Double, val motivo: String?)

private val LINEA_AMPLIACION = Regex("""^Ampliación (\d{4}-\d{2}-\d{2}): \+(\d+) sesión\(es\) por (\S+) ([\d.]+)(?: — (.+))?$""")

fun ampliacionesDeNotas(notas: String?): List<AmpliacionRegistrada> =
    (notas ?: "").lines().mapNotNull { linea ->
        val m = LINEA_AMPLIACION.find(linea.trim()) ?: return@mapNotNull null
        val g = m.groupValues
        AmpliacionRegistrada(g[1], g[2].toInt(), g[3], g[4].toDoubleOrNull() ?: 0.0, g[5].trim().ifBlank { null })
    }

private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "set", "oct", "nov", "dic")

/** '2026-10-29' → '29 oct'. */
fun fechaCortaAmpliacion(iso: String): String {
    val p = iso.split("-")
    val mes = p.getOrNull(1)?.toIntOrNull()?.let { MESES.getOrNull(it - 1) } ?: return iso
    return "${p.getOrNull(2)?.toIntOrNull() ?: return iso} $mes"
}

/**
 * ¿El motivo de la ampliación es obligatorio? Cuando el monto no es el de lista
 * (N × precio de la sesión). Gemelo de motivoAmpliacionObligatorio (ficha web).
 * Sin precio de lista, opcional.
 */
fun motivoAmpliacionObligatorio(lista: Double?, monto: Double): Boolean =
    lista != null && kotlin.math.abs(lista - monto) > 0.005
