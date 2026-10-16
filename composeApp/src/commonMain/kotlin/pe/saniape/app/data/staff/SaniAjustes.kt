package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

// Sani → Ajustes (solo Admin): cómo CONVERSA Sani. Gemelo PURO de lib/sani-ajustes.ts
// (y de `aUsted` de lib/trato-bot.ts). Tres perillas:
//  1. Seguimiento antes de que cierre la ventana de 24 h (vive en LeadAI /mi-plan,
//     vía /api/staff/mensajes/comportamiento): encendido y horas.
//  2. Precio repetido (configuracion.sani_captacion_numeros → escalarPrecioTras;
//     0 = Sani responde siempre con amabilidad).
//  3. Mensaje de bienvenida (configuracion.sani_bienvenida; vacío = el de Sani),
//     con la vista previa que integra la divulgación (requisito de Meta) y, con
//     trato de usted, pasa por aUsted como el playbook.

// ── 1. Seguimiento antes de que cierre la ventana ──────────────────────────

/** Contrato con LeadAI: entero 4..22, 20 por defecto. */
object HorasSeguimiento { const val MIN = 4; const val MAX = 22; const val POR_DEFECTO = 20 }

/** Lo que ofrece la pantalla: todas dentro de las 24 h de WhatsApp. */
val OPCIONES_HORAS_SEGUIMIENTO = listOf(12, 16, 18, 20, 22)

data class SeguimientoVentana(val disponible: Boolean, val activo: Boolean, val horas: Int)

/** Lo que muestra la pantalla (gemelo de `seguimientoDesdeMiPlan`): un LeadAI que no lo conoce → no disponible. */
fun seguimientoDesdeMiPlan(activo: Boolean?, horas: Int?): SeguimientoVentana = SeguimientoVentana(
    disponible = activo != null,
    activo = activo == true,
    horas = horas?.takeIf { it in HorasSeguimiento.MIN..HorasSeguimiento.MAX } ?: HorasSeguimiento.POR_DEFECTO,
)

/** El GET de /api/staff/mensajes/comportamiento → seguimiento (re-normalizado por si el servidor es más viejo). */
fun seguimientoDeComportamiento(o: JsonObject?): SeguimientoVentana {
    fun b(k: String) = (o?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull
    val horas = (o?.get("seguimientoVentanaHoras") as? JsonPrimitive)?.doubleOrNull?.takeIf { it == kotlin.math.floor(it) }?.toInt()
    val activo = if (b("seguimientoVentanaDisponible") == false) null else b("seguimientoVentanaActivo")
    return seguimientoDesdeMiPlan(activo, horas)
}

/** "A las ~20 horas de nuestro último mensaje". */
fun etiquetaHorasSeguimiento(h: Int): String = "A las ~$h horas de nuestro último mensaje"

// ── 2. Precio repetido (números de la captación) ────────────────────────────

const val CLAVE_CAPTACION_NUMEROS = "sani_captacion_numeros"

/** Lista CERRADA con rangos (NUMEROS_CAPTACION_PERMITIDOS de la web). */
val NUMEROS_CAPTACION_PERMITIDOS: Map<String, IntRange> = mapOf(
    "escalarPrecioTras" to 0..10,
    "maxPreguntasPrecioEscalera" to 1..5,
    "avisosIncoherencia" to 0..5,
    "reintentosDni" to 0..3,
    "maxCitasFuturas" to 1..5,
    "autogestionCitas" to 0..1,
    "escuchaEspejo" to 0..1,
    "pedirCorreo" to 0..1,
    "rescateLlm" to 0..1,
)

/** Lo guardado → solo las claves permitidas, enteras y en rango. Nunca lanza. */
fun numerosCaptacionValidos(valor: JsonElement?): Map<String, Int> {
    val obj = when (valor) {
        is JsonObject -> valor
        is JsonPrimitive -> if (valor.isString) runCatching { kotlinx.serialization.json.Json.parseToJsonElement(valor.content) as? JsonObject }.getOrNull() else null
        else -> null
    } ?: return emptyMap()
    val out = linkedMapOf<String, Int>()
    for ((k, v) in obj) {
        val r = NUMEROS_CAPTACION_PERMITIDOS[k] ?: continue
        val p = v as? JsonPrimitive ?: continue
        if (p.isString) continue
        val d = p.doubleOrNull ?: continue
        if (d != kotlin.math.floor(d)) continue
        val n = d.toInt()
        if (n in r) out[k] = n
    }
    return out
}

/** Las claves que cambia el Admin de la clínica (el resto es de soporte). */
val CLAVES_NUMEROS_CLINICA = listOf("escalarPrecioTras")

/** Opciones de "pasar a una persona después de N preguntas sobre el precio". */
val OPCIONES_ESCALAR_PRECIO = listOf(2, 3, 4, 5)

data class ModoPrecioRepetido(val escalar: Boolean, val tras: Int)

/** Lo guardado → la pantalla (0 o nada = responder siempre; con N, pasar a una persona). */
fun modoPrecioRepetido(numeros: Map<String, Int>?): ModoPrecioRepetido {
    val n = numeros?.get("escalarPrecioTras")?.takeIf { it in (NUMEROS_CAPTACION_PERMITIDOS["escalarPrecioTras"] ?: 0..10) } ?: 0
    return if (n > 0) ModoPrecioRepetido(true, n) else ModoPrecioRepetido(false, 3)
}

/** La elección → lo que se guarda en `sani_captacion_numeros` (solo la clave de la clínica). */
fun cambioPrecioRepetido(escalar: Boolean, tras: Int): Pair<String, Int> {
    if (!escalar) return "escalarPrecioTras" to 0
    val r = NUMEROS_CAPTACION_PERMITIDOS.getValue("escalarPrecioTras")
    return "escalarPrecioTras" to tras.coerceIn(1, r.last)
}

// ── 3. Mensaje de bienvenida ────────────────────────────────────────────────

const val CLAVE_BIENVENIDA = "sani_bienvenida"
const val MAX_BIENVENIDA = 700

fun validarBienvenida(valor: String?): String? {
    if (valor == null) return "El mensaje de bienvenida no es válido"
    if (valor.trim().length > MAX_BIENVENIDA) return "El mensaje de bienvenida no puede pasar de $MAX_BIENVENIDA caracteres"
    return null
}

/**
 * "virtual" a secas NO divulga ("consultas virtuales"): hace falta "asistente
 * virtual", "bot", "automático" o "inteligencia artificial". GEMELA de YA_DIVULGA de LeadAI.
 */
private val YA_DIVULGA = Regex("(?u)asistente virtual|\\bbot\\b|autom[aá]tic|inteligencia artificial", RegexOption.IGNORE_CASE)

/** Abreviaturas que terminan en punto sin terminar la oración ("Dr.", "Sra."). */
private val ABREVIATURAS = Regex(
    "(?u)(?:^|[\\s(])(?:dr|dra|dres|sr|sra|srta|lic|ing|mg|mgtr|psic|ps|obst|odont|tec|av|jr|nro|dpto|urb|mz|lt|of|cel|telf|tlf|st)\\.$",
    RegexOption.IGNORE_CASE,
)

/** ¿Emoji (pictográfico) en [i]? Devuelve su largo en UTF-16 (con un FE0F opcional detrás) o 0. */
private fun largoEmoji(t: String, i: Int): Int {
    if (i >= t.length) return 0
    val ch = t[i]
    val cp: Int
    val largo: Int
    if (ch.isHighSurrogate() && i + 1 < t.length && t[i + 1].isLowSurrogate()) {
        cp = 0x10000 + ((ch.code - 0xD800) shl 10) + (t[i + 1].code - 0xDC00)
        largo = 2
    } else {
        cp = ch.code
        largo = 1
    }
    val pictografico = cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF ||
        cp in 0x2190..0x21FF || cp in 0x2300..0x23FF || cp == 0x00A9 || cp == 0x00AE || cp in 0x2122..0x2139
    if (!pictografico) return 0
    return if (i + largo < t.length && t[i + largo] == '️') largo + 1 else largo
}

/**
 * Dónde termina la primera oración (gemela de `finPrimeraOracion`): tras un
 * ".!?…" (y los emojis pegados a él), si después viene un espacio y más texto.
 * No corta en "Dr.", "Sra.". -1 si no hay.
 */
private fun finPrimeraOracion(texto: String): Int {
    var i = 0
    while (i < texto.length) {
        val ch = texto[i]
        if (ch == '.' || ch == '!' || ch == '?' || ch == '…') {
            if (ch == '.' && ABREVIATURAS.containsMatchIn(texto.substring(0, i + 1))) { i++; continue }
            // Candidatos (greedy primero): justo tras el signo, y tras cada emoji que le sigue.
            val candidatos = mutableListOf(i + 1)
            var j = i + 1
            while (true) {
                var k = j
                while (k < texto.length && texto[k].isWhitespace()) k++
                val l = largoEmoji(texto, k)
                if (l == 0) break
                j = k + l
                candidatos.add(j)
            }
            for (fin in candidatos.asReversed()) {
                // (?=\s+\S): un espacio y luego algo que no lo sea.
                if (fin < texto.length && texto[fin].isWhitespace() && texto.substring(fin).isNotBlank()) return fin
            }
        }
        i++
    }
    return -1
}

/**
 * Lo que el paciente va a ver (gemela de `integrarDivulgacion` de LeadAI): si la
 * bienvenida no avisa que responde un asistente virtual, se intercala la frase
 * después del saludo inicial. Con usted, el texto pasa por [aUsted].
 */
fun vistaPreviaBienvenida(saludo: String, nombreClinica: String?, trato: String = "tu"): String {
    val base = saludo.trim()
    val texto = if (trato == "usted") aUsted(base) else base
    if (texto.isEmpty() || YA_DIVULGA.containsMatchIn(texto)) return texto
    val frase = "${if (trato == "usted") "Le" else "Te"} escribe el asistente virtual de ${nombreClinica?.trim()?.takeIf { it.isNotEmpty() } ?: "este negocio"}."
    val fin = finPrimeraOracion(texto)
    if (fin < 0 || fin > 140) return "$texto $frase"
    return "${texto.substring(0, fin)} $frase ${texto.substring(fin).trimStart()}"
}

/** ¿El texto ya avisa que es un asistente virtual? */
fun bienvenidaDivulga(texto: String): Boolean = YA_DIVULGA.containsMatchIn(texto)

// ── Tú → usted (gemelo de aUsted de lib/trato-bot.ts) ───────────────────────

private const val I = "(?<![\\p{L}\\p{N}])"
private const val F = "(?![\\p{L}\\p{N}])"

/** Verbos de tú → usted (lista cerrada, la de la web). */
private val VERBOS: Map<String, String> = linkedMapOf(
    "tienes" to "tiene", "quieres" to "quiere", "puedes" to "puede", "estás" to "está", "sientes" to "siente", "llevas" to "lleva",
    "vas" to "va", "sales" to "sale", "gastas" to "gasta", "compruebas" to "comprueba", "decides" to "decide", "toleras" to "tolera",
    "prefieres" to "prefiere", "necesitas" to "necesita", "podrías" to "podría", "eres" to "es", "sabes" to "sabe", "buscas" to "busca",
    "vienes" to "viene", "haces" to "hace", "crees" to "cree", "deseas" to "desea", "agarras" to "agarra", "confirmas" to "confirma",
    "compartes" to "comparte", "escribes" to "escribe", "notas" to "nota", "has" to "ha", "quieras" to "quiera", "puedas" to "pueda",
    "estés" to "esté", "tengas" to "tenga", "vengas" to "venga", "prefieras" to "prefiera", "elijas" to "elija", "sientas" to "sienta",
    "notes" to "note", "traes" to "trae", "llegas" to "llega", "dices" to "dice", "sabías" to "sabía", "ibas" to "iba", "tenías" to "tenía",
    "necesites" to "necesite", "usaste" to "usó", "probaste" to "probó", "fuiste" to "fue", "viniste" to "vino", "tuviste" to "tuvo",
    "empezaste" to "empezó", "notaste" to "notó",
    "cuéntame" to "cuénteme", "cuentame" to "cuénteme", "cuéntanos" to "cuéntenos", "cuéntaselo" to "cuénteselo", "dime" to "dígame",
    "dinos" to "díganos", "tráelos" to "tráigalos", "tráelas" to "tráigalas", "tráelo" to "tráigalo", "tráela" to "tráigala",
    "compara" to "compare", "mira" to "mire", "elige" to "elija", "escríbeme" to "escríbame", "escríbenos" to "escríbanos",
    "avísame" to "avíseme", "avísanos" to "avísenos", "confírmame" to "confírmeme", "confírmanos" to "confírmenos", "déjame" to "déjeme",
    "llámanos" to "llámenos", "tómate" to "tómese", "pásate" to "pásese", "ponte" to "póngase", "agenda" to "agende", "reserva" to "reserve",
    "acércate" to "acérquese", "anímate" to "anímese", "compárteme" to "compártame", "mándame" to "mándeme", "envíame" to "envíeme",
    "recuerda" to "recuerde", "fíjate" to "fíjese", "espera" to "espere",
)
private val IMPERATIVO_AMBIGUO = setOf("agenda", "reserva", "recuerda", "espera", "mira", "elige")
private val ENCLITICOS = listOf(
    "agendar", "evaluar", "ayudar", "atender", "orientar", "confirmar", "recordar", "explicar", "dar", "enviar",
    "llamar", "ver", "contar", "decir", "esperar", "registrar", "apartar", "reservar", "avisar", "preguntar",
    "mostrar", "pasar", "mandar", "acompañar", "escuchar", "recibir", "conocer", "ofrecer", "tratar", "revisar",
    "ayudándo", "explicándo", "esperándo", "atendiéndo",
)

private class Reemplazo(val re: Regex, val rep: (MatchResult) -> String)

private fun r(patron: String, reemplazo: String) = Reemplazo(Regex("(?u)$I(?:$patron)$F", RegexOption.IGNORE_CASE)) { reemplazo }

private val REEMPLAZOS: List<Reemplazo> by lazy {
    listOf(
        r("necesitas atenderte", "necesita atenderse"),
        r("te has", "se ha"), r("te hiciste", "se hizo"), r("te atendiste", "se atendió"), r("te vas", "se va"),
        r("te enfrentas", "se enfrenta"), r("te preocupes", "se preocupe"), r("te sientas", "se sienta"),
        r("te sientes", "se siente"), r("te animas", "se anima"), r("te olvides", "se olvide"),
        Reemplazo(Regex("(?u)$I(?:te (lo|la|los|las))$F", RegexOption.IGNORE_CASE)) { m -> "se ${m.groupValues[1]}" },
        r("dime tú", "dígame usted"), r("tú mismo", "usted mismo"), r("tú misma", "usted misma"),
        r("contigo", "con usted"),
        Reemplazo(Regex("(?u)(?<!(?:se|los|las|lo|la|le|les) )${I}ven$F", RegexOption.IGNORE_CASE)) { "venga" },
        Reemplazo(Regex("(?u)$I(${ENCLITICOS.joinToString("|")})te$F", RegexOption.IGNORE_CASE)) { m -> "${m.groupValues[1]}le" },
        Reemplazo(Regex("(?u)$I(${VERBOS.keys.filter { it !in IMPERATIVO_AMBIGUO }.joinToString("|")})$F", RegexOption.IGNORE_CASE)) { m ->
            VERBOS[m.value.lowercase()] ?: m.value
        },
        Reemplazo(Regex("(?u)(^|[.!?¡¿]\\s*|—\\s*|:\\s*)(${IMPERATIVO_AMBIGUO.joinToString("|")})$F", RegexOption.IGNORE_CASE)) { m ->
            m.groupValues[1] + (VERBOS[m.groupValues[2].lowercase()] ?: m.groupValues[2])
        },
        Reemplazo(Regex("(?u)$I(agenda|reserva)(?= (?:tu|tus)$F)", RegexOption.IGNORE_CASE)) { m -> VERBOS[m.value.lowercase()] ?: m.value },
        r("tú", "usted"), r("ti", "usted"), r("tus", "sus"), r("tu", "su"), r("te", "le"),
    )
}

private val PREFIJO_FRASE = Regex("^[\\s.!?¡¿—:]+")

/** Mayúscula inicial si el original la tenía ("Cuéntame" → "Cuénteme"). */
private fun conCaso(original: String, nuevo: String): String {
    val a = original.firstOrNull { it.isLetter() } ?: return nuevo
    if (a == a.lowercaseChar()) return nuevo
    val i = nuevo.indexOfFirst { it.isLetter() }
    return if (i < 0) nuevo else nuevo.substring(0, i) + nuevo[i].uppercase() + nuevo.substring(i + 1)
}

/** Un texto al paciente, de tú a usted. Los marcadores de LeadAI ({cita}…) no se tocan. */
fun aUsted(texto: String): String = runCatching {
    // runCatching: un motor de regex sin lookbehind (otra plataforma) deja el texto como está.
    var t = texto
    for (rr in REEMPLAZOS) {
        t = rr.re.replace(t) { m -> conCaso(m.value.replace(PREFIJO_FRASE, ""), rr.rep(m)) }
    }
    t
}.getOrDefault(texto)

/** El texto guardado en `configuracion` (JSON de un string) → texto ('' si no hay). */
fun leerTextoConfig(valor: JsonElement?): String {
    val p = valor as? JsonPrimitive ?: return ""
    if (!p.isString) return ""
    val crudo = p.contentOrNull ?: return ""
    return runCatching { (kotlinx.serialization.json.Json.parseToJsonElement(crudo) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim() }
        .getOrNull() ?: crudo.trim()
}
