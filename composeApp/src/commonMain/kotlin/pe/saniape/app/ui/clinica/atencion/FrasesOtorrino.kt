package pe.saniape.app.ui.clinica.atencion

import pe.saniape.app.data.staff.FraseFrecuenteApp

// ─────────────────────────────────────────────────────────────────────────────
// FRASES RÁPIDAS DE OTORRINOLARINGOLOGÍA (2026-11-03) — GEMELO de
// `FRASES_BASE.otorrino` de lib/frases-clinicas.ts (mismos textos, orden y
// etiquetas). Aprende en las MISMAS claves que medicina: solo cambia la base.
// Otoscopía, rinoscopía, orofaringe, cuello, diapasones y vértigo.
// ─────────────────────────────────────────────────────────────────────────────

private fun c(campo: String, texto: String, etiqueta: String = texto) =
    FraseFrecuenteApp(campo = campo, texto = texto, usos = 0, etiqueta = etiqueta)

/** Base de otorrino por campo ("motivo", "tiempo", "examen", "indicaciones"). */
internal val FRASES_BASE_OTORRINO: Map<String, List<FraseFrecuenteApp>> = mapOf(
    "motivo" to listOf(
        "Dolor de oído", "Oído tapado", "Secreción del oído", "Baja de audición", "Zumbido en el oído",
        "Mareo / vértigo", "Congestión nasal", "Sangrado nasal", "Estornudos y picazón nasal", "Ronquido",
        "Dolor de garganta", "Ronquera / cambio de voz", "Dificultad para tragar", "Cuerpo extraño",
        "Control de audición", "Evaluación preoperatoria",
    ).map { c("motivo", it) },
    "tiempo" to listOf(
        "Desde hoy", "1 día", "2–3 días", "1 semana", "1 mes", "Varios meses", "Más de 1 año",
    ).map { c("tiempo", it) },
    "examen" to listOf(
        c("examen", "Otoscopía: conducto auditivo externo permeable, membrana timpánica íntegra, nacarada, con triángulo luminoso, bilateral", "Otoscopía normal"),
        c("examen", "Otoscopía: tapón de cerumen que ocluye el conducto auditivo", "Tapón de cerumen"),
        c("examen", "Otoscopía: conducto auditivo eritematoso y edematoso, con secreción", "Otitis externa"),
        c("examen", "Otoscopía: membrana timpánica abombada, eritematosa, sin triángulo luminoso", "Otitis media"),
        c("examen", "Otoscopía: perforación de la membrana timpánica", "Perforación timpánica"),
        c("examen", "Rinoscopía anterior: mucosa rosada, tabique central, cornetes normales", "Rinoscopía normal"),
        c("examen", "Rinoscopía: mucosa pálida, edematosa, con rinorrea hialina", "Rinitis alérgica"),
        c("examen", "Rinoscopía: desviación del tabique nasal", "Desviación septal"),
        c("examen", "Rinoscopía: hipertrofia de cornetes inferiores", "Cornetes hipertróficos"),
        c("examen", "Orofaringe: amígdalas grado I, sin exudado, úvula central", "Orofaringe normal"),
        c("examen", "Amígdalas hipertróficas con exudado", "Amígdalas con exudado"),
        c("examen", "Cuello: sin adenopatías ni masas palpables", "Cuello normal"),
        c("examen", "Prueba de Rinne positiva bilateral, Weber central", "Diapasones normales"),
        c("examen", "Maniobra de Dix-Hallpike positiva", "Dix-Hallpike positiva"),
        c("examen", "Sin nistagmo espontáneo, Romberg negativo", "Sin nistagmo"),
    ),
    "indicaciones" to listOf(
        c("indicaciones", "Lavados nasales con suero fisiológico 3 veces al día", "Lavado nasal"),
        c("indicaciones", "Spray nasal indicado, 2 aplicaciones en cada fosa nasal cada 12 horas", "Spray nasal"),
        c("indicaciones", "Gotas óticas indicadas en el oído afectado, como dice la receta", "Gotas en el oído"),
        c("indicaciones", "No introducir hisopos ni objetos en los oídos", "No usar hisopos"),
        c("indicaciones", "Evitar que entre agua al oído: tapar con algodón y vaselina al bañarse", "Proteger del agua"),
        c("indicaciones", "Reposo de voz e hidratación abundante", "Reposo de voz"),
        c("indicaciones", "Levantarse despacio y evitar movimientos bruscos de la cabeza", "Cuidado con el vértigo"),
        c("indicaciones", "Traer la audiometría al próximo control", "Traer audiometría"),
        c("indicaciones", "Ante dolor intenso, fiebre alta, mareo con vómitos que no paran o sangrado nasal que no se detiene, acudir a emergencia", "Signos de alarma (ORL)"),
        c("indicaciones", "Control en 1 semana"),
    ),
)
