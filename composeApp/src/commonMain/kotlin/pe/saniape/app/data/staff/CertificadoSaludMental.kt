package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ─────────────────────────────────────────────────────────────────────────────
// 🧠 CERTIFICADO DE SALUD MENTAL — reglas PURAS, GEMELAS de
// lib/certificado-salud-mental.ts (web). Contrato staff:
// docs/psiquiatria-documentos-medicos.md §8; diseño:
// docs/superpowers/specs/2026-10-16-certificado-salud-mental-design.md.
//
// REQUISITO DEL CLIENTE (aprobado por QA): la parte clínica son CASILLAS, una
// por hallazgo, con su opción NORMAL marcada. Con todo en normal el texto es
// IDÉNTICO, carácter por carácter, al de los psiquiatras (test con el literal,
// el mismo de la web). Con una alternativa marcada el párrafo se rearma y
// "goza de buena salud mental" deja de ser coherente: se avisa, se ofrece una
// conclusión sobria y mantenerla exige CONFIRMAR (aquí, en el servidor y en un
// CHECK de la base). Cualquier cambio aquí va también en la web (y al revés).
// ─────────────────────────────────────────────────────────────────────────────

const val TIPO_CERTIFICADO = "salud_mental"
/** Clave del documento en las rutas del portal y de impresión. */
const val DOC_CERTIFICADO = "certificado_salud_mental"
const val NOMBRE_CERTIFICADO = "Certificado de salud mental"
const val ICONO_CERTIFICADO = "🧠"
const val PREFIJO_CERTIFICADO = "CSM"

/** "CSM N° 000012". */
fun formatearNumeroCertificado(n: Int?): String {
    val num = if (n == null || n < 1) "—" else n.toString().padStart(6, '0')
    return "$PREFIJO_CERTIFICADO N° $num"
}

/** Gemelos de los CHECK de la migración (LIMITES_CERTIFICADO). */
const val CERT_TEXTO_MIN = 40
const val CERT_TEXTO_MAX = 5000
const val CERT_FINALIDAD_MAX = 160
const val CERT_LUGAR_MAX = 80
const val CERT_PLANTILLA_MIN = 20
const val CERT_PLANTILLA_MAX = 5000

/** Días hacia atrás que se acepta fechar el certificado (como los otros documentos). */
const val MAX_DIAS_ATRAS_CERTIFICADO = 30

/** Espacios como los de `\s` en JS (incluye el espacio duro): Java/Kotlin no los cuenta todos. */
private const val WS = "[\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]"
private val RE_ESPACIOS = Regex("$WS+")

// ─────────────────────────────────────────────────────────────────────────────
// Casillas del examen
// ─────────────────────────────────────────────────────────────────────────────

val CLAVES_HALLAZGO: List<String> = listOf("estimulos", "juicio", "control", "ideas", "inteligencia", "memoria", "voluntad")

/** [clave] = "normal" o la de la alternativa; [etiqueta] = lo que dice la casilla; [frase] = la del texto. */
data class OpcionHallazgo(val clave: String, val etiqueta: String, val frase: String)

data class DefHallazgo(
    val clave: String,
    val titulo: String,
    val normal: OpcionHallazgo,
    val alternativas: List<OpcionHallazgo>,
    /** Juicio y control: normales, van en la conclusión ("capacidad de juicio y control de su voluntad adecuado"). */
    val enConclusion: Boolean = false,
)

/**
 * Los 7 hallazgos, en el orden del texto original. Las frases NORMALES son las
 * de los psiquiatras, literales. Las alternativas, sobrias: describen, no diagnostican.
 */
val HALLAZGOS: List<DefHallazgo> = listOf(
    DefHallazgo(
        "estimulos", "Respuesta a estímulos",
        OpcionHallazgo("normal", "Responde a estímulos", "es capaz de responder a estímulos"),
        listOf(OpcionHallazgo("lenta", "Responde con lentitud", "responde a estímulos con lentitud")),
    ),
    DefHallazgo(
        "juicio", "Juicio",
        OpcionHallazgo("normal", "Adecuado", "presenta capacidad de juicio adecuada"),
        listOf(
            OpcionHallazgo("parcial", "Parcialmente conservado", "presenta juicio parcialmente conservado"),
            OpcionHallazgo("comprometido", "Comprometido", "presenta juicio comprometido"),
        ),
        enConclusion = true,
    ),
    DefHallazgo(
        "control", "Control de la voluntad",
        OpcionHallazgo("normal", "Adecuado", "presenta control de su voluntad adecuado"),
        listOf(OpcionHallazgo("impulsos", "Dificultad en el control de impulsos", "presenta dificultad para el control de sus impulsos")),
        enConclusion = true,
    ),
    DefHallazgo(
        "ideas", "Ideas patológicas / delirios",
        OpcionHallazgo("normal", "Sin ideas patológicas ni delirios", "no presenta ideas patológicas ni delirios"),
        listOf(
            OpcionHallazgo("sobrevaloradas", "Ideas sobrevaloradas, sin delirios", "presenta ideas sobrevaloradas, sin delirios"),
            OpcionHallazgo("delirantes", "Ideas delirantes", "presenta ideas delirantes"),
        ),
    ),
    DefHallazgo(
        "inteligencia", "Inteligencia",
        OpcionHallazgo("normal", "Dentro del promedio", "presenta inteligencia dentro del promedio normal clínico"),
        listOf(OpcionHallazgo("bajo", "Impresiona por debajo del promedio", "impresiona inteligencia por debajo del promedio normal clínico")),
    ),
    DefHallazgo(
        "memoria", "Memoria",
        OpcionHallazgo("normal", "Conservada (pasados y presentes)", "tiene memoria conservada para hechos pasados y presentes de acuerdo a su edad"),
        listOf(
            OpcionHallazgo("leve", "Leve alteración para hechos recientes", "presenta leve alteración de la memoria para hechos recientes"),
            OpcionHallazgo("alterada", "Alterada (recientes y remotos)", "presenta alteración de la memoria para hechos recientes y remotos"),
        ),
    ),
    DefHallazgo(
        "voluntad", "Voluntad",
        OpcionHallazgo("normal", "Conservada", "mantiene conservada su voluntad"),
        listOf(OpcionHallazgo("disminuida", "Disminuida", "presenta disminución de la voluntad")),
    ),
)

private val DEF_HALLAZGO: Map<String, DefHallazgo> = HALLAZGOS.associateBy { it.clave }

data class EstadoHallazgo(val incluido: Boolean, val opcion: String)

/** clave del hallazgo → su estado (las 7 claves de [CLAVES_HALLAZGO]). */
typealias EstadoHallazgos = Map<String, EstadoHallazgo>

/** Todas marcadas en NORMAL: el texto original. */
fun hallazgosNormales(): EstadoHallazgos = CLAVES_HALLAZGO.associateWith { EstadoHallazgo(true, "normal") }

private fun opcionDe(h: DefHallazgo, clave: String?): OpcionHallazgo =
    h.alternativas.firstOrNull { it.clave == clave } ?: h.normal

/** ¿Alguna casilla marcada con una alternativa (no normal)? */
fun hayAlternativas(est: EstadoHallazgos?): Boolean {
    if (est == null) return false
    return HALLAZGOS.any { h -> est[h.clave]?.let { it.incluido && opcionDe(h, it.opcion).clave != "normal" } == true }
}

/** Lo que se guarda en `certificados_medicos.hallazgos`: (clave, opcion) de las MARCADAS. */
fun hallazgosParaGuardar(est: EstadoHallazgos?): List<Pair<String, String>> {
    if (est == null) return emptyList()
    return HALLAZGOS.filter { est[it.clave]?.incluido == true }.map { it.clave to opcionDe(it, est[it.clave]?.opcion).clave }
}

/** Del JSON (fila de la base) al estado: claves desconocidas fuera; lo que no viene, desmarcado. null si no es lista. */
fun hallazgosDesdeJson(v: Any?): EstadoHallazgos? {
    val arr = v as? JsonArray ?: return null
    val est = CLAVES_HALLAZGO.associateWith { EstadoHallazgo(false, "normal") }.toMutableMap()
    for (x in arr) {
        val o = x as? JsonObject ?: continue
        val clave = (o["clave"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
        val h = DEF_HALLAZGO[clave] ?: continue
        val opcion = (o["opcion"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: "normal"
        est[h.clave] = EstadoHallazgo(true, opcionDe(h, opcion).clave)
    }
    return est
}

private val RE_E_ANTE_I = Regex("^h?i(?![aeiouáéíóú])", RegexOption.IGNORE_CASE)

/** "a, b y c". "y" → "e" ante "i-"/"hi-" (no ante "hie-"/"hia-"). */
fun unirConY(items: List<String>): String {
    val xs = items.filter { it.isNotEmpty() }
    if (xs.size <= 1) return xs.firstOrNull().orEmpty()
    val ult = xs.last()
    val conj = if (RE_E_ANTE_I.containsMatchIn(ult)) "e" else "y"
    return "${xs.dropLast(1).joinToString(", ")} $conj $ult"
}

// ─────────────────────────────────────────────────────────────────────────────
// Motivo (finalidad)
// ─────────────────────────────────────────────────────────────────────────────

val CLAVES_FINALIDAD: List<String> = listOf("interesado", "licencia", "sucamec", "trabajo", "estudios", "otro")

/** Chip, la línea de la lista (`finalidad` en la base) y el final de la frase. */
data class Finalidad(val chip: String, val linea: String?, val para: String?)

val FINALIDADES: Map<String, Finalidad> = mapOf(
    "interesado" to Finalidad("A solicitud del interesado", null, "para los trámites que estime conveniente"),
    "licencia" to Finalidad("🚗 Licencia de conducir", "Licencia de conducir", "para el trámite de su licencia de conducir"),
    "sucamec" to Finalidad("🛡️ Porte de armas (SUCAMEC)", "Porte de armas (SUCAMEC)", "para el trámite de licencia de uso de armas de fuego ante la SUCAMEC"),
    "trabajo" to Finalidad("💼 Trabajo", "Trabajo", "para fines laborales"),
    "estudios" to Finalidad("🎓 Estudios", "Estudios", "para fines académicos"),
    "otro" to Finalidad("✏️ Otro", null, null),
)

private val RE_FIN_PUNTUACION = Regex("[.;,:]+$")
private val RE_PARA_INICIO = Regex("^para$WS+", RegexOption.IGNORE_CASE)

private fun limpiarOtra(s: String?): String? {
    val t = (s.orEmpty()).replace(RE_ESPACIOS, " ").trim().replace(RE_FIN_PUNTUACION, "").replace(RE_PARA_INICIO, "")
    return t.ifEmpty { null }?.take(CERT_FINALIDAD_MAX)
}

private fun capitalizar(s: String): String = s.replaceFirstChar { it.uppercase() }

/** La línea que se guarda en `finalidad` (null = a solicitud del interesado, sin más). */
fun lineaFinalidad(f: String, otra: String? = null): String? {
    if (f == "otro") return limpiarOtra(otra)?.let { capitalizar(it) }
    return FINALIDADES[f]?.linea
}

// ─────────────────────────────────────────────────────────────────────────────
// Datos del paciente → frases
// ─────────────────────────────────────────────────────────────────────────────

data class PacienteCertificado(
    val nombre: String,
    /** pacientes.dni (el número, sea DNI, CE, pasaporte…). */
    val documento: String? = null,
    /** pacientes.tipo_documento ('DNI', 'Carné de extranjería', 'Pasaporte', 'CI', 'RUT', 'Otro' | null). */
    val tipoDocumento: String? = null,
    /** historias_clinicas.tipo_documento (la filiación), si hay. */
    val tipoDocumentoHc: String? = null,
    /** País de la sede (el documento nacional sin tipo guardado). */
    val pais: String? = null,
    /** Edad al día del documento (en la zona de la sede). */
    val edad: Int? = null,
    /** 'F' | 'M'; otro = sin dato. */
    val sexo: String? = null,
)

private fun esFemenino(sexo: String?): Boolean = sexo == "F"

/**
 * "identificado" / "identificada". SIN dato de sexo, la redacción ORIGINAL de
 * los psiquiatras ("identificado"): casi ninguna ficha tiene sexo y el texto
 * tiene que salir idéntico. El formulario ofrece el chip Masculino / Femenino.
 */
fun identificadoSegun(sexo: String?): String = if (esFemenino(sexo)) "identificada" else "identificado"

/** "del interesado" / "de la interesada" (sin dato: el original, "del interesado"). */
fun interesadoSegun(sexo: String?): String = if (esFemenino(sexo)) "de la interesada" else "del interesado"

/** "el DNI 12345678", "el carné de extranjería N° 001234567"… null = sin documento. */
fun fraseDocumento(p: PacienteCertificado): String? {
    val n = p.documento?.trim().orEmpty()
    if (n.isEmpty()) return null
    return when (etiquetaDocumentoPaciente(p.tipoDocumento, p.pais, n, p.tipoDocumentoHc)) {
        "DNI" -> "el DNI $n"
        "Carné de extranjería" -> "el carné de extranjería N° $n"
        "Pasaporte" -> "el pasaporte N° $n"
        "CI" -> "la cédula de identidad N° $n"
        "RUT" -> "el RUT N° $n"
        else -> "el documento de identidad N° $n"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Armado del texto
// ─────────────────────────────────────────────────────────────────────────────

/** Marcadores de la plantilla, en el orden en que se reemplazan al volver a plantilla. */
val MARCADORES: List<String> = listOf("SUSCRIBE", "NOMBRE", "IDENTIFICACION", "EDAD", "CONCLUSION", "CERTIFICADO", "FINALIDAD")

/** La plantilla de fábrica: con todo en normal, [armarTexto] devuelve el texto literal de los psiquiatras. */
const val PLANTILLA_FABRICA =
    "[SUSCRIBE] certifica haber atendido a [NOMBRE][IDENTIFICACION][EDAD] y que, luego de la evaluación, [CONCLUSION].\n" +
        "De esta manera, se expide el presente [CERTIFICADO] [FINALIDAD]."

/** "buena" = "goza de buena salud mental…" (texto original); "hallazgos" = conclusión sobria. */
const val CONCLUSION_BUENA = "buena"
const val CONCLUSION_HALLAZGOS = "hallazgos"

data class OpcionesCertificado(
    /** "El" / "La" que suscribe (el profesional). */
    val suscribe: String = "El",
    val hallazgos: EstadoHallazgos = hallazgosNormales(),
    val conclusion: String = CONCLUSION_BUENA,
    val finalidad: String = "interesado",
    val finalidadOtra: String? = null,
    /** El examen mental de la atención, si se incluye (null = no). */
    val examenMental: String? = null,
)

fun opcionesPorDefecto(suscribe: String = "El"): OpcionesCertificado =
    OpcionesCertificado(suscribe = if (suscribe == "La") "La" else "El")

private val RE_SUSCRIBE_LA = Regex("^(m[eé]dica|psic[oó]loga|doctora|dra\\b|licenciada)", RegexOption.IGNORE_CASE)

/** "El que suscribe" por el título del profesional ("Médica Psiquiatra", "Psicóloga" → La). */
fun suscribePorTitulo(titulo: String?): String = if (RE_SUSCRIBE_LA.containsMatchIn(titulo.orEmpty().trim())) "La" else "El"

private val RE_SALTOS = Regex("\\n+")
private val RE_FIN_PUNTO = Regex("[.;]+$")

/** El examen mental de la atención en una línea (sin saltos ni punto final). */
fun examenMentalEnLinea(t: String?): String? {
    val s = t.orEmpty().split(RE_SALTOS).map { it.trim().replace(RE_FIN_PUNTO, "") }.filter { it.isNotEmpty() }.joinToString("; ")
    return s.ifEmpty { null }?.take(1500)
}

/** La conclusión (lo que va entre "luego de la evaluación, " y el punto). */
fun conclusionCertificado(o: OpcionesCertificado): String {
    val est = o.hallazgos
    fun marcada(k: String): OpcionHallazgo? {
        val e = est[k] ?: return null
        return if (e.incluido) opcionDe(DEF_HALLAZGO.getValue(k), e.opcion) else null
    }
    val cuerpo: String
    if (o.conclusion == CONCLUSION_BUENA) {
        val jN = marcada("juicio")?.clave == "normal"
        val cN = marcada("control")?.clave == "normal"
        // Literal del texto original cuando van los dos.
        val enConclusion = when {
            jN && cN -> "capacidad de juicio y control de su voluntad adecuado"
            jN -> "capacidad de juicio adecuada"
            cN -> "control de su voluntad adecuado"
            else -> ""
        }
        val lista = HALLAZGOS
            .filter { h -> !(h.enConclusion && marcada(h.clave)?.clave == "normal") }
            .mapNotNull { h -> marcada(h.clave)?.frase }
            .filter { it.isNotEmpty() }
        cuerpo = "se concluye que goza de buena salud mental" +
            (if (enConclusion.isNotEmpty()) ", $enConclusion" else "") +
            (if (lista.isNotEmpty()) " ya que ${unirConY(lista)}" else "")
    } else {
        val lista = HALLAZGOS.mapNotNull { marcada(it.clave)?.frase }.filter { it.isNotEmpty() }
        cuerpo = if (lista.isNotEmpty()) "se evidencia que ${unirConY(lista)}" else "se realizó la evaluación de su salud mental"
    }
    val em = examenMentalEnLinea(o.examenMental)
    return if (em != null) "$cuerpo. Al examen mental: $em" else cuerpo
}

/** Las piezas que reemplazan a cada marcador (también sirven para volver a plantilla). */
fun piezasCertificado(p: PacienteCertificado, o: OpcionesCertificado): Map<String, String> {
    val doc = fraseDocumento(p)
    val edad = p.edad?.takeIf { it >= 0 }?.let { ", de $it ${if (it == 1) "año" else "años"} de edad" }.orEmpty()
    val para = if (o.finalidad == "otro") {
        limpiarOtra(o.finalidadOtra)?.let { "para $it" } ?: FINALIDADES.getValue("interesado").para!!
    } else {
        (FINALIDADES[o.finalidad] ?: FINALIDADES.getValue("interesado")).para!!
    }
    return mapOf(
        "SUSCRIBE" to "${if (o.suscribe == "La") "La" else "El"} que suscribe",
        "NOMBRE" to p.nombre.replace(RE_ESPACIOS, " ").trim(),
        "IDENTIFICACION" to (doc?.let { ", ${identificadoSegun(p.sexo)} con $it" } ?: ""),
        "EDAD" to edad,
        "CONCLUSION" to conclusionCertificado(o),
        "CERTIFICADO" to if (o.conclusion == CONCLUSION_BUENA) "certificado de buena salud mental" else "certificado de salud mental",
        "FINALIDAD" to "a solicitud expresa ${interesadoSegun(p.sexo)} $para",
    )
}

private val RE_MARCADOR = Regex("\\[([A-Z_]+)\\]")

/** Reemplaza los marcadores de [plantilla] (los desconocidos quedan como están). Sin plantilla, la de fábrica. */
fun armarTexto(plantilla: String?, p: PacienteCertificado, o: OpcionesCertificado): String {
    val piezas = piezasCertificado(p, o)
    val base = if (plantilla.isNullOrBlank()) PLANTILLA_FABRICA else plantilla
    return base.replace(RE_MARCADOR) { m -> piezas[m.groupValues[1]] ?: m.value }.trim()
}

/** ¿La plantilla tiene la conclusión como marcador? (si no, las casillas no mandan). */
fun plantillaUsaCasillas(plantilla: String?): Boolean =
    (if (plantilla.isNullOrBlank()) PLANTILLA_FABRICA else plantilla).contains("[CONCLUSION]")

private val RE_NOMBRE_ID = Regex("\\[NOMBRE\\](\\[IDENTIFICACION\\])?")

/**
 * "Guardar como mi texto predeterminado": el texto de ESTE paciente vuelve a
 * plantilla — cada pieza generada se cambia por su marcador. Lo reescrito a
 * mano queda literal. Las piezas largas primero (una corta no rompe a una larga).
 */
fun aPlantilla(texto: String, piezas: Map<String, String>): String {
    var out = texto
    val orden = MARCADORES.filter { !piezas[it].isNullOrEmpty() }.sortedByDescending { piezas.getValue(it).length }
    for (m in orden) {
        val pieza = piezas.getValue(m)
        // El nombre, solo la primera vez (uno corto podría aparecer dentro de otra palabra).
        out = if (m == "NOMBRE") out.replaceFirst(pieza, "[$m]") else out.replace(pieza, "[$m]")
    }
    // Las piezas vacías (sin documento, sin edad) se reponen donde van en la de fábrica.
    if (piezas["IDENTIFICACION"].isNullOrEmpty() && !out.contains("[IDENTIFICACION]")) {
        out = out.replaceFirst("[NOMBRE]", "[NOMBRE][IDENTIFICACION]")
    }
    if (piezas["EDAD"].isNullOrEmpty() && !out.contains("[EDAD]")) {
        out = RE_NOMBRE_ID.find(out)?.let { m -> out.replaceRange(m.range, m.value + "[EDAD]") } ?: out
    }
    return out.trim()
}

private val RE_BUENA_SALUD = Regex("buena$WS+salud$WS+mental", RegexOption.IGNORE_CASE)

/** ¿El texto afirma "buena salud mental"? (mayúsculas, varios espacios o espacio duro). Gemelo del CHECK. */
fun afirmaBuenaSalud(texto: String?): Boolean = RE_BUENA_SALUD.containsMatchIn(texto.orEmpty())

/** "Visible para el paciente" por defecto: sí, salvo hallazgos alterados (lo decide el profesional). */
fun visiblePorDefecto(est: EstadoHallazgos?): Boolean = !hayAlternativas(est)

/**
 * ¿Quien mira ve el DETALLE (texto y casillas)? El Admin o un profesional
 * tratante: quien lo emitió o uno de salud mental. Recepción, no (el servidor
 * ya se lo manda sin detalle: texto null).
 */
fun veDetalleCertificado(rol: String?, miTerapeutaId: String?, miEsSaludMental: Boolean, terapeutaIdCertificado: String?): Boolean {
    if (rol == "Admin") return true
    if (miTerapeutaId.isNullOrBlank()) return false
    return miEsSaludMental || (!terapeutaIdCertificado.isNullOrBlank() && terapeutaIdCertificado == miTerapeutaId)
}

/** NUNCA "buena salud mental" con un hallazgo alterado sin confirmación: ¿hay que pedirla? */
fun requiereConfirmarConclusion(texto: String?, est: EstadoHallazgos?): Boolean =
    afirmaBuenaSalud(texto) && hayAlternativas(est)

// ─────────────────────────────────────────────────────────────────────────────
// Emisión: borrador, validación y cuerpo
// ─────────────────────────────────────────────────────────────────────────────

/** Lo que el formulario manda para emitir (BorradorCertificado de la web). */
data class BorradorCertificado(
    val pacienteId: String,
    val terapeutaId: String?,
    /** 'YYYY-MM-DD' en la zona de la sede. */
    val fecha: String,
    val texto: String,
    val lugar: String? = null,
    /** La línea de la lista ("Licencia de conducir"); null = a solicitud del interesado. */
    val finalidad: String? = null,
    /** Las casillas con que se armó (también si después se editó a mano). */
    val hallazgos: EstadoHallazgos? = null,
    val textoManual: Boolean = false,
    /** El profesional confirma "buena salud mental" pese a hallazgos alterados. */
    val confirmaConclusion: Boolean = false,
    /** null = el servidor decide (visiblePorDefecto). */
    val visiblePaciente: Boolean? = null,
    val especialidadId: String? = null,
    val citaId: String? = null,
    val atencionId: String? = null,
    val tratamientoId: String? = null,
)

private val RE_FECHA_CERT = Regex("^\\d{4}-\\d{2}-\\d{2}$")
private val RE_UUID_CERT = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
private val RE_MARCADOR_SUELTO = Regex("\\[(SUSCRIBE|NOMBRE|IDENTIFICACION|EDAD|CONCLUSION|CERTIFICADO|FINALIDAD)\\]")

/** Días entre dos 'YYYY-MM-DD' (b − a), o null si alguno no es fecha. */
private fun difDias(a: String, b: String): Int? {
    val da = runCatching { kotlinx.datetime.LocalDate.parse(a.take(10)) }.getOrNull() ?: return null
    val db = runCatching { kotlinx.datetime.LocalDate.parse(b.take(10)) }.getOrNull() ?: return null
    return (db.toEpochDays() - da.toEpochDays()).toInt()
}

/** Valida ANTES de mandar (el servidor y la base vuelven a validar). [hoy] en la zona de la sede. */
fun validarCertificado(b: BorradorCertificado, hoy: String? = null): List<String> {
    val e = mutableListOf<String>()
    if (!RE_UUID_CERT.matches(b.pacienteId)) e += "Falta el paciente."
    if (b.terapeutaId.isNullOrEmpty()) e += "Elige el profesional que firma."
    if (!RE_FECHA_CERT.matches(b.fecha)) {
        e += "Fecha inválida."
    } else if (hoy != null && RE_FECHA_CERT.matches(hoy)) {
        val dif = difDias(b.fecha, hoy)
        if (dif != null && dif < 0) e += "La fecha del certificado no puede ser futura."
        else if (dif != null && dif > MAX_DIAS_ATRAS_CERTIFICADO) {
            e += "La fecha del certificado no puede ser de hace más de $MAX_DIAS_ATRAS_CERTIFICADO días."
        }
    }
    val t = b.texto.trim()
    when {
        t.length < CERT_TEXTO_MIN -> e += "El texto del certificado está vacío o es muy corto."
        t.length > CERT_TEXTO_MAX -> e += "El texto es demasiado largo para un certificado."
        RE_MARCADOR_SUELTO.containsMatchIn(t) -> e += "Quedó un marcador sin reemplazar en el texto (por ejemplo [NOMBRE])."
    }
    if (b.finalidad.orEmpty().length > CERT_FINALIDAD_MAX) e += "El motivo es demasiado largo."
    if (b.lugar.orEmpty().length > CERT_LUGAR_MAX) e += "El lugar es demasiado largo."
    if (requiereConfirmarConclusion(t, b.hallazgos) && !b.confirmaConclusion) {
        e += "Marcaste hallazgos alterados y el texto dice \"buena salud mental\": usa la conclusión sugerida o confirma que goza de buena salud mental."
    }
    if (listOf(b.especialidadId, b.citaId, b.atencionId, b.tratamientoId).any { !it.isNullOrEmpty() && !RE_UUID_CERT.matches(it) }) {
        e += "Referencia inválida."
    }
    return e
}

/**
 * El cuerpo de `POST /api/staff/certificado/emitir` (como emitirCertificado de
 * la web): `hallazgos` = SOLO las casillas marcadas, con su `claveCliente`.
 * clinica_id nunca viaja (DEFAULT get_clinica_id()).
 */
fun cuerpoEmitirCertificado(b: BorradorCertificado, claveCliente: String): JsonObject = buildJsonObject {
    fun opt(k: String, v: String?) = put(k, v?.trim()?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
    put("pacienteId", b.pacienteId)
    opt("terapeutaId", b.terapeutaId)
    put("fecha", b.fecha)
    opt("lugar", b.lugar)
    opt("finalidad", b.finalidad)
    put("texto", b.texto.trim())
    put("hallazgos", buildJsonArray {
        hallazgosParaGuardar(b.hallazgos).forEach { (clave, opcion) ->
            add(buildJsonObject { put("clave", clave); put("opcion", opcion) })
        }
    })
    put("textoManual", b.textoManual)
    put("confirmaConclusion", b.confirmaConclusion)
    b.visiblePaciente?.let { put("visiblePaciente", it) }
    opt("especialidadId", b.especialidadId)
    opt("citaId", b.citaId)
    opt("atencionId", b.atencionId)
    opt("tratamientoId", b.tratamientoId)
    put("claveCliente", claveCliente)
}

/** Errores de la base → texto para el profesional (mensajeErrorCertificado de la web). */
fun mensajeErrorCertificado(msg: String?): String {
    val m = msg.orEmpty()
    return when {
        "CERTIFICADO_SIN_COLEGIATURA" in m -> "El profesional no tiene N° de colegiatura (CMP / C.Ps.P.). Complétalo en Equipo → profesional."
        "CERTIFICADO_PROFESIONAL_NO_SALUD_MENTAL" in m -> "Solo un psiquiatra o un psicólogo emite el certificado de salud mental."
        "CERTIFICADO_SIN_PROFESIONAL" in m || "CERTIFICADO_PROFESIONAL_INVALIDO" in m -> "Elige un profesional de tu clínica para firmar."
        "CERTIFICADO_PACIENTE_INVALIDO" in m -> "El paciente no pertenece a esta clínica."
        "CERTIFICADO_ESPECIALIDAD_INVALIDA" in m -> "La especialidad no es de salud mental."
        "CERTIFICADO_CITA_INVALIDA" in m || "CERTIFICADO_ATENCION_INVALIDA" in m || "CERTIFICADO_TRATAMIENTO_INVALIDO" in m -> "La atención no es de este paciente."
        "CERTIFICADO_FECHA_INVALIDA" in m -> "La fecha del certificado debe ser de hoy o de hasta $MAX_DIAS_ATRAS_CERTIFICADO días atrás."
        "CERTIFICADO_INMUTABLE" in m -> "Un certificado emitido no se modifica: anúlalo y emite uno nuevo."
        "CERTIFICADO_YA_ANULADO" in m -> "El certificado ya estaba anulado."
        "CERTIFICADO_ANULAR_SIN_MOTIVO" in m -> "Escribe el motivo de la anulación."
        "certificados_medicos_conclusion_chk" in m -> "Con hallazgos alterados, confirma la conclusión de \"buena salud mental\" o usa la sugerida."
        "certificados_medicos_texto_chk" in m -> "El texto del certificado está vacío o es demasiado largo."
        "certificados_medicos_largo_chk" in m -> "El motivo o el lugar son demasiado largos."
        "SOLO_LECTURA" in m || "row-level security" in m -> "No tienes permiso para emitir este certificado (solo el profesional a su nombre o el Admin)."
        else -> "No se pudo emitir el certificado. Intenta de nuevo."
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quién emite y qué pacientes son de salud mental (lib/flujo.ts)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ¿Esta especialidad es de salud mental? Por el rubro GUARDADO (el nombre solo
 * si no hay): 'psicologia', o 'medicina_general' con nombre "psiquiatr…". NUNCA
 * por el preset de la clínica. Gemelo de esEspecialidadSaludMental.
 */
fun esEspecialidadSaludMental(nombre: String?, rubro: String?): Boolean {
    val r = rubro?.takeIf { it.isNotBlank() } ?: rubroPorNombre(nombre)
    return r == "psicologia" || (r == "medicina_general" && esPsiquiatria(nombre))
}

/** ¿Puede FIRMAR? Con colegiatura (≥ 3) y alguna especialidad de salud mental. Gemelo del trigger. */
fun esEmisorSaludMental(cmp: String?, especialidades: List<Pair<String?, String?>>): Boolean {
    if (cmp.orEmpty().trim().length < 3) return false
    return especialidades.any { (nombre, rubro) -> esEspecialidadSaludMental(nombre, rubro) }
}

/** Qué especialidades de la clínica son de salud mental. `solo` = todas lo son. */
data class MapaSaludMental(val ids: List<String> = emptyList(), val solo: Boolean = false) {
    val activo: Boolean get() = solo || ids.isNotEmpty()
}

/** Gemelo de `mapaSaludMental`: (id, nombre, rubro) de las especialidades activas. */
fun mapaSaludMental(especialidades: List<Triple<String, String?, String?>>?): MapaSaludMental {
    val esps = especialidades.orEmpty()
    val ids = esps.filter { esEspecialidadSaludMental(it.second, it.third) }.map { it.first }
    return MapaSaludMental(ids, esps.isNotEmpty() && ids.size == esps.size)
}

/** ¿Esta cita / tratamiento es de salud mental? Misma cadena que `citaEsFisio`. */
fun citaEsSaludMental(
    mapa: MapaSaludMental,
    especialidadId: String? = null,
    especialidadServicioId: String? = null,
    especialidadesProfesional: List<String>? = null,
): Boolean = atencionEnMapaEspecialidad(mapa.ids, mapa.solo, especialidadId, especialidadServicioId, especialidadesProfesional)

/**
 * ¿La ficha ofrece el certificado? (pacienteEsSaludMental): clínica solo de
 * salud mental → sí; sin salud mental → no; mixta → si ya tiene certificados,
 * si alguna atención es de salud mental o si quien mira lo es.
 */
fun pacienteEsSaludMental(
    mapa: MapaSaludMental,
    atenciones: List<AtencionFisio> = emptyList(),
    tieneCertificados: Boolean = false,
    especialidadesDeQuienMira: List<String>? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    if (tieneCertificados) return true
    if (atenciones.any { citaEsSaludMental(mapa, it.especialidadId, it.especialidadServicioId, it.especialidadesProfesional) }) return true
    return especialidadesDeQuienMira.orEmpty().any { it.isNotBlank() && it in mapa.ids }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lo que llega del servidor
// ─────────────────────────────────────────────────────────────────────────────

/** Una fila de `certificados_medicos` (GET /api/staff/certificado). */
data class CertificadoApp(
    val id: String,
    val numero: Int?,
    val fecha: String,
    val finalidad: String?,
    /** null = sin el detalle (recepción: veDetalleCertificado en el servidor). */
    val texto: String?,
    val terapeutaId: String?,
    val anulado: Boolean,
    val visiblePaciente: Boolean,
    /** null = sin el detalle. */
    val conclusionConfirmada: Boolean?,
    val motivoAnulacion: String?,
    val profesionalNombre: String?,
    val profesionalColegiatura: String?,
) {
    val numeroTexto: String get() = formatearNumeroCertificado(numero)
}

/** "Para: Licencia de conducir" / "A solicitud del interesado" (resumenCertificado). */
fun resumenCertificado(finalidad: String?): String =
    finalidad?.takeIf { it.isNotBlank() }?.let { "Para: $it" } ?: "A solicitud del interesado"

private fun JsonObject.txtC(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

private fun JsonObject.boolC(k: String): Boolean? = when (txtC(k)?.lowercase()) {
    "true" -> true
    "false" -> false
    else -> null
}

/** Fila → [CertificadoApp] (tolerante: sin id, null). */
fun certificadoDeJson(o: JsonObject): CertificadoApp? {
    val id = o.txtC("id") ?: return null
    val prof = o["profesional"] as? JsonObject
    return CertificadoApp(
        id = id,
        numero = o.txtC("numero")?.toDoubleOrNull()?.toInt(),
        fecha = o.txtC("fecha")?.take(10).orEmpty(),
        finalidad = o.txtC("finalidad"),
        texto = o.txtC("texto"),
        terapeutaId = o.txtC("terapeuta_id"),
        anulado = o.txtC("estado") == "Anulado",
        visiblePaciente = o.boolC("visible_paciente") ?: false,
        conclusionConfirmada = o.boolC("conclusion_confirmada"),
        motivoAnulacion = o.txtC("motivo_anulacion"),
        profesionalNombre = prof?.txtC("nombre"),
        profesionalColegiatura = prof?.txtC("colegiatura"),
    )
}

data class FirmanteCertificado(
    val id: String,
    val nombre: String,
    val colegiatura: String,
    val especialidadId: String?,
    /** "El" / "La" que suscribe (por su título). */
    val suscribe: String,
)

data class PlantillaCertificado(val terapeutaId: String?, val texto: String, val updatedAt: String?)

/** GET /api/staff/certificado/prefill. */
data class PrefillCertificado(
    val pacienteId: String,
    val paciente: PacienteCertificado,
    val fechaNacimiento: String?,
    val hoy: String,
    val lugar: String?,
    val firmantes: List<FirmanteCertificado>,
    val terapeutaId: String?,
    val citaId: String?,
    val citaEspecialidadId: String?,
    val citaTratamientoId: String?,
    val atencionId: String?,
    val examenMental: String?,
    val plantillas: List<PlantillaCertificado>,
)

/** JSON del prefill → [PrefillCertificado]. null si falta lo esencial (paciente u hoy). */
fun prefillCertificadoDeJson(o: JsonObject): PrefillCertificado? {
    val pac = o["paciente"] as? JsonObject ?: return null
    val pacienteId = pac.txtC("id") ?: return null
    val hoy = o.txtC("hoy")?.take(10) ?: return null
    val cita = o["cita"] as? JsonObject
    val at = o["atencion"] as? JsonObject
    return PrefillCertificado(
        pacienteId = pacienteId,
        paciente = PacienteCertificado(
            nombre = pac.txtC("nombre").orEmpty(),
            documento = pac.txtC("documento"),
            tipoDocumento = pac.txtC("tipoDocumento"),
            tipoDocumentoHc = pac.txtC("tipoDocumentoHc"),
            pais = pac.txtC("pais"),
            edad = pac.txtC("edad")?.toDoubleOrNull()?.toInt(),
            sexo = pac.txtC("sexo")?.takeIf { it == "F" || it == "M" },
        ),
        fechaNacimiento = pac.txtC("fechaNacimiento"),
        hoy = hoy,
        lugar = o.txtC("lugar"),
        firmantes = (o["firmantes"] as? JsonArray).orEmpty().mapNotNull { x ->
            val f = x as? JsonObject ?: return@mapNotNull null
            FirmanteCertificado(
                id = f.txtC("id") ?: return@mapNotNull null,
                nombre = f.txtC("nombre").orEmpty(),
                colegiatura = f.txtC("colegiatura").orEmpty(),
                especialidadId = f.txtC("especialidadId"),
                suscribe = if (f.txtC("suscribe") == "La") "La" else "El",
            )
        },
        terapeutaId = o.txtC("terapeutaId"),
        citaId = cita?.txtC("id"),
        citaEspecialidadId = cita?.txtC("especialidadId"),
        citaTratamientoId = cita?.txtC("tratamientoId"),
        atencionId = at?.txtC("id"),
        examenMental = at?.txtC("examenMental"),
        plantillas = (o["plantillas"] as? JsonArray).orEmpty().mapNotNull { x ->
            val p = x as? JsonObject ?: return@mapNotNull null
            PlantillaCertificado(p.txtC("terapeutaId"), p.txtC("texto") ?: return@mapNotNull null, p.txtC("updatedAt"))
        },
    )
}

/** Plantilla vigente: la del firmante → la de la clínica → null (la de fábrica). */
fun plantillaVigente(plantillas: List<PlantillaCertificado>, terapeutaId: String?): PlantillaCertificado? =
    plantillas.firstOrNull { !it.terapeutaId.isNullOrEmpty() && it.terapeutaId == terapeutaId }
        ?: plantillas.firstOrNull { it.terapeutaId.isNullOrEmpty() }
