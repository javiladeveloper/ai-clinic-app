package pe.saniape.app.data.staff

// ─────────────────────────────────────────────────────────────────────────────
// DOCUMENTO CON TIPO (2026-11-03) — GEMELO de lib/documento-tipado.ts y del
// selector de ExamenesDerivaciones.tsx. Clínica de otorrino: «tomarle foto al
// audiograma y que quede en la historia». Al subir un documento se elige el
// tipo (los tipos de DOCUMENTO de la clínica: Audiometría, Radiografía…), que
// pasa a ser la categoría (agrupa la lista) y el nombre: «Audiometría
// 02-11-2026.jpg» en vez del «IMG_20261102_153012.jpg» del celular. Sin tipo
// elegido, como siempre (nombre del archivo, categoría «Documento»).
// ─────────────────────────────────────────────────────────────────────────────

/** Categoría de un documento subido sin tipo (la de siempre). */
const val CATEGORIA_DOCUMENTO = "Documento"

private val PROHIBIDOS_NOMBRE = Regex("""[\\/:*?"<>|]+""")
private val ESPACIOS = Regex("""\s+""")

/**
 * «<Tipo> dd-MM-aaaa.<ext>». La fecha es la del día de la clínica (Lima), como
 * el resto de la ficha. [hoy] = AAAA-MM-DD (inyectable para los tests).
 */
fun nombreDocumentoTipado(tipo: String, ext: String?, hoy: String = hoyClinicaIso()): String {
    val partes = hoy.split('-')
    val (a, m, d) = if (partes.size >= 3) Triple(partes[0], partes[1], partes[2].take(2)) else Triple(hoy, "", "")
    val limpio = tipo.trim().replace(PROHIBIDOS_NOMBRE, " ").replace(ESPACIOS, " ").take(80).ifEmpty { "Documento" }
    val sufijo = ext?.takeIf { it.isNotEmpty() }?.let { ".${it.lowercase()}" }.orEmpty()
    return "$limpio $d-$m-$a$sufijo"
}

/** La extensión de un nombre de archivo («IMG_1.JPG» → «JPG»); null si no tiene. */
fun extensionDe(nombreArchivo: String): String? =
    nombreArchivo.substringAfterLast('.', "").trim().takeIf { it.isNotEmpty() && it.length <= 8 && '/' !in it }

/**
 * Nombre y categoría con que se registra el documento: con [tipo], los del tipo;
 * sin él, el nombre del archivo y «Documento» (lo de siempre).
 */
fun nombreYCategoriaDocumento(tipo: String?, nombreArchivo: String, ext: String?, hoy: String = hoyClinicaIso()): Pair<String, String> {
    val t = tipo?.trim()?.takeIf { it.isNotEmpty() } ?: return nombreArchivo to CATEGORIA_DOCUMENTO
    return nombreDocumentoTipado(t, ext, hoy) to t
}
