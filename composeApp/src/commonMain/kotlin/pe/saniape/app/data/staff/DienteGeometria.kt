package pe.saniape.app.data.staff

/**
 * Geometría del diagrama de 5 caras de un diente: qué cara va en qué lado, y
 * qué cara se tocó a partir de un punto.
 *
 * Aparte de la UI y sin Compose a propósito: es exactamente donde es fácil
 * equivocarse —tocar la cara mesial y que se marque la distal— y un error ahí
 * queda escrito en la historia clínica. Así se puede probar entero.
 *
 * Gemelo de `components/odontologia/DienteSvg.tsx` en la web.
 */

/** Los cuatro lados del cuadrado y el centro. */
enum class Zona { ARRIBA, ABAJO, IZQUIERDA, DERECHA, CENTRO }

/**
 * La cara MESIAL siempre mira hacia la línea media de la boca.
 *
 * Mirando al paciente de frente, los cuadrantes 1 y 4 (y sus deciduos 5 y 8)
 * quedan a la derecha de la línea media, así que dentro del diagrama la cara
 * mesial queda a la DERECHA. En los cuadrantes 2 y 3 (6 y 7) queda a la
 * IZQUIERDA.
 */
fun mesialALaDerecha(diente: String): Boolean = when (diente.firstOrNull()) {
    '1', '4', '5', '8' -> true
    else -> false
}

/** Arcada inferior: cuadrantes 3 y 4 (y sus deciduos 7 y 8). */
fun esInferior(diente: String): Boolean = when (diente.firstOrNull()) {
    '3', '4', '7', '8' -> true
    else -> false
}

/**
 * Qué cara clínica va en cada zona del diagrama.
 *
 * - Arriba/abajo: en la arcada superior la VESTIBULAR (la que mira al labio)
 *   queda arriba; en la inferior se invierte, porque el diagrama se lee como si
 *   el odontólogo mirara la boca de frente.
 * - Izquierda/derecha: mesial hacia la línea media, distal hacia afuera.
 * - Centro: siempre OCLUSAL (la cara que muerde).
 */
fun caraEnZona(diente: String, zona: Zona): String {
    val inferior = esInferior(diente)
    val mesialDer = mesialALaDerecha(diente)
    return when (zona) {
        Zona.ARRIBA -> if (inferior) "L" else "V"
        Zona.ABAJO -> if (inferior) "V" else "L"
        Zona.IZQUIERDA -> if (mesialDer) "D" else "M"
        Zona.DERECHA -> if (mesialDer) "M" else "D"
        Zona.CENTRO -> "O"
    }
}

/**
 * Qué zona del diagrama contiene el punto (x, y).
 *
 * El diagrama es un cuadrado de lado [lado] con un cuadrado central de
 * [margen] de borde. Fuera del centro, las diagonales parten el marco en
 * cuatro trapecios: un punto pertenece al trapecio del lado al que está MÁS
 * cerca. Así el reparto coincide con lo que se ve dibujado, incluso en las
 * esquinas.
 *
 * Devuelve null si el punto cae fuera del cuadrado.
 */
fun zonaEn(x: Float, y: Float, lado: Float, margen: Float): Zona? {
    if (x < 0f || y < 0f || x > lado || y > lado) return null
    if (x >= margen && x <= lado - margen && y >= margen && y <= lado - margen) return Zona.CENTRO
    // Distancia a cada lado: gana el más cercano. Es lo mismo que comparar con
    // las dos diagonales, pero se lee sin geometría.
    val dArriba = y
    val dAbajo = lado - y
    val dIzq = x
    val dDer = lado - x
    val minimo = minOf(dArriba, dAbajo, dIzq, dDer)
    return when (minimo) {
        dArriba -> Zona.ARRIBA
        dAbajo -> Zona.ABAJO
        dIzq -> Zona.IZQUIERDA
        else -> Zona.DERECHA
    }
}

/** El nombre largo de una cara, para lo que lee el odontólogo. */
fun nombreCara(cara: String): String = when (cara) {
    "O" -> "oclusal"
    "M" -> "mesial"
    "D" -> "distal"
    "V" -> "vestibular"
    "L" -> "lingual"
    else -> cara
}
