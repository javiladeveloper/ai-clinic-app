package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// ─────────────────────────────────────────────────────────────────────────────
// EVALUACIÓN PSICOLÓGICA — FASE 3: instrumentos LIBRES con autocálculo
// (contrato §14.1; web: lib/instrumentos-psico.ts).
//
// La app NO puntúa: las definiciones llegan por GET /instrumentos y sirven para
// dibujar "Responder ítems" (ítems, opciones, inversos, consigna, licencia). Lo
// que se guarda lo calcula SIEMPRE el servidor (`test { accion: 'responder' }`):
// total, subescalas, categoría y alertas se muestran tal como vuelven.
// Lo único "local" es armar el arreglo de valores (uno por ítem, en orden) y,
// como vista previa de seguridad, la alerta de riesgo con el mismo texto que la
// definición trae (PHQ-9 ítem 9, SRQ ítem 17).
// ─────────────────────────────────────────────────────────────────────────────

data class OpcionItemPsico(val valor: Int, val texto: String)

data class ItemInstrumentoPsico(
    val n: Int,
    val texto: String,
    /** null = las opciones del instrumento. */
    val opciones: List<OpcionItemPsico>? = null,
    val inverso: Boolean = false,
    /** false = se pregunta pero no suma (dificultad funcional del PHQ-9 / GAD-7). */
    val puntua: Boolean = true,
    val opcional: Boolean = false,
    val umbralPositivo: Int? = null,
)

/** Banda de corte (min..max inclusive). `nivel` 0 sin hallazgo … 4 grave. */
data class BandaCortePsico(val min: Double, val max: Double, val categoria: String, val nivel: Int, val positivo: Boolean = false)

data class EscalaInstrumentoPsico(
    val clave: String,
    val nombre: String,
    val items: List<Int> = emptyList(),
    val min: Double = 0.0,
    val max: Double = 0.0,
    val bandas: List<BandaCortePsico> = emptyList(),
    /** Cortes por sexo ("F" / "M"), solo AUDIT-C. */
    val bandasSexo: Map<String, List<BandaCortePsico>>? = null,
    /** Subescala que, si da positivo, se suma a la categoría global (SRQ, PSC-17). */
    val enGlobal: Boolean = false,
)

data class AlertaItemPsico(val item: Int, val desde: Int, val mensaje: String)

data class InstrumentoPsico(
    val id: String,
    /** `tests_catalogo.nombre_corto` del catálogo GLOBAL al que se engancha. */
    val catalogo: String,
    val corto: String,
    val nombre: String = "",
    val variante: String? = null,
    val consigna: String = "",
    val opciones: List<OpcionItemPsico> = emptyList(),
    val items: List<ItemInstrumentoPsico> = emptyList(),
    /** 'suma' | 'positivos' (ASRS). */
    val modo: String = "suma",
    val total: EscalaInstrumentoPsico? = null,
    val subescalas: List<EscalaInstrumentoPsico> = emptyList(),
    val mayorEsPeor: Boolean = true,
    val alertas: List<AlertaItemPsico> = emptyList(),
    /** ASRS: la licencia no permite reproducir el texto de los ítems. */
    val sinTextoItems: Boolean = false,
    val fuente: String = "",
    val licencia: String = "",
    val poblacion: String = "",
) {
    fun opcionesDe(it: ItemInstrumentoPsico): List<OpcionItemPsico> = it.opciones ?: opciones
    /** Nombre en el selector de versión (AUDIT / AUDIT-C, SRQ-18 / SRQ-20…). */
    val nombreVersion: String get() = variante?.ifBlank { null } ?: corto
}

/** Una escala del resultado que calculó el servidor. */
data class ResultadoEscalaPsico(
    val clave: String = "",
    val nombre: String = "",
    /** null = falta algún ítem de la escala. */
    val puntaje: Double? = null,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val categoria: String? = null,
    val nivel: Int? = null,
    val positivo: Boolean = false,
)

data class ResultadoInstrumentoPsico(
    val instrumento: String,
    val completo: Boolean = false,
    val faltantes: List<Int> = emptyList(),
    val total: ResultadoEscalaPsico? = null,
    val subescalas: List<ResultadoEscalaPsico> = emptyList(),
    val categoria: String? = null,
    /** Alertas de riesgo: salen aunque falten ítems. Siempre visibles (banda roja). */
    val alertas: List<String> = emptyList(),
)

/** `tests_aplicados.respuestas` (null en el test = puntuado a mano). */
data class RespuestasTestPsico(
    val instrumento: String,
    val valores: List<Int?> = emptyList(),
    val resultado: ResultadoInstrumentoPsico? = null,
    val sexo: String? = null,
    val calculadoAt: String? = null,
    /** La psicóloga corrigió a mano los puntajes calculados ("corregido a mano"). */
    val editado: Boolean = false,
)

// ── Lectura tolerante ────────────────────────────────────────────────────────

private fun JsonElement?.objI(): JsonObject? = this as? JsonObject

private fun JsonObject?.txtI(k: String): String? =
    (this?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it != "null" }

private fun JsonObject?.numI(k: String): Double? = txtI(k)?.trim()?.replace(',', '.')?.toDoubleOrNull()

private fun JsonObject?.siI(k: String, defecto: Boolean): Boolean = when (txtI(k)?.lowercase()) {
    "true" -> true
    "false" -> false
    else -> defecto
}

private fun JsonObject?.listaI(k: String): List<JsonElement> = (this?.get(k) as? JsonArray).orEmpty()

/** Un número de JSON a entero (2, 2.0 o "2"); lo demás → null. */
internal fun enteroDeJson(e: JsonElement?): Int? {
    val p = e as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    val d = p.contentOrNull?.trim()?.toDoubleOrNull() ?: return null
    return if (d == kotlin.math.floor(d) && !d.isInfinite()) d.toInt() else null
}

private fun leerOpciones(e: JsonElement?): List<OpcionItemPsico>? = (e as? JsonArray)?.mapNotNull { x ->
    val o = x as? JsonObject ?: return@mapNotNull null
    val v = enteroDeJson(o["valor"]) ?: return@mapNotNull null
    OpcionItemPsico(v, o.txtI("texto").orEmpty())
}

private fun leerBandas(e: JsonElement?): List<BandaCortePsico> = (e as? JsonArray).orEmpty().mapNotNull { x ->
    val o = x as? JsonObject ?: return@mapNotNull null
    val min = o.numI("min") ?: return@mapNotNull null
    val max = o.numI("max") ?: return@mapNotNull null
    BandaCortePsico(min, max, o.txtI("categoria").orEmpty(), (o.numI("nivel") ?: 0.0).toInt().coerceIn(0, 4), o.siI("positivo", false))
}

private fun leerEscalaInstrumento(o: JsonObject?): EscalaInstrumentoPsico? {
    val clave = o.txtI("clave") ?: return null
    val sexo = o?.get("bandasSexo").objI()
    return EscalaInstrumentoPsico(
        clave = clave,
        nombre = o.txtI("nombre") ?: clave,
        items = o.listaI("items").mapNotNull { enteroDeJson(it) },
        min = o.numI("min") ?: 0.0,
        max = o.numI("max") ?: 0.0,
        bandas = leerBandas(o?.get("bandas")),
        bandasSexo = sexo?.let { s -> listOf("F", "M").associateWith { leerBandas(s[it]) }.filterValues { it.isNotEmpty() }.ifEmpty { null } },
        enGlobal = o.siI("enGlobal", false),
    )
}

/** Una definición de GET /instrumentos. Sin id, catálogo o ítems se descarta. */
internal fun leerInstrumento(o: JsonObject?): InstrumentoPsico? {
    val id = o.txtI("id")?.ifBlank { null } ?: return null
    val catalogo = o.txtI("catalogo")?.ifBlank { null } ?: return null
    val items = o.listaI("items").mapNotNull { x ->
        val it = x as? JsonObject ?: return@mapNotNull null
        val n = enteroDeJson(it["n"]) ?: return@mapNotNull null
        ItemInstrumentoPsico(
            n = n,
            texto = it.txtI("texto").orEmpty(),
            opciones = leerOpciones(it["opciones"])?.ifEmpty { null },
            inverso = it.siI("inverso", false),
            puntua = it.siI("puntua", true),
            opcional = it.siI("opcional", false),
            umbralPositivo = enteroDeJson(it["umbralPositivo"]),
        )
    }
    if (items.isEmpty()) return null
    return InstrumentoPsico(
        id = id,
        catalogo = catalogo,
        corto = o.txtI("corto")?.ifBlank { null } ?: catalogo,
        nombre = o.txtI("nombre").orEmpty(),
        variante = o.txtI("variante")?.ifBlank { null },
        consigna = o.txtI("consigna").orEmpty(),
        opciones = leerOpciones(o?.get("opciones")).orEmpty(),
        items = items,
        modo = if (o.txtI("modo") == "positivos") "positivos" else "suma",
        total = leerEscalaInstrumento(o?.get("total").objI()),
        subescalas = o.listaI("subescalas").mapNotNull { leerEscalaInstrumento(it.objI()) },
        mayorEsPeor = o.siI("mayorEsPeor", true),
        alertas = o.listaI("alertas").mapNotNull { x ->
            val a = x as? JsonObject ?: return@mapNotNull null
            val item = enteroDeJson(a["item"]) ?: return@mapNotNull null
            AlertaItemPsico(item, enteroDeJson(a["desde"]) ?: 1, a.txtI("mensaje").orEmpty())
        },
        sinTextoItems = o.siI("sinTextoItems", false),
        fuente = o.txtI("fuente").orEmpty(),
        licencia = o.txtI("licencia").orEmpty(),
        poblacion = o.txtI("poblacion").orEmpty(),
    )
}

/** `{ ok, instrumentos: [...] }` → definiciones válidas, en el orden del servidor. */
internal fun parsearInstrumentosPsico(o: JsonObject?): List<InstrumentoPsico> =
    o.listaI("instrumentos").mapNotNull { leerInstrumento(it.objI()) }

private fun leerResultadoEscala(o: JsonObject?): ResultadoEscalaPsico? {
    if (o == null) return null
    return ResultadoEscalaPsico(
        clave = o.txtI("clave").orEmpty(),
        nombre = o.txtI("nombre").orEmpty(),
        puntaje = o.numI("puntaje"),
        min = o.numI("min") ?: 0.0,
        max = o.numI("max") ?: 0.0,
        categoria = o.txtI("categoria")?.ifBlank { null },
        nivel = o.numI("nivel")?.toInt()?.coerceIn(0, 4),
        positivo = o.siI("positivo", false),
    )
}

/** `resultado` (del test guardado o de la respuesta de `responder`). */
internal fun leerResultadoInstrumento(o: JsonObject?): ResultadoInstrumentoPsico? {
    val ins = o.txtI("instrumento") ?: return null
    return ResultadoInstrumentoPsico(
        instrumento = ins,
        completo = o.siI("completo", false),
        faltantes = o.listaI("faltantes").mapNotNull { enteroDeJson(it) },
        total = leerResultadoEscala(o?.get("total").objI()),
        subescalas = o.listaI("subescalas").mapNotNull { leerResultadoEscala(it.objI()) },
        categoria = o.txtI("categoria")?.ifBlank { null },
        alertas = o.listaI("alertas").mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.contentOrNull?.ifBlank { null } },
    )
}

/** `tests_aplicados.respuestas`: null si no hay o no tiene forma válida (como leerRespuestasGuardadas). */
internal fun leerRespuestasTest(o: JsonObject?): RespuestasTestPsico? {
    val ins = o.txtI("instrumento")?.ifBlank { null } ?: return null
    val valores = o?.get("valores") as? JsonArray ?: return null
    return RespuestasTestPsico(
        instrumento = ins,
        valores = valores.map { enteroDeJson(it) },
        resultado = leerResultadoInstrumento(o["resultado"].objI()),
        sexo = o.txtI("sexo")?.takeIf { it == "F" || it == "M" },
        calculadoAt = o.txtI("calculado_at"),
        editado = o.siI("editado", false),
    )
}

/** Respuesta de `responder`: `{ ok, test, resultado }`. */
internal fun resultadoDeResponder(o: JsonObject?): ResultadoInstrumentoPsico? =
    leerResultadoInstrumento(o?.get("resultado").objI())

// ── Reglas de presentación (puras) ───────────────────────────────────────────

/**
 * Los instrumentos que puntúan un test (instrumentosDeTest de la web): solo del
 * catálogo GLOBAL (`test.clinica_id` null) y con su `nombre_corto` igual al
 * `catalogo` de la definición. Un test propio de la clínica con el mismo nombre
 * NO se puntúa solo (puede ser otra versión). Vacío = sin autocálculo.
 */
fun instrumentosDeTest(test: TestRefPsico?, instrumentos: List<InstrumentoPsico>?): List<InstrumentoPsico> {
    if (test == null || instrumentos.isNullOrEmpty()) return emptyList()
    if (test.clinicaId != null) return emptyList()
    val k = test.nombreCorto.trim().lowercase()
    if (k.isEmpty()) return emptyList()
    return instrumentos.filter { it.catalogo.trim().lowercase() == k }
}

/** El instrumento con que se abre "Responder ítems": el ya respondido o el primero. */
fun instrumentoInicial(disponibles: List<InstrumentoPsico>, previo: RespuestasTestPsico?): InstrumentoPsico? =
    disponibles.firstOrNull { it.id == previo?.instrumento } ?: disponibles.firstOrNull()

/**
 * Los valores con que arranca el formulario (sanearRespuestas de la web): las
 * respuestas guardadas si son de ESTE instrumento, uno por ítem y solo si existen
 * en sus opciones; si no, todos vacíos.
 */
fun valoresIniciales(ins: InstrumentoPsico, previo: RespuestasTestPsico?): List<Int?> =
    ins.items.mapIndexed { i, it ->
        val v = if (previo?.instrumento == ins.id) previo.valores.getOrNull(i) else null
        v?.takeIf { x -> ins.opcionesDe(it).any { o -> o.valor == x } }
    }

/** Marca una opción del ítem [indice]; tocar la ya marcada la quita (como la web). */
fun alternarValor(valores: List<Int?>, indice: Int, valor: Int): List<Int?> =
    valores.mapIndexed { j, x -> if (j == indice) (if (x == valor) null else valor) else x }

/** Números de los ítems obligatorios que faltan (los opcionales y los que no suman no cuentan). */
fun itemsFaltantes(ins: InstrumentoPsico, valores: List<Int?>): List<Int> =
    ins.items.filterIndexed { i, it -> !it.opcional && it.puntua && valores.getOrNull(i) == null }.map { it.n }

/**
 * Vista previa de las alertas de riesgo mientras se responde (mismo texto que
 * la definición; el servidor las vuelve a sacar al guardar). No es un puntaje.
 */
fun alertasVistaPrevia(ins: InstrumentoPsico, valores: List<Int?>): List<String> =
    ins.alertas.filter { a ->
        val idx = ins.items.indexOfFirst { it.n == a.item }
        val v = if (idx >= 0) valores.getOrNull(idx) else null
        v != null && v >= a.desde
    }.map { it.mensaje }.filter { it.isNotBlank() }

/** Texto del ítem: en ASRS (sin texto por licencia) solo "Ítem N". */
fun textoItemPsico(ins: InstrumentoPsico, it: ItemInstrumentoPsico): String =
    if (ins.sinTextoItems) "Ítem ${it.n}" else "${it.n}. ${it.texto}"

/** ¿Se muestra el valor junto a la opción? Solo en suma, ítem directo que suma (como la web). */
fun muestraValorOpcion(ins: InstrumentoPsico, it: ItemInstrumentoPsico): Boolean =
    ins.modo == "suma" && !it.inverso && it.puntua

/** Cuerpo de `test { accion: 'responder' }`: valores sin invertir (el servidor invierte), uno por ítem. */
internal fun jsonResponder(testAplicadoId: String, instrumento: String, valores: List<Int?>): JsonObject = buildJsonObject {
    put("accion", "responder")
    put("testAplicadoId", testAplicadoId)
    put("instrumento", instrumento)
    putJsonArray("valores") { valores.forEach { add(if (it == null) JsonNull else JsonPrimitive(it)) } }
}

/** "12" / "7.5" (sin ".0"). */
fun numeroPsico(d: Double): String {
    if (d == kotlin.math.floor(d) && !d.isInfinite()) return d.toLong().toString()
    val r = kotlin.math.round(d * 100) / 100
    return r.toString()
}

/** "12 / 27" o, si el rango no empieza en 0, "31 (10–40)" (textoPuntaje de la web). */
fun textoPuntajeEscala(e: ResultadoEscalaPsico?): String {
    val p = e?.puntaje ?: return ""
    return if (e.min == 0.0) "${numeroPsico(p)} / ${numeroPsico(e.max)}"
    else "${numeroPsico(p)} (${numeroPsico(e.min)}–${numeroPsico(e.max)})"
}

/** El aviso tras guardar (como el toast de la web). */
fun textoTrasResponder(corto: String, r: ResultadoInstrumentoPsico?): String =
    if (r?.completo == true) {
        val total = textoPuntajeEscala(r.total)
        "$corto: $total${r.categoria?.let { " · $it" } ?: ""}. Puntajes llenados (puedes editarlos)."
    } else "Respuestas guardadas. Faltan ítems para calcular."

/** Alertas del test tal como las guardó el servidor (badge y banda roja). */
val TestAplicadoPsico.alertas: List<String> get() = respuestas?.resultado?.alertas.orEmpty()
