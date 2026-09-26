package pe.saniape.app.data.staff

import kotlin.math.floor

/**
 * Tests funcionales de fisioterapia (M5). Gemelo EXACTO de `lib/tests-funcionales.ts`
 * de la web: mismo catálogo, mismas fórmulas y mismos cortes de interpretación, así
 * un Oswestry cargado en la app da el mismo puntaje que en la web (lo verifican los
 * tests gemelos de `TestsFuncionalesTest`).
 *
 * Fórmulas:
 *  - Oswestry y NDI: 10 ítems 0–5 → % = suma / (5 × contestadas) × 100.
 *  - QuickDASH: 11 ítems 1–5 → ((suma / n) − 1) × 25 (mínimo 10 de 11).
 *  - Lysholm: 8 ítems con pesos propios que suman 100 (todos obligatorios).
 * En Oswestry, NDI y QuickDASH MENOS es mejor; en Lysholm MÁS es mejor.
 */

data class OpcionTest(val texto: String, val valor: Int)
data class PreguntaTest(val titulo: String, val opciones: List<OpcionTest>, val omitible: Boolean = false)

data class TestFuncional(
    val id: String,
    val nombre: String,
    val corto: String,
    val region: String,
    val instrucciones: String,
    val preguntas: List<PreguntaTest>,
    /** "%" o "pts". */
    val unidad: String,
    val mejorEsMayor: Boolean,
    val minimoContestadas: Int,
)

private fun o(textos: List<String>, valores: List<Int>? = null): List<OpcionTest> =
    textos.mapIndexed { i, t -> OpcionTest(t, valores?.get(i) ?: i) }

private val OSWESTRY = TestFuncional(
    id = "oswestry",
    nombre = "Índice de discapacidad de Oswestry",
    corto = "Oswestry",
    region = "Columna lumbar",
    instrucciones = "Marque en cada sección la frase que mejor describa su situación HOY por su dolor de espalda o pierna.",
    unidad = "%",
    mejorEsMayor = false,
    minimoContestadas = 8,
    preguntas = listOf(
        PreguntaTest("Intensidad del dolor", o(listOf(
            "Puedo soportar el dolor sin necesidad de tomar calmantes",
            "El dolor es fuerte pero me arreglo sin tomar calmantes",
            "Los calmantes me alivian completamente el dolor",
            "Los calmantes me alivian un poco el dolor",
            "Los calmantes apenas me alivian el dolor",
            "Los calmantes no me quitan el dolor y no los tomo",
        ))),
        PreguntaTest("Cuidados personales (lavarse, vestirse, etc.)", o(listOf(
            "Me las puedo arreglar solo sin que me aumente el dolor",
            "Me las puedo arreglar solo pero esto me aumenta el dolor",
            "Lavarme, vestirme, etc., me produce dolor y tengo que hacerlo despacio y con cuidado",
            "Necesito alguna ayuda pero consigo hacer la mayoría de las cosas yo solo",
            "Necesito ayuda para hacer la mayoría de las cosas",
            "No puedo vestirme, me cuesta lavarme y suelo quedarme en la cama",
        ))),
        PreguntaTest("Levantar peso", o(listOf(
            "Puedo levantar objetos pesados sin que me aumente el dolor",
            "Puedo levantar objetos pesados pero me aumenta el dolor",
            "El dolor me impide levantar objetos pesados del suelo, pero puedo hacerlo si están en un sitio cómodo (ej. en una mesa)",
            "El dolor me impide levantar objetos pesados, pero sí puedo levantar objetos ligeros o medianos si están en un sitio cómodo",
            "Sólo puedo levantar objetos muy ligeros",
            "No puedo levantar ni elevar ningún objeto",
        ))),
        PreguntaTest("Andar", o(listOf(
            "El dolor no me impide andar",
            "El dolor me impide andar más de un kilómetro",
            "El dolor me impide andar más de 500 metros",
            "El dolor me impide andar más de 250 metros",
            "Sólo puedo andar con bastón o muletas",
            "Permanezco en la cama casi todo el tiempo y tengo que ir a rastras al baño",
        ))),
        PreguntaTest("Estar sentado", o(listOf(
            "Puedo estar sentado en cualquier tipo de silla todo el tiempo que quiera",
            "Puedo estar sentado en mi silla favorita todo el tiempo que quiera",
            "El dolor me impide estar sentado más de una hora",
            "El dolor me impide estar sentado más de media hora",
            "El dolor me impide estar sentado más de diez minutos",
            "El dolor me impide estar sentado",
        ))),
        PreguntaTest("Estar de pie", o(listOf(
            "Puedo estar de pie tanto tiempo como quiera sin que me aumente el dolor",
            "Puedo estar de pie tanto tiempo como quiera pero me aumenta el dolor",
            "El dolor me impide estar de pie más de una hora",
            "El dolor me impide estar de pie más de media hora",
            "El dolor me impide estar de pie más de diez minutos",
            "El dolor me impide estar de pie",
        ))),
        PreguntaTest("Dormir", o(listOf(
            "El dolor no me impide dormir bien",
            "Sólo puedo dormir si tomo pastillas",
            "Incluso tomando pastillas duermo menos de seis horas",
            "Incluso tomando pastillas duermo menos de cuatro horas",
            "Incluso tomando pastillas duermo menos de dos horas",
            "El dolor me impide totalmente dormir",
        ))),
        PreguntaTest("Actividad sexual", o(listOf(
            "Mi actividad sexual es normal y no me aumenta el dolor",
            "Mi actividad sexual es normal pero me aumenta el dolor",
            "Mi actividad sexual es casi normal pero me aumenta mucho el dolor",
            "Mi actividad sexual se ha visto muy limitada a causa del dolor",
            "Mi actividad sexual es casi nula a causa del dolor",
            "El dolor me impide todo tipo de actividad sexual",
        )), omitible = true),
        PreguntaTest("Vida social", o(listOf(
            "Mi vida social es normal y no me aumenta el dolor",
            "Mi vida social es normal pero me aumenta el dolor",
            "El dolor no tiene un efecto importante en mi vida social, pero sí impide mis actividades más enérgicas, como bailar, etc.",
            "El dolor ha limitado mi vida social y no salgo tan a menudo",
            "El dolor ha limitado mi vida social al hogar",
            "No tengo vida social a causa del dolor",
        ))),
        PreguntaTest("Viajar", o(listOf(
            "Puedo viajar a cualquier sitio sin que me aumente el dolor",
            "Puedo viajar a cualquier sitio, pero me aumenta el dolor",
            "El dolor es fuerte, pero aguanto viajes de más de dos horas",
            "El dolor me limita a viajes de menos de una hora",
            "El dolor me limita a viajes cortos y necesarios de menos de media hora",
            "El dolor me impide viajar excepto para ir al médico o al hospital",
        ))),
    ),
)

private val NDI = TestFuncional(
    id = "ndi",
    nombre = "Índice de discapacidad cervical (NDI)",
    corto = "NDI",
    region = "Columna cervical",
    instrucciones = "Marque en cada sección la frase que mejor describa cómo le afecta HOY su dolor de cuello.",
    unidad = "%",
    mejorEsMayor = false,
    minimoContestadas = 9,
    preguntas = listOf(
        PreguntaTest("Intensidad del dolor de cuello", o(listOf(
            "No tengo dolor en este momento",
            "El dolor es muy leve en este momento",
            "El dolor es moderado en este momento",
            "El dolor es fuerte en este momento",
            "El dolor es muy fuerte en este momento",
            "En este momento el dolor es el peor que uno se puede imaginar",
        ))),
        PreguntaTest("Cuidados personales (lavarse, vestirse, etc.)", o(listOf(
            "Puedo cuidarme con normalidad sin que me aumente el dolor",
            "Puedo cuidarme con normalidad, pero esto me aumenta el dolor",
            "Cuidarme me duele, y lo hago despacio y con cuidado",
            "Aunque necesito alguna ayuda, me las arreglo para casi todos mis cuidados",
            "Todos los días necesito ayuda para la mayor parte de mis cuidados",
            "No puedo vestirme, me lavo con dificultad y me quedo en la cama",
        ))),
        PreguntaTest("Levantar pesos", o(listOf(
            "Puedo levantar objetos pesados sin aumento del dolor",
            "Puedo levantar objetos pesados, pero me aumenta el dolor",
            "El dolor me impide levantar objetos pesados del suelo, pero puedo si están en un sitio cómodo (ej. en una mesa)",
            "El dolor me impide levantar objetos pesados del suelo, pero puedo levantar objetos medianos o ligeros si están en un sitio cómodo",
            "Sólo puedo levantar objetos muy ligeros",
            "No puedo levantar ni llevar ningún tipo de peso",
        ))),
        PreguntaTest("Lectura", o(listOf(
            "Puedo leer todo lo que quiera sin que me duela el cuello",
            "Puedo leer todo lo que quiera con un dolor leve en el cuello",
            "Puedo leer todo lo que quiera con un dolor moderado en el cuello",
            "No puedo leer todo lo que quiero debido a un dolor moderado en el cuello",
            "Apenas puedo leer por el gran dolor que me produce en el cuello",
            "No puedo leer nada en absoluto",
        ))),
        PreguntaTest("Dolor de cabeza", o(listOf(
            "No tengo ningún dolor de cabeza",
            "A veces tengo un pequeño dolor de cabeza",
            "A veces tengo un dolor moderado de cabeza",
            "Con frecuencia tengo un dolor moderado de cabeza",
            "Con frecuencia tengo un dolor fuerte de cabeza",
            "Tengo dolor de cabeza casi continuo",
        ))),
        PreguntaTest("Concentrarse", o(listOf(
            "Me concentro totalmente cuando quiero, sin dificultad",
            "Me concentro totalmente cuando quiero, con alguna dificultad",
            "Tengo alguna dificultad para concentrarme cuando quiero",
            "Tengo bastante dificultad para concentrarme cuando quiero",
            "Tengo mucha dificultad para concentrarme cuando quiero",
            "No puedo concentrarme nunca",
        ))),
        PreguntaTest("Trabajo", o(listOf(
            "Puedo trabajar todo lo que quiero",
            "Puedo hacer mi trabajo habitual, pero no más",
            "Puedo hacer casi todo mi trabajo habitual, pero no más",
            "No puedo hacer mi trabajo habitual",
            "A duras penas puedo hacer algún tipo de trabajo",
            "No puedo trabajar en nada",
        ))),
        PreguntaTest("Conducir vehículos", o(listOf(
            "Puedo conducir sin dolor de cuello",
            "Puedo conducir todo lo que quiero, pero con un ligero dolor de cuello",
            "Puedo conducir todo lo que quiero, pero con un dolor moderado de cuello",
            "No puedo conducir todo lo que quiero debido al dolor de cuello",
            "Apenas puedo conducir debido al intenso dolor de cuello",
            "No puedo conducir nada por el dolor de cuello",
        )), omitible = true),
        PreguntaTest("Sueño", o(listOf(
            "No tengo ningún problema para dormir",
            "El dolor de cuello me hace perder menos de 1 hora de sueño cada noche",
            "El dolor de cuello me hace perder de 1 a 2 horas de sueño cada noche",
            "El dolor de cuello me hace perder de 2 a 3 horas de sueño cada noche",
            "El dolor de cuello me hace perder de 3 a 5 horas de sueño cada noche",
            "El dolor de cuello me hace perder de 5 a 7 horas de sueño cada noche",
        ))),
        PreguntaTest("Actividades de ocio", o(listOf(
            "Puedo hacer todas mis actividades de ocio sin dolor de cuello",
            "Puedo hacer todas mis actividades de ocio con algún dolor de cuello",
            "No puedo hacer algunas de mis actividades de ocio por el dolor de cuello",
            "Sólo puedo hacer unas pocas actividades de ocio por el dolor de cuello",
            "Apenas puedo hacer las cosas que me gustan debido al dolor de cuello",
            "No puedo realizar ninguna actividad de ocio",
        ))),
    ),
)

private val DIFICULTAD = listOf("Ninguna dificultad", "Dificultad leve", "Dificultad moderada", "Mucha dificultad", "Imposible de realizar")
private val SEVERIDAD = listOf("Ninguno", "Leve", "Moderado", "Grave", "Extremo")
private fun d15(textos: List<String>) = o(textos, listOf(1, 2, 3, 4, 5))

private val QUICKDASH = TestFuncional(
    id = "quickdash",
    nombre = "QuickDASH (brazo, hombro y mano)",
    corto = "QuickDASH",
    region = "Miembro superior",
    instrucciones = "Valore su capacidad para hacer estas actividades durante la ÚLTIMA SEMANA. Si no hizo alguna, responda cómo cree que la habría hecho.",
    unidad = "pts",
    mejorEsMayor = false,
    minimoContestadas = 10,
    preguntas = listOf(
        PreguntaTest("Abrir un frasco nuevo o con la tapa muy apretada", d15(DIFICULTAD)),
        PreguntaTest("Hacer tareas duras de la casa (p. ej. limpiar paredes, fregar el piso)", d15(DIFICULTAD)),
        PreguntaTest("Cargar una bolsa del mercado o un maletín", d15(DIFICULTAD)),
        PreguntaTest("Lavarse la espalda", d15(DIFICULTAD)),
        PreguntaTest("Usar un cuchillo para cortar la comida", d15(DIFICULTAD)),
        PreguntaTest("Actividades de ocio que exigen fuerza o impacto en brazo, hombro o mano (p. ej. martillar, tenis, golf)", d15(DIFICULTAD)),
        PreguntaTest("En la última semana, ¿su problema de brazo, hombro o mano interfirió con sus actividades sociales normales (familia, amigos, vecinos)?", d15(listOf("Nada", "Un poco", "Regular", "Bastante", "Extremadamente"))),
        PreguntaTest("En la última semana, ¿estuvo limitado en su trabajo u otras actividades diarias por su problema de brazo, hombro o mano?", d15(listOf("Nada limitado", "Un poco limitado", "Regularmente limitado", "Muy limitado", "Incapaz"))),
        PreguntaTest("Dolor en el brazo, hombro o mano", d15(SEVERIDAD)),
        PreguntaTest("Hormigueo (pinchazos) en el brazo, hombro o mano", d15(SEVERIDAD)),
        PreguntaTest("En la última semana, ¿cuánta dificultad tuvo para dormir por el dolor de brazo, hombro o mano?", d15(listOf("Ninguna dificultad", "Dificultad leve", "Dificultad moderada", "Mucha dificultad", "Tanta que no puedo dormir"))),
    ),
)

private val LYSHOLM = TestFuncional(
    id = "lysholm",
    nombre = "Escala de Lysholm (rodilla)",
    corto = "Lysholm",
    region = "Rodilla",
    instrucciones = "Marque la opción que mejor describa cómo está su rodilla HOY.",
    unidad = "pts",
    mejorEsMayor = true,
    minimoContestadas = 8,
    preguntas = listOf(
        PreguntaTest("Cojera", o(listOf("No cojeo", "Cojera leve o de vez en cuando", "Cojera intensa y constante"), listOf(5, 3, 0))),
        PreguntaTest("Apoyo (bastón o muletas)", o(listOf("No necesito", "Uso bastón o muleta", "No puedo apoyar peso"), listOf(5, 2, 0))),
        PreguntaTest("Bloqueo", o(listOf(
            "Ni bloqueo ni sensación de enganche",
            "Sensación de enganche, pero sin bloqueo",
            "Bloqueo de vez en cuando",
            "Bloqueo frecuente",
            "Rodilla bloqueada al examinarla",
        ), listOf(15, 10, 6, 2, 0))),
        PreguntaTest("Inestabilidad (la rodilla \"se va\" o falla)", o(listOf(
            "Nunca",
            "Rara vez, solo en deporte u otro esfuerzo intenso",
            "Frecuente en deporte o esfuerzo intenso (o no puedo hacerlos)",
            "De vez en cuando en las actividades diarias",
            "Con frecuencia en las actividades diarias",
            "En cada paso",
        ), listOf(25, 20, 15, 10, 5, 0))),
        PreguntaTest("Dolor", o(listOf(
            "Ninguno",
            "Leve y de vez en cuando, con esfuerzo intenso",
            "Marcado con esfuerzo intenso",
            "Marcado al caminar más de 2 km o después",
            "Marcado al caminar menos de 2 km o después",
            "Constante",
        ), listOf(25, 20, 15, 10, 5, 0))),
        PreguntaTest("Hinchazón", o(listOf("Ninguna", "Con esfuerzo intenso", "Con esfuerzo normal", "Constante"), listOf(10, 6, 2, 0))),
        PreguntaTest("Subir escaleras", o(listOf("Sin problema", "Con una ligera dificultad", "Un escalón a la vez", "No puedo"), listOf(10, 6, 2, 0))),
        PreguntaTest("Ponerse en cuclillas", o(listOf("Sin problema", "Con una ligera dificultad", "No más allá de 90°", "No puedo"), listOf(5, 4, 2, 0))),
    ),
)

val LISTA_TESTS: List<TestFuncional> = listOf(OSWESTRY, NDI, QUICKDASH, LYSHOLM)
val TESTS_FUNCIONALES: Map<String, TestFuncional> = LISTA_TESTS.associateBy { it.id }

data class PuntajeTest(val puntaje: Double?, val contestadas: Int, val total: Int, val interpretacion: String?)

/** `Math.round` de JavaScript (mitades hacia +∞): los puntajes deben coincidir con la web. */
internal fun redondearJs(x: Double): Double = floor(x + 0.5)
internal fun redondear1(n: Double): Double = redondearJs(n * 10) / 10

/**
 * Respuestas: por pregunta, el VALOR elegido (no el índice de la opción); null = sin contestar.
 * Puntaje null = no hay suficientes respuestas para un resultado válido.
 */
fun calcularPuntaje(id: String, respuestas: List<Int?>): PuntajeTest {
    val t = TESTS_FUNCIONALES[id] ?: return PuntajeTest(null, 0, 0, null)
    val vals = t.preguntas.indices.mapNotNull { respuestas.getOrNull(it) }
    val contestadas = vals.size
    val total = t.preguntas.size
    val suma = vals.sum().toDouble()
    var puntaje: Double? = null
    if (contestadas >= t.minimoContestadas && contestadas > 0) {
        puntaje = when (id) {
            "oswestry", "ndi" -> redondear1((suma / (5 * contestadas)) * 100)
            "quickdash" -> redondear1(((suma / contestadas) - 1) * 25)
            else -> suma // Lysholm: suma directa de pesos (0–100)
        }
    }
    return PuntajeTest(puntaje, contestadas, total, puntaje?.let { interpretar(id, it) })
}

/** Categoría clínica del puntaje (para el informe y la tabla). */
fun interpretar(id: String, p: Double): String = when (id) {
    "oswestry" -> when {
        p <= 20 -> "Limitación mínima"
        p <= 40 -> "Limitación moderada"
        p <= 60 -> "Limitación intensa"
        p <= 80 -> "Discapacidad"
        else -> "Limitación máxima"
    }
    "ndi" -> when {
        p < 10 -> "Sin discapacidad"
        p < 30 -> "Discapacidad leve"
        p < 50 -> "Discapacidad moderada"
        p < 70 -> "Discapacidad grave"
        else -> "Discapacidad completa"
    }
    "quickdash" -> when {
        p <= 20 -> "Discapacidad leve o nula"
        p <= 40 -> "Discapacidad moderada"
        p <= 60 -> "Discapacidad importante"
        else -> "Discapacidad grave"
    }
    else -> when {
        p >= 95 -> "Excelente"
        p >= 84 -> "Bueno"
        p >= 65 -> "Regular"
        else -> "Malo"
    }
}

/** Un número como lo imprime JavaScript: 34.0 → "34", 35.6 → "35.6". */
fun numJs(v: Double): String {
    if (v == floor(v) && kotlin.math.abs(v) < 1e15) return v.toLong().toString()
    return v.toString()
}

/** "Oswestry 34 %" / "Lysholm 88 pts". */
fun textoPuntaje(id: String, p: Double?): String {
    val t = TESTS_FUNCIONALES[id] ?: return id
    if (p == null) return "${t.corto}: incompleto"
    return "${t.corto} ${numJs(p)}${if (t.unidad == "%") " %" else " pts"}"
}
