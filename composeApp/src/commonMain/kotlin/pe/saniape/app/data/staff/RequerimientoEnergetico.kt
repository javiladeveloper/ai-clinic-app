package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * REQUERIMIENTO ENERGÉTICO (Nutrición): la calculadora del plan alimentario.
 * Gemelo PURO de `lib/requerimiento-energetico.ts` de la web (mismos números,
 * mismos textos, mismo redondeo de JavaScript):
 *
 *   TMB (Mifflin-St Jeor):  hombre 10·peso + 6.25·talla − 5·edad + 5
 *                           mujer  10·peso + 6.25·talla − 5·edad − 161
 *   GET = TMB × factor de actividad
 *   kcal sugeridas = GET + ajuste del objetivo (bajar −500 · mantener 0 · subir +300),
 *   nunca por debajo de 1200 (mujer) / 1500 (hombre), redondeadas a 10 kcal.
 *
 * Es una SUGERENCIA: el nutricionista la usa o la cambia. `pacientes` no tiene
 * sexo: se pregunta en la calculadora y queda solo en la foto (`calculo`).
 */
enum class Sexo(val clave: String, val etiqueta: String) {
    MUJER("mujer", "Mujer"),
    HOMBRE("hombre", "Hombre");

    companion object {
        fun de(clave: String?): Sexo? = entries.firstOrNull { it.clave == clave }
    }
}

enum class NivelActividad(val clave: String, val factor: Double, val etiqueta: String) {
    SEDENTARIO("sedentario", 1.2, "Sedentario (poco o nada de ejercicio)"),
    LIGERA("ligera", 1.375, "Ligera (1–3 días por semana)"),
    MODERADA("moderada", 1.55, "Moderada (3–5 días por semana)"),
    INTENSA("intensa", 1.725, "Intensa (6–7 días por semana)"),
    MUY_INTENSA("muy_intensa", 1.9, "Muy intensa (trabajo físico o 2 veces al día)");

    companion object {
        fun de(clave: String?): NivelActividad? = entries.firstOrNull { it.clave == clave }
    }
}

enum class ObjetivoPeso(val clave: String, val ajuste: Int, val etiqueta: String) {
    BAJAR("bajar", -500, "Bajar de peso"),
    MANTENER("mantener", 0, "Mantener el peso"),
    SUBIR("subir", 300, "Subir de peso");

    companion object {
        fun de(clave: String?): ObjetivoPeso? = entries.firstOrNull { it.clave == clave }
    }
}

/** Piso: nunca sugerir menos de esto sin supervisión estrecha. */
fun kcalMinima(sexo: Sexo): Int = if (sexo == Sexo.MUJER) 1200 else 1500

/** Reparto por defecto al usar las kcal de la calculadora (editable). */
object MacrosPorDefecto {
    const val PROTEINAS = 25
    const val CARBOHIDRATOS = 50
    const val GRASAS = 25
}

/** kcal por gramo. */
object KcalPorGramo {
    const val PROTEINAS = 4.0
    const val CARBOHIDRATOS = 4.0
    const val GRASAS = 9.0
}

data class RangoCalculo(val min: Int, val max: Int)

object RangosCalculo {
    val EDAD = RangoCalculo(10, 110)
    val PESO = RangoCalculo(20, 400)
    val TALLA = RangoCalculo(100, 250)
}

/** Lo que se escribió en la calculadora (null = vacío o sin elegir). Talla en cm. */
data class DatosCalculo(
    val sexo: Sexo? = null,
    val edad: Double? = null,
    val peso: Double? = null,
    val talla: Double? = null,
    val actividad: NivelActividad? = null,
    val objetivo: ObjetivoPeso? = null,
)

/** Foto que se guarda en `planes_alimentacion.calculo`. */
data class SnapshotCalculo(
    val sexo: Sexo,
    val edad: Double,
    val peso: Double,
    val talla: Double,
    val actividad: NivelActividad,
    val objetivo: ObjetivoPeso,
    val tmb: Int,
    val factor: Double,
    val get: Int,
    val ajuste: Int,
    /** El piso subió la sugerencia (GET + ajuste quedaba por debajo). */
    val conPiso: Boolean,
    val kcal: Int,
) {
    val metodo: String get() = "mifflin_st_jeor"
}

private fun redondear10(n: Double): Int = (redondearJs(n / 10) * 10).toInt()

private fun fueraDe(v: Double?, r: RangoCalculo): Boolean = v == null || !v.isFinite() || v < r.min || v > r.max

/** Errores para mostrar (vacío = se puede calcular). Mismos textos y orden que la web. */
fun validarDatosCalculo(d: DatosCalculo): List<String> {
    val e = mutableListOf<String>()
    if (d.sexo == null) e.add("Elige el sexo.")
    if (fueraDe(d.edad, RangosCalculo.EDAD)) e.add("Edad: entre ${RangosCalculo.EDAD.min} y ${RangosCalculo.EDAD.max} años.")
    if (fueraDe(d.peso, RangosCalculo.PESO)) e.add("Peso: entre ${RangosCalculo.PESO.min} y ${RangosCalculo.PESO.max} kg.")
    if (fueraDe(d.talla, RangosCalculo.TALLA)) e.add("Talla: entre ${RangosCalculo.TALLA.min} y ${RangosCalculo.TALLA.max} cm.")
    if (d.actividad == null) e.add("Elige el nivel de actividad.")
    if (d.objetivo == null) e.add("Elige el objetivo.")
    return e
}

/** Tasa metabólica basal (Mifflin-St Jeor), sin redondear. */
fun tasaMetabolicaBasal(sexo: Sexo, peso: Double, talla: Double, edad: Double): Double =
    10 * peso + 6.25 * talla - 5 * edad + (if (sexo == Sexo.HOMBRE) 5 else -161)

/** La sugerencia, o null si los datos no son válidos (ver [validarDatosCalculo]). */
fun calcularRequerimiento(d: DatosCalculo): SnapshotCalculo? {
    if (validarDatosCalculo(d).isNotEmpty()) return null
    val sexo = d.sexo!!; val edad = d.edad!!; val peso = d.peso!!; val talla = d.talla!!
    val actividad = d.actividad!!; val objetivo = d.objetivo!!
    val tmb = tasaMetabolicaBasal(sexo, peso, talla, edad)
    val get = tmb * actividad.factor
    val bruto = get + objetivo.ajuste
    val piso = kcalMinima(sexo).toDouble()
    return SnapshotCalculo(
        sexo = sexo, edad = edad, peso = peso, talla = talla, actividad = actividad, objetivo = objetivo,
        tmb = redondearJs(tmb).toInt(), factor = actividad.factor, get = redondear10(get), ajuste = objetivo.ajuste,
        conPiso = bruto < piso, kcal = redondear10(maxOf(bruto, piso)),
    )
}

/** La foto como la guarda la web (mismas claves que `SnapshotCalculo` de TypeScript). */
fun SnapshotCalculo.aJson(): JsonObject = buildJsonObject {
    put("metodo", metodo)
    put("sexo", sexo.clave)
    put("edad", numeroJson(edad))
    put("peso", numeroJson(peso))
    put("talla", numeroJson(talla))
    put("actividad", actividad.clave)
    put("objetivo", objetivo.clave)
    put("tmb", tmb)
    put("factor", factor)
    put("get", get)
    put("ajuste", ajuste)
    put("conPiso", conPiso)
    put("kcal", kcal)
}

/** 60.0 → 60 (entero en el JSON, como lo escribe JavaScript); 60.5 se deja. */
private fun numeroJson(v: Double): Number = if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong() else v

/** Gramos por día de un macro para esas kcal y porcentaje (null si falta algo). */
fun gramosMacro(kcal: Double?, pct: Double?, kcalPorGramo: Double): Int? {
    if (kcal == null || !kcal.isFinite() || kcal <= 0 || pct == null || !pct.isFinite()) return null
    return redondearJs((kcal * pct / 100) / kcalPorGramo).toInt()
}

data class GramosMacros(val proteinas: Int?, val carbohidratos: Int?, val grasas: Int?)

/** Gemelo de `gramosMacros`: proteínas y carbohidratos /4, grasas /9. */
fun gramosMacros(kcal: Double?, proteinas: Double?, carbohidratos: Double?, grasas: Double?): GramosMacros = GramosMacros(
    gramosMacro(kcal, proteinas, KcalPorGramo.PROTEINAS),
    gramosMacro(kcal, carbohidratos, KcalPorGramo.CARBOHIDRATOS),
    gramosMacro(kcal, grasas, KcalPorGramo.GRASAS),
)
