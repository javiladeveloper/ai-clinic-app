package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * AUTOCOMPLETADO QUE APRENDE, ABIERTO A CUALQUIER CAMPO CLÍNICO — lo PURO.
 *
 * Gemelo de `lib/campos-aprendibles.ts` (el registro de claves) y de
 * `lib/frases-campo.ts` (los filtros) de la web. Spec:
 * `docs/superpowers/specs/2026-10-16-autocompletado-abierto-design.md`;
 * contrato: `docs/app-contrato-chips.md` §5 (GET/POST `/api/staff/frases`).
 *
 * La app NO guarda bases propias: las manda el servidor en el GET con lo
 * aprendido (un backend viejo → sin chips, el campo de siempre). Acá vive lo
 * que hace falta en el teléfono:
 *  - qué claves aprenden y cómo se trocean (para no hacer viajes inútiles);
 *  - qué se manda a aprender: solo lo NUEVO respecto de lo que había, sin datos
 *    del paciente (la RPC filtra igual; el servidor manda);
 *  - qué se muestra: base ∪ aprendido con la regla común de [frasesCampoPlan]
 *    (chip solo con 2+ usos) y, en psicología, solo lo visto en 2+ pacientes.
 *
 * Si cambia una regla en la web, cambia acá (y en sus tests).
 */

// ── Registro de campos (gemelo de CAMPOS_APRENDIBLES, sin las bases) ─────────

/** Un campo que aprende: [porLinea] = una frase por línea (si no, con coma); [max] = largo de una frase. */
data class CampoAprendible(val clave: String, val porLinea: Boolean, val max: Int) {
    /** Psicología (Ley 30947): la RPC lo deduce del prefijo `psico.`. */
    val sensible: Boolean get() = esCampoSensible(clave)
}

/** Las claves (una constante por campo: un typo no compila). */
object ClaveFrase {
    const val PSICO_MOTIVO = "psico.eval.motivo"
    const val PSICO_OBSERVACION = "psico.eval.observacion"
    const val PSICO_CONCLUSIONES = "psico.eval.conclusiones"
    const val PSICO_RECOMENDACIONES = "psico.eval.recomendaciones"
    const val FISIO_CARACTERISTICAS = "fisio.eval.caracteristicas"
    const val ESTETICA_OBSERVACIONES = "estetica.aplicacion.observaciones"
    const val ESTETICA_NOTAS_ZONA = "estetica.aplicacion.notas_zona"
    const val CAPILAR_OBSERVACIONES = "capilar.tricoscopia.observaciones"
    const val TRATAMIENTO_INDICACIONES = "tratamiento.indicaciones"
}

/**
 * Las claves del análisis psicológico, por área (las de [AREAS_ANALISIS]).
 * Gemelo de `CAMPO_AREA_PSICO`.
 */
val CAMPO_AREA_PSICO: Map<String, String> = linkedMapOf(
    "intelectual" to "psico.eval.area.intelectual",
    "visomotora" to "psico.eval.area.visomotora",
    "emocional" to "psico.eval.area.emocional",
    "personalidad" to "psico.eval.area.personalidad",
    "familiar_social" to "psico.eval.area.familiar_social",
    "vocacional" to "psico.eval.area.vocacional",
)

/** Los campos que aprenden, por clave. Gemelo de `CAMPOS_APRENDIBLES` (modo y largo). */
val CAMPOS_APRENDIBLES: Map<String, CampoAprendible> = buildList {
    add(CampoAprendible(ClaveFrase.PSICO_MOTIVO, porLinea = false, max = 80))
    add(CampoAprendible(ClaveFrase.PSICO_OBSERVACION, porLinea = true, max = 80))
    CAMPO_AREA_PSICO.values.forEach { add(CampoAprendible(it, porLinea = true, max = 80)) }
    add(CampoAprendible(ClaveFrase.PSICO_CONCLUSIONES, porLinea = true, max = 80))
    add(CampoAprendible(ClaveFrase.PSICO_RECOMENDACIONES, porLinea = true, max = 80))
    add(CampoAprendible(ClaveFrase.FISIO_CARACTERISTICAS, porLinea = false, max = 60))
    add(CampoAprendible(ClaveFrase.ESTETICA_OBSERVACIONES, porLinea = true, max = 120))
    add(CampoAprendible(ClaveFrase.ESTETICA_NOTAS_ZONA, porLinea = false, max = 60))
    add(CampoAprendible(ClaveFrase.CAPILAR_OBSERVACIONES, porLinea = true, max = 120))
    add(CampoAprendible(ClaveFrase.TRATAMIENTO_INDICACIONES, porLinea = true, max = 160))
}.associateBy { it.clave }

/**
 * Las de la evaluación psicológica que aprenden (una pantalla = una consulta).
 * Solo las secciones que se escriben con frases técnicas cortas: las narrativas
 * (historia, familia, fuentes, quién solicita…) NUNCA aprenden.
 */
val CAMPOS_PSICO_EVAL: List<String> = listOf(ClaveFrase.PSICO_MOTIVO, ClaveFrase.PSICO_OBSERVACION) +
    CAMPO_AREA_PSICO.values + listOf(ClaveFrase.PSICO_CONCLUSIONES, ClaveFrase.PSICO_RECOMENDACIONES)

/** Los de las aplicaciones estéticas (los aprende el SERVIDOR al guardar: la app solo los muestra). */
val CAMPOS_APLICACION_ESTETICA: List<String> = listOf(ClaveFrase.ESTETICA_OBSERVACIONES, ClaveFrase.ESTETICA_NOTAS_ZONA)

/** Pacientes distintos en los que tiene que aparecer una frase sensible para ofrecerse. Gemelo de la RLS. */
const val MIN_PACIENTES_SENSIBLE = 2
/** Largo máximo de una frase aprendida en un campo sensible (psico.*). Gemelo de la RPC. */
const val MAX_SENSIBLE = 80
/** Frases que se mandan a aprender por campo en UN guardado (la RPC corta en 20). */
const val MAX_FRASES_POR_GUARDADO = 12
/** Campos por GET (el servidor rechaza más). */
const val MAX_CAMPOS_POR_LECTURA = 20

fun esCampoAprendible(clave: String?): Boolean = clave != null && clave in CAMPOS_APRENDIBLES

/** ¿Contenido sensible (psicología)? Como la RPC: por el prefijo, esté o no en el registro. */
fun esCampoSensible(clave: String): Boolean = clave.startsWith("psico.")

// ── Troceo (gemelo de trocearItems + trocearCampo) ──────────────────────────

private val RE_VINETA = Regex("""^\s*[-•*·]+\s*""")
private val RE_ESPACIOS_FRASE = Regex("""\s+""")
private val RE_PUNTUACION_FIN = Regex("""[\s.,;:]+$""")
/** Fin de oración: punto, espacio y mayúscula ("…sesiones. Requirió…"); "aprox. 3 cm" no corta. */
private val RE_ORACION = Regex("""\.\s+(?=[A-ZÁÉÍÓÚÑ¿¡])""")

/**
 * Texto → ítems. Quita viñetas y la puntuación del final.
 *  · con coma ([porLinea] false): corta por coma, punto y coma y salto de línea,
 *    pero no dentro de paréntesis ("Mecánico (al caminar, al subir)" es UNO);
 *  · por línea: corta SOLO por salto de línea (una indicación lleva comas).
 */
fun trocearItems(texto: String?, porLinea: Boolean): List<String> {
    val out = ArrayList<String>()
    val actual = StringBuilder()
    var parentesis = 0
    fun cerrar() {
        val limpio = actual.toString().replace(RE_VINETA, "").replace(RE_ESPACIOS_FRASE, " ").trim()
            .replace(RE_PUNTUACION_FIN, "").trim()
        if (limpio.isNotEmpty()) out += limpio
        actual.clear()
    }
    for (ch in texto.orEmpty()) {
        if (ch == '(') parentesis++ else if (ch == ')') parentesis = maxOf(0, parentesis - 1)
        if (ch == '\n' || (!porLinea && (ch == ',' || ch == ';') && parentesis == 0)) { cerrar(); continue }
        actual.append(ch)
    }
    cerrar()
    return out
}

/** Texto de un campo → frases: como lo trocea el campo y, además, por oraciones (un párrafo nunca es UNA frase). */
fun trocearCampo(texto: String?, porLinea: Boolean): List<String> =
    trocearItems(texto, porLinea)
        .flatMap { it.split(RE_ORACION) }
        .map { it.replace(RE_PUNTUACION_FIN, "").trim() }
        .filter { it.isNotEmpty() }

// ── Privacidad (gemelos de esFraseRegistrable y de la RPC registrar_frases) ──

/**
 * Parentesco y títulos: en un campo sensible delatan a un tercero ("la madre
 * refiere…", "la profesora indica…"). Plegadas (sin tildes, ñ→n). GEMELA del
 * ARRAY de registrar_frases.
 */
val PALABRAS_TERCEROS: Set<String> = setOf(
    "padre", "padres", "madre", "mama", "papa", "hijo", "hija", "hijos", "hijas", "esposo", "esposa",
    "hermano", "hermana", "hermanos", "abuelo", "abuela", "tio", "tia", "primo", "prima", "nieto", "nieta",
    "novio", "novia", "pareja", "enamorado", "enamorada", "padrastro", "madrastra", "suegro", "suegra",
    "profesor", "profesora", "tutor", "tutora", "senor", "senora", "sr", "sra", "srta", "don", "dona",
    "dr", "dra", "doctor", "doctora", "lic", "licenciado", "licenciada",
)

/**
 * Instrumentos y especialidades que se escriben con mayúscula y NO son personas
 * ("Test de Bender", "derivar a Neurología"). GEMELA de la lista de registrar_frases.
 */
val INSTRUMENTOS_PSICO: List<String> = listOf(
    "Bender", "Wechsler", "Raven", "Rorschach", "Goodenough", "Machover", "Koppitz", "Frostig", "Conners", "Bayley",
    "Denver", "Battelle", "Vineland", "Achenbach", "Coopersmith", "Millon", "Minnesota", "Hamilton", "Beck", "Zung",
    "Kuder", "Stroop", "Luria", "Benton", "Toulouse", "Pieron", "Neurología", "Psiquiatría", "Pediatría",
    "Neuropediatría", "Psicología",
)

/** Un nombre propio EN MEDIO de la frase (palabra Capitalizada que no es la primera). Las siglas pasan. */
private val RE_NOMBRE_PROPIO = Regex("""\s[A-ZÁÉÍÓÚÑ][a-záéíóúñü]{2,}""")
// Límites de palabra con letras acentuadas (\b no las conoce).
private val RE_INSTRUMENTOS = Regex("""(^|[^\p{L}])(${INSTRUMENTOS_PSICO.joinToString("|")})(?=[^\p{L}]|$)""")
private val RE_SISTEMA_FRASE = Regex("""autom[aá]tic|confirmar (hora|con)|cerrada sin|sin registrar""", RegexOption.IGNORE_CASE)
private val RE_CONTACTO = Regex("""\b(whatsapp|correo)\b""", RegexOption.IGNORE_CASE)
private val RE_LETRAS = Regex("""[a-záéíóúñü]{2}""", RegexOption.IGNORE_CASE)
private val RE_SIMBOLOS_FRASE = Regex("""[\[\]{}<>@]""")
private val RE_NO_ALFANUM = Regex("[^a-z0-9]+")

/**
 * ¿Esta frase se puede aprender en ese campo? Largo (3..max del campo), con
 * letras, sin símbolos ni emojis, sin 7+ dígitos, fechas, palabras de documento
 * o contacto, sin el nombre del paciente. En un campo sensible (psico.*),
 * además ≤ 80, sin parentesco/títulos ni nombres propios.
 */
fun esFraseRegistrable(frase: String, clave: String, nombrePaciente: String? = null): Boolean {
    val def = CAMPOS_APRENDIBLES[clave] ?: return false
    val t = frase.replace(RE_ESPACIOS_FRASE, " ").trim()
    val sensible = def.sensible
    val max = if (sensible) minOf(def.max, MAX_SENSIBLE) else def.max
    if (t.length < 3 || t.length > max) return false
    if (!RE_LETRAS.containsMatchIn(t)) return false
    if (RE_SIMBOLOS_FRASE.containsMatchIn(t)) return false
    if (tieneEmoji(t)) return false
    if (RE_SISTEMA_FRASE.containsMatchIn(t) || RE_CONTACTO.containsMatchIn(t)) return false
    if (pareceDatoPersonal(t, nombrePaciente)) return false // DNI, teléfono, fecha, su nombre (≥ 3 letras)
    val palabras = claveChip(t).split(RE_NO_ALFANUM).filter { it.isNotEmpty() }
    // El nombre plegado, como la RPC (≥ 4 letras, sin palabras vacías).
    if (tokensDeNombre(nombrePaciente).any { it in palabras }) return false
    if (sensible) {
        if (palabras.any { it in PALABRAS_TERCEROS }) return false
        if (RE_NOMBRE_PROPIO.containsMatchIn(t.replace(RE_INSTRUMENTOS, "$1"))) return false
    }
    return true
}

/** Primera letra en mayúscula (lo troceado de una lista con comas viene en minúscula). */
private fun enMayuscula(s: String): String = s.replaceFirstChar { it.uppercaseChar() }

/**
 * Lo que se mandaría a aprender de un guardado: las frases registrables del
 * texto nuevo que NO estaban en [antes] (editar un registro no vuelve a sumar
 * lo que ya tenía), sin repetir y con tope. Vacío = no vale la pena el viaje.
 */
fun frasesParaRegistrar(clave: String, texto: String?, antes: String? = null, nombrePaciente: String? = null): List<String> {
    val def = CAMPOS_APRENDIBLES[clave] ?: return emptyList()
    val previas = trocearCampo(antes, def.porLinea).map { claveChip(it) }.toSet()
    val vistas = HashSet<String>()
    val r = ArrayList<String>()
    for (f in trocearCampo(texto, def.porLinea)) {
        val k = claveChip(f)
        if (k.isEmpty() || k in previas || !vistas.add(k)) continue
        if (!esFraseRegistrable(f, clave, nombrePaciente)) continue
        r += enMayuscula(f)
        if (r.size >= MAX_FRASES_POR_GUARDADO) break
    }
    return r
}

// ── Lo que llega del GET y lo que se pinta ──────────────────────────────────

/** Una frase aprendida: en cuántos registros apareció y en cuántos pacientes distintos. */
data class AprendidaCampo(val texto: String, val usos: Int, val pacientes: Int = 0)

/** Un campo del GET: su base (la del rubro, o la general) y lo aprendido, más usado primero. */
data class RespuestaFrasesCampo(
    val campo: String,
    val porLinea: Boolean,
    val base: List<String>,
    val aprendidas: List<AprendidaCampo>,
)

/** ¿Se puede ofrecer? Un campo sensible exige 2+ pacientes (una frase de UN paciente nunca va a otro). */
private fun ofrecible(clave: String, a: AprendidaCampo): Boolean =
    !esCampoSensible(clave) || a.pacientes >= MIN_PACIENTES_SENSIBLE

/**
 * Chips y autocompletado de un campo (gemelo de `frasesDeCampoAbierto`): la
 * base ∪ lo aprendido con la regla común de [frasesCampoPlan] — chip aprendido
 * solo con 2+ usos y ≤ 40 caracteres; lo escrito una vez, solo al
 * autocompletar. Lo leído se vuelve a filtrar (si una regla se endurece, lo
 * viejo no se muestra) y, en psicología, lo visto en menos de 2 pacientes no
 * aparece NI como chip NI en el autocompletado.
 */
fun frasesDeCampoAbierto(r: RespuestaFrasesCampo): FrasesCampoPlan {
    val conocido = esCampoAprendible(r.campo)
    val visibles = r.aprendidas
        .filter { ofrecible(r.campo, it) }
        .filter { !conocido || esFraseRegistrable(it.texto, r.campo) }
        .map { AprendidoPlan(it.texto, it.usos) }
    return frasesCampoPlan(r.base, visibles, r.porLinea)
}

private fun JsonElement?.textoONull(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

private fun JsonElement?.enteroONull(): Int? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.toInt()

/** Un campo del GET → modelo (tolerante: lo que no tiene la forma del contrato, null). */
private fun respuestaCampoDe(clave: String, v: JsonElement?): RespuestaFrasesCampo? {
    val o = v as? JsonObject ?: return null
    val campo = o["campo"].textoONull() ?: clave
    val modo = o["modo"].textoONull()
    val porLinea = when (modo) {
        "linea" -> true
        "coma" -> false
        else -> CAMPOS_APRENDIBLES[campo]?.porLinea ?: false
    }
    val base = (o["base"] as? JsonArray).orEmpty().mapNotNull { it.textoONull()?.trim()?.takeIf { t -> t.isNotEmpty() } }
    val aprendidas = (o["aprendidas"] as? JsonArray).orEmpty().mapNotNull { x ->
        val a = x as? JsonObject ?: return@mapNotNull null
        val t = a["texto"].textoONull()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        AprendidaCampo(t, maxOf(1, a["usos"].enteroONull() ?: 1), maxOf(0, a["pacientes"].enteroONull() ?: 0))
    }
    return RespuestaFrasesCampo(campo, porLinea, base, aprendidas)
}

/**
 * La respuesta del GET → campo → frases. Acepta las dos formas del contrato
 * (`{ items: { clave: {…} } }` con `?campos=`, o un solo campo con `?campo=`).
 * Solo devuelve los [pedidos]. Otra forma → vacío (el campo queda como siempre).
 */
fun parsearFrasesCampos(o: JsonObject?, pedidos: List<String>): Map<String, RespuestaFrasesCampo> {
    if (o == null) return emptyMap()
    val items = o["items"] as? JsonObject
    val r = LinkedHashMap<String, RespuestaFrasesCampo>()
    if (items != null) {
        for (clave in pedidos) respuestaCampoDe(clave, items[clave])?.let { r[clave] = it }
    } else {
        val campo = o["campo"].textoONull()
        if (campo != null && campo in pedidos) respuestaCampoDe(campo, o)?.let { r[campo] = it }
    }
    return r
}

/**
 * Cuerpo del POST (contrato §5). `textos` = el texto del campo TAL COMO SE
 * GUARDÓ (el servidor lo trocea); [antes] = lo que tenía (solo se aprende lo
 * nuevo). Los opcionales vacíos no viajan.
 */
fun cuerpoRegistrarFrases(
    campo: String, texto: String, antes: String?, especialidadId: String?, pacienteId: String?, nombrePaciente: String?,
): JsonObject = buildJsonObject {
    put("campo", campo)
    putJsonArray("textos") { add(texto) }
    antes?.takeIf { it.isNotBlank() }?.let { put("antes", it) }
    // El servidor rechaza un id mal formado (400): se valida antes ([esUuid], PagarConSaldo.kt).
    especialidadId?.takeIf { esUuid(it) }?.let { put("especialidadId", it) }
    pacienteId?.takeIf { esUuid(it) }?.let { put("pacienteId", it) }
    nombrePaciente?.trim()?.takeIf { it.isNotEmpty() }?.let { put("nombrePaciente", it) }
}

/**
 * ¿Vale la pena el POST? Campo del registro, algo NUEVO que aprender y, en un
 * campo sensible, con el paciente (sin él la RPC no puede contar pacientes
 * distintos: 400 DATOS_INVALIDOS).
 */
fun debeRegistrarFrases(campo: String, texto: String?, antes: String?, pacienteId: String?, nombrePaciente: String?): Boolean {
    if (!esCampoAprendible(campo) || texto.isNullOrBlank()) return false
    if (esCampoSensible(campo) && !esUuid(pacienteId)) return false
    return frasesParaRegistrar(campo, texto, antes, nombrePaciente).isNotEmpty()
}

// ── Dónde se aplica ─────────────────────────────────────────────────────────

/**
 * Evaluación psicológica → texto de cada campo que aprende (gemelo de
 * `textosPsicoAprendibles`). Solo las secciones técnicas: las narrativas
 * (historia, familia, fuentes…) nunca salen de aquí.
 */
fun textosPsicoAprendibles(ev: EvaluacionPsico): Map<String, String> = buildMap {
    put(ClaveFrase.PSICO_MOTIVO, ev.entrevista.texto("motivo"))
    put(ClaveFrase.PSICO_OBSERVACION, ev.observacion.notas)
    put(ClaveFrase.PSICO_CONCLUSIONES, ev.analisis.conclusiones)
    put(ClaveFrase.PSICO_RECOMENDACIONES, ev.plan.recomendaciones)
    for ((area, clave) in CAMPO_AREA_PSICO) put(clave, ev.analisis.areas[area].orEmpty())
}

/** Un campo a aprender: su texto de ahora y el que tenía (lo ya aprendido en esta pantalla). */
data class CambioFrase(val campo: String, val texto: String, val antes: String)

/** Los campos que cambiaron respecto de lo ya aprendido (los iguales no viajan). */
fun cambiosParaAprender(previo: Map<String, String>, ahora: Map<String, String>): List<CambioFrase> =
    ahora.mapNotNull { (campo, texto) ->
        val antes = previo[campo].orEmpty()
        if (texto == antes) null else CambioFrase(campo, texto, antes)
    }

/**
 * La base de las indicaciones del tratamiento depende del rubro del servicio
 * (`rubro` del GET): capilar, estética o fisioterapia tienen la suya; el resto,
 * la general (null). Por los mapas que arma la web en /api/staff/contexto.
 */
fun rubroFrasesDe(
    especialidadId: String?,
    mapaCapilar: MapaCapilar = MapaCapilar(),
    mapaEstetica: MapaEstetica = MapaEstetica(),
    mapaFisio: MapaFisio = MapaFisio(),
): String? = when {
    citaEsCapilar(mapaCapilar, especialidadId) -> "capilar"
    citaEsEstetica(mapaEstetica, especialidadId) -> "estetica"
    citaEsFisio(mapaFisio, especialidadId) -> "fisioterapia"
    else -> null
}

/** [rubroFrasesDe] con los mapas del contexto del staff (sin contexto: la base general). */
fun ContextoStaff?.rubroFrasesDe(especialidadId: String?): String? =
    this?.let { rubroFrasesDe(especialidadId, it.mapaCapilar, it.mapaEstetica, it.mapaFisio) }
