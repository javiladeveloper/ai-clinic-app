package pe.saniape.app.data.staff

// ─────────────────────────────────────────────────────────────────────────────
// OTORRINOLARINGOLOGÍA (2026-11-03) — GEMELO de la web (si cambia una, cambia
// la otra):
//   · lib/flujo.ts → esOtorrino, atencionEsOtorrino
//   · lib/cie10.ts → FRECUENTES_OTORRINO
//   · lib/atencion-medica.ts → EXAMENES_FRECUENTES_OTORRINO
//
// Médica (rubro medicina_general), se reconoce por el NOMBRE. A diferencia de
// oftalmología no tiene examen estructurado ni receta de lentes: la consulta es
// la médica de siempre (con signos vitales y triaje); cambian sus frases, sus
// CIE-10 frecuentes y sus exámenes auxiliares.
// ─────────────────────────────────────────────────────────────────────────────

/** ¿Otorrinolaringología / audiología? Por el NOMBRE, sin tildes ni mayúsculas. */
fun esOtorrino(nombre: String?): Boolean = plegarTildes(nombre.orEmpty()).lowercase().let {
    it.contains("otorrino") || it.contains("otolaring") || it.contains("audiolog")
}

/** FRECUENTES_OTORRINO de lib/cie10.ts: oído (H60–H95), nariz y garganta (J30–J39). */
val FRECUENTES_OTORRINO: List<Pair<String, String>> = listOf(
    "H61.2" to "Cerumen impactado",
    "H60.9" to "Otitis externa, sin otra especificación",
    "H66.9" to "Otitis media, no especificada",
    "H65.9" to "Otitis media no supurativa, sin otra especificación",
    "H72.9" to "Perforación de la membrana timpánica, sin otra especificación",
    "H90.3" to "Hipoacusia neurosensorial, bilateral",
    "H90.5" to "Hipoacusia neurosensorial, sin otra especificación",
    "H91.1" to "Presbiacusia",
    "H83.3" to "Efectos del ruido sobre el oído interno",
    "H93.1" to "Tinnitus",
    "H81.1" to "Vértigo paroxístico benigno",
    "H81.0" to "Enfermedad de Méniere",
    "J30.4" to "Rinitis alérgica, no especificada",
    "J31.0" to "Rinitis crónica",
    "J32.9" to "Sinusitis crónica, no especificada",
    "J01.9" to "Sinusitis aguda, no especificada",
    "J34.2" to "Desviación del tabique nasal",
    "J33.9" to "Pólipo nasal, no especificado",
    "J34.3" to "Hipertrofia de los cornetes nasales",
    "R04.0" to "Epistaxis",
    "J35.0" to "Amigdalitis crónica",
    "J35.2" to "Hipertrofia de las adenoides",
    "J03.9" to "Amigdalitis aguda, no especificada",
    "J37.0" to "Laringitis crónica",
    "J38.2" to "Nódulos de las cuerdas vocales",
    "G47.3" to "Apnea del sueño",
    "T16.X" to "Cuerpo extraño en el oído",
)

/** EXAMENES_FRECUENTES_OTORRINO de lib/atencion-medica.ts: audición, vía aérea alta y vértigo. */
val EXAMENES_FRECUENTES_OTORRINO = listOf(
    "Audiometría tonal", "Logoaudiometría", "Impedanciometría (timpanometría)", "Emisiones otoacústicas",
    "Potenciales evocados auditivos (BERA)", "Nasofibroscopía", "Videolaringoscopía", "Pruebas vestibulares (VNG)",
    "TEM de senos paranasales", "TEM de oídos", "Radiografía de cavum", "Hemograma completo", "IgE total",
)
