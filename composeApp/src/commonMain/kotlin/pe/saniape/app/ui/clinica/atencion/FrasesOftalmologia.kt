package pe.saniape.app.ui.clinica.atencion

import pe.saniape.app.data.staff.FraseFrecuenteApp

// ─────────────────────────────────────────────────────────────────────────────
// FRASES RÁPIDAS DE OFTALMOLOGÍA (2026-10-30, Dr. Santiago) — GEMELO de
// `FRASES_BASE.oftalmo` de lib/frases-clinicas.ts (mismos textos, orden y
// etiquetas). Aprende en las MISMAS claves que medicina: solo cambia la base.
// El examen estructurado va en su propio bloque; `examen` es "otros hallazgos".
// ─────────────────────────────────────────────────────────────────────────────

private fun c(campo: String, texto: String, etiqueta: String = texto) =
    FraseFrecuenteApp(campo = campo, texto = texto, usos = 0, etiqueta = etiqueta)

/** Base de oftalmología por campo ("motivo", "tiempo", "examen", "indicaciones"). */
internal val FRASES_BASE_OFTALMO: Map<String, List<FraseFrecuenteApp>> = mapOf(
    "motivo" to listOf(
        "Visión borrosa de lejos", "Visión borrosa de cerca", "Control de lentes / medida de vista", "Ojo rojo",
        "Picazón en los ojos", "Ardor / sensación de arenilla", "Lagrimeo", "Dolor ocular", "Moscas volantes",
        "Destellos de luz", "Visión doble", "Control de presión ocular (glaucoma)", "Control de diabetes (fondo de ojo)",
        "Carnosidad en el ojo", "Orzuelo / perrilla", "Golpe en el ojo", "Cuerpo extraño",
    ).map { c("motivo", it) },
    "tiempo" to listOf("Desde hoy", "Unos días", "1 semana", "1 mes", "Varios meses", "Más de 1 año").map { c("tiempo", it) },
    "examen" to listOf(
        c("examen", "Motilidad ocular conservada, sin desviaciones", "Motilidad normal"),
        c("examen", "Pupilas isocóricas, fotorreactivas, sin defecto pupilar aferente", "Pupilas normales"),
        c("examen", "Cover test: ortoforia", "Ortoforia"),
        c("examen", "Visión de colores normal (Ishihara)", "Ishihara normal"),
        c("examen", "Campo visual por confrontación sin alteraciones", "Campo visual normal"),
    ),
    "indicaciones" to listOf(
        c("indicaciones", "Uso permanente de los lentes indicados en la receta", "Lentes de uso permanente"),
        c("indicaciones", "Usar los lentes para leer y para trabajos de cerca", "Lentes para cerca"),
        c("indicaciones", "Lágrimas artificiales (lubricante ocular) 1 gota en cada ojo 4 veces al día", "Lubricante ocular"),
        c("indicaciones", "No frotarse los ojos", "No frotarse los ojos"),
        c("indicaciones", "Compresas tibias sobre el párpado 3 veces al día por 10 minutos", "Compresas tibias"),
        c("indicaciones", "Pausas activas frente a la pantalla: cada 20 minutos mirar a lo lejos 20 segundos", "Pausas en pantallas"),
        c("indicaciones", "Lentes con filtro UV al estar al sol", "Protección UV"),
        c("indicaciones", "No automedicarse con gotas para los ojos", "No automedicarse"),
        c("indicaciones", "Ante dolor intenso, pérdida brusca de visión, destellos o una cortina en la visión, acudir de inmediato a emergencia", "Signos de alarma (ojos)"),
        c("indicaciones", "Control en 1 año", "Control en 1 año"),
        c("indicaciones", "Control de presión intraocular en 3 meses", "Control de PIO en 3 meses"),
    ),
)
