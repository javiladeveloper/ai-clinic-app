package pe.saniape.app.data.staff

/** Sin Collator en común: la normalización equivalente a `localeCompare(…, 'es')`. */
actual fun comparadorNombresEs(): Comparator<String> = Comparator { a, b -> compararNombresEsAprox(a, b) }
