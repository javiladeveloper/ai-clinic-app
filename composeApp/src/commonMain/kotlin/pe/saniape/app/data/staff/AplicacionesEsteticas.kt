package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 💉 MAPA DE APLICACIÓN (estética): qué se inyectó, dónde, cuánto, con qué
 * producto, lote y vencimiento — por sesión. Tabla `aplicaciones_esteticas`,
 * vía /api/staff/aplicacion-estetica (ver [AplicacionesEsteticasRepo]).
 *
 * Gemelo PURO de `lib/aplicaciones-esteticas.ts` de la web: catálogo (zonas con
 * id ESTABLE, tipos de producto, unidades, productos comunes, rangos
 * orientativos), normalización y validación (mismos textos), totales por zona y
 * por producto (el servidor los vuelve a calcular), unidades cobrables (solo
 * para PRE-LLENAR el formulario del tratamiento: el dinero no se toca), tiempo
 * desde la última toxina, "repetir la sesión anterior" y los chips de producto.
 * Los RANGOS son orientativos: avisan, NUNCA bloquean.
 *
 * Solo ESTÉTICA y por PACIENTE: el mapa ([MapaEstetica]) lo arma la web
 * (/api/staff/contexto: `especialidadesEstetica`, `soloEstetica`, por el rubro
 * guardado — capilar no cuenta); un backend sin esos campos deja el mapa vacío
 * y la pestaña no aparece.
 */

// ─── ¿Es estética? ───────────────────────────────────────────────────────────

/** Qué especialidades de la clínica son de estética. `solo` = todas lo son. */
data class MapaEstetica(val ids: List<String> = emptyList(), val solo: Boolean = false) {
    val activo: Boolean get() = solo || ids.isNotEmpty()
}

/** ¿Esta cita / tratamiento es de estética? Misma cadena que `citaEsFisio`. */
fun citaEsEstetica(
    mapa: MapaEstetica,
    especialidadId: String? = null,
    especialidadServicioId: String? = null,
    especialidadesProfesional: List<String>? = null,
): Boolean = atencionEnMapaEspecialidad(mapa.ids, mapa.solo, especialidadId, especialidadServicioId, especialidadesProfesional)

/** ¿La ficha muestra la pestaña 💉 Aplicaciones? (gemelo de `pacienteEsEstetica`). */
fun pacienteEsEstetica(
    mapa: MapaEstetica,
    atenciones: List<AtencionFisio> = emptyList(),
    tieneAplicaciones: Boolean = false,
    especialidadesDeQuienMira: List<String>? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    if (tieneAplicaciones) return true
    if (atenciones.any { citaEsEstetica(mapa, it.especialidadId, it.especialidadServicioId, it.especialidadesProfesional) }) return true
    return especialidadesDeQuienMira.orEmpty().any { it.isNotBlank() && it in mapa.ids }
}

// ─── Catálogo ────────────────────────────────────────────────────────────────

data class TipoProductoDef(val id: String, val nombre: String, val corto: String, val icono: String, val unidades: List<String>)

val TIPOS_PRODUCTO: List<TipoProductoDef> = listOf(
    TipoProductoDef("toxina", "Toxina botulínica", "Toxina", "💉", listOf("U")),
    TipoProductoDef("acido_hialuronico", "Ácido hialurónico", "Hialurónico", "💧", listOf("ml", "jeringa")),
    TipoProductoDef("bioestimulador", "Bioestimulador", "Bioestimulador", "✨", listOf("ml", "vial", "jeringa")),
    TipoProductoDef("regenerativo", "PDRN / exosomas / skinbooster", "Regenerativo", "🧬", listOf("ml", "vial")),
    TipoProductoDef("otro", "Otro", "Otro", "•", listOf("ml", "U", "vial", "jeringa")),
)
private val IDS_TIPO = TIPOS_PRODUCTO.map { it.id }
val UNIDADES_DOSIS: List<String> = listOf("U", "ml", "vial", "jeringa")

val TECNICAS_APLICACION: List<OpcionPodo> = listOf(OpcionPodo("aguja", "Aguja"), OpcionPodo("canula", "Cánula"))

val PROFUNDIDADES: List<OpcionPodo> = listOf(
    OpcionPodo("intradermica", "Intradérmica"),
    OpcionPodo("dermica_profunda", "Dérmica profunda"),
    OpcionPodo("subcutanea", "Subcutánea"),
    OpcionPodo("supraperiostica", "Supraperióstica"),
    OpcionPodo("intramuscular", "Intramuscular"),
)

/** El tipo (desconocido → "Otro"). */
fun tipoProducto(id: String?): TipoProductoDef = TIPOS_PRODUCTO.firstOrNull { it.id == id } ?: TIPOS_PRODUCTO.last()

/** Unidad por defecto de cada tipo (toxina en U; rellenos y el resto en ml). */
fun unidadPorDefecto(tipo: String?): String = tipoProducto(tipo).unidades.first()

/** Una zona del rostro. [lado] del PACIENTE (null = línea media). */
data class ZonaFacial(
    val id: String,
    val nombre: String,
    val corto: String,
    /** "frontal" | "lateral". */
    val vista: String,
    val lado: String?,
    val profundidadRelleno: String? = null,
    /** Alto riesgo de oclusión vascular con relleno (Beleznay 2015). */
    val riesgoVascular: Boolean = false,
    /** Rangos orientativos por sesión: "toxina" (U de onabotulinumtoxina A) y "acido_hialuronico" (ml). */
    val rangos: Map<String, Pair<Double, Double>> = emptyMap(),
)

private fun zf(id: String, nombre: String, corto: String, vista: String, lado: String?, prof: String? = null,
               riesgo: Boolean = false, rangos: Map<String, Pair<Double, Double>> = emptyMap()) =
    ZonaFacial(id, nombre, corto, vista, lado, prof, riesgo, rangos)

/** Pares derecha / izquierda con los mismos rangos. */
private fun parZf(base: String, nombre: String, corto: String, prof: String? = null, riesgo: Boolean = false,
                  rangos: Map<String, Pair<Double, Double>> = emptyMap(), vista: String = "frontal") = listOf(
    zf("${base}_der", "$nombre derecha", "$corto D", vista, "der", prof, riesgo, rangos),
    zf("${base}_izq", "$nombre izquierda", "$corto I", vista, "izq", prof, riesgo, rangos),
)

private const val TOX = "toxina"
private const val AH = "acido_hialuronico"

/** Las zonas anatómicas (rangos ORIENTATIVOS por sesión; por lado cuando la zona es de un lado). */
val ZONAS_FACIALES: List<ZonaFacial> = buildList {
    // ── Tercio superior ──
    add(zf("frente", "Frente", "Frente", "frontal", null, riesgo = true, rangos = mapOf(TOX to (6.0 to 20.0))))
    add(zf("glabela", "Entrecejo (glabela)", "Glabela", "frontal", null, riesgo = true, rangos = mapOf(TOX to (15.0 to 25.0))))
    addAll(parZf("cola_ceja", "Cola de ceja", "Ceja", rangos = mapOf(TOX to (2.0 to 5.0))))
    addAll(parZf("patas_gallo", "Patas de gallo", "P. gallo", rangos = mapOf(TOX to (6.0 to 15.0))))
    addAll(parZf("sien", "Sien", "Sien", "supraperiostica", true, mapOf(AH to (0.5 to 1.5))))
    // ── Tercio medio ──
    addAll(parZf("ojera", "Ojera (valle lágrima)", "Ojera", "supraperiostica", true, mapOf(AH to (0.2 to 0.6))))
    add(zf("nariz_lineas", "Nariz — líneas de conejo", "L. conejo", "frontal", null, rangos = mapOf(TOX to (2.0 to 8.0))))
    add(zf("nariz", "Nariz (rinomodelación)", "Nariz", "frontal", null, "supraperiostica", true, mapOf(AH to (0.1 to 0.5))))
    addAll(parZf("malar", "Pómulo (malar)", "Malar", "supraperiostica", rangos = mapOf(AH to (0.5 to 1.5))))
    addAll(parZf("surco_nasogeniano", "Surco nasogeniano", "Surco", "subcutanea", true, mapOf(AH to (0.5 to 1.5))))
    // ── Tercio inferior ──
    add(zf("codigo_barras", "Código de barras (peribucal)", "C. barras", "frontal", null, "dermica_profunda", rangos = mapOf(TOX to (2.0 to 6.0), AH to (0.1 to 0.5))))
    add(zf("labio_superior", "Labio superior", "Labio sup.", "frontal", null, "subcutanea", true, mapOf(TOX to (2.0 to 6.0), AH to (0.3 to 1.0))))
    add(zf("labio_inferior", "Labio inferior", "Labio inf.", "frontal", null, "subcutanea", true, mapOf(AH to (0.3 to 1.0))))
    addAll(parZf("comisura", "Comisura", "Comisura", "subcutanea", rangos = mapOf(TOX to (2.0 to 5.0), AH to (0.2 to 1.0))))
    add(zf("menton", "Mentón", "Mentón", "frontal", null, "supraperiostica", rangos = mapOf(TOX to (4.0 to 10.0), AH to (0.5 to 2.0))))
    addAll(parZf("mandibula", "Mandíbula", "Mandíb.", "supraperiostica", rangos = mapOf(AH to (0.5 to 2.0))))
    addAll(parZf("masetero", "Masetero", "Masetero", rangos = mapOf(TOX to (20.0 to 40.0))))
    add(zf("cuello", "Cuello (bandas platismales)", "Cuello", "frontal", null, rangos = mapOf(TOX to (20.0 to 50.0))))
    // ── Perfil ──
    add(zf("dorso_nasal", "Dorso nasal", "Dorso", "lateral", null, "supraperiostica", true, mapOf(AH to (0.1 to 0.5))))
    add(zf("punta_nasal", "Punta nasal", "Punta", "lateral", null, "supraperiostica", true, mapOf(AH to (0.1 to 0.3))))
    add(zf("menton_proyeccion", "Proyección de mentón", "Proy. mentón", "lateral", null, "supraperiostica", rangos = mapOf(AH to (0.5 to 2.0))))
    addAll(parZf("angulo_mandibular", "Ángulo mandibular", "Áng. mand.", "supraperiostica", rangos = mapOf(AH to (0.5 to 1.5)), vista = "lateral"))
}

private val ZONA_FACIAL_POR_ID = ZONAS_FACIALES.associateBy { it.id }
fun zonaFacial(id: String?): ZonaFacial? = id?.let { ZONA_FACIAL_POR_ID[it] }

/** Lo que se dibuja: "frontal", "lateral_der" o "lateral_izq" (lado del PACIENTE). */
val VISTAS_MAPA: List<Pair<String, String>> = listOf("frontal" to "Frente", "lateral_der" to "Perfil D", "lateral_izq" to "Perfil I")

/** Las zonas que se tocan en esa vista (el perfil derecho muestra la línea media y las del lado derecho). */
fun zonasDeVista(vista: String): List<ZonaFacial> {
    if (vista == "frontal") return ZONAS_FACIALES.filter { it.vista == "frontal" }
    val lado = if (vista == "lateral_der") "der" else "izq"
    return ZONAS_FACIALES.filter { it.vista == "lateral" && (it.lado == null || it.lado == lado) }
}

/** En qué vista se dibuja una zona (para abrir el mapa donde corresponde). */
fun vistaDeZona(id: String): String {
    val x = zonaFacial(id)
    if (x == null || x.vista == "frontal") return "frontal"
    return if (x.lado == "izq") "lateral_izq" else "lateral_der"
}

/** Productos comunes (chips). Marca = texto libre. Antes que estos van los que la clínica ya usó. */
val PRODUCTOS_COMUNES: List<Pair<String, String>> = listOf(
    "Botox" to "toxina", "Xeomin" to "toxina", "Dysport" to "toxina", "Botulax" to "toxina", "Nabota" to "toxina",
    "Juvéderm" to "acido_hialuronico", "Restylane" to "acido_hialuronico", "Belotero" to "acido_hialuronico",
    "Neuramis" to "acido_hialuronico", "Revolax" to "acido_hialuronico",
    "Radiesse (hidroxiapatita de calcio)" to "bioestimulador", "Sculptra (ácido poliláctico)" to "bioestimulador",
    "HArmonyCa" to "bioestimulador",
    "Rejuran (PDRN)" to "regenerativo", "Exosomas" to "regenerativo",
)

private val PALABRAS_TIPO: List<Pair<String, List<String>>> = listOf(
    "toxina" to listOf("toxina", "botul", "botox", "xeomin", "dysport", "nabota", "botulax", "letybo", "relatox", "neuronox", "meditoxin", "azzalure", "bocouture", "vistabel"),
    "regenerativo" to listOf("pdrn", "rejuran", "exosom", "polinucleot", "skinbooster", "nctf", "mesoterapia", "prp", "plasma", "salmon"),
    "bioestimulador" to listOf("radiesse", "sculptra", "harmonyca", "hidroxiapatita", "polilact", "poli-l", "policaprolact", "ellanse", "bioestimul", "gouri"),
    "acido_hialuronico" to listOf("hialuron", "juvederm", "restylane", "belotero", "neuramis", "revolax", "teosyal", "stylage", "saypha", "profhilo", "relleno", "filler"),
)

/** Gemelo de `plegarTexto`: sin tildes, en minúscula, espacios colapsados. */
fun plegarProducto(s: String?): String = plegarTextoIndicacion(s)

/** El tipo que delata el nombre del producto ("Botox 100U" → toxina). null = no se sabe. */
fun inferirTipoProducto(producto: String?): String? {
    val t = plegarProducto(producto)
    if (t.isEmpty()) return null
    for ((tipo, palabras) in PALABRAS_TIPO) if (palabras.any { t.contains(it) }) return tipo
    return null
}

/** Profundidad por defecto: toxina intramuscular; relleno la habitual de la zona; el resto, subcutánea. */
fun profundidadPorDefecto(tipo: String, zonaId: String): String? = when (tipo) {
    "toxina" -> "intramuscular"
    "acido_hialuronico" -> zonaFacial(zonaId)?.profundidadRelleno ?: "subcutanea"
    "bioestimulador" -> "subcutanea"
    "regenerativo" -> "intradermica"
    else -> null
}

/** Tipo por defecto de una zona: si solo tiene referencia de relleno, relleno; si no, toxina. */
fun tipoPorZona(zonaId: String): String {
    val r = zonaFacial(zonaId)?.rangos.orEmpty()
    return if (r.containsKey(AH) && !r.containsKey(TOX)) AH else TOX
}

// ─── Números ─────────────────────────────────────────────────────────────────

/** Como `Math.round(n * 100) / 100` de JS (mitad hacia arriba). */
fun redondearDosis(n: Double): Double = floor(n * 100 + 0.5) / 100

/** "0.5" → "0,5"; enteros sin decimales. */
fun numeroDosis(n: Double): String {
    val r = redondearDosis(n)
    val s = if (r == floor(r) && kotlin.math.abs(r) < 1e15) r.toLong().toString() else r.toString()
    return s.replace('.', ',')
}

/** "20 U", "0,5 ml", "1 vial", "2 viales", "1 jeringa". */
fun textoDosis(n: Double, unidad: String): String = when (unidad) {
    "vial" -> "${numeroDosis(n)} ${if (n == 1.0) "vial" else "viales"}"
    "jeringa" -> "${numeroDosis(n)} ${if (n == 1.0) "jeringa" else "jeringas"}"
    else -> "${numeroDosis(n)} $unidad"
}

/** Lee un número que puede venir con coma decimal ("0,5"). NaN si no es número. */
fun leerNumeroDosis(v: String?): Double {
    val t = v?.trim().orEmpty()
    if (t.isEmpty()) return Double.NaN
    return t.replace(',', '.').toDoubleOrNull() ?: Double.NaN
}

// ─── Rangos orientativos (avisan, nunca bloquean) ────────────────────────────

/** Dysport (abobotulinumtoxina A): ~2,5 U por cada U de Botox/Xeomin. */
fun factorToxina(producto: String?): Double =
    if (Regex("dysport|abobotul|azzalure").containsMatchIn(plegarProducto(producto))) 2.5 else 1.0

data class RangoTipico(val min: Double, val max: Double, val unidad: String, val texto: String, val conversion: Boolean)

/** El rango habitual de esa zona para ese tipo de producto (o null si no hay referencia). */
fun rangoTipico(zonaId: String, tipo: String, producto: String? = null): RangoTipico? {
    if (tipo != TOX && tipo != AH) return null
    val r = zonaFacial(zonaId)?.rangos?.get(tipo) ?: return null
    val f = if (tipo == TOX) factorToxina(producto) else 1.0
    val unidad = if (tipo == TOX) "U" else "ml"
    val min = redondearDosis(r.first * f)
    val max = redondearDosis(r.second * f)
    return RangoTipico(min, max, unidad,
        "Habitual: ${numeroDosis(min)}–${numeroDosis(max)} $unidad${if (f != 1.0) " (Dysport ≈ ×2,5, aproximado)" else ""} · orientativo",
        f != 1.0)
}

data class AvisoDosis(val nivel: String, val texto: String)

/** ¿La dosis está en lo habitual? null = sin referencia o en otra unidad (no se compara). */
fun evaluarDosis(zona: String, tipo: String, producto: String?, dosis: Double, unidad: String): AvisoDosis? {
    val r = rangoTipico(zona, tipo, producto) ?: return null
    if (r.unidad != unidad || !(dosis > 0)) return null
    val conv = if (r.conversion) ", Dysport ≈ ×2,5" else ""
    val rango = "${numeroDosis(r.min)}–${numeroDosis(r.max)} ${r.unidad}$conv"
    if (dosis > r.max) return AvisoDosis("alto", "Por encima de lo habitual ($rango) · orientativo")
    if (dosis < r.min) return AvisoDosis("bajo", "Por debajo de lo habitual ($rango) · orientativo")
    return AvisoDosis("ok", r.texto)
}

fun evaluarDosis(a: Aplicacion): AvisoDosis? = evaluarDosis(a.zona, a.tipoProducto, a.producto, a.dosis, a.unidad)

/** Relleno en una zona de alto riesgo vascular: aviso rojo (no bloquea). */
fun avisoRiesgoVascular(zonaId: String, tipo: String): String? {
    if (tipo != AH && tipo != "bioestimulador") return null
    return if (zonaFacial(zonaId)?.riesgoVascular == true)
        "Zona de alto riesgo vascular con relleno: aspirar, inyectar lento y con poca presión, tener hialuronidasa a mano."
    else null
}

// ─── Modelo ──────────────────────────────────────────────────────────────────

/** Una aplicación (un elemento del jsonb `aplicaciones`), como la guarda la base. */
data class Aplicacion(
    val zona: String,
    val producto: String,
    val tipoProducto: String,
    val dosis: Double,
    val unidad: String,
    val puntos: Int? = null,
    val tecnica: String? = null,
    val profundidad: String? = null,
    val lote: String? = null,
    /** YYYY-MM-DD o YYYY-MM (el vial suele decir solo mes y año). */
    val vencimiento: String? = null,
    val notas: String? = null,
)

/** Lo que escribe el formulario de UNA aplicación (textos tal como se escriben). */
data class CrudoAplicacion(
    val zona: String,
    val producto: String,
    val tipoProducto: String? = null,
    val dosis: String,
    val unidad: String? = null,
    val puntos: String? = null,
    val tecnica: String? = null,
    val profundidad: String? = null,
    val lote: String? = null,
    val vencimiento: String? = null,
    val notas: String? = null,
)

fun crudoDe(a: Aplicacion) = CrudoAplicacion(
    a.zona, a.producto, a.tipoProducto, numeroDosis(a.dosis), a.unidad, a.puntos?.toString(), a.tecnica, a.profundidad,
    a.lote, a.vencimiento, a.notas,
)

data class TotalProducto(
    val producto: String, val tipoProducto: String, val unidad: String, val total: Double,
    val puntos: Int, val zonas: Int, val lotes: List<String>,
)

data class TotalesAplicacion(
    val porProducto: List<TotalProducto>,
    /** Suma por unidad (U, ml, vial, jeringa): nunca se suman unidades distintas. */
    val porUnidad: Map<String, Double>,
    val puntos: Int,
    val zonas: Int,
    val aplicaciones: Int,
)

data class AplicacionEstetica(
    val id: String,
    val pacienteId: String,
    val citaId: String? = null,
    val tratamientoId: String? = null,
    val terapeutaId: String? = null,
    val fecha: String,
    val vista: String = "frontal",
    val aplicaciones: List<Aplicacion> = emptyList(),
    val observaciones: String? = null,
    val createdAt: String? = null,
) {
    /** Recalculados desde las aplicaciones (como la HC de la web: no se confía en el jsonb). */
    val totales: TotalesAplicacion get() = calcularTotales(aplicaciones)
}

/** Lo que manda el editor a la API. */
data class BorradorAplicacion(
    val pacienteId: String,
    val aplicaciones: List<Aplicacion>,
    val citaId: String? = null,
    val tratamientoId: String? = null,
    val terapeutaId: String? = null,
    val fecha: String? = null,
    val observaciones: String? = null,
)

/** Gemelos de los CHECK de la base. */
object LimitesAplicacion {
    const val APLICACIONES = 60
    const val PRODUCTO = 80
    const val LOTE = 40
    const val NOTAS = 300
    const val OBSERVACIONES = 2000
    const val PUNTOS = 100
    val DOSIS: Map<String, Double> = mapOf("U" to 400.0, "ml" to 20.0, "vial" to 10.0, "jeringa" to 10.0)
}

private val RE_FECHA_AP = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val RE_VENCIMIENTO = Regex("""^\d{4}-(0[1-9]|1[0-2])(-(0[1-9]|[12]\d|3[01]))?$""")
private fun limpioAp(v: String?): String = v.orEmpty().replace(Regex("""\s+"""), " ").trim()

// ─── Normalización y validación ──────────────────────────────────────────────

data class NormalizadaAplicacion(val aplicacion: Aplicacion?, val errores: List<String>)

/** Una aplicación cruda → la forma de la base, más sus errores. `n` = número de la aplicación ("Aplicación 2: …"). */
fun normalizarAplicacion(r: CrudoAplicacion, n: Int = 1): NormalizadaAplicacion {
    val e = mutableListOf<String>()
    val pre = "Aplicación $n: "
    val zona = limpioAp(r.zona)
    if (zonaFacial(zona) == null) e += "${pre}zona no válida."
    val producto = limpioAp(r.producto)
    if (producto.isEmpty()) e += "${pre}falta el producto."
    else if (producto.length > LimitesAplicacion.PRODUCTO) e += "${pre}el nombre del producto es muy largo (máx. ${LimitesAplicacion.PRODUCTO})."
    val tipoCrudo = limpioAp(r.tipoProducto)
    val tipo: String? = if (tipoCrudo in IDS_TIPO) tipoCrudo else if (tipoCrudo.isNotEmpty()) null else inferirTipoProducto(producto) ?: "otro"
    if (tipo == null) e += "${pre}tipo de producto no válido."
    val unidad = limpioAp(r.unidad).ifEmpty { tipo?.let { unidadPorDefecto(it) }.orEmpty() }
    if (unidad !in UNIDADES_DOSIS) e += "${pre}unidad no válida (U, ml, vial o jeringa)."
    else if (tipo != null && unidad !in tipoProducto(tipo).unidades) e += "${pre}la ${tipoProducto(tipo).nombre.lowercase()} no se mide en $unidad."
    val dosis = leerNumeroDosis(r.dosis)
    if (!dosis.isFinite() || dosis <= 0) e += "${pre}la dosis tiene que ser mayor que 0."
    else if (unidad in UNIDADES_DOSIS && dosis > LimitesAplicacion.DOSIS.getValue(unidad)) {
        e += "${pre}la dosis es demasiado alta (máx. ${numeroDosis(LimitesAplicacion.DOSIS.getValue(unidad))} $unidad)."
    }
    var puntos: Int? = null
    if (!r.puntos.isNullOrBlank()) {
        val p = leerNumeroDosis(r.puntos)
        if (!p.isFinite() || p != floor(p) || p < 0 || p > LimitesAplicacion.PUNTOS) e += "${pre}los puntos tienen que ser un número entero de 0 a ${LimitesAplicacion.PUNTOS}."
        else puntos = p.toInt()
    }
    val tec = limpioAp(r.tecnica)
    if (tec.isNotEmpty() && TECNICAS_APLICACION.none { it.id == tec }) e += "${pre}técnica no válida (aguja o cánula)."
    val prof = limpioAp(r.profundidad)
    if (prof.isNotEmpty() && PROFUNDIDADES.none { it.id == prof }) e += "${pre}profundidad no válida."
    val lote = limpioAp(r.lote)
    if (lote.length > LimitesAplicacion.LOTE) e += "${pre}el lote es muy largo (máx. ${LimitesAplicacion.LOTE})."
    val venc = limpioAp(r.vencimiento)
    if (venc.isNotEmpty() && !RE_VENCIMIENTO.matches(venc)) e += "${pre}vencimiento inválido (AAAA-MM o AAAA-MM-DD)."
    val notas = limpioAp(r.notas)
    if (notas.length > LimitesAplicacion.NOTAS) e += "${pre}las notas son muy largas (máx. ${LimitesAplicacion.NOTAS})."
    if (e.isNotEmpty()) return NormalizadaAplicacion(null, e)
    return NormalizadaAplicacion(
        Aplicacion(
            zona = zona, producto = producto, tipoProducto = tipo!!, dosis = redondearDosis(dosis), unidad = unidad, puntos = puntos,
            tecnica = tec.ifEmpty { null }, profundidad = prof.ifEmpty { null },
            lote = lote.ifEmpty { null }, vencimiento = venc.ifEmpty { null }, notas = notas.ifEmpty { null },
        ),
        emptyList(),
    )
}

/** Lista cruda → aplicaciones normalizadas + errores (vacío = válida). */
fun normalizarAplicaciones(raw: List<CrudoAplicacion>?): Pair<List<Aplicacion>, List<String>> {
    if (raw.isNullOrEmpty()) return emptyList<Aplicacion>() to listOf("Marca al menos una zona con su producto y dosis.")
    if (raw.size > LimitesAplicacion.APLICACIONES) return emptyList<Aplicacion>() to listOf("Demasiadas aplicaciones (máx. ${LimitesAplicacion.APLICACIONES}).")
    val out = mutableListOf<Aplicacion>()
    val errores = mutableListOf<String>()
    raw.forEachIndexed { i, x ->
        val r = normalizarAplicacion(x, i + 1)
        r.aplicacion?.let { out += it }
        errores += r.errores
    }
    return (if (errores.isEmpty()) out else emptyList()) to errores
}

/** Errores para mostrar (vacío = válido). */
fun validarAplicacionEstetica(b: BorradorAplicacion): List<String> {
    val e = mutableListOf<String>()
    if (b.pacienteId.isBlank()) e += "Falta el paciente."
    if (!b.fecha.isNullOrEmpty() && !RE_FECHA_AP.matches(b.fecha)) e += "Fecha inválida."
    if (limpioAp(b.observaciones).length > LimitesAplicacion.OBSERVACIONES) e += "Las observaciones son muy largas (máx. ${LimitesAplicacion.OBSERVACIONES})."
    e += normalizarAplicaciones(b.aplicaciones.map(::crudoDe)).second
    return e
}

/** Último día del vencimiento ("2027-03" → "2027-03-31"). null si no hay o no es válido. */
fun finDeVencimiento(v: String?): String? {
    if (v == null || !RE_VENCIMIENTO.matches(v)) return null
    if (v.length == 10) return v
    val (a, m) = v.split("-").map { it.toInt() }
    val ultimo = when (m) {
        2 -> if ((a % 4 == 0 && a % 100 != 0) || a % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return "$v-${ultimo.toString().padStart(2, '0')}"
}

/** ¿El producto ya estaba vencido el día de la aplicación? (aviso fuerte, no bloquea). */
fun vencidoEn(vencimiento: String?, fecha: String): Boolean {
    val fin = finDeVencimiento(vencimiento) ?: return false
    return RE_FECHA_AP.matches(fecha) && fin < fecha
}

// ─── Totales ─────────────────────────────────────────────────────────────────

/** Totales de la sesión: por producto (y su unidad), por unidad, puntos y zonas. */
fun calcularTotales(aplicaciones: List<Aplicacion>): TotalesAplicacion {
    data class Acum(val producto: String, val tipo: String, val unidad: String, var total: Double = 0.0, var puntos: Int = 0,
                    val zonas: LinkedHashSet<String> = LinkedHashSet(), val lotes: LinkedHashSet<String> = LinkedHashSet())
    val porProducto = LinkedHashMap<String, Acum>()
    val porUnidad = LinkedHashMap<String, Double>()
    val zonas = LinkedHashSet<String>()
    var puntos = 0
    for (a in aplicaciones) {
        val clave = "${plegarProducto(a.producto)}|${a.unidad}"
        val t = porProducto.getOrPut(clave) { Acum(a.producto, a.tipoProducto, a.unidad) }
        t.total = redondearDosis(t.total + a.dosis)
        t.puntos += a.puntos ?: 0
        t.zonas += a.zona
        a.lote?.let { t.lotes += it }
        porUnidad[a.unidad] = redondearDosis((porUnidad[a.unidad] ?: 0.0) + a.dosis)
        puntos += a.puntos ?: 0
        zonas += a.zona
    }
    val orden = { t: Acum -> TIPOS_PRODUCTO.indexOfFirst { it.id == t.tipo } }
    return TotalesAplicacion(
        porProducto = porProducto.values
            .sortedWith(compareBy<Acum>({ orden(it) }, { plegarProducto(it.producto) }, { it.producto }))
            .map { TotalProducto(it.producto, it.tipo, it.unidad, it.total, it.puntos, it.zonas.size, it.lotes.toList()) },
        porUnidad = porUnidad,
        puntos = puntos,
        zonas = zonas.size,
        aplicaciones = aplicaciones.size,
    )
}

/** "20 U · 1,5 ml" (las unidades nunca se suman entre sí). */
fun textoTotales(porUnidad: Map<String, Double>?): String =
    UNIDADES_DOSIS.filter { (porUnidad?.get(it) ?: 0.0) > 0 }.joinToString(" · ") { textoDosis(porUnidad!!.getValue(it), it) }

fun textoTotales(t: TotalesAplicacion?): String = textoTotales(t?.porUnidad)

/** Lo aplicado en una zona. */
data class ResumenZonaAplicada(val texto: String, val tipos: List<String>, val aplicaciones: Int)

/** Lo aplicado en cada zona: "20 U", "0,5 ml · 4 U". */
fun totalesPorZona(aplicaciones: List<Aplicacion>): Map<String, ResumenZonaAplicada> {
    val pu = LinkedHashMap<String, LinkedHashMap<String, Double>>()
    val tipos = LinkedHashMap<String, LinkedHashSet<String>>()
    val n = LinkedHashMap<String, Int>()
    for (a in aplicaciones) {
        val m = pu.getOrPut(a.zona) { LinkedHashMap() }
        m[a.unidad] = redondearDosis((m[a.unidad] ?: 0.0) + a.dosis)
        tipos.getOrPut(a.zona) { LinkedHashSet() } += a.tipoProducto
        n[a.zona] = (n[a.zona] ?: 0) + 1
    }
    return pu.mapValues { (k, v) -> ResumenZonaAplicada(textoTotales(v), tipos[k].orEmpty().toList(), n[k] ?: 0) }
}

/** Qué vistas usó el mapa: "frontal" | "lateral" | "ambas". */
fun vistaDeAplicaciones(zonas: List<String>): String {
    val vistas = zonas.map { zonaFacial(it)?.vista ?: "frontal" }.toSet()
    return if ("frontal" in vistas && "lateral" in vistas) "ambas" else if ("lateral" in vistas) "lateral" else "frontal"
}

/** Más reciente primero (fecha y, a igual fecha, el último creado). */
fun ordenarAplicaciones(items: List<AplicacionEstetica>): List<AplicacionEstetica> =
    items.sortedWith(compareByDescending<AplicacionEstetica> { it.fecha }.thenByDescending { it.createdAt.orEmpty() })

// ─── Cobro por unidades: SOLO para pre-llenar el formulario del tratamiento ─

data class UnidadesCobrables(
    val cantidad: Int,
    /** "50 U de toxina" / "≈ 2 jeringas de 1 ml". */
    val texto: String,
    /** Hubo que redondear, convertir o sumar U de marcas no equivalentes (el usuario lo confirma igual). */
    val aproximado: Boolean,
    /** Total por producto ("Botox 20 U"): con varias marcas se muestra el detalle. */
    val detalle: List<String>,
    /** Se sumaron U de toxinas que NO son equivalentes (Dysport con Botox/Xeomin). */
    val mezclaMarcas: Boolean,
)

/**
 * Cuántas "unidades" del servicio salen del mapa, según el `unidad_label` del
 * procedimiento. null = la unidad del servicio no se deduce del mapa
 * (folículos, sesiones…): no se ofrece nada. NUNCA cambia dinero: el resultado
 * solo pre-llena la cantidad del formulario de siempre (entero en la base).
 */
fun unidadesCobrables(totales: TotalesAplicacion?, unidadLabel: String?): UnidadesCobrables? {
    if (totales == null) return null
    val l = plegarProducto(unidadLabel ?: "unidades").ifEmpty { "unidades" }
    val pu = totales.porUnidad
    fun entero(n: Double, etiqueta: String): UnidadesCobrables? {
        if (!(n > 0)) return null
        val c = ceil(n - 1e-9)
        return UnidadesCobrables(c.toInt(), if (c == n) "${numeroDosis(c)} $etiqueta" else "${numeroDosis(n)} → ${numeroDosis(c)} $etiqueta",
            c != n, emptyList(), false)
    }
    if (Regex("""^(u|ui|unidad|unidades|unid\.?|units?)$""").matches(l)) {
        val prods = totales.porProducto.filter { it.unidad == "U" }
        val detalle = prods.map { "${it.producto} ${textoDosis(it.total, "U")}" }
        val mezcla = prods.map { factorToxina(it.producto) }.toSet().size > 1
        val r = entero(pu["U"] ?: 0.0, "U") ?: return null
        return if (mezcla) r.copy(texto = "≈ ${numeroDosis(r.cantidad.toDouble())} U (${detalle.joinToString(" + ")})", aproximado = true, detalle = detalle, mezclaMarcas = true)
        else r.copy(detalle = if (prods.size > 1) detalle else emptyList(), mezclaMarcas = false)
    }
    if (Regex("""^(ml|mililitro|mililitros|cc)$""").matches(l)) return entero(pu["ml"] ?: 0.0, "ml")
    if (Regex("""^(vial|viales|ampolla|ampollas|frasco|frascos)$""").matches(l)) {
        val v = pu["vial"] ?: 0.0
        return entero(v, if (v == 1.0) "vial" else "viales")
    }
    if (Regex("""^(jeringa|jeringas|syringe)$""").matches(l)) {
        val j = pu["jeringa"] ?: 0.0
        if (j > 0) return entero(j, "jeringas")
        val mlRelleno = totales.porProducto.filter { it.unidad == "ml" && it.tipoProducto == AH }.sumOf { it.total }
        if (!(mlRelleno > 0)) return null
        val c = ceil(mlRelleno - 1e-9).toInt()
        return UnidadesCobrables(c, "≈ $c ${if (c == 1) "jeringa" else "jeringas"} de 1 ml (${numeroDosis(mlRelleno)} ml)", true, emptyList(), false)
    }
    return null
}

// ─── Historial ───────────────────────────────────────────────────────────────

/** Días entre dos fechas YYYY-MM-DD (b − a). null si alguna no es una fecha válida. */
fun diasEntreIso(a: String, b: String): Int? {
    fun d(s: String): Long? = if (!RE_FECHA_AP.matches(s.take(10))) null
    else runCatching { kotlinx.datetime.LocalDate.parse(s.take(10)).toEpochDays().toLong() }.getOrNull()
    val da = d(a) ?: return null
    val db = d(b) ?: return null
    return (db - da).toInt()
}

data class UltimaToxina(val fecha: String, val semanas: Int, val nivel: String?, val texto: String)

/** La última sesión con toxina (hasta hoy) y cuánto pasó. null = nunca. */
fun ultimaToxina(items: List<AplicacionEstetica>, hoy: String): UltimaToxina? {
    // Las filas sin una fecha válida no cuentan (y nunca rompen la pestaña).
    val fecha = items.filter { RE_FECHA_AP.matches(it.fecha) && it.fecha <= hoy && it.aplicaciones.any { a -> a.tipoProducto == TOX } }
        .map { it.fecha }.maxOrNull() ?: return null
    val dias = diasEntreIso(fecha, hoy) ?: return null
    val semanas = floor(dias / 7.0).toInt()
    val hace = when {
        dias == 0 -> "hoy"
        dias < 14 -> "hace $dias ${if (dias == 1) "día" else "días"}"
        semanas < 9 -> "hace $semanas semanas"
        else -> "hace ${floor(dias / 30.44).toInt()} meses"
    }
    val nivel = if (dias > 0 && semanas < 12) "pronto" else if (semanas in 16..26) "habitual" else null
    val sufijo = when (nivel) {
        "pronto" -> " · menos de 3 meses: la ficha técnica no recomienda repetir antes (orientativo)"
        "habitual" -> " · momento habitual de repetir"
        else -> ""
    }
    return UltimaToxina(fecha, semanas, nivel, "Última toxina: $hace$sufijo")
}

/** "Repetir la sesión anterior": mismas zonas, productos, dosis, puntos, técnica y profundidad; sin lote, vencimiento ni notas. */
fun copiarParaRepetir(aplicaciones: List<Aplicacion>): List<Aplicacion> =
    aplicaciones.map { it.copy(lote = null, vencimiento = null, notas = null) }

/** El "servicio" con el que 📝 Indicaciones sugiere su plantilla base (el tipo con más aplicaciones). */
fun servicioParaIndicaciones(aplicaciones: List<Aplicacion>): String? {
    val nombre = mapOf(TOX to "Toxina botulínica", AH to "Ácido hialurónico", "bioestimulador" to "Bioestimulador", "regenerativo" to "Exosomas")
    val cuenta = LinkedHashMap<String, Int>()
    for (a in aplicaciones) cuenta[a.tipoProducto] = (cuenta[a.tipoProducto] ?: 0) + 1
    val mejor = cuenta.entries.filter { nombre[it.key] != null }
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { IDS_TIPO.indexOf(it.key) })
        .firstOrNull() ?: return null
    return nombre[mejor.key]
}

// ─── Productos recientes de la clínica (GET ?recientes=1) ────────────────────

data class ProductoReciente(
    val producto: String, val tipoProducto: String, val unidad: String, val usos: Int,
    val ultimoLote: String?, val ultimoVencimiento: String?,
)

data class ChipProducto(val producto: String, val tipo: String, val propio: Boolean)

/** Chips de producto para ese tipo: los de la clínica primero, luego los comunes, sin repetir. */
fun chipsProducto(tipo: String?, recientes: List<ProductoReciente>?): List<ChipProducto> {
    val out = mutableListOf<ChipProducto>()
    val vistos = HashSet<String>()
    for (p in recientes.orEmpty()) {
        if (tipo != null && p.tipoProducto != tipo) continue
        if (!vistos.add(plegarProducto(p.producto))) continue
        out += ChipProducto(p.producto, p.tipoProducto, true)
    }
    for ((producto, t) in PRODUCTOS_COMUNES) {
        if (tipo != null && t != tipo) continue
        if (!vistos.add(plegarProducto(producto))) continue
        out += ChipProducto(producto, t, false)
    }
    return out
}

/** Dosis típicas para tocar (chips) según el tipo, la zona y el producto. */
fun dosisSugeridas(zonaId: String, tipo: String, producto: String? = null): List<Double> {
    val r = rangoTipico(zonaId, tipo, producto)
    if (r != null) {
        val redondos = if (r.unidad == "U") listOf(2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 60.0, 75.0, 100.0, 125.0)
        else listOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.8, 1.0, 1.5, 2.0)
        val lista = (listOf(r.min) + redondos.filter { it > r.min && it < r.max } + r.max).distinct().sorted()
        if (lista.size <= 6) return lista
        val medio = lista.subList(1, lista.size - 1)
        val elegidos = (0..3).map { i -> medio[jsRound(i * (medio.size - 1) / 3.0)] }
        return (listOf(lista.first()) + elegidos + lista.last()).distinct()
    }
    return when (tipo) {
        TOX -> listOf(2.0, 4.0, 6.0, 10.0, 20.0)
        AH -> listOf(0.1, 0.2, 0.5, 1.0)
        else -> listOf(0.5, 1.0, 2.0)
    }
}

private fun jsRound(x: Double): Int = floor(x + 0.5).toInt()

// ─── JSON ────────────────────────────────────────────────────────────────────

private fun JsonElement?.primAp(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
private fun JsonObject.txtAp(k: String): String? = this[k].primAp()?.content?.takeIf { it.isNotBlank() && it != "null" }
private fun JsonObject.numAp(k: String): Double? = this[k].primAp()?.content?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() }

/** Un elemento del jsonb → [Aplicacion]. Tolerante (snake o camel); sin zona o producto se descarta. */
fun aplicacionDeJson(o: JsonObject): Aplicacion? {
    val zona = o.txtAp("zona") ?: return null
    val producto = o.txtAp("producto") ?: return null
    val tipo = (o.txtAp("tipo_producto") ?: o.txtAp("tipoProducto"))?.takeIf { it in IDS_TIPO } ?: inferirTipoProducto(producto) ?: "otro"
    return Aplicacion(
        zona = zona, producto = producto, tipoProducto = tipo,
        dosis = o.numAp("dosis") ?: 0.0,
        unidad = o.txtAp("unidad")?.takeIf { it in UNIDADES_DOSIS } ?: unidadPorDefecto(tipo),
        puntos = o.numAp("puntos")?.toInt(),
        tecnica = o.txtAp("tecnica"), profundidad = o.txtAp("profundidad"),
        lote = o.txtAp("lote"), vencimiento = o.txtAp("vencimiento"), notas = o.txtAp("notas"),
    )
}

/** Fila del API → modelo. Sin id se descarta. */
fun aplicacionEsteticaDeJson(o: JsonObject): AplicacionEstetica? {
    val id = o.txtAp("id") ?: return null
    return AplicacionEstetica(
        id = id,
        pacienteId = o.txtAp("paciente_id").orEmpty(),
        citaId = o.txtAp("cita_id"),
        tratamientoId = o.txtAp("tratamiento_id"),
        terapeutaId = o.txtAp("terapeuta_id"),
        fecha = o.txtAp("fecha").orEmpty().take(10),
        vista = o.txtAp("vista") ?: "frontal",
        aplicaciones = (o["aplicaciones"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::aplicacionDeJson) },
        observaciones = o.txtAp("observaciones"),
        createdAt = o.txtAp("created_at"),
    )
}

/** {productos: [...]} → lista (tolerante). */
fun productosRecientesDeJson(o: JsonObject?): List<ProductoReciente> = (o?.get("productos") as? JsonArray).orEmpty().mapNotNull { x ->
    val p = x as? JsonObject ?: return@mapNotNull null
    val producto = p.txtAp("producto") ?: return@mapNotNull null
    val tipo = p.txtAp("tipo_producto")?.takeIf { it in IDS_TIPO } ?: return@mapNotNull null
    ProductoReciente(
        producto, tipo, p.txtAp("unidad")?.takeIf { it in UNIDADES_DOSIS } ?: unidadPorDefecto(tipo),
        p.numAp("usos")?.toInt() ?: 1, p.txtAp("ultimo_lote"), p.txtAp("ultimo_vencimiento"),
    )
}

fun aplicacionAJson(a: Aplicacion): JsonObject = buildJsonObject {
    put("zona", a.zona); put("producto", a.producto); put("tipo_producto", a.tipoProducto)
    put("dosis", a.dosis); put("unidad", a.unidad)
    if (a.puntos == null) put("puntos", JsonNull) else put("puntos", a.puntos)
    fun opc(k: String, v: String?) { if (v.isNullOrBlank()) put(k, JsonNull) else put(k, v) }
    opc("tecnica", a.tecnica); opc("profundidad", a.profundidad); opc("lote", a.lote)
    opc("vencimiento", a.vencimiento); opc("notas", a.notas)
}

/**
 * Cuerpo de POST (sin [id]) o PATCH (con [id]). `terapeutaId` solo si se eligió
 * (vacío = el servidor decide: el de la cita o el profesional del usuario).
 */
fun cuerpoAplicacion(b: BorradorAplicacion, id: String? = null): JsonObject = buildJsonObject {
    if (id == null) put("pacienteId", b.pacienteId) else put("id", id)
    b.fecha?.takeIf { it.isNotBlank() }?.let { put("fecha", it) }
    put("aplicaciones", buildJsonArray { b.aplicaciones.forEach { add(aplicacionAJson(it)) } })
    val obs = b.observaciones?.trim()?.takeIf { it.isNotEmpty() }
    if (obs == null) put("observaciones", JsonNull) else put("observaciones", obs)
    if (b.citaId.isNullOrBlank()) put("citaId", JsonNull) else put("citaId", b.citaId)
    if (b.tratamientoId.isNullOrBlank()) put("tratamientoId", JsonNull) else put("tratamientoId", b.tratamientoId)
    b.terapeutaId?.takeIf { it.isNotBlank() }?.let { put("terapeutaId", it) }
}

/** Mensaje cuando el servidor todavía no tiene el endpoint (backend viejo → 404 sin JSON). */
const val APLICACIONES_NO_DISPONIBLE =
    "El mapa de aplicación aún no está disponible en el servidor. Intenta más tarde."

// ─── Lo que la pestaña necesita de la ficha ──────────────────────────────────

/** Una cita del paciente que puede llevar mapa (las de estética). */
data class CitaParaMapa(
    val id: String,
    val fecha: String,
    val hora: String? = null,
    val estado: String,
    val servicio: String? = null,
    val tratamientoId: String? = null,
    /** Quién atendió la cita: por defecto, quien aplicó. */
    val terapeutaId: String? = null,
)

/** Un tratamiento de estética del paciente (vincular el mapa y el cobro por unidades). */
data class TratamientoParaMapa(
    val id: String,
    val nombre: String,
    val estado: String,
    val modalidad: String?,
    val unidadLabel: String?,
    val cantidadUnidades: Int?,
)

/**
 * ¿Ofrecer "🔢 Usar … como unidades cobradas"? Solo si el tratamiento cobra por
 * unidades y la cantidad del mapa es distinta de la que tiene. Solo PRE-LLENA
 * el formulario de editar tratamiento: el usuario revisa y guarda.
 */
fun ofertaUnidadesCobradas(aplicaciones: List<Aplicacion>, t: TratamientoParaMapa?): UnidadesCobrables? {
    if (t == null || t.modalidad != "Unidades") return null
    val uc = unidadesCobrables(calcularTotales(aplicaciones), t.unidadLabel) ?: return null
    return uc.takeIf { it.cantidad != t.cantidadUnidades }
}
