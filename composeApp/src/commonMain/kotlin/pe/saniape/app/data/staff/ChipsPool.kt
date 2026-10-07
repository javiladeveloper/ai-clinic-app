package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * CHIPS CLÍNICOS POR ESPECIALIDAD + POOL COMPARTIDO (lo puro, con tests).
 *
 * Gemelo de `lib/chips-pool.ts` de la web (2026-10-07). Contrato:
 * `docs/app-contrato-chips.md` del dashboard. La app NO reimplementa el
 * aprendizaje del pool: lee lo ya mezclado (`GET /api/staff/chips`) y registra
 * por la RPC `registrar_chips`. Acá solo vive lo que hace falta en el teléfono:
 *  - la clave plegada (sin tildes) para no duplicar "Liberacion"/"Liberación";
 *  - la mezcla propio → base → pool (para poner la base de la especialidad
 *    delante del pool, como `useChipsConPool`);
 *  - leer la respuesta del endpoint;
 *  - el filtro de "qué se puede registrar" (la RPC filtra igual; esto evita el viaje).
 *
 * Si cambia una regla en la web, cambia acá.
 */
object CampoChip {
    const val TECNICA = "tecnica"
    const val DIAGNOSTICO = "diagnostico"
    const val SINTOMA = "sintoma"
    const val PATOLOGIA = "patologia"
    const val SERVICIO = "servicio"
}

enum class FuenteChip { PROPIA, BASE, POOL }

data class ChipSugerido(val texto: String, val fuente: FuenteChip)

/** Una fila cruda: [peso] = usos (propias, mayor primero) o rango (pool, menor primero). */
data class ChipFila(val texto: String, val peso: Double? = null)

// ── Clave plegada ───────────────────────────────────────────────────────────

/** Letra con tilde → la base. Lo que hace `normalize('NFD')` + quitar marcas en la web. */
private val PLIEGUE: Map<Char, Char> = buildMap {
    fun pon(desde: String, a: Char) = desde.forEach { put(it, a) }
    pon("áàäâãåā", 'a'); pon("ÁÀÄÂÃÅĀ", 'A')
    pon("éèëêē", 'e'); pon("ÉÈËÊĒ", 'E')
    pon("íìïîī", 'i'); pon("ÍÌÏÎĪ", 'I')
    pon("óòöôõō", 'o'); pon("ÓÒÖÔÕŌ", 'O')
    pon("úùüûū", 'u'); pon("ÚÙÜÛŪ", 'U')
    pon("ñ", 'n'); pon("Ñ", 'N')
    pon("ç", 'c'); pon("Ç", 'C')
    pon("ý", 'y'); pon("Ý", 'Y')
}

/**
 * Sin tildes (ñ→n, ü→u), conservando mayúsculas. También quita las marcas
 * combinantes sueltas (U+0300–U+036F): un texto que llega descompuesto
 * ("a" + "́") queda igual que el compuesto.
 */
fun plegarTildes(s: String): String = buildString(s.length) {
    for (ch in s) {
        if (ch.code in 0x0300..0x036F) continue
        append(PLIEGUE[ch] ?: ch)
    }
}

private val RE_ESPACIOS = Regex("""[\s ]+""")
private val RE_PUNTUACION_FINAL = Regex("""[\s.,;:]+$""")

/**
 * Clave para no duplicar: minúsculas, sin tildes, espacios colapsados y sin
 * puntuación al final. "Liberación  miofascial." = "LIBERACION miofascial".
 * GEMELA de `claveChip()` (web) y de `public.chip_clave()` (SQL).
 */
fun claveChip(s: String): String =
    plegarTildes(s).lowercase().replace(RE_ESPACIOS, " ").trim().replace(RE_PUNTUACION_FINAL, "")

// ── Mezcla propio + base + pool ─────────────────────────────────────────────

/**
 * La lista que ve el profesional (gemela de `combinarChips`):
 *  1. lo PROPIO de la clínica, más usado primero (gana si coincide la clave);
 *  2. la BASE (chips de la especialidad, personalizados o del código), en su orden;
 *  3. el POOL del rubro, por rango (1 = el que más clínicas usan).
 * Sin repetir por clave. Sin peso se respeta el orden en que llegaron.
 */
fun combinarChips(
    propias: List<ChipFila> = emptyList(),
    base: List<String> = emptyList(),
    pool: List<ChipFila> = emptyList(),
    limite: Int = 80,
): List<ChipSugerido> {
    val vistos = HashSet<String>()
    val r = ArrayList<ChipSugerido>()
    fun meter(texto: String, fuente: FuenteChip) {
        val t = texto.trim()
        val k = claveChip(t)
        if (k.isEmpty() || r.size >= limite || !vistos.add(k)) return
        r.add(ChipSugerido(t, fuente))
    }
    // sortedBy es estable: a igual peso queda el orden de llegada.
    propias.sortedByDescending { it.peso ?: 0.0 }.forEach { meter(it.texto, FuenteChip.PROPIA) }
    base.forEach { meter(it, FuenteChip.BASE) }
    pool.sortedBy { it.peso ?: 1e9 }.forEach { meter(it.texto, FuenteChip.POOL) }
    return r
}

/**
 * Lo que llegó del endpoint con OTRA base delante del pool (gemelo de
 * `useChipsConPool`): propias → [base] → pool, y se corta en
 * `base.size + extras`. Así los chips de siempre no cambian de lugar y detrás
 * asoman unos pocos del pool. La base que mandó el servidor se descarta: manda
 * la de quien llama (la de la especialidad que eligió el formulario).
 */
fun chipsConBase(chips: List<ChipSugerido>, base: List<String>, extras: Int = 4): List<String> =
    combinarChips(
        propias = chips.filter { it.fuente == FuenteChip.PROPIA }.map { ChipFila(it.texto) },
        base = base,
        pool = chips.filter { it.fuente == FuenteChip.POOL }.map { ChipFila(it.texto) },
        limite = base.size + extras,
    ).map { it.texto }

/**
 * Filtra en memoria al escribir (cero viajes por tecla): fuera lo ya puesto y,
 * si hay consulta, solo lo que la contiene (sin tildes ni mayúsculas).
 */
fun filtrarChips(textos: List<String>, consulta: String, excluir: Collection<String> = emptyList(), max: Int = 8): List<String> {
    val fuera = excluir.mapTo(HashSet()) { claveChip(it) }
    val q = claveChip(consulta)
    return textos.asSequence()
        .filter { s -> val k = claveChip(s); k !in fuera && (q.isEmpty() || k.contains(q)) }
        .take(max).toList()
}

// ── Respuesta del endpoint ──────────────────────────────────────────────────

private val jsonChips = Json { ignoreUnknownKeys = true }

/**
 * `GET /api/staff/chips` → `{ campo, especialidadId, rubros, items: [{ texto, fuente }] }`.
 * null = no es esa forma (servidor viejo, HTML de error…): quien llama cae a lo de siempre.
 */
fun parsearChipsEndpoint(cuerpo: String): List<ChipSugerido>? {
    val obj = runCatching { jsonChips.parseToJsonElement(cuerpo) as? JsonObject }.getOrNull() ?: return null
    val items = obj["items"] as? JsonArray ?: return null
    return items.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val texto = (o["texto"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@mapNotNull null
        val fuente = when ((o["fuente"] as? JsonPrimitive)?.contentOrNull) {
            "propia" -> FuenteChip.PROPIA
            "base" -> FuenteChip.BASE
            else -> FuenteChip.POOL
        }
        ChipSugerido(texto, fuente)
    }
}

// ── Chips sobre un texto libre (diagnóstico "A, B; C") ──────────────────────

/** Los términos del texto, separados por coma, punto y coma o salto de línea. */
fun terminosDeTexto(texto: String): List<String> =
    texto.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

/** ¿El chip está puesto? Solo como término COMPLETO ("Postural" no está en "Alteración postural"). */
fun chipPuesto(texto: String, chip: String): Boolean {
    val k = claveChip(chip)
    return terminosDeTexto(texto).any { claveChip(it) == k }
}

/** Quita el término exacto (sin tildes ni mayúsculas) con su separador; nunca una subcadena. */
fun quitarChip(texto: String, chip: String): String {
    val k = claveChip(chip)
    // Piezas: término, separador, término, separador… (el separador se conserva).
    val tokens = ArrayList<String>()
    val seps = ArrayList<String>()
    val actual = StringBuilder()
    for (ch in texto) {
        if (ch == ',' || ch == ';' || ch == '\n') {
            tokens.add(actual.toString()); seps.add(ch.toString()); actual.clear()
        } else actual.append(ch)
    }
    tokens.add(actual.toString()); seps.add("")
    val quedan = tokens.indices.mapNotNull { i ->
        val tok = tokens[i].trim()
        if (tok.isEmpty() || claveChip(tok) == k) null else tok to seps[i]
    }
    return quedan.mapIndexed { i, (tok, sep) ->
        if (i == quedan.lastIndex) tok
        else tok + if (sep == "\n") "\n" else "${sep.ifEmpty { "," }} "
    }.joinToString("")
}

/** Agrega el chip al final ("A, B" + C → "A, B, C") si no está puesto. */
fun agregarChip(texto: String, chip: String): String {
    if (chipPuesto(texto, chip)) return texto
    val t = texto.trim().trimEnd(',', ';').trim()
    return if (t.isEmpty()) chip else "$t, $chip"
}

/** Toca un chip: si está lo quita, si no lo agrega. */
fun alternarChip(texto: String, chip: String): String =
    if (chipPuesto(texto, chip)) quitarChip(texto, chip) else agregarChip(texto, chip)

// ── Registro propio: qué vale la pena mandar ────────────────────────────────

/** Palabras de un nombre que NO sirven para cruzar (vacías y genéricas de clínica). Gemela web. */
private val PALABRAS_VACIAS_NOMBRE = setOf(
    "santa", "santo", "para", "como",
    "clinica", "clinicas", "centro", "consultorio", "consultorios", "policlinico", "instituto", "grupo",
    "medico", "medica", "medicos", "medicina", "salud", "dental", "odontologia", "odontologica",
    "fisio", "fisioterapia", "terapia", "terapias", "rehabilitacion", "fisica", "estetica", "estetico",
    "capilar", "nutricion", "psicologia", "psicologico", "psicologica", "ginecologia", "especialidades",
    "integral", "laser", "belleza", "sede", "central", "familia", "familiar", "bienestar", "cuidado",
    "demo", "prueba",
)

/** Tokens de un nombre para cruzar: plegados, ≥ 4 letras, sin palabras vacías. Gemela de `tokensDeNombre`. */
fun tokensDeNombre(nombre: String?): List<String> =
    claveChip(nombre.orEmpty()).split(Regex("[^a-z]+"))
        .filter { it.length >= 4 && it !in PALABRAS_VACIAS_NOMBRE }.distinct()

private val RE_FECHA = Regex("""\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b""")
private val RE_PALABRA_DATO = Regex("""\b(dni|documento|telefono|teléfono|celular|cel\.?|direcci[oó]n)\b""")
private val RE_SISTEMA = Regex("""autom[aá]tic|protocolo|confirmar (hora|con)|cerrada sin|sin registrar""")
private val RE_DOS_LETRAS = Regex("""[a-zA-ZáéíóúüñÁÉÍÓÚÜÑ]{2}""")
private val RE_SIMBOLOS = Regex("""[\[\]{}<>@]""")

/** DNI, teléfono, correo, fecha (gemelo de `pareceDatoPersonal` sin el cruce de nombre). */
internal fun pareceDatoPersonal(frase: String): Boolean {
    if ('@' in frase) return true
    if (Regex("""\d{7,}""").containsMatchIn(frase.replace(Regex("""[\s\-.()]"""), ""))) return true
    if (RE_FECHA.containsMatchIn(frase)) return true
    return RE_PALABRA_DATO.containsMatchIn(frase.lowercase())
}

/** ¿Lleva emoji o pictograma? (sustitutos UTF-16 o el bloque de símbolos varios). */
private fun tieneEmoji(s: String): Boolean = s.any { it.isSurrogate() || it.code in 0x2600..0x27BF }

/**
 * ¿Se guarda como sugerencia PROPIA? Nunca con datos del paciente ni marcas del
 * sistema. GEMELA de `esTextoRegistrable` (web) y del filtro de `registrar_chips`.
 */
fun esTextoRegistrable(texto: String, campo: String, nombrePaciente: String? = null): Boolean {
    val t = texto.replace(RE_ESPACIOS, " ").trim()
    val max = if (campo == CampoChip.TECNICA) 80 else 120
    if (t.length < 3 || t.length > max) return false
    if (!RE_DOS_LETRAS.containsMatchIn(t)) return false
    if (RE_SIMBOLOS.containsMatchIn(t)) return false
    if (tieneEmoji(t)) return false
    if (RE_SISTEMA.containsMatchIn(t.lowercase())) return false
    if (pareceDatoPersonal(t)) return false
    val tokens = claveChip(t).split(Regex("[^a-z0-9]+")).toSet()
    return tokensDeNombre(nombrePaciente).none { it in tokens }
}

/** Lo que se manda a `registrar_chips`: limpio, registrable, sin repetir por clave, 20 como mucho. */
fun textosARegistrar(textos: List<String>, campo: String, nombrePaciente: String? = null): List<String> {
    val vistos = HashSet<String>()
    return textos.asSequence()
        .map { it.replace(RE_ESPACIOS, " ").trim() }
        .filter { esTextoRegistrable(it, campo, nombrePaciente) && vistos.add(claveChip(it)) }
        .take(20).toList()
}

/** Diagnóstico libre → términos (coma / ; / salto), ≥ 3 caracteres. Gemelo de `trocearDiagnostico`. */
fun trocearDiagnostico(texto: String): List<String> =
    texto.split(',', ';', '\n').map { it.trim() }.filter { it.length >= 3 }

/** ¿El error es "la función no existe" (base sin la migración de chips)? Entonces, el camino de siempre. */
fun esFaltaFuncion(mensaje: String?): Boolean {
    val m = mensaje.orEmpty()
    return "PGRST202" in m || "42883" in m || m.contains("could not find the function", ignoreCase = true)
}
