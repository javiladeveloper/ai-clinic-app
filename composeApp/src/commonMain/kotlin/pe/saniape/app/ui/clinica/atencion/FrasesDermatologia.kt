package pe.saniape.app.ui.clinica.atencion

import pe.saniape.app.data.staff.FraseFrecuenteApp

// ─────────────────────────────────────────────────────────────────────────────
// FRASES RÁPIDAS DE DERMATOLOGÍA (2026-10-16) — GEMELO de `FRASES_BASE.derma`
// de lib/frases-clinicas.ts (mismos textos, mismo orden, mismas etiquetas).
// Antes la consulta de dermatología traía los chips de medicina ("Fiebre",
// "Tos"…). Aprende en las MISMAS claves que medicina: solo cambia la base.
// ─────────────────────────────────────────────────────────────────────────────

private object ExamenDerma {
    const val general = "Paciente en aparente buen estado general, lúcido, orientado"
    const val piel = "Piel de coloración, temperatura e hidratación conservadas"
    const val lesiones = "Sin lesiones primarias ni secundarias en el resto del tegumento"
    const val mucosas = "Mucosas sin lesiones"
    const val anexos = "Pelo y uñas sin alteraciones"
    const val ganglios = "Sin adenopatías palpables"
    val todos = listOf(general, piel, lesiones, mucosas, anexos, ganglios)
}

private const val SIGNOS_ALARMA_DERMA = "Acudir antes del control si la lesión crece, sangra, cambia de color o forma, aparecen ampollas, fiebre o la reacción se extiende"

private fun c(campo: String, texto: String, etiqueta: String = texto) =
    FraseFrecuenteApp(campo = campo, texto = texto, usos = 0, etiqueta = etiqueta)

/** Base de dermatología por campo ("motivo", "tiempo", "examen", "indicaciones"). */
internal val FRASES_BASE_DERMA: Map<String, List<FraseFrecuenteApp>> = mapOf(
    "motivo" to listOf(
        "Granitos / acné", "Manchas en la piel", "Picazón", "Caída de cabello", "Lunar que cambia",
        "Piel seca / eccema", "Verrugas", "Enrojecimiento / rosácea", "Uñas", "Hongos en la piel",
        "Ronchas / urticaria", "Caspa / dermatitis seborreica", "Control de tratamiento",
    ).map { c("motivo", it) },
    "tiempo" to listOf("Desde hace días", "1 semana", "1 mes", "Varios meses", "Más de 1 año").map { c("tiempo", it) },
    "examen" to listOf(
        c("examen", ExamenDerma.todos.joinToString("\n"), "Examen normal"),
        c("examen", ExamenDerma.general, "Buen estado general"),
        c("examen", ExamenDerma.piel, "Piel normal"),
        c("examen", "Comedones abiertos y cerrados en frente, nariz y mentón", "Acné comedoniano"),
        c("examen", "Pápulas y pústulas inflamatorias en rostro", "Acné inflamatorio"),
        c("examen", "Máculas hiperpigmentadas de bordes regulares en zonas fotoexpuestas", "Manchas / melasma"),
        c("examen", "Eritema centrofacial con telangiectasias", "Rosácea"),
        c("examen", "Placas eritematodescamativas", "Placas eritematosas"),
        c("examen", "Xerosis cutánea con signos de rascado", "Piel seca / rascado"),
        c("examen", "Nevus melanocítico de bordes regulares, color homogéneo, menor de 6 mm", "Lunar de aspecto benigno"),
        c("examen", "Lesión pigmentada con criterios ABCDE de alarma (asimetría, bordes irregulares, varios colores, más de 6 mm o cambios)", "Lunar con criterios ABCDE"),
        c("examen", "Pápulas hiperqueratósicas de superficie rugosa", "Verrugas"),
        c("examen", "Engrosamiento, cambio de color y onicólisis de la lámina ungueal", "Uña engrosada"),
        c("examen", "Disminución difusa de la densidad capilar, sin placas alopécicas", "Caída difusa del cabello"),
        c("examen", ExamenDerma.ganglios, "Sin adenopatías"),
    ),
    "indicaciones" to listOf(
        c("indicaciones", "Protector solar FPS 50 o más cada 3 horas, también en días nublados", "Protector solar"),
        c("indicaciones", "Limpieza de la piel dos veces al día con jabón suave (syndet), sin frotar", "Limpieza suave"),
        c("indicaciones", "Hidratar la piel con crema emoliente después del baño", "Hidratar la piel"),
        c("indicaciones", "Baño corto con agua tibia; evitar el agua caliente y los jabones perfumados", "Baño con agua tibia"),
        c("indicaciones", "No manipular, rascar ni exprimir las lesiones", "No manipular lesiones"),
        c("indicaciones", "Aplicar el tratamiento tópico en capa fina, solo en las zonas indicadas, según la receta", "Tópico en capa fina"),
        c("indicaciones", "No automedicarse ni usar cremas con corticoides por cuenta propia", "No automedicarse"),
        c("indicaciones", SIGNOS_ALARMA_DERMA, "Signos de alarma (piel)"),
        c("indicaciones", "Control en 4 semanas", "Control en 4 semanas"),
    ),
)
