package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs

/**
 * MEDICIONES CORPORALES (Nutrición): peso, talla y medidas por visita, en la
 * pestaña ⚖️ Medidas de la ficha. Tabla `mediciones_corporales`, vía
 * /api/staff/medicion-corporal (ver [MedicionesRepo]).
 *
 * Gemelo PURO de `lib/mediciones-corporales.ts` de la web (rangos, validación)
 * más las ayudas de la pestaña (serie, deltas, resumen). Talla e IMC reusan
 * `aNumero` / `tallaEnCm` / `calcularImc` / `clasificarImc` de AtencionMedica.kt.
 *
 * Solo NUTRICIÓN, y por PACIENTE (como lo de fisio): en una clínica mixta el
 * paciente de fisioterapia no ve esta pestaña. El MAPA lo arma la web
 * (/api/staff/contexto: `especialidadesNutricion`, `soloNutricion`); un backend
 * sin esos campos deja el mapa vacío y no aparece nada nuevo.
 */
data class MapaNutricion(val ids: List<String> = emptyList(), val solo: Boolean = false) {
    /** ¿La clínica hace algo de nutrición? */
    val activo: Boolean get() = solo || ids.isNotEmpty()
}

/**
 * ¿Esta cita / tratamiento es de nutrición? Misma cadena que `citaEsFisio`:
 * especialidad de la cita → la del servicio → el profesional (si TODAS sus
 * especialidades son de nutrición). Lo que no se sabe es NO.
 */
fun citaEsNutricion(
    mapa: MapaNutricion,
    especialidadId: String? = null,
    especialidadServicioId: String? = null,
    especialidadesProfesional: List<String>? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    listOf(especialidadId, especialidadServicioId).firstOrNull { !it.isNullOrBlank() }
        ?.let { return it in mapa.ids }
    val prof = especialidadesProfesional.orEmpty().filter { it.isNotBlank() }
    return prof.isNotEmpty() && prof.all { it in mapa.ids }
}

/**
 * ¿La ficha muestra la pestaña ⚖️ Medidas? (gemelo de `pacienteEsFisio`). Clínica
 * solo de nutrición → sí; sin nutrición → no; mixta → si ya tiene mediciones,
 * si alguna atención es de nutrición o si quien mira es nutricionista.
 * [atenciones] usa la misma forma que fisio (cita / servicio / profesional).
 */
fun pacienteEsNutricion(
    mapa: MapaNutricion,
    atenciones: List<AtencionFisio> = emptyList(),
    tieneMediciones: Boolean = false,
    especialidadesDeQuienMira: List<String>? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    if (tieneMediciones) return true
    if (atenciones.any { citaEsNutricion(mapa, it.especialidadId, it.especialidadServicioId, it.especialidadesProfesional) }) return true
    return especialidadesDeQuienMira.orEmpty().any { it.isNotBlank() && it in mapa.ids }
}

// ─── Modelo ──────────────────────────────────────────────────────────────────

/** Una fila de `mediciones_corporales` (talla en cm). */
data class MedicionCorporal(
    val id: String,
    val pacienteId: String,
    val fecha: String,
    val peso: Double? = null,
    val talla: Double? = null,
    val cintura: Double? = null,
    val cadera: Double? = null,
    val perimetroAbdominal: Double? = null,
    val grasaPct: Double? = null,
    val masaMuscular: Double? = null,
    val notas: String? = null,
    val citaId: String? = null,
    val tratamientoId: String? = null,
    val terapeutaId: String? = null,
    val createdAt: String? = null,
)

private fun JsonObject.txtMed(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() && it != "null" }
private fun JsonObject.numMed(k: String): Double? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }

/** Fila del API → modelo. Sin id se descarta. (numeric de Postgres puede llegar como texto.) */
fun medicionDeJson(o: JsonObject): MedicionCorporal? {
    val id = o.txtMed("id") ?: return null
    return MedicionCorporal(
        id = id,
        pacienteId = o.txtMed("paciente_id").orEmpty(),
        fecha = o.txtMed("fecha").orEmpty().take(10),
        peso = o.numMed("peso"),
        talla = o.numMed("talla"),
        cintura = o.numMed("cintura"),
        cadera = o.numMed("cadera"),
        perimetroAbdominal = o.numMed("perimetro_abdominal"),
        grasaPct = o.numMed("grasa_pct"),
        masaMuscular = o.numMed("masa_muscular"),
        notas = o.txtMed("notas"),
        citaId = o.txtMed("cita_id"),
        tratamientoId = o.txtMed("tratamiento_id"),
        terapeutaId = o.txtMed("terapeuta_id"),
        createdAt = o.txtMed("created_at"),
    )
}

/** Cronológico (la más antigua primero), como lo devuelve el API: fecha y, a igual fecha, creación. */
fun ordenarMediciones(items: List<MedicionCorporal>): List<MedicionCorporal> =
    items.sortedWith(compareBy<MedicionCorporal>({ it.fecha }, { it.createdAt.orEmpty() }))

// ─── Rangos y validación (gemelos de RANGO_MEDICION / validarMedicion) ──────

data class RangoMedicion(val min: Double, val max: Double, val nombre: String, val unidad: String)

/** Columnas medibles, en el orden de la web. */
val CAMPOS_MEDICION: List<String> =
    listOf("peso", "talla", "cintura", "cadera", "perimetro_abdominal", "grasa_pct", "masa_muscular")

/** Rangos POSIBLES (no "normales"). Gemelos de `mediciones_corporales_rangos_chk`. */
val RANGO_MEDICION: Map<String, RangoMedicion> = mapOf(
    "peso" to RangoMedicion(0.3, 400.0, "Peso", "kg"),
    "talla" to RangoMedicion(20.0, 250.0, "Talla", "cm"),
    "cintura" to RangoMedicion(20.0, 250.0, "Cintura", "cm"),
    "cadera" to RangoMedicion(20.0, 250.0, "Cadera", "cm"),
    "perimetro_abdominal" to RangoMedicion(20.0, 250.0, "Perímetro abdominal", "cm"),
    "grasa_pct" to RangoMedicion(1.0, 80.0, "Grasa corporal", "%"),
    "masa_muscular" to RangoMedicion(1.0, 200.0, "Masa muscular", "kg"),
)

/** Lo que se escribió en el formulario: columna → texto (vacío = no se midió). */
data class BorradorMedicion(
    val pacienteId: String,
    val fecha: String? = null,
    val valores: Map<String, String> = emptyMap(),
    val notas: String? = null,
)

/** Número de una columna del borrador (talla en cm: "1.70" → 170). null = vacío; NaN = basura. */
fun numeroMedicion(campo: String, texto: String?): Double? {
    val n = aNumero(texto)
    return if (campo == "talla") tallaEnCm(n) else n
}

/** Errores para mostrar (vacío = válida). Mismos textos que la web. */
fun validarMedicion(b: BorradorMedicion): List<String> {
    val e = mutableListOf<String>()
    if (b.pacienteId.isBlank()) e.add("Falta el paciente.")
    if (!b.fecha.isNullOrEmpty() && !Regex("""^\d{4}-\d{2}-\d{2}$""").matches(b.fecha)) e.add("Fecha inválida.")
    var alguna = false
    for (k in CAMPOS_MEDICION) {
        val n = numeroMedicion(k, b.valores[k]) ?: continue
        val r = RANGO_MEDICION.getValue(k)
        if (n.isNaN()) { e.add("${r.nombre}: escribe solo el número."); continue }
        if (n < r.min || n > r.max) e.add("${r.nombre}: ${numJs(n)} ${r.unidad} está fuera de lo posible (${numJs(r.min)}–${numJs(r.max)}).")
        alguna = true
    }
    if (!alguna && e.isEmpty()) e.add("Anota al menos una medida.")
    return e
}

/** Nombres del cuerpo del POST (camelCase del API). */
private val CLAVE_API = mapOf(
    "peso" to "peso", "talla" to "talla", "cintura" to "cintura", "cadera" to "cadera",
    "perimetro_abdominal" to "perimetroAbdominal", "grasa_pct" to "grasaPct", "masa_muscular" to "masaMuscular",
)

/** Cuerpo del POST (ya validado): solo lo medido; talla en cm; notas recortadas. */
fun cuerpoMedicion(b: BorradorMedicion, citaId: String? = null, tratamientoId: String? = null): JsonObject = buildJsonObject {
    put("pacienteId", b.pacienteId)
    b.fecha?.takeIf { it.isNotBlank() }?.let { put("fecha", it) }
    for (k in CAMPOS_MEDICION) {
        val n = numeroMedicion(k, b.valores[k])
        if (n != null && !n.isNaN()) put(CLAVE_API.getValue(k), n)
    }
    b.notas?.trim()?.takeIf { it.isNotEmpty() }?.let { put("notas", it.take(2000)) }
    citaId?.let { put("citaId", it) }
    tratamientoId?.let { put("tratamientoId", it) }
}

// ─── IMC ─────────────────────────────────────────────────────────────────────

data class ImcLeido(val valor: Double, val clasificacion: String?)

/** IMC en vivo del formulario (texto de peso y talla; la talla admite metros). null si falta alguno. */
fun imcEnVivo(pesoTexto: String?, tallaTexto: String?, edad: Int? = null): ImcLeido? {
    val p = aNumero(pesoTexto)?.takeIf { !it.isNaN() }
    val t = numeroMedicion("talla", tallaTexto)?.takeIf { !it.isNaN() }
    val imc = calcularImc(p, t) ?: return null
    return ImcLeido(imc, clasificarImc(imc, edad))
}

/** La última talla registrada (para prellenar el formulario: no cambia entre visitas). */
fun ultimaTalla(items: List<MedicionCorporal>): Double? = ordenarMediciones(items).lastOrNull { it.talla != null }?.talla

/**
 * IMC de cada medición con SU peso y SU talla (gemelo de `imcDeMedicion`, igual
 * que la historia clínica): sin talla en esa visita, no hay IMC. El formulario
 * prellena la talla con la última, así las nuevas la traen.
 */
fun imcDeMedicion(m: MedicionCorporal): Double? = calcularImc(m.peso, m.talla)

fun imcPorMedicion(items: List<MedicionCorporal>): Map<String, Double?> =
    items.associate { it.id to imcDeMedicion(it) }

/** ¿Fecha posterior a hoy? (AAAA-MM-DD; como el `max` de la fecha en la web). */
fun fechaMedicionFutura(fecha: String?, hoy: String): Boolean =
    !fecha.isNullOrBlank() && fecha.take(10) > hoy.take(10)

const val ERROR_FECHA_FUTURA = "La fecha no puede ser futura."

// ─── Evolución ───────────────────────────────────────────────────────────────

/** Métricas que se grafican (en el orden del selector). */
enum class MetricaMedicion(val nombre: String, val unidad: String) {
    PESO("Peso", "kg"),
    IMC("IMC", ""),
    CINTURA("Cintura", "cm"),
    GRASA("% grasa", "%"),
    MASA_MUSCULAR("Masa muscular", "kg"),
}

/** Serie de una métrica (cronológica), solo con las visitas que la midieron. */
fun serieMetrica(items: List<MedicionCorporal>, metrica: MetricaMedicion): List<PuntoSerieHc> {
    val orden = ordenarMediciones(items)
    val imcs = if (metrica == MetricaMedicion.IMC) imcPorMedicion(orden) else emptyMap()
    return orden.mapNotNull { m ->
        val v = when (metrica) {
            MetricaMedicion.PESO -> m.peso
            MetricaMedicion.IMC -> imcs[m.id]
            MetricaMedicion.CINTURA -> m.cintura
            MetricaMedicion.GRASA -> m.grasaPct
            MetricaMedicion.MASA_MUSCULAR -> m.masaMuscular
        }
        v?.let { PuntoSerieHc(m.fecha, it) }
    }
}

/** Las métricas con al menos 2 puntos (con una sola no hay curva que mirar). */
fun metricasDisponibles(items: List<MedicionCorporal>): List<MetricaMedicion> =
    MetricaMedicion.entries.filter { serieMetrica(items, it).size >= 2 }

/** Primera → última: cambio de peso y de cintura (si hay 2 tomas de cada uno) e IMC actual. */
data class ResumenMediciones(
    val desde: String,
    val hasta: String,
    val total: Int,
    val pesoInicial: Double? = null,
    val pesoActual: Double? = null,
    val cambioPeso: Double? = null,
    val cambioCintura: Double? = null,
    val imcActual: Double? = null,
    val clasificacionImc: String? = null,
)

fun resumenMediciones(items: List<MedicionCorporal>, edad: Int? = null): ResumenMediciones? {
    val orden = ordenarMediciones(items)
    if (orden.isEmpty()) return null
    val pesos = orden.mapNotNull { it.peso }
    val cinturas = orden.mapNotNull { it.cintura }
    val imc = serieMetrica(orden, MetricaMedicion.IMC).lastOrNull()?.valor
    return ResumenMediciones(
        desde = orden.first().fecha,
        hasta = orden.last().fecha,
        total = orden.size,
        pesoInicial = pesos.firstOrNull(),
        pesoActual = pesos.lastOrNull(),
        cambioPeso = if (pesos.size >= 2) redondear1(pesos.last() - pesos.first()) else null,
        cambioCintura = if (cinturas.size >= 2) redondear1(cinturas.last() - cinturas.first()) else null,
        imcActual = imc,
        clasificacionImc = clasificarImc(imc, edad),
    )
}

/** Una fila del historial: la medición y cuánto cambió el peso desde la toma anterior con peso. */
data class FilaHistorialMedicion(val medicion: MedicionCorporal, val deltaPeso: Double?, val imc: Double?)

/** Historial para mostrar: la más reciente primero, con el delta de peso contra la anterior. */
fun historialMediciones(items: List<MedicionCorporal>): List<FilaHistorialMedicion> {
    val orden = ordenarMediciones(items)
    val imcs = imcPorMedicion(orden)
    var pesoPrevio: Double? = null
    val filas = orden.map { m ->
        val delta = if (m.peso != null && pesoPrevio != null) redondear1(m.peso - pesoPrevio!!) else null
        if (m.peso != null) pesoPrevio = m.peso
        FilaHistorialMedicion(m, delta, imcs[m.id])
    }
    return filas.asReversed()
}

/**
 * "+1.2 kg" · "−0.8 kg" · "Sin cambio". Signo NEUTRO (sin verde/rojo): bajar de
 * peso no siempre es lo buscado (desnutrición, deportistas, embarazo).
 */
fun textoCambio(delta: Double, unidad: String): String {
    val r = redondear1(delta)
    if (abs(r) < 0.05) return "Sin cambio"
    val signo = if (r > 0) "+" else "−"
    return "$signo${numeroHc(abs(r))} $unidad".trim()
}

/** Línea corta de lo medido en una visita: "Peso 72.5 kg · Talla 170 cm · Cintura 88 cm". */
fun lineaMedidas(m: MedicionCorporal): String = listOfNotNull(
    m.peso?.let { "Peso ${numeroHc(it)} kg" },
    m.talla?.let { "Talla ${numeroHc(it)} cm" },
    m.cintura?.let { "Cintura ${numeroHc(it)} cm" },
    m.cadera?.let { "Cadera ${numeroHc(it)} cm" },
    m.perimetroAbdominal?.let { "P. abdominal ${numeroHc(it)} cm" },
    m.grasaPct?.let { "Grasa ${numeroHc(it)} %" },
    m.masaMuscular?.let { "Masa muscular ${numeroHc(it)} kg" },
).joinToString(" · ")

/** Texto para el campo del formulario (sin ".0"): 170.0 → "170". */
fun textoCampoMedicion(v: Double?): String = v?.let { numeroHc(it) } ?: ""
