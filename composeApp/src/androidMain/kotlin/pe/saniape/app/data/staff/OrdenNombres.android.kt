package pe.saniape.app.data.staff

import java.text.Collator
import java.util.Locale

/** `localeCompare(…, 'es')` de la web: el Collator español (la ñ después de la n; tildes y mayúsculas al final). */
actual fun comparadorNombresEs(): Comparator<String> {
    val c = Collator.getInstance(Locale("es")).apply { strength = Collator.TERTIARY }
    return Comparator { a, b -> c.compare(a, b) }
}
